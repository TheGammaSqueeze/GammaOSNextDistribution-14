#ifndef HIDL_GENERATED_ANDROID_HARDWARE_IR_V1_0_TYPES_H
#define HIDL_GENERATED_ANDROID_HARDWARE_IR_V1_0_TYPES_H

#include <hidl/HidlSupport.h>
#include <hidl/MQDescriptor.h>
#include <utils/NativeHandle.h>
#include <utils/misc.h>

namespace android {
namespace hardware {
namespace ir {
namespace V1_0 {

// Forward declaration for forward reference support:
struct ConsumerIrFreqRange;

struct ConsumerIrFreqRange final {
    uint32_t min __attribute__ ((aligned(4)));
    uint32_t max __attribute__ ((aligned(4)));
};

static_assert(offsetof(::android::hardware::ir::V1_0::ConsumerIrFreqRange, min) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::ir::V1_0::ConsumerIrFreqRange, max) == 4, "wrong offset");
static_assert(sizeof(::android::hardware::ir::V1_0::ConsumerIrFreqRange) == 8, "wrong size");
static_assert(__alignof(::android::hardware::ir::V1_0::ConsumerIrFreqRange) == 4, "wrong alignment");

//
// type declarations for package
//

static inline std::string toString(const ::android::hardware::ir::V1_0::ConsumerIrFreqRange& o);
static inline void PrintTo(const ::android::hardware::ir::V1_0::ConsumerIrFreqRange& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::ir::V1_0::ConsumerIrFreqRange& lhs, const ::android::hardware::ir::V1_0::ConsumerIrFreqRange& rhs);
static inline bool operator!=(const ::android::hardware::ir::V1_0::ConsumerIrFreqRange& lhs, const ::android::hardware::ir::V1_0::ConsumerIrFreqRange& rhs);

//
// type header definitions for package
//

static inline std::string toString(const ::android::hardware::ir::V1_0::ConsumerIrFreqRange& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".min = ";
    os += ::android::hardware::toString(o.min);
    os += ", .max = ";
    os += ::android::hardware::toString(o.max);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::ir::V1_0::ConsumerIrFreqRange& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::ir::V1_0::ConsumerIrFreqRange& lhs, const ::android::hardware::ir::V1_0::ConsumerIrFreqRange& rhs) {
    if (lhs.min != rhs.min) {
        return false;
    }
    if (lhs.max != rhs.max) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::ir::V1_0::ConsumerIrFreqRange& lhs, const ::android::hardware::ir::V1_0::ConsumerIrFreqRange& rhs){
    return !(lhs == rhs);
}


}  // namespace V1_0
}  // namespace ir
}  // namespace hardware
}  // namespace android

//
// global type declarations for package
//


#endif  // HIDL_GENERATED_ANDROID_HARDWARE_IR_V1_0_TYPES_H
