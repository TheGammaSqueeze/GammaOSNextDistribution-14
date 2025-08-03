/*
 * Copyright (C) 2016 The Android Open Source Project
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

/**
 * Bluetooth MAP MCE StateMachine
 *         (Disconnected)
 *             |    ^
 *     CONNECT |    | DISCONNECTED
 *             V    |
 *    (Connecting) (Disconnecting)
 *             |    ^
 *   CONNECTED |    | DISCONNECT
 *             V    |
 *           (Connected)
 *
 * Valid Transitions: State + Event -> Transition:
 *
 * Disconnected + CONNECT -> Connecting
 * Connecting + CONNECTED -> Connected
 * Connecting + TIMEOUT -> Disconnecting
 * Connecting + DISCONNECT/CONNECT -> Defer Message
 * Connected + DISCONNECT -> Disconnecting
 * Connected + CONNECT -> Disconnecting + Defer Message
 * Disconnecting + DISCONNECTED -> (Safe) Disconnected
 * Disconnecting + TIMEOUT -> (Force) Disconnected
 * Disconnecting + DISCONNECT/CONNECT : Defer Message
 */
package com.android.bluetooth.mapclient;

import static android.Manifest.permission.BLUETOOTH_CONNECT;
import static android.Manifest.permission.BLUETOOTH_PRIVILEGED;
import static android.Manifest.permission.RECEIVE_SMS;

import android.app.Activity;
import android.app.PendingIntent;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothMapClient;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.BluetoothUuid;
import android.bluetooth.SdpMasRecord;
import android.content.Intent;
import android.net.Uri;
import android.os.Looper;
import android.os.Message;
import android.os.SystemProperties;
import android.provider.Telephony;
import android.telecom.PhoneAccount;
import android.telephony.SmsManager;
import android.util.Log;

import com.android.bluetooth.BluetoothMetricsProto;
import com.android.bluetooth.Utils;
import com.android.bluetooth.btservice.AdapterService;
import com.android.bluetooth.btservice.MetricsLogger;
import com.android.bluetooth.btservice.ProfileService;
import com.android.bluetooth.flags.Flags;
import com.android.bluetooth.map.BluetoothMapbMessageMime;
import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.util.State;
import com.android.internal.util.StateMachine;
import com.android.vcard.VCardConstants;
import com.android.vcard.VCardEntry;
import com.android.vcard.VCardProperty;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/* The MceStateMachine is responsible for setting up and maintaining a connection to a single
 * specific Messaging Server Equipment endpoint.  Upon connect command an SDP record is retrieved,
 * a connection to the Message Access Server is created and a request to enable notification of new
 * messages is sent.
 */
class MceStateMachine extends StateMachine {
    // Messages for events handled by the StateMachine
    static final int MSG_MAS_CONNECTED = 1001;
    static final int MSG_MAS_DISCONNECTED = 1002;
    static final int MSG_MAS_REQUEST_COMPLETED = 1003;
    static final int MSG_MAS_REQUEST_FAILED = 1004;
    static final int MSG_MAS_SDP_DONE = 1005;
    static final int MSG_MAS_SDP_UNSUCCESSFUL = 1006;
    static final int MSG_OUTBOUND_MESSAGE = 2001;
    static final int MSG_INBOUND_MESSAGE = 2002;
    static final int MSG_NOTIFICATION = 2003;
    static final int MSG_GET_LISTING = 2004;
    static final int MSG_GET_MESSAGE_LISTING = 2005;
    // Set message status to read or deleted
    static final int MSG_SET_MESSAGE_STATUS = 2006;
    static final int MSG_SEARCH_OWN_NUMBER_TIMEOUT = 2007;

    private static final String TAG = "MceStateMachine";
    private static final Boolean DBG = MapClientService.DBG;
    private static final Boolean VDBG = MapClientService.VDBG;
    // SAVE_OUTBOUND_MESSAGES defaults to true to place the responsibility of managing content on
    // Bluetooth, to work with the default Car Messenger.  This may need to be set to false if the
    // messaging app takes that responsibility.
    private static final Boolean SAVE_OUTBOUND_MESSAGES = true;
    private static final int DISCONNECT_TIMEOUT = 3000;
    private static final int CONNECT_TIMEOUT = 10000;
    private static final int MAX_MESSAGES = 20;
    private static final int MSG_CONNECT = 1;
    private static final int MSG_DISCONNECT = 2;
    static final int MSG_CONNECTING_TIMEOUT = 3;
    private static final int MSG_DISCONNECTING_TIMEOUT = 4;

    // Constants for SDP. Note that these values come from the native stack, but no centralized
    // constants exist for them as part of the various SDP APIs.
    public static final int SDP_SUCCESS = 0;
    public static final int SDP_FAILED = 1;
    public static final int SDP_BUSY = 2;

    private static final boolean MESSAGE_SEEN = true;
    private static final boolean MESSAGE_NOT_SEEN = false;

    // Folder names as defined in Bluetooth.org MAP spec V10
    private static final String FOLDER_TELECOM = "telecom";
    private static final String FOLDER_MSG = "msg";
    private static final String FOLDER_OUTBOX = "outbox";
    static final String FOLDER_INBOX = "inbox";
    static final String FOLDER_SENT = "sent";
    private static final String INBOX_PATH = "telecom/msg/inbox";

    // URI Scheme for messages with email contact
    private static final String SCHEME_MAILTO = "mailto";

    private static final String FETCH_MESSAGE_TYPE =
            "persist.bluetooth.pts.mapclient.fetchmessagetype";
    private static final String SEND_MESSAGE_TYPE =
            "persist.bluetooth.pts.mapclient.sendmessagetype";

    // Connectivity States
    private int mPreviousState = BluetoothProfile.STATE_DISCONNECTED;
    private int mMostRecentState = BluetoothProfile.STATE_DISCONNECTED;
    private State mDisconnected;
    private State mConnecting;
    private State mConnected;
    private State mDisconnecting;

    private final BluetoothDevice mDevice;
    private MapClientService mService;
    private MasClient mMasClient;
    private MapClientContent mDatabase;
    private HashMap<String, Bmessage> mSentMessageLog = new HashMap<>(MAX_MESSAGES);
    private HashMap<Bmessage, PendingIntent> mSentReceiptRequested = new HashMap<>(MAX_MESSAGES);
    private HashMap<Bmessage, PendingIntent> mDeliveryReceiptRequested =
            new HashMap<>(MAX_MESSAGES);
    private Bmessage.Type mDefaultMessageType = Bmessage.Type.SMS_CDMA;

    // The amount of time for MCE to search for remote device's own phone number before:
    // (1) MCE registering itself for being notified of the arrival of new messages; and
    // (2) MCE start downloading existing messages off of MSE.
    // NOTE: the value is not "final" so that it can be modified in the unit tests
    @VisibleForTesting
    static int sOwnNumberSearchTimeoutMs = 3_000;

    /**
     * An object to hold the necessary meta-data for each message so we can broadcast it alongside
     * the message content.
     *
     * This is necessary because the metadata is inferred or received separately from the actual
     * message content.
     *
     * Note: In the future it may be best to use the entries from the MessageListing in full instead
     * of this small subset.
     */
    @VisibleForTesting
    static class MessageMetadata {
        private final String mHandle;
        private final Long mTimestamp;
        private boolean mRead;
        private boolean mSeen;

        MessageMetadata(String handle, Long timestamp, boolean read, boolean seen) {
            mHandle = handle;
            mTimestamp = timestamp;
            mRead = read;
            mSeen = seen;
        }

        public String getHandle() {
            return mHandle;
        }

        public Long getTimestamp() {
            return mTimestamp;
        }

        public synchronized boolean getRead() {
            return mRead;
        }

        public synchronized void setRead(boolean read) {
            mRead = read;
        }

        public synchronized boolean getSeen() {
            return mSeen;
        }

    }

    // Map each message to its metadata via the handle
    @VisibleForTesting
    ConcurrentHashMap<String, MessageMetadata> mMessages =
            new ConcurrentHashMap<String, MessageMetadata>();

    MceStateMachine(MapClientService service, BluetoothDevice device) {
        this(service, device, null, null);
    }

    MceStateMachine(MapClientService service, BluetoothDevice device, Looper looper) {
        this(service, device, null, null, looper);
    }

    @VisibleForTesting
    MceStateMachine(MapClientService service, BluetoothDevice device, MasClient masClient,
            MapClientContent database) {
        super(TAG);
        mService = service;
        mMasClient = masClient;
        mDevice = device;
        mDatabase = database;
        initStateMachine();
    }

    @VisibleForTesting
    MceStateMachine(
            MapClientService service,
            BluetoothDevice device,
            MasClient masClient,
            MapClientContent database,
            Looper looper) {
        super(TAG, looper);
        mService = service;
        mMasClient = masClient;
        mDevice = device;
        mDatabase = database;
        initStateMachine();
    }

    private void initStateMachine() {
        mPreviousState = BluetoothProfile.STATE_DISCONNECTED;

        mDisconnected = new Disconnected();
        mConnecting = new Connecting();
        mDisconnecting = new Disconnecting();
        mConnected = new Connected();

        addState(mDisconnected);
        addState(mConnecting);
        addState(mDisconnecting);
        addState(mConnected);
        setInitialState(mConnecting);
        start();
    }

    public void doQuit() {
        quitNow();
    }

    @Override
    protected void onQuitting() {
        if (mService != null) {
            mService.cleanupDevice(mDevice, this);
        }
    }

    synchronized BluetoothDevice getDevice() {
        return mDevice;
    }

    private void onConnectionStateChanged(int prevState, int state) {
        if (mMostRecentState == state) {
            return;
        }
        // mDevice == null only at setInitialState
        if (mDevice == null) {
            return;
        }
        if (DBG) {
            Log.d(TAG, Utils.getLoggableAddress(mDevice) + ": Connection state changed, prev="
                    + prevState + ", new=" + state);
        }
        if (prevState != state && state == BluetoothProfile.STATE_CONNECTED) {
            MetricsLogger.logProfileConnectionEvent(BluetoothMetricsProto.ProfileId.MAP_CLIENT);
        }
        setState(state);

        AdapterService adapterService = AdapterService.getAdapterService();
        if (adapterService != null) {
            adapterService.updateProfileConnectionAdapterProperties(
                    mDevice, BluetoothProfile.MAP_CLIENT, state, prevState);
        }

        Intent intent = new Intent(BluetoothMapClient.ACTION_CONNECTION_STATE_CHANGED);
        intent.putExtra(BluetoothProfile.EXTRA_PREVIOUS_STATE, prevState);
        intent.putExtra(BluetoothProfile.EXTRA_STATE, state);
        intent.putExtra(BluetoothDevice.EXTRA_DEVICE, mDevice);
        intent.addFlags(Intent.FLAG_RECEIVER_REGISTERED_ONLY_BEFORE_BOOT);
        mService.sendBroadcastMultiplePermissions(intent,
                new String[] {BLUETOOTH_CONNECT, BLUETOOTH_PRIVILEGED},
                Utils.getTempBroadcastOptions());
    }

    private synchronized void setState(int state) {
        mMostRecentState = state;
    }

    public synchronized int getState() {
        return mMostRecentState;
    }

    /**
     * Notify of SDP completion.
     */
    public void sendSdpResult(int status, SdpMasRecord record) {
        if (DBG) {
            Log.d(TAG, "Received SDP Result, status=" + status + ", record=" + record);
        }
        if (status != SDP_SUCCESS || record == null) {
            Log.w(TAG, "SDP unsuccessful, status: " + status + ", record: " + record);
            sendMessage(MceStateMachine.MSG_MAS_SDP_UNSUCCESSFUL, status);
            return;
        }
        sendMessage(MceStateMachine.MSG_MAS_SDP_DONE, record);
    }

    public boolean disconnect() {
        if (DBG) {
            Log.d(TAG, "Disconnect Request " + mDevice);
        }
        sendMessage(MSG_DISCONNECT, mDevice);
        return true;
    }

    public synchronized boolean sendMapMessage(Uri[] contacts, String message,
            PendingIntent sentIntent, PendingIntent deliveredIntent) {
        if (DBG) {
            Log.d(TAG, Utils.getLoggableAddress(mDevice) + ": Send, message=" + message);
        }
        if (contacts == null || contacts.length <= 0) {
            return false;
        }
        if (mMostRecentState == BluetoothProfile.STATE_CONNECTED) {
            Bmessage bmsg = new Bmessage();
            // Set type and status.
            bmsg.setType(getDefaultMessageType());
            bmsg.setStatus(Bmessage.Status.READ);

            for (Uri contact : contacts) {
                // Who to send the message to.
                if (VDBG) {
                    Log.d(TAG, "Scheme " + contact.getScheme());
                }
                if (PhoneAccount.SCHEME_TEL.equals(contact.getScheme())) {
                    String path = contact.getPath();
                    if (path != null && path.contains(Telephony.Threads.CONTENT_URI.toString())) {
                        mDatabase.addThreadContactsToEntries(bmsg, contact.getLastPathSegment());
                    } else {
                        VCardEntry destEntry = new VCardEntry();
                        VCardProperty destEntryPhone = new VCardProperty();
                        destEntryPhone.setName(VCardConstants.PROPERTY_TEL);
                        destEntryPhone.addValues(contact.getSchemeSpecificPart());
                        destEntry.addProperty(destEntryPhone);
                        bmsg.addRecipient(destEntry);
                        if (VDBG) {
                            Log.d(TAG, "Sending to phone numbers " + destEntryPhone.getValueList());
                        }
                    }
                } else if (SCHEME_MAILTO.equals(contact.getScheme())) {
                    VCardEntry destEntry = new VCardEntry();
                    VCardProperty destEntryContact = new VCardProperty();
                    destEntryContact.setName(VCardConstants.PROPERTY_EMAIL);
                    destEntryContact.addValues(contact.getSchemeSpecificPart());
                    destEntry.addProperty(destEntryContact);
                    bmsg.addRecipient(destEntry);
                    Log.d(TAG, "SPECIFIC: " + contact.getSchemeSpecificPart());
                    if (DBG) {
                        Log.d(TAG, "Sending to emails "
                                + destEntryContact.getValueList());
                    }
                } else {
                    Log.w(TAG, "Scheme " + contact.getScheme() + " not supported.");
                    return false;
                }
            }

            // Message of the body.
            bmsg.setBodyContent(message);
            if (sentIntent != null) {
                mSentReceiptRequested.put(bmsg, sentIntent);
            }
            if (deliveredIntent != null) {
                mDeliveryReceiptRequested.put(bmsg, deliveredIntent);
            }
            sendMessage(MSG_OUTBOUND_MESSAGE, bmsg);
            return true;
        }
        return false;
    }

    synchronized boolean getMessage(String handle) {
        if (DBG) {
            Log.d(TAG, "getMessage" + handle);
        }
        if (mMostRecentState == BluetoothProfile.STATE_CONNECTED) {
            sendMessage(MSG_INBOUND_MESSAGE, handle);
            return true;
        }
        return false;
    }

    synchronized boolean getUnreadMessages() {
        if (DBG) {
            Log.d(TAG, "getMessage");
        }
        if (mMostRecentState == BluetoothProfile.STATE_CONNECTED) {
            sendMessage(MSG_GET_MESSAGE_LISTING, FOLDER_INBOX);
            return true;
        }
        return false;
    }

    synchronized int getSupportedFeatures() {
        if (mMostRecentState == BluetoothProfile.STATE_CONNECTED && mMasClient != null) {
            if (DBG) Log.d(TAG, "returning getSupportedFeatures from SDP record");
            return mMasClient.getSdpMasRecord().getSupportedFeatures();
        }
        if (DBG) Log.d(TAG, "in getSupportedFeatures, returning 0");
        return 0;
    }

    synchronized boolean setMessageStatus(String handle, int status) {
        if (DBG) {
            Log.d(TAG, "setMessageStatus(" + handle + ", " + status + ")");
        }
        if (mMostRecentState == BluetoothProfile.STATE_CONNECTED) {
            RequestSetMessageStatus.StatusIndicator statusIndicator;
            byte value;
            switch (status) {
                case BluetoothMapClient.UNREAD:
                    statusIndicator = RequestSetMessageStatus.StatusIndicator.READ;
                    value = RequestSetMessageStatus.STATUS_NO;
                    break;

                case BluetoothMapClient.READ:
                    statusIndicator = RequestSetMessageStatus.StatusIndicator.READ;
                    value = RequestSetMessageStatus.STATUS_YES;
                    break;

                case BluetoothMapClient.UNDELETED:
                    statusIndicator = RequestSetMessageStatus.StatusIndicator.DELETED;
                    value = RequestSetMessageStatus.STATUS_NO;
                    break;

                case BluetoothMapClient.DELETED:
                    statusIndicator = RequestSetMessageStatus.StatusIndicator.DELETED;
                    value = RequestSetMessageStatus.STATUS_YES;
                    break;

                default:
                    Log.e(TAG, "Invalid parameter for status" + status);
                    return false;
            }
            sendMessage(MSG_SET_MESSAGE_STATUS, 0, 0, new RequestSetMessageStatus(
                    handle, statusIndicator, value));
            return true;
        }
        return false;
    }

    private String getContactURIFromPhone(String number) {
        return PhoneAccount.SCHEME_TEL + ":" + number;
    }

    private String getContactURIFromEmail(String email) {
        return SCHEME_MAILTO + "://" + email;
    }

    Bmessage.Type getDefaultMessageType() {
        synchronized (mDefaultMessageType) {
            if (Utils.isPtsTestMode()) {
                int messageType = SystemProperties.getInt(SEND_MESSAGE_TYPE, -1);
                if (messageType > 0 && messageType < Bmessage.Type.values().length) {
                    return Bmessage.Type.values()[messageType];
                }
            }
            return mDefaultMessageType;
        }
    }

    void setDefaultMessageType(SdpMasRecord sdpMasRecord) {
        int supportedMessageTypes = sdpMasRecord.getSupportedMessageTypes();
        synchronized (mDefaultMessageType) {
            if ((supportedMessageTypes & SdpMasRecord.MessageType.MMS) > 0) {
                mDefaultMessageType = Bmessage.Type.MMS;
            } else if ((supportedMessageTypes & SdpMasRecord.MessageType.SMS_CDMA) > 0) {
                mDefaultMessageType = Bmessage.Type.SMS_CDMA;
            } else if ((supportedMessageTypes & SdpMasRecord.MessageType.SMS_GSM) > 0) {
                mDefaultMessageType = Bmessage.Type.SMS_GSM;
            }
        }
    }

    public void dump(StringBuilder sb) {
        ProfileService.println(sb, "mCurrentDevice: " + mDevice + "("
                + Utils.getName(mDevice) + ") " + this.toString());
        if (mDatabase != null) {
            mDatabase.dump(sb);
        } else {
            ProfileService.println(sb, "  Device Message DB: null");
        }
        sb.append("\n");
    }

    class Disconnected extends State {
        @Override
        public void enter() {
            if (DBG) {
                Log.d(TAG, Utils.getLoggableAddress(mDevice) + " [Disconnected]: Entered, message="
                        + getMessageName(getCurrentMessage().what));
            }
            onConnectionStateChanged(mPreviousState, BluetoothProfile.STATE_DISCONNECTED);
            mPreviousState = BluetoothProfile.STATE_DISCONNECTED;
            quit();
        }

        @Override
        public void exit() {
            mPreviousState = BluetoothProfile.STATE_DISCONNECTED;
        }
    }

    class Connecting extends State {
        @Override
        public void enter() {
            if (DBG) {
                Log.d(TAG, Utils.getLoggableAddress(mDevice) + " [Connecting]: Entered, message="
                        + getMessageName(getCurrentMessage().what));
            }
            onConnectionStateChanged(mPreviousState, BluetoothProfile.STATE_CONNECTING);

            // When commanded to connect begin SDP to find the MAS server.
            mDevice.sdpSearch(BluetoothUuid.MAS);
            sendMessageDelayed(MSG_CONNECTING_TIMEOUT, CONNECT_TIMEOUT);
            Log.i(TAG, Utils.getLoggableAddress(mDevice) + " [Connecting]: Await SDP results");
        }

        @Override
        public boolean processMessage(Message message) {
            if (DBG) {
                Log.d(TAG, Utils.getLoggableAddress(mDevice) + " [Connecting]: Received "
                        + getMessageName(message.what));
            }

            switch (message.what) {
                case MSG_MAS_SDP_DONE:
                    Log.i(TAG, Utils.getLoggableAddress(mDevice) + " [Connecting]: SDP Complete");
                    if (mMasClient == null) {
                        SdpMasRecord record = (SdpMasRecord) message.obj;
                        if (record == null) {
                            Log.e(TAG, Utils.getLoggableAddress(mDevice)
                                    + " [Connecting]: SDP record is null");
                            return NOT_HANDLED;
                        }
                        mMasClient = new MasClient(mDevice, MceStateMachine.this, record);
                        setDefaultMessageType(record);
                    }
                    break;

                case MSG_MAS_SDP_UNSUCCESSFUL:
                    int sdpStatus = message.arg1;
                    Log.i(TAG, Utils.getLoggableAddress(mDevice)
                            + " [Connecting]: SDP unsuccessful, status=" + sdpStatus);
                    if (sdpStatus == SDP_BUSY) {
                        if (DBG) {
                            Log.d(TAG, Utils.getLoggableAddress(mDevice)
                                    + " [Connecting]: SDP was busy, try again");
                        }
                        mDevice.sdpSearch(BluetoothUuid.MAS);
                    } else {
                        // This means the status is 0 (success, but no record) or 1 (organic
                        // failure). We historically have never retried SDP in failure cases, so we
                        // don't need to wait for the timeout anymore.
                        if (DBG) {
                            Log.d(TAG, Utils.getLoggableAddress(mDevice)
                                    + " [Connecting]: SDP failed completely, disconnecting");
                        }
                        transitionTo(mDisconnecting);
                    }
                    break;

                case MSG_MAS_CONNECTED:
                    transitionTo(mConnected);
                    break;

                case MSG_MAS_DISCONNECTED:
                    if (mMasClient != null) {
                        mMasClient.shutdown();
                    }
                    transitionTo(mDisconnected);
                    break;

                case MSG_CONNECTING_TIMEOUT:
                    transitionTo(mDisconnecting);
                    break;

                case MSG_CONNECT:
                case MSG_DISCONNECT:
                    deferMessage(message);
                    break;

                default:
                    Log.w(TAG, Utils.getLoggableAddress(mDevice)
                            + " [Connecting]: Unexpected message: " + getMessageName(message.what));
                    return NOT_HANDLED;
            }
            return HANDLED;
        }

        @Override
        public void exit() {
            mPreviousState = BluetoothProfile.STATE_CONNECTING;
            removeMessages(MSG_CONNECTING_TIMEOUT);
        }
    }

    class Connected extends State {
        @Override
        public void enter() {
            if (DBG) {
                Log.d(TAG, Utils.getLoggableAddress(mDevice) + " [Connected]: Entered, message="
                        + getMessageName(getCurrentMessage().what));
            }

            MapClientContent.Callbacks callbacks = new MapClientContent.Callbacks(){
                @Override
                public void onMessageStatusChanged(String handle, int status) {
                    setMessageStatus(handle, status);
                }
            };
            // Keeps mock database from being overwritten in tests
            if (mDatabase == null) {
                mDatabase = new MapClientContent(mService, callbacks, mDevice);
            }
            onConnectionStateChanged(mPreviousState, BluetoothProfile.STATE_CONNECTED);
            if (Utils.isPtsTestMode()) return;

            mMasClient.makeRequest(new RequestSetPath(FOLDER_TELECOM));
            mMasClient.makeRequest(new RequestSetPath(FOLDER_MSG));
            mMasClient.makeRequest(new RequestSetPath(FOLDER_INBOX));
            mMasClient.makeRequest(new RequestGetFolderListing(0, 0));
            mMasClient.makeRequest(new RequestSetPath(false));
            // Start searching for remote device's own phone number. Only until either:
            //   (a) the search completes (with or without finding the number), or
            //   (b) the timeout expires,
            // does the MCE:
            //   (a) register itself for being notified of the arrival of new messages, and
            //   (b) start downloading existing messages off of MSE.
            // In other words, the MCE shouldn't handle any messages (new or existing) until after
            // it has tried obtaining the remote's own phone number.
            RequestGetMessagesListingForOwnNumber requestForOwnNumber =
                    new RequestGetMessagesListingForOwnNumber();
            mMasClient.makeRequest(requestForOwnNumber);
            sendMessageDelayed(MSG_SEARCH_OWN_NUMBER_TIMEOUT, requestForOwnNumber,
                    sOwnNumberSearchTimeoutMs);
            Log.i(TAG, Utils.getLoggableAddress(mDevice) + "[Connected]: Find phone number");
        }

        @Override
        public boolean processMessage(Message message) {
            if (DBG) {
                Log.d(TAG, Utils.getLoggableAddress(mDevice) + " [Connected]: Received "
                        + getMessageName(message.what));
            }
            switch (message.what) {
                case MSG_DISCONNECT:
                    if (mDevice.equals(message.obj)) {
                        transitionTo(mDisconnecting);
                    }
                    break;

                case MSG_MAS_DISCONNECTED:
                    deferMessage(message);
                    transitionTo(mDisconnecting);
                    break;

                case MSG_OUTBOUND_MESSAGE:
                    mMasClient.makeRequest(
                            new RequestPushMessage(FOLDER_OUTBOX, (Bmessage) message.obj, null,
                                    false, false));
                    break;

                case MSG_INBOUND_MESSAGE:
                    mMasClient.makeRequest(
                            new RequestGetMessage((String) message.obj, MasClient.CharsetType.UTF_8,
                                    false));
                    break;

                case MSG_NOTIFICATION:
                    EventReport notification = (EventReport) message.obj;
                    processNotification(notification);
                    break;

                case MSG_GET_LISTING:
                    mMasClient.makeRequest(new RequestGetFolderListing(0, 0));
                    break;

                case MSG_GET_MESSAGE_LISTING:
                    // Get latest 50 Unread messages in the last week
                    Calendar calendar = Calendar.getInstance();
                    calendar.add(Calendar.DATE, -7);
                    byte messageType;
                    if (Utils.isPtsTestMode()) {
                        messageType =
                                (byte)
                                        SystemProperties.getInt(
                                                FETCH_MESSAGE_TYPE,
                                                MessagesFilter.MESSAGE_TYPE_ALL);
                    } else {
                        messageType = MessagesFilter.MESSAGE_TYPE_ALL;
                    }

                    mMasClient.makeRequest(
                            new RequestGetMessagesListing(
                                    (String) message.obj,
                                    0,
                                    new MessagesFilter.Builder()
                                            .setPeriod(calendar.getTime(), null)
                                            .setMessageType(messageType)
                                            .build(),
                                    0,
                                    50,
                                    0));
                    break;

                case MSG_SET_MESSAGE_STATUS:
                    if (message.obj instanceof RequestSetMessageStatus) {
                        mMasClient.makeRequest((RequestSetMessageStatus) message.obj);
                    }
                    break;

                case MSG_MAS_REQUEST_COMPLETED:
                    if (message.obj instanceof RequestGetMessage) {
                        processInboundMessage((RequestGetMessage) message.obj);
                    } else if (message.obj instanceof RequestPushMessage) {
                        RequestPushMessage requestPushMessage = (RequestPushMessage) message.obj;
                        String messageHandle = requestPushMessage.getMsgHandle();
                        Log.i(TAG, Utils.getLoggableAddress(mDevice)
                                + " [Connected]: Message Sent, handle=" + messageHandle);
                        // ignore the top-order byte (converted to string) in the handle for now
                        // some test devices don't populate messageHandle field.
                        // in such cases, no need to wait up for response for such messages.
                        if (messageHandle != null && messageHandle.length() > 2) {
                            if (SAVE_OUTBOUND_MESSAGES) {
                                mDatabase.storeMessage(requestPushMessage.getBMsg(), messageHandle,
                                        System.currentTimeMillis(), MESSAGE_SEEN);
                            }
                            mSentMessageLog.put(messageHandle.substring(2),
                                    requestPushMessage.getBMsg());
                        }
                    } else if (message.obj instanceof RequestGetMessagesListing) {
                        processMessageListing((RequestGetMessagesListing) message.obj);
                    } else if (message.obj instanceof RequestSetMessageStatus) {
                        processSetMessageStatus((RequestSetMessageStatus) message.obj);
                    } else if (message.obj instanceof RequestGetMessagesListingForOwnNumber) {
                        processMessageListingForOwnNumber(
                                (RequestGetMessagesListingForOwnNumber) message.obj);
                    }
                    break;

                case MSG_CONNECT:
                    if (!mDevice.equals(message.obj)) {
                        deferMessage(message);
                        transitionTo(mDisconnecting);
                    }
                    break;

                case MSG_SEARCH_OWN_NUMBER_TIMEOUT:
                    Log.w(TAG, "Timeout while searching for own phone number.");
                    // Abort any outstanding Request so it doesn't execute on MasClient
                    RequestGetMessagesListingForOwnNumber request =
                            (RequestGetMessagesListingForOwnNumber) message.obj;
                    mMasClient.abortRequest(request);
                    // Remove any executed/completed Request that MasClient has passed back to
                    // state machine. Note: {@link StateMachine} doesn't provide a {@code
                    // removeMessages(int what, Object obj)}, nor direct access to {@link
                    // mSmHandler}, so this will remove *all* {@code MSG_MAS_REQUEST_COMPLETED}
                    // messages. However, {@link RequestGetMessagesListingForOwnNumber} should be
                    // the only MAS Request enqueued at this point, since none of the other MAS
                    // Requests should trigger/start until after getOwnNumber has completed.
                    removeMessages(MSG_MAS_REQUEST_COMPLETED);
                    // If failed to complete search for remote device's own phone number,
                    // proceed without it (i.e., register MCE for MNS and start download
                    // of existing messages from MSE).
                    notificationRegistrationAndStartDownloadMessages();
                    break;

                default:
                    Log.w(TAG, Utils.getLoggableAddress(mDevice)
                            + " [Connected]: Unexpected message: " + getMessageName(message.what));
                    return NOT_HANDLED;
            }
            return HANDLED;
        }

        @Override
        public void exit() {
            mDatabase.cleanUp();
            mDatabase = null;
            mPreviousState = BluetoothProfile.STATE_CONNECTED;
        }

        /**
         * Given a message notification event, will ensure message caching and updating and update
         * interested applications.
         *
         * Message notifications arrive for both remote message reception and Message-Listing object
         * updates that are triggered by the server side.
         *
         * @param msg - A Message object containing a EventReport object describing the remote event
         */
        private void processNotification(EventReport event) {
            Log.i(TAG, Utils.getLoggableAddress(mDevice)
                    + " [Connected]: Received Notification, event=" + event);

            if (event == null) {
                Log.w(TAG, Utils.getLoggableAddress(mDevice)
                        + "[Connected]: Notification event is null");
                return;
            }

            switch (event.getType()) {
                case NEW_MESSAGE:
                    if (!mMessages.containsKey(event.getHandle())) {
                        Long timestamp = event.getTimestamp();
                        if (timestamp == null) {
                            // Infer the timestamp for this message as 'now' and read status
                            // false instead of getting the message listing data for it
                            timestamp = Instant.now().toEpochMilli();
                        }
                        MessageMetadata metadata = new MessageMetadata(event.getHandle(),
                                timestamp, false, MESSAGE_NOT_SEEN);
                        mMessages.put(event.getHandle(), metadata);
                    }
                    mMasClient.makeRequest(new RequestGetMessage(event.getHandle(),
                            MasClient.CharsetType.UTF_8, false));
                    break;
                case DELIVERY_FAILURE:
                    // fall through
                case SENDING_FAILURE:
                    if (!Flags.handleDeliverySendingFailureEvents()) {
                        break;
                    }
                    // fall through
                case DELIVERY_SUCCESS:
                    // fall through
                case SENDING_SUCCESS:
                    notifySentMessageStatus(event.getHandle(), event.getType());
                    break;
                case READ_STATUS_CHANGED:
                    mDatabase.markRead(event.getHandle());
                    break;
                case MESSAGE_DELETED:
                    mDatabase.deleteMessage(event.getHandle());
                    break;
                default:
                    if (DBG) {
                        Log.d(TAG, "processNotification: ignoring event type=" + event.getType());
                    }
            }
        }

        // Sets the specified message status to "read" (from "unread" status, mostly)
        private void markMessageRead(RequestGetMessage request) {
            if (DBG) Log.d(TAG, "markMessageRead" + request.getHandle());
            MessageMetadata metadata = mMessages.get(request.getHandle());
            metadata.setRead(true);
            mMasClient.makeRequest(new RequestSetMessageStatus(request.getHandle(),
                    RequestSetMessageStatus.StatusIndicator.READ, RequestSetMessageStatus.STATUS_YES));
        }

        // Sets the specified message status to "deleted"
        private void markMessageDeleted(RequestGetMessage request) {
            if (DBG) Log.d(TAG, "markMessageDeleted");
            mMasClient.makeRequest(new RequestSetMessageStatus(request.getHandle(),
                    RequestSetMessageStatus.StatusIndicator.DELETED, RequestSetMessageStatus.STATUS_YES));
        }

        /**
         * Given the result of a Message Listing request, will cache the contents of each Message in
         * the Message Listing Object and kick off requests to retrieve message contents from the
         * remote device.
         *
         * @param request - A request object that has been resolved and returned with a message list
         */
        private void processMessageListing(RequestGetMessagesListing request) {
            Log.i(TAG, Utils.getLoggableAddress(mDevice)
                    + " [Connected]: Received Message Listing, listing="
                    + (request != null ? (request.getList() != null
                        ? String.valueOf(request.getList().size())
                        : "null list") : "null request"));

            ArrayList<com.android.bluetooth.mapclient.Message> messageListing = request.getList();
            if (messageListing != null) {
                // Message listings by spec arrive ordered newest first but we wish to broadcast as
                // oldest first. Iterate in reverse order so we initiate requests oldest first.
                for (int i = messageListing.size() - 1; i >= 0; i--) {
                    com.android.bluetooth.mapclient.Message msg = messageListing.get(i);
                    if (DBG) {
                        Log.d(TAG, Utils.getLoggableAddress(mDevice)
                                + " [Connected]: fetch message content, handle=" + msg.getHandle());
                    }
                    // A message listing coming from the server should always have up to date data
                    if (msg.getDateTime() == null) {
                        Log.w(TAG, "message with handle " + msg.getHandle()
                                + " has a null datetime, ignoring");
                        continue;
                    }
                    mMessages.put(msg.getHandle(), new MessageMetadata(msg.getHandle(),
                            msg.getDateTime().getTime(), msg.isRead(), MESSAGE_SEEN));
                    getMessage(msg.getHandle());
                }
            }
        }

        /**
         * Process the result of a MessageListing request that was made specifically to obtain
         * the remote device's own phone number.
         *
         * @param request - A request object that has been resolved and returned with:
         *   - a phone number (possibly null if a number wasn't found)
         *   - a flag indicating whether there are still messages that can be searched/requested.
         *   - the request will automatically update itself if a number wasn't found and there are
         *     still messages that can be searched.
         */
        private void processMessageListingForOwnNumber(
                RequestGetMessagesListingForOwnNumber request) {

            if (request.isSearchCompleted()) {
                if (DBG) {
                    Log.d(TAG, "processMessageListingForOwnNumber: search completed");
                }
                if (request.getOwnNumber() != null) {
                    // A phone number was found (should be the remote device's).
                    if (DBG) {
                        Log.d(TAG, "processMessageListingForOwnNumber: number found = "
                                + request.getOwnNumber());
                    }
                    mDatabase.setRemoteDeviceOwnNumber(request.getOwnNumber());
                }
                // Remove any outstanding timeouts from state machine queue
                removeDeferredMessages(MSG_SEARCH_OWN_NUMBER_TIMEOUT);
                removeMessages(MSG_SEARCH_OWN_NUMBER_TIMEOUT);
                // Move on to next stage of connection process
                notificationRegistrationAndStartDownloadMessages();
            } else {
                // A phone number wasn't found, but there are still additional messages that can
                // be requested and searched.
                if (DBG) {
                    Log.d(TAG, "processMessageListingForOwnNumber: continuing search");
                }
                mMasClient.makeRequest(request);
            }
        }

        /**
         * (1) MCE registering itself for being notified of the arrival of new messages; and
         * (2) MCE downloading existing messages of off MSE.
         */
        private void notificationRegistrationAndStartDownloadMessages() {
            Log.i(TAG, Utils.getLoggableAddress(mDevice) + "[Connected]: Queue Message downloads");
            mMasClient.makeRequest(new RequestSetNotificationRegistration(true));
            sendMessage(MSG_GET_MESSAGE_LISTING, FOLDER_SENT);
            sendMessage(MSG_GET_MESSAGE_LISTING, FOLDER_INBOX);
        }

        private void processSetMessageStatus(RequestSetMessageStatus request) {
            if (DBG) {
                Log.d(TAG, "processSetMessageStatus");
            }
            int result = BluetoothMapClient.RESULT_SUCCESS;
            if (!request.isSuccess()) {
                Log.e(TAG, "Set message status failed");
                result = BluetoothMapClient.RESULT_FAILURE;
            }
            RequestSetMessageStatus.StatusIndicator status = request.getStatusIndicator();
            switch (status) {
                case READ: {
                    Intent intent = new Intent(
                            BluetoothMapClient.ACTION_MESSAGE_READ_STATUS_CHANGED);
                    intent.putExtra(BluetoothMapClient.EXTRA_MESSAGE_READ_STATUS,
                            request.getValue() == RequestSetMessageStatus.STATUS_YES ? true : false);
                    intent.putExtra(BluetoothMapClient.EXTRA_MESSAGE_HANDLE, request.getHandle());
                    intent.putExtra(BluetoothMapClient.EXTRA_RESULT_CODE, result);
                    mService.sendBroadcast(intent, BLUETOOTH_CONNECT);
                    break;
                }
                case DELETED: {
                    Intent intent = new Intent(
                            BluetoothMapClient.ACTION_MESSAGE_DELETED_STATUS_CHANGED);
                    intent.putExtra(BluetoothMapClient.EXTRA_MESSAGE_DELETED_STATUS,
                            request.getValue() == RequestSetMessageStatus.STATUS_YES ? true : false);
                    intent.putExtra(BluetoothMapClient.EXTRA_MESSAGE_HANDLE, request.getHandle());
                    intent.putExtra(BluetoothMapClient.EXTRA_RESULT_CODE, result);
                    mService.sendBroadcast(intent, BLUETOOTH_CONNECT);
                    break;
                }
                default:
                    Log.e(TAG, "Unknown status indicator " + status);
                    return;
            }
        }

        /**
         * Given the response of a GetMessage request, will broadcast the bMessage contents on to
         * all registered applications.
         *
         * Inbound messages arrive as bMessage objects following a GetMessage request. GetMessage
         * uses a message handle that can arrive from both a GetMessageListing request or a Message
         * Notification event.
         *
         * @param request - A request object that has been resolved and returned with message data
         */
        private void processInboundMessage(RequestGetMessage request) {
            Bmessage message = request.getMessage();
            if (DBG) {
                Log.d(TAG, "Notify inbound Message" + message);
            }

            if (message == null) {
                return;
            }
            mDatabase.storeMessage(message, request.getHandle(),
                    mMessages.get(request.getHandle()).getTimestamp(),
                    mMessages.get(request.getHandle()).getSeen());
            if (!INBOX_PATH.equalsIgnoreCase(message.getFolder())) {
                if (DBG) {
                    Log.d(TAG, "Ignoring message received in " + message.getFolder() + ".");
                }
                return;
            }
            switch (message.getType()) {
                case SMS_CDMA:
                case SMS_GSM:
                case MMS:
                    if (DBG) {
                        Log.d(TAG, "Body: " + message.getBodyContent());
                        Log.d(TAG, message.toString());
                        Log.d(TAG, "Recipients" + message.getRecipients().toString());
                    }

                    // Grab the message metadata and update the cached read status from the bMessage
                    MessageMetadata metadata = mMessages.get(request.getHandle());
                    metadata.setRead(request.getMessage().getStatus() == Bmessage.Status.READ);

                    Intent intent = new Intent();
                    intent.setAction(BluetoothMapClient.ACTION_MESSAGE_RECEIVED);
                    intent.putExtra(BluetoothDevice.EXTRA_DEVICE, mDevice);
                    intent.putExtra(BluetoothMapClient.EXTRA_MESSAGE_HANDLE, request.getHandle());
                    intent.putExtra(BluetoothMapClient.EXTRA_MESSAGE_TIMESTAMP,
                            metadata.getTimestamp());
                    intent.putExtra(BluetoothMapClient.EXTRA_MESSAGE_READ_STATUS,
                            metadata.getRead());
                    intent.putExtra(android.content.Intent.EXTRA_TEXT, message.getBodyContent());
                    VCardEntry originator = message.getOriginator();
                    if (originator != null) {
                        if (DBG) {
                            Log.d(TAG, originator.toString());
                        }
                        List<VCardEntry.PhoneData> phoneData = originator.getPhoneList();
                        List<VCardEntry.EmailData> emailData = originator.getEmailList();
                        if (phoneData != null && phoneData.size() > 0) {
                            String phoneNumber = phoneData.get(0).getNumber();
                            if (DBG) {
                                Log.d(TAG, "Originator number: " + phoneNumber);
                            }
                            intent.putExtra(BluetoothMapClient.EXTRA_SENDER_CONTACT_URI,
                                    getContactURIFromPhone(phoneNumber));
                        } else if (emailData != null && emailData.size() > 0) {
                            String email = emailData.get(0).getAddress();
                            if (DBG) {
                                Log.d(TAG, "Originator email: " + email);
                            }
                            intent.putExtra(BluetoothMapClient.EXTRA_SENDER_CONTACT_URI,
                                    getContactURIFromEmail(email));
                        }
                        intent.putExtra(BluetoothMapClient.EXTRA_SENDER_CONTACT_NAME,
                                originator.getDisplayName());
                    }
                    if (message.getType() == Bmessage.Type.MMS) {
                        BluetoothMapbMessageMime mmsBmessage = new BluetoothMapbMessageMime();
                        mmsBmessage.parseMsgPart(message.getBodyContent());
                        intent.putExtra(android.content.Intent.EXTRA_TEXT,
                                mmsBmessage.getMessageAsText());
                        ArrayList<VCardEntry> recipients = message.getRecipients();
                        if (recipients != null && !recipients.isEmpty()) {
                            intent.putExtra(android.content.Intent.EXTRA_CC,
                                    getRecipientsUri(recipients));
                        }
                    }
                    String defaultMessagingPackage = Telephony.Sms.getDefaultSmsPackage(mService);
                    if (defaultMessagingPackage == null) {
                        // Broadcast to all RECEIVE_SMS recipients, including the SMS receiver
                        // package defined in system properties if one exists
                        mService.sendBroadcast(intent, RECEIVE_SMS);
                    } else {
                        String smsReceiverPackageName =
                                SystemProperties.get(
                                        "bluetooth.profile.map_client.sms_receiver_package",
                                        null
                                );
                        if (smsReceiverPackageName != null && !smsReceiverPackageName.isEmpty()) {
                            // Clone intent and broadcast to SMS receiver package if one exists
                            Intent messageNotificationIntent = (Intent) intent.clone();
                            messageNotificationIntent.setPackage(smsReceiverPackageName);
                            mService.sendBroadcast(messageNotificationIntent, RECEIVE_SMS);
                        }
                        // Broadcast to default messaging package
                        intent.setPackage(defaultMessagingPackage);
                        mService.sendBroadcast(intent, RECEIVE_SMS);
                    }
                    break;
                case EMAIL:
                default:
                    Log.e(TAG, "Received unhandled type" + message.getType().toString());
                    break;
            }
        }

        /**
         * Retrieves the URIs of all the participants of a group conversation, besides the sender
         * of the message.
         * @param recipients
         * @return
         */
        private String[] getRecipientsUri(ArrayList<VCardEntry> recipients) {
            Set<String> uris = new HashSet<>();

            for (VCardEntry recipient : recipients) {
                List<VCardEntry.PhoneData> phoneData = recipient.getPhoneList();
                if (phoneData != null && phoneData.size() > 0) {
                    String phoneNumber = phoneData.get(0).getNumber();
                    if (DBG) {
                        Log.d(TAG, "CC Recipient number: " + phoneNumber);
                    }
                    uris.add(getContactURIFromPhone(phoneNumber));
                }
            }
            String[] stringUris = new String[uris.size()];
            return uris.toArray(stringUris);
        }

        private void notifySentMessageStatus(String handle, EventReport.Type status) {
            if (DBG) {
                Log.d(TAG, "got a status for " + handle + " Status = " + status);
            }
            // some test devices don't populate messageHandle field.
            // in such cases, ignore such messages.
            if (handle == null || handle.length() <= 2) return;
            PendingIntent intentToSend = null;
            // ignore the top-order byte (converted to string) in the handle for now
            String shortHandle = handle.substring(2);
            if (status == EventReport.Type.SENDING_FAILURE
                    || status == EventReport.Type.SENDING_SUCCESS) {
                intentToSend = mSentReceiptRequested.remove(mSentMessageLog.get(shortHandle));
            } else if (status == EventReport.Type.DELIVERY_SUCCESS
                    || status == EventReport.Type.DELIVERY_FAILURE) {
                intentToSend = mDeliveryReceiptRequested.remove(mSentMessageLog.get(shortHandle));
            }

            if (intentToSend != null) {
                try {
                    if (DBG) {
                        Log.d(TAG, "*******Sending " + intentToSend);
                    }
                    int result = Activity.RESULT_OK;
                    if (status == EventReport.Type.SENDING_FAILURE
                            || status == EventReport.Type.DELIVERY_FAILURE) {
                        result = SmsManager.RESULT_ERROR_GENERIC_FAILURE;
                    }
                    intentToSend.send(result);
                } catch (PendingIntent.CanceledException e) {
                    Log.w(TAG, "Notification Request Canceled" + e);
                }
            } else {
                Log.e(TAG, "Received a notification on message with handle = "
                        + handle + ", but it is NOT found in mSentMessageLog! where did it go?");
            }
        }
    }

    class Disconnecting extends State {
        @Override
        public void enter() {
            if (DBG) {
                Log.d(TAG, Utils.getLoggableAddress(mDevice) + " [Disconnecting]: Entered, message="
                        + getMessageName(getCurrentMessage().what));
            }

            onConnectionStateChanged(mPreviousState, BluetoothProfile.STATE_DISCONNECTING);

            if (mMasClient != null) {
                mMasClient.makeRequest(new RequestSetNotificationRegistration(false));
                mMasClient.shutdown();
                sendMessageDelayed(MSG_DISCONNECTING_TIMEOUT, DISCONNECT_TIMEOUT);
            } else {
                // MAP was never connected
                transitionTo(mDisconnected);
            }
        }

        @Override
        public boolean processMessage(Message message) {
            if (DBG) {
                Log.d(TAG, Utils.getLoggableAddress(mDevice) + " [Disconnecting]: Received "
                        + getMessageName(message.what));
            }
            switch (message.what) {
                case MSG_DISCONNECTING_TIMEOUT:
                case MSG_MAS_DISCONNECTED:
                    mMasClient = null;
                    transitionTo(mDisconnected);
                    break;

                case MSG_CONNECT:
                case MSG_DISCONNECT:
                    deferMessage(message);
                    break;

                default:
                    Log.w(TAG, Utils.getLoggableAddress(mDevice)
                            + " [Disconnecting]: Unexpected message: "
                            + getMessageName(message.what));
                    return NOT_HANDLED;
            }
            return HANDLED;
        }

        @Override
        public void exit() {
            mPreviousState = BluetoothProfile.STATE_DISCONNECTING;
            removeMessages(MSG_DISCONNECTING_TIMEOUT);
        }
    }

    void receiveEvent(EventReport ev) {
        if (DBG) {
            Log.d(TAG, "Message Type = " + ev.getType()
                    + ", Message handle = " + ev.getHandle());
        }
        sendMessage(MSG_NOTIFICATION, ev);
    }

    private String getMessageName(int what) {
        switch (what) {
            case MSG_MAS_CONNECTED:
                return "MSG_MAS_CONNECTED";
            case MSG_MAS_DISCONNECTED:
                return "MSG_MAS_DISCONNECTED";
            case MSG_MAS_REQUEST_COMPLETED:
                return "MSG_MAS_REQUEST_COMPLETED";
            case MSG_MAS_REQUEST_FAILED:
                return "MSG_MAS_REQUEST_FAILED";
            case MSG_MAS_SDP_DONE:
                return "MSG_MAS_SDP_DONE";
            case MSG_MAS_SDP_UNSUCCESSFUL:
                return "MSG_MAS_SDP_UNSUCCESSFUL";
            case MSG_OUTBOUND_MESSAGE:
                return "MSG_OUTBOUND_MESSAGE";
            case MSG_INBOUND_MESSAGE:
                return "MSG_INBOUND_MESSAGE";
            case MSG_NOTIFICATION:
                return "MSG_NOTIFICATION";
            case MSG_GET_LISTING:
                return "MSG_GET_LISTING";
            case MSG_GET_MESSAGE_LISTING:
                return "MSG_GET_MESSAGE_LISTING";
            case MSG_SET_MESSAGE_STATUS:
                return "MSG_SET_MESSAGE_STATUS";
            case DISCONNECT_TIMEOUT:
                return "DISCONNECT_TIMEOUT";
            case CONNECT_TIMEOUT:
                return "CONNECT_TIMEOUT";
            case MSG_CONNECT:
                return "MSG_CONNECT";
            case MSG_DISCONNECT:
                return "MSG_DISCONNECT";
            case MSG_CONNECTING_TIMEOUT:
                return "MSG_CONNECTING_TIMEOUT";
            case MSG_DISCONNECTING_TIMEOUT:
                return "MSG_DISCONNECTING_TIMEOUT";
        }
        return "UNKNOWN";
    }
}
