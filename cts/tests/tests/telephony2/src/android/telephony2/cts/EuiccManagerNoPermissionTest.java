/*
 * Copyright (C) 2024 The Android Open Source Project
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

package android.telephony2.cts;

import static org.junit.Assert.fail;

import android.content.Context;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.telephony.euicc.EuiccManager;

import androidx.test.InstrumentationRegistry;

import com.android.internal.telephony.flags.Flags;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

/**
 * Test APIs when the package does not have any permissions.
 */
public class EuiccManagerNoPermissionTest {
    @Rule
    public final CheckFlagsRule mCheckFlagsRule =
            DeviceFlagsValueProvider.createCheckFlagsRule();

    private EuiccManager mEuiccManager;

    @Before
    public void setUp() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getContext();
        mEuiccManager = context.getSystemService(EuiccManager.class);
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_ESIM_AVAILABLE_MEMORY)
    public void testGetAvailableMemoryInBytes_euiccManagerEnabled_noPermissions() throws Exception {
        if (!mEuiccManager.isEnabled()) {
            return;
        }

        try {
            mEuiccManager.getAvailableMemoryInBytes();
            fail("EuiccManager#getAvailableMemoryInBytes needs READ_PHONE_STATE or "
                    + "READ_PRIVILEGED_PHONE_STATE or carrier privileges");
        } catch (SecurityException e) {
            // expected
        }
    }
}
