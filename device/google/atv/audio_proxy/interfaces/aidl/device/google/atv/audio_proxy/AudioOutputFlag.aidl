package device.google.atv.audio_proxy;

/**
 * Audio output flag for the output stream. The enum values are from
 * AUDIO_OUTPUT_FLAG_ defined in audio-hal-enums.h. The values listed
 * except HW_AV_SYNC are required to be supported by the client.
 */
@VintfStability
@Backing(type="int")
enum AudioOutputFlag {
    NONE = 0,
    DIRECT = 0x1,
    HW_AV_SYNC = 0x40,
}