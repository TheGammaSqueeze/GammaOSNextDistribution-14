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

import static android.adservices.common.AdServicesStatusUtils.STATUS_SUCCESS;

import static org.junit.Assert.fail;

import android.adservices.appsetid.GetAppSetIdResult;
import android.adservices.appsetid.IGetAppSetIdCallback;
import android.adservices.appsetid.IGetAppSetIdProviderCallback;
import android.annotation.NonNull;
import android.os.RemoteException;

import com.android.adservices.common.AdServicesUnitTestCase;

import org.junit.Test;
import org.mockito.Mockito;

import java.util.concurrent.CompletableFuture;

/** Unit test for {@link com.android.adservices.service.appsetid.AppSetIdWorker}. */
public final class AppSetIdWorkerTest extends AdServicesUnitTestCase {

    private boolean mTestSuccess;

    private static final String DEFAULT_APP_SET_ID = "00000000-0000-0000-0000-000000000000";

    @Test
    public void testGetAppSetIdOnResult() throws Exception {
        mTestSuccess = true;

        CompletableFuture<GetAppSetIdResult> future = new CompletableFuture<>();

        AppSetIdWorker spyWorker = Mockito.spy(AppSetIdWorker.getInstance());
        Mockito.doReturn(mInterface).when(spyWorker).getService();

        spyWorker.getAppSetId(
                "testPackageName",
                0,
                new IGetAppSetIdCallback.Stub() {
                    @Override
                    public void onResult(GetAppSetIdResult resultParcel) {
                        future.complete(resultParcel);
                    }

                    @Override
                    public void onError(int resultCode) {
                        // should never be called.
                        fail();
                    }
                });

        GetAppSetIdResult result = future.get();
        expect.withMessage("getAppSetId()")
                .that(result.getAppSetId())
                .isEqualTo(DEFAULT_APP_SET_ID);
        expect.withMessage("getAppSetIdScope()").that(result.getAppSetIdScope()).isEqualTo(1);
    }

    @Test
    public void testGetAppSetIdOnError() throws Exception {
        mTestSuccess = false;

        CompletableFuture<Integer> future = new CompletableFuture<>();

        AppSetIdWorker spyWorker = Mockito.spy(AppSetIdWorker.getInstance());
        Mockito.doReturn(mInterface).when(spyWorker).getService();

        spyWorker.getAppSetId(
                "testPackageName",
                0,
                new IGetAppSetIdCallback.Stub() {
                    @Override
                    public void onResult(GetAppSetIdResult resultParcel) {
                        // should never be called.
                        fail();
                    }

                    @Override
                    public void onError(int resultCode) {
                        future.complete(resultCode);
                    }
                });

        int result = future.get();
        expect.withMessage("result").that(result).isEqualTo(1); // INTERNAL_STATE_ERROR
    }

    private final android.adservices.appsetid.IAppSetIdProviderService mInterface =
            new android.adservices.appsetid.IAppSetIdProviderService.Stub() {
                @Override
                public void getAppSetId(
                        int appUID,
                        @NonNull String packageName,
                        @NonNull IGetAppSetIdProviderCallback resultCallback)
                        throws RemoteException {
                    try {
                        if (mTestSuccess) {
                            GetAppSetIdResult appSetIdInternal =
                                    new GetAppSetIdResult.Builder()
                                            .setStatusCode(STATUS_SUCCESS)
                                            .setErrorMessage("")
                                            .setAppSetId(DEFAULT_APP_SET_ID)
                                            .setAppSetIdScope(/* DEFAULT_SCOPE */ 1)
                                            .build();
                            resultCallback.onResult(appSetIdInternal);
                        } else {
                            throw new Exception("testOnError");
                        }
                    } catch (Throwable e) {
                        resultCallback.onError(e.getMessage());
                    }
                }
            };
}
