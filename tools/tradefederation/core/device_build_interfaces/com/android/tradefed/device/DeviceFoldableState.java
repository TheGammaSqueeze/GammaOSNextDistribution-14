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
package com.android.tradefed.device;

/**
 * Representation of device foldable state as returned by "cmd device_state print-states".
 */
public class DeviceFoldableState implements Comparable<DeviceFoldableState> {

    private final long mIdentifier;
    private final String mName;

    public DeviceFoldableState(long identifier, String name) {
        mIdentifier = identifier;
        mName = name;
    }

    public long getIdentifier() {
        return mIdentifier;
    }

    @Override
    public String toString() {
        return "foldable:" + mIdentifier + ":" + mName;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + (int) (mIdentifier ^ (mIdentifier >>> 32));
        result = prime * result + ((mName == null) ? 0 : mName.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        DeviceFoldableState other = (DeviceFoldableState) obj;
        if (mIdentifier != other.mIdentifier) {
            return false;
        }
        if (mName == null) {
            if (other.mName != null) {
                return false;
            }
        } else if (!mName.equals(other.mName)) {
            return false;
        }
        return true;
    }

    @Override
    public int compareTo(DeviceFoldableState o) {
        if (this.mIdentifier == o.mIdentifier) {
            return 0;
        } else if (this.mIdentifier > o.mIdentifier) {
            return 1;
        }
        return -1;
    }
}
