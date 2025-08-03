#ifndef HIDL_GENERATED_ANDROID_HARDWARE_CONTEXTHUB_V1_0_TYPES_H
#define HIDL_GENERATED_ANDROID_HARDWARE_CONTEXTHUB_V1_0_TYPES_H

#include <hidl/HidlSupport.h>
#include <hidl/MQDescriptor.h>
#include <utils/NativeHandle.h>
#include <utils/misc.h>

namespace android {
namespace hardware {
namespace contexthub {
namespace V1_0 {

// Forward declaration for forward reference support:
enum class Result : uint32_t;
enum class NanoAppFlags : uint32_t;
struct NanoAppBinary;
enum class SensorType : uint32_t;
struct PhysicalSensor;
struct ContextHub;
enum class HostEndPoint : uint16_t;
struct ContextHubMsg;
enum class HubMemoryType : uint32_t;
enum class HubMemoryFlag : uint32_t;
struct MemRange;
enum class AsyncEventType : uint32_t;
enum class TransactionResult : int32_t;
struct HubAppInfo;

enum class Result : uint32_t {
    OK = 0u,
    UNKNOWN_FAILURE = 1u /* ::android::hardware::contexthub::V1_0::Result.OK implicitly + 1 */,
    BAD_PARAMS = 2u /* ::android::hardware::contexthub::V1_0::Result.UNKNOWN_FAILURE implicitly + 1 */,
    NOT_INIT = 3u /* ::android::hardware::contexthub::V1_0::Result.BAD_PARAMS implicitly + 1 */,
    TRANSACTION_FAILED = 4u /* ::android::hardware::contexthub::V1_0::Result.NOT_INIT implicitly + 1 */,
    TRANSACTION_PENDING = 5u /* ::android::hardware::contexthub::V1_0::Result.TRANSACTION_FAILED implicitly + 1 */,
};

enum class NanoAppFlags : uint32_t {
    SIGNED = 1u /* 1 << 0 */,
    ENCRYPTED = 2u /* 1 << 1 */,
};

struct NanoAppBinary final {
    uint64_t appId __attribute__ ((aligned(8)));
    uint32_t appVersion __attribute__ ((aligned(4)));
    ::android::hardware::hidl_bitfield<::android::hardware::contexthub::V1_0::NanoAppFlags> flags __attribute__ ((aligned(4)));
    uint8_t targetChreApiMajorVersion __attribute__ ((aligned(1)));
    uint8_t targetChreApiMinorVersion __attribute__ ((aligned(1)));
    ::android::hardware::hidl_vec<uint8_t> customBinary __attribute__ ((aligned(8)));
};

static_assert(offsetof(::android::hardware::contexthub::V1_0::NanoAppBinary, appId) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::contexthub::V1_0::NanoAppBinary, appVersion) == 8, "wrong offset");
static_assert(offsetof(::android::hardware::contexthub::V1_0::NanoAppBinary, flags) == 12, "wrong offset");
static_assert(offsetof(::android::hardware::contexthub::V1_0::NanoAppBinary, targetChreApiMajorVersion) == 16, "wrong offset");
static_assert(offsetof(::android::hardware::contexthub::V1_0::NanoAppBinary, targetChreApiMinorVersion) == 17, "wrong offset");
static_assert(offsetof(::android::hardware::contexthub::V1_0::NanoAppBinary, customBinary) == 24, "wrong offset");
static_assert(sizeof(::android::hardware::contexthub::V1_0::NanoAppBinary) == 40, "wrong size");
static_assert(__alignof(::android::hardware::contexthub::V1_0::NanoAppBinary) == 8, "wrong alignment");

enum class SensorType : uint32_t {
    RESERVED = 0u,
    ACCELEROMETER = 1u /* ::android::hardware::contexthub::V1_0::SensorType.RESERVED implicitly + 1 */,
    GYROSCOPE = 2u /* ::android::hardware::contexthub::V1_0::SensorType.ACCELEROMETER implicitly + 1 */,
    MAGNETOMETER = 3u /* ::android::hardware::contexthub::V1_0::SensorType.GYROSCOPE implicitly + 1 */,
    BAROMETER = 4u /* ::android::hardware::contexthub::V1_0::SensorType.MAGNETOMETER implicitly + 1 */,
    PROXIMITY_SENSOR = 5u /* ::android::hardware::contexthub::V1_0::SensorType.BAROMETER implicitly + 1 */,
    AMBIENT_LIGHT_SENSOR = 6u /* ::android::hardware::contexthub::V1_0::SensorType.PROXIMITY_SENSOR implicitly + 1 */,
    STATIONARY_DETECT = 7u /* ::android::hardware::contexthub::V1_0::SensorType.AMBIENT_LIGHT_SENSOR implicitly + 1 */,
    INSTANT_MOTION_DETECT = 8u /* ::android::hardware::contexthub::V1_0::SensorType.STATIONARY_DETECT implicitly + 1 */,
    GPS = 256u /* 0x100 */,
    WIFI = 512u /* 0x200 */,
    AUDIO = 768u /* 0x300 */,
    CAMERA = 1024u /* 0x400 */,
    BLE = 1280u /* 0x500 */,
    WWAN = 1536u /* 0x600 */,
    PRIVATE_SENSOR_BASE = 65536u /* 0x10000 */,
};

struct PhysicalSensor final {
    ::android::hardware::contexthub::V1_0::SensorType sensorType __attribute__ ((aligned(4)));
    ::android::hardware::hidl_string type __attribute__ ((aligned(8)));
    ::android::hardware::hidl_string name __attribute__ ((aligned(8)));
    ::android::hardware::hidl_string vendor __attribute__ ((aligned(8)));
    uint32_t version __attribute__ ((aligned(4)));
    uint32_t fifoReservedCount __attribute__ ((aligned(4)));
    uint32_t fifoMaxCount __attribute__ ((aligned(4)));
    uint64_t minDelayMs __attribute__ ((aligned(8)));
    uint64_t maxDelayMs __attribute__ ((aligned(8)));
    float peakPowerMw __attribute__ ((aligned(4)));
};

static_assert(offsetof(::android::hardware::contexthub::V1_0::PhysicalSensor, sensorType) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::contexthub::V1_0::PhysicalSensor, type) == 8, "wrong offset");
static_assert(offsetof(::android::hardware::contexthub::V1_0::PhysicalSensor, name) == 24, "wrong offset");
static_assert(offsetof(::android::hardware::contexthub::V1_0::PhysicalSensor, vendor) == 40, "wrong offset");
static_assert(offsetof(::android::hardware::contexthub::V1_0::PhysicalSensor, version) == 56, "wrong offset");
static_assert(offsetof(::android::hardware::contexthub::V1_0::PhysicalSensor, fifoReservedCount) == 60, "wrong offset");
static_assert(offsetof(::android::hardware::contexthub::V1_0::PhysicalSensor, fifoMaxCount) == 64, "wrong offset");
static_assert(offsetof(::android::hardware::contexthub::V1_0::PhysicalSensor, minDelayMs) == 72, "wrong offset");
static_assert(offsetof(::android::hardware::contexthub::V1_0::PhysicalSensor, maxDelayMs) == 80, "wrong offset");
static_assert(offsetof(::android::hardware::contexthub::V1_0::PhysicalSensor, peakPowerMw) == 88, "wrong offset");
static_assert(sizeof(::android::hardware::contexthub::V1_0::PhysicalSensor) == 96, "wrong size");
static_assert(__alignof(::android::hardware::contexthub::V1_0::PhysicalSensor) == 8, "wrong alignment");

struct ContextHub final {
    ::android::hardware::hidl_string name __attribute__ ((aligned(8)));
    ::android::hardware::hidl_string vendor __attribute__ ((aligned(8)));
    ::android::hardware::hidl_string toolchain __attribute__ ((aligned(8)));
    uint32_t platformVersion __attribute__ ((aligned(4)));
    uint32_t toolchainVersion __attribute__ ((aligned(4)));
    uint32_t hubId __attribute__ ((aligned(4)));
    float peakMips __attribute__ ((aligned(4)));
    float stoppedPowerDrawMw __attribute__ ((aligned(4)));
    float sleepPowerDrawMw __attribute__ ((aligned(4)));
    float peakPowerDrawMw __attribute__ ((aligned(4)));
    ::android::hardware::hidl_vec<::android::hardware::contexthub::V1_0::PhysicalSensor> connectedSensors __attribute__ ((aligned(8)));
    uint32_t maxSupportedMsgLen __attribute__ ((aligned(4)));
    uint64_t chrePlatformId __attribute__ ((aligned(8)));
    uint8_t chreApiMajorVersion __attribute__ ((aligned(1)));
    uint8_t chreApiMinorVersion __attribute__ ((aligned(1)));
    uint16_t chrePatchVersion __attribute__ ((aligned(2)));
};

static_assert(offsetof(::android::hardware::contexthub::V1_0::ContextHub, name) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::contexthub::V1_0::ContextHub, vendor) == 16, "wrong offset");
static_assert(offsetof(::android::hardware::contexthub::V1_0::ContextHub, toolchain) == 32, "wrong offset");
static_assert(offsetof(::android::hardware::contexthub::V1_0::ContextHub, platformVersion) == 48, "wrong offset");
static_assert(offsetof(::android::hardware::contexthub::V1_0::ContextHub, toolchainVersion) == 52, "wrong offset");
static_assert(offsetof(::android::hardware::contexthub::V1_0::ContextHub, hubId) == 56, "wrong offset");
static_assert(offsetof(::android::hardware::contexthub::V1_0::ContextHub, peakMips) == 60, "wrong offset");
static_assert(offsetof(::android::hardware::contexthub::V1_0::ContextHub, stoppedPowerDrawMw) == 64, "wrong offset");
static_assert(offsetof(::android::hardware::contexthub::V1_0::ContextHub, sleepPowerDrawMw) == 68, "wrong offset");
static_assert(offsetof(::android::hardware::contexthub::V1_0::ContextHub, peakPowerDrawMw) == 72, "wrong offset");
static_assert(offsetof(::android::hardware::contexthub::V1_0::ContextHub, connectedSensors) == 80, "wrong offset");
static_assert(offsetof(::android::hardware::contexthub::V1_0::ContextHub, maxSupportedMsgLen) == 96, "wrong offset");
static_assert(offsetof(::android::hardware::contexthub::V1_0::ContextHub, chrePlatformId) == 104, "wrong offset");
static_assert(offsetof(::android::hardware::contexthub::V1_0::ContextHub, chreApiMajorVersion) == 112, "wrong offset");
static_assert(offsetof(::android::hardware::contexthub::V1_0::ContextHub, chreApiMinorVersion) == 113, "wrong offset");
static_assert(offsetof(::android::hardware::contexthub::V1_0::ContextHub, chrePatchVersion) == 114, "wrong offset");
static_assert(sizeof(::android::hardware::contexthub::V1_0::ContextHub) == 120, "wrong size");
static_assert(__alignof(::android::hardware::contexthub::V1_0::ContextHub) == 8, "wrong alignment");

enum class HostEndPoint : uint16_t {
    BROADCAST = 65535 /* 0xFFFF */,
    UNSPECIFIED = 65534 /* 0xFFFE */,
};

struct ContextHubMsg final {
    uint64_t appName __attribute__ ((aligned(8)));
    uint16_t hostEndPoint __attribute__ ((aligned(2)));
    uint32_t msgType __attribute__ ((aligned(4)));
    ::android::hardware::hidl_vec<uint8_t> msg __attribute__ ((aligned(8)));
};

static_assert(offsetof(::android::hardware::contexthub::V1_0::ContextHubMsg, appName) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::contexthub::V1_0::ContextHubMsg, hostEndPoint) == 8, "wrong offset");
static_assert(offsetof(::android::hardware::contexthub::V1_0::ContextHubMsg, msgType) == 12, "wrong offset");
static_assert(offsetof(::android::hardware::contexthub::V1_0::ContextHubMsg, msg) == 16, "wrong offset");
static_assert(sizeof(::android::hardware::contexthub::V1_0::ContextHubMsg) == 32, "wrong size");
static_assert(__alignof(::android::hardware::contexthub::V1_0::ContextHubMsg) == 8, "wrong alignment");

enum class HubMemoryType : uint32_t {
    MAIN = 0u,
    SECONDARY = 1u,
    TCM = 2u,
};

enum class HubMemoryFlag : uint32_t {
    READ = 1u /* 1 << 0 */,
    WRITE = 2u /* 1 << 1 */,
    EXEC = 4u /* 1 << 2 */,
};

struct MemRange final {
    uint32_t totalBytes __attribute__ ((aligned(4)));
    uint32_t freeBytes __attribute__ ((aligned(4)));
    ::android::hardware::contexthub::V1_0::HubMemoryType type __attribute__ ((aligned(4)));
    ::android::hardware::hidl_bitfield<::android::hardware::contexthub::V1_0::HubMemoryFlag> flags __attribute__ ((aligned(4)));
};

static_assert(offsetof(::android::hardware::contexthub::V1_0::MemRange, totalBytes) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::contexthub::V1_0::MemRange, freeBytes) == 4, "wrong offset");
static_assert(offsetof(::android::hardware::contexthub::V1_0::MemRange, type) == 8, "wrong offset");
static_assert(offsetof(::android::hardware::contexthub::V1_0::MemRange, flags) == 12, "wrong offset");
static_assert(sizeof(::android::hardware::contexthub::V1_0::MemRange) == 16, "wrong size");
static_assert(__alignof(::android::hardware::contexthub::V1_0::MemRange) == 4, "wrong alignment");

enum class AsyncEventType : uint32_t {
    RESTARTED = 1u,
};

enum class TransactionResult : int32_t {
    SUCCESS = 0,
    FAILURE = 1 /* ::android::hardware::contexthub::V1_0::TransactionResult.SUCCESS implicitly + 1 */,
};

struct HubAppInfo final {
    uint64_t appId __attribute__ ((aligned(8)));
    uint32_t version __attribute__ ((aligned(4)));
    ::android::hardware::hidl_vec<::android::hardware::contexthub::V1_0::MemRange> memUsage __attribute__ ((aligned(8)));
    bool enabled __attribute__ ((aligned(1)));
};

static_assert(offsetof(::android::hardware::contexthub::V1_0::HubAppInfo, appId) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::contexthub::V1_0::HubAppInfo, version) == 8, "wrong offset");
static_assert(offsetof(::android::hardware::contexthub::V1_0::HubAppInfo, memUsage) == 16, "wrong offset");
static_assert(offsetof(::android::hardware::contexthub::V1_0::HubAppInfo, enabled) == 32, "wrong offset");
static_assert(sizeof(::android::hardware::contexthub::V1_0::HubAppInfo) == 40, "wrong size");
static_assert(__alignof(::android::hardware::contexthub::V1_0::HubAppInfo) == 8, "wrong alignment");

//
// type declarations for package
//

template<typename>
static inline std::string toString(uint32_t o);
static inline std::string toString(::android::hardware::contexthub::V1_0::Result o);
static inline void PrintTo(::android::hardware::contexthub::V1_0::Result o, ::std::ostream* os);
constexpr uint32_t operator|(const ::android::hardware::contexthub::V1_0::Result lhs, const ::android::hardware::contexthub::V1_0::Result rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const uint32_t lhs, const ::android::hardware::contexthub::V1_0::Result rhs) {
    return static_cast<uint32_t>(lhs | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const ::android::hardware::contexthub::V1_0::Result lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | rhs);
}
constexpr uint32_t operator&(const ::android::hardware::contexthub::V1_0::Result lhs, const ::android::hardware::contexthub::V1_0::Result rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const uint32_t lhs, const ::android::hardware::contexthub::V1_0::Result rhs) {
    return static_cast<uint32_t>(lhs & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const ::android::hardware::contexthub::V1_0::Result lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & rhs);
}
constexpr uint32_t &operator|=(uint32_t& v, const ::android::hardware::contexthub::V1_0::Result e) {
    v |= static_cast<uint32_t>(e);
    return v;
}
constexpr uint32_t &operator&=(uint32_t& v, const ::android::hardware::contexthub::V1_0::Result e) {
    v &= static_cast<uint32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(uint32_t o);
static inline std::string toString(::android::hardware::contexthub::V1_0::NanoAppFlags o);
static inline void PrintTo(::android::hardware::contexthub::V1_0::NanoAppFlags o, ::std::ostream* os);
constexpr uint32_t operator|(const ::android::hardware::contexthub::V1_0::NanoAppFlags lhs, const ::android::hardware::contexthub::V1_0::NanoAppFlags rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const uint32_t lhs, const ::android::hardware::contexthub::V1_0::NanoAppFlags rhs) {
    return static_cast<uint32_t>(lhs | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const ::android::hardware::contexthub::V1_0::NanoAppFlags lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | rhs);
}
constexpr uint32_t operator&(const ::android::hardware::contexthub::V1_0::NanoAppFlags lhs, const ::android::hardware::contexthub::V1_0::NanoAppFlags rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const uint32_t lhs, const ::android::hardware::contexthub::V1_0::NanoAppFlags rhs) {
    return static_cast<uint32_t>(lhs & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const ::android::hardware::contexthub::V1_0::NanoAppFlags lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & rhs);
}
constexpr uint32_t &operator|=(uint32_t& v, const ::android::hardware::contexthub::V1_0::NanoAppFlags e) {
    v |= static_cast<uint32_t>(e);
    return v;
}
constexpr uint32_t &operator&=(uint32_t& v, const ::android::hardware::contexthub::V1_0::NanoAppFlags e) {
    v &= static_cast<uint32_t>(e);
    return v;
}

static inline std::string toString(const ::android::hardware::contexthub::V1_0::NanoAppBinary& o);
static inline void PrintTo(const ::android::hardware::contexthub::V1_0::NanoAppBinary& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::contexthub::V1_0::NanoAppBinary& lhs, const ::android::hardware::contexthub::V1_0::NanoAppBinary& rhs);
static inline bool operator!=(const ::android::hardware::contexthub::V1_0::NanoAppBinary& lhs, const ::android::hardware::contexthub::V1_0::NanoAppBinary& rhs);

template<typename>
static inline std::string toString(uint32_t o);
static inline std::string toString(::android::hardware::contexthub::V1_0::SensorType o);
static inline void PrintTo(::android::hardware::contexthub::V1_0::SensorType o, ::std::ostream* os);
constexpr uint32_t operator|(const ::android::hardware::contexthub::V1_0::SensorType lhs, const ::android::hardware::contexthub::V1_0::SensorType rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const uint32_t lhs, const ::android::hardware::contexthub::V1_0::SensorType rhs) {
    return static_cast<uint32_t>(lhs | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const ::android::hardware::contexthub::V1_0::SensorType lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | rhs);
}
constexpr uint32_t operator&(const ::android::hardware::contexthub::V1_0::SensorType lhs, const ::android::hardware::contexthub::V1_0::SensorType rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const uint32_t lhs, const ::android::hardware::contexthub::V1_0::SensorType rhs) {
    return static_cast<uint32_t>(lhs & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const ::android::hardware::contexthub::V1_0::SensorType lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & rhs);
}
constexpr uint32_t &operator|=(uint32_t& v, const ::android::hardware::contexthub::V1_0::SensorType e) {
    v |= static_cast<uint32_t>(e);
    return v;
}
constexpr uint32_t &operator&=(uint32_t& v, const ::android::hardware::contexthub::V1_0::SensorType e) {
    v &= static_cast<uint32_t>(e);
    return v;
}

static inline std::string toString(const ::android::hardware::contexthub::V1_0::PhysicalSensor& o);
static inline void PrintTo(const ::android::hardware::contexthub::V1_0::PhysicalSensor& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::contexthub::V1_0::PhysicalSensor& lhs, const ::android::hardware::contexthub::V1_0::PhysicalSensor& rhs);
static inline bool operator!=(const ::android::hardware::contexthub::V1_0::PhysicalSensor& lhs, const ::android::hardware::contexthub::V1_0::PhysicalSensor& rhs);

static inline std::string toString(const ::android::hardware::contexthub::V1_0::ContextHub& o);
static inline void PrintTo(const ::android::hardware::contexthub::V1_0::ContextHub& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::contexthub::V1_0::ContextHub& lhs, const ::android::hardware::contexthub::V1_0::ContextHub& rhs);
static inline bool operator!=(const ::android::hardware::contexthub::V1_0::ContextHub& lhs, const ::android::hardware::contexthub::V1_0::ContextHub& rhs);

template<typename>
static inline std::string toString(uint16_t o);
static inline std::string toString(::android::hardware::contexthub::V1_0::HostEndPoint o);
static inline void PrintTo(::android::hardware::contexthub::V1_0::HostEndPoint o, ::std::ostream* os);
constexpr uint16_t operator|(const ::android::hardware::contexthub::V1_0::HostEndPoint lhs, const ::android::hardware::contexthub::V1_0::HostEndPoint rhs) {
    return static_cast<uint16_t>(static_cast<uint16_t>(lhs) | static_cast<uint16_t>(rhs));
}
constexpr uint16_t operator|(const uint16_t lhs, const ::android::hardware::contexthub::V1_0::HostEndPoint rhs) {
    return static_cast<uint16_t>(lhs | static_cast<uint16_t>(rhs));
}
constexpr uint16_t operator|(const ::android::hardware::contexthub::V1_0::HostEndPoint lhs, const uint16_t rhs) {
    return static_cast<uint16_t>(static_cast<uint16_t>(lhs) | rhs);
}
constexpr uint16_t operator&(const ::android::hardware::contexthub::V1_0::HostEndPoint lhs, const ::android::hardware::contexthub::V1_0::HostEndPoint rhs) {
    return static_cast<uint16_t>(static_cast<uint16_t>(lhs) & static_cast<uint16_t>(rhs));
}
constexpr uint16_t operator&(const uint16_t lhs, const ::android::hardware::contexthub::V1_0::HostEndPoint rhs) {
    return static_cast<uint16_t>(lhs & static_cast<uint16_t>(rhs));
}
constexpr uint16_t operator&(const ::android::hardware::contexthub::V1_0::HostEndPoint lhs, const uint16_t rhs) {
    return static_cast<uint16_t>(static_cast<uint16_t>(lhs) & rhs);
}
constexpr uint16_t &operator|=(uint16_t& v, const ::android::hardware::contexthub::V1_0::HostEndPoint e) {
    v |= static_cast<uint16_t>(e);
    return v;
}
constexpr uint16_t &operator&=(uint16_t& v, const ::android::hardware::contexthub::V1_0::HostEndPoint e) {
    v &= static_cast<uint16_t>(e);
    return v;
}

static inline std::string toString(const ::android::hardware::contexthub::V1_0::ContextHubMsg& o);
static inline void PrintTo(const ::android::hardware::contexthub::V1_0::ContextHubMsg& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::contexthub::V1_0::ContextHubMsg& lhs, const ::android::hardware::contexthub::V1_0::ContextHubMsg& rhs);
static inline bool operator!=(const ::android::hardware::contexthub::V1_0::ContextHubMsg& lhs, const ::android::hardware::contexthub::V1_0::ContextHubMsg& rhs);

template<typename>
static inline std::string toString(uint32_t o);
static inline std::string toString(::android::hardware::contexthub::V1_0::HubMemoryType o);
static inline void PrintTo(::android::hardware::contexthub::V1_0::HubMemoryType o, ::std::ostream* os);
constexpr uint32_t operator|(const ::android::hardware::contexthub::V1_0::HubMemoryType lhs, const ::android::hardware::contexthub::V1_0::HubMemoryType rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const uint32_t lhs, const ::android::hardware::contexthub::V1_0::HubMemoryType rhs) {
    return static_cast<uint32_t>(lhs | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const ::android::hardware::contexthub::V1_0::HubMemoryType lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | rhs);
}
constexpr uint32_t operator&(const ::android::hardware::contexthub::V1_0::HubMemoryType lhs, const ::android::hardware::contexthub::V1_0::HubMemoryType rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const uint32_t lhs, const ::android::hardware::contexthub::V1_0::HubMemoryType rhs) {
    return static_cast<uint32_t>(lhs & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const ::android::hardware::contexthub::V1_0::HubMemoryType lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & rhs);
}
constexpr uint32_t &operator|=(uint32_t& v, const ::android::hardware::contexthub::V1_0::HubMemoryType e) {
    v |= static_cast<uint32_t>(e);
    return v;
}
constexpr uint32_t &operator&=(uint32_t& v, const ::android::hardware::contexthub::V1_0::HubMemoryType e) {
    v &= static_cast<uint32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(uint32_t o);
static inline std::string toString(::android::hardware::contexthub::V1_0::HubMemoryFlag o);
static inline void PrintTo(::android::hardware::contexthub::V1_0::HubMemoryFlag o, ::std::ostream* os);
constexpr uint32_t operator|(const ::android::hardware::contexthub::V1_0::HubMemoryFlag lhs, const ::android::hardware::contexthub::V1_0::HubMemoryFlag rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const uint32_t lhs, const ::android::hardware::contexthub::V1_0::HubMemoryFlag rhs) {
    return static_cast<uint32_t>(lhs | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const ::android::hardware::contexthub::V1_0::HubMemoryFlag lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | rhs);
}
constexpr uint32_t operator&(const ::android::hardware::contexthub::V1_0::HubMemoryFlag lhs, const ::android::hardware::contexthub::V1_0::HubMemoryFlag rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const uint32_t lhs, const ::android::hardware::contexthub::V1_0::HubMemoryFlag rhs) {
    return static_cast<uint32_t>(lhs & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const ::android::hardware::contexthub::V1_0::HubMemoryFlag lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & rhs);
}
constexpr uint32_t &operator|=(uint32_t& v, const ::android::hardware::contexthub::V1_0::HubMemoryFlag e) {
    v |= static_cast<uint32_t>(e);
    return v;
}
constexpr uint32_t &operator&=(uint32_t& v, const ::android::hardware::contexthub::V1_0::HubMemoryFlag e) {
    v &= static_cast<uint32_t>(e);
    return v;
}

static inline std::string toString(const ::android::hardware::contexthub::V1_0::MemRange& o);
static inline void PrintTo(const ::android::hardware::contexthub::V1_0::MemRange& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::contexthub::V1_0::MemRange& lhs, const ::android::hardware::contexthub::V1_0::MemRange& rhs);
static inline bool operator!=(const ::android::hardware::contexthub::V1_0::MemRange& lhs, const ::android::hardware::contexthub::V1_0::MemRange& rhs);

template<typename>
static inline std::string toString(uint32_t o);
static inline std::string toString(::android::hardware::contexthub::V1_0::AsyncEventType o);
static inline void PrintTo(::android::hardware::contexthub::V1_0::AsyncEventType o, ::std::ostream* os);
constexpr uint32_t operator|(const ::android::hardware::contexthub::V1_0::AsyncEventType lhs, const ::android::hardware::contexthub::V1_0::AsyncEventType rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const uint32_t lhs, const ::android::hardware::contexthub::V1_0::AsyncEventType rhs) {
    return static_cast<uint32_t>(lhs | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const ::android::hardware::contexthub::V1_0::AsyncEventType lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | rhs);
}
constexpr uint32_t operator&(const ::android::hardware::contexthub::V1_0::AsyncEventType lhs, const ::android::hardware::contexthub::V1_0::AsyncEventType rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const uint32_t lhs, const ::android::hardware::contexthub::V1_0::AsyncEventType rhs) {
    return static_cast<uint32_t>(lhs & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const ::android::hardware::contexthub::V1_0::AsyncEventType lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & rhs);
}
constexpr uint32_t &operator|=(uint32_t& v, const ::android::hardware::contexthub::V1_0::AsyncEventType e) {
    v |= static_cast<uint32_t>(e);
    return v;
}
constexpr uint32_t &operator&=(uint32_t& v, const ::android::hardware::contexthub::V1_0::AsyncEventType e) {
    v &= static_cast<uint32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(int32_t o);
static inline std::string toString(::android::hardware::contexthub::V1_0::TransactionResult o);
static inline void PrintTo(::android::hardware::contexthub::V1_0::TransactionResult o, ::std::ostream* os);
constexpr int32_t operator|(const ::android::hardware::contexthub::V1_0::TransactionResult lhs, const ::android::hardware::contexthub::V1_0::TransactionResult rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const int32_t lhs, const ::android::hardware::contexthub::V1_0::TransactionResult rhs) {
    return static_cast<int32_t>(lhs | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const ::android::hardware::contexthub::V1_0::TransactionResult lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | rhs);
}
constexpr int32_t operator&(const ::android::hardware::contexthub::V1_0::TransactionResult lhs, const ::android::hardware::contexthub::V1_0::TransactionResult rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const int32_t lhs, const ::android::hardware::contexthub::V1_0::TransactionResult rhs) {
    return static_cast<int32_t>(lhs & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const ::android::hardware::contexthub::V1_0::TransactionResult lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & rhs);
}
constexpr int32_t &operator|=(int32_t& v, const ::android::hardware::contexthub::V1_0::TransactionResult e) {
    v |= static_cast<int32_t>(e);
    return v;
}
constexpr int32_t &operator&=(int32_t& v, const ::android::hardware::contexthub::V1_0::TransactionResult e) {
    v &= static_cast<int32_t>(e);
    return v;
}

static inline std::string toString(const ::android::hardware::contexthub::V1_0::HubAppInfo& o);
static inline void PrintTo(const ::android::hardware::contexthub::V1_0::HubAppInfo& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::contexthub::V1_0::HubAppInfo& lhs, const ::android::hardware::contexthub::V1_0::HubAppInfo& rhs);
static inline bool operator!=(const ::android::hardware::contexthub::V1_0::HubAppInfo& lhs, const ::android::hardware::contexthub::V1_0::HubAppInfo& rhs);

//
// type header definitions for package
//

template<>
inline std::string toString<::android::hardware::contexthub::V1_0::Result>(uint32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::contexthub::V1_0::Result> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::contexthub::V1_0::Result::OK) == static_cast<uint32_t>(::android::hardware::contexthub::V1_0::Result::OK)) {
        os += (first ? "" : " | ");
        os += "OK";
        first = false;
        flipped |= ::android::hardware::contexthub::V1_0::Result::OK;
    }
    if ((o & ::android::hardware::contexthub::V1_0::Result::UNKNOWN_FAILURE) == static_cast<uint32_t>(::android::hardware::contexthub::V1_0::Result::UNKNOWN_FAILURE)) {
        os += (first ? "" : " | ");
        os += "UNKNOWN_FAILURE";
        first = false;
        flipped |= ::android::hardware::contexthub::V1_0::Result::UNKNOWN_FAILURE;
    }
    if ((o & ::android::hardware::contexthub::V1_0::Result::BAD_PARAMS) == static_cast<uint32_t>(::android::hardware::contexthub::V1_0::Result::BAD_PARAMS)) {
        os += (first ? "" : " | ");
        os += "BAD_PARAMS";
        first = false;
        flipped |= ::android::hardware::contexthub::V1_0::Result::BAD_PARAMS;
    }
    if ((o & ::android::hardware::contexthub::V1_0::Result::NOT_INIT) == static_cast<uint32_t>(::android::hardware::contexthub::V1_0::Result::NOT_INIT)) {
        os += (first ? "" : " | ");
        os += "NOT_INIT";
        first = false;
        flipped |= ::android::hardware::contexthub::V1_0::Result::NOT_INIT;
    }
    if ((o & ::android::hardware::contexthub::V1_0::Result::TRANSACTION_FAILED) == static_cast<uint32_t>(::android::hardware::contexthub::V1_0::Result::TRANSACTION_FAILED)) {
        os += (first ? "" : " | ");
        os += "TRANSACTION_FAILED";
        first = false;
        flipped |= ::android::hardware::contexthub::V1_0::Result::TRANSACTION_FAILED;
    }
    if ((o & ::android::hardware::contexthub::V1_0::Result::TRANSACTION_PENDING) == static_cast<uint32_t>(::android::hardware::contexthub::V1_0::Result::TRANSACTION_PENDING)) {
        os += (first ? "" : " | ");
        os += "TRANSACTION_PENDING";
        first = false;
        flipped |= ::android::hardware::contexthub::V1_0::Result::TRANSACTION_PENDING;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::contexthub::V1_0::Result o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::contexthub::V1_0::Result::OK) {
        return "OK";
    }
    if (o == ::android::hardware::contexthub::V1_0::Result::UNKNOWN_FAILURE) {
        return "UNKNOWN_FAILURE";
    }
    if (o == ::android::hardware::contexthub::V1_0::Result::BAD_PARAMS) {
        return "BAD_PARAMS";
    }
    if (o == ::android::hardware::contexthub::V1_0::Result::NOT_INIT) {
        return "NOT_INIT";
    }
    if (o == ::android::hardware::contexthub::V1_0::Result::TRANSACTION_FAILED) {
        return "TRANSACTION_FAILED";
    }
    if (o == ::android::hardware::contexthub::V1_0::Result::TRANSACTION_PENDING) {
        return "TRANSACTION_PENDING";
    }
    std::string os;
    os += toHexString(static_cast<uint32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::contexthub::V1_0::Result o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::contexthub::V1_0::NanoAppFlags>(uint32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::contexthub::V1_0::NanoAppFlags> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::contexthub::V1_0::NanoAppFlags::SIGNED) == static_cast<uint32_t>(::android::hardware::contexthub::V1_0::NanoAppFlags::SIGNED)) {
        os += (first ? "" : " | ");
        os += "SIGNED";
        first = false;
        flipped |= ::android::hardware::contexthub::V1_0::NanoAppFlags::SIGNED;
    }
    if ((o & ::android::hardware::contexthub::V1_0::NanoAppFlags::ENCRYPTED) == static_cast<uint32_t>(::android::hardware::contexthub::V1_0::NanoAppFlags::ENCRYPTED)) {
        os += (first ? "" : " | ");
        os += "ENCRYPTED";
        first = false;
        flipped |= ::android::hardware::contexthub::V1_0::NanoAppFlags::ENCRYPTED;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::contexthub::V1_0::NanoAppFlags o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::contexthub::V1_0::NanoAppFlags::SIGNED) {
        return "SIGNED";
    }
    if (o == ::android::hardware::contexthub::V1_0::NanoAppFlags::ENCRYPTED) {
        return "ENCRYPTED";
    }
    std::string os;
    os += toHexString(static_cast<uint32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::contexthub::V1_0::NanoAppFlags o, ::std::ostream* os) {
    *os << toString(o);
}

static inline std::string toString(const ::android::hardware::contexthub::V1_0::NanoAppBinary& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".appId = ";
    os += ::android::hardware::toString(o.appId);
    os += ", .appVersion = ";
    os += ::android::hardware::toString(o.appVersion);
    os += ", .flags = ";
    os += ::android::hardware::contexthub::V1_0::toString<::android::hardware::contexthub::V1_0::NanoAppFlags>(o.flags);
    os += ", .targetChreApiMajorVersion = ";
    os += ::android::hardware::toString(o.targetChreApiMajorVersion);
    os += ", .targetChreApiMinorVersion = ";
    os += ::android::hardware::toString(o.targetChreApiMinorVersion);
    os += ", .customBinary = ";
    os += ::android::hardware::toString(o.customBinary);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::contexthub::V1_0::NanoAppBinary& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::contexthub::V1_0::NanoAppBinary& lhs, const ::android::hardware::contexthub::V1_0::NanoAppBinary& rhs) {
    if (lhs.appId != rhs.appId) {
        return false;
    }
    if (lhs.appVersion != rhs.appVersion) {
        return false;
    }
    if (lhs.flags != rhs.flags) {
        return false;
    }
    if (lhs.targetChreApiMajorVersion != rhs.targetChreApiMajorVersion) {
        return false;
    }
    if (lhs.targetChreApiMinorVersion != rhs.targetChreApiMinorVersion) {
        return false;
    }
    if (lhs.customBinary != rhs.customBinary) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::contexthub::V1_0::NanoAppBinary& lhs, const ::android::hardware::contexthub::V1_0::NanoAppBinary& rhs){
    return !(lhs == rhs);
}

template<>
inline std::string toString<::android::hardware::contexthub::V1_0::SensorType>(uint32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::contexthub::V1_0::SensorType> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::contexthub::V1_0::SensorType::RESERVED) == static_cast<uint32_t>(::android::hardware::contexthub::V1_0::SensorType::RESERVED)) {
        os += (first ? "" : " | ");
        os += "RESERVED";
        first = false;
        flipped |= ::android::hardware::contexthub::V1_0::SensorType::RESERVED;
    }
    if ((o & ::android::hardware::contexthub::V1_0::SensorType::ACCELEROMETER) == static_cast<uint32_t>(::android::hardware::contexthub::V1_0::SensorType::ACCELEROMETER)) {
        os += (first ? "" : " | ");
        os += "ACCELEROMETER";
        first = false;
        flipped |= ::android::hardware::contexthub::V1_0::SensorType::ACCELEROMETER;
    }
    if ((o & ::android::hardware::contexthub::V1_0::SensorType::GYROSCOPE) == static_cast<uint32_t>(::android::hardware::contexthub::V1_0::SensorType::GYROSCOPE)) {
        os += (first ? "" : " | ");
        os += "GYROSCOPE";
        first = false;
        flipped |= ::android::hardware::contexthub::V1_0::SensorType::GYROSCOPE;
    }
    if ((o & ::android::hardware::contexthub::V1_0::SensorType::MAGNETOMETER) == static_cast<uint32_t>(::android::hardware::contexthub::V1_0::SensorType::MAGNETOMETER)) {
        os += (first ? "" : " | ");
        os += "MAGNETOMETER";
        first = false;
        flipped |= ::android::hardware::contexthub::V1_0::SensorType::MAGNETOMETER;
    }
    if ((o & ::android::hardware::contexthub::V1_0::SensorType::BAROMETER) == static_cast<uint32_t>(::android::hardware::contexthub::V1_0::SensorType::BAROMETER)) {
        os += (first ? "" : " | ");
        os += "BAROMETER";
        first = false;
        flipped |= ::android::hardware::contexthub::V1_0::SensorType::BAROMETER;
    }
    if ((o & ::android::hardware::contexthub::V1_0::SensorType::PROXIMITY_SENSOR) == static_cast<uint32_t>(::android::hardware::contexthub::V1_0::SensorType::PROXIMITY_SENSOR)) {
        os += (first ? "" : " | ");
        os += "PROXIMITY_SENSOR";
        first = false;
        flipped |= ::android::hardware::contexthub::V1_0::SensorType::PROXIMITY_SENSOR;
    }
    if ((o & ::android::hardware::contexthub::V1_0::SensorType::AMBIENT_LIGHT_SENSOR) == static_cast<uint32_t>(::android::hardware::contexthub::V1_0::SensorType::AMBIENT_LIGHT_SENSOR)) {
        os += (first ? "" : " | ");
        os += "AMBIENT_LIGHT_SENSOR";
        first = false;
        flipped |= ::android::hardware::contexthub::V1_0::SensorType::AMBIENT_LIGHT_SENSOR;
    }
    if ((o & ::android::hardware::contexthub::V1_0::SensorType::STATIONARY_DETECT) == static_cast<uint32_t>(::android::hardware::contexthub::V1_0::SensorType::STATIONARY_DETECT)) {
        os += (first ? "" : " | ");
        os += "STATIONARY_DETECT";
        first = false;
        flipped |= ::android::hardware::contexthub::V1_0::SensorType::STATIONARY_DETECT;
    }
    if ((o & ::android::hardware::contexthub::V1_0::SensorType::INSTANT_MOTION_DETECT) == static_cast<uint32_t>(::android::hardware::contexthub::V1_0::SensorType::INSTANT_MOTION_DETECT)) {
        os += (first ? "" : " | ");
        os += "INSTANT_MOTION_DETECT";
        first = false;
        flipped |= ::android::hardware::contexthub::V1_0::SensorType::INSTANT_MOTION_DETECT;
    }
    if ((o & ::android::hardware::contexthub::V1_0::SensorType::GPS) == static_cast<uint32_t>(::android::hardware::contexthub::V1_0::SensorType::GPS)) {
        os += (first ? "" : " | ");
        os += "GPS";
        first = false;
        flipped |= ::android::hardware::contexthub::V1_0::SensorType::GPS;
    }
    if ((o & ::android::hardware::contexthub::V1_0::SensorType::WIFI) == static_cast<uint32_t>(::android::hardware::contexthub::V1_0::SensorType::WIFI)) {
        os += (first ? "" : " | ");
        os += "WIFI";
        first = false;
        flipped |= ::android::hardware::contexthub::V1_0::SensorType::WIFI;
    }
    if ((o & ::android::hardware::contexthub::V1_0::SensorType::AUDIO) == static_cast<uint32_t>(::android::hardware::contexthub::V1_0::SensorType::AUDIO)) {
        os += (first ? "" : " | ");
        os += "AUDIO";
        first = false;
        flipped |= ::android::hardware::contexthub::V1_0::SensorType::AUDIO;
    }
    if ((o & ::android::hardware::contexthub::V1_0::SensorType::CAMERA) == static_cast<uint32_t>(::android::hardware::contexthub::V1_0::SensorType::CAMERA)) {
        os += (first ? "" : " | ");
        os += "CAMERA";
        first = false;
        flipped |= ::android::hardware::contexthub::V1_0::SensorType::CAMERA;
    }
    if ((o & ::android::hardware::contexthub::V1_0::SensorType::BLE) == static_cast<uint32_t>(::android::hardware::contexthub::V1_0::SensorType::BLE)) {
        os += (first ? "" : " | ");
        os += "BLE";
        first = false;
        flipped |= ::android::hardware::contexthub::V1_0::SensorType::BLE;
    }
    if ((o & ::android::hardware::contexthub::V1_0::SensorType::WWAN) == static_cast<uint32_t>(::android::hardware::contexthub::V1_0::SensorType::WWAN)) {
        os += (first ? "" : " | ");
        os += "WWAN";
        first = false;
        flipped |= ::android::hardware::contexthub::V1_0::SensorType::WWAN;
    }
    if ((o & ::android::hardware::contexthub::V1_0::SensorType::PRIVATE_SENSOR_BASE) == static_cast<uint32_t>(::android::hardware::contexthub::V1_0::SensorType::PRIVATE_SENSOR_BASE)) {
        os += (first ? "" : " | ");
        os += "PRIVATE_SENSOR_BASE";
        first = false;
        flipped |= ::android::hardware::contexthub::V1_0::SensorType::PRIVATE_SENSOR_BASE;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::contexthub::V1_0::SensorType o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::contexthub::V1_0::SensorType::RESERVED) {
        return "RESERVED";
    }
    if (o == ::android::hardware::contexthub::V1_0::SensorType::ACCELEROMETER) {
        return "ACCELEROMETER";
    }
    if (o == ::android::hardware::contexthub::V1_0::SensorType::GYROSCOPE) {
        return "GYROSCOPE";
    }
    if (o == ::android::hardware::contexthub::V1_0::SensorType::MAGNETOMETER) {
        return "MAGNETOMETER";
    }
    if (o == ::android::hardware::contexthub::V1_0::SensorType::BAROMETER) {
        return "BAROMETER";
    }
    if (o == ::android::hardware::contexthub::V1_0::SensorType::PROXIMITY_SENSOR) {
        return "PROXIMITY_SENSOR";
    }
    if (o == ::android::hardware::contexthub::V1_0::SensorType::AMBIENT_LIGHT_SENSOR) {
        return "AMBIENT_LIGHT_SENSOR";
    }
    if (o == ::android::hardware::contexthub::V1_0::SensorType::STATIONARY_DETECT) {
        return "STATIONARY_DETECT";
    }
    if (o == ::android::hardware::contexthub::V1_0::SensorType::INSTANT_MOTION_DETECT) {
        return "INSTANT_MOTION_DETECT";
    }
    if (o == ::android::hardware::contexthub::V1_0::SensorType::GPS) {
        return "GPS";
    }
    if (o == ::android::hardware::contexthub::V1_0::SensorType::WIFI) {
        return "WIFI";
    }
    if (o == ::android::hardware::contexthub::V1_0::SensorType::AUDIO) {
        return "AUDIO";
    }
    if (o == ::android::hardware::contexthub::V1_0::SensorType::CAMERA) {
        return "CAMERA";
    }
    if (o == ::android::hardware::contexthub::V1_0::SensorType::BLE) {
        return "BLE";
    }
    if (o == ::android::hardware::contexthub::V1_0::SensorType::WWAN) {
        return "WWAN";
    }
    if (o == ::android::hardware::contexthub::V1_0::SensorType::PRIVATE_SENSOR_BASE) {
        return "PRIVATE_SENSOR_BASE";
    }
    std::string os;
    os += toHexString(static_cast<uint32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::contexthub::V1_0::SensorType o, ::std::ostream* os) {
    *os << toString(o);
}

static inline std::string toString(const ::android::hardware::contexthub::V1_0::PhysicalSensor& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".sensorType = ";
    os += ::android::hardware::contexthub::V1_0::toString(o.sensorType);
    os += ", .type = ";
    os += ::android::hardware::toString(o.type);
    os += ", .name = ";
    os += ::android::hardware::toString(o.name);
    os += ", .vendor = ";
    os += ::android::hardware::toString(o.vendor);
    os += ", .version = ";
    os += ::android::hardware::toString(o.version);
    os += ", .fifoReservedCount = ";
    os += ::android::hardware::toString(o.fifoReservedCount);
    os += ", .fifoMaxCount = ";
    os += ::android::hardware::toString(o.fifoMaxCount);
    os += ", .minDelayMs = ";
    os += ::android::hardware::toString(o.minDelayMs);
    os += ", .maxDelayMs = ";
    os += ::android::hardware::toString(o.maxDelayMs);
    os += ", .peakPowerMw = ";
    os += ::android::hardware::toString(o.peakPowerMw);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::contexthub::V1_0::PhysicalSensor& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::contexthub::V1_0::PhysicalSensor& lhs, const ::android::hardware::contexthub::V1_0::PhysicalSensor& rhs) {
    if (lhs.sensorType != rhs.sensorType) {
        return false;
    }
    if (lhs.type != rhs.type) {
        return false;
    }
    if (lhs.name != rhs.name) {
        return false;
    }
    if (lhs.vendor != rhs.vendor) {
        return false;
    }
    if (lhs.version != rhs.version) {
        return false;
    }
    if (lhs.fifoReservedCount != rhs.fifoReservedCount) {
        return false;
    }
    if (lhs.fifoMaxCount != rhs.fifoMaxCount) {
        return false;
    }
    if (lhs.minDelayMs != rhs.minDelayMs) {
        return false;
    }
    if (lhs.maxDelayMs != rhs.maxDelayMs) {
        return false;
    }
    if (lhs.peakPowerMw != rhs.peakPowerMw) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::contexthub::V1_0::PhysicalSensor& lhs, const ::android::hardware::contexthub::V1_0::PhysicalSensor& rhs){
    return !(lhs == rhs);
}

static inline std::string toString(const ::android::hardware::contexthub::V1_0::ContextHub& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".name = ";
    os += ::android::hardware::toString(o.name);
    os += ", .vendor = ";
    os += ::android::hardware::toString(o.vendor);
    os += ", .toolchain = ";
    os += ::android::hardware::toString(o.toolchain);
    os += ", .platformVersion = ";
    os += ::android::hardware::toString(o.platformVersion);
    os += ", .toolchainVersion = ";
    os += ::android::hardware::toString(o.toolchainVersion);
    os += ", .hubId = ";
    os += ::android::hardware::toString(o.hubId);
    os += ", .peakMips = ";
    os += ::android::hardware::toString(o.peakMips);
    os += ", .stoppedPowerDrawMw = ";
    os += ::android::hardware::toString(o.stoppedPowerDrawMw);
    os += ", .sleepPowerDrawMw = ";
    os += ::android::hardware::toString(o.sleepPowerDrawMw);
    os += ", .peakPowerDrawMw = ";
    os += ::android::hardware::toString(o.peakPowerDrawMw);
    os += ", .connectedSensors = ";
    os += ::android::hardware::toString(o.connectedSensors);
    os += ", .maxSupportedMsgLen = ";
    os += ::android::hardware::toString(o.maxSupportedMsgLen);
    os += ", .chrePlatformId = ";
    os += ::android::hardware::toString(o.chrePlatformId);
    os += ", .chreApiMajorVersion = ";
    os += ::android::hardware::toString(o.chreApiMajorVersion);
    os += ", .chreApiMinorVersion = ";
    os += ::android::hardware::toString(o.chreApiMinorVersion);
    os += ", .chrePatchVersion = ";
    os += ::android::hardware::toString(o.chrePatchVersion);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::contexthub::V1_0::ContextHub& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::contexthub::V1_0::ContextHub& lhs, const ::android::hardware::contexthub::V1_0::ContextHub& rhs) {
    if (lhs.name != rhs.name) {
        return false;
    }
    if (lhs.vendor != rhs.vendor) {
        return false;
    }
    if (lhs.toolchain != rhs.toolchain) {
        return false;
    }
    if (lhs.platformVersion != rhs.platformVersion) {
        return false;
    }
    if (lhs.toolchainVersion != rhs.toolchainVersion) {
        return false;
    }
    if (lhs.hubId != rhs.hubId) {
        return false;
    }
    if (lhs.peakMips != rhs.peakMips) {
        return false;
    }
    if (lhs.stoppedPowerDrawMw != rhs.stoppedPowerDrawMw) {
        return false;
    }
    if (lhs.sleepPowerDrawMw != rhs.sleepPowerDrawMw) {
        return false;
    }
    if (lhs.peakPowerDrawMw != rhs.peakPowerDrawMw) {
        return false;
    }
    if (lhs.connectedSensors != rhs.connectedSensors) {
        return false;
    }
    if (lhs.maxSupportedMsgLen != rhs.maxSupportedMsgLen) {
        return false;
    }
    if (lhs.chrePlatformId != rhs.chrePlatformId) {
        return false;
    }
    if (lhs.chreApiMajorVersion != rhs.chreApiMajorVersion) {
        return false;
    }
    if (lhs.chreApiMinorVersion != rhs.chreApiMinorVersion) {
        return false;
    }
    if (lhs.chrePatchVersion != rhs.chrePatchVersion) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::contexthub::V1_0::ContextHub& lhs, const ::android::hardware::contexthub::V1_0::ContextHub& rhs){
    return !(lhs == rhs);
}

template<>
inline std::string toString<::android::hardware::contexthub::V1_0::HostEndPoint>(uint16_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::contexthub::V1_0::HostEndPoint> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::contexthub::V1_0::HostEndPoint::BROADCAST) == static_cast<uint16_t>(::android::hardware::contexthub::V1_0::HostEndPoint::BROADCAST)) {
        os += (first ? "" : " | ");
        os += "BROADCAST";
        first = false;
        flipped |= ::android::hardware::contexthub::V1_0::HostEndPoint::BROADCAST;
    }
    if ((o & ::android::hardware::contexthub::V1_0::HostEndPoint::UNSPECIFIED) == static_cast<uint16_t>(::android::hardware::contexthub::V1_0::HostEndPoint::UNSPECIFIED)) {
        os += (first ? "" : " | ");
        os += "UNSPECIFIED";
        first = false;
        flipped |= ::android::hardware::contexthub::V1_0::HostEndPoint::UNSPECIFIED;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::contexthub::V1_0::HostEndPoint o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::contexthub::V1_0::HostEndPoint::BROADCAST) {
        return "BROADCAST";
    }
    if (o == ::android::hardware::contexthub::V1_0::HostEndPoint::UNSPECIFIED) {
        return "UNSPECIFIED";
    }
    std::string os;
    os += toHexString(static_cast<uint16_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::contexthub::V1_0::HostEndPoint o, ::std::ostream* os) {
    *os << toString(o);
}

static inline std::string toString(const ::android::hardware::contexthub::V1_0::ContextHubMsg& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".appName = ";
    os += ::android::hardware::toString(o.appName);
    os += ", .hostEndPoint = ";
    os += ::android::hardware::toString(o.hostEndPoint);
    os += ", .msgType = ";
    os += ::android::hardware::toString(o.msgType);
    os += ", .msg = ";
    os += ::android::hardware::toString(o.msg);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::contexthub::V1_0::ContextHubMsg& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::contexthub::V1_0::ContextHubMsg& lhs, const ::android::hardware::contexthub::V1_0::ContextHubMsg& rhs) {
    if (lhs.appName != rhs.appName) {
        return false;
    }
    if (lhs.hostEndPoint != rhs.hostEndPoint) {
        return false;
    }
    if (lhs.msgType != rhs.msgType) {
        return false;
    }
    if (lhs.msg != rhs.msg) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::contexthub::V1_0::ContextHubMsg& lhs, const ::android::hardware::contexthub::V1_0::ContextHubMsg& rhs){
    return !(lhs == rhs);
}

template<>
inline std::string toString<::android::hardware::contexthub::V1_0::HubMemoryType>(uint32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::contexthub::V1_0::HubMemoryType> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::contexthub::V1_0::HubMemoryType::MAIN) == static_cast<uint32_t>(::android::hardware::contexthub::V1_0::HubMemoryType::MAIN)) {
        os += (first ? "" : " | ");
        os += "MAIN";
        first = false;
        flipped |= ::android::hardware::contexthub::V1_0::HubMemoryType::MAIN;
    }
    if ((o & ::android::hardware::contexthub::V1_0::HubMemoryType::SECONDARY) == static_cast<uint32_t>(::android::hardware::contexthub::V1_0::HubMemoryType::SECONDARY)) {
        os += (first ? "" : " | ");
        os += "SECONDARY";
        first = false;
        flipped |= ::android::hardware::contexthub::V1_0::HubMemoryType::SECONDARY;
    }
    if ((o & ::android::hardware::contexthub::V1_0::HubMemoryType::TCM) == static_cast<uint32_t>(::android::hardware::contexthub::V1_0::HubMemoryType::TCM)) {
        os += (first ? "" : " | ");
        os += "TCM";
        first = false;
        flipped |= ::android::hardware::contexthub::V1_0::HubMemoryType::TCM;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::contexthub::V1_0::HubMemoryType o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::contexthub::V1_0::HubMemoryType::MAIN) {
        return "MAIN";
    }
    if (o == ::android::hardware::contexthub::V1_0::HubMemoryType::SECONDARY) {
        return "SECONDARY";
    }
    if (o == ::android::hardware::contexthub::V1_0::HubMemoryType::TCM) {
        return "TCM";
    }
    std::string os;
    os += toHexString(static_cast<uint32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::contexthub::V1_0::HubMemoryType o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::contexthub::V1_0::HubMemoryFlag>(uint32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::contexthub::V1_0::HubMemoryFlag> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::contexthub::V1_0::HubMemoryFlag::READ) == static_cast<uint32_t>(::android::hardware::contexthub::V1_0::HubMemoryFlag::READ)) {
        os += (first ? "" : " | ");
        os += "READ";
        first = false;
        flipped |= ::android::hardware::contexthub::V1_0::HubMemoryFlag::READ;
    }
    if ((o & ::android::hardware::contexthub::V1_0::HubMemoryFlag::WRITE) == static_cast<uint32_t>(::android::hardware::contexthub::V1_0::HubMemoryFlag::WRITE)) {
        os += (first ? "" : " | ");
        os += "WRITE";
        first = false;
        flipped |= ::android::hardware::contexthub::V1_0::HubMemoryFlag::WRITE;
    }
    if ((o & ::android::hardware::contexthub::V1_0::HubMemoryFlag::EXEC) == static_cast<uint32_t>(::android::hardware::contexthub::V1_0::HubMemoryFlag::EXEC)) {
        os += (first ? "" : " | ");
        os += "EXEC";
        first = false;
        flipped |= ::android::hardware::contexthub::V1_0::HubMemoryFlag::EXEC;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::contexthub::V1_0::HubMemoryFlag o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::contexthub::V1_0::HubMemoryFlag::READ) {
        return "READ";
    }
    if (o == ::android::hardware::contexthub::V1_0::HubMemoryFlag::WRITE) {
        return "WRITE";
    }
    if (o == ::android::hardware::contexthub::V1_0::HubMemoryFlag::EXEC) {
        return "EXEC";
    }
    std::string os;
    os += toHexString(static_cast<uint32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::contexthub::V1_0::HubMemoryFlag o, ::std::ostream* os) {
    *os << toString(o);
}

static inline std::string toString(const ::android::hardware::contexthub::V1_0::MemRange& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".totalBytes = ";
    os += ::android::hardware::toString(o.totalBytes);
    os += ", .freeBytes = ";
    os += ::android::hardware::toString(o.freeBytes);
    os += ", .type = ";
    os += ::android::hardware::contexthub::V1_0::toString(o.type);
    os += ", .flags = ";
    os += ::android::hardware::contexthub::V1_0::toString<::android::hardware::contexthub::V1_0::HubMemoryFlag>(o.flags);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::contexthub::V1_0::MemRange& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::contexthub::V1_0::MemRange& lhs, const ::android::hardware::contexthub::V1_0::MemRange& rhs) {
    if (lhs.totalBytes != rhs.totalBytes) {
        return false;
    }
    if (lhs.freeBytes != rhs.freeBytes) {
        return false;
    }
    if (lhs.type != rhs.type) {
        return false;
    }
    if (lhs.flags != rhs.flags) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::contexthub::V1_0::MemRange& lhs, const ::android::hardware::contexthub::V1_0::MemRange& rhs){
    return !(lhs == rhs);
}

template<>
inline std::string toString<::android::hardware::contexthub::V1_0::AsyncEventType>(uint32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::contexthub::V1_0::AsyncEventType> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::contexthub::V1_0::AsyncEventType::RESTARTED) == static_cast<uint32_t>(::android::hardware::contexthub::V1_0::AsyncEventType::RESTARTED)) {
        os += (first ? "" : " | ");
        os += "RESTARTED";
        first = false;
        flipped |= ::android::hardware::contexthub::V1_0::AsyncEventType::RESTARTED;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::contexthub::V1_0::AsyncEventType o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::contexthub::V1_0::AsyncEventType::RESTARTED) {
        return "RESTARTED";
    }
    std::string os;
    os += toHexString(static_cast<uint32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::contexthub::V1_0::AsyncEventType o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::contexthub::V1_0::TransactionResult>(int32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::contexthub::V1_0::TransactionResult> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::contexthub::V1_0::TransactionResult::SUCCESS) == static_cast<int32_t>(::android::hardware::contexthub::V1_0::TransactionResult::SUCCESS)) {
        os += (first ? "" : " | ");
        os += "SUCCESS";
        first = false;
        flipped |= ::android::hardware::contexthub::V1_0::TransactionResult::SUCCESS;
    }
    if ((o & ::android::hardware::contexthub::V1_0::TransactionResult::FAILURE) == static_cast<int32_t>(::android::hardware::contexthub::V1_0::TransactionResult::FAILURE)) {
        os += (first ? "" : " | ");
        os += "FAILURE";
        first = false;
        flipped |= ::android::hardware::contexthub::V1_0::TransactionResult::FAILURE;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::contexthub::V1_0::TransactionResult o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::contexthub::V1_0::TransactionResult::SUCCESS) {
        return "SUCCESS";
    }
    if (o == ::android::hardware::contexthub::V1_0::TransactionResult::FAILURE) {
        return "FAILURE";
    }
    std::string os;
    os += toHexString(static_cast<int32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::contexthub::V1_0::TransactionResult o, ::std::ostream* os) {
    *os << toString(o);
}

static inline std::string toString(const ::android::hardware::contexthub::V1_0::HubAppInfo& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".appId = ";
    os += ::android::hardware::toString(o.appId);
    os += ", .version = ";
    os += ::android::hardware::toString(o.version);
    os += ", .memUsage = ";
    os += ::android::hardware::toString(o.memUsage);
    os += ", .enabled = ";
    os += ::android::hardware::toString(o.enabled);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::contexthub::V1_0::HubAppInfo& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::contexthub::V1_0::HubAppInfo& lhs, const ::android::hardware::contexthub::V1_0::HubAppInfo& rhs) {
    if (lhs.appId != rhs.appId) {
        return false;
    }
    if (lhs.version != rhs.version) {
        return false;
    }
    if (lhs.memUsage != rhs.memUsage) {
        return false;
    }
    if (lhs.enabled != rhs.enabled) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::contexthub::V1_0::HubAppInfo& lhs, const ::android::hardware::contexthub::V1_0::HubAppInfo& rhs){
    return !(lhs == rhs);
}


}  // namespace V1_0
}  // namespace contexthub
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
template<> inline constexpr std::array<::android::hardware::contexthub::V1_0::Result, 6> hidl_enum_values<::android::hardware::contexthub::V1_0::Result> = {
    ::android::hardware::contexthub::V1_0::Result::OK,
    ::android::hardware::contexthub::V1_0::Result::UNKNOWN_FAILURE,
    ::android::hardware::contexthub::V1_0::Result::BAD_PARAMS,
    ::android::hardware::contexthub::V1_0::Result::NOT_INIT,
    ::android::hardware::contexthub::V1_0::Result::TRANSACTION_FAILED,
    ::android::hardware::contexthub::V1_0::Result::TRANSACTION_PENDING,
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
template<> inline constexpr std::array<::android::hardware::contexthub::V1_0::NanoAppFlags, 2> hidl_enum_values<::android::hardware::contexthub::V1_0::NanoAppFlags> = {
    ::android::hardware::contexthub::V1_0::NanoAppFlags::SIGNED,
    ::android::hardware::contexthub::V1_0::NanoAppFlags::ENCRYPTED,
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
template<> inline constexpr std::array<::android::hardware::contexthub::V1_0::SensorType, 16> hidl_enum_values<::android::hardware::contexthub::V1_0::SensorType> = {
    ::android::hardware::contexthub::V1_0::SensorType::RESERVED,
    ::android::hardware::contexthub::V1_0::SensorType::ACCELEROMETER,
    ::android::hardware::contexthub::V1_0::SensorType::GYROSCOPE,
    ::android::hardware::contexthub::V1_0::SensorType::MAGNETOMETER,
    ::android::hardware::contexthub::V1_0::SensorType::BAROMETER,
    ::android::hardware::contexthub::V1_0::SensorType::PROXIMITY_SENSOR,
    ::android::hardware::contexthub::V1_0::SensorType::AMBIENT_LIGHT_SENSOR,
    ::android::hardware::contexthub::V1_0::SensorType::STATIONARY_DETECT,
    ::android::hardware::contexthub::V1_0::SensorType::INSTANT_MOTION_DETECT,
    ::android::hardware::contexthub::V1_0::SensorType::GPS,
    ::android::hardware::contexthub::V1_0::SensorType::WIFI,
    ::android::hardware::contexthub::V1_0::SensorType::AUDIO,
    ::android::hardware::contexthub::V1_0::SensorType::CAMERA,
    ::android::hardware::contexthub::V1_0::SensorType::BLE,
    ::android::hardware::contexthub::V1_0::SensorType::WWAN,
    ::android::hardware::contexthub::V1_0::SensorType::PRIVATE_SENSOR_BASE,
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
template<> inline constexpr std::array<::android::hardware::contexthub::V1_0::HostEndPoint, 2> hidl_enum_values<::android::hardware::contexthub::V1_0::HostEndPoint> = {
    ::android::hardware::contexthub::V1_0::HostEndPoint::BROADCAST,
    ::android::hardware::contexthub::V1_0::HostEndPoint::UNSPECIFIED,
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
template<> inline constexpr std::array<::android::hardware::contexthub::V1_0::HubMemoryType, 3> hidl_enum_values<::android::hardware::contexthub::V1_0::HubMemoryType> = {
    ::android::hardware::contexthub::V1_0::HubMemoryType::MAIN,
    ::android::hardware::contexthub::V1_0::HubMemoryType::SECONDARY,
    ::android::hardware::contexthub::V1_0::HubMemoryType::TCM,
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
template<> inline constexpr std::array<::android::hardware::contexthub::V1_0::HubMemoryFlag, 3> hidl_enum_values<::android::hardware::contexthub::V1_0::HubMemoryFlag> = {
    ::android::hardware::contexthub::V1_0::HubMemoryFlag::READ,
    ::android::hardware::contexthub::V1_0::HubMemoryFlag::WRITE,
    ::android::hardware::contexthub::V1_0::HubMemoryFlag::EXEC,
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
template<> inline constexpr std::array<::android::hardware::contexthub::V1_0::AsyncEventType, 1> hidl_enum_values<::android::hardware::contexthub::V1_0::AsyncEventType> = {
    ::android::hardware::contexthub::V1_0::AsyncEventType::RESTARTED,
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
template<> inline constexpr std::array<::android::hardware::contexthub::V1_0::TransactionResult, 2> hidl_enum_values<::android::hardware::contexthub::V1_0::TransactionResult> = {
    ::android::hardware::contexthub::V1_0::TransactionResult::SUCCESS,
    ::android::hardware::contexthub::V1_0::TransactionResult::FAILURE,
};
#pragma clang diagnostic pop
}  // namespace details
}  // namespace hardware
}  // namespace android


#endif  // HIDL_GENERATED_ANDROID_HARDWARE_CONTEXTHUB_V1_0_TYPES_H
