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
import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assert.assertThrows;

import android.app.Instrumentation;
import android.content.ComponentName;
import android.media.browse.MediaBrowser;
import android.media.browse.MediaBrowser.MediaItem;
import android.os.Bundle;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.NonMainlineTest;
import com.android.compatibility.common.util.PollingCheck;

import com.google.common.truth.Correspondence;

import org.junit.After;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Test {@link android.media.browse.MediaBrowser}.
 */
@NonMainlineTest
@RunWith(AndroidJUnit4.class)
public class MediaBrowserTest {
    // The maximum time to wait for an operation.
    private static final long TIME_OUT_MS = 3000L;

    /**
     * To check {@link MediaBrowser#unsubscribe} works properly,
     * we notify to the browser after the unsubscription that the media items have changed.
     * Then {@link MediaBrowser.SubscriptionCallback#onChildrenLoaded} should not be called.
     *
     * The measured time from calling {@link StubMediaBrowserService#notifyChildrenChanged}
     * to {@link MediaBrowser.SubscriptionCallback#onChildrenLoaded} being called is about 50ms.
     * So we make the thread sleep for 100ms to properly check that the callback is not called.
     */
    private static final long SLEEP_MS = 100L;
    private static final ComponentName TEST_BROWSER_SERVICE = new ComponentName(
            "android.media.bettertogether.cts",
            "android.media.bettertogether.cts.StubMediaBrowserService");
    private static final ComponentName TEST_INVALID_BROWSER_SERVICE = new ComponentName(
            "invalid.package", "invalid.ServiceClassName");

    private static final Correspondence<MediaBrowser.MediaItem, String>
            MEDIA_ITEM_HAS_ID =
            Correspondence.from((MediaBrowser.MediaItem actual, String expected) -> {
                if (actual == null) {
                    return expected == null;
                }

                return actual.getMediaId().equals(expected);
            }, "has an ID of");

    private final StubConnectionCallback mConnectionCallback = new StubConnectionCallback();
    private final StubSubscriptionCallback mSubscriptionCallback = new StubSubscriptionCallback();
    private final StubItemCallback mItemCallback = new StubItemCallback();

    private MediaBrowser mMediaBrowser;

    private Instrumentation getInstrumentation() {
        return InstrumentationRegistry.getInstrumentation();
    }

    @After
    public void tearDown() {
        if (mMediaBrowser != null) {
            try {
                disconnectMediaBrowser();
            } catch (Throwable t) {
                // Ignore.
            }
            mMediaBrowser = null;
        }
    }

    @Test
    public void testMediaBrowser() throws Throwable {
        resetCallbacks();
        createMediaBrowser(TEST_BROWSER_SERVICE);
        runOnMainThread(() -> assertThat(mMediaBrowser.isConnected()).isFalse());

        connectMediaBrowserService();
        runOnMainThread(() -> assertThat(mMediaBrowser.isConnected()).isTrue());

        runOnMainThread(() -> {
            assertThat(mMediaBrowser.getServiceComponent())
                    .isEqualTo(TEST_BROWSER_SERVICE);
            assertThat(mMediaBrowser.getRoot())
                    .isEqualTo(StubMediaBrowserService.MEDIA_ID_ROOT);
            assertThat(mMediaBrowser.getExtras().getString(StubMediaBrowserService.EXTRAS_KEY))
                    .isEqualTo(StubMediaBrowserService.EXTRAS_VALUE);
            assertThat(mMediaBrowser.getSessionToken())
                    .isEqualTo(StubMediaBrowserService.sSession.getSessionToken());
        });

        disconnectMediaBrowser();
        runOnMainThread(() -> new PollingCheck(TIME_OUT_MS) {
            @Override
            protected boolean check() {
                return !mMediaBrowser.isConnected();
            }
        }.run());
    }

    @Test
    public void testThrowingISEWhileNotConnected() throws Throwable {
        resetCallbacks();
        createMediaBrowser(TEST_BROWSER_SERVICE);
        runOnMainThread(() -> assertThat(mMediaBrowser.isConnected()).isFalse());

        runOnMainThread(() -> {
            assertThrows(IllegalStateException.class,
                    () -> mMediaBrowser.getExtras());

            assertThrows(IllegalStateException.class,
                    () -> mMediaBrowser.getRoot());

            assertThrows(IllegalStateException.class,
                    () -> mMediaBrowser.getServiceComponent());

            assertThrows(IllegalStateException.class,
                    () -> mMediaBrowser.getSessionToken());
        });
    }

    @Test
    public void testConnectTwice() throws Throwable {
        resetCallbacks();
        createMediaBrowser(TEST_BROWSER_SERVICE);
        connectMediaBrowserService();
        runOnMainThread(() -> {
            assertThrows(IllegalStateException.class,
                    () -> mMediaBrowser.connect());
        });
    }

    @Test
    public void testConnectionFailed() throws Throwable {
        resetCallbacks();
        createMediaBrowser(TEST_INVALID_BROWSER_SERVICE);

        runOnMainThread(() -> mMediaBrowser.connect());

        new PollingCheck(TIME_OUT_MS) {
            @Override
            protected boolean check() {
                return mConnectionCallback.mConnectionFailedCount > 0
                        && mConnectionCallback.mConnectedCount == 0
                        && mConnectionCallback.mConnectionSuspendedCount == 0;
            }
        }.run();
    }

    @Test
    public void testReconnection() throws Throwable {
        createMediaBrowser(TEST_BROWSER_SERVICE);

        runOnMainThread(() -> {
            // Reconnect before the first connection was established.
            mMediaBrowser.connect();
            mMediaBrowser.disconnect();
        });
        resetCallbacks();
        connectMediaBrowserService();

        // Test subscribe.
        resetCallbacks();
        runOnMainThread(() -> mMediaBrowser.subscribe(StubMediaBrowserService.MEDIA_ID_ROOT,
                mSubscriptionCallback));
        new PollingCheck(TIME_OUT_MS) {
            @Override
            protected boolean check() {
                return mSubscriptionCallback.mChildrenLoadedCount > 0;
            }
        }.run();

        // Test getItem.
        resetCallbacks();
        runOnMainThread(() -> mMediaBrowser.getItem(StubMediaBrowserService.MEDIA_ID_CHILDREN[0],
                mItemCallback));
        new PollingCheck(TIME_OUT_MS) {
            @Override
            protected boolean check() {
                return mItemCallback.mLastMediaItem != null;
            }
        }.run();

        // Reconnect after connection was established.
        disconnectMediaBrowser();
        resetCallbacks();
        connectMediaBrowserService();

        // Test getItem.
        resetCallbacks();
        runOnMainThread(() -> mMediaBrowser.getItem(StubMediaBrowserService.MEDIA_ID_CHILDREN[0],
                mItemCallback));
        new PollingCheck(TIME_OUT_MS) {
            @Override
            protected boolean check() {
                return mItemCallback.mLastMediaItem != null;
            }
        }.run();
    }

    @Test
    public void testConnectionCallbackNotCalledAfterDisconnect() throws Throwable {
        createMediaBrowser(TEST_BROWSER_SERVICE);
        runOnMainThread(() -> {
            mMediaBrowser.connect();
            mMediaBrowser.disconnect();
        });
        resetCallbacks();
        try {
            Thread.sleep(SLEEP_MS);
        } catch (InterruptedException e) {
            assertWithMessage("Unexpected InterruptedException occurred.").fail();
        }

        assertThat(mConnectionCallback.mConnectedCount)
                .isEqualTo(0);
        assertThat(mConnectionCallback.mConnectionFailedCount)
                .isEqualTo(0);
        assertThat(mConnectionCallback.mConnectionSuspendedCount)
                .isEqualTo(0);
    }

    @Test
    public void testSubscribe() throws Throwable {
        resetCallbacks();
        createMediaBrowser(TEST_BROWSER_SERVICE);
        connectMediaBrowserService();
        runOnMainThread(() -> mMediaBrowser.subscribe(StubMediaBrowserService.MEDIA_ID_ROOT,
                mSubscriptionCallback));
        new PollingCheck(TIME_OUT_MS) {
            @Override
            protected boolean check() {
                return mSubscriptionCallback.mChildrenLoadedCount > 0;
            }
        }.run();

        assertThat(mSubscriptionCallback.mLastParentId)
                .isEqualTo(StubMediaBrowserService.MEDIA_ID_ROOT);
        assertThat(mSubscriptionCallback.mLastChildMediaItems)
                .hasSize(StubMediaBrowserService.MEDIA_ID_CHILDREN.length);

        assertThat(mSubscriptionCallback.mLastChildMediaItems)
                .comparingElementsUsing(MEDIA_ITEM_HAS_ID)
                .containsExactlyElementsIn(StubMediaBrowserService.MEDIA_ID_CHILDREN)
                .inOrder();

        // Test unsubscribe.
        resetCallbacks();
        runOnMainThread(() -> mMediaBrowser.unsubscribe(StubMediaBrowserService.MEDIA_ID_ROOT));

        // After unsubscribing, make StubMediaBrowserService notify that the children are changed.
        StubMediaBrowserService.sInstance.notifyChildrenChanged(
                StubMediaBrowserService.MEDIA_ID_ROOT);
        try {
            Thread.sleep(SLEEP_MS);
        } catch (InterruptedException e) {
            assertWithMessage("Unexpected InterruptedException occurred.").fail();
        }
        // onChildrenLoaded should not be called.
        assertThat(mSubscriptionCallback.mChildrenLoadedCount)
                .isEqualTo(0);
    }

    @Test
    public void testSubscribeWithIllegalArguments() throws Throwable {
        createMediaBrowser(TEST_BROWSER_SERVICE);

        runOnMainThread(() -> {
            assertThrows(IllegalArgumentException.class, () -> {
                final String nullMediaId = null;
                mMediaBrowser.subscribe(nullMediaId, mSubscriptionCallback);
            });

            assertThrows(IllegalArgumentException.class, () -> {
                final String emptyMediaId = "";
                mMediaBrowser.subscribe(emptyMediaId, mSubscriptionCallback);
            });

            assertThrows(IllegalArgumentException.class, () -> {
                final MediaBrowser.SubscriptionCallback nullCallback = null;
                mMediaBrowser.subscribe(StubMediaBrowserService.MEDIA_ID_ROOT, nullCallback);
            });

            assertThrows(IllegalArgumentException.class, () -> {
                final Bundle nullOptions = null;
                mMediaBrowser.subscribe(StubMediaBrowserService.MEDIA_ID_ROOT, nullOptions,
                        mSubscriptionCallback);
            });
        });
    }

    @Test
    public void testSubscribeWithOptions() throws Throwable {
        createMediaBrowser(TEST_BROWSER_SERVICE);
        connectMediaBrowserService();
        final int pageSize = 3;
        final int lastPage = (StubMediaBrowserService.MEDIA_ID_CHILDREN.length - 1) / pageSize;
        Bundle options = new Bundle();
        options.putInt(MediaBrowser.EXTRA_PAGE_SIZE, pageSize);
        for (int page = 0; page <= lastPage; ++page) {
            resetCallbacks();
            options.putInt(MediaBrowser.EXTRA_PAGE, page);
            runOnMainThread(() -> mMediaBrowser.subscribe(StubMediaBrowserService.MEDIA_ID_ROOT,
                    options, mSubscriptionCallback));
            new PollingCheck(TIME_OUT_MS) {
                @Override
                protected boolean check() {
                    return mSubscriptionCallback.mChildrenLoadedWithOptionCount > 0;
                }
            }.run();
            assertThat(mSubscriptionCallback.mLastParentId)
                    .isEqualTo(StubMediaBrowserService.MEDIA_ID_ROOT);
            if (page != lastPage) {
                assertThat(mSubscriptionCallback.mLastChildMediaItems).hasSize(pageSize);
            } else {
                assertThat(mSubscriptionCallback.mLastChildMediaItems)
                        .hasSize((StubMediaBrowserService.MEDIA_ID_CHILDREN.length - 1)
                                % pageSize + 1);
            }

            final int lastChildMediaItemsCount = mSubscriptionCallback.mLastChildMediaItems.size();

            final String[] expectedMediaIds = getMediaIdsCurrentPage(
                    StubMediaBrowserService.MEDIA_ID_CHILDREN,
                    page, pageSize, lastChildMediaItemsCount);

            // Check whether all the items in the current page are loaded.
            assertThat(mSubscriptionCallback.mLastChildMediaItems)
                    .comparingElementsUsing(MEDIA_ITEM_HAS_ID)
                    .containsExactlyElementsIn(expectedMediaIds)
                    .inOrder();
        }

        // Test unsubscribe with callback argument.
        resetCallbacks();
        runOnMainThread(() -> mMediaBrowser.unsubscribe(StubMediaBrowserService.MEDIA_ID_ROOT,
                mSubscriptionCallback));

        // After unsubscribing, make StubMediaBrowserService notify that the children are changed.
        StubMediaBrowserService.sInstance.notifyChildrenChanged(
                StubMediaBrowserService.MEDIA_ID_ROOT);
        try {
            Thread.sleep(SLEEP_MS);
        } catch (InterruptedException e) {
            assertWithMessage("Unexpected InterruptedException occurred.").fail();
        }
        // onChildrenLoaded should not be called.
        assertThat(mSubscriptionCallback.mChildrenLoadedCount).isEqualTo(0);
    }

    @Test
    public void testSubscribeInvalidItem() throws Throwable {
        resetCallbacks();
        createMediaBrowser(TEST_BROWSER_SERVICE);
        connectMediaBrowserService();
        runOnMainThread(() -> mMediaBrowser.subscribe(
                StubMediaBrowserService.MEDIA_ID_INVALID, mSubscriptionCallback));
        new PollingCheck(TIME_OUT_MS) {
            @Override
            protected boolean check() {
                return mSubscriptionCallback.mLastErrorId != null;
            }
        }.run();

        assertThat(mSubscriptionCallback.mLastErrorId)
                .isEqualTo(StubMediaBrowserService.MEDIA_ID_INVALID);
    }

    @Test
    public void testSubscribeInvalidItemWithOptions() throws Throwable {
        resetCallbacks();
        createMediaBrowser(TEST_BROWSER_SERVICE);
        connectMediaBrowserService();

        final int pageSize = 5;
        final int page = 2;
        Bundle options = new Bundle();
        options.putInt(MediaBrowser.EXTRA_PAGE_SIZE, pageSize);
        options.putInt(MediaBrowser.EXTRA_PAGE, page);
        runOnMainThread(() -> mMediaBrowser.subscribe(StubMediaBrowserService.MEDIA_ID_INVALID,
                options, mSubscriptionCallback));
        new PollingCheck(TIME_OUT_MS) {
            @Override
            protected boolean check() {
                return mSubscriptionCallback.mLastErrorId != null;
            }
        }.run();

        assertThat(mSubscriptionCallback.mLastErrorId)
                .isEqualTo(StubMediaBrowserService.MEDIA_ID_INVALID);
        assertThat(mSubscriptionCallback.mLastOptions.getInt(MediaBrowser.EXTRA_PAGE))
                .isEqualTo(page);
        assertThat(mSubscriptionCallback.mLastOptions.getInt(MediaBrowser.EXTRA_PAGE_SIZE))
                .isEqualTo(pageSize);
    }

    @Ignore // TODO(b/291800179): Diagnose flakiness and re-enable.
    @Test
    public void testSubscriptionCallbackNotCalledAfterDisconnect() throws Throwable {
        createMediaBrowser(TEST_BROWSER_SERVICE);
        connectMediaBrowserService();
        runOnMainThread(() -> {
            mMediaBrowser.subscribe(StubMediaBrowserService.MEDIA_ID_ROOT, mSubscriptionCallback);
            mMediaBrowser.disconnect();
        });
        resetCallbacks();
        StubMediaBrowserService.sInstance.notifyChildrenChanged(
                StubMediaBrowserService.MEDIA_ID_ROOT);
        try {
            Thread.sleep(SLEEP_MS);
        } catch (InterruptedException e) {
            assertWithMessage("Unexpected InterruptedException occurred.").fail();
        }
        assertThat(mSubscriptionCallback.mChildrenLoadedCount).isEqualTo(0);
        assertThat(mSubscriptionCallback.mChildrenLoadedWithOptionCount).isEqualTo(0);
        assertThat(mSubscriptionCallback.mLastParentId).isNull();
    }

    @Test
    public void testUnsubscribeWithIllegalArguments() throws Throwable {
        createMediaBrowser(TEST_BROWSER_SERVICE);
        runOnMainThread(() -> {
            assertThrows(IllegalArgumentException.class, () -> {
                final String nullMediaId = null;
                mMediaBrowser.unsubscribe(nullMediaId);
            });

            assertThrows(IllegalArgumentException.class, () -> {
                final String emptyMediaId = "";
                mMediaBrowser.unsubscribe(emptyMediaId);
            });

            assertThrows(IllegalArgumentException.class, () -> {
                final MediaBrowser.SubscriptionCallback nullCallback = null;
                mMediaBrowser.unsubscribe(StubMediaBrowserService.MEDIA_ID_ROOT, nullCallback);
            });
        });
    }

    @Test
    public void testUnsubscribeForMultipleSubscriptions() throws Throwable {
        createMediaBrowser(TEST_BROWSER_SERVICE);
        connectMediaBrowserService();
        final List<StubSubscriptionCallback> subscriptionCallbacks = new ArrayList<>();
        final int pageSize = 1;

        // Subscribe four pages, one item per page.
        for (int page = 0; page < 4; page++) {
            final StubSubscriptionCallback callback = new StubSubscriptionCallback();
            subscriptionCallbacks.add(callback);

            Bundle options = new Bundle();
            options.putInt(MediaBrowser.EXTRA_PAGE, page);
            options.putInt(MediaBrowser.EXTRA_PAGE_SIZE, pageSize);
            runOnMainThread(() -> mMediaBrowser.subscribe(StubMediaBrowserService.MEDIA_ID_ROOT,
                    options, callback));

            // Each onChildrenLoaded() must be called.
            new PollingCheck(TIME_OUT_MS) {
                @Override
                protected boolean check() {
                    return callback.mChildrenLoadedWithOptionCount == 1;
                }
            }.run();
        }

        // Reset callbacks and unsubscribe.
        for (StubSubscriptionCallback callback : subscriptionCallbacks) {
            callback.reset();
        }
        runOnMainThread(() -> mMediaBrowser.unsubscribe(StubMediaBrowserService.MEDIA_ID_ROOT));

        // After unsubscribing, make StubMediaBrowserService notify that the children are changed.
        StubMediaBrowserService.sInstance.notifyChildrenChanged(
                StubMediaBrowserService.MEDIA_ID_ROOT);
        try {
            Thread.sleep(SLEEP_MS);
        } catch (InterruptedException e) {
            assertWithMessage("Unexpected InterruptedException occurred.").fail();
        }

        // onChildrenLoaded should not be called.
        for (StubSubscriptionCallback callback : subscriptionCallbacks) {
            assertThat(callback.mChildrenLoadedWithOptionCount).isEqualTo(0);
        }
    }

    @Test
    public void testUnsubscribeWithSubscriptionCallbackForMultipleSubscriptions() throws Throwable {
        createMediaBrowser(TEST_BROWSER_SERVICE);
        connectMediaBrowserService();
        final List<StubSubscriptionCallback> subscriptionCallbacks = new ArrayList<>();
        final int pageSize = 1;

        // Subscribe four pages, one item per page.
        for (int page = 0; page < 4; page++) {
            final StubSubscriptionCallback callback = new StubSubscriptionCallback();
            subscriptionCallbacks.add(callback);

            Bundle options = new Bundle();
            options.putInt(MediaBrowser.EXTRA_PAGE, page);
            options.putInt(MediaBrowser.EXTRA_PAGE_SIZE, pageSize);
            runOnMainThread(() -> mMediaBrowser.subscribe(StubMediaBrowserService.MEDIA_ID_ROOT,
                    options, callback));

            // Each onChildrenLoaded() must be called.
            new PollingCheck(TIME_OUT_MS) {
                @Override
                protected boolean check() {
                    return callback.mChildrenLoadedWithOptionCount == 1;
                }
            }.run();
        }

        // Unsubscribe existing subscriptions one-by-one.
        final int[] orderOfRemovingCallbacks = {2, 0, 3, 1};
        for (int i = 0; i < orderOfRemovingCallbacks.length; i++) {
            // Reset callbacks
            for (StubSubscriptionCallback callback : subscriptionCallbacks) {
                callback.reset();
            }

            final int index = i;
            runOnMainThread(() -> {
                // Remove one subscription
                mMediaBrowser.unsubscribe(StubMediaBrowserService.MEDIA_ID_ROOT,
                        subscriptionCallbacks.get(orderOfRemovingCallbacks[index]));
            });

            // Make StubMediaBrowserService notify that the children are changed.
            StubMediaBrowserService.sInstance.notifyChildrenChanged(
                    StubMediaBrowserService.MEDIA_ID_ROOT);
            try {
                Thread.sleep(SLEEP_MS);
            } catch (InterruptedException e) {
                assertWithMessage("Unexpected InterruptedException occurred.").fail();
            }

            // Only the remaining subscriptionCallbacks should be called.
            for (int j = 0; j < 4; j++) {
                int childrenLoadedWithOptionsCount = subscriptionCallbacks
                        .get(orderOfRemovingCallbacks[j]).mChildrenLoadedWithOptionCount;
                if (j <= i) {
                    assertThat(childrenLoadedWithOptionsCount)
                            .isEqualTo(0);
                } else {
                    assertThat(childrenLoadedWithOptionsCount)
                            .isEqualTo(1);
                }
            }
        }
    }

    @Test
    public void testGetItem() throws Throwable {
        resetCallbacks();
        createMediaBrowser(TEST_BROWSER_SERVICE);
        connectMediaBrowserService();

        runOnMainThread(() -> mMediaBrowser.getItem(StubMediaBrowserService.MEDIA_ID_CHILDREN[0],
                mItemCallback));
        new PollingCheck(TIME_OUT_MS) {
            @Override
            protected boolean check() {
                return mItemCallback.mLastMediaItem != null;
            }
        }.run();

        assertThat(mItemCallback.mLastMediaItem.getMediaId())
                .isEqualTo(StubMediaBrowserService.MEDIA_ID_CHILDREN[0]);
    }

    @Test
    public void testGetItemThrowsIAE() throws Throwable {
        resetCallbacks();
        createMediaBrowser(TEST_BROWSER_SERVICE);

        runOnMainThread(() -> {
            assertThrows(IllegalArgumentException.class, () -> {
                // Calling getItem() with empty mediaId will throw IAE.
                mMediaBrowser.getItem("",  mItemCallback);
            });

            assertThrows(IllegalArgumentException.class, () -> {
                // Calling getItem() with null mediaId will throw IAE.
                mMediaBrowser.getItem(null,  mItemCallback);
            });

            assertThrows(IllegalArgumentException.class, () -> {
                // Calling getItem() with null itemCallback will throw IAE.
                mMediaBrowser.getItem("media_id",  null);
            });
        });
    }

    @Test
    public void testGetItemWhileNotConnected() throws Throwable {
        resetCallbacks();
        createMediaBrowser(TEST_BROWSER_SERVICE);

        final String mediaId = "test_media_id";
        runOnMainThread(() -> mMediaBrowser.getItem(mediaId, mItemCallback));

        // Calling getItem while not connected will invoke ItemCallback.onError().
        new PollingCheck(TIME_OUT_MS) {
            @Override
            protected boolean check() {
                return mItemCallback.mLastErrorId != null;
            }
        }.run();

        assertThat(mItemCallback.mLastErrorId)
                .isEqualTo(mediaId);
    }

    @Test
    public void testGetItemFailure() throws Throwable {
        resetCallbacks();
        createMediaBrowser(TEST_BROWSER_SERVICE);
        connectMediaBrowserService();
        runOnMainThread(() -> mMediaBrowser.getItem(StubMediaBrowserService.MEDIA_ID_INVALID,
                mItemCallback));
        new PollingCheck(TIME_OUT_MS) {
            @Override
            protected boolean check() {
                return mItemCallback.mLastErrorId != null;
            }
        }.run();

        assertThat(mItemCallback.mLastErrorId)
                .isEqualTo(StubMediaBrowserService.MEDIA_ID_INVALID);
    }

    @Test
    public void testItemCallbackNotCalledAfterDisconnect() throws Throwable {
        createMediaBrowser(TEST_BROWSER_SERVICE);
        connectMediaBrowserService();
        runOnMainThread(() -> {
            mMediaBrowser.getItem(StubMediaBrowserService.MEDIA_ID_CHILDREN[0], mItemCallback);
            mMediaBrowser.disconnect();
        });
        resetCallbacks();
        try {
            Thread.sleep(SLEEP_MS);
        } catch (InterruptedException e) {
            assertWithMessage("Unexpected InterruptedException occurred.").fail();
        }
        assertThat(mItemCallback.mLastMediaItem).isNull();
        assertThat(mItemCallback.mLastErrorId).isNull();
    }

    private void createMediaBrowser(final ComponentName component) throws Throwable {
        runOnMainThread(() -> mMediaBrowser = new MediaBrowser(
                getInstrumentation().getTargetContext(), component, mConnectionCallback, null));
    }

    private void connectMediaBrowserService() throws Throwable {
        runOnMainThread(() -> mMediaBrowser.connect());
        new PollingCheck(TIME_OUT_MS) {
            @Override
            protected boolean check() {
                return mConnectionCallback.mConnectedCount > 0;
            }
        }.run();
    }

    private void disconnectMediaBrowser() throws Throwable {
        runOnMainThread(() -> mMediaBrowser.disconnect());
    }

    private void resetCallbacks() {
        mConnectionCallback.reset();
        mSubscriptionCallback.reset();
        mItemCallback.reset();
    }

    private void runOnMainThread(Runnable runnable) throws Throwable {
        AtomicReference<Throwable> throwableRef = new AtomicReference<>();

        getInstrumentation().runOnMainSync(() -> {
            try {
                runnable.run();
            } catch (Throwable t) {
                throwableRef.set(t);
            }
        });

        Throwable t = throwableRef.get();
        if (t != null) {
            throw t;
        }
    }

    private static String[] getMediaIdsCurrentPage(
            String[] mediaIds, int pageIndex, int pageSize, int itemsCount) {
        final int pageOffset = pageIndex * pageSize;
        return Arrays.copyOfRange(mediaIds, pageOffset, pageOffset + itemsCount);
    }

    private static class StubConnectionCallback extends MediaBrowser.ConnectionCallback {
        volatile int mConnectedCount;
        volatile int mConnectionFailedCount;
        volatile int mConnectionSuspendedCount;

        public void reset() {
            mConnectedCount = 0;
            mConnectionFailedCount = 0;
            mConnectionSuspendedCount = 0;
        }

        @Override
        public void onConnected() {
            mConnectedCount++;
        }

        @Override
        public void onConnectionFailed() {
            mConnectionFailedCount++;
        }

        @Override
        public void onConnectionSuspended() {
            mConnectionSuspendedCount++;
        }
    }

    private static class StubSubscriptionCallback extends MediaBrowser.SubscriptionCallback {
        private volatile int mChildrenLoadedCount;
        private volatile int mChildrenLoadedWithOptionCount;
        private volatile String mLastErrorId;
        private volatile String mLastParentId;
        private volatile Bundle mLastOptions;
        private volatile List<MediaBrowser.MediaItem> mLastChildMediaItems;

        public void reset() {
            mChildrenLoadedCount = 0;
            mChildrenLoadedWithOptionCount = 0;
            mLastErrorId = null;
            mLastParentId = null;
            mLastOptions = null;
            mLastChildMediaItems = null;
        }

        @Override
        public void onChildrenLoaded(String parentId, List<MediaBrowser.MediaItem> children) {
            mChildrenLoadedCount++;
            mLastParentId = parentId;
            mLastChildMediaItems = children;
        }

        @Override
        public void onChildrenLoaded(String parentId, List<MediaBrowser.MediaItem> children,
                Bundle options) {
            mChildrenLoadedWithOptionCount++;
            mLastParentId = parentId;
            mLastOptions = options;
            mLastChildMediaItems = children;
        }

        @Override
        public void onError(String id) {
            mLastErrorId = id;
        }

        @Override
        public void onError(String id, Bundle options) {
            mLastErrorId = id;
            mLastOptions = options;
        }
    }

    private static class StubItemCallback extends MediaBrowser.ItemCallback {
        private volatile MediaBrowser.MediaItem mLastMediaItem;
        private volatile String mLastErrorId;

        public void reset() {
            mLastMediaItem = null;
            mLastErrorId = null;
        }

        @Override
        public void onItemLoaded(MediaItem item) {
            mLastMediaItem = item;
        }

        @Override
        public void onError(String id) {
            mLastErrorId = id;
        }
    }
}
