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

package com.android.ims;

import static org.junit.Assert.assertEquals;

import android.os.Parcel;
import android.os.RemoteException;
import android.telephony.ims.DelegateRequest;
import android.telephony.ims.ImsService;
import android.telephony.ims.aidl.ISipDelegate;
import android.telephony.ims.aidl.ISipDelegateMessageCallback;
import android.telephony.ims.aidl.ISipDelegateStateCallback;
import android.telephony.ims.aidl.ISipTransport;
import android.telephony.ims.feature.ImsFeature;
import android.telephony.ims.feature.MmTelFeature;
import android.telephony.ims.stub.ImsConfigImplBase;
import android.telephony.ims.stub.ImsRegistrationImplBase;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class ImsFeatureContainerTest {

    // Use real objects here as I'm not sure how mock IBinders/IInterfaces would parcel.
    private MmTelFeature mMmTelFeature = new MmTelFeature();
    private ImsConfigImplBase mImsConfig = new ImsConfigImplBase();
    private ImsRegistrationImplBase mImsReg = new ImsRegistrationImplBase();
    private ISipTransport mSipTransport = new ISipTransport.Stub() {
        @Override
        public void createSipDelegate(int subId, DelegateRequest request,
                ISipDelegateStateCallback dc, ISipDelegateMessageCallback mc) {
        }

        @Override
        public void destroySipDelegate(ISipDelegate delegate, int reason) {
        }
    };

    @Test
    @SmallTest
    public void testParcelUnparcel() throws Exception {
        final int state = ImsFeature.STATE_READY;
        final long caps = ImsService.CAPABILITY_EMERGENCY_OVER_MMTEL;
        ImsFeatureContainer c = new ImsFeatureContainer(mMmTelFeature.getBinder().asBinder(),
                mImsConfig.getIImsConfig(), mImsReg.getBinder(), mSipTransport, caps);
        c.setState(state);

        ImsFeatureContainer result = parcelUnparcel(c);

        assertEquals(mMmTelFeature.getBinder().asBinder(), result.imsFeature);
        assertEquals(mImsConfig.getIImsConfig(), result.imsConfig);
        assertEquals(mImsReg.getBinder(), result.imsRegistration);
        assertEquals(state, result.getState());
        assertEquals(caps, result.getCapabilities());

        assertEquals(c, result);
    }

    public ImsFeatureContainer parcelUnparcel(ImsFeatureContainer data) {
        Parcel parcel = Parcel.obtain();
        data.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        ImsFeatureContainer unparceledData =
                ImsFeatureContainer.CREATOR.createFromParcel(parcel);
        parcel.recycle();
        return unparceledData;
    }
}
