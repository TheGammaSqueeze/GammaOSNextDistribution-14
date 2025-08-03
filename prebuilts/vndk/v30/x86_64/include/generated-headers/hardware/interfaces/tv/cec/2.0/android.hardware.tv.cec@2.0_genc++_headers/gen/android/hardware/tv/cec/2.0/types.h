#ifndef HIDL_GENERATED_ANDROID_HARDWARE_TV_CEC_V2_0_TYPES_H
#define HIDL_GENERATED_ANDROID_HARDWARE_TV_CEC_V2_0_TYPES_H

#include <hidl/HidlSupport.h>
#include <hidl/MQDescriptor.h>
#include <utils/NativeHandle.h>
#include <utils/misc.h>

namespace android {
namespace hardware {
namespace tv {
namespace cec {
namespace V2_0 {

// Forward declaration for forward reference support:
enum class CecDeviceType : int32_t;
enum class CecLogicalAddress : int32_t;
enum class CecMessageType : int32_t;
enum class AbortReason : int32_t;
enum class MaxLength : int32_t;
struct CecMessage;
enum class SendMessageResult : int32_t;
enum class CecAllDeviceTypeValue : uint8_t;
enum class CecVersion : int32_t;
enum class CecDeviceFeature : uint32_t;
enum class CecRcProfileId : uint8_t;
enum class CecRcProfileSource : uint8_t;
struct CecRcProfile1;
struct CecRcProfile;
enum class CecPowerState : int8_t;
struct CecDeviceInfo;
enum class CecTopologyEventType : int32_t;
struct CecTopologyEvent;
enum class CecUICommandCodes : int32_t;
enum class HdmiPortType : int32_t;
enum class OptionKey : int32_t;
struct HotplugEvent;
struct HdmiPortInfo;
enum class Result : int32_t;

/**
 * CEC device type as specified in CEC Table 11-7 of the CEC spec 2.0b.
 */
enum class CecDeviceType : int32_t {
    INACTIVE = -1 /* -1 */,
    TV = 0,
    RECORDER = 1,
    TUNER = 3,
    PLAYBACK = 4,
    AUDIO_SYSTEM = 5,
    PURE_CEC_SWITCH = 6,
    PROCESSOR = 7,
    MAX = 7 /* PROCESSOR */,
};

/**
 * CEC logical address as specified in CEC Table 11-9 of the CEC spec 2.0b.
 */
enum class CecLogicalAddress : int32_t {
    TV = 0,
    RECORDER_1 = 1,
    RECORDER_2 = 2,
    TUNER_1 = 3,
    PLAYBACK_1 = 4,
    AUDIO_SYSTEM = 5,
    TUNER_2 = 6,
    TUNER_3 = 7,
    PLAYBACK_2 = 8,
    RECORDER_3 = 9,
    TUNER_4 = 10,
    PLAYBACK_3 = 11,
    BACKUP_1 = 12,
    BACKUP_2 = 13,
    SPECIFIC_USE = 14,
    UNREGISTERED = 15,
    BROADCAST = 15,
};

/**
 * HDMI CEC message types.
 *
 * The assigned values represent opcode used in CEC frame as specified in
 * Section 11.10 of the CEC spec 2.0b on top of Section CEC 15 of the CEC
 * Spec 1.4b.
 */
enum class CecMessageType : int32_t {
    FEATURE_ABORT = 0 /* 0x00 */,
    IMAGE_VIEW_ON = 4 /* 0x04 */,
    TUNER_STEP_INCREMENT = 5 /* 0x05 */,
    TUNER_STEP_DECREMENT = 6 /* 0x06 */,
    TUNER_DEVICE_STATUS = 7 /* 0x07 */,
    GIVE_TUNER_DEVICE_STATUS = 8 /* 0x08 */,
    RECORD_ON = 9 /* 0x09 */,
    RECORD_STATUS = 10 /* 0x0A */,
    RECORD_OFF = 11 /* 0x0B */,
    TEXT_VIEW_ON = 13 /* 0x0D */,
    RECORD_TV_SCREEN = 15 /* 0x0F */,
    GIVE_DECK_STATUS = 26 /* 0x1A */,
    DECK_STATUS = 27 /* 0x1B */,
    SET_MENU_LANGUAGE = 50 /* 0x32 */,
    CLEAR_ANALOG_TIMER = 51 /* 0x33 */,
    SET_ANALOG_TIMER = 52 /* 0x34 */,
    TIMER_STATUS = 53 /* 0x35 */,
    STANDBY = 54 /* 0x36 */,
    PLAY = 65 /* 0x41 */,
    DECK_CONTROL = 66 /* 0x42 */,
    TIMER_CLEARED_STATUS = 67 /* 0x43 */,
    USER_CONTROL_PRESSED = 68 /* 0x44 */,
    USER_CONTROL_RELEASED = 69 /* 0x45 */,
    GIVE_OSD_NAME = 70 /* 0x46 */,
    SET_OSD_NAME = 71 /* 0x47 */,
    SET_OSD_STRING = 100 /* 0x64 */,
    SET_TIMER_PROGRAM_TITLE = 103 /* 0x67 */,
    SYSTEM_AUDIO_MODE_REQUEST = 112 /* 0x70 */,
    GIVE_AUDIO_STATUS = 113 /* 0x71 */,
    SET_SYSTEM_AUDIO_MODE = 114 /* 0x72 */,
    REPORT_AUDIO_STATUS = 122 /* 0x7A */,
    GIVE_SYSTEM_AUDIO_MODE_STATUS = 125 /* 0x7D */,
    SYSTEM_AUDIO_MODE_STATUS = 126 /* 0x7E */,
    ROUTING_CHANGE = 128 /* 0x80 */,
    ROUTING_INFORMATION = 129 /* 0x81 */,
    ACTIVE_SOURCE = 130 /* 0x82 */,
    GIVE_PHYSICAL_ADDRESS = 131 /* 0x83 */,
    REPORT_PHYSICAL_ADDRESS = 132 /* 0x84 */,
    REQUEST_ACTIVE_SOURCE = 133 /* 0x85 */,
    SET_STREAM_PATH = 134 /* 0x86 */,
    DEVICE_VENDOR_ID = 135 /* 0x87 */,
    VENDOR_COMMAND = 137 /* 0x89 */,
    VENDOR_REMOTE_BUTTON_DOWN = 138 /* 0x8A */,
    VENDOR_REMOTE_BUTTON_UP = 139 /* 0x8B */,
    GIVE_DEVICE_VENDOR_ID = 140 /* 0x8C */,
    MENU_REQUEST = 141 /* 0x8D */,
    MENU_STATUS = 142 /* 0x8E */,
    GIVE_DEVICE_POWER_STATUS = 143 /* 0x8F */,
    REPORT_POWER_STATUS = 144 /* 0x90 */,
    GET_MENU_LANGUAGE = 145 /* 0x91 */,
    SELECT_ANALOG_SERVICE = 146 /* 0x92 */,
    SELECT_DIGITAL_SERVICE = 147 /* 0x93 */,
    SET_DIGITAL_TIMER = 151 /* 0x97 */,
    CLEAR_DIGITAL_TIMER = 153 /* 0x99 */,
    SET_AUDIO_RATE = 154 /* 0x9A */,
    INACTIVE_SOURCE = 157 /* 0x9D */,
    CEC_VERSION = 158 /* 0x9E */,
    GET_CEC_VERSION = 159 /* 0x9F */,
    VENDOR_COMMAND_WITH_ID = 160 /* 0xA0 */,
    CLEAR_EXTERNAL_TIMER = 161 /* 0xA1 */,
    SET_EXTERNAL_TIMER = 162 /* 0xA2 */,
    REPORT_SHORT_AUDIO_DESCRIPTOR = 163 /* 0xA3 */,
    REQUEST_SHORT_AUDIO_DESCRIPTOR = 164 /* 0xA4 */,
    GIVE_FEATURES = 165 /* 0XA5 */,
    REPORT_FEATURES = 166 /* 0xA6 */,
    REQUEST_CURRENT_LATENCY = 167 /* 0xA7 */,
    REPORT_CURRENT_LATENCY = 168 /* 0xA8 */,
    INITIATE_ARC = 192 /* 0xC0 */,
    REPORT_ARC_INITIATED = 193 /* 0xC1 */,
    REPORT_ARC_TERMINATED = 194 /* 0xC2 */,
    REQUEST_ARC_INITIATION = 195 /* 0xC3 */,
    REQUEST_ARC_TERMINATION = 196 /* 0xC4 */,
    TERMINATE_ARC = 197 /* 0xC5 */,
    ABORT = 255 /* 0xFF */,
    POLLING_MESSAGE = -256 /* 0xFFFFFF00 */,
};

/**
 * Abort Reason as specified in CEC Table 29 of the CEC spec 1.4b.
 */
enum class AbortReason : int32_t {
    UNRECOGNIZED_MODE = 0,
    NOT_IN_CORRECT_MODE = 1,
    CANNOT_PROVIDE_SOURCE = 2,
    INVALID_OPERAND = 3,
    REFUSED = 4,
    UNABLE_TO_DETERMINE = 5,
};

enum class MaxLength : int32_t {
    MESSAGE_BODY = 14,
};

struct CecMessage final {
    /**
     * logical address of sender
     */
    ::android::hardware::tv::cec::V2_0::CecLogicalAddress initiator __attribute__ ((aligned(4)));
    /**
     * logical address of receiver
     */
    ::android::hardware::tv::cec::V2_0::CecLogicalAddress destination __attribute__ ((aligned(4)));
    /**
     * cec message type
     */
    ::android::hardware::tv::cec::V2_0::CecMessageType cecMessageType __attribute__ ((aligned(4)));
    /**
     * The maximum size of body is 14 (MaxLength::MESSAGE_BODY) as specified in
     * the section 6 of the CEC Spec 1.4b. Overflowed data must be ignored.
     */
    ::android::hardware::hidl_vec<uint8_t> body __attribute__ ((aligned(8)));
};

static_assert(offsetof(::android::hardware::tv::cec::V2_0::CecMessage, initiator) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::tv::cec::V2_0::CecMessage, destination) == 4, "wrong offset");
static_assert(offsetof(::android::hardware::tv::cec::V2_0::CecMessage, cecMessageType) == 8, "wrong offset");
static_assert(offsetof(::android::hardware::tv::cec::V2_0::CecMessage, body) == 16, "wrong offset");
static_assert(sizeof(::android::hardware::tv::cec::V2_0::CecMessage) == 32, "wrong size");
static_assert(__alignof(::android::hardware::tv::cec::V2_0::CecMessage) == 8, "wrong alignment");

/**
 * error code used for send_message.
 */
enum class SendMessageResult : int32_t {
    SUCCESS = 0,
    NACK = 1,
    BUSY = 2,
    FAIL = 3,
};

/**
 * CEC All Device Type Value as specified in Table 11-30 of the CEC spec 2.0b.
 */
enum class CecAllDeviceTypeValue : uint8_t {
    RESERVED_DEVICE_2 = 1 /* 1 << 0 */,
    RESERVED_DEVICE_1 = 2 /* 1 << 1 */,
    CEC_SWITCH_DEVICE = 4 /* 1 << 2 */,
    AUDIO_DEVICE = 8 /* 1 << 3 */,
    PLAYBACK_DEVICE = 16 /* 1 << 4 */,
    TUNER_DEVICE = 32 /* 1 << 5 */,
    RECORDING_DEVICE = 64 /* 1 << 6 */,
    TV_DEVICE = 128 /* 1 << 7 */,
};

/**
 * CEC All Device Types
 *
 * It is a combination of all supported type from CecAllDeviceTypeValue.
 * For example a record with tuner functionalitye,
 * cecAllDeviceTypes = ((CecAllDeviceTypeValue::RECORDING_DEVICE)
 *                     |(CecAllDeviceTypeValue::TUNER_DEVICE))
 */
typedef ::android::hardware::hidl_bitfield<::android::hardware::tv::cec::V2_0::CecAllDeviceTypeValue> CecAllDeviceTypes;

/**
 * CEC Versions as specified in CEC Table 11-30 of the CEC spec 2.0b.
 */
enum class CecVersion : int32_t {
    V_1_3_A = 4 /* 0x04 */,
    V_1_4 = 5 /* 0x05 */,
    V_2_0 = 6 /* 0x06 */,
};

/**
 * Device Feature
 *
 * It is specified in CEC Table 11-30 of the CEC spec 2.0b. As a uint32 there
 * is room for future extensions aka DeviceFeature2 through DeviceFeature4.
 */
enum class CecDeviceFeature : uint32_t {
    RESERVED = 1u /* 1 << 0 */,
    SOURCE_SUPPORT_ARC_RX = 2u /* 1 << 1 */,
    SINK_SUPPORT_ARC_TX = 4u /* 1 << 2 */,
    SOURCE_SUPPORT_SET_AUDIO_RATE = 8u /* 1 << 3 */,
    SUPPORT_CONTROLLED_BY_DECK = 16u /* 1 << 4 */,
    TV_SUPPORT_SET_OSD_STRINGS = 32u /* 1 << 5 */,
    TV_SUPPORT_RECORD_TV_SCREEN = 64u /* 1 << 6 */,
};

/**
 * CEC Device Features
 *
 * It is a combination of all supported features from CecDeviceFeature.
 * For example a TV with OSD and ARC capabilities,
 *   CecDeviceFeatures = ((CecDeviceFeature::TV_SUPPORT_SET_OSD_STRINGS)
 *                       |(CecDeviceFeature::SINK_SUPPORT_ARC_TX))
 */
typedef ::android::hardware::hidl_bitfield<::android::hardware::tv::cec::V2_0::CecDeviceFeature> CecDeviceFeatures;

/**
 * Remote Control Profile
 *
 * It is specified in CEC Table 11-30 of the CEC spec 2.0b.
 */
enum class CecRcProfileId : uint8_t {
    NONE = 0,
    RC_PROFILE_1 = 2 /* 0x02 */,
    RC_PROFILE_2 = 6 /* 0x06 */,
    RC_PROFILE_3 = 10 /* 0x0A */,
    RC_PROFILE_4 = 14 /* 0x0E */,
};

/**
 * Remote Control Profile Source
 *
 * It is specified in CEC Table 11-30 of the CEC spec 2.0b.
 */
enum class CecRcProfileSource : uint8_t {
    MEDIA_CONTEXT_SENSITIVE = 1 /* 1 << 0 */,
    MEDIA_TO = 2 /* 1 << 1 */,
    CONTENTS = 4 /* 1 << 2 */,
    DEVICE_SETUP = 8 /* 1 << 3 */,
    DEVICE_ROOT = 16 /* 1 << 4 */,
    SOURCE_FLAG = 64 /* 1 << 6 */,
};

/**
 * Remote Control Profile for either TV or Source.
 */
struct CecRcProfile1 final {
    enum class hidl_discriminator : uint8_t {
        /**
         * CEC remote control profile for TV.
         */
        profileId = 0,  // ::android::hardware::tv::cec::V2_0::CecRcProfileId
        /*
         * CEC remote control profile for source
         *
         * It is a combination of all supported profiles from CecRcProfileSource.
         * For example a playback device support root menu and setup menu,
         * profileSource = ((CecRcProfileSource::DEVICE_ROOT)
         *                  |(CecRcProfileSource::DEVICE_SETUP)
         *                  |(CecRcProfileSource::SOURCE_FLAG))
         */
        profileSource = 1,  // ::android::hardware::hidl_bitfield<::android::hardware::tv::cec::V2_0::CecRcProfileSource>
    };

    CecRcProfile1();
    ~CecRcProfile1();
    CecRcProfile1(CecRcProfile1&&);
    CecRcProfile1(const CecRcProfile1&);
    CecRcProfile1& operator=(CecRcProfile1&&);
    CecRcProfile1& operator=(const CecRcProfile1&);

    void profileId(::android::hardware::tv::cec::V2_0::CecRcProfileId);
    ::android::hardware::tv::cec::V2_0::CecRcProfileId& profileId();
    ::android::hardware::tv::cec::V2_0::CecRcProfileId profileId() const;

    void profileSource(::android::hardware::hidl_bitfield<::android::hardware::tv::cec::V2_0::CecRcProfileSource>);
    ::android::hardware::hidl_bitfield<::android::hardware::tv::cec::V2_0::CecRcProfileSource>& profileSource();
    ::android::hardware::hidl_bitfield<::android::hardware::tv::cec::V2_0::CecRcProfileSource> profileSource() const;

    // Utility methods
    hidl_discriminator getDiscriminator() const;

    constexpr size_t hidl_getUnionOffset() const {
        return offsetof(::android::hardware::tv::cec::V2_0::CecRcProfile1, hidl_u);
    }

private:
    void hidl_destructUnion();

    hidl_discriminator hidl_d __attribute__ ((aligned(1))) ;
    union hidl_union final {
        ::android::hardware::tv::cec::V2_0::CecRcProfileId profileId __attribute__ ((aligned(1)));
        ::android::hardware::hidl_bitfield<::android::hardware::tv::cec::V2_0::CecRcProfileSource> profileSource __attribute__ ((aligned(1)));

        hidl_union();
        ~hidl_union();
    } hidl_u;

    static_assert(sizeof(::android::hardware::tv::cec::V2_0::CecRcProfile1::hidl_union) == 1, "wrong size");
    static_assert(__alignof(::android::hardware::tv::cec::V2_0::CecRcProfile1::hidl_union) == 1, "wrong alignment");
    static_assert(sizeof(::android::hardware::tv::cec::V2_0::CecRcProfile1::hidl_discriminator) == 1, "wrong size");
    static_assert(__alignof(::android::hardware::tv::cec::V2_0::CecRcProfile1::hidl_discriminator) == 1, "wrong alignment");
};

static_assert(sizeof(::android::hardware::tv::cec::V2_0::CecRcProfile1) == 2, "wrong size");
static_assert(__alignof(::android::hardware::tv::cec::V2_0::CecRcProfile1) == 1, "wrong alignment");

/**
 * CEC Remote Control Profiles
 *
 * CEC 2.0 only use one byte to represent Remote Control Profile.
 */
struct CecRcProfile final {
    ::android::hardware::tv::cec::V2_0::CecRcProfile1 rcProfile1 __attribute__ ((aligned(1)));
};

static_assert(offsetof(::android::hardware::tv::cec::V2_0::CecRcProfile, rcProfile1) == 0, "wrong offset");
static_assert(sizeof(::android::hardware::tv::cec::V2_0::CecRcProfile) == 2, "wrong size");
static_assert(__alignof(::android::hardware::tv::cec::V2_0::CecRcProfile) == 1, "wrong alignment");

/**
 * CEC device power states as specified in CEC Table 11-10 of the CEC spec 2.0b
 */
enum class CecPowerState : int8_t {
    ON = 0,
    STANDBY = 1,
    ON_TO_STANDBY = 2,
    STANDBY_TO_ON = 4,
    UNKNOWN = -1 /* 0xFF */,
};

/**
 * CEC physical address of device
 */
typedef uint16_t CecPhysicalAddress;

/**
 * CEC device information
 *
 * It is initially built during addressing specified in CEC section 11.3 of
 * the CEC spec 2.0b. It may be updated with cec devices's status changed.
 */
struct CecDeviceInfo final {
    /**
     * CEC version which device supports
     */
    ::android::hardware::tv::cec::V2_0::CecVersion version __attribute__ ((aligned(4)));
    /**
     * CEC device primary type
     */
    ::android::hardware::tv::cec::V2_0::CecDeviceType devceType __attribute__ ((aligned(4)));
    /**
     * CEC all device types
     */
    ::android::hardware::hidl_bitfield<::android::hardware::tv::cec::V2_0::CecAllDeviceTypeValue> allDeviceTypes __attribute__ ((aligned(1)));
    /**
     * CEC device features
     */
    ::android::hardware::hidl_bitfield<::android::hardware::tv::cec::V2_0::CecDeviceFeature> deviceFeatures __attribute__ ((aligned(4)));
    /**
     * CEC Device Remote Control Profile
     */
    ::android::hardware::tv::cec::V2_0::CecRcProfile rcProfile __attribute__ ((aligned(1)));
    /**
     * CEC Device Vendor ID
     */
    uint32_t vendorId __attribute__ ((aligned(4)));
    /**
     * logical address of device
     */
    ::android::hardware::tv::cec::V2_0::CecLogicalAddress logicalAddress __attribute__ ((aligned(4)));
    /**
     * physical of device
     */
    uint16_t physicalAddress __attribute__ ((aligned(2)));
    /**
     * power status of device
     */
    ::android::hardware::tv::cec::V2_0::CecPowerState powerState __attribute__ ((aligned(1)));
};

static_assert(offsetof(::android::hardware::tv::cec::V2_0::CecDeviceInfo, version) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::tv::cec::V2_0::CecDeviceInfo, devceType) == 4, "wrong offset");
static_assert(offsetof(::android::hardware::tv::cec::V2_0::CecDeviceInfo, allDeviceTypes) == 8, "wrong offset");
static_assert(offsetof(::android::hardware::tv::cec::V2_0::CecDeviceInfo, deviceFeatures) == 12, "wrong offset");
static_assert(offsetof(::android::hardware::tv::cec::V2_0::CecDeviceInfo, rcProfile) == 16, "wrong offset");
static_assert(offsetof(::android::hardware::tv::cec::V2_0::CecDeviceInfo, vendorId) == 20, "wrong offset");
static_assert(offsetof(::android::hardware::tv::cec::V2_0::CecDeviceInfo, logicalAddress) == 24, "wrong offset");
static_assert(offsetof(::android::hardware::tv::cec::V2_0::CecDeviceInfo, physicalAddress) == 28, "wrong offset");
static_assert(offsetof(::android::hardware::tv::cec::V2_0::CecDeviceInfo, powerState) == 30, "wrong offset");
static_assert(sizeof(::android::hardware::tv::cec::V2_0::CecDeviceInfo) == 32, "wrong size");
static_assert(__alignof(::android::hardware::tv::cec::V2_0::CecDeviceInfo) == 4, "wrong alignment");

/**
 * Topology Event Type.
 */
enum class CecTopologyEventType : int32_t {
    DEVICE_ADDED = 0,
    DEVICE_REMOVED = 1 /* ::android::hardware::tv::cec::V2_0::CecTopologyEventType.DEVICE_ADDED implicitly + 1 */,
    DEVICE_UPDATED = 2 /* ::android::hardware::tv::cec::V2_0::CecTopologyEventType.DEVICE_REMOVED implicitly + 1 */,
};

/**
 * Topology Event.
 */
struct CecTopologyEvent final {
    ::android::hardware::tv::cec::V2_0::CecTopologyEventType eventType __attribute__ ((aligned(4)));
    ::android::hardware::tv::cec::V2_0::CecLogicalAddress logicalAddress __attribute__ ((aligned(4)));
    uint16_t physicalAddress __attribute__ ((aligned(2)));
    /**
     * true if the event is about the device which the system run on
     */
    bool isHostDevice __attribute__ ((aligned(1)));
};

static_assert(offsetof(::android::hardware::tv::cec::V2_0::CecTopologyEvent, eventType) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::tv::cec::V2_0::CecTopologyEvent, logicalAddress) == 4, "wrong offset");
static_assert(offsetof(::android::hardware::tv::cec::V2_0::CecTopologyEvent, physicalAddress) == 8, "wrong offset");
static_assert(offsetof(::android::hardware::tv::cec::V2_0::CecTopologyEvent, isHostDevice) == 10, "wrong offset");
static_assert(sizeof(::android::hardware::tv::cec::V2_0::CecTopologyEvent) == 12, "wrong size");
static_assert(__alignof(::android::hardware::tv::cec::V2_0::CecTopologyEvent) == 4, "wrong alignment");

/**
 * CEC UI Command Codes as specified in CEC Table 11-31 of the CEC spec 2.0b
 */
enum class CecUICommandCodes : int32_t {
    SELECT_OK = 0 /* 0x00 */,
    UP = 1 /* 0x01 */,
    DOWN = 2 /* 0x02 */,
    LEFT = 3 /* 0x03 */,
    RIGHT = 4 /* 0x04 */,
    RIGHT_UP = 5 /* 0x05 */,
    RIGHT_DOWN = 6 /* 0x06 */,
    LEFT_UP = 7 /* 0x07 */,
    LEFT_DOWN = 8 /* 0x08 */,
    DEVICE_ROOT_MENU = 9 /* 0x09 */,
    DEVICE_SETUP_MENU = 10 /* 0x0A */,
    CONTENTS_MENU = 11 /* 0x0B */,
    FAVORITE_MENU = 12 /* 0x0C */,
    BACK = 13 /* 0x0D */,
    MEDIA_TOP_MENU = 16 /* 0x10 */,
    MEDIA_CONTEXT_SENSITIVE_MENU = 17 /* 0x11 */,
    NUMBER_ENTRY_MODE = 29 /* 0x1D */,
    NUMBER_11 = 30 /* 0x1E */,
    NUMBER_12 = 31 /* 0x1F */,
    NUMBER_0 = 32 /* 0x20 */,
    NUMBER_1 = 33 /* 0x21 */,
    NUMBER_2 = 34 /* 0x22 */,
    NUMBER_3 = 35 /* 0x23 */,
    NUMBER_4 = 36 /* 0x24 */,
    NUMBER_5 = 37 /* 0x25 */,
    NUMBER_6 = 38 /* 0x26 */,
    NUMBER_7 = 39 /* 0x27 */,
    NUMBER_8 = 40 /* 0x28 */,
    NUMBER_9 = 41 /* 0x29 */,
    DOT = 42 /* 0x2A */,
    ENTER = 43 /* 0x2B */,
    CLEAR = 44 /* 0x2C */,
    NEXT_FAVORITE = 47 /* 0x2F */,
    CHANNEL_UP = 48 /* 0x30 */,
    CHANNEL_DOWN = 49 /* 0x31 */,
    PREVIOUS_CHANNEL = 50 /* 0x32 */,
    SOUND_SELECT = 51 /* 0x33 */,
    INPUT_SELECT = 52 /* 0x34 */,
    DISPLAY_INFORMATION = 53 /* 0x35 */,
    HELP = 54 /* 0x36 */,
    PAGE_UP = 55 /* 0x37 */,
    PAGE_DOWN = 56 /* 0x38 */,
    POWER = 64 /* 0x40 */,
    VOLUME_UP = 65 /* 0x41 */,
    VOLUME_DOWN = 66 /* 0x42 */,
    MUTE = 67 /* 0x43 */,
    PLAY = 68 /* 0x44 */,
    STOP = 69 /* 0x45 */,
    PAUSE = 70 /* 0x46 */,
    RECORD = 71 /* 0x47 */,
    REWIND = 72 /* 0x48 */,
    FAST_FORWARD = 73 /* 0x49 */,
    EJECT = 74 /* 0x4A */,
    SKIP_FORWARD = 75 /* 0x4B */,
    SKIP_BACKWARD = 76 /* 0x4C */,
    STOP_RECORD = 77 /* 0x4D */,
    PAUSE_RECORD = 78 /* 0x4E */,
    ANGLE = 80 /* 0x50 */,
    SUB_PICTURE = 81 /* 0x51 */,
    VIDEO_ON_DEMAND = 82 /* 0x52 */,
    ELECTRONIC_PROGRAM_GUIDE = 83 /* 0x53 */,
    TIMER_PROGRAMMING = 84 /* 0x54 */,
    INITIAL_CONFIGURATION = 85 /* 0x55 */,
    SELECT_BROADCAST_TYPE = 86 /* 0x56 */,
    SELECT_SOUND_PRESENTATION = 87 /* 0x57 */,
    AUDIO_DESCRIPTION = 88 /* 0x58 */,
    INTERNET = 89 /* 0x59 */,
    THREE_DIMENSIONAL_MODE = 90 /* 0x5A */,
    PLAY_FUNCTION = 96 /* 0x60 */,
    PAUSE_PLAY_FUNCTION = 97 /* 0x61 */,
    RECORD_FUNCTION = 98 /* 0x62 */,
    PAUSE_RECORD_FUNCTION = 99 /* 0x63 */,
    STOP_FUNCTION = 100 /* 0x64 */,
    MUTE_FUNCTION = 101 /* 0x65 */,
    RESTORE_VOLUME_FUNCTION = 102 /* 0x66 */,
    TUNE_FUNCTION = 103 /* 0x67 */,
    SELECT_MEDIA_FUNCTION = 104 /* 0x68 */,
    SELECT_AV_INPUT_FUNCTION = 105 /* 0x69 */,
    SELECT_AUDIO_INPUT_FUNCTION = 106 /* 0x6A */,
    POWER_TOGGLE_FUNCTION = 107 /* 0x6B */,
    POWER_OFF_FUNCTION = 108 /* 0x6C */,
    POWER_ON_FUNCTION = 109 /* 0x6D */,
    F1 = 113 /* 0x71 */,
    F2 = 114 /* 0x72 */,
    F3 = 115 /* 0x73 */,
    F4 = 116 /* 0x74 */,
    F5 = 117 /* 0x75 */,
    DATA = 118 /* 0x76 */,
};

/**
 * HDMI port type.
 */
enum class HdmiPortType : int32_t {
    INPUT = 0,
    OUTPUT = 1,
};

/**
 * Options used for IHdmiCec.setOption()
 */
enum class OptionKey : int32_t {
    /**
     * When set to false, HAL does not wake up the system upon receiving <Image
     * View On> or <Text View On>. Used when user changes the TV settings to
     * disable the auto TV on functionality.
     * Deprecated since <Image View On> and <Text View On> become mandatory
     * featrues for CEC device. Use ENABLE_CEC OptionKey to disable CEC
     * functionality instead.
     * True by Default
     */
    WAKEUP = 1,
    /**
     * When set to false, all the CEC commands are discarded. if logical address
     * is ever used, it shall be released. Used when user changes the TV
     * settings to disable CEC functionality.
     * True by default.
     *
     */
    ENABLE_CEC = 2,
    /**
     * Setting this flag to false means Android system must stop handling CEC
     * service and yield the control over to the microprocessor that is powered
     * on through the standby mode.The microprocessor shall keep current logical
     * and physical address. It shall response POLLING_MESSAGE, GIVE_FEATURES,
     * GIVE_DEVICE_POWER_STATUS,GIVE_DEVICE_VENDOR_ID and GIVE_PHYSICAL_ADDRESS
     * to allow other CEC devices to build CEC devices map specified in CEC
     * section 11.3 of the CEC spec 2.0b.
     * When set to true, the system must gain the control over, hence telling
     * the microprocessor to start forwarding CEC messages to Android system.
     * For example, this may be called when system goes in and out of
     * standby mode to notify the microprocessor that it should start/stop
     * handling CEC commands on behalf of the system.
     * True by default.
     */
    SYSTEM_CEC_CONTROL = 3,
};

/**
 * Hdmi port ID.
 *
 * It shall start from 1 which corresponds to HDMI "port 1".
 */
typedef uint32_t HdmiPortId;

/**
 * Hdmi hotplug event
 */
struct HotplugEvent final {
    bool connected __attribute__ ((aligned(1)));
    uint32_t portId __attribute__ ((aligned(4)));
};

static_assert(offsetof(::android::hardware::tv::cec::V2_0::HotplugEvent, connected) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::tv::cec::V2_0::HotplugEvent, portId) == 4, "wrong offset");
static_assert(sizeof(::android::hardware::tv::cec::V2_0::HotplugEvent) == 8, "wrong size");
static_assert(__alignof(::android::hardware::tv::cec::V2_0::HotplugEvent) == 4, "wrong alignment");

/**
 * HDMI port descriptor
 */
struct HdmiPortInfo final {
    ::android::hardware::tv::cec::V2_0::HdmiPortType type __attribute__ ((aligned(4)));
    uint32_t portId __attribute__ ((aligned(4)));
    bool cecSupported __attribute__ ((aligned(1)));
    bool arcSupported __attribute__ ((aligned(1)));
    uint16_t physicalAddress __attribute__ ((aligned(2)));
};

static_assert(offsetof(::android::hardware::tv::cec::V2_0::HdmiPortInfo, type) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::tv::cec::V2_0::HdmiPortInfo, portId) == 4, "wrong offset");
static_assert(offsetof(::android::hardware::tv::cec::V2_0::HdmiPortInfo, cecSupported) == 8, "wrong offset");
static_assert(offsetof(::android::hardware::tv::cec::V2_0::HdmiPortInfo, arcSupported) == 9, "wrong offset");
static_assert(offsetof(::android::hardware::tv::cec::V2_0::HdmiPortInfo, physicalAddress) == 10, "wrong offset");
static_assert(sizeof(::android::hardware::tv::cec::V2_0::HdmiPortInfo) == 12, "wrong size");
static_assert(__alignof(::android::hardware::tv::cec::V2_0::HdmiPortInfo) == 4, "wrong alignment");

enum class Result : int32_t {
    SUCCESS = 0,
    FAILURE_UNKNOWN = 1,
    FAILURE_INVALID_ARGS = 2,
    FAILURE_INVALID_STATE = 3,
    FAILURE_NOT_SUPPORTED = 4,
    FAILURE_BUSY = 5,
};

//
// type declarations for package
//

template<typename>
static inline std::string toString(int32_t o);
static inline std::string toString(::android::hardware::tv::cec::V2_0::CecDeviceType o);
static inline void PrintTo(::android::hardware::tv::cec::V2_0::CecDeviceType o, ::std::ostream* os);
constexpr int32_t operator|(const ::android::hardware::tv::cec::V2_0::CecDeviceType lhs, const ::android::hardware::tv::cec::V2_0::CecDeviceType rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const int32_t lhs, const ::android::hardware::tv::cec::V2_0::CecDeviceType rhs) {
    return static_cast<int32_t>(lhs | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const ::android::hardware::tv::cec::V2_0::CecDeviceType lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | rhs);
}
constexpr int32_t operator&(const ::android::hardware::tv::cec::V2_0::CecDeviceType lhs, const ::android::hardware::tv::cec::V2_0::CecDeviceType rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const int32_t lhs, const ::android::hardware::tv::cec::V2_0::CecDeviceType rhs) {
    return static_cast<int32_t>(lhs & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const ::android::hardware::tv::cec::V2_0::CecDeviceType lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & rhs);
}
constexpr int32_t &operator|=(int32_t& v, const ::android::hardware::tv::cec::V2_0::CecDeviceType e) {
    v |= static_cast<int32_t>(e);
    return v;
}
constexpr int32_t &operator&=(int32_t& v, const ::android::hardware::tv::cec::V2_0::CecDeviceType e) {
    v &= static_cast<int32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(int32_t o);
static inline std::string toString(::android::hardware::tv::cec::V2_0::CecLogicalAddress o);
static inline void PrintTo(::android::hardware::tv::cec::V2_0::CecLogicalAddress o, ::std::ostream* os);
constexpr int32_t operator|(const ::android::hardware::tv::cec::V2_0::CecLogicalAddress lhs, const ::android::hardware::tv::cec::V2_0::CecLogicalAddress rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const int32_t lhs, const ::android::hardware::tv::cec::V2_0::CecLogicalAddress rhs) {
    return static_cast<int32_t>(lhs | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const ::android::hardware::tv::cec::V2_0::CecLogicalAddress lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | rhs);
}
constexpr int32_t operator&(const ::android::hardware::tv::cec::V2_0::CecLogicalAddress lhs, const ::android::hardware::tv::cec::V2_0::CecLogicalAddress rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const int32_t lhs, const ::android::hardware::tv::cec::V2_0::CecLogicalAddress rhs) {
    return static_cast<int32_t>(lhs & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const ::android::hardware::tv::cec::V2_0::CecLogicalAddress lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & rhs);
}
constexpr int32_t &operator|=(int32_t& v, const ::android::hardware::tv::cec::V2_0::CecLogicalAddress e) {
    v |= static_cast<int32_t>(e);
    return v;
}
constexpr int32_t &operator&=(int32_t& v, const ::android::hardware::tv::cec::V2_0::CecLogicalAddress e) {
    v &= static_cast<int32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(int32_t o);
static inline std::string toString(::android::hardware::tv::cec::V2_0::CecMessageType o);
static inline void PrintTo(::android::hardware::tv::cec::V2_0::CecMessageType o, ::std::ostream* os);
constexpr int32_t operator|(const ::android::hardware::tv::cec::V2_0::CecMessageType lhs, const ::android::hardware::tv::cec::V2_0::CecMessageType rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const int32_t lhs, const ::android::hardware::tv::cec::V2_0::CecMessageType rhs) {
    return static_cast<int32_t>(lhs | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const ::android::hardware::tv::cec::V2_0::CecMessageType lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | rhs);
}
constexpr int32_t operator&(const ::android::hardware::tv::cec::V2_0::CecMessageType lhs, const ::android::hardware::tv::cec::V2_0::CecMessageType rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const int32_t lhs, const ::android::hardware::tv::cec::V2_0::CecMessageType rhs) {
    return static_cast<int32_t>(lhs & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const ::android::hardware::tv::cec::V2_0::CecMessageType lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & rhs);
}
constexpr int32_t &operator|=(int32_t& v, const ::android::hardware::tv::cec::V2_0::CecMessageType e) {
    v |= static_cast<int32_t>(e);
    return v;
}
constexpr int32_t &operator&=(int32_t& v, const ::android::hardware::tv::cec::V2_0::CecMessageType e) {
    v &= static_cast<int32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(int32_t o);
static inline std::string toString(::android::hardware::tv::cec::V2_0::AbortReason o);
static inline void PrintTo(::android::hardware::tv::cec::V2_0::AbortReason o, ::std::ostream* os);
constexpr int32_t operator|(const ::android::hardware::tv::cec::V2_0::AbortReason lhs, const ::android::hardware::tv::cec::V2_0::AbortReason rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const int32_t lhs, const ::android::hardware::tv::cec::V2_0::AbortReason rhs) {
    return static_cast<int32_t>(lhs | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const ::android::hardware::tv::cec::V2_0::AbortReason lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | rhs);
}
constexpr int32_t operator&(const ::android::hardware::tv::cec::V2_0::AbortReason lhs, const ::android::hardware::tv::cec::V2_0::AbortReason rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const int32_t lhs, const ::android::hardware::tv::cec::V2_0::AbortReason rhs) {
    return static_cast<int32_t>(lhs & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const ::android::hardware::tv::cec::V2_0::AbortReason lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & rhs);
}
constexpr int32_t &operator|=(int32_t& v, const ::android::hardware::tv::cec::V2_0::AbortReason e) {
    v |= static_cast<int32_t>(e);
    return v;
}
constexpr int32_t &operator&=(int32_t& v, const ::android::hardware::tv::cec::V2_0::AbortReason e) {
    v &= static_cast<int32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(int32_t o);
static inline std::string toString(::android::hardware::tv::cec::V2_0::MaxLength o);
static inline void PrintTo(::android::hardware::tv::cec::V2_0::MaxLength o, ::std::ostream* os);
constexpr int32_t operator|(const ::android::hardware::tv::cec::V2_0::MaxLength lhs, const ::android::hardware::tv::cec::V2_0::MaxLength rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const int32_t lhs, const ::android::hardware::tv::cec::V2_0::MaxLength rhs) {
    return static_cast<int32_t>(lhs | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const ::android::hardware::tv::cec::V2_0::MaxLength lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | rhs);
}
constexpr int32_t operator&(const ::android::hardware::tv::cec::V2_0::MaxLength lhs, const ::android::hardware::tv::cec::V2_0::MaxLength rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const int32_t lhs, const ::android::hardware::tv::cec::V2_0::MaxLength rhs) {
    return static_cast<int32_t>(lhs & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const ::android::hardware::tv::cec::V2_0::MaxLength lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & rhs);
}
constexpr int32_t &operator|=(int32_t& v, const ::android::hardware::tv::cec::V2_0::MaxLength e) {
    v |= static_cast<int32_t>(e);
    return v;
}
constexpr int32_t &operator&=(int32_t& v, const ::android::hardware::tv::cec::V2_0::MaxLength e) {
    v &= static_cast<int32_t>(e);
    return v;
}

static inline std::string toString(const ::android::hardware::tv::cec::V2_0::CecMessage& o);
static inline void PrintTo(const ::android::hardware::tv::cec::V2_0::CecMessage& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::tv::cec::V2_0::CecMessage& lhs, const ::android::hardware::tv::cec::V2_0::CecMessage& rhs);
static inline bool operator!=(const ::android::hardware::tv::cec::V2_0::CecMessage& lhs, const ::android::hardware::tv::cec::V2_0::CecMessage& rhs);

template<typename>
static inline std::string toString(int32_t o);
static inline std::string toString(::android::hardware::tv::cec::V2_0::SendMessageResult o);
static inline void PrintTo(::android::hardware::tv::cec::V2_0::SendMessageResult o, ::std::ostream* os);
constexpr int32_t operator|(const ::android::hardware::tv::cec::V2_0::SendMessageResult lhs, const ::android::hardware::tv::cec::V2_0::SendMessageResult rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const int32_t lhs, const ::android::hardware::tv::cec::V2_0::SendMessageResult rhs) {
    return static_cast<int32_t>(lhs | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const ::android::hardware::tv::cec::V2_0::SendMessageResult lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | rhs);
}
constexpr int32_t operator&(const ::android::hardware::tv::cec::V2_0::SendMessageResult lhs, const ::android::hardware::tv::cec::V2_0::SendMessageResult rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const int32_t lhs, const ::android::hardware::tv::cec::V2_0::SendMessageResult rhs) {
    return static_cast<int32_t>(lhs & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const ::android::hardware::tv::cec::V2_0::SendMessageResult lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & rhs);
}
constexpr int32_t &operator|=(int32_t& v, const ::android::hardware::tv::cec::V2_0::SendMessageResult e) {
    v |= static_cast<int32_t>(e);
    return v;
}
constexpr int32_t &operator&=(int32_t& v, const ::android::hardware::tv::cec::V2_0::SendMessageResult e) {
    v &= static_cast<int32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(uint8_t o);
static inline std::string toString(::android::hardware::tv::cec::V2_0::CecAllDeviceTypeValue o);
static inline void PrintTo(::android::hardware::tv::cec::V2_0::CecAllDeviceTypeValue o, ::std::ostream* os);
constexpr uint8_t operator|(const ::android::hardware::tv::cec::V2_0::CecAllDeviceTypeValue lhs, const ::android::hardware::tv::cec::V2_0::CecAllDeviceTypeValue rhs) {
    return static_cast<uint8_t>(static_cast<uint8_t>(lhs) | static_cast<uint8_t>(rhs));
}
constexpr uint8_t operator|(const uint8_t lhs, const ::android::hardware::tv::cec::V2_0::CecAllDeviceTypeValue rhs) {
    return static_cast<uint8_t>(lhs | static_cast<uint8_t>(rhs));
}
constexpr uint8_t operator|(const ::android::hardware::tv::cec::V2_0::CecAllDeviceTypeValue lhs, const uint8_t rhs) {
    return static_cast<uint8_t>(static_cast<uint8_t>(lhs) | rhs);
}
constexpr uint8_t operator&(const ::android::hardware::tv::cec::V2_0::CecAllDeviceTypeValue lhs, const ::android::hardware::tv::cec::V2_0::CecAllDeviceTypeValue rhs) {
    return static_cast<uint8_t>(static_cast<uint8_t>(lhs) & static_cast<uint8_t>(rhs));
}
constexpr uint8_t operator&(const uint8_t lhs, const ::android::hardware::tv::cec::V2_0::CecAllDeviceTypeValue rhs) {
    return static_cast<uint8_t>(lhs & static_cast<uint8_t>(rhs));
}
constexpr uint8_t operator&(const ::android::hardware::tv::cec::V2_0::CecAllDeviceTypeValue lhs, const uint8_t rhs) {
    return static_cast<uint8_t>(static_cast<uint8_t>(lhs) & rhs);
}
constexpr uint8_t &operator|=(uint8_t& v, const ::android::hardware::tv::cec::V2_0::CecAllDeviceTypeValue e) {
    v |= static_cast<uint8_t>(e);
    return v;
}
constexpr uint8_t &operator&=(uint8_t& v, const ::android::hardware::tv::cec::V2_0::CecAllDeviceTypeValue e) {
    v &= static_cast<uint8_t>(e);
    return v;
}

template<typename>
static inline std::string toString(int32_t o);
static inline std::string toString(::android::hardware::tv::cec::V2_0::CecVersion o);
static inline void PrintTo(::android::hardware::tv::cec::V2_0::CecVersion o, ::std::ostream* os);
constexpr int32_t operator|(const ::android::hardware::tv::cec::V2_0::CecVersion lhs, const ::android::hardware::tv::cec::V2_0::CecVersion rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const int32_t lhs, const ::android::hardware::tv::cec::V2_0::CecVersion rhs) {
    return static_cast<int32_t>(lhs | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const ::android::hardware::tv::cec::V2_0::CecVersion lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | rhs);
}
constexpr int32_t operator&(const ::android::hardware::tv::cec::V2_0::CecVersion lhs, const ::android::hardware::tv::cec::V2_0::CecVersion rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const int32_t lhs, const ::android::hardware::tv::cec::V2_0::CecVersion rhs) {
    return static_cast<int32_t>(lhs & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const ::android::hardware::tv::cec::V2_0::CecVersion lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & rhs);
}
constexpr int32_t &operator|=(int32_t& v, const ::android::hardware::tv::cec::V2_0::CecVersion e) {
    v |= static_cast<int32_t>(e);
    return v;
}
constexpr int32_t &operator&=(int32_t& v, const ::android::hardware::tv::cec::V2_0::CecVersion e) {
    v &= static_cast<int32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(uint32_t o);
static inline std::string toString(::android::hardware::tv::cec::V2_0::CecDeviceFeature o);
static inline void PrintTo(::android::hardware::tv::cec::V2_0::CecDeviceFeature o, ::std::ostream* os);
constexpr uint32_t operator|(const ::android::hardware::tv::cec::V2_0::CecDeviceFeature lhs, const ::android::hardware::tv::cec::V2_0::CecDeviceFeature rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const uint32_t lhs, const ::android::hardware::tv::cec::V2_0::CecDeviceFeature rhs) {
    return static_cast<uint32_t>(lhs | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const ::android::hardware::tv::cec::V2_0::CecDeviceFeature lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | rhs);
}
constexpr uint32_t operator&(const ::android::hardware::tv::cec::V2_0::CecDeviceFeature lhs, const ::android::hardware::tv::cec::V2_0::CecDeviceFeature rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const uint32_t lhs, const ::android::hardware::tv::cec::V2_0::CecDeviceFeature rhs) {
    return static_cast<uint32_t>(lhs & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const ::android::hardware::tv::cec::V2_0::CecDeviceFeature lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & rhs);
}
constexpr uint32_t &operator|=(uint32_t& v, const ::android::hardware::tv::cec::V2_0::CecDeviceFeature e) {
    v |= static_cast<uint32_t>(e);
    return v;
}
constexpr uint32_t &operator&=(uint32_t& v, const ::android::hardware::tv::cec::V2_0::CecDeviceFeature e) {
    v &= static_cast<uint32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(uint8_t o);
static inline std::string toString(::android::hardware::tv::cec::V2_0::CecRcProfileId o);
static inline void PrintTo(::android::hardware::tv::cec::V2_0::CecRcProfileId o, ::std::ostream* os);
constexpr uint8_t operator|(const ::android::hardware::tv::cec::V2_0::CecRcProfileId lhs, const ::android::hardware::tv::cec::V2_0::CecRcProfileId rhs) {
    return static_cast<uint8_t>(static_cast<uint8_t>(lhs) | static_cast<uint8_t>(rhs));
}
constexpr uint8_t operator|(const uint8_t lhs, const ::android::hardware::tv::cec::V2_0::CecRcProfileId rhs) {
    return static_cast<uint8_t>(lhs | static_cast<uint8_t>(rhs));
}
constexpr uint8_t operator|(const ::android::hardware::tv::cec::V2_0::CecRcProfileId lhs, const uint8_t rhs) {
    return static_cast<uint8_t>(static_cast<uint8_t>(lhs) | rhs);
}
constexpr uint8_t operator&(const ::android::hardware::tv::cec::V2_0::CecRcProfileId lhs, const ::android::hardware::tv::cec::V2_0::CecRcProfileId rhs) {
    return static_cast<uint8_t>(static_cast<uint8_t>(lhs) & static_cast<uint8_t>(rhs));
}
constexpr uint8_t operator&(const uint8_t lhs, const ::android::hardware::tv::cec::V2_0::CecRcProfileId rhs) {
    return static_cast<uint8_t>(lhs & static_cast<uint8_t>(rhs));
}
constexpr uint8_t operator&(const ::android::hardware::tv::cec::V2_0::CecRcProfileId lhs, const uint8_t rhs) {
    return static_cast<uint8_t>(static_cast<uint8_t>(lhs) & rhs);
}
constexpr uint8_t &operator|=(uint8_t& v, const ::android::hardware::tv::cec::V2_0::CecRcProfileId e) {
    v |= static_cast<uint8_t>(e);
    return v;
}
constexpr uint8_t &operator&=(uint8_t& v, const ::android::hardware::tv::cec::V2_0::CecRcProfileId e) {
    v &= static_cast<uint8_t>(e);
    return v;
}

template<typename>
static inline std::string toString(uint8_t o);
static inline std::string toString(::android::hardware::tv::cec::V2_0::CecRcProfileSource o);
static inline void PrintTo(::android::hardware::tv::cec::V2_0::CecRcProfileSource o, ::std::ostream* os);
constexpr uint8_t operator|(const ::android::hardware::tv::cec::V2_0::CecRcProfileSource lhs, const ::android::hardware::tv::cec::V2_0::CecRcProfileSource rhs) {
    return static_cast<uint8_t>(static_cast<uint8_t>(lhs) | static_cast<uint8_t>(rhs));
}
constexpr uint8_t operator|(const uint8_t lhs, const ::android::hardware::tv::cec::V2_0::CecRcProfileSource rhs) {
    return static_cast<uint8_t>(lhs | static_cast<uint8_t>(rhs));
}
constexpr uint8_t operator|(const ::android::hardware::tv::cec::V2_0::CecRcProfileSource lhs, const uint8_t rhs) {
    return static_cast<uint8_t>(static_cast<uint8_t>(lhs) | rhs);
}
constexpr uint8_t operator&(const ::android::hardware::tv::cec::V2_0::CecRcProfileSource lhs, const ::android::hardware::tv::cec::V2_0::CecRcProfileSource rhs) {
    return static_cast<uint8_t>(static_cast<uint8_t>(lhs) & static_cast<uint8_t>(rhs));
}
constexpr uint8_t operator&(const uint8_t lhs, const ::android::hardware::tv::cec::V2_0::CecRcProfileSource rhs) {
    return static_cast<uint8_t>(lhs & static_cast<uint8_t>(rhs));
}
constexpr uint8_t operator&(const ::android::hardware::tv::cec::V2_0::CecRcProfileSource lhs, const uint8_t rhs) {
    return static_cast<uint8_t>(static_cast<uint8_t>(lhs) & rhs);
}
constexpr uint8_t &operator|=(uint8_t& v, const ::android::hardware::tv::cec::V2_0::CecRcProfileSource e) {
    v |= static_cast<uint8_t>(e);
    return v;
}
constexpr uint8_t &operator&=(uint8_t& v, const ::android::hardware::tv::cec::V2_0::CecRcProfileSource e) {
    v &= static_cast<uint8_t>(e);
    return v;
}

static inline std::string toString(const ::android::hardware::tv::cec::V2_0::CecRcProfile1& o);
static inline void PrintTo(const ::android::hardware::tv::cec::V2_0::CecRcProfile1& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::tv::cec::V2_0::CecRcProfile1& lhs, const ::android::hardware::tv::cec::V2_0::CecRcProfile1& rhs);
static inline bool operator!=(const ::android::hardware::tv::cec::V2_0::CecRcProfile1& lhs, const ::android::hardware::tv::cec::V2_0::CecRcProfile1& rhs);

static inline std::string toString(const ::android::hardware::tv::cec::V2_0::CecRcProfile& o);
static inline void PrintTo(const ::android::hardware::tv::cec::V2_0::CecRcProfile& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::tv::cec::V2_0::CecRcProfile& lhs, const ::android::hardware::tv::cec::V2_0::CecRcProfile& rhs);
static inline bool operator!=(const ::android::hardware::tv::cec::V2_0::CecRcProfile& lhs, const ::android::hardware::tv::cec::V2_0::CecRcProfile& rhs);

template<typename>
static inline std::string toString(int8_t o);
static inline std::string toString(::android::hardware::tv::cec::V2_0::CecPowerState o);
static inline void PrintTo(::android::hardware::tv::cec::V2_0::CecPowerState o, ::std::ostream* os);
constexpr int8_t operator|(const ::android::hardware::tv::cec::V2_0::CecPowerState lhs, const ::android::hardware::tv::cec::V2_0::CecPowerState rhs) {
    return static_cast<int8_t>(static_cast<int8_t>(lhs) | static_cast<int8_t>(rhs));
}
constexpr int8_t operator|(const int8_t lhs, const ::android::hardware::tv::cec::V2_0::CecPowerState rhs) {
    return static_cast<int8_t>(lhs | static_cast<int8_t>(rhs));
}
constexpr int8_t operator|(const ::android::hardware::tv::cec::V2_0::CecPowerState lhs, const int8_t rhs) {
    return static_cast<int8_t>(static_cast<int8_t>(lhs) | rhs);
}
constexpr int8_t operator&(const ::android::hardware::tv::cec::V2_0::CecPowerState lhs, const ::android::hardware::tv::cec::V2_0::CecPowerState rhs) {
    return static_cast<int8_t>(static_cast<int8_t>(lhs) & static_cast<int8_t>(rhs));
}
constexpr int8_t operator&(const int8_t lhs, const ::android::hardware::tv::cec::V2_0::CecPowerState rhs) {
    return static_cast<int8_t>(lhs & static_cast<int8_t>(rhs));
}
constexpr int8_t operator&(const ::android::hardware::tv::cec::V2_0::CecPowerState lhs, const int8_t rhs) {
    return static_cast<int8_t>(static_cast<int8_t>(lhs) & rhs);
}
constexpr int8_t &operator|=(int8_t& v, const ::android::hardware::tv::cec::V2_0::CecPowerState e) {
    v |= static_cast<int8_t>(e);
    return v;
}
constexpr int8_t &operator&=(int8_t& v, const ::android::hardware::tv::cec::V2_0::CecPowerState e) {
    v &= static_cast<int8_t>(e);
    return v;
}

static inline std::string toString(const ::android::hardware::tv::cec::V2_0::CecDeviceInfo& o);
static inline void PrintTo(const ::android::hardware::tv::cec::V2_0::CecDeviceInfo& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::tv::cec::V2_0::CecDeviceInfo& lhs, const ::android::hardware::tv::cec::V2_0::CecDeviceInfo& rhs);
static inline bool operator!=(const ::android::hardware::tv::cec::V2_0::CecDeviceInfo& lhs, const ::android::hardware::tv::cec::V2_0::CecDeviceInfo& rhs);

template<typename>
static inline std::string toString(int32_t o);
static inline std::string toString(::android::hardware::tv::cec::V2_0::CecTopologyEventType o);
static inline void PrintTo(::android::hardware::tv::cec::V2_0::CecTopologyEventType o, ::std::ostream* os);
constexpr int32_t operator|(const ::android::hardware::tv::cec::V2_0::CecTopologyEventType lhs, const ::android::hardware::tv::cec::V2_0::CecTopologyEventType rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const int32_t lhs, const ::android::hardware::tv::cec::V2_0::CecTopologyEventType rhs) {
    return static_cast<int32_t>(lhs | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const ::android::hardware::tv::cec::V2_0::CecTopologyEventType lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | rhs);
}
constexpr int32_t operator&(const ::android::hardware::tv::cec::V2_0::CecTopologyEventType lhs, const ::android::hardware::tv::cec::V2_0::CecTopologyEventType rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const int32_t lhs, const ::android::hardware::tv::cec::V2_0::CecTopologyEventType rhs) {
    return static_cast<int32_t>(lhs & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const ::android::hardware::tv::cec::V2_0::CecTopologyEventType lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & rhs);
}
constexpr int32_t &operator|=(int32_t& v, const ::android::hardware::tv::cec::V2_0::CecTopologyEventType e) {
    v |= static_cast<int32_t>(e);
    return v;
}
constexpr int32_t &operator&=(int32_t& v, const ::android::hardware::tv::cec::V2_0::CecTopologyEventType e) {
    v &= static_cast<int32_t>(e);
    return v;
}

static inline std::string toString(const ::android::hardware::tv::cec::V2_0::CecTopologyEvent& o);
static inline void PrintTo(const ::android::hardware::tv::cec::V2_0::CecTopologyEvent& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::tv::cec::V2_0::CecTopologyEvent& lhs, const ::android::hardware::tv::cec::V2_0::CecTopologyEvent& rhs);
static inline bool operator!=(const ::android::hardware::tv::cec::V2_0::CecTopologyEvent& lhs, const ::android::hardware::tv::cec::V2_0::CecTopologyEvent& rhs);

template<typename>
static inline std::string toString(int32_t o);
static inline std::string toString(::android::hardware::tv::cec::V2_0::CecUICommandCodes o);
static inline void PrintTo(::android::hardware::tv::cec::V2_0::CecUICommandCodes o, ::std::ostream* os);
constexpr int32_t operator|(const ::android::hardware::tv::cec::V2_0::CecUICommandCodes lhs, const ::android::hardware::tv::cec::V2_0::CecUICommandCodes rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const int32_t lhs, const ::android::hardware::tv::cec::V2_0::CecUICommandCodes rhs) {
    return static_cast<int32_t>(lhs | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const ::android::hardware::tv::cec::V2_0::CecUICommandCodes lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | rhs);
}
constexpr int32_t operator&(const ::android::hardware::tv::cec::V2_0::CecUICommandCodes lhs, const ::android::hardware::tv::cec::V2_0::CecUICommandCodes rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const int32_t lhs, const ::android::hardware::tv::cec::V2_0::CecUICommandCodes rhs) {
    return static_cast<int32_t>(lhs & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const ::android::hardware::tv::cec::V2_0::CecUICommandCodes lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & rhs);
}
constexpr int32_t &operator|=(int32_t& v, const ::android::hardware::tv::cec::V2_0::CecUICommandCodes e) {
    v |= static_cast<int32_t>(e);
    return v;
}
constexpr int32_t &operator&=(int32_t& v, const ::android::hardware::tv::cec::V2_0::CecUICommandCodes e) {
    v &= static_cast<int32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(int32_t o);
static inline std::string toString(::android::hardware::tv::cec::V2_0::HdmiPortType o);
static inline void PrintTo(::android::hardware::tv::cec::V2_0::HdmiPortType o, ::std::ostream* os);
constexpr int32_t operator|(const ::android::hardware::tv::cec::V2_0::HdmiPortType lhs, const ::android::hardware::tv::cec::V2_0::HdmiPortType rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const int32_t lhs, const ::android::hardware::tv::cec::V2_0::HdmiPortType rhs) {
    return static_cast<int32_t>(lhs | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const ::android::hardware::tv::cec::V2_0::HdmiPortType lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | rhs);
}
constexpr int32_t operator&(const ::android::hardware::tv::cec::V2_0::HdmiPortType lhs, const ::android::hardware::tv::cec::V2_0::HdmiPortType rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const int32_t lhs, const ::android::hardware::tv::cec::V2_0::HdmiPortType rhs) {
    return static_cast<int32_t>(lhs & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const ::android::hardware::tv::cec::V2_0::HdmiPortType lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & rhs);
}
constexpr int32_t &operator|=(int32_t& v, const ::android::hardware::tv::cec::V2_0::HdmiPortType e) {
    v |= static_cast<int32_t>(e);
    return v;
}
constexpr int32_t &operator&=(int32_t& v, const ::android::hardware::tv::cec::V2_0::HdmiPortType e) {
    v &= static_cast<int32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(int32_t o);
static inline std::string toString(::android::hardware::tv::cec::V2_0::OptionKey o);
static inline void PrintTo(::android::hardware::tv::cec::V2_0::OptionKey o, ::std::ostream* os);
constexpr int32_t operator|(const ::android::hardware::tv::cec::V2_0::OptionKey lhs, const ::android::hardware::tv::cec::V2_0::OptionKey rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const int32_t lhs, const ::android::hardware::tv::cec::V2_0::OptionKey rhs) {
    return static_cast<int32_t>(lhs | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const ::android::hardware::tv::cec::V2_0::OptionKey lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | rhs);
}
constexpr int32_t operator&(const ::android::hardware::tv::cec::V2_0::OptionKey lhs, const ::android::hardware::tv::cec::V2_0::OptionKey rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const int32_t lhs, const ::android::hardware::tv::cec::V2_0::OptionKey rhs) {
    return static_cast<int32_t>(lhs & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const ::android::hardware::tv::cec::V2_0::OptionKey lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & rhs);
}
constexpr int32_t &operator|=(int32_t& v, const ::android::hardware::tv::cec::V2_0::OptionKey e) {
    v |= static_cast<int32_t>(e);
    return v;
}
constexpr int32_t &operator&=(int32_t& v, const ::android::hardware::tv::cec::V2_0::OptionKey e) {
    v &= static_cast<int32_t>(e);
    return v;
}

static inline std::string toString(const ::android::hardware::tv::cec::V2_0::HotplugEvent& o);
static inline void PrintTo(const ::android::hardware::tv::cec::V2_0::HotplugEvent& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::tv::cec::V2_0::HotplugEvent& lhs, const ::android::hardware::tv::cec::V2_0::HotplugEvent& rhs);
static inline bool operator!=(const ::android::hardware::tv::cec::V2_0::HotplugEvent& lhs, const ::android::hardware::tv::cec::V2_0::HotplugEvent& rhs);

static inline std::string toString(const ::android::hardware::tv::cec::V2_0::HdmiPortInfo& o);
static inline void PrintTo(const ::android::hardware::tv::cec::V2_0::HdmiPortInfo& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::tv::cec::V2_0::HdmiPortInfo& lhs, const ::android::hardware::tv::cec::V2_0::HdmiPortInfo& rhs);
static inline bool operator!=(const ::android::hardware::tv::cec::V2_0::HdmiPortInfo& lhs, const ::android::hardware::tv::cec::V2_0::HdmiPortInfo& rhs);

template<typename>
static inline std::string toString(int32_t o);
static inline std::string toString(::android::hardware::tv::cec::V2_0::Result o);
static inline void PrintTo(::android::hardware::tv::cec::V2_0::Result o, ::std::ostream* os);
constexpr int32_t operator|(const ::android::hardware::tv::cec::V2_0::Result lhs, const ::android::hardware::tv::cec::V2_0::Result rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const int32_t lhs, const ::android::hardware::tv::cec::V2_0::Result rhs) {
    return static_cast<int32_t>(lhs | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const ::android::hardware::tv::cec::V2_0::Result lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | rhs);
}
constexpr int32_t operator&(const ::android::hardware::tv::cec::V2_0::Result lhs, const ::android::hardware::tv::cec::V2_0::Result rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const int32_t lhs, const ::android::hardware::tv::cec::V2_0::Result rhs) {
    return static_cast<int32_t>(lhs & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const ::android::hardware::tv::cec::V2_0::Result lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & rhs);
}
constexpr int32_t &operator|=(int32_t& v, const ::android::hardware::tv::cec::V2_0::Result e) {
    v |= static_cast<int32_t>(e);
    return v;
}
constexpr int32_t &operator&=(int32_t& v, const ::android::hardware::tv::cec::V2_0::Result e) {
    v &= static_cast<int32_t>(e);
    return v;
}

//
// type header definitions for package
//

template<>
inline std::string toString<::android::hardware::tv::cec::V2_0::CecDeviceType>(int32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::tv::cec::V2_0::CecDeviceType> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::tv::cec::V2_0::CecDeviceType::INACTIVE) == static_cast<int32_t>(::android::hardware::tv::cec::V2_0::CecDeviceType::INACTIVE)) {
        os += (first ? "" : " | ");
        os += "INACTIVE";
        first = false;
        flipped |= ::android::hardware::tv::cec::V2_0::CecDeviceType::INACTIVE;
    }
    if ((o & ::android::hardware::tv::cec::V2_0::CecDeviceType::TV) == static_cast<int32_t>(::android::hardware::tv::cec::V2_0::CecDeviceType::TV)) {
        os += (first ? "" : " | ");
        os += "TV";
        first = false;
        flipped |= ::android::hardware::tv::cec::V2_0::CecDeviceType::TV;
    }
    if ((o & ::android::hardware::tv::cec::V2_0::CecDeviceType::RECORDER) == static_cast<int32_t>(::android::hardware::tv::cec::V2_0::CecDeviceType::RECORDER)) {
        os += (first ? "" : " | ");
        os += "RECORDER";
        first = false;
        flipped |= ::android::hardware::tv::cec::V2_0::CecDeviceType::RECORDER;
    }
    if ((o & ::android::hardware::tv::cec::V2_0::CecDeviceType::TUNER) == static_cast<int32_t>(::android::hardware::tv::cec::V2_0::CecDeviceType::TUNER)) {
        os += (first ? "" : " | ");
        os += "TUNER";
        first = false;
        flipped |= ::android::hardware::tv::cec::V2_0::CecDeviceType::TUNER;
    }
    if ((o & ::android::hardware::tv::cec::V2_0::CecDeviceType::PLAYBACK) == static_cast<int32_t>(::android::hardware::tv::cec::V2_0::CecDeviceType::PLAYBACK)) {
        os += (first ? "" : " | ");
        os += "PLAYBACK";
        first = false;
        flipped |= ::android::hardware::tv::cec::V2_0::CecDeviceType::PLAYBACK;
    }
    if ((o & ::android::hardware::tv::cec::V2_0::CecDeviceType::AUDIO_SYSTEM) == static_cast<int32_t>(::android::hardware::tv::cec::V2_0::CecDeviceType::AUDIO_SYSTEM)) {
        os += (first ? "" : " | ");
        os += "AUDIO_SYSTEM";
        first = false;
        flipped |= ::android::hardware::tv::cec::V2_0::CecDeviceType::AUDIO_SYSTEM;
    }
    if ((o & ::android::hardware::tv::cec::V2_0::CecDeviceType::PURE_CEC_SWITCH) == static_cast<int32_t>(::android::hardware::tv::cec::V2_0::CecDeviceType::PURE_CEC_SWITCH)) {
        os += (first ? "" : " | ");
        os += "PURE_CEC_SWITCH";
        first = false;
        flipped |= ::android::hardware::tv::cec::V2_0::CecDeviceType::PURE_CEC_SWITCH;
    }
    if ((o & ::android::hardware::tv::cec::V2_0::CecDeviceType::PROCESSOR) == static_cast<int32_t>(::android::hardware::tv::cec::V2_0::CecDeviceType::PROCESSOR)) {
        os += (first ? "" : " | ");
        os += "PROCESSOR";
        first = false;
        flipped |= ::android::hardware::tv::cec::V2_0::CecDeviceType::PROCESSOR;
    }
    if ((o & ::android::hardware::tv::cec::V2_0::CecDeviceType::MAX) == static_cast<int32_t>(::android::hardware::tv::cec::V2_0::CecDeviceType::MAX)) {
        os += (first ? "" : " | ");
        os += "MAX";
        first = false;
        flipped |= ::android::hardware::tv::cec::V2_0::CecDeviceType::MAX;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::tv::cec::V2_0::CecDeviceType o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::tv::cec::V2_0::CecDeviceType::INACTIVE) {
        return "INACTIVE";
    }
    if (o == ::android::hardware::tv::cec::V2_0::CecDeviceType::TV) {
        return "TV";
    }
    if (o == ::android::hardware::tv::cec::V2_0::CecDeviceType::RECORDER) {
        return "RECORDER";
    }
    if (o == ::android::hardware::tv::cec::V2_0::CecDeviceType::TUNER) {
        return "TUNER";
    }
    if (o == ::android::hardware::tv::cec::V2_0::CecDeviceType::PLAYBACK) {
        return "PLAYBACK";
    }
    if (o == ::android::hardware::tv::cec::V2_0::CecDeviceType::AUDIO_SYSTEM) {
        return "AUDIO_SYSTEM";
    }
    if (o == ::android::hardware::tv::cec::V2_0::CecDeviceType::PURE_CEC_SWITCH) {
        return "PURE_CEC_SWITCH";
    }
    if (o == ::android::hardware::tv::cec::V2_0::CecDeviceType::PROCESSOR) {
        return "PROCESSOR";
    }
    if (o == ::android::hardware::tv::cec::V2_0::CecDeviceType::MAX) {
        return "MAX";
    }
    std::string os;
    os += toHexString(static_cast<int32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::tv::cec::V2_0::CecDeviceType o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::tv::cec::V2_0::CecLogicalAddress>(int32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::tv::cec::V2_0::CecLogicalAddress> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::tv::cec::V2_0::CecLogicalAddress::TV) == static_cast<int32_t>(::android::hardware::tv::cec::V2_0::CecLogicalAddress::TV)) {
        os += (first ? "" : " | ");
        os += "TV";
        first = false;
        flipped |= ::android::hardware::tv::cec::V2_0::CecLogicalAddress::TV;
    }
    if ((o & ::android::hardware::tv::cec::V2_0::CecLogicalAddress::RECORDER_1) == static_cast<int32_t>(::android::hardware::tv::cec::V2_0::CecLogicalAddress::RECORDER_1)) {
        os += (first ? "" : " | ");
        os += "RECORDER_1";
        first = false;
        flipped |= ::android::hardware::tv::cec::V2_0::CecLogicalAddress::RECORDER_1;
    }
    if ((o & ::android::hardware::tv::cec::V2_0::CecLogicalAddress::RECORDER_2) == static_cast<int32_t>(::android::hardware::tv::cec::V2_0::CecLogicalAddress::RECORDER_2)) {
        os += (first ? "" : " | ");
        os += "RECORDER_2";
        first = false;
        flipped |= ::android::hardware::tv::cec::V2_0::CecLogicalAddress::RECORDER_2;
    }
    if ((o & ::android::hardware::tv::cec::V2_0::CecLogicalAddress::TUNER_1) == static_cast<int32_t>(::android::hardware::tv::cec::V2_0::CecLogicalAddress::TUNER_1)) {
        os += (first ? "" : " | ");
        os += "TUNER_1";
        first = false;
        flipped |= ::android::hardware::tv::cec::V2_0::CecLogicalAddress::TUNER_1;
    }
    if ((o & ::android::hardware::tv::cec::V2_0::CecLogicalAddress::PLAYBACK_1) == static_cast<int32_t>(::android::hardware::tv::cec::V2_0::CecLogicalAddress::PLAYBACK_1)) {
        os += (first ? "" : " | ");
        os += "PLAYBACK_1";
        first = false;
        flipped |= ::android::hardware::tv::cec::V2_0::CecLogicalAddress::PLAYBACK_1;
    }
    if ((o & ::android::hardware::tv::cec::V2_0::CecLogicalAddress::AUDIO_SYSTEM) == static_cast<int32_t>(::android::hardware::tv::cec::V2_0::CecLogicalAddress::AUDIO_SYSTEM)) {
        os += (first ? "" : " | ");
        os += "AUDIO_SYSTEM";
        first = false;
        flipped |= ::android::hardware::tv::cec::V2_0::CecLogicalAddress::AUDIO_SYSTEM;
    }
    if ((o & ::android::hardware::tv::cec::V2_0::CecLogicalAddress::TUNER_2) == static_cast<int32_t>(::android::hardware::tv::cec::V2_0::CecLogicalAddress::TUNER_2)) {
        os += (first ? "" : " | ");
        os += "TUNER_2";
        first = false;
        flipped |= ::android::hardware::tv::cec::V2_0::CecLogicalAddress::TUNER_2;
    }
    if ((o & ::android::hardware::tv::cec::V2_0::CecLogicalAddress::TUNER_3) == static_cast<int32_t>(::android::hardware::tv::cec::V2_0::CecLogicalAddress::TUNER_3)) {
        os += (first ? "" : " | ");
        os += "TUNER_3";
        first = false;
        flipped |= ::android::hardware::tv::cec::V2_0::CecLogicalAddress::TUNER_3;
    }
    if ((o & ::android::hardware::tv::cec::V2_0::CecLogicalAddress::PLAYBACK_2) == static_cast<int32_t>(::android::hardware::tv::cec::V2_0::CecLogicalAddress::PLAYBACK_2)) {
        os += (first ? "" : " | ");
        os += "PLAYBACK_2";
        first = false;
        flipped |= ::android::hardware::tv::cec::V2_0::CecLogicalAddress::PLAYBACK_2;
    }
    if ((o & ::android::hardware::tv::cec::V2_0::CecLogicalAddress::RECORDER_3) == static_cast<int32_t>(::android::hardware::tv::cec::V2_0::CecLogicalAddress::RECORDER_3)) {
        os += (first ? "" : " | ");
        os += "RECORDER_3";
        first = false;
        flipped |= ::android::hardware::tv::cec::V2_0::CecLogicalAddress::RECORDER_3;
    }
    if ((o & ::android::hardware::tv::cec::V2_0::CecLogicalAddress::TUNER_4) == static_cast<int32_t>(::android::hardware::tv::cec::V2_0::CecLogicalAddress::TUNER_4)) {
        os += (first ? "" : " | ");
        os += "TUNER_4";
        first = false;
        flipped |= ::android::hardware::tv::cec::V2_0::CecLogicalAddress::TUNER_4;
    }
    if ((o & ::android::hardware::tv::cec::V2_0::CecLogicalAddress::PLAYBACK_3) == static_cast<int32_t>(::android::hardware::tv::cec::V2_0::CecLogicalAddress::PLAYBACK_3)) {
        os += (first ? "" : " | ");
        os += "PLAYBACK_3";
        first = false;
        flipped |= ::android::hardware::tv::cec::V2_0::CecLogicalAddress::PLAYBACK_3;
    }
    if ((o & ::android::hardware::tv::cec::V2_0::CecLogicalAddress::BACKUP_1) == static_cast<int32_t>(::android::hardware::tv::cec::V2_0::CecLogicalAddress::BACKUP_1)) {
        os += (first ? "" : " | ");
        os += "BACKUP_1";
        first = false;
        flipped |= ::android::hardware::tv::cec::V2_0::CecLogicalAddress::BACKUP_1;
    }
    if ((o & ::android::hardware::tv::cec::V2_0::CecLogicalAddress::BACKUP_2) == static_cast<int32_t>(::android::hardware::tv::cec::V2_0::CecLogicalAddress::BACKUP_2)) {
        os += (first ? "" : " | ");
        os += "BACKUP_2";
        first = false;
        flipped |= ::android::hardware::tv::cec::V2_0::CecLogicalAddress::BACKUP_2;
    }
    if ((o & ::android::hardware::tv::cec::V2_0::CecLogicalAddress::SPECIFIC_USE) == static_cast<int32_t>(::android::hardware::tv::cec::V2_0::CecLogicalAddress::SPECIFIC_USE)) {
        os += (first ? "" : " | ");
        os += "SPECIFIC_USE";
        first = false;
        flipped |= ::android::hardware::tv::cec::V2_0::CecLogicalAddress::SPECIFIC_USE;
    }
    if ((o & ::android::hardware::tv::cec::V2_0::CecLogicalAddress::UNREGISTERED) == static_cast<int32_t>(::android::hardware::tv::cec::V2_0::CecLogicalAddress::UNREGISTERED)) {
        os += (first ? "" : " | ");
        os += "UNREGISTERED";
        first = false;
        flipped |= ::android::hardware::tv::cec::V2_0::CecLogicalAddress::UNREGISTERED;
    }
    if ((o & ::android::hardware::tv::cec::V2_0::CecLogicalAddress::BROADCAST) == static_cast<int32_t>(::android::hardware::tv::cec::V2_0::CecLogicalAddress::BROADCAST)) {
        os += (first ? "" : " | ");
        os += "BROADCAST";
        first = false;
        flipped |= ::android::hardware::tv::cec::V2_0::CecLogicalAddress::BROADCAST;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::tv::cec::V2_0::CecLogicalAddress o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::tv::cec::V2_0::CecLogicalAddress::TV) {
        return "TV";
    }
    if (o == ::android::hardware::tv::cec::V2_0::CecLogicalAddress::RECORDER_1) {
        return "RECORDER_1";
    }
    if (o == ::android::hardware::tv::cec::V2_0::CecLogicalAddress::RECORDER_2) {
        return "RECORDER_2";
    }
    if (o == ::android::hardware::tv::cec::V2_0::CecLogicalAddress::TUNER_1) {
        return "TUNER_1";
    }
    if (o == ::android::hardware::tv::cec::V2_0::CecLogicalAddress::PLAYBACK_1) {
        return "PLAYBACK_1";
    }
    if (o == ::android::hardware::tv::cec::V2_0::CecLogicalAddress::AUDIO_SYSTEM) {
        return "AUDIO_SYSTEM";
    }
    if (o == ::android::hardware::tv::cec::V2_0::CecLogicalAddress::TUNER_2) {
        return "TUNER_2";
    }
    if (o == ::android::hardware::tv::cec::V2_0::CecLogicalAddress::TUNER_3) {
        return "TUNER_3";
    }
    if (o == ::android::hardware::tv::cec::V2_0::CecLogicalAddress::PLAYBACK_2) {
        return "PLAYBACK_2";
    }
    if (o == ::android::hardware::tv::cec::V2_0::CecLogicalAddress::RECORDER_3) {
        return "RECORDER_3";
    }
    if (o == ::android::hardware::tv::cec::V2_0::CecLogicalAddress::TUNER_4) {
        return "TUNER_4";
    }
    if (o == ::android::hardware::tv::cec::V2_0::CecLogicalAddress::PLAYBACK_3) {
        return "PLAYBACK_3";
    }
    if (o == ::android::hardware::tv::cec::V2_0::CecLogicalAddress::BACKUP_1) {
        return "BACKUP_1";
    }
    if (o == ::android::hardware::tv::cec::V2_0::CecLogicalAddress::BACKUP_2) {
        return "BACKUP_2";
    }
    if (o == ::android::hardware::tv::cec::V2_0::CecLogicalAddress::SPECIFIC_USE) {
        return "SPECIFIC_USE";
    }
    if (o == ::android::hardware::tv::cec::V2_0::CecLogicalAddress::UNREGISTERED) {
        return "UNREGISTERED";
    }
    if (o == ::android::hardware::tv::cec::V2_0::CecLogicalAddress::BROADCAST) {
        return "BROADCAST";
    }
    std::string os;
    os += toHexString(static_cast<int32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::tv::cec::V2_0::CecLogicalAddress o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::tv::cec::V2_0::CecMessageType>(int32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::tv::cec::V2_0::CecMessageType> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::tv::cec::V2_0::CecMessageType::FEATURE_ABORT) == static_cast<int32_t>(::android::hardware::tv::cec::V2_0::CecMessageType::FEATURE_ABORT)) {
        os += (first ? "" : " | ");
        os += "FEATURE_ABORT";
        first = false;
        flipped |= ::android::hardware::tv::cec::V2_0::CecMessageType::FEATURE_ABORT;
    }
    if ((o & ::android::hardware::tv::cec::V2_0::CecMessageType::IMAGE_VIEW_ON) == static_cast<int32_t>(::android::hardware::tv::cec::V2_0::CecMessageType::IMAGE_VIEW_ON)) {
        os += (first ? "" : " | ");
        os += "IMAGE_VIEW_ON";
        first = false;
        flipped |= ::android::hardware::tv::cec::V2_0::CecMessageType::IMAGE_VIEW_ON;
    }
    if ((o & ::android::hardware::tv::cec::V2_0::CecMessageType::TUNER_STEP_INCREMENT) == static_cast<int32_t>(::android::hardware::tv::cec::V2_0::CecMessageType::TUNER_STEP_INCREMENT)) {
        os += (first ? "" : " | ");
        os += "TUNER_STEP_INCREMENT";
        first = false;
        flipped |= ::android::hardware::tv::cec::V2_0::CecMessageType::TUNER_STEP_INCREMENT;
    }
    if ((o & ::android::hardware::tv::cec::V2_0::CecMessageType::TUNER_STEP_DECREMENT) == static_cast<int32_t>(::android::hardware::tv::cec::V2_0::CecMessageType::TUNER_STEP_DECREMENT)) {
        os += (first ? "" : " | ");
        os += "TUNER_STEP_DECREMENT";
        first = false;
        flipped |= ::android::hardware::tv::cec::V2_0::CecMessageType::TUNER_STEP_DECREMENT;
    }
    if ((o & ::android::hardware::tv::cec::V2_0::CecMessageType::TUNER_DEVICE_STATUS) == static_cast<int32_t>(::android::hardware::tv::cec::V2_0::CecMessageType::TUNER_DEVICE_STATUS)) {
        os += (first ? "" : " | ");
        os += "TUNER_DEVICE_STATUS";
        first = false;
        flipped |= ::android::hardware::tv::cec::V2_0::CecMessageType::TUNER_DEVICE_STATUS;
    }
    if ((o & ::android::hardware::tv::cec::V2_0::CecMessageType::GIVE_TUNER_DEVICE_STATUS) == static_cast<int32_t>(::android::hardware::tv::cec::V2_0::CecMessageType::GIVE_TUNER_DEVICE_STATUS)) {
        os += (first ? "" : " | ");
        os += "GIVE_TUNER_DEVICE_STATUS";
        first = false;
        flipped |= ::android::hardware::tv::cec::V2_0::CecMessageType::GIVE_TUNER_DEVICE_STATUS;
    }
    if ((o & ::android::hardware::tv::cec::V2_0::CecMessageType::RECORD_ON) == static_cast<int32_t>(::android::hardware::tv::cec::V2_0::CecMessageType::RECORD_ON)) {
        os += (first ? "" : " | ");
        os += "RECORD_ON";
        first = false;
        flipped |= ::android::hardware::tv::cec::V2_0::CecMessageType::RECORD_ON;
    }
    if ((o & ::android::hardware::tv::cec::V2_0::CecMessageType::RECORD_STATUS) == static_cast<int32_t>(::android::hardware::tv::cec::V2_0::CecMessageType::RECORD_STATUS)) {
        os += (first ? "" : " | ");
        os += "RECORD_STATUS";
        first = false;
        flipped |= ::android::hardware::tv::cec::V2_0::CecMessageType::RECORD_STATUS;
    }
    if ((o & ::android::hardware::tv::cec::V2_0::CecMessageType::RECORD_OFF) == static_cast<int32_t>(::android::hardware::tv::cec::V2_0::CecMessageType::RECORD_OFF)) {
        os += (first ? "" : " | ");
        os += "RECORD_OFF";
        first = false;
        flipped |= ::android::hardware::tv::cec::V2_0::CecMessageType::RECORD_OFF;
    }
    if ((o & ::android::hardware::tv::cec::V2_0::CecMessageType::TEXT_VIEW_ON) == static_cast<int32_t>(::android::hardware::tv::cec::V2_0::CecMessageType::TEXT_VIEW_ON)) {
        os += (first ? "" : " | ");
        os += "TEXT_VIEW_ON";
        first = false;
        flipped |= ::android::hardware::tv::cec::V2_0::CecMessageType::TEXT_VIEW_ON;
    }
    if ((o & ::android::hardware::tv::cec::V2_0::CecMessageType::RECORD_TV_SCREEN) == static_cast<int32_t>(::android::hardware::tv::cec::V2_0::CecMessageType::RECORD_TV_SCREEN)) {
        os += (first ? "" : " | ");
        os += "RECORD_TV_SCREEN";
        first = false;
        flipped |= ::android::hardware::tv::cec::V2_0::CecMessageType::RECORD_TV_SCREEN;
    }
    if ((o & ::android::hardware::tv::cec::V2_0::CecMessageType::GIVE_DECK_STATUS) == static_cast<int32_t>(::android::hardware::tv::cec::V2_0::CecMessageType::GIVE_DECK_STATUS)) {
        os += (first ? "" : " | ");
        os += "GIVE_DECK_STATUS";
        first = false;
        flipped |= ::android::hardware::tv::cec::V2_0::CecMessageType::GIVE_DECK_STATUS;
    }
    if ((o & ::android::hardware::tv::cec::V2_0::CecMessageType::DECK_STATUS) == static_cast<int32_t>(::android::hardware::tv::cec::V2_0::CecMessageType::DECK_STATUS)) {
        os += (first ? "" : " | ");
        os += "DECK_STATUS";
        first = false;
        flipped |= ::android::hardware::tv::cec::V2_0::CecMessageType::DECK_STATUS;
    }
    if ((o & ::android::hardware::tv::cec::V2_0::CecMessageType::SET_MENU_LANGUAGE) == static_cast<int32_t>(::android::hardware::tv::cec::V2_0::CecMessageType::SET_MENU_LANGUAGE)) {
        os += (first ? "" : " | ");
        os += "SET_MENU_LANGUAGE";
        first = false;
        flipped |= ::android::hardware::tv::cec::V2_0::CecMessageType::SET_MENU_LANGUAGE;
    }
    if ((o & ::android::hardware::tv::cec::V2_0::CecMessageType::CLEAR_ANALOG_TIMER) == static_cast<int32_t>(::android::hardware::tv::cec::V2_0::CecMessageType::CLEAR_ANALOG_TIMER)) {
        os += (first ? "" : " | ");
        os += "CLEAR_ANALOG_TIMER";
        first = false;
        flipped |= ::android::hardware::tv::cec::V2_0::CecMessageType::CLEAR_ANALOG_TIMER;
    }
    if ((o & ::android::hardware::tv::cec::V2_0::CecMessageType::SET_ANALOG_TIMER) == static_cast<int32_t>(::android::hardware::tv::cec::V2_0::CecMessageType::SET_ANALOG_TIMER)) {
        os += (first ? "" : " | ");
        os += "SET_ANALOG_TIMER";
        first = false;
        flipped |= ::android::hardware::tv::cec::V2_0::CecMessageType::SET_ANALOG_TIMER;
    }
    if ((o & ::android::hardware::tv::cec::V2_0::CecMessageType::TIMER_STATUS) == static_cast<int32_t>(::android::hardware::tv::cec::V2_0::CecMessageType::TIMER_STATUS)) {
        os += (first ? "" : " | ");
        os += "TIMER_STATUS";
        first = false;
        flipped |= ::android::hardware::tv::cec::V2_0::CecMessageType::TIMER_STATUS;
    }
    if ((o & ::android::hardware::tv::cec::V2_0::CecMessageType::STANDBY) == static_cast<int32_t>(::android::hardware::tv::cec::V2_0::CecMessageType::STANDBY)) {
        os += (first ? "" : " | ");
        os += "STANDBY";
        first = false;
        flipped |= ::android::hardware::tv::cec::V2_0::CecMessageType::STANDBY;
    }
    if ((o & ::android::hardware::tv::cec::V2_0::CecMessageType::PLAY) == static_cast<int32_t>(::android::hardware::tv::cec::V2_0::CecMessageType::PLAY)) {
        os += (first ? "" : " | ");
        os += "PLAY";
        first = false;
        flipped |= ::android::hardware::tv::cec::V2_0::CecMessageType::PLAY;
    }
    if ((o & ::android::hardware::tv::cec::V2_0::CecMessageType::DECK_CONTROL) == static_cast<int32_t>(::android::hardware::tv::cec::V2_0::CecMessageType::DECK_CONTROL)) {
        os += (first ? "" : " | ");
        os += "DECK_CONTROL";
        first = false;
        flipped |= ::android::hardware::tv::cec::V2_0::CecMessageType::DECK_CONTROL;
    }
    if ((o & ::android::hardware::tv::cec::V2_0::CecMessageType::TIMER_CLEARED_STATUS) == static_cast<int32_t>(::android::hardware::tv::cec::V2_0::CecMessageType::TIMER_CLEARED_STATUS)) {
        os += (first ? "" : " | ");
        os += "TIMER_CLEARED_STATUS";
        first = false;
        flipped |= ::android::hardware::tv::cec::V2_0::CecMessageType::TIMER_CLEARED_STATUS;
    }
    if ((o & ::android::hardware::tv::cec::V2_0::CecMessageType::USER_CONTROL_PRESSED) == static_cast<int32_t>(::android::hardware::tv::cec::V2_0::CecMessageType::USER_CONTROL_PRESSED)) {
        os += (first ? "" : " | ");
        os += "USER_CONTROL_PRESSED";
        first = false;
        flipped |= ::android::hardware::tv::cec::V2_0::CecMessageType::USER_CONTROL_PRESSED;
    }
    if ((o & ::android::hardware::tv::cec::V2_0::CecMessageType::USER_CONTROL_RELEASED) == static_cast<int32_t>(::android::hardware::tv::cec::V2_0::CecMessageType::USER_CONTROL_RELEASED)) {
        os += (first ? "" : " | ");
        os += "USER_CONTROL_RELEASED";
        first = false;
        flipped |= ::android::hardware::tv::cec::V2_0::CecMessageType::USER_CONTROL_RELEASED;
    }
    if ((o & ::android::hardware::tv::cec::V2_0::CecMessageType::GIVE_OSD_NAME) == static_cast<int32_t>(::android::hardware::tv::cec::V2_0::CecMessageType::GIVE_OSD_NAME)) {
        os += (first ? "" : " | ");
        os += "GIVE_OSD_NAME";
        first = false;
        flipped |= ::android::hardware::tv::cec::V2_0::CecMessageType::GIVE_OSD_NAME;
    }
    if ((o & ::android::hardware::tv::cec::V2_0::CecMessageType::SET_OSD_NAME) == static_cast<int32_t>(::android::hardware::tv::cec::V2_0::CecMessageType::SET_OSD_NAME)) {
        os += (first ? "" : " | ");
        os += "SET_OSD_NAME";
        first = false;
        flipped |= ::android::hardware::tv::cec::V2_0::CecMessageType::SET_OSD_NAME;
    }
    if ((o & ::android::hardware::tv::cec::V2_0::CecMessageType::SET_OSD_STRING) == static_cast<int32_t>(::android::hardware::tv::cec::V2_0::CecMessageType::SET_OSD_STRING)) {
        os += (first ? "" : " | ");
        os += "SET_OSD_STRING";
        first = false;
        flipped |= ::android::hardware::tv::cec::V2_0::CecMessageType::SET_OSD_STRING;
    }
    if ((o & ::android::hardware::tv::cec::V2_0::CecMessageType::SET_TIMER_PROGRAM_TITLE) == static_cast<int32_t>(::android::hardware::tv::cec::V2_0::CecMessageType::SET_TIMER_PROGRAM_TITLE)) {
        os += (first ? "" : " | ");
        os += "SET_TIMER_PROGRAM_TITLE";
        first = false;
        flipped |= ::android::hardware::tv::cec::V2_0::CecMessageType::SET_TIMER_PROGRAM_TITLE;
    }
    if ((o & ::android::hardware::tv::cec::V2_0::CecMessageType::SYSTEM_AUDIO_MODE_REQUEST) == static_cast<int32_t>(::android::hardware::tv::cec::V2_0::CecMessageType::SYSTEM_AUDIO_MODE_REQUEST)) {
        os += (first ? "" : " | ");
        os += "SYSTEM_AUDIO_MODE_REQUEST";
        first = false;
        flipped |= ::android::hardware::tv::cec::V2_0::CecMessageType::SYSTEM_AUDIO_MODE_REQUEST;
    }
    if ((o & ::android::hardware::tv::cec::V2_0::CecMessageType::GIVE_AUDIO_STATUS) == static_cast<int32_t>(::android::hardware::tv::cec::V2_0::CecMessageType::GIVE_AUDIO_STATUS)) {
        os += (first ? "" : " | ");
        os += "GIVE_AUDIO_STATUS";
        first = false;
        flipped |= ::android::hardware::tv::cec::V2_0::CecMessageType::GIVE_AUDIO_STATUS;
    }
    if ((o & ::android::hardware::tv::cec::V2_0::CecMessageType::SET_SYSTEM_AUDIO_MODE) == static_cast<int32_t>(::android::hardware::tv::cec::V2_0::CecMessageType::SET_SYSTEM_AUDIO_MODE)) {
        os += (first ? "" : " | ");
        os += "SET_SYSTEM_AUDIO_MODE";
        first = false;
        flipped |= ::android::hardware::tv::cec::V2_0::CecMessageType::SET_SYSTEM_AUDIO_MODE;
    }
    if ((o & ::android::hardware::tv::cec::V2_0::CecMessageType::REPORT_AUDIO_STATUS) == static_cast<int32_t>(::android::hardware::tv::cec::V2_0::CecMessageType::REPORT_AUDIO_STATUS)) {
        os += (first ? "" : " | ");
        os += "REPORT_AUDIO_STATUS";
        first = false;
        flipped |= ::android::hardware::tv::cec::V2_0::CecMessageType::REPORT_AUDIO_STATUS;
    }
    if ((o & ::android::hardware::tv::cec::V2_0::CecMessageType::GIVE_SYSTEM_AUDIO_MODE_STATUS) == static_cast<int32_t>(::android::hardware::tv::cec::V2_0::CecMessageType::GIVE_SYSTEM_AUDIO_MODE_STATUS)) {
        os += (first ? "" : " | ");
        os += "GIVE_SYSTEM_AUDIO_MODE_STATUS";
        first = false;
        flipped |= ::android::hardware::tv::cec::V2_0::CecMessageType::GIVE_SYSTEM_AUDIO_MODE_STATUS;
    }
    if ((o & ::android::hardware::tv::cec::V2_0::CecMessageType::SYSTEM_AUDIO_MODE_STATUS) == static_cast<int32_t>(::android::hardware::tv::cec::V2_0::CecMessageType::SYSTEM_AUDIO_MODE_STATUS)) {
        os += (first ? "" : " | ");
        os += "SYSTEM_AUDIO_MODE_STATUS";
        first = false;
        flipped |= ::android::hardware::tv::cec::V2_0::CecMessageType::SYSTEM_AUDIO_MODE_STATUS;
    }
    if ((o & ::android::hardware::tv::cec::V2_0::CecMessageType::ROUTING_CHANGE) == static_cast<int32_t>(::android::hardware::tv::cec::V2_0::CecMessageType::ROUTING_CHANGE)) {
        os += (first ? "" : " | ");
        os += "ROUTING_CHANGE";
        first = false;
        flipped |= ::android::hardware::tv::cec::V2_0::CecMessageType::ROUTING_CHANGE;
    }
    if ((o & ::android::hardware::tv::cec::V2_0::CecMessageType::ROUTING_INFORMATION) == static_cast<int32_t>(::android::hardware::tv::cec::V2_0::CecMessageType::ROUTING_INFORMATION)) {
        os += (first ? "" : " | ");
        os += "ROUTING_INFORMATION";
        first = false;
        flipped |= ::android::hardware::tv::cec::V2_0::CecMessageType::ROUTING_INFORMATION;
    }
    if ((o & ::android::hardware::tv::cec::V2_0::CecMessageType::ACTIVE_SOURCE) == static_cast<int32_t>(::android::hardware::tv::cec::V2_0::CecMessageType::ACTIVE_SOURCE)) {
        os += (first ? "" : " | ");
        os += "ACTIVE_SOURCE";
        first = false;
        flipped |= ::android::hardware::tv::cec::V2_0::CecMessageType::ACTIVE_SOURCE;
    }
    if ((o & ::android::hardware::tv::cec::V2_0::CecMessageType::GIVE_PHYSICAL_ADDRESS) == static_cast<int32_t>(::android::hardware::tv::cec::V2_0::CecMessageType::GIVE_PHYSICAL_ADDRESS)) {
        os += (first ? "" : " | ");
        os += "GIVE_PHYSICAL_ADDRESS";
        first = false;
        flipped |= ::android::hardware::tv::cec::V2_0::CecMessageType::GIVE_PHYSICAL_ADDRESS;
    }
    if ((o & ::android::hardware::tv::cec::V2_0::CecMessageType::REPORT_PHYSICAL_ADDRESS) == static_cast<int32_t>(::android::hardware::tv::cec::V2_0::CecMessageType::REPORT_PHYSICAL_ADDRESS)) {
        os += (first ? "" : " | ");
        os += "REPORT_PHYSICAL_ADDRESS";
        first = false;
        flipped |= ::android::hardware::tv::cec::V2_0::CecMessageType::REPORT_PHYSICAL_ADDRESS;
    }
    if ((o & ::android::hardware::tv::cec::V2_0::CecMessageType::REQUEST_ACTIVE_SOURCE) == static_cast<int32_t>(::android::hardware::tv::cec::V2_0::CecMessageType::REQUEST_ACTIVE_SOURCE)) {
        os += (first ? "" : " | ");
        os += "REQUEST_ACTIVE_SOURCE";
        first = false;
        flipped |= ::android::hardware::tv::cec::V2_0::CecMessageType::REQUEST_ACTIVE_SOURCE;
    }
    if ((o & ::android::hardware::tv::cec::V2_0::CecMessageType::SET_STREAM_PATH) == static_cast<int32_t>(::android::hardware::tv::cec::V2_0::CecMessageType::SET_STREAM_PATH)) {
        os += (first ? "" : " | ");
        os += "SET_STREAM_PATH";
        first = false;
        flipped |= ::android::hardware::tv::cec::V2_0::CecMessageType::SET_STREAM_PATH;
    }
    if ((o & ::android::hardware::tv::cec::V2_0::CecMessageType::DEVICE_VENDOR_ID) == static_cast<int32_t>(::android::hardware::tv::cec::V2_0::CecMessageType::DEVICE_VENDOR_ID)) {
        os += (first ? "" : " | ");
        os += "DEVICE_VENDOR_ID";
        first = false;
        flipped |= ::android::hardware::tv::cec::V2_0::CecMessageType::DEVICE_VENDOR_ID;
    }
    if ((o & ::android::hardware::tv::cec::V2_0::CecMessageType::VENDOR_COMMAND) == static_cast<int32_t>(::android::hardware::tv::cec::V2_0::CecMessageType::VENDOR_COMMAND)) {
        os += (first ? "" : " | ");
        os += "VENDOR_COMMAND";
        first = false;
        flipped |= ::android::hardware::tv::cec::V2_0::CecMessageType::VENDOR_COMMAND;
    }
    if ((o & ::android::hardware::tv::cec::V2_0::CecMessageType::VENDOR_REMOTE_BUTTON_DOWN) == static_cast<int32_t>(::android::hardware::tv::cec::V2_0::CecMessageType::VENDOR_REMOTE_BUTTON_DOWN)) {
        os += (first ? "" : " | ");
        os += "VENDOR_REMOTE_BUTTON_DOWN";
        first = false;
        flipped |= ::android::hardware::tv::cec::V2_0::CecMessageType::VENDOR_REMOTE_BUTTON_DOWN;
    }
    if ((o & ::android::hardware::tv::cec::V2_0::CecMessageType::VENDOR_REMOTE_BUTTON_UP) == static_cast<int32_t>(::android::hardware::tv::cec::V2_0::CecMessageType::VENDOR_REMOTE_BUTTON_UP)) {
        os += (first ? "" : " | ");
        os += "VENDOR_REMOTE_BUTTON_UP";
        first = false;
        flipped |= ::android::hardware::tv::cec::V2_0::CecMessageType::VENDOR_REMOTE_BUTTON_UP;
    }
    if ((o & ::android::hardware::tv::cec::V2_0::CecMessageType::GIVE_DEVICE_VENDOR_ID) == static_cast<int32_t>(::android::hardware::tv::cec::V2_0::CecMessageType::GIVE_DEVICE_VENDOR_ID)) {
        os += (first ? "" : " | ");
        os += "GIVE_DEVICE_VENDOR_ID";
        first = false;
        flipped |= ::android::hardware::tv::cec::V2_0::CecMessageType::GIVE_DEVICE_VENDOR_ID;
    }
    if ((o & ::android::hardware::tv::cec::V2_0::CecMessageType::MENU_REQUEST) == static_cast<int32_t>(::android::hardware::tv::cec::V2_0::CecMessageType::MENU_REQUEST)) {
        os += (first ? "" : " | ");
        os += "MENU_REQUEST";
        first = false;
        flipped |= ::android::hardware::tv::cec::V2_0::CecMessageType::MENU_REQUEST;
    }
    if ((o & ::android::hardware::tv::cec::V2_0::CecMessageType::MENU_STATUS) == static_cast<int32_t>(::android::hardware::tv::cec::V2_0::CecMessageType::MENU_STATUS)) {
        os += (first ? "" : " | ");
        os += "MENU_STATUS";
        first = false;
        flipped |= ::android::hardware::tv::cec::V2_0::CecMessageType::MENU_STATUS;
    }
    if ((o & ::android::hardware::tv::cec::V2_0::CecMessageType::GIVE_DEVICE_POWER_STATUS) == static_cast<int32_t>(::android::hardware::tv::cec::V2_0::CecMessageType::GIVE_DEVICE_POWER_STATUS)) {
        os += (first ? "" : " | ");
        os += "GIVE_DEVICE_POWER_STATUS";
        first = false;
        flipped |= ::android::hardware::tv::cec::V2_0::CecMessageType::GIVE_DEVICE_POWER_STATUS;
    }
    if ((o & ::android::hardware::tv::cec::V2_0::CecMessageType::REPORT_POWER_STATUS) == static_cast<int32_t>(::android::hardware::tv::cec::V2_0::CecMessageType::REPORT_POWER_STATUS)) {
        os += (first ? "" : " | ");
        os += "REPORT_POWER_STATUS";
        first = false;
        flipped |= ::android::hardware::tv::cec::V2_0::CecMessageType::REPORT_POWER_STATUS;
    }
    if ((o & ::android::hardware::tv::cec::V2_0::CecMessageType::GET_MENU_LANGUAGE) == static_cast<int32_t>(::android::hardware::tv::cec::V2_0::CecMessageType::GET_MENU_LANGUAGE)) {
        os += (first ? "" : " | ");
        os += "GET_MENU_LANGUAGE";
        first = false;
        flipped |= ::android::hardware::tv::cec::V2_0::CecMessageType::GET_MENU_LANGUAGE;
    }
    if ((o & ::android::hardware::tv::cec::V2_0::CecMessageType::SELECT_ANALOG_SERVICE) == static_cast<int32_t>(::android::hardware::tv::cec::V2_0::CecMessageType::SELECT_ANALOG_SERVICE)) {
        os += (first ? "" : " | ");
        os += "SELECT_ANALOG_SERVICE";
        first = false;
        flipped |= ::android::hardware::tv::cec::V2_0::CecMessageType::SELECT_ANALOG_SERVICE;
    }
    if ((o & ::android::hardware::tv::cec::V2_0::CecMessageType::SELECT_DIGITAL_SERVICE) == static_cast<int32_t>(::android::hardware::tv::cec::V2_0::CecMessageType::SELECT_DIGITAL_SERVICE)) {
        os += (first ? "" : " | ");
        os += "SELECT_DIGITAL_SERVICE";
        first = false;
        flipped |= ::android::hardware::tv::cec::V2_0::CecMessageType::SELECT_DIGITAL_SERVICE;
    }
    if ((o & ::android::hardware::tv::cec::V2_0::CecMessageType::SET_DIGITAL_TIMER) == static_cast<int32_t>(::android::hardware::tv::cec::V2_0::CecMessageType::SET_DIGITAL_TIMER)) {
        os += (first ? "" : " | ");
        os += "SET_DIGITAL_TIMER";
        first = false;
        flipped |= ::android::hardware::tv::cec::V2_0::CecMessageType::SET_DIGITAL_TIMER;
    }
    if ((o & ::android::hardware::tv::cec::V2_0::CecMessageType::CLEAR_DIGITAL_TIMER) == static_cast<int32_t>(::android::hardware::tv::cec::V2_0::CecMessageType::CLEAR_DIGITAL_TIMER)) {
        os += (first ? "" : " | ");
        os += "CLEAR_DIGITAL_TIMER";
        first = false;
        flipped |= ::android::hardware::tv::cec::V2_0::CecMessageType::CLEAR_DIGITAL_TIMER;
    }
    if ((o & ::android::hardware::tv::cec::V2_0::CecMessageType::SET_AUDIO_RATE) == static_cast<int32_t>(::android::hardware::tv::cec::V2_0::CecMessageType::SET_AUDIO_RATE)) {
        os += (first ? "" : " | ");
        os += "SET_AUDIO_RATE";
        first = false;
        flipped |= ::android::hardware::tv::cec::V2_0::CecMessageType::SET_AUDIO_RATE;
    }
    if ((o & ::android::hardware::tv::cec::V2_0::CecMessageType::INACTIVE_SOURCE) == static_cast<int32_t>(::android::hardware::tv::cec::V2_0::CecMessageType::INACTIVE_SOURCE)) {
        os += (first ? "" : " | ");
        os += "INACTIVE_SOURCE";
        first = false;
        flipped |= ::android::hardware::tv::cec::V2_0::CecMessageType::INACTIVE_SOURCE;
    }
    if ((o & ::android::hardware::tv::cec::V2_0::CecMessageType::CEC_VERSION) == static_cast<int32_t>(::android::hardware::tv::cec::V2_0::CecMessageType::CEC_VERSION)) {
        os += (first ? "" : " | ");
        os += "CEC_VERSION";
        first = false;
        flipped |= ::android::hardware::tv::cec::V2_0::CecMessageType::CEC_VERSION;
    }
    if ((o & ::android::hardware::tv::cec::V2_0::CecMessageType::GET_CEC_VERSION) == static_cast<int32_t>(::android::hardware::tv::cec::V2_0::CecMessageType::GET_CEC_VERSION)) {
        os += (first ? "" : " | ");
        os += "GET_CEC_VERSION";
        first = false;
        flipped |= ::android::hardware::tv::cec::V2_0::CecMessageType::GET_CEC_VERSION;
    }
    if ((o & ::android::hardware::tv::cec::V2_0::CecMessageType::VENDOR_COMMAND_WITH_ID) == static_cast<int32_t>(::android::hardware::tv::cec::V2_0::CecMessageType::VENDOR_COMMAND_WITH_ID)) {
        os += (first ? "" : " | ");
        os += "VENDOR_COMMAND_WITH_ID";
        first = false;
        flipped |= ::android::hardware::tv::cec::V2_0::CecMessageType::VENDOR_COMMAND_WITH_ID;
    }
    if ((o & ::android::hardware::tv::cec::V2_0::CecMessageType::CLEAR_EXTERNAL_TIMER) == static_cast<int32_t>(::android::hardware::tv::cec::V2_0::CecMessageType::CLEAR_EXTERNAL_TIMER)) {
        os += (first ? "" : " | ");
        os += "CLEAR_EXTERNAL_TIMER";
        first = false;
        flipped |= ::android::hardware::tv::cec::V2_0::CecMessageType::CLEAR_EXTERNAL_TIMER;
    }
    if ((o & ::android::hardware::tv::cec::V2_0::CecMessageType::SET_EXTERNAL_TIMER) == static_cast<int32_t>(::android::hardware::tv::cec::V2_0::CecMessageType::SET_EXTERNAL_TIMER)) {
        os += (first ? "" : " | ");
        os += "SET_EXTERNAL_TIMER";
        first = false;
        flipped |= ::android::hardware::tv::cec::V2_0::CecMessageType::SET_EXTERNAL_TIMER;
    }
    if ((o & ::android::hardware::tv::cec::V2_0::CecMessageType::REPORT_SHORT_AUDIO_DESCRIPTOR) == static_cast<int32_t>(::android::hardware::tv::cec::V2_0::CecMessageType::REPORT_SHORT_AUDIO_DESCRIPTOR)) {
        os += (first ? "" : " | ");
        os += "REPORT_SHORT_AUDIO_DESCRIPTOR";
        first = false;
        flipped |= ::android::hardware::tv::cec::V2_0::CecMessageType::REPORT_SHORT_AUDIO_DESCRIPTOR;
    }
    if ((o & ::android::hardware::tv::cec::V2_0::CecMessageType::REQUEST_SHORT_AUDIO_DESCRIPTOR) == static_cast<int32_t>(::android::hardware::tv::cec::V2_0::CecMessageType::REQUEST_SHORT_AUDIO_DESCRIPTOR)) {
        os += (first ? "" : " | ");
        os += "REQUEST_SHORT_AUDIO_DESCRIPTOR";
        first = false;
        flipped |= ::android::hardware::tv::cec::V2_0::CecMessageType::REQUEST_SHORT_AUDIO_DESCRIPTOR;
    }
    if ((o & ::android::hardware::tv::cec::V2_0::CecMessageType::GIVE_FEATURES) == static_cast<int32_t>(::android::hardware::tv::cec::V2_0::CecMessageType::GIVE_FEATURES)) {
        os += (first ? "" : " | ");
        os += "GIVE_FEATURES";
        first = false;
        flipped |= ::android::hardware::tv::cec::V2_0::CecMessageType::GIVE_FEATURES;
    }
    if ((o & ::android::hardware::tv::cec::V2_0::CecMessageType::REPORT_FEATURES) == static_cast<int32_t>(::android::hardware::tv::cec::V2_0::CecMessageType::REPORT_FEATURES)) {
        os += (first ? "" : " | ");
        os += "REPORT_FEATURES";
        first = false;
        flipped |= ::android::hardware::tv::cec::V2_0::CecMessageType::REPORT_FEATURES;
    }
    if ((o & ::android::hardware::tv::cec::V2_0::CecMessageType::REQUEST_CURRENT_LATENCY) == static_cast<int32_t>(::android::hardware::tv::cec::V2_0::CecMessageType::REQUEST_CURRENT_LATENCY)) {
        os += (first ? "" : " | ");
        os += "REQUEST_CURRENT_LATENCY";
        first = false;
        flipped |= ::android::hardware::tv::cec::V2_0::CecMessageType::REQUEST_CURRENT_LATENCY;
    }
    if ((o & ::android::hardware::tv::cec::V2_0::CecMessageType::REPORT_CURRENT_LATENCY) == static_cast<int32_t>(::android::hardware::tv::cec::V2_0::CecMessageType::REPORT_CURRENT_LATENCY)) {
        os += (first ? "" : " | ");
        os += "REPORT_CURRENT_LATENCY";
        first = false;
        flipped |= ::android::hardware::tv::cec::V2_0::CecMessageType::REPORT_CURRENT_LATENCY;
    }
    if ((o & ::android::hardware::tv::cec::V2_0::CecMessageType::INITIATE_ARC) == static_cast<int32_t>(::android::hardware::tv::cec::V2_0::CecMessageType::INITIATE_ARC)) {
        os += (first ? "" : " | ");
        os += "INITIATE_ARC";
        first = false;
        flipped |= ::android::hardware::tv::cec::V2_0::CecMessageType::INITIATE_ARC;
    }
    if ((o & ::android::hardware::tv::cec::V2_0::CecMessageType::REPORT_ARC_INITIATED) == static_cast<int32_t>(::android::hardware::tv::cec::V2_0::CecMessageType::REPORT_ARC_INITIATED)) {
        os += (first ? "" : " | ");
        os += "REPORT_ARC_INITIATED";
        first = false;
        flipped |= ::android::hardware::tv::cec::V2_0::CecMessageType::REPORT_ARC_INITIATED;
    }
    if ((o & ::android::hardware::tv::cec::V2_0::CecMessageType::REPORT_ARC_TERMINATED) == static_cast<int32_t>(::android::hardware::tv::cec::V2_0::CecMessageType::REPORT_ARC_TERMINATED)) {
        os += (first ? "" : " | ");
        os += "REPORT_ARC_TERMINATED";
        first = false;
        flipped |= ::android::hardware::tv::cec::V2_0::CecMessageType::REPORT_ARC_TERMINATED;
    }
    if ((o & ::android::hardware::tv::cec::V2_0::CecMessageType::REQUEST_ARC_INITIATION) == static_cast<int32_t>(::android::hardware::tv::cec::V2_0::CecMessageType::REQUEST_ARC_INITIATION)) {
        os += (first ? "" : " | ");
        os += "REQUEST_ARC_INITIATION";
        first = false;
        flipped |= ::android::hardware::tv::cec::V2_0::CecMessageType::REQUEST_ARC_INITIATION;
    }
    if ((o & ::android::hardware::tv::cec::V2_0::CecMessageType::REQUEST_ARC_TERMINATION) == static_cast<int32_t>(::android::hardware::tv::cec::V2_0::CecMessageType::REQUEST_ARC_TERMINATION)) {
        os += (first ? "" : " | ");
        os += "REQUEST_ARC_TERMINATION";
        first = false;
        flipped |= ::android::hardware::tv::cec::V2_0::CecMessageType::REQUEST_ARC_TERMINATION;
    }
    if ((o & ::android::hardware::tv::cec::V2_0::CecMessageType::TERMINATE_ARC) == static_cast<int32_t>(::android::hardware::tv::cec::V2_0::CecMessageType::TERMINATE_ARC)) {
        os += (first ? "" : " | ");
        os += "TERMINATE_ARC";
        first = false;
        flipped |= ::android::hardware::tv::cec::V2_0::CecMessageType::TERMINATE_ARC;
    }
    if ((o & ::android::hardware::tv::cec::V2_0::CecMessageType::ABORT) == static_cast<int32_t>(::android::hardware::tv::cec::V2_0::CecMessageType::ABORT)) {
        os += (first ? "" : " | ");
        os += "ABORT";
        first = false;
        flipped |= ::android::hardware::tv::cec::V2_0::CecMessageType::ABORT;
    }
    if ((o & ::android::hardware::tv::cec::V2_0::CecMessageType::POLLING_MESSAGE) == static_cast<int32_t>(::android::hardware::tv::cec::V2_0::CecMessageType::POLLING_MESSAGE)) {
        os += (first ? "" : " | ");
        os += "POLLING_MESSAGE";
        first = false;
        flipped |= ::android::hardware::tv::cec::V2_0::CecMessageType::POLLING_MESSAGE;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::tv::cec::V2_0::CecMessageType o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::tv::cec::V2_0::CecMessageType::FEATURE_ABORT) {
        return "FEATURE_ABORT";
    }
    if (o == ::android::hardware::tv::cec::V2_0::CecMessageType::IMAGE_VIEW_ON) {
        return "IMAGE_VIEW_ON";
    }
    if (o == ::android::hardware::tv::cec::V2_0::CecMessageType::TUNER_STEP_INCREMENT) {
        return "TUNER_STEP_INCREMENT";
    }
    if (o == ::android::hardware::tv::cec::V2_0::CecMessageType::TUNER_STEP_DECREMENT) {
        return "TUNER_STEP_DECREMENT";
    }
    if (o == ::android::hardware::tv::cec::V2_0::CecMessageType::TUNER_DEVICE_STATUS) {
        return "TUNER_DEVICE_STATUS";
    }
    if (o == ::android::hardware::tv::cec::V2_0::CecMessageType::GIVE_TUNER_DEVICE_STATUS) {
        return "GIVE_TUNER_DEVICE_STATUS";
    }
    if (o == ::android::hardware::tv::cec::V2_0::CecMessageType::RECORD_ON) {
        return "RECORD_ON";
    }
    if (o == ::android::hardware::tv::cec::V2_0::CecMessageType::RECORD_STATUS) {
        return "RECORD_STATUS";
    }
    if (o == ::android::hardware::tv::cec::V2_0::CecMessageType::RECORD_OFF) {
        return "RECORD_OFF";
    }
    if (o == ::android::hardware::tv::cec::V2_0::CecMessageType::TEXT_VIEW_ON) {
        return "TEXT_VIEW_ON";
    }
    if (o == ::android::hardware::tv::cec::V2_0::CecMessageType::RECORD_TV_SCREEN) {
        return "RECORD_TV_SCREEN";
    }
    if (o == ::android::hardware::tv::cec::V2_0::CecMessageType::GIVE_DECK_STATUS) {
        return "GIVE_DECK_STATUS";
    }
    if (o == ::android::hardware::tv::cec::V2_0::CecMessageType::DECK_STATUS) {
        return "DECK_STATUS";
    }
    if (o == ::android::hardware::tv::cec::V2_0::CecMessageType::SET_MENU_LANGUAGE) {
        return "SET_MENU_LANGUAGE";
    }
    if (o == ::android::hardware::tv::cec::V2_0::CecMessageType::CLEAR_ANALOG_TIMER) {
        return "CLEAR_ANALOG_TIMER";
    }
    if (o == ::android::hardware::tv::cec::V2_0::CecMessageType::SET_ANALOG_TIMER) {
        return "SET_ANALOG_TIMER";
    }
    if (o == ::android::hardware::tv::cec::V2_0::CecMessageType::TIMER_STATUS) {
        return "TIMER_STATUS";
    }
    if (o == ::android::hardware::tv::cec::V2_0::CecMessageType::STANDBY) {
        return "STANDBY";
    }
    if (o == ::android::hardware::tv::cec::V2_0::CecMessageType::PLAY) {
        return "PLAY";
    }
    if (o == ::android::hardware::tv::cec::V2_0::CecMessageType::DECK_CONTROL) {
        return "DECK_CONTROL";
    }
    if (o == ::android::hardware::tv::cec::V2_0::CecMessageType::TIMER_CLEARED_STATUS) {
        return "TIMER_CLEARED_STATUS";
    }
    if (o == ::android::hardware::tv::cec::V2_0::CecMessageType::USER_CONTROL_PRESSED) {
        return "USER_CONTROL_PRESSED";
    }
    if (o == ::android::hardware::tv::cec::V2_0::CecMessageType::USER_CONTROL_RELEASED) {
        return "USER_CONTROL_RELEASED";
    }
    if (o == ::android::hardware::tv::cec::V2_0::CecMessageType::GIVE_OSD_NAME) {
        return "GIVE_OSD_NAME";
    }
    if (o == ::android::hardware::tv::cec::V2_0::CecMessageType::SET_OSD_NAME) {
        return "SET_OSD_NAME";
    }
    if (o == ::android::hardware::tv::cec::V2_0::CecMessageType::SET_OSD_STRING) {
        return "SET_OSD_STRING";
    }
    if (o == ::android::hardware::tv::cec::V2_0::CecMessageType::SET_TIMER_PROGRAM_TITLE) {
        return "SET_TIMER_PROGRAM_TITLE";
    }
    if (o == ::android::hardware::tv::cec::V2_0::CecMessageType::SYSTEM_AUDIO_MODE_REQUEST) {
        return "SYSTEM_AUDIO_MODE_REQUEST";
    }
    if (o == ::android::hardware::tv::cec::V2_0::CecMessageType::GIVE_AUDIO_STATUS) {
        return "GIVE_AUDIO_STATUS";
    }
    if (o == ::android::hardware::tv::cec::V2_0::CecMessageType::SET_SYSTEM_AUDIO_MODE) {
        return "SET_SYSTEM_AUDIO_MODE";
    }
    if (o == ::android::hardware::tv::cec::V2_0::CecMessageType::REPORT_AUDIO_STATUS) {
        return "REPORT_AUDIO_STATUS";
    }
    if (o == ::android::hardware::tv::cec::V2_0::CecMessageType::GIVE_SYSTEM_AUDIO_MODE_STATUS) {
        return "GIVE_SYSTEM_AUDIO_MODE_STATUS";
    }
    if (o == ::android::hardware::tv::cec::V2_0::CecMessageType::SYSTEM_AUDIO_MODE_STATUS) {
        return "SYSTEM_AUDIO_MODE_STATUS";
    }
    if (o == ::android::hardware::tv::cec::V2_0::CecMessageType::ROUTING_CHANGE) {
        return "ROUTING_CHANGE";
    }
    if (o == ::android::hardware::tv::cec::V2_0::CecMessageType::ROUTING_INFORMATION) {
        return "ROUTING_INFORMATION";
    }
    if (o == ::android::hardware::tv::cec::V2_0::CecMessageType::ACTIVE_SOURCE) {
        return "ACTIVE_SOURCE";
    }
    if (o == ::android::hardware::tv::cec::V2_0::CecMessageType::GIVE_PHYSICAL_ADDRESS) {
        return "GIVE_PHYSICAL_ADDRESS";
    }
    if (o == ::android::hardware::tv::cec::V2_0::CecMessageType::REPORT_PHYSICAL_ADDRESS) {
        return "REPORT_PHYSICAL_ADDRESS";
    }
    if (o == ::android::hardware::tv::cec::V2_0::CecMessageType::REQUEST_ACTIVE_SOURCE) {
        return "REQUEST_ACTIVE_SOURCE";
    }
    if (o == ::android::hardware::tv::cec::V2_0::CecMessageType::SET_STREAM_PATH) {
        return "SET_STREAM_PATH";
    }
    if (o == ::android::hardware::tv::cec::V2_0::CecMessageType::DEVICE_VENDOR_ID) {
        return "DEVICE_VENDOR_ID";
    }
    if (o == ::android::hardware::tv::cec::V2_0::CecMessageType::VENDOR_COMMAND) {
        return "VENDOR_COMMAND";
    }
    if (o == ::android::hardware::tv::cec::V2_0::CecMessageType::VENDOR_REMOTE_BUTTON_DOWN) {
        return "VENDOR_REMOTE_BUTTON_DOWN";
    }
    if (o == ::android::hardware::tv::cec::V2_0::CecMessageType::VENDOR_REMOTE_BUTTON_UP) {
        return "VENDOR_REMOTE_BUTTON_UP";
    }
    if (o == ::android::hardware::tv::cec::V2_0::CecMessageType::GIVE_DEVICE_VENDOR_ID) {
        return "GIVE_DEVICE_VENDOR_ID";
    }
    if (o == ::android::hardware::tv::cec::V2_0::CecMessageType::MENU_REQUEST) {
        return "MENU_REQUEST";
    }
    if (o == ::android::hardware::tv::cec::V2_0::CecMessageType::MENU_STATUS) {
        return "MENU_STATUS";
    }
    if (o == ::android::hardware::tv::cec::V2_0::CecMessageType::GIVE_DEVICE_POWER_STATUS) {
        return "GIVE_DEVICE_POWER_STATUS";
    }
    if (o == ::android::hardware::tv::cec::V2_0::CecMessageType::REPORT_POWER_STATUS) {
        return "REPORT_POWER_STATUS";
    }
    if (o == ::android::hardware::tv::cec::V2_0::CecMessageType::GET_MENU_LANGUAGE) {
        return "GET_MENU_LANGUAGE";
    }
    if (o == ::android::hardware::tv::cec::V2_0::CecMessageType::SELECT_ANALOG_SERVICE) {
        return "SELECT_ANALOG_SERVICE";
    }
    if (o == ::android::hardware::tv::cec::V2_0::CecMessageType::SELECT_DIGITAL_SERVICE) {
        return "SELECT_DIGITAL_SERVICE";
    }
    if (o == ::android::hardware::tv::cec::V2_0::CecMessageType::SET_DIGITAL_TIMER) {
        return "SET_DIGITAL_TIMER";
    }
    if (o == ::android::hardware::tv::cec::V2_0::CecMessageType::CLEAR_DIGITAL_TIMER) {
        return "CLEAR_DIGITAL_TIMER";
    }
    if (o == ::android::hardware::tv::cec::V2_0::CecMessageType::SET_AUDIO_RATE) {
        return "SET_AUDIO_RATE";
    }
    if (o == ::android::hardware::tv::cec::V2_0::CecMessageType::INACTIVE_SOURCE) {
        return "INACTIVE_SOURCE";
    }
    if (o == ::android::hardware::tv::cec::V2_0::CecMessageType::CEC_VERSION) {
        return "CEC_VERSION";
    }
    if (o == ::android::hardware::tv::cec::V2_0::CecMessageType::GET_CEC_VERSION) {
        return "GET_CEC_VERSION";
    }
    if (o == ::android::hardware::tv::cec::V2_0::CecMessageType::VENDOR_COMMAND_WITH_ID) {
        return "VENDOR_COMMAND_WITH_ID";
    }
    if (o == ::android::hardware::tv::cec::V2_0::CecMessageType::CLEAR_EXTERNAL_TIMER) {
        return "CLEAR_EXTERNAL_TIMER";
    }
    if (o == ::android::hardware::tv::cec::V2_0::CecMessageType::SET_EXTERNAL_TIMER) {
        return "SET_EXTERNAL_TIMER";
    }
    if (o == ::android::hardware::tv::cec::V2_0::CecMessageType::REPORT_SHORT_AUDIO_DESCRIPTOR) {
        return "REPORT_SHORT_AUDIO_DESCRIPTOR";
    }
    if (o == ::android::hardware::tv::cec::V2_0::CecMessageType::REQUEST_SHORT_AUDIO_DESCRIPTOR) {
        return "REQUEST_SHORT_AUDIO_DESCRIPTOR";
    }
    if (o == ::android::hardware::tv::cec::V2_0::CecMessageType::GIVE_FEATURES) {
        return "GIVE_FEATURES";
    }
    if (o == ::android::hardware::tv::cec::V2_0::CecMessageType::REPORT_FEATURES) {
        return "REPORT_FEATURES";
    }
    if (o == ::android::hardware::tv::cec::V2_0::CecMessageType::REQUEST_CURRENT_LATENCY) {
        return "REQUEST_CURRENT_LATENCY";
    }
    if (o == ::android::hardware::tv::cec::V2_0::CecMessageType::REPORT_CURRENT_LATENCY) {
        return "REPORT_CURRENT_LATENCY";
    }
    if (o == ::android::hardware::tv::cec::V2_0::CecMessageType::INITIATE_ARC) {
        return "INITIATE_ARC";
    }
    if (o == ::android::hardware::tv::cec::V2_0::CecMessageType::REPORT_ARC_INITIATED) {
        return "REPORT_ARC_INITIATED";
    }
    if (o == ::android::hardware::tv::cec::V2_0::CecMessageType::REPORT_ARC_TERMINATED) {
        return "REPORT_ARC_TERMINATED";
    }
    if (o == ::android::hardware::tv::cec::V2_0::CecMessageType::REQUEST_ARC_INITIATION) {
        return "REQUEST_ARC_INITIATION";
    }
    if (o == ::android::hardware::tv::cec::V2_0::CecMessageType::REQUEST_ARC_TERMINATION) {
        return "REQUEST_ARC_TERMINATION";
    }
    if (o == ::android::hardware::tv::cec::V2_0::CecMessageType::TERMINATE_ARC) {
        return "TERMINATE_ARC";
    }
    if (o == ::android::hardware::tv::cec::V2_0::CecMessageType::ABORT) {
        return "ABORT";
    }
    if (o == ::android::hardware::tv::cec::V2_0::CecMessageType::POLLING_MESSAGE) {
        return "POLLING_MESSAGE";
    }
    std::string os;
    os += toHexString(static_cast<int32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::tv::cec::V2_0::CecMessageType o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::tv::cec::V2_0::AbortReason>(int32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::tv::cec::V2_0::AbortReason> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::tv::cec::V2_0::AbortReason::UNRECOGNIZED_MODE) == static_cast<int32_t>(::android::hardware::tv::cec::V2_0::AbortReason::UNRECOGNIZED_MODE)) {
        os += (first ? "" : " | ");
        os += "UNRECOGNIZED_MODE";
        first = false;
        flipped |= ::android::hardware::tv::cec::V2_0::AbortReason::UNRECOGNIZED_MODE;
    }
    if ((o & ::android::hardware::tv::cec::V2_0::AbortReason::NOT_IN_CORRECT_MODE) == static_cast<int32_t>(::android::hardware::tv::cec::V2_0::AbortReason::NOT_IN_CORRECT_MODE)) {
        os += (first ? "" : " | ");
        os += "NOT_IN_CORRECT_MODE";
        first = false;
        flipped |= ::android::hardware::tv::cec::V2_0::AbortReason::NOT_IN_CORRECT_MODE;
    }
    if ((o & ::android::hardware::tv::cec::V2_0::AbortReason::CANNOT_PROVIDE_SOURCE) == static_cast<int32_t>(::android::hardware::tv::cec::V2_0::AbortReason::CANNOT_PROVIDE_SOURCE)) {
        os += (first ? "" : " | ");
        os += "CANNOT_PROVIDE_SOURCE";
        first = false;
        flipped |= ::android::hardware::tv::cec::V2_0::AbortReason::CANNOT_PROVIDE_SOURCE;
    }
    if ((o & ::android::hardware::tv::cec::V2_0::AbortReason::INVALID_OPERAND) == static_cast<int32_t>(::android::hardware::tv::cec::V2_0::AbortReason::INVALID_OPERAND)) {
        os += (first ? "" : " | ");
        os += "INVALID_OPERAND";
        first = false;
        flipped |= ::android::hardware::tv::cec::V2_0::AbortReason::INVALID_OPERAND;
    }
    if ((o & ::android::hardware::tv::cec::V2_0::AbortReason::REFUSED) == static_cast<int32_t>(::android::hardware::tv::cec::V2_0::AbortReason::REFUSED)) {
        os += (first ? "" : " | ");
        os += "REFUSED";
        first = false;
        flipped |= ::android::hardware::tv::cec::V2_0::AbortReason::REFUSED;
    }
    if ((o & ::android::hardware::tv::cec::V2_0::AbortReason::UNABLE_TO_DETERMINE) == static_cast<int32_t>(::android::hardware::tv::cec::V2_0::AbortReason::UNABLE_TO_DETERMINE)) {
        os += (first ? "" : " | ");
        os += "UNABLE_TO_DETERMINE";
        first = false;
        flipped |= ::android::hardware::tv::cec::V2_0::AbortReason::UNABLE_TO_DETERMINE;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::tv::cec::V2_0::AbortReason o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::tv::cec::V2_0::AbortReason::UNRECOGNIZED_MODE) {
        return "UNRECOGNIZED_MODE";
    }
    if (o == ::android::hardware::tv::cec::V2_0::AbortReason::NOT_IN_CORRECT_MODE) {
        return "NOT_IN_CORRECT_MODE";
    }
    if (o == ::android::hardware::tv::cec::V2_0::AbortReason::CANNOT_PROVIDE_SOURCE) {
        return "CANNOT_PROVIDE_SOURCE";
    }
    if (o == ::android::hardware::tv::cec::V2_0::AbortReason::INVALID_OPERAND) {
        return "INVALID_OPERAND";
    }
    if (o == ::android::hardware::tv::cec::V2_0::AbortReason::REFUSED) {
        return "REFUSED";
    }
    if (o == ::android::hardware::tv::cec::V2_0::AbortReason::UNABLE_TO_DETERMINE) {
        return "UNABLE_TO_DETERMINE";
    }
    std::string os;
    os += toHexString(static_cast<int32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::tv::cec::V2_0::AbortReason o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::tv::cec::V2_0::MaxLength>(int32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::tv::cec::V2_0::MaxLength> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::tv::cec::V2_0::MaxLength::MESSAGE_BODY) == static_cast<int32_t>(::android::hardware::tv::cec::V2_0::MaxLength::MESSAGE_BODY)) {
        os += (first ? "" : " | ");
        os += "MESSAGE_BODY";
        first = false;
        flipped |= ::android::hardware::tv::cec::V2_0::MaxLength::MESSAGE_BODY;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::tv::cec::V2_0::MaxLength o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::tv::cec::V2_0::MaxLength::MESSAGE_BODY) {
        return "MESSAGE_BODY";
    }
    std::string os;
    os += toHexString(static_cast<int32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::tv::cec::V2_0::MaxLength o, ::std::ostream* os) {
    *os << toString(o);
}

static inline std::string toString(const ::android::hardware::tv::cec::V2_0::CecMessage& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".initiator = ";
    os += ::android::hardware::tv::cec::V2_0::toString(o.initiator);
    os += ", .destination = ";
    os += ::android::hardware::tv::cec::V2_0::toString(o.destination);
    os += ", .cecMessageType = ";
    os += ::android::hardware::tv::cec::V2_0::toString(o.cecMessageType);
    os += ", .body = ";
    os += ::android::hardware::toString(o.body);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::tv::cec::V2_0::CecMessage& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::tv::cec::V2_0::CecMessage& lhs, const ::android::hardware::tv::cec::V2_0::CecMessage& rhs) {
    if (lhs.initiator != rhs.initiator) {
        return false;
    }
    if (lhs.destination != rhs.destination) {
        return false;
    }
    if (lhs.cecMessageType != rhs.cecMessageType) {
        return false;
    }
    if (lhs.body != rhs.body) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::tv::cec::V2_0::CecMessage& lhs, const ::android::hardware::tv::cec::V2_0::CecMessage& rhs){
    return !(lhs == rhs);
}

template<>
inline std::string toString<::android::hardware::tv::cec::V2_0::SendMessageResult>(int32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::tv::cec::V2_0::SendMessageResult> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::tv::cec::V2_0::SendMessageResult::SUCCESS) == static_cast<int32_t>(::android::hardware::tv::cec::V2_0::SendMessageResult::SUCCESS)) {
        os += (first ? "" : " | ");
        os += "SUCCESS";
        first = false;
        flipped |= ::android::hardware::tv::cec::V2_0::SendMessageResult::SUCCESS;
    }
    if ((o & ::android::hardware::tv::cec::V2_0::SendMessageResult::NACK) == static_cast<int32_t>(::android::hardware::tv::cec::V2_0::SendMessageResult::NACK)) {
        os += (first ? "" : " | ");
        os += "NACK";
        first = false;
        flipped |= ::android::hardware::tv::cec::V2_0::SendMessageResult::NACK;
    }
    if ((o & ::android::hardware::tv::cec::V2_0::SendMessageResult::BUSY) == static_cast<int32_t>(::android::hardware::tv::cec::V2_0::SendMessageResult::BUSY)) {
        os += (first ? "" : " | ");
        os += "BUSY";
        first = false;
        flipped |= ::android::hardware::tv::cec::V2_0::SendMessageResult::BUSY;
    }
    if ((o & ::android::hardware::tv::cec::V2_0::SendMessageResult::FAIL) == static_cast<int32_t>(::android::hardware::tv::cec::V2_0::SendMessageResult::FAIL)) {
        os += (first ? "" : " | ");
        os += "FAIL";
        first = false;
        flipped |= ::android::hardware::tv::cec::V2_0::SendMessageResult::FAIL;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::tv::cec::V2_0::SendMessageResult o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::tv::cec::V2_0::SendMessageResult::SUCCESS) {
        return "SUCCESS";
    }
    if (o == ::android::hardware::tv::cec::V2_0::SendMessageResult::NACK) {
        return "NACK";
    }
    if (o == ::android::hardware::tv::cec::V2_0::SendMessageResult::BUSY) {
        return "BUSY";
    }
    if (o == ::android::hardware::tv::cec::V2_0::SendMessageResult::FAIL) {
        return "FAIL";
    }
    std::string os;
    os += toHexString(static_cast<int32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::tv::cec::V2_0::SendMessageResult o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::tv::cec::V2_0::CecAllDeviceTypeValue>(uint8_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::tv::cec::V2_0::CecAllDeviceTypeValue> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::tv::cec::V2_0::CecAllDeviceTypeValue::RESERVED_DEVICE_2) == static_cast<uint8_t>(::android::hardware::tv::cec::V2_0::CecAllDeviceTypeValue::RESERVED_DEVICE_2)) {
        os += (first ? "" : " | ");
        os += "RESERVED_DEVICE_2";
        first = false;
        flipped |= ::android::hardware::tv::cec::V2_0::CecAllDeviceTypeValue::RESERVED_DEVICE_2;
    }
    if ((o & ::android::hardware::tv::cec::V2_0::CecAllDeviceTypeValue::RESERVED_DEVICE_1) == static_cast<uint8_t>(::android::hardware::tv::cec::V2_0::CecAllDeviceTypeValue::RESERVED_DEVICE_1)) {
        os += (first ? "" : " | ");
        os += "RESERVED_DEVICE_1";
        first = false;
        flipped |= ::android::hardware::tv::cec::V2_0::CecAllDeviceTypeValue::RESERVED_DEVICE_1;
    }
    if ((o & ::android::hardware::tv::cec::V2_0::CecAllDeviceTypeValue::CEC_SWITCH_DEVICE) == static_cast<uint8_t>(::android::hardware::tv::cec::V2_0::CecAllDeviceTypeValue::CEC_SWITCH_DEVICE)) {
        os += (first ? "" : " | ");
        os += "CEC_SWITCH_DEVICE";
        first = false;
        flipped |= ::android::hardware::tv::cec::V2_0::CecAllDeviceTypeValue::CEC_SWITCH_DEVICE;
    }
    if ((o & ::android::hardware::tv::cec::V2_0::CecAllDeviceTypeValue::AUDIO_DEVICE) == static_cast<uint8_t>(::android::hardware::tv::cec::V2_0::CecAllDeviceTypeValue::AUDIO_DEVICE)) {
        os += (first ? "" : " | ");
        os += "AUDIO_DEVICE";
        first = false;
        flipped |= ::android::hardware::tv::cec::V2_0::CecAllDeviceTypeValue::AUDIO_DEVICE;
    }
    if ((o & ::android::hardware::tv::cec::V2_0::CecAllDeviceTypeValue::PLAYBACK_DEVICE) == static_cast<uint8_t>(::android::hardware::tv::cec::V2_0::CecAllDeviceTypeValue::PLAYBACK_DEVICE)) {
        os += (first ? "" : " | ");
        os += "PLAYBACK_DEVICE";
        first = false;
        flipped |= ::android::hardware::tv::cec::V2_0::CecAllDeviceTypeValue::PLAYBACK_DEVICE;
    }
    if ((o & ::android::hardware::tv::cec::V2_0::CecAllDeviceTypeValue::TUNER_DEVICE) == static_cast<uint8_t>(::android::hardware::tv::cec::V2_0::CecAllDeviceTypeValue::TUNER_DEVICE)) {
        os += (first ? "" : " | ");
        os += "TUNER_DEVICE";
        first = false;
        flipped |= ::android::hardware::tv::cec::V2_0::CecAllDeviceTypeValue::TUNER_DEVICE;
    }
    if ((o & ::android::hardware::tv::cec::V2_0::CecAllDeviceTypeValue::RECORDING_DEVICE) == static_cast<uint8_t>(::android::hardware::tv::cec::V2_0::CecAllDeviceTypeValue::RECORDING_DEVICE)) {
        os += (first ? "" : " | ");
        os += "RECORDING_DEVICE";
        first = false;
        flipped |= ::android::hardware::tv::cec::V2_0::CecAllDeviceTypeValue::RECORDING_DEVICE;
    }
    if ((o & ::android::hardware::tv::cec::V2_0::CecAllDeviceTypeValue::TV_DEVICE) == static_cast<uint8_t>(::android::hardware::tv::cec::V2_0::CecAllDeviceTypeValue::TV_DEVICE)) {
        os += (first ? "" : " | ");
        os += "TV_DEVICE";
        first = false;
        flipped |= ::android::hardware::tv::cec::V2_0::CecAllDeviceTypeValue::TV_DEVICE;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::tv::cec::V2_0::CecAllDeviceTypeValue o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::tv::cec::V2_0::CecAllDeviceTypeValue::RESERVED_DEVICE_2) {
        return "RESERVED_DEVICE_2";
    }
    if (o == ::android::hardware::tv::cec::V2_0::CecAllDeviceTypeValue::RESERVED_DEVICE_1) {
        return "RESERVED_DEVICE_1";
    }
    if (o == ::android::hardware::tv::cec::V2_0::CecAllDeviceTypeValue::CEC_SWITCH_DEVICE) {
        return "CEC_SWITCH_DEVICE";
    }
    if (o == ::android::hardware::tv::cec::V2_0::CecAllDeviceTypeValue::AUDIO_DEVICE) {
        return "AUDIO_DEVICE";
    }
    if (o == ::android::hardware::tv::cec::V2_0::CecAllDeviceTypeValue::PLAYBACK_DEVICE) {
        return "PLAYBACK_DEVICE";
    }
    if (o == ::android::hardware::tv::cec::V2_0::CecAllDeviceTypeValue::TUNER_DEVICE) {
        return "TUNER_DEVICE";
    }
    if (o == ::android::hardware::tv::cec::V2_0::CecAllDeviceTypeValue::RECORDING_DEVICE) {
        return "RECORDING_DEVICE";
    }
    if (o == ::android::hardware::tv::cec::V2_0::CecAllDeviceTypeValue::TV_DEVICE) {
        return "TV_DEVICE";
    }
    std::string os;
    os += toHexString(static_cast<uint8_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::tv::cec::V2_0::CecAllDeviceTypeValue o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::tv::cec::V2_0::CecVersion>(int32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::tv::cec::V2_0::CecVersion> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::tv::cec::V2_0::CecVersion::V_1_3_A) == static_cast<int32_t>(::android::hardware::tv::cec::V2_0::CecVersion::V_1_3_A)) {
        os += (first ? "" : " | ");
        os += "V_1_3_A";
        first = false;
        flipped |= ::android::hardware::tv::cec::V2_0::CecVersion::V_1_3_A;
    }
    if ((o & ::android::hardware::tv::cec::V2_0::CecVersion::V_1_4) == static_cast<int32_t>(::android::hardware::tv::cec::V2_0::CecVersion::V_1_4)) {
        os += (first ? "" : " | ");
        os += "V_1_4";
        first = false;
        flipped |= ::android::hardware::tv::cec::V2_0::CecVersion::V_1_4;
    }
    if ((o & ::android::hardware::tv::cec::V2_0::CecVersion::V_2_0) == static_cast<int32_t>(::android::hardware::tv::cec::V2_0::CecVersion::V_2_0)) {
        os += (first ? "" : " | ");
        os += "V_2_0";
        first = false;
        flipped |= ::android::hardware::tv::cec::V2_0::CecVersion::V_2_0;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::tv::cec::V2_0::CecVersion o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::tv::cec::V2_0::CecVersion::V_1_3_A) {
        return "V_1_3_A";
    }
    if (o == ::android::hardware::tv::cec::V2_0::CecVersion::V_1_4) {
        return "V_1_4";
    }
    if (o == ::android::hardware::tv::cec::V2_0::CecVersion::V_2_0) {
        return "V_2_0";
    }
    std::string os;
    os += toHexString(static_cast<int32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::tv::cec::V2_0::CecVersion o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::tv::cec::V2_0::CecDeviceFeature>(uint32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::tv::cec::V2_0::CecDeviceFeature> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::tv::cec::V2_0::CecDeviceFeature::RESERVED) == static_cast<uint32_t>(::android::hardware::tv::cec::V2_0::CecDeviceFeature::RESERVED)) {
        os += (first ? "" : " | ");
        os += "RESERVED";
        first = false;
        flipped |= ::android::hardware::tv::cec::V2_0::CecDeviceFeature::RESERVED;
    }
    if ((o & ::android::hardware::tv::cec::V2_0::CecDeviceFeature::SOURCE_SUPPORT_ARC_RX) == static_cast<uint32_t>(::android::hardware::tv::cec::V2_0::CecDeviceFeature::SOURCE_SUPPORT_ARC_RX)) {
        os += (first ? "" : " | ");
        os += "SOURCE_SUPPORT_ARC_RX";
        first = false;
        flipped |= ::android::hardware::tv::cec::V2_0::CecDeviceFeature::SOURCE_SUPPORT_ARC_RX;
    }
    if ((o & ::android::hardware::tv::cec::V2_0::CecDeviceFeature::SINK_SUPPORT_ARC_TX) == static_cast<uint32_t>(::android::hardware::tv::cec::V2_0::CecDeviceFeature::SINK_SUPPORT_ARC_TX)) {
        os += (first ? "" : " | ");
        os += "SINK_SUPPORT_ARC_TX";
        first = false;
        flipped |= ::android::hardware::tv::cec::V2_0::CecDeviceFeature::SINK_SUPPORT_ARC_TX;
    }
    if ((o & ::android::hardware::tv::cec::V2_0::CecDeviceFeature::SOURCE_SUPPORT_SET_AUDIO_RATE) == static_cast<uint32_t>(::android::hardware::tv::cec::V2_0::CecDeviceFeature::SOURCE_SUPPORT_SET_AUDIO_RATE)) {
        os += (first ? "" : " | ");
        os += "SOURCE_SUPPORT_SET_AUDIO_RATE";
        first = false;
        flipped |= ::android::hardware::tv::cec::V2_0::CecDeviceFeature::SOURCE_SUPPORT_SET_AUDIO_RATE;
    }
    if ((o & ::android::hardware::tv::cec::V2_0::CecDeviceFeature::SUPPORT_CONTROLLED_BY_DECK) == static_cast<uint32_t>(::android::hardware::tv::cec::V2_0::CecDeviceFeature::SUPPORT_CONTROLLED_BY_DECK)) {
        os += (first ? "" : " | ");
        os += "SUPPORT_CONTROLLED_BY_DECK";
        first = false;
        flipped |= ::android::hardware::tv::cec::V2_0::CecDeviceFeature::SUPPORT_CONTROLLED_BY_DECK;
    }
    if ((o & ::android::hardware::tv::cec::V2_0::CecDeviceFeature::TV_SUPPORT_SET_OSD_STRINGS) == static_cast<uint32_t>(::android::hardware::tv::cec::V2_0::CecDeviceFeature::TV_SUPPORT_SET_OSD_STRINGS)) {
        os += (first ? "" : " | ");
        os += "TV_SUPPORT_SET_OSD_STRINGS";
        first = false;
        flipped |= ::android::hardware::tv::cec::V2_0::CecDeviceFeature::TV_SUPPORT_SET_OSD_STRINGS;
    }
    if ((o & ::android::hardware::tv::cec::V2_0::CecDeviceFeature::TV_SUPPORT_RECORD_TV_SCREEN) == static_cast<uint32_t>(::android::hardware::tv::cec::V2_0::CecDeviceFeature::TV_SUPPORT_RECORD_TV_SCREEN)) {
        os += (first ? "" : " | ");
        os += "TV_SUPPORT_RECORD_TV_SCREEN";
        first = false;
        flipped |= ::android::hardware::tv::cec::V2_0::CecDeviceFeature::TV_SUPPORT_RECORD_TV_SCREEN;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::tv::cec::V2_0::CecDeviceFeature o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::tv::cec::V2_0::CecDeviceFeature::RESERVED) {
        return "RESERVED";
    }
    if (o == ::android::hardware::tv::cec::V2_0::CecDeviceFeature::SOURCE_SUPPORT_ARC_RX) {
        return "SOURCE_SUPPORT_ARC_RX";
    }
    if (o == ::android::hardware::tv::cec::V2_0::CecDeviceFeature::SINK_SUPPORT_ARC_TX) {
        return "SINK_SUPPORT_ARC_TX";
    }
    if (o == ::android::hardware::tv::cec::V2_0::CecDeviceFeature::SOURCE_SUPPORT_SET_AUDIO_RATE) {
        return "SOURCE_SUPPORT_SET_AUDIO_RATE";
    }
    if (o == ::android::hardware::tv::cec::V2_0::CecDeviceFeature::SUPPORT_CONTROLLED_BY_DECK) {
        return "SUPPORT_CONTROLLED_BY_DECK";
    }
    if (o == ::android::hardware::tv::cec::V2_0::CecDeviceFeature::TV_SUPPORT_SET_OSD_STRINGS) {
        return "TV_SUPPORT_SET_OSD_STRINGS";
    }
    if (o == ::android::hardware::tv::cec::V2_0::CecDeviceFeature::TV_SUPPORT_RECORD_TV_SCREEN) {
        return "TV_SUPPORT_RECORD_TV_SCREEN";
    }
    std::string os;
    os += toHexString(static_cast<uint32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::tv::cec::V2_0::CecDeviceFeature o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::tv::cec::V2_0::CecRcProfileId>(uint8_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::tv::cec::V2_0::CecRcProfileId> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::tv::cec::V2_0::CecRcProfileId::NONE) == static_cast<uint8_t>(::android::hardware::tv::cec::V2_0::CecRcProfileId::NONE)) {
        os += (first ? "" : " | ");
        os += "NONE";
        first = false;
        flipped |= ::android::hardware::tv::cec::V2_0::CecRcProfileId::NONE;
    }
    if ((o & ::android::hardware::tv::cec::V2_0::CecRcProfileId::RC_PROFILE_1) == static_cast<uint8_t>(::android::hardware::tv::cec::V2_0::CecRcProfileId::RC_PROFILE_1)) {
        os += (first ? "" : " | ");
        os += "RC_PROFILE_1";
        first = false;
        flipped |= ::android::hardware::tv::cec::V2_0::CecRcProfileId::RC_PROFILE_1;
    }
    if ((o & ::android::hardware::tv::cec::V2_0::CecRcProfileId::RC_PROFILE_2) == static_cast<uint8_t>(::android::hardware::tv::cec::V2_0::CecRcProfileId::RC_PROFILE_2)) {
        os += (first ? "" : " | ");
        os += "RC_PROFILE_2";
        first = false;
        flipped |= ::android::hardware::tv::cec::V2_0::CecRcProfileId::RC_PROFILE_2;
    }
    if ((o & ::android::hardware::tv::cec::V2_0::CecRcProfileId::RC_PROFILE_3) == static_cast<uint8_t>(::android::hardware::tv::cec::V2_0::CecRcProfileId::RC_PROFILE_3)) {
        os += (first ? "" : " | ");
        os += "RC_PROFILE_3";
        first = false;
        flipped |= ::android::hardware::tv::cec::V2_0::CecRcProfileId::RC_PROFILE_3;
    }
    if ((o & ::android::hardware::tv::cec::V2_0::CecRcProfileId::RC_PROFILE_4) == static_cast<uint8_t>(::android::hardware::tv::cec::V2_0::CecRcProfileId::RC_PROFILE_4)) {
        os += (first ? "" : " | ");
        os += "RC_PROFILE_4";
        first = false;
        flipped |= ::android::hardware::tv::cec::V2_0::CecRcProfileId::RC_PROFILE_4;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::tv::cec::V2_0::CecRcProfileId o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::tv::cec::V2_0::CecRcProfileId::NONE) {
        return "NONE";
    }
    if (o == ::android::hardware::tv::cec::V2_0::CecRcProfileId::RC_PROFILE_1) {
        return "RC_PROFILE_1";
    }
    if (o == ::android::hardware::tv::cec::V2_0::CecRcProfileId::RC_PROFILE_2) {
        return "RC_PROFILE_2";
    }
    if (o == ::android::hardware::tv::cec::V2_0::CecRcProfileId::RC_PROFILE_3) {
        return "RC_PROFILE_3";
    }
    if (o == ::android::hardware::tv::cec::V2_0::CecRcProfileId::RC_PROFILE_4) {
        return "RC_PROFILE_4";
    }
    std::string os;
    os += toHexString(static_cast<uint8_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::tv::cec::V2_0::CecRcProfileId o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::tv::cec::V2_0::CecRcProfileSource>(uint8_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::tv::cec::V2_0::CecRcProfileSource> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::tv::cec::V2_0::CecRcProfileSource::MEDIA_CONTEXT_SENSITIVE) == static_cast<uint8_t>(::android::hardware::tv::cec::V2_0::CecRcProfileSource::MEDIA_CONTEXT_SENSITIVE)) {
        os += (first ? "" : " | ");
        os += "MEDIA_CONTEXT_SENSITIVE";
        first = false;
        flipped |= ::android::hardware::tv::cec::V2_0::CecRcProfileSource::MEDIA_CONTEXT_SENSITIVE;
    }
    if ((o & ::android::hardware::tv::cec::V2_0::CecRcProfileSource::MEDIA_TO) == static_cast<uint8_t>(::android::hardware::tv::cec::V2_0::CecRcProfileSource::MEDIA_TO)) {
        os += (first ? "" : " | ");
        os += "MEDIA_TO";
        first = false;
        flipped |= ::android::hardware::tv::cec::V2_0::CecRcProfileSource::MEDIA_TO;
    }
    if ((o & ::android::hardware::tv::cec::V2_0::CecRcProfileSource::CONTENTS) == static_cast<uint8_t>(::android::hardware::tv::cec::V2_0::CecRcProfileSource::CONTENTS)) {
        os += (first ? "" : " | ");
        os += "CONTENTS";
        first = false;
        flipped |= ::android::hardware::tv::cec::V2_0::CecRcProfileSource::CONTENTS;
    }
    if ((o & ::android::hardware::tv::cec::V2_0::CecRcProfileSource::DEVICE_SETUP) == static_cast<uint8_t>(::android::hardware::tv::cec::V2_0::CecRcProfileSource::DEVICE_SETUP)) {
        os += (first ? "" : " | ");
        os += "DEVICE_SETUP";
        first = false;
        flipped |= ::android::hardware::tv::cec::V2_0::CecRcProfileSource::DEVICE_SETUP;
    }
    if ((o & ::android::hardware::tv::cec::V2_0::CecRcProfileSource::DEVICE_ROOT) == static_cast<uint8_t>(::android::hardware::tv::cec::V2_0::CecRcProfileSource::DEVICE_ROOT)) {
        os += (first ? "" : " | ");
        os += "DEVICE_ROOT";
        first = false;
        flipped |= ::android::hardware::tv::cec::V2_0::CecRcProfileSource::DEVICE_ROOT;
    }
    if ((o & ::android::hardware::tv::cec::V2_0::CecRcProfileSource::SOURCE_FLAG) == static_cast<uint8_t>(::android::hardware::tv::cec::V2_0::CecRcProfileSource::SOURCE_FLAG)) {
        os += (first ? "" : " | ");
        os += "SOURCE_FLAG";
        first = false;
        flipped |= ::android::hardware::tv::cec::V2_0::CecRcProfileSource::SOURCE_FLAG;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::tv::cec::V2_0::CecRcProfileSource o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::tv::cec::V2_0::CecRcProfileSource::MEDIA_CONTEXT_SENSITIVE) {
        return "MEDIA_CONTEXT_SENSITIVE";
    }
    if (o == ::android::hardware::tv::cec::V2_0::CecRcProfileSource::MEDIA_TO) {
        return "MEDIA_TO";
    }
    if (o == ::android::hardware::tv::cec::V2_0::CecRcProfileSource::CONTENTS) {
        return "CONTENTS";
    }
    if (o == ::android::hardware::tv::cec::V2_0::CecRcProfileSource::DEVICE_SETUP) {
        return "DEVICE_SETUP";
    }
    if (o == ::android::hardware::tv::cec::V2_0::CecRcProfileSource::DEVICE_ROOT) {
        return "DEVICE_ROOT";
    }
    if (o == ::android::hardware::tv::cec::V2_0::CecRcProfileSource::SOURCE_FLAG) {
        return "SOURCE_FLAG";
    }
    std::string os;
    os += toHexString(static_cast<uint8_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::tv::cec::V2_0::CecRcProfileSource o, ::std::ostream* os) {
    *os << toString(o);
}

static inline std::string toString(const ::android::hardware::tv::cec::V2_0::CecRcProfile1& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";

    switch (o.getDiscriminator()) {
        case ::android::hardware::tv::cec::V2_0::CecRcProfile1::hidl_discriminator::profileId: {
            os += ".profileId = ";
            os += toString(o.profileId());
            break;
        }
        case ::android::hardware::tv::cec::V2_0::CecRcProfile1::hidl_discriminator::profileSource: {
            os += ".profileSource = ";
            os += toString(o.profileSource());
            break;
        }
        default: {
            ::android::hardware::details::logAlwaysFatal((
                    "Unknown union discriminator (value: " +
                    std::to_string((uint8_t) o.getDiscriminator()) + ").").c_str());
        }
    }
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::tv::cec::V2_0::CecRcProfile1& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::tv::cec::V2_0::CecRcProfile1& lhs, const ::android::hardware::tv::cec::V2_0::CecRcProfile1& rhs) {
    if (lhs.getDiscriminator() != rhs.getDiscriminator()) {
        return false;
    }
    switch (lhs.getDiscriminator()) {
        case ::android::hardware::tv::cec::V2_0::CecRcProfile1::hidl_discriminator::profileId: {
            return (lhs.profileId() == rhs.profileId());
        }
        case ::android::hardware::tv::cec::V2_0::CecRcProfile1::hidl_discriminator::profileSource: {
            return (lhs.profileSource() == rhs.profileSource());
        }
        default: {
            ::android::hardware::details::logAlwaysFatal((
                    "Unknown union discriminator (value: " +
                    std::to_string((uint8_t) lhs.getDiscriminator()) + ").").c_str());
        }
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::tv::cec::V2_0::CecRcProfile1& lhs, const ::android::hardware::tv::cec::V2_0::CecRcProfile1& rhs){
    return !(lhs == rhs);
}

static inline std::string toString(const ::android::hardware::tv::cec::V2_0::CecRcProfile& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".rcProfile1 = ";
    os += ::android::hardware::tv::cec::V2_0::toString(o.rcProfile1);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::tv::cec::V2_0::CecRcProfile& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::tv::cec::V2_0::CecRcProfile& lhs, const ::android::hardware::tv::cec::V2_0::CecRcProfile& rhs) {
    if (lhs.rcProfile1 != rhs.rcProfile1) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::tv::cec::V2_0::CecRcProfile& lhs, const ::android::hardware::tv::cec::V2_0::CecRcProfile& rhs){
    return !(lhs == rhs);
}

template<>
inline std::string toString<::android::hardware::tv::cec::V2_0::CecPowerState>(int8_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::tv::cec::V2_0::CecPowerState> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::tv::cec::V2_0::CecPowerState::ON) == static_cast<int8_t>(::android::hardware::tv::cec::V2_0::CecPowerState::ON)) {
        os += (first ? "" : " | ");
        os += "ON";
        first = false;
        flipped |= ::android::hardware::tv::cec::V2_0::CecPowerState::ON;
    }
    if ((o & ::android::hardware::tv::cec::V2_0::CecPowerState::STANDBY) == static_cast<int8_t>(::android::hardware::tv::cec::V2_0::CecPowerState::STANDBY)) {
        os += (first ? "" : " | ");
        os += "STANDBY";
        first = false;
        flipped |= ::android::hardware::tv::cec::V2_0::CecPowerState::STANDBY;
    }
    if ((o & ::android::hardware::tv::cec::V2_0::CecPowerState::ON_TO_STANDBY) == static_cast<int8_t>(::android::hardware::tv::cec::V2_0::CecPowerState::ON_TO_STANDBY)) {
        os += (first ? "" : " | ");
        os += "ON_TO_STANDBY";
        first = false;
        flipped |= ::android::hardware::tv::cec::V2_0::CecPowerState::ON_TO_STANDBY;
    }
    if ((o & ::android::hardware::tv::cec::V2_0::CecPowerState::STANDBY_TO_ON) == static_cast<int8_t>(::android::hardware::tv::cec::V2_0::CecPowerState::STANDBY_TO_ON)) {
        os += (first ? "" : " | ");
        os += "STANDBY_TO_ON";
        first = false;
        flipped |= ::android::hardware::tv::cec::V2_0::CecPowerState::STANDBY_TO_ON;
    }
    if ((o & ::android::hardware::tv::cec::V2_0::CecPowerState::UNKNOWN) == static_cast<int8_t>(::android::hardware::tv::cec::V2_0::CecPowerState::UNKNOWN)) {
        os += (first ? "" : " | ");
        os += "UNKNOWN";
        first = false;
        flipped |= ::android::hardware::tv::cec::V2_0::CecPowerState::UNKNOWN;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::tv::cec::V2_0::CecPowerState o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::tv::cec::V2_0::CecPowerState::ON) {
        return "ON";
    }
    if (o == ::android::hardware::tv::cec::V2_0::CecPowerState::STANDBY) {
        return "STANDBY";
    }
    if (o == ::android::hardware::tv::cec::V2_0::CecPowerState::ON_TO_STANDBY) {
        return "ON_TO_STANDBY";
    }
    if (o == ::android::hardware::tv::cec::V2_0::CecPowerState::STANDBY_TO_ON) {
        return "STANDBY_TO_ON";
    }
    if (o == ::android::hardware::tv::cec::V2_0::CecPowerState::UNKNOWN) {
        return "UNKNOWN";
    }
    std::string os;
    os += toHexString(static_cast<int8_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::tv::cec::V2_0::CecPowerState o, ::std::ostream* os) {
    *os << toString(o);
}

static inline std::string toString(const ::android::hardware::tv::cec::V2_0::CecDeviceInfo& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".version = ";
    os += ::android::hardware::tv::cec::V2_0::toString(o.version);
    os += ", .devceType = ";
    os += ::android::hardware::tv::cec::V2_0::toString(o.devceType);
    os += ", .allDeviceTypes = ";
    os += ::android::hardware::tv::cec::V2_0::toString<::android::hardware::tv::cec::V2_0::CecAllDeviceTypeValue>(o.allDeviceTypes);
    os += ", .deviceFeatures = ";
    os += ::android::hardware::tv::cec::V2_0::toString<::android::hardware::tv::cec::V2_0::CecDeviceFeature>(o.deviceFeatures);
    os += ", .rcProfile = ";
    os += ::android::hardware::tv::cec::V2_0::toString(o.rcProfile);
    os += ", .vendorId = ";
    os += ::android::hardware::toString(o.vendorId);
    os += ", .logicalAddress = ";
    os += ::android::hardware::tv::cec::V2_0::toString(o.logicalAddress);
    os += ", .physicalAddress = ";
    os += ::android::hardware::toString(o.physicalAddress);
    os += ", .powerState = ";
    os += ::android::hardware::tv::cec::V2_0::toString(o.powerState);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::tv::cec::V2_0::CecDeviceInfo& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::tv::cec::V2_0::CecDeviceInfo& lhs, const ::android::hardware::tv::cec::V2_0::CecDeviceInfo& rhs) {
    if (lhs.version != rhs.version) {
        return false;
    }
    if (lhs.devceType != rhs.devceType) {
        return false;
    }
    if (lhs.allDeviceTypes != rhs.allDeviceTypes) {
        return false;
    }
    if (lhs.deviceFeatures != rhs.deviceFeatures) {
        return false;
    }
    if (lhs.rcProfile != rhs.rcProfile) {
        return false;
    }
    if (lhs.vendorId != rhs.vendorId) {
        return false;
    }
    if (lhs.logicalAddress != rhs.logicalAddress) {
        return false;
    }
    if (lhs.physicalAddress != rhs.physicalAddress) {
        return false;
    }
    if (lhs.powerState != rhs.powerState) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::tv::cec::V2_0::CecDeviceInfo& lhs, const ::android::hardware::tv::cec::V2_0::CecDeviceInfo& rhs){
    return !(lhs == rhs);
}

template<>
inline std::string toString<::android::hardware::tv::cec::V2_0::CecTopologyEventType>(int32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::tv::cec::V2_0::CecTopologyEventType> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::tv::cec::V2_0::CecTopologyEventType::DEVICE_ADDED) == static_cast<int32_t>(::android::hardware::tv::cec::V2_0::CecTopologyEventType::DEVICE_ADDED)) {
        os += (first ? "" : " | ");
        os += "DEVICE_ADDED";
        first = false;
        flipped |= ::android::hardware::tv::cec::V2_0::CecTopologyEventType::DEVICE_ADDED;
    }
    if ((o & ::android::hardware::tv::cec::V2_0::CecTopologyEventType::DEVICE_REMOVED) == static_cast<int32_t>(::android::hardware::tv::cec::V2_0::CecTopologyEventType::DEVICE_REMOVED)) {
        os += (first ? "" : " | ");
        os += "DEVICE_REMOVED";
        first = false;
        flipped |= ::android::hardware::tv::cec::V2_0::CecTopologyEventType::DEVICE_REMOVED;
    }
    if ((o & ::android::hardware::tv::cec::V2_0::CecTopologyEventType::DEVICE_UPDATED) == static_cast<int32_t>(::android::hardware::tv::cec::V2_0::CecTopologyEventType::DEVICE_UPDATED)) {
        os += (first ? "" : " | ");
        os += "DEVICE_UPDATED";
        first = false;
        flipped |= ::android::hardware::tv::cec::V2_0::CecTopologyEventType::DEVICE_UPDATED;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::tv::cec::V2_0::CecTopologyEventType o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::tv::cec::V2_0::CecTopologyEventType::DEVICE_ADDED) {
        return "DEVICE_ADDED";
    }
    if (o == ::android::hardware::tv::cec::V2_0::CecTopologyEventType::DEVICE_REMOVED) {
        return "DEVICE_REMOVED";
    }
    if (o == ::android::hardware::tv::cec::V2_0::CecTopologyEventType::DEVICE_UPDATED) {
        return "DEVICE_UPDATED";
    }
    std::string os;
    os += toHexString(static_cast<int32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::tv::cec::V2_0::CecTopologyEventType o, ::std::ostream* os) {
    *os << toString(o);
}

static inline std::string toString(const ::android::hardware::tv::cec::V2_0::CecTopologyEvent& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".eventType = ";
    os += ::android::hardware::tv::cec::V2_0::toString(o.eventType);
    os += ", .logicalAddress = ";
    os += ::android::hardware::tv::cec::V2_0::toString(o.logicalAddress);
    os += ", .physicalAddress = ";
    os += ::android::hardware::toString(o.physicalAddress);
    os += ", .isHostDevice = ";
    os += ::android::hardware::toString(o.isHostDevice);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::tv::cec::V2_0::CecTopologyEvent& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::tv::cec::V2_0::CecTopologyEvent& lhs, const ::android::hardware::tv::cec::V2_0::CecTopologyEvent& rhs) {
    if (lhs.eventType != rhs.eventType) {
        return false;
    }
    if (lhs.logicalAddress != rhs.logicalAddress) {
        return false;
    }
    if (lhs.physicalAddress != rhs.physicalAddress) {
        return false;
    }
    if (lhs.isHostDevice != rhs.isHostDevice) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::tv::cec::V2_0::CecTopologyEvent& lhs, const ::android::hardware::tv::cec::V2_0::CecTopologyEvent& rhs){
    return !(lhs == rhs);
}

template<>
inline std::string toString<::android::hardware::tv::cec::V2_0::CecUICommandCodes>(int32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::tv::cec::V2_0::CecUICommandCodes> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::tv::cec::V2_0::CecUICommandCodes::SELECT_OK) == static_cast<int32_t>(::android::hardware::tv::cec::V2_0::CecUICommandCodes::SELECT_OK)) {
        os += (first ? "" : " | ");
        os += "SELECT_OK";
        first = false;
        flipped |= ::android::hardware::tv::cec::V2_0::CecUICommandCodes::SELECT_OK;
    }
    if ((o & ::android::hardware::tv::cec::V2_0::CecUICommandCodes::UP) == static_cast<int32_t>(::android::hardware::tv::cec::V2_0::CecUICommandCodes::UP)) {
        os += (first ? "" : " | ");
        os += "UP";
        first = false;
        flipped |= ::android::hardware::tv::cec::V2_0::CecUICommandCodes::UP;
    }
    if ((o & ::android::hardware::tv::cec::V2_0::CecUICommandCodes::DOWN) == static_cast<int32_t>(::android::hardware::tv::cec::V2_0::CecUICommandCodes::DOWN)) {
        os += (first ? "" : " | ");
        os += "DOWN";
        first = false;
        flipped |= ::android::hardware::tv::cec::V2_0::CecUICommandCodes::DOWN;
    }
    if ((o & ::android::hardware::tv::cec::V2_0::CecUICommandCodes::LEFT) == static_cast<int32_t>(::android::hardware::tv::cec::V2_0::CecUICommandCodes::LEFT)) {
        os += (first ? "" : " | ");
        os += "LEFT";
        first = false;
        flipped |= ::android::hardware::tv::cec::V2_0::CecUICommandCodes::LEFT;
    }
    if ((o & ::android::hardware::tv::cec::V2_0::CecUICommandCodes::RIGHT) == static_cast<int32_t>(::android::hardware::tv::cec::V2_0::CecUICommandCodes::RIGHT)) {
        os += (first ? "" : " | ");
        os += "RIGHT";
        first = false;
        flipped |= ::android::hardware::tv::cec::V2_0::CecUICommandCodes::RIGHT;
    }
    if ((o & ::android::hardware::tv::cec::V2_0::CecUICommandCodes::RIGHT_UP) == static_cast<int32_t>(::android::hardware::tv::cec::V2_0::CecUICommandCodes::RIGHT_UP)) {
        os += (first ? "" : " | ");
        os += "RIGHT_UP";
        first = false;
        flipped |= ::android::hardware::tv::cec::V2_0::CecUICommandCodes::RIGHT_UP;
    }
    if ((o & ::android::hardware::tv::cec::V2_0::CecUICommandCodes::RIGHT_DOWN) == static_cast<int32_t>(::android::hardware::tv::cec::V2_0::CecUICommandCodes::RIGHT_DOWN)) {
        os += (first ? "" : " | ");
        os += "RIGHT_DOWN";
        first = false;
        flipped |= ::android::hardware::tv::cec::V2_0::CecUICommandCodes::RIGHT_DOWN;
    }
    if ((o & ::android::hardware::tv::cec::V2_0::CecUICommandCodes::LEFT_UP) == static_cast<int32_t>(::android::hardware::tv::cec::V2_0::CecUICommandCodes::LEFT_UP)) {
        os += (first ? "" : " | ");
        os += "LEFT_UP";
        first = false;
        flipped |= ::android::hardware::tv::cec::V2_0::CecUICommandCodes::LEFT_UP;
    }
    if ((o & ::android::hardware::tv::cec::V2_0::CecUICommandCodes::LEFT_DOWN) == static_cast<int32_t>(::android::hardware::tv::cec::V2_0::CecUICommandCodes::LEFT_DOWN)) {
        os += (first ? "" : " | ");
        os += "LEFT_DOWN";
        first = false;
        flipped |= ::android::hardware::tv::cec::V2_0::CecUICommandCodes::LEFT_DOWN;
    }
    if ((o & ::android::hardware::tv::cec::V2_0::CecUICommandCodes::DEVICE_ROOT_MENU) == static_cast<int32_t>(::android::hardware::tv::cec::V2_0::CecUICommandCodes::DEVICE_ROOT_MENU)) {
        os += (first ? "" : " | ");
        os += "DEVICE_ROOT_MENU";
        first = false;
        flipped |= ::android::hardware::tv::cec::V2_0::CecUICommandCodes::DEVICE_ROOT_MENU;
    }
    if ((o & ::android::hardware::tv::cec::V2_0::CecUICommandCodes::DEVICE_SETUP_MENU) == static_cast<int32_t>(::android::hardware::tv::cec::V2_0::CecUICommandCodes::DEVICE_SETUP_MENU)) {
        os += (first ? "" : " | ");
        os += "DEVICE_SETUP_MENU";
        first = false;
        flipped |= ::android::hardware::tv::cec::V2_0::CecUICommandCodes::DEVICE_SETUP_MENU;
    }
    if ((o & ::android::hardware::tv::cec::V2_0::CecUICommandCodes::CONTENTS_MENU) == static_cast<int32_t>(::android::hardware::tv::cec::V2_0::CecUICommandCodes::CONTENTS_MENU)) {
        os += (first ? "" : " | ");
        os += "CONTENTS_MENU";
        first = false;
        flipped |= ::android::hardware::tv::cec::V2_0::CecUICommandCodes::CONTENTS_MENU;
    }
    if ((o & ::android::hardware::tv::cec::V2_0::CecUICommandCodes::FAVORITE_MENU) == static_cast<int32_t>(::android::hardware::tv::cec::V2_0::CecUICommandCodes::FAVORITE_MENU)) {
        os += (first ? "" : " | ");
        os += "FAVORITE_MENU";
        first = false;
        flipped |= ::android::hardware::tv::cec::V2_0::CecUICommandCodes::FAVORITE_MENU;
    }
    if ((o & ::android::hardware::tv::cec::V2_0::CecUICommandCodes::BACK) == static_cast<int32_t>(::android::hardware::tv::cec::V2_0::CecUICommandCodes::BACK)) {
        os += (first ? "" : " | ");
        os += "BACK";
        first = false;
        flipped |= ::android::hardware::tv::cec::V2_0::CecUICommandCodes::BACK;
    }
    if ((o & ::android::hardware::tv::cec::V2_0::CecUICommandCodes::MEDIA_TOP_MENU) == static_cast<int32_t>(::android::hardware::tv::cec::V2_0::CecUICommandCodes::MEDIA_TOP_MENU)) {
        os += (first ? "" : " | ");
        os += "MEDIA_TOP_MENU";
        first = false;
        flipped |= ::android::hardware::tv::cec::V2_0::CecUICommandCodes::MEDIA_TOP_MENU;
    }
    if ((o & ::android::hardware::tv::cec::V2_0::CecUICommandCodes::MEDIA_CONTEXT_SENSITIVE_MENU) == static_cast<int32_t>(::android::hardware::tv::cec::V2_0::CecUICommandCodes::MEDIA_CONTEXT_SENSITIVE_MENU)) {
        os += (first ? "" : " | ");
        os += "MEDIA_CONTEXT_SENSITIVE_MENU";
        first = false;
        flipped |= ::android::hardware::tv::cec::V2_0::CecUICommandCodes::MEDIA_CONTEXT_SENSITIVE_MENU;
    }
    if ((o & ::android::hardware::tv::cec::V2_0::CecUICommandCodes::NUMBER_ENTRY_MODE) == static_cast<int32_t>(::android::hardware::tv::cec::V2_0::CecUICommandCodes::NUMBER_ENTRY_MODE)) {
        os += (first ? "" : " | ");
        os += "NUMBER_ENTRY_MODE";
        first = false;
        flipped |= ::android::hardware::tv::cec::V2_0::CecUICommandCodes::NUMBER_ENTRY_MODE;
    }
    if ((o & ::android::hardware::tv::cec::V2_0::CecUICommandCodes::NUMBER_11) == static_cast<int32_t>(::android::hardware::tv::cec::V2_0::CecUICommandCodes::NUMBER_11)) {
        os += (first ? "" : " | ");
        os += "NUMBER_11";
        first = false;
        flipped |= ::android::hardware::tv::cec::V2_0::CecUICommandCodes::NUMBER_11;
    }
    if ((o & ::android::hardware::tv::cec::V2_0::CecUICommandCodes::NUMBER_12) == static_cast<int32_t>(::android::hardware::tv::cec::V2_0::CecUICommandCodes::NUMBER_12)) {
        os += (first ? "" : " | ");
        os += "NUMBER_12";
        first = false;
        flipped |= ::android::hardware::tv::cec::V2_0::CecUICommandCodes::NUMBER_12;
    }
    if ((o & ::android::hardware::tv::cec::V2_0::CecUICommandCodes::NUMBER_0) == static_cast<int32_t>(::android::hardware::tv::cec::V2_0::CecUICommandCodes::NUMBER_0)) {
        os += (first ? "" : " | ");
        os += "NUMBER_0";
        first = false;
        flipped |= ::android::hardware::tv::cec::V2_0::CecUICommandCodes::NUMBER_0;
    }
    if ((o & ::android::hardware::tv::cec::V2_0::CecUICommandCodes::NUMBER_1) == static_cast<int32_t>(::android::hardware::tv::cec::V2_0::CecUICommandCodes::NUMBER_1)) {
        os += (first ? "" : " | ");
        os += "NUMBER_1";
        first = false;
        flipped |= ::android::hardware::tv::cec::V2_0::CecUICommandCodes::NUMBER_1;
    }
    if ((o & ::android::hardware::tv::cec::V2_0::CecUICommandCodes::NUMBER_2) == static_cast<int32_t>(::android::hardware::tv::cec::V2_0::CecUICommandCodes::NUMBER_2)) {
        os += (first ? "" : " | ");
        os += "NUMBER_2";
        first = false;
        flipped |= ::android::hardware::tv::cec::V2_0::CecUICommandCodes::NUMBER_2;
    }
    if ((o & ::android::hardware::tv::cec::V2_0::CecUICommandCodes::NUMBER_3) == static_cast<int32_t>(::android::hardware::tv::cec::V2_0::CecUICommandCodes::NUMBER_3)) {
        os += (first ? "" : " | ");
        os += "NUMBER_3";
        first = false;
        flipped |= ::android::hardware::tv::cec::V2_0::CecUICommandCodes::NUMBER_3;
    }
    if ((o & ::android::hardware::tv::cec::V2_0::CecUICommandCodes::NUMBER_4) == static_cast<int32_t>(::android::hardware::tv::cec::V2_0::CecUICommandCodes::NUMBER_4)) {
        os += (first ? "" : " | ");
        os += "NUMBER_4";
        first = false;
        flipped |= ::android::hardware::tv::cec::V2_0::CecUICommandCodes::NUMBER_4;
    }
    if ((o & ::android::hardware::tv::cec::V2_0::CecUICommandCodes::NUMBER_5) == static_cast<int32_t>(::android::hardware::tv::cec::V2_0::CecUICommandCodes::NUMBER_5)) {
        os += (first ? "" : " | ");
        os += "NUMBER_5";
        first = false;
        flipped |= ::android::hardware::tv::cec::V2_0::CecUICommandCodes::NUMBER_5;
    }
    if ((o & ::android::hardware::tv::cec::V2_0::CecUICommandCodes::NUMBER_6) == static_cast<int32_t>(::android::hardware::tv::cec::V2_0::CecUICommandCodes::NUMBER_6)) {
        os += (first ? "" : " | ");
        os += "NUMBER_6";
        first = false;
        flipped |= ::android::hardware::tv::cec::V2_0::CecUICommandCodes::NUMBER_6;
    }
    if ((o & ::android::hardware::tv::cec::V2_0::CecUICommandCodes::NUMBER_7) == static_cast<int32_t>(::android::hardware::tv::cec::V2_0::CecUICommandCodes::NUMBER_7)) {
        os += (first ? "" : " | ");
        os += "NUMBER_7";
        first = false;
        flipped |= ::android::hardware::tv::cec::V2_0::CecUICommandCodes::NUMBER_7;
    }
    if ((o & ::android::hardware::tv::cec::V2_0::CecUICommandCodes::NUMBER_8) == static_cast<int32_t>(::android::hardware::tv::cec::V2_0::CecUICommandCodes::NUMBER_8)) {
        os += (first ? "" : " | ");
        os += "NUMBER_8";
        first = false;
        flipped |= ::android::hardware::tv::cec::V2_0::CecUICommandCodes::NUMBER_8;
    }
    if ((o & ::android::hardware::tv::cec::V2_0::CecUICommandCodes::NUMBER_9) == static_cast<int32_t>(::android::hardware::tv::cec::V2_0::CecUICommandCodes::NUMBER_9)) {
        os += (first ? "" : " | ");
        os += "NUMBER_9";
        first = false;
        flipped |= ::android::hardware::tv::cec::V2_0::CecUICommandCodes::NUMBER_9;
    }
    if ((o & ::android::hardware::tv::cec::V2_0::CecUICommandCodes::DOT) == static_cast<int32_t>(::android::hardware::tv::cec::V2_0::CecUICommandCodes::DOT)) {
        os += (first ? "" : " | ");
        os += "DOT";
        first = false;
        flipped |= ::android::hardware::tv::cec::V2_0::CecUICommandCodes::DOT;
    }
    if ((o & ::android::hardware::tv::cec::V2_0::CecUICommandCodes::ENTER) == static_cast<int32_t>(::android::hardware::tv::cec::V2_0::CecUICommandCodes::ENTER)) {
        os += (first ? "" : " | ");
        os += "ENTER";
        first = false;
        flipped |= ::android::hardware::tv::cec::V2_0::CecUICommandCodes::ENTER;
    }
    if ((o & ::android::hardware::tv::cec::V2_0::CecUICommandCodes::CLEAR) == static_cast<int32_t>(::android::hardware::tv::cec::V2_0::CecUICommandCodes::CLEAR)) {
        os += (first ? "" : " | ");
        os += "CLEAR";
        first = false;
        flipped |= ::android::hardware::tv::cec::V2_0::CecUICommandCodes::CLEAR;
    }
    if ((o & ::android::hardware::tv::cec::V2_0::CecUICommandCodes::NEXT_FAVORITE) == static_cast<int32_t>(::android::hardware::tv::cec::V2_0::CecUICommandCodes::NEXT_FAVORITE)) {
        os += (first ? "" : " | ");
        os += "NEXT_FAVORITE";
        first = false;
        flipped |= ::android::hardware::tv::cec::V2_0::CecUICommandCodes::NEXT_FAVORITE;
    }
    if ((o & ::android::hardware::tv::cec::V2_0::CecUICommandCodes::CHANNEL_UP) == static_cast<int32_t>(::android::hardware::tv::cec::V2_0::CecUICommandCodes::CHANNEL_UP)) {
        os += (first ? "" : " | ");
        os += "CHANNEL_UP";
        first = false;
        flipped |= ::android::hardware::tv::cec::V2_0::CecUICommandCodes::CHANNEL_UP;
    }
    if ((o & ::android::hardware::tv::cec::V2_0::CecUICommandCodes::CHANNEL_DOWN) == static_cast<int32_t>(::android::hardware::tv::cec::V2_0::CecUICommandCodes::CHANNEL_DOWN)) {
        os += (first ? "" : " | ");
        os += "CHANNEL_DOWN";
        first = false;
        flipped |= ::android::hardware::tv::cec::V2_0::CecUICommandCodes::CHANNEL_DOWN;
    }
    if ((o & ::android::hardware::tv::cec::V2_0::CecUICommandCodes::PREVIOUS_CHANNEL) == static_cast<int32_t>(::android::hardware::tv::cec::V2_0::CecUICommandCodes::PREVIOUS_CHANNEL)) {
        os += (first ? "" : " | ");
        os += "PREVIOUS_CHANNEL";
        first = false;
        flipped |= ::android::hardware::tv::cec::V2_0::CecUICommandCodes::PREVIOUS_CHANNEL;
    }
    if ((o & ::android::hardware::tv::cec::V2_0::CecUICommandCodes::SOUND_SELECT) == static_cast<int32_t>(::android::hardware::tv::cec::V2_0::CecUICommandCodes::SOUND_SELECT)) {
        os += (first ? "" : " | ");
        os += "SOUND_SELECT";
        first = false;
        flipped |= ::android::hardware::tv::cec::V2_0::CecUICommandCodes::SOUND_SELECT;
    }
    if ((o & ::android::hardware::tv::cec::V2_0::CecUICommandCodes::INPUT_SELECT) == static_cast<int32_t>(::android::hardware::tv::cec::V2_0::CecUICommandCodes::INPUT_SELECT)) {
        os += (first ? "" : " | ");
        os += "INPUT_SELECT";
        first = false;
        flipped |= ::android::hardware::tv::cec::V2_0::CecUICommandCodes::INPUT_SELECT;
    }
    if ((o & ::android::hardware::tv::cec::V2_0::CecUICommandCodes::DISPLAY_INFORMATION) == static_cast<int32_t>(::android::hardware::tv::cec::V2_0::CecUICommandCodes::DISPLAY_INFORMATION)) {
        os += (first ? "" : " | ");
        os += "DISPLAY_INFORMATION";
        first = false;
        flipped |= ::android::hardware::tv::cec::V2_0::CecUICommandCodes::DISPLAY_INFORMATION;
    }
    if ((o & ::android::hardware::tv::cec::V2_0::CecUICommandCodes::HELP) == static_cast<int32_t>(::android::hardware::tv::cec::V2_0::CecUICommandCodes::HELP)) {
        os += (first ? "" : " | ");
        os += "HELP";
        first = false;
        flipped |= ::android::hardware::tv::cec::V2_0::CecUICommandCodes::HELP;
    }
    if ((o & ::android::hardware::tv::cec::V2_0::CecUICommandCodes::PAGE_UP) == static_cast<int32_t>(::android::hardware::tv::cec::V2_0::CecUICommandCodes::PAGE_UP)) {
        os += (first ? "" : " | ");
        os += "PAGE_UP";
        first = false;
        flipped |= ::android::hardware::tv::cec::V2_0::CecUICommandCodes::PAGE_UP;
    }
    if ((o & ::android::hardware::tv::cec::V2_0::CecUICommandCodes::PAGE_DOWN) == static_cast<int32_t>(::android::hardware::tv::cec::V2_0::CecUICommandCodes::PAGE_DOWN)) {
        os += (first ? "" : " | ");
        os += "PAGE_DOWN";
        first = false;
        flipped |= ::android::hardware::tv::cec::V2_0::CecUICommandCodes::PAGE_DOWN;
    }
    if ((o & ::android::hardware::tv::cec::V2_0::CecUICommandCodes::POWER) == static_cast<int32_t>(::android::hardware::tv::cec::V2_0::CecUICommandCodes::POWER)) {
        os += (first ? "" : " | ");
        os += "POWER";
        first = false;
        flipped |= ::android::hardware::tv::cec::V2_0::CecUICommandCodes::POWER;
    }
    if ((o & ::android::hardware::tv::cec::V2_0::CecUICommandCodes::VOLUME_UP) == static_cast<int32_t>(::android::hardware::tv::cec::V2_0::CecUICommandCodes::VOLUME_UP)) {
        os += (first ? "" : " | ");
        os += "VOLUME_UP";
        first = false;
        flipped |= ::android::hardware::tv::cec::V2_0::CecUICommandCodes::VOLUME_UP;
    }
    if ((o & ::android::hardware::tv::cec::V2_0::CecUICommandCodes::VOLUME_DOWN) == static_cast<int32_t>(::android::hardware::tv::cec::V2_0::CecUICommandCodes::VOLUME_DOWN)) {
        os += (first ? "" : " | ");
        os += "VOLUME_DOWN";
        first = false;
        flipped |= ::android::hardware::tv::cec::V2_0::CecUICommandCodes::VOLUME_DOWN;
    }
    if ((o & ::android::hardware::tv::cec::V2_0::CecUICommandCodes::MUTE) == static_cast<int32_t>(::android::hardware::tv::cec::V2_0::CecUICommandCodes::MUTE)) {
        os += (first ? "" : " | ");
        os += "MUTE";
        first = false;
        flipped |= ::android::hardware::tv::cec::V2_0::CecUICommandCodes::MUTE;
    }
    if ((o & ::android::hardware::tv::cec::V2_0::CecUICommandCodes::PLAY) == static_cast<int32_t>(::android::hardware::tv::cec::V2_0::CecUICommandCodes::PLAY)) {
        os += (first ? "" : " | ");
        os += "PLAY";
        first = false;
        flipped |= ::android::hardware::tv::cec::V2_0::CecUICommandCodes::PLAY;
    }
    if ((o & ::android::hardware::tv::cec::V2_0::CecUICommandCodes::STOP) == static_cast<int32_t>(::android::hardware::tv::cec::V2_0::CecUICommandCodes::STOP)) {
        os += (first ? "" : " | ");
        os += "STOP";
        first = false;
        flipped |= ::android::hardware::tv::cec::V2_0::CecUICommandCodes::STOP;
    }
    if ((o & ::android::hardware::tv::cec::V2_0::CecUICommandCodes::PAUSE) == static_cast<int32_t>(::android::hardware::tv::cec::V2_0::CecUICommandCodes::PAUSE)) {
        os += (first ? "" : " | ");
        os += "PAUSE";
        first = false;
        flipped |= ::android::hardware::tv::cec::V2_0::CecUICommandCodes::PAUSE;
    }
    if ((o & ::android::hardware::tv::cec::V2_0::CecUICommandCodes::RECORD) == static_cast<int32_t>(::android::hardware::tv::cec::V2_0::CecUICommandCodes::RECORD)) {
        os += (first ? "" : " | ");
        os += "RECORD";
        first = false;
        flipped |= ::android::hardware::tv::cec::V2_0::CecUICommandCodes::RECORD;
    }
    if ((o & ::android::hardware::tv::cec::V2_0::CecUICommandCodes::REWIND) == static_cast<int32_t>(::android::hardware::tv::cec::V2_0::CecUICommandCodes::REWIND)) {
        os += (first ? "" : " | ");
        os += "REWIND";
        first = false;
        flipped |= ::android::hardware::tv::cec::V2_0::CecUICommandCodes::REWIND;
    }
    if ((o & ::android::hardware::tv::cec::V2_0::CecUICommandCodes::FAST_FORWARD) == static_cast<int32_t>(::android::hardware::tv::cec::V2_0::CecUICommandCodes::FAST_FORWARD)) {
        os += (first ? "" : " | ");
        os += "FAST_FORWARD";
        first = false;
        flipped |= ::android::hardware::tv::cec::V2_0::CecUICommandCodes::FAST_FORWARD;
    }
    if ((o & ::android::hardware::tv::cec::V2_0::CecUICommandCodes::EJECT) == static_cast<int32_t>(::android::hardware::tv::cec::V2_0::CecUICommandCodes::EJECT)) {
        os += (first ? "" : " | ");
        os += "EJECT";
        first = false;
        flipped |= ::android::hardware::tv::cec::V2_0::CecUICommandCodes::EJECT;
    }
    if ((o & ::android::hardware::tv::cec::V2_0::CecUICommandCodes::SKIP_FORWARD) == static_cast<int32_t>(::android::hardware::tv::cec::V2_0::CecUICommandCodes::SKIP_FORWARD)) {
        os += (first ? "" : " | ");
        os += "SKIP_FORWARD";
        first = false;
        flipped |= ::android::hardware::tv::cec::V2_0::CecUICommandCodes::SKIP_FORWARD;
    }
    if ((o & ::android::hardware::tv::cec::V2_0::CecUICommandCodes::SKIP_BACKWARD) == static_cast<int32_t>(::android::hardware::tv::cec::V2_0::CecUICommandCodes::SKIP_BACKWARD)) {
        os += (first ? "" : " | ");
        os += "SKIP_BACKWARD";
        first = false;
        flipped |= ::android::hardware::tv::cec::V2_0::CecUICommandCodes::SKIP_BACKWARD;
    }
    if ((o & ::android::hardware::tv::cec::V2_0::CecUICommandCodes::STOP_RECORD) == static_cast<int32_t>(::android::hardware::tv::cec::V2_0::CecUICommandCodes::STOP_RECORD)) {
        os += (first ? "" : " | ");
        os += "STOP_RECORD";
        first = false;
        flipped |= ::android::hardware::tv::cec::V2_0::CecUICommandCodes::STOP_RECORD;
    }
    if ((o & ::android::hardware::tv::cec::V2_0::CecUICommandCodes::PAUSE_RECORD) == static_cast<int32_t>(::android::hardware::tv::cec::V2_0::CecUICommandCodes::PAUSE_RECORD)) {
        os += (first ? "" : " | ");
        os += "PAUSE_RECORD";
        first = false;
        flipped |= ::android::hardware::tv::cec::V2_0::CecUICommandCodes::PAUSE_RECORD;
    }
    if ((o & ::android::hardware::tv::cec::V2_0::CecUICommandCodes::ANGLE) == static_cast<int32_t>(::android::hardware::tv::cec::V2_0::CecUICommandCodes::ANGLE)) {
        os += (first ? "" : " | ");
        os += "ANGLE";
        first = false;
        flipped |= ::android::hardware::tv::cec::V2_0::CecUICommandCodes::ANGLE;
    }
    if ((o & ::android::hardware::tv::cec::V2_0::CecUICommandCodes::SUB_PICTURE) == static_cast<int32_t>(::android::hardware::tv::cec::V2_0::CecUICommandCodes::SUB_PICTURE)) {
        os += (first ? "" : " | ");
        os += "SUB_PICTURE";
        first = false;
        flipped |= ::android::hardware::tv::cec::V2_0::CecUICommandCodes::SUB_PICTURE;
    }
    if ((o & ::android::hardware::tv::cec::V2_0::CecUICommandCodes::VIDEO_ON_DEMAND) == static_cast<int32_t>(::android::hardware::tv::cec::V2_0::CecUICommandCodes::VIDEO_ON_DEMAND)) {
        os += (first ? "" : " | ");
        os += "VIDEO_ON_DEMAND";
        first = false;
        flipped |= ::android::hardware::tv::cec::V2_0::CecUICommandCodes::VIDEO_ON_DEMAND;
    }
    if ((o & ::android::hardware::tv::cec::V2_0::CecUICommandCodes::ELECTRONIC_PROGRAM_GUIDE) == static_cast<int32_t>(::android::hardware::tv::cec::V2_0::CecUICommandCodes::ELECTRONIC_PROGRAM_GUIDE)) {
        os += (first ? "" : " | ");
        os += "ELECTRONIC_PROGRAM_GUIDE";
        first = false;
        flipped |= ::android::hardware::tv::cec::V2_0::CecUICommandCodes::ELECTRONIC_PROGRAM_GUIDE;
    }
    if ((o & ::android::hardware::tv::cec::V2_0::CecUICommandCodes::TIMER_PROGRAMMING) == static_cast<int32_t>(::android::hardware::tv::cec::V2_0::CecUICommandCodes::TIMER_PROGRAMMING)) {
        os += (first ? "" : " | ");
        os += "TIMER_PROGRAMMING";
        first = false;
        flipped |= ::android::hardware::tv::cec::V2_0::CecUICommandCodes::TIMER_PROGRAMMING;
    }
    if ((o & ::android::hardware::tv::cec::V2_0::CecUICommandCodes::INITIAL_CONFIGURATION) == static_cast<int32_t>(::android::hardware::tv::cec::V2_0::CecUICommandCodes::INITIAL_CONFIGURATION)) {
        os += (first ? "" : " | ");
        os += "INITIAL_CONFIGURATION";
        first = false;
        flipped |= ::android::hardware::tv::cec::V2_0::CecUICommandCodes::INITIAL_CONFIGURATION;
    }
    if ((o & ::android::hardware::tv::cec::V2_0::CecUICommandCodes::SELECT_BROADCAST_TYPE) == static_cast<int32_t>(::android::hardware::tv::cec::V2_0::CecUICommandCodes::SELECT_BROADCAST_TYPE)) {
        os += (first ? "" : " | ");
        os += "SELECT_BROADCAST_TYPE";
        first = false;
        flipped |= ::android::hardware::tv::cec::V2_0::CecUICommandCodes::SELECT_BROADCAST_TYPE;
    }
    if ((o & ::android::hardware::tv::cec::V2_0::CecUICommandCodes::SELECT_SOUND_PRESENTATION) == static_cast<int32_t>(::android::hardware::tv::cec::V2_0::CecUICommandCodes::SELECT_SOUND_PRESENTATION)) {
        os += (first ? "" : " | ");
        os += "SELECT_SOUND_PRESENTATION";
        first = false;
        flipped |= ::android::hardware::tv::cec::V2_0::CecUICommandCodes::SELECT_SOUND_PRESENTATION;
    }
    if ((o & ::android::hardware::tv::cec::V2_0::CecUICommandCodes::AUDIO_DESCRIPTION) == static_cast<int32_t>(::android::hardware::tv::cec::V2_0::CecUICommandCodes::AUDIO_DESCRIPTION)) {
        os += (first ? "" : " | ");
        os += "AUDIO_DESCRIPTION";
        first = false;
        flipped |= ::android::hardware::tv::cec::V2_0::CecUICommandCodes::AUDIO_DESCRIPTION;
    }
    if ((o & ::android::hardware::tv::cec::V2_0::CecUICommandCodes::INTERNET) == static_cast<int32_t>(::android::hardware::tv::cec::V2_0::CecUICommandCodes::INTERNET)) {
        os += (first ? "" : " | ");
        os += "INTERNET";
        first = false;
        flipped |= ::android::hardware::tv::cec::V2_0::CecUICommandCodes::INTERNET;
    }
    if ((o & ::android::hardware::tv::cec::V2_0::CecUICommandCodes::THREE_DIMENSIONAL_MODE) == static_cast<int32_t>(::android::hardware::tv::cec::V2_0::CecUICommandCodes::THREE_DIMENSIONAL_MODE)) {
        os += (first ? "" : " | ");
        os += "THREE_DIMENSIONAL_MODE";
        first = false;
        flipped |= ::android::hardware::tv::cec::V2_0::CecUICommandCodes::THREE_DIMENSIONAL_MODE;
    }
    if ((o & ::android::hardware::tv::cec::V2_0::CecUICommandCodes::PLAY_FUNCTION) == static_cast<int32_t>(::android::hardware::tv::cec::V2_0::CecUICommandCodes::PLAY_FUNCTION)) {
        os += (first ? "" : " | ");
        os += "PLAY_FUNCTION";
        first = false;
        flipped |= ::android::hardware::tv::cec::V2_0::CecUICommandCodes::PLAY_FUNCTION;
    }
    if ((o & ::android::hardware::tv::cec::V2_0::CecUICommandCodes::PAUSE_PLAY_FUNCTION) == static_cast<int32_t>(::android::hardware::tv::cec::V2_0::CecUICommandCodes::PAUSE_PLAY_FUNCTION)) {
        os += (first ? "" : " | ");
        os += "PAUSE_PLAY_FUNCTION";
        first = false;
        flipped |= ::android::hardware::tv::cec::V2_0::CecUICommandCodes::PAUSE_PLAY_FUNCTION;
    }
    if ((o & ::android::hardware::tv::cec::V2_0::CecUICommandCodes::RECORD_FUNCTION) == static_cast<int32_t>(::android::hardware::tv::cec::V2_0::CecUICommandCodes::RECORD_FUNCTION)) {
        os += (first ? "" : " | ");
        os += "RECORD_FUNCTION";
        first = false;
        flipped |= ::android::hardware::tv::cec::V2_0::CecUICommandCodes::RECORD_FUNCTION;
    }
    if ((o & ::android::hardware::tv::cec::V2_0::CecUICommandCodes::PAUSE_RECORD_FUNCTION) == static_cast<int32_t>(::android::hardware::tv::cec::V2_0::CecUICommandCodes::PAUSE_RECORD_FUNCTION)) {
        os += (first ? "" : " | ");
        os += "PAUSE_RECORD_FUNCTION";
        first = false;
        flipped |= ::android::hardware::tv::cec::V2_0::CecUICommandCodes::PAUSE_RECORD_FUNCTION;
    }
    if ((o & ::android::hardware::tv::cec::V2_0::CecUICommandCodes::STOP_FUNCTION) == static_cast<int32_t>(::android::hardware::tv::cec::V2_0::CecUICommandCodes::STOP_FUNCTION)) {
        os += (first ? "" : " | ");
        os += "STOP_FUNCTION";
        first = false;
        flipped |= ::android::hardware::tv::cec::V2_0::CecUICommandCodes::STOP_FUNCTION;
    }
    if ((o & ::android::hardware::tv::cec::V2_0::CecUICommandCodes::MUTE_FUNCTION) == static_cast<int32_t>(::android::hardware::tv::cec::V2_0::CecUICommandCodes::MUTE_FUNCTION)) {
        os += (first ? "" : " | ");
        os += "MUTE_FUNCTION";
        first = false;
        flipped |= ::android::hardware::tv::cec::V2_0::CecUICommandCodes::MUTE_FUNCTION;
    }
    if ((o & ::android::hardware::tv::cec::V2_0::CecUICommandCodes::RESTORE_VOLUME_FUNCTION) == static_cast<int32_t>(::android::hardware::tv::cec::V2_0::CecUICommandCodes::RESTORE_VOLUME_FUNCTION)) {
        os += (first ? "" : " | ");
        os += "RESTORE_VOLUME_FUNCTION";
        first = false;
        flipped |= ::android::hardware::tv::cec::V2_0::CecUICommandCodes::RESTORE_VOLUME_FUNCTION;
    }
    if ((o & ::android::hardware::tv::cec::V2_0::CecUICommandCodes::TUNE_FUNCTION) == static_cast<int32_t>(::android::hardware::tv::cec::V2_0::CecUICommandCodes::TUNE_FUNCTION)) {
        os += (first ? "" : " | ");
        os += "TUNE_FUNCTION";
        first = false;
        flipped |= ::android::hardware::tv::cec::V2_0::CecUICommandCodes::TUNE_FUNCTION;
    }
    if ((o & ::android::hardware::tv::cec::V2_0::CecUICommandCodes::SELECT_MEDIA_FUNCTION) == static_cast<int32_t>(::android::hardware::tv::cec::V2_0::CecUICommandCodes::SELECT_MEDIA_FUNCTION)) {
        os += (first ? "" : " | ");
        os += "SELECT_MEDIA_FUNCTION";
        first = false;
        flipped |= ::android::hardware::tv::cec::V2_0::CecUICommandCodes::SELECT_MEDIA_FUNCTION;
    }
    if ((o & ::android::hardware::tv::cec::V2_0::CecUICommandCodes::SELECT_AV_INPUT_FUNCTION) == static_cast<int32_t>(::android::hardware::tv::cec::V2_0::CecUICommandCodes::SELECT_AV_INPUT_FUNCTION)) {
        os += (first ? "" : " | ");
        os += "SELECT_AV_INPUT_FUNCTION";
        first = false;
        flipped |= ::android::hardware::tv::cec::V2_0::CecUICommandCodes::SELECT_AV_INPUT_FUNCTION;
    }
    if ((o & ::android::hardware::tv::cec::V2_0::CecUICommandCodes::SELECT_AUDIO_INPUT_FUNCTION) == static_cast<int32_t>(::android::hardware::tv::cec::V2_0::CecUICommandCodes::SELECT_AUDIO_INPUT_FUNCTION)) {
        os += (first ? "" : " | ");
        os += "SELECT_AUDIO_INPUT_FUNCTION";
        first = false;
        flipped |= ::android::hardware::tv::cec::V2_0::CecUICommandCodes::SELECT_AUDIO_INPUT_FUNCTION;
    }
    if ((o & ::android::hardware::tv::cec::V2_0::CecUICommandCodes::POWER_TOGGLE_FUNCTION) == static_cast<int32_t>(::android::hardware::tv::cec::V2_0::CecUICommandCodes::POWER_TOGGLE_FUNCTION)) {
        os += (first ? "" : " | ");
        os += "POWER_TOGGLE_FUNCTION";
        first = false;
        flipped |= ::android::hardware::tv::cec::V2_0::CecUICommandCodes::POWER_TOGGLE_FUNCTION;
    }
    if ((o & ::android::hardware::tv::cec::V2_0::CecUICommandCodes::POWER_OFF_FUNCTION) == static_cast<int32_t>(::android::hardware::tv::cec::V2_0::CecUICommandCodes::POWER_OFF_FUNCTION)) {
        os += (first ? "" : " | ");
        os += "POWER_OFF_FUNCTION";
        first = false;
        flipped |= ::android::hardware::tv::cec::V2_0::CecUICommandCodes::POWER_OFF_FUNCTION;
    }
    if ((o & ::android::hardware::tv::cec::V2_0::CecUICommandCodes::POWER_ON_FUNCTION) == static_cast<int32_t>(::android::hardware::tv::cec::V2_0::CecUICommandCodes::POWER_ON_FUNCTION)) {
        os += (first ? "" : " | ");
        os += "POWER_ON_FUNCTION";
        first = false;
        flipped |= ::android::hardware::tv::cec::V2_0::CecUICommandCodes::POWER_ON_FUNCTION;
    }
    if ((o & ::android::hardware::tv::cec::V2_0::CecUICommandCodes::F1) == static_cast<int32_t>(::android::hardware::tv::cec::V2_0::CecUICommandCodes::F1)) {
        os += (first ? "" : " | ");
        os += "F1";
        first = false;
        flipped |= ::android::hardware::tv::cec::V2_0::CecUICommandCodes::F1;
    }
    if ((o & ::android::hardware::tv::cec::V2_0::CecUICommandCodes::F2) == static_cast<int32_t>(::android::hardware::tv::cec::V2_0::CecUICommandCodes::F2)) {
        os += (first ? "" : " | ");
        os += "F2";
        first = false;
        flipped |= ::android::hardware::tv::cec::V2_0::CecUICommandCodes::F2;
    }
    if ((o & ::android::hardware::tv::cec::V2_0::CecUICommandCodes::F3) == static_cast<int32_t>(::android::hardware::tv::cec::V2_0::CecUICommandCodes::F3)) {
        os += (first ? "" : " | ");
        os += "F3";
        first = false;
        flipped |= ::android::hardware::tv::cec::V2_0::CecUICommandCodes::F3;
    }
    if ((o & ::android::hardware::tv::cec::V2_0::CecUICommandCodes::F4) == static_cast<int32_t>(::android::hardware::tv::cec::V2_0::CecUICommandCodes::F4)) {
        os += (first ? "" : " | ");
        os += "F4";
        first = false;
        flipped |= ::android::hardware::tv::cec::V2_0::CecUICommandCodes::F4;
    }
    if ((o & ::android::hardware::tv::cec::V2_0::CecUICommandCodes::F5) == static_cast<int32_t>(::android::hardware::tv::cec::V2_0::CecUICommandCodes::F5)) {
        os += (first ? "" : " | ");
        os += "F5";
        first = false;
        flipped |= ::android::hardware::tv::cec::V2_0::CecUICommandCodes::F5;
    }
    if ((o & ::android::hardware::tv::cec::V2_0::CecUICommandCodes::DATA) == static_cast<int32_t>(::android::hardware::tv::cec::V2_0::CecUICommandCodes::DATA)) {
        os += (first ? "" : " | ");
        os += "DATA";
        first = false;
        flipped |= ::android::hardware::tv::cec::V2_0::CecUICommandCodes::DATA;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::tv::cec::V2_0::CecUICommandCodes o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::tv::cec::V2_0::CecUICommandCodes::SELECT_OK) {
        return "SELECT_OK";
    }
    if (o == ::android::hardware::tv::cec::V2_0::CecUICommandCodes::UP) {
        return "UP";
    }
    if (o == ::android::hardware::tv::cec::V2_0::CecUICommandCodes::DOWN) {
        return "DOWN";
    }
    if (o == ::android::hardware::tv::cec::V2_0::CecUICommandCodes::LEFT) {
        return "LEFT";
    }
    if (o == ::android::hardware::tv::cec::V2_0::CecUICommandCodes::RIGHT) {
        return "RIGHT";
    }
    if (o == ::android::hardware::tv::cec::V2_0::CecUICommandCodes::RIGHT_UP) {
        return "RIGHT_UP";
    }
    if (o == ::android::hardware::tv::cec::V2_0::CecUICommandCodes::RIGHT_DOWN) {
        return "RIGHT_DOWN";
    }
    if (o == ::android::hardware::tv::cec::V2_0::CecUICommandCodes::LEFT_UP) {
        return "LEFT_UP";
    }
    if (o == ::android::hardware::tv::cec::V2_0::CecUICommandCodes::LEFT_DOWN) {
        return "LEFT_DOWN";
    }
    if (o == ::android::hardware::tv::cec::V2_0::CecUICommandCodes::DEVICE_ROOT_MENU) {
        return "DEVICE_ROOT_MENU";
    }
    if (o == ::android::hardware::tv::cec::V2_0::CecUICommandCodes::DEVICE_SETUP_MENU) {
        return "DEVICE_SETUP_MENU";
    }
    if (o == ::android::hardware::tv::cec::V2_0::CecUICommandCodes::CONTENTS_MENU) {
        return "CONTENTS_MENU";
    }
    if (o == ::android::hardware::tv::cec::V2_0::CecUICommandCodes::FAVORITE_MENU) {
        return "FAVORITE_MENU";
    }
    if (o == ::android::hardware::tv::cec::V2_0::CecUICommandCodes::BACK) {
        return "BACK";
    }
    if (o == ::android::hardware::tv::cec::V2_0::CecUICommandCodes::MEDIA_TOP_MENU) {
        return "MEDIA_TOP_MENU";
    }
    if (o == ::android::hardware::tv::cec::V2_0::CecUICommandCodes::MEDIA_CONTEXT_SENSITIVE_MENU) {
        return "MEDIA_CONTEXT_SENSITIVE_MENU";
    }
    if (o == ::android::hardware::tv::cec::V2_0::CecUICommandCodes::NUMBER_ENTRY_MODE) {
        return "NUMBER_ENTRY_MODE";
    }
    if (o == ::android::hardware::tv::cec::V2_0::CecUICommandCodes::NUMBER_11) {
        return "NUMBER_11";
    }
    if (o == ::android::hardware::tv::cec::V2_0::CecUICommandCodes::NUMBER_12) {
        return "NUMBER_12";
    }
    if (o == ::android::hardware::tv::cec::V2_0::CecUICommandCodes::NUMBER_0) {
        return "NUMBER_0";
    }
    if (o == ::android::hardware::tv::cec::V2_0::CecUICommandCodes::NUMBER_1) {
        return "NUMBER_1";
    }
    if (o == ::android::hardware::tv::cec::V2_0::CecUICommandCodes::NUMBER_2) {
        return "NUMBER_2";
    }
    if (o == ::android::hardware::tv::cec::V2_0::CecUICommandCodes::NUMBER_3) {
        return "NUMBER_3";
    }
    if (o == ::android::hardware::tv::cec::V2_0::CecUICommandCodes::NUMBER_4) {
        return "NUMBER_4";
    }
    if (o == ::android::hardware::tv::cec::V2_0::CecUICommandCodes::NUMBER_5) {
        return "NUMBER_5";
    }
    if (o == ::android::hardware::tv::cec::V2_0::CecUICommandCodes::NUMBER_6) {
        return "NUMBER_6";
    }
    if (o == ::android::hardware::tv::cec::V2_0::CecUICommandCodes::NUMBER_7) {
        return "NUMBER_7";
    }
    if (o == ::android::hardware::tv::cec::V2_0::CecUICommandCodes::NUMBER_8) {
        return "NUMBER_8";
    }
    if (o == ::android::hardware::tv::cec::V2_0::CecUICommandCodes::NUMBER_9) {
        return "NUMBER_9";
    }
    if (o == ::android::hardware::tv::cec::V2_0::CecUICommandCodes::DOT) {
        return "DOT";
    }
    if (o == ::android::hardware::tv::cec::V2_0::CecUICommandCodes::ENTER) {
        return "ENTER";
    }
    if (o == ::android::hardware::tv::cec::V2_0::CecUICommandCodes::CLEAR) {
        return "CLEAR";
    }
    if (o == ::android::hardware::tv::cec::V2_0::CecUICommandCodes::NEXT_FAVORITE) {
        return "NEXT_FAVORITE";
    }
    if (o == ::android::hardware::tv::cec::V2_0::CecUICommandCodes::CHANNEL_UP) {
        return "CHANNEL_UP";
    }
    if (o == ::android::hardware::tv::cec::V2_0::CecUICommandCodes::CHANNEL_DOWN) {
        return "CHANNEL_DOWN";
    }
    if (o == ::android::hardware::tv::cec::V2_0::CecUICommandCodes::PREVIOUS_CHANNEL) {
        return "PREVIOUS_CHANNEL";
    }
    if (o == ::android::hardware::tv::cec::V2_0::CecUICommandCodes::SOUND_SELECT) {
        return "SOUND_SELECT";
    }
    if (o == ::android::hardware::tv::cec::V2_0::CecUICommandCodes::INPUT_SELECT) {
        return "INPUT_SELECT";
    }
    if (o == ::android::hardware::tv::cec::V2_0::CecUICommandCodes::DISPLAY_INFORMATION) {
        return "DISPLAY_INFORMATION";
    }
    if (o == ::android::hardware::tv::cec::V2_0::CecUICommandCodes::HELP) {
        return "HELP";
    }
    if (o == ::android::hardware::tv::cec::V2_0::CecUICommandCodes::PAGE_UP) {
        return "PAGE_UP";
    }
    if (o == ::android::hardware::tv::cec::V2_0::CecUICommandCodes::PAGE_DOWN) {
        return "PAGE_DOWN";
    }
    if (o == ::android::hardware::tv::cec::V2_0::CecUICommandCodes::POWER) {
        return "POWER";
    }
    if (o == ::android::hardware::tv::cec::V2_0::CecUICommandCodes::VOLUME_UP) {
        return "VOLUME_UP";
    }
    if (o == ::android::hardware::tv::cec::V2_0::CecUICommandCodes::VOLUME_DOWN) {
        return "VOLUME_DOWN";
    }
    if (o == ::android::hardware::tv::cec::V2_0::CecUICommandCodes::MUTE) {
        return "MUTE";
    }
    if (o == ::android::hardware::tv::cec::V2_0::CecUICommandCodes::PLAY) {
        return "PLAY";
    }
    if (o == ::android::hardware::tv::cec::V2_0::CecUICommandCodes::STOP) {
        return "STOP";
    }
    if (o == ::android::hardware::tv::cec::V2_0::CecUICommandCodes::PAUSE) {
        return "PAUSE";
    }
    if (o == ::android::hardware::tv::cec::V2_0::CecUICommandCodes::RECORD) {
        return "RECORD";
    }
    if (o == ::android::hardware::tv::cec::V2_0::CecUICommandCodes::REWIND) {
        return "REWIND";
    }
    if (o == ::android::hardware::tv::cec::V2_0::CecUICommandCodes::FAST_FORWARD) {
        return "FAST_FORWARD";
    }
    if (o == ::android::hardware::tv::cec::V2_0::CecUICommandCodes::EJECT) {
        return "EJECT";
    }
    if (o == ::android::hardware::tv::cec::V2_0::CecUICommandCodes::SKIP_FORWARD) {
        return "SKIP_FORWARD";
    }
    if (o == ::android::hardware::tv::cec::V2_0::CecUICommandCodes::SKIP_BACKWARD) {
        return "SKIP_BACKWARD";
    }
    if (o == ::android::hardware::tv::cec::V2_0::CecUICommandCodes::STOP_RECORD) {
        return "STOP_RECORD";
    }
    if (o == ::android::hardware::tv::cec::V2_0::CecUICommandCodes::PAUSE_RECORD) {
        return "PAUSE_RECORD";
    }
    if (o == ::android::hardware::tv::cec::V2_0::CecUICommandCodes::ANGLE) {
        return "ANGLE";
    }
    if (o == ::android::hardware::tv::cec::V2_0::CecUICommandCodes::SUB_PICTURE) {
        return "SUB_PICTURE";
    }
    if (o == ::android::hardware::tv::cec::V2_0::CecUICommandCodes::VIDEO_ON_DEMAND) {
        return "VIDEO_ON_DEMAND";
    }
    if (o == ::android::hardware::tv::cec::V2_0::CecUICommandCodes::ELECTRONIC_PROGRAM_GUIDE) {
        return "ELECTRONIC_PROGRAM_GUIDE";
    }
    if (o == ::android::hardware::tv::cec::V2_0::CecUICommandCodes::TIMER_PROGRAMMING) {
        return "TIMER_PROGRAMMING";
    }
    if (o == ::android::hardware::tv::cec::V2_0::CecUICommandCodes::INITIAL_CONFIGURATION) {
        return "INITIAL_CONFIGURATION";
    }
    if (o == ::android::hardware::tv::cec::V2_0::CecUICommandCodes::SELECT_BROADCAST_TYPE) {
        return "SELECT_BROADCAST_TYPE";
    }
    if (o == ::android::hardware::tv::cec::V2_0::CecUICommandCodes::SELECT_SOUND_PRESENTATION) {
        return "SELECT_SOUND_PRESENTATION";
    }
    if (o == ::android::hardware::tv::cec::V2_0::CecUICommandCodes::AUDIO_DESCRIPTION) {
        return "AUDIO_DESCRIPTION";
    }
    if (o == ::android::hardware::tv::cec::V2_0::CecUICommandCodes::INTERNET) {
        return "INTERNET";
    }
    if (o == ::android::hardware::tv::cec::V2_0::CecUICommandCodes::THREE_DIMENSIONAL_MODE) {
        return "THREE_DIMENSIONAL_MODE";
    }
    if (o == ::android::hardware::tv::cec::V2_0::CecUICommandCodes::PLAY_FUNCTION) {
        return "PLAY_FUNCTION";
    }
    if (o == ::android::hardware::tv::cec::V2_0::CecUICommandCodes::PAUSE_PLAY_FUNCTION) {
        return "PAUSE_PLAY_FUNCTION";
    }
    if (o == ::android::hardware::tv::cec::V2_0::CecUICommandCodes::RECORD_FUNCTION) {
        return "RECORD_FUNCTION";
    }
    if (o == ::android::hardware::tv::cec::V2_0::CecUICommandCodes::PAUSE_RECORD_FUNCTION) {
        return "PAUSE_RECORD_FUNCTION";
    }
    if (o == ::android::hardware::tv::cec::V2_0::CecUICommandCodes::STOP_FUNCTION) {
        return "STOP_FUNCTION";
    }
    if (o == ::android::hardware::tv::cec::V2_0::CecUICommandCodes::MUTE_FUNCTION) {
        return "MUTE_FUNCTION";
    }
    if (o == ::android::hardware::tv::cec::V2_0::CecUICommandCodes::RESTORE_VOLUME_FUNCTION) {
        return "RESTORE_VOLUME_FUNCTION";
    }
    if (o == ::android::hardware::tv::cec::V2_0::CecUICommandCodes::TUNE_FUNCTION) {
        return "TUNE_FUNCTION";
    }
    if (o == ::android::hardware::tv::cec::V2_0::CecUICommandCodes::SELECT_MEDIA_FUNCTION) {
        return "SELECT_MEDIA_FUNCTION";
    }
    if (o == ::android::hardware::tv::cec::V2_0::CecUICommandCodes::SELECT_AV_INPUT_FUNCTION) {
        return "SELECT_AV_INPUT_FUNCTION";
    }
    if (o == ::android::hardware::tv::cec::V2_0::CecUICommandCodes::SELECT_AUDIO_INPUT_FUNCTION) {
        return "SELECT_AUDIO_INPUT_FUNCTION";
    }
    if (o == ::android::hardware::tv::cec::V2_0::CecUICommandCodes::POWER_TOGGLE_FUNCTION) {
        return "POWER_TOGGLE_FUNCTION";
    }
    if (o == ::android::hardware::tv::cec::V2_0::CecUICommandCodes::POWER_OFF_FUNCTION) {
        return "POWER_OFF_FUNCTION";
    }
    if (o == ::android::hardware::tv::cec::V2_0::CecUICommandCodes::POWER_ON_FUNCTION) {
        return "POWER_ON_FUNCTION";
    }
    if (o == ::android::hardware::tv::cec::V2_0::CecUICommandCodes::F1) {
        return "F1";
    }
    if (o == ::android::hardware::tv::cec::V2_0::CecUICommandCodes::F2) {
        return "F2";
    }
    if (o == ::android::hardware::tv::cec::V2_0::CecUICommandCodes::F3) {
        return "F3";
    }
    if (o == ::android::hardware::tv::cec::V2_0::CecUICommandCodes::F4) {
        return "F4";
    }
    if (o == ::android::hardware::tv::cec::V2_0::CecUICommandCodes::F5) {
        return "F5";
    }
    if (o == ::android::hardware::tv::cec::V2_0::CecUICommandCodes::DATA) {
        return "DATA";
    }
    std::string os;
    os += toHexString(static_cast<int32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::tv::cec::V2_0::CecUICommandCodes o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::tv::cec::V2_0::HdmiPortType>(int32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::tv::cec::V2_0::HdmiPortType> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::tv::cec::V2_0::HdmiPortType::INPUT) == static_cast<int32_t>(::android::hardware::tv::cec::V2_0::HdmiPortType::INPUT)) {
        os += (first ? "" : " | ");
        os += "INPUT";
        first = false;
        flipped |= ::android::hardware::tv::cec::V2_0::HdmiPortType::INPUT;
    }
    if ((o & ::android::hardware::tv::cec::V2_0::HdmiPortType::OUTPUT) == static_cast<int32_t>(::android::hardware::tv::cec::V2_0::HdmiPortType::OUTPUT)) {
        os += (first ? "" : " | ");
        os += "OUTPUT";
        first = false;
        flipped |= ::android::hardware::tv::cec::V2_0::HdmiPortType::OUTPUT;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::tv::cec::V2_0::HdmiPortType o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::tv::cec::V2_0::HdmiPortType::INPUT) {
        return "INPUT";
    }
    if (o == ::android::hardware::tv::cec::V2_0::HdmiPortType::OUTPUT) {
        return "OUTPUT";
    }
    std::string os;
    os += toHexString(static_cast<int32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::tv::cec::V2_0::HdmiPortType o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::tv::cec::V2_0::OptionKey>(int32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::tv::cec::V2_0::OptionKey> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::tv::cec::V2_0::OptionKey::WAKEUP) == static_cast<int32_t>(::android::hardware::tv::cec::V2_0::OptionKey::WAKEUP)) {
        os += (first ? "" : " | ");
        os += "WAKEUP";
        first = false;
        flipped |= ::android::hardware::tv::cec::V2_0::OptionKey::WAKEUP;
    }
    if ((o & ::android::hardware::tv::cec::V2_0::OptionKey::ENABLE_CEC) == static_cast<int32_t>(::android::hardware::tv::cec::V2_0::OptionKey::ENABLE_CEC)) {
        os += (first ? "" : " | ");
        os += "ENABLE_CEC";
        first = false;
        flipped |= ::android::hardware::tv::cec::V2_0::OptionKey::ENABLE_CEC;
    }
    if ((o & ::android::hardware::tv::cec::V2_0::OptionKey::SYSTEM_CEC_CONTROL) == static_cast<int32_t>(::android::hardware::tv::cec::V2_0::OptionKey::SYSTEM_CEC_CONTROL)) {
        os += (first ? "" : " | ");
        os += "SYSTEM_CEC_CONTROL";
        first = false;
        flipped |= ::android::hardware::tv::cec::V2_0::OptionKey::SYSTEM_CEC_CONTROL;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::tv::cec::V2_0::OptionKey o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::tv::cec::V2_0::OptionKey::WAKEUP) {
        return "WAKEUP";
    }
    if (o == ::android::hardware::tv::cec::V2_0::OptionKey::ENABLE_CEC) {
        return "ENABLE_CEC";
    }
    if (o == ::android::hardware::tv::cec::V2_0::OptionKey::SYSTEM_CEC_CONTROL) {
        return "SYSTEM_CEC_CONTROL";
    }
    std::string os;
    os += toHexString(static_cast<int32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::tv::cec::V2_0::OptionKey o, ::std::ostream* os) {
    *os << toString(o);
}

static inline std::string toString(const ::android::hardware::tv::cec::V2_0::HotplugEvent& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".connected = ";
    os += ::android::hardware::toString(o.connected);
    os += ", .portId = ";
    os += ::android::hardware::toString(o.portId);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::tv::cec::V2_0::HotplugEvent& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::tv::cec::V2_0::HotplugEvent& lhs, const ::android::hardware::tv::cec::V2_0::HotplugEvent& rhs) {
    if (lhs.connected != rhs.connected) {
        return false;
    }
    if (lhs.portId != rhs.portId) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::tv::cec::V2_0::HotplugEvent& lhs, const ::android::hardware::tv::cec::V2_0::HotplugEvent& rhs){
    return !(lhs == rhs);
}

static inline std::string toString(const ::android::hardware::tv::cec::V2_0::HdmiPortInfo& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".type = ";
    os += ::android::hardware::tv::cec::V2_0::toString(o.type);
    os += ", .portId = ";
    os += ::android::hardware::toString(o.portId);
    os += ", .cecSupported = ";
    os += ::android::hardware::toString(o.cecSupported);
    os += ", .arcSupported = ";
    os += ::android::hardware::toString(o.arcSupported);
    os += ", .physicalAddress = ";
    os += ::android::hardware::toString(o.physicalAddress);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::tv::cec::V2_0::HdmiPortInfo& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::tv::cec::V2_0::HdmiPortInfo& lhs, const ::android::hardware::tv::cec::V2_0::HdmiPortInfo& rhs) {
    if (lhs.type != rhs.type) {
        return false;
    }
    if (lhs.portId != rhs.portId) {
        return false;
    }
    if (lhs.cecSupported != rhs.cecSupported) {
        return false;
    }
    if (lhs.arcSupported != rhs.arcSupported) {
        return false;
    }
    if (lhs.physicalAddress != rhs.physicalAddress) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::tv::cec::V2_0::HdmiPortInfo& lhs, const ::android::hardware::tv::cec::V2_0::HdmiPortInfo& rhs){
    return !(lhs == rhs);
}

template<>
inline std::string toString<::android::hardware::tv::cec::V2_0::Result>(int32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::tv::cec::V2_0::Result> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::tv::cec::V2_0::Result::SUCCESS) == static_cast<int32_t>(::android::hardware::tv::cec::V2_0::Result::SUCCESS)) {
        os += (first ? "" : " | ");
        os += "SUCCESS";
        first = false;
        flipped |= ::android::hardware::tv::cec::V2_0::Result::SUCCESS;
    }
    if ((o & ::android::hardware::tv::cec::V2_0::Result::FAILURE_UNKNOWN) == static_cast<int32_t>(::android::hardware::tv::cec::V2_0::Result::FAILURE_UNKNOWN)) {
        os += (first ? "" : " | ");
        os += "FAILURE_UNKNOWN";
        first = false;
        flipped |= ::android::hardware::tv::cec::V2_0::Result::FAILURE_UNKNOWN;
    }
    if ((o & ::android::hardware::tv::cec::V2_0::Result::FAILURE_INVALID_ARGS) == static_cast<int32_t>(::android::hardware::tv::cec::V2_0::Result::FAILURE_INVALID_ARGS)) {
        os += (first ? "" : " | ");
        os += "FAILURE_INVALID_ARGS";
        first = false;
        flipped |= ::android::hardware::tv::cec::V2_0::Result::FAILURE_INVALID_ARGS;
    }
    if ((o & ::android::hardware::tv::cec::V2_0::Result::FAILURE_INVALID_STATE) == static_cast<int32_t>(::android::hardware::tv::cec::V2_0::Result::FAILURE_INVALID_STATE)) {
        os += (first ? "" : " | ");
        os += "FAILURE_INVALID_STATE";
        first = false;
        flipped |= ::android::hardware::tv::cec::V2_0::Result::FAILURE_INVALID_STATE;
    }
    if ((o & ::android::hardware::tv::cec::V2_0::Result::FAILURE_NOT_SUPPORTED) == static_cast<int32_t>(::android::hardware::tv::cec::V2_0::Result::FAILURE_NOT_SUPPORTED)) {
        os += (first ? "" : " | ");
        os += "FAILURE_NOT_SUPPORTED";
        first = false;
        flipped |= ::android::hardware::tv::cec::V2_0::Result::FAILURE_NOT_SUPPORTED;
    }
    if ((o & ::android::hardware::tv::cec::V2_0::Result::FAILURE_BUSY) == static_cast<int32_t>(::android::hardware::tv::cec::V2_0::Result::FAILURE_BUSY)) {
        os += (first ? "" : " | ");
        os += "FAILURE_BUSY";
        first = false;
        flipped |= ::android::hardware::tv::cec::V2_0::Result::FAILURE_BUSY;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::tv::cec::V2_0::Result o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::tv::cec::V2_0::Result::SUCCESS) {
        return "SUCCESS";
    }
    if (o == ::android::hardware::tv::cec::V2_0::Result::FAILURE_UNKNOWN) {
        return "FAILURE_UNKNOWN";
    }
    if (o == ::android::hardware::tv::cec::V2_0::Result::FAILURE_INVALID_ARGS) {
        return "FAILURE_INVALID_ARGS";
    }
    if (o == ::android::hardware::tv::cec::V2_0::Result::FAILURE_INVALID_STATE) {
        return "FAILURE_INVALID_STATE";
    }
    if (o == ::android::hardware::tv::cec::V2_0::Result::FAILURE_NOT_SUPPORTED) {
        return "FAILURE_NOT_SUPPORTED";
    }
    if (o == ::android::hardware::tv::cec::V2_0::Result::FAILURE_BUSY) {
        return "FAILURE_BUSY";
    }
    std::string os;
    os += toHexString(static_cast<int32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::tv::cec::V2_0::Result o, ::std::ostream* os) {
    *os << toString(o);
}


}  // namespace V2_0
}  // namespace cec
}  // namespace tv
}  // namespace hardware
}  // namespace android

//
// global type declarations for package
//

namespace android {
namespace hardware {
namespace details {
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wc++17-extensions"
template<> inline constexpr std::array<::android::hardware::tv::cec::V2_0::CecDeviceType, 9> hidl_enum_values<::android::hardware::tv::cec::V2_0::CecDeviceType> = {
    ::android::hardware::tv::cec::V2_0::CecDeviceType::INACTIVE,
    ::android::hardware::tv::cec::V2_0::CecDeviceType::TV,
    ::android::hardware::tv::cec::V2_0::CecDeviceType::RECORDER,
    ::android::hardware::tv::cec::V2_0::CecDeviceType::TUNER,
    ::android::hardware::tv::cec::V2_0::CecDeviceType::PLAYBACK,
    ::android::hardware::tv::cec::V2_0::CecDeviceType::AUDIO_SYSTEM,
    ::android::hardware::tv::cec::V2_0::CecDeviceType::PURE_CEC_SWITCH,
    ::android::hardware::tv::cec::V2_0::CecDeviceType::PROCESSOR,
    ::android::hardware::tv::cec::V2_0::CecDeviceType::MAX,
};
#pragma clang diagnostic pop
}  // namespace details
}  // namespace hardware
}  // namespace android

namespace android {
namespace hardware {
namespace details {
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wc++17-extensions"
template<> inline constexpr std::array<::android::hardware::tv::cec::V2_0::CecLogicalAddress, 17> hidl_enum_values<::android::hardware::tv::cec::V2_0::CecLogicalAddress> = {
    ::android::hardware::tv::cec::V2_0::CecLogicalAddress::TV,
    ::android::hardware::tv::cec::V2_0::CecLogicalAddress::RECORDER_1,
    ::android::hardware::tv::cec::V2_0::CecLogicalAddress::RECORDER_2,
    ::android::hardware::tv::cec::V2_0::CecLogicalAddress::TUNER_1,
    ::android::hardware::tv::cec::V2_0::CecLogicalAddress::PLAYBACK_1,
    ::android::hardware::tv::cec::V2_0::CecLogicalAddress::AUDIO_SYSTEM,
    ::android::hardware::tv::cec::V2_0::CecLogicalAddress::TUNER_2,
    ::android::hardware::tv::cec::V2_0::CecLogicalAddress::TUNER_3,
    ::android::hardware::tv::cec::V2_0::CecLogicalAddress::PLAYBACK_2,
    ::android::hardware::tv::cec::V2_0::CecLogicalAddress::RECORDER_3,
    ::android::hardware::tv::cec::V2_0::CecLogicalAddress::TUNER_4,
    ::android::hardware::tv::cec::V2_0::CecLogicalAddress::PLAYBACK_3,
    ::android::hardware::tv::cec::V2_0::CecLogicalAddress::BACKUP_1,
    ::android::hardware::tv::cec::V2_0::CecLogicalAddress::BACKUP_2,
    ::android::hardware::tv::cec::V2_0::CecLogicalAddress::SPECIFIC_USE,
    ::android::hardware::tv::cec::V2_0::CecLogicalAddress::UNREGISTERED,
    ::android::hardware::tv::cec::V2_0::CecLogicalAddress::BROADCAST,
};
#pragma clang diagnostic pop
}  // namespace details
}  // namespace hardware
}  // namespace android

namespace android {
namespace hardware {
namespace details {
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wc++17-extensions"
template<> inline constexpr std::array<::android::hardware::tv::cec::V2_0::CecMessageType, 75> hidl_enum_values<::android::hardware::tv::cec::V2_0::CecMessageType> = {
    ::android::hardware::tv::cec::V2_0::CecMessageType::FEATURE_ABORT,
    ::android::hardware::tv::cec::V2_0::CecMessageType::IMAGE_VIEW_ON,
    ::android::hardware::tv::cec::V2_0::CecMessageType::TUNER_STEP_INCREMENT,
    ::android::hardware::tv::cec::V2_0::CecMessageType::TUNER_STEP_DECREMENT,
    ::android::hardware::tv::cec::V2_0::CecMessageType::TUNER_DEVICE_STATUS,
    ::android::hardware::tv::cec::V2_0::CecMessageType::GIVE_TUNER_DEVICE_STATUS,
    ::android::hardware::tv::cec::V2_0::CecMessageType::RECORD_ON,
    ::android::hardware::tv::cec::V2_0::CecMessageType::RECORD_STATUS,
    ::android::hardware::tv::cec::V2_0::CecMessageType::RECORD_OFF,
    ::android::hardware::tv::cec::V2_0::CecMessageType::TEXT_VIEW_ON,
    ::android::hardware::tv::cec::V2_0::CecMessageType::RECORD_TV_SCREEN,
    ::android::hardware::tv::cec::V2_0::CecMessageType::GIVE_DECK_STATUS,
    ::android::hardware::tv::cec::V2_0::CecMessageType::DECK_STATUS,
    ::android::hardware::tv::cec::V2_0::CecMessageType::SET_MENU_LANGUAGE,
    ::android::hardware::tv::cec::V2_0::CecMessageType::CLEAR_ANALOG_TIMER,
    ::android::hardware::tv::cec::V2_0::CecMessageType::SET_ANALOG_TIMER,
    ::android::hardware::tv::cec::V2_0::CecMessageType::TIMER_STATUS,
    ::android::hardware::tv::cec::V2_0::CecMessageType::STANDBY,
    ::android::hardware::tv::cec::V2_0::CecMessageType::PLAY,
    ::android::hardware::tv::cec::V2_0::CecMessageType::DECK_CONTROL,
    ::android::hardware::tv::cec::V2_0::CecMessageType::TIMER_CLEARED_STATUS,
    ::android::hardware::tv::cec::V2_0::CecMessageType::USER_CONTROL_PRESSED,
    ::android::hardware::tv::cec::V2_0::CecMessageType::USER_CONTROL_RELEASED,
    ::android::hardware::tv::cec::V2_0::CecMessageType::GIVE_OSD_NAME,
    ::android::hardware::tv::cec::V2_0::CecMessageType::SET_OSD_NAME,
    ::android::hardware::tv::cec::V2_0::CecMessageType::SET_OSD_STRING,
    ::android::hardware::tv::cec::V2_0::CecMessageType::SET_TIMER_PROGRAM_TITLE,
    ::android::hardware::tv::cec::V2_0::CecMessageType::SYSTEM_AUDIO_MODE_REQUEST,
    ::android::hardware::tv::cec::V2_0::CecMessageType::GIVE_AUDIO_STATUS,
    ::android::hardware::tv::cec::V2_0::CecMessageType::SET_SYSTEM_AUDIO_MODE,
    ::android::hardware::tv::cec::V2_0::CecMessageType::REPORT_AUDIO_STATUS,
    ::android::hardware::tv::cec::V2_0::CecMessageType::GIVE_SYSTEM_AUDIO_MODE_STATUS,
    ::android::hardware::tv::cec::V2_0::CecMessageType::SYSTEM_AUDIO_MODE_STATUS,
    ::android::hardware::tv::cec::V2_0::CecMessageType::ROUTING_CHANGE,
    ::android::hardware::tv::cec::V2_0::CecMessageType::ROUTING_INFORMATION,
    ::android::hardware::tv::cec::V2_0::CecMessageType::ACTIVE_SOURCE,
    ::android::hardware::tv::cec::V2_0::CecMessageType::GIVE_PHYSICAL_ADDRESS,
    ::android::hardware::tv::cec::V2_0::CecMessageType::REPORT_PHYSICAL_ADDRESS,
    ::android::hardware::tv::cec::V2_0::CecMessageType::REQUEST_ACTIVE_SOURCE,
    ::android::hardware::tv::cec::V2_0::CecMessageType::SET_STREAM_PATH,
    ::android::hardware::tv::cec::V2_0::CecMessageType::DEVICE_VENDOR_ID,
    ::android::hardware::tv::cec::V2_0::CecMessageType::VENDOR_COMMAND,
    ::android::hardware::tv::cec::V2_0::CecMessageType::VENDOR_REMOTE_BUTTON_DOWN,
    ::android::hardware::tv::cec::V2_0::CecMessageType::VENDOR_REMOTE_BUTTON_UP,
    ::android::hardware::tv::cec::V2_0::CecMessageType::GIVE_DEVICE_VENDOR_ID,
    ::android::hardware::tv::cec::V2_0::CecMessageType::MENU_REQUEST,
    ::android::hardware::tv::cec::V2_0::CecMessageType::MENU_STATUS,
    ::android::hardware::tv::cec::V2_0::CecMessageType::GIVE_DEVICE_POWER_STATUS,
    ::android::hardware::tv::cec::V2_0::CecMessageType::REPORT_POWER_STATUS,
    ::android::hardware::tv::cec::V2_0::CecMessageType::GET_MENU_LANGUAGE,
    ::android::hardware::tv::cec::V2_0::CecMessageType::SELECT_ANALOG_SERVICE,
    ::android::hardware::tv::cec::V2_0::CecMessageType::SELECT_DIGITAL_SERVICE,
    ::android::hardware::tv::cec::V2_0::CecMessageType::SET_DIGITAL_TIMER,
    ::android::hardware::tv::cec::V2_0::CecMessageType::CLEAR_DIGITAL_TIMER,
    ::android::hardware::tv::cec::V2_0::CecMessageType::SET_AUDIO_RATE,
    ::android::hardware::tv::cec::V2_0::CecMessageType::INACTIVE_SOURCE,
    ::android::hardware::tv::cec::V2_0::CecMessageType::CEC_VERSION,
    ::android::hardware::tv::cec::V2_0::CecMessageType::GET_CEC_VERSION,
    ::android::hardware::tv::cec::V2_0::CecMessageType::VENDOR_COMMAND_WITH_ID,
    ::android::hardware::tv::cec::V2_0::CecMessageType::CLEAR_EXTERNAL_TIMER,
    ::android::hardware::tv::cec::V2_0::CecMessageType::SET_EXTERNAL_TIMER,
    ::android::hardware::tv::cec::V2_0::CecMessageType::REPORT_SHORT_AUDIO_DESCRIPTOR,
    ::android::hardware::tv::cec::V2_0::CecMessageType::REQUEST_SHORT_AUDIO_DESCRIPTOR,
    ::android::hardware::tv::cec::V2_0::CecMessageType::GIVE_FEATURES,
    ::android::hardware::tv::cec::V2_0::CecMessageType::REPORT_FEATURES,
    ::android::hardware::tv::cec::V2_0::CecMessageType::REQUEST_CURRENT_LATENCY,
    ::android::hardware::tv::cec::V2_0::CecMessageType::REPORT_CURRENT_LATENCY,
    ::android::hardware::tv::cec::V2_0::CecMessageType::INITIATE_ARC,
    ::android::hardware::tv::cec::V2_0::CecMessageType::REPORT_ARC_INITIATED,
    ::android::hardware::tv::cec::V2_0::CecMessageType::REPORT_ARC_TERMINATED,
    ::android::hardware::tv::cec::V2_0::CecMessageType::REQUEST_ARC_INITIATION,
    ::android::hardware::tv::cec::V2_0::CecMessageType::REQUEST_ARC_TERMINATION,
    ::android::hardware::tv::cec::V2_0::CecMessageType::TERMINATE_ARC,
    ::android::hardware::tv::cec::V2_0::CecMessageType::ABORT,
    ::android::hardware::tv::cec::V2_0::CecMessageType::POLLING_MESSAGE,
};
#pragma clang diagnostic pop
}  // namespace details
}  // namespace hardware
}  // namespace android

namespace android {
namespace hardware {
namespace details {
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wc++17-extensions"
template<> inline constexpr std::array<::android::hardware::tv::cec::V2_0::AbortReason, 6> hidl_enum_values<::android::hardware::tv::cec::V2_0::AbortReason> = {
    ::android::hardware::tv::cec::V2_0::AbortReason::UNRECOGNIZED_MODE,
    ::android::hardware::tv::cec::V2_0::AbortReason::NOT_IN_CORRECT_MODE,
    ::android::hardware::tv::cec::V2_0::AbortReason::CANNOT_PROVIDE_SOURCE,
    ::android::hardware::tv::cec::V2_0::AbortReason::INVALID_OPERAND,
    ::android::hardware::tv::cec::V2_0::AbortReason::REFUSED,
    ::android::hardware::tv::cec::V2_0::AbortReason::UNABLE_TO_DETERMINE,
};
#pragma clang diagnostic pop
}  // namespace details
}  // namespace hardware
}  // namespace android

namespace android {
namespace hardware {
namespace details {
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wc++17-extensions"
template<> inline constexpr std::array<::android::hardware::tv::cec::V2_0::MaxLength, 1> hidl_enum_values<::android::hardware::tv::cec::V2_0::MaxLength> = {
    ::android::hardware::tv::cec::V2_0::MaxLength::MESSAGE_BODY,
};
#pragma clang diagnostic pop
}  // namespace details
}  // namespace hardware
}  // namespace android

namespace android {
namespace hardware {
namespace details {
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wc++17-extensions"
template<> inline constexpr std::array<::android::hardware::tv::cec::V2_0::SendMessageResult, 4> hidl_enum_values<::android::hardware::tv::cec::V2_0::SendMessageResult> = {
    ::android::hardware::tv::cec::V2_0::SendMessageResult::SUCCESS,
    ::android::hardware::tv::cec::V2_0::SendMessageResult::NACK,
    ::android::hardware::tv::cec::V2_0::SendMessageResult::BUSY,
    ::android::hardware::tv::cec::V2_0::SendMessageResult::FAIL,
};
#pragma clang diagnostic pop
}  // namespace details
}  // namespace hardware
}  // namespace android

namespace android {
namespace hardware {
namespace details {
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wc++17-extensions"
template<> inline constexpr std::array<::android::hardware::tv::cec::V2_0::CecAllDeviceTypeValue, 8> hidl_enum_values<::android::hardware::tv::cec::V2_0::CecAllDeviceTypeValue> = {
    ::android::hardware::tv::cec::V2_0::CecAllDeviceTypeValue::RESERVED_DEVICE_2,
    ::android::hardware::tv::cec::V2_0::CecAllDeviceTypeValue::RESERVED_DEVICE_1,
    ::android::hardware::tv::cec::V2_0::CecAllDeviceTypeValue::CEC_SWITCH_DEVICE,
    ::android::hardware::tv::cec::V2_0::CecAllDeviceTypeValue::AUDIO_DEVICE,
    ::android::hardware::tv::cec::V2_0::CecAllDeviceTypeValue::PLAYBACK_DEVICE,
    ::android::hardware::tv::cec::V2_0::CecAllDeviceTypeValue::TUNER_DEVICE,
    ::android::hardware::tv::cec::V2_0::CecAllDeviceTypeValue::RECORDING_DEVICE,
    ::android::hardware::tv::cec::V2_0::CecAllDeviceTypeValue::TV_DEVICE,
};
#pragma clang diagnostic pop
}  // namespace details
}  // namespace hardware
}  // namespace android

namespace android {
namespace hardware {
namespace details {
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wc++17-extensions"
template<> inline constexpr std::array<::android::hardware::tv::cec::V2_0::CecVersion, 3> hidl_enum_values<::android::hardware::tv::cec::V2_0::CecVersion> = {
    ::android::hardware::tv::cec::V2_0::CecVersion::V_1_3_A,
    ::android::hardware::tv::cec::V2_0::CecVersion::V_1_4,
    ::android::hardware::tv::cec::V2_0::CecVersion::V_2_0,
};
#pragma clang diagnostic pop
}  // namespace details
}  // namespace hardware
}  // namespace android

namespace android {
namespace hardware {
namespace details {
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wc++17-extensions"
template<> inline constexpr std::array<::android::hardware::tv::cec::V2_0::CecDeviceFeature, 7> hidl_enum_values<::android::hardware::tv::cec::V2_0::CecDeviceFeature> = {
    ::android::hardware::tv::cec::V2_0::CecDeviceFeature::RESERVED,
    ::android::hardware::tv::cec::V2_0::CecDeviceFeature::SOURCE_SUPPORT_ARC_RX,
    ::android::hardware::tv::cec::V2_0::CecDeviceFeature::SINK_SUPPORT_ARC_TX,
    ::android::hardware::tv::cec::V2_0::CecDeviceFeature::SOURCE_SUPPORT_SET_AUDIO_RATE,
    ::android::hardware::tv::cec::V2_0::CecDeviceFeature::SUPPORT_CONTROLLED_BY_DECK,
    ::android::hardware::tv::cec::V2_0::CecDeviceFeature::TV_SUPPORT_SET_OSD_STRINGS,
    ::android::hardware::tv::cec::V2_0::CecDeviceFeature::TV_SUPPORT_RECORD_TV_SCREEN,
};
#pragma clang diagnostic pop
}  // namespace details
}  // namespace hardware
}  // namespace android

namespace android {
namespace hardware {
namespace details {
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wc++17-extensions"
template<> inline constexpr std::array<::android::hardware::tv::cec::V2_0::CecRcProfileId, 5> hidl_enum_values<::android::hardware::tv::cec::V2_0::CecRcProfileId> = {
    ::android::hardware::tv::cec::V2_0::CecRcProfileId::NONE,
    ::android::hardware::tv::cec::V2_0::CecRcProfileId::RC_PROFILE_1,
    ::android::hardware::tv::cec::V2_0::CecRcProfileId::RC_PROFILE_2,
    ::android::hardware::tv::cec::V2_0::CecRcProfileId::RC_PROFILE_3,
    ::android::hardware::tv::cec::V2_0::CecRcProfileId::RC_PROFILE_4,
};
#pragma clang diagnostic pop
}  // namespace details
}  // namespace hardware
}  // namespace android

namespace android {
namespace hardware {
namespace details {
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wc++17-extensions"
template<> inline constexpr std::array<::android::hardware::tv::cec::V2_0::CecRcProfileSource, 6> hidl_enum_values<::android::hardware::tv::cec::V2_0::CecRcProfileSource> = {
    ::android::hardware::tv::cec::V2_0::CecRcProfileSource::MEDIA_CONTEXT_SENSITIVE,
    ::android::hardware::tv::cec::V2_0::CecRcProfileSource::MEDIA_TO,
    ::android::hardware::tv::cec::V2_0::CecRcProfileSource::CONTENTS,
    ::android::hardware::tv::cec::V2_0::CecRcProfileSource::DEVICE_SETUP,
    ::android::hardware::tv::cec::V2_0::CecRcProfileSource::DEVICE_ROOT,
    ::android::hardware::tv::cec::V2_0::CecRcProfileSource::SOURCE_FLAG,
};
#pragma clang diagnostic pop
}  // namespace details
}  // namespace hardware
}  // namespace android

namespace android {
namespace hardware {
namespace details {
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wc++17-extensions"
template<> inline constexpr std::array<::android::hardware::tv::cec::V2_0::CecPowerState, 5> hidl_enum_values<::android::hardware::tv::cec::V2_0::CecPowerState> = {
    ::android::hardware::tv::cec::V2_0::CecPowerState::ON,
    ::android::hardware::tv::cec::V2_0::CecPowerState::STANDBY,
    ::android::hardware::tv::cec::V2_0::CecPowerState::ON_TO_STANDBY,
    ::android::hardware::tv::cec::V2_0::CecPowerState::STANDBY_TO_ON,
    ::android::hardware::tv::cec::V2_0::CecPowerState::UNKNOWN,
};
#pragma clang diagnostic pop
}  // namespace details
}  // namespace hardware
}  // namespace android

namespace android {
namespace hardware {
namespace details {
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wc++17-extensions"
template<> inline constexpr std::array<::android::hardware::tv::cec::V2_0::CecTopologyEventType, 3> hidl_enum_values<::android::hardware::tv::cec::V2_0::CecTopologyEventType> = {
    ::android::hardware::tv::cec::V2_0::CecTopologyEventType::DEVICE_ADDED,
    ::android::hardware::tv::cec::V2_0::CecTopologyEventType::DEVICE_REMOVED,
    ::android::hardware::tv::cec::V2_0::CecTopologyEventType::DEVICE_UPDATED,
};
#pragma clang diagnostic pop
}  // namespace details
}  // namespace hardware
}  // namespace android

namespace android {
namespace hardware {
namespace details {
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wc++17-extensions"
template<> inline constexpr std::array<::android::hardware::tv::cec::V2_0::CecUICommandCodes, 88> hidl_enum_values<::android::hardware::tv::cec::V2_0::CecUICommandCodes> = {
    ::android::hardware::tv::cec::V2_0::CecUICommandCodes::SELECT_OK,
    ::android::hardware::tv::cec::V2_0::CecUICommandCodes::UP,
    ::android::hardware::tv::cec::V2_0::CecUICommandCodes::DOWN,
    ::android::hardware::tv::cec::V2_0::CecUICommandCodes::LEFT,
    ::android::hardware::tv::cec::V2_0::CecUICommandCodes::RIGHT,
    ::android::hardware::tv::cec::V2_0::CecUICommandCodes::RIGHT_UP,
    ::android::hardware::tv::cec::V2_0::CecUICommandCodes::RIGHT_DOWN,
    ::android::hardware::tv::cec::V2_0::CecUICommandCodes::LEFT_UP,
    ::android::hardware::tv::cec::V2_0::CecUICommandCodes::LEFT_DOWN,
    ::android::hardware::tv::cec::V2_0::CecUICommandCodes::DEVICE_ROOT_MENU,
    ::android::hardware::tv::cec::V2_0::CecUICommandCodes::DEVICE_SETUP_MENU,
    ::android::hardware::tv::cec::V2_0::CecUICommandCodes::CONTENTS_MENU,
    ::android::hardware::tv::cec::V2_0::CecUICommandCodes::FAVORITE_MENU,
    ::android::hardware::tv::cec::V2_0::CecUICommandCodes::BACK,
    ::android::hardware::tv::cec::V2_0::CecUICommandCodes::MEDIA_TOP_MENU,
    ::android::hardware::tv::cec::V2_0::CecUICommandCodes::MEDIA_CONTEXT_SENSITIVE_MENU,
    ::android::hardware::tv::cec::V2_0::CecUICommandCodes::NUMBER_ENTRY_MODE,
    ::android::hardware::tv::cec::V2_0::CecUICommandCodes::NUMBER_11,
    ::android::hardware::tv::cec::V2_0::CecUICommandCodes::NUMBER_12,
    ::android::hardware::tv::cec::V2_0::CecUICommandCodes::NUMBER_0,
    ::android::hardware::tv::cec::V2_0::CecUICommandCodes::NUMBER_1,
    ::android::hardware::tv::cec::V2_0::CecUICommandCodes::NUMBER_2,
    ::android::hardware::tv::cec::V2_0::CecUICommandCodes::NUMBER_3,
    ::android::hardware::tv::cec::V2_0::CecUICommandCodes::NUMBER_4,
    ::android::hardware::tv::cec::V2_0::CecUICommandCodes::NUMBER_5,
    ::android::hardware::tv::cec::V2_0::CecUICommandCodes::NUMBER_6,
    ::android::hardware::tv::cec::V2_0::CecUICommandCodes::NUMBER_7,
    ::android::hardware::tv::cec::V2_0::CecUICommandCodes::NUMBER_8,
    ::android::hardware::tv::cec::V2_0::CecUICommandCodes::NUMBER_9,
    ::android::hardware::tv::cec::V2_0::CecUICommandCodes::DOT,
    ::android::hardware::tv::cec::V2_0::CecUICommandCodes::ENTER,
    ::android::hardware::tv::cec::V2_0::CecUICommandCodes::CLEAR,
    ::android::hardware::tv::cec::V2_0::CecUICommandCodes::NEXT_FAVORITE,
    ::android::hardware::tv::cec::V2_0::CecUICommandCodes::CHANNEL_UP,
    ::android::hardware::tv::cec::V2_0::CecUICommandCodes::CHANNEL_DOWN,
    ::android::hardware::tv::cec::V2_0::CecUICommandCodes::PREVIOUS_CHANNEL,
    ::android::hardware::tv::cec::V2_0::CecUICommandCodes::SOUND_SELECT,
    ::android::hardware::tv::cec::V2_0::CecUICommandCodes::INPUT_SELECT,
    ::android::hardware::tv::cec::V2_0::CecUICommandCodes::DISPLAY_INFORMATION,
    ::android::hardware::tv::cec::V2_0::CecUICommandCodes::HELP,
    ::android::hardware::tv::cec::V2_0::CecUICommandCodes::PAGE_UP,
    ::android::hardware::tv::cec::V2_0::CecUICommandCodes::PAGE_DOWN,
    ::android::hardware::tv::cec::V2_0::CecUICommandCodes::POWER,
    ::android::hardware::tv::cec::V2_0::CecUICommandCodes::VOLUME_UP,
    ::android::hardware::tv::cec::V2_0::CecUICommandCodes::VOLUME_DOWN,
    ::android::hardware::tv::cec::V2_0::CecUICommandCodes::MUTE,
    ::android::hardware::tv::cec::V2_0::CecUICommandCodes::PLAY,
    ::android::hardware::tv::cec::V2_0::CecUICommandCodes::STOP,
    ::android::hardware::tv::cec::V2_0::CecUICommandCodes::PAUSE,
    ::android::hardware::tv::cec::V2_0::CecUICommandCodes::RECORD,
    ::android::hardware::tv::cec::V2_0::CecUICommandCodes::REWIND,
    ::android::hardware::tv::cec::V2_0::CecUICommandCodes::FAST_FORWARD,
    ::android::hardware::tv::cec::V2_0::CecUICommandCodes::EJECT,
    ::android::hardware::tv::cec::V2_0::CecUICommandCodes::SKIP_FORWARD,
    ::android::hardware::tv::cec::V2_0::CecUICommandCodes::SKIP_BACKWARD,
    ::android::hardware::tv::cec::V2_0::CecUICommandCodes::STOP_RECORD,
    ::android::hardware::tv::cec::V2_0::CecUICommandCodes::PAUSE_RECORD,
    ::android::hardware::tv::cec::V2_0::CecUICommandCodes::ANGLE,
    ::android::hardware::tv::cec::V2_0::CecUICommandCodes::SUB_PICTURE,
    ::android::hardware::tv::cec::V2_0::CecUICommandCodes::VIDEO_ON_DEMAND,
    ::android::hardware::tv::cec::V2_0::CecUICommandCodes::ELECTRONIC_PROGRAM_GUIDE,
    ::android::hardware::tv::cec::V2_0::CecUICommandCodes::TIMER_PROGRAMMING,
    ::android::hardware::tv::cec::V2_0::CecUICommandCodes::INITIAL_CONFIGURATION,
    ::android::hardware::tv::cec::V2_0::CecUICommandCodes::SELECT_BROADCAST_TYPE,
    ::android::hardware::tv::cec::V2_0::CecUICommandCodes::SELECT_SOUND_PRESENTATION,
    ::android::hardware::tv::cec::V2_0::CecUICommandCodes::AUDIO_DESCRIPTION,
    ::android::hardware::tv::cec::V2_0::CecUICommandCodes::INTERNET,
    ::android::hardware::tv::cec::V2_0::CecUICommandCodes::THREE_DIMENSIONAL_MODE,
    ::android::hardware::tv::cec::V2_0::CecUICommandCodes::PLAY_FUNCTION,
    ::android::hardware::tv::cec::V2_0::CecUICommandCodes::PAUSE_PLAY_FUNCTION,
    ::android::hardware::tv::cec::V2_0::CecUICommandCodes::RECORD_FUNCTION,
    ::android::hardware::tv::cec::V2_0::CecUICommandCodes::PAUSE_RECORD_FUNCTION,
    ::android::hardware::tv::cec::V2_0::CecUICommandCodes::STOP_FUNCTION,
    ::android::hardware::tv::cec::V2_0::CecUICommandCodes::MUTE_FUNCTION,
    ::android::hardware::tv::cec::V2_0::CecUICommandCodes::RESTORE_VOLUME_FUNCTION,
    ::android::hardware::tv::cec::V2_0::CecUICommandCodes::TUNE_FUNCTION,
    ::android::hardware::tv::cec::V2_0::CecUICommandCodes::SELECT_MEDIA_FUNCTION,
    ::android::hardware::tv::cec::V2_0::CecUICommandCodes::SELECT_AV_INPUT_FUNCTION,
    ::android::hardware::tv::cec::V2_0::CecUICommandCodes::SELECT_AUDIO_INPUT_FUNCTION,
    ::android::hardware::tv::cec::V2_0::CecUICommandCodes::POWER_TOGGLE_FUNCTION,
    ::android::hardware::tv::cec::V2_0::CecUICommandCodes::POWER_OFF_FUNCTION,
    ::android::hardware::tv::cec::V2_0::CecUICommandCodes::POWER_ON_FUNCTION,
    ::android::hardware::tv::cec::V2_0::CecUICommandCodes::F1,
    ::android::hardware::tv::cec::V2_0::CecUICommandCodes::F2,
    ::android::hardware::tv::cec::V2_0::CecUICommandCodes::F3,
    ::android::hardware::tv::cec::V2_0::CecUICommandCodes::F4,
    ::android::hardware::tv::cec::V2_0::CecUICommandCodes::F5,
    ::android::hardware::tv::cec::V2_0::CecUICommandCodes::DATA,
};
#pragma clang diagnostic pop
}  // namespace details
}  // namespace hardware
}  // namespace android

namespace android {
namespace hardware {
namespace details {
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wc++17-extensions"
template<> inline constexpr std::array<::android::hardware::tv::cec::V2_0::HdmiPortType, 2> hidl_enum_values<::android::hardware::tv::cec::V2_0::HdmiPortType> = {
    ::android::hardware::tv::cec::V2_0::HdmiPortType::INPUT,
    ::android::hardware::tv::cec::V2_0::HdmiPortType::OUTPUT,
};
#pragma clang diagnostic pop
}  // namespace details
}  // namespace hardware
}  // namespace android

namespace android {
namespace hardware {
namespace details {
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wc++17-extensions"
template<> inline constexpr std::array<::android::hardware::tv::cec::V2_0::OptionKey, 3> hidl_enum_values<::android::hardware::tv::cec::V2_0::OptionKey> = {
    ::android::hardware::tv::cec::V2_0::OptionKey::WAKEUP,
    ::android::hardware::tv::cec::V2_0::OptionKey::ENABLE_CEC,
    ::android::hardware::tv::cec::V2_0::OptionKey::SYSTEM_CEC_CONTROL,
};
#pragma clang diagnostic pop
}  // namespace details
}  // namespace hardware
}  // namespace android

namespace android {
namespace hardware {
namespace details {
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wc++17-extensions"
template<> inline constexpr std::array<::android::hardware::tv::cec::V2_0::Result, 6> hidl_enum_values<::android::hardware::tv::cec::V2_0::Result> = {
    ::android::hardware::tv::cec::V2_0::Result::SUCCESS,
    ::android::hardware::tv::cec::V2_0::Result::FAILURE_UNKNOWN,
    ::android::hardware::tv::cec::V2_0::Result::FAILURE_INVALID_ARGS,
    ::android::hardware::tv::cec::V2_0::Result::FAILURE_INVALID_STATE,
    ::android::hardware::tv::cec::V2_0::Result::FAILURE_NOT_SUPPORTED,
    ::android::hardware::tv::cec::V2_0::Result::FAILURE_BUSY,
};
#pragma clang diagnostic pop
}  // namespace details
}  // namespace hardware
}  // namespace android


#endif  // HIDL_GENERATED_ANDROID_HARDWARE_TV_CEC_V2_0_TYPES_H
