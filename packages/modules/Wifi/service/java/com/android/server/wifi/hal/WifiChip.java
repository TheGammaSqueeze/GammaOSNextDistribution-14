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

package com.android.server.wifi.hal;

import android.annotation.IntDef;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.content.Context;
import android.hardware.wifi.WifiStatusCode;
import android.net.wifi.CoexUnsafeChannel;
import android.net.wifi.OuiKeyedData;
import android.net.wifi.WifiAvailableChannel;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiScanner;
import android.util.Log;

import com.android.server.wifi.SarInfo;
import com.android.server.wifi.SsidTranslator;
import com.android.server.wifi.WifiNative;
import com.android.server.wifi.WlanWakeReasonAndCounts;
import com.android.server.wifi.util.GeneralUtil.Mutable;
import com.android.server.wifi.util.NativeUtil;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.List;
import java.util.function.Supplier;

/**
 * Wrapper around a WifiChip.
 * May be initialized using a HIDL or AIDL WifiChip.
 */
public class WifiChip {
    public static final String TAG = "WifiChip";
    private IWifiChip mWifiChip;

    /**
     * Interface concurrency types used in reporting device concurrency capabilities.
     */
    public static final int IFACE_CONCURRENCY_TYPE_STA = 0;
    public static final int IFACE_CONCURRENCY_TYPE_AP = 1;
    public static final int IFACE_CONCURRENCY_TYPE_AP_BRIDGED = 2;
    public static final int IFACE_CONCURRENCY_TYPE_P2P = 3;
    public static final int IFACE_CONCURRENCY_TYPE_NAN = 4;

    @IntDef(prefix = { "IFACE_CONCURRENCY_TYPE_" }, value = {
            IFACE_CONCURRENCY_TYPE_STA,
            IFACE_CONCURRENCY_TYPE_AP,
            IFACE_CONCURRENCY_TYPE_AP_BRIDGED,
            IFACE_CONCURRENCY_TYPE_P2P,
            IFACE_CONCURRENCY_TYPE_NAN,
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface IfaceConcurrencyType {}

    /**
     * Supported interface types.
     */
    public static final int IFACE_TYPE_STA = 0;
    public static final int IFACE_TYPE_AP = 1;
    public static final int IFACE_TYPE_P2P = 2;
    public static final int IFACE_TYPE_NAN = 3;

    @IntDef(prefix = { "IFACE_TYPE_" }, value = {
            IFACE_TYPE_STA,
            IFACE_TYPE_AP,
            IFACE_TYPE_P2P,
            IFACE_TYPE_NAN,
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface IfaceType {}

    /**
     * Antenna configurations.
     */
    public static final int WIFI_ANTENNA_MODE_UNSPECIFIED = 0;
    public static final int WIFI_ANTENNA_MODE_1X1 = 1;
    public static final int WIFI_ANTENNA_MODE_2X2 = 2;
    public static final int WIFI_ANTENNA_MODE_3X3 = 3;
    public static final int WIFI_ANTENNA_MODE_4X4 = 4;

    @IntDef(prefix = { "WIFI_ANTENNA_MODE_" }, value = {
            WIFI_ANTENNA_MODE_UNSPECIFIED,
            WIFI_ANTENNA_MODE_1X1,
            WIFI_ANTENNA_MODE_2X2,
            WIFI_ANTENNA_MODE_3X3,
            WIFI_ANTENNA_MODE_4X4,
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface WifiAntennaMode {}

    /**
     * Response containing a value and a status code.
     *
     * @param <T> Type of value that should be returned.
     */
    public static class Response<T> {
        private Mutable<T> mMutable;
        private int mStatusCode;

        public Response(T initialValue) {
            mMutable = new Mutable<>(initialValue);
            mStatusCode = WifiHal.WIFI_STATUS_ERROR_UNKNOWN;
        }

        public void setValue(T value) {
            mMutable.value = value;
        }

        public T getValue() {
            return mMutable.value;
        }

        public void setStatusCode(@WifiHal.WifiStatusCode int statusCode) {
            mStatusCode = statusCode;
        }

        public @WifiHal.WifiStatusCode int getStatusCode() {
            return mStatusCode;
        }
    }

    /**
     * Set of interface concurrency types, along with the maximum number of interfaces that can have
     * one of the specified concurrency types for a given ChipConcurrencyCombination. See
     * ChipConcurrencyCombination below for examples.
     */
    public static class ChipConcurrencyCombinationLimit {
        public final int maxIfaces;
        public final @IfaceConcurrencyType List<Integer> types;

        public ChipConcurrencyCombinationLimit(int inMaxIfaces,
                @IfaceConcurrencyType List<Integer> inTypes) {
            maxIfaces = inMaxIfaces;
            types = inTypes;
        }

        @Override
        public String toString() {
            return "{maxIfaces=" + maxIfaces + ", types=" + types + "}";
        }
    }

    /**
     * Set of interfaces that can operate concurrently when in a given mode.
     *
     * For example:
     *   [{STA} <= 2]
     *       At most two STA interfaces are supported
     *       [], [STA], [STA+STA]
     *
     *   [{STA} <= 1, {NAN} <= 1, {AP_BRIDGED} <= 1]
     *       Any combination of STA, NAN, AP_BRIDGED
     *       [], [STA], [NAN], [AP_BRIDGED], [STA+NAN], [STA+AP_BRIDGED], [NAN+AP_BRIDGED],
     *       [STA+NAN+AP_BRIDGED]
     *
     *   [{STA} <= 1, {NAN,P2P} <= 1]
     *       Optionally a STA and either NAN or P2P
     *       [], [STA], [STA+NAN], [STA+P2P], [NAN], [P2P]
     *       Not included [NAN+P2P], [STA+NAN+P2P]
     *
     *   [{STA} <= 1, {STA,NAN} <= 1]
     *       Optionally a STA and either a second STA or a NAN
     *       [], [STA], [STA+NAN], [STA+STA], [NAN]
     *       Not included [STA+STA+NAN]
     */
    public static class ChipConcurrencyCombination {
        public final List<ChipConcurrencyCombinationLimit> limits;

        public ChipConcurrencyCombination(List<ChipConcurrencyCombinationLimit> inLimits) {
            limits = inLimits;
        }

        @Override
        public String toString() {
            return "{limits=" + limits + "}";
        }
    }

    /**
     * A mode that the chip can be put in. A mode defines a set of constraints on
     * the interfaces that can exist while in that mode. Modes define a unit of
     * configuration where all interfaces must be torn down to switch to a
     * different mode. Some HALs may only have a single mode, but an example where
     * multiple modes would be required is if a chip has different firmwares with
     * different capabilities.
     *
     * When in a mode, it must be possible to perform any combination of creating
     * and removing interfaces as long as at least one of the
     * ChipConcurrencyCombinations is satisfied. This means that if a chip has two
     * available combinations, [{STA} <= 1] and [{AP_BRIDGED} <= 1] then it is expected
     * that exactly one STA type or one AP_BRIDGED type can be created, but it
     * is not expected that both a STA and AP_BRIDGED type  could be created. If it
     * was then there would be a single available combination
     * [{STA} <=1, {AP_BRIDGED} <= 1].
     *
     * When switching between two available combinations it is expected that
     * interfaces only supported by the initial combination must be removed until
     * the target combination is also satisfied. At that point new interfaces
     * satisfying only the target combination can be added (meaning the initial
     * combination limits will no longer satisfied). The addition of these new
     * interfaces must not impact the existence of interfaces that satisfy both
     * combinations.
     *
     * For example, a chip with available combinations:
     *     [{STA} <= 2, {NAN} <=1] and [{STA} <=1, {NAN} <= 1, {AP_BRIDGED} <= 1}]
     * If the chip currently has 3 interfaces STA, STA and NAN and wants to add an
     * AP_BRIDGED interface in place of one of the STAs, then one of the STA interfaces
     * must be removed first, and then the AP interface can be created after
     * the STA has been torn down. During this process the remaining STA and NAN
     * interfaces must not be removed/recreated.
     *
     * If a chip does not support this kind of reconfiguration in this mode then
     * the combinations must be separated into two separate modes. Before
     * switching modes, all interfaces must be torn down, the mode switch must be
     * enacted, and when it completes the new interfaces must be brought up.
     */
    public static class ChipMode {
        public final int id;
        public final List<ChipConcurrencyCombination> availableCombinations;

        public ChipMode(int inId, List<ChipConcurrencyCombination> inAvailableCombinations) {
            id = inId;
            availableCombinations = inAvailableCombinations;
        }

        @Override
        public String toString() {
            return "{id=" + id + ", availableCombinations=" + availableCombinations + "}";
        }
    }

    /**
     * Wifi radio configuration.
     */
    public static class WifiRadioConfiguration {
        public final @WifiScanner.WifiBand int bandInfo;
        public final @WifiAntennaMode int antennaMode;

        public WifiRadioConfiguration(int inBandInfo, int inAntennaMode) {
            bandInfo = inBandInfo;
            antennaMode = inAntennaMode;
        }

        @Override
        public String toString() {
            return "{bandInfo=" + bandInfo + ", antennaMode=" + antennaMode + "}";
        }
    }

    /**
     * Wifi radio combination.
     */
    public static class WifiRadioCombination {
        public final List<WifiRadioConfiguration> radioConfigurations;

        public WifiRadioCombination(List<WifiRadioConfiguration> inRadioConfigurations) {
            radioConfigurations = inRadioConfigurations;
        }

        @Override
        public String toString() {
            return "{radioConfigurations=" + radioConfigurations + "}";
        }
    }

    /**
     * AFC channel allowance.
     */
    public static class AfcChannelAllowance {
        /**
         * AFC max permissible information queried from AFC server based on frequency.
         */
        public List<AvailableAfcFrequencyInfo> availableAfcFrequencyInfos;
        /**
         * AFC max permissible information queried from AFC server based on channel number.
         */
        public List<AvailableAfcChannelInfo> availableAfcChannelInfos;
        /**
         * The time in UTC at which this information expires, as the difference, measured in
         * milliseconds, between the expiration time and midnight, January 1, 1970 UTC.
         */
        public long availabilityExpireTimeMs;
    }

    /**
     * Available AFC frequency info.
     */
    public static class AvailableAfcFrequencyInfo {
        public int startFrequencyMhz = 0;
        public int endFrequencyMhz = 0;
        public int maxPsdDbmPerMhz = 0;
    }

    /**
     * Available AFC channel info.
     */
    public static class AvailableAfcChannelInfo {
        public int globalOperatingClass = 0;
        public int channelCfi = 0;
        public int maxEirpDbm = 0;
    }

    /**
     * Wifi Chip capabilities.
     */
    public static class WifiChipCapabilities {
        /**
         * Maximum number of links supported by the chip for MLO association.
         *
         * Note: This is a static configuration of the chip.
         */
        public final int maxMloAssociationLinkCount;
        /**
         * Maximum number of STR links used in Multi-Link Operation. The maximum
         * number of STR links used for MLO can be different from the number of
         * radios supported by the chip.
         *
         * Note: This is a static configuration of the chip.
         */
        public final int maxMloStrLinkCount;
        /**
         * Maximum number of concurrent TDLS sessions that can be enabled
         * by framework via
         * {@link android.hardware.wifi.supplicant.ISupplicantStaIface#initiateTdlsSetup(byte[])}.
         */
        public final int maxConcurrentTdlsSessionCount;

        public WifiChipCapabilities(int maxMloAssociationLinkCount, int maxMloStrLinkCount,
                int maxConcurrentTdlsSessionCount) {
            this.maxMloAssociationLinkCount = maxMloAssociationLinkCount;
            this.maxMloStrLinkCount = maxMloStrLinkCount;
            this.maxConcurrentTdlsSessionCount = maxConcurrentTdlsSessionCount;
        }

        @Override
        public String toString() {
            return "{maxMloAssociationLinkCount=" + maxMloAssociationLinkCount
                    + ", maxMloStrLinkCount=" + maxMloStrLinkCount
                    + ", maxConcurrentTdlsSessionCount=" + maxConcurrentTdlsSessionCount + "}";
        }
    }

    /**
     * Information about the version of the driver and firmware running this chip.
     *
     * The information in these ASCII strings are vendor specific and does not
     * need to follow any particular format. It may be dumped as part of the bug
     * report.
     */
    public static class ChipDebugInfo {
        public final String driverDescription;
        public final String firmwareDescription;

        public ChipDebugInfo(String inDriverDescription, String inFirmwareDescription) {
            driverDescription = inDriverDescription;
            firmwareDescription = inFirmwareDescription;
        }
    }

    /**
     * State of an iface operating on the radio chain (hardware MAC) on the device.
     */
    public static class IfaceInfo {
        public final String name;
        public final int channel;

        public IfaceInfo(String inName, int inChannel) {
            name = inName;
            channel = inChannel;
        }
    }

    /**
     * State of a hardware radio chain (hardware MAC) on the device.
     */
    public static class RadioModeInfo {
        public final int radioId;
        public final @WifiScanner.WifiBand int bandInfo;
        public final List<IfaceInfo> ifaceInfos;

        public RadioModeInfo(int inRadioId, @WifiScanner.WifiBand int inBandInfo,
                List<IfaceInfo> inIfaceInfos) {
            radioId = inRadioId;
            bandInfo = inBandInfo;
            ifaceInfos = inIfaceInfos;
        }
    }

    /**
     * Framework callback object. Will get called when the equivalent events are received
     * from the HAL.
     */
    public interface Callback {
        /**
         * Indicates that a chip reconfiguration failed. This is a fatal
         * error and any iface objects available previously must be considered
         * invalid. The client can attempt to recover by trying to reconfigure the
         * chip again using {@link IWifiChip#configureChip(int)}.
         *
         * @param status Failure reason code.
         */
        void onChipReconfigureFailure(int status);

        /**
         * Indicates that the chip has been reconfigured successfully. At
         * this point, the interfaces available in the mode must be able to be
         * configured. When this is called, any previous iface objects must be
         * considered invalid.
         *
         * @param modeId The mode that the chip switched to, corresponding to the id
         *        property of the target ChipMode.
         */
        void onChipReconfigured(int modeId);

        /**
         * Indicates that the chip has encountered a fatal error.
         * Client must not attempt to parse either the errorCode or debugData.
         * Must only be captured in a bugreport.
         *
         * @param errorCode Vendor defined error code.
         * @param debugData Vendor defined data used for debugging.
         */
        void onDebugErrorAlert(int errorCode, byte[] debugData);

        /**
         * Reports debug ring buffer data.
         *
         * The ring buffer data collection is event based:
         * - Driver calls this callback when new records are available, the
         *   |WifiDebugRingBufferStatus| passed up to framework in the callback
         *   indicates to framework if more data is available in the ring buffer.
         *   It is not expected that driver will necessarily always empty the ring
         *   immediately as data is available. Instead the driver will report data
         *   every X seconds, or if N bytes are available, based on the parameters
         *   set via |startLoggingToDebugRingBuffer|.
         * - In the case where a bug report has to be captured, the framework will
         *   require driver to upload all data immediately. This is indicated to
         *   driver when framework calls |forceDumpToDebugRingBuffer|. The driver
         *   will start sending all available data in the indicated ring by repeatedly
         *   invoking this callback.
         *
         * @param status Status of the corresponding ring buffer. This should
         *         contain the name of the ring buffer on which the data is
         *         available.
         * @param data Raw bytes of data sent by the driver. Must be dumped
         *         out to a bugreport and post processed.
         */
        void onDebugRingBufferDataAvailable(WifiNative.RingBufferStatus status, byte[] data);

        /**
         * Indicates that a new iface has been added to the chip.
         *
         * @param type Type of iface added.
         * @param name Name of iface added.
         */
        void onIfaceAdded(@IfaceType int type, String name);

        /**
         * Indicates that an existing iface has been removed from the chip.
         *
         * @param type Type of iface removed.
         * @param name Name of iface removed.
         */
        void onIfaceRemoved(@IfaceType int type, String name);

        /**
         * Indicates a radio mode change.
         * Radio mode change could be a result of:
         * a) Bringing up concurrent interfaces (ex. STA + AP).
         * b) Change in operating band of one of the concurrent interfaces
         * (ex. STA connection moved from 2.4G to 5G)
         *
         * @param radioModeInfos List of RadioModeInfo structures for each
         *        radio chain (hardware MAC) on the device.
         */
        void onRadioModeChange(List<RadioModeInfo> radioModeInfos);
    }

    public WifiChip(@NonNull android.hardware.wifi.V1_0.IWifiChip chip,
            @NonNull Context context, @NonNull SsidTranslator ssidTranslator) {
        mWifiChip = createWifiChipHidlImplMockable(chip, context, ssidTranslator);
    }

    public WifiChip(@NonNull android.hardware.wifi.IWifiChip chip,
            @NonNull Context context, @NonNull SsidTranslator ssidTranslator) {
        mWifiChip = createWifiChipAidlImplMockable(chip, context, ssidTranslator);
    }

    protected WifiChipHidlImpl createWifiChipHidlImplMockable(
            @NonNull android.hardware.wifi.V1_0.IWifiChip chip,
            @NonNull Context context, @NonNull SsidTranslator ssidTranslator) {
        return new WifiChipHidlImpl(chip, context, ssidTranslator);
    }

    protected WifiChipAidlImpl createWifiChipAidlImplMockable(
            @NonNull android.hardware.wifi.IWifiChip chip,
            @NonNull Context context, @NonNull SsidTranslator ssidTranslator) {
        return new WifiChipAidlImpl(chip, context, ssidTranslator);
    }

    private <T> T validateAndCall(String methodStr, T defaultVal, @NonNull Supplier<T> supplier) {
        if (mWifiChip == null) {
            Log.wtf(TAG, "Cannot call " + methodStr + " because mWifiChip is null");
            return defaultVal;
        }
        return supplier.get();
    }

    /**
     * See comments for {@link IWifiChip#configureChip(int)}
     */
    public boolean configureChip(int modeId) {
        return validateAndCall("configureChip", false,
                () -> mWifiChip.configureChip(modeId));
    }

    /**
     * See comments for {@link IWifiChip#createApIface(List)}
     */
    @Nullable
    public WifiApIface createApIface(@NonNull List<OuiKeyedData> vendorData) {
        if (vendorData == null) {
            Log.e(TAG, "createApIface received null vendorData");
            return null;
        }
        return validateAndCall("createApIface", null,
                () -> mWifiChip.createApIface(vendorData));
    }

    /**
     * See comments for {@link IWifiChip#createBridgedApIface(List)}
     */
    @Nullable
    public WifiApIface createBridgedApIface(@NonNull List<OuiKeyedData> vendorData) {
        if (vendorData == null) {
            Log.e(TAG, "createBridgedApIface received null vendorData");
            return null;
        }
        return validateAndCall("createBridgedApIface", null,
                () -> mWifiChip.createBridgedApIface(vendorData));
    }

    /**
     * See comments for {@link IWifiChip#createNanIface()}
     */
    @Nullable
    public WifiNanIface createNanIface() {
        return validateAndCall("createNanIface", null,
                () -> mWifiChip.createNanIface());
    }

    /**
     * See comments for {@link IWifiChip#createP2pIface()}
     */
    @Nullable
    public WifiP2pIface createP2pIface() {
        return validateAndCall("createP2pIface", null,
                () -> mWifiChip.createP2pIface());
    }

    /**
     * See comments for {@link IWifiChip#createRttController()}
     */
    @Nullable
    public WifiRttController createRttController() {
        return validateAndCall("createRttController", null,
                () -> mWifiChip.createRttController());
    }

    /**
     * See comments for {@link IWifiChip#createStaIface()}
     */
    @Nullable
    public WifiStaIface createStaIface() {
        return validateAndCall("createStaIface", null,
                () -> mWifiChip.createStaIface());
    }

    /**
     * See comments for {@link IWifiChip#enableDebugErrorAlerts(boolean)}
     */
    public boolean enableDebugErrorAlerts(boolean enable) {
        return validateAndCall("enableDebugErrorAlerts", false,
                () -> mWifiChip.enableDebugErrorAlerts(enable));
    }

    /**
     * See comments for {@link IWifiChip#flushRingBufferToFile()}
     */
    public boolean flushRingBufferToFile() {
        return validateAndCall("flushRingBufferToFile", false,
                () -> mWifiChip.flushRingBufferToFile());
    }

    /**
     * See comments for {@link IWifiChip#forceDumpToDebugRingBuffer(String)}
     */
    public boolean forceDumpToDebugRingBuffer(String ringName) {
        return validateAndCall("forceDumpToDebugRingBuffer", false,
                () -> mWifiChip.forceDumpToDebugRingBuffer(ringName));
    }

    /**
     * See comments for {@link IWifiChip#getApIface(String)}
     */
    @Nullable
    public WifiApIface getApIface(String ifaceName) {
        return validateAndCall("getApIface", null,
                () -> mWifiChip.getApIface(ifaceName));
    }

    /**
     * See comments for {@link IWifiChip#getApIfaceNames()}
     */
    @Nullable
    public List<String> getApIfaceNames() {
        return validateAndCall("getApIfaceNames", null,
                () -> mWifiChip.getApIfaceNames());
    }

    /**
     * See comments for {@link IWifiChip#getAvailableModes()}
     */
    @Nullable
    public List<WifiChip.ChipMode> getAvailableModes() {
        return validateAndCall("getAvailableModes", null,
                () -> mWifiChip.getAvailableModes());
    }

    /**
     * See comments for {@link IWifiChip#getCapabilitiesBeforeIfacesExist()}
     */
    public Response<Long> getCapabilitiesBeforeIfacesExist() {
        return validateAndCall("getCapabilitiesBeforeIfacesExist", new Response<>(0L),
                () -> mWifiChip.getCapabilitiesBeforeIfacesExist());
    }

    /**
     * See comments for {@link IWifiChip#getCapabilitiesAfterIfacesExist()}
     */
    public Response<Long> getCapabilitiesAfterIfacesExist() {
        return validateAndCall("getCapabilitiesAfterIfacesExist", new Response<>(0L),
                () -> mWifiChip.getCapabilitiesAfterIfacesExist());
    }

    /**
     * See comments for {@link IWifiChip#getDebugHostWakeReasonStats()}
     */
    @Nullable
    public WlanWakeReasonAndCounts getDebugHostWakeReasonStats() {
        return validateAndCall("getDebugHostWakeReasonStats", null,
                () -> mWifiChip.getDebugHostWakeReasonStats());
    }

    /**
     * See comments for {@link IWifiChip#getDebugRingBuffersStatus()}
     */
    @Nullable
    public List<WifiNative.RingBufferStatus> getDebugRingBuffersStatus() {
        return validateAndCall("getDebugRingBuffersStatus", null,
                () -> mWifiChip.getDebugRingBuffersStatus());
    }

    /**
     * See comments for {@link IWifiChip#getId()}
     */
    public int getId() {
        return validateAndCall("getId", -1, () -> mWifiChip.getId());
    }

    /**
     * See comments for {@link IWifiChip#getMode()}
     */
    public Response<Integer> getMode() {
        return validateAndCall("getMode", new Response<>(0), () -> mWifiChip.getMode());
    }

    /**
     * See comments for {@link IWifiChip#getNanIface(String)}
     */
    @Nullable
    public WifiNanIface getNanIface(String ifaceName) {
        return validateAndCall("getNanIface", null,
                () -> mWifiChip.getNanIface(ifaceName));
    }

    /**
     * See comments for {@link IWifiChip#getNanIfaceNames()}
     */
    @Nullable
    public List<String> getNanIfaceNames() {
        return validateAndCall("getNanIfaceNames", null,
                () -> mWifiChip.getNanIfaceNames());
    }

    /**
     * See comments for {@link IWifiChip#getP2pIface(String)}
     */
    @Nullable
    public WifiP2pIface getP2pIface(String ifaceName) {
        return validateAndCall("getP2pIface", null,
                () -> mWifiChip.getP2pIface(ifaceName));
    }

    /**
     * See comments for {@link IWifiChip#getP2pIfaceNames()}
     */
    @Nullable
    public List<String> getP2pIfaceNames() {
        return validateAndCall("getP2pIfaceNames", null,
                () -> mWifiChip.getP2pIfaceNames());
    }

    /**
     * See comments for {@link IWifiChip#getStaIface(String)}
     */
    @Nullable
    public WifiStaIface getStaIface(String ifaceName) {
        return validateAndCall("getStaIface", null,
                () -> mWifiChip.getStaIface(ifaceName));
    }

    /**
     * See comments for {@link IWifiChip#getStaIfaceNames()}
     */
    @Nullable
    public List<String> getStaIfaceNames() {
        return validateAndCall("getStaIfaceNames", null,
                () -> mWifiChip.getStaIfaceNames());
    }

    /**
     * See comments for {@link IWifiChip#getSupportedRadioCombinations()}
     */
    @Nullable
    public List<WifiChip.WifiRadioCombination> getSupportedRadioCombinations() {
        return validateAndCall("getSupportedRadioCombinations", null,
                () -> mWifiChip.getSupportedRadioCombinations());
    }

    /**
     * See comments for {@link IWifiChip#getWifiChipCapabilities()}
     */
    @Nullable
    public WifiChipCapabilities getWifiChipCapabilities() {
        return validateAndCall("getWifiChipCapabilities", null,
                () -> mWifiChip.getWifiChipCapabilities());
    }

    /**
     * See comments for {@link IWifiChip#getUsableChannels(int, int, int)}
     */
    @Nullable
    public List<WifiAvailableChannel> getUsableChannels(@WifiScanner.WifiBand int band,
            @WifiAvailableChannel.OpMode int mode, @WifiAvailableChannel.Filter int filter) {
        return validateAndCall("getUsableChannels", null,
                () -> mWifiChip.getUsableChannels(band, mode, filter));
    }

    /**
     * See comments for {@link IWifiChip#registerCallback(Callback)}
     */
    public boolean registerCallback(WifiChip.Callback callback) {
        return validateAndCall("registerCallback", false,
                () -> mWifiChip.registerCallback(callback));
    }

    /**
     * See comments for {@link IWifiChip#removeApIface(String)}
     */
    public boolean removeApIface(String ifaceName) {
        return validateAndCall("removeApIface", false,
                () -> mWifiChip.removeApIface(ifaceName));
    }

    /**
     * See comments for {@link IWifiChip#removeIfaceInstanceFromBridgedApIface(String, String)}
     */
    public boolean removeIfaceInstanceFromBridgedApIface(String brIfaceName, String ifaceName) {
        return validateAndCall("removeIfaceInstanceFromBridgedApIface", false,
                () -> mWifiChip.removeIfaceInstanceFromBridgedApIface(brIfaceName, ifaceName));
    }

    /**
     * See comments for {@link IWifiChip#removeNanIface(String)}
     */
    public boolean removeNanIface(String ifaceName) {
        return validateAndCall("removeNanIface", false,
                () -> mWifiChip.removeNanIface(ifaceName));
    }

    /**
     * See comments for {@link IWifiChip#removeP2pIface(String)}
     */
    public boolean removeP2pIface(String ifaceName) {
        return validateAndCall("removeP2pIface", false,
                () -> mWifiChip.removeP2pIface(ifaceName));
    }

    /**
     * See comments for {@link IWifiChip#removeStaIface(String)}
     */
    public boolean removeStaIface(String ifaceName) {
        return validateAndCall("removeStaIface", false,
                () -> mWifiChip.removeStaIface(ifaceName));
    }

    /**
     * See comments for {@link IWifiChip#requestChipDebugInfo()}
     */
    @Nullable
    public WifiChip.ChipDebugInfo requestChipDebugInfo() {
        return validateAndCall("requestChipDebugInfo", null,
                () -> mWifiChip.requestChipDebugInfo());
    }

    /**
     * See comments for {@link IWifiChip#requestDriverDebugDump()}
     */
    @Nullable
    public byte[] requestDriverDebugDump() {
        return validateAndCall("requestDriverDebugDump", null,
                () -> mWifiChip.requestDriverDebugDump());
    }

    /**
     * See comments for {@link IWifiChip#requestFirmwareDebugDump()}
     */
    @Nullable
    public byte[] requestFirmwareDebugDump() {
        return validateAndCall("requestFirmwareDebugDump", null,
                () -> mWifiChip.requestFirmwareDebugDump());
    }

    /**
     * See comments for {@link IWifiChip#selectTxPowerScenario(SarInfo)}
     */
    public boolean selectTxPowerScenario(SarInfo sarInfo) {
        return validateAndCall("selectTxPowerScenario", false,
                () -> mWifiChip.selectTxPowerScenario(sarInfo));
    }

    /**
     * See comments for {@link IWifiChip#setCoexUnsafeChannels(List, int)}
     */
    public boolean setCoexUnsafeChannels(List<CoexUnsafeChannel> unsafeChannels, int restrictions) {
        return validateAndCall("setCoexUnsafeChannels", false,
                () -> mWifiChip.setCoexUnsafeChannels(unsafeChannels, restrictions));
    }

    /**
     * See comments for {@link IWifiChip#setCountryCode(byte[])}
     */
    public boolean setCountryCode(String countryCode) {
        if (countryCode == null || countryCode.length() != 2) {
            Log.e(TAG, "Invalid country code " + countryCode);
            return false;
        }
        try {
            final byte[] code = NativeUtil.stringToByteArray(countryCode);
            return validateAndCall("setCountryCode", false,
                    () -> mWifiChip.setCountryCode(code));
        } catch (IllegalArgumentException e) {
            Log.e(TAG, "Invalid country code " + countryCode + ", error: " + e);
            return false;
        }
    }

    /**
     * See comments for {@link IWifiChip#setLowLatencyMode(boolean)}
     */
    public boolean setLowLatencyMode(boolean enable) {
        return validateAndCall("setLowLatencyMode", false,
                () -> mWifiChip.setLowLatencyMode(enable));
    }

    /**
     * See comments for {@link IWifiChip#setMultiStaPrimaryConnection(String)}
     */
    public boolean setMultiStaPrimaryConnection(String ifaceName) {
        return validateAndCall("setMultiStaPrimaryConnection", false,
                () -> mWifiChip.setMultiStaPrimaryConnection(ifaceName));
    }

    /**
     * See comments for {@link IWifiChip#setMultiStaUseCase(int)}
     */
    public boolean setMultiStaUseCase(@WifiNative.MultiStaUseCase int useCase) {
        return validateAndCall("setMultiStaUseCase", false,
                () -> mWifiChip.setMultiStaUseCase(useCase));
    }

    /**
     * See comments for {@link IWifiChip#startLoggingToDebugRingBuffer(String, int, int, int)}
     */
    public boolean startLoggingToDebugRingBuffer(String ringName, int verboseLevel,
            int maxIntervalInSec, int minDataSizeInBytes) {
        return validateAndCall("startLoggingToDebugRingBuffer", false,
                () -> mWifiChip.startLoggingToDebugRingBuffer(ringName, verboseLevel,
                        maxIntervalInSec, minDataSizeInBytes));
    }

    /**
     * See comments for {@link IWifiChip#stopLoggingToDebugRingBuffer()}
     */
    public boolean stopLoggingToDebugRingBuffer() {
        return validateAndCall("stopLoggingToDebugRingBuffer", false,
                () -> mWifiChip.stopLoggingToDebugRingBuffer());
    }

    /**
     * See comments for {@link IWifiChip#triggerSubsystemRestart()}
     */
    public boolean triggerSubsystemRestart() {
        return validateAndCall("triggerSubsystemRestart", false,
                () -> mWifiChip.triggerSubsystemRestart());
    }

    /**
     * See comments for {@link IWifiChip#setMloMode(int)}.
     */
    public @WifiStatusCode int setMloMode(@WifiManager.MloMode int mode) {
        return validateAndCall("setMloMode", WifiStatusCode.ERROR_NOT_STARTED,
                () -> mWifiChip.setMloMode(mode));
    }

    /**
     * See comments for {@link IWifiChip#enableStaChannelForPeerNetwork(boolean, boolean)}
     */
    public boolean enableStaChannelForPeerNetwork(boolean enableIndoorChannel,
            boolean enableDfsChannel) {
        return validateAndCall("enableStaChannelForPeerNetwork", false,
                () -> mWifiChip.enableStaChannelForPeerNetwork(enableIndoorChannel,
                        enableDfsChannel));
    }

    /**
     * See comments for {@link IWifiChip#setAfcChannelAllowance(AfcChannelAllowance)}
     */
    public boolean setAfcChannelAllowance(AfcChannelAllowance afcChannelAllowance) {
        if (afcChannelAllowance == null) return false;
        return validateAndCall("setAfcChannelAllowance", false,
                () -> mWifiChip.setAfcChannelAllowance(afcChannelAllowance));
    }
}
