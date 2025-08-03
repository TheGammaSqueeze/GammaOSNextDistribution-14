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

import static com.android.libraries.rcs.simpleclient.protocol.sdp.SdpUtils.SDP_CONTENT_SUB_TYPE;
import static com.android.libraries.rcs.simpleclient.protocol.sdp.SdpUtils.SDP_CONTENT_TYPE;

import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.Nullable;

import com.android.libraries.rcs.simpleclient.protocol.sdp.SimpleSdpMessage;

import com.google.common.base.Ascii;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.net.InetAddresses;

import gov.nist.javax.sip.Utils;
import gov.nist.javax.sip.address.AddressFactoryImpl;
import gov.nist.javax.sip.header.ContentType;
import gov.nist.javax.sip.header.HeaderFactoryImpl;
import gov.nist.javax.sip.header.Via;
import gov.nist.javax.sip.header.extensions.SessionExpires;
import gov.nist.javax.sip.header.ims.PPreferredIdentityHeader;
import gov.nist.javax.sip.header.ims.PPreferredServiceHeader;
import gov.nist.javax.sip.header.ims.SecurityVerifyHeader;
import gov.nist.javax.sip.message.SIPMessage;
import gov.nist.javax.sip.message.SIPRequest;
import gov.nist.javax.sip.message.SIPResponse;

import java.net.Inet6Address;
import java.text.ParseException;
import java.util.List;
import java.util.UUID;

import javax.sip.InvalidArgumentException;
import javax.sip.address.AddressFactory;
import javax.sip.address.SipURI;
import javax.sip.address.URI;
import javax.sip.header.ContactHeader;
import javax.sip.header.Header;
import javax.sip.header.HeaderFactory;
import javax.sip.header.ViaHeader;
import javax.sip.message.Request;
import javax.sip.message.Response;

/** Collections of utility functions for SIP */
public final class SipUtils {
    private static final String TAG = "SipUtils";
    private static final String SUPPORTED_TIMER_TAG = "timer";
    private static final String ICSI_REF_PARAM_NAME = "+g.3gpp.icsi-ref";
    private static final String SIP_INSTANCE_PARAM_NAME = "+sip.instance";
    private static final String CPM_SESSION_FEATURE_TAG_PARAM_VALUE =
            "\"urn%3Aurn-7%3A3gpp-service.ims.icsi.oma.cpm.session\"";
    private static final String CPM_SESSION_FEATURE_TAG_FULL_STRING =
            "+g.3gpp.icsi-ref=\"urn%3Aurn-7%3A3gpp-service.ims.icsi.oma.cpm.session\"";
    private static final String CPM_SESSION_SERVICE_NAME =
            "urn:urn-7:3gpp-service.ims.icsi.oma.cpm.session";
    private static final String CONTRIBUTION_ID_HEADER_NAME = "Contribution-ID";
    private static final String CONVERSATION_ID_HEADER_NAME = "Conversation-ID";
    private static final String ACCEPT_CONTACT_HEADER_NAME = "Accept-Contact";
    private static final String PANI_HEADER_NAME = "P-Access-Network-Info";
    private static final String PLANI_HEADER_NAME = "P-Last-Access-Network-Info";
    private static final String USER_AGENT_HEADER = "RcsTestClient";

    private static AddressFactory sAddressFactory = new AddressFactoryImpl();
    private static HeaderFactory sHeaderFactory = new HeaderFactoryImpl();

    private SipUtils() {
    }

    /**
     * Try to parse the given uri.
     *
     * @throws IllegalArgumentException in case of parsing error.
     */
    public static URI createUri(String uri) {
        try {
            return sAddressFactory.createURI(uri);
        } catch (ParseException exception) {
            throw new IllegalArgumentException("URI cannot be created", exception);
        }
    }

    /**
     * Create SIP INVITE request for a CPM 1:1 chat.
     *
     * @param configuration  The SipSessionConfiguration instance used for populating SIP headers.
     * @param targetUri      The uri to be targeted.
     * @param conversationId The id to be contained in Conversation-ID header.
     */
    public static SIPRequest buildInvite(
            SipSessionConfiguration configuration,
            String targetUri,
            String conversationId,
            byte[] content)
            throws ParseException {
        String address = configuration.getLocalIpAddress();
        int port = configuration.getLocalPort();
        String transport = configuration.getSipTransport();
        List<String> associatedUris = configuration.getAssociatedUris();
        String preferredUri = Iterables.getFirst(associatedUris,
                configuration.getPublicUserIdentity());

        SIPRequest request = new SIPRequest();
        request.setMethod(Request.INVITE);

        URI remoteUri = createUri(targetUri);
        request.setRequestURI(remoteUri);
        request.setFrom(
                sHeaderFactory.createFromHeader(
                        sAddressFactory.createAddress(preferredUri),
                        Utils.getInstance().generateTag()));
        request.setTo(
                sHeaderFactory.createToHeader(sAddressFactory.createAddress(remoteUri), null));

        ViaHeader viaHeader = null;

        try {
            // Set a default Max-Forwards header.
            request.setMaxForwards(sHeaderFactory.createMaxForwardsHeader(70));
            request.setCSeq(sHeaderFactory.createCSeqHeader(1L, Request.INVITE));
            viaHeader =
                    sHeaderFactory.createViaHeader(
                            address, port, transport, Utils.getInstance().generateBranchId());
            request.setVia(ImmutableList.of(viaHeader));

            // Set a default Session-Expires header.
            SessionExpires sessionExpires = new SessionExpires();
            sessionExpires.setRefresher("uac");
            sessionExpires.setExpires(1800);
            request.setHeader(sessionExpires);

            // Set a Contact header.
            request.setHeader(generateContactHeader(configuration));

            // Set PANI and PLANI if exists
            if (configuration.getPaniHeader() != null) {
                request.setHeader(
                        sHeaderFactory.createHeader(PANI_HEADER_NAME,
                                configuration.getPaniHeader()));
            }
            if (configuration.getPlaniHeader() != null) {
                request.setHeader(
                        sHeaderFactory.createHeader(PLANI_HEADER_NAME,
                                configuration.getPlaniHeader()));
            }
        } catch (InvalidArgumentException e) {
            // Nothing to do here
            Log.e(TAG, e.getMessage());
        }

        request.setCallId(UUID.randomUUID().toString());
        request.setHeader(sHeaderFactory.createHeader(CONVERSATION_ID_HEADER_NAME, conversationId));
        request.setHeader(
                sHeaderFactory.createHeader(CONTRIBUTION_ID_HEADER_NAME,
                        UUID.randomUUID().toString()));

        String acceptContact = "*;" + CPM_SESSION_FEATURE_TAG_FULL_STRING;
        request.setHeader(sHeaderFactory.createHeader(ACCEPT_CONTACT_HEADER_NAME, acceptContact));
        request.setHeader(sHeaderFactory.createSupportedHeader(SUPPORTED_TIMER_TAG));
        request.setHeader(sHeaderFactory.createHeader(PPreferredIdentityHeader.NAME, preferredUri));
        request.setHeader(
                sHeaderFactory.createHeader(PPreferredServiceHeader.NAME,
                        CPM_SESSION_SERVICE_NAME));

        // Set a Security-Verify header if exist.
        String securityVerify = configuration.getSecurityVerifyHeader();
        if (!TextUtils.isEmpty(securityVerify)) {
            request.setHeaders(sHeaderFactory.createHeaders(SecurityVerifyHeader.NAME
                    + ":" + securityVerify.trim()));
        }

        // Add Route headers.
        List<String> serviceRoutes = configuration.getServiceRouteHeaders();
        if (!serviceRoutes.isEmpty()) {
            for (String sr : serviceRoutes) {
                request.addHeader(
                        sHeaderFactory.createRouteHeader(sAddressFactory.createAddress(sr)));
            }
        }

        String userAgent = configuration.getUserAgentHeader();
        userAgent = (userAgent == null) ? USER_AGENT_HEADER : userAgent;
        request.addHeader(sHeaderFactory.createUserAgentHeader(ImmutableList.of(userAgent)));

        request.setMessageContent(SDP_CONTENT_TYPE, SDP_CONTENT_SUB_TYPE, content);

        if (viaHeader != null && Ascii.equalsIgnoreCase("udp", transport)) {
            String newTransport =
                    determineTransportBySize(configuration, request.encodeAsBytes("udp").length);
            if (!Ascii.equalsIgnoreCase(transport, newTransport)) {
                viaHeader.setTransport(newTransport);
            }
        }

        return request;
    }

    private static ContactHeader generateContactHeader(SipSessionConfiguration configuration)
            throws ParseException {
        String host = configuration.getLocalIpAddress();
        if (isIPv6Address(host)) {
            host = "[" + host + "]";
        }

        String userPart = configuration.getContactUser();
        SipURI uri = sAddressFactory.createSipURI(userPart, host);
        try {
            uri.setPort(configuration.getLocalPort());
            uri.setTransportParam(configuration.getSipTransport());
        } catch (Exception e) {
            // Shouldn't be here.
        }

        ContactHeader contactHeader =
                sHeaderFactory.createContactHeader(sAddressFactory.createAddress(uri));

        // Add +sip.instance param.
        String sipInstance = "\"<urn:gsma:imei:" + configuration.getImei() + ">\"";
        contactHeader.setParameter(SIP_INSTANCE_PARAM_NAME, sipInstance);

        // Add CPM feature tag.
        uri.setTransportParam(configuration.getSipTransport());
        contactHeader.setParameter(ICSI_REF_PARAM_NAME, CPM_SESSION_FEATURE_TAG_PARAM_VALUE);

        return contactHeader;
    }

    /**
     * Create a SIP BYE request for terminating the chat session.
     *
     * @param invite the initial INVITE request of the chat session.
     */
    public static SIPRequest buildBye(SIPRequest invite) throws ParseException {
        SIPRequest request = new SIPRequest();
        request.setRequestURI(invite.getRequestURI());
        request.setMethod(Request.BYE);
        try {
            long cSeqNumber = invite.getCSeq().getSeqNumber();
            request.setHeader(sHeaderFactory.createCSeqHeader(cSeqNumber, Request.BYE));
        } catch (InvalidArgumentException e) {
            // Nothing to do here
        }

        request.setCallId(invite.getCallId());

        Via via = (Via) invite.getTopmostVia().clone();
        via.removeParameter("branch");
        via.setBranch(Utils.getInstance().generateBranchId());
        request.addHeader(via);
        request.addHeader(
                sHeaderFactory.createFromHeader(invite.getFrom().getAddress(),
                        invite.getFrom().getTag()));
        request.addHeader(
                sHeaderFactory.createToHeader(invite.getTo().getAddress(),
                        invite.getTo().getTag()));

        return request;
    }

    /**
     * Create SIP INVITE response for a CPM 1:1 chat.
     *
     * @param configuration The SipSessionConfiguration instance used for populating SIP headers.
     * @param invite        the initial INVITE request of the chat session.
     * @param code          The status code of the response.
     */
    public static SIPResponse buildInviteResponse(
            SipSessionConfiguration configuration,
            SIPRequest invite,
            int code,
            @Nullable SimpleSdpMessage sdp)
            throws ParseException {
        SIPResponse response = invite.createResponse(code);
        if (code == Response.OK) {
            response.setMessageContent(SDP_CONTENT_TYPE, SDP_CONTENT_SUB_TYPE, sdp.encode());
        }
        response.setToTag(Utils.getInstance().generateTag());

        // Set a Contact header.
        response.setHeader(generateContactHeader(configuration));

        // Set Conversation-ID and Contribution-ID
        Header conversationIdHeader = invite.getHeader(CONVERSATION_ID_HEADER_NAME);
        if (conversationIdHeader != null) {
            response.setHeader((Header) conversationIdHeader.clone());
        }
        Header contributionIdHeader = invite.getHeader(CONTRIBUTION_ID_HEADER_NAME);
        if (conversationIdHeader != null) {
            response.setHeader((Header) contributionIdHeader.clone());
        }

        // Set P-Preferred-Identity
        List<String> associatedUris = configuration.getAssociatedUris();
        String preferredUri = Iterables.getFirst(associatedUris,
                configuration.getPublicUserIdentity());
        response.setHeader(
                sHeaderFactory.createHeader(PPreferredIdentityHeader.NAME, preferredUri));

        // Set PANI and PLANI if exists
        if (configuration.getPaniHeader() != null) {
            response.setHeader(
                    sHeaderFactory.createHeader(PANI_HEADER_NAME, configuration.getPaniHeader()));
        }
        if (configuration.getPlaniHeader() != null) {
            response.setHeader(
                    sHeaderFactory.createHeader(PLANI_HEADER_NAME, configuration.getPlaniHeader()));
        }
        return response;
    }

    public static boolean isIPv6Address(String address) {
        return InetAddresses.forString(address) instanceof Inet6Address;
    }

    /** Return whether the SIP message has a SDP content or not */
    public static boolean hasSdpContent(SIPMessage message) {
        ContentType contentType = message.getContentTypeHeader();
        return contentType != null
                && TextUtils.equals(contentType.getContentType(), SDP_CONTENT_TYPE)
                && TextUtils.equals(contentType.getContentSubType(), SDP_CONTENT_SUB_TYPE);
    }

    private static String determineTransportBySize(SipSessionConfiguration configuration,
            int size) {
        if (size > configuration.getMaxPayloadSizeOnUdp()) {
            return "tcp";
        }
        return "udp";
    }
}
