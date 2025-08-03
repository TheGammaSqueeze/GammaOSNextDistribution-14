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

package com.android.car.settings.qc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.dx.mockito.inline.extended.ExtendedMockito;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoSession;
import org.mockito.quality.Strictness;

@RunWith(AndroidJUnit4.class)
public class DisabledQCToastBroadcastReceiverTest {
    private static final String TEST_MESSAGE = "test_message";
    private Context mContext = ApplicationProvider.getApplicationContext();
    private DisabledQCToastBroadcastReceiver mReceiver;
    private MockitoSession mSession;

    @Mock
    private Toast mMockToast;

    @Before
    public void setUp() {
        mSession = ExtendedMockito.mockitoSession()
                .initMocks(this)
                .mockStatic(Toast.class)
                .strictness(Strictness.LENIENT)
                .startMocking();

        ExtendedMockito.when(Toast.makeText(any(), anyString(), anyInt())).thenReturn(mMockToast);

        mReceiver = new DisabledQCToastBroadcastReceiver();
    }

    @After
    public void tearDown() {
        if (mSession != null) {
            mSession.finishMocking();
        }
    }

    @Test
    public void onReceive_validMessage_createsToast() {
        Bundle bundle = new Bundle();
        bundle.putString(DisabledQCToastBroadcastReceiver.DISABLED_QC_TOAST_KEY, TEST_MESSAGE);
        Intent intent = new Intent()
                .putExtras(bundle);

        mReceiver.onReceive(mContext, intent);
        ExtendedMockito.verify(
                () -> Toast.makeText(any(), eq(TEST_MESSAGE), anyInt()));
        verify(mMockToast).show();
    }

    @Test
    public void onReceive_nullMessage_doesNotCreateToast() {
        Intent intent = new Intent();

        mReceiver.onReceive(mContext, intent);
        ExtendedMockito.verify(
                () -> Toast.makeText(any(), eq(TEST_MESSAGE), anyInt()), never());
        verify(mMockToast, never()).show();
    }
}
