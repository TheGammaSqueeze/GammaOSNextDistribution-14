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
package android.autofillservice.cts.dialog;


import static android.autofillservice.cts.testcore.CannedFillResponse.NO_RESPONSE;
import static android.autofillservice.cts.testcore.Helper.assertHasFlags;
import static android.autofillservice.cts.testcore.Helper.setFillDialogHints;
import static android.service.autofill.FillRequest.FLAG_SUPPORTS_FILL_DIALOG;

import static org.testng.Assert.assertThrows;

import android.autofillservice.cts.activities.MyWebView;
import android.autofillservice.cts.activities.WebViewActivity;
import android.autofillservice.cts.commontests.AbstractWebViewTestCase;
import android.autofillservice.cts.testcore.AutofillActivityTestRule;
import android.autofillservice.cts.testcore.Helper;
import android.autofillservice.cts.testcore.InstrumentedAutoFillService;
import android.platform.test.annotations.AppModeFull;
import android.util.Log;

import org.junit.Assume;
import org.junit.Test;

public class WebViewActivityTest extends AbstractWebViewTestCase<WebViewActivity> {

    private static final String TAG = "WebViewActivityTest";

    private WebViewActivity mActivity;

    @Override
    protected AutofillActivityTestRule<WebViewActivity> getActivityRule() {
        return new AutofillActivityTestRule<WebViewActivity>(WebViewActivity.class) {

            // TODO(b/111838239): latest WebView implementation calls AutofillManager.isEnabled() to
            // disable autofill for optimization when it returns false, and unfortunately the value
            // returned by that method does not change when the service is enabled / disabled, so we
            // need to start enable the service before launching the activity.
            // Once that's fixed, remove this overridden method.
            @Override
            protected void beforeActivityLaunched() {
                super.beforeActivityLaunched();
                Log.i(TAG, "Setting service before launching the activity");
                enableService();
                setFillDialogHints(sContext, "email:postalAddress:postalCode");
            }

            @Override
            protected void afterActivityLaunched() {
                mActivity = getActivity();
            }
        };
    }

    @Test
    @AppModeFull(reason = "LoginActivityTest is enough")
    public void testViewReady_hintsMatch() throws Exception {
        // TODO(b/226240255) WebView does not inform the autofill service about editText (re-)entry
        Assume.assumeFalse(isPreventImeStartup());

        // Enable the instrumented service
        enableService();

        // Load WebView
        final MyWebView myWebView = mActivity.loadWebView(mUiBot);
        // Validation check to make sure autofill is enabled in the application context
        Helper.assertAutofillEnabled(myWebView.getContext(), true);

        // Set expectations.
        sReplier.addResponse(NO_RESPONSE);

        mActivity.notifyViewReady(new String[]{"email"});

        // Check onFillRequest has the flag: FLAG_SUPPORTS_FILL_DIALOG
        final InstrumentedAutoFillService.FillRequest fillRequest = sReplier.getNextFillRequest();
        assertHasFlags(fillRequest.flags, FLAG_SUPPORTS_FILL_DIALOG);
    }

    @Test
    @AppModeFull(reason = "LoginActivityTest is enough")
    public void testTwoViewReady_hintsMatchSecondView() throws Exception {
        // TODO(b/226240255) WebView does not inform the autofill service about editText (re-)entry
        Assume.assumeFalse(isPreventImeStartup());

        // Enable the instrumented service
        enableService();

        // Load WebView
        final MyWebView myWebView = mActivity.loadWebView(mUiBot);
        // Validation check to make sure autofill is enabled in the application context
        Helper.assertAutofillEnabled(myWebView.getContext(), true);

        // Set expectations.
        sReplier.addResponse(NO_RESPONSE);

        mActivity.notifyViewReady(new String[]{"phone"}, new String[]{"email"});

        // Check onFillRequest has the flag: FLAG_SUPPORTS_FILL_DIALOG
        final InstrumentedAutoFillService.FillRequest fillRequest = sReplier.getNextFillRequest();
        assertHasFlags(fillRequest.flags, FLAG_SUPPORTS_FILL_DIALOG);
    }

    @Test
    @AppModeFull(reason = "LoginActivityTest is enough")
    public void testViewReady_hintsMatchUpperCase() throws Exception {
        // TODO(b/226240255) WebView does not inform the autofill service about editText (re-)entry
        Assume.assumeFalse(isPreventImeStartup());

        // Enable the instrumented service
        enableService();

        // Load WebView
        final MyWebView myWebView = mActivity.loadWebView(mUiBot);
        // Validation check to make sure autofill is enabled in the application context
        Helper.assertAutofillEnabled(myWebView.getContext(), true);

        // Set expectations.
        sReplier.addResponse(NO_RESPONSE);

        mActivity.notifyViewReady(new String[]{"EMAIL"});

        // Check onFillRequest has the flag: FLAG_SUPPORTS_FILL_DIALOG
        final InstrumentedAutoFillService.FillRequest fillRequest = sReplier.getNextFillRequest();
        assertHasFlags(fillRequest.flags, FLAG_SUPPORTS_FILL_DIALOG);
    }

    @Test
    @AppModeFull(reason = "LoginActivityTest is enough")
    public void testViewReady_hintsNotMatch_noFillRequest() throws Exception {
        // TODO(b/226240255) WebView does not inform the autofill service about editText (re-)entry
        Assume.assumeFalse(isPreventImeStartup());

        // Enable the instrumented service
        enableService();

        // Load WebView
        final MyWebView myWebView = mActivity.loadWebView(mUiBot);
        // Validation check to make sure autofill is enabled in the application context
        Helper.assertAutofillEnabled(myWebView.getContext(), true);

        // Set expectations.
        sReplier.addResponse(NO_RESPONSE);

        mActivity.notifyViewReady(new String[]{"phone"});

        sReplier.assertNoUnhandledFillRequests();
    }

    @Test
    public void testViewReady_nullInfo() throws Exception {
        // TODO(b/226240255) WebView does not inform the autofill service about editText (re-)entry
        Assume.assumeFalse(isPreventImeStartup());

        // Enable the instrumented service
        enableService();

        // Load WebView
        final MyWebView myWebView = mActivity.loadWebView(mUiBot);
        // Validation check to make sure autofill is enabled in the application context
        Helper.assertAutofillEnabled(myWebView.getContext(), true);

        assertThrows(NullPointerException.class,
                () ->mActivity.notifyViewReadyWithNullInfo());
    }

    @Test
    public void testViewReady_emptyInfo() throws Exception {
        // TODO(b/226240255) WebView does not inform the autofill service about editText (re-)entry
        Assume.assumeFalse(isPreventImeStartup());

        // Enable the instrumented service
        enableService();

        // Load WebView
        final MyWebView myWebView = mActivity.loadWebView(mUiBot);
        // Validation check to make sure autofill is enabled in the application context
        Helper.assertAutofillEnabled(myWebView.getContext(), true);

        assertThrows(IllegalArgumentException.class,
                () ->mActivity.notifyViewReadyWithEmptyInfo());
    }
}
