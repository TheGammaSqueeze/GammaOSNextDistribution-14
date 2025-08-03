/*
 * Copyright (C) 2009 The Android Open Source Project
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

package android.webkit.cts;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.platform.test.annotations.AppModeFull;
import android.webkit.HttpAuthHandler;
import android.webkit.WebView;
import android.webkit.cts.WebViewSyncLoader.WaitForLoadedClient;

import androidx.test.ext.junit.rules.ActivityScenarioRule;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.MediumTest;

import com.android.compatibility.common.util.NullWebViewUtils;

import org.apache.http.HttpStatus;
import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@AppModeFull
@MediumTest
@RunWith(AndroidJUnit4.class)
public class HttpAuthHandlerTest extends SharedWebViewTest {
    private static final long TIMEOUT = 10000;

    private static final String WRONG_USERNAME = "wrong_user";
    private static final String WRONG_PASSWORD = "wrong_password";
    private static final String CORRECT_USERNAME = CtsTestServer.AUTH_USER;
    private static final String CORRECT_PASSWORD = CtsTestServer.AUTH_PASS;

    private SharedSdkWebServer mWebServer;
    private WebViewOnUiThread mOnUiThread;

    @Rule
    public ActivityScenarioRule mActivityScenarioRule =
            new ActivityScenarioRule(WebViewCtsActivity.class);

    @Before
    public void setUp() throws Exception {
        WebView webview = getTestEnvironment().getWebView();
        if (webview != null) {
            mOnUiThread = new WebViewOnUiThread(webview);
        }

        mWebServer = getTestEnvironment().getSetupWebServer(SslMode.INSECURE);
    }

    @After
    public void tearDown() throws Exception {
        if (mOnUiThread != null) {
            mOnUiThread.cleanUp();
        }
        if (mWebServer != null) {
            mWebServer.shutdown();
        }
    }

    @Override
    protected SharedWebViewTestEnvironment createTestEnvironment() {
        Assume.assumeTrue("WebView is not available", NullWebViewUtils.isWebViewAvailable());

        SharedWebViewTestEnvironment.Builder builder = new SharedWebViewTestEnvironment.Builder();

        mActivityScenarioRule
                .getScenario()
                .onActivity(
                        activity -> {
                            WebView webView = ((WebViewCtsActivity) activity).getWebView();
                            builder.setHostAppInvoker(
                                            SharedWebViewTestEnvironment.createHostAppInvoker(
                                                activity))
                                    .setContext(activity)
                                    .setWebView(webView);
                        });

        return builder.build();
    }

    private class ProceedHttpAuthClient extends WaitForLoadedClient {
        String realm;
        boolean useHttpAuthUsernamePassword;

        private int mMaxAuthAttempts;
        private String mUser;
        private String mPassword;
        private int mAuthCount;

        ProceedHttpAuthClient(int maxAuthAttempts, String user, String password) {
            super(mOnUiThread);
            mMaxAuthAttempts = maxAuthAttempts;
            mUser = user;
            mPassword = password;
        }

        @Override
        public void onReceivedHttpAuthRequest(WebView view,
                HttpAuthHandler handler, String host, String realm) {
            if (++mAuthCount > mMaxAuthAttempts) {
                handler.cancel();
                return;
            }

            this.realm = realm;
            this.useHttpAuthUsernamePassword = handler.useHttpAuthUsernamePassword();

            handler.proceed(mUser, mPassword);
        }
    }

    private class CancelHttpAuthClient extends WaitForLoadedClient {
        String realm;

        CancelHttpAuthClient() {
            super(mOnUiThread);
        }

        @Override
        public void onReceivedHttpAuthRequest(WebView view,
                HttpAuthHandler handler, String host, String realm) {
            this.realm = realm;
            handler.cancel();
        }
    }

    private void incorrectCredentialsAccessDenied(String url) throws Throwable {
        ProceedHttpAuthClient client = new ProceedHttpAuthClient(1, WRONG_USERNAME, WRONG_PASSWORD);
        mOnUiThread.setWebViewClient(client);

        // As we're providing incorrect credentials, the page should complete but at
        // an access denied page.
        mOnUiThread.loadUrlAndWaitForCompletion(url);

        assertEquals(CtsTestServer.AUTH_REALM, client.realm);
        assertEquals(CtsTestServer.getReasonString(HttpStatus.SC_UNAUTHORIZED), mOnUiThread.getTitle());
    }

    private void missingCredentialsAccessDenied(String url) throws Throwable {
        ProceedHttpAuthClient client = new ProceedHttpAuthClient(1, null, null);
        mOnUiThread.setWebViewClient(client);

        // As we're providing no credentials, the page should complete but at
        // an access denied page.
        mOnUiThread.loadUrlAndWaitForCompletion(url);

        assertEquals(CtsTestServer.AUTH_REALM, client.realm);
        assertEquals(CtsTestServer.getReasonString(HttpStatus.SC_UNAUTHORIZED), mOnUiThread.getTitle());
    }

    private void correctCredentialsAccessGranted(String url) throws Throwable {
        ProceedHttpAuthClient client = new ProceedHttpAuthClient(1, CORRECT_USERNAME, CORRECT_PASSWORD);
        mOnUiThread.setWebViewClient(client);

        // As we're providing valid credentials, the page should complete and
        // at the page we requested.
        mOnUiThread.loadUrlAndWaitForCompletion(url);

        assertEquals(CtsTestServer.AUTH_REALM, client.realm);
        assertEquals(TestHtmlConstants.HELLO_WORLD_TITLE, mOnUiThread.getTitle());
    }

    @Test
    public void testProceed() throws Throwable {
        String url = mWebServer.getAuthAssetUrl(TestHtmlConstants.HELLO_WORLD_URL);

        incorrectCredentialsAccessDenied(url);
        missingCredentialsAccessDenied(url);
        correctCredentialsAccessGranted(url);
    }

    @Test
    public void testCancel() throws Throwable {
        String url = mWebServer.getAuthAssetUrl(TestHtmlConstants.HELLO_WORLD_URL);

        CancelHttpAuthClient client = new CancelHttpAuthClient();
        mOnUiThread.setWebViewClient(client);

        mOnUiThread.loadUrlAndWaitForCompletion(url);
        assertEquals(CtsTestServer.AUTH_REALM, client.realm);
        assertEquals(CtsTestServer.getReasonString(HttpStatus.SC_UNAUTHORIZED), mOnUiThread.getTitle());
    }

    @Test
    public void testUseHttpAuthUsernamePassword() throws Throwable {
        String url = mWebServer.getAuthAssetUrl(TestHtmlConstants.HELLO_WORLD_URL);

        // Try to login once with incorrect credentials. This should cause
        // useHttpAuthUsernamePassword to be true in the callback, as at that point
        // we don't yet know that the credentials we will use are invalid.
        ProceedHttpAuthClient client = new ProceedHttpAuthClient(1, WRONG_USERNAME, WRONG_PASSWORD);
        mOnUiThread.setWebViewClient(client);
        mOnUiThread.loadUrlAndWaitForCompletion(url);
        assertEquals(CtsTestServer.AUTH_REALM, client.realm);
        assertEquals(CtsTestServer.getReasonString(HttpStatus.SC_UNAUTHORIZED), mOnUiThread.getTitle());
        assertTrue(client.useHttpAuthUsernamePassword);

        // Try to login twice with invalid credentials. This should cause
        // useHttpAuthUsernamePassword to return false, as the credentials
        // we would have stored on the first auth request
        // are not suitable for use the second time.
        client = new ProceedHttpAuthClient(2, WRONG_USERNAME, WRONG_PASSWORD);
        mOnUiThread.setWebViewClient(client);
        mOnUiThread.loadUrlAndWaitForCompletion(url);
        assertEquals(CtsTestServer.AUTH_REALM, client.realm);
        assertEquals(CtsTestServer.getReasonString(HttpStatus.SC_UNAUTHORIZED), mOnUiThread.getTitle());
        assertFalse(client.useHttpAuthUsernamePassword);
    }
}
