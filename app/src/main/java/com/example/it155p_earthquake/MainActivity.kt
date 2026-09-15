package com.example.it155p_earthquake // <-- NOTE: Make sure this package name matches your Gradle file

import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.*
import org.json.JSONObject // Used to parse the data retrieved from the mp_record table
import java.net.HttpURLConnection
import java.net.URL

// FIX: Import the R class for resource references
import com.example.it155p_earthquake.R

class MainActivity : AppCompatActivity() {

    private lateinit var textEarthquakeInfo: TextView // Latest earthquake status text
    private lateinit var textAlertStatus: TextView    // Alert Notification status text
    private lateinit var btnRecord: Button            // Retrieve last row of mp_record table
    private lateinit var btnSosOn: Button             // Update mp_sos stat to "1"
    private lateinit var btnSosOff: Button            // Update mp_sos stat to "0"

    // Base URL for the services (same as C# file)
    private val BASE_URL = "http://192.168.1.11/IT155P/mobile/"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // 1. UI Binding (Using new IDs from updated activity_main.xml)
        textEarthquakeInfo = findViewById(R.id.text_earthquake_info)
        textAlertStatus = findViewById(R.id.text_alert_status)

        btnRecord = findViewById(R.id.btn_record)
        btnSosOn = findViewById(R.id.btn_sos_on)
        btnSosOff = findViewById(R.id.btn_sos_off)

        // 2. Event Handlers
        btnRecord.setOnClickListener { retrieveEarthquakeRecord() }
        btnSosOn.setOnClickListener { updateSOSStatus(1) } // Activate SOS
        btnSosOff.setOnClickListener { updateSOSStatus(0) } // Reset / Evacuate (based on mp_sos stat = 0)

        // Start checking the alert status in the background
        startAlertStatusPolling()
    }

    // Generic function to perform a network request asynchronously
    private fun fetchData(endpoint: String, successCallback: (String) -> Unit) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val url = URL(BASE_URL + endpoint)
                val connection = url.openConnection() as HttpURLConnection
                connection.connectTimeout = 5000 // 5 seconds timeout
                connection.readTimeout = 5000

                val responseCode = connection.responseCode

                if (responseCode == HttpURLConnection.HTTP_OK) {
                    val inputStream = connection.inputStream
                    val response = inputStream.bufferedReader().use { it.readText() }

                    // Switch back to the Main thread to update the UI
                    withContext(Dispatchers.Main) {
                        successCallback(response)
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@MainActivity, "Error: HTTP $responseCode for $endpoint", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    // Only show network error for manual button presses
                    if (endpoint.contains("mp_sos") || endpoint.contains("mobile_record")) {
                        Toast.makeText(this@MainActivity, "Network Error: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    // ----------------------------------------------------
    // NEW FUNCTIONALITY IMPLEMENTATION
    // ----------------------------------------------------

    /**
     * Maps to old fetchDHT -> new mobile_record (retrieves and formats last earthquake record).
     */
    private fun retrieveEarthquakeRecord() {
        // Assuming the new PHP file is named 'mobile_record.php'
        fetchData("mobile_record.php") { jsonResponse ->
            try {
                // Assuming the PHP returns a single JSON object for the last record
                val json = JSONObject(jsonResponse)

                val iHour = json.getString("I_hour")
                val iMinute = json.getString("I_minute")
                val duration = json.getString("duration")
                val cHour = json.getString("c_hour")
                val cMinute = json.getString("c_minute")

                // Format the text according to the PDF example
                val formattedText = buildString {
                    append("[Latest Earthquake Information]\n")
                    append("Location: Malayan Village\n") // Hardcoded location per example
                    append("Time: $iHour:$iMinute\n") // "10" from I_hour & "45" from I_minute
                    append("Duration: $duration secs.\n") // "5" from duration
                    append("(updated as of $cHour:$cMinute)") // "14" from c_hour & "23" from c_minute
                }
                textEarthquakeInfo.text = formattedText
            } catch (e: Exception) {
                textEarthquakeInfo.text = "Error parsing record data. Check mobile_record.php output."
                Toast.makeText(this, "Data Format Error", Toast.LENGTH_LONG).show()
            }
        }
    }

    /**
     * Maps to old setLEDStat -> new mp_sos (updates the mp_sos stat).
     */
    private fun updateSOSStatus(stat: Int) {
        // Assuming the new PHP file is named 'mp_sos.php'
        val endpoint = "mp_sos.php?stat=$stat"
        fetchData(endpoint) { response ->
            val message = if (stat == 1) "SOS Activated: $response" else "SOS Reset/Evac: $response"
            Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        }
    }

    /**
     * Continuously checks the mp_eq table for the earthquake alert status.
     */
    private fun startAlertStatusPolling() {
        // Run a coroutine that repeats every 5 seconds (or suitable interval)
        CoroutineScope(Dispatchers.Main).launch {
            while(true) {
                delay(5000L)
                checkAlertStatus()
            }
        }
    }

    /**
     * Maps to old getSwitchStatus -> new mobile_eq (retrieves stat for alert notification).
     */
    private fun checkAlertStatus() {
        // Assuming the new PHP file is named 'mobile_eq.php'
        fetchData("mobile_eq.php") { response ->
            val status = response.trim()
            if (status == "1") {
                // 1: when a sensor detects and send notif to mobile app
                textAlertStatus.text = "Alert Status: EARTHQUAKE DETECTED! (1)"
                textAlertStatus.setTextColor(resources.getColor(android.R.color.holo_red_dark))
                // Logic for playing sound/vibration for the alert notification would go here
            } else {
                // 0: when a sensor not detect
                textAlertStatus.text = "Alert Status: SAFE (0)"
                textAlertStatus.setTextColor(resources.getColor(android.R.color.holo_green_dark))
            }
        }
    }
}