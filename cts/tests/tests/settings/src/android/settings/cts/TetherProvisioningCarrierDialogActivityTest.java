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

package android.settings.cts;

import static org.junit.Assume.assumeFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.RemoteException;
import android.provider.Settings;

import androidx.test.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Tests to ensure the Activity to handle
 * {@link Settings#ACTION_TETHER_PROVISIONING_CARRIER_UI}
 */
@RunWith(AndroidJUnit4.class)
public class TetherProvisioningCarrierDialogActivityTest {
    @Before
    public void setUp() throws Exception {
        final PackageManager pm =
                InstrumentationRegistry.getInstrumentation().getContext().getPackageManager();
        assumeTrue(pm.hasSystemFeature(PackageManager.FEATURE_TELEPHONY));
        assumeFalse(
                "Skipping test: Tethering is not supported in Wear OS", isWatch());
    }

    @Test
    public void testTetheringProvisioningCarrierUiExisted() throws RemoteException {
        final Intent intent = new Intent(Settings.ACTION_TETHER_UNSUPPORTED_CARRIER_UI);
        final ResolveInfo ri = InstrumentationRegistry.getTargetContext()
                .getPackageManager().resolveActivity(
                        intent, PackageManager.MATCH_DEFAULT_ONLY);
        assertTrue(ri != null);
    }

    private boolean isWatch() {
        return InstrumentationRegistry.getTargetContext()
                .getPackageManager().hasSystemFeature(PackageManager.FEATURE_WATCH);
    }
}
