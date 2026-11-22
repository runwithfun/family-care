package com.runwithfun.familycare.records

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/** On-device key/value text store used by Records file and review persistence. */
interface RecordsLocalStore {
    fun read(key: String): String?
    fun write(key: String, value: String?)
}

@Composable
expect fun rememberRecordsLocalStore(): RecordsLocalStore

class InMemoryRecordsLocalStore(
    initial: Map<String, String> = emptyMap(),
) : RecordsLocalStore {
    private val values = initial.toMutableMap()

    override fun read(key: String): String? = values[key]

    override fun write(key: String, value: String?) {
        if (value == null) values.remove(key) else values[key] = value
    }

    fun snapshot(): Map<String, String> = values.toMap()
}

@OptIn(ExperimentalEncodingApi::class)
class DocumentFileStore(private val store: RecordsLocalStore) {
    fun writeBytes(storageKey: String, bytes: ByteArray) {
        store.write(fileKey(storageKey), Base64.encode(bytes))
    }

    fun readBytes(storageKey: String): ByteArray? {
        val encoded = store.read(fileKey(storageKey)) ?: return null
        return Base64.decode(encoded)
    }

    fun hasBytes(storageKey: String): Boolean = store.read(fileKey(storageKey)) != null

    private fun fileKey(storageKey: String) = "familycare.records.file.$storageKey"
}

class DocumentReviewStore(private val store: RecordsLocalStore) {
    fun loadAll(): Map<ReviewKey, String?> {
        val payload = store.read(ReviewsKey) ?: return emptyMap()
        return payload.lineSequence()
            .filter { it.isNotBlank() }
            .mapNotNull { line ->
                val parts = line.split('\t')
                if (parts.size != 3) return@mapNotNull null
                val value = when (parts[2]) {
                    MISSING -> null
                    else -> parts[2]
                }
                ReviewKey(parts[0], parts[1]) to value
            }
            .toMap()
    }

    fun save(documentId: String, fieldLabel: String, value: String?) {
        val next = loadAll().toMutableMap()
        next[ReviewKey(documentId, fieldLabel)] = value
        persist(next)
    }

    private fun persist(values: Map<ReviewKey, String?>) {
        val encoded = values.entries
            .sortedWith(compareBy({ it.key.documentId }, { it.key.fieldLabel }))
            .joinToString("\n") { (key, value) ->
                val encodedValue = value ?: MISSING
                "${key.documentId}\t${key.fieldLabel}\t$encodedValue"
            }
        store.write(ReviewsKey, encoded.ifEmpty { null })
    }

    data class ReviewKey(val documentId: String, val fieldLabel: String)

    companion object {
        const val ReviewsKey = "familycare.records.reviews"
        private const val MISSING = "__MISSING__"
    }
}

@Composable
fun rememberDocumentFileStore(): DocumentFileStore {
    val store = rememberRecordsLocalStore()
    return remember(store) { DocumentFileStore(store) }
}

@Composable
fun rememberDocumentReviewStore(): DocumentReviewStore {
    val store = rememberRecordsLocalStore()
    return remember(store) { DocumentReviewStore(store) }
}
