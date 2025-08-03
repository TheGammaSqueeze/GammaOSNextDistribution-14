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

import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeNotNull;

import android.app.Activity;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Intent;
import android.os.Bundle;
import android.security.cts.R;
import android.text.Html;

// The vulnerable activity ADD_DEVICE_ADMIN can't be started as a new task hence PocActivity is
// created to launch it.
public class PocActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        try {
            super.onCreate(savedInstanceState);

            // Create an intent to launch DeviceAdminAdd activity.
            Intent intent = new Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN);
            assumeNotNull(getString(R.string.cve_2021_0600_intentNotFound, intent),
                    intent.resolveActivity(getPackageManager()));
            intent.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN,
                    new ComponentName(this, PocDeviceAdminReceiver.class));

            // For adding an extra 'explanation' to the intent, creating a charsequence object.
            CharSequence seq = Html.fromHtml(getString(R.string.cve_2021_0600_targetText));
            if (getIntent().getBooleanExtra(getString(R.string.cve_2021_0600_keyHtml), false)) {
                seq = Html.fromHtml(getString(R.string.cve_2021_0600_targetTextHtml));
            }

            // Using Html.fromHtml() causes whitespaces to occur at the start/end of the text which
            // are unwanted. Remove the whitespace characters if any at the start and end of the
            // charsequence.
            int end = seq.length() - 1;
            int start = 0;
            while ((Character.isWhitespace(seq.charAt(start))) && start < end) {
                ++start;
            }
            while ((Character.isWhitespace(seq.charAt(end))) && end > start) {
                --end;
            }

            // Check if the charsequence is valid after trimming the whitespaces.
            assumeFalse(getString(R.string.cve_2021_0600_errorCreateCharSeq), start > end);

            // Adding the extra 'explanation' and launching the activity.
            intent.putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                    seq.subSequence(start, end + 1));
            startActivity(intent);

            // Send a broadcast to indicate no exceptions occurred.
            sendBroadcast(new Intent(getString(R.string.cve_2021_0600_action)).putExtra(
                    getString(R.string.cve_2021_0600_keyException),
                    getString(R.string.cve_2021_0600_noException)));
        } catch (Exception e) {
            try {
                // Send a broadcast to report exception.
                sendBroadcast(new Intent(getString(R.string.cve_2021_0600_action))
                        .putExtra(getString(R.string.cve_2021_0600_keyException), e.getMessage()));
            } catch (Exception ignored) {
                // ignore.
            }
        }
    }
}
