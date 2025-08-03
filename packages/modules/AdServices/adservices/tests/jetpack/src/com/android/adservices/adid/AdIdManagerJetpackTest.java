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

package com.android.adservices.adid;

import static com.google.common.truth.Truth.assertThat;

import android.os.Build;
import android.os.ext.SdkExtensions;

import androidx.privacysandbox.ads.adservices.adid.AdId;
import androidx.privacysandbox.ads.adservices.java.adid.AdIdManagerFutures;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.adservices.TestUtil;

import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;

public class AdIdManagerJetpackTest {
    private static final String TAG = "AdIdManagerJetpackTest";
    private TestUtil mTestUtil = new TestUtil(InstrumentationRegistry.getInstrumentation(), TAG);

    @Before
    public void setup() throws Exception {
        int adServicesVersion = SdkExtensions.getExtensionVersion(SdkExtensions.AD_SERVICES);
        int extServicesVersion = SdkExtensions.getExtensionVersion(Build.VERSION_CODES.S);
        Assume.assumeTrue(adServicesVersion >= 4 || extServicesVersion >= 9);

        mTestUtil.overrideAdIdKillSwitch(true);
        mTestUtil.overrideKillSwitches(true);
        mTestUtil.overrideConsentManagerDebugMode(true);
        mTestUtil.overrideAllowlists(true);

        // Put in a short sleep to make sure the updated config propagates
        // before starting the tests
        Thread.sleep(1000);
    }

    @After
    public void teardown() {
        mTestUtil.overrideAdIdKillSwitch(false);
        mTestUtil.overrideKillSwitches(false);
        mTestUtil.overrideConsentManagerDebugMode(false);
        mTestUtil.overrideAllowlists(false);
    }

    @Test
    public void testAdId() throws Exception {
        AdIdManagerFutures adIdManager =
                AdIdManagerFutures.from(ApplicationProvider.getApplicationContext());
        AdId adId = adIdManager.getAdIdAsync().get();
        assertThat(adId.getAdId()).isNotEmpty();
        assertThat(adId.isLimitAdTrackingEnabled()).isNotNull();
    }
}
