package com.dfshine.launcher.vehicle

import android.os.Parcel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Ring buffer of the raw CAN-service traffic (events received + getter
 * replies) for the diagnostics screen, so a wrong decode on this firmware
 * can be spotted and fixed from real bytes instead of guesses.
 */
object RawLog {
    data class Entry(
        val timeMs: Long,
        val kind: String,
        val name: String,
        val code: Int,
        val size: Int,
        val ints: String
    ) {
        fun format(): String =
            "${TIME.format(Date(timeMs))} $kind $name#$code size=$size [$ints]"
    }

    private const val MAX = 300
    private val TIME = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
    private val buffer = ArrayDeque<Entry>()
    private val _entries = MutableStateFlow<List<Entry>>(emptyList())
    val entries: StateFlow<List<Entry>> = _entries

    /** Records the payload from [start] as a list of 32-bit words (ints, with float view when it looks like one). */
    fun add(kind: String, name: String, code: Int, p: Parcel, start: Int) {
        val size = (p.dataSize() - start).coerceAtLeast(0)
        val words = StringBuilder()
        val saved = p.dataPosition()
        try {
            p.setDataPosition(start)
            var n = 0
            while (p.dataAvail() >= 4 && n < 24) {
                val i = p.readInt()
                if (n > 0) words.append(", ")
                words.append(i)
                val f = java.lang.Float.intBitsToFloat(i)
                if (i != 0 && (i > 0xFFFF || i < -0xFFFF) && !f.isNaN() && kotlin.math.abs(f) in 0.01f..100000f) {
                    words.append("/").append(String.format(Locale.US, "%.2ff", f))
                }
                n++
            }
            if (p.dataAvail() > 0) words.append(", …")
        } catch (_: Throwable) {
        } finally {
            p.setDataPosition(saved)
        }
        synchronized(buffer) {
            buffer.addLast(Entry(System.currentTimeMillis(), kind, name, code, size, words.toString()))
            while (buffer.size > MAX) buffer.removeFirst()
            _entries.value = buffer.toList()
        }
    }

    /** Free-text entry (used for commands we send). */
    fun note(kind: String, name: String, code: Int, text: String) = synchronized(buffer) {
        buffer.addLast(Entry(System.currentTimeMillis(), kind, name, code, 0, text))
        while (buffer.size > MAX) buffer.removeFirst()
        _entries.value = buffer.toList()
    }

    fun clear() = synchronized(buffer) {
        buffer.clear()
        _entries.value = emptyList()
    }

    fun dump(): String = synchronized(buffer) { buffer.joinToString("\n") { it.format() } }
}
