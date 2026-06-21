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

#include "NanoIcyDemux.h"
#include "NanoHls.h"
#include "NanoAudio.h"

#include <stdio.h>
#include <string.h>
#include <unistd.h>

#include <media/NdkMediaCodec.h>
#include <media/NdkMediaFormat.h>

#include <android/log.h>
#define DLOGI(...) __android_log_print(ANDROID_LOG_INFO, "GammaOSNano", __VA_ARGS__)
#define DLOGW(...) __android_log_print(ANDROID_LOG_WARN, "GammaOSNano", __VA_ARGS__)

namespace android {

static inline uint32_t le32(const uint8_t* p) {
    return (uint32_t)p[0] | ((uint32_t)p[1] << 8) | ((uint32_t)p[2] << 16) | ((uint32_t)p[3] << 24);
}

// ---- probe -----------------------------------------------------------------
NanoIcyDemux::Codec NanoIcyDemux::probe(const std::string& url) {
    if (url.empty() || url.find('\'') != std::string::npos || url.size() >= 4000) return kUnknown;
    // Grab the first few KB (Range-limited; --max-time bounds servers that ignore Range).
    std::string cmd = "/system/bin/curl -s -L --max-time 8 --http0.9 -H 'Icy-MetaData: 0' "
                      "-r 0-8191 -A 'gammaos-nano-radio' '" + url + "' 2>/dev/null";
    FILE* f = popen(cmd.c_str(), "r");
    if (!f) return kUnknown;
    uint8_t buf[8192];
    size_t n = fread(buf, 1, sizeof(buf), f);
    pclose(f);
    // Scan the buffer for an Ogg page (icecast streams from the live point, so the capture
    // pattern is rarely at offset 0).
    for (size_t i = 0; i + 4 <= n; i++)
        if (buf[i] == 'O' && buf[i+1] == 'g' && buf[i+2] == 'g' && buf[i+3] == 'S') return kOgg;
    // Scan for an ADTS frame sync (0xFF, layer bits 00 - distinguishes from MP3 whose layer != 00)
    // CONFIRMED by a second sync at +frame_length, so we never false-positive on stray bytes.
    for (size_t i = 0; i + 7 <= n; i++) {
        if (buf[i] != 0xFF || (buf[i+1] & 0xF6) != 0xF0) continue;
        int fl = ((buf[i+3] & 0x3) << 11) | ((int)buf[i+4] << 3) | (buf[i+5] >> 5);
        if (fl >= 7 && i + (size_t)fl + 1 < n &&
            buf[i+fl] == 0xFF && (buf[i+fl+1] & 0xF6) == 0xF0)
            return kAac;
    }
    return kUnknown;   // MP3 / HLS / unknown -> stays on the AMediaExtractor path
}

// ---------------------------------------------------------------------------
NanoIcyDemux::NanoIcyDemux(const std::string& url, NanoAudioPlayer* sink, Codec codec)
    : mUrl(url), mSink(sink), mCodec(codec) {}

NanoIcyDemux::~NanoIcyDemux() {
    requestStop();
    if (mHls) mHls->requestStop();   // unblock a worker parked in readBytes
    if (mThread.joinable()) mThread.join();
    if (mHls) { delete mHls; mHls = nullptr; }
}

bool NanoIcyDemux::start() {
    mHls = new NanoHls(mUrl, /*directHint=*/true);
    if (!mHls->start()) { delete mHls; mHls = nullptr; return false; }
    mStarted = true;
    mThread = std::thread(&NanoIcyDemux::workerFunc, this);
    return true;
}

void NanoIcyDemux::requestStop() { mStop.store(true); if (mHls) mHls->requestStop(); }

void NanoIcyDemux::workerFunc() {
    if (mCodec == kOgg) runOgg();
    else if (mCodec == kAac) runAac();
    // Never produced audio (codec unsupported / stream died before the first frame) and not a
    // user teardown: surface the failure so the UI shows "Could not open this station."
    if (!mFedOpen && !mStop.load() && mSink) mSink->markOpenFailed();
}

// ---- byte reader over the NanoHls stream -----------------------------------
bool NanoIcyDemux::readBytes(uint8_t* dst, size_t n) {
    size_t got = 0;
    while (got < n) {
        if (mStop.load()) return false;
        ssize_t r = mHls->readAt((off64_t)(mReadOff + got), dst + got, n - got);
        if (r <= 0) return false;   // EOS / stop
        got += (size_t)r;
    }
    mReadOff += (int64_t)n;
    return true;
}

// ---- Ogg page + packet reassembly ------------------------------------------
bool NanoIcyDemux::oggReadPage(bool& bos, bool& eos, uint32_t& serial) {
    uint8_t hdr[27];
    if (!readBytes(hdr, 27)) return false;
    // Resync to the "OggS" capture pattern (skips any ICY/junk between pages).
    int guard = 0;
    while (!(hdr[0] == 'O' && hdr[1] == 'g' && hdr[2] == 'g' && hdr[3] == 'S')) {
        memmove(hdr, hdr + 1, 26);
        if (!readBytes(hdr + 26, 1)) return false;
        if (++guard > 1 << 20) return false;   // ~1MB of garbage: give up
    }
    bos = (hdr[5] & 0x02) != 0;
    eos = (hdr[5] & 0x04) != 0;
    serial = le32(hdr + 14);
    mNumSeg = hdr[26];
    if (mNumSeg > 0 && !readBytes(mLace, (size_t)mNumSeg)) return false;
    size_t dataLen = 0;
    for (int i = 0; i < mNumSeg; i++) dataLen += mLace[i];
    mPageData.resize(dataLen);
    if (dataLen > 0 && !readBytes(mPageData.data(), dataLen)) return false;
    return true;
}

bool NanoIcyDemux::oggNextPacket(std::vector<uint8_t>& out) {
    for (;;) {
        if (!mOggPkts.empty()) { out = std::move(mOggPkts.front()); mOggPkts.pop_front(); return true; }
        if (mStop.load()) return false;
        bool bos = false, eos = false; uint32_t serial = 0;
        if (!oggReadPage(bos, eos, serial)) return false;
        // Lock onto the first logical stream; ignore pages from any other serial (chained/multiplexed).
        if (!mOggSerialLocked) { mOggSerial = serial; mOggSerialLocked = true; }
        else if (serial != mOggSerial) continue;
        // Walk the segment table, accumulating packets (a lacing value < 255 ends a packet).
        size_t pos = 0;
        for (int i = 0; i < mNumSeg; i++) {
            int l = mLace[i];
            if (l > 0) mOggAccum.insert(mOggAccum.end(), mPageData.begin() + pos, mPageData.begin() + pos + l);
            pos += l;
            if (l < 255) { mOggPkts.push_back(std::move(mOggAccum)); mOggAccum.clear(); }
        }
    }
}

// ---- AMediaCodec helpers ---------------------------------------------------
void* NanoIcyDemux::openCodec(const char* mime, int sampleRate, int channels,
                              const uint8_t* csd0, size_t csd0n,
                              const uint8_t* csd1, size_t csd1n,
                              const uint8_t* csd2, size_t csd2n) {
    AMediaFormat* fmt = AMediaFormat_new();
    AMediaFormat_setString(fmt, AMEDIAFORMAT_KEY_MIME, mime);
    AMediaFormat_setInt32(fmt, AMEDIAFORMAT_KEY_SAMPLE_RATE, sampleRate > 0 ? sampleRate : 48000);
    AMediaFormat_setInt32(fmt, AMEDIAFORMAT_KEY_CHANNEL_COUNT, channels > 0 ? channels : 2);
    if (csd0 && csd0n) AMediaFormat_setBuffer(fmt, AMEDIAFORMAT_KEY_CSD_0, csd0, csd0n);
    if (csd1 && csd1n) AMediaFormat_setBuffer(fmt, AMEDIAFORMAT_KEY_CSD_1, csd1, csd1n);
    if (csd2 && csd2n) AMediaFormat_setBuffer(fmt, AMEDIAFORMAT_KEY_CSD_2, csd2, csd2n);
    AMediaCodec* c = AMediaCodec_createDecoderByType(mime);
    if (!c) { DLOGW("NanoIcy: no decoder for %s", mime); AMediaFormat_delete(fmt); return nullptr; }
    media_status_t st = AMediaCodec_configure(c, fmt, nullptr, nullptr, 0);
    AMediaFormat_delete(fmt);
    if (st != AMEDIA_OK) { DLOGW("NanoIcy: configure %s failed (%d)", mime, (int)st); AMediaCodec_delete(c); return nullptr; }
    if (AMediaCodec_start(c) != AMEDIA_OK) { DLOGW("NanoIcy: start %s failed", mime); AMediaCodec_delete(c); return nullptr; }
    DLOGI("NanoIcy: codec %s started (%dHz x%d)", mime, sampleRate, channels);
    return c;
}

void NanoIcyDemux::pcmToSink(const uint8_t* data, size_t bytes, int pcmEncoding) {
    if (!mFedOpen || bytes == 0) return;
    const int16_t* s;
    size_t nSamples;
    if (pcmEncoding == 4) {   // PCM_FLOAT -> int16
        size_t nf = bytes / sizeof(float);
        mConv.resize(nf);
        const float* fp = reinterpret_cast<const float*>(data);
        for (size_t i = 0; i < nf; i++) {
            float v = fp[i] * 32767.0f;
            if (v > 32767.0f) v = 32767.0f; else if (v < -32768.0f) v = -32768.0f;
            mConv[i] = (int16_t)v;
        }
        s = mConv.data(); nSamples = nf;
    } else {
        s = reinterpret_cast<const int16_t*>(data); nSamples = bytes / sizeof(int16_t);
    }
    size_t off = 0;
    while (off < nSamples && !mStop.load()) {
        size_t w = mSink->feedPcm(s + off, nSamples - off);
        off += w;
        if (w == 0) usleep(5000);   // ring full: pace
    }
    if (mOutChans > 0) mFramesFed += (int64_t)(nSamples / (size_t)mOutChans);
}

void NanoIcyDemux::drainCodec(void* codecV, bool toEos) {
    AMediaCodec* codec = static_cast<AMediaCodec*>(codecV);
    for (;;) {
        if (mStop.load()) return;
        AMediaCodecBufferInfo info;
        ssize_t ob = AMediaCodec_dequeueOutputBuffer(codec, &info, toEos ? 5000 : 0);
        if (ob >= 0) {
            if (info.size > 0) {
                size_t osz = 0;
                uint8_t* out = AMediaCodec_getOutputBuffer(codec, (size_t)ob, &osz);
                if (out) pcmToSink(out + info.offset, (size_t)info.size, mPcmEnc);
            }
            bool eos = (info.flags & AMEDIACODEC_BUFFER_FLAG_END_OF_STREAM) != 0;
            AMediaCodec_releaseOutputBuffer(codec, (size_t)ob, false);
            if (eos) return;
        } else if (ob == AMEDIACODEC_INFO_OUTPUT_FORMAT_CHANGED) {
            AMediaFormat* of = AMediaCodec_getOutputFormat(codec);
            if (of) {
                int32_t r = 0, c = 0, enc = 0;
                AMediaFormat_getInt32(of, AMEDIAFORMAT_KEY_SAMPLE_RATE, &r);
                AMediaFormat_getInt32(of, AMEDIAFORMAT_KEY_CHANNEL_COUNT, &c);
                if (AMediaFormat_getInt32(of, AMEDIAFORMAT_KEY_PCM_ENCODING, &enc)) mPcmEnc = enc;
                AMediaFormat_delete(of);
                if (r > 0 && c > 0 && (r != mOutRate || c != mOutChans || !mFedOpen)) {
                    mOutRate = r; mOutChans = c;
                    DLOGI("NanoIcy: output format %dHz x%d (enc %d)", r, c, mPcmEnc);
                    if (mSink->openFed(r, c)) {
                        if (mCodecLabel[0]) mSink->setCodecLabel(mCodecLabel);   // badge (openFed cleared meta)
                        mSink->play(); mFedOpen = true;
                    }
                }
            }
        } else {
            return;   // TRY_AGAIN_LATER / buffers-changed: nothing ready
        }
    }
}

// ---- Ogg pipeline (Vorbis / Opus / FLAC-in-Ogg) ----------------------------
void NanoIcyDemux::runOgg() {
    std::vector<uint8_t> p0;
    if (!oggNextPacket(p0)) return;

    const char* mime = nullptr;
    std::vector<uint8_t> csd0, csd1, csd2;
    int rate = 48000, ch = 2;

    if (p0.size() >= 16 && p0[0] == 0x01 && memcmp(&p0[1], "vorbis", 6) == 0) {
        mime = "audio/vorbis"; mCodecLabel = "OGG";
        ch = p0[11];
        rate = (int)le32(&p0[12]);
        csd0 = p0;                                   // identification header
        std::vector<uint8_t> p1, p2;
        if (!oggNextPacket(p1)) return;              // comment header (skipped)
        if (!oggNextPacket(p2)) return;              // setup header
        csd1 = p2;
    } else if (p0.size() >= 19 && memcmp(&p0[0], "OpusHead", 8) == 0) {
        mime = "audio/opus"; mCodecLabel = "OPUS";
        ch = p0[9];
        rate = 48000;                                // Opus always decodes at 48k
        int preskip = (int)p0[10] | ((int)p0[11] << 8);
        csd0 = p0;
        int64_t delayNs = (int64_t)preskip * 1000000000LL / 48000;
        int64_t prerollNs = 80000000LL;             // 80 ms
        csd1.assign((uint8_t*)&delayNs, (uint8_t*)&delayNs + 8);
        csd2.assign((uint8_t*)&prerollNs, (uint8_t*)&prerollNs + 8);
        std::vector<uint8_t> tags; oggNextPacket(tags);   // OpusTags (skipped)
    } else if (p0.size() >= 13 && p0[0] == 0x7F && memcmp(&p0[1], "FLAC", 4) == 0) {
        mime = "audio/flac"; mCodecLabel = "FLAC";   // device decoder = c2.android.flac.decoder (audio/flac)
        int nhdr = ((int)p0[7] << 8) | (int)p0[8];   // number of header packets
        csd0.assign(p0.begin() + 9, p0.end());       // "fLaC" + STREAMINFO metadata block
        if (csd0.size() >= 8 + 18) {
            const uint8_t* si = &csd0[8];            // STREAMINFO payload
            rate = ((int)si[10] << 12) | ((int)si[11] << 4) | ((int)si[12] >> 4);
            ch = (((int)si[12] >> 1) & 0x7) + 1;
        }
        for (int i = 1; i < nhdr; i++) { std::vector<uint8_t> mh; if (!oggNextPacket(mh)) break; }
    } else {
        DLOGW("NanoIcy: unrecognized Ogg codec");
        return;
    }

    void* codec = openCodec(mime, rate, ch,
                            csd0.empty() ? nullptr : csd0.data(), csd0.size(),
                            csd1.empty() ? nullptr : csd1.data(), csd1.size(),
                            csd2.empty() ? nullptr : csd2.data(), csd2.size());
    if (!codec) return;

    std::vector<uint8_t> pkt;
    bool have = false, inEos = false;
    while (!mStop.load()) {
        if (!have && !inEos) {
            if (!oggNextPacket(pkt)) { inEos = true; }   // stream ended; flush
            else have = true;
        }
        ssize_t ib = AMediaCodec_dequeueInputBuffer(static_cast<AMediaCodec*>(codec), 5000);
        if (ib >= 0) {
            if (have) {
                size_t cap = 0;
                uint8_t* in = AMediaCodec_getInputBuffer(static_cast<AMediaCodec*>(codec), (size_t)ib, &cap);
                size_t cn = pkt.size() < cap ? pkt.size() : cap;
                if (in && cn) memcpy(in, pkt.data(), cn);
                int64_t pts = mOutRate > 0 ? (mFramesFed * 1000000LL / mOutRate) : 0;
                AMediaCodec_queueInputBuffer(static_cast<AMediaCodec*>(codec), (size_t)ib, 0, cn,
                                             (uint64_t)pts, 0);
                have = false;
            } else if (inEos) {
                AMediaCodec_queueInputBuffer(static_cast<AMediaCodec*>(codec), (size_t)ib, 0, 0, 0,
                                             AMEDIACODEC_BUFFER_FLAG_END_OF_STREAM);
            }
        }
        drainCodec(codec, false);
        if (inEos) break;
    }
    drainCodec(codec, true);
    AMediaCodec_stop(static_cast<AMediaCodec*>(codec));
    AMediaCodec_delete(static_cast<AMediaCodec*>(codec));
}

// ---- ADTS AAC pipeline -----------------------------------------------------
void NanoIcyDemux::runAac() {
    static const int kRates[] = {96000,88200,64000,48000,44100,32000,24000,22050,16000,12000,11025,8000};
    mCodecLabel = "AAC";
    void* codec = nullptr;
    std::vector<uint8_t> au;
    bool have = false;
    while (!mStop.load()) {
        if (!have) {
            uint8_t h[7];
            if (!readBytes(h, 2)) break;
            int guard = 0;
            while (!(h[0] == 0xFF && (h[1] & 0xF6) == 0xF0)) {   // resync to ADTS sync + layer 00
                h[0] = h[1];
                if (!readBytes(&h[1], 1)) return;
                if (++guard > 1 << 20) return;
            }
            if (!readBytes(&h[2], 5)) break;
            bool crc = !(h[1] & 0x01);
            int profile = (h[2] >> 6) & 0x3;
            int sfi = (h[2] >> 2) & 0xF;
            int chan = ((h[2] & 0x1) << 2) | ((h[3] >> 6) & 0x3);
            int frameLen = ((h[3] & 0x3) << 11) | ((int)h[4] << 3) | ((h[5] >> 5) & 0x7);
            int hdrLen = crc ? 9 : 7;
            if (frameLen < hdrLen || sfi > 11) continue;         // bad header: resync
            if (crc) { uint8_t skip[2]; if (!readBytes(skip, 2)) break; }
            int auLen = frameLen - hdrLen;
            au.resize(auLen);
            if (auLen > 0 && !readBytes(au.data(), (size_t)auLen)) break;
            have = true;
            if (!codec) {
                int rate = kRates[sfi];
                uint8_t asc[2];
                asc[0] = (uint8_t)(((profile + 1) << 3) | (sfi >> 1));
                asc[1] = (uint8_t)(((sfi & 1) << 7) | ((chan & 0xF) << 3));
                codec = openCodec("audio/mp4a-latm", rate, chan ? chan : 2, asc, 2, nullptr, 0, nullptr, 0);
                if (!codec) return;
            }
        }
        ssize_t ib = AMediaCodec_dequeueInputBuffer(static_cast<AMediaCodec*>(codec), 5000);
        if (ib >= 0 && have) {
            size_t cap = 0;
            uint8_t* in = AMediaCodec_getInputBuffer(static_cast<AMediaCodec*>(codec), (size_t)ib, &cap);
            size_t cn = au.size() < cap ? au.size() : cap;
            if (in && cn) memcpy(in, au.data(), cn);
            int64_t pts = mOutRate > 0 ? (mFramesFed * 1000000LL / mOutRate) : 0;
            AMediaCodec_queueInputBuffer(static_cast<AMediaCodec*>(codec), (size_t)ib, 0, cn, (uint64_t)pts, 0);
            have = false;
        }
        drainCodec(codec, false);
    }
    if (codec) {
        drainCodec(codec, true);
        AMediaCodec_stop(static_cast<AMediaCodec*>(codec));
        AMediaCodec_delete(static_cast<AMediaCodec*>(codec));
    }
}

} // namespace android
