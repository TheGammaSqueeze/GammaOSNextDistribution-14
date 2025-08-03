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
package com.android.server.appsearch;

import static android.Manifest.permission.READ_GLOBAL_APP_SEARCH_DATA;
import static android.system.OsConstants.O_RDONLY;
import static android.system.OsConstants.O_WRONLY;

import static com.android.internal.util.ConcurrentUtils.DIRECT_EXECUTOR;
import static com.android.server.appsearch.FrameworkAppSearchConfigImpl.KEY_DENYLIST;
import static com.android.server.appsearch.FrameworkAppSearchConfigImpl.KEY_RATE_LIMIT_API_COSTS;
import static com.android.server.appsearch.FrameworkAppSearchConfigImpl.KEY_RATE_LIMIT_ENABLED;
import static com.android.server.appsearch.FrameworkAppSearchConfigImpl.KEY_RATE_LIMIT_TASK_QUEUE_PER_PACKAGE_CAPACITY_PERCENTAGE;
import static com.android.server.appsearch.FrameworkAppSearchConfigImpl.KEY_RATE_LIMIT_TASK_QUEUE_TOTAL_CAPACITY;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import android.Manifest;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.app.UiAutomation;
import android.app.appsearch.AppSearchBatchResult;
import android.app.appsearch.AppSearchEnvironmentFactory;
import android.app.appsearch.AppSearchResult;
import android.app.appsearch.AppSearchSchema;
import android.app.appsearch.FrameworkAppSearchEnvironment;
import android.app.appsearch.GenericDocument;
import android.app.appsearch.GetSchemaResponse;
import android.app.appsearch.InternalSetSchemaResponse;
import android.app.appsearch.SearchResultPage;
import android.app.appsearch.SearchSpec;
import android.app.appsearch.SearchSuggestionSpec;
import android.app.appsearch.aidl.AppSearchAttributionSource;
import android.app.appsearch.aidl.AppSearchBatchResultParcel;
import android.app.appsearch.aidl.AppSearchResultParcel;
import android.app.appsearch.aidl.DocumentsParcel;
import android.app.appsearch.aidl.GetNextPageAidlRequest;
import android.app.appsearch.aidl.GetSchemaAidlRequest;
import android.app.appsearch.aidl.GetNamespacesAidlRequest;
import android.app.appsearch.aidl.GetStorageInfoAidlRequest;
import android.app.appsearch.aidl.GlobalSearchAidlRequest;
import android.app.appsearch.aidl.IAppSearchBatchResultCallback;
import android.app.appsearch.aidl.IAppSearchManager;
import android.app.appsearch.aidl.IAppSearchObserverProxy;
import android.app.appsearch.aidl.IAppSearchResultCallback;
import android.app.appsearch.aidl.InitializeAidlRequest;
import android.app.appsearch.aidl.InvalidateNextPageTokenAidlRequest;
import android.app.appsearch.aidl.PersistToDiskAidlRequest;
import android.app.appsearch.aidl.PutDocumentsAidlRequest;
import android.app.appsearch.aidl.PutDocumentsFromFileAidlRequest;
import android.app.appsearch.aidl.RegisterObserverCallbackAidlRequest;
import android.app.appsearch.aidl.RemoveByDocumentIdAidlRequest;
import android.app.appsearch.aidl.RemoveByQueryAidlRequest;
import android.app.appsearch.aidl.SearchAidlRequest;
import android.app.appsearch.aidl.SearchSuggestionAidlRequest;
import android.app.appsearch.aidl.SetSchemaAidlRequest;
import android.app.appsearch.aidl.UnregisterObserverCallbackAidlRequest;
import android.app.appsearch.aidl.WriteSearchResultsToFileAidlRequest;
import android.app.appsearch.observer.ObserverSpec;
import android.app.appsearch.stats.SchemaMigrationStats;
import android.content.AttributionSource;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.ParcelFileDescriptor;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.SystemClock;
import android.os.UserHandle;
import android.provider.DeviceConfig;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.dx.mockito.inline.extended.ExtendedMockito;
import com.android.dx.mockito.inline.extended.StaticMockitoSessionBuilder;
import com.android.modules.utils.testing.ExtendedMockitoRule;
import com.android.modules.utils.testing.StaticMockFixture;
import com.android.modules.utils.testing.TestableDeviceConfig;
import com.android.server.LocalManagerRegistry;
import com.android.server.appsearch.external.localstorage.stats.CallStats;
import com.android.server.appsearch.external.localstorage.stats.SearchStats;
import com.android.server.appsearch.external.localstorage.stats.SetSchemaStats;
import com.android.server.usage.StorageStatsManagerLocal;

import libcore.io.IoBridge;

import com.google.common.util.concurrent.SettableFuture;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockito.ArgumentCaptor;

import java.io.File;
import java.io.FileDescriptor;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutionException;

public class AppSearchManagerServiceTest {
    private static final String DATABASE_NAME = "databaseName";
    private static final String NAMESPACE = "namespace";
    private static final String ID = "ID";
    private static final SearchSpec EMPTY_SEARCH_SPEC = new SearchSpec.Builder().build();
    // Mostly guarantees the logged estimated binder latency is positive and doesn't overflow
    private static final long BINDER_CALL_START_TIME = SystemClock.elapsedRealtime() - 1;
    // TODO(b/279047435): use actual AppSearchResult.RESULT_DENIED constant after it's unhidden
    private static final int RESULT_DENIED = 9;

    // TODO(b/279047435): use actual AppSearchResult.RESULT_RATE_LIMITED constant after it's
    //  unhidden
    private static final int RESULT_RATE_LIMITED = 10;
    private static final String FOO_PACKAGE_NAME = "foo";

    private final MockServiceManager mMockServiceManager = new MockServiceManager();

    @Rule
    public ExtendedMockitoRule mExtendedMockitoRule = new ExtendedMockitoRule.Builder()
            .addStaticMockFixtures(() -> mMockServiceManager, TestableDeviceConfig::new)
            .build();

    @Rule
    public TemporaryFolder mTemporaryFolder = new TemporaryFolder();

    private Context mContext;
    private AppSearchManagerService mAppSearchManagerService;
    private UserHandle mUserHandle;
    private UiAutomation mUiAutomation;
    private IAppSearchManager.Stub mAppSearchManagerServiceStub;
    private AppSearchUserInstance mUserInstance;
    private InternalAppSearchLogger mLogger;

    @Before
    public void setUp() throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        mUserHandle = context.getUser();
        mUiAutomation = InstrumentationRegistry.getInstrumentation().getUiAutomation();
        mContext = new ContextWrapper(context) {
            // Mock-able package manager for testing
            final PackageManager mPackageManager = spy(context.getPackageManager());

            @Override
            public Intent registerReceiverForAllUsers(@Nullable BroadcastReceiver receiver,
                    @NonNull IntentFilter filter, @Nullable String broadcastPermission,
                    @Nullable Handler scheduler) {
                // Do nothing
                return null;
            }

            @Override
            public Context createContextAsUser(UserHandle user, int flags) {
                return new ContextWrapper(super.createContextAsUser(user, flags)) {
                    @Override
                    public PackageManager getPackageManager() {
                        return mPackageManager;
                    }
                };
            }

            @Override
            public PackageManager getPackageManager() {
                return mPackageManager;
            }
        };

        // Set a test environment that provides a temporary folder for AppSearch
        File mAppSearchDir = mTemporaryFolder.newFolder();
        AppSearchEnvironmentFactory.setEnvironmentInstanceForTest(
                new FrameworkAppSearchEnvironment() {
                    @Override
                    public File getAppSearchDir(@NonNull Context unused,
                            @NonNull UserHandle userHandle) {
                        return mAppSearchDir;
                    }
                });

        // In AppSearchManagerService, FrameworkAppSearchConfig is a singleton. During tearDown for
        // TestableDeviceConfig, the propertyChangedListeners are removed. Therefore we have to set
        // a fresh config with listeners in setUp in order to set new properties.
        FrameworkAppSearchConfig appSearchConfig =
            FrameworkAppSearchConfigImpl.create(DIRECT_EXECUTOR);
        AppSearchComponentFactory.setConfigInstanceForTest(appSearchConfig);

        // Create the user instance and add a spy to its logger to verify logging
        // Note, SimpleTestLogger does not suffice for our tests since CallStats logging in
        // AppSearchManagerService occurs in a separate thread. With a spy, we can verify with a
        // timeout to catch asynchronous calls.
        mUserInstance = AppSearchUserInstanceManager.getInstance().getOrCreateUserInstance(mContext,
                mUserHandle, appSearchConfig);
        mLogger = spy(mUserInstance.getLogger());
        mUserInstance.setLoggerForTest(mLogger);

        // Start the service
        mAppSearchManagerService = new AppSearchManagerService(mContext,
                new AppSearchModule.Lifecycle(mContext));
        mAppSearchManagerService.onStart();
        mAppSearchManagerServiceStub = mMockServiceManager.mStubCaptor.getValue();
        assertThat(mAppSearchManagerServiceStub).isNotNull();
    }

    @After
    public void tearDown() {
        // The TemporaryFolder rule's teardown will delete the current test folder; by removing the
        // current user instance, the next test will be able to create a new AppSearchImpl with
        // a new test folder
        AppSearchUserInstanceManager.getInstance().closeAndRemoveUserInstance(mUserHandle);
    }

    @Test
    public void testCallingPackageDoesntExistsInTargetUser() throws Exception {
        UserHandle testTargetUser = new UserHandle(1234);
        mUiAutomation.adoptShellPermissionIdentity(Manifest.permission.INTERACT_ACROSS_USERS_FULL);
        try {
            // Try to initial a AppSearchSession for secondary user, but the calling package doesn't
            // exist in there.
            TestResultCallback callback = new TestResultCallback();
            mAppSearchManagerServiceStub.initialize(
                    new InitializeAidlRequest(
                            AppSearchAttributionSource.createAttributionSource(mContext),
                            testTargetUser, System.currentTimeMillis())
                    , callback);
            assertThat(callback.get().isSuccess()).isFalse();
            assertThat(callback.get().getErrorMessage()).contains(
                    "SecurityException: Package: " + mContext.getPackageName()
                            + " haven't installed for user " + testTargetUser.getIdentifier());
        } finally {
            mUiAutomation.dropShellPermissionIdentity();
        }
    }

    @Test
    public void testSetSchemaStatsLogging() throws Exception {
        TestResultCallback callback = new TestResultCallback();
        mAppSearchManagerServiceStub.setSchema(
                new SetSchemaAidlRequest(
                AppSearchAttributionSource.createAttributionSource(mContext), DATABASE_NAME,
                /* schemaBundles= */ Collections.emptyList(),
                /* visibilityBundles= */ Collections.emptyList(), /* forceOverride= */ false,
                /* schemaVersion= */ 0, mUserHandle, BINDER_CALL_START_TIME,
                SchemaMigrationStats.FIRST_CALL_GET_INCOMPATIBLE),
                callback);
        assertThat(callback.get().getResultCode()).isEqualTo(AppSearchResult.RESULT_OK);
        verifyCallStats(mContext.getPackageName(), DATABASE_NAME, CallStats.CALL_TYPE_SET_SCHEMA);
        // SetSchemaStats is also logged in SetSchema
        ArgumentCaptor<SetSchemaStats> setSchemaStatsCaptor = ArgumentCaptor.forClass(
                SetSchemaStats.class);
        verify(mLogger, timeout(1000).times(1)).logStats(setSchemaStatsCaptor.capture());
        SetSchemaStats setSchemaStats = setSchemaStatsCaptor.getValue();
        assertThat(setSchemaStats.getPackageName()).isEqualTo(mContext.getPackageName());
        assertThat(setSchemaStats.getDatabase()).isEqualTo(DATABASE_NAME);
        assertThat(setSchemaStats.getStatusCode()).isEqualTo(callback.get().getResultCode());
        assertThat(setSchemaStats.getSchemaMigrationCallType()).isEqualTo(
                SchemaMigrationStats.FIRST_CALL_GET_INCOMPATIBLE);
    }

    @Test
    public void testLocalGetSchemaStatsLogging() throws Exception {
        TestResultCallback callback = new TestResultCallback();
        mAppSearchManagerServiceStub.getSchema(
                new GetSchemaAidlRequest(
                        AppSearchAttributionSource.createAttributionSource(mContext),
                        mContext.getPackageName(), DATABASE_NAME, mUserHandle,
                        BINDER_CALL_START_TIME, /* isForEnterprise= */ false),
                callback);
        assertThat(callback.get().getResultCode()).isEqualTo(AppSearchResult.RESULT_OK);
        verifyCallStats(mContext.getPackageName(), DATABASE_NAME, CallStats.CALL_TYPE_GET_SCHEMA);
    }

    @Test
    public void testGlobalGetSchemaStatsLogging() throws Exception {
        String otherPackageName = mContext.getPackageName() + "foo";
        TestResultCallback callback = new TestResultCallback();
        mAppSearchManagerServiceStub.getSchema(
                new GetSchemaAidlRequest(
                        AppSearchAttributionSource.createAttributionSource(mContext),
                        otherPackageName, DATABASE_NAME, mUserHandle, BINDER_CALL_START_TIME,
                        /* isForEnterprise= */ false),
                callback);
        assertThat(callback.get().getResultCode()).isEqualTo(AppSearchResult.RESULT_OK);
        verifyCallStats(mContext.getPackageName(), DATABASE_NAME,
                CallStats.CALL_TYPE_GLOBAL_GET_SCHEMA);
    }

    @Test
    public void testGetNamespacesStatsLogging() throws Exception {
        TestResultCallback callback = new TestResultCallback();
        mAppSearchManagerServiceStub.getNamespaces(
                new GetNamespacesAidlRequest(
                        AppSearchAttributionSource.createAttributionSource(mContext), DATABASE_NAME,
                        mUserHandle, BINDER_CALL_START_TIME),
                callback);
        assertThat(callback.get().getResultCode()).isEqualTo(AppSearchResult.RESULT_OK);
        verifyCallStats(mContext.getPackageName(), DATABASE_NAME,
                CallStats.CALL_TYPE_GET_NAMESPACES);
    }

    @Test
    public void testPutDocumentsStatsLogging() throws Exception {
        TestBatchResultErrorCallback callback = new TestBatchResultErrorCallback();
        mAppSearchManagerServiceStub.putDocuments(
                new PutDocumentsAidlRequest(
                        AppSearchAttributionSource.createAttributionSource(mContext), DATABASE_NAME,
                        new DocumentsParcel(Collections.emptyList(), Collections.emptyList()),
                        mUserHandle, BINDER_CALL_START_TIME), callback);
        assertThat(callback.get()).isNull(); // null means there wasn't an error
        verifyCallStats(mContext.getPackageName(), DATABASE_NAME,
                CallStats.CALL_TYPE_PUT_DOCUMENTS);
        // putDocuments only logs PutDocumentStats indirectly so we don't verify it
    }

    @Test
    public void testLocalGetDocumentsStatsLogging() throws Exception {
        TestBatchResultErrorCallback callback = new TestBatchResultErrorCallback();
        mAppSearchManagerServiceStub.getDocuments(
                AppSearchAttributionSource.createAttributionSource(mContext),
                mContext.getPackageName(), DATABASE_NAME, NAMESPACE,
                /* ids= */ Collections.emptyList(), /* typePropertyPaths= */ Collections.emptyMap(),
                mUserHandle, BINDER_CALL_START_TIME, /* isForEnterprise= */ false, callback);
        assertThat(callback.get()).isNull(); // null means there wasn't an error
        verifyCallStats(mContext.getPackageName(), DATABASE_NAME,
                CallStats.CALL_TYPE_GET_DOCUMENTS);
    }

    @Test
    public void testGlobalGetDocumentsStatsLogging() throws Exception {
        String otherPackageName = mContext.getPackageName() + "foo";
        TestBatchResultErrorCallback callback = new TestBatchResultErrorCallback();
        mAppSearchManagerServiceStub.getDocuments(
                AppSearchAttributionSource.createAttributionSource(mContext),
                otherPackageName, DATABASE_NAME, NAMESPACE, /* ids= */ Collections.emptyList(),
                /* typePropertyPaths= */ Collections.emptyMap(), mUserHandle,
                BINDER_CALL_START_TIME, /* isForEnterprise= */ false, callback);
        assertThat(callback.get()).isNull(); // null means there wasn't an error
        verifyCallStats(mContext.getPackageName(), DATABASE_NAME,
                CallStats.CALL_TYPE_GLOBAL_GET_DOCUMENT_BY_ID);
    }

    @Test
    public void testSearchStatsLogging() throws Exception {
        TestResultCallback callback = new TestResultCallback();
        mAppSearchManagerServiceStub.search(
                new SearchAidlRequest(AppSearchAttributionSource.createAttributionSource(mContext),
                        DATABASE_NAME, /* searchExpression= */ "", EMPTY_SEARCH_SPEC, mUserHandle,
                        BINDER_CALL_START_TIME), callback);
        assertThat(callback.get().getResultCode()).isEqualTo(AppSearchResult.RESULT_OK);
        verifyCallStats(mContext.getPackageName(), DATABASE_NAME, CallStats.CALL_TYPE_SEARCH);
        // search only logs SearchStats indirectly so we don't verify it
    }

    @Test
    public void testGlobalSearchStatsLogging() throws Exception {
        TestResultCallback callback = new TestResultCallback();
        mAppSearchManagerServiceStub.globalSearch(new GlobalSearchAidlRequest(
                AppSearchAttributionSource.createAttributionSource(mContext),
                /* searchExpression= */ "", EMPTY_SEARCH_SPEC, mUserHandle, BINDER_CALL_START_TIME,
                /* isForEnterprise= */ false), callback);
        assertThat(callback.get().getResultCode()).isEqualTo(AppSearchResult.RESULT_OK);
        verifyCallStats(mContext.getPackageName(), CallStats.CALL_TYPE_GLOBAL_SEARCH);
        // globalSearch only logs SearchStats indirectly so we don't verify it
    }

    @Test
    public void testLocalGetNextPageStatsLogging() throws Exception {
        TestResultCallback callback = new TestResultCallback();
        mAppSearchManagerServiceStub.getNextPage(new GetNextPageAidlRequest(
                AppSearchAttributionSource.createAttributionSource(mContext),
                DATABASE_NAME, /* nextPageToken= */ 0,
                AppSearchSchema.StringPropertyConfig.JOINABLE_VALUE_TYPE_QUALIFIED_ID, mUserHandle,
                BINDER_CALL_START_TIME, /* isForEnterprise= */ false), callback);
        assertThat(callback.get().getResultCode()).isEqualTo(AppSearchResult.RESULT_OK);
        verifyCallStats(mContext.getPackageName(), DATABASE_NAME,
                CallStats.CALL_TYPE_GET_NEXT_PAGE);
        // getNextPage also logs SearchStats
        ArgumentCaptor<SearchStats> searchStatsCaptor = ArgumentCaptor.forClass(SearchStats.class);
        verify(mLogger, timeout(1000).times(1)).logStats(searchStatsCaptor.capture());
        SearchStats searchStats = searchStatsCaptor.getValue();
        assertThat(searchStats.getVisibilityScope()).isEqualTo(SearchStats.VISIBILITY_SCOPE_LOCAL);
        assertThat(searchStats.getPackageName()).isEqualTo(mContext.getPackageName());
        assertThat(searchStats.getDatabase()).isEqualTo(DATABASE_NAME);
        assertThat(searchStats.getJoinType()).isEqualTo(
                AppSearchSchema.StringPropertyConfig.JOINABLE_VALUE_TYPE_QUALIFIED_ID);
    }

    @Test
    public void testGlobalGetNextPageStatsLogging() throws Exception {
        TestResultCallback callback = new TestResultCallback();
        mAppSearchManagerServiceStub.getNextPage(new GetNextPageAidlRequest(
                AppSearchAttributionSource.createAttributionSource(mContext),
                /* databaseName= */ null, /* nextPageToken= */ 0,
                AppSearchSchema.StringPropertyConfig.JOINABLE_VALUE_TYPE_QUALIFIED_ID, mUserHandle,
                BINDER_CALL_START_TIME, /* isForEnterprise= */ false), callback);
        assertThat(callback.get().getResultCode()).isEqualTo(AppSearchResult.RESULT_OK);
        verifyCallStats(mContext.getPackageName(), CallStats.CALL_TYPE_GLOBAL_GET_NEXT_PAGE);
        // getNextPage also logs SearchStats
        ArgumentCaptor<SearchStats> searchStatsCaptor = ArgumentCaptor.forClass(SearchStats.class);
        verify(mLogger, timeout(1000).times(1)).logStats(searchStatsCaptor.capture());
        SearchStats searchStats = searchStatsCaptor.getValue();
        assertThat(searchStats.getVisibilityScope()).isEqualTo(SearchStats.VISIBILITY_SCOPE_GLOBAL);
        assertThat(searchStats.getPackageName()).isEqualTo(mContext.getPackageName());
        assertThat(searchStats.getDatabase()).isNull();
        assertThat(searchStats.getJoinType()).isEqualTo(
                AppSearchSchema.StringPropertyConfig.JOINABLE_VALUE_TYPE_QUALIFIED_ID);
    }

    @Test
    public void testInvalidateNextPageTokenStatsLogging() throws Exception {
        mAppSearchManagerServiceStub.invalidateNextPageToken(new InvalidateNextPageTokenAidlRequest(
                AppSearchAttributionSource.createAttributionSource(mContext),
                /* nextPageToken= */ 0, mUserHandle, BINDER_CALL_START_TIME,
                /* isForEnterprise= */ false));
        verifyCallStats(mContext.getPackageName(), CallStats.CALL_TYPE_INVALIDATE_NEXT_PAGE_TOKEN);
    }

    @Test
    public void testWriteSearchResultsToFileStatsLogging() throws Exception {
        File tempFile = mTemporaryFolder.newFile();
        FileDescriptor fd = IoBridge.open(tempFile.getPath(), O_WRONLY);
        TestResultCallback callback = new TestResultCallback();
        mAppSearchManagerServiceStub.writeSearchResultsToFile(
                new WriteSearchResultsToFileAidlRequest(
                        AppSearchAttributionSource.createAttributionSource(mContext), DATABASE_NAME,
                        new ParcelFileDescriptor(fd), /* searchExpression= */ "", EMPTY_SEARCH_SPEC,
                        mUserHandle, BINDER_CALL_START_TIME), callback);
        assertThat(callback.get().getResultCode()).isEqualTo(AppSearchResult.RESULT_OK);
        verifyCallStats(mContext.getPackageName(), DATABASE_NAME,
                CallStats.CALL_TYPE_WRITE_SEARCH_RESULTS_TO_FILE);
    }

    @Test
    public void testPutDocumentsFromFileStatsLogging() throws Exception {
        File tempFile = mTemporaryFolder.newFile();
        FileDescriptor fd = IoBridge.open(tempFile.getPath(), O_RDONLY);
        TestResultCallback callback = new TestResultCallback();
        mAppSearchManagerServiceStub.putDocumentsFromFile(new PutDocumentsFromFileAidlRequest(
                AppSearchAttributionSource.createAttributionSource(mContext), DATABASE_NAME,
                new ParcelFileDescriptor(fd), mUserHandle,
                new SchemaMigrationStats.Builder(mContext.getPackageName(), DATABASE_NAME).build(),
                /* totalLatencyStartTimeMillis= */ 0, BINDER_CALL_START_TIME), callback);
        assertThat(callback.get().getResultCode()).isEqualTo(AppSearchResult.RESULT_OK);
        verifyCallStats(mContext.getPackageName(), DATABASE_NAME,
                CallStats.CALL_TYPE_PUT_DOCUMENTS_FROM_FILE);
        // putDocumentsFromFile also logs SchemaMigrationStats
        ArgumentCaptor<SchemaMigrationStats> migrationStatsCaptor = ArgumentCaptor.forClass(
                SchemaMigrationStats.class);
        verify(mLogger, timeout(1000).times(1)).logStats(migrationStatsCaptor.capture());
        SchemaMigrationStats migrationStats = migrationStatsCaptor.getValue();
        assertThat(migrationStats.getStatusCode()).isEqualTo(AppSearchResult.RESULT_OK);
        assertThat(migrationStats.getSaveDocumentLatencyMillis()).isGreaterThan(0);
        // putDocumentsFromFile only logs PutDocumentStats indirectly so we don't verify it
    }

    @Test
    public void testSearchSuggestionStatsLogging() throws Exception {
        SearchSuggestionSpec searchSuggestionSpec =
            new SearchSuggestionSpec.Builder(/*maximumResultCount=*/1).build();
        TestResultCallback callback = new TestResultCallback();
        mAppSearchManagerServiceStub.searchSuggestion(
                new SearchSuggestionAidlRequest(
                        AppSearchAttributionSource.createAttributionSource(mContext),
                        DATABASE_NAME, /* suggestionQueryExpression= */ "foo", searchSuggestionSpec,
                        mUserHandle, BINDER_CALL_START_TIME),
                callback);
        assertThat(callback.get().getResultCode()).isEqualTo(AppSearchResult.RESULT_OK);
        verifyCallStats(mContext.getPackageName(), DATABASE_NAME,
                CallStats.CALL_TYPE_SEARCH_SUGGESTION);
    }

    @Test
    public void testLocalReportUsageStatsLogging() throws Exception {
        setUpTestSchema(mContext.getPackageName(), DATABASE_NAME);
        setUpTestDocument(mContext.getPackageName(), DATABASE_NAME, NAMESPACE, ID);
        TestResultCallback callback = new TestResultCallback();
        mAppSearchManagerServiceStub.reportUsage(
                AppSearchAttributionSource.createAttributionSource(mContext),
                mContext.getPackageName(), DATABASE_NAME, NAMESPACE, ID,
                /* usageTimestampMillis= */ 0, /* systemUsage= */ false, mUserHandle,
                BINDER_CALL_START_TIME, callback);
        assertThat(callback.get().getResultCode()).isEqualTo(AppSearchResult.RESULT_OK);
        verifyCallStats(mContext.getPackageName(), DATABASE_NAME, CallStats.CALL_TYPE_REPORT_USAGE);
        removeTestSchema(mContext.getPackageName(), DATABASE_NAME);
    }

    @Test
    public void testGlobalReportUsageStatsLogging() throws Exception {
        // Grant system access for global report usage
        mUiAutomation.adoptShellPermissionIdentity(Manifest.permission.READ_GLOBAL_APP_SEARCH_DATA);
        try {
            String otherPackageName = mContext.getPackageName() + "foo";
            setUpTestSchema(otherPackageName, DATABASE_NAME);
            setUpTestDocument(otherPackageName, DATABASE_NAME, NAMESPACE, ID);
            TestResultCallback callback = new TestResultCallback();
            mAppSearchManagerServiceStub.reportUsage(
                    AppSearchAttributionSource.createAttributionSource(mContext),
                    otherPackageName, DATABASE_NAME, NAMESPACE, ID, /* usageTimestampMillis= */ 0,
                    /* systemUsage= */ true, mUserHandle, BINDER_CALL_START_TIME, callback);
            assertThat(callback.get().getResultCode()).isEqualTo(AppSearchResult.RESULT_OK);
            verifyCallStats(mContext.getPackageName(), DATABASE_NAME,
                    CallStats.CALL_TYPE_REPORT_SYSTEM_USAGE);
            removeTestSchema(otherPackageName, DATABASE_NAME);
        } finally {
            mUiAutomation.dropShellPermissionIdentity();
        }
    }

    @Test
    public void testRemoveByDocumentIdStatsLogging() throws Exception {
        TestBatchResultErrorCallback callback = new TestBatchResultErrorCallback();
        mAppSearchManagerServiceStub.removeByDocumentId(
                new RemoveByDocumentIdAidlRequest(
                        AppSearchAttributionSource.createAttributionSource(mContext),
                        DATABASE_NAME, NAMESPACE, /* ids= */ Collections.emptyList(), mUserHandle,
                        BINDER_CALL_START_TIME),
                callback);
        assertThat(callback.get()).isNull(); // null means there wasn't an error
        verifyCallStats(mContext.getPackageName(), DATABASE_NAME,
                CallStats.CALL_TYPE_REMOVE_DOCUMENTS_BY_ID);
    }

    @Test
    public void testRemoveByQueryStatsLogging() throws Exception {
        TestResultCallback callback = new TestResultCallback();
        mAppSearchManagerServiceStub.removeByQuery(
                new RemoveByQueryAidlRequest(
                        AppSearchAttributionSource.createAttributionSource(mContext), DATABASE_NAME,
                        /* queryExpression= */ "", EMPTY_SEARCH_SPEC, mUserHandle,
                        BINDER_CALL_START_TIME),
                callback);
        assertThat(callback.get().getResultCode()).isEqualTo(AppSearchResult.RESULT_OK);
        verifyCallStats(mContext.getPackageName(), DATABASE_NAME,
                CallStats.CALL_TYPE_REMOVE_DOCUMENTS_BY_SEARCH);
    }

    @Test
    public void testGetStorageInfoStatsLogging() throws Exception {
        TestResultCallback callback = new TestResultCallback();
        mAppSearchManagerServiceStub.getStorageInfo(
                new GetStorageInfoAidlRequest(
                        AppSearchAttributionSource.createAttributionSource(mContext), DATABASE_NAME,
                        mUserHandle, BINDER_CALL_START_TIME),
                callback);
        assertThat(callback.get().getResultCode()).isEqualTo(AppSearchResult.RESULT_OK);
        verifyCallStats(mContext.getPackageName(), DATABASE_NAME,
                CallStats.CALL_TYPE_GET_STORAGE_INFO);
    }

    @Test
    public void testPersistToDiskStatsLogging() throws Exception {
        mAppSearchManagerServiceStub.persistToDisk(
                new PersistToDiskAidlRequest(
                        AppSearchAttributionSource.createAttributionSource(mContext), mUserHandle,
                        BINDER_CALL_START_TIME));
        verifyCallStats(mContext.getPackageName(), CallStats.CALL_TYPE_FLUSH);
    }

    @Test
    public void testRegisterObserverCallbackStatsLogging() throws Exception {
        AppSearchResultParcel<Void> resultParcel =
                mAppSearchManagerServiceStub.registerObserverCallback(
                        new RegisterObserverCallbackAidlRequest(
                                AppSearchAttributionSource.createAttributionSource(mContext),
                                mContext.getPackageName(),
                                new ObserverSpec.Builder().build(),
                                mUserHandle, BINDER_CALL_START_TIME),
                        new IAppSearchObserverProxy.Stub() {
                            @Override
                            public void onSchemaChanged(String packageName, String databaseName,
                                    List<String> changedSchemaNames) throws RemoteException {
                            }

                            @Override
                            public void onDocumentChanged(String packageName, String databaseName,
                                    String namespace, String schemaName,
                                    List<String> changedDocumentIds) throws RemoteException {
                            }
                        });
        assertThat(resultParcel.getResult().getResultCode()).isEqualTo(AppSearchResult.RESULT_OK);
        verifyCallStats(mContext.getPackageName(), CallStats.CALL_TYPE_REGISTER_OBSERVER_CALLBACK);
    }

    @Test
    public void testUnregisterObserverCallbackStatsLogging() throws Exception {
        AppSearchResultParcel<Void> resultParcel =
                mAppSearchManagerServiceStub.unregisterObserverCallback(
                        new UnregisterObserverCallbackAidlRequest(
                                AppSearchAttributionSource.createAttributionSource(mContext),
                                mContext.getPackageName(), mUserHandle,
                                BINDER_CALL_START_TIME),
                        new IAppSearchObserverProxy.Stub() {
                            @Override
                            public void onSchemaChanged(String packageName, String databaseName,
                                    List<String> changedSchemaNames) throws RemoteException {
                            }

                            @Override
                            public void onDocumentChanged(String packageName, String databaseName,
                                    String namespace, String schemaName,
                                    List<String> changedDocumentIds) throws RemoteException {
                            }
                        });
        assertThat(resultParcel.getResult().getResultCode()).isEqualTo(AppSearchResult.RESULT_OK);
        verifyCallStats(mContext.getPackageName(),
                CallStats.CALL_TYPE_UNREGISTER_OBSERVER_CALLBACK);
    }

    @Test
    public void testInitializeStatsLogging() throws Exception {
        TestResultCallback callback = new TestResultCallback();
        mAppSearchManagerServiceStub.initialize(
                new InitializeAidlRequest(
                        AppSearchAttributionSource.createAttributionSource(mContext), mUserHandle,
                        BINDER_CALL_START_TIME),
                callback);
        assertThat(callback.get().getResultCode()).isEqualTo(AppSearchResult.RESULT_OK);
        verifyCallStats(mContext.getPackageName(), CallStats.CALL_TYPE_INITIALIZE);
        // initialize only logs InitializeStats indirectly so we don't verify it
    }

    private void verifyCallStats(String packageName, String databaseName, int callType) {
        ArgumentCaptor<CallStats> captor = ArgumentCaptor.forClass(CallStats.class);
        verify(mLogger, timeout(1000).times(1)).logStats(captor.capture());
        CallStats callStats = captor.getValue();
        assertThat(callStats.getPackageName()).isEqualTo(packageName);
        assertThat(callStats.getDatabase()).isEqualTo(databaseName);
        assertThat(callStats.getCallType()).isEqualTo(callType);
        assertThat(callStats.getStatusCode()).isEqualTo(AppSearchResult.RESULT_OK);
        assertThat(callStats.getEstimatedBinderLatencyMillis()).isGreaterThan(0);
    }

    private void verifyCallStats(String packageName, int callType) {
        verifyCallStats(packageName, /* databaseName= */ null, callType);
    }

    @Test
    public void testDenylistMatchingCallingPackage() throws Exception {
        String denylistString =
                "pkg=com.android.appsearch.mockingservicestests&apis=localSetSchema,"
                        + "globalGetSchema,localGetSchema,localGetNamespaces,localPutDocuments,"
                        + "globalGetDocuments,localGetDocuments,localSearch,globalSearch,"
                        + "globalGetNextPage,localGetNextPage,invalidateNextPageToken,"
                        + "localWriteSearchResultsToFile,localPutDocumentsFromFile,"
                        + "localSearchSuggestion,globalReportUsage,localReportUsage,"
                        + "localRemoveByDocumentId,localRemoveBySearch,localGetStorageInfo,flush,"
                        + "globalRegisterObserverCallback,globalUnregisterObserverCallback,"
                        + "initialize";
        DeviceConfig.setProperty(DeviceConfig.NAMESPACE_APPSEARCH,
                KEY_DENYLIST, denylistString, false);
        // We expect all local calls (pkg+db) and global calls (pkg only) to be denied since the
        // denylist denies all api's for our calling package
        verifyLocalCallsResults(RESULT_DENIED);
        verifyGlobalCallsResults(RESULT_DENIED);
    }

    @Test
    public void testDenylistNonMatchingCallingPackage() throws Exception {
        String denylistString =
                "pkg=foo&apis=localSetSchema,globalGetSchema,localGetSchema,localGetNamespaces,"
                        + "localPutDocuments,globalGetDocuments,localGetDocuments,localSearch,"
                        + "globalSearch,globalGetNextPage,localGetNextPage,invalidateNextPageToken,"
                        + "localWriteSearchResultsToFile,localPutDocumentsFromFile,"
                        + "localSearchSuggestion,globalReportUsage,localReportUsage,"
                        + "localRemoveByDocumentId,localRemoveBySearch,localGetStorageInfo,flush,"
                        + "globalRegisterObserverCallback,globalUnregisterObserverCallback,"
                        + "initialize";
        DeviceConfig.setProperty(DeviceConfig.NAMESPACE_APPSEARCH,
                KEY_DENYLIST, denylistString, false);
        // We expect none of the local calls (pkg+db) and global calls (pkg only) to be denied since
        // the denylist denies all api's for a different calling package
        verifyLocalCallsResults(AppSearchResult.RESULT_OK);
        verifyGlobalCallsResults(AppSearchResult.RESULT_OK);
    }

    @Test
    public void testDenylistMatchingCallingPackageAndDatabase() throws Exception {
        String denylistString =
                "pkg=com.android.appsearch.mockingservicestests&db=databaseName&apis="
                        + "localSetSchema,globalGetSchema,localGetSchema,localGetNamespaces,"
                        + "localPutDocuments,globalGetDocuments,localGetDocuments,localSearch,"
                        + "globalSearch,globalGetNextPage,localGetNextPage,invalidateNextPageToken,"
                        + "localWriteSearchResultsToFile,localPutDocumentsFromFile,"
                        + "localSearchSuggestion,globalReportUsage,localReportUsage,"
                        + "localRemoveByDocumentId,localRemoveBySearch,localGetStorageInfo,flush,"
                        + "globalRegisterObserverCallback,globalUnregisterObserverCallback,"
                        + "initialize";
        DeviceConfig.setProperty(DeviceConfig.NAMESPACE_APPSEARCH,
                KEY_DENYLIST, denylistString, false);
        // We expect only the local calls (pkg+db) to be denied since the denylist specifies a
        // package-database name pair
        verifyLocalCallsResults(RESULT_DENIED);
        verifyGlobalCallsResults(AppSearchResult.RESULT_OK);
    }

    @Test
    public void testDenylistNonMatchingCallingPackageAndDatabase() throws Exception {
        // This denylist has two entries both of which should not match any of the api calls below
        // since either the package name or the database name will be different
        String denylistString =
                "pkg=foo&db=databaseName&apis=localSetSchema,globalGetSchema,localGetSchema,"
                        + "localGetNamespaces,localPutDocuments,globalGetDocuments,"
                        + "localGetDocuments,localSearch,globalSearch,globalGetNextPage,"
                        + "localGetNextPage,invalidateNextPageToken,localWriteSearchResultsToFile,"
                        + "localPutDocumentsFromFile,localSearchSuggestion,globalReportUsage,"
                        + "localReportUsage,localRemoveByDocumentId,localRemoveBySearch,"
                        + "localGetStorageInfo,flush,globalRegisterObserverCallback,"
                        + "globalUnregisterObserverCallback,initialize;"
                        + "pkg=com.android.appsearch.mockingservicestests&db=foo&apis="
                        + "localSetSchema,globalGetSchema,localGetSchema,localGetNamespaces,"
                        + "localPutDocuments,globalGetDocuments,localGetDocuments,localSearch,"
                        + "globalSearch,globalGetNextPage,localGetNextPage,invalidateNextPageToken,"
                        + "localWriteSearchResultsToFile,localPutDocumentsFromFile,"
                        + "localSearchSuggestion,globalReportUsage,localReportUsage,"
                        + "localRemoveByDocumentId,localRemoveBySearch,localGetStorageInfo,flush,"
                        + "globalRegisterObserverCallback,globalUnregisterObserverCallback,"
                        + "initialize";
        DeviceConfig.setProperty(DeviceConfig.NAMESPACE_APPSEARCH,
                KEY_DENYLIST, denylistString, false);
        verifyLocalCallsResults(AppSearchResult.RESULT_OK);
        verifyGlobalCallsResults(AppSearchResult.RESULT_OK);
    }

    @Test
    public void testDenylistMatchingCallingDatabase() throws Exception {
        String denylistString =
                "db=databaseName&apis=localSetSchema,globalGetSchema,localGetSchema,"
                        + "localGetNamespaces,localPutDocuments,globalGetDocuments,"
                        + "localGetDocuments,localSearch,globalSearch,globalGetNextPage,"
                        + "localGetNextPage,invalidateNextPageToken,localWriteSearchResultsToFile,"
                        + "localPutDocumentsFromFile,localSearchSuggestion,globalReportUsage,"
                        + "localReportUsage,localRemoveByDocumentId,localRemoveBySearch,"
                        + "localGetStorageInfo,flush,globalRegisterObserverCallback,"
                        + "globalUnregisterObserverCallback,initialize";
        DeviceConfig.setProperty(DeviceConfig.NAMESPACE_APPSEARCH,
                KEY_DENYLIST, denylistString, false);

        verifyLocalCallsResults(RESULT_DENIED);
        verifyGlobalCallsResults(AppSearchResult.RESULT_OK);

        // Add mocking to spy'd package manager to return current uid for package foo
        // This is necessary to pass call verification using a different package name
        PackageManager spyPackageManager = mContext.getPackageManager();
        int uid = AppSearchAttributionSource.createAttributionSource(mContext).getUid();
        doReturn(uid).when(spyPackageManager).getPackageUid(FOO_PACKAGE_NAME, /* flags= */ 0);
        // Specifically grant permission for report system usage to package foo
        doReturn(PackageManager.PERMISSION_GRANTED).when(spyPackageManager).checkPermission(
                READ_GLOBAL_APP_SEARCH_DATA, FOO_PACKAGE_NAME);

        // Change the calling package name used in the helper methods indirectly through a newly
        // wrapped context
        Context context = ApplicationProvider.getApplicationContext();
        mContext = new ContextWrapper(context) {
            @Override
            public String getPackageName() {
                return FOO_PACKAGE_NAME;
            }

            @Override
            public AttributionSource getAttributionSource() {
                return super.getAttributionSource().withPackageName(FOO_PACKAGE_NAME);
            }
        };

        // Confirm that we're using a different package name
        assertThat(mContext.getPackageName()).isEqualTo(FOO_PACKAGE_NAME);
        assertThat(AppSearchAttributionSource.createAttributionSource(mContext)
                .getPackageName()).isEqualTo(FOO_PACKAGE_NAME);

        verifyLocalCallsResults(RESULT_DENIED);
        verifyGlobalCallsResults(AppSearchResult.RESULT_OK);
    }

    @Test
    public void testDenylistNonMatchingCallingDatabase() throws Exception {
        String denylistString =
                "db=foo&apis=localSetSchema,globalGetSchema,localGetSchema,localGetNamespaces,"
                        + "localPutDocuments,globalGetDocuments,localGetDocuments,localSearch,"
                        + "globalSearch,globalGetNextPage,localGetNextPage,invalidateNextPageToken,"
                        + "localWriteSearchResultsToFile,localPutDocumentsFromFile,"
                        + "localSearchSuggestion,globalReportUsage,localReportUsage,"
                        + "localRemoveByDocumentId,localRemoveBySearch,localGetStorageInfo,flush,"
                        + "globalRegisterObserverCallback,globalUnregisterObserverCallback,"
                        + "initialize";
        DeviceConfig.setProperty(DeviceConfig.NAMESPACE_APPSEARCH,
                KEY_DENYLIST, denylistString, false);
        verifyLocalCallsResults(AppSearchResult.RESULT_OK);
        verifyGlobalCallsResults(AppSearchResult.RESULT_OK);
    }

    @Test
    public void testDenylistSomeApis() throws Exception {
        String denylistString =
                "pkg=com.android.appsearch.mockingservicestests&apis=localSetSchema,localGetSchema,"
                        + "localGetNamespaces";
        DeviceConfig.setProperty(DeviceConfig.NAMESPACE_APPSEARCH,
                KEY_DENYLIST, denylistString, false);
        // Specified APIs
        verifySetSchemaResult(RESULT_DENIED);
        verifyLocalGetSchemaResult(RESULT_DENIED);
        verifyGetNamespacesResult(RESULT_DENIED);
        // Everything else
        verifyPutDocumentsResult(AppSearchResult.RESULT_OK);
        verifyLocalGetDocumentsResult(AppSearchResult.RESULT_OK);
        verifySearchResult(AppSearchResult.RESULT_OK);
        verifyGlobalGetSchemaResult(AppSearchResult.RESULT_OK);
        verifyLocalGetNextPageResult(AppSearchResult.RESULT_OK);
        verifyWriteSearchResultsToFileResult(AppSearchResult.RESULT_OK);
        verifyPutDocumentsFromFileResult(AppSearchResult.RESULT_OK);
        verifySearchSuggestionResult(AppSearchResult.RESULT_OK);
        verifyLocalReportUsageResult(AppSearchResult.RESULT_OK);
        verifyRemoveByDocumentIdResult(AppSearchResult.RESULT_OK);
        verifyRemoveByQueryResult(AppSearchResult.RESULT_OK);
        verifyGetStorageInfoResult(AppSearchResult.RESULT_OK);
        verifyGlobalGetDocumentsResult(AppSearchResult.RESULT_OK);
        verifyGlobalSearchResult(AppSearchResult.RESULT_OK);
        verifyGlobalGetNextPageResult(AppSearchResult.RESULT_OK);
        verifyInvalidateNextPageTokenResult(AppSearchResult.RESULT_OK);
        verifyGlobalReportUsageResult(AppSearchResult.RESULT_OK);
        verifyPersistToDiskResult(AppSearchResult.RESULT_OK);
        verifyRegisterObserverCallbackResult(AppSearchResult.RESULT_OK);
        verifyUnregisterObserverCallbackResult(AppSearchResult.RESULT_OK);
        verifyInitializeResult(AppSearchResult.RESULT_OK);
    }

    @Test
    public void testAppSearchRateLimit_rateLimitOff_acceptTasksAndIgnoreCapacities()
            throws Exception {
        DeviceConfig.setProperty(DeviceConfig.NAMESPACE_APPSEARCH,
                KEY_RATE_LIMIT_ENABLED, Boolean.toString(false), false);
        DeviceConfig.setProperty(DeviceConfig.NAMESPACE_APPSEARCH,
                KEY_RATE_LIMIT_TASK_QUEUE_TOTAL_CAPACITY, Integer.toString(10), false);
        DeviceConfig.setProperty(DeviceConfig.NAMESPACE_APPSEARCH,
                KEY_RATE_LIMIT_TASK_QUEUE_PER_PACKAGE_CAPACITY_PERCENTAGE,
                Float.toString(0.8f),
                false);
        DeviceConfig.setProperty(DeviceConfig.NAMESPACE_APPSEARCH,
                KEY_RATE_LIMIT_API_COSTS,
                "localSearch:6;localSetSchema:9;localGetSchema:15",
                false);

        // All rate limits should be ignored when rate limit is off
        verifyLocalCallsResults(AppSearchResult.RESULT_OK);
        verifyGlobalCallsResults(AppSearchResult.RESULT_OK);
    }

    @Test
    public void testAppSearchRateLimit_rateLimitOn_dropTaskDueToCapacitiesExceeded()
            throws Exception {
        DeviceConfig.setProperty(DeviceConfig.NAMESPACE_APPSEARCH,
                KEY_RATE_LIMIT_ENABLED, Boolean.toString(true), false);
        DeviceConfig.setProperty(DeviceConfig.NAMESPACE_APPSEARCH,
                KEY_RATE_LIMIT_TASK_QUEUE_TOTAL_CAPACITY, Integer.toString(10), false);
        DeviceConfig.setProperty(DeviceConfig.NAMESPACE_APPSEARCH,
                KEY_RATE_LIMIT_TASK_QUEUE_PER_PACKAGE_CAPACITY_PERCENTAGE,
                Float.toString(0.8f),
                false);
        DeviceConfig.setProperty(DeviceConfig.NAMESPACE_APPSEARCH,
                KEY_RATE_LIMIT_API_COSTS,
                "localSearch:6;localSetSchema:9;localGetSchema:15",
                false);

        // Set Schema call is rejected because of per-package capacity exceeded
        verifySetSchemaResult(RESULT_RATE_LIMITED);
        // Set Schema call is rejected because of total capacity exceeded
        verifyLocalGetSchemaResult(RESULT_RATE_LIMITED);
        // Other calls should be fine
        verifySearchResult(AppSearchResult.RESULT_OK);
        verifyPutDocumentsResult(AppSearchResult.RESULT_OK);
        verifyLocalGetDocumentsResult(AppSearchResult.RESULT_OK);
        verifyLocalGetNextPageResult(AppSearchResult.RESULT_OK);
        verifyGlobalGetDocumentsResult(AppSearchResult.RESULT_OK);
        verifyGlobalSearchResult(AppSearchResult.RESULT_OK);
        verifyGlobalGetNextPageResult(AppSearchResult.RESULT_OK);
        verifyInvalidateNextPageTokenResult(AppSearchResult.RESULT_OK);
        verifyGlobalReportUsageResult(AppSearchResult.RESULT_OK);
        verifyPersistToDiskResult(AppSearchResult.RESULT_OK);
        verifyRegisterObserverCallbackResult(AppSearchResult.RESULT_OK);
        verifyUnregisterObserverCallbackResult(AppSearchResult.RESULT_OK);
        verifyInitializeResult(AppSearchResult.RESULT_OK);
    }

    @Test
    public void testAppSearchRateLimit_rateLimitOn_noTasksDropped() throws Exception {
        DeviceConfig.setProperty(DeviceConfig.NAMESPACE_APPSEARCH,
                KEY_RATE_LIMIT_ENABLED, Boolean.toString(true), false);
        DeviceConfig.setProperty(DeviceConfig.NAMESPACE_APPSEARCH,
                KEY_RATE_LIMIT_TASK_QUEUE_TOTAL_CAPACITY, Integer.toString(1000), false);
        DeviceConfig.setProperty(DeviceConfig.NAMESPACE_APPSEARCH,
                KEY_RATE_LIMIT_TASK_QUEUE_PER_PACKAGE_CAPACITY_PERCENTAGE,
                Float.toString(0.8f),
                false);
        DeviceConfig.setProperty(DeviceConfig.NAMESPACE_APPSEARCH,
                KEY_RATE_LIMIT_API_COSTS,
                "localSearch:6;localSetSchema:9;localGetSchema:15",
                false);

        verifyLocalCallsResults(AppSearchResult.RESULT_OK);
        verifyGlobalCallsResults(AppSearchResult.RESULT_OK);
    }

    @Test
    public void testAppSearchRateLimit_rateLimitOnToOff() throws Exception {
        DeviceConfig.setProperty(DeviceConfig.NAMESPACE_APPSEARCH,
                KEY_RATE_LIMIT_ENABLED, Boolean.toString(true), false);
        DeviceConfig.setProperty(DeviceConfig.NAMESPACE_APPSEARCH,
                KEY_RATE_LIMIT_TASK_QUEUE_TOTAL_CAPACITY, Integer.toString(10), false);
        DeviceConfig.setProperty(DeviceConfig.NAMESPACE_APPSEARCH,
                KEY_RATE_LIMIT_TASK_QUEUE_PER_PACKAGE_CAPACITY_PERCENTAGE,
                Float.toString(0.8f),
                false);
        DeviceConfig.setProperty(DeviceConfig.NAMESPACE_APPSEARCH,
                KEY_RATE_LIMIT_API_COSTS,
                "localSearch:6;localSetSchema:9;localGetSchema:15",
                false);
        verifySetSchemaResult(RESULT_RATE_LIMITED);
        verifyLocalGetSchemaResult(RESULT_RATE_LIMITED);
        verifySearchResult(AppSearchResult.RESULT_OK);
        verifyPutDocumentsResult(AppSearchResult.RESULT_OK);
        verifyLocalGetDocumentsResult(AppSearchResult.RESULT_OK);
        verifyLocalGetNextPageResult(AppSearchResult.RESULT_OK);
        verifyGlobalGetDocumentsResult(AppSearchResult.RESULT_OK);
        verifyGlobalSearchResult(AppSearchResult.RESULT_OK);
        verifyGlobalGetNextPageResult(AppSearchResult.RESULT_OK);
        verifyInvalidateNextPageTokenResult(AppSearchResult.RESULT_OK);
        verifyGlobalReportUsageResult(AppSearchResult.RESULT_OK);
        verifyPersistToDiskResult(AppSearchResult.RESULT_OK);
        verifyRegisterObserverCallbackResult(AppSearchResult.RESULT_OK);
        verifyUnregisterObserverCallbackResult(AppSearchResult.RESULT_OK);
        verifyInitializeResult(AppSearchResult.RESULT_OK);

        // All calls should be fine after switching rate limiting to off
        DeviceConfig.setProperty(DeviceConfig.NAMESPACE_APPSEARCH,
                KEY_RATE_LIMIT_ENABLED, Boolean.toString(false), false);
        verifyLocalCallsResults(AppSearchResult.RESULT_OK);
        verifyGlobalCallsResults(AppSearchResult.RESULT_OK);
    }

    @Test
    public void testAppSearchRateLimit_rateLimitOffToOn() throws Exception {
        DeviceConfig.setProperty(DeviceConfig.NAMESPACE_APPSEARCH,
                KEY_RATE_LIMIT_ENABLED, Boolean.toString(false), false);
        verifyLocalCallsResults(AppSearchResult.RESULT_OK);
        verifyGlobalCallsResults(AppSearchResult.RESULT_OK);

        DeviceConfig.setProperty(DeviceConfig.NAMESPACE_APPSEARCH,
                KEY_RATE_LIMIT_ENABLED, Boolean.toString(true), false);
        DeviceConfig.setProperty(DeviceConfig.NAMESPACE_APPSEARCH,
                KEY_RATE_LIMIT_TASK_QUEUE_TOTAL_CAPACITY, Integer.toString(5), false);
        DeviceConfig.setProperty(DeviceConfig.NAMESPACE_APPSEARCH,
                KEY_RATE_LIMIT_TASK_QUEUE_PER_PACKAGE_CAPACITY_PERCENTAGE,
                Float.toString(0.8f),
                false);
        DeviceConfig.setProperty(DeviceConfig.NAMESPACE_APPSEARCH,
                KEY_RATE_LIMIT_API_COSTS,
                "localSearch:6;localSetSchema:9;localGetSchema:15",
                false);
        // Some calls are rejected once rate limiting gets enabled
        verifySearchResult(RESULT_RATE_LIMITED);
        verifySetSchemaResult(RESULT_RATE_LIMITED);
        verifyLocalGetSchemaResult(RESULT_RATE_LIMITED);
        verifyPutDocumentsResult(AppSearchResult.RESULT_OK);
        verifyLocalGetDocumentsResult(AppSearchResult.RESULT_OK);
        verifyLocalGetNextPageResult(AppSearchResult.RESULT_OK);
        verifyGlobalGetDocumentsResult(AppSearchResult.RESULT_OK);
        verifyGlobalSearchResult(AppSearchResult.RESULT_OK);
        verifyGlobalGetNextPageResult(AppSearchResult.RESULT_OK);
        verifyInvalidateNextPageTokenResult(AppSearchResult.RESULT_OK);
        verifyGlobalReportUsageResult(AppSearchResult.RESULT_OK);
        verifyPersistToDiskResult(AppSearchResult.RESULT_OK);
        verifyRegisterObserverCallbackResult(AppSearchResult.RESULT_OK);
        verifyUnregisterObserverCallbackResult(AppSearchResult.RESULT_OK);
        verifyInitializeResult(AppSearchResult.RESULT_OK);
    }

    @Test
    public void testAppSearchRateLimit_rateLimitChangeToHigherLimit() throws Exception {
        DeviceConfig.setProperty(DeviceConfig.NAMESPACE_APPSEARCH,
                KEY_RATE_LIMIT_ENABLED, Boolean.toString(true), false);
        DeviceConfig.setProperty(DeviceConfig.NAMESPACE_APPSEARCH,
                KEY_RATE_LIMIT_TASK_QUEUE_TOTAL_CAPACITY, Integer.toString(10), false);
        DeviceConfig.setProperty(DeviceConfig.NAMESPACE_APPSEARCH,
                KEY_RATE_LIMIT_TASK_QUEUE_PER_PACKAGE_CAPACITY_PERCENTAGE,
                Float.toString(0.8f),
                false);
        DeviceConfig.setProperty(DeviceConfig.NAMESPACE_APPSEARCH,
                KEY_RATE_LIMIT_API_COSTS,
                "localSearch:6;localSetSchema:9;localGetSchema:15",
                false);
        verifySetSchemaResult(RESULT_RATE_LIMITED);
        verifyLocalGetSchemaResult(RESULT_RATE_LIMITED);

        verifySearchResult(AppSearchResult.RESULT_OK);
        verifyPutDocumentsResult(AppSearchResult.RESULT_OK);
        verifyLocalGetDocumentsResult(AppSearchResult.RESULT_OK);
        verifyLocalGetNextPageResult(AppSearchResult.RESULT_OK);
        verifyGlobalGetDocumentsResult(AppSearchResult.RESULT_OK);
        verifyGlobalSearchResult(AppSearchResult.RESULT_OK);
        verifyGlobalGetNextPageResult(AppSearchResult.RESULT_OK);
        verifyInvalidateNextPageTokenResult(AppSearchResult.RESULT_OK);
        verifyGlobalReportUsageResult(AppSearchResult.RESULT_OK);
        verifyPersistToDiskResult(AppSearchResult.RESULT_OK);
        verifyRegisterObserverCallbackResult(AppSearchResult.RESULT_OK);
        verifyUnregisterObserverCallbackResult(AppSearchResult.RESULT_OK);
        verifyInitializeResult(AppSearchResult.RESULT_OK);
        verifyGlobalCallsResults(AppSearchResult.RESULT_OK);

        // Only getSchema call should be rejected after setting to higher limit
        DeviceConfig.setProperty(DeviceConfig.NAMESPACE_APPSEARCH,
                KEY_RATE_LIMIT_TASK_QUEUE_TOTAL_CAPACITY, Integer.toString(15), false);
        verifyLocalGetSchemaResult(RESULT_RATE_LIMITED);

        verifySetSchemaResult(AppSearchResult.RESULT_OK);
        verifySearchResult(AppSearchResult.RESULT_OK);
        verifyPutDocumentsResult(AppSearchResult.RESULT_OK);
        verifyLocalGetDocumentsResult(AppSearchResult.RESULT_OK);
        verifyLocalGetNextPageResult(AppSearchResult.RESULT_OK);
        verifyGlobalGetDocumentsResult(AppSearchResult.RESULT_OK);
        verifyGlobalSearchResult(AppSearchResult.RESULT_OK);
        verifyGlobalGetNextPageResult(AppSearchResult.RESULT_OK);
        verifyInvalidateNextPageTokenResult(AppSearchResult.RESULT_OK);
        verifyGlobalReportUsageResult(AppSearchResult.RESULT_OK);
        verifyPersistToDiskResult(AppSearchResult.RESULT_OK);
        verifyRegisterObserverCallbackResult(AppSearchResult.RESULT_OK);
        verifyUnregisterObserverCallbackResult(AppSearchResult.RESULT_OK);
        verifyInitializeResult(AppSearchResult.RESULT_OK);
        verifyGlobalCallsResults(AppSearchResult.RESULT_OK);
    }

    @Test
    public void testAppSearchRateLimit_rateLimitChangeToLowerLimit() throws Exception {
        DeviceConfig.setProperty(DeviceConfig.NAMESPACE_APPSEARCH,
                KEY_RATE_LIMIT_ENABLED, Boolean.toString(true), false);
        DeviceConfig.setProperty(DeviceConfig.NAMESPACE_APPSEARCH,
                KEY_RATE_LIMIT_TASK_QUEUE_TOTAL_CAPACITY, Integer.toString(10), false);
        DeviceConfig.setProperty(DeviceConfig.NAMESPACE_APPSEARCH,
                KEY_RATE_LIMIT_TASK_QUEUE_PER_PACKAGE_CAPACITY_PERCENTAGE,
                Float.toString(0.8f),
                false);
        DeviceConfig.setProperty(DeviceConfig.NAMESPACE_APPSEARCH,
                KEY_RATE_LIMIT_API_COSTS,
                "localSearch:6;localSetSchema:9;localGetSchema:15",
                false);
        verifySetSchemaResult(RESULT_RATE_LIMITED);
        verifyLocalGetSchemaResult(RESULT_RATE_LIMITED);

        verifySearchResult(AppSearchResult.RESULT_OK);
        verifyPutDocumentsResult(AppSearchResult.RESULT_OK);
        verifyLocalGetDocumentsResult(AppSearchResult.RESULT_OK);
        verifyLocalGetNextPageResult(AppSearchResult.RESULT_OK);
        verifyGlobalGetDocumentsResult(AppSearchResult.RESULT_OK);
        verifyGlobalSearchResult(AppSearchResult.RESULT_OK);
        verifyGlobalGetNextPageResult(AppSearchResult.RESULT_OK);
        verifyInvalidateNextPageTokenResult(AppSearchResult.RESULT_OK);
        verifyGlobalReportUsageResult(AppSearchResult.RESULT_OK);
        verifyPersistToDiskResult(AppSearchResult.RESULT_OK);
        verifyRegisterObserverCallbackResult(AppSearchResult.RESULT_OK);
        verifyUnregisterObserverCallbackResult(AppSearchResult.RESULT_OK);
        verifyInitializeResult(AppSearchResult.RESULT_OK);
        verifyGlobalCallsResults(AppSearchResult.RESULT_OK);

        // Search call should also get rejected after setting a lower limit
        DeviceConfig.setProperty(DeviceConfig.NAMESPACE_APPSEARCH,
                KEY_RATE_LIMIT_TASK_QUEUE_PER_PACKAGE_CAPACITY_PERCENTAGE,
                Float.toString(0.5f),
                false);
        verifySearchResult(RESULT_RATE_LIMITED);
        verifySetSchemaResult(RESULT_RATE_LIMITED);
        verifyLocalGetSchemaResult(RESULT_RATE_LIMITED);

        verifyPutDocumentsResult(AppSearchResult.RESULT_OK);
        verifyLocalGetDocumentsResult(AppSearchResult.RESULT_OK);
        verifyLocalGetNextPageResult(AppSearchResult.RESULT_OK);
        verifyGlobalGetDocumentsResult(AppSearchResult.RESULT_OK);
        verifyGlobalSearchResult(AppSearchResult.RESULT_OK);
        verifyGlobalGetNextPageResult(AppSearchResult.RESULT_OK);
        verifyInvalidateNextPageTokenResult(AppSearchResult.RESULT_OK);
        verifyGlobalReportUsageResult(AppSearchResult.RESULT_OK);
        verifyPersistToDiskResult(AppSearchResult.RESULT_OK);
        verifyRegisterObserverCallbackResult(AppSearchResult.RESULT_OK);
        verifyUnregisterObserverCallbackResult(AppSearchResult.RESULT_OK);
        verifyInitializeResult(AppSearchResult.RESULT_OK);
        verifyGlobalCallsResults(AppSearchResult.RESULT_OK);
    }

    @Test
    public void testEnterpriseGetSchema_noEnterpriseUser_emptyResult() throws Exception {
        // Even on devices with an enterprise user, this test will run properly, since we haven't
        // unlocked the enterprise user for our local instance of AppSearchManagerService
        TestResultCallback callback = new TestResultCallback();
        mAppSearchManagerServiceStub.getSchema(
                new GetSchemaAidlRequest(
                        AppSearchAttributionSource.createAttributionSource(mContext),
                        mContext.getPackageName(), DATABASE_NAME, mUserHandle,
                        BINDER_CALL_START_TIME, /* isForEnterprise= */ true),
                callback);
        AppSearchResult<GetSchemaResponse> result =
                (AppSearchResult<GetSchemaResponse>) callback.get();
        assertThat(result.getResultCode()).isEqualTo(AppSearchResult.RESULT_OK);
        assertThat(result.getResultValue().getSchemas()).isEmpty();
        // No CallStats logged since we returned early
        verify(mLogger, timeout(1000).times(0)).logStats(any(CallStats.class));
    }

    @Test
    public void testEnterpriseGetDocuments_noEnterpriseUser_emptyResult() throws Exception {
        // Even on devices with an enterprise user, this test will run properly, since we haven't
        // unlocked the enterprise user for our local instance of AppSearchManagerService
        TestBatchResultErrorCallback callback = new TestBatchResultErrorCallback();
        mAppSearchManagerServiceStub.getDocuments(
                AppSearchAttributionSource.createAttributionSource(mContext),
                mContext.getPackageName(), DATABASE_NAME, NAMESPACE,
                /* ids= */ Collections.emptyList(), /* typePropertyPaths= */ Collections.emptyMap(),
                mUserHandle, BINDER_CALL_START_TIME, /* isForEnterprise= */ true, callback);
        assertThat(callback.get()).isNull(); // null means there wasn't an error
        assertThat(callback.getBatchResult().getAll()).isEmpty();
        // No CallStats logged since we returned early
        verify(mLogger, timeout(1000).times(0)).logStats(any(CallStats.class));
    }

    @Test
    public void testEnterpriseGlobalSearch_noEnterpriseUser_emptyResult() throws Exception {
        // Even on devices with an enterprise user, this test will run properly, since we haven't
        // unlocked the enterprise user for our local instance of AppSearchManagerService
        TestResultCallback callback = new TestResultCallback();
        mAppSearchManagerServiceStub.globalSearch(new GlobalSearchAidlRequest(
                AppSearchAttributionSource.createAttributionSource(mContext),
                /* searchExpression= */ "", EMPTY_SEARCH_SPEC, mUserHandle, BINDER_CALL_START_TIME,
                /* isForEnterprise= */ true), callback);
        AppSearchResult<SearchResultPage> result =
                (AppSearchResult<SearchResultPage>) callback.get();
        assertThat(result.getResultCode()).isEqualTo(AppSearchResult.RESULT_OK);
        assertThat(result.getResultValue().getResults()).isEmpty();
        // No CallStats logged since we returned early
        verify(mLogger, timeout(1000).times(0)).logStats(any(CallStats.class));
    }

    @Test
    public void testEnterpriseGetNextPage_noEnterpriseUser_emptyResult() throws Exception {
        // Even on devices with an enterprise user, this test will run properly, since we haven't
        // unlocked the enterprise user for our local instance of AppSearchManagerService
        TestResultCallback callback = new TestResultCallback();
        mAppSearchManagerServiceStub.getNextPage(new GetNextPageAidlRequest(
                AppSearchAttributionSource.createAttributionSource(mContext),
                /* databaseName= */ null,/* nextPageToken= */ 0,
                AppSearchSchema.StringPropertyConfig.JOINABLE_VALUE_TYPE_QUALIFIED_ID, mUserHandle,
                BINDER_CALL_START_TIME, /* isForEnterprise= */ true), callback);
        AppSearchResult<SearchResultPage> result =
                (AppSearchResult<SearchResultPage>) callback.get();
        assertThat(result.getResultCode()).isEqualTo(AppSearchResult.RESULT_OK);
        assertThat(result.getResultValue().getResults()).isEmpty();
        // No CallStats logged since we returned early
        verify(mLogger, timeout(1000).times(0)).logStats(any(CallStats.class));
    }

    @Test
    public void testEnterpriseInvalidateNextPageToken_noEnterpriseUser() throws Exception {
        // Even on devices with an enterprise user, this test will run properly, since we haven't
        // unlocked the enterprise user for our local instance of AppSearchManagerService
        mAppSearchManagerServiceStub.invalidateNextPageToken(new InvalidateNextPageTokenAidlRequest(
                AppSearchAttributionSource.createAttributionSource(mContext),
                /* nextPageToken= */ 0, mUserHandle, BINDER_CALL_START_TIME,
                /* isForEnterprise= */ true));
        // No CallStats logged since we returned early
        verify(mLogger, timeout(1000).times(0)).logStats(any(CallStats.class));
    }

    private void verifyLocalCallsResults(int resultCode) throws Exception {
        // These APIs are local calls since they specify a database. If the API specifies a target
        // package, then the target package matches the calling package
        verifySetSchemaResult(resultCode);
        verifyLocalGetSchemaResult(resultCode);
        verifyGetNamespacesResult(resultCode);
        verifyPutDocumentsResult(resultCode);
        verifyLocalGetDocumentsResult(resultCode);
        verifySearchResult(resultCode);
        verifyLocalGetNextPageResult(resultCode);
        verifyWriteSearchResultsToFileResult(resultCode);
        verifyPutDocumentsFromFileResult(resultCode);
        verifySearchSuggestionResult(resultCode);
        verifyLocalReportUsageResult(resultCode);
        verifyRemoveByDocumentIdResult(resultCode);
        verifyRemoveByQueryResult(resultCode);
        verifyGetStorageInfoResult(resultCode);
    }

    private void verifyGlobalCallsResults(int resultCode) throws Exception {
        // These APIs are global calls since either they do not specify a database or if they do,
        // they specify the database along with a target package that does not match the calling
        // package
        verifyGlobalGetSchemaResult(resultCode);
        verifyGlobalGetDocumentsResult(resultCode);
        verifyGlobalSearchResult(resultCode);
        verifyGlobalGetNextPageResult(resultCode);
        verifyInvalidateNextPageTokenResult(resultCode);
        verifyGlobalReportUsageResult(resultCode);
        verifyPersistToDiskResult(resultCode);
        verifyRegisterObserverCallbackResult(resultCode);
        verifyUnregisterObserverCallbackResult(resultCode);
        verifyInitializeResult(resultCode);
    }

    private void verifySetSchemaResult(int resultCode) throws Exception {
        TestResultCallback callback = new TestResultCallback();
        mAppSearchManagerServiceStub.setSchema(
                new SetSchemaAidlRequest(
                AppSearchAttributionSource
                    .createAttributionSource(mContext), DATABASE_NAME,
                /* schemaBundles= */ Collections.emptyList(),
                /* visibilityBundles= */ Collections.emptyList(), /* forceOverride= */ false,
                /* schemaVersion= */ 0, mUserHandle, BINDER_CALL_START_TIME,
                SchemaMigrationStats.FIRST_CALL_GET_INCOMPATIBLE),
                callback);
        verifyCallResult(resultCode, CallStats.CALL_TYPE_SET_SCHEMA, callback.get());
    }

    private void verifyLocalGetSchemaResult(int resultCode) throws Exception {
        TestResultCallback callback = new TestResultCallback();
        mAppSearchManagerServiceStub.getSchema(
                new GetSchemaAidlRequest(
                        AppSearchAttributionSource.createAttributionSource(mContext),
                        mContext.getPackageName(), DATABASE_NAME, mUserHandle,
                        BINDER_CALL_START_TIME, /* isForEnterprise= */ false),
                callback);
        verifyCallResult(resultCode, CallStats.CALL_TYPE_GET_SCHEMA, callback.get());
    }

    private void verifyGlobalGetSchemaResult(int resultCode) throws Exception {
        String otherPackageName = mContext.getPackageName() + "foo";
        TestResultCallback callback = new TestResultCallback();
        mAppSearchManagerServiceStub.getSchema(
                new GetSchemaAidlRequest(
                        AppSearchAttributionSource.createAttributionSource(mContext),
                        otherPackageName, DATABASE_NAME, mUserHandle, BINDER_CALL_START_TIME,
                        /* isForEnterprise= */ false),
                callback);
        verifyCallResult(resultCode, CallStats.CALL_TYPE_GLOBAL_GET_SCHEMA, callback.get());
    }

    private void verifyGetNamespacesResult(int resultCode) throws Exception {
        TestResultCallback callback = new TestResultCallback();
        mAppSearchManagerServiceStub.getNamespaces(
                new GetNamespacesAidlRequest(
                        AppSearchAttributionSource.createAttributionSource(mContext), DATABASE_NAME,
                        mUserHandle, BINDER_CALL_START_TIME),
                callback);
        verifyCallResult(resultCode, CallStats.CALL_TYPE_GET_NAMESPACES, callback.get());
    }

    private void verifyPutDocumentsResult(int resultCode) throws Exception {
        TestBatchResultErrorCallback callback = new TestBatchResultErrorCallback();
        mAppSearchManagerServiceStub.putDocuments(
                new PutDocumentsAidlRequest(
                        AppSearchAttributionSource.createAttributionSource(mContext), DATABASE_NAME,
                        new DocumentsParcel(Collections.emptyList(), Collections.emptyList()),
                        mUserHandle, BINDER_CALL_START_TIME), callback);
        verifyCallResult(resultCode, CallStats.CALL_TYPE_PUT_DOCUMENTS, callback.get());
    }

    private void verifyLocalGetDocumentsResult(int resultCode) throws Exception {
        TestBatchResultErrorCallback callback = new TestBatchResultErrorCallback();
        mAppSearchManagerServiceStub.getDocuments(
                AppSearchAttributionSource.createAttributionSource(mContext),
                mContext.getPackageName(), DATABASE_NAME, NAMESPACE,
                /* ids= */ Collections.emptyList(), /* typePropertyPaths= */ Collections.emptyMap(),
                mUserHandle, BINDER_CALL_START_TIME, /* isForEnterprise= */ false, callback);
        verifyCallResult(resultCode, CallStats.CALL_TYPE_GET_DOCUMENTS, callback.get());
    }

    private void verifyGlobalGetDocumentsResult(int resultCode) throws Exception {
        String otherPackageName = mContext.getPackageName() + "foo";
        TestBatchResultErrorCallback callback = new TestBatchResultErrorCallback();
        mAppSearchManagerServiceStub.getDocuments(
                AppSearchAttributionSource.createAttributionSource(mContext),
                otherPackageName, DATABASE_NAME, NAMESPACE, /* ids= */ Collections.emptyList(),
                /* typePropertyPaths= */ Collections.emptyMap(), mUserHandle,
                BINDER_CALL_START_TIME, /* isForEnterprise= */ false, callback);
        verifyCallResult(resultCode, CallStats.CALL_TYPE_GLOBAL_GET_DOCUMENT_BY_ID, callback.get());
    }

    private void verifySearchResult(int resultCode) throws Exception {
        TestResultCallback callback = new TestResultCallback();
        mAppSearchManagerServiceStub.search(
                new SearchAidlRequest(AppSearchAttributionSource.createAttributionSource(mContext),
                        DATABASE_NAME,/* searchExpression= */ "", EMPTY_SEARCH_SPEC, mUserHandle,
                        BINDER_CALL_START_TIME), callback);
        verifyCallResult(resultCode, CallStats.CALL_TYPE_SEARCH, callback.get());
    }

    private void verifyGlobalSearchResult(int resultCode) throws Exception {
        TestResultCallback callback = new TestResultCallback();
        mAppSearchManagerServiceStub.globalSearch(new GlobalSearchAidlRequest(
                AppSearchAttributionSource.createAttributionSource(mContext),
                /* searchExpression= */ "", EMPTY_SEARCH_SPEC, mUserHandle, BINDER_CALL_START_TIME,
                /* isForEnterprise= */ false), callback);
        verifyCallResult(resultCode, CallStats.CALL_TYPE_GLOBAL_SEARCH, callback.get());
    }

    private void verifyLocalGetNextPageResult(int resultCode) throws Exception {
        TestResultCallback callback = new TestResultCallback();
        mAppSearchManagerServiceStub.getNextPage(new GetNextPageAidlRequest(
                AppSearchAttributionSource.createAttributionSource(mContext), DATABASE_NAME,
                /* nextPageToken= */ 0,
                AppSearchSchema.StringPropertyConfig.JOINABLE_VALUE_TYPE_QUALIFIED_ID, mUserHandle,
                BINDER_CALL_START_TIME, /* isForEnterprise= */ false), callback);
        verifyCallResult(resultCode, CallStats.CALL_TYPE_GET_NEXT_PAGE, callback.get());
    }

    private void verifyGlobalGetNextPageResult(int resultCode) throws Exception {
        TestResultCallback callback = new TestResultCallback();
        mAppSearchManagerServiceStub.getNextPage(new GetNextPageAidlRequest(
                AppSearchAttributionSource.createAttributionSource(mContext),
                /* databaseName= */ null, /* nextPageToken= */ 0,
                AppSearchSchema.StringPropertyConfig.JOINABLE_VALUE_TYPE_QUALIFIED_ID, mUserHandle,
                BINDER_CALL_START_TIME, /* isForEnterprise= */ false), callback);
        verifyCallResult(resultCode, CallStats.CALL_TYPE_GLOBAL_GET_NEXT_PAGE, callback.get());
    }

    private void verifyInvalidateNextPageTokenResult(int resultCode) throws Exception {
        mAppSearchManagerServiceStub.invalidateNextPageToken(new InvalidateNextPageTokenAidlRequest(
                AppSearchAttributionSource.createAttributionSource(mContext),
                /* nextPageToken= */ 0, mUserHandle, BINDER_CALL_START_TIME,
                /* isForEnterprise= */ false));
        verifyCallResult(resultCode, CallStats.CALL_TYPE_INVALIDATE_NEXT_PAGE_TOKEN, /* result= */
                null);
    }

    private void verifyWriteSearchResultsToFileResult(int resultCode) throws Exception {
        File tempFile = mTemporaryFolder.newFile();
        FileDescriptor fd = IoBridge.open(tempFile.getPath(), O_WRONLY);
        TestResultCallback callback = new TestResultCallback();
        mAppSearchManagerServiceStub.writeSearchResultsToFile(
                new WriteSearchResultsToFileAidlRequest(
                        AppSearchAttributionSource.createAttributionSource(mContext), DATABASE_NAME,
                        new ParcelFileDescriptor(fd), /* searchExpression= */ "", EMPTY_SEARCH_SPEC,
                        mUserHandle, BINDER_CALL_START_TIME), callback);
        verifyCallResult(resultCode, CallStats.CALL_TYPE_WRITE_SEARCH_RESULTS_TO_FILE,
                callback.get());
    }

    private void verifyPutDocumentsFromFileResult(int resultCode) throws Exception {
        File tempFile = mTemporaryFolder.newFile();
        FileDescriptor fd = IoBridge.open(tempFile.getPath(), O_RDONLY);
        TestResultCallback callback = new TestResultCallback();
        mAppSearchManagerServiceStub.putDocumentsFromFile(new PutDocumentsFromFileAidlRequest(
                AppSearchAttributionSource.createAttributionSource(mContext), DATABASE_NAME,
                new ParcelFileDescriptor(fd), mUserHandle,
                new SchemaMigrationStats.Builder(mContext.getPackageName(), DATABASE_NAME).build(),
                /* totalLatencyStartTimeMillis= */ 0, BINDER_CALL_START_TIME), callback);
        verifyCallResult(resultCode, CallStats.CALL_TYPE_PUT_DOCUMENTS_FROM_FILE, callback.get());
    }

    private void verifySearchSuggestionResult(int resultCode) throws Exception {
        SearchSuggestionSpec searchSuggestionSpec =
            new SearchSuggestionSpec.Builder(/*maximumResultCount=*/1).build();
        TestResultCallback callback = new TestResultCallback();
        mAppSearchManagerServiceStub.searchSuggestion(
                new SearchSuggestionAidlRequest(
                        AppSearchAttributionSource.createAttributionSource(mContext),
                        DATABASE_NAME, /* suggestionQueryExpression= */ "foo", searchSuggestionSpec,
                        mUserHandle, BINDER_CALL_START_TIME),
                callback);
        verifyCallResult(resultCode, CallStats.CALL_TYPE_SEARCH_SUGGESTION, callback.get());
    }

    private void verifyLocalReportUsageResult(int resultCode) throws Exception {
        setUpTestSchema(mContext.getPackageName(), DATABASE_NAME);
        setUpTestDocument(mContext.getPackageName(), DATABASE_NAME, NAMESPACE, ID);
        TestResultCallback callback = new TestResultCallback();
        mAppSearchManagerServiceStub.reportUsage(
                AppSearchAttributionSource.createAttributionSource(mContext),
                mContext.getPackageName(), DATABASE_NAME, NAMESPACE, ID,
                /* usageTimestampMillis= */ 0, /* systemUsage= */ false, mUserHandle,
                BINDER_CALL_START_TIME, callback);
        verifyCallResult(resultCode, CallStats.CALL_TYPE_REPORT_USAGE, callback.get());
        removeTestSchema(mContext.getPackageName(), DATABASE_NAME);
    }

    private void verifyGlobalReportUsageResult(int resultCode) throws Exception {
        // Grant system access for global report usage
        mUiAutomation.adoptShellPermissionIdentity(Manifest.permission.READ_GLOBAL_APP_SEARCH_DATA);
        try {
            String otherPackageName = mContext.getPackageName() + "foo";
            setUpTestSchema(otherPackageName, DATABASE_NAME);
            setUpTestDocument(otherPackageName, DATABASE_NAME, NAMESPACE, ID);
            TestResultCallback callback = new TestResultCallback();
            mAppSearchManagerServiceStub.reportUsage(
                    AppSearchAttributionSource.createAttributionSource(mContext),
                    otherPackageName, DATABASE_NAME, NAMESPACE, ID, /* usageTimestampMillis= */ 0,
                    /* systemUsage= */ true, mUserHandle, BINDER_CALL_START_TIME, callback);
            verifyCallResult(resultCode, CallStats.CALL_TYPE_REPORT_SYSTEM_USAGE, callback.get());
            removeTestSchema(otherPackageName, DATABASE_NAME);
        } finally {
            mUiAutomation.dropShellPermissionIdentity();
        }
    }

    private void verifyRemoveByDocumentIdResult(int resultCode) throws Exception {
        TestBatchResultErrorCallback callback = new TestBatchResultErrorCallback();
        mAppSearchManagerServiceStub.removeByDocumentId(
                new RemoveByDocumentIdAidlRequest(
                        AppSearchAttributionSource.createAttributionSource(mContext),
                        DATABASE_NAME, NAMESPACE, /* ids= */ Collections.emptyList(), mUserHandle,
                        BINDER_CALL_START_TIME),
                callback);
        verifyCallResult(resultCode, CallStats.CALL_TYPE_REMOVE_DOCUMENTS_BY_ID, callback.get());
    }

    private void verifyRemoveByQueryResult(int resultCode) throws Exception {
        TestResultCallback callback = new TestResultCallback();
        mAppSearchManagerServiceStub.removeByQuery(
                new RemoveByQueryAidlRequest(
                        AppSearchAttributionSource.createAttributionSource(mContext), DATABASE_NAME,
                        /* queryExpression= */ "", EMPTY_SEARCH_SPEC, mUserHandle,
                        BINDER_CALL_START_TIME),
                callback);
        verifyCallResult(resultCode, CallStats.CALL_TYPE_REMOVE_DOCUMENTS_BY_SEARCH,
                callback.get());
    }

    private void verifyGetStorageInfoResult(int resultCode) throws Exception {
        TestResultCallback callback = new TestResultCallback();
        mAppSearchManagerServiceStub.getStorageInfo(
                new GetStorageInfoAidlRequest(
                        AppSearchAttributionSource.createAttributionSource(mContext), DATABASE_NAME,
                        mUserHandle, BINDER_CALL_START_TIME),
                callback);
        verifyCallResult(resultCode, CallStats.CALL_TYPE_GET_STORAGE_INFO, callback.get());
    }

    private void verifyPersistToDiskResult(int resultCode) throws Exception {
        mAppSearchManagerServiceStub.persistToDisk(
                new PersistToDiskAidlRequest(
                        AppSearchAttributionSource.createAttributionSource(mContext), mUserHandle,
                        BINDER_CALL_START_TIME));
        verifyCallResult(resultCode, CallStats.CALL_TYPE_FLUSH, /* result= */ null);
    }

    private void verifyRegisterObserverCallbackResult(int resultCode) throws Exception {
        AppSearchResultParcel<Void> resultParcel =
                mAppSearchManagerServiceStub.registerObserverCallback(
                        new RegisterObserverCallbackAidlRequest(
                                AppSearchAttributionSource.createAttributionSource(mContext),
                                mContext.getPackageName(),
                                new ObserverSpec.Builder().build(),
                                mUserHandle,
                                BINDER_CALL_START_TIME),
                        new IAppSearchObserverProxy.Stub() {
                            @Override
                            public void onSchemaChanged(String packageName, String databaseName,
                                    List<String> changedSchemaNames) throws RemoteException {
                            }

                            @Override
                            public void onDocumentChanged(String packageName, String databaseName,
                                    String namespace, String schemaName,
                                    List<String> changedDocumentIds) throws RemoteException {
                            }
                        });
        verifyCallResult(resultCode, CallStats.CALL_TYPE_REGISTER_OBSERVER_CALLBACK,
                resultParcel.getResult());
    }

    private void verifyUnregisterObserverCallbackResult(int resultCode) throws Exception {
        AppSearchResultParcel<Void> resultParcel =
                mAppSearchManagerServiceStub.unregisterObserverCallback(
                        new UnregisterObserverCallbackAidlRequest(
                                AppSearchAttributionSource.createAttributionSource(mContext),
                                mContext.getPackageName(), mUserHandle,
                                BINDER_CALL_START_TIME),
                        new IAppSearchObserverProxy.Stub() {
                            @Override
                            public void onSchemaChanged(String packageName, String databaseName,
                                    List<String> changedSchemaNames) throws RemoteException {
                            }

                            @Override
                            public void onDocumentChanged(String packageName, String databaseName,
                                    String namespace, String schemaName,
                                    List<String> changedDocumentIds) throws RemoteException {
                            }
                        });
        verifyCallResult(resultCode, CallStats.CALL_TYPE_UNREGISTER_OBSERVER_CALLBACK,
                resultParcel.getResult());
    }

    private void verifyInitializeResult(int resultCode) throws Exception {
        TestResultCallback callback = new TestResultCallback();
        mAppSearchManagerServiceStub.initialize(
                new InitializeAidlRequest(
                        AppSearchAttributionSource.createAttributionSource(mContext), mUserHandle,
                        BINDER_CALL_START_TIME),
                callback);
        if (resultCode == RESULT_DENIED) {
            verify(mLogger, never()).logStats(any(CallStats.class));
        } else {
            verifyCallResult(resultCode, CallStats.CALL_TYPE_INITIALIZE, /* result= */ null);
        }
        assertThat(callback.get().getResultCode()).isEqualTo(resultCode);
    }

    private void verifyCallResult(int resultCode, int callType, AppSearchResult<?> result) {
        ArgumentCaptor<CallStats> captor = ArgumentCaptor.forClass(CallStats.class);
        verify(mLogger, timeout(1000).times(1)).logStats(captor.capture());
        assertThat(captor.getValue().getCallType()).isEqualTo(callType);
        assertThat(captor.getValue().getStatusCode()).isEqualTo(resultCode);
        assertThat(captor.getValue().getEstimatedBinderLatencyMillis()).isGreaterThan(0);
        clearInvocations(mLogger);
        // Not all calls return a result
        if (result != null) {
            assertThat(result.getResultCode()).isEqualTo(resultCode);
        }
    }

    private void setUpTestSchema(String packageName, String databaseName) throws Exception {
        // Insert schema
        List<AppSearchSchema> schemas = Collections.singletonList(
                new AppSearchSchema.Builder("type").build());
        InternalSetSchemaResponse internalSetSchemaResponse =
                mUserInstance.getAppSearchImpl().setSchema(packageName, databaseName, schemas,
                        /* visibilityDocuments= */ Collections.emptyList(),
                        /* forceOverride= */ false,
                        /* version= */ 0,
                        /* setSchemaStatsBuilder= */ null);
        assertThat(internalSetSchemaResponse.isSuccess()).isTrue();
    }

    private void setUpTestDocument(String packageName, String databaseName, String namespace,
            String id) throws Exception {
        // Insert a document
        GenericDocument document = new GenericDocument.Builder<>(namespace, id, "type").build();
        mUserInstance.getAppSearchImpl().putDocument(packageName, databaseName, document,
                /* sendChangeNotifications= */ false,
                /* logger= */ null);
    }

    private void removeTestSchema(String packageName, String databaseName) throws Exception {
        InternalSetSchemaResponse internalSetSchemaResponse =
                mUserInstance.getAppSearchImpl().setSchema(packageName, databaseName,
                        /* schemas= */ Collections.emptyList(),
                        /* visibilityDocuments= */ Collections.emptyList(),
                        /* forceOverride= */ true,
                        /* version= */ 0,
                        /* setSchemaStatsBuilder= */ null);
        assertThat(internalSetSchemaResponse.isSuccess()).isTrue();
    }

    private static class MockServiceManager implements StaticMockFixture {
        ArgumentCaptor<IAppSearchManager.Stub> mStubCaptor = ArgumentCaptor.forClass(
                IAppSearchManager.Stub.class);

        @Override
        public StaticMockitoSessionBuilder setUpMockedClasses(
                @NonNull StaticMockitoSessionBuilder sessionBuilder) {
            sessionBuilder.mockStatic(LocalManagerRegistry.class);
            sessionBuilder.spyStatic(ServiceManager.class);
            return sessionBuilder;
        }

        @Override
        public void setUpMockBehaviors() {
            ExtendedMockito.doReturn(mock(StorageStatsManagerLocal.class)).when(
                    () -> LocalManagerRegistry.getManager(StorageStatsManagerLocal.class));
            ExtendedMockito.doNothing().when(
                    () -> ServiceManager.addService(anyString(), mStubCaptor.capture(),
                            anyBoolean(), anyInt()));
        }

        @Override
        public void tearDown() {
        }
    }

    private static final class TestResultCallback extends IAppSearchResultCallback.Stub {
        private final SettableFuture<AppSearchResult<?>> future = SettableFuture.create();

        @Override
        public void onResult(AppSearchResultParcel appSearchResultParcel) {
            future.set(appSearchResultParcel.getResult());
        }

        public AppSearchResult<?> get() throws InterruptedException, ExecutionException {
            return future.get();
        }
    }

    private static final class TestBatchResultErrorCallback extends
            IAppSearchBatchResultCallback.Stub {
        private final SettableFuture<AppSearchResult<?>> future = SettableFuture.create();
        private final SettableFuture<AppSearchBatchResult<?, ?>> batchFuture =
                SettableFuture.create();

        @Override
        public void onResult(AppSearchBatchResultParcel appSearchBatchResultParcel) {
            future.set(null);
            batchFuture.set(appSearchBatchResultParcel.getResult());
        }

        @Override
        public void onSystemError(AppSearchResultParcel appSearchResultParcel) {
            future.set(appSearchResultParcel.getResult());
            batchFuture.set(null);
        }

        public AppSearchResult<?> get() throws InterruptedException, ExecutionException {
            return future.get();
        }

        public AppSearchBatchResult<?, ?> getBatchResult()
                throws InterruptedException, ExecutionException {
            return batchFuture.get();
        }
    }
}
