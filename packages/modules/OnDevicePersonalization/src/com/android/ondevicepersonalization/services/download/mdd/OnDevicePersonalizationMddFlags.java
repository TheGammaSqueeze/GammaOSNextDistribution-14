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

package com.android.ondevicepersonalization.services.download.mdd;

import com.google.android.libraries.mobiledatadownload.Flags;

/**
 * Defines OnDevicePersonalization MobileDataDownload flags
 */
public class OnDevicePersonalizationMddFlags implements Flags {
    /**
     *  Allow non-http urls. This will be used for debuggable packages.
     * {@link OnDevicePersonalizationFileDownloader} will enforce uri scheme instead.
     */
    @Override
    public boolean downloaderEnforceHttps() {
        return false;
    }

    /**
     * Allow sideloading for files. This will be used for debuggable packages.
     * {@link OnDevicePersonalizationFileDownloader} will enforce uri scheme instead.
     */
    @Override
    public boolean enableSideloading() {
        return true;
    }
}
