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

import com.android.interactive.annotations.Interactive;
import com.android.interactive.annotations.SupportMultiDisplayMode;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;

import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(DeviceJUnit4ClassRunner.class)
public final class TelecomTest extends CtsVerifierTest {

    @Interactive
    @Test
    @SupportMultiDisplayMode
    // MultiDisplayMode
    public void EnablePhoneAccountTest() throws Exception {
        requireFeatures("android.hardware.telephony");
        excludeFeatures("android.hardware.type.watch");

        runTest(".telecom.EnablePhoneAccountTestActivity", "config_voice_capable");
    }

    @Interactive
    @Test
    @SupportMultiDisplayMode
    // MultiDisplayMode
    public void OutgoingCallTest() throws Exception {
        requireFeatures("android.hardware.telephony");
        excludeFeatures("android.hardware.type.watch");

        runTest(".telecom.OutgoingCallTestActivity", "config_voice_capable");
    }

    @Interactive
    @Test
    @SupportMultiDisplayMode
    // MultiDisplayMode
    public void SelfManagedIncomingCallTest() throws Exception {
        requireFeatures("android.hardware.telephony");

        runTest(".telecom.SelfManagedIncomingCallTestActivity", "config_voice_capable");
    }

    @Interactive
    @Test
    @SupportMultiDisplayMode
    // MultiDisplayMode
    public void IncomingCallTest() throws Exception {
        requireFeatures("android.hardware.telephony");
        excludeFeatures("android.hardware.type.watch");

        runTest(".telecom.IncomingCallTestActivity", "config_voice_capable");
    }

    @Interactive
    @Test
    @SupportMultiDisplayMode
    // MultiDisplayMode
    public void TelecomDefaultDialerTest() throws Exception {
        requireFeatures("android.hardware.telephony");

        runTest(".telecom.TelecomDefaultDialerTestActivity", "config_voice_capable");
    }

    @Interactive
    @Test
    @SupportMultiDisplayMode
    // MultiDisplayMode
    @Ignore // TODO: I don't think this is actually a test - but is declared as one in the manifest
    // - needs investigation
    public void CtsVerifierInCallUiTest() throws Exception {
        requireFeatures("android.hardware.telephony");

        runTest(".telecom.CtsVerifierInCallUi", "config_voice_capable");
    }
}
