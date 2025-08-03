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

import android.content.pm.PackageInfo;

public class PackageInfoCompat {
    public PackageInfo mPackageInfo;
    public String restrictedAccountType;

    /**
     * The required account type without which this application will not function.
     */
    public String mRequiredAccountType;
    public boolean mRequiredForAllUsers;

    public PackageInfoCompat(PackageInfo packageInfo) {
        mPackageInfo = packageInfo;
        this.mRequiredAccountType = packageInfo.requiredAccountType;
        this.restrictedAccountType = packageInfo.restrictedAccountType;
        this.mRequiredForAllUsers = packageInfo.requiredForAllUsers;
    }
}
