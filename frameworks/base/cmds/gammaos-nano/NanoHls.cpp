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

#include "NanoHls.h"

#include <algorithm>
#include <cctype>
#include <fcntl.h>
#include <linux/falloc.h>
#include <stdio.h>
#include <string.h>
#include <sys/stat.h>
#include <sys/wait.h>
#include <unistd.h>
#include <vector>

#include <android/log.h>
#define HLOGI(...) __android_log_print(ANDROID_LOG_INFO, "GammaOSNano", __VA_ARGS__)
#define HLOGW(...) __android_log_print(ANDROID_LOG_WARN, "GammaOSNano", __VA_ARGS__)

namespace android {

static std::atomic<int> sHlsCounter{0};
static const int64_t kHlsCapBytes = 256LL * 1024 * 1024;   // stop a long live session at ~256 MB
static const int64_t kRadioCapBytes = 64LL * 1024 * 1024 * 1024;  // ~effectively unlimited (disk bounded by hole-punch)
static const int kSegTimeoutSec = 20;
static const int kDirectMaxTimeSec = 6 * 60 * 60;          // one continuous radio connection (6 h)
static const int kFirstByteDeadlineMs = 25000;             // open fails if no bytes in 25 s
// Hole-punch geometry for continuous radio: never punch the first KEEP_HEAD bytes (header sniff
// zone), keep MARGIN bytes behind the reader, and punch in STEP-sized chunks to limit syscalls.
static const int64_t kPunchKeepHead = 1LL * 1024 * 1024;
static const int64_t kPunchMargin   = 4LL * 1024 * 1024;
static const int64_t kPunchStep     = 8LL * 1024 * 1024;

// ---- URL helpers ----------------------------------------------------------
static std::string urlSchemeHost(const std::string& url) {
    size_t s = url.find("://");
    if (s == std::string::npos) return std::string();
    size_t h = url.find('/', s + 3);
    return (h == std::string::npos) ? url : url.substr(0, h);
}
static std::string urlDir(const std::string& url) {
    size_t q = url.find('?');
    std::string base = (q == std::string::npos) ? url : url.substr(0, q);
    size_t sl = base.rfind('/');
    return (sl == std::string::npos) ? base : base.substr(0, sl + 1);
}
// Resolve a possibly-relative reference against the playlist URL.
static std::string urlResolve(const std::string& playlistUrl, const std::string& ref) {
    if (ref.compare(0, 7, "http://") == 0 || ref.compare(0, 8, "https://") == 0) return ref;
    if (ref.compare(0, 2, "//") == 0) {
        size_t s = playlistUrl.find("://");
        std::string scheme = (s == std::string::npos) ? "https" : playlistUrl.substr(0, s);
        return scheme + ":" + ref;
    }
    if (!ref.empty() && ref[0] == '/') return urlSchemeHost(playlistUrl) + ref;
    return urlDir(playlistUrl) + ref;
}
static bool looksLikePlaylist(const std::string& s) {
    return s.find("#EXTM3U") != std::string::npos;
}
static bool urlSafe(const std::string& u) {
    // Reject URLs containing a single quote (would break the shell-quoted curl command).
    return !u.empty() && u.find('\'') == std::string::npos && u.size() < 4000;
}
// Lowercased file extension of the URL's last path segment (no query/fragment); "" if none.
static std::string urlPathExt(const std::string& url) {
    size_t s = url.find("://");
    std::string p = (s == std::string::npos) ? url : url.substr(s + 3);
    size_t cut = p.find_first_of("?#");
    if (cut != std::string::npos) p = p.substr(0, cut);
    size_t sl = p.rfind('/');
    std::string seg = (sl == std::string::npos) ? p : p.substr(sl + 1);
    size_t dot = seg.rfind('.');
    if (dot == std::string::npos) return std::string();
    std::string e = seg.substr(dot + 1);
    for (char& c : e) c = (char)tolower((unsigned char)c);
    return e;
}
// Pull the first playable stream URL out of a .pls / .m3u / .asx station-pointer body. Returns
// an absolute URL (resolved against baseUrl) or "" if none is found.
static std::string resolveStationPointer(const std::string& body, const std::string& baseUrl) {
    size_t pos = 0;
    while (pos < body.size()) {
        size_t eol = body.find('\n', pos);
        if (eol == std::string::npos) eol = body.size();
        std::string line = body.substr(pos, eol - pos);
        pos = eol + 1;
        while (!line.empty() && (line.back() == '\r' || line.back() == ' ' || line.back() == '\t')) line.pop_back();
        size_t b = line.find_first_not_of(" \t");
        if (b == std::string::npos) continue;
        line = line.substr(b);
        if (line.empty()) continue;
        // PLS: FileN=URL
        if (line.size() > 4 && (line.compare(0, 4, "File") == 0 || line.compare(0, 4, "file") == 0)) {
            size_t eq = line.find('=');
            if (eq != std::string::npos) {
                std::string v = line.substr(eq + 1);
                if (v.compare(0, 4, "http") == 0) return urlResolve(baseUrl, v);
            }
            continue;
        }
        // ASX: <ref href="URL"/>
        size_t href = line.find("href=");
        if (href == std::string::npos) href = line.find("HREF=");
        if (href != std::string::npos) {
            size_t q1 = line.find_first_of("\"'", href);
            if (q1 != std::string::npos) {
                size_t q2 = line.find_first_of("\"'", q1 + 1);
                if (q2 != std::string::npos) {
                    std::string v = line.substr(q1 + 1, q2 - q1 - 1);
                    if (v.compare(0, 4, "http") == 0) return urlResolve(baseUrl, v);
                }
            }
            continue;
        }
        // Plain M3U: a bare http(s) line (skip #EXTINF/comments).
        if (line[0] == '#') continue;
        if (line.compare(0, 4, "http") == 0) return urlResolve(baseUrl, line);
    }
    return std::string();
}

// curl a URL's body into a string (text: playlists). Empty on failure.
static std::string curlText(const std::string& url) {
    if (!urlSafe(url)) return std::string();
    std::string cmd = "/system/bin/curl -s -L --max-time 15 -A 'gammaos-nano-iptv' '" + url + "' 2>/dev/null";
    FILE* f = popen(cmd.c_str(), "r");
    if (!f) return std::string();
    std::string out;
    char buf[4096];
    size_t n;
    while ((n = fread(buf, 1, sizeof(buf), f)) > 0) out.append(buf, n);
    pclose(f);
    return out;
}

// ---- data-source trampolines ----------------------------------------------
static ssize_t hlsReadAt(void* u, off64_t offset, void* buffer, size_t size) {
    return static_cast<NanoHls*>(u)->readAt(offset, buffer, size);
}
static ssize_t hlsGetSize(void* u) { return static_cast<NanoHls*>(u)->getSize(); }
static void    hlsClose(void* /*u*/) {}   // fds/source freed in ~NanoHls after the extractor is gone

// ---------------------------------------------------------------------------
NanoHls::NanoHls(const std::string& url, bool directHint)
    : mUrl(url), mDirectHint(directHint),
      mCapBytes(directHint ? kRadioCapBytes : kHlsCapBytes) {}

NanoHls::~NanoHls() {
    mStop.store(true);
    if (mThread.joinable()) mThread.join();
    if (mSource) { AMediaDataSource_delete(mSource); mSource = nullptr; }
    if (mReadFd >= 0) { close(mReadFd); mReadFd = -1; }
    if (mWriteFd >= 0) { close(mWriteFd); mWriteFd = -1; }
    if (!mTmpPath.empty()) unlink(mTmpPath.c_str());
}

bool NanoHls::start() {
    int id = sHlsCounter.fetch_add(1);
    mTmpPath = "/data/system/nano_hls_" + std::to_string((long)getpid()) + "_" + std::to_string(id) + ".ts";
    mWriteFd = open(mTmpPath.c_str(), O_WRONLY | O_CREAT | O_TRUNC, 0644);
    if (mWriteFd < 0) { HLOGW("NanoHls: cannot create %s", mTmpPath.c_str()); return false; }
    mReadFd = open(mTmpPath.c_str(), O_RDONLY);
    if (mReadFd < 0) { close(mWriteFd); mWriteFd = -1; unlink(mTmpPath.c_str()); return false; }

    mSource = AMediaDataSource_new();
    AMediaDataSource_setUserdata(mSource, this);
    AMediaDataSource_setReadAt(mSource, hlsReadAt);
    AMediaDataSource_setGetSize(mSource, hlsGetSize);
    AMediaDataSource_setClose(mSource, hlsClose);

    mStarted = true;
    mThread = std::thread(&NanoHls::fetchThread, this);
    return true;
}

ssize_t NanoHls::readAt(off64_t offset, void* buffer, size_t size) {
    if (size == 0) return 0;
    int waitedMs = 0;
    for (;;) {
        if (mStop.load()) return -1;
        int64_t prod = mProduced.load();
        // For a continuous radio stream, wait for the WHOLE requested chunk before returning so
        // the raw-codec container sniffers/parsers (Ogg/FLAC/ADTS-AAC) get complete reads - a
        // partial read makes them bail and the open fails. For other sources (e.g. the MPEG-TS
        // IPTV path, which resyncs on partial data) one available byte is enough. A short bound
        // keeps live-frontier playback reads from ever stalling on a too-large request.
        bool ready = mDirectHint ? (prod >= offset + (int64_t)size) : (offset < prod);
        if (ready) break;
        if (mDone.load()) { if (offset < prod) break; return -1; }   // EOF: serve the remainder
        if (mDirectHint && offset < prod && waitedMs >= 1500) break; // have some data, don't stall
        usleep(5000); waitedMs += 5;
        if (prod == 0 && waitedMs >= kFirstByteDeadlineMs) return -1;  // never connected
    }
    int64_t prod = mProduced.load();
    size_t avail = (size_t)(prod - offset);
    size_t n = avail < size ? avail : size;
    ssize_t got = pread(mReadFd, buffer, n, (off_t)offset);
    if (got > 0) {
        int64_t end = offset + got;
        int64_t cur = mReadFrontier.load();
        while (end > cur && !mReadFrontier.compare_exchange_weak(cur, end)) { /* retry */ }
    }
    return got > 0 ? got : (mDone.load() ? -1 : 0);
}

// Punch out the consumed prefix of the temp file so a multi-hour radio session does not grow it
// without bound on /data. Keeps the first kPunchKeepHead bytes (header sniff zone) and a margin
// behind the reader; the logical file size is preserved (KEEP_SIZE) so read offsets stay valid.
void NanoHls::maybePunch() {
    if (!mDirectHint) return;
    int64_t target = mReadFrontier.load() - kPunchMargin;
    int64_t start = (mPunchedTo < kPunchKeepHead) ? kPunchKeepHead : mPunchedTo;
    if (target - start < kPunchStep) return;            // punch in steps to limit syscalls
    int64_t len = target - start;
    if (fallocate(mWriteFd, FALLOC_FL_PUNCH_HOLE | FALLOC_FL_KEEP_SIZE, start, len) == 0)
        mPunchedTo = target;
}

int NanoHls::appendBytesFromCurl(const std::string& url, int maxTimeSec, bool icyAudio) {
    if (!urlSafe(url)) return -1;
    // For a direct icecast/shoutcast radio stream: allow non-HTTP "ICY 200 OK" status lines
    // (--http0.9) and ask for no interleaved ICY metadata (clean audio bytes).
    std::string icy = icyAudio ? " --http0.9 -H 'Icy-MetaData: 0'" : "";
    std::string cmd = "/system/bin/curl -s -L --max-time " + std::to_string(maxTimeSec)
        + icy + " -A 'gammaos-nano-iptv' '" + url + "' 2>/dev/null";
    FILE* f = popen(cmd.c_str(), "r");
    if (!f) return -1;
    char buf[65536];
    size_t n;
    while (!mStop.load() && (n = fread(buf, 1, sizeof(buf), f)) > 0) {
        size_t off = 0;
        while (off < n) {
            ssize_t w = write(mWriteFd, buf + off, n - off);
            if (w <= 0) break;
            off += (size_t)w;
        }
        mProduced.fetch_add((int64_t)off);
        maybePunch();
        if (mProduced.load() > mCapBytes) break;   // closing the pipe (pclose) sends curl SIGPIPE
    }
    int st = pclose(f);                            // closes the read end -> curl exits on EPIPE
    return (st == -1 || !WIFEXITED(st)) ? -1 : WEXITSTATUS(st);
}

bool NanoHls::fetchSegment(const std::string& url) {
    int64_t before = mProduced.load();
    appendBytesFromCurl(url, kSegTimeoutSec, false);
    return mProduced.load() > before;
}

void NanoHls::streamDirect(const std::string& url) {
    // A non-playlist URL: stream the raw media straight through (progressive .ts/.mp4), one pass.
    appendBytesFromCurl(url, kSegTimeoutSec, false);
    setDone();
}

void NanoHls::streamContinuous(const std::string& url) {
    // Continuous progressive source (icecast/shoutcast radio). One long connection; reconnect on
    // an unexpected drop so a brief blip does not end playback. A clean EOF (curl rc 0 = finite
    // resource / server close) or our 6 h cap (rc 28) ends it; repeated quick failures give up.
    int fails = 0;
    while (!mStop.load() && mProduced.load() <= mCapBytes) {
        int64_t before = mProduced.load();
        int rc = appendBytesFromCurl(url, kDirectMaxTimeSec, true);
        if (mStop.load()) break;
        if (rc == 0 || rc == 28) break;                 // clean end / long-cap reached -> done
        int64_t got = mProduced.load() - before;
        if (got >= 64 * 1024) fails = 0;                // delivered audio then dropped -> reconnect
        else if (++fails >= 6) break;                   // repeated quick failures -> give up
        for (int s = 0; s < 2 && !mStop.load(); s++) usleep(500000);   // ~1 s backoff
    }
    setDone();
}

void NanoHls::fetchThread() {
    // Internet Radio (directHint): resolve a station pointer, stream a direct icecast URL
    // continuously without the wasteful text probe, and let .m3u8 fall through to HLS below.
    if (mDirectHint) {
        std::string target = mUrl;
        std::string ext = urlPathExt(target);
        if (ext == "pls" || ext == "asx" || ext == "m3u") {
            std::string body = curlText(target);
            std::string r = body.empty() ? std::string() : resolveStationPointer(body, target);
            if (!r.empty()) { target = r; ext = urlPathExt(target); }
            else if (looksLikePlaylist(body)) { /* an EXTM3U HLS .m3u: fall through to HLS */ ext = "m3u8"; }
        }
        if (ext != "m3u8") { streamContinuous(target); return; }
        mUrl = target;   // HLS station: continue into the HLS handler below using the resolved URL
    }

    std::string playlistUrl = mUrl;
    std::string text = curlText(playlistUrl);
    if (text.empty()) { HLOGW("NanoHls: empty fetch for %s", mUrl.c_str()); setDone(); return; }

    if (!looksLikePlaylist(text)) {
        if (mDirectHint) streamContinuous(mUrl); else streamDirect(mUrl);   // direct media stream
        return;
    }

    // Master playlist: pick the lowest-bandwidth variant (most reliable on this device).
    if (text.find("#EXT-X-STREAM-INF") != std::string::npos) {
        std::string bestUrl; long bestBw = -1;
        size_t pos = 0;
        while (pos < text.size()) {
            size_t eol = text.find('\n', pos);
            if (eol == std::string::npos) eol = text.size();
            std::string line = text.substr(pos, eol - pos);
            pos = eol + 1;
            if (line.compare(0, 18, "#EXT-X-STREAM-INF:") != 0) continue;
            long bw = 0;
            size_t b = line.find("BANDWIDTH=");
            if (b != std::string::npos) bw = atol(line.c_str() + b + 10);
            // next non-blank, non-# line is the variant URL
            std::string vurl;
            while (pos < text.size()) {
                size_t e2 = text.find('\n', pos);
                if (e2 == std::string::npos) e2 = text.size();
                std::string l2 = text.substr(pos, e2 - pos);
                pos = e2 + 1;
                while (!l2.empty() && (l2.back() == '\r' || l2.back() == ' ')) l2.pop_back();
                if (l2.empty() || l2[0] == '#') continue;
                vurl = l2; break;
            }
            if (vurl.empty()) continue;
            if (bestBw < 0 || (bw > 0 && bw < bestBw)) { bestBw = bw > 0 ? bw : 1; bestUrl = vurl; }
        }
        if (bestUrl.empty()) { HLOGW("NanoHls: no variant in master"); setDone(); return; }
        playlistUrl = urlResolve(mUrl, bestUrl);
        text = curlText(playlistUrl);
        if (text.empty() || !looksLikePlaylist(text)) { setDone(); return; }
    }

    // Media playlist loop (VOD: fetch all then done; live: re-poll for new segments).
    int64_t lastSeq = -1;
    int emptyPolls = 0;
    for (;;) {
        if (mStop.load()) break;
        if (mProduced.load() > mCapBytes) { HLOGI("NanoHls: byte cap reached"); break; }

        bool encrypted = false, endList = false;
        int64_t mediaSeq = 0;
        std::vector<std::string> segs;
        {
            size_t pos = 0; int idx = 0;
            while (pos < text.size()) {
                size_t eol = text.find('\n', pos);
                if (eol == std::string::npos) eol = text.size();
                std::string line = text.substr(pos, eol - pos);
                pos = eol + 1;
                while (!line.empty() && (line.back() == '\r' || line.back() == ' ' || line.back() == '\t')) line.pop_back();
                if (line.empty()) continue;
                if (line[0] == '#') {
                    if (line.compare(0, 22, "#EXT-X-MEDIA-SEQUENCE:") == 0)
                        mediaSeq = atoll(line.c_str() + 22);
                    else if (line.compare(0, 11, "#EXT-X-KEY:") == 0 && line.find("METHOD=NONE") == std::string::npos
                             && line.find("METHOD=") != std::string::npos)
                        encrypted = true;
                    else if (line.compare(0, 14, "#EXT-X-ENDLIST") == 0)
                        endList = true;
                    continue;
                }
                segs.push_back(line);   // a segment URI (paired by position with its #EXTINF)
                idx++;
            }
        }

        if (encrypted && lastSeq < 0) { HLOGW("NanoHls: AES-encrypted stream unsupported"); break; }

        // Live media-sequence reset (server failover / encoder restart re-numbers from a
        // lower base): if the WHOLE new window sits below our watermark, none of its
        // segments would ever be fetched and the stream silently stalls. Adopt the new
        // frontier so playback continues. (A window that merely slid forward past us keeps
        // maxSeq >= lastSeq and is handled normally as a gap.)
        if (lastSeq >= 0 && !segs.empty() && (mediaSeq + (int64_t)segs.size() - 1) < lastSeq) {
            HLOGW("NanoHls: media-sequence reset (%lld < %lld), re-syncing",
                  (long long)(mediaSeq + (int64_t)segs.size() - 1), (long long)lastSeq);
            lastSeq = mediaSeq - 1;
        }

        bool fetchedAny = false;
        for (size_t i = 0; i < segs.size(); i++) {
            if (mStop.load()) break;
            int64_t seq = mediaSeq + (int64_t)i;
            if (seq <= lastSeq) continue;
            std::string segUrl = urlResolve(playlistUrl, segs[i]);
            fetchSegment(segUrl);
            lastSeq = seq;
            fetchedAny = true;
            if (mProduced.load() > mCapBytes) break;
        }

        if (endList) break;            // VOD complete
        if (mStop.load()) break;
        // Live: re-poll the media playlist for new segments.
        if (!fetchedAny) { if (++emptyPolls > 40) break; }   // ~2 min with nothing new -> give up
        else emptyPolls = 0;
        // pace at ~2s (or one target-duration); short enough for live, light on the server
        for (int s = 0; s < 4 && !mStop.load(); s++) usleep(500000);
        std::string nt = curlText(playlistUrl);
        if (!nt.empty() && looksLikePlaylist(nt)) text = nt;
    }
    setDone();
    // Unlink the temp file now that no more bytes will be written. The reader fd stays
    // valid (the inode lives until ~NanoHls closes the fds), so this never disrupts an
    // in-flight teardown read - it just guarantees the file is removed promptly even if the
    // decoder's async teardown (which would otherwise call ~NanoHls) is slow on this device.
    if (!mTmpPath.empty()) unlink(mTmpPath.c_str());
}

} // namespace android
