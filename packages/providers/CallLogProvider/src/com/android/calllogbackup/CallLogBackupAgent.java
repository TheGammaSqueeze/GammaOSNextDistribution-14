/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.calllogbackup;

import static android.provider.CallLog.Calls.MISSED_REASON_NOT_MISSED;

import android.app.backup.BackupAgent;
import android.app.backup.BackupDataInput;
import android.app.backup.BackupDataOutput;
import android.app.backup.BackupManager;
import android.app.backup.BackupRestoreEventLogger;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.database.Cursor;
import android.os.ParcelFileDescriptor;
import android.provider.CallLog;
import android.provider.CallLog.Calls;
import android.telecom.PhoneAccountHandle;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.util.Log;

import com.android.internal.annotations.VisibleForTesting;

import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInput;
import java.io.DataInputStream;
import java.io.DataOutput;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;

/**
 * Call log backup agent.
 */
public class CallLogBackupAgent extends BackupAgent {

    @VisibleForTesting
    static class CallLogBackupState {
        int version;
        SortedSet<Integer> callIds;
    }

    @VisibleForTesting
    static class Call {
        int id;
        long date;
        long duration;
        String number;
        String postDialDigits = "";
        String viaNumber = "";
        int type;
        int numberPresentation;
        String accountComponentName;
        String accountId;
        String accountAddress;
        Long dataUsage;
        int features;
        int addForAllUsers = 1;
        int callBlockReason = Calls.BLOCK_REASON_NOT_BLOCKED;
        String callScreeningAppName = null;
        String callScreeningComponentName = null;
        long missedReason = MISSED_REASON_NOT_MISSED;
        int isPhoneAccountMigrationPending;
        int isBusinessCall;
        String assertedDisplayName = "";

        @Override
        public String toString() {
            if (isDebug()) {
                return  "[" + id + ", account: [" + accountComponentName + " : " + accountId +
                    "]," + number + ", " + date + "]";
            } else {
                return "[" + id + "]";
            }
        }
    }

    static class OEMData {
        String namespace;
        byte[] bytes;

        public OEMData(String namespace, byte[] bytes) {
            this.namespace = namespace;
            this.bytes = bytes == null ? ZERO_BYTE_ARRAY : bytes;
        }
    }

    private static final String TAG = "CallLogBackupAgent";

    /** Data types and errors used when reporting B&R success rate and errors.  */
    @BackupRestoreEventLogger.BackupRestoreDataType
    @VisibleForTesting
    static final String CALLLOGS = "telecom_call_logs";

    @BackupRestoreEventLogger.BackupRestoreError
    static final String ERROR_UNEXPECTED_KEY = "unexpected_key";
    @BackupRestoreEventLogger.BackupRestoreError
    static final String ERROR_END_OEM_MARKER_NOT_FOUND = "end_oem_marker_not_found";
    @BackupRestoreEventLogger.BackupRestoreError
    static final String ERROR_READING_CALL_DATA = "error_reading_call_data";
    @BackupRestoreEventLogger.BackupRestoreError
    static final String ERROR_BACKUP_CALL_FAILED = "backup_call_failed";

    private BackupRestoreEventLogger mLogger;

    /** Current version of CallLogBackup. Used to track the backup format. */
    @VisibleForTesting
    static final int VERSION = 1010;
    /** Version indicating that there exists no previous backup entry. */
    @VisibleForTesting
    static final int VERSION_NO_PREVIOUS_STATE = 0;

    static final String NO_OEM_NAMESPACE = "no-oem-namespace";

    static final byte[] ZERO_BYTE_ARRAY = new byte[0];

    static final int END_OEM_DATA_MARKER = 0x60061E;

    static final String TELEPHONY_PHONE_ACCOUNT_HANDLE_COMPONENT_NAME =
            "com.android.phone/com.android.services.telephony.TelephonyConnectionService";

    @VisibleForTesting
    protected Map<Integer, String> mSubscriptionInfoMap;

    private static final String[] CALL_LOG_PROJECTION = new String[] {
        CallLog.Calls._ID,
        CallLog.Calls.DATE,
        CallLog.Calls.DURATION,
        CallLog.Calls.NUMBER,
        CallLog.Calls.POST_DIAL_DIGITS,
        CallLog.Calls.VIA_NUMBER,
        CallLog.Calls.TYPE,
        CallLog.Calls.COUNTRY_ISO,
        CallLog.Calls.GEOCODED_LOCATION,
        CallLog.Calls.NUMBER_PRESENTATION,
        CallLog.Calls.PHONE_ACCOUNT_COMPONENT_NAME,
        CallLog.Calls.PHONE_ACCOUNT_ID,
        CallLog.Calls.PHONE_ACCOUNT_ADDRESS,
        CallLog.Calls.DATA_USAGE,
        CallLog.Calls.FEATURES,
        CallLog.Calls.ADD_FOR_ALL_USERS,
        CallLog.Calls.BLOCK_REASON,
        CallLog.Calls.CALL_SCREENING_APP_NAME,
        CallLog.Calls.CALL_SCREENING_COMPONENT_NAME,
        CallLog.Calls.MISSED_REASON,
        CallLog.Calls.IS_PHONE_ACCOUNT_MIGRATION_PENDING,
        CallLog.Calls.IS_BUSINESS_CALL,
        CallLog.Calls.ASSERTED_DISPLAY_NAME
    };

    /**
     * BackupRestoreEventLogger Dependencies for testing.
     */
    @VisibleForTesting
    public interface BackupRestoreEventLoggerProxy {
        void logItemsBackedUp(String dataType, int count);
        void logItemsBackupFailed(String dataType, int count, String error);
        void logItemsRestored(String dataType, int count);
        void logItemsRestoreFailed(String dataType, int count, String error);
    }

    private BackupRestoreEventLoggerProxy mBackupRestoreEventLoggerProxy =
            new BackupRestoreEventLoggerProxy() {
        @Override
        public void logItemsBackedUp(String dataType, int count) {
            mLogger.logItemsBackedUp(dataType, count);
        }

        @Override
        public void logItemsBackupFailed(String dataType, int count, String error) {
            mLogger.logItemsBackupFailed(dataType, count, error);
        }

        @Override
        public void logItemsRestored(String dataType, int count) {
            mLogger.logItemsRestored(dataType, count);
        }

        @Override
        public void logItemsRestoreFailed(String dataType, int count, String error) {
            mLogger.logItemsRestoreFailed(dataType, count, error);
        }
    };

    /**
     * Overrides BackupRestoreEventLogger dependencies for testing.
     */
    @VisibleForTesting
    public void setBackupRestoreEventLoggerProxy(BackupRestoreEventLoggerProxy proxy) {
        mBackupRestoreEventLoggerProxy = proxy;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "onCreate");
        BackupManager backupManager = new BackupManager(getApplicationContext());
        mLogger = backupManager.getBackupRestoreEventLogger(/* backupAgent */ this);
    }

    /** ${inheritDoc} */
    @Override
    public void onBackup(ParcelFileDescriptor oldStateDescriptor, BackupDataOutput data,
            ParcelFileDescriptor newStateDescriptor) throws IOException {
        // Get the list of the previous calls IDs which were backed up.
        DataInputStream dataInput = new DataInputStream(
                new FileInputStream(oldStateDescriptor.getFileDescriptor()));
        final CallLogBackupState state;
        try {
            state = readState(dataInput);
        } finally {
            dataInput.close();
        }

        SubscriptionManager subscriptionManager = getBaseContext().getSystemService(
                SubscriptionManager.class);
        if (subscriptionManager != null) {
            mSubscriptionInfoMap = new HashMap<>();
            // Use getAllSubscirptionInfoList() to get the mapping between iccId and subId
            // from the subscription database
            List<SubscriptionInfo> subscriptionInfos = subscriptionManager
                    .getAllSubscriptionInfoList();
            for (SubscriptionInfo subscriptionInfo : subscriptionInfos) {
                mSubscriptionInfoMap.put(
                        subscriptionInfo.getSubscriptionId(), subscriptionInfo.getIccId());
            }
        }

        // Run the actual backup of data
        runBackup(state, data, getAllCallLogEntries());

        // Rewrite the backup state.
        DataOutputStream dataOutput = new DataOutputStream(new BufferedOutputStream(
                new FileOutputStream(newStateDescriptor.getFileDescriptor())));
        try {
            writeState(dataOutput, state);
        } finally {
            dataOutput.close();
        }
    }

    /**
     * Restores a call log backup given provided backup data.
     * @param data the call log backup data; must be in a format written by
     * {@link #onBackup(ParcelFileDescriptor, BackupDataOutput, ParcelFileDescriptor)}.
     *
     * @param appVersionCode The OS version of the data to restore; not used here.
     * @param newState See parent class; not used here.
     * @throws IOException Not thrown by the call log backup agent, but required by the underlying
     * interface -- throwing IOException will cause the existing call log data to be cleared.
     */
    @Override
    public void onRestore(BackupDataInput data, int appVersionCode, ParcelFileDescriptor newState)
            throws IOException {

        if (isDebug()) {
            Log.d(TAG, "Performing Restore");
        }

        while (data.readNextHeader()) {
            Call call = readCallFromData(data);
            if (call != null && call.type != Calls.VOICEMAIL_TYPE) {
                writeCallToProvider(call);
                mBackupRestoreEventLoggerProxy.logItemsRestored(CALLLOGS, /* count */ 1);
                if (isDebug()) {
                    Log.d(TAG, "Restored call: " + call);
                }
            }
        }
    }

    @VisibleForTesting
    void runBackup(CallLogBackupState state, BackupDataOutput data, Iterable<Call> calls) {
        SortedSet<Integer> callsToRemove = new TreeSet<>(state.callIds);

        // Loop through all the call log entries to identify:
        // (1) new calls
        // (2) calls which have been deleted.
        for (Call call : calls) {
            if (!state.callIds.contains(call.id)) {

                if (isDebug()) {
                    Log.d(TAG, "Adding call to backup: " + call);
                }

                // This call new (not in our list from the last backup), lets back it up.
                addCallToBackup(data, call);
                state.callIds.add(call.id);
            } else {
                // This call still exists in the current call log so delete it from the
                // "callsToRemove" set since we want to keep it.
                callsToRemove.remove(call.id);
                mBackupRestoreEventLoggerProxy.logItemsBackedUp(CALLLOGS, /* count */ 1);
            }
        }

        // Remove calls which no longer exist in the set.
        for (Integer i : callsToRemove) {
            if (isDebug()) {
                Log.d(TAG, "Removing call from backup: " + i);
            }

            removeCallFromBackup(data, i);
            state.callIds.remove(i);
        }
    }

    @VisibleForTesting
    Iterable<Call> getAllCallLogEntries() {
        List<Call> calls = new LinkedList<>();

        // We use the API here instead of querying ContactsDatabaseHelper directly because
        // CallLogProvider has special locks in place for sychronizing when to read.  Using the APIs
        // gives us that for free.
        ContentResolver resolver = getContentResolver();
        Cursor cursor = resolver.query(
                CallLog.Calls.CONTENT_URI, CALL_LOG_PROJECTION, null, null, null);
        if (cursor != null) {
            try {
                while (cursor.moveToNext()) {
                    Call call = readCallFromCursor(cursor);
                    if (call != null && call.type != Calls.VOICEMAIL_TYPE) {
                        calls.add(call);
                    }
                }
            } finally {
                cursor.close();
            }
        }

        return calls;
    }

    private void writeCallToProvider(Call call) {
        Long dataUsage = call.dataUsage == 0 ? null : call.dataUsage;

        PhoneAccountHandle handle = null;
        if (call.accountComponentName != null && call.accountId != null) {
            handle = new PhoneAccountHandle(
                    ComponentName.unflattenFromString(call.accountComponentName), call.accountId);
        }
        boolean addForAllUsers = call.addForAllUsers == 1;

        // We backup the calllog in the user running this backup agent, so write calls to this user.
        CallLog.AddCallParams.AddCallParametersBuilder builder =
                new CallLog.AddCallParams.AddCallParametersBuilder();
        builder.setCallerInfo(null);
        builder.setNumber(call.number);
        builder.setPostDialDigits(call.postDialDigits);
        builder.setViaNumber(call.viaNumber);
        builder.setPresentation(call.numberPresentation);
        builder.setCallType(call.type);
        builder.setFeatures(call.features);
        builder.setAccountHandle(handle);
        builder.setStart(call.date);
        builder.setDuration((int) call.duration);
        builder.setDataUsage(dataUsage == null ? Long.MIN_VALUE : dataUsage);
        builder.setAddForAllUsers(addForAllUsers);
        builder.setUserToBeInsertedTo(null);
        builder.setIsRead(true);
        builder.setCallBlockReason(call.callBlockReason);
        builder.setCallScreeningAppName(call.callScreeningAppName);
        builder.setCallScreeningComponentName(call.callScreeningComponentName);
        builder.setMissedReason(call.missedReason);
        builder.setIsPhoneAccountMigrationPending(call.isPhoneAccountMigrationPending);
        builder.setIsBusinessCall(call.isBusinessCall == 1);
        builder.setAssertedDisplayName(call.assertedDisplayName);

        Calls.addCall(this, builder.build());
    }

    @VisibleForTesting
    CallLogBackupState readState(DataInput dataInput) throws IOException {
        CallLogBackupState state = new CallLogBackupState();
        state.callIds = new TreeSet<>();

        try {
            // Read the version.
            state.version = dataInput.readInt();

            if (state.version >= 1) {
                // Read the size.
                int size = dataInput.readInt();

                // Read all of the call IDs.
                for (int i = 0; i < size; i++) {
                    state.callIds.add(dataInput.readInt());
                }
            }
        } catch (EOFException e) {
            state.version = VERSION_NO_PREVIOUS_STATE;
        }

        return state;
    }

    @VisibleForTesting
    void writeState(DataOutput dataOutput, CallLogBackupState state)
            throws IOException {
        // Write version first of all
        dataOutput.writeInt(VERSION);

        // [Version 1]
        // size + callIds
        dataOutput.writeInt(state.callIds.size());
        for (Integer i : state.callIds) {
            dataOutput.writeInt(i);
        }
    }

    @VisibleForTesting
    Call readCallFromData(BackupDataInput data) {
        final int callId;
        try {
            callId = Integer.parseInt(data.getKey());
        } catch (NumberFormatException e) {
            mBackupRestoreEventLoggerProxy.logItemsRestoreFailed(
                    CALLLOGS, /* count */ 1, ERROR_UNEXPECTED_KEY);
            Log.e(TAG, "Unexpected key found in restore: " + data.getKey());
            return null;
        }

        try {
            byte [] byteArray = new byte[data.getDataSize()];
            data.readEntityData(byteArray, 0, byteArray.length);
            DataInputStream dataInput = new DataInputStream(new ByteArrayInputStream(byteArray));

            Call call = new Call();
            call.id = callId;

            int version = dataInput.readInt();

            // Don't allow downgrades when restoring except when the version is 1010; that version
            // adds some rather inconsequential columns to the call log database and it is generally
            // preferable to allow the restore knowing that those new columns will be skipped in the
            // restore.
            if (version > VERSION && version != 1010) {
                // If somehow we got a backed up row that is newer than the supported file format
                // we know of, we will log an error and return null to represent an invalid item.
                String errorMessage = "Backup version " + version + " is newer than the current "
                        + "supported version, " + VERSION;
                Log.w(TAG, errorMessage);
                mBackupRestoreEventLoggerProxy.logItemsRestoreFailed(CALLLOGS, 1,
                        errorMessage);
                return null;
            }

            if (version >= 1) {
                call.date = dataInput.readLong();
                call.duration = dataInput.readLong();
                call.number = readString(dataInput);
                call.type = dataInput.readInt();
                call.numberPresentation = dataInput.readInt();
                call.accountComponentName = readString(dataInput);
                call.accountId = readString(dataInput);
                call.accountAddress = readString(dataInput);
                call.dataUsage = dataInput.readLong();
                call.features = dataInput.readInt();
            }

            if (version >= 1002) {
                String namespace = dataInput.readUTF();
                int length = dataInput.readInt();
                byte[] buffer = new byte[length];
                dataInput.read(buffer);
                readOEMDataForCall(call, new OEMData(namespace, buffer));

                int marker = dataInput.readInt();
                if (marker != END_OEM_DATA_MARKER) {
                    mBackupRestoreEventLoggerProxy.logItemsRestoreFailed(CALLLOGS, /* count */ 1,
                            ERROR_END_OEM_MARKER_NOT_FOUND);
                    Log.e(TAG, "Did not find END-OEM marker for call " + call.id);
                    // The marker does not match the expected value, ignore this call completely.
                    return null;
                }
            }

            if (version >= 1003) {
                call.addForAllUsers = dataInput.readInt();
            }

            if (version >= 1004) {
                call.postDialDigits = readString(dataInput);
            }

            if(version >= 1005) {
                call.viaNumber = readString(dataInput);
            }

            if(version >= 1006) {
                call.callBlockReason = dataInput.readInt();
                call.callScreeningAppName = readString(dataInput);
                call.callScreeningComponentName = readString(dataInput);
            }
            if(version >= 1007) {
                // Version 1007 had call id columns early in the Q release; they were pulled so we
                // will just read the values out here if they exist in a backup and ignore them.
                readString(dataInput);
                readString(dataInput);
                readString(dataInput);
                readString(dataInput);
                readString(dataInput);
                readInteger(dataInput);
            }
            if (version >= 1008) {
                call.missedReason = dataInput.readLong();
            }
            if (version >= 1009) {
                call.isPhoneAccountMigrationPending = dataInput.readInt();
            }
            if (version >= 1010) {
                call.isBusinessCall = dataInput.readInt();
                call.assertedDisplayName = readString(dataInput);
            }
            /**
             * In >=T Android, Telephony PhoneAccountHandle must use SubId as the ID (the unique
             * identifier). Any version of Telephony call logs that are restored in >=T Android
             * should set pending migration status as true and migrate to the subId later because
             * different devices have different mappings between SubId and IccId.
             *
             * In <T Android, call log PhoneAccountHandle ID uses IccId, and backup with IccId;
             * in >=T Android, call log PhoneAccountHandle ID uses SubId, and IccId is decided to
             * use for backup for the reason mentioned above. Every time a call log is restored,
             * the on-devie sub Id can be determined based on its IccId. The pending migration
             * from IccId to SubId will be complete after the PhoneAccountHandle is registrated by
             * Telecom and before CallLogProvider unhides it.
             */
            if (call.accountComponentName != null && call.accountComponentName.equals(
                    TELEPHONY_PHONE_ACCOUNT_HANDLE_COMPONENT_NAME)) {
                call.isPhoneAccountMigrationPending = 1;
            }
            return call;
        } catch (IOException e) {
            mBackupRestoreEventLoggerProxy.logItemsRestoreFailed(
                    CALLLOGS, /* count */ 1, ERROR_READING_CALL_DATA);
            Log.e(TAG, "Error reading call data for " + callId, e);
            return null;
        }
    }

    /**
     * We need to use IccId for the PHONE_ACCOUNT_ID and set it as pending in backup when:
     * 1) the phone account component name is telephony; AND
     * 2) IS_PHONE_ACCOUNT_MIGRATION_PENDING status is not 1 ("1" means the ID is already IccId).
     */
    private boolean shouldConvertSubIdToIccIdForBackup(
            String accountComponentName, int isPhoneAccountMigrationPending) {
        if (mSubscriptionInfoMap == null) {
            Log.e(TAG, "Subscription database is not available.");
            return false;
        }
        if (accountComponentName != null
                && accountComponentName.equals(TELEPHONY_PHONE_ACCOUNT_HANDLE_COMPONENT_NAME)
                && isPhoneAccountMigrationPending != 1) {
            return true;
        }
        return false;
    }

    @VisibleForTesting
    Call readCallFromCursor(Cursor cursor) {
        Call call = new Call();
        call.id = cursor.getInt(cursor.getColumnIndex(CallLog.Calls._ID));
        call.date = cursor.getLong(cursor.getColumnIndex(CallLog.Calls.DATE));
        call.duration = cursor.getLong(cursor.getColumnIndex(CallLog.Calls.DURATION));
        call.number = cursor.getString(cursor.getColumnIndex(CallLog.Calls.NUMBER));
        call.postDialDigits = cursor.getString(
                cursor.getColumnIndex(CallLog.Calls.POST_DIAL_DIGITS));
        call.viaNumber = cursor.getString(cursor.getColumnIndex(CallLog.Calls.VIA_NUMBER));
        call.type = cursor.getInt(cursor.getColumnIndex(CallLog.Calls.TYPE));
        call.numberPresentation =
                cursor.getInt(cursor.getColumnIndex(CallLog.Calls.NUMBER_PRESENTATION));
        call.accountComponentName =
                cursor.getString(cursor.getColumnIndex(CallLog.Calls.PHONE_ACCOUNT_COMPONENT_NAME));
        call.accountId =
                cursor.getString(cursor.getColumnIndex(CallLog.Calls.PHONE_ACCOUNT_ID));
        call.accountAddress =
                cursor.getString(cursor.getColumnIndex(CallLog.Calls.PHONE_ACCOUNT_ADDRESS));
        call.dataUsage = cursor.getLong(cursor.getColumnIndex(CallLog.Calls.DATA_USAGE));
        call.features = cursor.getInt(cursor.getColumnIndex(CallLog.Calls.FEATURES));
        call.addForAllUsers = cursor.getInt(cursor.getColumnIndex(Calls.ADD_FOR_ALL_USERS));
        call.callBlockReason = cursor.getInt(cursor.getColumnIndex(CallLog.Calls.BLOCK_REASON));
        call.callScreeningAppName = cursor
            .getString(cursor.getColumnIndex(CallLog.Calls.CALL_SCREENING_APP_NAME));
        call.callScreeningComponentName = cursor
            .getString(cursor.getColumnIndex(CallLog.Calls.CALL_SCREENING_COMPONENT_NAME));
        call.missedReason = cursor
            .getInt(cursor.getColumnIndex(CallLog.Calls.MISSED_REASON));
        call.isPhoneAccountMigrationPending = cursor.getInt(
                cursor.getColumnIndex(CallLog.Calls.IS_PHONE_ACCOUNT_MIGRATION_PENDING));
        call.isBusinessCall = cursor.getInt(cursor.getColumnIndex(CallLog.Calls.IS_BUSINESS_CALL));
        call.assertedDisplayName =
                cursor.getString(cursor.getColumnIndex(CallLog.Calls.ASSERTED_DISPLAY_NAME));
        /*
         * Starting Android T, the ID of Telephony PhoneAccountHandle need to migrate from IccId
         * to SubId. Because the mapping between IccId and SubId in different devices is different,
         * the Backup need to use IccId for the ID and set it as pending migration, and when the
         * ID is restored, ID need migrated to SubId after the corresponding PhoneAccountHandle
         * is registrated by Telecom and before CallLogProvider unhides them.
         */
        if (shouldConvertSubIdToIccIdForBackup(call.accountComponentName,
                call.isPhoneAccountMigrationPending)) {
            Log.i(TAG, "Processing PhoneAccountMigration Backup accountId: " + call.accountId);
            String iccId = null;
            try {
                iccId = mSubscriptionInfoMap.get(Integer.parseInt(call.accountId));
            } catch (NullPointerException e) {
                // Ignore, iccId will be null;
            } catch(NumberFormatException e) {
                // Ignore, iccId will be null;
            }

            if (iccId != null) {
                Log.i(TAG, "processing PhoneAccountMigration Found Subid during Backup: "
                        + call.accountId);
                call.accountId = iccId;
                call.isPhoneAccountMigrationPending = 1;
            }
        }
        return call;
    }

    private void addCallToBackup(BackupDataOutput output, Call call) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream data = new DataOutputStream(baos);

        try {
            data.writeInt(VERSION);
            data.writeLong(call.date);
            data.writeLong(call.duration);
            writeString(data, call.number);
            data.writeInt(call.type);
            data.writeInt(call.numberPresentation);
            writeString(data, call.accountComponentName);
            writeString(data, call.accountId);
            writeString(data, call.accountAddress);
            data.writeLong(call.dataUsage == null ? 0 : call.dataUsage);
            data.writeInt(call.features);

            OEMData oemData = getOEMDataForCall(call);
            data.writeUTF(oemData.namespace);
            data.writeInt(oemData.bytes.length);
            data.write(oemData.bytes);
            data.writeInt(END_OEM_DATA_MARKER);

            data.writeInt(call.addForAllUsers);

            writeString(data, call.postDialDigits);

            writeString(data, call.viaNumber);

            data.writeInt(call.callBlockReason);
            writeString(data, call.callScreeningAppName);
            writeString(data, call.callScreeningComponentName);

            // Step 1007 used to write caller ID data; those were pulled.  Keeping that in here
            // to maintain compatibility for backups which had this data.
            writeString(data, "");
            writeString(data, "");
            writeString(data, "");
            writeString(data, "");
            writeString(data, "");
            writeInteger(data, null);

            data.writeLong(call.missedReason);
            data.writeInt(call.isPhoneAccountMigrationPending);

            data.writeInt(call.isBusinessCall);
            writeString(data, call.assertedDisplayName);

            data.flush();

            output.writeEntityHeader(Integer.toString(call.id), baos.size());
            output.writeEntityData(baos.toByteArray(), baos.size());

            mBackupRestoreEventLoggerProxy.logItemsBackedUp(CALLLOGS, /* count */ 1);

            if (isDebug()) {
                Log.d(TAG, "Wrote call to backup: " + call + " with byte array: " + baos);
            }
        } catch (Exception e) {
            mBackupRestoreEventLoggerProxy.logItemsBackupFailed(
                    CALLLOGS, /* count */ 1, ERROR_BACKUP_CALL_FAILED);
            Log.e(TAG, "Failed to backup call: " + call, e);
        }
    }

    /**
     * Allows OEMs to provide proprietary data to backup along with the rest of the call log
     * data. Because there is no way to provide a Backup Transport implementation
     * nor peek into the data format of backup entries without system-level permissions, it is
     * not possible (at the time of this writing) to write CTS tests for this piece of code.
     * It is, therefore, important that if you alter this portion of code that you
     * test backup and restore of call log is working as expected; ideally this would be tested by
     * backing up and restoring between two different Android phone devices running M+.
     */
    private OEMData getOEMDataForCall(Call call) {
        return new OEMData(NO_OEM_NAMESPACE, ZERO_BYTE_ARRAY);

        // OEMs that want to add their own proprietary data to call log backup should replace the
        // code above with their own namespace and add any additional data they need.
        // Versioning and size-prefixing the data should be done here as needed.
        //
        // Example:

        /*
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream data = new DataOutputStream(baos);

        String customData1 = "Generic OEM";
        int customData2 = 42;

        // Write a version for the data
        data.writeInt(OEM_DATA_VERSION);

        // Write the data and flush
        data.writeUTF(customData1);
        data.writeInt(customData2);
        data.flush();

        String oemNamespace = "com.oem.namespace";
        return new OEMData(oemNamespace, baos.toByteArray());
        */
    }

    /**
     * Allows OEMs to read their own proprietary data when doing a call log restore. It is important
     * that the implementation verify the namespace of the data matches their expected value before
     * attempting to read the data or else you may risk reading invalid data.
     *
     * See {@link #getOEMDataForCall} for information concerning proper testing of this code.
     */
    private void readOEMDataForCall(Call call, OEMData oemData) {
        // OEMs that want to read proprietary data from a call log restore should do so here.
        // Before reading from the data, an OEM should verify that the data matches their
        // expected namespace.
        //
        // Example:

        /*
        if ("com.oem.expected.namespace".equals(oemData.namespace)) {
            ByteArrayInputStream bais = new ByteArrayInputStream(oemData.bytes);
            DataInputStream data = new DataInputStream(bais);

            // Check against this version as we read data.
            int version = data.readInt();
            String customData1 = data.readUTF();
            int customData2 = data.readInt();
            // do something with data
        }
        */
    }


    private void writeString(DataOutputStream data, String str) throws IOException {
        if (str == null) {
            data.writeBoolean(false);
        } else {
            data.writeBoolean(true);
            data.writeUTF(str);
        }
    }

    private String readString(DataInputStream data) throws IOException {
        if (data.readBoolean()) {
            return data.readUTF();
        } else {
            return null;
        }
    }

    private void writeInteger(DataOutputStream data, Integer num) throws IOException {
        if (num == null) {
            data.writeBoolean(false);
        } else {
            data.writeBoolean(true);
            data.writeInt(num);
        }
    }

    private Integer readInteger(DataInputStream data) throws IOException {
        if (data.readBoolean()) {
            return data.readInt();
        } else {
            return null;
        }
    }

    private void removeCallFromBackup(BackupDataOutput output, int callId) {
        try {
            output.writeEntityHeader(Integer.toString(callId), -1);
        } catch (IOException e) {
            Log.e(TAG, "Failed to remove call: " + callId, e);
        }
    }

    private static boolean isDebug() {
        return Log.isLoggable(TAG, Log.DEBUG);
    }
}
