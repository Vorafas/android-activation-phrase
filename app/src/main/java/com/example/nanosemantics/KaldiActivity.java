package com.example.nanosemantics;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;

import com.example.nanosemantics.Response.PartialResult;
import com.example.nanosemantics.Response.TextResult;
import com.google.gson.Gson;

import org.kaldi.Assets;
import org.kaldi.Model;
import org.kaldi.RecognitionListener;
import org.kaldi.Vosk;

import java.io.File;
import java.io.IOException;
import java.lang.ref.WeakReference;

public class KaldiActivity extends Activity implements RecognitionListener {
    static {
        System.loadLibrary("kaldi_jni");
    }

    final String KEY_WORD = "привет мувик";

    private static final int REQUEST_CODE__RECORD_AUDIO = 1;

    private SpeechRecognizer recognizer;
    private Model model;
    private TextView tvInfo;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_kaldi);

        tvInfo = findViewById(R.id.tvInfo);

        int recordAudioPermission = ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO);
        if (recordAudioPermission != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO}, REQUEST_CODE__RECORD_AUDIO);
            return;
        }

        new SetupTask(this).execute();
    }

    private static class SetupTask extends AsyncTask<Void, Void, Exception> {
        WeakReference<KaldiActivity> activity;

        SetupTask(KaldiActivity activity) {
            this.activity = new WeakReference<KaldiActivity>(activity);
        }

        protected Exception doInBackground(Void... params) {
            try {
                KaldiActivity kaldiActivity = activity.get();

                Assets assets = new Assets(kaldiActivity);
                File assetDir = assets.syncAssets();

                Vosk.SetLogLevel(0);

                kaldiActivity.model = new Model(assetDir.toString() + "/model/ru");
                kaldiActivity.recognizeMicrophone();
            } catch (IOException exc) {
                exc.printStackTrace();
                return exc;
            }
            return null;
        }

        @Override
        protected void onPostExecute(Exception result) {
            KaldiActivity kaldiActivity = activity.get();
            if (result == null) {
                kaldiActivity.showToast("Скажите активационную фразу");
            } else {
                kaldiActivity.showToast("Произошла ошибка");
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResult) {
        if (requestCode == REQUEST_CODE__RECORD_AUDIO) {
            if (grantResult.length > 0 && grantResult[0] == PackageManager.PERMISSION_GRANTED) {
                new SetupTask(this).execute();
            } else {
                showToast("Разрешение на использование микрофона не получено");
                finish();
            }
        }
    }

    public void recognizeMicrophone() {
        if (recognizer != null) {
            recognizer.cancel();
            recognizer = null;
        } else {
            try {
                recognizer = new SpeechRecognizer(this, model);
                recognizer.addListener(this);
                recognizer.startListening();
            } catch (IOException exc) {
                exc.printStackTrace();
            }
        }
    }

    private void showToast(String text) {
        Toast.makeText(this, text, Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onPartialResult(String result) {
        Gson gson = new Gson();
        PartialResult res = gson.fromJson(result, PartialResult.class);
        checkKeyWord(res.getPartial());
    }

    @Override
    public void onResult(String result) {
        Gson gson = new Gson();
        TextResult res = gson.fromJson(result, TextResult.class);
        checkKeyWord(res.getText());
    }

    @Override
    public void onError(Exception e) {
    }

    @Override
    public void onTimeout() {
    }

    private void checkKeyWord(String result) {
        tvInfo.setText(result);
        if (result.contains(KEY_WORD)) {
            recognizeMicrophone();
            showToast("Сработала активационная фраза");
            finish();
        }
    }
}
