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

package android.translation.cts;

import android.content.ComponentName;
import android.service.contentcapture.ActivityEvent;
import android.service.contentcapture.ContentCaptureService;
import android.service.contentcapture.DataShareCallback;
import android.util.ArraySet;
import android.util.Log;
import android.util.Pair;
import android.view.contentcapture.ContentCaptureContext;
import android.view.contentcapture.ContentCaptureEvent;
import android.view.contentcapture.ContentCaptureSessionId;
import android.view.contentcapture.DataRemovalRequest;
import android.view.contentcapture.DataShareRequest;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Implementation of {@link ContentCaptureService} used in CTS tests.
 */
public class CtsContentCaptureService extends ContentCaptureService {

    private static final String TAG = "CtsContentCaptureService";

    public static final String SERVICE_PACKAGE = "android.translation.cts";
    public static final String SERVICE_NAME = SERVICE_PACKAGE + "/."
            + CtsContentCaptureService.class.getSimpleName();

    public static final long GENERIC_TIMEOUT_MS = 10_000;

    private static ServiceWatcher sServiceWatcher;

    private ContentCaptureContext mContentCaptureContext;
    private final CountDownLatch mSessionCreatedLatch = new CountDownLatch(1);

    @Override
    public void onConnected() {
        Log.i(TAG, "onConnected.");

        if (sServiceWatcher != null) {
            sServiceWatcher.mService = this;
            sServiceWatcher.mConnected.countDown();
        }
    }

    @Override
    public void onDisconnected() {
        Log.i(TAG, "onDisconnected.");
        if (sServiceWatcher != null) {
            sServiceWatcher.mService = null;
            sServiceWatcher.mDisconnected.countDown();
            sServiceWatcher = null;
        }
    }

    @Override
    public void onCreateContentCaptureSession(ContentCaptureContext context,
            ContentCaptureSessionId sessionId) {
        Log.i(TAG, "onCreateContentCaptureSession.");
        mSessionCreatedLatch.countDown();
        mContentCaptureContext = context;
    }

    @Override
    public void onDestroyContentCaptureSession(ContentCaptureSessionId sessionId) {
        Log.i(TAG, "onDestroyContentCaptureSession.");
    }

    @Override
    public void onContentCaptureEvent(ContentCaptureSessionId sessionId,
            ContentCaptureEvent event) {

    }

    @Override
    public void onDataRemovalRequest(DataRemovalRequest request) {

    }

    @Override
    public void onDataShareRequest(DataShareRequest request, DataShareCallback callback) {

    }

    @Override
    public void onActivityEvent(ActivityEvent event) {

    }

    /**
     * Set the ServiceWatcher that used to monitor the service status.
     */
    public static ServiceWatcher setServiceWatcher() {
        if (sServiceWatcher != null) {
            throw new IllegalStateException("There Can Be Only One!");
        }
        sServiceWatcher = new ServiceWatcher();
        return sServiceWatcher;
    }

    /**
     * Resets the static state of this Service. Called before each test.
     */
    public static void resetStaticState() {
        sServiceWatcher = null;
    }

    /**
     * Get the ContentCaptureContext that set by {@link #onCreateContentCaptureSession}.
     */
    ContentCaptureContext getContentCaptureContext() {
        return mContentCaptureContext;
    }

    /**
     * Wait the ContentCapture session created.
     */
    void awaitSessionCreated(long timeoutMillis) {
        try {
            mSessionCreatedLatch.await(timeoutMillis, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            // do nothing
        }
    }

    /**
     * Helper class to monitor the state of the service.
     */
    public static final class ServiceWatcher {
        private final CountDownLatch mConnected = new CountDownLatch(1);
        private final CountDownLatch mDisconnected = new CountDownLatch(1);

        CtsContentCaptureService mService;
        private Pair<Set<String>, Set<ComponentName>> mAllowList;

        @NonNull
        public CtsContentCaptureService waitOnConnected() throws InterruptedException {
            await(mConnected, "not connected");

            if (mService == null) {
                throw new IllegalStateException("not connected");
            }
            if (mAllowList != null) {
                Log.d(TAG, "Allow after created: " + mAllowList);
                mService.setContentCaptureWhitelist(mAllowList.first, mAllowList.second);
            }
            return mService;
        }

        public void waitOnDisconnected() throws InterruptedException {
            await(mDisconnected, "not disconnected");
        }

        public void setAllowSelf() {
            final ArraySet<String> pkgs = new ArraySet<>(1);
            pkgs.add(SERVICE_PACKAGE);
            mAllowList = new Pair<>(pkgs, null);
        }

        private static void await(@NonNull CountDownLatch latch, @NonNull String fmt,
                @Nullable Object... args)
                throws InterruptedException {
            final boolean called = latch.await(GENERIC_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            if (!called) {
                throw new IllegalStateException(String.format(fmt, args)
                        + " in " + GENERIC_TIMEOUT_MS + "ms");
            }
        }
    }
}
