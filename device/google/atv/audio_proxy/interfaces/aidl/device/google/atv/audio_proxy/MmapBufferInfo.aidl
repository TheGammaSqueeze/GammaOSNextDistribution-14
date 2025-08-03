package device.google.atv.audio_proxy;

import android.os.ParcelFileDescriptor;

/**
 * Shared memory and the associated info for the playback.
 * This is the corresponding structure of audio HAL MmapBufferInfo.
 */
@VintfStability
parcelable MmapBufferInfo {
    ParcelFileDescriptor sharedMemoryFd;
    int bufferSizeFrames;
    int burstSizeFrames;
    int flags;
}
