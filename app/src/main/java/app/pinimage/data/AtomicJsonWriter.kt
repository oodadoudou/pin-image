package app.pinimage.data

import android.util.AtomicFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import java.io.File

/** Serializes atomic writes off the main thread. Repositories live for the app process. */
internal class AtomicJsonWriter(file: File) {
    private val atomicFile = AtomicFile(file)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val writes = Channel<String>(Channel.CONFLATED)

    init {
        scope.launch {
            for (text in writes) writeNow(text)
        }
    }

    fun write(text: String) {
        writes.trySend(text)
    }

    private fun writeNow(text: String) {
        var stream: java.io.FileOutputStream? = null
        try {
            stream = atomicFile.startWrite()
            stream.write(text.toByteArray(Charsets.UTF_8))
            atomicFile.finishWrite(stream)
        } catch (_: Exception) {
            if (stream != null) atomicFile.failWrite(stream)
        }
    }
}

internal fun readAtomicText(file: File): String =
    AtomicFile(file).openRead().bufferedReader(Charsets.UTF_8).use { it.readText() }
