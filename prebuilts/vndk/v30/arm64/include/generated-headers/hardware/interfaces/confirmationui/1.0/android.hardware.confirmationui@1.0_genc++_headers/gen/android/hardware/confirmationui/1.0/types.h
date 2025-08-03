#ifndef HIDL_GENERATED_ANDROID_HARDWARE_CONFIRMATIONUI_V1_0_TYPES_H
#define HIDL_GENERATED_ANDROID_HARDWARE_CONFIRMATIONUI_V1_0_TYPES_H

#include <hidl/HidlSupport.h>
#include <hidl/MQDescriptor.h>
#include <utils/NativeHandle.h>
#include <utils/misc.h>

namespace android {
namespace hardware {
namespace confirmationui {
namespace V1_0 {

// Forward declaration for forward reference support:
enum class UIOption : uint32_t;
enum class ResponseCode : uint32_t;
enum class MessageSize : uint32_t;
enum class TestKeyBits : uint8_t;
enum class TestModeCommands : uint64_t;

/**
 * UI modification options.
 */
enum class UIOption : uint32_t {
    /**
     * Accessibility: Requests color inverted style.
     */
    AccessibilityInverted = 0u,
    /**
     * Accessibility: Requests magnified style.
     */
    AccessibilityMagnified = 1u,
};

/**
 * Codes returned by ConfirmationUI API calls.
 */
enum class ResponseCode : uint32_t {
    /**
     * API call succeeded or the user gave approval (result callback).
     */
    OK = 0u,
    /**
     * The user canceled the TUI (result callback).
     */
    Canceled = 1u,
    /**
     * IConfirmationUI::abort() was called. (result callback).
     */
    Aborted = 2u,
    /**
     * Cannot start another prompt.
     */
    OperationPending = 3u,
    /**
     * IConfirmationUI::deliverSecureInputEvent call was ingored.
     */
    Ignored = 4u,
    /**
     * An unexpected system error occured.
     */
    SystemError = 5u,
    /**
     * Returned by an unimplemented API call.
     */
    Unimplemented = 6u,
    /**
     * This is returned when an error is diagnosed that should have been
     * caught by earlier input sanitization. Should never be seen in production.
     */
    Unexpected = 7u,
    /**
     * General UI error.
     */
    UIError = 65536u /* 0x10000 */,
    UIErrorMissingGlyph = 65537u /* ::android::hardware::confirmationui::V1_0::ResponseCode.UIError implicitly + 1 */,
    /**
     * The implementation must return this error code on promptUserConfirmation if the
     * resulting formatted message does not fit into MessageSize::MAX bytes. It is
     * advised that the implementation formats the message upon receiving this API call to
     * be able to diagnose this syndrome.
     */
    UIErrorMessageTooLong = 65538u /* ::android::hardware::confirmationui::V1_0::ResponseCode.UIErrorMissingGlyph implicitly + 1 */,
    UIErrorMalformedUTF8Encoding = 65539u /* ::android::hardware::confirmationui::V1_0::ResponseCode.UIErrorMessageTooLong implicitly + 1 */,
};

/**
 * This defines the maximum message size. This indirectly limits the size of the prompt text
 * and the extra data that can be passed to the confirmation UI. The prompt text and extra data
 * must fit in to this size including CBOR header information.
 */
enum class MessageSize : uint32_t {
    MAX = 6144u /* 0x1800 */,
};

/**
 * The test key is 32byte word with all bytes set to TestKeyBits::BYTE.
 */
enum class TestKeyBits : uint8_t {
    BYTE = 165 /* 0xA5 */,
};

/**
 * Test mode commands.
 *
 * IConfirmationUI::deliverSecureInputEvent can be used to test certain code paths.
 * To that end, the caller passes an auth token that has an HMAC keyed with the test key
 * (see TestKeyBits in types.hal). Implementations first check the HMAC against test key.
 * If the test key produces a matching HMAC, the implementation evaluates the challenge field
 * of the auth token against the values defined in TestModeCommand.
 * If the command indicates that a confirmation token is to be generated the test key MUST be used
 * to generate this confirmation token.
 *
 * See command code for individual test command descriptions.
 */
enum class TestModeCommands : uint64_t {
    /**
     * Simulates the user pressing the OK button on the UI. If no operation is pending
     * ResponseCode::Ignored must be returned. A pending operation is finalized successfully
     * see IConfirmationResultCallback::result, however, the test key (see TestKeyBits) MUST be
     * used to generate the confirmation token.
     */
    OK_EVENT = 0ull,
    /**
     * Simulates the user pressing the CANCEL button on the UI. If no operation is pending
     * Result::Ignored must be returned. A pending operation is finalized as specified in
     * IConfirmationResultCallback.hal.
     */
    CANCEL_EVENT = 1ull,
};

//
// type declarations for package
//

template<typename>
static inline std::string toString(uint32_t o);
static inline std::string toString(::android::hardware::confirmationui::V1_0::UIOption o);
static inline void PrintTo(::android::hardware::confirmationui::V1_0::UIOption o, ::std::ostream* os);
constexpr uint32_t operator|(const ::android::hardware::confirmationui::V1_0::UIOption lhs, const ::android::hardware::confirmationui::V1_0::UIOption rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const uint32_t lhs, const ::android::hardware::confirmationui::V1_0::UIOption rhs) {
    return static_cast<uint32_t>(lhs | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const ::android::hardware::confirmationui::V1_0::UIOption lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | rhs);
}
constexpr uint32_t operator&(const ::android::hardware::confirmationui::V1_0::UIOption lhs, const ::android::hardware::confirmationui::V1_0::UIOption rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const uint32_t lhs, const ::android::hardware::confirmationui::V1_0::UIOption rhs) {
    return static_cast<uint32_t>(lhs & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const ::android::hardware::confirmationui::V1_0::UIOption lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & rhs);
}
constexpr uint32_t &operator|=(uint32_t& v, const ::android::hardware::confirmationui::V1_0::UIOption e) {
    v |= static_cast<uint32_t>(e);
    return v;
}
constexpr uint32_t &operator&=(uint32_t& v, const ::android::hardware::confirmationui::V1_0::UIOption e) {
    v &= static_cast<uint32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(uint32_t o);
static inline std::string toString(::android::hardware::confirmationui::V1_0::ResponseCode o);
static inline void PrintTo(::android::hardware::confirmationui::V1_0::ResponseCode o, ::std::ostream* os);
constexpr uint32_t operator|(const ::android::hardware::confirmationui::V1_0::ResponseCode lhs, const ::android::hardware::confirmationui::V1_0::ResponseCode rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const uint32_t lhs, const ::android::hardware::confirmationui::V1_0::ResponseCode rhs) {
    return static_cast<uint32_t>(lhs | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const ::android::hardware::confirmationui::V1_0::ResponseCode lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | rhs);
}
constexpr uint32_t operator&(const ::android::hardware::confirmationui::V1_0::ResponseCode lhs, const ::android::hardware::confirmationui::V1_0::ResponseCode rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const uint32_t lhs, const ::android::hardware::confirmationui::V1_0::ResponseCode rhs) {
    return static_cast<uint32_t>(lhs & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const ::android::hardware::confirmationui::V1_0::ResponseCode lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & rhs);
}
constexpr uint32_t &operator|=(uint32_t& v, const ::android::hardware::confirmationui::V1_0::ResponseCode e) {
    v |= static_cast<uint32_t>(e);
    return v;
}
constexpr uint32_t &operator&=(uint32_t& v, const ::android::hardware::confirmationui::V1_0::ResponseCode e) {
    v &= static_cast<uint32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(uint32_t o);
static inline std::string toString(::android::hardware::confirmationui::V1_0::MessageSize o);
static inline void PrintTo(::android::hardware::confirmationui::V1_0::MessageSize o, ::std::ostream* os);
constexpr uint32_t operator|(const ::android::hardware::confirmationui::V1_0::MessageSize lhs, const ::android::hardware::confirmationui::V1_0::MessageSize rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const uint32_t lhs, const ::android::hardware::confirmationui::V1_0::MessageSize rhs) {
    return static_cast<uint32_t>(lhs | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const ::android::hardware::confirmationui::V1_0::MessageSize lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | rhs);
}
constexpr uint32_t operator&(const ::android::hardware::confirmationui::V1_0::MessageSize lhs, const ::android::hardware::confirmationui::V1_0::MessageSize rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const uint32_t lhs, const ::android::hardware::confirmationui::V1_0::MessageSize rhs) {
    return static_cast<uint32_t>(lhs & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const ::android::hardware::confirmationui::V1_0::MessageSize lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & rhs);
}
constexpr uint32_t &operator|=(uint32_t& v, const ::android::hardware::confirmationui::V1_0::MessageSize e) {
    v |= static_cast<uint32_t>(e);
    return v;
}
constexpr uint32_t &operator&=(uint32_t& v, const ::android::hardware::confirmationui::V1_0::MessageSize e) {
    v &= static_cast<uint32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(uint8_t o);
static inline std::string toString(::android::hardware::confirmationui::V1_0::TestKeyBits o);
static inline void PrintTo(::android::hardware::confirmationui::V1_0::TestKeyBits o, ::std::ostream* os);
constexpr uint8_t operator|(const ::android::hardware::confirmationui::V1_0::TestKeyBits lhs, const ::android::hardware::confirmationui::V1_0::TestKeyBits rhs) {
    return static_cast<uint8_t>(static_cast<uint8_t>(lhs) | static_cast<uint8_t>(rhs));
}
constexpr uint8_t operator|(const uint8_t lhs, const ::android::hardware::confirmationui::V1_0::TestKeyBits rhs) {
    return static_cast<uint8_t>(lhs | static_cast<uint8_t>(rhs));
}
constexpr uint8_t operator|(const ::android::hardware::confirmationui::V1_0::TestKeyBits lhs, const uint8_t rhs) {
    return static_cast<uint8_t>(static_cast<uint8_t>(lhs) | rhs);
}
constexpr uint8_t operator&(const ::android::hardware::confirmationui::V1_0::TestKeyBits lhs, const ::android::hardware::confirmationui::V1_0::TestKeyBits rhs) {
    return static_cast<uint8_t>(static_cast<uint8_t>(lhs) & static_cast<uint8_t>(rhs));
}
constexpr uint8_t operator&(const uint8_t lhs, const ::android::hardware::confirmationui::V1_0::TestKeyBits rhs) {
    return static_cast<uint8_t>(lhs & static_cast<uint8_t>(rhs));
}
constexpr uint8_t operator&(const ::android::hardware::confirmationui::V1_0::TestKeyBits lhs, const uint8_t rhs) {
    return static_cast<uint8_t>(static_cast<uint8_t>(lhs) & rhs);
}
constexpr uint8_t &operator|=(uint8_t& v, const ::android::hardware::confirmationui::V1_0::TestKeyBits e) {
    v |= static_cast<uint8_t>(e);
    return v;
}
constexpr uint8_t &operator&=(uint8_t& v, const ::android::hardware::confirmationui::V1_0::TestKeyBits e) {
    v &= static_cast<uint8_t>(e);
    return v;
}

template<typename>
static inline std::string toString(uint64_t o);
static inline std::string toString(::android::hardware::confirmationui::V1_0::TestModeCommands o);
static inline void PrintTo(::android::hardware::confirmationui::V1_0::TestModeCommands o, ::std::ostream* os);
constexpr uint64_t operator|(const ::android::hardware::confirmationui::V1_0::TestModeCommands lhs, const ::android::hardware::confirmationui::V1_0::TestModeCommands rhs) {
    return static_cast<uint64_t>(static_cast<uint64_t>(lhs) | static_cast<uint64_t>(rhs));
}
constexpr uint64_t operator|(const uint64_t lhs, const ::android::hardware::confirmationui::V1_0::TestModeCommands rhs) {
    return static_cast<uint64_t>(lhs | static_cast<uint64_t>(rhs));
}
constexpr uint64_t operator|(const ::android::hardware::confirmationui::V1_0::TestModeCommands lhs, const uint64_t rhs) {
    return static_cast<uint64_t>(static_cast<uint64_t>(lhs) | rhs);
}
constexpr uint64_t operator&(const ::android::hardware::confirmationui::V1_0::TestModeCommands lhs, const ::android::hardware::confirmationui::V1_0::TestModeCommands rhs) {
    return static_cast<uint64_t>(static_cast<uint64_t>(lhs) & static_cast<uint64_t>(rhs));
}
constexpr uint64_t operator&(const uint64_t lhs, const ::android::hardware::confirmationui::V1_0::TestModeCommands rhs) {
    return static_cast<uint64_t>(lhs & static_cast<uint64_t>(rhs));
}
constexpr uint64_t operator&(const ::android::hardware::confirmationui::V1_0::TestModeCommands lhs, const uint64_t rhs) {
    return static_cast<uint64_t>(static_cast<uint64_t>(lhs) & rhs);
}
constexpr uint64_t &operator|=(uint64_t& v, const ::android::hardware::confirmationui::V1_0::TestModeCommands e) {
    v |= static_cast<uint64_t>(e);
    return v;
}
constexpr uint64_t &operator&=(uint64_t& v, const ::android::hardware::confirmationui::V1_0::TestModeCommands e) {
    v &= static_cast<uint64_t>(e);
    return v;
}

//
// type header definitions for package
//

template<>
inline std::string toString<::android::hardware::confirmationui::V1_0::UIOption>(uint32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::confirmationui::V1_0::UIOption> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::confirmationui::V1_0::UIOption::AccessibilityInverted) == static_cast<uint32_t>(::android::hardware::confirmationui::V1_0::UIOption::AccessibilityInverted)) {
        os += (first ? "" : " | ");
        os += "AccessibilityInverted";
        first = false;
        flipped |= ::android::hardware::confirmationui::V1_0::UIOption::AccessibilityInverted;
    }
    if ((o & ::android::hardware::confirmationui::V1_0::UIOption::AccessibilityMagnified) == static_cast<uint32_t>(::android::hardware::confirmationui::V1_0::UIOption::AccessibilityMagnified)) {
        os += (first ? "" : " | ");
        os += "AccessibilityMagnified";
        first = false;
        flipped |= ::android::hardware::confirmationui::V1_0::UIOption::AccessibilityMagnified;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::confirmationui::V1_0::UIOption o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::confirmationui::V1_0::UIOption::AccessibilityInverted) {
        return "AccessibilityInverted";
    }
    if (o == ::android::hardware::confirmationui::V1_0::UIOption::AccessibilityMagnified) {
        return "AccessibilityMagnified";
    }
    std::string os;
    os += toHexString(static_cast<uint32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::confirmationui::V1_0::UIOption o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::confirmationui::V1_0::ResponseCode>(uint32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::confirmationui::V1_0::ResponseCode> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::confirmationui::V1_0::ResponseCode::OK) == static_cast<uint32_t>(::android::hardware::confirmationui::V1_0::ResponseCode::OK)) {
        os += (first ? "" : " | ");
        os += "OK";
        first = false;
        flipped |= ::android::hardware::confirmationui::V1_0::ResponseCode::OK;
    }
    if ((o & ::android::hardware::confirmationui::V1_0::ResponseCode::Canceled) == static_cast<uint32_t>(::android::hardware::confirmationui::V1_0::ResponseCode::Canceled)) {
        os += (first ? "" : " | ");
        os += "Canceled";
        first = false;
        flipped |= ::android::hardware::confirmationui::V1_0::ResponseCode::Canceled;
    }
    if ((o & ::android::hardware::confirmationui::V1_0::ResponseCode::Aborted) == static_cast<uint32_t>(::android::hardware::confirmationui::V1_0::ResponseCode::Aborted)) {
        os += (first ? "" : " | ");
        os += "Aborted";
        first = false;
        flipped |= ::android::hardware::confirmationui::V1_0::ResponseCode::Aborted;
    }
    if ((o & ::android::hardware::confirmationui::V1_0::ResponseCode::OperationPending) == static_cast<uint32_t>(::android::hardware::confirmationui::V1_0::ResponseCode::OperationPending)) {
        os += (first ? "" : " | ");
        os += "OperationPending";
        first = false;
        flipped |= ::android::hardware::confirmationui::V1_0::ResponseCode::OperationPending;
    }
    if ((o & ::android::hardware::confirmationui::V1_0::ResponseCode::Ignored) == static_cast<uint32_t>(::android::hardware::confirmationui::V1_0::ResponseCode::Ignored)) {
        os += (first ? "" : " | ");
        os += "Ignored";
        first = false;
        flipped |= ::android::hardware::confirmationui::V1_0::ResponseCode::Ignored;
    }
    if ((o & ::android::hardware::confirmationui::V1_0::ResponseCode::SystemError) == static_cast<uint32_t>(::android::hardware::confirmationui::V1_0::ResponseCode::SystemError)) {
        os += (first ? "" : " | ");
        os += "SystemError";
        first = false;
        flipped |= ::android::hardware::confirmationui::V1_0::ResponseCode::SystemError;
    }
    if ((o & ::android::hardware::confirmationui::V1_0::ResponseCode::Unimplemented) == static_cast<uint32_t>(::android::hardware::confirmationui::V1_0::ResponseCode::Unimplemented)) {
        os += (first ? "" : " | ");
        os += "Unimplemented";
        first = false;
        flipped |= ::android::hardware::confirmationui::V1_0::ResponseCode::Unimplemented;
    }
    if ((o & ::android::hardware::confirmationui::V1_0::ResponseCode::Unexpected) == static_cast<uint32_t>(::android::hardware::confirmationui::V1_0::ResponseCode::Unexpected)) {
        os += (first ? "" : " | ");
        os += "Unexpected";
        first = false;
        flipped |= ::android::hardware::confirmationui::V1_0::ResponseCode::Unexpected;
    }
    if ((o & ::android::hardware::confirmationui::V1_0::ResponseCode::UIError) == static_cast<uint32_t>(::android::hardware::confirmationui::V1_0::ResponseCode::UIError)) {
        os += (first ? "" : " | ");
        os += "UIError";
        first = false;
        flipped |= ::android::hardware::confirmationui::V1_0::ResponseCode::UIError;
    }
    if ((o & ::android::hardware::confirmationui::V1_0::ResponseCode::UIErrorMissingGlyph) == static_cast<uint32_t>(::android::hardware::confirmationui::V1_0::ResponseCode::UIErrorMissingGlyph)) {
        os += (first ? "" : " | ");
        os += "UIErrorMissingGlyph";
        first = false;
        flipped |= ::android::hardware::confirmationui::V1_0::ResponseCode::UIErrorMissingGlyph;
    }
    if ((o & ::android::hardware::confirmationui::V1_0::ResponseCode::UIErrorMessageTooLong) == static_cast<uint32_t>(::android::hardware::confirmationui::V1_0::ResponseCode::UIErrorMessageTooLong)) {
        os += (first ? "" : " | ");
        os += "UIErrorMessageTooLong";
        first = false;
        flipped |= ::android::hardware::confirmationui::V1_0::ResponseCode::UIErrorMessageTooLong;
    }
    if ((o & ::android::hardware::confirmationui::V1_0::ResponseCode::UIErrorMalformedUTF8Encoding) == static_cast<uint32_t>(::android::hardware::confirmationui::V1_0::ResponseCode::UIErrorMalformedUTF8Encoding)) {
        os += (first ? "" : " | ");
        os += "UIErrorMalformedUTF8Encoding";
        first = false;
        flipped |= ::android::hardware::confirmationui::V1_0::ResponseCode::UIErrorMalformedUTF8Encoding;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::confirmationui::V1_0::ResponseCode o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::confirmationui::V1_0::ResponseCode::OK) {
        return "OK";
    }
    if (o == ::android::hardware::confirmationui::V1_0::ResponseCode::Canceled) {
        return "Canceled";
    }
    if (o == ::android::hardware::confirmationui::V1_0::ResponseCode::Aborted) {
        return "Aborted";
    }
    if (o == ::android::hardware::confirmationui::V1_0::ResponseCode::OperationPending) {
        return "OperationPending";
    }
    if (o == ::android::hardware::confirmationui::V1_0::ResponseCode::Ignored) {
        return "Ignored";
    }
    if (o == ::android::hardware::confirmationui::V1_0::ResponseCode::SystemError) {
        return "SystemError";
    }
    if (o == ::android::hardware::confirmationui::V1_0::ResponseCode::Unimplemented) {
        return "Unimplemented";
    }
    if (o == ::android::hardware::confirmationui::V1_0::ResponseCode::Unexpected) {
        return "Unexpected";
    }
    if (o == ::android::hardware::confirmationui::V1_0::ResponseCode::UIError) {
        return "UIError";
    }
    if (o == ::android::hardware::confirmationui::V1_0::ResponseCode::UIErrorMissingGlyph) {
        return "UIErrorMissingGlyph";
    }
    if (o == ::android::hardware::confirmationui::V1_0::ResponseCode::UIErrorMessageTooLong) {
        return "UIErrorMessageTooLong";
    }
    if (o == ::android::hardware::confirmationui::V1_0::ResponseCode::UIErrorMalformedUTF8Encoding) {
        return "UIErrorMalformedUTF8Encoding";
    }
    std::string os;
    os += toHexString(static_cast<uint32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::confirmationui::V1_0::ResponseCode o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::confirmationui::V1_0::MessageSize>(uint32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::confirmationui::V1_0::MessageSize> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::confirmationui::V1_0::MessageSize::MAX) == static_cast<uint32_t>(::android::hardware::confirmationui::V1_0::MessageSize::MAX)) {
        os += (first ? "" : " | ");
        os += "MAX";
        first = false;
        flipped |= ::android::hardware::confirmationui::V1_0::MessageSize::MAX;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::confirmationui::V1_0::MessageSize o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::confirmationui::V1_0::MessageSize::MAX) {
        return "MAX";
    }
    std::string os;
    os += toHexString(static_cast<uint32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::confirmationui::V1_0::MessageSize o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::confirmationui::V1_0::TestKeyBits>(uint8_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::confirmationui::V1_0::TestKeyBits> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::confirmationui::V1_0::TestKeyBits::BYTE) == static_cast<uint8_t>(::android::hardware::confirmationui::V1_0::TestKeyBits::BYTE)) {
        os += (first ? "" : " | ");
        os += "BYTE";
        first = false;
        flipped |= ::android::hardware::confirmationui::V1_0::TestKeyBits::BYTE;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::confirmationui::V1_0::TestKeyBits o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::confirmationui::V1_0::TestKeyBits::BYTE) {
        return "BYTE";
    }
    std::string os;
    os += toHexString(static_cast<uint8_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::confirmationui::V1_0::TestKeyBits o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::confirmationui::V1_0::TestModeCommands>(uint64_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::confirmationui::V1_0::TestModeCommands> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::confirmationui::V1_0::TestModeCommands::OK_EVENT) == static_cast<uint64_t>(::android::hardware::confirmationui::V1_0::TestModeCommands::OK_EVENT)) {
        os += (first ? "" : " | ");
        os += "OK_EVENT";
        first = false;
        flipped |= ::android::hardware::confirmationui::V1_0::TestModeCommands::OK_EVENT;
    }
    if ((o & ::android::hardware::confirmationui::V1_0::TestModeCommands::CANCEL_EVENT) == static_cast<uint64_t>(::android::hardware::confirmationui::V1_0::TestModeCommands::CANCEL_EVENT)) {
        os += (first ? "" : " | ");
        os += "CANCEL_EVENT";
        first = false;
        flipped |= ::android::hardware::confirmationui::V1_0::TestModeCommands::CANCEL_EVENT;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::confirmationui::V1_0::TestModeCommands o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::confirmationui::V1_0::TestModeCommands::OK_EVENT) {
        return "OK_EVENT";
    }
    if (o == ::android::hardware::confirmationui::V1_0::TestModeCommands::CANCEL_EVENT) {
        return "CANCEL_EVENT";
    }
    std::string os;
    os += toHexString(static_cast<uint64_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::confirmationui::V1_0::TestModeCommands o, ::std::ostream* os) {
    *os << toString(o);
}


}  // namespace V1_0
}  // namespace confirmationui
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
template<> inline constexpr std::array<::android::hardware::confirmationui::V1_0::UIOption, 2> hidl_enum_values<::android::hardware::confirmationui::V1_0::UIOption> = {
    ::android::hardware::confirmationui::V1_0::UIOption::AccessibilityInverted,
    ::android::hardware::confirmationui::V1_0::UIOption::AccessibilityMagnified,
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
template<> inline constexpr std::array<::android::hardware::confirmationui::V1_0::ResponseCode, 12> hidl_enum_values<::android::hardware::confirmationui::V1_0::ResponseCode> = {
    ::android::hardware::confirmationui::V1_0::ResponseCode::OK,
    ::android::hardware::confirmationui::V1_0::ResponseCode::Canceled,
    ::android::hardware::confirmationui::V1_0::ResponseCode::Aborted,
    ::android::hardware::confirmationui::V1_0::ResponseCode::OperationPending,
    ::android::hardware::confirmationui::V1_0::ResponseCode::Ignored,
    ::android::hardware::confirmationui::V1_0::ResponseCode::SystemError,
    ::android::hardware::confirmationui::V1_0::ResponseCode::Unimplemented,
    ::android::hardware::confirmationui::V1_0::ResponseCode::Unexpected,
    ::android::hardware::confirmationui::V1_0::ResponseCode::UIError,
    ::android::hardware::confirmationui::V1_0::ResponseCode::UIErrorMissingGlyph,
    ::android::hardware::confirmationui::V1_0::ResponseCode::UIErrorMessageTooLong,
    ::android::hardware::confirmationui::V1_0::ResponseCode::UIErrorMalformedUTF8Encoding,
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
template<> inline constexpr std::array<::android::hardware::confirmationui::V1_0::MessageSize, 1> hidl_enum_values<::android::hardware::confirmationui::V1_0::MessageSize> = {
    ::android::hardware::confirmationui::V1_0::MessageSize::MAX,
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
template<> inline constexpr std::array<::android::hardware::confirmationui::V1_0::TestKeyBits, 1> hidl_enum_values<::android::hardware::confirmationui::V1_0::TestKeyBits> = {
    ::android::hardware::confirmationui::V1_0::TestKeyBits::BYTE,
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
template<> inline constexpr std::array<::android::hardware::confirmationui::V1_0::TestModeCommands, 2> hidl_enum_values<::android::hardware::confirmationui::V1_0::TestModeCommands> = {
    ::android::hardware::confirmationui::V1_0::TestModeCommands::OK_EVENT,
    ::android::hardware::confirmationui::V1_0::TestModeCommands::CANCEL_EVENT,
};
#pragma clang diagnostic pop
}  // namespace details
}  // namespace hardware
}  // namespace android


#endif  // HIDL_GENERATED_ANDROID_HARDWARE_CONFIRMATIONUI_V1_0_TYPES_H
