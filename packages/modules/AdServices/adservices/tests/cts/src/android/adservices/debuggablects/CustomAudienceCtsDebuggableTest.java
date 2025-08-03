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

package android.adservices.debuggablects;

import android.adservices.clients.customaudience.TestAdvertisingCustomAudienceClient;
import android.adservices.common.AdSelectionSignals;
import android.adservices.common.AdTechIdentifier;
import android.adservices.customaudience.AddCustomAudienceOverrideRequest;
import android.adservices.customaudience.RemoveCustomAudienceOverrideRequest;
import android.os.Process;

import com.android.adservices.common.AdservicesTestHelper;
import com.android.adservices.common.RequiresSdkLevelAtLeastS;
import com.android.adservices.service.devapi.DevContext;
import com.android.adservices.service.devapi.DevContextFilter;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;

import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;

import java.util.concurrent.TimeUnit;

@RequiresSdkLevelAtLeastS
public final class CustomAudienceCtsDebuggableTest extends ForegroundDebuggableCtsTest {

    private TestAdvertisingCustomAudienceClient mTestClient;

    private static final AdTechIdentifier BUYER = AdTechIdentifier.fromString("buyer.example.com");
    private static final String NAME = "name";
    private static final String BIDDING_LOGIC_JS = "function test() { return \"hello world\"; }";
    private static final AdSelectionSignals TRUSTED_BIDDING_SIGNALS =
            AdSelectionSignals.fromString("{\"trusted_bidding_signal\":1}");

    private boolean mHasAccessToDevOverrides;

    private String mAccessStatus;

    @Before
    public void setup() {
        if (sdkLevel.isAtLeastT()) {
            assertForegroundActivityStarted();
        }

        mTestClient =
                new TestAdvertisingCustomAudienceClient.Builder()
                        .setContext(sContext)
                        .setExecutor(MoreExecutors.directExecutor())
                        .build();
        DevContextFilter devContextFilter = DevContextFilter.create(sContext);
        DevContext devContext = DevContextFilter.create(sContext).createDevContext(Process.myUid());
        boolean isDebuggable = devContextFilter.isDebuggable(devContext.getCallingAppPackageName());
        boolean isDeveloperMode = devContextFilter.isDeveloperMode();
        mHasAccessToDevOverrides = devContext.getDevOptionsEnabled();
        mAccessStatus =
                String.format("Debuggable: %b\n", isDebuggable)
                        + String.format("Developer options on: %b", isDeveloperMode);

        // Kill AdServices process
        AdservicesTestHelper.killAdservicesProcess(sContext);
    }

    @Test
    public void testAddOverrideSucceeds() throws Exception {
        Assume.assumeTrue(mAccessStatus, mHasAccessToDevOverrides);

        AddCustomAudienceOverrideRequest request =
                new AddCustomAudienceOverrideRequest.Builder()
                        .setBuyer(BUYER)
                        .setName(NAME)
                        .setBiddingLogicJs(BIDDING_LOGIC_JS)
                        .setTrustedBiddingSignals(TRUSTED_BIDDING_SIGNALS)
                        .build();

        ListenableFuture<Void> result = mTestClient.overrideCustomAudienceRemoteInfo(request);

        // Asserting no exception since there is no returned value
        result.get(10, TimeUnit.SECONDS);
    }

    @Test
    public void testRemoveNotExistingOverrideSucceeds() throws Exception {
        Assume.assumeTrue(mAccessStatus, mHasAccessToDevOverrides);

        RemoveCustomAudienceOverrideRequest request =
                new RemoveCustomAudienceOverrideRequest.Builder()
                        .setBuyer(BUYER)
                        .setName(NAME)
                        .build();

        ListenableFuture<Void> result = mTestClient.removeCustomAudienceRemoteInfoOverride(request);

        // Asserting no exception since there is no returned value
        result.get(10, TimeUnit.SECONDS);
    }

    @Test
    public void testRemoveExistingOverrideSucceeds() throws Exception {
        Assume.assumeTrue(mAccessStatus, mHasAccessToDevOverrides);

        AddCustomAudienceOverrideRequest addRequest =
                new AddCustomAudienceOverrideRequest.Builder()
                        .setBuyer(BUYER)
                        .setName(NAME)
                        .setBiddingLogicJs(BIDDING_LOGIC_JS)
                        .setTrustedBiddingSignals(TRUSTED_BIDDING_SIGNALS)
                        .build();

        ListenableFuture<Void> addResult = mTestClient.overrideCustomAudienceRemoteInfo(addRequest);

        // Asserting no exception since there is no returned value
        addResult.get(10, TimeUnit.SECONDS);

        RemoveCustomAudienceOverrideRequest removeRequest =
                new RemoveCustomAudienceOverrideRequest.Builder()
                        .setBuyer(BUYER)
                        .setName(NAME)
                        .build();

        ListenableFuture<Void> removeResult =
                mTestClient.removeCustomAudienceRemoteInfoOverride(removeRequest);

        // Asserting no exception since there is no returned value
        removeResult.get(10, TimeUnit.SECONDS);
    }

    @Test
    public void testResetAllOverridesSucceeds() throws Exception {
        Assume.assumeTrue(mAccessStatus, mHasAccessToDevOverrides);

        ListenableFuture<Void> result = mTestClient.resetAllCustomAudienceOverrides();

        // Asserting no exception since there is no returned value
        result.get(10, TimeUnit.SECONDS);
    }
}
