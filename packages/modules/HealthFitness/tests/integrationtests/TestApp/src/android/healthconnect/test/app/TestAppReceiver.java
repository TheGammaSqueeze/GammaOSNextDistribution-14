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

package android.healthconnect.test.app;

import static android.health.connect.datatypes.ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL;

import static java.util.Objects.requireNonNull;
import static java.util.concurrent.Executors.newSingleThreadExecutor;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.health.connect.AggregateRecordsRequest;
import android.health.connect.AggregateRecordsResponse;
import android.health.connect.HealthConnectException;
import android.health.connect.HealthConnectManager;
import android.health.connect.InsertRecordsResponse;
import android.health.connect.ReadRecordsRequestUsingFilters;
import android.health.connect.ReadRecordsResponse;
import android.health.connect.TimeInstantRangeFilter;
import android.health.connect.changelog.ChangeLogTokenRequest;
import android.health.connect.changelog.ChangeLogTokenResponse;
import android.health.connect.changelog.ChangeLogsRequest;
import android.health.connect.changelog.ChangeLogsResponse;
import android.health.connect.datatypes.ActiveCaloriesBurnedRecord;
import android.health.connect.datatypes.DataOrigin;
import android.health.connect.datatypes.Metadata;
import android.health.connect.datatypes.Record;
import android.health.connect.datatypes.StepsRecord;
import android.health.connect.datatypes.WeightRecord;
import android.health.connect.datatypes.units.Energy;
import android.health.connect.datatypes.units.Mass;
import android.os.Bundle;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/** Receives requests from test cases. Required to perform API calls in background. */
public class TestAppReceiver extends BroadcastReceiver {
    public static final String ACTION_INSERT_STEPS_RECORDS = "action.INSERT_STEPS_RECORDS";
    public static final String ACTION_INSERT_WEIGHT_RECORDS = "action.INSERT_WEIGHT_RECORDS";
    public static final String ACTION_READ_RECORDS_FOR_OTHER_APP =
            "action.READ_RECORDS_FOR_OTHER_APP";
    public static final String ACTION_AGGREGATE = "action.AGGREGATE";
    public static final String ACTION_GET_CHANGE_LOG_TOKEN = "action.GET_CHANGE_LOG_TOKEN";
    public static final String ACTION_GET_CHANGE_LOGS = "action.GET_CHANGE_LOGS";
    public static final String ACTION_RESULT_SUCCESS = "action.SUCCESS";
    public static final String ACTION_RESULT_ERROR = "action.ERROR";
    public static final String EXTRA_RESULT_ERROR_CODE = "extra.ERROR_CODE";
    public static final String EXTRA_RESULT_ERROR_MESSAGE = "extra.ERROR_MESSAGE";
    public static final String EXTRA_RECORD_COUNT = "extra.RECORD_COUNT";
    public static final String EXTRA_RECORD_IDS = "extra.RECORD_IDS";

    /**
     * This is used to represent either times for InstantRecords or start times for IntervalRecords.
     */
    public static final String EXTRA_TIMES = "extra.TIMES";

    public static final String EXTRA_END_TIMES = "extra.END_TIMES";

    /** Represents a list of values in {@code long}. */
    public static final String EXTRA_RECORD_VALUES = "extra.RECORD_VALUES";

    public static final String EXTRA_TOKEN = "extra.TOKEN";
    public static final String EXTRA_SENDER_PACKAGE_NAME = "extra.SENDER_PACKAGE_NAME";
    private static final String TEST_SUITE_RECEIVER =
            "android.healthconnect.cts.utils.TestReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        switch (intent.getAction()) {
            case ACTION_INSERT_STEPS_RECORDS:
                insertStepsRecords(context, intent);
                break;
            case ACTION_INSERT_WEIGHT_RECORDS:
                insertWeightRecords(context, intent);
                break;
            case ACTION_READ_RECORDS_FOR_OTHER_APP:
                readRecordsForOtherApp(context, intent);
                break;
            case ACTION_AGGREGATE:
                aggregate(context, intent);
                break;
            case ACTION_GET_CHANGE_LOG_TOKEN:
                getChangeLogToken(context, intent);
                break;
            case ACTION_GET_CHANGE_LOGS:
                getChangeLogs(context, intent);
                break;
            default:
                throw new IllegalStateException("Unsupported command: " + intent.getAction());
        }
    }

    private static void insertStepsRecords(Context context, Intent intent) {
        DefaultOutcomeReceiver<InsertRecordsResponse> outcome = new DefaultOutcomeReceiver<>();
        getHealthConnectManager(context)
                .insertRecords(createStepsRecords(intent), newSingleThreadExecutor(), outcome);
        sendInsertRecordsResult(context, intent, outcome);
    }

    private static void insertWeightRecords(Context context, Intent intent) {
        DefaultOutcomeReceiver<InsertRecordsResponse> outcome = new DefaultOutcomeReceiver<>();
        getHealthConnectManager(context)
                .insertRecords(createWeightRecords(intent), newSingleThreadExecutor(), outcome);
        sendInsertRecordsResult(context, intent, outcome);
    }

    private void readRecordsForOtherApp(Context context, Intent intent) {
        DefaultOutcomeReceiver<ReadRecordsResponse<ActiveCaloriesBurnedRecord>> outcome =
                new DefaultOutcomeReceiver<>();

        getHealthConnectManager(context)
                .readRecords(
                        new ReadRecordsRequestUsingFilters.Builder<>(
                                        ActiveCaloriesBurnedRecord.class)
                                .addDataOrigins(
                                        new DataOrigin.Builder()
                                                .setPackageName(getSenderPackageName(intent))
                                                .build())
                                .build(),
                        newSingleThreadExecutor(),
                        outcome);

        sendReadRecordsResult(context, intent, outcome);
    }

    private void aggregate(Context context, Intent intent) {
        DefaultOutcomeReceiver<AggregateRecordsResponse<Energy>> outcome =
                new DefaultOutcomeReceiver<>();

        getHealthConnectManager(context)
                .aggregate(
                        new AggregateRecordsRequest.Builder<Energy>(
                                        new TimeInstantRangeFilter.Builder()
                                                .setEndTime(Instant.now())
                                                .setStartTime(Instant.now().minusSeconds(10))
                                                .build())
                                .addAggregationType(ACTIVE_CALORIES_TOTAL)
                                .build(),
                        newSingleThreadExecutor(),
                        outcome);

        sendResult(context, intent, outcome);
    }

    private void getChangeLogToken(Context context, Intent intent) {
        DefaultOutcomeReceiver<ChangeLogTokenResponse> outcome = new DefaultOutcomeReceiver<>();

        getHealthConnectManager(context)
                .getChangeLogToken(
                        new ChangeLogTokenRequest.Builder()
                                .addRecordType(ActiveCaloriesBurnedRecord.class)
                                .build(),
                        newSingleThreadExecutor(),
                        outcome);

        final HealthConnectException error = outcome.getError();
        if (error == null) {
            final Bundle extras = new Bundle();
            extras.putString(EXTRA_TOKEN, outcome.getResult().getToken());
            sendSuccess(context, intent, extras);
        } else {
            sendError(context, intent, error);
        }
    }

    private void getChangeLogs(Context context, Intent intent) {
        String token = intent.getStringExtra(EXTRA_TOKEN);
        DefaultOutcomeReceiver<ChangeLogsResponse> outcome = new DefaultOutcomeReceiver<>();

        getHealthConnectManager(context)
                .getChangeLogs(
                        new ChangeLogsRequest.Builder(token).build(),
                        newSingleThreadExecutor(),
                        outcome);

        sendResult(context, intent, outcome);
    }

    private static HealthConnectManager getHealthConnectManager(Context context) {
        return requireNonNull(context.getSystemService(HealthConnectManager.class));
    }

    private static void sendReadRecordsResult(
            Context context,
            Intent intent,
            DefaultOutcomeReceiver<? extends ReadRecordsResponse<?>> outcome) {
        final HealthConnectException error = outcome.getError();
        if (error != null) {
            sendError(context, intent, error);
            return;
        }

        final Bundle extras = new Bundle();
        extras.putInt(EXTRA_RECORD_COUNT, outcome.getResult().getRecords().size());
        sendSuccess(context, intent, extras);
    }

    private static void sendInsertRecordsResult(
            Context context,
            Intent intent,
            DefaultOutcomeReceiver<? extends InsertRecordsResponse> outcome) {
        final HealthConnectException error = outcome.getError();
        if (error != null) {
            sendError(context, intent, error);
            return;
        }

        final Bundle extras = new Bundle();
        List<? extends Record> records = outcome.getResult().getRecords();
        ArrayList<String> recordIds =
                new ArrayList<>(
                        records.stream()
                                .map(Record::getMetadata)
                                .map(Metadata::getId)
                                .collect(Collectors.toList()));
        extras.putStringArrayList(EXTRA_RECORD_IDS, recordIds);
        extras.putInt(EXTRA_RECORD_COUNT, records.size());
        sendSuccess(context, intent, extras);
    }

    private static void sendResult(
            Context context, Intent intent, DefaultOutcomeReceiver<?> outcomeReceiver) {
        final HealthConnectException error = outcomeReceiver.getError();
        if (error != null) {
            sendError(context, intent, error);
            return;
        }
        sendSuccess(context, intent);
    }

    private static void sendSuccess(Context context, Intent intent) {
        context.sendBroadcast(getSuccessIntent(intent));
    }

    private static void sendSuccess(Context context, Intent intent, Bundle extras) {
        context.sendBroadcast(getSuccessIntent(intent).putExtras(extras));
    }

    private static Intent getSuccessIntent(Intent intent) {
        return new Intent(ACTION_RESULT_SUCCESS)
                .setClassName(getSenderPackageName(intent), TEST_SUITE_RECEIVER);
    }

    private static void sendError(Context context, Intent intent, HealthConnectException error) {
        context.sendBroadcast(
                new Intent(ACTION_RESULT_ERROR)
                        .setClassName(getSenderPackageName(intent), TEST_SUITE_RECEIVER)
                        .putExtra(EXTRA_RESULT_ERROR_CODE, error.getErrorCode())
                        .putExtra(EXTRA_RESULT_ERROR_MESSAGE, error.getMessage()));
    }

    private static List<Record> createStepsRecords(Intent intent) {
        List<Instant> startTimes = getTimes(intent, EXTRA_TIMES);
        List<Instant> endTimes = getTimes(intent, EXTRA_END_TIMES);
        long[] values = intent.getLongArrayExtra(EXTRA_RECORD_VALUES);

        List<Record> result = new ArrayList<>();
        for (int i = 0; i < startTimes.size(); i++) {
            result.add(createStepsRecord(startTimes.get(i), endTimes.get(i), values[i]));
        }
        return result;
    }

    private static StepsRecord createStepsRecord(Instant startTime, Instant endTime, long steps) {
        return new StepsRecord.Builder(new Metadata.Builder().build(), startTime, endTime, steps)
                .build();
    }

    private static List<Record> createWeightRecords(Intent intent) {
        List<Instant> times = getTimes(intent, EXTRA_TIMES);
        double[] values = intent.getDoubleArrayExtra(EXTRA_RECORD_VALUES);

        List<Record> result = new ArrayList<>();
        for (int i = 0; i < times.size(); i++) {
            result.add(createWeightRecord(times.get(i), values[i]));
        }
        return result;
    }

    private static WeightRecord createWeightRecord(Instant time, double weight) {
        return new WeightRecord.Builder(
                        new Metadata.Builder().build(), time, Mass.fromGrams(weight))
                .build();
    }

    private static List<Instant> getTimes(Intent intent, String key) {
        return Arrays.stream(intent.getLongArrayExtra(key))
                .mapToObj(Instant::ofEpochMilli)
                .collect(Collectors.toList());
    }

    private static String getSenderPackageName(Intent intent) {
        return intent.getStringExtra(EXTRA_SENDER_PACKAGE_NAME);
    }
}
