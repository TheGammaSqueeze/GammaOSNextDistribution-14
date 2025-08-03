/*
 * Copyright (C) 2019 The Android Open Source Project
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

package android.autofillservice.cts.augmented;

import static com.google.common.truth.Truth.assertWithMessage;

import android.autofillservice.cts.commontests.AugmentedAutofillManualActivityLaunchTestCase;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.platform.test.annotations.AppModeFull;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

@AppModeFull(reason = "AugmentedLoginActivityTest is enough")
public class ClipboardAccessTest extends AugmentedAutofillManualActivityLaunchTestCase {

    private ClipboardManager mClipboardManager;

    @Before
    public void prepareClipboardManager() {
        mClipboardManager = getClipboardManager();
        mClipboardManager.clearPrimaryClip();
    }

    @After
    public void cleanYourself() {
        // This test extends AutoFillServiceTestCase.ManualActivityLaunch, the @Before may not be
        // executed, mClipboardManager will not be set.
        getClipboardManager().clearPrimaryClip();
    }

    @Test
    public void testDoIt() throws Exception {
        // Check to make sure test is in a state where it cannot write to the clipboard.
        mClipboardManager.setPrimaryClip(ClipData.newPlainText(null, "Y U SET?"));
        assertWithMessage("should not be able to set clipboard yet")
                .that(mClipboardManager.getPrimaryClip()).isNull();

        enableAugmentedService();

        mClipboardManager.setPrimaryClip(ClipData.newPlainText(null, "YES, WE CAN!"));
        assertWithMessage("should be able to set clipboard now").that(mClipboardManager.getText())
                .isEqualTo("YES, WE CAN!");
    }

    private ClipboardManager getClipboardManager() {
        return sContext.getSystemService(ClipboardManager.class);
    }
}
