package com.runwithfun.familycare.records

import com.runwithfun.familycare.domain.CareDate
import com.runwithfun.familycare.domain.CareTime

sealed interface FieldValidationResult {
    data class Ok(val value: String) : FieldValidationResult
    data class Invalid(val detail: String) : FieldValidationResult
}

fun validateReviewInput(fieldLabel: String, raw: String): FieldValidationResult {
    val trimmed = raw.trim()
    if (trimmed.isEmpty()) {
        return FieldValidationResult.Invalid("Correction for \"$fieldLabel\" is empty.")
    }
    val lower = fieldLabel.lowercase()
    return when {
        "date" in lower -> validateDate(fieldLabel, trimmed)
        "time" in lower -> validateTime(fieldLabel, trimmed)
        else -> FieldValidationResult.Ok(trimmed)
    }
}

private fun validateDate(fieldLabel: String, raw: String): FieldValidationResult {
    val parts = raw.split('-')
    if (parts.size != 3) {
        return FieldValidationResult.Invalid(
            "\"$fieldLabel\" must use YYYY-MM-DD. Received: \"$raw\".",
        )
    }
    val year = parts[0].toIntOrNull()
    val month = parts[1].toIntOrNull()
    val day = parts[2].toIntOrNull()
    if (year == null || month == null || day == null) {
        return FieldValidationResult.Invalid(
            "\"$fieldLabel\" must use numeric YYYY-MM-DD. Received: \"$raw\".",
        )
    }
    return try {
        FieldValidationResult.Ok(CareDate(year, month, day).toString())
    } catch (error: IllegalArgumentException) {
        FieldValidationResult.Invalid(
            "\"$fieldLabel\" is not a valid calendar date: \"$raw\". ${error.message}",
        )
    }
}

private fun validateTime(fieldLabel: String, raw: String): FieldValidationResult {
    val parts = raw.split(':')
    if (parts.size != 2) {
        return FieldValidationResult.Invalid(
            "\"$fieldLabel\" must use HH:MM. Received: \"$raw\".",
        )
    }
    val hour = parts[0].toIntOrNull()
    val minute = parts[1].toIntOrNull()
    if (hour == null || minute == null) {
        return FieldValidationResult.Invalid(
            "\"$fieldLabel\" must use numeric HH:MM. Received: \"$raw\".",
        )
    }
    return try {
        FieldValidationResult.Ok(CareTime(hour, minute).toString())
    } catch (error: IllegalArgumentException) {
        FieldValidationResult.Invalid(
            "\"$fieldLabel\" is not a valid time: \"$raw\". ${error.message}",
        )
    }
}
