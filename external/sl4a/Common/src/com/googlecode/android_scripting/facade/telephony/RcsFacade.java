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

package com.googlecode.android_scripting.facade.telephony;

import android.content.Context;
import android.telephony.SubscriptionManager;
import android.telephony.ims.ImsException;
import android.telephony.ims.ImsManager;
import android.telephony.ims.RcsClientConfiguration;
import android.util.Log;

import com.android.libraries.rcs.simpleclient.SimpleRcsClient;
import com.android.libraries.rcs.simpleclient.protocol.cpim.SimpleCpimMessage;
import com.android.libraries.rcs.simpleclient.protocol.sip.SipSession;
import com.android.libraries.rcs.simpleclient.provisioning.ProvisioningStateChangeCallback;
import com.android.libraries.rcs.simpleclient.provisioning.StaticConfigProvisioningController;
import com.android.libraries.rcs.simpleclient.registration.RegistrationController;
import com.android.libraries.rcs.simpleclient.registration.RegistrationControllerImpl;
import com.android.libraries.rcs.simpleclient.registration.RegistrationStateChangeCallback;
import com.android.libraries.rcs.simpleclient.service.ImsService;
import com.android.libraries.rcs.simpleclient.service.chat.ChatServiceListener;
import com.android.libraries.rcs.simpleclient.service.chat.ChatSessionListener;
import com.android.libraries.rcs.simpleclient.service.chat.MinimalCpmChatService;
import com.android.libraries.rcs.simpleclient.service.chat.SimpleChatSession;

import com.googlecode.android_scripting.facade.FacadeManager;
import com.googlecode.android_scripting.jsonrpc.RpcReceiver;
import com.googlecode.android_scripting.rpc.Rpc;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Exposes RCS single registration functionality based on SimpleClient lib implementation
 */
public class RcsFacade extends RpcReceiver {

    private static final String TAG = RcsFacade.class.getSimpleName();

    private final Context mContext;
    private final ChatSessionListener mSessionListener = new ChatSessionListener() {
        @Override
        public void onMessageReceived(SimpleCpimMessage message) {
            Log.i(TAG, "Received message: " + message.content());
        }
    };
    private ImsManager mImsManager;
    private StaticConfigProvisioningController mProvisioningController;
    private RegistrationController mRegistrationController;
    private MinimalCpmChatService mChatService;
    private SimpleRcsClient mSimpleRcsClient;
    private Executor mExecutor;
    private SimpleChatSession mChatsession;
    private final ChatServiceListener mServiceListener = new ChatServiceListener() {
        @Override
        public void onIncomingSession(SimpleChatSession chatSession) {
            Log.i(TAG, "Received incoming session");
            mChatsession = chatSession;
            mChatsession.setListener(mSessionListener);
        }
    };
    private String mConfigXml = "";
    private RegistrationStateChangeCallback mRegistrationCB =
            new RegistrationStateChangeCallback() {
        @Override
        public void notifyRegStateChanged(ImsService imsService) {
            Log.i(TAG, "RegistrationStateChangeCallback");
        }

        @Override
        public void onFailure(String error) {
            Log.i(TAG, "Registration Failure:" + error);
        }

        @Override
        public void onSuccess(SipSession sipSession) {
            Log.i(TAG, "Registration Success");
        }
    };
    private ProvisioningStateChangeCallback mProvisioningCB =
            new ProvisioningStateChangeCallback() {
        @Override
        public void notifyConfigChanged(byte[] configXml) {
            Log.i(TAG, "Provisioning State Change Callback with configXML? " + (configXml != null));
            mConfigXml = new String(configXml, StandardCharsets.UTF_8);
        }
    };

    public RcsFacade(FacadeManager manager) {
        super(manager);

        int subId = SubscriptionManager.getActiveDataSubscriptionId();
        mContext = manager.getService().getBaseContext();
        mImsManager = mContext.getSystemService(ImsManager.class);
        mExecutor = Executors.newCachedThreadPool();
        mChatService = new MinimalCpmChatService(mContext);
        mChatService.setListener(mServiceListener);
        mProvisioningController = StaticConfigProvisioningController
                .createForSubscriptionId(subId, mContext);
        mRegistrationController = new RegistrationControllerImpl(subId, mExecutor, mImsManager);
        mProvisioningController.onConfigurationChange(mProvisioningCB);
        mSimpleRcsClient = SimpleRcsClient.newBuilder()
                .registrationController(mRegistrationController)
                .provisioningController(mProvisioningController)
                .imsService(mChatService).build();
    }

    /**
     * Start RCS provisioning.
     *
     * @param rcsVersion rcs version
     * @param rcsProfile rcsProfile
     * @param clientVendor clientVendor
     * @param clientVersion clientVersion
     */
    @Rpc(description = "Set RCS Client Configuration Parameters")
    public void startRCSProvisioning(
            String rcsVersion,
            String rcsProfile,
            String clientVendor,
            String clientVersion) throws ImsException {
        RcsClientConfiguration config =
                new RcsClientConfiguration(rcsVersion, rcsProfile, clientVendor, clientVersion);
        Log.i(TAG, "Start RCS Provisioning with client configuratoin parameters: "
                + "rcsVersion = " + rcsVersion
                + ", rcsProfile = " + rcsProfile
                + ", clientVendor = " + clientVendor
                + ", clientVersion = " + clientVersion);
        try {
            mProvisioningController.register(config);
        } catch (Exception e) {
            Log.i(TAG, "Unable to register RCS Configuration: " + e.getLocalizedMessage());
        }
    }

    /**
     * Unregister provisioning config change listener
     */
    @Rpc(description = "Unregister provisioning config change listener")
    public void unregisterRCSProvisioningCallback() throws IllegalStateException {
        Log.i(TAG, "Unregister Provision Manager Callback");
        mProvisioningController.unRegister();
    }

    /**
     * Get RCS configuration xml
     */
    @Rpc(description = "Get RCS configuration XML.")
    public String getRCSConfigXml() throws ImsException {
        return mConfigXml;
    }

    /**
     * Check if the current subscription is RCS VOLTE Single Registartion capable
     */
    @Rpc(description = "Examine if RCS VoLTE Single Registration is available.")
    public boolean isRcsVolteSingleRegistrationCapable() throws ImsException {
        return mProvisioningController.isRcsVolteSingleRegistrationCapable();
    }

    /**
     * Get latest RCS configuration xml
     */
    @Rpc(description = "Get the latest configuration")
    synchronized byte[] getLatestConfiguration() throws IllegalStateException {
        return mProvisioningController.getLatestConfiguration();
    }

    /**
     * Register RCS. This is essential for chat.
     */
    @Rpc(description = "RCS Register")
    public void register() {
        Log.i(TAG, "Register");
        mRegistrationController.register(mChatService, mRegistrationCB);
    }

    /**
     * Deregister RCS
     */
    @Rpc(description = "RCS Deregister")
    public void deregister() {
        Log.i(TAG, "Deregister");
        mRegistrationController.deregister();
    }

    @Override
    public void shutdown() {
    }
}
