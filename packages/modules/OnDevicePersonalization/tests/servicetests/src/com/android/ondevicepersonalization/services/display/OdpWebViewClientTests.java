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

package com.android.ondevicepersonalization.services.display;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import android.adservices.ondevicepersonalization.EventOutputParcel;
import android.adservices.ondevicepersonalization.RequestLogRecord;
import android.content.ComponentName;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.PersistableBundle;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.annotation.NonNull;
import androidx.test.core.app.ApplicationProvider;

import com.android.compatibility.common.util.ShellUtils;
import com.android.dx.mockito.inline.extended.ExtendedMockito;
import com.android.ondevicepersonalization.services.OnDevicePersonalizationExecutors;
import com.android.ondevicepersonalization.services.PhFlagsTestUtil;
import com.android.ondevicepersonalization.services.data.OnDevicePersonalizationDbHelper;
import com.android.ondevicepersonalization.services.data.events.EventUrlHelper;
import com.android.ondevicepersonalization.services.data.events.EventUrlPayload;
import com.android.ondevicepersonalization.services.data.events.EventsContract;
import com.android.ondevicepersonalization.services.data.events.EventsDao;
import com.android.ondevicepersonalization.services.data.events.Query;
import com.android.ondevicepersonalization.services.fbs.EventFields;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;

import org.jetbrains.annotations.NotNull;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.mockito.MockitoSession;
import org.mockito.quality.Strictness;

import java.net.HttpURLConnection;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.CountDownLatch;

@RunWith(Parameterized.class)
public class OdpWebViewClientTests {
    private static final long QUERY_ID = 1L;
    private static final String SERVICE_CLASS = "com.test.TestPersonalizationService";
    private final Context mContext = ApplicationProvider.getApplicationContext();
    private static final byte[] RESPONSE_BYTES = {'A', 'B'};
    private EventUrlPayload mTestEventPayload;
    private final Query mTestQuery = new Query.Builder()
            .setTimeMillis(1L)
            .setService(ComponentName.createRelative(mContext.getPackageName(), SERVICE_CLASS))
            .setQueryData("query".getBytes(StandardCharsets.UTF_8))
            .build();
    private EventsDao mDao;
    private OnDevicePersonalizationDbHelper mDbHelper;
    private OdpWebView mWebView;
    private String mOpenedUrl;

    private CountDownLatch mLatch;

    @Parameterized.Parameter(0)
    public boolean mIsSipFeatureEnabled;

    private FutureCallback mTestCallback;
    private boolean mCallbackSuccess;
    private boolean mCallbackFailure;

    @Parameterized.Parameters
    public static Collection<Object[]> data() {
        return Arrays.asList(
                new Object[][] {
                        {true}, {false}
                }
        );
    }

    @Before
    public void setup() throws Exception {
        mDbHelper = OnDevicePersonalizationDbHelper.getInstanceForTest(mContext);
        mDao = EventsDao.getInstanceForTest(mContext);
        // Insert query for FK constraint
        mDao.insertQuery(mTestQuery);
        mLatch = new CountDownLatch(1);

        PhFlagsTestUtil.setUpDeviceConfigPermissions();
        ShellUtils.runShellCommand("settings put global hidden_api_policy 1");
        ShellUtils.runShellCommand(
                "device_config put on_device_personalization "
                        + "shared_isolated_process_feature_enabled "
                        + mIsSipFeatureEnabled);

        CountDownLatch latch = new CountDownLatch(1);
        OnDevicePersonalizationExecutors.getHandlerForMainThread().postAtFrontOfQueue(() -> {
            mWebView = new OdpWebView(mContext);
            latch.countDown();
        });
        latch.await();

        mTestCallback = new FutureCallback<EventOutputParcel>() {
            @Override
            public void onSuccess(EventOutputParcel result) {
                mCallbackSuccess = true;
                mLatch.countDown();
            }

            @Override
            public void onFailure(@NotNull Throwable t) {
                mCallbackFailure = true;
                mLatch.countDown();
            }
        };

        mTestEventPayload = new EventUrlPayload(
                createEventParameters(), null, null);
    }

    @After
    public void cleanup() {
        mDbHelper.getWritableDatabase().close();
        mDbHelper.getReadableDatabase().close();
        mDbHelper.close();
    }

    @Test
    public void testValidUrlOverride() throws Exception {
        WebViewClient webViewClient = getWebViewClient();
        String odpUrl = EventUrlHelper.getEncryptedOdpEventUrl(mTestEventPayload).toString();
        WebResourceRequest webResourceRequest = new OdpWebResourceRequest(Uri.parse(odpUrl));

        assertTrue(webViewClient.shouldOverrideUrlLoading(mWebView, webResourceRequest));
        mLatch.await();

        assertTrue(mCallbackSuccess);
        assertEquals(1,
                mDbHelper.getReadableDatabase().query(EventsContract.EventsEntry.TABLE_NAME, null,
                        null, null, null, null, null).getCount());
    }

    @Test
    public void testValidUrlWithNoContentIntercept() throws Exception {
        WebViewClient webViewClient = getWebViewClient();
        String odpUrl = EventUrlHelper.getEncryptedOdpEventUrl(mTestEventPayload).toString();
        WebResourceRequest webResourceRequest = new OdpWebResourceRequest(Uri.parse(odpUrl));

        WebResourceResponse response = webViewClient.shouldInterceptRequest(
                mWebView, webResourceRequest);
        mLatch.await();

        assertTrue(mCallbackSuccess);
        assertEquals(HttpURLConnection.HTTP_NO_CONTENT, response.getStatusCode());
        assertEquals(1,
                mDbHelper.getReadableDatabase().query(EventsContract.EventsEntry.TABLE_NAME, null,
                        null, null, null, null, null).getCount());
    }

    @Test
    public void testValidUrlWithResponseDataIntercept() throws Exception {
        WebViewClient webViewClient = getWebViewClient();
        String odpUrl = EventUrlHelper.getEncryptedOdpEventUrl(new EventUrlPayload(
                createEventParameters(), RESPONSE_BYTES, "image/gif")).toString();
        WebResourceRequest webResourceRequest = new OdpWebResourceRequest(Uri.parse(odpUrl));

        WebResourceResponse response = webViewClient.shouldInterceptRequest(
                mWebView, webResourceRequest);
        mLatch.await();

        assertTrue(mCallbackSuccess);
        assertEquals(HttpURLConnection.HTTP_OK, response.getStatusCode());
        assertEquals("image/gif", response.getMimeType());
        assertArrayEquals(RESPONSE_BYTES, response.getData().readAllBytes());
        assertEquals(1,
                mDbHelper.getReadableDatabase().query(EventsContract.EventsEntry.TABLE_NAME, null,
                        null, null, null, null, null).getCount());
    }

    @Test
    public void testValidUrlWithRedirect() throws Exception {
        String landingPage = "https://www.google.com";
        String odpUrl = EventUrlHelper.getEncryptedClickTrackingUrl(
                mTestEventPayload, landingPage).toString();
        WebResourceRequest webResourceRequest = new OdpWebResourceRequest(Uri.parse(odpUrl));
        WebViewClient webViewClient = getWebViewClient();

        assertTrue(webViewClient.shouldOverrideUrlLoading(mWebView, webResourceRequest));
        mLatch.await();

        assertTrue(mCallbackSuccess);
        assertEquals(landingPage, mOpenedUrl);
        assertEquals(1,
                mDbHelper.getReadableDatabase().query(EventsContract.EventsEntry.TABLE_NAME, null,
                        null, null, null, null, null).getCount());
    }

    @Test
    public void testValidUrlWithEventMetrics() throws Exception {
        WebViewClient webViewClient = getWebViewClient();
        String odpUrl = EventUrlHelper.getEncryptedOdpEventUrl(mTestEventPayload).toString();
        WebResourceRequest webResourceRequest = new OdpWebResourceRequest(Uri.parse(odpUrl));

        assertTrue(webViewClient.shouldOverrideUrlLoading(mWebView, webResourceRequest));
        mLatch.await();

        assertTrue(mCallbackSuccess);
        Cursor result =
                mDbHelper.getReadableDatabase().query(
                    EventsContract.EventsEntry.TABLE_NAME, null,
                    null, null, null, null, null);
        assertEquals(1, result.getCount());
        result.moveToFirst();
        int dataColumn = result.getColumnIndex("eventData");
        byte[] data = result.getBlob(dataColumn);
        EventFields eventFields = EventFields.getRootAsEventFields(ByteBuffer.wrap(data));
        assertEquals(1, eventFields.data().entriesLength());
        assertEquals("x", eventFields.data().entries(0).key());
        assertEquals(10, eventFields.data().entries(0).longValue());
    }

    @Test
    public void testNonOdpUrl() throws Exception {
        WebViewClient webViewClient = getWebViewClient();
        WebResourceRequest webResourceRequest = new OdpWebResourceRequest(
                Uri.parse("https://www.google.com"));

        assertNull(webViewClient.shouldInterceptRequest(mWebView, webResourceRequest));
        assertFalse(webViewClient.shouldOverrideUrlLoading(mWebView, webResourceRequest));
    }

    @Test
    public void testDefaultInjector() {
        // Assert constructor using default injector succeeds.
        new OdpWebViewClient(mContext,
                ComponentName.createRelative(mContext.getPackageName(), SERVICE_CLASS), 0,
                new RequestLogRecord.Builder().build());

        // Mock context for default injector tests.
        MockitoSession session = ExtendedMockito.mockitoSession().strictness(
                Strictness.LENIENT).startMocking();
        try {
            Context mockContext = mock(Context.class);
            OdpWebViewClient.Injector injector = new OdpWebViewClient.Injector();
            injector.openUrl("https://google.com", mockContext);
            assertEquals(injector.getExecutor(),
                    OnDevicePersonalizationExecutors.getBackgroundExecutor());
            verify(mockContext, times(1)).startActivity(any());
        } finally {
            session.finishMocking();
        }
    }

    class TestInjector extends OdpWebViewClient.Injector {
        ListeningExecutorService getExecutor() {
            return MoreExecutors.newDirectExecutorService();
        }

        FutureCallback<EventOutputParcel> getFutureCallback() {
            return mTestCallback;
        }

        void openUrl(String url, Context context) {
            mOpenedUrl = url;
        }
    }

    private WebViewClient getWebViewClient() {
        RequestLogRecord logRecord =
                new RequestLogRecord.Builder().addRow(new ContentValues()).build();
        return getWebViewClient(QUERY_ID, logRecord);
    }

    private WebViewClient getWebViewClient(long queryId, RequestLogRecord logRecord) {
        return new OdpWebViewClient(mContext,
                ComponentName.createRelative(mContext.getPackageName(), SERVICE_CLASS),
                queryId, logRecord, new TestInjector());
    }

    private static PersistableBundle createEventParameters() {
        PersistableBundle data = new PersistableBundle();
        data.putLong("x", 10);
        return data;
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

    static class OdpWebResourceRequest implements WebResourceRequest {
        Uri mUri;

        OdpWebResourceRequest(Uri uri) {
            this.mUri = uri;
        }

        @Override
        public Uri getUrl() {
            return mUri;
        }

        @Override
        public boolean isForMainFrame() {
            return false;
        }

        @Override
        public boolean isRedirect() {
            return false;
        }

        @Override
        public boolean hasGesture() {
            return false;
        }

        @Override
        public String getMethod() {
            return null;
        }

        @Override
        public Map<String, String> getRequestHeaders() {
            return null;
        }
    }
}
