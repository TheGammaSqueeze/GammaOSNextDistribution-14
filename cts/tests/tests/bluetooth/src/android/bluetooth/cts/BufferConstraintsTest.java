/*
 * Copyright 2021 The Android Open Source Project
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
package android.bluetooth.cts;

import static org.junit.Assert.assertEquals;
import static org.junit.Assume.assumeTrue;

import android.bluetooth.BufferConstraint;
import android.bluetooth.BufferConstraints;
import android.content.Context;
import android.content.pm.PackageManager;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.List;

@RunWith(AndroidJUnit4.class)
public class BufferConstraintsTest {
    private BufferConstraints mBufferConstraints;
    private List<BufferConstraint> mBufferConstraintList;
    private boolean mHasBluetooth;
    private static final int DEFAULT_BUFFER_TIME = 1;
    private static final int MAXIMUM_BUFFER_TIME = 2;
    private static final int MINIMUM_BUFFER_TIME = 3;

    @Before
    public void setUp() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getContext();
        mHasBluetooth = context.getPackageManager().hasSystemFeature(
                PackageManager.FEATURE_BLUETOOTH);
    }

    @Test
    @SmallTest
    public void forCodec() {
        // Skip the test if bluetooth is not present.
        assumeTrue(mHasBluetooth);

        mBufferConstraintList = new ArrayList<BufferConstraint>();
        for (int i = 0; i < BufferConstraints.BUFFER_CODEC_MAX_NUM; i++) {
            int defaultBufferTime = DEFAULT_BUFFER_TIME;
            int maximumBufferTime = MAXIMUM_BUFFER_TIME;
            int minimumBufferTime = MINIMUM_BUFFER_TIME;
            BufferConstraint bufferConstraint = new BufferConstraint(defaultBufferTime,
                    maximumBufferTime, minimumBufferTime);
            mBufferConstraintList.add(bufferConstraint);
        }
        mBufferConstraints = new BufferConstraints(mBufferConstraintList);

        for (int i = 0; i < 6; i++) {
            assertEquals(DEFAULT_BUFFER_TIME, mBufferConstraints.forCodec(i).getDefaultMillis());
            assertEquals(MAXIMUM_BUFFER_TIME, mBufferConstraints.forCodec(i).getMaxMillis());
            assertEquals(MINIMUM_BUFFER_TIME, mBufferConstraints.forCodec(i).getMinMillis());
        }
    }
}
