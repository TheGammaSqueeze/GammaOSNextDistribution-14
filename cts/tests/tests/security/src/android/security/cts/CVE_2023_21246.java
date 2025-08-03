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

package android.security.cts;

import static androidx.test.core.app.ApplicationProvider.getApplicationContext;

import static org.junit.Assert.assertFalse;
import static org.junit.Assume.assumeNoException;

import android.content.pm.ShortcutInfo;
import android.platform.test.annotations.AsbSecurityTest;

import androidx.test.runner.AndroidJUnit4;

import com.android.sts.common.util.StsExtraBusinessLogicTestCase;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class CVE_2023_21246 extends StsExtraBusinessLogicTestCase {

    @AsbSecurityTest(cveBugId = 273729476)
    @Test
    public void testPocCVE_2023_21246() {
        try {
            // Set shortcutIdLength = 10 * MAX_ID_LENGTH where value of MAX_ID_LENGTH is set to 1000
            // in fix patch
            final int shortcutIdLength = 10000;

            // Create shortcutInfo object with shortcutId of length 10000
            ShortcutInfo shortcutInfo =
                    new ShortcutInfo.Builder(
                                    getApplicationContext(),
                                    new String(new char[shortcutIdLength])
                                            .replace("\0" /* oldChar */, "A" /* newChar */))
                            .build();

            // Fail if shortcutId length is more than or equal to 10000
            assertFalse(
                    "Device is vulnerable to b/273729476 since length of shortcutId is more than"
                            + " or equal to 10000 hence notification access can be persisted after"
                            + " reboot via a malformed notification listener with super large"
                            + " shortcutId enabled",
                    shortcutInfo.getId().length() >= shortcutIdLength);
        } catch (Exception e) {
            assumeNoException(e);
        }
    }
}
