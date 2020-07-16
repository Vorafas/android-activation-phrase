package com.example.nanosemantics;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;


import java.io.File;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.OkHttpClient.Builder;

import com.example.nanosemantics.Response.PartialResult;
import com.example.nanosemantics.Response.TextResult;
import com.google.gson.Gson;
import com.itkacher.okhttpprofiler.OkHttpProfilerInterceptor;

import org.kaldi.Assets;
import org.kaldi.Model;
import org.kaldi.RecognitionListener;
import org.kaldi.SpeechRecognizer;
import org.kaldi.Vosk;

public class MainActivity extends AppCompatActivity implements RecognitionListener {
    static {
        System.loadLibrary("kaldi_jni");
    }

    final String URL_INIT = "https://biz.nanosemantics.ru/api/bat/nkd/json/Chat.init";
    final String URL_REQUEST = "https://biz.nanosemantics.ru/api/bat/nkd/json/Chat.request";
    final String LOG_TAG = "myLog";
    final String KEY_WORD = "привет мувикс";

    static private final int STATE_START = 0;
    static private final int STATE_READY = 1;
    static private final int STATE_STOP = 2;
    static private final int STATE_ERROR = 3;

    private static final int REQUEST_CODE__RECORD_AUDIO = 1;

    public static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private List<Long> responsesTimes = new ArrayList<Long>();

    private SpeechRecognizer recognizer;
    private Model model;

    OkHttpClient client;

    private Button btnSend;
    private EditText etMsg;
    private TextView tvResult;
    private TextView tvRubric;
    private TextView tvValue;
    private TextView tvStateInfo;
    private TextView tvResponseTime;
    private TextView tvAvgResponseTime;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        btnSend = findViewById(R.id.btnSend);
        etMsg = findViewById(R.id.etMsg);
        tvResult = findViewById(R.id.tvResult);
        tvRubric = findViewById(R.id.tvRubric);
        tvValue = findViewById(R.id.tvValue);
        tvResponseTime = findViewById(R.id.tvResponseTime);
        tvAvgResponseTime = findViewById(R.id.tvAvgResponseTime);
        tvStateInfo = findViewById(R.id.tvStateInfo);

        btnSend.setOnClickListener(this::onClick);
        setUiState(STATE_START);

        int permissionCheck = ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO);
        if (permissionCheck != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO}, REQUEST_CODE__RECORD_AUDIO);
            return;
        }

        new SetupTask(this).execute();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResult) {
        if (requestCode == REQUEST_CODE__RECORD_AUDIO) {
            if (grantResult.length > 0 && grantResult[0] == PackageManager.PERMISSION_GRANTED) {
                new SetupTask(this).execute();
            } else {
                finish();
            }
        }
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
        setUiState(STATE_ERROR);
    }

    @Override
    public void onTimeout() {
        setUiState(STATE_ERROR);
    }

    void checkKeyWord(String result) {
        tvResponseTime.setText(result);
        if (result.contains(KEY_WORD)) {
            recognizeMicrophone();
            showToast("Сработала активационная фраза");
            setUiState(STATE_STOP);
        }
    }

    private static class SetupTask extends AsyncTask<Void, Void, Exception> {
        WeakReference<MainActivity> activity;

        SetupTask(MainActivity activity) {
            this.activity = new WeakReference<MainActivity>(activity);
        }

        protected Exception doInBackground(Void... params) {
            try {
                MainActivity mainActivity = activity.get();

                Assets assets = new Assets(mainActivity);
                File assetDir = assets.syncAssets();
                Log.d(mainActivity.LOG_TAG, "Sync files in the folder " + assetDir.toString());

                Vosk.SetLogLevel(0);

                mainActivity.model = new Model(assetDir.toString() + "/model/ru");
                mainActivity.recognizeMicrophone();
            } catch (IOException exc) {
                exc.printStackTrace();
                return exc;
            }
            return null;
        }

        @Override
        protected void onPostExecute(Exception result) {
            MainActivity mainActivity = activity.get();
            if (result == null) {
                mainActivity.setUiState(STATE_READY);
                mainActivity.showToast("Скажите активационную фразу");
            } else {
                mainActivity.setUiState(STATE_ERROR);
                mainActivity.showToast("Произошла ошибка");
            }
        }
    }

    public void onClick(View v) {
        String text = etMsg.getText().toString();
        try {
            request(text);
        } catch (IOException exc) {
            showToast(exc.toString());
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

    private void setUiState(int state) {
        switch(state) {
            case STATE_START:
                etMsg.setEnabled(false);
                btnSend.setEnabled(false);
                btnSend.setAlpha(0.5F);
                etMsg.setAlpha(0.5F);
                tvStateInfo.setText("Дождитесь загрузки модели");
                break;
            case STATE_READY:
                btnSend.setEnabled(false);
                etMsg.setEnabled(false);
                btnSend.setAlpha(0.5F);
                etMsg.setAlpha(0.5F);
                tvStateInfo.setText("Скажите активационную фразу");
                break;
            case STATE_STOP:
                btnSend.setEnabled(true);
                etMsg.setEnabled(true);
                btnSend.setAlpha(1F);
                etMsg.setAlpha(1F);
                tvStateInfo.setText("Введите текст запроса");
                break;
            case STATE_ERROR:
                etMsg.setEnabled(false);
                btnSend.setEnabled(false);
                btnSend.setAlpha(0.5F);
                etMsg.setAlpha(0.5F);
                tvStateInfo.setText("Произошла ошибка");
        }
    }

    private void request(String text) throws IOException {
        long startTime = System.currentTimeMillis();
        String json = "{\"cuid\": \"7531e7a5-4d5a-4e6e-be16-5fb568240bab\", \"text\": \"" + text + "\"}";

        Builder builder = new Builder();
        builder.addInterceptor(new OkHttpProfilerInterceptor());
        client = builder.build();
        RequestBody body = RequestBody.create(JSON, json);
        Request request = new Request.Builder()
                .addHeader("Content-Type", "application/json")
                .url(URL_REQUEST)
                .post(body)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                e.printStackTrace();
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (response.isSuccessful()) {
                    long elapsedTime = System.currentTimeMillis() - startTime;
                    responsesTimes.add(elapsedTime);
                    double avgResultTime = round(getAvgResponseTime(), 2);

                    String myResponse = response.body().string();

                    Gson gson = new Gson();
                    com.example.nanosemantics.Response.Response res = gson.fromJson(myResponse, com.example.nanosemantics.Response.Response.class);
                    String value = res.getResult().getText().getValue();
                    String rubric = res.getResult().getRubric();

                    MainActivity.this.runOnUiThread(() -> {
                        tvRubric.setText("Рубрика: " + rubric);
                        tvValue.setText("Ответ: " + value);
                        tvResponseTime.setText("Время запроса: " + elapsedTime);
                        tvAvgResponseTime.setText("Среднее время запроса: " + avgResultTime);
                        tvResult.setText(myResponse);
                        setUiState(STATE_READY);
                        recognizeMicrophone();
                    });
                }
            }
        });
    }

    private double getAvgResponseTime() {
        double result = 0;
        for (long item : responsesTimes) {
            result += item;
        }
        return result / responsesTimes.size();
    }

    private void showToast(String text) {
        Toast.makeText(this, text, Toast.LENGTH_SHORT).show();
    }

    public static double round(double value, int places) {
        BigDecimal bd = new BigDecimal(Double.toString(value));
        bd = bd.setScale(places, RoundingMode.HALF_UP);
        return bd.doubleValue();
    }
}
