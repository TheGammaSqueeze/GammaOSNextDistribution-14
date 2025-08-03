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

package com.android.adservices.service.appsetid;

import static android.adservices.common.AdServicesStatusUtils.FAILURE_REASON_CALLING_PACKAGE_DOES_NOT_BELONG_TO_CALLING_ID;
import static android.adservices.common.AdServicesStatusUtils.FAILURE_REASON_CALLING_PACKAGE_NOT_FOUND;
import static android.adservices.common.AdServicesStatusUtils.FAILURE_REASON_PACKAGE_NOT_IN_ALLOWLIST;
import static android.adservices.common.AdServicesStatusUtils.FAILURE_REASON_UNSET;
import static android.adservices.common.AdServicesStatusUtils.STATUS_CALLER_NOT_ALLOWED;
import static android.adservices.common.AdServicesStatusUtils.STATUS_RATE_LIMIT_REACHED;
import static android.adservices.common.AdServicesStatusUtils.STATUS_UNAUTHORIZED;

import static com.android.adservices.mockito.ExtendedMockitoExpectations.doNothingOnErrorLogUtilError;
import static com.android.adservices.mockito.MockitoExpectations.mockLogApiCallStats;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_API_CALLED;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_API_CALLED__API_CLASS__APPSETID;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_API_CALLED__API_NAME__GET_APPSETID;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;
import static com.android.modules.utils.testing.ExtendedMockitoRule.MockStatic;
import static com.android.modules.utils.testing.ExtendedMockitoRule.SpyStatic;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.adservices.appsetid.GetAppSetIdParam;
import android.adservices.appsetid.GetAppSetIdResult;
import android.adservices.appsetid.IGetAppSetIdCallback;
import android.adservices.common.CallerMetadata;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Binder;
import android.os.Process;

import androidx.annotation.NonNull;

import com.android.adservices.common.AdServicesExtendedMockitoTestCase;
import com.android.adservices.common.IntFailureSyncCallback;
import com.android.adservices.common.NoFailureSyncCallback;
import com.android.adservices.common.RequiresSdkLevelAtLeastT;
import com.android.adservices.errorlogging.ErrorLogUtil;
import com.android.adservices.service.Flags;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.common.AppImportanceFilter;
import com.android.adservices.service.common.AppImportanceFilter.WrongCallingApplicationStateException;
import com.android.adservices.service.common.Throttler;
import com.android.adservices.service.stats.AdServicesLogger;
import com.android.adservices.service.stats.AdServicesLoggerImpl;
import com.android.adservices.service.stats.ApiCallStats;
import com.android.adservices.shared.util.Clock;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.stubbing.Answer;

import java.util.concurrent.CountDownLatch;

/** Unit test for {@link com.android.adservices.service.appsetid.AppSetIdServiceImpl}. */
@MockStatic(Binder.class)
@SpyStatic(FlagsFactory.class)
@SpyStatic(ErrorLogUtil.class)
public final class AppSetIdServiceImplTest extends AdServicesExtendedMockitoTestCase {

    private static final String TEST_APP_PACKAGE_NAME =
            "com.android.adservices.servicecoreappsetidtest";
    private static final String INVALID_PACKAGE_NAME = "com.do_not_exists";
    private static final String SOME_SDK_NAME = "SomeSdkName";
    private static final int BINDER_CONNECTION_TIMEOUT_MS = 5_000;
    private static final String SDK_PACKAGE_NAME = "test_package_name";
    private static final String APPSETID_API_ALLOW_LIST =
            "com.android.adservices.servicecoreappsetidtest";
    private static final int SANDBOX_UID = 25000;
    private final AdServicesLogger mAdServicesLogger =
            Mockito.spy(AdServicesLoggerImpl.getInstance());

    private CallerMetadata mCallerMetadata;
    private AppSetIdWorker mAppSetIdWorker;
    private GetAppSetIdParam mRequest;

    @Mock private PackageManager mPackageManager;
    @Mock private Flags mMockFlags;
    @Mock private Clock mClock;
    @Mock private Context mMockSdkContext;
    @Mock private Throttler mMockThrottler;
    @Mock private AppSetIdServiceImpl mAppSetIdServiceImpl;
    @Mock private AppImportanceFilter mMockAppImportanceFilter;

    @Before
    public void setup() throws Exception {
        mAppSetIdWorker = Mockito.spy(AppSetIdWorker.getInstance());
        doReturn(null).when(mAppSetIdWorker).getService();

        when(mClock.elapsedRealtime()).thenReturn(150L, 200L);
        mCallerMetadata = new CallerMetadata.Builder().setBinderElapsedTimestamp(100L).build();
        mRequest =
                new GetAppSetIdParam.Builder()
                        .setAppPackageName(TEST_APP_PACKAGE_NAME)
                        .setSdkPackageName(SDK_PACKAGE_NAME)
                        .build();

        when(mMockSdkContext.getPackageManager()).thenReturn(mPackageManager);
        when(mPackageManager.getPackageUid(TEST_APP_PACKAGE_NAME, 0)).thenReturn(Process.myUid());

        // Put this test app into bypass list to bypass Allow-list check.
        when(mMockFlags.getPpapiAppAllowList()).thenReturn(APPSETID_API_ALLOW_LIST);

        // Rate Limit is not reached.
        when(mMockThrottler.tryAcquire(
                        eq(Throttler.ApiKey.APPSETID_API_APP_PACKAGE_NAME), anyString()))
                .thenReturn(true);

        extendedMockito.mockGetFlags(mMockFlags);
    }

    @Test
    public void checkAllowList_emptyAllowList() throws InterruptedException {
        when(Binder.getCallingUidOrThrow()).thenReturn(Process.myUid());
        // Empty allow list.
        when(mMockFlags.getPpapiAppAllowList()).thenReturn("");
        invokeGetAppSetIdAndVerifyError(
                mContext,
                STATUS_CALLER_NOT_ALLOWED, /* checkLoggingStatus */
                true,
                FAILURE_REASON_PACKAGE_NOT_IN_ALLOWLIST);
    }

    @Test
    public void checkThrottler_rateLimitReached_forAppPackageName() throws InterruptedException {
        // App calls AppSetId API directly, not via an SDK.
        GetAppSetIdParam request =
                new GetAppSetIdParam.Builder()
                        .setAppPackageName(TEST_APP_PACKAGE_NAME)
                        .setSdkPackageName(SDK_PACKAGE_NAME)
                        .build();

        // Rate Limit Reached.
        when(mMockThrottler.tryAcquire(
                        eq(Throttler.ApiKey.APPSETID_API_APP_PACKAGE_NAME), anyString()))
                .thenReturn(false);
        // We don't log STATUS_RATE_LIMIT_REACHED for getAppSetId API.
        invokeGetAppSetIdAndVerifyError(
                mContext,
                STATUS_RATE_LIMIT_REACHED,
                request, /* checkLoggingStatus */
                false,
                FAILURE_REASON_UNSET);
    }

    @Test
    @RequiresSdkLevelAtLeastT(reason = "Sandbox caller is only applicable on T+")
    public void testEnforceForeground_sandboxCaller() throws Exception {
        // Mock AppImportanceFilter to throw Exception when invoked. This is to verify getAppSetId()
        // doesn't throw if caller is via Sandbox.
        doThrow(new WrongCallingApplicationStateException())
                .when(mMockAppImportanceFilter)
                .assertCallerIsInForeground(
                        SANDBOX_UID, AD_SERVICES_API_CALLED__API_NAME__GET_APPSETID, SOME_SDK_NAME);

        // Mock UID with SDK UID
        when(Binder.getCallingUidOrThrow()).thenReturn(SANDBOX_UID);

        // Mock Flags with true to enable enforcing foreground check.
        doReturn(true).when(mMockFlags).getEnforceForegroundStatusForAppSetId();

        // Mock to grant required permissions
        // Copied UID calculation from Process.getAppUidForSdkSandboxUid().
        final int appCallingUid = SANDBOX_UID - 10000;
        when(mPackageManager.getPackageUid(TEST_APP_PACKAGE_NAME, 0)).thenReturn(appCallingUid);

        // Verify getAppSetId() doesn't throw.
        mAppSetIdServiceImpl = createAppSetIdServiceImplInstance_SandboxContext();
        runGetAppSetId(mAppSetIdServiceImpl);

        verify(mMockAppImportanceFilter, never())
                .assertCallerIsInForeground(
                        SANDBOX_UID, AD_SERVICES_API_CALLED__API_NAME__GET_APPSETID, SOME_SDK_NAME);
    }

    @Test
    public void testEnforceForeground_disableEnforcing() throws Exception {
        final int uid = Process.myUid();
        // Mock AppImportanceFilter to throw Exception when invoked. This is to verify getAppSetId()
        // doesn't throw if enforcing foreground is disabled
        doThrow(new WrongCallingApplicationStateException())
                .when(mMockAppImportanceFilter)
                .assertCallerIsInForeground(
                        uid, AD_SERVICES_API_CALLED__API_NAME__GET_APPSETID, SOME_SDK_NAME);

        // Mock UID with Non-SDK UI
        when(Binder.getCallingUidOrThrow()).thenReturn(uid);

        // Mock Flags with false to disable enforcing foreground check.
        doReturn(false).when(mMockFlags).getEnforceForegroundStatusForAppSetId();

        // Mock to grant required permissions
        // TODO
        when(mPackageManager.getPackageUid(TEST_APP_PACKAGE_NAME, 0)).thenReturn(uid);

        // Verify getAppSetId() doesn't throw.
        mAppSetIdServiceImpl = createTestAppSetIdServiceImplInstance();
        runGetAppSetId(mAppSetIdServiceImpl);

        verify(mMockAppImportanceFilter, never())
                .assertCallerIsInForeground(
                        uid, AD_SERVICES_API_CALLED__API_NAME__GET_APPSETID, SOME_SDK_NAME);
    }

    @Test
    public void getAppSetId() throws Exception {
        when(Binder.getCallingUidOrThrow()).thenReturn(Process.myUid());
        runGetAppSetId(createTestAppSetIdServiceImplInstance());
    }

    @Test
    public void testGetAppSetId_enforceCallingPackage_invalidPackage() throws InterruptedException {
        when(Binder.getCallingUidOrThrow()).thenReturn(Process.myUid());

        // A request with an invalid package name.
        mRequest =
                new GetAppSetIdParam.Builder()
                        .setAppPackageName(INVALID_PACKAGE_NAME)
                        .setSdkPackageName(SOME_SDK_NAME)
                        .build();

        invokeGetAppSetIdAndVerifyError(
                mContext,
                STATUS_CALLER_NOT_ALLOWED,
                mRequest, /* checkLoggingStatus */
                true,
                FAILURE_REASON_PACKAGE_NOT_IN_ALLOWLIST);
    }

    @Test
    public void testGetAppSetId_enforceCallingPackage_logCallingPackageNotFound() throws Exception {
        doNothingOnErrorLogUtilError();
        when(mSpyContext.getPackageManager()).thenReturn(mPackageManager);
        when(mPackageManager.getPackageUid(TEST_APP_PACKAGE_NAME, 0))
                .thenThrow(new PackageManager.NameNotFoundException());

        mRequest =
                new GetAppSetIdParam.Builder()
                        .setAppPackageName(TEST_APP_PACKAGE_NAME)
                        .setSdkPackageName(SOME_SDK_NAME)
                        .build();

        invokeGetAppSetIdAndVerifyError(
                mSpyContext,
                STATUS_UNAUTHORIZED,
                mRequest, /* checkLoggingStatus */
                true,
                FAILURE_REASON_CALLING_PACKAGE_NOT_FOUND);
    }

    @Test
    public void testGetAppSetId_enforceCallingPackage_logCallingPackageIdMismatch()
            throws Exception {
        when(mSpyContext.getPackageManager()).thenReturn(mPackageManager);
        when(mPackageManager.getPackageUid(TEST_APP_PACKAGE_NAME, 0)).thenReturn(/* uid */ -1);

        mRequest =
                new GetAppSetIdParam.Builder()
                        .setAppPackageName(TEST_APP_PACKAGE_NAME)
                        .setSdkPackageName(SOME_SDK_NAME)
                        .build();

        invokeGetAppSetIdAndVerifyError(
                mSpyContext,
                STATUS_UNAUTHORIZED,
                mRequest, /* checkLoggingStatus */
                true,
                FAILURE_REASON_CALLING_PACKAGE_DOES_NOT_BELONG_TO_CALLING_ID);
    }

    private void invokeGetAppSetIdAndVerifyError(
            Context context, int expectedResultCode, boolean checkLoggingStatus, int failureReason)
            throws InterruptedException {
        invokeGetAppSetIdAndVerifyError(
                context, expectedResultCode, mRequest, checkLoggingStatus, failureReason);
    }

    private void invokeGetAppSetIdAndVerifyError(
            Context context,
            int expectedResultCode,
            GetAppSetIdParam request,
            boolean checkLoggingStatus,
            int failureReason)
            throws InterruptedException {
        SyncIGetAppSetIdCallback callback =
                new SyncIGetAppSetIdCallback(BINDER_CONNECTION_TIMEOUT_MS);

        CountDownLatch logOperationCalledLatch = new CountDownLatch(1);
        Mockito.doAnswer(
                        (Answer<Object>)
                                invocation -> {
                                    // The method logAPiCallStats is called.
                                    invocation.callRealMethod();
                                    logOperationCalledLatch.countDown();
                                    return null;
                                })
                .when(mAdServicesLogger)
                .logApiCallStats(any(ApiCallStats.class));

        mAppSetIdServiceImpl =
                new AppSetIdServiceImpl(
                        context,
                        mAppSetIdWorker,
                        mAdServicesLogger,
                        mClock,
                        mMockFlags,
                        mMockThrottler,
                        mMockAppImportanceFilter);
        mAppSetIdServiceImpl.getAppSetId(request, mCallerMetadata, callback);
        callback.assertFailed(expectedResultCode);

        if (checkLoggingStatus) {
            // getAppSetId method finished executing.
            logOperationCalledLatch.await();

            ArgumentCaptor<ApiCallStats> argument = ArgumentCaptor.forClass(ApiCallStats.class);

            verify(mAdServicesLogger).logApiCallStats(argument.capture());
            assertThat(argument.getValue().getCode()).isEqualTo(AD_SERVICES_API_CALLED);
            assertThat(argument.getValue().getApiClass())
                    .isEqualTo(AD_SERVICES_API_CALLED__API_CLASS__APPSETID);
            assertThat(argument.getValue().getApiName())
                    .isEqualTo(AD_SERVICES_API_CALLED__API_NAME__GET_APPSETID);
            assertThat(argument.getValue().getResultCode()).isEqualTo(expectedResultCode);
            assertThat(argument.getValue().getAppPackageName())
                    .isEqualTo(request.getAppPackageName());
            assertThat(argument.getValue().getSdkPackageName())
                    .isEqualTo(request.getSdkPackageName());
            assertThat(argument.getValue().getFailureReason()).isEqualTo(failureReason);
        }
    }

    private void runGetAppSetId(AppSetIdServiceImpl appSetIdServiceImpl) throws Exception {

        GetAppSetIdResult expectedGetAppSetIdResult =
                new GetAppSetIdResult.Builder()
                        .setAppSetId("00000000-0000-0000-0000-000000000000")
                        .setAppSetIdScope(0)
                        .build();

        NoFailureSyncCallback<ApiCallStats> logApiCallStatsCallback =
                mockLogApiCallStats(mAdServicesLogger);

        GetAppSetIdResult getAppSetIdResult = getAppSetIdResults(appSetIdServiceImpl);

        assertThat(getAppSetIdResult.getAppSetId())
                .isEqualTo(expectedGetAppSetIdResult.getAppSetId());

        logApiCallStatsCallback.assertResultReceived();
    }

    @NonNull
    private GetAppSetIdResult getAppSetIdResults(AppSetIdServiceImpl appSetIdServiceImpl)
            throws Exception {
        // To capture result in inner class, we have to declare final.
        SyncIGetAppSetIdCallback callback =
                new SyncIGetAppSetIdCallback(BINDER_CONNECTION_TIMEOUT_MS);

        appSetIdServiceImpl.getAppSetId(mRequest, mCallerMetadata, callback);

        return callback.assertSuccess();
    }

    @NonNull
    private AppSetIdServiceImpl createTestAppSetIdServiceImplInstance() {
        return new AppSetIdServiceImpl(
                mContext,
                mAppSetIdWorker,
                mAdServicesLogger,
                mClock,
                mMockFlags,
                mMockThrottler,
                mMockAppImportanceFilter);
    }

    @NonNull
    private AppSetIdServiceImpl createAppSetIdServiceImplInstance_SandboxContext() {
        return new AppSetIdServiceImpl(
                mMockSdkContext,
                mAppSetIdWorker,
                mAdServicesLogger,
                mClock,
                mMockFlags,
                mMockThrottler,
                mMockAppImportanceFilter);
    }

    private static final class SyncIGetAppSetIdCallback
            extends IntFailureSyncCallback<GetAppSetIdResult> implements IGetAppSetIdCallback {

        private SyncIGetAppSetIdCallback(int timeout) {
            super(timeout);
        }

        @Override
        public void onError(int resultCode) {
            onFailure(resultCode);
        }
    }
}
