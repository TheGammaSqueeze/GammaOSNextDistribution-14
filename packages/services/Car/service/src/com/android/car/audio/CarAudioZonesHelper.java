/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.car.audio;

import static android.car.media.CarAudioManager.PRIMARY_AUDIO_ZONE;
import static android.media.AudioDeviceInfo.TYPE_AUX_LINE;
import static android.media.AudioDeviceInfo.TYPE_BLE_BROADCAST;
import static android.media.AudioDeviceInfo.TYPE_BLE_HEADSET;
import static android.media.AudioDeviceInfo.TYPE_BLE_SPEAKER;
import static android.media.AudioDeviceInfo.TYPE_BLUETOOTH_A2DP;
import static android.media.AudioDeviceInfo.TYPE_BUILTIN_SPEAKER;
import static android.media.AudioDeviceInfo.TYPE_BUS;
import static android.media.AudioDeviceInfo.TYPE_HDMI;
import static android.media.AudioDeviceInfo.TYPE_USB_ACCESSORY;
import static android.media.AudioDeviceInfo.TYPE_USB_DEVICE;
import static android.media.AudioDeviceInfo.TYPE_USB_HEADSET;
import static android.media.AudioDeviceInfo.TYPE_WIRED_HEADPHONES;
import static android.media.AudioDeviceInfo.TYPE_WIRED_HEADSET;

import static com.android.car.audio.CarAudioService.CAR_DEFAULT_AUDIO_ATTRIBUTE;
import static com.android.car.audio.CarAudioService.TAG;
import static com.android.car.audio.CarAudioUtils.isMicrophoneInputDevice;

import static java.util.Locale.ROOT;

import android.annotation.NonNull;
import android.car.builtin.util.Slogf;
import android.car.feature.Flags;
import android.car.oem.CarAudioFadeConfiguration;
import android.media.AudioAttributes;
import android.media.AudioDeviceAttributes;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.media.audiopolicy.AudioProductStrategy;
import android.text.TextUtils;
import android.util.ArrayMap;
import android.util.ArraySet;
import android.util.SparseArray;
import android.util.SparseIntArray;
import android.util.Xml;

import com.android.car.audio.CarAudioContext.AudioContext;
import com.android.car.internal.util.ConstantDebugUtils;
import com.android.car.internal.util.DebugUtils;
import com.android.car.internal.util.LocalLog;
import com.android.internal.util.Preconditions;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.MissingResourceException;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * A helper class loads all audio zones from the configuration XML file.
 */
/* package */ final class CarAudioZonesHelper {
    private static final String NAMESPACE = null;
    private static final String TAG_ROOT = "carAudioConfiguration";

    private static final String TAG_OEM_CONTEXTS = "oemContexts";
    private static final String TAG_OEM_CONTEXT = "oemContext";
    private static final String OEM_CONTEXT_NAME = "name";

    private static final String TAG_AUDIO_ZONES = "zones";
    private static final String TAG_AUDIO_ZONE = "zone";
    private static final String TAG_AUDIO_ZONE_CONFIGS = "zoneConfigs";
    private static final String TAG_AUDIO_ZONE_CONFIG = "zoneConfig";
    private static final String TAG_VOLUME_GROUPS = "volumeGroups";
    private static final String VOLUME_GROUP_NAME = "name";
    private static final String TAG_VOLUME_GROUP = "group";
    private static final String TAG_AUDIO_DEVICE = "device";
    private static final String TAG_CONTEXT = "context";
    private static final String ATTR_VERSION = "version";
    private static final String ATTR_IS_PRIMARY = "isPrimary";
    private static final String ATTR_IS_CONFIG_DEFAULT = "isDefault";
    private static final String ATTR_ZONE_NAME = "name";
    private static final String ATTR_CONFIG_NAME = "name";
    private static final String ATTR_DEVICE_ADDRESS = "address";
    private static final String ATTR_DEVICE_TYPE = "type";
    private static final String ATTR_CONTEXT_NAME = "context";
    private static final String ATTR_ZONE_ID = "audioZoneId";
    private static final String ATTR_OCCUPANT_ZONE_ID = "occupantZoneId";
    private static final String TAG_INPUT_DEVICES = "inputDevices";
    private static final String TAG_INPUT_DEVICE = "inputDevice";
    private static final String TAG_MIRRORING_DEVICES = "mirroringDevices";
    private static final String TAG_MIRRORING_DEVICE = "mirroringDevice";
    private static final String ATTR_MAX_ACTIVATION_VOLUME_PERCENTAGE =
            "maxActivationVolumePercentage";
    private static final String ATTR_MIN_ACTIVATION_VOLUME_PERCENTAGE =
            "minActivationVolumePercentage";
    private static final String TAG_APPLY_FADE_CONFIGS = "applyFadeConfigs";
    private static final String FADE_CONFIG = "fadeConfig";
    private static final String FADE_CONFIG_NAME = "name";
    private static final String FADE_CONFIG_IS_DEFAULT = "isDefault";
    private static final int INVALID_VERSION = -1;
    private static final int SUPPORTED_VERSION_1 = 1;
    private static final int SUPPORTED_VERSION_2 = 2;
    private static final int SUPPORTED_VERSION_3 = 3;
    private static final int SUPPORTED_VERSION_4 = 4;
    private static final SparseIntArray SUPPORTED_VERSIONS;

    static {
        SUPPORTED_VERSIONS = new SparseIntArray(4);
        SUPPORTED_VERSIONS.put(SUPPORTED_VERSION_1, SUPPORTED_VERSION_1);
        SUPPORTED_VERSIONS.put(SUPPORTED_VERSION_2, SUPPORTED_VERSION_2);
        SUPPORTED_VERSIONS.put(SUPPORTED_VERSION_3, SUPPORTED_VERSION_3);
        SUPPORTED_VERSIONS.put(SUPPORTED_VERSION_4, SUPPORTED_VERSION_4);
    }

    private static final int ACTIVATION_VOLUME_PERCENTAGE_MIN = 0;
    private static final int ACTIVATION_VOLUME_PERCENTAGE_MAX = 100;

    private final AudioManager mAudioManager;
    private final CarAudioSettings mCarAudioSettings;
    private final List<CarAudioContextInfo> mCarAudioContextInfos = new ArrayList<>();
    private final Map<String, CarAudioDeviceInfo> mAddressToCarAudioDeviceInfo;
    private final Map<String, AudioDeviceInfo> mAddressToInputAudioDeviceInfoForAllInputDevices;
    private final InputStream mInputStream;
    private final SparseIntArray mZoneIdToOccupantZoneIdMapping;
    private final Set<Integer> mAudioZoneIds;
    private final Set<String> mAssignedInputAudioDevices;
    private final Set<String> mAudioZoneConfigNames;
    private final boolean mUseCarVolumeGroupMute;
    private final boolean mUseCoreAudioVolume;
    private final boolean mUseCoreAudioRouting;
    private final boolean mUseFadeManagerConfiguration;
    private final CarAudioFadeConfigurationHelper mCarAudioFadeConfigurationHelper;
    private final List<CarAudioDeviceInfo> mMirroringDevices = new ArrayList<>();

    private final ArrayMap<String, Integer> mContextNameToId = new ArrayMap<>();
    private final LocalLog mCarServiceLocalLog;
    private CarAudioContext mCarAudioContext;
    private int mNextSecondaryZoneId;
    private int mCurrentVersion;

    /**
     * <p><b>Note: <b/> CarAudioZonesHelper is expected to be used from a single thread. This
     * should be the same thread that originally called new CarAudioZonesHelper.
     */
    CarAudioZonesHelper(AudioManager audioManager, CarAudioSettings carAudioSettings,
            InputStream inputStream, List<CarAudioDeviceInfo> carAudioDeviceInfos,
            AudioDeviceInfo[] inputDeviceInfo, LocalLog serviceLog, boolean useCarVolumeGroupMute,
            boolean useCoreAudioVolume, boolean useCoreAudioRouting,
            boolean useFadeManagerConfiguration,
            CarAudioFadeConfigurationHelper carAudioFadeConfigurationHelper) {
        mAudioManager = Objects.requireNonNull(audioManager,
                "Audio manager cannot be null");
        mCarAudioSettings = Objects.requireNonNull(carAudioSettings);
        mInputStream = Objects.requireNonNull(inputStream);
        Objects.requireNonNull(carAudioDeviceInfos);
        Objects.requireNonNull(inputDeviceInfo);
        mAddressToCarAudioDeviceInfo = CarAudioZonesHelper.generateAddressToInfoMap(
                carAudioDeviceInfos);
        mCarServiceLocalLog = Objects.requireNonNull(serviceLog,
                "Car audio service local log cannot be null");
        mAddressToInputAudioDeviceInfoForAllInputDevices =
                CarAudioZonesHelper.generateAddressToInputAudioDeviceInfoMap(inputDeviceInfo);
        mNextSecondaryZoneId = PRIMARY_AUDIO_ZONE + 1;
        mZoneIdToOccupantZoneIdMapping = new SparseIntArray();
        mAudioZoneIds = new ArraySet<>();
        mAssignedInputAudioDevices = new ArraySet<>();
        mAudioZoneConfigNames = new ArraySet<>();
        mUseCarVolumeGroupMute = useCarVolumeGroupMute;
        mUseCoreAudioVolume = useCoreAudioVolume;
        mUseCoreAudioRouting = useCoreAudioRouting;
        mUseFadeManagerConfiguration = useFadeManagerConfiguration;
        mCarAudioFadeConfigurationHelper = carAudioFadeConfigurationHelper;
    }

    SparseIntArray getCarAudioZoneIdToOccupantZoneIdMapping() {
        return mZoneIdToOccupantZoneIdMapping;
    }

    SparseArray<CarAudioZone> loadAudioZones() throws IOException, XmlPullParserException {
        return parseCarAudioZones(mInputStream);
    }

    private static Map<String, CarAudioDeviceInfo> generateAddressToInfoMap(
            List<CarAudioDeviceInfo> carAudioDeviceInfos) {
        return carAudioDeviceInfos.stream()
                .filter(info -> !TextUtils.isEmpty(info.getAddress()))
                .collect(Collectors.toMap(CarAudioDeviceInfo::getAddress, info -> info));
    }

    private static Map<String, AudioDeviceInfo> generateAddressToInputAudioDeviceInfoMap(
            @NonNull AudioDeviceInfo[] inputAudioDeviceInfos) {
        Map<String, AudioDeviceInfo> deviceAddressToInputDeviceMap =
                new ArrayMap<>(inputAudioDeviceInfos.length);
        for (int i = 0; i < inputAudioDeviceInfos.length; ++i) {
            AudioDeviceInfo device = inputAudioDeviceInfos[i];
            if (device.isSource()) {
                deviceAddressToInputDeviceMap.put(device.getAddress(), device);
            }
        }
        return deviceAddressToInputDeviceMap;
    }

    private SparseArray<CarAudioZone> parseCarAudioZones(InputStream stream)
            throws XmlPullParserException, IOException {
        XmlPullParser parser = Xml.newPullParser();
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, NAMESPACE != null);
        parser.setInput(stream, null);

        // Ensure <carAudioConfiguration> is the root
        parser.nextTag();
        parser.require(XmlPullParser.START_TAG, NAMESPACE, TAG_ROOT);

        // Version check
        final int versionNumber = Integer.parseInt(
                parser.getAttributeValue(NAMESPACE, ATTR_VERSION));

        if (SUPPORTED_VERSIONS.get(versionNumber, INVALID_VERSION) == INVALID_VERSION) {
            throw new IllegalArgumentException("Latest Supported version:"
                    + SUPPORTED_VERSION_4 + " , got version:" + versionNumber);
        }

        mCurrentVersion = versionNumber;
        // Get all zones configured under <zones> tag
        while (parser.next() != XmlPullParser.END_TAG) {
            if (parser.getEventType() != XmlPullParser.START_TAG) continue;
            if (Objects.equals(parser.getName(), TAG_OEM_CONTEXTS)) {
                parseCarAudioContexts(parser);
            } else if (Objects.equals(parser.getName(), TAG_MIRRORING_DEVICES)) {
                parseMirroringDevices(parser);
            } else if (Objects.equals(parser.getName(), TAG_AUDIO_ZONES)) {
                loadCarAudioContexts();
                return parseAudioZones(parser);
            } else {
                CarAudioParserUtils.skip(parser);
            }
        }
        throw new MissingResourceException(TAG_AUDIO_ZONES + " is missing from configuration",
                "", TAG_AUDIO_ZONES);
    }

    private void parseMirroringDevices(XmlPullParser parser)
            throws XmlPullParserException, IOException {
        if (isVersionLessThanThree()) {
            throw new IllegalStateException(
                    TAG_MIRRORING_DEVICES + " are not supported in car_audio_configuration.xml"
                            + " version " + mCurrentVersion + ". Must be at least version "
                            + SUPPORTED_VERSION_3);
        }

        while (parser.next() != XmlPullParser.END_TAG) {
            if (parser.getEventType() != XmlPullParser.START_TAG) {
                continue;
            }
            if (Objects.equals(parser.getName(), TAG_MIRRORING_DEVICE)) {
                parseMirroringDevice(parser);
            }
            CarAudioParserUtils.skip(parser);
        }
    }

    private void parseMirroringDevice(XmlPullParser parser) {
        String address = parser.getAttributeValue(NAMESPACE, ATTR_DEVICE_ADDRESS);
        validateOutputAudioDevice(address, TYPE_BUS);
        CarAudioDeviceInfo info = getCarAudioDeviceInfo(address, TYPE_BUS);
        if (mMirroringDevices.contains(info)) {
            throw new IllegalArgumentException(TAG_MIRRORING_DEVICE + " " + address
                    + " repeats, " + TAG_MIRRORING_DEVICES + " can not repeat.");
        }
        mMirroringDevices.add(info);
    }

    private void loadCarAudioContexts() {
        if (isVersionLessThanThree() || mCarAudioContextInfos.isEmpty()) {
            mCarAudioContextInfos.addAll(CarAudioContext.getAllContextsInfo());
        }
        for (int index = 0; index < mCarAudioContextInfos.size(); index++) {
            CarAudioContextInfo info = mCarAudioContextInfos.get(index);
            mContextNameToId.put(info.getName().toLowerCase(ROOT), info.getId());
        }
        mCarAudioContext = new CarAudioContext(mCarAudioContextInfos, mUseCoreAudioRouting);
    }

    private void parseCarAudioContexts(XmlPullParser parser)
            throws XmlPullParserException, IOException {
        int contextId = CarAudioContext.getInvalidContext() + 1;

        while (parser.next() != XmlPullParser.END_TAG) {
            if (parser.getEventType() != XmlPullParser.START_TAG) {
                continue;
            }
            if (Objects.equals(parser.getName(), TAG_OEM_CONTEXT)) {
                parseCarAudioContext(parser, contextId);
                contextId++;
            } else {
                CarAudioParserUtils.skip(parser);
            }
        }
    }

    private void parseCarAudioContext(XmlPullParser parser, int contextId)
            throws XmlPullParserException, IOException {
        String contextName = parser.getAttributeValue(NAMESPACE, OEM_CONTEXT_NAME);
        CarAudioContextInfo context = null;
        while (parser.next() != XmlPullParser.END_TAG) {
            if (parser.getEventType() != XmlPullParser.START_TAG) {
                continue;
            }
            if (Objects.equals(parser.getName(), CarAudioParserUtils.TAG_AUDIO_ATTRIBUTES)) {
                List<AudioAttributes> attributes =
                        CarAudioParserUtils.parseAudioAttributes(parser, contextName);
                if (mUseCoreAudioRouting) {
                    contextId = CoreAudioHelper.getStrategyForContextName(contextName);
                    if (contextId == CoreAudioHelper.INVALID_STRATEGY) {
                        throw new IllegalArgumentException(CarAudioParserUtils.TAG_AUDIO_ATTRIBUTES
                                + ": Cannot find strategy id for context: "
                                + contextName + " and attributes \"" + attributes.get(0) + "\" .");
                    }
                }
                validateCarAudioContextAttributes(contextId, attributes, contextName);
                context = new CarAudioContextInfo(attributes.toArray(new AudioAttributes[0]),
                        contextName, contextId);
                mCarAudioContextInfos.add(context);
            } else {
                CarAudioParserUtils.skip(parser);
            }
        }
    }

    private void validateCarAudioContextAttributes(int contextId, List<AudioAttributes> attributes,
            String contextName) {
        if (!mUseCoreAudioRouting) {
            return;
        }
        AudioProductStrategy strategy = CoreAudioHelper.getStrategy(contextId);
        Preconditions.checkNotNull(strategy, "No strategy for context id = %d", contextId);
        for (int index = 0; index < attributes.size(); index++) {
            AudioAttributes aa = attributes.get(index);
            if (!strategy.supportsAudioAttributes(aa)
                    && !CoreAudioHelper.isDefaultStrategy(strategy.getId())) {
                throw new IllegalArgumentException("Invalid attributes " + aa + " for context: "
                        + contextName);
            }
        }
    }

    private SparseArray<CarAudioZone> parseAudioZones(XmlPullParser parser)
            throws XmlPullParserException, IOException {
        SparseArray<CarAudioZone> carAudioZones = new SparseArray<>();

        while (parser.next() != XmlPullParser.END_TAG) {
            if (parser.getEventType() != XmlPullParser.START_TAG) continue;
            if (Objects.equals(parser.getName(), TAG_AUDIO_ZONE)) {
                CarAudioZone zone = parseAudioZone(parser);
                verifyOnlyOnePrimaryZone(zone, carAudioZones);
                carAudioZones.put(zone.getId(), zone);
            } else {
                CarAudioParserUtils.skip(parser);
            }
        }

        verifyPrimaryZonePresent(carAudioZones);
        addRemainingMicrophonesToPrimaryZone(carAudioZones);
        return carAudioZones;
    }

    private void addRemainingMicrophonesToPrimaryZone(SparseArray<CarAudioZone> carAudioZones) {
        CarAudioZone primaryAudioZone = carAudioZones.get(PRIMARY_AUDIO_ZONE);
        for (AudioDeviceInfo info : mAddressToInputAudioDeviceInfoForAllInputDevices.values()) {
            if (!mAssignedInputAudioDevices.contains(info.getAddress())
                    && isMicrophoneInputDevice(info)) {
                primaryAudioZone.addInputAudioDevice(new AudioDeviceAttributes(info));
            }
        }
    }

    private void verifyOnlyOnePrimaryZone(CarAudioZone newZone, SparseArray<CarAudioZone> zones) {
        if (newZone.getId() == PRIMARY_AUDIO_ZONE && zones.contains(PRIMARY_AUDIO_ZONE)) {
            throw new RuntimeException("More than one zone parsed with primary audio zone ID: "
                    + PRIMARY_AUDIO_ZONE);
        }
    }

    private void verifyPrimaryZonePresent(SparseArray<CarAudioZone> zones) {
        if (!zones.contains(PRIMARY_AUDIO_ZONE)) {
            throw new RuntimeException("Primary audio zone is required");
        }
    }

    private CarAudioZone parseAudioZone(XmlPullParser parser)
            throws XmlPullParserException, IOException {
        final boolean isPrimary = Boolean.parseBoolean(
                parser.getAttributeValue(NAMESPACE, ATTR_IS_PRIMARY));
        final String zoneName = parser.getAttributeValue(NAMESPACE, ATTR_ZONE_NAME);
        final int audioZoneId = getZoneId(isPrimary, parser);
        parseOccupantZoneId(audioZoneId, parser);
        final CarAudioZone zone = new CarAudioZone(mCarAudioContext, zoneName, audioZoneId);
        if (isVersionLessThanThree()) {
            final CarAudioZoneConfig.Builder zoneConfigBuilder = new CarAudioZoneConfig.Builder(
                    zoneName, audioZoneId, /* zoneConfigId= */ 0, /* isDefault= */ true);
            while (parser.next() != XmlPullParser.END_TAG) {
                if (parser.getEventType() != XmlPullParser.START_TAG) continue;
                // Expect at least one <volumeGroups> in one audio zone
                if (Objects.equals(parser.getName(), TAG_VOLUME_GROUPS)) {
                    parseVolumeGroups(parser, zoneConfigBuilder);
                } else if (Objects.equals(parser.getName(), TAG_INPUT_DEVICES)) {
                    parseInputAudioDevices(parser, zone);
                } else {
                    CarAudioParserUtils.skip(parser);
                }
            }
            zone.addZoneConfig(zoneConfigBuilder.build());
            return zone;
        }
        while (parser.next() != XmlPullParser.END_TAG) {
            if (parser.getEventType() != XmlPullParser.START_TAG) continue;
            // Expect at least one <zoneConfigs> in one audio zone
            if (Objects.equals(parser.getName(), TAG_AUDIO_ZONE_CONFIGS)) {
                parseZoneConfigs(parser, zone);
            } else if (Objects.equals(parser.getName(), TAG_INPUT_DEVICES)) {
                parseInputAudioDevices(parser, zone);
            } else {
                CarAudioParserUtils.skip(parser);
            }
        }
        return zone;
    }

    private int getZoneId(boolean isPrimary, XmlPullParser parser) {
        String audioZoneIdString = parser.getAttributeValue(NAMESPACE, ATTR_ZONE_ID);
        if (isVersionOne()) {
            Preconditions.checkArgument(audioZoneIdString == null,
                    "Invalid audio attribute %s"
                            + ", Please update car audio configurations file "
                            + "to version to 2 to use it.", ATTR_ZONE_ID);
            return isPrimary ? PRIMARY_AUDIO_ZONE
                    : getNextSecondaryZoneId();
        }
        // Primary zone does not need to define it
        if (isPrimary && audioZoneIdString == null) {
            return PRIMARY_AUDIO_ZONE;
        }
        Objects.requireNonNull(audioZoneIdString,
                "Requires audioZoneId for all audio zones.");
        int zoneId = CarAudioParserUtils.parsePositiveIntAttribute(ATTR_ZONE_ID, audioZoneIdString);
        //Verify that primary zone id is PRIMARY_AUDIO_ZONE
        if (isPrimary) {
            Preconditions.checkArgument(zoneId == PRIMARY_AUDIO_ZONE,
                    "Primary zone %s must be %d or it can be left empty.",
                    ATTR_ZONE_ID, PRIMARY_AUDIO_ZONE);
        } else {
            Preconditions.checkArgument(zoneId != PRIMARY_AUDIO_ZONE,
                    "%s can only be %d for primary zone.",
                    ATTR_ZONE_ID, PRIMARY_AUDIO_ZONE);
        }
        validateAudioZoneIdIsUnique(zoneId);
        return zoneId;
    }

    private void parseOccupantZoneId(int audioZoneId, XmlPullParser parser) {
        String occupantZoneIdString = parser.getAttributeValue(NAMESPACE, ATTR_OCCUPANT_ZONE_ID);
        if (isVersionOne()) {
            Preconditions.checkArgument(occupantZoneIdString == null,
                    "Invalid audio attribute %s"
                            + ", Please update car audio configurations file "
                            + "to version to 2 to use it.", ATTR_OCCUPANT_ZONE_ID);
            return;
        }
        //Occupant id not required for all zones
        if (occupantZoneIdString == null) {
            return;
        }
        int occupantZoneId = CarAudioParserUtils.parsePositiveIntAttribute(ATTR_OCCUPANT_ZONE_ID,
                occupantZoneIdString);
        validateOccupantZoneIdIsUnique(occupantZoneId);
        mZoneIdToOccupantZoneIdMapping.put(audioZoneId, occupantZoneId);
    }

    private void parseInputAudioDevices(XmlPullParser parser, CarAudioZone zone)
            throws IOException, XmlPullParserException {
        if (isVersionOne()) {
            throw new IllegalStateException(
                    TAG_INPUT_DEVICES + " are not supported in car_audio_configuration.xml version "
                            + SUPPORTED_VERSION_1);
        }
        while (parser.next() != XmlPullParser.END_TAG) {
            if (parser.getEventType() != XmlPullParser.START_TAG) continue;
            if (Objects.equals(parser.getName(), TAG_INPUT_DEVICE)) {
                String audioDeviceAddress =
                        parser.getAttributeValue(NAMESPACE, ATTR_DEVICE_ADDRESS);
                validateInputAudioDeviceAddress(audioDeviceAddress);
                AudioDeviceInfo info =
                        mAddressToInputAudioDeviceInfoForAllInputDevices.get(audioDeviceAddress);
                Preconditions.checkArgument(info != null,
                        "%s %s of %s does not exist, add input device to"
                                + " audio_policy_configuration.xml.",
                        ATTR_DEVICE_ADDRESS, audioDeviceAddress, TAG_INPUT_DEVICE);
                zone.addInputAudioDevice(new AudioDeviceAttributes(info));
            }
            CarAudioParserUtils.skip(parser);
        }
    }

    private void validateInputAudioDeviceAddress(String audioDeviceAddress) {
        Objects.requireNonNull(audioDeviceAddress, () ->
                TAG_INPUT_DEVICE + " " + ATTR_DEVICE_ADDRESS + " attribute must be present.");
        Preconditions.checkArgument(!audioDeviceAddress.isEmpty(),
                "%s %s attribute can not be empty.",
                TAG_INPUT_DEVICE, ATTR_DEVICE_ADDRESS);
        if (mAssignedInputAudioDevices.contains(audioDeviceAddress)) {
            throw new IllegalArgumentException(TAG_INPUT_DEVICE + " " + audioDeviceAddress
                    + " repeats, " + TAG_INPUT_DEVICES + " can not repeat.");
        }
        mAssignedInputAudioDevices.add(audioDeviceAddress);
    }

    private void validateOccupantZoneIdIsUnique(int occupantZoneId) {
        if (mZoneIdToOccupantZoneIdMapping.indexOfValue(occupantZoneId) > -1) {
            throw new IllegalArgumentException(ATTR_OCCUPANT_ZONE_ID + " " + occupantZoneId
                    + " is already associated with a zone");
        }
    }

    private void validateAudioZoneIdIsUnique(int audioZoneId) {
        if (mAudioZoneIds.contains(audioZoneId)) {
            throw new IllegalArgumentException(ATTR_ZONE_ID + " " + audioZoneId
                    + " is already associated with a zone");
        }
        mAudioZoneIds.add(audioZoneId);
    }

    private void parseZoneConfigs(XmlPullParser parser, CarAudioZone zone)
            throws XmlPullParserException, IOException {
        int zoneConfigId = 0;
        while (parser.next() != XmlPullParser.END_TAG) {
            if (parser.getEventType() != XmlPullParser.START_TAG) continue;
            if (Objects.equals(parser.getName(), TAG_AUDIO_ZONE_CONFIG)) {
                if (isVersionLessThanFour() && zone.getId() == PRIMARY_AUDIO_ZONE
                        && zoneConfigId > 0) {
                    throw new IllegalArgumentException(
                            "Primary zone cannot have multiple zone configurations");
                }
                parseZoneConfig(parser, zone, zoneConfigId);
                zoneConfigId++;
            } else {
                CarAudioParserUtils.skip(parser);
            }
        }
    }

    private void parseZoneConfig(XmlPullParser parser, CarAudioZone zone, int zoneConfigId)
            throws XmlPullParserException, IOException {
        final boolean isDefault = Boolean.parseBoolean(
                parser.getAttributeValue(NAMESPACE, ATTR_IS_CONFIG_DEFAULT));
        final String zoneConfigName = parser.getAttributeValue(NAMESPACE, ATTR_CONFIG_NAME);
        validateAudioZoneConfigName(zoneConfigName);
        final CarAudioZoneConfig.Builder zoneConfigBuilder = new CarAudioZoneConfig.Builder(
                zoneConfigName, zone.getId(), zoneConfigId, isDefault);
        boolean valid = true;
        zoneConfigBuilder.setFadeManagerConfigurationEnabled(mUseFadeManagerConfiguration);
        while (parser.next() != XmlPullParser.END_TAG) {
            if (parser.getEventType() != XmlPullParser.START_TAG) continue;
            // Expect at least one <volumeGroups> in one audio zone config
            if (Objects.equals(parser.getName(), TAG_VOLUME_GROUPS)) {
                if (!parseVolumeGroups(parser, zoneConfigBuilder)) {
                    valid = false;
                }
            }  else if (Objects.equals(parser.getName(), TAG_APPLY_FADE_CONFIGS)) {
                parseApplyFadeConfigs(parser, zoneConfigBuilder);
            } else {
                CarAudioParserUtils.skip(parser);
            }
        }
        // If the configuration is not valid we can the config
        if (!valid) {
            String message = "Skipped configuration " + zoneConfigName + " in zone " + zone.getId();
            Slogf.e(TAG, message);
            mCarServiceLocalLog.log(message);
            return;
        }
        zone.addZoneConfig(zoneConfigBuilder.build());
    }

    private void validateAudioZoneConfigName(String configName) {
        Objects.requireNonNull(configName, TAG_AUDIO_ZONE_CONFIG + " " + ATTR_CONFIG_NAME
                        + " attribute must be present.");
        Preconditions.checkArgument(!configName.isEmpty(),
                "%s %s attribute can not be empty.",
                TAG_AUDIO_ZONE_CONFIG, ATTR_CONFIG_NAME);
        if (mAudioZoneConfigNames.contains(configName)) {
            throw new IllegalArgumentException(ATTR_CONFIG_NAME + " " + configName
                            + " repeats, " + ATTR_CONFIG_NAME + " can not repeat.");
        }
        mAudioZoneConfigNames.add(configName);
    }

    private boolean parseVolumeGroups(XmlPullParser parser,
            CarAudioZoneConfig.Builder zoneConfigBuilder)
            throws XmlPullParserException, IOException {
        int groupId = 0;
        boolean valid = true;
        while (parser.next() != XmlPullParser.END_TAG) {
            if (parser.getEventType() != XmlPullParser.START_TAG) continue;
            if (Objects.equals(parser.getName(), TAG_VOLUME_GROUP)) {
                String groupName = parser.getAttributeValue(NAMESPACE, VOLUME_GROUP_NAME);
                Preconditions.checkArgument(!mUseCoreAudioVolume || groupName != null,
                        "%s %s attribute can not be empty when relying on core volume groups",
                        TAG_VOLUME_GROUP, VOLUME_GROUP_NAME);
                if (groupName == null) {
                    groupName = new StringBuilder().append("config ")
                            .append(zoneConfigBuilder.getZoneConfigId()).append(" group ")
                            .append(groupId).toString();
                }
                int maxActivationVolumePercentage = parseVolumeGroupActivationVolume(parser,
                        /* isMax= */ true);
                int minActivationVolumePercentage = parseVolumeGroupActivationVolume(parser,
                        /* isMax= */ false);
                validateMinMaxActivationVolume(maxActivationVolumePercentage,
                        minActivationVolumePercentage);

                CarVolumeGroupFactory factory = new CarVolumeGroupFactory(mAudioManager,
                        mCarAudioSettings, mCarAudioContext, zoneConfigBuilder.getZoneId(),
                        zoneConfigBuilder.getZoneConfigId(), groupId, groupName,
                        mUseCarVolumeGroupMute, maxActivationVolumePercentage,
                        minActivationVolumePercentage);

                if (!parseVolumeGroup(parser, factory)) {
                    valid = false;
                }
                if (!valid) {
                    continue;
                }
                zoneConfigBuilder.addVolumeGroup(factory.getCarVolumeGroup(mUseCoreAudioVolume));
                groupId++;
            } else {
                CarAudioParserUtils.skip(parser);
            }
        }
        return valid;
    }

    private int parseVolumeGroupActivationVolume(XmlPullParser parser, boolean isMax) {
        int defaultValue = isMax ? ACTIVATION_VOLUME_PERCENTAGE_MAX
                : ACTIVATION_VOLUME_PERCENTAGE_MIN;
        String attr = isMax ? ATTR_MAX_ACTIVATION_VOLUME_PERCENTAGE
                : ATTR_MIN_ACTIVATION_VOLUME_PERCENTAGE;
        String activationPercentageString = parser.getAttributeValue(NAMESPACE, attr);
        if (activationPercentageString == null) {
            return defaultValue;
        }
        if (isVersionLessThanFour()) {
            throw new IllegalArgumentException(TAG_VOLUME_GROUP + " " + attr
                    + " attribute not supported for versions less than " + SUPPORTED_VERSION_4
                    + ", but current version is " + mCurrentVersion);
        }
        if (Flags.carAudioMinMaxActivationVolume()) {
            return CarAudioParserUtils.parsePositiveIntAttribute(attr, activationPercentageString);
        }
        mCarServiceLocalLog.log("Found " + TAG_VOLUME_GROUP + " " + attr
                + " attribute while min/max activation volume is disabled");
        return defaultValue;
    }

    private boolean parseVolumeGroup(XmlPullParser parser, CarVolumeGroupFactory groupFactory)
            throws XmlPullParserException, IOException {
        boolean valid = true;
        while (parser.next() != XmlPullParser.END_TAG) {
            if (parser.getEventType() != XmlPullParser.START_TAG) continue;
            if (Objects.equals(parser.getName(), TAG_AUDIO_DEVICE)) {
                if (!parseVolumeGroupDeviceToContextMapping(parser, groupFactory)) {
                    valid = false;
                }
            } else {
                CarAudioParserUtils.skip(parser);
            }
        }
        return valid;
    }

    private boolean parseVolumeGroupDeviceToContextMapping(XmlPullParser parser,
            CarVolumeGroupFactory groupFactory) throws XmlPullParserException, IOException {
        String address = parser.getAttributeValue(NAMESPACE, ATTR_DEVICE_ADDRESS);
        int type = parseAudioDeviceType(parser);
        boolean valid = validateOutputAudioDevice(address, type);
        parseVolumeGroupContexts(parser, groupFactory, address, type);
        return valid;
    }

    private int parseAudioDeviceType(XmlPullParser parser) {
        String typeString = parser.getAttributeValue(NAMESPACE, ATTR_DEVICE_TYPE);
        if (typeString == null) {
            return TYPE_BUS;
        }
        if (isVersionLessThanFour()) {
            throw new IllegalArgumentException("Audio device type " + typeString
                    + " not supported for versions less than " + SUPPORTED_VERSION_4
                    + ", but current version is " + mCurrentVersion);
        }
        return ConstantDebugUtils.toValue(AudioDeviceInfo.class, typeString);
    }

    private void parseApplyFadeConfigs(XmlPullParser parser,
            CarAudioZoneConfig.Builder zoneConfigBuilder)
            throws XmlPullParserException, IOException {
        Preconditions.checkArgument(!isVersionLessThanFour(),
                "Fade configurations not available in versions < 4");
        // ignore fade configurations when feature is not enabled
        if (!mUseFadeManagerConfiguration) {
            String message = "Skipped applying fade configs since feature flag is disabled";
            Slogf.i(TAG, message);
            mCarServiceLocalLog.log(message);
            CarAudioParserUtils.skip(parser);
            return;
        }

        Objects.requireNonNull(mCarAudioFadeConfigurationHelper,
                "Car audio fade configuration helper can not be null when parsing fade configs");
        while (parser.next() != XmlPullParser.END_TAG) {
            if (parser.getEventType() != XmlPullParser.START_TAG) {
                continue;
            }
            if (Objects.equals(parser.getName(), FADE_CONFIG)) {
                parseFadeConfig(parser, zoneConfigBuilder);
            } else {
                CarAudioParserUtils.skip(parser);
            }
        }
    }

    private void parseFadeConfig(XmlPullParser parser,
            CarAudioZoneConfig.Builder zoneConfigBuilder)
            throws XmlPullParserException, IOException {
        String name = parser.getAttributeValue(NAMESPACE, FADE_CONFIG_NAME);
        boolean isDefault = Boolean.parseBoolean(
                parser.getAttributeValue(NAMESPACE, FADE_CONFIG_IS_DEFAULT));
        validateFadeConfig(name);
        CarAudioFadeConfiguration afc =
                mCarAudioFadeConfigurationHelper.getCarAudioFadeConfiguration(name);
        if (isDefault) {
            zoneConfigBuilder.setDefaultCarAudioFadeConfiguration(afc);
            CarAudioParserUtils.skip(parser);
        } else {
            parseTransientFadeConfigs(parser, zoneConfigBuilder, afc);
        }
    }

    private void parseTransientFadeConfigs(XmlPullParser parser,
            CarAudioZoneConfig.Builder zoneConfigBuilder,
            CarAudioFadeConfiguration carAudioFadeConfiguration)
            throws XmlPullParserException, IOException {
        boolean hasAudioAttributes = false;
        while (parser.next() != XmlPullParser.END_TAG) {
            if (parser.getEventType() != XmlPullParser.START_TAG) {
                continue;
            }
            if (Objects.equals(parser.getName(), CarAudioParserUtils.TAG_AUDIO_ATTRIBUTES)) {
                List<AudioAttributes> attributes =
                        CarAudioParserUtils.parseAudioAttributes(parser,
                                CarAudioParserUtils.TAG_AUDIO_ATTRIBUTES);
                for (int index = 0; index < attributes.size(); index++) {
                    hasAudioAttributes = true;
                    zoneConfigBuilder.setCarAudioFadeConfigurationForAudioAttributes(
                            attributes.get(index), carAudioFadeConfiguration);
                }
            } else {
                CarAudioParserUtils.skip(parser);
            }
        }
        Preconditions.checkArgument(hasAudioAttributes,
                "Transient fade configs must have valid audio attributes");
    }

    private void validateFadeConfig(String name) {
        Objects.requireNonNull(name, "Fade config name cannot be null");
        Preconditions.checkArgument(!name.isEmpty(), "Fade config name cannot be empty");
        Preconditions.checkArgument(mCarAudioFadeConfigurationHelper.isConfigAvailable(name),
                "No config available for name:%s", name);
    }

    private boolean isVersionLessThanFour() {
        return mCurrentVersion < SUPPORTED_VERSION_4;
    }

    private boolean isVersionFourOrGreater() {
        return mCurrentVersion >= SUPPORTED_VERSION_4;
    }

    private void validateMinMaxActivationVolume(int maxActivationVolume,
                                                int minActivationVolume) {
        if (!Flags.carAudioMinMaxActivationVolume()) {
            return;
        }
        Preconditions.checkArgument(maxActivationVolume >= ACTIVATION_VOLUME_PERCENTAGE_MIN
                        && maxActivationVolume <= ACTIVATION_VOLUME_PERCENTAGE_MAX,
                "%s %s attribute is %s but can not be outside the range (%s,%s)",
                TAG_VOLUME_GROUP, ATTR_MAX_ACTIVATION_VOLUME_PERCENTAGE, maxActivationVolume,
                ACTIVATION_VOLUME_PERCENTAGE_MIN, ACTIVATION_VOLUME_PERCENTAGE_MAX);
        Preconditions.checkArgument(minActivationVolume >= ACTIVATION_VOLUME_PERCENTAGE_MIN
                        && minActivationVolume <= ACTIVATION_VOLUME_PERCENTAGE_MAX,
                "%s %s attribute is %s but can not be outside the range (%s,%s)",
                TAG_VOLUME_GROUP, ATTR_MIN_ACTIVATION_VOLUME_PERCENTAGE, minActivationVolume,
                ACTIVATION_VOLUME_PERCENTAGE_MIN, ACTIVATION_VOLUME_PERCENTAGE_MAX);
        Preconditions.checkArgument(minActivationVolume < maxActivationVolume,
                "%s %s is %s but can not be larger than or equal to %s attribute %s",
                TAG_VOLUME_GROUP, ATTR_MIN_ACTIVATION_VOLUME_PERCENTAGE, minActivationVolume,
                ATTR_MAX_ACTIVATION_VOLUME_PERCENTAGE, maxActivationVolume);
    }

    private boolean validateOutputAudioDevice(String address, int type) {
        if (!Flags.carAudioDynamicDevices() && TextUtils.isEmpty(address)) {
            // If the version is four or greater, we can only return that the output device is not
            // valid since we can not crash. The configuration will only skip reading configuration.
            if (isVersionFourOrGreater()) {
                mCarServiceLocalLog.log("Found invalid device while dynamic device is disabled,"
                        + " device address is empty for device type "
                        + DebugUtils.constantToString(AudioDeviceInfo.class,
                        /* prefix= */ "TYPE_", type));
                return false;
            }
            throw new IllegalStateException("Output device address must be specified");
        }

        if (!isValidAudioDeviceTypeOut(type)) {
            // If the version is four or greater, we can only return that the output device is not
            // valid since we can not crash. The configuration will only skip reading configuration.
            if (isVersionFourOrGreater() && !Flags.carAudioDynamicDevices()) {
                mCarServiceLocalLog.log("Found invalid device type while dynamic device is"
                        + " disabled, device address " + address + " and device type "
                        + DebugUtils.constantToString(AudioDeviceInfo.class,
                        /* prefix= */ "TYPE_", type));
                return false;
            }
            throw new IllegalStateException("Output device type " + DebugUtils.constantToString(
                    AudioDeviceInfo.class, /* prefix= */ "TYPE_", type) + " is not valid");
        }

        boolean requiresDeviceAddress = type == TYPE_BUS;
        if (requiresDeviceAddress && !mAddressToCarAudioDeviceInfo.containsKey(address)) {
            throw new IllegalStateException("Output device address " + address
                    + " does not belong to any configured output device.");
        }
        return true;
    }

    private void parseVolumeGroupContexts(
            XmlPullParser parser, CarVolumeGroupFactory groupFactory, String address, int type)
            throws XmlPullParserException, IOException {
        while (parser.next() != XmlPullParser.END_TAG) {
            if (parser.getEventType() != XmlPullParser.START_TAG) continue;
            if (Objects.equals(parser.getName(), TAG_CONTEXT)) {
                @AudioContext int carAudioContextId = parseCarAudioContextId(
                        parser.getAttributeValue(NAMESPACE, ATTR_CONTEXT_NAME));
                validateCarAudioContextSupport(carAudioContextId);
                CarAudioDeviceInfo info = getCarAudioDeviceInfo(address, type);
                groupFactory.setDeviceInfoForContext(carAudioContextId, info);

                // If V1, default new contexts to same device as DEFAULT_AUDIO_USAGE
                if (isVersionOne() && carAudioContextId == mCarAudioContext
                        .getContextForAudioAttribute(CAR_DEFAULT_AUDIO_ATTRIBUTE)) {
                    groupFactory.setNonLegacyContexts(info);
                }
            }
            // Always skip to upper level since we're at the lowest.
            CarAudioParserUtils.skip(parser);
        }
    }

    private CarAudioDeviceInfo getCarAudioDeviceInfo(String address, int type) {
        if (type == TYPE_BUS) {
            return mAddressToCarAudioDeviceInfo.get(address);
        }

        String newAddress = address == null ? "" : address;
        return new CarAudioDeviceInfo(mAudioManager,
                new AudioDeviceAttributes(AudioDeviceAttributes.ROLE_OUTPUT, type, newAddress));
    }

    private boolean isVersionLessThanThree() {
        return mCurrentVersion < SUPPORTED_VERSION_3;
    }

    private boolean isVersionOne() {
        return mCurrentVersion == SUPPORTED_VERSION_1;
    }

    private @AudioContext int parseCarAudioContextId(String context) {
        return mContextNameToId.getOrDefault(context.toLowerCase(ROOT),
                CarAudioContext.getInvalidContext());
    }

    private void validateCarAudioContextSupport(@AudioContext int audioContext) {
        if (isVersionOne() && CarAudioContext.getCarSystemContextIds().contains(audioContext)) {
            throw new IllegalArgumentException(String.format(
                    "Non-legacy audio contexts such as %s are not supported in "
                            + "car_audio_configuration.xml version %d",
                    mCarAudioContext.toString(audioContext), SUPPORTED_VERSION_1));
        }
    }

    private int getNextSecondaryZoneId() {
        int zoneId = mNextSecondaryZoneId;
        mNextSecondaryZoneId += 1;
        return zoneId;
    }

    public CarAudioContext getCarAudioContext() {
        return mCarAudioContext;
    }

    public List<CarAudioDeviceInfo> getMirrorDeviceInfos() {
        return mMirroringDevices;
    }

    /**
     * Car audio service supports a subset of the output devices, including most dynamic
     * devices, built in speaker, and bus devices.
     */
    private static boolean isValidAudioDeviceTypeOut(int type) {
        if (!Flags.carAudioDynamicDevices()) {
            return type == TYPE_BUS;
        }

        switch (type) {
            case TYPE_BUILTIN_SPEAKER:
            case TYPE_WIRED_HEADSET:
            case TYPE_WIRED_HEADPHONES:
            case TYPE_BLUETOOTH_A2DP:
            case TYPE_HDMI:
            case TYPE_USB_ACCESSORY:
            case TYPE_USB_DEVICE:
            case TYPE_USB_HEADSET:
            case TYPE_AUX_LINE:
            case TYPE_BUS:
            case TYPE_BLE_HEADSET:
            case TYPE_BLE_SPEAKER:
            case TYPE_BLE_BROADCAST:
                return true;
            default:
                return false;
        }
    }
}
