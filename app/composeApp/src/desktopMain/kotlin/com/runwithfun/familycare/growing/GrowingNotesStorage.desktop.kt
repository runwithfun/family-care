package com.runwithfun.familycare.growing

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import java.nio.file.StandardCopyOption

@Composable
internal actual fun rememberGrowingNotesStorage(): GrowingNotesStorage = remember {
    val file = Path.of(System.getProperty("user.home"), ".family-care", "growing-notes")
    object : GrowingNotesStorage {
        override fun read(): String? = try {
            Files.readString(file)
        } catch (_: NoSuchFileException) {
            null
        }

        override fun write(value: String?) {
            if (value == null) {
                Files.deleteIfExists(file)
                return
            }
            Files.createDirectories(file.parent)
            val temporary = Files.createTempFile(file.parent, "growing-notes-", ".tmp")
            try {
                Files.writeString(temporary, value)
                Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            } catch (error: Exception) {
                try {
                    Files.deleteIfExists(temporary)
                } catch (cleanup: Exception) {
                    error.addSuppressed(cleanup)
                }
                throw error
            }
        }
    }
}
