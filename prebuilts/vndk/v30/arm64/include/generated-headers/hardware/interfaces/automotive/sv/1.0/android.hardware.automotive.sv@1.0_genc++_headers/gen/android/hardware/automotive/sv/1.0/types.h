#ifndef HIDL_GENERATED_ANDROID_HARDWARE_AUTOMOTIVE_SV_V1_0_TYPES_H
#define HIDL_GENERATED_ANDROID_HARDWARE_AUTOMOTIVE_SV_V1_0_TYPES_H

#include <android/hardware/graphics/common/1.2/types.h>

#include <hidl/HidlSupport.h>
#include <hidl/MQDescriptor.h>
#include <utils/NativeHandle.h>
#include <utils/misc.h>

namespace android {
namespace hardware {
namespace automotive {
namespace sv {
namespace V1_0 {

// Forward declaration for forward reference support:
struct Translation;
struct RotationQuat;
struct Point2dInt;
struct Point2dFloat;
struct Point3dFloat;
struct Pose;
struct View3d;
struct SvBuffer;
struct SvFramesDesc;
enum class SvResult : uint32_t;
enum class SvEvent : uint32_t;
struct Sv2dMappingInfo;
enum class SvQuality : uint32_t;
struct Sv2dConfig;
struct Sv3dConfig;
enum class OverlayPrimitive : uint32_t;
struct OverlayMemoryDesc;
struct OverlaysData;

/**
 * Structure for translation with x, y and z units.
 */
struct Translation final {
    float x __attribute__ ((aligned(4)));
    float y __attribute__ ((aligned(4)));
    float z __attribute__ ((aligned(4)));
};

static_assert(offsetof(::android::hardware::automotive::sv::V1_0::Translation, x) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::automotive::sv::V1_0::Translation, y) == 4, "wrong offset");
static_assert(offsetof(::android::hardware::automotive::sv::V1_0::Translation, z) == 8, "wrong offset");
static_assert(sizeof(::android::hardware::automotive::sv::V1_0::Translation) == 12, "wrong size");
static_assert(__alignof(::android::hardware::automotive::sv::V1_0::Translation) == 4, "wrong alignment");

/**
 * Structure for rotation expressed as quaternions.
 * Convention used: Unit quaternion with hamilton convention.
 */
struct RotationQuat final {
    float x __attribute__ ((aligned(4)));
    float y __attribute__ ((aligned(4)));
    float z __attribute__ ((aligned(4)));
    float w __attribute__ ((aligned(4)));
};

static_assert(offsetof(::android::hardware::automotive::sv::V1_0::RotationQuat, x) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::automotive::sv::V1_0::RotationQuat, y) == 4, "wrong offset");
static_assert(offsetof(::android::hardware::automotive::sv::V1_0::RotationQuat, z) == 8, "wrong offset");
static_assert(offsetof(::android::hardware::automotive::sv::V1_0::RotationQuat, w) == 12, "wrong offset");
static_assert(sizeof(::android::hardware::automotive::sv::V1_0::RotationQuat) == 16, "wrong size");
static_assert(__alignof(::android::hardware::automotive::sv::V1_0::RotationQuat) == 4, "wrong alignment");

/**
 * Structure representing a 2D point with integers. Units are pixels.
 */
struct Point2dInt final {
    uint32_t x __attribute__ ((aligned(4)));
    uint32_t y __attribute__ ((aligned(4)));
};

static_assert(offsetof(::android::hardware::automotive::sv::V1_0::Point2dInt, x) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::automotive::sv::V1_0::Point2dInt, y) == 4, "wrong offset");
static_assert(sizeof(::android::hardware::automotive::sv::V1_0::Point2dInt) == 8, "wrong size");
static_assert(__alignof(::android::hardware::automotive::sv::V1_0::Point2dInt) == 4, "wrong alignment");

/**
 * Structure representing a 2D point with floats.
 */
struct Point2dFloat final {
    /**
     * Boolean flag to indicate the (x, y) data is valid.
     */
    bool isValid __attribute__ ((aligned(1)));
    /**
     * (x, y) data is only valid if isValid is true. Units are pixels or milli-meters.
     */
    float x __attribute__ ((aligned(4)));
    float y __attribute__ ((aligned(4)));
};

static_assert(offsetof(::android::hardware::automotive::sv::V1_0::Point2dFloat, isValid) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::automotive::sv::V1_0::Point2dFloat, x) == 4, "wrong offset");
static_assert(offsetof(::android::hardware::automotive::sv::V1_0::Point2dFloat, y) == 8, "wrong offset");
static_assert(sizeof(::android::hardware::automotive::sv::V1_0::Point2dFloat) == 12, "wrong size");
static_assert(__alignof(::android::hardware::automotive::sv::V1_0::Point2dFloat) == 4, "wrong alignment");

/**
 * Structure representing a 3D point with floats.
 */
struct Point3dFloat final {
    /**
     * Boolean flag to indicate the (x, y, z) data is valid.
     */
    bool isValid __attribute__ ((aligned(1)));
    /**
     * (x, y, z) data is only valid if isValid is true. Units are milli-meters.
     */
    float x __attribute__ ((aligned(4)));
    float y __attribute__ ((aligned(4)));
    float z __attribute__ ((aligned(4)));
};

static_assert(offsetof(::android::hardware::automotive::sv::V1_0::Point3dFloat, isValid) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::automotive::sv::V1_0::Point3dFloat, x) == 4, "wrong offset");
static_assert(offsetof(::android::hardware::automotive::sv::V1_0::Point3dFloat, y) == 8, "wrong offset");
static_assert(offsetof(::android::hardware::automotive::sv::V1_0::Point3dFloat, z) == 12, "wrong offset");
static_assert(sizeof(::android::hardware::automotive::sv::V1_0::Point3dFloat) == 16, "wrong size");
static_assert(__alignof(::android::hardware::automotive::sv::V1_0::Point3dFloat) == 4, "wrong alignment");

/**
 * Structure defining the pose in 3D space.
 */
struct Pose final {
    /**
     * Rotation part of the pose, expressed as a unit quaternion.
     */
    ::android::hardware::automotive::sv::V1_0::RotationQuat rotation __attribute__ ((aligned(4)));
    /**
     * Translation part of the pose, in (x, y, z) format with milli-meter units.
     */
    ::android::hardware::automotive::sv::V1_0::Translation translation __attribute__ ((aligned(4)));
};

static_assert(offsetof(::android::hardware::automotive::sv::V1_0::Pose, rotation) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::automotive::sv::V1_0::Pose, translation) == 16, "wrong offset");
static_assert(sizeof(::android::hardware::automotive::sv::V1_0::Pose) == 28, "wrong size");
static_assert(__alignof(::android::hardware::automotive::sv::V1_0::Pose) == 4, "wrong alignment");

/**
 * Struct defining a virtual view in the 3d space around the car.
 */
struct View3d final {
    /**
     * Id to identify each custom view, this is passed along in each result SvBuffer.
     * Recommend client to have a unique id for each different view.
     */
    uint32_t viewId __attribute__ ((aligned(4)));
    /**
     * Pose of the view. Describes the orientation and location of a virtual view relative to the
     * android automotive coordinate system:
     * https://source.android.com/devices/sensors/sensor-types#auto_axes
     * The virtual view axes are defined as +Y as look-at direction, +X as right direction and
     * +Z as up direction.
     * The rotation and translation of the virtual view axes w.r.t the android automotive axes is
     * specified by the rotation and tranlation component of the pose respectively.
     * Example: A virtual view points to the right face of the car, located on right side of
     * the car at (4, 2, 0) and is upright w.r.t the ground :
     *         ______
     *  front |      |
     *        | car  |    ↑X
     *        |  ↑Y  |  Y←∘  view
     *  rear  |  ∘→X |  (4,2)
     *        |(0,0) |
     *        |______|
     *
     * Here the view axes are rotated by 90 counter-clockwise w.r.t android automotive axes.
     * For this example the rotation and translation will be:
     * Rotation = + 90 degrees around Z axis = (0.7071, 0, 0, 0.7071) as a unit quaternion.
     * Translation = (4, 2, 0) in meters = (2000, 4000, 0) in milli-meters.
     */
    ::android::hardware::automotive::sv::V1_0::Pose pose __attribute__ ((aligned(4)));
    /**
     * Horizontal field of view of the virtual view in degrees. Vertical fov is scaled accordingly
     * to maintain the aspect ratio of the output frame. Must be in range (20,
     */
    float horizontalFov __attribute__ ((aligned(4)));
};

static_assert(offsetof(::android::hardware::automotive::sv::V1_0::View3d, viewId) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::automotive::sv::V1_0::View3d, pose) == 4, "wrong offset");
static_assert(offsetof(::android::hardware::automotive::sv::V1_0::View3d, horizontalFov) == 32, "wrong offset");
static_assert(sizeof(::android::hardware::automotive::sv::V1_0::View3d) == 36, "wrong size");
static_assert(__alignof(::android::hardware::automotive::sv::V1_0::View3d) == 4, "wrong alignment");

/**
 * Memory Buffer that stores the output of a single view from 2d/3d surround view.
 */
struct SvBuffer final {
    /**
     * viewId identifying the view as passed by the client in setViews() call for
     * surround view 3d. Id value is 0 for 2d surround view frame.
     */
    uint32_t viewId __attribute__ ((aligned(4)));
    /**
     * Hardware buffer containing the surround view 2d/3d result.
     */
    ::android::hardware::graphics::common::V1_2::HardwareBuffer hardwareBuffer __attribute__ ((aligned(8)));
};

static_assert(offsetof(::android::hardware::automotive::sv::V1_0::SvBuffer, viewId) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::automotive::sv::V1_0::SvBuffer, hardwareBuffer) == 8, "wrong offset");
static_assert(sizeof(::android::hardware::automotive::sv::V1_0::SvBuffer) == 64, "wrong size");
static_assert(__alignof(::android::hardware::automotive::sv::V1_0::SvBuffer) == 8, "wrong alignment");

/**
 * Structure describing a set of frames to be returned as output from 2d/3d surround view.
 */
struct SvFramesDesc final {
    /**
     * Elapsed real-time nanoseconds of earliest camera frame from the set of camera
     * frames used to generate the view.
     */
    uint64_t timestampNs __attribute__ ((aligned(8)));
    /**
     * Incremental counter for client to keep track of frames.
     */
    uint32_t sequenceId __attribute__ ((aligned(4)));
    /**
     * Frames generated with different views.
     * 2d surround view has only a single svBuffer with Id 0.
     */
    ::android::hardware::hidl_vec<::android::hardware::automotive::sv::V1_0::SvBuffer> svBuffers __attribute__ ((aligned(8)));
};

static_assert(offsetof(::android::hardware::automotive::sv::V1_0::SvFramesDesc, timestampNs) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::automotive::sv::V1_0::SvFramesDesc, sequenceId) == 8, "wrong offset");
static_assert(offsetof(::android::hardware::automotive::sv::V1_0::SvFramesDesc, svBuffers) == 16, "wrong offset");
static_assert(sizeof(::android::hardware::automotive::sv::V1_0::SvFramesDesc) == 32, "wrong size");
static_assert(__alignof(::android::hardware::automotive::sv::V1_0::SvFramesDesc) == 8, "wrong alignment");

/**
 * Enumerator for list of result returns by surround view .
 */
enum class SvResult : uint32_t {
    /**
     * Operation was successful.
     */
    OK = 0u,
    /**
     * Invalid argument to function was provided.
     */
    INVALID_ARG = 1u /* ::android::hardware::automotive::sv::V1_0::SvResult.OK implicitly + 1 */,
    /**
     * Error indicating the particular operation is not supported.
     */
    NOT_SUPPORTED = 2u /* ::android::hardware::automotive::sv::V1_0::SvResult.INVALID_ARG implicitly + 1 */,
    /**
     * Error indicating view not set before starting stream.
     */
    VIEW_NOT_SET = 3u /* ::android::hardware::automotive::sv::V1_0::SvResult.NOT_SUPPORTED implicitly + 1 */,
    /**
     * Error indicating system does not currently have enough resources to
     * allocate for a new requested session.
     * Clients may retry request for session if resources become available.
     */
    NO_RESOURCE = 4u /* ::android::hardware::automotive::sv::V1_0::SvResult.VIEW_NOT_SET implicitly + 1 */,
    /**
     * Internal error in surround view service.
     */
    INTERNAL_ERROR = 5u /* ::android::hardware::automotive::sv::V1_0::SvResult.NO_RESOURCE implicitly + 1 */,
};

/**
 * Enumerator listing events for surround view.
 */
enum class SvEvent : uint32_t {
    STREAM_STARTED = 1u,
    STREAM_STOPPED = 2u /* ::android::hardware::automotive::sv::V1_0::SvEvent.STREAM_STARTED implicitly + 1 */,
    /**
     * Event sent after service switches to an updated config, all frames
     * streamed after this event are of the updated config.
     */
    CONFIG_UPDATED = 3u /* ::android::hardware::automotive::sv::V1_0::SvEvent.STREAM_STOPPED implicitly + 1 */,
    /**
     * Each frame dropped will be notified with this event.
     */
    FRAME_DROPPED = 4u /* ::android::hardware::automotive::sv::V1_0::SvEvent.CONFIG_UPDATED implicitly + 1 */,
    /**
     * Timeout event occurs if any individual camera stream has a timeout.
     * Frames will not be delivered and clients must stop the stream.
     */
    TIMEOUT = 5u /* ::android::hardware::automotive::sv::V1_0::SvEvent.FRAME_DROPPED implicitly + 1 */,
};

/**
 * Structure defining the mapping information for 2d surround view.
 *
 * Mapping information provides the area on ground (width and height) and
 * position w.r.t the car that the surround view 2d covers. This can be used for
 * mapping (linear transformation) with other sensors whose data is available in
 * the car coordinate system (eg. Ultrasonics).
 * Axes and origin are as per the android automotive axes:
 * https://source.android.com/devices/sensors/sensor-types#auto_axes
 */
struct Sv2dMappingInfo final {
    /**
     * Width in milli-meters of the 2d surround view along the ground plane.
     */
    float width __attribute__ ((aligned(4)));
    /**
     * Height in milli-meters of the 2d surround view along the ground plane.
     */
    float height __attribute__ ((aligned(4)));
    /**
     * Coordinates (x, y) of the center of the view in android automotive coordinate system on the
     * ground plane. Units are milli-meters.
     */
    ::android::hardware::automotive::sv::V1_0::Point2dFloat center __attribute__ ((aligned(4)));
};

static_assert(offsetof(::android::hardware::automotive::sv::V1_0::Sv2dMappingInfo, width) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::automotive::sv::V1_0::Sv2dMappingInfo, height) == 4, "wrong offset");
static_assert(offsetof(::android::hardware::automotive::sv::V1_0::Sv2dMappingInfo, center) == 8, "wrong offset");
static_assert(sizeof(::android::hardware::automotive::sv::V1_0::Sv2dMappingInfo) == 20, "wrong size");
static_assert(__alignof(::android::hardware::automotive::sv::V1_0::Sv2dMappingInfo) == 4, "wrong alignment");

/**
 * Enumerator for quality presets for 2d/3d surround view.
 * Details of each preset are specified in the respective 2d/3d config structures.
 */
enum class SvQuality : uint32_t {
    HIGH = 0u,
    LOW = 1u /* ::android::hardware::automotive::sv::V1_0::SvQuality.HIGH implicitly + 1 */,
};

/**
 * Structure for surround view 2d configuration.
 */
struct Sv2dConfig final {
    /**
     * Desired output width in pixels. Must be in range (0, 4096].
     * Height is computed keeping the aspect ratio of the mapping info,
     * Example: If width = 1080 px and mapping_width = 5000 mm, mapping_height = 10000 mm.
     *          then, height = width * (mapping_height / mapping_width) = 2160 px.
     * Height is set to the floor value in case of (mapping_height / mapping_width) is not integer.
     * Mapping width, height is fixed for a car and is based on camera parameters and their ground
     * coverage.
     */
    uint32_t width __attribute__ ((aligned(4)));
    /**
     * Blending quality preset to use.
     * HIGH: High quality blending (eg. multiband blending) that consumes more resources.
     * LOW: Low quality blending (eg. alpha blending) that consumes less resources.
     */
    ::android::hardware::automotive::sv::V1_0::SvQuality blending __attribute__ ((aligned(4)));
};

static_assert(offsetof(::android::hardware::automotive::sv::V1_0::Sv2dConfig, width) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::automotive::sv::V1_0::Sv2dConfig, blending) == 4, "wrong offset");
static_assert(sizeof(::android::hardware::automotive::sv::V1_0::Sv2dConfig) == 8, "wrong size");
static_assert(__alignof(::android::hardware::automotive::sv::V1_0::Sv2dConfig) == 4, "wrong alignment");

/**
 * Structure for surround view 3d configuration.
 */
struct Sv3dConfig final {
    /**
     * Desired output width in pixels. Must be in range (0, 4096].
     */
    uint32_t width __attribute__ ((aligned(4)));
    /**
     * Desired output height in pixels. Must be in range (0, 4096].
     */
    uint32_t height __attribute__ ((aligned(4)));
    /**
     * Car model rendering details level.
     * HIGH: Rendering includes shadows and reflections. Default option.
     * LOW: Rendering with no shadows and reflections.
     */
    ::android::hardware::automotive::sv::V1_0::SvQuality carDetails __attribute__ ((aligned(4)));
};

static_assert(offsetof(::android::hardware::automotive::sv::V1_0::Sv3dConfig, width) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::automotive::sv::V1_0::Sv3dConfig, height) == 4, "wrong offset");
static_assert(offsetof(::android::hardware::automotive::sv::V1_0::Sv3dConfig, carDetails) == 8, "wrong offset");
static_assert(sizeof(::android::hardware::automotive::sv::V1_0::Sv3dConfig) == 12, "wrong size");
static_assert(__alignof(::android::hardware::automotive::sv::V1_0::Sv3dConfig) == 4, "wrong alignment");

/**
 * Enumerator for a list of overlay primitives.
 *
 * Given a list of vertices for an overlay, a primitive type defines which vertices are used to form
 * the surfaces of the overlay object.
 */
enum class OverlayPrimitive : uint32_t {
    /**
     * Consecutive vertices picked in order 3 at a time form a triangle.
     * Eg: In a list of vertices (V1, V2, V3, V4, V5, V6)
     * (V1, V2, V3) form a triangle and (V4, V5, V6) form a triangle.
     */
    TRIANGLES = 0u,
    /**
     * Every 3 consecutive vertices form a triangle.
     * Example in a list of vertices V1, V2, V3, V4, V5, V6
     * (V1, V2, V3), (V2, V3, V4), (V3, V4, V5) and (V4, V5, V6) form triangles.
     */
    TRIANGLES_STRIP = 1u /* ::android::hardware::automotive::sv::V1_0::OverlayPrimitive.TRIANGLES implicitly + 1 */,
};

/**
 * Structure identifying an overlay and describing the size and arrangement of its data in
 * shared memory.
 */
struct OverlayMemoryDesc final {
    /**
     * Identifier of the overlay.
     */
    uint16_t id __attribute__ ((aligned(2)));
    /**
     * Number of vertices in the overlay.
     */
    uint32_t verticesCount __attribute__ ((aligned(4)));
    /**
     * Primitive for the overlay.
     */
    ::android::hardware::automotive::sv::V1_0::OverlayPrimitive overlayPrimitive __attribute__ ((aligned(4)));
};

static_assert(offsetof(::android::hardware::automotive::sv::V1_0::OverlayMemoryDesc, id) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::automotive::sv::V1_0::OverlayMemoryDesc, verticesCount) == 4, "wrong offset");
static_assert(offsetof(::android::hardware::automotive::sv::V1_0::OverlayMemoryDesc, overlayPrimitive) == 8, "wrong offset");
static_assert(sizeof(::android::hardware::automotive::sv::V1_0::OverlayMemoryDesc) == 12, "wrong size");
static_assert(__alignof(::android::hardware::automotive::sv::V1_0::OverlayMemoryDesc) == 4, "wrong alignment");

/**
 *  Structure containing the overlays data in shared memory.
 */
struct OverlaysData final {
    /**
     * List of overlay memory descriptors, describing the data in the shared memory
     */
    ::android::hardware::hidl_vec<::android::hardware::automotive::sv::V1_0::OverlayMemoryDesc> overlaysMemoryDesc __attribute__ ((aligned(8)));
    /**
     * Shared memory object containing a list of vertices for each overlay as described by
     * overlaysMemoryDesc.
     *
     * Each vertex comprises of:
     * | PositionX | PositionY | PositionZ |   RGBA       |
     * |   float   |   float   |   float   |  4 * uint8_t |
     *
     * Each vertex is of 3 floats and 4 bytes = 16 bytes.
     *
     * Layout of vertices in shared memory is in order:
     *
     * Bytes: | 0-1 | 2-18 | 19-34 | 35-50 | 51-66 | 67-68 | 69-84 | 85-100 | 101-116  |...
     * Data:  | id1 |  V1  |  V2   |   V3  |   V4  |  id2  |   V1  |   V2   |  V3      |...
     *        |           overlay1                 |          overlay 2                |
     *
     * The order of overlays must match the order as specified in the overlaysMemoryDesc.
     * The number of vertices each overlay has must match the verticesCount in overlaysMemoryDesc.
     * The id must match the id specificed in the OverlayMemoryDesc. This is used for verification.
     * For each overlay the number of vertices must be 3 or greater.
     * For TRIANGLES primitive the number of vertices must be a multiple of 3.
     * The overlay vertices are grouped as per the overlayPrimitive specified in overlaysMemoryDesc,
     * eg: If primitive is TRIANGLES, (V1, V2, V3) and (V4, V5, V6) form a triangle.
     */
    ::android::hardware::hidl_memory overlaysMemory __attribute__ ((aligned(8)));
};

static_assert(offsetof(::android::hardware::automotive::sv::V1_0::OverlaysData, overlaysMemoryDesc) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::automotive::sv::V1_0::OverlaysData, overlaysMemory) == 16, "wrong offset");
static_assert(sizeof(::android::hardware::automotive::sv::V1_0::OverlaysData) == 56, "wrong size");
static_assert(__alignof(::android::hardware::automotive::sv::V1_0::OverlaysData) == 8, "wrong alignment");

//
// type declarations for package
//

static inline std::string toString(const ::android::hardware::automotive::sv::V1_0::Translation& o);
static inline void PrintTo(const ::android::hardware::automotive::sv::V1_0::Translation& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::automotive::sv::V1_0::Translation& lhs, const ::android::hardware::automotive::sv::V1_0::Translation& rhs);
static inline bool operator!=(const ::android::hardware::automotive::sv::V1_0::Translation& lhs, const ::android::hardware::automotive::sv::V1_0::Translation& rhs);

static inline std::string toString(const ::android::hardware::automotive::sv::V1_0::RotationQuat& o);
static inline void PrintTo(const ::android::hardware::automotive::sv::V1_0::RotationQuat& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::automotive::sv::V1_0::RotationQuat& lhs, const ::android::hardware::automotive::sv::V1_0::RotationQuat& rhs);
static inline bool operator!=(const ::android::hardware::automotive::sv::V1_0::RotationQuat& lhs, const ::android::hardware::automotive::sv::V1_0::RotationQuat& rhs);

static inline std::string toString(const ::android::hardware::automotive::sv::V1_0::Point2dInt& o);
static inline void PrintTo(const ::android::hardware::automotive::sv::V1_0::Point2dInt& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::automotive::sv::V1_0::Point2dInt& lhs, const ::android::hardware::automotive::sv::V1_0::Point2dInt& rhs);
static inline bool operator!=(const ::android::hardware::automotive::sv::V1_0::Point2dInt& lhs, const ::android::hardware::automotive::sv::V1_0::Point2dInt& rhs);

static inline std::string toString(const ::android::hardware::automotive::sv::V1_0::Point2dFloat& o);
static inline void PrintTo(const ::android::hardware::automotive::sv::V1_0::Point2dFloat& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::automotive::sv::V1_0::Point2dFloat& lhs, const ::android::hardware::automotive::sv::V1_0::Point2dFloat& rhs);
static inline bool operator!=(const ::android::hardware::automotive::sv::V1_0::Point2dFloat& lhs, const ::android::hardware::automotive::sv::V1_0::Point2dFloat& rhs);

static inline std::string toString(const ::android::hardware::automotive::sv::V1_0::Point3dFloat& o);
static inline void PrintTo(const ::android::hardware::automotive::sv::V1_0::Point3dFloat& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::automotive::sv::V1_0::Point3dFloat& lhs, const ::android::hardware::automotive::sv::V1_0::Point3dFloat& rhs);
static inline bool operator!=(const ::android::hardware::automotive::sv::V1_0::Point3dFloat& lhs, const ::android::hardware::automotive::sv::V1_0::Point3dFloat& rhs);

static inline std::string toString(const ::android::hardware::automotive::sv::V1_0::Pose& o);
static inline void PrintTo(const ::android::hardware::automotive::sv::V1_0::Pose& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::automotive::sv::V1_0::Pose& lhs, const ::android::hardware::automotive::sv::V1_0::Pose& rhs);
static inline bool operator!=(const ::android::hardware::automotive::sv::V1_0::Pose& lhs, const ::android::hardware::automotive::sv::V1_0::Pose& rhs);

static inline std::string toString(const ::android::hardware::automotive::sv::V1_0::View3d& o);
static inline void PrintTo(const ::android::hardware::automotive::sv::V1_0::View3d& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::automotive::sv::V1_0::View3d& lhs, const ::android::hardware::automotive::sv::V1_0::View3d& rhs);
static inline bool operator!=(const ::android::hardware::automotive::sv::V1_0::View3d& lhs, const ::android::hardware::automotive::sv::V1_0::View3d& rhs);

static inline std::string toString(const ::android::hardware::automotive::sv::V1_0::SvBuffer& o);
static inline void PrintTo(const ::android::hardware::automotive::sv::V1_0::SvBuffer& o, ::std::ostream*);
// operator== and operator!= are not generated for SvBuffer

static inline std::string toString(const ::android::hardware::automotive::sv::V1_0::SvFramesDesc& o);
static inline void PrintTo(const ::android::hardware::automotive::sv::V1_0::SvFramesDesc& o, ::std::ostream*);
// operator== and operator!= are not generated for SvFramesDesc

template<typename>
static inline std::string toString(uint32_t o);
static inline std::string toString(::android::hardware::automotive::sv::V1_0::SvResult o);
static inline void PrintTo(::android::hardware::automotive::sv::V1_0::SvResult o, ::std::ostream* os);
constexpr uint32_t operator|(const ::android::hardware::automotive::sv::V1_0::SvResult lhs, const ::android::hardware::automotive::sv::V1_0::SvResult rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const uint32_t lhs, const ::android::hardware::automotive::sv::V1_0::SvResult rhs) {
    return static_cast<uint32_t>(lhs | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const ::android::hardware::automotive::sv::V1_0::SvResult lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | rhs);
}
constexpr uint32_t operator&(const ::android::hardware::automotive::sv::V1_0::SvResult lhs, const ::android::hardware::automotive::sv::V1_0::SvResult rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const uint32_t lhs, const ::android::hardware::automotive::sv::V1_0::SvResult rhs) {
    return static_cast<uint32_t>(lhs & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const ::android::hardware::automotive::sv::V1_0::SvResult lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & rhs);
}
constexpr uint32_t &operator|=(uint32_t& v, const ::android::hardware::automotive::sv::V1_0::SvResult e) {
    v |= static_cast<uint32_t>(e);
    return v;
}
constexpr uint32_t &operator&=(uint32_t& v, const ::android::hardware::automotive::sv::V1_0::SvResult e) {
    v &= static_cast<uint32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(uint32_t o);
static inline std::string toString(::android::hardware::automotive::sv::V1_0::SvEvent o);
static inline void PrintTo(::android::hardware::automotive::sv::V1_0::SvEvent o, ::std::ostream* os);
constexpr uint32_t operator|(const ::android::hardware::automotive::sv::V1_0::SvEvent lhs, const ::android::hardware::automotive::sv::V1_0::SvEvent rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const uint32_t lhs, const ::android::hardware::automotive::sv::V1_0::SvEvent rhs) {
    return static_cast<uint32_t>(lhs | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const ::android::hardware::automotive::sv::V1_0::SvEvent lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | rhs);
}
constexpr uint32_t operator&(const ::android::hardware::automotive::sv::V1_0::SvEvent lhs, const ::android::hardware::automotive::sv::V1_0::SvEvent rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const uint32_t lhs, const ::android::hardware::automotive::sv::V1_0::SvEvent rhs) {
    return static_cast<uint32_t>(lhs & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const ::android::hardware::automotive::sv::V1_0::SvEvent lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & rhs);
}
constexpr uint32_t &operator|=(uint32_t& v, const ::android::hardware::automotive::sv::V1_0::SvEvent e) {
    v |= static_cast<uint32_t>(e);
    return v;
}
constexpr uint32_t &operator&=(uint32_t& v, const ::android::hardware::automotive::sv::V1_0::SvEvent e) {
    v &= static_cast<uint32_t>(e);
    return v;
}

static inline std::string toString(const ::android::hardware::automotive::sv::V1_0::Sv2dMappingInfo& o);
static inline void PrintTo(const ::android::hardware::automotive::sv::V1_0::Sv2dMappingInfo& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::automotive::sv::V1_0::Sv2dMappingInfo& lhs, const ::android::hardware::automotive::sv::V1_0::Sv2dMappingInfo& rhs);
static inline bool operator!=(const ::android::hardware::automotive::sv::V1_0::Sv2dMappingInfo& lhs, const ::android::hardware::automotive::sv::V1_0::Sv2dMappingInfo& rhs);

template<typename>
static inline std::string toString(uint32_t o);
static inline std::string toString(::android::hardware::automotive::sv::V1_0::SvQuality o);
static inline void PrintTo(::android::hardware::automotive::sv::V1_0::SvQuality o, ::std::ostream* os);
constexpr uint32_t operator|(const ::android::hardware::automotive::sv::V1_0::SvQuality lhs, const ::android::hardware::automotive::sv::V1_0::SvQuality rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const uint32_t lhs, const ::android::hardware::automotive::sv::V1_0::SvQuality rhs) {
    return static_cast<uint32_t>(lhs | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const ::android::hardware::automotive::sv::V1_0::SvQuality lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | rhs);
}
constexpr uint32_t operator&(const ::android::hardware::automotive::sv::V1_0::SvQuality lhs, const ::android::hardware::automotive::sv::V1_0::SvQuality rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const uint32_t lhs, const ::android::hardware::automotive::sv::V1_0::SvQuality rhs) {
    return static_cast<uint32_t>(lhs & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const ::android::hardware::automotive::sv::V1_0::SvQuality lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & rhs);
}
constexpr uint32_t &operator|=(uint32_t& v, const ::android::hardware::automotive::sv::V1_0::SvQuality e) {
    v |= static_cast<uint32_t>(e);
    return v;
}
constexpr uint32_t &operator&=(uint32_t& v, const ::android::hardware::automotive::sv::V1_0::SvQuality e) {
    v &= static_cast<uint32_t>(e);
    return v;
}

static inline std::string toString(const ::android::hardware::automotive::sv::V1_0::Sv2dConfig& o);
static inline void PrintTo(const ::android::hardware::automotive::sv::V1_0::Sv2dConfig& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::automotive::sv::V1_0::Sv2dConfig& lhs, const ::android::hardware::automotive::sv::V1_0::Sv2dConfig& rhs);
static inline bool operator!=(const ::android::hardware::automotive::sv::V1_0::Sv2dConfig& lhs, const ::android::hardware::automotive::sv::V1_0::Sv2dConfig& rhs);

static inline std::string toString(const ::android::hardware::automotive::sv::V1_0::Sv3dConfig& o);
static inline void PrintTo(const ::android::hardware::automotive::sv::V1_0::Sv3dConfig& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::automotive::sv::V1_0::Sv3dConfig& lhs, const ::android::hardware::automotive::sv::V1_0::Sv3dConfig& rhs);
static inline bool operator!=(const ::android::hardware::automotive::sv::V1_0::Sv3dConfig& lhs, const ::android::hardware::automotive::sv::V1_0::Sv3dConfig& rhs);

template<typename>
static inline std::string toString(uint32_t o);
static inline std::string toString(::android::hardware::automotive::sv::V1_0::OverlayPrimitive o);
static inline void PrintTo(::android::hardware::automotive::sv::V1_0::OverlayPrimitive o, ::std::ostream* os);
constexpr uint32_t operator|(const ::android::hardware::automotive::sv::V1_0::OverlayPrimitive lhs, const ::android::hardware::automotive::sv::V1_0::OverlayPrimitive rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const uint32_t lhs, const ::android::hardware::automotive::sv::V1_0::OverlayPrimitive rhs) {
    return static_cast<uint32_t>(lhs | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const ::android::hardware::automotive::sv::V1_0::OverlayPrimitive lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | rhs);
}
constexpr uint32_t operator&(const ::android::hardware::automotive::sv::V1_0::OverlayPrimitive lhs, const ::android::hardware::automotive::sv::V1_0::OverlayPrimitive rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const uint32_t lhs, const ::android::hardware::automotive::sv::V1_0::OverlayPrimitive rhs) {
    return static_cast<uint32_t>(lhs & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const ::android::hardware::automotive::sv::V1_0::OverlayPrimitive lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & rhs);
}
constexpr uint32_t &operator|=(uint32_t& v, const ::android::hardware::automotive::sv::V1_0::OverlayPrimitive e) {
    v |= static_cast<uint32_t>(e);
    return v;
}
constexpr uint32_t &operator&=(uint32_t& v, const ::android::hardware::automotive::sv::V1_0::OverlayPrimitive e) {
    v &= static_cast<uint32_t>(e);
    return v;
}

static inline std::string toString(const ::android::hardware::automotive::sv::V1_0::OverlayMemoryDesc& o);
static inline void PrintTo(const ::android::hardware::automotive::sv::V1_0::OverlayMemoryDesc& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::automotive::sv::V1_0::OverlayMemoryDesc& lhs, const ::android::hardware::automotive::sv::V1_0::OverlayMemoryDesc& rhs);
static inline bool operator!=(const ::android::hardware::automotive::sv::V1_0::OverlayMemoryDesc& lhs, const ::android::hardware::automotive::sv::V1_0::OverlayMemoryDesc& rhs);

static inline std::string toString(const ::android::hardware::automotive::sv::V1_0::OverlaysData& o);
static inline void PrintTo(const ::android::hardware::automotive::sv::V1_0::OverlaysData& o, ::std::ostream*);
// operator== and operator!= are not generated for OverlaysData

//
// type header definitions for package
//

static inline std::string toString(const ::android::hardware::automotive::sv::V1_0::Translation& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".x = ";
    os += ::android::hardware::toString(o.x);
    os += ", .y = ";
    os += ::android::hardware::toString(o.y);
    os += ", .z = ";
    os += ::android::hardware::toString(o.z);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::automotive::sv::V1_0::Translation& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::automotive::sv::V1_0::Translation& lhs, const ::android::hardware::automotive::sv::V1_0::Translation& rhs) {
    if (lhs.x != rhs.x) {
        return false;
    }
    if (lhs.y != rhs.y) {
        return false;
    }
    if (lhs.z != rhs.z) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::automotive::sv::V1_0::Translation& lhs, const ::android::hardware::automotive::sv::V1_0::Translation& rhs){
    return !(lhs == rhs);
}

static inline std::string toString(const ::android::hardware::automotive::sv::V1_0::RotationQuat& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".x = ";
    os += ::android::hardware::toString(o.x);
    os += ", .y = ";
    os += ::android::hardware::toString(o.y);
    os += ", .z = ";
    os += ::android::hardware::toString(o.z);
    os += ", .w = ";
    os += ::android::hardware::toString(o.w);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::automotive::sv::V1_0::RotationQuat& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::automotive::sv::V1_0::RotationQuat& lhs, const ::android::hardware::automotive::sv::V1_0::RotationQuat& rhs) {
    if (lhs.x != rhs.x) {
        return false;
    }
    if (lhs.y != rhs.y) {
        return false;
    }
    if (lhs.z != rhs.z) {
        return false;
    }
    if (lhs.w != rhs.w) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::automotive::sv::V1_0::RotationQuat& lhs, const ::android::hardware::automotive::sv::V1_0::RotationQuat& rhs){
    return !(lhs == rhs);
}

static inline std::string toString(const ::android::hardware::automotive::sv::V1_0::Point2dInt& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".x = ";
    os += ::android::hardware::toString(o.x);
    os += ", .y = ";
    os += ::android::hardware::toString(o.y);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::automotive::sv::V1_0::Point2dInt& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::automotive::sv::V1_0::Point2dInt& lhs, const ::android::hardware::automotive::sv::V1_0::Point2dInt& rhs) {
    if (lhs.x != rhs.x) {
        return false;
    }
    if (lhs.y != rhs.y) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::automotive::sv::V1_0::Point2dInt& lhs, const ::android::hardware::automotive::sv::V1_0::Point2dInt& rhs){
    return !(lhs == rhs);
}

static inline std::string toString(const ::android::hardware::automotive::sv::V1_0::Point2dFloat& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".isValid = ";
    os += ::android::hardware::toString(o.isValid);
    os += ", .x = ";
    os += ::android::hardware::toString(o.x);
    os += ", .y = ";
    os += ::android::hardware::toString(o.y);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::automotive::sv::V1_0::Point2dFloat& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::automotive::sv::V1_0::Point2dFloat& lhs, const ::android::hardware::automotive::sv::V1_0::Point2dFloat& rhs) {
    if (lhs.isValid != rhs.isValid) {
        return false;
    }
    if (lhs.x != rhs.x) {
        return false;
    }
    if (lhs.y != rhs.y) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::automotive::sv::V1_0::Point2dFloat& lhs, const ::android::hardware::automotive::sv::V1_0::Point2dFloat& rhs){
    return !(lhs == rhs);
}

static inline std::string toString(const ::android::hardware::automotive::sv::V1_0::Point3dFloat& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".isValid = ";
    os += ::android::hardware::toString(o.isValid);
    os += ", .x = ";
    os += ::android::hardware::toString(o.x);
    os += ", .y = ";
    os += ::android::hardware::toString(o.y);
    os += ", .z = ";
    os += ::android::hardware::toString(o.z);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::automotive::sv::V1_0::Point3dFloat& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::automotive::sv::V1_0::Point3dFloat& lhs, const ::android::hardware::automotive::sv::V1_0::Point3dFloat& rhs) {
    if (lhs.isValid != rhs.isValid) {
        return false;
    }
    if (lhs.x != rhs.x) {
        return false;
    }
    if (lhs.y != rhs.y) {
        return false;
    }
    if (lhs.z != rhs.z) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::automotive::sv::V1_0::Point3dFloat& lhs, const ::android::hardware::automotive::sv::V1_0::Point3dFloat& rhs){
    return !(lhs == rhs);
}

static inline std::string toString(const ::android::hardware::automotive::sv::V1_0::Pose& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".rotation = ";
    os += ::android::hardware::automotive::sv::V1_0::toString(o.rotation);
    os += ", .translation = ";
    os += ::android::hardware::automotive::sv::V1_0::toString(o.translation);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::automotive::sv::V1_0::Pose& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::automotive::sv::V1_0::Pose& lhs, const ::android::hardware::automotive::sv::V1_0::Pose& rhs) {
    if (lhs.rotation != rhs.rotation) {
        return false;
    }
    if (lhs.translation != rhs.translation) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::automotive::sv::V1_0::Pose& lhs, const ::android::hardware::automotive::sv::V1_0::Pose& rhs){
    return !(lhs == rhs);
}

static inline std::string toString(const ::android::hardware::automotive::sv::V1_0::View3d& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".viewId = ";
    os += ::android::hardware::toString(o.viewId);
    os += ", .pose = ";
    os += ::android::hardware::automotive::sv::V1_0::toString(o.pose);
    os += ", .horizontalFov = ";
    os += ::android::hardware::toString(o.horizontalFov);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::automotive::sv::V1_0::View3d& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::automotive::sv::V1_0::View3d& lhs, const ::android::hardware::automotive::sv::V1_0::View3d& rhs) {
    if (lhs.viewId != rhs.viewId) {
        return false;
    }
    if (lhs.pose != rhs.pose) {
        return false;
    }
    if (lhs.horizontalFov != rhs.horizontalFov) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::automotive::sv::V1_0::View3d& lhs, const ::android::hardware::automotive::sv::V1_0::View3d& rhs){
    return !(lhs == rhs);
}

static inline std::string toString(const ::android::hardware::automotive::sv::V1_0::SvBuffer& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".viewId = ";
    os += ::android::hardware::toString(o.viewId);
    os += ", .hardwareBuffer = ";
    os += ::android::hardware::graphics::common::V1_2::toString(o.hardwareBuffer);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::automotive::sv::V1_0::SvBuffer& o, ::std::ostream* os) {
    *os << toString(o);
}

// operator== and operator!= are not generated for SvBuffer

static inline std::string toString(const ::android::hardware::automotive::sv::V1_0::SvFramesDesc& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".timestampNs = ";
    os += ::android::hardware::toString(o.timestampNs);
    os += ", .sequenceId = ";
    os += ::android::hardware::toString(o.sequenceId);
    os += ", .svBuffers = ";
    os += ::android::hardware::toString(o.svBuffers);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::automotive::sv::V1_0::SvFramesDesc& o, ::std::ostream* os) {
    *os << toString(o);
}

// operator== and operator!= are not generated for SvFramesDesc

template<>
inline std::string toString<::android::hardware::automotive::sv::V1_0::SvResult>(uint32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::automotive::sv::V1_0::SvResult> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::automotive::sv::V1_0::SvResult::OK) == static_cast<uint32_t>(::android::hardware::automotive::sv::V1_0::SvResult::OK)) {
        os += (first ? "" : " | ");
        os += "OK";
        first = false;
        flipped |= ::android::hardware::automotive::sv::V1_0::SvResult::OK;
    }
    if ((o & ::android::hardware::automotive::sv::V1_0::SvResult::INVALID_ARG) == static_cast<uint32_t>(::android::hardware::automotive::sv::V1_0::SvResult::INVALID_ARG)) {
        os += (first ? "" : " | ");
        os += "INVALID_ARG";
        first = false;
        flipped |= ::android::hardware::automotive::sv::V1_0::SvResult::INVALID_ARG;
    }
    if ((o & ::android::hardware::automotive::sv::V1_0::SvResult::NOT_SUPPORTED) == static_cast<uint32_t>(::android::hardware::automotive::sv::V1_0::SvResult::NOT_SUPPORTED)) {
        os += (first ? "" : " | ");
        os += "NOT_SUPPORTED";
        first = false;
        flipped |= ::android::hardware::automotive::sv::V1_0::SvResult::NOT_SUPPORTED;
    }
    if ((o & ::android::hardware::automotive::sv::V1_0::SvResult::VIEW_NOT_SET) == static_cast<uint32_t>(::android::hardware::automotive::sv::V1_0::SvResult::VIEW_NOT_SET)) {
        os += (first ? "" : " | ");
        os += "VIEW_NOT_SET";
        first = false;
        flipped |= ::android::hardware::automotive::sv::V1_0::SvResult::VIEW_NOT_SET;
    }
    if ((o & ::android::hardware::automotive::sv::V1_0::SvResult::NO_RESOURCE) == static_cast<uint32_t>(::android::hardware::automotive::sv::V1_0::SvResult::NO_RESOURCE)) {
        os += (first ? "" : " | ");
        os += "NO_RESOURCE";
        first = false;
        flipped |= ::android::hardware::automotive::sv::V1_0::SvResult::NO_RESOURCE;
    }
    if ((o & ::android::hardware::automotive::sv::V1_0::SvResult::INTERNAL_ERROR) == static_cast<uint32_t>(::android::hardware::automotive::sv::V1_0::SvResult::INTERNAL_ERROR)) {
        os += (first ? "" : " | ");
        os += "INTERNAL_ERROR";
        first = false;
        flipped |= ::android::hardware::automotive::sv::V1_0::SvResult::INTERNAL_ERROR;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::automotive::sv::V1_0::SvResult o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::automotive::sv::V1_0::SvResult::OK) {
        return "OK";
    }
    if (o == ::android::hardware::automotive::sv::V1_0::SvResult::INVALID_ARG) {
        return "INVALID_ARG";
    }
    if (o == ::android::hardware::automotive::sv::V1_0::SvResult::NOT_SUPPORTED) {
        return "NOT_SUPPORTED";
    }
    if (o == ::android::hardware::automotive::sv::V1_0::SvResult::VIEW_NOT_SET) {
        return "VIEW_NOT_SET";
    }
    if (o == ::android::hardware::automotive::sv::V1_0::SvResult::NO_RESOURCE) {
        return "NO_RESOURCE";
    }
    if (o == ::android::hardware::automotive::sv::V1_0::SvResult::INTERNAL_ERROR) {
        return "INTERNAL_ERROR";
    }
    std::string os;
    os += toHexString(static_cast<uint32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::automotive::sv::V1_0::SvResult o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::automotive::sv::V1_0::SvEvent>(uint32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::automotive::sv::V1_0::SvEvent> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::automotive::sv::V1_0::SvEvent::STREAM_STARTED) == static_cast<uint32_t>(::android::hardware::automotive::sv::V1_0::SvEvent::STREAM_STARTED)) {
        os += (first ? "" : " | ");
        os += "STREAM_STARTED";
        first = false;
        flipped |= ::android::hardware::automotive::sv::V1_0::SvEvent::STREAM_STARTED;
    }
    if ((o & ::android::hardware::automotive::sv::V1_0::SvEvent::STREAM_STOPPED) == static_cast<uint32_t>(::android::hardware::automotive::sv::V1_0::SvEvent::STREAM_STOPPED)) {
        os += (first ? "" : " | ");
        os += "STREAM_STOPPED";
        first = false;
        flipped |= ::android::hardware::automotive::sv::V1_0::SvEvent::STREAM_STOPPED;
    }
    if ((o & ::android::hardware::automotive::sv::V1_0::SvEvent::CONFIG_UPDATED) == static_cast<uint32_t>(::android::hardware::automotive::sv::V1_0::SvEvent::CONFIG_UPDATED)) {
        os += (first ? "" : " | ");
        os += "CONFIG_UPDATED";
        first = false;
        flipped |= ::android::hardware::automotive::sv::V1_0::SvEvent::CONFIG_UPDATED;
    }
    if ((o & ::android::hardware::automotive::sv::V1_0::SvEvent::FRAME_DROPPED) == static_cast<uint32_t>(::android::hardware::automotive::sv::V1_0::SvEvent::FRAME_DROPPED)) {
        os += (first ? "" : " | ");
        os += "FRAME_DROPPED";
        first = false;
        flipped |= ::android::hardware::automotive::sv::V1_0::SvEvent::FRAME_DROPPED;
    }
    if ((o & ::android::hardware::automotive::sv::V1_0::SvEvent::TIMEOUT) == static_cast<uint32_t>(::android::hardware::automotive::sv::V1_0::SvEvent::TIMEOUT)) {
        os += (first ? "" : " | ");
        os += "TIMEOUT";
        first = false;
        flipped |= ::android::hardware::automotive::sv::V1_0::SvEvent::TIMEOUT;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::automotive::sv::V1_0::SvEvent o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::automotive::sv::V1_0::SvEvent::STREAM_STARTED) {
        return "STREAM_STARTED";
    }
    if (o == ::android::hardware::automotive::sv::V1_0::SvEvent::STREAM_STOPPED) {
        return "STREAM_STOPPED";
    }
    if (o == ::android::hardware::automotive::sv::V1_0::SvEvent::CONFIG_UPDATED) {
        return "CONFIG_UPDATED";
    }
    if (o == ::android::hardware::automotive::sv::V1_0::SvEvent::FRAME_DROPPED) {
        return "FRAME_DROPPED";
    }
    if (o == ::android::hardware::automotive::sv::V1_0::SvEvent::TIMEOUT) {
        return "TIMEOUT";
    }
    std::string os;
    os += toHexString(static_cast<uint32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::automotive::sv::V1_0::SvEvent o, ::std::ostream* os) {
    *os << toString(o);
}

static inline std::string toString(const ::android::hardware::automotive::sv::V1_0::Sv2dMappingInfo& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".width = ";
    os += ::android::hardware::toString(o.width);
    os += ", .height = ";
    os += ::android::hardware::toString(o.height);
    os += ", .center = ";
    os += ::android::hardware::automotive::sv::V1_0::toString(o.center);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::automotive::sv::V1_0::Sv2dMappingInfo& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::automotive::sv::V1_0::Sv2dMappingInfo& lhs, const ::android::hardware::automotive::sv::V1_0::Sv2dMappingInfo& rhs) {
    if (lhs.width != rhs.width) {
        return false;
    }
    if (lhs.height != rhs.height) {
        return false;
    }
    if (lhs.center != rhs.center) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::automotive::sv::V1_0::Sv2dMappingInfo& lhs, const ::android::hardware::automotive::sv::V1_0::Sv2dMappingInfo& rhs){
    return !(lhs == rhs);
}

template<>
inline std::string toString<::android::hardware::automotive::sv::V1_0::SvQuality>(uint32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::automotive::sv::V1_0::SvQuality> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::automotive::sv::V1_0::SvQuality::HIGH) == static_cast<uint32_t>(::android::hardware::automotive::sv::V1_0::SvQuality::HIGH)) {
        os += (first ? "" : " | ");
        os += "HIGH";
        first = false;
        flipped |= ::android::hardware::automotive::sv::V1_0::SvQuality::HIGH;
    }
    if ((o & ::android::hardware::automotive::sv::V1_0::SvQuality::LOW) == static_cast<uint32_t>(::android::hardware::automotive::sv::V1_0::SvQuality::LOW)) {
        os += (first ? "" : " | ");
        os += "LOW";
        first = false;
        flipped |= ::android::hardware::automotive::sv::V1_0::SvQuality::LOW;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::automotive::sv::V1_0::SvQuality o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::automotive::sv::V1_0::SvQuality::HIGH) {
        return "HIGH";
    }
    if (o == ::android::hardware::automotive::sv::V1_0::SvQuality::LOW) {
        return "LOW";
    }
    std::string os;
    os += toHexString(static_cast<uint32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::automotive::sv::V1_0::SvQuality o, ::std::ostream* os) {
    *os << toString(o);
}

static inline std::string toString(const ::android::hardware::automotive::sv::V1_0::Sv2dConfig& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".width = ";
    os += ::android::hardware::toString(o.width);
    os += ", .blending = ";
    os += ::android::hardware::automotive::sv::V1_0::toString(o.blending);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::automotive::sv::V1_0::Sv2dConfig& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::automotive::sv::V1_0::Sv2dConfig& lhs, const ::android::hardware::automotive::sv::V1_0::Sv2dConfig& rhs) {
    if (lhs.width != rhs.width) {
        return false;
    }
    if (lhs.blending != rhs.blending) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::automotive::sv::V1_0::Sv2dConfig& lhs, const ::android::hardware::automotive::sv::V1_0::Sv2dConfig& rhs){
    return !(lhs == rhs);
}

static inline std::string toString(const ::android::hardware::automotive::sv::V1_0::Sv3dConfig& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".width = ";
    os += ::android::hardware::toString(o.width);
    os += ", .height = ";
    os += ::android::hardware::toString(o.height);
    os += ", .carDetails = ";
    os += ::android::hardware::automotive::sv::V1_0::toString(o.carDetails);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::automotive::sv::V1_0::Sv3dConfig& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::automotive::sv::V1_0::Sv3dConfig& lhs, const ::android::hardware::automotive::sv::V1_0::Sv3dConfig& rhs) {
    if (lhs.width != rhs.width) {
        return false;
    }
    if (lhs.height != rhs.height) {
        return false;
    }
    if (lhs.carDetails != rhs.carDetails) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::automotive::sv::V1_0::Sv3dConfig& lhs, const ::android::hardware::automotive::sv::V1_0::Sv3dConfig& rhs){
    return !(lhs == rhs);
}

template<>
inline std::string toString<::android::hardware::automotive::sv::V1_0::OverlayPrimitive>(uint32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::automotive::sv::V1_0::OverlayPrimitive> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::automotive::sv::V1_0::OverlayPrimitive::TRIANGLES) == static_cast<uint32_t>(::android::hardware::automotive::sv::V1_0::OverlayPrimitive::TRIANGLES)) {
        os += (first ? "" : " | ");
        os += "TRIANGLES";
        first = false;
        flipped |= ::android::hardware::automotive::sv::V1_0::OverlayPrimitive::TRIANGLES;
    }
    if ((o & ::android::hardware::automotive::sv::V1_0::OverlayPrimitive::TRIANGLES_STRIP) == static_cast<uint32_t>(::android::hardware::automotive::sv::V1_0::OverlayPrimitive::TRIANGLES_STRIP)) {
        os += (first ? "" : " | ");
        os += "TRIANGLES_STRIP";
        first = false;
        flipped |= ::android::hardware::automotive::sv::V1_0::OverlayPrimitive::TRIANGLES_STRIP;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::automotive::sv::V1_0::OverlayPrimitive o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::automotive::sv::V1_0::OverlayPrimitive::TRIANGLES) {
        return "TRIANGLES";
    }
    if (o == ::android::hardware::automotive::sv::V1_0::OverlayPrimitive::TRIANGLES_STRIP) {
        return "TRIANGLES_STRIP";
    }
    std::string os;
    os += toHexString(static_cast<uint32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::automotive::sv::V1_0::OverlayPrimitive o, ::std::ostream* os) {
    *os << toString(o);
}

static inline std::string toString(const ::android::hardware::automotive::sv::V1_0::OverlayMemoryDesc& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".id = ";
    os += ::android::hardware::toString(o.id);
    os += ", .verticesCount = ";
    os += ::android::hardware::toString(o.verticesCount);
    os += ", .overlayPrimitive = ";
    os += ::android::hardware::automotive::sv::V1_0::toString(o.overlayPrimitive);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::automotive::sv::V1_0::OverlayMemoryDesc& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::automotive::sv::V1_0::OverlayMemoryDesc& lhs, const ::android::hardware::automotive::sv::V1_0::OverlayMemoryDesc& rhs) {
    if (lhs.id != rhs.id) {
        return false;
    }
    if (lhs.verticesCount != rhs.verticesCount) {
        return false;
    }
    if (lhs.overlayPrimitive != rhs.overlayPrimitive) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::automotive::sv::V1_0::OverlayMemoryDesc& lhs, const ::android::hardware::automotive::sv::V1_0::OverlayMemoryDesc& rhs){
    return !(lhs == rhs);
}

static inline std::string toString(const ::android::hardware::automotive::sv::V1_0::OverlaysData& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".overlaysMemoryDesc = ";
    os += ::android::hardware::toString(o.overlaysMemoryDesc);
    os += ", .overlaysMemory = ";
    os += ::android::hardware::toString(o.overlaysMemory);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::automotive::sv::V1_0::OverlaysData& o, ::std::ostream* os) {
    *os << toString(o);
}

// operator== and operator!= are not generated for OverlaysData


}  // namespace V1_0
}  // namespace sv
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
template<> inline constexpr std::array<::android::hardware::automotive::sv::V1_0::SvResult, 6> hidl_enum_values<::android::hardware::automotive::sv::V1_0::SvResult> = {
    ::android::hardware::automotive::sv::V1_0::SvResult::OK,
    ::android::hardware::automotive::sv::V1_0::SvResult::INVALID_ARG,
    ::android::hardware::automotive::sv::V1_0::SvResult::NOT_SUPPORTED,
    ::android::hardware::automotive::sv::V1_0::SvResult::VIEW_NOT_SET,
    ::android::hardware::automotive::sv::V1_0::SvResult::NO_RESOURCE,
    ::android::hardware::automotive::sv::V1_0::SvResult::INTERNAL_ERROR,
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
template<> inline constexpr std::array<::android::hardware::automotive::sv::V1_0::SvEvent, 5> hidl_enum_values<::android::hardware::automotive::sv::V1_0::SvEvent> = {
    ::android::hardware::automotive::sv::V1_0::SvEvent::STREAM_STARTED,
    ::android::hardware::automotive::sv::V1_0::SvEvent::STREAM_STOPPED,
    ::android::hardware::automotive::sv::V1_0::SvEvent::CONFIG_UPDATED,
    ::android::hardware::automotive::sv::V1_0::SvEvent::FRAME_DROPPED,
    ::android::hardware::automotive::sv::V1_0::SvEvent::TIMEOUT,
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
template<> inline constexpr std::array<::android::hardware::automotive::sv::V1_0::SvQuality, 2> hidl_enum_values<::android::hardware::automotive::sv::V1_0::SvQuality> = {
    ::android::hardware::automotive::sv::V1_0::SvQuality::HIGH,
    ::android::hardware::automotive::sv::V1_0::SvQuality::LOW,
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
template<> inline constexpr std::array<::android::hardware::automotive::sv::V1_0::OverlayPrimitive, 2> hidl_enum_values<::android::hardware::automotive::sv::V1_0::OverlayPrimitive> = {
    ::android::hardware::automotive::sv::V1_0::OverlayPrimitive::TRIANGLES,
    ::android::hardware::automotive::sv::V1_0::OverlayPrimitive::TRIANGLES_STRIP,
};
#pragma clang diagnostic pop
}  // namespace details
}  // namespace hardware
}  // namespace android


#endif  // HIDL_GENERATED_ANDROID_HARDWARE_AUTOMOTIVE_SV_V1_0_TYPES_H
