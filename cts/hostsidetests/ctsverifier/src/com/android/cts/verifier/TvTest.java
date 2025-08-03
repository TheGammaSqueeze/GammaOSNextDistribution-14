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

package com.android.cts.verifier;

import com.android.compatibility.common.util.ApiTest;
import com.android.compatibility.common.util.CddTest;
import com.android.interactive.annotations.Interactive;
import com.android.interactive.annotations.SupportMultiDisplayMode;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(DeviceJUnit4ClassRunner.class)
public final class TvTest extends CtsVerifierTest {

    @Interactive
    @Test
    @SupportMultiDisplayMode
    // MultiDisplayMode
    public void TvInputDiscoveryTest() throws Exception {
        requireFeatures("android.software.live_tv");

        runTest(".tv.TvInputDiscoveryTestActivity");
    }

    @Interactive
    @Test
    @SupportMultiDisplayMode
    // MultiDisplayMode
    public void ParentalControlTest() throws Exception {
        requireFeatures("android.software.live_tv");

        runTest(".tv.ParentalControlTestActivity");
    }

    @Interactive
    @Test
    @SupportMultiDisplayMode
    // MultiDisplayMode
    public void MultipleTracksTest() throws Exception {
        requireFeatures("android.software.live_tv");

        runTest(".tv.MultipleTracksTestActivity");
    }

    @Interactive
    @Test
    @SupportMultiDisplayMode
    // MultiDisplayMode
    public void TimeShiftTest() throws Exception {
        requireFeatures("android.software.live_tv");

        runTest(".tv.TimeShiftTestActivity");
    }

    @Interactive
    @Test
    @SupportMultiDisplayMode
    // MultiDisplayMode
    @CddTest(requirements = "3.12/C-1-2")
    public void AppLinkTest() throws Exception {
        requireFeatures("android.software.live_tv");

        runTest(".tv.AppLinkTestActivity");
    }

    @Interactive
    @Test
    @SupportMultiDisplayMode
    // MultiDisplayMode
    public void MicrophoneDeviceTest() throws Exception {
        requireFeatures("android.software.leanback", "android.hardware.microphone");

        runTest(".tv.MicrophoneDeviceTestActivity");
    }

    @Interactive
    @Test
    @SupportMultiDisplayMode
    // MultiDisplayMode
    @ApiTest(apis = "android.media.AudioTrack#isDirectPlaybackSupported")
    public void AudioCapabilitiesTest() throws Exception {
        requireFeatures("android.software.leanback");

        runTest(".tv.audio.AudioCapabilitiesTestActivity", "config_hdmi_source");
    }

    @Interactive
    @Test
    @SupportMultiDisplayMode
    // MultiDisplayMode
    @ApiTest(apis = "android.hardware.display.DisplayManager.DisplayListener#onDisplayChanged")
    public void HotplugTest() throws Exception {
        requireFeatures("android.software.leanback");

        runTest(".tv.display.HotplugTestActivity", "config_hdmi_source");
    }

    @Interactive
    @Test
    @SupportMultiDisplayMode
    // MultiDisplayMode
    @ApiTest(apis = "android.view.WindowManager.LayoutParams#preferredDisplayModeId")
    public void ModeSwitchingTest() throws Exception {
        requireFeatures("android.software.leanback");

        runTest(".tv.display.ModeSwitchingTestActivity");
    }
}
