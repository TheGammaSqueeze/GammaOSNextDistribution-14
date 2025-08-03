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

import android.app.usage.ExternalStorageStats;
import android.app.usage.StorageStats;
import android.app.usage.StorageStatsManager;
import android.content.Context;
import android.os.UserHandle;

import java.io.IOException;

public class StorageStatsManagerCompat {
    private final StorageStatsManager mManager;

    public StorageStatsManagerCompat(Context context) {
        mManager = context.getSystemService(StorageStatsManager.class);
    }

    public StorageStatsManagerCompat(StorageStatsManager manager) {
        this.mManager = manager;
    }

    public long getCacheQuotaBytes(String volumeUuid, int uid) {
        return this.mManager.getCacheQuotaBytes(volumeUuid, uid);
    }

    public long getTotalBytes(String fsUuid) throws IOException {
        return mManager.getTotalBytes(fsUuid);
    }

    public long getFreeBytes(String fsUuid) throws IOException {
        return mManager.getFreeBytes(fsUuid);
    }


    public ExternalStorageStats queryExternalStatsForUser(String uuid, UserHandle user)
            throws IOException {
        return mManager.queryExternalStatsForUser(uuid, user);
    }

    public StorageStats queryStatsForUser(String uuid, UserHandle user) throws IOException {
        return mManager.queryStatsForUser(uuid, user);
    }
}
