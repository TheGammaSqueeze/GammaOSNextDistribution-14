package device.google.atv.audio_proxy;

import device.google.atv.audio_proxy.IStreamProvider;

@VintfStability
interface IAudioProxy {
    /*
     * Init AudioProxy service with provider. This should be called only once
     * before any other APIs, otherwise an exception will be thrown. In NDK
     * backend, the ScopedAStatus::isOk() returns false.
     *
     * @param provider the provider to provide different IOutputStream for
     *                 playback.
     */
    void start(in IStreamProvider provider);
}