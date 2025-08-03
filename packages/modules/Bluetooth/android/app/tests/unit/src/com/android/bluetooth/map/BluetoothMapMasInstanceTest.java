/*
 * Copyright 2023 The Android Open Source Project
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

package com.android.bluetooth.map;

import static com.google.common.truth.Truth.assertThat;

import android.content.Context;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;

import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class BluetoothMapMasInstanceTest {
    private static final int TEST_MAS_ID = 1;
    private static final boolean TEST_ENABLE_SMS_MMS = true;
    private static final String TEST_NAME = "test_name";
    private static final String TEST_PACKAGE_NAME = "test.package.name";
    private static final String TEST_ID = "1111";
    private static final String TEST_PROVIDER_AUTHORITY = "test.project.provider";
    private static final Drawable TEST_DRAWABLE = new ColorDrawable();
    private static final BluetoothMapUtils.TYPE TEST_TYPE = BluetoothMapUtils.TYPE.EMAIL;
    private static final String TEST_UCI = "uci";
    private static final String TEST_UCI_PREFIX = "uci_prefix";

    private BluetoothMapAccountItem mAccountItem;

    @Mock
    private Context mContext;
    @Mock
    private BluetoothMapService mMapService;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);

        mAccountItem = BluetoothMapAccountItem.create(TEST_ID, TEST_NAME, TEST_PACKAGE_NAME,
                TEST_PROVIDER_AUTHORITY, TEST_DRAWABLE, TEST_TYPE, TEST_UCI, TEST_UCI_PREFIX);
    }

    @Test
    public void toString_returnsInfo() {
        BluetoothMapMasInstance instance = new BluetoothMapMasInstance(mMapService, mContext,
                mAccountItem, TEST_MAS_ID, TEST_ENABLE_SMS_MMS);

        String expected = "MasId: " + TEST_MAS_ID + " Uri:" + mAccountItem.mBase_uri + " SMS/MMS:"
                + TEST_ENABLE_SMS_MMS;
        assertThat(instance.toString()).isEqualTo(expected);
    }
}
