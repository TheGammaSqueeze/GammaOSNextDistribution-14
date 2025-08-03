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

package com.android.adservices.service.adid;

import static android.adservices.common.AdServicesStatusUtils.STATUS_INTERNAL_ERROR;
import static android.adservices.common.AdServicesStatusUtils.STATUS_SUCCESS;

import static com.android.adservices.service.adid.AdIdCacheManager.SHARED_PREFS_IAPC;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import android.adservices.adid.AdId;
import android.adservices.adid.GetAdIdResult;
import android.adservices.adid.IAdIdProviderService;
import android.adservices.adid.IGetAdIdCallback;
import android.adservices.adid.IGetAdIdProviderCallback;
import android.adservices.common.UpdateAdIdRequest;
import android.os.RemoteException;

import com.android.adservices.common.AdServicesExtendedMockitoTestCase;
import com.android.adservices.service.Flags;
import com.android.adservices.service.FlagsFactory;
import com.android.modules.utils.testing.ExtendedMockitoRule.SpyStatic;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

import java.util.concurrent.CompletableFuture;

/** Unit test for {@link AdIdCacheManager}. */
@SpyStatic(FlagsFactory.class)
public final class AdIdCacheManagerTest extends AdServicesExtendedMockitoTestCase {
    private static final String PACKAGE_NAME = "package_name";
    // Use a non zeroed out AdId differentiate from the scenario without the provider service.
    private static final String AD_ID = "10000000-0000-0000-0000-000000000000";
    private static final String AD_ID_UPDATE = "20000000-0000-0000-0000-000000000000";
    private static final int DUMMY_CALLER_UID = 0;

    private IAdIdProviderService mAdIdProviderService;
    private AdIdCacheManager mAdIdCacheManager;

    @Mock private Flags mMockFlags;

    @Before
    public void setup() {
        extendedMockito.mockGetFlags(mMockFlags);

        deleteIapcSharedPreference();

        mAdIdCacheManager = spy(new AdIdCacheManager(mContext));
    }

    // Clear the shared preference to isolate the test result from other tests.
    @After
    public void deleteIapcSharedPreference() {
        mContext.deleteSharedPreferences(SHARED_PREFS_IAPC);
    }

    @Test
    public void testGetAdId_cacheEnabled() throws Exception {
        // Enable the AdId cache.
        doReturn(true).when(mMockFlags).getAdIdCacheEnabled();

        mAdIdProviderService = createAdIdProviderService(/* isSuccess= */ true);
        doReturn(mAdIdProviderService).when(mAdIdCacheManager).getService();

        // First getAdId() call should get AdId from the provider.
        CompletableFuture<GetAdIdResult> future1 = new CompletableFuture<>();
        IGetAdIdCallback callback1 = createSuccessGetAdIdCallBack(future1);

        mAdIdCacheManager.getAdId(PACKAGE_NAME, DUMMY_CALLER_UID, callback1);

        GetAdIdResult result = future1.get();
        AdId actualAdId = new AdId(result.getAdId(), result.isLatEnabled());
        AdId expectedAdId = new AdId(AD_ID, /* limitAdTrackingEnabled= */ false);
        assertWithMessage("The first result is from the Provider.")
                .that(actualAdId)
                .isEqualTo(expectedAdId);

        // Verify the first call should call the provider to fetch the AdId
        verify(mAdIdCacheManager).getAdIdFromProvider(PACKAGE_NAME, DUMMY_CALLER_UID, callback1);

        // Second getAdId() call should get AdId from the cache.
        CompletableFuture<GetAdIdResult> future2 = new CompletableFuture<>();
        IGetAdIdCallback callback2 = createSuccessGetAdIdCallBack(future2);
        mAdIdCacheManager.getAdId(PACKAGE_NAME, DUMMY_CALLER_UID, callback2);
        result = future2.get();
        actualAdId = new AdId(result.getAdId(), result.isLatEnabled());
        assertWithMessage("The second result is from the Cache")
                .that(actualAdId)
                .isEqualTo(expectedAdId);

        // Verify the second call should NOT call the provider to fetch the AdId, the only
        // invocation comes from the first getAdId() call.
        verify(mAdIdCacheManager).getAdIdFromProvider(any(), anyInt(), any());

        // Make the third getAdId() call after updating the shared preference.
        mAdIdCacheManager.setAdIdInStorage(
                new AdId(AD_ID_UPDATE, /* limitAdTrackingEnabled= */ true));
        CompletableFuture<GetAdIdResult> future3 = new CompletableFuture<>();
        IGetAdIdCallback callback3 = createSuccessGetAdIdCallBack(future3);

        mAdIdCacheManager.getAdId(PACKAGE_NAME, DUMMY_CALLER_UID, callback3);

        result = future3.get();
        actualAdId = new AdId(result.getAdId(), result.isLatEnabled());
        expectedAdId = new AdId(AD_ID_UPDATE, /* limitAdTrackingEnabled= */ true);

        assertWithMessage("The third result is from the Cache (Updated)")
                .that(actualAdId)
                .isEqualTo(expectedAdId);
        verify(mAdIdCacheManager).getAdIdFromProvider(any(), anyInt(), any());
    }

    @Test
    public void testGetAdId_cacheDisabled() throws Exception {
        // Disable the AdId cache.
        doReturn(false).when(mMockFlags).getAdIdCacheEnabled();

        mAdIdProviderService = createAdIdProviderService(/* isSuccess= */ true);
        doReturn(mAdIdProviderService).when(mAdIdCacheManager).getService();

        // First getAdId() call should get AdId from the provider.
        CompletableFuture<GetAdIdResult> future = new CompletableFuture<>();
        IGetAdIdCallback callback = createSuccessGetAdIdCallBack(future);

        mAdIdCacheManager.getAdId(PACKAGE_NAME, DUMMY_CALLER_UID, callback);

        GetAdIdResult result = future.get();
        AdId actualAdId = new AdId(result.getAdId(), result.isLatEnabled());
        AdId expectedAdId = new AdId(AD_ID, /* limitAdTrackingEnabled= */ false);
        assertWithMessage("Get AdId from Provider").that(actualAdId).isEqualTo(expectedAdId);

        // Verify the first call should call the provider to fetch the AdId
        verify(mAdIdCacheManager).getAdIdFromProvider(PACKAGE_NAME, DUMMY_CALLER_UID, callback);
        // Verify the cache is never visited. (the SharedPreference getter is never called.)
        verify(mAdIdCacheManager).getAdIdInStorage();
        verify(mAdIdCacheManager, never()).getSharedPreferences();
    }

    @Test
    public void testGetAdIdOnError() throws Exception {
        // Enable the AdId cache.
        doReturn(true).when(mMockFlags).getAdIdCacheEnabled();

        mAdIdProviderService = createAdIdProviderService(/* isSuccess= */ false);
        doReturn(mAdIdProviderService).when(mAdIdCacheManager).getService();

        CompletableFuture<Integer> future = new CompletableFuture<>();
        IGetAdIdCallback callback = createFailureGetAdIdCallBack(future);

        mAdIdCacheManager.getAdId(PACKAGE_NAME, DUMMY_CALLER_UID, callback);

        int result = future.get();
        assertThat(result).isEqualTo(STATUS_INTERNAL_ERROR);
    }

    @Test
    public void testUpdateAdId_success() {
        // Enable the AdId cache.
        doReturn(true).when(mMockFlags).getAdIdCacheEnabled();

        AdId adId = new AdId(AD_ID, /* limitAdTrackingEnabled= */ false);
        AdId adIdUpdate = new AdId(AD_ID_UPDATE, /* limitAdTrackingEnabled= */ true);

        mAdIdCacheManager.setAdIdInStorage(adId);

        mAdIdCacheManager.updateAdId(
                new UpdateAdIdRequest.Builder(adIdUpdate.getAdId())
                        .setLimitAdTrackingEnabled(adIdUpdate.isLimitAdTrackingEnabled())
                        .build());
        assertWithMessage("getAdIdInStorage()")
                .that(mAdIdCacheManager.getAdIdInStorage())
                .isEqualTo(adIdUpdate);
        verify(mAdIdCacheManager).setAdIdInStorage(adIdUpdate);
    }

    @Test
    public void testUpdateAdId_cacheDisabledWhenUpdating() {
        // Enable the AdId cache at beginning to initialize the cache.
        doReturn(true).when(mMockFlags).getAdIdCacheEnabled();

        AdId adId = new AdId(AD_ID, /* limitAdTrackingEnabled= */ false);
        mAdIdCacheManager.setAdIdInStorage(adId);
        assertWithMessage("getAdIdInStorage() 1st")
                .that(mAdIdCacheManager.getAdIdInStorage())
                .isEqualTo(adId);

        // Disable the cache before updating.
        doReturn(false).when(mMockFlags).getAdIdCacheEnabled();

        AdId adIdUpdate = new AdId(AD_ID_UPDATE, /* limitAdTrackingEnabled= */ true);
        mAdIdCacheManager.updateAdId(
                new UpdateAdIdRequest.Builder(adIdUpdate.getAdId())
                        .setLimitAdTrackingEnabled(adIdUpdate.isLimitAdTrackingEnabled())
                        .build());

        // Enable the cache again to check the cached value.
        doReturn(true).when(mMockFlags).getAdIdCacheEnabled();

        assertWithMessage("getAdIdInStorage() 2nd")
                .that(mAdIdCacheManager.getAdIdInStorage())
                .isEqualTo(adId);

        // Verify the SharedPreference is interacted 3 times when there are 4 cache get/set actions.
        verify(mAdIdCacheManager).setAdIdInStorage(adId);
        verify(mAdIdCacheManager).setAdIdInStorage(adIdUpdate);
        verify(mAdIdCacheManager, times(2)).getAdIdInStorage();
        verify(mAdIdCacheManager, times(3)).getSharedPreferences();
    }

    private IGetAdIdCallback createSuccessGetAdIdCallBack(CompletableFuture<GetAdIdResult> future) {
        return new IGetAdIdCallback.Stub() {
            @Override
            public void onResult(GetAdIdResult resultParcel) {
                future.complete(resultParcel);
            }

            @Override
            public void onError(int resultCode) {
                throw new UnsupportedOperationException("Should never be called!");
            }
        };
    }

    private IGetAdIdCallback createFailureGetAdIdCallBack(CompletableFuture<Integer> future) {
        return new IGetAdIdCallback.Stub() {
            @Override
            public void onResult(GetAdIdResult resultParcel) {
                throw new UnsupportedOperationException("Should never be called!");
            }

            @Override
            public void onError(int resultCode) {
                future.complete(resultCode);
            }
        };
    }

    private IAdIdProviderService createAdIdProviderService(boolean isSuccess) {
        return new IAdIdProviderService.Stub() {
            @Override
            public void getAdIdProvider(
                    int appUid, String packageName, IGetAdIdProviderCallback resultCallback)
                    throws RemoteException {

                if (isSuccess) {
                    GetAdIdResult adIdInternal =
                            new GetAdIdResult.Builder()
                                    .setStatusCode(STATUS_SUCCESS)
                                    .setErrorMessage("")
                                    .setAdId(AD_ID)
                                    .setLatEnabled(/* isLimitAdTrackingEnabled= */ false)
                                    .build();

                    // Mock the write operation to the storage
                    mAdIdCacheManager.setAdIdInStorage(
                            new AdId(AD_ID, /* limitAdTrackingEnabled= */ false));
                    resultCallback.onResult(adIdInternal);
                } else {
                    resultCallback.onError("testOnError");
                }
            }
        };
    }
}
