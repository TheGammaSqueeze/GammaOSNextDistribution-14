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

package android.voiceinteraction.service;

import static android.Manifest.permission.CAPTURE_AUDIO_HOTWORD;
import static android.Manifest.permission.RECORD_AUDIO;

import static com.android.compatibility.common.util.SystemUtil.runWithShellPermissionIdentity;

import static java.util.Objects.requireNonNull;

import android.content.Intent;
import android.hardware.soundtrigger.SoundTrigger;
import android.hardware.soundtrigger.SoundTrigger.KeyphraseRecognitionExtra;
import android.hardware.soundtrigger.SoundTrigger.KeyphraseSoundModel;
import android.media.AudioFormat;
import android.media.soundtrigger.SoundTriggerManager;
import android.media.voice.KeyphraseModelManager;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.os.PersistableBundle;
import android.os.RemoteException;
import android.os.SharedMemory;
import android.service.voice.AlwaysOnHotwordDetector;
import android.service.voice.VoiceInteractionService;
import android.util.Log;

import androidx.annotation.GuardedBy;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.IOException;
import java.io.OutputStream;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Proxy VoiceInteractionService implementation
 *
 * This class is intended to be instrumented by a test class. All the service's APIs and owned
 * objects should be exposed as directly as possible via an AIDL interface. This allows the test
 * class to control the entire behavior of the service and make no assumptions about what the
 * service may or may not do.
 */
public class ProxyVoiceInteractionService extends VoiceInteractionService {
    public static final String ACTION_BIND_TEST_VOICE_INTERACTION =
            "android.intent.action.ACTION_BIND_TEST_VOICE_INTERACTION";

    static final String TAG = ProxyVoiceInteractionService.class.getSimpleName();
    private final Object mLock = new Object();
    private final Handler mHandler = Handler.createAsync(Looper.getMainLooper());
    private ITestVoiceInteractionServiceListener mClientListener = null;
    private boolean mReady = false;

    ITestVoiceInteractionService mDebugInterface = new ITestVoiceInteractionService.Stub() {
        @Override
        public void registerListener(ITestVoiceInteractionServiceListener listener) {
            Log.d(TAG, "registerListener");
            mHandler.post(() -> ProxyVoiceInteractionService.this.registerListener(
                    listener));
        }

        @Override
        public IProxyAlwaysOnHotwordDetector createAlwaysOnHotwordDetector(String keyphrase,
                String locale, IProxyDetectorCallback callback) {
            Log.d(TAG,
                    "createAlwaysOnHotwordDetector: keyphrase=" + keyphrase + ", locale=" + locale
                            + ", targetSdk="
                            + getApplicationContext().getApplicationInfo().targetSdkVersion);
            return FakeAlwaysOnHotwordDetector.create(keyphrase, Locale.forLanguageTag(locale),
                    callback, ProxyVoiceInteractionService.this, mHandler);
        }

        @Override
        public IProxyAlwaysOnHotwordDetector createAlwaysOnHotwordDetectorWithTrustedService(
                String keyphrase, String locale, PersistableBundle options,
                SharedMemory sharedMemory, IProxyDetectorCallback callback) {
            Log.d(TAG,
                    "createAlwaysOnHotwordDetectorWithTrustedService: keyphrase=" + keyphrase
                            + ", locale=" + locale
                            + ", targetSdk="
                            + getApplicationContext().getApplicationInfo().targetSdkVersion);
            return FakeAlwaysOnHotwordDetector.create(keyphrase, Locale.forLanguageTag(locale),
                    options, sharedMemory, callback, ProxyVoiceInteractionService.this, mHandler);
        }

        @Override
        public IProxyKeyphraseModelManager createKeyphraseModelManager() {
            Log.d(TAG, "createKeyphraseModelManager");
            KeyphraseModelManager keyphraseModelManager =
                    ProxyVoiceInteractionService.this.createKeyphraseModelManager();
            return new IProxyKeyphraseModelManager.Stub() {
                @Override
                public KeyphraseSoundModel getKeyphraseSoundModel(int keyphraseId, String locale) {
                    return keyphraseModelManager.getKeyphraseSoundModel(keyphraseId,
                            Locale.forLanguageTag(locale));
                }

                @Override
                public void updateKeyphraseSoundModel(KeyphraseSoundModel model) {
                    keyphraseModelManager.updateKeyphraseSoundModel(model);
                }

                @Override
                public void deleteKeyphraseSoundModel(int keyphraseId, String locale) {
                    keyphraseModelManager.deleteKeyphraseSoundModel(keyphraseId,
                            Locale.forLanguageTag(locale));
                }
            };
        }

        @Override
        public SoundTrigger.ModuleProperties getDspModuleProperties() {
            return requireNonNull(
                    getSystemService(SoundTriggerManager.class)).getModuleProperties();
        }
    };

    @Override
    public void onReady() {
        super.onReady();
        Log.d(TAG, "service ready");
        synchronized (mLock) {
            mReady = true;
            if (mClientListener != null) {
                updateClientListenerReady();
            }
        }
    }

    @Override
    public void onShutdown() {
        super.onShutdown();
        Log.d(TAG, "asynchronous shutdown");
        synchronized (mLock) {
            if (mClientListener != null) {
                try {
                    mClientListener.onShutdown();
                } catch (RemoteException e) {
                    Log.e(TAG, "failed to notify client system service has shutdown", e);
                    mClientListener = null;
                }
            }

            mReady = false;
            mClientListener = null;
        }
    }

    private void registerListener(ITestVoiceInteractionServiceListener listener) {
        synchronized (mLock) {
            Log.d(TAG, "registering listener for service events");
            mClientListener = listener;
            if (mReady) {
                updateClientListenerReady();
            }
        }
    }

    @GuardedBy("mLock")
    private void updateClientListenerReady() {
        try {
            mClientListener.onReady();
        } catch (RemoteException e) {
            mClientListener = null;
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        if (ACTION_BIND_TEST_VOICE_INTERACTION.equals(intent.getAction())) {
            Log.d(TAG, "returning debug binder: " + intent);
            synchronized (mLock) {
                mClientListener = null;
                return mDebugInterface.asBinder();
            }
        }
        return super.onBind(intent);
    }

    private static class FakeAlwaysOnHotwordDetector extends IProxyAlwaysOnHotwordDetector.Stub {

        private static final byte[] FAKE_HOTWORD_AUDIO_DATA =
                new byte[]{'h', 'o', 't', 'w', 'o', 'r', 'd', '!'};
        private final AlwaysOnHotwordDetector mAlwaysOnHotwordDetector;
        private ParcelFileDescriptor[] mTempParcelFileDescriptor = null;

        FakeAlwaysOnHotwordDetector(AlwaysOnHotwordDetector alwaysOnHotwordDetector) {
            mAlwaysOnHotwordDetector = alwaysOnHotwordDetector;
        }

        static FakeAlwaysOnHotwordDetector create(String keyphrase, Locale locale,
                PersistableBundle options, SharedMemory sharedMemory,
                IProxyDetectorCallback callback, VoiceInteractionService service, Handler handler) {
            return create(() -> runWithShellPermissionIdentity(() -> {
                        service.setTestModuleForAlwaysOnHotwordDetectorEnabled(true);
                        var aohd = service.createAlwaysOnHotwordDetector(keyphrase, locale, options,
                                sharedMemory,
                                new HandlerExecutor(new Handler(Looper.myLooper())),
                                createDetectorCallback(callback));
                        service.setTestModuleForAlwaysOnHotwordDetectorEnabled(false);
                        return aohd;
                        }, RECORD_AUDIO, CAPTURE_AUDIO_HOTWORD /** required perms **/
                ), handler);
        }

        static FakeAlwaysOnHotwordDetector create(String keyphrase, Locale locale,
                IProxyDetectorCallback callback, VoiceInteractionService service, Handler handler) {
            return create(() -> runWithShellPermissionIdentity(() -> {
                    service.setTestModuleForAlwaysOnHotwordDetectorEnabled(true);
                    var aohd = service.createAlwaysOnHotwordDetector(keyphrase, locale,
                        new HandlerExecutor(new Handler(Looper.myLooper())),
                        createDetectorCallback(callback));
                    service.setTestModuleForAlwaysOnHotwordDetectorEnabled(false);
                    return aohd;
                    }, RECORD_AUDIO, CAPTURE_AUDIO_HOTWORD /** required perms */
                ), handler);
        }

        private static AlwaysOnHotwordDetector.Callback createDetectorCallback(
                IProxyDetectorCallback callback) {
            return new AlwaysOnHotwordDetector.Callback() {
                @Override
                public void onAvailabilityChanged(int status) {
                    try {
                        callback.onAvailabilityChanged(status);
                    } catch (RemoteException e) {
                        e.rethrowAsRuntimeException();
                    }
                }

                @Override
                public void onDetected(
                        AlwaysOnHotwordDetector.EventPayload eventPayload) {
                }

                @Override
                public void onError() {
                }

                @Override
                public void onRecognitionPaused() {
                }

                @Override
                public void onRecognitionResumed() {
                }
            };
        }

        private static FakeAlwaysOnHotwordDetector create(
                Callable<AlwaysOnHotwordDetector> createDetectorCallable, Handler handler) {
            AtomicReference<Optional<AlwaysOnHotwordDetector>> detector =
                    new AtomicReference<>(Optional.empty());
            AtomicReference<Optional<RuntimeException>> detectorException =
                    new AtomicReference<>(Optional.empty());
            final CountDownLatch detectorCreatedLatch = new CountDownLatch(1);
            handler.post(() -> {
                try {
                    detector.set(Optional.of(createDetectorCallable.call()));
                } catch (RuntimeException e) {
                    Log.e(TAG, "failed to create hotword detector", e);
                    detectorException.set(Optional.of(e));
                } catch (Exception e) {
                    /*
                      Only log exception which will cause TimeoutException to create the fake
                      detector. If there is a need to report a checked exception to the caller,
                      assign an {@link ExceptionReference} value with a
                      {@link ServiceSpecificException} and throw it.
                     */
                    Log.e(TAG, "failed to create hotword detector", e);
                }
                detectorCreatedLatch.countDown();
            });
            try {
                if (!detectorCreatedLatch.await(5, TimeUnit.SECONDS)) {
                    return null;
                }
            } catch (InterruptedException e) {
                return null;
            }
            if (detectorException.get().isPresent()) {
                throw detectorException.get().get();
            }
            if (detector.get().isPresent()) {
                return new FakeAlwaysOnHotwordDetector(detector.get().get());
            }
            return null;
        }

        @Override
        public void updateState(@Nullable PersistableBundle options,
                @Nullable SharedMemory sharedMemory) {
            mAlwaysOnHotwordDetector.updateState(options, sharedMemory);
        }

        @Override
        public boolean startRecognitionOnFakeAudioStream() {
            return startRecognitionWithAudioStream(
                    createFakeAudioStream(),
                    createFakeAudioFormat(),
                    null
            );
        }

        @Override
        public boolean startRecognitionWithAudioStream(ParcelFileDescriptor audioStream,
                AudioFormat audioFormat, @Nullable PersistableBundle options) {
            return mAlwaysOnHotwordDetector.startRecognition(audioStream,
                    audioFormat, options);
        }

        @Override
        public boolean startRecognitionWithFlagsAndData(int recognitionFlags, byte[] data) {
            return mAlwaysOnHotwordDetector.startRecognition(recognitionFlags, data);
        }

        @Override
        public boolean startRecognitionWithFlags(int recognitionFlags) {
            return mAlwaysOnHotwordDetector.startRecognition(recognitionFlags);
        }

        @Override
        public boolean startRecognition() {
            return mAlwaysOnHotwordDetector.startRecognition();
        }

        @Override
        public boolean stopRecognition() {
            closeFakeAudioStream();
            return mAlwaysOnHotwordDetector.stopRecognition();
        }

        @Override
        public void triggerHardwareRecognitionEventForTest(
                int status,
                int soundModelHandle,
                long halEventReceivedMillis,
                boolean captureAvailable,
                int captureSession,
                int captureDelayMs,
                int capturePreambleMs,
                boolean triggerInData,
                AudioFormat audioFormat,
                @Nullable byte[] data,
                List<KeyphraseRecognitionExtra> keyphraseExtras) {
            mAlwaysOnHotwordDetector.triggerHardwareRecognitionEventForTest(status,
                    soundModelHandle, halEventReceivedMillis, captureAvailable,
                    captureSession, captureDelayMs, capturePreambleMs, triggerInData, audioFormat,
                    data, keyphraseExtras);
        }

        @Override
        public void overrideAvailability(int availability) {
            mAlwaysOnHotwordDetector.overrideAvailability(availability);
        }

        @Override
        public void resetAvailability() {
            mAlwaysOnHotwordDetector.resetAvailability();
        }

        @Override
        public void destroy() {
            mAlwaysOnHotwordDetector.destroy();
        }

        private AudioFormat createFakeAudioFormat() {
            return new AudioFormat.Builder()
                    .setSampleRate(32000)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setChannelMask(AudioFormat.CHANNEL_IN_MONO).build();
        }

        private ParcelFileDescriptor createFakeAudioStream() {
            try {
                mTempParcelFileDescriptor = ParcelFileDescriptor.createPipe();
                try (OutputStream fos =
                             new ParcelFileDescriptor.AutoCloseOutputStream(
                                     mTempParcelFileDescriptor[1])) {
                    fos.write(FAKE_HOTWORD_AUDIO_DATA, 0, 8);
                } catch (IOException e) {
                    Log.w(TAG, "Failed to pipe audio data : ", e);
                    return null;
                }
                return mTempParcelFileDescriptor[0];
            } catch (IOException e) {
                Log.w(TAG, "Failed to create a pipe : " + e);
            }
            return null;
        }

        private void closeFakeAudioStream() {
            if (mTempParcelFileDescriptor != null) {
                try {
                    mTempParcelFileDescriptor[0].close();
                    mTempParcelFileDescriptor[1].close();
                } catch (IOException e) {
                    Log.w(TAG, "Failed closing : " + e);
                }
                mTempParcelFileDescriptor = null;
            }
        }
    }

    /**
     * An adapter {@link Executor} that posts all executed tasks onto the given
     * {@link Handler}.
     */
    private static class HandlerExecutor implements Executor {
        private final Handler mHandler;

        public HandlerExecutor(@NonNull Handler handler) {
            mHandler = requireNonNull(handler);
        }

        @Override
        public void execute(Runnable command) {
            if (!mHandler.post(command)) {
                throw new RejectedExecutionException(mHandler + " is shutting down");
            }
        }
    }

}
