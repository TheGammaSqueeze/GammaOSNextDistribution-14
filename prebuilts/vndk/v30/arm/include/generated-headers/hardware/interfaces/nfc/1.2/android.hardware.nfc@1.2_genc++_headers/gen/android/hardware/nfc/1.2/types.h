#ifndef HIDL_GENERATED_ANDROID_HARDWARE_NFC_V1_2_TYPES_H
#define HIDL_GENERATED_ANDROID_HARDWARE_NFC_V1_2_TYPES_H

#include <android/hardware/nfc/1.1/types.h>

#include <hidl/HidlSupport.h>
#include <hidl/MQDescriptor.h>
#include <utils/NativeHandle.h>
#include <utils/misc.h>

namespace android {
namespace hardware {
namespace nfc {
namespace V1_2 {

// Forward declaration for forward reference support:
struct NfcConfig;

struct NfcConfig final {
    ::android::hardware::nfc::V1_1::NfcConfig v1_1 __attribute__ ((aligned(8)));
    /*
     * NFCEE ID for offhost UICC & eSE secure element.
     * 0x00 if there aren't any. Refer NCI specification
     */
    ::android::hardware::hidl_vec<uint8_t> offHostRouteUicc __attribute__ ((aligned(8)));
    ::android::hardware::hidl_vec<uint8_t> offHostRouteEse __attribute__ ((aligned(8)));
    /**
     * Default IsoDep route. 0x00 if there aren't any. Refer NCI spec
     */
    uint8_t defaultIsoDepRoute __attribute__ ((aligned(1)));
};

static_assert(offsetof(::android::hardware::nfc::V1_2::NfcConfig, v1_1) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::nfc::V1_2::NfcConfig, offHostRouteUicc) == 40, "wrong offset");
static_assert(offsetof(::android::hardware::nfc::V1_2::NfcConfig, offHostRouteEse) == 56, "wrong offset");
static_assert(offsetof(::android::hardware::nfc::V1_2::NfcConfig, defaultIsoDepRoute) == 72, "wrong offset");
static_assert(sizeof(::android::hardware::nfc::V1_2::NfcConfig) == 80, "wrong size");
static_assert(__alignof(::android::hardware::nfc::V1_2::NfcConfig) == 8, "wrong alignment");

//
// type declarations for package
//

static inline std::string toString(const ::android::hardware::nfc::V1_2::NfcConfig& o);
static inline void PrintTo(const ::android::hardware::nfc::V1_2::NfcConfig& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::nfc::V1_2::NfcConfig& lhs, const ::android::hardware::nfc::V1_2::NfcConfig& rhs);
static inline bool operator!=(const ::android::hardware::nfc::V1_2::NfcConfig& lhs, const ::android::hardware::nfc::V1_2::NfcConfig& rhs);

//
// type header definitions for package
//

static inline std::string toString(const ::android::hardware::nfc::V1_2::NfcConfig& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".v1_1 = ";
    os += ::android::hardware::nfc::V1_1::toString(o.v1_1);
    os += ", .offHostRouteUicc = ";
    os += ::android::hardware::toString(o.offHostRouteUicc);
    os += ", .offHostRouteEse = ";
    os += ::android::hardware::toString(o.offHostRouteEse);
    os += ", .defaultIsoDepRoute = ";
    os += ::android::hardware::toString(o.defaultIsoDepRoute);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::nfc::V1_2::NfcConfig& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::nfc::V1_2::NfcConfig& lhs, const ::android::hardware::nfc::V1_2::NfcConfig& rhs) {
    if (lhs.v1_1 != rhs.v1_1) {
        return false;
    }
    if (lhs.offHostRouteUicc != rhs.offHostRouteUicc) {
        return false;
    }
    if (lhs.offHostRouteEse != rhs.offHostRouteEse) {
        return false;
    }
    if (lhs.defaultIsoDepRoute != rhs.defaultIsoDepRoute) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::nfc::V1_2::NfcConfig& lhs, const ::android::hardware::nfc::V1_2::NfcConfig& rhs){
    return !(lhs == rhs);
}


}  // namespace V1_2
}  // namespace nfc
}  // namespace hardware
}  // namespace android

//
// global type declarations for package
//


#endif  // HIDL_GENERATED_ANDROID_HARDWARE_NFC_V1_2_TYPES_H
