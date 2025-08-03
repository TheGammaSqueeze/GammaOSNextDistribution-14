/*
 * Copyright (C) 2020 The Android Open Source Project
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

package android.cts.install;

/**
 * Indicates target test packages of the installation test.
 *
 * @note Once the enum changed, remember to update
 * {@link InstallRule#getTestPackageNames(INSTALL_TYPE)} correspondingly as well.
 */
// TODO(b/136152558): Supports MULTIPLE_APEXS type
public enum INSTALL_TYPE {
    SINGLE_APK,
    SINGLE_APEX,
    MULTIPLE_APKS,
    MULTIPLE_MIX;

    /** Returns true if the install type are testing apex package. */
    public boolean containsApex() {
        switch (this) {
            case SINGLE_APEX:
            case MULTIPLE_MIX:
                return true;
            default:
                return false;
        }
    }
}
