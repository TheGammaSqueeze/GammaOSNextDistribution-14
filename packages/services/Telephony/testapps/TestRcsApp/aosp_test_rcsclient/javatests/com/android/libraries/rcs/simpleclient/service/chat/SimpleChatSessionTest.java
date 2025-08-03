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

package com.android.libraries.rcs.simpleclient.service.chat;

import static com.google.common.labs.truth.FutureSubject.assertThat;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.libraries.rcs.simpleclient.SimpleRcsClientContext;
import com.android.libraries.rcs.simpleclient.protocol.msrp.MsrpManager;
import com.android.libraries.rcs.simpleclient.protocol.sdp.SdpUtils;
import com.android.libraries.rcs.simpleclient.protocol.sip.SipSession;
import com.android.libraries.rcs.simpleclient.protocol.sip.SipSessionConfiguration;
import com.android.libraries.rcs.simpleclient.protocol.sip.SipSessionListener;

import com.google.common.collect.Lists;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;

import gov.nist.javax.sip.message.SIPRequest;
import gov.nist.javax.sip.message.SIPResponse;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;

import javax.sip.message.Message;
import javax.sip.message.Request;

@RunWith(AndroidJUnit4.class)
public class SimpleChatSessionTest {
    private static final String LOCAL_URI = "tel:+1234567890";
    private static final String REMOTE_URI = "tel:+1234567891";
    private final MsrpManager msrpManager =
            new MsrpManager(ApplicationProvider.getApplicationContext());
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
                    return "IEEE-802.11;i-wlan-node-id=PANIC01EB5B0";
                }

                @Override
                public String getPlaniHeader() {
                    return "IEEE-802.11;i-wlan-node-id=PLANI01EB5B0";
                }

                @Override
                public String getUserAgentHeader() {
                    return "Test-Client";
                }

                @Override
                public int getMaxPayloadSizeOnUdp() {
                    return 0;
                }
            };
    private final SimpleRcsClientContext context =
            new SimpleRcsClientContext(
                    /* provisioningController= */ null,
                    /* registrationController= */ null,
                    /* imsService= */ null,
                    new SipSession() {
                        @Override
                        public SipSessionConfiguration getSessionConfiguration() {
                            return configuration;
                        }

                        @Override
                        public ListenableFuture<Boolean> send(Message message) {
                            return Futures.immediateFuture(true);
                        }

                        @Override
                        public void setSessionListener(SipSessionListener listener) {
                        }
                    });

    @Test
    public void start_reply200_returnSuccessfulFuture() throws Exception {
        SimpleChatSession session =
                new SimpleChatSession(
                        context,
                        new MinimalCpmChatService(ApplicationProvider.getApplicationContext()) {
                            @Override
                            ListenableFuture<Boolean> sendSipRequest(SIPRequest msg,
                                    SimpleChatSession session) {
                                if (msg.getMethod().equals(Request.INVITE)) {
                                    SIPResponse response = msg.createResponse(/* statusCode= */
                                            200);
                                    response.setMessageContent(
                                            /* type= */ "application",
                                            /* subType= */ "sdp",
                                            SdpUtils.createSdpForMsrp(/* address= */
                                                    "127.0.0.1", /* isTls= */ false)
                                                    .encode());
                                    session.receiveMessage(response);
                                }
                                return Futures.immediateFuture(true);
                            }
                        },
                        msrpManager);

        // session.start should return the successful void future.
        assertThat(session.start(REMOTE_URI)).whenDone().isSuccessful();
    }

    @Test
    public void start_reply404_returnFailedFuture() throws Exception {
        SimpleChatSession session =
                new SimpleChatSession(
                        context,
                        new MinimalCpmChatService(ApplicationProvider.getApplicationContext()) {
                            @Override
                            ListenableFuture<Boolean> sendSipRequest(SIPRequest msg,
                                    SimpleChatSession session) {
                                if (msg.getMethod().equals(Request.INVITE)) {
                                    SIPResponse response = msg.createResponse(/* statusCode= */
                                            404);
                                    session.receiveMessage(response);
                                }
                                return Futures.immediateFuture(true);
                            }
                        },
                        msrpManager);

        // session.start should return the failed future with the exception.
        assertThat(session.start(REMOTE_URI)).whenDone().isFailedWith(ChatServiceException.class);
    }
}
