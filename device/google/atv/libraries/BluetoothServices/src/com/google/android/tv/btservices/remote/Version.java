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

package com.google.android.tv.btservices.remote;

public class Version implements Comparable<Version> {

    public static class OverrideVersion extends Version {
        public OverrideVersion(Version ver) {
            super(ver.major(), ver.minor(), ver.vid(), ver.pid());
        }
    }

    public static final Version BAD_VERSION = new Version(-1, -1, (byte) 0, (byte) 0);

    protected int mMajorVersion;
    protected int mMinorVersion;
    protected int mVendorId;
    protected int mProductId;

    public Version(int majorVersion, int minorVersion, int vendorId, int productId) {
        mMajorVersion = majorVersion;
        mMinorVersion = minorVersion;
        mVendorId = vendorId;
        mProductId = productId;
    }

    public int major() {
        return mMajorVersion;
    }

    public int minor() {
        return mMinorVersion;
    }

    public int vid() {
        return mVendorId;
    }

    public int pid() {
        return mProductId;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof Version)) {
            return false;
        }
        Version v = (Version) o;
        if (mVendorId != v.mVendorId || mProductId != v.mProductId) {
            return false;
        }
        return v.mMajorVersion == mMajorVersion && v.mMinorVersion == mMinorVersion;
    }

    @Override
    public int compareTo(Version version) {
        if (this instanceof OverrideVersion && !(version instanceof OverrideVersion)) {
            return 1;
        }
        if (!(this instanceof OverrideVersion) && version instanceof OverrideVersion) {
            return -1;
        }
        if (mVendorId != version.mVendorId) {
            return mVendorId - version.mVendorId;
        }
        if (mProductId != version.mProductId) {
            return mProductId - version.mProductId;
        }
        final int major = mMajorVersion - version.mMajorVersion;
        if (major != 0) {
            return major;
        }
        return mMinorVersion - version.mMinorVersion;
    }

    public String toVersionString() {
        String major = String.format("%01d", mMajorVersion);
        String minor = String.format("%02d", mMinorVersion);
        return major + "." + minor;
    }

    @Override
    public String toString() {
        return String.format(
                "%s (%02X:%02X)", toVersionString(), mVendorId, mProductId);
    }
}
