package device.google.atv.audio_proxy;

import android.hardware.common.fmq.MQDescriptor;
import android.hardware.common.fmq.SynchronizedReadWrite;

import device.google.atv.audio_proxy.AudioDrain;
import device.google.atv.audio_proxy.MmapBufferInfo;
import device.google.atv.audio_proxy.PresentationPosition;
import device.google.atv.audio_proxy.WriteStatus;

/**
 * A simplified audio HAL IStreamOut interface. The methods listed should cover
 * usages for PCM playback.
 * Unless specified, the method should have a corresponding API in IStreamOut.hal.
 * Some optional APIs are removed since they are not needed for our use case.
 * Refer IStreamOut.hal/IStream.hal for more details.
 */
@VintfStability
interface IOutputStream {
    /**
     * Playback control signals.
     */
    void standby();
    void close();
    void pause();
    void resume();
    void drain(AudioDrain drain);
    void flush();

    /**
     * Creates FMQ for audio data. Compared to IStreamOut::prepareForWriting,
     * 1. WriteStatus contains both written bytes and rendering delay.
     * 2. We removed WriteCommand FMQ because the impl should return all the
     *    fields. The rendering delay will be used to calculate the presentation
     *    position required by IStreamOut.
     */
    void prepareForWriting(
        in int frameSize,
        in int framesCount,
        out MQDescriptor<byte, SynchronizedReadWrite> dataMQ,
        out MQDescriptor<WriteStatus, SynchronizedReadWrite> statusMQ);

    /**
     * Volume control.
     */
    void setVolume(float left, float right);

    /**
     * Get the buffer size and latency of the stream. They're called before starting the playback.
     */
    long getBufferSizeBytes();
    int getLatencyMs();

    /**
     * Start/Stop playback for MMAP_NOIRQ stream.
     */
    void start();
    void stop();

    /**
     * Create a share memory for MMAP_NOIRQ stream.
     */
    MmapBufferInfo createMmapBuffer(int minSizeFrames);

    /**
     * Query the presentation position for MMAP_NOIRQ stream.
     */
    PresentationPosition getMmapPosition();
}
