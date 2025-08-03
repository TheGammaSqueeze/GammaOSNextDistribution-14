/*
 * Copyright (C) 2017 The Android Open Source Project
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

package android.media.session.cts;

import android.content.Context;
import android.media.AudioManager;
import android.media.AudioPlaybackConfiguration;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Handler;
import android.os.HandlerThread;
import android.platform.test.annotations.AppModeFull;

import junit.framework.Assert;

import java.io.File;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class Utils {
    private static final String TAG = "CtsMediaTestUtil";
    private static final int TEST_TIMING_TOLERANCE_MS = 500;

    /**
     * Assert that a media playback is started and an active {@link AudioPlaybackConfiguration}
     * is created once. The playback will be stopped immediately after that.
     * <p>For a media session to receive media button events, an actual playback is needed.
     */
    @AppModeFull(reason = "Instant apps cannot access the SD card")
    static void assertMediaPlaybackStarted(Context context) {
        final AudioManager am = context.getSystemService(AudioManager.class);
        final HandlerThread handlerThread = new HandlerThread(TAG);
        handlerThread.start();
        final TestAudioPlaybackCallback callback = new TestAudioPlaybackCallback();
        MediaPlayer mediaPlayer = null;

        try {
            final int activeConfigSizeBeforeStart = am.getActivePlaybackConfigurations().size();
            final Handler handler = new Handler(handlerThread.getLooper());

            am.registerAudioPlaybackCallback(callback, handler);
            mediaPlayer = MediaPlayer.create(context, R.raw.sine1khzm40db);
            mediaPlayer.start();
            if (!callback.mCountDownLatch.await(TEST_TIMING_TOLERANCE_MS, TimeUnit.MILLISECONDS)
                    || callback.mActiveConfigSize != activeConfigSizeBeforeStart + 1) {
                Assert.fail("Failed to create an active AudioPlaybackConfiguration");
            }
        } catch (InterruptedException e) {
            Assert.fail("Failed to create an active AudioPlaybackConfiguration");
        } finally {
            am.unregisterAudioPlaybackCallback(callback);
            if (mediaPlayer != null) {
                mediaPlayer.stop();
                mediaPlayer.release();
                mediaPlayer = null;
            }
            handlerThread.quitSafely();
        }
    }

    private static class TestAudioPlaybackCallback extends AudioManager.AudioPlaybackCallback {
        private final CountDownLatch mCountDownLatch = new CountDownLatch(1);
        private int mActiveConfigSize;

        @Override
        public void onPlaybackConfigChanged(List<AudioPlaybackConfiguration> configs) {
            // For non-framework apps, only anonymized active AudioPlaybackCallbacks will be
            // notified.
            mActiveConfigSize = configs.size();
            mCountDownLatch.countDown();
        }
    }
}
