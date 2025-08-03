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

import android.content.Context;
import android.telephony.ims.SipDelegateConnection;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.Nullable;

import com.android.libraries.rcs.simpleclient.SimpleRcsClientContext;
import com.android.libraries.rcs.simpleclient.protocol.msrp.MsrpManager;
import com.android.libraries.rcs.simpleclient.protocol.sip.SipSession;
import com.android.libraries.rcs.simpleclient.protocol.sip.SipSessionListener;
import com.android.libraries.rcs.simpleclient.protocol.sip.SipUtils;
import com.android.libraries.rcs.simpleclient.service.ImsService;
import com.android.libraries.rcs.simpleclient.service.StateChangeCallback;

import com.google.common.collect.ImmutableSet;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;

import gov.nist.javax.sip.message.SIPRequest;
import gov.nist.javax.sip.message.SIPResponse;

import java.text.ParseException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import javax.sip.message.Request;
import javax.sip.message.Response;

/**
 * Minimal CPM chat session service that provides the interface creating a {@link SimpleChatSession}
 * instance using {@link SipDelegateConnection}.
 */
public class MinimalCpmChatService implements ImsService {
    public static final String CPM_SESSION_TAG =
            "+g.3gpp.icsi-ref=\"urn%3Aurn-7%3A3gpp-service.ims.icsi.oma.cpm.session\"";
    private static final String TAG = MinimalCpmChatService.class.getSimpleName();
    private final Map<String, SimpleChatSession> mTransactions = new HashMap<>();
    private final Map<String, SimpleChatSession> mDialogs = new HashMap<>();

    private final MsrpManager mMsrpManager;
    private SimpleRcsClientContext mContext;

    @Nullable
    private ChatServiceListener mListener;

    private final SipSessionListener mSipSessionListener =
            sipMessage -> {
                if (sipMessage instanceof SIPRequest) {
                    handleRequest((SIPRequest) sipMessage);
                } else if (sipMessage instanceof SIPResponse) {
                    handleResponse((SIPResponse) sipMessage);
                }
            };

    public MinimalCpmChatService(Context context) {
        mMsrpManager = new MsrpManager(context);
    }

    @Override
    public Set<String> getFeatureTags() {
        return ImmutableSet.of(CPM_SESSION_TAG);
    }

    @Override
    public void start(SimpleRcsClientContext context) {
        mContext = context;
        context.getSipSession().setSessionListener(mSipSessionListener);
    }

    @Override
    public void stop() {
    }

    @Override
    public void onStateChange(StateChangeCallback cb) {
    }

    /**
     * Start an originating 1:1 chat session interacting with the RCS server.
     *
     * @param telUriContact The remote contact in the from of TEL URI
     * @return The future will be completed with SimpleChatSession once the session is established
     * successfully. If the session fails for any reason, return the failed future with {@link
     * ChatServiceException}
     */
    public ListenableFuture<SimpleChatSession> startOriginatingChatSession(String telUriContact) {
        Log.i(TAG, "startOriginatingChatSession");
        SimpleChatSession session = new SimpleChatSession(mContext, this, mMsrpManager);
        return Futures.transform(
                session.start(telUriContact), v -> session, MoreExecutors.directExecutor());
    }

    ListenableFuture<Boolean> sendSipRequest(SIPRequest msg, SimpleChatSession session) {
        Log.i(TAG, "sendSipRequest:\r\n" + msg);
        if (!TextUtils.equals(msg.getMethod(), Request.ACK)) {
            mTransactions.put(msg.getTransactionId(), session);
        }

        if (TextUtils.equals(msg.getMethod(), Request.BYE)) {
            mDialogs.remove(msg.getDialogId(/* isServer= */ false));
        }

        SipSession sipSession = mContext.getSipSession();
        return sipSession.send(msg);
    }

    ListenableFuture<Boolean> sendSipResponse(SIPResponse msg, SimpleChatSession session) {
        Log.i(TAG, "sendSipResponse:\r\n" + msg);
        if (TextUtils.equals(msg.getCSeq().getMethod(), Request.BYE)) {
            mDialogs.remove(msg.getDialogId(/* isServer= */ true));
        } else if (TextUtils.equals(msg.getCSeq().getMethod(), Request.INVITE)
                && msg.getStatusCode() == Response.OK) {
            // Cache the dialog in order to route in-dialog request to the corresponding session.
            mDialogs.put(msg.getDialogId(/* isServer= */ true), session);
        }
        SipSession sipSession = mContext.getSipSession();
        return sipSession.send(msg);
    }

    private void handleRequest(SIPRequest request) {
        Log.i(TAG, "handleRequest:\r\n" + request);
        String dialogId = request.getDialogId(/* isServer= */ true);
        if (mDialogs.containsKey(dialogId)) {
            SimpleChatSession session = mDialogs.get(dialogId);
            session.receiveMessage(request);
        } else if (TextUtils.equals(request.getMethod(), Request.INVITE)) {
            SimpleChatSession session = new SimpleChatSession(mContext, this, mMsrpManager);
            session
                    .start(request)
                    .addListener(
                            () -> {
                                ChatServiceListener listener = mListener;
                                if (listener != null) {
                                    listener.onIncomingSession(session);
                                }
                            },
                            MoreExecutors.directExecutor());
        } else {
            // Reject non-INVITE request.
            try {
                SIPResponse response =
                        SipUtils.buildInviteResponse(
                                mContext.getSipSession().getSessionConfiguration(),
                                request,
                                Response.METHOD_NOT_ALLOWED,
                                null);
                sendSipResponse(response, /* session= */ null)
                        .addListener(() -> {
                        }, MoreExecutors.directExecutor());
            } catch (ParseException e) {
                Log.e(TAG, "Exception while sending response", e);
            }
        }
    }

    private void handleResponse(SIPResponse response) {
        Log.i(TAG, "handleResponse:\r\n" + response);
        // catch the exception because abnormal response always causes App to crash.
        try {
            SimpleChatSession session = mTransactions.get(response.getTransactionId());
            if (session != null) {
                if (response.isFinalResponse()) {
                    mTransactions.remove(response.getTransactionId());

                    // Cache the dialog in order to route in-dialog request to the corresponding
                    // session.
                    if (TextUtils.equals(response.getCSeq().getMethod(), Request.INVITE)
                            && response.getStatusCode() == Response.OK) {
                        mDialogs.put(response.getDialogId(/* isServer= */ false), session);
                    }
                }

                session.receiveMessage(response);
            }
        } catch (Exception e) {
            Log.e(TAG, e.getMessage());
            e.printStackTrace();
        }
    }

    /** Set new listener for the chat service. */
    public void setListener(@Nullable ChatServiceListener listener) {
        mListener = listener;
    }
}
