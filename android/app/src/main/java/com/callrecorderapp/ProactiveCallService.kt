package com.callrecorderapp

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.telephony.PhoneStateListener
import android.telephony.TelephonyManager
import android.telephony.TelephonyCallback
import android.util.Log
import android.os.Build
import android.content.Context
import androidx.annotation.RequiresApi
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import androidx.core.app.NotificationCompat
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat

class ProactiveCallService : Service() {

    companion object {
        private const val TAG = "ProactiveCallService"
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "call_recording_service"
    }

    private var telephonyManager: TelephonyManager? = null
    private var modernListener: ModernCallListener? = null
    private var legacyListener: LegacyCallListener? = null
    private var recorder: CallRecorder? = null

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service created - Starting proactive call monitoring")
        
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification())
        
        recorder = CallRecorder(this)
        telephonyManager = getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        
        registerCallListener()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Call Recording Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Background service for call recording"
                setSound(null, null)
            }
            
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): android.app.Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent, 
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Call Recording Active")
            .setContentText("Monitoring calls for voice recording")
            .setSmallIcon(android.R.drawable.ic_menu_call)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    private fun registerCallListener() {
        if (!hasRequiredPermissions()) {
            Log.e(TAG, "Missing required permissions")
            return
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                modernListener = ModernCallListener(this, recorder!!)
                telephonyManager?.registerTelephonyCallback(mainExecutor, modernListener!!)
                Log.d(TAG, "Registered modern telephony callback")
            } else {
                legacyListener = LegacyCallListener(recorder!!)
                @Suppress("DEPRECATION")
                telephonyManager?.listen(legacyListener, PhoneStateListener.LISTEN_CALL_STATE)
                Log.d(TAG, "Registered legacy phone state listener")
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "Security exception: ${e.message}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register call listener: ${e.message}")
        }
    }

    private fun hasRequiredPermissions(): Boolean {
        val permissions = listOf(
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.RECORD_AUDIO
        )
        
        return permissions.all { 
            ActivityCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED 
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "Service started/restarted")
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "Service destroyed - Cleaning up")
        
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                modernListener?.let { telephonyManager?.unregisterTelephonyCallback(it) }
            } else {
                legacyListener?.let { 
                    @Suppress("DEPRECATION")
                    telephonyManager?.listen(it, PhoneStateListener.LISTEN_NONE) 
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error unregistering listener: ${e.message}")
        }
        
        recorder?.stopRecording()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun setSpeakerOn(enable: Boolean) {
        try {
            val audioManager = getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
            audioManager.mode = android.media.AudioManager.MODE_IN_CALL
            audioManager.isSpeakerphoneOn = enable
            Log.d(TAG, "Speakerphone set to: $enable")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set speakerphone: ${e.message}")
        }
    }

    // ------------------- LISTENERS -------------------

    @RequiresApi(Build.VERSION_CODES.S)
    inner class ModernCallListener(
        private val context: Context, 
        private val recorder: CallRecorder
    ) : TelephonyCallback(), TelephonyCallback.CallStateListener {
        
        private var lastState = TelephonyManager.CALL_STATE_IDLE
        private var isOutgoingCall = false

        override fun onCallStateChanged(state: Int) {
            Log.d(TAG, "IMMEDIATE: Call state changed to ${getStateString(state)}")
            
            when (state) {
                TelephonyManager.CALL_STATE_RINGING -> {
                    Log.d(TAG, "IMMEDIATE: Incoming call detected")
                    isOutgoingCall = false
                    recorder.setCallType("Incoming")
                }
                
                TelephonyManager.CALL_STATE_OFFHOOK -> {
                    if (lastState == TelephonyManager.CALL_STATE_IDLE) {
                        Log.d(TAG, "IMMEDIATE: Outgoing call detected")
                        isOutgoingCall = true
                        recorder.setCallType("Outgoing")
                    } else if (lastState == TelephonyManager.CALL_STATE_RINGING) {
                        isOutgoingCall = false
                    }

                    Log.d(TAG, "IMMEDIATE: Starting recording (isOutgoing: $isOutgoingCall)")
                    this@ProactiveCallService.setSpeakerOn(true)
                    recorder.startRecording(isOutgoingCall)
                }
                
                TelephonyManager.CALL_STATE_IDLE -> {
                    Log.d(TAG, "IMMEDIATE: Call ended")
                    recorder.stopRecording()
                    this@ProactiveCallService.setSpeakerOn(false)
                    isOutgoingCall = false
                }
            }
            lastState = state
        }
        
        private fun getStateString(state: Int) = when (state) {
            TelephonyManager.CALL_STATE_IDLE -> "IDLE"
            TelephonyManager.CALL_STATE_RINGING -> "RINGING"
            TelephonyManager.CALL_STATE_OFFHOOK -> "OFFHOOK"
            else -> "UNKNOWN($state)"
        }
    }

    inner class LegacyCallListener(private val recorder: CallRecorder) : PhoneStateListener() {
        
        private var lastState = TelephonyManager.CALL_STATE_IDLE
        private var isOutgoingCall = false

        @Suppress("DEPRECATION")
        override fun onCallStateChanged(state: Int, incomingNumber: String?) {
            Log.d(TAG, "IMMEDIATE: Call state changed to ${getStateString(state)}, Number: $incomingNumber")
            
            incomingNumber?.let { recorder.setPhoneNumber(it) }

            when (state) {
                TelephonyManager.CALL_STATE_RINGING -> {
                    Log.d(TAG, "IMMEDIATE: Incoming call detected")
                    isOutgoingCall = false
                    recorder.setCallType("Incoming")
                }
                
                TelephonyManager.CALL_STATE_OFFHOOK -> {
                    if (lastState == TelephonyManager.CALL_STATE_IDLE) {
                        Log.d(TAG, "IMMEDIATE: Outgoing call detected")
                        isOutgoingCall = true
                        recorder.setCallType("Outgoing")
                    } else if (lastState == TelephonyManager.CALL_STATE_RINGING) {
                        isOutgoingCall = false
                    }

                    Log.d(TAG, "IMMEDIATE: Starting recording (isOutgoing: $isOutgoingCall)")
                    this@ProactiveCallService.setSpeakerOn(true)
                    recorder.startRecording(isOutgoingCall)
                }
                
                TelephonyManager.CALL_STATE_IDLE -> {
                    Log.d(TAG, "IMMEDIATE: Call ended")
                    recorder.stopRecording()
                    this@ProactiveCallService.setSpeakerOn(false)
                    isOutgoingCall = false
                }
            }
            lastState = state
        }
        
        private fun getStateString(state: Int) = when (state) {
            TelephonyManager.CALL_STATE_IDLE -> "IDLE"
            TelephonyManager.CALL_STATE_RINGING -> "RINGING"
            TelephonyManager.CALL_STATE_OFFHOOK -> "OFFHOOK"
            else -> "UNKNOWN($state)"
        }
    }

}
