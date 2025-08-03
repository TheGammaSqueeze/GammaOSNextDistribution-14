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

package com.google.android.chre.test.chqts;

import android.content.Context;
import android.hardware.location.ContextHubClient;
import android.hardware.location.ContextHubClientCallback;
import android.hardware.location.ContextHubInfo;
import android.hardware.location.ContextHubManager;
import android.hardware.location.NanoAppBinary;

import androidx.test.InstrumentationRegistry;

import com.google.android.chre.utils.pigweed.ChreRpcClient;
import com.google.android.utils.chre.ChreApiTestUtil;
import com.google.android.utils.chre.ChreTestUtil;

import org.junit.Assert;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import dev.pigweed.pw_rpc.Service;

/**
 * A base class for test executors that uses RPC-Based nanoapp.
 */
public class ContextHubChreApiTestExecutor extends ContextHubClientCallback {
    private final List<NanoAppBinary> mNanoAppBinaries;
    private final ContextHubClient mContextHubClient;
    private final AtomicBoolean mChreReset = new AtomicBoolean(false);
    protected final Context mContext = InstrumentationRegistry.getTargetContext();
    protected final ContextHubManager mContextHubManager;
    protected final ContextHubInfo mContextHub;
    protected final List<ChreRpcClient> mRpcClients = new ArrayList<ChreRpcClient>();

    public ContextHubChreApiTestExecutor(NanoAppBinary nanoapp) {
        this(List.of(nanoapp));
    }

    public ContextHubChreApiTestExecutor(List<NanoAppBinary> nanoapps) {
        mNanoAppBinaries = nanoapps;
        mContextHubManager = mContext.getSystemService(ContextHubManager.class);
        Assert.assertTrue(mContextHubManager != null);
        List<ContextHubInfo> contextHubs = mContextHubManager.getContextHubs();
        Assert.assertTrue(contextHubs.size() > 0);
        mContextHub = contextHubs.get(0);
        mContextHubClient = mContextHubManager.createClient(mContextHub, this);

        for (NanoAppBinary nanoapp: nanoapps) {
            Service chreApiService = ChreApiTestUtil.getChreApiService();
            mRpcClients.add(new ChreRpcClient(
                    mContextHubManager, mContextHub, nanoapp.getNanoAppId(),
                    List.of(chreApiService), this));
        }
    }

    @Override
    public void onHubReset(ContextHubClient client) {
        mChreReset.set(true);
    }

    /** Should be invoked before run() is invoked to set up the test, e.g. in a @Before method. */
    public void init() {
        for (NanoAppBinary nanoapp: mNanoAppBinaries) {
            ChreTestUtil.loadNanoAppAssertSuccess(mContextHubManager, mContextHub, nanoapp);
        }
    }

    /** Cleans up the test, should be invoked in e.g. @After method. */
    public void deinit() {
        if (mChreReset.get()) {
            Assert.fail("CHRE reset during the test");
        }

        for (NanoAppBinary nanoapp: mNanoAppBinaries) {
            ChreTestUtil.unloadNanoAppAssertSuccess(mContextHubManager, mContextHub,
                    nanoapp.getNanoAppId());
        }
        mContextHubClient.close();
    }

    /**
     * Gets the first RPC client in the list or returns null if the list is null or empty.
     * This is useful for tests with only one nanoapp/client.
     */
    public ChreRpcClient getRpcClient() {
        return mRpcClients != null && !mRpcClients.isEmpty() ? mRpcClients.get(0) : null;
    }
}
