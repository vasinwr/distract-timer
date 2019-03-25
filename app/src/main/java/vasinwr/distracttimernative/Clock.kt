package vasinwr.distracttimernative

import android.databinding.Observable
import android.databinding.ObservableField
import android.os.Handler

class Clock() {
    var time: Int = 0
    var timeStr = ObservableField(Integer.toString(time))

    val handler = Handler()

    var started = false

    val tickForever : Runnable = object: Runnable {
        override fun run() {
            if (started) {
                time += 1
                timeStr.set(Integer.toString(time))
                handler.postDelayed(this, 1000)
            }
        }
    }

    init {
        handler.post(tickForever)
    }

    fun start() {
        started = true
    }

    fun stop() {
        started = false
    }

}