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

package com.android.server.nearby.presence;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.nearby.DataElement;
import android.nearby.PresenceDevice;

import androidx.test.filters.SdkSuppress;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class PresenceManagerTest {
    private static final byte[] IDENTITY =
            new byte[] {1, 2, 3, 4, 1, 2, 3, 4, 1, 2, 3, 4, 1, 2, 3, 4};
    private static final byte[] SALT = {2, 3};
    private static final byte[] SECRET_ID =
            new byte[] {-97, 10, 107, -86, 25, 65, -54, -95, -72, 59, 54, 93, 9, 3, -24, -88};

    @Mock private Context mContext;
    private PresenceManager mPresenceManager;

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);

        mPresenceManager = new PresenceManager(mContext);
        when(mContext.getContentResolver())
                .thenReturn(InstrumentationRegistry.getInstrumentation()
                        .getContext().getContentResolver());
    }

    @Test
    @SdkSuppress(minSdkVersion = 32, codeName = "T")
    public void testInit() {
        mPresenceManager.initiate();

        verify(mContext, times(1)).registerReceiver(any(), any());
    }

    @Test
    @SdkSuppress(minSdkVersion = 32, codeName = "T")
    public void testDeviceStatusUpdated() {
        DataElement dataElement1 = new DataElement(1, new byte[] {1, 2});
        DataElement dataElement2 = new DataElement(2, new byte[] {-1, -2, 3, 4, 5, 6, 7, 8, 9});

        PresenceDevice presenceDevice =
                new PresenceDevice.Builder(/* deviceId= */ "deviceId", SALT, SECRET_ID, IDENTITY)
                        .addExtendedProperty(dataElement1)
                        .addExtendedProperty(dataElement2)
                        .build();

        mPresenceManager.mScanCallback.onDiscovered(presenceDevice);
        mPresenceManager.mScanCallback.onUpdated(presenceDevice);
        mPresenceManager.mScanCallback.onLost(presenceDevice);
    }
}
