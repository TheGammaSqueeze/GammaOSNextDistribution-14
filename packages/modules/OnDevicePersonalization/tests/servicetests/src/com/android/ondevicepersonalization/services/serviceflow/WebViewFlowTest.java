/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.ondevicepersonalization.services.serviceflow;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.spy;

import android.adservices.ondevicepersonalization.EventOutputParcel;
import android.adservices.ondevicepersonalization.RequestLogRecord;
import android.content.ComponentName;
import android.content.ContentValues;
import android.content.Context;
import android.os.PersistableBundle;
import android.webkit.WebView;

import androidx.annotation.NonNull;
import androidx.test.core.app.ApplicationProvider;

import com.android.compatibility.common.util.ShellUtils;
import com.android.ondevicepersonalization.services.OnDevicePersonalizationExecutors;
import com.android.ondevicepersonalization.services.PhFlagsTestUtil;
import com.android.ondevicepersonalization.services.data.OnDevicePersonalizationDbHelper;
import com.android.ondevicepersonalization.services.data.events.EventUrlPayload;
import com.android.ondevicepersonalization.services.data.events.EventsContract;
import com.android.ondevicepersonalization.services.data.events.EventsDao;
import com.android.ondevicepersonalization.services.data.events.Query;

import com.google.common.util.concurrent.FutureCallback;

import org.jetbrains.annotations.NotNull;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;

@RunWith(JUnit4.class)
public class WebViewFlowTest {

    private static final String SERVICE_CLASS = "com.test.TestPersonalizationService";
    private final Context mContext = spy(ApplicationProvider.getApplicationContext());
    private CountDownLatch mLatch;
    private final OnDevicePersonalizationDbHelper mDbHelper =
            OnDevicePersonalizationDbHelper.getInstanceForTest(mContext);
    private EventUrlPayload mTestEventPayload;
    private EventsDao mDao;
    private WebView mWebView;
    private boolean mCallbackSuccess;
    private boolean mCallbackFailure;
    private Throwable mException;
    private FutureCallback<EventOutputParcel> mCallback;
    private static final ServiceFlowOrchestrator sSfo = ServiceFlowOrchestrator.getInstance();

    @Before
    public void setup() throws Exception {
        PhFlagsTestUtil.setUpDeviceConfigPermissions();
        ShellUtils.runShellCommand("settings put global hidden_api_policy 1");

        mDao = EventsDao.getInstanceForTest(mContext);
        Query mTestQuery = new Query.Builder()
                .setTimeMillis(1L)
                .setService(new ComponentName("pkg", "cls"))
                .setQueryData("query".getBytes(StandardCharsets.UTF_8))
                .build();
        // Insert query for FK constraint
        mDao.insertQuery(mTestQuery);

        setUpWebView();

        mLatch = new CountDownLatch(1);

        mCallback = new FutureCallback<>() {
                @Override
                public void onSuccess(EventOutputParcel result) {
                    mCallbackSuccess = true;
                    mLatch.countDown();
                }

                @Override
                public void onFailure(@NotNull Throwable t) {
                    mCallbackFailure = true;
                    mException = t;
                    mLatch.countDown();
                }
        };
    }

    @After
    public void cleanup() {
        mDbHelper.getWritableDatabase().close();
        mDbHelper.getReadableDatabase().close();
        mDbHelper.close();
    }

    @Test
    public void testNullPayload() throws Exception {
        sSfo.schedule(ServiceFlowType.WEB_VIEW_FLOW,
                mContext, ComponentName.createRelative(mContext.getPackageName(), SERVICE_CLASS),
                1L, new RequestLogRecord.Builder().addRow(new ContentValues()).build(),
                mCallback, null);
        mLatch.await();

        assertThat(mCallbackSuccess).isFalse();
        assertThat(mCallbackFailure).isTrue();
        assertThat(mException).isInstanceOf(NullPointerException.class);
        assertThat(getDbEntryCount()).isEqualTo(0);
    }

    @Test
    public void testValidPayload() throws Exception {
        mTestEventPayload =
                new EventUrlPayload(createEventParameters(), null, null);

        sSfo.schedule(ServiceFlowType.WEB_VIEW_FLOW,
                mContext, ComponentName.createRelative(mContext.getPackageName(), SERVICE_CLASS),
                1L, new RequestLogRecord.Builder().addRow(new ContentValues()).build(),
                mCallback, mTestEventPayload);
        mLatch.await();

        assertThat(mCallbackSuccess).isTrue();
        assertThat(mCallbackFailure).isFalse();
        assertThat(getDbEntryCount()).isEqualTo(1);
    }

    private void setUpWebView() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        OnDevicePersonalizationExecutors.getHandlerForMainThread().postAtFrontOfQueue(() -> {
            mWebView = new OdpWebView(mContext);
            latch.countDown();
        });
        latch.await();
    }

    static class OdpWebView extends WebView {
        private String mLastLoadedUrl;

        OdpWebView(@NonNull Context context) {
            super(context);
        }

        @Override
        public void loadUrl(String url) {
            mLastLoadedUrl = url;
        }
    }

    private static PersistableBundle createEventParameters() {
        PersistableBundle data = new PersistableBundle();
        data.putLong("x", 10);
        return data;
    }

    private int getDbEntryCount() {
        return mDbHelper.getReadableDatabase().query(EventsContract.EventsEntry.TABLE_NAME,
                null, null, null, null,
                null, null).getCount();
    }
}
