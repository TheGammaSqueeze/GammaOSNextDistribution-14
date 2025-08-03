#ifndef HIDL_GENERATED_ANDROID_HARDWARE_MEMTRACK_V1_0_TYPES_H
#define HIDL_GENERATED_ANDROID_HARDWARE_MEMTRACK_V1_0_TYPES_H

#include <hidl/HidlSupport.h>
#include <hidl/MQDescriptor.h>
#include <utils/NativeHandle.h>
#include <utils/misc.h>

namespace android {
namespace hardware {
namespace memtrack {
namespace V1_0 {

// Forward declaration for forward reference support:
enum class MemtrackFlag : uint32_t;
enum class MemtrackType : uint32_t;
enum class MemtrackStatus : uint32_t;
struct MemtrackRecord;

/**
 * SMAPS_ACCOUNTED/SMAPS_UNACCOUNTED
 * Flags to differentiate memory that can already be accounted for in
 * /proc/<pid>/smaps,
 * (Shared_Clean + Shared_Dirty + Private_Clean + Private_Dirty = Size).
 * In general, memory mapped in to a userspace process is accounted unless
 * it was mapped with remap_pfn_range.
 * Exactly one of these must be set.
 *
 * SHARED/SHARED_PSS/PRIVATE
 * Flags to differentiate memory shared across multiple processes vs. memory
 * used by a single process.
 * If SHARED_PSS flags is used, the memory must be divided by the number of
 * processes holding reference to it (shared / num_processes).
 * Only zero or one of these may be set in a record.
 * If none are set, record is assumed to count shared + private memory.
 *
 * SYSTEM/DEDICATED
 * Flags to differentiate memory taken from the kernel's allocation pool vs.
 * memory that is dedicated to non-kernel allocations, for example a carveout
 * or separate video memory.  Only zero or one of these may be set in a record.
 * If none are set, record is assumed to count system + dedicated memory.
 *
 * NONSECURE/SECURE
 * Flags to differentiate memory accessible by the CPU in non-secure mode vs.
 * memory that is protected.  Only zero or one of these may be set in a record.
 * If none are set, record is assumed to count secure + nonsecure memory.
 */
enum class MemtrackFlag : uint32_t {
    SMAPS_ACCOUNTED = 2u /* 1 << 1 */,
    SMAPS_UNACCOUNTED = 4u /* 1 << 2 */,
    SHARED = 8u /* 1 << 3 */,
    SHARED_PSS = 16u /* 1 << 4 */,
    PRIVATE = 32u /* 1 << 5 */,
    SYSTEM = 64u /* 1 << 6 */,
    DEDICATED = 128u /* 1 << 7 */,
    NONSECURE = 256u /* 1 << 8 */,
    SECURE = 512u /* 1 << 9 */,
};

/**
 * Tags which define the usage of the memory buffers.
 */
enum class MemtrackType : uint32_t {
    OTHER = 0u,
    GL = 1u,
    GRAPHICS = 2u,
    MULTIMEDIA = 3u,
    CAMERA = 4u,
    NUM_TYPES = 5u /* ::android::hardware::memtrack::V1_0::MemtrackType.CAMERA implicitly + 1 */,
};

enum class MemtrackStatus : uint32_t {
    SUCCESS = 0u,
    MEMORY_TRACKING_NOT_SUPPORTED = 1u,
    TYPE_NOT_SUPPORTED = 2u,
};

/*
 * A vector of MemtrackRecord is returned by the function getMemory().
 * Each record consists of the size of the memory used by the process and
 * flags indicate the all the MemtrackFlag that are valid for this record.
 * see getMemory() comments for further details.
 */
struct MemtrackRecord final {
    uint64_t sizeInBytes __attribute__ ((aligned(8)));
    /**
     * This is the bitfield for the MemtrackFlag indicating all the flags that
     * are valid for this record.
     */
    uint32_t flags __attribute__ ((aligned(4)));
};

static_assert(offsetof(::android::hardware::memtrack::V1_0::MemtrackRecord, sizeInBytes) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::memtrack::V1_0::MemtrackRecord, flags) == 8, "wrong offset");
static_assert(sizeof(::android::hardware::memtrack::V1_0::MemtrackRecord) == 16, "wrong size");
static_assert(__alignof(::android::hardware::memtrack::V1_0::MemtrackRecord) == 8, "wrong alignment");

//
// type declarations for package
//

template<typename>
static inline std::string toString(uint32_t o);
static inline std::string toString(::android::hardware::memtrack::V1_0::MemtrackFlag o);
static inline void PrintTo(::android::hardware::memtrack::V1_0::MemtrackFlag o, ::std::ostream* os);
constexpr uint32_t operator|(const ::android::hardware::memtrack::V1_0::MemtrackFlag lhs, const ::android::hardware::memtrack::V1_0::MemtrackFlag rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const uint32_t lhs, const ::android::hardware::memtrack::V1_0::MemtrackFlag rhs) {
    return static_cast<uint32_t>(lhs | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const ::android::hardware::memtrack::V1_0::MemtrackFlag lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | rhs);
}
constexpr uint32_t operator&(const ::android::hardware::memtrack::V1_0::MemtrackFlag lhs, const ::android::hardware::memtrack::V1_0::MemtrackFlag rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const uint32_t lhs, const ::android::hardware::memtrack::V1_0::MemtrackFlag rhs) {
    return static_cast<uint32_t>(lhs & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const ::android::hardware::memtrack::V1_0::MemtrackFlag lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & rhs);
}
constexpr uint32_t &operator|=(uint32_t& v, const ::android::hardware::memtrack::V1_0::MemtrackFlag e) {
    v |= static_cast<uint32_t>(e);
    return v;
}
constexpr uint32_t &operator&=(uint32_t& v, const ::android::hardware::memtrack::V1_0::MemtrackFlag e) {
    v &= static_cast<uint32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(uint32_t o);
static inline std::string toString(::android::hardware::memtrack::V1_0::MemtrackType o);
static inline void PrintTo(::android::hardware::memtrack::V1_0::MemtrackType o, ::std::ostream* os);
constexpr uint32_t operator|(const ::android::hardware::memtrack::V1_0::MemtrackType lhs, const ::android::hardware::memtrack::V1_0::MemtrackType rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const uint32_t lhs, const ::android::hardware::memtrack::V1_0::MemtrackType rhs) {
    return static_cast<uint32_t>(lhs | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const ::android::hardware::memtrack::V1_0::MemtrackType lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | rhs);
}
constexpr uint32_t operator&(const ::android::hardware::memtrack::V1_0::MemtrackType lhs, const ::android::hardware::memtrack::V1_0::MemtrackType rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const uint32_t lhs, const ::android::hardware::memtrack::V1_0::MemtrackType rhs) {
    return static_cast<uint32_t>(lhs & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const ::android::hardware::memtrack::V1_0::MemtrackType lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & rhs);
}
constexpr uint32_t &operator|=(uint32_t& v, const ::android::hardware::memtrack::V1_0::MemtrackType e) {
    v |= static_cast<uint32_t>(e);
    return v;
}
constexpr uint32_t &operator&=(uint32_t& v, const ::android::hardware::memtrack::V1_0::MemtrackType e) {
    v &= static_cast<uint32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(uint32_t o);
static inline std::string toString(::android::hardware::memtrack::V1_0::MemtrackStatus o);
static inline void PrintTo(::android::hardware::memtrack::V1_0::MemtrackStatus o, ::std::ostream* os);
constexpr uint32_t operator|(const ::android::hardware::memtrack::V1_0::MemtrackStatus lhs, const ::android::hardware::memtrack::V1_0::MemtrackStatus rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const uint32_t lhs, const ::android::hardware::memtrack::V1_0::MemtrackStatus rhs) {
    return static_cast<uint32_t>(lhs | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const ::android::hardware::memtrack::V1_0::MemtrackStatus lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | rhs);
}
constexpr uint32_t operator&(const ::android::hardware::memtrack::V1_0::MemtrackStatus lhs, const ::android::hardware::memtrack::V1_0::MemtrackStatus rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const uint32_t lhs, const ::android::hardware::memtrack::V1_0::MemtrackStatus rhs) {
    return static_cast<uint32_t>(lhs & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const ::android::hardware::memtrack::V1_0::MemtrackStatus lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & rhs);
}
constexpr uint32_t &operator|=(uint32_t& v, const ::android::hardware::memtrack::V1_0::MemtrackStatus e) {
    v |= static_cast<uint32_t>(e);
    return v;
}
constexpr uint32_t &operator&=(uint32_t& v, const ::android::hardware::memtrack::V1_0::MemtrackStatus e) {
    v &= static_cast<uint32_t>(e);
    return v;
}

static inline std::string toString(const ::android::hardware::memtrack::V1_0::MemtrackRecord& o);
static inline void PrintTo(const ::android::hardware::memtrack::V1_0::MemtrackRecord& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::memtrack::V1_0::MemtrackRecord& lhs, const ::android::hardware::memtrack::V1_0::MemtrackRecord& rhs);
static inline bool operator!=(const ::android::hardware::memtrack::V1_0::MemtrackRecord& lhs, const ::android::hardware::memtrack::V1_0::MemtrackRecord& rhs);

//
// type header definitions for package
//

template<>
inline std::string toString<::android::hardware::memtrack::V1_0::MemtrackFlag>(uint32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::memtrack::V1_0::MemtrackFlag> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::memtrack::V1_0::MemtrackFlag::SMAPS_ACCOUNTED) == static_cast<uint32_t>(::android::hardware::memtrack::V1_0::MemtrackFlag::SMAPS_ACCOUNTED)) {
        os += (first ? "" : " | ");
        os += "SMAPS_ACCOUNTED";
        first = false;
        flipped |= ::android::hardware::memtrack::V1_0::MemtrackFlag::SMAPS_ACCOUNTED;
    }
    if ((o & ::android::hardware::memtrack::V1_0::MemtrackFlag::SMAPS_UNACCOUNTED) == static_cast<uint32_t>(::android::hardware::memtrack::V1_0::MemtrackFlag::SMAPS_UNACCOUNTED)) {
        os += (first ? "" : " | ");
        os += "SMAPS_UNACCOUNTED";
        first = false;
        flipped |= ::android::hardware::memtrack::V1_0::MemtrackFlag::SMAPS_UNACCOUNTED;
    }
    if ((o & ::android::hardware::memtrack::V1_0::MemtrackFlag::SHARED) == static_cast<uint32_t>(::android::hardware::memtrack::V1_0::MemtrackFlag::SHARED)) {
        os += (first ? "" : " | ");
        os += "SHARED";
        first = false;
        flipped |= ::android::hardware::memtrack::V1_0::MemtrackFlag::SHARED;
    }
    if ((o & ::android::hardware::memtrack::V1_0::MemtrackFlag::SHARED_PSS) == static_cast<uint32_t>(::android::hardware::memtrack::V1_0::MemtrackFlag::SHARED_PSS)) {
        os += (first ? "" : " | ");
        os += "SHARED_PSS";
        first = false;
        flipped |= ::android::hardware::memtrack::V1_0::MemtrackFlag::SHARED_PSS;
    }
    if ((o & ::android::hardware::memtrack::V1_0::MemtrackFlag::PRIVATE) == static_cast<uint32_t>(::android::hardware::memtrack::V1_0::MemtrackFlag::PRIVATE)) {
        os += (first ? "" : " | ");
        os += "PRIVATE";
        first = false;
        flipped |= ::android::hardware::memtrack::V1_0::MemtrackFlag::PRIVATE;
    }
    if ((o & ::android::hardware::memtrack::V1_0::MemtrackFlag::SYSTEM) == static_cast<uint32_t>(::android::hardware::memtrack::V1_0::MemtrackFlag::SYSTEM)) {
        os += (first ? "" : " | ");
        os += "SYSTEM";
        first = false;
        flipped |= ::android::hardware::memtrack::V1_0::MemtrackFlag::SYSTEM;
    }
    if ((o & ::android::hardware::memtrack::V1_0::MemtrackFlag::DEDICATED) == static_cast<uint32_t>(::android::hardware::memtrack::V1_0::MemtrackFlag::DEDICATED)) {
        os += (first ? "" : " | ");
        os += "DEDICATED";
        first = false;
        flipped |= ::android::hardware::memtrack::V1_0::MemtrackFlag::DEDICATED;
    }
    if ((o & ::android::hardware::memtrack::V1_0::MemtrackFlag::NONSECURE) == static_cast<uint32_t>(::android::hardware::memtrack::V1_0::MemtrackFlag::NONSECURE)) {
        os += (first ? "" : " | ");
        os += "NONSECURE";
        first = false;
        flipped |= ::android::hardware::memtrack::V1_0::MemtrackFlag::NONSECURE;
    }
    if ((o & ::android::hardware::memtrack::V1_0::MemtrackFlag::SECURE) == static_cast<uint32_t>(::android::hardware::memtrack::V1_0::MemtrackFlag::SECURE)) {
        os += (first ? "" : " | ");
        os += "SECURE";
        first = false;
        flipped |= ::android::hardware::memtrack::V1_0::MemtrackFlag::SECURE;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::memtrack::V1_0::MemtrackFlag o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::memtrack::V1_0::MemtrackFlag::SMAPS_ACCOUNTED) {
        return "SMAPS_ACCOUNTED";
    }
    if (o == ::android::hardware::memtrack::V1_0::MemtrackFlag::SMAPS_UNACCOUNTED) {
        return "SMAPS_UNACCOUNTED";
    }
    if (o == ::android::hardware::memtrack::V1_0::MemtrackFlag::SHARED) {
        return "SHARED";
    }
    if (o == ::android::hardware::memtrack::V1_0::MemtrackFlag::SHARED_PSS) {
        return "SHARED_PSS";
    }
    if (o == ::android::hardware::memtrack::V1_0::MemtrackFlag::PRIVATE) {
        return "PRIVATE";
    }
    if (o == ::android::hardware::memtrack::V1_0::MemtrackFlag::SYSTEM) {
        return "SYSTEM";
    }
    if (o == ::android::hardware::memtrack::V1_0::MemtrackFlag::DEDICATED) {
        return "DEDICATED";
    }
    if (o == ::android::hardware::memtrack::V1_0::MemtrackFlag::NONSECURE) {
        return "NONSECURE";
    }
    if (o == ::android::hardware::memtrack::V1_0::MemtrackFlag::SECURE) {
        return "SECURE";
    }
    std::string os;
    os += toHexString(static_cast<uint32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::memtrack::V1_0::MemtrackFlag o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::memtrack::V1_0::MemtrackType>(uint32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::memtrack::V1_0::MemtrackType> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::memtrack::V1_0::MemtrackType::OTHER) == static_cast<uint32_t>(::android::hardware::memtrack::V1_0::MemtrackType::OTHER)) {
        os += (first ? "" : " | ");
        os += "OTHER";
        first = false;
        flipped |= ::android::hardware::memtrack::V1_0::MemtrackType::OTHER;
    }
    if ((o & ::android::hardware::memtrack::V1_0::MemtrackType::GL) == static_cast<uint32_t>(::android::hardware::memtrack::V1_0::MemtrackType::GL)) {
        os += (first ? "" : " | ");
        os += "GL";
        first = false;
        flipped |= ::android::hardware::memtrack::V1_0::MemtrackType::GL;
    }
    if ((o & ::android::hardware::memtrack::V1_0::MemtrackType::GRAPHICS) == static_cast<uint32_t>(::android::hardware::memtrack::V1_0::MemtrackType::GRAPHICS)) {
        os += (first ? "" : " | ");
        os += "GRAPHICS";
        first = false;
        flipped |= ::android::hardware::memtrack::V1_0::MemtrackType::GRAPHICS;
    }
    if ((o & ::android::hardware::memtrack::V1_0::MemtrackType::MULTIMEDIA) == static_cast<uint32_t>(::android::hardware::memtrack::V1_0::MemtrackType::MULTIMEDIA)) {
        os += (first ? "" : " | ");
        os += "MULTIMEDIA";
        first = false;
        flipped |= ::android::hardware::memtrack::V1_0::MemtrackType::MULTIMEDIA;
    }
    if ((o & ::android::hardware::memtrack::V1_0::MemtrackType::CAMERA) == static_cast<uint32_t>(::android::hardware::memtrack::V1_0::MemtrackType::CAMERA)) {
        os += (first ? "" : " | ");
        os += "CAMERA";
        first = false;
        flipped |= ::android::hardware::memtrack::V1_0::MemtrackType::CAMERA;
    }
    if ((o & ::android::hardware::memtrack::V1_0::MemtrackType::NUM_TYPES) == static_cast<uint32_t>(::android::hardware::memtrack::V1_0::MemtrackType::NUM_TYPES)) {
        os += (first ? "" : " | ");
        os += "NUM_TYPES";
        first = false;
        flipped |= ::android::hardware::memtrack::V1_0::MemtrackType::NUM_TYPES;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::memtrack::V1_0::MemtrackType o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::memtrack::V1_0::MemtrackType::OTHER) {
        return "OTHER";
    }
    if (o == ::android::hardware::memtrack::V1_0::MemtrackType::GL) {
        return "GL";
    }
    if (o == ::android::hardware::memtrack::V1_0::MemtrackType::GRAPHICS) {
        return "GRAPHICS";
    }
    if (o == ::android::hardware::memtrack::V1_0::MemtrackType::MULTIMEDIA) {
        return "MULTIMEDIA";
    }
    if (o == ::android::hardware::memtrack::V1_0::MemtrackType::CAMERA) {
        return "CAMERA";
    }
    if (o == ::android::hardware::memtrack::V1_0::MemtrackType::NUM_TYPES) {
        return "NUM_TYPES";
    }
    std::string os;
    os += toHexString(static_cast<uint32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::memtrack::V1_0::MemtrackType o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::memtrack::V1_0::MemtrackStatus>(uint32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::memtrack::V1_0::MemtrackStatus> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::memtrack::V1_0::MemtrackStatus::SUCCESS) == static_cast<uint32_t>(::android::hardware::memtrack::V1_0::MemtrackStatus::SUCCESS)) {
        os += (first ? "" : " | ");
        os += "SUCCESS";
        first = false;
        flipped |= ::android::hardware::memtrack::V1_0::MemtrackStatus::SUCCESS;
    }
    if ((o & ::android::hardware::memtrack::V1_0::MemtrackStatus::MEMORY_TRACKING_NOT_SUPPORTED) == static_cast<uint32_t>(::android::hardware::memtrack::V1_0::MemtrackStatus::MEMORY_TRACKING_NOT_SUPPORTED)) {
        os += (first ? "" : " | ");
        os += "MEMORY_TRACKING_NOT_SUPPORTED";
        first = false;
        flipped |= ::android::hardware::memtrack::V1_0::MemtrackStatus::MEMORY_TRACKING_NOT_SUPPORTED;
    }
    if ((o & ::android::hardware::memtrack::V1_0::MemtrackStatus::TYPE_NOT_SUPPORTED) == static_cast<uint32_t>(::android::hardware::memtrack::V1_0::MemtrackStatus::TYPE_NOT_SUPPORTED)) {
        os += (first ? "" : " | ");
        os += "TYPE_NOT_SUPPORTED";
        first = false;
        flipped |= ::android::hardware::memtrack::V1_0::MemtrackStatus::TYPE_NOT_SUPPORTED;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::memtrack::V1_0::MemtrackStatus o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::memtrack::V1_0::MemtrackStatus::SUCCESS) {
        return "SUCCESS";
    }
    if (o == ::android::hardware::memtrack::V1_0::MemtrackStatus::MEMORY_TRACKING_NOT_SUPPORTED) {
        return "MEMORY_TRACKING_NOT_SUPPORTED";
    }
    if (o == ::android::hardware::memtrack::V1_0::MemtrackStatus::TYPE_NOT_SUPPORTED) {
        return "TYPE_NOT_SUPPORTED";
    }
    std::string os;
    os += toHexString(static_cast<uint32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::memtrack::V1_0::MemtrackStatus o, ::std::ostream* os) {
    *os << toString(o);
}

static inline std::string toString(const ::android::hardware::memtrack::V1_0::MemtrackRecord& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".sizeInBytes = ";
    os += ::android::hardware::toString(o.sizeInBytes);
    os += ", .flags = ";
    os += ::android::hardware::toString(o.flags);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::memtrack::V1_0::MemtrackRecord& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::memtrack::V1_0::MemtrackRecord& lhs, const ::android::hardware::memtrack::V1_0::MemtrackRecord& rhs) {
    if (lhs.sizeInBytes != rhs.sizeInBytes) {
        return false;
    }
    if (lhs.flags != rhs.flags) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::memtrack::V1_0::MemtrackRecord& lhs, const ::android::hardware::memtrack::V1_0::MemtrackRecord& rhs){
    return !(lhs == rhs);
}


}  // namespace V1_0
}  // namespace memtrack
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
template<> inline constexpr std::array<::android::hardware::memtrack::V1_0::MemtrackFlag, 9> hidl_enum_values<::android::hardware::memtrack::V1_0::MemtrackFlag> = {
    ::android::hardware::memtrack::V1_0::MemtrackFlag::SMAPS_ACCOUNTED,
    ::android::hardware::memtrack::V1_0::MemtrackFlag::SMAPS_UNACCOUNTED,
    ::android::hardware::memtrack::V1_0::MemtrackFlag::SHARED,
    ::android::hardware::memtrack::V1_0::MemtrackFlag::SHARED_PSS,
    ::android::hardware::memtrack::V1_0::MemtrackFlag::PRIVATE,
    ::android::hardware::memtrack::V1_0::MemtrackFlag::SYSTEM,
    ::android::hardware::memtrack::V1_0::MemtrackFlag::DEDICATED,
    ::android::hardware::memtrack::V1_0::MemtrackFlag::NONSECURE,
    ::android::hardware::memtrack::V1_0::MemtrackFlag::SECURE,
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
template<> inline constexpr std::array<::android::hardware::memtrack::V1_0::MemtrackType, 6> hidl_enum_values<::android::hardware::memtrack::V1_0::MemtrackType> = {
    ::android::hardware::memtrack::V1_0::MemtrackType::OTHER,
    ::android::hardware::memtrack::V1_0::MemtrackType::GL,
    ::android::hardware::memtrack::V1_0::MemtrackType::GRAPHICS,
    ::android::hardware::memtrack::V1_0::MemtrackType::MULTIMEDIA,
    ::android::hardware::memtrack::V1_0::MemtrackType::CAMERA,
    ::android::hardware::memtrack::V1_0::MemtrackType::NUM_TYPES,
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
template<> inline constexpr std::array<::android::hardware::memtrack::V1_0::MemtrackStatus, 3> hidl_enum_values<::android::hardware::memtrack::V1_0::MemtrackStatus> = {
    ::android::hardware::memtrack::V1_0::MemtrackStatus::SUCCESS,
    ::android::hardware::memtrack::V1_0::MemtrackStatus::MEMORY_TRACKING_NOT_SUPPORTED,
    ::android::hardware::memtrack::V1_0::MemtrackStatus::TYPE_NOT_SUPPORTED,
};
#pragma clang diagnostic pop
}  // namespace details
}  // namespace hardware
}  // namespace android


#endif  // HIDL_GENERATED_ANDROID_HARDWARE_MEMTRACK_V1_0_TYPES_H
