"""Run the synthetic Family Care walkthrough on a fresh Android emulator."""

import argparse
from pathlib import Path
import re
import subprocess
import time
import traceback
import xml.etree.ElementTree as ET


PACKAGE = "com.runwithfun.familycare"
ACTIVITY = f"{PACKAGE}/.MainActivity"
DUMP_PATH = "/sdcard/family-care-smoke.xml"


def adb(*args, timeout=20, check=True):
    result = subprocess.run(
        ["adb", *args], capture_output=True, text=True, timeout=timeout,
    )
    if result.returncode:
        message = (
            f"adb {' '.join(args)} exited with {result.returncode}\n"
            f"{result.stdout}{result.stderr}"
        )
        if check:
            raise RuntimeError(message)
        print(message, flush=True)
    return result.stdout.strip()


def wait_for_boot():
    adb("wait-for-device", timeout=180)
    deadline = time.monotonic() + 180
    while time.monotonic() < deadline:
        if adb("shell", "getprop", "sys.boot_completed", check=False) == "1":
            return
        time.sleep(2)
    raise RuntimeError("Android did not complete boot within three minutes.")


def setup_device(apk):
    wait_for_boot()
    adb("shell", "input", "keyevent", "82")
    for setting in ("window_animation_scale", "transition_animation_scale", "animator_duration_scale"):
        adb("shell", "settings", "put", "global", setting, "0")
    adb("shell", "settings", "put", "system", "screen_off_timeout", "2147483647")
    adb("shell", "cmd", "uimode", "night", "no")
    adb("install", str(apk), timeout=90)

    adb("shell", "cmd", "connectivity", "airplane-mode", "enable", check=False)
    adb("shell", "svc", "wifi", "disable", check=False)
    adb("shell", "svc", "data", "disable", check=False)
    airplane = adb("shell", "settings", "get", "global", "airplane_mode_on", check=False)
    print(f"Emulator airplane-mode setting after disabling data and Wi-Fi: {airplane}", flush=True)
    adb("shell", "am", "start", "-W", "-n", ACTIVITY)


def dump_ui():
    result = adb("shell", "uiautomator", "dump", DUMP_PATH, timeout=12)
    if DUMP_PATH not in result:
        raise RuntimeError(f"UI Automator did not write the hierarchy: {result}")
    return ET.fromstring(adb("exec-out", "cat", DUMP_PATH))


def bounds(node):
    return tuple(map(int, re.findall(r"-?\d+", node.get("bounds", ""))))


def matching_nodes(root, label, prefix):
    def matches(value):
        value = " ".join(value.split())
        return value.startswith(label) if prefix else value == label

    return [
        node for node in root.iter("node")
        if node.get("package") == PACKAGE
        and (matches(node.get("text", "")) or matches(node.get("content-desc", "")))
    ]


def clickable_node(node, parents):
    target = node
    while target is not None and target.get("package") == PACKAGE:
        if target.get("clickable") == "true":
            return target
        target = parents.get(target)
    return node


def find(label, *, prefix=False, scroll=False):
    deadline = time.monotonic() + 24
    scrolls = 0
    last_failure = None
    while time.monotonic() < deadline:
        try:
            root = dump_ui()
        except (RuntimeError, ET.ParseError, subprocess.TimeoutExpired) as failure:
            last_failure = failure
            time.sleep(0.5)
            continue
        matches = matching_nodes(root, label, prefix)
        if matches:
            return root, matches
        if scroll and scrolls < 4:
            view = next(
                (node for node in root.iter("node")
                 if node.get("package") == PACKAGE and node.get("scrollable") == "true"),
                None,
            )
            if view is not None and len(bounds(view)) == 4:
                left, top, right, bottom = bounds(view)
                x = str((left + right) // 2)
                y_start = str(top + (bottom - top) * 3 // 4)
                y_end = str(top + (bottom - top) // 4)
                adb("shell", "input", "swipe", x, y_start, x, y_end, "300")
                scrolls += 1
        time.sleep(0.5)
    raise AssertionError(f"Could not find {label!r} in Family Care.") from last_failure


def tap(label, *, prefix=False, scroll=False):
    root, matches = find(label, prefix=prefix, scroll=scroll)
    parents = {child: parent for parent in root.iter() for child in parent}
    for node in matches:
        target = clickable_node(node, parents)
        rectangle = bounds(target)
        if len(rectangle) == 4 and target.get("enabled") != "false":
            left, top, right, bottom = rectangle
            if left < right and top < bottom:
                adb("shell", "input", "tap", str((left + right) // 2), str((top + bottom) // 2))
                return
    raise AssertionError(f"Found {label!r}, but it did not have visible touch bounds.")


def screenshot(output, name):
    path = output / f"{name}.png"
    with path.open("wb") as image:
        subprocess.run(
            ["adb", "exec-out", "screencap", "-p"], stdout=image, check=True, timeout=20,
        )
    with path.open("rb") as image:
        if image.read(8) != b"\x89PNG\r\n\x1a\n":
            raise RuntimeError(f"{path} is not a PNG screenshot.")


def save_relaunch_diagnostics(output):
    files = (
        ("FamilyCare-Logcat.log", ("logcat", "-d", "-v", "threadtime")),
        ("FamilyCare-Profile.xml", ("exec-out", "run-as", PACKAGE, "cat", "shared_prefs/familycare.profile.xml")),
        ("FamilyCare-Assistant.xml", ("exec-out", "run-as", PACKAGE, "cat", "shared_prefs/family-care-assistant.xml")),
    )
    for name, command in files:
        try:
            with (output / name).open("wb") as diagnostic:
                subprocess.run(["adb", *command], stdout=diagnostic, check=True, timeout=20)
        except (subprocess.CalledProcessError, subprocess.TimeoutExpired):
            traceback.print_exc()


def walkthrough(output):
    find("Care for both of you")
    tap("Continue")
    find("Create a child profile")
    tap("Open day plan")
    find("Calendar")
    screenshot(output, "FamilyCare-Home")

    tap("Calendar")
    find("November 2026")
    tap("Well-baby visit", prefix=True, scroll=True)
    find("Visit details")

    tap("Records")
    tap("Import synthetic clinic PDF")
    find("Synthetic clinic PDF is stored on this device.")
    tap("demo-immunizations.pdf", prefix=True, scroll=True)
    tap("Open stored original", scroll=True)
    find("Original clinic PDF, page 1", scroll=True)
    screenshot(output, "FamilyCare-Records")

    tap("Assistant")
    find("Add to Today")
    screenshot(output, "FamilyCare-Assistant")
    tap("Open original note", scroll=True)
    find("Original visit note")
    tap("Close")
    tap("Add to Today", scroll=True)

    follow_up = "Ask about recovery at today's visit"
    find(follow_up, prefix=True, scroll=True)
    adb("shell", "am", "force-stop", PACKAGE)
    adb("shell", "am", "start", "-W", "-n", ACTIVITY)
    find("Calendar")
    root, matches = find(follow_up, prefix=True, scroll=True)
    parents = {child: parent for parent in root.iter() for child in parent}
    rows = {bounds(clickable_node(node, parents)) for node in matches}
    if len(rows) != 1:
        raise AssertionError(f"Expected one restored follow-up row, found {len(rows)}.")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("apk", type=Path)
    parser.add_argument("output", type=Path)
    args = parser.parse_args()
    args.output.mkdir(parents=True, exist_ok=True)
    try:
        setup_device(args.apk)
        walkthrough(args.output)
    except Exception:
        save_relaunch_diagnostics(args.output)
        try:
            screenshot(args.output, "FamilyCare-Failure")
            ET.ElementTree(dump_ui()).write(args.output / "Failure-hierarchy.xml", encoding="utf-8")
        except Exception:
            traceback.print_exc()
        raise
    print("Android synthetic family walkthrough passed.", flush=True)


if __name__ == "__main__":
    main()
