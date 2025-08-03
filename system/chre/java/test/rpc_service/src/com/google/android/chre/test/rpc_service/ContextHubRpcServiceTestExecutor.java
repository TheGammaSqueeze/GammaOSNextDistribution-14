/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.google.android.chre.test.rpc_service;

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.location.ContextHubClient;
import android.hardware.location.ContextHubClientCallback;
import android.hardware.location.ContextHubInfo;
import android.hardware.location.ContextHubIntentEvent;
import android.hardware.location.ContextHubManager;
import android.hardware.location.NanoAppBinary;
import android.hardware.location.NanoAppState;

import com.google.android.chre.utils.pigweed.ChreRpcClient;
import com.google.android.utils.chre.ChreTestUtil;

import org.junit.Assert;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import dev.pigweed.pw_rpc.Call.UnaryFuture;
import dev.pigweed.pw_rpc.MethodClient;
import dev.pigweed.pw_rpc.Service;
import dev.pigweed.pw_rpc.UnaryResult;
import dev.pigweed.pw_rpc.proto.Echo;

/**
 * A class that can execute the test for the RPC service test nanoapp.
 */
public class ContextHubRpcServiceTestExecutor extends ContextHubClientCallback {
    public static final String ACTION = "com.google.android.chre.test.chqts.ACTION";
    private static final String TAG = "ContextHubRpcServiceTestExecutor";
    // The ID and version of the "rpc_service_test" nanoapp. Must be synchronized with the
    // value defined in the nanoapp code.
    private static final long RPC_SERVICE_ID = 0xca8f7150a3f05847L;
    private static final int RPC_SERVICE_VERSION = 0x01020034;
    private static final String RPC_ECHO_STRING = "HELLO_WORLD";
    private final NanoAppBinary mNanoAppBinary;
    private final long mNanoAppId;
    private final AtomicBoolean mChreReset = new AtomicBoolean(false);
    private final ContextHubManager mContextHubManager;
    private final ContextHubInfo mContextHubInfo;
    private final Service mEchoService;
    private final Context mContext = getInstrumentation().getTargetContext();
    private ChreRpcClient mRpcClient;

    public ContextHubRpcServiceTestExecutor(ContextHubManager manager, ContextHubInfo info,
            NanoAppBinary binary) {
        mContextHubManager = manager;
        mContextHubInfo = info;
        mNanoAppBinary = binary;
        mNanoAppId = mNanoAppBinary.getNanoAppId();

        mEchoService = new Service("pw.rpc.EchoService",
                Service.unaryMethod("Echo", Echo.EchoMessage.class, Echo.EchoMessage.class));
    }

    @Override
    public void onHubReset(ContextHubClient client) {
        mChreReset.set(true);
    }

    /**
     * Should be invoked before each test, e.g. in a @Before method.
     */
    public void init() {
        ChreTestUtil.loadNanoAppAssertSuccess(mContextHubManager, mContextHubInfo, mNanoAppBinary);
        mRpcClient = null;
    }

    public void findServiceTest() throws Exception {
        List<NanoAppState> stateList = ChreTestUtil.queryNanoAppsAssertSuccess(mContextHubManager,
                mContextHubInfo);
        boolean serviceFound = false;
        for (NanoAppState state : stateList) {
            if (ChreRpcClient.hasService(state, mNanoAppId, RPC_SERVICE_ID, RPC_SERVICE_VERSION)) {
                // The service is provided only by the test nanoapp.
                Assert.assertFalse(serviceFound);
                serviceFound = true;
            }
        }
        Assert.assertTrue(serviceFound);
    }

    /**
     * Using callbacks (persistent service).
     */
    public void callbackModeTest() throws Exception {
        mRpcClient = new ChreRpcClient(mContextHubManager, mContextHubInfo, mNanoAppId,
                List.of(mEchoService), this);

        invokeRpc(mRpcClient);
    }

    /**
     * Using intents (non-persistent service).
     */
    public void pendingIntentModeTest() throws Exception {
        BroadcastReceiver receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                ContextHubIntentEvent event = ContextHubIntentEvent.fromIntent(intent);
                if (event.getEventType() == ContextHubManager.EVENT_HUB_RESET) {
                    mChreReset.set(true);
                }
                mRpcClient.handleIntent(intent);
            }
        };

        IntentFilter filter = new IntentFilter(ACTION);
        Intent intent = new Intent(ACTION).setPackage(mContext.getPackageName());
        PendingIntent pendingIntent = PendingIntent.getBroadcast(mContext, 0 /* requestCode */,
                intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE);

        ContextHubClient contextHubClient = mContextHubManager.createClient(mContextHubInfo,
                pendingIntent, mNanoAppId);

        mRpcClient = new ChreRpcClient(contextHubClient, mNanoAppId, List.of(mEchoService));
        mContext.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED);

        invokeRpc(mRpcClient);

        mContext.unregisterReceiver(receiver);
    }

    /**
     * Cleans up the test, should be invoked in e.g. @After method.
     */
    public void deinit() {
        if (mChreReset.get()) {
            Assert.fail("CHRE reset during the test");
        }

        ChreTestUtil.unloadNanoAppAssertSuccess(mContextHubManager, mContextHubInfo, mNanoAppId);

        if (mRpcClient != null) {
            mRpcClient.close();
        }
    }

    private void invokeRpc(ChreRpcClient rpcClient) throws Exception {
        MethodClient methodClient = rpcClient.getMethodClient("pw.rpc.EchoService.Echo");

        Echo.EchoMessage message = Echo.EchoMessage.newBuilder().setMsg(RPC_ECHO_STRING).build();
        UnaryFuture<Echo.EchoMessage> responseFuture = methodClient.invokeUnaryFuture(message);

        UnaryResult<Echo.EchoMessage> responseResult = responseFuture.get(2, TimeUnit.SECONDS);
        Assert.assertNotNull(responseResult);
        Assert.assertTrue(responseResult.status().ok());

        Echo.EchoMessage response = responseResult.response();
        Assert.assertNotNull(response);
        Assert.assertEquals(RPC_ECHO_STRING, response.getMsg());
    }
}