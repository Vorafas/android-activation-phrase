package com.example.nanosemantics;

import androidx.appcompat.app.AppCompatActivity;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.example.nanosemantics.Response.Context;
import com.google.gson.GsonBuilder;
import com.itkacher.okhttpprofiler.OkHttpProfilerInterceptor;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.OkHttpClient.Builder;
import okhttp3.ResponseBody;

import com.google.gson.Gson;

public class MainActivity extends AppCompatActivity {
    private final String URL_INIT = "https://biz.nanosemantics.ru/api/bat/nkd/json/Chat.init";
    private final String URL_REQUEST = "https://biz.nanosemantics.ru/api/bat/nkd/json/Chat.request";
    public static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private ArrayList<Long> responsesTimes = new ArrayList();

    public final String TAG = "MY_TAG";
    OkHttpClient client;

    private Button btnSend;
    private EditText etMsg;
    private TextView tvResult;
    private TextView tvRubric;
    private TextView tvValue;
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

        btnSend.setOnClickListener(this::onClick);
    }

    public void onClick(View v) {
        String text = etMsg.getText().toString();
        try {
            request(text);
        } catch(IOException exc) {
            showMsg(exc.toString());
        }
    }

    private void request(String text) throws IOException {
        long startTime = System.currentTimeMillis();
        // cuid be87fd04-0888-449a-92b0-265956cc82e3
        // cuid hh.ru  6804ca12-b73c-4f6c-a014-ef11b69ee422
        // cuid от Вани d8f6807c-866a-402f-9a40-e10921d7f0e9
        // cuid от Демо Банк d3c97352-89e0-40f7-a033-3f61a5152043
        // cuid от Демо А 10a5a95f-e512-4727-95ee-62911a00bc1a
        String json = "{\"cuid\": \"7531e7a5-4d5a-4e6e-be16-5fb568240bab\", \"text\": \"" + text + "\"}";
        // String json = "{\"uuid\": \"2abbfcad-cff3-476d-9e6b-bb3cbedef52d\"}";

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

    public void showMsg(String text) {
        Toast.makeText(this, text, Toast.LENGTH_SHORT).show();
    }

    public static double round(double value, int places) {
        BigDecimal bd = new BigDecimal(Double.toString(value));
        bd = bd.setScale(places, RoundingMode.HALF_UP);
        return bd.doubleValue();
    }
}
