/**
 * Copyright (C) 2020 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy
 * of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package android.app.usage.cts;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import android.app.usage.CacheQuotaHint;
import android.app.usage.UsageStats;
import android.os.Parcel;

import androidx.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class CacheQuotaHintTest {

    @Test
    public void testCacheQuotaHintBuilder() throws Exception {
        final CacheQuotaHint hint =
                buildHint(/* volumeUuid= */ "uuid", /* uid= */ 0, /* quota= */ 100);
        assertCacheQuotaHint(hint);
    }

    @Test
    public void testCacheQuotaHintBuilderFromCacheQuotaHint() throws Exception {
        final CacheQuotaHint hint = new CacheQuotaHint.Builder(
                buildHint(/* volumeUuid= */ "uuid", /* uid= */ 0, /* quota= */ 100)).build();

        assertCacheQuotaHint(hint);
    }

    @Test
    public void testCacheQuotaHintParcelizeDeparcelize() throws Exception {
        final CacheQuotaHint hint =
                buildHint(/* volumeUuid= */ "uuid", /* uid= */ 0, /* quota= */ 100);

        final Parcel p = Parcel.obtain();
        hint.writeToParcel(p, 0);
        p.setDataPosition(0);

        final CacheQuotaHint targetHint = CacheQuotaHint.CREATOR.createFromParcel(p);
        p.recycle();

        assertCacheQuotaHint(targetHint);
    }

    private CacheQuotaHint buildHint(String volumeUuid, int uid, long quota) {
        return new CacheQuotaHint.Builder()
                .setVolumeUuid(volumeUuid)
                .setUid(uid)
                .setQuota(quota)
                .setUsageStats(new UsageStats()).build();
    }

    private void assertCacheQuotaHint(CacheQuotaHint hint) {
        assertEquals("uuid", hint.getVolumeUuid());
        assertEquals(0, hint.getUid());
        assertEquals(100, hint.getQuota());
        assertNotNull(hint.getUsageStats());
    }
}
