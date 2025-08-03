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

package android.display.cts;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import android.Manifest;
import android.content.Context;
import android.hardware.display.DisplayManager;
import android.hardware.display.HdrConversionMode;
import android.platform.test.annotations.AppModeSdkSandbox;
import android.provider.DeviceConfig;

import androidx.test.InstrumentationRegistry;
import androidx.test.ext.junit.rules.ActivityScenarioRule;
import androidx.test.runner.AndroidJUnit4;

import com.android.compatibility.common.util.AdoptShellPermissionsRule;
import com.android.compatibility.common.util.DeviceConfigStateChangerRule;
import com.android.compatibility.common.util.DisplayUtil;
import com.android.compatibility.common.util.FeatureUtil;
import com.android.compatibility.common.util.MediaUtils;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;
import java.util.List;

@RunWith(AndroidJUnit4.class)
@AppModeSdkSandbox(reason = "Allow test in the SDK sandbox (does not prevent other modes).")
public class HdrConversionDisabledTest {
    private static final Context sContext =
            InstrumentationRegistry.getInstrumentation().getContext();

    private DisplayManager mDisplayManager;


    @Rule
    public AdoptShellPermissionsRule mAdoptShellPermissionsRule = new AdoptShellPermissionsRule(
            InstrumentationRegistry.getInstrumentation().getUiAutomation(),
            Manifest.permission.HDMI_CEC,
            Manifest.permission.MODIFY_HDR_CONVERSION_MODE);

    @Rule
    public ActivityScenarioRule<HdrConversionTestActivity> mActivityRule =
            new ActivityScenarioRule<>(HdrConversionTestActivity.class);

    @Rule
    public DeviceConfigStateChangerRule mHdrOutputControlDeviceConfig =
            new DeviceConfigStateChangerRule(sContext,
                    DeviceConfig.NAMESPACE_DISPLAY_MANAGER,
                    DisplayManager.HDR_OUTPUT_CONTROL_FLAG,
                    Boolean.toString(false));

    @Before
    public void setUp() throws Exception {
        assumeTrue("Need an Android TV device to run this test.", FeatureUtil.isTV());
        assertTrue("Physical display is expected.", DisplayUtil.isDisplayConnected(sContext)
                || MediaUtils.onCuttlefish());

        mDisplayManager = sContext.getSystemService(DisplayManager.class);
    }

    @Test
    public void testSetHdrConversionMode() {
        List<Integer> conversionModes = Arrays.asList(
                HdrConversionMode.HDR_CONVERSION_FORCE,
                HdrConversionMode.HDR_CONVERSION_UNSUPPORTED,
                HdrConversionMode.HDR_CONVERSION_SYSTEM,
                HdrConversionMode.HDR_CONVERSION_PASSTHROUGH);

        for (Integer conversionMode : conversionModes) {
            mDisplayManager.setHdrConversionMode(new HdrConversionMode(conversionMode));
        }
    }

    @Test
    public void testGetHdrConversionModeSetting() {
        HdrConversionMode hdrConversionModeSetting = mDisplayManager.getHdrConversionModeSetting();

        assertEquals(HdrConversionMode.HDR_CONVERSION_UNSUPPORTED,
                hdrConversionModeSetting.getConversionMode());
    }

    @Test
    public void testGetHdrConversionMode() {
        HdrConversionMode hdrConversionModeSetting = mDisplayManager.getHdrConversionMode();

        assertEquals(HdrConversionMode.HDR_CONVERSION_UNSUPPORTED,
                hdrConversionModeSetting.getConversionMode());
    }

    @Test
    public void testGetSupportedHdrOutputTypes() {
        int[] supportedHdrOutputTypes = mDisplayManager.getSupportedHdrOutputTypes();

        assertArrayEquals(new int[0], supportedHdrOutputTypes);
    }
}
