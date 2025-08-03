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
package android.autofillservice.cts.dropdown;

import static android.autofillservice.cts.activities.CheckoutActivity.ID_ADDRESS;
import static android.autofillservice.cts.activities.CheckoutActivity.ID_CC_EXPIRATION;
import static android.autofillservice.cts.activities.CheckoutActivity.ID_CC_NUMBER;
import static android.autofillservice.cts.activities.CheckoutActivity.ID_SAVE_CC;
import static android.autofillservice.cts.activities.CheckoutActivity.INDEX_ADDRESS_HOME;
import static android.autofillservice.cts.testcore.CannedFillResponse.NO_RESPONSE;
import static android.autofillservice.cts.testcore.Helper.assertListValue;
import static android.autofillservice.cts.testcore.Helper.assertTextAndValue;
import static android.autofillservice.cts.testcore.Helper.assertTextIsSanitized;
import static android.autofillservice.cts.testcore.Helper.assertToggleValue;
import static android.autofillservice.cts.testcore.Helper.findNodeByResourceId;

import android.autofillservice.cts.activities.InitializedCheckoutActivity;
import android.autofillservice.cts.commontests.AutoFillServiceTestCase;
import android.autofillservice.cts.testcore.AutofillActivityTestRule;
import android.autofillservice.cts.testcore.InstrumentedAutoFillService.FillRequest;
import android.platform.test.annotations.AppModeFull;

import org.junit.Test;

/**
 * Test case for an activity containing non-TextField views with initial values set on XML.
 */
@AppModeFull(reason = "CheckoutActivityTest() is enough")
public class InitializedCheckoutActivityTest
        extends AutoFillServiceTestCase.AutoActivityLaunch<InitializedCheckoutActivity> {

    private InitializedCheckoutActivity mCheckoutActivity;

    @Override
    protected AutofillActivityTestRule<InitializedCheckoutActivity> getActivityRule() {
        return new AutofillActivityTestRule<InitializedCheckoutActivity>(
                InitializedCheckoutActivity.class) {
            @Override
            protected void afterActivityLaunched() {
                mCheckoutActivity = getActivity();
            }
        };

    }

    @Test
    public void testSanitization() throws Exception {
        // Set service.
        enableService();

        // Set expectations.
        sReplier.addResponse(NO_RESPONSE);

        // Trigger auto-fill.
        mCheckoutActivity.onCcNumber((v) -> v.requestFocus());

        // Assert sanitization: most everything should be available...
        final FillRequest fillRequest = sReplier.getNextFillRequest();

        assertTextAndValue(findNodeByResourceId(fillRequest.structure, ID_CC_NUMBER), "4815162342");
        assertListValue(findNodeByResourceId(fillRequest.structure, ID_ADDRESS),
                INDEX_ADDRESS_HOME);
        assertToggleValue(findNodeByResourceId(fillRequest.structure, ID_SAVE_CC), true);

        // ... except Spinner, whose initial value cannot be set by resources:
        assertTextIsSanitized(fillRequest.structure, ID_CC_EXPIRATION);
    }
}
