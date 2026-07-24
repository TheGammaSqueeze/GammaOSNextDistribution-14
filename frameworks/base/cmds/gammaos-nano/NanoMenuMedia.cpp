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

// NanoMenuMedia - Bluetooth AVRCP media-control bridge endpoints (nano side).
//
// nano is a native launcher with no Java MediaSession, so a companion in
// system_server (NanoMediaBridge) owns an AVRCP-eligible MediaSession and talks
// to nano through system properties:
//   - the bridge writes a one-shot transport command to sys.gammaos.nano.media
//     on a headphone/car button press; nanoMediaDispatch() runs it on the active
//     player (the consume + self-clear lives in pollInput(), mirroring the nav hook);
//   - nano publishes its now-playing state (and metadata) via nanoPublishMediaState()
//     so the bridge keeps the session active + carries the right PlaybackState (which
//     is what makes AVRCP route to it) and shows the track on the headphone/car.

#define LOG_TAG "GammaOSNano"

#include <errno.h>
#include <fcntl.h>
#include <stdio.h>
#include <string.h>
#include <sys/stat.h>
#include <unistd.h>

#include <cutils/properties.h>
#include <log/log.h>

#include "NanoMenu.h"
#include "NanoAudio.h"
#include "NanoVideo.h"

namespace android {

namespace {
// Write a property only when its value actually changed, so an idle/steady player
// performs zero property writes per frame (the project's lazy-cost requirement).
inline void setPropIfChanged(std::string& cache, const char* name, const char* value) {
    if (cache != value) {
        cache = value;
        property_set(name, value);
    }
}
}  // namespace

// Run one AVRCP transport command on whichever player is the foreground transport.
// Video takes priority over music; music is driven even when minimized (the
// Now-Playing screen is closed but the queue is still loaded / playing). Radio and
// IPTV next/prev step the station/channel (no per-track scrub), which nano's
// mpStep/vidStepTitle already handle.
void NanoMenu::nanoMediaDispatch(const char* cmd) {
    if (!cmd || !cmd[0]) return;

    // ---- Video player (incl. IPTV streams) has priority when it is up ----
    if (mVidActive && mVideoTest) {
        if      (!strcmp(cmd, "play"))      { if (!mVidPlaying) vidTogglePlay(); }
        else if (!strcmp(cmd, "pause"))     { if (mVidPlaying)  vidTogglePlay(); }
        else if (!strcmp(cmd, "playpause")) { vidTogglePlay(); }
        else if (!strcmp(cmd, "stop"))      { vidStop(); }
        else if (!strcmp(cmd, "next"))      { vidStepTitle(+1); }
        else if (!strcmp(cmd, "prev"))      { vidStepTitle(-1); }
        return;
    }

    // ---- Music / radio: active OR minimized-but-loaded ----
    bool musicLoaded = mMpIsRadio ? !mMpRadioQueue.empty() : !mMpQueue.empty();
    if (musicLoaded) {
        if      (!strcmp(cmd, "play"))      { mpAudioCmd(MpAudioCmd::Play); }
        else if (!strcmp(cmd, "pause"))     { mpAudioCmd(MpAudioCmd::Pause); }
        else if (!strcmp(cmd, "playpause")) {
            if (mMusicPlayer.isPlaying()) mpAudioCmd(MpAudioCmd::Pause);
            else                          mpAudioCmd(MpAudioCmd::Play);
        }
        else if (!strcmp(cmd, "stop"))      { mpAudioCmd(MpAudioCmd::Stop); }
        else if (!strcmp(cmd, "next"))      { mpNext(); }
        else if (!strcmp(cmd, "prev"))      { mpPrev(); }
        return;
    }
    // Nothing playing: ignore (the bridge keeps its session inactive anyway).
}

// Write the current track's metadata to a small JSON the bridge reads when the
// generation prop bumps (title/artist/album routinely exceed PROPERTY_VALUE_MAX,
// so they cannot ride in a property). Atomic temp+rename, world-readable, same
// pattern as nano_music.json.
void NanoMenu::writeMediaMetaJson(const std::string& title, const std::string& artist,
                                  const std::string& album, const char* kind, double dur) {
    auto esc = [](const std::string& s) {
        std::string o;
        o.reserve(s.size() + 8);
        for (char c : s) {
            if (c == '"' || c == '\\') { o += '\\'; o += c; }
            else if (c == '\n' || c == '\r' || c == '\t') o += ' ';
            else o += c;
        }
        return o;
    };
    char durbuf[24];
    snprintf(durbuf, sizeof durbuf, "%d", (int)(dur + 0.5));
    std::string text = "{\"title\":\"" + esc(title) +
                       "\",\"artist\":\"" + esc(artist) +
                       "\",\"album\":\"" + esc(album) +
                       "\",\"kind\":\"" + esc(kind) +
                       "\",\"dur\":" + durbuf + "}";

    const char* path = "/data/system/nano_media_meta.json";
    const char* tmp  = "/data/system/nano_media_meta.json.tmp";
    int fd = open(tmp, O_WRONLY | O_CREAT | O_TRUNC, 0644);
    if (fd < 0) { ALOGW("NanoMenu: cannot write %s (errno %d)", tmp, errno); return; }
    size_t off = 0; bool ok = true;
    while (off < text.size()) {
        ssize_t w = write(fd, text.c_str() + off, text.size() - off);
        if (w <= 0) { ok = false; break; }
        off += (size_t)w;
    }
    fsync(fd); close(fd);
    if (!ok) { unlink(tmp); return; }
    if (rename(tmp, path) != 0) { unlink(tmp); return; }
    (void)chown(path, 0, 0);
    (void)chmod(path, 0644);
}

// Detached writer thread for the metadata JSON. The render thread must never block
// on the file write (open/write/fsync/rename on /data): under heavy I/O (a video or
// stream playing) plus memory pressure that rename stalled ~8s and tripped the render
// watchdog, aborting nano. Here, off the render thread, the blocking write is safe.
// Writes the file FIRST then bumps the generation prop, so the bridge (which reads the
// file when the prop changes) never sees a stale/missing file.
void NanoMenu::mediaMetaWriter() {
    for (;;) {
        MediaMetaReq req;
        {
            std::unique_lock<std::mutex> lk(mMediaWriteMutex);
            mMediaWriteCv.wait(lk, [this] { return mMediaWriteHasPending; });
            req = mMediaWritePending;
            mMediaWriteHasPending = false;
        }
        writeMediaMetaJson(req.title, req.artist, req.album, req.kind.c_str(), req.dur);
        char buf[24];
        snprintf(buf, sizeof buf, "%u", req.gen);
        property_set("sys.gammaos.nano.media.meta", buf);
    }
}

// Hand a metadata snapshot to the writer thread (lazy-started). Coalesces to a single
// pending slot - only the most recent track/title matters - and never blocks the caller.
void NanoMenu::queueMediaMetaWrite(const std::string& title, const std::string& artist,
                                   const std::string& album, const char* kind, double dur, unsigned gen) {
    if (!mMediaWriteStarted) {
        mMediaWriteStarted = true;
        std::thread(&NanoMenu::mediaMetaWriter, this).detach();
    }
    {
        std::lock_guard<std::mutex> lk(mMediaWriteMutex);
        mMediaWritePending = MediaMetaReq{title, artist, album, kind, dur, gen};
        mMediaWriteHasPending = true;
    }
    mMediaWriteCv.notify_one();
}

// Publish the now-playing state for NanoMediaBridge. Called once per frame from
// pollInput(); change-gated so a steady or idle player writes nothing.
void NanoMenu::nanoPublishMediaState() {
    const char* state = "stopped";
    const char* kind  = "none";
    double pos = 0.0, dur = 0.0;
    std::string title, artist, album;

    if (mVidActive && mVideoTest) {
        state = mVidStopped ? "stopped" : (mVidPlaying ? "playing" : "paused");
        kind  = mVidIsStream ? "stream" : "video";
        pos = mVideoTest->position();
        dur = vidDuration();
        if (mVidIsStream) {
            if (mVidIdx >= 0 && mVidIdx < (int)mVidStreamList.size())
                title = mVidStreamList[mVidIdx].name;
        } else {
            int vi = (mVidIdx >= 0 && mVidIdx < (int)mVidList.size()) ? mVidList[mVidIdx] : -1;
            if (vi >= 0 && vi < (int)mVideos.size()) title = mVideos[vi].name;
        }
    } else {
        bool musicLoaded = mMpIsRadio ? !mMpRadioQueue.empty() : !mMpQueue.empty();
        if (musicLoaded) {
            state = mMusicPlayer.isPlaying() ? "playing"
                  : (mMusicPlayer.isStopped() ? "stopped" : "paused");
            kind  = mMpIsRadio ? "radio" : "music";
            pos = mMusicPlayer.position();
            dur = mMusicPlayer.duration();
            auto m = mMusicPlayer.meta();
            title = m.title; artist = m.artist; album = m.album;
        }
    }

    setPropIfChanged(mMediaLastState, "sys.gammaos.nano.media.state", state);
    setPropIfChanged(mMediaLastKind,  "sys.gammaos.nano.media.kind",  kind);
    // GammaOS Nano: pin the display on while the in-process video / stream player is
    // actively playing. The player has no framework window and gets no input during a
    // clip, so without this the home would idle off mid-video (PowerManagerService
    // .isNanoDisplayForcedOn reads this flag). Music is audio-only and is deliberately
    // allowed to keep playing with the screen off, so it does NOT set the flag.
    bool videoPlaying = mVidActive && mVideoTest && mVidPlaying && !mVidStopped;
    setPropIfChanged(mMediaLastPlaying, "sys.gammaos.nano.media_playing",
                     videoPlaying ? "1" : "0");
    char buf[24];
    snprintf(buf, sizeof buf, "%d", (int)(pos + 0.5));
    setPropIfChanged(mMediaLastPos, "sys.gammaos.nano.media.pos", buf);
    snprintf(buf, sizeof buf, "%d", (int)(dur + 0.5));
    setPropIfChanged(mMediaLastDur, "sys.gammaos.nano.media.dur", buf);

    // Metadata file + generation bump only on a track/title change. The file write is
    // handed to the writer thread - it must NEVER run on the render thread, where a
    // stalled rename under I/O+memory pressure tripped the render watchdog (SIGABRT).
    std::string sig = title + "\x1f" + artist + "\x1f" + album + "\x1f" + kind;
    if (sig != mMediaMetaSig) {
        mMediaMetaSig = sig;
        queueMediaMetaWrite(title, artist, album, kind, dur, (unsigned)(++mMediaMetaGen));
    }
}

} // namespace android
