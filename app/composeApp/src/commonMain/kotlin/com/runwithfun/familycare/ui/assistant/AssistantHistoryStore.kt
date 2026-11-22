package com.runwithfun.familycare.ui.assistant

import com.runwithfun.familycare.records.RecordsLocalStore

internal class AssistantHistoryStore(private val store: RecordsLocalStore) {
    fun read(key: String): String? {
        val stored = store.read(key) ?: return null
        val chunks = chunkIndex(stored) ?: return stored
        return buildString {
            repeat(chunks.count) { index ->
                val chunkKey = chunks.key(key, index)
                append(checkNotNull(store.read(chunkKey)) { "Missing Assistant history chunk $chunkKey." })
            }
        }
    }

    fun write(key: String, payload: String) {
        val previous = store.read(key)
        val previousChunks = previous?.let(::chunkIndex)
        val nextChunks = if (payload.length > MaxPreferenceValueLength) payload.chunked(MaxPreferenceValueLength) else emptyList()
        val nextIndex = if (nextChunks.isEmpty()) null else ChunkIndex(if (previousChunks?.slot == 'a') 'b' else 'a', nextChunks.size)

        try {
            if (nextIndex != null) {
                nextChunks.forEachIndexed { index, chunk -> store.write(nextIndex.key(key, index), chunk) }
            }
            // Publish only after all chunks exist. A crash while staging leaves the old history readable.
            store.write(key, nextIndex?.encode() ?: payload)
        } catch (failure: Exception) {
            try {
                if (store.read(key) != previous) store.write(key, previous)
                nextIndex?.let { removeChunks(key, it) }
            } catch (rollback: Exception) {
                if (rollback !== failure) failure.addSuppressed(rollback)
            }
            throw failure
        }

        if (previousChunks != null) {
            try {
                removeChunks(key, previousChunks)
            } catch (error: Exception) {
                println("Could not clean up prior Assistant history chunks for $key. Request: $previousChunks\n${error.stackTraceToString()}")
            }
        }
    }

    private fun removeChunks(key: String, chunks: ChunkIndex) {
        repeat(chunks.count) { index -> store.write(chunks.key(key, index), null) }
    }

    private data class ChunkIndex(val slot: Char, val count: Int) {
        fun encode(): String = "$ChunkPrefix$slot:$count"

        fun key(ownerKey: String, index: Int): String = "$ownerKey.$slot.$index"
    }

    private companion object {
        // The desktop Records store uses java.util.prefs.Preferences, which limits each value to 8,192 characters.
        const val MaxPreferenceValueLength = 8_192
        const val ChunkPrefix = "chunks-v1:"

        fun chunkIndex(value: String): ChunkIndex? {
            if (!value.startsWith(ChunkPrefix)) return null
            val fields = value.removePrefix(ChunkPrefix).split(':')
            val slot = fields.firstOrNull()?.singleOrNull()
            val count = fields.getOrNull(1)?.toIntOrNull()
            require(fields.size == 2 && (slot == 'a' || slot == 'b') && count != null && count > 0) {
                "Invalid Assistant history chunk index."
            }
            return ChunkIndex(slot, count)
        }
    }
}
