#ifndef HIDL_GENERATED_ANDROID_HARDWARE_AUDIO_COMMON_V2_0_TYPES_H
#define HIDL_GENERATED_ANDROID_HARDWARE_AUDIO_COMMON_V2_0_TYPES_H

#include <hidl/HidlSupport.h>
#include <hidl/MQDescriptor.h>
#include <utils/NativeHandle.h>
#include <utils/misc.h>

namespace android {
namespace hardware {
namespace audio {
namespace common {
namespace V2_0 {

// Forward declaration for forward reference support:
enum class AudioHandleConsts : int32_t;
struct Uuid;
enum class AudioStreamType : int32_t;
enum class AudioSource : int32_t;
enum class AudioSessionConsts : int32_t;
enum class AudioFormat : uint32_t;
enum class FixedChannelCount : int32_t;
enum class AudioChannelMask : uint32_t;
enum class AudioInterleave : int32_t;
enum class AudioMode : int32_t;
enum class AudioDevice : uint32_t;
enum class AudioOutputFlag : int32_t;
enum class AudioInputFlag : int32_t;
enum class AudioUsage : int32_t;
struct AudioOffloadInfo;
struct AudioConfig;
enum class AudioGainMode : uint32_t;
struct AudioGain;
struct AudioGainConfig;
enum class AudioPortRole : int32_t;
enum class AudioPortType : int32_t;
struct AudioPortConfigDeviceExt;
struct AudioPortConfigSessionExt;
enum class AudioPortConfigMask : uint32_t;
struct AudioPortConfig;
struct AudioPortDeviceExt;
enum class AudioMixLatencyClass : int32_t;
struct AudioPortMixExt;
struct AudioPortSessionExt;
struct AudioPort;
struct ThreadInfo;

/**
 *  IDs and Handles
 *
 *
 *
 * Handle type for identifying audio sources and sinks.
 */
typedef int32_t AudioIoHandle;

/**
 * Audio hw module handle functions or structures referencing a module.
 */
typedef int32_t AudioModuleHandle;

/**
 * Each port has a unique ID or handle allocated by policy manager.
 */
typedef int32_t AudioPortHandle;

/**
 * Each patch is identified by a handle at the interface used to create that
 * patch. For instance, when a patch is created by the audio HAL, the HAL
 * allocates and returns a handle.  This handle is unique to a given audio HAL
 * hardware module.  But the same patch receives another system wide unique
 * handle allocated by the framework.  This unique handle is used for all
 * transactions inside the framework.
 */
typedef int32_t AudioPatchHandle;

/**
 * A HW synchronization source returned by the audio HAL.
 */
typedef uint32_t AudioHwSync;

/**
 * Each port has a unique ID or handle allocated by policy manager.
 */
enum class AudioHandleConsts : int32_t {
    AUDIO_IO_HANDLE_NONE = 0,
    AUDIO_MODULE_HANDLE_NONE = 0,
    AUDIO_PORT_HANDLE_NONE = 0,
    AUDIO_PATCH_HANDLE_NONE = 0,
};

/**
 * Commonly used structure for passing unique identifieds (UUID).
 * For the definition of UUID, refer to ITU-T X.667 spec.
 */
struct Uuid final {
    uint32_t timeLow __attribute__ ((aligned(4)));
    uint16_t timeMid __attribute__ ((aligned(2)));
    uint16_t versionAndTimeHigh __attribute__ ((aligned(2)));
    uint16_t variantAndClockSeqHigh __attribute__ ((aligned(2)));
    ::android::hardware::hidl_array<uint8_t, 6> node __attribute__ ((aligned(1)));
};

static_assert(offsetof(::android::hardware::audio::common::V2_0::Uuid, timeLow) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::audio::common::V2_0::Uuid, timeMid) == 4, "wrong offset");
static_assert(offsetof(::android::hardware::audio::common::V2_0::Uuid, versionAndTimeHigh) == 6, "wrong offset");
static_assert(offsetof(::android::hardware::audio::common::V2_0::Uuid, variantAndClockSeqHigh) == 8, "wrong offset");
static_assert(offsetof(::android::hardware::audio::common::V2_0::Uuid, node) == 10, "wrong offset");
static_assert(sizeof(::android::hardware::audio::common::V2_0::Uuid) == 16, "wrong size");
static_assert(__alignof(::android::hardware::audio::common::V2_0::Uuid) == 4, "wrong alignment");

/**
 *  Audio streams
 *
 *
 *
 * Audio stream type describing the intented use case of a stream.
 */
enum class AudioStreamType : int32_t {
    DEFAULT = -1 /* -1 */,
    MIN = 0,
    VOICE_CALL = 0,
    SYSTEM = 1,
    RING = 2,
    MUSIC = 3,
    ALARM = 4,
    NOTIFICATION = 5,
    BLUETOOTH_SCO = 6,
    ENFORCED_AUDIBLE = 7,
    DTMF = 8,
    TTS = 9,
    ACCESSIBILITY = 10,
    REROUTING = 11,
    PATCH = 12,
    PUBLIC_CNT = 11 /* ACCESSIBILITY + 1 */,
    FOR_POLICY_CNT = 12 /* PATCH */,
    CNT = 13 /* PATCH + 1 */,
};

enum class AudioSource : int32_t {
    DEFAULT = 0,
    MIC = 1,
    VOICE_UPLINK = 2,
    VOICE_DOWNLINK = 3,
    VOICE_CALL = 4,
    CAMCORDER = 5,
    VOICE_RECOGNITION = 6,
    VOICE_COMMUNICATION = 7,
    /**
     * Source for the mix to be presented remotely. An example of remote
     * presentation is Wifi Display where a dongle attached to a TV can be used
     * to play the mix captured by this audio source.
     */
    REMOTE_SUBMIX = 8,
    /**
     * Source for unprocessed sound. Usage examples include level measurement
     * and raw signal analysis.
     */
    UNPROCESSED = 9,
    CNT = 10 /* ::android::hardware::audio::common::V2_0::AudioSource.UNPROCESSED implicitly + 1 */,
    MAX = 9 /* CNT - 1 */,
    FM_TUNER = 1998,
    /**
     * A low-priority, preemptible audio source for for background software
     * hotword detection. Same tuning as VOICE_RECOGNITION.  Used only
     * internally by the framework.
     */
    HOTWORD = 1999,
};

typedef int32_t AudioSession;

/**
 * Special audio session values.
 */
enum class AudioSessionConsts : int32_t {
    /**
     * Session for effects attached to a particular output stream
     * (value must be less than 0)
     */
    OUTPUT_STAGE = -1 /* -1 */,
    /**
     * Session for effects applied to output mix. These effects can
     * be moved by audio policy manager to another output stream
     * (value must be 0)
     */
    OUTPUT_MIX = 0,
    /**
     * Application does not specify an explicit session ID to be used, and
     * requests a new session ID to be allocated. Corresponds to
     * AudioManager.AUDIO_SESSION_ID_GENERATE and
     * AudioSystem.AUDIO_SESSION_ALLOCATE.
     */
    ALLOCATE = 0,
    /**
     * For use with AudioRecord::start(), this indicates no trigger session.
     * It is also used with output tracks and patch tracks, which never have a
     * session.
     */
    NONE = 0,
};

/**
 * Audio format  is a 32-bit word that consists of:
 *   main format field (upper 8 bits)
 *   sub format field (lower 24 bits).
 *
 * The main format indicates the main codec type. The sub format field indicates
 * options and parameters for each format. The sub format is mainly used for
 * record to indicate for instance the requested bitrate or profile.  It can
 * also be used for certain formats to give informations not present in the
 * encoded audio stream (e.g. octet alignement for AMR).
 */
enum class AudioFormat : uint32_t {
    INVALID = 4294967295u /* 0xFFFFFFFFUL */,
    DEFAULT = 0u,
    PCM = 0u /* 0x00000000UL */,
    /**
     * DO NOT CHANGE
     */
    MP3 = 16777216u /* 0x01000000UL */,
    AMR_NB = 33554432u /* 0x02000000UL */,
    AMR_WB = 50331648u /* 0x03000000UL */,
    AAC = 67108864u /* 0x04000000UL */,
    HE_AAC_V1 = 83886080u /* 0x05000000UL */,
    /**
     * Deprecated, Use AAC_HE_V1
     */
    HE_AAC_V2 = 100663296u /* 0x06000000UL */,
    /**
     * Deprecated, Use AAC_HE_V2
     */
    VORBIS = 117440512u /* 0x07000000UL */,
    OPUS = 134217728u /* 0x08000000UL */,
    AC3 = 150994944u /* 0x09000000UL */,
    E_AC3 = 167772160u /* 0x0A000000UL */,
    DTS = 184549376u /* 0x0B000000UL */,
    DTS_HD = 201326592u /* 0x0C000000UL */,
    IEC61937 = 218103808u /* 0x0D000000UL */,
    DOLBY_TRUEHD = 234881024u /* 0x0E000000UL */,
    EVRC = 268435456u /* 0x10000000UL */,
    EVRCB = 285212672u /* 0x11000000UL */,
    EVRCWB = 301989888u /* 0x12000000UL */,
    EVRCNW = 318767104u /* 0x13000000UL */,
    AAC_ADIF = 335544320u /* 0x14000000UL */,
    WMA = 352321536u /* 0x15000000UL */,
    WMA_PRO = 369098752u /* 0x16000000UL */,
    AMR_WB_PLUS = 385875968u /* 0x17000000UL */,
    MP2 = 402653184u /* 0x18000000UL */,
    QCELP = 419430400u /* 0x19000000UL */,
    DSD = 436207616u /* 0x1A000000UL */,
    FLAC = 452984832u /* 0x1B000000UL */,
    ALAC = 469762048u /* 0x1C000000UL */,
    APE = 486539264u /* 0x1D000000UL */,
    AAC_ADTS = 503316480u /* 0x1E000000UL */,
    SBC = 520093696u /* 0x1F000000UL */,
    APTX = 536870912u /* 0x20000000UL */,
    APTX_HD = 553648128u /* 0x21000000UL */,
    AC4 = 570425344u /* 0x22000000UL */,
    LDAC = 587202560u /* 0x23000000UL */,
    MAIN_MASK = 4278190080u /* 0xFF000000UL */,
    /**
     * Deprecated
     */
    SUB_MASK = 16777215u /* 0x00FFFFFFUL */,
    /**
     * Subformats
     */
    PCM_SUB_16_BIT = 1u /* 0x1 */,
    PCM_SUB_8_BIT = 2u /* 0x2 */,
    PCM_SUB_32_BIT = 3u /* 0x3 */,
    PCM_SUB_8_24_BIT = 4u /* 0x4 */,
    PCM_SUB_FLOAT = 5u /* 0x5 */,
    PCM_SUB_24_BIT_PACKED = 6u /* 0x6 */,
    MP3_SUB_NONE = 0u /* 0x0 */,
    AMR_SUB_NONE = 0u /* 0x0 */,
    AAC_SUB_MAIN = 1u /* 0x1 */,
    AAC_SUB_LC = 2u /* 0x2 */,
    AAC_SUB_SSR = 4u /* 0x4 */,
    AAC_SUB_LTP = 8u /* 0x8 */,
    AAC_SUB_HE_V1 = 16u /* 0x10 */,
    AAC_SUB_SCALABLE = 32u /* 0x20 */,
    AAC_SUB_ERLC = 64u /* 0x40 */,
    AAC_SUB_LD = 128u /* 0x80 */,
    AAC_SUB_HE_V2 = 256u /* 0x100 */,
    AAC_SUB_ELD = 512u /* 0x200 */,
    VORBIS_SUB_NONE = 0u /* 0x0 */,
    /**
     * Aliases
     *
     *
     * note != AudioFormat.ENCODING_PCM_16BIT
     */
    PCM_16_BIT = 1u /* (PCM | PCM_SUB_16_BIT) */,
    /**
     * note != AudioFormat.ENCODING_PCM_8BIT
     */
    PCM_8_BIT = 2u /* (PCM | PCM_SUB_8_BIT) */,
    PCM_32_BIT = 3u /* (PCM | PCM_SUB_32_BIT) */,
    PCM_8_24_BIT = 4u /* (PCM | PCM_SUB_8_24_BIT) */,
    PCM_FLOAT = 5u /* (PCM | PCM_SUB_FLOAT) */,
    PCM_24_BIT_PACKED = 6u /* (PCM | PCM_SUB_24_BIT_PACKED) */,
    AAC_MAIN = 67108865u /* (AAC | AAC_SUB_MAIN) */,
    AAC_LC = 67108866u /* (AAC | AAC_SUB_LC) */,
    AAC_SSR = 67108868u /* (AAC | AAC_SUB_SSR) */,
    AAC_LTP = 67108872u /* (AAC | AAC_SUB_LTP) */,
    AAC_HE_V1 = 67108880u /* (AAC | AAC_SUB_HE_V1) */,
    AAC_SCALABLE = 67108896u /* (AAC | AAC_SUB_SCALABLE) */,
    AAC_ERLC = 67108928u /* (AAC | AAC_SUB_ERLC) */,
    AAC_LD = 67108992u /* (AAC | AAC_SUB_LD) */,
    AAC_HE_V2 = 67109120u /* (AAC | AAC_SUB_HE_V2) */,
    AAC_ELD = 67109376u /* (AAC | AAC_SUB_ELD) */,
    AAC_ADTS_MAIN = 503316481u /* (AAC_ADTS | AAC_SUB_MAIN) */,
    AAC_ADTS_LC = 503316482u /* (AAC_ADTS | AAC_SUB_LC) */,
    AAC_ADTS_SSR = 503316484u /* (AAC_ADTS | AAC_SUB_SSR) */,
    AAC_ADTS_LTP = 503316488u /* (AAC_ADTS | AAC_SUB_LTP) */,
    AAC_ADTS_HE_V1 = 503316496u /* (AAC_ADTS | AAC_SUB_HE_V1) */,
    AAC_ADTS_SCALABLE = 503316512u /* (AAC_ADTS | AAC_SUB_SCALABLE) */,
    AAC_ADTS_ERLC = 503316544u /* (AAC_ADTS | AAC_SUB_ERLC) */,
    AAC_ADTS_LD = 503316608u /* (AAC_ADTS | AAC_SUB_LD) */,
    AAC_ADTS_HE_V2 = 503316736u /* (AAC_ADTS | AAC_SUB_HE_V2) */,
    AAC_ADTS_ELD = 503316992u /* (AAC_ADTS | AAC_SUB_ELD) */,
};

/**
 * Usage of these values highlights places in the code that use 2- or 8- channel
 * assumptions.
 */
enum class FixedChannelCount : int32_t {
    FCC_2 = 2,
    FCC_8 = 8,
};

/**
 * A channel mask per se only defines the presence or absence of a channel, not
 * the order.  See AUDIO_INTERLEAVE_* for the platform convention of order.
 *
 * AudioChannelMask is an opaque type and its internal layout should not be
 * assumed as it may change in the future.  Instead, always use functions
 * to examine it.
 *
 * These are the current representations:
 *
 *   REPRESENTATION_POSITION
 *     is a channel mask representation for position assignment.  Each low-order
 *     bit corresponds to the spatial position of a transducer (output), or
 *     interpretation of channel (input).  The user of a channel mask needs to
 *     know the context of whether it is for output or input.  The constants
 *     OUT_* or IN_* apply to the bits portion.  It is not permitted for no bits
 *     to be set.
 *
 *   REPRESENTATION_INDEX
 *     is a channel mask representation for index assignment.  Each low-order
 *     bit corresponds to a selected channel.  There is no platform
 *     interpretation of the various bits.  There is no concept of output or
 *     input.  It is not permitted for no bits to be set.
 *
 * All other representations are reserved for future use.
 *
 * Warning: current representation distinguishes between input and output, but
 * this will not the be case in future revisions of the platform. Wherever there
 * is an ambiguity between input and output that is currently resolved by
 * checking the channel mask, the implementer should look for ways to fix it
 * with additional information outside of the mask.
 */
enum class AudioChannelMask : uint32_t {
    REPRESENTATION_POSITION = 0u,
    /**
     * must be 0 for compatibility
     *
     *
     * 1 is reserved for future use
     */
    REPRESENTATION_INDEX = 2u,
    /**
     * 3 is reserved for future use
     *
     *
     * These can be a complete value of AudioChannelMask
     */
    NONE = 0u /* 0x0 */,
    INVALID = 3221225472u /* 0xC0000000 */,
    /**
     * These can be the bits portion of an AudioChannelMask
     * with representation REPRESENTATION_POSITION.
     *
     *
     * output channels
     */
    OUT_FRONT_LEFT = 1u /* 0x1 */,
    OUT_FRONT_RIGHT = 2u /* 0x2 */,
    OUT_FRONT_CENTER = 4u /* 0x4 */,
    OUT_LOW_FREQUENCY = 8u /* 0x8 */,
    OUT_BACK_LEFT = 16u /* 0x10 */,
    OUT_BACK_RIGHT = 32u /* 0x20 */,
    OUT_FRONT_LEFT_OF_CENTER = 64u /* 0x40 */,
    OUT_FRONT_RIGHT_OF_CENTER = 128u /* 0x80 */,
    OUT_BACK_CENTER = 256u /* 0x100 */,
    OUT_SIDE_LEFT = 512u /* 0x200 */,
    OUT_SIDE_RIGHT = 1024u /* 0x400 */,
    OUT_TOP_CENTER = 2048u /* 0x800 */,
    OUT_TOP_FRONT_LEFT = 4096u /* 0x1000 */,
    OUT_TOP_FRONT_CENTER = 8192u /* 0x2000 */,
    OUT_TOP_FRONT_RIGHT = 16384u /* 0x4000 */,
    OUT_TOP_BACK_LEFT = 32768u /* 0x8000 */,
    OUT_TOP_BACK_CENTER = 65536u /* 0x10000 */,
    OUT_TOP_BACK_RIGHT = 131072u /* 0x20000 */,
    OUT_MONO = 1u /* OUT_FRONT_LEFT */,
    OUT_STEREO = 3u /* (OUT_FRONT_LEFT | OUT_FRONT_RIGHT) */,
    OUT_2POINT1 = 11u /* (OUT_FRONT_LEFT | OUT_FRONT_RIGHT | OUT_LOW_FREQUENCY) */,
    OUT_QUAD = 51u /* (OUT_FRONT_LEFT | OUT_FRONT_RIGHT | OUT_BACK_LEFT | OUT_BACK_RIGHT) */,
    OUT_QUAD_BACK = 51u /* OUT_QUAD */,
    /**
     * like OUT_QUAD_BACK with *_SIDE_* instead of *_BACK_*
     */
    OUT_QUAD_SIDE = 1539u /* (OUT_FRONT_LEFT | OUT_FRONT_RIGHT | OUT_SIDE_LEFT | OUT_SIDE_RIGHT) */,
    OUT_SURROUND = 263u /* (OUT_FRONT_LEFT | OUT_FRONT_RIGHT | OUT_FRONT_CENTER | OUT_BACK_CENTER) */,
    OUT_PENTA = 55u /* (OUT_QUAD | OUT_FRONT_CENTER) */,
    OUT_5POINT1 = 63u /* (OUT_FRONT_LEFT | OUT_FRONT_RIGHT | OUT_FRONT_CENTER | OUT_LOW_FREQUENCY | OUT_BACK_LEFT | OUT_BACK_RIGHT) */,
    OUT_5POINT1_BACK = 63u /* OUT_5POINT1 */,
    /**
     * like OUT_5POINT1_BACK with *_SIDE_* instead of *_BACK_*
     */
    OUT_5POINT1_SIDE = 1551u /* (OUT_FRONT_LEFT | OUT_FRONT_RIGHT | OUT_FRONT_CENTER | OUT_LOW_FREQUENCY | OUT_SIDE_LEFT | OUT_SIDE_RIGHT) */,
    OUT_6POINT1 = 319u /* (OUT_FRONT_LEFT | OUT_FRONT_RIGHT | OUT_FRONT_CENTER | OUT_LOW_FREQUENCY | OUT_BACK_LEFT | OUT_BACK_RIGHT | OUT_BACK_CENTER) */,
    /**
     * matches the correct AudioFormat.CHANNEL_OUT_7POINT1_SURROUND
     */
    OUT_7POINT1 = 1599u /* (OUT_FRONT_LEFT | OUT_FRONT_RIGHT | OUT_FRONT_CENTER | OUT_LOW_FREQUENCY | OUT_BACK_LEFT | OUT_BACK_RIGHT | OUT_SIDE_LEFT | OUT_SIDE_RIGHT) */,
    OUT_ALL = 262143u /* (OUT_FRONT_LEFT | OUT_FRONT_RIGHT | OUT_FRONT_CENTER | OUT_LOW_FREQUENCY | OUT_BACK_LEFT | OUT_BACK_RIGHT | OUT_FRONT_LEFT_OF_CENTER | OUT_FRONT_RIGHT_OF_CENTER | OUT_BACK_CENTER | OUT_SIDE_LEFT | OUT_SIDE_RIGHT | OUT_TOP_CENTER | OUT_TOP_FRONT_LEFT | OUT_TOP_FRONT_CENTER | OUT_TOP_FRONT_RIGHT | OUT_TOP_BACK_LEFT | OUT_TOP_BACK_CENTER | OUT_TOP_BACK_RIGHT) */,
    /**
     * These are bits only, not complete values
     *
     *
     * input channels
     */
    IN_LEFT = 4u /* 0x4 */,
    IN_RIGHT = 8u /* 0x8 */,
    IN_FRONT = 16u /* 0x10 */,
    IN_BACK = 32u /* 0x20 */,
    IN_LEFT_PROCESSED = 64u /* 0x40 */,
    IN_RIGHT_PROCESSED = 128u /* 0x80 */,
    IN_FRONT_PROCESSED = 256u /* 0x100 */,
    IN_BACK_PROCESSED = 512u /* 0x200 */,
    IN_PRESSURE = 1024u /* 0x400 */,
    IN_X_AXIS = 2048u /* 0x800 */,
    IN_Y_AXIS = 4096u /* 0x1000 */,
    IN_Z_AXIS = 8192u /* 0x2000 */,
    IN_VOICE_UPLINK = 16384u /* 0x4000 */,
    IN_VOICE_DNLINK = 32768u /* 0x8000 */,
    IN_MONO = 16u /* IN_FRONT */,
    IN_STEREO = 12u /* (IN_LEFT | IN_RIGHT) */,
    IN_FRONT_BACK = 48u /* (IN_FRONT | IN_BACK) */,
    IN_6 = 252u /* (IN_LEFT | IN_RIGHT | IN_FRONT | IN_BACK | IN_LEFT_PROCESSED | IN_RIGHT_PROCESSED) */,
    IN_VOICE_UPLINK_MONO = 16400u /* (IN_VOICE_UPLINK | IN_MONO) */,
    IN_VOICE_DNLINK_MONO = 32784u /* (IN_VOICE_DNLINK | IN_MONO) */,
    IN_VOICE_CALL_MONO = 49168u /* (IN_VOICE_UPLINK_MONO | IN_VOICE_DNLINK_MONO) */,
    IN_ALL = 65532u /* (IN_LEFT | IN_RIGHT | IN_FRONT | IN_BACK | IN_LEFT_PROCESSED | IN_RIGHT_PROCESSED | IN_FRONT_PROCESSED | IN_BACK_PROCESSED | IN_PRESSURE | IN_X_AXIS | IN_Y_AXIS | IN_Z_AXIS | IN_VOICE_UPLINK | IN_VOICE_DNLINK) */,
    COUNT_MAX = 30u,
    INDEX_HDR = 2147483648u /* REPRESENTATION_INDEX << COUNT_MAX */,
    INDEX_MASK_1 = 2147483649u /* INDEX_HDR | ((1 << 1) - 1) */,
    INDEX_MASK_2 = 2147483651u /* INDEX_HDR | ((1 << 2) - 1) */,
    INDEX_MASK_3 = 2147483655u /* INDEX_HDR | ((1 << 3) - 1) */,
    INDEX_MASK_4 = 2147483663u /* INDEX_HDR | ((1 << 4) - 1) */,
    INDEX_MASK_5 = 2147483679u /* INDEX_HDR | ((1 << 5) - 1) */,
    INDEX_MASK_6 = 2147483711u /* INDEX_HDR | ((1 << 6) - 1) */,
    INDEX_MASK_7 = 2147483775u /* INDEX_HDR | ((1 << 7) - 1) */,
    INDEX_MASK_8 = 2147483903u /* INDEX_HDR | ((1 << 8) - 1) */,
};

/**
 * Expresses the convention when stereo audio samples are stored interleaved
 * in an array.  This should improve readability by allowing code to use
 * symbolic indices instead of hard-coded [0] and [1].
 *
 * For multi-channel beyond stereo, the platform convention is that channels
 * are interleaved in order from least significant channel mask bit to most
 * significant channel mask bit, with unused bits skipped.  Any exceptions
 * to this convention will be noted at the appropriate API.
 */
enum class AudioInterleave : int32_t {
    LEFT = 0,
    RIGHT = 1,
};

/**
 * Major modes for a mobile device. The current mode setting affects audio
 * routing.
 */
enum class AudioMode : int32_t {
    INVALID = -2 /* -2 */,
    CURRENT = -1 /* -1 */,
    NORMAL = 0,
    RINGTONE = 1,
    IN_CALL = 2,
    IN_COMMUNICATION = 3,
    CNT = 4 /* ::android::hardware::audio::common::V2_0::AudioMode.IN_COMMUNICATION implicitly + 1 */,
    MAX = 3 /* CNT - 1 */,
};

enum class AudioDevice : uint32_t {
    NONE = 0u /* 0x0 */,
    /**
     * reserved bits
     */
    BIT_IN = 2147483648u /* 0x80000000 */,
    BIT_DEFAULT = 1073741824u /* 0x40000000 */,
    /**
     * output devices
     */
    OUT_EARPIECE = 1u /* 0x1 */,
    OUT_SPEAKER = 2u /* 0x2 */,
    OUT_WIRED_HEADSET = 4u /* 0x4 */,
    OUT_WIRED_HEADPHONE = 8u /* 0x8 */,
    OUT_BLUETOOTH_SCO = 16u /* 0x10 */,
    OUT_BLUETOOTH_SCO_HEADSET = 32u /* 0x20 */,
    OUT_BLUETOOTH_SCO_CARKIT = 64u /* 0x40 */,
    OUT_BLUETOOTH_A2DP = 128u /* 0x80 */,
    OUT_BLUETOOTH_A2DP_HEADPHONES = 256u /* 0x100 */,
    OUT_BLUETOOTH_A2DP_SPEAKER = 512u /* 0x200 */,
    OUT_AUX_DIGITAL = 1024u /* 0x400 */,
    OUT_HDMI = 1024u /* OUT_AUX_DIGITAL */,
    /**
     * uses an analog connection (multiplexed over the USB pins for instance)
     */
    OUT_ANLG_DOCK_HEADSET = 2048u /* 0x800 */,
    OUT_DGTL_DOCK_HEADSET = 4096u /* 0x1000 */,
    /**
     * USB accessory mode: Android device is USB device and dock is USB host
     */
    OUT_USB_ACCESSORY = 8192u /* 0x2000 */,
    /**
     * USB host mode: Android device is USB host and dock is USB device
     */
    OUT_USB_DEVICE = 16384u /* 0x4000 */,
    OUT_REMOTE_SUBMIX = 32768u /* 0x8000 */,
    /**
     * Telephony voice TX path
     */
    OUT_TELEPHONY_TX = 65536u /* 0x10000 */,
    /**
     * Analog jack with line impedance detected
     */
    OUT_LINE = 131072u /* 0x20000 */,
    /**
     * HDMI Audio Return Channel
     */
    OUT_HDMI_ARC = 262144u /* 0x40000 */,
    /**
     * S/PDIF out
     */
    OUT_SPDIF = 524288u /* 0x80000 */,
    /**
     * FM transmitter out
     */
    OUT_FM = 1048576u /* 0x100000 */,
    /**
     * Line out for av devices
     */
    OUT_AUX_LINE = 2097152u /* 0x200000 */,
    /**
     * limited-output speaker device for acoustic safety
     */
    OUT_SPEAKER_SAFE = 4194304u /* 0x400000 */,
    OUT_IP = 8388608u /* 0x800000 */,
    /**
     * audio bus implemented by the audio system (e.g an MOST stereo channel)
     */
    OUT_BUS = 16777216u /* 0x1000000 */,
    OUT_PROXY = 33554432u /* 0x2000000 */,
    OUT_USB_HEADSET = 67108864u /* 0x4000000 */,
    OUT_DEFAULT = 1073741824u /* BIT_DEFAULT */,
    OUT_ALL = 1207959551u /* (OUT_EARPIECE | OUT_SPEAKER | OUT_WIRED_HEADSET | OUT_WIRED_HEADPHONE | OUT_BLUETOOTH_SCO | OUT_BLUETOOTH_SCO_HEADSET | OUT_BLUETOOTH_SCO_CARKIT | OUT_BLUETOOTH_A2DP | OUT_BLUETOOTH_A2DP_HEADPHONES | OUT_BLUETOOTH_A2DP_SPEAKER | OUT_HDMI | OUT_ANLG_DOCK_HEADSET | OUT_DGTL_DOCK_HEADSET | OUT_USB_ACCESSORY | OUT_USB_DEVICE | OUT_REMOTE_SUBMIX | OUT_TELEPHONY_TX | OUT_LINE | OUT_HDMI_ARC | OUT_SPDIF | OUT_FM | OUT_AUX_LINE | OUT_SPEAKER_SAFE | OUT_IP | OUT_BUS | OUT_PROXY | OUT_USB_HEADSET | OUT_DEFAULT) */,
    OUT_ALL_A2DP = 896u /* (OUT_BLUETOOTH_A2DP | OUT_BLUETOOTH_A2DP_HEADPHONES | OUT_BLUETOOTH_A2DP_SPEAKER) */,
    OUT_ALL_SCO = 112u /* (OUT_BLUETOOTH_SCO | OUT_BLUETOOTH_SCO_HEADSET | OUT_BLUETOOTH_SCO_CARKIT) */,
    OUT_ALL_USB = 67133440u /* (OUT_USB_ACCESSORY | OUT_USB_DEVICE | OUT_USB_HEADSET) */,
    /**
     * input devices
     */
    IN_COMMUNICATION = 2147483649u /* BIT_IN | 0x1 */,
    IN_AMBIENT = 2147483650u /* BIT_IN | 0x2 */,
    IN_BUILTIN_MIC = 2147483652u /* BIT_IN | 0x4 */,
    IN_BLUETOOTH_SCO_HEADSET = 2147483656u /* BIT_IN | 0x8 */,
    IN_WIRED_HEADSET = 2147483664u /* BIT_IN | 0x10 */,
    IN_AUX_DIGITAL = 2147483680u /* BIT_IN | 0x20 */,
    IN_HDMI = 2147483680u /* IN_AUX_DIGITAL */,
    /**
     * Telephony voice RX path
     */
    IN_VOICE_CALL = 2147483712u /* BIT_IN | 0x40 */,
    IN_TELEPHONY_RX = 2147483712u /* IN_VOICE_CALL */,
    IN_BACK_MIC = 2147483776u /* BIT_IN | 0x80 */,
    IN_REMOTE_SUBMIX = 2147483904u /* BIT_IN | 0x100 */,
    IN_ANLG_DOCK_HEADSET = 2147484160u /* BIT_IN | 0x200 */,
    IN_DGTL_DOCK_HEADSET = 2147484672u /* BIT_IN | 0x400 */,
    IN_USB_ACCESSORY = 2147485696u /* BIT_IN | 0x800 */,
    IN_USB_DEVICE = 2147487744u /* BIT_IN | 0x1000 */,
    /**
     * FM tuner input
     */
    IN_FM_TUNER = 2147491840u /* BIT_IN | 0x2000 */,
    /**
     * TV tuner input
     */
    IN_TV_TUNER = 2147500032u /* BIT_IN | 0x4000 */,
    /**
     * Analog jack with line impedance detected
     */
    IN_LINE = 2147516416u /* BIT_IN | 0x8000 */,
    /**
     * S/PDIF in
     */
    IN_SPDIF = 2147549184u /* BIT_IN | 0x10000 */,
    IN_BLUETOOTH_A2DP = 2147614720u /* BIT_IN | 0x20000 */,
    IN_LOOPBACK = 2147745792u /* BIT_IN | 0x40000 */,
    IN_IP = 2148007936u /* BIT_IN | 0x80000 */,
    /**
     * audio bus implemented by the audio system (e.g an MOST stereo channel)
     */
    IN_BUS = 2148532224u /* BIT_IN | 0x100000 */,
    IN_PROXY = 2164260864u /* BIT_IN | 0x1000000 */,
    IN_USB_HEADSET = 2181038080u /* BIT_IN | 0x2000000 */,
    IN_DEFAULT = 3221225472u /* BIT_IN | BIT_DEFAULT */,
    IN_ALL = 3273654271u /* (IN_COMMUNICATION | IN_AMBIENT | IN_BUILTIN_MIC | IN_BLUETOOTH_SCO_HEADSET | IN_WIRED_HEADSET | IN_HDMI | IN_TELEPHONY_RX | IN_BACK_MIC | IN_REMOTE_SUBMIX | IN_ANLG_DOCK_HEADSET | IN_DGTL_DOCK_HEADSET | IN_USB_ACCESSORY | IN_USB_DEVICE | IN_FM_TUNER | IN_TV_TUNER | IN_LINE | IN_SPDIF | IN_BLUETOOTH_A2DP | IN_LOOPBACK | IN_IP | IN_BUS | IN_PROXY | IN_USB_HEADSET | IN_DEFAULT) */,
    IN_ALL_SCO = 2147483656u /* IN_BLUETOOTH_SCO_HEADSET */,
    IN_ALL_USB = 2181044224u /* (IN_USB_ACCESSORY | IN_USB_DEVICE | IN_USB_HEADSET) */,
};

/**
 * The audio output flags serve two purposes:
 *
 *  - when an AudioTrack is created they indicate a "wish" to be connected to an
 *    output stream with attributes corresponding to the specified flags;
 *
 *  - when present in an output profile descriptor listed for a particular audio
 *    hardware module, they indicate that an output stream can be opened that
 *    supports the attributes indicated by the flags.
 *
 * The audio policy manager will try to match the flags in the request
 * (when getOuput() is called) to an available output stream.
 */
enum class AudioOutputFlag : int32_t {
    NONE = 0 /* 0x0 */,
    DIRECT = 1 /* 0x1 */,
    PRIMARY = 2 /* 0x2 */,
    FAST = 4 /* 0x4 */,
    DEEP_BUFFER = 8 /* 0x8 */,
    COMPRESS_OFFLOAD = 16 /* 0x10 */,
    NON_BLOCKING = 32 /* 0x20 */,
    HW_AV_SYNC = 64 /* 0x40 */,
    TTS = 128 /* 0x80 */,
    RAW = 256 /* 0x100 */,
    SYNC = 512 /* 0x200 */,
    IEC958_NONAUDIO = 1024 /* 0x400 */,
    DIRECT_PCM = 8192 /* 0x2000 */,
    MMAP_NOIRQ = 16384 /* 0x4000 */,
    VOIP_RX = 32768 /* 0x8000 */,
};

/**
 * The audio input flags are analogous to audio output flags.
 * Currently they are used only when an AudioRecord is created,
 * to indicate a preference to be connected to an input stream with
 * attributes corresponding to the specified flags.
 */
enum class AudioInputFlag : int32_t {
    NONE = 0 /* 0x0 */,
    FAST = 1 /* 0x1 */,
    HW_HOTWORD = 2 /* 0x2 */,
    RAW = 4 /* 0x4 */,
    SYNC = 8 /* 0x8 */,
    MMAP_NOIRQ = 16 /* 0x10 */,
    VOIP_TX = 32 /* 0x20 */,
};

enum class AudioUsage : int32_t {
    UNKNOWN = 0,
    MEDIA = 1,
    VOICE_COMMUNICATION = 2,
    VOICE_COMMUNICATION_SIGNALLING = 3,
    ALARM = 4,
    NOTIFICATION = 5,
    NOTIFICATION_TELEPHONY_RINGTONE = 6,
    NOTIFICATION_COMMUNICATION_REQUEST = 7,
    NOTIFICATION_COMMUNICATION_INSTANT = 8,
    NOTIFICATION_COMMUNICATION_DELAYED = 9,
    NOTIFICATION_EVENT = 10,
    ASSISTANCE_ACCESSIBILITY = 11,
    ASSISTANCE_NAVIGATION_GUIDANCE = 12,
    ASSISTANCE_SONIFICATION = 13,
    GAME = 14,
    VIRTUAL_SOURCE = 15,
    ASSISTANT = 16,
    CNT = 17 /* ::android::hardware::audio::common::V2_0::AudioUsage.ASSISTANT implicitly + 1 */,
    MAX = 16 /* CNT - 1 */,
};

/**
 * Additional information about the stream passed to hardware decoders.
 */
struct AudioOffloadInfo final {
    uint32_t sampleRateHz __attribute__ ((aligned(4)));
    ::android::hardware::audio::common::V2_0::AudioChannelMask channelMask __attribute__ ((aligned(4)));
    ::android::hardware::audio::common::V2_0::AudioFormat format __attribute__ ((aligned(4)));
    ::android::hardware::audio::common::V2_0::AudioStreamType streamType __attribute__ ((aligned(4)));
    uint32_t bitRatePerSecond __attribute__ ((aligned(4)));
    int64_t durationMicroseconds __attribute__ ((aligned(8)));
    bool hasVideo __attribute__ ((aligned(1)));
    bool isStreaming __attribute__ ((aligned(1)));
    uint32_t bitWidth __attribute__ ((aligned(4)));
    uint32_t bufferSize __attribute__ ((aligned(4)));
    ::android::hardware::audio::common::V2_0::AudioUsage usage __attribute__ ((aligned(4)));
};

static_assert(offsetof(::android::hardware::audio::common::V2_0::AudioOffloadInfo, sampleRateHz) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::audio::common::V2_0::AudioOffloadInfo, channelMask) == 4, "wrong offset");
static_assert(offsetof(::android::hardware::audio::common::V2_0::AudioOffloadInfo, format) == 8, "wrong offset");
static_assert(offsetof(::android::hardware::audio::common::V2_0::AudioOffloadInfo, streamType) == 12, "wrong offset");
static_assert(offsetof(::android::hardware::audio::common::V2_0::AudioOffloadInfo, bitRatePerSecond) == 16, "wrong offset");
static_assert(offsetof(::android::hardware::audio::common::V2_0::AudioOffloadInfo, durationMicroseconds) == 24, "wrong offset");
static_assert(offsetof(::android::hardware::audio::common::V2_0::AudioOffloadInfo, hasVideo) == 32, "wrong offset");
static_assert(offsetof(::android::hardware::audio::common::V2_0::AudioOffloadInfo, isStreaming) == 33, "wrong offset");
static_assert(offsetof(::android::hardware::audio::common::V2_0::AudioOffloadInfo, bitWidth) == 36, "wrong offset");
static_assert(offsetof(::android::hardware::audio::common::V2_0::AudioOffloadInfo, bufferSize) == 40, "wrong offset");
static_assert(offsetof(::android::hardware::audio::common::V2_0::AudioOffloadInfo, usage) == 44, "wrong offset");
static_assert(sizeof(::android::hardware::audio::common::V2_0::AudioOffloadInfo) == 48, "wrong size");
static_assert(__alignof(::android::hardware::audio::common::V2_0::AudioOffloadInfo) == 8, "wrong alignment");

/**
 * Commonly used audio stream configuration parameters.
 */
struct AudioConfig final {
    uint32_t sampleRateHz __attribute__ ((aligned(4)));
    ::android::hardware::audio::common::V2_0::AudioChannelMask channelMask __attribute__ ((aligned(4)));
    ::android::hardware::audio::common::V2_0::AudioFormat format __attribute__ ((aligned(4)));
    ::android::hardware::audio::common::V2_0::AudioOffloadInfo offloadInfo __attribute__ ((aligned(8)));
    uint64_t frameCount __attribute__ ((aligned(8)));
};

static_assert(offsetof(::android::hardware::audio::common::V2_0::AudioConfig, sampleRateHz) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::audio::common::V2_0::AudioConfig, channelMask) == 4, "wrong offset");
static_assert(offsetof(::android::hardware::audio::common::V2_0::AudioConfig, format) == 8, "wrong offset");
static_assert(offsetof(::android::hardware::audio::common::V2_0::AudioConfig, offloadInfo) == 16, "wrong offset");
static_assert(offsetof(::android::hardware::audio::common::V2_0::AudioConfig, frameCount) == 64, "wrong offset");
static_assert(sizeof(::android::hardware::audio::common::V2_0::AudioConfig) == 72, "wrong size");
static_assert(__alignof(::android::hardware::audio::common::V2_0::AudioConfig) == 8, "wrong alignment");

/**
 *  Volume control
 *
 *
 *
 * Type of gain control exposed by an audio port.
 */
enum class AudioGainMode : uint32_t {
    JOINT = 1u /* 0x1 */,
    CHANNELS = 2u /* 0x2 */,
    RAMP = 4u /* 0x4 */,
};

/**
 * An audio_gain struct is a representation of a gain stage.
 * A gain stage is always attached to an audio port.
 */
struct AudioGain final {
    ::android::hardware::audio::common::V2_0::AudioGainMode mode __attribute__ ((aligned(4)));
    ::android::hardware::audio::common::V2_0::AudioChannelMask channelMask __attribute__ ((aligned(4)));
    int32_t minValue __attribute__ ((aligned(4)));
    int32_t maxValue __attribute__ ((aligned(4)));
    int32_t defaultValue __attribute__ ((aligned(4)));
    uint32_t stepValue __attribute__ ((aligned(4)));
    uint32_t minRampMs __attribute__ ((aligned(4)));
    uint32_t maxRampMs __attribute__ ((aligned(4)));
};

static_assert(offsetof(::android::hardware::audio::common::V2_0::AudioGain, mode) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::audio::common::V2_0::AudioGain, channelMask) == 4, "wrong offset");
static_assert(offsetof(::android::hardware::audio::common::V2_0::AudioGain, minValue) == 8, "wrong offset");
static_assert(offsetof(::android::hardware::audio::common::V2_0::AudioGain, maxValue) == 12, "wrong offset");
static_assert(offsetof(::android::hardware::audio::common::V2_0::AudioGain, defaultValue) == 16, "wrong offset");
static_assert(offsetof(::android::hardware::audio::common::V2_0::AudioGain, stepValue) == 20, "wrong offset");
static_assert(offsetof(::android::hardware::audio::common::V2_0::AudioGain, minRampMs) == 24, "wrong offset");
static_assert(offsetof(::android::hardware::audio::common::V2_0::AudioGain, maxRampMs) == 28, "wrong offset");
static_assert(sizeof(::android::hardware::audio::common::V2_0::AudioGain) == 32, "wrong size");
static_assert(__alignof(::android::hardware::audio::common::V2_0::AudioGain) == 4, "wrong alignment");

/**
 * The gain configuration structure is used to get or set the gain values of a
 * given port.
 */
struct AudioGainConfig final {
    int32_t index __attribute__ ((aligned(4)));
    ::android::hardware::audio::common::V2_0::AudioGainMode mode __attribute__ ((aligned(4)));
    ::android::hardware::audio::common::V2_0::AudioChannelMask channelMask __attribute__ ((aligned(4)));
    /**
     * 4 = sizeof(AudioChannelMask),
     * 8 is not "FCC_8", so it won't need to be changed for > 8 channels.
     * Gain values in millibels for each channel ordered from LSb to MSb in
     * channel mask. The number of values is 1 in joint mode or
     * popcount(channel_mask).
     */
    ::android::hardware::hidl_array<int32_t, 32 /* 4 * 8 */> values __attribute__ ((aligned(4)));
    uint32_t rampDurationMs __attribute__ ((aligned(4)));
};

static_assert(offsetof(::android::hardware::audio::common::V2_0::AudioGainConfig, index) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::audio::common::V2_0::AudioGainConfig, mode) == 4, "wrong offset");
static_assert(offsetof(::android::hardware::audio::common::V2_0::AudioGainConfig, channelMask) == 8, "wrong offset");
static_assert(offsetof(::android::hardware::audio::common::V2_0::AudioGainConfig, values) == 12, "wrong offset");
static_assert(offsetof(::android::hardware::audio::common::V2_0::AudioGainConfig, rampDurationMs) == 140, "wrong offset");
static_assert(sizeof(::android::hardware::audio::common::V2_0::AudioGainConfig) == 144, "wrong size");
static_assert(__alignof(::android::hardware::audio::common::V2_0::AudioGainConfig) == 4, "wrong alignment");

/**
 *  Routing control
 *
 *
 *
 * Types defined here are used to describe an audio source or sink at internal
 * framework interfaces (audio policy, patch panel) or at the audio HAL.
 * Sink and sources are grouped in a concept of “audio port” representing an
 * audio end point at the edge of the system managed by the module exposing
 * the interface.
 *
 *
 * Audio port role: either source or sink
 */
enum class AudioPortRole : int32_t {
    NONE = 0,
    SOURCE = 1 /* ::android::hardware::audio::common::V2_0::AudioPortRole.NONE implicitly + 1 */,
    SINK = 2 /* ::android::hardware::audio::common::V2_0::AudioPortRole.SOURCE implicitly + 1 */,
};

/**
 * Audio port type indicates if it is a session (e.g AudioTrack), a mix (e.g
 * PlaybackThread output) or a physical device (e.g OUT_SPEAKER)
 */
enum class AudioPortType : int32_t {
    NONE = 0,
    DEVICE = 1 /* ::android::hardware::audio::common::V2_0::AudioPortType.NONE implicitly + 1 */,
    MIX = 2 /* ::android::hardware::audio::common::V2_0::AudioPortType.DEVICE implicitly + 1 */,
    SESSION = 3 /* ::android::hardware::audio::common::V2_0::AudioPortType.MIX implicitly + 1 */,
};

/**
 * Extension for audio port configuration structure when the audio port is a
 * hardware device.
 */
struct AudioPortConfigDeviceExt final {
    int32_t hwModule __attribute__ ((aligned(4)));
    ::android::hardware::audio::common::V2_0::AudioDevice type __attribute__ ((aligned(4)));
    ::android::hardware::hidl_array<uint8_t, 32> address __attribute__ ((aligned(1)));
};

static_assert(offsetof(::android::hardware::audio::common::V2_0::AudioPortConfigDeviceExt, hwModule) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::audio::common::V2_0::AudioPortConfigDeviceExt, type) == 4, "wrong offset");
static_assert(offsetof(::android::hardware::audio::common::V2_0::AudioPortConfigDeviceExt, address) == 8, "wrong offset");
static_assert(sizeof(::android::hardware::audio::common::V2_0::AudioPortConfigDeviceExt) == 40, "wrong size");
static_assert(__alignof(::android::hardware::audio::common::V2_0::AudioPortConfigDeviceExt) == 4, "wrong alignment");

/**
 * Extension for audio port configuration structure when the audio port is an
 * audio session.
 */
struct AudioPortConfigSessionExt final {
    int32_t session __attribute__ ((aligned(4)));
};

static_assert(offsetof(::android::hardware::audio::common::V2_0::AudioPortConfigSessionExt, session) == 0, "wrong offset");
static_assert(sizeof(::android::hardware::audio::common::V2_0::AudioPortConfigSessionExt) == 4, "wrong size");
static_assert(__alignof(::android::hardware::audio::common::V2_0::AudioPortConfigSessionExt) == 4, "wrong alignment");

/**
 * Flags indicating which fields are to be considered in AudioPortConfig.
 */
enum class AudioPortConfigMask : uint32_t {
    SAMPLE_RATE = 1u /* 0x1 */,
    CHANNEL_MASK = 2u /* 0x2 */,
    FORMAT = 4u /* 0x4 */,
    GAIN = 8u /* 0x8 */,
    ALL = 15u /* SAMPLE_RATE | CHANNEL_MASK | FORMAT | GAIN */,
};

/**
 * Audio port configuration structure used to specify a particular configuration
 * of an audio port.
 */
struct AudioPortConfig final {
    // Forward declaration for forward reference support:
    union Ext;

    union Ext final {
        // Forward declaration for forward reference support:
        struct AudioPortConfigMixExt;

        struct AudioPortConfigMixExt final {
            // Forward declaration for forward reference support:
            union UseCase;

            union UseCase final {
                ::android::hardware::audio::common::V2_0::AudioStreamType stream __attribute__ ((aligned(4)));
                ::android::hardware::audio::common::V2_0::AudioSource source __attribute__ ((aligned(4)));
            };

            static_assert(offsetof(::android::hardware::audio::common::V2_0::AudioPortConfig::Ext::AudioPortConfigMixExt::UseCase, stream) == 0, "wrong offset");
            static_assert(offsetof(::android::hardware::audio::common::V2_0::AudioPortConfig::Ext::AudioPortConfigMixExt::UseCase, source) == 0, "wrong offset");
            static_assert(sizeof(::android::hardware::audio::common::V2_0::AudioPortConfig::Ext::AudioPortConfigMixExt::UseCase) == 4, "wrong size");
            static_assert(__alignof(::android::hardware::audio::common::V2_0::AudioPortConfig::Ext::AudioPortConfigMixExt::UseCase) == 4, "wrong alignment");

            int32_t hwModule __attribute__ ((aligned(4)));
            int32_t ioHandle __attribute__ ((aligned(4)));
            ::android::hardware::audio::common::V2_0::AudioPortConfig::Ext::AudioPortConfigMixExt::UseCase useCase __attribute__ ((aligned(4)));
        };

        static_assert(offsetof(::android::hardware::audio::common::V2_0::AudioPortConfig::Ext::AudioPortConfigMixExt, hwModule) == 0, "wrong offset");
        static_assert(offsetof(::android::hardware::audio::common::V2_0::AudioPortConfig::Ext::AudioPortConfigMixExt, ioHandle) == 4, "wrong offset");
        static_assert(offsetof(::android::hardware::audio::common::V2_0::AudioPortConfig::Ext::AudioPortConfigMixExt, useCase) == 8, "wrong offset");
        static_assert(sizeof(::android::hardware::audio::common::V2_0::AudioPortConfig::Ext::AudioPortConfigMixExt) == 12, "wrong size");
        static_assert(__alignof(::android::hardware::audio::common::V2_0::AudioPortConfig::Ext::AudioPortConfigMixExt) == 4, "wrong alignment");

        ::android::hardware::audio::common::V2_0::AudioPortConfigDeviceExt device __attribute__ ((aligned(4)));
        ::android::hardware::audio::common::V2_0::AudioPortConfig::Ext::AudioPortConfigMixExt mix __attribute__ ((aligned(4)));
        ::android::hardware::audio::common::V2_0::AudioPortConfigSessionExt session __attribute__ ((aligned(4)));
    };

    static_assert(offsetof(::android::hardware::audio::common::V2_0::AudioPortConfig::Ext, device) == 0, "wrong offset");
    static_assert(offsetof(::android::hardware::audio::common::V2_0::AudioPortConfig::Ext, mix) == 0, "wrong offset");
    static_assert(offsetof(::android::hardware::audio::common::V2_0::AudioPortConfig::Ext, session) == 0, "wrong offset");
    static_assert(sizeof(::android::hardware::audio::common::V2_0::AudioPortConfig::Ext) == 40, "wrong size");
    static_assert(__alignof(::android::hardware::audio::common::V2_0::AudioPortConfig::Ext) == 4, "wrong alignment");

    int32_t id __attribute__ ((aligned(4)));
    ::android::hardware::audio::common::V2_0::AudioPortConfigMask configMask __attribute__ ((aligned(4)));
    uint32_t sampleRateHz __attribute__ ((aligned(4)));
    ::android::hardware::audio::common::V2_0::AudioChannelMask channelMask __attribute__ ((aligned(4)));
    ::android::hardware::audio::common::V2_0::AudioFormat format __attribute__ ((aligned(4)));
    ::android::hardware::audio::common::V2_0::AudioGainConfig gain __attribute__ ((aligned(4)));
    ::android::hardware::audio::common::V2_0::AudioPortType type __attribute__ ((aligned(4)));
    ::android::hardware::audio::common::V2_0::AudioPortRole role __attribute__ ((aligned(4)));
    ::android::hardware::audio::common::V2_0::AudioPortConfig::Ext ext __attribute__ ((aligned(4)));
};

static_assert(offsetof(::android::hardware::audio::common::V2_0::AudioPortConfig, id) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::audio::common::V2_0::AudioPortConfig, configMask) == 4, "wrong offset");
static_assert(offsetof(::android::hardware::audio::common::V2_0::AudioPortConfig, sampleRateHz) == 8, "wrong offset");
static_assert(offsetof(::android::hardware::audio::common::V2_0::AudioPortConfig, channelMask) == 12, "wrong offset");
static_assert(offsetof(::android::hardware::audio::common::V2_0::AudioPortConfig, format) == 16, "wrong offset");
static_assert(offsetof(::android::hardware::audio::common::V2_0::AudioPortConfig, gain) == 20, "wrong offset");
static_assert(offsetof(::android::hardware::audio::common::V2_0::AudioPortConfig, type) == 164, "wrong offset");
static_assert(offsetof(::android::hardware::audio::common::V2_0::AudioPortConfig, role) == 168, "wrong offset");
static_assert(offsetof(::android::hardware::audio::common::V2_0::AudioPortConfig, ext) == 172, "wrong offset");
static_assert(sizeof(::android::hardware::audio::common::V2_0::AudioPortConfig) == 212, "wrong size");
static_assert(__alignof(::android::hardware::audio::common::V2_0::AudioPortConfig) == 4, "wrong alignment");

/**
 * Extension for audio port structure when the audio port is a hardware device.
 */
struct AudioPortDeviceExt final {
    int32_t hwModule __attribute__ ((aligned(4)));
    ::android::hardware::audio::common::V2_0::AudioDevice type __attribute__ ((aligned(4)));
    ::android::hardware::hidl_array<uint8_t, 32> address __attribute__ ((aligned(1)));
};

static_assert(offsetof(::android::hardware::audio::common::V2_0::AudioPortDeviceExt, hwModule) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::audio::common::V2_0::AudioPortDeviceExt, type) == 4, "wrong offset");
static_assert(offsetof(::android::hardware::audio::common::V2_0::AudioPortDeviceExt, address) == 8, "wrong offset");
static_assert(sizeof(::android::hardware::audio::common::V2_0::AudioPortDeviceExt) == 40, "wrong size");
static_assert(__alignof(::android::hardware::audio::common::V2_0::AudioPortDeviceExt) == 4, "wrong alignment");

/**
 * Latency class of the audio mix.
 */
enum class AudioMixLatencyClass : int32_t {
    LOW = 0,
    NORMAL = 1 /* ::android::hardware::audio::common::V2_0::AudioMixLatencyClass.LOW implicitly + 1 */,
};

struct AudioPortMixExt final {
    int32_t hwModule __attribute__ ((aligned(4)));
    int32_t ioHandle __attribute__ ((aligned(4)));
    ::android::hardware::audio::common::V2_0::AudioMixLatencyClass latencyClass __attribute__ ((aligned(4)));
};

static_assert(offsetof(::android::hardware::audio::common::V2_0::AudioPortMixExt, hwModule) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::audio::common::V2_0::AudioPortMixExt, ioHandle) == 4, "wrong offset");
static_assert(offsetof(::android::hardware::audio::common::V2_0::AudioPortMixExt, latencyClass) == 8, "wrong offset");
static_assert(sizeof(::android::hardware::audio::common::V2_0::AudioPortMixExt) == 12, "wrong size");
static_assert(__alignof(::android::hardware::audio::common::V2_0::AudioPortMixExt) == 4, "wrong alignment");

/**
 * Extension for audio port structure when the audio port is an audio session.
 */
struct AudioPortSessionExt final {
    int32_t session __attribute__ ((aligned(4)));
};

static_assert(offsetof(::android::hardware::audio::common::V2_0::AudioPortSessionExt, session) == 0, "wrong offset");
static_assert(sizeof(::android::hardware::audio::common::V2_0::AudioPortSessionExt) == 4, "wrong size");
static_assert(__alignof(::android::hardware::audio::common::V2_0::AudioPortSessionExt) == 4, "wrong alignment");

struct AudioPort final {
    // Forward declaration for forward reference support:
    union Ext;

    union Ext final {
        ::android::hardware::audio::common::V2_0::AudioPortDeviceExt device __attribute__ ((aligned(4)));
        ::android::hardware::audio::common::V2_0::AudioPortMixExt mix __attribute__ ((aligned(4)));
        ::android::hardware::audio::common::V2_0::AudioPortSessionExt session __attribute__ ((aligned(4)));
    };

    static_assert(offsetof(::android::hardware::audio::common::V2_0::AudioPort::Ext, device) == 0, "wrong offset");
    static_assert(offsetof(::android::hardware::audio::common::V2_0::AudioPort::Ext, mix) == 0, "wrong offset");
    static_assert(offsetof(::android::hardware::audio::common::V2_0::AudioPort::Ext, session) == 0, "wrong offset");
    static_assert(sizeof(::android::hardware::audio::common::V2_0::AudioPort::Ext) == 40, "wrong size");
    static_assert(__alignof(::android::hardware::audio::common::V2_0::AudioPort::Ext) == 4, "wrong alignment");

    int32_t id __attribute__ ((aligned(4)));
    ::android::hardware::audio::common::V2_0::AudioPortRole role __attribute__ ((aligned(4)));
    ::android::hardware::hidl_string name __attribute__ ((aligned(8)));
    ::android::hardware::hidl_vec<uint32_t> sampleRates __attribute__ ((aligned(8)));
    ::android::hardware::hidl_vec<::android::hardware::audio::common::V2_0::AudioChannelMask> channelMasks __attribute__ ((aligned(8)));
    ::android::hardware::hidl_vec<::android::hardware::audio::common::V2_0::AudioFormat> formats __attribute__ ((aligned(8)));
    ::android::hardware::hidl_vec<::android::hardware::audio::common::V2_0::AudioGain> gains __attribute__ ((aligned(8)));
    ::android::hardware::audio::common::V2_0::AudioPortConfig activeConfig __attribute__ ((aligned(4)));
    ::android::hardware::audio::common::V2_0::AudioPortType type __attribute__ ((aligned(4)));
    ::android::hardware::audio::common::V2_0::AudioPort::Ext ext __attribute__ ((aligned(4)));
};

static_assert(offsetof(::android::hardware::audio::common::V2_0::AudioPort, id) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::audio::common::V2_0::AudioPort, role) == 4, "wrong offset");
static_assert(offsetof(::android::hardware::audio::common::V2_0::AudioPort, name) == 8, "wrong offset");
static_assert(offsetof(::android::hardware::audio::common::V2_0::AudioPort, sampleRates) == 24, "wrong offset");
static_assert(offsetof(::android::hardware::audio::common::V2_0::AudioPort, channelMasks) == 40, "wrong offset");
static_assert(offsetof(::android::hardware::audio::common::V2_0::AudioPort, formats) == 56, "wrong offset");
static_assert(offsetof(::android::hardware::audio::common::V2_0::AudioPort, gains) == 72, "wrong offset");
static_assert(offsetof(::android::hardware::audio::common::V2_0::AudioPort, activeConfig) == 88, "wrong offset");
static_assert(offsetof(::android::hardware::audio::common::V2_0::AudioPort, type) == 300, "wrong offset");
static_assert(offsetof(::android::hardware::audio::common::V2_0::AudioPort, ext) == 304, "wrong offset");
static_assert(sizeof(::android::hardware::audio::common::V2_0::AudioPort) == 344, "wrong size");
static_assert(__alignof(::android::hardware::audio::common::V2_0::AudioPort) == 8, "wrong alignment");

struct ThreadInfo final {
    int64_t pid __attribute__ ((aligned(8)));
    int64_t tid __attribute__ ((aligned(8)));
};

static_assert(offsetof(::android::hardware::audio::common::V2_0::ThreadInfo, pid) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::audio::common::V2_0::ThreadInfo, tid) == 8, "wrong offset");
static_assert(sizeof(::android::hardware::audio::common::V2_0::ThreadInfo) == 16, "wrong size");
static_assert(__alignof(::android::hardware::audio::common::V2_0::ThreadInfo) == 8, "wrong alignment");

//
// type declarations for package
//

template<typename>
static inline std::string toString(int32_t o);
static inline std::string toString(::android::hardware::audio::common::V2_0::AudioHandleConsts o);
static inline void PrintTo(::android::hardware::audio::common::V2_0::AudioHandleConsts o, ::std::ostream* os);
constexpr int32_t operator|(const ::android::hardware::audio::common::V2_0::AudioHandleConsts lhs, const ::android::hardware::audio::common::V2_0::AudioHandleConsts rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const int32_t lhs, const ::android::hardware::audio::common::V2_0::AudioHandleConsts rhs) {
    return static_cast<int32_t>(lhs | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const ::android::hardware::audio::common::V2_0::AudioHandleConsts lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | rhs);
}
constexpr int32_t operator&(const ::android::hardware::audio::common::V2_0::AudioHandleConsts lhs, const ::android::hardware::audio::common::V2_0::AudioHandleConsts rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const int32_t lhs, const ::android::hardware::audio::common::V2_0::AudioHandleConsts rhs) {
    return static_cast<int32_t>(lhs & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const ::android::hardware::audio::common::V2_0::AudioHandleConsts lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & rhs);
}
constexpr int32_t &operator|=(int32_t& v, const ::android::hardware::audio::common::V2_0::AudioHandleConsts e) {
    v |= static_cast<int32_t>(e);
    return v;
}
constexpr int32_t &operator&=(int32_t& v, const ::android::hardware::audio::common::V2_0::AudioHandleConsts e) {
    v &= static_cast<int32_t>(e);
    return v;
}

static inline std::string toString(const ::android::hardware::audio::common::V2_0::Uuid& o);
static inline void PrintTo(const ::android::hardware::audio::common::V2_0::Uuid& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::audio::common::V2_0::Uuid& lhs, const ::android::hardware::audio::common::V2_0::Uuid& rhs);
static inline bool operator!=(const ::android::hardware::audio::common::V2_0::Uuid& lhs, const ::android::hardware::audio::common::V2_0::Uuid& rhs);

template<typename>
static inline std::string toString(int32_t o);
static inline std::string toString(::android::hardware::audio::common::V2_0::AudioStreamType o);
static inline void PrintTo(::android::hardware::audio::common::V2_0::AudioStreamType o, ::std::ostream* os);
constexpr int32_t operator|(const ::android::hardware::audio::common::V2_0::AudioStreamType lhs, const ::android::hardware::audio::common::V2_0::AudioStreamType rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const int32_t lhs, const ::android::hardware::audio::common::V2_0::AudioStreamType rhs) {
    return static_cast<int32_t>(lhs | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const ::android::hardware::audio::common::V2_0::AudioStreamType lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | rhs);
}
constexpr int32_t operator&(const ::android::hardware::audio::common::V2_0::AudioStreamType lhs, const ::android::hardware::audio::common::V2_0::AudioStreamType rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const int32_t lhs, const ::android::hardware::audio::common::V2_0::AudioStreamType rhs) {
    return static_cast<int32_t>(lhs & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const ::android::hardware::audio::common::V2_0::AudioStreamType lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & rhs);
}
constexpr int32_t &operator|=(int32_t& v, const ::android::hardware::audio::common::V2_0::AudioStreamType e) {
    v |= static_cast<int32_t>(e);
    return v;
}
constexpr int32_t &operator&=(int32_t& v, const ::android::hardware::audio::common::V2_0::AudioStreamType e) {
    v &= static_cast<int32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(int32_t o);
static inline std::string toString(::android::hardware::audio::common::V2_0::AudioSource o);
static inline void PrintTo(::android::hardware::audio::common::V2_0::AudioSource o, ::std::ostream* os);
constexpr int32_t operator|(const ::android::hardware::audio::common::V2_0::AudioSource lhs, const ::android::hardware::audio::common::V2_0::AudioSource rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const int32_t lhs, const ::android::hardware::audio::common::V2_0::AudioSource rhs) {
    return static_cast<int32_t>(lhs | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const ::android::hardware::audio::common::V2_0::AudioSource lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | rhs);
}
constexpr int32_t operator&(const ::android::hardware::audio::common::V2_0::AudioSource lhs, const ::android::hardware::audio::common::V2_0::AudioSource rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const int32_t lhs, const ::android::hardware::audio::common::V2_0::AudioSource rhs) {
    return static_cast<int32_t>(lhs & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const ::android::hardware::audio::common::V2_0::AudioSource lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & rhs);
}
constexpr int32_t &operator|=(int32_t& v, const ::android::hardware::audio::common::V2_0::AudioSource e) {
    v |= static_cast<int32_t>(e);
    return v;
}
constexpr int32_t &operator&=(int32_t& v, const ::android::hardware::audio::common::V2_0::AudioSource e) {
    v &= static_cast<int32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(int32_t o);
static inline std::string toString(::android::hardware::audio::common::V2_0::AudioSessionConsts o);
static inline void PrintTo(::android::hardware::audio::common::V2_0::AudioSessionConsts o, ::std::ostream* os);
constexpr int32_t operator|(const ::android::hardware::audio::common::V2_0::AudioSessionConsts lhs, const ::android::hardware::audio::common::V2_0::AudioSessionConsts rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const int32_t lhs, const ::android::hardware::audio::common::V2_0::AudioSessionConsts rhs) {
    return static_cast<int32_t>(lhs | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const ::android::hardware::audio::common::V2_0::AudioSessionConsts lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | rhs);
}
constexpr int32_t operator&(const ::android::hardware::audio::common::V2_0::AudioSessionConsts lhs, const ::android::hardware::audio::common::V2_0::AudioSessionConsts rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const int32_t lhs, const ::android::hardware::audio::common::V2_0::AudioSessionConsts rhs) {
    return static_cast<int32_t>(lhs & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const ::android::hardware::audio::common::V2_0::AudioSessionConsts lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & rhs);
}
constexpr int32_t &operator|=(int32_t& v, const ::android::hardware::audio::common::V2_0::AudioSessionConsts e) {
    v |= static_cast<int32_t>(e);
    return v;
}
constexpr int32_t &operator&=(int32_t& v, const ::android::hardware::audio::common::V2_0::AudioSessionConsts e) {
    v &= static_cast<int32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(uint32_t o);
static inline std::string toString(::android::hardware::audio::common::V2_0::AudioFormat o);
static inline void PrintTo(::android::hardware::audio::common::V2_0::AudioFormat o, ::std::ostream* os);
constexpr uint32_t operator|(const ::android::hardware::audio::common::V2_0::AudioFormat lhs, const ::android::hardware::audio::common::V2_0::AudioFormat rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const uint32_t lhs, const ::android::hardware::audio::common::V2_0::AudioFormat rhs) {
    return static_cast<uint32_t>(lhs | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const ::android::hardware::audio::common::V2_0::AudioFormat lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | rhs);
}
constexpr uint32_t operator&(const ::android::hardware::audio::common::V2_0::AudioFormat lhs, const ::android::hardware::audio::common::V2_0::AudioFormat rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const uint32_t lhs, const ::android::hardware::audio::common::V2_0::AudioFormat rhs) {
    return static_cast<uint32_t>(lhs & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const ::android::hardware::audio::common::V2_0::AudioFormat lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & rhs);
}
constexpr uint32_t &operator|=(uint32_t& v, const ::android::hardware::audio::common::V2_0::AudioFormat e) {
    v |= static_cast<uint32_t>(e);
    return v;
}
constexpr uint32_t &operator&=(uint32_t& v, const ::android::hardware::audio::common::V2_0::AudioFormat e) {
    v &= static_cast<uint32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(int32_t o);
static inline std::string toString(::android::hardware::audio::common::V2_0::FixedChannelCount o);
static inline void PrintTo(::android::hardware::audio::common::V2_0::FixedChannelCount o, ::std::ostream* os);
constexpr int32_t operator|(const ::android::hardware::audio::common::V2_0::FixedChannelCount lhs, const ::android::hardware::audio::common::V2_0::FixedChannelCount rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const int32_t lhs, const ::android::hardware::audio::common::V2_0::FixedChannelCount rhs) {
    return static_cast<int32_t>(lhs | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const ::android::hardware::audio::common::V2_0::FixedChannelCount lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | rhs);
}
constexpr int32_t operator&(const ::android::hardware::audio::common::V2_0::FixedChannelCount lhs, const ::android::hardware::audio::common::V2_0::FixedChannelCount rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const int32_t lhs, const ::android::hardware::audio::common::V2_0::FixedChannelCount rhs) {
    return static_cast<int32_t>(lhs & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const ::android::hardware::audio::common::V2_0::FixedChannelCount lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & rhs);
}
constexpr int32_t &operator|=(int32_t& v, const ::android::hardware::audio::common::V2_0::FixedChannelCount e) {
    v |= static_cast<int32_t>(e);
    return v;
}
constexpr int32_t &operator&=(int32_t& v, const ::android::hardware::audio::common::V2_0::FixedChannelCount e) {
    v &= static_cast<int32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(uint32_t o);
static inline std::string toString(::android::hardware::audio::common::V2_0::AudioChannelMask o);
static inline void PrintTo(::android::hardware::audio::common::V2_0::AudioChannelMask o, ::std::ostream* os);
constexpr uint32_t operator|(const ::android::hardware::audio::common::V2_0::AudioChannelMask lhs, const ::android::hardware::audio::common::V2_0::AudioChannelMask rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const uint32_t lhs, const ::android::hardware::audio::common::V2_0::AudioChannelMask rhs) {
    return static_cast<uint32_t>(lhs | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const ::android::hardware::audio::common::V2_0::AudioChannelMask lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | rhs);
}
constexpr uint32_t operator&(const ::android::hardware::audio::common::V2_0::AudioChannelMask lhs, const ::android::hardware::audio::common::V2_0::AudioChannelMask rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const uint32_t lhs, const ::android::hardware::audio::common::V2_0::AudioChannelMask rhs) {
    return static_cast<uint32_t>(lhs & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const ::android::hardware::audio::common::V2_0::AudioChannelMask lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & rhs);
}
constexpr uint32_t &operator|=(uint32_t& v, const ::android::hardware::audio::common::V2_0::AudioChannelMask e) {
    v |= static_cast<uint32_t>(e);
    return v;
}
constexpr uint32_t &operator&=(uint32_t& v, const ::android::hardware::audio::common::V2_0::AudioChannelMask e) {
    v &= static_cast<uint32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(int32_t o);
static inline std::string toString(::android::hardware::audio::common::V2_0::AudioInterleave o);
static inline void PrintTo(::android::hardware::audio::common::V2_0::AudioInterleave o, ::std::ostream* os);
constexpr int32_t operator|(const ::android::hardware::audio::common::V2_0::AudioInterleave lhs, const ::android::hardware::audio::common::V2_0::AudioInterleave rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const int32_t lhs, const ::android::hardware::audio::common::V2_0::AudioInterleave rhs) {
    return static_cast<int32_t>(lhs | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const ::android::hardware::audio::common::V2_0::AudioInterleave lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | rhs);
}
constexpr int32_t operator&(const ::android::hardware::audio::common::V2_0::AudioInterleave lhs, const ::android::hardware::audio::common::V2_0::AudioInterleave rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const int32_t lhs, const ::android::hardware::audio::common::V2_0::AudioInterleave rhs) {
    return static_cast<int32_t>(lhs & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const ::android::hardware::audio::common::V2_0::AudioInterleave lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & rhs);
}
constexpr int32_t &operator|=(int32_t& v, const ::android::hardware::audio::common::V2_0::AudioInterleave e) {
    v |= static_cast<int32_t>(e);
    return v;
}
constexpr int32_t &operator&=(int32_t& v, const ::android::hardware::audio::common::V2_0::AudioInterleave e) {
    v &= static_cast<int32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(int32_t o);
static inline std::string toString(::android::hardware::audio::common::V2_0::AudioMode o);
static inline void PrintTo(::android::hardware::audio::common::V2_0::AudioMode o, ::std::ostream* os);
constexpr int32_t operator|(const ::android::hardware::audio::common::V2_0::AudioMode lhs, const ::android::hardware::audio::common::V2_0::AudioMode rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const int32_t lhs, const ::android::hardware::audio::common::V2_0::AudioMode rhs) {
    return static_cast<int32_t>(lhs | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const ::android::hardware::audio::common::V2_0::AudioMode lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | rhs);
}
constexpr int32_t operator&(const ::android::hardware::audio::common::V2_0::AudioMode lhs, const ::android::hardware::audio::common::V2_0::AudioMode rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const int32_t lhs, const ::android::hardware::audio::common::V2_0::AudioMode rhs) {
    return static_cast<int32_t>(lhs & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const ::android::hardware::audio::common::V2_0::AudioMode lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & rhs);
}
constexpr int32_t &operator|=(int32_t& v, const ::android::hardware::audio::common::V2_0::AudioMode e) {
    v |= static_cast<int32_t>(e);
    return v;
}
constexpr int32_t &operator&=(int32_t& v, const ::android::hardware::audio::common::V2_0::AudioMode e) {
    v &= static_cast<int32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(uint32_t o);
static inline std::string toString(::android::hardware::audio::common::V2_0::AudioDevice o);
static inline void PrintTo(::android::hardware::audio::common::V2_0::AudioDevice o, ::std::ostream* os);
constexpr uint32_t operator|(const ::android::hardware::audio::common::V2_0::AudioDevice lhs, const ::android::hardware::audio::common::V2_0::AudioDevice rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const uint32_t lhs, const ::android::hardware::audio::common::V2_0::AudioDevice rhs) {
    return static_cast<uint32_t>(lhs | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const ::android::hardware::audio::common::V2_0::AudioDevice lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | rhs);
}
constexpr uint32_t operator&(const ::android::hardware::audio::common::V2_0::AudioDevice lhs, const ::android::hardware::audio::common::V2_0::AudioDevice rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const uint32_t lhs, const ::android::hardware::audio::common::V2_0::AudioDevice rhs) {
    return static_cast<uint32_t>(lhs & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const ::android::hardware::audio::common::V2_0::AudioDevice lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & rhs);
}
constexpr uint32_t &operator|=(uint32_t& v, const ::android::hardware::audio::common::V2_0::AudioDevice e) {
    v |= static_cast<uint32_t>(e);
    return v;
}
constexpr uint32_t &operator&=(uint32_t& v, const ::android::hardware::audio::common::V2_0::AudioDevice e) {
    v &= static_cast<uint32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(int32_t o);
static inline std::string toString(::android::hardware::audio::common::V2_0::AudioOutputFlag o);
static inline void PrintTo(::android::hardware::audio::common::V2_0::AudioOutputFlag o, ::std::ostream* os);
constexpr int32_t operator|(const ::android::hardware::audio::common::V2_0::AudioOutputFlag lhs, const ::android::hardware::audio::common::V2_0::AudioOutputFlag rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const int32_t lhs, const ::android::hardware::audio::common::V2_0::AudioOutputFlag rhs) {
    return static_cast<int32_t>(lhs | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const ::android::hardware::audio::common::V2_0::AudioOutputFlag lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | rhs);
}
constexpr int32_t operator&(const ::android::hardware::audio::common::V2_0::AudioOutputFlag lhs, const ::android::hardware::audio::common::V2_0::AudioOutputFlag rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const int32_t lhs, const ::android::hardware::audio::common::V2_0::AudioOutputFlag rhs) {
    return static_cast<int32_t>(lhs & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const ::android::hardware::audio::common::V2_0::AudioOutputFlag lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & rhs);
}
constexpr int32_t &operator|=(int32_t& v, const ::android::hardware::audio::common::V2_0::AudioOutputFlag e) {
    v |= static_cast<int32_t>(e);
    return v;
}
constexpr int32_t &operator&=(int32_t& v, const ::android::hardware::audio::common::V2_0::AudioOutputFlag e) {
    v &= static_cast<int32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(int32_t o);
static inline std::string toString(::android::hardware::audio::common::V2_0::AudioInputFlag o);
static inline void PrintTo(::android::hardware::audio::common::V2_0::AudioInputFlag o, ::std::ostream* os);
constexpr int32_t operator|(const ::android::hardware::audio::common::V2_0::AudioInputFlag lhs, const ::android::hardware::audio::common::V2_0::AudioInputFlag rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const int32_t lhs, const ::android::hardware::audio::common::V2_0::AudioInputFlag rhs) {
    return static_cast<int32_t>(lhs | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const ::android::hardware::audio::common::V2_0::AudioInputFlag lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | rhs);
}
constexpr int32_t operator&(const ::android::hardware::audio::common::V2_0::AudioInputFlag lhs, const ::android::hardware::audio::common::V2_0::AudioInputFlag rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const int32_t lhs, const ::android::hardware::audio::common::V2_0::AudioInputFlag rhs) {
    return static_cast<int32_t>(lhs & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const ::android::hardware::audio::common::V2_0::AudioInputFlag lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & rhs);
}
constexpr int32_t &operator|=(int32_t& v, const ::android::hardware::audio::common::V2_0::AudioInputFlag e) {
    v |= static_cast<int32_t>(e);
    return v;
}
constexpr int32_t &operator&=(int32_t& v, const ::android::hardware::audio::common::V2_0::AudioInputFlag e) {
    v &= static_cast<int32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(int32_t o);
static inline std::string toString(::android::hardware::audio::common::V2_0::AudioUsage o);
static inline void PrintTo(::android::hardware::audio::common::V2_0::AudioUsage o, ::std::ostream* os);
constexpr int32_t operator|(const ::android::hardware::audio::common::V2_0::AudioUsage lhs, const ::android::hardware::audio::common::V2_0::AudioUsage rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const int32_t lhs, const ::android::hardware::audio::common::V2_0::AudioUsage rhs) {
    return static_cast<int32_t>(lhs | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const ::android::hardware::audio::common::V2_0::AudioUsage lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | rhs);
}
constexpr int32_t operator&(const ::android::hardware::audio::common::V2_0::AudioUsage lhs, const ::android::hardware::audio::common::V2_0::AudioUsage rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const int32_t lhs, const ::android::hardware::audio::common::V2_0::AudioUsage rhs) {
    return static_cast<int32_t>(lhs & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const ::android::hardware::audio::common::V2_0::AudioUsage lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & rhs);
}
constexpr int32_t &operator|=(int32_t& v, const ::android::hardware::audio::common::V2_0::AudioUsage e) {
    v |= static_cast<int32_t>(e);
    return v;
}
constexpr int32_t &operator&=(int32_t& v, const ::android::hardware::audio::common::V2_0::AudioUsage e) {
    v &= static_cast<int32_t>(e);
    return v;
}

static inline std::string toString(const ::android::hardware::audio::common::V2_0::AudioOffloadInfo& o);
static inline void PrintTo(const ::android::hardware::audio::common::V2_0::AudioOffloadInfo& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::audio::common::V2_0::AudioOffloadInfo& lhs, const ::android::hardware::audio::common::V2_0::AudioOffloadInfo& rhs);
static inline bool operator!=(const ::android::hardware::audio::common::V2_0::AudioOffloadInfo& lhs, const ::android::hardware::audio::common::V2_0::AudioOffloadInfo& rhs);

static inline std::string toString(const ::android::hardware::audio::common::V2_0::AudioConfig& o);
static inline void PrintTo(const ::android::hardware::audio::common::V2_0::AudioConfig& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::audio::common::V2_0::AudioConfig& lhs, const ::android::hardware::audio::common::V2_0::AudioConfig& rhs);
static inline bool operator!=(const ::android::hardware::audio::common::V2_0::AudioConfig& lhs, const ::android::hardware::audio::common::V2_0::AudioConfig& rhs);

template<typename>
static inline std::string toString(uint32_t o);
static inline std::string toString(::android::hardware::audio::common::V2_0::AudioGainMode o);
static inline void PrintTo(::android::hardware::audio::common::V2_0::AudioGainMode o, ::std::ostream* os);
constexpr uint32_t operator|(const ::android::hardware::audio::common::V2_0::AudioGainMode lhs, const ::android::hardware::audio::common::V2_0::AudioGainMode rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const uint32_t lhs, const ::android::hardware::audio::common::V2_0::AudioGainMode rhs) {
    return static_cast<uint32_t>(lhs | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const ::android::hardware::audio::common::V2_0::AudioGainMode lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | rhs);
}
constexpr uint32_t operator&(const ::android::hardware::audio::common::V2_0::AudioGainMode lhs, const ::android::hardware::audio::common::V2_0::AudioGainMode rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const uint32_t lhs, const ::android::hardware::audio::common::V2_0::AudioGainMode rhs) {
    return static_cast<uint32_t>(lhs & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const ::android::hardware::audio::common::V2_0::AudioGainMode lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & rhs);
}
constexpr uint32_t &operator|=(uint32_t& v, const ::android::hardware::audio::common::V2_0::AudioGainMode e) {
    v |= static_cast<uint32_t>(e);
    return v;
}
constexpr uint32_t &operator&=(uint32_t& v, const ::android::hardware::audio::common::V2_0::AudioGainMode e) {
    v &= static_cast<uint32_t>(e);
    return v;
}

static inline std::string toString(const ::android::hardware::audio::common::V2_0::AudioGain& o);
static inline void PrintTo(const ::android::hardware::audio::common::V2_0::AudioGain& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::audio::common::V2_0::AudioGain& lhs, const ::android::hardware::audio::common::V2_0::AudioGain& rhs);
static inline bool operator!=(const ::android::hardware::audio::common::V2_0::AudioGain& lhs, const ::android::hardware::audio::common::V2_0::AudioGain& rhs);

static inline std::string toString(const ::android::hardware::audio::common::V2_0::AudioGainConfig& o);
static inline void PrintTo(const ::android::hardware::audio::common::V2_0::AudioGainConfig& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::audio::common::V2_0::AudioGainConfig& lhs, const ::android::hardware::audio::common::V2_0::AudioGainConfig& rhs);
static inline bool operator!=(const ::android::hardware::audio::common::V2_0::AudioGainConfig& lhs, const ::android::hardware::audio::common::V2_0::AudioGainConfig& rhs);

template<typename>
static inline std::string toString(int32_t o);
static inline std::string toString(::android::hardware::audio::common::V2_0::AudioPortRole o);
static inline void PrintTo(::android::hardware::audio::common::V2_0::AudioPortRole o, ::std::ostream* os);
constexpr int32_t operator|(const ::android::hardware::audio::common::V2_0::AudioPortRole lhs, const ::android::hardware::audio::common::V2_0::AudioPortRole rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const int32_t lhs, const ::android::hardware::audio::common::V2_0::AudioPortRole rhs) {
    return static_cast<int32_t>(lhs | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const ::android::hardware::audio::common::V2_0::AudioPortRole lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | rhs);
}
constexpr int32_t operator&(const ::android::hardware::audio::common::V2_0::AudioPortRole lhs, const ::android::hardware::audio::common::V2_0::AudioPortRole rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const int32_t lhs, const ::android::hardware::audio::common::V2_0::AudioPortRole rhs) {
    return static_cast<int32_t>(lhs & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const ::android::hardware::audio::common::V2_0::AudioPortRole lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & rhs);
}
constexpr int32_t &operator|=(int32_t& v, const ::android::hardware::audio::common::V2_0::AudioPortRole e) {
    v |= static_cast<int32_t>(e);
    return v;
}
constexpr int32_t &operator&=(int32_t& v, const ::android::hardware::audio::common::V2_0::AudioPortRole e) {
    v &= static_cast<int32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(int32_t o);
static inline std::string toString(::android::hardware::audio::common::V2_0::AudioPortType o);
static inline void PrintTo(::android::hardware::audio::common::V2_0::AudioPortType o, ::std::ostream* os);
constexpr int32_t operator|(const ::android::hardware::audio::common::V2_0::AudioPortType lhs, const ::android::hardware::audio::common::V2_0::AudioPortType rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const int32_t lhs, const ::android::hardware::audio::common::V2_0::AudioPortType rhs) {
    return static_cast<int32_t>(lhs | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const ::android::hardware::audio::common::V2_0::AudioPortType lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | rhs);
}
constexpr int32_t operator&(const ::android::hardware::audio::common::V2_0::AudioPortType lhs, const ::android::hardware::audio::common::V2_0::AudioPortType rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const int32_t lhs, const ::android::hardware::audio::common::V2_0::AudioPortType rhs) {
    return static_cast<int32_t>(lhs & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const ::android::hardware::audio::common::V2_0::AudioPortType lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & rhs);
}
constexpr int32_t &operator|=(int32_t& v, const ::android::hardware::audio::common::V2_0::AudioPortType e) {
    v |= static_cast<int32_t>(e);
    return v;
}
constexpr int32_t &operator&=(int32_t& v, const ::android::hardware::audio::common::V2_0::AudioPortType e) {
    v &= static_cast<int32_t>(e);
    return v;
}

static inline std::string toString(const ::android::hardware::audio::common::V2_0::AudioPortConfigDeviceExt& o);
static inline void PrintTo(const ::android::hardware::audio::common::V2_0::AudioPortConfigDeviceExt& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::audio::common::V2_0::AudioPortConfigDeviceExt& lhs, const ::android::hardware::audio::common::V2_0::AudioPortConfigDeviceExt& rhs);
static inline bool operator!=(const ::android::hardware::audio::common::V2_0::AudioPortConfigDeviceExt& lhs, const ::android::hardware::audio::common::V2_0::AudioPortConfigDeviceExt& rhs);

static inline std::string toString(const ::android::hardware::audio::common::V2_0::AudioPortConfigSessionExt& o);
static inline void PrintTo(const ::android::hardware::audio::common::V2_0::AudioPortConfigSessionExt& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::audio::common::V2_0::AudioPortConfigSessionExt& lhs, const ::android::hardware::audio::common::V2_0::AudioPortConfigSessionExt& rhs);
static inline bool operator!=(const ::android::hardware::audio::common::V2_0::AudioPortConfigSessionExt& lhs, const ::android::hardware::audio::common::V2_0::AudioPortConfigSessionExt& rhs);

template<typename>
static inline std::string toString(uint32_t o);
static inline std::string toString(::android::hardware::audio::common::V2_0::AudioPortConfigMask o);
static inline void PrintTo(::android::hardware::audio::common::V2_0::AudioPortConfigMask o, ::std::ostream* os);
constexpr uint32_t operator|(const ::android::hardware::audio::common::V2_0::AudioPortConfigMask lhs, const ::android::hardware::audio::common::V2_0::AudioPortConfigMask rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const uint32_t lhs, const ::android::hardware::audio::common::V2_0::AudioPortConfigMask rhs) {
    return static_cast<uint32_t>(lhs | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const ::android::hardware::audio::common::V2_0::AudioPortConfigMask lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | rhs);
}
constexpr uint32_t operator&(const ::android::hardware::audio::common::V2_0::AudioPortConfigMask lhs, const ::android::hardware::audio::common::V2_0::AudioPortConfigMask rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const uint32_t lhs, const ::android::hardware::audio::common::V2_0::AudioPortConfigMask rhs) {
    return static_cast<uint32_t>(lhs & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const ::android::hardware::audio::common::V2_0::AudioPortConfigMask lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & rhs);
}
constexpr uint32_t &operator|=(uint32_t& v, const ::android::hardware::audio::common::V2_0::AudioPortConfigMask e) {
    v |= static_cast<uint32_t>(e);
    return v;
}
constexpr uint32_t &operator&=(uint32_t& v, const ::android::hardware::audio::common::V2_0::AudioPortConfigMask e) {
    v &= static_cast<uint32_t>(e);
    return v;
}

static inline std::string toString(const ::android::hardware::audio::common::V2_0::AudioPortConfig::Ext::AudioPortConfigMixExt::UseCase& o);
static inline void PrintTo(const ::android::hardware::audio::common::V2_0::AudioPortConfig::Ext::AudioPortConfigMixExt::UseCase& o, ::std::ostream*);
// operator== and operator!= are not generated for UseCase

static inline std::string toString(const ::android::hardware::audio::common::V2_0::AudioPortConfig::Ext::AudioPortConfigMixExt& o);
static inline void PrintTo(const ::android::hardware::audio::common::V2_0::AudioPortConfig::Ext::AudioPortConfigMixExt& o, ::std::ostream*);
// operator== and operator!= are not generated for AudioPortConfigMixExt

static inline std::string toString(const ::android::hardware::audio::common::V2_0::AudioPortConfig::Ext& o);
static inline void PrintTo(const ::android::hardware::audio::common::V2_0::AudioPortConfig::Ext& o, ::std::ostream*);
// operator== and operator!= are not generated for Ext

static inline std::string toString(const ::android::hardware::audio::common::V2_0::AudioPortConfig& o);
static inline void PrintTo(const ::android::hardware::audio::common::V2_0::AudioPortConfig& o, ::std::ostream*);
// operator== and operator!= are not generated for AudioPortConfig

static inline std::string toString(const ::android::hardware::audio::common::V2_0::AudioPortDeviceExt& o);
static inline void PrintTo(const ::android::hardware::audio::common::V2_0::AudioPortDeviceExt& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::audio::common::V2_0::AudioPortDeviceExt& lhs, const ::android::hardware::audio::common::V2_0::AudioPortDeviceExt& rhs);
static inline bool operator!=(const ::android::hardware::audio::common::V2_0::AudioPortDeviceExt& lhs, const ::android::hardware::audio::common::V2_0::AudioPortDeviceExt& rhs);

template<typename>
static inline std::string toString(int32_t o);
static inline std::string toString(::android::hardware::audio::common::V2_0::AudioMixLatencyClass o);
static inline void PrintTo(::android::hardware::audio::common::V2_0::AudioMixLatencyClass o, ::std::ostream* os);
constexpr int32_t operator|(const ::android::hardware::audio::common::V2_0::AudioMixLatencyClass lhs, const ::android::hardware::audio::common::V2_0::AudioMixLatencyClass rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const int32_t lhs, const ::android::hardware::audio::common::V2_0::AudioMixLatencyClass rhs) {
    return static_cast<int32_t>(lhs | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const ::android::hardware::audio::common::V2_0::AudioMixLatencyClass lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | rhs);
}
constexpr int32_t operator&(const ::android::hardware::audio::common::V2_0::AudioMixLatencyClass lhs, const ::android::hardware::audio::common::V2_0::AudioMixLatencyClass rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const int32_t lhs, const ::android::hardware::audio::common::V2_0::AudioMixLatencyClass rhs) {
    return static_cast<int32_t>(lhs & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const ::android::hardware::audio::common::V2_0::AudioMixLatencyClass lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & rhs);
}
constexpr int32_t &operator|=(int32_t& v, const ::android::hardware::audio::common::V2_0::AudioMixLatencyClass e) {
    v |= static_cast<int32_t>(e);
    return v;
}
constexpr int32_t &operator&=(int32_t& v, const ::android::hardware::audio::common::V2_0::AudioMixLatencyClass e) {
    v &= static_cast<int32_t>(e);
    return v;
}

static inline std::string toString(const ::android::hardware::audio::common::V2_0::AudioPortMixExt& o);
static inline void PrintTo(const ::android::hardware::audio::common::V2_0::AudioPortMixExt& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::audio::common::V2_0::AudioPortMixExt& lhs, const ::android::hardware::audio::common::V2_0::AudioPortMixExt& rhs);
static inline bool operator!=(const ::android::hardware::audio::common::V2_0::AudioPortMixExt& lhs, const ::android::hardware::audio::common::V2_0::AudioPortMixExt& rhs);

static inline std::string toString(const ::android::hardware::audio::common::V2_0::AudioPortSessionExt& o);
static inline void PrintTo(const ::android::hardware::audio::common::V2_0::AudioPortSessionExt& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::audio::common::V2_0::AudioPortSessionExt& lhs, const ::android::hardware::audio::common::V2_0::AudioPortSessionExt& rhs);
static inline bool operator!=(const ::android::hardware::audio::common::V2_0::AudioPortSessionExt& lhs, const ::android::hardware::audio::common::V2_0::AudioPortSessionExt& rhs);

static inline std::string toString(const ::android::hardware::audio::common::V2_0::AudioPort::Ext& o);
static inline void PrintTo(const ::android::hardware::audio::common::V2_0::AudioPort::Ext& o, ::std::ostream*);
// operator== and operator!= are not generated for Ext

static inline std::string toString(const ::android::hardware::audio::common::V2_0::AudioPort& o);
static inline void PrintTo(const ::android::hardware::audio::common::V2_0::AudioPort& o, ::std::ostream*);
// operator== and operator!= are not generated for AudioPort

static inline std::string toString(const ::android::hardware::audio::common::V2_0::ThreadInfo& o);
static inline void PrintTo(const ::android::hardware::audio::common::V2_0::ThreadInfo& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::audio::common::V2_0::ThreadInfo& lhs, const ::android::hardware::audio::common::V2_0::ThreadInfo& rhs);
static inline bool operator!=(const ::android::hardware::audio::common::V2_0::ThreadInfo& lhs, const ::android::hardware::audio::common::V2_0::ThreadInfo& rhs);

//
// type header definitions for package
//

template<>
inline std::string toString<::android::hardware::audio::common::V2_0::AudioHandleConsts>(int32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::audio::common::V2_0::AudioHandleConsts> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::audio::common::V2_0::AudioHandleConsts::AUDIO_IO_HANDLE_NONE) == static_cast<int32_t>(::android::hardware::audio::common::V2_0::AudioHandleConsts::AUDIO_IO_HANDLE_NONE)) {
        os += (first ? "" : " | ");
        os += "AUDIO_IO_HANDLE_NONE";
        first = false;
        flipped |= ::android::hardware::audio::common::V2_0::AudioHandleConsts::AUDIO_IO_HANDLE_NONE;
    }
    if ((o & ::android::hardware::audio::common::V2_0::AudioHandleConsts::AUDIO_MODULE_HANDLE_NONE) == static_cast<int32_t>(::android::hardware::audio::common::V2_0::AudioHandleConsts::AUDIO_MODULE_HANDLE_NONE)) {
        os += (first ? "" : " | ");
        os += "AUDIO_MODULE_HANDLE_NONE";
        first = false;
        flipped |= ::android::hardware::audio::common::V2_0::AudioHandleConsts::AUDIO_MODULE_HANDLE_NONE;
    }
    if ((o & ::android::hardware::audio::common::V2_0::AudioHandleConsts::AUDIO_PORT_HANDLE_NONE) == static_cast<int32_t>(::android::hardware::audio::common::V2_0::AudioHandleConsts::AUDIO_PORT_HANDLE_NONE)) {
        os += (first ? "" : " | ");
        os += "AUDIO_PORT_HANDLE_NONE";
        first = false;
        flipped |= ::android::hardware::audio::common::V2_0::AudioHandleConsts::AUDIO_PORT_HANDLE_NONE;
    }
    if ((o & ::android::hardware::audio::common::V2_0::AudioHandleConsts::AUDIO_PATCH_HANDLE_NONE) == static_cast<int32_t>(::android::hardware::audio::common::V2_0::AudioHandleConsts::AUDIO_PATCH_HANDLE_NONE)) {
        os += (first ? "" : " | ");
        os += "AUDIO_PATCH_HANDLE_NONE";
        first = false;
        flipped |= ::android::hardware::audio::common::V2_0::AudioHandleConsts::AUDIO_PATCH_HANDLE_NONE;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::audio::common::V2_0::AudioHandleConsts o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::audio::common::V2_0::AudioHandleConsts::AUDIO_IO_HANDLE_NONE) {
        return "AUDIO_IO_HANDLE_NONE";
    }
    if (o == ::android::hardware::audio::common::V2_0::AudioHandleConsts::AUDIO_MODULE_HANDLE_NONE) {
        return "AUDIO_MODULE_HANDLE_NONE";
    }
    if (o == ::android::hardware::audio::common::V2_0::AudioHandleConsts::AUDIO_PORT_HANDLE_NONE) {
        return "AUDIO_PORT_HANDLE_NONE";
    }
    if (o == ::android::hardware::audio::common::V2_0::AudioHandleConsts::AUDIO_PATCH_HANDLE_NONE) {
        return "AUDIO_PATCH_HANDLE_NONE";
    }
    std::string os;
    os += toHexString(static_cast<int32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::audio::common::V2_0::AudioHandleConsts o, ::std::ostream* os) {
    *os << toString(o);
}

static inline std::string toString(const ::android::hardware::audio::common::V2_0::Uuid& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".timeLow = ";
    os += ::android::hardware::toString(o.timeLow);
    os += ", .timeMid = ";
    os += ::android::hardware::toString(o.timeMid);
    os += ", .versionAndTimeHigh = ";
    os += ::android::hardware::toString(o.versionAndTimeHigh);
    os += ", .variantAndClockSeqHigh = ";
    os += ::android::hardware::toString(o.variantAndClockSeqHigh);
    os += ", .node = ";
    os += ::android::hardware::toString(o.node);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::audio::common::V2_0::Uuid& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::audio::common::V2_0::Uuid& lhs, const ::android::hardware::audio::common::V2_0::Uuid& rhs) {
    if (lhs.timeLow != rhs.timeLow) {
        return false;
    }
    if (lhs.timeMid != rhs.timeMid) {
        return false;
    }
    if (lhs.versionAndTimeHigh != rhs.versionAndTimeHigh) {
        return false;
    }
    if (lhs.variantAndClockSeqHigh != rhs.variantAndClockSeqHigh) {
        return false;
    }
    if (lhs.node != rhs.node) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::audio::common::V2_0::Uuid& lhs, const ::android::hardware::audio::common::V2_0::Uuid& rhs){
    return !(lhs == rhs);
}

template<>
inline std::string toString<::android::hardware::audio::common::V2_0::AudioStreamType>(int32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::audio::common::V2_0::AudioStreamType> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::audio::common::V2_0::AudioStreamType::DEFAULT) == static_cast<int32_t>(::android::hardware::audio::common::V2_0::AudioStreamType::DEFAULT)) {
        os += (first ? "" : " | ");
        os += "DEFAULT";
        first = false;
        flipped |= ::android::hardware::audio::common::V2_0::AudioStreamType::DEFAULT;
    }
    if ((o & ::android::hardware::audio::common::V2_0::AudioStreamType::MIN) == static_cast<int32_t>(::android::hardware::audio::common::V2_0::AudioStreamType::MIN)) {
        os += (first ? "" : " | ");
        os += "MIN";
        first = false;
        flipped |= ::android::hardware::audio::common::V2_0::AudioStreamType::MIN;
    }
    if ((o & ::android::hardware::audio::common::V2_0::AudioStreamType::VOICE_CALL) == static_cast<int32_t>(::android::hardware::audio::common::V2_0::AudioStreamType::VOICE_CALL)) {
        os += (first ? "" : " | ");
        os += "VOICE_CALL";
        first = false;
        flipped |= ::android::hardware::audio::common::V2_0::AudioStreamType::VOICE_CALL;
    }
    if ((o & ::android::hardware::audio::common::V2_0::AudioStreamType::SYSTEM) == static_cast<int32_t>(::android::hardware::audio::common::V2_0::AudioStreamType::SYSTEM)) {
        os += (first ? "" : " | ");
        os += "SYSTEM";
        first = false;
        flipped |= ::android::hardware::audio::common::V2_0::AudioStreamType::SYSTEM;
    }
    if ((o & ::android::hardware::audio::common::V2_0::AudioStreamType::RING) == static_cast<int32_t>(::android::hardware::audio::common::V2_0::AudioStreamType::RING)) {
        os += (first ? "" : " | ");
        os += "RING";
        first = false;
        flipped |= ::android::hardware::audio::common::V2_0::AudioStreamType::RING;
    }
    if ((o & ::android::hardware::audio::common::V2_0::AudioStreamType::MUSIC) == static_cast<int32_t>(::android::hardware::audio::common::V2_0::AudioStreamType::MUSIC)) {
        os += (first ? "" : " | ");
        os += "MUSIC";
        first = false;
        flipped |= ::android::hardware::audio::common::V2_0::AudioStreamType::MUSIC;
    }
    if ((o & ::android::hardware::audio::common::V2_0::AudioStreamType::ALARM) == static_cast<int32_t>(::android::hardware::audio::common::V2_0::AudioStreamType::ALARM)) {
        os += (first ? "" : " | ");
        os += "ALARM";
        first = false;
        flipped |= ::android::hardware::audio::common::V2_0::AudioStreamType::ALARM;
    }
    if ((o & ::android::hardware::audio::common::V2_0::AudioStreamType::NOTIFICATION) == static_cast<int32_t>(::android::hardware::audio::common::V2_0::AudioStreamType::NOTIFICATION)) {
        os += (first ? "" : " | ");
        os += "NOTIFICATION";
        first = false;
        flipped |= ::android::hardware::audio::common::V2_0::AudioStreamType::NOTIFICATION;
    }
    if ((o & ::android::hardware::audio::common::V2_0::AudioStreamType::BLUETOOTH_SCO) == static_cast<int32_t>(::android::hardware::audio::common::V2_0::AudioStreamType::BLUETOOTH_SCO)) {
        os += (first ? "" : " | ");
        os += "BLUETOOTH_SCO";
        first = false;
        flipped |= ::android::hardware::audio::common::V2_0::AudioStreamType::BLUETOOTH_SCO;
    }
    if ((o & ::android::hardware::audio::common::V2_0::AudioStreamType::ENFORCED_AUDIBLE) == static_cast<int32_t>(::android::hardware::audio::common::V2_0::AudioStreamType::ENFORCED_AUDIBLE)) {
        os += (first ? "" : " | ");
        os += "ENFORCED_AUDIBLE";
        first = false;
        flipped |= ::android::hardware::audio::common::V2_0::AudioStreamType::ENFORCED_AUDIBLE;
    }
    if ((o & ::android::hardware::audio::common::V2_0::AudioStreamType::DTMF) == static_cast<int32_t>(::android::hardware::audio::common::V2_0::AudioStreamType::DTMF)) {
        os += (first ? "" : " | ");
        os += "DTMF";
        first = false;
        flipped |= ::android::hardware::audio::common::V2_0::AudioStreamType::DTMF;
    }
    if ((o & ::android::hardware::audio::common::V2_0::AudioStreamType::TTS) == static_cast<int32_t>(::android::hardware::audio::common::V2_0::AudioStreamType::TTS)) {
        os += (first ? "" : " | ");
        os += "TTS";
        first = false;
        flipped |= ::android::hardware::audio::common::V2_0::AudioStreamType::TTS;
    }
    if ((o & ::android::hardware::audio::common::V2_0::AudioStreamType::ACCESSIBILITY) == static_cast<int32_t>(::android::hardware::audio::common::V2_0::AudioStreamType::ACCESSIBILITY)) {
        os += (first ? "" : " | ");
        os += "ACCESSIBILITY";
        first = false;
        flipped |= ::android::hardware::audio::common::V2_0::AudioStreamType::ACCESSIBILITY;
    }
    if ((o & ::android::hardware::audio::common::V2_0::AudioStreamType::REROUTING) == static_cast<int32_t>(::android::hardware::audio::common::V2_0::AudioStreamType::REROUTING)) {
        os += (first ? "" : " | ");
        os += "REROUTING";
        first = false;
        flipped |= ::android::hardware::audio::common::V2_0::AudioStreamType::REROUTING;
    }
    if ((o & ::android::hardware::audio::common::V2_0::AudioStreamType::PATCH) == static_cast<int32_t>(::android::hardware::audio::common::V2_0::AudioStreamType::PATCH)) {
        os += (first ? "" : " | ");
        os += "PATCH";
        first = false;
        flipped |= ::android::hardware::audio::common::V2_0::AudioStreamType::PATCH;
    }
    if ((o & ::android::hardware::audio::common::V2_0::AudioStreamType::PUBLIC_CNT) == static_cast<int32_t>(::android::hardware::audio::common::V2_0::AudioStreamType::PUBLIC_CNT)) {
        os += (first ? "" : " | ");
        os += "PUBLIC_CNT";
        first = false;
        flipped |= ::android::hardware::audio::common::V2_0::AudioStreamType::PUBLIC_CNT;
    }
    if ((o & ::android::hardware::audio::common::V2_0::AudioStreamType::FOR_POLICY_CNT) == static_cast<int32_t>(::android::hardware::audio::common::V2_0::AudioStreamType::FOR_POLICY_CNT)) {
        os += (first ? "" : " | ");
        os += "FOR_POLICY_CNT";
        first = false;
        flipped |= ::android::hardware::audio::common::V2_0::AudioStreamType::FOR_POLICY_CNT;
    }
    if ((o & ::android::hardware::audio::common::V2_0::AudioStreamType::CNT) == static_cast<int32_t>(::android::hardware::audio::common::V2_0::AudioStreamType::CNT)) {
        os += (first ? "" : " | ");
        os += "CNT";
        first = false;
        flipped |= ::android::hardware::audio::common::V2_0::AudioStreamType::CNT;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::audio::common::V2_0::AudioStreamType o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::audio::common::V2_0::AudioStreamType::DEFAULT) {
        return "DEFAULT";
    }
    if (o == ::android::hardware::audio::common::V2_0::AudioStreamType::MIN) {
        return "MIN";
    }
    if (o == ::android::hardware::audio::common::V2_0::AudioStreamType::VOICE_CALL) {
        return "VOICE_CALL";
    }
    if (o == ::android::hardware::audio::common::V2_0::AudioStreamType::SYSTEM) {
        return "SYSTEM";
    }
    if (o == ::android::hardware::audio::common::V2_0::AudioStreamType::RING) {
        return "RING";
    }
    if (o == ::android::hardware::audio::common::V2_0::AudioStreamType::MUSIC) {
        return "MUSIC";
    }
    if (o == ::android::hardware::audio::common::V2_0::AudioStreamType::ALARM) {
        return "ALARM";
    }
    if (o == ::android::hardware::audio::common::V2_0::AudioStreamType::NOTIFICATION) {
        return "NOTIFICATION";
    }
    if (o == ::android::hardware::audio::common::V2_0::AudioStreamType::BLUETOOTH_SCO) {
        return "BLUETOOTH_SCO";
    }
    if (o == ::android::hardware::audio::common::V2_0::AudioStreamType::ENFORCED_AUDIBLE) {
        return "ENFORCED_AUDIBLE";
    }
    if (o == ::android::hardware::audio::common::V2_0::AudioStreamType::DTMF) {
        return "DTMF";
    }
    if (o == ::android::hardware::audio::common::V2_0::AudioStreamType::TTS) {
        return "TTS";
    }
    if (o == ::android::hardware::audio::common::V2_0::AudioStreamType::ACCESSIBILITY) {
        return "ACCESSIBILITY";
    }
    if (o == ::android::hardware::audio::common::V2_0::AudioStreamType::REROUTING) {
        return "REROUTING";
    }
    if (o == ::android::hardware::audio::common::V2_0::AudioStreamType::PATCH) {
        return "PATCH";
    }
    if (o == ::android::hardware::audio::common::V2_0::AudioStreamType::PUBLIC_CNT) {
        return "PUBLIC_CNT";
    }
    if (o == ::android::hardware::audio::common::V2_0::AudioStreamType::FOR_POLICY_CNT) {
        return "FOR_POLICY_CNT";
    }
    if (o == ::android::hardware::audio::common::V2_0::AudioStreamType::CNT) {
        return "CNT";
    }
    std::string os;
    os += toHexString(static_cast<int32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::audio::common::V2_0::AudioStreamType o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::audio::common::V2_0::AudioSource>(int32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::audio::common::V2_0::AudioSource> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::audio::common::V2_0::AudioSource::DEFAULT) == static_cast<int32_t>(::android::hardware::audio::common::V2_0::AudioSource::DEFAULT)) {
        os += (first ? "" : " | ");
        os += "DEFAULT";
        first = false;
        flipped |= ::android::hardware::audio::common::V2_0::AudioSource::DEFAULT;
    }
    if ((o & ::android::hardware::audio::common::V2_0::AudioSource::MIC) == static_cast<int32_t>(::android::hardware::audio::common::V2_0::AudioSource::MIC)) {
        os += (first ? "" : " | ");
        os += "MIC";
        first = false;
        flipped |= ::android::hardware::audio::common::V2_0::AudioSource::MIC;
    }
    if ((o & ::android::hardware::audio::common::V2_0::AudioSource::VOICE_UPLINK) == static_cast<int32_t>(::android::hardware::audio::common::V2_0::AudioSource::VOICE_UPLINK)) {
        os += (first ? "" : " | ");
        os += "VOICE_UPLINK";
        first = false;
        flipped |= ::android::hardware::audio::common::V2_0::AudioSource::VOICE_UPLINK;
    }
    if ((o & ::android::hardware::audio::common::V2_0::AudioSource::VOICE_DOWNLINK) == static_cast<int32_t>(::android::hardware::audio::common::V2_0::AudioSource::VOICE_DOWNLINK)) {
        os += (first ? "" : " | ");
        os += "VOICE_DOWNLINK";
        first = false;
        flipped |= ::android::hardware::audio::common::V2_0::AudioSource::VOICE_DOWNLINK;
    }
    if ((o & ::android::hardware::audio::common::V2_0::AudioSource::VOICE_CALL) == static_cast<int32_t>(::android::hardware::audio::common::V2_0::AudioSource::VOICE_CALL)) {
        os += (first ? "" : " | ");
        os += "VOICE_CALL";
        first = false;
        flipped |= ::android::hardware::audio::common::V2_0::AudioSource::VOICE_CALL;
    }
    if ((o & ::android::hardware::audio::common::V2_0::AudioSource::CAMCORDER) == static_cast<int32_t>(::android::hardware::audio::common::V2_0::AudioSource::CAMCORDER)) {
        os += (first ? "" : " | ");
        os += "CAMCORDER";
        first = false;
        flipped |= ::android::hardware::audio::common::V2_0::AudioSource::CAMCORDER;
    }
    if ((o & ::android::hardware::audio::common::V2_0::AudioSource::VOICE_RECOGNITION) == static_cast<int32_t>(::android::hardware::audio::common::V2_0::AudioSource::VOICE_RECOGNITION)) {
        os += (first ? "" : " | ");
        os += "VOICE_RECOGNITION";
        first = false;
        flipped |= ::android::hardware::audio::common::V2_0::AudioSource::VOICE_RECOGNITION;
    }
    if ((o & ::android::hardware::audio::common::V2_0::AudioSource::VOICE_COMMUNICATION) == static_cast<int32_t>(::android::hardware::audio::common::V2_0::AudioSource::VOICE_COMMUNICATION)) {
        os += (first ? "" : " | ");
        os += "VOICE_COMMUNICATION";
        first = false;
        flipped |= ::android::hardware::audio::common::V2_0::AudioSource::VOICE_COMMUNICATION;
    }
    if ((o & ::android::hardware::audio::common::V2_0::AudioSource::REMOTE_SUBMIX) == static_cast<int32_t>(::android::hardware::audio::common::V2_0::AudioSource::REMOTE_SUBMIX)) {
        os += (first ? "" : " | ");
        os += "REMOTE_SUBMIX";
        first = false;
        flipped |= ::android::hardware::audio::common::V2_0::AudioSource::REMOTE_SUBMIX;
    }
    if ((o & ::android::hardware::audio::common::V2_0::AudioSource::UNPROCESSED) == static_cast<int32_t>(::android::hardware::audio::common::V2_0::AudioSource::UNPROCESSED)) {
        os += (first ? "" : " | ");
        os += "UNPROCESSED";
        first = false;
        flipped |= ::android::hardware::audio::common::V2_0::AudioSource::UNPROCESSED;
    }
    if ((o & ::android::hardware::audio::common::V2_0::AudioSource::CNT) == static_cast<int32_t>(::android::hardware::audio::common::V2_0::AudioSource::CNT)) {
        os += (first ? "" : " | ");
        os += "CNT";
        first = false;
        flipped |= ::android::hardware::audio::common::V2_0::AudioSource::CNT;
    }
    if ((o & ::android::hardware::audio::common::V2_0::AudioSource::MAX) == static_cast<int32_t>(::android::hardware::audio::common::V2_0::AudioSource::MAX)) {
        os += (first ? "" : " | ");
        os += "MAX";
        first = false;
        flipped |= ::android::hardware::audio::common::V2_0::AudioSource::MAX;
    }
    if ((o & ::android::hardware::audio::common::V2_0::AudioSource::FM_TUNER) == static_cast<int32_t>(::android::hardware::audio::common::V2_0::AudioSource::FM_TUNER)) {
        os += (first ? "" : " | ");
        os += "FM_TUNER";
        first = false;
        flipped |= ::android::hardware::audio::common::V2_0::AudioSource::FM_TUNER;
    }
    if ((o & ::android::hardware::audio::common::V2_0::AudioSource::HOTWORD) == static_cast<int32_t>(::android::hardware::audio::common::V2_0::AudioSource::HOTWORD)) {
        os += (first ? "" : " | ");
        os += "HOTWORD";
        first = false;
        flipped |= ::android::hardware::audio::common::V2_0::AudioSource::HOTWORD;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::audio::common::V2_0::AudioSource o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::audio::common::V2_0::AudioSource::DEFAULT) {
        return "DEFAULT";
    }
    if (o == ::android::hardware::audio::common::V2_0::AudioSource::MIC) {
        return "MIC";
    }
    if (o == ::android::hardware::audio::common::V2_0::AudioSource::VOICE_UPLINK) {
        return "VOICE_UPLINK";
    }
    if (o == ::android::hardware::audio::common::V2_0::AudioSource::VOICE_DOWNLINK) {
        return "VOICE_DOWNLINK";
    }
    if (o == ::android::hardware::audio::common::V2_0::AudioSource::VOICE_CALL) {
        return "VOICE_CALL";
    }
    if (o == ::android::hardware::audio::common::V2_0::AudioSource::CAMCORDER) {
        return "CAMCORDER";
    }
    if (o == ::android::hardware::audio::common::V2_0::AudioSource::VOICE_RECOGNITION) {
        return "VOICE_RECOGNITION";
    }
    if (o == ::android::hardware::audio::common::V2_0::AudioSource::VOICE_COMMUNICATION) {
        return "VOICE_COMMUNICATION";
    }
    if (o == ::android::hardware::audio::common::V2_0::AudioSource::REMOTE_SUBMIX) {
        return "REMOTE_SUBMIX";
    }
    if (o == ::android::hardware::audio::common::V2_0::AudioSource::UNPROCESSED) {
        return "UNPROCESSED";
    }
    if (o == ::android::hardware::audio::common::V2_0::AudioSource::CNT) {
        return "CNT";
    }
    if (o == ::android::hardware::audio::common::V2_0::AudioSource::MAX) {
        return "MAX";
    }
    if (o == ::android::hardware::audio::common::V2_0::AudioSource::FM_TUNER) {
        return "FM_TUNER";
    }
    if (o == ::android::hardware::audio::common::V2_0::AudioSource::HOTWORD) {
        return "HOTWORD";
    }
    std::string os;
    os += toHexString(static_cast<int32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::audio::common::V2_0::AudioSource o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::audio::common::V2_0::AudioSessionConsts>(int32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::audio::common::V2_0::AudioSessionConsts> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::audio::common::V2_0::AudioSessionConsts::OUTPUT_STAGE) == static_cast<int32_t>(::android::hardware::audio::common::V2_0::AudioSessionConsts::OUTPUT_STAGE)) {
        os += (first ? "" : " | ");
        os += "OUTPUT_STAGE";
        first = false;
        flipped |= ::android::hardware::audio::common::V2_0::AudioSessionConsts::OUTPUT_STAGE;
    }
    if ((o & ::android::hardware::audio::common::V2_0::AudioSessionConsts::OUTPUT_MIX) == static_cast<int32_t>(::android::hardware::audio::common::V2_0::AudioSessionConsts::OUTPUT_MIX)) {
        os += (first ? "" : " | ");
        os += "OUTPUT_MIX";
        first = false;
        flipped |= ::android::hardware::audio::common::V2_0::AudioSessionConsts::OUTPUT_MIX;
    }
    if ((o & ::android::hardware::audio::common::V2_0::AudioSessionConsts::ALLOCATE) == static_cast<int32_t>(::android::hardware::audio::common::V2_0::AudioSessionConsts::ALLOCATE)) {
        os += (first ? "" : " | ");
        os += "ALLOCATE";
        first = false;
        flipped |= ::android::hardware::audio::common::V2_0::AudioSessionConsts::ALLOCATE;
    }
    if ((o & ::android::hardware::audio::common::V2_0::AudioSessionConsts::NONE) == static_cast<int32_t>(::android::hardware::audio::common::V2_0::AudioSessionConsts::NONE)) {
        os += (first ? "" : " | ");
        os += "NONE";
        first = false;
        flipped |= ::android::hardware::audio::common::V2_0::AudioSessionConsts::NONE;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::audio::common::V2_0::AudioSessionConsts o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::audio::common::V2_0::AudioSessionConsts::OUTPUT_STAGE) {
        return "OUTPUT_STAGE";
    }
    if (o == ::android::hardware::audio::common::V2_0::AudioSessionConsts::OUTPUT_MIX) {
        return "OUTPUT_MIX";
    }
    if (o == ::android::hardware::audio::common::V2_0::AudioSessionConsts::ALLOCATE) {
        return "ALLOCATE";
    }
    if (o == ::android::hardware::audio::common::V2_0::AudioSessionConsts::NONE) {
        return "NONE";
    }
    std::string os;
    os += toHexString(static_cast<int32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::audio::common::V2_0::AudioSessionConsts o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::audio::common::V2_0::AudioFormat>(uint32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::audio::common::V2_0::AudioFormat> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::audio::common::V2_0::AudioFormat::INVALID) == static_cast<uint32_t>(::android::hardware::audio::common::V2_0::AudioFormat::INVALID)) {
        os += (first ? "" : " | ");
        os += "INVALID";
        first = false;
        flipped |= ::android::hardware::audio::common::V2_0::AudioFormat::INVALID;
    }
    if ((o & ::android::hardware::audio::common::V2_0::AudioFormat::DEFAULT) == static_cast<uint32_t>(::android::hardware::audio::common::V2_0::AudioFormat::DEFAULT)) {
        os += (first ? "" : " | ");
        os += "DEFAULT";
        first = false;
        flipped |= ::android::hardware::audio::common::V2_0::AudioFormat::DEFAULT;
    }
    if ((o & ::android::hardware::audio::common::V2_0::AudioFormat::PCM) == static_cast<uint32_t>(::android::hardware::audio::common::V2_0::AudioFormat::PCM)) {
        os += (first ? "" : " | ");
        os += "PCM";
        first = false;
        flipped |= ::android::hardware::audio::common::V2_0::AudioFormat::PCM;
    }
    if ((o & ::android::hardware::audio::common::V2_0::AudioFormat::MP3) == static_cast<uint32_t>(::android::hardware::audio::common::V2_0::AudioFormat::MP3)) {
        os += (first ? "" : " | ");
        os += "MP3";
        first = false;
        flipped |= ::android::hardware::audio::common::V2_0::AudioFormat::MP3;
    }
    if ((o & ::android::hardware::audio::common::V2_0::AudioFormat::AMR_NB) == static_cast<uint32_t>(::android::hardware::audio::common::V2_0::AudioFormat::AMR_NB)) {
        os += (first ? "" : " | ");
        os += "AMR_NB";
        first = false;
        flipped |= ::android::hardware::audio::common::V2_0::AudioFormat::AMR_NB;
    }
    if ((o & ::android::hardware::audio::common::V2_0::AudioFormat::AMR_WB) == static_cast<uint32_t>(::android::hardware::audio::common::V2_0::AudioFormat::AMR_WB)) {
        os += (first ? "" : " | ");
        os += "AMR_WB";
        first = false;
        flipped |= ::android::hardware::audio::common::V2_0::AudioFormat::AMR_WB;
    }
    if ((o & ::android::hardware::audio::common::V2_0::AudioFormat::AAC) == static_cast<uint32_t>(::android::hardware::audio::common::V2_0::AudioFormat::AAC)) {
        os += (first ? "" : " | ");
        os += "AAC";
        first = false;
        flipped |= ::android::hardware::audio::common::V2_0::AudioFormat::AAC;
    }
    if ((o & ::android::hardware::audio::common::V2_0::AudioFormat::HE_AAC_V1) == static_cast<uint32_t>(::android::hardware::audio::common::V2_0::AudioFormat::HE_AAC_V1)) {
        os += (first ? "" : " | ");
        os += "HE_AAC_V1";
        first = false;
        flipped |= ::android::hardware::audio::common::V2_0::AudioFormat::HE_AAC_V1;
    }
    if ((o & ::android::hardware::audio::common::V2_0::AudioFormat::HE_AAC_V2) == static_cast<uint32_t>(::android::hardware::audio::common::V2_0::AudioFormat::HE_AAC_V2)) {
        os += (first ? "" : " | ");
        os += "HE_AAC_V2";
        first = false;
        flipped |= ::android::hardware::audio::common::V2_0::AudioFormat::HE_AAC_V2;
    }
    if ((o & ::android::hardware::audio::common::V2_0::AudioFormat::VORBIS) == static_cast<uint32_t>(::android::hardware::audio::common::V2_0::AudioFormat::VORBIS)) {
        os += (first ? "" : " | ");
        os += "VORBIS";
        first = false;
        flipped |= ::android::hardware::audio::common::V2_0::AudioFormat::VORBIS;
    }
    if ((o & ::android::hardware::audio::common::V2_0::AudioFormat::OPUS) == static_cast<uint32_t>(::android::hardware::audio::common::V2_0::AudioFormat::OPUS)) {
        os += (first ? "" : " | ");
        os += "OPUS";
        first = false;
        flipped |= ::android::hardware::audio::common::V2_0::AudioFormat::OPUS;
    }
    if ((o & ::android::hardware::audio::common::V2_0::AudioFormat::AC3) == static_cast<uint32_t>(::android::hardware::audio::common::V2_0::AudioFormat::AC3)) {
        os += (first ? "" : " | ");
        os += "AC3";
        first = false;
        flipped |= ::android::hardware::audio::common::V2_0::AudioFormat::AC3;
    }
    if ((o & ::android::hardware::audio::common::V2_0::AudioFormat::E_AC3) == static_cast<uint32_t>(::android::hardware::audio::common::V2_0::AudioFormat::E_AC3)) {
        os += (first ? "" : " | ");
        os += "E_AC3";
        first = false;
        flipped |= ::android::hardware::audio::common::V2_0::AudioFormat::E_AC3;
    }
    if ((o & ::android::hardware::audio::common::V2_0::AudioFormat::DTS) == static_cast<uint32_t>(::android::hardware::audio::common::V2_0::AudioFormat::DTS)) {
        os += (first ? "" : " | ");
        os += "DTS";
        first = false;
        flipped |= ::android::hardware::audio::common::V2_0::AudioFormat::DTS;
    }
    if ((o & ::android::hardware::audio::common::V2_0::AudioFormat::DTS_HD) == static_cast<uint32_t>(::android::hardware::audio::common::V2_0::AudioFormat::DTS_HD)) {
        os += (first ? "" : " | ");
        os += "DTS_HD";
        first = false;
        flipped |= ::android::hardware::audio::common::V2_0::AudioFormat::DTS_HD;
    }
    if ((o & ::android::hardware::audio::common::V2_0::AudioFormat::IEC61937) == static_cast<uint32_t>(::android::hardware::audio::common::V2_0::AudioFormat::IEC61937)) {
        os += (first ? "" : " | ");
        os += "IEC61937";
        first = false;
        flipped |= ::android::hardware::audio::common::V2_0::AudioFormat::IEC61937;
    }
    if ((o & ::android::hardware::audio::common::V2_0::AudioFormat::DOLBY_TRUEHD) == static_cast<uint32_t>(::android::hardware::audio::common::V2_0::AudioFormat::DOLBY_TRUEHD)) {
        os += (first ? "" : " | ");
        os += "DOLBY_TRUEHD";
        first = false;
        flipped |= ::android::hardware::audio::common::V2_0::AudioFormat::DOLBY_TRUEHD;
    }
    if ((o & ::android::hardware::audio::common::V2_0::AudioFormat::EVRC) == static_cast<uint32_t>(::android::hardware::audio::common::V2_0::AudioFormat::EVRC)) {
        os += (first ? "" : " | ");
        os += "EVRC";
        first = false;
        flipped |= ::android::hardware::audio::common::V2_0::AudioFormat::EVRC;
    }
    if ((o & ::android::hardware::audio::common::V2_0::AudioFormat::EVRCB) == static_cast<uint32_t>(::android::hardware::audio::common::V2_0::AudioFormat::EVRCB)) {
        os += (first ? "" : " | ");
        os += "EVRCB";
        first = false;
        flipped |= ::android::hardware::audio::common::V2_0::AudioFormat::EVRCB;
    }
    if ((o & ::android::hardware::audio::common::V2_0::AudioFormat::EVRCWB) == static_cast<uint32_t>(::android::hardware::audio::common::V2_0::AudioFormat::EVRCWB)) {
        os += (first ? "" : " | ");
        os += "EVRCWB";
        first = false;
        flipped |= ::android::hardware::audio::common::V2_0::AudioFormat::EVRCWB;
    }
    if ((o & ::android::hardware::audio::common::V2_0::AudioFormat::EVRCNW) == static_cast<uint32_t>(::android::hardware::audio::common::V2_0::AudioFormat::EVRCNW)) {
        os += (first ? "" : " | ");
        os += "EVRCNW";
        first = false;
        flipped |= ::android::hardware::audio::common::V2_0::AudioFormat::EVRCNW;
    }
    if ((o & ::android::hardware::audio::common::V2_0::AudioFormat::AAC_ADIF) == static_cast<uint32_t>(::android::hardware::audio::common::V2_0::AudioFormat::AAC_ADIF)) {
        os += (first ? "" : " | ");
        os += "AAC_ADIF";
        first = false;
        flipped |= ::android::hardware::audio::common::V2_0::AudioFormat::AAC_ADIF;
    }
    if ((o & ::android::hardware::audio::common::V2_0::AudioFormat::WMA) == static_cast<uint32_t>(::android::hardware::audio::common::V2_0::AudioFormat::WMA)) {
        os += (first ? "" : " | ");
        os += "WMA";
        first = false;
        flipped |= ::android::hardware::audio::common::V2_0::AudioFormat::WMA;
    }
    if ((o & ::android::hardware::audio::common::V2_0::AudioFormat::WMA_PRO) == static_cast<uint32_t>(::android::hardware::audio::common::V2_0::AudioFormat::WMA_PRO)) {
        os += (first ? "" : " | ");
        os += "WMA_PRO";
        first = false;
        flipped |= ::android::hardware::audio::common::V2_0::AudioFormat::WMA_PRO;
    }
    if ((o & ::android::hardware::audio::common::V2_0::AudioFormat::AMR_WB_PLUS) == static_cast<uint32_t>(::android::hardware::audio::common::V2_0::AudioFormat::AMR_WB_PLUS)) {
        os += (first ? "" : " | ");
        os += "AMR_WB_PLUS";
        first = false;
        flipped |= ::android::hardware::audio::common::V2_0::AudioFormat::AMR_WB_PLUS;
    }
    if ((o & ::android::hardware::audio::common::V2_0::AudioFormat::MP2) == static_cast<uint32_t>(::android::hardware::audio::common::V2_0::AudioFormat::MP2)) {
        os += (first ? "" : " | ");
        os += "MP2";
        first = false;
        flipped |= ::android::hardware::audio::common::V2_0::AudioFormat::MP2;
    }
    if ((o & ::android::hardware::audio::common::V2_0::AudioFormat::QCELP) == static_cast<uint32_t>(::android::hardware::audio::common::V2_0::AudioFormat::QCELP)) {
        os += (first ? "" : " | ");
        os += "QCELP";
        first = false;
        flipped |= ::android::hardware::audio::common::V2_0::AudioFormat::QCELP;
    }
    if ((o & ::android::hardware::audio::common::V2_0::AudioFormat::DSD) == static_cast<uint32_t>(::android::hardware::audio::common::V2_0::AudioFormat::DSD)) {
        os += (first ? "" : " | ");
        os += "DSD";
        first = false;
        flipped |= ::android::hardware::audio::common::V2_0::AudioFormat::DSD;
    }
    if ((o & ::android::hardware::audio::common::V2_0::AudioFormat::FLAC) == static_cast<uint32_t>(::android::hardware::audio::common::V2_0::AudioFormat::FLAC)) {
        os += (first ? "" : " | ");
        os += "FLAC";
        first = false;
        flipped |= ::android::hardware::audio::common::V2_0::AudioFormat::FLAC;
    }
    if ((o & ::android::hardware::audio::common::V2_0::AudioFormat::ALAC) == static_cast<uint32_t>(::android::hardware::audio::common::V2_0::AudioFormat::ALAC)) {
        os += (first ? "" : " | ");
        os += "ALAC";
        first = false;
        flipped |= ::android::hardware::audio::common::V2_0::AudioFormat::ALAC;
    }
    if ((o & ::android::hardware::audio::common::V2_0::AudioFormat::APE) == static_cast<uint32_t>(::android::hardware::audio::common::V2_0::AudioFormat::APE)) {
        os += (first ? "" : " | ");
        os += "APE";
        first = false;
        flipped |= ::android::hardware::audio::common::V2_0::AudioFormat::APE;
    }
    if ((o & ::android::hardware::audio::common::V2_0::AudioFormat::AAC_ADTS) == static_cast<uint32_t>(::android::hardware::audio::common::V2_0::AudioFormat::AAC_ADTS)) {
        os += (first ? "" : " | ");
        os += "AAC_ADTS";
        first = false;
        flipped |= ::android::hardware::audio::common::V2_0::AudioFormat::AAC_ADTS;
    }
    if ((o & ::android::hardware::audio::common::V2_0::AudioFormat::SBC) == static_cast<uint32_t>(::android::hardware::audio::common::V2_0::AudioFormat::SBC)) {
        os += (first ? "" : " | ");
        os += "SBC";
        first = false;
        flipped |= ::android::hardware::audio::common::V2_0::AudioFormat::SBC;
    }
    if ((o & ::android::hardware::audio::common::V2_0::AudioFormat::APTX) == static_cast<uint32_t>(::android::hardware::audio::common::V2_0::AudioFormat::APTX)) {
        os += (first ? "" : " | ");
        os += "APTX";
        first = false;
        flipped |= ::android::hardware::audio::common::V2_0::AudioFormat::APTX;
    }
    if ((o & ::android::hardware::audio::common::V2_0::AudioFormat::APTX_HD) == static_cast<uint32_t>(::android::hardware::audio::common::V2_0::AudioFormat::APTX_HD)) {
        os += (first ? "" : " | ");
        os += "APTX_HD";
        first = false;
        flipped |= ::android::hardware::audio::common::V2_0::AudioFormat::APTX_HD;
    }
    if ((o & ::android::hardware::audio::common::V2_0::AudioFormat::AC4) == static_cast<uint32_t>(::android::hardware::audio::common::V2_0::AudioFormat::AC4)) {
        os += (first ? "" : " | ");
        os += "AC4";
        first = false;
        flipped |= ::android::hardware::audio::common::V2_0::AudioFormat::AC4;
    }
    if ((o & ::android::hardware::audio::common::V2_0::AudioFormat::LDAC) == static_cast<uint32_t>(::android::hardware::audio::common::V2_0::AudioFormat::LDAC)) {
        os += (first ? "" : " | ");
        os += "LDAC";
        first = false;
        flipped |= ::android::hardware::audio::common::V2_0::AudioFormat::LDAC;
    }
    if ((o & ::android::hardware::audio::common::V2_0::AudioFormat::MAIN_MASK) == static_cast<uint32_t>(::android::hardware::audio::common::V2_0::AudioFormat::MAIN_MASK)) {
        os += (first ? "" : " | ");
        os += "MAIN_MASK";
        first = false;
        flipped |= ::android::hardware::audio::common::V2_0::AudioFormat::MAIN_MASK;
    }
    if ((o & ::android::hardware::audio::common::V2_0::AudioFormat::SUB_MASK) == static_cast<uint32_t>(::android::hardware::audio::common::V2_0::AudioFormat::SUB_MASK)) {
        os += (first ? "" : " | ");
        os += "SUB_MASK";
        first = false;
        flipped |= ::android::hardware::audio::common::V2_0::AudioFormat::SUB_MASK;
    }
    if ((o & ::android::hardware::audio::common::V2_0::AudioFormat::PCM_SUB_16_BIT) == static_cast<uint32_t>(::android::hardware::audio::common::V2_0::AudioFormat::PCM_SUB_16_BIT)) {
        os += (first ? "" : " | ");
        os += "PCM_SUB_16_BIT";
        first = false;
        flipped |= ::android::hardware::audio::common::V2_0::AudioFormat::PCM_SUB_16_BIT;
    }
    if ((o & ::android::hardware::audio::common::V2_0::AudioFormat::PCM_SUB_8_BIT) == static_cast<uint32_t>(::android::hardware::audio::common::V2_0::AudioFormat::PCM_SUB_8_BIT)) {
        os += (first ? "" : " | ");
        os += "PCM_SUB_8_BIT";
        first = false;
        flipped |= ::android::hardware::audio::common::V2_0::AudioFormat::PCM_SUB_8_BIT;
    }
    if ((o & ::android::hardware::audio::common::V2_0::AudioFormat::PCM_SUB_32_BIT) == static_cast<uint32_t>(::android::hardware::audio::common::V2_0::AudioFormat::PCM_SUB_32_BIT)) {
        os += (first ? "" : " | ");
        os += "PCM_SUB_32_BIT";
        first = false;
        flipped |= ::android::hardware::audio::common::V2_0::AudioFormat::PCM_SUB_32_BIT;
    }
    if ((o & ::android::hardware::audio::common::V2_0::AudioFormat::PCM_SUB_8_24_BIT) == static_cast<uint32_t>(::android::hardware::audio::common::V2_0::AudioFormat::PCM_SUB_8_24_BIT)) {
        os += (first ? "" : " | ");
        os += "PCM_SUB_8_24_BIT";
        first = false;
        flipped |= ::android::hardware::audio::common::V2_0::AudioFormat::PCM_SUB_8_24_BIT;
    }
    if ((o & ::android::hardware::audio::common::V2_0::AudioFormat::PCM_SUB_FLOAT) == static_cast<uint32_t>(::android::hardware::audio::common::V2_0::AudioFormat::PCM_SUB_FLOAT)) {
        os += (first ? "" : " | ");
        os += "PCM_SUB_FLOAT";
        first = false;
        flipped |= ::android::hardware::audio::common::V2_0::AudioFormat::PCM_SUB_FLOAT;
    }
    if ((o & ::android::hardware::audio::common::V2_0::AudioFormat::PCM_SUB_24_BIT_PACKED) == static_cast<uint32_t>(::android::hardware::audio::common::V2_0::AudioFormat::PCM_SUB_24_BIT_PACKED)) {
        os += (first ? "" : " | ");
        os += "PCM_SUB_24_BIT_PACKED";
        first = false;
        flipped |= ::android::hardware::audio::common::V2_0::AudioFormat::PCM_SUB_24_BIT_PACKED;
    }
    if ((o & ::android::hardware::audio::common::V2_0::AudioFormat::MP3_SUB_NONE) == static_cast<uint32_t>(::android::hardware::audio::common::V2_0::AudioFormat::MP3_SUB_NONE)) {
        os += (first ? "" : " | ");
        os += "MP3_SUB_NONE";
        first = false;
        flipped |= ::android::hardware::audio::common::V2_0::AudioFormat::MP3_SUB_NONE;
    }
    if ((o & ::android::hardware::audio::common::V2_0::AudioFormat::AMR_SUB_NONE) == static_cast<uint32_t>(::android::hardware::audio::common::V2_0::AudioFormat::AMR_SUB_NONE)) {
        os += (first ? "" : " | ");
        os += "AMR_SUB_NONE";
        first = false;
        flipped |= ::android::hardware::audio::common::V2_0::AudioFormat::AMR_SUB_NONE;
    }
    if ((o & ::android::hardware::audio::common::V2_0::AudioFormat::AAC_SUB_MAIN) == static_cast<uint32_t>(::android::hardware::audio::common::V2_0::AudioFormat::AAC_SUB_MAIN)) {
        os += (first ? "" : " | ");
        os += "AAC_SUB_MAIN";
        first = false;
        flipped |= ::android::hardware::audio::common::V2_0::AudioFormat::AAC_SUB_MAIN;
    }
    if ((o & ::android::hardware::audio::common::V2_0::AudioFormat::AAC_SUB_LC) == static_cast<uint32_t>(::android::hardware::audio::common::V2_0::AudioFormat::AAC_SUB_LC)) {
        os += (first ? "" : " | ");
        os += "AAC_SUB_LC";
        first = false;
        flipped |= ::android::hardware::audio::common::V2_0::AudioFormat::AAC_SUB_LC;
    }
    if ((o & ::android::hardware::audio::common::V2_0::AudioFormat::AAC_SUB_SSR) == static_cast<uint32_t>(::android::hardware::audio::common::V2_0::AudioFormat::AAC_SUB_SSR)) {
        os += (first ? "" : " | ");
        os += "AAC_SUB_SSR";
        first = false;
        flipped |= ::android::hardware::audio::common::V2_0::AudioFormat::AAC_SUB_SSR;
    }
    if ((o & ::android::hardware::audio::common::V2_0::AudioFormat::AAC_SUB_LTP) == static_cast<uint32_t>(::android::hardware::audio::common::V2_0::AudioFormat::AAC_SUB_LTP)) {
        os += (first ? "" : " | ");
        os += "AAC_SUB_LTP";
        first = false;
        flipped |= ::android::hardware::audio::common::V2_0::AudioFormat::AAC_SUB_LTP;
    }
    if ((o & ::android::hardware::audio::common::V2_0::AudioFormat::AAC_SUB_HE_V1) == static_cast<uint32_t>(::android::hardware::audio::common::V2_0::AudioFormat::AAC_SUB_HE_V1)) {
        os += (first ? "" : " | ");
        os += "AAC_SUB_HE_V1";
        first = false;
        flipped |= ::android::hardware::audio::common::V2_0::AudioFormat::AAC_SUB_HE_V1;
    }
    if ((o & ::android::hardware::audio::common::V2_0::AudioFormat::AAC_SUB_SCALABLE) == static_cast<uint32_t>(::android::hardware::audio::common::V2_0::AudioFormat::AAC_SUB_SCALABLE)) {
        os += (first ? "" : " | ");
        os += "AAC_SUB_SCALABLE";
        first = false;
        flipped |= ::android::hardware::audio::common::V2_0::AudioFormat::AAC_SUB_SCALABLE;
    }
    if ((o & ::android::hardware::audio::common::V2_0::AudioFormat::AAC_SUB_ERLC) == static_cast<uint32_t>(::android::hardware::audio::common::V2_0::AudioFormat::AAC_SUB_ERLC)) {
        os += (first ? "" : " | ");
        os += "AAC_SUB_ERLC";
        first = false;
        flipped |= ::android::hardware::audio::common::V2_0::AudioFormat::AAC_SUB_ERLC;
    }
    if ((o & ::android::hardware::audio::common::V2_0::AudioFormat::AAC_SUB_LD) == static_cast<uint32_t>(::android::hardware::audio::common::V2_0::AudioFormat::AAC_SUB_LD)) {
        os += (first ? "" : " | ");
        os += "AAC_SUB_LD";
        first = false;
        flipped |= ::android::hardware::audio::common::V2_0::AudioFormat::AAC_SUB_LD;
    }
    if ((o & ::android::hardware::audio::common::V2_0::AudioFormat::AAC_SUB_HE_V2) == static_cast<uint32_t>(::android::hardware::audio::common::V2_0::AudioFormat::AAC_SUB_HE_V2)) {
        os += (first ? "" : " | ");
        os += "AAC_SUB_HE_V2";
        first = false;
        flipped |= ::android::hardware::audio::common::V2_0::AudioFormat::AAC_SUB_HE_V2;
    }
    if ((o & ::android::hardware::audio::common::V2_0::AudioFormat::AAC_SUB_ELD) == static_cast<uint32_t>(::android::hardware::audio::common::V2_0::AudioFormat::AAC_SUB_ELD)) {
        os += (first ? "" : " | ");
        os += "AAC_SUB_ELD";
        first = false;
        flipped |= ::android::hardware::audio::common::V2_0::AudioFormat::AAC_SUB_ELD;
    }
    if ((o & ::android::hardware::audio::common::V2_0::AudioFormat::VORBIS_SUB_NONE) == static_cast<uint32_t>(::android::hardware::audio::common::V2_0::AudioFormat::VORBIS_SUB_NONE)) {
        os += (first ? "" : " | ");
        os += "VORBIS_SUB_NONE";
        first = false;
        flipped |= ::android::hardware::audio::common::V2_0::AudioFormat::VORBIS_SUB_NONE;
    }
    if ((o & ::android::hardware::audio::common::V2_0::AudioFormat::PCM_16_BIT) == static_cast<uint32_t>(::android::hardware::audio::common::V2_0::AudioFormat::PCM_16_BIT)) {
        os += (first ? "" : " | ");
        os += "PCM_16_BIT";
        first = false;
        flipped |= ::android::hardware::audio::common::V2_0::AudioFormat::PCM_16_BIT;
    }
    if ((o & ::android::hardware::audio::common::V2_0::AudioFormat::PCM_8_BIT) == static_cast<uint32_t>(::android::hardware::audio::common::V2_0::AudioFormat::PCM_8_BIT)) {
        os += (first ? "" : " | ");
        os += "PCM_8_BIT";
        first = false;
        flipped |= ::android::hardware::audio::common::V2_0::AudioFormat::PCM_8_BIT;
    }
    if ((o & ::android::hardware::audio::common::V2_0::AudioFormat::PCM_32_BIT) == static_cast<uint32_t>(::android::hardware::audio::common::V2_0::AudioFormat::PCM_32_BIT)) {
        os += (first ? "" : " | ");
        os += "PCM_32_BIT";
        first = false;
        flipped |= ::android::hardware::audio::common::V2_0::AudioFormat::PCM_32_BIT;
    }
    if ((o & ::android::hardware::audio::common::V2_0::AudioFormat::PCM_8_24_BIT) == static_cast<uint32_t>(::android::hardware::audio::common::V2_0::AudioFormat::PCM_8_24_BIT)) {
        os += (first ? "" : " | ");
        os += "PCM_8_24_BIT";
        first = false;
        flipped |= ::android::hardware::audio::common::V2_0::AudioFormat::PCM_8_24_BIT;
    }
    if ((o & ::android::hardware::audio::common::V2_0::AudioFormat::PCM_FLOAT) == static_cast<uint32_t>(::android::hardware::audio::common::V2_0::AudioFormat::PCM_FLOAT)) {
        os += (first ? "" : " | ");
        os += "PCM_FLOAT";
        first = false;
        flipped |= ::android::hardware::audio::common::V2_0::AudioFormat::PCM_FLOAT;
    }
    if ((o & ::android::hardware::audio::common::V2_0::AudioFormat::PCM_24_BIT_PACKED) == static_cast<uint32_t>(::android::hardware::audio::common::V2_0::AudioFormat::PCM_24_BIT_PACKED)) {
        os += (first ? "" : " | ");
        os += "PCM_24_BIT_PACKED";
        first = false;
        flipped |= ::android::hardware::audio::common::V2_0::AudioFormat::PCM_24_BIT_PACKED;
    }
    if ((o & ::android::hardware::audio::common::V2_0::AudioFormat::AAC_MAIN) == static_cast<uint32_t>(::android::hardware::audio::common::V2_0::AudioFormat::AAC_MAIN)) {
        os += (first ? "" : " | ");
        os += "AAC_MAIN";
        first = false;
        flipped |= ::android::hardware::audio::common::V2_0::AudioFormat::AAC_MAIN;
    }
    if ((o & ::android::hardware::audio::common::V2_0::AudioFormat::AAC_LC) == static_cast<uint32_t>(::android::hardware::audio::common::V2_0::AudioFormat::AAC_LC)) {
        os += (first ? "" : " | ");
        os += "AAC_LC";
        first = false;
        flipped |= ::android::hardware::audio::common::V2_0::AudioFormat::AAC_LC;
    }
    if ((o & ::android::hardware::audio::common::V2_0::AudioFormat::AAC_SSR) == static_cast<uint32_t>(::android::hardware::audio::common::V2_0::AudioFormat::AAC_SSR)) {
        os += (first ? "" : " | ");
        os += "AAC_SSR";
        first = false;
        flipped |= ::android::hardware::audio::common::V2_0::AudioFormat::AAC_SSR;
    }
    if ((o & ::android::hardware::audio::common::V2_0::AudioFormat::AAC_LTP) == static_cast<uint32_t>(::android::hardware::audio::common::V2_0::AudioFormat::AAC_LTP)) {
        os += (first ? "" : " | ");
        os += "AAC_LTP";
        first = false;
        flipped |= ::android::hardware::audio::common::V2_0::AudioFormat::AAC_LTP;
    }
    if ((o & ::android::hardware::audio::common::V2_0::AudioFormat::AAC_HE_V1) == static_cast<uint32_t>(::android::hardware::audio::common::V2_0::AudioFormat::AAC_HE_V1)) {
        os += (first ? "" : " | ");
        os += "AAC_HE_V1";
        first = false;
        flipped |= ::android::hardware::audio::common::V2_0::AudioFormat::AAC_HE_V1;
    }
    if ((o & ::android::hardware::audio::common::V2_0::AudioFormat::AAC_SCALABLE) == static_cast<uint32_t>(::android::hardware::audio::common::V2_0::AudioFormat::AAC_SCALABLE)) {
        os += (first ? "" : " | ");
        os += "AAC_SCALABLE";
        first = false;
        flipped |= ::android::hardware::audio::common::V2_0::AudioFormat::AAC_SCALABLE;
    }
    if ((o & ::android::hardware::audio::common::V2_0::AudioFormat::AAC_ERLC) == static_cast<uint32_t>(::android::hardware::audio::common::V2_0::AudioFormat::AAC_ERLC)) {
        os += (first ? "" : " | ");
        os += "AAC_ERLC";
        first = false;
        flipped |= ::android::hardware::audio::common::V2_0::AudioFormat::AAC_ERLC;
    }
    if ((o & ::android::hardware::audio::common::V2_0::AudioFormat::AAC_LD) == static_cast<uint32_t>(::android::hardware::audio::common::V2_0::AudioFormat::AAC_LD)) {
        os += (first ? "" : " | ");
        os += "AAC_LD";
        first = false;
        flipped |= ::android::hardware::audio::common::V2_0::AudioFormat::AAC_LD;
    }
    if ((o & ::android::hardware::audio::common::V2_0::AudioFormat::AAC_HE_V2) == static_cast<uint32_t>(::android::hardware::audio::common::V2_0::AudioFormat::AAC_HE_V2)) {
        os += (first ? "" : " | ");
        os += "AAC_HE_V2";
        first = false;
        flipped |= ::android::hardware::audio::common::V2_0::AudioFormat::AAC_HE_V2;
    }
    if ((o & ::android::hardware::audio::common::V2_0::AudioFormat::AAC_ELD) == static_cast<uint32_t>(::android::hardware::audio::common::V2_0::AudioFormat::AAC_ELD)) {
        os += (first ? "" : " | ");
        os += "AAC_ELD";
        first = false;
        flipped |= ::android::hardware::audio::common::V2_0::AudioFormat::AAC_ELD;
    }
    if ((o & ::android::hardware::audio::common::V2_0::AudioFormat::AAC_ADTS_MAIN) == static_cast<uint32_t>(::android::hardware::audio::common::V2_0::AudioFormat::AAC_ADTS_MAIN)) {
        os += (first ? "" : " | ");
        os += "AAC_ADTS_MAIN";
        first = false;
        flipped |= ::android::hardware::audio::common::V2_0::AudioFormat::AAC_ADTS_MAIN;
    }
    if ((o & ::android::hardware::audio::common::V2_0::AudioFormat::AAC_ADTS_LC) == static_cast<uint32_t>(::android::hardware::audio::common::V2_0::AudioFormat::AAC_ADTS_LC)) {
        os += (first ? "" : " | ");
        os += "AAC_ADTS_LC";
        first = false;
        flipped |= ::android::hardware::audio::common::V2_0::AudioFormat::AAC_ADTS_LC;
    }
    if ((o & ::android::hardware::audio::common::V2_0::AudioFormat::AAC_ADTS_SSR) == static_cast<uint32_t>(::android::hardware::audio::common::V2_0::AudioFormat::AAC_ADTS_SSR)) {
        os += (first ? "" : " | ");
        os += "AAC_ADTS_SSR";
        first = false;
        flipped |= ::android::hardware::audio::common::V2_0::AudioFormat::AAC_ADTS_SSR;
    }
    if ((o & ::android::hardware::audio::common::V2_0::AudioFormat::AAC_ADTS_LTP) == static_cast<uint32_t>(::android::hardware::audio::common::V2_0::AudioFormat::AAC_ADTS_LTP)) {
        os += (first ? "" : " | ");
        os += "AAC_ADTS_LTP";
        first = false;
        flipped |= ::android::hardware::audio::common::V2_0::AudioFormat::AAC_ADTS_LTP;
    }
    if ((o & ::android::hardware::audio::common::V2_0::AudioFormat::AAC_ADTS_HE_V1) == static_cast<uint32_t>(::android::hardware::audio::common::V2_0::AudioFormat::AAC_ADTS_HE_V1)) {
        os += (first ? "" : " | ");
        os += "AAC_ADTS_HE_V1";
        first = false;
        flipped |= ::android::hardware::audio::common::V2_0::AudioFormat::AAC_ADTS_HE_V1;
    }
    if ((o & ::android::hardware::audio::common::V2_0::AudioFormat::AAC_ADTS_SCALABLE) == static_cast<uint32_t>(::android::hardware::audio::common::V2_0::AudioFormat::AAC_ADTS_SCALABLE)) {
        os += (first ? "" : " | ");
        os += "AAC_ADTS_SCALABLE";
        first = false;
        flipped |= ::android::hardware::audio::common::V2_0::AudioFormat::AAC_ADTS_SCALABLE;
    }
    if ((o & ::android::hardware::audio::common::V2_0::AudioFormat::AAC_ADTS_ERLC) == static_cast<uint32_t>(::android::hardware::audio::common::V2_0::AudioFormat::AAC_ADTS_ERLC)) {
        os += (first ? "" : " | ");
        os += "AAC_ADTS_ERLC";
        first = false;
        flipped |= ::android::hardware::audio::common::V2_0::AudioFormat::AAC_ADTS_ERLC;
    }
    if ((o & ::android::hardware::audio::common::V2_0::AudioFormat::AAC_ADTS_LD) == static_cast<uint32_t>(::android::hardware::audio::common::V2_0::AudioFormat::AAC_ADTS_LD)) {
        os += (first ? "" : " | ");
        os += "AAC_ADTS_LD";
        first = false;
        flipped |= ::android::hardware::audio::common::V2_0::AudioFormat::AAC_ADTS_LD;
    }
    if ((o & ::android::hardware::audio::common::V2_0::AudioFormat::AAC_ADTS_HE_V2) == static_cast<uint32_t>(::android::hardware::audio::common::V2_0::AudioFormat::AAC_ADTS_HE_V2)) {
        os += (first ? "" : " | ");
        os += "AAC_ADTS_HE_V2";
        first = false;
        flipped |= ::android::hardware::audio::common::V2_0::AudioFormat::AAC_ADTS_HE_V2;
    }
    if ((o & ::android::hardware::audio::common::V2_0::AudioFormat::AAC_ADTS_ELD) == static_cast<uint32_t>(::android::hardware::audio::common::V2_0::AudioFormat::AAC_ADTS_ELD)) {
        os += (first ? "" : " | ");
        os += "AAC_ADTS_ELD";
        first = false;
        flipped |= ::android::hardware::audio::common::V2_0::AudioFormat::AAC_ADTS_ELD;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::audio::common::V2_0::AudioFormat o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::audio::common::V2_0::AudioFormat::INVALID) {
        return "INVALID";
    }
    if (o == ::android::hardware::audio::common::V2_0::AudioFormat::DEFAULT) {
        return "DEFAULT";
    }
    if (o == ::android::hardware::audio::common::V2_0::AudioFormat::PCM) {
        return "PCM";
    }
    if (o == ::android::hardware::audio::common::V2_0::AudioFormat::MP3) {
        return "MP3";
    }
    if (o == ::android::hardware::audio::common::V2_0::AudioFormat::AMR_NB) {
        return "AMR_NB";
    }
    if (o == ::android::hardware::audio::common::V2_0::AudioFormat::AMR_WB) {
        return "AMR_WB";
    }
    if (o == ::android::hardware::audio::common::V2_0::AudioFormat::AAC) {
        return "AAC";
    }
    if (o == ::android::hardware::audio::common::V2_0::AudioFormat::HE_AAC_V1) {
        return "HE_AAC_V1";
    }
    if (o == ::android::hardware::audio::common::V2_0::AudioFormat::HE_AAC_V2) {
        return "HE_AAC_V2";
    }
    if (o == ::android::hardware::audio::common::V2_0::AudioFormat::VORBIS) {
        return "VORBIS";
    }
    if (o == ::android::hardware::audio::common::V2_0::AudioFormat::OPUS) {
        return "OPUS";
    }
    if (o == ::android::hardware::audio::common::V2_0::AudioFormat::AC3) {
        return "AC3";
    }
    if (o == ::android::hardware::audio::common::V2_0::AudioFormat::E_AC3) {
        return "E_AC3";
    }
    if (o == ::android::hardware::audio::common::V2_0::AudioFormat::DTS) {
        return "DTS";
    }
    if (o == ::android::hardware::audio::common::V2_0::AudioFormat::DTS_HD) {
        return "DTS_HD";
    }
    if (o == ::android::hardware::audio::common::V2_0::AudioFormat::IEC61937) {
        return "IEC61937";
    }
    if (o == ::android::hardware::audio::common::V2_0::AudioFormat::DOLBY_TRUEHD) {
        return "DOLBY_TRUEHD";
    }
    if (o == ::android::hardware::audio::common::V2_0::AudioFormat::EVRC) {
        return "EVRC";
    }
    if (o == ::android::hardware::audio::common::V2_0::AudioFormat::EVRCB) {
        return "EVRCB";
    }
    if (o == ::android::hardware::audio::common::V2_0::AudioFormat::EVRCWB) {
        return "EVRCWB";
    }
    if (o == ::android::hardware::audio::common::V2_0::AudioFormat::EVRCNW) {
        return "EVRCNW";
    }
    if (o == ::android::hardware::audio::common::V2_0::AudioFormat::AAC_ADIF) {
        return "AAC_ADIF";
    }
    if (o == ::android::hardware::audio::common::V2_0::AudioFormat::WMA) {
        return "WMA";
    }
    if (o == ::android::hardware::audio::common::V2_0::AudioFormat::WMA_PRO) {
        return "WMA_PRO";
    }
    if (o == ::android::hardware::audio::common::V2_0::AudioFormat::AMR_WB_PLUS) {
        return "AMR_WB_PLUS";
    }
    if (o == ::android::hardware::audio::common::V2_0::AudioFormat::MP2) {
        return "MP2";
    }
    if (o == ::android::hardware::audio::common::V2_0::AudioFormat::QCELP) {
        return "QCELP";
    }
    if (o == ::android::hardware::audio::common::V2_0::AudioFormat::DSD) {
        return "DSD";
    }
    if (o == ::android::hardware::audio::common::V2_0::AudioFormat::FLAC) {
        return "FLAC";
    }
    if (o == ::android::hardware::audio::common::V2_0::AudioFormat::ALAC) {
        return "ALAC";
    }
    if (o == ::android::hardware::audio::common::V2_0::AudioFormat::APE) {
        return "APE";
    }
    if (o == ::android::hardware::audio::common::V2_0::AudioFormat::AAC_ADTS) {
        return "AAC_ADTS";
    }
    if (o == ::android::hardware::audio::common::V2_0::AudioFormat::SBC) {
        return "SBC";
    }
    if (o == ::android::hardware::audio::common::V2_0::AudioFormat::APTX) {
        return "APTX";
    }
    if (o == ::android::hardware::audio::common::V2_0::AudioFormat::APTX_HD) {
        return "APTX_HD";
    }
    if (o == ::android::hardware::audio::common::V2_0::AudioFormat::AC4) {
        return "AC4";
    }
    if (o == ::android::hardware::audio::common::V2_0::AudioFormat::LDAC) {
        return "LDAC";
    }
    if (o == ::android::hardware::audio::common::V2_0::AudioFormat::MAIN_MASK) {
        return "MAIN_MASK";
    }
    if (o == ::android::hardware::audio::common::V2_0::AudioFormat::SUB_MASK) {
        return "SUB_MASK";
    }
    if (o == ::android::hardware::audio::common::V2_0::AudioFormat::PCM_SUB_16_BIT) {
        return "PCM_SUB_16_BIT";
    }
    if (o == ::android::hardware::audio::common::V2_0::AudioFormat::PCM_SUB_8_BIT) {
        return "PCM_SUB_8_BIT";
    }
    if (o == ::android::hardware::audio::common::V2_0::AudioFormat::PCM_SUB_32_BIT) {
        return "PCM_SUB_32_BIT";
    }
    if (o == ::android::hardware::audio::common::V2_0::AudioFormat::PCM_SUB_8_24_BIT) {
        return "PCM_SUB_8_24_BIT";
    }
    if (o == ::android::hardware::audio::common::V2_0::AudioFormat::PCM_SUB_FLOAT) {
        return "PCM_SUB_FLOAT";
    }
    if (o == ::android::hardware::audio::common::V2_0::AudioFormat::PCM_SUB_24_BIT_PACKED) {
        return "PCM_SUB_24_BIT_PACKED";
    }
    if (o == ::android::hardware::audio::common::V2_0::AudioFormat::MP3_SUB_NONE) {
        return "MP3_SUB_NONE";
    }
    if (o == ::android::hardware::audio::common::V2_0::AudioFormat::AMR_SUB_NONE) {
        return "AMR_SUB_NONE";
    }
    if (o == ::android::hardware::audio::common::V2_0::AudioFormat::AAC_SUB_MAIN) {
        return "AAC_SUB_MAIN";
    }
    if (o == ::android::hardware::audio::common::V2_0::AudioFormat::AAC_SUB_LC) {
        return "AAC_SUB_LC";
    }
    if (o == ::android::hardware::audio::common::V2_0::AudioFormat::AAC_SUB_SSR) {
        return "AAC_SUB_SSR";
    }
    if (o == ::android::hardware::audio::common::V2_0::AudioFormat::AAC_SUB_LTP) {
        return "AAC_SUB_LTP";
    }
    if (o == ::android::hardware::audio::common::V2_0::AudioFormat::AAC_SUB_HE_V1) {
        return "AAC_SUB_HE_V1";
    }
    if (o == ::android::hardware::audio::common::V2_0::AudioFormat::AAC_SUB_SCALABLE) {
        return "AAC_SUB_SCALABLE";
    }
    if (o == ::android::hardware::audio::common::V2_0::AudioFormat::AAC_SUB_ERLC) {
        return "AAC_SUB_ERLC";
    }
    if (o == ::android::hardware::audio::common::V2_0::AudioFormat::AAC_SUB_LD) {
        return "AAC_SUB_LD";
    }
    if (o == ::android::hardware::audio::common::V2_0::AudioFormat::AAC_SUB_HE_V2) {
        return "AAC_SUB_HE_V2";
    }
    if (o == ::android::hardware::audio::common::V2_0::AudioFormat::AAC_SUB_ELD) {
        return "AAC_SUB_ELD";
    }
    if (o == ::android::hardware::audio::common::V2_0::AudioFormat::VORBIS_SUB_NONE) {
        return "VORBIS_SUB_NONE";
    }
    if (o == ::android::hardware::audio::common::V2_0::AudioFormat::PCM_16_BIT) {
        return "PCM_16_BIT";
    }
    if (o == ::android::hardware::audio::common::V2_0::AudioFormat::PCM_8_BIT) {
        return "PCM_8_BIT";
    }
    if (o == ::android::hardware::audio::common::V2_0::AudioFormat::PCM_32_BIT) {
        return "PCM_32_BIT";
    }
    if (o == ::android::hardware::audio::common::V2_0::AudioFormat::PCM_8_24_BIT) {
        return "PCM_8_24_BIT";
    }
    if (o == ::android::hardware::audio::common::V2_0::AudioFormat::PCM_FLOAT) {
        return "PCM_FLOAT";
    }
    if (o == ::android::hardware::audio::common::V2_0::AudioFormat::PCM_24_BIT_PACKED) {
        return "PCM_24_BIT_PACKED";
    }
    if (o == ::android::hardware::audio::common::V2_0::AudioFormat::AAC_MAIN) {
        return "AAC_MAIN";
    }
    if (o == ::android::hardware::audio::common::V2_0::AudioFormat::AAC_LC) {
        return "AAC_LC";
    }
    if (o == ::android::hardware::audio::common::V2_0::AudioFormat::AAC_SSR) {
        return "AAC_SSR";
    }
    if (o == ::android::hardware::audio::common::V2_0::AudioFormat::AAC_LTP) {
        return "AAC_LTP";
    }
    if (o == ::android::hardware::audio::common::V2_0::AudioFormat::AAC_HE_V1) {
        return "AAC_HE_V1";
    }
    if (o == ::android::hardware::audio::common::V2_0::AudioFormat::AAC_SCALABLE) {
        return "AAC_SCALABLE";
    }
    if (o == ::android::hardware::audio::common::V2_0::AudioFormat::AAC_ERLC) {
        return "AAC_ERLC";
    }
    if (o == ::android::hardware::audio::common::V2_0::AudioFormat::AAC_LD) {
        return "AAC_LD";
    }
    if (o == ::android::hardware::audio::common::V2_0::AudioFormat::AAC_HE_V2) {
        return "AAC_HE_V2";
    }
    if (o == ::android::hardware::audio::common::V2_0::AudioFormat::AAC_ELD) {
        return "AAC_ELD";
    }
    if (o == ::android::hardware::audio::common::V2_0::AudioFormat::AAC_ADTS_MAIN) {
        return "AAC_ADTS_MAIN";
    }
    if (o == ::android::hardware::audio::common::V2_0::AudioFormat::AAC_ADTS_LC) {
        return "AAC_ADTS_LC";
    }
    if (o == ::android::hardware::audio::common::V2_0::AudioFormat::AAC_ADTS_SSR) {
        return "AAC_ADTS_SSR";
    }
    if (o == ::android::hardware::audio::common::V2_0::AudioFormat::AAC_ADTS_LTP) {
        return "AAC_ADTS_LTP";
    }
    if (o == ::android::hardware::audio::common::V2_0::AudioFormat::AAC_ADTS_HE_V1) {
        return "AAC_ADTS_HE_V1";
    }
    if (o == ::android::hardware::audio::common::V2_0::AudioFormat::AAC_ADTS_SCALABLE) {
        return "AAC_ADTS_SCALABLE";
    }
    if (o == ::android::hardware::audio::common::V2_0::AudioFormat::AAC_ADTS_ERLC) {
        return "AAC_ADTS_ERLC";
    }
    if (o == ::android::hardware::audio::common::V2_0::AudioFormat::AAC_ADTS_LD) {
        return "AAC_ADTS_LD";
    }
    if (o == ::android::hardware::audio::common::V2_0::AudioFormat::AAC_ADTS_HE_V2) {
        return "AAC_ADTS_HE_V2";
    }
    if (o == ::android::hardware::audio::common::V2_0::AudioFormat::AAC_ADTS_ELD) {
        return "AAC_ADTS_ELD";
    }
    std::string os;
    os += toHexString(static_cast<uint32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::audio::common::V2_0::AudioFormat o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::audio::common::V2_0::FixedChannelCount>(int32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::audio::common::V2_0::FixedChannelCount> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::audio::common::V2_0::FixedChannelCount::FCC_2) == static_cast<int32_t>(::android::hardware::audio::common::V2_0::FixedChannelCount::FCC_2)) {
        os += (first ? "" : " | ");
        os += "FCC_2";
        first = false;
        flipped |= ::android::hardware::audio::common::V2_0::FixedChannelCount::FCC_2;
    }
    if ((o & ::android::hardware::audio::common::V2_0::FixedChannelCount::FCC_8) == static_cast<int32_t>(::android::hardware::audio::common::V2_0::FixedChannelCount::FCC_8)) {
        os += (first ? "" : " | ");
        os += "FCC_8";
        first = false;
        flipped |= ::android::hardware::audio::common::V2_0::FixedChannelCount::FCC_8;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::audio::common::V2_0::FixedChannelCount o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::audio::common::V2_0::FixedChannelCount::FCC_2) {
        return "FCC_2";
    }
    if (o == ::android::hardware::audio::common::V2_0::FixedChannelCount::FCC_8) {
        return "FCC_8";
    }
    std::string os;
    os += toHexString(static_cast<int32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::audio::common::V2_0::FixedChannelCount o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::audio::common::V2_0::AudioChannelMask>(uint32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::audio::common::V2_0::AudioChannelMask> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::audio::common::V2_0::AudioChannelMask::REPRESENTATION_POSITION) == static_cast<uint32_t>(::android::hardware::audio::common::V2_0::AudioChannelMask::REPRESENTATION_POSITION)) {
        os += (first ? "" : " | ");
        os += "REPRESENTATION_POSITION";
        first = false;
        flipped |= ::android::hardware::audio::common::V2_0::AudioChannelMask::REPRESENTATION_POSITION;
    }
    if ((o & ::android::hardware::audio::common::V2_0::AudioChannelMask::REPRESENTATION_INDEX) == static_cast<uint32_t>(::android::hardware::audio::common::V2_0::AudioChannelMask::REPRESENTATION_INDEX)) {
        os += (first ? "" : " | ");
        os += "REPRESENTATION_INDEX";
        first = false;
        flipped |= ::android::hardware::audio::common::V2_0::AudioChannelMask::REPRESENTATION_INDEX;
    }
    if ((o & ::android::hardware::audio::common::V2_0::AudioChannelMask::NONE) == static_cast<uint32_t>(::android::hardware::audio::common::V2_0::AudioChannelMask::NONE)) {
        os += (first ? "" : " | ");
        os += "NONE";
        first = false;
        flipped |= ::android::hardware::audio::common::V2_0::AudioChannelMask::NONE;
    }
    if ((o & ::android::hardware::audio::common::V2_0::AudioChannelMask::INVALID) == static_cast<uint32_t>(::android::hardware::audio::common::V2_0::AudioChannelMask::INVALID)) {
        os += (first ? "" : " | ");
        os += "INVALID";
        first = false;
        flipped |= ::android::hardware::audio::common::V2_0::AudioChannelMask::INVALID;
    }
    if ((o & ::android::hardware::audio::common::V2_0::AudioChannelMask::OUT_FRONT_LEFT) == static_cast<uint32_t>(::android::hardware::audio::common::V2_0::AudioChannelMask::OUT_FRONT_LEFT)) {
        os += (first ? "" : " | ");
        os += "OUT_FRONT_LEFT";
        first = false;
        flipped |= ::android::hardware::audio::common::V2_0::AudioChannelMask::OUT_FRONT_LEFT;
    }
    if ((o & ::android::hardware::audio::common::V2_0::AudioChannelMask::OUT_FRONT_RIGHT) == static_cast<uint32_t>(::android::hardware::audio::common::V2_0::AudioChannelMask::OUT_FRONT_RIGHT)) {
        os += (first ? "" : " | ");
        os += "OUT_FRONT_RIGHT";
        first = false;
        flipped |= ::android::hardware::audio::common::V2_0::AudioChannelMask::OUT_FRONT_RIGHT;
    }
    if ((o & ::android::hardware::audio::common::V2_0::AudioChannelMask::OUT_FRONT_CENTER) == static_cast<uint32_t>(::android::hardware::audio::common::V2_0::AudioChannelMask::OUT_FRONT_CENTER)) {
        os += (first ? "" : " | ");
        os += "OUT_FRONT_CENTER";
        first = false;
        flipped |= ::android::hardware::audio::common::V2_0::AudioChannelMask::OUT_FRONT_CENTER;
    }
    if ((o & ::android::hardware::audio::common::V2_0::AudioChannelMask::OUT_LOW_FREQUENCY) == static_cast<uint32_t>(::android::hardware::audio::common::V2_0::AudioChannelMask::OUT_LOW_FREQUENCY)) {
        os += (first ? "" : " | ");
        os += "OUT_LOW_FREQUENCY";
        first = false;
        flipped |= ::android::hardware::audio::common::V2_0::AudioChannelMask::OUT_LOW_FREQUENCY;
    }
    if ((o & ::android::hardware::audio::common::V2_0::AudioChannelMask::OUT_BACK_LEFT) == static_cast<uint32_t>(::android::hardware::audio::common::V2_0::AudioChannelMask::OUT_BACK_LEFT)) {
        os += (first ? "" : " | ");
        os += "OUT_BACK_LEFT";
        first = false;
        flipped |= ::android::hardware::audio::common::V2_0::AudioChannelMask::OUT_BACK_LEFT;
    }
    if ((o & ::android::hardware::audio::common::V2_0::AudioChannelMask::OUT_BACK_RIGHT) == static_cast<uint32_t>(::android::hardware::audio::common::V2_0::AudioChannelMask::OUT_BACK_RIGHT)) {
        os += (first ? "" : " | ");
        os += "OUT_BACK_RIGHT";
        first = false;
        flipped |= ::android::hardware::audio::common::V2_0::AudioChannelMask::OUT_BACK_RIGHT;
    }
    if ((o & ::android::hardware::audio::common::V2_0::AudioChannelMask::OUT_FRONT_LEFT_OF_CENTER) == static_cast<uint32_t>(::android::hardware::audio::common::V2_0::AudioChannelMask::OUT_FRONT_LEFT_OF_CENTER)) {
        os += (first ? "" : " | ");
        os += "OUT_FRONT_LEFT_OF_CENTER";
        first = false;
        flipped |= ::android::hardware::audio::common::V2_0::AudioChannelMask::OUT_FRONT_LEFT_OF_CENTER;
    }
    if ((o & ::android::hardware::audio::common::V2_0::AudioChannelMask::OUT_FRONT_RIGHT_OF_CENTER) == static_cast<uint32_t>(::android::hardware::audio::common::V2_0::AudioChannelMask::OUT_FRONT_RIGHT_OF_CENTER)) {
        os += (first ? "" : " | ");
        os += "OUT_FRONT_RIGHT_OF_CENTER";
        first = false;
        flipped |= ::android::hardware::audio::common::V2_0::AudioChannelMask::OUT_FRONT_RIGHT_OF_CENTER;
    }
    if ((o & ::android::hardware::audio::common::V2_0::AudioChannelMask::OUT_BACK_CENTER) == static_cast<uint32_t>(::android::hardware::audio::common::V2_0::AudioChannelMask::OUT_BACK_CENTER)) {
        os += (first ? "" : " | ");
        os += "OUT_BACK_CENTER";
        first = false;
        flipped |= ::android::hardware::audio::common::V2_0::AudioChannelMask::OUT_BACK_CENTER;
    }
    if ((o & ::android::hardware::audio::common::V2_0::AudioChannelMask::OUT_SIDE_LEFT) == static_cast<uint32_t>(::android::hardware::audio::common::V2_0::AudioChannelMask::OUT_SIDE_LEFT)) {
        os += (first ? "" : " | ");
        os += "OUT_SIDE_LEFT";
        first = false;
        flipped |= ::android::hardware::audio::common::V2_0::AudioChannelMask::OUT_SIDE_LEFT;
    }
    if ((o & ::android::hardware::audio::common::V2_0::AudioChannelMask::OUT_SIDE_RIGHT) == static_cast<uint32_t>(::android::hardware::audio::common::V2_0::AudioChannelMask::OUT_SIDE_RIGHT)) {
        os += (first ? "" : " | ");
        os += "OUT_SIDE_RIGHT";
        first = false;
        flipped |= ::android::hardware::audio::common::V2_0::AudioChannelMask::OUT_SIDE_RIGHT;
    }
    if ((o & ::android::hardware::audio::common::V2_0::AudioChannelMask::OUT_TOP_CENTER) == static_cast<uint32_t>(::android::hardware::audio::common::V2_0::AudioChannelMask::OUT_TOP_CENTER)) {
        os += (first ? "" : " | ");
        os += "OUT_TOP_CENTER";
        first = false;
        flipped |= ::android::hardware::audio::common::V2_0::AudioChannelMask::OUT_TOP_CENTER;
    }
    if ((o & ::android::hardware::audio::common::V2_0::AudioChannelMask::OUT_TOP_FRONT_LEFT) == static_cast<uint32_t>(::android::hardware::audio::common::V2_0::AudioChannelMask::OUT_TOP_FRONT_LEFT)) {
        os += (first ? "" : " | ");
        os += "OUT_TOP_FRONT_LEFT";
        first = false;
        flipped |= ::android::hardware::audio::common::V2_0::AudioChannelMask::OUT_TOP_FRONT_LEFT;
    }
    if ((o & ::android::hardware::audio::common::V2_0::AudioChannelMask::OUT_TOP_FRONT_CENTER) == static_cast<uint32_t>(::android::hardware::audio::common::V2_0::AudioChannelMask::OUT_TOP_FRONT_CENTER)) {
        os += (first ? "" : " | ");
        os += "OUT_TOP_FRONT_CENTER";
        first = false;
        flipped |= ::android::hardware::audio::common::V2_0::AudioChannelMask::OUT_TOP_FRONT_CENTER;
    }
    if ((o & ::android::hardware::audio::common::V2_0::AudioChannelMask::OUT_TOP_FRONT_RIGHT) == static_cast<uint32_t>(::android::hardware::audio::common::V2_0::AudioChannelMask::OUT_TOP_FRONT_RIGHT)) {
        os += (first ? "" : " | ");
        os += "OUT_TOP_FRONT_RIGHT";
        first = false;
        flipped |= ::android::hardware::audio::common::V2_0::AudioChannelMask::OUT_TOP_FRONT_RIGHT;
    }
    if ((o & ::android::hardware::audio::common::V2_0::AudioChannelMask::OUT_TOP_BACK_LEFT) == static_cast<uint32_t>(::android::hardware::audio::common::V2_0::AudioChannelMask::OUT_TOP_BACK_LEFT)) {
        os += (first ? "" : " | ");
        os += "OUT_TOP_BACK_LEFT";
        first = false;
        flipped |= ::android::hardware::audio::common::V2_0::AudioChannelMask::OUT_TOP_BACK_LEFT;
    }
    if ((o & ::android::hardware::audio::common::V2_0::AudioChannelMask::OUT_TOP_BACK_CENTER) == static_cast<uint32_t>(::android::hardware::audio::common::V2_0::AudioChannelMask::OUT_TOP_BACK_CENTER)) {
        os += (first ? "" : " | ");
        os += "OUT_TOP_BACK_CENTER";
        first = false;
        flipped |= ::android::hardware::audio::common::V2_0::AudioChannelMask::OUT_TOP_BACK_CENTER;
    }
    if ((o & ::android::hardware::audio::common::V2_0::AudioChannelMask::OUT_TOP_BACK_RIGHT) == static_cast<uint32_t>(::android::hardware::audio::common::V2_0::AudioChannelMask::OUT_TOP_BACK_RIGHT)) {
        os += (first ? "" : " | ");
        os += "OUT_TOP_BACK_RIGHT";
        first = false;
        flipped |= ::android::hardware::audio::common::V2_0::AudioChannelMask::OUT_TOP_BACK_RIGHT;
    }
    if ((o & ::android::hardware::audio::common::V2_0::AudioChannelMask::OUT_MONO) == static_cast<uint32_t>(::android::hardware::audio::common::V2_0::AudioChannelMask::OUT_MONO)) {
        os += (first ? "" : " | ");
        os += "OUT_MONO";
        first = false;
        flipped |= ::android::hardware::audio::common::V2_0::AudioChannelMask::OUT_MONO;
    }
    if ((o & ::android::hardware::audio::common::V2_0::AudioChannelMask::OUT_STEREO) == static_cast<uint32_t>(::android::hardware::audio::common::V2_0::AudioChannelMask::OUT_STEREO)) {
        os += (first ? "" : " | ");
        os += "OUT_STEREO";
        first = false;
        flipped |= ::android::hardware::audio::common::V2_0::AudioChannelMask::OUT_STEREO;
    }
    if ((o & ::android::hardware::audio::common::V2_0::AudioChannelMask::OUT_2POINT1) == static_cast<uint32_t>(::android::hardware::audio::common::V2_0::AudioChannelMask::OUT_2POINT1)) {
        os += (first ? "" : " | ");
        os += "OUT_2POINT1";
        first = false;
        flipped |= ::android::hardware::audio::common::V2_0::AudioChannelMask::OUT_2POINT1;
    }
    if ((o & ::android::hardware::audio::common::V2_0::AudioChannelMask::OUT_QUAD) == static_cast<uint32_t>(::android::hardware::audio::common::V2_0::AudioChannelMask::OUT_QUAD)) {
        os += (first ? "" : " | ");
        os += "OUT_QUAD";
        first = false;
        flipped |= ::android::hardware::audio::common::V2_0::AudioChannelMask::OUT_QUAD;
    }
    if ((o & ::android::hardware::audio::common::V2_0::AudioChannelMask::OUT_QUAD_BACK) == static_cast<uint32_t>(::android::hardware::audio::common::V2_0::AudioChannelMask::OUT_QUAD_BACK)) {
        os += (first ? "" : " | ");
        os += "OUT_QUAD_BACK";
        first = false;
        flipped |= ::android::hardware::audio::common::V2_0::AudioChannelMask::OUT_QUAD_BACK;
    }
    if ((o & ::android::hardware::audio::common::V2_0::AudioChannelMask::OUT_QUAD_SIDE) == static_cast<uint32_t>(::android::hardware::audio::common::V2_0::AudioChannelMask::OUT_QUAD_SIDE)) {
        os += (first ? "" : " | ");
        os += "OUT_QUAD_SIDE";
        first = false;
        flipped |= ::android::hardware::audio::common::V2_0::AudioChannelMask::OUT_QUAD_SIDE;
    }
    if ((o & ::android::hardware::audio::common::V2_0::AudioChannelMask::OUT_SURROUND) == static_cast<uint32_t>(::android::hardware::audio::common::V2_0::AudioChannelMask::OUT_SURROUND)) {
        os += (first ? "" : " | ");
        os += "OUT_SURROUND";
        first = false;
        flipped |= ::android::hardware::audio::common::V2_0::AudioChannelMask::OUT_SURROUND;
    }
    if ((o & ::android::hardware::audio::common::V2_0::AudioChannelMask::OUT_PENTA) == static_cast<uint32_t>(::android::hardware::audio::common::V2_0::AudioChannelMask::OUT_PENTA)) {
        os += (first ? "" : " | ");
        os += "OUT_PENTA";
        first = false;
        flipped |= ::android::hardware::audio::common::V2_0::AudioChannelMask::OUT_PENTA;
    }
    if ((o & ::android::hardware::audio::common::V2_0::AudioChannelMask::OUT_5POINT1) == static_cast<uint32_t>(::android::hardware::audio::common::V2_0::AudioChannelMask::OUT_5POINT1)) {
        os += (first ? "" : " | ");
        os += "OUT_5POINT1";
        first = false;
        flipped |= ::android::hardware::audio::common::V2_0::AudioChannelMask::OUT_5POINT1;
    }
    if ((o & ::android::hardware::audio::common::V2_0::AudioChannelMask::OUT_5POINT1_BACK) == static_cast<uint32_t>(::android::hardware::audio::common::V2_0::AudioChannelMask::OUT_5POINT1_BACK)) {
        os += (first ? "" : " | ");
        os += "OUT_5POINT1_BACK";
        first = false;
        flipped |= ::android::hardware::audio::common::V2_0::AudioChannelMask::OUT_5POINT1_BACK;
    }
    if ((o & ::android::hardware::audio::common::V2_0::AudioChannelMask::OUT_5POINT1_SIDE) == static_cast<uint32_t>(::android::hardware::audio::common::V2_0::AudioChannelMask::OUT_5POINT1_SIDE)) {
        os += (first ? "" : " | ");
        os += "OUT_5POINT1_SIDE";
        first = false;
        flipped |= ::android::hardware::audio::common::V2_0::AudioChannelMask::OUT_5POINT1_SIDE;
    }
    if ((o & ::android::hardware::audio::common::V2_0::AudioChannelMask::OUT_6POINT1) == static_cast<uint32_t>(::android::hardware::audio::common::V2_0::AudioChannelMask::OUT_6POINT1)) {
        os += (first ? "" : " | ");
        os += "OUT_6POINT1";
        first = false;
        flipped |= ::android::hardware::audio::common::V2_0::AudioChannelMask::OUT_6POINT1;
    }
    if ((o & ::android::hardware::audio::common::V2_0::AudioChannelMask::OUT_7POINT1) == static_cast<uint32_t>(::android::hardware::audio::common::V2_0::AudioChannelMask::OUT_7POINT1)) {
        os += (first ? "" : " | ");
        os += "OUT_7POINT1";
        first = false;
        flipped |= ::android::hardware::audio::common::V2_0::AudioChannelMask::OUT_7POINT1;
    }
    if ((o & ::android::hardware::audio::common::V2_0::AudioChannelMask::OUT_ALL) == static_cast<uint32_t>(::android::hardware::audio::common::V2_0::AudioChannelMask::OUT_ALL)) {
        os += (first ? "" : " | ");
        os += "OUT_ALL";
        first = false;
        flipped |= ::android::hardware::audio::common::V2_0::AudioChannelMask::OUT_ALL;
    }
    if ((o & ::android::hardware::audio::common::V2_0::AudioChannelMask::IN_LEFT) == static_cast<uint32_t>(::android::hardware::audio::common::V2_0::AudioChannelMask::IN_LEFT)) {
        os += (first ? "" : " | ");
        os += "IN_LEFT";
        first = false;
        flipped |= ::android::hardware::audio::common::V2_0::AudioChannelMask::IN_LEFT;
    }
    if ((o & ::android::hardware::audio::common::V2_0::AudioChannelMask::IN_RIGHT) == static_cast<uint32_t>(::android::hardware::audio::common::V2_0::AudioChannelMask::IN_RIGHT)) {
        os += (first ? "" : " | ");
        os += "IN_RIGHT";
        first = false;
        flipped |= ::android::hardware::audio::common::V2_0::AudioChannelMask::IN_RIGHT;
    }
    if ((o & ::android::hardware::audio::common::V2_0::AudioChannelMask::IN_FRONT) == static_cast<uint32_t>(::android::hardware::audio::common::V2_0::AudioChannelMask::IN_FRONT)) {
        os += (first ? "" : " | ");
        os += "IN_FRONT";
        first = false;
        flipped |= ::android::hardware::audio::common::V2_0::AudioChannelMask::IN_FRONT;
    }
    if ((o & ::android::hardware::audio::common::V2_0::AudioChannelMask::IN_BACK) == static_cast<uint32_t>(::android::hardware::audio::common::V2_0::AudioChannelMask::IN_BACK)) {
        os += (first ? "" : " | ");
        os += "IN_BACK";
        first = false;
        flipped |= ::android::hardware::audio::common::V2_0::AudioChannelMask::IN_BACK;
    }
    if ((o & ::android::hardware::audio::common::V2_0::AudioChannelMask::IN_LEFT_PROCESSED) == static_cast<uint32_t>(::android::hardware::audio::common::V2_0::AudioChannelMask::IN_LEFT_PROCESSED)) {
        os += (first ? "" : " | ");
        os += "IN_LEFT_PROCESSED";
        first = false;
        flipped |= ::android::hardware::audio::common::V2_0::AudioChannelMask::IN_LEFT_PROCESSED;
    }
    if ((o & ::android::hardware::audio::common::V2_0::AudioChannelMask::IN_RIGHT_PROCESSED) == static_cast<uint32_t>(::android::hardware::audio::common::V2_0::AudioChannelMask::IN_RIGHT_PROCESSED)) {
        os += (first ? "" : " | ");
        os += "IN_RIGHT_PROCESSED";
        first = false;
        flipped |= ::android::hardware::audio::common::V2_0::AudioChannelMask::IN_RIGHT_PROCESSED;
    }
    if ((o & ::android::hardware::audio::common::V2_0::AudioChannelMask::IN_FRONT_PROCESSED) == static_cast<uint32_t>(::android::hardware::audio::common::V2_0::AudioChannelMask::IN_FRONT_PROCESSED)) {
        os += (first ? "" : " | ");
        os += "IN_FRONT_PROCESSED";
        first = false;
        flipped |= ::android::hardware::audio::common::V2_0::AudioChannelMask::IN_FRONT_PROCESSED;
    }
    if ((o & ::android::hardware::audio::common::V2_0::AudioChannelMask::IN_BACK_PROCESSED) == static_cast<uint32_t>(::android::hardware::audio::common::V2_0::AudioChannelMask::IN_BACK_PROCESSED)) {
        os += (first ? "" : " | ");
        os += "IN_BACK_PROCESSED";
        first = false;
        flipped |= ::android::hardware::audio::common::V2_0::AudioChannelMask::IN_BACK_PROCESSED;
    }
    if ((o & ::android::hardware::audio::common::V2_0::AudioChannelMask::IN_PRESSURE) == static_cast<uint32_t>(::android::hardware::audio::common::V2_0::AudioChannelMask::IN_PRESSURE)) {
        os += (first ? "" : " | ");
        os += "IN_PRESSURE";
        first = false;
        flipped |= ::android::hardware::audio::common::V2_0::AudioChannelMask::IN_PRESSURE;
    }
    if ((o & ::android::hardware::audio::common::V2_0::AudioChannelMask::IN_X_AXIS) == static_cast<uint32_t>(::android::hardware::audio::common::V2_0::AudioChannelMask::IN_X_AXIS)) {
        os += (first ? "" : " | ");
        os += "IN_X_AXIS";
        first = false;
        flipped |= ::android::hardware::audio::common::V2_0::AudioChannelMask::IN_X_AXIS;
    }
    if ((o & ::android::hardware::audio::common::V2_0::AudioChannelMask::IN_Y_AXIS) == static_cast<uint32_t>(::android::hardware::audio::common::V2_0::AudioChannelMask::IN_Y_AXIS)) {
        os += (first ? "" : " | ");
        os += "IN_Y_AXIS";
        first = false;
        flipped |= ::android::hardware::audio::common::V2_0::AudioChannelMask::IN_Y_AXIS;
    }
    if ((o & ::android::hardware::audio::common::V2_0::AudioChannelMask::IN_Z_AXIS) == static_cast<uint32_t>(::android::hardware::audio::common::V2_0::AudioChannelMask::IN_Z_AXIS)) {
        os += (first ? "" : " | ");
        os += "IN_Z_AXIS";
        first = false;
        flipped |= ::android::hardware::audio::common::V2_0::AudioChannelMask::IN_Z_AXIS;
    }
    if ((o & ::android::hardware::audio::common::V2_0::AudioChannelMask::IN_VOICE_UPLINK) == static_cast<uint32_t>(::android::hardware::audio::common::V2_0::AudioChannelMask::IN_VOICE_UPLINK)) {
        os += (first ? "" : " | ");
        os += "IN_VOICE_UPLINK";
        first = false;
        flipped |= ::android::hardware::audio::common::V2_0::AudioChannelMask::IN_VOICE_UPLINK;
    }
    if ((o & ::android::hardware::audio::common::V2_0::AudioChannelMask::IN_VOICE_DNLINK) == static_cast<uint32_t>(::android::hardware::audio::common::V2_0::AudioChannelMask::IN_VOICE_DNLINK)) {
        os += (first ? "" : " | ");
        os += "IN_VOICE_DNLINK";
        first = false;
        flipped |= ::android::hardware::audio::common::V2_0::AudioChannelMask::IN_VOICE_DNLINK;
    }
    if ((o & ::android::hardware::audio::common::V2_0::AudioChannelMask::IN_MONO) == static_cast<uint32_t>(::android::hardware::audio::common::V2_0::AudioChannelMask::IN_MONO)) {
        os += (first ? "" : " | ");
        os += "IN_MONO";
        first = false;
        flipped |= ::android::hardware::audio::common::V2_0::AudioChannelMask::IN_MONO;
    }
    if ((o & ::android::hardware::audio::common::V2_0::AudioChannelMask::IN_STEREO) == static_cast<uint32_t>(::android::hardware::audio::common::V2_0::AudioChannelMask::IN_STEREO)) {
        os += (first ? "" : " | ");
        os += "IN_STEREO";
        first = false;
        flipped |= ::android::hardware::audio::common::V2_0::AudioChannelMask::IN_STEREO;
    }
    if ((o & ::android::hardware::audio::common::V2_0::AudioChannelMask::IN_FRONT_BACK) == static_cast<uint32_t>(::android::hardware::audio::common::V2_0::AudioChannelMask::IN_FRONT_BACK)) {
        os += (first ? "" : " | ");
        os += "IN_FRONT_BACK";
        first = false;
        flipped |= ::android::hardware::audio::common::V2_0::AudioChannelMask::IN_FRONT_BACK;
    }
    if ((o & ::android::hardware::audio::common::V2_0::AudioChannelMask::IN_6) == static_cast<uint32_t>(::android::hardware::audio::common::V2_0::AudioChannelMask::IN_6)) {
        os += (first ? "" : " | ");
        os += "IN_6";
        first = false;
        flipped |= ::android::hardware::audio::common::V2_0::AudioChannelMask::IN_6;
    }
    if ((o & ::android::hardware::audio::common::V2_0::AudioChannelMask::IN_VOICE_UPLINK_MONO) == static_cast<uint32_t>(::android::hardware::audio::common::V2_0::AudioChannelMask::IN_VOICE_UPLINK_MONO)) {
        os += (first ? "" : " | ");
        os += "IN_VOICE_UPLINK_MONO";
        first = false;
        flipped |= ::android::hardware::audio::common::V2_0::AudioChannelMask::IN_VOICE_UPLINK_MONO;
    }
    if ((o & ::android::hardware::audio::common::V2_0::AudioChannelMask::IN_VOICE_DNLINK_MONO) == static_cast<uint32_t>(::android::hardware::audio::common::V2_0::AudioChannelMask::IN_VOICE_DNLINK_MONO)) {
        os += (first ? "" : " | ");
        os += "IN_VOICE_DNLINK_MONO";
        first = false;
        flipped |= ::android::hardware::audio::common::V2_0::AudioChannelMask::IN_VOICE_DNLINK_MONO;
    }
    if ((o & ::android::hardware::audio::common::V2_0::AudioChannelMask::IN_VOICE_CALL_MONO) == static_cast<uint32_t>(::android::hardware::audio::common::V2_0::AudioChannelMask::IN_VOICE_CALL_MONO)) {
        os += (first ? "" : " | ");
        os += "IN_VOICE_CALL_MONO";
        first = false;
        flipped |= ::android::hardware::audio::common::V2_0::AudioChannelMask::IN_VOICE_CALL_MONO;
    }
    if ((o & ::android::hardware::audio::common::V2_0::AudioChannelMask::IN_ALL) == static_cast<uint32_t>(::android::hardware::audio::common::V2_0::AudioChannelMask::IN_ALL)) {
        os += (first ? "" : " | ");
        os += "IN_ALL";
        first = false;
        flipped |= ::android::hardware::audio::common::V2_0::AudioChannelMask::IN_ALL;
    }
    if ((o & ::android::hardware::audio::common::V2_0::AudioChannelMask::COUNT_MAX) == static_cast<uint32_t>(::android::hardware::audio::common::V2_0::AudioChannelMask::COUNT_MAX)) {
        os += (first ? "" : " | ");
        os += "COUNT_MAX";
        first = false;
        flipped |= ::android::hardware::audio::common::V2_0::AudioChannelMask::COUNT_MAX;
    }
    if ((o & ::android::hardware::audio::common::V2_0::AudioChannelMask::INDEX_HDR) == static_cast<uint32_t>(::android::hardware::audio::common::V2_0::AudioChannelMask::INDEX_HDR)) {
        os += (first ? "" : " | ");
        os += "INDEX_HDR";
        first = false;
        flipped |= ::android::hardware::audio::common::V2_0::AudioChannelMask::INDEX_HDR;
    }
    if ((o & ::android::hardware::audio::common::V2_0::AudioChannelMask::INDEX_MASK_1) == static_cast<uint32_t>(::android::hardware::audio::common::V2_0::AudioChannelMask::INDEX_MASK_1)) {
        os += (first ? "" : " | ");
        os += "INDEX_MASK_1";
        first = false;
        flipped |= ::android::hardware::audio::common::V2_0::AudioChannelMask::INDEX_MASK_1;
    }
    if ((o & ::android::hardware::audio::common::V2_0::AudioChannelMask::INDEX_MASK_2) == static_cast<uint32_t>(::android::hardware::audio::common::V2_0::AudioChannelMask::INDEX_MASK_2)) {
        os += (first ? "" : " | ");
        os += "INDEX_MASK_2";
        first = false;
        flipped |= ::android::hardware::audio::common::V2_0::AudioChannelMask::INDEX_MASK_2;
    }
    if ((o & ::android::hardware::audio::common::V2_0::AudioChannelMask::INDEX_MASK_3) == static_cast<uint32_t>(::android::hardware::audio::common::V2_0::AudioChannelMask::INDEX_MASK_3)) {
        os += (first ? "" : " | ");
        os += "INDEX_MASK_3";
        first = false;
        flipped |= ::android::hardware::audio::common::V2_0::AudioChannelMask::INDEX_MASK_3;
    }
    if ((o & ::android::hardware::audio::common::V2_0::AudioChannelMask::INDEX_MASK_4) == static_cast<uint32_t>(::android::hardware::audio::common::V2_0::AudioChannelMask::INDEX_MASK_4)) {
        os += (first ? "" : " | ");
        os += "INDEX_MASK_4";
        first = false;
        flipped |= ::android::hardware::audio::common::V2_0::AudioChannelMask::INDEX_MASK_4;
    }
    if ((o & ::android::hardware::audio::common::V2_0::AudioChannelMask::INDEX_MASK_5) == static_cast<uint32_t>(::android::hardware::audio::common::V2_0::AudioChannelMask::INDEX_MASK_5)) {
        os += (first ? "" : " | ");
        os += "INDEX_MASK_5";
        first = false;
        flipped |= ::android::hardware::audio::common::V2_0::AudioChannelMask::INDEX_MASK_5;
    }
    if ((o & ::android::hardware::audio::common::V2_0::AudioChannelMask::INDEX_MASK_6) == static_cast<uint32_t>(::android::hardware::audio::common::V2_0::AudioChannelMask::INDEX_MASK_6)) {
        os += (first ? "" : " | ");
        os += "INDEX_MASK_6";
        first = false;
        flipped |= ::android::hardware::audio::common::V2_0::AudioChannelMask::INDEX_MASK_6;
    }
    if ((o & ::android::hardware::audio::common::V2_0::AudioChannelMask::INDEX_MASK_7) == static_cast<uint32_t>(::android::hardware::audio::common::V2_0::AudioChannelMask::INDEX_MASK_7)) {
        os += (first ? "" : " | ");
        os += "INDEX_MASK_7";
        first = false;
        flipped |= ::android::hardware::audio::common::V2_0::AudioChannelMask::INDEX_MASK_7;
    }
    if ((o & ::android::hardware::audio::common::V2_0::AudioChannelMask::INDEX_MASK_8) == static_cast<uint32_t>(::android::hardware::audio::common::V2_0::AudioChannelMask::INDEX_MASK_8)) {
        os += (first ? "" : " | ");
        os += "INDEX_MASK_8";
        first = false;
        flipped |= ::android::hardware::audio::common::V2_0::AudioChannelMask::INDEX_MASK_8;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::audio::common::V2_0::AudioChannelMask o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::audio::common::V2_0::AudioChannelMask::REPRESENTATION_POSITION) {
        return "REPRESENTATION_POSITION";
    }
    if (o == ::android::hardware::audio::common::V2_0::AudioChannelMask::REPRESENTATION_INDEX) {
        return "REPRESENTATION_INDEX";
    }
    if (o == ::android::hardware::audio::common::V2_0::AudioChannelMask::NONE) {
        return "NONE";
    }
    if (o == ::android::hardware::audio::common::V2_0::AudioChannelMask::INVALID) {
        return "INVALID";
    }
    if (o == ::android::hardware::audio::common::V2_0::AudioChannelMask::OUT_FRONT_LEFT) {
        return "OUT_FRONT_LEFT";
    }
    if (o == ::android::hardware::audio::common::V2_0::AudioChannelMask::OUT_FRONT_RIGHT) {
        return "OUT_FRONT_RIGHT";
    }
    if (o == ::android::hardware::audio::common::V2_0::AudioChannelMask::OUT_FRONT_CENTER) {
        return "OUT_FRONT_CENTER";
    }
    if (o == ::android::hardware::audio::common::V2_0::AudioChannelMask::OUT_LOW_FREQUENCY) {
        return "OUT_LOW_FREQUENCY";
    }
    if (o == ::android::hardware::audio::common::V2_0::AudioChannelMask::OUT_BACK_LEFT) {
        return "OUT_BACK_LEFT";
    }
    if (o == ::android::hardware::audio::common::V2_0::AudioChannelMask::OUT_BACK_RIGHT) {
        return "OUT_BACK_RIGHT";
    }
    if (o == ::android::hardware::audio::common::V2_0::AudioChannelMask::OUT_FRONT_LEFT_OF_CENTER) {
        return "OUT_FRONT_LEFT_OF_CENTER";
    }
    if (o == ::android::hardware::audio::common::V2_0::AudioChannelMask::OUT_FRONT_RIGHT_OF_CENTER) {
        return "OUT_FRONT_RIGHT_OF_CENTER";
    }
    if (o == ::android::hardware::audio::common::V2_0::AudioChannelMask::OUT_BACK_CENTER) {
        return "OUT_BACK_CENTER";
    }
    if (o == ::android::hardware::audio::common::V2_0::AudioChannelMask::OUT_SIDE_LEFT) {
        return "OUT_SIDE_LEFT";
    }
    if (o == ::android::hardware::audio::common::V2_0::AudioChannelMask::OUT_SIDE_RIGHT) {
        return "OUT_SIDE_RIGHT";
    }
    if (o == ::android::hardware::audio::common::V2_0::AudioChannelMask::OUT_TOP_CENTER) {
        return "OUT_TOP_CENTER";
    }
    if (o == ::android::hardware::audio::common::V2_0::AudioChannelMask::OUT_TOP_FRONT_LEFT) {
        return "OUT_TOP_FRONT_LEFT";
    }
    if (o == ::android::hardware::audio::common::V2_0::AudioChannelMask::OUT_TOP_FRONT_CENTER) {
        return "OUT_TOP_FRONT_CENTER";
    }
    if (o == ::android::hardware::audio::common::V2_0::AudioChannelMask::OUT_TOP_FRONT_RIGHT) {
        return "OUT_TOP_FRONT_RIGHT";
    }
    if (o == ::android::hardware::audio::common::V2_0::AudioChannelMask::OUT_TOP_BACK_LEFT) {
        return "OUT_TOP_BACK_LEFT";
    }
    if (o == ::android::hardware::audio::common::V2_0::AudioChannelMask::OUT_TOP_BACK_CENTER) {
        return "OUT_TOP_BACK_CENTER";
    }
    if (o == ::android::hardware::audio::common::V2_0::AudioChannelMask::OUT_TOP_BACK_RIGHT) {
        return "OUT_TOP_BACK_RIGHT";
    }
    if (o == ::android::hardware::audio::common::V2_0::AudioChannelMask::OUT_MONO) {
        return "OUT_MONO";
    }
    if (o == ::android::hardware::audio::common::V2_0::AudioChannelMask::OUT_STEREO) {
        return "OUT_STEREO";
    }
    if (o == ::android::hardware::audio::common::V2_0::AudioChannelMask::OUT_2POINT1) {
        return "OUT_2POINT1";
    }
    if (o == ::android::hardware::audio::common::V2_0::AudioChannelMask::OUT_QUAD) {
        return "OUT_QUAD";
    }
    if (o == ::android::hardware::audio::common::V2_0::AudioChannelMask::OUT_QUAD_BACK) {
        return "OUT_QUAD_BACK";
    }
    if (o == ::android::hardware::audio::common::V2_0::AudioChannelMask::OUT_QUAD_SIDE) {
        return "OUT_QUAD_SIDE";
    }
    if (o == ::android::hardware::audio::common::V2_0::AudioChannelMask::OUT_SURROUND) {
        return "OUT_SURROUND";
    }
    if (o == ::android::hardware::audio::common::V2_0::AudioChannelMask::OUT_PENTA) {
        return "OUT_PENTA";
    }
    if (o == ::android::hardware::audio::common::V2_0::AudioChannelMask::OUT_5POINT1) {
        return "OUT_5POINT1";
    }
    if (o == ::android::hardware::audio::common::V2_0::AudioChannelMask::OUT_5POINT1_BACK) {
        return "OUT_5POINT1_BACK";
    }
    if (o == ::android::hardware::audio::common::V2_0::AudioChannelMask::OUT_5POINT1_SIDE) {
        return "OUT_5POINT1_SIDE";
    }
    if (o == ::android::hardware::audio::common::V2_0::AudioChannelMask::OUT_6POINT1) {
        return "OUT_6POINT1";
    }
    if (o == ::android::hardware::audio::common::V2_0::AudioChannelMask::OUT_7POINT1) {
        return "OUT_7POINT1";
    }
    if (o == ::android::hardware::audio::common::V2_0::AudioChannelMask::OUT_ALL) {
        return "OUT_ALL";
    }
    if (o == ::android::hardware::audio::common::V2_0::AudioChannelMask::IN_LEFT) {
        return "IN_LEFT";
    }
    if (o == ::android::hardware::audio::common::V2_0::AudioChannelMask::IN_RIGHT) {
        return "IN_RIGHT";
    }
    if (o == ::android::hardware::audio::common::V2_0::AudioChannelMask::IN_FRONT) {
        return "IN_FRONT";
    }
    if (o == ::android::hardware::audio::common::V2_0::AudioChannelMask::IN_BACK) {
        return "IN_BACK";
    }
    if (o == ::android::hardware::audio::common::V2_0::AudioChannelMask::IN_LEFT_PROCESSED) {
        return "IN_LEFT_PROCESSED";
    }
    if (o == ::android::hardware::audio::common::V2_0::AudioChannelMask::IN_RIGHT_PROCESSED) {
        return "IN_RIGHT_PROCESSED";
    }
    if (o == ::android::hardware::audio::common::V2_0::AudioChannelMask::IN_FRONT_PROCESSED) {
        return "IN_FRONT_PROCESSED";
    }
    if (o == ::android::hardware::audio::common::V2_0::AudioChannelMask::IN_BACK_PROCESSED) {
        return "IN_BACK_PROCESSED";
    }
    if (o == ::android::hardware::audio::common::V2_0::AudioChannelMask::IN_PRESSURE) {
        return "IN_PRESSURE";
    }
    if (o == ::android::hardware::audio::common::V2_0::AudioChannelMask::IN_X_AXIS) {
        return "IN_X_AXIS";
    }
    if (o == ::android::hardware::audio::common::V2_0::AudioChannelMask::IN_Y_AXIS) {
        return "IN_Y_AXIS";
    }
    if (o == ::android::hardware::audio::common::V2_0::AudioChannelMask::IN_Z_AXIS) {
        return "IN_Z_AXIS";
    }
    if (o == ::android::hardware::audio::common::V2_0::AudioChannelMask::IN_VOICE_UPLINK) {
        return "IN_VOICE_UPLINK";
    }
    if (o == ::android::hardware::audio::common::V2_0::AudioChannelMask::IN_VOICE_DNLINK) {
        return "IN_VOICE_DNLINK";
    }
    if (o == ::android::hardware::audio::common::V2_0::AudioChannelMask::IN_MONO) {
        return "IN_MONO";
    }
    if (o == ::android::hardware::audio::common::V2_0::AudioChannelMask::IN_STEREO) {
        return "IN_STEREO";
    }
    if (o == ::android::hardware::audio::common::V2_0::AudioChannelMask::IN_FRONT_BACK) {
        return "IN_FRONT_BACK";
    }
    if (o == ::android::hardware::audio::common::V2_0::AudioChannelMask::IN_6) {
        return "IN_6";
    }
    if (o == ::android::hardware::audio::common::V2_0::AudioChannelMask::IN_VOICE_UPLINK_MONO) {
        return "IN_VOICE_UPLINK_MONO";
    }
    if (o == ::android::hardware::audio::common::V2_0::AudioChannelMask::IN_VOICE_DNLINK_MONO) {
        return "IN_VOICE_DNLINK_MONO";
    }
    if (o == ::android::hardware::audio::common::V2_0::AudioChannelMask::IN_VOICE_CALL_MONO) {
        return "IN_VOICE_CALL_MONO";
    }
    if (o == ::android::hardware::audio::common::V2_0::AudioChannelMask::IN_ALL) {
        return "IN_ALL";
    }
    if (o == ::android::hardware::audio::common::V2_0::AudioChannelMask::COUNT_MAX) {
        return "COUNT_MAX";
    }
    if (o == ::android::hardware::audio::common::V2_0::AudioChannelMask::INDEX_HDR) {
        return "INDEX_HDR";
    }
    if (o == ::android::hardware::audio::common::V2_0::AudioChannelMask::INDEX_MASK_1) {
        return "INDEX_MASK_1";
    }
    if (o == ::android::hardware::audio::common::V2_0::AudioChannelMask::INDEX_MASK_2) {
        return "INDEX_MASK_2";
    }
    if (o == ::android::hardware::audio::common::V2_0::AudioChannelMask::INDEX_MASK_3) {
        return "INDEX_MASK_3";
    }
    if (o == ::android::hardware::audio::common::V2_0::AudioChannelMask::INDEX_MASK_4) {
        return "INDEX_MASK_4";
    }
    if (o == ::android::hardware::audio::common::V2_0::AudioChannelMask::INDEX_MASK_5) {
        return "INDEX_MASK_5";
    }
    if (o == ::android::hardware::audio::common::V2_0::AudioChannelMask::INDEX_MASK_6) {
        return "INDEX_MASK_6";
    }
    if (o == ::android::hardware::audio::common::V2_0::AudioChannelMask::INDEX_MASK_7) {
        return "INDEX_MASK_7";
    }
    if (o == ::android::hardware::audio::common::V2_0::AudioChannelMask::INDEX_MASK_8) {
        return "INDEX_MASK_8";
    }
    std::string os;
    os += toHexString(static_cast<uint32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::audio::common::V2_0::AudioChannelMask o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::audio::common::V2_0::AudioInterleave>(int32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::audio::common::V2_0::AudioInterleave> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::audio::common::V2_0::AudioInterleave::LEFT) == static_cast<int32_t>(::android::hardware::audio::common::V2_0::AudioInterleave::LEFT)) {
        os += (first ? "" : " | ");
        os += "LEFT";
        first = false;
        flipped |= ::android::hardware::audio::common::V2_0::AudioInterleave::LEFT;
    }
    if ((o & ::android::hardware::audio::common::V2_0::AudioInterleave::RIGHT) == static_cast<int32_t>(::android::hardware::audio::common::V2_0::AudioInterleave::RIGHT)) {
        os += (first ? "" : " | ");
        os += "RIGHT";
        first = false;
        flipped |= ::android::hardware::audio::common::V2_0::AudioInterleave::RIGHT;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::audio::common::V2_0::AudioInterleave o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::audio::common::V2_0::AudioInterleave::LEFT) {
        return "LEFT";
    }
    if (o == ::android::hardware::audio::common::V2_0::AudioInterleave::RIGHT) {
        return "RIGHT";
    }
    std::string os;
    os += toHexString(static_cast<int32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::audio::common::V2_0::AudioInterleave o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::audio::common::V2_0::AudioMode>(int32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::audio::common::V2_0::AudioMode> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::audio::common::V2_0::AudioMode::INVALID) == static_cast<int32_t>(::android::hardware::audio::common::V2_0::AudioMode::INVALID)) {
        os += (first ? "" : " | ");
        os += "INVALID";
        first = false;
        flipped |= ::android::hardware::audio::common::V2_0::AudioMode::INVALID;
    }
    if ((o & ::android::hardware::audio::common::V2_0::AudioMode::CURRENT) == static_cast<int32_t>(::android::hardware::audio::common::V2_0::AudioMode::CURRENT)) {
        os += (first ? "" : " | ");
        os += "CURRENT";
        first = false;
        flipped |= ::android::hardware::audio::common::V2_0::AudioMode::CURRENT;
    }
    if ((o & ::android::hardware::audio::common::V2_0::AudioMode::NORMAL) == static_cast<int32_t>(::android::hardware::audio::common::V2_0::AudioMode::NORMAL)) {
        os += (first ? "" : " | ");
        os += "NORMAL";
        first = false;
        flipped |= ::android::hardware::audio::common::V2_0::AudioMode::NORMAL;
    }
    if ((o & ::android::hardware::audio::common::V2_0::AudioMode::RINGTONE) == static_cast<int32_t>(::android::hardware::audio::common::V2_0::AudioMode::RINGTONE)) {
        os += (first ? "" : " | ");
        os += "RINGTONE";
        first = false;
        flipped |= ::android::hardware::audio::common::V2_0::AudioMode::RINGTONE;
    }
    if ((o & ::android::hardware::audio::common::V2_0::AudioMode::IN_CALL) == static_cast<int32_t>(::android::hardware::audio::common::V2_0::AudioMode::IN_CALL)) {
        os += (first ? "" : " | ");
        os += "IN_CALL";
        first = false;
        flipped |= ::android::hardware::audio::common::V2_0::AudioMode::IN_CALL;
    }
    if ((o & ::android::hardware::audio::common::V2_0::AudioMode::IN_COMMUNICATION) == static_cast<int32_t>(::android::hardware::audio::common::V2_0::AudioMode::IN_COMMUNICATION)) {
        os += (first ? "" : " | ");
        os += "IN_COMMUNICATION";
        first = false;
        flipped |= ::android::hardware::audio::common::V2_0::AudioMode::IN_COMMUNICATION;
    }
    if ((o & ::android::hardware::audio::common::V2_0::AudioMode::CNT) == static_cast<int32_t>(::android::hardware::audio::common::V2_0::AudioMode::CNT)) {
        os += (first ? "" : " | ");
        os += "CNT";
        first = false;
        flipped |= ::android::hardware::audio::common::V2_0::AudioMode::CNT;
    }
    if ((o & ::android::hardware::audio::common::V2_0::AudioMode::MAX) == static_cast<int32_t>(::android::hardware::audio::common::V2_0::AudioMode::MAX)) {
        os += (first ? "" : " | ");
        os += "MAX";
        first = false;
        flipped |= ::android::hardware::audio::common::V2_0::AudioMode::MAX;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::audio::common::V2_0::AudioMode o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::audio::common::V2_0::AudioMode::INVALID) {
        return "INVALID";
    }
    if (o == ::android::hardware::audio::common::V2_0::AudioMode::CURRENT) {
        return "CURRENT";
    }
    if (o == ::android::hardware::audio::common::V2_0::AudioMode::NORMAL) {
        return "NORMAL";
    }
    if (o == ::android::hardware::audio::common::V2_0::AudioMode::RINGTONE) {
        return "RINGTONE";
    }
    if (o == ::android::hardware::audio::common::V2_0::AudioMode::IN_CALL) {
        return "IN_CALL";
    }
    if (o == ::android::hardware::audio::common::V2_0::AudioMode::IN_COMMUNICATION) {
        return "IN_COMMUNICATION";
    }
    if (o == ::android::hardware::audio::common::V2_0::AudioMode::CNT) {
        return "CNT";
    }
    if (o == ::android::hardware::audio::common::V2_0::AudioMode::MAX) {
        return "MAX";
    }
    std::string os;
    os += toHexString(static_cast<int32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::audio::common::V2_0::AudioMode o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::audio::common::V2_0::AudioDevice>(uint32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::audio::common::V2_0::AudioDevice> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::audio::common::V2_0::AudioDevice::NONE) == static_cast<uint32_t>(::android::hardware::audio::common::V2_0::AudioDevice::NONE)) {
        os += (first ? "" : " | ");
        os += "NONE";
        first = false;
        flipped |= ::android::hardware::audio::common::V2_0::AudioDevice::NONE;
    }
    if ((o & ::android::hardware::audio::common::V2_0::AudioDevice::BIT_IN) == static_cast<uint32_t>(::android::hardware::audio::common::V2_0::AudioDevice::BIT_IN)) {
        os += (first ? "" : " | ");
        os += "BIT_IN";
        first = false;
        flipped |= ::android::hardware::audio::common::V2_0::AudioDevice::BIT_IN;
    }
    if ((o & ::android::hardware::audio::common::V2_0::AudioDevice::BIT_DEFAULT) == static_cast<uint32_t>(::android::hardware::audio::common::V2_0::AudioDevice::BIT_DEFAULT)) {
        os += (first ? "" : " | ");
        os += "BIT_DEFAULT";
        first = false;
        flipped |= ::android::hardware::audio::common::V2_0::AudioDevice::BIT_DEFAULT;
    }
    if ((o & ::android::hardware::audio::common::V2_0::AudioDevice::OUT_EARPIECE) == static_cast<uint32_t>(::android::hardware::audio::common::V2_0::AudioDevice::OUT_EARPIECE)) {
        os += (first ? "" : " | ");
        os += "OUT_EARPIECE";
        first = false;
        flipped |= ::android::hardware::audio::common::V2_0::AudioDevice::OUT_EARPIECE;
    }
    if ((o & ::android::hardware::audio::common::V2_0::AudioDevice::OUT_SPEAKER) == static_cast<uint32_t>(::android::hardware::audio::common::V2_0::AudioDevice::OUT_SPEAKER)) {
        os += (first ? "" : " | ");
        os += "OUT_SPEAKER";
        first = false;
        flipped |= ::android::hardware::audio::common::V2_0::AudioDevice::OUT_SPEAKER;
    }
    if ((o & ::android::hardware::audio::common::V2_0::AudioDevice::OUT_WIRED_HEADSET) == static_cast<uint32_t>(::android::hardware::audio::common::V2_0::AudioDevice::OUT_WIRED_HEADSET)) {
        os += (first ? "" : " | ");
        os += "OUT_WIRED_HEADSET";
        first = false;
        flipped |= ::android::hardware::audio::common::V2_0::AudioDevice::OUT_WIRED_HEADSET;
    }
    if ((o & ::android::hardware::audio::common::V2_0::AudioDevice::OUT_WIRED_HEADPHONE) == static_cast<uint32_t>(::android::hardware::audio::common::V2_0::AudioDevice::OUT_WIRED_HEADPHONE)) {
        os += (first ? "" : " | ");
        os += "OUT_WIRED_HEADPHONE";
        first = false;
        flipped |= ::android::hardware::audio::common::V2_0::AudioDevice::OUT_WIRED_HEADPHONE;
    }
    if ((o & ::android::hardware::audio::common::V2_0::AudioDevice::OUT_BLUETOOTH_SCO) == static_cast<uint32_t>(::android::hardware::audio::common::V2_0::AudioDevice::OUT_BLUETOOTH_SCO)) {
        os += (first ? "" : " | ");
        os += "OUT_BLUETOOTH_SCO";
        first = false;
        flipped |= ::android::hardware::audio::common::V2_0::AudioDevice::OUT_BLUETOOTH_SCO;
    }
    if ((o & ::android::hardware::audio::common::V2_0::AudioDevice::OUT_BLUETOOTH_SCO_HEADSET) == static_cast<uint32_t>(::android::hardware::audio::common::V2_0::AudioDevice::OUT_BLUETOOTH_SCO_HEADSET)) {
        os += (first ? "" : " | ");
        os += "OUT_BLUETOOTH_SCO_HEADSET";
        first = false;
        flipped |= ::android::hardware::audio::common::V2_0::AudioDevice::OUT_BLUETOOTH_SCO_HEADSET;
    }
    if ((o & ::android::hardware::audio::common::V2_0::AudioDevice::OUT_BLUETOOTH_SCO_CARKIT) == static_cast<uint32_t>(::android::hardware::audio::common::V2_0::AudioDevice::OUT_BLUETOOTH_SCO_CARKIT)) {
        os += (first ? "" : " | ");
        os += "OUT_BLUETOOTH_SCO_CARKIT";
        first = false;
        flipped |= ::android::hardware::audio::common::V2_0::AudioDevice::OUT_BLUETOOTH_SCO_CARKIT;
    }
    if ((o & ::android::hardware::audio::common::V2_0::AudioDevice::OUT_BLUETOOTH_A2DP) == static_cast<uint32_t>(::android::hardware::audio::common::V2_0::AudioDevice::OUT_BLUETOOTH_A2DP)) {
        os += (first ? "" : " | ");
        os += "OUT_BLUETOOTH_A2DP";
        first = false;
        flipped |= ::android::hardware::audio::common::V2_0::AudioDevice::OUT_BLUETOOTH_A2DP;
    }
    if ((o & ::android::hardware::audio::common::V2_0::AudioDevice::OUT_BLUETOOTH_A2DP_HEADPHONES) == static_cast<uint32_t>(::android::hardware::audio::common::V2_0::AudioDevice::OUT_BLUETOOTH_A2DP_HEADPHONES)) {
        os += (first ? "" : " | ");
        os += "OUT_BLUETOOTH_A2DP_HEADPHONES";
        first = false;
        flipped |= ::android::hardware::audio::common::V2_0::AudioDevice::OUT_BLUETOOTH_A2DP_HEADPHONES;
    }
    if ((o & ::android::hardware::audio::common::V2_0::AudioDevice::OUT_BLUETOOTH_A2DP_SPEAKER) == static_cast<uint32_t>(::android::hardware::audio::common::V2_0::AudioDevice::OUT_BLUETOOTH_A2DP_SPEAKER)) {
        os += (first ? "" : " | ");
        os += "OUT_BLUETOOTH_A2DP_SPEAKER";
        first = false;
        flipped |= ::android::hardware::audio::common::V2_0::AudioDevice::OUT_BLUETOOTH_A2DP_SPEAKER;
    }
    if ((o & ::android::hardware::audio::common::V2_0::AudioDevice::OUT_AUX_DIGITAL) == static_cast<uint32_t>(::android::hardware::audio::common::V2_0::AudioDevice::OUT_AUX_DIGITAL)) {
        os += (first ? "" : " | ");
        os += "OUT_AUX_DIGITAL";
        first = false;
        flipped |= ::android::hardware::audio::common::V2_0::AudioDevice::OUT_AUX_DIGITAL;
    }
    if ((o & ::android::hardware::audio::common::V2_0::AudioDevice::OUT_HDMI) == static_cast<uint32_t>(::android::hardware::audio::common::V2_0::AudioDevice::OUT_HDMI)) {
        os += (first ? "" : " | ");
        os += "OUT_HDMI";
        first = false;
        flipped |= ::android::hardware::audio::common::V2_0::AudioDevice::OUT_HDMI;
    }
    if ((o & ::android::hardware::audio::common::V2_0::AudioDevice::OUT_ANLG_DOCK_HEADSET) == static_cast<uint32_t>(::android::hardware::audio::common::V2_0::AudioDevice::OUT_ANLG_DOCK_HEADSET)) {
        os += (first ? "" : " | ");
        os += "OUT_ANLG_DOCK_HEADSET";
        first = false;
        flipped |= ::android::hardware::audio::common::V2_0::AudioDevice::OUT_ANLG_DOCK_HEADSET;
    }
    if ((o & ::android::hardware::audio::common::V2_0::AudioDevice::OUT_DGTL_DOCK_HEADSET) == static_cast<uint32_t>(::android::hardware::audio::common::V2_0::AudioDevice::OUT_DGTL_DOCK_HEADSET)) {
        os += (first ? "" : " | ");
        os += "OUT_DGTL_DOCK_HEADSET";
        first = false;
        flipped |= ::android::hardware::audio::common::V2_0::AudioDevice::OUT_DGTL_DOCK_HEADSET;
    }
    if ((o & ::android::hardware::audio::common::V2_0::AudioDevice::OUT_USB_ACCESSORY) == static_cast<uint32_t>(::android::hardware::audio::common::V2_0::AudioDevice::OUT_USB_ACCESSORY)) {
        os += (first ? "" : " | ");
        os += "OUT_USB_ACCESSORY";
        first = false;
        flipped |= ::android::hardware::audio::common::V2_0::AudioDevice::OUT_USB_ACCESSORY;
    }
    if ((o & ::android::hardware::audio::common::V2_0::AudioDevice::OUT_USB_DEVICE) == static_cast<uint32_t>(::android::hardware::audio::common::V2_0::AudioDevice::OUT_USB_DEVICE)) {
        os += (first ? "" : " | ");
        os += "OUT_USB_DEVICE";
        first = false;
        flipped |= ::android::hardware::audio::common::V2_0::AudioDevice::OUT_USB_DEVICE;
    }
    if ((o & ::android::hardware::audio::common::V2_0::AudioDevice::OUT_REMOTE_SUBMIX) == static_cast<uint32_t>(::android::hardware::audio::common::V2_0::AudioDevice::OUT_REMOTE_SUBMIX)) {
        os += (first ? "" : " | ");
        os += "OUT_REMOTE_SUBMIX";
        first = false;
        flipped |= ::android::hardware::audio::common::V2_0::AudioDevice::OUT_REMOTE_SUBMIX;
    }
    if ((o & ::android::hardware::audio::common::V2_0::AudioDevice::OUT_TELEPHONY_TX) == static_cast<uint32_t>(::android::hardware::audio::common::V2_0::AudioDevice::OUT_TELEPHONY_TX)) {
        os += (first ? "" : " | ");
        os += "OUT_TELEPHONY_TX";
        first = false;
        flipped |= ::android::hardware::audio::common::V2_0::AudioDevice::OUT_TELEPHONY_TX;
    }
    if ((o & ::android::hardware::audio::common::V2_0::AudioDevice::OUT_LINE) == static_cast<uint32_t>(::android::hardware::audio::common::V2_0::AudioDevice::OUT_LINE)) {
        os += (first ? "" : " | ");
        os += "OUT_LINE";
        first = false;
        flipped |= ::android::hardware::audio::common::V2_0::AudioDevice::OUT_LINE;
    }
    if ((o & ::android::hardware::audio::common::V2_0::AudioDevice::OUT_HDMI_ARC) == static_cast<uint32_t>(::android::hardware::audio::common::V2_0::AudioDevice::OUT_HDMI_ARC)) {
        os += (first ? "" : " | ");
        os += "OUT_HDMI_ARC";
        first = false;
        flipped |= ::android::hardware::audio::common::V2_0::AudioDevice::OUT_HDMI_ARC;
    }
    if ((o & ::android::hardware::audio::common::V2_0::AudioDevice::OUT_SPDIF) == static_cast<uint32_t>(::android::hardware::audio::common::V2_0::AudioDevice::OUT_SPDIF)) {
        os += (first ? "" : " | ");
        os += "OUT_SPDIF";
        first = false;
        flipped |= ::android::hardware::audio::common::V2_0::AudioDevice::OUT_SPDIF;
    }
    if ((o & ::android::hardware::audio::common::V2_0::AudioDevice::OUT_FM) == static_cast<uint32_t>(::android::hardware::audio::common::V2_0::AudioDevice::OUT_FM)) {
        os += (first ? "" : " | ");
        os += "OUT_FM";
        first = false;
        flipped |= ::android::hardware::audio::common::V2_0::AudioDevice::OUT_FM;
    }
    if ((o & ::android::hardware::audio::common::V2_0::AudioDevice::OUT_AUX_LINE) == static_cast<uint32_t>(::android::hardware::audio::common::V2_0::AudioDevice::OUT_AUX_LINE)) {
        os += (first ? "" : " | ");
        os += "OUT_AUX_LINE";
        first = false;
        flipped |= ::android::hardware::audio::common::V2_0::AudioDevice::OUT_AUX_LINE;
    }
    if ((o & ::android::hardware::audio::common::V2_0::AudioDevice::OUT_SPEAKER_SAFE) == static_cast<uint32_t>(::android::hardware::audio::common::V2_0::AudioDevice::OUT_SPEAKER_SAFE)) {
        os += (first ? "" : " | ");
        os += "OUT_SPEAKER_SAFE";
        first = false;
        flipped |= ::android::hardware::audio::common::V2_0::AudioDevice::OUT_SPEAKER_SAFE;
    }
    if ((o & ::android::hardware::audio::common::V2_0::AudioDevice::OUT_IP) == static_cast<uint32_t>(::android::hardware::audio::common::V2_0::AudioDevice::OUT_IP)) {
        os += (first ? "" : " | ");
        os += "OUT_IP";
        first = false;
        flipped |= ::android::hardware::audio::common::V2_0::AudioDevice::OUT_IP;
    }
    if ((o & ::android::hardware::audio::common::V2_0::AudioDevice::OUT_BUS) == static_cast<uint32_t>(::android::hardware::audio::common::V2_0::AudioDevice::OUT_BUS)) {
        os += (first ? "" : " | ");
        os += "OUT_BUS";
        first = false;
        flipped |= ::android::hardware::audio::common::V2_0::AudioDevice::OUT_BUS;
    }
    if ((o & ::android::hardware::audio::common::V2_0::AudioDevice::OUT_PROXY) == static_cast<uint32_t>(::android::hardware::audio::common::V2_0::AudioDevice::OUT_PROXY)) {
        os += (first ? "" : " | ");
        os += "OUT_PROXY";
        first = false;
        flipped |= ::android::hardware::audio::common::V2_0::AudioDevice::OUT_PROXY;
    }
    if ((o & ::android::hardware::audio::common::V2_0::AudioDevice::OUT_USB_HEADSET) == static_cast<uint32_t>(::android::hardware::audio::common::V2_0::AudioDevice::OUT_USB_HEADSET)) {
        os += (first ? "" : " | ");
        os += "OUT_USB_HEADSET";
        first = false;
        flipped |= ::android::hardware::audio::common::V2_0::AudioDevice::OUT_USB_HEADSET;
    }
    if ((o & ::android::hardware::audio::common::V2_0::AudioDevice::OUT_DEFAULT) == static_cast<uint32_t>(::android::hardware::audio::common::V2_0::AudioDevice::OUT_DEFAULT)) {
        os += (first ? "" : " | ");
        os += "OUT_DEFAULT";
        first = false;
        flipped |= ::android::hardware::audio::common::V2_0::AudioDevice::OUT_DEFAULT;
    }
    if ((o & ::android::hardware::audio::common::V2_0::AudioDevice::OUT_ALL) == static_cast<uint32_t>(::android::hardware::audio::common::V2_0::AudioDevice::OUT_ALL)) {
        os += (first ? "" : " | ");
        os += "OUT_ALL";
        first = false;
        flipped |= ::android::hardware::audio::common::V2_0::AudioDevice::OUT_ALL;
    }
    if ((o & ::android::hardware::audio::common::V2_0::AudioDevice::OUT_ALL_A2DP) == static_cast<uint32_t>(::android::hardware::audio::common::V2_0::AudioDevice::OUT_ALL_A2DP)) {
        os += (first ? "" : " | ");
        os += "OUT_ALL_A2DP";
        first = false;
        flipped |= ::android::hardware::audio::common::V2_0::AudioDevice::OUT_ALL_A2DP;
    }
    if ((o & ::android::hardware::audio::common::V2_0::AudioDevice::OUT_ALL_SCO) == static_cast<uint32_t>(::android::hardware::audio::common::V2_0::AudioDevice::OUT_ALL_SCO)) {
        os += (first ? "" : " | ");
        os += "OUT_ALL_SCO";
        first = false;
        flipped |= ::android::hardware::audio::common::V2_0::AudioDevice::OUT_ALL_SCO;
    }
    if ((o & ::android::hardware::audio::common::V2_0::AudioDevice::OUT_ALL_USB) == static_cast<uint32_t>(::android::hardware::audio::common::V2_0::AudioDevice::OUT_ALL_USB)) {
        os += (first ? "" : " | ");
        os += "OUT_ALL_USB";
        first = false;
        flipped |= ::android::hardware::audio::common::V2_0::AudioDevice::OUT_ALL_USB;
    }
    if ((o & ::android::hardware::audio::common::V2_0::AudioDevice::IN_COMMUNICATION) == static_cast<uint32_t>(::android::hardware::audio::common::V2_0::AudioDevice::IN_COMMUNICATION)) {
        os += (first ? "" : " | ");
        os += "IN_COMMUNICATION";
        first = false;
        flipped |= ::android::hardware::audio::common::V2_0::AudioDevice::IN_COMMUNICATION;
    }
    if ((o & ::android::hardware::audio::common::V2_0::AudioDevice::IN_AMBIENT) == static_cast<uint32_t>(::android::hardware::audio::common::V2_0::AudioDevice::IN_AMBIENT)) {
        os += (first ? "" : " | ");
        os += "IN_AMBIENT";
        first = false;
        flipped |= ::android::hardware::audio::common::V2_0::AudioDevice::IN_AMBIENT;
    }
    if ((o & ::android::hardware::audio::common::V2_0::AudioDevice::IN_BUILTIN_MIC) == static_cast<uint32_t>(::android::hardware::audio::common::V2_0::AudioDevice::IN_BUILTIN_MIC)) {
        os += (first ? "" : " | ");
        os += "IN_BUILTIN_MIC";
        first = false;
        flipped |= ::android::hardware::audio::common::V2_0::AudioDevice::IN_BUILTIN_MIC;
    }
    if ((o & ::android::hardware::audio::common::V2_0::AudioDevice::IN_BLUETOOTH_SCO_HEADSET) == static_cast<uint32_t>(::android::hardware::audio::common::V2_0::AudioDevice::IN_BLUETOOTH_SCO_HEADSET)) {
        os += (first ? "" : " | ");
        os += "IN_BLUETOOTH_SCO_HEADSET";
        first = false;
        flipped |= ::android::hardware::audio::common::V2_0::AudioDevice::IN_BLUETOOTH_SCO_HEADSET;
    }
    if ((o & ::android::hardware::audio::common::V2_0::AudioDevice::IN_WIRED_HEADSET) == static_cast<uint32_t>(::android::hardware::audio::common::V2_0::AudioDevice::IN_WIRED_HEADSET)) {
        os += (first ? "" : " | ");
        os += "IN_WIRED_HEADSET";
        first = false;
        flipped |= ::android::hardware::audio::common::V2_0::AudioDevice::IN_WIRED_HEADSET;
    }
    if ((o & ::android::hardware::audio::common::V2_0::AudioDevice::IN_AUX_DIGITAL) == static_cast<uint32_t>(::android::hardware::audio::common::V2_0::AudioDevice::IN_AUX_DIGITAL)) {
        os += (first ? "" : " | ");
        os += "IN_AUX_DIGITAL";
        first = false;
        flipped |= ::android::hardware::audio::common::V2_0::AudioDevice::IN_AUX_DIGITAL;
    }
    if ((o & ::android::hardware::audio::common::V2_0::AudioDevice::IN_HDMI) == static_cast<uint32_t>(::android::hardware::audio::common::V2_0::AudioDevice::IN_HDMI)) {
        os += (first ? "" : " | ");
        os += "IN_HDMI";
        first = false;
        flipped |= ::android::hardware::audio::common::V2_0::AudioDevice::IN_HDMI;
    }
    if ((o & ::android::hardware::audio::common::V2_0::AudioDevice::IN_VOICE_CALL) == static_cast<uint32_t>(::android::hardware::audio::common::V2_0::AudioDevice::IN_VOICE_CALL)) {
        os += (first ? "" : " | ");
        os += "IN_VOICE_CALL";
        first = false;
        flipped |= ::android::hardware::audio::common::V2_0::AudioDevice::IN_VOICE_CALL;
    }
    if ((o & ::android::hardware::audio::common::V2_0::AudioDevice::IN_TELEPHONY_RX) == static_cast<uint32_t>(::android::hardware::audio::common::V2_0::AudioDevice::IN_TELEPHONY_RX)) {
        os += (first ? "" : " | ");
        os += "IN_TELEPHONY_RX";
        first = false;
        flipped |= ::android::hardware::audio::common::V2_0::AudioDevice::IN_TELEPHONY_RX;
    }
    if ((o & ::android::hardware::audio::common::V2_0::AudioDevice::IN_BACK_MIC) == static_cast<uint32_t>(::android::hardware::audio::common::V2_0::AudioDevice::IN_BACK_MIC)) {
        os += (first ? "" : " | ");
        os += "IN_BACK_MIC";
        first = false;
        flipped |= ::android::hardware::audio::common::V2_0::AudioDevice::IN_BACK_MIC;
    }
    if ((o & ::android::hardware::audio::common::V2_0::AudioDevice::IN_REMOTE_SUBMIX) == static_cast<uint32_t>(::android::hardware::audio::common::V2_0::AudioDevice::IN_REMOTE_SUBMIX)) {
        os += (first ? "" : " | ");
        os += "IN_REMOTE_SUBMIX";
        first = false;
        flipped |= ::android::hardware::audio::common::V2_0::AudioDevice::IN_REMOTE_SUBMIX;
    }
    if ((o & ::android::hardware::audio::common::V2_0::AudioDevice::IN_ANLG_DOCK_HEADSET) == static_cast<uint32_t>(::android::hardware::audio::common::V2_0::AudioDevice::IN_ANLG_DOCK_HEADSET)) {
        os += (first ? "" : " | ");
        os += "IN_ANLG_DOCK_HEADSET";
        first = false;
        flipped |= ::android::hardware::audio::common::V2_0::AudioDevice::IN_ANLG_DOCK_HEADSET;
    }
    if ((o & ::android::hardware::audio::common::V2_0::AudioDevice::IN_DGTL_DOCK_HEADSET) == static_cast<uint32_t>(::android::hardware::audio::common::V2_0::AudioDevice::IN_DGTL_DOCK_HEADSET)) {
        os += (first ? "" : " | ");
        os += "IN_DGTL_DOCK_HEADSET";
        first = false;
        flipped |= ::android::hardware::audio::common::V2_0::AudioDevice::IN_DGTL_DOCK_HEADSET;
    }
    if ((o & ::android::hardware::audio::common::V2_0::AudioDevice::IN_USB_ACCESSORY) == static_cast<uint32_t>(::android::hardware::audio::common::V2_0::AudioDevice::IN_USB_ACCESSORY)) {
        os += (first ? "" : " | ");
        os += "IN_USB_ACCESSORY";
        first = false;
        flipped |= ::android::hardware::audio::common::V2_0::AudioDevice::IN_USB_ACCESSORY;
    }
    if ((o & ::android::hardware::audio::common::V2_0::AudioDevice::IN_USB_DEVICE) == static_cast<uint32_t>(::android::hardware::audio::common::V2_0::AudioDevice::IN_USB_DEVICE)) {
        os += (first ? "" : " | ");
        os += "IN_USB_DEVICE";
        first = false;
        flipped |= ::android::hardware::audio::common::V2_0::AudioDevice::IN_USB_DEVICE;
    }
    if ((o & ::android::hardware::audio::common::V2_0::AudioDevice::IN_FM_TUNER) == static_cast<uint32_t>(::android::hardware::audio::common::V2_0::AudioDevice::IN_FM_TUNER)) {
        os += (first ? "" : " | ");
        os += "IN_FM_TUNER";
        first = false;
        flipped |= ::android::hardware::audio::common::V2_0::AudioDevice::IN_FM_TUNER;
    }
    if ((o & ::android::hardware::audio::common::V2_0::AudioDevice::IN_TV_TUNER) == static_cast<uint32_t>(::android::hardware::audio::common::V2_0::AudioDevice::IN_TV_TUNER)) {
        os += (first ? "" : " | ");
        os += "IN_TV_TUNER";
        first = false;
        flipped |= ::android::hardware::audio::common::V2_0::AudioDevice::IN_TV_TUNER;
    }
    if ((o & ::android::hardware::audio::common::V2_0::AudioDevice::IN_LINE) == static_cast<uint32_t>(::android::hardware::audio::common::V2_0::AudioDevice::IN_LINE)) {
        os += (first ? "" : " | ");
        os += "IN_LINE";
        first = false;
        flipped |= ::android::hardware::audio::common::V2_0::AudioDevice::IN_LINE;
    }
    if ((o & ::android::hardware::audio::common::V2_0::AudioDevice::IN_SPDIF) == static_cast<uint32_t>(::android::hardware::audio::common::V2_0::AudioDevice::IN_SPDIF)) {
        os += (first ? "" : " | ");
        os += "IN_SPDIF";
        first = false;
        flipped |= ::android::hardware::audio::common::V2_0::AudioDevice::IN_SPDIF;
    }
    if ((o & ::android::hardware::audio::common::V2_0::AudioDevice::IN_BLUETOOTH_A2DP) == static_cast<uint32_t>(::android::hardware::audio::common::V2_0::AudioDevice::IN_BLUETOOTH_A2DP)) {
        os += (first ? "" : " | ");
        os += "IN_BLUETOOTH_A2DP";
        first = false;
        flipped |= ::android::hardware::audio::common::V2_0::AudioDevice::IN_BLUETOOTH_A2DP;
    }
    if ((o & ::android::hardware::audio::common::V2_0::AudioDevice::IN_LOOPBACK) == static_cast<uint32_t>(::android::hardware::audio::common::V2_0::AudioDevice::IN_LOOPBACK)) {
        os += (first ? "" : " | ");
        os += "IN_LOOPBACK";
        first = false;
        flipped |= ::android::hardware::audio::common::V2_0::AudioDevice::IN_LOOPBACK;
    }
    if ((o & ::android::hardware::audio::common::V2_0::AudioDevice::IN_IP) == static_cast<uint32_t>(::android::hardware::audio::common::V2_0::AudioDevice::IN_IP)) {
        os += (first ? "" : " | ");
        os += "IN_IP";
        first = false;
        flipped |= ::android::hardware::audio::common::V2_0::AudioDevice::IN_IP;
    }
    if ((o & ::android::hardware::audio::common::V2_0::AudioDevice::IN_BUS) == static_cast<uint32_t>(::android::hardware::audio::common::V2_0::AudioDevice::IN_BUS)) {
        os += (first ? "" : " | ");
        os += "IN_BUS";
        first = false;
        flipped |= ::android::hardware::audio::common::V2_0::AudioDevice::IN_BUS;
    }
    if ((o & ::android::hardware::audio::common::V2_0::AudioDevice::IN_PROXY) == static_cast<uint32_t>(::android::hardware::audio::common::V2_0::AudioDevice::IN_PROXY)) {
        os += (first ? "" : " | ");
        os += "IN_PROXY";
        first = false;
        flipped |= ::android::hardware::audio::common::V2_0::AudioDevice::IN_PROXY;
    }
    if ((o & ::android::hardware::audio::common::V2_0::AudioDevice::IN_USB_HEADSET) == static_cast<uint32_t>(::android::hardware::audio::common::V2_0::AudioDevice::IN_USB_HEADSET)) {
        os += (first ? "" : " | ");
        os += "IN_USB_HEADSET";
        first = false;
        flipped |= ::android::hardware::audio::common::V2_0::AudioDevice::IN_USB_HEADSET;
    }
    if ((o & ::android::hardware::audio::common::V2_0::AudioDevice::IN_DEFAULT) == static_cast<uint32_t>(::android::hardware::audio::common::V2_0::AudioDevice::IN_DEFAULT)) {
        os += (first ? "" : " | ");
        os += "IN_DEFAULT";
        first = false;
        flipped |= ::android::hardware::audio::common::V2_0::AudioDevice::IN_DEFAULT;
    }
    if ((o & ::android::hardware::audio::common::V2_0::AudioDevice::IN_ALL) == static_cast<uint32_t>(::android::hardware::audio::common::V2_0::AudioDevice::IN_ALL)) {
        os += (first ? "" : " | ");
        os += "IN_ALL";
        first = false;
        flipped |= ::android::hardware::audio::common::V2_0::AudioDevice::IN_ALL;
    }
    if ((o & ::android::hardware::audio::common::V2_0::AudioDevice::IN_ALL_SCO) == static_cast<uint32_t>(::android::hardware::audio::common::V2_0::AudioDevice::IN_ALL_SCO)) {
        os += (first ? "" : " | ");
        os += "IN_ALL_SCO";
        first = false;
        flipped |= ::android::hardware::audio::common::V2_0::AudioDevice::IN_ALL_SCO;
    }
    if ((o & ::android::hardware::audio::common::V2_0::AudioDevice::IN_ALL_USB) == static_cast<uint32_t>(::android::hardware::audio::common::V2_0::AudioDevice::IN_ALL_USB)) {
        os += (first ? "" : " | ");
        os += "IN_ALL_USB";
        first = false;
        flipped |= ::android::hardware::audio::common::V2_0::AudioDevice::IN_ALL_USB;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::audio::common::V2_0::AudioDevice o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::audio::common::V2_0::AudioDevice::NONE) {
        return "NONE";
    }
    if (o == ::android::hardware::audio::common::V2_0::AudioDevice::BIT_IN) {
        return "BIT_IN";
    }
    if (o == ::android::hardware::audio::common::V2_0::AudioDevice::BIT_DEFAULT) {
        return "BIT_DEFAULT";
    }
    if (o == ::android::hardware::audio::common::V2_0::AudioDevice::OUT_EARPIECE) {
        return "OUT_EARPIECE";
    }
    if (o == ::android::hardware::audio::common::V2_0::AudioDevice::OUT_SPEAKER) {
        return "OUT_SPEAKER";
    }
    if (o == ::android::hardware::audio::common::V2_0::AudioDevice::OUT_WIRED_HEADSET) {
        return "OUT_WIRED_HEADSET";
    }
    if (o == ::android::hardware::audio::common::V2_0::AudioDevice::OUT_WIRED_HEADPHONE) {
        return "OUT_WIRED_HEADPHONE";
    }
    if (o == ::android::hardware::audio::common::V2_0::AudioDevice::OUT_BLUETOOTH_SCO) {
        return "OUT_BLUETOOTH_SCO";
    }
    if (o == ::android::hardware::audio::common::V2_0::AudioDevice::OUT_BLUETOOTH_SCO_HEADSET) {
        return "OUT_BLUETOOTH_SCO_HEADSET";
    }
    if (o == ::android::hardware::audio::common::V2_0::AudioDevice::OUT_BLUETOOTH_SCO_CARKIT) {
        return "OUT_BLUETOOTH_SCO_CARKIT";
    }
    if (o == ::android::hardware::audio::common::V2_0::AudioDevice::OUT_BLUETOOTH_A2DP) {
        return "OUT_BLUETOOTH_A2DP";
    }
    if (o == ::android::hardware::audio::common::V2_0::AudioDevice::OUT_BLUETOOTH_A2DP_HEADPHONES) {
        return "OUT_BLUETOOTH_A2DP_HEADPHONES";
    }
    if (o == ::android::hardware::audio::common::V2_0::AudioDevice::OUT_BLUETOOTH_A2DP_SPEAKER) {
        return "OUT_BLUETOOTH_A2DP_SPEAKER";
    }
    if (o == ::android::hardware::audio::common::V2_0::AudioDevice::OUT_AUX_DIGITAL) {
        return "OUT_AUX_DIGITAL";
    }
    if (o == ::android::hardware::audio::common::V2_0::AudioDevice::OUT_HDMI) {
        return "OUT_HDMI";
    }
    if (o == ::android::hardware::audio::common::V2_0::AudioDevice::OUT_ANLG_DOCK_HEADSET) {
        return "OUT_ANLG_DOCK_HEADSET";
    }
    if (o == ::android::hardware::audio::common::V2_0::AudioDevice::OUT_DGTL_DOCK_HEADSET) {
        return "OUT_DGTL_DOCK_HEADSET";
    }
    if (o == ::android::hardware::audio::common::V2_0::AudioDevice::OUT_USB_ACCESSORY) {
        return "OUT_USB_ACCESSORY";
    }
    if (o == ::android::hardware::audio::common::V2_0::AudioDevice::OUT_USB_DEVICE) {
        return "OUT_USB_DEVICE";
    }
    if (o == ::android::hardware::audio::common::V2_0::AudioDevice::OUT_REMOTE_SUBMIX) {
        return "OUT_REMOTE_SUBMIX";
    }
    if (o == ::android::hardware::audio::common::V2_0::AudioDevice::OUT_TELEPHONY_TX) {
        return "OUT_TELEPHONY_TX";
    }
    if (o == ::android::hardware::audio::common::V2_0::AudioDevice::OUT_LINE) {
        return "OUT_LINE";
    }
    if (o == ::android::hardware::audio::common::V2_0::AudioDevice::OUT_HDMI_ARC) {
        return "OUT_HDMI_ARC";
    }
    if (o == ::android::hardware::audio::common::V2_0::AudioDevice::OUT_SPDIF) {
        return "OUT_SPDIF";
    }
    if (o == ::android::hardware::audio::common::V2_0::AudioDevice::OUT_FM) {
        return "OUT_FM";
    }
    if (o == ::android::hardware::audio::common::V2_0::AudioDevice::OUT_AUX_LINE) {
        return "OUT_AUX_LINE";
    }
    if (o == ::android::hardware::audio::common::V2_0::AudioDevice::OUT_SPEAKER_SAFE) {
        return "OUT_SPEAKER_SAFE";
    }
    if (o == ::android::hardware::audio::common::V2_0::AudioDevice::OUT_IP) {
        return "OUT_IP";
    }
    if (o == ::android::hardware::audio::common::V2_0::AudioDevice::OUT_BUS) {
        return "OUT_BUS";
    }
    if (o == ::android::hardware::audio::common::V2_0::AudioDevice::OUT_PROXY) {
        return "OUT_PROXY";
    }
    if (o == ::android::hardware::audio::common::V2_0::AudioDevice::OUT_USB_HEADSET) {
        return "OUT_USB_HEADSET";
    }
    if (o == ::android::hardware::audio::common::V2_0::AudioDevice::OUT_DEFAULT) {
        return "OUT_DEFAULT";
    }
    if (o == ::android::hardware::audio::common::V2_0::AudioDevice::OUT_ALL) {
        return "OUT_ALL";
    }
    if (o == ::android::hardware::audio::common::V2_0::AudioDevice::OUT_ALL_A2DP) {
        return "OUT_ALL_A2DP";
    }
    if (o == ::android::hardware::audio::common::V2_0::AudioDevice::OUT_ALL_SCO) {
        return "OUT_ALL_SCO";
    }
    if (o == ::android::hardware::audio::common::V2_0::AudioDevice::OUT_ALL_USB) {
        return "OUT_ALL_USB";
    }
    if (o == ::android::hardware::audio::common::V2_0::AudioDevice::IN_COMMUNICATION) {
        return "IN_COMMUNICATION";
    }
    if (o == ::android::hardware::audio::common::V2_0::AudioDevice::IN_AMBIENT) {
        return "IN_AMBIENT";
    }
    if (o == ::android::hardware::audio::common::V2_0::AudioDevice::IN_BUILTIN_MIC) {
        return "IN_BUILTIN_MIC";
    }
    if (o == ::android::hardware::audio::common::V2_0::AudioDevice::IN_BLUETOOTH_SCO_HEADSET) {
        return "IN_BLUETOOTH_SCO_HEADSET";
    }
    if (o == ::android::hardware::audio::common::V2_0::AudioDevice::IN_WIRED_HEADSET) {
        return "IN_WIRED_HEADSET";
    }
    if (o == ::android::hardware::audio::common::V2_0::AudioDevice::IN_AUX_DIGITAL) {
        return "IN_AUX_DIGITAL";
    }
    if (o == ::android::hardware::audio::common::V2_0::AudioDevice::IN_HDMI) {
        return "IN_HDMI";
    }
    if (o == ::android::hardware::audio::common::V2_0::AudioDevice::IN_VOICE_CALL) {
        return "IN_VOICE_CALL";
    }
    if (o == ::android::hardware::audio::common::V2_0::AudioDevice::IN_TELEPHONY_RX) {
        return "IN_TELEPHONY_RX";
    }
    if (o == ::android::hardware::audio::common::V2_0::AudioDevice::IN_BACK_MIC) {
        return "IN_BACK_MIC";
    }
    if (o == ::android::hardware::audio::common::V2_0::AudioDevice::IN_REMOTE_SUBMIX) {
        return "IN_REMOTE_SUBMIX";
    }
    if (o == ::android::hardware::audio::common::V2_0::AudioDevice::IN_ANLG_DOCK_HEADSET) {
        return "IN_ANLG_DOCK_HEADSET";
    }
    if (o == ::android::hardware::audio::common::V2_0::AudioDevice::IN_DGTL_DOCK_HEADSET) {
        return "IN_DGTL_DOCK_HEADSET";
    }
    if (o == ::android::hardware::audio::common::V2_0::AudioDevice::IN_USB_ACCESSORY) {
        return "IN_USB_ACCESSORY";
    }
    if (o == ::android::hardware::audio::common::V2_0::AudioDevice::IN_USB_DEVICE) {
        return "IN_USB_DEVICE";
    }
    if (o == ::android::hardware::audio::common::V2_0::AudioDevice::IN_FM_TUNER) {
        return "IN_FM_TUNER";
    }
    if (o == ::android::hardware::audio::common::V2_0::AudioDevice::IN_TV_TUNER) {
        return "IN_TV_TUNER";
    }
    if (o == ::android::hardware::audio::common::V2_0::AudioDevice::IN_LINE) {
        return "IN_LINE";
    }
    if (o == ::android::hardware::audio::common::V2_0::AudioDevice::IN_SPDIF) {
        return "IN_SPDIF";
    }
    if (o == ::android::hardware::audio::common::V2_0::AudioDevice::IN_BLUETOOTH_A2DP) {
        return "IN_BLUETOOTH_A2DP";
    }
    if (o == ::android::hardware::audio::common::V2_0::AudioDevice::IN_LOOPBACK) {
        return "IN_LOOPBACK";
    }
    if (o == ::android::hardware::audio::common::V2_0::AudioDevice::IN_IP) {
        return "IN_IP";
    }
    if (o == ::android::hardware::audio::common::V2_0::AudioDevice::IN_BUS) {
        return "IN_BUS";
    }
    if (o == ::android::hardware::audio::common::V2_0::AudioDevice::IN_PROXY) {
        return "IN_PROXY";
    }
    if (o == ::android::hardware::audio::common::V2_0::AudioDevice::IN_USB_HEADSET) {
        return "IN_USB_HEADSET";
    }
    if (o == ::android::hardware::audio::common::V2_0::AudioDevice::IN_DEFAULT) {
        return "IN_DEFAULT";
    }
    if (o == ::android::hardware::audio::common::V2_0::AudioDevice::IN_ALL) {
        return "IN_ALL";
    }
    if (o == ::android::hardware::audio::common::V2_0::AudioDevice::IN_ALL_SCO) {
        return "IN_ALL_SCO";
    }
    if (o == ::android::hardware::audio::common::V2_0::AudioDevice::IN_ALL_USB) {
        return "IN_ALL_USB";
    }
    std::string os;
    os += toHexString(static_cast<uint32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::audio::common::V2_0::AudioDevice o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::audio::common::V2_0::AudioOutputFlag>(int32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::audio::common::V2_0::AudioOutputFlag> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::audio::common::V2_0::AudioOutputFlag::NONE) == static_cast<int32_t>(::android::hardware::audio::common::V2_0::AudioOutputFlag::NONE)) {
        os += (first ? "" : " | ");
        os += "NONE";
        first = false;
        flipped |= ::android::hardware::audio::common::V2_0::AudioOutputFlag::NONE;
    }
    if ((o & ::android::hardware::audio::common::V2_0::AudioOutputFlag::DIRECT) == static_cast<int32_t>(::android::hardware::audio::common::V2_0::AudioOutputFlag::DIRECT)) {
        os += (first ? "" : " | ");
        os += "DIRECT";
        first = false;
        flipped |= ::android::hardware::audio::common::V2_0::AudioOutputFlag::DIRECT;
    }
    if ((o & ::android::hardware::audio::common::V2_0::AudioOutputFlag::PRIMARY) == static_cast<int32_t>(::android::hardware::audio::common::V2_0::AudioOutputFlag::PRIMARY)) {
        os += (first ? "" : " | ");
        os += "PRIMARY";
        first = false;
        flipped |= ::android::hardware::audio::common::V2_0::AudioOutputFlag::PRIMARY;
    }
    if ((o & ::android::hardware::audio::common::V2_0::AudioOutputFlag::FAST) == static_cast<int32_t>(::android::hardware::audio::common::V2_0::AudioOutputFlag::FAST)) {
        os += (first ? "" : " | ");
        os += "FAST";
        first = false;
        flipped |= ::android::hardware::audio::common::V2_0::AudioOutputFlag::FAST;
    }
    if ((o & ::android::hardware::audio::common::V2_0::AudioOutputFlag::DEEP_BUFFER) == static_cast<int32_t>(::android::hardware::audio::common::V2_0::AudioOutputFlag::DEEP_BUFFER)) {
        os += (first ? "" : " | ");
        os += "DEEP_BUFFER";
        first = false;
        flipped |= ::android::hardware::audio::common::V2_0::AudioOutputFlag::DEEP_BUFFER;
    }
    if ((o & ::android::hardware::audio::common::V2_0::AudioOutputFlag::COMPRESS_OFFLOAD) == static_cast<int32_t>(::android::hardware::audio::common::V2_0::AudioOutputFlag::COMPRESS_OFFLOAD)) {
        os += (first ? "" : " | ");
        os += "COMPRESS_OFFLOAD";
        first = false;
        flipped |= ::android::hardware::audio::common::V2_0::AudioOutputFlag::COMPRESS_OFFLOAD;
    }
    if ((o & ::android::hardware::audio::common::V2_0::AudioOutputFlag::NON_BLOCKING) == static_cast<int32_t>(::android::hardware::audio::common::V2_0::AudioOutputFlag::NON_BLOCKING)) {
        os += (first ? "" : " | ");
        os += "NON_BLOCKING";
        first = false;
        flipped |= ::android::hardware::audio::common::V2_0::AudioOutputFlag::NON_BLOCKING;
    }
    if ((o & ::android::hardware::audio::common::V2_0::AudioOutputFlag::HW_AV_SYNC) == static_cast<int32_t>(::android::hardware::audio::common::V2_0::AudioOutputFlag::HW_AV_SYNC)) {
        os += (first ? "" : " | ");
        os += "HW_AV_SYNC";
        first = false;
        flipped |= ::android::hardware::audio::common::V2_0::AudioOutputFlag::HW_AV_SYNC;
    }
    if ((o & ::android::hardware::audio::common::V2_0::AudioOutputFlag::TTS) == static_cast<int32_t>(::android::hardware::audio::common::V2_0::AudioOutputFlag::TTS)) {
        os += (first ? "" : " | ");
        os += "TTS";
        first = false;
        flipped |= ::android::hardware::audio::common::V2_0::AudioOutputFlag::TTS;
    }
    if ((o & ::android::hardware::audio::common::V2_0::AudioOutputFlag::RAW) == static_cast<int32_t>(::android::hardware::audio::common::V2_0::AudioOutputFlag::RAW)) {
        os += (first ? "" : " | ");
        os += "RAW";
        first = false;
        flipped |= ::android::hardware::audio::common::V2_0::AudioOutputFlag::RAW;
    }
    if ((o & ::android::hardware::audio::common::V2_0::AudioOutputFlag::SYNC) == static_cast<int32_t>(::android::hardware::audio::common::V2_0::AudioOutputFlag::SYNC)) {
        os += (first ? "" : " | ");
        os += "SYNC";
        first = false;
        flipped |= ::android::hardware::audio::common::V2_0::AudioOutputFlag::SYNC;
    }
    if ((o & ::android::hardware::audio::common::V2_0::AudioOutputFlag::IEC958_NONAUDIO) == static_cast<int32_t>(::android::hardware::audio::common::V2_0::AudioOutputFlag::IEC958_NONAUDIO)) {
        os += (first ? "" : " | ");
        os += "IEC958_NONAUDIO";
        first = false;
        flipped |= ::android::hardware::audio::common::V2_0::AudioOutputFlag::IEC958_NONAUDIO;
    }
    if ((o & ::android::hardware::audio::common::V2_0::AudioOutputFlag::DIRECT_PCM) == static_cast<int32_t>(::android::hardware::audio::common::V2_0::AudioOutputFlag::DIRECT_PCM)) {
        os += (first ? "" : " | ");
        os += "DIRECT_PCM";
        first = false;
        flipped |= ::android::hardware::audio::common::V2_0::AudioOutputFlag::DIRECT_PCM;
    }
    if ((o & ::android::hardware::audio::common::V2_0::AudioOutputFlag::MMAP_NOIRQ) == static_cast<int32_t>(::android::hardware::audio::common::V2_0::AudioOutputFlag::MMAP_NOIRQ)) {
        os += (first ? "" : " | ");
        os += "MMAP_NOIRQ";
        first = false;
        flipped |= ::android::hardware::audio::common::V2_0::AudioOutputFlag::MMAP_NOIRQ;
    }
    if ((o & ::android::hardware::audio::common::V2_0::AudioOutputFlag::VOIP_RX) == static_cast<int32_t>(::android::hardware::audio::common::V2_0::AudioOutputFlag::VOIP_RX)) {
        os += (first ? "" : " | ");
        os += "VOIP_RX";
        first = false;
        flipped |= ::android::hardware::audio::common::V2_0::AudioOutputFlag::VOIP_RX;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::audio::common::V2_0::AudioOutputFlag o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::audio::common::V2_0::AudioOutputFlag::NONE) {
        return "NONE";
    }
    if (o == ::android::hardware::audio::common::V2_0::AudioOutputFlag::DIRECT) {
        return "DIRECT";
    }
    if (o == ::android::hardware::audio::common::V2_0::AudioOutputFlag::PRIMARY) {
        return "PRIMARY";
    }
    if (o == ::android::hardware::audio::common::V2_0::AudioOutputFlag::FAST) {
        return "FAST";
    }
    if (o == ::android::hardware::audio::common::V2_0::AudioOutputFlag::DEEP_BUFFER) {
        return "DEEP_BUFFER";
    }
    if (o == ::android::hardware::audio::common::V2_0::AudioOutputFlag::COMPRESS_OFFLOAD) {
        return "COMPRESS_OFFLOAD";
    }
    if (o == ::android::hardware::audio::common::V2_0::AudioOutputFlag::NON_BLOCKING) {
        return "NON_BLOCKING";
    }
    if (o == ::android::hardware::audio::common::V2_0::AudioOutputFlag::HW_AV_SYNC) {
        return "HW_AV_SYNC";
    }
    if (o == ::android::hardware::audio::common::V2_0::AudioOutputFlag::TTS) {
        return "TTS";
    }
    if (o == ::android::hardware::audio::common::V2_0::AudioOutputFlag::RAW) {
        return "RAW";
    }
    if (o == ::android::hardware::audio::common::V2_0::AudioOutputFlag::SYNC) {
        return "SYNC";
    }
    if (o == ::android::hardware::audio::common::V2_0::AudioOutputFlag::IEC958_NONAUDIO) {
        return "IEC958_NONAUDIO";
    }
    if (o == ::android::hardware::audio::common::V2_0::AudioOutputFlag::DIRECT_PCM) {
        return "DIRECT_PCM";
    }
    if (o == ::android::hardware::audio::common::V2_0::AudioOutputFlag::MMAP_NOIRQ) {
        return "MMAP_NOIRQ";
    }
    if (o == ::android::hardware::audio::common::V2_0::AudioOutputFlag::VOIP_RX) {
        return "VOIP_RX";
    }
    std::string os;
    os += toHexString(static_cast<int32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::audio::common::V2_0::AudioOutputFlag o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::audio::common::V2_0::AudioInputFlag>(int32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::audio::common::V2_0::AudioInputFlag> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::audio::common::V2_0::AudioInputFlag::NONE) == static_cast<int32_t>(::android::hardware::audio::common::V2_0::AudioInputFlag::NONE)) {
        os += (first ? "" : " | ");
        os += "NONE";
        first = false;
        flipped |= ::android::hardware::audio::common::V2_0::AudioInputFlag::NONE;
    }
    if ((o & ::android::hardware::audio::common::V2_0::AudioInputFlag::FAST) == static_cast<int32_t>(::android::hardware::audio::common::V2_0::AudioInputFlag::FAST)) {
        os += (first ? "" : " | ");
        os += "FAST";
        first = false;
        flipped |= ::android::hardware::audio::common::V2_0::AudioInputFlag::FAST;
    }
    if ((o & ::android::hardware::audio::common::V2_0::AudioInputFlag::HW_HOTWORD) == static_cast<int32_t>(::android::hardware::audio::common::V2_0::AudioInputFlag::HW_HOTWORD)) {
        os += (first ? "" : " | ");
        os += "HW_HOTWORD";
        first = false;
        flipped |= ::android::hardware::audio::common::V2_0::AudioInputFlag::HW_HOTWORD;
    }
    if ((o & ::android::hardware::audio::common::V2_0::AudioInputFlag::RAW) == static_cast<int32_t>(::android::hardware::audio::common::V2_0::AudioInputFlag::RAW)) {
        os += (first ? "" : " | ");
        os += "RAW";
        first = false;
        flipped |= ::android::hardware::audio::common::V2_0::AudioInputFlag::RAW;
    }
    if ((o & ::android::hardware::audio::common::V2_0::AudioInputFlag::SYNC) == static_cast<int32_t>(::android::hardware::audio::common::V2_0::AudioInputFlag::SYNC)) {
        os += (first ? "" : " | ");
        os += "SYNC";
        first = false;
        flipped |= ::android::hardware::audio::common::V2_0::AudioInputFlag::SYNC;
    }
    if ((o & ::android::hardware::audio::common::V2_0::AudioInputFlag::MMAP_NOIRQ) == static_cast<int32_t>(::android::hardware::audio::common::V2_0::AudioInputFlag::MMAP_NOIRQ)) {
        os += (first ? "" : " | ");
        os += "MMAP_NOIRQ";
        first = false;
        flipped |= ::android::hardware::audio::common::V2_0::AudioInputFlag::MMAP_NOIRQ;
    }
    if ((o & ::android::hardware::audio::common::V2_0::AudioInputFlag::VOIP_TX) == static_cast<int32_t>(::android::hardware::audio::common::V2_0::AudioInputFlag::VOIP_TX)) {
        os += (first ? "" : " | ");
        os += "VOIP_TX";
        first = false;
        flipped |= ::android::hardware::audio::common::V2_0::AudioInputFlag::VOIP_TX;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::audio::common::V2_0::AudioInputFlag o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::audio::common::V2_0::AudioInputFlag::NONE) {
        return "NONE";
    }
    if (o == ::android::hardware::audio::common::V2_0::AudioInputFlag::FAST) {
        return "FAST";
    }
    if (o == ::android::hardware::audio::common::V2_0::AudioInputFlag::HW_HOTWORD) {
        return "HW_HOTWORD";
    }
    if (o == ::android::hardware::audio::common::V2_0::AudioInputFlag::RAW) {
        return "RAW";
    }
    if (o == ::android::hardware::audio::common::V2_0::AudioInputFlag::SYNC) {
        return "SYNC";
    }
    if (o == ::android::hardware::audio::common::V2_0::AudioInputFlag::MMAP_NOIRQ) {
        return "MMAP_NOIRQ";
    }
    if (o == ::android::hardware::audio::common::V2_0::AudioInputFlag::VOIP_TX) {
        return "VOIP_TX";
    }
    std::string os;
    os += toHexString(static_cast<int32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::audio::common::V2_0::AudioInputFlag o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::audio::common::V2_0::AudioUsage>(int32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::audio::common::V2_0::AudioUsage> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::audio::common::V2_0::AudioUsage::UNKNOWN) == static_cast<int32_t>(::android::hardware::audio::common::V2_0::AudioUsage::UNKNOWN)) {
        os += (first ? "" : " | ");
        os += "UNKNOWN";
        first = false;
        flipped |= ::android::hardware::audio::common::V2_0::AudioUsage::UNKNOWN;
    }
    if ((o & ::android::hardware::audio::common::V2_0::AudioUsage::MEDIA) == static_cast<int32_t>(::android::hardware::audio::common::V2_0::AudioUsage::MEDIA)) {
        os += (first ? "" : " | ");
        os += "MEDIA";
        first = false;
        flipped |= ::android::hardware::audio::common::V2_0::AudioUsage::MEDIA;
    }
    if ((o & ::android::hardware::audio::common::V2_0::AudioUsage::VOICE_COMMUNICATION) == static_cast<int32_t>(::android::hardware::audio::common::V2_0::AudioUsage::VOICE_COMMUNICATION)) {
        os += (first ? "" : " | ");
        os += "VOICE_COMMUNICATION";
        first = false;
        flipped |= ::android::hardware::audio::common::V2_0::AudioUsage::VOICE_COMMUNICATION;
    }
    if ((o & ::android::hardware::audio::common::V2_0::AudioUsage::VOICE_COMMUNICATION_SIGNALLING) == static_cast<int32_t>(::android::hardware::audio::common::V2_0::AudioUsage::VOICE_COMMUNICATION_SIGNALLING)) {
        os += (first ? "" : " | ");
        os += "VOICE_COMMUNICATION_SIGNALLING";
        first = false;
        flipped |= ::android::hardware::audio::common::V2_0::AudioUsage::VOICE_COMMUNICATION_SIGNALLING;
    }
    if ((o & ::android::hardware::audio::common::V2_0::AudioUsage::ALARM) == static_cast<int32_t>(::android::hardware::audio::common::V2_0::AudioUsage::ALARM)) {
        os += (first ? "" : " | ");
        os += "ALARM";
        first = false;
        flipped |= ::android::hardware::audio::common::V2_0::AudioUsage::ALARM;
    }
    if ((o & ::android::hardware::audio::common::V2_0::AudioUsage::NOTIFICATION) == static_cast<int32_t>(::android::hardware::audio::common::V2_0::AudioUsage::NOTIFICATION)) {
        os += (first ? "" : " | ");
        os += "NOTIFICATION";
        first = false;
        flipped |= ::android::hardware::audio::common::V2_0::AudioUsage::NOTIFICATION;
    }
    if ((o & ::android::hardware::audio::common::V2_0::AudioUsage::NOTIFICATION_TELEPHONY_RINGTONE) == static_cast<int32_t>(::android::hardware::audio::common::V2_0::AudioUsage::NOTIFICATION_TELEPHONY_RINGTONE)) {
        os += (first ? "" : " | ");
        os += "NOTIFICATION_TELEPHONY_RINGTONE";
        first = false;
        flipped |= ::android::hardware::audio::common::V2_0::AudioUsage::NOTIFICATION_TELEPHONY_RINGTONE;
    }
    if ((o & ::android::hardware::audio::common::V2_0::AudioUsage::NOTIFICATION_COMMUNICATION_REQUEST) == static_cast<int32_t>(::android::hardware::audio::common::V2_0::AudioUsage::NOTIFICATION_COMMUNICATION_REQUEST)) {
        os += (first ? "" : " | ");
        os += "NOTIFICATION_COMMUNICATION_REQUEST";
        first = false;
        flipped |= ::android::hardware::audio::common::V2_0::AudioUsage::NOTIFICATION_COMMUNICATION_REQUEST;
    }
    if ((o & ::android::hardware::audio::common::V2_0::AudioUsage::NOTIFICATION_COMMUNICATION_INSTANT) == static_cast<int32_t>(::android::hardware::audio::common::V2_0::AudioUsage::NOTIFICATION_COMMUNICATION_INSTANT)) {
        os += (first ? "" : " | ");
        os += "NOTIFICATION_COMMUNICATION_INSTANT";
        first = false;
        flipped |= ::android::hardware::audio::common::V2_0::AudioUsage::NOTIFICATION_COMMUNICATION_INSTANT;
    }
    if ((o & ::android::hardware::audio::common::V2_0::AudioUsage::NOTIFICATION_COMMUNICATION_DELAYED) == static_cast<int32_t>(::android::hardware::audio::common::V2_0::AudioUsage::NOTIFICATION_COMMUNICATION_DELAYED)) {
        os += (first ? "" : " | ");
        os += "NOTIFICATION_COMMUNICATION_DELAYED";
        first = false;
        flipped |= ::android::hardware::audio::common::V2_0::AudioUsage::NOTIFICATION_COMMUNICATION_DELAYED;
    }
    if ((o & ::android::hardware::audio::common::V2_0::AudioUsage::NOTIFICATION_EVENT) == static_cast<int32_t>(::android::hardware::audio::common::V2_0::AudioUsage::NOTIFICATION_EVENT)) {
        os += (first ? "" : " | ");
        os += "NOTIFICATION_EVENT";
        first = false;
        flipped |= ::android::hardware::audio::common::V2_0::AudioUsage::NOTIFICATION_EVENT;
    }
    if ((o & ::android::hardware::audio::common::V2_0::AudioUsage::ASSISTANCE_ACCESSIBILITY) == static_cast<int32_t>(::android::hardware::audio::common::V2_0::AudioUsage::ASSISTANCE_ACCESSIBILITY)) {
        os += (first ? "" : " | ");
        os += "ASSISTANCE_ACCESSIBILITY";
        first = false;
        flipped |= ::android::hardware::audio::common::V2_0::AudioUsage::ASSISTANCE_ACCESSIBILITY;
    }
    if ((o & ::android::hardware::audio::common::V2_0::AudioUsage::ASSISTANCE_NAVIGATION_GUIDANCE) == static_cast<int32_t>(::android::hardware::audio::common::V2_0::AudioUsage::ASSISTANCE_NAVIGATION_GUIDANCE)) {
        os += (first ? "" : " | ");
        os += "ASSISTANCE_NAVIGATION_GUIDANCE";
        first = false;
        flipped |= ::android::hardware::audio::common::V2_0::AudioUsage::ASSISTANCE_NAVIGATION_GUIDANCE;
    }
    if ((o & ::android::hardware::audio::common::V2_0::AudioUsage::ASSISTANCE_SONIFICATION) == static_cast<int32_t>(::android::hardware::audio::common::V2_0::AudioUsage::ASSISTANCE_SONIFICATION)) {
        os += (first ? "" : " | ");
        os += "ASSISTANCE_SONIFICATION";
        first = false;
        flipped |= ::android::hardware::audio::common::V2_0::AudioUsage::ASSISTANCE_SONIFICATION;
    }
    if ((o & ::android::hardware::audio::common::V2_0::AudioUsage::GAME) == static_cast<int32_t>(::android::hardware::audio::common::V2_0::AudioUsage::GAME)) {
        os += (first ? "" : " | ");
        os += "GAME";
        first = false;
        flipped |= ::android::hardware::audio::common::V2_0::AudioUsage::GAME;
    }
    if ((o & ::android::hardware::audio::common::V2_0::AudioUsage::VIRTUAL_SOURCE) == static_cast<int32_t>(::android::hardware::audio::common::V2_0::AudioUsage::VIRTUAL_SOURCE)) {
        os += (first ? "" : " | ");
        os += "VIRTUAL_SOURCE";
        first = false;
        flipped |= ::android::hardware::audio::common::V2_0::AudioUsage::VIRTUAL_SOURCE;
    }
    if ((o & ::android::hardware::audio::common::V2_0::AudioUsage::ASSISTANT) == static_cast<int32_t>(::android::hardware::audio::common::V2_0::AudioUsage::ASSISTANT)) {
        os += (first ? "" : " | ");
        os += "ASSISTANT";
        first = false;
        flipped |= ::android::hardware::audio::common::V2_0::AudioUsage::ASSISTANT;
    }
    if ((o & ::android::hardware::audio::common::V2_0::AudioUsage::CNT) == static_cast<int32_t>(::android::hardware::audio::common::V2_0::AudioUsage::CNT)) {
        os += (first ? "" : " | ");
        os += "CNT";
        first = false;
        flipped |= ::android::hardware::audio::common::V2_0::AudioUsage::CNT;
    }
    if ((o & ::android::hardware::audio::common::V2_0::AudioUsage::MAX) == static_cast<int32_t>(::android::hardware::audio::common::V2_0::AudioUsage::MAX)) {
        os += (first ? "" : " | ");
        os += "MAX";
        first = false;
        flipped |= ::android::hardware::audio::common::V2_0::AudioUsage::MAX;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::audio::common::V2_0::AudioUsage o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::audio::common::V2_0::AudioUsage::UNKNOWN) {
        return "UNKNOWN";
    }
    if (o == ::android::hardware::audio::common::V2_0::AudioUsage::MEDIA) {
        return "MEDIA";
    }
    if (o == ::android::hardware::audio::common::V2_0::AudioUsage::VOICE_COMMUNICATION) {
        return "VOICE_COMMUNICATION";
    }
    if (o == ::android::hardware::audio::common::V2_0::AudioUsage::VOICE_COMMUNICATION_SIGNALLING) {
        return "VOICE_COMMUNICATION_SIGNALLING";
    }
    if (o == ::android::hardware::audio::common::V2_0::AudioUsage::ALARM) {
        return "ALARM";
    }
    if (o == ::android::hardware::audio::common::V2_0::AudioUsage::NOTIFICATION) {
        return "NOTIFICATION";
    }
    if (o == ::android::hardware::audio::common::V2_0::AudioUsage::NOTIFICATION_TELEPHONY_RINGTONE) {
        return "NOTIFICATION_TELEPHONY_RINGTONE";
    }
    if (o == ::android::hardware::audio::common::V2_0::AudioUsage::NOTIFICATION_COMMUNICATION_REQUEST) {
        return "NOTIFICATION_COMMUNICATION_REQUEST";
    }
    if (o == ::android::hardware::audio::common::V2_0::AudioUsage::NOTIFICATION_COMMUNICATION_INSTANT) {
        return "NOTIFICATION_COMMUNICATION_INSTANT";
    }
    if (o == ::android::hardware::audio::common::V2_0::AudioUsage::NOTIFICATION_COMMUNICATION_DELAYED) {
        return "NOTIFICATION_COMMUNICATION_DELAYED";
    }
    if (o == ::android::hardware::audio::common::V2_0::AudioUsage::NOTIFICATION_EVENT) {
        return "NOTIFICATION_EVENT";
    }
    if (o == ::android::hardware::audio::common::V2_0::AudioUsage::ASSISTANCE_ACCESSIBILITY) {
        return "ASSISTANCE_ACCESSIBILITY";
    }
    if (o == ::android::hardware::audio::common::V2_0::AudioUsage::ASSISTANCE_NAVIGATION_GUIDANCE) {
        return "ASSISTANCE_NAVIGATION_GUIDANCE";
    }
    if (o == ::android::hardware::audio::common::V2_0::AudioUsage::ASSISTANCE_SONIFICATION) {
        return "ASSISTANCE_SONIFICATION";
    }
    if (o == ::android::hardware::audio::common::V2_0::AudioUsage::GAME) {
        return "GAME";
    }
    if (o == ::android::hardware::audio::common::V2_0::AudioUsage::VIRTUAL_SOURCE) {
        return "VIRTUAL_SOURCE";
    }
    if (o == ::android::hardware::audio::common::V2_0::AudioUsage::ASSISTANT) {
        return "ASSISTANT";
    }
    if (o == ::android::hardware::audio::common::V2_0::AudioUsage::CNT) {
        return "CNT";
    }
    if (o == ::android::hardware::audio::common::V2_0::AudioUsage::MAX) {
        return "MAX";
    }
    std::string os;
    os += toHexString(static_cast<int32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::audio::common::V2_0::AudioUsage o, ::std::ostream* os) {
    *os << toString(o);
}

static inline std::string toString(const ::android::hardware::audio::common::V2_0::AudioOffloadInfo& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".sampleRateHz = ";
    os += ::android::hardware::toString(o.sampleRateHz);
    os += ", .channelMask = ";
    os += ::android::hardware::audio::common::V2_0::toString(o.channelMask);
    os += ", .format = ";
    os += ::android::hardware::audio::common::V2_0::toString(o.format);
    os += ", .streamType = ";
    os += ::android::hardware::audio::common::V2_0::toString(o.streamType);
    os += ", .bitRatePerSecond = ";
    os += ::android::hardware::toString(o.bitRatePerSecond);
    os += ", .durationMicroseconds = ";
    os += ::android::hardware::toString(o.durationMicroseconds);
    os += ", .hasVideo = ";
    os += ::android::hardware::toString(o.hasVideo);
    os += ", .isStreaming = ";
    os += ::android::hardware::toString(o.isStreaming);
    os += ", .bitWidth = ";
    os += ::android::hardware::toString(o.bitWidth);
    os += ", .bufferSize = ";
    os += ::android::hardware::toString(o.bufferSize);
    os += ", .usage = ";
    os += ::android::hardware::audio::common::V2_0::toString(o.usage);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::audio::common::V2_0::AudioOffloadInfo& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::audio::common::V2_0::AudioOffloadInfo& lhs, const ::android::hardware::audio::common::V2_0::AudioOffloadInfo& rhs) {
    if (lhs.sampleRateHz != rhs.sampleRateHz) {
        return false;
    }
    if (lhs.channelMask != rhs.channelMask) {
        return false;
    }
    if (lhs.format != rhs.format) {
        return false;
    }
    if (lhs.streamType != rhs.streamType) {
        return false;
    }
    if (lhs.bitRatePerSecond != rhs.bitRatePerSecond) {
        return false;
    }
    if (lhs.durationMicroseconds != rhs.durationMicroseconds) {
        return false;
    }
    if (lhs.hasVideo != rhs.hasVideo) {
        return false;
    }
    if (lhs.isStreaming != rhs.isStreaming) {
        return false;
    }
    if (lhs.bitWidth != rhs.bitWidth) {
        return false;
    }
    if (lhs.bufferSize != rhs.bufferSize) {
        return false;
    }
    if (lhs.usage != rhs.usage) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::audio::common::V2_0::AudioOffloadInfo& lhs, const ::android::hardware::audio::common::V2_0::AudioOffloadInfo& rhs){
    return !(lhs == rhs);
}

static inline std::string toString(const ::android::hardware::audio::common::V2_0::AudioConfig& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".sampleRateHz = ";
    os += ::android::hardware::toString(o.sampleRateHz);
    os += ", .channelMask = ";
    os += ::android::hardware::audio::common::V2_0::toString(o.channelMask);
    os += ", .format = ";
    os += ::android::hardware::audio::common::V2_0::toString(o.format);
    os += ", .offloadInfo = ";
    os += ::android::hardware::audio::common::V2_0::toString(o.offloadInfo);
    os += ", .frameCount = ";
    os += ::android::hardware::toString(o.frameCount);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::audio::common::V2_0::AudioConfig& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::audio::common::V2_0::AudioConfig& lhs, const ::android::hardware::audio::common::V2_0::AudioConfig& rhs) {
    if (lhs.sampleRateHz != rhs.sampleRateHz) {
        return false;
    }
    if (lhs.channelMask != rhs.channelMask) {
        return false;
    }
    if (lhs.format != rhs.format) {
        return false;
    }
    if (lhs.offloadInfo != rhs.offloadInfo) {
        return false;
    }
    if (lhs.frameCount != rhs.frameCount) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::audio::common::V2_0::AudioConfig& lhs, const ::android::hardware::audio::common::V2_0::AudioConfig& rhs){
    return !(lhs == rhs);
}

template<>
inline std::string toString<::android::hardware::audio::common::V2_0::AudioGainMode>(uint32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::audio::common::V2_0::AudioGainMode> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::audio::common::V2_0::AudioGainMode::JOINT) == static_cast<uint32_t>(::android::hardware::audio::common::V2_0::AudioGainMode::JOINT)) {
        os += (first ? "" : " | ");
        os += "JOINT";
        first = false;
        flipped |= ::android::hardware::audio::common::V2_0::AudioGainMode::JOINT;
    }
    if ((o & ::android::hardware::audio::common::V2_0::AudioGainMode::CHANNELS) == static_cast<uint32_t>(::android::hardware::audio::common::V2_0::AudioGainMode::CHANNELS)) {
        os += (first ? "" : " | ");
        os += "CHANNELS";
        first = false;
        flipped |= ::android::hardware::audio::common::V2_0::AudioGainMode::CHANNELS;
    }
    if ((o & ::android::hardware::audio::common::V2_0::AudioGainMode::RAMP) == static_cast<uint32_t>(::android::hardware::audio::common::V2_0::AudioGainMode::RAMP)) {
        os += (first ? "" : " | ");
        os += "RAMP";
        first = false;
        flipped |= ::android::hardware::audio::common::V2_0::AudioGainMode::RAMP;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::audio::common::V2_0::AudioGainMode o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::audio::common::V2_0::AudioGainMode::JOINT) {
        return "JOINT";
    }
    if (o == ::android::hardware::audio::common::V2_0::AudioGainMode::CHANNELS) {
        return "CHANNELS";
    }
    if (o == ::android::hardware::audio::common::V2_0::AudioGainMode::RAMP) {
        return "RAMP";
    }
    std::string os;
    os += toHexString(static_cast<uint32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::audio::common::V2_0::AudioGainMode o, ::std::ostream* os) {
    *os << toString(o);
}

static inline std::string toString(const ::android::hardware::audio::common::V2_0::AudioGain& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".mode = ";
    os += ::android::hardware::audio::common::V2_0::toString(o.mode);
    os += ", .channelMask = ";
    os += ::android::hardware::audio::common::V2_0::toString(o.channelMask);
    os += ", .minValue = ";
    os += ::android::hardware::toString(o.minValue);
    os += ", .maxValue = ";
    os += ::android::hardware::toString(o.maxValue);
    os += ", .defaultValue = ";
    os += ::android::hardware::toString(o.defaultValue);
    os += ", .stepValue = ";
    os += ::android::hardware::toString(o.stepValue);
    os += ", .minRampMs = ";
    os += ::android::hardware::toString(o.minRampMs);
    os += ", .maxRampMs = ";
    os += ::android::hardware::toString(o.maxRampMs);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::audio::common::V2_0::AudioGain& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::audio::common::V2_0::AudioGain& lhs, const ::android::hardware::audio::common::V2_0::AudioGain& rhs) {
    if (lhs.mode != rhs.mode) {
        return false;
    }
    if (lhs.channelMask != rhs.channelMask) {
        return false;
    }
    if (lhs.minValue != rhs.minValue) {
        return false;
    }
    if (lhs.maxValue != rhs.maxValue) {
        return false;
    }
    if (lhs.defaultValue != rhs.defaultValue) {
        return false;
    }
    if (lhs.stepValue != rhs.stepValue) {
        return false;
    }
    if (lhs.minRampMs != rhs.minRampMs) {
        return false;
    }
    if (lhs.maxRampMs != rhs.maxRampMs) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::audio::common::V2_0::AudioGain& lhs, const ::android::hardware::audio::common::V2_0::AudioGain& rhs){
    return !(lhs == rhs);
}

static inline std::string toString(const ::android::hardware::audio::common::V2_0::AudioGainConfig& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".index = ";
    os += ::android::hardware::toString(o.index);
    os += ", .mode = ";
    os += ::android::hardware::audio::common::V2_0::toString(o.mode);
    os += ", .channelMask = ";
    os += ::android::hardware::audio::common::V2_0::toString(o.channelMask);
    os += ", .values = ";
    os += ::android::hardware::toString(o.values);
    os += ", .rampDurationMs = ";
    os += ::android::hardware::toString(o.rampDurationMs);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::audio::common::V2_0::AudioGainConfig& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::audio::common::V2_0::AudioGainConfig& lhs, const ::android::hardware::audio::common::V2_0::AudioGainConfig& rhs) {
    if (lhs.index != rhs.index) {
        return false;
    }
    if (lhs.mode != rhs.mode) {
        return false;
    }
    if (lhs.channelMask != rhs.channelMask) {
        return false;
    }
    if (lhs.values != rhs.values) {
        return false;
    }
    if (lhs.rampDurationMs != rhs.rampDurationMs) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::audio::common::V2_0::AudioGainConfig& lhs, const ::android::hardware::audio::common::V2_0::AudioGainConfig& rhs){
    return !(lhs == rhs);
}

template<>
inline std::string toString<::android::hardware::audio::common::V2_0::AudioPortRole>(int32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::audio::common::V2_0::AudioPortRole> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::audio::common::V2_0::AudioPortRole::NONE) == static_cast<int32_t>(::android::hardware::audio::common::V2_0::AudioPortRole::NONE)) {
        os += (first ? "" : " | ");
        os += "NONE";
        first = false;
        flipped |= ::android::hardware::audio::common::V2_0::AudioPortRole::NONE;
    }
    if ((o & ::android::hardware::audio::common::V2_0::AudioPortRole::SOURCE) == static_cast<int32_t>(::android::hardware::audio::common::V2_0::AudioPortRole::SOURCE)) {
        os += (first ? "" : " | ");
        os += "SOURCE";
        first = false;
        flipped |= ::android::hardware::audio::common::V2_0::AudioPortRole::SOURCE;
    }
    if ((o & ::android::hardware::audio::common::V2_0::AudioPortRole::SINK) == static_cast<int32_t>(::android::hardware::audio::common::V2_0::AudioPortRole::SINK)) {
        os += (first ? "" : " | ");
        os += "SINK";
        first = false;
        flipped |= ::android::hardware::audio::common::V2_0::AudioPortRole::SINK;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::audio::common::V2_0::AudioPortRole o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::audio::common::V2_0::AudioPortRole::NONE) {
        return "NONE";
    }
    if (o == ::android::hardware::audio::common::V2_0::AudioPortRole::SOURCE) {
        return "SOURCE";
    }
    if (o == ::android::hardware::audio::common::V2_0::AudioPortRole::SINK) {
        return "SINK";
    }
    std::string os;
    os += toHexString(static_cast<int32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::audio::common::V2_0::AudioPortRole o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::audio::common::V2_0::AudioPortType>(int32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::audio::common::V2_0::AudioPortType> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::audio::common::V2_0::AudioPortType::NONE) == static_cast<int32_t>(::android::hardware::audio::common::V2_0::AudioPortType::NONE)) {
        os += (first ? "" : " | ");
        os += "NONE";
        first = false;
        flipped |= ::android::hardware::audio::common::V2_0::AudioPortType::NONE;
    }
    if ((o & ::android::hardware::audio::common::V2_0::AudioPortType::DEVICE) == static_cast<int32_t>(::android::hardware::audio::common::V2_0::AudioPortType::DEVICE)) {
        os += (first ? "" : " | ");
        os += "DEVICE";
        first = false;
        flipped |= ::android::hardware::audio::common::V2_0::AudioPortType::DEVICE;
    }
    if ((o & ::android::hardware::audio::common::V2_0::AudioPortType::MIX) == static_cast<int32_t>(::android::hardware::audio::common::V2_0::AudioPortType::MIX)) {
        os += (first ? "" : " | ");
        os += "MIX";
        first = false;
        flipped |= ::android::hardware::audio::common::V2_0::AudioPortType::MIX;
    }
    if ((o & ::android::hardware::audio::common::V2_0::AudioPortType::SESSION) == static_cast<int32_t>(::android::hardware::audio::common::V2_0::AudioPortType::SESSION)) {
        os += (first ? "" : " | ");
        os += "SESSION";
        first = false;
        flipped |= ::android::hardware::audio::common::V2_0::AudioPortType::SESSION;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::audio::common::V2_0::AudioPortType o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::audio::common::V2_0::AudioPortType::NONE) {
        return "NONE";
    }
    if (o == ::android::hardware::audio::common::V2_0::AudioPortType::DEVICE) {
        return "DEVICE";
    }
    if (o == ::android::hardware::audio::common::V2_0::AudioPortType::MIX) {
        return "MIX";
    }
    if (o == ::android::hardware::audio::common::V2_0::AudioPortType::SESSION) {
        return "SESSION";
    }
    std::string os;
    os += toHexString(static_cast<int32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::audio::common::V2_0::AudioPortType o, ::std::ostream* os) {
    *os << toString(o);
}

static inline std::string toString(const ::android::hardware::audio::common::V2_0::AudioPortConfigDeviceExt& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".hwModule = ";
    os += ::android::hardware::toString(o.hwModule);
    os += ", .type = ";
    os += ::android::hardware::audio::common::V2_0::toString(o.type);
    os += ", .address = ";
    os += ::android::hardware::toString(o.address);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::audio::common::V2_0::AudioPortConfigDeviceExt& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::audio::common::V2_0::AudioPortConfigDeviceExt& lhs, const ::android::hardware::audio::common::V2_0::AudioPortConfigDeviceExt& rhs) {
    if (lhs.hwModule != rhs.hwModule) {
        return false;
    }
    if (lhs.type != rhs.type) {
        return false;
    }
    if (lhs.address != rhs.address) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::audio::common::V2_0::AudioPortConfigDeviceExt& lhs, const ::android::hardware::audio::common::V2_0::AudioPortConfigDeviceExt& rhs){
    return !(lhs == rhs);
}

static inline std::string toString(const ::android::hardware::audio::common::V2_0::AudioPortConfigSessionExt& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".session = ";
    os += ::android::hardware::toString(o.session);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::audio::common::V2_0::AudioPortConfigSessionExt& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::audio::common::V2_0::AudioPortConfigSessionExt& lhs, const ::android::hardware::audio::common::V2_0::AudioPortConfigSessionExt& rhs) {
    if (lhs.session != rhs.session) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::audio::common::V2_0::AudioPortConfigSessionExt& lhs, const ::android::hardware::audio::common::V2_0::AudioPortConfigSessionExt& rhs){
    return !(lhs == rhs);
}

template<>
inline std::string toString<::android::hardware::audio::common::V2_0::AudioPortConfigMask>(uint32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::audio::common::V2_0::AudioPortConfigMask> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::audio::common::V2_0::AudioPortConfigMask::SAMPLE_RATE) == static_cast<uint32_t>(::android::hardware::audio::common::V2_0::AudioPortConfigMask::SAMPLE_RATE)) {
        os += (first ? "" : " | ");
        os += "SAMPLE_RATE";
        first = false;
        flipped |= ::android::hardware::audio::common::V2_0::AudioPortConfigMask::SAMPLE_RATE;
    }
    if ((o & ::android::hardware::audio::common::V2_0::AudioPortConfigMask::CHANNEL_MASK) == static_cast<uint32_t>(::android::hardware::audio::common::V2_0::AudioPortConfigMask::CHANNEL_MASK)) {
        os += (first ? "" : " | ");
        os += "CHANNEL_MASK";
        first = false;
        flipped |= ::android::hardware::audio::common::V2_0::AudioPortConfigMask::CHANNEL_MASK;
    }
    if ((o & ::android::hardware::audio::common::V2_0::AudioPortConfigMask::FORMAT) == static_cast<uint32_t>(::android::hardware::audio::common::V2_0::AudioPortConfigMask::FORMAT)) {
        os += (first ? "" : " | ");
        os += "FORMAT";
        first = false;
        flipped |= ::android::hardware::audio::common::V2_0::AudioPortConfigMask::FORMAT;
    }
    if ((o & ::android::hardware::audio::common::V2_0::AudioPortConfigMask::GAIN) == static_cast<uint32_t>(::android::hardware::audio::common::V2_0::AudioPortConfigMask::GAIN)) {
        os += (first ? "" : " | ");
        os += "GAIN";
        first = false;
        flipped |= ::android::hardware::audio::common::V2_0::AudioPortConfigMask::GAIN;
    }
    if ((o & ::android::hardware::audio::common::V2_0::AudioPortConfigMask::ALL) == static_cast<uint32_t>(::android::hardware::audio::common::V2_0::AudioPortConfigMask::ALL)) {
        os += (first ? "" : " | ");
        os += "ALL";
        first = false;
        flipped |= ::android::hardware::audio::common::V2_0::AudioPortConfigMask::ALL;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::audio::common::V2_0::AudioPortConfigMask o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::audio::common::V2_0::AudioPortConfigMask::SAMPLE_RATE) {
        return "SAMPLE_RATE";
    }
    if (o == ::android::hardware::audio::common::V2_0::AudioPortConfigMask::CHANNEL_MASK) {
        return "CHANNEL_MASK";
    }
    if (o == ::android::hardware::audio::common::V2_0::AudioPortConfigMask::FORMAT) {
        return "FORMAT";
    }
    if (o == ::android::hardware::audio::common::V2_0::AudioPortConfigMask::GAIN) {
        return "GAIN";
    }
    if (o == ::android::hardware::audio::common::V2_0::AudioPortConfigMask::ALL) {
        return "ALL";
    }
    std::string os;
    os += toHexString(static_cast<uint32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::audio::common::V2_0::AudioPortConfigMask o, ::std::ostream* os) {
    *os << toString(o);
}

static inline std::string toString(const ::android::hardware::audio::common::V2_0::AudioPortConfig::Ext::AudioPortConfigMixExt::UseCase& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".stream = ";
    os += ::android::hardware::audio::common::V2_0::toString(o.stream);
    os += ", .source = ";
    os += ::android::hardware::audio::common::V2_0::toString(o.source);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::audio::common::V2_0::AudioPortConfig::Ext::AudioPortConfigMixExt::UseCase& o, ::std::ostream* os) {
    *os << toString(o);
}

// operator== and operator!= are not generated for UseCase

static inline std::string toString(const ::android::hardware::audio::common::V2_0::AudioPortConfig::Ext::AudioPortConfigMixExt& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".hwModule = ";
    os += ::android::hardware::toString(o.hwModule);
    os += ", .ioHandle = ";
    os += ::android::hardware::toString(o.ioHandle);
    os += ", .useCase = ";
    os += ::android::hardware::audio::common::V2_0::toString(o.useCase);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::audio::common::V2_0::AudioPortConfig::Ext::AudioPortConfigMixExt& o, ::std::ostream* os) {
    *os << toString(o);
}

// operator== and operator!= are not generated for AudioPortConfigMixExt

static inline std::string toString(const ::android::hardware::audio::common::V2_0::AudioPortConfig::Ext& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".device = ";
    os += ::android::hardware::audio::common::V2_0::toString(o.device);
    os += ", .mix = ";
    os += ::android::hardware::audio::common::V2_0::toString(o.mix);
    os += ", .session = ";
    os += ::android::hardware::audio::common::V2_0::toString(o.session);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::audio::common::V2_0::AudioPortConfig::Ext& o, ::std::ostream* os) {
    *os << toString(o);
}

// operator== and operator!= are not generated for Ext

static inline std::string toString(const ::android::hardware::audio::common::V2_0::AudioPortConfig& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".id = ";
    os += ::android::hardware::toString(o.id);
    os += ", .configMask = ";
    os += ::android::hardware::audio::common::V2_0::toString(o.configMask);
    os += ", .sampleRateHz = ";
    os += ::android::hardware::toString(o.sampleRateHz);
    os += ", .channelMask = ";
    os += ::android::hardware::audio::common::V2_0::toString(o.channelMask);
    os += ", .format = ";
    os += ::android::hardware::audio::common::V2_0::toString(o.format);
    os += ", .gain = ";
    os += ::android::hardware::audio::common::V2_0::toString(o.gain);
    os += ", .type = ";
    os += ::android::hardware::audio::common::V2_0::toString(o.type);
    os += ", .role = ";
    os += ::android::hardware::audio::common::V2_0::toString(o.role);
    os += ", .ext = ";
    os += ::android::hardware::audio::common::V2_0::toString(o.ext);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::audio::common::V2_0::AudioPortConfig& o, ::std::ostream* os) {
    *os << toString(o);
}

// operator== and operator!= are not generated for AudioPortConfig

static inline std::string toString(const ::android::hardware::audio::common::V2_0::AudioPortDeviceExt& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".hwModule = ";
    os += ::android::hardware::toString(o.hwModule);
    os += ", .type = ";
    os += ::android::hardware::audio::common::V2_0::toString(o.type);
    os += ", .address = ";
    os += ::android::hardware::toString(o.address);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::audio::common::V2_0::AudioPortDeviceExt& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::audio::common::V2_0::AudioPortDeviceExt& lhs, const ::android::hardware::audio::common::V2_0::AudioPortDeviceExt& rhs) {
    if (lhs.hwModule != rhs.hwModule) {
        return false;
    }
    if (lhs.type != rhs.type) {
        return false;
    }
    if (lhs.address != rhs.address) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::audio::common::V2_0::AudioPortDeviceExt& lhs, const ::android::hardware::audio::common::V2_0::AudioPortDeviceExt& rhs){
    return !(lhs == rhs);
}

template<>
inline std::string toString<::android::hardware::audio::common::V2_0::AudioMixLatencyClass>(int32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::audio::common::V2_0::AudioMixLatencyClass> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::audio::common::V2_0::AudioMixLatencyClass::LOW) == static_cast<int32_t>(::android::hardware::audio::common::V2_0::AudioMixLatencyClass::LOW)) {
        os += (first ? "" : " | ");
        os += "LOW";
        first = false;
        flipped |= ::android::hardware::audio::common::V2_0::AudioMixLatencyClass::LOW;
    }
    if ((o & ::android::hardware::audio::common::V2_0::AudioMixLatencyClass::NORMAL) == static_cast<int32_t>(::android::hardware::audio::common::V2_0::AudioMixLatencyClass::NORMAL)) {
        os += (first ? "" : " | ");
        os += "NORMAL";
        first = false;
        flipped |= ::android::hardware::audio::common::V2_0::AudioMixLatencyClass::NORMAL;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::audio::common::V2_0::AudioMixLatencyClass o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::audio::common::V2_0::AudioMixLatencyClass::LOW) {
        return "LOW";
    }
    if (o == ::android::hardware::audio::common::V2_0::AudioMixLatencyClass::NORMAL) {
        return "NORMAL";
    }
    std::string os;
    os += toHexString(static_cast<int32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::audio::common::V2_0::AudioMixLatencyClass o, ::std::ostream* os) {
    *os << toString(o);
}

static inline std::string toString(const ::android::hardware::audio::common::V2_0::AudioPortMixExt& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".hwModule = ";
    os += ::android::hardware::toString(o.hwModule);
    os += ", .ioHandle = ";
    os += ::android::hardware::toString(o.ioHandle);
    os += ", .latencyClass = ";
    os += ::android::hardware::audio::common::V2_0::toString(o.latencyClass);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::audio::common::V2_0::AudioPortMixExt& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::audio::common::V2_0::AudioPortMixExt& lhs, const ::android::hardware::audio::common::V2_0::AudioPortMixExt& rhs) {
    if (lhs.hwModule != rhs.hwModule) {
        return false;
    }
    if (lhs.ioHandle != rhs.ioHandle) {
        return false;
    }
    if (lhs.latencyClass != rhs.latencyClass) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::audio::common::V2_0::AudioPortMixExt& lhs, const ::android::hardware::audio::common::V2_0::AudioPortMixExt& rhs){
    return !(lhs == rhs);
}

static inline std::string toString(const ::android::hardware::audio::common::V2_0::AudioPortSessionExt& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".session = ";
    os += ::android::hardware::toString(o.session);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::audio::common::V2_0::AudioPortSessionExt& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::audio::common::V2_0::AudioPortSessionExt& lhs, const ::android::hardware::audio::common::V2_0::AudioPortSessionExt& rhs) {
    if (lhs.session != rhs.session) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::audio::common::V2_0::AudioPortSessionExt& lhs, const ::android::hardware::audio::common::V2_0::AudioPortSessionExt& rhs){
    return !(lhs == rhs);
}

static inline std::string toString(const ::android::hardware::audio::common::V2_0::AudioPort::Ext& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".device = ";
    os += ::android::hardware::audio::common::V2_0::toString(o.device);
    os += ", .mix = ";
    os += ::android::hardware::audio::common::V2_0::toString(o.mix);
    os += ", .session = ";
    os += ::android::hardware::audio::common::V2_0::toString(o.session);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::audio::common::V2_0::AudioPort::Ext& o, ::std::ostream* os) {
    *os << toString(o);
}

// operator== and operator!= are not generated for Ext

static inline std::string toString(const ::android::hardware::audio::common::V2_0::AudioPort& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".id = ";
    os += ::android::hardware::toString(o.id);
    os += ", .role = ";
    os += ::android::hardware::audio::common::V2_0::toString(o.role);
    os += ", .name = ";
    os += ::android::hardware::toString(o.name);
    os += ", .sampleRates = ";
    os += ::android::hardware::toString(o.sampleRates);
    os += ", .channelMasks = ";
    os += ::android::hardware::toString(o.channelMasks);
    os += ", .formats = ";
    os += ::android::hardware::toString(o.formats);
    os += ", .gains = ";
    os += ::android::hardware::toString(o.gains);
    os += ", .activeConfig = ";
    os += ::android::hardware::audio::common::V2_0::toString(o.activeConfig);
    os += ", .type = ";
    os += ::android::hardware::audio::common::V2_0::toString(o.type);
    os += ", .ext = ";
    os += ::android::hardware::audio::common::V2_0::toString(o.ext);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::audio::common::V2_0::AudioPort& o, ::std::ostream* os) {
    *os << toString(o);
}

// operator== and operator!= are not generated for AudioPort

static inline std::string toString(const ::android::hardware::audio::common::V2_0::ThreadInfo& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".pid = ";
    os += ::android::hardware::toString(o.pid);
    os += ", .tid = ";
    os += ::android::hardware::toString(o.tid);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::audio::common::V2_0::ThreadInfo& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::audio::common::V2_0::ThreadInfo& lhs, const ::android::hardware::audio::common::V2_0::ThreadInfo& rhs) {
    if (lhs.pid != rhs.pid) {
        return false;
    }
    if (lhs.tid != rhs.tid) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::audio::common::V2_0::ThreadInfo& lhs, const ::android::hardware::audio::common::V2_0::ThreadInfo& rhs){
    return !(lhs == rhs);
}


}  // namespace V2_0
}  // namespace common
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
template<> inline constexpr std::array<::android::hardware::audio::common::V2_0::AudioHandleConsts, 4> hidl_enum_values<::android::hardware::audio::common::V2_0::AudioHandleConsts> = {
    ::android::hardware::audio::common::V2_0::AudioHandleConsts::AUDIO_IO_HANDLE_NONE,
    ::android::hardware::audio::common::V2_0::AudioHandleConsts::AUDIO_MODULE_HANDLE_NONE,
    ::android::hardware::audio::common::V2_0::AudioHandleConsts::AUDIO_PORT_HANDLE_NONE,
    ::android::hardware::audio::common::V2_0::AudioHandleConsts::AUDIO_PATCH_HANDLE_NONE,
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
template<> inline constexpr std::array<::android::hardware::audio::common::V2_0::AudioStreamType, 18> hidl_enum_values<::android::hardware::audio::common::V2_0::AudioStreamType> = {
    ::android::hardware::audio::common::V2_0::AudioStreamType::DEFAULT,
    ::android::hardware::audio::common::V2_0::AudioStreamType::MIN,
    ::android::hardware::audio::common::V2_0::AudioStreamType::VOICE_CALL,
    ::android::hardware::audio::common::V2_0::AudioStreamType::SYSTEM,
    ::android::hardware::audio::common::V2_0::AudioStreamType::RING,
    ::android::hardware::audio::common::V2_0::AudioStreamType::MUSIC,
    ::android::hardware::audio::common::V2_0::AudioStreamType::ALARM,
    ::android::hardware::audio::common::V2_0::AudioStreamType::NOTIFICATION,
    ::android::hardware::audio::common::V2_0::AudioStreamType::BLUETOOTH_SCO,
    ::android::hardware::audio::common::V2_0::AudioStreamType::ENFORCED_AUDIBLE,
    ::android::hardware::audio::common::V2_0::AudioStreamType::DTMF,
    ::android::hardware::audio::common::V2_0::AudioStreamType::TTS,
    ::android::hardware::audio::common::V2_0::AudioStreamType::ACCESSIBILITY,
    ::android::hardware::audio::common::V2_0::AudioStreamType::REROUTING,
    ::android::hardware::audio::common::V2_0::AudioStreamType::PATCH,
    ::android::hardware::audio::common::V2_0::AudioStreamType::PUBLIC_CNT,
    ::android::hardware::audio::common::V2_0::AudioStreamType::FOR_POLICY_CNT,
    ::android::hardware::audio::common::V2_0::AudioStreamType::CNT,
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
template<> inline constexpr std::array<::android::hardware::audio::common::V2_0::AudioSource, 14> hidl_enum_values<::android::hardware::audio::common::V2_0::AudioSource> = {
    ::android::hardware::audio::common::V2_0::AudioSource::DEFAULT,
    ::android::hardware::audio::common::V2_0::AudioSource::MIC,
    ::android::hardware::audio::common::V2_0::AudioSource::VOICE_UPLINK,
    ::android::hardware::audio::common::V2_0::AudioSource::VOICE_DOWNLINK,
    ::android::hardware::audio::common::V2_0::AudioSource::VOICE_CALL,
    ::android::hardware::audio::common::V2_0::AudioSource::CAMCORDER,
    ::android::hardware::audio::common::V2_0::AudioSource::VOICE_RECOGNITION,
    ::android::hardware::audio::common::V2_0::AudioSource::VOICE_COMMUNICATION,
    ::android::hardware::audio::common::V2_0::AudioSource::REMOTE_SUBMIX,
    ::android::hardware::audio::common::V2_0::AudioSource::UNPROCESSED,
    ::android::hardware::audio::common::V2_0::AudioSource::CNT,
    ::android::hardware::audio::common::V2_0::AudioSource::MAX,
    ::android::hardware::audio::common::V2_0::AudioSource::FM_TUNER,
    ::android::hardware::audio::common::V2_0::AudioSource::HOTWORD,
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
template<> inline constexpr std::array<::android::hardware::audio::common::V2_0::AudioSessionConsts, 4> hidl_enum_values<::android::hardware::audio::common::V2_0::AudioSessionConsts> = {
    ::android::hardware::audio::common::V2_0::AudioSessionConsts::OUTPUT_STAGE,
    ::android::hardware::audio::common::V2_0::AudioSessionConsts::OUTPUT_MIX,
    ::android::hardware::audio::common::V2_0::AudioSessionConsts::ALLOCATE,
    ::android::hardware::audio::common::V2_0::AudioSessionConsts::NONE,
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
template<> inline constexpr std::array<::android::hardware::audio::common::V2_0::AudioFormat, 84> hidl_enum_values<::android::hardware::audio::common::V2_0::AudioFormat> = {
    ::android::hardware::audio::common::V2_0::AudioFormat::INVALID,
    ::android::hardware::audio::common::V2_0::AudioFormat::DEFAULT,
    ::android::hardware::audio::common::V2_0::AudioFormat::PCM,
    ::android::hardware::audio::common::V2_0::AudioFormat::MP3,
    ::android::hardware::audio::common::V2_0::AudioFormat::AMR_NB,
    ::android::hardware::audio::common::V2_0::AudioFormat::AMR_WB,
    ::android::hardware::audio::common::V2_0::AudioFormat::AAC,
    ::android::hardware::audio::common::V2_0::AudioFormat::HE_AAC_V1,
    ::android::hardware::audio::common::V2_0::AudioFormat::HE_AAC_V2,
    ::android::hardware::audio::common::V2_0::AudioFormat::VORBIS,
    ::android::hardware::audio::common::V2_0::AudioFormat::OPUS,
    ::android::hardware::audio::common::V2_0::AudioFormat::AC3,
    ::android::hardware::audio::common::V2_0::AudioFormat::E_AC3,
    ::android::hardware::audio::common::V2_0::AudioFormat::DTS,
    ::android::hardware::audio::common::V2_0::AudioFormat::DTS_HD,
    ::android::hardware::audio::common::V2_0::AudioFormat::IEC61937,
    ::android::hardware::audio::common::V2_0::AudioFormat::DOLBY_TRUEHD,
    ::android::hardware::audio::common::V2_0::AudioFormat::EVRC,
    ::android::hardware::audio::common::V2_0::AudioFormat::EVRCB,
    ::android::hardware::audio::common::V2_0::AudioFormat::EVRCWB,
    ::android::hardware::audio::common::V2_0::AudioFormat::EVRCNW,
    ::android::hardware::audio::common::V2_0::AudioFormat::AAC_ADIF,
    ::android::hardware::audio::common::V2_0::AudioFormat::WMA,
    ::android::hardware::audio::common::V2_0::AudioFormat::WMA_PRO,
    ::android::hardware::audio::common::V2_0::AudioFormat::AMR_WB_PLUS,
    ::android::hardware::audio::common::V2_0::AudioFormat::MP2,
    ::android::hardware::audio::common::V2_0::AudioFormat::QCELP,
    ::android::hardware::audio::common::V2_0::AudioFormat::DSD,
    ::android::hardware::audio::common::V2_0::AudioFormat::FLAC,
    ::android::hardware::audio::common::V2_0::AudioFormat::ALAC,
    ::android::hardware::audio::common::V2_0::AudioFormat::APE,
    ::android::hardware::audio::common::V2_0::AudioFormat::AAC_ADTS,
    ::android::hardware::audio::common::V2_0::AudioFormat::SBC,
    ::android::hardware::audio::common::V2_0::AudioFormat::APTX,
    ::android::hardware::audio::common::V2_0::AudioFormat::APTX_HD,
    ::android::hardware::audio::common::V2_0::AudioFormat::AC4,
    ::android::hardware::audio::common::V2_0::AudioFormat::LDAC,
    ::android::hardware::audio::common::V2_0::AudioFormat::MAIN_MASK,
    ::android::hardware::audio::common::V2_0::AudioFormat::SUB_MASK,
    ::android::hardware::audio::common::V2_0::AudioFormat::PCM_SUB_16_BIT,
    ::android::hardware::audio::common::V2_0::AudioFormat::PCM_SUB_8_BIT,
    ::android::hardware::audio::common::V2_0::AudioFormat::PCM_SUB_32_BIT,
    ::android::hardware::audio::common::V2_0::AudioFormat::PCM_SUB_8_24_BIT,
    ::android::hardware::audio::common::V2_0::AudioFormat::PCM_SUB_FLOAT,
    ::android::hardware::audio::common::V2_0::AudioFormat::PCM_SUB_24_BIT_PACKED,
    ::android::hardware::audio::common::V2_0::AudioFormat::MP3_SUB_NONE,
    ::android::hardware::audio::common::V2_0::AudioFormat::AMR_SUB_NONE,
    ::android::hardware::audio::common::V2_0::AudioFormat::AAC_SUB_MAIN,
    ::android::hardware::audio::common::V2_0::AudioFormat::AAC_SUB_LC,
    ::android::hardware::audio::common::V2_0::AudioFormat::AAC_SUB_SSR,
    ::android::hardware::audio::common::V2_0::AudioFormat::AAC_SUB_LTP,
    ::android::hardware::audio::common::V2_0::AudioFormat::AAC_SUB_HE_V1,
    ::android::hardware::audio::common::V2_0::AudioFormat::AAC_SUB_SCALABLE,
    ::android::hardware::audio::common::V2_0::AudioFormat::AAC_SUB_ERLC,
    ::android::hardware::audio::common::V2_0::AudioFormat::AAC_SUB_LD,
    ::android::hardware::audio::common::V2_0::AudioFormat::AAC_SUB_HE_V2,
    ::android::hardware::audio::common::V2_0::AudioFormat::AAC_SUB_ELD,
    ::android::hardware::audio::common::V2_0::AudioFormat::VORBIS_SUB_NONE,
    ::android::hardware::audio::common::V2_0::AudioFormat::PCM_16_BIT,
    ::android::hardware::audio::common::V2_0::AudioFormat::PCM_8_BIT,
    ::android::hardware::audio::common::V2_0::AudioFormat::PCM_32_BIT,
    ::android::hardware::audio::common::V2_0::AudioFormat::PCM_8_24_BIT,
    ::android::hardware::audio::common::V2_0::AudioFormat::PCM_FLOAT,
    ::android::hardware::audio::common::V2_0::AudioFormat::PCM_24_BIT_PACKED,
    ::android::hardware::audio::common::V2_0::AudioFormat::AAC_MAIN,
    ::android::hardware::audio::common::V2_0::AudioFormat::AAC_LC,
    ::android::hardware::audio::common::V2_0::AudioFormat::AAC_SSR,
    ::android::hardware::audio::common::V2_0::AudioFormat::AAC_LTP,
    ::android::hardware::audio::common::V2_0::AudioFormat::AAC_HE_V1,
    ::android::hardware::audio::common::V2_0::AudioFormat::AAC_SCALABLE,
    ::android::hardware::audio::common::V2_0::AudioFormat::AAC_ERLC,
    ::android::hardware::audio::common::V2_0::AudioFormat::AAC_LD,
    ::android::hardware::audio::common::V2_0::AudioFormat::AAC_HE_V2,
    ::android::hardware::audio::common::V2_0::AudioFormat::AAC_ELD,
    ::android::hardware::audio::common::V2_0::AudioFormat::AAC_ADTS_MAIN,
    ::android::hardware::audio::common::V2_0::AudioFormat::AAC_ADTS_LC,
    ::android::hardware::audio::common::V2_0::AudioFormat::AAC_ADTS_SSR,
    ::android::hardware::audio::common::V2_0::AudioFormat::AAC_ADTS_LTP,
    ::android::hardware::audio::common::V2_0::AudioFormat::AAC_ADTS_HE_V1,
    ::android::hardware::audio::common::V2_0::AudioFormat::AAC_ADTS_SCALABLE,
    ::android::hardware::audio::common::V2_0::AudioFormat::AAC_ADTS_ERLC,
    ::android::hardware::audio::common::V2_0::AudioFormat::AAC_ADTS_LD,
    ::android::hardware::audio::common::V2_0::AudioFormat::AAC_ADTS_HE_V2,
    ::android::hardware::audio::common::V2_0::AudioFormat::AAC_ADTS_ELD,
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
template<> inline constexpr std::array<::android::hardware::audio::common::V2_0::FixedChannelCount, 2> hidl_enum_values<::android::hardware::audio::common::V2_0::FixedChannelCount> = {
    ::android::hardware::audio::common::V2_0::FixedChannelCount::FCC_2,
    ::android::hardware::audio::common::V2_0::FixedChannelCount::FCC_8,
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
template<> inline constexpr std::array<::android::hardware::audio::common::V2_0::AudioChannelMask, 68> hidl_enum_values<::android::hardware::audio::common::V2_0::AudioChannelMask> = {
    ::android::hardware::audio::common::V2_0::AudioChannelMask::REPRESENTATION_POSITION,
    ::android::hardware::audio::common::V2_0::AudioChannelMask::REPRESENTATION_INDEX,
    ::android::hardware::audio::common::V2_0::AudioChannelMask::NONE,
    ::android::hardware::audio::common::V2_0::AudioChannelMask::INVALID,
    ::android::hardware::audio::common::V2_0::AudioChannelMask::OUT_FRONT_LEFT,
    ::android::hardware::audio::common::V2_0::AudioChannelMask::OUT_FRONT_RIGHT,
    ::android::hardware::audio::common::V2_0::AudioChannelMask::OUT_FRONT_CENTER,
    ::android::hardware::audio::common::V2_0::AudioChannelMask::OUT_LOW_FREQUENCY,
    ::android::hardware::audio::common::V2_0::AudioChannelMask::OUT_BACK_LEFT,
    ::android::hardware::audio::common::V2_0::AudioChannelMask::OUT_BACK_RIGHT,
    ::android::hardware::audio::common::V2_0::AudioChannelMask::OUT_FRONT_LEFT_OF_CENTER,
    ::android::hardware::audio::common::V2_0::AudioChannelMask::OUT_FRONT_RIGHT_OF_CENTER,
    ::android::hardware::audio::common::V2_0::AudioChannelMask::OUT_BACK_CENTER,
    ::android::hardware::audio::common::V2_0::AudioChannelMask::OUT_SIDE_LEFT,
    ::android::hardware::audio::common::V2_0::AudioChannelMask::OUT_SIDE_RIGHT,
    ::android::hardware::audio::common::V2_0::AudioChannelMask::OUT_TOP_CENTER,
    ::android::hardware::audio::common::V2_0::AudioChannelMask::OUT_TOP_FRONT_LEFT,
    ::android::hardware::audio::common::V2_0::AudioChannelMask::OUT_TOP_FRONT_CENTER,
    ::android::hardware::audio::common::V2_0::AudioChannelMask::OUT_TOP_FRONT_RIGHT,
    ::android::hardware::audio::common::V2_0::AudioChannelMask::OUT_TOP_BACK_LEFT,
    ::android::hardware::audio::common::V2_0::AudioChannelMask::OUT_TOP_BACK_CENTER,
    ::android::hardware::audio::common::V2_0::AudioChannelMask::OUT_TOP_BACK_RIGHT,
    ::android::hardware::audio::common::V2_0::AudioChannelMask::OUT_MONO,
    ::android::hardware::audio::common::V2_0::AudioChannelMask::OUT_STEREO,
    ::android::hardware::audio::common::V2_0::AudioChannelMask::OUT_2POINT1,
    ::android::hardware::audio::common::V2_0::AudioChannelMask::OUT_QUAD,
    ::android::hardware::audio::common::V2_0::AudioChannelMask::OUT_QUAD_BACK,
    ::android::hardware::audio::common::V2_0::AudioChannelMask::OUT_QUAD_SIDE,
    ::android::hardware::audio::common::V2_0::AudioChannelMask::OUT_SURROUND,
    ::android::hardware::audio::common::V2_0::AudioChannelMask::OUT_PENTA,
    ::android::hardware::audio::common::V2_0::AudioChannelMask::OUT_5POINT1,
    ::android::hardware::audio::common::V2_0::AudioChannelMask::OUT_5POINT1_BACK,
    ::android::hardware::audio::common::V2_0::AudioChannelMask::OUT_5POINT1_SIDE,
    ::android::hardware::audio::common::V2_0::AudioChannelMask::OUT_6POINT1,
    ::android::hardware::audio::common::V2_0::AudioChannelMask::OUT_7POINT1,
    ::android::hardware::audio::common::V2_0::AudioChannelMask::OUT_ALL,
    ::android::hardware::audio::common::V2_0::AudioChannelMask::IN_LEFT,
    ::android::hardware::audio::common::V2_0::AudioChannelMask::IN_RIGHT,
    ::android::hardware::audio::common::V2_0::AudioChannelMask::IN_FRONT,
    ::android::hardware::audio::common::V2_0::AudioChannelMask::IN_BACK,
    ::android::hardware::audio::common::V2_0::AudioChannelMask::IN_LEFT_PROCESSED,
    ::android::hardware::audio::common::V2_0::AudioChannelMask::IN_RIGHT_PROCESSED,
    ::android::hardware::audio::common::V2_0::AudioChannelMask::IN_FRONT_PROCESSED,
    ::android::hardware::audio::common::V2_0::AudioChannelMask::IN_BACK_PROCESSED,
    ::android::hardware::audio::common::V2_0::AudioChannelMask::IN_PRESSURE,
    ::android::hardware::audio::common::V2_0::AudioChannelMask::IN_X_AXIS,
    ::android::hardware::audio::common::V2_0::AudioChannelMask::IN_Y_AXIS,
    ::android::hardware::audio::common::V2_0::AudioChannelMask::IN_Z_AXIS,
    ::android::hardware::audio::common::V2_0::AudioChannelMask::IN_VOICE_UPLINK,
    ::android::hardware::audio::common::V2_0::AudioChannelMask::IN_VOICE_DNLINK,
    ::android::hardware::audio::common::V2_0::AudioChannelMask::IN_MONO,
    ::android::hardware::audio::common::V2_0::AudioChannelMask::IN_STEREO,
    ::android::hardware::audio::common::V2_0::AudioChannelMask::IN_FRONT_BACK,
    ::android::hardware::audio::common::V2_0::AudioChannelMask::IN_6,
    ::android::hardware::audio::common::V2_0::AudioChannelMask::IN_VOICE_UPLINK_MONO,
    ::android::hardware::audio::common::V2_0::AudioChannelMask::IN_VOICE_DNLINK_MONO,
    ::android::hardware::audio::common::V2_0::AudioChannelMask::IN_VOICE_CALL_MONO,
    ::android::hardware::audio::common::V2_0::AudioChannelMask::IN_ALL,
    ::android::hardware::audio::common::V2_0::AudioChannelMask::COUNT_MAX,
    ::android::hardware::audio::common::V2_0::AudioChannelMask::INDEX_HDR,
    ::android::hardware::audio::common::V2_0::AudioChannelMask::INDEX_MASK_1,
    ::android::hardware::audio::common::V2_0::AudioChannelMask::INDEX_MASK_2,
    ::android::hardware::audio::common::V2_0::AudioChannelMask::INDEX_MASK_3,
    ::android::hardware::audio::common::V2_0::AudioChannelMask::INDEX_MASK_4,
    ::android::hardware::audio::common::V2_0::AudioChannelMask::INDEX_MASK_5,
    ::android::hardware::audio::common::V2_0::AudioChannelMask::INDEX_MASK_6,
    ::android::hardware::audio::common::V2_0::AudioChannelMask::INDEX_MASK_7,
    ::android::hardware::audio::common::V2_0::AudioChannelMask::INDEX_MASK_8,
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
template<> inline constexpr std::array<::android::hardware::audio::common::V2_0::AudioInterleave, 2> hidl_enum_values<::android::hardware::audio::common::V2_0::AudioInterleave> = {
    ::android::hardware::audio::common::V2_0::AudioInterleave::LEFT,
    ::android::hardware::audio::common::V2_0::AudioInterleave::RIGHT,
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
template<> inline constexpr std::array<::android::hardware::audio::common::V2_0::AudioMode, 8> hidl_enum_values<::android::hardware::audio::common::V2_0::AudioMode> = {
    ::android::hardware::audio::common::V2_0::AudioMode::INVALID,
    ::android::hardware::audio::common::V2_0::AudioMode::CURRENT,
    ::android::hardware::audio::common::V2_0::AudioMode::NORMAL,
    ::android::hardware::audio::common::V2_0::AudioMode::RINGTONE,
    ::android::hardware::audio::common::V2_0::AudioMode::IN_CALL,
    ::android::hardware::audio::common::V2_0::AudioMode::IN_COMMUNICATION,
    ::android::hardware::audio::common::V2_0::AudioMode::CNT,
    ::android::hardware::audio::common::V2_0::AudioMode::MAX,
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
template<> inline constexpr std::array<::android::hardware::audio::common::V2_0::AudioDevice, 65> hidl_enum_values<::android::hardware::audio::common::V2_0::AudioDevice> = {
    ::android::hardware::audio::common::V2_0::AudioDevice::NONE,
    ::android::hardware::audio::common::V2_0::AudioDevice::BIT_IN,
    ::android::hardware::audio::common::V2_0::AudioDevice::BIT_DEFAULT,
    ::android::hardware::audio::common::V2_0::AudioDevice::OUT_EARPIECE,
    ::android::hardware::audio::common::V2_0::AudioDevice::OUT_SPEAKER,
    ::android::hardware::audio::common::V2_0::AudioDevice::OUT_WIRED_HEADSET,
    ::android::hardware::audio::common::V2_0::AudioDevice::OUT_WIRED_HEADPHONE,
    ::android::hardware::audio::common::V2_0::AudioDevice::OUT_BLUETOOTH_SCO,
    ::android::hardware::audio::common::V2_0::AudioDevice::OUT_BLUETOOTH_SCO_HEADSET,
    ::android::hardware::audio::common::V2_0::AudioDevice::OUT_BLUETOOTH_SCO_CARKIT,
    ::android::hardware::audio::common::V2_0::AudioDevice::OUT_BLUETOOTH_A2DP,
    ::android::hardware::audio::common::V2_0::AudioDevice::OUT_BLUETOOTH_A2DP_HEADPHONES,
    ::android::hardware::audio::common::V2_0::AudioDevice::OUT_BLUETOOTH_A2DP_SPEAKER,
    ::android::hardware::audio::common::V2_0::AudioDevice::OUT_AUX_DIGITAL,
    ::android::hardware::audio::common::V2_0::AudioDevice::OUT_HDMI,
    ::android::hardware::audio::common::V2_0::AudioDevice::OUT_ANLG_DOCK_HEADSET,
    ::android::hardware::audio::common::V2_0::AudioDevice::OUT_DGTL_DOCK_HEADSET,
    ::android::hardware::audio::common::V2_0::AudioDevice::OUT_USB_ACCESSORY,
    ::android::hardware::audio::common::V2_0::AudioDevice::OUT_USB_DEVICE,
    ::android::hardware::audio::common::V2_0::AudioDevice::OUT_REMOTE_SUBMIX,
    ::android::hardware::audio::common::V2_0::AudioDevice::OUT_TELEPHONY_TX,
    ::android::hardware::audio::common::V2_0::AudioDevice::OUT_LINE,
    ::android::hardware::audio::common::V2_0::AudioDevice::OUT_HDMI_ARC,
    ::android::hardware::audio::common::V2_0::AudioDevice::OUT_SPDIF,
    ::android::hardware::audio::common::V2_0::AudioDevice::OUT_FM,
    ::android::hardware::audio::common::V2_0::AudioDevice::OUT_AUX_LINE,
    ::android::hardware::audio::common::V2_0::AudioDevice::OUT_SPEAKER_SAFE,
    ::android::hardware::audio::common::V2_0::AudioDevice::OUT_IP,
    ::android::hardware::audio::common::V2_0::AudioDevice::OUT_BUS,
    ::android::hardware::audio::common::V2_0::AudioDevice::OUT_PROXY,
    ::android::hardware::audio::common::V2_0::AudioDevice::OUT_USB_HEADSET,
    ::android::hardware::audio::common::V2_0::AudioDevice::OUT_DEFAULT,
    ::android::hardware::audio::common::V2_0::AudioDevice::OUT_ALL,
    ::android::hardware::audio::common::V2_0::AudioDevice::OUT_ALL_A2DP,
    ::android::hardware::audio::common::V2_0::AudioDevice::OUT_ALL_SCO,
    ::android::hardware::audio::common::V2_0::AudioDevice::OUT_ALL_USB,
    ::android::hardware::audio::common::V2_0::AudioDevice::IN_COMMUNICATION,
    ::android::hardware::audio::common::V2_0::AudioDevice::IN_AMBIENT,
    ::android::hardware::audio::common::V2_0::AudioDevice::IN_BUILTIN_MIC,
    ::android::hardware::audio::common::V2_0::AudioDevice::IN_BLUETOOTH_SCO_HEADSET,
    ::android::hardware::audio::common::V2_0::AudioDevice::IN_WIRED_HEADSET,
    ::android::hardware::audio::common::V2_0::AudioDevice::IN_AUX_DIGITAL,
    ::android::hardware::audio::common::V2_0::AudioDevice::IN_HDMI,
    ::android::hardware::audio::common::V2_0::AudioDevice::IN_VOICE_CALL,
    ::android::hardware::audio::common::V2_0::AudioDevice::IN_TELEPHONY_RX,
    ::android::hardware::audio::common::V2_0::AudioDevice::IN_BACK_MIC,
    ::android::hardware::audio::common::V2_0::AudioDevice::IN_REMOTE_SUBMIX,
    ::android::hardware::audio::common::V2_0::AudioDevice::IN_ANLG_DOCK_HEADSET,
    ::android::hardware::audio::common::V2_0::AudioDevice::IN_DGTL_DOCK_HEADSET,
    ::android::hardware::audio::common::V2_0::AudioDevice::IN_USB_ACCESSORY,
    ::android::hardware::audio::common::V2_0::AudioDevice::IN_USB_DEVICE,
    ::android::hardware::audio::common::V2_0::AudioDevice::IN_FM_TUNER,
    ::android::hardware::audio::common::V2_0::AudioDevice::IN_TV_TUNER,
    ::android::hardware::audio::common::V2_0::AudioDevice::IN_LINE,
    ::android::hardware::audio::common::V2_0::AudioDevice::IN_SPDIF,
    ::android::hardware::audio::common::V2_0::AudioDevice::IN_BLUETOOTH_A2DP,
    ::android::hardware::audio::common::V2_0::AudioDevice::IN_LOOPBACK,
    ::android::hardware::audio::common::V2_0::AudioDevice::IN_IP,
    ::android::hardware::audio::common::V2_0::AudioDevice::IN_BUS,
    ::android::hardware::audio::common::V2_0::AudioDevice::IN_PROXY,
    ::android::hardware::audio::common::V2_0::AudioDevice::IN_USB_HEADSET,
    ::android::hardware::audio::common::V2_0::AudioDevice::IN_DEFAULT,
    ::android::hardware::audio::common::V2_0::AudioDevice::IN_ALL,
    ::android::hardware::audio::common::V2_0::AudioDevice::IN_ALL_SCO,
    ::android::hardware::audio::common::V2_0::AudioDevice::IN_ALL_USB,
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
template<> inline constexpr std::array<::android::hardware::audio::common::V2_0::AudioOutputFlag, 15> hidl_enum_values<::android::hardware::audio::common::V2_0::AudioOutputFlag> = {
    ::android::hardware::audio::common::V2_0::AudioOutputFlag::NONE,
    ::android::hardware::audio::common::V2_0::AudioOutputFlag::DIRECT,
    ::android::hardware::audio::common::V2_0::AudioOutputFlag::PRIMARY,
    ::android::hardware::audio::common::V2_0::AudioOutputFlag::FAST,
    ::android::hardware::audio::common::V2_0::AudioOutputFlag::DEEP_BUFFER,
    ::android::hardware::audio::common::V2_0::AudioOutputFlag::COMPRESS_OFFLOAD,
    ::android::hardware::audio::common::V2_0::AudioOutputFlag::NON_BLOCKING,
    ::android::hardware::audio::common::V2_0::AudioOutputFlag::HW_AV_SYNC,
    ::android::hardware::audio::common::V2_0::AudioOutputFlag::TTS,
    ::android::hardware::audio::common::V2_0::AudioOutputFlag::RAW,
    ::android::hardware::audio::common::V2_0::AudioOutputFlag::SYNC,
    ::android::hardware::audio::common::V2_0::AudioOutputFlag::IEC958_NONAUDIO,
    ::android::hardware::audio::common::V2_0::AudioOutputFlag::DIRECT_PCM,
    ::android::hardware::audio::common::V2_0::AudioOutputFlag::MMAP_NOIRQ,
    ::android::hardware::audio::common::V2_0::AudioOutputFlag::VOIP_RX,
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
template<> inline constexpr std::array<::android::hardware::audio::common::V2_0::AudioInputFlag, 7> hidl_enum_values<::android::hardware::audio::common::V2_0::AudioInputFlag> = {
    ::android::hardware::audio::common::V2_0::AudioInputFlag::NONE,
    ::android::hardware::audio::common::V2_0::AudioInputFlag::FAST,
    ::android::hardware::audio::common::V2_0::AudioInputFlag::HW_HOTWORD,
    ::android::hardware::audio::common::V2_0::AudioInputFlag::RAW,
    ::android::hardware::audio::common::V2_0::AudioInputFlag::SYNC,
    ::android::hardware::audio::common::V2_0::AudioInputFlag::MMAP_NOIRQ,
    ::android::hardware::audio::common::V2_0::AudioInputFlag::VOIP_TX,
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
template<> inline constexpr std::array<::android::hardware::audio::common::V2_0::AudioUsage, 19> hidl_enum_values<::android::hardware::audio::common::V2_0::AudioUsage> = {
    ::android::hardware::audio::common::V2_0::AudioUsage::UNKNOWN,
    ::android::hardware::audio::common::V2_0::AudioUsage::MEDIA,
    ::android::hardware::audio::common::V2_0::AudioUsage::VOICE_COMMUNICATION,
    ::android::hardware::audio::common::V2_0::AudioUsage::VOICE_COMMUNICATION_SIGNALLING,
    ::android::hardware::audio::common::V2_0::AudioUsage::ALARM,
    ::android::hardware::audio::common::V2_0::AudioUsage::NOTIFICATION,
    ::android::hardware::audio::common::V2_0::AudioUsage::NOTIFICATION_TELEPHONY_RINGTONE,
    ::android::hardware::audio::common::V2_0::AudioUsage::NOTIFICATION_COMMUNICATION_REQUEST,
    ::android::hardware::audio::common::V2_0::AudioUsage::NOTIFICATION_COMMUNICATION_INSTANT,
    ::android::hardware::audio::common::V2_0::AudioUsage::NOTIFICATION_COMMUNICATION_DELAYED,
    ::android::hardware::audio::common::V2_0::AudioUsage::NOTIFICATION_EVENT,
    ::android::hardware::audio::common::V2_0::AudioUsage::ASSISTANCE_ACCESSIBILITY,
    ::android::hardware::audio::common::V2_0::AudioUsage::ASSISTANCE_NAVIGATION_GUIDANCE,
    ::android::hardware::audio::common::V2_0::AudioUsage::ASSISTANCE_SONIFICATION,
    ::android::hardware::audio::common::V2_0::AudioUsage::GAME,
    ::android::hardware::audio::common::V2_0::AudioUsage::VIRTUAL_SOURCE,
    ::android::hardware::audio::common::V2_0::AudioUsage::ASSISTANT,
    ::android::hardware::audio::common::V2_0::AudioUsage::CNT,
    ::android::hardware::audio::common::V2_0::AudioUsage::MAX,
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
template<> inline constexpr std::array<::android::hardware::audio::common::V2_0::AudioGainMode, 3> hidl_enum_values<::android::hardware::audio::common::V2_0::AudioGainMode> = {
    ::android::hardware::audio::common::V2_0::AudioGainMode::JOINT,
    ::android::hardware::audio::common::V2_0::AudioGainMode::CHANNELS,
    ::android::hardware::audio::common::V2_0::AudioGainMode::RAMP,
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
template<> inline constexpr std::array<::android::hardware::audio::common::V2_0::AudioPortRole, 3> hidl_enum_values<::android::hardware::audio::common::V2_0::AudioPortRole> = {
    ::android::hardware::audio::common::V2_0::AudioPortRole::NONE,
    ::android::hardware::audio::common::V2_0::AudioPortRole::SOURCE,
    ::android::hardware::audio::common::V2_0::AudioPortRole::SINK,
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
template<> inline constexpr std::array<::android::hardware::audio::common::V2_0::AudioPortType, 4> hidl_enum_values<::android::hardware::audio::common::V2_0::AudioPortType> = {
    ::android::hardware::audio::common::V2_0::AudioPortType::NONE,
    ::android::hardware::audio::common::V2_0::AudioPortType::DEVICE,
    ::android::hardware::audio::common::V2_0::AudioPortType::MIX,
    ::android::hardware::audio::common::V2_0::AudioPortType::SESSION,
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
template<> inline constexpr std::array<::android::hardware::audio::common::V2_0::AudioPortConfigMask, 5> hidl_enum_values<::android::hardware::audio::common::V2_0::AudioPortConfigMask> = {
    ::android::hardware::audio::common::V2_0::AudioPortConfigMask::SAMPLE_RATE,
    ::android::hardware::audio::common::V2_0::AudioPortConfigMask::CHANNEL_MASK,
    ::android::hardware::audio::common::V2_0::AudioPortConfigMask::FORMAT,
    ::android::hardware::audio::common::V2_0::AudioPortConfigMask::GAIN,
    ::android::hardware::audio::common::V2_0::AudioPortConfigMask::ALL,
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
template<> inline constexpr std::array<::android::hardware::audio::common::V2_0::AudioMixLatencyClass, 2> hidl_enum_values<::android::hardware::audio::common::V2_0::AudioMixLatencyClass> = {
    ::android::hardware::audio::common::V2_0::AudioMixLatencyClass::LOW,
    ::android::hardware::audio::common::V2_0::AudioMixLatencyClass::NORMAL,
};
#pragma clang diagnostic pop
}  // namespace details
}  // namespace hardware
}  // namespace android


#endif  // HIDL_GENERATED_ANDROID_HARDWARE_AUDIO_COMMON_V2_0_TYPES_H
