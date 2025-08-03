#ifndef HIDL_GENERATED_ANDROID_HARDWARE_TV_CEC_V1_0_TYPES_H
#define HIDL_GENERATED_ANDROID_HARDWARE_TV_CEC_V1_0_TYPES_H

#include <hidl/HidlSupport.h>
#include <hidl/MQDescriptor.h>
#include <utils/NativeHandle.h>
#include <utils/misc.h>

namespace android {
namespace hardware {
namespace tv {
namespace cec {
namespace V1_0 {

// Forward declaration for forward reference support:
enum class MaxLength : int32_t;
enum class CecDeviceType : int32_t;
enum class CecLogicalAddress : int32_t;
enum class CecMessageType : int32_t;
enum class AbortReason : int32_t;
enum class Result : int32_t;
enum class SendMessageResult : int32_t;
enum class HdmiPortType : int32_t;
enum class OptionKey : int32_t;
struct CecMessage;
struct HotplugEvent;
struct HdmiPortInfo;

enum class MaxLength : int32_t {
    MESSAGE_BODY = 15,
};

enum class CecDeviceType : int32_t {
    INACTIVE = -1 /* -1 */,
    TV = 0,
    RECORDER = 1,
    TUNER = 3,
    PLAYBACK = 4,
    AUDIO_SYSTEM = 5,
    MAX = 5 /* AUDIO_SYSTEM */,
};

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
    FREE_USE = 14,
    UNREGISTERED = 15,
    BROADCAST = 15,
};

/**
 * HDMI CEC message types. The assigned values represent opcode used in CEC
 * frame as specified in CEC Table 8-26 of the CEC Spec 1.4b.
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
    INITIATE_ARC = 192 /* 0xC0 */,
    REPORT_ARC_INITIATED = 193 /* 0xC1 */,
    REPORT_ARC_TERMINATED = 194 /* 0xC2 */,
    REQUEST_ARC_INITIATION = 195 /* 0xC3 */,
    REQUEST_ARC_TERMINATION = 196 /* 0xC4 */,
    TERMINATE_ARC = 197 /* 0xC5 */,
    ABORT = 255 /* 0xFF */,
};

/**
 * Operand description [Abort Reason]
 */
enum class AbortReason : int32_t {
    UNRECOGNIZED_MODE = 0,
    NOT_IN_CORRECT_MODE = 1,
    CANNOT_PROVIDE_SOURCE = 2,
    INVALID_OPERAND = 3,
    REFUSED = 4,
    UNABLE_TO_DETERMINE = 5,
};

enum class Result : int32_t {
    SUCCESS = 0,
    FAILURE_UNKNOWN = 1,
    FAILURE_INVALID_ARGS = 2,
    FAILURE_INVALID_STATE = 3,
    FAILURE_NOT_SUPPORTED = 4,
    FAILURE_BUSY = 5,
};

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
     * True by default.
     */
    WAKEUP = 1,
    /**
     * When set to false, all the CEC commands are discarded. Used when user
     * changes the TV settings to disable CEC functionality.
     * True by default.
     */
    ENABLE_CEC = 2,
    /**
     * Setting this flag to false means Android system must stop handling CEC
     * service and yield the control over to the microprocessor that is powered
     * on through the standby mode. When set to true, the system must gain the
     * control over, hence telling the microprocessor to stop handling the CEC
     * commands. For example, this may be called when system goes in and out of
     * standby mode to notify the microprocessor that it should start/stop
     * handling CEC commands on behalf of the system.
     * False by default.
     */
    SYSTEM_CEC_CONTROL = 3,
};

struct CecMessage final {
    /**
     * logical address of sender
     */
    ::android::hardware::tv::cec::V1_0::CecLogicalAddress initiator __attribute__ ((aligned(4)));
    /**
     * logical address of receiver
     */
    ::android::hardware::tv::cec::V1_0::CecLogicalAddress destination __attribute__ ((aligned(4)));
    /**
     * The maximum size of body is 15 (MaxLength::MESSAGE_BODY) as specified in
     * the section 6 of the CEC Spec 1.4b. Overflowed data must be ignored.
     */
    ::android::hardware::hidl_vec<uint8_t> body __attribute__ ((aligned(8)));
};

static_assert(offsetof(::android::hardware::tv::cec::V1_0::CecMessage, initiator) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::tv::cec::V1_0::CecMessage, destination) == 4, "wrong offset");
static_assert(offsetof(::android::hardware::tv::cec::V1_0::CecMessage, body) == 8, "wrong offset");
static_assert(sizeof(::android::hardware::tv::cec::V1_0::CecMessage) == 24, "wrong size");
static_assert(__alignof(::android::hardware::tv::cec::V1_0::CecMessage) == 8, "wrong alignment");

struct HotplugEvent final {
    bool connected __attribute__ ((aligned(1)));
    uint32_t portId __attribute__ ((aligned(4)));
};

static_assert(offsetof(::android::hardware::tv::cec::V1_0::HotplugEvent, connected) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::tv::cec::V1_0::HotplugEvent, portId) == 4, "wrong offset");
static_assert(sizeof(::android::hardware::tv::cec::V1_0::HotplugEvent) == 8, "wrong size");
static_assert(__alignof(::android::hardware::tv::cec::V1_0::HotplugEvent) == 4, "wrong alignment");

/**
 * HDMI port descriptor
 */
struct HdmiPortInfo final {
    ::android::hardware::tv::cec::V1_0::HdmiPortType type __attribute__ ((aligned(4)));
    uint32_t portId __attribute__ ((aligned(4)));
    bool cecSupported __attribute__ ((aligned(1)));
    bool arcSupported __attribute__ ((aligned(1)));
    uint16_t physicalAddress __attribute__ ((aligned(2)));
};

static_assert(offsetof(::android::hardware::tv::cec::V1_0::HdmiPortInfo, type) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::tv::cec::V1_0::HdmiPortInfo, portId) == 4, "wrong offset");
static_assert(offsetof(::android::hardware::tv::cec::V1_0::HdmiPortInfo, cecSupported) == 8, "wrong offset");
static_assert(offsetof(::android::hardware::tv::cec::V1_0::HdmiPortInfo, arcSupported) == 9, "wrong offset");
static_assert(offsetof(::android::hardware::tv::cec::V1_0::HdmiPortInfo, physicalAddress) == 10, "wrong offset");
static_assert(sizeof(::android::hardware::tv::cec::V1_0::HdmiPortInfo) == 12, "wrong size");
static_assert(__alignof(::android::hardware::tv::cec::V1_0::HdmiPortInfo) == 4, "wrong alignment");

//
// type declarations for package
//

template<typename>
static inline std::string toString(int32_t o);
static inline std::string toString(::android::hardware::tv::cec::V1_0::MaxLength o);
static inline void PrintTo(::android::hardware::tv::cec::V1_0::MaxLength o, ::std::ostream* os);
constexpr int32_t operator|(const ::android::hardware::tv::cec::V1_0::MaxLength lhs, const ::android::hardware::tv::cec::V1_0::MaxLength rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const int32_t lhs, const ::android::hardware::tv::cec::V1_0::MaxLength rhs) {
    return static_cast<int32_t>(lhs | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const ::android::hardware::tv::cec::V1_0::MaxLength lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | rhs);
}
constexpr int32_t operator&(const ::android::hardware::tv::cec::V1_0::MaxLength lhs, const ::android::hardware::tv::cec::V1_0::MaxLength rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const int32_t lhs, const ::android::hardware::tv::cec::V1_0::MaxLength rhs) {
    return static_cast<int32_t>(lhs & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const ::android::hardware::tv::cec::V1_0::MaxLength lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & rhs);
}
constexpr int32_t &operator|=(int32_t& v, const ::android::hardware::tv::cec::V1_0::MaxLength e) {
    v |= static_cast<int32_t>(e);
    return v;
}
constexpr int32_t &operator&=(int32_t& v, const ::android::hardware::tv::cec::V1_0::MaxLength e) {
    v &= static_cast<int32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(int32_t o);
static inline std::string toString(::android::hardware::tv::cec::V1_0::CecDeviceType o);
static inline void PrintTo(::android::hardware::tv::cec::V1_0::CecDeviceType o, ::std::ostream* os);
constexpr int32_t operator|(const ::android::hardware::tv::cec::V1_0::CecDeviceType lhs, const ::android::hardware::tv::cec::V1_0::CecDeviceType rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const int32_t lhs, const ::android::hardware::tv::cec::V1_0::CecDeviceType rhs) {
    return static_cast<int32_t>(lhs | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const ::android::hardware::tv::cec::V1_0::CecDeviceType lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | rhs);
}
constexpr int32_t operator&(const ::android::hardware::tv::cec::V1_0::CecDeviceType lhs, const ::android::hardware::tv::cec::V1_0::CecDeviceType rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const int32_t lhs, const ::android::hardware::tv::cec::V1_0::CecDeviceType rhs) {
    return static_cast<int32_t>(lhs & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const ::android::hardware::tv::cec::V1_0::CecDeviceType lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & rhs);
}
constexpr int32_t &operator|=(int32_t& v, const ::android::hardware::tv::cec::V1_0::CecDeviceType e) {
    v |= static_cast<int32_t>(e);
    return v;
}
constexpr int32_t &operator&=(int32_t& v, const ::android::hardware::tv::cec::V1_0::CecDeviceType e) {
    v &= static_cast<int32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(int32_t o);
static inline std::string toString(::android::hardware::tv::cec::V1_0::CecLogicalAddress o);
static inline void PrintTo(::android::hardware::tv::cec::V1_0::CecLogicalAddress o, ::std::ostream* os);
constexpr int32_t operator|(const ::android::hardware::tv::cec::V1_0::CecLogicalAddress lhs, const ::android::hardware::tv::cec::V1_0::CecLogicalAddress rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const int32_t lhs, const ::android::hardware::tv::cec::V1_0::CecLogicalAddress rhs) {
    return static_cast<int32_t>(lhs | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const ::android::hardware::tv::cec::V1_0::CecLogicalAddress lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | rhs);
}
constexpr int32_t operator&(const ::android::hardware::tv::cec::V1_0::CecLogicalAddress lhs, const ::android::hardware::tv::cec::V1_0::CecLogicalAddress rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const int32_t lhs, const ::android::hardware::tv::cec::V1_0::CecLogicalAddress rhs) {
    return static_cast<int32_t>(lhs & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const ::android::hardware::tv::cec::V1_0::CecLogicalAddress lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & rhs);
}
constexpr int32_t &operator|=(int32_t& v, const ::android::hardware::tv::cec::V1_0::CecLogicalAddress e) {
    v |= static_cast<int32_t>(e);
    return v;
}
constexpr int32_t &operator&=(int32_t& v, const ::android::hardware::tv::cec::V1_0::CecLogicalAddress e) {
    v &= static_cast<int32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(int32_t o);
static inline std::string toString(::android::hardware::tv::cec::V1_0::CecMessageType o);
static inline void PrintTo(::android::hardware::tv::cec::V1_0::CecMessageType o, ::std::ostream* os);
constexpr int32_t operator|(const ::android::hardware::tv::cec::V1_0::CecMessageType lhs, const ::android::hardware::tv::cec::V1_0::CecMessageType rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const int32_t lhs, const ::android::hardware::tv::cec::V1_0::CecMessageType rhs) {
    return static_cast<int32_t>(lhs | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const ::android::hardware::tv::cec::V1_0::CecMessageType lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | rhs);
}
constexpr int32_t operator&(const ::android::hardware::tv::cec::V1_0::CecMessageType lhs, const ::android::hardware::tv::cec::V1_0::CecMessageType rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const int32_t lhs, const ::android::hardware::tv::cec::V1_0::CecMessageType rhs) {
    return static_cast<int32_t>(lhs & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const ::android::hardware::tv::cec::V1_0::CecMessageType lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & rhs);
}
constexpr int32_t &operator|=(int32_t& v, const ::android::hardware::tv::cec::V1_0::CecMessageType e) {
    v |= static_cast<int32_t>(e);
    return v;
}
constexpr int32_t &operator&=(int32_t& v, const ::android::hardware::tv::cec::V1_0::CecMessageType e) {
    v &= static_cast<int32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(int32_t o);
static inline std::string toString(::android::hardware::tv::cec::V1_0::AbortReason o);
static inline void PrintTo(::android::hardware::tv::cec::V1_0::AbortReason o, ::std::ostream* os);
constexpr int32_t operator|(const ::android::hardware::tv::cec::V1_0::AbortReason lhs, const ::android::hardware::tv::cec::V1_0::AbortReason rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const int32_t lhs, const ::android::hardware::tv::cec::V1_0::AbortReason rhs) {
    return static_cast<int32_t>(lhs | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const ::android::hardware::tv::cec::V1_0::AbortReason lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | rhs);
}
constexpr int32_t operator&(const ::android::hardware::tv::cec::V1_0::AbortReason lhs, const ::android::hardware::tv::cec::V1_0::AbortReason rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const int32_t lhs, const ::android::hardware::tv::cec::V1_0::AbortReason rhs) {
    return static_cast<int32_t>(lhs & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const ::android::hardware::tv::cec::V1_0::AbortReason lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & rhs);
}
constexpr int32_t &operator|=(int32_t& v, const ::android::hardware::tv::cec::V1_0::AbortReason e) {
    v |= static_cast<int32_t>(e);
    return v;
}
constexpr int32_t &operator&=(int32_t& v, const ::android::hardware::tv::cec::V1_0::AbortReason e) {
    v &= static_cast<int32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(int32_t o);
static inline std::string toString(::android::hardware::tv::cec::V1_0::Result o);
static inline void PrintTo(::android::hardware::tv::cec::V1_0::Result o, ::std::ostream* os);
constexpr int32_t operator|(const ::android::hardware::tv::cec::V1_0::Result lhs, const ::android::hardware::tv::cec::V1_0::Result rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const int32_t lhs, const ::android::hardware::tv::cec::V1_0::Result rhs) {
    return static_cast<int32_t>(lhs | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const ::android::hardware::tv::cec::V1_0::Result lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | rhs);
}
constexpr int32_t operator&(const ::android::hardware::tv::cec::V1_0::Result lhs, const ::android::hardware::tv::cec::V1_0::Result rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const int32_t lhs, const ::android::hardware::tv::cec::V1_0::Result rhs) {
    return static_cast<int32_t>(lhs & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const ::android::hardware::tv::cec::V1_0::Result lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & rhs);
}
constexpr int32_t &operator|=(int32_t& v, const ::android::hardware::tv::cec::V1_0::Result e) {
    v |= static_cast<int32_t>(e);
    return v;
}
constexpr int32_t &operator&=(int32_t& v, const ::android::hardware::tv::cec::V1_0::Result e) {
    v &= static_cast<int32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(int32_t o);
static inline std::string toString(::android::hardware::tv::cec::V1_0::SendMessageResult o);
static inline void PrintTo(::android::hardware::tv::cec::V1_0::SendMessageResult o, ::std::ostream* os);
constexpr int32_t operator|(const ::android::hardware::tv::cec::V1_0::SendMessageResult lhs, const ::android::hardware::tv::cec::V1_0::SendMessageResult rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const int32_t lhs, const ::android::hardware::tv::cec::V1_0::SendMessageResult rhs) {
    return static_cast<int32_t>(lhs | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const ::android::hardware::tv::cec::V1_0::SendMessageResult lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | rhs);
}
constexpr int32_t operator&(const ::android::hardware::tv::cec::V1_0::SendMessageResult lhs, const ::android::hardware::tv::cec::V1_0::SendMessageResult rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const int32_t lhs, const ::android::hardware::tv::cec::V1_0::SendMessageResult rhs) {
    return static_cast<int32_t>(lhs & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const ::android::hardware::tv::cec::V1_0::SendMessageResult lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & rhs);
}
constexpr int32_t &operator|=(int32_t& v, const ::android::hardware::tv::cec::V1_0::SendMessageResult e) {
    v |= static_cast<int32_t>(e);
    return v;
}
constexpr int32_t &operator&=(int32_t& v, const ::android::hardware::tv::cec::V1_0::SendMessageResult e) {
    v &= static_cast<int32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(int32_t o);
static inline std::string toString(::android::hardware::tv::cec::V1_0::HdmiPortType o);
static inline void PrintTo(::android::hardware::tv::cec::V1_0::HdmiPortType o, ::std::ostream* os);
constexpr int32_t operator|(const ::android::hardware::tv::cec::V1_0::HdmiPortType lhs, const ::android::hardware::tv::cec::V1_0::HdmiPortType rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const int32_t lhs, const ::android::hardware::tv::cec::V1_0::HdmiPortType rhs) {
    return static_cast<int32_t>(lhs | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const ::android::hardware::tv::cec::V1_0::HdmiPortType lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | rhs);
}
constexpr int32_t operator&(const ::android::hardware::tv::cec::V1_0::HdmiPortType lhs, const ::android::hardware::tv::cec::V1_0::HdmiPortType rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const int32_t lhs, const ::android::hardware::tv::cec::V1_0::HdmiPortType rhs) {
    return static_cast<int32_t>(lhs & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const ::android::hardware::tv::cec::V1_0::HdmiPortType lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & rhs);
}
constexpr int32_t &operator|=(int32_t& v, const ::android::hardware::tv::cec::V1_0::HdmiPortType e) {
    v |= static_cast<int32_t>(e);
    return v;
}
constexpr int32_t &operator&=(int32_t& v, const ::android::hardware::tv::cec::V1_0::HdmiPortType e) {
    v &= static_cast<int32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(int32_t o);
static inline std::string toString(::android::hardware::tv::cec::V1_0::OptionKey o);
static inline void PrintTo(::android::hardware::tv::cec::V1_0::OptionKey o, ::std::ostream* os);
constexpr int32_t operator|(const ::android::hardware::tv::cec::V1_0::OptionKey lhs, const ::android::hardware::tv::cec::V1_0::OptionKey rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const int32_t lhs, const ::android::hardware::tv::cec::V1_0::OptionKey rhs) {
    return static_cast<int32_t>(lhs | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const ::android::hardware::tv::cec::V1_0::OptionKey lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | rhs);
}
constexpr int32_t operator&(const ::android::hardware::tv::cec::V1_0::OptionKey lhs, const ::android::hardware::tv::cec::V1_0::OptionKey rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const int32_t lhs, const ::android::hardware::tv::cec::V1_0::OptionKey rhs) {
    return static_cast<int32_t>(lhs & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const ::android::hardware::tv::cec::V1_0::OptionKey lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & rhs);
}
constexpr int32_t &operator|=(int32_t& v, const ::android::hardware::tv::cec::V1_0::OptionKey e) {
    v |= static_cast<int32_t>(e);
    return v;
}
constexpr int32_t &operator&=(int32_t& v, const ::android::hardware::tv::cec::V1_0::OptionKey e) {
    v &= static_cast<int32_t>(e);
    return v;
}

static inline std::string toString(const ::android::hardware::tv::cec::V1_0::CecMessage& o);
static inline void PrintTo(const ::android::hardware::tv::cec::V1_0::CecMessage& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::tv::cec::V1_0::CecMessage& lhs, const ::android::hardware::tv::cec::V1_0::CecMessage& rhs);
static inline bool operator!=(const ::android::hardware::tv::cec::V1_0::CecMessage& lhs, const ::android::hardware::tv::cec::V1_0::CecMessage& rhs);

static inline std::string toString(const ::android::hardware::tv::cec::V1_0::HotplugEvent& o);
static inline void PrintTo(const ::android::hardware::tv::cec::V1_0::HotplugEvent& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::tv::cec::V1_0::HotplugEvent& lhs, const ::android::hardware::tv::cec::V1_0::HotplugEvent& rhs);
static inline bool operator!=(const ::android::hardware::tv::cec::V1_0::HotplugEvent& lhs, const ::android::hardware::tv::cec::V1_0::HotplugEvent& rhs);

static inline std::string toString(const ::android::hardware::tv::cec::V1_0::HdmiPortInfo& o);
static inline void PrintTo(const ::android::hardware::tv::cec::V1_0::HdmiPortInfo& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::tv::cec::V1_0::HdmiPortInfo& lhs, const ::android::hardware::tv::cec::V1_0::HdmiPortInfo& rhs);
static inline bool operator!=(const ::android::hardware::tv::cec::V1_0::HdmiPortInfo& lhs, const ::android::hardware::tv::cec::V1_0::HdmiPortInfo& rhs);

//
// type header definitions for package
//

template<>
inline std::string toString<::android::hardware::tv::cec::V1_0::MaxLength>(int32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::tv::cec::V1_0::MaxLength> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::tv::cec::V1_0::MaxLength::MESSAGE_BODY) == static_cast<int32_t>(::android::hardware::tv::cec::V1_0::MaxLength::MESSAGE_BODY)) {
        os += (first ? "" : " | ");
        os += "MESSAGE_BODY";
        first = false;
        flipped |= ::android::hardware::tv::cec::V1_0::MaxLength::MESSAGE_BODY;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::tv::cec::V1_0::MaxLength o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::tv::cec::V1_0::MaxLength::MESSAGE_BODY) {
        return "MESSAGE_BODY";
    }
    std::string os;
    os += toHexString(static_cast<int32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::tv::cec::V1_0::MaxLength o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::tv::cec::V1_0::CecDeviceType>(int32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::tv::cec::V1_0::CecDeviceType> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::tv::cec::V1_0::CecDeviceType::INACTIVE) == static_cast<int32_t>(::android::hardware::tv::cec::V1_0::CecDeviceType::INACTIVE)) {
        os += (first ? "" : " | ");
        os += "INACTIVE";
        first = false;
        flipped |= ::android::hardware::tv::cec::V1_0::CecDeviceType::INACTIVE;
    }
    if ((o & ::android::hardware::tv::cec::V1_0::CecDeviceType::TV) == static_cast<int32_t>(::android::hardware::tv::cec::V1_0::CecDeviceType::TV)) {
        os += (first ? "" : " | ");
        os += "TV";
        first = false;
        flipped |= ::android::hardware::tv::cec::V1_0::CecDeviceType::TV;
    }
    if ((o & ::android::hardware::tv::cec::V1_0::CecDeviceType::RECORDER) == static_cast<int32_t>(::android::hardware::tv::cec::V1_0::CecDeviceType::RECORDER)) {
        os += (first ? "" : " | ");
        os += "RECORDER";
        first = false;
        flipped |= ::android::hardware::tv::cec::V1_0::CecDeviceType::RECORDER;
    }
    if ((o & ::android::hardware::tv::cec::V1_0::CecDeviceType::TUNER) == static_cast<int32_t>(::android::hardware::tv::cec::V1_0::CecDeviceType::TUNER)) {
        os += (first ? "" : " | ");
        os += "TUNER";
        first = false;
        flipped |= ::android::hardware::tv::cec::V1_0::CecDeviceType::TUNER;
    }
    if ((o & ::android::hardware::tv::cec::V1_0::CecDeviceType::PLAYBACK) == static_cast<int32_t>(::android::hardware::tv::cec::V1_0::CecDeviceType::PLAYBACK)) {
        os += (first ? "" : " | ");
        os += "PLAYBACK";
        first = false;
        flipped |= ::android::hardware::tv::cec::V1_0::CecDeviceType::PLAYBACK;
    }
    if ((o & ::android::hardware::tv::cec::V1_0::CecDeviceType::AUDIO_SYSTEM) == static_cast<int32_t>(::android::hardware::tv::cec::V1_0::CecDeviceType::AUDIO_SYSTEM)) {
        os += (first ? "" : " | ");
        os += "AUDIO_SYSTEM";
        first = false;
        flipped |= ::android::hardware::tv::cec::V1_0::CecDeviceType::AUDIO_SYSTEM;
    }
    if ((o & ::android::hardware::tv::cec::V1_0::CecDeviceType::MAX) == static_cast<int32_t>(::android::hardware::tv::cec::V1_0::CecDeviceType::MAX)) {
        os += (first ? "" : " | ");
        os += "MAX";
        first = false;
        flipped |= ::android::hardware::tv::cec::V1_0::CecDeviceType::MAX;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::tv::cec::V1_0::CecDeviceType o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::tv::cec::V1_0::CecDeviceType::INACTIVE) {
        return "INACTIVE";
    }
    if (o == ::android::hardware::tv::cec::V1_0::CecDeviceType::TV) {
        return "TV";
    }
    if (o == ::android::hardware::tv::cec::V1_0::CecDeviceType::RECORDER) {
        return "RECORDER";
    }
    if (o == ::android::hardware::tv::cec::V1_0::CecDeviceType::TUNER) {
        return "TUNER";
    }
    if (o == ::android::hardware::tv::cec::V1_0::CecDeviceType::PLAYBACK) {
        return "PLAYBACK";
    }
    if (o == ::android::hardware::tv::cec::V1_0::CecDeviceType::AUDIO_SYSTEM) {
        return "AUDIO_SYSTEM";
    }
    if (o == ::android::hardware::tv::cec::V1_0::CecDeviceType::MAX) {
        return "MAX";
    }
    std::string os;
    os += toHexString(static_cast<int32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::tv::cec::V1_0::CecDeviceType o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::tv::cec::V1_0::CecLogicalAddress>(int32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::tv::cec::V1_0::CecLogicalAddress> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::tv::cec::V1_0::CecLogicalAddress::TV) == static_cast<int32_t>(::android::hardware::tv::cec::V1_0::CecLogicalAddress::TV)) {
        os += (first ? "" : " | ");
        os += "TV";
        first = false;
        flipped |= ::android::hardware::tv::cec::V1_0::CecLogicalAddress::TV;
    }
    if ((o & ::android::hardware::tv::cec::V1_0::CecLogicalAddress::RECORDER_1) == static_cast<int32_t>(::android::hardware::tv::cec::V1_0::CecLogicalAddress::RECORDER_1)) {
        os += (first ? "" : " | ");
        os += "RECORDER_1";
        first = false;
        flipped |= ::android::hardware::tv::cec::V1_0::CecLogicalAddress::RECORDER_1;
    }
    if ((o & ::android::hardware::tv::cec::V1_0::CecLogicalAddress::RECORDER_2) == static_cast<int32_t>(::android::hardware::tv::cec::V1_0::CecLogicalAddress::RECORDER_2)) {
        os += (first ? "" : " | ");
        os += "RECORDER_2";
        first = false;
        flipped |= ::android::hardware::tv::cec::V1_0::CecLogicalAddress::RECORDER_2;
    }
    if ((o & ::android::hardware::tv::cec::V1_0::CecLogicalAddress::TUNER_1) == static_cast<int32_t>(::android::hardware::tv::cec::V1_0::CecLogicalAddress::TUNER_1)) {
        os += (first ? "" : " | ");
        os += "TUNER_1";
        first = false;
        flipped |= ::android::hardware::tv::cec::V1_0::CecLogicalAddress::TUNER_1;
    }
    if ((o & ::android::hardware::tv::cec::V1_0::CecLogicalAddress::PLAYBACK_1) == static_cast<int32_t>(::android::hardware::tv::cec::V1_0::CecLogicalAddress::PLAYBACK_1)) {
        os += (first ? "" : " | ");
        os += "PLAYBACK_1";
        first = false;
        flipped |= ::android::hardware::tv::cec::V1_0::CecLogicalAddress::PLAYBACK_1;
    }
    if ((o & ::android::hardware::tv::cec::V1_0::CecLogicalAddress::AUDIO_SYSTEM) == static_cast<int32_t>(::android::hardware::tv::cec::V1_0::CecLogicalAddress::AUDIO_SYSTEM)) {
        os += (first ? "" : " | ");
        os += "AUDIO_SYSTEM";
        first = false;
        flipped |= ::android::hardware::tv::cec::V1_0::CecLogicalAddress::AUDIO_SYSTEM;
    }
    if ((o & ::android::hardware::tv::cec::V1_0::CecLogicalAddress::TUNER_2) == static_cast<int32_t>(::android::hardware::tv::cec::V1_0::CecLogicalAddress::TUNER_2)) {
        os += (first ? "" : " | ");
        os += "TUNER_2";
        first = false;
        flipped |= ::android::hardware::tv::cec::V1_0::CecLogicalAddress::TUNER_2;
    }
    if ((o & ::android::hardware::tv::cec::V1_0::CecLogicalAddress::TUNER_3) == static_cast<int32_t>(::android::hardware::tv::cec::V1_0::CecLogicalAddress::TUNER_3)) {
        os += (first ? "" : " | ");
        os += "TUNER_3";
        first = false;
        flipped |= ::android::hardware::tv::cec::V1_0::CecLogicalAddress::TUNER_3;
    }
    if ((o & ::android::hardware::tv::cec::V1_0::CecLogicalAddress::PLAYBACK_2) == static_cast<int32_t>(::android::hardware::tv::cec::V1_0::CecLogicalAddress::PLAYBACK_2)) {
        os += (first ? "" : " | ");
        os += "PLAYBACK_2";
        first = false;
        flipped |= ::android::hardware::tv::cec::V1_0::CecLogicalAddress::PLAYBACK_2;
    }
    if ((o & ::android::hardware::tv::cec::V1_0::CecLogicalAddress::RECORDER_3) == static_cast<int32_t>(::android::hardware::tv::cec::V1_0::CecLogicalAddress::RECORDER_3)) {
        os += (first ? "" : " | ");
        os += "RECORDER_3";
        first = false;
        flipped |= ::android::hardware::tv::cec::V1_0::CecLogicalAddress::RECORDER_3;
    }
    if ((o & ::android::hardware::tv::cec::V1_0::CecLogicalAddress::TUNER_4) == static_cast<int32_t>(::android::hardware::tv::cec::V1_0::CecLogicalAddress::TUNER_4)) {
        os += (first ? "" : " | ");
        os += "TUNER_4";
        first = false;
        flipped |= ::android::hardware::tv::cec::V1_0::CecLogicalAddress::TUNER_4;
    }
    if ((o & ::android::hardware::tv::cec::V1_0::CecLogicalAddress::PLAYBACK_3) == static_cast<int32_t>(::android::hardware::tv::cec::V1_0::CecLogicalAddress::PLAYBACK_3)) {
        os += (first ? "" : " | ");
        os += "PLAYBACK_3";
        first = false;
        flipped |= ::android::hardware::tv::cec::V1_0::CecLogicalAddress::PLAYBACK_3;
    }
    if ((o & ::android::hardware::tv::cec::V1_0::CecLogicalAddress::FREE_USE) == static_cast<int32_t>(::android::hardware::tv::cec::V1_0::CecLogicalAddress::FREE_USE)) {
        os += (first ? "" : " | ");
        os += "FREE_USE";
        first = false;
        flipped |= ::android::hardware::tv::cec::V1_0::CecLogicalAddress::FREE_USE;
    }
    if ((o & ::android::hardware::tv::cec::V1_0::CecLogicalAddress::UNREGISTERED) == static_cast<int32_t>(::android::hardware::tv::cec::V1_0::CecLogicalAddress::UNREGISTERED)) {
        os += (first ? "" : " | ");
        os += "UNREGISTERED";
        first = false;
        flipped |= ::android::hardware::tv::cec::V1_0::CecLogicalAddress::UNREGISTERED;
    }
    if ((o & ::android::hardware::tv::cec::V1_0::CecLogicalAddress::BROADCAST) == static_cast<int32_t>(::android::hardware::tv::cec::V1_0::CecLogicalAddress::BROADCAST)) {
        os += (first ? "" : " | ");
        os += "BROADCAST";
        first = false;
        flipped |= ::android::hardware::tv::cec::V1_0::CecLogicalAddress::BROADCAST;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::tv::cec::V1_0::CecLogicalAddress o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::tv::cec::V1_0::CecLogicalAddress::TV) {
        return "TV";
    }
    if (o == ::android::hardware::tv::cec::V1_0::CecLogicalAddress::RECORDER_1) {
        return "RECORDER_1";
    }
    if (o == ::android::hardware::tv::cec::V1_0::CecLogicalAddress::RECORDER_2) {
        return "RECORDER_2";
    }
    if (o == ::android::hardware::tv::cec::V1_0::CecLogicalAddress::TUNER_1) {
        return "TUNER_1";
    }
    if (o == ::android::hardware::tv::cec::V1_0::CecLogicalAddress::PLAYBACK_1) {
        return "PLAYBACK_1";
    }
    if (o == ::android::hardware::tv::cec::V1_0::CecLogicalAddress::AUDIO_SYSTEM) {
        return "AUDIO_SYSTEM";
    }
    if (o == ::android::hardware::tv::cec::V1_0::CecLogicalAddress::TUNER_2) {
        return "TUNER_2";
    }
    if (o == ::android::hardware::tv::cec::V1_0::CecLogicalAddress::TUNER_3) {
        return "TUNER_3";
    }
    if (o == ::android::hardware::tv::cec::V1_0::CecLogicalAddress::PLAYBACK_2) {
        return "PLAYBACK_2";
    }
    if (o == ::android::hardware::tv::cec::V1_0::CecLogicalAddress::RECORDER_3) {
        return "RECORDER_3";
    }
    if (o == ::android::hardware::tv::cec::V1_0::CecLogicalAddress::TUNER_4) {
        return "TUNER_4";
    }
    if (o == ::android::hardware::tv::cec::V1_0::CecLogicalAddress::PLAYBACK_3) {
        return "PLAYBACK_3";
    }
    if (o == ::android::hardware::tv::cec::V1_0::CecLogicalAddress::FREE_USE) {
        return "FREE_USE";
    }
    if (o == ::android::hardware::tv::cec::V1_0::CecLogicalAddress::UNREGISTERED) {
        return "UNREGISTERED";
    }
    if (o == ::android::hardware::tv::cec::V1_0::CecLogicalAddress::BROADCAST) {
        return "BROADCAST";
    }
    std::string os;
    os += toHexString(static_cast<int32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::tv::cec::V1_0::CecLogicalAddress o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::tv::cec::V1_0::CecMessageType>(int32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::tv::cec::V1_0::CecMessageType> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::tv::cec::V1_0::CecMessageType::FEATURE_ABORT) == static_cast<int32_t>(::android::hardware::tv::cec::V1_0::CecMessageType::FEATURE_ABORT)) {
        os += (first ? "" : " | ");
        os += "FEATURE_ABORT";
        first = false;
        flipped |= ::android::hardware::tv::cec::V1_0::CecMessageType::FEATURE_ABORT;
    }
    if ((o & ::android::hardware::tv::cec::V1_0::CecMessageType::IMAGE_VIEW_ON) == static_cast<int32_t>(::android::hardware::tv::cec::V1_0::CecMessageType::IMAGE_VIEW_ON)) {
        os += (first ? "" : " | ");
        os += "IMAGE_VIEW_ON";
        first = false;
        flipped |= ::android::hardware::tv::cec::V1_0::CecMessageType::IMAGE_VIEW_ON;
    }
    if ((o & ::android::hardware::tv::cec::V1_0::CecMessageType::TUNER_STEP_INCREMENT) == static_cast<int32_t>(::android::hardware::tv::cec::V1_0::CecMessageType::TUNER_STEP_INCREMENT)) {
        os += (first ? "" : " | ");
        os += "TUNER_STEP_INCREMENT";
        first = false;
        flipped |= ::android::hardware::tv::cec::V1_0::CecMessageType::TUNER_STEP_INCREMENT;
    }
    if ((o & ::android::hardware::tv::cec::V1_0::CecMessageType::TUNER_STEP_DECREMENT) == static_cast<int32_t>(::android::hardware::tv::cec::V1_0::CecMessageType::TUNER_STEP_DECREMENT)) {
        os += (first ? "" : " | ");
        os += "TUNER_STEP_DECREMENT";
        first = false;
        flipped |= ::android::hardware::tv::cec::V1_0::CecMessageType::TUNER_STEP_DECREMENT;
    }
    if ((o & ::android::hardware::tv::cec::V1_0::CecMessageType::TUNER_DEVICE_STATUS) == static_cast<int32_t>(::android::hardware::tv::cec::V1_0::CecMessageType::TUNER_DEVICE_STATUS)) {
        os += (first ? "" : " | ");
        os += "TUNER_DEVICE_STATUS";
        first = false;
        flipped |= ::android::hardware::tv::cec::V1_0::CecMessageType::TUNER_DEVICE_STATUS;
    }
    if ((o & ::android::hardware::tv::cec::V1_0::CecMessageType::GIVE_TUNER_DEVICE_STATUS) == static_cast<int32_t>(::android::hardware::tv::cec::V1_0::CecMessageType::GIVE_TUNER_DEVICE_STATUS)) {
        os += (first ? "" : " | ");
        os += "GIVE_TUNER_DEVICE_STATUS";
        first = false;
        flipped |= ::android::hardware::tv::cec::V1_0::CecMessageType::GIVE_TUNER_DEVICE_STATUS;
    }
    if ((o & ::android::hardware::tv::cec::V1_0::CecMessageType::RECORD_ON) == static_cast<int32_t>(::android::hardware::tv::cec::V1_0::CecMessageType::RECORD_ON)) {
        os += (first ? "" : " | ");
        os += "RECORD_ON";
        first = false;
        flipped |= ::android::hardware::tv::cec::V1_0::CecMessageType::RECORD_ON;
    }
    if ((o & ::android::hardware::tv::cec::V1_0::CecMessageType::RECORD_STATUS) == static_cast<int32_t>(::android::hardware::tv::cec::V1_0::CecMessageType::RECORD_STATUS)) {
        os += (first ? "" : " | ");
        os += "RECORD_STATUS";
        first = false;
        flipped |= ::android::hardware::tv::cec::V1_0::CecMessageType::RECORD_STATUS;
    }
    if ((o & ::android::hardware::tv::cec::V1_0::CecMessageType::RECORD_OFF) == static_cast<int32_t>(::android::hardware::tv::cec::V1_0::CecMessageType::RECORD_OFF)) {
        os += (first ? "" : " | ");
        os += "RECORD_OFF";
        first = false;
        flipped |= ::android::hardware::tv::cec::V1_0::CecMessageType::RECORD_OFF;
    }
    if ((o & ::android::hardware::tv::cec::V1_0::CecMessageType::TEXT_VIEW_ON) == static_cast<int32_t>(::android::hardware::tv::cec::V1_0::CecMessageType::TEXT_VIEW_ON)) {
        os += (first ? "" : " | ");
        os += "TEXT_VIEW_ON";
        first = false;
        flipped |= ::android::hardware::tv::cec::V1_0::CecMessageType::TEXT_VIEW_ON;
    }
    if ((o & ::android::hardware::tv::cec::V1_0::CecMessageType::RECORD_TV_SCREEN) == static_cast<int32_t>(::android::hardware::tv::cec::V1_0::CecMessageType::RECORD_TV_SCREEN)) {
        os += (first ? "" : " | ");
        os += "RECORD_TV_SCREEN";
        first = false;
        flipped |= ::android::hardware::tv::cec::V1_0::CecMessageType::RECORD_TV_SCREEN;
    }
    if ((o & ::android::hardware::tv::cec::V1_0::CecMessageType::GIVE_DECK_STATUS) == static_cast<int32_t>(::android::hardware::tv::cec::V1_0::CecMessageType::GIVE_DECK_STATUS)) {
        os += (first ? "" : " | ");
        os += "GIVE_DECK_STATUS";
        first = false;
        flipped |= ::android::hardware::tv::cec::V1_0::CecMessageType::GIVE_DECK_STATUS;
    }
    if ((o & ::android::hardware::tv::cec::V1_0::CecMessageType::DECK_STATUS) == static_cast<int32_t>(::android::hardware::tv::cec::V1_0::CecMessageType::DECK_STATUS)) {
        os += (first ? "" : " | ");
        os += "DECK_STATUS";
        first = false;
        flipped |= ::android::hardware::tv::cec::V1_0::CecMessageType::DECK_STATUS;
    }
    if ((o & ::android::hardware::tv::cec::V1_0::CecMessageType::SET_MENU_LANGUAGE) == static_cast<int32_t>(::android::hardware::tv::cec::V1_0::CecMessageType::SET_MENU_LANGUAGE)) {
        os += (first ? "" : " | ");
        os += "SET_MENU_LANGUAGE";
        first = false;
        flipped |= ::android::hardware::tv::cec::V1_0::CecMessageType::SET_MENU_LANGUAGE;
    }
    if ((o & ::android::hardware::tv::cec::V1_0::CecMessageType::CLEAR_ANALOG_TIMER) == static_cast<int32_t>(::android::hardware::tv::cec::V1_0::CecMessageType::CLEAR_ANALOG_TIMER)) {
        os += (first ? "" : " | ");
        os += "CLEAR_ANALOG_TIMER";
        first = false;
        flipped |= ::android::hardware::tv::cec::V1_0::CecMessageType::CLEAR_ANALOG_TIMER;
    }
    if ((o & ::android::hardware::tv::cec::V1_0::CecMessageType::SET_ANALOG_TIMER) == static_cast<int32_t>(::android::hardware::tv::cec::V1_0::CecMessageType::SET_ANALOG_TIMER)) {
        os += (first ? "" : " | ");
        os += "SET_ANALOG_TIMER";
        first = false;
        flipped |= ::android::hardware::tv::cec::V1_0::CecMessageType::SET_ANALOG_TIMER;
    }
    if ((o & ::android::hardware::tv::cec::V1_0::CecMessageType::TIMER_STATUS) == static_cast<int32_t>(::android::hardware::tv::cec::V1_0::CecMessageType::TIMER_STATUS)) {
        os += (first ? "" : " | ");
        os += "TIMER_STATUS";
        first = false;
        flipped |= ::android::hardware::tv::cec::V1_0::CecMessageType::TIMER_STATUS;
    }
    if ((o & ::android::hardware::tv::cec::V1_0::CecMessageType::STANDBY) == static_cast<int32_t>(::android::hardware::tv::cec::V1_0::CecMessageType::STANDBY)) {
        os += (first ? "" : " | ");
        os += "STANDBY";
        first = false;
        flipped |= ::android::hardware::tv::cec::V1_0::CecMessageType::STANDBY;
    }
    if ((o & ::android::hardware::tv::cec::V1_0::CecMessageType::PLAY) == static_cast<int32_t>(::android::hardware::tv::cec::V1_0::CecMessageType::PLAY)) {
        os += (first ? "" : " | ");
        os += "PLAY";
        first = false;
        flipped |= ::android::hardware::tv::cec::V1_0::CecMessageType::PLAY;
    }
    if ((o & ::android::hardware::tv::cec::V1_0::CecMessageType::DECK_CONTROL) == static_cast<int32_t>(::android::hardware::tv::cec::V1_0::CecMessageType::DECK_CONTROL)) {
        os += (first ? "" : " | ");
        os += "DECK_CONTROL";
        first = false;
        flipped |= ::android::hardware::tv::cec::V1_0::CecMessageType::DECK_CONTROL;
    }
    if ((o & ::android::hardware::tv::cec::V1_0::CecMessageType::TIMER_CLEARED_STATUS) == static_cast<int32_t>(::android::hardware::tv::cec::V1_0::CecMessageType::TIMER_CLEARED_STATUS)) {
        os += (first ? "" : " | ");
        os += "TIMER_CLEARED_STATUS";
        first = false;
        flipped |= ::android::hardware::tv::cec::V1_0::CecMessageType::TIMER_CLEARED_STATUS;
    }
    if ((o & ::android::hardware::tv::cec::V1_0::CecMessageType::USER_CONTROL_PRESSED) == static_cast<int32_t>(::android::hardware::tv::cec::V1_0::CecMessageType::USER_CONTROL_PRESSED)) {
        os += (first ? "" : " | ");
        os += "USER_CONTROL_PRESSED";
        first = false;
        flipped |= ::android::hardware::tv::cec::V1_0::CecMessageType::USER_CONTROL_PRESSED;
    }
    if ((o & ::android::hardware::tv::cec::V1_0::CecMessageType::USER_CONTROL_RELEASED) == static_cast<int32_t>(::android::hardware::tv::cec::V1_0::CecMessageType::USER_CONTROL_RELEASED)) {
        os += (first ? "" : " | ");
        os += "USER_CONTROL_RELEASED";
        first = false;
        flipped |= ::android::hardware::tv::cec::V1_0::CecMessageType::USER_CONTROL_RELEASED;
    }
    if ((o & ::android::hardware::tv::cec::V1_0::CecMessageType::GIVE_OSD_NAME) == static_cast<int32_t>(::android::hardware::tv::cec::V1_0::CecMessageType::GIVE_OSD_NAME)) {
        os += (first ? "" : " | ");
        os += "GIVE_OSD_NAME";
        first = false;
        flipped |= ::android::hardware::tv::cec::V1_0::CecMessageType::GIVE_OSD_NAME;
    }
    if ((o & ::android::hardware::tv::cec::V1_0::CecMessageType::SET_OSD_NAME) == static_cast<int32_t>(::android::hardware::tv::cec::V1_0::CecMessageType::SET_OSD_NAME)) {
        os += (first ? "" : " | ");
        os += "SET_OSD_NAME";
        first = false;
        flipped |= ::android::hardware::tv::cec::V1_0::CecMessageType::SET_OSD_NAME;
    }
    if ((o & ::android::hardware::tv::cec::V1_0::CecMessageType::SET_OSD_STRING) == static_cast<int32_t>(::android::hardware::tv::cec::V1_0::CecMessageType::SET_OSD_STRING)) {
        os += (first ? "" : " | ");
        os += "SET_OSD_STRING";
        first = false;
        flipped |= ::android::hardware::tv::cec::V1_0::CecMessageType::SET_OSD_STRING;
    }
    if ((o & ::android::hardware::tv::cec::V1_0::CecMessageType::SET_TIMER_PROGRAM_TITLE) == static_cast<int32_t>(::android::hardware::tv::cec::V1_0::CecMessageType::SET_TIMER_PROGRAM_TITLE)) {
        os += (first ? "" : " | ");
        os += "SET_TIMER_PROGRAM_TITLE";
        first = false;
        flipped |= ::android::hardware::tv::cec::V1_0::CecMessageType::SET_TIMER_PROGRAM_TITLE;
    }
    if ((o & ::android::hardware::tv::cec::V1_0::CecMessageType::SYSTEM_AUDIO_MODE_REQUEST) == static_cast<int32_t>(::android::hardware::tv::cec::V1_0::CecMessageType::SYSTEM_AUDIO_MODE_REQUEST)) {
        os += (first ? "" : " | ");
        os += "SYSTEM_AUDIO_MODE_REQUEST";
        first = false;
        flipped |= ::android::hardware::tv::cec::V1_0::CecMessageType::SYSTEM_AUDIO_MODE_REQUEST;
    }
    if ((o & ::android::hardware::tv::cec::V1_0::CecMessageType::GIVE_AUDIO_STATUS) == static_cast<int32_t>(::android::hardware::tv::cec::V1_0::CecMessageType::GIVE_AUDIO_STATUS)) {
        os += (first ? "" : " | ");
        os += "GIVE_AUDIO_STATUS";
        first = false;
        flipped |= ::android::hardware::tv::cec::V1_0::CecMessageType::GIVE_AUDIO_STATUS;
    }
    if ((o & ::android::hardware::tv::cec::V1_0::CecMessageType::SET_SYSTEM_AUDIO_MODE) == static_cast<int32_t>(::android::hardware::tv::cec::V1_0::CecMessageType::SET_SYSTEM_AUDIO_MODE)) {
        os += (first ? "" : " | ");
        os += "SET_SYSTEM_AUDIO_MODE";
        first = false;
        flipped |= ::android::hardware::tv::cec::V1_0::CecMessageType::SET_SYSTEM_AUDIO_MODE;
    }
    if ((o & ::android::hardware::tv::cec::V1_0::CecMessageType::REPORT_AUDIO_STATUS) == static_cast<int32_t>(::android::hardware::tv::cec::V1_0::CecMessageType::REPORT_AUDIO_STATUS)) {
        os += (first ? "" : " | ");
        os += "REPORT_AUDIO_STATUS";
        first = false;
        flipped |= ::android::hardware::tv::cec::V1_0::CecMessageType::REPORT_AUDIO_STATUS;
    }
    if ((o & ::android::hardware::tv::cec::V1_0::CecMessageType::GIVE_SYSTEM_AUDIO_MODE_STATUS) == static_cast<int32_t>(::android::hardware::tv::cec::V1_0::CecMessageType::GIVE_SYSTEM_AUDIO_MODE_STATUS)) {
        os += (first ? "" : " | ");
        os += "GIVE_SYSTEM_AUDIO_MODE_STATUS";
        first = false;
        flipped |= ::android::hardware::tv::cec::V1_0::CecMessageType::GIVE_SYSTEM_AUDIO_MODE_STATUS;
    }
    if ((o & ::android::hardware::tv::cec::V1_0::CecMessageType::SYSTEM_AUDIO_MODE_STATUS) == static_cast<int32_t>(::android::hardware::tv::cec::V1_0::CecMessageType::SYSTEM_AUDIO_MODE_STATUS)) {
        os += (first ? "" : " | ");
        os += "SYSTEM_AUDIO_MODE_STATUS";
        first = false;
        flipped |= ::android::hardware::tv::cec::V1_0::CecMessageType::SYSTEM_AUDIO_MODE_STATUS;
    }
    if ((o & ::android::hardware::tv::cec::V1_0::CecMessageType::ROUTING_CHANGE) == static_cast<int32_t>(::android::hardware::tv::cec::V1_0::CecMessageType::ROUTING_CHANGE)) {
        os += (first ? "" : " | ");
        os += "ROUTING_CHANGE";
        first = false;
        flipped |= ::android::hardware::tv::cec::V1_0::CecMessageType::ROUTING_CHANGE;
    }
    if ((o & ::android::hardware::tv::cec::V1_0::CecMessageType::ROUTING_INFORMATION) == static_cast<int32_t>(::android::hardware::tv::cec::V1_0::CecMessageType::ROUTING_INFORMATION)) {
        os += (first ? "" : " | ");
        os += "ROUTING_INFORMATION";
        first = false;
        flipped |= ::android::hardware::tv::cec::V1_0::CecMessageType::ROUTING_INFORMATION;
    }
    if ((o & ::android::hardware::tv::cec::V1_0::CecMessageType::ACTIVE_SOURCE) == static_cast<int32_t>(::android::hardware::tv::cec::V1_0::CecMessageType::ACTIVE_SOURCE)) {
        os += (first ? "" : " | ");
        os += "ACTIVE_SOURCE";
        first = false;
        flipped |= ::android::hardware::tv::cec::V1_0::CecMessageType::ACTIVE_SOURCE;
    }
    if ((o & ::android::hardware::tv::cec::V1_0::CecMessageType::GIVE_PHYSICAL_ADDRESS) == static_cast<int32_t>(::android::hardware::tv::cec::V1_0::CecMessageType::GIVE_PHYSICAL_ADDRESS)) {
        os += (first ? "" : " | ");
        os += "GIVE_PHYSICAL_ADDRESS";
        first = false;
        flipped |= ::android::hardware::tv::cec::V1_0::CecMessageType::GIVE_PHYSICAL_ADDRESS;
    }
    if ((o & ::android::hardware::tv::cec::V1_0::CecMessageType::REPORT_PHYSICAL_ADDRESS) == static_cast<int32_t>(::android::hardware::tv::cec::V1_0::CecMessageType::REPORT_PHYSICAL_ADDRESS)) {
        os += (first ? "" : " | ");
        os += "REPORT_PHYSICAL_ADDRESS";
        first = false;
        flipped |= ::android::hardware::tv::cec::V1_0::CecMessageType::REPORT_PHYSICAL_ADDRESS;
    }
    if ((o & ::android::hardware::tv::cec::V1_0::CecMessageType::REQUEST_ACTIVE_SOURCE) == static_cast<int32_t>(::android::hardware::tv::cec::V1_0::CecMessageType::REQUEST_ACTIVE_SOURCE)) {
        os += (first ? "" : " | ");
        os += "REQUEST_ACTIVE_SOURCE";
        first = false;
        flipped |= ::android::hardware::tv::cec::V1_0::CecMessageType::REQUEST_ACTIVE_SOURCE;
    }
    if ((o & ::android::hardware::tv::cec::V1_0::CecMessageType::SET_STREAM_PATH) == static_cast<int32_t>(::android::hardware::tv::cec::V1_0::CecMessageType::SET_STREAM_PATH)) {
        os += (first ? "" : " | ");
        os += "SET_STREAM_PATH";
        first = false;
        flipped |= ::android::hardware::tv::cec::V1_0::CecMessageType::SET_STREAM_PATH;
    }
    if ((o & ::android::hardware::tv::cec::V1_0::CecMessageType::DEVICE_VENDOR_ID) == static_cast<int32_t>(::android::hardware::tv::cec::V1_0::CecMessageType::DEVICE_VENDOR_ID)) {
        os += (first ? "" : " | ");
        os += "DEVICE_VENDOR_ID";
        first = false;
        flipped |= ::android::hardware::tv::cec::V1_0::CecMessageType::DEVICE_VENDOR_ID;
    }
    if ((o & ::android::hardware::tv::cec::V1_0::CecMessageType::VENDOR_COMMAND) == static_cast<int32_t>(::android::hardware::tv::cec::V1_0::CecMessageType::VENDOR_COMMAND)) {
        os += (first ? "" : " | ");
        os += "VENDOR_COMMAND";
        first = false;
        flipped |= ::android::hardware::tv::cec::V1_0::CecMessageType::VENDOR_COMMAND;
    }
    if ((o & ::android::hardware::tv::cec::V1_0::CecMessageType::VENDOR_REMOTE_BUTTON_DOWN) == static_cast<int32_t>(::android::hardware::tv::cec::V1_0::CecMessageType::VENDOR_REMOTE_BUTTON_DOWN)) {
        os += (first ? "" : " | ");
        os += "VENDOR_REMOTE_BUTTON_DOWN";
        first = false;
        flipped |= ::android::hardware::tv::cec::V1_0::CecMessageType::VENDOR_REMOTE_BUTTON_DOWN;
    }
    if ((o & ::android::hardware::tv::cec::V1_0::CecMessageType::VENDOR_REMOTE_BUTTON_UP) == static_cast<int32_t>(::android::hardware::tv::cec::V1_0::CecMessageType::VENDOR_REMOTE_BUTTON_UP)) {
        os += (first ? "" : " | ");
        os += "VENDOR_REMOTE_BUTTON_UP";
        first = false;
        flipped |= ::android::hardware::tv::cec::V1_0::CecMessageType::VENDOR_REMOTE_BUTTON_UP;
    }
    if ((o & ::android::hardware::tv::cec::V1_0::CecMessageType::GIVE_DEVICE_VENDOR_ID) == static_cast<int32_t>(::android::hardware::tv::cec::V1_0::CecMessageType::GIVE_DEVICE_VENDOR_ID)) {
        os += (first ? "" : " | ");
        os += "GIVE_DEVICE_VENDOR_ID";
        first = false;
        flipped |= ::android::hardware::tv::cec::V1_0::CecMessageType::GIVE_DEVICE_VENDOR_ID;
    }
    if ((o & ::android::hardware::tv::cec::V1_0::CecMessageType::MENU_REQUEST) == static_cast<int32_t>(::android::hardware::tv::cec::V1_0::CecMessageType::MENU_REQUEST)) {
        os += (first ? "" : " | ");
        os += "MENU_REQUEST";
        first = false;
        flipped |= ::android::hardware::tv::cec::V1_0::CecMessageType::MENU_REQUEST;
    }
    if ((o & ::android::hardware::tv::cec::V1_0::CecMessageType::MENU_STATUS) == static_cast<int32_t>(::android::hardware::tv::cec::V1_0::CecMessageType::MENU_STATUS)) {
        os += (first ? "" : " | ");
        os += "MENU_STATUS";
        first = false;
        flipped |= ::android::hardware::tv::cec::V1_0::CecMessageType::MENU_STATUS;
    }
    if ((o & ::android::hardware::tv::cec::V1_0::CecMessageType::GIVE_DEVICE_POWER_STATUS) == static_cast<int32_t>(::android::hardware::tv::cec::V1_0::CecMessageType::GIVE_DEVICE_POWER_STATUS)) {
        os += (first ? "" : " | ");
        os += "GIVE_DEVICE_POWER_STATUS";
        first = false;
        flipped |= ::android::hardware::tv::cec::V1_0::CecMessageType::GIVE_DEVICE_POWER_STATUS;
    }
    if ((o & ::android::hardware::tv::cec::V1_0::CecMessageType::REPORT_POWER_STATUS) == static_cast<int32_t>(::android::hardware::tv::cec::V1_0::CecMessageType::REPORT_POWER_STATUS)) {
        os += (first ? "" : " | ");
        os += "REPORT_POWER_STATUS";
        first = false;
        flipped |= ::android::hardware::tv::cec::V1_0::CecMessageType::REPORT_POWER_STATUS;
    }
    if ((o & ::android::hardware::tv::cec::V1_0::CecMessageType::GET_MENU_LANGUAGE) == static_cast<int32_t>(::android::hardware::tv::cec::V1_0::CecMessageType::GET_MENU_LANGUAGE)) {
        os += (first ? "" : " | ");
        os += "GET_MENU_LANGUAGE";
        first = false;
        flipped |= ::android::hardware::tv::cec::V1_0::CecMessageType::GET_MENU_LANGUAGE;
    }
    if ((o & ::android::hardware::tv::cec::V1_0::CecMessageType::SELECT_ANALOG_SERVICE) == static_cast<int32_t>(::android::hardware::tv::cec::V1_0::CecMessageType::SELECT_ANALOG_SERVICE)) {
        os += (first ? "" : " | ");
        os += "SELECT_ANALOG_SERVICE";
        first = false;
        flipped |= ::android::hardware::tv::cec::V1_0::CecMessageType::SELECT_ANALOG_SERVICE;
    }
    if ((o & ::android::hardware::tv::cec::V1_0::CecMessageType::SELECT_DIGITAL_SERVICE) == static_cast<int32_t>(::android::hardware::tv::cec::V1_0::CecMessageType::SELECT_DIGITAL_SERVICE)) {
        os += (first ? "" : " | ");
        os += "SELECT_DIGITAL_SERVICE";
        first = false;
        flipped |= ::android::hardware::tv::cec::V1_0::CecMessageType::SELECT_DIGITAL_SERVICE;
    }
    if ((o & ::android::hardware::tv::cec::V1_0::CecMessageType::SET_DIGITAL_TIMER) == static_cast<int32_t>(::android::hardware::tv::cec::V1_0::CecMessageType::SET_DIGITAL_TIMER)) {
        os += (first ? "" : " | ");
        os += "SET_DIGITAL_TIMER";
        first = false;
        flipped |= ::android::hardware::tv::cec::V1_0::CecMessageType::SET_DIGITAL_TIMER;
    }
    if ((o & ::android::hardware::tv::cec::V1_0::CecMessageType::CLEAR_DIGITAL_TIMER) == static_cast<int32_t>(::android::hardware::tv::cec::V1_0::CecMessageType::CLEAR_DIGITAL_TIMER)) {
        os += (first ? "" : " | ");
        os += "CLEAR_DIGITAL_TIMER";
        first = false;
        flipped |= ::android::hardware::tv::cec::V1_0::CecMessageType::CLEAR_DIGITAL_TIMER;
    }
    if ((o & ::android::hardware::tv::cec::V1_0::CecMessageType::SET_AUDIO_RATE) == static_cast<int32_t>(::android::hardware::tv::cec::V1_0::CecMessageType::SET_AUDIO_RATE)) {
        os += (first ? "" : " | ");
        os += "SET_AUDIO_RATE";
        first = false;
        flipped |= ::android::hardware::tv::cec::V1_0::CecMessageType::SET_AUDIO_RATE;
    }
    if ((o & ::android::hardware::tv::cec::V1_0::CecMessageType::INACTIVE_SOURCE) == static_cast<int32_t>(::android::hardware::tv::cec::V1_0::CecMessageType::INACTIVE_SOURCE)) {
        os += (first ? "" : " | ");
        os += "INACTIVE_SOURCE";
        first = false;
        flipped |= ::android::hardware::tv::cec::V1_0::CecMessageType::INACTIVE_SOURCE;
    }
    if ((o & ::android::hardware::tv::cec::V1_0::CecMessageType::CEC_VERSION) == static_cast<int32_t>(::android::hardware::tv::cec::V1_0::CecMessageType::CEC_VERSION)) {
        os += (first ? "" : " | ");
        os += "CEC_VERSION";
        first = false;
        flipped |= ::android::hardware::tv::cec::V1_0::CecMessageType::CEC_VERSION;
    }
    if ((o & ::android::hardware::tv::cec::V1_0::CecMessageType::GET_CEC_VERSION) == static_cast<int32_t>(::android::hardware::tv::cec::V1_0::CecMessageType::GET_CEC_VERSION)) {
        os += (first ? "" : " | ");
        os += "GET_CEC_VERSION";
        first = false;
        flipped |= ::android::hardware::tv::cec::V1_0::CecMessageType::GET_CEC_VERSION;
    }
    if ((o & ::android::hardware::tv::cec::V1_0::CecMessageType::VENDOR_COMMAND_WITH_ID) == static_cast<int32_t>(::android::hardware::tv::cec::V1_0::CecMessageType::VENDOR_COMMAND_WITH_ID)) {
        os += (first ? "" : " | ");
        os += "VENDOR_COMMAND_WITH_ID";
        first = false;
        flipped |= ::android::hardware::tv::cec::V1_0::CecMessageType::VENDOR_COMMAND_WITH_ID;
    }
    if ((o & ::android::hardware::tv::cec::V1_0::CecMessageType::CLEAR_EXTERNAL_TIMER) == static_cast<int32_t>(::android::hardware::tv::cec::V1_0::CecMessageType::CLEAR_EXTERNAL_TIMER)) {
        os += (first ? "" : " | ");
        os += "CLEAR_EXTERNAL_TIMER";
        first = false;
        flipped |= ::android::hardware::tv::cec::V1_0::CecMessageType::CLEAR_EXTERNAL_TIMER;
    }
    if ((o & ::android::hardware::tv::cec::V1_0::CecMessageType::SET_EXTERNAL_TIMER) == static_cast<int32_t>(::android::hardware::tv::cec::V1_0::CecMessageType::SET_EXTERNAL_TIMER)) {
        os += (first ? "" : " | ");
        os += "SET_EXTERNAL_TIMER";
        first = false;
        flipped |= ::android::hardware::tv::cec::V1_0::CecMessageType::SET_EXTERNAL_TIMER;
    }
    if ((o & ::android::hardware::tv::cec::V1_0::CecMessageType::REPORT_SHORT_AUDIO_DESCRIPTOR) == static_cast<int32_t>(::android::hardware::tv::cec::V1_0::CecMessageType::REPORT_SHORT_AUDIO_DESCRIPTOR)) {
        os += (first ? "" : " | ");
        os += "REPORT_SHORT_AUDIO_DESCRIPTOR";
        first = false;
        flipped |= ::android::hardware::tv::cec::V1_0::CecMessageType::REPORT_SHORT_AUDIO_DESCRIPTOR;
    }
    if ((o & ::android::hardware::tv::cec::V1_0::CecMessageType::REQUEST_SHORT_AUDIO_DESCRIPTOR) == static_cast<int32_t>(::android::hardware::tv::cec::V1_0::CecMessageType::REQUEST_SHORT_AUDIO_DESCRIPTOR)) {
        os += (first ? "" : " | ");
        os += "REQUEST_SHORT_AUDIO_DESCRIPTOR";
        first = false;
        flipped |= ::android::hardware::tv::cec::V1_0::CecMessageType::REQUEST_SHORT_AUDIO_DESCRIPTOR;
    }
    if ((o & ::android::hardware::tv::cec::V1_0::CecMessageType::INITIATE_ARC) == static_cast<int32_t>(::android::hardware::tv::cec::V1_0::CecMessageType::INITIATE_ARC)) {
        os += (first ? "" : " | ");
        os += "INITIATE_ARC";
        first = false;
        flipped |= ::android::hardware::tv::cec::V1_0::CecMessageType::INITIATE_ARC;
    }
    if ((o & ::android::hardware::tv::cec::V1_0::CecMessageType::REPORT_ARC_INITIATED) == static_cast<int32_t>(::android::hardware::tv::cec::V1_0::CecMessageType::REPORT_ARC_INITIATED)) {
        os += (first ? "" : " | ");
        os += "REPORT_ARC_INITIATED";
        first = false;
        flipped |= ::android::hardware::tv::cec::V1_0::CecMessageType::REPORT_ARC_INITIATED;
    }
    if ((o & ::android::hardware::tv::cec::V1_0::CecMessageType::REPORT_ARC_TERMINATED) == static_cast<int32_t>(::android::hardware::tv::cec::V1_0::CecMessageType::REPORT_ARC_TERMINATED)) {
        os += (first ? "" : " | ");
        os += "REPORT_ARC_TERMINATED";
        first = false;
        flipped |= ::android::hardware::tv::cec::V1_0::CecMessageType::REPORT_ARC_TERMINATED;
    }
    if ((o & ::android::hardware::tv::cec::V1_0::CecMessageType::REQUEST_ARC_INITIATION) == static_cast<int32_t>(::android::hardware::tv::cec::V1_0::CecMessageType::REQUEST_ARC_INITIATION)) {
        os += (first ? "" : " | ");
        os += "REQUEST_ARC_INITIATION";
        first = false;
        flipped |= ::android::hardware::tv::cec::V1_0::CecMessageType::REQUEST_ARC_INITIATION;
    }
    if ((o & ::android::hardware::tv::cec::V1_0::CecMessageType::REQUEST_ARC_TERMINATION) == static_cast<int32_t>(::android::hardware::tv::cec::V1_0::CecMessageType::REQUEST_ARC_TERMINATION)) {
        os += (first ? "" : " | ");
        os += "REQUEST_ARC_TERMINATION";
        first = false;
        flipped |= ::android::hardware::tv::cec::V1_0::CecMessageType::REQUEST_ARC_TERMINATION;
    }
    if ((o & ::android::hardware::tv::cec::V1_0::CecMessageType::TERMINATE_ARC) == static_cast<int32_t>(::android::hardware::tv::cec::V1_0::CecMessageType::TERMINATE_ARC)) {
        os += (first ? "" : " | ");
        os += "TERMINATE_ARC";
        first = false;
        flipped |= ::android::hardware::tv::cec::V1_0::CecMessageType::TERMINATE_ARC;
    }
    if ((o & ::android::hardware::tv::cec::V1_0::CecMessageType::ABORT) == static_cast<int32_t>(::android::hardware::tv::cec::V1_0::CecMessageType::ABORT)) {
        os += (first ? "" : " | ");
        os += "ABORT";
        first = false;
        flipped |= ::android::hardware::tv::cec::V1_0::CecMessageType::ABORT;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::tv::cec::V1_0::CecMessageType o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::tv::cec::V1_0::CecMessageType::FEATURE_ABORT) {
        return "FEATURE_ABORT";
    }
    if (o == ::android::hardware::tv::cec::V1_0::CecMessageType::IMAGE_VIEW_ON) {
        return "IMAGE_VIEW_ON";
    }
    if (o == ::android::hardware::tv::cec::V1_0::CecMessageType::TUNER_STEP_INCREMENT) {
        return "TUNER_STEP_INCREMENT";
    }
    if (o == ::android::hardware::tv::cec::V1_0::CecMessageType::TUNER_STEP_DECREMENT) {
        return "TUNER_STEP_DECREMENT";
    }
    if (o == ::android::hardware::tv::cec::V1_0::CecMessageType::TUNER_DEVICE_STATUS) {
        return "TUNER_DEVICE_STATUS";
    }
    if (o == ::android::hardware::tv::cec::V1_0::CecMessageType::GIVE_TUNER_DEVICE_STATUS) {
        return "GIVE_TUNER_DEVICE_STATUS";
    }
    if (o == ::android::hardware::tv::cec::V1_0::CecMessageType::RECORD_ON) {
        return "RECORD_ON";
    }
    if (o == ::android::hardware::tv::cec::V1_0::CecMessageType::RECORD_STATUS) {
        return "RECORD_STATUS";
    }
    if (o == ::android::hardware::tv::cec::V1_0::CecMessageType::RECORD_OFF) {
        return "RECORD_OFF";
    }
    if (o == ::android::hardware::tv::cec::V1_0::CecMessageType::TEXT_VIEW_ON) {
        return "TEXT_VIEW_ON";
    }
    if (o == ::android::hardware::tv::cec::V1_0::CecMessageType::RECORD_TV_SCREEN) {
        return "RECORD_TV_SCREEN";
    }
    if (o == ::android::hardware::tv::cec::V1_0::CecMessageType::GIVE_DECK_STATUS) {
        return "GIVE_DECK_STATUS";
    }
    if (o == ::android::hardware::tv::cec::V1_0::CecMessageType::DECK_STATUS) {
        return "DECK_STATUS";
    }
    if (o == ::android::hardware::tv::cec::V1_0::CecMessageType::SET_MENU_LANGUAGE) {
        return "SET_MENU_LANGUAGE";
    }
    if (o == ::android::hardware::tv::cec::V1_0::CecMessageType::CLEAR_ANALOG_TIMER) {
        return "CLEAR_ANALOG_TIMER";
    }
    if (o == ::android::hardware::tv::cec::V1_0::CecMessageType::SET_ANALOG_TIMER) {
        return "SET_ANALOG_TIMER";
    }
    if (o == ::android::hardware::tv::cec::V1_0::CecMessageType::TIMER_STATUS) {
        return "TIMER_STATUS";
    }
    if (o == ::android::hardware::tv::cec::V1_0::CecMessageType::STANDBY) {
        return "STANDBY";
    }
    if (o == ::android::hardware::tv::cec::V1_0::CecMessageType::PLAY) {
        return "PLAY";
    }
    if (o == ::android::hardware::tv::cec::V1_0::CecMessageType::DECK_CONTROL) {
        return "DECK_CONTROL";
    }
    if (o == ::android::hardware::tv::cec::V1_0::CecMessageType::TIMER_CLEARED_STATUS) {
        return "TIMER_CLEARED_STATUS";
    }
    if (o == ::android::hardware::tv::cec::V1_0::CecMessageType::USER_CONTROL_PRESSED) {
        return "USER_CONTROL_PRESSED";
    }
    if (o == ::android::hardware::tv::cec::V1_0::CecMessageType::USER_CONTROL_RELEASED) {
        return "USER_CONTROL_RELEASED";
    }
    if (o == ::android::hardware::tv::cec::V1_0::CecMessageType::GIVE_OSD_NAME) {
        return "GIVE_OSD_NAME";
    }
    if (o == ::android::hardware::tv::cec::V1_0::CecMessageType::SET_OSD_NAME) {
        return "SET_OSD_NAME";
    }
    if (o == ::android::hardware::tv::cec::V1_0::CecMessageType::SET_OSD_STRING) {
        return "SET_OSD_STRING";
    }
    if (o == ::android::hardware::tv::cec::V1_0::CecMessageType::SET_TIMER_PROGRAM_TITLE) {
        return "SET_TIMER_PROGRAM_TITLE";
    }
    if (o == ::android::hardware::tv::cec::V1_0::CecMessageType::SYSTEM_AUDIO_MODE_REQUEST) {
        return "SYSTEM_AUDIO_MODE_REQUEST";
    }
    if (o == ::android::hardware::tv::cec::V1_0::CecMessageType::GIVE_AUDIO_STATUS) {
        return "GIVE_AUDIO_STATUS";
    }
    if (o == ::android::hardware::tv::cec::V1_0::CecMessageType::SET_SYSTEM_AUDIO_MODE) {
        return "SET_SYSTEM_AUDIO_MODE";
    }
    if (o == ::android::hardware::tv::cec::V1_0::CecMessageType::REPORT_AUDIO_STATUS) {
        return "REPORT_AUDIO_STATUS";
    }
    if (o == ::android::hardware::tv::cec::V1_0::CecMessageType::GIVE_SYSTEM_AUDIO_MODE_STATUS) {
        return "GIVE_SYSTEM_AUDIO_MODE_STATUS";
    }
    if (o == ::android::hardware::tv::cec::V1_0::CecMessageType::SYSTEM_AUDIO_MODE_STATUS) {
        return "SYSTEM_AUDIO_MODE_STATUS";
    }
    if (o == ::android::hardware::tv::cec::V1_0::CecMessageType::ROUTING_CHANGE) {
        return "ROUTING_CHANGE";
    }
    if (o == ::android::hardware::tv::cec::V1_0::CecMessageType::ROUTING_INFORMATION) {
        return "ROUTING_INFORMATION";
    }
    if (o == ::android::hardware::tv::cec::V1_0::CecMessageType::ACTIVE_SOURCE) {
        return "ACTIVE_SOURCE";
    }
    if (o == ::android::hardware::tv::cec::V1_0::CecMessageType::GIVE_PHYSICAL_ADDRESS) {
        return "GIVE_PHYSICAL_ADDRESS";
    }
    if (o == ::android::hardware::tv::cec::V1_0::CecMessageType::REPORT_PHYSICAL_ADDRESS) {
        return "REPORT_PHYSICAL_ADDRESS";
    }
    if (o == ::android::hardware::tv::cec::V1_0::CecMessageType::REQUEST_ACTIVE_SOURCE) {
        return "REQUEST_ACTIVE_SOURCE";
    }
    if (o == ::android::hardware::tv::cec::V1_0::CecMessageType::SET_STREAM_PATH) {
        return "SET_STREAM_PATH";
    }
    if (o == ::android::hardware::tv::cec::V1_0::CecMessageType::DEVICE_VENDOR_ID) {
        return "DEVICE_VENDOR_ID";
    }
    if (o == ::android::hardware::tv::cec::V1_0::CecMessageType::VENDOR_COMMAND) {
        return "VENDOR_COMMAND";
    }
    if (o == ::android::hardware::tv::cec::V1_0::CecMessageType::VENDOR_REMOTE_BUTTON_DOWN) {
        return "VENDOR_REMOTE_BUTTON_DOWN";
    }
    if (o == ::android::hardware::tv::cec::V1_0::CecMessageType::VENDOR_REMOTE_BUTTON_UP) {
        return "VENDOR_REMOTE_BUTTON_UP";
    }
    if (o == ::android::hardware::tv::cec::V1_0::CecMessageType::GIVE_DEVICE_VENDOR_ID) {
        return "GIVE_DEVICE_VENDOR_ID";
    }
    if (o == ::android::hardware::tv::cec::V1_0::CecMessageType::MENU_REQUEST) {
        return "MENU_REQUEST";
    }
    if (o == ::android::hardware::tv::cec::V1_0::CecMessageType::MENU_STATUS) {
        return "MENU_STATUS";
    }
    if (o == ::android::hardware::tv::cec::V1_0::CecMessageType::GIVE_DEVICE_POWER_STATUS) {
        return "GIVE_DEVICE_POWER_STATUS";
    }
    if (o == ::android::hardware::tv::cec::V1_0::CecMessageType::REPORT_POWER_STATUS) {
        return "REPORT_POWER_STATUS";
    }
    if (o == ::android::hardware::tv::cec::V1_0::CecMessageType::GET_MENU_LANGUAGE) {
        return "GET_MENU_LANGUAGE";
    }
    if (o == ::android::hardware::tv::cec::V1_0::CecMessageType::SELECT_ANALOG_SERVICE) {
        return "SELECT_ANALOG_SERVICE";
    }
    if (o == ::android::hardware::tv::cec::V1_0::CecMessageType::SELECT_DIGITAL_SERVICE) {
        return "SELECT_DIGITAL_SERVICE";
    }
    if (o == ::android::hardware::tv::cec::V1_0::CecMessageType::SET_DIGITAL_TIMER) {
        return "SET_DIGITAL_TIMER";
    }
    if (o == ::android::hardware::tv::cec::V1_0::CecMessageType::CLEAR_DIGITAL_TIMER) {
        return "CLEAR_DIGITAL_TIMER";
    }
    if (o == ::android::hardware::tv::cec::V1_0::CecMessageType::SET_AUDIO_RATE) {
        return "SET_AUDIO_RATE";
    }
    if (o == ::android::hardware::tv::cec::V1_0::CecMessageType::INACTIVE_SOURCE) {
        return "INACTIVE_SOURCE";
    }
    if (o == ::android::hardware::tv::cec::V1_0::CecMessageType::CEC_VERSION) {
        return "CEC_VERSION";
    }
    if (o == ::android::hardware::tv::cec::V1_0::CecMessageType::GET_CEC_VERSION) {
        return "GET_CEC_VERSION";
    }
    if (o == ::android::hardware::tv::cec::V1_0::CecMessageType::VENDOR_COMMAND_WITH_ID) {
        return "VENDOR_COMMAND_WITH_ID";
    }
    if (o == ::android::hardware::tv::cec::V1_0::CecMessageType::CLEAR_EXTERNAL_TIMER) {
        return "CLEAR_EXTERNAL_TIMER";
    }
    if (o == ::android::hardware::tv::cec::V1_0::CecMessageType::SET_EXTERNAL_TIMER) {
        return "SET_EXTERNAL_TIMER";
    }
    if (o == ::android::hardware::tv::cec::V1_0::CecMessageType::REPORT_SHORT_AUDIO_DESCRIPTOR) {
        return "REPORT_SHORT_AUDIO_DESCRIPTOR";
    }
    if (o == ::android::hardware::tv::cec::V1_0::CecMessageType::REQUEST_SHORT_AUDIO_DESCRIPTOR) {
        return "REQUEST_SHORT_AUDIO_DESCRIPTOR";
    }
    if (o == ::android::hardware::tv::cec::V1_0::CecMessageType::INITIATE_ARC) {
        return "INITIATE_ARC";
    }
    if (o == ::android::hardware::tv::cec::V1_0::CecMessageType::REPORT_ARC_INITIATED) {
        return "REPORT_ARC_INITIATED";
    }
    if (o == ::android::hardware::tv::cec::V1_0::CecMessageType::REPORT_ARC_TERMINATED) {
        return "REPORT_ARC_TERMINATED";
    }
    if (o == ::android::hardware::tv::cec::V1_0::CecMessageType::REQUEST_ARC_INITIATION) {
        return "REQUEST_ARC_INITIATION";
    }
    if (o == ::android::hardware::tv::cec::V1_0::CecMessageType::REQUEST_ARC_TERMINATION) {
        return "REQUEST_ARC_TERMINATION";
    }
    if (o == ::android::hardware::tv::cec::V1_0::CecMessageType::TERMINATE_ARC) {
        return "TERMINATE_ARC";
    }
    if (o == ::android::hardware::tv::cec::V1_0::CecMessageType::ABORT) {
        return "ABORT";
    }
    std::string os;
    os += toHexString(static_cast<int32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::tv::cec::V1_0::CecMessageType o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::tv::cec::V1_0::AbortReason>(int32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::tv::cec::V1_0::AbortReason> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::tv::cec::V1_0::AbortReason::UNRECOGNIZED_MODE) == static_cast<int32_t>(::android::hardware::tv::cec::V1_0::AbortReason::UNRECOGNIZED_MODE)) {
        os += (first ? "" : " | ");
        os += "UNRECOGNIZED_MODE";
        first = false;
        flipped |= ::android::hardware::tv::cec::V1_0::AbortReason::UNRECOGNIZED_MODE;
    }
    if ((o & ::android::hardware::tv::cec::V1_0::AbortReason::NOT_IN_CORRECT_MODE) == static_cast<int32_t>(::android::hardware::tv::cec::V1_0::AbortReason::NOT_IN_CORRECT_MODE)) {
        os += (first ? "" : " | ");
        os += "NOT_IN_CORRECT_MODE";
        first = false;
        flipped |= ::android::hardware::tv::cec::V1_0::AbortReason::NOT_IN_CORRECT_MODE;
    }
    if ((o & ::android::hardware::tv::cec::V1_0::AbortReason::CANNOT_PROVIDE_SOURCE) == static_cast<int32_t>(::android::hardware::tv::cec::V1_0::AbortReason::CANNOT_PROVIDE_SOURCE)) {
        os += (first ? "" : " | ");
        os += "CANNOT_PROVIDE_SOURCE";
        first = false;
        flipped |= ::android::hardware::tv::cec::V1_0::AbortReason::CANNOT_PROVIDE_SOURCE;
    }
    if ((o & ::android::hardware::tv::cec::V1_0::AbortReason::INVALID_OPERAND) == static_cast<int32_t>(::android::hardware::tv::cec::V1_0::AbortReason::INVALID_OPERAND)) {
        os += (first ? "" : " | ");
        os += "INVALID_OPERAND";
        first = false;
        flipped |= ::android::hardware::tv::cec::V1_0::AbortReason::INVALID_OPERAND;
    }
    if ((o & ::android::hardware::tv::cec::V1_0::AbortReason::REFUSED) == static_cast<int32_t>(::android::hardware::tv::cec::V1_0::AbortReason::REFUSED)) {
        os += (first ? "" : " | ");
        os += "REFUSED";
        first = false;
        flipped |= ::android::hardware::tv::cec::V1_0::AbortReason::REFUSED;
    }
    if ((o & ::android::hardware::tv::cec::V1_0::AbortReason::UNABLE_TO_DETERMINE) == static_cast<int32_t>(::android::hardware::tv::cec::V1_0::AbortReason::UNABLE_TO_DETERMINE)) {
        os += (first ? "" : " | ");
        os += "UNABLE_TO_DETERMINE";
        first = false;
        flipped |= ::android::hardware::tv::cec::V1_0::AbortReason::UNABLE_TO_DETERMINE;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::tv::cec::V1_0::AbortReason o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::tv::cec::V1_0::AbortReason::UNRECOGNIZED_MODE) {
        return "UNRECOGNIZED_MODE";
    }
    if (o == ::android::hardware::tv::cec::V1_0::AbortReason::NOT_IN_CORRECT_MODE) {
        return "NOT_IN_CORRECT_MODE";
    }
    if (o == ::android::hardware::tv::cec::V1_0::AbortReason::CANNOT_PROVIDE_SOURCE) {
        return "CANNOT_PROVIDE_SOURCE";
    }
    if (o == ::android::hardware::tv::cec::V1_0::AbortReason::INVALID_OPERAND) {
        return "INVALID_OPERAND";
    }
    if (o == ::android::hardware::tv::cec::V1_0::AbortReason::REFUSED) {
        return "REFUSED";
    }
    if (o == ::android::hardware::tv::cec::V1_0::AbortReason::UNABLE_TO_DETERMINE) {
        return "UNABLE_TO_DETERMINE";
    }
    std::string os;
    os += toHexString(static_cast<int32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::tv::cec::V1_0::AbortReason o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::tv::cec::V1_0::Result>(int32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::tv::cec::V1_0::Result> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::tv::cec::V1_0::Result::SUCCESS) == static_cast<int32_t>(::android::hardware::tv::cec::V1_0::Result::SUCCESS)) {
        os += (first ? "" : " | ");
        os += "SUCCESS";
        first = false;
        flipped |= ::android::hardware::tv::cec::V1_0::Result::SUCCESS;
    }
    if ((o & ::android::hardware::tv::cec::V1_0::Result::FAILURE_UNKNOWN) == static_cast<int32_t>(::android::hardware::tv::cec::V1_0::Result::FAILURE_UNKNOWN)) {
        os += (first ? "" : " | ");
        os += "FAILURE_UNKNOWN";
        first = false;
        flipped |= ::android::hardware::tv::cec::V1_0::Result::FAILURE_UNKNOWN;
    }
    if ((o & ::android::hardware::tv::cec::V1_0::Result::FAILURE_INVALID_ARGS) == static_cast<int32_t>(::android::hardware::tv::cec::V1_0::Result::FAILURE_INVALID_ARGS)) {
        os += (first ? "" : " | ");
        os += "FAILURE_INVALID_ARGS";
        first = false;
        flipped |= ::android::hardware::tv::cec::V1_0::Result::FAILURE_INVALID_ARGS;
    }
    if ((o & ::android::hardware::tv::cec::V1_0::Result::FAILURE_INVALID_STATE) == static_cast<int32_t>(::android::hardware::tv::cec::V1_0::Result::FAILURE_INVALID_STATE)) {
        os += (first ? "" : " | ");
        os += "FAILURE_INVALID_STATE";
        first = false;
        flipped |= ::android::hardware::tv::cec::V1_0::Result::FAILURE_INVALID_STATE;
    }
    if ((o & ::android::hardware::tv::cec::V1_0::Result::FAILURE_NOT_SUPPORTED) == static_cast<int32_t>(::android::hardware::tv::cec::V1_0::Result::FAILURE_NOT_SUPPORTED)) {
        os += (first ? "" : " | ");
        os += "FAILURE_NOT_SUPPORTED";
        first = false;
        flipped |= ::android::hardware::tv::cec::V1_0::Result::FAILURE_NOT_SUPPORTED;
    }
    if ((o & ::android::hardware::tv::cec::V1_0::Result::FAILURE_BUSY) == static_cast<int32_t>(::android::hardware::tv::cec::V1_0::Result::FAILURE_BUSY)) {
        os += (first ? "" : " | ");
        os += "FAILURE_BUSY";
        first = false;
        flipped |= ::android::hardware::tv::cec::V1_0::Result::FAILURE_BUSY;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::tv::cec::V1_0::Result o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::tv::cec::V1_0::Result::SUCCESS) {
        return "SUCCESS";
    }
    if (o == ::android::hardware::tv::cec::V1_0::Result::FAILURE_UNKNOWN) {
        return "FAILURE_UNKNOWN";
    }
    if (o == ::android::hardware::tv::cec::V1_0::Result::FAILURE_INVALID_ARGS) {
        return "FAILURE_INVALID_ARGS";
    }
    if (o == ::android::hardware::tv::cec::V1_0::Result::FAILURE_INVALID_STATE) {
        return "FAILURE_INVALID_STATE";
    }
    if (o == ::android::hardware::tv::cec::V1_0::Result::FAILURE_NOT_SUPPORTED) {
        return "FAILURE_NOT_SUPPORTED";
    }
    if (o == ::android::hardware::tv::cec::V1_0::Result::FAILURE_BUSY) {
        return "FAILURE_BUSY";
    }
    std::string os;
    os += toHexString(static_cast<int32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::tv::cec::V1_0::Result o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::tv::cec::V1_0::SendMessageResult>(int32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::tv::cec::V1_0::SendMessageResult> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::tv::cec::V1_0::SendMessageResult::SUCCESS) == static_cast<int32_t>(::android::hardware::tv::cec::V1_0::SendMessageResult::SUCCESS)) {
        os += (first ? "" : " | ");
        os += "SUCCESS";
        first = false;
        flipped |= ::android::hardware::tv::cec::V1_0::SendMessageResult::SUCCESS;
    }
    if ((o & ::android::hardware::tv::cec::V1_0::SendMessageResult::NACK) == static_cast<int32_t>(::android::hardware::tv::cec::V1_0::SendMessageResult::NACK)) {
        os += (first ? "" : " | ");
        os += "NACK";
        first = false;
        flipped |= ::android::hardware::tv::cec::V1_0::SendMessageResult::NACK;
    }
    if ((o & ::android::hardware::tv::cec::V1_0::SendMessageResult::BUSY) == static_cast<int32_t>(::android::hardware::tv::cec::V1_0::SendMessageResult::BUSY)) {
        os += (first ? "" : " | ");
        os += "BUSY";
        first = false;
        flipped |= ::android::hardware::tv::cec::V1_0::SendMessageResult::BUSY;
    }
    if ((o & ::android::hardware::tv::cec::V1_0::SendMessageResult::FAIL) == static_cast<int32_t>(::android::hardware::tv::cec::V1_0::SendMessageResult::FAIL)) {
        os += (first ? "" : " | ");
        os += "FAIL";
        first = false;
        flipped |= ::android::hardware::tv::cec::V1_0::SendMessageResult::FAIL;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::tv::cec::V1_0::SendMessageResult o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::tv::cec::V1_0::SendMessageResult::SUCCESS) {
        return "SUCCESS";
    }
    if (o == ::android::hardware::tv::cec::V1_0::SendMessageResult::NACK) {
        return "NACK";
    }
    if (o == ::android::hardware::tv::cec::V1_0::SendMessageResult::BUSY) {
        return "BUSY";
    }
    if (o == ::android::hardware::tv::cec::V1_0::SendMessageResult::FAIL) {
        return "FAIL";
    }
    std::string os;
    os += toHexString(static_cast<int32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::tv::cec::V1_0::SendMessageResult o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::tv::cec::V1_0::HdmiPortType>(int32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::tv::cec::V1_0::HdmiPortType> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::tv::cec::V1_0::HdmiPortType::INPUT) == static_cast<int32_t>(::android::hardware::tv::cec::V1_0::HdmiPortType::INPUT)) {
        os += (first ? "" : " | ");
        os += "INPUT";
        first = false;
        flipped |= ::android::hardware::tv::cec::V1_0::HdmiPortType::INPUT;
    }
    if ((o & ::android::hardware::tv::cec::V1_0::HdmiPortType::OUTPUT) == static_cast<int32_t>(::android::hardware::tv::cec::V1_0::HdmiPortType::OUTPUT)) {
        os += (first ? "" : " | ");
        os += "OUTPUT";
        first = false;
        flipped |= ::android::hardware::tv::cec::V1_0::HdmiPortType::OUTPUT;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::tv::cec::V1_0::HdmiPortType o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::tv::cec::V1_0::HdmiPortType::INPUT) {
        return "INPUT";
    }
    if (o == ::android::hardware::tv::cec::V1_0::HdmiPortType::OUTPUT) {
        return "OUTPUT";
    }
    std::string os;
    os += toHexString(static_cast<int32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::tv::cec::V1_0::HdmiPortType o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::tv::cec::V1_0::OptionKey>(int32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::tv::cec::V1_0::OptionKey> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::tv::cec::V1_0::OptionKey::WAKEUP) == static_cast<int32_t>(::android::hardware::tv::cec::V1_0::OptionKey::WAKEUP)) {
        os += (first ? "" : " | ");
        os += "WAKEUP";
        first = false;
        flipped |= ::android::hardware::tv::cec::V1_0::OptionKey::WAKEUP;
    }
    if ((o & ::android::hardware::tv::cec::V1_0::OptionKey::ENABLE_CEC) == static_cast<int32_t>(::android::hardware::tv::cec::V1_0::OptionKey::ENABLE_CEC)) {
        os += (first ? "" : " | ");
        os += "ENABLE_CEC";
        first = false;
        flipped |= ::android::hardware::tv::cec::V1_0::OptionKey::ENABLE_CEC;
    }
    if ((o & ::android::hardware::tv::cec::V1_0::OptionKey::SYSTEM_CEC_CONTROL) == static_cast<int32_t>(::android::hardware::tv::cec::V1_0::OptionKey::SYSTEM_CEC_CONTROL)) {
        os += (first ? "" : " | ");
        os += "SYSTEM_CEC_CONTROL";
        first = false;
        flipped |= ::android::hardware::tv::cec::V1_0::OptionKey::SYSTEM_CEC_CONTROL;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::tv::cec::V1_0::OptionKey o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::tv::cec::V1_0::OptionKey::WAKEUP) {
        return "WAKEUP";
    }
    if (o == ::android::hardware::tv::cec::V1_0::OptionKey::ENABLE_CEC) {
        return "ENABLE_CEC";
    }
    if (o == ::android::hardware::tv::cec::V1_0::OptionKey::SYSTEM_CEC_CONTROL) {
        return "SYSTEM_CEC_CONTROL";
    }
    std::string os;
    os += toHexString(static_cast<int32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::tv::cec::V1_0::OptionKey o, ::std::ostream* os) {
    *os << toString(o);
}

static inline std::string toString(const ::android::hardware::tv::cec::V1_0::CecMessage& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".initiator = ";
    os += ::android::hardware::tv::cec::V1_0::toString(o.initiator);
    os += ", .destination = ";
    os += ::android::hardware::tv::cec::V1_0::toString(o.destination);
    os += ", .body = ";
    os += ::android::hardware::toString(o.body);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::tv::cec::V1_0::CecMessage& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::tv::cec::V1_0::CecMessage& lhs, const ::android::hardware::tv::cec::V1_0::CecMessage& rhs) {
    if (lhs.initiator != rhs.initiator) {
        return false;
    }
    if (lhs.destination != rhs.destination) {
        return false;
    }
    if (lhs.body != rhs.body) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::tv::cec::V1_0::CecMessage& lhs, const ::android::hardware::tv::cec::V1_0::CecMessage& rhs){
    return !(lhs == rhs);
}

static inline std::string toString(const ::android::hardware::tv::cec::V1_0::HotplugEvent& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".connected = ";
    os += ::android::hardware::toString(o.connected);
    os += ", .portId = ";
    os += ::android::hardware::toString(o.portId);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::tv::cec::V1_0::HotplugEvent& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::tv::cec::V1_0::HotplugEvent& lhs, const ::android::hardware::tv::cec::V1_0::HotplugEvent& rhs) {
    if (lhs.connected != rhs.connected) {
        return false;
    }
    if (lhs.portId != rhs.portId) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::tv::cec::V1_0::HotplugEvent& lhs, const ::android::hardware::tv::cec::V1_0::HotplugEvent& rhs){
    return !(lhs == rhs);
}

static inline std::string toString(const ::android::hardware::tv::cec::V1_0::HdmiPortInfo& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".type = ";
    os += ::android::hardware::tv::cec::V1_0::toString(o.type);
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

static inline void PrintTo(const ::android::hardware::tv::cec::V1_0::HdmiPortInfo& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::tv::cec::V1_0::HdmiPortInfo& lhs, const ::android::hardware::tv::cec::V1_0::HdmiPortInfo& rhs) {
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

static inline bool operator!=(const ::android::hardware::tv::cec::V1_0::HdmiPortInfo& lhs, const ::android::hardware::tv::cec::V1_0::HdmiPortInfo& rhs){
    return !(lhs == rhs);
}


}  // namespace V1_0
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
template<> inline constexpr std::array<::android::hardware::tv::cec::V1_0::MaxLength, 1> hidl_enum_values<::android::hardware::tv::cec::V1_0::MaxLength> = {
    ::android::hardware::tv::cec::V1_0::MaxLength::MESSAGE_BODY,
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
template<> inline constexpr std::array<::android::hardware::tv::cec::V1_0::CecDeviceType, 7> hidl_enum_values<::android::hardware::tv::cec::V1_0::CecDeviceType> = {
    ::android::hardware::tv::cec::V1_0::CecDeviceType::INACTIVE,
    ::android::hardware::tv::cec::V1_0::CecDeviceType::TV,
    ::android::hardware::tv::cec::V1_0::CecDeviceType::RECORDER,
    ::android::hardware::tv::cec::V1_0::CecDeviceType::TUNER,
    ::android::hardware::tv::cec::V1_0::CecDeviceType::PLAYBACK,
    ::android::hardware::tv::cec::V1_0::CecDeviceType::AUDIO_SYSTEM,
    ::android::hardware::tv::cec::V1_0::CecDeviceType::MAX,
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
template<> inline constexpr std::array<::android::hardware::tv::cec::V1_0::CecLogicalAddress, 15> hidl_enum_values<::android::hardware::tv::cec::V1_0::CecLogicalAddress> = {
    ::android::hardware::tv::cec::V1_0::CecLogicalAddress::TV,
    ::android::hardware::tv::cec::V1_0::CecLogicalAddress::RECORDER_1,
    ::android::hardware::tv::cec::V1_0::CecLogicalAddress::RECORDER_2,
    ::android::hardware::tv::cec::V1_0::CecLogicalAddress::TUNER_1,
    ::android::hardware::tv::cec::V1_0::CecLogicalAddress::PLAYBACK_1,
    ::android::hardware::tv::cec::V1_0::CecLogicalAddress::AUDIO_SYSTEM,
    ::android::hardware::tv::cec::V1_0::CecLogicalAddress::TUNER_2,
    ::android::hardware::tv::cec::V1_0::CecLogicalAddress::TUNER_3,
    ::android::hardware::tv::cec::V1_0::CecLogicalAddress::PLAYBACK_2,
    ::android::hardware::tv::cec::V1_0::CecLogicalAddress::RECORDER_3,
    ::android::hardware::tv::cec::V1_0::CecLogicalAddress::TUNER_4,
    ::android::hardware::tv::cec::V1_0::CecLogicalAddress::PLAYBACK_3,
    ::android::hardware::tv::cec::V1_0::CecLogicalAddress::FREE_USE,
    ::android::hardware::tv::cec::V1_0::CecLogicalAddress::UNREGISTERED,
    ::android::hardware::tv::cec::V1_0::CecLogicalAddress::BROADCAST,
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
template<> inline constexpr std::array<::android::hardware::tv::cec::V1_0::CecMessageType, 70> hidl_enum_values<::android::hardware::tv::cec::V1_0::CecMessageType> = {
    ::android::hardware::tv::cec::V1_0::CecMessageType::FEATURE_ABORT,
    ::android::hardware::tv::cec::V1_0::CecMessageType::IMAGE_VIEW_ON,
    ::android::hardware::tv::cec::V1_0::CecMessageType::TUNER_STEP_INCREMENT,
    ::android::hardware::tv::cec::V1_0::CecMessageType::TUNER_STEP_DECREMENT,
    ::android::hardware::tv::cec::V1_0::CecMessageType::TUNER_DEVICE_STATUS,
    ::android::hardware::tv::cec::V1_0::CecMessageType::GIVE_TUNER_DEVICE_STATUS,
    ::android::hardware::tv::cec::V1_0::CecMessageType::RECORD_ON,
    ::android::hardware::tv::cec::V1_0::CecMessageType::RECORD_STATUS,
    ::android::hardware::tv::cec::V1_0::CecMessageType::RECORD_OFF,
    ::android::hardware::tv::cec::V1_0::CecMessageType::TEXT_VIEW_ON,
    ::android::hardware::tv::cec::V1_0::CecMessageType::RECORD_TV_SCREEN,
    ::android::hardware::tv::cec::V1_0::CecMessageType::GIVE_DECK_STATUS,
    ::android::hardware::tv::cec::V1_0::CecMessageType::DECK_STATUS,
    ::android::hardware::tv::cec::V1_0::CecMessageType::SET_MENU_LANGUAGE,
    ::android::hardware::tv::cec::V1_0::CecMessageType::CLEAR_ANALOG_TIMER,
    ::android::hardware::tv::cec::V1_0::CecMessageType::SET_ANALOG_TIMER,
    ::android::hardware::tv::cec::V1_0::CecMessageType::TIMER_STATUS,
    ::android::hardware::tv::cec::V1_0::CecMessageType::STANDBY,
    ::android::hardware::tv::cec::V1_0::CecMessageType::PLAY,
    ::android::hardware::tv::cec::V1_0::CecMessageType::DECK_CONTROL,
    ::android::hardware::tv::cec::V1_0::CecMessageType::TIMER_CLEARED_STATUS,
    ::android::hardware::tv::cec::V1_0::CecMessageType::USER_CONTROL_PRESSED,
    ::android::hardware::tv::cec::V1_0::CecMessageType::USER_CONTROL_RELEASED,
    ::android::hardware::tv::cec::V1_0::CecMessageType::GIVE_OSD_NAME,
    ::android::hardware::tv::cec::V1_0::CecMessageType::SET_OSD_NAME,
    ::android::hardware::tv::cec::V1_0::CecMessageType::SET_OSD_STRING,
    ::android::hardware::tv::cec::V1_0::CecMessageType::SET_TIMER_PROGRAM_TITLE,
    ::android::hardware::tv::cec::V1_0::CecMessageType::SYSTEM_AUDIO_MODE_REQUEST,
    ::android::hardware::tv::cec::V1_0::CecMessageType::GIVE_AUDIO_STATUS,
    ::android::hardware::tv::cec::V1_0::CecMessageType::SET_SYSTEM_AUDIO_MODE,
    ::android::hardware::tv::cec::V1_0::CecMessageType::REPORT_AUDIO_STATUS,
    ::android::hardware::tv::cec::V1_0::CecMessageType::GIVE_SYSTEM_AUDIO_MODE_STATUS,
    ::android::hardware::tv::cec::V1_0::CecMessageType::SYSTEM_AUDIO_MODE_STATUS,
    ::android::hardware::tv::cec::V1_0::CecMessageType::ROUTING_CHANGE,
    ::android::hardware::tv::cec::V1_0::CecMessageType::ROUTING_INFORMATION,
    ::android::hardware::tv::cec::V1_0::CecMessageType::ACTIVE_SOURCE,
    ::android::hardware::tv::cec::V1_0::CecMessageType::GIVE_PHYSICAL_ADDRESS,
    ::android::hardware::tv::cec::V1_0::CecMessageType::REPORT_PHYSICAL_ADDRESS,
    ::android::hardware::tv::cec::V1_0::CecMessageType::REQUEST_ACTIVE_SOURCE,
    ::android::hardware::tv::cec::V1_0::CecMessageType::SET_STREAM_PATH,
    ::android::hardware::tv::cec::V1_0::CecMessageType::DEVICE_VENDOR_ID,
    ::android::hardware::tv::cec::V1_0::CecMessageType::VENDOR_COMMAND,
    ::android::hardware::tv::cec::V1_0::CecMessageType::VENDOR_REMOTE_BUTTON_DOWN,
    ::android::hardware::tv::cec::V1_0::CecMessageType::VENDOR_REMOTE_BUTTON_UP,
    ::android::hardware::tv::cec::V1_0::CecMessageType::GIVE_DEVICE_VENDOR_ID,
    ::android::hardware::tv::cec::V1_0::CecMessageType::MENU_REQUEST,
    ::android::hardware::tv::cec::V1_0::CecMessageType::MENU_STATUS,
    ::android::hardware::tv::cec::V1_0::CecMessageType::GIVE_DEVICE_POWER_STATUS,
    ::android::hardware::tv::cec::V1_0::CecMessageType::REPORT_POWER_STATUS,
    ::android::hardware::tv::cec::V1_0::CecMessageType::GET_MENU_LANGUAGE,
    ::android::hardware::tv::cec::V1_0::CecMessageType::SELECT_ANALOG_SERVICE,
    ::android::hardware::tv::cec::V1_0::CecMessageType::SELECT_DIGITAL_SERVICE,
    ::android::hardware::tv::cec::V1_0::CecMessageType::SET_DIGITAL_TIMER,
    ::android::hardware::tv::cec::V1_0::CecMessageType::CLEAR_DIGITAL_TIMER,
    ::android::hardware::tv::cec::V1_0::CecMessageType::SET_AUDIO_RATE,
    ::android::hardware::tv::cec::V1_0::CecMessageType::INACTIVE_SOURCE,
    ::android::hardware::tv::cec::V1_0::CecMessageType::CEC_VERSION,
    ::android::hardware::tv::cec::V1_0::CecMessageType::GET_CEC_VERSION,
    ::android::hardware::tv::cec::V1_0::CecMessageType::VENDOR_COMMAND_WITH_ID,
    ::android::hardware::tv::cec::V1_0::CecMessageType::CLEAR_EXTERNAL_TIMER,
    ::android::hardware::tv::cec::V1_0::CecMessageType::SET_EXTERNAL_TIMER,
    ::android::hardware::tv::cec::V1_0::CecMessageType::REPORT_SHORT_AUDIO_DESCRIPTOR,
    ::android::hardware::tv::cec::V1_0::CecMessageType::REQUEST_SHORT_AUDIO_DESCRIPTOR,
    ::android::hardware::tv::cec::V1_0::CecMessageType::INITIATE_ARC,
    ::android::hardware::tv::cec::V1_0::CecMessageType::REPORT_ARC_INITIATED,
    ::android::hardware::tv::cec::V1_0::CecMessageType::REPORT_ARC_TERMINATED,
    ::android::hardware::tv::cec::V1_0::CecMessageType::REQUEST_ARC_INITIATION,
    ::android::hardware::tv::cec::V1_0::CecMessageType::REQUEST_ARC_TERMINATION,
    ::android::hardware::tv::cec::V1_0::CecMessageType::TERMINATE_ARC,
    ::android::hardware::tv::cec::V1_0::CecMessageType::ABORT,
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
template<> inline constexpr std::array<::android::hardware::tv::cec::V1_0::AbortReason, 6> hidl_enum_values<::android::hardware::tv::cec::V1_0::AbortReason> = {
    ::android::hardware::tv::cec::V1_0::AbortReason::UNRECOGNIZED_MODE,
    ::android::hardware::tv::cec::V1_0::AbortReason::NOT_IN_CORRECT_MODE,
    ::android::hardware::tv::cec::V1_0::AbortReason::CANNOT_PROVIDE_SOURCE,
    ::android::hardware::tv::cec::V1_0::AbortReason::INVALID_OPERAND,
    ::android::hardware::tv::cec::V1_0::AbortReason::REFUSED,
    ::android::hardware::tv::cec::V1_0::AbortReason::UNABLE_TO_DETERMINE,
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
template<> inline constexpr std::array<::android::hardware::tv::cec::V1_0::Result, 6> hidl_enum_values<::android::hardware::tv::cec::V1_0::Result> = {
    ::android::hardware::tv::cec::V1_0::Result::SUCCESS,
    ::android::hardware::tv::cec::V1_0::Result::FAILURE_UNKNOWN,
    ::android::hardware::tv::cec::V1_0::Result::FAILURE_INVALID_ARGS,
    ::android::hardware::tv::cec::V1_0::Result::FAILURE_INVALID_STATE,
    ::android::hardware::tv::cec::V1_0::Result::FAILURE_NOT_SUPPORTED,
    ::android::hardware::tv::cec::V1_0::Result::FAILURE_BUSY,
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
template<> inline constexpr std::array<::android::hardware::tv::cec::V1_0::SendMessageResult, 4> hidl_enum_values<::android::hardware::tv::cec::V1_0::SendMessageResult> = {
    ::android::hardware::tv::cec::V1_0::SendMessageResult::SUCCESS,
    ::android::hardware::tv::cec::V1_0::SendMessageResult::NACK,
    ::android::hardware::tv::cec::V1_0::SendMessageResult::BUSY,
    ::android::hardware::tv::cec::V1_0::SendMessageResult::FAIL,
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
template<> inline constexpr std::array<::android::hardware::tv::cec::V1_0::HdmiPortType, 2> hidl_enum_values<::android::hardware::tv::cec::V1_0::HdmiPortType> = {
    ::android::hardware::tv::cec::V1_0::HdmiPortType::INPUT,
    ::android::hardware::tv::cec::V1_0::HdmiPortType::OUTPUT,
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
template<> inline constexpr std::array<::android::hardware::tv::cec::V1_0::OptionKey, 3> hidl_enum_values<::android::hardware::tv::cec::V1_0::OptionKey> = {
    ::android::hardware::tv::cec::V1_0::OptionKey::WAKEUP,
    ::android::hardware::tv::cec::V1_0::OptionKey::ENABLE_CEC,
    ::android::hardware::tv::cec::V1_0::OptionKey::SYSTEM_CEC_CONTROL,
};
#pragma clang diagnostic pop
}  // namespace details
}  // namespace hardware
}  // namespace android


#endif  // HIDL_GENERATED_ANDROID_HARDWARE_TV_CEC_V1_0_TYPES_H
