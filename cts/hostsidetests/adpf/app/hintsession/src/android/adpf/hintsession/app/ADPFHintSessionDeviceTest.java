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

package android.adpf.hintsession.app;

import static android.adpf.common.ADPFHintSessionConstants.TEST_NAME_KEY;

import static org.junit.Assert.fail;
import static org.junit.Assume.assumeFalse;

import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.device.collectors.util.SendToInstrumentation;
import android.os.Bundle;
import android.os.SystemProperties;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.util.Map;

@RunWith(AndroidJUnit4.class)
public class ADPFHintSessionDeviceTest {
    private final Instrumentation mInstrumentation = InstrumentationRegistry.getInstrumentation();
    private Context mContext;

    @Before
    public void setUp() {
        mContext = mInstrumentation.getContext();
    }

    protected void assumeMobileDeviceFormFactor() throws IOException {
        final PackageManager pm = mContext.getPackageManager();
        assumeFalse("Skipping test for auto",
                pm.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE));
        assumeFalse("Skipping test for TV",
                pm.hasSystemFeature(PackageManager.FEATURE_LEANBACK)); // TVs
        assumeFalse("Skipping test for watch",
                pm.hasSystemFeature(PackageManager.FEATURE_WATCH));
        assumeFalse("Skipping test for embedded",
                pm.hasSystemFeature(PackageManager.FEATURE_EMBEDDED));
        assumeFalse("Skipping test for virtual devices",
                onVirtualDevice());
    }

    protected static boolean onVirtualDevice() {
        String device = SystemProperties.get("ro.product.device", "");
        String model = SystemProperties.get("ro.product.model", "");
        String name = SystemProperties.get("ro.product.name", "");
        String qemu = SystemProperties.get("ro.boot.qemu", "0");
        String hardware = SystemProperties.get("ro.hardware", "");
        return device.startsWith("vsoc_") || model.startsWith("Cuttlefish ")
                || name.startsWith("cf_") || name.startsWith("aosp_cf_")
                || qemu.equals("1") || hardware.contains("goldfish")
                || hardware.contains("ranchu")
                || hardware.contains("cutf_cvm") || hardware.contains("starfish");
    }

    @Test
    public void testAdpfHintSession() throws IOException {
        assumeMobileDeviceFormFactor();
        final Intent intent = new Intent(mContext, ADPFHintSessionDeviceActivity.class);
        // TODO: pass config to app
        intent.putExtra(TEST_NAME_KEY, "testAdpfHintSession");
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        ADPFHintSessionDeviceActivity activity = (ADPFHintSessionDeviceActivity) mInstrumentation
                .startActivitySync(intent);
        // this will wait until the test is complete
        activity.waitForTestFinished();

        // report metrics
        final Bundle returnBundle = new Bundle();
        Map<String, String> metrics = activity.getMetrics();
        if (metrics.containsKey("failure")) {
            fail("Failed with error: " + metrics.get("failure"));
        }
        metrics.forEach(returnBundle::putString);
        SendToInstrumentation.sendBundle(mInstrumentation, returnBundle);
    }
}
