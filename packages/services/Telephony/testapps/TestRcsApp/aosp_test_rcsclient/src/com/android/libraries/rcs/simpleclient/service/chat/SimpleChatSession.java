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

import static com.android.libraries.rcs.simpleclient.protocol.cpim.CpimUtils.CPIM_CONTENT_TYPE;
import static com.android.libraries.rcs.simpleclient.service.chat.ChatServiceException.CODE_ERROR_SEND_MESSAGE_FAILED;
import static com.android.libraries.rcs.simpleclient.service.chat.ChatServiceException.CODE_ERROR_UNSPECIFIED;

import static java.nio.charset.StandardCharsets.UTF_8;

import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.Nullable;

import com.android.libraries.rcs.simpleclient.SimpleRcsClientContext;
import com.android.libraries.rcs.simpleclient.protocol.cpim.CpimUtils;
import com.android.libraries.rcs.simpleclient.protocol.cpim.SimpleCpimMessage;
import com.android.libraries.rcs.simpleclient.protocol.msrp.MsrpChunk;
import com.android.libraries.rcs.simpleclient.protocol.msrp.MsrpChunk.Continuation;
import com.android.libraries.rcs.simpleclient.protocol.msrp.MsrpChunkHeader;
import com.android.libraries.rcs.simpleclient.protocol.msrp.MsrpConstants;
import com.android.libraries.rcs.simpleclient.protocol.msrp.MsrpManager;
import com.android.libraries.rcs.simpleclient.protocol.msrp.MsrpSession;
import com.android.libraries.rcs.simpleclient.protocol.msrp.MsrpUtils;
import com.android.libraries.rcs.simpleclient.protocol.sdp.SdpUtils;
import com.android.libraries.rcs.simpleclient.protocol.sdp.SimpleSdpMessage;
import com.android.libraries.rcs.simpleclient.protocol.sip.SipSessionConfiguration;
import com.android.libraries.rcs.simpleclient.protocol.sip.SipUtils;
import com.android.libraries.rcs.simpleclient.service.chat.ChatServiceException.ErrorCode;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.SettableFuture;

import gov.nist.javax.sip.header.To;
import gov.nist.javax.sip.header.ims.PAssertedIdentityHeader;
import gov.nist.javax.sip.message.SIPRequest;
import gov.nist.javax.sip.message.SIPResponse;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.text.ParseException;
import java.util.UUID;

import javax.sip.address.URI;
import javax.sip.message.Message;
import javax.sip.message.Request;
import javax.sip.message.Response;

/**
 * Simple chat session implementation in order to send/receive a text message via SIP/MSRP
 * connection. Currently, this supports only a outgoing CPM session.
 */
public class SimpleChatSession {
    private static final String TAG = SimpleChatSession.class.getSimpleName();
    private final SimpleRcsClientContext mContext;
    private final MinimalCpmChatService mService;
    private final MsrpManager mMsrpManager;
    private final String mConversationId = UUID.randomUUID().toString();
    private SettableFuture<Void> mStartFuture;
    @Nullable
    private SIPRequest mInviteRequest;
    @Nullable
    private URI mRemoteUri;
    @Nullable
    private SimpleSdpMessage mRemoteSdp;
    @Nullable
    private SimpleSdpMessage mLocalSdp;
    @Nullable
    private MsrpSession mMsrpSession;
    @Nullable
    private ChatSessionListener mListener;


    public SimpleChatSession(
            SimpleRcsClientContext context, MinimalCpmChatService service,
            MsrpManager msrpManager) {
        mService = service;
        mContext = context;
        mMsrpManager = msrpManager;
    }

    public URI getRemoteUri() {
        return mRemoteUri;
    }

    /** Send a text message via MSRP session associated with this session. */
    public ListenableFuture<Void> sendMessage(String msg) {
        MsrpSession session = mMsrpSession;
        if (session == null || mRemoteSdp == null || mLocalSdp == null) {
            Log.e(TAG, "Session is not established");
            return Futures.immediateFailedFuture(
                    new IllegalStateException("Session is not established"));
        }

        // Build a new CPIM message and send it out through the MSRP session.
        SimpleCpimMessage cpim = CpimUtils.createForText(msg);
        Log.i(TAG, "Encoded CPIM:" + cpim.encode());

        byte[] content = cpim.encode().getBytes(UTF_8);
        MsrpChunk msrpChunk =
                MsrpChunk.newBuilder()
                        .method(MsrpChunk.Method.SEND)
                        .transactionId(MsrpUtils.generateRandomId())
                        .content(content)
                        .continuation(Continuation.COMPLETE)
                        .addHeader(MsrpConstants.HEADER_TO_PATH, mRemoteSdp.getPath().get())
                        .addHeader(MsrpConstants.HEADER_FROM_PATH, mLocalSdp.getPath().get())
                        .addHeader(MsrpConstants.HEADER_FAILURE_REPORT,
                                MsrpConstants.REPORT_VALUE_YES)
                        .addHeader(MsrpConstants.HEADER_SUCCESS_REPORT,
                                MsrpConstants.REPORT_VALUE_NO)
                        .addHeader(
                                MsrpConstants.HEADER_BYTE_RANGE,
                                String.format("1-%d/%d", content.length, content.length))
                        .addHeader(MsrpConstants.HEADER_MESSAGE_ID, MsrpUtils.generateRandomId())
                        .addHeader(MsrpConstants.HEADER_CONTENT_TYPE, CPIM_CONTENT_TYPE)
                        .build();

        Log.i(TAG, "Send a MSRP chunk: " + msrpChunk);
        return Futures.transformAsync(session.send(msrpChunk), result -> {
            if (result == null) {
                return Futures.immediateFailedFuture(
                        new ChatServiceException("Failed to send a chunk",
                                CODE_ERROR_SEND_MESSAGE_FAILED));
            }
            if (result.responseCode() != 200) {
                Log.d(TAG, "Received error response id=" + result.transactionId()
                        + " code=" + result.responseCode());
                return Futures.immediateFailedFuture(
                        new ChatServiceException("Msrp response code: " + result.responseCode(),
                                CODE_ERROR_SEND_MESSAGE_FAILED));
            }
            return Futures.immediateFuture(null);
        }, MoreExecutors.directExecutor());
    }

    /** Start outgoing chat session. */
    ListenableFuture<Void> start(String telUriContact) {
        if (mStartFuture != null) {
            return Futures.immediateFailedFuture(
                    new ChatServiceException("Session already started"));
        }

        SettableFuture<Void> future = SettableFuture.create();
        mStartFuture = future;
        mRemoteUri = SipUtils.createUri(telUriContact);
        try {
            SipSessionConfiguration configuration =
                    mContext.getSipSession().getSessionConfiguration();
            SimpleSdpMessage sdp = SdpUtils.createSdpForMsrp(configuration.getLocalIpAddress(),
                    false);
            SIPRequest invite =
                    SipUtils.buildInvite(
                            mContext.getSipSession().getSessionConfiguration(),
                            telUriContact,
                            mConversationId,
                            sdp.encode().getBytes(UTF_8));
            mInviteRequest = invite;
            mLocalSdp = sdp;
            Futures.addCallback(
                    mService.sendSipRequest(invite, this),
                    new FutureCallback<Boolean>() {
                        @Override
                        public void onSuccess(Boolean result) {
                            Log.i(TAG, "onSuccess:" + result);
                            if (!result) {
                                notifyFailure("Failed to send INVITE", CODE_ERROR_UNSPECIFIED);
                            }
                        }

                        @Override
                        public void onFailure(Throwable t) {
                            Log.i(TAG, "onFailure:" + t.getMessage());
                            notifyFailure("Failed to send INVITE", CODE_ERROR_UNSPECIFIED);
                        }
                    },
                    MoreExecutors.directExecutor());
        } catch (ParseException e) {
            Log.e(TAG, e.getMessage());
            e.printStackTrace();
            return Futures.immediateFailedFuture(
                    new ChatServiceException("Failed to build INVITE"));
        }

        return future;
    }

    /** Start incoming chat session. */
    ListenableFuture<Void> start(SIPRequest invite) {
        mInviteRequest = invite;
        int statusCode = Response.OK;
        if (!SipUtils.hasSdpContent(invite)) {
            statusCode = Response.NOT_ACCEPTABLE_HERE;
        } else {
            try {
                mRemoteSdp = SimpleSdpMessage.parse(
                        new ByteArrayInputStream(invite.getRawContent()));
            } catch (ParseException | IOException e) {
                statusCode = Response.BAD_REQUEST;
            }
        }

        updateRemoteUri(mInviteRequest);

        SipSessionConfiguration configuration = mContext.getSipSession().getSessionConfiguration();
        SimpleSdpMessage sdp = SdpUtils.createSdpForMsrp(configuration.getLocalIpAddress(), false);

        // Automatically reply back to the invite by building a pre-canned response.
        try {
            SIPResponse response = SipUtils.buildInviteResponse(configuration, invite, statusCode,
                    sdp);
            mLocalSdp = sdp;
            return Futures.transform(
                    mService.sendSipResponse(response, this), result -> null,
                    MoreExecutors.directExecutor());
        } catch (ParseException e) {
            Log.e(TAG, "Exception while building response", e);
            return Futures.immediateFailedFuture(e);
        }
    }

    /** Terminate the current SIP session. */
    public ListenableFuture<Void> terminate() {
        if (mInviteRequest == null) {
            return Futures.immediateFuture(null);
        }
        try {
            if (mMsrpSession != null) {
                mMsrpSession.terminate();
            }
        } catch (IOException e) {
            return Futures.immediateFailedFuture(
                    new ChatServiceException(
                            "Exception while terminating MSRP session", CODE_ERROR_UNSPECIFIED));
        }
        try {

            SettableFuture<Void> future = SettableFuture.create();
            Futures.addCallback(
                    mService.sendSipRequest(SipUtils.buildBye(mInviteRequest), this),
                    new FutureCallback<Boolean>() {
                        @Override
                        public void onSuccess(Boolean result) {
                            future.set(null);
                        }

                        @Override
                        public void onFailure(Throwable t) {
                            future.setException(
                                    new ChatServiceException("Failed to send BYE",
                                            CODE_ERROR_UNSPECIFIED, t));
                        }
                    },
                    MoreExecutors.directExecutor());
            return future;
        } catch (ParseException e) {
            return Futures.immediateFailedFuture(
                    new ChatServiceException("Failed to build BYE", CODE_ERROR_UNSPECIFIED));
        }
    }

    void receiveMessage(Message msg) {
        if (msg instanceof SIPRequest) {
            handleSipRequest((SIPRequest) msg);
        } else {
            handleSipResponse((SIPResponse) msg);
        }
    }

    private void handleSipRequest(SIPRequest request) {
        SIPResponse response;
        if (TextUtils.equals(request.getMethod(), Request.ACK)) {
            // Terminating session established, start a msrp session.
            if (mRemoteSdp != null) {
                startMsrpSession(mRemoteSdp);
            }
            return;
        }

        if (TextUtils.equals(request.getMethod(), Request.BYE)) {
            response = request.createResponse(Response.OK);
        } else {
            // Currently we support only INVITE and BYE.
            response = request.createResponse(Response.METHOD_NOT_ALLOWED);
        }
        Futures.addCallback(
                mService.sendSipResponse(response, this),
                new FutureCallback<Boolean>() {
                    @Override
                    public void onSuccess(Boolean result) {
                        if (result) {
                            Log.d(
                                    TAG,
                                    "Response to Call-Id: "
                                            + response.getCallId().getCallId()
                                            + " sent successfully");
                        } else {
                            Log.d(TAG, "Failed to send response");
                        }
                    }

                    @Override
                    public void onFailure(Throwable t) {
                        Log.d(TAG, "Exception while sending response: ", t);
                    }
                },
                MoreExecutors.directExecutor());
    }

    private void handleSipResponse(SIPResponse response) {
        int code = response.getStatusCode();

        // Nothing to do for a provisional response.
        if (response.isFinalResponse()) {
            if (code == Response.OK) {
                handle200OK(response);
            } else {
                handleNon200(response);
            }
        }
    }

    private void handleNon200(SIPResponse response) {
        Log.d(TAG, "Received error response code=" + response.getStatusCode());
        notifyFailure("Received non-200 INVITE response", CODE_ERROR_UNSPECIFIED);
    }

    private void handle200OK(SIPResponse response) {
        if (!SipUtils.hasSdpContent(response)) {
            notifyFailure("Content is not a SDP", CODE_ERROR_UNSPECIFIED);
            return;
        }

        SimpleSdpMessage sdp;
        try {
            sdp = SimpleSdpMessage.parse(new ByteArrayInputStream(response.getRawContent()));
        } catch (ParseException | IOException e) {
            notifyFailure("Invalid SDP in INVITE", CODE_ERROR_UNSPECIFIED);
            return;
        }

        if (mInviteRequest == null) {
            notifyFailure("No INVITE request sent out", CODE_ERROR_UNSPECIFIED);
            return;
        }

        SIPRequest ack = mInviteRequest.createAckRequest((To) response.getToHeader());
        Futures.addCallback(
                mService.sendSipRequest(ack, this),
                new FutureCallback<Boolean>() {
                    @Override
                    public void onSuccess(Boolean result) {
                        if (result) {
                            startMsrpSession(sdp);
                            mRemoteSdp = sdp;
                        } else {
                            notifyFailure("Failed to send ACK", CODE_ERROR_UNSPECIFIED);
                        }
                    }

                    @Override
                    public void onFailure(Throwable t) {
                        notifyFailure("Failed to send ACK", CODE_ERROR_UNSPECIFIED);
                    }
                },
                MoreExecutors.directExecutor());
    }

    private void notifyFailure(String message, @ErrorCode int code) {
        if (mStartFuture != null) {
            mStartFuture.setException(new ChatServiceException(message, code));
            mStartFuture = null;
        }
    }

    private void notifySuccess() {
        if (mStartFuture != null) {
            mStartFuture.set(null);
            mStartFuture = null;
        }
    }

    private void startMsrpSession(SimpleSdpMessage remoteSdp) {
        Log.d(TAG, "Start MSRP session: " + remoteSdp);
        if (remoteSdp.getAddress().isPresent() && remoteSdp.getPort().isPresent()) {
            String localIp = getLocalIp();
            Futures.addCallback(
                    mMsrpManager.createMsrpSession(
                            remoteSdp.getAddress().get(), remoteSdp.getPort().getAsInt(), localIp,
                            0 /* localPort */, this::receiveMsrpChunk),
                    new FutureCallback<MsrpSession>() {
                        @Override
                        public void onSuccess(MsrpSession result) {
                            mMsrpSession = result;
                            sendEmptyPacket();
                            notifySuccess();
                        }

                        @Override
                        public void onFailure(Throwable t) {
                            Log.e(TAG, "Failed to create msrp session", t);
                            notifyFailure("Failed to establish msrp session",
                                    CODE_ERROR_UNSPECIFIED);
                            terminate()
                                    .addListener(
                                            () -> Log.d(TAG, "Session terminated"),
                                            MoreExecutors.directExecutor());
                        }
                    },
                    MoreExecutors.directExecutor());
        } else {
            Log.e(TAG, "Address or port is not present");
        }
    }

    private void sendEmptyPacket() {
        MsrpChunk msrpChunk =
                MsrpChunk.newBuilder()
                        .method(MsrpChunk.Method.SEND)
                        .transactionId(MsrpUtils.generateRandomId())
                        .continuation(Continuation.COMPLETE)
                        .addHeader(MsrpConstants.HEADER_TO_PATH, mRemoteSdp.getPath().get())
                        .addHeader(MsrpConstants.HEADER_FROM_PATH, mLocalSdp.getPath().get())
                        .addHeader(MsrpConstants.HEADER_FAILURE_REPORT,
                                MsrpConstants.REPORT_VALUE_NO)
                        .addHeader(MsrpConstants.HEADER_SUCCESS_REPORT,
                                MsrpConstants.REPORT_VALUE_NO)
                        .addHeader(MsrpConstants.HEADER_BYTE_RANGE, "1/0-0")
                        .addHeader(MsrpConstants.HEADER_MESSAGE_ID, MsrpUtils.generateRandomId())
                        .build();

        mMsrpSession.send(msrpChunk);
    }

    private String getLocalIp() {
        SipSessionConfiguration configuration = mContext.getSipSession().getSessionConfiguration();
        return configuration.getLocalIpAddress();
    }

    private void receiveMsrpChunk(MsrpChunk chunk) {
        Log.d(TAG, "Received msrp= " + chunk + " conversation=" + mConversationId);

        MsrpChunkHeader contentTypeHeader = chunk.header("Content-Type");
        if (chunk.content().length == 0 || contentTypeHeader == null) {
            Log.i(TAG, "No content or Content-Type header, drop it");
            return;
        }

        String contentType = contentTypeHeader.value();
        if ("message/cpim".equals(contentType)) {
            Log.d(TAG, "Received CPIM: " + new String(chunk.content(), UTF_8));
            try {
                SimpleCpimMessage cpim = SimpleCpimMessage.parse(chunk.content());
                if (mListener != null) {
                    mListener.onMessageReceived(cpim);
                }
            } catch (Exception e) {
                Log.e(TAG, "Error while parsing cpim message.", e);
            }
        } else {
            Log.w(TAG, contentType + " is not supported.");
        }
    }


    /** Set new listener for this session. */
    public void setListener(@Nullable ChatSessionListener listener) {
        mListener = listener;
    }

    private void updateRemoteUri(SIPRequest request) {
        PAssertedIdentityHeader pAssertedIdentityHeader =
                (PAssertedIdentityHeader) request.getHeader("P-Asserted-Identity");
        if (pAssertedIdentityHeader == null) {
            mRemoteUri = request.getFrom().getAddress().getURI();
        } else {
            mRemoteUri = pAssertedIdentityHeader.getAddress().getURI();
        }
    }
}
