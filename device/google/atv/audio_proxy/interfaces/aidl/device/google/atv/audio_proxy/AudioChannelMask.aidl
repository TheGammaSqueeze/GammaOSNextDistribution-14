package device.google.atv.audio_proxy;

/**
 * Audio channel mask. The enum values are from AUDIO_CHANNEL_OUT_MASK defined
 * in audio-hal-enums.h. The listed values are required to be supported by the
 * client.
 */
@VintfStability
@Backing(type="int")
enum AudioChannelMask {
    MONO = 1,
    STEREO = 3,
}