/*
 * Copyright (C) 2019 The Android Open Source Project
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

package android.telephony.ims.cts;

import android.telephony.ims.RcsClientConfiguration;
import android.telephony.ims.stub.ImsConfigImplBase;

import java.util.concurrent.LinkedBlockingQueue;

public class TestAcsClient {
    public static int ACTION_SET_RCS_CLIENT_CONFIG = 1;
    public static int ACTION_TRIGGER_AUTO_CONFIG = 2;
    public static int ACTION_CONFIG_CHANGED = 3;
    public static int ACTION_CONFIG_REMOVED = 4;

    private LinkedBlockingQueue<Integer> mActionQueue = new LinkedBlockingQueue<>();
    private RcsClientConfiguration mRcc;
    private byte[] mConfig;
    private ImsConfigImplBase mImsConfigImpl;

    private static TestAcsClient sInstance;

    private TestAcsClient() {}

    public static TestAcsClient getInstance() {
        if (sInstance == null) {
            sInstance = new TestAcsClient();
        }
        return sInstance;
    }

    public void onSetRcsClientConfiguration(RcsClientConfiguration rcc) {
        mActionQueue.offer(ACTION_SET_RCS_CLIENT_CONFIG);
        mRcc = rcc;
    }

    public void onTriggerAutoConfiguration() {
        mActionQueue.offer(ACTION_TRIGGER_AUTO_CONFIG);
    }

    public void onConfigChanged(byte[] config, boolean isCompressed) {
        mActionQueue.offer(ACTION_CONFIG_CHANGED);
        mConfig = isCompressed ? ImsUtils.decompressGzip(config) : config;
    }

    public void onConfigRemoved() {
        mActionQueue.offer(ACTION_CONFIG_REMOVED);
        mConfig = null;
    }

    public LinkedBlockingQueue<Integer> getActionQueue() {
        return mActionQueue;
    }

    public RcsClientConfiguration getRcc() {
        return mRcc;
    }

    public byte[] getConfig() {
        return mConfig;
    }

    public void reset() {
        mActionQueue.clear();
        mRcc = null;
        mConfig = null;
    }

    public void setImsConfigImpl(ImsConfigImplBase impl) {
        mImsConfigImpl = impl;
    }

    public void notifyPreProvisioning(byte[] conf) {
        mImsConfigImpl.notifyPreProvisioningReceived(conf);
    }
}
