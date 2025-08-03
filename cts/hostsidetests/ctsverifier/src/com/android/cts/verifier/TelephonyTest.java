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
import com.android.interactive.annotations.Interactive;
import com.android.interactive.annotations.SupportMultiDisplayMode;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(DeviceJUnit4ClassRunner.class)
public final class TelephonyTest extends CtsVerifierTest {

    @Interactive
    @Test
    @SupportMultiDisplayMode
    // MultiDisplayMode
    @ApiTest(apis = "android.telephony.TelephonyManager#ACTION_SHOW_VOICEMAIL_NOTIFICATION")
    public void VoicemailBroadcastTest() throws Exception {
        requireFeatures("android.hardware.telephony");

        runTest(".voicemail.VoicemailBroadcastActivity", "config_voice_capable");
    }

    @Interactive
    @Test
    @SupportMultiDisplayMode
    // MultiDisplayMode
    @ApiTest(apis = "android.telephony.TelephonyManager#ACTION_SHOW_VOICEMAIL_NOTIFICATION")
    public void VisualVoicemailServiceTest() throws Exception {
        requireFeatures("android.hardware.telephony");

        runTest(".voicemail.VisualVoicemailServiceActivity", "config_voice_capable");
    }

    @Interactive
    @Test
    @SupportMultiDisplayMode
    // MultiDisplayMode
    public void DialerIncomingCallTest() throws Exception { // Needs to receive a call
        requireFeatures("android.hardware.telephony");

        runTest(".dialer.DialerIncomingCallTestActivity", "config_voice_capable");
    }

    @Interactive
    @Test
    @SupportMultiDisplayMode
    // MultiDisplayMode
    public void DialerShowsHunOnIncomingCallTest() throws Exception {
        requireFeatures("android.hardware.telephony");

        runTest(".dialer.DialerShowsHunOnIncomingCallActivity", "config_voice_capable");
    }

    @Interactive
    @Test
    @SupportMultiDisplayMode
    // MultiDisplayMode
    @ApiTest(apis = "android.telephony.TelephonyManager#METADATA_HIDE_VOICEMAIL_SETTINGS_MENU")
    public void CallSettingsCheckTest() throws Exception {
        requireFeatures("android.hardware.telephony");

        runTest(".voicemail.CallSettingsCheckActivity", "config_voice_capable");
    }

    @Interactive
    @Test
    @SupportMultiDisplayMode
    // MultiDisplayMode
    @ApiTest(apis = "android.telephony.TelephonyManager#EXTRA_HIDE_PUBLIC_SETTINGS")
    public void VoicemailSettingsCheckTest() throws Exception {
        requireFeatures("android.hardware.telephony");

        runTest(".voicemail.VoicemailSettingsCheckActivity", "config_voice_capable");
    }

    @Interactive
    @Test
    @SupportMultiDisplayMode
    // MultiDisplayMode
    public void DialerImplementsTelecomIntentsTest() throws Exception {
        requireFeatures("android.hardware.telephony");
        excludeFeatures("android.hardware.type.watch");

        runTest(".dialer.DialerImplementsTelecomIntentsActivity", "config_voice_capable");
    }
}
