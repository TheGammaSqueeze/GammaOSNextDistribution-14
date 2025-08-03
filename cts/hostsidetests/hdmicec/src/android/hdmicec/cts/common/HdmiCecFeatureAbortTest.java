/*
 * Copyright (C) 2020 The Android Open Source Project
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

package android.hdmicec.cts.common;

import android.hdmicec.cts.BaseHdmiCecCtsTest;
import android.hdmicec.cts.CecMessage;
import android.hdmicec.cts.CecOperand;
import android.hdmicec.cts.HdmiCecConstants;
import android.hdmicec.cts.LogicalAddress;

import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.runner.RunWith;

import java.util.Arrays;
import java.util.List;

/** HDMI CEC tests related to {@code <Feature Abort>} */
@RunWith(DeviceJUnit4ClassRunner.class)
public final class HdmiCecFeatureAbortTest extends BaseHdmiCecCtsTest {

    private static final int TIMEOUT_SHORT_MILLIS = 1000;

    @Rule
    public RuleChain ruleChain =
            RuleChain
                    .outerRule(CecRules.requiresCec(this))
                    .around(CecRules.requiresLeanback(this))
                    .around(CecRules.requiresPhysicalDevice(this))
                    .around(hdmiCecClient);

    /**
     * Test HF4-2-11
     * Verify {@code <Feature Abort} message does not result in a {@code <Feature Abort>} response.
     */
    @Test
    public void cect_hf4_2_11_featureAbortBehavior() throws Exception {
        setCec20();

        List<Integer> abortReasons = Arrays.asList(
                HdmiCecConstants.ABORT_UNRECOGNIZED_MODE,
                HdmiCecConstants.ABORT_NOT_IN_CORRECT_MODE,
                HdmiCecConstants.ABORT_CANNOT_PROVIDE_SOURCE,
                HdmiCecConstants.ABORT_INVALID_OPERAND,
                HdmiCecConstants.ABORT_REFUSED,
                HdmiCecConstants.ABORT_UNABLE_TO_DETERMINE);

        for (Integer abortReason : abortReasons) {
            hdmiCecClient.sendCecMessage(
                    LogicalAddress.RECORDER_1,
                    CecOperand.FEATURE_ABORT,
                    CecMessage.formatParams(abortReason));

            hdmiCecClient.checkOutputDoesNotContainMessage(LogicalAddress.RECORDER_1,
                    CecOperand.FEATURE_ABORT, TIMEOUT_SHORT_MILLIS);
        }
    }
}
