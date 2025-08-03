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

package android.media.bettertogether.cts;

import static com.google.common.truth.Truth.assertThat;

import static java.util.concurrent.TimeUnit.MILLISECONDS;

import android.annotation.Nullable;
import android.media.session.MediaController;
import android.media.session.MediaSession;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

public class MediaSessionTestService extends RemoteService {
    public static final int TEST_SERIES_OF_SET_QUEUE = 0;
    public static final int TEST_SET_QUEUE = 1;

    public static final int STEP_SET_UP = 0;
    public static final int STEP_CHECK = 1;
    public static final int STEP_CLEAN_UP = 2;

    public static final String KEY_SESSION_TOKEN = "sessionToken";
    public static final String KEY_EXPECTED_TOTAL_NUMBER_OF_ITEMS = "expectedTotalNumberOfItems";
    public static final String KEY_EXPECTED_QUEUE_SIZE = "expectedQueueSize";

    private MediaController mMediaController;
    private MediaController.Callback mMediaControllerCallback;
    private CountDownLatch mAllItemsNotified;
    private CountDownLatch mQueueNotified;

    private void testSeriesOfSetQueue_setUp(Bundle args) {
        MediaSession.Token token = args.getParcelable(KEY_SESSION_TOKEN);
        int expectedTotalNumberOfItems = args.getInt(KEY_EXPECTED_TOTAL_NUMBER_OF_ITEMS);

        mAllItemsNotified = new CountDownLatch(1);
        AtomicInteger numberOfItems = new AtomicInteger();
        mMediaControllerCallback = new MediaController.Callback() {
            @Override
            public void onQueueChanged(List<MediaSession.QueueItem> queue) {
                if (queue != null) {
                    if (numberOfItems.addAndGet(queue.size()) >= expectedTotalNumberOfItems) {
                        mAllItemsNotified.countDown();
                    }
                }
            }
        };
        mMediaController = new MediaController(this, token);
        mMediaController.registerCallback(mMediaControllerCallback,
                new Handler(Looper.getMainLooper()));
    }

    private void testSeriesOfSetQueue_check() throws Exception {
        assertThat(mAllItemsNotified.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    }

    private void testSeriesOfSetQueue_cleanUp() {
        mMediaController.unregisterCallback(mMediaControllerCallback);
        mMediaController = null;
        mMediaControllerCallback = null;
        mAllItemsNotified = null;
    }

    private void testSetQueue_setUp(Bundle args) {
        MediaSession.Token token = args.getParcelable(KEY_SESSION_TOKEN);
        int expectedQueueSize = args.getInt(KEY_EXPECTED_QUEUE_SIZE);

        mQueueNotified = new CountDownLatch(1);
        mMediaControllerCallback = new MediaController.Callback() {
            @Override
            public void onQueueChanged(List<MediaSession.QueueItem> queue) {
                if (queue != null && queue.size() == expectedQueueSize) {
                    mQueueNotified.countDown();
                }
            }
        };
        mMediaController = new MediaController(this, token);
        mMediaController.registerCallback(mMediaControllerCallback,
                new Handler(Looper.getMainLooper()));
    }

    private void testSetQueue_check() throws Exception {
        assertThat(mQueueNotified.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    }

    private void testSetQueue_cleanUp() {
        mMediaController.unregisterCallback(mMediaControllerCallback);
        mMediaController = null;
        mMediaControllerCallback = null;
        mQueueNotified = null;
    }

    @Override
    public void onRun(int testId, int step, @Nullable Bundle args) throws Exception {
        if (testId == TEST_SERIES_OF_SET_QUEUE) {
            if (step == STEP_SET_UP) {
                testSeriesOfSetQueue_setUp(args);
            } else if (step == STEP_CHECK) {
                testSeriesOfSetQueue_check();
            } else if (step == STEP_CLEAN_UP) {
                testSeriesOfSetQueue_cleanUp();
            } else {
                throw new IllegalArgumentException("Unknown step=" + step);
            }
        } else if (testId == TEST_SET_QUEUE) {
            if (step == STEP_SET_UP) {
                testSetQueue_setUp(args);
            } else if (step == STEP_CHECK) {
                testSetQueue_check();
            } else if (step == STEP_CLEAN_UP) {
                testSetQueue_cleanUp();
            } else {
                throw new IllegalArgumentException("Unknown step=" + step);
            }

        } else {
            throw new IllegalArgumentException("Unknown testId=" + testId);
        }
    }
}
