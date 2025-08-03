#ifndef HIDL_GENERATED_ANDROID_HARDWARE_RENDERSCRIPT_V1_0_TYPES_H
#define HIDL_GENERATED_ANDROID_HARDWARE_RENDERSCRIPT_V1_0_TYPES_H

#include <hidl/HidlSupport.h>
#include <hidl/MQDescriptor.h>
#include <utils/NativeHandle.h>
#include <utils/misc.h>

namespace android {
namespace hardware {
namespace renderscript {
namespace V1_0 {

// Forward declaration for forward reference support:
enum class ContextType : int32_t;
enum class AllocationUsageType : int32_t;
enum class AllocationMipmapControl : int32_t;
enum class AllocationCubemapFace : int32_t;
enum class DataType : int32_t;
enum class DataKind : int32_t;
enum class YuvFormat : int32_t;
enum class SamplerValue : int32_t;
enum class ForEachStrategy : int32_t;
struct ScriptCall;
enum class ContextFlags : int32_t;
enum class MessageToClientType : int32_t;
enum class ScriptIntrinsicID : int32_t;
enum class ThreadPriorities : int32_t;
enum class MetadataSignatureBitval : int32_t;

typedef uint64_t OpaqueHandle;

typedef void* Ptr;

typedef uint64_t Size;

typedef uint64_t Allocation;

typedef uint64_t AllocationAdapter;

typedef uint64_t Closure;

typedef uint64_t Element;

typedef uint64_t NativeWindow;

typedef uint64_t ObjectBase;

typedef uint64_t Sampler;

typedef uint64_t Script;

typedef uint64_t ScriptFieldID;

typedef uint64_t ScriptGroup;

typedef uint64_t ScriptGroup2;

typedef uint64_t ScriptInvokeID;

typedef uint64_t ScriptKernelID;

typedef uint64_t Type;

enum class ContextType : int32_t {
    NORMAL = 0,
    DEBUG = 1 /* ::android::hardware::renderscript::V1_0::ContextType.NORMAL implicitly + 1 */,
    PROFILE = 2 /* ::android::hardware::renderscript::V1_0::ContextType.DEBUG implicitly + 1 */,
};

enum class AllocationUsageType : int32_t {
    SCRIPT = 1 /* 0x0001 */,
    GRAPHICS_TEXTURE = 2 /* 0x0002 */,
    GRAPHICS_VERTEX = 4 /* 0x0004 */,
    GRAPHICS_CONSTANTS = 8 /* 0x0008 */,
    GRAPHICS_RENDER_TARGET = 16 /* 0x0010 */,
    IO_INPUT = 32 /* 0x0020 */,
    IO_OUTPUT = 64 /* 0x0040 */,
    SHARED = 128 /* 0x0080 */,
    OEM = 32768 /* 0x8000 */,
    ALL = 33023 /* 0x80FF */,
};

enum class AllocationMipmapControl : int32_t {
    NONE = 0,
    FULL = 1,
    ON_SYNC_TO_TEXTURE = 2,
};

enum class AllocationCubemapFace : int32_t {
    POSITIVE_X = 0,
    NEGATIVE_X = 1,
    POSITIVE_Y = 2,
    NEGATIVE_Y = 3,
    POSITIVE_Z = 4,
    NEGATIVE_Z = 5,
};

enum class DataType : int32_t {
    NONE = 0,
    FLOAT_16 = 1 /* ::android::hardware::renderscript::V1_0::DataType.NONE implicitly + 1 */,
    FLOAT_32 = 2 /* ::android::hardware::renderscript::V1_0::DataType.FLOAT_16 implicitly + 1 */,
    FLOAT_64 = 3 /* ::android::hardware::renderscript::V1_0::DataType.FLOAT_32 implicitly + 1 */,
    SIGNED_8 = 4 /* ::android::hardware::renderscript::V1_0::DataType.FLOAT_64 implicitly + 1 */,
    SIGNED_16 = 5 /* ::android::hardware::renderscript::V1_0::DataType.SIGNED_8 implicitly + 1 */,
    SIGNED_32 = 6 /* ::android::hardware::renderscript::V1_0::DataType.SIGNED_16 implicitly + 1 */,
    SIGNED_64 = 7 /* ::android::hardware::renderscript::V1_0::DataType.SIGNED_32 implicitly + 1 */,
    UNSIGNED_8 = 8 /* ::android::hardware::renderscript::V1_0::DataType.SIGNED_64 implicitly + 1 */,
    UNSIGNED_16 = 9 /* ::android::hardware::renderscript::V1_0::DataType.UNSIGNED_8 implicitly + 1 */,
    UNSIGNED_32 = 10 /* ::android::hardware::renderscript::V1_0::DataType.UNSIGNED_16 implicitly + 1 */,
    UNSIGNED_64 = 11 /* ::android::hardware::renderscript::V1_0::DataType.UNSIGNED_32 implicitly + 1 */,
    BOOLEAN = 12 /* ::android::hardware::renderscript::V1_0::DataType.UNSIGNED_64 implicitly + 1 */,
    UNSIGNED_5_6_5 = 13 /* ::android::hardware::renderscript::V1_0::DataType.BOOLEAN implicitly + 1 */,
    UNSIGNED_5_5_5_1 = 14 /* ::android::hardware::renderscript::V1_0::DataType.UNSIGNED_5_6_5 implicitly + 1 */,
    UNSIGNED_4_4_4_4 = 15 /* ::android::hardware::renderscript::V1_0::DataType.UNSIGNED_5_5_5_1 implicitly + 1 */,
    MATRIX_4X4 = 16 /* ::android::hardware::renderscript::V1_0::DataType.UNSIGNED_4_4_4_4 implicitly + 1 */,
    MATRIX_3X3 = 17 /* ::android::hardware::renderscript::V1_0::DataType.MATRIX_4X4 implicitly + 1 */,
    MATRIX_2X2 = 18 /* ::android::hardware::renderscript::V1_0::DataType.MATRIX_3X3 implicitly + 1 */,
    ELEMENT = 1000,
    TYPE = 1001 /* ::android::hardware::renderscript::V1_0::DataType.ELEMENT implicitly + 1 */,
    ALLOCATION = 1002 /* ::android::hardware::renderscript::V1_0::DataType.TYPE implicitly + 1 */,
    SAMPLER = 1003 /* ::android::hardware::renderscript::V1_0::DataType.ALLOCATION implicitly + 1 */,
    SCRIPT = 1004 /* ::android::hardware::renderscript::V1_0::DataType.SAMPLER implicitly + 1 */,
    MESH = 1005 /* ::android::hardware::renderscript::V1_0::DataType.SCRIPT implicitly + 1 */,
    PROGRAM_FRAGMENT = 1006 /* ::android::hardware::renderscript::V1_0::DataType.MESH implicitly + 1 */,
    PROGRAM_VERTEX = 1007 /* ::android::hardware::renderscript::V1_0::DataType.PROGRAM_FRAGMENT implicitly + 1 */,
    PROGRAM_RASTER = 1008 /* ::android::hardware::renderscript::V1_0::DataType.PROGRAM_VERTEX implicitly + 1 */,
    PROGRAM_STORE = 1009 /* ::android::hardware::renderscript::V1_0::DataType.PROGRAM_RASTER implicitly + 1 */,
    FONT = 1010 /* ::android::hardware::renderscript::V1_0::DataType.PROGRAM_STORE implicitly + 1 */,
    INVALID = 10000,
};

enum class DataKind : int32_t {
    USER = 0,
    PIXEL_L = 7,
    PIXEL_A = 8 /* ::android::hardware::renderscript::V1_0::DataKind.PIXEL_L implicitly + 1 */,
    PIXEL_LA = 9 /* ::android::hardware::renderscript::V1_0::DataKind.PIXEL_A implicitly + 1 */,
    PIXEL_RGB = 10 /* ::android::hardware::renderscript::V1_0::DataKind.PIXEL_LA implicitly + 1 */,
    PIXEL_RGBA = 11 /* ::android::hardware::renderscript::V1_0::DataKind.PIXEL_RGB implicitly + 1 */,
    PIXEL_DEPTH = 12 /* ::android::hardware::renderscript::V1_0::DataKind.PIXEL_RGBA implicitly + 1 */,
    PIXEL_YUV = 13 /* ::android::hardware::renderscript::V1_0::DataKind.PIXEL_DEPTH implicitly + 1 */,
    INVALID = 100,
};

enum class YuvFormat : int32_t {
    YUV_NONE = 0,
    YUV_YV12 = 842094169 /* 0x32315659 */,
    YUV_NV21 = 17 /* 0x11 */,
    YUV_420_888 = 35 /* 0x23 */,
};

enum class SamplerValue : int32_t {
    NEAREST = 0,
    LINEAR = 1 /* ::android::hardware::renderscript::V1_0::SamplerValue.NEAREST implicitly + 1 */,
    LINEAR_MIP_LINEAR = 2 /* ::android::hardware::renderscript::V1_0::SamplerValue.LINEAR implicitly + 1 */,
    WRAP = 3 /* ::android::hardware::renderscript::V1_0::SamplerValue.LINEAR_MIP_LINEAR implicitly + 1 */,
    CLAMP = 4 /* ::android::hardware::renderscript::V1_0::SamplerValue.WRAP implicitly + 1 */,
    LINEAR_MIP_NEAREST = 5 /* ::android::hardware::renderscript::V1_0::SamplerValue.CLAMP implicitly + 1 */,
    MIRRORED_REPEAT = 6 /* ::android::hardware::renderscript::V1_0::SamplerValue.LINEAR_MIP_NEAREST implicitly + 1 */,
    INVALID = 100,
};

enum class ForEachStrategy : int32_t {
    SERIAL = 0,
    DONT_CARE = 1,
    DST_LINEAR = 2,
    TILE_SMALL = 3,
    TILE_MEDIUM = 4,
    TILE_LARGE = 5,
};

struct ScriptCall final {
    ::android::hardware::renderscript::V1_0::ForEachStrategy strategy __attribute__ ((aligned(4)));
    uint32_t xStart __attribute__ ((aligned(4)));
    uint32_t xEnd __attribute__ ((aligned(4)));
    uint32_t yStart __attribute__ ((aligned(4)));
    uint32_t yEnd __attribute__ ((aligned(4)));
    uint32_t zStart __attribute__ ((aligned(4)));
    uint32_t zEnd __attribute__ ((aligned(4)));
    uint32_t arrayStart __attribute__ ((aligned(4)));
    uint32_t arrayEnd __attribute__ ((aligned(4)));
    uint32_t array2Start __attribute__ ((aligned(4)));
    uint32_t array2End __attribute__ ((aligned(4)));
    uint32_t array3Start __attribute__ ((aligned(4)));
    uint32_t array3End __attribute__ ((aligned(4)));
    uint32_t array4Start __attribute__ ((aligned(4)));
    uint32_t array4End __attribute__ ((aligned(4)));
};

static_assert(offsetof(::android::hardware::renderscript::V1_0::ScriptCall, strategy) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::renderscript::V1_0::ScriptCall, xStart) == 4, "wrong offset");
static_assert(offsetof(::android::hardware::renderscript::V1_0::ScriptCall, xEnd) == 8, "wrong offset");
static_assert(offsetof(::android::hardware::renderscript::V1_0::ScriptCall, yStart) == 12, "wrong offset");
static_assert(offsetof(::android::hardware::renderscript::V1_0::ScriptCall, yEnd) == 16, "wrong offset");
static_assert(offsetof(::android::hardware::renderscript::V1_0::ScriptCall, zStart) == 20, "wrong offset");
static_assert(offsetof(::android::hardware::renderscript::V1_0::ScriptCall, zEnd) == 24, "wrong offset");
static_assert(offsetof(::android::hardware::renderscript::V1_0::ScriptCall, arrayStart) == 28, "wrong offset");
static_assert(offsetof(::android::hardware::renderscript::V1_0::ScriptCall, arrayEnd) == 32, "wrong offset");
static_assert(offsetof(::android::hardware::renderscript::V1_0::ScriptCall, array2Start) == 36, "wrong offset");
static_assert(offsetof(::android::hardware::renderscript::V1_0::ScriptCall, array2End) == 40, "wrong offset");
static_assert(offsetof(::android::hardware::renderscript::V1_0::ScriptCall, array3Start) == 44, "wrong offset");
static_assert(offsetof(::android::hardware::renderscript::V1_0::ScriptCall, array3End) == 48, "wrong offset");
static_assert(offsetof(::android::hardware::renderscript::V1_0::ScriptCall, array4Start) == 52, "wrong offset");
static_assert(offsetof(::android::hardware::renderscript::V1_0::ScriptCall, array4End) == 56, "wrong offset");
static_assert(sizeof(::android::hardware::renderscript::V1_0::ScriptCall) == 60, "wrong size");
static_assert(__alignof(::android::hardware::renderscript::V1_0::ScriptCall) == 4, "wrong alignment");

enum class ContextFlags : int32_t {
    SYNCHRONOUS = 1 /* 1 << 0 */,
    LOW_LATENCY = 2 /* 1 << 1 */,
    LOW_POWER = 4 /* 1 << 2 */,
    WAIT_FOR_ATTACH = 8 /* 1 << 3 */,
};

enum class MessageToClientType : int32_t {
    NONE = 0,
    EXCEPTION = 1,
    RESIZE = 2,
    ERROR = 3,
    USER = 4,
    NEW_BUFFER = 5,
};

enum class ScriptIntrinsicID : int32_t {
    ID_UNDEFINED = 0,
    ID_CONVOLVE_3X3 = 1,
    ID_COLOR_MATRIX = 2,
    ID_LUT = 3,
    ID_CONVOLVE_5X5 = 4,
    ID_BLUR = 5,
    ID_YUV_TO_RGB = 6,
    ID_BLEND = 7,
    ID_3DLUT = 8,
    ID_HISTOGRAM = 9,
    ID_RESIZE = 12,
    ID_BLAS = 13,
    ID_EXTBLAS = 14,
    ID_OEM_START = 268435456 /* 0x10000000 */,
};

enum class ThreadPriorities : int32_t {
    LOW = 15,
    NORMAL_GRAPHICS = -8 /* -8 */,
    NORMAL = -1 /* -1 */,
    LOW_LATENCY = -4 /* -4 */,
};

enum class MetadataSignatureBitval : int32_t {
    SIG_None = 0,
    SIG_In = 1 /* 1 << 0 */,
    SIG_Out = 2 /* 1 << 1 */,
    SIG_Usr = 4 /* 1 << 2 */,
    SIG_X = 8 /* 1 << 3 */,
    SIG_Y = 16 /* 1 << 4 */,
    SIG_Kernel = 32 /* 1 << 5 */,
    SIG_Z = 64 /* 1 << 6 */,
    SIG_Ctxt = 128 /* 1 << 7 */,
};

//
// type declarations for package
//

template<typename>
static inline std::string toString(int32_t o);
static inline std::string toString(::android::hardware::renderscript::V1_0::ContextType o);
static inline void PrintTo(::android::hardware::renderscript::V1_0::ContextType o, ::std::ostream* os);
constexpr int32_t operator|(const ::android::hardware::renderscript::V1_0::ContextType lhs, const ::android::hardware::renderscript::V1_0::ContextType rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const int32_t lhs, const ::android::hardware::renderscript::V1_0::ContextType rhs) {
    return static_cast<int32_t>(lhs | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const ::android::hardware::renderscript::V1_0::ContextType lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | rhs);
}
constexpr int32_t operator&(const ::android::hardware::renderscript::V1_0::ContextType lhs, const ::android::hardware::renderscript::V1_0::ContextType rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const int32_t lhs, const ::android::hardware::renderscript::V1_0::ContextType rhs) {
    return static_cast<int32_t>(lhs & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const ::android::hardware::renderscript::V1_0::ContextType lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & rhs);
}
constexpr int32_t &operator|=(int32_t& v, const ::android::hardware::renderscript::V1_0::ContextType e) {
    v |= static_cast<int32_t>(e);
    return v;
}
constexpr int32_t &operator&=(int32_t& v, const ::android::hardware::renderscript::V1_0::ContextType e) {
    v &= static_cast<int32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(int32_t o);
static inline std::string toString(::android::hardware::renderscript::V1_0::AllocationUsageType o);
static inline void PrintTo(::android::hardware::renderscript::V1_0::AllocationUsageType o, ::std::ostream* os);
constexpr int32_t operator|(const ::android::hardware::renderscript::V1_0::AllocationUsageType lhs, const ::android::hardware::renderscript::V1_0::AllocationUsageType rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const int32_t lhs, const ::android::hardware::renderscript::V1_0::AllocationUsageType rhs) {
    return static_cast<int32_t>(lhs | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const ::android::hardware::renderscript::V1_0::AllocationUsageType lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | rhs);
}
constexpr int32_t operator&(const ::android::hardware::renderscript::V1_0::AllocationUsageType lhs, const ::android::hardware::renderscript::V1_0::AllocationUsageType rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const int32_t lhs, const ::android::hardware::renderscript::V1_0::AllocationUsageType rhs) {
    return static_cast<int32_t>(lhs & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const ::android::hardware::renderscript::V1_0::AllocationUsageType lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & rhs);
}
constexpr int32_t &operator|=(int32_t& v, const ::android::hardware::renderscript::V1_0::AllocationUsageType e) {
    v |= static_cast<int32_t>(e);
    return v;
}
constexpr int32_t &operator&=(int32_t& v, const ::android::hardware::renderscript::V1_0::AllocationUsageType e) {
    v &= static_cast<int32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(int32_t o);
static inline std::string toString(::android::hardware::renderscript::V1_0::AllocationMipmapControl o);
static inline void PrintTo(::android::hardware::renderscript::V1_0::AllocationMipmapControl o, ::std::ostream* os);
constexpr int32_t operator|(const ::android::hardware::renderscript::V1_0::AllocationMipmapControl lhs, const ::android::hardware::renderscript::V1_0::AllocationMipmapControl rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const int32_t lhs, const ::android::hardware::renderscript::V1_0::AllocationMipmapControl rhs) {
    return static_cast<int32_t>(lhs | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const ::android::hardware::renderscript::V1_0::AllocationMipmapControl lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | rhs);
}
constexpr int32_t operator&(const ::android::hardware::renderscript::V1_0::AllocationMipmapControl lhs, const ::android::hardware::renderscript::V1_0::AllocationMipmapControl rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const int32_t lhs, const ::android::hardware::renderscript::V1_0::AllocationMipmapControl rhs) {
    return static_cast<int32_t>(lhs & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const ::android::hardware::renderscript::V1_0::AllocationMipmapControl lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & rhs);
}
constexpr int32_t &operator|=(int32_t& v, const ::android::hardware::renderscript::V1_0::AllocationMipmapControl e) {
    v |= static_cast<int32_t>(e);
    return v;
}
constexpr int32_t &operator&=(int32_t& v, const ::android::hardware::renderscript::V1_0::AllocationMipmapControl e) {
    v &= static_cast<int32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(int32_t o);
static inline std::string toString(::android::hardware::renderscript::V1_0::AllocationCubemapFace o);
static inline void PrintTo(::android::hardware::renderscript::V1_0::AllocationCubemapFace o, ::std::ostream* os);
constexpr int32_t operator|(const ::android::hardware::renderscript::V1_0::AllocationCubemapFace lhs, const ::android::hardware::renderscript::V1_0::AllocationCubemapFace rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const int32_t lhs, const ::android::hardware::renderscript::V1_0::AllocationCubemapFace rhs) {
    return static_cast<int32_t>(lhs | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const ::android::hardware::renderscript::V1_0::AllocationCubemapFace lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | rhs);
}
constexpr int32_t operator&(const ::android::hardware::renderscript::V1_0::AllocationCubemapFace lhs, const ::android::hardware::renderscript::V1_0::AllocationCubemapFace rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const int32_t lhs, const ::android::hardware::renderscript::V1_0::AllocationCubemapFace rhs) {
    return static_cast<int32_t>(lhs & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const ::android::hardware::renderscript::V1_0::AllocationCubemapFace lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & rhs);
}
constexpr int32_t &operator|=(int32_t& v, const ::android::hardware::renderscript::V1_0::AllocationCubemapFace e) {
    v |= static_cast<int32_t>(e);
    return v;
}
constexpr int32_t &operator&=(int32_t& v, const ::android::hardware::renderscript::V1_0::AllocationCubemapFace e) {
    v &= static_cast<int32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(int32_t o);
static inline std::string toString(::android::hardware::renderscript::V1_0::DataType o);
static inline void PrintTo(::android::hardware::renderscript::V1_0::DataType o, ::std::ostream* os);
constexpr int32_t operator|(const ::android::hardware::renderscript::V1_0::DataType lhs, const ::android::hardware::renderscript::V1_0::DataType rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const int32_t lhs, const ::android::hardware::renderscript::V1_0::DataType rhs) {
    return static_cast<int32_t>(lhs | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const ::android::hardware::renderscript::V1_0::DataType lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | rhs);
}
constexpr int32_t operator&(const ::android::hardware::renderscript::V1_0::DataType lhs, const ::android::hardware::renderscript::V1_0::DataType rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const int32_t lhs, const ::android::hardware::renderscript::V1_0::DataType rhs) {
    return static_cast<int32_t>(lhs & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const ::android::hardware::renderscript::V1_0::DataType lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & rhs);
}
constexpr int32_t &operator|=(int32_t& v, const ::android::hardware::renderscript::V1_0::DataType e) {
    v |= static_cast<int32_t>(e);
    return v;
}
constexpr int32_t &operator&=(int32_t& v, const ::android::hardware::renderscript::V1_0::DataType e) {
    v &= static_cast<int32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(int32_t o);
static inline std::string toString(::android::hardware::renderscript::V1_0::DataKind o);
static inline void PrintTo(::android::hardware::renderscript::V1_0::DataKind o, ::std::ostream* os);
constexpr int32_t operator|(const ::android::hardware::renderscript::V1_0::DataKind lhs, const ::android::hardware::renderscript::V1_0::DataKind rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const int32_t lhs, const ::android::hardware::renderscript::V1_0::DataKind rhs) {
    return static_cast<int32_t>(lhs | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const ::android::hardware::renderscript::V1_0::DataKind lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | rhs);
}
constexpr int32_t operator&(const ::android::hardware::renderscript::V1_0::DataKind lhs, const ::android::hardware::renderscript::V1_0::DataKind rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const int32_t lhs, const ::android::hardware::renderscript::V1_0::DataKind rhs) {
    return static_cast<int32_t>(lhs & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const ::android::hardware::renderscript::V1_0::DataKind lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & rhs);
}
constexpr int32_t &operator|=(int32_t& v, const ::android::hardware::renderscript::V1_0::DataKind e) {
    v |= static_cast<int32_t>(e);
    return v;
}
constexpr int32_t &operator&=(int32_t& v, const ::android::hardware::renderscript::V1_0::DataKind e) {
    v &= static_cast<int32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(int32_t o);
static inline std::string toString(::android::hardware::renderscript::V1_0::YuvFormat o);
static inline void PrintTo(::android::hardware::renderscript::V1_0::YuvFormat o, ::std::ostream* os);
constexpr int32_t operator|(const ::android::hardware::renderscript::V1_0::YuvFormat lhs, const ::android::hardware::renderscript::V1_0::YuvFormat rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const int32_t lhs, const ::android::hardware::renderscript::V1_0::YuvFormat rhs) {
    return static_cast<int32_t>(lhs | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const ::android::hardware::renderscript::V1_0::YuvFormat lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | rhs);
}
constexpr int32_t operator&(const ::android::hardware::renderscript::V1_0::YuvFormat lhs, const ::android::hardware::renderscript::V1_0::YuvFormat rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const int32_t lhs, const ::android::hardware::renderscript::V1_0::YuvFormat rhs) {
    return static_cast<int32_t>(lhs & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const ::android::hardware::renderscript::V1_0::YuvFormat lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & rhs);
}
constexpr int32_t &operator|=(int32_t& v, const ::android::hardware::renderscript::V1_0::YuvFormat e) {
    v |= static_cast<int32_t>(e);
    return v;
}
constexpr int32_t &operator&=(int32_t& v, const ::android::hardware::renderscript::V1_0::YuvFormat e) {
    v &= static_cast<int32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(int32_t o);
static inline std::string toString(::android::hardware::renderscript::V1_0::SamplerValue o);
static inline void PrintTo(::android::hardware::renderscript::V1_0::SamplerValue o, ::std::ostream* os);
constexpr int32_t operator|(const ::android::hardware::renderscript::V1_0::SamplerValue lhs, const ::android::hardware::renderscript::V1_0::SamplerValue rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const int32_t lhs, const ::android::hardware::renderscript::V1_0::SamplerValue rhs) {
    return static_cast<int32_t>(lhs | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const ::android::hardware::renderscript::V1_0::SamplerValue lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | rhs);
}
constexpr int32_t operator&(const ::android::hardware::renderscript::V1_0::SamplerValue lhs, const ::android::hardware::renderscript::V1_0::SamplerValue rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const int32_t lhs, const ::android::hardware::renderscript::V1_0::SamplerValue rhs) {
    return static_cast<int32_t>(lhs & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const ::android::hardware::renderscript::V1_0::SamplerValue lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & rhs);
}
constexpr int32_t &operator|=(int32_t& v, const ::android::hardware::renderscript::V1_0::SamplerValue e) {
    v |= static_cast<int32_t>(e);
    return v;
}
constexpr int32_t &operator&=(int32_t& v, const ::android::hardware::renderscript::V1_0::SamplerValue e) {
    v &= static_cast<int32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(int32_t o);
static inline std::string toString(::android::hardware::renderscript::V1_0::ForEachStrategy o);
static inline void PrintTo(::android::hardware::renderscript::V1_0::ForEachStrategy o, ::std::ostream* os);
constexpr int32_t operator|(const ::android::hardware::renderscript::V1_0::ForEachStrategy lhs, const ::android::hardware::renderscript::V1_0::ForEachStrategy rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const int32_t lhs, const ::android::hardware::renderscript::V1_0::ForEachStrategy rhs) {
    return static_cast<int32_t>(lhs | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const ::android::hardware::renderscript::V1_0::ForEachStrategy lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | rhs);
}
constexpr int32_t operator&(const ::android::hardware::renderscript::V1_0::ForEachStrategy lhs, const ::android::hardware::renderscript::V1_0::ForEachStrategy rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const int32_t lhs, const ::android::hardware::renderscript::V1_0::ForEachStrategy rhs) {
    return static_cast<int32_t>(lhs & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const ::android::hardware::renderscript::V1_0::ForEachStrategy lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & rhs);
}
constexpr int32_t &operator|=(int32_t& v, const ::android::hardware::renderscript::V1_0::ForEachStrategy e) {
    v |= static_cast<int32_t>(e);
    return v;
}
constexpr int32_t &operator&=(int32_t& v, const ::android::hardware::renderscript::V1_0::ForEachStrategy e) {
    v &= static_cast<int32_t>(e);
    return v;
}

static inline std::string toString(const ::android::hardware::renderscript::V1_0::ScriptCall& o);
static inline void PrintTo(const ::android::hardware::renderscript::V1_0::ScriptCall& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::renderscript::V1_0::ScriptCall& lhs, const ::android::hardware::renderscript::V1_0::ScriptCall& rhs);
static inline bool operator!=(const ::android::hardware::renderscript::V1_0::ScriptCall& lhs, const ::android::hardware::renderscript::V1_0::ScriptCall& rhs);

template<typename>
static inline std::string toString(int32_t o);
static inline std::string toString(::android::hardware::renderscript::V1_0::ContextFlags o);
static inline void PrintTo(::android::hardware::renderscript::V1_0::ContextFlags o, ::std::ostream* os);
constexpr int32_t operator|(const ::android::hardware::renderscript::V1_0::ContextFlags lhs, const ::android::hardware::renderscript::V1_0::ContextFlags rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const int32_t lhs, const ::android::hardware::renderscript::V1_0::ContextFlags rhs) {
    return static_cast<int32_t>(lhs | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const ::android::hardware::renderscript::V1_0::ContextFlags lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | rhs);
}
constexpr int32_t operator&(const ::android::hardware::renderscript::V1_0::ContextFlags lhs, const ::android::hardware::renderscript::V1_0::ContextFlags rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const int32_t lhs, const ::android::hardware::renderscript::V1_0::ContextFlags rhs) {
    return static_cast<int32_t>(lhs & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const ::android::hardware::renderscript::V1_0::ContextFlags lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & rhs);
}
constexpr int32_t &operator|=(int32_t& v, const ::android::hardware::renderscript::V1_0::ContextFlags e) {
    v |= static_cast<int32_t>(e);
    return v;
}
constexpr int32_t &operator&=(int32_t& v, const ::android::hardware::renderscript::V1_0::ContextFlags e) {
    v &= static_cast<int32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(int32_t o);
static inline std::string toString(::android::hardware::renderscript::V1_0::MessageToClientType o);
static inline void PrintTo(::android::hardware::renderscript::V1_0::MessageToClientType o, ::std::ostream* os);
constexpr int32_t operator|(const ::android::hardware::renderscript::V1_0::MessageToClientType lhs, const ::android::hardware::renderscript::V1_0::MessageToClientType rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const int32_t lhs, const ::android::hardware::renderscript::V1_0::MessageToClientType rhs) {
    return static_cast<int32_t>(lhs | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const ::android::hardware::renderscript::V1_0::MessageToClientType lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | rhs);
}
constexpr int32_t operator&(const ::android::hardware::renderscript::V1_0::MessageToClientType lhs, const ::android::hardware::renderscript::V1_0::MessageToClientType rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const int32_t lhs, const ::android::hardware::renderscript::V1_0::MessageToClientType rhs) {
    return static_cast<int32_t>(lhs & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const ::android::hardware::renderscript::V1_0::MessageToClientType lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & rhs);
}
constexpr int32_t &operator|=(int32_t& v, const ::android::hardware::renderscript::V1_0::MessageToClientType e) {
    v |= static_cast<int32_t>(e);
    return v;
}
constexpr int32_t &operator&=(int32_t& v, const ::android::hardware::renderscript::V1_0::MessageToClientType e) {
    v &= static_cast<int32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(int32_t o);
static inline std::string toString(::android::hardware::renderscript::V1_0::ScriptIntrinsicID o);
static inline void PrintTo(::android::hardware::renderscript::V1_0::ScriptIntrinsicID o, ::std::ostream* os);
constexpr int32_t operator|(const ::android::hardware::renderscript::V1_0::ScriptIntrinsicID lhs, const ::android::hardware::renderscript::V1_0::ScriptIntrinsicID rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const int32_t lhs, const ::android::hardware::renderscript::V1_0::ScriptIntrinsicID rhs) {
    return static_cast<int32_t>(lhs | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const ::android::hardware::renderscript::V1_0::ScriptIntrinsicID lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | rhs);
}
constexpr int32_t operator&(const ::android::hardware::renderscript::V1_0::ScriptIntrinsicID lhs, const ::android::hardware::renderscript::V1_0::ScriptIntrinsicID rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const int32_t lhs, const ::android::hardware::renderscript::V1_0::ScriptIntrinsicID rhs) {
    return static_cast<int32_t>(lhs & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const ::android::hardware::renderscript::V1_0::ScriptIntrinsicID lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & rhs);
}
constexpr int32_t &operator|=(int32_t& v, const ::android::hardware::renderscript::V1_0::ScriptIntrinsicID e) {
    v |= static_cast<int32_t>(e);
    return v;
}
constexpr int32_t &operator&=(int32_t& v, const ::android::hardware::renderscript::V1_0::ScriptIntrinsicID e) {
    v &= static_cast<int32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(int32_t o);
static inline std::string toString(::android::hardware::renderscript::V1_0::ThreadPriorities o);
static inline void PrintTo(::android::hardware::renderscript::V1_0::ThreadPriorities o, ::std::ostream* os);
constexpr int32_t operator|(const ::android::hardware::renderscript::V1_0::ThreadPriorities lhs, const ::android::hardware::renderscript::V1_0::ThreadPriorities rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const int32_t lhs, const ::android::hardware::renderscript::V1_0::ThreadPriorities rhs) {
    return static_cast<int32_t>(lhs | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const ::android::hardware::renderscript::V1_0::ThreadPriorities lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | rhs);
}
constexpr int32_t operator&(const ::android::hardware::renderscript::V1_0::ThreadPriorities lhs, const ::android::hardware::renderscript::V1_0::ThreadPriorities rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const int32_t lhs, const ::android::hardware::renderscript::V1_0::ThreadPriorities rhs) {
    return static_cast<int32_t>(lhs & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const ::android::hardware::renderscript::V1_0::ThreadPriorities lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & rhs);
}
constexpr int32_t &operator|=(int32_t& v, const ::android::hardware::renderscript::V1_0::ThreadPriorities e) {
    v |= static_cast<int32_t>(e);
    return v;
}
constexpr int32_t &operator&=(int32_t& v, const ::android::hardware::renderscript::V1_0::ThreadPriorities e) {
    v &= static_cast<int32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(int32_t o);
static inline std::string toString(::android::hardware::renderscript::V1_0::MetadataSignatureBitval o);
static inline void PrintTo(::android::hardware::renderscript::V1_0::MetadataSignatureBitval o, ::std::ostream* os);
constexpr int32_t operator|(const ::android::hardware::renderscript::V1_0::MetadataSignatureBitval lhs, const ::android::hardware::renderscript::V1_0::MetadataSignatureBitval rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const int32_t lhs, const ::android::hardware::renderscript::V1_0::MetadataSignatureBitval rhs) {
    return static_cast<int32_t>(lhs | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const ::android::hardware::renderscript::V1_0::MetadataSignatureBitval lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | rhs);
}
constexpr int32_t operator&(const ::android::hardware::renderscript::V1_0::MetadataSignatureBitval lhs, const ::android::hardware::renderscript::V1_0::MetadataSignatureBitval rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const int32_t lhs, const ::android::hardware::renderscript::V1_0::MetadataSignatureBitval rhs) {
    return static_cast<int32_t>(lhs & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const ::android::hardware::renderscript::V1_0::MetadataSignatureBitval lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & rhs);
}
constexpr int32_t &operator|=(int32_t& v, const ::android::hardware::renderscript::V1_0::MetadataSignatureBitval e) {
    v |= static_cast<int32_t>(e);
    return v;
}
constexpr int32_t &operator&=(int32_t& v, const ::android::hardware::renderscript::V1_0::MetadataSignatureBitval e) {
    v &= static_cast<int32_t>(e);
    return v;
}

//
// type header definitions for package
//

template<>
inline std::string toString<::android::hardware::renderscript::V1_0::ContextType>(int32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::renderscript::V1_0::ContextType> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::renderscript::V1_0::ContextType::NORMAL) == static_cast<int32_t>(::android::hardware::renderscript::V1_0::ContextType::NORMAL)) {
        os += (first ? "" : " | ");
        os += "NORMAL";
        first = false;
        flipped |= ::android::hardware::renderscript::V1_0::ContextType::NORMAL;
    }
    if ((o & ::android::hardware::renderscript::V1_0::ContextType::DEBUG) == static_cast<int32_t>(::android::hardware::renderscript::V1_0::ContextType::DEBUG)) {
        os += (first ? "" : " | ");
        os += "DEBUG";
        first = false;
        flipped |= ::android::hardware::renderscript::V1_0::ContextType::DEBUG;
    }
    if ((o & ::android::hardware::renderscript::V1_0::ContextType::PROFILE) == static_cast<int32_t>(::android::hardware::renderscript::V1_0::ContextType::PROFILE)) {
        os += (first ? "" : " | ");
        os += "PROFILE";
        first = false;
        flipped |= ::android::hardware::renderscript::V1_0::ContextType::PROFILE;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::renderscript::V1_0::ContextType o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::renderscript::V1_0::ContextType::NORMAL) {
        return "NORMAL";
    }
    if (o == ::android::hardware::renderscript::V1_0::ContextType::DEBUG) {
        return "DEBUG";
    }
    if (o == ::android::hardware::renderscript::V1_0::ContextType::PROFILE) {
        return "PROFILE";
    }
    std::string os;
    os += toHexString(static_cast<int32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::renderscript::V1_0::ContextType o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::renderscript::V1_0::AllocationUsageType>(int32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::renderscript::V1_0::AllocationUsageType> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::renderscript::V1_0::AllocationUsageType::SCRIPT) == static_cast<int32_t>(::android::hardware::renderscript::V1_0::AllocationUsageType::SCRIPT)) {
        os += (first ? "" : " | ");
        os += "SCRIPT";
        first = false;
        flipped |= ::android::hardware::renderscript::V1_0::AllocationUsageType::SCRIPT;
    }
    if ((o & ::android::hardware::renderscript::V1_0::AllocationUsageType::GRAPHICS_TEXTURE) == static_cast<int32_t>(::android::hardware::renderscript::V1_0::AllocationUsageType::GRAPHICS_TEXTURE)) {
        os += (first ? "" : " | ");
        os += "GRAPHICS_TEXTURE";
        first = false;
        flipped |= ::android::hardware::renderscript::V1_0::AllocationUsageType::GRAPHICS_TEXTURE;
    }
    if ((o & ::android::hardware::renderscript::V1_0::AllocationUsageType::GRAPHICS_VERTEX) == static_cast<int32_t>(::android::hardware::renderscript::V1_0::AllocationUsageType::GRAPHICS_VERTEX)) {
        os += (first ? "" : " | ");
        os += "GRAPHICS_VERTEX";
        first = false;
        flipped |= ::android::hardware::renderscript::V1_0::AllocationUsageType::GRAPHICS_VERTEX;
    }
    if ((o & ::android::hardware::renderscript::V1_0::AllocationUsageType::GRAPHICS_CONSTANTS) == static_cast<int32_t>(::android::hardware::renderscript::V1_0::AllocationUsageType::GRAPHICS_CONSTANTS)) {
        os += (first ? "" : " | ");
        os += "GRAPHICS_CONSTANTS";
        first = false;
        flipped |= ::android::hardware::renderscript::V1_0::AllocationUsageType::GRAPHICS_CONSTANTS;
    }
    if ((o & ::android::hardware::renderscript::V1_0::AllocationUsageType::GRAPHICS_RENDER_TARGET) == static_cast<int32_t>(::android::hardware::renderscript::V1_0::AllocationUsageType::GRAPHICS_RENDER_TARGET)) {
        os += (first ? "" : " | ");
        os += "GRAPHICS_RENDER_TARGET";
        first = false;
        flipped |= ::android::hardware::renderscript::V1_0::AllocationUsageType::GRAPHICS_RENDER_TARGET;
    }
    if ((o & ::android::hardware::renderscript::V1_0::AllocationUsageType::IO_INPUT) == static_cast<int32_t>(::android::hardware::renderscript::V1_0::AllocationUsageType::IO_INPUT)) {
        os += (first ? "" : " | ");
        os += "IO_INPUT";
        first = false;
        flipped |= ::android::hardware::renderscript::V1_0::AllocationUsageType::IO_INPUT;
    }
    if ((o & ::android::hardware::renderscript::V1_0::AllocationUsageType::IO_OUTPUT) == static_cast<int32_t>(::android::hardware::renderscript::V1_0::AllocationUsageType::IO_OUTPUT)) {
        os += (first ? "" : " | ");
        os += "IO_OUTPUT";
        first = false;
        flipped |= ::android::hardware::renderscript::V1_0::AllocationUsageType::IO_OUTPUT;
    }
    if ((o & ::android::hardware::renderscript::V1_0::AllocationUsageType::SHARED) == static_cast<int32_t>(::android::hardware::renderscript::V1_0::AllocationUsageType::SHARED)) {
        os += (first ? "" : " | ");
        os += "SHARED";
        first = false;
        flipped |= ::android::hardware::renderscript::V1_0::AllocationUsageType::SHARED;
    }
    if ((o & ::android::hardware::renderscript::V1_0::AllocationUsageType::OEM) == static_cast<int32_t>(::android::hardware::renderscript::V1_0::AllocationUsageType::OEM)) {
        os += (first ? "" : " | ");
        os += "OEM";
        first = false;
        flipped |= ::android::hardware::renderscript::V1_0::AllocationUsageType::OEM;
    }
    if ((o & ::android::hardware::renderscript::V1_0::AllocationUsageType::ALL) == static_cast<int32_t>(::android::hardware::renderscript::V1_0::AllocationUsageType::ALL)) {
        os += (first ? "" : " | ");
        os += "ALL";
        first = false;
        flipped |= ::android::hardware::renderscript::V1_0::AllocationUsageType::ALL;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::renderscript::V1_0::AllocationUsageType o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::renderscript::V1_0::AllocationUsageType::SCRIPT) {
        return "SCRIPT";
    }
    if (o == ::android::hardware::renderscript::V1_0::AllocationUsageType::GRAPHICS_TEXTURE) {
        return "GRAPHICS_TEXTURE";
    }
    if (o == ::android::hardware::renderscript::V1_0::AllocationUsageType::GRAPHICS_VERTEX) {
        return "GRAPHICS_VERTEX";
    }
    if (o == ::android::hardware::renderscript::V1_0::AllocationUsageType::GRAPHICS_CONSTANTS) {
        return "GRAPHICS_CONSTANTS";
    }
    if (o == ::android::hardware::renderscript::V1_0::AllocationUsageType::GRAPHICS_RENDER_TARGET) {
        return "GRAPHICS_RENDER_TARGET";
    }
    if (o == ::android::hardware::renderscript::V1_0::AllocationUsageType::IO_INPUT) {
        return "IO_INPUT";
    }
    if (o == ::android::hardware::renderscript::V1_0::AllocationUsageType::IO_OUTPUT) {
        return "IO_OUTPUT";
    }
    if (o == ::android::hardware::renderscript::V1_0::AllocationUsageType::SHARED) {
        return "SHARED";
    }
    if (o == ::android::hardware::renderscript::V1_0::AllocationUsageType::OEM) {
        return "OEM";
    }
    if (o == ::android::hardware::renderscript::V1_0::AllocationUsageType::ALL) {
        return "ALL";
    }
    std::string os;
    os += toHexString(static_cast<int32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::renderscript::V1_0::AllocationUsageType o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::renderscript::V1_0::AllocationMipmapControl>(int32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::renderscript::V1_0::AllocationMipmapControl> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::renderscript::V1_0::AllocationMipmapControl::NONE) == static_cast<int32_t>(::android::hardware::renderscript::V1_0::AllocationMipmapControl::NONE)) {
        os += (first ? "" : " | ");
        os += "NONE";
        first = false;
        flipped |= ::android::hardware::renderscript::V1_0::AllocationMipmapControl::NONE;
    }
    if ((o & ::android::hardware::renderscript::V1_0::AllocationMipmapControl::FULL) == static_cast<int32_t>(::android::hardware::renderscript::V1_0::AllocationMipmapControl::FULL)) {
        os += (first ? "" : " | ");
        os += "FULL";
        first = false;
        flipped |= ::android::hardware::renderscript::V1_0::AllocationMipmapControl::FULL;
    }
    if ((o & ::android::hardware::renderscript::V1_0::AllocationMipmapControl::ON_SYNC_TO_TEXTURE) == static_cast<int32_t>(::android::hardware::renderscript::V1_0::AllocationMipmapControl::ON_SYNC_TO_TEXTURE)) {
        os += (first ? "" : " | ");
        os += "ON_SYNC_TO_TEXTURE";
        first = false;
        flipped |= ::android::hardware::renderscript::V1_0::AllocationMipmapControl::ON_SYNC_TO_TEXTURE;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::renderscript::V1_0::AllocationMipmapControl o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::renderscript::V1_0::AllocationMipmapControl::NONE) {
        return "NONE";
    }
    if (o == ::android::hardware::renderscript::V1_0::AllocationMipmapControl::FULL) {
        return "FULL";
    }
    if (o == ::android::hardware::renderscript::V1_0::AllocationMipmapControl::ON_SYNC_TO_TEXTURE) {
        return "ON_SYNC_TO_TEXTURE";
    }
    std::string os;
    os += toHexString(static_cast<int32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::renderscript::V1_0::AllocationMipmapControl o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::renderscript::V1_0::AllocationCubemapFace>(int32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::renderscript::V1_0::AllocationCubemapFace> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::renderscript::V1_0::AllocationCubemapFace::POSITIVE_X) == static_cast<int32_t>(::android::hardware::renderscript::V1_0::AllocationCubemapFace::POSITIVE_X)) {
        os += (first ? "" : " | ");
        os += "POSITIVE_X";
        first = false;
        flipped |= ::android::hardware::renderscript::V1_0::AllocationCubemapFace::POSITIVE_X;
    }
    if ((o & ::android::hardware::renderscript::V1_0::AllocationCubemapFace::NEGATIVE_X) == static_cast<int32_t>(::android::hardware::renderscript::V1_0::AllocationCubemapFace::NEGATIVE_X)) {
        os += (first ? "" : " | ");
        os += "NEGATIVE_X";
        first = false;
        flipped |= ::android::hardware::renderscript::V1_0::AllocationCubemapFace::NEGATIVE_X;
    }
    if ((o & ::android::hardware::renderscript::V1_0::AllocationCubemapFace::POSITIVE_Y) == static_cast<int32_t>(::android::hardware::renderscript::V1_0::AllocationCubemapFace::POSITIVE_Y)) {
        os += (first ? "" : " | ");
        os += "POSITIVE_Y";
        first = false;
        flipped |= ::android::hardware::renderscript::V1_0::AllocationCubemapFace::POSITIVE_Y;
    }
    if ((o & ::android::hardware::renderscript::V1_0::AllocationCubemapFace::NEGATIVE_Y) == static_cast<int32_t>(::android::hardware::renderscript::V1_0::AllocationCubemapFace::NEGATIVE_Y)) {
        os += (first ? "" : " | ");
        os += "NEGATIVE_Y";
        first = false;
        flipped |= ::android::hardware::renderscript::V1_0::AllocationCubemapFace::NEGATIVE_Y;
    }
    if ((o & ::android::hardware::renderscript::V1_0::AllocationCubemapFace::POSITIVE_Z) == static_cast<int32_t>(::android::hardware::renderscript::V1_0::AllocationCubemapFace::POSITIVE_Z)) {
        os += (first ? "" : " | ");
        os += "POSITIVE_Z";
        first = false;
        flipped |= ::android::hardware::renderscript::V1_0::AllocationCubemapFace::POSITIVE_Z;
    }
    if ((o & ::android::hardware::renderscript::V1_0::AllocationCubemapFace::NEGATIVE_Z) == static_cast<int32_t>(::android::hardware::renderscript::V1_0::AllocationCubemapFace::NEGATIVE_Z)) {
        os += (first ? "" : " | ");
        os += "NEGATIVE_Z";
        first = false;
        flipped |= ::android::hardware::renderscript::V1_0::AllocationCubemapFace::NEGATIVE_Z;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::renderscript::V1_0::AllocationCubemapFace o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::renderscript::V1_0::AllocationCubemapFace::POSITIVE_X) {
        return "POSITIVE_X";
    }
    if (o == ::android::hardware::renderscript::V1_0::AllocationCubemapFace::NEGATIVE_X) {
        return "NEGATIVE_X";
    }
    if (o == ::android::hardware::renderscript::V1_0::AllocationCubemapFace::POSITIVE_Y) {
        return "POSITIVE_Y";
    }
    if (o == ::android::hardware::renderscript::V1_0::AllocationCubemapFace::NEGATIVE_Y) {
        return "NEGATIVE_Y";
    }
    if (o == ::android::hardware::renderscript::V1_0::AllocationCubemapFace::POSITIVE_Z) {
        return "POSITIVE_Z";
    }
    if (o == ::android::hardware::renderscript::V1_0::AllocationCubemapFace::NEGATIVE_Z) {
        return "NEGATIVE_Z";
    }
    std::string os;
    os += toHexString(static_cast<int32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::renderscript::V1_0::AllocationCubemapFace o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::renderscript::V1_0::DataType>(int32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::renderscript::V1_0::DataType> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::renderscript::V1_0::DataType::NONE) == static_cast<int32_t>(::android::hardware::renderscript::V1_0::DataType::NONE)) {
        os += (first ? "" : " | ");
        os += "NONE";
        first = false;
        flipped |= ::android::hardware::renderscript::V1_0::DataType::NONE;
    }
    if ((o & ::android::hardware::renderscript::V1_0::DataType::FLOAT_16) == static_cast<int32_t>(::android::hardware::renderscript::V1_0::DataType::FLOAT_16)) {
        os += (first ? "" : " | ");
        os += "FLOAT_16";
        first = false;
        flipped |= ::android::hardware::renderscript::V1_0::DataType::FLOAT_16;
    }
    if ((o & ::android::hardware::renderscript::V1_0::DataType::FLOAT_32) == static_cast<int32_t>(::android::hardware::renderscript::V1_0::DataType::FLOAT_32)) {
        os += (first ? "" : " | ");
        os += "FLOAT_32";
        first = false;
        flipped |= ::android::hardware::renderscript::V1_0::DataType::FLOAT_32;
    }
    if ((o & ::android::hardware::renderscript::V1_0::DataType::FLOAT_64) == static_cast<int32_t>(::android::hardware::renderscript::V1_0::DataType::FLOAT_64)) {
        os += (first ? "" : " | ");
        os += "FLOAT_64";
        first = false;
        flipped |= ::android::hardware::renderscript::V1_0::DataType::FLOAT_64;
    }
    if ((o & ::android::hardware::renderscript::V1_0::DataType::SIGNED_8) == static_cast<int32_t>(::android::hardware::renderscript::V1_0::DataType::SIGNED_8)) {
        os += (first ? "" : " | ");
        os += "SIGNED_8";
        first = false;
        flipped |= ::android::hardware::renderscript::V1_0::DataType::SIGNED_8;
    }
    if ((o & ::android::hardware::renderscript::V1_0::DataType::SIGNED_16) == static_cast<int32_t>(::android::hardware::renderscript::V1_0::DataType::SIGNED_16)) {
        os += (first ? "" : " | ");
        os += "SIGNED_16";
        first = false;
        flipped |= ::android::hardware::renderscript::V1_0::DataType::SIGNED_16;
    }
    if ((o & ::android::hardware::renderscript::V1_0::DataType::SIGNED_32) == static_cast<int32_t>(::android::hardware::renderscript::V1_0::DataType::SIGNED_32)) {
        os += (first ? "" : " | ");
        os += "SIGNED_32";
        first = false;
        flipped |= ::android::hardware::renderscript::V1_0::DataType::SIGNED_32;
    }
    if ((o & ::android::hardware::renderscript::V1_0::DataType::SIGNED_64) == static_cast<int32_t>(::android::hardware::renderscript::V1_0::DataType::SIGNED_64)) {
        os += (first ? "" : " | ");
        os += "SIGNED_64";
        first = false;
        flipped |= ::android::hardware::renderscript::V1_0::DataType::SIGNED_64;
    }
    if ((o & ::android::hardware::renderscript::V1_0::DataType::UNSIGNED_8) == static_cast<int32_t>(::android::hardware::renderscript::V1_0::DataType::UNSIGNED_8)) {
        os += (first ? "" : " | ");
        os += "UNSIGNED_8";
        first = false;
        flipped |= ::android::hardware::renderscript::V1_0::DataType::UNSIGNED_8;
    }
    if ((o & ::android::hardware::renderscript::V1_0::DataType::UNSIGNED_16) == static_cast<int32_t>(::android::hardware::renderscript::V1_0::DataType::UNSIGNED_16)) {
        os += (first ? "" : " | ");
        os += "UNSIGNED_16";
        first = false;
        flipped |= ::android::hardware::renderscript::V1_0::DataType::UNSIGNED_16;
    }
    if ((o & ::android::hardware::renderscript::V1_0::DataType::UNSIGNED_32) == static_cast<int32_t>(::android::hardware::renderscript::V1_0::DataType::UNSIGNED_32)) {
        os += (first ? "" : " | ");
        os += "UNSIGNED_32";
        first = false;
        flipped |= ::android::hardware::renderscript::V1_0::DataType::UNSIGNED_32;
    }
    if ((o & ::android::hardware::renderscript::V1_0::DataType::UNSIGNED_64) == static_cast<int32_t>(::android::hardware::renderscript::V1_0::DataType::UNSIGNED_64)) {
        os += (first ? "" : " | ");
        os += "UNSIGNED_64";
        first = false;
        flipped |= ::android::hardware::renderscript::V1_0::DataType::UNSIGNED_64;
    }
    if ((o & ::android::hardware::renderscript::V1_0::DataType::BOOLEAN) == static_cast<int32_t>(::android::hardware::renderscript::V1_0::DataType::BOOLEAN)) {
        os += (first ? "" : " | ");
        os += "BOOLEAN";
        first = false;
        flipped |= ::android::hardware::renderscript::V1_0::DataType::BOOLEAN;
    }
    if ((o & ::android::hardware::renderscript::V1_0::DataType::UNSIGNED_5_6_5) == static_cast<int32_t>(::android::hardware::renderscript::V1_0::DataType::UNSIGNED_5_6_5)) {
        os += (first ? "" : " | ");
        os += "UNSIGNED_5_6_5";
        first = false;
        flipped |= ::android::hardware::renderscript::V1_0::DataType::UNSIGNED_5_6_5;
    }
    if ((o & ::android::hardware::renderscript::V1_0::DataType::UNSIGNED_5_5_5_1) == static_cast<int32_t>(::android::hardware::renderscript::V1_0::DataType::UNSIGNED_5_5_5_1)) {
        os += (first ? "" : " | ");
        os += "UNSIGNED_5_5_5_1";
        first = false;
        flipped |= ::android::hardware::renderscript::V1_0::DataType::UNSIGNED_5_5_5_1;
    }
    if ((o & ::android::hardware::renderscript::V1_0::DataType::UNSIGNED_4_4_4_4) == static_cast<int32_t>(::android::hardware::renderscript::V1_0::DataType::UNSIGNED_4_4_4_4)) {
        os += (first ? "" : " | ");
        os += "UNSIGNED_4_4_4_4";
        first = false;
        flipped |= ::android::hardware::renderscript::V1_0::DataType::UNSIGNED_4_4_4_4;
    }
    if ((o & ::android::hardware::renderscript::V1_0::DataType::MATRIX_4X4) == static_cast<int32_t>(::android::hardware::renderscript::V1_0::DataType::MATRIX_4X4)) {
        os += (first ? "" : " | ");
        os += "MATRIX_4X4";
        first = false;
        flipped |= ::android::hardware::renderscript::V1_0::DataType::MATRIX_4X4;
    }
    if ((o & ::android::hardware::renderscript::V1_0::DataType::MATRIX_3X3) == static_cast<int32_t>(::android::hardware::renderscript::V1_0::DataType::MATRIX_3X3)) {
        os += (first ? "" : " | ");
        os += "MATRIX_3X3";
        first = false;
        flipped |= ::android::hardware::renderscript::V1_0::DataType::MATRIX_3X3;
    }
    if ((o & ::android::hardware::renderscript::V1_0::DataType::MATRIX_2X2) == static_cast<int32_t>(::android::hardware::renderscript::V1_0::DataType::MATRIX_2X2)) {
        os += (first ? "" : " | ");
        os += "MATRIX_2X2";
        first = false;
        flipped |= ::android::hardware::renderscript::V1_0::DataType::MATRIX_2X2;
    }
    if ((o & ::android::hardware::renderscript::V1_0::DataType::ELEMENT) == static_cast<int32_t>(::android::hardware::renderscript::V1_0::DataType::ELEMENT)) {
        os += (first ? "" : " | ");
        os += "ELEMENT";
        first = false;
        flipped |= ::android::hardware::renderscript::V1_0::DataType::ELEMENT;
    }
    if ((o & ::android::hardware::renderscript::V1_0::DataType::TYPE) == static_cast<int32_t>(::android::hardware::renderscript::V1_0::DataType::TYPE)) {
        os += (first ? "" : " | ");
        os += "TYPE";
        first = false;
        flipped |= ::android::hardware::renderscript::V1_0::DataType::TYPE;
    }
    if ((o & ::android::hardware::renderscript::V1_0::DataType::ALLOCATION) == static_cast<int32_t>(::android::hardware::renderscript::V1_0::DataType::ALLOCATION)) {
        os += (first ? "" : " | ");
        os += "ALLOCATION";
        first = false;
        flipped |= ::android::hardware::renderscript::V1_0::DataType::ALLOCATION;
    }
    if ((o & ::android::hardware::renderscript::V1_0::DataType::SAMPLER) == static_cast<int32_t>(::android::hardware::renderscript::V1_0::DataType::SAMPLER)) {
        os += (first ? "" : " | ");
        os += "SAMPLER";
        first = false;
        flipped |= ::android::hardware::renderscript::V1_0::DataType::SAMPLER;
    }
    if ((o & ::android::hardware::renderscript::V1_0::DataType::SCRIPT) == static_cast<int32_t>(::android::hardware::renderscript::V1_0::DataType::SCRIPT)) {
        os += (first ? "" : " | ");
        os += "SCRIPT";
        first = false;
        flipped |= ::android::hardware::renderscript::V1_0::DataType::SCRIPT;
    }
    if ((o & ::android::hardware::renderscript::V1_0::DataType::MESH) == static_cast<int32_t>(::android::hardware::renderscript::V1_0::DataType::MESH)) {
        os += (first ? "" : " | ");
        os += "MESH";
        first = false;
        flipped |= ::android::hardware::renderscript::V1_0::DataType::MESH;
    }
    if ((o & ::android::hardware::renderscript::V1_0::DataType::PROGRAM_FRAGMENT) == static_cast<int32_t>(::android::hardware::renderscript::V1_0::DataType::PROGRAM_FRAGMENT)) {
        os += (first ? "" : " | ");
        os += "PROGRAM_FRAGMENT";
        first = false;
        flipped |= ::android::hardware::renderscript::V1_0::DataType::PROGRAM_FRAGMENT;
    }
    if ((o & ::android::hardware::renderscript::V1_0::DataType::PROGRAM_VERTEX) == static_cast<int32_t>(::android::hardware::renderscript::V1_0::DataType::PROGRAM_VERTEX)) {
        os += (first ? "" : " | ");
        os += "PROGRAM_VERTEX";
        first = false;
        flipped |= ::android::hardware::renderscript::V1_0::DataType::PROGRAM_VERTEX;
    }
    if ((o & ::android::hardware::renderscript::V1_0::DataType::PROGRAM_RASTER) == static_cast<int32_t>(::android::hardware::renderscript::V1_0::DataType::PROGRAM_RASTER)) {
        os += (first ? "" : " | ");
        os += "PROGRAM_RASTER";
        first = false;
        flipped |= ::android::hardware::renderscript::V1_0::DataType::PROGRAM_RASTER;
    }
    if ((o & ::android::hardware::renderscript::V1_0::DataType::PROGRAM_STORE) == static_cast<int32_t>(::android::hardware::renderscript::V1_0::DataType::PROGRAM_STORE)) {
        os += (first ? "" : " | ");
        os += "PROGRAM_STORE";
        first = false;
        flipped |= ::android::hardware::renderscript::V1_0::DataType::PROGRAM_STORE;
    }
    if ((o & ::android::hardware::renderscript::V1_0::DataType::FONT) == static_cast<int32_t>(::android::hardware::renderscript::V1_0::DataType::FONT)) {
        os += (first ? "" : " | ");
        os += "FONT";
        first = false;
        flipped |= ::android::hardware::renderscript::V1_0::DataType::FONT;
    }
    if ((o & ::android::hardware::renderscript::V1_0::DataType::INVALID) == static_cast<int32_t>(::android::hardware::renderscript::V1_0::DataType::INVALID)) {
        os += (first ? "" : " | ");
        os += "INVALID";
        first = false;
        flipped |= ::android::hardware::renderscript::V1_0::DataType::INVALID;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::renderscript::V1_0::DataType o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::renderscript::V1_0::DataType::NONE) {
        return "NONE";
    }
    if (o == ::android::hardware::renderscript::V1_0::DataType::FLOAT_16) {
        return "FLOAT_16";
    }
    if (o == ::android::hardware::renderscript::V1_0::DataType::FLOAT_32) {
        return "FLOAT_32";
    }
    if (o == ::android::hardware::renderscript::V1_0::DataType::FLOAT_64) {
        return "FLOAT_64";
    }
    if (o == ::android::hardware::renderscript::V1_0::DataType::SIGNED_8) {
        return "SIGNED_8";
    }
    if (o == ::android::hardware::renderscript::V1_0::DataType::SIGNED_16) {
        return "SIGNED_16";
    }
    if (o == ::android::hardware::renderscript::V1_0::DataType::SIGNED_32) {
        return "SIGNED_32";
    }
    if (o == ::android::hardware::renderscript::V1_0::DataType::SIGNED_64) {
        return "SIGNED_64";
    }
    if (o == ::android::hardware::renderscript::V1_0::DataType::UNSIGNED_8) {
        return "UNSIGNED_8";
    }
    if (o == ::android::hardware::renderscript::V1_0::DataType::UNSIGNED_16) {
        return "UNSIGNED_16";
    }
    if (o == ::android::hardware::renderscript::V1_0::DataType::UNSIGNED_32) {
        return "UNSIGNED_32";
    }
    if (o == ::android::hardware::renderscript::V1_0::DataType::UNSIGNED_64) {
        return "UNSIGNED_64";
    }
    if (o == ::android::hardware::renderscript::V1_0::DataType::BOOLEAN) {
        return "BOOLEAN";
    }
    if (o == ::android::hardware::renderscript::V1_0::DataType::UNSIGNED_5_6_5) {
        return "UNSIGNED_5_6_5";
    }
    if (o == ::android::hardware::renderscript::V1_0::DataType::UNSIGNED_5_5_5_1) {
        return "UNSIGNED_5_5_5_1";
    }
    if (o == ::android::hardware::renderscript::V1_0::DataType::UNSIGNED_4_4_4_4) {
        return "UNSIGNED_4_4_4_4";
    }
    if (o == ::android::hardware::renderscript::V1_0::DataType::MATRIX_4X4) {
        return "MATRIX_4X4";
    }
    if (o == ::android::hardware::renderscript::V1_0::DataType::MATRIX_3X3) {
        return "MATRIX_3X3";
    }
    if (o == ::android::hardware::renderscript::V1_0::DataType::MATRIX_2X2) {
        return "MATRIX_2X2";
    }
    if (o == ::android::hardware::renderscript::V1_0::DataType::ELEMENT) {
        return "ELEMENT";
    }
    if (o == ::android::hardware::renderscript::V1_0::DataType::TYPE) {
        return "TYPE";
    }
    if (o == ::android::hardware::renderscript::V1_0::DataType::ALLOCATION) {
        return "ALLOCATION";
    }
    if (o == ::android::hardware::renderscript::V1_0::DataType::SAMPLER) {
        return "SAMPLER";
    }
    if (o == ::android::hardware::renderscript::V1_0::DataType::SCRIPT) {
        return "SCRIPT";
    }
    if (o == ::android::hardware::renderscript::V1_0::DataType::MESH) {
        return "MESH";
    }
    if (o == ::android::hardware::renderscript::V1_0::DataType::PROGRAM_FRAGMENT) {
        return "PROGRAM_FRAGMENT";
    }
    if (o == ::android::hardware::renderscript::V1_0::DataType::PROGRAM_VERTEX) {
        return "PROGRAM_VERTEX";
    }
    if (o == ::android::hardware::renderscript::V1_0::DataType::PROGRAM_RASTER) {
        return "PROGRAM_RASTER";
    }
    if (o == ::android::hardware::renderscript::V1_0::DataType::PROGRAM_STORE) {
        return "PROGRAM_STORE";
    }
    if (o == ::android::hardware::renderscript::V1_0::DataType::FONT) {
        return "FONT";
    }
    if (o == ::android::hardware::renderscript::V1_0::DataType::INVALID) {
        return "INVALID";
    }
    std::string os;
    os += toHexString(static_cast<int32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::renderscript::V1_0::DataType o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::renderscript::V1_0::DataKind>(int32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::renderscript::V1_0::DataKind> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::renderscript::V1_0::DataKind::USER) == static_cast<int32_t>(::android::hardware::renderscript::V1_0::DataKind::USER)) {
        os += (first ? "" : " | ");
        os += "USER";
        first = false;
        flipped |= ::android::hardware::renderscript::V1_0::DataKind::USER;
    }
    if ((o & ::android::hardware::renderscript::V1_0::DataKind::PIXEL_L) == static_cast<int32_t>(::android::hardware::renderscript::V1_0::DataKind::PIXEL_L)) {
        os += (first ? "" : " | ");
        os += "PIXEL_L";
        first = false;
        flipped |= ::android::hardware::renderscript::V1_0::DataKind::PIXEL_L;
    }
    if ((o & ::android::hardware::renderscript::V1_0::DataKind::PIXEL_A) == static_cast<int32_t>(::android::hardware::renderscript::V1_0::DataKind::PIXEL_A)) {
        os += (first ? "" : " | ");
        os += "PIXEL_A";
        first = false;
        flipped |= ::android::hardware::renderscript::V1_0::DataKind::PIXEL_A;
    }
    if ((o & ::android::hardware::renderscript::V1_0::DataKind::PIXEL_LA) == static_cast<int32_t>(::android::hardware::renderscript::V1_0::DataKind::PIXEL_LA)) {
        os += (first ? "" : " | ");
        os += "PIXEL_LA";
        first = false;
        flipped |= ::android::hardware::renderscript::V1_0::DataKind::PIXEL_LA;
    }
    if ((o & ::android::hardware::renderscript::V1_0::DataKind::PIXEL_RGB) == static_cast<int32_t>(::android::hardware::renderscript::V1_0::DataKind::PIXEL_RGB)) {
        os += (first ? "" : " | ");
        os += "PIXEL_RGB";
        first = false;
        flipped |= ::android::hardware::renderscript::V1_0::DataKind::PIXEL_RGB;
    }
    if ((o & ::android::hardware::renderscript::V1_0::DataKind::PIXEL_RGBA) == static_cast<int32_t>(::android::hardware::renderscript::V1_0::DataKind::PIXEL_RGBA)) {
        os += (first ? "" : " | ");
        os += "PIXEL_RGBA";
        first = false;
        flipped |= ::android::hardware::renderscript::V1_0::DataKind::PIXEL_RGBA;
    }
    if ((o & ::android::hardware::renderscript::V1_0::DataKind::PIXEL_DEPTH) == static_cast<int32_t>(::android::hardware::renderscript::V1_0::DataKind::PIXEL_DEPTH)) {
        os += (first ? "" : " | ");
        os += "PIXEL_DEPTH";
        first = false;
        flipped |= ::android::hardware::renderscript::V1_0::DataKind::PIXEL_DEPTH;
    }
    if ((o & ::android::hardware::renderscript::V1_0::DataKind::PIXEL_YUV) == static_cast<int32_t>(::android::hardware::renderscript::V1_0::DataKind::PIXEL_YUV)) {
        os += (first ? "" : " | ");
        os += "PIXEL_YUV";
        first = false;
        flipped |= ::android::hardware::renderscript::V1_0::DataKind::PIXEL_YUV;
    }
    if ((o & ::android::hardware::renderscript::V1_0::DataKind::INVALID) == static_cast<int32_t>(::android::hardware::renderscript::V1_0::DataKind::INVALID)) {
        os += (first ? "" : " | ");
        os += "INVALID";
        first = false;
        flipped |= ::android::hardware::renderscript::V1_0::DataKind::INVALID;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::renderscript::V1_0::DataKind o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::renderscript::V1_0::DataKind::USER) {
        return "USER";
    }
    if (o == ::android::hardware::renderscript::V1_0::DataKind::PIXEL_L) {
        return "PIXEL_L";
    }
    if (o == ::android::hardware::renderscript::V1_0::DataKind::PIXEL_A) {
        return "PIXEL_A";
    }
    if (o == ::android::hardware::renderscript::V1_0::DataKind::PIXEL_LA) {
        return "PIXEL_LA";
    }
    if (o == ::android::hardware::renderscript::V1_0::DataKind::PIXEL_RGB) {
        return "PIXEL_RGB";
    }
    if (o == ::android::hardware::renderscript::V1_0::DataKind::PIXEL_RGBA) {
        return "PIXEL_RGBA";
    }
    if (o == ::android::hardware::renderscript::V1_0::DataKind::PIXEL_DEPTH) {
        return "PIXEL_DEPTH";
    }
    if (o == ::android::hardware::renderscript::V1_0::DataKind::PIXEL_YUV) {
        return "PIXEL_YUV";
    }
    if (o == ::android::hardware::renderscript::V1_0::DataKind::INVALID) {
        return "INVALID";
    }
    std::string os;
    os += toHexString(static_cast<int32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::renderscript::V1_0::DataKind o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::renderscript::V1_0::YuvFormat>(int32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::renderscript::V1_0::YuvFormat> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::renderscript::V1_0::YuvFormat::YUV_NONE) == static_cast<int32_t>(::android::hardware::renderscript::V1_0::YuvFormat::YUV_NONE)) {
        os += (first ? "" : " | ");
        os += "YUV_NONE";
        first = false;
        flipped |= ::android::hardware::renderscript::V1_0::YuvFormat::YUV_NONE;
    }
    if ((o & ::android::hardware::renderscript::V1_0::YuvFormat::YUV_YV12) == static_cast<int32_t>(::android::hardware::renderscript::V1_0::YuvFormat::YUV_YV12)) {
        os += (first ? "" : " | ");
        os += "YUV_YV12";
        first = false;
        flipped |= ::android::hardware::renderscript::V1_0::YuvFormat::YUV_YV12;
    }
    if ((o & ::android::hardware::renderscript::V1_0::YuvFormat::YUV_NV21) == static_cast<int32_t>(::android::hardware::renderscript::V1_0::YuvFormat::YUV_NV21)) {
        os += (first ? "" : " | ");
        os += "YUV_NV21";
        first = false;
        flipped |= ::android::hardware::renderscript::V1_0::YuvFormat::YUV_NV21;
    }
    if ((o & ::android::hardware::renderscript::V1_0::YuvFormat::YUV_420_888) == static_cast<int32_t>(::android::hardware::renderscript::V1_0::YuvFormat::YUV_420_888)) {
        os += (first ? "" : " | ");
        os += "YUV_420_888";
        first = false;
        flipped |= ::android::hardware::renderscript::V1_0::YuvFormat::YUV_420_888;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::renderscript::V1_0::YuvFormat o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::renderscript::V1_0::YuvFormat::YUV_NONE) {
        return "YUV_NONE";
    }
    if (o == ::android::hardware::renderscript::V1_0::YuvFormat::YUV_YV12) {
        return "YUV_YV12";
    }
    if (o == ::android::hardware::renderscript::V1_0::YuvFormat::YUV_NV21) {
        return "YUV_NV21";
    }
    if (o == ::android::hardware::renderscript::V1_0::YuvFormat::YUV_420_888) {
        return "YUV_420_888";
    }
    std::string os;
    os += toHexString(static_cast<int32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::renderscript::V1_0::YuvFormat o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::renderscript::V1_0::SamplerValue>(int32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::renderscript::V1_0::SamplerValue> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::renderscript::V1_0::SamplerValue::NEAREST) == static_cast<int32_t>(::android::hardware::renderscript::V1_0::SamplerValue::NEAREST)) {
        os += (first ? "" : " | ");
        os += "NEAREST";
        first = false;
        flipped |= ::android::hardware::renderscript::V1_0::SamplerValue::NEAREST;
    }
    if ((o & ::android::hardware::renderscript::V1_0::SamplerValue::LINEAR) == static_cast<int32_t>(::android::hardware::renderscript::V1_0::SamplerValue::LINEAR)) {
        os += (first ? "" : " | ");
        os += "LINEAR";
        first = false;
        flipped |= ::android::hardware::renderscript::V1_0::SamplerValue::LINEAR;
    }
    if ((o & ::android::hardware::renderscript::V1_0::SamplerValue::LINEAR_MIP_LINEAR) == static_cast<int32_t>(::android::hardware::renderscript::V1_0::SamplerValue::LINEAR_MIP_LINEAR)) {
        os += (first ? "" : " | ");
        os += "LINEAR_MIP_LINEAR";
        first = false;
        flipped |= ::android::hardware::renderscript::V1_0::SamplerValue::LINEAR_MIP_LINEAR;
    }
    if ((o & ::android::hardware::renderscript::V1_0::SamplerValue::WRAP) == static_cast<int32_t>(::android::hardware::renderscript::V1_0::SamplerValue::WRAP)) {
        os += (first ? "" : " | ");
        os += "WRAP";
        first = false;
        flipped |= ::android::hardware::renderscript::V1_0::SamplerValue::WRAP;
    }
    if ((o & ::android::hardware::renderscript::V1_0::SamplerValue::CLAMP) == static_cast<int32_t>(::android::hardware::renderscript::V1_0::SamplerValue::CLAMP)) {
        os += (first ? "" : " | ");
        os += "CLAMP";
        first = false;
        flipped |= ::android::hardware::renderscript::V1_0::SamplerValue::CLAMP;
    }
    if ((o & ::android::hardware::renderscript::V1_0::SamplerValue::LINEAR_MIP_NEAREST) == static_cast<int32_t>(::android::hardware::renderscript::V1_0::SamplerValue::LINEAR_MIP_NEAREST)) {
        os += (first ? "" : " | ");
        os += "LINEAR_MIP_NEAREST";
        first = false;
        flipped |= ::android::hardware::renderscript::V1_0::SamplerValue::LINEAR_MIP_NEAREST;
    }
    if ((o & ::android::hardware::renderscript::V1_0::SamplerValue::MIRRORED_REPEAT) == static_cast<int32_t>(::android::hardware::renderscript::V1_0::SamplerValue::MIRRORED_REPEAT)) {
        os += (first ? "" : " | ");
        os += "MIRRORED_REPEAT";
        first = false;
        flipped |= ::android::hardware::renderscript::V1_0::SamplerValue::MIRRORED_REPEAT;
    }
    if ((o & ::android::hardware::renderscript::V1_0::SamplerValue::INVALID) == static_cast<int32_t>(::android::hardware::renderscript::V1_0::SamplerValue::INVALID)) {
        os += (first ? "" : " | ");
        os += "INVALID";
        first = false;
        flipped |= ::android::hardware::renderscript::V1_0::SamplerValue::INVALID;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::renderscript::V1_0::SamplerValue o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::renderscript::V1_0::SamplerValue::NEAREST) {
        return "NEAREST";
    }
    if (o == ::android::hardware::renderscript::V1_0::SamplerValue::LINEAR) {
        return "LINEAR";
    }
    if (o == ::android::hardware::renderscript::V1_0::SamplerValue::LINEAR_MIP_LINEAR) {
        return "LINEAR_MIP_LINEAR";
    }
    if (o == ::android::hardware::renderscript::V1_0::SamplerValue::WRAP) {
        return "WRAP";
    }
    if (o == ::android::hardware::renderscript::V1_0::SamplerValue::CLAMP) {
        return "CLAMP";
    }
    if (o == ::android::hardware::renderscript::V1_0::SamplerValue::LINEAR_MIP_NEAREST) {
        return "LINEAR_MIP_NEAREST";
    }
    if (o == ::android::hardware::renderscript::V1_0::SamplerValue::MIRRORED_REPEAT) {
        return "MIRRORED_REPEAT";
    }
    if (o == ::android::hardware::renderscript::V1_0::SamplerValue::INVALID) {
        return "INVALID";
    }
    std::string os;
    os += toHexString(static_cast<int32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::renderscript::V1_0::SamplerValue o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::renderscript::V1_0::ForEachStrategy>(int32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::renderscript::V1_0::ForEachStrategy> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::renderscript::V1_0::ForEachStrategy::SERIAL) == static_cast<int32_t>(::android::hardware::renderscript::V1_0::ForEachStrategy::SERIAL)) {
        os += (first ? "" : " | ");
        os += "SERIAL";
        first = false;
        flipped |= ::android::hardware::renderscript::V1_0::ForEachStrategy::SERIAL;
    }
    if ((o & ::android::hardware::renderscript::V1_0::ForEachStrategy::DONT_CARE) == static_cast<int32_t>(::android::hardware::renderscript::V1_0::ForEachStrategy::DONT_CARE)) {
        os += (first ? "" : " | ");
        os += "DONT_CARE";
        first = false;
        flipped |= ::android::hardware::renderscript::V1_0::ForEachStrategy::DONT_CARE;
    }
    if ((o & ::android::hardware::renderscript::V1_0::ForEachStrategy::DST_LINEAR) == static_cast<int32_t>(::android::hardware::renderscript::V1_0::ForEachStrategy::DST_LINEAR)) {
        os += (first ? "" : " | ");
        os += "DST_LINEAR";
        first = false;
        flipped |= ::android::hardware::renderscript::V1_0::ForEachStrategy::DST_LINEAR;
    }
    if ((o & ::android::hardware::renderscript::V1_0::ForEachStrategy::TILE_SMALL) == static_cast<int32_t>(::android::hardware::renderscript::V1_0::ForEachStrategy::TILE_SMALL)) {
        os += (first ? "" : " | ");
        os += "TILE_SMALL";
        first = false;
        flipped |= ::android::hardware::renderscript::V1_0::ForEachStrategy::TILE_SMALL;
    }
    if ((o & ::android::hardware::renderscript::V1_0::ForEachStrategy::TILE_MEDIUM) == static_cast<int32_t>(::android::hardware::renderscript::V1_0::ForEachStrategy::TILE_MEDIUM)) {
        os += (first ? "" : " | ");
        os += "TILE_MEDIUM";
        first = false;
        flipped |= ::android::hardware::renderscript::V1_0::ForEachStrategy::TILE_MEDIUM;
    }
    if ((o & ::android::hardware::renderscript::V1_0::ForEachStrategy::TILE_LARGE) == static_cast<int32_t>(::android::hardware::renderscript::V1_0::ForEachStrategy::TILE_LARGE)) {
        os += (first ? "" : " | ");
        os += "TILE_LARGE";
        first = false;
        flipped |= ::android::hardware::renderscript::V1_0::ForEachStrategy::TILE_LARGE;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::renderscript::V1_0::ForEachStrategy o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::renderscript::V1_0::ForEachStrategy::SERIAL) {
        return "SERIAL";
    }
    if (o == ::android::hardware::renderscript::V1_0::ForEachStrategy::DONT_CARE) {
        return "DONT_CARE";
    }
    if (o == ::android::hardware::renderscript::V1_0::ForEachStrategy::DST_LINEAR) {
        return "DST_LINEAR";
    }
    if (o == ::android::hardware::renderscript::V1_0::ForEachStrategy::TILE_SMALL) {
        return "TILE_SMALL";
    }
    if (o == ::android::hardware::renderscript::V1_0::ForEachStrategy::TILE_MEDIUM) {
        return "TILE_MEDIUM";
    }
    if (o == ::android::hardware::renderscript::V1_0::ForEachStrategy::TILE_LARGE) {
        return "TILE_LARGE";
    }
    std::string os;
    os += toHexString(static_cast<int32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::renderscript::V1_0::ForEachStrategy o, ::std::ostream* os) {
    *os << toString(o);
}

static inline std::string toString(const ::android::hardware::renderscript::V1_0::ScriptCall& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".strategy = ";
    os += ::android::hardware::renderscript::V1_0::toString(o.strategy);
    os += ", .xStart = ";
    os += ::android::hardware::toString(o.xStart);
    os += ", .xEnd = ";
    os += ::android::hardware::toString(o.xEnd);
    os += ", .yStart = ";
    os += ::android::hardware::toString(o.yStart);
    os += ", .yEnd = ";
    os += ::android::hardware::toString(o.yEnd);
    os += ", .zStart = ";
    os += ::android::hardware::toString(o.zStart);
    os += ", .zEnd = ";
    os += ::android::hardware::toString(o.zEnd);
    os += ", .arrayStart = ";
    os += ::android::hardware::toString(o.arrayStart);
    os += ", .arrayEnd = ";
    os += ::android::hardware::toString(o.arrayEnd);
    os += ", .array2Start = ";
    os += ::android::hardware::toString(o.array2Start);
    os += ", .array2End = ";
    os += ::android::hardware::toString(o.array2End);
    os += ", .array3Start = ";
    os += ::android::hardware::toString(o.array3Start);
    os += ", .array3End = ";
    os += ::android::hardware::toString(o.array3End);
    os += ", .array4Start = ";
    os += ::android::hardware::toString(o.array4Start);
    os += ", .array4End = ";
    os += ::android::hardware::toString(o.array4End);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::renderscript::V1_0::ScriptCall& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::renderscript::V1_0::ScriptCall& lhs, const ::android::hardware::renderscript::V1_0::ScriptCall& rhs) {
    if (lhs.strategy != rhs.strategy) {
        return false;
    }
    if (lhs.xStart != rhs.xStart) {
        return false;
    }
    if (lhs.xEnd != rhs.xEnd) {
        return false;
    }
    if (lhs.yStart != rhs.yStart) {
        return false;
    }
    if (lhs.yEnd != rhs.yEnd) {
        return false;
    }
    if (lhs.zStart != rhs.zStart) {
        return false;
    }
    if (lhs.zEnd != rhs.zEnd) {
        return false;
    }
    if (lhs.arrayStart != rhs.arrayStart) {
        return false;
    }
    if (lhs.arrayEnd != rhs.arrayEnd) {
        return false;
    }
    if (lhs.array2Start != rhs.array2Start) {
        return false;
    }
    if (lhs.array2End != rhs.array2End) {
        return false;
    }
    if (lhs.array3Start != rhs.array3Start) {
        return false;
    }
    if (lhs.array3End != rhs.array3End) {
        return false;
    }
    if (lhs.array4Start != rhs.array4Start) {
        return false;
    }
    if (lhs.array4End != rhs.array4End) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::renderscript::V1_0::ScriptCall& lhs, const ::android::hardware::renderscript::V1_0::ScriptCall& rhs){
    return !(lhs == rhs);
}

template<>
inline std::string toString<::android::hardware::renderscript::V1_0::ContextFlags>(int32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::renderscript::V1_0::ContextFlags> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::renderscript::V1_0::ContextFlags::SYNCHRONOUS) == static_cast<int32_t>(::android::hardware::renderscript::V1_0::ContextFlags::SYNCHRONOUS)) {
        os += (first ? "" : " | ");
        os += "SYNCHRONOUS";
        first = false;
        flipped |= ::android::hardware::renderscript::V1_0::ContextFlags::SYNCHRONOUS;
    }
    if ((o & ::android::hardware::renderscript::V1_0::ContextFlags::LOW_LATENCY) == static_cast<int32_t>(::android::hardware::renderscript::V1_0::ContextFlags::LOW_LATENCY)) {
        os += (first ? "" : " | ");
        os += "LOW_LATENCY";
        first = false;
        flipped |= ::android::hardware::renderscript::V1_0::ContextFlags::LOW_LATENCY;
    }
    if ((o & ::android::hardware::renderscript::V1_0::ContextFlags::LOW_POWER) == static_cast<int32_t>(::android::hardware::renderscript::V1_0::ContextFlags::LOW_POWER)) {
        os += (first ? "" : " | ");
        os += "LOW_POWER";
        first = false;
        flipped |= ::android::hardware::renderscript::V1_0::ContextFlags::LOW_POWER;
    }
    if ((o & ::android::hardware::renderscript::V1_0::ContextFlags::WAIT_FOR_ATTACH) == static_cast<int32_t>(::android::hardware::renderscript::V1_0::ContextFlags::WAIT_FOR_ATTACH)) {
        os += (first ? "" : " | ");
        os += "WAIT_FOR_ATTACH";
        first = false;
        flipped |= ::android::hardware::renderscript::V1_0::ContextFlags::WAIT_FOR_ATTACH;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::renderscript::V1_0::ContextFlags o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::renderscript::V1_0::ContextFlags::SYNCHRONOUS) {
        return "SYNCHRONOUS";
    }
    if (o == ::android::hardware::renderscript::V1_0::ContextFlags::LOW_LATENCY) {
        return "LOW_LATENCY";
    }
    if (o == ::android::hardware::renderscript::V1_0::ContextFlags::LOW_POWER) {
        return "LOW_POWER";
    }
    if (o == ::android::hardware::renderscript::V1_0::ContextFlags::WAIT_FOR_ATTACH) {
        return "WAIT_FOR_ATTACH";
    }
    std::string os;
    os += toHexString(static_cast<int32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::renderscript::V1_0::ContextFlags o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::renderscript::V1_0::MessageToClientType>(int32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::renderscript::V1_0::MessageToClientType> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::renderscript::V1_0::MessageToClientType::NONE) == static_cast<int32_t>(::android::hardware::renderscript::V1_0::MessageToClientType::NONE)) {
        os += (first ? "" : " | ");
        os += "NONE";
        first = false;
        flipped |= ::android::hardware::renderscript::V1_0::MessageToClientType::NONE;
    }
    if ((o & ::android::hardware::renderscript::V1_0::MessageToClientType::EXCEPTION) == static_cast<int32_t>(::android::hardware::renderscript::V1_0::MessageToClientType::EXCEPTION)) {
        os += (first ? "" : " | ");
        os += "EXCEPTION";
        first = false;
        flipped |= ::android::hardware::renderscript::V1_0::MessageToClientType::EXCEPTION;
    }
    if ((o & ::android::hardware::renderscript::V1_0::MessageToClientType::RESIZE) == static_cast<int32_t>(::android::hardware::renderscript::V1_0::MessageToClientType::RESIZE)) {
        os += (first ? "" : " | ");
        os += "RESIZE";
        first = false;
        flipped |= ::android::hardware::renderscript::V1_0::MessageToClientType::RESIZE;
    }
    if ((o & ::android::hardware::renderscript::V1_0::MessageToClientType::ERROR) == static_cast<int32_t>(::android::hardware::renderscript::V1_0::MessageToClientType::ERROR)) {
        os += (first ? "" : " | ");
        os += "ERROR";
        first = false;
        flipped |= ::android::hardware::renderscript::V1_0::MessageToClientType::ERROR;
    }
    if ((o & ::android::hardware::renderscript::V1_0::MessageToClientType::USER) == static_cast<int32_t>(::android::hardware::renderscript::V1_0::MessageToClientType::USER)) {
        os += (first ? "" : " | ");
        os += "USER";
        first = false;
        flipped |= ::android::hardware::renderscript::V1_0::MessageToClientType::USER;
    }
    if ((o & ::android::hardware::renderscript::V1_0::MessageToClientType::NEW_BUFFER) == static_cast<int32_t>(::android::hardware::renderscript::V1_0::MessageToClientType::NEW_BUFFER)) {
        os += (first ? "" : " | ");
        os += "NEW_BUFFER";
        first = false;
        flipped |= ::android::hardware::renderscript::V1_0::MessageToClientType::NEW_BUFFER;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::renderscript::V1_0::MessageToClientType o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::renderscript::V1_0::MessageToClientType::NONE) {
        return "NONE";
    }
    if (o == ::android::hardware::renderscript::V1_0::MessageToClientType::EXCEPTION) {
        return "EXCEPTION";
    }
    if (o == ::android::hardware::renderscript::V1_0::MessageToClientType::RESIZE) {
        return "RESIZE";
    }
    if (o == ::android::hardware::renderscript::V1_0::MessageToClientType::ERROR) {
        return "ERROR";
    }
    if (o == ::android::hardware::renderscript::V1_0::MessageToClientType::USER) {
        return "USER";
    }
    if (o == ::android::hardware::renderscript::V1_0::MessageToClientType::NEW_BUFFER) {
        return "NEW_BUFFER";
    }
    std::string os;
    os += toHexString(static_cast<int32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::renderscript::V1_0::MessageToClientType o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::renderscript::V1_0::ScriptIntrinsicID>(int32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::renderscript::V1_0::ScriptIntrinsicID> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::renderscript::V1_0::ScriptIntrinsicID::ID_UNDEFINED) == static_cast<int32_t>(::android::hardware::renderscript::V1_0::ScriptIntrinsicID::ID_UNDEFINED)) {
        os += (first ? "" : " | ");
        os += "ID_UNDEFINED";
        first = false;
        flipped |= ::android::hardware::renderscript::V1_0::ScriptIntrinsicID::ID_UNDEFINED;
    }
    if ((o & ::android::hardware::renderscript::V1_0::ScriptIntrinsicID::ID_CONVOLVE_3X3) == static_cast<int32_t>(::android::hardware::renderscript::V1_0::ScriptIntrinsicID::ID_CONVOLVE_3X3)) {
        os += (first ? "" : " | ");
        os += "ID_CONVOLVE_3X3";
        first = false;
        flipped |= ::android::hardware::renderscript::V1_0::ScriptIntrinsicID::ID_CONVOLVE_3X3;
    }
    if ((o & ::android::hardware::renderscript::V1_0::ScriptIntrinsicID::ID_COLOR_MATRIX) == static_cast<int32_t>(::android::hardware::renderscript::V1_0::ScriptIntrinsicID::ID_COLOR_MATRIX)) {
        os += (first ? "" : " | ");
        os += "ID_COLOR_MATRIX";
        first = false;
        flipped |= ::android::hardware::renderscript::V1_0::ScriptIntrinsicID::ID_COLOR_MATRIX;
    }
    if ((o & ::android::hardware::renderscript::V1_0::ScriptIntrinsicID::ID_LUT) == static_cast<int32_t>(::android::hardware::renderscript::V1_0::ScriptIntrinsicID::ID_LUT)) {
        os += (first ? "" : " | ");
        os += "ID_LUT";
        first = false;
        flipped |= ::android::hardware::renderscript::V1_0::ScriptIntrinsicID::ID_LUT;
    }
    if ((o & ::android::hardware::renderscript::V1_0::ScriptIntrinsicID::ID_CONVOLVE_5X5) == static_cast<int32_t>(::android::hardware::renderscript::V1_0::ScriptIntrinsicID::ID_CONVOLVE_5X5)) {
        os += (first ? "" : " | ");
        os += "ID_CONVOLVE_5X5";
        first = false;
        flipped |= ::android::hardware::renderscript::V1_0::ScriptIntrinsicID::ID_CONVOLVE_5X5;
    }
    if ((o & ::android::hardware::renderscript::V1_0::ScriptIntrinsicID::ID_BLUR) == static_cast<int32_t>(::android::hardware::renderscript::V1_0::ScriptIntrinsicID::ID_BLUR)) {
        os += (first ? "" : " | ");
        os += "ID_BLUR";
        first = false;
        flipped |= ::android::hardware::renderscript::V1_0::ScriptIntrinsicID::ID_BLUR;
    }
    if ((o & ::android::hardware::renderscript::V1_0::ScriptIntrinsicID::ID_YUV_TO_RGB) == static_cast<int32_t>(::android::hardware::renderscript::V1_0::ScriptIntrinsicID::ID_YUV_TO_RGB)) {
        os += (first ? "" : " | ");
        os += "ID_YUV_TO_RGB";
        first = false;
        flipped |= ::android::hardware::renderscript::V1_0::ScriptIntrinsicID::ID_YUV_TO_RGB;
    }
    if ((o & ::android::hardware::renderscript::V1_0::ScriptIntrinsicID::ID_BLEND) == static_cast<int32_t>(::android::hardware::renderscript::V1_0::ScriptIntrinsicID::ID_BLEND)) {
        os += (first ? "" : " | ");
        os += "ID_BLEND";
        first = false;
        flipped |= ::android::hardware::renderscript::V1_0::ScriptIntrinsicID::ID_BLEND;
    }
    if ((o & ::android::hardware::renderscript::V1_0::ScriptIntrinsicID::ID_3DLUT) == static_cast<int32_t>(::android::hardware::renderscript::V1_0::ScriptIntrinsicID::ID_3DLUT)) {
        os += (first ? "" : " | ");
        os += "ID_3DLUT";
        first = false;
        flipped |= ::android::hardware::renderscript::V1_0::ScriptIntrinsicID::ID_3DLUT;
    }
    if ((o & ::android::hardware::renderscript::V1_0::ScriptIntrinsicID::ID_HISTOGRAM) == static_cast<int32_t>(::android::hardware::renderscript::V1_0::ScriptIntrinsicID::ID_HISTOGRAM)) {
        os += (first ? "" : " | ");
        os += "ID_HISTOGRAM";
        first = false;
        flipped |= ::android::hardware::renderscript::V1_0::ScriptIntrinsicID::ID_HISTOGRAM;
    }
    if ((o & ::android::hardware::renderscript::V1_0::ScriptIntrinsicID::ID_RESIZE) == static_cast<int32_t>(::android::hardware::renderscript::V1_0::ScriptIntrinsicID::ID_RESIZE)) {
        os += (first ? "" : " | ");
        os += "ID_RESIZE";
        first = false;
        flipped |= ::android::hardware::renderscript::V1_0::ScriptIntrinsicID::ID_RESIZE;
    }
    if ((o & ::android::hardware::renderscript::V1_0::ScriptIntrinsicID::ID_BLAS) == static_cast<int32_t>(::android::hardware::renderscript::V1_0::ScriptIntrinsicID::ID_BLAS)) {
        os += (first ? "" : " | ");
        os += "ID_BLAS";
        first = false;
        flipped |= ::android::hardware::renderscript::V1_0::ScriptIntrinsicID::ID_BLAS;
    }
    if ((o & ::android::hardware::renderscript::V1_0::ScriptIntrinsicID::ID_EXTBLAS) == static_cast<int32_t>(::android::hardware::renderscript::V1_0::ScriptIntrinsicID::ID_EXTBLAS)) {
        os += (first ? "" : " | ");
        os += "ID_EXTBLAS";
        first = false;
        flipped |= ::android::hardware::renderscript::V1_0::ScriptIntrinsicID::ID_EXTBLAS;
    }
    if ((o & ::android::hardware::renderscript::V1_0::ScriptIntrinsicID::ID_OEM_START) == static_cast<int32_t>(::android::hardware::renderscript::V1_0::ScriptIntrinsicID::ID_OEM_START)) {
        os += (first ? "" : " | ");
        os += "ID_OEM_START";
        first = false;
        flipped |= ::android::hardware::renderscript::V1_0::ScriptIntrinsicID::ID_OEM_START;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::renderscript::V1_0::ScriptIntrinsicID o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::renderscript::V1_0::ScriptIntrinsicID::ID_UNDEFINED) {
        return "ID_UNDEFINED";
    }
    if (o == ::android::hardware::renderscript::V1_0::ScriptIntrinsicID::ID_CONVOLVE_3X3) {
        return "ID_CONVOLVE_3X3";
    }
    if (o == ::android::hardware::renderscript::V1_0::ScriptIntrinsicID::ID_COLOR_MATRIX) {
        return "ID_COLOR_MATRIX";
    }
    if (o == ::android::hardware::renderscript::V1_0::ScriptIntrinsicID::ID_LUT) {
        return "ID_LUT";
    }
    if (o == ::android::hardware::renderscript::V1_0::ScriptIntrinsicID::ID_CONVOLVE_5X5) {
        return "ID_CONVOLVE_5X5";
    }
    if (o == ::android::hardware::renderscript::V1_0::ScriptIntrinsicID::ID_BLUR) {
        return "ID_BLUR";
    }
    if (o == ::android::hardware::renderscript::V1_0::ScriptIntrinsicID::ID_YUV_TO_RGB) {
        return "ID_YUV_TO_RGB";
    }
    if (o == ::android::hardware::renderscript::V1_0::ScriptIntrinsicID::ID_BLEND) {
        return "ID_BLEND";
    }
    if (o == ::android::hardware::renderscript::V1_0::ScriptIntrinsicID::ID_3DLUT) {
        return "ID_3DLUT";
    }
    if (o == ::android::hardware::renderscript::V1_0::ScriptIntrinsicID::ID_HISTOGRAM) {
        return "ID_HISTOGRAM";
    }
    if (o == ::android::hardware::renderscript::V1_0::ScriptIntrinsicID::ID_RESIZE) {
        return "ID_RESIZE";
    }
    if (o == ::android::hardware::renderscript::V1_0::ScriptIntrinsicID::ID_BLAS) {
        return "ID_BLAS";
    }
    if (o == ::android::hardware::renderscript::V1_0::ScriptIntrinsicID::ID_EXTBLAS) {
        return "ID_EXTBLAS";
    }
    if (o == ::android::hardware::renderscript::V1_0::ScriptIntrinsicID::ID_OEM_START) {
        return "ID_OEM_START";
    }
    std::string os;
    os += toHexString(static_cast<int32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::renderscript::V1_0::ScriptIntrinsicID o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::renderscript::V1_0::ThreadPriorities>(int32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::renderscript::V1_0::ThreadPriorities> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::renderscript::V1_0::ThreadPriorities::LOW) == static_cast<int32_t>(::android::hardware::renderscript::V1_0::ThreadPriorities::LOW)) {
        os += (first ? "" : " | ");
        os += "LOW";
        first = false;
        flipped |= ::android::hardware::renderscript::V1_0::ThreadPriorities::LOW;
    }
    if ((o & ::android::hardware::renderscript::V1_0::ThreadPriorities::NORMAL_GRAPHICS) == static_cast<int32_t>(::android::hardware::renderscript::V1_0::ThreadPriorities::NORMAL_GRAPHICS)) {
        os += (first ? "" : " | ");
        os += "NORMAL_GRAPHICS";
        first = false;
        flipped |= ::android::hardware::renderscript::V1_0::ThreadPriorities::NORMAL_GRAPHICS;
    }
    if ((o & ::android::hardware::renderscript::V1_0::ThreadPriorities::NORMAL) == static_cast<int32_t>(::android::hardware::renderscript::V1_0::ThreadPriorities::NORMAL)) {
        os += (first ? "" : " | ");
        os += "NORMAL";
        first = false;
        flipped |= ::android::hardware::renderscript::V1_0::ThreadPriorities::NORMAL;
    }
    if ((o & ::android::hardware::renderscript::V1_0::ThreadPriorities::LOW_LATENCY) == static_cast<int32_t>(::android::hardware::renderscript::V1_0::ThreadPriorities::LOW_LATENCY)) {
        os += (first ? "" : " | ");
        os += "LOW_LATENCY";
        first = false;
        flipped |= ::android::hardware::renderscript::V1_0::ThreadPriorities::LOW_LATENCY;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::renderscript::V1_0::ThreadPriorities o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::renderscript::V1_0::ThreadPriorities::LOW) {
        return "LOW";
    }
    if (o == ::android::hardware::renderscript::V1_0::ThreadPriorities::NORMAL_GRAPHICS) {
        return "NORMAL_GRAPHICS";
    }
    if (o == ::android::hardware::renderscript::V1_0::ThreadPriorities::NORMAL) {
        return "NORMAL";
    }
    if (o == ::android::hardware::renderscript::V1_0::ThreadPriorities::LOW_LATENCY) {
        return "LOW_LATENCY";
    }
    std::string os;
    os += toHexString(static_cast<int32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::renderscript::V1_0::ThreadPriorities o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::renderscript::V1_0::MetadataSignatureBitval>(int32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::renderscript::V1_0::MetadataSignatureBitval> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::renderscript::V1_0::MetadataSignatureBitval::SIG_None) == static_cast<int32_t>(::android::hardware::renderscript::V1_0::MetadataSignatureBitval::SIG_None)) {
        os += (first ? "" : " | ");
        os += "SIG_None";
        first = false;
        flipped |= ::android::hardware::renderscript::V1_0::MetadataSignatureBitval::SIG_None;
    }
    if ((o & ::android::hardware::renderscript::V1_0::MetadataSignatureBitval::SIG_In) == static_cast<int32_t>(::android::hardware::renderscript::V1_0::MetadataSignatureBitval::SIG_In)) {
        os += (first ? "" : " | ");
        os += "SIG_In";
        first = false;
        flipped |= ::android::hardware::renderscript::V1_0::MetadataSignatureBitval::SIG_In;
    }
    if ((o & ::android::hardware::renderscript::V1_0::MetadataSignatureBitval::SIG_Out) == static_cast<int32_t>(::android::hardware::renderscript::V1_0::MetadataSignatureBitval::SIG_Out)) {
        os += (first ? "" : " | ");
        os += "SIG_Out";
        first = false;
        flipped |= ::android::hardware::renderscript::V1_0::MetadataSignatureBitval::SIG_Out;
    }
    if ((o & ::android::hardware::renderscript::V1_0::MetadataSignatureBitval::SIG_Usr) == static_cast<int32_t>(::android::hardware::renderscript::V1_0::MetadataSignatureBitval::SIG_Usr)) {
        os += (first ? "" : " | ");
        os += "SIG_Usr";
        first = false;
        flipped |= ::android::hardware::renderscript::V1_0::MetadataSignatureBitval::SIG_Usr;
    }
    if ((o & ::android::hardware::renderscript::V1_0::MetadataSignatureBitval::SIG_X) == static_cast<int32_t>(::android::hardware::renderscript::V1_0::MetadataSignatureBitval::SIG_X)) {
        os += (first ? "" : " | ");
        os += "SIG_X";
        first = false;
        flipped |= ::android::hardware::renderscript::V1_0::MetadataSignatureBitval::SIG_X;
    }
    if ((o & ::android::hardware::renderscript::V1_0::MetadataSignatureBitval::SIG_Y) == static_cast<int32_t>(::android::hardware::renderscript::V1_0::MetadataSignatureBitval::SIG_Y)) {
        os += (first ? "" : " | ");
        os += "SIG_Y";
        first = false;
        flipped |= ::android::hardware::renderscript::V1_0::MetadataSignatureBitval::SIG_Y;
    }
    if ((o & ::android::hardware::renderscript::V1_0::MetadataSignatureBitval::SIG_Kernel) == static_cast<int32_t>(::android::hardware::renderscript::V1_0::MetadataSignatureBitval::SIG_Kernel)) {
        os += (first ? "" : " | ");
        os += "SIG_Kernel";
        first = false;
        flipped |= ::android::hardware::renderscript::V1_0::MetadataSignatureBitval::SIG_Kernel;
    }
    if ((o & ::android::hardware::renderscript::V1_0::MetadataSignatureBitval::SIG_Z) == static_cast<int32_t>(::android::hardware::renderscript::V1_0::MetadataSignatureBitval::SIG_Z)) {
        os += (first ? "" : " | ");
        os += "SIG_Z";
        first = false;
        flipped |= ::android::hardware::renderscript::V1_0::MetadataSignatureBitval::SIG_Z;
    }
    if ((o & ::android::hardware::renderscript::V1_0::MetadataSignatureBitval::SIG_Ctxt) == static_cast<int32_t>(::android::hardware::renderscript::V1_0::MetadataSignatureBitval::SIG_Ctxt)) {
        os += (first ? "" : " | ");
        os += "SIG_Ctxt";
        first = false;
        flipped |= ::android::hardware::renderscript::V1_0::MetadataSignatureBitval::SIG_Ctxt;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::renderscript::V1_0::MetadataSignatureBitval o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::renderscript::V1_0::MetadataSignatureBitval::SIG_None) {
        return "SIG_None";
    }
    if (o == ::android::hardware::renderscript::V1_0::MetadataSignatureBitval::SIG_In) {
        return "SIG_In";
    }
    if (o == ::android::hardware::renderscript::V1_0::MetadataSignatureBitval::SIG_Out) {
        return "SIG_Out";
    }
    if (o == ::android::hardware::renderscript::V1_0::MetadataSignatureBitval::SIG_Usr) {
        return "SIG_Usr";
    }
    if (o == ::android::hardware::renderscript::V1_0::MetadataSignatureBitval::SIG_X) {
        return "SIG_X";
    }
    if (o == ::android::hardware::renderscript::V1_0::MetadataSignatureBitval::SIG_Y) {
        return "SIG_Y";
    }
    if (o == ::android::hardware::renderscript::V1_0::MetadataSignatureBitval::SIG_Kernel) {
        return "SIG_Kernel";
    }
    if (o == ::android::hardware::renderscript::V1_0::MetadataSignatureBitval::SIG_Z) {
        return "SIG_Z";
    }
    if (o == ::android::hardware::renderscript::V1_0::MetadataSignatureBitval::SIG_Ctxt) {
        return "SIG_Ctxt";
    }
    std::string os;
    os += toHexString(static_cast<int32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::renderscript::V1_0::MetadataSignatureBitval o, ::std::ostream* os) {
    *os << toString(o);
}


}  // namespace V1_0
}  // namespace renderscript
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
template<> inline constexpr std::array<::android::hardware::renderscript::V1_0::ContextType, 3> hidl_enum_values<::android::hardware::renderscript::V1_0::ContextType> = {
    ::android::hardware::renderscript::V1_0::ContextType::NORMAL,
    ::android::hardware::renderscript::V1_0::ContextType::DEBUG,
    ::android::hardware::renderscript::V1_0::ContextType::PROFILE,
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
template<> inline constexpr std::array<::android::hardware::renderscript::V1_0::AllocationUsageType, 10> hidl_enum_values<::android::hardware::renderscript::V1_0::AllocationUsageType> = {
    ::android::hardware::renderscript::V1_0::AllocationUsageType::SCRIPT,
    ::android::hardware::renderscript::V1_0::AllocationUsageType::GRAPHICS_TEXTURE,
    ::android::hardware::renderscript::V1_0::AllocationUsageType::GRAPHICS_VERTEX,
    ::android::hardware::renderscript::V1_0::AllocationUsageType::GRAPHICS_CONSTANTS,
    ::android::hardware::renderscript::V1_0::AllocationUsageType::GRAPHICS_RENDER_TARGET,
    ::android::hardware::renderscript::V1_0::AllocationUsageType::IO_INPUT,
    ::android::hardware::renderscript::V1_0::AllocationUsageType::IO_OUTPUT,
    ::android::hardware::renderscript::V1_0::AllocationUsageType::SHARED,
    ::android::hardware::renderscript::V1_0::AllocationUsageType::OEM,
    ::android::hardware::renderscript::V1_0::AllocationUsageType::ALL,
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
template<> inline constexpr std::array<::android::hardware::renderscript::V1_0::AllocationMipmapControl, 3> hidl_enum_values<::android::hardware::renderscript::V1_0::AllocationMipmapControl> = {
    ::android::hardware::renderscript::V1_0::AllocationMipmapControl::NONE,
    ::android::hardware::renderscript::V1_0::AllocationMipmapControl::FULL,
    ::android::hardware::renderscript::V1_0::AllocationMipmapControl::ON_SYNC_TO_TEXTURE,
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
template<> inline constexpr std::array<::android::hardware::renderscript::V1_0::AllocationCubemapFace, 6> hidl_enum_values<::android::hardware::renderscript::V1_0::AllocationCubemapFace> = {
    ::android::hardware::renderscript::V1_0::AllocationCubemapFace::POSITIVE_X,
    ::android::hardware::renderscript::V1_0::AllocationCubemapFace::NEGATIVE_X,
    ::android::hardware::renderscript::V1_0::AllocationCubemapFace::POSITIVE_Y,
    ::android::hardware::renderscript::V1_0::AllocationCubemapFace::NEGATIVE_Y,
    ::android::hardware::renderscript::V1_0::AllocationCubemapFace::POSITIVE_Z,
    ::android::hardware::renderscript::V1_0::AllocationCubemapFace::NEGATIVE_Z,
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
template<> inline constexpr std::array<::android::hardware::renderscript::V1_0::DataType, 31> hidl_enum_values<::android::hardware::renderscript::V1_0::DataType> = {
    ::android::hardware::renderscript::V1_0::DataType::NONE,
    ::android::hardware::renderscript::V1_0::DataType::FLOAT_16,
    ::android::hardware::renderscript::V1_0::DataType::FLOAT_32,
    ::android::hardware::renderscript::V1_0::DataType::FLOAT_64,
    ::android::hardware::renderscript::V1_0::DataType::SIGNED_8,
    ::android::hardware::renderscript::V1_0::DataType::SIGNED_16,
    ::android::hardware::renderscript::V1_0::DataType::SIGNED_32,
    ::android::hardware::renderscript::V1_0::DataType::SIGNED_64,
    ::android::hardware::renderscript::V1_0::DataType::UNSIGNED_8,
    ::android::hardware::renderscript::V1_0::DataType::UNSIGNED_16,
    ::android::hardware::renderscript::V1_0::DataType::UNSIGNED_32,
    ::android::hardware::renderscript::V1_0::DataType::UNSIGNED_64,
    ::android::hardware::renderscript::V1_0::DataType::BOOLEAN,
    ::android::hardware::renderscript::V1_0::DataType::UNSIGNED_5_6_5,
    ::android::hardware::renderscript::V1_0::DataType::UNSIGNED_5_5_5_1,
    ::android::hardware::renderscript::V1_0::DataType::UNSIGNED_4_4_4_4,
    ::android::hardware::renderscript::V1_0::DataType::MATRIX_4X4,
    ::android::hardware::renderscript::V1_0::DataType::MATRIX_3X3,
    ::android::hardware::renderscript::V1_0::DataType::MATRIX_2X2,
    ::android::hardware::renderscript::V1_0::DataType::ELEMENT,
    ::android::hardware::renderscript::V1_0::DataType::TYPE,
    ::android::hardware::renderscript::V1_0::DataType::ALLOCATION,
    ::android::hardware::renderscript::V1_0::DataType::SAMPLER,
    ::android::hardware::renderscript::V1_0::DataType::SCRIPT,
    ::android::hardware::renderscript::V1_0::DataType::MESH,
    ::android::hardware::renderscript::V1_0::DataType::PROGRAM_FRAGMENT,
    ::android::hardware::renderscript::V1_0::DataType::PROGRAM_VERTEX,
    ::android::hardware::renderscript::V1_0::DataType::PROGRAM_RASTER,
    ::android::hardware::renderscript::V1_0::DataType::PROGRAM_STORE,
    ::android::hardware::renderscript::V1_0::DataType::FONT,
    ::android::hardware::renderscript::V1_0::DataType::INVALID,
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
template<> inline constexpr std::array<::android::hardware::renderscript::V1_0::DataKind, 9> hidl_enum_values<::android::hardware::renderscript::V1_0::DataKind> = {
    ::android::hardware::renderscript::V1_0::DataKind::USER,
    ::android::hardware::renderscript::V1_0::DataKind::PIXEL_L,
    ::android::hardware::renderscript::V1_0::DataKind::PIXEL_A,
    ::android::hardware::renderscript::V1_0::DataKind::PIXEL_LA,
    ::android::hardware::renderscript::V1_0::DataKind::PIXEL_RGB,
    ::android::hardware::renderscript::V1_0::DataKind::PIXEL_RGBA,
    ::android::hardware::renderscript::V1_0::DataKind::PIXEL_DEPTH,
    ::android::hardware::renderscript::V1_0::DataKind::PIXEL_YUV,
    ::android::hardware::renderscript::V1_0::DataKind::INVALID,
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
template<> inline constexpr std::array<::android::hardware::renderscript::V1_0::YuvFormat, 4> hidl_enum_values<::android::hardware::renderscript::V1_0::YuvFormat> = {
    ::android::hardware::renderscript::V1_0::YuvFormat::YUV_NONE,
    ::android::hardware::renderscript::V1_0::YuvFormat::YUV_YV12,
    ::android::hardware::renderscript::V1_0::YuvFormat::YUV_NV21,
    ::android::hardware::renderscript::V1_0::YuvFormat::YUV_420_888,
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
template<> inline constexpr std::array<::android::hardware::renderscript::V1_0::SamplerValue, 8> hidl_enum_values<::android::hardware::renderscript::V1_0::SamplerValue> = {
    ::android::hardware::renderscript::V1_0::SamplerValue::NEAREST,
    ::android::hardware::renderscript::V1_0::SamplerValue::LINEAR,
    ::android::hardware::renderscript::V1_0::SamplerValue::LINEAR_MIP_LINEAR,
    ::android::hardware::renderscript::V1_0::SamplerValue::WRAP,
    ::android::hardware::renderscript::V1_0::SamplerValue::CLAMP,
    ::android::hardware::renderscript::V1_0::SamplerValue::LINEAR_MIP_NEAREST,
    ::android::hardware::renderscript::V1_0::SamplerValue::MIRRORED_REPEAT,
    ::android::hardware::renderscript::V1_0::SamplerValue::INVALID,
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
template<> inline constexpr std::array<::android::hardware::renderscript::V1_0::ForEachStrategy, 6> hidl_enum_values<::android::hardware::renderscript::V1_0::ForEachStrategy> = {
    ::android::hardware::renderscript::V1_0::ForEachStrategy::SERIAL,
    ::android::hardware::renderscript::V1_0::ForEachStrategy::DONT_CARE,
    ::android::hardware::renderscript::V1_0::ForEachStrategy::DST_LINEAR,
    ::android::hardware::renderscript::V1_0::ForEachStrategy::TILE_SMALL,
    ::android::hardware::renderscript::V1_0::ForEachStrategy::TILE_MEDIUM,
    ::android::hardware::renderscript::V1_0::ForEachStrategy::TILE_LARGE,
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
template<> inline constexpr std::array<::android::hardware::renderscript::V1_0::ContextFlags, 4> hidl_enum_values<::android::hardware::renderscript::V1_0::ContextFlags> = {
    ::android::hardware::renderscript::V1_0::ContextFlags::SYNCHRONOUS,
    ::android::hardware::renderscript::V1_0::ContextFlags::LOW_LATENCY,
    ::android::hardware::renderscript::V1_0::ContextFlags::LOW_POWER,
    ::android::hardware::renderscript::V1_0::ContextFlags::WAIT_FOR_ATTACH,
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
template<> inline constexpr std::array<::android::hardware::renderscript::V1_0::MessageToClientType, 6> hidl_enum_values<::android::hardware::renderscript::V1_0::MessageToClientType> = {
    ::android::hardware::renderscript::V1_0::MessageToClientType::NONE,
    ::android::hardware::renderscript::V1_0::MessageToClientType::EXCEPTION,
    ::android::hardware::renderscript::V1_0::MessageToClientType::RESIZE,
    ::android::hardware::renderscript::V1_0::MessageToClientType::ERROR,
    ::android::hardware::renderscript::V1_0::MessageToClientType::USER,
    ::android::hardware::renderscript::V1_0::MessageToClientType::NEW_BUFFER,
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
template<> inline constexpr std::array<::android::hardware::renderscript::V1_0::ScriptIntrinsicID, 14> hidl_enum_values<::android::hardware::renderscript::V1_0::ScriptIntrinsicID> = {
    ::android::hardware::renderscript::V1_0::ScriptIntrinsicID::ID_UNDEFINED,
    ::android::hardware::renderscript::V1_0::ScriptIntrinsicID::ID_CONVOLVE_3X3,
    ::android::hardware::renderscript::V1_0::ScriptIntrinsicID::ID_COLOR_MATRIX,
    ::android::hardware::renderscript::V1_0::ScriptIntrinsicID::ID_LUT,
    ::android::hardware::renderscript::V1_0::ScriptIntrinsicID::ID_CONVOLVE_5X5,
    ::android::hardware::renderscript::V1_0::ScriptIntrinsicID::ID_BLUR,
    ::android::hardware::renderscript::V1_0::ScriptIntrinsicID::ID_YUV_TO_RGB,
    ::android::hardware::renderscript::V1_0::ScriptIntrinsicID::ID_BLEND,
    ::android::hardware::renderscript::V1_0::ScriptIntrinsicID::ID_3DLUT,
    ::android::hardware::renderscript::V1_0::ScriptIntrinsicID::ID_HISTOGRAM,
    ::android::hardware::renderscript::V1_0::ScriptIntrinsicID::ID_RESIZE,
    ::android::hardware::renderscript::V1_0::ScriptIntrinsicID::ID_BLAS,
    ::android::hardware::renderscript::V1_0::ScriptIntrinsicID::ID_EXTBLAS,
    ::android::hardware::renderscript::V1_0::ScriptIntrinsicID::ID_OEM_START,
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
template<> inline constexpr std::array<::android::hardware::renderscript::V1_0::ThreadPriorities, 4> hidl_enum_values<::android::hardware::renderscript::V1_0::ThreadPriorities> = {
    ::android::hardware::renderscript::V1_0::ThreadPriorities::LOW,
    ::android::hardware::renderscript::V1_0::ThreadPriorities::NORMAL_GRAPHICS,
    ::android::hardware::renderscript::V1_0::ThreadPriorities::NORMAL,
    ::android::hardware::renderscript::V1_0::ThreadPriorities::LOW_LATENCY,
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
template<> inline constexpr std::array<::android::hardware::renderscript::V1_0::MetadataSignatureBitval, 9> hidl_enum_values<::android::hardware::renderscript::V1_0::MetadataSignatureBitval> = {
    ::android::hardware::renderscript::V1_0::MetadataSignatureBitval::SIG_None,
    ::android::hardware::renderscript::V1_0::MetadataSignatureBitval::SIG_In,
    ::android::hardware::renderscript::V1_0::MetadataSignatureBitval::SIG_Out,
    ::android::hardware::renderscript::V1_0::MetadataSignatureBitval::SIG_Usr,
    ::android::hardware::renderscript::V1_0::MetadataSignatureBitval::SIG_X,
    ::android::hardware::renderscript::V1_0::MetadataSignatureBitval::SIG_Y,
    ::android::hardware::renderscript::V1_0::MetadataSignatureBitval::SIG_Kernel,
    ::android::hardware::renderscript::V1_0::MetadataSignatureBitval::SIG_Z,
    ::android::hardware::renderscript::V1_0::MetadataSignatureBitval::SIG_Ctxt,
};
#pragma clang diagnostic pop
}  // namespace details
}  // namespace hardware
}  // namespace android


#endif  // HIDL_GENERATED_ANDROID_HARDWARE_RENDERSCRIPT_V1_0_TYPES_H
