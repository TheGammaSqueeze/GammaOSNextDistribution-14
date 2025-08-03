package device.google.atv.audio_proxy;

/**
 * AIDL version of timespec.
 */
@VintfStability
@FixedSize
parcelable TimeSpec {
    long tvSec;
    long tvNSec;
}
