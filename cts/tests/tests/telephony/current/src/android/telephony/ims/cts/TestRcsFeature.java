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

import android.telephony.ims.feature.CapabilityChangeRequest;
import android.telephony.ims.feature.RcsFeature;
import android.telephony.ims.stub.CapabilityExchangeEventListener;
import android.telephony.ims.stub.ImsRegistrationImplBase;
import android.telephony.ims.stub.RcsCapabilityExchangeImplBase;
import android.util.Log;

import java.util.List;
import java.util.concurrent.Executor;

public class TestRcsFeature extends RcsFeature {

    private static final String TAG = "CtsTestImsService";

    private final TestImsService.ReadyListener mReadyListener;
    private final TestImsService.RemovedListener mRemovedListener;
    private final TestImsService.RcsCapabilityExchangeEventListener mCapExchangeEventListener;

    private final RcsImsCapabilities mRcsCapabilitiesLte;
    private final RcsImsCapabilities mRcsCapabilitiesIWan;
    private final TestImsService.CapabilitiesSetListener mRcsCapabilityChangedListener;

    private TestRcsCapabilityExchangeImpl mCapExchangeImpl;
    private CapabilityExchangeEventListener mCapEventListener;
    private TestImsService.DeviceCapPublishListener mDeviceCapPublishListener;

    TestRcsFeature(TestImsService.ReadyListener readyListener,
            TestImsService.RemovedListener removedListener,
            TestImsService.CapabilitiesSetListener setListener,
            TestImsService.RcsCapabilityExchangeEventListener capExchangeEventListener) {
        super();
        Log.d(TAG, "TestRcsFeature with default constructor");
        mReadyListener = readyListener;
        mRemovedListener = removedListener;
        mCapExchangeEventListener = capExchangeEventListener;

        mRcsCapabilityChangedListener = setListener;
        mRcsCapabilitiesLte = new RcsImsCapabilities(RcsImsCapabilities.CAPABILITY_TYPE_NONE);
        mRcsCapabilitiesIWan = new RcsImsCapabilities(RcsImsCapabilities.CAPABILITY_TYPE_NONE);

        setFeatureState(STATE_READY);
    }

    TestRcsFeature(TestImsService.ReadyListener readyListener,
            TestImsService.RemovedListener removedListener,
            TestImsService.CapabilitiesSetListener setListener,
            TestImsService.RcsCapabilityExchangeEventListener capExchangeEventListener,
            Executor executor) {
        super(executor);
        Log.d(TAG, "TestRcsFeature with Executor constructor");
        mReadyListener = readyListener;
        mRemovedListener = removedListener;
        mCapExchangeEventListener = capExchangeEventListener;

        mRcsCapabilityChangedListener = setListener;
        mRcsCapabilitiesLte = new RcsImsCapabilities(RcsImsCapabilities.CAPABILITY_TYPE_NONE);
        mRcsCapabilitiesIWan = new RcsImsCapabilities(RcsImsCapabilities.CAPABILITY_TYPE_NONE);

        setFeatureState(STATE_READY);
    }

    public void setDeviceCapPublishListener(TestImsService.DeviceCapPublishListener listener) {
        mDeviceCapPublishListener = listener;
    }

    @Override
    public void onFeatureReady() {
        if (ImsUtils.VDBG) {
            Log.d(TAG, "TestRcsFeature.onFeatureReady called");
        }
        mReadyListener.onReady();
    }

    @Override
    public void onFeatureRemoved() {
        if (ImsUtils.VDBG) {
            Log.d(TAG, "TestRcsFeature.onFeatureRemoved called");
        }
        mRemovedListener.onRemoved();
    }

    public RcsCapabilityExchangeImplBase createCapabilityExchangeImpl(Executor executor,
            CapabilityExchangeEventListener listener) {
        return createCapabilityExchangeImpl(listener);
    }

    @Override
    public RcsCapabilityExchangeImplBase createCapabilityExchangeImpl(
            CapabilityExchangeEventListener listener) {
        if (ImsUtils.VDBG) {
            Log.d(TAG, "TestRcsFeature.createCapabilityExchangeImpl called");
        }
        mCapEventListener = listener;
        mCapExchangeImpl = new TestRcsCapabilityExchangeImpl(mDeviceCapPublishListener);
        mCapExchangeEventListener.onSet();
        return mCapExchangeImpl;
    }

    @Override
    public void destroyCapabilityExchangeImpl(RcsCapabilityExchangeImplBase capExchangeImpl) {
        if (ImsUtils.VDBG) {
            Log.d(TAG, "TestRcsFeature.destroyCapabilityExchangeImpl called");
        }
        mCapEventListener = null;
        mCapExchangeEventListener.onSet();
    }

    public CapabilityExchangeEventListener getEventListener() {
        return mCapEventListener;
    }

    public TestRcsCapabilityExchangeImpl getRcsCapabilityExchangeImpl() {
        return mCapExchangeImpl;
    }

    @Override
    public void changeEnabledCapabilities(CapabilityChangeRequest request,
            CapabilityCallbackProxy c) {

        // Enabled RCS capabilities
        List<CapabilityChangeRequest.CapabilityPair> pairs = request.getCapabilitiesToEnable();
        for (CapabilityChangeRequest.CapabilityPair pair : pairs) {
            if (pair.getRadioTech() == ImsRegistrationImplBase.REGISTRATION_TECH_LTE) {
                mRcsCapabilitiesLte.addCapabilities(pair.getCapability());
            } else if (pair.getRadioTech() == ImsRegistrationImplBase.REGISTRATION_TECH_IWLAN) {
                mRcsCapabilitiesIWan.addCapabilities(pair.getCapability());
            }
        }

        // Disabled RCS capabilities
        pairs = request.getCapabilitiesToDisable();
        for (CapabilityChangeRequest.CapabilityPair pair : pairs) {
            if (pair.getRadioTech() == ImsRegistrationImplBase.REGISTRATION_TECH_LTE) {
                mRcsCapabilitiesLte.removeCapabilities(pair.getCapability());
            } else if (pair.getRadioTech() == ImsRegistrationImplBase.REGISTRATION_TECH_IWLAN) {
                mRcsCapabilitiesIWan.removeCapabilities(pair.getCapability());
            }
        }
        mRcsCapabilityChangedListener.onSet();
    }

    @Override
    public boolean queryCapabilityConfiguration(int capability, int radioTech) {
        if (ImsUtils.VDBG) {
            Log.d(TAG, "TestRcsFeature.queryCapabilityConfiguration capability: " + capability);
        }
        if (radioTech == ImsRegistrationImplBase.REGISTRATION_TECH_LTE) {
            return mRcsCapabilitiesLte.isCapable(capability);
        } else if (radioTech == ImsRegistrationImplBase.REGISTRATION_TECH_IWLAN) {
            return mRcsCapabilitiesIWan.isCapable(capability);
        }
        return false;
    }
}
