package com.example.nanosemantics;

import android.Manifest;
import android.app.Service;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.IBinder;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.Nullable;

import com.example.nanosemantics.Response.PartialResult;
import com.example.nanosemantics.Response.TextResult;
import com.google.gson.Gson;

import org.kaldi.Assets;
import org.kaldi.Model;
import org.kaldi.RecognitionListener;
import org.kaldi.SpeechRecognizer;
import org.kaldi.Vosk;

import java.io.File;
import java.io.IOException;
import java.lang.ref.WeakReference;

public class PhraseDetectorService extends Service implements RecognitionListener {
    static {
        System.loadLibrary("kaldi_jni");
    }

    private static final String KEY_WORD = "привет мувик";
    private static final String LOG_TAG = "PhraseDetectorService";

    private SpeechRecognizer recognizer;
    private Gson gson = new Gson();
    private Model model;

    @Override
    public void onCreate() {
        super.onCreate();

        Log.d(LOG_TAG, "onCreate    ");
        new SetupTask(PhraseDetectorService.this).execute();
    }

    private static class SetupTask extends AsyncTask<Void, Void, Exception> {
        WeakReference<PhraseDetectorService> service;

        SetupTask(PhraseDetectorService service) {
            this.service = new WeakReference<PhraseDetectorService>(service);
        }

        protected Exception doInBackground(Void... params) {
            try {
                PhraseDetectorService phraseDetectorService = service.get();

                Assets assets = new Assets(phraseDetectorService);
                File assetDir = assets.syncAssets();

                Vosk.SetLogLevel(0);

                phraseDetectorService.model = new Model(assetDir.toString() + "/model/ru");
                phraseDetectorService.recognizeMicrophone();
            } catch (IOException exc) {
                exc.printStackTrace();
                return exc;
            }
            return null;
        }

        @Override
        protected void onPostExecute(Exception result) {
            PhraseDetectorService phraseDetectorService = service.get();
            if (result == null) {
                phraseDetectorService.showToast("Скажите активационную фразу");
            } else {
                phraseDetectorService.showToast("Произошла ошибка");
            }
        }
    }

    public void recognizeMicrophone() {
        if (recognizer != null) {
            recognizer.cancel();
            recognizer = null;
        } else {
            try {
                recognizer = new SpeechRecognizer(model);
                recognizer.addListener(this);
                recognizer.startListening();
            } catch (IOException exc) {
                exc.printStackTrace();
            }
        }
    }

    @Override
    public void onPartialResult(String result) {
        PartialResult res = gson.fromJson(result, PartialResult.class);
        checkKeyWord(res.getPartial());
    }

    @Override
    public void onResult(String result) {
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
        if (result.contains(KEY_WORD)) {
            recognizeMicrophone();
            showToast("Сработала активационная фраза");
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(LOG_TAG, "onStartCommand");
        return super.onStartCommand(intent, flags, startId);
    }

    private void showToast(String text) {
        Toast.makeText(this, text, Toast.LENGTH_SHORT).show();
    }
}
