// NanoAc3 - liba52 (a52dec 0.7.4) wrapper. See NanoAc3.h.
#include "NanoAc3.h"

#include <stdint.h>
#include <string.h>

extern "C" {
#include "a52.h"     // vendored liba52 public API (sample_t = float; no LIBA52_DOUBLE)
}

namespace android {

// AC-3 sync word is 0x0B77. a52_syncinfo() validates the header and returns the full
// frame length (and the sample/bit rate) so we know when a whole frame is buffered.
static const int kA52FrameMax = 3840;   // max AC-3 frame size

NanoAc3::~NanoAc3() {
    if (mState) { a52_free(mState); mState = nullptr; }
}

bool NanoAc3::init() {
    if (mState) return true;
    mState = a52_init(0);   // mm_accel 0 = generic C paths
    return mState != nullptr;
}

void NanoAc3::free() { if (mState) { a52_free(mState); mState = nullptr; } }

void NanoAc3::reset() { /* liba52 is per-frame stateless across our use; nothing to do */ }

int NanoAc3::decode(const uint8_t* data, int len, std::vector<int16_t>& out, int& outRate) {
    if (!mState || !data || len < 7) return 0;
    int consumed = 0;
    while (len - consumed >= 7) {
        const uint8_t* buf = data + consumed;
        int flags = 0, rate = 0, bitrate = 0;
        int flen = a52_syncinfo(const_cast<uint8_t*>(buf), &flags, &rate, &bitrate);
        if (flen <= 0 || flen > kA52FrameMax) {
            // Not a frame boundary: advance one byte to resync. (Extractor access
            // units are normally frame-aligned, so this rarely triggers.)
            consumed++;
            continue;
        }
        if (len - consumed < flen) break;   // partial trailing frame: wait for more
        mRate = rate; outRate = rate;
        // Downmix to stereo, normalize level, no bias.
        int outFlags = A52_STEREO | A52_ADJUST_LEVEL;
        sample_t level = 1.0f, bias = 0.0f;
        if (a52_frame(mState, const_cast<uint8_t*>(buf), &outFlags, &level, bias) != 0) {
            consumed += flen;               // corrupt frame: skip it
            continue;
        }
        // 6 blocks per frame, each 256 samples per channel. With A52_STEREO the output
        // is L then R (256 each). Interleave to int16.
        for (int blk = 0; blk < 6; blk++) {
            if (a52_block(mState) != 0) break;
            const sample_t* s = a52_samples(mState);   // [0..255]=L, [256..511]=R
            for (int i = 0; i < 256; i++) {
                float l = s[i], r = s[256 + i];
                if (l > 1.0f) l = 1.0f; else if (l < -1.0f) l = -1.0f;
                if (r > 1.0f) r = 1.0f; else if (r < -1.0f) r = -1.0f;
                out.push_back((int16_t)(l * 32767.0f));
                out.push_back((int16_t)(r * 32767.0f));
            }
        }
        consumed += flen;
    }
    return consumed;
}

} // namespace android
