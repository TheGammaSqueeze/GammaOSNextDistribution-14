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

package android.musicrecognition.cts;

import static com.google.common.truth.Truth.assertWithMessage;

import android.content.ComponentName;
import android.media.AudioFormat;
import android.media.MediaMetadata;
import android.media.musicrecognition.MusicRecognitionService;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.common.io.ByteStreams;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** No-op implementation of MusicRecognitionService for testing purposes. */
public class CtsMusicRecognitionService extends MusicRecognitionService {
    private static final String TAG = CtsMusicRecognitionService.class.getSimpleName();
    public static final String SERVICE_PACKAGE = "android.musicrecognition.cts";
    public static final ComponentName SERVICE_COMPONENT = new ComponentName(
            SERVICE_PACKAGE, CtsMusicRecognitionService.class.getName());

    private static Watcher sWatcher;

    @Override
    public void onDestroy() {
        super.onDestroy();
        sWatcher.destroyed.countDown();
    }

    @Override
    public void onRecognize(@NonNull ParcelFileDescriptor stream,
            @NonNull AudioFormat audioFormat,
            @NonNull Callback callback) {
        if (sWatcher.failureCode != 0) {
            callback.onRecognitionFailed(sWatcher.failureCode);
        } else {
            Log.i(TAG, "Reading audio stream...");
            sWatcher.stream = readStream(stream);
            Log.i(TAG, "Reading audio done.");
            callback.onRecognitionSucceeded(sWatcher.result, sWatcher.resultExtras);
        }
    }

    @Override
    public @Nullable String getAttributionTag() {
        return "CtsMusicRecognitionAttributionTag";
    }

    private byte[] readStream(ParcelFileDescriptor stream) {
        ParcelFileDescriptor.AutoCloseInputStream fis =
                new ParcelFileDescriptor.AutoCloseInputStream(stream);
        try {
            return ByteStreams.toByteArray(fis);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static Watcher setWatcher() {
        if (sWatcher != null) {
            throw new IllegalStateException("Set watcher with watcher already set");
        }
        sWatcher = new Watcher();
        return sWatcher;
    }

    public static void clearWatcher() {
        sWatcher = null;
    }

    public static final class Watcher {
        private static final long SERVICE_LIFECYCLE_TIMEOUT_MS = 30_000;

        public CountDownLatch destroyed = new CountDownLatch(1);
        public int failureCode = 0;
        public byte[] stream;
        public MediaMetadata result;
        public Bundle resultExtras;

        public void awaitOnDestroy() {
            await(destroyed, "Waiting for service destroyed");
        }

        private void await(@NonNull CountDownLatch latch, @NonNull String message) {
            try {
                assertWithMessage(message).that(
                        latch.await(SERVICE_LIFECYCLE_TIMEOUT_MS, TimeUnit.MILLISECONDS)).isTrue();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while: " + message);
            }
        }
    }
}
