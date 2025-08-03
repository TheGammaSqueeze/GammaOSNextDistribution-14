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

package android.appsearch.app.helper_a;

import static android.os.storage.StorageManager.UUID_DEFAULT;

import static com.google.common.truth.Truth.assertThat;

import android.app.appsearch.AppSearchManager;
import android.app.appsearch.testutil.AppSearchSessionShimImpl;
import android.app.usage.StorageStats;
import android.app.usage.StorageStatsManager;
import android.content.Context;
import android.os.UserHandle;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class AppSearchStorageAugmenterDeviceTest {

    @Test
    public void connectToAppSearch() throws Exception {
        AppSearchSessionShimImpl.createSearchSessionAsync(
                new AppSearchManager.SearchContext.Builder("dbName").build()).get();
    }

    @Test
    public void testReadStorageInfo() throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        StorageStatsManager storageStatsManager =
                context.getSystemService(StorageStatsManager.class);
        String packageName = context.getPackageName();
        UserHandle user = context.getUser();
        int uid = android.os.Process.myUid();

        // Query the storage info through AppSearchStorageStatsAugmenter.
        StorageStats statsForPkg =
                storageStatsManager.queryStatsForPackage(UUID_DEFAULT, packageName, user);
        StorageStats statsForUid = storageStatsManager.queryStatsForUid(UUID_DEFAULT, uid);

        assertThat(statsForPkg.getDataBytes()).isGreaterThan(0);
        assertThat(statsForUid.getDataBytes()).isGreaterThan(0);
    }
}
