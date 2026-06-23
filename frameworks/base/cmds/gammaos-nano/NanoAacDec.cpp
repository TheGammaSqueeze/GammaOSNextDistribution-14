// NanoAacDec - ADTS-AAC -> int16 PCM via AMediaCodec. See NanoAacDec.h.
#include "NanoAacDec.h"

#include <cstring>
#include <media/NdkMediaCodec.h>
#include <media/NdkMediaFormat.h>
#include <android/log.h>

#define ALOGI(...) __android_log_print(ANDROID_LOG_INFO, "GammaOSNano", __VA_ARGS__)
#define ALOGW(...) __android_log_print(ANDROID_LOG_WARN, "GammaOSNano", __VA_ARGS__)

namespace android {

// ADTS sampling_frequency_index -> Hz (the base rate; HE-AAC SBR output is reported 2x by the codec).
static const int kAdtsRates[16] = {96000,88200,64000,48000,44100,32000,24000,22050,
                                   16000,12000,11025,8000,7350,0,0,0};

NanoAacDec::~NanoAacDec() { free(); }

void NanoAacDec::free() {
    if (mCodec) {
        AMediaCodec_stop(static_cast<AMediaCodec*>(mCodec));
        AMediaCodec_delete(static_cast<AMediaCodec*>(mCodec));
        mCodec = nullptr;
    }
    mRate = mChannels = 0;
    mPcmEnc = 2;
    mFedFrames = 0;
}

void NanoAacDec::reset() {
    if (mCodec) AMediaCodec_flush(static_cast<AMediaCodec*>(mCodec));
    mFedFrames = 0;
}

bool NanoAacDec::ensureCodec(const uint8_t* h) {
    int profile = (h[2] >> 6) & 0x3;            // 0=Main 1=LC 2=SSR 3=LTP (object type = profile + 1)
    int sfi     = (h[2] >> 2) & 0xF;
    int chan    = ((h[2] & 0x1) << 2) | ((h[3] >> 6) & 0x3);
    if (sfi > 12 || kAdtsRates[sfi] == 0) return false;
    int rate = kAdtsRates[sfi];
    // AudioSpecificConfig (2 bytes): audioObjectType(5) | samplingFrequencyIndex(4) | channelConfiguration(4) | 000
    uint8_t asc[2];
    asc[0] = (uint8_t)(((profile + 1) << 3) | (sfi >> 1));
    asc[1] = (uint8_t)(((sfi & 1) << 7) | ((chan & 0xF) << 3));

    AMediaFormat* fmt = AMediaFormat_new();
    AMediaFormat_setString(fmt, AMEDIAFORMAT_KEY_MIME, "audio/mp4a-latm");
    AMediaFormat_setInt32(fmt, AMEDIAFORMAT_KEY_SAMPLE_RATE, rate);
    AMediaFormat_setInt32(fmt, AMEDIAFORMAT_KEY_CHANNEL_COUNT, chan ? chan : 2);
    AMediaFormat_setBuffer(fmt, AMEDIAFORMAT_KEY_CSD_0, asc, 2);
    AMediaCodec* c = AMediaCodec_createDecoderByType("audio/mp4a-latm");
    if (!c) { ALOGW("NanoAacDec: no AAC decoder"); AMediaFormat_delete(fmt); return false; }
    media_status_t st = AMediaCodec_configure(c, fmt, nullptr, nullptr, 0);
    AMediaFormat_delete(fmt);
    if (st != AMEDIA_OK) { ALOGW("NanoAacDec: configure failed (%d)", (int)st); AMediaCodec_delete(c); return false; }
    if (AMediaCodec_start(c) != AMEDIA_OK) { ALOGW("NanoAacDec: start failed"); AMediaCodec_delete(c); return false; }
    mCodec = c;
    // Provisional; the real output format (incl. HE-AAC SBR 2x rate) arrives via OUTPUT_FORMAT_CHANGED.
    ALOGI("NanoAacDec: codec started (ADTS %dHz x%d, obj %d)", rate, chan ? chan : 2, profile + 1);
    return true;
}

void NanoAacDec::drain(std::vector<int16_t>& out) {
    AMediaCodec* c = static_cast<AMediaCodec*>(mCodec);
    for (;;) {
        AMediaCodecBufferInfo info;
        ssize_t ob = AMediaCodec_dequeueOutputBuffer(c, &info, 0);   // non-blocking
        if (ob >= 0) {
            if (info.size > 0) {
                size_t osz = 0;
                uint8_t* o = AMediaCodec_getOutputBuffer(c, (size_t)ob, &osz);
                if (o) {
                    const uint8_t* p = o + info.offset;
                    if (mPcmEnc == 4) {                 // PCM_FLOAT -> int16
                        size_t nf = (size_t)info.size / sizeof(float);
                        const float* fp = reinterpret_cast<const float*>(p);
                        size_t base = out.size();
                        out.resize(base + nf);
                        for (size_t i = 0; i < nf; i++) {
                            float v = fp[i] * 32767.0f;
                            if (v > 32767.0f) v = 32767.0f; else if (v < -32768.0f) v = -32768.0f;
                            out[base + i] = (int16_t)v;
                        }
                    } else {
                        size_t ns = (size_t)info.size / sizeof(int16_t);
                        const int16_t* sp = reinterpret_cast<const int16_t*>(p);
                        out.insert(out.end(), sp, sp + ns);
                    }
                }
            }
            AMediaCodec_releaseOutputBuffer(c, (size_t)ob, false);
        } else if (ob == AMEDIACODEC_INFO_OUTPUT_FORMAT_CHANGED) {
            AMediaFormat* of = AMediaCodec_getOutputFormat(c);
            if (of) {
                int32_t r = 0, ch = 0, enc = 0;
                AMediaFormat_getInt32(of, AMEDIAFORMAT_KEY_SAMPLE_RATE, &r);
                AMediaFormat_getInt32(of, AMEDIAFORMAT_KEY_CHANNEL_COUNT, &ch);
                if (AMediaFormat_getInt32(of, AMEDIAFORMAT_KEY_PCM_ENCODING, &enc)) mPcmEnc = enc;
                AMediaFormat_delete(of);
                if (r > 0) mRate = r;
                if (ch > 0) mChannels = ch;
                ALOGI("NanoAacDec: output format %dHz x%d (enc %d)", mRate, mChannels, mPcmEnc);
            }
        } else {
            break;   // TRY_AGAIN_LATER / buffers-changed: nothing ready right now
        }
    }
}

int NanoAacDec::decode(const uint8_t* data, int len, std::vector<int16_t>& out, int& outRate, int& outChannels) {
    int pos = 0;
    while (pos + 7 <= len) {
        const uint8_t* h = data + pos;
        // ADTS sync: 0xFFF + layer 00 (the 0xF6 mask checks syncword high + layer bits == 0).
        if (!(h[0] == 0xFF && (h[1] & 0xF6) == 0xF0)) { pos++; continue; }   // resync
        bool crc = !(h[1] & 0x01);
        int frameLen = ((h[3] & 0x3) << 11) | ((int)h[4] << 3) | ((h[5] >> 5) & 0x7);
        int hdrLen = crc ? 9 : 7;
        if (frameLen < hdrLen) { pos++; continue; }       // bad header: resync one byte
        if (pos + frameLen > len) break;                  // incomplete frame: keep for the next call
        if (!mCodec && !ensureCodec(h)) { pos += frameLen; continue; }   // could not create: skip frame

        AMediaCodec* c = static_cast<AMediaCodec*>(mCodec);
        ssize_t ib = AMediaCodec_dequeueInputBuffer(c, 4000);   // short wait for an input slot
        if (ib < 0) { drain(out); break; }                 // no slot now: leave this frame for next call
        size_t cap = 0;
        uint8_t* in = AMediaCodec_getInputBuffer(c, (size_t)ib, &cap);
        const uint8_t* au = h + hdrLen;
        int auLen = frameLen - hdrLen;
        size_t cn = (auLen > 0 && (size_t)auLen <= cap) ? (size_t)auLen : 0;
        if (in && cn) memcpy(in, au, cn);
        int64_t pts = mRate > 0 ? mFedFrames * 1000000LL / mRate : (mFedFrames * 1000000LL / 48000);
        AMediaCodec_queueInputBuffer(c, (size_t)ib, 0, cn, (uint64_t)pts, 0);
        mFedFrames += 1024;                                // one AAC frame = 1024 samples
        pos += frameLen;
        drain(out);
    }
    drain(out);
    outRate = mRate;
    outChannels = mChannels;
    return pos;
}

} // namespace android
