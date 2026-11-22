package com.runwithfun.familycare.domain

enum class AgeStage {
    NEWBORN_0_2_MONTHS,
    INFANT_2_4_MONTHS,
    ;

    val label: String
        get() = when (this) {
            NEWBORN_0_2_MONTHS -> "0–2 months"
            INFANT_2_4_MONTHS -> "2–4 months"
        }

    val title: String
        get() = when (this) {
            NEWBORN_0_2_MONTHS -> "Newborn"
            INFANT_2_4_MONTHS -> "Early infant"
        }
}

internal fun suggestedAgeStage(birthDate: CareDate?, asOf: CareDate): AgeStage? {
    val months = birthDate?.let(asOf::completedMonthsSince) ?: return null
    return when (months) {
        in 0..1 -> AgeStage.NEWBORN_0_2_MONTHS
        in 2..3 -> AgeStage.INFANT_2_4_MONTHS
        else -> null
    }
}

data class GrowingObservation(
    val id: String,
    val personId: String,
    val stage: AgeStage,
    val note: String,
    val recordedOn: CareDate,
    val forVisitId: String? = null,
)

data class ReviewedActivity(
    val id: String,
    val stage: AgeStage,
    val title: String,
    val summary: String,
    val steps: List<String>,
    val sourceLabel: String,
    val sourceUrl: String,
)
