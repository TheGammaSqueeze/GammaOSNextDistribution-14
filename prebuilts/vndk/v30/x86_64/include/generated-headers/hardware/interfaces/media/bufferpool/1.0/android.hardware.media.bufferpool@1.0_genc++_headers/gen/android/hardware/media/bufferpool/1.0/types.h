#ifndef HIDL_GENERATED_ANDROID_HARDWARE_MEDIA_BUFFERPOOL_V1_0_TYPES_H
#define HIDL_GENERATED_ANDROID_HARDWARE_MEDIA_BUFFERPOOL_V1_0_TYPES_H

#include <hidl/HidlSupport.h>
#include <hidl/MQDescriptor.h>
#include <utils/NativeHandle.h>
#include <utils/misc.h>

namespace android {
namespace hardware {
namespace media {
namespace bufferpool {
namespace V1_0 {

// Forward declaration for forward reference support:
enum class ResultStatus : int32_t;
struct Buffer;
enum class BufferStatus : int32_t;
struct BufferStatusMessage;

enum class ResultStatus : int32_t {
    OK = 0,
    NO_MEMORY = 1,
    ALREADY_EXISTS = 2,
    NOT_FOUND = 3,
    CRITICAL_ERROR = 4,
};

/**
 * Generic buffer for fast recycling for media/stagefright.
 *
 * During media pipeline buffer references are created, shared and
 * destroyed frequently. The underlying buffers are allocated on demand
 * by a buffer pool, and are recycled to the buffer pool when they are
 * no longer referenced by the clients.
 *
 * E.g. ion or gralloc buffer
 */
struct Buffer final {
    uint32_t id __attribute__ ((aligned(4)));
    ::android::hardware::hidl_handle buffer __attribute__ ((aligned(8)));
};

static_assert(offsetof(::android::hardware::media::bufferpool::V1_0::Buffer, id) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::media::bufferpool::V1_0::Buffer, buffer) == 8, "wrong offset");
static_assert(sizeof(::android::hardware::media::bufferpool::V1_0::Buffer) == 24, "wrong size");
static_assert(__alignof(::android::hardware::media::bufferpool::V1_0::Buffer) == 8, "wrong alignment");

/**
 * Buffer ownership status for the specified client.
 * Buffer transfer status for the specified buffer transafer transaction.
 * BufferStatus is posted along with BufferStatusMessage from a client to
 * the buffer pool for synchronization after status change.
 */
enum class BufferStatus : int32_t {
    /**
     * No longer used by the specified client.
     */
    NOT_USED = 0,
    /**
     * Buffer is acquired by the specified client.
     */
    USED = 1,
    /**
     * Buffer is sent by the specified client.
     */
    TRANSFER_TO = 2,
    /**
     * Buffer transfer is acked by the receiver client.
     */
    TRANSFER_FROM = 3,
    /**
     * Buffer transfer is timed out by receiver client.
     */
    TRANSFER_TIMEOUT = 4,
    /**
     * Buffer transfer is not acked by the receiver.
     */
    TRANSFER_LOST = 5,
    /**
     * Buffer fetch request from the client.
     */
    TRANSFER_FETCH = 6,
    /**
     * Buffer transaction succeeded.
     */
    TRANSFER_OK = 7,
    /**
     * Buffer transaction failure.
     */
    TRANSFER_ERROR = 8,
};

/**
 * Buffer ownership status change message. This message is
 * sent via fmq to the buffer pool from client processes.
 */
struct BufferStatusMessage final {
    /**
     * Transaction Id = (SenderId : sender local transaction Id)
     * Transaction Id is created from sender and posted via fmq within
     * TRANSFER_TO message.
     */
    uint64_t transactionId __attribute__ ((aligned(8)));
    uint32_t bufferId __attribute__ ((aligned(4)));
    ::android::hardware::media::bufferpool::V1_0::BufferStatus newStatus __attribute__ ((aligned(4)));
    /**
     * Used by the buffer pool. not by client.
     */
    int64_t connectionId __attribute__ ((aligned(8)));
    /**
     * Valid only when TRANSFER_TO is posted.
     */
    int64_t targetConnectionId __attribute__ ((aligned(8)));
    /**
     * Used by the buffer pool, not by client.
     * Monotonic timestamp in Us since fixed point in time as decided
     * by the sender of the message
     */
    int64_t timestampUs __attribute__ ((aligned(8)));
};

static_assert(offsetof(::android::hardware::media::bufferpool::V1_0::BufferStatusMessage, transactionId) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::media::bufferpool::V1_0::BufferStatusMessage, bufferId) == 8, "wrong offset");
static_assert(offsetof(::android::hardware::media::bufferpool::V1_0::BufferStatusMessage, newStatus) == 12, "wrong offset");
static_assert(offsetof(::android::hardware::media::bufferpool::V1_0::BufferStatusMessage, connectionId) == 16, "wrong offset");
static_assert(offsetof(::android::hardware::media::bufferpool::V1_0::BufferStatusMessage, targetConnectionId) == 24, "wrong offset");
static_assert(offsetof(::android::hardware::media::bufferpool::V1_0::BufferStatusMessage, timestampUs) == 32, "wrong offset");
static_assert(sizeof(::android::hardware::media::bufferpool::V1_0::BufferStatusMessage) == 40, "wrong size");
static_assert(__alignof(::android::hardware::media::bufferpool::V1_0::BufferStatusMessage) == 8, "wrong alignment");

//
// type declarations for package
//

template<typename>
static inline std::string toString(int32_t o);
static inline std::string toString(::android::hardware::media::bufferpool::V1_0::ResultStatus o);
static inline void PrintTo(::android::hardware::media::bufferpool::V1_0::ResultStatus o, ::std::ostream* os);
constexpr int32_t operator|(const ::android::hardware::media::bufferpool::V1_0::ResultStatus lhs, const ::android::hardware::media::bufferpool::V1_0::ResultStatus rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const int32_t lhs, const ::android::hardware::media::bufferpool::V1_0::ResultStatus rhs) {
    return static_cast<int32_t>(lhs | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const ::android::hardware::media::bufferpool::V1_0::ResultStatus lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | rhs);
}
constexpr int32_t operator&(const ::android::hardware::media::bufferpool::V1_0::ResultStatus lhs, const ::android::hardware::media::bufferpool::V1_0::ResultStatus rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const int32_t lhs, const ::android::hardware::media::bufferpool::V1_0::ResultStatus rhs) {
    return static_cast<int32_t>(lhs & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const ::android::hardware::media::bufferpool::V1_0::ResultStatus lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & rhs);
}
constexpr int32_t &operator|=(int32_t& v, const ::android::hardware::media::bufferpool::V1_0::ResultStatus e) {
    v |= static_cast<int32_t>(e);
    return v;
}
constexpr int32_t &operator&=(int32_t& v, const ::android::hardware::media::bufferpool::V1_0::ResultStatus e) {
    v &= static_cast<int32_t>(e);
    return v;
}

static inline std::string toString(const ::android::hardware::media::bufferpool::V1_0::Buffer& o);
static inline void PrintTo(const ::android::hardware::media::bufferpool::V1_0::Buffer& o, ::std::ostream*);
// operator== and operator!= are not generated for Buffer

template<typename>
static inline std::string toString(int32_t o);
static inline std::string toString(::android::hardware::media::bufferpool::V1_0::BufferStatus o);
static inline void PrintTo(::android::hardware::media::bufferpool::V1_0::BufferStatus o, ::std::ostream* os);
constexpr int32_t operator|(const ::android::hardware::media::bufferpool::V1_0::BufferStatus lhs, const ::android::hardware::media::bufferpool::V1_0::BufferStatus rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const int32_t lhs, const ::android::hardware::media::bufferpool::V1_0::BufferStatus rhs) {
    return static_cast<int32_t>(lhs | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const ::android::hardware::media::bufferpool::V1_0::BufferStatus lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | rhs);
}
constexpr int32_t operator&(const ::android::hardware::media::bufferpool::V1_0::BufferStatus lhs, const ::android::hardware::media::bufferpool::V1_0::BufferStatus rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const int32_t lhs, const ::android::hardware::media::bufferpool::V1_0::BufferStatus rhs) {
    return static_cast<int32_t>(lhs & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const ::android::hardware::media::bufferpool::V1_0::BufferStatus lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & rhs);
}
constexpr int32_t &operator|=(int32_t& v, const ::android::hardware::media::bufferpool::V1_0::BufferStatus e) {
    v |= static_cast<int32_t>(e);
    return v;
}
constexpr int32_t &operator&=(int32_t& v, const ::android::hardware::media::bufferpool::V1_0::BufferStatus e) {
    v &= static_cast<int32_t>(e);
    return v;
}

static inline std::string toString(const ::android::hardware::media::bufferpool::V1_0::BufferStatusMessage& o);
static inline void PrintTo(const ::android::hardware::media::bufferpool::V1_0::BufferStatusMessage& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::media::bufferpool::V1_0::BufferStatusMessage& lhs, const ::android::hardware::media::bufferpool::V1_0::BufferStatusMessage& rhs);
static inline bool operator!=(const ::android::hardware::media::bufferpool::V1_0::BufferStatusMessage& lhs, const ::android::hardware::media::bufferpool::V1_0::BufferStatusMessage& rhs);

//
// type header definitions for package
//

template<>
inline std::string toString<::android::hardware::media::bufferpool::V1_0::ResultStatus>(int32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::media::bufferpool::V1_0::ResultStatus> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::media::bufferpool::V1_0::ResultStatus::OK) == static_cast<int32_t>(::android::hardware::media::bufferpool::V1_0::ResultStatus::OK)) {
        os += (first ? "" : " | ");
        os += "OK";
        first = false;
        flipped |= ::android::hardware::media::bufferpool::V1_0::ResultStatus::OK;
    }
    if ((o & ::android::hardware::media::bufferpool::V1_0::ResultStatus::NO_MEMORY) == static_cast<int32_t>(::android::hardware::media::bufferpool::V1_0::ResultStatus::NO_MEMORY)) {
        os += (first ? "" : " | ");
        os += "NO_MEMORY";
        first = false;
        flipped |= ::android::hardware::media::bufferpool::V1_0::ResultStatus::NO_MEMORY;
    }
    if ((o & ::android::hardware::media::bufferpool::V1_0::ResultStatus::ALREADY_EXISTS) == static_cast<int32_t>(::android::hardware::media::bufferpool::V1_0::ResultStatus::ALREADY_EXISTS)) {
        os += (first ? "" : " | ");
        os += "ALREADY_EXISTS";
        first = false;
        flipped |= ::android::hardware::media::bufferpool::V1_0::ResultStatus::ALREADY_EXISTS;
    }
    if ((o & ::android::hardware::media::bufferpool::V1_0::ResultStatus::NOT_FOUND) == static_cast<int32_t>(::android::hardware::media::bufferpool::V1_0::ResultStatus::NOT_FOUND)) {
        os += (first ? "" : " | ");
        os += "NOT_FOUND";
        first = false;
        flipped |= ::android::hardware::media::bufferpool::V1_0::ResultStatus::NOT_FOUND;
    }
    if ((o & ::android::hardware::media::bufferpool::V1_0::ResultStatus::CRITICAL_ERROR) == static_cast<int32_t>(::android::hardware::media::bufferpool::V1_0::ResultStatus::CRITICAL_ERROR)) {
        os += (first ? "" : " | ");
        os += "CRITICAL_ERROR";
        first = false;
        flipped |= ::android::hardware::media::bufferpool::V1_0::ResultStatus::CRITICAL_ERROR;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::media::bufferpool::V1_0::ResultStatus o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::media::bufferpool::V1_0::ResultStatus::OK) {
        return "OK";
    }
    if (o == ::android::hardware::media::bufferpool::V1_0::ResultStatus::NO_MEMORY) {
        return "NO_MEMORY";
    }
    if (o == ::android::hardware::media::bufferpool::V1_0::ResultStatus::ALREADY_EXISTS) {
        return "ALREADY_EXISTS";
    }
    if (o == ::android::hardware::media::bufferpool::V1_0::ResultStatus::NOT_FOUND) {
        return "NOT_FOUND";
    }
    if (o == ::android::hardware::media::bufferpool::V1_0::ResultStatus::CRITICAL_ERROR) {
        return "CRITICAL_ERROR";
    }
    std::string os;
    os += toHexString(static_cast<int32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::media::bufferpool::V1_0::ResultStatus o, ::std::ostream* os) {
    *os << toString(o);
}

static inline std::string toString(const ::android::hardware::media::bufferpool::V1_0::Buffer& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".id = ";
    os += ::android::hardware::toString(o.id);
    os += ", .buffer = ";
    os += ::android::hardware::toString(o.buffer);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::media::bufferpool::V1_0::Buffer& o, ::std::ostream* os) {
    *os << toString(o);
}

// operator== and operator!= are not generated for Buffer

template<>
inline std::string toString<::android::hardware::media::bufferpool::V1_0::BufferStatus>(int32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::media::bufferpool::V1_0::BufferStatus> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::media::bufferpool::V1_0::BufferStatus::NOT_USED) == static_cast<int32_t>(::android::hardware::media::bufferpool::V1_0::BufferStatus::NOT_USED)) {
        os += (first ? "" : " | ");
        os += "NOT_USED";
        first = false;
        flipped |= ::android::hardware::media::bufferpool::V1_0::BufferStatus::NOT_USED;
    }
    if ((o & ::android::hardware::media::bufferpool::V1_0::BufferStatus::USED) == static_cast<int32_t>(::android::hardware::media::bufferpool::V1_0::BufferStatus::USED)) {
        os += (first ? "" : " | ");
        os += "USED";
        first = false;
        flipped |= ::android::hardware::media::bufferpool::V1_0::BufferStatus::USED;
    }
    if ((o & ::android::hardware::media::bufferpool::V1_0::BufferStatus::TRANSFER_TO) == static_cast<int32_t>(::android::hardware::media::bufferpool::V1_0::BufferStatus::TRANSFER_TO)) {
        os += (first ? "" : " | ");
        os += "TRANSFER_TO";
        first = false;
        flipped |= ::android::hardware::media::bufferpool::V1_0::BufferStatus::TRANSFER_TO;
    }
    if ((o & ::android::hardware::media::bufferpool::V1_0::BufferStatus::TRANSFER_FROM) == static_cast<int32_t>(::android::hardware::media::bufferpool::V1_0::BufferStatus::TRANSFER_FROM)) {
        os += (first ? "" : " | ");
        os += "TRANSFER_FROM";
        first = false;
        flipped |= ::android::hardware::media::bufferpool::V1_0::BufferStatus::TRANSFER_FROM;
    }
    if ((o & ::android::hardware::media::bufferpool::V1_0::BufferStatus::TRANSFER_TIMEOUT) == static_cast<int32_t>(::android::hardware::media::bufferpool::V1_0::BufferStatus::TRANSFER_TIMEOUT)) {
        os += (first ? "" : " | ");
        os += "TRANSFER_TIMEOUT";
        first = false;
        flipped |= ::android::hardware::media::bufferpool::V1_0::BufferStatus::TRANSFER_TIMEOUT;
    }
    if ((o & ::android::hardware::media::bufferpool::V1_0::BufferStatus::TRANSFER_LOST) == static_cast<int32_t>(::android::hardware::media::bufferpool::V1_0::BufferStatus::TRANSFER_LOST)) {
        os += (first ? "" : " | ");
        os += "TRANSFER_LOST";
        first = false;
        flipped |= ::android::hardware::media::bufferpool::V1_0::BufferStatus::TRANSFER_LOST;
    }
    if ((o & ::android::hardware::media::bufferpool::V1_0::BufferStatus::TRANSFER_FETCH) == static_cast<int32_t>(::android::hardware::media::bufferpool::V1_0::BufferStatus::TRANSFER_FETCH)) {
        os += (first ? "" : " | ");
        os += "TRANSFER_FETCH";
        first = false;
        flipped |= ::android::hardware::media::bufferpool::V1_0::BufferStatus::TRANSFER_FETCH;
    }
    if ((o & ::android::hardware::media::bufferpool::V1_0::BufferStatus::TRANSFER_OK) == static_cast<int32_t>(::android::hardware::media::bufferpool::V1_0::BufferStatus::TRANSFER_OK)) {
        os += (first ? "" : " | ");
        os += "TRANSFER_OK";
        first = false;
        flipped |= ::android::hardware::media::bufferpool::V1_0::BufferStatus::TRANSFER_OK;
    }
    if ((o & ::android::hardware::media::bufferpool::V1_0::BufferStatus::TRANSFER_ERROR) == static_cast<int32_t>(::android::hardware::media::bufferpool::V1_0::BufferStatus::TRANSFER_ERROR)) {
        os += (first ? "" : " | ");
        os += "TRANSFER_ERROR";
        first = false;
        flipped |= ::android::hardware::media::bufferpool::V1_0::BufferStatus::TRANSFER_ERROR;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::media::bufferpool::V1_0::BufferStatus o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::media::bufferpool::V1_0::BufferStatus::NOT_USED) {
        return "NOT_USED";
    }
    if (o == ::android::hardware::media::bufferpool::V1_0::BufferStatus::USED) {
        return "USED";
    }
    if (o == ::android::hardware::media::bufferpool::V1_0::BufferStatus::TRANSFER_TO) {
        return "TRANSFER_TO";
    }
    if (o == ::android::hardware::media::bufferpool::V1_0::BufferStatus::TRANSFER_FROM) {
        return "TRANSFER_FROM";
    }
    if (o == ::android::hardware::media::bufferpool::V1_0::BufferStatus::TRANSFER_TIMEOUT) {
        return "TRANSFER_TIMEOUT";
    }
    if (o == ::android::hardware::media::bufferpool::V1_0::BufferStatus::TRANSFER_LOST) {
        return "TRANSFER_LOST";
    }
    if (o == ::android::hardware::media::bufferpool::V1_0::BufferStatus::TRANSFER_FETCH) {
        return "TRANSFER_FETCH";
    }
    if (o == ::android::hardware::media::bufferpool::V1_0::BufferStatus::TRANSFER_OK) {
        return "TRANSFER_OK";
    }
    if (o == ::android::hardware::media::bufferpool::V1_0::BufferStatus::TRANSFER_ERROR) {
        return "TRANSFER_ERROR";
    }
    std::string os;
    os += toHexString(static_cast<int32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::media::bufferpool::V1_0::BufferStatus o, ::std::ostream* os) {
    *os << toString(o);
}

static inline std::string toString(const ::android::hardware::media::bufferpool::V1_0::BufferStatusMessage& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".transactionId = ";
    os += ::android::hardware::toString(o.transactionId);
    os += ", .bufferId = ";
    os += ::android::hardware::toString(o.bufferId);
    os += ", .newStatus = ";
    os += ::android::hardware::media::bufferpool::V1_0::toString(o.newStatus);
    os += ", .connectionId = ";
    os += ::android::hardware::toString(o.connectionId);
    os += ", .targetConnectionId = ";
    os += ::android::hardware::toString(o.targetConnectionId);
    os += ", .timestampUs = ";
    os += ::android::hardware::toString(o.timestampUs);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::media::bufferpool::V1_0::BufferStatusMessage& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::media::bufferpool::V1_0::BufferStatusMessage& lhs, const ::android::hardware::media::bufferpool::V1_0::BufferStatusMessage& rhs) {
    if (lhs.transactionId != rhs.transactionId) {
        return false;
    }
    if (lhs.bufferId != rhs.bufferId) {
        return false;
    }
    if (lhs.newStatus != rhs.newStatus) {
        return false;
    }
    if (lhs.connectionId != rhs.connectionId) {
        return false;
    }
    if (lhs.targetConnectionId != rhs.targetConnectionId) {
        return false;
    }
    if (lhs.timestampUs != rhs.timestampUs) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::media::bufferpool::V1_0::BufferStatusMessage& lhs, const ::android::hardware::media::bufferpool::V1_0::BufferStatusMessage& rhs){
    return !(lhs == rhs);
}


}  // namespace V1_0
}  // namespace bufferpool
}  // namespace media
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
template<> inline constexpr std::array<::android::hardware::media::bufferpool::V1_0::ResultStatus, 5> hidl_enum_values<::android::hardware::media::bufferpool::V1_0::ResultStatus> = {
    ::android::hardware::media::bufferpool::V1_0::ResultStatus::OK,
    ::android::hardware::media::bufferpool::V1_0::ResultStatus::NO_MEMORY,
    ::android::hardware::media::bufferpool::V1_0::ResultStatus::ALREADY_EXISTS,
    ::android::hardware::media::bufferpool::V1_0::ResultStatus::NOT_FOUND,
    ::android::hardware::media::bufferpool::V1_0::ResultStatus::CRITICAL_ERROR,
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
template<> inline constexpr std::array<::android::hardware::media::bufferpool::V1_0::BufferStatus, 9> hidl_enum_values<::android::hardware::media::bufferpool::V1_0::BufferStatus> = {
    ::android::hardware::media::bufferpool::V1_0::BufferStatus::NOT_USED,
    ::android::hardware::media::bufferpool::V1_0::BufferStatus::USED,
    ::android::hardware::media::bufferpool::V1_0::BufferStatus::TRANSFER_TO,
    ::android::hardware::media::bufferpool::V1_0::BufferStatus::TRANSFER_FROM,
    ::android::hardware::media::bufferpool::V1_0::BufferStatus::TRANSFER_TIMEOUT,
    ::android::hardware::media::bufferpool::V1_0::BufferStatus::TRANSFER_LOST,
    ::android::hardware::media::bufferpool::V1_0::BufferStatus::TRANSFER_FETCH,
    ::android::hardware::media::bufferpool::V1_0::BufferStatus::TRANSFER_OK,
    ::android::hardware::media::bufferpool::V1_0::BufferStatus::TRANSFER_ERROR,
};
#pragma clang diagnostic pop
}  // namespace details
}  // namespace hardware
}  // namespace android


#endif  // HIDL_GENERATED_ANDROID_HARDWARE_MEDIA_BUFFERPOOL_V1_0_TYPES_H
