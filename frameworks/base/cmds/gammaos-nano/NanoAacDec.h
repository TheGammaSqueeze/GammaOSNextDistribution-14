// NanoAacDec - a thin push-style wrapper over the platform AAC decoder (AMediaCodec
// "audio/mp4a-latm") that decodes ADTS-AAC elementary-stream frames to interleaved
// int16 PCM. It mirrors NanoAc3's interface (accumulate ES bytes, decode whole frames,
// report consumed) so NanoTsDemux can route a live .ts AAC PID through it exactly the
// way it routes AC-3 through liba52: one read pointer, one fed audio ring, no second
// extractor. Unlike AC-3 there is no vendored software decoder - AAC is a platform
// codec - so this drives an async AMediaCodec synchronously (feed each ADTS frame, drain
// whatever PCM is ready). The true output rate/channels are only known after the codec's
// OUTPUT_FORMAT_CHANGED (HE-AAC SBR doubles the ADTS base rate), reported back through
// decode() so the caller can open the fed ring at the correct format. Lazy + self-contained.
#ifndef GAMMAOS_NANO_AAC_DEC_H
#define GAMMAOS_NANO_AAC_DEC_H

#include <cstdint>
#include <vector>

namespace android {

class NanoAacDec {
public:
    NanoAacDec() = default;
    ~NanoAacDec();

    bool ready() const { return mCodec != nullptr; }
    void free();              // stop + delete the codec (full teardown when audio stops)
    void reset();             // flush the codec + forget partial-frame state (on seek/discontinuity)

    // Decode ADTS-AAC from a buffer that may hold zero or more whole frames. Appends
    // interleaved int16 PCM to `out`, sets outRate/outChannels to the codec's true output
    // format (0 until the codec has reported it via OUTPUT_FORMAT_CHANGED - the caller must
    // wait for outRate > 0 before opening the fed ring), and returns the number of input
    // bytes consumed (whole ADTS frames only; a trailing partial frame is left for the next
    // call). The codec is created lazily from the first valid ADTS header.
    int decode(const uint8_t* data, int len, std::vector<int16_t>& out, int& outRate, int& outChannels);

private:
    bool ensureCodec(const uint8_t* adtsHdr);   // create + configure + start from an ADTS header
    void drain(std::vector<int16_t>& out);       // pull all ready output buffers (non-blocking)

    void* mCodec = nullptr;     // AMediaCodec*
    int  mRate = 0;             // output sample rate (0 until OUTPUT_FORMAT_CHANGED)
    int  mChannels = 0;         // output channel count (0 until OUTPUT_FORMAT_CHANGED)
    int  mPcmEnc = 2;           // 2 = PCM_16BIT, 4 = PCM_FLOAT (from OUTPUT_FORMAT_CHANGED)
    int64_t mFedFrames = 0;     // monotonic input pts source (1024 samples per AAC frame)
    std::vector<float> mConv;   // scratch for PCM_FLOAT -> int16 conversion
};

} // namespace android

#endif // GAMMAOS_NANO_AAC_DEC_H
