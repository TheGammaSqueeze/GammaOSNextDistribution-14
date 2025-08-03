package device.google.atv.audio_proxy;

/**
 * Audio format for the output stream. The enum values are from AUDIO_FORMAT_
 * defined in audio-hal-enums.h. The listed values are required to be supported
 * by the client.
 */
@VintfStability
@Backing(type="int")
enum AudioFormat {
    PCM_16_BIT = 1,
    PCM_8_BIT = 2,
    PCM_FLOAT = 5,
}