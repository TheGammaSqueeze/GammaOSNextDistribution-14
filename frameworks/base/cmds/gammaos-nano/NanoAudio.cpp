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
#include "NanoTsDescramble.h"   // descramble scrambled-flagged .ts so its audio track extracts
#include "NanoHls.h"   // in-process HTTP/HLS fetcher (IPTV audio over http)
#include "NanoIcyDemux.h"   // custom streaming demuxer for radio Ogg/FLAC/AAC

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
    // Scrambled-flagged .ts (CA descriptor in the PMT): feed the descramble data source
    // so the audio track (e.g. AC-3) is visible to the extractor. Without this the probe
    // fails on a scrambled .ts and open() bails before the decode thread starts.
    AMediaDataSource* tsDs = nullptr; void* tsUd = nullptr; int tsPmt = -1;
    media_status_t ms;
    if (tsNeedsDescramble(fd, tsPmt)) {
        tsDs = tsMakeDataSource(fd, (off64_t)st.st_size, tsPmt, &tsUd);
        ms = tsDs ? AMediaExtractor_setDataSourceCustom(ex, tsDs) : AMEDIA_ERROR_UNKNOWN;
    } else {
        ms = AMediaExtractor_setDataSourceFd(ex, fd, 0, st.st_size);
    }
    bool ok = false;
    if (ms == AMEDIA_OK) {
        AMediaFormat* tf = nullptr;
        ok = readMetaFromExtractor(ex, fd, out, nullptr, &tf, wantTrack);
        if (tf) AMediaFormat_delete(tf);
    }
    AMediaExtractor_delete(ex);
    tsFreeDataSource(tsDs, tsUd);   // after the extractor that used it
    ::close(fd);
    return ok && out.sampleRate > 0 && out.channels > 0;
}

bool NanoAudioPlayer::ensureStream(int rate, int channels) {
    std::lock_guard<std::mutex> lk(mStreamMutex);
    if (mStream && mStreamRate == rate && mStreamChans == channels) return true;
    return openStreamLocked(rate, channels);
}

// Build (or rebuild) the AAudio output stream. Caller holds mStreamMutex. Installs the data
// AND error callbacks; the error callback drives route-change recovery (see recoverStream).
bool NanoAudioPlayer::openStreamLocked(int rate, int channels) {
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
    // Output-device change (headphone plug/unplug, BT connect) DISCONNECTS the stream; reopen
    // it on the new default device from a one-shot thread (must not close/reopen in the callback).
    AAudioStreamBuilder_setErrorCallback(b, [](AAudioStream*, void* u, aaudio_result_t err) {
        NanoAudioPlayer* self = static_cast<NanoAudioPlayer*>(u);
        if (err != AAUDIO_ERROR_DISCONNECTED || self->mShutdown.load()) return;
        bool expected = false;
        if (self->mRecovering.compare_exchange_strong(expected, true))
            std::thread(&NanoAudioPlayer::recoverStream, self).detach();
    }, this);
    AAudioStream* s = nullptr;
    aaudio_result_t r = AAudioStreamBuilder_openStream(b, &s);
    AAudioStreamBuilder_delete(b);
    if (r != AAUDIO_OK || !s) { ALOGW("NanoAudio: open stream failed (%d)", (int)r); return false; }
    mStream = s;
    mStreamRate = rate;
    mStreamChans = channels;
    mStarted = false;
    // clkdbg (symptom-C diagnosis): AAudio may negotiate a rate/channel count different from the
    // request; position() divides the callback frame count by mStreamRate, so a mismatch would skew
    // the A/V clock. Log both to confirm whether requested == device on this device.
    ALOGI("NanoAudio: stream open req=%dHz x%d, device=%dHz x%d", rate, channels,
          AAudioStream_getSampleRate(s), AAudioStream_getChannelCount(s));
    return true;
}

// Reopen the output stream after an AAUDIO_ERROR_DISCONNECTED (output device changed). Runs on
// a detached one-shot thread. Audioserver is alive for a route change so the AAudio calls return
// promptly (the audioserver-death case never reaches here - that error callback does not fire).
// The decode thread keeps the ring filled, so a fresh stream resumes playback on the new device.
void NanoAudioPlayer::recoverStream() {
    {
        std::lock_guard<std::mutex> lk(mStreamMutex);
        if (!mShutdown.load() && mStreamRate > 0 && mStreamChans > 0) {
            bool wasStarted = mStarted.load();
            if (openStreamLocked(mStreamRate, mStreamChans) && wasStarted && !mStopped.load()) {
                if (AAudioStream_requestStart(static_cast<AAudioStream*>(mStream)) == AAUDIO_OK)
                    mStarted = true;
            }
            ALOGI("NanoAudio: stream recovered after route change (playing=%d)", (int)mStarted.load());
        }
    }
    mRecovering.store(false);
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

void NanoAudioPlayer::icyFree() {
    if (mIcy) { mIcy->requestStop(); delete mIcy; mIcy = nullptr; }   // dtor joins the worker
}

bool NanoAudioPlayer::open(const std::string& path, int audioTrackIndex, bool radioStream) {
    init();
    mShutdown.store(false);   // re-arm route-change recovery for this playback
    mOpenFailed.store(false); // clear any prior open error
    mForcedAudioTrack = audioTrackIndex;   // -1 = first audio (default); >=0 = that extractor track
    mRadioStream = radioStream;            // continuous-stream hint for NanoHls (Internet Radio)

    icyFree();                     // drop any prior radio demuxer
    stopDecoder();                 // join any previous decode (thread no longer owns the extractor)

    // Internet Radio: the platform AMediaExtractor cannot open Ogg (Vorbis/FLAC/Opus) or ADTS-AAC
    // over a streaming source (0 tracks). Route those to NanoIcyDemux (custom demux -> AMediaCodec
    // -> fed PCM). MP3 / HLS / unknown fall through to the extractor below (which handles them).
    if (radioStream) {
        NanoIcyDemux::Codec c = NanoIcyDemux::probe(path);
        if (c == NanoIcyDemux::kOgg || c == NanoIcyDemux::kAac) {
            freeExtractor();
            mHead = 0; mTail = 0; mEos = false; mFramesConsumed = 0; mSeekBaseFrames = 0;
            mClockArmed = false; mPendingSeekUs = -1; mStopped = false;
            mCurrentPath = path;
            { std::lock_guard<std::mutex> lk(mMetaMutex); mMeta = Meta{};
              mMeta.codec = (c == NanoIcyDemux::kAac) ? "AAC" : "OGG"; }
            mIcy = new NanoIcyDemux(path, this, c);
            if (mIcy->start()) return true;   // worker calls openFed + play + feedPcm; sets openFailed on error
            delete mIcy; mIcy = nullptr;
            mOpenFailed.store(true);
            return false;
        }
        // c == kUnknown: fall through to the extractor (MP3 etc.)
    }

    // Parse the container + select the track ONCE here (fills the meta) and cache the
    // demuxer; the decode thread and every later seek reuse it without re-parsing.
    Meta m;
    if (!setupExtractor(path, audioTrackIndex, m)) {
        ALOGW("NanoAudio: open failed %s", path.c_str());
        mOpenFailed.store(true);
        freeExtractor();
        return false;
    }

    // Reset playback state.
    mHead = 0; mTail = 0;
    mEos = false;
    mFramesConsumed = 0;
    mSeekBaseFrames = 0;
    mClockArmed = false;
    mOriginPts.store(0.0); mPrerollMute.store(false);   // Step 1: clean slate (music default; video re-mutes)
    mPendingSeekUs = -1;
    mStopped = false;
    { std::lock_guard<std::mutex> lk(mMetaMutex); mMeta = m; }

    // AC-3 is decoded by liba52 and downmixed to stereo, so the output stream/ring are
    // 2-channel regardless of the coded channel count (which may be 5.1).
    bool ac3 = (m.codec == "AC3");
    int outChans = ac3 ? 2 : m.channels;
    bool streamUp = false;
    if (m.sampleRate > 0 && outChans > 0 && ensureStream(m.sampleRate, outChans)) {
        // Size the ring to ~3s of audio at the stream format (reuse if big enough).
        size_t need = (size_t)m.sampleRate * (size_t)outChans * 3;
        if (mRingCap < need) { mRing.assign(need, 0); mRingCap = need; }
        streamUp = true;
    }
    if (!streamUp) {
        // The container's rate/channels were missing or AAudio rejected them (e.g. mp4a-latm AAC
        // in a .mov reports a rate AAudio will not open). Defer the stream + ring to the decode
        // thread, which (re)opens with the TRUE values from the codec's first OUTPUT_FORMAT_CHANGED.
        // Do NOT fail here - that dropped the audio entirely. AC-3 (liba52) has no codec format
        // event, so for it fall back to the container rate (or 48k) when the stream is still down.
        ALOGI("NanoAudio: stream not up at open (container rate/ch %d/%d); deferring to decoder",
              m.sampleRate, outChans);
        if (ac3) {
            int r = m.sampleRate > 0 ? m.sampleRate : 48000;
            if (ensureStream(r, 2)) {
                size_t need = (size_t)r * 2 * 3;
                if (mRingCap < need) { mRing.assign(need, 0); mRingCap = need; }
            }
        }
    }

    mCurrentPath = path;
    mDecodeStop = false;
    mDecodeThread = std::thread(&NanoAudioPlayer::decodeThreadFunc, this, path);
    return true;
}

bool NanoAudioPlayer::openFed(int rate, int channels) {
    init();
    mShutdown.store(false);   // re-arm route-change recovery for this playback
    stopDecoder();                 // no decode thread runs in fed mode, but be safe
    freeExtractor();               // fed mode owns no extractor
    mHead = 0; mTail = 0;
    mEos = false;
    mFramesConsumed = 0;
    mSeekBaseFrames = 0;
    mClockArmed = false;
    mOriginPts.store(0.0); mPrerollMute.store(false);   // Step 1: clean slate (video re-mutes after openFed)
    mPendingSeekUs = -1;
    mStopped = false;
    { std::lock_guard<std::mutex> lk(mMetaMutex); mMeta = Meta{}; mMeta.sampleRate = rate; mMeta.channels = channels; }
    if (rate <= 0 || channels <= 0) return false;
    if (!ensureStream(rate, channels)) return false;
    size_t need = (size_t)rate * (size_t)channels * 3;     // ~3s ring
    if (mRingCap < need) { mRing.assign(need, 0); mRingCap = need; }
    mCurrentPath.clear();
    return true;
}

size_t NanoAudioPlayer::feedPcm(const int16_t* s, size_t n) {
    if (n == 0 || mRingCap == 0) return 0;
    size_t head = mHead.load(std::memory_order_relaxed);
    size_t tail = mTail.load(std::memory_order_acquire);
    size_t freeS = mRingCap - (head - tail);
    size_t chunk = std::min(n, freeS);
    if (chunk == 0) return 0;
    size_t idx = head % mRingCap;
    size_t first = std::min(chunk, mRingCap - idx);
    memcpy(&mRing[idx], s, first * sizeof(int16_t));
    if (chunk > first) memcpy(&mRing[0], s + first, (chunk - first) * sizeof(int16_t));
    mHead.store(head + chunk, std::memory_order_release);
    return chunk;
}

void NanoAudioPlayer::seekFed(double sec) {
    if (sec < 0) sec = 0;
    bool wasPlaying = mStarted.load();
    pause();                       // halt the callback before clearing the ring
    mHead = 0; mTail = 0;
    mEos = false;
    mSeekBaseFrames = (int64_t)(sec * mStreamRate);
    mFramesConsumed = 0;
    mClockArmed = false;
    mStopped = false;
    if (wasPlaying) play();
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

// Step 1 A/V start-together: un-mute and shift the clock into the video's PTS domain. Called once
// by the host the moment the picture has decoded its first frame, with that frame's PTS. The
// origin is the OFFSET between the video PTS domain and this engine's own 0-based position, so
// position() == videoFirstPts at the arm instant and tracks the video thereafter. For a 0-based
// path with a seek already applied (mp4/mov resume, .ts normalized) the raw position already
// equals the media time, so the offset is ~0; for live (raw MPEG-TS PTS, no seek) it is the raw
// first PTS. Computed while still muted (mFramesConsumed frozen), so the read is stable. Idempotent.
void NanoAudioPlayer::armOrigin(double videoFirstPtsSec) {
    double rawPos = 0.0;
    if (mStreamRate > 0)
        rawPos = (double)(mSeekBaseFrames.load() + mFramesConsumed.load()) / (double)mStreamRate;
    mOriginPts.store(videoFirstPtsSec - rawPos);
    mPrerollMute.store(false);
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
    mClockArmed = false;
    mPendingSeekUs = (int64_t)(sec * 1e6);
    mStopped = false;
    mDecodeStop = false;
    // Restart the decode thread on the SAME cached extractor: it only AMediaExtractor_seekTo's
    // (cheap), never re-parses the container. This makes A/V resync affordable on slow sources.
    if (mExtractor)
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
    if (mStreamRate <= 0) return mOriginPts.load();   // deferred-rate stream: report the origin, not 0
    int64_t f = mSeekBaseFrames.load() + mFramesConsumed.load();
    return mOriginPts.load() + (double)f / (double)mStreamRate;   // Step 1: origin-shift into the video PTS domain
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
    mShutdown.store(true);   // block any new route-change recovery from touching the stream
    // Wait out an in-flight recovery (bounded) so its detached thread never reopens after free.
    for (int i = 0; i < 100 && mRecovering.load(); i++) usleep(10000);
    icyFree();                           // stop + join the radio demuxer (its worker calls feedPcm)
    if (mExHls) mExHls->requestStop();   // unblock a decode thread parked in the HLS readAt before join
    stopDecoder();
    freeExtractor();               // decode thread joined: safe to drop the cached demuxer
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

    // Step 1 start-together preroll: emit SILENCE and FREEZE the clock until the host arms the
    // origin, but keep DRAINING the ring so the shared .ts/.avi demux worker (video then audio off
    // one read pointer) never wedges on a full audio ring. mClockArmed stays false -> clockArmed()
    // keeps the video on wall-clock pacing until real audio actually starts.
    if (mPrerollMute.load(std::memory_order_relaxed)) {
        for (int32_t i = 0; i < want; i++) dst[i] = 0;
        // drain (fed/live): discard the muted audio so the shared .ts/.avi worker never wedges on a
        // full ring / the stream stays at the live edge. HOLD (mp4/mov separate decoder): leave the
        // ring intact (the decoder back-pressures) so playback begins at content time 0 in lock-step
        // with the picture's first frame, not skipped ahead by a long video cold start (the bbb
        // audio-leads-video desync). Either way do NOT count -> the clock is held at origin until arm.
        if (mPrerollDrain.load(std::memory_order_relaxed))
            mTail.store(tail + toRead, std::memory_order_release);
        return AAUDIO_CALLBACK_RESULT_CONTINUE;
    }
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
    // Presentation clock. Once real audio has flowed since the last seek/open, silence
    // emitted on a mid-stream underrun is still played time, so advance by the FULL request
    // (counting only real frames would freeze the clock on underrun). But BEFORE the first
    // real frame (the decoder is still priming / a slow container like TS is re-parsing) the
    // stream has not actually started: advancing on that startup silence races the clock
    // seconds ahead of the picture and makes the video A/V resync reseek forever. So hold the
    // clock until real audio appears, then arm it.
    if (ch > 0) {
        if (toRead > 0) mClockArmed.store(true, std::memory_order_relaxed);
        // Advance the presentation clock ONLY by real content frames played (toRead/ch), never by
        // underrun (toRead < want) OR startup priming silence. Counting the full request on a
        // mid-stream underrun advanced position() through silence that was not real content, so the
        // picture (which paces to this clock) slowly drifted AHEAD of the audio over a long clip.
        // With the picture-holds-for-audio pacing, a brief underrun now holds both together instead
        // of accumulating drift. Before the first real frame toRead is 0, so the clock still does
        // not race on startup silence (mClockArmed gates the video slew the same way).
        mFramesConsumed.fetch_add(toRead / ch, std::memory_order_relaxed);
    }

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

// Parse the container + select the audio track once, caching the demuxer so seeks (which
// restart the decode thread) never re-parse. Must run with the decode thread stopped.
bool NanoAudioPlayer::setupExtractor(const std::string& path, int wantTrack, Meta& outMeta) {
    freeExtractor();                                  // drop any previous cache
    // Network stream (http/https, incl. HLS .m3u8): point the extractor at the URL; the
    // platform fetches the manifest/segments. No fd, no descramble (IPTV is clear).
    bool isUrl = path.compare(0, 7, "http://") == 0 || path.compare(0, 8, "https://") == 0;
    if (isUrl) {
        // Native AMediaExtractor cannot fetch http(s) (UNSUPPORTED). Fetch in-process via
        // curl/HLS and feed the bytes through a custom data source (mirrors the video path).
        // Internet Radio (mRadioStream) hints continuous streaming + bounded disk in NanoHls.
        mExHls = new NanoHls(path, mRadioStream);
        if (!mExHls->start()) { delete mExHls; mExHls = nullptr; return false; }
        if (mRadioStream) {
            // Pre-buffer before the container probe: a raw-codec icecast stream (Ogg/FLAC/AAC)
            // needs its full setup headers present when AMediaExtractor sniffs + enumerates
            // tracks, otherwise the probe races the first byte and finds 0 tracks. Wait up to
            // ~3s for 256 KB (plenty for any header); abort early on teardown.
            for (int i = 0; i < 300 && mExHls->produced() < 256 * 1024; i++) {
                if (mDecodeStop.load()) { delete mExHls; mExHls = nullptr; return false; }
                usleep(10000);
            }
        }
        AMediaExtractor* ex = AMediaExtractor_new();
        media_status_t dst = AMediaExtractor_setDataSourceCustom(ex, mExHls->dataSource());
        if (dst != AMEDIA_OK) {
            ALOGW("NanoAudio: setDataSourceCustom failed (%d) for %s", (int)dst, path.c_str());
            AMediaExtractor_delete(ex); delete mExHls; mExHls = nullptr; return false;
        }
        int track = -1;
        AMediaFormat* tf = nullptr;
        if (!readMetaFromExtractor(ex, -1, outMeta, &track, &tf, wantTrack)) {
            ALOGW("NanoAudio: no audio track in stream %s (tracks=%zu)",
                  path.c_str(), AMediaExtractor_getTrackCount(ex));
            AMediaExtractor_delete(ex); delete mExHls; mExHls = nullptr; return false;
        }
        AMediaExtractor_selectTrack(ex, track);
        const char* mime = nullptr;
        AMediaFormat_getString(tf, AMEDIAFORMAT_KEY_MIME, &mime);
        mExtractor = ex; mExFormat = tf; mExTsDs = nullptr; mExTsUd = nullptr; mExFd = -1;
        mExTrack = track; mExPath = path; mExUseAc3 = isAc3Mime(mime);
        return true;
    }
    int fd = ::open(path.c_str(), O_RDONLY);
    if (fd < 0) return false;
    struct stat st;
    if (fstat(fd, &st) != 0 || st.st_size <= 0) { ::close(fd); return false; }

    AMediaExtractor* ex = AMediaExtractor_new();
    // Scrambled-flagged .ts (CA descriptor in the PMT): feed the descramble data source so
    // the audio track (e.g. AC-3) is extractable, the same source the video path uses.
    AMediaDataSource* tsDs = nullptr; void* tsUd = nullptr; int tsPmt = -1;
    media_status_t dst;
    if (tsNeedsDescramble(fd, tsPmt)) {
        tsDs = tsMakeDataSource(fd, (off64_t)st.st_size, tsPmt, &tsUd);
        dst = tsDs ? AMediaExtractor_setDataSourceCustom(ex, tsDs) : AMEDIA_ERROR_UNKNOWN;
    } else {
        dst = AMediaExtractor_setDataSourceFd(ex, fd, 0, st.st_size);
    }
    if (dst != AMEDIA_OK) {
        AMediaExtractor_delete(ex); tsFreeDataSource(tsDs, tsUd); ::close(fd); return false;
    }
    int track = -1;
    AMediaFormat* tf = nullptr;
    if (!readMetaFromExtractor(ex, fd, outMeta, &track, &tf, wantTrack)) {
        AMediaExtractor_delete(ex); tsFreeDataSource(tsDs, tsUd); ::close(fd); return false;
    }
    AMediaExtractor_selectTrack(ex, track);
    const char* mime = nullptr;
    AMediaFormat_getString(tf, AMEDIAFORMAT_KEY_MIME, &mime);

    mExtractor = ex; mExFormat = tf; mExTsDs = tsDs; mExTsUd = tsUd; mExFd = fd;
    mExTrack = track; mExPath = path; mExUseAc3 = isAc3Mime(mime);
    return true;
}

void NanoAudioPlayer::freeExtractor() {
    if (mExFormat) { AMediaFormat_delete(static_cast<AMediaFormat*>(mExFormat)); mExFormat = nullptr; }
    if (mExtractor) { AMediaExtractor_delete(static_cast<AMediaExtractor*>(mExtractor)); mExtractor = nullptr; }
    tsFreeDataSource(static_cast<AMediaDataSource*>(mExTsDs), mExTsUd);   // null-safe
    mExTsDs = nullptr; mExTsUd = nullptr;
    if (mExHls) { delete mExHls; mExHls = nullptr; }   // after the extractor (it read through the source)
    if (mExFd >= 0) { ::close(mExFd); mExFd = -1; }
    mExPath.clear(); mExTrack = -1; mExUseAc3 = false;
}

// ---- decoder worker: AMediaExtractor + AMediaCodec -> int16 PCM ring ----
// Uses the cached extractor/format built by setupExtractor() (parsed once in open()).
// A seek restarts this thread, which only seeks the existing extractor - no re-parse.
void NanoAudioPlayer::decodeThreadFunc(std::string /*path*/) {
    AMediaExtractor* ex = static_cast<AMediaExtractor*>(mExtractor);
    AMediaFormat* tf = static_cast<AMediaFormat*>(mExFormat);
    if (!ex || !tf) { mEos = true; return; }

    const char* mime = nullptr;
    AMediaFormat_getString(tf, AMEDIAFORMAT_KEY_MIME, &mime);
    bool useAc3 = mExUseAc3;          // decode AC-3 via liba52, not AMediaCodec
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
                    // The codec's output format carries the true rate/channels even when the
                    // container omitted them (e.g. mp4a-latm AAC), so (re)open the AAudio stream
                    // and ring here if they are not open yet or changed. closeStream() first so
                    // the ring resize never races the audio callback.
                    int32_t r2 = 0, c2 = 0;
                    AMediaFormat_getInt32(of, AMEDIAFORMAT_KEY_SAMPLE_RATE, &r2);
                    AMediaFormat_getInt32(of, AMEDIAFORMAT_KEY_CHANNEL_COUNT, &c2);
                    if (r2 > 0 && c2 > 0 && (!mStream || mStreamRate != r2 || mStreamChans != c2)) {
                        ALOGI("NanoAudio: codec output format %dHz x%d; (re)opening stream", r2, c2);
                        closeStream();
                        size_t need = (size_t)r2 * (size_t)c2 * 3;
                        if (mRingCap < need) { mRing.assign(need, 0); mRingCap = need; }
                        ensureStream(r2, c2);
                        { std::lock_guard<std::mutex> lk(mMetaMutex); mMeta.sampleRate = r2; mMeta.channels = c2; }
                    }
                    AMediaFormat_delete(of);
                }
            }
            // ob == TRY_AGAIN_LATER: loop.
        }
    }

    // Drop only the codec; the extractor/format/datasource/fd are cached members reused on
    // the next seek-restart (freed in freeExtractor() once the thread is stopped for good).
    if (codec) { AMediaCodec_stop(codec); AMediaCodec_delete(codec); }
    if (!mDecodeStop.load()) mEos = true;   // natural end
}

} // namespace android
