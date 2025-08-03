/*
 * Copyright (C) 2022 The Android Open Source Project
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

package android.voiceinteraction.cts.services;

import static android.Manifest.permission.BIND_HOTWORD_DETECTION_SERVICE;
import static android.Manifest.permission.BIND_VISUAL_QUERY_DETECTION_SERVICE;
import static android.Manifest.permission.CAPTURE_AUDIO_HOTWORD;
import static android.Manifest.permission.MANAGE_HOTWORD_DETECTION;
import static android.Manifest.permission.RECEIVE_SANDBOX_TRIGGER_AUDIO;
import static android.Manifest.permission.RECORD_AUDIO;
import static android.voiceinteraction.cts.testcore.Helper.WAIT_EXPECTED_NO_CALL_TIMEOUT_IN_MS;
import static android.voiceinteraction.cts.testcore.Helper.WAIT_LONG_TIMEOUT_IN_MS;
import static android.voiceinteraction.cts.testcore.Helper.WAIT_TIMEOUT_IN_MS;

import static com.android.compatibility.common.util.SystemUtil.callWithShellPermissionIdentity;
import static com.android.compatibility.common.util.SystemUtil.runWithShellPermissionIdentity;

import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.PersistableBundle;
import android.permission.flags.Flags;
import android.service.voice.AlwaysOnHotwordDetector;
import android.service.voice.HotwordDetectionService;
import android.service.voice.HotwordDetectionServiceFailure;
import android.service.voice.HotwordDetector;
import android.service.voice.HotwordRejectedResult;
import android.service.voice.SandboxedDetectionInitializer;
import android.service.voice.SoundTriggerFailure;
import android.service.voice.VisualQueryDetectedResult;
import android.service.voice.VisualQueryDetectionService;
import android.service.voice.VisualQueryDetectionServiceFailure;
import android.service.voice.VisualQueryDetector;
import android.service.voice.VoiceInteractionService;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * The {@link VoiceInteractionService} included a basic HotwordDetectionService for testing.
 */
public class CtsBasicVoiceInteractionService extends BaseVoiceInteractionService {
    private static final String TAG = "CtsBasicVoiceInteractionService";

    private final Handler mHandler;

    // The CountDownLatch waits for a service Availability change result
    private CountDownLatch mAvailabilityChangeLatch;
    // The CountDownLatch waits for a service detect or reject result
    private CountDownLatch mOnDetectRejectLatch;
    // The CountDownLatch waits for a service onError called
    private CountDownLatch mOnErrorLatch;
    // The CountDownLatch waits for a service onFailure called
    private CountDownLatch mOnFailureLatch;
    // The CountDownLatch waits for vqds
    private CountDownLatch mOnQueryFinishRejectLatch;
    // The CountDownLatch waits for a service onRecognitionPaused called
    private CountDownLatch mOnRecognitionPausedLatch;
    // The CountDownLatch waits for a service onRecognitionResumed called
    private CountDownLatch mOnRecognitionResumedLatch;
    // The CountDownLatch waits for a service onHotwordDetectionServiceRestarted called
    private CountDownLatch mOnHotwordDetectionServiceRestartedLatch;
    // The CountDownLatch waits for a service onVisualQueryDetectionServiceRestarted called
    private CountDownLatch mOnVisualQueryDetectionServiceRestartedLatch;

    private AlwaysOnHotwordDetector.EventPayload mDetectedResult;
    private HotwordRejectedResult mRejectedResult;
    private ArrayList<String> mStreamedQueries = new ArrayList<>();
    private ArrayList<byte[]> mStreamedAccessibilityData = new ArrayList();
    private String mCurrentQuery = "";
    private byte[] mCurrentAccessibilityData = new byte[0];
    private HotwordDetectionServiceFailure mHotwordDetectionServiceFailure = null;
    private SoundTriggerFailure mSoundTriggerFailure = null;
    private String mUnknownFailure = null;

    private int mSoftwareOnDetectedCount = 0;
    private int mDspOnDetectedCount = 0;
    private int mDspOnRejectedCount = 0;

    private boolean mVoiceActivationPermissionEnabled;

    public CtsBasicVoiceInteractionService() {
        HandlerThread handlerThread = new HandlerThread("CtsBasicVoiceInteractionService");
        handlerThread.start();
        mHandler = Handler.createAsync(handlerThread.getLooper());
    }

    @Override
    public void resetState() {
        super.resetState();
        mAvailabilityChangeLatch = null;
        mOnDetectRejectLatch = null;
        mOnErrorLatch = null;
        mOnFailureLatch = null;
        mOnQueryFinishRejectLatch = null;
        mOnRecognitionPausedLatch = null;
        mOnRecognitionResumedLatch = null;
        mOnHotwordDetectionServiceRestartedLatch = null;
        mDetectedResult = null;
        mRejectedResult = null;
        mStreamedQueries.clear();
        mCurrentQuery = "";
        mHotwordDetectionServiceFailure = null;
        mSoundTriggerFailure = null;
        mUnknownFailure = null;
        mSoftwareOnDetectedCount = 0;
        mDspOnDetectedCount = 0;
        mDspOnRejectedCount = 0;
    }

    /**
     * Returns the onDetected() callback count for the software detector.
     */
    public int getSoftwareOnDetectedCount() {
        return mSoftwareOnDetectedCount;
    }

    /**
     * Returns the onDetected() callback count for the dsp detector.
     */
    public int getDspOnDetectedCount() {
        return mDspOnDetectedCount;
    }

    /**
     * Returns the onRejected() callback count for the dsp detector.
     */
    public int getDspOnRejectedCount() {
        return mDspOnRejectedCount;
    }

    public void createAlwaysOnHotwordDetectorNoHotwordDetectionService(boolean useExecutor,
            boolean runOnMainThread) {
        Log.i(TAG, "createAlwaysOnHotwordDetectorNoHotwordDetectionService");
        mDetectorInitializedLatch = new CountDownLatch(1);

        AlwaysOnHotwordDetector.Callback callback =
                createAlwaysOnHotwordDetectorCallbackWithListeners();
        final Handler handler = runOnMainThread ? new Handler(Looper.getMainLooper()) : mHandler;
        handler.post(() -> runWithShellPermissionIdentity(() -> {
            mAlwaysOnHotwordDetector = callCreateAlwaysOnHotwordDetectorNoHotwordDetectionService(
                    callback, useExecutor);
            if (mDetectorInitializedLatch != null) {
                mDetectorInitializedLatch.countDown();
            }
        }, MANAGE_HOTWORD_DETECTION, RECORD_AUDIO, CAPTURE_AUDIO_HOTWORD));
    }

    /**
     * Create an AlwaysOnHotwordDetector, but it will not implement the onFailure method of
     * AlwaysOnHotwordDetector.Callback. It will implement the onFailure method by using
     * createAlwaysOnHotwordDetectorWithOnFailureCallback method.
     */
    public void createAlwaysOnHotwordDetector() {
        createAlwaysOnHotwordDetector(/* useExecutor= */ false, /* runOnMainThread= */ false);
    }

    /**
     * Create an AlwaysOnHotwordDetector, but it will not implement the onFailure method of
     * AlwaysOnHotwordDetector.Callback. It will implement the onFailure method by using
     * createAlwaysOnHotwordDetectorWithOnFailureCallback method.
     */
    public void createAlwaysOnHotwordDetector(boolean useExecutor, boolean runOnMainThread) {
        createAlwaysOnHotwordDetector(useExecutor, runOnMainThread, /* options= */ null);
    }

    /**
     * Create an AlwaysOnHotwordDetector, but it will not implement the onFailure method of
     * AlwaysOnHotwordDetector.Callback. It will implement the onFailure method by using
     * createAlwaysOnHotwordDetectorWithOnFailureCallback method.
     */
    public void createAlwaysOnHotwordDetector(boolean useExecutor, boolean runOnMainThread,
            @Nullable PersistableBundle options) {
        Log.i(TAG, "createAlwaysOnHotwordDetector!!!!");
        mDetectorInitializedLatch = new CountDownLatch(1);

        final AlwaysOnHotwordDetector.Callback callback = new AlwaysOnHotwordDetector.Callback() {
            @Override
            public void onAvailabilityChanged(int status) {
                Log.i(TAG, "onAvailabilityChanged(" + status + ")");
                mAvailabilityStatus = status;
                setIsDetectorCallbackRunningOnMainThread(isRunningOnMainThread());
                if (mAvailabilityChangeLatch != null) {
                    mAvailabilityChangeLatch.countDown();
                }
            }

            @Override
            public void onDetected(AlwaysOnHotwordDetector.EventPayload eventPayload) {
                Log.i(TAG, "onDetected");
                mDetectedResult = eventPayload;
                mDspOnDetectedCount++;
                setIsDetectorCallbackRunningOnMainThread(isRunningOnMainThread());
                if (mOnDetectRejectLatch != null) {
                    mOnDetectRejectLatch.countDown();
                }
            }

            @Override
            public void onRejected(@NonNull HotwordRejectedResult result) {
                Log.i(TAG, "onRejected");
                mRejectedResult = result;
                mDspOnRejectedCount++;
                setIsDetectorCallbackRunningOnMainThread(isRunningOnMainThread());
                if (mOnDetectRejectLatch != null) {
                    mOnDetectRejectLatch.countDown();
                }
            }

            @Override
            public void onError() {
                Log.i(TAG, "onError");
                setIsDetectorCallbackRunningOnMainThread(isRunningOnMainThread());
                if (mOnErrorLatch != null) {
                    mOnErrorLatch.countDown();
                }
            }

            @Override
            public void onRecognitionPaused() {
                Log.i(TAG, "onRecognitionPaused");
            }

            @Override
            public void onRecognitionResumed() {
                Log.i(TAG, "onRecognitionResumed");
            }

            @Override
            public void onHotwordDetectionServiceInitialized(int status) {
                Log.i(TAG, "onHotwordDetectionServiceInitialized status = " + status);
                mInitializedStatus = status;
                setIsDetectorCallbackRunningOnMainThread(isRunningOnMainThread());
                if (mDetectorInitializedLatch != null) {
                    mDetectorInitializedLatch.countDown();
                }
            }

            @Override
            public void onHotwordDetectionServiceRestarted() {
                Log.i(TAG, "onHotwordDetectionServiceRestarted");
                setIsDetectorCallbackRunningOnMainThread(isRunningOnMainThread());
                if (mOnHotwordDetectionServiceRestartedLatch != null) {
                    mOnHotwordDetectionServiceRestartedLatch.countDown();
                }
            }
        };

        final Handler handler = runOnMainThread ? new Handler(Looper.getMainLooper()) : mHandler;
        handler.post(() -> {
            mAlwaysOnHotwordDetector = callCreateAlwaysOnHotwordDetectorWithNecessaryPerm(callback,
                    useExecutor, options, MANAGE_HOTWORD_DETECTION, RECORD_AUDIO,
                    CAPTURE_AUDIO_HOTWORD);
        });
    }

    /**
     * Create an AlwaysOnHotwordDetector but doesn't hold MANAGE_HOTWORD_DETECTION
     */
    public void createAlwaysOnHotwordDetectorWithoutManageHotwordDetectionPermission() {
        mDetectorInitializedLatch = new CountDownLatch(1);
        mHandler.post(() -> callCreateAlwaysOnHotwordDetectorWithNecessaryPerm(
                mNoOpHotwordDetectorCallback, /* useExecutor= */ false, null, RECORD_AUDIO,
                CAPTURE_AUDIO_HOTWORD));
    }

    /**
     * Create a SoftwareHotwordDetector but doesn't hold MANAGE_HOTWORD_DETECTION
     */
    public void createSoftwareHotwordDetectorWithoutManageHotwordDetectionPermission() {
        mDetectorInitializedLatch = new CountDownLatch(1);
        mHandler.post(() -> callCreateSoftwareDetectorWithNecessaryPerm(
                mNoOpSoftwareDetectorCallback, /* useExecutor= */ false,
                null, CAPTURE_AUDIO_HOTWORD));
    }

    /**
     * Create an SoftwareHotwordDetector holds MANAGE_HOTWORD_DETECTION and
     * BIND_HOTWORD_DETECTION_SERVICE. The client should have MANAGE_HOTWORD_DETECTION to make the
     * API call to the system to do BIND_HOTWORD_DETECTION_SERVICE permission checking.
     */
    public void createSoftwareHotwordDetectorHoldBindHotwordDetectionPermission() {
        mDetectorInitializedLatch = new CountDownLatch(1);
        mHandler.post(() ->   callCreateSoftwareDetectorWithNecessaryPerm(
                mNoOpSoftwareDetectorCallback, /* useExecutor= */ false, null,
                MANAGE_HOTWORD_DETECTION, BIND_HOTWORD_DETECTION_SERVICE));
    }

    /**
     * Create an AlwaysOnHotwordDetector holds MANAGE_HOTWORD_DETECTION and
     * BIND_HOTWORD_DETECTION_SERVICE. The client should have MANAGE_HOTWORD_DETECTION to make the
     * API call to the system to do BIND_HOTWORD_DETECTION_SERVICE permission checking.
     */
    public void createAlwaysOnHotwordDetectorHoldBindHotwordDetectionPermission() {
        mDetectorInitializedLatch = new CountDownLatch(1);
        mHandler.post(() ->
                callCreateAlwaysOnHotwordDetectorWithNecessaryPerm(
                        mNoOpHotwordDetectorCallback, /* useExecutor= */ false, null,
                        RECORD_AUDIO, CAPTURE_AUDIO_HOTWORD, MANAGE_HOTWORD_DETECTION,
                        BIND_HOTWORD_DETECTION_SERVICE));
    }

    /**
     * Create an AlwaysOnHotwordDetector with onFailure callback. The onFailure provides the error
     * code, error message and suggested action the assistant application should take.
     */
    public void createAlwaysOnHotwordDetectorWithOnFailureCallback(boolean useExecutor,
            boolean runOnMainThread) {
        createAlwaysOnHotwordDetectorWithOnFailureCallback(useExecutor, runOnMainThread,
                null /* options */);
    }

    /**
     * Create an AlwaysOnHotwordDetector with onFailure callback. The onFailure provides the error
     * code, error message and suggested action the assistant application should take.
     */
    public void createAlwaysOnHotwordDetectorWithOnFailureCallback(boolean useExecutor,
            boolean runOnMainThread, @Nullable PersistableBundle options) {
        Log.i(TAG, "createAlwaysOnHotwordDetectorWithOnFailureCallback");
        mDetectorInitializedLatch = new CountDownLatch(1);

        final Handler handler = runOnMainThread ? new Handler(Looper.getMainLooper()) : mHandler;
        handler.post(() -> {
            mAlwaysOnHotwordDetector =
                callCreateAlwaysOnHotwordDetectorWithNecessaryPerm(
                        createAlwaysOnHotwordDetectorCallbackWithListeners(), useExecutor,
                        options, MANAGE_HOTWORD_DETECTION, RECORD_AUDIO, CAPTURE_AUDIO_HOTWORD);
        });
    }

    /**
     * Creates a callback which is set up to listen and log all events.
     */
    public AlwaysOnHotwordDetector.Callback createAlwaysOnHotwordDetectorCallbackWithListeners() {
        return new AlwaysOnHotwordDetector.Callback() {
            @Override
            public void onAvailabilityChanged(int status) {
                Log.i(TAG, "onAvailabilityChanged(" + status + ")");
                mAvailabilityStatus = status;
                setIsDetectorCallbackRunningOnMainThread(isRunningOnMainThread());
                if (mAvailabilityChangeLatch != null) {
                    mAvailabilityChangeLatch.countDown();
                }
            }

            @Override
            public void onDetected(AlwaysOnHotwordDetector.EventPayload eventPayload) {
                Log.i(TAG, "onDetected");
                mDetectedResult = eventPayload;
                mDspOnDetectedCount++;
                setIsDetectorCallbackRunningOnMainThread(isRunningOnMainThread());
                if (mOnDetectRejectLatch != null) {
                    mOnDetectRejectLatch.countDown();
                }
            }

            @Override
            public void onRejected(@NonNull HotwordRejectedResult result) {
                Log.i(TAG, "onRejected");
                mRejectedResult = result;
                mDspOnRejectedCount++;
                setIsDetectorCallbackRunningOnMainThread(isRunningOnMainThread());
                if (mOnDetectRejectLatch != null) {
                    mOnDetectRejectLatch.countDown();
                }
            }

            @Override
            public void onError() {
                Log.i(TAG, "onError");
            }

            @Override
            public void onFailure(HotwordDetectionServiceFailure hotwordDetectionServiceFailure) {
                Log.i(TAG, "onFailure hotwordDetectionServiceFailure="
                        + hotwordDetectionServiceFailure);
                mHotwordDetectionServiceFailure = hotwordDetectionServiceFailure;
                setIsDetectorCallbackRunningOnMainThread(isRunningOnMainThread());
                if (mOnFailureLatch != null) {
                    mOnFailureLatch.countDown();
                }
            }

            @Override
            public void onFailure(SoundTriggerFailure soundTriggerFailure) {
                Log.i(TAG, "onFailure soundTriggerFailure=" + soundTriggerFailure);
                mSoundTriggerFailure = soundTriggerFailure;
                setIsDetectorCallbackRunningOnMainThread(isRunningOnMainThread());
                if (mOnFailureLatch != null) {
                    mOnFailureLatch.countDown();
                }
            }

            @Override
            public void onUnknownFailure(String errorMessage) {
                Log.i(TAG, "onUnknownFailure errorMessage=" + errorMessage);
                mUnknownFailure = errorMessage;
                setIsDetectorCallbackRunningOnMainThread(isRunningOnMainThread());
                if (mOnFailureLatch != null) {
                    mOnFailureLatch.countDown();
                }
            }

            @Override
            public void onRecognitionPaused() {
                Log.i(TAG, "onRecognitionPaused");
                if (mOnRecognitionPausedLatch != null) {
                    mOnRecognitionPausedLatch.countDown();
                }
            }

            @Override
            public void onRecognitionResumed() {
                Log.i(TAG, "onRecognitionResumed");
                if (mOnRecognitionResumedLatch != null) {
                    mOnRecognitionResumedLatch.countDown();
                }
            }

            @Override
            public void onHotwordDetectionServiceInitialized(int status) {
                Log.i(TAG, "onHotwordDetectionServiceInitialized status = " + status);
                mInitializedStatus = status;
                setIsDetectorCallbackRunningOnMainThread(isRunningOnMainThread());
                if (mDetectorInitializedLatch != null) {
                    mDetectorInitializedLatch.countDown();
                }
            }

            @Override
            public void onHotwordDetectionServiceRestarted() {
                Log.i(TAG, "onHotwordDetectionServiceRestarted");
                setIsDetectorCallbackRunningOnMainThread(isRunningOnMainThread());
                if (mOnHotwordDetectionServiceRestartedLatch != null) {
                    mOnHotwordDetectionServiceRestartedLatch.countDown();
                }
            }
        };
    }

    /**
     * Create a SoftwareHotwordDetector, but it will not implement the onFailure method of
     * HotwordDetector.Callback. It will implement the onFailure method by using
     * createSoftwareHotwordDetectorWithOnFailureCallback method.
     */
    public void createSoftwareHotwordDetector() {
        createSoftwareHotwordDetector(/* useExecutor= */ false, /* runOnMainThread= */ false);
    }

    /**
     * Create a SoftwareHotwordDetector, but it will not implement the onFailure method of
     * HotwordDetector.Callback. It will implement the onFailure method by using
     * createSoftwareHotwordDetectorWithOnFailureCallback method.
     */
    public void createSoftwareHotwordDetector(boolean useExecutor, boolean runOnMainThread) {
        createSoftwareHotwordDetector(useExecutor, runOnMainThread, /* options= */ null);
    }

    /**
     * Create a SoftwareHotwordDetector, but it will not implement the onFailure method of
     * HotwordDetector.Callback. It will implement the onFailure method by using
     * createSoftwareHotwordDetectorWithOnFailureCallback method.
     */
    public void createSoftwareHotwordDetector(boolean useExecutor, boolean runOnMainThread,
            @Nullable PersistableBundle options) {
        mDetectorInitializedLatch = new CountDownLatch(1);

        final HotwordDetector.Callback callback = new HotwordDetector.Callback() {
            @Override
            public void onDetected(AlwaysOnHotwordDetector.EventPayload eventPayload) {
                Log.i(TAG, "onDetected");
                mDetectedResult = eventPayload;
                mSoftwareOnDetectedCount++;
                setIsDetectorCallbackRunningOnMainThread(isRunningOnMainThread());
                if (mOnDetectRejectLatch != null) {
                    mOnDetectRejectLatch.countDown();
                }
            }

            @Override
            public void onError() {
                Log.i(TAG, "onError");
                setIsDetectorCallbackRunningOnMainThread(isRunningOnMainThread());
                if (mOnErrorLatch != null) {
                    mOnErrorLatch.countDown();
                }
            }

            @Override
            public void onRecognitionPaused() {
                Log.i(TAG, "onRecognitionPaused");
            }

            @Override
            public void onRecognitionResumed() {
                Log.i(TAG, "onRecognitionResumed");
            }

            @Override
            public void onRejected(HotwordRejectedResult result) {
                Log.i(TAG, "onRejected");
                mRejectedResult = result;
                setIsDetectorCallbackRunningOnMainThread(isRunningOnMainThread());
                if (mOnDetectRejectLatch != null) {
                    mOnDetectRejectLatch.countDown();
                }
            }

            @Override
            public void onHotwordDetectionServiceInitialized(int status) {
                Log.i(TAG, "onHotwordDetectionServiceInitialized status = " + status);
                mInitializedStatus = status;
                setIsDetectorCallbackRunningOnMainThread(isRunningOnMainThread());
                if (mDetectorInitializedLatch != null) {
                    mDetectorInitializedLatch.countDown();
                }
            }

            @Override
            public void onHotwordDetectionServiceRestarted() {
                Log.i(TAG, "onHotwordDetectionServiceRestarted");
                setIsDetectorCallbackRunningOnMainThread(isRunningOnMainThread());
                if (mOnHotwordDetectionServiceRestartedLatch != null) {
                    mOnHotwordDetectionServiceRestartedLatch.countDown();
                }
            }
        };

        final Handler handler = runOnMainThread ? new Handler(Looper.getMainLooper()) : mHandler;
        handler.post(() -> {
            mSoftwareHotwordDetector =
                callCreateSoftwareDetectorWithNecessaryPerm(callback, useExecutor, options,
                        MANAGE_HOTWORD_DETECTION);
        });
    }

    /**
     * Create a SoftwareHotwordDetector with onFailure callback. The onFailure provides the error
     * code, error message and suggested action the assistant application should take.
     */
    public void createSoftwareHotwordDetectorWithOnFailureCallback(boolean useExecutor,
            boolean runOnMainThread) {
        mDetectorInitializedLatch = new CountDownLatch(1);

        final HotwordDetector.Callback callback = new HotwordDetector.Callback() {
            @Override
            public void onDetected(AlwaysOnHotwordDetector.EventPayload eventPayload) {
                Log.i(TAG, "onDetected");
                mDetectedResult = eventPayload;
                setIsDetectorCallbackRunningOnMainThread(isRunningOnMainThread());
                if (mOnDetectRejectLatch != null) {
                    mOnDetectRejectLatch.countDown();
                }
            }

            @Override
            public void onError() {
                Log.i(TAG, "onError");
            }

            @Override
            public void onFailure(HotwordDetectionServiceFailure hotwordDetectionServiceFailure) {
                Log.i(TAG, "onFailure hotwordDetectionServiceFailure="
                        + hotwordDetectionServiceFailure);
                mHotwordDetectionServiceFailure = hotwordDetectionServiceFailure;
                setIsDetectorCallbackRunningOnMainThread(isRunningOnMainThread());
                if (mOnFailureLatch != null) {
                    mOnFailureLatch.countDown();
                }
            }

            @Override
            public void onUnknownFailure(String errorMessage) {
                Log.i(TAG, "onUnknownFailure errorMessage=" + errorMessage);
                mUnknownFailure = errorMessage;
                setIsDetectorCallbackRunningOnMainThread(isRunningOnMainThread());
                if (mOnFailureLatch != null) {
                    mOnFailureLatch.countDown();
                }
            }

            @Override
            public void onRecognitionPaused() {
                Log.i(TAG, "onRecognitionPaused");
            }

            @Override
            public void onRecognitionResumed() {
                Log.i(TAG, "onRecognitionResumed");
            }

            @Override
            public void onRejected(HotwordRejectedResult result) {
                Log.i(TAG, "onRejected");
                mRejectedResult = result;
                setIsDetectorCallbackRunningOnMainThread(isRunningOnMainThread());
                if (mOnDetectRejectLatch != null) {
                    mOnDetectRejectLatch.countDown();
                }
            }

            @Override
            public void onHotwordDetectionServiceInitialized(int status) {
                Log.i(TAG, "onHotwordDetectionServiceInitialized status = " + status);
                mInitializedStatus = status;
                setIsDetectorCallbackRunningOnMainThread(isRunningOnMainThread());
                if (mDetectorInitializedLatch != null) {
                    mDetectorInitializedLatch.countDown();
                }
            }

            @Override
            public void onHotwordDetectionServiceRestarted() {
                Log.i(TAG, "onHotwordDetectionServiceRestarted");
                setIsDetectorCallbackRunningOnMainThread(isRunningOnMainThread());
                if (mOnHotwordDetectionServiceRestartedLatch != null) {
                    mOnHotwordDetectionServiceRestartedLatch.countDown();
                }
            }
        };

        final Handler handler = runOnMainThread ? new Handler(Looper.getMainLooper()) : mHandler;
        handler.post(() -> {
            mSoftwareHotwordDetector = callCreateSoftwareDetectorWithNecessaryPerm(callback,
                    useExecutor, null, MANAGE_HOTWORD_DETECTION);
        });
    }

    /**
     * Create a VisualQueryDetector but doesn't hold MANAGE_HOTWORD_DETECTION
     */
    public void createVisualQueryDetectorWithoutManageHotwordDetectionPermission() {
        mDetectorInitializedLatch = new CountDownLatch(1);
        mHandler.post(() -> runWithShellPermissionIdentity(
                () -> callCreateVisualQueryDetector(mNoOpVisualQueryDetectorCallback)));
    }

    /**
     * Create a VisualQueryDetector but doesn't hold MANAGE_HOTWORD_DETECTION but hold
     * BIND_VISUAL_QUERY_DETECTION_SERVICE.
     */
    public void createVisualQueryDetectorHoldBindVisualQueryDetectionPermission() {
        mDetectorInitializedLatch = new CountDownLatch(1);
        mHandler.post(() -> runWithShellPermissionIdentity(
                () -> callCreateVisualQueryDetector(mNoOpVisualQueryDetectorCallback),
                BIND_VISUAL_QUERY_DETECTION_SERVICE));
    }

    public void createVisualQueryDetector() {
        mDetectorInitializedLatch = new CountDownLatch(1);
        mHandler.post(() -> runWithShellPermissionIdentity(() -> {
            VisualQueryDetector.Callback callback = new VisualQueryDetector.Callback() {
                @Override
                public void onQueryDetected(@NonNull String transcribedText) {
                    Log.i(TAG, "onQueryDetected");
                    mCurrentQuery += transcribedText;
                }

                @Override
                public void onQueryDetected(@NonNull VisualQueryDetectedResult partialResult) {
                    Log.i(TAG, "onQueryDetected with VisualQueryDetectedResult");
                    mCurrentQuery += partialResult.getPartialQuery();
                    mCurrentAccessibilityData = accumulateAccessibilityStreamedData(
                            mCurrentAccessibilityData,
                            partialResult.getAccessibilityDetectionData());
                }

                private byte[] accumulateAccessibilityStreamedData(byte[] streamedData,
                        byte[] newData) {
                    byte[] newStreamedData = new byte[streamedData.length + newData.length];
                    System.arraycopy(streamedData, 0, newStreamedData, 0,
                            streamedData.length);
                    System.arraycopy(newData, 0, newStreamedData, streamedData.length,
                            newData.length);
                    return newStreamedData;
                }

                @Override
                public void onQueryRejected() {
                    Log.i(TAG, "onQueryRejected");
                    // mStreamedQueries are used to store previously streamed queries for testing
                    // reason, regardless of the queries being rejected or finished.
                    mStreamedQueries.add(mCurrentQuery);
                    mCurrentQuery = "";
                    mCurrentAccessibilityData = new byte[0];
                    if (mOnQueryFinishRejectLatch != null) {
                        mOnQueryFinishRejectLatch.countDown();
                    }
                }

                @Override
                public void onQueryFinished() {
                    Log.i(TAG, "onQueryFinished");
                    mStreamedQueries.add(mCurrentQuery);
                    mStreamedAccessibilityData.add(mCurrentAccessibilityData);
                    mCurrentQuery = "";
                    mCurrentAccessibilityData = new byte[0];
                    if (mOnQueryFinishRejectLatch != null) {
                        mOnQueryFinishRejectLatch.countDown();
                    }
                }

                @Override
                public void onVisualQueryDetectionServiceInitialized(int status) {
                    Log.i(TAG, "onVisualQueryDetectionServiceInitialized status = " + status);
                    if (status != SandboxedDetectionInitializer.INITIALIZATION_STATUS_SUCCESS) {
                        return;
                    }
                    mInitializedStatus = status;
                    if (mDetectorInitializedLatch != null) {
                        mDetectorInitializedLatch.countDown();
                    }
                }

                @Override
                public void onVisualQueryDetectionServiceRestarted() {
                    Log.i(TAG, "onVisualQueryDetectionServiceRestarted");
                    setIsDetectorCallbackRunningOnMainThread(isRunningOnMainThread());
                    if (mOnVisualQueryDetectionServiceRestartedLatch != null) {
                        mOnVisualQueryDetectionServiceRestartedLatch.countDown();
                    }
                }

                @Override
                public void onFailure(
                        VisualQueryDetectionServiceFailure visualQueryDetectionServiceFailure) {
                    Log.i(TAG, "onFailure visualQueryDetectionServiceFailure: "
                            + visualQueryDetectionServiceFailure);
                    if (mOnFailureLatch != null) {
                        mOnFailureLatch.countDown();
                    }
                }

                @Override
                public void onUnknownFailure(String errorMessage) {
                    Log.i(TAG, "onUnknownFailure errorMessage: " + errorMessage);
                    if (mOnFailureLatch != null) {
                        mOnFailureLatch.countDown();
                    }
                }
            };
            mVisualQueryDetector = callCreateVisualQueryDetector(callback);
        }, MANAGE_HOTWORD_DETECTION));
    }

    /**
     * Create a CountDownLatch that is used to wait for a HotwordDetector to be fully initialized
     * after calling create.
     *
     * <p>For detector create requests which do not use an isolated service like
     * {@link HotwordDetectionService} or {@link VisualQueryDetectionService}, this latch
     * is counted down as soon as the create detector API returns.
     *
     * <p>For detector create requests which use {@link HotwordDetectionService}, this latch is
     * counted down when {@link HotwordDetector.Callback#onHotwordDetectionServiceInitialized} is
     * received.
     *
     * <p>For detector create requests which use {@link VisualQueryDetectionService}, this latch is
     * counted down when
     * {@link VisualQueryDetector.Callback#onVisualQueryDetectionServiceInitialized} is received.
     */
    public void initDetectorInitializedLatch() {
        mDetectorInitializedLatch = new CountDownLatch(1);
    }

    /**
     * Create a CountDownLatch that is used to wait for onHotwordDetectionServiceRestarted() called
     */
    public void initOnHotwordDetectionServiceRestartedLatch() {
        mOnHotwordDetectionServiceRestartedLatch = new CountDownLatch(1);
    }

    public void initOnVisualQueryDetectionServiceRestartedLatch() {
        mOnVisualQueryDetectionServiceRestartedLatch = new CountDownLatch(1);
    }

    /**
     * Create a CountDownLatch that is used to wait for availability change
     */
    public void initAvailabilityChangeLatch() {
        mAvailabilityChangeLatch = new CountDownLatch(1);
    }

    /**
     * Create a CountDownLatch that is used to wait for onDetected() or onRejected() result
     */
    public void initDetectRejectLatch() {
        mOnDetectRejectLatch = new CountDownLatch(1);
    }

    /**
     * Create a CountDownLatch that wait for onQueryFinished() or onQueryRejected() result
     */
    public void initQueryFinishRejectLatch(int numQueries) {
        mOnQueryFinishRejectLatch = new CountDownLatch(numQueries);
    }

    /**
     * Create a CountDownLatch that is used to wait for onError()
     */
    public void initOnErrorLatch() {
        Log.d(TAG, "initOnErrorLatch()");
        mOnErrorLatch = new CountDownLatch(1);
    }

    /**
     * Create a CountDownLatch that is used to wait for onFailure()
     */
    public void initOnFailureLatch() {
        mOnFailureLatch = new CountDownLatch(1);
    }

    public boolean isOnFailureLatchOpen() {
        return mOnFailureLatch != null && mOnFailureLatch.getCount() > 0;
    }

    /**
     * Create a CountDownLatch that is used to wait for onRecognitionPaused()
     */
    public void initOnRecognitionPausedLatch() {
        mOnRecognitionPausedLatch = new CountDownLatch(1);
    }

    /**
     * Create a CountDownLatch that is used to wait for OnRecognitionResumed()
     */
    public void initOnRecognitionResumedLatch() {
        mOnRecognitionResumedLatch = new CountDownLatch(1);
    }

    /**
     * Returns the onDetected() result.
     */
    public AlwaysOnHotwordDetector.EventPayload getHotwordServiceOnDetectedResult() {
        return mDetectedResult;
    }

    /**
     * Returns the OnRejected() result.
     */
    public HotwordRejectedResult getHotwordServiceOnRejectedResult() {
        return mRejectedResult;
    }

    /**
     * Resets the onDetected() and OnRejected() result.
     */
    public void resetHotwordServiceOnDetectedAndOnRejectedResult() {
        mDetectedResult = null;
        mRejectedResult = null;
        mDspOnDetectedCount = 0;
        mDspOnRejectedCount = 0;
        mSoftwareOnDetectedCount = 0;
    }

    /**
     * Returns the OnQueryDetected() result.
     */
    public ArrayList<String> getStreamedQueriesResult() {
        return mStreamedQueries;
    }

    /**
     * Returns the OnQueryDetected() result.
     */
    public ArrayList<byte[]> getAccessibilityDataResult() {
        return mStreamedAccessibilityData;
    }

    /**
     * Wait for onHotwordDetectionServiceRestarted() callback called.
     */
    public void waitOnHotwordDetectionServiceRestartedCalled() throws InterruptedException {
        Log.d(TAG, "waitOnHotwordDetectionServiceRestartedCalled(), latch="
                + mOnHotwordDetectionServiceRestartedLatch);
        if (mOnHotwordDetectionServiceRestartedLatch == null
                || !mOnHotwordDetectionServiceRestartedLatch.await(WAIT_LONG_TIMEOUT_IN_MS,
                TimeUnit.MILLISECONDS)) {
            mOnHotwordDetectionServiceRestartedLatch = null;
            throw new AssertionError(
                    "HotwordDetectionService onHotwordDetectionServiceRestarted not called.");
        }
        mOnHotwordDetectionServiceRestartedLatch = null;
    }

    public void waitOnVisualQueryDetectionServiceRestartedCalled() throws InterruptedException {
        Log.d(TAG, "waitOnVisualQueryDetectionServiceRestartedCalled(), latch="
                + mOnVisualQueryDetectionServiceRestartedLatch);
        if (mOnVisualQueryDetectionServiceRestartedLatch == null
                || !mOnVisualQueryDetectionServiceRestartedLatch.await(WAIT_TIMEOUT_IN_MS,
                TimeUnit.MILLISECONDS)) {
            mOnVisualQueryDetectionServiceRestartedLatch = null;
            throw new AssertionError(
                "VisualQueryDetectionService onVisualQueryDetectionServiceRestarted not called.");
        }
        mOnVisualQueryDetectionServiceRestartedLatch = null;
    }


    /**
     * Returns the OnFailure() with HotwordDetectionServiceFailure result.
     */
    public HotwordDetectionServiceFailure getHotwordDetectionServiceFailure() {
        return mHotwordDetectionServiceFailure;
    }

    /**
     * Returns the OnFailure() with SoundTriggerFailure result.
     */
    public SoundTriggerFailure getSoundTriggerFailure() {
        return mSoundTriggerFailure;
    }

    /**
     * Returns the onUnknownFailure() with error message.
     */
    public String getUnknownFailure() {
        return mUnknownFailure;
    }

    /**
     * Wait for onAvailabilityChanged() callback called.
     */
    public void waitAvailabilityChangedCalled() throws InterruptedException {
        Log.d(TAG, "waitAvailabilityChangedCalled(), latch=" + mAvailabilityChangeLatch);
        if (mAvailabilityChangeLatch == null
                || !mAvailabilityChangeLatch.await(WAIT_TIMEOUT_IN_MS, TimeUnit.MILLISECONDS)) {
            mAvailabilityChangeLatch = null;
            throw new AssertionError("HotwordDetectionService get ability fail.");
        }
        mAvailabilityChangeLatch = null;
    }

    /**
     * Return the result for onAvailabilityChanged().
     */
    public int getHotwordDetectionServiceAvailabilityResult() {
        return mAvailabilityStatus;
    }

    /**
     * Wait for onDetected() or OnRejected() callback called.
     */
    public void waitOnDetectOrRejectCalled() throws InterruptedException {
        Log.d(TAG, "waitOnDetectOrRejectCalled(), latch=" + mOnDetectRejectLatch);
        if (mOnDetectRejectLatch == null
                || !mOnDetectRejectLatch.await(WAIT_TIMEOUT_IN_MS, TimeUnit.MILLISECONDS)) {
            mOnDetectRejectLatch = null;
            throw new AssertionError("onDetected or OnRejected() fail.");
        }
        mOnDetectRejectLatch = null;
    }

    /**
     * Wait for onQueryFinished() or OnQueryRejected() callback called.
     */
    public void waitOnQueryFinishedRejectCalled() throws InterruptedException {
        Log.d(TAG, "waitOnQueryFinishedRejectCalled(), latch=" + mOnQueryFinishRejectLatch);
        if (mOnQueryFinishRejectLatch == null
                || !mOnQueryFinishRejectLatch.await(WAIT_TIMEOUT_IN_MS, TimeUnit.MILLISECONDS)) {
            mOnQueryFinishRejectLatch = null;
            throw new AssertionError("onDetected or OnRejected() fail.");
        }
        mOnQueryFinishRejectLatch = null;
    }

    /**
     * Wait for onError() callback called.
     */
    public void waitOnErrorCalled() throws InterruptedException {
        Log.d(TAG, "waitOnErrorCalled(), latch=" + mOnErrorLatch);
        if (mOnErrorLatch == null
                || !mOnErrorLatch.await(WAIT_TIMEOUT_IN_MS, TimeUnit.MILLISECONDS)) {
            mOnErrorLatch = null;
            throw new AssertionError("OnError() fail.");
        }
        mOnErrorLatch = null;
    }

    /**
     * Wait for onFailure() callback called.
     */
    public void waitOnFailureCalled() throws InterruptedException {
        Log.d(TAG, "waitOnFailureCalled(), latch=" + mOnFailureLatch);
        if (mOnFailureLatch == null
                || !mOnFailureLatch.await(WAIT_TIMEOUT_IN_MS, TimeUnit.MILLISECONDS)) {
            mOnFailureLatch = null;
            throw new AssertionError("OnFailure() fail.");
        }
        mOnFailureLatch = null;
    }

    /**
     * Wait for onRecognitionPaused() callback called.
     */
    public void waitOnRecognitionPausedCalled() throws InterruptedException {
        Log.d(TAG, "waitOnRecognitionPausedCalled(), latch=" + mOnRecognitionPausedLatch);
        if (mOnRecognitionPausedLatch == null
                || !mOnRecognitionPausedLatch.await(WAIT_TIMEOUT_IN_MS, TimeUnit.MILLISECONDS)) {
            mOnRecognitionPausedLatch = null;
            throw new AssertionError("onRecognitionPaused() fail.");
        }
        mOnRecognitionPausedLatch = null;
    }

    /**
     * Wait for onRecognitionResumed() callback called.
     */
    public void waitOnRecognitionResumedCalled() throws InterruptedException {
        Log.d(TAG, "waitOnRecognitionResumedCalled(), latch=" + mOnRecognitionResumedLatch);
        if (mOnRecognitionResumedLatch == null
                || !mOnRecognitionResumedLatch.await(WAIT_TIMEOUT_IN_MS, TimeUnit.MILLISECONDS)) {
            mOnRecognitionResumedLatch = null;
            throw new AssertionError("onRecognitionResumed() fail.");
        }
        mOnRecognitionResumedLatch = null;
    }

    /**
     * Wait for no onRecognitionPaused() callback called.
     */
    public boolean waitNoOnRecognitionPausedCalled() throws InterruptedException {
        Log.d(TAG, "waitNoOnRecognitionPausedCalled(), latch=" + mOnRecognitionPausedLatch);
        if (mOnRecognitionPausedLatch == null) {
            throw new AssertionError("mOnRecognitionPausedLatch is not initialized.");
        }
        boolean result = mOnRecognitionPausedLatch.await(WAIT_EXPECTED_NO_CALL_TIMEOUT_IN_MS,
                TimeUnit.MILLISECONDS);
        mOnRecognitionPausedLatch = null;
        return !result;
    }

    public void setVoiceActivationPermissionEnabled(boolean val) {
        mVoiceActivationPermissionEnabled = val;
    }

    /**
     * Creates always on hotword detector, appending RECEIVE_SANDBOX_TRIGGER_AUDIO permission to
     * requested permissions when the relevant flag is enabled.
     *
     * <p> <b>Context:</b>  A new permission (RECEIVE_SANDBOX_TRIGGER_AUDIO) has been added to
     * guard creating trusted hotword detectors. This permission guard is only enabled when
     * {@link Flags.FLAG_VOICE_ACTIVATION_PERMISSION_APIS} is enabled.
     *
     */
    // TODO(b/305787465): Remove this method and request RECEIVE_SANDBOX_TRIGGER_AUDIO at the r
    //  elevant locations once flag has been fully ramped up.
    private AlwaysOnHotwordDetector callCreateAlwaysOnHotwordDetectorWithNecessaryPerm(
            AlwaysOnHotwordDetector.Callback callback, boolean useExecutor,
            @Nullable PersistableBundle options, String... permissions) {
        List<String> requestedPermissions = new ArrayList<String>(Arrays.asList(permissions));

        if (mVoiceActivationPermissionEnabled) {
            Log.i(TAG, "Requesting voice activation permissions!");
            requestedPermissions.add(RECEIVE_SANDBOX_TRIGGER_AUDIO);
        }

        try {
            return callWithShellPermissionIdentity(() ->
                            callCreateAlwaysOnHotwordDetector(callback, useExecutor, options),
                    requestedPermissions.toArray(new String[0]));
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Creates software hotword detector, appending RECEIVE_SANDBOX_TRIGGER_AUDIO permission to
     * requested permissions when the relevant flag is enabled.
     *
     * <p> <b>Context:</b>  A new permission (RECEIVE_SANDBOX_TRIGGER_AUDIO) has been added to
     * guard creating trusted hotword detectors. This permission guard is only enabled when
     *  {@link Flags.FLAG_VOICE_ACTIVATION_PERMISSION_APIS} is enabled.
     *
     */
    // TODO(b/305787465): Remove this method and request RECEIVE_SANDBOX_TRIGGER_AUDIO at the r
    //  elevant locations once flag has been fully ramped up.
    private HotwordDetector callCreateSoftwareDetectorWithNecessaryPerm(
            HotwordDetector.Callback callback, boolean useExecutor,
            @Nullable PersistableBundle options, String... permissions) {
        List<String> requestedPermissions = new ArrayList<String>(Arrays.asList(permissions));

        if (mVoiceActivationPermissionEnabled) {
            Log.i(TAG, "Requesting voice activation permissions!");
            requestedPermissions.add(RECEIVE_SANDBOX_TRIGGER_AUDIO);
        }

        try {
            return callWithShellPermissionIdentity(() ->
                            callCreateSoftwareHotwordDetector(callback, useExecutor, options),
                    requestedPermissions.toArray(new String[0]));
        } catch (Exception e) {
            return null;
        }
    }
}
