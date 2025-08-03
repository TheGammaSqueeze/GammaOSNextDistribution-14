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
import android.content.ComponentName;
import android.media.browse.MediaBrowser;
import android.media.browse.MediaBrowser.MediaItem;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

public class MediaBrowserServiceTestService extends RemoteService {
    public static final int TEST_SERIES_OF_NOTIFY_CHILDREN_CHANGED = 0;

    public static final int STEP_SET_UP = 0;
    public static final int STEP_CHECK = 1;
    public static final int STEP_CLEAN_UP = 2;

    public static final String KEY_SERVICE_COMPONENT_NAME = "serviceComponentName";
    public static final String KEY_PARENT_MEDIA_ID = "parentMediaId";
    public static final String KEY_EXPECTED_TOTAL_NUMBER_OF_ITEMS = "expectedTotalNumberOfItems";

    private final Handler mMainHandler = new Handler(Looper.getMainLooper());
    private MediaBrowser mMediaBrowser;
    private CountDownLatch mAllItemsNotified;

    private void testSeriesOfNotifyChildrenChanged_setUp(Bundle args) throws Exception {
        ComponentName componentName = args.getParcelable(KEY_SERVICE_COMPONENT_NAME);
        String parentMediaId = args.getString(KEY_PARENT_MEDIA_ID);
        int expectedTotalNumberOfItems = args.getInt(KEY_EXPECTED_TOTAL_NUMBER_OF_ITEMS);

        mAllItemsNotified = new CountDownLatch(1);
        AtomicInteger numberOfItems = new AtomicInteger();
        CountDownLatch subscribed = new CountDownLatch(1);
        MediaBrowser.ConnectionCallback connectionCallback = new MediaBrowser.ConnectionCallback();
        MediaBrowser.SubscriptionCallback subscriptionCallback =
                new MediaBrowser.SubscriptionCallback() {
                    @Override
                    public void onChildrenLoaded(String parentId, List<MediaItem> children) {
                        if (parentMediaId.equals(parentId) && children != null) {
                            if (subscribed.getCount() > 0) {
                                subscribed.countDown();
                                return;
                            }
                            if (numberOfItems.addAndGet(children.size())
                                    >= expectedTotalNumberOfItems) {
                                mAllItemsNotified.countDown();
                            }
                        }
                    }
                };
        mMainHandler.post(() -> {
            mMediaBrowser = new MediaBrowser(this, componentName, connectionCallback, null);
            mMediaBrowser.connect();
            mMediaBrowser.subscribe(parentMediaId, subscriptionCallback);
        });
        assertThat(subscribed.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    }

    private void testSeriesOfNotifyChildrenChanged_check() throws Exception {
        assertThat(mAllItemsNotified.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    }

    private void testSeriesOfNotifyChildrenChanged_cleanUp() {
        mMainHandler.post(() -> {
            mMediaBrowser.disconnect();
            mMediaBrowser = null;
        });
        mAllItemsNotified = null;
    }

    @Override
    public void onRun(int testId, int step, @Nullable Bundle args) throws Exception {
        if (testId == TEST_SERIES_OF_NOTIFY_CHILDREN_CHANGED) {
            if (step == STEP_SET_UP) {
                testSeriesOfNotifyChildrenChanged_setUp(args);
            } else if (step == STEP_CHECK) {
                testSeriesOfNotifyChildrenChanged_check();
            } else if (step == STEP_CLEAN_UP) {
                testSeriesOfNotifyChildrenChanged_cleanUp();
            } else {
                throw new IllegalArgumentException("Unknown step=" + step);
            }
        } else {
            throw new IllegalArgumentException("Unknown testId=" + testId);
        }
    }
}
