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

package com.android.car.settingslib.enterprise;

import android.annotation.Nullable;

import java.util.Date;

/**
 * TODO(b/208511815): copied from phone (but stripped what's not used), should be moved to
 * SettingsLib
 */
public interface EnterprisePrivacyFeatureProvider {

    /**
     * Returns the time at which the Device Owner last retrieved security logs, or {@code null} if
     * logs were never retrieved by the Device Owner on this device.
     */
    @Nullable
    Date getLastSecurityLogRetrievalTime();

    /**
     * Returns the time at which the Device Owner last requested a bug report, or {@code null} if no
     * bug report was ever requested by the Device Owner on this device.
     */
    @Nullable
    Date getLastBugReportRequestTime();

    /**
     * Returns the time at which the Device Owner last retrieved network logs, or {@code null} if
     * logs were never retrieved by the Device Owner on this device.
     */
    @Nullable
    Date getLastNetworkLogRetrievalTime();

    /**
     * Returns the label of the current user's input method if that input method was set by a Device
     * Owner or Profile Owner in that user. Otherwise, returns {@code null}.
     */
    @Nullable
    String getImeLabelIfOwnerSet();

    /**
     * Returns the number of failed login attempts that the Device Owner or Profile Owner allows
     * before the current user is wiped, or zero if no such limit is set.
     */
    int getMaximumFailedPasswordsBeforeWipeInCurrentUser();
}
