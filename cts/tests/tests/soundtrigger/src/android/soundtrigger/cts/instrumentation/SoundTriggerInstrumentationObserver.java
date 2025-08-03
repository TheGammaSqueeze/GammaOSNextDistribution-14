/*
 * Copyright (C) 2023 The Android Open Source Project
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

package android.soundtrigger.cts.instrumentation;

import static com.google.common.truth.Truth.assertThat;


import android.media.soundtrigger.SoundTriggerInstrumentation;
import android.media.soundtrigger.SoundTriggerInstrumentation.GlobalCallback;
import android.media.soundtrigger.SoundTriggerInstrumentation.ModelCallback;
import android.media.soundtrigger.SoundTriggerInstrumentation.ModelSession;
import android.media.soundtrigger.SoundTriggerInstrumentation.RecognitionCallback;
import android.media.soundtrigger.SoundTriggerInstrumentation.RecognitionSession;
import android.media.soundtrigger.SoundTriggerManager;
import android.util.Log;

import androidx.annotation.GuardedBy;
import androidx.annotation.NonNull;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Supporting class to observe and attach to the SoundTrigger HAL via
 * {@link SoundTriggerInstrumentation}
 */
public class SoundTriggerInstrumentationObserver implements AutoCloseable {
    private static final String TAG = SoundTriggerInstrumentationObserver.class.getSimpleName();

    /**
     * Observer class exposing {@link ListenableFuture}'s on the {@link GlobalCallback} interface
     *
     * <p>This value gets replaced via {@link #attachInstrumentation}
     */
    private GlobalCallbackObserver mGlobalCallbackObserver;

    /**
     * Attaches to the SoundTrigger HAL instrumentation and registers listeners to HAL events
     *
     * <p>This call requires {@link android.Manifest.permission.MANAGE_SOUND_TRIGGER}.
     * and then the permissions are revoked prior to returning.
     */
    public void attachInstrumentation() {
        Log.d(TAG, "attachInstrumentation");
        mGlobalCallbackObserver = new GlobalCallbackObserver();
        mGlobalCallbackObserver.attach();
    }

    /**
     * Observer class exposing {@link ListenableFuture}'s on the {@link ModelCallback} and
     * {@link RecognitionCallback}
     * interfaces.
     */
    public static class ModelSessionObserver implements AutoCloseable {
        private final Object mModelSessionLock = new Object();
        @GuardedBy("mModelSessionLock")
        @NonNull
        private SettableFuture<RecognitionSession> mOnRecognitionStarted = SettableFuture.create();
        @GuardedBy("mModelSessionLock")
        @NonNull
        private SettableFuture<Void> mOnRecognitionStopped = SettableFuture.create();
        @GuardedBy("mModelSessionLock")
        @NonNull
        private SettableFuture<Void> mOnModelUnloaded = SettableFuture.create();
        private final ModelSession mModelSession;
        private final Executor mModelSessionExecutor = Executors.newSingleThreadExecutor();

        private final RecognitionCallback mRecognitionCallback = () -> {
            Log.d(TAG, "RecognitionCallback.onRecognitionStopped");
            synchronized (mModelSessionLock) {
                mOnRecognitionStopped.set(null);
            }
        };

        private final ModelCallback mModelCallback = new ModelCallback() {
            @Override
            public void onRecognitionStarted(@NonNull RecognitionSession recognitionSession) {
                Log.d(TAG, "ModelCallback.onRecognitionStarted");
                recognitionSession.setRecognitionCallback(mModelSessionExecutor,
                        mRecognitionCallback);
                synchronized (mModelSessionLock) {
                    mOnRecognitionStarted.set(recognitionSession);
                }
            }

            @Override
            public void onModelUnloaded() {
                Log.d(TAG, "ModelCallback.onModelUnloaded");
                synchronized (mModelSessionLock) {
                    mOnModelUnloaded.set(null);
                }
            }
        };

        private ModelSessionObserver(ModelSession modelSession) {
            mModelSession = modelSession;
            modelSession.setModelCallback(mModelSessionExecutor, mModelCallback);
        }

        public ModelSession getModelSession() {
            return mModelSession;
        }

        /**
         * Returns a future to be completed on the next {@link ModelCallback#onRecognitionStarted}
         */
        public ListenableFuture<RecognitionSession> getOnRecognitionStartedFuture() {
            synchronized (mModelSessionLock) {
                return mOnRecognitionStarted;
            }
        }

        /**
         * Returns a future to be completed on the next
         * {@link RecognitionCallback#onRecognitionStopped}
         */
        public ListenableFuture<Void> getOnRecognitionStoppedFuture() {
            synchronized (mModelSessionLock) {
                return mOnRecognitionStopped;
            }
        }

        /**
         * Creates future to be completed on the next
         * {@link ModelCallback#onModelUnloaded}
         */
        public ListenableFuture<Void> getOnModelUnloadedFuture() {
            synchronized (mModelSessionLock) {
                return mOnModelUnloaded;
            }
        }

        /**
         * Reset future listening for {@link ModelCallback#onRecognitionStarted}
         *
         * <p>The future must be completed prior to calling this method.
         */
        public void resetOnRecognitionStartedFuture() {
            synchronized (mModelSessionLock) {
                assertThat(mOnRecognitionStarted.isDone()).isTrue();
                mOnRecognitionStarted = SettableFuture.create();
            }
        }

        /**
         * Reset future listening for {@link RecognitionCallback#onRecognitionStopped}
         *
         * <p>The future must be completed prior to calling this method.
         */
        public void resetOnRecognitionStoppedFuture() {
            synchronized (mModelSessionLock) {
                assertThat(mOnRecognitionStopped.isDone()).isTrue();
                mOnRecognitionStopped = SettableFuture.create();
            }
        }

        /**
         * Reset future listening for {@link ModelCallback#onModelUnloaded}
         *
         * <p>The future must be completed prior to calling this method.
         */
        public void resetOnModelUnloadedFuture() {
            synchronized (mModelSessionLock) {
                assertThat(mOnModelUnloaded.isDone()).isTrue();
                mOnModelUnloaded = SettableFuture.create();
            }
        }

        @Override
        public void close() throws Exception {
            mModelSession.clearModelCallback();
            synchronized (mModelSessionLock) {
                mOnRecognitionStarted.cancel(true /* mayInterruptIfRunning */);
                mOnRecognitionStopped.cancel(true /* mayInterruptIfRunning */);
                mOnModelUnloaded.cancel(true /* mayInterruptIfRunning */);
            }
        }
    }

    /**
     * Observer class exposing {@link ListenableFuture}'s on the {@link GlobalCallback} interface
     */
    public static class GlobalCallbackObserver implements AutoCloseable {
        private final Executor mGlobalCallbackExecutor = Executors.newSingleThreadExecutor();
        private final Object mGlobalObserverLock = new Object();
        @GuardedBy("mGlobalObserverLock")
        @NonNull
        private SettableFuture<ModelSessionObserver> mOnModelLoaded = SettableFuture.create();
        @GuardedBy("mGlobalObserverLock")
        @NonNull
        private SettableFuture<Void> mOnClientAttached = SettableFuture.create();
        @GuardedBy("mGlobalObserverLock")
        @NonNull
        private SettableFuture<Void> mOnClientDetached = SettableFuture.create();
        // do not expose as an API, only held for cleanup
        @GuardedBy("mGlobalObserverLock")
        private ModelSessionObserver mModelSessionObserver;
        private SoundTriggerInstrumentation mInstrumentation;

        private final GlobalCallback mGlobalCallback = new GlobalCallback() {
            @Override
            public void onModelLoaded(@NonNull ModelSession modelSession) {
                Log.d(TAG, "GlobalCallback.onModelLoaded");
                synchronized (mGlobalObserverLock) {
                    mModelSessionObserver = new ModelSessionObserver(modelSession);
                    mOnModelLoaded.set(mModelSessionObserver);
                }
            }

            @Override
            public void onClientAttached() {
                Log.d(TAG, "GlobalCallback.onClientAttached");
                synchronized (mGlobalObserverLock) {
                    mOnClientAttached.set(null);
                }
            }

            @Override
            public void onClientDetached() {
                Log.d(TAG, "GlobalCallback.onClientDetached");
                synchronized (mGlobalObserverLock) {
                    mOnClientDetached.set(null);
                }
            }
        };

        /**
         * Attaches to the SoundTrigger HAL instrumentation and registers listeners to HAL events
         */
        private void attach() {
            Log.d(TAG, "attach SoundTriggerInstrumentation");
            mInstrumentation = SoundTriggerManager.attachInstrumentation(
                    mGlobalCallbackExecutor, mGlobalCallback);
        }

        /**
         * Instrumentation reference to the SoundTrigger HAL
         *
         * <p>Must call {@link #attach} first
         * <p>This value gets replaced via {@link #attach}
         */
        public SoundTriggerInstrumentation getInstrumentation() {
            assertThat(mInstrumentation).isNotNull();
            return mInstrumentation;
        }

        /**
         * Returns a future to be completed on the next {@link GlobalCallback#onModelLoaded}
         */
        public ListenableFuture<ModelSessionObserver> getOnModelLoadedFuture() {
            synchronized (mGlobalObserverLock) {
                return mOnModelLoaded;
            }
        }

        /**
         * Returns a future to be completed on the next {@link GlobalCallback#onClientAttached()}
         */
        public ListenableFuture<Void> getOnClientAttachedFuture() {
            synchronized (mGlobalObserverLock) {
                return mOnClientAttached;
            }
        }

        /**
         * Returns a future to be completed on the next {@link GlobalCallback#onClientDetached()}
         */
        public ListenableFuture<Void> getOnClientDetachedFuture() {
            synchronized (mGlobalObserverLock) {
                return mOnClientDetached;
            }
        }

        /**
         * Reset future listening for {@link GlobalCallback#onModelLoaded}
         *
         * <p>The future must be completed prior to calling this method.
         */
        public void resetOnModelLoadedFuture() {
            synchronized (mGlobalObserverLock) {
                assertThat(mOnModelLoaded.isDone()).isTrue();
                mOnModelLoaded = SettableFuture.create();
            }
        }

        /**
         * Reset future listening for {@link GlobalCallback#onModelLoaded}
         *
         * <p>The future must be completed prior to calling this method.
         */
        public void resetOnClientAttachedFuture() {
            synchronized (mGlobalObserverLock) {
                assertThat(mOnClientAttached.isDone()).isTrue();
                mOnClientAttached = SettableFuture.create();
            }
        }

        /**
         * Reset future listening for {@link GlobalCallback#onModelLoaded}
         *
         * <p>The future must be completed prior to calling this method.
         */
        public void resetOnClientDetachedFuture() {
            synchronized (mGlobalObserverLock) {
                assertThat(mOnClientDetached.isDone()).isTrue();
                mOnClientDetached = SettableFuture.create();
            }
        }

        @Override
        public void close() throws Exception {
            synchronized (mGlobalObserverLock) {
                if (mModelSessionObserver != null) {
                    mModelSessionObserver.close();
                    mModelSessionObserver = null;
                }
                mOnModelLoaded.cancel(true /* mayInterruptIfRunning */);
                mOnClientAttached.cancel(true /* mayInterruptIfRunning */);
                mOnClientDetached.cancel(true /* mayInterruptIfRunning */);
            }
            if (mInstrumentation != null) {
                try {
                    mInstrumentation.triggerRestart();
                } catch (IllegalStateException e) {
                    Log.i(TAG, "Closing before instrumentation registration", e);
                }
                mInstrumentation = null;
            }
        }
    }

    /**
     * Get the observer to {@link SoundTriggerInstrumentation} callbacks
     *
     * <p>Must call {@link #attachInstrumentation} first
     */
    public GlobalCallbackObserver getGlobalCallbackObserver() {
        assertThat(mGlobalCallbackObserver).isNotNull();
        return mGlobalCallbackObserver;
    }

    /**
     * Helper method for common listener of recognition started
     */
    public ListenableFuture<RecognitionSession> getOnRecognitionStartedFuture() {
        return Futures.transformAsync(getGlobalCallbackObserver().getOnModelLoadedFuture(),
                ModelSessionObserver::getOnRecognitionStartedFuture, Runnable::run);
    }

    @Override
    public void close() throws Exception {
        if (mGlobalCallbackObserver != null) {
            mGlobalCallbackObserver.close();
            mGlobalCallbackObserver = null;
        }
    }
}
