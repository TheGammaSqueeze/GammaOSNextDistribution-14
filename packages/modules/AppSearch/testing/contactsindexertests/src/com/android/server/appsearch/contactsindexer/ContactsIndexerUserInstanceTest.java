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

package com.android.server.appsearch.contactsindexer;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;

import android.annotation.NonNull;
import android.app.appsearch.AppSearchManager;
import android.app.appsearch.AppSearchResult;
import android.app.appsearch.AppSearchSessionShim;
import android.app.appsearch.GlobalSearchSessionShim;
import android.app.appsearch.SetSchemaRequest;
import android.app.appsearch.observer.DocumentChangeInfo;
import android.app.appsearch.observer.ObserverCallback;
import android.app.appsearch.observer.ObserverSpec;
import android.app.appsearch.observer.SchemaChangeInfo;
import android.app.appsearch.testutil.AppSearchSessionShimImpl;
import android.app.appsearch.testutil.GlobalSearchSessionShimImpl;
import android.app.appsearch.testutil.TestContactsIndexerConfig;
import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.os.CancellationSignal;
import android.os.PersistableBundle;
import android.provider.ContactsContract;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.dx.mockito.inline.extended.ExtendedMockito;
import com.android.dx.mockito.inline.extended.StaticMockitoSessionBuilder;
import com.android.modules.utils.testing.ExtendedMockitoRule;
import com.android.modules.utils.testing.StaticMockFixture;
import com.android.server.appsearch.contactsindexer.appsearchtypes.Person;
import com.android.server.appsearch.stats.AppSearchStatsLog;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.io.File;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

@RunWith(AndroidJUnit4.class)
public class ContactsIndexerUserInstanceTest extends FakeContactsProviderTestBase {
    @Rule
    public TemporaryFolder mTemporaryFolder = new TemporaryFolder();

    @Rule
    public ExtendedMockitoRule mExtendedMockitoRule = new ExtendedMockitoRule.Builder()
            .addStaticMockFixtures(TestMockFixture::new)
            .build();

    private final ExecutorService mSingleThreadedExecutor = Executors.newSingleThreadExecutor();
    private File mContactsDir;
    private File mSettingsFile;
    private ContactsIndexerUserInstance mInstance;
    private ContactsUpdateStats mUpdateStats;
    private ContactsIndexerConfig mConfigForTest = new TestContactsIndexerConfig();

    @Override
    @Before
    public void setUp() throws Exception {
        super.setUp();

        // Setup the file path to the persisted data
        mContactsDir = new File(mTemporaryFolder.newFolder(), "appsearch/contacts");
        mSettingsFile = new File(mContactsDir, ContactsIndexerSettings.SETTINGS_FILE_NAME);
        mInstance = ContactsIndexerUserInstance.createInstance(mContext, mContactsDir,
                mConfigForTest, mSingleThreadedExecutor);
        mUpdateStats = new ContactsUpdateStats();
    }

    @Override
    @After
    public void tearDown() throws Exception {
        // Wipe the data in AppSearchHelper.DATABASE_NAME.
        AppSearchManager.SearchContext searchContext =
                new AppSearchManager.SearchContext.Builder(AppSearchHelper.DATABASE_NAME).build();
        AppSearchSessionShim db = AppSearchSessionShimImpl.createSearchSessionAsync(
                searchContext).get();
        SetSchemaRequest setSchemaRequest = new SetSchemaRequest.Builder()
                .setForceOverride(true).build();
        db.setSchemaAsync(setSchemaRequest).get();
        super.tearDown();
    }

    @Test
    public void testHandleMultipleNotifications_onlyOneDeltaUpdateCanBeScheduledAndRun()
            throws Exception {
        try {
            long dataQueryDelayMs = 5000;
            mFakeContactsProvider.setDataQueryDelayMs(dataQueryDelayMs);
            BlockingQueue<Runnable> queue = new LinkedBlockingQueue<>();
            ThreadPoolExecutor singleThreadedExecutor =
                    new ThreadPoolExecutor(/*corePoolSize=*/1, /*maximumPoolSize=*/
                            1, /*KeepAliveTime=*/ 0L, TimeUnit.MILLISECONDS, queue);
            ContactsIndexerUserInstance instance = ContactsIndexerUserInstance.createInstance(
                    mContext, mContactsDir,
                    mConfigForTest, singleThreadedExecutor);

            int numOfNotifications = 20;
            for (int i = 0; i < numOfNotifications / 2; ++i) {
                int docCount = 2;
                // Insert contacts to trigger delta update.
                ContentResolver resolver = mContext.getContentResolver();
                ContentValues dummyValues = new ContentValues();
                for (int j = 0; j < docCount; j++) {
                    resolver.insert(ContactsContract.Contacts.CONTENT_URI, dummyValues);
                }
                instance.handleDeltaUpdate();
            }
            // Sleep here so the active delta update can be run for some time. While
            // notifications come before and afterwards.
            Thread.sleep(1500);
            long totalTaskAfterFirstDeltaUpdate = singleThreadedExecutor.getTaskCount();
            for (int i = 0; i < numOfNotifications / 2; ++i) {
                int docCount = 2;
                // Insert contacts to trigger delta update.
                ContentResolver resolver = mContext.getContentResolver();
                ContentValues dummyValues = new ContentValues();
                for (int j = 0; j < docCount; j++) {
                    resolver.insert(ContactsContract.Contacts.CONTENT_URI, dummyValues);
                }
                instance.handleDeltaUpdate();
            }

            // The total task count will be increased if there is another delta update scheduled.
            assertThat(singleThreadedExecutor.getTaskCount()).isEqualTo(
                    totalTaskAfterFirstDeltaUpdate);
        } finally {
            mFakeContactsProvider.setDataQueryDelayMs(0);
        }
    }

    @Test
    public void testHandleNotificationDuringUpdate_oneAdditionalUpdateWillBeRun()
            throws Exception {
        try {
            long dataQueryDelayMs = 5000;
            mFakeContactsProvider.setDataQueryDelayMs(dataQueryDelayMs);
            BlockingQueue<Runnable> queue = new LinkedBlockingQueue<>();
            ThreadPoolExecutor singleThreadedExecutor =
                    new ThreadPoolExecutor(/*corePoolSize=*/1, /*maximumPoolSize=*/
                            1, /*KeepAliveTime=*/ 0L, TimeUnit.MILLISECONDS, queue);
            ContactsIndexerUserInstance instance = ContactsIndexerUserInstance.createInstance(
                    mContext, mContactsDir,
                    mConfigForTest, singleThreadedExecutor);
            int docCount = 10;
            // Insert contacts to trigger delta update.
            ContentResolver resolver = mContext.getContentResolver();
            ContentValues dummyValues = new ContentValues();

            for (int j = 0; j < docCount; j++) {
                resolver.insert(ContactsContract.Contacts.CONTENT_URI, dummyValues);
            }
            instance.handleDeltaUpdate();
            // Sleep here so the active delta update can be run for some time to make sure
            // notification come during an update.
            Thread.sleep(1500);
            long totalTaskAfterFirstDeltaUpdate = singleThreadedExecutor.getTaskCount();
            // Insert contacts to trigger delta update.
            for (int j = 0; j < docCount; j++) {
                resolver.insert(ContactsContract.Contacts.CONTENT_URI, dummyValues);
            }
            instance.handleDeltaUpdate();

            //The 2nd update won't be scheduled right away.
            assertThat(singleThreadedExecutor.getTaskCount()).isEqualTo(
                    totalTaskAfterFirstDeltaUpdate);

            // To make sure the 1st update has been finished.
            Thread.sleep(dataQueryDelayMs);

            // This means additional task has been run by the 1st delta update to handle
            // the change notification.
            assertThat(singleThreadedExecutor.getActiveCount()).isEqualTo(1);
        } finally {
            mFakeContactsProvider.setDataQueryDelayMs(0);
        }
    }

    @Test
    public void testCreateInstance_dataDirectoryCreatedAsynchronously() throws Exception {
        File dataDir = new File(mTemporaryFolder.newFolder(), "contacts");
        boolean isDataDirectoryCreatedSynchronously = mSingleThreadedExecutor.submit(() -> {
            ContactsIndexerUserInstance unused =
                    ContactsIndexerUserInstance.createInstance(mContext, dataDir, mConfigForTest,
                            mSingleThreadedExecutor);
            // Data directory shouldn't have been created synchronously in createInstance()
            return dataDir.exists();
        }).get();
        assertThat(isDataDirectoryCreatedSynchronously).isFalse();
        boolean isDataDirectoryCreatedAsynchronously = mSingleThreadedExecutor.submit(
                dataDir::exists).get();
        assertThat(isDataDirectoryCreatedAsynchronously).isTrue();
    }

    @Test
    public void testStart_initialRun_schedulesFullUpdateJob() throws Exception {
        JobScheduler mockJobScheduler = mock(JobScheduler.class);
        mContext.setJobScheduler(mockJobScheduler);
        ContactsIndexerUserInstance instance = ContactsIndexerUserInstance.createInstance(
                mContext,
                mContactsDir, mConfigForTest, mSingleThreadedExecutor);

        int docCount = 100;
        CountDownLatch latch = new CountDownLatch(docCount);
        GlobalSearchSessionShim shim =
                GlobalSearchSessionShimImpl.createGlobalSearchSessionAsync(mContext).get();
        ObserverCallback callback = new ObserverCallback() {
            @Override
            public void onSchemaChanged(SchemaChangeInfo changeInfo) {
                // Do nothing
            }

            @Override
            public void onDocumentChanged(DocumentChangeInfo changeInfo) {
                for (int i = 0; i < changeInfo.getChangedDocumentIds().size(); i++) {
                    latch.countDown();
                }
            }
        };
        shim.registerObserverCallback(mContext.getPackageName(),
                new ObserverSpec.Builder().addFilterSchemas("builtin:Person").build(),
                mSingleThreadedExecutor,
                callback);
        // Insert contacts to trigger delta update.
        ContentResolver resolver = mContext.getContentResolver();
        ContentValues dummyValues = new ContentValues();
        for (int i = 0; i < docCount; i++) {
            resolver.insert(ContactsContract.Contacts.CONTENT_URI, dummyValues);
        }

        try {
            instance.startAsync();

            // Wait for all async tasks to complete
            latch.await(30L, TimeUnit.SECONDS);

            ArgumentCaptor<JobInfo> jobInfoArgumentCaptor = ArgumentCaptor.forClass(JobInfo.class);
            verify(mockJobScheduler).schedule(jobInfoArgumentCaptor.capture());
            JobInfo fullUpdateJob = jobInfoArgumentCaptor.getValue();
            assertThat(fullUpdateJob.isRequireBatteryNotLow()).isTrue();
            assertThat(fullUpdateJob.isRequireDeviceIdle()).isTrue();
            assertThat(fullUpdateJob.isPersisted()).isTrue();
            assertThat(fullUpdateJob.isPeriodic()).isFalse();
        } finally {
            instance.shutdown();
        }
    }

    @Test
    public void testStart_subsequentRunWithNoScheduledJob_schedulesFullUpdateJob()
            throws Exception {
        // Trigger an initial full update.
        executeAndWaitForCompletion(
                mInstance.doFullUpdateInternalAsync(new CancellationSignal(), mUpdateStats),
                mSingleThreadedExecutor);

        // By default mockJobScheduler.getPendingJob() would return null. This simulates the
        // scenario where the scheduled full update job after the initial run is cancelled
        // due to some reason.
        JobScheduler mockJobScheduler = mock(JobScheduler.class);
        mContext.setJobScheduler(mockJobScheduler);
        ContactsIndexerUserInstance instance = ContactsIndexerUserInstance.createInstance(
                mContext, mContactsDir, mConfigForTest, mSingleThreadedExecutor);

        int docCount = 100;
        CountDownLatch latch = new CountDownLatch(docCount);
        GlobalSearchSessionShim shim =
                GlobalSearchSessionShimImpl.createGlobalSearchSessionAsync(mContext).get();
        ObserverCallback callback = new ObserverCallback() {
            @Override
            public void onSchemaChanged(SchemaChangeInfo changeInfo) {
                // Do nothing
            }

            @Override
            public void onDocumentChanged(DocumentChangeInfo changeInfo) {
                for (int i = 0; i < changeInfo.getChangedDocumentIds().size(); i++) {
                    latch.countDown();
                }
            }
        };
        shim.registerObserverCallback(mContext.getPackageName(),
                new ObserverSpec.Builder().addFilterSchemas("builtin:Person").build(),
                mSingleThreadedExecutor,
                callback);
        // Insert contacts to trigger delta update.
        ContentResolver resolver = mContext.getContentResolver();
        ContentValues dummyValues = new ContentValues();
        for (int i = 0; i < docCount; i++) {
            resolver.insert(ContactsContract.Contacts.CONTENT_URI, dummyValues);
        }

        try {
            instance.startAsync();

            // Wait for all async tasks to complete
            latch.await(30L, TimeUnit.SECONDS);

            ArgumentCaptor<JobInfo> jobInfoArgumentCaptor = ArgumentCaptor.forClass(JobInfo.class);
            verify(mockJobScheduler).schedule(jobInfoArgumentCaptor.capture());
            JobInfo fullUpdateJob = jobInfoArgumentCaptor.getValue();
            assertThat(fullUpdateJob.isRequireBatteryNotLow()).isTrue();
            assertThat(fullUpdateJob.isRequireDeviceIdle()).isTrue();
            assertThat(fullUpdateJob.isPersisted()).isTrue();
            assertThat(fullUpdateJob.isPeriodic()).isFalse();
        } finally {
            instance.shutdown();
        }
    }

    @Test
    public void testStart_subsequentRunWithScheduledJob_doesNotScheduleFullUpdateJob()
            throws Exception {
        // Trigger an initial full update.
        executeAndWaitForCompletion(
                mInstance.doFullUpdateInternalAsync(new CancellationSignal(), mUpdateStats),
                mSingleThreadedExecutor);

        JobScheduler mockJobScheduler = mock(JobScheduler.class);
        JobInfo mockJobInfo = mock(JobInfo.class);
        // getPendingJob() should return a non-null value to simulate the scenario where a
        // background job is already scheduled.
        doReturn(mockJobInfo).when(mockJobScheduler).getPendingJob(
                ContactsIndexerMaintenanceService.MIN_INDEXER_JOB_ID +
                        mContext.getUser().getIdentifier());
        mContext.setJobScheduler(mockJobScheduler);
        ContactsIndexerUserInstance instance = ContactsIndexerUserInstance.createInstance(
                mContext, mContactsDir, mConfigForTest, mSingleThreadedExecutor);

        int docCount = 100;
        CountDownLatch latch = new CountDownLatch(docCount);
        GlobalSearchSessionShim shim =
                GlobalSearchSessionShimImpl.createGlobalSearchSessionAsync(mContext).get();
        ObserverCallback callback = new ObserverCallback() {
            @Override
            public void onSchemaChanged(SchemaChangeInfo changeInfo) {
                // Do nothing
            }

            @Override
            public void onDocumentChanged(DocumentChangeInfo changeInfo) {
                for (int i = 0; i < changeInfo.getChangedDocumentIds().size(); i++) {
                    latch.countDown();
                }
            }
        };
        shim.registerObserverCallback(mContext.getPackageName(),
                new ObserverSpec.Builder().addFilterSchemas("builtin:Person").build(),
                mSingleThreadedExecutor,
                callback);
        // Insert contacts to trigger delta update.
        ContentResolver resolver = mContext.getContentResolver();
        ContentValues dummyValues = new ContentValues();
        for (int i = 0; i < docCount; i++) {
            resolver.insert(ContactsContract.Contacts.CONTENT_URI, dummyValues);
        }

        try {
            instance.startAsync();

            // Wait for all async tasks to complete
            latch.await(30L, TimeUnit.SECONDS);

            verify(mockJobScheduler, never()).schedule(any());
        } finally {
            instance.shutdown();
        }
    }

    @Test
    public void testFullUpdate() throws Exception {
        ContentResolver resolver = mContext.getContentResolver();
        ContentValues dummyValues = new ContentValues();
        for (int i = 0; i < 500; i++) {
            resolver.insert(ContactsContract.Contacts.CONTENT_URI, dummyValues);
        }

        executeAndWaitForCompletion(
                mInstance.doFullUpdateInternalAsync(new CancellationSignal(), mUpdateStats),
                mSingleThreadedExecutor);

        AppSearchHelper searchHelper = AppSearchHelper.createAppSearchHelper(mContext,
                mSingleThreadedExecutor, mConfigForTest);
        List<String> contactIds = searchHelper.getAllContactIdsAsync().get();
        assertThat(contactIds.size()).isEqualTo(500);
    }

    @Test
    public void testFullUpdate_setsPreviousLastContactUpdatedTimestamp() throws Exception {
        long timeBeforeDeltaChangeNotification = System.currentTimeMillis();
        // Insert contact
        ContentResolver resolver = mContext.getContentResolver();
        ContentValues dummyValues = new ContentValues();
        resolver.insert(ContactsContract.Contacts.CONTENT_URI, dummyValues);

        executeAndWaitForCompletion(
                mInstance.doDeltaUpdateAsync(/*indexingLimit=*/ -1, mUpdateStats),
                mSingleThreadedExecutor);

        // check that delta update set the last contact updated timestamp (but not the previous one)
        assertThat(mUpdateStats.mLastContactUpdatedTimeMillis).isAtLeast(
                timeBeforeDeltaChangeNotification);
        assertThat(mUpdateStats.mPreviousLastContactUpdatedTimeMillis).isEqualTo(0);

        // Insert another contact
        resolver.insert(ContactsContract.Contacts.CONTENT_URI, dummyValues);

        ContactsUpdateStats updateStats = new ContactsUpdateStats();
        executeAndWaitForCompletion(
                mInstance.doFullUpdateInternalAsync(new CancellationSignal(), updateStats),
                mSingleThreadedExecutor);

        // check that full update set the previous last contact updated timestamp
        assertThat(updateStats.mLastContactUpdatedTimeMillis).isAtLeast(
                mUpdateStats.mLastContactUpdatedTimeMillis);
        assertThat(updateStats.mPreviousLastContactUpdatedTimeMillis).isEqualTo(
                mUpdateStats.mLastContactUpdatedTimeMillis);
    }

    @Test
    public void testDeltaUpdate_insertedContacts() throws Exception {
        long timeBeforeDeltaChangeNotification = System.currentTimeMillis();
        // Insert contacts to trigger delta update.
        ContentResolver resolver = mContext.getContentResolver();
        ContentValues dummyValues = new ContentValues();
        for (int i = 0; i < 250; i++) {
            resolver.insert(ContactsContract.Contacts.CONTENT_URI, dummyValues);
        }

        executeAndWaitForCompletion(mInstance.doDeltaUpdateAsync(/*indexingLimit=*/ -1,
                        mUpdateStats),
                mSingleThreadedExecutor);

        AppSearchHelper searchHelper = AppSearchHelper.createAppSearchHelper(mContext,
                mSingleThreadedExecutor, mConfigForTest);
        List<String> contactIds = searchHelper.getAllContactIdsAsync().get();
        assertThat(contactIds.size()).isEqualTo(250);

        PersistableBundle settingsBundle = ContactsIndexerSettings.readBundle(mSettingsFile);
        assertThat(settingsBundle.getLong(ContactsIndexerSettings.LAST_DELTA_UPDATE_TIMESTAMP_KEY))
                .isAtLeast(timeBeforeDeltaChangeNotification);
        assertThat(
                settingsBundle.getLong(ContactsIndexerSettings.LAST_CONTACT_UPDATE_TIMESTAMP_KEY))
                .isAtLeast(timeBeforeDeltaChangeNotification);
        // check stats
        assertThat(mUpdateStats.mUpdateType).isEqualTo(ContactsUpdateStats.DELTA_UPDATE);
        assertThat(mUpdateStats.mUpdateStatuses).hasSize(1);
        assertThat(mUpdateStats.mUpdateStatuses).containsExactly(AppSearchResult.RESULT_OK);
        assertThat(mUpdateStats.mDeleteStatuses).hasSize(1);
        assertThat(mUpdateStats.mDeleteStatuses).containsExactly(AppSearchResult.RESULT_OK);
        assertThat(mUpdateStats.mContactsUpdateFailedCount).isEqualTo(0);
        assertThat(mUpdateStats.mContactsDeleteFailedCount).isEqualTo(0);
        assertThat(mUpdateStats.mContactsDeleteNotFoundCount).isEqualTo(0);
        assertThat(mUpdateStats.mNewContactsToBeUpdated).isEqualTo(250);
        assertThat(mUpdateStats.mContactsUpdateSkippedCount).isEqualTo(0);
        assertThat(mUpdateStats.mTotalContactsToBeUpdated).isEqualTo(250);
        assertThat(mUpdateStats.mContactsUpdateSucceededCount).isEqualTo(250);
        assertThat(mUpdateStats.mTotalContactsToBeDeleted).isEqualTo(0);
        assertThat(mUpdateStats.mContactsDeleteSucceededCount).isEqualTo(0);
        // check timestamps
        assertThat(mUpdateStats.mUpdateAndDeleteStartTimeMillis).isEqualTo(settingsBundle.getLong(
                ContactsIndexerSettings.LAST_DELTA_UPDATE_TIMESTAMP_KEY));
        assertThat(mUpdateStats.mLastDeltaUpdateStartTimeMillis).isLessThan(settingsBundle.getLong(
                ContactsIndexerSettings.LAST_DELTA_UPDATE_TIMESTAMP_KEY));
        assertThat(mUpdateStats.mLastContactUpdatedTimeMillis).isEqualTo(
                settingsBundle.getLong(ContactsIndexerSettings.LAST_CONTACT_UPDATE_TIMESTAMP_KEY));
        assertThat(mUpdateStats.mLastContactDeletedTimeMillis).isEqualTo(
                settingsBundle.getLong(ContactsIndexerSettings.LAST_CONTACT_DELETE_TIMESTAMP_KEY));
    }

    @Test
    public void testDeltaUpdateWithLimit_fewerContactsIndexed() throws Exception {
        // Insert contacts to trigger delta update.
        ContentResolver resolver = mContext.getContentResolver();
        ContentValues dummyValues = new ContentValues();
        for (int i = 0; i < 250; i++) {
            resolver.insert(ContactsContract.Contacts.CONTENT_URI, dummyValues);
        }

        executeAndWaitForCompletion(mInstance.doDeltaUpdateAsync(/*indexingLimit=*/ 100,
                        mUpdateStats),
                mSingleThreadedExecutor);

        AppSearchHelper searchHelper = AppSearchHelper.createAppSearchHelper(mContext,
                mSingleThreadedExecutor, mConfigForTest);
        List<String> contactIds = searchHelper.getAllContactIdsAsync().get();
        assertThat(contactIds.size()).isEqualTo(100);
    }

    @Test
    public void testDeltaUpdate_deletedContacts() throws Exception {
        long timeBeforeDeltaChangeNotification = System.currentTimeMillis();
        // Insert contacts to trigger delta update.
        ContentResolver resolver = mContext.getContentResolver();
        ContentValues dummyValues = new ContentValues();
        for (int i = 0; i < 10; i++) {
            resolver.insert(ContactsContract.Contacts.CONTENT_URI, dummyValues);
        }

        executeAndWaitForCompletion(mInstance.doDeltaUpdateAsync(/*indexingLimit=*/ -1,
                        mUpdateStats),
                mSingleThreadedExecutor);

        // Delete a few contacts to trigger delta update.
        resolver.delete(ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, 2),
                /*extras=*/ null);
        resolver.delete(ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, 3),
                /*extras=*/ null);
        resolver.delete(ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, 5),
                /*extras=*/ null);
        resolver.delete(ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, 7),
                /*extras=*/ null);

        executeAndWaitForCompletion(mInstance.doDeltaUpdateAsync(/*indexingLimit=*/ -1,
                        mUpdateStats),
                mSingleThreadedExecutor);

        AppSearchHelper searchHelper = AppSearchHelper.createAppSearchHelper(mContext,
                mSingleThreadedExecutor, mConfigForTest);
        List<String> contactIds = searchHelper.getAllContactIdsAsync().get();
        assertThat(contactIds.size()).isEqualTo(6);
        assertThat(contactIds).containsNoneOf("2", "3", "5", "7");

        PersistableBundle settingsBundle = ContactsIndexerSettings.readBundle(mSettingsFile);
        assertThat(settingsBundle.getLong(ContactsIndexerSettings.LAST_DELTA_UPDATE_TIMESTAMP_KEY))
                .isAtLeast(timeBeforeDeltaChangeNotification);
        assertThat(
                settingsBundle.getLong(ContactsIndexerSettings.LAST_CONTACT_DELETE_TIMESTAMP_KEY))
                .isAtLeast(timeBeforeDeltaChangeNotification);
        // check stats
        assertThat(mUpdateStats.mUpdateType).isEqualTo(ContactsUpdateStats.DELTA_UPDATE);
        assertThat(mUpdateStats.mUpdateStatuses).hasSize(1);
        assertThat(mUpdateStats.mUpdateStatuses).containsExactly(AppSearchResult.RESULT_OK);
        assertThat(mUpdateStats.mDeleteStatuses).hasSize(1);
        assertThat(mUpdateStats.mDeleteStatuses).containsExactly(AppSearchResult.RESULT_OK);
        assertThat(mUpdateStats.mContactsUpdateFailedCount).isEqualTo(0);
        assertThat(mUpdateStats.mContactsDeleteFailedCount).isEqualTo(0);
        assertThat(mUpdateStats.mContactsDeleteNotFoundCount).isEqualTo(0);
        assertThat(mUpdateStats.mNewContactsToBeUpdated).isEqualTo(10);
        assertThat(mUpdateStats.mContactsUpdateSkippedCount).isEqualTo(0);
        assertThat(mUpdateStats.mTotalContactsToBeUpdated).isEqualTo(10);
        assertThat(mUpdateStats.mContactsUpdateSucceededCount).isEqualTo(10);
        assertThat(mUpdateStats.mTotalContactsToBeDeleted).isEqualTo(4);
        assertThat(mUpdateStats.mContactsDeleteSucceededCount).isEqualTo(4);
        // check timestamps
        assertThat(mUpdateStats.mUpdateAndDeleteStartTimeMillis).isEqualTo(settingsBundle.getLong(
                ContactsIndexerSettings.LAST_DELTA_UPDATE_TIMESTAMP_KEY));
        assertThat(mUpdateStats.mLastDeltaUpdateStartTimeMillis).isLessThan(settingsBundle.getLong(
                ContactsIndexerSettings.LAST_DELTA_UPDATE_TIMESTAMP_KEY));
        assertThat(mUpdateStats.mLastContactUpdatedTimeMillis).isEqualTo(
                settingsBundle.getLong(ContactsIndexerSettings.LAST_CONTACT_UPDATE_TIMESTAMP_KEY));
        assertThat(mUpdateStats.mLastContactDeletedTimeMillis).isEqualTo(
                settingsBundle.getLong(ContactsIndexerSettings.LAST_CONTACT_DELETE_TIMESTAMP_KEY));
    }

    @Test
    public void testDeltaUpdate_insertedAndDeletedContacts() throws Exception {
        long timeBeforeDeltaChangeNotification = System.currentTimeMillis();
        // Insert contacts to trigger delta update.
        ContentResolver resolver = mContext.getContentResolver();
        ContentValues dummyValues = new ContentValues();
        for (int i = 0; i < 10; i++) {
            resolver.insert(ContactsContract.Contacts.CONTENT_URI, dummyValues);
        }

        // Delete a few contacts to trigger delta update.
        resolver.delete(ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, 2),
                /*extras=*/ null);
        resolver.delete(ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, 3),
                /*extras=*/ null);
        resolver.delete(ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, 5),
                /*extras=*/ null);
        resolver.delete(ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, 7),
                /*extras=*/ null);

        mUpdateStats.clear();
        executeAndWaitForCompletion(mInstance.doDeltaUpdateAsync(/*indexingLimit=*/ -1,
                        mUpdateStats),
                mSingleThreadedExecutor);

        AppSearchHelper searchHelper = AppSearchHelper.createAppSearchHelper(mContext,
                mSingleThreadedExecutor, mConfigForTest);
        List<String> contactIds = searchHelper.getAllContactIdsAsync().get();
        assertThat(contactIds.size()).isEqualTo(6);
        assertThat(contactIds).containsNoneOf("2", "3", "5", "7");

        PersistableBundle settingsBundle = ContactsIndexerSettings.readBundle(mSettingsFile);
        assertThat(settingsBundle.getLong(ContactsIndexerSettings.LAST_DELTA_UPDATE_TIMESTAMP_KEY))
                .isAtLeast(timeBeforeDeltaChangeNotification);
        assertThat(
                settingsBundle.getLong(ContactsIndexerSettings.LAST_CONTACT_UPDATE_TIMESTAMP_KEY))
                .isAtLeast(timeBeforeDeltaChangeNotification);
        assertThat(
                settingsBundle.getLong(ContactsIndexerSettings.LAST_CONTACT_DELETE_TIMESTAMP_KEY))
                .isAtLeast(timeBeforeDeltaChangeNotification);
        // check stats
        assertThat(mUpdateStats.mUpdateType).isEqualTo(ContactsUpdateStats.DELTA_UPDATE);
        assertThat(mUpdateStats.mUpdateStatuses).hasSize(1);
        assertThat(mUpdateStats.mUpdateStatuses).containsExactly(AppSearchResult.RESULT_OK);
        assertThat(mUpdateStats.mDeleteStatuses).hasSize(1);
        assertThat(mUpdateStats.mDeleteStatuses).containsExactly(AppSearchResult.RESULT_NOT_FOUND);
        assertThat(mUpdateStats.mContactsUpdateFailedCount).isEqualTo(0);
        // 4 contacts deleted in CP2, but we don't have those in AppSearch. So we will get
        // NOT_FOUND.
        assertThat(mUpdateStats.mContactsDeleteFailedCount).isEqualTo(4);
        assertThat(mUpdateStats.mContactsDeleteNotFoundCount).isEqualTo(4);
        assertThat(mUpdateStats.mNewContactsToBeUpdated).isEqualTo(6);
        assertThat(mUpdateStats.mContactsUpdateSkippedCount).isEqualTo(0);
        assertThat(mUpdateStats.mTotalContactsToBeUpdated).isEqualTo(6);
        assertThat(mUpdateStats.mContactsUpdateSucceededCount).isEqualTo(6);
        assertThat(mUpdateStats.mTotalContactsToBeDeleted).isEqualTo(4);
        assertThat(mUpdateStats.mContactsDeleteSucceededCount).isEqualTo(0);
        // check timestamps
        assertThat(mUpdateStats.mUpdateAndDeleteStartTimeMillis).isEqualTo(settingsBundle.getLong(
                ContactsIndexerSettings.LAST_DELTA_UPDATE_TIMESTAMP_KEY));
        assertThat(mUpdateStats.mLastDeltaUpdateStartTimeMillis).isLessThan(settingsBundle.getLong(
                ContactsIndexerSettings.LAST_DELTA_UPDATE_TIMESTAMP_KEY));
        assertThat(mUpdateStats.mLastContactUpdatedTimeMillis).isEqualTo(
                settingsBundle.getLong(ContactsIndexerSettings.LAST_CONTACT_UPDATE_TIMESTAMP_KEY));
        assertThat(mUpdateStats.mLastContactDeletedTimeMillis).isEqualTo(
                settingsBundle.getLong(ContactsIndexerSettings.LAST_CONTACT_DELETE_TIMESTAMP_KEY));
    }

    @Test
    public void testDeltaUpdate_insertedAndDeletedContacts_withDeletionSucceed() throws Exception {
        ContentResolver resolver = mContext.getContentResolver();
        ContentValues dummyValues = new ContentValues();
        // Index 10 documents before testing.
        for (int i = 0; i < 10; i++) {
            resolver.insert(ContactsContract.Contacts.CONTENT_URI, dummyValues);
        }
        executeAndWaitForCompletion(
                mInstance.doFullUpdateInternalAsync(new CancellationSignal(), mUpdateStats),
                mSingleThreadedExecutor);

        long timeBeforeDeltaChangeNotification = System.currentTimeMillis();
        // Insert additional 5 contacts to trigger delta update.
        for (int i = 0; i < 5; i++) {
            resolver.insert(ContactsContract.Contacts.CONTENT_URI, dummyValues);
        }
        // Delete a few contacts to trigger delta update.
        resolver.delete(ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, 2),
                /*extras=*/ null);
        resolver.delete(ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, 3),
                /*extras=*/ null);
        resolver.delete(ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, 5),
                /*extras=*/ null);
        resolver.delete(ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, 7),
                /*extras=*/ null);

        mUpdateStats.clear();
        executeAndWaitForCompletion(mInstance.doDeltaUpdateAsync(/*indexingLimit=*/ -1,
                        mUpdateStats),
                mSingleThreadedExecutor);

        AppSearchHelper searchHelper = AppSearchHelper.createAppSearchHelper(mContext,
                mSingleThreadedExecutor, mConfigForTest);
        List<String> contactIds = searchHelper.getAllContactIdsAsync().get();
        assertThat(contactIds.size()).isEqualTo(11);
        assertThat(contactIds).containsNoneOf("2", "3", "5", "7");

        PersistableBundle settingsBundle = ContactsIndexerSettings.readBundle(mSettingsFile);
        assertThat(settingsBundle.getLong(ContactsIndexerSettings.LAST_DELTA_UPDATE_TIMESTAMP_KEY))
                .isAtLeast(timeBeforeDeltaChangeNotification);
        assertThat(
                settingsBundle.getLong(ContactsIndexerSettings.LAST_CONTACT_UPDATE_TIMESTAMP_KEY))
                .isAtLeast(timeBeforeDeltaChangeNotification);
        assertThat(
                settingsBundle.getLong(ContactsIndexerSettings.LAST_CONTACT_DELETE_TIMESTAMP_KEY))
                .isAtLeast(timeBeforeDeltaChangeNotification);
        // check stats
        assertThat(mUpdateStats.mUpdateType).isEqualTo(ContactsUpdateStats.DELTA_UPDATE);
        assertThat(mUpdateStats.mUpdateStatuses).hasSize(1);
        assertThat(mUpdateStats.mUpdateStatuses).containsExactly(AppSearchResult.RESULT_OK);
        assertThat(mUpdateStats.mDeleteStatuses).hasSize(1);
        assertThat(mUpdateStats.mDeleteStatuses).containsExactly(AppSearchResult.RESULT_OK);
        assertThat(mUpdateStats.mContactsUpdateFailedCount).isEqualTo(0);
        // NOT_FOUND does not count as error.
        assertThat(mUpdateStats.mContactsDeleteFailedCount).isEqualTo(0);
        assertThat(mUpdateStats.mContactsDeleteNotFoundCount).isEqualTo(0);
        assertThat(mUpdateStats.mNewContactsToBeUpdated).isEqualTo(5);
        assertThat(mUpdateStats.mContactsUpdateSkippedCount).isEqualTo(0);
        assertThat(mUpdateStats.mTotalContactsToBeUpdated).isEqualTo(5);
        assertThat(mUpdateStats.mContactsUpdateSucceededCount).isEqualTo(5);
        assertThat(mUpdateStats.mTotalContactsToBeDeleted).isEqualTo(4);
        assertThat(mUpdateStats.mContactsDeleteSucceededCount).isEqualTo(4);
        // check timestamps
        assertThat(mUpdateStats.mUpdateAndDeleteStartTimeMillis).isEqualTo(settingsBundle.getLong(
                ContactsIndexerSettings.LAST_DELTA_UPDATE_TIMESTAMP_KEY));
        assertThat(mUpdateStats.mLastDeltaUpdateStartTimeMillis).isLessThan(settingsBundle.getLong(
                ContactsIndexerSettings.LAST_DELTA_UPDATE_TIMESTAMP_KEY));
        assertThat(mUpdateStats.mLastContactUpdatedTimeMillis).isEqualTo(
                settingsBundle.getLong(ContactsIndexerSettings.LAST_CONTACT_UPDATE_TIMESTAMP_KEY));
        assertThat(mUpdateStats.mLastContactDeletedTimeMillis).isEqualTo(
                settingsBundle.getLong(ContactsIndexerSettings.LAST_CONTACT_DELETE_TIMESTAMP_KEY));
    }

    @Test
    public void testDeltaUpdate_outOfSpaceError_fullUpdateScheduled() throws Exception {
        // This tests whether a full update job will be run to prune the person corpus when
        // AppSearch reaches its max document limit. Since there are issues with obtaining the
        // permissions to change the device config for max document limit, and we don't want to
        // index 10000+ documents in this test, we simulate the out of space error by manually
        // adding it to update stats beforehand.

        // Cancel any existing jobs.
        ContactsIndexerMaintenanceService.cancelFullUpdateJobIfScheduled(mContext,
                mContext.getUser());

        JobScheduler mockJobScheduler = mock(JobScheduler.class);
        mContext.setJobScheduler(mockJobScheduler);

        // manually add out of space error
        mUpdateStats.mUpdateStatuses.add(AppSearchResult.RESULT_OUT_OF_SPACE);

        executeAndWaitForCompletion(
                mInstance.doDeltaUpdateAsync(ContactsProviderUtil.UPDATE_LIMIT_NONE,
                        mUpdateStats),
                mSingleThreadedExecutor);

        // Verify the full update job is scheduled due to out_of_space.
        verify(mockJobScheduler).schedule(any());
    }

    @Test
    public void testDeltaUpdate_notTriggered_afterCompatibleSchemaChange() throws Exception {
        long timeAtBeginning = System.currentTimeMillis();

        // Configure the timestamps to non-zero on disk.
        PersistableBundle settingsBundle = new PersistableBundle();
        settingsBundle.putLong(ContactsIndexerSettings.LAST_FULL_UPDATE_TIMESTAMP_KEY,
                timeAtBeginning);
        settingsBundle.putLong(ContactsIndexerSettings.LAST_CONTACT_UPDATE_TIMESTAMP_KEY,
                timeAtBeginning);
        settingsBundle.putLong(ContactsIndexerSettings.LAST_CONTACT_DELETE_TIMESTAMP_KEY,
                timeAtBeginning);
        mSettingsFile.getParentFile().mkdirs();
        mSettingsFile.createNewFile();
        ContactsIndexerSettings.writeBundle(mSettingsFile, settingsBundle);
        // Preset a compatible schema.
        AppSearchManager.SearchContext searchContext =
                new AppSearchManager.SearchContext.Builder(AppSearchHelper.DATABASE_NAME).build();
        AppSearchSessionShim db = AppSearchSessionShimImpl.createSearchSessionAsync(
                searchContext).get();
        SetSchemaRequest setSchemaRequest = new SetSchemaRequest.Builder()
                .addSchemas(TestUtils.CONTACT_POINT_SCHEMA_WITH_APP_IDS_OPTIONAL,
                        Person.getSchema(mConfigForTest))
                .setForceOverride(true).build();
        db.setSchemaAsync(setSchemaRequest).get();

        // Since the current schema is compatible, this won't trigger any delta update and
        // schedule a full update job.
        JobScheduler mockJobScheduler = mock(JobScheduler.class);
        mContext.setJobScheduler(mockJobScheduler);
        mInstance = ContactsIndexerUserInstance.createInstance(mContext, mContactsDir,
                mConfigForTest, mSingleThreadedExecutor);
        try {
            mInstance.startAsync();
            verifyZeroInteractions(mockJobScheduler);
        } finally {
            mInstance.shutdown();
        }
    }

    @Test
    public void testDeltaUpdate_triggered_afterIncompatibleSchemaChange() throws Exception {
        long timeBeforeDeltaChangeNotification = System.currentTimeMillis();

        // Configure the timestamps to non-zero on disk.
        PersistableBundle settingsBundle = new PersistableBundle();
        settingsBundle.putLong(ContactsIndexerSettings.LAST_FULL_UPDATE_TIMESTAMP_KEY,
                timeBeforeDeltaChangeNotification);
        settingsBundle.putLong(ContactsIndexerSettings.LAST_CONTACT_UPDATE_TIMESTAMP_KEY,
                timeBeforeDeltaChangeNotification);
        settingsBundle.putLong(ContactsIndexerSettings.LAST_CONTACT_DELETE_TIMESTAMP_KEY,
                timeBeforeDeltaChangeNotification);
        mSettingsFile.getParentFile().mkdirs();
        mSettingsFile.createNewFile();
        ContactsIndexerSettings.writeBundle(mSettingsFile, settingsBundle);
        // Insert contacts
        int docCount = 250;
        ContentResolver resolver = mContext.getContentResolver();
        ContentValues dummyValues = new ContentValues();
        for (int i = 0; i < docCount; i++) {
            resolver.insert(ContactsContract.Contacts.CONTENT_URI, dummyValues);
        }
        // Preset an incompatible schema.
        AppSearchManager.SearchContext searchContext =
                new AppSearchManager.SearchContext.Builder(AppSearchHelper.DATABASE_NAME).build();
        AppSearchSessionShim db = AppSearchSessionShimImpl.createSearchSessionAsync(
                searchContext).get();
        SetSchemaRequest setSchemaRequest = new SetSchemaRequest.Builder()
                .addSchemas(TestUtils.CONTACT_POINT_SCHEMA_WITH_LABEL_REPEATED,
                        Person.getSchema(mConfigForTest))
                .setForceOverride(true).build();
        db.setSchemaAsync(setSchemaRequest).get();
        // Setup a latch
        CountDownLatch latch = new CountDownLatch(docCount);
        GlobalSearchSessionShim shim =
                GlobalSearchSessionShimImpl.createGlobalSearchSessionAsync(mContext).get();
        ObserverCallback callback = new ObserverCallback() {
            @Override
            public void onSchemaChanged(SchemaChangeInfo changeInfo) {
                // Do nothing
            }

            @Override
            public void onDocumentChanged(DocumentChangeInfo changeInfo) {
                for (int i = 0; i < changeInfo.getChangedDocumentIds().size(); i++) {
                    latch.countDown();
                }
            }
        };
        shim.registerObserverCallback(mContext.getPackageName(),
                new ObserverSpec.Builder().addFilterSchemas("builtin:Person").build(),
                mSingleThreadedExecutor,
                callback);

        // Since the current schema is incompatible, this will trigger two setSchemas, and run do
        // doCp2SyncFirstRun again.
        JobScheduler mockJobScheduler = mock(JobScheduler.class);
        mContext.setJobScheduler(mockJobScheduler);
        mInstance = ContactsIndexerUserInstance.createInstance(mContext, mContactsDir,
                mConfigForTest, mSingleThreadedExecutor);
        try {
            mInstance.startAsync();
            latch.await(30L, TimeUnit.SECONDS);
            verify(mockJobScheduler).schedule(any());
        } finally {
            mInstance.shutdown();
        }
    }

    @Test
    public void testLogStats_succeedsWhenMoreUpdateStatusCodesThanDeleteStatusCodes() {
        // This test exists since there was a typo/bug where we logged the update status codes
        // a second time instead of the delete status codes. This could also throw
        // ArrayIndexOutOfBoundsException if there were more update status codes than delete status
        // codes since the allocated array for delete status codes would be too small to store the
        // update status codes.
        ContactsUpdateStats updateStats = new ContactsUpdateStats();
        updateStats.mUpdateStatuses.add(AppSearchResult.RESULT_UNKNOWN_ERROR);
        updateStats.mUpdateStatuses.add(AppSearchResult.RESULT_OUT_OF_SPACE);
        updateStats.mDeleteStatuses.add(AppSearchResult.RESULT_INTERNAL_ERROR);
        mInstance.logStats(updateStats);

        ArgumentCaptor<int[]> updateStatusArr = ArgumentCaptor.forClass(int[].class);
        ArgumentCaptor<int[]> deleteStatusArr = ArgumentCaptor.forClass(int[].class);

        ExtendedMockito.verify(() -> AppSearchStatsLog.write(
                Mockito.eq(AppSearchStatsLog.CONTACTS_INDEXER_UPDATE_STATS_REPORTED),
                Mockito.anyInt(),
                Mockito.anyInt(),
                updateStatusArr.capture(),
                deleteStatusArr.capture(),
                Mockito.anyInt(),
                Mockito.anyInt(),
                Mockito.anyInt(),
                Mockito.anyInt(),
                Mockito.anyInt(),
                Mockito.anyInt(),
                Mockito.anyInt(),
                Mockito.anyLong(),
                Mockito.anyLong(),
                Mockito.anyLong(),
                Mockito.anyLong(),
                Mockito.anyLong(),
                Mockito.anyLong()));

        assertThat(updateStatusArr.getValue()).asList().containsExactly(
                AppSearchResult.RESULT_UNKNOWN_ERROR, AppSearchResult.RESULT_OUT_OF_SPACE);
        assertThat(deleteStatusArr.getValue()).asList().containsExactly(
                AppSearchResult.RESULT_INTERNAL_ERROR);
    }

    @Test
    public void testConcurrentUpdates_updatesDoNotInterfereWithEachOther() throws Exception {
        // Generally, two delta updates cannot occur simultaneously, but it is possible for a full
        // update and delta update to run at the same time. Both updates use the same
        // ContactsIndexerImpl to index contacts, and previously, ContactsIndexerImpl would keep
        // a single ContactsBatcher for all updates. This could lead to updates taking contacts away
        // from each other to index and would mess up the metrics/counts for succeeded/skipped
        // contacts. This has been fixed by using local ContactsBatchers instead.
        long timeBeforeDeltaChangeNotification = System.currentTimeMillis();
        // Insert contacts to trigger delta update.
        ContentResolver resolver = mContext.getContentResolver();
        ContentValues dummyValues = new ContentValues();
        for (int i = 0; i < 250; i++) {
            resolver.insert(ContactsContract.Contacts.CONTENT_URI, dummyValues);
        }

        mSingleThreadedExecutor.submit(
                () -> mInstance.doDeltaUpdateAsync(/*indexingLimit=*/ -1, mUpdateStats));

        ContactsUpdateStats updateStats = new ContactsUpdateStats();
        executeAndWaitForCompletion(
                mInstance.doFullUpdateInternalAsync(new CancellationSignal(), updateStats),
                mSingleThreadedExecutor);

        AppSearchHelper searchHelper = AppSearchHelper.createAppSearchHelper(mContext,
                mSingleThreadedExecutor, mConfigForTest);
        List<String> contactIds = searchHelper.getAllContactIdsAsync().get();
        assertThat(contactIds.size()).isEqualTo(250);

        PersistableBundle settingsBundle = ContactsIndexerSettings.readBundle(mSettingsFile);
        assertThat(settingsBundle.getLong(ContactsIndexerSettings.LAST_DELTA_UPDATE_TIMESTAMP_KEY))
                .isAtLeast(timeBeforeDeltaChangeNotification);

        // check stats
        assertThat(mUpdateStats.mUpdateType).isEqualTo(ContactsUpdateStats.DELTA_UPDATE);
        assertThat(mUpdateStats.mUpdateStatuses).hasSize(1);
        assertThat(mUpdateStats.mUpdateStatuses).containsExactly(AppSearchResult.RESULT_OK);
        assertThat(mUpdateStats.mDeleteStatuses).hasSize(1);
        assertThat(mUpdateStats.mDeleteStatuses).containsExactly(AppSearchResult.RESULT_OK);
        assertThat(mUpdateStats.mContactsUpdateFailedCount).isEqualTo(0);
        assertThat(mUpdateStats.mContactsDeleteFailedCount).isEqualTo(0);
        assertThat(mUpdateStats.mContactsDeleteNotFoundCount).isEqualTo(0);
        assertThat(mUpdateStats.mTotalContactsToBeUpdated).isEqualTo(250);
        assertThat(mUpdateStats.mContactsUpdateSucceededCount
                + mUpdateStats.mContactsUpdateSkippedCount).isEqualTo(250);
        assertThat(mUpdateStats.mTotalContactsToBeDeleted).isEqualTo(0);
        assertThat(mUpdateStats.mContactsDeleteSucceededCount).isEqualTo(0);

        // check stats
        assertThat(updateStats.mUpdateType).isEqualTo(ContactsUpdateStats.FULL_UPDATE);
        assertThat(updateStats.mUpdateStatuses).hasSize(1);
        assertThat(updateStats.mUpdateStatuses).containsExactly(AppSearchResult.RESULT_OK);
        assertThat(updateStats.mDeleteStatuses).hasSize(1);
        assertThat(updateStats.mDeleteStatuses).containsExactly(AppSearchResult.RESULT_OK);
        assertThat(updateStats.mContactsUpdateFailedCount).isEqualTo(0);
        // NOT_FOUND does not count as error.
        assertThat(updateStats.mContactsDeleteFailedCount).isEqualTo(0);
        assertThat(updateStats.mTotalContactsToBeUpdated).isEqualTo(250);
        assertThat(updateStats.mContactsUpdateSucceededCount
                + updateStats.mContactsUpdateSkippedCount).isEqualTo(250);
        assertThat(updateStats.mTotalContactsToBeDeleted).isEqualTo(0);
        assertThat(updateStats.mContactsDeleteSucceededCount).isEqualTo(0);
    }

    /**
     * Executes given {@link CompletionStage} on the {@code executor} and waits for its completion.
     *
     * <p>There are 2 steps in this implementation. The first step is to execute the stage on the
     * executor, and wait for its execution. The second step is to wait for the completion of the
     * stage itself.
     */
    private <T> T executeAndWaitForCompletion(CompletionStage<T> stage, ExecutorService executor)
            throws Exception {
        AtomicReference<CompletableFuture<T>> future = new AtomicReference<>(
                CompletableFuture.completedFuture(null));
        executor.submit(() -> {
            // Chain the given stage inside the runnable task so that it executes on the executor.
            CompletableFuture<T> chainedFuture = future.get().thenCompose(x -> stage);
            future.set(chainedFuture);
        }).get();
        // Wait for the task to complete on the executor, and wait for the stage to complete also.
        return future.get().get();
    }

    private static class TestMockFixture implements StaticMockFixture {
        @Override
        public StaticMockitoSessionBuilder setUpMockedClasses(
                @NonNull StaticMockitoSessionBuilder sessionBuilder) {
            sessionBuilder.spyStatic(AppSearchStatsLog.class);
            return sessionBuilder;
        }

        @Override
        public void setUpMockBehaviors() {
        }

        @Override
        public void tearDown() {
        }
    }
}
