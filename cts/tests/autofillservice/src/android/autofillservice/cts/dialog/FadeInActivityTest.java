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

package android.autofillservice.cts.dialog;

import static android.autofillservice.cts.testcore.Helper.ID_PASSWORD;
import static android.autofillservice.cts.testcore.Helper.assertHasFlags;
import static android.autofillservice.cts.testcore.Helper.enableFillDialogFeature;
import static android.service.autofill.FillRequest.FLAG_SUPPORTS_FILL_DIALOG;

import static org.junit.Assume.assumeFalse;

import android.autofillservice.cts.activities.FadeInActivity;
import android.autofillservice.cts.commontests.AutoFillServiceTestCase;
import android.autofillservice.cts.testcore.CannedFillResponse;
import android.autofillservice.cts.testcore.CannedFillResponse.CannedDataset;
import android.autofillservice.cts.testcore.Helper;
import android.autofillservice.cts.testcore.InstrumentedAutoFillService.FillRequest;
import android.content.Intent;
import android.content.pm.PackageManager;

import org.junit.Test;


/**
 * This is the test cases about fade-in animation for the fill dialog UI.
 */
public class FadeInActivityTest extends AutoFillServiceTestCase.ManualActivityLaunch {

    @Test
    public void testShowFillDialog_withFadeInAnimation() throws Exception {
        // EditText will autofocus when running in TV target, fill dialog doesn't support
        // for autofocus case, skip test op TV target
        assumeFalse("Skip test on TV. Does not support autofocus for fill dialog", isTv());

        // Enable feature and test service
        enableFillDialogFeature(sContext);
        enableService();

        // Set response
        final CannedFillResponse.Builder builder = new CannedFillResponse.Builder()
                .addDataset(new CannedDataset.Builder()
                        .setField(ID_PASSWORD, "sweet")
                        .setPresentation(createPresentation("Dropdown Presentation"))
                        .setDialogPresentation(createPresentation("Dialog Presentation"))
                        .build())
                .setDialogHeader(createPresentation("Dialog Header"))
                .setDialogTriggerIds(ID_PASSWORD);
        sReplier.addResponse(builder.build());

        // Start activity
        startFadeInActivity();

        // Check onFillRequest has the flag: FLAG_SUPPORTS_FILL_DIALOG
        final FillRequest fillRequest = sReplier.getNextFillRequest();
        assertHasFlags(fillRequest.flags, FLAG_SUPPORTS_FILL_DIALOG);

        // Click on password field to trigger fill dialog
        mUiBot.selectByRelativeId(ID_PASSWORD);
        mUiBot.waitForIdleSync();

        // Set expected value, then select dataset
        mUiBot.assertFillDialogDatasets("Dialog Presentation");
    }

    private void startFadeInActivity() throws Exception {
        final Intent intent = new Intent(mContext, FadeInActivity.class)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        mContext.startActivity(intent);
        mUiBot.assertShownByRelativeId(Helper.ID_PASSWORD_LABEL);
    }

    private boolean isTv() {
        return mContext.getPackageManager().hasSystemFeature(PackageManager.FEATURE_LEANBACK);
    }
}
