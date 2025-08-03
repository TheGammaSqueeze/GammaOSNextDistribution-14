package device.google.atv.audio_proxy;

import device.google.atv.audio_proxy.AudioConfig;
import device.google.atv.audio_proxy.IOutputStream;

@VintfStability
interface IStreamProvider {
    /**
     * Opens an output stream for PCM playback. From audio server's perspective,
     * the stream is opened by an audio device with type AUDIO_DEVICE_OUT_BUS.
     *
     * @param address used to distinguish different streams. In practice, the
     *                client app will use address to configure the audio
     *                routing, e.g. media stream to address1, any other streams
     *                to address2.
     * @param config the config for the output stream.
     * @param flags bitset of AudioOutputFlag.
     */
    IOutputStream openOutputStream(
        in String address, in AudioConfig config, in int flags);
}
