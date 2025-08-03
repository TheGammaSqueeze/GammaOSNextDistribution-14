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

package com.android.bluetooth.mapclient;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothMapClient;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.database.ContentObserver;
import android.database.Cursor;
import android.net.Uri;
import android.provider.BaseColumns;
import android.provider.Telephony;
import android.provider.Telephony.Mms;
import android.provider.Telephony.MmsSms;
import android.provider.Telephony.Sms;
import android.provider.Telephony.Threads;
import android.telephony.PhoneNumberUtils;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.util.ArraySet;
import android.util.Log;

import com.android.bluetooth.Utils;
import com.android.bluetooth.map.BluetoothMapbMessageMime;
import com.android.bluetooth.map.BluetoothMapbMessageMime.MimePart;
import com.android.vcard.VCardConstants;
import com.android.vcard.VCardEntry;
import com.android.vcard.VCardProperty;

import com.google.android.mms.pdu.PduHeaders;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Set;

class MapClientContent {

    private static final String INBOX_PATH = "telecom/msg/inbox";
    private static final String TAG = "MapClientContent";
    private static final int DEFAULT_CHARSET = 106;
    private static final int ORIGINATOR_ADDRESS_TYPE = 137;
    private static final int RECIPIENT_ADDRESS_TYPE = 151;

    private static final int NUM_RECENT_MSGS_TO_DUMP = 5;

    private enum Type {
        UNKNOWN,
        SMS,
        MMS
    }

    private enum Folder {
        UNKNOWN,
        INBOX,
        SENT
    }

    final BluetoothDevice mDevice;
    private final Context mContext;
    private final Callbacks mCallbacks;
    private final ContentResolver mResolver;
    ContentObserver mContentObserver;
    String mPhoneNumber = null;
    private int mSubscriptionId = SubscriptionManager.INVALID_SUBSCRIPTION_ID;
    private SubscriptionManager mSubscriptionManager;
    private TelephonyManager mTelephonyManager;
    private HashMap<String, Uri> mHandleToUriMap = new HashMap<>();
    private HashMap<Uri, MessageStatus> mUriToHandleMap = new HashMap<>();

    /**
     * Callbacks
     * API to notify about statusChanges as observed from the content provider
     */
    interface Callbacks {
        void onMessageStatusChanged(String handle, int status);
    }

    /**
     * MapClientContent manages all interactions between Bluetooth and the messaging provider.
     *
     * <p>Changes to the database are mirrored between the remote and local providers, specifically
     * new messages, changes to read status, and removal of messages.
     *
     * <p>Object is invalid after cleanUp() is called.
     *
     * <p>context: the context that all content provider interactions are conducted MceStateMachine:
     * the interface to send outbound updates such as when a message is read locally device: the
     * associated Bluetooth device used for associating messages with a subscription
     */
    MapClientContent(Context context, Callbacks callbacks, BluetoothDevice device) {
        mContext = context;
        mDevice = device;
        mCallbacks = callbacks;
        mResolver = mContext.getContentResolver();

        mSubscriptionManager = mContext.getSystemService(SubscriptionManager.class);
        mTelephonyManager = mContext.getSystemService(TelephonyManager.class);
        mSubscriptionManager
                .addSubscriptionInfoRecord(mDevice.getAddress(), Utils.getName(mDevice), 0,
                        SubscriptionManager.SUBSCRIPTION_TYPE_REMOTE_SIM);
        SubscriptionInfo info = mSubscriptionManager
                .getActiveSubscriptionInfoForIcc(mDevice.getAddress());
        if (info != null) {
            mSubscriptionId = info.getSubscriptionId();
        }

        mContentObserver = new ContentObserver(null) {
            @Override
            public boolean deliverSelfNotifications() {
                return false;
            }

            @Override
            public void onChange(boolean selfChange) {
                logV("onChange(self=" + selfChange + ")");
                findChangeInDatabase();
            }

            @Override
            public void onChange(boolean selfChange, Uri uri) {
                logV("onChange(self=" + selfChange + ", uri=" + uri.toString() + ")");
                findChangeInDatabase();
            }
        };

        clearMessages(mContext, mSubscriptionId);
        mResolver.registerContentObserver(Sms.CONTENT_URI, true, mContentObserver);
        mResolver.registerContentObserver(Mms.CONTENT_URI, true, mContentObserver);
        mResolver.registerContentObserver(MmsSms.CONTENT_URI, true, mContentObserver);
    }

    static void clearAllContent(Context context) {
        SubscriptionManager subscriptionManager =
                context.getSystemService(SubscriptionManager.class);
        List<SubscriptionInfo> subscriptions = subscriptionManager.getActiveSubscriptionInfoList();
        if (subscriptions == null) {
            Log.w(TAG, "Active subscription list is missing");
            return;
        }
        for (SubscriptionInfo info : subscriptions) {
            if (info.getSubscriptionType() == SubscriptionManager.SUBSCRIPTION_TYPE_REMOTE_SIM) {
                clearMessages(context, info.getSubscriptionId());
                try {
                    subscriptionManager.removeSubscriptionInfoRecord(info.getIccId(),
                            SubscriptionManager.SUBSCRIPTION_TYPE_REMOTE_SIM);
                } catch (Exception e) {
                    Log.w(TAG, "cleanUp failed: " + e.toString());
                }
            }
        }
    }

    private static void logI(String message) {
        Log.i(TAG, message);
    }

    private static void logD(String message) {
        if (MapClientService.DBG) {
            Log.d(TAG, message);
        }
    }

    private static void logV(String message) {
        if (MapClientService.VDBG) {
            Log.v(TAG, message);
        }
    }

    /**
     * This number is necessary for thread_id to work properly. thread_id is needed for
     * (group) MMS messages to be displayed/stitched correctly.
     */
    void setRemoteDeviceOwnNumber(String phoneNumber) {
        mPhoneNumber = phoneNumber;
        logV("Remote device " + mDevice.getAddress() + " phone number set to: " + mPhoneNumber);
    }

    /**
     * storeMessage
     *
     * Store a message in database with the associated handle and timestamp.
     * The handle is used to associate the local message with the remote message.
     */
    void storeMessage(Bmessage message, String handle, Long timestamp, boolean seen) {
        logI(
                "storeMessage(device="
                        + Utils.getLoggableAddress(mDevice)
                        + ", time="
                        + timestamp
                        + "["
                        + toDatetimeString(timestamp)
                        + "]"
                        + ", handle="
                        + handle
                        + ", type="
                        + message.getType()
                        + ", folder="
                        + message.getFolder());

        switch (message.getType()) {
            case MMS:
                storeMms(message, handle, timestamp, seen);
                return;
            case SMS_CDMA:
            case SMS_GSM:
                storeSms(message, handle, timestamp, seen);
                return;
            default:
                logD("Request to store unsupported message type: " + message.getType());
        }
    }

    private void storeSms(Bmessage message, String handle, Long timestamp, boolean seen) {
        logD("storeSms");
        logV(message.toString());
        VCardEntry originator = message.getOriginator();
        String recipients;
        if (INBOX_PATH.equals(message.getFolder())) {
            recipients = getOriginatorNumber(message);
        } else {
            recipients = getFirstRecipientNumber(message);
            if (recipients == null) {
                logD("invalid recipients");
                return;
            }
        }
        logV("Received SMS from Number " + recipients);
        String messageContent;

        Uri contentUri = INBOX_PATH.equalsIgnoreCase(message.getFolder()) ? Sms.Inbox.CONTENT_URI
                : Sms.Sent.CONTENT_URI;
        ContentValues values = new ContentValues();
        long threadId = getThreadId(message);
        int readStatus = message.getStatus() == Bmessage.Status.READ ? 1 : 0;

        values.put(Sms.THREAD_ID, threadId);
        values.put(Sms.ADDRESS, recipients);
        values.put(Sms.BODY, message.getBodyContent());
        values.put(Sms.SUBSCRIPTION_ID, mSubscriptionId);
        values.put(Sms.DATE, timestamp);
        values.put(Sms.READ, readStatus);
        values.put(Sms.SEEN, seen);

        Uri results = mResolver.insert(contentUri, values);
        mHandleToUriMap.put(handle, results);
        mUriToHandleMap.put(results, new MessageStatus(handle, readStatus));
        logD("Map InsertedThread" + results);
    }

    /**
     * deleteMessage
     * remove a message from the local provider based on a remote change
     */
    void deleteMessage(String handle) {
        logD("deleting handle" + handle);
        Uri messageToChange = mHandleToUriMap.get(handle);
        if (messageToChange != null) {
            mResolver.delete(messageToChange, null);
        }
    }


    /**
     * markRead
     * mark a message read in the local provider based on a remote change
     */
    void markRead(String handle) {
        logD("marking read " + handle);
        Uri messageToChange = mHandleToUriMap.get(handle);
        if (messageToChange != null) {
            ContentValues values = new ContentValues();
            values.put(Sms.READ, 1);
            mResolver.update(messageToChange, values, null);
        }
    }

    /**
     * findChangeInDatabase
     * compare the current state of the local content provider to the expected state and propagate
     * changes to the remote.
     */
    private void findChangeInDatabase() {
        HashMap<Uri, MessageStatus> originalUriToHandleMap;
        HashMap<Uri, MessageStatus> duplicateUriToHandleMap;

        originalUriToHandleMap = mUriToHandleMap;
        duplicateUriToHandleMap = new HashMap<>(originalUriToHandleMap);
        for (Uri uri : new Uri[]{Mms.CONTENT_URI, Sms.CONTENT_URI}) {
            try (Cursor cursor = mResolver.query(uri, null, null, null, null)) {
                while (cursor.moveToNext()) {
                    Uri index =
                            Uri.withAppendedPath(
                                    uri, cursor.getString(cursor.getColumnIndex("_id")));
                    int readStatus = cursor.getInt(cursor.getColumnIndex(Sms.READ));
                    MessageStatus currentMessage = duplicateUriToHandleMap.remove(index);
                    if (currentMessage != null && currentMessage.mRead != readStatus) {
                        logV(currentMessage.mHandle);
                        currentMessage.mRead = readStatus;
                        mCallbacks.onMessageStatusChanged(
                                currentMessage.mHandle, BluetoothMapClient.READ);
                    }
                }
            }
        }
        for (HashMap.Entry record : duplicateUriToHandleMap.entrySet()) {
            logV("Deleted " + ((MessageStatus) record.getValue()).mHandle);
            originalUriToHandleMap.remove(record.getKey());
            mCallbacks.onMessageStatusChanged(((MessageStatus) record.getValue()).mHandle,
                    BluetoothMapClient.DELETED);
        }
    }

    private void storeMms(Bmessage message, String handle, Long timestamp, boolean seen) {
        logD("storeMms");
        logV(message.toString());
        try {
            ContentValues values = new ContentValues();
            long threadId = getThreadId(message);
            BluetoothMapbMessageMime mmsBmessage = new BluetoothMapbMessageMime();
            mmsBmessage.parseMsgPart(message.getBodyContent());
            int read = message.getStatus() == Bmessage.Status.READ ? 1 : 0;
            Uri contentUri;
            int messageBox;
            if (INBOX_PATH.equalsIgnoreCase(message.getFolder())) {
                contentUri = Mms.Inbox.CONTENT_URI;
                messageBox = Mms.MESSAGE_BOX_INBOX;
            } else {
                contentUri = Mms.Sent.CONTENT_URI;
                messageBox = Mms.MESSAGE_BOX_SENT;
            }
            logD("Parsed");
            values.put(Mms.SUBSCRIPTION_ID, mSubscriptionId);
            values.put(Mms.THREAD_ID, threadId);
            values.put(Mms.DATE, timestamp / 1000L);
            values.put(Mms.TEXT_ONLY, true);
            values.put(Mms.MESSAGE_BOX, messageBox);
            values.put(Mms.READ, read);
            values.put(Mms.SEEN, seen);
            values.put(Mms.MESSAGE_TYPE, PduHeaders.MESSAGE_TYPE_SEND_REQ);
            values.put(Mms.MMS_VERSION, PduHeaders.CURRENT_MMS_VERSION);
            values.put(Mms.PRIORITY, PduHeaders.PRIORITY_NORMAL);
            values.put(Mms.READ_REPORT, PduHeaders.VALUE_NO);
            values.put(Mms.TRANSACTION_ID, "T" + Long.toHexString(System.currentTimeMillis()));
            values.put(Mms.DELIVERY_REPORT, PduHeaders.VALUE_NO);
            values.put(Mms.LOCKED, 0);
            values.put(Mms.CONTENT_TYPE, "application/vnd.wap.multipart.related");
            values.put(Mms.MESSAGE_CLASS, PduHeaders.MESSAGE_CLASS_PERSONAL_STR);
            values.put(Mms.MESSAGE_SIZE, mmsBmessage.getSize());

            Uri results = mResolver.insert(contentUri, values);
            mHandleToUriMap.put(handle, results);
            mUriToHandleMap.put(results, new MessageStatus(handle, read));

            logD("Map InsertedThread" + results);

            for (MimePart part : mmsBmessage.getMimeParts()) {
                storeMmsPart(part, results);
            }

            storeAddressPart(message, results);

            String messageContent = mmsBmessage.getMessageAsText();

            values.put(Mms.Part.CONTENT_TYPE, "plain/text");
            values.put(Mms.SUBSCRIPTION_ID, mSubscriptionId);
        } catch (Exception e) {
            Log.e(TAG, e.toString());
            throw e;
        }
    }

    private Uri storeMmsPart(MimePart messagePart, Uri messageUri) {
        ContentValues values = new ContentValues();
        values.put(Mms.Part.CONTENT_TYPE, "text/plain");
        values.put(Mms.Part.CHARSET, DEFAULT_CHARSET);
        values.put(Mms.Part.FILENAME, "text_1.txt");
        values.put(Mms.Part.NAME, "text_1.txt");
        values.put(Mms.Part.CONTENT_ID, messagePart.mContentId);
        values.put(Mms.Part.CONTENT_LOCATION, messagePart.mContentLocation);
        values.put(Mms.Part.TEXT, messagePart.getDataAsString());

        Uri contentUri = Uri.parse(messageUri.toString() + "/part");
        Uri results = mResolver.insert(contentUri, values);
        logD("Inserted" + results);
        return results;
    }

    private void storeAddressPart(Bmessage message, Uri messageUri) {
        ContentValues values = new ContentValues();
        Uri contentUri = Uri.parse(messageUri.toString() + "/addr");
        String originator = getOriginatorNumber(message);
        values.put(Mms.Addr.CHARSET, DEFAULT_CHARSET);

        values.put(Mms.Addr.ADDRESS, originator);
        values.put(Mms.Addr.TYPE, ORIGINATOR_ADDRESS_TYPE);
        mResolver.insert(contentUri, values);

        Set<String> messageContacts = new ArraySet<>();
        getRecipientsFromMessage(message, messageContacts);
        for (String recipient : messageContacts) {
            values.put(Mms.Addr.ADDRESS, recipient);
            values.put(Mms.Addr.TYPE, RECIPIENT_ADDRESS_TYPE);
            mResolver.insert(contentUri, values);
        }
    }

    private Uri insertIntoMmsTable(String subject) {
        ContentValues mmsValues = new ContentValues();
        mmsValues.put(Mms.TEXT_ONLY, 1);
        mmsValues.put(Mms.MESSAGE_TYPE, 128);
        mmsValues.put(Mms.SUBJECT, subject);
        return mResolver.insert(Mms.CONTENT_URI, mmsValues);
    }

    /**
     * cleanUp
     * clear the subscription info and content on shutdown
     */
    void cleanUp() {
        logD("cleanUp(device=" + Utils.getLoggableAddress(mDevice)
                + "subscriptionId=" + mSubscriptionId);
        mResolver.unregisterContentObserver(mContentObserver);
        clearMessages(mContext, mSubscriptionId);
        try {
            mSubscriptionManager.removeSubscriptionInfoRecord(mDevice.getAddress(),
                    SubscriptionManager.SUBSCRIPTION_TYPE_REMOTE_SIM);
            mSubscriptionId = SubscriptionManager.INVALID_SUBSCRIPTION_ID;
        } catch (Exception e) {
            Log.w(TAG, "cleanUp failed: " + e.toString());
        }
    }

    /**
     * clearMessages
     * clean up the content provider on startup
     */
    private static void clearMessages(Context context, int subscriptionId) {
        logD("clearMessages(subscriptionId=" + subscriptionId);

        ContentResolver resolver = context.getContentResolver();
        String threads = new String();

        Uri uri = Threads.CONTENT_URI.buildUpon().appendQueryParameter("simple", "true").build();
        try (Cursor threadCursor = resolver.query(uri, null, null, null, null)) {
            while (threadCursor.moveToNext()) {
                threads += threadCursor.getInt(threadCursor.getColumnIndex(Threads._ID)) + ", ";
            }
        }

        resolver.delete(Sms.CONTENT_URI, Sms.SUBSCRIPTION_ID + " =? ",
                new String[]{Integer.toString(subscriptionId)});
        resolver.delete(Mms.CONTENT_URI, Mms.SUBSCRIPTION_ID + " =? ",
                new String[]{Integer.toString(subscriptionId)});
        if (threads.length() > 2) {
            threads = threads.substring(0, threads.length() - 2);
            resolver.delete(Threads.CONTENT_URI, Threads._ID + " IN (" + threads + ")", null);
        }
    }

    /**
     * getThreadId
     * utilize the originator and recipients to obtain the thread id
     */
    private long getThreadId(Bmessage message) {

        Set<String> messageContacts = new ArraySet<>();
        String originator = PhoneNumberUtils.extractNetworkPortion(getOriginatorNumber(message));
        if (originator != null) {
            messageContacts.add(originator);
        }
        getRecipientsFromMessage(message, messageContacts);
        // If there is only one contact don't remove it.
        if (messageContacts.isEmpty()) {
            return Telephony.Threads.COMMON_THREAD;
        } else if (messageContacts.size() > 1) {
            if (mPhoneNumber == null) {
                Log.w(TAG, "getThreadId called, mPhoneNumber never found.");
            }
            messageContacts.removeIf(number -> (PhoneNumberUtils.areSamePhoneNumber(number,
                    mPhoneNumber, mTelephonyManager.getNetworkCountryIso())));
        }

        logV("Contacts = " + messageContacts.toString());
        return Telephony.Threads.getOrCreateThreadId(mContext, messageContacts);
    }

    private void getRecipientsFromMessage(Bmessage message, Set<String> messageContacts) {
        List<VCardEntry> recipients = message.getRecipients();
        for (VCardEntry recipient : recipients) {
            List<VCardEntry.PhoneData> phoneData = recipient.getPhoneList();
            if (phoneData != null && !phoneData.isEmpty()) {
                messageContacts
                        .add(PhoneNumberUtils.extractNetworkPortion(phoneData.get(0).getNumber()));
            }
        }
    }

    private String getOriginatorNumber(Bmessage message) {
        VCardEntry originator = message.getOriginator();
        if (originator == null) {
            return null;
        }

        List<VCardEntry.PhoneData> phoneData = originator.getPhoneList();
        if (phoneData == null || phoneData.isEmpty()) {
            return null;
        }

        return PhoneNumberUtils.extractNetworkPortion(phoneData.get(0).getNumber());
    }

    private String getFirstRecipientNumber(Bmessage message) {
        List<VCardEntry> recipients = message.getRecipients();
        if (recipients == null || recipients.isEmpty()) {
            return null;
        }

        List<VCardEntry.PhoneData> phoneData = recipients.get(0).getPhoneList();
        if (phoneData == null || phoneData.isEmpty()) {
            return null;
        }

        return phoneData.get(0).getNumber();
    }

    /**
     * addThreadContactToEntries
     * utilizing the thread id fill in the appropriate fields of bmsg with the intended recipients
     */
    boolean addThreadContactsToEntries(Bmessage bmsg, String thread) {
        String threadId = Uri.parse(thread).getLastPathSegment();

        logD("MATCHING THREAD" + threadId);
        logD(MmsSms.CONTENT_CONVERSATIONS_URI + threadId + "/recipients");

        try (Cursor cursor =
                mResolver.query(
                        Uri.withAppendedPath(
                                MmsSms.CONTENT_CONVERSATIONS_URI, threadId + "/recipients"),
                        null,
                        null,
                        null,
                        null)) {

            if (cursor.moveToNext()) {
                logD("Columns" + Arrays.toString(cursor.getColumnNames()));
                logV("CONTACT LIST: " + cursor.getString(cursor.getColumnIndex("recipient_ids")));
                addRecipientsToEntries(
                        bmsg, cursor.getString(cursor.getColumnIndex("recipient_ids")).split(" "));
                return true;
            } else {
                Log.w(TAG, "Thread Not Found");
                return false;
            }
        }
    }


    private void addRecipientsToEntries(Bmessage bmsg, String[] recipients) {
        logV("CONTACT LIST: " + Arrays.toString(recipients));
        for (String recipient : recipients) {
            try (Cursor cursor =
                    mResolver.query(
                            Uri.parse("content://mms-sms/canonical-address/" + recipient),
                            null,
                            null,
                            null,
                            null)) {
                while (cursor.moveToNext()) {
                    String number = cursor.getString(cursor.getColumnIndex(Mms.Addr.ADDRESS));
                    logV("CONTACT number: " + number);
                    VCardEntry destEntry = new VCardEntry();
                    VCardProperty destEntryPhone = new VCardProperty();
                    destEntryPhone.setName(VCardConstants.PROPERTY_TEL);
                    destEntryPhone.addValues(number);
                    destEntry.addProperty(destEntryPhone);
                    bmsg.addRecipient(destEntry);
                }
            }
        }
    }

    /**
     * Get the total number of messages we've stored under this device's subscription ID, for a
     * given message source, provided by the "uri" parameter.
     */
    private int getStoredMessagesCount(Uri uri) {
        if (mSubscriptionId == SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
            logV("getStoredMessagesCount(uri=" + uri + "): Failed, no subscription ID");
            return 0;
        }

        Cursor cursor = null;
        if (Sms.CONTENT_URI.equals(uri) || Sms.Inbox.CONTENT_URI.equals(uri)
                || Sms.Sent.CONTENT_URI.equals(uri)) {
            cursor = mResolver.query(uri, new String[] {"count(*)"}, Sms.SUBSCRIPTION_ID + " =? ",
                    new String[]{Integer.toString(mSubscriptionId)}, null);
        } else if (Mms.CONTENT_URI.equals(uri) || Mms.Inbox.CONTENT_URI.equals(uri)
                || Mms.Sent.CONTENT_URI.equals(uri)) {
            cursor = mResolver.query(uri, new String[] {"count(*)"}, Mms.SUBSCRIPTION_ID + " =? ",
                    new String[]{Integer.toString(mSubscriptionId)}, null);
        } else if (Threads.CONTENT_URI.equals(uri)) {
            uri = Threads.CONTENT_URI.buildUpon().appendQueryParameter("simple", "true").build();
            cursor = mResolver.query(uri, new String[] {"count(*)"}, null, null, null);
        }

        if (cursor == null) {
            return 0;
        }

        cursor.moveToFirst();
        int count = cursor.getInt(0);
        cursor.close();

        return count;
    }

    private List<MessageDumpElement> getRecentMessagesFromFolder(Folder folder) {
        Uri smsUri = null;
        Uri mmsUri = null;
        if (folder == Folder.INBOX) {
            smsUri = Sms.Inbox.CONTENT_URI;
            mmsUri = Mms.Inbox.CONTENT_URI;
        } else if (folder == Folder.SENT) {
            smsUri = Sms.Sent.CONTENT_URI;
            mmsUri = Mms.Sent.CONTENT_URI;
        } else {
            Log.w(TAG, "getRecentMessagesFromFolder: Failed, unsupported folder=" + folder);
            return null;
        }

        ArrayList<MessageDumpElement> messages = new ArrayList<MessageDumpElement>();
        for (Uri uri : new Uri[] {smsUri, mmsUri}) {
            messages.addAll(getMessagesFromUri(uri));
        }
        logV(
                "getRecentMessagesFromFolder: "
                        + folder
                        + ", "
                        + messages.size()
                        + " messages found.");

        Collections.sort(messages);
        if (messages.size() > NUM_RECENT_MSGS_TO_DUMP) {
            return messages.subList(0, NUM_RECENT_MSGS_TO_DUMP);
        }
        return messages;
    }

    private List<MessageDumpElement> getMessagesFromUri(Uri uri) {
        logD("getMessagesFromUri: uri=" + uri);
        ArrayList<MessageDumpElement> messages = new ArrayList<MessageDumpElement>();

        if (mSubscriptionId == SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
            Log.w(TAG, "getMessagesFromUri: Failed, no subscription ID");
            return messages;
        }

        Type type = getMessageTypeFromUri(uri);
        if (type == Type.UNKNOWN) {
            Log.w(TAG, "getMessagesFromUri: unknown message type");
            return messages;
        }

        String[] selectionArgs = new String[] {Integer.toString(mSubscriptionId)};
        String limit = " LIMIT " + NUM_RECENT_MSGS_TO_DUMP;
        String[] projection = null;
        String selectionClause = null;
        String threadIdColumnName = null;
        String timestampColumnName = null;

        if (type == Type.SMS) {
            projection = new String[] {BaseColumns._ID, Sms.THREAD_ID, Sms.DATE};
            selectionClause = Sms.SUBSCRIPTION_ID + " =? ";
            threadIdColumnName = Sms.THREAD_ID;
            timestampColumnName = Sms.DATE;
        } else if (type == Type.MMS) {
            projection = new String[] {BaseColumns._ID, Mms.THREAD_ID, Mms.DATE};
            selectionClause = Mms.SUBSCRIPTION_ID + " =? ";
            threadIdColumnName = Mms.THREAD_ID;
            timestampColumnName = Mms.DATE;
        }

        Cursor cursor =
                mResolver.query(
                        uri,
                        projection,
                        selectionClause,
                        selectionArgs,
                        timestampColumnName + " DESC" + limit);

        try {
            if (cursor == null) {
                Log.w(TAG, "getMessagesFromUri: null cursor for uri=" + uri);
                return messages;
            }
            logV("Number of rows in cursor = " + cursor.getCount() + ", for uri=" + uri);

            cursor.moveToPosition(-1);
            while (cursor.moveToNext()) {
                // Even though {@link storeSms} and {@link storeMms} use Uris that contain the
                // folder name (e.g., {@code Sms.Inbox.CONTENT_URI}), the Uri returned by
                // {@link ContentResolver#insert} does not (e.g., {@code Sms.CONTENT_URI}).
                // Therefore, the Uris in the keyset of {@code mUriToHandleMap} do not contain
                // the folder name, but unfortunately, the Uri passed in to query the database
                // does contains the folder name, so we can't simply append messageId to the
                // passed-in Uri.
                String messageId = cursor.getString(cursor.getColumnIndex(BaseColumns._ID));
                Uri messageUri =
                        Uri.withAppendedPath(
                                type == Type.SMS ? Sms.CONTENT_URI : Mms.CONTENT_URI, messageId);

                MessageStatus handleAndStatus = mUriToHandleMap.get(messageUri);
                String messageHandle = "<unknown>";
                if (handleAndStatus == null) {
                    Log.w(TAG, "getMessagesFromUri: no entry for message uri=" + messageUri);
                } else {
                    messageHandle = handleAndStatus.mHandle;
                }

                long timestamp = cursor.getLong(cursor.getColumnIndex(timestampColumnName));
                // TODO: why does `storeMms` truncate down to the seconds instead of keeping it
                // millisec, like `storeSms`?
                if (type == Type.MMS) {
                    timestamp *= 1000L;
                }

                messages.add(
                        new MessageDumpElement(
                                messageHandle,
                                messageUri,
                                timestamp,
                                cursor.getLong(cursor.getColumnIndex(threadIdColumnName)),
                                type));
            }
        } catch (Exception e) {
            Log.w(TAG, "Exception when querying db for dumpsys", e);
        } finally {
            cursor.close();
        }
        return messages;
    }

    private Type getMessageTypeFromUri(Uri uri) {
        if (Sms.CONTENT_URI.equals(uri)
                || Sms.Inbox.CONTENT_URI.equals(uri)
                || Sms.Sent.CONTENT_URI.equals(uri)) {
            return Type.SMS;
        } else if (Mms.CONTENT_URI.equals(uri)
                || Mms.Inbox.CONTENT_URI.equals(uri)
                || Mms.Sent.CONTENT_URI.equals(uri)) {
            return Type.MMS;
        } else {
            return Type.UNKNOWN;
        }
    }

    public void dump(StringBuilder sb) {
        sb.append("    Device Message DB:");
        sb.append("\n      Subscription ID: " + mSubscriptionId);
        if (mSubscriptionId != SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
            sb.append("\n      SMS Messages (Inbox/Sent/Total): "
                    + getStoredMessagesCount(Sms.Inbox.CONTENT_URI)
                    + " / " + getStoredMessagesCount(Sms.Sent.CONTENT_URI)
                    + " / " + getStoredMessagesCount(Sms.CONTENT_URI));

            sb.append("\n      MMS Messages (Inbox/Sent/Total): "
                    + getStoredMessagesCount(Mms.Inbox.CONTENT_URI)
                    + " / " + getStoredMessagesCount(Mms.Sent.CONTENT_URI)
                    + " / " + getStoredMessagesCount(Mms.CONTENT_URI));

            sb.append("\n      Threads: " + getStoredMessagesCount(Threads.CONTENT_URI));

            sb.append("\n      Most recent 'Sent' messages:");
            sb.append("\n        " + MessageDumpElement.getFormattedColumnNames());
            for (MessageDumpElement e : getRecentMessagesFromFolder(Folder.SENT)) {
                sb.append("\n        " + e);
            }
            sb.append("\n      Most recent 'Inbox' messages:");
            sb.append("\n        " + MessageDumpElement.getFormattedColumnNames());
            for (MessageDumpElement e : getRecentMessagesFromFolder(Folder.INBOX)) {
                sb.append("\n        " + e);
            }
        }
        sb.append("\n");
    }

    /**
     * MessageStatus
     *
     * Helper class to store associations between remote and local provider based on message handle
     * and read status
     */
    class MessageStatus {

        String mHandle;
        int mRead;

        MessageStatus(String handle, int read) {
            mHandle = handle;
            mRead = read;
        }

        @Override
        public boolean equals(Object other) {
            return ((other instanceof MessageStatus) && ((MessageStatus) other).mHandle
                    .equals(mHandle));
        }
    }

    @SuppressWarnings("GoodTime") // Use system time zone to render times for logging
    private static String toDatetimeString(long epochMillis) {
        return DateTimeFormatter.ofPattern("MM-dd HH:mm:ss.SSS")
                .format(
                        Instant.ofEpochMilli(epochMillis)
                                .atZone(ZoneId.systemDefault())
                                .toLocalDateTime());
    }

    private static class MessageDumpElement implements Comparable<MessageDumpElement> {
        private String mMessageHandle;
        private long mTimestamp;
        private Type mType;
        private long mThreadId;
        private Uri mUri;

        MessageDumpElement(String handle, Uri uri, long timestamp, long threadId, Type type) {
            mMessageHandle = handle;
            mTimestamp = timestamp;
            mUri = uri;
            mThreadId = threadId;
            mType = type;
        }

        public static String getFormattedColumnNames() {
            return String.format(
                    "%-19s %s %-16s %s %s", "Timestamp", "ThreadId", "Handle", "Type", "Uri");
        }

        @Override
        public String toString() {
            return String.format(
                    "%-19s %8d %-16s %-4s %s",
                    toDatetimeString(mTimestamp), mThreadId, mMessageHandle, mType, mUri);
        }

        @Override
        public int compareTo(MessageDumpElement e) {
            // we want reverse chronological.
            if (this.mTimestamp < e.mTimestamp) {
                return 1;
            } else if (this.mTimestamp > e.mTimestamp) {
                return -1;
            } else {
                return 0;
            }
        }
    }
}
