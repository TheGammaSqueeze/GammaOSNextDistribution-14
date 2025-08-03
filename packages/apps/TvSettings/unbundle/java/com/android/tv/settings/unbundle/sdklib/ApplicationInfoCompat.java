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

import android.content.pm.ApplicationInfo;

public class ApplicationInfoCompat {
    private final ApplicationInfo mInfo;

    public ApplicationInfoCompat(ApplicationInfo info) {
        this.mInfo = info;
        this.enabledSetting = info.enabledSetting;

        this.volumeUuid = info.volumeUuid;
        this.privateFlags = info.privateFlags;
    }

    public boolean isSystemApp() {
        return this.mInfo.isSystemApp();
    }

    public int enabledSetting;
    public String volumeUuid;

    /**
     * Value for {@link #privateFlags}: whether this app is pre-installed on the
     * OEM partition of the system image.
     */
    public static final int PRIVATE_FLAG_OEM = ApplicationInfo.PRIVATE_FLAG_OEM;

    public static final int PRIVATE_FLAG_HIDDEN = ApplicationInfo.PRIVATE_FLAG_HIDDEN;

    /**
     * Value for {@link #privateFlags}: whether this app is pre-installed on the
     * vendor partition of the system image.
     */
    public static final int PRIVATE_FLAG_VENDOR = ApplicationInfo.PRIVATE_FLAG_VENDOR;

    /**
     * Value for {@link #privateFlags}: whether this app is pre-installed on the
     * product partition of the system image.
     */
    public static final int PRIVATE_FLAG_PRODUCT = ApplicationInfo.PRIVATE_FLAG_PRODUCT;

    public @ApplicationInfo.ApplicationInfoPrivateFlags
    int privateFlags;

    public boolean isInternal() {
        return mInfo.isInternal();
    }
}
