/*
 * Copyright (C) 2020 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License
 */

package android.voicerecognition.cts;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.speech.ModelDownloadListener;
import android.speech.RecognitionListener;
import android.speech.RecognitionSupportCallback;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;

/**
 * An activity that uses SpeechRecognition APIs. SpeechRecognition will bind the RecognitionService
 * to provide the voice recognition functions.
 */
public class SpeechRecognitionActivity extends Activity {
    private final String TAG = "SpeechRecognitionActivity";

    private List<RecognizerInfo> mRecognizerInfos;

    private Handler mHandler;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mHandler = new Handler(getMainLooper());
        setContentView(R.layout.main);
    }

    @Override
    protected void onDestroy() {
        destroyAllRecognizers();
        super.onDestroy();
    }

    public void startListeningDefault() {
        startListening(/* index */ 0);
    }

    public void startListening(int index) {
        final Intent recognizerIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, this.getPackageName());
        startListening(recognizerIntent, index);
    }

    public void startListening(Intent intent, int index) {
        mHandler.post(() -> mRecognizerInfos.get(index).mRecognizer.startListening(intent));
    }

    public void stopListeningDefault() {
        stopListening(/* index */ 0);
    }

    public void stopListening(int index) {
        mHandler.post(mRecognizerInfos.get(index).mRecognizer::stopListening);
    }

    public void cancelDefault() {
        cancel(/* index */ 0);
    }

    public void cancel(int index) {
        mHandler.post(mRecognizerInfos.get(index).mRecognizer::cancel);
    }

    public void destroyRecognizerDefault() {
        destroyRecognizer(/* index */ 0);
    }

    public void destroyRecognizer(int index) {
        mHandler.post(mRecognizerInfos.get(index).mRecognizer::destroy);
    }

    /**
     * Destroy any / all recognizers used by this Activity.
     */
    public void destroyAllRecognizers() {
        mHandler.post(() -> {
            if (mRecognizerInfos != null) {
                for (RecognizerInfo recognizerInfo : mRecognizerInfos) {
                    if (recognizerInfo.mRecognizer != null) {
                        recognizerInfo.mRecognizer.destroy();
                    }
                }
                mRecognizerInfos.clear();
            }
        });
    }

    public void checkRecognitionSupportDefault(Intent intent, RecognitionSupportCallback rsc) {
        checkRecognitionSupport(intent, rsc, /* index */ 0);
    }

    public void checkRecognitionSupport(Intent intent, RecognitionSupportCallback rsc, int index) {
        mHandler.post(() -> mRecognizerInfos.get(index).mRecognizer.checkRecognitionSupport(
                intent, Executors.newSingleThreadExecutor(), rsc));
    }

    public void triggerModelDownloadDefault(Intent intent) {
        triggerModelDownload(intent, /* index */ 0);
    }

    public void triggerModelDownload(Intent intent, int index) {
        mHandler.post(() -> mRecognizerInfos.get(index).mRecognizer.triggerModelDownload(intent));
    }

    public void triggerModelDownloadWithListenerDefault(
            Intent intent, ModelDownloadListener listener) {
        triggerModelDownloadWithListener(intent, listener, /* index */ 0);
    }

    public void triggerModelDownloadWithListener(
            Intent intent, ModelDownloadListener listener, int index) {
        mHandler.post(() -> mRecognizerInfos.get(index)
                .mRecognizer.triggerModelDownload(
                        intent,
                        Executors.newSingleThreadExecutor(),
                        listener));
    }

    public void initDefault(boolean onDevice, String customRecognizerComponent) {
        init(onDevice, customRecognizerComponent, /* recognizerCount */ 1);
    }

    public void init(boolean onDevice, String customRecognizerComponent, int recognizerCount) {
        mRecognizerInfos = new ArrayList<>();

        for (int i = 0; i < recognizerCount; i++) {
            mHandler.post(() -> {
                final SpeechRecognizer recognizer;
                if (onDevice) {
                    recognizer = SpeechRecognizer.createOnDeviceTestingSpeechRecognizer(this);
                } else if (customRecognizerComponent != null) {
                    recognizer = SpeechRecognizer.createSpeechRecognizer(this,
                            ComponentName.unflattenFromString(customRecognizerComponent));
                } else {
                    recognizer = SpeechRecognizer.createSpeechRecognizer(this);
                }
                mRecognizerInfos.add(new RecognizerInfo(recognizer));
            });
        }
    }

    RecognizerInfo getRecognizerInfoDefault() {
        return getRecognizerInfo(/* index */ 0);
    }

    RecognizerInfo getRecognizerInfo(int index) {
        return mRecognizerInfos.get(index);
    }

    int getRecognizerCount() {
        return mRecognizerInfos.size();
    }

    /**
     * Data class containing information about a recognizer object used in the activity:
     * <ul>
     *   <li> {@link RecognizerInfo#mRecognizer} - the recognizer object;
     *   <li> {@link RecognizerInfo#mCallbackMethodsInvoked} - list of {@link CallbackMethod}s
     *   invoked on the recognizer's listener;
     *   <li> {@link RecognizerInfo#mStartListeningCalled} - flag denoting
     *   if the recognizer has been used;
     *   <li> {@link RecognizerInfo#mCountDownLatch} - synchronization object used
     *   to emulate waiting on the recognition result.
     */
    static class RecognizerInfo {
        final SpeechRecognizer mRecognizer;
        final List<CallbackMethod> mCallbackMethodsInvoked;
        final List<Integer> mErrorCodesReceived;
        public boolean mStartListeningCalled;
        public CountDownLatch mCountDownLatch;

        RecognizerInfo(SpeechRecognizer recognizer) {
            mRecognizer = recognizer;
            mCallbackMethodsInvoked = new ArrayList<>();
            mErrorCodesReceived = new ArrayList<>();
            mStartListeningCalled = false;
            mCountDownLatch = new CountDownLatch(1);

            mRecognizer.setRecognitionListener(new SpeechRecognizerListener());
        }

        class SpeechRecognizerListener implements RecognitionListener {
            @Override
            public void onReadyForSpeech(Bundle params) {
                mCallbackMethodsInvoked.add(CallbackMethod.CALLBACK_METHOD_READY_FOR_SPEECH);
            }

            @Override
            public void onBeginningOfSpeech() {
                mCallbackMethodsInvoked.add(CallbackMethod.CALLBACK_METHOD_BEGINNING_OF_SPEECH);
            }

            @Override
            public void onRmsChanged(float rmsdB) {
                mCallbackMethodsInvoked.add(CallbackMethod.CALLBACK_METHOD_RMS_CHANGED);
            }

            @Override
            public void onBufferReceived(byte[] buffer) {
                mCallbackMethodsInvoked.add(CallbackMethod.CALLBACK_METHOD_BUFFER_RECEIVED);
            }

            @Override
            public void onEndOfSpeech() {
                mCallbackMethodsInvoked.add(CallbackMethod.CALLBACK_METHOD_END_OF_SPEECH);
            }

            @Override
            public void onError(int error) {
                mCallbackMethodsInvoked.add(CallbackMethod.CALLBACK_METHOD_ERROR);
                mErrorCodesReceived.add(error);
            }

            @Override
            public void onResults(Bundle results) {
                mCallbackMethodsInvoked.add(CallbackMethod.CALLBACK_METHOD_RESULTS);
                mStartListeningCalled = true;
                mCountDownLatch.countDown();
            }

            @Override
            public void onPartialResults(Bundle partialResults) {
                mCallbackMethodsInvoked.add(CallbackMethod.CALLBACK_METHOD_PARTIAL_RESULTS);
            }

            @Override
            public void onSegmentResults(@NonNull Bundle segmentResults) {
                mCallbackMethodsInvoked.add(CallbackMethod.CALLBACK_METHOD_SEGMENTS_RESULTS);
            }

            @Override
            public void onEndOfSegmentedSession() {
                mCallbackMethodsInvoked.add(CallbackMethod.CALLBACK_METHOD_END_SEGMENTED_SESSION);
            }

            @Override
            public void onLanguageDetection(@NonNull Bundle results) {
                mCallbackMethodsInvoked.add(CallbackMethod.CALLBACK_METHOD_LANGUAGE_DETECTION);
            }

            @Override
            public void onEvent(int eventType, Bundle params) {
            }
        }
    }
}
