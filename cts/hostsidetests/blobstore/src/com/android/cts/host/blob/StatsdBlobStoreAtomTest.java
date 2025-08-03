/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.cts.host.blob;

import static com.google.common.truth.Truth.assertThat;

import android.cts.statsdatom.lib.AtomTestUtils;
import android.cts.statsdatom.lib.ConfigUtils;
import android.cts.statsdatom.lib.ReportUtils;

import com.android.internal.os.StatsdConfigProto;
import com.android.os.AtomsProto;
import com.android.os.StatsLog;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;
import com.android.tradefed.util.RunUtil;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;

@RunWith(DeviceJUnit4ClassRunner.class)
public class StatsdBlobStoreAtomTest extends BaseBlobStoreHostTest {
    private static final String TEST_CLASS = TARGET_PKG + ".AtomTest";

    // Constants that match the constants for AtomTests#testBlobStore
    private static final long BLOB_COMMIT_CALLBACK_TIMEOUT_SEC = 5;
    private static final long BLOB_EXPIRY_DURATION_MS = 24 * 60 * 60 * 1000;
    private static final long BLOB_FILE_SIZE_BYTES = 23 * 1024L;
    private static final long BLOB_LEASE_EXPIRY_DURATION_MS = 60 * 60 * 1000;

    private int mTestAppUid;

    @Before
    public void setUp() throws Exception {
        installPackage(TARGET_APK);
        ConfigUtils.removeConfig(getDevice());
        ReportUtils.clearReports(getDevice());
        RunUtil.getDefault().sleep(AtomTestUtils.WAIT_TIME_LONG);
        mTestAppUid = getAppUid(TARGET_PKG);
    }

    @After
    public void tearDown() throws Exception {
        ConfigUtils.removeConfig(getDevice());
        ReportUtils.clearReports(getDevice());
        uninstallPackage(TARGET_PKG);
    }

    @Test
    public void testPushedBlobStoreStats() throws Exception {
        final StatsdConfigProto.StatsdConfig.Builder conf =
                ConfigUtils.createConfigBuilder(TARGET_PKG);
        ConfigUtils.addEventMetricForUidAtom(conf, AtomsProto.Atom.BLOB_COMMITTED_FIELD_NUMBER,
                /*useUidAttributionChain=*/ false, TARGET_PKG);
        ConfigUtils.addEventMetricForUidAtom(conf, AtomsProto.Atom.BLOB_LEASED_FIELD_NUMBER,
                /*useUidAttributionChain=*/ false, TARGET_PKG);
        ConfigUtils.addEventMetricForUidAtom(conf, AtomsProto.Atom.BLOB_OPENED_FIELD_NUMBER,
                /*useUidAttributionChain=*/ false, TARGET_PKG);
        ConfigUtils.uploadConfig(getDevice(), conf);

        runDeviceTest(TARGET_PKG, TEST_CLASS, "testBlobStoreOps");

        final List<StatsLog.EventMetricData> data = ReportUtils.getEventMetricDataList(getDevice());
        assertThat(data).hasSize(3);

        final AtomsProto.BlobCommitted blobCommitted = data.get(0).getAtom().getBlobCommitted();
        final long blobId = blobCommitted.getBlobId();
        final long blobSize = blobCommitted.getSize();
        assertThat(blobCommitted.getUid()).isEqualTo(mTestAppUid);
        assertThat(blobId).isGreaterThan(0L);
        assertThat(blobSize).isGreaterThan(0L);
        assertThat(blobCommitted.getResult()).isEqualTo(AtomsProto.BlobCommitted.Result.SUCCESS);

        final AtomsProto.BlobLeased blobLeased = data.get(1).getAtom().getBlobLeased();
        assertThat(blobLeased.getUid()).isEqualTo(mTestAppUid);
        assertThat(blobLeased.getBlobId()).isEqualTo(blobId);
        assertThat(blobLeased.getSize()).isEqualTo(blobSize);
        assertThat(blobLeased.getResult()).isEqualTo(AtomsProto.BlobLeased.Result.SUCCESS);

        final AtomsProto.BlobOpened blobOpened = data.get(2).getAtom().getBlobOpened();
        assertThat(blobOpened.getUid()).isEqualTo(mTestAppUid);
        assertThat(blobOpened.getBlobId()).isEqualTo(blobId);
        assertThat(blobOpened.getSize()).isEqualTo(blobSize);
        assertThat(blobOpened.getResult()).isEqualTo(AtomsProto.BlobOpened.Result.SUCCESS);
    }

    @Test
    public void testPulledBlobStoreStats() throws Exception {
        ConfigUtils.uploadConfigForPulledAtom(getDevice(), TARGET_PKG,
                AtomsProto.Atom.BLOB_INFO_FIELD_NUMBER);

        final long testStartTimeMs = getDeviceTimeMs();
        RunUtil.getDefault().sleep(AtomTestUtils.WAIT_TIME_SHORT);
        runDeviceTest(TARGET_PKG, TEST_CLASS, "testBlobStoreOps");
        RunUtil.getDefault().sleep(AtomTestUtils.WAIT_TIME_LONG);
        AtomTestUtils.sendAppBreadcrumbReportedAtom(getDevice());
        RunUtil.getDefault().sleep(AtomTestUtils.WAIT_TIME_SHORT);

        // Add commit callback time to test end time to account for async execution
        final long testEndTimeMs =
                getDeviceTimeMs() + BLOB_COMMIT_CALLBACK_TIMEOUT_SEC * 1000;

        // Find the BlobInfo for the blob created in the test run
        AtomsProto.BlobInfo blobInfo = null;
        for (AtomsProto.Atom atom : ReportUtils.getGaugeMetricAtoms(getDevice())) {
            if (atom.hasBlobInfo()) {
                final AtomsProto.BlobInfo temp = atom.getBlobInfo();
                if (temp.getCommitters().getCommitter(0).getUid() == mTestAppUid) {
                    blobInfo = temp;
                    break;
                }
            }
        }
        assertThat(blobInfo).isNotNull();

        assertThat(blobInfo.getSize()).isEqualTo(BLOB_FILE_SIZE_BYTES);

        // Check that expiry time is reasonable
        assertThat(blobInfo.getExpiryTimestampMillis()).isGreaterThan(
                testStartTimeMs + BLOB_EXPIRY_DURATION_MS);
        assertThat(blobInfo.getExpiryTimestampMillis()).isLessThan(
                testEndTimeMs + BLOB_EXPIRY_DURATION_MS);

        // Check that commit time is reasonable
        final long commitTimeMs = blobInfo.getCommitters().getCommitter(0)
                .getCommitTimestampMillis();
        assertThat(commitTimeMs).isGreaterThan(testStartTimeMs);
        assertThat(commitTimeMs).isLessThan(testEndTimeMs);

        // Check that WHITELIST and PRIVATE access mode flags are set
        assertThat(blobInfo.getCommitters().getCommitter(0).getAccessMode()).isEqualTo(0b1001);
        assertThat(blobInfo.getCommitters().getCommitter(0).getNumWhitelistedPackage())
                .isEqualTo(1);

        assertThat(blobInfo.getLeasees().getLeaseeCount()).isGreaterThan(0);
        assertThat(blobInfo.getLeasees().getLeasee(0).getUid()).isEqualTo(mTestAppUid);

        // Check that lease expiry time is reasonable
        final long leaseExpiryMs = blobInfo.getLeasees().getLeasee(0)
                .getLeaseExpiryTimestampMillis();
        assertThat(leaseExpiryMs).isGreaterThan(testStartTimeMs + BLOB_LEASE_EXPIRY_DURATION_MS);
        assertThat(leaseExpiryMs).isLessThan(testEndTimeMs + BLOB_LEASE_EXPIRY_DURATION_MS);
    }
}
