package com.example.audioexample

import android.content.pm.PackageManager
import android.media.*
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Environment
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import kotlinx.android.synthetic.main.activity_main.*
import java.io.*
import java.lang.RuntimeException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        var permissions = arrayOf(
            android.Manifest.permission.WRITE_EXTERNAL_STORAGE,
            android.Manifest.permission.RECORD_AUDIO
        )
        var isGranted = true;
        for (permission in permissions) {
            isGranted = ContextCompat.checkSelfPermission(
                this,
                permission
            ) == PackageManager.PERMISSION_GRANTED
            if (!isGranted) break
        }
        if (!isGranted) {
            ActivityCompat.requestPermissions(this, permissions, 0)
        }

        btn_start.setOnClickListener {
            startRecord()
            tv_state.setText("recording")
        }
        btn_stop.setOnClickListener {
            stopRecord()
            tv_state.setText("stop")
        }
        btn_play.setOnClickListener {
            playRecord()
        }
    }

    private var audioRecord: AudioRecord
    private var bufferSizeInBytes:Int = 0
    private var isRecording: Boolean = false
    private var threadPool: ExecutorService
    private var sampleRateInHz = 44100
    private var fileName = "audio-record.pcm"

    private fun log(text: String) {
        Log.e("MainActivity", text)
    }

    init {
        bufferSizeInBytes = AudioRecord.getMinBufferSize(
            sampleRateInHz,
            AudioFormat.CHANNEL_IN_STEREO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        if (AudioRecord.ERROR_BAD_VALUE == bufferSizeInBytes || AudioRecord.ERROR == bufferSizeInBytes)
            log("Unable to getMinBufferSize")
        /**
         * audioSource
         * sampleRateInHz
         * channelConfig
         * audioFormat
         * bufferSizeInBytes
         */
        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            sampleRateInHz,
            AudioFormat.CHANNEL_IN_STEREO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufferSizeInBytes
        )
        threadPool = Executors.newSingleThreadExecutor()
    }

    private fun stopRecord() {
        isRecording = false
        if (audioRecord != null) {
            with(audioRecord) {
                if (recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                    stop()
                }
                if (state == AudioRecord.STATE_INITIALIZED) {
                    release()
                }
            }
        }
    }

    private fun startRecord() {
        with(audioRecord) {
            if (this.state == AudioRecord.STATE_UNINITIALIZED)
                throw RuntimeException("AudioRecord STATE_UNINITIALIZED")
            startRecording()
            threadPool.execute{
                log("recordTask is run")
                var saveFile = File(Environment.getExternalStorageDirectory(), fileName)
                if (saveFile.length() > 0)
                    saveFile.delete()
                var dataOutputStream =
                    DataOutputStream(BufferedOutputStream(FileOutputStream(saveFile)))
                var data = ByteArray(bufferSizeInBytes)
                isRecording = true
                while (isRecording && audioRecord.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                    var read = audioRecord.read(data, 0, bufferSizeInBytes) as Int
                    if (AudioRecord.ERROR_INVALID_OPERATION != read && AudioRecord.ERROR_BAD_VALUE != read) {
                        log("read size = $read")
                        dataOutputStream.write(data, 0, read)
                    }
                }
                dataOutputStream.close()
                log("recordTask is stop")
            }
        }
    }

    private fun playRecord() {
        var audioTrack = AudioTrack(
            AudioManager.STREAM_MUSIC,
            sampleRateInHz,
            AudioFormat.CHANNEL_IN_STEREO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufferSizeInBytes,
            AudioTrack.MODE_STREAM
        )
        threadPool.execute {
            audioTrack.run {
                log("start play pcm")
                play()
                var bytes = ByteArray(bufferSizeInBytes)
                var len = 0
                var dataInputStream = DataInputStream(
                    BufferedInputStream(
                        FileInputStream(
                            File(
                                Environment.getExternalStorageDirectory(),
                                fileName
                            )
                        )
                    )
                )
                len = dataInputStream.read(bytes)
                while (len != -1) {
                    log("play size $len")
                    audioTrack.write(bytes, 0, len)
                    len = dataInputStream.read(bytes)
                }
                dataInputStream.close()
                stop()
                release()
            }
        }
    }

}
