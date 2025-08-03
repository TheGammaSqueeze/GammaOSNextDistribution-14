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

import android.accounts.Account;
import android.annotation.UserIdInt;
import android.content.ContentResolver;
import android.content.SyncAdapterType;
import android.content.SyncInfo;
import android.os.Bundle;

import java.util.List;

public class ContentResolverCompat {
    public static boolean getSyncAutomaticallyAsUser(
            Account account, String authority, @UserIdInt int userId) {
        return ContentResolver.getSyncAutomaticallyAsUser(account, authority, userId);
    }

    public static List<SyncInfo> getCurrentSyncsAsUser(@UserIdInt int userId) {
        return ContentResolver.getCurrentSyncsAsUser(userId);
    }

    public static SyncStatusInfoCompat getSyncStatusAsUser(Account account, String authority,

            @UserIdInt int userId) {
        return new SyncStatusInfoCompat(
                ContentResolver.getSyncStatusAsUser(account, authority, userId));
    }

    public static final int SYNC_OBSERVER_TYPE_STATUS = 1 << 3;

    public static int getIsSyncableAsUser(
            Account account, String authority, @UserIdInt int userId) {
        return ContentResolver.getIsSyncableAsUser(account, authority, userId);
    }

    public static void setSyncAutomaticallyAsUser(
            Account account, String authority, boolean sync, @UserIdInt int userId) {
        ContentResolver.setSyncAutomaticallyAsUser(account, authority, sync, userId);
    }

    public static boolean getMasterSyncAutomaticallyAsUser(@UserIdInt int userId) {
        return ContentResolver.getMasterSyncAutomaticallyAsUser(userId);
    }

    public static SyncAdapterType[] getSyncAdapterTypesAsUser(@UserIdInt int userId) {
        return ContentResolver.getSyncAdapterTypesAsUser(userId);
    }

    public static final int SYNC_ERROR_SYNC_ALREADY_IN_PROGRESS = 1;

    public static void requestSyncAsUser(
            Account account, String authority, @UserIdInt int userId, Bundle extras) {
        ContentResolver.requestSyncAsUser(account, authority, userId, extras);
    }

    public static void cancelSyncAsUser(Account account, String authority, @UserIdInt int userId) {
        ContentResolver.cancelSyncAsUser(account, authority, userId);
    }
}
