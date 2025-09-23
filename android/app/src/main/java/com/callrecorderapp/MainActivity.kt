package com.callrecorderapp

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.callrecorderapp.adapters.CallListAdapter
import com.callrecorderapp.databinding.ActivityMainBinding
import com.callrecorderapp.models.RecordedCall
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import kotlin.collections.ArrayList
import android.provider.CallLog
import android.net.Uri

class MainActivity : AppCompatActivity() {

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

        setupRecyclerView()
        checkAndRequestPermissions()
    }

    private fun setupRecyclerView() {
        callListAdapter = CallListAdapter { recordedCall ->
            val intent = Intent(this, CallDetailActivity::class.java).apply {
                putExtra("recorded_call", recordedCall)
            }
            startActivity(intent)
        }
        binding.recyclerViewCalls.apply {
            adapter = callListAdapter
            layoutManager = LinearLayoutManager(this@MainActivity)
        }
    }

    private fun checkAndRequestPermissions() {
        val missingPermissions = requiredPermissions.filter {
            ActivityCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missingPermissions.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, missingPermissions.toTypedArray(), PERMISSIONS_REQUEST_CODE)
        } else {
            loadRecordedCalls()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSIONS_REQUEST_CODE) {
            if (grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                loadRecordedCalls()
            } else {
                // Handle denied permissions, maybe show a message to the user
                Log.e("MainActivity", "Permissions denied")
                binding.textViewNoRecordings.text = "Permissions are required to show recordings."
                binding.textViewNoRecordings.visibility = View.VISIBLE
                binding.recyclerViewCalls.visibility = View.GONE
            }
        }
    }

    private fun loadRecordedCalls() {
        val recordingDir = getRecordingDirectory()
        if (!recordingDir.exists() || !recordingDir.isDirectory) {
            binding.textViewNoRecordings.visibility = View.VISIBLE
            return
        }

        val files = recordingDir.listFiles { file -> file.isFile && file.name.startsWith("CALL_") && file.name.endsWith(".m4a") }
        val recordedCalls = files?.mapNotNull { file ->
            parseRecordingFile(file)
        }?.sortedByDescending { it.startTime } ?: emptyList()

        if (recordedCalls.isEmpty()) {
            binding.textViewNoRecordings.visibility = View.VISIBLE
            binding.recyclerViewCalls.visibility = View.GONE
        } else {
            callListAdapter.submitList(recordedCalls)
            binding.textViewNoRecordings.visibility = View.GONE
            binding.recyclerViewCalls.visibility = View.VISIBLE
        }
    }

    private fun parseRecordingFile(file: File): RecordedCall? {
        val regex = "CALL_([^_]+)_(\\d{8}_\\d{6})\\.m4a".toRegex()
        val matchResult = regex.find(file.name) ?: return null

        val phoneNumber = matchResult.groupValues[1]
        val dateString = matchResult.groupValues[2]

        val sdf = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
        val date = sdf.parse(dateString) ?: return null

        // This is a simplified way to get call details. A more robust solution would query the CallLog.
        val callDetails = getCallDetails(phoneNumber, date.time)

        return RecordedCall(
            fileName = file.name,
            phoneNumber = callDetails.first,
            callType = callDetails.second,
            callDuration = callDetails.third,
            startTime = date,
            endTime = Date(date.time + callDetails.third),
            recordingFile = file
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
    
    // A function to query the CallLog for additional details
    private fun getCallDetails(phoneNumber: String, timestamp: Long): Triple<String, String, Long> {
        val contentResolver = contentResolver
        val uri = Uri.parse("content://call_log/calls")
        val projection = arrayOf(
            CallLog.Calls.NUMBER,
            CallLog.Calls.TYPE,
            CallLog.Calls.DURATION,
            CallLog.Calls.DATE
        )
        // Find the call log entry closest to the recording timestamp
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
                    val dateColumnIndex = cursor.getColumnIndex(CallLog.Calls.DATE)

                    if (typeColumnIndex != -1 && durationColumnIndex != -1) {
                        type = cursor.getInt(typeColumnIndex)
                        duration = cursor.getLong(durationColumnIndex) * 1000 // Convert to ms
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "Error querying CallLog: ${e.message}")
        }

        val callType = when (type) {
            CallLog.Calls.INCOMING_TYPE -> "Incoming"
            CallLog.Calls.OUTGOING_TYPE -> "Outgoing"
            else -> "Unknown"
        }

        return Triple(phoneNumber, callType, duration)
    }

    override fun onResume() {
        super.onResume()
        // Reload calls in case a new one was just recorded
        if (allPermissionsGranted()) {
            loadRecordedCalls()
        }
    }

    private fun allPermissionsGranted(): Boolean {
        return requiredPermissions.all {
            ActivityCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
    }
}