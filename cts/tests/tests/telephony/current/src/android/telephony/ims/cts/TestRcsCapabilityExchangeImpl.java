/*
 * Copyright (C) 2020 The Android Open Source Project
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

import android.net.Uri;
import android.telephony.ims.ImsException;
import android.telephony.ims.cts.TestImsService.DeviceCapPublishListener;
import android.telephony.ims.stub.RcsCapabilityExchangeImplBase;
import android.util.Log;

import java.util.Collection;
import java.util.Set;

/**
 * A implementation class of RcsCapabilityExchangeImplBase for the TestRcsFeature.
 */
public class TestRcsCapabilityExchangeImpl extends RcsCapabilityExchangeImplBase {

    private static final String LOG_TAG = "TestRcsCapExchangeImpl";

    @FunctionalInterface
    public interface PublishOperation {
        void execute(DeviceCapPublishListener listener, String pidfXml, PublishResponseCallback cb)
                throws ImsException;
    }

    @FunctionalInterface
    public interface SubscribeOperation {
        void execute(Collection<Uri> uris, SubscribeResponseCallback cb) throws ImsException;
    }

    @FunctionalInterface
    public interface OptionsOperation {
        void execute(Uri contactUri, Set<String> myCapabilities, OptionsResponseCallback callback)
                throws ImsException;
    }

    private DeviceCapPublishListener mPublishListener;

    // The operation of publishing capabilities
    private PublishOperation mPublishOperation;

    // The operation of requesting capabilities.
    private SubscribeOperation mSubscribeOperation;

    // The operation of send Options
    private OptionsOperation mOptionsOperation;

    /**
     * Create a new RcsCapabilityExchangeImplBase instance.
     */
    public TestRcsCapabilityExchangeImpl(DeviceCapPublishListener listener) {
        mPublishListener = listener;
    }

    public void setPublishOperator(PublishOperation operation) {
        mPublishOperation = operation;
    }

    public void setSubscribeOperation(SubscribeOperation operator) {
        mSubscribeOperation = operator;
    }

    public void setOptionsOperation(OptionsOperation operation) {
        mOptionsOperation = operation;
    }

    @Override
    public void publishCapabilities(String pidfXml, PublishResponseCallback cb) {
        try {
            mPublishOperation.execute(mPublishListener, pidfXml, cb);
        } catch (ImsException e) {
            Log.w(LOG_TAG, "publishCapabilities exception: " + e);
        }
    }

    @Override
    public void subscribeForCapabilities(Collection<Uri> uris, SubscribeResponseCallback cb) {
        try {
            mSubscribeOperation.execute(uris, cb);
        } catch (ImsException e) {
            Log.w(LOG_TAG, "subscribeForCapabilities exception: " + e);
        }
    }

    @Override
    public void sendOptionsCapabilityRequest(Uri contactUri, Set<String> myCapabilities,
            OptionsResponseCallback callback) {
        try {
            mOptionsOperation.execute(contactUri, myCapabilities, callback);
        } catch (ImsException e) {
            Log.w(LOG_TAG, "sendOptionsCapabilityRequest exception: " + e);
        }
    }
}
