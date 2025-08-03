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
 * limitations under the License
 */

package android.provider.cts;

import android.Manifest;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.database.ContentObserver;
import android.database.Cursor;
import android.net.Uri;
import android.os.UserManager;
import android.provider.BlockedNumberContract;
import android.provider.BlockedNumberContract.BlockedNumbers;
import android.provider.BlockedNumberContract.SystemContract;
import android.telecom.TelecomManager;
import android.telephony.TelephonyManager;
import android.util.Log;

import androidx.test.platform.app.InstrumentationRegistry;

import junit.framework.Assert;

import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * CTS tests for blockednumber provider accessed through {@link BlockedNumberContract}.
 */
// To run the tests in this file w/o running all the cts tests:
// make cts
// cts-tradefed
// run cts -m CtsProviderTestCases --test android.provider.cts.BlockedNumberContractTest
public class BlockedNumberContractTest extends TestCaseThatRunsIfTelephonyIsEnabled {
    private static final String TAG = "BlockedNumberContractTest";
    private static final String TEST_PHONE_NUMBER = "1234567890";
    private ContentResolver mContentResolver;
    private Context mContext;
    private ArrayList<Uri> mAddedUris;
    private boolean mIsSystemUser;

    private static final String[] BLOCKED_NUMBERS_PROJECTION = new String[]{
            BlockedNumbers.COLUMN_ORIGINAL_NUMBER,
            BlockedNumbers.COLUMN_E164_NUMBER};

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mContext = getInstrumentation().getContext();
        mContentResolver = mContext.getContentResolver();
        mAddedUris = new ArrayList<>();
        mIsSystemUser = isSystemUser(mContext);
    }

    @Override
    protected void tearDown() throws Exception {
        for (Uri row : mAddedUris) {
            mContentResolver.delete(row, null, null);
        }
        mAddedUris.clear();
        setDefaultSmsApp(false);

        super.tearDown();
    }

    public void testProviderInteractionsAsRegularApp_fails() {
        if (!mIsSystemUser) {
            Log.i(TAG, "skipping BlockedNumberContractTest");
            return;
        }
        TelephonyManager telephonyManager = mContext.getSystemService(TelephonyManager.class);
        try {
            InstrumentationRegistry.getInstrumentation().getUiAutomation()
                    .adoptShellPermissionIdentity(
                            "android.permission.READ_PRIVILEGED_PHONE_STATE");
            // Don't run this test if we're carrier privileged.
            if (telephonyManager.checkCarrierPrivilegesForPackage(mContext.getPackageName())
                            == TelephonyManager.CARRIER_PRIVILEGE_STATUS_HAS_ACCESS) {
                    return;
            }
        } catch (SecurityException e) {
            fail("TelephonyManager#checkCarrierPrivilegesForPackage requires "
                    + " READ_PRIVILEGED_PHONE_STATE");
        } finally {
            InstrumentationRegistry.getInstrumentation().getUiAutomation()
                    .dropShellPermissionIdentity();
        }

        try {
            mAddedUris.add(mContentResolver.insert(
                    BlockedNumbers.CONTENT_URI, getContentValues("1234567890")));
            fail("Should throw SecurityException");
        } catch (SecurityException expected) {
        }

        try {
            mContentResolver.query(BlockedNumbers.CONTENT_URI, null, null, null, null);
            fail("Should throw SecurityException");
        } catch (SecurityException expected) {
        }

        try {
            mContentResolver.update(
                    BlockedNumbers.CONTENT_URI, getContentValues("123"), null, null);
            fail("Should throw SecurityException");
        } catch (SecurityException expected) {
        }

        try {
            BlockedNumberContract.isBlocked(mContext, "123");
            fail("Should throw SecurityException");
        } catch (SecurityException expected) {
        }

        try {
            BlockedNumberContract.unblock(mContext, "1234567890");
            fail("Should throw SecurityException");
        } catch (SecurityException expected) {
        }

        assertTrue(BlockedNumberContract.canCurrentUserBlockNumbers(mContext));
    }

    public void testGetType() throws Exception {
        assertEquals(BlockedNumbers.CONTENT_TYPE,
                mContentResolver.getType(BlockedNumbers.CONTENT_URI));
        assertEquals(BlockedNumbers.CONTENT_ITEM_TYPE,
                mContentResolver.getType(
                        ContentUris.withAppendedId(BlockedNumbers.CONTENT_URI, 0)));

        assertNull(mContentResolver.getType(BlockedNumberContract.AUTHORITY_URI));
    }

    public void testInsertAndBlockCheck_succeeds() throws Exception {
        if (!mIsSystemUser) {
            Log.i(TAG, "skipping BlockedNumberContractTest");
            return;
        }
        setDefaultSmsApp(true);

        assertTrue(BlockedNumberContract.canCurrentUserBlockNumbers(mContext));

        assertInsertBlockedNumberSucceeds("1234567890", null);
        // Attempting to insert a duplicate replaces the existing entry.
        assertInsertBlockedNumberSucceeds("1234567890", "+812345678901");
        assertInsertBlockedNumberSucceeds("1234567890", null);

        assertTrue(BlockedNumberContract.isBlocked(mContext, "1234567890"));
        assertFalse(BlockedNumberContract.isBlocked(mContext, "2234567890"));

        assertInsertBlockedNumberSucceeds("2345678901", "+12345678901");
        assertTrue(BlockedNumberContract.isBlocked(mContext, "2345678901"));
        assertTrue(BlockedNumberContract.isBlocked(mContext, "+12345678901"));

        assertInsertBlockedNumberSucceeds("1234@abcd.com", null);
        assertTrue(BlockedNumberContract.isBlocked(mContext, "1234@abcd.com"));

        assertInsertBlockedNumberSucceeds("2345@abcd.com", null);
        assertTrue(BlockedNumberContract.isBlocked(mContext, "2345@abcd.com"));

        assertFalse(BlockedNumberContract.isBlocked(mContext, "9999@abcd.com"));
        assertFalse(BlockedNumberContract.isBlocked(mContext, "random string"));

        assertInsertBlockedNumberSucceeds("IMASPAMMER", null);
        assertTrue(BlockedNumberContract.isBlocked(mContext, "IMASPAMMER"));
        assertFalse(BlockedNumberContract.isBlocked(mContext, "ALSOASPAMMER"));
        assertFalse(BlockedNumberContract.isBlocked(mContext, "9999@abcd.com"));
        assertFalse(BlockedNumberContract.isBlocked(mContext, "NOTASPAMMER"));
        assertFalse(BlockedNumberContract.isBlocked(mContext, "InTerCap"));

        assertInsertBlockedNumberSucceeds("ALSOASPAMMER", null);
        assertTrue(BlockedNumberContract.isBlocked(mContext, "IMASPAMMER"));
        assertTrue(BlockedNumberContract.isBlocked(mContext, "ALSOASPAMMER"));
        assertFalse(BlockedNumberContract.isBlocked(mContext, "VMMyGovt"));
        assertFalse(BlockedNumberContract.isBlocked(mContext, "9999@abcd.com"));
        assertFalse(BlockedNumberContract.isBlocked(mContext, "NOTASPAMMER"));
        assertFalse(BlockedNumberContract.isBlocked(mContext, "InTerCap"));

        assertInsertBlockedNumberSucceeds("VMMyGovt", null);
        assertTrue(BlockedNumberContract.isBlocked(mContext, "IMASPAMMER"));
        assertTrue(BlockedNumberContract.isBlocked(mContext, "ALSOASPAMMER"));
        assertTrue(BlockedNumberContract.isBlocked(mContext, "VMMyGovt"));
        assertFalse(BlockedNumberContract.isBlocked(mContext, "9999@abcd.com"));
        assertFalse(BlockedNumberContract.isBlocked(mContext, "NOTASPAMMER"));
        assertFalse(BlockedNumberContract.isBlocked(mContext, "InTerCap"));
    }

    public void testUnblock_succeeds() throws Exception {
        if (!mIsSystemUser) {
            Log.i(TAG, "skipping BlockedNumberContractTest");
            return;
        }
        setDefaultSmsApp(true);

        // Unblocking non-existent blocked number should return 0.
        assertEquals(0, BlockedNumberContract.unblock(mContext, "6501004000"));

        assertInsertBlockedNumberSucceeds("6501004000", null);
        assertEquals(1, BlockedNumberContract.unblock(mContext, "6501004000"));
        assertFalse(BlockedNumberContract.isBlocked(mContext, "(650)1004000"));

        assertInsertBlockedNumberSucceeds("1234@abcd.com", null);
        assertEquals(1, BlockedNumberContract.unblock(mContext, "1234@abcd.com"));
        assertFalse(BlockedNumberContract.isBlocked(mContext, "1234@abcd.com"));

        assertInsertBlockedNumberSucceeds("SpamSource", null);
        assertInsertBlockedNumberSucceeds("SUPERSPAM", null);
        assertEquals(1, BlockedNumberContract.unblock(mContext, "SpamSource"));
        assertFalse(BlockedNumberContract.isBlocked(mContext, "SpamSource"));
        assertTrue(BlockedNumberContract.isBlocked(mContext, "SUPERSPAM"));

        assertEquals(1, BlockedNumberContract.unblock(mContext, "SUPERSPAM"));
        assertFalse(BlockedNumberContract.isBlocked(mContext, "SpamSource"));
        assertFalse(BlockedNumberContract.isBlocked(mContext, "SUPERSPAM"));
    }

    public void testInsert_failsWithInvalidInputs() throws Exception {
        if (!mIsSystemUser) {
            Log.i(TAG, "skipping BlockedNumberContractTest");
            return;
        }
        setDefaultSmsApp(true);

        try {
            ContentValues cv = new ContentValues();
            cv.put(BlockedNumbers.COLUMN_ORIGINAL_NUMBER, "1234");
            mAddedUris.add(mContentResolver.insert(
                    ContentUris.withAppendedId(BlockedNumbers.CONTENT_URI, 1),
                    new ContentValues()));
            fail("Should throw IllegalArgumentException.");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("Unsupported URI"));
        }

        try {
            mAddedUris.add(
                    mContentResolver.insert(BlockedNumbers.CONTENT_URI, new ContentValues()));
            fail("Should throw IllegalArgumentException.");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("Missing a required column"));
        }

        try {
            ContentValues cv = new ContentValues();
            cv.put(BlockedNumbers.COLUMN_E164_NUMBER, "+1234");
            mAddedUris.add(mContentResolver.insert(BlockedNumbers.CONTENT_URI, cv));
            fail("Should throw IllegalArgumentException.");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("Missing a required column"));
        }

        try {
            ContentValues cv = new ContentValues();
            cv.put(BlockedNumbers.COLUMN_ID, "1");
            mAddedUris.add(mContentResolver.insert(BlockedNumbers.CONTENT_URI, cv));
            fail("Should throw IllegalArgumentException.");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("must not be specified"));
        }
    }

    public void testUpdate_isUnsupported() throws  Exception {
        if (!mIsSystemUser) {
            Log.i(TAG, "skipping BlockedNumberContractTest");
            return;
        }
        setDefaultSmsApp(true);
        try {
            mContentResolver.update(
                    BlockedNumbers.CONTENT_URI, getContentValues("123"), null, null);
            fail("Should throw UnsupportedOperationException");
        } catch (UnsupportedOperationException expected) {
        }
    }

    public void testIsBlocked_returnsFalseForNullAndEmpty() throws Exception {
        if (!mIsSystemUser) {
            Log.i(TAG, "skipping BlockedNumberContractTest");
            return;
        }
        setDefaultSmsApp(true);
        assertFalse(BlockedNumberContract.isBlocked(mContext, null));
        assertFalse(BlockedNumberContract.isBlocked(mContext, ""));
    }

    public void testDelete() throws Exception {
        if (!mIsSystemUser) {
            Log.i(TAG, "skipping BlockedNumberContractTest");
            return;
        }
        setDefaultSmsApp(true);

        assertInsertBlockedNumberSucceeds("12345", "+112345");
        assertInsertBlockedNumberSucceeds("012345", "+112345");
        assertEquals(2,
                mContentResolver.delete(
                        BlockedNumbers.CONTENT_URI,
                        BlockedNumbers.COLUMN_E164_NUMBER + "= ?",
                        new String[] {"+112345"}));

        assertInsertBlockedNumberSucceeds("12345", "");
        assertEquals(1,
                mContentResolver.delete(
                        BlockedNumbers.CONTENT_URI,
                        BlockedNumbers.COLUMN_ORIGINAL_NUMBER + "= ?",
                        new String[] {"12345"}));

        Uri insertedUri = assertInsertBlockedNumberSucceeds("12345", "");
        assertEquals(1,
                mContentResolver.delete(
                        BlockedNumbers.CONTENT_URI,
                        BlockedNumbers.COLUMN_ID + "= ?",
                        new String[] {Long.toString(ContentUris.parseId(insertedUri))}));

        insertedUri = assertInsertBlockedNumberSucceeds("12345", "");
        assertEquals(1,
                mContentResolver.delete(
                        ContentUris.withAppendedId(
                                BlockedNumbers.CONTENT_URI, ContentUris.parseId(insertedUri)),
                        null, null));

        insertedUri = assertInsertBlockedNumberSucceeds("12345", "");
        assertEquals(1, mContentResolver.delete(insertedUri, null, null));

        assertEquals(0,
                mContentResolver.delete(
                        BlockedNumbers.CONTENT_URI,
                        BlockedNumbers.COLUMN_ORIGINAL_NUMBER + "= ?",
                        new String[] {"12345"}));
    }

    public void testDelete_failsOnInvalidInputs() throws Exception {
        if (!mIsSystemUser) {
            Log.i(TAG, "skipping BlockedNumberContractTest");
            return;
        }
        setDefaultSmsApp(true);

        try {
            mContentResolver.delete(Uri.parse("foobar"), null, null);
            fail("Should throw IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
        }

        Uri insertedUri = assertInsertBlockedNumberSucceeds("12345", "");
        try {
            mContentResolver.delete(
                    insertedUri,
                    BlockedNumbers.COLUMN_E164_NUMBER + "= ?",
                    new String[] {"+112345"});
            fail("Should throw IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("selection must be null"));
        }
    }

    public void testProviderNotifiesChangesUsingContentObserver() throws Exception {
        if (!mIsSystemUser) {
            Log.i(TAG, "skipping BlockedNumberContractTest");
            return;
        }
        setDefaultSmsApp(true);

        Cursor cursor = mContentResolver.query(BlockedNumbers.CONTENT_URI, null, null, null, null);

        final CountDownLatch latch = new CountDownLatch(2);
        ContentObserver contentObserver = new ContentObserver(null) {
            @Override
            public void onChange(boolean selfChange) {
                Assert.assertFalse(selfChange);
                latch.notify();
            }
        };
        cursor.registerContentObserver(contentObserver);

        try {
            Uri uri = assertInsertBlockedNumberSucceeds("12345", "");
            mContentResolver.delete(uri, null, null);
            latch.await(10, TimeUnit.SECONDS);
        } catch (Exception e) {
            fail(e.toString());
        } finally {
            cursor.unregisterContentObserver(contentObserver);
        }
    }

    public void testAccessingNonExistentMethod_fails() throws Exception {
        if (!mIsSystemUser) {
            Log.i(TAG, "skipping BlockedNumberContractTest");
            return;
        }
        setDefaultSmsApp(true);

        try {
            mContext.getContentResolver()
                    .call(BlockedNumberContract.AUTHORITY_URI, "nonExistentMethod", "1234", null);
            fail("Should throw IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("Unsupported method"));
        }
    }

    public void testBlockSuppression() throws Exception {
        if (!mIsSystemUser) {
            Log.i(TAG, "skipping BlockedNumberContractTest");
            return;
        }

        try {
            setDefaultSmsApp(true);
            InstrumentationRegistry.getInstrumentation().getUiAutomation()
                    .adoptShellPermissionIdentity(
                            Manifest.permission.WRITE_BLOCKED_NUMBERS,
                            Manifest.permission.READ_BLOCKED_NUMBERS);

            // Enable enhanced block setting.
            BlockedNumbers.setBlockedNumberSetting(mContext,
                    SystemContract.ENHANCED_SETTING_KEY_BLOCK_UNREGISTERED, true);
            BlockedNumbers.setBlockedNumberSetting(mContext,
                    SystemContract.ENHANCED_SETTING_KEY_BLOCK_PRIVATE, true);
            BlockedNumbers.setBlockedNumberSetting(mContext,
                    SystemContract.ENHANCED_SETTING_KEY_BLOCK_PAYPHONE, true);
            BlockedNumbers.setBlockedNumberSetting(mContext,
                    SystemContract.ENHANCED_SETTING_KEY_BLOCK_UNKNOWN, true);
            BlockedNumbers.setBlockedNumberSetting(mContext,
                    SystemContract.ENHANCED_SETTING_KEY_BLOCK_UNAVAILABLE, true);
            BlockedNumbers.setBlockedNumberSetting(mContext,
                    SystemContract.ENHANCED_SETTING_KEY_SHOW_EMERGENCY_CALL_NOTIFICATION, true);

            // Notify emergency contact and verify that blocks are suppressed.
            BlockedNumbers.notifyEmergencyContact(mContext);
            assertShouldSystemBlock(false, TEST_PHONE_NUMBER,
                    TelecomManager.PRESENTATION_ALLOWED, false);
            assertShouldSystemBlock(false, TEST_PHONE_NUMBER,
                    TelecomManager.PRESENTATION_RESTRICTED, false);
            assertShouldSystemBlock(false, TEST_PHONE_NUMBER,
                    TelecomManager.PRESENTATION_PAYPHONE, false);
            assertShouldSystemBlock(false, TEST_PHONE_NUMBER,
                    TelecomManager.PRESENTATION_UNKNOWN, false);
            assertShouldSystemBlock(false, TEST_PHONE_NUMBER,
                    TelecomManager.PRESENTATION_UNAVAILABLE, false);
            // Verify block suppression.
            BlockedNumbers.BlockSuppressionStatus suppressionStatus =
                    BlockedNumbers.getBlockSuppressionStatus(mContext);
            assertTrue(suppressionStatus.getIsSuppressed());
            // Verify that emergency call notification is shown.
            assertTrue(BlockedNumbers.shouldShowEmergencyCallNotification(mContext));
            BlockedNumbers.setBlockedNumberSetting(mContext,
                    SystemContract.ENHANCED_SETTING_KEY_SHOW_EMERGENCY_CALL_NOTIFICATION, false);
            assertFalse(BlockedNumbers.shouldShowEmergencyCallNotification(mContext));

            // End block suppression and verify blocks are no longer suppressed.
            SystemContract.endBlockSuppression(mContext);
            assertShouldSystemBlock(true, TEST_PHONE_NUMBER,
                    TelecomManager.PRESENTATION_ALLOWED, false);
            assertShouldSystemBlock(true, TEST_PHONE_NUMBER,
                    TelecomManager.PRESENTATION_RESTRICTED, false);
            assertShouldSystemBlock(true, TEST_PHONE_NUMBER,
                    TelecomManager.PRESENTATION_PAYPHONE, false);
            assertShouldSystemBlock(true, TEST_PHONE_NUMBER,
                    TelecomManager.PRESENTATION_UNKNOWN, false);
            // Verify that block suppression is not enabled.
            suppressionStatus = BlockedNumbers.getBlockSuppressionStatus(mContext);
            assertFalse(suppressionStatus.getIsSuppressed());
            assertEquals(0, suppressionStatus.getUntilTimestampMillis());
            // Verify that emergency call notification is not shown.
            assertFalse(BlockedNumbers.shouldShowEmergencyCallNotification(mContext));
        }  finally {
            InstrumentationRegistry.getInstrumentation().getUiAutomation()
                    .dropShellPermissionIdentity();
        }
    }

    public void testEnhancedBlocking() throws Exception {
        if (!mIsSystemUser) {
            Log.i(TAG, "skipping BlockedNumberContractTest");
            return;
        }

        try {
            setDefaultSmsApp(true);
            InstrumentationRegistry.getInstrumentation().getUiAutomation()
                    .adoptShellPermissionIdentity(
                            Manifest.permission.WRITE_BLOCKED_NUMBERS,
                            Manifest.permission.READ_BLOCKED_NUMBERS);

            // Check whether block numbers not in contacts setting works as expected.
            BlockedNumbers.setBlockedNumberSetting(mContext,
                    SystemContract.ENHANCED_SETTING_KEY_BLOCK_UNREGISTERED, true);
            assertShouldSystemBlock(true, TEST_PHONE_NUMBER,
                    TelecomManager.PRESENTATION_ALLOWED, false);
            assertShouldSystemBlock(false, TEST_PHONE_NUMBER,
                    TelecomManager.PRESENTATION_ALLOWED, true);
            assertTrue(BlockedNumbers.getBlockedNumberSetting(
                    mContext, SystemContract.ENHANCED_SETTING_KEY_BLOCK_UNREGISTERED));
            BlockedNumbers.setBlockedNumberSetting(mContext,
                    SystemContract.ENHANCED_SETTING_KEY_BLOCK_UNREGISTERED, false);
            assertShouldSystemBlock(false, TEST_PHONE_NUMBER,
                    TelecomManager.PRESENTATION_ALLOWED, false);
            assertFalse(BlockedNumbers.getBlockedNumberSetting(
                    mContext, SystemContract.ENHANCED_SETTING_KEY_BLOCK_UNREGISTERED));

            // Check whether block private number calls setting works as expected.
            BlockedNumbers.setBlockedNumberSetting(mContext,
                    SystemContract.ENHANCED_SETTING_KEY_BLOCK_PRIVATE, true);
            assertShouldSystemBlock(true, TEST_PHONE_NUMBER,
                    TelecomManager.PRESENTATION_RESTRICTED, false);
            assertTrue(BlockedNumbers.getBlockedNumberSetting(
                    mContext, SystemContract.ENHANCED_SETTING_KEY_BLOCK_PRIVATE));
            BlockedNumbers.setBlockedNumberSetting(mContext,
                    SystemContract.ENHANCED_SETTING_KEY_BLOCK_PRIVATE, false);
            assertShouldSystemBlock(false, TEST_PHONE_NUMBER,
                    TelecomManager.PRESENTATION_RESTRICTED, false);
            assertFalse(BlockedNumbers.getBlockedNumberSetting(
                    mContext, SystemContract.ENHANCED_SETTING_KEY_BLOCK_PRIVATE));

            // Check whether block payphone calls setting works as expected.
            BlockedNumbers.setBlockedNumberSetting(mContext,
                    SystemContract.ENHANCED_SETTING_KEY_BLOCK_PAYPHONE, true);
            assertShouldSystemBlock(true, TEST_PHONE_NUMBER,
                    TelecomManager.PRESENTATION_PAYPHONE, false);
            assertTrue(BlockedNumbers.getBlockedNumberSetting(
                    mContext, SystemContract.ENHANCED_SETTING_KEY_BLOCK_PAYPHONE));
            BlockedNumbers.setBlockedNumberSetting(mContext,
                    SystemContract.ENHANCED_SETTING_KEY_BLOCK_PAYPHONE, false);
            assertShouldSystemBlock(false, TEST_PHONE_NUMBER,
                    TelecomManager.PRESENTATION_PAYPHONE, false);
            assertFalse(BlockedNumbers.getBlockedNumberSetting(
                    mContext, SystemContract.ENHANCED_SETTING_KEY_BLOCK_PAYPHONE));

            // Check whether block unknown calls setting works as expected.
            BlockedNumbers.setBlockedNumberSetting(mContext,
                    SystemContract.ENHANCED_SETTING_KEY_BLOCK_UNKNOWN, true);
            assertShouldSystemBlock(true, TEST_PHONE_NUMBER,
                    TelecomManager.PRESENTATION_UNKNOWN, false);
            assertShouldSystemBlock(true, TEST_PHONE_NUMBER,
                    TelecomManager.PRESENTATION_UNAVAILABLE, false);
            assertTrue(BlockedNumbers.getBlockedNumberSetting(
                    mContext, SystemContract.ENHANCED_SETTING_KEY_BLOCK_UNKNOWN));
            BlockedNumbers.setBlockedNumberSetting(mContext,
                    SystemContract.ENHANCED_SETTING_KEY_BLOCK_UNKNOWN, false);
            assertShouldSystemBlock(false, TEST_PHONE_NUMBER,
                    TelecomManager.PRESENTATION_UNKNOWN, false);
            assertShouldSystemBlock(false, TEST_PHONE_NUMBER,
                    TelecomManager.PRESENTATION_UNAVAILABLE, false);
            assertFalse(BlockedNumbers.getBlockedNumberSetting(
                    mContext, SystemContract.ENHANCED_SETTING_KEY_BLOCK_UNKNOWN));

            // Check whether block unavailable calls setting works as expected.
            BlockedNumbers.setBlockedNumberSetting(mContext,
                    SystemContract.ENHANCED_SETTING_KEY_BLOCK_UNAVAILABLE, true);
            assertShouldSystemBlock(false, TEST_PHONE_NUMBER,
                    TelecomManager.PRESENTATION_UNAVAILABLE, false);
            assertTrue(BlockedNumbers.getBlockedNumberSetting(
                    mContext, SystemContract.ENHANCED_SETTING_KEY_BLOCK_UNAVAILABLE));
            BlockedNumbers.setBlockedNumberSetting(mContext,
                    SystemContract.ENHANCED_SETTING_KEY_BLOCK_UNAVAILABLE, false);
            assertShouldSystemBlock(false, TEST_PHONE_NUMBER,
                    TelecomManager.PRESENTATION_UNAVAILABLE, false);
            assertFalse(BlockedNumbers.getBlockedNumberSetting(
                    mContext, SystemContract.ENHANCED_SETTING_KEY_BLOCK_UNAVAILABLE));
        } finally {
            InstrumentationRegistry.getInstrumentation().getUiAutomation()
                    .dropShellPermissionIdentity();
        }
    }

    private void assertShouldSystemBlock(boolean expected, String phoneNumber,
            int presentationNumber, boolean isNumberInContacts) {
        assertEquals(expected, BlockedNumbers.shouldSystemBlockNumber(mContext, phoneNumber,
                presentationNumber, isNumberInContacts)
                != BlockedNumberContract.STATUS_NOT_BLOCKED);
    }

    private Uri assertInsertBlockedNumberSucceeds(
            String originalNumber, String e164Number) {
        ContentValues cv = new ContentValues();
        cv.put(BlockedNumbers.COLUMN_ORIGINAL_NUMBER, originalNumber);
        if (e164Number != null) {
            cv.put(BlockedNumbers.COLUMN_E164_NUMBER, e164Number);
        }
        Uri insertedUri = mContentResolver.insert(BlockedNumbers.CONTENT_URI, cv);
        mAddedUris.add(insertedUri);

        Cursor cursor = mContentResolver.query(
                BlockedNumbers.CONTENT_URI,
                BLOCKED_NUMBERS_PROJECTION,
                BlockedNumbers.COLUMN_ORIGINAL_NUMBER + "= ?",
                new String[]{originalNumber},
                null);
        assertTrue(cursor.moveToFirst());

        assertEquals(originalNumber, cursor.getString(0));
        if (e164Number != null) {
            assertEquals(e164Number, cursor.getString(1));
        }
        cursor.close();
        return insertedUri;
    }

    private ContentValues getContentValues(String originalNumber) {
        ContentValues cv = new ContentValues();
        cv.put(BlockedNumbers.COLUMN_ORIGINAL_NUMBER, originalNumber);
        return cv;
    }

    private void setDefaultSmsApp(boolean setToSmsApp) throws Exception {
        ProviderTestUtils.setDefaultSmsApp(
                setToSmsApp, mContext.getPackageName(), getInstrumentation().getUiAutomation());
    }

    private static boolean isSystemUser(Context context) {
        return context.getSystemService(UserManager.class).isSystemUser();
    }
}
