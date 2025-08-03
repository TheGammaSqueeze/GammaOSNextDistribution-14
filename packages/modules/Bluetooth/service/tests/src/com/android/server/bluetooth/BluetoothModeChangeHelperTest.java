/*
 * Copyright 2022 The Android Open Source Project
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

package com.android.server.bluetooth;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.spy;

import android.content.Context;
import android.content.ContextWrapper;
import android.text.TextUtils;

import androidx.test.InstrumentationRegistry;
import androidx.test.annotation.UiThreadTest;
import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class BluetoothModeChangeHelperTest {

    @Mock
    BluetoothManagerService mService;

    Context mContext;
    BluetoothModeChangeHelper mHelper;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        mContext = spy(new ContextWrapper(InstrumentationRegistry.getTargetContext()));

        mHelper = new BluetoothModeChangeHelper(mContext);
    }

    @Test
    public void isBluetoothOn_doesNotCrash() {
        // assertThat(mHelper.isBluetoothOn()).isFalse();
        // TODO: Strangely, isBluetoothOn() does not call BluetoothAdapter.isEnabled().
        //       Instead, it calls isLeEnabled(). Two results can be different.
        //       Is this a mistake, or in purpose?
        mHelper.isBluetoothOn();
    }

    @Test
    public void isBluetoothOnAPM_doesNotCrash() {
        mHelper.isBluetoothOnAPM();
    }

    @UiThreadTest
    @Test
    public void showToastMessage_doesNotCrash() {
        mHelper.showToastMessage();
    }

    @Test
    public void getBluetoothPackageName() {
        // TODO: Find a good way to specify the exact name of bluetooth package.
        //       mContext.getPackageName() does not work as this is not a test for BT app.
        String bluetoothPackageName = mHelper.getBluetoothPackageName();

        boolean packageNameFound =
                TextUtils.equals(bluetoothPackageName, "com.android.bluetooth")
                || TextUtils.equals(bluetoothPackageName, "com.google.android.bluetooth");

        assertThat(packageNameFound).isTrue();
    }
}
