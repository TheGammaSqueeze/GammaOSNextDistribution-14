/*
 * Copyright 2022 The Android Open Source Project
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

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import android.util.Log;

import androidx.test.runner.AndroidJUnit4;

import com.android.bluetooth.ObexAppParameters;
import com.android.bluetooth.map.BluetoothMapMessageListing;
import com.android.bluetooth.map.BluetoothMapMessageListingElement;
import com.android.obex.HeaderSet;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RunWith(AndroidJUnit4.class)
public class RequestGetMessagesListingForOwnNumberTest {
    private static final String TAG =
            RequestGetMessagesListingForOwnNumberTest.class.getSimpleName();

    private static final int MAX_LIST_COUNT_UPPER_LIMIT = 100;
    private static final int LIST_START_OFFSET_UPPER_LIMIT = 100;
    // Window size doubles every iteration, e.g., if a folder has at most
    // {@code MAX_LIST_COUNT_UPPER_LIMIT == 100} elements, then at most {@code 7} iterations are
    // needed to search a folder, so a total of {@code 14} iterations for two folders.
    private static final int MAX_ITERATIONS = 14;

    private static final String TEST_OWN_NUMBER = "5551234";
    private static final String TEST_NO_NUMBER = "";
    private static final String TEST_OTHER_NUMBER_TX = "5556789";
    private static final String TEST_OTHER_NUMBER_RX = "5559876";

    private static final String TEST_MSG_LISTING_OBJ_VERSION = "1.0";
    private static final boolean TEST_INCLUDE_THREAD_ID_ENCODE = false;

    private BluetoothMapMessageListingElement mSMSWithOwnNumberAsRecipient;
    private BluetoothMapMessageListingElement mMMSWithOwnNumberAsSender;
    private BluetoothMapMessageListingElement mSMSWithoutOwnNumber;

    private static class FakeMessageFoldersForListing {
        private Map<String, List<BluetoothMapMessageListingElement>> mFolders = new HashMap<>();

        /**
         * @param folder - the folder you want to create messages for.
         * @param size - the number of messages in {@code folder}
         * @param fillerElements - placeholder elements to fill up the folder.
         * @param targetElement - the element of interest that contains the info you want.
         * @param position - the index in the folder where {@code targetElement} is to be inserted.
         *                   If {@code -1} or otherwise outside range, {@code folder} will not
         *                   contain {@code targetElement}.
         */
        public void createMessageFolder(String folder, int size,
                BluetoothMapMessageListingElement fillerElements,
                BluetoothMapMessageListingElement targetElement, int position) {
            List<BluetoothMapMessageListingElement> list = new ArrayList<>();
            for (int i = 0; i < size; i++) {
                list.add(fillerElements);
            }
            Log.d(TAG, "Folder [" + folder + "] created with (" + list.size() + ") msgs");
            if (position >= 0 && position < size) {
                list.set(position, targetElement);
                Log.d(TAG, "Target element added to [" + folder + "] at (" + position + ") index");
            }

            if (folder != null) {
                mFolders.put(folder, list);
            }
        }

        public InputStream getMessageListingAsInputStream(String folder, int offset, int maxCount,
                byte msgTypeFilter) {
            List<BluetoothMapMessageListingElement> folderElements = mFolders.get(folder);

            BluetoothMapMessageListing requestedListing = new BluetoothMapMessageListing();
            if (folderElements != null
                    && offset >= 0 && offset < LIST_START_OFFSET_UPPER_LIMIT
                    && maxCount >= 0 && maxCount < MAX_LIST_COUNT_UPPER_LIMIT) {
                int msgCount = 0;
                for (int i = offset; i < folderElements.size() && msgCount < maxCount; i++ ) {
                    BluetoothMapMessageListingElement element = folderElements.get(i);
                    byte msgType = listingElementGetType(element);
                    if ((msgTypeFilter & msgType) > 0) {
                        requestedListing.add(element);
                        msgCount += 1;
                    }
                    Log.d(TAG, "getMessageListingAsInputStream: i=" + i + ", msgCount=" + msgCount
                            + ", msgType=" + msgType + ", msgTypeFilter=" + msgTypeFilter);
                }
            }
            byte[] encodedListing = null;
            try {
                encodedListing = requestedListing.encode(TEST_INCLUDE_THREAD_ID_ENCODE,
                        TEST_MSG_LISTING_OBJ_VERSION);
            } catch (Exception e) {
                assertWithMessage("Cannot encode MessageListing: " + e.getMessage()).fail();
            }
            final InputStream listingAsStream = new ByteArrayInputStream(encodedListing);

            return listingAsStream;
        }
    }

    /**
     * Map Client uses bytes to represent message types (e.g., MMS, SMS, Email) (c.f.
     * {@link MessagesFilter}). However, Map Server (i.e.,
     * {@link BluetoothMapMessageListingElement}) uses enum to represent types (c.f.,
     * {@link BluetoothMapUtils}). Instead, we'll be abusing
     * {@link BluetoothMapMessageListingElement#mAttachmentSize} to store Map Client's type,
     * since it's otherwise unused in the tests.
     */
    static void listingElementSetType(BluetoothMapMessageListingElement element, byte type) {
        element.setAttachmentSize(type);
    }

    /**
     * Map Client uses bytes to represent message types (e.g., MMS, SMS, Email) (c.f.
     * {@link MessagesFilter}). However, Map Server (i.e.,
     * {@link BluetoothMapMessageListingElement}) uses enum to represent types (c.f.,
     * {@link BluetoothMapUtils}). Instead, we'll be abusing
     * {@link BluetoothMapMessageListingElement#mAttachmentSize} to store Map Client's type,
     * since it's otherwise unused in the tests.
     */
    static byte listingElementGetType(BluetoothMapMessageListingElement element) {
        return (byte) element.getAttachmentSize();
    }

    @Before
    public void setUp() {
        // Override the MAX_LIST_COUNT upper limit to speed up tests
        RequestGetMessagesListingForOwnNumber.sMaxListCountUpperLimit =
                MAX_LIST_COUNT_UPPER_LIMIT;
        // Override the START_OFFSET upper limit to speed up tests
        RequestGetMessagesListingForOwnNumber.sListStartOffsetUpperLimit =
                LIST_START_OFFSET_UPPER_LIMIT;

        mSMSWithOwnNumberAsRecipient = new BluetoothMapMessageListingElement();
        mSMSWithOwnNumberAsRecipient.setSenderAddressing(TEST_OTHER_NUMBER_TX);
        mSMSWithOwnNumberAsRecipient.setRecipientAddressing(TEST_OWN_NUMBER);
        listingElementSetType(mSMSWithOwnNumberAsRecipient, MessagesFilter.MESSAGE_TYPE_SMS_GSM);

        mMMSWithOwnNumberAsSender = new BluetoothMapMessageListingElement();
        mMMSWithOwnNumberAsSender.setSenderAddressing(TEST_OWN_NUMBER);
        mMMSWithOwnNumberAsSender.setRecipientAddressing(TEST_OTHER_NUMBER_RX);
        listingElementSetType(mMMSWithOwnNumberAsSender, MessagesFilter.MESSAGE_TYPE_MMS);

        mSMSWithoutOwnNumber = new BluetoothMapMessageListingElement();
        mSMSWithoutOwnNumber.setSenderAddressing(TEST_NO_NUMBER);
        mSMSWithoutOwnNumber.setRecipientAddressing(TEST_NO_NUMBER);
        listingElementSetType(mSMSWithoutOwnNumber, MessagesFilter.MESSAGE_TYPE_SMS_GSM);
    }

    private String testGetOwnNumberBase(int sizeSentFolder, int sizeInboxFolder,
            int positionSentFolder, int positionInboxFolder,
            BluetoothMapMessageListingElement targetElement) {
        FakeMessageFoldersForListing folders = new FakeMessageFoldersForListing();
        folders.createMessageFolder(MceStateMachine.FOLDER_SENT, sizeSentFolder,
                mSMSWithoutOwnNumber, targetElement, positionSentFolder);
        folders.createMessageFolder(MceStateMachine.FOLDER_INBOX, sizeInboxFolder,
                mSMSWithoutOwnNumber, targetElement, positionInboxFolder);

        RequestGetMessagesListingForOwnNumber newRequest =
                new RequestGetMessagesListingForOwnNumber();

        for (int i = 0; !newRequest.isSearchCompleted() && i < MAX_ITERATIONS; i ++) {
            String folderName = null;
            try {
                folderName = (String) newRequest.mHeaderSet.getHeader(HeaderSet.NAME);
            } catch (Exception e) {
                assertWithMessage("Cannot obtain folder name from Request's HeaderSet: "
                        + e.getMessage()).fail();
            }
            ObexAppParameters oap = ObexAppParameters.fromHeaderSet(newRequest.mHeaderSet);
            byte filterMessageType = oap.getByte(Request.OAP_TAGID_FILTER_MESSAGE_TYPE);
            int maxListCount = (int) oap.getShort(Request.OAP_TAGID_MAX_LIST_COUNT);
            int startOffset = (int) oap.getShort(Request.OAP_TAGID_START_OFFSET);

            Log.d(TAG, String.format("testBase: filter=%b, count=%b, offset=%b",
                    oap.exists(Request.OAP_TAGID_FILTER_MESSAGE_TYPE),
                    oap.exists(Request.OAP_TAGID_MAX_LIST_COUNT),
                    oap.exists(Request.OAP_TAGID_START_OFFSET)));

            Log.d(TAG, String.format(
                    "testBase: [i=%d] folder=%s, maxCount=%d, offset=%d, filterMessageType=%02X",
                    i, folderName, maxListCount, startOffset, filterMessageType));

            newRequest.readResponse(
                    folders.getMessageListingAsInputStream(folderName, startOffset, maxListCount,
                    filterMessageType));
        }

        return newRequest.getOwnNumber();
    }

    /**
     * Preconditions:
     * - SENT is empty.
     * - INBOX is empty.
     *
     * Actions:
     * - Invoke {@link RequestGetMessagesListingForOwnNumber#readResponse} repeatedly until the
     *   own number has been found or folder elements have been exhausted.
     *
     * Outcome:
     * - Own number is not found.
     */
    @Test
    public void testEmpty_Empty_null() {
        int sentFolderSize = 0;
        int inboxFolderSize = 0;
        int sentFolderPosition = -1;
        int inboxFolderPosition = -1;
        BluetoothMapMessageListingElement targetElement = null;

        String ownNumber = testGetOwnNumberBase(sentFolderSize, inboxFolderSize,
                sentFolderPosition, inboxFolderPosition, targetElement);

        assertThat(ownNumber).isNull();
    }

    /**
     * Preconditions:
     * - SENT number of messages is half of {@code LIST_START_OFFSET_UPPER_LIMIT}.
     *     - MMS with own number as sender is the first message in SENT folder.
     * - INBOX is empty.
     *
     * Actions:
     * - Invoke {@link RequestGetMessagesListingForOwnNumber#readResponse} repeatedly until the
     *   own number has been found or folder elements have been exhausted.
     *
     * Outcome:
     * - Own number is found.
     */
    @Test
    public void testHalfFirst_Empty_found() {
        int sentFolderSize = LIST_START_OFFSET_UPPER_LIMIT / 2;
        int inboxFolderSize = 0;
        int sentFolderPosition = 0;
        int inboxFolderPosition = -1;
        BluetoothMapMessageListingElement targetElement = mMMSWithOwnNumberAsSender;

        String ownNumber = testGetOwnNumberBase(sentFolderSize, inboxFolderSize,
                sentFolderPosition, inboxFolderPosition, targetElement);

        assertThat(ownNumber).isEqualTo(TEST_OWN_NUMBER);
    }

    /**
     * Preconditions:
     * - SENT number of messages is half of {@code LIST_START_OFFSET_UPPER_LIMIT}.
     *     - MMS with own number as sender is the last message in SENT folder.
     * - INBOX is empty.
     *
     * Actions:
     * - Invoke {@link RequestGetMessagesListingForOwnNumber#readResponse} repeatedly until the
     *   own number has been found or folder elements have been exhausted.
     *
     * Outcome:
     * - Own number is found.
     */
    @Test
    public void testHalfLast_Empty_found() {
        int sentFolderSize = LIST_START_OFFSET_UPPER_LIMIT / 2;
        int inboxFolderSize = 0;
        int sentFolderPosition = sentFolderSize - 1;
        int inboxFolderPosition = -1;
        BluetoothMapMessageListingElement targetElement = mMMSWithOwnNumberAsSender;

        String ownNumber = testGetOwnNumberBase(sentFolderSize, inboxFolderSize,
                sentFolderPosition, inboxFolderPosition, targetElement);

        assertThat(ownNumber).isEqualTo(TEST_OWN_NUMBER);
    }

    /**
     * Preconditions:
     * - SENT number of messages is equal to {@code LIST_START_OFFSET_UPPER_LIMIT}.
     *     - MMS with own number as sender is the last message in SENT folder.
     * - INBOX is empty.
     *
     * Actions:
     * - Invoke {@link RequestGetMessagesListingForOwnNumber#readResponse} repeatedly until the
     *   own number has been found or folder elements have been exhausted.
     *
     * Outcome:
     * - Own number is found.
     */
    @Test
    public void testFullLast_Empty_found() {
        int sentFolderSize = LIST_START_OFFSET_UPPER_LIMIT;
        int inboxFolderSize = 0;
        int sentFolderPosition = sentFolderSize - 1;
        int inboxFolderPosition = -1;
        BluetoothMapMessageListingElement targetElement = mMMSWithOwnNumberAsSender;

        String ownNumber = testGetOwnNumberBase(sentFolderSize, inboxFolderSize,
                sentFolderPosition, inboxFolderPosition, targetElement);

        assertThat(ownNumber).isEqualTo(TEST_OWN_NUMBER);
    }

    /**
     * Preconditions:
     * - SENT number of messages is half of {@code LIST_START_OFFSET_UPPER_LIMIT}.
     * - INBOX number of messages is half of {@code LIST_START_OFFSET_UPPER_LIMIT}.
     *     - SMS with own number as recipient is the first message in INBOX folder.
     *
     * Actions:
     * - Invoke {@link RequestGetMessagesListingForOwnNumber#readResponse} repeatedly until the
     *   own number has been found or folder elements have been exhausted.
     *
     * Outcome:
     * - Own number is found.
     */
    @Test
    public void testHalf_HalfFirst_found() {
        int sentFolderSize = LIST_START_OFFSET_UPPER_LIMIT / 2;
        int inboxFolderSize = LIST_START_OFFSET_UPPER_LIMIT / 2;
        int sentFolderPosition = -1;
        int inboxFolderPosition = 0;
        BluetoothMapMessageListingElement targetElement = mSMSWithOwnNumberAsRecipient;

        String ownNumber = testGetOwnNumberBase(sentFolderSize, inboxFolderSize,
                sentFolderPosition, inboxFolderPosition, targetElement);

        assertThat(ownNumber).isEqualTo(TEST_OWN_NUMBER);
    }

    /**
     * Preconditions:
     * - SENT number of messages is half of {@code LIST_START_OFFSET_UPPER_LIMIT}.
     * - INBOX number of messages is half of {@code LIST_START_OFFSET_UPPER_LIMIT}.
     *     - SMS with own number as recipient is the last message in INBOX folder.
     *
     * Actions:
     * - Invoke {@link RequestGetMessagesListingForOwnNumber#readResponse} repeatedly until the
     *   own number has been found or folder elements have been exhausted.
     *
     * Outcome:
     * - Own number is found.
     */
    @Test
    public void testHalf_HalfLast_found() {
        int sentFolderSize = LIST_START_OFFSET_UPPER_LIMIT / 2;
        int inboxFolderSize = LIST_START_OFFSET_UPPER_LIMIT / 2;
        int sentFolderPosition = -1;
        int inboxFolderPosition = inboxFolderSize - 1;
        BluetoothMapMessageListingElement targetElement = mSMSWithOwnNumberAsRecipient;

        String ownNumber = testGetOwnNumberBase(sentFolderSize, inboxFolderSize,
                sentFolderPosition, inboxFolderPosition, targetElement);

        assertThat(ownNumber).isEqualTo(TEST_OWN_NUMBER);
    }

    /**
     * Preconditions:
     * - SENT number of messages is equal to {@code LIST_START_OFFSET_UPPER_LIMIT}.
     * - INBOX number of messages is equal to {@code LIST_START_OFFSET_UPPER_LIMIT}.
     *     - SMS with own number as recipient is the last message in INBOX folder.
     *
     * Actions:
     * - Invoke {@link RequestGetMessagesListingForOwnNumber#readResponse} repeatedly until the
     *   own number has been found or folder elements have been exhausted.
     *
     * Outcome:
     * - Own number is found.
     */
    @Test
    public void testFull_FullLast_found() {
        int sentFolderSize = LIST_START_OFFSET_UPPER_LIMIT;
        int inboxFolderSize = LIST_START_OFFSET_UPPER_LIMIT;
        int sentFolderPosition = -1;
        int inboxFolderPosition = inboxFolderSize - 1;
        BluetoothMapMessageListingElement targetElement = mSMSWithOwnNumberAsRecipient;

        String ownNumber = testGetOwnNumberBase(sentFolderSize, inboxFolderSize,
                sentFolderPosition, inboxFolderPosition, targetElement);

        assertThat(ownNumber).isEqualTo(TEST_OWN_NUMBER);
    }

    /**
     * Preconditions:
     * - SENT is empty.
     * - INBOX contains a single message.
     *     - MMS with someone else's number as recipient in INBOX folder (e.g., group MMS case).
     *
     * Actions:
     * - Invoke {@link RequestGetMessagesListingForOwnNumber#readResponse} repeatedly until the
     *   own number has been found or folder elements have been exhausted.
     *
     * Outcome:
     * - No number is found, not even someone else's number, since MMS should be filtered out
     *   of INBOX.
     */
    @Test
    public void testMMSinInbox_null() {
        int sentFolderSize = 0;
        int inboxFolderSize = 1;
        int sentFolderPosition = -1;
        int inboxFolderPosition = 0;
        // {@code mMMSWithOwnNumberAsSender} has someone else's number as recipient; recipient
        // is non-empty (i.e., would not get skipped if not filtered out by msg type).
        BluetoothMapMessageListingElement targetElement = mMMSWithOwnNumberAsSender;

        String ownNumber = testGetOwnNumberBase(sentFolderSize, inboxFolderSize,
                sentFolderPosition, inboxFolderPosition, targetElement);

        assertThat(ownNumber).isNull();
    }
}
