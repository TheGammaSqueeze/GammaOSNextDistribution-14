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
import static android.Manifest.permission.BLUETOOTH_PRIVILEGED;
import static android.bluetooth.BluetoothStatusCodes.FEATURE_SUPPORTED;
import static android.bluetooth.le.DistanceMeasurementResult.NADM_ATTACK_IS_VERY_UNLIKELY;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.le.DistanceMeasurementResult;
import android.content.Context;
import android.os.Build;
import android.os.Parcel;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.bluetooth.flags.Flags;
import com.android.compatibility.common.util.ApiLevelUtil;
import com.android.compatibility.common.util.CddTest;

import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class DistanceMeasurementResultTest {
    private Context mContext;
    private BluetoothAdapter mAdapter;

    @Rule
    public final CheckFlagsRule mCheckFlagsRule =
            DeviceFlagsValueProvider.createCheckFlagsRule();

    @Before
    public void setUp() {
        mContext = InstrumentationRegistry.getInstrumentation().getContext();
        Assume.assumeTrue(ApiLevelUtil.isAtLeast(Build.VERSION_CODES.TIRAMISU));
        Assume.assumeTrue(TestUtils.isBleSupported(mContext));

        mAdapter = TestUtils.getBluetoothAdapterOrDie();
        assertTrue(BTAdapterUtils.enableAdapter(mAdapter, mContext));
        TestUtils.adoptPermissionAsShellUid(BLUETOOTH_CONNECT, BLUETOOTH_PRIVILEGED);
        Assume.assumeTrue(mAdapter.isDistanceMeasurementSupported() == FEATURE_SUPPORTED);
    }

    @After
    public void tearDown() {
        TestUtils.dropPermissionAsShellUid();
        mAdapter = null;
    }

    @CddTest(requirements = {"7.4.3/C-2-1"})
    @Test
    public void createFromParcel() {
        final Parcel parcel = Parcel.obtain();
        try {
            DistanceMeasurementResult result = new DistanceMeasurementResult
                    .Builder(121.0, 120.0)
                    .setAzimuthAngle(90).setErrorAzimuthAngle(45)
                    .setAltitudeAngle(60).setErrorAltitudeAngle(30).build();
            result.writeToParcel(parcel, 0);
            parcel.setDataPosition(0);
            DistanceMeasurementResult resultFromParcel =
                    DistanceMeasurementResult.CREATOR.createFromParcel(parcel);
            assertResultEquals(result, resultFromParcel);
        } finally {
            parcel.recycle();
        }
    }

    @CddTest(requirements = {"7.4.3/C-2-1"})
    @Test
    public void setGetResultMeters() {
        DistanceMeasurementResult result = new DistanceMeasurementResult.Builder(121.0, 120.0)
                .build();
        assertEquals(121.0, result.getResultMeters(), 0.0);
    }

    @CddTest(requirements = {"7.4.3/C-2-1"})
    @Test
    public void setGetErrorMeters() {
        DistanceMeasurementResult result = new DistanceMeasurementResult.Builder(121.0, 120.0)
                .build();
        assertEquals(120.0, result.getErrorMeters(), 0.0);
    }

    @CddTest(requirements = {"7.4.3/C-2-1"})
    @Test
    public void setGetAzimuthAngle() {
        DistanceMeasurementResult result = new DistanceMeasurementResult.Builder(121.0, 120.0)
                .setAzimuthAngle(60).build();
        assertEquals(60.0, result.getAzimuthAngle(), 0.0);
    }

    @CddTest(requirements = {"7.4.3/C-2-1"})
    @Test
    public void setGetErrorAzimuthAngle() {
        DistanceMeasurementResult result = new DistanceMeasurementResult.Builder(121.0, 120.0)
                .setErrorAzimuthAngle(60).build();
        assertEquals(60.0, result.getErrorAzimuthAngle(), 0.0);
    }

    @CddTest(requirements = {"7.4.3/C-2-1"})
    @Test
    public void setGetAltitudeAngle() {
        DistanceMeasurementResult result = new DistanceMeasurementResult.Builder(121.0, 120.0)
                .setAltitudeAngle(60).build();
        assertEquals(60.0, result.getAltitudeAngle(), 0.0);
    }

    @CddTest(requirements = {"7.4.3/C-2-1"})
    @Test
    public void setGetErrorAltitudeAngle() {
        DistanceMeasurementResult result = new DistanceMeasurementResult.Builder(121.0, 120.0)
                .setErrorAltitudeAngle(60).build();
        assertEquals(60.0, result.getErrorAltitudeAngle(), 0.0);
    }

    @RequiresFlagsEnabled(Flags.FLAG_CHANNEL_SOUNDING)
    @CddTest(requirements = {"7.4.3/C-2-1"})
    @Test
    public void setGetDelaySpreadMeters() {
        DistanceMeasurementResult.Builder builder =
                new DistanceMeasurementResult.Builder(121.0, 120.0);
        assertThrows(IllegalArgumentException.class, () -> builder.setDelaySpreadMeters(-1));
        DistanceMeasurementResult result = builder.setDelaySpreadMeters(60).build();
        assertEquals(60.0, result.getDelaySpreadMeters(), 0.0);
    }

    @RequiresFlagsEnabled(Flags.FLAG_CHANNEL_SOUNDING)
    @CddTest(requirements = {"7.4.3/C-2-1"})
    @Test
    public void setGetConfidenceLevel() {
        DistanceMeasurementResult.Builder builder =
                new DistanceMeasurementResult.Builder(121.0, 120.0);
        assertThrows(IllegalArgumentException.class, () -> builder.setConfidenceLevel(101));
        DistanceMeasurementResult result = builder.setConfidenceLevel(0.5).build();
        assertEquals(0.5, result.getConfidenceLevel(), 0.0);
    }

    @RequiresFlagsEnabled(Flags.FLAG_CHANNEL_SOUNDING)
    @CddTest(requirements = {"7.4.3/C-2-1"})
    @Test
    public void setGetDetectedAttackLevel() {
        DistanceMeasurementResult.Builder builder =
                new DistanceMeasurementResult.Builder(121.0, 120.0);
        assertThrows(IllegalArgumentException.class, () -> builder.setDetectedAttackLevel(60));
        DistanceMeasurementResult result =
                builder.setDetectedAttackLevel(NADM_ATTACK_IS_VERY_UNLIKELY).build();
        assertEquals(NADM_ATTACK_IS_VERY_UNLIKELY, result.getDetectedAttackLevel());
    }

    @RequiresFlagsEnabled(Flags.FLAG_CHANNEL_SOUNDING)
    @CddTest(requirements = {"7.4.3/C-2-1"})
    @Test
    public void setGetVelocityMetersPerSecond() {
        DistanceMeasurementResult result = new DistanceMeasurementResult.Builder(121.0, 120.0)
                .setVelocityMetersPerSecond(60).build();
        assertEquals(60.0, result.getVelocityMetersPerSecond(), 0.0);
    }

    @RequiresFlagsEnabled(Flags.FLAG_CHANNEL_SOUNDING)
    @CddTest(requirements = {"7.4.3/C-2-1"})
    @Test
    public void readWriteParcelForCs() {
        Parcel parcel = Parcel.obtain();
        DistanceMeasurementResult result = new DistanceMeasurementResult.Builder(10.0, 5.0)
                .setDelaySpreadMeters(20)
                .setConfidenceLevel(0.5)
                .setDetectedAttackLevel(NADM_ATTACK_IS_VERY_UNLIKELY)
                .setVelocityMetersPerSecond(30)
                .build();
        result.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        DistanceMeasurementResult resultFromParcel =
                DistanceMeasurementResult.CREATOR.createFromParcel(parcel);
        assertEquals(result.getDelaySpreadMeters(), resultFromParcel.getDelaySpreadMeters(), 0.0);
        assertEquals(result.getConfidenceLevel(), resultFromParcel.getConfidenceLevel(), 0.0);
        assertEquals(result.getDetectedAttackLevel(), resultFromParcel.getDetectedAttackLevel());
        assertEquals(result.getVelocityMetersPerSecond(),
                resultFromParcel.getVelocityMetersPerSecond(), 0.0);
    }

    @CddTest(requirements = {"7.4.3/C-2-1"})
    @Test
    public void illegalArgument() {
        assertThrows(IllegalArgumentException.class,
                () -> new DistanceMeasurementResult.Builder(-1.0, 0.0));
        assertThrows(IllegalArgumentException.class,
                () -> new DistanceMeasurementResult.Builder(10.0, -1.0));
        DistanceMeasurementResult.Builder result = new DistanceMeasurementResult.Builder(10.0, 5.0);
        assertThrows(IllegalArgumentException.class, () -> result.setAzimuthAngle(380));
        assertThrows(IllegalArgumentException.class, () -> result.setErrorAzimuthAngle(380));
        assertThrows(IllegalArgumentException.class, () -> result.setAltitudeAngle(180));
        assertThrows(IllegalArgumentException.class, () -> result.setErrorAltitudeAngle(181));
    }

    private void assertResultEquals(DistanceMeasurementResult p, DistanceMeasurementResult other) {
        if (p == null && other == null) {
            return;
        }

        if (p == null || other == null) {
            fail("Cannot compare null with non-null value: p=" + p + ", other=" + other);
        }

        assertEquals(p.getResultMeters(), other.getResultMeters(), 0.0);
        assertEquals(p.getErrorMeters(), other.getErrorMeters(), 0.0);
        assertEquals(p.getAzimuthAngle(), other.getAzimuthAngle(), 0.0);
        assertEquals(p.getErrorAzimuthAngle(), other.getErrorAzimuthAngle(), 0.0);
        assertEquals(p.getAltitudeAngle(), other.getAltitudeAngle(), 0.0);
        assertEquals(p.getErrorAltitudeAngle(), other.getErrorAltitudeAngle(), 0.0);
    }
}
