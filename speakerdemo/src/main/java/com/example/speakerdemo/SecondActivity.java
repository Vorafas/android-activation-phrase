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

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;

public class SecondActivity extends Activity {

    final int REQUEST_CODE__RECORD_AUDIO = 10;

    public static final String TAG = "MainActivity";

    private static final int RECORDER_SAMPLE_RATE = 44100;
    private static final int RECORDER_CHANNELS = 16;
    private static final int RECORDER_AUDIO_ENCODING = AudioFormat.ENCODING_PCM_16BIT;
    private static final int BUFFER_ELEMENTS_REC = 1024;
    private static final int BYTES_PER_ELEMENT = 2;

    private AudioRecord recorder;
    private Thread recordingThread;
    private boolean isRecording;
    private boolean isStartRecording;


    @RequiresApi(api = Build.VERSION_CODES.P)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main_activity);

    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        Log.d(TAG, "KEY CODE: " + keyCode);
        if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) {
            if (isStartRecording) {
                stopRecording();
                isStartRecording = false;
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
            isStartRecording = true;
        }
    }

    private void startRecording() {
        int bufferSize = BUFFER_ELEMENTS_REC * BYTES_PER_ELEMENT;
        recorder = new AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION, RECORDER_SAMPLE_RATE, RECORDER_CHANNELS, RECORDER_AUDIO_ENCODING, bufferSize);
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
        isRecording = true;
        recordingThread = new Thread(this::writeAudioDataToFile);
        recordingThread.start();
        showMessage("Началась запись!");
    }

    private byte[] short2byte(short[] sData) {
        int shortArraySize = sData.length;
        byte[] bytes = new byte[shortArraySize * 2];
        for (int i = 0; i < shortArraySize; i++) {
            bytes[i * 2] = (byte) (sData[i] & 0x00FF);
            bytes[(i * 2) + 1] = (byte) (sData[i] >> 8);
            sData[i] = 0;
        }
        return bytes;
    }

    private void writeAudioDataToFile() {
        String filePath = Environment.getExternalStorageDirectory().getAbsolutePath() + "/test.3gp";
        short sData[] = new short[BUFFER_ELEMENTS_REC];

        FileOutputStream os = null;
        try {
            os = new FileOutputStream(filePath);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }

        while (isRecording) {
            recorder.read(sData, 0, BUFFER_ELEMENTS_REC);
            try {
                byte bData[] = short2byte(sData);
                os.write(bData, 0, BUFFER_ELEMENTS_REC * BYTES_PER_ELEMENT);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        try {
            os.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void stopRecording() {
        if (recorder != null) {
            isRecording = false;
            recorder.stop();
            recorder.release();
            recorder = null;
            recordingThread = null;
            showMessage("Запись остановлена!");
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == REQUEST_CODE__RECORD_AUDIO) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startRecording();
                isStartRecording = true;
            } else {
                showMessage("Разрешение на использоование микрофона не получено");
                finish();
            }
        }
    }


    private void showMessage(String text) {
        Toast.makeText(this, text, Toast.LENGTH_SHORT).show();
    }


}
