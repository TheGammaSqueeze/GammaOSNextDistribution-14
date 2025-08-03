#ifndef HIDL_GENERATED_ANDROID_HARDWARE_AUDIO_EFFECT_V6_0_TYPES_H
#define HIDL_GENERATED_ANDROID_HARDWARE_AUDIO_EFFECT_V6_0_TYPES_H

#include <android/hardware/audio/common/6.0/types.h>

#include <hidl/HidlSupport.h>
#include <hidl/MQDescriptor.h>
#include <utils/NativeHandle.h>
#include <utils/misc.h>

namespace android {
namespace hardware {
namespace audio {
namespace effect {
namespace V6_0 {

// Forward declaration for forward reference support:
enum class Result : int32_t;
enum class EffectFlags : int32_t;
struct EffectDescriptor;
struct AudioBuffer;
enum class EffectBufferAccess : int32_t;
enum class EffectConfigParameters : int32_t;
struct EffectBufferConfig;
struct EffectConfig;
enum class EffectFeature : int32_t;
struct EffectAuxChannelsConfig;
struct EffectOffloadParameter;
enum class MessageQueueFlagBits : uint32_t;

enum class Result : int32_t {
    OK = 0,
    NOT_INITIALIZED = 1 /* ::android::hardware::audio::effect::V6_0::Result.OK implicitly + 1 */,
    INVALID_ARGUMENTS = 2 /* ::android::hardware::audio::effect::V6_0::Result.NOT_INITIALIZED implicitly + 1 */,
    INVALID_STATE = 3 /* ::android::hardware::audio::effect::V6_0::Result.INVALID_ARGUMENTS implicitly + 1 */,
    NOT_SUPPORTED = 4 /* ::android::hardware::audio::effect::V6_0::Result.INVALID_STATE implicitly + 1 */,
    RESULT_TOO_BIG = 5 /* ::android::hardware::audio::effect::V6_0::Result.NOT_SUPPORTED implicitly + 1 */,
};

/**
 * Effect engine capabilities/requirements flags.
 *
 * Definitions for flags field of effect descriptor.
 *
 * +----------------+--------+--------------------------------------------------
 * | description    | bits   | values
 * +----------------+--------+--------------------------------------------------
 * | connection     | 0..2   | 0 insert: after track process
 * | mode           |        | 1 auxiliary: connect to track auxiliary
 * |                |        |  output and use send level
 * |                |        | 2 replace: replaces track process function;
 * |                |        |   must implement SRC, volume and mono to stereo.
 * |                |        | 3 pre processing: applied below audio HAL on in
 * |                |        | 4 post processing: applied below audio HAL on out
 * |                |        | 5 - 7 reserved
 * +----------------+--------+--------------------------------------------------
 * | insertion      | 3..5   | 0 none
 * | preference     |        | 1 first of the chain
 * |                |        | 2 last of the chain
 * |                |        | 3 exclusive (only effect in the insert chain)
 * |                |        | 4..7 reserved
 * +----------------+--------+--------------------------------------------------
 * | Volume         | 6..8   | 0 none
 * | management     |        | 1 implements volume control
 * |                |        | 2 requires volume indication
 * |                |        | 3 monitors requested volume
 * |                |        | 4 reserved
 * +----------------+--------+--------------------------------------------------
 * | Device         | 9..11  | 0 none
 * | indication     |        | 1 requires device updates
 * |                |        | 2, 4 reserved
 * +----------------+--------+--------------------------------------------------
 * | Sample input   | 12..13 | 1 direct: process() function or
 * | mode           |        |   EFFECT_CMD_SET_CONFIG command must specify
 * |                |        |   a buffer descriptor
 * |                |        | 2 provider: process() function uses the
 * |                |        |   bufferProvider indicated by the
 * |                |        |   EFFECT_CMD_SET_CONFIG command to request input.
 * |                |        |   buffers.
 * |                |        | 3 both: both input modes are supported
 * +----------------+--------+--------------------------------------------------
 * | Sample output  | 14..15 | 1 direct: process() function or
 * | mode           |        |   EFFECT_CMD_SET_CONFIG command must specify
 * |                |        |   a buffer descriptor
 * |                |        | 2 provider: process() function uses the
 * |                |        |   bufferProvider indicated by the
 * |                |        |   EFFECT_CMD_SET_CONFIG command to request output
 * |                |        |   buffers.
 * |                |        | 3 both: both output modes are supported
 * +----------------+--------+--------------------------------------------------
 * | Hardware       | 16..17 | 0 No hardware acceleration
 * | acceleration   |        | 1 non tunneled hw acceleration: the process()
 * |                |        |   function reads the samples, send them to HW
 * |                |        |   accelerated effect processor, reads back
 * |                |        |   the processed samples and returns them
 * |                |        |   to the output buffer.
 * |                |        | 2 tunneled hw acceleration: the process()
 * |                |        |   function is transparent. The effect interface
 * |                |        |   is only used to control the effect engine.
 * |                |        |   This mode is relevant for global effects
 * |                |        |   actually applied by the audio hardware on
 * |                |        |   the output stream.
 * +----------------+--------+--------------------------------------------------
 * | Audio Mode     | 18..19 | 0 none
 * | indication     |        | 1 requires audio mode updates
 * |                |        | 2..3 reserved
 * +----------------+--------+--------------------------------------------------
 * | Audio source   | 20..21 | 0 none
 * | indication     |        | 1 requires audio source updates
 * |                |        | 2..3 reserved
 * +----------------+--------+--------------------------------------------------
 * | Effect offload | 22     | 0 The effect cannot be offloaded to an audio DSP
 * | supported      |        | 1 The effect can be offloaded to an audio DSP
 * +----------------+--------+--------------------------------------------------
 * | Process        | 23     | 0 The effect implements a process function.
 * | function       |        | 1 The effect does not implement a process
 * | not            |        |   function: enabling the effect has no impact
 * | implemented    |        |   on latency or CPU load.
 * |                |        |   Effect implementations setting this flag do not
 * |                |        |   have to implement a process function.
 * +----------------+--------+--------------------------------------------------
 */
enum class EffectFlags : int32_t {
    TYPE_SHIFT = 0,
    TYPE_SIZE = 3,
    TYPE_MASK = 7 /* ((1 << TYPE_SIZE) - 1) << TYPE_SHIFT */,
    TYPE_INSERT = 0 /* 0 << TYPE_SHIFT */,
    TYPE_AUXILIARY = 1 /* 1 << TYPE_SHIFT */,
    TYPE_REPLACE = 2 /* 2 << TYPE_SHIFT */,
    TYPE_PRE_PROC = 3 /* 3 << TYPE_SHIFT */,
    TYPE_POST_PROC = 4 /* 4 << TYPE_SHIFT */,
    INSERT_SHIFT = 3 /* TYPE_SHIFT + TYPE_SIZE */,
    INSERT_SIZE = 3,
    INSERT_MASK = 56 /* ((1 << INSERT_SIZE) - 1) << INSERT_SHIFT */,
    INSERT_ANY = 0 /* 0 << INSERT_SHIFT */,
    INSERT_FIRST = 8 /* 1 << INSERT_SHIFT */,
    INSERT_LAST = 16 /* 2 << INSERT_SHIFT */,
    INSERT_EXCLUSIVE = 24 /* 3 << INSERT_SHIFT */,
    VOLUME_SHIFT = 6 /* INSERT_SHIFT + INSERT_SIZE */,
    VOLUME_SIZE = 3,
    VOLUME_MASK = 448 /* ((1 << VOLUME_SIZE) - 1) << VOLUME_SHIFT */,
    VOLUME_CTRL = 64 /* 1 << VOLUME_SHIFT */,
    VOLUME_IND = 128 /* 2 << VOLUME_SHIFT */,
    VOLUME_MONITOR = 192 /* 3 << VOLUME_SHIFT */,
    VOLUME_NONE = 0 /* 0 << VOLUME_SHIFT */,
    DEVICE_SHIFT = 9 /* VOLUME_SHIFT + VOLUME_SIZE */,
    DEVICE_SIZE = 3,
    DEVICE_MASK = 3584 /* ((1 << DEVICE_SIZE) - 1) << DEVICE_SHIFT */,
    DEVICE_IND = 512 /* 1 << DEVICE_SHIFT */,
    DEVICE_NONE = 0 /* 0 << DEVICE_SHIFT */,
    INPUT_SHIFT = 12 /* DEVICE_SHIFT + DEVICE_SIZE */,
    INPUT_SIZE = 2,
    INPUT_MASK = 12288 /* ((1 << INPUT_SIZE) - 1) << INPUT_SHIFT */,
    INPUT_DIRECT = 4096 /* 1 << INPUT_SHIFT */,
    INPUT_PROVIDER = 8192 /* 2 << INPUT_SHIFT */,
    INPUT_BOTH = 12288 /* 3 << INPUT_SHIFT */,
    OUTPUT_SHIFT = 14 /* INPUT_SHIFT + INPUT_SIZE */,
    OUTPUT_SIZE = 2,
    OUTPUT_MASK = 49152 /* ((1 << OUTPUT_SIZE) - 1) << OUTPUT_SHIFT */,
    OUTPUT_DIRECT = 16384 /* 1 << OUTPUT_SHIFT */,
    OUTPUT_PROVIDER = 32768 /* 2 << OUTPUT_SHIFT */,
    OUTPUT_BOTH = 49152 /* 3 << OUTPUT_SHIFT */,
    HW_ACC_SHIFT = 16 /* OUTPUT_SHIFT + OUTPUT_SIZE */,
    HW_ACC_SIZE = 2,
    HW_ACC_MASK = 196608 /* ((1 << HW_ACC_SIZE) - 1) << HW_ACC_SHIFT */,
    HW_ACC_SIMPLE = 65536 /* 1 << HW_ACC_SHIFT */,
    HW_ACC_TUNNEL = 131072 /* 2 << HW_ACC_SHIFT */,
    AUDIO_MODE_SHIFT = 18 /* HW_ACC_SHIFT + HW_ACC_SIZE */,
    AUDIO_MODE_SIZE = 2,
    AUDIO_MODE_MASK = 786432 /* ((1 << AUDIO_MODE_SIZE) - 1) << AUDIO_MODE_SHIFT */,
    AUDIO_MODE_IND = 262144 /* 1 << AUDIO_MODE_SHIFT */,
    AUDIO_MODE_NONE = 0 /* 0 << AUDIO_MODE_SHIFT */,
    AUDIO_SOURCE_SHIFT = 20 /* AUDIO_MODE_SHIFT + AUDIO_MODE_SIZE */,
    AUDIO_SOURCE_SIZE = 2,
    AUDIO_SOURCE_MASK = 3145728 /* ((1 << AUDIO_SOURCE_SIZE) - 1) << AUDIO_SOURCE_SHIFT */,
    AUDIO_SOURCE_IND = 1048576 /* 1 << AUDIO_SOURCE_SHIFT */,
    AUDIO_SOURCE_NONE = 0 /* 0 << AUDIO_SOURCE_SHIFT */,
    OFFLOAD_SHIFT = 22 /* AUDIO_SOURCE_SHIFT + AUDIO_SOURCE_SIZE */,
    OFFLOAD_SIZE = 1,
    OFFLOAD_MASK = 4194304 /* ((1 << OFFLOAD_SIZE) - 1) << OFFLOAD_SHIFT */,
    OFFLOAD_SUPPORTED = 4194304 /* 1 << OFFLOAD_SHIFT */,
    NO_PROCESS_SHIFT = 23 /* OFFLOAD_SHIFT + OFFLOAD_SIZE */,
    NO_PROCESS_SIZE = 1,
    NO_PROCESS_MASK = 8388608 /* ((1 << NO_PROCESS_SIZE) - 1) << NO_PROCESS_SHIFT */,
    NO_PROCESS = 8388608 /* 1 << NO_PROCESS_SHIFT */,
};

/**
 * The effect descriptor contains necessary information to facilitate the
 * enumeration of the effect engines present in a library.
 */
struct EffectDescriptor final {
    ::android::hardware::audio::common::V6_0::Uuid type __attribute__ ((aligned(4)));
    ::android::hardware::audio::common::V6_0::Uuid uuid __attribute__ ((aligned(4)));
    ::android::hardware::hidl_bitfield<::android::hardware::audio::effect::V6_0::EffectFlags> flags __attribute__ ((aligned(4)));
    uint16_t cpuLoad __attribute__ ((aligned(2)));
    uint16_t memoryUsage __attribute__ ((aligned(2)));
    ::android::hardware::hidl_array<uint8_t, 64> name __attribute__ ((aligned(1)));
    ::android::hardware::hidl_array<uint8_t, 64> implementor __attribute__ ((aligned(1)));
};

static_assert(offsetof(::android::hardware::audio::effect::V6_0::EffectDescriptor, type) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::audio::effect::V6_0::EffectDescriptor, uuid) == 16, "wrong offset");
static_assert(offsetof(::android::hardware::audio::effect::V6_0::EffectDescriptor, flags) == 32, "wrong offset");
static_assert(offsetof(::android::hardware::audio::effect::V6_0::EffectDescriptor, cpuLoad) == 36, "wrong offset");
static_assert(offsetof(::android::hardware::audio::effect::V6_0::EffectDescriptor, memoryUsage) == 38, "wrong offset");
static_assert(offsetof(::android::hardware::audio::effect::V6_0::EffectDescriptor, name) == 40, "wrong offset");
static_assert(offsetof(::android::hardware::audio::effect::V6_0::EffectDescriptor, implementor) == 104, "wrong offset");
static_assert(sizeof(::android::hardware::audio::effect::V6_0::EffectDescriptor) == 168, "wrong size");
static_assert(__alignof(::android::hardware::audio::effect::V6_0::EffectDescriptor) == 4, "wrong alignment");

/**
 * A buffer is a chunk of audio data for processing.  Multi-channel audio is
 * always interleaved. The channel order is from LSB to MSB with regard to the
 * channel mask definition in audio.h, audio_channel_mask_t, e.g.:
 * Stereo: L, R; 5.1: FL, FR, FC, LFE, BL, BR.
 *
 * The buffer size is expressed in frame count, a frame being composed of
 * samples for all channels at a given time. Frame size for unspecified format
 * (AUDIO_FORMAT_OTHER) is 8 bit by definition.
 */
struct AudioBuffer final {
    uint64_t id __attribute__ ((aligned(8)));
    uint32_t frameCount __attribute__ ((aligned(4)));
    ::android::hardware::hidl_memory data __attribute__ ((aligned(8)));
};

static_assert(offsetof(::android::hardware::audio::effect::V6_0::AudioBuffer, id) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::audio::effect::V6_0::AudioBuffer, frameCount) == 8, "wrong offset");
static_assert(offsetof(::android::hardware::audio::effect::V6_0::AudioBuffer, data) == 16, "wrong offset");
static_assert(sizeof(::android::hardware::audio::effect::V6_0::AudioBuffer) == 56, "wrong size");
static_assert(__alignof(::android::hardware::audio::effect::V6_0::AudioBuffer) == 8, "wrong alignment");

enum class EffectBufferAccess : int32_t {
    ACCESS_WRITE = 0,
    ACCESS_READ = 1 /* ::android::hardware::audio::effect::V6_0::EffectBufferAccess.ACCESS_WRITE implicitly + 1 */,
    ACCESS_ACCUMULATE = 2 /* ::android::hardware::audio::effect::V6_0::EffectBufferAccess.ACCESS_READ implicitly + 1 */,
};

/**
 * Determines what fields of EffectBufferConfig need to be considered.
 */
enum class EffectConfigParameters : int32_t {
    BUFFER = 1 /* 0x0001 */,
    SMP_RATE = 2 /* 0x0002 */,
    CHANNELS = 4 /* 0x0004 */,
    FORMAT = 8 /* 0x0008 */,
    ACC_MODE = 16 /* 0x0010 */,
};

/**
 * The buffer config structure specifies the input or output audio format
 * to be used by the effect engine.
 */
struct EffectBufferConfig final {
    ::android::hardware::audio::effect::V6_0::AudioBuffer buffer __attribute__ ((aligned(8)));
    uint32_t samplingRateHz __attribute__ ((aligned(4)));
    ::android::hardware::hidl_bitfield<::android::hardware::audio::common::V6_0::AudioChannelMask> channels __attribute__ ((aligned(4)));
    ::android::hardware::audio::common::V6_0::AudioFormat format __attribute__ ((aligned(4)));
    ::android::hardware::audio::effect::V6_0::EffectBufferAccess accessMode __attribute__ ((aligned(4)));
    ::android::hardware::hidl_bitfield<::android::hardware::audio::effect::V6_0::EffectConfigParameters> mask __attribute__ ((aligned(4)));
};

static_assert(offsetof(::android::hardware::audio::effect::V6_0::EffectBufferConfig, buffer) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::audio::effect::V6_0::EffectBufferConfig, samplingRateHz) == 56, "wrong offset");
static_assert(offsetof(::android::hardware::audio::effect::V6_0::EffectBufferConfig, channels) == 60, "wrong offset");
static_assert(offsetof(::android::hardware::audio::effect::V6_0::EffectBufferConfig, format) == 64, "wrong offset");
static_assert(offsetof(::android::hardware::audio::effect::V6_0::EffectBufferConfig, accessMode) == 68, "wrong offset");
static_assert(offsetof(::android::hardware::audio::effect::V6_0::EffectBufferConfig, mask) == 72, "wrong offset");
static_assert(sizeof(::android::hardware::audio::effect::V6_0::EffectBufferConfig) == 80, "wrong size");
static_assert(__alignof(::android::hardware::audio::effect::V6_0::EffectBufferConfig) == 8, "wrong alignment");

struct EffectConfig final {
    ::android::hardware::audio::effect::V6_0::EffectBufferConfig inputCfg __attribute__ ((aligned(8)));
    ::android::hardware::audio::effect::V6_0::EffectBufferConfig outputCfg __attribute__ ((aligned(8)));
};

static_assert(offsetof(::android::hardware::audio::effect::V6_0::EffectConfig, inputCfg) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::audio::effect::V6_0::EffectConfig, outputCfg) == 80, "wrong offset");
static_assert(sizeof(::android::hardware::audio::effect::V6_0::EffectConfig) == 160, "wrong size");
static_assert(__alignof(::android::hardware::audio::effect::V6_0::EffectConfig) == 8, "wrong alignment");

enum class EffectFeature : int32_t {
    AUX_CHANNELS = 0,
    CNT = 1 /* ::android::hardware::audio::effect::V6_0::EffectFeature.AUX_CHANNELS implicitly + 1 */,
};

struct EffectAuxChannelsConfig final {
    ::android::hardware::hidl_bitfield<::android::hardware::audio::common::V6_0::AudioChannelMask> mainChannels __attribute__ ((aligned(4)));
    ::android::hardware::hidl_bitfield<::android::hardware::audio::common::V6_0::AudioChannelMask> auxChannels __attribute__ ((aligned(4)));
};

static_assert(offsetof(::android::hardware::audio::effect::V6_0::EffectAuxChannelsConfig, mainChannels) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::audio::effect::V6_0::EffectAuxChannelsConfig, auxChannels) == 4, "wrong offset");
static_assert(sizeof(::android::hardware::audio::effect::V6_0::EffectAuxChannelsConfig) == 8, "wrong size");
static_assert(__alignof(::android::hardware::audio::effect::V6_0::EffectAuxChannelsConfig) == 4, "wrong alignment");

struct EffectOffloadParameter final {
    bool isOffload __attribute__ ((aligned(1)));
    int32_t ioHandle __attribute__ ((aligned(4)));
};

static_assert(offsetof(::android::hardware::audio::effect::V6_0::EffectOffloadParameter, isOffload) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::audio::effect::V6_0::EffectOffloadParameter, ioHandle) == 4, "wrong offset");
static_assert(sizeof(::android::hardware::audio::effect::V6_0::EffectOffloadParameter) == 8, "wrong size");
static_assert(__alignof(::android::hardware::audio::effect::V6_0::EffectOffloadParameter) == 4, "wrong alignment");

/**
 * The message queue flags used to synchronize reads and writes from
 * the status message queue used by effects.
 */
enum class MessageQueueFlagBits : uint32_t {
    DONE_PROCESSING = 1u /* 1 << 0 */,
    REQUEST_PROCESS = 2u /* 1 << 1 */,
    REQUEST_PROCESS_REVERSE = 4u /* 1 << 2 */,
    REQUEST_QUIT = 8u /* 1 << 3 */,
    REQUEST_PROCESS_ALL = 14u /* REQUEST_PROCESS | REQUEST_PROCESS_REVERSE | REQUEST_QUIT */,
};

//
// type declarations for package
//

template<typename>
static inline std::string toString(int32_t o);
static inline std::string toString(::android::hardware::audio::effect::V6_0::Result o);
static inline void PrintTo(::android::hardware::audio::effect::V6_0::Result o, ::std::ostream* os);
constexpr int32_t operator|(const ::android::hardware::audio::effect::V6_0::Result lhs, const ::android::hardware::audio::effect::V6_0::Result rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const int32_t lhs, const ::android::hardware::audio::effect::V6_0::Result rhs) {
    return static_cast<int32_t>(lhs | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const ::android::hardware::audio::effect::V6_0::Result lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | rhs);
}
constexpr int32_t operator&(const ::android::hardware::audio::effect::V6_0::Result lhs, const ::android::hardware::audio::effect::V6_0::Result rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const int32_t lhs, const ::android::hardware::audio::effect::V6_0::Result rhs) {
    return static_cast<int32_t>(lhs & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const ::android::hardware::audio::effect::V6_0::Result lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & rhs);
}
constexpr int32_t &operator|=(int32_t& v, const ::android::hardware::audio::effect::V6_0::Result e) {
    v |= static_cast<int32_t>(e);
    return v;
}
constexpr int32_t &operator&=(int32_t& v, const ::android::hardware::audio::effect::V6_0::Result e) {
    v &= static_cast<int32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(int32_t o);
static inline std::string toString(::android::hardware::audio::effect::V6_0::EffectFlags o);
static inline void PrintTo(::android::hardware::audio::effect::V6_0::EffectFlags o, ::std::ostream* os);
constexpr int32_t operator|(const ::android::hardware::audio::effect::V6_0::EffectFlags lhs, const ::android::hardware::audio::effect::V6_0::EffectFlags rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const int32_t lhs, const ::android::hardware::audio::effect::V6_0::EffectFlags rhs) {
    return static_cast<int32_t>(lhs | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const ::android::hardware::audio::effect::V6_0::EffectFlags lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | rhs);
}
constexpr int32_t operator&(const ::android::hardware::audio::effect::V6_0::EffectFlags lhs, const ::android::hardware::audio::effect::V6_0::EffectFlags rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const int32_t lhs, const ::android::hardware::audio::effect::V6_0::EffectFlags rhs) {
    return static_cast<int32_t>(lhs & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const ::android::hardware::audio::effect::V6_0::EffectFlags lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & rhs);
}
constexpr int32_t &operator|=(int32_t& v, const ::android::hardware::audio::effect::V6_0::EffectFlags e) {
    v |= static_cast<int32_t>(e);
    return v;
}
constexpr int32_t &operator&=(int32_t& v, const ::android::hardware::audio::effect::V6_0::EffectFlags e) {
    v &= static_cast<int32_t>(e);
    return v;
}

static inline std::string toString(const ::android::hardware::audio::effect::V6_0::EffectDescriptor& o);
static inline void PrintTo(const ::android::hardware::audio::effect::V6_0::EffectDescriptor& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::audio::effect::V6_0::EffectDescriptor& lhs, const ::android::hardware::audio::effect::V6_0::EffectDescriptor& rhs);
static inline bool operator!=(const ::android::hardware::audio::effect::V6_0::EffectDescriptor& lhs, const ::android::hardware::audio::effect::V6_0::EffectDescriptor& rhs);

static inline std::string toString(const ::android::hardware::audio::effect::V6_0::AudioBuffer& o);
static inline void PrintTo(const ::android::hardware::audio::effect::V6_0::AudioBuffer& o, ::std::ostream*);
// operator== and operator!= are not generated for AudioBuffer

template<typename>
static inline std::string toString(int32_t o);
static inline std::string toString(::android::hardware::audio::effect::V6_0::EffectBufferAccess o);
static inline void PrintTo(::android::hardware::audio::effect::V6_0::EffectBufferAccess o, ::std::ostream* os);
constexpr int32_t operator|(const ::android::hardware::audio::effect::V6_0::EffectBufferAccess lhs, const ::android::hardware::audio::effect::V6_0::EffectBufferAccess rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const int32_t lhs, const ::android::hardware::audio::effect::V6_0::EffectBufferAccess rhs) {
    return static_cast<int32_t>(lhs | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const ::android::hardware::audio::effect::V6_0::EffectBufferAccess lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | rhs);
}
constexpr int32_t operator&(const ::android::hardware::audio::effect::V6_0::EffectBufferAccess lhs, const ::android::hardware::audio::effect::V6_0::EffectBufferAccess rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const int32_t lhs, const ::android::hardware::audio::effect::V6_0::EffectBufferAccess rhs) {
    return static_cast<int32_t>(lhs & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const ::android::hardware::audio::effect::V6_0::EffectBufferAccess lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & rhs);
}
constexpr int32_t &operator|=(int32_t& v, const ::android::hardware::audio::effect::V6_0::EffectBufferAccess e) {
    v |= static_cast<int32_t>(e);
    return v;
}
constexpr int32_t &operator&=(int32_t& v, const ::android::hardware::audio::effect::V6_0::EffectBufferAccess e) {
    v &= static_cast<int32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(int32_t o);
static inline std::string toString(::android::hardware::audio::effect::V6_0::EffectConfigParameters o);
static inline void PrintTo(::android::hardware::audio::effect::V6_0::EffectConfigParameters o, ::std::ostream* os);
constexpr int32_t operator|(const ::android::hardware::audio::effect::V6_0::EffectConfigParameters lhs, const ::android::hardware::audio::effect::V6_0::EffectConfigParameters rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const int32_t lhs, const ::android::hardware::audio::effect::V6_0::EffectConfigParameters rhs) {
    return static_cast<int32_t>(lhs | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const ::android::hardware::audio::effect::V6_0::EffectConfigParameters lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | rhs);
}
constexpr int32_t operator&(const ::android::hardware::audio::effect::V6_0::EffectConfigParameters lhs, const ::android::hardware::audio::effect::V6_0::EffectConfigParameters rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const int32_t lhs, const ::android::hardware::audio::effect::V6_0::EffectConfigParameters rhs) {
    return static_cast<int32_t>(lhs & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const ::android::hardware::audio::effect::V6_0::EffectConfigParameters lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & rhs);
}
constexpr int32_t &operator|=(int32_t& v, const ::android::hardware::audio::effect::V6_0::EffectConfigParameters e) {
    v |= static_cast<int32_t>(e);
    return v;
}
constexpr int32_t &operator&=(int32_t& v, const ::android::hardware::audio::effect::V6_0::EffectConfigParameters e) {
    v &= static_cast<int32_t>(e);
    return v;
}

static inline std::string toString(const ::android::hardware::audio::effect::V6_0::EffectBufferConfig& o);
static inline void PrintTo(const ::android::hardware::audio::effect::V6_0::EffectBufferConfig& o, ::std::ostream*);
// operator== and operator!= are not generated for EffectBufferConfig

static inline std::string toString(const ::android::hardware::audio::effect::V6_0::EffectConfig& o);
static inline void PrintTo(const ::android::hardware::audio::effect::V6_0::EffectConfig& o, ::std::ostream*);
// operator== and operator!= are not generated for EffectConfig

template<typename>
static inline std::string toString(int32_t o);
static inline std::string toString(::android::hardware::audio::effect::V6_0::EffectFeature o);
static inline void PrintTo(::android::hardware::audio::effect::V6_0::EffectFeature o, ::std::ostream* os);
constexpr int32_t operator|(const ::android::hardware::audio::effect::V6_0::EffectFeature lhs, const ::android::hardware::audio::effect::V6_0::EffectFeature rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const int32_t lhs, const ::android::hardware::audio::effect::V6_0::EffectFeature rhs) {
    return static_cast<int32_t>(lhs | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const ::android::hardware::audio::effect::V6_0::EffectFeature lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | rhs);
}
constexpr int32_t operator&(const ::android::hardware::audio::effect::V6_0::EffectFeature lhs, const ::android::hardware::audio::effect::V6_0::EffectFeature rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const int32_t lhs, const ::android::hardware::audio::effect::V6_0::EffectFeature rhs) {
    return static_cast<int32_t>(lhs & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const ::android::hardware::audio::effect::V6_0::EffectFeature lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & rhs);
}
constexpr int32_t &operator|=(int32_t& v, const ::android::hardware::audio::effect::V6_0::EffectFeature e) {
    v |= static_cast<int32_t>(e);
    return v;
}
constexpr int32_t &operator&=(int32_t& v, const ::android::hardware::audio::effect::V6_0::EffectFeature e) {
    v &= static_cast<int32_t>(e);
    return v;
}

static inline std::string toString(const ::android::hardware::audio::effect::V6_0::EffectAuxChannelsConfig& o);
static inline void PrintTo(const ::android::hardware::audio::effect::V6_0::EffectAuxChannelsConfig& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::audio::effect::V6_0::EffectAuxChannelsConfig& lhs, const ::android::hardware::audio::effect::V6_0::EffectAuxChannelsConfig& rhs);
static inline bool operator!=(const ::android::hardware::audio::effect::V6_0::EffectAuxChannelsConfig& lhs, const ::android::hardware::audio::effect::V6_0::EffectAuxChannelsConfig& rhs);

static inline std::string toString(const ::android::hardware::audio::effect::V6_0::EffectOffloadParameter& o);
static inline void PrintTo(const ::android::hardware::audio::effect::V6_0::EffectOffloadParameter& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::audio::effect::V6_0::EffectOffloadParameter& lhs, const ::android::hardware::audio::effect::V6_0::EffectOffloadParameter& rhs);
static inline bool operator!=(const ::android::hardware::audio::effect::V6_0::EffectOffloadParameter& lhs, const ::android::hardware::audio::effect::V6_0::EffectOffloadParameter& rhs);

template<typename>
static inline std::string toString(uint32_t o);
static inline std::string toString(::android::hardware::audio::effect::V6_0::MessageQueueFlagBits o);
static inline void PrintTo(::android::hardware::audio::effect::V6_0::MessageQueueFlagBits o, ::std::ostream* os);
constexpr uint32_t operator|(const ::android::hardware::audio::effect::V6_0::MessageQueueFlagBits lhs, const ::android::hardware::audio::effect::V6_0::MessageQueueFlagBits rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const uint32_t lhs, const ::android::hardware::audio::effect::V6_0::MessageQueueFlagBits rhs) {
    return static_cast<uint32_t>(lhs | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const ::android::hardware::audio::effect::V6_0::MessageQueueFlagBits lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | rhs);
}
constexpr uint32_t operator&(const ::android::hardware::audio::effect::V6_0::MessageQueueFlagBits lhs, const ::android::hardware::audio::effect::V6_0::MessageQueueFlagBits rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const uint32_t lhs, const ::android::hardware::audio::effect::V6_0::MessageQueueFlagBits rhs) {
    return static_cast<uint32_t>(lhs & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const ::android::hardware::audio::effect::V6_0::MessageQueueFlagBits lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & rhs);
}
constexpr uint32_t &operator|=(uint32_t& v, const ::android::hardware::audio::effect::V6_0::MessageQueueFlagBits e) {
    v |= static_cast<uint32_t>(e);
    return v;
}
constexpr uint32_t &operator&=(uint32_t& v, const ::android::hardware::audio::effect::V6_0::MessageQueueFlagBits e) {
    v &= static_cast<uint32_t>(e);
    return v;
}

//
// type header definitions for package
//

template<>
inline std::string toString<::android::hardware::audio::effect::V6_0::Result>(int32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::audio::effect::V6_0::Result> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::audio::effect::V6_0::Result::OK) == static_cast<int32_t>(::android::hardware::audio::effect::V6_0::Result::OK)) {
        os += (first ? "" : " | ");
        os += "OK";
        first = false;
        flipped |= ::android::hardware::audio::effect::V6_0::Result::OK;
    }
    if ((o & ::android::hardware::audio::effect::V6_0::Result::NOT_INITIALIZED) == static_cast<int32_t>(::android::hardware::audio::effect::V6_0::Result::NOT_INITIALIZED)) {
        os += (first ? "" : " | ");
        os += "NOT_INITIALIZED";
        first = false;
        flipped |= ::android::hardware::audio::effect::V6_0::Result::NOT_INITIALIZED;
    }
    if ((o & ::android::hardware::audio::effect::V6_0::Result::INVALID_ARGUMENTS) == static_cast<int32_t>(::android::hardware::audio::effect::V6_0::Result::INVALID_ARGUMENTS)) {
        os += (first ? "" : " | ");
        os += "INVALID_ARGUMENTS";
        first = false;
        flipped |= ::android::hardware::audio::effect::V6_0::Result::INVALID_ARGUMENTS;
    }
    if ((o & ::android::hardware::audio::effect::V6_0::Result::INVALID_STATE) == static_cast<int32_t>(::android::hardware::audio::effect::V6_0::Result::INVALID_STATE)) {
        os += (first ? "" : " | ");
        os += "INVALID_STATE";
        first = false;
        flipped |= ::android::hardware::audio::effect::V6_0::Result::INVALID_STATE;
    }
    if ((o & ::android::hardware::audio::effect::V6_0::Result::NOT_SUPPORTED) == static_cast<int32_t>(::android::hardware::audio::effect::V6_0::Result::NOT_SUPPORTED)) {
        os += (first ? "" : " | ");
        os += "NOT_SUPPORTED";
        first = false;
        flipped |= ::android::hardware::audio::effect::V6_0::Result::NOT_SUPPORTED;
    }
    if ((o & ::android::hardware::audio::effect::V6_0::Result::RESULT_TOO_BIG) == static_cast<int32_t>(::android::hardware::audio::effect::V6_0::Result::RESULT_TOO_BIG)) {
        os += (first ? "" : " | ");
        os += "RESULT_TOO_BIG";
        first = false;
        flipped |= ::android::hardware::audio::effect::V6_0::Result::RESULT_TOO_BIG;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::audio::effect::V6_0::Result o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::audio::effect::V6_0::Result::OK) {
        return "OK";
    }
    if (o == ::android::hardware::audio::effect::V6_0::Result::NOT_INITIALIZED) {
        return "NOT_INITIALIZED";
    }
    if (o == ::android::hardware::audio::effect::V6_0::Result::INVALID_ARGUMENTS) {
        return "INVALID_ARGUMENTS";
    }
    if (o == ::android::hardware::audio::effect::V6_0::Result::INVALID_STATE) {
        return "INVALID_STATE";
    }
    if (o == ::android::hardware::audio::effect::V6_0::Result::NOT_SUPPORTED) {
        return "NOT_SUPPORTED";
    }
    if (o == ::android::hardware::audio::effect::V6_0::Result::RESULT_TOO_BIG) {
        return "RESULT_TOO_BIG";
    }
    std::string os;
    os += toHexString(static_cast<int32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::audio::effect::V6_0::Result o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::audio::effect::V6_0::EffectFlags>(int32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::audio::effect::V6_0::EffectFlags> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::audio::effect::V6_0::EffectFlags::TYPE_SHIFT) == static_cast<int32_t>(::android::hardware::audio::effect::V6_0::EffectFlags::TYPE_SHIFT)) {
        os += (first ? "" : " | ");
        os += "TYPE_SHIFT";
        first = false;
        flipped |= ::android::hardware::audio::effect::V6_0::EffectFlags::TYPE_SHIFT;
    }
    if ((o & ::android::hardware::audio::effect::V6_0::EffectFlags::TYPE_SIZE) == static_cast<int32_t>(::android::hardware::audio::effect::V6_0::EffectFlags::TYPE_SIZE)) {
        os += (first ? "" : " | ");
        os += "TYPE_SIZE";
        first = false;
        flipped |= ::android::hardware::audio::effect::V6_0::EffectFlags::TYPE_SIZE;
    }
    if ((o & ::android::hardware::audio::effect::V6_0::EffectFlags::TYPE_MASK) == static_cast<int32_t>(::android::hardware::audio::effect::V6_0::EffectFlags::TYPE_MASK)) {
        os += (first ? "" : " | ");
        os += "TYPE_MASK";
        first = false;
        flipped |= ::android::hardware::audio::effect::V6_0::EffectFlags::TYPE_MASK;
    }
    if ((o & ::android::hardware::audio::effect::V6_0::EffectFlags::TYPE_INSERT) == static_cast<int32_t>(::android::hardware::audio::effect::V6_0::EffectFlags::TYPE_INSERT)) {
        os += (first ? "" : " | ");
        os += "TYPE_INSERT";
        first = false;
        flipped |= ::android::hardware::audio::effect::V6_0::EffectFlags::TYPE_INSERT;
    }
    if ((o & ::android::hardware::audio::effect::V6_0::EffectFlags::TYPE_AUXILIARY) == static_cast<int32_t>(::android::hardware::audio::effect::V6_0::EffectFlags::TYPE_AUXILIARY)) {
        os += (first ? "" : " | ");
        os += "TYPE_AUXILIARY";
        first = false;
        flipped |= ::android::hardware::audio::effect::V6_0::EffectFlags::TYPE_AUXILIARY;
    }
    if ((o & ::android::hardware::audio::effect::V6_0::EffectFlags::TYPE_REPLACE) == static_cast<int32_t>(::android::hardware::audio::effect::V6_0::EffectFlags::TYPE_REPLACE)) {
        os += (first ? "" : " | ");
        os += "TYPE_REPLACE";
        first = false;
        flipped |= ::android::hardware::audio::effect::V6_0::EffectFlags::TYPE_REPLACE;
    }
    if ((o & ::android::hardware::audio::effect::V6_0::EffectFlags::TYPE_PRE_PROC) == static_cast<int32_t>(::android::hardware::audio::effect::V6_0::EffectFlags::TYPE_PRE_PROC)) {
        os += (first ? "" : " | ");
        os += "TYPE_PRE_PROC";
        first = false;
        flipped |= ::android::hardware::audio::effect::V6_0::EffectFlags::TYPE_PRE_PROC;
    }
    if ((o & ::android::hardware::audio::effect::V6_0::EffectFlags::TYPE_POST_PROC) == static_cast<int32_t>(::android::hardware::audio::effect::V6_0::EffectFlags::TYPE_POST_PROC)) {
        os += (first ? "" : " | ");
        os += "TYPE_POST_PROC";
        first = false;
        flipped |= ::android::hardware::audio::effect::V6_0::EffectFlags::TYPE_POST_PROC;
    }
    if ((o & ::android::hardware::audio::effect::V6_0::EffectFlags::INSERT_SHIFT) == static_cast<int32_t>(::android::hardware::audio::effect::V6_0::EffectFlags::INSERT_SHIFT)) {
        os += (first ? "" : " | ");
        os += "INSERT_SHIFT";
        first = false;
        flipped |= ::android::hardware::audio::effect::V6_0::EffectFlags::INSERT_SHIFT;
    }
    if ((o & ::android::hardware::audio::effect::V6_0::EffectFlags::INSERT_SIZE) == static_cast<int32_t>(::android::hardware::audio::effect::V6_0::EffectFlags::INSERT_SIZE)) {
        os += (first ? "" : " | ");
        os += "INSERT_SIZE";
        first = false;
        flipped |= ::android::hardware::audio::effect::V6_0::EffectFlags::INSERT_SIZE;
    }
    if ((o & ::android::hardware::audio::effect::V6_0::EffectFlags::INSERT_MASK) == static_cast<int32_t>(::android::hardware::audio::effect::V6_0::EffectFlags::INSERT_MASK)) {
        os += (first ? "" : " | ");
        os += "INSERT_MASK";
        first = false;
        flipped |= ::android::hardware::audio::effect::V6_0::EffectFlags::INSERT_MASK;
    }
    if ((o & ::android::hardware::audio::effect::V6_0::EffectFlags::INSERT_ANY) == static_cast<int32_t>(::android::hardware::audio::effect::V6_0::EffectFlags::INSERT_ANY)) {
        os += (first ? "" : " | ");
        os += "INSERT_ANY";
        first = false;
        flipped |= ::android::hardware::audio::effect::V6_0::EffectFlags::INSERT_ANY;
    }
    if ((o & ::android::hardware::audio::effect::V6_0::EffectFlags::INSERT_FIRST) == static_cast<int32_t>(::android::hardware::audio::effect::V6_0::EffectFlags::INSERT_FIRST)) {
        os += (first ? "" : " | ");
        os += "INSERT_FIRST";
        first = false;
        flipped |= ::android::hardware::audio::effect::V6_0::EffectFlags::INSERT_FIRST;
    }
    if ((o & ::android::hardware::audio::effect::V6_0::EffectFlags::INSERT_LAST) == static_cast<int32_t>(::android::hardware::audio::effect::V6_0::EffectFlags::INSERT_LAST)) {
        os += (first ? "" : " | ");
        os += "INSERT_LAST";
        first = false;
        flipped |= ::android::hardware::audio::effect::V6_0::EffectFlags::INSERT_LAST;
    }
    if ((o & ::android::hardware::audio::effect::V6_0::EffectFlags::INSERT_EXCLUSIVE) == static_cast<int32_t>(::android::hardware::audio::effect::V6_0::EffectFlags::INSERT_EXCLUSIVE)) {
        os += (first ? "" : " | ");
        os += "INSERT_EXCLUSIVE";
        first = false;
        flipped |= ::android::hardware::audio::effect::V6_0::EffectFlags::INSERT_EXCLUSIVE;
    }
    if ((o & ::android::hardware::audio::effect::V6_0::EffectFlags::VOLUME_SHIFT) == static_cast<int32_t>(::android::hardware::audio::effect::V6_0::EffectFlags::VOLUME_SHIFT)) {
        os += (first ? "" : " | ");
        os += "VOLUME_SHIFT";
        first = false;
        flipped |= ::android::hardware::audio::effect::V6_0::EffectFlags::VOLUME_SHIFT;
    }
    if ((o & ::android::hardware::audio::effect::V6_0::EffectFlags::VOLUME_SIZE) == static_cast<int32_t>(::android::hardware::audio::effect::V6_0::EffectFlags::VOLUME_SIZE)) {
        os += (first ? "" : " | ");
        os += "VOLUME_SIZE";
        first = false;
        flipped |= ::android::hardware::audio::effect::V6_0::EffectFlags::VOLUME_SIZE;
    }
    if ((o & ::android::hardware::audio::effect::V6_0::EffectFlags::VOLUME_MASK) == static_cast<int32_t>(::android::hardware::audio::effect::V6_0::EffectFlags::VOLUME_MASK)) {
        os += (first ? "" : " | ");
        os += "VOLUME_MASK";
        first = false;
        flipped |= ::android::hardware::audio::effect::V6_0::EffectFlags::VOLUME_MASK;
    }
    if ((o & ::android::hardware::audio::effect::V6_0::EffectFlags::VOLUME_CTRL) == static_cast<int32_t>(::android::hardware::audio::effect::V6_0::EffectFlags::VOLUME_CTRL)) {
        os += (first ? "" : " | ");
        os += "VOLUME_CTRL";
        first = false;
        flipped |= ::android::hardware::audio::effect::V6_0::EffectFlags::VOLUME_CTRL;
    }
    if ((o & ::android::hardware::audio::effect::V6_0::EffectFlags::VOLUME_IND) == static_cast<int32_t>(::android::hardware::audio::effect::V6_0::EffectFlags::VOLUME_IND)) {
        os += (first ? "" : " | ");
        os += "VOLUME_IND";
        first = false;
        flipped |= ::android::hardware::audio::effect::V6_0::EffectFlags::VOLUME_IND;
    }
    if ((o & ::android::hardware::audio::effect::V6_0::EffectFlags::VOLUME_MONITOR) == static_cast<int32_t>(::android::hardware::audio::effect::V6_0::EffectFlags::VOLUME_MONITOR)) {
        os += (first ? "" : " | ");
        os += "VOLUME_MONITOR";
        first = false;
        flipped |= ::android::hardware::audio::effect::V6_0::EffectFlags::VOLUME_MONITOR;
    }
    if ((o & ::android::hardware::audio::effect::V6_0::EffectFlags::VOLUME_NONE) == static_cast<int32_t>(::android::hardware::audio::effect::V6_0::EffectFlags::VOLUME_NONE)) {
        os += (first ? "" : " | ");
        os += "VOLUME_NONE";
        first = false;
        flipped |= ::android::hardware::audio::effect::V6_0::EffectFlags::VOLUME_NONE;
    }
    if ((o & ::android::hardware::audio::effect::V6_0::EffectFlags::DEVICE_SHIFT) == static_cast<int32_t>(::android::hardware::audio::effect::V6_0::EffectFlags::DEVICE_SHIFT)) {
        os += (first ? "" : " | ");
        os += "DEVICE_SHIFT";
        first = false;
        flipped |= ::android::hardware::audio::effect::V6_0::EffectFlags::DEVICE_SHIFT;
    }
    if ((o & ::android::hardware::audio::effect::V6_0::EffectFlags::DEVICE_SIZE) == static_cast<int32_t>(::android::hardware::audio::effect::V6_0::EffectFlags::DEVICE_SIZE)) {
        os += (first ? "" : " | ");
        os += "DEVICE_SIZE";
        first = false;
        flipped |= ::android::hardware::audio::effect::V6_0::EffectFlags::DEVICE_SIZE;
    }
    if ((o & ::android::hardware::audio::effect::V6_0::EffectFlags::DEVICE_MASK) == static_cast<int32_t>(::android::hardware::audio::effect::V6_0::EffectFlags::DEVICE_MASK)) {
        os += (first ? "" : " | ");
        os += "DEVICE_MASK";
        first = false;
        flipped |= ::android::hardware::audio::effect::V6_0::EffectFlags::DEVICE_MASK;
    }
    if ((o & ::android::hardware::audio::effect::V6_0::EffectFlags::DEVICE_IND) == static_cast<int32_t>(::android::hardware::audio::effect::V6_0::EffectFlags::DEVICE_IND)) {
        os += (first ? "" : " | ");
        os += "DEVICE_IND";
        first = false;
        flipped |= ::android::hardware::audio::effect::V6_0::EffectFlags::DEVICE_IND;
    }
    if ((o & ::android::hardware::audio::effect::V6_0::EffectFlags::DEVICE_NONE) == static_cast<int32_t>(::android::hardware::audio::effect::V6_0::EffectFlags::DEVICE_NONE)) {
        os += (first ? "" : " | ");
        os += "DEVICE_NONE";
        first = false;
        flipped |= ::android::hardware::audio::effect::V6_0::EffectFlags::DEVICE_NONE;
    }
    if ((o & ::android::hardware::audio::effect::V6_0::EffectFlags::INPUT_SHIFT) == static_cast<int32_t>(::android::hardware::audio::effect::V6_0::EffectFlags::INPUT_SHIFT)) {
        os += (first ? "" : " | ");
        os += "INPUT_SHIFT";
        first = false;
        flipped |= ::android::hardware::audio::effect::V6_0::EffectFlags::INPUT_SHIFT;
    }
    if ((o & ::android::hardware::audio::effect::V6_0::EffectFlags::INPUT_SIZE) == static_cast<int32_t>(::android::hardware::audio::effect::V6_0::EffectFlags::INPUT_SIZE)) {
        os += (first ? "" : " | ");
        os += "INPUT_SIZE";
        first = false;
        flipped |= ::android::hardware::audio::effect::V6_0::EffectFlags::INPUT_SIZE;
    }
    if ((o & ::android::hardware::audio::effect::V6_0::EffectFlags::INPUT_MASK) == static_cast<int32_t>(::android::hardware::audio::effect::V6_0::EffectFlags::INPUT_MASK)) {
        os += (first ? "" : " | ");
        os += "INPUT_MASK";
        first = false;
        flipped |= ::android::hardware::audio::effect::V6_0::EffectFlags::INPUT_MASK;
    }
    if ((o & ::android::hardware::audio::effect::V6_0::EffectFlags::INPUT_DIRECT) == static_cast<int32_t>(::android::hardware::audio::effect::V6_0::EffectFlags::INPUT_DIRECT)) {
        os += (first ? "" : " | ");
        os += "INPUT_DIRECT";
        first = false;
        flipped |= ::android::hardware::audio::effect::V6_0::EffectFlags::INPUT_DIRECT;
    }
    if ((o & ::android::hardware::audio::effect::V6_0::EffectFlags::INPUT_PROVIDER) == static_cast<int32_t>(::android::hardware::audio::effect::V6_0::EffectFlags::INPUT_PROVIDER)) {
        os += (first ? "" : " | ");
        os += "INPUT_PROVIDER";
        first = false;
        flipped |= ::android::hardware::audio::effect::V6_0::EffectFlags::INPUT_PROVIDER;
    }
    if ((o & ::android::hardware::audio::effect::V6_0::EffectFlags::INPUT_BOTH) == static_cast<int32_t>(::android::hardware::audio::effect::V6_0::EffectFlags::INPUT_BOTH)) {
        os += (first ? "" : " | ");
        os += "INPUT_BOTH";
        first = false;
        flipped |= ::android::hardware::audio::effect::V6_0::EffectFlags::INPUT_BOTH;
    }
    if ((o & ::android::hardware::audio::effect::V6_0::EffectFlags::OUTPUT_SHIFT) == static_cast<int32_t>(::android::hardware::audio::effect::V6_0::EffectFlags::OUTPUT_SHIFT)) {
        os += (first ? "" : " | ");
        os += "OUTPUT_SHIFT";
        first = false;
        flipped |= ::android::hardware::audio::effect::V6_0::EffectFlags::OUTPUT_SHIFT;
    }
    if ((o & ::android::hardware::audio::effect::V6_0::EffectFlags::OUTPUT_SIZE) == static_cast<int32_t>(::android::hardware::audio::effect::V6_0::EffectFlags::OUTPUT_SIZE)) {
        os += (first ? "" : " | ");
        os += "OUTPUT_SIZE";
        first = false;
        flipped |= ::android::hardware::audio::effect::V6_0::EffectFlags::OUTPUT_SIZE;
    }
    if ((o & ::android::hardware::audio::effect::V6_0::EffectFlags::OUTPUT_MASK) == static_cast<int32_t>(::android::hardware::audio::effect::V6_0::EffectFlags::OUTPUT_MASK)) {
        os += (first ? "" : " | ");
        os += "OUTPUT_MASK";
        first = false;
        flipped |= ::android::hardware::audio::effect::V6_0::EffectFlags::OUTPUT_MASK;
    }
    if ((o & ::android::hardware::audio::effect::V6_0::EffectFlags::OUTPUT_DIRECT) == static_cast<int32_t>(::android::hardware::audio::effect::V6_0::EffectFlags::OUTPUT_DIRECT)) {
        os += (first ? "" : " | ");
        os += "OUTPUT_DIRECT";
        first = false;
        flipped |= ::android::hardware::audio::effect::V6_0::EffectFlags::OUTPUT_DIRECT;
    }
    if ((o & ::android::hardware::audio::effect::V6_0::EffectFlags::OUTPUT_PROVIDER) == static_cast<int32_t>(::android::hardware::audio::effect::V6_0::EffectFlags::OUTPUT_PROVIDER)) {
        os += (first ? "" : " | ");
        os += "OUTPUT_PROVIDER";
        first = false;
        flipped |= ::android::hardware::audio::effect::V6_0::EffectFlags::OUTPUT_PROVIDER;
    }
    if ((o & ::android::hardware::audio::effect::V6_0::EffectFlags::OUTPUT_BOTH) == static_cast<int32_t>(::android::hardware::audio::effect::V6_0::EffectFlags::OUTPUT_BOTH)) {
        os += (first ? "" : " | ");
        os += "OUTPUT_BOTH";
        first = false;
        flipped |= ::android::hardware::audio::effect::V6_0::EffectFlags::OUTPUT_BOTH;
    }
    if ((o & ::android::hardware::audio::effect::V6_0::EffectFlags::HW_ACC_SHIFT) == static_cast<int32_t>(::android::hardware::audio::effect::V6_0::EffectFlags::HW_ACC_SHIFT)) {
        os += (first ? "" : " | ");
        os += "HW_ACC_SHIFT";
        first = false;
        flipped |= ::android::hardware::audio::effect::V6_0::EffectFlags::HW_ACC_SHIFT;
    }
    if ((o & ::android::hardware::audio::effect::V6_0::EffectFlags::HW_ACC_SIZE) == static_cast<int32_t>(::android::hardware::audio::effect::V6_0::EffectFlags::HW_ACC_SIZE)) {
        os += (first ? "" : " | ");
        os += "HW_ACC_SIZE";
        first = false;
        flipped |= ::android::hardware::audio::effect::V6_0::EffectFlags::HW_ACC_SIZE;
    }
    if ((o & ::android::hardware::audio::effect::V6_0::EffectFlags::HW_ACC_MASK) == static_cast<int32_t>(::android::hardware::audio::effect::V6_0::EffectFlags::HW_ACC_MASK)) {
        os += (first ? "" : " | ");
        os += "HW_ACC_MASK";
        first = false;
        flipped |= ::android::hardware::audio::effect::V6_0::EffectFlags::HW_ACC_MASK;
    }
    if ((o & ::android::hardware::audio::effect::V6_0::EffectFlags::HW_ACC_SIMPLE) == static_cast<int32_t>(::android::hardware::audio::effect::V6_0::EffectFlags::HW_ACC_SIMPLE)) {
        os += (first ? "" : " | ");
        os += "HW_ACC_SIMPLE";
        first = false;
        flipped |= ::android::hardware::audio::effect::V6_0::EffectFlags::HW_ACC_SIMPLE;
    }
    if ((o & ::android::hardware::audio::effect::V6_0::EffectFlags::HW_ACC_TUNNEL) == static_cast<int32_t>(::android::hardware::audio::effect::V6_0::EffectFlags::HW_ACC_TUNNEL)) {
        os += (first ? "" : " | ");
        os += "HW_ACC_TUNNEL";
        first = false;
        flipped |= ::android::hardware::audio::effect::V6_0::EffectFlags::HW_ACC_TUNNEL;
    }
    if ((o & ::android::hardware::audio::effect::V6_0::EffectFlags::AUDIO_MODE_SHIFT) == static_cast<int32_t>(::android::hardware::audio::effect::V6_0::EffectFlags::AUDIO_MODE_SHIFT)) {
        os += (first ? "" : " | ");
        os += "AUDIO_MODE_SHIFT";
        first = false;
        flipped |= ::android::hardware::audio::effect::V6_0::EffectFlags::AUDIO_MODE_SHIFT;
    }
    if ((o & ::android::hardware::audio::effect::V6_0::EffectFlags::AUDIO_MODE_SIZE) == static_cast<int32_t>(::android::hardware::audio::effect::V6_0::EffectFlags::AUDIO_MODE_SIZE)) {
        os += (first ? "" : " | ");
        os += "AUDIO_MODE_SIZE";
        first = false;
        flipped |= ::android::hardware::audio::effect::V6_0::EffectFlags::AUDIO_MODE_SIZE;
    }
    if ((o & ::android::hardware::audio::effect::V6_0::EffectFlags::AUDIO_MODE_MASK) == static_cast<int32_t>(::android::hardware::audio::effect::V6_0::EffectFlags::AUDIO_MODE_MASK)) {
        os += (first ? "" : " | ");
        os += "AUDIO_MODE_MASK";
        first = false;
        flipped |= ::android::hardware::audio::effect::V6_0::EffectFlags::AUDIO_MODE_MASK;
    }
    if ((o & ::android::hardware::audio::effect::V6_0::EffectFlags::AUDIO_MODE_IND) == static_cast<int32_t>(::android::hardware::audio::effect::V6_0::EffectFlags::AUDIO_MODE_IND)) {
        os += (first ? "" : " | ");
        os += "AUDIO_MODE_IND";
        first = false;
        flipped |= ::android::hardware::audio::effect::V6_0::EffectFlags::AUDIO_MODE_IND;
    }
    if ((o & ::android::hardware::audio::effect::V6_0::EffectFlags::AUDIO_MODE_NONE) == static_cast<int32_t>(::android::hardware::audio::effect::V6_0::EffectFlags::AUDIO_MODE_NONE)) {
        os += (first ? "" : " | ");
        os += "AUDIO_MODE_NONE";
        first = false;
        flipped |= ::android::hardware::audio::effect::V6_0::EffectFlags::AUDIO_MODE_NONE;
    }
    if ((o & ::android::hardware::audio::effect::V6_0::EffectFlags::AUDIO_SOURCE_SHIFT) == static_cast<int32_t>(::android::hardware::audio::effect::V6_0::EffectFlags::AUDIO_SOURCE_SHIFT)) {
        os += (first ? "" : " | ");
        os += "AUDIO_SOURCE_SHIFT";
        first = false;
        flipped |= ::android::hardware::audio::effect::V6_0::EffectFlags::AUDIO_SOURCE_SHIFT;
    }
    if ((o & ::android::hardware::audio::effect::V6_0::EffectFlags::AUDIO_SOURCE_SIZE) == static_cast<int32_t>(::android::hardware::audio::effect::V6_0::EffectFlags::AUDIO_SOURCE_SIZE)) {
        os += (first ? "" : " | ");
        os += "AUDIO_SOURCE_SIZE";
        first = false;
        flipped |= ::android::hardware::audio::effect::V6_0::EffectFlags::AUDIO_SOURCE_SIZE;
    }
    if ((o & ::android::hardware::audio::effect::V6_0::EffectFlags::AUDIO_SOURCE_MASK) == static_cast<int32_t>(::android::hardware::audio::effect::V6_0::EffectFlags::AUDIO_SOURCE_MASK)) {
        os += (first ? "" : " | ");
        os += "AUDIO_SOURCE_MASK";
        first = false;
        flipped |= ::android::hardware::audio::effect::V6_0::EffectFlags::AUDIO_SOURCE_MASK;
    }
    if ((o & ::android::hardware::audio::effect::V6_0::EffectFlags::AUDIO_SOURCE_IND) == static_cast<int32_t>(::android::hardware::audio::effect::V6_0::EffectFlags::AUDIO_SOURCE_IND)) {
        os += (first ? "" : " | ");
        os += "AUDIO_SOURCE_IND";
        first = false;
        flipped |= ::android::hardware::audio::effect::V6_0::EffectFlags::AUDIO_SOURCE_IND;
    }
    if ((o & ::android::hardware::audio::effect::V6_0::EffectFlags::AUDIO_SOURCE_NONE) == static_cast<int32_t>(::android::hardware::audio::effect::V6_0::EffectFlags::AUDIO_SOURCE_NONE)) {
        os += (first ? "" : " | ");
        os += "AUDIO_SOURCE_NONE";
        first = false;
        flipped |= ::android::hardware::audio::effect::V6_0::EffectFlags::AUDIO_SOURCE_NONE;
    }
    if ((o & ::android::hardware::audio::effect::V6_0::EffectFlags::OFFLOAD_SHIFT) == static_cast<int32_t>(::android::hardware::audio::effect::V6_0::EffectFlags::OFFLOAD_SHIFT)) {
        os += (first ? "" : " | ");
        os += "OFFLOAD_SHIFT";
        first = false;
        flipped |= ::android::hardware::audio::effect::V6_0::EffectFlags::OFFLOAD_SHIFT;
    }
    if ((o & ::android::hardware::audio::effect::V6_0::EffectFlags::OFFLOAD_SIZE) == static_cast<int32_t>(::android::hardware::audio::effect::V6_0::EffectFlags::OFFLOAD_SIZE)) {
        os += (first ? "" : " | ");
        os += "OFFLOAD_SIZE";
        first = false;
        flipped |= ::android::hardware::audio::effect::V6_0::EffectFlags::OFFLOAD_SIZE;
    }
    if ((o & ::android::hardware::audio::effect::V6_0::EffectFlags::OFFLOAD_MASK) == static_cast<int32_t>(::android::hardware::audio::effect::V6_0::EffectFlags::OFFLOAD_MASK)) {
        os += (first ? "" : " | ");
        os += "OFFLOAD_MASK";
        first = false;
        flipped |= ::android::hardware::audio::effect::V6_0::EffectFlags::OFFLOAD_MASK;
    }
    if ((o & ::android::hardware::audio::effect::V6_0::EffectFlags::OFFLOAD_SUPPORTED) == static_cast<int32_t>(::android::hardware::audio::effect::V6_0::EffectFlags::OFFLOAD_SUPPORTED)) {
        os += (first ? "" : " | ");
        os += "OFFLOAD_SUPPORTED";
        first = false;
        flipped |= ::android::hardware::audio::effect::V6_0::EffectFlags::OFFLOAD_SUPPORTED;
    }
    if ((o & ::android::hardware::audio::effect::V6_0::EffectFlags::NO_PROCESS_SHIFT) == static_cast<int32_t>(::android::hardware::audio::effect::V6_0::EffectFlags::NO_PROCESS_SHIFT)) {
        os += (first ? "" : " | ");
        os += "NO_PROCESS_SHIFT";
        first = false;
        flipped |= ::android::hardware::audio::effect::V6_0::EffectFlags::NO_PROCESS_SHIFT;
    }
    if ((o & ::android::hardware::audio::effect::V6_0::EffectFlags::NO_PROCESS_SIZE) == static_cast<int32_t>(::android::hardware::audio::effect::V6_0::EffectFlags::NO_PROCESS_SIZE)) {
        os += (first ? "" : " | ");
        os += "NO_PROCESS_SIZE";
        first = false;
        flipped |= ::android::hardware::audio::effect::V6_0::EffectFlags::NO_PROCESS_SIZE;
    }
    if ((o & ::android::hardware::audio::effect::V6_0::EffectFlags::NO_PROCESS_MASK) == static_cast<int32_t>(::android::hardware::audio::effect::V6_0::EffectFlags::NO_PROCESS_MASK)) {
        os += (first ? "" : " | ");
        os += "NO_PROCESS_MASK";
        first = false;
        flipped |= ::android::hardware::audio::effect::V6_0::EffectFlags::NO_PROCESS_MASK;
    }
    if ((o & ::android::hardware::audio::effect::V6_0::EffectFlags::NO_PROCESS) == static_cast<int32_t>(::android::hardware::audio::effect::V6_0::EffectFlags::NO_PROCESS)) {
        os += (first ? "" : " | ");
        os += "NO_PROCESS";
        first = false;
        flipped |= ::android::hardware::audio::effect::V6_0::EffectFlags::NO_PROCESS;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::audio::effect::V6_0::EffectFlags o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::audio::effect::V6_0::EffectFlags::TYPE_SHIFT) {
        return "TYPE_SHIFT";
    }
    if (o == ::android::hardware::audio::effect::V6_0::EffectFlags::TYPE_SIZE) {
        return "TYPE_SIZE";
    }
    if (o == ::android::hardware::audio::effect::V6_0::EffectFlags::TYPE_MASK) {
        return "TYPE_MASK";
    }
    if (o == ::android::hardware::audio::effect::V6_0::EffectFlags::TYPE_INSERT) {
        return "TYPE_INSERT";
    }
    if (o == ::android::hardware::audio::effect::V6_0::EffectFlags::TYPE_AUXILIARY) {
        return "TYPE_AUXILIARY";
    }
    if (o == ::android::hardware::audio::effect::V6_0::EffectFlags::TYPE_REPLACE) {
        return "TYPE_REPLACE";
    }
    if (o == ::android::hardware::audio::effect::V6_0::EffectFlags::TYPE_PRE_PROC) {
        return "TYPE_PRE_PROC";
    }
    if (o == ::android::hardware::audio::effect::V6_0::EffectFlags::TYPE_POST_PROC) {
        return "TYPE_POST_PROC";
    }
    if (o == ::android::hardware::audio::effect::V6_0::EffectFlags::INSERT_SHIFT) {
        return "INSERT_SHIFT";
    }
    if (o == ::android::hardware::audio::effect::V6_0::EffectFlags::INSERT_SIZE) {
        return "INSERT_SIZE";
    }
    if (o == ::android::hardware::audio::effect::V6_0::EffectFlags::INSERT_MASK) {
        return "INSERT_MASK";
    }
    if (o == ::android::hardware::audio::effect::V6_0::EffectFlags::INSERT_ANY) {
        return "INSERT_ANY";
    }
    if (o == ::android::hardware::audio::effect::V6_0::EffectFlags::INSERT_FIRST) {
        return "INSERT_FIRST";
    }
    if (o == ::android::hardware::audio::effect::V6_0::EffectFlags::INSERT_LAST) {
        return "INSERT_LAST";
    }
    if (o == ::android::hardware::audio::effect::V6_0::EffectFlags::INSERT_EXCLUSIVE) {
        return "INSERT_EXCLUSIVE";
    }
    if (o == ::android::hardware::audio::effect::V6_0::EffectFlags::VOLUME_SHIFT) {
        return "VOLUME_SHIFT";
    }
    if (o == ::android::hardware::audio::effect::V6_0::EffectFlags::VOLUME_SIZE) {
        return "VOLUME_SIZE";
    }
    if (o == ::android::hardware::audio::effect::V6_0::EffectFlags::VOLUME_MASK) {
        return "VOLUME_MASK";
    }
    if (o == ::android::hardware::audio::effect::V6_0::EffectFlags::VOLUME_CTRL) {
        return "VOLUME_CTRL";
    }
    if (o == ::android::hardware::audio::effect::V6_0::EffectFlags::VOLUME_IND) {
        return "VOLUME_IND";
    }
    if (o == ::android::hardware::audio::effect::V6_0::EffectFlags::VOLUME_MONITOR) {
        return "VOLUME_MONITOR";
    }
    if (o == ::android::hardware::audio::effect::V6_0::EffectFlags::VOLUME_NONE) {
        return "VOLUME_NONE";
    }
    if (o == ::android::hardware::audio::effect::V6_0::EffectFlags::DEVICE_SHIFT) {
        return "DEVICE_SHIFT";
    }
    if (o == ::android::hardware::audio::effect::V6_0::EffectFlags::DEVICE_SIZE) {
        return "DEVICE_SIZE";
    }
    if (o == ::android::hardware::audio::effect::V6_0::EffectFlags::DEVICE_MASK) {
        return "DEVICE_MASK";
    }
    if (o == ::android::hardware::audio::effect::V6_0::EffectFlags::DEVICE_IND) {
        return "DEVICE_IND";
    }
    if (o == ::android::hardware::audio::effect::V6_0::EffectFlags::DEVICE_NONE) {
        return "DEVICE_NONE";
    }
    if (o == ::android::hardware::audio::effect::V6_0::EffectFlags::INPUT_SHIFT) {
        return "INPUT_SHIFT";
    }
    if (o == ::android::hardware::audio::effect::V6_0::EffectFlags::INPUT_SIZE) {
        return "INPUT_SIZE";
    }
    if (o == ::android::hardware::audio::effect::V6_0::EffectFlags::INPUT_MASK) {
        return "INPUT_MASK";
    }
    if (o == ::android::hardware::audio::effect::V6_0::EffectFlags::INPUT_DIRECT) {
        return "INPUT_DIRECT";
    }
    if (o == ::android::hardware::audio::effect::V6_0::EffectFlags::INPUT_PROVIDER) {
        return "INPUT_PROVIDER";
    }
    if (o == ::android::hardware::audio::effect::V6_0::EffectFlags::INPUT_BOTH) {
        return "INPUT_BOTH";
    }
    if (o == ::android::hardware::audio::effect::V6_0::EffectFlags::OUTPUT_SHIFT) {
        return "OUTPUT_SHIFT";
    }
    if (o == ::android::hardware::audio::effect::V6_0::EffectFlags::OUTPUT_SIZE) {
        return "OUTPUT_SIZE";
    }
    if (o == ::android::hardware::audio::effect::V6_0::EffectFlags::OUTPUT_MASK) {
        return "OUTPUT_MASK";
    }
    if (o == ::android::hardware::audio::effect::V6_0::EffectFlags::OUTPUT_DIRECT) {
        return "OUTPUT_DIRECT";
    }
    if (o == ::android::hardware::audio::effect::V6_0::EffectFlags::OUTPUT_PROVIDER) {
        return "OUTPUT_PROVIDER";
    }
    if (o == ::android::hardware::audio::effect::V6_0::EffectFlags::OUTPUT_BOTH) {
        return "OUTPUT_BOTH";
    }
    if (o == ::android::hardware::audio::effect::V6_0::EffectFlags::HW_ACC_SHIFT) {
        return "HW_ACC_SHIFT";
    }
    if (o == ::android::hardware::audio::effect::V6_0::EffectFlags::HW_ACC_SIZE) {
        return "HW_ACC_SIZE";
    }
    if (o == ::android::hardware::audio::effect::V6_0::EffectFlags::HW_ACC_MASK) {
        return "HW_ACC_MASK";
    }
    if (o == ::android::hardware::audio::effect::V6_0::EffectFlags::HW_ACC_SIMPLE) {
        return "HW_ACC_SIMPLE";
    }
    if (o == ::android::hardware::audio::effect::V6_0::EffectFlags::HW_ACC_TUNNEL) {
        return "HW_ACC_TUNNEL";
    }
    if (o == ::android::hardware::audio::effect::V6_0::EffectFlags::AUDIO_MODE_SHIFT) {
        return "AUDIO_MODE_SHIFT";
    }
    if (o == ::android::hardware::audio::effect::V6_0::EffectFlags::AUDIO_MODE_SIZE) {
        return "AUDIO_MODE_SIZE";
    }
    if (o == ::android::hardware::audio::effect::V6_0::EffectFlags::AUDIO_MODE_MASK) {
        return "AUDIO_MODE_MASK";
    }
    if (o == ::android::hardware::audio::effect::V6_0::EffectFlags::AUDIO_MODE_IND) {
        return "AUDIO_MODE_IND";
    }
    if (o == ::android::hardware::audio::effect::V6_0::EffectFlags::AUDIO_MODE_NONE) {
        return "AUDIO_MODE_NONE";
    }
    if (o == ::android::hardware::audio::effect::V6_0::EffectFlags::AUDIO_SOURCE_SHIFT) {
        return "AUDIO_SOURCE_SHIFT";
    }
    if (o == ::android::hardware::audio::effect::V6_0::EffectFlags::AUDIO_SOURCE_SIZE) {
        return "AUDIO_SOURCE_SIZE";
    }
    if (o == ::android::hardware::audio::effect::V6_0::EffectFlags::AUDIO_SOURCE_MASK) {
        return "AUDIO_SOURCE_MASK";
    }
    if (o == ::android::hardware::audio::effect::V6_0::EffectFlags::AUDIO_SOURCE_IND) {
        return "AUDIO_SOURCE_IND";
    }
    if (o == ::android::hardware::audio::effect::V6_0::EffectFlags::AUDIO_SOURCE_NONE) {
        return "AUDIO_SOURCE_NONE";
    }
    if (o == ::android::hardware::audio::effect::V6_0::EffectFlags::OFFLOAD_SHIFT) {
        return "OFFLOAD_SHIFT";
    }
    if (o == ::android::hardware::audio::effect::V6_0::EffectFlags::OFFLOAD_SIZE) {
        return "OFFLOAD_SIZE";
    }
    if (o == ::android::hardware::audio::effect::V6_0::EffectFlags::OFFLOAD_MASK) {
        return "OFFLOAD_MASK";
    }
    if (o == ::android::hardware::audio::effect::V6_0::EffectFlags::OFFLOAD_SUPPORTED) {
        return "OFFLOAD_SUPPORTED";
    }
    if (o == ::android::hardware::audio::effect::V6_0::EffectFlags::NO_PROCESS_SHIFT) {
        return "NO_PROCESS_SHIFT";
    }
    if (o == ::android::hardware::audio::effect::V6_0::EffectFlags::NO_PROCESS_SIZE) {
        return "NO_PROCESS_SIZE";
    }
    if (o == ::android::hardware::audio::effect::V6_0::EffectFlags::NO_PROCESS_MASK) {
        return "NO_PROCESS_MASK";
    }
    if (o == ::android::hardware::audio::effect::V6_0::EffectFlags::NO_PROCESS) {
        return "NO_PROCESS";
    }
    std::string os;
    os += toHexString(static_cast<int32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::audio::effect::V6_0::EffectFlags o, ::std::ostream* os) {
    *os << toString(o);
}

static inline std::string toString(const ::android::hardware::audio::effect::V6_0::EffectDescriptor& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".type = ";
    os += ::android::hardware::audio::common::V6_0::toString(o.type);
    os += ", .uuid = ";
    os += ::android::hardware::audio::common::V6_0::toString(o.uuid);
    os += ", .flags = ";
    os += ::android::hardware::audio::effect::V6_0::toString<::android::hardware::audio::effect::V6_0::EffectFlags>(o.flags);
    os += ", .cpuLoad = ";
    os += ::android::hardware::toString(o.cpuLoad);
    os += ", .memoryUsage = ";
    os += ::android::hardware::toString(o.memoryUsage);
    os += ", .name = ";
    os += ::android::hardware::toString(o.name);
    os += ", .implementor = ";
    os += ::android::hardware::toString(o.implementor);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::audio::effect::V6_0::EffectDescriptor& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::audio::effect::V6_0::EffectDescriptor& lhs, const ::android::hardware::audio::effect::V6_0::EffectDescriptor& rhs) {
    if (lhs.type != rhs.type) {
        return false;
    }
    if (lhs.uuid != rhs.uuid) {
        return false;
    }
    if (lhs.flags != rhs.flags) {
        return false;
    }
    if (lhs.cpuLoad != rhs.cpuLoad) {
        return false;
    }
    if (lhs.memoryUsage != rhs.memoryUsage) {
        return false;
    }
    if (lhs.name != rhs.name) {
        return false;
    }
    if (lhs.implementor != rhs.implementor) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::audio::effect::V6_0::EffectDescriptor& lhs, const ::android::hardware::audio::effect::V6_0::EffectDescriptor& rhs){
    return !(lhs == rhs);
}

static inline std::string toString(const ::android::hardware::audio::effect::V6_0::AudioBuffer& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".id = ";
    os += ::android::hardware::toString(o.id);
    os += ", .frameCount = ";
    os += ::android::hardware::toString(o.frameCount);
    os += ", .data = ";
    os += ::android::hardware::toString(o.data);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::audio::effect::V6_0::AudioBuffer& o, ::std::ostream* os) {
    *os << toString(o);
}

// operator== and operator!= are not generated for AudioBuffer

template<>
inline std::string toString<::android::hardware::audio::effect::V6_0::EffectBufferAccess>(int32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::audio::effect::V6_0::EffectBufferAccess> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::audio::effect::V6_0::EffectBufferAccess::ACCESS_WRITE) == static_cast<int32_t>(::android::hardware::audio::effect::V6_0::EffectBufferAccess::ACCESS_WRITE)) {
        os += (first ? "" : " | ");
        os += "ACCESS_WRITE";
        first = false;
        flipped |= ::android::hardware::audio::effect::V6_0::EffectBufferAccess::ACCESS_WRITE;
    }
    if ((o & ::android::hardware::audio::effect::V6_0::EffectBufferAccess::ACCESS_READ) == static_cast<int32_t>(::android::hardware::audio::effect::V6_0::EffectBufferAccess::ACCESS_READ)) {
        os += (first ? "" : " | ");
        os += "ACCESS_READ";
        first = false;
        flipped |= ::android::hardware::audio::effect::V6_0::EffectBufferAccess::ACCESS_READ;
    }
    if ((o & ::android::hardware::audio::effect::V6_0::EffectBufferAccess::ACCESS_ACCUMULATE) == static_cast<int32_t>(::android::hardware::audio::effect::V6_0::EffectBufferAccess::ACCESS_ACCUMULATE)) {
        os += (first ? "" : " | ");
        os += "ACCESS_ACCUMULATE";
        first = false;
        flipped |= ::android::hardware::audio::effect::V6_0::EffectBufferAccess::ACCESS_ACCUMULATE;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::audio::effect::V6_0::EffectBufferAccess o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::audio::effect::V6_0::EffectBufferAccess::ACCESS_WRITE) {
        return "ACCESS_WRITE";
    }
    if (o == ::android::hardware::audio::effect::V6_0::EffectBufferAccess::ACCESS_READ) {
        return "ACCESS_READ";
    }
    if (o == ::android::hardware::audio::effect::V6_0::EffectBufferAccess::ACCESS_ACCUMULATE) {
        return "ACCESS_ACCUMULATE";
    }
    std::string os;
    os += toHexString(static_cast<int32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::audio::effect::V6_0::EffectBufferAccess o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::audio::effect::V6_0::EffectConfigParameters>(int32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::audio::effect::V6_0::EffectConfigParameters> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::audio::effect::V6_0::EffectConfigParameters::BUFFER) == static_cast<int32_t>(::android::hardware::audio::effect::V6_0::EffectConfigParameters::BUFFER)) {
        os += (first ? "" : " | ");
        os += "BUFFER";
        first = false;
        flipped |= ::android::hardware::audio::effect::V6_0::EffectConfigParameters::BUFFER;
    }
    if ((o & ::android::hardware::audio::effect::V6_0::EffectConfigParameters::SMP_RATE) == static_cast<int32_t>(::android::hardware::audio::effect::V6_0::EffectConfigParameters::SMP_RATE)) {
        os += (first ? "" : " | ");
        os += "SMP_RATE";
        first = false;
        flipped |= ::android::hardware::audio::effect::V6_0::EffectConfigParameters::SMP_RATE;
    }
    if ((o & ::android::hardware::audio::effect::V6_0::EffectConfigParameters::CHANNELS) == static_cast<int32_t>(::android::hardware::audio::effect::V6_0::EffectConfigParameters::CHANNELS)) {
        os += (first ? "" : " | ");
        os += "CHANNELS";
        first = false;
        flipped |= ::android::hardware::audio::effect::V6_0::EffectConfigParameters::CHANNELS;
    }
    if ((o & ::android::hardware::audio::effect::V6_0::EffectConfigParameters::FORMAT) == static_cast<int32_t>(::android::hardware::audio::effect::V6_0::EffectConfigParameters::FORMAT)) {
        os += (first ? "" : " | ");
        os += "FORMAT";
        first = false;
        flipped |= ::android::hardware::audio::effect::V6_0::EffectConfigParameters::FORMAT;
    }
    if ((o & ::android::hardware::audio::effect::V6_0::EffectConfigParameters::ACC_MODE) == static_cast<int32_t>(::android::hardware::audio::effect::V6_0::EffectConfigParameters::ACC_MODE)) {
        os += (first ? "" : " | ");
        os += "ACC_MODE";
        first = false;
        flipped |= ::android::hardware::audio::effect::V6_0::EffectConfigParameters::ACC_MODE;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::audio::effect::V6_0::EffectConfigParameters o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::audio::effect::V6_0::EffectConfigParameters::BUFFER) {
        return "BUFFER";
    }
    if (o == ::android::hardware::audio::effect::V6_0::EffectConfigParameters::SMP_RATE) {
        return "SMP_RATE";
    }
    if (o == ::android::hardware::audio::effect::V6_0::EffectConfigParameters::CHANNELS) {
        return "CHANNELS";
    }
    if (o == ::android::hardware::audio::effect::V6_0::EffectConfigParameters::FORMAT) {
        return "FORMAT";
    }
    if (o == ::android::hardware::audio::effect::V6_0::EffectConfigParameters::ACC_MODE) {
        return "ACC_MODE";
    }
    std::string os;
    os += toHexString(static_cast<int32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::audio::effect::V6_0::EffectConfigParameters o, ::std::ostream* os) {
    *os << toString(o);
}

static inline std::string toString(const ::android::hardware::audio::effect::V6_0::EffectBufferConfig& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".buffer = ";
    os += ::android::hardware::audio::effect::V6_0::toString(o.buffer);
    os += ", .samplingRateHz = ";
    os += ::android::hardware::toString(o.samplingRateHz);
    os += ", .channels = ";
    os += ::android::hardware::audio::common::V6_0::toString<::android::hardware::audio::common::V6_0::AudioChannelMask>(o.channels);
    os += ", .format = ";
    os += ::android::hardware::audio::common::V6_0::toString(o.format);
    os += ", .accessMode = ";
    os += ::android::hardware::audio::effect::V6_0::toString(o.accessMode);
    os += ", .mask = ";
    os += ::android::hardware::audio::effect::V6_0::toString<::android::hardware::audio::effect::V6_0::EffectConfigParameters>(o.mask);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::audio::effect::V6_0::EffectBufferConfig& o, ::std::ostream* os) {
    *os << toString(o);
}

// operator== and operator!= are not generated for EffectBufferConfig

static inline std::string toString(const ::android::hardware::audio::effect::V6_0::EffectConfig& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".inputCfg = ";
    os += ::android::hardware::audio::effect::V6_0::toString(o.inputCfg);
    os += ", .outputCfg = ";
    os += ::android::hardware::audio::effect::V6_0::toString(o.outputCfg);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::audio::effect::V6_0::EffectConfig& o, ::std::ostream* os) {
    *os << toString(o);
}

// operator== and operator!= are not generated for EffectConfig

template<>
inline std::string toString<::android::hardware::audio::effect::V6_0::EffectFeature>(int32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::audio::effect::V6_0::EffectFeature> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::audio::effect::V6_0::EffectFeature::AUX_CHANNELS) == static_cast<int32_t>(::android::hardware::audio::effect::V6_0::EffectFeature::AUX_CHANNELS)) {
        os += (first ? "" : " | ");
        os += "AUX_CHANNELS";
        first = false;
        flipped |= ::android::hardware::audio::effect::V6_0::EffectFeature::AUX_CHANNELS;
    }
    if ((o & ::android::hardware::audio::effect::V6_0::EffectFeature::CNT) == static_cast<int32_t>(::android::hardware::audio::effect::V6_0::EffectFeature::CNT)) {
        os += (first ? "" : " | ");
        os += "CNT";
        first = false;
        flipped |= ::android::hardware::audio::effect::V6_0::EffectFeature::CNT;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::audio::effect::V6_0::EffectFeature o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::audio::effect::V6_0::EffectFeature::AUX_CHANNELS) {
        return "AUX_CHANNELS";
    }
    if (o == ::android::hardware::audio::effect::V6_0::EffectFeature::CNT) {
        return "CNT";
    }
    std::string os;
    os += toHexString(static_cast<int32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::audio::effect::V6_0::EffectFeature o, ::std::ostream* os) {
    *os << toString(o);
}

static inline std::string toString(const ::android::hardware::audio::effect::V6_0::EffectAuxChannelsConfig& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".mainChannels = ";
    os += ::android::hardware::audio::common::V6_0::toString<::android::hardware::audio::common::V6_0::AudioChannelMask>(o.mainChannels);
    os += ", .auxChannels = ";
    os += ::android::hardware::audio::common::V6_0::toString<::android::hardware::audio::common::V6_0::AudioChannelMask>(o.auxChannels);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::audio::effect::V6_0::EffectAuxChannelsConfig& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::audio::effect::V6_0::EffectAuxChannelsConfig& lhs, const ::android::hardware::audio::effect::V6_0::EffectAuxChannelsConfig& rhs) {
    if (lhs.mainChannels != rhs.mainChannels) {
        return false;
    }
    if (lhs.auxChannels != rhs.auxChannels) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::audio::effect::V6_0::EffectAuxChannelsConfig& lhs, const ::android::hardware::audio::effect::V6_0::EffectAuxChannelsConfig& rhs){
    return !(lhs == rhs);
}

static inline std::string toString(const ::android::hardware::audio::effect::V6_0::EffectOffloadParameter& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".isOffload = ";
    os += ::android::hardware::toString(o.isOffload);
    os += ", .ioHandle = ";
    os += ::android::hardware::toString(o.ioHandle);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::audio::effect::V6_0::EffectOffloadParameter& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::audio::effect::V6_0::EffectOffloadParameter& lhs, const ::android::hardware::audio::effect::V6_0::EffectOffloadParameter& rhs) {
    if (lhs.isOffload != rhs.isOffload) {
        return false;
    }
    if (lhs.ioHandle != rhs.ioHandle) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::audio::effect::V6_0::EffectOffloadParameter& lhs, const ::android::hardware::audio::effect::V6_0::EffectOffloadParameter& rhs){
    return !(lhs == rhs);
}

template<>
inline std::string toString<::android::hardware::audio::effect::V6_0::MessageQueueFlagBits>(uint32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::audio::effect::V6_0::MessageQueueFlagBits> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::audio::effect::V6_0::MessageQueueFlagBits::DONE_PROCESSING) == static_cast<uint32_t>(::android::hardware::audio::effect::V6_0::MessageQueueFlagBits::DONE_PROCESSING)) {
        os += (first ? "" : " | ");
        os += "DONE_PROCESSING";
        first = false;
        flipped |= ::android::hardware::audio::effect::V6_0::MessageQueueFlagBits::DONE_PROCESSING;
    }
    if ((o & ::android::hardware::audio::effect::V6_0::MessageQueueFlagBits::REQUEST_PROCESS) == static_cast<uint32_t>(::android::hardware::audio::effect::V6_0::MessageQueueFlagBits::REQUEST_PROCESS)) {
        os += (first ? "" : " | ");
        os += "REQUEST_PROCESS";
        first = false;
        flipped |= ::android::hardware::audio::effect::V6_0::MessageQueueFlagBits::REQUEST_PROCESS;
    }
    if ((o & ::android::hardware::audio::effect::V6_0::MessageQueueFlagBits::REQUEST_PROCESS_REVERSE) == static_cast<uint32_t>(::android::hardware::audio::effect::V6_0::MessageQueueFlagBits::REQUEST_PROCESS_REVERSE)) {
        os += (first ? "" : " | ");
        os += "REQUEST_PROCESS_REVERSE";
        first = false;
        flipped |= ::android::hardware::audio::effect::V6_0::MessageQueueFlagBits::REQUEST_PROCESS_REVERSE;
    }
    if ((o & ::android::hardware::audio::effect::V6_0::MessageQueueFlagBits::REQUEST_QUIT) == static_cast<uint32_t>(::android::hardware::audio::effect::V6_0::MessageQueueFlagBits::REQUEST_QUIT)) {
        os += (first ? "" : " | ");
        os += "REQUEST_QUIT";
        first = false;
        flipped |= ::android::hardware::audio::effect::V6_0::MessageQueueFlagBits::REQUEST_QUIT;
    }
    if ((o & ::android::hardware::audio::effect::V6_0::MessageQueueFlagBits::REQUEST_PROCESS_ALL) == static_cast<uint32_t>(::android::hardware::audio::effect::V6_0::MessageQueueFlagBits::REQUEST_PROCESS_ALL)) {
        os += (first ? "" : " | ");
        os += "REQUEST_PROCESS_ALL";
        first = false;
        flipped |= ::android::hardware::audio::effect::V6_0::MessageQueueFlagBits::REQUEST_PROCESS_ALL;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::audio::effect::V6_0::MessageQueueFlagBits o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::audio::effect::V6_0::MessageQueueFlagBits::DONE_PROCESSING) {
        return "DONE_PROCESSING";
    }
    if (o == ::android::hardware::audio::effect::V6_0::MessageQueueFlagBits::REQUEST_PROCESS) {
        return "REQUEST_PROCESS";
    }
    if (o == ::android::hardware::audio::effect::V6_0::MessageQueueFlagBits::REQUEST_PROCESS_REVERSE) {
        return "REQUEST_PROCESS_REVERSE";
    }
    if (o == ::android::hardware::audio::effect::V6_0::MessageQueueFlagBits::REQUEST_QUIT) {
        return "REQUEST_QUIT";
    }
    if (o == ::android::hardware::audio::effect::V6_0::MessageQueueFlagBits::REQUEST_PROCESS_ALL) {
        return "REQUEST_PROCESS_ALL";
    }
    std::string os;
    os += toHexString(static_cast<uint32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::audio::effect::V6_0::MessageQueueFlagBits o, ::std::ostream* os) {
    *os << toString(o);
}


}  // namespace V6_0
}  // namespace effect
}  // namespace audio
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
template<> inline constexpr std::array<::android::hardware::audio::effect::V6_0::Result, 6> hidl_enum_values<::android::hardware::audio::effect::V6_0::Result> = {
    ::android::hardware::audio::effect::V6_0::Result::OK,
    ::android::hardware::audio::effect::V6_0::Result::NOT_INITIALIZED,
    ::android::hardware::audio::effect::V6_0::Result::INVALID_ARGUMENTS,
    ::android::hardware::audio::effect::V6_0::Result::INVALID_STATE,
    ::android::hardware::audio::effect::V6_0::Result::NOT_SUPPORTED,
    ::android::hardware::audio::effect::V6_0::Result::RESULT_TOO_BIG,
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
template<> inline constexpr std::array<::android::hardware::audio::effect::V6_0::EffectFlags, 62> hidl_enum_values<::android::hardware::audio::effect::V6_0::EffectFlags> = {
    ::android::hardware::audio::effect::V6_0::EffectFlags::TYPE_SHIFT,
    ::android::hardware::audio::effect::V6_0::EffectFlags::TYPE_SIZE,
    ::android::hardware::audio::effect::V6_0::EffectFlags::TYPE_MASK,
    ::android::hardware::audio::effect::V6_0::EffectFlags::TYPE_INSERT,
    ::android::hardware::audio::effect::V6_0::EffectFlags::TYPE_AUXILIARY,
    ::android::hardware::audio::effect::V6_0::EffectFlags::TYPE_REPLACE,
    ::android::hardware::audio::effect::V6_0::EffectFlags::TYPE_PRE_PROC,
    ::android::hardware::audio::effect::V6_0::EffectFlags::TYPE_POST_PROC,
    ::android::hardware::audio::effect::V6_0::EffectFlags::INSERT_SHIFT,
    ::android::hardware::audio::effect::V6_0::EffectFlags::INSERT_SIZE,
    ::android::hardware::audio::effect::V6_0::EffectFlags::INSERT_MASK,
    ::android::hardware::audio::effect::V6_0::EffectFlags::INSERT_ANY,
    ::android::hardware::audio::effect::V6_0::EffectFlags::INSERT_FIRST,
    ::android::hardware::audio::effect::V6_0::EffectFlags::INSERT_LAST,
    ::android::hardware::audio::effect::V6_0::EffectFlags::INSERT_EXCLUSIVE,
    ::android::hardware::audio::effect::V6_0::EffectFlags::VOLUME_SHIFT,
    ::android::hardware::audio::effect::V6_0::EffectFlags::VOLUME_SIZE,
    ::android::hardware::audio::effect::V6_0::EffectFlags::VOLUME_MASK,
    ::android::hardware::audio::effect::V6_0::EffectFlags::VOLUME_CTRL,
    ::android::hardware::audio::effect::V6_0::EffectFlags::VOLUME_IND,
    ::android::hardware::audio::effect::V6_0::EffectFlags::VOLUME_MONITOR,
    ::android::hardware::audio::effect::V6_0::EffectFlags::VOLUME_NONE,
    ::android::hardware::audio::effect::V6_0::EffectFlags::DEVICE_SHIFT,
    ::android::hardware::audio::effect::V6_0::EffectFlags::DEVICE_SIZE,
    ::android::hardware::audio::effect::V6_0::EffectFlags::DEVICE_MASK,
    ::android::hardware::audio::effect::V6_0::EffectFlags::DEVICE_IND,
    ::android::hardware::audio::effect::V6_0::EffectFlags::DEVICE_NONE,
    ::android::hardware::audio::effect::V6_0::EffectFlags::INPUT_SHIFT,
    ::android::hardware::audio::effect::V6_0::EffectFlags::INPUT_SIZE,
    ::android::hardware::audio::effect::V6_0::EffectFlags::INPUT_MASK,
    ::android::hardware::audio::effect::V6_0::EffectFlags::INPUT_DIRECT,
    ::android::hardware::audio::effect::V6_0::EffectFlags::INPUT_PROVIDER,
    ::android::hardware::audio::effect::V6_0::EffectFlags::INPUT_BOTH,
    ::android::hardware::audio::effect::V6_0::EffectFlags::OUTPUT_SHIFT,
    ::android::hardware::audio::effect::V6_0::EffectFlags::OUTPUT_SIZE,
    ::android::hardware::audio::effect::V6_0::EffectFlags::OUTPUT_MASK,
    ::android::hardware::audio::effect::V6_0::EffectFlags::OUTPUT_DIRECT,
    ::android::hardware::audio::effect::V6_0::EffectFlags::OUTPUT_PROVIDER,
    ::android::hardware::audio::effect::V6_0::EffectFlags::OUTPUT_BOTH,
    ::android::hardware::audio::effect::V6_0::EffectFlags::HW_ACC_SHIFT,
    ::android::hardware::audio::effect::V6_0::EffectFlags::HW_ACC_SIZE,
    ::android::hardware::audio::effect::V6_0::EffectFlags::HW_ACC_MASK,
    ::android::hardware::audio::effect::V6_0::EffectFlags::HW_ACC_SIMPLE,
    ::android::hardware::audio::effect::V6_0::EffectFlags::HW_ACC_TUNNEL,
    ::android::hardware::audio::effect::V6_0::EffectFlags::AUDIO_MODE_SHIFT,
    ::android::hardware::audio::effect::V6_0::EffectFlags::AUDIO_MODE_SIZE,
    ::android::hardware::audio::effect::V6_0::EffectFlags::AUDIO_MODE_MASK,
    ::android::hardware::audio::effect::V6_0::EffectFlags::AUDIO_MODE_IND,
    ::android::hardware::audio::effect::V6_0::EffectFlags::AUDIO_MODE_NONE,
    ::android::hardware::audio::effect::V6_0::EffectFlags::AUDIO_SOURCE_SHIFT,
    ::android::hardware::audio::effect::V6_0::EffectFlags::AUDIO_SOURCE_SIZE,
    ::android::hardware::audio::effect::V6_0::EffectFlags::AUDIO_SOURCE_MASK,
    ::android::hardware::audio::effect::V6_0::EffectFlags::AUDIO_SOURCE_IND,
    ::android::hardware::audio::effect::V6_0::EffectFlags::AUDIO_SOURCE_NONE,
    ::android::hardware::audio::effect::V6_0::EffectFlags::OFFLOAD_SHIFT,
    ::android::hardware::audio::effect::V6_0::EffectFlags::OFFLOAD_SIZE,
    ::android::hardware::audio::effect::V6_0::EffectFlags::OFFLOAD_MASK,
    ::android::hardware::audio::effect::V6_0::EffectFlags::OFFLOAD_SUPPORTED,
    ::android::hardware::audio::effect::V6_0::EffectFlags::NO_PROCESS_SHIFT,
    ::android::hardware::audio::effect::V6_0::EffectFlags::NO_PROCESS_SIZE,
    ::android::hardware::audio::effect::V6_0::EffectFlags::NO_PROCESS_MASK,
    ::android::hardware::audio::effect::V6_0::EffectFlags::NO_PROCESS,
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
template<> inline constexpr std::array<::android::hardware::audio::effect::V6_0::EffectBufferAccess, 3> hidl_enum_values<::android::hardware::audio::effect::V6_0::EffectBufferAccess> = {
    ::android::hardware::audio::effect::V6_0::EffectBufferAccess::ACCESS_WRITE,
    ::android::hardware::audio::effect::V6_0::EffectBufferAccess::ACCESS_READ,
    ::android::hardware::audio::effect::V6_0::EffectBufferAccess::ACCESS_ACCUMULATE,
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
template<> inline constexpr std::array<::android::hardware::audio::effect::V6_0::EffectConfigParameters, 5> hidl_enum_values<::android::hardware::audio::effect::V6_0::EffectConfigParameters> = {
    ::android::hardware::audio::effect::V6_0::EffectConfigParameters::BUFFER,
    ::android::hardware::audio::effect::V6_0::EffectConfigParameters::SMP_RATE,
    ::android::hardware::audio::effect::V6_0::EffectConfigParameters::CHANNELS,
    ::android::hardware::audio::effect::V6_0::EffectConfigParameters::FORMAT,
    ::android::hardware::audio::effect::V6_0::EffectConfigParameters::ACC_MODE,
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
template<> inline constexpr std::array<::android::hardware::audio::effect::V6_0::EffectFeature, 2> hidl_enum_values<::android::hardware::audio::effect::V6_0::EffectFeature> = {
    ::android::hardware::audio::effect::V6_0::EffectFeature::AUX_CHANNELS,
    ::android::hardware::audio::effect::V6_0::EffectFeature::CNT,
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
template<> inline constexpr std::array<::android::hardware::audio::effect::V6_0::MessageQueueFlagBits, 5> hidl_enum_values<::android::hardware::audio::effect::V6_0::MessageQueueFlagBits> = {
    ::android::hardware::audio::effect::V6_0::MessageQueueFlagBits::DONE_PROCESSING,
    ::android::hardware::audio::effect::V6_0::MessageQueueFlagBits::REQUEST_PROCESS,
    ::android::hardware::audio::effect::V6_0::MessageQueueFlagBits::REQUEST_PROCESS_REVERSE,
    ::android::hardware::audio::effect::V6_0::MessageQueueFlagBits::REQUEST_QUIT,
    ::android::hardware::audio::effect::V6_0::MessageQueueFlagBits::REQUEST_PROCESS_ALL,
};
#pragma clang diagnostic pop
}  // namespace details
}  // namespace hardware
}  // namespace android


#endif  // HIDL_GENERATED_ANDROID_HARDWARE_AUDIO_EFFECT_V6_0_TYPES_H
