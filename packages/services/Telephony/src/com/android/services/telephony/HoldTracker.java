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

import java.util.HashSet;
import java.util.Set;

/**
 * Tracks and updates the hold capability of every call or conference across PhoneAccountHandles.
 *
 * @hide
 */
public class HoldTracker {
    private final Set<Holdable> mHoldables;

    public HoldTracker() {
        mHoldables = new HashSet<>();
    }

    /**
     * Adds the holdable, and updates the hold capability for all holdables.
     */
    public void addHoldable(Holdable holdable) {
        if (!mHoldables.contains(holdable)) {
            mHoldables.add(holdable);
            updateHoldCapability();
        }
    }

    /**
     * Removes the holdable, and updates the hold capability for all holdable.
     */
    public void removeHoldable(Holdable holdable) {
        if (mHoldables.remove(holdable)) {
            updateHoldCapability();
        }
    }

    /**
     * Updates the hold capability for all tracked holdables.
     */
    public void updateHoldCapability() {
        int topHoldableCount = 0;
        for (Holdable holdable : mHoldables) {
            if (!holdable.isChildHoldable()) {
                ++topHoldableCount;
            }
        }

        Log.d(this, "updateHoldCapability(): topHoldableCount = "
                + topHoldableCount);
        boolean isHoldable = topHoldableCount < 2;
        for (Holdable holdable : mHoldables) {
            holdable.setHoldable(holdable.isChildHoldable() ? false : isHoldable);
        }
    }
}
