package vasinwr.distracttimernative

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.PowerManager
import android.os.SystemClock
import android.support.v4.app.ActivityCompat
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


class TimerActivity: AppCompatActivity() {

    companion object {
        private const val POLL_INTERVAL: Long = 300
    }

    private lateinit var recyclerView: RecyclerView
    private lateinit var viewAdapter: RecyclerView.Adapter<*>
    private lateinit var viewManager: RecyclerView.LayoutManager

    private lateinit var noiseThresholdInput: EditText
    private val noiseSensor = DetectNoise()
    private val handler = Handler()
    private val RECORD_AUDIO = 0
    private var noiseSensorRunning = false
    private var noiseThreshold = 0.0

    lateinit var distractionClock: MiliChrono
    lateinit var mainClock: MiliChrono
    var distractionClockStarted = false
    var lastStartedDistraction:Int = -1

    var onPausedTime = 0L
    var phoneDistractionLocal = 0L

    private val distractionData = DistractionDataSource.instance

    val distractionDurationLocal = LongArray(distractionData.distractions.size)

    private val noisePollTask = object: Runnable {
        override fun run() {
            val amp = noiseSensor.amplitude
            if (! "".equals(noiseThresholdInput.text.toString())) {
                noiseThreshold = noiseThresholdInput.text.toString().toDouble()
            }
            if (amp > noiseThreshold) {
                //TODO: take noise Threashold from View input
                Toast.makeText(
                    applicationContext, "Noise Thersold Crossed, amp=$amp.",
                    Toast.LENGTH_LONG
                ).show()
                handler.postDelayed(this, POLL_INTERVAL*10)
            }
            else {
                handler.postDelayed(this, POLL_INTERVAL)
            }
        }
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
        handler.postDelayed(noisePollTask, POLL_INTERVAL)
    }

    private fun stopNoiseDetection() {
        if (!noiseSensorRunning) {
            return
        }
        handler.removeCallbacks(noisePollTask)
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

        startNoiseDetection()
    }

    override fun onBackPressed() {
        for (i in 0..distractionDurationLocal.size-1) {
            distractionData.distractions[i].duration += distractionDurationLocal[i]
        }
        distractionData.totalTime += mainClock.timeElapsed
        distractionData.phoneTime += phoneDistractionLocal
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