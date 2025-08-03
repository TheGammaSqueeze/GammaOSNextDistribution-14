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
package android.cts.statsdatom.net;

import com.android.tradefed.util.RunUtil;
import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import android.cts.statsdatom.lib.AtomTestUtils;
import android.cts.statsdatom.lib.ConfigUtils;
import android.cts.statsdatom.lib.DeviceUtils;
import android.cts.statsdatom.lib.ReportUtils;

import android.telephony.NetworkTypeEnum;

import com.android.internal.os.StatsdConfigProto.StatsdConfig;
import com.android.os.AtomsProto;
import com.android.os.AtomsProto.Atom;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.testtype.DeviceTestCase;
import com.android.tradefed.testtype.IBuildReceiver;

import java.util.List;

public class BytesTransferredTest extends DeviceTestCase implements IBuildReceiver {
    private static final String FEATURE_TELEPHONY = "android.hardware.telephony";

    private IBuildInfo mCtsBuild;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        assertThat(mCtsBuild).isNotNull();
        // Put a delay to give statsd enough time to remove previous configs and
        // reports, as well as install the test app.
        ConfigUtils.removeConfig(getDevice());
        ReportUtils.clearReports(getDevice());
        DeviceUtils.installTestApp(getDevice(), DeviceUtils.STATSD_ATOM_TEST_APK,
                DeviceUtils.STATSD_ATOM_TEST_PKG, mCtsBuild);
        RunUtil.getDefault().sleep(AtomTestUtils.WAIT_TIME_LONG);
    }

    @Override
    protected void tearDown() throws Exception {
        ConfigUtils.removeConfig(getDevice());
        ReportUtils.clearReports(getDevice());
        DeviceUtils.uninstallTestApp(getDevice(), DeviceUtils.STATSD_ATOM_TEST_PKG);
        super.tearDown();
    }

    @Override
    public void setBuild(IBuildInfo buildInfo) {
        mCtsBuild = buildInfo;
    }

    public void testDataUsageBytesTransfer() throws Throwable {
        doTestMobileBytesTransferThat(Atom.DATA_USAGE_BYTES_TRANSFER_FIELD_NUMBER, /*isUidAtom=*/
                false, (atom) -> {
                    final AtomsProto.DataUsageBytesTransfer data =
                            atom.getDataUsageBytesTransfer();
                    final boolean ratTypeGreaterThanUnknown =
                            (data.getRatType() > NetworkTypeEnum.NETWORK_TYPE_UNKNOWN_VALUE);

                    if (ratTypeGreaterThanUnknown) {
                        // Assert that subscription info is valid.
                        assertSubscriptionInfo(data);
                        // DataUsageBytesTransferred atom does not report app uid.
                        return new TransferredBytes(data.getRxBytes(), data.getTxBytes(),
                                data.getRxPackets(), data.getTxPackets(), /*appUid=*/-1);
                    }
                    return null;
                });
    }

    public void testMobileBytesTransfer() throws Throwable {
        // Tests MobileBytesTransfer, passing a ThrowingPredicate that returns TransferredBytes,
        doTestMobileBytesTransferThat(Atom.MOBILE_BYTES_TRANSFER_FIELD_NUMBER, /*isUidAtom=*/true,
                (atom) -> {
                    final AtomsProto.MobileBytesTransfer data = atom.getMobileBytesTransfer();
                    return new TransferredBytes(data.getRxBytes(), data.getTxBytes(),
                            data.getRxPackets(), data.getTxPackets(), data.getUid());
                }
        );
    }

    public void testMobileBytesTransferByFgBg() throws Throwable {

        doTestMobileBytesTransferThat(Atom.MOBILE_BYTES_TRANSFER_BY_FG_BG_FIELD_NUMBER,
                /*isUidAtom=*/true,
                (atom) -> {
                    final AtomsProto.MobileBytesTransferByFgBg data =
                            atom.getMobileBytesTransferByFgBg();
                    if (!data.getIsForeground()) {
                        return null;
                    }
                    return new TransferredBytes(data.getRxBytes(), data.getTxBytes(),
                            data.getRxPackets(), data.getTxPackets(), data.getUid());
                }
        );
    }

    // TODO(b/157651730): Determine how to test tag and metered state within atom.
    public void testBytesTransferByTagAndMetered() throws Throwable {
        doTestMobileBytesTransferThat(Atom.BYTES_TRANSFER_BY_TAG_AND_METERED_FIELD_NUMBER,
                /*isUidAtom=*/true,
                (atom) -> {
                    final AtomsProto.BytesTransferByTagAndMetered data =
                            atom.getBytesTransferByTagAndMetered();
                    if (data.getTag() != 0 /*app traffic not generated on tag 0*/) {
                        return null;
                    }
                    return new TransferredBytes(data.getRxBytes(), data.getTxBytes(),
                            data.getRxPackets(), data.getTxPackets(), data.getUid());
                }
        );
    }

    private static class TransferredBytes {
        final long mRxBytes;
        final long mTxBytes;
        final long mRxPackets;
        final long mTxPackets;
        final long mAppUid;

        TransferredBytes(
                long rxBytes, long txBytes, long rxPackets, long txPackets, long appUid) {
            mRxBytes = rxBytes;
            mTxBytes = txBytes;
            mRxPackets = rxPackets;
            mTxPackets = txPackets;
            mAppUid = appUid;
        }

        TransferredBytes plus(TransferredBytes other) {
            return new TransferredBytes(this.mRxBytes + other.mRxBytes,
                    this.mTxBytes + other.mTxBytes, this.mRxPackets + other.mRxPackets,
                    this.mTxPackets + other.mTxPackets, other.mAppUid);
        }
    }

    @FunctionalInterface
    private interface ThrowingPredicate<S, T extends Throwable> {
        TransferredBytes accept(S s) throws T;
    }

    private void doTestMobileBytesTransferThat(int atomId, boolean isUidAtom,
            ThrowingPredicate<Atom, Exception> p)
            throws Throwable {
        if (!DeviceUtils.hasFeature(getDevice(), FEATURE_TELEPHONY)) return;
        // Upload the config.
        final StatsdConfig.Builder config = ConfigUtils.createConfigBuilder(
                DeviceUtils.STATSD_ATOM_TEST_PKG);
        if (isUidAtom) {
            ConfigUtils.addGaugeMetricForUidAtom(config, atomId, /*uidInAttributionChain=*/false,
                    DeviceUtils.STATSD_ATOM_TEST_PKG);
        } else {
            ConfigUtils.addGaugeMetric(config, atomId);
        }
        ConfigUtils.uploadConfig(getDevice(), config);
        // Generate some mobile traffic.
        DeviceUtils.runDeviceTests(getDevice(), DeviceUtils.STATSD_ATOM_TEST_PKG, ".AtomTests",
                "testGenerateMobileTraffic");
        RunUtil.getDefault().sleep(AtomTestUtils.WAIT_TIME_SHORT);
        // Force poll NetworkStatsService to get most updated network stats from lower layer.
        DeviceUtils.runDeviceTests(getDevice(), DeviceUtils.STATSD_ATOM_TEST_PKG, ".AtomTests",
                "testForcePollNetworkStats");
        RunUtil.getDefault().sleep(AtomTestUtils.WAIT_TIME_SHORT);

        // Trigger atom pull.
        AtomTestUtils.sendAppBreadcrumbReportedAtom(getDevice());
        RunUtil.getDefault().sleep(AtomTestUtils.WAIT_TIME_SHORT);
        final List<Atom> atoms = ReportUtils.getGaugeMetricAtoms(getDevice(),
                /*checkTimestampTruncated=*/true);
        assertThat(atoms.size()).isAtLeast(1);

        boolean foundAppStats = false;
        TransferredBytes transBytesSum = new TransferredBytes(0, 0, 0, 0, /*appUid=*/-1);

        for (final Atom atom : atoms) {
            TransferredBytes transferredBytes = p.accept(atom);
            if (transferredBytes != null) {
                foundAppStats = true;
                // Checks that the uid in the atom corresponds to the app uid and checks that the
                // bytes and packet data are as expected.
                if (isUidAtom) {
                    final int appUid = DeviceUtils.getAppUid(getDevice(),
                            DeviceUtils.STATSD_ATOM_TEST_PKG);
                    assertThat(transferredBytes.mAppUid).isEqualTo(appUid);
                }
                transBytesSum = transBytesSum.plus(transferredBytes);
            }
        }
        assertDataUsageAtomDataExpected(
                transBytesSum.mRxBytes, transBytesSum.mTxBytes,
                transBytesSum.mRxPackets, transBytesSum.mTxPackets);
        assertWithMessage("Data for uid " + DeviceUtils.getAppUid(getDevice(),
                DeviceUtils.STATSD_ATOM_TEST_PKG)
                + " is not found in " + atoms.size() + " atoms.").that(foundAppStats).isTrue();
    }

    private void assertDataUsageAtomDataExpected(long rxb, long txb, long rxp, long txp) {
        assertThat(rxb).isGreaterThan(0L);
        assertThat(txb).isGreaterThan(0L);
        assertThat(rxp).isGreaterThan(0L);
        assertThat(txp).isGreaterThan(0L);
    }

    private void assertSubscriptionInfo(AtomsProto.DataUsageBytesTransfer data) {
        assertThat(data.getSimMcc()).matches("^\\d{3}$");
        assertThat(data.getSimMnc()).matches("^\\d{2,3}$");
        assertThat(data.getCarrierId()).isNotEqualTo(-1); // TelephonyManager#UNKNOWN_CARRIER_ID
    }
}
