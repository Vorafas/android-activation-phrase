package com.example.phrasedetectordemo;

import android.Manifest;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.IBinder;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.Nullable;

import com.example.phrasedetectordemo.model.PartialResult;
import com.example.phrasedetectordemo.model.TextResult;
import com.google.gson.Gson;
import com.nabinbhandari.android.permissions.PermissionHandler;
import com.nabinbhandari.android.permissions.Permissions;

import org.kaldi.Assets;
import org.kaldi.Model;
import org.kaldi.RecognitionListener;
import org.kaldi.SpeechRecognizer;
import org.kaldi.Vosk;

import java.io.File;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.ArrayList;

public class PhraseDetectorService extends Service implements RecognitionListener {

    private static final String TAG = PhraseDetectorService.class.getSimpleName();
    private static final String KEY_WORD = "привет мувикс";

    private SpeechRecognizer recognizer;
    private Model model;
    private Gson gson = new Gson();

    public void onCreate() {
        super.onCreate();

        Log.d(TAG, "PhraseDetectorService onCreate()");
        Toast.makeText(this, "PhraseDetectorService onCreate()", Toast.LENGTH_SHORT).show();
        Permissions.check(this, Manifest.permission.RECORD_AUDIO, null, new PermissionHandler() {
            @Override
            public void onGranted() {
                new SetupTask(PhraseDetectorService.this).execute();
            }

            @Override
            public void onJustBlocked(Context context, ArrayList<String> justBlockedList,
                                      ArrayList<String> deniedPermissions) {
                new SetupTask(PhraseDetectorService.this).execute();
            }
        });

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
                Log.d(TAG, "Скажите активационную фразу");
                // mainActivity.showToast("Скажите активационную фразу");
            } else {
                Log.d(TAG, "Произошла ошибка");
                // mainActivity.showToast("Произошла ошибка");
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

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return super.onStartCommand(intent, flags, startId);
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
        Log.d(TAG, "Result: " + result);
        if (result.contains(KEY_WORD)) {
            recognizeMicrophone();
            Toast.makeText(this, "Сработала активационная фраза", Toast.LENGTH_SHORT).show();
            // TODO: show message
        }
    }
}