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

package android.cts.statsdatom.media;

import static android.stats.mediametrics.Mediametrics.HdrFormat.HDR_TYPE_UNKNOWN;

import static com.google.common.truth.Truth.assertThat;

import android.cts.statsdatom.lib.AtomTestUtils;
import android.cts.statsdatom.lib.ConfigUtils;
import android.cts.statsdatom.lib.DeviceUtils;
import android.cts.statsdatom.lib.ReportUtils;

import com.android.os.AtomsProto;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.testtype.DeviceTestCase;
import com.android.tradefed.testtype.IBuildReceiver;
import com.android.tradefed.util.RunUtil;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Scanner;
import java.util.Set;
import java.util.stream.Collectors;

public class HdrCapabilitiesTests extends DeviceTestCase implements IBuildReceiver {
    private static final String FEATURE_TV = "android.hardware.type.television";
    private static final String TEST_CLASS = ".HdrCapabilitiesAtomTests";
    private static final String TEST_CLASS_LOGS = "HdrCapabilitiesAtomTests";
    private static final String TEST_PKG = "com.android.server.cts.device.statsdatom";
    private IBuildInfo mCtsBuild;
    private boolean mIsHdrOutputControlSupported;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        assertThat(mCtsBuild).isNotNull();
        ConfigUtils.removeConfig(getDevice());
        ReportUtils.clearReports(getDevice());
        DeviceUtils.installStatsdTestApp(getDevice(), mCtsBuild);
        RunUtil.getDefault().sleep(AtomTestUtils.WAIT_TIME_SHORT);
        DeviceUtils.runDeviceTests(getDevice(), TEST_PKG, TEST_CLASS,
                "cacheOriginalHdrConversionMode");
        fetchIsHdrOutputControlSupportedFromMetrics();
    }

    private void fetchIsHdrOutputControlSupportedFromMetrics() throws Exception {
        if (!DeviceUtils.hasFeature(getDevice(), FEATURE_TV)) {
            return;
        }
        ConfigUtils.uploadConfigForPulledAtom(getDevice(), DeviceUtils.STATSD_ATOM_TEST_PKG,
                AtomsProto.Atom.HDR_CAPABILITIES_FIELD_NUMBER);
        RunUtil.getDefault().sleep(AtomTestUtils.WAIT_TIME_SHORT);

        List<AtomsProto.Atom> atoms = getAtomsFromDevice();
        assertFalse(atoms.isEmpty());

        for (AtomsProto.Atom atom : atoms) {
            mIsHdrOutputControlSupported =
                    atom.getHdrCapabilities().getDeviceSupportsHdrOutputControl();
        }
    }

    @Override
    protected void tearDown() throws Exception {
        DeviceUtils.runDeviceTests(getDevice(), TEST_PKG, TEST_CLASS,
                "restoreOriginalHdrConversionMode");
        ConfigUtils.removeConfig(getDevice());
        ReportUtils.clearReports(getDevice());
        DeviceUtils.uninstallStatsdTestApp(getDevice());
        super.tearDown();
    }

    @Override
    public void setBuild(IBuildInfo buildInfo) {
        mCtsBuild = buildInfo;
    }

    public void testForceHdrFormat() throws Exception {
        // Run this test only on TVs
        if (!DeviceUtils.hasFeature(getDevice(), FEATURE_TV)) return;

        if (!mIsHdrOutputControlSupported) {
            List<AtomsProto.Atom> atoms = getAtomsFromDevice();
            assertFalse(atoms.isEmpty());

            for (AtomsProto.Atom atom : atoms) {
                assertEquals(HDR_TYPE_UNKNOWN,
                        atom.getHdrCapabilities().getForceHdrFormat());
            }
        } else {
            int[] hdrTypes = getHdrTypesFromDevice();
            for (Integer hdrFormat : hdrTypes) {
                DeviceUtils.runDeviceTests(getDevice(), TEST_PKG, TEST_CLASS,
                        forceHdrConversionToMethod(hdrFormat));

                List<AtomsProto.Atom> atoms = getAtomsFromDevice();
                assertFalse(atoms.isEmpty());

                for (AtomsProto.Atom atom : atoms) {
                    assertEquals(hdrFormat.intValue(),
                            atom.getHdrCapabilities().getForceHdrFormat().getNumber());
                }
            }
        }
    }

    public void testHasUserDisabledHdrConversion() throws Exception {
        // Run this test only on TVs
        if (!DeviceUtils.hasFeature(getDevice(), FEATURE_TV)) return;

        if (!mIsHdrOutputControlSupported) {
            List<AtomsProto.Atom> atoms = getAtomsFromDevice();
            assertFalse(atoms.isEmpty());

            for (AtomsProto.Atom atom : atoms) {
                assertFalse(atom.getHdrCapabilities().getHasUserDisabledHdrConversion());
            }
        } else {
            DeviceUtils.runDeviceTests(getDevice(), TEST_PKG, TEST_CLASS,
                    "setHdrConversionPassthrough");

            List<AtomsProto.Atom> atoms = getAtomsFromDevice();
            assertFalse(atoms.isEmpty());

            for (AtomsProto.Atom atom : atoms) {
                assertTrue(atom.getHdrCapabilities().getHasUserDisabledHdrConversion());
            }
            DeviceUtils.runDeviceTests(getDevice(), TEST_PKG, TEST_CLASS,
                    "setHdrConversionForceDV");

            atoms = getAtomsFromDevice();
            assertFalse(atoms.isEmpty());

            for (AtomsProto.Atom atom : atoms) {
                assertFalse(atom.getHdrCapabilities().getHasUserDisabledHdrConversion());
            }
        }
    }

    public void testDeviceHdrOutputCapabilities() throws Exception {
        // Run this test only on TVs
        if (!DeviceUtils.hasFeature(getDevice(), FEATURE_TV)) return;

        DeviceUtils.runDeviceTests(getDevice(), TEST_PKG, TEST_CLASS,
                "getDeviceHdrOutCapabilities");

        String logs = getDevice().executeAdbCommand("logcat", "-v", "brief", "-d",
                TEST_CLASS_LOGS + ":I", "*:S");

        String[] hdrOutputCapabilities = new String[0];
        try (Scanner in = new Scanner(logs)) {
            while (in.hasNextLine()) {
                String line = in.nextLine();

                if (line.contains("hdr-output-capabilities")) {
                    String trim = line.split(":")[2].trim();
                    if (!trim.isBlank()) {
                        hdrOutputCapabilities = trim.split(" ");
                    }
                }
            }
        }
        List<AtomsProto.Atom> atoms = getAtomsFromDevice();
        assertFalse(atoms.isEmpty());

        for (AtomsProto.Atom atom : atoms) {
            List<Long> hdrOutputCapabilitiesList =
                    atom.getHdrCapabilities().getDeviceHdrOutputCapabilities()
                            .getUnknownFields().getField(1).getVarintList();
            assertEquals(hdrOutputCapabilities.length, hdrOutputCapabilitiesList.size());
            Set<Integer> hdrCapabilities = Arrays.stream(hdrOutputCapabilities).map(
                    Integer::parseInt).collect(Collectors.toSet());
            for (Long hdrOutputCapability : hdrOutputCapabilitiesList) {
                assertTrue(hdrCapabilities.contains(hdrOutputCapability.intValue()));
            }
        }
    }

    public void testHas4k30DolbyVisionIssue() throws Exception {
        // Run this test only on TVs
        if (!DeviceUtils.hasFeature(getDevice(), FEATURE_TV)) return;

        DeviceUtils.runDeviceTests(getDevice(), TEST_PKG, TEST_CLASS, "has4k30Issue");

        String logs = getDevice().executeAdbCommand("logcat", "-v", "brief", "-d",
                TEST_CLASS_LOGS + ":I", "*:S");

        boolean has4k30Issue = false;
        try (Scanner in = new Scanner(logs)) {
            while (in.hasNextLine()) {
                String line = in.nextLine();

                if (line.contains("has-4k30-issue")) {
                    has4k30Issue = Boolean.parseBoolean(line.split(":")[2].trim());
                }
            }
        }
        List<AtomsProto.Atom> atoms = getAtomsFromDevice();
        assertFalse(atoms.isEmpty());

        for (AtomsProto.Atom atom : atoms) {
            assertEquals(has4k30Issue, atom.getHdrCapabilities().getHas4K30DolbyVisionIssue());
        }
    }

    private int[] getHdrTypesFromDevice() throws DeviceNotAvailableException {
        DeviceUtils.runDeviceTests(getDevice(), TEST_PKG, TEST_CLASS,
                "getSupportedHdrTypes");

        String logs = getDevice().executeAdbCommand("logcat", "-v", "brief", "-d",
                TEST_CLASS_LOGS + ":I", "*:S");

        String[] hdrStrings = new String[0];
        try (Scanner in = new Scanner(logs)) {
            while (in.hasNextLine()) {
                String line = in.nextLine();

                if (line.contains("hdr-types")) {
                    hdrStrings = line.split(":")[2].trim().split(" ");
                }
            }
        }
        int[] hdrTypes = new int[hdrStrings.length];
        for (int i = 0; i < hdrTypes.length; i++) {
            hdrTypes[i] = Integer.parseInt(hdrStrings[i]);
        }
        return hdrTypes;
    }

    private List<AtomsProto.Atom> getAtomsFromDevice() throws Exception {
        AtomTestUtils.sendAppBreadcrumbReportedAtom(getDevice());
        RunUtil.getDefault().sleep(AtomTestUtils.WAIT_TIME_LONG);
        return ReportUtils.getGaugeMetricAtoms(getDevice());
    }

    private String forceHdrConversionToMethod(int hdrType) {
        switch (hdrType) {
            case 1:
                return "setHdrConversionForceDV";
            case 2:
                return "setHdrConversionForceHDR10";
            case 3:
                return "setHdrConversionForceHLG";
            case 4:
                return "setHdrConversionForceHDR10Plus";
            default:
                fail(String.format(Locale.getDefault(),
                        "HDR type %d does not have a method associated with it in %s, please add "
                                + "one.",
                        hdrType, TEST_CLASS));
        }
        return null;
    }
}
