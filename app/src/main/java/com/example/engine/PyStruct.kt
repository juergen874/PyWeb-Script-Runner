package com.example.engine

import java.nio.ByteBuffer
import java.nio.ByteOrder

object PyStruct {

    fun calcsize(format: String): Int {
        var size = 0
        var i = 0
        val cleanFmt = if (format.startsWith(">") || format.startsWith("<") || format.startsWith("!") || format.startsWith("=")) {
            format.substring(1)
        } else format

        while (i < cleanFmt.length) {
            var countStr = ""
            while (i < cleanFmt.length && cleanFmt[i].isDigit()) {
                countStr += cleanFmt[i]
                i++
            }
            val count = if (countStr.isNotEmpty()) countStr.toInt() else 1
            if (i >= cleanFmt.length) break
            val char = cleanFmt[i]
            size += when (char) {
                'b', 'B', 'c', '?' -> 1 * count
                'h', 'H' -> 2 * count
                'i', 'I', 'l', 'L', 'f' -> 4 * count
                'q', 'Q', 'd' -> 8 * count
                's', 'p' -> count
                else -> 1 * count
            }
            i++
        }
        return size
    }

    fun pack(format: String, args: List<PyValue>): ByteArray {
        val isBigEndian = !format.startsWith("<")
        val order = if (isBigEndian) ByteOrder.BIG_ENDIAN else ByteOrder.LITTLE_ENDIAN

        val totalSize = calcsize(format).coerceAtLeast(1)
        val buffer = ByteBuffer.allocate(totalSize).order(order)

        var argIdx = 0
        var i = 0
        val cleanFmt = if (format.startsWith(">") || format.startsWith("<") || format.startsWith("!") || format.startsWith("=")) {
            format.substring(1)
        } else format

        while (i < cleanFmt.length) {
            var countStr = ""
            while (i < cleanFmt.length && cleanFmt[i].isDigit()) {
                countStr += cleanFmt[i]
                i++
            }
            val count = if (countStr.isNotEmpty()) countStr.toInt() else 1
            if (i >= cleanFmt.length) break
            val char = cleanFmt[i]

            if (char == 's' || char == 'p') {
                val arg = args.getOrNull(argIdx++)
                val bytes = when (arg) {
                    is PyValue.BytesVal -> arg.data
                    is PyValue.StringVal -> arg.value.toByteArray(Charsets.UTF_8)
                    else -> ByteArray(0)
                }
                for (bIdx in 0 until count) {
                    if (bIdx < bytes.size) buffer.put(bytes[bIdx])
                    else buffer.put(0.toByte())
                }
            } else {
                for (c in 0 until count) {
                    val arg = args.getOrNull(argIdx++) ?: PyValue.IntVal(0)
                    val num = when (arg) {
                        is PyValue.IntVal -> arg.value
                        is PyValue.FloatVal -> arg.value.toLong()
                        else -> 0L
                    }
                    when (char) {
                        'b' -> buffer.put(num.toByte())
                        'B' -> buffer.put((num and 0xFF).toByte())
                        'h' -> buffer.putShort(num.toShort())
                        'H' -> buffer.putShort((num and 0xFFFF).toShort())
                        'i', 'l' -> buffer.putInt(num.toInt())
                        'I', 'L' -> buffer.putInt((num and 0xFFFFFFFFL).toInt())
                        'q', 'Q' -> buffer.putLong(num)
                        'f' -> {
                            val fVal = (arg as? PyValue.FloatVal)?.value?.toFloat() ?: num.toFloat()
                            buffer.putFloat(fVal)
                        }
                        'd' -> {
                            val dVal = (arg as? PyValue.FloatVal)?.value ?: num.toDouble()
                            buffer.putDouble(dVal)
                        }
                    }
                }
            }
            i++
        }
        return buffer.array()
    }

    fun unpack(format: String, data: ByteArray): List<PyValue> {
        val isBigEndian = !format.startsWith("<")
        val order = if (isBigEndian) ByteOrder.BIG_ENDIAN else ByteOrder.LITTLE_ENDIAN
        val buffer = ByteBuffer.wrap(data).order(order)
        val result = mutableListOf<PyValue>()

        var i = 0
        val cleanFmt = if (format.startsWith(">") || format.startsWith("<") || format.startsWith("!") || format.startsWith("=")) {
            format.substring(1)
        } else format

        while (i < cleanFmt.length && buffer.hasRemaining()) {
            var countStr = ""
            while (i < cleanFmt.length && cleanFmt[i].isDigit()) {
                countStr += cleanFmt[i]
                i++
            }
            val count = if (countStr.isNotEmpty()) countStr.toInt() else 1
            if (i >= cleanFmt.length) break
            val char = cleanFmt[i]

            if (char == 's' || char == 'p') {
                val readBytes = ByteArray(count)
                val toRead = count.coerceAtMost(buffer.remaining())
                buffer.get(readBytes, 0, toRead)
                result.add(PyValue.BytesVal(readBytes))
            } else {
                for (c in 0 until count) {
                    if (!buffer.hasRemaining()) break
                    when (char) {
                        'b' -> result.add(PyValue.IntVal(buffer.get().toLong()))
                        'B' -> result.add(PyValue.IntVal((buffer.get().toInt() and 0xFF).toLong()))
                        'h' -> result.add(PyValue.IntVal(buffer.short.toLong()))
                        'H' -> result.add(PyValue.IntVal((buffer.short.toInt() and 0xFFFF).toLong()))
                        'i', 'l' -> result.add(PyValue.IntVal(buffer.int.toLong()))
                        'I', 'L' -> result.add(PyValue.IntVal((buffer.int.toLong() and 0xFFFFFFFFL)))
                        'q', 'Q' -> result.add(PyValue.IntVal(buffer.long))
                        'f' -> result.add(PyValue.FloatVal(buffer.float.toDouble()))
                        'd' -> result.add(PyValue.FloatVal(buffer.double))
                        else -> result.add(PyValue.IntVal(buffer.get().toLong()))
                    }
                }
            }
            i++
        }
        return result
    }

    fun crc16Modbus(data: ByteArray): Int {
        var crc = 0xFFFF
        for (b in data) {
            crc = crc xor (b.toInt() and 0xFF)
            for (i in 0 until 8) {
                crc = if ((crc and 0x0001) != 0) {
                    (crc ushr 1) xor 0xA001
                } else {
                    crc ushr 1
                }
            }
        }
        return crc and 0xFFFF
    }
}
