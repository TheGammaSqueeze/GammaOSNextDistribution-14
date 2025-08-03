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

import static android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI;

import static com.google.common.truth.Truth.assertWithMessage;
import static com.google.common.truth.TruthJUnit.assume;

import static org.junit.Assume.assumeNoException;

import android.content.ContentProvider;
import android.content.Context;
import android.graphics.drawable.Icon;
import android.os.UserManager;
import android.platform.test.annotations.AsbSecurityTest;
import android.telecom.StatusHints;

import androidx.test.runner.AndroidJUnit4;

import com.android.sts.common.util.StsExtraBusinessLogicTestCase;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class CVE_2023_21283 extends StsExtraBusinessLogicTestCase {

    @AsbSecurityTest(cveBugId = 280797684)
    @Test
    public void testPocCVE_2023_21283() {
        try {
            // Check if the device supports multiple users or not
            Context context = getContext();
            assume().withMessage("This device does not support multiple users")
                    .that(context.getSystemService(UserManager.class).supportsMultipleUsers())
                    .isTrue();

            // Create StatusHints object with an icon specified by URI associated with target user.
            int targetUserId = context.getUserId() + 1;
            StatusHints hints =
                    new StatusHints(
                            "CVE_2023_21283_user",
                            Icon.createWithContentUri(
                                    ContentProvider.maybeAddUserId(
                                            EXTERNAL_CONTENT_URI, targetUserId)),
                            null);

            // With fix, getIcon() returns null.
            assertWithMessage("Vulnerable to b/280797684").that(hints.getIcon()).isNull();
        } catch (Exception e) {
            assumeNoException(e);
        }
    }
}
