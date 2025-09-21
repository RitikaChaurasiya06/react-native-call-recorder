package com.callrecorderapp

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.MediaRecorder
import android.telephony.PhoneStateListener
import android.telephony.TelephonyManager
import android.os.Build
import android.os.Environment
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import android.util.Log
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import android.os.Handler
import android.os.Looper
import android.telephony.TelephonyCallback
import androidx.annotation.RequiresApi

enum class RecordingStatus {
    IDLE,
    RECORDING,
    FAILED
}

@RequiresApi(Build.VERSION_CODES.S)
class CallStateCallback(private val context: Context) : TelephonyCallback(), TelephonyCallback.CallStateListener {

    private var mediaRecorder: MediaRecorder? = null
    private var recordingStatus = RecordingStatus.IDLE
    private var phoneNumber: String? = null
    private val handler = Handler(Looper.getMainLooper())

    init {
        Log.d("CallRecorder", "🔧 CallStateCallback INITIALIZED")
    }

    override fun onCallStateChanged(state: Int) {
        Log.d("CallRecorder", "🔥 MODERN CallStateCallback - Call state changed: $state")
        when (state) {
            TelephonyManager.CALL_STATE_IDLE -> Log.d("CallRecorder", "📱 State: IDLE")
            TelephonyManager.CALL_STATE_RINGING -> Log.d("CallRecorder", "📱 State: RINGING")
            TelephonyManager.CALL_STATE_OFFHOOK -> Log.d("CallRecorder", "📱 State: OFFHOOK")
            else -> Log.d("CallRecorder", "📱 State: UNKNOWN ($state)")
        }
        handleCallStateChange(state)
    }

    private fun handleCallStateChange(state: Int) {
        when (state) {
            TelephonyManager.CALL_STATE_RINGING -> {
                Log.d("CallRecorder", "📞 Call RINGING: ${phoneNumber ?: "unknown"}")
            }

            TelephonyManager.CALL_STATE_OFFHOOK -> {
                Log.d("CallRecorder", "📞 Call PICKED UP: ${phoneNumber ?: "unknown"}")
                handler.postDelayed({
                    if (checkAllPermissions()) {
                        startRecording()
                    } else {
                        Log.e("CallRecorder", "❌ Missing permissions for recording")
                    }
                }, 2000)
            }

            TelephonyManager.CALL_STATE_IDLE -> {
                Log.d("CallRecorder", "📞 Call ENDED: ${phoneNumber ?: "unknown"}")
                handler.removeCallbacksAndMessages(null)
                stopRecording()
                phoneNumber = null
            }
        }
    }

    fun setPhoneNumber(number: String?) {
        phoneNumber = number
        Log.d("CallRecorder", "📋 Phone number set: $number")
    }

    private fun checkAllPermissions(): Boolean {
        val requiredPermissions = mutableListOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.READ_CALL_LOG
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requiredPermissions.add(Manifest.permission.READ_MEDIA_AUDIO)
        } else {
            requiredPermissions.addAll(listOf(
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                Manifest.permission.READ_EXTERNAL_STORAGE
            ))
        }

        val missingPermissions = requiredPermissions.filter { permission ->
            ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED
        }

        if (missingPermissions.isNotEmpty()) {
            Log.e("CallRecorder", "❌ Missing permissions: $missingPermissions")
            return false
        }

        Log.d("CallRecorder", "✅ All permissions granted")
        return true
    }

    private fun getRecordingDirectory(): File {
        val dir = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            File(context.getExternalFilesDir(Environment.DIRECTORY_MUSIC), "CallRecordings")
        } else {
            File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC), "CallRecordings")
        }

        if (!dir.exists()) {
            val created = dir.mkdirs()
            Log.d("CallRecorder", "📁 Directory created: $created -> ${dir.absolutePath}")
        }
        return dir
    }

    private fun startRecording() {
        if (recordingStatus == RecordingStatus.RECORDING) {
            Log.w("CallRecorder", "⚠️ Already recording, skipping start")
            return
        }

        Log.d("CallRecorder", "🎤 STARTING RECORDING...")

        try {
            val sdf = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
            val sanitizedNumber = phoneNumber?.replace(Regex("[^0-9+]"), "") ?: "unknown"
            val fileName = "CALL_${sanitizedNumber}_${sdf.format(Date())}.m4a"
            val recordingDir = getRecordingDirectory()
            val file = File(recordingDir, fileName)

            Log.d("CallRecorder", "🎯 Attempting to record to: ${file.absolutePath}")

            val audioSources = listOf(
                MediaRecorder.AudioSource.VOICE_CALL,
                MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                MediaRecorder.AudioSource.MIC,
                MediaRecorder.AudioSource.DEFAULT
            )

            var recordingStarted = false

            run audioSourceLoop@{
                audioSources.forEach { audioSource ->
                    Log.d("CallRecorder", "🔄 Trying audio source: $audioSource")
                    try {
                        val recorder = MediaRecorder(context)
                        recorder.setAudioSource(audioSource)
                        recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                        recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                        recorder.setAudioEncodingBitRate(128000)
                        recorder.setAudioSamplingRate(44100)
                        recorder.setOutputFile(file.absolutePath)
                        recorder.setMaxDuration(3600000)

                        recorder.prepare()
                        recorder.start()

                        mediaRecorder = recorder
                        recordingStarted = true
                        recordingStatus = RecordingStatus.RECORDING

                        Log.d("CallRecorder", "🎉 Recording STARTED with source: $audioSource -> ${file.absolutePath}")
                        return@audioSourceLoop

                    } catch (e: Exception) {
                        Log.w("CallRecorder", "❌ Failed with audio source $audioSource: ${e.message}")
                    }
                }
            }

            if (!recordingStarted) {
                recordingStatus = RecordingStatus.FAILED
                Log.e("CallRecorder", "💥 All audio sources failed - recording impossible on this device/Android version")
            }

        } catch (e: Exception) {
            Log.e("CallRecorder", "💥 Recording failed: ${e.message}", e)
            recordingStatus = RecordingStatus.FAILED
            mediaRecorder?.release()
            mediaRecorder = null
        }
    }

    private fun stopRecording() {
        if (recordingStatus != RecordingStatus.RECORDING) {
            Log.d("CallRecorder", "⏹️ Not recording, skipping stop")
            return
        }

        Log.d("CallRecorder", "🛑 STOPPING RECORDING...")

        try {
            mediaRecorder?.apply {
                try {
                    stop()
                    Log.d("CallRecorder", "✅ Recording STOPPED successfully")
                } catch (e: Exception) {
                    Log.e("CallRecorder", "❌ Error stopping recording: ${e.message}")
                } finally {
                    release()
                }
            }
        } catch (e: Exception) {
            Log.e("CallRecorder", "💥 Error in stopRecording: ${e.message}")
        }

        mediaRecorder = null
        recordingStatus = RecordingStatus.IDLE
        Log.d("CallRecorder", "🔄 Recording status reset to IDLE")
    }
}

// Legacy PhoneStateListener for Android versions < 12
class CallStateListener(private val context: Context) : PhoneStateListener() {

    private var mediaRecorder: MediaRecorder? = null
    private var recordingStatus = RecordingStatus.IDLE
    private var phoneNumber: String? = null
    private val handler = Handler(Looper.getMainLooper())

    init {
        Log.d("CallRecorder", "🔧 LEGACY CallStateListener INITIALIZED")
    }

    @Deprecated("Deprecated in API level 31")
    override fun onCallStateChanged(state: Int, incomingNumber: String?) {
        super.onCallStateChanged(state, incomingNumber)

        Log.d("CallRecorder", "🔥 LEGACY CallStateListener - Call state changed: $state, number: $incomingNumber")

        if (!incomingNumber.isNullOrEmpty()) {
            phoneNumber = incomingNumber
        }

        when (state) {
            TelephonyManager.CALL_STATE_IDLE -> Log.d("CallRecorder", "📱 Legacy State: IDLE")
            TelephonyManager.CALL_STATE_RINGING -> Log.d("CallRecorder", "📱 Legacy State: RINGING")
            TelephonyManager.CALL_STATE_OFFHOOK -> Log.d("CallRecorder", "📱 Legacy State: OFFHOOK")
            else -> Log.d("CallRecorder", "📱 Legacy State: UNKNOWN ($state)")
        }

        handleCallStateChange(state)
    }

    private fun handleCallStateChange(state: Int) {
        when (state) {
            TelephonyManager.CALL_STATE_RINGING -> {
                Log.d("CallRecorder", "📞 Legacy Call RINGING: ${phoneNumber ?: "unknown"}")
            }

            TelephonyManager.CALL_STATE_OFFHOOK -> {
                Log.d("CallRecorder", "📞 Legacy Call PICKED UP: ${phoneNumber ?: "unknown"}")
                handler.postDelayed({
                    if (checkAllPermissions()) {
                        startRecording()
                    } else {
                        Log.e("CallRecorder", "❌ Missing permissions for recording")
                    }
                }, 2000)
            }

            TelephonyManager.CALL_STATE_IDLE -> {
                Log.d("CallRecorder", "📞 Legacy Call ENDED: ${phoneNumber ?: "unknown"}")
                handler.removeCallbacksAndMessages(null)
                stopRecording()
                phoneNumber = null
            }
        }
    }

    private fun checkAllPermissions(): Boolean {
        val requiredPermissions = mutableListOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.READ_CALL_LOG
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requiredPermissions.add(Manifest.permission.READ_MEDIA_AUDIO)
        } else {
            requiredPermissions.addAll(listOf(
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                Manifest.permission.READ_EXTERNAL_STORAGE
            ))
        }

        val missingPermissions = requiredPermissions.filter { permission ->
            ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED
        }

        if (missingPermissions.isNotEmpty()) {
            Log.e("CallRecorder", "❌ Missing permissions: $missingPermissions")
            return false
        }

        return true
    }

    private fun getRecordingDirectory(): File {
        val dir = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            File(context.getExternalFilesDir(Environment.DIRECTORY_MUSIC), "CallRecordings")
        } else {
            File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC), "CallRecordings")
        }

        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    private fun startRecording() {
        if (recordingStatus == RecordingStatus.RECORDING) {
            Log.w("CallRecorder", "⚠️ Already recording, skipping start")
            return
        }

        Log.d("CallRecorder", "🎤 LEGACY STARTING RECORDING...")

        try {
            val sdf = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
            val sanitizedNumber = phoneNumber?.replace(Regex("[^0-9+]"), "") ?: "unknown"
            val fileName = "CALL_${sanitizedNumber}_${sdf.format(Date())}.m4a"
            val recordingDir = getRecordingDirectory()
            val file = File(recordingDir, fileName)

            val audioSources = listOf(
                MediaRecorder.AudioSource.VOICE_CALL,
                MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                MediaRecorder.AudioSource.MIC
            )

            var recordingStarted = false

            run audioSourceLoop@{
                audioSources.forEach { audioSource ->
                    Log.d("CallRecorder", "🔄 Legacy trying audio source: $audioSource")
                    try {
                        val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            MediaRecorder(context)
                        } else {
                            @Suppress("DEPRECATION")
                            MediaRecorder()
                        }

                        recorder.setAudioSource(audioSource)
                        recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                        recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                        recorder.setAudioEncodingBitRate(128000)
                        recorder.setAudioSamplingRate(44100)
                        recorder.setOutputFile(file.absolutePath)

                        recorder.prepare()
                        recorder.start()

                        mediaRecorder = recorder
                        recordingStarted = true
                        recordingStatus = RecordingStatus.RECORDING

                        Log.d("CallRecorder", "🎉 Legacy Recording STARTED with source: $audioSource -> ${file.absolutePath}")
                        return@audioSourceLoop

                    } catch (e: Exception) {
                        Log.w("CallRecorder", "❌ Legacy Failed with audio source $audioSource: ${e.message}")
                    }
                }
            }

            if (!recordingStarted) {
                recordingStatus = RecordingStatus.FAILED
                Log.e("CallRecorder", "💥 Legacy All audio sources failed")
            }

        } catch (e: Exception) {
            Log.e("CallRecorder", "💥 Legacy Recording failed: ${e.message}")
            recordingStatus = RecordingStatus.FAILED
        }
    }

    private fun stopRecording() {
        if (recordingStatus != RecordingStatus.RECORDING) {
            Log.d("CallRecorder", "⏹️ Legacy Not recording, skipping stop")
            return
        }

        Log.d("CallRecorder", "🛑 LEGACY STOPPING RECORDING...")

        try {
            mediaRecorder?.apply {
                try {
                    stop()
                    Log.d("CallRecorder", "✅ Legacy Recording STOPPED successfully")
                } catch (e: Exception) {
                    Log.e("CallRecorder", "❌ Legacy Error stopping recording: ${e.message}")
                } finally {
                    release()
                }
            }
        } catch (e: Exception) {
            Log.e("CallRecorder", "💥 Legacy Error in stopRecording: ${e.message}")
        }

        mediaRecorder = null
        recordingStatus = RecordingStatus.IDLE
    }
}

class IncomingCallReceiver : BroadcastReceiver() {

    companion object {
        private var isListenerRegistered = false
        private var callStateListener: CallStateListener? = null
        private var callStateCallback: CallStateCallback? = null
    }

    override fun onReceive(context: Context, intent: Intent) {
        Log.d("CallRecorder", "🚨 IncomingCallReceiver triggered!")
        Log.d("CallRecorder", "📨 Action: ${intent.action}")
        Log.d("CallRecorder", "📦 Intent extras: ${intent.extras?.keySet()}")

        val incomingNumber = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER)
        val state = intent.getStringExtra(TelephonyManager.EXTRA_STATE)

        Log.d("CallRecorder", "📞 Incoming number: $incomingNumber")
        Log.d("CallRecorder", "📱 Phone state: $state")

        if (!isListenerRegistered) {
            Log.d("CallRecorder", "🔧 Registering telephony listener...")
            try {
                val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
                Log.d("CallRecorder", "📡 TelephonyManager obtained: ${telephonyManager != null}")

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    Log.d("CallRecorder", "🆕 Using modern TelephonyCallback (Android ${Build.VERSION.SDK_INT})")
                    callStateCallback = CallStateCallback(context).apply {
                        setPhoneNumber(incomingNumber)
                    }
                    telephonyManager.registerTelephonyCallback(
                        context.mainExecutor,
                        callStateCallback!!
                    )
                    Log.d("CallRecorder", "✅ TelephonyCallback registered successfully")
                } else {
                    Log.d("CallRecorder", "🔄 Using legacy PhoneStateListener (Android ${Build.VERSION.SDK_INT})")
                    callStateListener = CallStateListener(context)
                    @Suppress("DEPRECATION")
                    telephonyManager.listen(callStateListener, PhoneStateListener.LISTEN_CALL_STATE)
                    Log.d("CallRecorder", "✅ PhoneStateListener registered successfully")
                }

                isListenerRegistered = true

            } catch (e: SecurityException) {
                Log.e("CallRecorder", "🚫 Security exception registering listener: ${e.message}")
                Log.e("CallRecorder", "❌ This usually means missing READ_PHONE_STATE permission")
            } catch (e: Exception) {
                Log.e("CallRecorder", "💥 Failed to register call state listener: ${e.message}")
                e.printStackTrace()
            }
        } else {
            Log.d("CallRecorder", "✅ Call state listener already registered")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                callStateCallback?.setPhoneNumber(incomingNumber)
            }
        }
    }
}