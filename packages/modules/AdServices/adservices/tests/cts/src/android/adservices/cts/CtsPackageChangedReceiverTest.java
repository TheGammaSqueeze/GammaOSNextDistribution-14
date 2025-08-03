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

package android.adservices.cts;

import android.content.Context;
import android.content.Intent;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.adservices.common.AdServicesDeviceSupportedRule;
import com.android.modules.utils.build.SdkLevel;

import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class CtsPackageChangedReceiverTest {
    private static final String PACKAGE_CHANGED_BROADCAST =
            SdkLevel.isAtLeastT()
                    ? "com.android.adservices.PACKAGE_CHANGED"
                    : "com.android.ext.adservices.PACKAGE_CHANGED";

    @Rule
    public final AdServicesDeviceSupportedRule adServicesDeviceSupportedRule =
            new AdServicesDeviceSupportedRule();

    /**
     * Verify that the com.android.adservices.service.common.PACKAGE_CHANGED broadcast is protected
     * as defined in the AdServices' AndroidManifest.xml and can't be sent by non-system apps. The
     * test is a non-system app and should throw a SecurityException when it tries to send the
     * broadcast.
     */
    @Test(expected = SecurityException.class)
    public void testSendProtectedBroadcast() {
        final Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        Intent intent = new Intent(PACKAGE_CHANGED_BROADCAST);
        context.sendBroadcast(intent);
        // Fail since the test app is a non-system app so it cannot send the broadcast, an exception
        // should have been thrown.
        Assert.fail(
                "Expected security exception broadcasting action: " + PACKAGE_CHANGED_BROADCAST);
    }
}
