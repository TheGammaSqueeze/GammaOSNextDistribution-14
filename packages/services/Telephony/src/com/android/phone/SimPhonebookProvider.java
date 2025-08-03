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

package com.android.phone;

import static com.android.internal.telephony.IccProvider.STR_NEW_NUMBER;
import static com.android.internal.telephony.IccProvider.STR_NEW_TAG;

import android.Manifest;
import android.annotation.TestApi;
import android.content.ContentProvider;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.UriMatcher;
import android.content.pm.PackageManager;
import android.database.ContentObserver;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.os.RemoteException;
import android.provider.SimPhonebookContract;
import android.provider.SimPhonebookContract.ElementaryFiles;
import android.provider.SimPhonebookContract.SimRecords;
import android.telephony.PhoneNumberUtils;
import android.telephony.Rlog;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyFrameworkInitializer;
import android.telephony.TelephonyManager;
import android.util.ArraySet;
import android.util.SparseArray;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.telephony.IIccPhoneBook;
import com.android.internal.telephony.flags.Flags;
import com.android.internal.telephony.uicc.AdnRecord;
import com.android.internal.telephony.uicc.IccConstants;

import com.google.common.base.Joiner;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.util.concurrent.MoreExecutors;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

/**
 * Provider for contact records stored on the SIM card.
 *
 * @see SimPhonebookContract
 */
public class SimPhonebookProvider extends ContentProvider {

    @VisibleForTesting
    static final String[] ELEMENTARY_FILES_ALL_COLUMNS = {
            ElementaryFiles.SLOT_INDEX,
            ElementaryFiles.SUBSCRIPTION_ID,
            ElementaryFiles.EF_TYPE,
            ElementaryFiles.MAX_RECORDS,
            ElementaryFiles.RECORD_COUNT,
            ElementaryFiles.NAME_MAX_LENGTH,
            ElementaryFiles.PHONE_NUMBER_MAX_LENGTH
    };
    @VisibleForTesting
    static final String[] SIM_RECORDS_ALL_COLUMNS = {
            SimRecords.SUBSCRIPTION_ID,
            SimRecords.ELEMENTARY_FILE_TYPE,
            SimRecords.RECORD_NUMBER,
            SimRecords.NAME,
            SimRecords.PHONE_NUMBER
    };
    private static final String TAG = "SimPhonebookProvider";
    private static final Set<String> ELEMENTARY_FILES_COLUMNS_SET =
            ImmutableSet.copyOf(ELEMENTARY_FILES_ALL_COLUMNS);
    private static final Set<String> SIM_RECORDS_COLUMNS_SET =
            ImmutableSet.copyOf(SIM_RECORDS_ALL_COLUMNS);
    private static final Set<String> SIM_RECORDS_WRITABLE_COLUMNS = ImmutableSet.of(
            SimRecords.NAME, SimRecords.PHONE_NUMBER
    );

    private static final int WRITE_TIMEOUT_SECONDS = 30;

    private static final UriMatcher URI_MATCHER = new UriMatcher(UriMatcher.NO_MATCH);

    private static final int ELEMENTARY_FILES = 100;
    private static final int ELEMENTARY_FILES_ITEM = 101;
    private static final int SIM_RECORDS = 200;
    private static final int SIM_RECORDS_ITEM = 201;

    static {
        URI_MATCHER.addURI(SimPhonebookContract.AUTHORITY,
                ElementaryFiles.ELEMENTARY_FILES_PATH_SEGMENT, ELEMENTARY_FILES);
        URI_MATCHER.addURI(
                SimPhonebookContract.AUTHORITY,
                ElementaryFiles.ELEMENTARY_FILES_PATH_SEGMENT + "/"
                        + SimPhonebookContract.SUBSCRIPTION_ID_PATH_SEGMENT + "/#/*",
                ELEMENTARY_FILES_ITEM);
        URI_MATCHER.addURI(SimPhonebookContract.AUTHORITY,
                SimPhonebookContract.SUBSCRIPTION_ID_PATH_SEGMENT + "/#/*", SIM_RECORDS);
        URI_MATCHER.addURI(SimPhonebookContract.AUTHORITY,
                SimPhonebookContract.SUBSCRIPTION_ID_PATH_SEGMENT + "/#/*/#", SIM_RECORDS_ITEM);
    }

    // Only allow 1 write at a time to prevent races; the mutations are based on reads of the
    // existing list of records which means concurrent writes would be problematic.
    private final Lock mWriteLock = new ReentrantLock(true);
    private SubscriptionManager mSubscriptionManager;
    private Supplier<IIccPhoneBook> mIccPhoneBookSupplier;
    private ContentNotifier mContentNotifier;

    static int efIdForEfType(@ElementaryFiles.EfType int efType) {
        switch (efType) {
            case ElementaryFiles.EF_ADN:
                return IccConstants.EF_ADN;
            case ElementaryFiles.EF_FDN:
                return IccConstants.EF_FDN;
            case ElementaryFiles.EF_SDN:
                return IccConstants.EF_SDN;
            default:
                return 0;
        }
    }

    private static void validateProjection(Set<String> allowed, String[] projection) {
        if (projection == null || allowed.containsAll(Arrays.asList(projection))) {
            return;
        }
        Set<String> invalidColumns = new LinkedHashSet<>(Arrays.asList(projection));
        invalidColumns.removeAll(allowed);
        throw new IllegalArgumentException(
                "Unsupported columns: " + Joiner.on(",").join(invalidColumns));
    }

    private static int getRecordSize(int[] recordsSize) {
        return recordsSize[0];
    }

    private static int getRecordCount(int[] recordsSize) {
        return recordsSize[2];
    }

    /** Returns the IccPhoneBook used to load the AdnRecords. */
    private static IIccPhoneBook getIccPhoneBook() {
        return IIccPhoneBook.Stub.asInterface(TelephonyFrameworkInitializer
                .getTelephonyServiceManager().getIccPhoneBookServiceRegisterer().get());
    }

    @Override
    public boolean onCreate() {
        ContentResolver resolver = getContext().getContentResolver();

        SubscriptionManager sm = getContext().getSystemService(SubscriptionManager.class);
        if (sm == null) {
            return false;
        } else if (Flags.workProfileApiSplit()) {
            sm = sm.createForAllUserProfiles();
        }
        return onCreate(sm,
                SimPhonebookProvider::getIccPhoneBook,
                uri -> resolver.notifyChange(uri, null));
    }

    @TestApi
    boolean onCreate(@NonNull SubscriptionManager subscriptionManager,
            Supplier<IIccPhoneBook> iccPhoneBookSupplier, ContentNotifier notifier) {
        mSubscriptionManager = subscriptionManager;
        mIccPhoneBookSupplier = iccPhoneBookSupplier;
        mContentNotifier = notifier;

        mSubscriptionManager.addOnSubscriptionsChangedListener(MoreExecutors.directExecutor(),
                new SubscriptionManager.OnSubscriptionsChangedListener() {
                    boolean mFirstCallback = true;
                    private int[] mNotifiedSubIds = {};

                    @Override
                    public void onSubscriptionsChanged() {
                        if (mFirstCallback) {
                            mFirstCallback = false;
                            return;
                        }
                        int[] activeSubIds = mSubscriptionManager.getActiveSubscriptionIdList();
                        if (!Arrays.equals(mNotifiedSubIds, activeSubIds)) {
                            notifier.notifyChange(SimPhonebookContract.AUTHORITY_URI);
                            mNotifiedSubIds = Arrays.copyOf(activeSubIds, activeSubIds.length);
                        }
                    }
                });
        return true;
    }

    @Nullable
    @Override
    public Bundle call(@NonNull String method, @Nullable String arg, @Nullable Bundle extras) {
        if (SimRecords.GET_ENCODED_NAME_LENGTH_METHOD_NAME.equals(method)) {
            // No permissions checks needed. This isn't leaking any sensitive information since the
            // name we are checking is provided by the caller.
            return callForEncodedNameLength(arg);
        }
        return super.call(method, arg, extras);
    }

    private Bundle callForEncodedNameLength(String name) {
        Bundle result = new Bundle();
        result.putInt(SimRecords.EXTRA_ENCODED_NAME_LENGTH, getEncodedNameLength(name));
        return result;
    }

    private int getEncodedNameLength(String name) {
        if (Strings.isNullOrEmpty(name)) {
            return 0;
        } else {
            byte[] encoded = AdnRecord.encodeAlphaTag(name);
            return encoded.length;
        }
    }

    @Nullable
    @Override
    public Cursor query(@NonNull Uri uri, @Nullable String[] projection, @Nullable Bundle queryArgs,
            @Nullable CancellationSignal cancellationSignal) {
        if (queryArgs != null && (queryArgs.containsKey(ContentResolver.QUERY_ARG_SQL_SELECTION)
                || queryArgs.containsKey(ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS)
                || queryArgs.containsKey(ContentResolver.QUERY_ARG_SQL_LIMIT))) {
            throw new IllegalArgumentException(
                    "A SQL selection was provided but it is not supported by this provider.");
        }
        switch (URI_MATCHER.match(uri)) {
            case ELEMENTARY_FILES:
                return queryElementaryFiles(projection);
            case ELEMENTARY_FILES_ITEM:
                return queryElementaryFilesItem(PhonebookArgs.forElementaryFilesItem(uri),
                        projection);
            case SIM_RECORDS:
                return querySimRecords(PhonebookArgs.forSimRecords(uri, queryArgs), projection);
            case SIM_RECORDS_ITEM:
                return querySimRecordsItem(PhonebookArgs.forSimRecordsItem(uri, queryArgs),
                        projection);
            default:
                throw new IllegalArgumentException("Unsupported Uri " + uri);
        }
    }

    @Nullable
    @Override
    public Cursor query(@NonNull Uri uri, @Nullable String[] projection, @Nullable String selection,
            @Nullable String[] selectionArgs, @Nullable String sortOrder,
            @Nullable CancellationSignal cancellationSignal) {
        throw new UnsupportedOperationException("Only query with Bundle is supported");
    }

    @Nullable
    @Override
    public Cursor query(@NonNull Uri uri, @Nullable String[] projection, @Nullable String selection,
            @Nullable String[] selectionArgs, @Nullable String sortOrder) {
        throw new UnsupportedOperationException("Only query with Bundle is supported");
    }

    private Cursor queryElementaryFiles(String[] projection) {
        validateProjection(ELEMENTARY_FILES_COLUMNS_SET, projection);
        if (projection == null) {
            projection = ELEMENTARY_FILES_ALL_COLUMNS;
        }

        MatrixCursor result = new MatrixCursor(projection);

        List<SubscriptionInfo> activeSubscriptions = getActiveSubscriptionInfoList();
        for (SubscriptionInfo subInfo : activeSubscriptions) {
            try {
                addEfToCursor(result, subInfo, ElementaryFiles.EF_ADN);
                addEfToCursor(result, subInfo, ElementaryFiles.EF_FDN);
                addEfToCursor(result, subInfo, ElementaryFiles.EF_SDN);
            } catch (RemoteException e) {
                // Return an empty cursor. If service to access it is throwing remote
                // exceptions then it's basically the same as not having a SIM.
                return new MatrixCursor(projection, 0);
            }
        }
        return result;
    }

    private Cursor queryElementaryFilesItem(PhonebookArgs args, String[] projection) {
        validateProjection(ELEMENTARY_FILES_COLUMNS_SET, projection);
        if (projection == null) {
            projection = ELEMENTARY_FILES_ALL_COLUMNS;
        }

        MatrixCursor result = new MatrixCursor(projection);
        try {
            SubscriptionInfo info = getActiveSubscriptionInfo(args.subscriptionId);
            if (info != null) {
                addEfToCursor(result, info, args.efType);
            }
        } catch (RemoteException e) {
            // Return an empty cursor. If service to access it is throwing remote
            // exceptions then it's basically the same as not having a SIM.
            return new MatrixCursor(projection, 0);
        }
        return result;
    }

    private void addEfToCursor(MatrixCursor result, SubscriptionInfo subscriptionInfo,
            int efType) throws RemoteException {
        int[] recordsSize = mIccPhoneBookSupplier.get().getAdnRecordsSizeForSubscriber(
                subscriptionInfo.getSubscriptionId(), efIdForEfType(efType));
        addEfToCursor(result, subscriptionInfo, efType, recordsSize);
    }

    private void addEfToCursor(MatrixCursor result, SubscriptionInfo subscriptionInfo,
            int efType, int[] recordsSize) throws RemoteException {
        // If the record count is zero then the SIM doesn't support the elementary file so just
        // omit it.
        if (recordsSize == null || getRecordCount(recordsSize) == 0) {
            return;
        }
        int efid = efIdForEfType(efType);
        // Have to load the existing records to get the size because there may be more than one
        // phonebook set in which case the total capacity is the sum of the capacity of EF_ADN for
        // all the phonebook sets whereas the recordsSize is just the size for a single EF.
        List<AdnRecord> existingRecords = mIccPhoneBookSupplier.get()
                .getAdnRecordsInEfForSubscriber(subscriptionInfo.getSubscriptionId(), efid);
        if (existingRecords == null) {
            existingRecords = ImmutableList.of();
        }
        MatrixCursor.RowBuilder row = result.newRow()
                .add(ElementaryFiles.SLOT_INDEX, subscriptionInfo.getSimSlotIndex())
                .add(ElementaryFiles.SUBSCRIPTION_ID, subscriptionInfo.getSubscriptionId())
                .add(ElementaryFiles.EF_TYPE, efType)
                .add(ElementaryFiles.MAX_RECORDS, existingRecords.size())
                .add(ElementaryFiles.NAME_MAX_LENGTH,
                        AdnRecord.getMaxAlphaTagBytes(getRecordSize(recordsSize)))
                .add(ElementaryFiles.PHONE_NUMBER_MAX_LENGTH,
                        AdnRecord.getMaxPhoneNumberDigits());
        if (result.getColumnIndex(ElementaryFiles.RECORD_COUNT) != -1) {
            int nonEmptyCount = 0;
            for (AdnRecord record : existingRecords) {
                if (!record.isEmpty()) {
                    nonEmptyCount++;
                }
            }
            row.add(ElementaryFiles.RECORD_COUNT, nonEmptyCount);
        }
    }

    private Cursor querySimRecords(PhonebookArgs args, String[] projection) {
        validateProjection(SIM_RECORDS_COLUMNS_SET, projection);
        validateSubscriptionAndEf(args);
        if (projection == null) {
            projection = SIM_RECORDS_ALL_COLUMNS;
        }

        List<AdnRecord> records = loadRecordsForEf(args);
        if (records == null) {
            return new MatrixCursor(projection, 0);
        }
        MatrixCursor result = new MatrixCursor(projection, records.size());
        SparseArray<MatrixCursor.RowBuilder> rowBuilders = new SparseArray<>(records.size());
        for (int i = 0; i < records.size(); i++) {
            AdnRecord record = records.get(i);
            if (!record.isEmpty()) {
                rowBuilders.put(i, result.newRow());
            }
        }
        // This is kind of ugly but avoids looking up columns in an inner loop.
        for (String column : projection) {
            switch (column) {
                case SimRecords.SUBSCRIPTION_ID:
                    for (int i = 0; i < rowBuilders.size(); i++) {
                        rowBuilders.valueAt(i).add(args.subscriptionId);
                    }
                    break;
                case SimRecords.ELEMENTARY_FILE_TYPE:
                    for (int i = 0; i < rowBuilders.size(); i++) {
                        rowBuilders.valueAt(i).add(args.efType);
                    }
                    break;
                case SimRecords.RECORD_NUMBER:
                    for (int i = 0; i < rowBuilders.size(); i++) {
                        int index = rowBuilders.keyAt(i);
                        MatrixCursor.RowBuilder rowBuilder = rowBuilders.valueAt(i);
                        // See b/201685690. The logical record number, i.e. the 1-based index in the
                        // list, is used the rather than AdnRecord.getRecId() because getRecId is
                        // not offset when a single logical EF is made up of multiple physical EFs.
                        rowBuilder.add(index + 1);
                    }
                    break;
                case SimRecords.NAME:
                    for (int i = 0; i < rowBuilders.size(); i++) {
                        AdnRecord record = records.get(rowBuilders.keyAt(i));
                        rowBuilders.valueAt(i).add(record.getAlphaTag());
                    }
                    break;
                case SimRecords.PHONE_NUMBER:
                    for (int i = 0; i < rowBuilders.size(); i++) {
                        AdnRecord record = records.get(rowBuilders.keyAt(i));
                        rowBuilders.valueAt(i).add(record.getNumber());
                    }
                    break;
                default:
                    Rlog.w(TAG, "Column " + column + " is unsupported for " + args.uri);
                    break;
            }
        }
        return result;
    }

    private Cursor querySimRecordsItem(PhonebookArgs args, String[] projection) {
        validateProjection(SIM_RECORDS_COLUMNS_SET, projection);
        if (projection == null) {
            projection = SIM_RECORDS_ALL_COLUMNS;
        }
        validateSubscriptionAndEf(args);
        AdnRecord record = loadRecord(args);

        MatrixCursor result = new MatrixCursor(projection, 1);
        if (record == null || record.isEmpty()) {
            return result;
        }
        result.newRow()
                .add(SimRecords.SUBSCRIPTION_ID, args.subscriptionId)
                .add(SimRecords.ELEMENTARY_FILE_TYPE, args.efType)
                .add(SimRecords.RECORD_NUMBER, record.getRecId())
                .add(SimRecords.NAME, record.getAlphaTag())
                .add(SimRecords.PHONE_NUMBER, record.getNumber());
        return result;
    }

    @Nullable
    @Override
    public String getType(@NonNull Uri uri) {
        switch (URI_MATCHER.match(uri)) {
            case ELEMENTARY_FILES:
                return ElementaryFiles.CONTENT_TYPE;
            case ELEMENTARY_FILES_ITEM:
                return ElementaryFiles.CONTENT_ITEM_TYPE;
            case SIM_RECORDS:
                return SimRecords.CONTENT_TYPE;
            case SIM_RECORDS_ITEM:
                return SimRecords.CONTENT_ITEM_TYPE;
            default:
                throw new IllegalArgumentException("Unsupported Uri " + uri);
        }
    }

    @Nullable
    @Override
    public Uri insert(@NonNull Uri uri, @Nullable ContentValues values) {
        return insert(uri, values, null);
    }

    @Nullable
    @Override
    public Uri insert(@NonNull Uri uri, @Nullable ContentValues values, @Nullable Bundle extras) {
        switch (URI_MATCHER.match(uri)) {
            case SIM_RECORDS:
                return insertSimRecord(PhonebookArgs.forSimRecords(uri, extras), values);
            case ELEMENTARY_FILES:
            case ELEMENTARY_FILES_ITEM:
            case SIM_RECORDS_ITEM:
                throw new UnsupportedOperationException(uri + " does not support insert");
            default:
                throw new IllegalArgumentException("Unsupported Uri " + uri);
        }
    }

    private Uri insertSimRecord(PhonebookArgs args, ContentValues values) {
        validateWritableEf(args, "insert");
        validateSubscriptionAndEf(args);

        if (values == null || values.isEmpty()) {
            return null;
        }
        validateValues(args, values);
        String newName = Strings.nullToEmpty(values.getAsString(SimRecords.NAME));
        String newPhoneNumber = Strings.nullToEmpty(values.getAsString(SimRecords.PHONE_NUMBER));

        acquireWriteLockOrThrow();
        try {
            List<AdnRecord> records = loadRecordsForEf(args);
            if (records == null) {
                Rlog.e(TAG, "Failed to load existing records for " + args.uri);
                return null;
            }
            AdnRecord emptyRecord = null;
            for (AdnRecord record : records) {
                if (record.isEmpty()) {
                    emptyRecord = record;
                    break;
                }
            }
            if (emptyRecord == null) {
                // When there are no empty records that means the EF is full.
                throw new IllegalStateException(
                        args.uri + " is full. Please delete records to add new ones.");
            }
            boolean success = updateRecord(args, emptyRecord, args.pin2, newName, newPhoneNumber);
            if (!success) {
                Rlog.e(TAG, "Insert failed for " + args.uri);
                // Something didn't work but since we don't have any more specific
                // information to provide to the caller it's better to just return null
                // rather than throwing and possibly crashing their process.
                return null;
            }
            notifyChange();
            return SimRecords.getItemUri(args.subscriptionId, args.efType, emptyRecord.getRecId());
        } finally {
            releaseWriteLock();
        }
    }

    @Override
    public int delete(@NonNull Uri uri, @Nullable String selection,
            @Nullable String[] selectionArgs) {
        throw new UnsupportedOperationException("Only delete with Bundle is supported");
    }

    @Override
    public int delete(@NonNull Uri uri, @Nullable Bundle extras) {
        switch (URI_MATCHER.match(uri)) {
            case SIM_RECORDS_ITEM:
                return deleteSimRecordsItem(PhonebookArgs.forSimRecordsItem(uri, extras));
            case ELEMENTARY_FILES:
            case ELEMENTARY_FILES_ITEM:
            case SIM_RECORDS:
                throw new UnsupportedOperationException(uri + " does not support delete");
            default:
                throw new IllegalArgumentException("Unsupported Uri " + uri);
        }
    }

    private int deleteSimRecordsItem(PhonebookArgs args) {
        validateWritableEf(args, "delete");
        validateSubscriptionAndEf(args);

        acquireWriteLockOrThrow();
        try {
            AdnRecord record = loadRecord(args);
            if (record == null || record.isEmpty()) {
                return 0;
            }
            if (!updateRecord(args, record, args.pin2, "", "")) {
                Rlog.e(TAG, "Failed to delete " + args.uri);
            }
            notifyChange();
        } finally {
            releaseWriteLock();
        }
        return 1;
    }


    @Override
    public int update(@NonNull Uri uri, @Nullable ContentValues values, @Nullable Bundle extras) {
        switch (URI_MATCHER.match(uri)) {
            case SIM_RECORDS_ITEM:
                return updateSimRecordsItem(PhonebookArgs.forSimRecordsItem(uri, extras), values);
            case ELEMENTARY_FILES:
            case ELEMENTARY_FILES_ITEM:
            case SIM_RECORDS:
                throw new UnsupportedOperationException(uri + " does not support update");
            default:
                throw new IllegalArgumentException("Unsupported Uri " + uri);
        }
    }

    @Override
    public int update(@NonNull Uri uri, @Nullable ContentValues values, @Nullable String selection,
            @Nullable String[] selectionArgs) {
        throw new UnsupportedOperationException("Only Update with bundle is supported");
    }

    private int updateSimRecordsItem(PhonebookArgs args, ContentValues values) {
        validateWritableEf(args, "update");
        validateSubscriptionAndEf(args);

        if (values == null || values.isEmpty()) {
            return 0;
        }
        validateValues(args, values);
        String newName = Strings.nullToEmpty(values.getAsString(SimRecords.NAME));
        String newPhoneNumber = Strings.nullToEmpty(values.getAsString(SimRecords.PHONE_NUMBER));

        acquireWriteLockOrThrow();

        try {
            AdnRecord record = loadRecord(args);

            // Note we allow empty records to be updated. This is a bit weird because they are
            // not returned by query methods but this allows a client application assign a name
            // to a specific record number. This may be desirable in some phone app use cases since
            // the record number is often used as a quick dial index.
            if (record == null) {
                return 0;
            }
            if (!updateRecord(args, record, args.pin2, newName, newPhoneNumber)) {
                Rlog.e(TAG, "Failed to update " + args.uri);
                return 0;
            }
            notifyChange();
        } finally {
            releaseWriteLock();
        }
        return 1;
    }

    void validateSubscriptionAndEf(PhonebookArgs args) {
        SubscriptionInfo info =
                args.subscriptionId != SubscriptionManager.INVALID_SUBSCRIPTION_ID
                        ? getActiveSubscriptionInfo(args.subscriptionId)
                        : null;
        if (info == null) {
            throw new IllegalArgumentException("No active SIM with subscription ID "
                    + args.subscriptionId);
        }

        int[] recordsSize = getRecordsSizeForEf(args);
        if (recordsSize == null || recordsSize[1] == 0) {
            throw new IllegalArgumentException(args.efName
                    + " is not supported for SIM with subscription ID " + args.subscriptionId);
        }
    }

    private void acquireWriteLockOrThrow() {
        try {
            if (!mWriteLock.tryLock(WRITE_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Timeout waiting to write");
            }
        } catch (InterruptedException e) {
            throw new IllegalStateException("Write failed");
        }
    }

    private void releaseWriteLock() {
        mWriteLock.unlock();
    }

    private void validateWritableEf(PhonebookArgs args, String operationName) {
        if (args.efType == ElementaryFiles.EF_FDN) {
            if (hasPermissionsForFdnWrite(args)) {
                return;
            }
        }
        if (args.efType != ElementaryFiles.EF_ADN) {
            throw new UnsupportedOperationException(
                    args.uri + " does not support " + operationName);
        }
    }

    private boolean hasPermissionsForFdnWrite(PhonebookArgs args) {
        TelephonyManager telephonyManager = Objects.requireNonNull(
                getContext().getSystemService(TelephonyManager.class));
        String callingPackage = getCallingPackage();
        int granted = PackageManager.PERMISSION_DENIED;
        if (callingPackage != null) {
            granted = getContext().getPackageManager().checkPermission(
                    Manifest.permission.MODIFY_PHONE_STATE, callingPackage);
        }
        return granted == PackageManager.PERMISSION_GRANTED
                || telephonyManager.hasCarrierPrivileges(args.subscriptionId);

    }


    private boolean updateRecord(PhonebookArgs args, AdnRecord existingRecord, String pin2,
            String newName, String newPhone) {
        try {
            ContentValues values = new ContentValues();
            values.put(STR_NEW_TAG, newName);
            values.put(STR_NEW_NUMBER, newPhone);
            return mIccPhoneBookSupplier.get().updateAdnRecordsInEfByIndexForSubscriber(
                    args.subscriptionId, existingRecord.getEfid(), values,
                    existingRecord.getRecId(),
                    pin2);
        } catch (RemoteException e) {
            return false;
        }
    }

    private void validatePhoneNumber(@Nullable String phoneNumber) {
        if (phoneNumber == null || phoneNumber.isEmpty()) {
            throw new IllegalArgumentException(SimRecords.PHONE_NUMBER + " is required.");
        }
        int actualLength = phoneNumber.length();
        // When encoded the "+" prefix sets a bit and so doesn't count against the maximum length
        if (phoneNumber.startsWith("+")) {
            actualLength--;
        }
        if (actualLength > AdnRecord.getMaxPhoneNumberDigits()) {
            throw new IllegalArgumentException(SimRecords.PHONE_NUMBER + " is too long.");
        }
        for (int i = 0; i < phoneNumber.length(); i++) {
            char c = phoneNumber.charAt(i);
            if (!PhoneNumberUtils.isNonSeparator(c)) {
                throw new IllegalArgumentException(
                        SimRecords.PHONE_NUMBER + " contains unsupported characters.");
            }
        }
    }

    private void validateValues(PhonebookArgs args, ContentValues values) {
        if (!SIM_RECORDS_WRITABLE_COLUMNS.containsAll(values.keySet())) {
            Set<String> unsupportedColumns = new ArraySet<>(values.keySet());
            unsupportedColumns.removeAll(SIM_RECORDS_WRITABLE_COLUMNS);
            throw new IllegalArgumentException("Unsupported columns: " + Joiner.on(',')
                    .join(unsupportedColumns));
        }

        String phoneNumber = values.getAsString(SimRecords.PHONE_NUMBER);
        validatePhoneNumber(phoneNumber);

        String name = values.getAsString(SimRecords.NAME);
        int length = getEncodedNameLength(name);
        int[] recordsSize = getRecordsSizeForEf(args);
        if (recordsSize == null) {
            throw new IllegalStateException(
                    "Failed to get " + ElementaryFiles.NAME_MAX_LENGTH + " from SIM");
        }
        int maxLength = AdnRecord.getMaxAlphaTagBytes(getRecordSize(recordsSize));

        if (length > maxLength) {
            throw new IllegalArgumentException(SimRecords.NAME + " is too long.");
        }
    }

    private List<SubscriptionInfo> getActiveSubscriptionInfoList() {
        // Getting the SubscriptionInfo requires READ_PHONE_STATE but we're only returning
        // the subscription ID and slot index which are not sensitive information.
        CallingIdentity identity = clearCallingIdentity();
        try {
            return mSubscriptionManager.getActiveSubscriptionInfoList();
        } finally {
            restoreCallingIdentity(identity);
        }
    }

    @Nullable
    private SubscriptionInfo getActiveSubscriptionInfo(int subId) {
        // Getting the SubscriptionInfo requires READ_PHONE_STATE.
        CallingIdentity identity = clearCallingIdentity();
        try {
            return mSubscriptionManager.getActiveSubscriptionInfo(subId);
        } finally {
            restoreCallingIdentity(identity);
        }
    }

    private List<AdnRecord> loadRecordsForEf(PhonebookArgs args) {
        try {
            return mIccPhoneBookSupplier.get().getAdnRecordsInEfForSubscriber(
                    args.subscriptionId, args.efid);
        } catch (RemoteException e) {
            return null;
        }
    }

    private AdnRecord loadRecord(PhonebookArgs args) {
        List<AdnRecord> records = loadRecordsForEf(args);
        if (records == null || args.recordNumber > records.size()) {
            return null;
        }
        return records.get(args.recordNumber - 1);
    }

    private int[] getRecordsSizeForEf(PhonebookArgs args) {
        try {
            return mIccPhoneBookSupplier.get().getAdnRecordsSizeForSubscriber(
                    args.subscriptionId, args.efid);
        } catch (RemoteException e) {
            return null;
        }
    }

    void notifyChange() {
        mContentNotifier.notifyChange(SimPhonebookContract.AUTHORITY_URI);
    }

    /** Testable wrapper around {@link ContentResolver#notifyChange(Uri, ContentObserver)} */
    @TestApi
    interface ContentNotifier {
        void notifyChange(Uri uri);
    }

    /**
     * Holds the arguments extracted from the Uri and query args for accessing the referenced
     * phonebook data on a SIM.
     */
    private static class PhonebookArgs {
        public final Uri uri;
        public final int subscriptionId;
        public final String efName;
        public final int efType;
        public final int efid;
        public final int recordNumber;
        public final String pin2;

        PhonebookArgs(Uri uri, int subscriptionId, String efName,
                @ElementaryFiles.EfType int efType, int efid, int recordNumber,
                @Nullable Bundle queryArgs) {
            this.uri = uri;
            this.subscriptionId = subscriptionId;
            this.efName = efName;
            this.efType = efType;
            this.efid = efid;
            this.recordNumber = recordNumber;
            pin2 = efType == ElementaryFiles.EF_FDN && queryArgs != null
                    ? queryArgs.getString(SimRecords.QUERY_ARG_PIN2)
                    : null;
        }

        static PhonebookArgs createFromEfName(Uri uri, int subscriptionId,
                String efName, int recordNumber, @Nullable Bundle queryArgs) {
            int efType;
            int efid;
            if (efName != null) {
                switch (efName) {
                    case ElementaryFiles.PATH_SEGMENT_EF_ADN:
                        efType = ElementaryFiles.EF_ADN;
                        efid = IccConstants.EF_ADN;
                        break;
                    case ElementaryFiles.PATH_SEGMENT_EF_FDN:
                        efType = ElementaryFiles.EF_FDN;
                        efid = IccConstants.EF_FDN;
                        break;
                    case ElementaryFiles.PATH_SEGMENT_EF_SDN:
                        efType = ElementaryFiles.EF_SDN;
                        efid = IccConstants.EF_SDN;
                        break;
                    default:
                        throw new IllegalArgumentException(
                                "Unrecognized elementary file " + efName);
                }
            } else {
                efType = ElementaryFiles.EF_UNKNOWN;
                efid = 0;
            }
            return new PhonebookArgs(uri, subscriptionId, efName, efType, efid, recordNumber,
                    queryArgs);
        }

        /**
         * Pattern: elementary_files/subid/${subscriptionId}/${efName}
         *
         * e.g. elementary_files/subid/1/adn
         *
         * @see ElementaryFiles#getItemUri(int, int)
         * @see #ELEMENTARY_FILES_ITEM
         */
        static PhonebookArgs forElementaryFilesItem(Uri uri) {
            int subscriptionId = parseSubscriptionIdFromUri(uri, 2);
            String efName = uri.getPathSegments().get(3);
            return PhonebookArgs.createFromEfName(
                    uri, subscriptionId, efName, -1, null);
        }

        /**
         * Pattern: subid/${subscriptionId}/${efName}
         *
         * <p>e.g. subid/1/adn
         *
         * @see SimRecords#getContentUri(int, int)
         * @see #SIM_RECORDS
         */
        static PhonebookArgs forSimRecords(Uri uri, Bundle queryArgs) {
            int subscriptionId = parseSubscriptionIdFromUri(uri, 1);
            String efName = uri.getPathSegments().get(2);
            return PhonebookArgs.createFromEfName(uri, subscriptionId, efName, -1, queryArgs);
        }

        /**
         * Pattern: subid/${subscriptionId}/${efName}/${recordNumber}
         *
         * <p>e.g. subid/1/adn/10
         *
         * @see SimRecords#getItemUri(int, int, int)
         * @see #SIM_RECORDS_ITEM
         */
        static PhonebookArgs forSimRecordsItem(Uri uri, Bundle queryArgs) {
            int subscriptionId = parseSubscriptionIdFromUri(uri, 1);
            String efName = uri.getPathSegments().get(2);
            int recordNumber = parseRecordNumberFromUri(uri, 3);
            return PhonebookArgs.createFromEfName(uri, subscriptionId, efName, recordNumber,
                    queryArgs);
        }

        private static int parseSubscriptionIdFromUri(Uri uri, int pathIndex) {
            if (pathIndex == -1) {
                return SubscriptionManager.INVALID_SUBSCRIPTION_ID;
            }
            String segment = uri.getPathSegments().get(pathIndex);
            try {
                return Integer.parseInt(segment);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Invalid subscription ID: " + segment);
            }
        }

        private static int parseRecordNumberFromUri(Uri uri, int pathIndex) {
            try {
                return Integer.parseInt(uri.getPathSegments().get(pathIndex));
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException(
                        "Invalid record index: " + uri.getLastPathSegment());
            }
        }
    }
}
