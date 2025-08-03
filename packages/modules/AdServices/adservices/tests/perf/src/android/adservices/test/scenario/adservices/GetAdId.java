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

package android.adservices.test.scenario.adservices;

import android.adservices.adid.AdId;
import android.adservices.adid.AdIdCompatibleManager;
import android.adservices.adid.AdIdManager;
import android.adservices.common.AdServicesOutcomeReceiver;
import android.content.Context;
import android.os.Trace;
import android.platform.test.scenario.annotation.Scenario;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.test.core.app.ApplicationProvider;

import com.android.compatibility.common.util.ShellUtils;
import com.android.modules.utils.build.SdkLevel;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

@Scenario
@RunWith(JUnit4.class)
public class GetAdId {
    private static final String TAG = GetAdId.class.getSimpleName();
    private static final Context sContext = ApplicationProvider.getApplicationContext();
    private static final Executor sExecutor = Executors.newCachedThreadPool();

    @Before
    public void setup() {
        disableGlobalKillSwitch();
    }

    @Test
    public void test_getAdId() throws ExecutionException, InterruptedException {
        if (SdkLevel.isAtLeastS()) {
            testSPlus();
        } else {
            // Android R testing is a special case as we will need to use the custom
            // AdServicesOutcomeReceiver. Since there are no plans to expose the AdId
            // API with the custom AdServicesOutcomeReceiver as a parameter on R, we will invoke
            // AdIdCompatibleManager, which is available internally.
            testR();
        }
    }

    private void testSPlus() throws ExecutionException, InterruptedException {
        AdIdManager adIdManager = AdIdManager.get(sContext);
        CompletableFuture<AdId> future = new CompletableFuture<>();
        android.os.OutcomeReceiver<AdId, Exception> callback =
                new android.os.OutcomeReceiver<>() {
                    @Override
                    public void onResult(@NonNull AdId adId) {
                        future.complete(adId);
                    }

                    @Override
                    public void onError(@NonNull Exception error) {
                        Log.e(TAG, "Retrieving Ad ID resulted in an error!", error);
                        Assert.fail();
                    }
                };

        Trace.beginSection(TAG + "#GetAdId");
        adIdManager.getAdId(sExecutor, callback);
        AdId adId = future.get();
        Assert.assertEquals(AdId.ZERO_OUT.length(), adId.getAdId().length());
        Trace.endSection();
    }

    private void testR() throws ExecutionException, InterruptedException {
        AdIdCompatibleManager compatAdIdManagerManager = new AdIdCompatibleManager(sContext);
        CompletableFuture<AdId> future = new CompletableFuture<>();
        AdServicesOutcomeReceiver<AdId, Exception> callback =
                new AdServicesOutcomeReceiver<>() {
                    @Override
                    public void onResult(@NonNull AdId adId) {
                        future.complete(adId);
                    }

                    @Override
                    public void onError(@NonNull Exception error) {
                        Log.e(TAG, "Retrieving Ad ID resulted in an error!", error);
                        Assert.fail();
                    }
                };

        Trace.beginSection(TAG + "#GetAdId");
        compatAdIdManagerManager.getAdId(sExecutor, callback);
        AdId adId = future.get();
        Assert.assertEquals(AdId.ZERO_OUT.length(), adId.getAdId().length());
        Trace.endSection();
    }

    // Override global_kill_switch to ignore the effect of actual PH values.
    private void disableGlobalKillSwitch() {
        ShellUtils.runShellCommand("device_config put adservices global_kill_switch false");
        ShellUtils.runShellCommand("device_config put adservices adid_kill_switch false");
    }
}
