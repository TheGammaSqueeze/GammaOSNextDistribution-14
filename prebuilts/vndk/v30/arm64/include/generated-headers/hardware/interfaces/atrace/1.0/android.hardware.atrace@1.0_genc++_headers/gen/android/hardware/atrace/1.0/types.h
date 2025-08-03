#ifndef HIDL_GENERATED_ANDROID_HARDWARE_ATRACE_V1_0_TYPES_H
#define HIDL_GENERATED_ANDROID_HARDWARE_ATRACE_V1_0_TYPES_H

#include <hidl/HidlSupport.h>
#include <hidl/MQDescriptor.h>
#include <utils/NativeHandle.h>
#include <utils/misc.h>

namespace android {
namespace hardware {
namespace atrace {
namespace V1_0 {

// Forward declaration for forward reference support:
enum class Status : uint32_t;
struct TracingCategory;

enum class Status : uint32_t {
    /**
     * Operation completed without errors.
     */
    SUCCESS = 0u,
    /**
     * Operation failed with errors on enabling tracing point.
     */
    ERROR_TRACING_POINT = 1u,
    /**
     * Operation failed because of invalid argument.
     */
    ERROR_INVALID_ARGUMENT = 2u,
};

struct TracingCategory final {
    /**
     * Tracing category name, unique to frameworks's category.
     */
    ::android::hardware::hidl_string name __attribute__ ((aligned(8)));
    /**
     * Tracing category description.
     */
    ::android::hardware::hidl_string description __attribute__ ((aligned(8)));
};

static_assert(offsetof(::android::hardware::atrace::V1_0::TracingCategory, name) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::atrace::V1_0::TracingCategory, description) == 16, "wrong offset");
static_assert(sizeof(::android::hardware::atrace::V1_0::TracingCategory) == 32, "wrong size");
static_assert(__alignof(::android::hardware::atrace::V1_0::TracingCategory) == 8, "wrong alignment");

//
// type declarations for package
//

template<typename>
static inline std::string toString(uint32_t o);
static inline std::string toString(::android::hardware::atrace::V1_0::Status o);
static inline void PrintTo(::android::hardware::atrace::V1_0::Status o, ::std::ostream* os);
constexpr uint32_t operator|(const ::android::hardware::atrace::V1_0::Status lhs, const ::android::hardware::atrace::V1_0::Status rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const uint32_t lhs, const ::android::hardware::atrace::V1_0::Status rhs) {
    return static_cast<uint32_t>(lhs | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const ::android::hardware::atrace::V1_0::Status lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | rhs);
}
constexpr uint32_t operator&(const ::android::hardware::atrace::V1_0::Status lhs, const ::android::hardware::atrace::V1_0::Status rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const uint32_t lhs, const ::android::hardware::atrace::V1_0::Status rhs) {
    return static_cast<uint32_t>(lhs & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const ::android::hardware::atrace::V1_0::Status lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & rhs);
}
constexpr uint32_t &operator|=(uint32_t& v, const ::android::hardware::atrace::V1_0::Status e) {
    v |= static_cast<uint32_t>(e);
    return v;
}
constexpr uint32_t &operator&=(uint32_t& v, const ::android::hardware::atrace::V1_0::Status e) {
    v &= static_cast<uint32_t>(e);
    return v;
}

static inline std::string toString(const ::android::hardware::atrace::V1_0::TracingCategory& o);
static inline void PrintTo(const ::android::hardware::atrace::V1_0::TracingCategory& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::atrace::V1_0::TracingCategory& lhs, const ::android::hardware::atrace::V1_0::TracingCategory& rhs);
static inline bool operator!=(const ::android::hardware::atrace::V1_0::TracingCategory& lhs, const ::android::hardware::atrace::V1_0::TracingCategory& rhs);

//
// type header definitions for package
//

template<>
inline std::string toString<::android::hardware::atrace::V1_0::Status>(uint32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::atrace::V1_0::Status> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::atrace::V1_0::Status::SUCCESS) == static_cast<uint32_t>(::android::hardware::atrace::V1_0::Status::SUCCESS)) {
        os += (first ? "" : " | ");
        os += "SUCCESS";
        first = false;
        flipped |= ::android::hardware::atrace::V1_0::Status::SUCCESS;
    }
    if ((o & ::android::hardware::atrace::V1_0::Status::ERROR_TRACING_POINT) == static_cast<uint32_t>(::android::hardware::atrace::V1_0::Status::ERROR_TRACING_POINT)) {
        os += (first ? "" : " | ");
        os += "ERROR_TRACING_POINT";
        first = false;
        flipped |= ::android::hardware::atrace::V1_0::Status::ERROR_TRACING_POINT;
    }
    if ((o & ::android::hardware::atrace::V1_0::Status::ERROR_INVALID_ARGUMENT) == static_cast<uint32_t>(::android::hardware::atrace::V1_0::Status::ERROR_INVALID_ARGUMENT)) {
        os += (first ? "" : " | ");
        os += "ERROR_INVALID_ARGUMENT";
        first = false;
        flipped |= ::android::hardware::atrace::V1_0::Status::ERROR_INVALID_ARGUMENT;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::atrace::V1_0::Status o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::atrace::V1_0::Status::SUCCESS) {
        return "SUCCESS";
    }
    if (o == ::android::hardware::atrace::V1_0::Status::ERROR_TRACING_POINT) {
        return "ERROR_TRACING_POINT";
    }
    if (o == ::android::hardware::atrace::V1_0::Status::ERROR_INVALID_ARGUMENT) {
        return "ERROR_INVALID_ARGUMENT";
    }
    std::string os;
    os += toHexString(static_cast<uint32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::atrace::V1_0::Status o, ::std::ostream* os) {
    *os << toString(o);
}

static inline std::string toString(const ::android::hardware::atrace::V1_0::TracingCategory& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".name = ";
    os += ::android::hardware::toString(o.name);
    os += ", .description = ";
    os += ::android::hardware::toString(o.description);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::atrace::V1_0::TracingCategory& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::atrace::V1_0::TracingCategory& lhs, const ::android::hardware::atrace::V1_0::TracingCategory& rhs) {
    if (lhs.name != rhs.name) {
        return false;
    }
    if (lhs.description != rhs.description) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::atrace::V1_0::TracingCategory& lhs, const ::android::hardware::atrace::V1_0::TracingCategory& rhs){
    return !(lhs == rhs);
}


}  // namespace V1_0
}  // namespace atrace
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
template<> inline constexpr std::array<::android::hardware::atrace::V1_0::Status, 3> hidl_enum_values<::android::hardware::atrace::V1_0::Status> = {
    ::android::hardware::atrace::V1_0::Status::SUCCESS,
    ::android::hardware::atrace::V1_0::Status::ERROR_TRACING_POINT,
    ::android::hardware::atrace::V1_0::Status::ERROR_INVALID_ARGUMENT,
};
#pragma clang diagnostic pop
}  // namespace details
}  // namespace hardware
}  // namespace android


#endif  // HIDL_GENERATED_ANDROID_HARDWARE_ATRACE_V1_0_TYPES_H
