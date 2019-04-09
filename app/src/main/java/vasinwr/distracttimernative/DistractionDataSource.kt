package vasinwr.distracttimernative

class DistractionDataSource {
    companion object {
        val instance = DistractionDataSource()
    }

    val distractions : MutableList<Distraction> = ArrayList()
    var totalTime: Long = 0L
    val focusedTime: Long
        get() = totalTime - getTotalDistractedTime() - phoneTime - noisyTime
    var phoneTime: Long = 0L
    var noisyTime: Long = 0L

    init {
//        populateWithEmojiSample()
    }


    private fun populateWithSampleData(size: Int) {
        for (i in 1..size) {
            distractions.add(Distraction("distraction sample $i", 0L))
        }
    }

    private fun populateWithEmojiSample() {

    }

    private fun getTotalDistractedTime(): Long{
        var sum = 0L
        for (i in distractions) {
            sum += i.duration
        }
        return sum
    }
}

data class Distraction(var name: String, var duration: Long)

