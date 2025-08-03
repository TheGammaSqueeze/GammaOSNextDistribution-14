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

package com.android.libraries.rcs.simpleclient.protocol.sip;

import static com.google.common.truth.Truth.assertThat;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.google.common.collect.Lists;

import gov.nist.javax.sip.message.SIPRequest;

import java.util.List;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class SipUtilsTest {
    private static final String LOCAL_URI = "tel:+1234567890";
    private static final String REMOTE_URI = "tel:+1234567891";
    private static final String CONVERSATION_ID = "abcd-1234";

    SipSessionConfiguration configuration =
            new SipSessionConfiguration() {
                @Override
                public long getVersion() {
                    return 0;
                }

                @Override
                public String getOutboundProxyAddr() {
                    return "3001:4870:e00b:5e94:21b8:8d20:c425:5e6c";
                }

                @Override
                public int getOutboundProxyPort() {
                    return 5060;
                }

                @Override
                public String getLocalIpAddress() {
                    return "2001:4870:e00b:5e94:21b8:8d20:c425:5e6c";
                }

                @Override
                public int getLocalPort() {
                    return 5060;
                }

                @Override
                public String getSipTransport() {
                    return "TCP";
                }

                @Override
                public String getPublicUserIdentity() {
                    return "sip:+1234567890@foo.bar";
                }

                @Override
                public String getDomain() {
                    return "foo.bar";
                }

                @Override
                public List<String> getAssociatedUris() {
                    return Lists.newArrayList(LOCAL_URI, "sip:+1234567890@foo.bar");
                }

                @Override
                public String getSecurityVerifyHeader() {
                    return "ipsec-3gpp;q=0.5;alg=hmac-sha-1-96;prot=esp;mod=trans;ealg=null;"
                            + "spi-c=983227540;spi-s=2427966379;port-c=65528;port-s=65529";
                }

                @Override
                public List<String> getServiceRouteHeaders() {
                    return Lists.newArrayList();
                }

                @Override
                public String getContactUser() {
                    return "abcd-efgh";
                }

                @Override
                public String getImei() {
                    return "35293211-111080-0";
                }

                @Override
                public String getPaniHeader() {
                    return null;
                }

                @Override
                public String getPlaniHeader() {
                    return null;
                }

                @Override
                public int getMaxPayloadSizeOnUdp() {
                    return 0;
                }
            };

    @Test
    public void buildInvite_returnExpectedInviteMessage() throws Exception {
        SIPRequest request = SipUtils.buildInvite(configuration, REMOTE_URI, CONVERSATION_ID);

        assertThat(request.getRequestURI().toString()).isEqualTo(REMOTE_URI);
        assertThat(request.getFrom().getAddress().getURI().toString()).isEqualTo(LOCAL_URI);
        assertThat(request.getTo().getAddress().getURI().toString()).isEqualTo(REMOTE_URI);
        assertThat(request.hasHeader("Conversation-ID")).isTrue();
        assertThat(request.hasHeader("Contribution-ID")).isTrue();
        assertThat(request.hasHeader("Accept-Contact")).isTrue();
        assertThat(request.hasHeader("Security-Verify")).isTrue();
    }

    @Test
    public void buildInvite_sizeIsGreaterThanMaxPayloadSize_transportShouldBeTcp()
            throws Exception {
        SIPRequest request = SipUtils.buildInvite(configuration, REMOTE_URI, CONVERSATION_ID);

        // The size is always greater than maxPayloadSizeOnUdp = 0
        assertThat(request.getTopmostVia().getTransport()).isEqualTo("TCP");
    }
}
