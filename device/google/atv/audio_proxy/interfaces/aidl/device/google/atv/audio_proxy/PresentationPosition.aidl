package device.google.atv.audio_proxy;

import device.google.atv.audio_proxy.TimeSpec;

/**
 * Info on playback timestamp:
 * frames is the amount of data which the pipeline played out up to this
 * timestamp.
 * timestamp is the CLOCK_MONOTONIC time at which the presented frames
 * measurement was taken.
 */
@VintfStability
@FixedSize
parcelable PresentationPosition {
    long frames;
    TimeSpec timestamp;
}