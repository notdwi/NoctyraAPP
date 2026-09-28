package com.noctyra.app.party

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import java.net.Socket
import java.util.concurrent.atomic.AtomicBoolean

internal val partyJson = Json { ignoreUnknownKeys = true; encodeDefaults = false; explicitNulls = false }

/** Uma conexão TCP com mensagens JSON, uma por linha. */
internal class PartyConnection(
    private val socket: Socket,
    private val scope: CoroutineScope,
    private val onMessage: (Msg) -> Unit,
    private val onClosed: () -> Unit
) {
    private val outbox = Channel<String>(128, BufferOverflow.DROP_OLDEST)
    private val closed = AtomicBoolean(false)

    fun start() {
        scope.launch(Dispatchers.IO) {
            runCatching {
                val writer = socket.getOutputStream().bufferedWriter(Charsets.UTF_8)
                for (line in outbox) {
                    writer.write(line)
                    writer.write('\n'.code)
                    writer.flush()
                }
            }
            close()
        }
        scope.launch(Dispatchers.IO) {
            runCatching {
                val reader = socket.getInputStream().bufferedReader(Charsets.UTF_8)
                while (!closed.get()) {
                    val line = reader.readLine() ?: break
                    if (line.length > 16_384) continue
                    val msg = runCatching { partyJson.decodeFromString(Msg.serializer(), line) }.getOrNull() ?: continue
                    onMessage(msg)
                }
            }
            close()
        }
    }

    fun send(msg: Msg) {
        if (!closed.get()) outbox.trySend(partyJson.encodeToString(Msg.serializer(), msg))
    }

    fun close() {
        if (!closed.compareAndSet(false, true)) return
        outbox.close()
        runCatching { socket.close() }
        onClosed()
    }
}
