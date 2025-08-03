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

package com.android.bedstead.nene.packages;

/** Packages helper methods common to host and device. */
public class CommonPackages {
    CommonPackages() {}

    /** See {@code PackageManager#FEATURE_AUDIO_LOW_LATENCY}. */
    public static final String FEATURE_AUDIO_LOW_LATENCY = "android.hardware.audio.low_latency";

    /** See {@code PackageManager#FEATURE_AUDIO_OUTPUT}. */
    public static final String FEATURE_AUDIO_OUTPUT = "android.hardware.audio.output";

    /** See {@code PackageManager#FEATURE_AUDIO_PRO}. */
    public static final String FEATURE_AUDIO_PRO = "android.hardware.audio.pro";

    /** See {@code PackageManager#FEATURE_BLUETOOTH}. */
    public static final String FEATURE_BLUETOOTH = "android.hardware.bluetooth";

    /** See {@code PackageManager#FEATURE_BLUETOOTH_LE}. */
    public static final String FEATURE_BLUETOOTH_LE = "android.hardware.bluetooth_le";

    /** See {@code PackageManager#FEATURE_CAMERA}. */
    public static final String FEATURE_CAMERA = "android.hardware.camera";

    /** See {@code PackageManager#FEATURE_CAMERA_AUTOFOCUS}. */
    public static final String FEATURE_CAMERA_AUTOFOCUS = "android.hardware.camera.autofocus";

    /** See {@code PackageManager#FEATURE_CAMERA_ANY}. */
    public static final String FEATURE_CAMERA_ANY = "android.hardware.camera.any";

    /** See {@code PackageManager#FEATURE_CAMERA_EXTERNAL}. */
    public static final String FEATURE_CAMERA_EXTERNAL = "android.hardware.camera.external";

    /** See {@code PackageManager#FEATURE_CAMERA_FLASH}. */
    public static final String FEATURE_CAMERA_FLASH = "android.hardware.camera.flash";

    /** See {@code PackageManager#FEATURE_CAMERA_FRONT}. */
    public static final String FEATURE_CAMERA_FRONT = "android.hardware.camera.front";

    /** See {@code PackageManager#FEATURE_CAMERA_LEVEL_FULL}. */
    public static final String FEATURE_CAMERA_LEVEL_FULL = "android.hardware.camera.level.full";

    /** See {@code PackageManager#FEATURE_CAMERA_CAPABILITY_MANUAL_SENSOR}. */
    public static final String FEATURE_CAMERA_CAPABILITY_MANUAL_SENSOR =
            "android.hardware.camera.capability.manual_sensor";

    /** See {@code PackageManager#FEATURE_CAMERA_CAPABILITY_MANUAL_POST_PROCESSING}. */
    public static final String FEATURE_CAMERA_CAPABILITY_MANUAL_POST_PROCESSING =
            "android.hardware.camera.capability.manual_post_processing";

    /** See {@code PackageManager#FEATURE_CAMERA_CAPABILITY_RAW}. */
    public static final String FEATURE_CAMERA_CAPABILITY_RAW =
            "android.hardware.camera.capability.raw";

    /** See {@code PackageManager#FEATURE_CAMERA_AR}. */
    public static final String FEATURE_CAMERA_AR =
            "android.hardware.camera.ar";

    /** See {@code PackageManager#FEATURE_CAMERA_CONCURRENT}. */
    public static final String FEATURE_CAMERA_CONCURRENT = "android.hardware.camera.concurrent";

    /** See {@code PackageManager#FEATURE_CONSUMER_IR}. */
    public static final String FEATURE_CONSUMER_IR = "android.hardware.consumerir";

    /** See {@code PackageManager#FEATURE_CONTEXT_HUB}. */
    public static final String FEATURE_CONTEXT_HUB = "android.hardware.context_hub";

    /** See {@code PackageManager#FEATURE_CTS}. */
    public static final String FEATURE_CTS = "android.software.cts";

    /** See {@code PackageManager#FEATURE_CAR_TEMPLATES_HOST}. */
    public static final String FEATURE_CAR_TEMPLATES_HOST =
            "android.software.car.templates_host";

    /** See {@code PackageManager#FEATURE_IDENTITY_CREDENTIAL_HARDWARE}. */
    public static final String FEATURE_IDENTITY_CREDENTIAL_HARDWARE =
            "android.hardware.identity_credential";

    /** See {@code PackageManager#FEATURE_IDENTITY_CREDENTIAL_HARDWARE_DIRECT_ACCESS}. */
    public static final String FEATURE_IDENTITY_CREDENTIAL_HARDWARE_DIRECT_ACCESS =
            "android.hardware.identity_credential_direct_access";

    /** See {@code PackageManager#FEATURE_LOCATION}. */
    public static final String FEATURE_LOCATION = "android.hardware.location";

    /** See {@code PackageManager#FEATURE_LOCATION_GPS}. */
    public static final String FEATURE_LOCATION_GPS = "android.hardware.location.gps";

    /** See {@code PackageManager#FEATURE_LOCATION_NETWORK}. */
    public static final String FEATURE_LOCATION_NETWORK = "android.hardware.location.network";

    /** See {@code PackageManager#FEATURE_FELICA}. */
    public static final String FEATURE_FELICA = "android.hardware.felica";

    /** See {@code PackageManager#FEATURE_RAM_LOW}. */
    public static final String FEATURE_RAM_LOW = "android.hardware.ram.low";

    /** See {@code PackageManager#FEATURE_RAM_NORMAL}. */
    public static final String FEATURE_RAM_NORMAL = "android.hardware.ram.normal";

    /** See {@code PackageManager#FEATURE_MICROPHONE}. */
    public static final String FEATURE_MICROPHONE = "android.hardware.microphone";

    /** See {@code PackageManager#FEATURE_NFC}. */
    public static final String FEATURE_NFC = "android.hardware.nfc";

    /** See {@code PackageManager#FEATURE_NFC_HCE}. */
    public static final String FEATURE_NFC_HCE = "android.hardware.nfc.hce";

    /** See {@code PackageManager#FEATURE_NFC_HOST_CARD_EMULATION}. */
    public static final String FEATURE_NFC_HOST_CARD_EMULATION = "android.hardware.nfc.hce";

    /** See {@code PackageManager#FEATURE_NFC_HOST_CARD_EMULATION_NFCF}. */
    public static final String FEATURE_NFC_HOST_CARD_EMULATION_NFCF = "android.hardware.nfc.hcef";

    /** See {@code PackageManager#FEATURE_NFC_OFF_HOST_CARD_EMULATION_UICC}. */
    public static final String FEATURE_NFC_OFF_HOST_CARD_EMULATION_UICC =
            "android.hardware.nfc.uicc";

    /** See {@code PackageManager#FEATURE_NFC_OFF_HOST_CARD_EMULATION_ESE}. */
    public static final String FEATURE_NFC_OFF_HOST_CARD_EMULATION_ESE = "android.hardware.nfc.ese";

    /** See {@code PackageManager#FEATURE_NFC_BEAM}. */
    public static final String FEATURE_NFC_BEAM = "android.sofware.nfc.beam";

    /** See {@code PackageManager#FEATURE_NFC_ANY}. */
    public static final String FEATURE_NFC_ANY = "android.hardware.nfc.any";

    /** See {@code PackageManager#FEATURE_SE_OMAPI_UICC}. */
    public static final String FEATURE_SE_OMAPI_UICC = "android.hardware.se.omapi.uicc";

    /** See {@code PackageManager#FEATURE_SE_OMAPI_ESE}. */
    public static final String FEATURE_SE_OMAPI_ESE = "android.hardware.se.omapi.ese";

    /** See {@code PackageManager#FEATURE_SE_OMAPI_SD}. */
    public static final String FEATURE_SE_OMAPI_SD = "android.hardware.se.omapi.sd";

    /** See {@code PackageManager#FEATURE_SECURITY_MODEL_COMPATIBLE}. */
    public static final String FEATURE_SECURITY_MODEL_COMPATIBLE =
            "android.hardware.security.model.compatible";

    /** See {@code PackageManager#FEATURE_OPENGLES_EXTENSION_PACK}. */
    public static final String FEATURE_OPENGLES_EXTENSION_PACK = "android.hardware.opengles.aep";

    /** See {@code PackageManager#FEATURE_VULKAN_HARDWARE_LEVEL}. */
    public static final String FEATURE_VULKAN_HARDWARE_LEVEL = "android.hardware.vulkan.level";

    /** See {@code PackageManager#FEATURE_VULKAN_HARDWARE_COMPUTE}. */
    public static final String FEATURE_VULKAN_HARDWARE_COMPUTE = "android.hardware.vulkan.compute";

    /** See {@code PackageManager#FEATURE_VULKAN_HARDWARE_VERSION}. */
    public static final String FEATURE_VULKAN_HARDWARE_VERSION = "android.hardware.vulkan.version";

    /** See {@code PackageManager#FEATURE_VULKAN_DEQP_LEVEL}. */
    public static final String FEATURE_VULKAN_DEQP_LEVEL = "android.software.vulkan.deqp.level";

    /** See {@code PackageManager#FEATURE_OPENGLES_DEQP_LEVEL}. */
    public static final String FEATURE_OPENGLES_DEQP_LEVEL = "android.software.opengles.deqp.level";

    /** See {@code PackageManager#FEATURE_BROADCAST_RADIO}. */
    public static final String FEATURE_BROADCAST_RADIO = "android.hardware.broadcastradio";

    /** See {@code PackageManager#FEATURE_SECURE_LOCK_SCREEN}. */
    public static final String FEATURE_SECURE_LOCK_SCREEN = "android.software.secure_lock_screen";

    /** See {@code PackageManager#FEATURE_SENSOR_ACCELEROMETER}. */
    public static final String FEATURE_SENSOR_ACCELEROMETER = "android.hardware.sensor.accelerometer";

    /** See {@code PackageManager#FEATURE_SENSOR_BAROMETER}. */
    public static final String FEATURE_SENSOR_BAROMETER = "android.hardware.sensor.barometer";

    /** See {@code PackageManager#FEATURE_SENSOR_COMPASS}. */
    public static final String FEATURE_SENSOR_COMPASS = "android.hardware.sensor.compass";

    /** See {@code PackageManager#FEATURE_SENSOR_GYROSCOPE}. */
    public static final String FEATURE_SENSOR_GYROSCOPE = "android.hardware.sensor.gyroscope";

    /** See {@code PackageManager#FEATURE_SENSOR_LIGHT}. */
    public static final String FEATURE_SENSOR_LIGHT = "android.hardware.sensor.light";

    /** See {@code PackageManager#FEATURE_SENSOR_PROXIMITY}. */
    public static final String FEATURE_SENSOR_PROXIMITY = "android.hardware.sensor.proximity";

    /** See {@code PackageManager#FEATURE_SENSOR_STEP_COUNTER}. */
    public static final String FEATURE_SENSOR_STEP_COUNTER = "android.hardware.sensor.stepcounter";

    /** See {@code PackageManager#FEATURE_SENSOR_STEP_DETECTOR}. */
    public static final String FEATURE_SENSOR_STEP_DETECTOR = "android.hardware.sensor.stepdetector";

    /** See {@code PackageManager#FEATURE_SENSOR_HEART_RATE}. */
    public static final String FEATURE_SENSOR_HEART_RATE = "android.hardware.sensor.heartrate";

    /** See {@code PackageManager#FEATURE_SENSOR_HEART_RATE_ECG}. */
    public static final String FEATURE_SENSOR_HEART_RATE_ECG =
            "android.hardware.sensor.heartrate.ecg";

    /** See {@code PackageManager#FEATURE_SENSOR_RELATIVE_HUMIDITY}. */
    public static final String FEATURE_SENSOR_RELATIVE_HUMIDITY =
            "android.hardware.sensor.relative_humidity";

    /** See {@code PackageManager#FEATURE_SENSOR_AMBIENT_TEMPERATURE}. */
    public static final String FEATURE_SENSOR_AMBIENT_TEMPERATURE =
            "android.hardware.sensor.ambient_temperature";

    /** See {@code PackageManager#FEATURE_SENSOR_HINGE_ANGLE}. */
    public static final String FEATURE_SENSOR_HINGE_ANGLE = "android.hardware.sensor.hinge_angle";

    /** See {@code PackageManager#FEATURE_HIFI_SENSORS}. */
    public static final String FEATURE_HIFI_SENSORS =
            "android.hardware.sensor.hifi_sensors";

    /** See {@code PackageManager#FEATURE_ASSIST_GESTURE}. */
    public static final String FEATURE_ASSIST_GESTURE = "android.hardware.sensor.assist";

    /** See {@code PackageManager#FEATURE_TELEPHONY}. */
    public static final String FEATURE_TELEPHONY = "android.hardware.telephony";

    /** See {@code PackageManager#FEATURE_TELEPHONY_CDMA}. */
    public static final String FEATURE_TELEPHONY_CDMA = "android.hardware.telephony.cdma";

    /** See {@code PackageManager#FEATURE_TELEPHONY_GSM}. */
    public static final String FEATURE_TELEPHONY_GSM = "android.hardware.telephony.gsm";

    /** See {@code PackageManager#FEATURE_TELEPHONY_CARRIERLOCK}. */
    public static final String FEATURE_TELEPHONY_CARRIERLOCK =
            "android.hardware.telephony.carrierlock";

    /** See {@code PackageManager#FEATURE_TELEPHONY_EUICC}. */
    public static final String FEATURE_TELEPHONY_EUICC = "android.hardware.telephony.euicc";

    /** See {@code PackageManager#FEATURE_TELEPHONY_MBMS}. */
    public static final String FEATURE_TELEPHONY_MBMS = "android.hardware.telephony.mbms";

    /** See {@code PackageManager#FEATURE_TELEPHONY_IMS}. */
    public static final String FEATURE_TELEPHONY_IMS = "android.hardware.telephony.ims";

    /** See {@code PackageManager#FEATURE_TELEPHONY_IMS_SINGLE_REGISTRATION}. */
    public static final String FEATURE_TELEPHONY_IMS_SINGLE_REGISTRATION =
            "android.hardware.telephony.ims.singlereg";

    /** See {@code PackageManager#FEATURE_UWB}. */
    public static final String FEATURE_UWB = "android.hardware.uwb";

    /** See {@code PackageManager#FEATURE_USB_HOST}. */
    public static final String FEATURE_USB_HOST = "android.hardware.usb.host";

    /** See {@code PackageManager#FEATURE_USB_ACCESSORY}. */
    public static final String FEATURE_USB_ACCESSORY = "android.hardware.usb.accessory";

    /** See {@code PackageManager#FEATURE_SIP}. */
    public static final String FEATURE_SIP = "android.software.sip";

    /** See {@code PackageManager#FEATURE_SIP_VOIP}. */
    public static final String FEATURE_SIP_VOIP = "android.software.sip.voip";

    /** See {@code PackageManager#FEATURE_CONNECTION_SERVICE}. */
    public static final String FEATURE_CONNECTION_SERVICE = "android.software.connectionservice";

    /** See {@code PackageManager#FEATURE_TOUCHSCREEN}. */
    public static final String FEATURE_TOUCHSCREEN = "android.hardware.touchscreen";

    /** See {@code PackageManager#FEATURE_TOUCHSCREEN_MULTITOUCH}. */
    public static final String FEATURE_TOUCHSCREEN_MULTITOUCH = "android.hardware.touchscreen.multitouch";

    /** See {@code PackageManager#FEATURE_TOUCHSCREEN_MULTITOUCH_DISTINCT}. */
    public static final String FEATURE_TOUCHSCREEN_MULTITOUCH_DISTINCT = "android.hardware.touchscreen.multitouch.distinct";

    /** See {@code PackageManager#FEATURE_TOUCHSCREEN_MULTITOUCH_JAZZHAND}. */
    public static final String FEATURE_TOUCHSCREEN_MULTITOUCH_JAZZHAND = "android.hardware.touchscreen.multitouch.jazzhand";

    /** See {@code PackageManager#FEATURE_FAKETOUCH}. */
    public static final String FEATURE_FAKETOUCH = "android.hardware.faketouch";

    /** See {@code PackageManager#FEATURE_FAKETOUCH_MULTITOUCH_DISTINCT}. */
    public static final String FEATURE_FAKETOUCH_MULTITOUCH_DISTINCT = "android.hardware.faketouch.multitouch.distinct";

    /** See {@code PackageManager#FEATURE_FAKETOUCH_MULTITOUCH_JAZZHAND}. */
    public static final String FEATURE_FAKETOUCH_MULTITOUCH_JAZZHAND = "android.hardware.faketouch.multitouch.jazzhand";

    /** See {@code PackageManager#FEATURE_FINGERPRINT}. */
    public static final String FEATURE_FINGERPRINT = "android.hardware.fingerprint";

    /** See {@code PackageManager#FEATURE_FACE}. */
    public static final String FEATURE_FACE = "android.hardware.biometrics.face";

    /** See {@code PackageManager#FEATURE_IRIS}. */
    public static final String FEATURE_IRIS = "android.hardware.biometrics.iris";

    /** See {@code PackageManager#FEATURE_SCREEN_PORTRAIT}. */
    public static final String FEATURE_SCREEN_PORTRAIT = "android.hardware.screen.portrait";

    /** See {@code PackageManager#FEATURE_SCREEN_LANDSCAPE}. */
    public static final String FEATURE_SCREEN_LANDSCAPE = "android.hardware.screen.landscape";

    /** See {@code PackageManager#FEATURE_LIVE_WALLPAPER}. */
    public static final String FEATURE_LIVE_WALLPAPER = "android.software.live_wallpaper";

    /** See {@code PackageManager#FEATURE_APP_WIDGETS}. */
    public static final String FEATURE_APP_WIDGETS = "android.software.app_widgets";

    /** See {@code PackageManager#FEATURE_CANT_SAVE_STATE}. */
    public static final String FEATURE_CANT_SAVE_STATE = "android.software.cant_save_state";

    /** See {@code PackageManager#FEATURE_GAME_SERVICE}. */
    public static final String FEATURE_GAME_SERVICE = "android.software.game_service";

    /** See {@code PackageManager#FEATURE_VOICE_RECOGNIZERS}. */
    public static final String FEATURE_VOICE_RECOGNIZERS = "android.software.voice_recognizers";

    /** See {@code PackageManager#FEATURE_HOME_SCREEN}. */
    public static final String FEATURE_HOME_SCREEN = "android.software.home_screen";

    /** See {@code PackageManager#FEATURE_INPUT_METHODS}. */
    public static final String FEATURE_INPUT_METHODS = "android.software.input_methods";

    /** See {@code PackageManager#FEATURE_DEVICE_ADMIN}. */
    public static final String FEATURE_DEVICE_ADMIN = "android.software.device_admin";

    /** See {@code PackageManager#FEATURE_LEANBACK}. */
    public static final String FEATURE_LEANBACK = "android.software.leanback";

    /** See {@code PackageManager#FEATURE_LEANBACK_ONLY}. */
    public static final String FEATURE_LEANBACK_ONLY = "android.software.leanback_only";

    /** See {@code PackageManager#FEATURE_LIVE_TV}. */
    public static final String FEATURE_LIVE_TV = "android.software.live_tv";

    /** See {@code PackageManager#FEATURE_WIFI}. */
    public static final String FEATURE_WIFI = "android.hardware.wifi";

    /** See {@code PackageManager#FEATURE_WIFI_DIRECT}. */
    public static final String FEATURE_WIFI_DIRECT = "android.hardware.wifi.direct";

    /** See {@code PackageManager#FEATURE_WIFI_AWARE}. */
    public static final String FEATURE_WIFI_AWARE = "android.hardware.wifi.aware";

    /** See {@code PackageManager#FEATURE_WIFI_PASSPOINT}. */
    public static final String FEATURE_WIFI_PASSPOINT = "android.hardware.wifi.passpoint";

    /** See {@code PackageManager#FEATURE_WIFI_RTT}. */
    public static final String FEATURE_WIFI_RTT = "android.hardware.wifi.rtt";

    /** See {@code PackageManager#FEATURE_LOWPAN}. */
    public static final String FEATURE_LOWPAN = "android.hardware.lowpan";

    /** See {@code PackageManager#FEATURE_AUTOMOTIVE}. */
    public static final String FEATURE_AUTOMOTIVE = "android.hardware.type.automotive";

    /** See {@code PackageManager#FEATURE_TELEVISION}. */
    public static final String FEATURE_TELEVISION = "android.hardware.type.television";

    /** See {@code PackageManager#FEATURE_WATCH}. */
    public static final String FEATURE_WATCH = "android.hardware.type.watch";

    /** See {@code PackageManager#FEATURE_EMBEDDED}. */
    public static final String FEATURE_EMBEDDED = "android.hardware.type.embedded";

    /** See {@code PackageManager#FEATURE_PC}. */
    public static final String FEATURE_PC = "android.hardware.type.pc";

    /** See {@code PackageManager#FEATURE_PRINTING}. */
    public static final String FEATURE_PRINTING = "android.software.print";

    /** See {@code PackageManager#FEATURE_COMPANION_DEVICE_SETUP}. */
    public static final String FEATURE_COMPANION_DEVICE_SETUP =
            "android.software.companion_device_setup";

    /** See {@code PackageManager#FEATURE_BACKUP}. */
    public static final String FEATURE_BACKUP = "android.software.backup";

    /** See {@code PackageManager#FEATURE_FREEFORM_WINDOW_MANAGEMENT}. */
    public static final String FEATURE_FREEFORM_WINDOW_MANAGEMENT =
            "android.software.freeform_window_management";

    /** See {@code PackageManager#FEATURE_PICTURE_IN_PICTURE}. */
    public static final String FEATURE_PICTURE_IN_PICTURE = "android.software.picture_in_picture";

    /** See {@code PackageManager#FEATURE_ACTIVITIES_ON_SECONDARY_DISPLAYS}. */
    public static final String FEATURE_ACTIVITIES_ON_SECONDARY_DISPLAYS =
            "android.software.activities_on_secondary_displays";

    /** See {@code PackageManager#FEATURE_MANAGED_USERS}. */
    public static final String FEATURE_MANAGED_USERS = "android.software.managed_users";

    /** See {@code PackageManager#FEATURE_MANAGED_PROFILES}. */
    public static final String FEATURE_MANAGED_PROFILES = "android.software.managed_users";

    /** See {@code PackageManager#FEATURE_NEARBY}. */
    public static final String FEATURE_NEARBY = "android.software.nearby";

    /** See {@code PackageManager#FEATURE_VERIFIED_BOOT}. */
    public static final String FEATURE_VERIFIED_BOOT = "android.software.verified_boot";

    /** See {@code PackageManager#FEATURE_SECURELY_REMOVES_USERS}. */
    public static final String FEATURE_SECURELY_REMOVES_USERS =
            "android.software.securely_removes_users";

    /** See {@code PackageManager#FEATURE_FILE_BASED_ENCRYPTION}. */
    public static final String FEATURE_FILE_BASED_ENCRYPTION =
            "android.software.file_based_encryption";

    /** See {@code PackageManager#FEATURE_ADOPTABLE_STORAGE}. */
    public static final String FEATURE_ADOPTABLE_STORAGE =
            "android.software.adoptable_storage";

    /** See {@code PackageManager#FEATURE_WEBVIEW}. */
    public static final String FEATURE_WEBVIEW = "android.software.webview";

    /** See {@code PackageManager#FEATURE_ETHERNET}. */
    public static final String FEATURE_ETHERNET = "android.hardware.ethernet";

    /** See {@code PackageManager#FEATURE_HDMI_CEC}. */
    public static final String FEATURE_HDMI_CEC = "android.hardware.hdmi.cec";

    /** See {@code PackageManager#FEATURE_GAMEPAD}. */
    public static final String FEATURE_GAMEPAD = "android.hardware.gamepad";

    /** See {@code PackageManager#FEATURE_MIDI}. */
    public static final String FEATURE_MIDI = "android.software.midi";

    /** See {@code PackageManager#FEATURE_VR_MODE}. */
    public static final String FEATURE_VR_MODE = "android.software.vr.mode";

    /** See {@code PackageManager#FEATURE_VR_MODE_HIGH_PERFORMANCE}. */
    public static final String FEATURE_VR_MODE_HIGH_PERFORMANCE
            = "android.hardware.vr.high_performance";

    /** See {@code PackageManager#FEATURE_AUTOFILL}. */
    public static final String FEATURE_AUTOFILL = "android.software.autofill";

    /** See {@code PackageManager#FEATURE_VR_HEADTRACKING}. */
    public static final String FEATURE_VR_HEADTRACKING = "android.hardware.vr.headtracking";

    /** See {@code PackageManager#FEATURE_HARDWARE_KEYSTORE}. */
    public static final String FEATURE_HARDWARE_KEYSTORE = "android.hardware.hardware_keystore";

    /** See {@code PackageManager#FEATURE_STRONGBOX_KEYSTORE}. */
    public static final String FEATURE_STRONGBOX_KEYSTORE =
            "android.hardware.strongbox_keystore";

    /** See {@code PackageManager#FEATURE_SLICES_DISABLED}. */
    public static final String FEATURE_SLICES_DISABLED = "android.software.slices_disabled";

    /** See {@code PackageManager#FEATURE_DEVICE_UNIQUE_ATTESTATION}. */
    public static final String FEATURE_DEVICE_UNIQUE_ATTESTATION =
            "android.hardware.device_unique_attestation";

    /** See {@code PackageManager#FEATURE_DEVICE_ID_ATTESTATION}. */
    public static final String FEATURE_DEVICE_ID_ATTESTATION =
            "android.software.device_id_attestation";

    /** See {@code PackageManager#FEATURE_IPSEC_TUNNELS}. */
    public static final String FEATURE_IPSEC_TUNNELS = "android.software.ipsec_tunnels";

    /** See {@code PackageManager#FEATURE_CONTROLS}. */
    public static final String FEATURE_CONTROLS = "android.software.controls";

    /** See {@code PackageManager#FEATURE_REBOOT_ESCROW}. */
    public static final String FEATURE_REBOOT_ESCROW = "android.hardware.reboot_escrow";

    /** See {@code PackageManager#FEATURE_INCREMENTAL_DELIVERY}. */
    public static final String FEATURE_INCREMENTAL_DELIVERY =
            "android.software.incremental_delivery";

    /** See {@code PackageManager#FEATURE_TUNER}. */
    public static final String FEATURE_TUNER = "android.hardware.tv.tuner";

    /** See {@code PackageManager#FEATURE_APP_ENUMERATION}. */
    public static final String FEATURE_APP_ENUMERATION = "android.software.app_enumeration";

    /** See {@code PackageManager#FEATURE_KEYSTORE_SINGLE_USE_KEY}. */
    public static final String FEATURE_KEYSTORE_SINGLE_USE_KEY =
            "android.hardware.keystore.single_use_key";

    /** See {@code PackageManager#FEATURE_KEYSTORE_LIMITED_USE_KEY}. */
    public static final String FEATURE_KEYSTORE_LIMITED_USE_KEY =
            "android.hardware.keystore.limited_use_key";

    /** See {@code PackageManager#FEATURE_KEYSTORE_APP_ATTEST_KEY}. */
    public static final String FEATURE_KEYSTORE_APP_ATTEST_KEY =
            "android.hardware.keystore.app_attest_key";

    /** See {@code PackageManager#FEATURE_APP_COMPAT_OVERRIDES}. */
    public static final String FEATURE_APP_COMPAT_OVERRIDES =
            "android.software.app_compat_overrides";

    /** See {@code PackageManager#FEATURE_COMMUNAL_MODE}. */
    public static final String FEATURE_COMMUNAL_MODE = "android.software.communal_mode";
}
