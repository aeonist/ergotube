package app.freshtuber.data.account

import app.freshtuber.core.security.BlobCipher
import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException

/**
 * Reads and writes [StoredSessions] as one encrypted file. Blocking: call from a background thread.
 */
class SessionFileStore(
    private val file: File,
    private val cipher: BlobCipher,
) {
    private val json = Json { ignoreUnknownKeys = true }

    /** Returns empty sessions when the file is missing or cannot be decrypted (e.g. key was reset). */
    fun read(): StoredSessions {
        if (!file.exists()) return StoredSessions()
        return try {
            val plain = cipher.decrypt(file.readBytes())
            json.decodeFromString(StoredSessions.serializer(), plain.decodeToString())
        } catch (_: Exception) {
            StoredSessions()
        }
    }

    fun write(sessions: StoredSessions) {
        if (sessions.profiles.isEmpty()) {
            delete()
            return
        }
        val blob = cipher.encrypt(json.encodeToString(StoredSessions.serializer(), sessions).encodeToByteArray())
        file.parentFile?.mkdirs()
        val temp = File(file.parentFile, "${file.name}.tmp")
        temp.writeBytes(blob)
        if (!temp.renameTo(file)) {
            temp.delete()
            throw IOException("Could not replace session file")
        }
    }

    fun delete() {
        file.delete()
        File(file.parentFile, "${file.name}.tmp").delete()
    }
}
