/*
 * Copyright (C) 2020 The Android Open Source Project
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

import static android.content.Intent.FLAG_ACTIVITY_NEW_TASK;

import static com.android.compatibility.common.util.ShellUtils.runShellCommand;

import static com.google.common.truth.Truth.assertThat;

import android.app.PendingIntent;
import android.autofillservice.cts.testcore.Helper;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.SystemClock;
import android.platform.test.annotations.AppModeFull;
import android.provider.Settings;
import android.util.Log;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import com.android.compatibility.common.util.BlockingBroadcastReceiver;
import com.android.compatibility.common.util.RequiredFeatureRule;
import com.android.compatibility.common.util.SettingsStateKeeperRule;

import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class AutofillManagerTest {

    private static final String TAG = "AutofillManagerTest";

    private static final int AUTOFILL_ENABLE = 1;
    private static final int AUTOFILL_DISABLE = 2;
    private static final String OUTSIDE_QUERYAUTOFILLSTATUS_APK =
            "TestAutofillServiceApp.apk";

    private static final Context sContext =
            InstrumentationRegistry.getInstrumentation().getContext();

    @ClassRule
    public static final SettingsStateKeeperRule sPublicServiceSettingsKeeper =
            new SettingsStateKeeperRule(sContext, Settings.Secure.AUTOFILL_SERVICE);

    @Rule
    public final RequiredFeatureRule sRequiredFeatureRule =
            new RequiredFeatureRule(PackageManager.FEATURE_AUTOFILL);

    @Test
    @AppModeFull(reason = "Package cannot install in instant app mode")
    public void testHasEnabledAutofillServices() throws Exception {
        // Verify the calling application's AutofillService is initially disabled
        runQueryAutofillStatusActivityAndVerifyResult(AUTOFILL_DISABLE);

        // Enable calling application's AutofillService
        enableOutsidePackageTestAutofillService();

        // Verify the calling application's AutofillService is enabled
        runQueryAutofillStatusActivityAndVerifyResult(AUTOFILL_ENABLE);

        // Update the calling application package and verify the calling application's
        // AutofillService is still enabled
        install(OUTSIDE_QUERYAUTOFILLSTATUS_APK);
        runQueryAutofillStatusActivityAndVerifyResult(AUTOFILL_ENABLE);
    }

    private void enableOutsidePackageTestAutofillService() {
        final String outsidePackageAutofillServiceName =
                "android.autofill.cts2/.NoOpAutofillService";
        Helper.enableAutofillService(outsidePackageAutofillServiceName);
    }

    private void install(String apk) {
        final String installResult = runShellCommand(
                "pm install -r /data/local/tmp/cts/autofill/" + apk);
        Log.d(TAG, "install result = " + installResult);
        assertThat(installResult.trim()).isEqualTo("Success");
    }

    /**
     * Start an activity that uses hasEnabledAutofillServices() to query its AutofillService
     * status and return the status result to the caller. Then we verify the status result from
     * the Activity.
     */
    private void runQueryAutofillStatusActivityAndVerifyResult(int expectedStatus) {
        final String actionAutofillStatusActivityFinish =
                "ACTION_AUTOFILL_STATUS_ACTIVITY_FINISH_" + SystemClock.uptimeMillis();

        // register a activity finish receiver
        final BlockingBroadcastReceiver receiver = new BlockingBroadcastReceiver(sContext,
                actionAutofillStatusActivityFinish);
        receiver.register();

        // Start an Activity from another package
        final Intent outsideActivity = new Intent();
        outsideActivity.setComponent(new ComponentName("android.autofill.cts2",
                "android.autofill.cts2.QueryAutofillStatusActivity"));
        outsideActivity.setFlags(FLAG_ACTIVITY_NEW_TASK);
        final Intent broadcastIntent = new Intent(actionAutofillStatusActivityFinish);
        final PendingIntent pendingIntent = PendingIntent.getBroadcast(sContext, 0, broadcastIntent,
                PendingIntent.FLAG_IMMUTABLE);
        outsideActivity.putExtra("finishBroadcast", pendingIntent);
        sContext.startActivity(outsideActivity);

        // Verify the finish broadcast is received.
        final Intent intent = receiver.awaitForBroadcast();
        assertThat(intent).isNotNull();
        // Verify the status result code.
        final int statusResultCode = receiver.getResultCode();
        Log.d(TAG, "hasEnabledAutofillServices statusResultCode = " + statusResultCode);
        assertThat(statusResultCode).isEqualTo(expectedStatus);

        // unregister receiver
        receiver.unregisterQuietly();
    }
}
