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

package com.android.car.notification;

import android.os.RemoteException;
import android.util.ArraySet;
import android.util.Log;

import com.android.internal.statusbar.IStatusBarService;
import com.android.internal.statusbar.NotificationVisibility;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Handles notification logging, in particular, logging which notifications are visible and which
 * are not.
 */
public class CarNotificationVisibilityLogger {

    private static final String TAG = "CarNotifVisLogger";

    private final ArraySet<NotificationVisibility> mCurrentlyVisible = new ArraySet<>();

    private final NotificationDataManager mNotificationDataManager;
    private IStatusBarService mStatusBarService;

    CarNotificationVisibilityLogger(IStatusBarService statusBarService,
            NotificationDataManager notificationDataManager) {
        mStatusBarService = statusBarService;
        mNotificationDataManager = notificationDataManager;
    }

    private NotificationVisibility obtainNotificationVisibility(AlertEntry entry, int count) {
        return NotificationVisibility.obtain(
                entry.getKey(),
                /* rank= */ -1,
                count,
                /* isVisible= */ true,
                NotificationVisibility.NotificationLocation.LOCATION_MAIN_AREA);
    }

    /**
     * Notifies the appropriate services that the notification state might have changed.
     *
     * @param isVisible Whether the notification panel is visible or not. If it is false the
     *                  method assumes that all the notifications are invisible.
     */
    public void notifyVisibilityChanged(boolean isVisible) {
        ArraySet<NotificationVisibility> previouslyVisible = new ArraySet<>(mCurrentlyVisible);

        ArraySet<NotificationVisibility> newlyVisible = new ArraySet<>();
        ArraySet<NotificationVisibility> noLongerVisible = new ArraySet<>();

        mCurrentlyVisible.clear();

        if (isVisible) {
            List<AlertEntry> entries = mNotificationDataManager.getVisibleNotifications();
            int count = entries.size();

            mCurrentlyVisible.addAll(
                    entries.stream().map(entry -> obtainNotificationVisibility(entry, count))
                            .collect(Collectors.toSet()));

            newlyVisible.addAll(mCurrentlyVisible);
            newlyVisible.removeAll(previouslyVisible);

            noLongerVisible.addAll(previouslyVisible);
            noLongerVisible.removeAll(mCurrentlyVisible);
        } else {
            noLongerVisible.addAll(previouslyVisible);
        }

        onNotificationVisibilityChanged(newlyVisible, previouslyVisible);
        recycleAndClear(noLongerVisible);
    }

    /**
     * Notify StatusBarService of change in notifications' visibility.
     *
     * @param newlyVisible Notifications that became visible.
     * @param noLongerVisible Notifications that are no longer visible.
     */
    private void onNotificationVisibilityChanged(
            Set<NotificationVisibility> newlyVisible, Set<NotificationVisibility> noLongerVisible) {
        if (newlyVisible.isEmpty() && noLongerVisible.isEmpty()) {
            return;
        }

        try {
            NotificationVisibility[] newlyVisibleArray = createDeepClone(newlyVisible)
                    .toArray(new NotificationVisibility[0]);

            NotificationVisibility[] noLongerVisibleArray = createDeepClone(noLongerVisible)
                    .toArray(new NotificationVisibility[0]);

            mStatusBarService.onNotificationVisibilityChanged(newlyVisibleArray,
                    noLongerVisibleArray);
        } catch (RemoteException e) {
            Log.e(TAG, "Failed to notify StatusBarService of notification visibility change");
        }
    }

    /**
     * Clears array and recycles NotificationVisibility objects for reuse.
     *
     * @param set The array that needs to be cleared.
     */
    private static void recycleAndClear(Set<NotificationVisibility> set) {
        set.stream().forEach(NotificationVisibility::recycle);
        set.clear();
    }

    /**
     * Creates a deep clone of the collection by cloning each item of the collection.
     *
     * @param set The collection that has to be cloned.
     */
    private static Set<NotificationVisibility> createDeepClone(
            Set<NotificationVisibility> set) {
        return set.stream().map(NotificationVisibility::clone).collect(Collectors.toSet());
    }
}
