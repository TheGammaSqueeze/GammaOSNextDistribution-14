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

#define LOG_TAG "GammaOSNano"

#include "NanoAudio.h"
#include "NanoAc3.h"   // liba52 AC-3 -> int16 stereo (device has no AC-3 codec)

#include <aaudio/AAudio.h>
#include <media/NdkMediaExtractor.h>
#include <media/NdkMediaCodec.h>
#include <media/NdkMediaFormat.h>

#include <log/log.h>
#include <fcntl.h>
#include <unistd.h>
#include <sys/stat.h>

#include <algorithm>
#include <cmath>
#include <cstring>

namespace android {

// AudioFormat PCM encodings (same int values NdkMediaFormat uses for PCM_ENCODING).
static const int kEncPcm16   = 2;
static const int kEncPcmFloat = 4;

// ---------------------------------------------------------------------------
// 512-point real FFT (iterative radix-2 Cooley-Tukey) for the visualizer bands.
// A precomputed twiddle table + bit-reversal; matches the web AnalyserNode
// fftSize=512 -> 256 magnitude bins.
// ---------------------------------------------------------------------------
static const int kFftN = 512;
static const int kFftBins = 256;

static float sTwidCos[kFftN / 2];
static float sTwidSin[kFftN / 2];
static int   sBitRev[kFftN];
static bool  sFftReady = false;

static void fftInit() {
    if (sFftReady) return;
    for (int i = 0; i < kFftN / 2; i++) {
        float a = -2.0f * (float)M_PI * (float)i / (float)kFftN;
        sTwidCos[i] = cosf(a);
        sTwidSin[i] = sinf(a);
    }
    int bits = 9; // log2(512)
    for (int i = 0; i < kFftN; i++) {
        int x = i, r = 0;
        for (int b = 0; b < bits; b++) { r = (r << 1) | (x & 1); x >>= 1; }
        sBitRev[i] = r;
    }
    sFftReady = true;
}

// In-place FFT of real input `in` (kFftN samples). Writes magnitudes (not yet
// scaled) into `mag` (kFftBins entries).
static void fftMagnitudes(const float* in, float* mag) {
    float re[kFftN], im[kFftN];
    for (int i = 0; i < kFftN; i++) { int j = sBitRev[i]; re[i] = in[j]; im[i] = 0.0f; }
    for (int len = 2; len <= kFftN; len <<= 1) {
        int half = len >> 1;
        int step = kFftN / len;
        for (int i = 0; i < kFftN; i += len) {
            int k = 0;
            for (int j = 0; j < half; j++) {
                float wc = sTwidCos[k], ws = sTwidSin[k];
                int a = i + j, b = i + j + half;
                float tr = re[b] * wc - im[b] * ws;
                float ti = re[b] * ws + im[b] * wc;
                re[b] = re[a] - tr; im[b] = im[a] - ti;
                re[a] += tr;        im[a] += ti;
                k += step;
            }
        }
    }
    for (int i = 0; i < kFftBins; i++)
        mag[i] = sqrtf(re[i] * re[i] + im[i] * im[i]) / (float)kFftN;
}

// ---------------------------------------------------------------------------
// extractor helper: open `path`, find the first audio track, read format + tags.
// Returns the AMediaExtractor (track selected) + the track AMediaFormat via out
// params, or nullptr on failure. Caller deletes both + closes fd.
// ---------------------------------------------------------------------------
static const char* codecBadge(const char* mime) {
    if (!mime) return "AUDIO";
    if (!strcmp(mime, "audio/mpeg")) return "MP3";
    if (!strncmp(mime, "audio/mp4a", 10) || !strcmp(mime, "audio/aac")) return "AAC";
    if (!strcmp(mime, "audio/flac")) return "FLAC";
    if (!strcmp(mime, "audio/raw"))  return "PCM";
    if (!strcmp(mime, "audio/vorbis")) return "OGG";
    if (!strcmp(mime, "audio/opus"))   return "OPUS";
    if (!strcmp(mime, "audio/x-ms-wma")) return "WMA";
    if (!strcmp(mime, "audio/ac3"))    return "AC3";
    if (!strcmp(mime, "audio/eac3"))   return "EAC3";
    return "AUDIO";
}

// AC-3 (audio/ac3) is decoded by the vendored liba52, not AMediaCodec (the device has
// no AC-3 decoder). E-AC-3 / DTS are NOT handled by liba52 and remain unsupported.
static bool isAc3Mime(const char* mime) { return mime && !strcmp(mime, "audio/ac3"); }

static bool readMetaFromExtractor(AMediaExtractor* ex, int fd,
                                  NanoAudioPlayer::Meta& meta, int* trackOut,
                                  AMediaFormat** trackFmtOut, int wantTrack = -1) {
    int n = AMediaExtractor_getTrackCount(ex);
    int track = -1;
    AMediaFormat* tf = nullptr;
    const char* mime = nullptr;
    // If a specific audio track is requested and it really is audio, use it exactly.
    if (wantTrack >= 0 && wantTrack < n) {
        AMediaFormat* f = AMediaExtractor_getTrackFormat(ex, wantTrack);
        const char* m = nullptr;
        if (AMediaFormat_getString(f, AMEDIAFORMAT_KEY_MIME, &m) && m && !strncmp(m, "audio/", 6)) {
            track = wantTrack; tf = f; mime = m;
        } else {
            AMediaFormat_delete(f);
        }
    }
    // Otherwise (or on a bad request) fall back to the first audio track.
    for (int i = 0; track < 0 && i < n; i++) {
        AMediaFormat* f = AMediaExtractor_getTrackFormat(ex, i);
        const char* m = nullptr;
        if (AMediaFormat_getString(f, AMEDIAFORMAT_KEY_MIME, &m) && m && !strncmp(m, "audio/", 6)) {
            track = i; tf = f; mime = m; break;
        }
        AMediaFormat_delete(f);
    }
    if (track < 0) return false;

    int32_t rate = 0, chans = 0, brate = 0;
    AMediaFormat_getInt32(tf, AMEDIAFORMAT_KEY_SAMPLE_RATE, &rate);
    AMediaFormat_getInt32(tf, AMEDIAFORMAT_KEY_CHANNEL_COUNT, &chans);
    AMediaFormat_getInt32(tf, AMEDIAFORMAT_KEY_BIT_RATE, &brate);
    int64_t durUs = 0;
    AMediaFormat_getInt64(tf, AMEDIAFORMAT_KEY_DURATION, &durUs);
    meta.sampleRate = rate;
    meta.channels = chans;
    meta.bitRate = brate;
    meta.durationSec = durUs > 0 ? (double)durUs / 1e6 : 0.0;
    meta.codec = codecBadge(mime);

    // Container-level tags (ID3 / Vorbis comment / MP4 atoms). getFileFormat now
    // surfaces these (the libstagefright fix); read the full set for the Information
    // page.
    AMediaFormat* file = AMediaExtractor_getFileFormat(ex);
    if (file) {
        const char* s = nullptr;
        if (AMediaFormat_getString(file, AMEDIAFORMAT_KEY_TITLE, &s) && s) meta.title = s;
        if (AMediaFormat_getString(file, AMEDIAFORMAT_KEY_ARTIST, &s) && s) meta.artist = s;
        if (AMediaFormat_getString(file, AMEDIAFORMAT_KEY_ALBUM, &s) && s) meta.album = s;
        if (AMediaFormat_getString(file, AMEDIAFORMAT_KEY_GENRE, &s) && s) {
            meta.genre = s;
            // ID3v1 genres arrive as "(NN)Refinement" or bare "(NN)". Drop the numeric
            // code prefix when a readable name follows (e.g. "(36)Game" -> "Game").
            if (meta.genre.size() > 2 && meta.genre[0] == '(') {
                size_t cp = meta.genre.find(')');
                if (cp != std::string::npos && cp + 1 < meta.genre.size())
                    meta.genre = meta.genre.substr(cp + 1);
            }
        }
        if (AMediaFormat_getString(file, AMEDIAFORMAT_KEY_YEAR, &s) && s) meta.year = s;
        if (AMediaFormat_getString(file, AMEDIAFORMAT_KEY_CDTRACKNUMBER, &s) && s) meta.track = s;
        AMediaFormat_delete(file);
    }
    (void)fd;
    if (trackOut) *trackOut = track;
    if (trackFmtOut) *trackFmtOut = tf; else AMediaFormat_delete(tf);
    return true;
}

// ---------------------------------------------------------------------------
// NanoAudioPlayer
// ---------------------------------------------------------------------------
NanoAudioPlayer::NanoAudioPlayer() {}
NanoAudioPlayer::~NanoAudioPlayer() { release(); }

void NanoAudioPlayer::init() {
    if (mInited) return;
    fftInit();
    memset(mFftPublish, 0, sizeof(mFftPublish));
    mInited = true;
}

bool NanoAudioPlayer::probe(const std::string& path, Meta& out, int wantTrack) {
    int fd = ::open(path.c_str(), O_RDONLY);
    if (fd < 0) return false;
    struct stat st;
    if (fstat(fd, &st) != 0 || st.st_size <= 0) { ::close(fd); return false; }
    AMediaExtractor* ex = AMediaExtractor_new();
    media_status_t ms = AMediaExtractor_setDataSourceFd(ex, fd, 0, st.st_size);
    bool ok = false;
    if (ms == AMEDIA_OK) {
        AMediaFormat* tf = nullptr;
        ok = readMetaFromExtractor(ex, fd, out, nullptr, &tf, wantTrack);
        if (tf) AMediaFormat_delete(tf);
    }
    AMediaExtractor_delete(ex);
    ::close(fd);
    return ok && out.sampleRate > 0 && out.channels > 0;
}

bool NanoAudioPlayer::ensureStream(int rate, int channels) {
    std::lock_guard<std::mutex> lk(mStreamMutex);
    if (mStream && mStreamRate == rate && mStreamChans == channels) return true;
    // close existing
    if (mStream) {
        AAudioStream* s = static_cast<AAudioStream*>(mStream);
        AAudioStream_requestStop(s);
        AAudioStream_close(s);
        mStream = nullptr;
        mStarted = false;
    }
    AAudioStreamBuilder* b = nullptr;
    if (AAudio_createStreamBuilder(&b) != AAUDIO_OK || !b) return false;
    AAudioStreamBuilder_setDirection(b, AAUDIO_DIRECTION_OUTPUT);
    AAudioStreamBuilder_setFormat(b, AAUDIO_FORMAT_PCM_I16);
    AAudioStreamBuilder_setChannelCount(b, channels);
    AAudioStreamBuilder_setSampleRate(b, rate);
    AAudioStreamBuilder_setUsage(b, AAUDIO_USAGE_MEDIA);
    AAudioStreamBuilder_setContentType(b, AAUDIO_CONTENT_TYPE_MUSIC);
    AAudioStreamBuilder_setPerformanceMode(b, AAUDIO_PERFORMANCE_MODE_NONE);
    AAudioStreamBuilder_setDataCallback(b, [](AAudioStream*, void* u, void* data, int32_t n) {
        return (aaudio_data_callback_result_t)
            static_cast<NanoAudioPlayer*>(u)->fillAudio(data, n);
    }, this);
    AAudioStream* s = nullptr;
    aaudio_result_t r = AAudioStreamBuilder_openStream(b, &s);
    AAudioStreamBuilder_delete(b);
    if (r != AAUDIO_OK || !s) { ALOGW("NanoAudio: open stream failed (%d)", (int)r); return false; }
    mStream = s;
    mStreamRate = rate;
    mStreamChans = channels;
    mStarted = false;
    ALOGI("NanoAudio: stream open %dHz x%d", rate, channels);
    return true;
}

void NanoAudioPlayer::closeStream() {
    std::lock_guard<std::mutex> lk(mStreamMutex);
    if (mStream) {
        AAudioStream* s = static_cast<AAudioStream*>(mStream);
        AAudioStream_requestStop(s);
        AAudioStream_close(s);
        mStream = nullptr;
    }
    mStarted = false;
}

void NanoAudioPlayer::stopDecoder() {
    mDecodeStop = true;
    if (mDecodeThread.joinable()) mDecodeThread.join();
    mDecodeStop = false;
}

bool NanoAudioPlayer::open(const std::string& path, int audioTrackIndex) {
    init();
    mForcedAudioTrack = audioTrackIndex;   // -1 = first audio (default); >=0 = that extractor track
    Meta m;
    if (!probe(path, m, audioTrackIndex)) { ALOGW("NanoAudio: probe failed %s", path.c_str()); return false; }

    stopDecoder();                 // join any previous decode

    // Reset playback state.
    mHead = 0; mTail = 0;
    mEos = false;
    mFramesConsumed = 0;
    mSeekBaseFrames = 0;
    mPendingSeekUs = -1;
    mStopped = false;
    { std::lock_guard<std::mutex> lk(mMetaMutex); mMeta = m; }

    // AC-3 is decoded by liba52 and downmixed to stereo, so the output stream/ring are
    // 2-channel regardless of the coded channel count (which may be 5.1).
    bool ac3 = (m.codec == "AC3");
    int outChans = ac3 ? 2 : m.channels;
    if (!ensureStream(m.sampleRate, outChans)) return false;

    // Size the ring to ~3s of audio at the stream format (reuse if big enough).
    size_t need = (size_t)m.sampleRate * (size_t)outChans * 3;
    if (mRingCap < need) {
        mRing.assign(need, 0);
        mRingCap = need;
    }

    mCurrentPath = path;
    mDecodeStop = false;
    mDecodeThread = std::thread(&NanoAudioPlayer::decodeThreadFunc, this, path);
    return true;
}

void NanoAudioPlayer::play() {
    std::lock_guard<std::mutex> lk(mStreamMutex);
    if (mStream && !mStarted) {
        AAudioStream_requestStart(static_cast<AAudioStream*>(mStream));
        mStarted = true;
        mStopped = false;
    }
}

void NanoAudioPlayer::pause() {
    std::lock_guard<std::mutex> lk(mStreamMutex);
    if (mStream && mStarted) {
        AAudioStream_requestPause(static_cast<AAudioStream*>(mStream));
        mStarted = false;
    }
}

void NanoAudioPlayer::togglePause() {
    if (mStarted.load()) pause(); else play();
}

void NanoAudioPlayer::stop() {
    pause();
    seek(0.0);
    mStopped = true;
}

void NanoAudioPlayer::seek(double sec) {
    if (sec < 0) sec = 0;
    bool wasPlaying = mStarted.load();
    pause();                       // halt the callback
    stopDecoder();                 // join the decoder
    mHead = 0; mTail = 0;
    mEos = false;
    mSeekBaseFrames = (int64_t)(sec * mStreamRate);
    mFramesConsumed = 0;
    mPendingSeekUs = (int64_t)(sec * 1e6);
    mStopped = false;
    mDecodeStop = false;
    if (!mCurrentPath.empty())
        mDecodeThread = std::thread(&NanoAudioPlayer::decodeThreadFunc, this, mCurrentPath);
    if (wasPlaying) play();
}

bool NanoAudioPlayer::isPlaying() const { return mStarted.load() && !mStopped.load(); }
bool NanoAudioPlayer::isPaused() const { return !mStarted.load() && !mStopped.load(); }
bool NanoAudioPlayer::isStopped() const { return mStopped.load(); }

bool NanoAudioPlayer::ended() {
    return mEos.load() && (mHead.load() == mTail.load());
}

double NanoAudioPlayer::position() const {
    if (mStreamRate <= 0) return 0.0;
    int64_t f = mSeekBaseFrames.load() + mFramesConsumed.load();
    return (double)f / (double)mStreamRate;
}

double NanoAudioPlayer::duration() const {
    std::lock_guard<std::mutex> lk(mMetaMutex);
    return mMeta.durationSec;
}

void NanoAudioPlayer::setVolume(float v01) {
    if (v01 < 0) v01 = 0; if (v01 > 1) v01 = 1;
    mVol = v01;
}

NanoAudioPlayer::Meta NanoAudioPlayer::meta() const {
    std::lock_guard<std::mutex> lk(mMetaMutex);
    return mMeta;
}

void NanoAudioPlayer::release() {
    stopDecoder();
    closeStream();
    std::vector<int16_t>().swap(mRing);
    mRingCap = 0;
    mHead = 0; mTail = 0;
}

// ---- AAudio data callback (runs on the audio thread): lock-free ring drain ----
int32_t NanoAudioPlayer::fillAudio(void* audioData, int32_t numFrames) {
    int16_t* dst = static_cast<int16_t*>(audioData);
    int ch = mStreamChans;
    int32_t want = numFrames * ch;
    size_t head = mHead.load(std::memory_order_acquire);
    size_t tail = mTail.load(std::memory_order_relaxed);
    size_t avail = head - tail;
    int32_t toRead = (int32_t)std::min((size_t)want, avail);
    float vol = mVol.load(std::memory_order_relaxed);

    size_t idx = tail % mRingCap;
    for (int32_t i = 0; i < toRead; i++) {
        int v = (int)((float)mRing[idx] * vol);
        if (v > 32767) v = 32767; else if (v < -32768) v = -32768;
        dst[i] = (int16_t)v;
        if (++idx >= mRingCap) idx = 0;
    }
    for (int32_t i = toRead; i < want; i++) dst[i] = 0;   // underrun -> silence
    mTail.store(tail + toRead, std::memory_order_release);
    // Advance the presentation clock by the FULL request (numFrames), not just the
    // frames actually drained: silence emitted on underrun is still played time, so the
    // audio clock must track wall time. Counting only real frames froze the clock while
    // the decoder re-primed after a seek, which made the video A/V resync (drift > 0.3s)
    // reseek forever on slow-to-reopen containers (TS), thrashing AC-3 audio.
    if (ch > 0) mFramesConsumed.fetch_add(numFrames, std::memory_order_relaxed);

    // FFT tap: roll the emitted frames (downmixed to mono) into a 512-sample window
    // and publish a linear copy for getBands() on the UI thread.
    int frames = (ch > 0) ? toRead / ch : 0;
    for (int f = 0; f < frames; f++) {
        float s = 0.0f;
        for (int c = 0; c < ch; c++) s += (float)dst[f * ch + c];
        s = (s / (float)ch) / 32768.0f;
        mFftRoll[mFftRollPos] = s;
        if (++mFftRollPos >= kFftN) mFftRollPos = 0;
    }
    if (frames > 0) {
        int w = mFftWhich.load(std::memory_order_relaxed) ^ 1;
        int p = mFftRollPos;
        for (int i = 0; i < kFftN; i++) { mFftPublish[w][i] = mFftRoll[p]; if (++p >= kFftN) p = 0; }
        mFftWhich.store(w, std::memory_order_release);
    }
    return AAUDIO_CALLBACK_RESULT_CONTINUE;
}

void NanoAudioPlayer::getBands(Bands& out) {
    init();
    int w = mFftWhich.load(std::memory_order_acquire);
    float win[kFftN];
    // Hann window.
    for (int i = 0; i < kFftN; i++) {
        float h = 0.5f * (1.0f - cosf(2.0f * (float)M_PI * (float)i / (float)(kFftN - 1)));
        win[i] = mFftPublish[w][i] * h;
    }
    float mag[kFftBins];
    fftMagnitudes(win, mag);

    // Web AnalyserNode getByteFrequencyData mapping: smoothed magnitude -> dB ->
    // byte (minDb -100, maxDb -30) -> 0..1. Per-bin temporal smoothing 0.82.
    for (int i = 0; i < kFftBins; i++) {
        mFftSmooth[i] = 0.82f * mFftSmooth[i] + 0.18f * mag[i];
        float db = 20.0f * log10f(mFftSmooth[i] + 1e-9f);
        float b = (db + 100.0f) / 70.0f;     // map [-100,-30] dB -> [0,1]
        if (b < 0) b = 0; else if (b > 1) b = 1;
        out.bins[i] = b;
    }
    // mpBands(): bass = mean(bins[0,0.10n)), mid = [0.10n,0.45n), treble = rest.
    int bEnd = (int)(kFftBins * 0.10f);   // 25
    int mEnd = (int)(kFftBins * 0.45f);   // 115
    float sb = 0, sm = 0, st = 0;
    for (int i = 0; i < bEnd; i++) sb += out.bins[i];
    for (int i = bEnd; i < mEnd; i++) sm += out.bins[i];
    for (int i = mEnd; i < kFftBins; i++) st += out.bins[i];
    out.bass = bEnd > 0 ? sb / bEnd : 0;
    out.mid = (mEnd - bEnd) > 0 ? sm / (mEnd - bEnd) : 0;
    out.treble = (kFftBins - mEnd) > 0 ? st / (kFftBins - mEnd) : 0;
}

// ---- decoder worker: AMediaExtractor + AMediaCodec -> int16 PCM ring ----
void NanoAudioPlayer::decodeThreadFunc(std::string path) {
    int fd = ::open(path.c_str(), O_RDONLY);
    if (fd < 0) { mEos = true; return; }
    struct stat st;
    if (fstat(fd, &st) != 0 || st.st_size <= 0) { ::close(fd); mEos = true; return; }

    AMediaExtractor* ex = AMediaExtractor_new();
    if (AMediaExtractor_setDataSourceFd(ex, fd, 0, st.st_size) != AMEDIA_OK) {
        AMediaExtractor_delete(ex); ::close(fd); mEos = true; return;
    }
    Meta tmp;
    int track = -1;
    AMediaFormat* tf = nullptr;
    if (!readMetaFromExtractor(ex, fd, tmp, &track, &tf, mForcedAudioTrack)) {
        AMediaExtractor_delete(ex); ::close(fd); mEos = true; return;
    }
    AMediaExtractor_selectTrack(ex, track);

    const char* mime = nullptr;
    AMediaFormat_getString(tf, AMEDIAFORMAT_KEY_MIME, &mime);
    bool useAc3 = isAc3Mime(mime);   // decode AC-3 via liba52, not AMediaCodec
    AMediaCodec* codec = (!useAc3 && mime) ? AMediaCodec_createDecoderByType(mime) : nullptr;
    bool rawPcm = false;
    if (!useAc3 && (!codec || AMediaCodec_configure(codec, tf, nullptr, nullptr, 0) != AMEDIA_OK ||
        AMediaCodec_start(codec) != AMEDIA_OK)) {
        // No decoder (e.g. raw PCM WAV): copy extractor sample data straight through.
        if (codec) { AMediaCodec_delete(codec); codec = nullptr; }
        rawPcm = true;
    }

    // Honour a pending seek (from seek()).
    int64_t sk = mPendingSeekUs.exchange(-1);
    if (sk >= 0) {
        AMediaExtractor_seekTo(ex, sk, AMEDIAEXTRACTOR_SEEK_PREVIOUS_SYNC);
        if (codec) AMediaCodec_flush(codec);
    }

    int pcmEnc = kEncPcm16;
    bool sawInputEos = false;
    std::vector<int16_t> tmp16;

    auto writeRing = [&](const int16_t* s, size_t n) {
        size_t written = 0;
        while (written < n && !mDecodeStop.load()) {
            size_t head = mHead.load(std::memory_order_relaxed);
            size_t tail = mTail.load(std::memory_order_acquire);
            size_t freeS = mRingCap - (head - tail);
            if (freeS == 0) { usleep(2000); continue; }
            size_t chunk = std::min(n - written, freeS);
            size_t idx = head % mRingCap;
            size_t first = std::min(chunk, mRingCap - idx);
            memcpy(&mRing[idx], s + written, first * sizeof(int16_t));
            if (chunk > first) memcpy(&mRing[0], s + written + first, (chunk - first) * sizeof(int16_t));
            mHead.store(head + chunk, std::memory_order_release);
            written += chunk;
        }
    };

    if (useAc3) {
        // AC-3 -> int16 stereo via liba52. Each extractor access unit is one AC-3 frame.
        NanoAc3 ac3; ac3.init();
        const size_t kBuf = 8192;
        std::vector<uint8_t> buf(kBuf);
        std::vector<int16_t> pcm;
        ALOGI("NanoAudio: AC-3 (liba52) path, stream=%dHz/%dch", mStreamRate, mStreamChans);
        while (!mDecodeStop.load()) {
            int64_t sk2 = mPendingSeekUs.exchange(-1);
            if (sk2 >= 0) { AMediaExtractor_seekTo(ex, sk2, AMEDIAEXTRACTOR_SEEK_PREVIOUS_SYNC); ac3.reset(); }
            ssize_t got = AMediaExtractor_readSampleData(ex, buf.data(), kBuf);
            if (got <= 0) { mEos = true; break; }
            pcm.clear();
            int rate = 0;
            ac3.decode(buf.data(), (int)got, pcm, rate);
            if (!pcm.empty()) writeRing(pcm.data(), pcm.size());
            AMediaExtractor_advance(ex);
        }
    } else if (rawPcm) {
        // Direct passthrough of extractor PCM samples (16-bit assumed; WAV).
        const size_t kBuf = 16384;
        std::vector<uint8_t> buf(kBuf);
        while (!mDecodeStop.load()) {
            ssize_t got = AMediaExtractor_readSampleData(ex, buf.data(), kBuf);
            if (got <= 0) { mEos = true; break; }
            writeRing(reinterpret_cast<const int16_t*>(buf.data()), (size_t)got / sizeof(int16_t));
            AMediaExtractor_advance(ex);
        }
    } else {
        while (!mDecodeStop.load()) {
            if (!sawInputEos) {
                ssize_t ib = AMediaCodec_dequeueInputBuffer(codec, 2000);
                if (ib >= 0) {
                    size_t cap = 0;
                    uint8_t* in = AMediaCodec_getInputBuffer(codec, (size_t)ib, &cap);
                    ssize_t ss = in ? AMediaExtractor_readSampleData(ex, in, cap) : -1;
                    if (ss < 0) {
                        AMediaCodec_queueInputBuffer(codec, (size_t)ib, 0, 0, 0,
                                                     AMEDIACODEC_BUFFER_FLAG_END_OF_STREAM);
                        sawInputEos = true;
                    } else {
                        int64_t pts = AMediaExtractor_getSampleTime(ex);
                        AMediaCodec_queueInputBuffer(codec, (size_t)ib, 0, (size_t)ss,
                                                     pts < 0 ? 0 : (uint64_t)pts, 0);
                        AMediaExtractor_advance(ex);
                    }
                }
            }
            AMediaCodecBufferInfo info;
            ssize_t ob = AMediaCodec_dequeueOutputBuffer(codec, &info, 2000);
            if (ob >= 0) {
                if (info.flags & AMEDIACODEC_BUFFER_FLAG_END_OF_STREAM) {
                    AMediaCodec_releaseOutputBuffer(codec, (size_t)ob, false);
                    mEos = true; break;
                }
                size_t osz = 0;
                uint8_t* out = AMediaCodec_getOutputBuffer(codec, (size_t)ob, &osz);
                if (out && info.size > 0) {
                    const uint8_t* p = out + info.offset;
                    if (pcmEnc == kEncPcmFloat) {
                        size_t nf = (size_t)info.size / sizeof(float);
                        tmp16.resize(nf);
                        const float* fp = reinterpret_cast<const float*>(p);
                        for (size_t i = 0; i < nf; i++) {
                            float v = fp[i] * 32767.0f;
                            if (v > 32767) v = 32767; else if (v < -32768) v = -32768;
                            tmp16[i] = (int16_t)v;
                        }
                        writeRing(tmp16.data(), nf);
                    } else {
                        writeRing(reinterpret_cast<const int16_t*>(p), (size_t)info.size / sizeof(int16_t));
                    }
                }
                AMediaCodec_releaseOutputBuffer(codec, (size_t)ob, false);
            } else if (ob == AMEDIACODEC_INFO_OUTPUT_FORMAT_CHANGED) {
                AMediaFormat* of = AMediaCodec_getOutputFormat(codec);
                if (of) {
                    int32_t enc = 0;
                    if (AMediaFormat_getInt32(of, AMEDIAFORMAT_KEY_PCM_ENCODING, &enc)) pcmEnc = enc;
                    AMediaFormat_delete(of);
                }
            }
            // ob == TRY_AGAIN_LATER: loop.
        }
    }

    if (codec) { AMediaCodec_stop(codec); AMediaCodec_delete(codec); }
    AMediaFormat_delete(tf);
    AMediaExtractor_delete(ex);
    ::close(fd);
    if (!mDecodeStop.load()) mEos = true;   // natural end
}

} // namespace android
