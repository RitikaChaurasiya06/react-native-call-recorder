package com.callrecorderapp

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.facebook.react.ReactActivity
import com.facebook.react.ReactActivityDelegate
import com.facebook.react.defaults.DefaultNewArchitectureEntryPoint.fabricEnabled
import com.facebook.react.defaults.DefaultReactActivityDelegate
import android.app.AlertDialog
import android.widget.Toast

class MainActivity : ReactActivity() {
    
    private companion object {
        const val PERMISSION_REQUEST_CODE = 1001
        const val OVERLAY_PERMISSION_REQUEST_CODE = 1002
    }

    override fun getMainComponentName(): String = "CallRecorderApp"

    override fun createReactActivityDelegate(): ReactActivityDelegate =
        DefaultReactActivityDelegate(this, mainComponentName, fabricEnabled)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d("CallRecorder", "🚀 MainActivity created")
        requestAllPermissions()
    }

    /**
     * Request all necessary runtime permissions with user-friendly explanations
     */
    private fun requestAllPermissions() {
        val permissions = mutableListOf<String>()
        
        // Core permissions required for call recording
        permissions.addAll(listOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.READ_CALL_LOG,
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.MODIFY_AUDIO_SETTINGS
        ))

        // Storage permissions based on Android version
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.READ_MEDIA_AUDIO)
        } else {
            permissions.addAll(listOf(
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            ))
        }

        // Check which permissions are missing
        val missingPermissions = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missingPermissions.isNotEmpty()) {
            Log.d("CallRecorder", "⚠️ Requesting permissions: $missingPermissions")
            
            // Show explanation dialog first
            showPermissionExplanationDialog {
                ActivityCompat.requestPermissions(
                    this,
                    missingPermissions.toTypedArray(),
                    PERMISSION_REQUEST_CODE
                )
            }
        } else {
            Log.d("CallRecorder", "✅ All permissions already granted")
            checkSystemAlertWindowPermission()
        }
    }

    private fun showPermissionExplanationDialog(onProceed: () -> Unit) {
        AlertDialog.Builder(this)
            .setTitle("Permissions Required")
            .setMessage("""
                This app needs the following permissions to record calls:
                
                • 🎙️ Microphone Access - To record audio
                • 📞 Phone Access - To detect incoming/outgoing calls  
                • 📋 Call Log Access - To identify call details
                • 📁 Storage Access - To save recordings
                • 👥 Contacts Access - To identify callers
                
                ⚠️ Important: Call recording may not work on all devices due to Android security restrictions.
            """.trimIndent())
            .setPositiveButton("Grant Permissions") { _, _ -> onProceed() }
            .setNegativeButton("Cancel") { _, _ -> 
                showPermissionDeniedDialog()
            }
            .setCancelable(false)
            .show()
    }

    private fun showPermissionDeniedDialog() {
        AlertDialog.Builder(this)
            .setTitle("App Cannot Function")
            .setMessage("Without these permissions, the app cannot record calls. You can grant them later in Settings > Apps > Call Recorder > Permissions.")
            .setPositiveButton("Open Settings") { _, _ -> openAppSettings() }
            .setNegativeButton("Close App") { _, _ -> finish() }
            .show()
    }

    private fun checkSystemAlertWindowPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!Settings.canDrawOverlays(this)) {
                AlertDialog.Builder(this)
                    .setTitle("Overlay Permission")
                    .setMessage("For better recording reliability, allow this app to display over other apps.")
                    .setPositiveButton("Allow") { _, _ ->
                        val intent = Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:$packageName")
                        )
                        startActivityForResult(intent, OVERLAY_PERMISSION_REQUEST_CODE)
                    }
                    .setNegativeButton("Skip") { _, _ -> 
                        showSetupCompleteMessage()
                    }
                    .show()
            } else {
                showSetupCompleteMessage()
            }
        } else {
            showSetupCompleteMessage()
        }
    }

    private fun showSetupCompleteMessage() {
        Toast.makeText(
            this, 
            "✅ Setup complete! The app will now attempt to record calls automatically.", 
            Toast.LENGTH_LONG
        ).show()
        
        // Show important warning about call recording limitations
        AlertDialog.Builder(this)
            .setTitle("Important Notice")
            .setMessage("""
                📱 Call Recording Limitations:
                
                • Many modern Android devices block call recording for privacy
                • Some manufacturers completely disable this feature
                • Recording may only capture your voice, not the other party
                • This is a system limitation, not an app issue
                
                💡 For best results:
                • Use speakerphone during calls
                • Test with different devices if possible
                • Check your device manufacturer's policies
            """.trimIndent())
            .setPositiveButton("Understood") { _, _ -> }
            .show()
    }

    private fun openAppSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", packageName, null)
        }
        startActivity(intent)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        
        if (requestCode == PERMISSION_REQUEST_CODE) {
            val deniedPermissions = mutableListOf<String>()
            val grantedPermissions = mutableListOf<String>()
            
            permissions.forEachIndexed { index, permission ->
                if (grantResults[index] == PackageManager.PERMISSION_GRANTED) {
                    grantedPermissions.add(getPermissionName(permission))
                } else {
                    deniedPermissions.add(getPermissionName(permission))
                }
            }
            
            if (grantedPermissions.isNotEmpty()) {
                Log.d("CallRecorder", "✅ Granted permissions: $grantedPermissions")
            }
            
            if (deniedPermissions.isNotEmpty()) {
                Log.e("CallRecorder", "❌ Denied permissions: $deniedPermissions")
                handleDeniedPermissions(deniedPermissions)
            } else {
                // All permissions granted, check for overlay permission
                checkSystemAlertWindowPermission()
            }
        }
    }

    private fun getPermissionName(permission: String): String {
        return when (permission) {
            Manifest.permission.RECORD_AUDIO -> "Microphone"
            Manifest.permission.READ_PHONE_STATE -> "Phone"
            Manifest.permission.READ_CALL_LOG -> "Call Log"
            Manifest.permission.READ_CONTACTS -> "Contacts"
            Manifest.permission.WRITE_EXTERNAL_STORAGE -> "Storage (Write)"
            Manifest.permission.READ_EXTERNAL_STORAGE -> "Storage (Read)"
            Manifest.permission.READ_MEDIA_AUDIO -> "Media Audio"
            Manifest.permission.MODIFY_AUDIO_SETTINGS -> "Audio Settings"
            else -> permission.substringAfterLast('.')
        }
    }

    private fun handleDeniedPermissions(deniedPermissions: List<String>) {
        val criticalPermissions = listOf("Microphone", "Phone", "Call Log")
        val hasCriticalDenials = deniedPermissions.any { it in criticalPermissions }
        
        if (hasCriticalDenials) {
            AlertDialog.Builder(this)
                .setTitle("Critical Permissions Denied")
                .setMessage("""
                    The following critical permissions were denied:
                    ${deniedPermissions.joinToString(", ")}
                    
                    The app cannot function without these permissions.
                    
                    Please go to Settings and manually grant these permissions.
                """.trimIndent())
                .setPositiveButton("Open Settings") { _, _ -> openAppSettings() }
                .setNegativeButton("Try Again") { _, _ -> requestAllPermissions() }
                .setNeutralButton("Continue Anyway") { _, _ -> 
                    showSetupCompleteMessage()
                }
                .show()
        } else {
            // Only non-critical permissions denied
            Toast.makeText(
                this,
                "Some optional permissions denied. App will work with limited functionality.",
                Toast.LENGTH_LONG
            ).show()
            checkSystemAlertWindowPermission()
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        
        if (requestCode == OVERLAY_PERMISSION_REQUEST_CODE) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (Settings.canDrawOverlays(this)) {
                    Toast.makeText(this, "✅ Overlay permission granted", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "⚠️ Overlay permission denied - may affect recording reliability", Toast.LENGTH_LONG).show()
                }
            }
            showSetupCompleteMessage()
        }
    }

    override fun onResume() {
        super.onResume()
        
        // Check if we lost any critical permissions while app was in background
        val criticalPermissions = listOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.READ_CALL_LOG
        )
        
        val lostPermissions = criticalPermissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        
        if (lostPermissions.isNotEmpty()) {
            Log.w("CallRecorder", "⚠️ Lost permissions detected: $lostPermissions")
            Toast.makeText(
                this,
                "Some permissions were revoked. Please re-grant them for the app to work.",
                Toast.LENGTH_LONG
            ).show()
        }
    }
}