#ifndef HIDL_GENERATED_ANDROID_HARDWARE_AUTOMOTIVE_EVS_V1_0_TYPES_H
#define HIDL_GENERATED_ANDROID_HARDWARE_AUTOMOTIVE_EVS_V1_0_TYPES_H

#include <hidl/HidlSupport.h>
#include <hidl/MQDescriptor.h>
#include <utils/NativeHandle.h>
#include <utils/misc.h>

namespace android {
namespace hardware {
namespace automotive {
namespace evs {
namespace V1_0 {

// Forward declaration for forward reference support:
struct CameraDesc;
struct DisplayDesc;
struct BufferDesc;
enum class DisplayState : uint32_t;
enum class EvsResult : uint32_t;

/**
 * Structure describing the basic properties of an EVS camera
 *
 * The HAL is responsible for filling out this structure for each
 * EVS camera in the system.
 */
struct CameraDesc final {
    /*
     * Unique identifier for camera devices.  This may be a path to detected
     * camera device; for example, "/dev/video0".
     */
    ::android::hardware::hidl_string cameraId __attribute__ ((aligned(8)));
    /*
     * Opaque value from driver.  Vendor may use this field to store additional
     * information; for example, sensor and bridge chip id.
     */
    uint32_t vendorFlags __attribute__ ((aligned(4)));
};

static_assert(offsetof(::android::hardware::automotive::evs::V1_0::CameraDesc, cameraId) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::automotive::evs::V1_0::CameraDesc, vendorFlags) == 16, "wrong offset");
static_assert(sizeof(::android::hardware::automotive::evs::V1_0::CameraDesc) == 24, "wrong size");
static_assert(__alignof(::android::hardware::automotive::evs::V1_0::CameraDesc) == 8, "wrong alignment");

/**
 * Structure describing the basic properties of an EVS display
 *
 * The HAL is responsible for filling out this structure to describe
 * the EVS display. As an implementation detail, this may be a physical
 * display or a virtual display that is overlaid or mixed with another
 * presentation device.
 */
struct DisplayDesc final {
    /*
     * Unique identifier for the display
     */
    ::android::hardware::hidl_string displayId __attribute__ ((aligned(8)));
    /*
     * Opaque value from driver
     */
    uint32_t vendorFlags __attribute__ ((aligned(4)));
};

static_assert(offsetof(::android::hardware::automotive::evs::V1_0::DisplayDesc, displayId) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::automotive::evs::V1_0::DisplayDesc, vendorFlags) == 16, "wrong offset");
static_assert(sizeof(::android::hardware::automotive::evs::V1_0::DisplayDesc) == 24, "wrong size");
static_assert(__alignof(::android::hardware::automotive::evs::V1_0::DisplayDesc) == 8, "wrong alignment");

/**
 * Structure representing an image buffer through our APIs
 *
 * In addition to the handle to the graphics memory, we need to retain
 * the properties of the buffer for easy reference and reconstruction of
 * an ANativeWindowBuffer object on the remote side of API calls.
 * (Not least because OpenGL expect an ANativeWindowBuffer* for us as a
 * texture via eglCreateImageKHR().
 * See also related types from android.hardware.graphics.common
 * TODO:  b/34722508  Review details of interaction of this structure with gralloc and OpenGL.
 *        Specifically consider if format and/or usage should become enumerated types.
 */
struct BufferDesc final {
    /*
     * A frame width in the units of pixels
     */
    uint32_t width __attribute__ ((aligned(4)));
    /*
     * A frame height in the units of pixels
     */
    uint32_t height __attribute__ ((aligned(4)));
    /*
     * A frame stride in the units of pixels, to match gralloc
     */
    uint32_t stride __attribute__ ((aligned(4)));
    /*
     * The size of a pixel in the units of bytes
     */
    uint32_t pixelSize __attribute__ ((aligned(4)));
    /*
     * The image format of the frame; may contain values from
     * android_pixel_format_t
     */
    uint32_t format __attribute__ ((aligned(4)));
    /*
     * May contain values from Gralloc.h
     */
    uint32_t usage __attribute__ ((aligned(4)));
    /*
     * Opaque value from driver
     */
    uint32_t bufferId __attribute__ ((aligned(4)));
    /*
     * Gralloc memory buffer handle
     */
    ::android::hardware::hidl_handle memHandle __attribute__ ((aligned(8)));
};

static_assert(offsetof(::android::hardware::automotive::evs::V1_0::BufferDesc, width) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::automotive::evs::V1_0::BufferDesc, height) == 4, "wrong offset");
static_assert(offsetof(::android::hardware::automotive::evs::V1_0::BufferDesc, stride) == 8, "wrong offset");
static_assert(offsetof(::android::hardware::automotive::evs::V1_0::BufferDesc, pixelSize) == 12, "wrong offset");
static_assert(offsetof(::android::hardware::automotive::evs::V1_0::BufferDesc, format) == 16, "wrong offset");
static_assert(offsetof(::android::hardware::automotive::evs::V1_0::BufferDesc, usage) == 20, "wrong offset");
static_assert(offsetof(::android::hardware::automotive::evs::V1_0::BufferDesc, bufferId) == 24, "wrong offset");
static_assert(offsetof(::android::hardware::automotive::evs::V1_0::BufferDesc, memHandle) == 32, "wrong offset");
static_assert(sizeof(::android::hardware::automotive::evs::V1_0::BufferDesc) == 48, "wrong size");
static_assert(__alignof(::android::hardware::automotive::evs::V1_0::BufferDesc) == 8, "wrong alignment");

/**
 * States for control of the EVS display
 *
 * The DisplayInfo structure describes the basic properties of an EVS display. Any EVS
 * implementation is required to have one. The HAL is responsible for filling out this
 * structure to describe the EVS display. As an implementation detail, this may be a
 * physical display or a virtual display that is overlaid or mixed with another
 * presentation device.
 */
enum class DisplayState : uint32_t {
    /*
     * Display has not been requested by any application yet
     */
    NOT_OPEN = 0u,
    /*
     * Display is inhibited
     */
    NOT_VISIBLE = 1u /* ::android::hardware::automotive::evs::V1_0::DisplayState.NOT_OPEN implicitly + 1 */,
    /*
     * Will become visible with next frame
     */
    VISIBLE_ON_NEXT_FRAME = 2u /* ::android::hardware::automotive::evs::V1_0::DisplayState.NOT_VISIBLE implicitly + 1 */,
    /*
     * Display is currently active
     */
    VISIBLE = 3u /* ::android::hardware::automotive::evs::V1_0::DisplayState.VISIBLE_ON_NEXT_FRAME implicitly + 1 */,
    /*
     * Driver is in an undefined state.  Interface should be closed.
     */
    DEAD = 4u /* ::android::hardware::automotive::evs::V1_0::DisplayState.VISIBLE implicitly + 1 */,
    /*
     * Must be the last
     */
    NUM_STATES = 5u /* ::android::hardware::automotive::evs::V1_0::DisplayState.DEAD implicitly + 1 */,
};

/**
 * Error codes used in EVS HAL interface.
 */
enum class EvsResult : uint32_t {
    OK = 0u,
    INVALID_ARG = 1u /* ::android::hardware::automotive::evs::V1_0::EvsResult.OK implicitly + 1 */,
    STREAM_ALREADY_RUNNING = 2u /* ::android::hardware::automotive::evs::V1_0::EvsResult.INVALID_ARG implicitly + 1 */,
    BUFFER_NOT_AVAILABLE = 3u /* ::android::hardware::automotive::evs::V1_0::EvsResult.STREAM_ALREADY_RUNNING implicitly + 1 */,
    OWNERSHIP_LOST = 4u /* ::android::hardware::automotive::evs::V1_0::EvsResult.BUFFER_NOT_AVAILABLE implicitly + 1 */,
    UNDERLYING_SERVICE_ERROR = 5u /* ::android::hardware::automotive::evs::V1_0::EvsResult.OWNERSHIP_LOST implicitly + 1 */,
};

//
// type declarations for package
//

static inline std::string toString(const ::android::hardware::automotive::evs::V1_0::CameraDesc& o);
static inline void PrintTo(const ::android::hardware::automotive::evs::V1_0::CameraDesc& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::automotive::evs::V1_0::CameraDesc& lhs, const ::android::hardware::automotive::evs::V1_0::CameraDesc& rhs);
static inline bool operator!=(const ::android::hardware::automotive::evs::V1_0::CameraDesc& lhs, const ::android::hardware::automotive::evs::V1_0::CameraDesc& rhs);

static inline std::string toString(const ::android::hardware::automotive::evs::V1_0::DisplayDesc& o);
static inline void PrintTo(const ::android::hardware::automotive::evs::V1_0::DisplayDesc& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::automotive::evs::V1_0::DisplayDesc& lhs, const ::android::hardware::automotive::evs::V1_0::DisplayDesc& rhs);
static inline bool operator!=(const ::android::hardware::automotive::evs::V1_0::DisplayDesc& lhs, const ::android::hardware::automotive::evs::V1_0::DisplayDesc& rhs);

static inline std::string toString(const ::android::hardware::automotive::evs::V1_0::BufferDesc& o);
static inline void PrintTo(const ::android::hardware::automotive::evs::V1_0::BufferDesc& o, ::std::ostream*);
// operator== and operator!= are not generated for BufferDesc

template<typename>
static inline std::string toString(uint32_t o);
static inline std::string toString(::android::hardware::automotive::evs::V1_0::DisplayState o);
static inline void PrintTo(::android::hardware::automotive::evs::V1_0::DisplayState o, ::std::ostream* os);
constexpr uint32_t operator|(const ::android::hardware::automotive::evs::V1_0::DisplayState lhs, const ::android::hardware::automotive::evs::V1_0::DisplayState rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const uint32_t lhs, const ::android::hardware::automotive::evs::V1_0::DisplayState rhs) {
    return static_cast<uint32_t>(lhs | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const ::android::hardware::automotive::evs::V1_0::DisplayState lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | rhs);
}
constexpr uint32_t operator&(const ::android::hardware::automotive::evs::V1_0::DisplayState lhs, const ::android::hardware::automotive::evs::V1_0::DisplayState rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const uint32_t lhs, const ::android::hardware::automotive::evs::V1_0::DisplayState rhs) {
    return static_cast<uint32_t>(lhs & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const ::android::hardware::automotive::evs::V1_0::DisplayState lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & rhs);
}
constexpr uint32_t &operator|=(uint32_t& v, const ::android::hardware::automotive::evs::V1_0::DisplayState e) {
    v |= static_cast<uint32_t>(e);
    return v;
}
constexpr uint32_t &operator&=(uint32_t& v, const ::android::hardware::automotive::evs::V1_0::DisplayState e) {
    v &= static_cast<uint32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(uint32_t o);
static inline std::string toString(::android::hardware::automotive::evs::V1_0::EvsResult o);
static inline void PrintTo(::android::hardware::automotive::evs::V1_0::EvsResult o, ::std::ostream* os);
constexpr uint32_t operator|(const ::android::hardware::automotive::evs::V1_0::EvsResult lhs, const ::android::hardware::automotive::evs::V1_0::EvsResult rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const uint32_t lhs, const ::android::hardware::automotive::evs::V1_0::EvsResult rhs) {
    return static_cast<uint32_t>(lhs | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const ::android::hardware::automotive::evs::V1_0::EvsResult lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | rhs);
}
constexpr uint32_t operator&(const ::android::hardware::automotive::evs::V1_0::EvsResult lhs, const ::android::hardware::automotive::evs::V1_0::EvsResult rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const uint32_t lhs, const ::android::hardware::automotive::evs::V1_0::EvsResult rhs) {
    return static_cast<uint32_t>(lhs & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const ::android::hardware::automotive::evs::V1_0::EvsResult lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & rhs);
}
constexpr uint32_t &operator|=(uint32_t& v, const ::android::hardware::automotive::evs::V1_0::EvsResult e) {
    v |= static_cast<uint32_t>(e);
    return v;
}
constexpr uint32_t &operator&=(uint32_t& v, const ::android::hardware::automotive::evs::V1_0::EvsResult e) {
    v &= static_cast<uint32_t>(e);
    return v;
}

//
// type header definitions for package
//

static inline std::string toString(const ::android::hardware::automotive::evs::V1_0::CameraDesc& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".cameraId = ";
    os += ::android::hardware::toString(o.cameraId);
    os += ", .vendorFlags = ";
    os += ::android::hardware::toString(o.vendorFlags);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::automotive::evs::V1_0::CameraDesc& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::automotive::evs::V1_0::CameraDesc& lhs, const ::android::hardware::automotive::evs::V1_0::CameraDesc& rhs) {
    if (lhs.cameraId != rhs.cameraId) {
        return false;
    }
    if (lhs.vendorFlags != rhs.vendorFlags) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::automotive::evs::V1_0::CameraDesc& lhs, const ::android::hardware::automotive::evs::V1_0::CameraDesc& rhs){
    return !(lhs == rhs);
}

static inline std::string toString(const ::android::hardware::automotive::evs::V1_0::DisplayDesc& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".displayId = ";
    os += ::android::hardware::toString(o.displayId);
    os += ", .vendorFlags = ";
    os += ::android::hardware::toString(o.vendorFlags);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::automotive::evs::V1_0::DisplayDesc& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::automotive::evs::V1_0::DisplayDesc& lhs, const ::android::hardware::automotive::evs::V1_0::DisplayDesc& rhs) {
    if (lhs.displayId != rhs.displayId) {
        return false;
    }
    if (lhs.vendorFlags != rhs.vendorFlags) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::automotive::evs::V1_0::DisplayDesc& lhs, const ::android::hardware::automotive::evs::V1_0::DisplayDesc& rhs){
    return !(lhs == rhs);
}

static inline std::string toString(const ::android::hardware::automotive::evs::V1_0::BufferDesc& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".width = ";
    os += ::android::hardware::toString(o.width);
    os += ", .height = ";
    os += ::android::hardware::toString(o.height);
    os += ", .stride = ";
    os += ::android::hardware::toString(o.stride);
    os += ", .pixelSize = ";
    os += ::android::hardware::toString(o.pixelSize);
    os += ", .format = ";
    os += ::android::hardware::toString(o.format);
    os += ", .usage = ";
    os += ::android::hardware::toString(o.usage);
    os += ", .bufferId = ";
    os += ::android::hardware::toString(o.bufferId);
    os += ", .memHandle = ";
    os += ::android::hardware::toString(o.memHandle);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::automotive::evs::V1_0::BufferDesc& o, ::std::ostream* os) {
    *os << toString(o);
}

// operator== and operator!= are not generated for BufferDesc

template<>
inline std::string toString<::android::hardware::automotive::evs::V1_0::DisplayState>(uint32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::automotive::evs::V1_0::DisplayState> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::automotive::evs::V1_0::DisplayState::NOT_OPEN) == static_cast<uint32_t>(::android::hardware::automotive::evs::V1_0::DisplayState::NOT_OPEN)) {
        os += (first ? "" : " | ");
        os += "NOT_OPEN";
        first = false;
        flipped |= ::android::hardware::automotive::evs::V1_0::DisplayState::NOT_OPEN;
    }
    if ((o & ::android::hardware::automotive::evs::V1_0::DisplayState::NOT_VISIBLE) == static_cast<uint32_t>(::android::hardware::automotive::evs::V1_0::DisplayState::NOT_VISIBLE)) {
        os += (first ? "" : " | ");
        os += "NOT_VISIBLE";
        first = false;
        flipped |= ::android::hardware::automotive::evs::V1_0::DisplayState::NOT_VISIBLE;
    }
    if ((o & ::android::hardware::automotive::evs::V1_0::DisplayState::VISIBLE_ON_NEXT_FRAME) == static_cast<uint32_t>(::android::hardware::automotive::evs::V1_0::DisplayState::VISIBLE_ON_NEXT_FRAME)) {
        os += (first ? "" : " | ");
        os += "VISIBLE_ON_NEXT_FRAME";
        first = false;
        flipped |= ::android::hardware::automotive::evs::V1_0::DisplayState::VISIBLE_ON_NEXT_FRAME;
    }
    if ((o & ::android::hardware::automotive::evs::V1_0::DisplayState::VISIBLE) == static_cast<uint32_t>(::android::hardware::automotive::evs::V1_0::DisplayState::VISIBLE)) {
        os += (first ? "" : " | ");
        os += "VISIBLE";
        first = false;
        flipped |= ::android::hardware::automotive::evs::V1_0::DisplayState::VISIBLE;
    }
    if ((o & ::android::hardware::automotive::evs::V1_0::DisplayState::DEAD) == static_cast<uint32_t>(::android::hardware::automotive::evs::V1_0::DisplayState::DEAD)) {
        os += (first ? "" : " | ");
        os += "DEAD";
        first = false;
        flipped |= ::android::hardware::automotive::evs::V1_0::DisplayState::DEAD;
    }
    if ((o & ::android::hardware::automotive::evs::V1_0::DisplayState::NUM_STATES) == static_cast<uint32_t>(::android::hardware::automotive::evs::V1_0::DisplayState::NUM_STATES)) {
        os += (first ? "" : " | ");
        os += "NUM_STATES";
        first = false;
        flipped |= ::android::hardware::automotive::evs::V1_0::DisplayState::NUM_STATES;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::automotive::evs::V1_0::DisplayState o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::automotive::evs::V1_0::DisplayState::NOT_OPEN) {
        return "NOT_OPEN";
    }
    if (o == ::android::hardware::automotive::evs::V1_0::DisplayState::NOT_VISIBLE) {
        return "NOT_VISIBLE";
    }
    if (o == ::android::hardware::automotive::evs::V1_0::DisplayState::VISIBLE_ON_NEXT_FRAME) {
        return "VISIBLE_ON_NEXT_FRAME";
    }
    if (o == ::android::hardware::automotive::evs::V1_0::DisplayState::VISIBLE) {
        return "VISIBLE";
    }
    if (o == ::android::hardware::automotive::evs::V1_0::DisplayState::DEAD) {
        return "DEAD";
    }
    if (o == ::android::hardware::automotive::evs::V1_0::DisplayState::NUM_STATES) {
        return "NUM_STATES";
    }
    std::string os;
    os += toHexString(static_cast<uint32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::automotive::evs::V1_0::DisplayState o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::automotive::evs::V1_0::EvsResult>(uint32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::automotive::evs::V1_0::EvsResult> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::automotive::evs::V1_0::EvsResult::OK) == static_cast<uint32_t>(::android::hardware::automotive::evs::V1_0::EvsResult::OK)) {
        os += (first ? "" : " | ");
        os += "OK";
        first = false;
        flipped |= ::android::hardware::automotive::evs::V1_0::EvsResult::OK;
    }
    if ((o & ::android::hardware::automotive::evs::V1_0::EvsResult::INVALID_ARG) == static_cast<uint32_t>(::android::hardware::automotive::evs::V1_0::EvsResult::INVALID_ARG)) {
        os += (first ? "" : " | ");
        os += "INVALID_ARG";
        first = false;
        flipped |= ::android::hardware::automotive::evs::V1_0::EvsResult::INVALID_ARG;
    }
    if ((o & ::android::hardware::automotive::evs::V1_0::EvsResult::STREAM_ALREADY_RUNNING) == static_cast<uint32_t>(::android::hardware::automotive::evs::V1_0::EvsResult::STREAM_ALREADY_RUNNING)) {
        os += (first ? "" : " | ");
        os += "STREAM_ALREADY_RUNNING";
        first = false;
        flipped |= ::android::hardware::automotive::evs::V1_0::EvsResult::STREAM_ALREADY_RUNNING;
    }
    if ((o & ::android::hardware::automotive::evs::V1_0::EvsResult::BUFFER_NOT_AVAILABLE) == static_cast<uint32_t>(::android::hardware::automotive::evs::V1_0::EvsResult::BUFFER_NOT_AVAILABLE)) {
        os += (first ? "" : " | ");
        os += "BUFFER_NOT_AVAILABLE";
        first = false;
        flipped |= ::android::hardware::automotive::evs::V1_0::EvsResult::BUFFER_NOT_AVAILABLE;
    }
    if ((o & ::android::hardware::automotive::evs::V1_0::EvsResult::OWNERSHIP_LOST) == static_cast<uint32_t>(::android::hardware::automotive::evs::V1_0::EvsResult::OWNERSHIP_LOST)) {
        os += (first ? "" : " | ");
        os += "OWNERSHIP_LOST";
        first = false;
        flipped |= ::android::hardware::automotive::evs::V1_0::EvsResult::OWNERSHIP_LOST;
    }
    if ((o & ::android::hardware::automotive::evs::V1_0::EvsResult::UNDERLYING_SERVICE_ERROR) == static_cast<uint32_t>(::android::hardware::automotive::evs::V1_0::EvsResult::UNDERLYING_SERVICE_ERROR)) {
        os += (first ? "" : " | ");
        os += "UNDERLYING_SERVICE_ERROR";
        first = false;
        flipped |= ::android::hardware::automotive::evs::V1_0::EvsResult::UNDERLYING_SERVICE_ERROR;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::automotive::evs::V1_0::EvsResult o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::automotive::evs::V1_0::EvsResult::OK) {
        return "OK";
    }
    if (o == ::android::hardware::automotive::evs::V1_0::EvsResult::INVALID_ARG) {
        return "INVALID_ARG";
    }
    if (o == ::android::hardware::automotive::evs::V1_0::EvsResult::STREAM_ALREADY_RUNNING) {
        return "STREAM_ALREADY_RUNNING";
    }
    if (o == ::android::hardware::automotive::evs::V1_0::EvsResult::BUFFER_NOT_AVAILABLE) {
        return "BUFFER_NOT_AVAILABLE";
    }
    if (o == ::android::hardware::automotive::evs::V1_0::EvsResult::OWNERSHIP_LOST) {
        return "OWNERSHIP_LOST";
    }
    if (o == ::android::hardware::automotive::evs::V1_0::EvsResult::UNDERLYING_SERVICE_ERROR) {
        return "UNDERLYING_SERVICE_ERROR";
    }
    std::string os;
    os += toHexString(static_cast<uint32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::automotive::evs::V1_0::EvsResult o, ::std::ostream* os) {
    *os << toString(o);
}


}  // namespace V1_0
}  // namespace evs
}  // namespace automotive
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
template<> inline constexpr std::array<::android::hardware::automotive::evs::V1_0::DisplayState, 6> hidl_enum_values<::android::hardware::automotive::evs::V1_0::DisplayState> = {
    ::android::hardware::automotive::evs::V1_0::DisplayState::NOT_OPEN,
    ::android::hardware::automotive::evs::V1_0::DisplayState::NOT_VISIBLE,
    ::android::hardware::automotive::evs::V1_0::DisplayState::VISIBLE_ON_NEXT_FRAME,
    ::android::hardware::automotive::evs::V1_0::DisplayState::VISIBLE,
    ::android::hardware::automotive::evs::V1_0::DisplayState::DEAD,
    ::android::hardware::automotive::evs::V1_0::DisplayState::NUM_STATES,
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
template<> inline constexpr std::array<::android::hardware::automotive::evs::V1_0::EvsResult, 6> hidl_enum_values<::android::hardware::automotive::evs::V1_0::EvsResult> = {
    ::android::hardware::automotive::evs::V1_0::EvsResult::OK,
    ::android::hardware::automotive::evs::V1_0::EvsResult::INVALID_ARG,
    ::android::hardware::automotive::evs::V1_0::EvsResult::STREAM_ALREADY_RUNNING,
    ::android::hardware::automotive::evs::V1_0::EvsResult::BUFFER_NOT_AVAILABLE,
    ::android::hardware::automotive::evs::V1_0::EvsResult::OWNERSHIP_LOST,
    ::android::hardware::automotive::evs::V1_0::EvsResult::UNDERLYING_SERVICE_ERROR,
};
#pragma clang diagnostic pop
}  // namespace details
}  // namespace hardware
}  // namespace android


#endif  // HIDL_GENERATED_ANDROID_HARDWARE_AUTOMOTIVE_EVS_V1_0_TYPES_H
