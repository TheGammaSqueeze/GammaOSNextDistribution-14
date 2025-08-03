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

import static org.junit.Assert.assertEquals;

import android.os.PersistableBundle;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import com.google.uwb.support.multichip.ChipInfoParams;

import org.junit.Test;
import org.junit.runner.RunWith;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class MultichipTests {

    @Test
    public void testChipInfoParams() {
        String chipId = "testChipId";
        double positionX = 1.0;
        double positionY = 2.0;
        double positionZ = 3.0;

        // Use Builder to build chipInfoParams
        ChipInfoParams chipInfoParams = ChipInfoParams.createBuilder().setChipId(chipId)
                .setPositionX(positionX).setPositionY(positionY).setPositionZ(positionZ).build();

        assertEquals(chipInfoParams.getChipId(), chipId);
        assertEquals(chipInfoParams.getPositionX(), positionX, 0);
        assertEquals(chipInfoParams.getPositionY(), positionY, 0);
        assertEquals(chipInfoParams.getPositionZ(), positionZ, 0);

        // Convert to and from PersistableBundle
        PersistableBundle bundle = chipInfoParams.toBundle();
        ChipInfoParams fromBundle = ChipInfoParams.fromBundle(bundle);

        assertEquals(fromBundle.getChipId(), chipId);
        assertEquals(fromBundle.getPositionX(), positionX, 0);
        assertEquals(fromBundle.getPositionY(), positionY, 0);
        assertEquals(fromBundle.getPositionZ(), positionZ, 0);
    }

    @Test
    public void testChipInfoParamsDefaults() {
        ChipInfoParams chipInfoParams = ChipInfoParams.createBuilder().build();

        assertEquals(chipInfoParams.getChipId(), "UNKNOWN_CHIP_ID");
        assertEquals(chipInfoParams.getPositionX(), 0.0, 0);
        assertEquals(chipInfoParams.getPositionY(), 0.0, 0);
        assertEquals(chipInfoParams.getPositionZ(), 0.0, 0);
    }
}
