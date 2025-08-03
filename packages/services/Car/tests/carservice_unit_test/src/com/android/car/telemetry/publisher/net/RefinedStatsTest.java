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

package com.android.car.telemetry.publisher.net;

import static android.net.NetworkStats.TAG_NONE;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;

import android.annotation.NonNull;
import android.car.test.mocks.AbstractExtendedMockitoTestCase;
import android.os.PersistableBundle;

import com.android.car.telemetry.UidPackageMapper;
import com.android.car.telemetry.publisher.Constants;
import com.android.internal.util.FastXmlSerializer;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.io.StringWriter;
import java.util.List;

/**
 * Tests for {@link RefinedStats}. Some logic is acquired from {@link android.net.NetworkStatsTest}
 * in {@code FrameworksNetTests} test package.
 */
@RunWith(MockitoJUnitRunner.class)
public class RefinedStatsTest extends AbstractExtendedMockitoTestCase {
    private static final int UID_0 = 0;
    private static final int UID_1 = 1;
    private static final int UID_2 = 2;

    private static final int TAG_1 = 1;

    @Mock private UidPackageMapper mMockUidMapper;

    private RefinedStats mRefinedStats; // subject

    public RefinedStatsTest() {
        super(RefinedStatsTest.class.getSimpleName());
    }

    @Before
    public void setUp() throws Exception {
        when(mMockUidMapper.getPackagesForUid(anyInt())).thenReturn(List.of("pkg1", "pkg2"));
        mRefinedStats = new RefinedStats(/* startMillis= */ 10_000, /* endMillis= */ 20_000);
    }

    @Override
    protected void onSessionBuilder(@NonNull CustomMockitoSessionBuilder session) {}

    @Test
    public void testAddNetworkStats_toPersistableBundle() throws Exception {
        FakeNetworkStats stats = new FakeNetworkStats();
        stats.add(buildBucket(UID_0, TAG_1, /* rx= */ 4096, /* tx= */ 2048));
        stats.add(buildBucket(UID_1, TAG_NONE, /* rx= */ 4095, /* tx= */ 2047));
        stats.add(buildBucket(UID_0, TAG_1, /* rx= */ 10000, /* tx= */ 10000)); // merges with 1st

        mRefinedStats.addNetworkStats(stats);
        PersistableBundle result = mRefinedStats.toPersistableBundle(mMockUidMapper);

        PersistableBundle expected = new PersistableBundle();
        expected.putLong(Constants.CONNECTIVITY_BUNDLE_KEY_START_MILLIS, 10_000);
        expected.putLong(Constants.CONNECTIVITY_BUNDLE_KEY_END_MILLIS, 20_000);
        expected.putInt(Constants.CONNECTIVITY_BUNDLE_KEY_SIZE, 2);
        expected.putIntArray(Constants.CONNECTIVITY_BUNDLE_KEY_UID, new int[] {UID_0, UID_1});
        expected.putStringArray(Constants.CONNECTIVITY_BUNDLE_KEY_PACKAGES,
                new String[] {"pkg1,pkg2", "pkg1,pkg2"});
        expected.putIntArray(Constants.CONNECTIVITY_BUNDLE_KEY_TAG, new int[] {TAG_1, TAG_NONE});
        expected.putLongArray(Constants.CONNECTIVITY_BUNDLE_KEY_RX_BYTES, new long[] {14096, 4095});
        expected.putLongArray(Constants.CONNECTIVITY_BUNDLE_KEY_TX_BYTES, new long[] {12048, 2047});
        assertThat(bundleToXml(result)).isEqualTo(bundleToXml(expected));
    }

    @Test
    public void testIsEmpty() throws Exception {
        FakeNetworkStats stats = new FakeNetworkStats();
        stats.add(buildBucket(UID_0, TAG_NONE, /* rx= */ 256, /* tx= */ 256));

        assertThat(mRefinedStats.isEmpty()).isTrue();

        mRefinedStats.addNetworkStats(stats);
        assertThat(mRefinedStats.isEmpty()).isFalse();
    }

    @Test
    public void testSubtract() throws Exception {
        FakeNetworkStats stats = new FakeNetworkStats();
        stats.add(buildBucket(UID_1, TAG_NONE, /* rx= */ 4096, /* tx= */ 2048));
        stats.add(buildBucket(UID_1, TAG_1, /* rx= */ 4096, /* tx= */ 2048));
        // This one is not present in "other" below.
        stats.add(buildBucket(UID_2, TAG_NONE, /* rx= */ 4096, /* tx= */ 2048));
        mRefinedStats.addNetworkStats(stats);

        FakeNetworkStats stats2 = new FakeNetworkStats();
        stats2.add(buildBucket(UID_1, TAG_NONE, /* rx= */ 6, /* tx= */ 8));
        stats2.add(buildBucket(UID_1, TAG_1, /* rx= */ 99999, /* tx= */ 99999));
        // This one is not present in mRefinedStats.
        stats2.add(buildBucket(UID_0, TAG_1, /* rx= */ 6, /* tx= */ 8));
        stats2.add(buildBucket(UID_2, TAG_1, /* rx= */ 6, /* tx= */ 8));
        RefinedStats other = new RefinedStats(/* startMillis= */ 9_000, /* endMillis= */ 15_000);
        other.addNetworkStats(stats2);

        RefinedStats diff = RefinedStats.subtract(mRefinedStats, other);

        PersistableBundle expected = new PersistableBundle();
        expected.putLong(Constants.CONNECTIVITY_BUNDLE_KEY_START_MILLIS, 10_000);
        expected.putLong(Constants.CONNECTIVITY_BUNDLE_KEY_END_MILLIS, 20_000);
        // the same size as "mRefinedStats"
        expected.putInt(Constants.CONNECTIVITY_BUNDLE_KEY_SIZE, 3);
        expected.putIntArray(Constants.CONNECTIVITY_BUNDLE_KEY_UID,
                new int[] {UID_1, UID_1, UID_2});
        expected.putStringArray(Constants.CONNECTIVITY_BUNDLE_KEY_PACKAGES,
                new String[] {"pkg1,pkg2", "pkg1,pkg2", "pkg1,pkg2"});
        expected.putIntArray(Constants.CONNECTIVITY_BUNDLE_KEY_TAG,
                new int[] {TAG_NONE, TAG_1, TAG_NONE});
        // Handles negative values too (hence 0).
        expected.putLongArray(Constants.CONNECTIVITY_BUNDLE_KEY_RX_BYTES,
                new long[] {4090, 0, 4096});
        expected.putLongArray(Constants.CONNECTIVITY_BUNDLE_KEY_TX_BYTES,
                new long[] {2040, 0, 2048});
        assertThat(bundleToXml(diff.toPersistableBundle(mMockUidMapper)))
                .isEqualTo(bundleToXml(expected));
    }

    private static FakeNetworkStats.CustomBucket buildBucket(int uid, int tag, long rx, long tx) {
        return new FakeNetworkStats.CustomBucket(
                /* identity= */ null,
                uid,
                tag,
                /* rxBytes= */ rx,
                /* txBytes= */ tx,
                /* timestampMillis= */ 0);
    }

    /** Converts the bundle to a XML String for easy asserting equality. */
    private static String bundleToXml(PersistableBundle bundle) throws Exception {
        StringWriter writer = new StringWriter();
        FastXmlSerializer serializer = new FastXmlSerializer();
        serializer.setOutput(writer);
        bundle.saveToXml(serializer);
        serializer.flush();
        return writer.toString();
    }
}
