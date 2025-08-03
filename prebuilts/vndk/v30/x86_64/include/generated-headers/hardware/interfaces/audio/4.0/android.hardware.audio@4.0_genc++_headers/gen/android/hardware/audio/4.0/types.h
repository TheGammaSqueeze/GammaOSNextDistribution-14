#ifndef HIDL_GENERATED_ANDROID_HARDWARE_AUDIO_V4_0_TYPES_H
#define HIDL_GENERATED_ANDROID_HARDWARE_AUDIO_V4_0_TYPES_H

#include <android/hardware/audio/common/4.0/types.h>

#include <hidl/HidlSupport.h>
#include <hidl/MQDescriptor.h>
#include <utils/NativeHandle.h>
#include <utils/misc.h>

namespace android {
namespace hardware {
namespace audio {
namespace V4_0 {

// Forward declaration for forward reference support:
enum class Result : int32_t;
enum class AudioDrain : int32_t;
struct TimeSpec;
struct ParameterValue;
struct DeviceAddress;
enum class MmapBufferFlag : uint32_t;
struct MmapBufferInfo;
struct MmapPosition;
enum class MessageQueueFlagBits : uint32_t;
struct PlaybackTrackMetadata;
struct SourceMetadata;
struct RecordTrackMetadata;
struct SinkMetadata;
struct AudioMicrophoneCoordinate;
enum class AudioMicrophoneChannelMapping : uint32_t;
enum class AudioMicrophoneLocation : uint32_t;
enum class AudioMicrophoneDirectionality : uint32_t;
struct AudioFrequencyResponsePoint;
struct MicrophoneInfo;

enum class Result : int32_t {
    OK = 0,
    NOT_INITIALIZED = 1 /* ::android::hardware::audio::V4_0::Result.OK implicitly + 1 */,
    INVALID_ARGUMENTS = 2 /* ::android::hardware::audio::V4_0::Result.NOT_INITIALIZED implicitly + 1 */,
    INVALID_STATE = 3 /* ::android::hardware::audio::V4_0::Result.INVALID_ARGUMENTS implicitly + 1 */,
    /**
     * Methods marked as "Optional method" must return this result value
     * if the operation is not supported by HAL.
     */
    NOT_SUPPORTED = 4 /* ::android::hardware::audio::V4_0::Result.INVALID_STATE implicitly + 1 */,
};

enum class AudioDrain : int32_t {
    /**
     * drain() returns when all data has been played.
     */
    ALL = 0,
    /**
     * drain() returns a short time before all data from the current track has
     * been played to give time for gapless track switch.
     */
    EARLY_NOTIFY = 1 /* ::android::hardware::audio::V4_0::AudioDrain.ALL implicitly + 1 */,
};

/**
 * A substitute for POSIX timespec.
 */
struct TimeSpec final {
    uint64_t tvSec __attribute__ ((aligned(8)));
    uint64_t tvNSec __attribute__ ((aligned(8)));
};

static_assert(offsetof(::android::hardware::audio::V4_0::TimeSpec, tvSec) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::audio::V4_0::TimeSpec, tvNSec) == 8, "wrong offset");
static_assert(sizeof(::android::hardware::audio::V4_0::TimeSpec) == 16, "wrong size");
static_assert(__alignof(::android::hardware::audio::V4_0::TimeSpec) == 8, "wrong alignment");

/**
 * IEEE 802 MAC address.
 */
typedef ::android::hardware::hidl_array<uint8_t, 6> MacAddress;

struct ParameterValue final {
    ::android::hardware::hidl_string key __attribute__ ((aligned(8)));
    ::android::hardware::hidl_string value __attribute__ ((aligned(8)));
};

static_assert(offsetof(::android::hardware::audio::V4_0::ParameterValue, key) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::audio::V4_0::ParameterValue, value) == 16, "wrong offset");
static_assert(sizeof(::android::hardware::audio::V4_0::ParameterValue) == 32, "wrong size");
static_assert(__alignof(::android::hardware::audio::V4_0::ParameterValue) == 8, "wrong alignment");

/**
 * Specifies a device in case when several devices of the same type
 * can be connected (e.g. BT A2DP, USB).
 */
struct DeviceAddress final {
    // Forward declaration for forward reference support:
    union Address;

    union Address final {
        // Forward declaration for forward reference support:
        struct Alsa;

        struct Alsa final {
            int32_t card __attribute__ ((aligned(4)));
            int32_t device __attribute__ ((aligned(4)));
        };

        static_assert(offsetof(::android::hardware::audio::V4_0::DeviceAddress::Address::Alsa, card) == 0, "wrong offset");
        static_assert(offsetof(::android::hardware::audio::V4_0::DeviceAddress::Address::Alsa, device) == 4, "wrong offset");
        static_assert(sizeof(::android::hardware::audio::V4_0::DeviceAddress::Address::Alsa) == 8, "wrong size");
        static_assert(__alignof(::android::hardware::audio::V4_0::DeviceAddress::Address::Alsa) == 4, "wrong alignment");

        ::android::hardware::hidl_array<uint8_t, 6> mac __attribute__ ((aligned(1)));
        ::android::hardware::hidl_array<uint8_t, 4> ipv4 __attribute__ ((aligned(1)));
        ::android::hardware::audio::V4_0::DeviceAddress::Address::Alsa alsa __attribute__ ((aligned(4)));
    };

    static_assert(offsetof(::android::hardware::audio::V4_0::DeviceAddress::Address, mac) == 0, "wrong offset");
    static_assert(offsetof(::android::hardware::audio::V4_0::DeviceAddress::Address, ipv4) == 0, "wrong offset");
    static_assert(offsetof(::android::hardware::audio::V4_0::DeviceAddress::Address, alsa) == 0, "wrong offset");
    static_assert(sizeof(::android::hardware::audio::V4_0::DeviceAddress::Address) == 8, "wrong size");
    static_assert(__alignof(::android::hardware::audio::V4_0::DeviceAddress::Address) == 4, "wrong alignment");

    ::android::hardware::audio::common::V4_0::AudioDevice device __attribute__ ((aligned(4)));
    ::android::hardware::audio::V4_0::DeviceAddress::Address address __attribute__ ((aligned(4)));
    ::android::hardware::hidl_string busAddress __attribute__ ((aligned(8)));
    ::android::hardware::hidl_string rSubmixAddress __attribute__ ((aligned(8)));
};

static_assert(offsetof(::android::hardware::audio::V4_0::DeviceAddress, device) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::audio::V4_0::DeviceAddress, address) == 4, "wrong offset");
static_assert(offsetof(::android::hardware::audio::V4_0::DeviceAddress, busAddress) == 16, "wrong offset");
static_assert(offsetof(::android::hardware::audio::V4_0::DeviceAddress, rSubmixAddress) == 32, "wrong offset");
static_assert(sizeof(::android::hardware::audio::V4_0::DeviceAddress) == 48, "wrong size");
static_assert(__alignof(::android::hardware::audio::V4_0::DeviceAddress) == 8, "wrong alignment");

enum class MmapBufferFlag : uint32_t {
    NONE = 0u /* 0x0 */,
    /**
     * If the buffer can be securely shared to untrusted applications
     * through the AAudio exclusive mode.
     * Only set this flag if applications are restricted from accessing the
     * memory surrounding the audio data buffer by a kernel mechanism.
     * See Linux kernel's dma_buf.
     */
    APPLICATION_SHAREABLE = 1u /* 0x1 */,
};

/**
 * Mmap buffer descriptor returned by IStream.createMmapBuffer().
 * Used by streams opened in mmap mode.
 */
struct MmapBufferInfo final {
    /**
     * Mmap memory buffer
     */
    ::android::hardware::hidl_memory sharedMemory __attribute__ ((aligned(8)));
    /**
     * Total buffer size in frames
     */
    uint32_t bufferSizeFrames __attribute__ ((aligned(4)));
    /**
     * Transfer size granularity in frames
     */
    uint32_t burstSizeFrames __attribute__ ((aligned(4)));
    /**
     * Attributes describing the buffer.
     */
    ::android::hardware::hidl_bitfield<::android::hardware::audio::V4_0::MmapBufferFlag> flags __attribute__ ((aligned(4)));
};

static_assert(offsetof(::android::hardware::audio::V4_0::MmapBufferInfo, sharedMemory) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::audio::V4_0::MmapBufferInfo, bufferSizeFrames) == 40, "wrong offset");
static_assert(offsetof(::android::hardware::audio::V4_0::MmapBufferInfo, burstSizeFrames) == 44, "wrong offset");
static_assert(offsetof(::android::hardware::audio::V4_0::MmapBufferInfo, flags) == 48, "wrong offset");
static_assert(sizeof(::android::hardware::audio::V4_0::MmapBufferInfo) == 56, "wrong size");
static_assert(__alignof(::android::hardware::audio::V4_0::MmapBufferInfo) == 8, "wrong alignment");

/**
 * Mmap buffer read/write position returned by IStream.getMmapPosition().
 * Used by streams opened in mmap mode.
 */
struct MmapPosition final {
    int64_t timeNanoseconds __attribute__ ((aligned(8)));
    int32_t positionFrames __attribute__ ((aligned(4)));
};

static_assert(offsetof(::android::hardware::audio::V4_0::MmapPosition, timeNanoseconds) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::audio::V4_0::MmapPosition, positionFrames) == 8, "wrong offset");
static_assert(sizeof(::android::hardware::audio::V4_0::MmapPosition) == 16, "wrong size");
static_assert(__alignof(::android::hardware::audio::V4_0::MmapPosition) == 8, "wrong alignment");

/**
 * The message queue flags used to synchronize reads and writes from
 * message queues used by StreamIn and StreamOut.
 */
enum class MessageQueueFlagBits : uint32_t {
    NOT_EMPTY = 1u /* 1 << 0 */,
    NOT_FULL = 2u /* 1 << 1 */,
};

/**
 * Metadata of a playback track for a StreamOut.
 */
struct PlaybackTrackMetadata final {
    ::android::hardware::audio::common::V4_0::AudioUsage usage __attribute__ ((aligned(4)));
    ::android::hardware::audio::common::V4_0::AudioContentType contentType __attribute__ ((aligned(4)));
    /**
     * Positive linear gain applied to the track samples. 0 being muted and 1 is no attenuation,
     * 2 means double amplification...
     * Must not be negative.
     */
    float gain __attribute__ ((aligned(4)));
};

static_assert(offsetof(::android::hardware::audio::V4_0::PlaybackTrackMetadata, usage) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::audio::V4_0::PlaybackTrackMetadata, contentType) == 4, "wrong offset");
static_assert(offsetof(::android::hardware::audio::V4_0::PlaybackTrackMetadata, gain) == 8, "wrong offset");
static_assert(sizeof(::android::hardware::audio::V4_0::PlaybackTrackMetadata) == 12, "wrong size");
static_assert(__alignof(::android::hardware::audio::V4_0::PlaybackTrackMetadata) == 4, "wrong alignment");

/**
 * Metadatas of the source of a StreamOut.
 */
struct SourceMetadata final {
    ::android::hardware::hidl_vec<::android::hardware::audio::V4_0::PlaybackTrackMetadata> tracks __attribute__ ((aligned(8)));
};

static_assert(offsetof(::android::hardware::audio::V4_0::SourceMetadata, tracks) == 0, "wrong offset");
static_assert(sizeof(::android::hardware::audio::V4_0::SourceMetadata) == 16, "wrong size");
static_assert(__alignof(::android::hardware::audio::V4_0::SourceMetadata) == 8, "wrong alignment");

/**
 * Metadata of a record track for a StreamIn.
 */
struct RecordTrackMetadata final {
    ::android::hardware::audio::common::V4_0::AudioSource source __attribute__ ((aligned(4)));
    /**
     * Positive linear gain applied to the track samples. 0 being muted and 1 is no attenuation,
     * 2 means double amplification...
     * Must not be negative.
     */
    float gain __attribute__ ((aligned(4)));
};

static_assert(offsetof(::android::hardware::audio::V4_0::RecordTrackMetadata, source) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::audio::V4_0::RecordTrackMetadata, gain) == 4, "wrong offset");
static_assert(sizeof(::android::hardware::audio::V4_0::RecordTrackMetadata) == 8, "wrong size");
static_assert(__alignof(::android::hardware::audio::V4_0::RecordTrackMetadata) == 4, "wrong alignment");

/**
 * Metadatas of the source of a StreamIn.
 */
struct SinkMetadata final {
    ::android::hardware::hidl_vec<::android::hardware::audio::V4_0::RecordTrackMetadata> tracks __attribute__ ((aligned(8)));
};

static_assert(offsetof(::android::hardware::audio::V4_0::SinkMetadata, tracks) == 0, "wrong offset");
static_assert(sizeof(::android::hardware::audio::V4_0::SinkMetadata) == 16, "wrong size");
static_assert(__alignof(::android::hardware::audio::V4_0::SinkMetadata) == 8, "wrong alignment");

/*
 * Microphone information
 *
 *
 *
 * A 3D point used to represent position or orientation of a microphone.
 *
 * Position: Coordinates of the microphone's capsule, in meters, from the
 * bottom-left-back corner of the bounding box of android device in natural
 * orientation (PORTRAIT for phones, LANDSCAPE for tablets, tvs, etc).
 * The orientation musth match the reported by the api Display.getRotation().
 *
 * Orientation: Normalized vector to signal the main orientation of the
 * microphone's capsule. Magnitude = sqrt(x^2 + y^2 + z^2) = 1
 */
struct AudioMicrophoneCoordinate final {
    float x __attribute__ ((aligned(4)));
    float y __attribute__ ((aligned(4)));
    float z __attribute__ ((aligned(4)));
};

static_assert(offsetof(::android::hardware::audio::V4_0::AudioMicrophoneCoordinate, x) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::audio::V4_0::AudioMicrophoneCoordinate, y) == 4, "wrong offset");
static_assert(offsetof(::android::hardware::audio::V4_0::AudioMicrophoneCoordinate, z) == 8, "wrong offset");
static_assert(sizeof(::android::hardware::audio::V4_0::AudioMicrophoneCoordinate) == 12, "wrong size");
static_assert(__alignof(::android::hardware::audio::V4_0::AudioMicrophoneCoordinate) == 4, "wrong alignment");

/**
 * Enum to identify the type of channel mapping for active microphones.
 * Used channels further identify if the microphone has any significative
 * process (e.g. High Pass Filtering, dynamic compression)
 * Simple processing as constant gain adjustment must be DIRECT.
 */
enum class AudioMicrophoneChannelMapping : uint32_t {
    UNUSED = 0u,
    /*
     * Channel not used
     */
    DIRECT = 1u,
    /*
     * Channel used and signal not processed
     */
    PROCESSED = 2u,
};

/**
 * Enum to identify locations of microphones in regards to the body of the
 * android device.
 */
enum class AudioMicrophoneLocation : uint32_t {
    UNKNOWN = 0u,
    MAINBODY = 1u,
    MAINBODY_MOVABLE = 2u,
    PERIPHERAL = 3u,
};

/**
 * Identifier to help group related microphones together
 * e.g. microphone arrays should belong to the same group
 */
typedef int32_t AudioMicrophoneGroup;

/**
 * Enum with standard polar patterns of microphones
 */
enum class AudioMicrophoneDirectionality : uint32_t {
    UNKNOWN = 0u,
    OMNI = 1u,
    BI_DIRECTIONAL = 2u,
    CARDIOID = 3u,
    HYPER_CARDIOID = 4u,
    SUPER_CARDIOID = 5u,
};

/**
 * A (frequency, level) pair. Used to represent frequency response.
 */
struct AudioFrequencyResponsePoint final {
    /**
     * In Hz
     */
    float frequency __attribute__ ((aligned(4)));
    /**
     * In dB
     */
    float level __attribute__ ((aligned(4)));
};

static_assert(offsetof(::android::hardware::audio::V4_0::AudioFrequencyResponsePoint, frequency) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::audio::V4_0::AudioFrequencyResponsePoint, level) == 4, "wrong offset");
static_assert(sizeof(::android::hardware::audio::V4_0::AudioFrequencyResponsePoint) == 8, "wrong size");
static_assert(__alignof(::android::hardware::audio::V4_0::AudioFrequencyResponsePoint) == 4, "wrong alignment");

/**
 * Structure used by the HAL to describe microphone's characteristics
 * Used by StreamIn and Device
 */
struct MicrophoneInfo final {
    /**
     * Unique alphanumeric id for microphone. Guaranteed to be the same
     * even after rebooting.
     */
    ::android::hardware::hidl_string deviceId __attribute__ ((aligned(8)));
    /**
     * Device specific information
     */
    ::android::hardware::audio::V4_0::DeviceAddress deviceAddress __attribute__ ((aligned(8)));
    /**
     * Each element of the vector must describe the channel with the same
     *  index.
     */
    ::android::hardware::hidl_vec<::android::hardware::audio::V4_0::AudioMicrophoneChannelMapping> channelMapping __attribute__ ((aligned(8)));
    /**
     * Location of the microphone in regard to the body of the device
     */
    ::android::hardware::audio::V4_0::AudioMicrophoneLocation location __attribute__ ((aligned(4)));
    /**
     * Identifier to help group related microphones together
     *  e.g. microphone arrays should belong to the same group
     */
    int32_t group __attribute__ ((aligned(4)));
    /**
     * Index of this microphone within the group.
     *  (group, index) must be unique within the same device.
     */
    uint32_t indexInTheGroup __attribute__ ((aligned(4)));
    /**
     * Level in dBFS produced by a 1000 Hz tone at 94 dB SPL
     */
    float sensitivity __attribute__ ((aligned(4)));
    /**
     * Level in dB of the max SPL supported at 1000 Hz
     */
    float maxSpl __attribute__ ((aligned(4)));
    /**
     * Level in dB of the min SPL supported at 1000 Hz
     */
    float minSpl __attribute__ ((aligned(4)));
    /**
     * Standard polar pattern of the microphone
     */
    ::android::hardware::audio::V4_0::AudioMicrophoneDirectionality directionality __attribute__ ((aligned(4)));
    /**
     * Vector with ordered frequency responses (from low to high frequencies)
     *  with the frequency response of the microphone.
     *  Levels are in dB, relative to level at 1000 Hz
     */
    ::android::hardware::hidl_vec<::android::hardware::audio::V4_0::AudioFrequencyResponsePoint> frequencyResponse __attribute__ ((aligned(8)));
    /**
     * Position of the microphone's capsule in meters, from the
     *  bottom-left-back corner of the bounding box of device.
     */
    ::android::hardware::audio::V4_0::AudioMicrophoneCoordinate position __attribute__ ((aligned(4)));
    /**
     * Normalized point to signal the main orientation of the microphone's
     *  capsule. sqrt(x^2 + y^2 + z^2) = 1
     */
    ::android::hardware::audio::V4_0::AudioMicrophoneCoordinate orientation __attribute__ ((aligned(4)));
};

static_assert(offsetof(::android::hardware::audio::V4_0::MicrophoneInfo, deviceId) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::audio::V4_0::MicrophoneInfo, deviceAddress) == 16, "wrong offset");
static_assert(offsetof(::android::hardware::audio::V4_0::MicrophoneInfo, channelMapping) == 64, "wrong offset");
static_assert(offsetof(::android::hardware::audio::V4_0::MicrophoneInfo, location) == 80, "wrong offset");
static_assert(offsetof(::android::hardware::audio::V4_0::MicrophoneInfo, group) == 84, "wrong offset");
static_assert(offsetof(::android::hardware::audio::V4_0::MicrophoneInfo, indexInTheGroup) == 88, "wrong offset");
static_assert(offsetof(::android::hardware::audio::V4_0::MicrophoneInfo, sensitivity) == 92, "wrong offset");
static_assert(offsetof(::android::hardware::audio::V4_0::MicrophoneInfo, maxSpl) == 96, "wrong offset");
static_assert(offsetof(::android::hardware::audio::V4_0::MicrophoneInfo, minSpl) == 100, "wrong offset");
static_assert(offsetof(::android::hardware::audio::V4_0::MicrophoneInfo, directionality) == 104, "wrong offset");
static_assert(offsetof(::android::hardware::audio::V4_0::MicrophoneInfo, frequencyResponse) == 112, "wrong offset");
static_assert(offsetof(::android::hardware::audio::V4_0::MicrophoneInfo, position) == 128, "wrong offset");
static_assert(offsetof(::android::hardware::audio::V4_0::MicrophoneInfo, orientation) == 140, "wrong offset");
static_assert(sizeof(::android::hardware::audio::V4_0::MicrophoneInfo) == 152, "wrong size");
static_assert(__alignof(::android::hardware::audio::V4_0::MicrophoneInfo) == 8, "wrong alignment");

//
// type declarations for package
//

template<typename>
static inline std::string toString(int32_t o);
static inline std::string toString(::android::hardware::audio::V4_0::Result o);
static inline void PrintTo(::android::hardware::audio::V4_0::Result o, ::std::ostream* os);
constexpr int32_t operator|(const ::android::hardware::audio::V4_0::Result lhs, const ::android::hardware::audio::V4_0::Result rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const int32_t lhs, const ::android::hardware::audio::V4_0::Result rhs) {
    return static_cast<int32_t>(lhs | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const ::android::hardware::audio::V4_0::Result lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | rhs);
}
constexpr int32_t operator&(const ::android::hardware::audio::V4_0::Result lhs, const ::android::hardware::audio::V4_0::Result rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const int32_t lhs, const ::android::hardware::audio::V4_0::Result rhs) {
    return static_cast<int32_t>(lhs & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const ::android::hardware::audio::V4_0::Result lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & rhs);
}
constexpr int32_t &operator|=(int32_t& v, const ::android::hardware::audio::V4_0::Result e) {
    v |= static_cast<int32_t>(e);
    return v;
}
constexpr int32_t &operator&=(int32_t& v, const ::android::hardware::audio::V4_0::Result e) {
    v &= static_cast<int32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(int32_t o);
static inline std::string toString(::android::hardware::audio::V4_0::AudioDrain o);
static inline void PrintTo(::android::hardware::audio::V4_0::AudioDrain o, ::std::ostream* os);
constexpr int32_t operator|(const ::android::hardware::audio::V4_0::AudioDrain lhs, const ::android::hardware::audio::V4_0::AudioDrain rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const int32_t lhs, const ::android::hardware::audio::V4_0::AudioDrain rhs) {
    return static_cast<int32_t>(lhs | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const ::android::hardware::audio::V4_0::AudioDrain lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | rhs);
}
constexpr int32_t operator&(const ::android::hardware::audio::V4_0::AudioDrain lhs, const ::android::hardware::audio::V4_0::AudioDrain rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const int32_t lhs, const ::android::hardware::audio::V4_0::AudioDrain rhs) {
    return static_cast<int32_t>(lhs & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const ::android::hardware::audio::V4_0::AudioDrain lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & rhs);
}
constexpr int32_t &operator|=(int32_t& v, const ::android::hardware::audio::V4_0::AudioDrain e) {
    v |= static_cast<int32_t>(e);
    return v;
}
constexpr int32_t &operator&=(int32_t& v, const ::android::hardware::audio::V4_0::AudioDrain e) {
    v &= static_cast<int32_t>(e);
    return v;
}

static inline std::string toString(const ::android::hardware::audio::V4_0::TimeSpec& o);
static inline void PrintTo(const ::android::hardware::audio::V4_0::TimeSpec& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::audio::V4_0::TimeSpec& lhs, const ::android::hardware::audio::V4_0::TimeSpec& rhs);
static inline bool operator!=(const ::android::hardware::audio::V4_0::TimeSpec& lhs, const ::android::hardware::audio::V4_0::TimeSpec& rhs);

static inline std::string toString(const ::android::hardware::audio::V4_0::ParameterValue& o);
static inline void PrintTo(const ::android::hardware::audio::V4_0::ParameterValue& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::audio::V4_0::ParameterValue& lhs, const ::android::hardware::audio::V4_0::ParameterValue& rhs);
static inline bool operator!=(const ::android::hardware::audio::V4_0::ParameterValue& lhs, const ::android::hardware::audio::V4_0::ParameterValue& rhs);

static inline std::string toString(const ::android::hardware::audio::V4_0::DeviceAddress::Address::Alsa& o);
static inline void PrintTo(const ::android::hardware::audio::V4_0::DeviceAddress::Address::Alsa& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::audio::V4_0::DeviceAddress::Address::Alsa& lhs, const ::android::hardware::audio::V4_0::DeviceAddress::Address::Alsa& rhs);
static inline bool operator!=(const ::android::hardware::audio::V4_0::DeviceAddress::Address::Alsa& lhs, const ::android::hardware::audio::V4_0::DeviceAddress::Address::Alsa& rhs);

static inline std::string toString(const ::android::hardware::audio::V4_0::DeviceAddress::Address& o);
static inline void PrintTo(const ::android::hardware::audio::V4_0::DeviceAddress::Address& o, ::std::ostream*);
// operator== and operator!= are not generated for Address

static inline std::string toString(const ::android::hardware::audio::V4_0::DeviceAddress& o);
static inline void PrintTo(const ::android::hardware::audio::V4_0::DeviceAddress& o, ::std::ostream*);
// operator== and operator!= are not generated for DeviceAddress

template<typename>
static inline std::string toString(uint32_t o);
static inline std::string toString(::android::hardware::audio::V4_0::MmapBufferFlag o);
static inline void PrintTo(::android::hardware::audio::V4_0::MmapBufferFlag o, ::std::ostream* os);
constexpr uint32_t operator|(const ::android::hardware::audio::V4_0::MmapBufferFlag lhs, const ::android::hardware::audio::V4_0::MmapBufferFlag rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const uint32_t lhs, const ::android::hardware::audio::V4_0::MmapBufferFlag rhs) {
    return static_cast<uint32_t>(lhs | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const ::android::hardware::audio::V4_0::MmapBufferFlag lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | rhs);
}
constexpr uint32_t operator&(const ::android::hardware::audio::V4_0::MmapBufferFlag lhs, const ::android::hardware::audio::V4_0::MmapBufferFlag rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const uint32_t lhs, const ::android::hardware::audio::V4_0::MmapBufferFlag rhs) {
    return static_cast<uint32_t>(lhs & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const ::android::hardware::audio::V4_0::MmapBufferFlag lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & rhs);
}
constexpr uint32_t &operator|=(uint32_t& v, const ::android::hardware::audio::V4_0::MmapBufferFlag e) {
    v |= static_cast<uint32_t>(e);
    return v;
}
constexpr uint32_t &operator&=(uint32_t& v, const ::android::hardware::audio::V4_0::MmapBufferFlag e) {
    v &= static_cast<uint32_t>(e);
    return v;
}

static inline std::string toString(const ::android::hardware::audio::V4_0::MmapBufferInfo& o);
static inline void PrintTo(const ::android::hardware::audio::V4_0::MmapBufferInfo& o, ::std::ostream*);
// operator== and operator!= are not generated for MmapBufferInfo

static inline std::string toString(const ::android::hardware::audio::V4_0::MmapPosition& o);
static inline void PrintTo(const ::android::hardware::audio::V4_0::MmapPosition& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::audio::V4_0::MmapPosition& lhs, const ::android::hardware::audio::V4_0::MmapPosition& rhs);
static inline bool operator!=(const ::android::hardware::audio::V4_0::MmapPosition& lhs, const ::android::hardware::audio::V4_0::MmapPosition& rhs);

template<typename>
static inline std::string toString(uint32_t o);
static inline std::string toString(::android::hardware::audio::V4_0::MessageQueueFlagBits o);
static inline void PrintTo(::android::hardware::audio::V4_0::MessageQueueFlagBits o, ::std::ostream* os);
constexpr uint32_t operator|(const ::android::hardware::audio::V4_0::MessageQueueFlagBits lhs, const ::android::hardware::audio::V4_0::MessageQueueFlagBits rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const uint32_t lhs, const ::android::hardware::audio::V4_0::MessageQueueFlagBits rhs) {
    return static_cast<uint32_t>(lhs | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const ::android::hardware::audio::V4_0::MessageQueueFlagBits lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | rhs);
}
constexpr uint32_t operator&(const ::android::hardware::audio::V4_0::MessageQueueFlagBits lhs, const ::android::hardware::audio::V4_0::MessageQueueFlagBits rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const uint32_t lhs, const ::android::hardware::audio::V4_0::MessageQueueFlagBits rhs) {
    return static_cast<uint32_t>(lhs & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const ::android::hardware::audio::V4_0::MessageQueueFlagBits lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & rhs);
}
constexpr uint32_t &operator|=(uint32_t& v, const ::android::hardware::audio::V4_0::MessageQueueFlagBits e) {
    v |= static_cast<uint32_t>(e);
    return v;
}
constexpr uint32_t &operator&=(uint32_t& v, const ::android::hardware::audio::V4_0::MessageQueueFlagBits e) {
    v &= static_cast<uint32_t>(e);
    return v;
}

static inline std::string toString(const ::android::hardware::audio::V4_0::PlaybackTrackMetadata& o);
static inline void PrintTo(const ::android::hardware::audio::V4_0::PlaybackTrackMetadata& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::audio::V4_0::PlaybackTrackMetadata& lhs, const ::android::hardware::audio::V4_0::PlaybackTrackMetadata& rhs);
static inline bool operator!=(const ::android::hardware::audio::V4_0::PlaybackTrackMetadata& lhs, const ::android::hardware::audio::V4_0::PlaybackTrackMetadata& rhs);

static inline std::string toString(const ::android::hardware::audio::V4_0::SourceMetadata& o);
static inline void PrintTo(const ::android::hardware::audio::V4_0::SourceMetadata& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::audio::V4_0::SourceMetadata& lhs, const ::android::hardware::audio::V4_0::SourceMetadata& rhs);
static inline bool operator!=(const ::android::hardware::audio::V4_0::SourceMetadata& lhs, const ::android::hardware::audio::V4_0::SourceMetadata& rhs);

static inline std::string toString(const ::android::hardware::audio::V4_0::RecordTrackMetadata& o);
static inline void PrintTo(const ::android::hardware::audio::V4_0::RecordTrackMetadata& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::audio::V4_0::RecordTrackMetadata& lhs, const ::android::hardware::audio::V4_0::RecordTrackMetadata& rhs);
static inline bool operator!=(const ::android::hardware::audio::V4_0::RecordTrackMetadata& lhs, const ::android::hardware::audio::V4_0::RecordTrackMetadata& rhs);

static inline std::string toString(const ::android::hardware::audio::V4_0::SinkMetadata& o);
static inline void PrintTo(const ::android::hardware::audio::V4_0::SinkMetadata& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::audio::V4_0::SinkMetadata& lhs, const ::android::hardware::audio::V4_0::SinkMetadata& rhs);
static inline bool operator!=(const ::android::hardware::audio::V4_0::SinkMetadata& lhs, const ::android::hardware::audio::V4_0::SinkMetadata& rhs);

static inline std::string toString(const ::android::hardware::audio::V4_0::AudioMicrophoneCoordinate& o);
static inline void PrintTo(const ::android::hardware::audio::V4_0::AudioMicrophoneCoordinate& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::audio::V4_0::AudioMicrophoneCoordinate& lhs, const ::android::hardware::audio::V4_0::AudioMicrophoneCoordinate& rhs);
static inline bool operator!=(const ::android::hardware::audio::V4_0::AudioMicrophoneCoordinate& lhs, const ::android::hardware::audio::V4_0::AudioMicrophoneCoordinate& rhs);

template<typename>
static inline std::string toString(uint32_t o);
static inline std::string toString(::android::hardware::audio::V4_0::AudioMicrophoneChannelMapping o);
static inline void PrintTo(::android::hardware::audio::V4_0::AudioMicrophoneChannelMapping o, ::std::ostream* os);
constexpr uint32_t operator|(const ::android::hardware::audio::V4_0::AudioMicrophoneChannelMapping lhs, const ::android::hardware::audio::V4_0::AudioMicrophoneChannelMapping rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const uint32_t lhs, const ::android::hardware::audio::V4_0::AudioMicrophoneChannelMapping rhs) {
    return static_cast<uint32_t>(lhs | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const ::android::hardware::audio::V4_0::AudioMicrophoneChannelMapping lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | rhs);
}
constexpr uint32_t operator&(const ::android::hardware::audio::V4_0::AudioMicrophoneChannelMapping lhs, const ::android::hardware::audio::V4_0::AudioMicrophoneChannelMapping rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const uint32_t lhs, const ::android::hardware::audio::V4_0::AudioMicrophoneChannelMapping rhs) {
    return static_cast<uint32_t>(lhs & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const ::android::hardware::audio::V4_0::AudioMicrophoneChannelMapping lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & rhs);
}
constexpr uint32_t &operator|=(uint32_t& v, const ::android::hardware::audio::V4_0::AudioMicrophoneChannelMapping e) {
    v |= static_cast<uint32_t>(e);
    return v;
}
constexpr uint32_t &operator&=(uint32_t& v, const ::android::hardware::audio::V4_0::AudioMicrophoneChannelMapping e) {
    v &= static_cast<uint32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(uint32_t o);
static inline std::string toString(::android::hardware::audio::V4_0::AudioMicrophoneLocation o);
static inline void PrintTo(::android::hardware::audio::V4_0::AudioMicrophoneLocation o, ::std::ostream* os);
constexpr uint32_t operator|(const ::android::hardware::audio::V4_0::AudioMicrophoneLocation lhs, const ::android::hardware::audio::V4_0::AudioMicrophoneLocation rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const uint32_t lhs, const ::android::hardware::audio::V4_0::AudioMicrophoneLocation rhs) {
    return static_cast<uint32_t>(lhs | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const ::android::hardware::audio::V4_0::AudioMicrophoneLocation lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | rhs);
}
constexpr uint32_t operator&(const ::android::hardware::audio::V4_0::AudioMicrophoneLocation lhs, const ::android::hardware::audio::V4_0::AudioMicrophoneLocation rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const uint32_t lhs, const ::android::hardware::audio::V4_0::AudioMicrophoneLocation rhs) {
    return static_cast<uint32_t>(lhs & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const ::android::hardware::audio::V4_0::AudioMicrophoneLocation lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & rhs);
}
constexpr uint32_t &operator|=(uint32_t& v, const ::android::hardware::audio::V4_0::AudioMicrophoneLocation e) {
    v |= static_cast<uint32_t>(e);
    return v;
}
constexpr uint32_t &operator&=(uint32_t& v, const ::android::hardware::audio::V4_0::AudioMicrophoneLocation e) {
    v &= static_cast<uint32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(uint32_t o);
static inline std::string toString(::android::hardware::audio::V4_0::AudioMicrophoneDirectionality o);
static inline void PrintTo(::android::hardware::audio::V4_0::AudioMicrophoneDirectionality o, ::std::ostream* os);
constexpr uint32_t operator|(const ::android::hardware::audio::V4_0::AudioMicrophoneDirectionality lhs, const ::android::hardware::audio::V4_0::AudioMicrophoneDirectionality rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const uint32_t lhs, const ::android::hardware::audio::V4_0::AudioMicrophoneDirectionality rhs) {
    return static_cast<uint32_t>(lhs | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const ::android::hardware::audio::V4_0::AudioMicrophoneDirectionality lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | rhs);
}
constexpr uint32_t operator&(const ::android::hardware::audio::V4_0::AudioMicrophoneDirectionality lhs, const ::android::hardware::audio::V4_0::AudioMicrophoneDirectionality rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const uint32_t lhs, const ::android::hardware::audio::V4_0::AudioMicrophoneDirectionality rhs) {
    return static_cast<uint32_t>(lhs & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const ::android::hardware::audio::V4_0::AudioMicrophoneDirectionality lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & rhs);
}
constexpr uint32_t &operator|=(uint32_t& v, const ::android::hardware::audio::V4_0::AudioMicrophoneDirectionality e) {
    v |= static_cast<uint32_t>(e);
    return v;
}
constexpr uint32_t &operator&=(uint32_t& v, const ::android::hardware::audio::V4_0::AudioMicrophoneDirectionality e) {
    v &= static_cast<uint32_t>(e);
    return v;
}

static inline std::string toString(const ::android::hardware::audio::V4_0::AudioFrequencyResponsePoint& o);
static inline void PrintTo(const ::android::hardware::audio::V4_0::AudioFrequencyResponsePoint& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::audio::V4_0::AudioFrequencyResponsePoint& lhs, const ::android::hardware::audio::V4_0::AudioFrequencyResponsePoint& rhs);
static inline bool operator!=(const ::android::hardware::audio::V4_0::AudioFrequencyResponsePoint& lhs, const ::android::hardware::audio::V4_0::AudioFrequencyResponsePoint& rhs);

static inline std::string toString(const ::android::hardware::audio::V4_0::MicrophoneInfo& o);
static inline void PrintTo(const ::android::hardware::audio::V4_0::MicrophoneInfo& o, ::std::ostream*);
// operator== and operator!= are not generated for MicrophoneInfo

//
// type header definitions for package
//

template<>
inline std::string toString<::android::hardware::audio::V4_0::Result>(int32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::audio::V4_0::Result> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::audio::V4_0::Result::OK) == static_cast<int32_t>(::android::hardware::audio::V4_0::Result::OK)) {
        os += (first ? "" : " | ");
        os += "OK";
        first = false;
        flipped |= ::android::hardware::audio::V4_0::Result::OK;
    }
    if ((o & ::android::hardware::audio::V4_0::Result::NOT_INITIALIZED) == static_cast<int32_t>(::android::hardware::audio::V4_0::Result::NOT_INITIALIZED)) {
        os += (first ? "" : " | ");
        os += "NOT_INITIALIZED";
        first = false;
        flipped |= ::android::hardware::audio::V4_0::Result::NOT_INITIALIZED;
    }
    if ((o & ::android::hardware::audio::V4_0::Result::INVALID_ARGUMENTS) == static_cast<int32_t>(::android::hardware::audio::V4_0::Result::INVALID_ARGUMENTS)) {
        os += (first ? "" : " | ");
        os += "INVALID_ARGUMENTS";
        first = false;
        flipped |= ::android::hardware::audio::V4_0::Result::INVALID_ARGUMENTS;
    }
    if ((o & ::android::hardware::audio::V4_0::Result::INVALID_STATE) == static_cast<int32_t>(::android::hardware::audio::V4_0::Result::INVALID_STATE)) {
        os += (first ? "" : " | ");
        os += "INVALID_STATE";
        first = false;
        flipped |= ::android::hardware::audio::V4_0::Result::INVALID_STATE;
    }
    if ((o & ::android::hardware::audio::V4_0::Result::NOT_SUPPORTED) == static_cast<int32_t>(::android::hardware::audio::V4_0::Result::NOT_SUPPORTED)) {
        os += (first ? "" : " | ");
        os += "NOT_SUPPORTED";
        first = false;
        flipped |= ::android::hardware::audio::V4_0::Result::NOT_SUPPORTED;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::audio::V4_0::Result o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::audio::V4_0::Result::OK) {
        return "OK";
    }
    if (o == ::android::hardware::audio::V4_0::Result::NOT_INITIALIZED) {
        return "NOT_INITIALIZED";
    }
    if (o == ::android::hardware::audio::V4_0::Result::INVALID_ARGUMENTS) {
        return "INVALID_ARGUMENTS";
    }
    if (o == ::android::hardware::audio::V4_0::Result::INVALID_STATE) {
        return "INVALID_STATE";
    }
    if (o == ::android::hardware::audio::V4_0::Result::NOT_SUPPORTED) {
        return "NOT_SUPPORTED";
    }
    std::string os;
    os += toHexString(static_cast<int32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::audio::V4_0::Result o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::audio::V4_0::AudioDrain>(int32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::audio::V4_0::AudioDrain> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::audio::V4_0::AudioDrain::ALL) == static_cast<int32_t>(::android::hardware::audio::V4_0::AudioDrain::ALL)) {
        os += (first ? "" : " | ");
        os += "ALL";
        first = false;
        flipped |= ::android::hardware::audio::V4_0::AudioDrain::ALL;
    }
    if ((o & ::android::hardware::audio::V4_0::AudioDrain::EARLY_NOTIFY) == static_cast<int32_t>(::android::hardware::audio::V4_0::AudioDrain::EARLY_NOTIFY)) {
        os += (first ? "" : " | ");
        os += "EARLY_NOTIFY";
        first = false;
        flipped |= ::android::hardware::audio::V4_0::AudioDrain::EARLY_NOTIFY;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::audio::V4_0::AudioDrain o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::audio::V4_0::AudioDrain::ALL) {
        return "ALL";
    }
    if (o == ::android::hardware::audio::V4_0::AudioDrain::EARLY_NOTIFY) {
        return "EARLY_NOTIFY";
    }
    std::string os;
    os += toHexString(static_cast<int32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::audio::V4_0::AudioDrain o, ::std::ostream* os) {
    *os << toString(o);
}

static inline std::string toString(const ::android::hardware::audio::V4_0::TimeSpec& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".tvSec = ";
    os += ::android::hardware::toString(o.tvSec);
    os += ", .tvNSec = ";
    os += ::android::hardware::toString(o.tvNSec);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::audio::V4_0::TimeSpec& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::audio::V4_0::TimeSpec& lhs, const ::android::hardware::audio::V4_0::TimeSpec& rhs) {
    if (lhs.tvSec != rhs.tvSec) {
        return false;
    }
    if (lhs.tvNSec != rhs.tvNSec) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::audio::V4_0::TimeSpec& lhs, const ::android::hardware::audio::V4_0::TimeSpec& rhs){
    return !(lhs == rhs);
}

static inline std::string toString(const ::android::hardware::audio::V4_0::ParameterValue& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".key = ";
    os += ::android::hardware::toString(o.key);
    os += ", .value = ";
    os += ::android::hardware::toString(o.value);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::audio::V4_0::ParameterValue& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::audio::V4_0::ParameterValue& lhs, const ::android::hardware::audio::V4_0::ParameterValue& rhs) {
    if (lhs.key != rhs.key) {
        return false;
    }
    if (lhs.value != rhs.value) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::audio::V4_0::ParameterValue& lhs, const ::android::hardware::audio::V4_0::ParameterValue& rhs){
    return !(lhs == rhs);
}

static inline std::string toString(const ::android::hardware::audio::V4_0::DeviceAddress::Address::Alsa& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".card = ";
    os += ::android::hardware::toString(o.card);
    os += ", .device = ";
    os += ::android::hardware::toString(o.device);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::audio::V4_0::DeviceAddress::Address::Alsa& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::audio::V4_0::DeviceAddress::Address::Alsa& lhs, const ::android::hardware::audio::V4_0::DeviceAddress::Address::Alsa& rhs) {
    if (lhs.card != rhs.card) {
        return false;
    }
    if (lhs.device != rhs.device) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::audio::V4_0::DeviceAddress::Address::Alsa& lhs, const ::android::hardware::audio::V4_0::DeviceAddress::Address::Alsa& rhs){
    return !(lhs == rhs);
}

static inline std::string toString(const ::android::hardware::audio::V4_0::DeviceAddress::Address& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".mac = ";
    os += ::android::hardware::toString(o.mac);
    os += ", .ipv4 = ";
    os += ::android::hardware::toString(o.ipv4);
    os += ", .alsa = ";
    os += ::android::hardware::audio::V4_0::toString(o.alsa);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::audio::V4_0::DeviceAddress::Address& o, ::std::ostream* os) {
    *os << toString(o);
}

// operator== and operator!= are not generated for Address

static inline std::string toString(const ::android::hardware::audio::V4_0::DeviceAddress& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".device = ";
    os += ::android::hardware::audio::common::V4_0::toString(o.device);
    os += ", .address = ";
    os += ::android::hardware::audio::V4_0::toString(o.address);
    os += ", .busAddress = ";
    os += ::android::hardware::toString(o.busAddress);
    os += ", .rSubmixAddress = ";
    os += ::android::hardware::toString(o.rSubmixAddress);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::audio::V4_0::DeviceAddress& o, ::std::ostream* os) {
    *os << toString(o);
}

// operator== and operator!= are not generated for DeviceAddress

template<>
inline std::string toString<::android::hardware::audio::V4_0::MmapBufferFlag>(uint32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::audio::V4_0::MmapBufferFlag> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::audio::V4_0::MmapBufferFlag::NONE) == static_cast<uint32_t>(::android::hardware::audio::V4_0::MmapBufferFlag::NONE)) {
        os += (first ? "" : " | ");
        os += "NONE";
        first = false;
        flipped |= ::android::hardware::audio::V4_0::MmapBufferFlag::NONE;
    }
    if ((o & ::android::hardware::audio::V4_0::MmapBufferFlag::APPLICATION_SHAREABLE) == static_cast<uint32_t>(::android::hardware::audio::V4_0::MmapBufferFlag::APPLICATION_SHAREABLE)) {
        os += (first ? "" : " | ");
        os += "APPLICATION_SHAREABLE";
        first = false;
        flipped |= ::android::hardware::audio::V4_0::MmapBufferFlag::APPLICATION_SHAREABLE;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::audio::V4_0::MmapBufferFlag o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::audio::V4_0::MmapBufferFlag::NONE) {
        return "NONE";
    }
    if (o == ::android::hardware::audio::V4_0::MmapBufferFlag::APPLICATION_SHAREABLE) {
        return "APPLICATION_SHAREABLE";
    }
    std::string os;
    os += toHexString(static_cast<uint32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::audio::V4_0::MmapBufferFlag o, ::std::ostream* os) {
    *os << toString(o);
}

static inline std::string toString(const ::android::hardware::audio::V4_0::MmapBufferInfo& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".sharedMemory = ";
    os += ::android::hardware::toString(o.sharedMemory);
    os += ", .bufferSizeFrames = ";
    os += ::android::hardware::toString(o.bufferSizeFrames);
    os += ", .burstSizeFrames = ";
    os += ::android::hardware::toString(o.burstSizeFrames);
    os += ", .flags = ";
    os += ::android::hardware::audio::V4_0::toString<::android::hardware::audio::V4_0::MmapBufferFlag>(o.flags);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::audio::V4_0::MmapBufferInfo& o, ::std::ostream* os) {
    *os << toString(o);
}

// operator== and operator!= are not generated for MmapBufferInfo

static inline std::string toString(const ::android::hardware::audio::V4_0::MmapPosition& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".timeNanoseconds = ";
    os += ::android::hardware::toString(o.timeNanoseconds);
    os += ", .positionFrames = ";
    os += ::android::hardware::toString(o.positionFrames);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::audio::V4_0::MmapPosition& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::audio::V4_0::MmapPosition& lhs, const ::android::hardware::audio::V4_0::MmapPosition& rhs) {
    if (lhs.timeNanoseconds != rhs.timeNanoseconds) {
        return false;
    }
    if (lhs.positionFrames != rhs.positionFrames) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::audio::V4_0::MmapPosition& lhs, const ::android::hardware::audio::V4_0::MmapPosition& rhs){
    return !(lhs == rhs);
}

template<>
inline std::string toString<::android::hardware::audio::V4_0::MessageQueueFlagBits>(uint32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::audio::V4_0::MessageQueueFlagBits> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::audio::V4_0::MessageQueueFlagBits::NOT_EMPTY) == static_cast<uint32_t>(::android::hardware::audio::V4_0::MessageQueueFlagBits::NOT_EMPTY)) {
        os += (first ? "" : " | ");
        os += "NOT_EMPTY";
        first = false;
        flipped |= ::android::hardware::audio::V4_0::MessageQueueFlagBits::NOT_EMPTY;
    }
    if ((o & ::android::hardware::audio::V4_0::MessageQueueFlagBits::NOT_FULL) == static_cast<uint32_t>(::android::hardware::audio::V4_0::MessageQueueFlagBits::NOT_FULL)) {
        os += (first ? "" : " | ");
        os += "NOT_FULL";
        first = false;
        flipped |= ::android::hardware::audio::V4_0::MessageQueueFlagBits::NOT_FULL;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::audio::V4_0::MessageQueueFlagBits o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::audio::V4_0::MessageQueueFlagBits::NOT_EMPTY) {
        return "NOT_EMPTY";
    }
    if (o == ::android::hardware::audio::V4_0::MessageQueueFlagBits::NOT_FULL) {
        return "NOT_FULL";
    }
    std::string os;
    os += toHexString(static_cast<uint32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::audio::V4_0::MessageQueueFlagBits o, ::std::ostream* os) {
    *os << toString(o);
}

static inline std::string toString(const ::android::hardware::audio::V4_0::PlaybackTrackMetadata& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".usage = ";
    os += ::android::hardware::audio::common::V4_0::toString(o.usage);
    os += ", .contentType = ";
    os += ::android::hardware::audio::common::V4_0::toString(o.contentType);
    os += ", .gain = ";
    os += ::android::hardware::toString(o.gain);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::audio::V4_0::PlaybackTrackMetadata& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::audio::V4_0::PlaybackTrackMetadata& lhs, const ::android::hardware::audio::V4_0::PlaybackTrackMetadata& rhs) {
    if (lhs.usage != rhs.usage) {
        return false;
    }
    if (lhs.contentType != rhs.contentType) {
        return false;
    }
    if (lhs.gain != rhs.gain) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::audio::V4_0::PlaybackTrackMetadata& lhs, const ::android::hardware::audio::V4_0::PlaybackTrackMetadata& rhs){
    return !(lhs == rhs);
}

static inline std::string toString(const ::android::hardware::audio::V4_0::SourceMetadata& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".tracks = ";
    os += ::android::hardware::toString(o.tracks);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::audio::V4_0::SourceMetadata& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::audio::V4_0::SourceMetadata& lhs, const ::android::hardware::audio::V4_0::SourceMetadata& rhs) {
    if (lhs.tracks != rhs.tracks) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::audio::V4_0::SourceMetadata& lhs, const ::android::hardware::audio::V4_0::SourceMetadata& rhs){
    return !(lhs == rhs);
}

static inline std::string toString(const ::android::hardware::audio::V4_0::RecordTrackMetadata& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".source = ";
    os += ::android::hardware::audio::common::V4_0::toString(o.source);
    os += ", .gain = ";
    os += ::android::hardware::toString(o.gain);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::audio::V4_0::RecordTrackMetadata& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::audio::V4_0::RecordTrackMetadata& lhs, const ::android::hardware::audio::V4_0::RecordTrackMetadata& rhs) {
    if (lhs.source != rhs.source) {
        return false;
    }
    if (lhs.gain != rhs.gain) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::audio::V4_0::RecordTrackMetadata& lhs, const ::android::hardware::audio::V4_0::RecordTrackMetadata& rhs){
    return !(lhs == rhs);
}

static inline std::string toString(const ::android::hardware::audio::V4_0::SinkMetadata& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".tracks = ";
    os += ::android::hardware::toString(o.tracks);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::audio::V4_0::SinkMetadata& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::audio::V4_0::SinkMetadata& lhs, const ::android::hardware::audio::V4_0::SinkMetadata& rhs) {
    if (lhs.tracks != rhs.tracks) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::audio::V4_0::SinkMetadata& lhs, const ::android::hardware::audio::V4_0::SinkMetadata& rhs){
    return !(lhs == rhs);
}

static inline std::string toString(const ::android::hardware::audio::V4_0::AudioMicrophoneCoordinate& o) {
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

static inline void PrintTo(const ::android::hardware::audio::V4_0::AudioMicrophoneCoordinate& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::audio::V4_0::AudioMicrophoneCoordinate& lhs, const ::android::hardware::audio::V4_0::AudioMicrophoneCoordinate& rhs) {
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

static inline bool operator!=(const ::android::hardware::audio::V4_0::AudioMicrophoneCoordinate& lhs, const ::android::hardware::audio::V4_0::AudioMicrophoneCoordinate& rhs){
    return !(lhs == rhs);
}

template<>
inline std::string toString<::android::hardware::audio::V4_0::AudioMicrophoneChannelMapping>(uint32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::audio::V4_0::AudioMicrophoneChannelMapping> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::audio::V4_0::AudioMicrophoneChannelMapping::UNUSED) == static_cast<uint32_t>(::android::hardware::audio::V4_0::AudioMicrophoneChannelMapping::UNUSED)) {
        os += (first ? "" : " | ");
        os += "UNUSED";
        first = false;
        flipped |= ::android::hardware::audio::V4_0::AudioMicrophoneChannelMapping::UNUSED;
    }
    if ((o & ::android::hardware::audio::V4_0::AudioMicrophoneChannelMapping::DIRECT) == static_cast<uint32_t>(::android::hardware::audio::V4_0::AudioMicrophoneChannelMapping::DIRECT)) {
        os += (first ? "" : " | ");
        os += "DIRECT";
        first = false;
        flipped |= ::android::hardware::audio::V4_0::AudioMicrophoneChannelMapping::DIRECT;
    }
    if ((o & ::android::hardware::audio::V4_0::AudioMicrophoneChannelMapping::PROCESSED) == static_cast<uint32_t>(::android::hardware::audio::V4_0::AudioMicrophoneChannelMapping::PROCESSED)) {
        os += (first ? "" : " | ");
        os += "PROCESSED";
        first = false;
        flipped |= ::android::hardware::audio::V4_0::AudioMicrophoneChannelMapping::PROCESSED;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::audio::V4_0::AudioMicrophoneChannelMapping o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::audio::V4_0::AudioMicrophoneChannelMapping::UNUSED) {
        return "UNUSED";
    }
    if (o == ::android::hardware::audio::V4_0::AudioMicrophoneChannelMapping::DIRECT) {
        return "DIRECT";
    }
    if (o == ::android::hardware::audio::V4_0::AudioMicrophoneChannelMapping::PROCESSED) {
        return "PROCESSED";
    }
    std::string os;
    os += toHexString(static_cast<uint32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::audio::V4_0::AudioMicrophoneChannelMapping o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::audio::V4_0::AudioMicrophoneLocation>(uint32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::audio::V4_0::AudioMicrophoneLocation> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::audio::V4_0::AudioMicrophoneLocation::UNKNOWN) == static_cast<uint32_t>(::android::hardware::audio::V4_0::AudioMicrophoneLocation::UNKNOWN)) {
        os += (first ? "" : " | ");
        os += "UNKNOWN";
        first = false;
        flipped |= ::android::hardware::audio::V4_0::AudioMicrophoneLocation::UNKNOWN;
    }
    if ((o & ::android::hardware::audio::V4_0::AudioMicrophoneLocation::MAINBODY) == static_cast<uint32_t>(::android::hardware::audio::V4_0::AudioMicrophoneLocation::MAINBODY)) {
        os += (first ? "" : " | ");
        os += "MAINBODY";
        first = false;
        flipped |= ::android::hardware::audio::V4_0::AudioMicrophoneLocation::MAINBODY;
    }
    if ((o & ::android::hardware::audio::V4_0::AudioMicrophoneLocation::MAINBODY_MOVABLE) == static_cast<uint32_t>(::android::hardware::audio::V4_0::AudioMicrophoneLocation::MAINBODY_MOVABLE)) {
        os += (first ? "" : " | ");
        os += "MAINBODY_MOVABLE";
        first = false;
        flipped |= ::android::hardware::audio::V4_0::AudioMicrophoneLocation::MAINBODY_MOVABLE;
    }
    if ((o & ::android::hardware::audio::V4_0::AudioMicrophoneLocation::PERIPHERAL) == static_cast<uint32_t>(::android::hardware::audio::V4_0::AudioMicrophoneLocation::PERIPHERAL)) {
        os += (first ? "" : " | ");
        os += "PERIPHERAL";
        first = false;
        flipped |= ::android::hardware::audio::V4_0::AudioMicrophoneLocation::PERIPHERAL;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::audio::V4_0::AudioMicrophoneLocation o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::audio::V4_0::AudioMicrophoneLocation::UNKNOWN) {
        return "UNKNOWN";
    }
    if (o == ::android::hardware::audio::V4_0::AudioMicrophoneLocation::MAINBODY) {
        return "MAINBODY";
    }
    if (o == ::android::hardware::audio::V4_0::AudioMicrophoneLocation::MAINBODY_MOVABLE) {
        return "MAINBODY_MOVABLE";
    }
    if (o == ::android::hardware::audio::V4_0::AudioMicrophoneLocation::PERIPHERAL) {
        return "PERIPHERAL";
    }
    std::string os;
    os += toHexString(static_cast<uint32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::audio::V4_0::AudioMicrophoneLocation o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::audio::V4_0::AudioMicrophoneDirectionality>(uint32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::audio::V4_0::AudioMicrophoneDirectionality> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::audio::V4_0::AudioMicrophoneDirectionality::UNKNOWN) == static_cast<uint32_t>(::android::hardware::audio::V4_0::AudioMicrophoneDirectionality::UNKNOWN)) {
        os += (first ? "" : " | ");
        os += "UNKNOWN";
        first = false;
        flipped |= ::android::hardware::audio::V4_0::AudioMicrophoneDirectionality::UNKNOWN;
    }
    if ((o & ::android::hardware::audio::V4_0::AudioMicrophoneDirectionality::OMNI) == static_cast<uint32_t>(::android::hardware::audio::V4_0::AudioMicrophoneDirectionality::OMNI)) {
        os += (first ? "" : " | ");
        os += "OMNI";
        first = false;
        flipped |= ::android::hardware::audio::V4_0::AudioMicrophoneDirectionality::OMNI;
    }
    if ((o & ::android::hardware::audio::V4_0::AudioMicrophoneDirectionality::BI_DIRECTIONAL) == static_cast<uint32_t>(::android::hardware::audio::V4_0::AudioMicrophoneDirectionality::BI_DIRECTIONAL)) {
        os += (first ? "" : " | ");
        os += "BI_DIRECTIONAL";
        first = false;
        flipped |= ::android::hardware::audio::V4_0::AudioMicrophoneDirectionality::BI_DIRECTIONAL;
    }
    if ((o & ::android::hardware::audio::V4_0::AudioMicrophoneDirectionality::CARDIOID) == static_cast<uint32_t>(::android::hardware::audio::V4_0::AudioMicrophoneDirectionality::CARDIOID)) {
        os += (first ? "" : " | ");
        os += "CARDIOID";
        first = false;
        flipped |= ::android::hardware::audio::V4_0::AudioMicrophoneDirectionality::CARDIOID;
    }
    if ((o & ::android::hardware::audio::V4_0::AudioMicrophoneDirectionality::HYPER_CARDIOID) == static_cast<uint32_t>(::android::hardware::audio::V4_0::AudioMicrophoneDirectionality::HYPER_CARDIOID)) {
        os += (first ? "" : " | ");
        os += "HYPER_CARDIOID";
        first = false;
        flipped |= ::android::hardware::audio::V4_0::AudioMicrophoneDirectionality::HYPER_CARDIOID;
    }
    if ((o & ::android::hardware::audio::V4_0::AudioMicrophoneDirectionality::SUPER_CARDIOID) == static_cast<uint32_t>(::android::hardware::audio::V4_0::AudioMicrophoneDirectionality::SUPER_CARDIOID)) {
        os += (first ? "" : " | ");
        os += "SUPER_CARDIOID";
        first = false;
        flipped |= ::android::hardware::audio::V4_0::AudioMicrophoneDirectionality::SUPER_CARDIOID;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::audio::V4_0::AudioMicrophoneDirectionality o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::audio::V4_0::AudioMicrophoneDirectionality::UNKNOWN) {
        return "UNKNOWN";
    }
    if (o == ::android::hardware::audio::V4_0::AudioMicrophoneDirectionality::OMNI) {
        return "OMNI";
    }
    if (o == ::android::hardware::audio::V4_0::AudioMicrophoneDirectionality::BI_DIRECTIONAL) {
        return "BI_DIRECTIONAL";
    }
    if (o == ::android::hardware::audio::V4_0::AudioMicrophoneDirectionality::CARDIOID) {
        return "CARDIOID";
    }
    if (o == ::android::hardware::audio::V4_0::AudioMicrophoneDirectionality::HYPER_CARDIOID) {
        return "HYPER_CARDIOID";
    }
    if (o == ::android::hardware::audio::V4_0::AudioMicrophoneDirectionality::SUPER_CARDIOID) {
        return "SUPER_CARDIOID";
    }
    std::string os;
    os += toHexString(static_cast<uint32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::audio::V4_0::AudioMicrophoneDirectionality o, ::std::ostream* os) {
    *os << toString(o);
}

static inline std::string toString(const ::android::hardware::audio::V4_0::AudioFrequencyResponsePoint& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".frequency = ";
    os += ::android::hardware::toString(o.frequency);
    os += ", .level = ";
    os += ::android::hardware::toString(o.level);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::audio::V4_0::AudioFrequencyResponsePoint& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::audio::V4_0::AudioFrequencyResponsePoint& lhs, const ::android::hardware::audio::V4_0::AudioFrequencyResponsePoint& rhs) {
    if (lhs.frequency != rhs.frequency) {
        return false;
    }
    if (lhs.level != rhs.level) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::audio::V4_0::AudioFrequencyResponsePoint& lhs, const ::android::hardware::audio::V4_0::AudioFrequencyResponsePoint& rhs){
    return !(lhs == rhs);
}

static inline std::string toString(const ::android::hardware::audio::V4_0::MicrophoneInfo& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".deviceId = ";
    os += ::android::hardware::toString(o.deviceId);
    os += ", .deviceAddress = ";
    os += ::android::hardware::audio::V4_0::toString(o.deviceAddress);
    os += ", .channelMapping = ";
    os += ::android::hardware::toString(o.channelMapping);
    os += ", .location = ";
    os += ::android::hardware::audio::V4_0::toString(o.location);
    os += ", .group = ";
    os += ::android::hardware::toString(o.group);
    os += ", .indexInTheGroup = ";
    os += ::android::hardware::toString(o.indexInTheGroup);
    os += ", .sensitivity = ";
    os += ::android::hardware::toString(o.sensitivity);
    os += ", .maxSpl = ";
    os += ::android::hardware::toString(o.maxSpl);
    os += ", .minSpl = ";
    os += ::android::hardware::toString(o.minSpl);
    os += ", .directionality = ";
    os += ::android::hardware::audio::V4_0::toString(o.directionality);
    os += ", .frequencyResponse = ";
    os += ::android::hardware::toString(o.frequencyResponse);
    os += ", .position = ";
    os += ::android::hardware::audio::V4_0::toString(o.position);
    os += ", .orientation = ";
    os += ::android::hardware::audio::V4_0::toString(o.orientation);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::audio::V4_0::MicrophoneInfo& o, ::std::ostream* os) {
    *os << toString(o);
}

// operator== and operator!= are not generated for MicrophoneInfo


}  // namespace V4_0
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
template<> inline constexpr std::array<::android::hardware::audio::V4_0::Result, 5> hidl_enum_values<::android::hardware::audio::V4_0::Result> = {
    ::android::hardware::audio::V4_0::Result::OK,
    ::android::hardware::audio::V4_0::Result::NOT_INITIALIZED,
    ::android::hardware::audio::V4_0::Result::INVALID_ARGUMENTS,
    ::android::hardware::audio::V4_0::Result::INVALID_STATE,
    ::android::hardware::audio::V4_0::Result::NOT_SUPPORTED,
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
template<> inline constexpr std::array<::android::hardware::audio::V4_0::AudioDrain, 2> hidl_enum_values<::android::hardware::audio::V4_0::AudioDrain> = {
    ::android::hardware::audio::V4_0::AudioDrain::ALL,
    ::android::hardware::audio::V4_0::AudioDrain::EARLY_NOTIFY,
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
template<> inline constexpr std::array<::android::hardware::audio::V4_0::MmapBufferFlag, 2> hidl_enum_values<::android::hardware::audio::V4_0::MmapBufferFlag> = {
    ::android::hardware::audio::V4_0::MmapBufferFlag::NONE,
    ::android::hardware::audio::V4_0::MmapBufferFlag::APPLICATION_SHAREABLE,
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
template<> inline constexpr std::array<::android::hardware::audio::V4_0::MessageQueueFlagBits, 2> hidl_enum_values<::android::hardware::audio::V4_0::MessageQueueFlagBits> = {
    ::android::hardware::audio::V4_0::MessageQueueFlagBits::NOT_EMPTY,
    ::android::hardware::audio::V4_0::MessageQueueFlagBits::NOT_FULL,
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
template<> inline constexpr std::array<::android::hardware::audio::V4_0::AudioMicrophoneChannelMapping, 3> hidl_enum_values<::android::hardware::audio::V4_0::AudioMicrophoneChannelMapping> = {
    ::android::hardware::audio::V4_0::AudioMicrophoneChannelMapping::UNUSED,
    ::android::hardware::audio::V4_0::AudioMicrophoneChannelMapping::DIRECT,
    ::android::hardware::audio::V4_0::AudioMicrophoneChannelMapping::PROCESSED,
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
template<> inline constexpr std::array<::android::hardware::audio::V4_0::AudioMicrophoneLocation, 4> hidl_enum_values<::android::hardware::audio::V4_0::AudioMicrophoneLocation> = {
    ::android::hardware::audio::V4_0::AudioMicrophoneLocation::UNKNOWN,
    ::android::hardware::audio::V4_0::AudioMicrophoneLocation::MAINBODY,
    ::android::hardware::audio::V4_0::AudioMicrophoneLocation::MAINBODY_MOVABLE,
    ::android::hardware::audio::V4_0::AudioMicrophoneLocation::PERIPHERAL,
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
template<> inline constexpr std::array<::android::hardware::audio::V4_0::AudioMicrophoneDirectionality, 6> hidl_enum_values<::android::hardware::audio::V4_0::AudioMicrophoneDirectionality> = {
    ::android::hardware::audio::V4_0::AudioMicrophoneDirectionality::UNKNOWN,
    ::android::hardware::audio::V4_0::AudioMicrophoneDirectionality::OMNI,
    ::android::hardware::audio::V4_0::AudioMicrophoneDirectionality::BI_DIRECTIONAL,
    ::android::hardware::audio::V4_0::AudioMicrophoneDirectionality::CARDIOID,
    ::android::hardware::audio::V4_0::AudioMicrophoneDirectionality::HYPER_CARDIOID,
    ::android::hardware::audio::V4_0::AudioMicrophoneDirectionality::SUPER_CARDIOID,
};
#pragma clang diagnostic pop
}  // namespace details
}  // namespace hardware
}  // namespace android


#endif  // HIDL_GENERATED_ANDROID_HARDWARE_AUDIO_V4_0_TYPES_H
