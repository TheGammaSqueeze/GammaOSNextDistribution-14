/*
 * Copyright (C) 2026 GammaOS
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

// GammaOS Nano standalone audio engine for the PS3-XMB music player.
//
// A self-contained, GL-free module that decodes an audio file (mp3/flac/m4a/aac/
// ogg/opus/wav) to interleaved int16 PCM on a worker thread, streams it out via
// AAudio (the same output API the GammaEQ preview uses), and taps the PCM into a
// 512-point FFT so the visualizers get the same bass/mid/treble + 256 frequency
// bins the web app reads from its AnalyserNode (index.html mpInitAudio / mpBands).
//
// 1:1 mapping to the web player's audio model:
//   - HTMLAudioElement playback        -> AMediaExtractor + AMediaCodec -> AAudio
//   - AnalyserNode fftSize=512         -> a 512-sample Hann-windowed real FFT
//   - smoothingTimeConstant=0.82       -> per-bin temporal smoothing 0.82
//   - mpBands() bass/mid/treble        -> band splits at 0.10 / 0.45 of 256 bins
//
// Everything is lazy: init() is cheap, open() spins the decoder + opens the stream
// only when the user plays a track, and release() tears it all down on exit. The
// audio callback is lock-free (atomic ring indices, zero-fill on underrun); the
// decoder self-throttles by polling the ring's free space.

#ifndef GAMMAOS_NANO_AUDIO_H
#define GAMMAOS_NANO_AUDIO_H

#include <atomic>
#include <cstdint>
#include <mutex>
#include <string>
#include <thread>
#include <vector>

namespace android {

class NanoAudioPlayer {
public:
    struct Meta {
        std::string title;
        std::string artist;
        std::string album;
        std::string codec;       // badge text: MP3 / AAC / PCM / FLAC ...
        std::string genre;       // container tag (may be empty)
        std::string year;        // container tag "date"/"year" (may be empty)
        std::string track;       // raw "cdtracknum" tag, e.g. "3" or "3/12" (may be empty)
        double durationSec = 0.0;
        int sampleRate = 0;
        int channels = 0;
        int bitRate = 0;         // bits/sec, when the track format exposes it
    };

    // 256 frequency bins (0..1) + the three band energies (0..1), matching the web
    // mpBands() shape (bass = mean of bins[0,0.10n), mid = [0.10n,0.45n), treble = rest).
    struct Bands {
        float bass = 0.0f;
        float mid = 0.0f;
        float treble = 0.0f;
        float bins[256] = {0.0f};
    };

    NanoAudioPlayer();
    ~NanoAudioPlayer();

    void init();                               // idempotent, cheap; safe to call repeatedly

    // Start decoding `path`. Reopens the AAudio stream if the format changed. Begins
    // PAUSED; call play() to start. Returns false if the file can't be opened/decoded.
    bool open(const std::string& path);

    void play();
    void pause();
    void togglePause();
    void stop();                               // pause + seek to 0 (marks "stopped")
    void seek(double sec);                      // FF / REW / scrub

    bool isPlaying() const;
    bool isPaused() const;
    bool isStopped() const;
    bool ended();                              // EOS decoded AND ring drained (drives auto-advance)

    double position() const;                    // elapsed seconds
    double duration() const;                    // track length seconds (from metadata)
    void   setVolume(float v01);                // 0..1, applied in the callback

    Meta meta() const;                          // thread-safe copy of the current track's tags
    void getBands(Bands& out);                  // compute the FFT bins/bands for this frame

    void release();                             // stop stream, join decoder, free buffers

    // Metadata + duration only, no playback: used by the library scanner. Cheap-ish
    // (opens an extractor, reads the container/track format, closes). Thread-safe
    // (static, touches no instance state).
    static bool probe(const std::string& path, Meta& out);

    // --- internal: called by the AAudio data callback (public so the C trampoline
    // in the .cpp can reach it; do not call from app code) ---
    int32_t fillAudio(void* audioData, int32_t numFrames);

private:
    void decodeThreadFunc(std::string path);   // worker: extractor+codec -> ring
    bool ensureStream(int rate, int channels); // open/reopen AAudio for the format
    void closeStream();
    void stopDecoder();                         // signal + join the decode thread

    // ---- AAudio output ----
    void* mStream = nullptr;                    // AAudioStream* (opaque)
    int   mStreamRate = 0;
    int   mStreamChans = 0;
    std::mutex mStreamMutex;                    // guards open/close/start/pause
    std::atomic<bool> mStarted{false};          // requestStart issued (playing)
    std::atomic<bool> mStopped{false};          // user pressed Stop (vs Pause)

    // ---- PCM ring (SPSC: decoder produces, callback consumes) ----
    std::vector<int16_t> mRing;                 // capacity in int16 samples
    size_t mRingCap = 0;
    std::atomic<size_t> mHead{0};               // total samples written (producer)
    std::atomic<size_t> mTail{0};               // total samples read (consumer)
    std::atomic<float>  mVol{1.0f};

    // ---- decoder thread ----
    std::thread mDecodeThread;
    std::atomic<bool> mDecodeStop{false};       // ask the decoder to exit
    std::atomic<bool> mEos{false};              // decoder hit end-of-stream
    std::atomic<int64_t> mPendingSeekUs{-1};    // seek (us) the next decode consumes at start
    std::string mCurrentPath;                   // path of the open track (for re-seek respawn)

    // ---- position / metadata ----
    std::atomic<int64_t> mFramesConsumed{0};    // frames the callback has emitted
    std::atomic<int64_t> mSeekBaseFrames{0};    // frame offset applied on the last seek
    Meta mMeta;
    mutable std::mutex mMetaMutex;

    // ---- FFT tap (callback writes the latest 512 mono samples; UI reads) ----
    float mFftRoll[512] = {0.0f};               // callback-owned rolling mono window
    int   mFftRollPos = 0;                      // callback-owned write cursor into mFftRoll
    float mFftPublish[2][512];                  // double-buffered mono window (callback writes, UI reads)
    std::atomic<int> mFftWhich{0};              // index of the most-recently-written buffer
    float mFftSmooth[256] = {0.0f};             // per-bin temporal smoothing state (UI thread)

    bool mInited = false;
};

} // namespace android

#endif // GAMMAOS_NANO_AUDIO_H
