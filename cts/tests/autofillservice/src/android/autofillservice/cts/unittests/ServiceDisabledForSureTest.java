/*
 * Copyright (C) 2018 The Android Open Source Project
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

package android.autofillservice.cts.unittests;

import static android.autofillservice.cts.activities.OnCreateServiceStatusVerifierActivity.SERVICE_NAME;
import static android.autofillservice.cts.testcore.Helper.disableAutofillService;
import static android.autofillservice.cts.testcore.Helper.enableAutofillService;

import static com.google.common.truth.Truth.assertThat;

import android.autofillservice.cts.activities.OnCreateServiceStatusVerifierActivity;
import android.autofillservice.cts.commontests.AutoFillServiceTestCase;
import android.autofillservice.cts.testcore.AutofillActivityTestRule;
import android.autofillservice.cts.testcore.Helper;
import android.platform.test.annotations.AppModeFull;
import android.util.Log;
import android.view.autofill.AutofillManager;

import org.junit.BeforeClass;
import org.junit.Test;

/**
 * Test case that guarantee the service is disabled before the activity launches.
 */
@AppModeFull(reason = "Service-specific test")
public class ServiceDisabledForSureTest
        extends AutoFillServiceTestCase.AutoActivityLaunch<OnCreateServiceStatusVerifierActivity> {

    private static final String TAG = "ServiceDisabledForSureTest";

    private OnCreateServiceStatusVerifierActivity mActivity;

    @BeforeClass
    public static void resetService() {
        disableAutofillService();
    }

    @Override
    protected AutofillActivityTestRule<OnCreateServiceStatusVerifierActivity> getActivityRule() {
        return new AutofillActivityTestRule<OnCreateServiceStatusVerifierActivity>(
                OnCreateServiceStatusVerifierActivity.class) {
            @Override
            protected void afterActivityLaunched() {
                mActivity = getActivity();
            }
        };
    }

    @Override
    protected void prepareServicePreTest() {
        // Doesn't need to prepare the service - that was already taken care of in a @BeforeClass -
        // but to guarantee the test finishes in the proper state
        Log.v(TAG, "prepareServicePreTest(): not doing anything");
        mSafeCleanerRule.run(() ->assertThat(mActivity.getAutofillManager().isEnabled()).isFalse());
    }

    @Test
    public void testIsAutofillEnabled() throws Exception {
        mActivity.assertServiceStatusOnCreate(false);

        final AutofillManager afm = mActivity.getAutofillManager();
        Helper.assertAutofillEnabled(afm, false);

        enableAutofillService(SERVICE_NAME);
        Helper.assertAutofillEnabled(afm, true);

        disableAutofillService();
        Helper.assertAutofillEnabled(afm, false);
    }
}
