#ifndef HIDL_GENERATED_ANDROID_HARDWARE_CAS_NATIVE_V1_0_TYPES_H
#define HIDL_GENERATED_ANDROID_HARDWARE_CAS_NATIVE_V1_0_TYPES_H

#include <hidl/HidlSupport.h>
#include <hidl/MQDescriptor.h>
#include <utils/NativeHandle.h>
#include <utils/misc.h>

namespace android {
namespace hardware {
namespace cas {
namespace native {
namespace V1_0 {

// Forward declaration for forward reference support:
enum class ScramblingControl : uint32_t;
struct SubSample;
struct SharedBuffer;
enum class BufferType : uint32_t;
struct DestinationBuffer;

/**
 * Enumerates the keys used to scramble the content.
 */
enum class ScramblingControl : uint32_t {
    UNSCRAMBLED = 0u,
    RESERVED = 1u,
    EVENKEY = 2u,
    ODDKEY = 3u,
};

/**
 * A subsample consists of some number of bytes of clear (unscrambled)
 * data followed by a number of bytes of scrambled data.
 */
struct SubSample final {
    uint32_t numBytesOfClearData __attribute__ ((aligned(4)));
    uint32_t numBytesOfEncryptedData __attribute__ ((aligned(4)));
};

static_assert(offsetof(::android::hardware::cas::native::V1_0::SubSample, numBytesOfClearData) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::cas::native::V1_0::SubSample, numBytesOfEncryptedData) == 4, "wrong offset");
static_assert(sizeof(::android::hardware::cas::native::V1_0::SubSample) == 8, "wrong size");
static_assert(__alignof(::android::hardware::cas::native::V1_0::SubSample) == 4, "wrong alignment");

/**
 * SharedBuffer describes a shared buffer which is defined by a heapBase, an
 * offset and a size. The offset is relative to the shared memory base for the
 * memory region identified by heapBase.
 */
struct SharedBuffer final {
    /**
     * The shared memory base handle
     */
    ::android::hardware::hidl_memory heapBase __attribute__ ((aligned(8)));
    /**
     * The offset from the shared memory base
     */
    uint64_t offset __attribute__ ((aligned(8)));
    /**
     * The size of the shared buffer in bytes
     */
    uint64_t size __attribute__ ((aligned(8)));
};

static_assert(offsetof(::android::hardware::cas::native::V1_0::SharedBuffer, heapBase) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::cas::native::V1_0::SharedBuffer, offset) == 40, "wrong offset");
static_assert(offsetof(::android::hardware::cas::native::V1_0::SharedBuffer, size) == 48, "wrong offset");
static_assert(sizeof(::android::hardware::cas::native::V1_0::SharedBuffer) == 56, "wrong size");
static_assert(__alignof(::android::hardware::cas::native::V1_0::SharedBuffer) == 8, "wrong alignment");

/**
 * A descrambling destination buffer can be either normal user-space shared
 * memory for the non-secure descrambling case, or it can be a secure buffer
 * which is referenced by a native-handle. The native handle is allocated
 * by the vendor's buffer allocator.
 */
enum class BufferType : uint32_t {
    SHARED_MEMORY = 0u,
    NATIVE_HANDLE = 1u,
};

struct DestinationBuffer final {
    /**
     * The type of the buffer
     */
    ::android::hardware::cas::native::V1_0::BufferType type __attribute__ ((aligned(4)));
    /**
     * If type == SHARED_MEMORY, the descrambled data must be written
     * to user-space non-secure shared memory.
     */
    ::android::hardware::cas::native::V1_0::SharedBuffer nonsecureMemory __attribute__ ((aligned(8)));
    /**
     * If type == NATIVE_HANDLE, the descrambled data must be written
     * to secure memory referenced by the vendor's buffer allocator.
     */
    ::android::hardware::hidl_handle secureMemory __attribute__ ((aligned(8)));
};

static_assert(offsetof(::android::hardware::cas::native::V1_0::DestinationBuffer, type) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::cas::native::V1_0::DestinationBuffer, nonsecureMemory) == 8, "wrong offset");
static_assert(offsetof(::android::hardware::cas::native::V1_0::DestinationBuffer, secureMemory) == 64, "wrong offset");
static_assert(sizeof(::android::hardware::cas::native::V1_0::DestinationBuffer) == 80, "wrong size");
static_assert(__alignof(::android::hardware::cas::native::V1_0::DestinationBuffer) == 8, "wrong alignment");

//
// type declarations for package
//

template<typename>
static inline std::string toString(uint32_t o);
static inline std::string toString(::android::hardware::cas::native::V1_0::ScramblingControl o);
static inline void PrintTo(::android::hardware::cas::native::V1_0::ScramblingControl o, ::std::ostream* os);
constexpr uint32_t operator|(const ::android::hardware::cas::native::V1_0::ScramblingControl lhs, const ::android::hardware::cas::native::V1_0::ScramblingControl rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const uint32_t lhs, const ::android::hardware::cas::native::V1_0::ScramblingControl rhs) {
    return static_cast<uint32_t>(lhs | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const ::android::hardware::cas::native::V1_0::ScramblingControl lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | rhs);
}
constexpr uint32_t operator&(const ::android::hardware::cas::native::V1_0::ScramblingControl lhs, const ::android::hardware::cas::native::V1_0::ScramblingControl rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const uint32_t lhs, const ::android::hardware::cas::native::V1_0::ScramblingControl rhs) {
    return static_cast<uint32_t>(lhs & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const ::android::hardware::cas::native::V1_0::ScramblingControl lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & rhs);
}
constexpr uint32_t &operator|=(uint32_t& v, const ::android::hardware::cas::native::V1_0::ScramblingControl e) {
    v |= static_cast<uint32_t>(e);
    return v;
}
constexpr uint32_t &operator&=(uint32_t& v, const ::android::hardware::cas::native::V1_0::ScramblingControl e) {
    v &= static_cast<uint32_t>(e);
    return v;
}

static inline std::string toString(const ::android::hardware::cas::native::V1_0::SubSample& o);
static inline void PrintTo(const ::android::hardware::cas::native::V1_0::SubSample& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::cas::native::V1_0::SubSample& lhs, const ::android::hardware::cas::native::V1_0::SubSample& rhs);
static inline bool operator!=(const ::android::hardware::cas::native::V1_0::SubSample& lhs, const ::android::hardware::cas::native::V1_0::SubSample& rhs);

static inline std::string toString(const ::android::hardware::cas::native::V1_0::SharedBuffer& o);
static inline void PrintTo(const ::android::hardware::cas::native::V1_0::SharedBuffer& o, ::std::ostream*);
// operator== and operator!= are not generated for SharedBuffer

template<typename>
static inline std::string toString(uint32_t o);
static inline std::string toString(::android::hardware::cas::native::V1_0::BufferType o);
static inline void PrintTo(::android::hardware::cas::native::V1_0::BufferType o, ::std::ostream* os);
constexpr uint32_t operator|(const ::android::hardware::cas::native::V1_0::BufferType lhs, const ::android::hardware::cas::native::V1_0::BufferType rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const uint32_t lhs, const ::android::hardware::cas::native::V1_0::BufferType rhs) {
    return static_cast<uint32_t>(lhs | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const ::android::hardware::cas::native::V1_0::BufferType lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | rhs);
}
constexpr uint32_t operator&(const ::android::hardware::cas::native::V1_0::BufferType lhs, const ::android::hardware::cas::native::V1_0::BufferType rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const uint32_t lhs, const ::android::hardware::cas::native::V1_0::BufferType rhs) {
    return static_cast<uint32_t>(lhs & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const ::android::hardware::cas::native::V1_0::BufferType lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & rhs);
}
constexpr uint32_t &operator|=(uint32_t& v, const ::android::hardware::cas::native::V1_0::BufferType e) {
    v |= static_cast<uint32_t>(e);
    return v;
}
constexpr uint32_t &operator&=(uint32_t& v, const ::android::hardware::cas::native::V1_0::BufferType e) {
    v &= static_cast<uint32_t>(e);
    return v;
}

static inline std::string toString(const ::android::hardware::cas::native::V1_0::DestinationBuffer& o);
static inline void PrintTo(const ::android::hardware::cas::native::V1_0::DestinationBuffer& o, ::std::ostream*);
// operator== and operator!= are not generated for DestinationBuffer

//
// type header definitions for package
//

template<>
inline std::string toString<::android::hardware::cas::native::V1_0::ScramblingControl>(uint32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::cas::native::V1_0::ScramblingControl> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::cas::native::V1_0::ScramblingControl::UNSCRAMBLED) == static_cast<uint32_t>(::android::hardware::cas::native::V1_0::ScramblingControl::UNSCRAMBLED)) {
        os += (first ? "" : " | ");
        os += "UNSCRAMBLED";
        first = false;
        flipped |= ::android::hardware::cas::native::V1_0::ScramblingControl::UNSCRAMBLED;
    }
    if ((o & ::android::hardware::cas::native::V1_0::ScramblingControl::RESERVED) == static_cast<uint32_t>(::android::hardware::cas::native::V1_0::ScramblingControl::RESERVED)) {
        os += (first ? "" : " | ");
        os += "RESERVED";
        first = false;
        flipped |= ::android::hardware::cas::native::V1_0::ScramblingControl::RESERVED;
    }
    if ((o & ::android::hardware::cas::native::V1_0::ScramblingControl::EVENKEY) == static_cast<uint32_t>(::android::hardware::cas::native::V1_0::ScramblingControl::EVENKEY)) {
        os += (first ? "" : " | ");
        os += "EVENKEY";
        first = false;
        flipped |= ::android::hardware::cas::native::V1_0::ScramblingControl::EVENKEY;
    }
    if ((o & ::android::hardware::cas::native::V1_0::ScramblingControl::ODDKEY) == static_cast<uint32_t>(::android::hardware::cas::native::V1_0::ScramblingControl::ODDKEY)) {
        os += (first ? "" : " | ");
        os += "ODDKEY";
        first = false;
        flipped |= ::android::hardware::cas::native::V1_0::ScramblingControl::ODDKEY;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::cas::native::V1_0::ScramblingControl o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::cas::native::V1_0::ScramblingControl::UNSCRAMBLED) {
        return "UNSCRAMBLED";
    }
    if (o == ::android::hardware::cas::native::V1_0::ScramblingControl::RESERVED) {
        return "RESERVED";
    }
    if (o == ::android::hardware::cas::native::V1_0::ScramblingControl::EVENKEY) {
        return "EVENKEY";
    }
    if (o == ::android::hardware::cas::native::V1_0::ScramblingControl::ODDKEY) {
        return "ODDKEY";
    }
    std::string os;
    os += toHexString(static_cast<uint32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::cas::native::V1_0::ScramblingControl o, ::std::ostream* os) {
    *os << toString(o);
}

static inline std::string toString(const ::android::hardware::cas::native::V1_0::SubSample& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".numBytesOfClearData = ";
    os += ::android::hardware::toString(o.numBytesOfClearData);
    os += ", .numBytesOfEncryptedData = ";
    os += ::android::hardware::toString(o.numBytesOfEncryptedData);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::cas::native::V1_0::SubSample& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::cas::native::V1_0::SubSample& lhs, const ::android::hardware::cas::native::V1_0::SubSample& rhs) {
    if (lhs.numBytesOfClearData != rhs.numBytesOfClearData) {
        return false;
    }
    if (lhs.numBytesOfEncryptedData != rhs.numBytesOfEncryptedData) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::cas::native::V1_0::SubSample& lhs, const ::android::hardware::cas::native::V1_0::SubSample& rhs){
    return !(lhs == rhs);
}

static inline std::string toString(const ::android::hardware::cas::native::V1_0::SharedBuffer& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".heapBase = ";
    os += ::android::hardware::toString(o.heapBase);
    os += ", .offset = ";
    os += ::android::hardware::toString(o.offset);
    os += ", .size = ";
    os += ::android::hardware::toString(o.size);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::cas::native::V1_0::SharedBuffer& o, ::std::ostream* os) {
    *os << toString(o);
}

// operator== and operator!= are not generated for SharedBuffer

template<>
inline std::string toString<::android::hardware::cas::native::V1_0::BufferType>(uint32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::cas::native::V1_0::BufferType> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::cas::native::V1_0::BufferType::SHARED_MEMORY) == static_cast<uint32_t>(::android::hardware::cas::native::V1_0::BufferType::SHARED_MEMORY)) {
        os += (first ? "" : " | ");
        os += "SHARED_MEMORY";
        first = false;
        flipped |= ::android::hardware::cas::native::V1_0::BufferType::SHARED_MEMORY;
    }
    if ((o & ::android::hardware::cas::native::V1_0::BufferType::NATIVE_HANDLE) == static_cast<uint32_t>(::android::hardware::cas::native::V1_0::BufferType::NATIVE_HANDLE)) {
        os += (first ? "" : " | ");
        os += "NATIVE_HANDLE";
        first = false;
        flipped |= ::android::hardware::cas::native::V1_0::BufferType::NATIVE_HANDLE;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::cas::native::V1_0::BufferType o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::cas::native::V1_0::BufferType::SHARED_MEMORY) {
        return "SHARED_MEMORY";
    }
    if (o == ::android::hardware::cas::native::V1_0::BufferType::NATIVE_HANDLE) {
        return "NATIVE_HANDLE";
    }
    std::string os;
    os += toHexString(static_cast<uint32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::cas::native::V1_0::BufferType o, ::std::ostream* os) {
    *os << toString(o);
}

static inline std::string toString(const ::android::hardware::cas::native::V1_0::DestinationBuffer& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".type = ";
    os += ::android::hardware::cas::native::V1_0::toString(o.type);
    os += ", .nonsecureMemory = ";
    os += ::android::hardware::cas::native::V1_0::toString(o.nonsecureMemory);
    os += ", .secureMemory = ";
    os += ::android::hardware::toString(o.secureMemory);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::cas::native::V1_0::DestinationBuffer& o, ::std::ostream* os) {
    *os << toString(o);
}

// operator== and operator!= are not generated for DestinationBuffer


}  // namespace V1_0
}  // namespace native
}  // namespace cas
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
template<> inline constexpr std::array<::android::hardware::cas::native::V1_0::ScramblingControl, 4> hidl_enum_values<::android::hardware::cas::native::V1_0::ScramblingControl> = {
    ::android::hardware::cas::native::V1_0::ScramblingControl::UNSCRAMBLED,
    ::android::hardware::cas::native::V1_0::ScramblingControl::RESERVED,
    ::android::hardware::cas::native::V1_0::ScramblingControl::EVENKEY,
    ::android::hardware::cas::native::V1_0::ScramblingControl::ODDKEY,
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
template<> inline constexpr std::array<::android::hardware::cas::native::V1_0::BufferType, 2> hidl_enum_values<::android::hardware::cas::native::V1_0::BufferType> = {
    ::android::hardware::cas::native::V1_0::BufferType::SHARED_MEMORY,
    ::android::hardware::cas::native::V1_0::BufferType::NATIVE_HANDLE,
};
#pragma clang diagnostic pop
}  // namespace details
}  // namespace hardware
}  // namespace android


#endif  // HIDL_GENERATED_ANDROID_HARDWARE_CAS_NATIVE_V1_0_TYPES_H
