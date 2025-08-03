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

package com.android.systemui.car.qc;

import android.testing.AndroidTestingRunner;
import android.testing.TestableLooper;

import androidx.test.filters.SmallTest;

import com.android.car.qc.QCList;
import com.android.car.qc.QCRow;
import com.android.systemui.SysuiTestCase;
import com.android.systemui.car.CarSystemUiTest;
import com.android.systemui.car.drivemode.InMemoryDriveModeManager;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;

@CarSystemUiTest
@RunWith(AndroidTestingRunner.class)
@TestableLooper.RunWithLooper
@SmallTest
public class DriveModeQcPanelTest extends SysuiTestCase {

    private DriveModeQcPanel mPanel;

    @Before
    public void setup() {
        mPanel = new DriveModeQcPanel(getContext(), new InMemoryDriveModeManager(getContext()));
    }

    @Test
    public void driveModeManagerIsProvided_correctItemsAreReturned() {
        List<QCRow> qcRows = ((QCList) mPanel.getQCItem()).getRows();

        Assert.assertEquals(qcRows.get(0).getTitle(), "Comfort");
        Assert.assertEquals(qcRows.get(1).getTitle(), "Eco");
        Assert.assertEquals(qcRows.get(2).getTitle(), "Sport");
        Assert.assertEquals(qcRows.get(0).getSubtitle(), "Active");
        Assert.assertNull(qcRows.get(1).getSubtitle());
        Assert.assertNull(qcRows.get(2).getSubtitle());
    }
}
