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

package com.android.adservices.appsetid;

import static com.google.common.truth.Truth.assertThat;

import android.os.Build;
import android.os.ext.SdkExtensions;

import androidx.privacysandbox.ads.adservices.appsetid.AppSetId;
import androidx.privacysandbox.ads.adservices.java.appsetid.AppSetIdManagerFutures;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.adservices.TestUtil;

import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;

public class AppSetIdManagerJetpackTest {
    private static final String TAG = "AppSetIdManagerJetpackTest";
    TestUtil mTestUtil = new TestUtil(InstrumentationRegistry.getInstrumentation(), TAG);

    @Before
    public void setup() throws Exception {
        int adServicesVersion = SdkExtensions.getExtensionVersion(SdkExtensions.AD_SERVICES);
        int extServicesVersion = SdkExtensions.getExtensionVersion(Build.VERSION_CODES.S);
        Assume.assumeTrue(adServicesVersion >= 4 || extServicesVersion >= 9);

        mTestUtil.overrideAppSetIdKillSwitch(true);
        mTestUtil.overrideKillSwitches(true);
        mTestUtil.overrideAllowlists(true);

        // Put in a short sleep to make sure the updated config propagates
        // before starting the tests
        Thread.sleep(100);
    }

    @After
    public void teardown() {
        mTestUtil.overrideAppSetIdKillSwitch(false);
        mTestUtil.overrideKillSwitches(false);
        mTestUtil.overrideAllowlists(false);
    }

    @Test
    public void testAppSetId() throws Exception {
        AppSetIdManagerFutures appSetIdManager =
                AppSetIdManagerFutures.from(ApplicationProvider.getApplicationContext());
        AppSetId appSetId = appSetIdManager.getAppSetIdAsync().get();
        assertThat(appSetId.getId()).isNotEmpty();
        assertThat(appSetId.getScope()).isNotNull();
    }
}
