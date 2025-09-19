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

/**
 * 🔹 Modern Call State Callback (Android 12+)
 */
@RequiresApi(Build.VERSION_CODES.S)
class CallStateCallback(private val context: Context) : TelephonyCallback(), TelephonyCallback.CallStateListener {

    private var mediaRecorder: MediaRecorder? = null
    private var recordingStatus = RecordingStatus.IDLE
    private var phoneNumber: String? = null
    private val handler = Handler(Looper.getMainLooper())

    override fun onCallStateChanged(state: Int) {
        when (state) {
            TelephonyManager.CALL_STATE_IDLE -> handleCallStateChange(TelephonyManager.CALL_STATE_IDLE)
            TelephonyManager.CALL_STATE_RINGING -> handleCallStateChange(TelephonyManager.CALL_STATE_RINGING)
            TelephonyManager.CALL_STATE_OFFHOOK -> handleCallStateChange(TelephonyManager.CALL_STATE_OFFHOOK)
        }
    }

    private fun handleCallStateChange(state: Int) {
        when (state) {
            TelephonyManager.CALL_STATE_RINGING -> {
                Log.d("CallRecorder", "📞 RINGING: ${phoneNumber ?: "unknown"}")
            }

            TelephonyManager.CALL_STATE_OFFHOOK -> {
                Log.d("CallRecorder", "📞 OFFHOOK: ${phoneNumber ?: "unknown"}")
                handler.postDelayed({
                    if (checkAllPermissions()) startRecording()
                }, 2000)
            }

            TelephonyManager.CALL_STATE_IDLE -> {
                Log.d("CallRecorder", "📞 ENDED: ${phoneNumber ?: "unknown"}")
                handler.removeCallbacksAndMessages(null)
                stopRecording()
                phoneNumber = null
            }
        }
    }

    fun setPhoneNumber(number: String?) {
        phoneNumber = number
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
            requiredPermissions.addAll(
                listOf(
                    Manifest.permission.WRITE_EXTERNAL_STORAGE,
                    Manifest.permission.READ_EXTERNAL_STORAGE
                )
            )
        }

        val missing = requiredPermissions.filter {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }

        return missing.isEmpty()
    }

    private fun getRecordingDirectory(): File {
        val dir = File(context.getExternalFilesDir(Environment.DIRECTORY_MUSIC), "CallRecordings")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    private fun enableSpeakerphone() {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
        audioManager.isSpeakerphoneOn = true
    }

    private fun restoreAudioSettings() {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audioManager.mode = AudioManager.MODE_NORMAL
        audioManager.isSpeakerphoneOn = false
    }

    private fun startRecording() {
        if (recordingStatus == RecordingStatus.RECORDING) return

        try {
            enableSpeakerphone()

            val sdf = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
            val sanitizedNumber = phoneNumber?.replace(Regex("[^0-9+]"), "") ?: "unknown"
            val fileName = "CALL_${sanitizedNumber}_${sdf.format(Date())}.m4a"
            val file = File(getRecordingDirectory(), fileName)

            val recorder = MediaRecorder(context)
            recorder.setAudioSource(MediaRecorder.AudioSource.VOICE_COMMUNICATION)
            recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            recorder.setAudioEncodingBitRate(128000)
            recorder.setAudioSamplingRate(44100)
            recorder.setOutputFile(file.absolutePath)

            recorder.prepare()
            recorder.start()

            mediaRecorder = recorder
            recordingStatus = RecordingStatus.RECORDING
            Log.d("CallRecorder", "🎉 Recording STARTED -> ${file.absolutePath}")

        } catch (e: Exception) {
            Log.e("CallRecorder", "💥 Failed to start recording: ${e.message}", e)
            recordingStatus = RecordingStatus.FAILED
        }
    }

    private fun stopRecording() {
        if (recordingStatus != RecordingStatus.RECORDING) return

        try {
            mediaRecorder?.apply {
                stop()
                release()
            }
            Log.d("CallRecorder", "✅ Recording STOPPED")
        } catch (e: Exception) {
            Log.e("CallRecorder", "❌ Error stopping recording: ${e.message}")
        } finally {
            mediaRecorder = null
            recordingStatus = RecordingStatus.IDLE
            restoreAudioSettings()
        }
    }
}

/**
 * 🔹 Legacy PhoneStateListener (Android < 12)
 */
class CallStateListener(private val context: Context) : PhoneStateListener() {

    private var mediaRecorder: MediaRecorder? = null
    private var recordingStatus = RecordingStatus.IDLE
    private var phoneNumber: String? = null
    private val handler = Handler(Looper.getMainLooper())

    override fun onCallStateChanged(state: Int, incomingNumber: String?) {
        super.onCallStateChanged(state, incomingNumber)

        if (!incomingNumber.isNullOrEmpty()) phoneNumber = incomingNumber

        when (state) {
            TelephonyManager.CALL_STATE_RINGING -> {}
            TelephonyManager.CALL_STATE_OFFHOOK -> {
                handler.postDelayed({
                    if (checkAllPermissions()) startRecording()
                }, 2000)
            }

            TelephonyManager.CALL_STATE_IDLE -> {
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
            requiredPermissions.addAll(
                listOf(
                    Manifest.permission.WRITE_EXTERNAL_STORAGE,
                    Manifest.permission.READ_EXTERNAL_STORAGE
                )
            )
        }

        val missing = requiredPermissions.filter {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }

        return missing.isEmpty()
    }

    private fun getRecordingDirectory(): File {
        val dir = File(context.getExternalFilesDir(Environment.DIRECTORY_MUSIC), "CallRecordings")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    private fun enableSpeakerphone() {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
        audioManager.isSpeakerphoneOn = true
    }

    private fun restoreAudioSettings() {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audioManager.mode = AudioManager.MODE_NORMAL
        audioManager.isSpeakerphoneOn = false
    }

    private fun startRecording() {
        if (recordingStatus == RecordingStatus.RECORDING) return

        try {
            enableSpeakerphone()

            val sdf = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
            val sanitizedNumber = phoneNumber?.replace(Regex("[^0-9+]"), "") ?: "unknown"
            val fileName = "CALL_${sanitizedNumber}_${sdf.format(Date())}.m4a"
            val file = File(getRecordingDirectory(), fileName)

            val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }

            recorder.setAudioSource(MediaRecorder.AudioSource.VOICE_COMMUNICATION)
            recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            recorder.setAudioEncodingBitRate(128000)
            recorder.setAudioSamplingRate(44100)
            recorder.setOutputFile(file.absolutePath)

            recorder.prepare()
            recorder.start()

            mediaRecorder = recorder
            recordingStatus = RecordingStatus.RECORDING
            Log.d("CallRecorder", "🎉 Legacy Recording STARTED -> ${file.absolutePath}")

        } catch (e: Exception) {
            Log.e("CallRecorder", "💥 Legacy Recording failed: ${e.message}", e)
            recordingStatus = RecordingStatus.FAILED
        }
    }

    private fun stopRecording() {
        if (recordingStatus != RecordingStatus.RECORDING) return

        try {
            mediaRecorder?.apply {
                stop()
                release()
            }
            Log.d("CallRecorder", "✅ Legacy Recording STOPPED")
        } catch (e: Exception) {
            Log.e("CallRecorder", "❌ Legacy Error stopping recording: ${e.message}")
        } finally {
            mediaRecorder = null
            recordingStatus = RecordingStatus.IDLE
            restoreAudioSettings()
        }
    }
}

/**
 * 🔹 Receiver to register Telephony listeners
 */
class IncomingCallReceiver : BroadcastReceiver() {

    companion object {
        private var isListenerRegistered = false
        private var callStateListener: CallStateListener? = null
        private var callStateCallback: CallStateCallback? = null
    }

    override fun onReceive(context: Context, intent: Intent) {
        val incomingNumber = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER)

        if (!isListenerRegistered) {
            try {
                val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    callStateCallback = CallStateCallback(context).apply {
                        setPhoneNumber(incomingNumber)
                    }
                    telephonyManager.registerTelephonyCallback(context.mainExecutor, callStateCallback!!)
                } else {
                    callStateListener = CallStateListener(context)
                    @Suppress("DEPRECATION")
                    telephonyManager.listen(callStateListener, PhoneStateListener.LISTEN_CALL_STATE)
                }
                isListenerRegistered = true
            } catch (e: Exception) {
                Log.e("CallRecorder", "💥 Failed to register listener: ${e.message}")
            }
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                callStateCallback?.setPhoneNumber(incomingNumber)
            }
        }
    }
}
