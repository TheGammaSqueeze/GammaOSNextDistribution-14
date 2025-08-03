package device.google.atv.audio_proxy;

/**
 * Enum defines the behavior for IOutputStream.drain.
 */
@VintfStability
@Backing(type="int")
enum AudioDrain {
    // drain() returns after all the frames being played out.
    ALL = 0,
    // drain() returns shortly before all the frame being played out.
    EARLY_NOTIFY = 1,
}