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

package com.android.services.telephony;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import android.content.AttributionSource;
import android.content.Context;
import android.os.Process;
import android.telephony.TelephonyManager;

import com.android.internal.telephony.Phone;
import com.android.internal.telephony.PhoneConstants;
import com.android.phone.PhoneGlobals;

import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class TestTelephonyConnectionSimple extends TelephonyConnection{

    @Mock
    Context mMockContext;

    @Mock
    PhoneGlobals mPhoneGlobals;

    private Phone mMockPhone;

    public TelephonyConnection cloneConnection() {
        return this;
    }

    public TestTelephonyConnectionSimple(){
        super(null, null, android.telecom.Call.Details.DIRECTION_INCOMING);
        MockitoAnnotations.initMocks(this);

        AttributionSource attributionSource = new AttributionSource.Builder(
                Process.myUid()).build();

        mMockPhone    = mock(Phone.class);
        mMockContext  = mock(Context.class);
        mPhoneGlobals = mock(PhoneGlobals.class);

        when(mMockPhone.getSubId()).thenReturn(1);
    }

    public void setMockPhone(Phone newPhone) {
        mMockPhone = newPhone;
    }

    @Override
    public Phone getPhone() {
        return mMockPhone;
    }

}
