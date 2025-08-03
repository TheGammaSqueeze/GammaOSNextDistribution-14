/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.devicelockcontroller.activities;

import java.util.Objects;

/**
 * A data model class which is used to describe the policy that the device provider enforces on the
 * device.
 */
final class DevicePolicy {
    private final int mDrawableId;

    private final int mTextId;

    DevicePolicy(int drawableId, int textId) {
        mDrawableId = drawableId;
        mTextId = textId;
    }

    int getDrawableId() {
        return mDrawableId;
    }

    int getTextId() {
        return mTextId;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof DevicePolicy)) {
            return false;
        }
        DevicePolicy that = (DevicePolicy) obj;
        return this.mDrawableId == that.mDrawableId && this.mTextId == that.mTextId;
    }

    @Override
    public int hashCode() {
        return Objects.hash(mDrawableId, mTextId);
    }
}
