# Working on Family Care

Read the [README](README.md) and the issue for your change before editing. The app and public repository material use English.

## Coordination between agents

- Check [repository issues](https://github.com/runwithfun/family-care/issues) and the [shared project](https://github.com/orgs/runwithfun/projects/2) before choosing a task, about every 30–45 minutes while actively working, and before handing off or merging a PR. Look for new tasks, changed dependencies, and work ready for cross-review. Use your own GitHub CLI login.
- Use a separate branch and worktree for each active task. Tell the other contributor which issue and shared files you are working on. Suggest changes to the shared model or navigation in the linked issue before editing a contract another agent uses.
- Use the linked issue or PR for updates. Record what changed, verification results, and any blocker that affects someone else's task. Keep lasting agreements in this file.
- Set an active item to In Progress. Set it to In Review when someone on the other side can run or inspect the result. Close it after review and a passing merge. Keep commits focused and link PRs to their issues. The initial public source snapshot gets its own review.

## Local operation and diagnostics

- Keep the app self-contained. Store family data and documents on the device. Use local reminders, local Assistant actions, and on-device document tools. The demo and its core features must work without a backend, remote model, or analytics collection. GitHub Pages serves only the static project site.
- Keep full local logs and crash details in all builds, including Release. Preserve existing entries and fields. New code uses the same level of detail.
- On failure, keep the complete error type, message, stack trace, upstream status and response body, and request body. Mask credential values in every destination while preserving the fields. Where a real limit applies, retain as much as it permits. Keep upstream error details out of user-facing responses.
- Use synthetic identities, dates, and documents in the demo.

## Local verification

- Check available space before heavy builds. If less than about 20 GB is available, remove only your own finished artifacts or report the limit. Preserve other contributors' files and uncommitted work.
- Prefer focused tests while implementing. Reuse one DerivedData path for the project. Pass `-collect-test-diagnostics never` for Xcode test runs. Save logs once and compress long-run logs when they finish.
