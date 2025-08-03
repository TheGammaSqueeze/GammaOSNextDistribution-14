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

package android.os.cts.powerexemption;


import static org.junit.Assert.assertNotNull;

import android.content.Context;
import android.os.PowerExemptionManager;

import androidx.test.InstrumentationRegistry;
import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class PowerExemptionTest {

    @Test
    public void testPowerExemptionManager_WithClass() {
        Context context = InstrumentationRegistry.getInstrumentation().getContext();
        assertNotNull(context.getSystemService(PowerExemptionManager.class));
    }

    @Test
    public void testPowerExemptionManager_WithContextString() {
        Context context = InstrumentationRegistry.getInstrumentation().getContext();
        assertNotNull(context.getSystemService(Context.POWER_EXEMPTION_SERVICE));
    }
}
