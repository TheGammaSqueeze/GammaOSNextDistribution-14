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
package android.usage.cts_root;

import static android.os.storage.StorageManager.UUID_DEFAULT;

import static com.google.common.truth.Truth.assertThat;

import android.app.usage.StorageStats;
import android.app.usage.StorageStatsManager;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageStats;
import android.os.UserHandle;
import android.util.Log;

import androidx.test.core.app.ApplicationProvider;

import com.android.server.LocalManagerRegistry;
import com.android.server.usage.StorageStatsManagerLocal;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.junit.Test;

public class StorageStatsManagerLocalTest {
    private static final String TAG = "StorageStatsManagerLocalTest";

    private static final int AUGMENT_BYTES_FOR_PACKAGE = 7907;
    private static final int AUGMENT_BYTES_FOR_UID = 6883;
    private static final int AUGMENT_BYTES_FOR_USER = 5741;

    @Test
    public void testStorageAugmenter() throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        StorageStatsManager storageStatsManager =
                context.getSystemService(StorageStatsManager.class);
        String packageName = context.getPackageName();
        UserHandle user = context.getUser();
        int uid = android.os.Process.myUid();

        // Collect stats before augmentation
        StorageStats beforeStatsForPkg =
                storageStatsManager.queryStatsForPackage(UUID_DEFAULT, packageName, user);
        StorageStats beforeStatsForUid = storageStatsManager.queryStatsForUid(UUID_DEFAULT, uid);
        StorageStats beforeStatsForUser = storageStatsManager.queryStatsForUser(UUID_DEFAULT, user);

        // Register our augmenter
        LocalManagerRegistry.getManager(StorageStatsManagerLocal.class)
                .registerStorageStatsAugmenter(new TestStorageStatsAugmenter(), TAG);

        // Collect stats after augmentation
        StorageStats afterStatsForPkg =
                storageStatsManager.queryStatsForPackage(UUID_DEFAULT, packageName, user);
        StorageStats afterStatsForUid = storageStatsManager.queryStatsForUid(UUID_DEFAULT, uid);
        StorageStats afterStatsForUser = storageStatsManager.queryStatsForUser(UUID_DEFAULT, user);

        // Verify the storage size increase.
        assertThat(afterStatsForPkg.getDataBytes() - beforeStatsForPkg.getDataBytes())
                .isEqualTo(AUGMENT_BYTES_FOR_PACKAGE);
        assertThat(afterStatsForUid.getDataBytes() - beforeStatsForUid.getDataBytes())
                .isEqualTo(AUGMENT_BYTES_FOR_UID);
        assertThat(afterStatsForUser.getDataBytes() - beforeStatsForUser.getDataBytes())
                .isEqualTo(AUGMENT_BYTES_FOR_USER);
    }

    private class TestStorageStatsAugmenter implements
            StorageStatsManagerLocal.StorageStatsAugmenter {
        @Override
        public void augmentStatsForPackageForUser(
                PackageStats stats,
                String packageName,
                UserHandle userHandle,
                boolean canCallerAccessAllStats) {
            stats.dataSize += AUGMENT_BYTES_FOR_PACKAGE;
        }

        @Override
        public void augmentStatsForUid(
                PackageStats stats, int uid, boolean canCallerAccessAllStats) {
            stats.dataSize += AUGMENT_BYTES_FOR_UID;
        }

        @Override
        public void augmentStatsForUser(PackageStats stats, UserHandle userHandle) {
            stats.dataSize += AUGMENT_BYTES_FOR_USER;
        }
    }
}
