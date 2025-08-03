/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.services.telephony;

import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertTrue;

import androidx.test.runner.AndroidJUnit4;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class HoldTrackerTest {

    private HoldTracker mHoldTrackerUT;

    @Before
    public void setUp() throws Exception {
        mHoldTrackerUT = new HoldTracker();
    }

    @Test
    public void oneTopHoldableCanBeHeld() {
        FakeHoldable topHoldable = createHoldable(false);
        mHoldTrackerUT.addHoldable(topHoldable);

        assertTrue(topHoldable.canBeHeld());
    }

    @Test
    public void childHoldableCannotBeHeld() {
        FakeHoldable topHoldable = createHoldable(false);
        FakeHoldable childHoldable = createHoldable(true);
        mHoldTrackerUT.addHoldable(topHoldable);
        mHoldTrackerUT.addHoldable(childHoldable);

        assertTrue(topHoldable.canBeHeld());
        assertFalse(childHoldable.canBeHeld());
    }

    @Test
    public void twoTopHoldablesCannotBeHeld() {
        FakeHoldable topHoldable1 = createHoldable(false);
        FakeHoldable topHoldable2 = createHoldable(false);
        mHoldTrackerUT.addHoldable(topHoldable1);
        mHoldTrackerUT.addHoldable(topHoldable2);

        mHoldTrackerUT.updateHoldCapability();
        assertFalse(topHoldable1.canBeHeld());
        assertFalse(topHoldable2.canBeHeld());
    }

    @Test
    public void removeOneTopHoldableAndUpdateHoldCapabilityCorrectly() {
        FakeHoldable topHoldable1 = createHoldable(false);
        FakeHoldable topHoldable2 = createHoldable(false);
        mHoldTrackerUT.addHoldable(topHoldable1);
        mHoldTrackerUT.addHoldable(topHoldable2);
        assertFalse(topHoldable1.canBeHeld());
        assertFalse(topHoldable2.canBeHeld());

        mHoldTrackerUT.removeHoldable(topHoldable1);
        assertTrue(topHoldable2.canBeHeld());
    }

    public FakeHoldable createHoldable(boolean isChildHoldable) {
        return new FakeHoldable(isChildHoldable);
    }

    private class FakeHoldable implements Holdable {
        private boolean mIsChildHoldable;
        private boolean mIsHoldable;

        FakeHoldable(boolean isChildHoldable) {
            mIsChildHoldable = isChildHoldable;
        }

        @Override
        public boolean isChildHoldable() {
            return mIsChildHoldable;
        }

        @Override
        public void setHoldable(boolean isHoldable) {
            mIsHoldable = isHoldable;
        }

        public boolean canBeHeld() {
            return mIsHoldable;
        }
    }
}
