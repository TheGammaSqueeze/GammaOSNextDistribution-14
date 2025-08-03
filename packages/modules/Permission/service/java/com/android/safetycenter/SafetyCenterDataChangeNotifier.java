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

package com.android.safetycenter;

import android.annotation.UserIdInt;

import com.android.safetycenter.notifications.SafetyCenterNotificationSender;

import java.util.List;

import javax.annotation.concurrent.NotThreadSafe;

/**
 * Knows how to update classes that need to know about any change in SafetyCenter data, which in
 * this context entails any state change that happened in the data subpackage.
 *
 * @hide
 */
@NotThreadSafe
public final class SafetyCenterDataChangeNotifier {

    private final SafetyCenterNotificationSender mSafetyCenterNotificationSender;
    private final SafetyCenterListeners mSafetyCenterListeners;

    /** Initializes a new instance of {@link SafetyCenterDataChangeNotifier}. */
    SafetyCenterDataChangeNotifier(
            SafetyCenterNotificationSender safetyCenterNotificationSender,
            SafetyCenterListeners safetyCenterListeners) {
        mSafetyCenterNotificationSender = safetyCenterNotificationSender;
        mSafetyCenterListeners = safetyCenterListeners;
    }

    /** Updates classes that depend on data changes (changes of state in the data subpackage). */
    public void updateDataConsumers(UserProfileGroup userProfileGroup, @UserIdInt int userId) {
        mSafetyCenterNotificationSender.updateNotifications(userId);
        mSafetyCenterListeners.deliverDataForUserProfileGroup(userProfileGroup);
    }

    /** Updates classes that depend on data changes (changes of state in the data subpackage). */
    void updateDataConsumers(UserProfileGroup userProfileGroup) {
        mSafetyCenterNotificationSender.updateNotifications(userProfileGroup);
        mSafetyCenterListeners.deliverDataForUserProfileGroup(userProfileGroup);
    }

    /** Updates classes that depend on data changes (changes of state in the data subpackage). */
    void updateDataConsumers(List<UserProfileGroup> userProfileGroups) {
        for (int i = 0; i < userProfileGroups.size(); i++) {
            updateDataConsumers(userProfileGroups.get(i));
        }
    }
}
