/*
 * Copyright (C) 2022 The Android Open Source Project
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

import static java.lang.Math.min;

import android.telephony.PhoneNumberUtils;
import android.util.Log;

import com.android.bluetooth.ObexAppParameters;
import com.android.internal.annotations.VisibleForTesting;
import com.android.obex.ClientSession;
import com.android.obex.HeaderSet;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Request to get a listing of messages in directory. Listing is used to determine the
 * remote device's own phone number. Searching the SENT folder is the most reliable way
 * since there should only be one Originator (From:), as opposed to the INBOX folder,
 * where there can be multiple Recipients (To: and Cc:).
 *
 * Ideally, only a single message is needed; however, the Originator (From:) field in the listing
 * is optional (not required by specs). Hence, a geometrically increasing sliding window is used
 * to request additional message listings until either a number is found or folders have been
 * exhausted.
 *
 * The sliding window is automated (i.e., offset and size, transitions across folders). Simply use
 * the same {@link RequestGetMessagesListingForOwnNumber} repeatedly with {@link
 * MasClient#makeRequest}. {@link #isSearchCompleted} indicates when the search is complete,
 * i.e., the object cannot be used further.
 */
class RequestGetMessagesListingForOwnNumber extends Request {
    private static final String TAG = RequestGetMessagesListingForOwnNumber.class.getSimpleName();

    private static final String TYPE = "x-bt/MAP-msg-listing";

    // Search for sent messages (MMS or SMS) first. If that fails, search for received SMS.
    @VisibleForTesting
    static final List<String> FOLDERS_TO_SEARCH = new ArrayList<>(Arrays.asList(
            MceStateMachine.FOLDER_SENT,
            MceStateMachine.FOLDER_INBOX
    ));

    private static final int MAX_LIST_COUNT_INITIAL = 1;
    // NOTE: the value is not "final" so that it can be modified in the unit tests
    @VisibleForTesting
    static int sMaxListCountUpperLimit = 65535;
    private static final int LIST_START_OFFSET_INITIAL = 0;
    // NOTE: the value is not "final" so that it can be modified in the unit tests
    @VisibleForTesting
    static int sListStartOffsetUpperLimit = 65535;

    /**
     * A geometrically increasing sliding window for messages to list.
     *
     * E.g., if we don't find the phone number in the 1st message, try the next 2, then the next 4,
     * then the next 8, etc.
     */
    private static class MessagesSlidingWindow {
        private int mListStartOffset;
        private int mMaxListCount;

        MessagesSlidingWindow() {
            reset();
        }

        /**
         * Returns false if start of window exceeds range; o.w. returns true.
         */
        public boolean moveWindow() {
            if (mListStartOffset > sListStartOffsetUpperLimit) {
                return false;
            }
            mListStartOffset = mListStartOffset + mMaxListCount;
            if (mListStartOffset > sListStartOffsetUpperLimit) {
                return false;
            }
            mMaxListCount = min(2 * mMaxListCount, sMaxListCountUpperLimit);
            logD(String.format(Locale.US,
                    "MessagesSlidingWindow, moveWindow: startOffset=%d, maxCount=%d",
                    mListStartOffset, mMaxListCount));
            return true;
        }

        public void reset() {
            mListStartOffset = LIST_START_OFFSET_INITIAL;
            mMaxListCount = MAX_LIST_COUNT_INITIAL;
        }

        public int getStartOffset() {
            return mListStartOffset;
        }

        public int getMaxCount() {
            return mMaxListCount;
        }
    }
    private MessagesSlidingWindow mMessageListingWindow;

    private ObexAppParameters mOap;

    private int mFolderCounter;
    private boolean mSearchCompleted;
    private String mPhoneNumber;

    RequestGetMessagesListingForOwnNumber() {
        mHeaderSet.setHeader(HeaderSet.TYPE, TYPE);
        mOap = new ObexAppParameters();

        mMessageListingWindow = new MessagesSlidingWindow();

        mFolderCounter = 0;
        setupCurrentFolderForSearch();

        mSearchCompleted = false;
        mPhoneNumber = null;
    }

    @Override
    protected void readResponse(InputStream stream) {
        if (mSearchCompleted) {
            return;
        }

        MessagesListing response = new MessagesListing(stream);

        if (response == null) {
            // This shouldn't have happened; move on to the next window
            logD("readResponse: null Response, moving to next window");
            moveToNextWindow();
            return;
        }

        ArrayList<Message> messageListing = response.getList();
        if (messageListing == null || messageListing.isEmpty()) {
            // No more messages in this folder; move on to the next folder;
            logD("readResponse: no messages, moving to next folder");
            moveToNextFolder();
            return;
        }

        // Search through message listing for own phone number.
        // Message listings by spec arrive ordered newest first.
        String folderName = FOLDERS_TO_SEARCH.get(mFolderCounter);
        logD(String.format(Locale.US,
                "readResponse: Folder=%s, # of msgs=%d, startOffset=%d, maxCount=%d",
                folderName, messageListing.size(),
                mMessageListingWindow.getStartOffset(), mMessageListingWindow.getMaxCount()));
        String number = null;
        for (int i = 0; i < messageListing.size(); i++) {
            Message msg = messageListing.get(i);
            if (MceStateMachine.FOLDER_INBOX.equals(folderName)) {
                number = PhoneNumberUtils.extractNetworkPortion(
                        msg.getRecipientAddressing());
            } else if (MceStateMachine.FOLDER_SENT.equals(folderName)) {
                number = PhoneNumberUtils.extractNetworkPortion(
                        msg.getSenderAddressing());
            }
            if (number != null && !number.trim().isEmpty()) {
                // Search is completed when a phone number is found
                mPhoneNumber = number;
                mSearchCompleted = true;
                logD(String.format("readResponse: phone number found = %s", mPhoneNumber));
                return;
            }
        }

        // If a number hasn't been found, move on to the next window.
        if (!mSearchCompleted) {
            logD("readResponse: number hasn't been found, moving to next window");
            moveToNextWindow();
        }
    }

    /**
     * Move on to next folder to start searching (sliding window).
     *
     * Overall search for own-phone-number is completed when we run out of folders to search.
     */
    private void moveToNextFolder() {
        if (mFolderCounter < FOLDERS_TO_SEARCH.size() - 1) {
            mFolderCounter += 1;
            setupCurrentFolderForSearch();
        } else {
            logD("moveToNextFolder: folders exhausted, search complete");
            mSearchCompleted = true;
        }
    }

    /**
     * Tries sliding the window in the current folder.
     *   - If successful (didn't exceed range), update the headers to reflect new window's
     *     offset and size.
     *   - If fails (exceeded range), move on to the next folder.
     */
    private void moveToNextWindow() {
        if (mMessageListingWindow.moveWindow()) {
            setListOffsetAndMaxCountInHeaderSet(mMessageListingWindow.getMaxCount(),
                    mMessageListingWindow.getStartOffset());
        } else {
            // Can't slide window anymore, exceeded range; move on to next folder
            logD("moveToNextWindow: can't slide window anymore, folder complete");
            moveToNextFolder();
        }
    }

    /**
     * Set up the current folder for searching:
     *   1. Updates headers to reflect new folder name.
     *   2. Resets the sliding window.
     *   3. Updates headers to reflect new window's offset and size.
     */
    private void setupCurrentFolderForSearch() {
        String folderName = FOLDERS_TO_SEARCH.get(mFolderCounter);
        mHeaderSet.setHeader(HeaderSet.NAME, folderName);

        byte filter = messageTypeBasedOnFolder(folderName);
        mOap.add(OAP_TAGID_FILTER_MESSAGE_TYPE, filter);
        mOap.addToHeaderSet(mHeaderSet);

        mMessageListingWindow.reset();
        int maxCount = mMessageListingWindow.getMaxCount();
        int offset = mMessageListingWindow.getStartOffset();
        setListOffsetAndMaxCountInHeaderSet(maxCount, offset);
        logD(String.format(Locale.US,
                "setupCurrentFolderForSearch: folder=%s, filter=%d, offset=%d, maxCount=%d",
                folderName, filter, maxCount, offset));
    }

    private byte messageTypeBasedOnFolder(String folderName) {
        byte messageType = (byte) (MessagesFilter.MESSAGE_TYPE_SMS_GSM
                | MessagesFilter.MESSAGE_TYPE_SMS_CDMA
                | MessagesFilter.MESSAGE_TYPE_MMS);

        // If trying to grab own number from messages received by the remote device,
        // only use SMS messages since SMS will only have one recipient (the remote device),
        // whereas MMS may have more than one recipient (e.g., group MMS or if the originator
        // is also CC-ed as a recipient). Even if there is only one recipient presented to
        // Bluetooth in a group MMS, it may not necessarily correspond to the remote device;
        // there is no specification governing the `To:` and `Cc:` fields in the MMS specs.
        if (MceStateMachine.FOLDER_INBOX.equals(folderName)) {
            messageType = (byte) (MessagesFilter.MESSAGE_TYPE_SMS_GSM
                    | MessagesFilter.MESSAGE_TYPE_SMS_CDMA);
        }

        return messageType;
    }

    private void setListOffsetAndMaxCountInHeaderSet(int maxListCount, int listStartOffset) {
        mOap.add(OAP_TAGID_MAX_LIST_COUNT, (short) maxListCount);
        mOap.add(OAP_TAGID_START_OFFSET, (short) listStartOffset);

        mOap.addToHeaderSet(mHeaderSet);
    }

    /**
     * Returns {@code null} if {@code readResponse} has not completed or if no
     * phone number was obtained from the Message Listing.
     *
     * Otherwise, returns the remote device's own phone number.
     */
    public String getOwnNumber() {
        return mPhoneNumber;
    }

    public boolean isSearchCompleted() {
        return mSearchCompleted;
    }

    @Override
    public void execute(ClientSession session) throws IOException {
        executeGet(session);
    }

    private static void logD(String message) {
        if (MapClientService.DBG) {
            Log.d(TAG, message);
        }
    }
}
