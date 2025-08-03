/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.phone.security;

import static android.safetycenter.SafetyCenterManager.ACTION_REFRESH_SAFETY_SOURCES;
import static android.safetycenter.SafetyCenterManager.EXTRA_REFRESH_SAFETY_SOURCES_BROADCAST_ID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.platform.test.flag.junit.SetFlagsRule;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.internal.telephony.Phone;
import com.android.internal.telephony.flags.Flags;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@RunWith(AndroidJUnit4.class)
public class SafetySourceReceiverTest {
    @Rule
    public final SetFlagsRule mSetFlagsRule = new SetFlagsRule();

    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    Context mContext;

    SafetySourceReceiver mSafetySourceReceiver;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        SafetySourceReceiver receiver = new SafetySourceReceiver();
        mSafetySourceReceiver = spy(receiver);

        when(mContext.getPackageManager().hasSystemFeature(
                PackageManager.FEATURE_TELEPHONY)).thenReturn(true);
    }

    @Test
    public void testOnReceive() {
        mSetFlagsRule.enableFlags(Flags.FLAG_ENABLE_IDENTIFIER_DISCLOSURE_TRANSPARENCY_UNSOL_EVENTS,
                Flags.FLAG_ENABLE_MODEM_CIPHER_TRANSPARENCY_UNSOL_EVENTS,
                Flags.FLAG_ENFORCE_TELEPHONY_FEATURE_MAPPING_FOR_PUBLIC_APIS);
        Phone mockPhone = mock(Phone.class);
        when(mSafetySourceReceiver.getDefaultPhone()).thenReturn(mockPhone);

        Intent intent = new Intent(ACTION_REFRESH_SAFETY_SOURCES);
        intent.putExtra(EXTRA_REFRESH_SAFETY_SOURCES_BROADCAST_ID, "aBroadcastId");
        mSafetySourceReceiver.onReceive(mContext, intent);

        verify(mockPhone, times(1)).refreshSafetySources("aBroadcastId");
    }

    @Test
    public void testOnReceive_featureFlagsOff() {
        mSetFlagsRule.disableFlags(
                Flags.FLAG_ENABLE_IDENTIFIER_DISCLOSURE_TRANSPARENCY_UNSOL_EVENTS,
                Flags.FLAG_ENABLE_MODEM_CIPHER_TRANSPARENCY_UNSOL_EVENTS,
                Flags.FLAG_ENFORCE_TELEPHONY_FEATURE_MAPPING_FOR_PUBLIC_APIS);

        Intent intent = new Intent(ACTION_REFRESH_SAFETY_SOURCES);
        intent.putExtra(EXTRA_REFRESH_SAFETY_SOURCES_BROADCAST_ID, "aBroadcastId");
        mSafetySourceReceiver.onReceive(mContext, intent);

        verify(mSafetySourceReceiver, never()).getDefaultPhone();
    }

    @Test
    public void testOnReceive_phoneNotReadyYet() {
        mSetFlagsRule.enableFlags(Flags.FLAG_ENABLE_IDENTIFIER_DISCLOSURE_TRANSPARENCY_UNSOL_EVENTS,
                Flags.FLAG_ENABLE_MODEM_CIPHER_TRANSPARENCY_UNSOL_EVENTS,
                Flags.FLAG_ENFORCE_TELEPHONY_FEATURE_MAPPING_FOR_PUBLIC_APIS);
        when(mSafetySourceReceiver.getDefaultPhone()).thenReturn(null);

        Intent intent = new Intent(ACTION_REFRESH_SAFETY_SOURCES);
        intent.putExtra(EXTRA_REFRESH_SAFETY_SOURCES_BROADCAST_ID, "aBroadcastId");

        // this call succeeding without a NPE means this test has passed. There are no observable
        // side effects to a null Phone, because all side effects happen on the Phone instance.
        mSafetySourceReceiver.onReceive(mContext, intent);
    }

    @Test
    public void testOnReceive_noTelephonyFeature() {
        mSetFlagsRule.enableFlags(Flags.FLAG_ENABLE_IDENTIFIER_DISCLOSURE_TRANSPARENCY_UNSOL_EVENTS,
                Flags.FLAG_ENABLE_MODEM_CIPHER_TRANSPARENCY_UNSOL_EVENTS,
                Flags.FLAG_ENFORCE_TELEPHONY_FEATURE_MAPPING_FOR_PUBLIC_APIS);

        when(mContext.getPackageManager().hasSystemFeature(
                PackageManager.FEATURE_TELEPHONY)).thenReturn(false);

        Phone mockPhone = mock(Phone.class);
        when(mSafetySourceReceiver.getDefaultPhone()).thenReturn(mockPhone);

        Intent intent = new Intent(ACTION_REFRESH_SAFETY_SOURCES);
        intent.putExtra(EXTRA_REFRESH_SAFETY_SOURCES_BROADCAST_ID, "aBroadcastId");
        mSafetySourceReceiver.onReceive(mContext, intent);

        verify(mockPhone, never()).refreshSafetySources(any());
    }
}
