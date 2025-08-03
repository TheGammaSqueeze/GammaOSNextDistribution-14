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

package android.security.cts.CVE_2021_0600;

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;

import static org.junit.Assert.assertFalse;
import static org.junit.Assume.assumeNoException;
import static org.junit.Assume.assumeTrue;

import android.app.Instrumentation;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Rect;
import android.platform.test.annotations.AsbSecurityTest;
import android.security.cts.R;
import android.support.test.uiautomator.By;
import android.support.test.uiautomator.BySelector;
import android.support.test.uiautomator.UiDevice;
import android.support.test.uiautomator.UiObject2;
import android.support.test.uiautomator.Until;

import androidx.test.runner.AndroidJUnit4;

import com.android.sts.common.util.StsExtraBusinessLogicTestCase;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class CVE_2021_0600 extends StsExtraBusinessLogicTestCase {
    private static final long TIMEOUT_MS = 5000;
    private CompletableFuture<String> mPocActivityReturn;
    private UiDevice mDevice;
    private Context mContext;

    // b/179042963
    // Vulnerable package : com.android.settings (As per AOSP code)
    // Vulnerable app     : Settings.apk (As per AOSP code)
    @AsbSecurityTest(cveBugId = 179042963)
    @Test
    public void testPocCVE_2021_0600() {
        try {
            Instrumentation instrumentation = getInstrumentation();
            mDevice = UiDevice.getInstance(instrumentation);
            mContext = instrumentation.getContext();

            // Registering a broadcast receiver to receive broadcast from PocActivity.
            mPocActivityReturn = new CompletableFuture<>();
            BroadcastReceiver broadcastReceiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    try {
                        mPocActivityReturn.complete(intent.getStringExtra(
                                mContext.getString(R.string.cve_2021_0600_keyException)));
                    } catch (Exception e) {
                        // ignore.
                    }
                }
            };
            mContext.registerReceiver(broadcastReceiver,
                    new IntentFilter(mContext.getString(R.string.cve_2021_0600_action)));

            // Launch the PocActivity which in turn starts DeviceAdminAdd activity with normal
            // text as 'explanation'.
            Intent intent = new Intent(mContext, PocActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            intent.putExtra(mContext.getString(R.string.cve_2021_0600_keyHtml), false);
            mContext.startActivity(intent);
            String pocActivityException = mPocActivityReturn.get(TIMEOUT_MS, TimeUnit.MILLISECONDS);
            assumeTrue(pocActivityException, pocActivityException.trim()
                    .equals(mContext.getString(R.string.cve_2021_0600_noException)));

            // Get the height of the normal text with no formatting. Because width is same both
            // with and without fix, height is being used for comparing the with and without
            // fix behaviour.
            int heightWoHtml = getVulnerableUIHeight();
            assumeTrue(heightWoHtml != -1);

            // Launch PocActivity again such that DeviceAdminAdd activity starts with formatted text
            // this time.
            mPocActivityReturn = new CompletableFuture<>();
            intent.putExtra(mContext.getString(R.string.cve_2021_0600_keyHtml), true);
            mContext.startActivity(intent);
            pocActivityException = mPocActivityReturn.get(TIMEOUT_MS, TimeUnit.MILLISECONDS);
            assumeTrue(pocActivityException, pocActivityException
                    .equalsIgnoreCase(mContext.getString(R.string.cve_2021_0600_noException)));

            // Get the height of HTML text with formatting.
            int heightWithHtml = getVulnerableUIHeight();
            assumeTrue(heightWithHtml != -1);

            // On vulnerable device, the text displayed on the screen will be HTML formatted, so
            // there will be considerable increase in height of the text due to <h1> tag, if there
            // is at least 20% increase in height, the test will fail.
            assertFalse(mContext.getString(R.string.cve_2021_0600_failMsg),
                    heightWithHtml > 1.2 * heightWoHtml);
        } catch (Exception e) {
            assumeNoException(e);
        }
    }

    private int getVulnerableUIHeight() {
        Pattern pattern = Pattern.compile(mContext.getString(R.string.cve_2021_0600_pattern),
                Pattern.CASE_INSENSITIVE);
        BySelector selector = By.text(pattern);
        assumeTrue(mContext.getString(R.string.cve_2021_0600_patternNotFound, pattern),
                mDevice.wait(Until.hasObject(selector), TIMEOUT_MS));
        UiObject2 obj = mDevice.findObject(selector);
        if (obj != null && obj.getText() != null
                && obj.getText().contains(mContext.getString(R.string.cve_2021_0600_targetText))) {
            Rect bounds = obj.getVisibleBounds();
            return bounds.bottom - bounds.top;
        }
        return -1;
    }
}
