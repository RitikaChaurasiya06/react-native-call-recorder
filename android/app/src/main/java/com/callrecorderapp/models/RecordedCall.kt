package com.callrecorderapp.models

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import java.io.File
import java.util.Date

@Parcelize
data class RecordedCall(
    val fileName: String,
    val phoneNumber: String,
    val callType: String, // "Incoming" or "Outgoing"
    val callDuration: Long, // in milliseconds
    val startTime: Date,
    val endTime: Date,
    val recordingFile: File
) : Parcelable