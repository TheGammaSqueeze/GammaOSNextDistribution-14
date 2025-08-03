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
package com.android.adservices.tests.adid;

import android.adservices.adid.AdId;
import android.adservices.adid.AdIdProviderService;
import android.adservices.adid.GetAdIdResult;
import android.adservices.adid.IAdIdProviderService;
import android.adservices.adid.IGetAdIdProviderCallback;
import android.adservices.common.AdServicesOutcomeReceiver;
import android.content.Intent;
import android.os.IBinder;

import androidx.test.runner.AndroidJUnit4;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;

@RunWith(AndroidJUnit4.class)
public class AdIdProviderTest {

    private static final String ZEROED_OUT_AD_ID = "00000000-0000-0000-0000-000000000000";
    private static final boolean DEFAULT_IS_LIMIT_AD_TRACKING_ENABLED = false;

    private static final class AdIdProviderServiceProxy extends AdIdProviderService {

        @Override
        public AdId onGetAdId(int clientUid, String clientPackageName) throws IOException {
            return new AdId(ZEROED_OUT_AD_ID, DEFAULT_IS_LIMIT_AD_TRACKING_ENABLED);
        }
    }

    @Test
    public void testAdIdProvider() throws Exception {
        AdIdProviderServiceProxy proxy = new AdIdProviderServiceProxy();

        Intent intent = new Intent();
        intent.setAction(AdIdProviderService.SERVICE_INTERFACE);
        IBinder remoteObject = proxy.onBind(intent);
        Assert.assertNotNull(remoteObject);

        IAdIdProviderService service = IAdIdProviderService.Stub.asInterface(remoteObject);
        Assert.assertNotNull(service);

        CompletableFuture<AdId> future = new CompletableFuture<>();
        AdServicesOutcomeReceiver<AdId, Exception> callback =
                new AdServicesOutcomeReceiver<>() {
                    @Override
                    public void onResult(AdId adId) {
                        future.complete(adId);
                    }

                    @Override
                    public void onError(Exception error) {
                        Assert.fail();
                    }
                };
        service.getAdIdProvider(
                /* testAppUId */ 0,
                "testPackageName",
                new IGetAdIdProviderCallback.Stub() {
                    @Override
                    public void onResult(GetAdIdResult result) {
                        callback.onResult(new AdId(result.getAdId(), result.isLatEnabled()));
                    }

                    @Override
                    public void onError(String errorMessage) {
                        Assert.fail();
                    }
                });
        AdId resultAdId = future.get();
        Assert.assertEquals(ZEROED_OUT_AD_ID, resultAdId.getAdId());
        Assert.assertEquals(
                DEFAULT_IS_LIMIT_AD_TRACKING_ENABLED, resultAdId.isLimitAdTrackingEnabled());
    }
}
