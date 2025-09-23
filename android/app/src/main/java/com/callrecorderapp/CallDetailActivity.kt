package com.callrecorderapp

import android.media.MediaPlayer
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.SeekBar
import androidx.appcompat.app.AppCompatActivity
import com.callrecorderapp.databinding.ActivityCallDetailBinding
import com.callrecorderapp.models.RecordedCall
import java.text.SimpleDateFormat
import java.util.*

class CallDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCallDetailBinding
    private var mediaPlayer: MediaPlayer? = null
    private var isPlaying = false
    private val handler = Handler(Looper.getMainLooper()) // Immutable
    private val sdfTime = SimpleDateFormat("HH:mm:ss", Locale.getDefault()) // Immutable

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCallDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val recordedCall = intent.getParcelableExtra<RecordedCall>("recorded_call") // Immutable
        if (recordedCall != null) {
            displayCallDetails(recordedCall)
            setupMediaPlayer(recordedCall)
        }
    }

    private fun displayCallDetails(call: RecordedCall) {
        binding.textViewNumberValue.text = call.phoneNumber
        binding.textViewTypeValue.text = call.callType
        binding.textViewDurationValue.text = formatDuration(call.callDuration)
        binding.textViewPickupValue.text = sdfTime.format(call.startTime)
        binding.textViewHangupValue.text = sdfTime.format(call.endTime)
    }

    private fun setupMediaPlayer(call: RecordedCall) {
        mediaPlayer = MediaPlayer().apply {
            setDataSource(call.recordingFile.absolutePath)
            prepare()
            binding.textViewTotalTime.text = formatDuration(duration.toLong())

            setOnCompletionListener {
                val   isPlaying = false
                binding.imageViewPlayback.setImageResource(R.drawable.ic_play)
                binding.seekBarPlayback.progress = 0
                binding.textViewCurrentTime.text = "00:00:00"
                handler.removeCallbacks(updateSeekBarRunnable)
            }
        }

        binding.imageViewPlayback.setOnClickListener {
            if (isPlaying) {
                mediaPlayer?.pause()
                binding.imageViewPlayback.setImageResource(R.drawable.ic_play)
                handler.removeCallbacks(updateSeekBarRunnable)
            } else {
                mediaPlayer?.start()
                binding.imageViewPlayback.setImageResource(R.drawable.ic_pause)
                handler.post(updateSeekBarRunnable)
            }
            isPlaying = !isPlaying
        }

        binding.seekBarPlayback.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    mediaPlayer?.seekTo(progress)
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }

    private val updateSeekBarRunnable = object : Runnable { // Immutable
        override fun run() {
            mediaPlayer?.let {
                binding.seekBarPlayback.max = it.duration
                binding.seekBarPlayback.progress = it.currentPosition
                binding.textViewCurrentTime.text = formatDuration(it.currentPosition.toLong())
            }
            handler.postDelayed(this, 1000)
        }
    }

    private fun formatDuration(millis: Long): String {
        val seconds = (millis / 1000) % 60
        val minutes = (millis / (1000 * 60)) % 60
        val hours = (millis / (1000 * 60 * 60)) % 24
        return String.format("%02d:%02d:%02d", hours, minutes, seconds)
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(updateSeekBarRunnable)
        mediaPlayer?.release()
        mediaPlayer = null
    }
}
