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

package android.security.cts;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.os.Parcel;
import android.os.WorkSource;
import android.platform.test.annotations.AsbSecurityTest;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.sts.common.util.StsExtraBusinessLogicTestCase;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class WorkSourceTest extends StsExtraBusinessLogicTestCase {
    private static final int TEST_PID = 6512;
    private static final String TEST_PACKAGE_NAME = "android.security.cts";

    @Test
    @AsbSecurityTest(cveBugId = 220302519)
    public void testWorkChainParceling() {
        WorkSource ws = new WorkSource(TEST_PID, TEST_PACKAGE_NAME);
        // Create a WorkChain so the mChains becomes non-null
        ws.createWorkChain();
        assertNotNull("WorkChains must be non-null in order to properly test parceling",
                ws.getWorkChains());
        // Then clear it so it's an empty list.
        ws.getWorkChains().clear();
        assertTrue("WorkChains must be empty in order to properly test parceling",
                ws.getWorkChains().isEmpty());

        Parcel p = Parcel.obtain();
        ws.writeToParcel(p, 0);
        p.setDataPosition(0);

        // Read the Parcel back out and validate the two Parcels are identical
        WorkSource readWs = WorkSource.CREATOR.createFromParcel(p);
        assertNotNull(readWs.getWorkChains());
        assertTrue(readWs.getWorkChains().isEmpty());
        assertEquals(ws, readWs);

        // Assert that we've read every byte out of the Parcel.
        assertEquals(p.dataSize(), p.dataPosition());

        p.recycle();
    }
}
