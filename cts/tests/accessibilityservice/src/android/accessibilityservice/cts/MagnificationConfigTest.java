/*
 * Copyright (C) 2021 The Android Open Source Project
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

package android.accessibilityservice.cts;

import static android.accessibilityservice.MagnificationConfig.MAGNIFICATION_MODE_FULLSCREEN;

import static org.junit.Assert.assertEquals;

import android.accessibilityservice.MagnificationConfig;
import android.os.Parcel;
import android.platform.test.annotations.Presubmit;

import androidx.test.runner.AndroidJUnit4;

import com.android.compatibility.common.util.CddTest;

import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Class for testing {@link android.accessibilityservice.MagnificationConfig}.
 */
@Presubmit
@RunWith(AndroidJUnit4.class)
@CddTest(requirements = {"3.10/C-1-1,C-1-2"})
public class MagnificationConfigTest {

    private final int mMode = MAGNIFICATION_MODE_FULLSCREEN;
    private final boolean mActivated = true;
    private final float mScale = 1;
    private final float mCenterX = 2;
    private final float mCenterY = 3;

    @Test
    public void testMarshaling() {
        // Populate the magnification config to marshal.
        MagnificationConfig magnificationConfig = populateMagnificationConfig();

        // Marshal and unmarshal the magnification config.
        Parcel parcel = Parcel.obtain();
        magnificationConfig.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        MagnificationConfig fromParcel =
                MagnificationConfig.CREATOR.createFromParcel(parcel);

        // Make sure all fields properly marshaled.
        assertEqualsMagnificationConfig(magnificationConfig, fromParcel);

        parcel.recycle();
    }

    @Test
    public void testGetterMethods_dataPopulated_assertDataEqual() {
        MagnificationConfig magnificationConfig = populateMagnificationConfig();

        assertEquals("getMode is different from magnificationConfig", mMode,
                magnificationConfig.getMode());
        assertEquals("isActivated is different from magnificationConfig", mActivated,
                magnificationConfig.isActivated());
        assertEquals("getScale is different from magnificationConfig", mScale,
                magnificationConfig.getScale(), 0);
        assertEquals("getCenterX is different from magnificationConfig", mCenterX,
                magnificationConfig.getCenterX(), 0);
        assertEquals("getCenterY is different from magnificationConfig", mCenterY,
                magnificationConfig.getCenterY(), 0);
    }

    private void assertEqualsMagnificationConfig(MagnificationConfig expectedConfig,
            MagnificationConfig actualConfig) {
        assertEquals("getMode has incorrect value", expectedConfig.getMode(),
                actualConfig.getMode());
        assertEquals("isActivated has incorrect value", expectedConfig.isActivated(),
                actualConfig.isActivated());
        assertEquals("getScale has incorrect value", expectedConfig.getScale(),
                actualConfig.getScale(), 0);
        assertEquals("getCenterX has incorrect value", expectedConfig.getCenterX(),
                actualConfig.getCenterX(), 0);
        assertEquals("getCenterY has incorrect value", expectedConfig.getCenterY(),
                actualConfig.getCenterY(), 0);
    }

    private MagnificationConfig populateMagnificationConfig() {
        MagnificationConfig.Builder builder =
                new MagnificationConfig.Builder();
        MagnificationConfig magnificationConfig = builder.setMode(mMode).setActivated(mActivated)
                        .setScale(mScale).setCenterX(mCenterX).setCenterY(mCenterY).build();
        return magnificationConfig;
    }
}
