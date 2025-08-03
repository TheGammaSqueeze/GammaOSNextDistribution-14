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

package android.adservices.cts;

import static com.android.adservices.service.FlagsConstants.KEY_ADSERVICES_ENABLED;
import static com.android.adservices.service.FlagsConstants.KEY_AD_ID_CACHE_ENABLED;

import android.adservices.adid.AdId;
import android.adservices.common.AdServicesCommonManager;
import android.adservices.common.UpdateAdIdRequest;
import android.util.Log;

import com.android.adservices.common.AdServicesOutcomeReceiverForTests;
import com.android.adservices.common.OutcomeReceiverForTests;
import com.android.adservices.common.RequiresSdkLevelAtLeastS;
import com.android.adservices.common.annotations.SetFlagDisabled;
import com.android.adservices.common.annotations.SetFlagEnabled;

import org.junit.Before;
import org.junit.Test;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public final class AdServicesCommonManagerTest extends CtsAdServicesDeviceTestCase {

    private static final Executor CALLBACK_EXECUTOR = Executors.newCachedThreadPool();

    private final AdServicesCommonManager mCommonManager = AdServicesCommonManager.get(sContext);

    @Before
    public void printRelevantFlags() {
        Log.d(
                mTag,
                "Relevant flags @Before: "
                        + KEY_AD_ID_CACHE_ENABLED
                        + "="
                        + flags.getFlag(KEY_AD_ID_CACHE_ENABLED)
                        + ", "
                        + KEY_AD_ID_CACHE_ENABLED
                        + "="
                        + flags.getFlag(KEY_AD_ID_CACHE_ENABLED));
    }

    @Test
    @RequiresSdkLevelAtLeastS(reason = "uses OutcomeReceiver, which is only available on S+.")
    @SetFlagDisabled(KEY_ADSERVICES_ENABLED)
    public void testStatusManagerNotAuthorizedOnSPlus() throws Exception {
        // At beginning, Sdk1 receives a false status.
        OutcomeReceiverForTests<Boolean> receiver = new OutcomeReceiverForTests<>();

        mCommonManager.isAdServicesEnabled(CALLBACK_EXECUTOR, receiver);

        receiver.assertFailure(SecurityException.class);
    }

    @Test
    @RequiresSdkLevelAtLeastS
    public void testSetStatusEnabledNotExecutedOnSPlus() throws Exception {
        mCommonManager.setAdServicesEnabled(true, true);
        OutcomeReceiverForTests<Boolean> receiver = new OutcomeReceiverForTests<>();

        mCommonManager.isAdServicesEnabled(CALLBACK_EXECUTOR, receiver);

        receiver.assertFailure(SecurityException.class);
    }

    @Test
    @SetFlagDisabled(KEY_ADSERVICES_ENABLED)
    public void testStatusManagerNotAuthorizedCompat() throws Exception {
        // At beginning, Sdk1 receives a false status.
        AdServicesOutcomeReceiverForTests<Boolean> receiver =
                new AdServicesOutcomeReceiverForTests<>();

        mCommonManager.isAdServicesEnabled(CALLBACK_EXECUTOR, receiver);

        receiver.assertFailure(SecurityException.class);
    }

    @Test
    public void testSetStatusEnabledNotExecutedCompat() throws Exception {
        mCommonManager.setAdServicesEnabled(true, true);
        AdServicesOutcomeReceiverForTests<Boolean> receiver =
                new AdServicesOutcomeReceiverForTests<>();

        mCommonManager.isAdServicesEnabled(CALLBACK_EXECUTOR, receiver);

        receiver.assertFailure(SecurityException.class);
    }

    @Test
    @RequiresSdkLevelAtLeastS(reason = "uses OutcomeReceiver, which is only available on T")
    @SetFlagEnabled(KEY_AD_ID_CACHE_ENABLED)
    public void testUpdateAdIdCache_notAuthorized_sPlus() throws Exception {
        UpdateAdIdRequest request = new UpdateAdIdRequest.Builder(AdId.ZERO_OUT).build();
        OutcomeReceiverForTests<Boolean> receiver = new OutcomeReceiverForTests<>();

        mCommonManager.updateAdId(request, CALLBACK_EXECUTOR, receiver);

        receiver.assertFailure(SecurityException.class);
    }

    @Test
    @RequiresSdkLevelAtLeastS(reason = "uses OutcomeReceiver, which is only available on T")
    @SetFlagDisabled(KEY_AD_ID_CACHE_ENABLED)
    public void testUpdateAdIdCache_notEnabled_sPlus() throws Exception {
        UpdateAdIdRequest request = new UpdateAdIdRequest.Builder(AdId.ZERO_OUT).build();

        OutcomeReceiverForTests<Boolean> receiver = new OutcomeReceiverForTests<>();
        mCommonManager.updateAdId(request, CALLBACK_EXECUTOR, receiver);

        receiver.assertFailure(IllegalStateException.class);
    }

    @Test
    @SetFlagEnabled(KEY_AD_ID_CACHE_ENABLED)
    public void testUpdateAdIdCache_notAuthorized_rPlus() throws Exception {
        AdServicesOutcomeReceiverForTests<Boolean> receiver =
                new AdServicesOutcomeReceiverForTests<>();

        mCommonManager.updateAdId(
                new UpdateAdIdRequest.Builder(AdId.ZERO_OUT).build(), CALLBACK_EXECUTOR, receiver);

        receiver.assertFailure(SecurityException.class);
    }

    @Test
    @SetFlagDisabled(KEY_AD_ID_CACHE_ENABLED)
    public void testUpdateAdIdCache_notEnabled_rPlus() throws Exception {
        AdServicesOutcomeReceiverForTests<Boolean> receiver =
                new AdServicesOutcomeReceiverForTests<>();

        mCommonManager.updateAdId(
                new UpdateAdIdRequest.Builder(AdId.ZERO_OUT).build(), CALLBACK_EXECUTOR, receiver);

        receiver.assertFailure(IllegalStateException.class);
    }
}
