package device.google.atv.audio_proxy;

/**
 * FMQ event flag to indicate the status of the queue.
 */
@VintfStability
@Backing(type="int")
enum MessageQueueFlag {
    NOT_EMPTY = 1 << 0,
    NOT_FULL = 1 << 1,
}