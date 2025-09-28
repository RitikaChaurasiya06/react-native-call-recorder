package com.callrecorderapp

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.callrecorderapp.adapters.CallListAdapter
import com.callrecorderapp.databinding.ActivityMainBinding
import com.callrecorderapp.models.RecordedCall
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import android.provider.CallLog
import android.net.Uri
import android.telephony.TelephonyManager
import android.content.Context

class MainActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "MainActivity"
    }

    private lateinit var binding: ActivityMainBinding
    private lateinit var callListAdapter: CallListAdapter

    private val PERMISSIONS_REQUEST_CODE = 101

    private val requiredPermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        arrayOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.READ_CALL_LOG,
            Manifest.permission.READ_MEDIA_AUDIO
        )
    } else {
        arrayOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.READ_CALL_LOG,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.READ_EXTERNAL_STORAGE
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        Log.d(TAG, "=== MainActivity onCreate ===")
        Log.d(TAG, "App started successfully")
        
        setupRecyclerView()
        checkAndRequestPermissions()
        
        // Add debug information
        logSystemInfo()
        checkTelephonyService()
        
        // Start proactive call monitoring service
        startCallMonitoringService()
    }
    
    private fun startCallMonitoringService() {
        if (allPermissionsGranted()) {
            Log.d(TAG, "Starting ProactiveCallService for immediate call detection")
            val serviceIntent = Intent(this, ProactiveCallService::class.java)
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent)
            } else {
                startService(serviceIntent)
            }
            
            Toast.makeText(this, "Call monitoring service is Start", Toast.LENGTH_SHORT).show()
        } else {
            Log.w(TAG, "Cannot start service - permissions not granted")
        }
    }

    private fun logSystemInfo() {
        Log.d(TAG, "=== System Information ===")
        Log.d(TAG, "Android Version: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
        Log.d(TAG, "Device: ${Build.MANUFACTURER} ${Build.MODEL}")
        Log.d(TAG, "Package: ${packageName}")
    }

    private fun checkTelephonyService() {
        try {
            val tm = getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
            Log.d(TAG, "=== Telephony Service Check ===")
            Log.d(TAG, "TelephonyManager available: ${tm != null}")
            
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED) {
                val callState = tm.callState
                Log.d(TAG, "Current call state: ${getCallStateString(callState)}")
            } else {
                Log.w(TAG, "Cannot check call state - missing READ_PHONE_STATE permission")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking telephony service: ${e.message}")
        }
    }
    
    private fun getCallStateString(state: Int): String {
        return when (state) {
            TelephonyManager.CALL_STATE_IDLE -> "IDLE"
            TelephonyManager.CALL_STATE_RINGING -> "RINGING"
            TelephonyManager.CALL_STATE_OFFHOOK -> "OFFHOOK"
            else -> "UNKNOWN($state)"
        }
    }

    private fun setupRecyclerView() {
        Log.d(TAG, "Setting up RecyclerView")
        callListAdapter = CallListAdapter { recordedCall ->
            Log.d(TAG, "Opening call detail for: ${recordedCall.phoneNumber}")
            val intent = Intent(this, CallDetailActivity::class.java).apply {
                putExtra("recordedCall", recordedCall)
            }
            startActivity(intent)
        }
        binding.recyclerViewCalls.apply {
            adapter = callListAdapter
            layoutManager = LinearLayoutManager(this@MainActivity)
        }
    }

    private fun checkAndRequestPermissions() {
        Log.d(TAG, "=== Checking Permissions ===")
        
        val missingPermissions = requiredPermissions.filter {
            val granted = ActivityCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
            Log.d(TAG, "Permission $it: ${if (granted) "GRANTED" else "DENIED"}")
            !granted
        }

        if (missingPermissions.isNotEmpty()) {
            Log.w(TAG, "Missing permissions: $missingPermissions")
            Log.d(TAG, "Requesting permissions...")
            ActivityCompat.requestPermissions(this, missingPermissions.toTypedArray(), PERMISSIONS_REQUEST_CODE)
        } else {
            Log.d(TAG, "All permissions granted")
            loadRecordedCalls()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        
        Log.d(TAG, "=== Permission Result ===")
        Log.d(TAG, "Request code: $requestCode")
        
        if (requestCode == PERMISSIONS_REQUEST_CODE) {
            val allGranted = grantResults.all { it == PackageManager.PERMISSION_GRANTED }
            Log.d(TAG, "All permissions granted: $allGranted")
            
            permissions.forEachIndexed { index, permission ->
                val result = if (grantResults.getOrNull(index) == PackageManager.PERMISSION_GRANTED) "GRANTED" else "DENIED"
                Log.d(TAG, "Permission $permission: $result")
            }
            
            if (allGranted) {
                loadRecordedCalls()
                Toast.makeText(this, "All permissions is done Call recording is ready", Toast.LENGTH_SHORT).show()
            } else {
                Log.e(TAG, "Some permissions denied")
                binding.textViewNoRecordings.text = "Permissions required not receive । For Voice recordings need permissions "
                binding.textViewNoRecordings.visibility = View.VISIBLE
                binding.recyclerViewCalls.visibility = View.GONE
                Toast.makeText(this, "No permissions Found । App will not work properly ।", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun loadRecordedCalls() {
        Log.d(TAG, "=== Loading Recorded Calls ===")
        
        val recordingDir = getRecordingDirectory()
        Log.d(TAG, "Recording directory: ${recordingDir.absolutePath}")
        Log.d(TAG, "Directory exists: ${recordingDir.exists()}")
        
        if (!recordingDir.exists() || !recordingDir.isDirectory) {
            Log.w(TAG, "Recording directory not found or not a directory")
            binding.textViewNoRecordings.text = "No voice recordings Found \n${recordingDir.absolutePath}"
            binding.textViewNoRecordings.visibility = View.VISIBLE
            return
        }

        val files = recordingDir.listFiles { file -> 
            val isValidFile = file.isFile && (file.name.startsWith("CALL_") || file.name.startsWith("VOICE_")) && file.name.endsWith(".m4a")
            Log.d(TAG, "File: ${file.name} - Valid: $isValidFile")
            isValidFile
        }
        
        Log.d(TAG, "Found ${files?.size ?: 0} recording files")
        
        val recordedCalls = files?.mapNotNull { file ->
            Log.d(TAG, "Parsing file: ${file.name}")
            parseRecordingFile(file)
        }?.sortedByDescending { it.startTime } ?: emptyList()

        Log.d(TAG, "Parsed ${recordedCalls.size} valid recordings")

        // Filter to show only outgoing calls (voice recordings)
        val voiceRecordings = recordedCalls.filter { it.callType == "Outgoing" }
        Log.d(TAG, "Outgoing calls (voice recordings): ${voiceRecordings.size}")

        if (voiceRecordings.isEmpty()) {
            val message = if (recordedCalls.isEmpty()) {
                "कोई voice recordings नहीं मिलीं।\nOutgoing calls करें recording के लिए।"
            } else {
                "केवल outgoing calls record होती हैं।\n${recordedCalls.size} total recordings मिलीं, लेकिन सब incoming थीं।"
            }
            binding.textViewNoRecordings.text = message
            binding.textViewNoRecordings.visibility = View.VISIBLE
            binding.recyclerViewCalls.visibility = View.GONE
        } else {
            Log.d(TAG, "Displaying ${voiceRecordings.size} voice recordings")
            callListAdapter.submitList(voiceRecordings)
            binding.textViewNoRecordings.visibility = View.GONE
            binding.recyclerViewCalls.visibility = View.VISIBLE
            Toast.makeText(this, "${voiceRecordings.size} voice recordings मिलीं", Toast.LENGTH_SHORT).show()
        }
    }

    private fun parseRecordingFile(file: File): RecordedCall? {
        Log.d(TAG, "Parsing recording file: ${file.name}")
        
        // Support both old CALL_ format and new VOICE_ format
        val voiceRegex = "VOICE_(OUT|IN)_([^_]+)_(\\d{8}_\\d{6})\\.m4a".toRegex()
        val callRegex = "CALL_([^_]+)_(\\d{8}_\\d{6})\\.m4a".toRegex()
        
        var phoneNumber: String
        var dateString: String
        var callType: String
        
        val voiceMatch = voiceRegex.find(file.name)
        if (voiceMatch != null) {
            callType = if (voiceMatch.groupValues[1] == "OUT") "Outgoing" else "Incoming"
            phoneNumber = voiceMatch.groupValues[2]
            dateString = voiceMatch.groupValues[3]
            Log.d(TAG, "Parsed VOICE format - Type: $callType, Number: $phoneNumber, Date: $dateString")
        } else {
            val callMatch = callRegex.find(file.name)
            if (callMatch == null) {
                Log.w(TAG, "Could not parse filename: ${file.name}")
                return null
            }
            phoneNumber = callMatch.groupValues[1]
            dateString = callMatch.groupValues[2]
            // For old format, determine call type from call log
            val sdf = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
            val date = sdf.parse(dateString)
            if (date == null) {
                Log.w(TAG, "Could not parse date: $dateString")
                return null
            }
            val callDetails = getCallDetails(phoneNumber, date.time)
            callType = callDetails.second
            Log.d(TAG, "Parsed CALL format - Type: $callType, Number: $phoneNumber, Date: $dateString")
        }

        val sdf = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
        val date = sdf.parse(dateString)
        if (date == null) {
            Log.w(TAG, "Could not parse date: $dateString")
            return null
        }

        // Get call details from call log
        val callDetails = getCallDetails(phoneNumber, date.time)
        Log.d(TAG, "Call details from log - Number: ${callDetails.first}, Type: ${callDetails.second}, Duration: ${callDetails.third}ms")

        return RecordedCall(
            fileName = file.name,
            phoneNumber = callDetails.first,
            callType = callType,
            callDuration = callDetails.third,
            startTime = date,
            endTime = Date(date.time + callDetails.third),
            recordingFile = file,
            filePath = file.absolutePath
        )
    }

    private fun getRecordingDirectory(): File {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            File(getExternalFilesDir(Environment.DIRECTORY_MUSIC), "CallRecordings")
        } else {
            @Suppress("DEPRECATION")
            File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC), "CallRecordings")
        }
    }
    
    private fun getCallDetails(phoneNumber: String, timestamp: Long): Triple<String, String, Long> {
        Log.d(TAG, "Getting call details for $phoneNumber at timestamp $timestamp")
        
        val contentResolver = contentResolver
        val uri = Uri.parse("content://call_log/calls")
        val projection = arrayOf(
            CallLog.Calls.NUMBER,
            CallLog.Calls.TYPE,
            CallLog.Calls.DURATION,
            CallLog.Calls.DATE
        )
        
        val selection = "${CallLog.Calls.NUMBER} = ? AND ${CallLog.Calls.DATE} >= ?"
        val selectionArgs = arrayOf(phoneNumber, (timestamp - 10000).toString()) // 10s window
        val sortOrder = "${CallLog.Calls.DATE} DESC"

        var type = CallLog.Calls.OUTGOING_TYPE
        var duration = 0L

        try {
            contentResolver.query(uri, projection, selection, selectionArgs, sortOrder)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val typeColumnIndex = cursor.getColumnIndex(CallLog.Calls.TYPE)
                    val durationColumnIndex = cursor.getColumnIndex(CallLog.Calls.DURATION)

                    if (typeColumnIndex != -1 && durationColumnIndex != -1) {
                        type = cursor.getInt(typeColumnIndex)
                        duration = cursor.getLong(durationColumnIndex) * 1000 // Convert to ms
                        Log.d(TAG, "Found call in log - Type: $type, Duration: ${duration}ms")
                    }
                } else {
                    Log.w(TAG, "No matching call found in call log")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error querying CallLog: ${e.message}")
        }

        val callType = when (type) {
            CallLog.Calls.INCOMING_TYPE -> "Incoming"
            CallLog.Calls.OUTGOING_TYPE -> "Outgoing"
            else -> "Outgoing" // Default for voice recordings
        }

        return Triple(phoneNumber, callType, duration)
    }

    override fun onResume() {
        super.onResume()
        Log.d(TAG, "onResume called")
        
        // Reload calls in case a new one was just recorded
        if (allPermissionsGranted()) {
            Log.d(TAG, "Reloading recorded calls")
            loadRecordedCalls()
        }
        
        // Check telephony service again
        checkTelephonyService()
    }

    private fun allPermissionsGranted(): Boolean {
        val granted = requiredPermissions.all {
            ActivityCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
        Log.d(TAG, "All permissions granted check: $granted")
        return granted
    }
}