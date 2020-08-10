package com.example.nanosemantics;

import android.content.Context;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.kaldi.KaldiRecognizer;
import org.kaldi.Model;
import org.kaldi.RecognitionListener;

import java.io.IOException;
import java.util.Collection;
import java.util.HashSet;

public class SpeechRecognizer {
    protected static final String TAG = SpeechRecognizer.class.getSimpleName();
    private final KaldiRecognizer recognizer;
    private final int sampleRate;
    private static final float BUFFER_SIZE_SECONDS = 0.4F;
    private int bufferSize;
    private final AudioRecord recorder;
    private Thread recognizerThread;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Collection<RecognitionListener> listeners = new HashSet();
MediaRecorder a = new MediaRecorder();

    public SpeechRecognizer(Context context, Model model) throws IOException {
        this.recognizer = new KaldiRecognizer(model, 16000.0F);
        this.sampleRate = 16000;
        this.bufferSize = Math.round((float) this.sampleRate * 0.4F);
        this.recorder = new AudioRecord(6, this.sampleRate, 16, 2, this.bufferSize * 2);

        AudioManager audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        assert audioManager != null;
        AudioDeviceInfo[] adi = audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS);
        for (int i = 0; i < adi.length; i++) {
            AudioDeviceInfo device = adi[i];
            Log.d(TAG, "Device_" + i + " getType: " + device.getType());
            if (device.getType() == AudioDeviceInfo.TYPE_BUILTIN_MIC) {
                if (this.recorder.setPreferredDevice(device)) {
                    Log.d(TAG, "Выбран TYPE_BUILTIN_MIC: " + device.getType());
                    break;
                }
            }
        }

        if (this.recorder.getState() == 0) {
            this.recorder.release();
            throw new IOException("Failed to initialize recorder. Microphone might be already in use.");
        }
    }

    public void addListener(RecognitionListener listener) {
        synchronized (this.listeners) {
            this.listeners.add(listener);
        }
    }

    public void removeListener(RecognitionListener listener) {
        synchronized (this.listeners) {
            this.listeners.remove(listener);
        }
    }

    public boolean startListening() {
        if (null != this.recognizerThread) {
            return false;
        } else {
            this.recognizerThread = new SpeechRecognizer.RecognizerThread();
            this.recognizerThread.start();
            return true;
        }
    }

    public boolean startListening(int timeout) {
        if (null != this.recognizerThread) {
            return false;
        } else {
            this.recognizerThread = new SpeechRecognizer.RecognizerThread(timeout);
            this.recognizerThread.start();
            return true;
        }
    }

    private boolean stopRecognizerThread() {
        if (null == this.recognizerThread) {
            return false;
        } else {
            try {
                this.recognizerThread.interrupt();
                this.recognizerThread.join();
            } catch (InterruptedException var2) {
                Thread.currentThread().interrupt();
            }

            this.recognizerThread = null;
            return true;
        }
    }

    public boolean stop() {
        boolean result = this.stopRecognizerThread();
        if (result) {
            this.mainHandler.post(new SpeechRecognizer.ResultEvent(this.recognizer.Result(), true));
        }

        return result;
    }

    public boolean cancel() {
        boolean result = this.stopRecognizerThread();
        this.recognizer.Result();
        return result;
    }

    public void shutdown() {
        this.recorder.release();
    }

    private class TimeoutEvent extends SpeechRecognizer.RecognitionEvent {

        protected void execute(RecognitionListener listener) {
            listener.onTimeout();
        }
    }

    private class OnErrorEvent extends SpeechRecognizer.RecognitionEvent {
        private final Exception exception;

        OnErrorEvent(Exception exception) {
            this.exception = exception;
        }

        protected void execute(RecognitionListener listener) {
            listener.onError(this.exception);
        }
    }

    private class ResultEvent extends SpeechRecognizer.RecognitionEvent {
        protected final String hypothesis;
        private final boolean finalResult;

        ResultEvent(String hypothesis, boolean finalResult) {
            this.hypothesis = hypothesis;
            this.finalResult = finalResult;
        }

        protected void execute(RecognitionListener listener) {
            if (this.finalResult) {
                listener.onResult(this.hypothesis);
            } else {
                listener.onPartialResult(this.hypothesis);
            }

        }
    }

    private abstract class RecognitionEvent implements Runnable {

        public void run() {
            RecognitionListener[] emptyArray = new RecognitionListener[0];
            RecognitionListener[] var2 = (RecognitionListener[]) SpeechRecognizer.this.listeners.toArray(emptyArray);
            int var3 = var2.length;

            for (int var4 = 0; var4 < var3; ++var4) {
                RecognitionListener listener = var2[var4];
                this.execute(listener);
            }

        }

        protected abstract void execute(RecognitionListener var1);
    }

    private final class RecognizerThread extends Thread {
        private int remainingSamples;
        private int timeoutSamples;
        private static final int NO_TIMEOUT = -1;

        public RecognizerThread(int timeout) {
            if (timeout != -1) {
                this.timeoutSamples = timeout * SpeechRecognizer.this.sampleRate / 1000;
            } else {
                this.timeoutSamples = -1;
            }

            this.remainingSamples = this.timeoutSamples;
        }

        public RecognizerThread() {
            this(-1);
        }

        public void run() {
            SpeechRecognizer.this.recorder.startRecording();
            if (SpeechRecognizer.this.recorder.getRecordingState() == 1) {
                SpeechRecognizer.this.recorder.stop();
                IOException ioe = new IOException("Failed to start recording. Microphone might be already in use.");
                SpeechRecognizer.this.mainHandler.post(SpeechRecognizer.this.new OnErrorEvent(ioe));
            } else {
                short[] buffer = new short[SpeechRecognizer.this.bufferSize];

                while (!interrupted() && (this.timeoutSamples == -1 || this.remainingSamples > 0)) {
                    int nread = SpeechRecognizer.this.recorder.read(buffer, 0, buffer.length);
                    if (nread < 0) {
                        throw new RuntimeException("error reading audio buffer");
                    }

                    boolean isFinal = SpeechRecognizer.this.recognizer.AcceptWaveform(buffer, nread);
                    if (isFinal) {
                        SpeechRecognizer.this.mainHandler.post(SpeechRecognizer.this.new ResultEvent(SpeechRecognizer.this.recognizer.Result(), true));
                    } else {
                        SpeechRecognizer.this.mainHandler.post(SpeechRecognizer.this.new ResultEvent(SpeechRecognizer.this.recognizer.PartialResult(), false));
                    }

                    if (this.timeoutSamples != -1) {
                        this.remainingSamples -= nread;
                    }
                }

                SpeechRecognizer.this.recorder.stop();
                SpeechRecognizer.this.mainHandler.removeCallbacksAndMessages((Object) null);
                if (this.timeoutSamples != -1 && this.remainingSamples <= 0) {
                    SpeechRecognizer.this.mainHandler.post(SpeechRecognizer.this.new TimeoutEvent());
                }

            }
        }
    }
}
