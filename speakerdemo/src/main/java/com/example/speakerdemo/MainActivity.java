package com.example.speakerdemo;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.AudioDeviceInfo;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.KeyEvent;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.core.app.ActivityCompat;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicBoolean;


public class MainActivity extends Activity {

    private static final int REQUEST_CODE__RECORD_AUDIO = 10;

    private static final int SAMPLING_RATE_IN_HZ = 16000;
    private static final int CHANNEL_CONFIG = 16;
    private static final int AUDIO_FORMAT = 2;
    private static final int BUFFER_SIZE_FACTOR = 2;
    private static final int BUFFER_SIZE = AudioRecord.getMinBufferSize(SAMPLING_RATE_IN_HZ,
            CHANNEL_CONFIG, AUDIO_FORMAT) * BUFFER_SIZE_FACTOR;

    private final AtomicBoolean recordingInProgress = new AtomicBoolean(false);

    private AudioRecord recorder;
    private Thread recordingThread;

    public static final String TAG = "MainActivity";

    @RequiresApi(api = Build.VERSION_CODES.P)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main_activity);
    }

    private void startRecording() {
        recorder = new AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION, SAMPLING_RATE_IN_HZ,
                CHANNEL_CONFIG, AUDIO_FORMAT, BUFFER_SIZE);

        AudioManager audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        assert audioManager != null;
        AudioDeviceInfo[] adi = audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS);
        for (int i = 0; i < adi.length; i++) {
            AudioDeviceInfo device = adi[i];
            Log.d(TAG, "Device_" + i + " getType: " + device.getType());
            if (device.getType() == AudioDeviceInfo.TYPE_BUILTIN_MIC) {
                if (recorder.setPreferredDevice(device)) {
                    Log.d(TAG, "Выбран TYPE_BUILTIN_MIC: " + device.getType());
                    break;
                }
            }
        }

        recorder.startRecording();
        recordingInProgress.set(true);
        recordingThread = new Thread(new RecordingRunnable(), "Recording Thread");
        recordingThread.start();
        showMessage("Началась запись!");
    }

    private void stopRecording() {
        if (null == recorder) {
            return;
        }
        recordingInProgress.set(false);
        recorder.stop();
        recorder.release();
        recorder = null;
        recordingThread = null;
        showMessage("Запись остановлена!");
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        Log.d(TAG, "KEY CODE: " + keyCode);
        if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) {
            if (recordingInProgress.get()) {
                stopRecording();
            } else {
                recordStart();
            }
            return true;
        }
        return false;
    }

    private void recordStart() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO, Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQUEST_CODE__RECORD_AUDIO);
        } else {
            startRecording();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == REQUEST_CODE__RECORD_AUDIO) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startRecording();
            } else {
                showMessage("Разрешение на использоование микрофона не получено");
                finish();
            }
        }
    }

    private class RecordingRunnable implements Runnable {

        @Override
        public void run() {
            final File file = new File(Environment.getExternalStorageDirectory(), "recording.pcm");
            final ByteBuffer buffer = ByteBuffer.allocateDirect(BUFFER_SIZE);

            try (final FileOutputStream outStream = new FileOutputStream(file)) {
                while (recordingInProgress.get()) {
                    int result = recorder.read(buffer, BUFFER_SIZE);
                    if (result < 0) {
                        throw new RuntimeException("Reading of audio buffer failed: " +
                                getBufferReadFailureReason(result));
                    }
                    outStream.write(buffer.array(), 0, BUFFER_SIZE);
                    buffer.clear();
                }
            } catch (IOException e) {
                throw new RuntimeException("Writing of recorded audio failed", e);
            }
        }

        private String getBufferReadFailureReason(int errorCode) {
            switch (errorCode) {
                case AudioRecord.ERROR_INVALID_OPERATION:
                    return "ERROR_INVALID_OPERATION";
                case AudioRecord.ERROR_BAD_VALUE:
                    return "ERROR_BAD_VALUE";
                case AudioRecord.ERROR_DEAD_OBJECT:
                    return "ERROR_DEAD_OBJECT";
                case AudioRecord.ERROR:
                    return "ERROR";
                default:
                    return "Unknown (" + errorCode + ")";
            }
        }
    }

    private void showMessage(String text) {
        Toast.makeText(this, text, Toast.LENGTH_SHORT).show();
    }

}
