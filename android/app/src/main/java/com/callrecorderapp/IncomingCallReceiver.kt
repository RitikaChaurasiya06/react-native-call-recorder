package com.callrecorderapp

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.telephony.PhoneStateListener
import android.telephony.TelephonyManager
import android.telephony.TelephonyCallback
import android.util.Log
import androidx.annotation.RequiresApi
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat

class IncomingCallReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "IncomingCallReceiver"
        private var isRegistered = false
        private var modernListener: ModernCallListener? = null
        private var legacyListener: LegacyCallListener? = null
        private var recorder: CallRecorder? = null
    }

    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "=== BroadcastReceiver onReceive ===")
        Log.d(TAG, "Intent action: ${intent.action}")
        Log.d(TAG, "Intent extras: ${intent.extras?.keySet()}")
        
        // Check permissions first
        if (!hasRequiredPermissions(context)) {
            Log.e(TAG, "Missing required permissions")
            return
        }
        
        when (intent.action) {
            TelephonyManager.ACTION_PHONE_STATE_CHANGED -> {
                handlePhoneStateChange(context, intent)
            }
            Intent.ACTION_NEW_OUTGOING_CALL -> {
                handleOutgoingCall(context, intent)
            }
            else -> {
                Log.w(TAG, "Unknown intent action: ${intent.action}")
            }
        }
    }

    private fun hasRequiredPermissions(context: Context): Boolean {
        val permissions = listOf(
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.READ_CALL_LOG
        )
        
        return permissions.all { 
            ActivityCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED 
        }
    }

    private fun handlePhoneStateChange(context: Context, intent: Intent) {
        val state = intent.getStringExtra(TelephonyManager.EXTRA_STATE)
        val incomingNumber = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER)
        
        Log.d(TAG, "--- Phone State Changed ---")
        Log.d(TAG, "State: $state")
        Log.d(TAG, "Incoming Number: $incomingNumber")
        
        when (state) {
            TelephonyManager.EXTRA_STATE_RINGING -> {
                Log.d(TAG, "PHONE STATE: RINGING (Incoming Call)")
            }
            TelephonyManager.EXTRA_STATE_OFFHOOK -> {
                Log.d(TAG, "PHONE STATE: OFFHOOK (Call Active)")
            }
            TelephonyManager.EXTRA_STATE_IDLE -> {
                Log.d(TAG, "PHONE STATE: IDLE (No Call)")
            }
        }
        
        setupCallRecording(context, incomingNumber, "PHONE_STATE")
    }

    private fun handleOutgoingCall(context: Context, intent: Intent) {
        val outgoingNumber = intent.getStringExtra(Intent.EXTRA_PHONE_NUMBER)
        
        Log.d(TAG, "--- Outgoing Call ---")
        Log.d(TAG, "Outgoing Number: $outgoingNumber")
        
        setupCallRecording(context, outgoingNumber, "OUTGOING_CALL")
    }

    private fun setupCallRecording(context: Context, phoneNumber: String?, source: String) {
        Log.d(TAG, "Setting up call recording from source: $source")
        
        try {
            val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
            
            if (recorder == null) {
                recorder = CallRecorder(context)
                Log.d(TAG, "Created new CallRecorder instance")
            }
            
            recorder?.setPhoneNumber(phoneNumber)

            if (!isRegistered) {
                Log.d(TAG, "Registering telephony callback/listener...")
                
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    registerModernCallback(context, tm)
                } else {
                    registerLegacyListener(tm)
                }
                
                isRegistered = true
                Log.d(TAG, "Telephony callback/listener registered successfully")
            } else {
                Log.d(TAG, "Telephony callback/listener already registered")
            }
            
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException: Missing permissions - ${e.message}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to setup call recording: ${e.message}", e)
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun registerModernCallback(context: Context, tm: TelephonyManager) {
        try {
            modernListener = ModernCallListener(context, recorder!!)
            tm.registerTelephonyCallback(context.mainExecutor, modernListener!!)
            Log.d(TAG, "Modern TelephonyCallback registered for Android S+")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register modern callback: ${e.message}")
        }
    }

    private fun registerLegacyListener(tm: TelephonyManager) {
        try {
            legacyListener = LegacyCallListener(recorder!!)
            @Suppress("DEPRECATION")
            tm.listen(legacyListener, PhoneStateListener.LISTEN_CALL_STATE)
            Log.d(TAG, "Legacy PhoneStateListener registered")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register legacy listener: ${e.message}")
        }
    }
}

@RequiresApi(Build.VERSION_CODES.S)
class ModernCallListener(
    private val context: Context, 
    private val recorder: CallRecorder
) : TelephonyCallback(), TelephonyCallback.CallStateListener {

    companion object {
        private const val TAG = "ModernCallListener"
    }
    
    private var lastState = TelephonyManager.CALL_STATE_IDLE
    private var isOutgoingCall = false

    override fun onCallStateChanged(state: Int) {
        Log.d(TAG, "=== Modern Call State Changed ===")
        Log.d(TAG, "Previous state: ${getStateString(lastState)}")
        Log.d(TAG, "New state: ${getStateString(state)}")
        
        when (state) {
            TelephonyManager.CALL_STATE_RINGING -> {
                Log.d(TAG, "📞 INCOMING CALL DETECTED")
                isOutgoingCall = false
                recorder.setCallType("Incoming")
            }
            
            TelephonyManager.CALL_STATE_OFFHOOK -> {
                if (lastState == TelephonyManager.CALL_STATE_IDLE) {
                    Log.d(TAG, "📱 OUTGOING CALL DETECTED")
                    isOutgoingCall = true
                    recorder.setCallType("Outgoing")
                } else if (lastState == TelephonyManager.CALL_STATE_RINGING) {
                    Log.d(TAG, "📞 INCOMING CALL ANSWERED")
                    isOutgoingCall = false
                }
                
                Log.d(TAG, "🎙️ Starting recording for ${if (isOutgoingCall) "OUTGOING" else "INCOMING"} call")
                recorder.startRecording(isOutgoingCall)
            }
            
            TelephonyManager.CALL_STATE_IDLE -> {
                Log.d(TAG, "❌ CALL ENDED")
                recorder.stopRecording()
                isOutgoingCall = false
            }
        }
        
        lastState = state
        Log.d(TAG, "State transition complete. Is outgoing: $isOutgoingCall")
    }
    
    private fun getStateString(state: Int): String {
        return when (state) {
            TelephonyManager.CALL_STATE_IDLE -> "IDLE"
            TelephonyManager.CALL_STATE_RINGING -> "RINGING"
            TelephonyManager.CALL_STATE_OFFHOOK -> "OFFHOOK"
            else -> "UNKNOWN($state)"
        }
    }
}

class LegacyCallListener(private val recorder: CallRecorder) : PhoneStateListener() {

    companion object {
        private const val TAG = "LegacyCallListener"
    }
    
    private var lastState = TelephonyManager.CALL_STATE_IDLE
    private var isOutgoingCall = false

    @Suppress("DEPRECATION")
    override fun onCallStateChanged(state: Int, incomingNumber: String?) {
        Log.d(TAG, "=== Legacy Call State Changed ===")
        Log.d(TAG, "Previous state: ${getStateString(lastState)}")
        Log.d(TAG, "New state: ${getStateString(state)}")
        Log.d(TAG, "Phone number: $incomingNumber")
        
        if (!incomingNumber.isNullOrEmpty()) {
            recorder.setPhoneNumber(incomingNumber)
            Log.d(TAG, "Set phone number: $incomingNumber")
        }

        when (state) {
            TelephonyManager.CALL_STATE_RINGING -> {
                Log.d(TAG, "📞 INCOMING CALL DETECTED")
                isOutgoingCall = false
                recorder.setCallType("Incoming")
            }
            
            TelephonyManager.CALL_STATE_OFFHOOK -> {
                if (lastState == TelephonyManager.CALL_STATE_IDLE) {
                    Log.d(TAG, "📱 OUTGOING CALL DETECTED")
                    isOutgoingCall = true
                    recorder.setCallType("Outgoing")
                } else if (lastState == TelephonyManager.CALL_STATE_RINGING) {
                    Log.d(TAG, "📞 INCOMING CALL ANSWERED")
                    isOutgoingCall = false
                }
                
                Log.d(TAG, "🎙️ Starting recording for ${if (isOutgoingCall) "OUTGOING" else "INCOMING"} call")
                recorder.startRecording(isOutgoingCall)
            }
                   
            TelephonyManager.CALL_STATE_IDLE -> {
                Log.d(TAG, "❌ CALL ENDED")
                recorder.stopRecording()
                isOutgoingCall = false
            }
        }
        
        lastState = state
        Log.d(TAG, "State transition complete. Is outgoing: $isOutgoingCall")
    }
    
    private fun getStateString(state: Int): String {
        return when (state) {
            TelephonyManager.CALL_STATE_IDLE -> "IDLE"
            TelephonyManager.CALL_STATE_RINGING -> "RINGING"
            TelephonyManager.CALL_STATE_OFFHOOK -> "OFFHOOK"
            else -> "UNKNOWN($state)"
        }
    }
}