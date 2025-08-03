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

package com.google.android.sample.rcsclient.util;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.telephony.ims.ImsManager;
import android.text.TextUtils;
import android.util.Log;

import com.android.libraries.rcs.simpleclient.SimpleRcsClient;
import com.android.libraries.rcs.simpleclient.SimpleRcsClient.State;
import com.android.libraries.rcs.simpleclient.provisioning.ProvisioningController;
import com.android.libraries.rcs.simpleclient.provisioning.StaticConfigProvisioningController;
import com.android.libraries.rcs.simpleclient.registration.RegistrationController;
import com.android.libraries.rcs.simpleclient.registration.RegistrationControllerImpl;
import com.android.libraries.rcs.simpleclient.service.chat.MinimalCpmChatService;
import com.android.libraries.rcs.simpleclient.service.chat.SimpleChatSession;

import com.google.android.sample.rcsclient.RcsStateChangedCallback;
import com.google.android.sample.rcsclient.SessionStateCallback;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;

import gov.nist.javax.sip.address.AddressFactoryImpl;

import java.text.ParseException;
import java.time.Instant;
import java.util.HashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.sip.address.AddressFactory;
import javax.sip.address.URI;

/**
 * This class takes advantage of rcs library to manage chat session and send/receive chat message.
 */
public class ChatManager {
    public static final String SELF = "self";
    private static final String TAG = "TestRcsApp.ChatManager";
    private static final String TELURI_PREFIX = "tel:";
    private static AddressFactory sAddressFactory = new AddressFactoryImpl();
    private static HashMap<Integer, ChatManager> sChatManagerInstances = new HashMap<>();
    private final ExecutorService mFixedThreadPool = Executors.newFixedThreadPool(5);
    private Context mContext;
    private ProvisioningController mProvisioningController;
    private RegistrationController mRegistrationController;
    private MinimalCpmChatService mImsService;
    private SimpleRcsClient mSimpleRcsClient;
    private State mState;
    private int mSubId;
    private HashMap<String, SimpleChatSession> mContactSessionMap = new HashMap<>();
    private RcsStateChangedCallback mRcsStateChangedCallback;

    private ChatManager(Context context, int subId) {
        mContext = context;
        mSubId = subId;
        mProvisioningController = StaticConfigProvisioningController.createForSubscriptionId(subId,
                context);
        ImsManager imsManager = mContext.getSystemService(ImsManager.class);
        mRegistrationController = new RegistrationControllerImpl(subId, mFixedThreadPool,
                imsManager);
        mImsService = new MinimalCpmChatService(context);
        mSimpleRcsClient = SimpleRcsClient.newBuilder()
                .registrationController(mRegistrationController)
                .provisioningController(mProvisioningController)
                .imsService(mImsService).build();

        mState = State.NEW;
        // register callback for state change
        mSimpleRcsClient.onStateChanged((oldState, newState) -> {
            Log.i(TAG, "notifyStateChange() oldState:" + oldState + " newState:" + newState);
            mState = newState;
            mRcsStateChangedCallback.notifyStateChange(oldState, newState);
        });
        mImsService.setListener((session) -> {
            Log.i(TAG, "onIncomingSession():" + session.getRemoteUri());
            String phoneNumber = getNumberFromUri(session.getRemoteUri().toString());
            mContactSessionMap.put(phoneNumber, session);
            session.setListener(
                    // implement onMessageReceived()
                    (message) -> {
                        mFixedThreadPool.execute(() -> {
                            String msg = message.content();
                            if (TextUtils.isEmpty(phoneNumber)) {
                                Log.i(TAG, "dest number is empty, uri:"
                                        + session.getRemoteUri());
                            } else {
                                addNewMessage(msg, phoneNumber, SELF);
                            }
                        });
                    });
        });
    }

    /**
     * Create ChatManager with a specific subId.
     */
    public static ChatManager getInstance(Context context, int subId) {
        synchronized (sChatManagerInstances) {
            if (sChatManagerInstances.containsKey(subId)) {
                return sChatManagerInstances.get(subId);
            }
            ChatManager chatManager = new ChatManager(context, subId);
            sChatManagerInstances.put(subId, chatManager);
            return chatManager;
        }
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

    private static String getNumberFromUri(String number) {
        String[] numberParts = number.split("[@;:]");
        if (numberParts.length < 2) {
            return null;
        }
        return numberParts[1];
    }

    /**
     * set callback for RCS state change.
     */
    public void setRcsStateChangedCallback(RcsStateChangedCallback callback) {
        mRcsStateChangedCallback = callback;
    }

    /**
     * Start to register by doing provisioning and creating SipDelegate
     */
    public void register() {
        Log.i(TAG, "do start(), State State = " + mState);
        if (mState == State.NEW) {
            mSimpleRcsClient.start();
        }
    }

    /**
     * Deregister chat feature.
     */
    public void deregister() {
        Log.i(TAG, "deregister");
        sChatManagerInstances.remove(mSubId);
        mSimpleRcsClient.stop();
    }

    /**
     * Initiate 1 to 1 chat session.
     *
     * @param contact  destination phone number.
     * @param callback callback for session state.
     */
    public void initChatSession(String contact, SessionStateCallback callback) {
        if (mState != State.REGISTERED) {
            Log.i(TAG, "Could not init session due to State = " + mState);
            return;
        }
        Log.i(TAG, "initChatSession contact: " + contact);
        if (mContactSessionMap.containsKey(contact)) {
            callback.onSuccess();
            Log.i(TAG, "contact exists");
            return;
        }
        Futures.addCallback(
                mImsService.startOriginatingChatSession(TELURI_PREFIX + contact),
                new FutureCallback<SimpleChatSession>() {
                    @Override
                    public void onSuccess(SimpleChatSession chatSession) {
                        String phoneNumber = getNumberFromUri(
                                chatSession.getRemoteUri().toString());
                        mContactSessionMap.put(phoneNumber, chatSession);
                        chatSession.setListener(
                                // implement onMessageReceived()
                                (message) -> {
                                    mFixedThreadPool.execute(() -> {
                                        String msg = message.content();
                                        if (TextUtils.isEmpty(phoneNumber)) {
                                            Log.i(TAG, "dest number is empty, uri:"
                                                    + chatSession.getRemoteUri());
                                        } else {
                                            addNewMessage(msg, phoneNumber, SELF);
                                        }
                                    });

                                });
                        callback.onSuccess();
                    }

                    @Override
                    public void onFailure(Throwable t) {
                        callback.onFailure();
                    }
                },
                MoreExecutors.directExecutor());
    }

    /**
     * Send a chat message.
     *
     * @param contact destination phone number.
     * @param message chat message.
     */
    public ListenableFuture<Void> sendMessage(String contact, String message) {
        SimpleChatSession chatSession = mContactSessionMap.get(contact);
        if (chatSession == null) {
            Log.i(TAG, "session is unavailable for contact = " + contact);
            return Futures.immediateFailedFuture(
                    new IllegalStateException("Chat session does not exist"));
        }
        return chatSession.sendMessage(message);
    }

    public boolean isRegistered() {
        return (mState == State.REGISTERED);
    }

    /**
     * Terminate the chat session.
     *
     * @param contact destination phone number.
     */
    public void terminateSession(String contact) {
        Log.i(TAG, "terminateSession");
        SimpleChatSession chatSession = mContactSessionMap.get(contact);
        if (chatSession == null) {
            Log.i(TAG, "session is unavailable for contact = " + contact);
            return;
        }
        chatSession.terminate();
        mContactSessionMap.remove(contact);
    }

    /**
     * Insert chat information into database.
     *
     * @param message chat message.
     * @param src source phone number.
     * @param dest destination phone number.
     */
    public Uri addNewMessage(String message, String src, String dest) {
        long currentTime = Instant.now().getEpochSecond();
        ContentValues contentValues = new ContentValues();
        contentValues.put(ChatProvider.RcsColumns.SRC_PHONE_NUMBER, src);
        contentValues.put(ChatProvider.RcsColumns.DEST_PHONE_NUMBER, dest);
        contentValues.put(ChatProvider.RcsColumns.CHAT_MESSAGE, message);
        contentValues.put(ChatProvider.RcsColumns.MSG_TIMESTAMP, currentTime);
        contentValues.put(ChatProvider.RcsColumns.IS_READ, Boolean.TRUE);
        // insert chat table
        Uri result = mContext.getContentResolver().insert(ChatProvider.CHAT_URI, contentValues);

        ContentValues summary = new ContentValues();
        summary.put(ChatProvider.SummaryColumns.LATEST_MESSAGE, message);
        summary.put(ChatProvider.SummaryColumns.MSG_TIMESTAMP, currentTime);
        summary.put(ChatProvider.SummaryColumns.IS_READ, Boolean.TRUE);

        String remoteNumber = src.equals(SELF) ? dest : src;
        if (remoteNumberExists(remoteNumber)) {
            mContext.getContentResolver().update(ChatProvider.SUMMARY_URI, summary,
                    ChatProvider.SummaryColumns.REMOTE_PHONE_NUMBER + "=?",
                    new String[]{remoteNumber});
        } else {
            summary.put(ChatProvider.SummaryColumns.REMOTE_PHONE_NUMBER, remoteNumber);
            mContext.getContentResolver().insert(ChatProvider.SUMMARY_URI, summary);
        }
        return result;
    }

    /**
     * Update MSRP chat message sent result.
     */
    public void updateMsgResult(String id, boolean success) {
        ContentValues contentValues = new ContentValues();
        contentValues.put(ChatProvider.RcsColumns.RESULT, success);
        mContext.getContentResolver().update(ChatProvider.CHAT_URI, contentValues,
                ChatProvider.RcsColumns._ID + "=?", new String[]{id});
    }

    /**
     * Check if the number exists in the database.
     */
    public boolean remoteNumberExists(String number) {
        Cursor cursor = mContext.getContentResolver().query(ChatProvider.SUMMARY_URI, null,
                ChatProvider.SummaryColumns.REMOTE_PHONE_NUMBER + "=?", new String[]{number},
                null);
        if (cursor != null) {
            int count = cursor.getCount();
            return count > 0;
        }
        return false;
    }

}
