package vasinwr.distracttimernative

import android.Manifest
import android.Manifest.permission.RECORD_AUDIO
import android.content.Intent
import android.content.pm.PackageManager
import android.databinding.DataBindingUtil
import android.databinding.ObservableField
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.provider.AlarmClock.EXTRA_MESSAGE
import android.support.design.widget.FloatingActionButton
import android.support.v4.app.ActivityCompat
import android.support.v4.content.ContextCompat
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.Button
import android.widget.Chronometer
import android.widget.EditText
import android.widget.TextView
import vasinwr.distracttimernative.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var viewAdapter: RecyclerView.Adapter<*>
    private lateinit var viewManager: RecyclerView.LayoutManager

    private val distractionData = DistractionDataSource.instance

    val PICK_CONTACT_REQUEST = 1  // The request code

    val MY_PERMISSIONS_REQUEST_READ_CONTACTS = 2

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        viewManager = LinearLayoutManager(this)
        viewAdapter = MyAdapter(distractionData.distractions)

        recyclerView = findViewById<RecyclerView>(R.id.distractionList).apply {
            setHasFixedSize(true)
            layoutManager = viewManager
            adapter = viewAdapter
        }

        val addButton = findViewById<Button>(R.id.addDistractionButton)
        addButton.setOnClickListener { addDistraction() }

        val playButton = findViewById<FloatingActionButton>(R.id.startTimerPlayButton)
        playButton.setOnClickListener { startTimerActivity() }

        tryRequestPermission()
    }

    private fun tryRequestPermission(){
        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) {

            // Permission is not granted
            // Should we show an explanation?
            if (ActivityCompat.shouldShowRequestPermissionRationale(this,
                    Manifest.permission.RECORD_AUDIO)) {
                // Show an explanation to the user *asynchronously* -- don't block
                // this thread waiting for the user's response! After the user
                // sees the explanation, try again to request the permission.
            } else {
                // No explanation needed, we can request the permission.
                ActivityCompat.requestPermissions(this,
                    arrayOf(Manifest.permission.RECORD_AUDIO),
                    MY_PERMISSIONS_REQUEST_READ_CONTACTS)

                // MY_PERMISSIONS_REQUEST_READ_CONTACTS is an
                // app-defined int constant. The callback method gets the
                // result of the request.
            }
        } else {
            // Permission has already been granted
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int,
                                            permissions: Array<String>, grantResults: IntArray) {
        when (requestCode) {
            MY_PERMISSIONS_REQUEST_READ_CONTACTS -> {
                // If request is cancelled, the result arrays are empty.
                if ((grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                    // permission was granted, yay! Do the
                    // contacts-related task you need to do.
                } else {
                    // permission denied, boo! Disable the
                    // functionality that depends on this permission.
                }
                return
            }

            // Add other 'when' lines to check for other
            // permissions this app might request.
            else -> {
                // Ignore all other requests.
            }
        }
    }

    override fun onResume() {
        super.onResume()
        val focusRow = findViewById<TextView>(R.id.focusedItem)
        focusRow.text = "Total focus = ${DevUtils.longToDateString(distractionData.focusedTime)}"

        val phoneRow = findViewById<TextView>(R.id.phoneDistractItem)
        phoneRow.text = "Phone distraction = ${DevUtils.longToDateString(distractionData.phoneTime)}"

        val noiseRow = findViewById<TextView>(R.id.noiseDistractItem)
        noiseRow.text = "Noise distraction = ${DevUtils.longToDateString(distractionData.noisyTime)}"

        viewAdapter.notifyDataSetChanged()
    }


    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == PICK_CONTACT_REQUEST) {
            if (resultCode == AppCompatActivity.RESULT_OK) {
                // no op yet
            }
        }
    }

    private fun startTimerActivity() {
        Intent(this, TimerActivity::class.java).also {
            startActivityForResult(it, PICK_CONTACT_REQUEST)
        }
    }

    private fun extractText(editText: EditText) : String {
        val output = editText.text.toString()
        editText.text.clear()
        return output
    }

    fun addDistraction() {
        val text = extractText(findViewById(R.id.distractionTextEdit))
        distractionData.distractions.add(Distraction(text, 0L))
        viewAdapter.notifyItemInserted(distractionData.distractions.size)
    }

    class MyAdapter(private val myDataset: List<Distraction>) :
        RecyclerView.Adapter<MyAdapter.MyViewHolder>() {

        class MyViewHolder(val textView: TextView) : RecyclerView.ViewHolder(textView)

        override fun onCreateViewHolder(parent: ViewGroup, p1: Int): MyViewHolder {
            val textView = LayoutInflater.from(parent.context)
                .inflate(R.layout.distraction_item, parent, false) as TextView

            return MyViewHolder(textView)
        }

        override fun onBindViewHolder(holder: MyViewHolder, position: Int) {
            val distraction = myDataset[position]
            holder.textView.text = "${distraction.name} (${DevUtils.longToDateString(distraction.duration)})"
        }

        override fun getItemCount() = myDataset.size
    }
}


