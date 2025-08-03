/*
 * Copyright (C) 2023 The Android Open Source Project
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

package android.healthconnect.cts.testhelper;

import static android.healthconnect.cts.lib.BundleHelper.DELETE_RECORDS_QUERY;
import static android.healthconnect.cts.lib.BundleHelper.GET_CHANGE_LOG_TOKEN_QUERY;
import static android.healthconnect.cts.lib.BundleHelper.INSERT_RECORDS_QUERY;
import static android.healthconnect.cts.lib.BundleHelper.INTENT_EXCEPTION;
import static android.healthconnect.cts.lib.BundleHelper.KILL_SELF_REQUEST;
import static android.healthconnect.cts.lib.BundleHelper.QUERY_TYPE;
import static android.healthconnect.cts.lib.BundleHelper.READ_CHANGE_LOGS_QUERY;
import static android.healthconnect.cts.lib.BundleHelper.READ_RECORDS_QUERY;
import static android.healthconnect.cts.lib.BundleHelper.READ_RECORDS_USING_IDS_QUERY;
import static android.healthconnect.cts.lib.BundleHelper.SELF_REVOKE_PERMISSION_REQUEST;
import static android.healthconnect.cts.lib.BundleHelper.UPDATE_RECORDS_QUERY;

import android.content.Context;
import android.content.Intent;
import android.health.connect.ReadRecordsRequestUsingFilters;
import android.health.connect.ReadRecordsRequestUsingIds;
import android.health.connect.RecordIdFilter;
import android.health.connect.changelog.ChangeLogTokenRequest;
import android.health.connect.changelog.ChangeLogTokenResponse;
import android.health.connect.changelog.ChangeLogsRequest;
import android.health.connect.changelog.ChangeLogsResponse;
import android.health.connect.datatypes.Metadata;
import android.health.connect.datatypes.Record;
import android.healthconnect.cts.lib.BundleHelper;
import android.healthconnect.cts.utils.TestUtils;
import android.os.Bundle;

import java.util.List;

final class TestAppHelper {

    static Intent handleRequest(Context context, Bundle bundle) {
        String queryType = bundle.getString(QUERY_TYPE);
        Intent response = new Intent(queryType);
        try {
            Bundle responseBundle = handleRequestUnchecked(context, bundle, queryType);
            response.putExtras(responseBundle);
        } catch (Exception e) {
            response.putExtra(INTENT_EXCEPTION, e);
        }
        return response;
    }

    private static Bundle handleRequestUnchecked(Context context, Bundle bundle, String queryType)
            throws Exception {
        return switch (queryType) {
            case INSERT_RECORDS_QUERY -> handleInsertRecords(context, bundle);
            case DELETE_RECORDS_QUERY -> handleDeleteRecords(context, bundle);
            case UPDATE_RECORDS_QUERY -> handleUpdateRecords(context, bundle);
            case READ_RECORDS_QUERY -> handleReadRecords(context, bundle);
            case READ_RECORDS_USING_IDS_QUERY -> handleReadRecordsUsingIds(context, bundle);
            case READ_CHANGE_LOGS_QUERY -> handleGetChangeLogs(context, bundle);
            case GET_CHANGE_LOG_TOKEN_QUERY -> handleGetChangeLogToken(context, bundle);
            case SELF_REVOKE_PERMISSION_REQUEST -> handleSelfRevoke(context, bundle);
            case KILL_SELF_REQUEST -> handleKillSelf();
            default -> throw new IllegalStateException(
                    "Unknown query received from launcher app: " + queryType);
        };
    }

    private static Bundle handleInsertRecords(Context context, Bundle bundle) throws Exception {
        List<? extends Record> records = BundleHelper.toInsertRecordsRequest(bundle);
        List<Record> insertedRecords = TestUtils.insertRecords(records, context);
        List<String> response =
                insertedRecords.stream().map(Record::getMetadata).map(Metadata::getId).toList();
        return BundleHelper.fromInsertRecordsResponse(response);
    }

    private static Bundle handleReadRecords(Context context, Bundle bundle) throws Exception {
        ReadRecordsRequestUsingFilters<? extends Record> request =
                BundleHelper.toReadRecordsRequestUsingFilters(bundle);
        List<? extends Record> records = TestUtils.readRecords(request, context);
        return BundleHelper.fromReadRecordsResponse(records);
    }

    private static Bundle handleReadRecordsUsingIds(Context context, Bundle bundle)
            throws Exception {
        ReadRecordsRequestUsingIds<? extends Record> request =
                BundleHelper.toReadRecordsRequestUsingIds(bundle);
        List<? extends Record> records = TestUtils.readRecords(request, context);
        return BundleHelper.fromReadRecordsResponse(records);
    }

    private static Bundle handleDeleteRecords(Context context, Bundle bundle) throws Exception {
        List<RecordIdFilter> recordIdFilters = BundleHelper.toDeleteRecordsByIdsRequest(bundle);

        TestUtils.verifyDeleteRecords(recordIdFilters, context);

        return new Bundle();
    }

    private static Bundle handleUpdateRecords(Context context, Bundle bundle) throws Exception {
        List<? extends Record> records = BundleHelper.toUpdateRecordsRequest(bundle);
        TestUtils.updateRecords(records, context);
        return new Bundle();
    }

    private static Bundle handleGetChangeLogToken(Context context, Bundle bundle) throws Exception {
        ChangeLogTokenRequest request = BundleHelper.toChangeLogTokenRequest(bundle);
        ChangeLogTokenResponse response = TestUtils.getChangeLogToken(request, context);
        return BundleHelper.fromChangeLogTokenResponse(response.getToken());
    }

    private static Bundle handleSelfRevoke(Context context, Bundle bundle) throws Exception {
        String permissionToRevoke = BundleHelper.toPermissionToSelfRevoke(bundle);
        context.revokeSelfPermissionOnKill(permissionToRevoke);
        return new Bundle();
    }

    private static Bundle handleKillSelf() throws Exception {
        System.exit(0);
        return new Bundle();
    }

    private static Bundle handleGetChangeLogs(Context context, Bundle bundle) throws Exception {
        ChangeLogsRequest request = BundleHelper.toChangeLogsRequest(bundle);
        ChangeLogsResponse response = TestUtils.getChangeLogs(request, context);
        return BundleHelper.fromChangeLogsResponse(response);
    }

    private TestAppHelper() {}
}
