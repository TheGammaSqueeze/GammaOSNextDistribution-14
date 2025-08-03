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

package com.android.libraries.rcs.simpleclient.protocol.sdp;

import static com.google.common.truth.Truth.assertThat;

import static java.nio.charset.StandardCharsets.UTF_8;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import java.io.ByteArrayInputStream;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class SimpleSdpMessageTest {
    private static final String SAMPLE_SDP_REGEX =
            "v=0\r\n"
                    + "o=TestRcsClient .+ .+ IN IP4 192.168.1.1\r\n"
                    + "s=-\r\n"
                    + "c=IN IP4 192.168.1.1\r\n"
                    + "t=0 0\r\n"
                    + "m=message 9 TCP/MSRP \\*\r\n"
                    + "a=path:msrp://192.168.1.1:9/.+;tcp\r\n"
                    + "a=setup:active\r\n"
                    + "a=accept-types:message/cpim application/im-iscomposing\\+xml\r\n"
                    + "a=accept-wrapped-types:text/plain message/imdn\\+xml"
                    + " application/vnd.gsma.rcs-ft-http\\+xml application/vnd.gsma"
                    + ".rcspushlocation\\+xml\r\n"
                    + "a=sendrecv\r\n";

    @Test
    public void createForMsrp_returnExpectedSdpString() {
        SimpleSdpMessage sdp =
                SdpUtils.createSdpForMsrp(/* address= */ "192.168.1.1", /* isTls= */ false);

        assertThat(sdp.encode()).matches(SAMPLE_SDP_REGEX);
    }

    @Test
    public void encodeAndParse_shouldBeEqualToOriginal() throws Exception {
        SimpleSdpMessage original =
                SdpUtils.createSdpForMsrp(/* address= */ "192.168.1.1", /* isTls= */ false);

        SimpleSdpMessage parsedSdp =
                SimpleSdpMessage.parse(new ByteArrayInputStream(original.encode().getBytes(UTF_8)));

        assertThat(parsedSdp).isEqualTo(original);
    }
}
