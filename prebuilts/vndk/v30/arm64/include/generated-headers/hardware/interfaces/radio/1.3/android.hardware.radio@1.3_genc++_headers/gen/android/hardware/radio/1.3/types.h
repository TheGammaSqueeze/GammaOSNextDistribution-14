#ifndef HIDL_GENERATED_ANDROID_HARDWARE_RADIO_V1_3_TYPES_H
#define HIDL_GENERATED_ANDROID_HARDWARE_RADIO_V1_3_TYPES_H

#include <android/hardware/radio/1.0/types.h>

#include <hidl/HidlSupport.h>
#include <hidl/MQDescriptor.h>
#include <utils/NativeHandle.h>
#include <utils/misc.h>

namespace android {
namespace hardware {
namespace radio {
namespace V1_3 {

// Forward declaration for forward reference support:
struct RadioResponseInfoModem;

struct RadioResponseInfoModem final {
    ::android::hardware::radio::V1_0::RadioResponseType type __attribute__ ((aligned(4)));
    int32_t serial __attribute__ ((aligned(4)));
    ::android::hardware::radio::V1_0::RadioError error __attribute__ ((aligned(4)));
    bool isEnabled __attribute__ ((aligned(1)));
};

static_assert(offsetof(::android::hardware::radio::V1_3::RadioResponseInfoModem, type) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::radio::V1_3::RadioResponseInfoModem, serial) == 4, "wrong offset");
static_assert(offsetof(::android::hardware::radio::V1_3::RadioResponseInfoModem, error) == 8, "wrong offset");
static_assert(offsetof(::android::hardware::radio::V1_3::RadioResponseInfoModem, isEnabled) == 12, "wrong offset");
static_assert(sizeof(::android::hardware::radio::V1_3::RadioResponseInfoModem) == 16, "wrong size");
static_assert(__alignof(::android::hardware::radio::V1_3::RadioResponseInfoModem) == 4, "wrong alignment");

//
// type declarations for package
//

static inline std::string toString(const ::android::hardware::radio::V1_3::RadioResponseInfoModem& o);
static inline void PrintTo(const ::android::hardware::radio::V1_3::RadioResponseInfoModem& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::radio::V1_3::RadioResponseInfoModem& lhs, const ::android::hardware::radio::V1_3::RadioResponseInfoModem& rhs);
static inline bool operator!=(const ::android::hardware::radio::V1_3::RadioResponseInfoModem& lhs, const ::android::hardware::radio::V1_3::RadioResponseInfoModem& rhs);

//
// type header definitions for package
//

static inline std::string toString(const ::android::hardware::radio::V1_3::RadioResponseInfoModem& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".type = ";
    os += ::android::hardware::radio::V1_0::toString(o.type);
    os += ", .serial = ";
    os += ::android::hardware::toString(o.serial);
    os += ", .error = ";
    os += ::android::hardware::radio::V1_0::toString(o.error);
    os += ", .isEnabled = ";
    os += ::android::hardware::toString(o.isEnabled);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::radio::V1_3::RadioResponseInfoModem& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::radio::V1_3::RadioResponseInfoModem& lhs, const ::android::hardware::radio::V1_3::RadioResponseInfoModem& rhs) {
    if (lhs.type != rhs.type) {
        return false;
    }
    if (lhs.serial != rhs.serial) {
        return false;
    }
    if (lhs.error != rhs.error) {
        return false;
    }
    if (lhs.isEnabled != rhs.isEnabled) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::radio::V1_3::RadioResponseInfoModem& lhs, const ::android::hardware::radio::V1_3::RadioResponseInfoModem& rhs){
    return !(lhs == rhs);
}


}  // namespace V1_3
}  // namespace radio
}  // namespace hardware
}  // namespace android

//
// global type declarations for package
//


#endif  // HIDL_GENERATED_ANDROID_HARDWARE_RADIO_V1_3_TYPES_H
