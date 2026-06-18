// NanoAc3 - a thin C++ wrapper over the vendored liba52 (a52dec 0.7.4, GPL-2) that
// decodes AC-3 elementary-stream frames to interleaved int16 stereo PCM. The device
// has no hardware or software AC-3 decoder, so NanoAudio routes audio/ac3 tracks here
// instead of AMediaCodec; everything downstream (the PCM ring, AAudio, the FFT tap) is
// unchanged. Lazy + self-contained: a52_init on init(), a52_free on destruction.
#ifndef GAMMAOS_NANO_AC3_H
#define GAMMAOS_NANO_AC3_H

#include <cstdint>
#include <vector>

struct a52_state_s;

namespace android {

class NanoAc3 {
public:
    NanoAc3() = default;
    ~NanoAc3();

    bool init();              // allocate the liba52 state (idempotent)
    bool ready() const { return mState != nullptr; }
    void reset();             // forget partial-frame state (on seek/flush)

    // Decode AC-3 from a buffer that may hold zero or more whole frames. Appends
    // interleaved int16 stereo PCM to `out`, sets outRate to the stream sample rate,
    // and returns the number of input bytes consumed (whole frames only; a trailing
    // partial frame is left for the next call). Returns 0 if no full frame is present.
    int decode(const uint8_t* data, int len, std::vector<int16_t>& out, int& outRate);

private:
    a52_state_s* mState = nullptr;
    int mRate = 48000;
};

} // namespace android

#endif // GAMMAOS_NANO_AC3_H
