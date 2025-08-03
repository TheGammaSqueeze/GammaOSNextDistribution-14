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

import android.adservices.appsetid.AppSetId;
import android.adservices.appsetid.AppSetIdManager;
import android.content.Context;
import android.os.OutcomeReceiver;
import android.os.Trace;
import android.platform.test.scenario.annotation.Scenario;

import androidx.annotation.NonNull;
import androidx.test.core.app.ApplicationProvider;

import com.android.compatibility.common.util.ShellUtils;

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
public class GetAppSetId {
    private static final String DEFAULT_APP_SET_ID = "00000000-0000-0000-0000-000000000000";
    private static final String TAG = GetAppSetId.class.getSimpleName();
    private static final Context sContext = ApplicationProvider.getApplicationContext();
    private static final AppSetIdManager sAppSetIdManager = AppSetIdManager.get(sContext);
    private static final Executor sExecutor = Executors.newCachedThreadPool();

    @Before
    public void setup() {
        disableGlobalKillSwitch();
    }

    @Test
    public void test_getAppSetId() throws ExecutionException, InterruptedException {
        CompletableFuture<AppSetId> future = new CompletableFuture<>();
        OutcomeReceiver callback =
                new OutcomeReceiver<AppSetId, Exception>() {
                    @Override
                    public void onResult(@NonNull AppSetId appSetId) {
                        future.complete(appSetId);
                    }

                    @Override
                    public void onError(@NonNull Exception error) {
                        Assert.fail();
                    }
                };

        Trace.beginSection(TAG + "#GetAppSetId");
        sAppSetIdManager.getAppSetId(sExecutor, callback);
        AppSetId appSetId = future.get();
        Trace.endSection();

        Assert.assertEquals(DEFAULT_APP_SET_ID.length(), appSetId.getId().length());
    }

    // Override global_kill_switch to ignore the effect of actual PH values.
    private void disableGlobalKillSwitch() {
        ShellUtils.runShellCommand("device_config put adservices global_kill_switch false");
        ShellUtils.runShellCommand("device_config put adservices appsetid_kill_switch false");
    }
}
