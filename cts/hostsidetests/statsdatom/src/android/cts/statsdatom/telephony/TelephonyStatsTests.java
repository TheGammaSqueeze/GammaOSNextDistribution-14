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

package android.cts.statsdatom.telephony;

import static com.google.common.truth.Truth.assertThat;

import android.cts.statsdatom.lib.AtomTestUtils;
import android.cts.statsdatom.lib.ConfigUtils;
import android.cts.statsdatom.lib.DeviceUtils;
import android.cts.statsdatom.lib.ReportUtils;
import android.telephony.NetworkTypeEnum;

import com.android.os.AtomsProto;
import com.android.os.StatsLog.EventMetricData;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.testtype.DeviceTestCase;
import com.android.tradefed.testtype.IBuildReceiver;
import com.android.tradefed.util.RunUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TelephonyStatsTests extends DeviceTestCase implements IBuildReceiver {

    private static final String FEATURE_TELEPHONY = "android.hardware.telephony";

    // Allow retrying dumpsys for up to 30 seconds when it is not ready
    private static final int DUMPSYS_RETRIES = 30;
    private static final int DUMPSYS_RETRY_WAIT_MILLIS = 1000;

    // Dumpsys output is usually very long. If it is short, it is very likely that dumpsys wasn't
    // ready, e.g. "Can't find service: activity", "No services match: TelephonyDebugService"
    private static final int DUMPSYS_MIN_NUM_LINES = 10;

    // Bitmask of radio access technologies that all GSM phones should at least partially support
    protected static final long NETWORK_TYPE_BITMASK_GSM_ALL =
            (1 << (NetworkTypeEnum.NETWORK_TYPE_GSM_VALUE - 1))
                    | (1 << (NetworkTypeEnum.NETWORK_TYPE_GPRS_VALUE - 1))
                    | (1 << (NetworkTypeEnum.NETWORK_TYPE_EDGE_VALUE - 1))
                    | (1 << (NetworkTypeEnum.NETWORK_TYPE_UMTS_VALUE - 1))
                    | (1 << (NetworkTypeEnum.NETWORK_TYPE_HSDPA_VALUE - 1))
                    | (1 << (NetworkTypeEnum.NETWORK_TYPE_HSUPA_VALUE - 1))
                    | (1 << (NetworkTypeEnum.NETWORK_TYPE_HSPA_VALUE - 1))
                    | (1 << (NetworkTypeEnum.NETWORK_TYPE_HSPAP_VALUE - 1))
                    | (1 << (NetworkTypeEnum.NETWORK_TYPE_TD_SCDMA_VALUE - 1))
                    | (1 << (NetworkTypeEnum.NETWORK_TYPE_LTE_VALUE - 1))
                    | (1 << (NetworkTypeEnum.NETWORK_TYPE_LTE_CA_VALUE - 1))
                    | (1 << (NetworkTypeEnum.NETWORK_TYPE_NR_VALUE - 1));
    // Bitmask of radio access technologies that all CDMA phones should at least partially support
    protected static final long NETWORK_TYPE_BITMASK_CDMA_ALL =
            (1 << (NetworkTypeEnum.NETWORK_TYPE_CDMA_VALUE - 1))
                    | (1 << (NetworkTypeEnum.NETWORK_TYPE_1XRTT_VALUE - 1))
                    | (1 << (NetworkTypeEnum.NETWORK_TYPE_EVDO_0_VALUE - 1))
                    | (1 << (NetworkTypeEnum.NETWORK_TYPE_EVDO_A_VALUE - 1))
                    | (1 << (NetworkTypeEnum.NETWORK_TYPE_EHRPD_VALUE - 1));

    // Telephony phone types
    private static final int PHONE_TYPE_GSM = 1;
    private static final int PHONE_TYPE_CDMA = 2;
    private static final int PHONE_TYPE_CDMA_LTE = 6;

    private IBuildInfo mCtsBuild;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        assertThat(mCtsBuild).isNotNull();
        ConfigUtils.removeConfig(getDevice());
        ReportUtils.clearReports(getDevice());
        DeviceUtils.installStatsdTestApp(getDevice(), mCtsBuild);
        RunUtil.getDefault().sleep(AtomTestUtils.WAIT_TIME_LONG);
    }

    @Override
    protected void tearDown() throws Exception {
        ConfigUtils.removeConfig(getDevice());
        ReportUtils.clearReports(getDevice());
        DeviceUtils.uninstallStatsdTestApp(getDevice());
        super.tearDown();
    }

    @Override
    public void setBuild(IBuildInfo buildInfo) {
        mCtsBuild = buildInfo;
    }

    public void testSupportedRadioAccessFamily() throws Exception {
        if (!DeviceUtils.hasFeature(getDevice(), FEATURE_TELEPHONY)) {
            return;
        }

        ConfigUtils.uploadConfigForPulledAtom(getDevice(), DeviceUtils.STATSD_ATOM_TEST_PKG,
                AtomsProto.Atom.SUPPORTED_RADIO_ACCESS_FAMILY_FIELD_NUMBER);

        AtomTestUtils.sendAppBreadcrumbReportedAtom(getDevice());
        RunUtil.getDefault().sleep(AtomTestUtils.WAIT_TIME_LONG);

        List<AtomsProto.Atom> data = ReportUtils.getGaugeMetricAtoms(getDevice());
        assertThat(data).isNotEmpty();
        AtomsProto.SupportedRadioAccessFamily atom = data.get(0).getSupportedRadioAccessFamily();
        if (hasGsmPhone()) {
            assertThat(atom.getNetworkTypeBitmask() & NETWORK_TYPE_BITMASK_GSM_ALL)
                    .isNotEqualTo(0L);
        }
        if (hasCdmaPhone()) {
            assertThat(atom.getNetworkTypeBitmask() & NETWORK_TYPE_BITMASK_CDMA_ALL)
                    .isNotEqualTo(0L);
        }
    }

    public void testCarrierIdTableVersion() throws Exception {
        if (!DeviceUtils.hasFeature(getDevice(), FEATURE_TELEPHONY)) {
            return;
        }

        int expectedVersion = getCarrierIdTableVersion();

        ConfigUtils.uploadConfigForPulledAtom(getDevice(), DeviceUtils.STATSD_ATOM_TEST_PKG,
                AtomsProto.Atom.CARRIER_ID_TABLE_VERSION_FIELD_NUMBER);

        AtomTestUtils.sendAppBreadcrumbReportedAtom(getDevice());
        RunUtil.getDefault().sleep(AtomTestUtils.WAIT_TIME_LONG);

        List<AtomsProto.Atom> data = ReportUtils.getGaugeMetricAtoms(getDevice());
        assertThat(data).isNotEmpty();
        AtomsProto.CarrierIdTableVersion atom = data.get(0).getCarrierIdTableVersion();
        assertThat(atom.getTableVersion()).isEqualTo(expectedVersion);
    }

    public void testAirplaneModeEvent_shortToggle() throws Exception {
        if (!DeviceUtils.hasFeature(getDevice(), FEATURE_TELEPHONY)) {
            return;
        }

        ConfigUtils.uploadConfigForPushedAtom(getDevice(), DeviceUtils.STATSD_ATOM_TEST_PKG,
                AtomsProto.Atom.AIRPLANE_MODE_FIELD_NUMBER);

        turnOnAirplaneMode();
        // wait long enough for airplane mode events to propagate, but less than threshold for
        // long toggle.
        RunUtil.getDefault().sleep(1_200);
        turnOffAirplaneMode();
        // wait long enough for airplane mode events to propagate.
        RunUtil.getDefault().sleep(3_200);

        // Verify that we have at least one atom for enablement and one for disablement.
        List<EventMetricData> data = ReportUtils.getEventMetricDataList(getDevice());
        AtomsProto.AirplaneMode airplaneModeEnabledAtom = null;
        AtomsProto.AirplaneMode airplaneModeDisabledAtom = null;
        for (EventMetricData d : data) {
            AtomsProto.AirplaneMode atom = d.getAtom().getAirplaneMode();
            if (atom.getIsEnabled() && airplaneModeEnabledAtom == null) {
                airplaneModeEnabledAtom = atom;
            }
            if (!atom.getIsEnabled() && airplaneModeDisabledAtom == null) {
                airplaneModeDisabledAtom = atom;
            }
        }
        assertThat(airplaneModeEnabledAtom).isNotNull();
        assertThat(airplaneModeDisabledAtom).isNotNull();
        assertThat(airplaneModeDisabledAtom.getShortToggle()).isTrue();
    }

    public void testAirplaneModeEvent_longToggle() throws Exception {
        if (!DeviceUtils.hasFeature(getDevice(), FEATURE_TELEPHONY)) {
            return;
        }

        ConfigUtils.uploadConfigForPushedAtom(getDevice(), DeviceUtils.STATSD_ATOM_TEST_PKG,
                AtomsProto.Atom.AIRPLANE_MODE_FIELD_NUMBER);

        turnOnAirplaneMode();
        // wait long enough for long airplane mode toggle (10 seconds).
        RunUtil.getDefault().sleep(12_000);
        turnOffAirplaneMode();
        // wait long enough for airplane mode events to propagate.
        RunUtil.getDefault().sleep(1_200);

        // Verify that we have at least one atom for enablement and one for disablement.
        List<EventMetricData> data = ReportUtils.getEventMetricDataList(getDevice());
        AtomsProto.AirplaneMode airplaneModeEnabledAtom = null;
        AtomsProto.AirplaneMode airplaneModeDisabledAtom = null;
        for (EventMetricData d : data) {
            AtomsProto.AirplaneMode atom = d.getAtom().getAirplaneMode();
            if (atom.getIsEnabled() && airplaneModeEnabledAtom == null) {
                airplaneModeEnabledAtom = atom;
            }
            if (!atom.getIsEnabled() && airplaneModeDisabledAtom == null) {
                airplaneModeDisabledAtom = atom;
            }
        }
        assertThat(airplaneModeEnabledAtom).isNotNull();
        assertThat(airplaneModeDisabledAtom).isNotNull();
        assertThat(airplaneModeDisabledAtom.getShortToggle()).isFalse();
    }

    private boolean hasGsmPhone() throws Exception {
        // Not using log entries or ServiceState in the dump since they may or may not be present,
        // which can make the test flaky
        return getTelephonyDumpEntries("Phone").stream()
                .anyMatch(phone ->
                        String.format("%d", PHONE_TYPE_GSM).equals(phone.get("getPhoneType()")));
    }

    private boolean hasCdmaPhone() throws Exception {
        // Not using log entries or ServiceState in the dump due to the same reason as hasGsmPhone()
        return getTelephonyDumpEntries("Phone").stream()
                .anyMatch(phone ->
                        String.format("%d", PHONE_TYPE_CDMA).equals(phone.get("getPhoneType()"))
                                || String.format("%d", PHONE_TYPE_CDMA_LTE)
                                .equals(phone.get("getPhoneType()")));
    }

    private int getActiveSimSlotCount() throws Exception {
        List<Map<String, String>> slots = getTelephonyDumpEntries("UiccSlot");
        long count = slots.stream().filter(slot -> "true".equals(slot.get("mActive"))).count();
        return Math.toIntExact(count);
    }

    /**
     * Returns the upper bound of active SIM profile count.
     *
     * <p>The value is an upper bound as eSIMs without profiles are also counted in.
     */
    private int getActiveSimCountUpperBound() throws Exception {
        List<Map<String, String>> slots = getTelephonyDumpEntries("UiccSlot");
        long count = slots.stream().filter(slot ->
                "true".equals(slot.get("mActive"))
                        && slot.get("mCardState") != null
                        && slot.get("mCardState").contains("CARDSTATE_PRESENT")).count();
        return Math.toIntExact(count);
    }

    /**
     * Returns the upper bound of active eSIM profile count.
     *
     * <p>The value is an upper bound as eSIMs without profiles are also counted in.
     */
    private int getActiveEsimCountUpperBound() throws Exception {
        List<Map<String, String>> slots = getTelephonyDumpEntries("UiccSlot");
        long count = slots.stream().filter(slot ->
                "true".equals(slot.get("mActive"))
                        && slot.get("mCardState") != null
                        && slot.get("mCardState").contains("CARDSTATE_PRESENT")
                        && "true".equals(slot.get("mIsEuicc"))).count();
        return Math.toIntExact(count);
    }

    /**
     * Returns if the device as an eUICC with multiple enable profile support.
     */
    private boolean isEuiccSupportsMultipleEnabledProfiles() throws Exception {
        List<Map<String, String>> slots = getTelephonyDumpEntries("UiccSlot");
        return slots.stream().anyMatch(slot ->
                "true".equals(slot.get("isEuiccSupportsMultipleEnabledProfiles"))
                        && "true".equals(slot.get("mIsEuicc")));
    }

    private Queue<String> getTelephonyDumpEntries() throws Exception {
        String response = "";
        // Retry if dumpsys wasn't ready (where output is 1 or 2 lines)
        for (int i = 0; i < DUMPSYS_RETRIES; i++) {
            response = getDevice()
                    .executeShellCommand("dumpsys activity service TelephonyDebugService");
            String[] lines = response.split("[\\r\\n]+");
            if (lines.length >= DUMPSYS_MIN_NUM_LINES) {
                return new LinkedList<>(Arrays.asList(lines));
            }
            RunUtil.getDefault().sleep(DUMPSYS_RETRY_WAIT_MILLIS);
        }
        throw new RuntimeException(
                "dumpsys wasn't ready after "
                + (DUMPSYS_RETRIES * DUMPSYS_RETRY_WAIT_MILLIS)
                + " ms, response=" + response);
    }

    /**
     * Returns a list of fields and values for {@code className} from {@link TelephonyDebugService}
     * output.
     *
     * <p>Telephony dumpsys output does not support proto at the moment. This method provides
     * limited support for parsing its output. Specifically, it does not support arrays or
     * multi-line values.
     */
    private List<Map<String, String>> getTelephonyDumpEntries(String className) throws Exception {
        // Matches any line with indentation, except for lines with only spaces
        Pattern indentPattern = Pattern.compile("^(\\s*)[^ ].*$");
        // Matches pattern for class, e.g. "    Phone:"
        Pattern classNamePattern = Pattern.compile("^(\\s*)" + Pattern.quote(className) + ":.*$");
        // Matches pattern for key-value pairs, e.g. "     mPhoneId=1"
        Pattern keyValuePattern = Pattern.compile("^(\\s*)([a-zA-Z]+[a-zA-Z0-9_]*)\\=(.+)$");
        Queue<String> responseLines = getTelephonyDumpEntries();

        List<Map<String, String>> results = new ArrayList<>();
        while (responseLines.peek() != null) {
            Matcher matcher = classNamePattern.matcher(responseLines.poll());
            if (matcher.matches()) {
                final int classIndentLevel = matcher.group(1).length();
                final Map<String, String> instanceEntries = new HashMap<>();
                while (responseLines.peek() != null) {
                    // Skip blank lines
                    matcher = indentPattern.matcher(responseLines.peek());
                    if (responseLines.peek().length() == 0 || !matcher.matches()) {
                        responseLines.poll();
                        continue;
                    }
                    // Finish (without consuming the line) if already parsed past this instance
                    final int indentLevel = matcher.group(1).length();
                    if (indentLevel <= classIndentLevel) {
                        break;
                    }
                    // Parse key-value pair if it belongs to the instance directly
                    matcher = keyValuePattern.matcher(responseLines.poll());
                    if (indentLevel == classIndentLevel + 1 && matcher.matches()) {
                        instanceEntries.put(matcher.group(2), matcher.group(3));
                    }
                }
                results.add(instanceEntries);
            }
        }
        return results;
    }

    private int getCarrierIdTableVersion() throws Exception {
        Queue<String> responseLines = getTelephonyDumpEntries();
        for (String line : responseLines) {
            if (line.contains("carrier_list_version")) {
                String version = line.replaceFirst("^\\s*carrier_list_version:\\s*", "");
                try {
                    return Integer.parseInt(version);
                } catch (NumberFormatException e) {
                    return 0;
                }
            }
        }
        return 0;
    }

    private void turnOnAirplaneMode() throws Exception {
        getDevice().executeShellCommand("cmd connectivity airplane-mode enable");
    }

    private void turnOffAirplaneMode() throws Exception {
        getDevice().executeShellCommand("cmd connectivity airplane-mode disable");
    }

    private boolean restartModem() throws Exception {
        String response = getDevice().executeShellCommand("cmd phone restart-modem");
        return response.contains("true");
    }
}
