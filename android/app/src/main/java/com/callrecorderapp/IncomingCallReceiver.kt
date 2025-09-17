package com.callrecorderapp

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.MediaRecorder
import android.telephony.PhoneStateListener
import android.telephony.TelephonyManager
import android.os.Environment
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import android.util.Log

enum class RecordingStatus {
    IDLE,
    RECORDING
}

class CallStateListener(private val context: Context) : PhoneStateListener() {

    private var mediaRecorder: MediaRecorder? = null
    private var recordingStatus = RecordingStatus.IDLE

    override fun onCallStateChanged(state: Int, incomingNumber: String?) {
        super.onCallStateChanged(state, incomingNumber)

        when (state) {
            TelephonyManager.CALL_STATE_RINGING -> {
                Log.d("CallRecorder", "📞 Phone is RINGING. Number: ${incomingNumber ?: "unknown"}")
            }

            TelephonyManager.CALL_STATE_OFFHOOK -> {
                Log.d("CallRecorder", "✅ Call PICKED UP. Number: ${incomingNumber ?: "unknown"}")
                startRecording()
            }

            TelephonyManager.CALL_STATE_IDLE -> {
                Log.d("CallRecorder", "🛑 Call ENDED. Number: ${incomingNumber ?: "unknown"}")
                stopRecording()
            }

            else -> {
                Log.d("CallRecorder", "❓ Unknown state: $state, Number: ${incomingNumber ?: "unknown"}")
            }
        }
    }

    private fun startRecording() {
        if (recordingStatus == RecordingStatus.RECORDING) {
            Log.d("CallRecorder", "⚠️ Already recording, skipping start.")
            return
        }

        val sdf = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
        val fileName = "CALL_${sdf.format(Date())}.m4a"
        val file = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC), fileName)

        try {
            // 🔊 Force loudspeaker ON so the other side is audible
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            audioManager.isSpeakerphoneOn = true
            Log.d("CallRecorder", "🔊 Speakerphone forced ON for recording")

            mediaRecorder = MediaRecorder().apply {
                setAudioSource(MediaRecorder.AudioSource.MIC) // capture from mic
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setOutputFile(file.absolutePath)
                prepare()
                start()
                recordingStatus = RecordingStatus.RECORDING
                Log.d("CallRecorder", "🎙️ Recording STARTED -> ${file.absolutePath}")
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Log.e("CallRecorder", "❌ Recording FAILED to start: ${e.message}")
        }
    }

    private fun stopRecording() {
        if (recordingStatus != RecordingStatus.RECORDING) {
            Log.d("CallRecorder", "⚠️ Not recording, skipping stop.")
            return
        }
        try {
            mediaRecorder?.stop()
            mediaRecorder?.release()
            Log.d("CallRecorder", "💾 Recording STOPPED and saved")
        } catch (e: Exception) {
            e.printStackTrace()
            Log.e("CallRecorder", "❌ Error stopping recording: ${e.message}")
        }
        mediaRecorder = null
        recordingStatus = RecordingStatus.IDLE

        // 🔇 Turn speaker back OFF when call ends
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audioManager.isSpeakerphoneOn = false
        Log.d("CallRecorder", "🔇 Speakerphone turned OFF after call")
    }
}

class IncomingCallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Log.d("CallRecorder", "📡 IncomingCallReceiver triggered: ${intent.action}")

        val telephony = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        telephony.listen(CallStateListener(context), PhoneStateListener.LISTEN_CALL_STATE)

        Log.d("CallRecorder", "👂 CallStateListener registered")
    }
}
