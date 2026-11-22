package com.runwithfun.familycare.domain

/** A field is complete only when the parent confirmed a non-null value. */
fun DocumentField.isConfirmed(): Boolean = review?.value != null

/** Explicit leave-unresolved, or never reviewed and no extraction to work from. */
fun DocumentField.needsAttention(): Boolean =
    review?.value == null && (review != null || proposal == null)

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

fun CareDocument.originalRetrievalLabel(): String = when (val content = original.content) {
    is OriginalContent.Text -> original.fileName
    is OriginalContent.StoredFile -> content.storageKey
}