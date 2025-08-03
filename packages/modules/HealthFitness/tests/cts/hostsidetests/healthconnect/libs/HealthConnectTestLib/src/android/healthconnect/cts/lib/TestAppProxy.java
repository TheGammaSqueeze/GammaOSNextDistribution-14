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

package android.healthconnect.cts.lib;

import static android.healthconnect.cts.lib.BundleHelper.INTENT_EXCEPTION;
import static android.healthconnect.cts.lib.BundleHelper.KILL_SELF_REQUEST;
import static android.healthconnect.cts.lib.BundleHelper.QUERY_TYPE;

import static androidx.test.InstrumentationRegistry.getContext;

import android.app.Instrumentation;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.health.connect.ReadRecordsRequestUsingFilters;
import android.health.connect.ReadRecordsRequestUsingIds;
import android.health.connect.RecordIdFilter;
import android.health.connect.changelog.ChangeLogTokenRequest;
import android.health.connect.changelog.ChangeLogsRequest;
import android.health.connect.changelog.ChangeLogsResponse;
import android.health.connect.datatypes.Record;
import android.healthconnect.cts.utils.ProxyActivity;
import android.os.Bundle;

import com.android.cts.install.lib.TestApp;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

/** Performs API calls to HC on behalf of test apps. */
public class TestAppProxy {
    private static final String TEST_APP_RECEIVER_CLASS_NAME =
            "android.healthconnect.cts.testhelper.TestAppReceiver";
    private static final long POLLING_TIMEOUT_MILLIS = TimeUnit.SECONDS.toMillis(20);

    private final String mPackageName;
    private final boolean mInBackground;

    private TestAppProxy(String packageName, boolean inBackground) {
        mPackageName = packageName;
        mInBackground = inBackground;
    }

    /** Create a new {@link TestAppProxy} for given test app. */
    public static TestAppProxy forApp(TestApp testApp) {
        return forPackageName(testApp.getPackageName());
    }

    /** Create a new {@link TestAppProxy} for given package name. */
    public static TestAppProxy forPackageName(String packageName) {
        return new TestAppProxy(packageName, false);
    }

    /**
     * Create a new {@link TestAppProxy} for given package name which performs calls in the
     * background.
     */
    public static TestAppProxy forPackageNameInBackground(String packageName) {
        return new TestAppProxy(packageName, true);
    }

    /** Returns the package name of the app. */
    public String getPackageName() {
        return mPackageName;
    }

    /** Inserts a record to HC on behalf of the app. */
    public String insertRecord(Record record) throws Exception {
        return insertRecords(Collections.singletonList(record)).get(0);
    }

    /** Inserts records to HC on behalf of the app. */
    public List<String> insertRecords(Record... records) throws Exception {
        return insertRecords(Arrays.asList(records));
    }

    /** Inserts records to HC on behalf of the app. */
    public List<String> insertRecords(List<Record> records) throws Exception {
        Bundle requestBundle = BundleHelper.fromInsertRecordsRequest(records);
        Bundle responseBundle = getFromTestApp(requestBundle);
        return BundleHelper.toInsertRecordsResponse(responseBundle);
    }

    /** Deletes records from HC on behalf of the app. */
    public void deleteRecords(RecordIdFilter... recordIdFilters) throws Exception {
        deleteRecords(Arrays.asList(recordIdFilters));
    }

    /** Deletes records from HC on behalf of the app. */
    public void deleteRecords(List<RecordIdFilter> recordIdFilters) throws Exception {
        Bundle requestBundle = BundleHelper.fromDeleteRecordsByIdsRequest(recordIdFilters);
        getFromTestApp(requestBundle);
    }

    /** Updates records in HC on behalf of the app. */
    public void updateRecords(Record... records) throws Exception {
        updateRecords(Arrays.asList(records));
    }

    /** Updates records in HC on behalf of the app. */
    public void updateRecords(List<Record> records) throws Exception {
        Bundle requestBundle = BundleHelper.fromUpdateRecordsRequest(records);
        getFromTestApp(requestBundle);
    }

    /** Read records from HC on behalf of the app. */
    public <T extends Record> List<T> readRecords(ReadRecordsRequestUsingFilters<T> request)
            throws Exception {
        Bundle requestBundle = BundleHelper.fromReadRecordsRequestUsingFilters(request);
        Bundle responseBundle = getFromTestApp(requestBundle);
        return BundleHelper.toReadRecordsResponse(responseBundle);
    }

    /** Read records from HC on behalf of the app. */
    public <T extends Record> List<T> readRecords(ReadRecordsRequestUsingIds<T> request)
            throws Exception {
        Bundle requestBundle = BundleHelper.fromReadRecordsRequestUsingIds(request);
        Bundle responseBundle = getFromTestApp(requestBundle);
        return BundleHelper.toReadRecordsResponse(responseBundle);
    }

    /** Gets changelogs from HC on behalf of the app. */
    public ChangeLogsResponse getChangeLogs(ChangeLogsRequest request) throws Exception {
        Bundle requestBundle = BundleHelper.fromChangeLogsRequest(request);
        Bundle responseBundle = getFromTestApp(requestBundle);
        return BundleHelper.toChangeLogsResponse(responseBundle);
    }

    /** Gets a change log token from HC on behalf of the app. */
    public String getChangeLogToken(ChangeLogTokenRequest request) throws Exception {
        Bundle requestBundle = BundleHelper.fromChangeLogTokenRequest(request);
        Bundle responseBundle = getFromTestApp(requestBundle);
        return BundleHelper.toChangeLogTokenResponse(responseBundle);
    }

    /** Instructs the app to self-revokes the specified permission. */
    public void selfRevokePermission(String permission) throws Exception {
        Bundle requestBundle = BundleHelper.forSelfRevokePermissionRequest(permission);
        getFromTestApp(requestBundle);
    }

    /** Instructs the app to kill itself. */
    public void kill() throws Exception {
        Bundle requestBundle = BundleHelper.forKillSelfRequest();
        getFromTestApp(requestBundle);
    }

    /** Starts an activity on behalf of the app and returns the result. */
    public Instrumentation.ActivityResult startActivityForResult(Intent intent) throws Exception {
        return startActivityForResult(intent, () -> {});
    }

    /**
     * Starts an activity on behalf of the app, executes the runnable and returns the result.
     *
     * <p>The corresponding test app must have the following activity declared in the Manifest.
     *
     * <pre>{@code
     * <activity android:name="android.healthconnect.cts.utils.ProxyActivity"
     *           android:exported="true">
     *   <intent-filter>
     *      <action android:name="android.healthconnect.cts.ACTION_START_ACTIVITY_FOR_RESULT"/>
     *      <category android:name="android.intent.category.DEFAULT"/>
     *   </intent-filter>
     * </activity>
     * }</pre>
     */
    public Instrumentation.ActivityResult startActivityForResult(Intent intent, Runnable runnable)
            throws Exception {
        Intent testAppIntent = new Intent(ProxyActivity.PROXY_ACTIVITY_ACTION);
        testAppIntent.setPackage(mPackageName);
        testAppIntent.putExtra(Intent.EXTRA_INTENT, intent);

        return ProxyActivity.launchActivityForResult(testAppIntent, runnable);
    }

    private Bundle getFromTestApp(Bundle bundleToCreateIntent) throws Exception {
        final CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Bundle> response = new AtomicReference<>();
        AtomicReference<Exception> exceptionAtomicReference = new AtomicReference<>();
        BroadcastReceiver broadcastReceiver =
                new BroadcastReceiver() {
                    @Override
                    public void onReceive(Context context, Intent intent) {
                        if (intent.hasExtra(INTENT_EXCEPTION)) {
                            exceptionAtomicReference.set(
                                    (Exception) (intent.getSerializableExtra(INTENT_EXCEPTION)));
                        } else {
                            response.set(intent.getExtras());
                        }
                        latch.countDown();
                    }
                };

        launchTestApp(bundleToCreateIntent, broadcastReceiver, latch);
        if (exceptionAtomicReference.get() != null) {
            throw exceptionAtomicReference.get();
        }
        return response.get();
    }

    private void launchTestApp(
            Bundle bundleToCreateIntent, BroadcastReceiver broadcastReceiver, CountDownLatch latch)
            throws Exception {

        // Register broadcast receiver
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(bundleToCreateIntent.getString(QUERY_TYPE));
        intentFilter.addCategory(Intent.CATEGORY_DEFAULT);
        getContext().registerReceiver(broadcastReceiver, intentFilter, Context.RECEIVER_EXPORTED);

        // Launch the test app.
        Intent intent;

        if (mInBackground) {
            intent = new Intent().setClassName(mPackageName, TEST_APP_RECEIVER_CLASS_NAME);
        } else {
            intent = new Intent(Intent.ACTION_MAIN);
            intent.setPackage(mPackageName);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            intent.addCategory(Intent.CATEGORY_LAUNCHER);
        }

        intent.putExtras(bundleToCreateIntent);

        Thread.sleep(500);

        if (mInBackground) {
            getContext().sendBroadcast(intent);
        } else {
            getContext().startActivity(intent);
        }

        // We don't wait for responses to kill requests. These kill the app & there is no easy or
        // reliable way for the app to return a broadcast before being killed.
        boolean isKillRequest =
                bundleToCreateIntent.getString(QUERY_TYPE).equals(KILL_SELF_REQUEST);
        if (!isKillRequest && !latch.await(POLLING_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)) {
            final String errorMessage =
                    "Timed out while waiting to receive "
                            + bundleToCreateIntent.getString(QUERY_TYPE)
                            + " intent from "
                            + mPackageName;
            throw new TimeoutException(errorMessage);
        }
        getContext().unregisterReceiver(broadcastReceiver);
    }
}
