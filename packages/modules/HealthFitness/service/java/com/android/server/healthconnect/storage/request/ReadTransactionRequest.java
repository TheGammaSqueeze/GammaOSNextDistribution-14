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

import static android.health.connect.Constants.DEFAULT_INT;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.health.connect.PageTokenWrapper;
import android.health.connect.aidl.ReadRecordsRequestParcel;

import com.android.server.healthconnect.storage.datatypehelpers.RecordHelper;
import com.android.server.healthconnect.storage.utils.RecordHelperProvider;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Refines a request from what the user sent to a format that makes the most sense for the
 * TransactionManager.
 *
 * <p>Notes, This class refines the queries the records stored in the DB
 *
 * @hide
 */
// TODO(b/308158714): Separate two types of requests: read by id and read by filter.
public class ReadTransactionRequest {
    public static final String TYPE_NOT_PRESENT_PACKAGE_NAME = "package_name";
    private final List<ReadTableRequest> mReadTableRequests;
    @Nullable // page token is null for read by id requests
    private final PageTokenWrapper mPageToken;
    private final int mPageSize;

    public ReadTransactionRequest(
            String callingPackageName,
            ReadRecordsRequestParcel request,
            long startDateAccessMillis,
            boolean enforceSelfRead,
            Set<String> grantedExtraReadPermissions,
            boolean isInForeground) {
        RecordHelper<?> recordHelper =
                RecordHelperProvider.getInstance().getRecordHelper(request.getRecordType());
        mReadTableRequests =
                Collections.singletonList(
                        recordHelper.getReadTableRequest(
                                request,
                                callingPackageName,
                                enforceSelfRead,
                                startDateAccessMillis,
                                grantedExtraReadPermissions,
                                isInForeground));
        if (request.getRecordIdFiltersParcel() == null) {
            mPageToken = PageTokenWrapper.from(request.getPageToken(), request.isAscending());
            mPageSize = request.getPageSize();
        } else {
            mPageSize = DEFAULT_INT;
            mPageToken = null;
        }
    }

    public ReadTransactionRequest(
            String packageName,
            Map<Integer, List<UUID>> recordTypeToUuids,
            long startDateAccessMillis,
            Set<String> grantedExtraReadPermissions,
            boolean isInForeground) {
        mReadTableRequests = new ArrayList<>();
        recordTypeToUuids.forEach(
                (recordType, uuids) ->
                        mReadTableRequests.add(
                                RecordHelperProvider.getInstance()
                                        .getRecordHelper(recordType)
                                        .getReadTableRequest(
                                                packageName,
                                                uuids,
                                                startDateAccessMillis,
                                                grantedExtraReadPermissions,
                                                isInForeground)));
        mPageSize = DEFAULT_INT;
        mPageToken = null;
    }

    @NonNull
    public List<ReadTableRequest> getReadRequests() {
        return mReadTableRequests;
    }

    @Nullable
    public PageTokenWrapper getPageToken() {
        return mPageToken;
    }

    /**
     * Returns optional of page size in the {@link android.health.connect.ReadRecordsRequest}
     * refined by this {@link ReadTransactionRequest}.
     *
     * <p>For {@link android.health.connect.ReadRecordsRequestUsingIds} requests, page size is
     * {@code Optional.empty}.
     */
    public Optional<Integer> getPageSize() {
        return mPageSize == DEFAULT_INT ? Optional.empty() : Optional.of(mPageSize);
    }
}
