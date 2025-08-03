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

package com.android.safetycenter.testing

import android.app.Notification

/** The characteristic properties of a notification. */
data class NotificationCharacteristics(
    val title: String,
    val text: String,
    val actions: List<CharSequence> = emptyList(),
    val importance: Int = IMPORTANCE_ANY,
    val blockable: Boolean? = null
) {
    companion object {
        const val IMPORTANCE_ANY = -1

        private fun importanceMatches(
            statusBarNotificationWithChannel: StatusBarNotificationWithChannel,
            characteristicImportance: Int
        ): Boolean {
            return characteristicImportance == IMPORTANCE_ANY ||
                statusBarNotificationWithChannel.channel.importance == characteristicImportance
        }

        private fun blockableMatches(
            statusBarNotificationWithChannel: StatusBarNotificationWithChannel,
            characteristicBlockable: Boolean?
        ): Boolean {
            return characteristicBlockable == null ||
                statusBarNotificationWithChannel.channel.isBlockable == characteristicBlockable
        }

        private fun isMatch(
            statusBarNotificationWithChannel: StatusBarNotificationWithChannel,
            characteristic: NotificationCharacteristics
        ): Boolean {
            val notif = statusBarNotificationWithChannel.statusBarNotification.notification
            return notif != null &&
                notif.extras.getString(Notification.EXTRA_TITLE) == characteristic.title &&
                notif.extras.getString(Notification.EXTRA_TEXT).orEmpty() == characteristic.text &&
                notif.actions.orEmpty().map { it.title } == characteristic.actions &&
                importanceMatches(statusBarNotificationWithChannel, characteristic.importance) &&
                blockableMatches(statusBarNotificationWithChannel, characteristic.blockable)
        }

        fun areMatching(
            statusBarNotifications: List<StatusBarNotificationWithChannel>,
            characteristics: List<NotificationCharacteristics>
        ): Boolean {
            if (statusBarNotifications.size != characteristics.size) {
                return false
            }
            return statusBarNotifications.zip(characteristics).all { isMatch(it.first, it.second) }
        }
    }
}
