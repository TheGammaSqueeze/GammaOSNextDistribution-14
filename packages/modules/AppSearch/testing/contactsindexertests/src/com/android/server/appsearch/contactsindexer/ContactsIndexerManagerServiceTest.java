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

import static android.Manifest.permission.INTERACT_ACROSS_USERS_FULL;
import static android.Manifest.permission.RECEIVE_BOOT_COMPLETED;

import static com.android.server.appsearch.contactsindexer.ContactsIndexerMaintenanceService.MIN_INDEXER_JOB_ID;

import static com.google.common.truth.Truth.assertThat;

import android.app.UiAutomation;
import android.app.appsearch.AppSearchManager;
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
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.pm.UserInfo;
import android.provider.ContactsContract;

import androidx.annotation.NonNull;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.SystemUtil;
import com.android.server.SystemService;

import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@RunWith(AndroidJUnit4.class)
public class ContactsIndexerManagerServiceTest extends FakeContactsProviderTestBase {
    private static final String TAG = "ContactsIndexerManagerServiceTest";

    private final ExecutorService mSingleThreadedExecutor = Executors.newSingleThreadExecutor();
    private ContactsIndexerManagerService mContactsIndexerManagerService;
    private UiAutomation mUiAutomation;

    @Override
    @Before
    public void setUp() throws Exception {
        super.setUp();
        mUiAutomation = InstrumentationRegistry.getInstrumentation().getUiAutomation();
        // INTERACT_ACROSS_USERS_FULL: needed when we do registerReceiverForAllUsers for getting
        // package change notifications.
        mUiAutomation.adoptShellPermissionIdentity(INTERACT_ACROSS_USERS_FULL);

        // TODO(b/276401961) right now we can't have more than one test in this class because in
        //  ContactsIndexerManagerService.onStart we register a local service, and it will throw
        //  an exception if we try to register another one in a 2nd test.
        //  Unfortunately catching the exception doesn't work because the registered
        //  manager/LocalService will still point to the previous ContactsIndexerManagerService,
        //  making the 2nd test fail.
        //  If I make ContactsIndexerManagerService static and only initialize it once in setUp
        //  by checking nullness, it seems working. However, it will make the tests run 3X slower,
        //  and we need to investigate that.
        //  UPDATE: mContactsIndexerManagerService.onStart() has been moved to the test
        //  testCP2Clear_runsFullUpdate specifically because currently that's the only test that
        //  needs mContactsIndexerManagerService to be fully functional.
        mContactsIndexerManagerService = new ContactsIndexerManagerService(mContext,
                new TestContactsIndexerConfig());
    }

    @Override
    @After
    public void tearDown() throws Exception {
        // Wipe the data in AppSearchHelper.DATABASE_NAME.
        AppSearchSessionShim db = AppSearchSessionShimImpl.createSearchSessionAsync(mContext,
                new AppSearchManager.SearchContext.Builder(AppSearchHelper.DATABASE_NAME).build(),
                mSingleThreadedExecutor).get();
        db.setSchemaAsync(new SetSchemaRequest.Builder().setForceOverride(true).build()).get();
        mUiAutomation.dropShellPermissionIdentity();
        super.tearDown();
    }

    // TODO(b/282073711) This test is flaky, figure out the root cause and fix it.
    @Test
    @Ignore
    public void testCP2Clear_runsFullUpdate() throws Exception {
        // TODO(b/276401961) onStart is being called here instead of the setUp method because setUp
        //  is called before every test and running onStart more than once will throw an exception
        //  (see the note in setUp method for more context). The other tests in this class don't
        //  really need the mContactsIndexerManagerService to function fully so onStart can be
        //  skipped there, but if we later need to add more tests that need
        //  mContactsIndexerManagerService to be fully functional, onStart will need to be moved
        //  back to setUp after figuring out a better way to handle multiple calls to it.
        mContactsIndexerManagerService.onStart();
        int userId = mContext.getUserId();

        // Populate fake CP2 with 100 contacts.
        ContentResolver resolver = mContext.getContentResolver();
        ContentValues values = new ContentValues();
        for (int i = 0; i < 100; i++) {
            resolver.insert(ContactsContract.Contacts.CONTENT_URI, values);
        }

        // Contacts indexer schedules a full-update job for bootstrapping from CP2,
        // and JobScheduler API requires BOOT_COMPLETED permission for persisting the job.
        mUiAutomation.adoptShellPermissionIdentity(RECEIVE_BOOT_COMPLETED);
        try {
            CountDownLatch bootstrapLatch = countDownAppSearchDocumentChanges(100);
            UserInfo userInfo = new UserInfo(mContext.getUser().getIdentifier(),
                    /*name=*/ "default", /*flags=*/ 0);
            mContactsIndexerManagerService.onUserUnlocking(new SystemService.TargetUser(userInfo));
            bootstrapLatch.await(30L, TimeUnit.SECONDS);
        } finally {
            mUiAutomation.dropShellPermissionIdentity();
        }

        long prevTimestampMillis = System.currentTimeMillis();
        // Clear fake CP2 first 50 contacts.
        for (int i = 0; i < 50; i++) {
            resolver.delete(ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, i),
                    /*extras=*/ null);
        }
        CountDownLatch fullUpdateLatch = countDownAppSearchDocumentChanges(50);
        SystemUtil.runShellCommand("pm clear --user " + userId + " com.android.providers.contacts");
        // Wait for full-update to run and delete all 100 contacts.
        fullUpdateLatch.await(30L, TimeUnit.SECONDS);

        // Clear fake CP2 last 50 contacts.
        // We trigger the 2nd update so the timestamps for the 1st update can be persisted.
        for (int i = 50; i < 100; i++) {
            resolver.delete(ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, i),
                    /*extras=*/ null);
        }
        fullUpdateLatch = countDownAppSearchDocumentChanges(50);
        SystemUtil.runShellCommand("pm clear --user " + userId + " com.android.providers.contacts");
        // Wait for full-update to run and delete all 100 contacts.
        fullUpdateLatch.await(30L, TimeUnit.SECONDS);

        // Verify that a periodic full-update job is scheduled still.
        assertThat(getJobState(MIN_INDEXER_JOB_ID + userId)).contains("waiting");

        // Verify the stats for the ContactsIndexer. Two full updates are triggered at this
        // point, and the timestamps for 1st update must have been persisted.
        StringWriter stringWriter = new StringWriter();
        PrintWriter pw = new PrintWriter(stringWriter);
        mContactsIndexerManagerService.dumpContactsIndexerForUser(
                mContext.getUser(), pw, /* verbose= */ false);
        String[] output = stringWriter.toString().split(System.lineSeparator());

        assertThat(output).hasLength(3);
        // DeltaDeleteTimestamp
        assertThat(getTimestampOutOfDump(output[0])).isGreaterThan(prevTimestampMillis);
        // DeltaUpdateTimestamp
        assertThat(getTimestampOutOfDump(output[1])).isGreaterThan(prevTimestampMillis);
        // FullUpdateTimestamp
        assertThat(getTimestampOutOfDump(output[2])).isGreaterThan(prevTimestampMillis);
    }

    private long getTimestampOutOfDump(String dumpOutputOneLine) {
        String[] arrs = dumpOutputOneLine.split(" ");
        assertThat(arrs.length).isAtLeast(2);
        return Long.parseLong(arrs[arrs.length - 2]);
    }

    @Test
    public void test_onUserUnlocking_handlesExceptionGracefully() {
        mContactsIndexerManagerService.onUserUnlocking(null);
    }

    @Test
    public void test_onUserStopping_handlesExceptionGracefully() {
        mContactsIndexerManagerService.onUserStopping(null);
    }

    @Test
    public void test_dumpContactsIndexerForUser_handlesExceptionGracefully() {
        mContactsIndexerManagerService.dumpContactsIndexerForUser(null, null, false);
    }

    /**
     * Returns a latch to count down the given number of document changes in the Person corpus.
     *
     * <p>The latch counts down to 0, and can be used to wait until the expected number of document
     * changes have occurred.
     */
    @NonNull
    private CountDownLatch countDownAppSearchDocumentChanges(int numChanges) throws Exception {
        CountDownLatch latch = new CountDownLatch(numChanges);
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
        return latch;
    }

    /**
     * Returns the current state of a job, which may be "pending", "active", "ready", or "waiting".
     *
     * <p>See "adb shell cmd jobscheduler -h" for more details.
     */
    @NonNull
    private String getJobState(int jobId) throws Exception {
        return SystemUtil.runShellCommand(mUiAutomation,
                "cmd jobscheduler get-job-state android " + jobId).trim();
    }
}
