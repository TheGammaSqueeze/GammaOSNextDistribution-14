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
package com.android.adservices.tests.appsetid;

import android.adservices.appsetid.AppSetId;
import android.adservices.appsetid.AppSetIdProviderService;
import android.adservices.appsetid.GetAppSetIdResult;
import android.adservices.appsetid.IAppSetIdProviderService;
import android.adservices.appsetid.IGetAppSetIdProviderCallback;
import android.content.Intent;
import android.os.IBinder;
import android.os.OutcomeReceiver;

import androidx.test.runner.AndroidJUnit4;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;

@RunWith(AndroidJUnit4.class)
public class AppSetIdProviderTest {

    private static final String DEFAULT_APP_SET_ID = "00000000-0000-0000-0000-000000000000";
    private static final int DEFAULT_SCOPE = 1;

    private static final class AppSetIdProviderServiceProxy extends AppSetIdProviderService {

        @Override
        public AppSetId onGetAppSetId(int clientUid, String clientPackageName) throws IOException {
            return new AppSetId(DEFAULT_APP_SET_ID, DEFAULT_SCOPE);
        }
    }

    @Test
    public void testAppSetIdProvider() throws Exception {
        AppSetIdProviderServiceProxy proxy = new AppSetIdProviderServiceProxy();

        Intent intent = new Intent();
        intent.setAction(AppSetIdProviderService.SERVICE_INTERFACE);
        IBinder remoteObject = proxy.onBind(intent);
        Assert.assertNotNull(remoteObject);

        IAppSetIdProviderService service = IAppSetIdProviderService.Stub.asInterface(remoteObject);
        Assert.assertNotNull(service);

        CompletableFuture<AppSetId> future = new CompletableFuture<>();
        OutcomeReceiver<AppSetId, Exception> callback =
                new OutcomeReceiver<AppSetId, Exception>() {
                    @Override
                    public void onResult(AppSetId appSetId) {
                        future.complete(appSetId);
                    }

                    @Override
                    public void onError(Exception error) {
                        Assert.fail();
                    }
                };
        service.getAppSetId(
                /* testAppUId */ 0,
                "testPackageName",
                new IGetAppSetIdProviderCallback.Stub() {
                    @Override
                    public void onResult(GetAppSetIdResult result) {
                        callback.onResult(
                                new AppSetId(result.getAppSetId(), result.getAppSetIdScope()));
                    }

                    @Override
                    public void onError(String errorMessage) {
                        Assert.fail();
                    }
                });
        AppSetId resultAppSetId = future.get();
        Assert.assertEquals(DEFAULT_APP_SET_ID, resultAppSetId.getId());
        Assert.assertEquals(DEFAULT_SCOPE, resultAppSetId.getScope());
    }
}
