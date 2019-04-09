package vasinwr.distracttimernative

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.SystemClock
import android.support.v4.app.ActivityCompat
import android.support.v4.content.ContextCompat
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import org.w3c.dom.Text


class TimerActivity: AppCompatActivity() {

    companion object {
        private const val POLL_INTERVAL: Long = 1000
    }

    private lateinit var recyclerView: RecyclerView
    private lateinit var viewAdapter: RecyclerView.Adapter<*>
    private lateinit var viewManager: RecyclerView.LayoutManager

    private lateinit var noiseThresholdInput: EditText
    private val noiseSensor = DetectNoise()
    private val handler = Handler()
    private val RECORD_AUDIO = 0
    private var noiseSensorRunning = false
    private var DEFAULT_NOISE_THRESHOLD = 5.0
    private var noisyTimerStartedTime : Long = -1

    private val verifyNoiseAmpList = mutableListOf<Double>()
    private val calibrateNoiseAmpList = mutableListOf<Double>()
    private var verifyNoiseStartedTime : Long = -1
    private var calibrateNoiseStartedTime : Long = -1
    private val NOISE_VERIFY_POLL_INTERVAL: Long = 100
    private val NOISE_VERIFY_DURATION = 3000
    private val RATIO_NOISY_THRESHOLD = 0.2


    lateinit var distractionClock: MiliChrono
    lateinit var mainClock: MiliChrono


    var distractionClockStarted = false
    var lastStartedDistraction:Int = -1

    var onPausedTime = 0L
    var phoneDistractionLocal = 0L
    var noiseDistractionLocal = 0L

    private val distractionData = DistractionDataSource.instance

    val distractionDurationLocal = LongArray(distractionData.distractions.size)

    private val calibrateNoiseTask = object: Runnable {
        override fun run() {
            val amp = noiseSensor.amplitude
            if (SystemClock.elapsedRealtime() - calibrateNoiseStartedTime < NOISE_VERIFY_DURATION) {
                calibrateNoiseAmpList.add(amp)
                handler.postDelayed(this, NOISE_VERIFY_POLL_INTERVAL)
            }
            else {
                findViewById<EditText>(R.id.noiseThreshold)
                    .setText(calibrationResult(calibrateNoiseAmpList).toString())
                calibrateNoiseStartedTime = -1
                calibrateNoiseAmpList.clear()
                findViewById<Button>(R.id.calibrateNoise).isEnabled = true
            }
        }
    }


    private val noisePollTask = object: Runnable {
        override fun run() {
            val amp = noiseSensor.amplitude
            val threshold = tryGetNoiseThresholdFromInput()
            if (amp > threshold) {
                gettingNoisy()
            }
            else {
                handler.postDelayed(this, POLL_INTERVAL)
            }
        }
    }

    private val verifyNoisy = object: Runnable {
        override fun run() {
            val amp = noiseSensor.amplitude

            if (SystemClock.elapsedRealtime() - verifyNoiseStartedTime < NOISE_VERIFY_DURATION) {
                verifyNoiseAmpList.add(amp)
                handler.postDelayed(this, NOISE_VERIFY_POLL_INTERVAL)
            } else {
                val ratioNoisy = findNoisyRatio(verifyNoiseAmpList, tryGetNoiseThresholdFromInput())
                verifyNoiseAmpList.clear()
                if (ratioNoisy > RATIO_NOISY_THRESHOLD) {
                    verifyNoiseStartedTime = SystemClock.elapsedRealtime()
                    handler.postDelayed(this, NOISE_VERIFY_POLL_INTERVAL)
                } else {
                    noLongerNoisy()
                }
            }
        }
    }

    private fun gettingNoisy() {
        Toast.makeText(applicationContext, "getting noisy",Toast.LENGTH_LONG).show()
        findViewById<TextView>(R.id.noiseDistractItem).setBackgroundColor(ContextCompat.getColor(this, R.color.AliceBrightBlue))

        noisyTimerStartedTime = SystemClock.elapsedRealtime()
        verifyNoiseStartedTime = SystemClock.elapsedRealtime()
        handler.postDelayed(verifyNoisy, NOISE_VERIFY_POLL_INTERVAL)
    }

    private fun noLongerNoisy() {
        val noisyTime = SystemClock.elapsedRealtime() - noisyTimerStartedTime
        findViewById<TextView>(R.id.noiseDistractItem).setBackgroundColor(ContextCompat.getColor(this, R.color.AliceBlue))

        if (noisyTime > 5000){
            Toast.makeText(applicationContext, "noisy time = ${noisyTime/1000} seconds",Toast.LENGTH_LONG).show()

            val noiseRow = findViewById<TextView>(R.id.noiseDistractItem)
            noiseDistractionLocal += noisyTime
            noiseRow.text = "Noise distraction = ${DevUtils.longToMinuteString(noiseDistractionLocal)}"

            noisyTimerStartedTime = -1
            handler.postDelayed(noisePollTask, POLL_INTERVAL)
        } else {
            Toast.makeText(applicationContext, "wasn't really noisy",Toast.LENGTH_SHORT).show()
            handler.postDelayed(noisePollTask, POLL_INTERVAL)
        }

    }

    private fun findNoisyRatio(amps: List<Double>, threshold : Double) : Double {
        return amps.filter { amp -> amp > threshold }.size.toDouble() / (amps.size)
    }

    private fun tryGetNoiseThresholdFromInput(): Double {
        if (! "".equals(noiseThresholdInput.text.toString())) {
            return noiseThresholdInput.text.toString().toDouble()
        }
        return DEFAULT_NOISE_THRESHOLD
    }

    private fun calibrationResult(amps: List<Double>) : Double {
        // find lowest threshold which catigorise 'amps' as noisy
        var threshold = -30.0
        //naive way
        while (findNoisyRatio(amps, threshold) > RATIO_NOISY_THRESHOLD) {
            threshold += 0.1
        }
        return threshold
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_timer)

        viewManager = LinearLayoutManager(this)
        viewAdapter = TimerActivity.MyAdapter(distractionData.distractions, distractionDurationLocal, ::startDistractionClock)

        recyclerView = findViewById<RecyclerView>(R.id.distractionsButton).apply {
            setHasFixedSize(true)
            layoutManager = viewManager
            adapter = viewAdapter
        }

        mainClock = findViewById(R.id.mainClock)
        mainClock.base = SystemClock.elapsedRealtime()
        mainClock.start()

        distractionClock = findViewById(R.id.secondaryClock)

        val buttonStart = findViewById<Button>(R.id.buttonStartChronometer)
        buttonStart.setOnClickListener {
            if (distractionClockStarted) {
                stopDistractionClock()
            }
        }

        noiseThresholdInput = findViewById(R.id.noiseThreshold)

        findViewById<TextView>(R.id.noiseThreshold).setText(DEFAULT_NOISE_THRESHOLD.toString())

        findViewById<Button>(R.id.calibrateNoise).setOnClickListener {
            calibrateNoiseStartedTime = SystemClock.elapsedRealtime()
            handler.postDelayed(calibrateNoiseTask, NOISE_VERIFY_POLL_INTERVAL)
            (it as Button).isEnabled = false
        }
    }

    private fun startNoiseDetection() {
        if (noiseSensorRunning) {
            return
        }
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {

            ActivityCompat.requestPermissions(
                this, arrayOf(Manifest.permission.RECORD_AUDIO),
                RECORD_AUDIO
            )
        }
        noiseSensor.start()
        noiseSensorRunning = true
        handler.postDelayed(noisePollTask, POLL_INTERVAL)
    }

    private fun stopNoiseDetection() {
        if (!noiseSensorRunning) {
            return
        }
        handler.removeCallbacks(noisePollTask)
        handler.removeCallbacks(verifyNoisy)
        noiseSensor.stop()
        noiseSensorRunning = false
    }

    override fun onPause() {
        super.onPause()
        onPausedTime = SystemClock.elapsedRealtime()

        stopNoiseDetection()
    }

    override fun onResume() {
        super.onResume()
        if (onPausedTime != 0L) {
            phoneDistractionLocal += SystemClock.elapsedRealtime() - onPausedTime
        }
        val phoneRow = findViewById<TextView>(R.id.phoneDistractItem)
        phoneRow.text = "Phone distraction = ${DevUtils.longToDateString(phoneDistractionLocal)}"

        val noiseRow = findViewById<TextView>(R.id.noiseDistractItem)
        noiseRow.text = "Noise distraction = ${DevUtils.longToDateString(noiseDistractionLocal)}"

        startNoiseDetection()
    }

    override fun onBackPressed() {
        for (i in 0..distractionDurationLocal.size-1) {
            distractionData.distractions[i].duration += distractionDurationLocal[i]
        }
        distractionData.totalTime += mainClock.timeElapsed
        distractionData.phoneTime += phoneDistractionLocal
        distractionData.noisyTime += noiseDistractionLocal
        super.onBackPressed()
    }

    private fun startDistractionClock(distractionPosition: Int) {
        lastStartedDistraction = distractionPosition
        distractionClock.base = SystemClock.elapsedRealtime()
        distractionClock.start()
        distractionClockStarted = true
    }

    private fun stopDistractionClock() {
        distractionClock.stop()
        val timeElapsed = resetDistractionClock()
        distractionClockStarted = false
        distractionDurationLocal[lastStartedDistraction] += timeElapsed
//        distractionData.distractions[lastStartedDistraction].duration += timeElapsed
        viewAdapter.notifyItemChanged(lastStartedDistraction)
    }

    private fun resetDistractionClock(): Long {
        val timeElapsed = distractionClock.timeElapsed
        distractionClock.base = SystemClock.elapsedRealtime()
        return timeElapsed
    }

    class MyAdapter(private val myDataset: List<Distraction>, private val distractionDurationLocal: LongArray, var clickCallback: (Int) -> Unit) :
        RecyclerView.Adapter<MyAdapter.MyViewHolder>() {

        class MyViewHolder(itemView: View, var clickCallback: (Int) -> Unit) : RecyclerView.ViewHolder(itemView) {
            var button: Button = itemView.findViewById(R.id.distraction_button)
            var label: TextView = itemView.findViewById(R.id.distraction_name)
        }

        override fun onCreateViewHolder(parent: ViewGroup, p1: Int): MyViewHolder {
            val row = LayoutInflater.from(parent.context)
                .inflate(R.layout.distraction_item_button, parent, false) as View

            return MyViewHolder(row, clickCallback)
        }

        override fun onBindViewHolder(holder: MyViewHolder, position: Int) {
            holder.button.text = myDataset[position].name
            holder.label.text = "duration: ${DevUtils.longToDateString(distractionDurationLocal[position])}"

            holder.button.setOnClickListener {
                holder.label.text = "* timer started *"
                holder.clickCallback(position)
            }
        }

        override fun getItemCount() = myDataset.size
    }
}