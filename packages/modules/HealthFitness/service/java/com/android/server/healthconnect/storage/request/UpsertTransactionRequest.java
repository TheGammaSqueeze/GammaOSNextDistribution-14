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

package com.android.server.healthconnect.storage.request;

import static android.health.connect.Constants.UPSERT;

import static com.android.server.healthconnect.storage.utils.WhereClauses.LogicalOperator.AND;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.content.Context;
import android.health.connect.Constants;
import android.health.connect.datatypes.RecordTypeIdentifier;
import android.health.connect.internal.datatypes.RecordInternal;
import android.util.ArrayMap;
import android.util.ArraySet;
import android.util.Slog;

import com.android.server.healthconnect.storage.datatypehelpers.AccessLogsHelper;
import com.android.server.healthconnect.storage.datatypehelpers.AppInfoHelper;
import com.android.server.healthconnect.storage.datatypehelpers.ChangeLogsHelper;
import com.android.server.healthconnect.storage.datatypehelpers.DeviceInfoHelper;
import com.android.server.healthconnect.storage.datatypehelpers.RecordHelper;
import com.android.server.healthconnect.storage.utils.RecordHelperProvider;
import com.android.server.healthconnect.storage.utils.StorageUtils;
import com.android.server.healthconnect.storage.utils.WhereClauses;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Refines a request from what the user sent to a format that makes the most sense for the
 * TransactionManager.
 *
 * <p>Notes, This class refines the request as well by replacing the untrusted fields with the
 * platform's trusted sources. As a part of that this class populates uuid and package name for all
 * the entries in {@param records}.
 *
 * @hide
 */
public class UpsertTransactionRequest {
    private static final String TAG = "HealthConnectUTR";
    @NonNull private final List<UpsertTableRequest> mUpsertRequests = new ArrayList<>();
    @NonNull private final String mPackageName;
    private final List<UpsertTableRequest> mAccessLogs = new ArrayList<>();
    private final boolean mSkipPackageNameAndLogs;
    @RecordTypeIdentifier.RecordType Set<Integer> mRecordTypes = new ArraySet<>();

    private ArrayMap<String, Boolean> mExtraWritePermissionsToState;

    public UpsertTransactionRequest(
            @Nullable String packageName,
            @NonNull List<RecordInternal<?>> recordInternals,
            Context context,
            boolean isInsertRequest,
            Map<String, Boolean> extraPermsStateMap) {
        this(
                packageName,
                recordInternals,
                context,
                isInsertRequest,
                false /* skipPackageNameAndLogs */,
                extraPermsStateMap);
    }

    public UpsertTransactionRequest(
            @Nullable String packageName,
            @NonNull List<RecordInternal<?>> recordInternals,
            Context context,
            boolean isInsertRequest,
            boolean skipPackageNameAndLogs) {
        this(
                packageName,
                recordInternals,
                context,
                isInsertRequest,
                skipPackageNameAndLogs,
                Collections.emptyMap());
    }

    @SuppressWarnings("NullAway") // TODO(b/317029272): fix this suppression
    public UpsertTransactionRequest(
            @Nullable String packageName,
            @NonNull List<RecordInternal<?>> recordInternals,
            Context context,
            boolean isInsertRequest,
            boolean skipPackageNameAndLogs,
            Map<String, Boolean> extraPermsStateMap) {
        mPackageName = packageName;
        mSkipPackageNameAndLogs = skipPackageNameAndLogs;
        if (extraPermsStateMap != null && !extraPermsStateMap.isEmpty()) {
            mExtraWritePermissionsToState = new ArrayMap<>();
            mExtraWritePermissionsToState.putAll(extraPermsStateMap);
        }

        for (RecordInternal<?> recordInternal : recordInternals) {
            if (!mSkipPackageNameAndLogs) {
                StorageUtils.addPackageNameTo(recordInternal, packageName);
            }
            AppInfoHelper.getInstance()
                    .populateAppInfoId(recordInternal, context, /* requireAllFields= */ true);
            DeviceInfoHelper.getInstance().populateDeviceInfoId(recordInternal);

            if (isInsertRequest) {
                // Always generate an uuid field for insert requests, we should not trust what is
                // already present.
                StorageUtils.addNameBasedUUIDTo(recordInternal);
                mRecordTypes.add(recordInternal.getRecordType());
            } else {
                // For update requests, generate uuid if the clientRecordID is present, else use the
                // uuid passed as input.
                StorageUtils.updateNameBasedUUIDIfRequired(recordInternal);
            }
            recordInternal.setLastModifiedTime(Instant.now().toEpochMilli());
            addRequest(recordInternal, isInsertRequest);
        }

        if (!mRecordTypes.isEmpty()) {
            if (!mSkipPackageNameAndLogs) {
                mAccessLogs.add(
                        AccessLogsHelper.getInstance()
                                .getUpsertTableRequest(
                                        packageName, new ArrayList<>(mRecordTypes), UPSERT));
            }

            if (Constants.DEBUG) {
                Slog.d(
                        TAG,
                        "Upserting transaction for "
                                + packageName
                                + " with size "
                                + recordInternals.size());
            }
        }
    }

    public List<UpsertTableRequest> getAccessLogs() {
        return mAccessLogs;
    }

    @NonNull
    public List<UpsertTableRequest> getInsertRequestsForChangeLogs() {
        if (mSkipPackageNameAndLogs) {
            return Collections.emptyList();
        }
        long currentTime = Instant.now().toEpochMilli();
        ChangeLogsHelper.ChangeLogs insertChangeLogs =
                new ChangeLogsHelper.ChangeLogs(UPSERT, mPackageName, currentTime);
        for (UpsertTableRequest upsertRequest : mUpsertRequests) {
            insertChangeLogs.addUUID(
                    upsertRequest.getRecordInternal().getRecordType(),
                    upsertRequest.getRecordInternal().getAppInfoId(),
                    upsertRequest.getRecordInternal().getUuid());
        }

        return insertChangeLogs.getUpsertTableRequests();
    }

    @NonNull
    public List<UpsertTableRequest> getUpsertRequests() {
        return mUpsertRequests;
    }

    @NonNull
    public List<String> getUUIdsInOrder() {
        return mUpsertRequests.stream()
                .map((request) -> request.getRecordInternal().getUuid().toString())
                .collect(Collectors.toList());
    }

    private WhereClauses generateWhereClausesForUpdate(@NonNull RecordInternal<?> recordInternal) {
        WhereClauses whereClauseForUpdateRequest = new WhereClauses(AND);
        whereClauseForUpdateRequest.addWhereEqualsClause(
                RecordHelper.UUID_COLUMN_NAME, StorageUtils.getHexString(recordInternal.getUuid()));
        whereClauseForUpdateRequest.addWhereEqualsClause(
                RecordHelper.APP_INFO_ID_COLUMN_NAME,
                /* expected args value */ String.valueOf(recordInternal.getAppInfoId()));
        return whereClauseForUpdateRequest;
    }

    private void addRequest(@NonNull RecordInternal<?> recordInternal, boolean isInsertRequest) {
        RecordHelper<?> recordHelper =
                RecordHelperProvider.getInstance().getRecordHelper(recordInternal.getRecordType());
        Objects.requireNonNull(recordHelper);

        UpsertTableRequest request =
                recordHelper.getUpsertTableRequest(recordInternal, mExtraWritePermissionsToState);
        request.setRecordType(recordHelper.getRecordIdentifier());
        if (!isInsertRequest) {
            request.setUpdateWhereClauses(generateWhereClausesForUpdate(recordInternal));
        }
        request.setRecordInternal(recordInternal);
        mUpsertRequests.add(request);
    }
}
