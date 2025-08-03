/*
 * Copyright (C) 2021 The Android Open Source Project
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
 * limitations under the License.
 */

package android.voicerecognition.cts;

import static android.voicerecognition.cts.TestObjects.ERROR_CODE;
import static android.voicerecognition.cts.TestObjects.LANGUAGE_DETECTION_BUNDLE;
import static android.voicerecognition.cts.TestObjects.PARTIAL_RESULTS_BUNDLE;
import static android.voicerecognition.cts.TestObjects.READY_FOR_SPEECH_BUNDLE;
import static android.voicerecognition.cts.TestObjects.RESULTS_BUNDLE;
import static android.voicerecognition.cts.TestObjects.RMS_CHANGED_VALUE;
import static android.voicerecognition.cts.TestObjects.SEGMENT_RESULTS_BUNDLE;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.fail;

import android.content.AttributionSource;
import android.content.Intent;
import android.os.RemoteException;
import android.speech.ModelDownloadListener;
import android.speech.RecognitionService;
import android.speech.SpeechRecognizer;
import android.util.Log;
import android.util.Pair;

import androidx.annotation.NonNull;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

public class CtsRecognitionService extends RecognitionService {
    private static final String TAG = CtsRecognitionService.class.getSimpleName();

    /** Map of the recognizer methods invoked, indexed by their id. */
    public static Map<Integer, List<RecognizerMethod>> sInvokedRecognizerMethods = new HashMap<>();

    /**
     * Queue of instructions for callbacks on main tasks ({@link
     * CtsRecognitionService#onStartListening}, {@link CtsRecognitionService#onStopListening},
     * {@link CtsRecognitionService#onCancel}). Each instruction is a pair
     * consisting of a recognizer id and the callback to be run on the recognizer's listener.
     */
    public static Queue<Pair<Integer, CallbackMethod>> sInstructedCallbackMethods =
            new ArrayDeque<>();

    /**
     * Queue of instructions for callbacks on the model download task - {@link
     * CtsRecognitionService#onTriggerModelDownload(
     * Intent, AttributionSource, ModelDownloadListener)}.
     *
     * <p> Each instruction represents the callback to be run on the given listener.
     */
    public static Queue<ModelDownloadCallback> sInstructedModelDownloadCallbacks = null;

    public static AtomicBoolean sIsActive = new AtomicBoolean(false);
    public static Queue<Consumer<SupportCallback>> sConsumerQueue = new ArrayDeque<>();
    public static List<Intent> sDownloadTriggers = new ArrayList<>();

    public static AtomicInteger sBindCount = new AtomicInteger(0);

    static final int MAX_CONCURRENT_SESSIONS_COUNT = 3;

    private final Random mRandom = new Random();

    @Override
    protected void onStartListening(Intent recognizerIntent, Callback listener) {
        sIsActive.set(true);
        assertThat(listener.getCallingUid()).isEqualTo(android.os.Process.myUid());

        int recognizerId = processInstructedCallback(listener);
        if (recognizerId >= 0) {
            sInvokedRecognizerMethods.putIfAbsent(recognizerId, new ArrayList<>());
            sInvokedRecognizerMethods.get(recognizerId)
                    .add(RecognizerMethod.RECOGNIZER_METHOD_START_LISTENING);
        }
        sIsActive.set(false);
    }

    @Override
    public void onBindInternal() {
        Log.d(TAG, "onBind");
        sBindCount.incrementAndGet();
    }

    @Override
    public boolean onUnbind(Intent intent) {
        Log.d(TAG, "onUnbind");
        sBindCount.decrementAndGet();
        return super.onUnbind(intent);
    }

    @Override
    protected void onStopListening(Callback listener) {
        sIsActive.set(true);
        assertThat(listener.getCallingUid()).isEqualTo(android.os.Process.myUid());

        int recognizerId = processInstructedCallback(listener);
        if (recognizerId >= 0) {
            sInvokedRecognizerMethods.putIfAbsent(recognizerId, new ArrayList<>());
            sInvokedRecognizerMethods.get(recognizerId)
                    .add(RecognizerMethod.RECOGNIZER_METHOD_STOP_LISTENING);
        }
        sIsActive.set(false);
    }

    @Override
    public void onCheckRecognitionSupport(
            @NonNull Intent recognizerIntent,
            @NonNull AttributionSource attributionSource,
            @NonNull SupportCallback supportCallback) {
        Consumer<SupportCallback> consumer = sConsumerQueue.poll();
        if (consumer == null) {
            supportCallback.onError(SpeechRecognizer.ERROR_CANNOT_CHECK_SUPPORT);
        } else {
            assertThat(attributionSource.getUid()).isEqualTo(android.os.Process.myUid());
            consumer.accept(supportCallback);
        }
    }

    @Override
    public void onTriggerModelDownload(
            @NonNull Intent recognizerIntent,
            @NonNull AttributionSource attributionSource) {
        assertThat(attributionSource.getUid()).isEqualTo(android.os.Process.myUid());
        sDownloadTriggers.add(recognizerIntent);
    }

    @Override
    public void onTriggerModelDownload(
            @NonNull Intent recognizerIntent,
            @NonNull AttributionSource attributionSource,
            @NonNull ModelDownloadListener listener) {
        assertThat(attributionSource.getUid()).isEqualTo(android.os.Process.myUid());

        while (!sInstructedModelDownloadCallbacks.isEmpty()) {
            ModelDownloadCallback callback = sInstructedModelDownloadCallbacks.poll();
            switch (callback) {
                case ON_PROGRESS:
                    listener.onProgress(50);
                    break;
                case ON_SUCCESS:
                    listener.onSuccess();
                    break;
                case ON_SCHEDULED:
                    listener.onScheduled();
                    break;
                case ON_ERROR:
                    listener.onError(0);
                    break;
                default:
                    break;
            }
        }
        sDownloadTriggers.add(recognizerIntent);
    }

    @Override
    protected void onCancel(Callback listener) {
        sIsActive.set(true);
        assertThat(listener.getCallingUid()).isEqualTo(android.os.Process.myUid());

        int recognizerId = processInstructedCallback(listener);
        if (recognizerId >= 0) {
            sInvokedRecognizerMethods.putIfAbsent(recognizerId, new ArrayList<>());
            sInvokedRecognizerMethods.get(recognizerId).add(
                    RecognizerMethod.RECOGNIZER_METHOD_CANCEL);
        }
        sIsActive.set(false);
    }

    @Override
    public int getMaxConcurrentSessionsCount() {
        return MAX_CONCURRENT_SESSIONS_COUNT;
    }

    /**
     * Process the next callback instruction in the queue by callback on the given listener.
     * Return the id of the corresponding recognizer object.
     *
     * @param listener listener on which the callback should be invoked
     * @return the id of the corresponding recognizer
     */
    private int processInstructedCallback(Callback listener) {
        if (sInstructedCallbackMethods.isEmpty()) {
            return -1;
        }

        Pair<Integer, CallbackMethod> callbackInstruction = sInstructedCallbackMethods.poll();
        int recognizerId = callbackInstruction.first;
        CallbackMethod callbackMethod = callbackInstruction.second;

        Log.i(TAG, "Responding with " + callbackMethod.name() + ".");

        try {
            switch (callbackMethod) {
                case CALLBACK_METHOD_UNSPECIFIED:
                    // ignore
                    break;
                case CALLBACK_METHOD_BEGINNING_OF_SPEECH:
                    listener.beginningOfSpeech();
                    break;
                case CALLBACK_METHOD_BUFFER_RECEIVED:
                    byte[] buffer = new byte[100];
                    mRandom.nextBytes(buffer);
                    listener.bufferReceived(buffer);
                    break;
                case CALLBACK_METHOD_END_OF_SPEECH:
                    listener.endOfSpeech();
                    break;
                case CALLBACK_METHOD_ERROR:
                    listener.error(ERROR_CODE);
                    break;
                case CALLBACK_METHOD_RESULTS:
                    listener.results(RESULTS_BUNDLE);
                    break;
                case CALLBACK_METHOD_PARTIAL_RESULTS:
                    listener.partialResults(PARTIAL_RESULTS_BUNDLE);
                    break;
                case CALLBACK_METHOD_READY_FOR_SPEECH:
                    listener.readyForSpeech(READY_FOR_SPEECH_BUNDLE);
                    break;
                case CALLBACK_METHOD_RMS_CHANGED:
                    listener.rmsChanged(RMS_CHANGED_VALUE);
                    break;
                case CALLBACK_METHOD_SEGMENTS_RESULTS:
                    listener.segmentResults(SEGMENT_RESULTS_BUNDLE);
                    break;
                case CALLBACK_METHOD_END_SEGMENTED_SESSION:
                    listener.endOfSegmentedSession();
                    break;
                case CALLBACK_METHOD_LANGUAGE_DETECTION:
                    listener.languageDetection(LANGUAGE_DETECTION_BUNDLE);
                    break;
                default:
                    fail();
            }
        } catch (RemoteException e) {
            fail();
        }

        return recognizerId;
    }

    static int totalInvokedRecognizerMethodsCount() {
        return sInvokedRecognizerMethods.values().stream().mapToInt(List::size).sum();
    }
}
