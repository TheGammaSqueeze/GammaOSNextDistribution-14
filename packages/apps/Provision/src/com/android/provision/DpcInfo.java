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
package com.android.provision;

import static com.android.provision.Utils.DEFAULT_SETTINGS_PROVISION_DO_CHECKSUM;
import static com.android.provision.Utils.DEFAULT_SETTINGS_PROVISION_DO_DOWNLOAD_TRIGGER;
import static com.android.provision.Utils.DEFAULT_SETTINGS_PROVISION_DO_DOWNLOAD_URL;
import static com.android.provision.Utils.DEFAULT_SETTINGS_PROVISION_DO_PKG;
import static com.android.provision.Utils.DEFAULT_SETTINGS_PROVISION_DO_RECEIVER;
import static com.android.provision.Utils.SETTINGS_PROVISION_DO_CHECKSUM;
import static com.android.provision.Utils.SETTINGS_PROVISION_DO_DOWNLOAD_TRIGGER;
import static com.android.provision.Utils.SETTINGS_PROVISION_DO_DOWNLOAD_URL;
import static com.android.provision.Utils.SETTINGS_PROVISION_DO_PKG;
import static com.android.provision.Utils.SETTINGS_PROVISION_DO_RECEIVER;
import static com.android.provision.Utils.getSettings;

import android.content.ComponentName;
import android.content.ContentResolver;

import java.util.Objects;

/**
 * Info about a Device Policy Controller app.
 */
final class DpcInfo {

    public final String packageName;
    private final String mReceiverName;
    public final String checkSum;
    public final String downloadUrl;
    public final int trigger;

    DpcInfo(ContentResolver resolver) {
        this(getSettings(resolver, SETTINGS_PROVISION_DO_PKG, DEFAULT_SETTINGS_PROVISION_DO_PKG),
                getSettings(resolver, SETTINGS_PROVISION_DO_RECEIVER,
                        DEFAULT_SETTINGS_PROVISION_DO_RECEIVER),
                getSettings(resolver, SETTINGS_PROVISION_DO_CHECKSUM,
                        DEFAULT_SETTINGS_PROVISION_DO_CHECKSUM),
                getSettings(resolver, SETTINGS_PROVISION_DO_DOWNLOAD_URL,
                        DEFAULT_SETTINGS_PROVISION_DO_DOWNLOAD_URL),
                getSettings(resolver, SETTINGS_PROVISION_DO_DOWNLOAD_TRIGGER,
                        DEFAULT_SETTINGS_PROVISION_DO_DOWNLOAD_TRIGGER));
    }

    private DpcInfo(String packageName, String receiverName, String checkSum, String downloadUrl,
            int trigger) {
        this.packageName = Objects.requireNonNull(packageName,
                "packageName (" + SETTINGS_PROVISION_DO_PKG + ") cannot be null");
        this.mReceiverName = Objects.requireNonNull(receiverName,
                "receiverName(" + SETTINGS_PROVISION_DO_RECEIVER + ") cannot be null");
        this.checkSum = checkSum;
        this.downloadUrl = downloadUrl;
        this.trigger = trigger;
    }

    /***
     * Gets the name of the admin receiver.
     */
    public ComponentName getReceiverComponentName() {
        return new ComponentName(packageName, mReceiverName);
    }

    @Override
    public String toString() {
        return "DpcInfo[package=" + packageName
                + ", receiver=" + getReceiverComponentName()
                + ", checkSum=" + checkSum
                + ", downloadUrl=" + downloadUrl
                + ", trigger=" + trigger
                + "]";
    }
}
