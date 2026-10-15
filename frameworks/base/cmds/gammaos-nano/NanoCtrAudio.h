// NanoCtrAudio - the 3DS theme's sound player: the menu's sound effects and BGM, rendered from
// the console's sequences by tools/ctr_render_sounds.py into WAVs (looping sounds carry a "smpl"
// loop), mixed on one AAudio stream. Voices have volume, pitch and fades (the nw::snd handle
// operations the menu uses). One power saving stream (menu sounds need no low latency, so the
// fast mixer is never woken); it stops after a short silence so an idle home costs nothing, and
// reopens on a route change.
#pragma once

#include <atomic>
#include <cstdint>
#include <map>
#include <memory>
#include <mutex>
#include <string>
#include <vector>

namespace android {
namespace ctr {

class CtrAudio {
public:
    explicit CtrAudio(std::string soundDir);
    ~CtrAudio();
    // Starts a sound by its BCSAR name; returns a handle (0 when the sound does not exist).
    int play(const std::string& name, float volume = 1.0f, float pitch = 1.0f);
    void stop(int handle, int fadeFrames = 0);
    void setPitch(int handle, float pitch);
    void stopAll(int fadeFrames = 0);
    void stopBgm(int fadeFrames = 0);   // the BGM voices only
    bool bgmPlaying();
    bool playing(int handle);
    void setMasters(float se, float bgm) { mSeMaster = se; mBgmMaster = bgm; }
    void preload(const std::string& name);
    // Plays 16 bit PCM held elsewhere (a title's banner sound); `keep` holds the samples alive
    // while the voice plays.
    int playPcm(std::shared_ptr<const void> keep, const int16_t* pcm, int64_t frames, int channels, int rate,
                bool loop, int64_t loopStart, int64_t loopEnd, float volume = 1.0f);
    // A theme's sounds (theme/sounds.ts): a named sound is replaced by PCM held elsewhere or by a
    // WAV file (mapped like the menu's own), or silenced; `gain` scales it against the master of
    // its kind. clearOverrides() gives every name back its menu sound.
    void overridePcm(const std::string& name, std::shared_ptr<const void> keep, const int16_t* pcm, int64_t frames,
                     int channels, int rate, bool loop, int64_t loopStart, int64_t loopEnd, float gain);
    void overrideWav(const std::string& name, const std::string& path, float gain);
    void silence(const std::string& name);
    void clearOverrides();
    float seMaster() const { return mSeMaster; }
    float bgmMaster() const { return mBgmMaster; }
    // Closes the output (the home parks behind an app); a later play reopens it.
    void release();
    int32_t fill(int which, int16_t* out, int32_t frames);   // stream callback (public for the trampolines)
    // Debug capture (nano's boot recording, sys.gammaos.nano.bootrec): while on, the output plays
    // silence and the voices advance only through capture(), which mixes `frames` at the output
    // rate into `out` (stereo), so a recording driven by its own clock gets the menu's sound in step.
    void setCapture(bool on) { mCapture.store(on); }
    void capture(int16_t* out, int32_t frames);
    int outputRate() const { return mOut[0].rate; }
    void recover(int which);

private:
    // A WAV mapped read only: the samples stay file backed page cache (evictable) instead of
    // heap, which matters for the long BGM loops on 1 GB devices.
    struct Clip {
        ~Clip();
        void* map = nullptr;
        size_t mapSize = 0;
        const int16_t* pcm = nullptr;
        int channels = 2;
        int rate = 44100;
        int64_t frames = 0;
        bool loop = false;
        int64_t loopStart = 0, loopEnd = 0;
        bool bgm = false;
        float gain = 1;   // an override's level against the master of its kind
        std::string name;
        std::shared_ptr<const void> keep;   // playPcm: the owner of the samples
    };
    struct Voice {
        int handle = 0;
        std::shared_ptr<Clip> clip;
        double pos = 0, step = 1;
        float gain = 1, pitch = 1;
        int fadeTotal = 0, fadeLeft = 0;   // output frames
        bool active = false;
    };
    std::shared_ptr<Clip> clip(const std::string& name);
    std::shared_ptr<Clip> loadWav(const std::string& path, const std::string& name);
    std::map<std::string, std::shared_ptr<Clip>> mOverrides;   // under mClipM; null = silenced
    bool openLocked(int which);
    void ensureRunning(int which);

    std::string mDir;
    std::mutex mClipM;
    std::map<std::string, std::shared_ptr<Clip>> mClips;
    std::mutex mVoiceM;
    std::vector<Voice> mVoices;
    int mNextHandle = 1;
    // The renders carry the console's relative levels (sound volume x velocity^2, as the web
    // player) and come out quiet; the console's output stage adds gain on top. Effects get 2x
    // (their loudest peaks reach full scale), the BGM 4x (rendered about 30 dB below the effects
    // it stays well under them, as on the console); the mix goes through a soft limiter.
    float mSeMaster = 2.0f, mBgmMaster = 4.0f;
    std::atomic<bool> mProbeOn{false};   // sys.gammaos.nano.ctr.audiolog: also log each sound start
    struct Out {
        void* stream = nullptr;
        std::atomic<bool> running{false};
        std::atomic<bool> starting{false};
        int64_t idleFrames = 0;
        std::vector<int32_t> acc;   // mix accumulator, callback thread only
        int rate = 48000;
    };
    std::mutex mStreamM;
    Out mOut[1];
    std::atomic<bool> mShutdown{false};
    std::atomic<bool> mCapture{false};
    int64_t mProbeFrames = 0;
    int mProbePeak = 0;
};

}  // namespace ctr
}  // namespace android
