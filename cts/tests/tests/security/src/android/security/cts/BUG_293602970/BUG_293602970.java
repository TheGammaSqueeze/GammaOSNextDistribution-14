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

package android.security.cts.BUG_293602970;

import static androidx.test.core.app.ApplicationProvider.getApplicationContext;

import static com.android.compatibility.common.util.SystemUtil.runShellCommand;
import static com.android.sts.common.SystemUtil.poll;

import static com.google.common.truth.Truth.assertWithMessage;
import static com.google.common.truth.TruthJUnit.assume;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.UserManager;
import android.platform.test.annotations.AsbSecurityTest;

import androidx.test.runner.AndroidJUnit4;

import com.android.sts.common.util.StsExtraBusinessLogicTestCase;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@RunWith(AndroidJUnit4.class)
public class BUG_293602970 extends StsExtraBusinessLogicTestCase {

    @AsbSecurityTest(cveBugId = 293602970)
    @Test
    public void testPocBUG_293602970() {
        try {
            final Context context = getApplicationContext();

            // Check if the device supports multiple users
            assume().withMessage("This device does not support multiple users")
                    .that(context.getSystemService(UserManager.class).supportsMultipleUsers())
                    .isTrue();

            // Fetch and add the flag 'RECEIVER_NOT_EXPORTED' for 'TIRAMISU' and above versions to
            // keep the code consistent
            final int requiredFlag =
                    Build.VERSION.SDK_INT >= 33 /* TIRAMISU */
                            ? (int) Context.class.getField("RECEIVER_NOT_EXPORTED").get(context)
                            : 0;

            // Register broadcast receiver to receive broadcast from 'PocActivity'
            CompletableFuture<String> callbackReturn = new CompletableFuture<String>();
            context.registerReceiver(
                    new BroadcastReceiver() {
                        @Override
                        public void onReceive(Context context, Intent intent) {
                            try {
                                // Read value of 'activityName' from intent extra
                                callbackReturn.complete(intent.getStringExtra("activityName"));
                            } catch (Exception ignore) {
                                // Ignore
                            }
                        }
                    },
                    new IntentFilter("BUG_293602970_action"),
                    requiredFlag);

            // Launch 'PocActivity' to reproduce vulnerability
            context.startActivity(
                    new Intent(context, PocActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));

            // Without fix, 'ConfirmUserCreationActivity' activity launches and the test fails
            final String activityName =
                    callbackReturn.get(10_000L /* timeout */, TimeUnit.MILLISECONDS /* unit */);
            assertWithMessage(
                            "Device is vulnerable to b/293602970!!"
                                    + " Create and persist a new secondary user without any"
                                    + " restrictions via a super large seed account option")
                    .that(checkActivityLaunched(activityName))
                    .isFalse();
        } catch (Exception e) {
            assume().that(e).isNull();
        }
    }

    private boolean checkActivityLaunched(String activityName) throws Exception {
        final Pattern resumedPattern = Pattern.compile("mResumed=true", Pattern.CASE_INSENSITIVE);
        final Pattern visiblePattern = Pattern.compile("mVisible=true", Pattern.CASE_INSENSITIVE);
        return poll(
                () -> {
                    final String dumpsysOutput =
                            runShellCommand(String.format("dumpsys activity -a " + activityName));
                    final Matcher resumedMatcher = resumedPattern.matcher(dumpsysOutput);
                    final Matcher visibleMatcher = visiblePattern.matcher(dumpsysOutput);
                    return resumedMatcher.find() && visibleMatcher.find();
                });
    }
}
