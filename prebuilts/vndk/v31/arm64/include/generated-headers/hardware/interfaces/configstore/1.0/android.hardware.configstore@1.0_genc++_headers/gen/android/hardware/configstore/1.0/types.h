#ifndef HIDL_GENERATED_ANDROID_HARDWARE_CONFIGSTORE_V1_0_TYPES_H
#define HIDL_GENERATED_ANDROID_HARDWARE_CONFIGSTORE_V1_0_TYPES_H

#include <hidl/HidlSupport.h>
#include <hidl/MQDescriptor.h>
#include <utils/NativeHandle.h>
#include <utils/misc.h>

namespace android {
namespace hardware {
namespace configstore {
namespace V1_0 {

// Forward declaration for forward reference support:
struct OptionalBool;
struct OptionalInt32;
struct OptionalUInt32;
struct OptionalInt64;
struct OptionalUInt64;
struct OptionalString;

struct OptionalBool final {
    bool specified __attribute__ ((aligned(1)));
    bool value __attribute__ ((aligned(1)));
};

static_assert(offsetof(::android::hardware::configstore::V1_0::OptionalBool, specified) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::configstore::V1_0::OptionalBool, value) == 1, "wrong offset");
static_assert(sizeof(::android::hardware::configstore::V1_0::OptionalBool) == 2, "wrong size");
static_assert(__alignof(::android::hardware::configstore::V1_0::OptionalBool) == 1, "wrong alignment");

struct OptionalInt32 final {
    bool specified __attribute__ ((aligned(1)));
    int32_t value __attribute__ ((aligned(4)));
};

static_assert(offsetof(::android::hardware::configstore::V1_0::OptionalInt32, specified) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::configstore::V1_0::OptionalInt32, value) == 4, "wrong offset");
static_assert(sizeof(::android::hardware::configstore::V1_0::OptionalInt32) == 8, "wrong size");
static_assert(__alignof(::android::hardware::configstore::V1_0::OptionalInt32) == 4, "wrong alignment");

struct OptionalUInt32 final {
    bool specified __attribute__ ((aligned(1)));
    uint32_t value __attribute__ ((aligned(4)));
};

static_assert(offsetof(::android::hardware::configstore::V1_0::OptionalUInt32, specified) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::configstore::V1_0::OptionalUInt32, value) == 4, "wrong offset");
static_assert(sizeof(::android::hardware::configstore::V1_0::OptionalUInt32) == 8, "wrong size");
static_assert(__alignof(::android::hardware::configstore::V1_0::OptionalUInt32) == 4, "wrong alignment");

struct OptionalInt64 final {
    bool specified __attribute__ ((aligned(1)));
    int64_t value __attribute__ ((aligned(8)));
};

static_assert(offsetof(::android::hardware::configstore::V1_0::OptionalInt64, specified) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::configstore::V1_0::OptionalInt64, value) == 8, "wrong offset");
static_assert(sizeof(::android::hardware::configstore::V1_0::OptionalInt64) == 16, "wrong size");
static_assert(__alignof(::android::hardware::configstore::V1_0::OptionalInt64) == 8, "wrong alignment");

struct OptionalUInt64 final {
    bool specified __attribute__ ((aligned(1)));
    uint64_t value __attribute__ ((aligned(8)));
};

static_assert(offsetof(::android::hardware::configstore::V1_0::OptionalUInt64, specified) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::configstore::V1_0::OptionalUInt64, value) == 8, "wrong offset");
static_assert(sizeof(::android::hardware::configstore::V1_0::OptionalUInt64) == 16, "wrong size");
static_assert(__alignof(::android::hardware::configstore::V1_0::OptionalUInt64) == 8, "wrong alignment");

struct OptionalString final {
    bool specified __attribute__ ((aligned(1)));
    ::android::hardware::hidl_string value __attribute__ ((aligned(8)));
};

static_assert(offsetof(::android::hardware::configstore::V1_0::OptionalString, specified) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::configstore::V1_0::OptionalString, value) == 8, "wrong offset");
static_assert(sizeof(::android::hardware::configstore::V1_0::OptionalString) == 24, "wrong size");
static_assert(__alignof(::android::hardware::configstore::V1_0::OptionalString) == 8, "wrong alignment");

//
// type declarations for package
//

static inline std::string toString(const ::android::hardware::configstore::V1_0::OptionalBool& o);
static inline void PrintTo(const ::android::hardware::configstore::V1_0::OptionalBool& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::configstore::V1_0::OptionalBool& lhs, const ::android::hardware::configstore::V1_0::OptionalBool& rhs);
static inline bool operator!=(const ::android::hardware::configstore::V1_0::OptionalBool& lhs, const ::android::hardware::configstore::V1_0::OptionalBool& rhs);

static inline std::string toString(const ::android::hardware::configstore::V1_0::OptionalInt32& o);
static inline void PrintTo(const ::android::hardware::configstore::V1_0::OptionalInt32& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::configstore::V1_0::OptionalInt32& lhs, const ::android::hardware::configstore::V1_0::OptionalInt32& rhs);
static inline bool operator!=(const ::android::hardware::configstore::V1_0::OptionalInt32& lhs, const ::android::hardware::configstore::V1_0::OptionalInt32& rhs);

static inline std::string toString(const ::android::hardware::configstore::V1_0::OptionalUInt32& o);
static inline void PrintTo(const ::android::hardware::configstore::V1_0::OptionalUInt32& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::configstore::V1_0::OptionalUInt32& lhs, const ::android::hardware::configstore::V1_0::OptionalUInt32& rhs);
static inline bool operator!=(const ::android::hardware::configstore::V1_0::OptionalUInt32& lhs, const ::android::hardware::configstore::V1_0::OptionalUInt32& rhs);

static inline std::string toString(const ::android::hardware::configstore::V1_0::OptionalInt64& o);
static inline void PrintTo(const ::android::hardware::configstore::V1_0::OptionalInt64& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::configstore::V1_0::OptionalInt64& lhs, const ::android::hardware::configstore::V1_0::OptionalInt64& rhs);
static inline bool operator!=(const ::android::hardware::configstore::V1_0::OptionalInt64& lhs, const ::android::hardware::configstore::V1_0::OptionalInt64& rhs);

static inline std::string toString(const ::android::hardware::configstore::V1_0::OptionalUInt64& o);
static inline void PrintTo(const ::android::hardware::configstore::V1_0::OptionalUInt64& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::configstore::V1_0::OptionalUInt64& lhs, const ::android::hardware::configstore::V1_0::OptionalUInt64& rhs);
static inline bool operator!=(const ::android::hardware::configstore::V1_0::OptionalUInt64& lhs, const ::android::hardware::configstore::V1_0::OptionalUInt64& rhs);

static inline std::string toString(const ::android::hardware::configstore::V1_0::OptionalString& o);
static inline void PrintTo(const ::android::hardware::configstore::V1_0::OptionalString& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::configstore::V1_0::OptionalString& lhs, const ::android::hardware::configstore::V1_0::OptionalString& rhs);
static inline bool operator!=(const ::android::hardware::configstore::V1_0::OptionalString& lhs, const ::android::hardware::configstore::V1_0::OptionalString& rhs);

//
// type header definitions for package
//

static inline std::string toString(const ::android::hardware::configstore::V1_0::OptionalBool& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".specified = ";
    os += ::android::hardware::toString(o.specified);
    os += ", .value = ";
    os += ::android::hardware::toString(o.value);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::configstore::V1_0::OptionalBool& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::configstore::V1_0::OptionalBool& lhs, const ::android::hardware::configstore::V1_0::OptionalBool& rhs) {
    if (lhs.specified != rhs.specified) {
        return false;
    }
    if (lhs.value != rhs.value) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::configstore::V1_0::OptionalBool& lhs, const ::android::hardware::configstore::V1_0::OptionalBool& rhs){
    return !(lhs == rhs);
}

static inline std::string toString(const ::android::hardware::configstore::V1_0::OptionalInt32& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".specified = ";
    os += ::android::hardware::toString(o.specified);
    os += ", .value = ";
    os += ::android::hardware::toString(o.value);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::configstore::V1_0::OptionalInt32& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::configstore::V1_0::OptionalInt32& lhs, const ::android::hardware::configstore::V1_0::OptionalInt32& rhs) {
    if (lhs.specified != rhs.specified) {
        return false;
    }
    if (lhs.value != rhs.value) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::configstore::V1_0::OptionalInt32& lhs, const ::android::hardware::configstore::V1_0::OptionalInt32& rhs){
    return !(lhs == rhs);
}

static inline std::string toString(const ::android::hardware::configstore::V1_0::OptionalUInt32& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".specified = ";
    os += ::android::hardware::toString(o.specified);
    os += ", .value = ";
    os += ::android::hardware::toString(o.value);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::configstore::V1_0::OptionalUInt32& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::configstore::V1_0::OptionalUInt32& lhs, const ::android::hardware::configstore::V1_0::OptionalUInt32& rhs) {
    if (lhs.specified != rhs.specified) {
        return false;
    }
    if (lhs.value != rhs.value) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::configstore::V1_0::OptionalUInt32& lhs, const ::android::hardware::configstore::V1_0::OptionalUInt32& rhs){
    return !(lhs == rhs);
}

static inline std::string toString(const ::android::hardware::configstore::V1_0::OptionalInt64& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".specified = ";
    os += ::android::hardware::toString(o.specified);
    os += ", .value = ";
    os += ::android::hardware::toString(o.value);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::configstore::V1_0::OptionalInt64& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::configstore::V1_0::OptionalInt64& lhs, const ::android::hardware::configstore::V1_0::OptionalInt64& rhs) {
    if (lhs.specified != rhs.specified) {
        return false;
    }
    if (lhs.value != rhs.value) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::configstore::V1_0::OptionalInt64& lhs, const ::android::hardware::configstore::V1_0::OptionalInt64& rhs){
    return !(lhs == rhs);
}

static inline std::string toString(const ::android::hardware::configstore::V1_0::OptionalUInt64& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".specified = ";
    os += ::android::hardware::toString(o.specified);
    os += ", .value = ";
    os += ::android::hardware::toString(o.value);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::configstore::V1_0::OptionalUInt64& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::configstore::V1_0::OptionalUInt64& lhs, const ::android::hardware::configstore::V1_0::OptionalUInt64& rhs) {
    if (lhs.specified != rhs.specified) {
        return false;
    }
    if (lhs.value != rhs.value) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::configstore::V1_0::OptionalUInt64& lhs, const ::android::hardware::configstore::V1_0::OptionalUInt64& rhs){
    return !(lhs == rhs);
}

static inline std::string toString(const ::android::hardware::configstore::V1_0::OptionalString& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".specified = ";
    os += ::android::hardware::toString(o.specified);
    os += ", .value = ";
    os += ::android::hardware::toString(o.value);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::configstore::V1_0::OptionalString& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::configstore::V1_0::OptionalString& lhs, const ::android::hardware::configstore::V1_0::OptionalString& rhs) {
    if (lhs.specified != rhs.specified) {
        return false;
    }
    if (lhs.value != rhs.value) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::configstore::V1_0::OptionalString& lhs, const ::android::hardware::configstore::V1_0::OptionalString& rhs){
    return !(lhs == rhs);
}


}  // namespace V1_0
}  // namespace configstore
}  // namespace hardware
}  // namespace android

//
// global type declarations for package
//


#endif  // HIDL_GENERATED_ANDROID_HARDWARE_CONFIGSTORE_V1_0_TYPES_H
