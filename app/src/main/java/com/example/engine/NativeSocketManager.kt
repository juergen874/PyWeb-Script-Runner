package com.example.engine

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

data class ManagedSocket(
    val id: String,
    val socket: Socket,
    val inputStream: InputStream,
    val outputStream: OutputStream,
    val host: String,
    val port: Int
)

class NativeSocketManager {

    private val sockets = ConcurrentHashMap<String, ManagedSocket>()

    suspend fun openSocket(host: String, port: Int, timeoutMs: Int = 5000): Result<String> = withContext(Dispatchers.IO) {
        try {
            val socket = Socket()
            socket.soTimeout = timeoutMs
            socket.tcpNoDelay = true
            socket.connect(InetSocketAddress(host, port), timeoutMs)
            val socketId = UUID.randomUUID().toString()
            val managed = ManagedSocket(
                id = socketId,
                socket = socket,
                inputStream = socket.getInputStream(),
                outputStream = socket.getOutputStream(),
                host = host,
                port = port
            )
            sockets[socketId] = managed
            Log.d("NativeSocket", "Connected to $host:$port (id: $socketId)")
            Result.success(socketId)
        } catch (e: Exception) {
            Log.e("NativeSocket", "Failed to connect to $host:$port", e)
            Result.failure(e)
        }
    }

    suspend fun sendBytes(socketId: String, data: ByteArray): Result<Int> = withContext(Dispatchers.IO) {
        val managed = sockets[socketId] ?: return@withContext Result.failure(IllegalStateException("Socket $socketId not found"))
        try {
            managed.outputStream.write(data)
            managed.outputStream.flush()
            Result.success(data.size)
        } catch (e: Exception) {
            Log.e("NativeSocket", "Send error on $socketId", e)
            Result.failure(e)
        }
    }

    suspend fun receiveBytes(socketId: String, maxBytes: Int = 1024, timeoutMs: Int = 5000): Result<ByteArray> = withContext(Dispatchers.IO) {
        val managed = sockets[socketId] ?: return@withContext Result.failure(IllegalStateException("Socket $socketId not found"))
        try {
            managed.socket.soTimeout = timeoutMs
            val buffer = ByteArray(maxBytes)
            val bytesRead = managed.inputStream.read(buffer)
            if (bytesRead == -1) {
                Result.success(ByteArray(0))
            } else {
                Result.success(buffer.copyOf(bytesRead))
            }
        } catch (e: Exception) {
            Log.e("NativeSocket", "Recv error on $socketId", e)
            Result.failure(e)
        }
    }

    fun closeSocket(socketId: String) {
        val managed = sockets.remove(socketId)
        try {
            managed?.socket?.close()
        } catch (ignored: Exception) {}
    }

    fun closeAll() {
        for ((_, managed) in sockets) {
            try {
                managed.socket.close()
            } catch (ignored: Exception) {}
        }
        sockets.clear()
    }
}
