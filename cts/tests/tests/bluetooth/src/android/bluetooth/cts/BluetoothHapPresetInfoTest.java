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

package android.bluetooth.cts;

import static android.Manifest.permission.BLUETOOTH_CONNECT;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothHapPresetInfo;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.os.Build;
import android.os.Parcel;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.ApiLevelUtil;
import com.android.compatibility.common.util.CddTest;

import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
@SmallTest
public class BluetoothHapPresetInfoTest {
    private static final int TEST_PRESET_INDEX = 15;
    private static final String TEST_PRESET_NAME = "Test";

    private Context mContext;
    private BluetoothAdapter mAdapter;

    @Before
    public void setUp() {
        mContext = InstrumentationRegistry.getInstrumentation().getContext();

        Assume.assumeTrue(ApiLevelUtil.isAtLeast(Build.VERSION_CODES.TIRAMISU));
        Assume.assumeTrue(TestUtils.isBleSupported(mContext));
        Assume.assumeTrue(TestUtils.isProfileEnabled(BluetoothProfile.HAP_CLIENT));

        TestUtils.adoptPermissionAsShellUid(BLUETOOTH_CONNECT);
        mAdapter = TestUtils.getBluetoothAdapterOrDie();
        assertTrue(BTAdapterUtils.enableAdapter(mAdapter, mContext));
    }

    @After
    public void tearDown() {
        mAdapter = null;
        TestUtils.dropPermissionAsShellUid();
    }

    @CddTest(requirements = {"7.4.3/C-2-1", "7.4.3/C-3-2"})
    @Test
    public void createHapPresetInfo() {
        BluetoothHapPresetInfo presetInfo = createBluetoothHapPresetInfoForTest(TEST_PRESET_INDEX,
                TEST_PRESET_NAME, true /* isAvailable */, false /* isWritable */);
        assertEquals(TEST_PRESET_INDEX, presetInfo.getIndex());
        assertEquals(TEST_PRESET_NAME, presetInfo.getName());
        assertTrue(presetInfo.isAvailable());
        assertFalse(presetInfo.isWritable());
    }

    static BluetoothHapPresetInfo createBluetoothHapPresetInfoForTest(int presetIndex,
            String presetName, boolean isAvailable, boolean isWritable) {
        Parcel out = Parcel.obtain();
        out.writeInt(presetIndex);
        out.writeString(presetName);
        out.writeBoolean(isWritable);
        out.writeBoolean(isAvailable);
        out.setDataPosition(0); // reset position of parcel before passing to constructor
        return BluetoothHapPresetInfo.CREATOR.createFromParcel(out);
    }
}
