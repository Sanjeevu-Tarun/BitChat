package com.bitchat.util

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import java.io.File
import java.io.IOException

class AudioRecorder(private val context: Context) {

    companion object {
        private const val TAG = "AudioRecorder"
        private const val SAMPLE_RATE = 44100
        private const val BIT_RATE = 128000
        private const val MIN_RECORDING_DURATION = 1000L // 1 second minimum
    }

    private var mediaRecorder: MediaRecorder? = null
    private var outputFile: File? = null
    private var isRecording = false
    private var recordingStartTime = 0L

    @Suppress("DEPRECATION")
    fun startRecording(): File? {
        return try {
            // Clean up any previous recording
            cleanup()

            val timestamp = System.currentTimeMillis()
            recordingStartTime = timestamp
            outputFile = File(context.filesDir, "audio_${timestamp}.m4a")

            mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                MediaRecorder()
            }.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setOutputFile(outputFile!!.absolutePath)
                setAudioEncodingBitRate(BIT_RATE)
                setAudioSamplingRate(SAMPLE_RATE)

                // Set max duration to prevent extremely long recordings
                setMaxDuration(300000) // 5 minutes max

                prepare()
                start()
            }

            isRecording = true
            Log.d(TAG, "Recording started: ${outputFile!!.absolutePath}")
            outputFile
        } catch (e: IOException) {
            Log.e(TAG, "Failed to start recording - IOException", e)
            cleanup()
            null
        } catch (e: SecurityException) {
            Log.e(TAG, "Failed to start recording - SecurityException (missing permission?)", e)
            cleanup()
            null
        } catch (e: IllegalStateException) {
            Log.e(TAG, "Failed to start recording - IllegalStateException", e)
            cleanup()
            null
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error starting recording", e)
            cleanup()
            null
        }
    }

    fun stopRecording(): File? {
        return if (isRecording && mediaRecorder != null) {
            try {
                val recordingDuration = System.currentTimeMillis() - recordingStartTime

                // Check if recording duration meets minimum requirement
                if (recordingDuration < MIN_RECORDING_DURATION) {
                    Log.w(TAG, "Recording too short (${recordingDuration}ms), canceling")
                    cancelRecording()
                    return null
                }

                mediaRecorder?.apply {
                    stop()
                    release()
                }
                mediaRecorder = null
                isRecording = false

                val file = outputFile
                if (file?.exists() == true && file.length() > 0) {
                    Log.d(TAG, "Recording stopped successfully: ${file.absolutePath}, size: ${file.length()}, duration: ${recordingDuration}ms")
                    file
                } else {
                    Log.w(TAG, "Recording file is empty or doesn't exist")
                    file?.delete()
                    null
                }
            } catch (e: RuntimeException) {
                Log.e(TAG, "Failed to stop recording", e)
                cleanup()
                null
            } catch (e: Exception) {
                Log.e(TAG, "Unexpected error stopping recording", e)
                cleanup()
                null
            }
        } else {
            Log.w(TAG, "Not currently recording")
            null
        }
    }

    fun cancelRecording() {
        try {
            if (isRecording && mediaRecorder != null) {
                mediaRecorder?.apply {
                    try {
                        stop()
                    } catch (e: RuntimeException) {
                        Log.w(TAG, "Error stopping MediaRecorder during cancel", e)
                    }
                    release()
                }
                mediaRecorder = null
                isRecording = false
            }

            // Delete the file if it exists
            outputFile?.let { file ->
                if (file.exists()) {
                    val deleted = file.delete()
                    Log.d(TAG, "Recording file deleted: $deleted (${file.absolutePath})")
                }
            }
            outputFile = null
        } catch (e: Exception) {
            Log.e(TAG, "Error canceling recording", e)
            cleanup()
        }
    }

    fun getMaxAmplitude(): Int {
        return try {
            if (isRecording && mediaRecorder != null) {
                mediaRecorder?.maxAmplitude ?: 0
            } else {
                0
            }
        } catch (e: IllegalStateException) {
            Log.w(TAG, "Cannot get amplitude - MediaRecorder in invalid state", e)
            0
        } catch (e: Exception) {
            Log.w(TAG, "Error getting amplitude", e)
            0
        }
    }

    fun isCurrentlyRecording(): Boolean = isRecording

    fun getRecordingDuration(): Long {
        return if (isRecording) {
            System.currentTimeMillis() - recordingStartTime
        } else {
            0L
        }
    }

    private fun cleanup() {
        try {
            mediaRecorder?.apply {
                if (isRecording) {
                    try {
                        stop()
                    } catch (e: RuntimeException) {
                        Log.w(TAG, "Error stopping MediaRecorder during cleanup", e)
                    }
                }
                release()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing MediaRecorder", e)
        }

        mediaRecorder = null
        isRecording = false

        // Clean up output file if recording was incomplete
        outputFile?.let { file ->
            if (file.exists() && file.length() == 0L) {
                file.delete()
                Log.d(TAG, "Cleaned up empty recording file")
            }
        }
        outputFile = null
    }

    // Call this when the recorder is no longer needed
    fun release() {
        if (isRecording) {
            cancelRecording()
        } else {
            cleanup()
        }
    }
}