package com.runwithfun.familycare.records

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.runwithfun.familycare.demo.DemoFamily
import com.runwithfun.familycare.domain.CareDocument
import com.runwithfun.familycare.domain.CarePlan
import com.runwithfun.familycare.domain.DocumentOriginal
import com.runwithfun.familycare.domain.FieldReview
import com.runwithfun.familycare.domain.OriginalContent
import com.runwithfun.familycare.domain.RecordedImmunization
import com.runwithfun.familycare.domain.Task
import com.runwithfun.familycare.domain.TaskStatus
import com.runwithfun.familycare.domain.isExplicitlyUnresolved
import com.runwithfun.familycare.domain.isUntouched
import com.runwithfun.familycare.domain.storageKey

data class RetrievedOriginal(
    val fileName: String,
    val mediaType: String,
    val storageKey: String?,
    val byteSize: Int?,
    val isPdf: Boolean,
    val textPreview: String?,
)

class RecordsController(
    val plan: CarePlan,
    private val files: DocumentFileStore,
    private val reviews: DocumentReviewStore,
    private val pdfBodyText: String = DemoFamily.immunizationPdfBody(),
) {
    var revision by mutableIntStateOf(0)
        private set

    /** Full local failure detail for logs and on-device diagnostics. */
    var lastErrorDetail by mutableStateOf<String?>(null)
        private set

    var openedOriginal by mutableStateOf<RetrievedOriginal?>(null)
        private set

    init {
        restoreReviews()
    }

    fun documents(): List<CareDocument> {
        revision
        return plan.documents
    }

    fun document(id: String): CareDocument? {
        revision
        return plan.document(id)
    }

    fun isPdfImported(documentId: String = DemoFamily.immunizationDocumentId): Boolean {
        val key = plan.document(documentId)?.storageKey() ?: return false
        return files.hasBytes(key)
    }

    fun importSyntheticImmunizationPdf(): Boolean = runLocal("import synthetic clinic PDF") {
        val bytes = buildSyntheticClinicPdf(pdfBodyText)
        require(bytes.isPdfHeader()) { "Synthetic PDF builder did not produce a PDF header." }
        require(bytes.containsLatin1("Example immunization A")) {
            "Synthetic PDF is missing the immunization A marker."
        }
        files.writeBytes(DemoFamily.immunizationPdfStorageKey, bytes)
        plan.setDocumentOriginal(
            DemoFamily.immunizationDocumentId,
            DocumentOriginal(
                fileName = "demo-immunizations.pdf",
                mediaType = "application/pdf",
                content = OriginalContent.StoredFile(DemoFamily.immunizationPdfStorageKey),
            ),
        )
        openedOriginal = null
        bump()
    }

    fun reopenOriginal(documentId: String): RetrievedOriginal? {
        return try {
            val document = plan.document(documentId)
                ?: error("Unknown document: $documentId")
            val retrieved = when (val content = document.original.content) {
                is OriginalContent.Text -> RetrievedOriginal(
                    fileName = document.original.fileName,
                    mediaType = document.original.mediaType,
                    storageKey = null,
                    byteSize = content.value.encodeToByteArray().size,
                    isPdf = false,
                    textPreview = content.value,
                )
                is OriginalContent.StoredFile -> {
                    val bytes = files.readBytes(content.storageKey)
                        ?: error("Stored original is missing for key ${content.storageKey}")
                    require(document.original.mediaType != "application/pdf" || bytes.isPdfHeader()) {
                        "Stored original for ${content.storageKey} is not a PDF (${bytes.size} bytes)."
                    }
                    RetrievedOriginal(
                        fileName = document.original.fileName,
                        mediaType = document.original.mediaType,
                        storageKey = content.storageKey,
                        byteSize = bytes.size,
                        isPdf = bytes.isPdfHeader(),
                        textPreview = if (bytes.isPdfHeader()) {
                            pdfBodyText.takeIf { bytes.containsLatin1("Example immunization A") }
                        } else {
                            bytes.decodeToString(throwOnInvalidSequence = false)
                        },
                    )
                }
            }
            openedOriginal = retrieved
            lastErrorDetail = null
            bump()
            retrieved
        } catch (error: Exception) {
            reportFailure("reopen original $documentId", error)
            null
        }
    }

    fun saveCorrection(documentId: String, fieldLabel: String, rawValue: String): Boolean {
        return when (val validation = validateReviewInput(fieldLabel, rawValue)) {
            is FieldValidationResult.Invalid -> {
                lastErrorDetail = validation.detail
                false
            }
            is FieldValidationResult.Ok -> runLocal("save review $documentId/$fieldLabel") {
                plan.reviewField(documentId, fieldLabel, FieldReview(validation.value))
                reviews.save(documentId, fieldLabel, validation.value)
                bump()
            }
        }
    }

    fun leaveUnresolved(documentId: String, fieldLabel: String): Boolean =
        runLocal("leave unresolved $documentId/$fieldLabel") {
            plan.reviewField(documentId, fieldLabel, FieldReview(null))
            reviews.save(documentId, fieldLabel, null)
            bump()
        }

    fun openRequestTasks(documentId: String): List<Task> {
        revision
        return plan.openTasksForDocument(documentId)
    }

    /** Links the existing open provider request without creating a duplicate. */
    fun linkExistingProviderRequest(documentId: String): Task? {
        revision
        return plan.openTasksForDocument(documentId).firstOrNull()
    }

    fun ensureProviderRequest(documentId: String): Task = run {
        val existing = plan.openTasksForDocument(documentId).firstOrNull()
        if (existing != null) return existing
        val document = plan.document(documentId) ?: error("Unknown document: $documentId")
        plan.linkOpenRequestTask(
            Task(
                id = "request-$documentId",
                personId = document.personId,
                title = "Request a copy of the immunization record",
                date = DemoFamily.today,
                status = TaskStatus.OPEN,
                sourceDocumentId = documentId,
            ),
        ).also { bump() }
    }

    fun recordedDoseSnapshot(documentId: String, doseName: String): RecordedImmunization? =
        plan.recordedImmunizations.firstOrNull {
            it.sourceDocumentId == documentId && it.name == doseName
        }

    fun fieldStatusLabel(documentId: String, fieldLabel: String): String {
        val field = plan.document(documentId)?.fields?.firstOrNull { it.label == fieldLabel }
            ?: return "Unknown field"
        return when {
            field.isExplicitlyUnresolved() -> "Explicitly unresolved — ask the clinic; no status assumed."
            field.isUntouched() && field.proposal != null ->
                "Untouched proposal: ${field.proposal.value}"
            field.isUntouched() -> "Not found on this document — leave unresolved and ask the clinic."
            else -> "Confirmed: ${field.review?.value}"
        }
    }

    private fun restoreReviews() {
        try {
            for ((key, value) in reviews.loadAll()) {
                if (plan.document(key.documentId)?.fields?.any { it.label == key.fieldLabel } == true) {
                    plan.reviewField(key.documentId, key.fieldLabel, FieldReview(value))
                }
            }
            lastErrorDetail = null
        } catch (error: Exception) {
            reportFailure("restore reviews", error)
        }
    }

    private fun bump() {
        revision += 1
    }

    private fun runLocal(operation: String, block: () -> Unit): Boolean =
        try {
            block()
            lastErrorDetail = null
            true
        } catch (error: Exception) {
            reportFailure(operation, error)
            false
        }

    private fun reportFailure(operation: String, error: Throwable) {
        val detail = "Could not $operation.\n${error.stackTraceToString()}"
        println(detail)
        lastErrorDetail = detail
    }
}

@Composable
fun rememberRecordsController(plan: CarePlan): RecordsController {
    val files = rememberDocumentFileStore()
    val reviews = rememberDocumentReviewStore()
    val childName = plan.people.firstOrNull { it.id == DemoFamily.childId }?.displayName ?: "Maya"
    return remember(plan, files, reviews, childName) {
        RecordsController(plan, files, reviews, DemoFamily.immunizationPdfBody(childName))
    }
}
