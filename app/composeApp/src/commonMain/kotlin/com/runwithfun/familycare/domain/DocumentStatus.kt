package com.runwithfun.familycare.domain

/** A field is complete only when the parent confirmed a non-null value. */
fun DocumentField.isConfirmed(): Boolean = review?.value != null

/** Explicit leave-unresolved decision (distinct from an untouched proposal). */
fun DocumentField.isExplicitlyUnresolved(): Boolean = review != null && review.value == null

/** Never reviewed — proposal may still be present. */
fun DocumentField.isUntouched(): Boolean = review == null

fun DocumentField.needsAttention(): Boolean =
    isExplicitlyUnresolved() || (isUntouched() && proposal == null)

fun DocumentField.shownValue(): String? = review?.value ?: proposal?.value

fun CareDocument.isComplete(): Boolean =
    fields.isNotEmpty() && fields.all { it.isConfirmed() }

fun CareDocument.hasGaps(): Boolean = fields.any { !it.isConfirmed() }

/**
 * Incomplete immunization records must not invent a vaccine status.
 * Returns guidance for the parent; never asserts that a missing dose was given or skipped.
 */
fun CareDocument.providerFollowUpMessage(): String? {
    if (!hasGaps()) return null
    return when (kind) {
        DocumentKind.IMMUNIZATION_RECORD ->
            "Ask the clinic for a complete immunization record. " +
                "Do not assume any missing vaccine was given or skipped."
        DocumentKind.VISIT_SUMMARY, DocumentKind.OTHER ->
            "Ask the clinic about the missing details before treating this record as complete."
    }
}

fun CareDocument.originalText(): String? = when (val content = original.content) {
    is OriginalContent.Text -> content.value
    is OriginalContent.StoredFile -> null
}

fun CareDocument.storageKey(): String? = when (val content = original.content) {
    is OriginalContent.StoredFile -> content.storageKey
    is OriginalContent.Text -> null
}

fun CareDocument.originalRetrievalLabel(): String = when (val content = original.content) {
    is OriginalContent.Text -> original.fileName
    is OriginalContent.StoredFile -> content.storageKey
}
