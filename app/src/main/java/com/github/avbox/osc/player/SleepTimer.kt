package com.github.avbox.osc.player

import android.os.Handler
import android.os.Looper

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object SleepTimer {

    val optionsMinutes = listOf(0, 5, 10, 15, 30, 45, 60, 90)

    private val _selectedMinutes = MutableStateFlow(0)
    val selectedMinutes: StateFlow<Int> = _selectedMinutes.asStateFlow()

    private val _remainingMs = MutableStateFlow(0L)
    val remainingMs: StateFlow<Long> = _remainingMs.asStateFlow()

    private val handler = Handler(Looper.getMainLooper())
    private var deadlineMs = 0L

    private val tick = object : Runnable {
        override fun run() {
            val remaining = deadlineMs - System.currentTimeMillis()
            if (remaining <= 0L) {
                onTimeout()
                return
            }
            _remainingMs.value = remaining
            handler.postDelayed(this, TICK_MS)
        }
    }

    fun select(minutes: Int) {
        cancelTimer()
        _selectedMinutes.value = minutes
        if (minutes <= 0) return
        deadlineMs = System.currentTimeMillis() + minutes * 60_000L
        _remainingMs.value = minutes * 60_000L
        handler.postDelayed(tick, TICK_MS)
    }

    private fun cancelTimer() {
        handler.removeCallbacks(tick)
        deadlineMs = 0L
        _remainingMs.value = 0L
    }

    private fun onTimeout() {
        cancelTimer()
        _selectedMinutes.value = 0
        PlaybackService.peek()?.pauseFromMediaSession()
    }

    private const val TICK_MS = 1000L
}
