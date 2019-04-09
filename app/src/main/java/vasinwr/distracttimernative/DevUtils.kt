package vasinwr.distracttimernative

class DevUtils {
    companion object {
        fun longToDateString(miliSec: Long): String {

            val hours = (miliSec / (60 * 60 * 1000)).toInt()
            val minutes = ((miliSec % (60 * 60 * 1000)) / (60 * 1000)).toInt()
            val seconds = (((miliSec % (60 * 60 * 1000)) % (60 * 1000)) / 1000).toInt()

            return "$hours hour, $minutes minute, $seconds second"
        }

        fun longToMinuteString(miliSec: Long): String {

            val hours = (miliSec / (60 * 60 * 1000)).toInt()
            val minutes = ((miliSec % (60 * 60 * 1000)) / (60 * 1000)).toInt()
            val seconds = (((miliSec % (60 * 60 * 1000)) % (60 * 1000)) / 1000).toInt()

            return "$minutes minute, $seconds second"
        }
    }

}