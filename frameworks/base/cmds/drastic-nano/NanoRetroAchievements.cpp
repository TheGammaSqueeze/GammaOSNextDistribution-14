/*
 * Copyright (C) 2026 GammaOS
 *
 * RetroAchievements support for drastic-nano. See NanoRetroAchievements.h and
 * RETROACHIEVEMENTS.md for the design. rc_client owns the achievement runtime,
 * the server protocol, hardcore policy, session/ping and rich presence; this
 * file provides the three integration callbacks (read memory, server call,
 * event handler), runs them on a single dedicated thread, and pumps the result
 * to the render thread as UI events.
 */

#define LOG_TAG "DrasticNanoRA"

#include "NanoRetroAchievements.h"
#include "DrasticRunner.h"
#include "NanoI18n.h"   // trDyn() shared nano UI translations

#include <rc_consoles.h>
#include <rc_error.h>

#include <utils/Log.h>
#include <cutils/properties.h>

#include <cstdio>
#include <cstring>
#include <cstdlib>
#include <cstdint>
#include <cmath>
#include <string>
#include <chrono>
#include <thread>
#include <unistd.h>
#include <sys/stat.h>
#include <sys/wait.h>
#include <fcntl.h>
#include <signal.h>
#include <cerrno>
#include <vector>
#include <utility>

#include <aaudio/AAudio.h>

// PNG decode for achievement badges. stb_image lives in the gammaos-nano dir,
// which is already on this binary's include path (see Android.bp include_dirs).
// We own the single implementation translation unit here.
#define STB_IMAGE_IMPLEMENTATION
#define STBI_ONLY_PNG
#define STBI_NO_STDIO
#include "stb_image.h"

namespace android {

using namespace std::chrono;

// ---------------------------------------------------------------------------
// small helpers
// ---------------------------------------------------------------------------

static bool readFileLines(const std::string& path, std::string* l1, std::string* l2) {
    FILE* f = fopen(path.c_str(), "rb");
    if (!f) return false;
    char buf[1024] = {0};
    size_t n = fread(buf, 1, sizeof(buf) - 1, f);
    fclose(f);
    if (n == 0) return false;
    std::string s(buf, n);
    size_t nl = s.find('\n');
    if (nl == std::string::npos) { if (l1) *l1 = s; if (l2) l2->clear(); return true; }
    if (l1) *l1 = s.substr(0, nl);
    std::string rest = s.substr(nl + 1);
    size_t nl2 = rest.find('\n');
    if (l2) *l2 = (nl2 == std::string::npos) ? rest : rest.substr(0, nl2);
    return true;
}

// ---------------------------------------------------------------------------
// lifecycle
// ---------------------------------------------------------------------------

NanoRetroAchievements::NanoRetroAchievements() {}

NanoRetroAchievements::~NanoRetroAchievements() {
    shutdown();
}

void NanoRetroAchievements::onGameLoaded(DrasticRunner* dr, const std::string& romPath) {
    if (mStarted) return;
    mRunner = dr;
    mRomPath = romPath;

    // Resolve and verify the in-process DS Main RAM. Useful to log even when
    // RetroAchievements itself is disabled, since it proves the memory read.
    DrasticRunner::DsMainRam m = dr->dsMainRam();
    mRamBase = m.valid() ? m.base : nullptr;
    mRamMask = m.mask;
    mRamSize = m.mask + 1;
    // Resolve the DS ARM9 Data TCM too, the second region a DS achievement set
    // can read. When unresolved (null), reads of it return 0 and rc_client marks
    // any achievement that references it unsupported rather than mis-evaluating.
    DrasticRunner::DsDataTcm tcm = dr->dsDataTcm();
    mDtcmBase = tcm.valid() ? tcm.base : nullptr;
    mDtcmSize = tcm.mask + 1;
    // Debug/testing A/B (default off): force Data TCM to be treated as unbacked
    // so its references fall to the unsupported bucket, to demonstrate the
    // difference Data TCM support makes for a set that reads it.
    {
        char nodtcm[PROPERTY_VALUE_MAX] = {};
        property_get("persist.gammaos.drastic_nano.ra_no_dtcm", nodtcm, "0");
        if (nodtcm[0] == '1') {
            mDtcmBase = nullptr;
            ALOGW("RA: Data TCM serving DISABLED (debug ra_no_dtcm=1)");
        }
    }
    // Verify the in-process read on a retry thread: the DS finishes its boot and
    // cartridge load a little after the first frame, so the header mirror and ARM9
    // image are not all present immediately. Purely diagnostic; the live reads
    // used by the runtime are correct regardless of this check.
    mProofThread = std::thread([this, romPath]() { verifyMainRamOnDevice(romPath); });

    char enabled[PROPERTY_VALUE_MAX] = {};
    property_get("persist.gammaos.drastic_nano.ra_enabled", enabled, "0");
    if (enabled[0] != '1') {
        // RA off at load. Do NOT start the client now, but leave the door open:
        // a UI login (requestLogin) enables RA and starts the client on demand,
        // so the login is no longer a silent no-op when RA was not pre-enabled.
        ALOGI("RA: disabled at load (persist.gammaos.drastic_nano.ra_enabled=0); "
              "a UI login will enable and start it on demand");
        return;
    }
    if (!mRamBase) {
        ALOGE("RA: DS Main RAM unresolved; not starting RetroAchievements");
        return;
    }
    startClient();
}

// Start the rc_client + HTTP worker threads for the already-loaded game. Shared
// by onGameLoaded (RA pre-enabled) and requestLogin (RA enabled on demand by a UI
// login). mRunner/mRomPath/mRamBase were resolved by onGameLoaded before either
// caller reaches here.
bool NanoRetroAchievements::startClient() {
    if (mStarted) return true;
    if (!mRamBase) {
        ALOGE("RA: DS Main RAM unresolved; cannot start the client");
        return false;
    }
    mStateDir = "/data/system/nano_ra";
    mkdir(mStateDir.c_str(), 0700);

    // Pre-load the cached achievement set for this ROM so the Achievements
    // section shows the list right away (even before the network load finishes,
    // or if it never does on a poor link). The live load overwrites this.
    loadAchSetCache();

    mStop.store(false);
    mStarted = true;
    mHttpThread = std::thread(&NanoRetroAchievements::httpThreadMain, this);
    mClientThread = std::thread(&NanoRetroAchievements::clientThreadMain, this);
    ALOGI("RA: started (rc_client thread + http worker)");
    return true;
}

void NanoRetroAchievements::setPaused(bool paused) {
    mPaused.store(paused);
}

void NanoRetroAchievements::shutdown() {
    mStop.store(true);
    mHttpCv.notify_all();
    // Interrupt any curl the HTTP worker is mid-flight on so the join below does
    // not stall game-exit. Taken under mCurlMutex against the worker's fork and
    // its pre-reap clear: either we see the live PID (curl still running) and
    // kill it, or the worker has not forked yet (sees mStop, skips it), or the
    // worker has already cleared the PID before reaping (we read 0, kill nothing).
    // So a reaped-and-recycled PID is never signalled.
    {
        std::lock_guard<std::mutex> lk(mCurlMutex);
        pid_t curl = mCurlChild.load();
        if (curl > 0) kill(curl, SIGKILL);
    }
    // The proof thread is started even when RetroAchievements is disabled, so it
    // is always joined here (and in the destructor) regardless of mStarted.
    if (mProofThread.joinable()) mProofThread.join();
    if (mClientThread.joinable()) mClientThread.join();
    if (mHttpThread.joinable()) mHttpThread.join();
    if (mStarted) {
        mStarted = false;
        ALOGI("RA: shut down");
    }
}

// ---------------------------------------------------------------------------
// static C callbacks -> instance methods (via rc_client userdata)
// ---------------------------------------------------------------------------

uint32_t NanoRetroAchievements::sReadMemory(uint32_t address, uint8_t* buffer,
                                            uint32_t num_bytes, rc_client_t* client) {
    NanoRetroAchievements* self = (NanoRetroAchievements*) rc_client_get_userdata(client);
    return self ? self->readMemory(address, buffer, num_bytes) : 0;
}

void NanoRetroAchievements::sServerCall(const rc_api_request_t* request,
                                        rc_client_server_callback_t callback,
                                        void* callback_data, rc_client_t* client) {
    NanoRetroAchievements* self = (NanoRetroAchievements*) rc_client_get_userdata(client);
    if (self) self->enqueueServerCall(request, callback, callback_data);
}

void NanoRetroAchievements::sEventHandler(const rc_client_event_t* event, rc_client_t* client) {
    NanoRetroAchievements* self = (NanoRetroAchievements*) rc_client_get_userdata(client);
    if (self) self->onEvent(event);
}

void NanoRetroAchievements::sLogMessage(const char* message, const rc_client_t* /*client*/) {
    ALOGI("RA[rc_client]: %s", message ? message : "");
}

void NanoRetroAchievements::sLoginCallback(int result, const char* error_message,
                                           rc_client_t* client, void* /*userdata*/) {
    NanoRetroAchievements* self = (NanoRetroAchievements*) rc_client_get_userdata(client);
    if (self) self->onLoginResult(result, error_message);
}

void NanoRetroAchievements::sLoadCallback(int result, const char* error_message,
                                          rc_client_t* client, void* /*userdata*/) {
    NanoRetroAchievements* self = (NanoRetroAchievements*) rc_client_get_userdata(client);
    if (self) self->onLoadResult(result, error_message);
}

// ---------------------------------------------------------------------------
// read_memory: DS Main RAM, read directly in-process
// ---------------------------------------------------------------------------

uint32_t NanoRetroAchievements::readMemory(uint32_t address, uint8_t* buffer, uint32_t num_bytes) {
    // RetroAchievements flattens the DS address space into the regions its
    // console table defines. Two are backed here:
    //   - Main RAM: the 4 MB ARM9 work RAM at flat 0x000000..0x3FFFFF. DraStic
    //     stores it as one contiguous little-endian block, so no per-byte
    //     translation is needed; bounds-check and copy.
    //   - Data TCM: the 16 KB ARM9 DTCM at flat 0x1000000..0x1003FFF, a separate
    //     buffer in DraStic.
    // Any other flat address (including the 0x400000..0xFFFFFF DSi-only padding
    // between the two) is not backed and reads as zero, so at game load
    // rc_client_validate_addresses marks references to it unsupported rather than
    // mis-evaluating them. A read that would straddle the end of a region returns
    // 0 (rc_client never legitimately reads across a region boundary).
    if (num_bytes == 0) return 0;
    if (address < mRamSize) {
        if (!mRamBase) return 0;
        if (num_bytes > mRamSize - address) return 0;
        memcpy(buffer, mRamBase + address, num_bytes);
        return num_bytes;
    }
    if (address >= 0x1000000 && address < 0x1000000 + mDtcmSize) {
        if (!mDtcmBase) return 0;
        uint32_t off = address - 0x1000000;
        if (num_bytes > mDtcmSize - off) return 0;
        memcpy(buffer, mDtcmBase + off, num_bytes);
        return num_bytes;
    }
    return 0;
}

// ---------------------------------------------------------------------------
// server_call: hand off to the HTTP worker; never block the caller
// ---------------------------------------------------------------------------

void NanoRetroAchievements::enqueueServerCall(const rc_api_request_t* request,
                                              rc_client_server_callback_t callback,
                                              void* callback_data) {
    HttpJob j;
    j.url = request->url ? request->url : "";
    j.isPost = (request->post_data != nullptr && request->post_data[0] != '\0');
    if (j.isPost) j.postData = request->post_data;
    j.contentType = request->content_type ? request->content_type
                                           : "application/x-www-form-urlencoded";
    j.callback = callback;
    j.callbackData = callback_data;
    {
        std::lock_guard<std::mutex> lk(mHttpMutex);
        mHttpQueue.push_back(std::move(j));
    }
    mHttpCv.notify_one();
}

// ---------------------------------------------------------------------------
// HTTP worker thread (device curl, off the render and client threads)
// ---------------------------------------------------------------------------

void NanoRetroAchievements::httpThreadMain() {
    while (!mStop.load()) {
        HttpJob j;
        std::string ua;
        {
            std::unique_lock<std::mutex> lk(mHttpMutex);
            mHttpCv.wait(lk, [&] { return mStop.load() || !mHttpQueue.empty(); });
            if (mStop.load()) break;
            j = std::move(mHttpQueue.front());
            mHttpQueue.pop_front();
            ua = mUserAgent.empty() ? std::string("GammaOS-DrasticNano/1.0.0") : mUserAgent;
        }

        std::string body;
        int status = 0;
        bool ok = false;

        // Achievement badge disk cache: /data/system/nano_ra/badges/<id>.png.
        // Serve from disk when present (no network) so badges survive flaky wifi
        // and reboots and the banner shows instantly. Warmed by prefetchAllBadges
        // on connect.
        std::string badgeCache;
        bool servedFromCache = false;
        if (j.isBadge && j.badgeAchId) {
            badgeCache = std::string("/data/system/nano_ra/badges/")
                       + std::to_string(j.badgeAchId) + ".png";
            FILE* cf = fopen(badgeCache.c_str(), "rb");
            if (cf) {
                char cbuf[4096];
                size_t cr;
                while ((cr = fread(cbuf, 1, sizeof(cbuf), cf)) > 0) body.append(cbuf, cr);
                fclose(cf);
                if (!body.empty()) { ok = true; servedFromCache = true; }
            }
        }

        char tmpl[] = "/data/system/nano_ra/.http-XXXXXX";
        int fd = servedFromCache ? -1 : mkstemp(tmpl);
        if (fd >= 0) {
            close(fd);
            // Badge images get a shorter timeout so a slow image never stalls
            // the queued rc_client requests behind it.
            const char* maxTime = j.isBadge ? "10" : "30";

            // Run curl via fork/exec (not popen) so we can track the child PID
            // and kill it from shutdown(). On flaky wifi a request can hold the
            // full --max-time; without this, the exit-time thread join would
            // block on an in-flight curl and stall game-exit for up to a minute.
            std::vector<std::string> args = {
                "/system/bin/curl", "-s", "-L", "--max-time", maxTime,
                "-A", ua,
            };
            if (j.isPost) {
                args.push_back("-H");
                args.push_back("Content-Type: " + j.contentType);
                args.push_back("--data-raw");
                args.push_back(j.postData);
            }
            args.push_back("-o");      args.push_back(tmpl);
            args.push_back("-w");      args.push_back("%{http_code}");
            args.push_back(j.url);
            std::vector<char*> argv;
            argv.reserve(args.size() + 1);
            for (auto& a : args) argv.push_back(const_cast<char*>(a.c_str()));
            argv.push_back(nullptr);

            int rc = -1;
            char codebuf[64] = {0};
            int pipefd[2] = { -1, -1 };
            pid_t pid = -1;
            // The fork + child-PID publish happens under mCurlMutex, the same
            // lock shutdown() takes to read + kill the PID, so the worker never
            // starts a curl after shutdown has set mStop (and any curl it does
            // start is visible for shutdown to kill). The PID stays published for
            // the whole pipe read (where a stuck curl is blocked and a kill is
            // useful), then is cleared under the lock before the reap so a
            // recycled PID can never be signalled.
            {
                std::lock_guard<std::mutex> lk(mCurlMutex);
                if (!mStop.load() && pipe(pipefd) == 0) {
                    pid = fork();
                    if (pid == 0) {
                        // child: stdout -> pipe (for %{http_code}), stderr -> null
                        dup2(pipefd[1], STDOUT_FILENO);
                        int dn = open("/dev/null", O_WRONLY);
                        if (dn >= 0) { dup2(dn, STDERR_FILENO); close(dn); }
                        close(pipefd[0]);
                        close(pipefd[1]);
                        // Close every OTHER inherited fd before exec. drastic holds
                        // ~55 fds (DRM master, EGL/GLES render node, /dev/binder,
                        // AAudio, SurfaceFlinger/libgui sockets), none opened
                        // O_CLOEXEC. The forked curl inheriting them is the single
                        // difference from a standalone/runcon curl (which works), and
                        // is what made the in-process login fail with -32. Also clear
                        // any blocked signal mask the dlopen'd DS core may have left on
                        // this worker thread so curl starts with clean signal state.
                        for (int fd = 3; fd < 1024; fd++) close(fd);
                        sigset_t empty;
                        sigemptyset(&empty);
                        sigprocmask(SIG_SETMASK, &empty, nullptr);
                        execv(argv[0], argv.data());
                        _exit(127);
                    }
                    mCurlChild.store(pid > 0 ? pid : 0);
                }
            }
            if (pid > 0) {
                close(pipefd[1]);
                size_t n = 0;
                ssize_t r;
                while (n < sizeof(codebuf) - 1 &&
                       (r = read(pipefd[0], codebuf + n, sizeof(codebuf) - 1 - n)) > 0)
                    n += (size_t) r;
                codebuf[n] = '\0';
                close(pipefd[0]);
                // The read above hit EOF, which means curl closed its stdout, i.e.
                // the process is exiting. Clear the published PID under the lock
                // BEFORE we reap it: after this, shutdown() reads 0 and will not
                // kill, so a PID that waitpid() reaps and the OS later recycles can
                // never be signalled by a late shutdown(). The kill is only needed
                // while curl is still running (blocked on the network during the
                // read above), and that window is still covered because mCurlChild
                // is live throughout the read.
                {
                    std::lock_guard<std::mutex> lk(mCurlMutex);
                    mCurlChild.store(0);
                }
                int wstatus = 0;
                while (waitpid(pid, &wstatus, 0) < 0 && errno == EINTR) {}
                rc = (WIFEXITED(wstatus) && WEXITSTATUS(wstatus) == 0) ? 0 : -1;
            } else {
                if (pipefd[0] >= 0) close(pipefd[0]);
                if (pipefd[1] >= 0) close(pipefd[1]);
            }
            int httpcode = atoi(codebuf);

            FILE* bf = fopen(tmpl, "rb");
            if (bf) {
                char buf[4096];
                size_t r;
                while ((r = fread(buf, 1, sizeof(buf), bf)) > 0) body.append(buf, r);
                fclose(bf);
            }
            unlink(tmpl);

            // rc == 0 with a real HTTP status (even 4xx/5xx) is a completed call;
            // rc_client parses the body for server-side errors. A curl failure or
            // a 0 status means a transport problem, which is retryable.
            if (rc == 0 && httpcode > 0) {
                status = httpcode;
                ok = true;
            } else {
                ALOGW("RA: curl xfer FAILED rc=%d httpcode=%d bodylen=%zu url=%s",
                      rc, httpcode, body.size(), j.url.c_str());
            }
        }

        // Persist a freshly downloaded badge PNG to the on-disk cache for reuse.
        // Write to a temp file then rename so a reader (the bottom-panel detail
        // view decodes badges straight from this cache) never sees a torn PNG.
        if (j.isBadge && ok && !servedFromCache && !body.empty() && !badgeCache.empty()) {
            std::string btmp = badgeCache + ".tmp";
            FILE* wf = fopen(btmp.c_str(), "wb");
            if (wf) {
                fwrite(body.data(), 1, body.size(), wf);
                fflush(wf);
                bool werr = ferror(wf);
                if (fclose(wf) != 0 || werr) unlink(btmp.c_str());
                else rename(btmp.c_str(), badgeCache.c_str());
            }
        }

        // Badge image download (achievement banner): decode the PNG here, off
        // the render thread, and hand the raw RGBA to the render thread to
        // upload. Not an rc_client request, so it never touches mDoneQueue.
        if (j.isBadge) {
            // Prefetch jobs only warm the disk cache; nothing to display.
            if (j.prefetchOnly) {
                if (!ok) ALOGW("RA: badge %u prefetch failed (status %d)",
                               j.badgeAchId, status);
                continue;
            }
            if (ok && !body.empty()) {
                int w = 0, h = 0, comp = 0;
                unsigned char* px = stbi_load_from_memory(
                    reinterpret_cast<const unsigned char*>(body.data()),
                    (int)body.size(), &w, &h, &comp, 4);
                if (px && w > 0 && h > 0) {
                    BadgeReady br;
                    br.achId = j.badgeAchId;
                    br.w = w;
                    br.h = h;
                    br.rgba.assign(px, px + (size_t)w * h * 4);
                    {
                        std::lock_guard<std::mutex> lk(mBadgeMutex);
                        if (mBadgeQueue.size() > 8) mBadgeQueue.pop_front();
                        mBadgeQueue.push_back(std::move(br));
                    }
                    ALOGI("RA: badge %u decoded %dx%d", j.badgeAchId, w, h);
                }
                if (px) stbi_image_free(px);
            } else {
                ALOGW("RA: badge %u download failed (status %d)", j.badgeAchId, status);
            }
            continue;
        }

        if (!ok) {
            status = RC_API_SERVER_RESPONSE_RETRYABLE_CLIENT_ERROR;
            body.clear();
        }

        HttpDone d;
        d.body = std::move(body);
        d.httpStatus = status;
        d.callback = j.callback;
        d.callbackData = j.callbackData;
        {
            std::lock_guard<std::mutex> lk(mDoneMutex);
            mDoneQueue.push_back(std::move(d));
        }
    }
}

// Queue a badge image download (GET) for the achievement banner. Decoded on
// the HTTP worker; the RGBA is drained by the render thread via popBadge().
// Pushed to the FRONT so a live unlock's badge is not stuck behind the
// connect-time prefetch backlog (which is queued at the back).
void NanoRetroAchievements::enqueueBadgeDownload(uint32_t achId,
                                                 const std::string& url) {
    if (url.empty()) return;
    HttpJob j;
    j.url = url;
    j.isBadge = true;
    j.badgeAchId = achId;
    {
        std::lock_guard<std::mutex> lk(mHttpMutex);
        mHttpQueue.push_front(std::move(j));
    }
    mHttpCv.notify_one();
}

// Warm the on-disk badge cache for every achievement once the set has loaded
// (called from the game-load callback, client thread). Each job either hits the
// existing cache file (instant, no network) or downloads + saves it. Queued at
// the back so it never delays login/load or a live unlock badge.
void NanoRetroAchievements::prefetchAllBadges() {
    mkdir("/data/system/nano_ra/badges", 0700);
    // Only fetch badges not already on disk, so this is safe to call repeatedly
    // (connect time, then every 60s) to finish the cache over a flaky link.
    std::vector<std::pair<uint32_t, std::string>> missing;
    {
        std::lock_guard<std::mutex> lk(mUiDataMutex);
        for (const auto& a : mAchList) {
            if (!a.id || a.badgeUrl.empty()) continue;
            std::string p = "/data/system/nano_ra/badges/"
                          + std::to_string(a.id) + ".png";
            struct stat st;
            if (stat(p.c_str(), &st) != 0 || st.st_size == 0)
                missing.emplace_back(a.id, a.badgeUrl);
        }
    }
    if (missing.empty()) return;
    {
        std::lock_guard<std::mutex> lk(mHttpMutex);
        // Don't pile on if a previous prefetch batch is still draining.
        for (const auto& q : mHttpQueue) if (q.prefetchOnly) return;
        for (const auto& it : missing) {
            HttpJob j;
            j.url = it.second;
            j.isBadge = true;
            j.badgeAchId = it.first;
            j.prefetchOnly = true;
            mHttpQueue.push_back(std::move(j));
        }
    }
    mHttpCv.notify_one();
    ALOGI("RA: prefetching %zu missing achievement badges into the disk cache",
          missing.size());
}

bool NanoRetroAchievements::popBadge(uint32_t* achId,
                                     std::vector<uint8_t>* rgba,
                                     int* w, int* h) {
    std::lock_guard<std::mutex> lk(mBadgeMutex);
    if (mBadgeQueue.empty()) return false;
    BadgeReady br = std::move(mBadgeQueue.front());
    mBadgeQueue.pop_front();
    *achId = br.achId;
    *rgba = std::move(br.rgba);
    *w = br.w;
    *h = br.h;
    return true;
}

// Play a short synthesized "achievement" chime on its own AAudio stream so it
// mixes with (does not disturb) the game's audio. Runs on a detached thread;
// unlocks are infrequent so the open/close cost is irrelevant.
void NanoRetroAchievements::playTwinkle() {
    std::thread([]() {
        AAudioStreamBuilder* builder = nullptr;
        if (AAudio_createStreamBuilder(&builder) != AAUDIO_OK || !builder) return;
        AAudioStreamBuilder_setDirection(builder, AAUDIO_DIRECTION_OUTPUT);
        AAudioStreamBuilder_setFormat(builder, AAUDIO_FORMAT_PCM_I16);
        AAudioStreamBuilder_setChannelCount(builder, 1);
        AAudioStreamBuilder_setSampleRate(builder, 48000);
        AAudioStreamBuilder_setUsage(builder, AAUDIO_USAGE_NOTIFICATION_EVENT);
        AAudioStreamBuilder_setContentType(builder, AAUDIO_CONTENT_TYPE_SONIFICATION);
        AAudioStreamBuilder_setPerformanceMode(builder, AAUDIO_PERFORMANCE_MODE_NONE);
        AAudioStream* stream = nullptr;
        aaudio_result_t r = AAudioStreamBuilder_openStream(builder, &stream);
        AAudioStreamBuilder_delete(builder);
        if (r != AAUDIO_OK || !stream) return;

        const int sr = AAudioStream_getSampleRate(stream);
        const int total = (int)(sr * 0.62f);
        std::vector<int16_t> pcm(total, 0);
        // Ascending major-triad arpeggio (C6 E6 G6 C7) with a bell-like decay.
        const float notes[4] = {1046.5f, 1318.5f, 1567.98f, 2093.0f};
        const float starts[4] = {0.00f, 0.075f, 0.15f, 0.245f};
        for (int n = 0; n < 4; n++) {
            const int s0 = (int)(starts[n] * sr);
            for (int i = s0; i < total; i++) {
                const float t = (i - s0) / (float)sr;
                const float env = expf(-t * 7.0f);
                if (env < 0.0015f) break;
                const float w0 = 2.0f * (float)M_PI * notes[n];
                const float s = sinf(w0 * t) * 0.6f + sinf(2.0f * w0 * t) * 0.18f;
                int v = pcm[i] + (int)(s * env * 0.32f * 32767.0f);
                if (v > 32767) v = 32767;
                if (v < -32768) v = -32768;
                pcm[i] = (int16_t)v;
            }
        }

        AAudioStream_requestStart(stream);
        int written = 0;
        while (written < total) {
            int32_t n = AAudioStream_write(stream, pcm.data() + written,
                                           total - written,
                                           200LL * 1000 * 1000);
            if (n < 0) break;
            written += n;
        }
        // Let the queued tail play out before tearing the stream down.
        usleep(250 * 1000);
        AAudioStream_requestStop(stream);
        AAudioStream_close(stream);
    }).detach();
}

// Rebuild the achievement-list snapshot the overlay draws, and bump the UI
// generation so an open Achievements section refreshes. Must be called on the
// client thread (touches rc_client). Used on game load and after each unlock so
// the unlocked achievement moves to the "Recently Unlocked" bucket live.
void NanoRetroAchievements::refreshAchievementSnapshot() {
    if (!mClient) return;
    rc_client_achievement_list_t* list = rc_client_create_achievement_list(
        mClient, RC_CLIENT_ACHIEVEMENT_CATEGORY_CORE,
        RC_CLIENT_ACHIEVEMENT_LIST_GROUPING_PROGRESS);
    std::vector<AchievementInfo> snap;
    if (list) {
        for (uint32_t b = 0; b < list->num_buckets; b++) {
            const rc_client_achievement_bucket_t& bk = list->buckets[b];
            for (uint32_t a = 0; a < bk.num_achievements; a++) {
                const rc_client_achievement_t* ach = bk.achievements[a];
                if (!ach) continue;
                AchievementInfo info;
                info.id = ach->id;
                info.title = ach->title ? ach->title : "";
                info.description = ach->description ? ach->description : "";
                info.points = ach->points;
                info.unlocked = (ach->state == RC_CLIENT_ACHIEVEMENT_STATE_UNLOCKED);
                info.bucket = bk.label ? bk.label : "";
                // Measured progress (e.g. "23/50") for in-progress measured
                // achievements, so the list shows the Measured flag, not just the
                // bucket. rc_client leaves this empty for non-measured ones and
                // sets "unlocked"/full for completed, so only show it while locked.
                if (!info.unlocked && ach->measured_progress[0])
                    info.measuredProgress = ach->measured_progress;
                info.rarity = ach->rarity;            // % of players (RA "rarity")
                info.unlockTime = (int64_t)ach->unlock_time;  // 0 if still locked
                char burl[256] = {0};
                if (rc_client_achievement_get_image_url(
                        ach, RC_CLIENT_ACHIEVEMENT_STATE_UNLOCKED,
                        burl, sizeof(burl)) == RC_OK) {
                    info.badgeUrl = burl;
                }
                snap.push_back(std::move(info));
            }
        }
        rc_client_destroy_achievement_list(list);
    }
    {
        std::lock_guard<std::mutex> lk(mUiDataMutex);
        mAchList = std::move(snap);
    }
    mUiGen.fetch_add(1, std::memory_order_relaxed);
    // Persist the fresh live snapshot so the Achievements section still has the
    // set to show next time the network is poor or down.
    writeAchSetCache();
}

// ---------------------------------------------------------------------------
// Achievement-set disk cache (poor-network fallback for the Achievements list)
// ---------------------------------------------------------------------------

static std::string raSanitizeField(const std::string& s) {
    std::string o;
    o.reserve(s.size());
    for (char c : s) o += (c == '\t' || c == '\n' || c == '\r') ? ' ' : c;
    return o;
}

std::string NanoRetroAchievements::achSetCachePath() const {
    // FNV-1a over the ROM path, so each game gets its own cache file.
    uint64_t h = 1469598103934665603ULL;
    for (unsigned char c : mRomPath) { h ^= c; h *= 1099511628211ULL; }
    char name[24];
    snprintf(name, sizeof(name), "%016llx", (unsigned long long) h);
    std::string dir = (mStateDir.empty() ? std::string("/data/system/nano_ra")
                                          : mStateDir) + "/sets";
    mkdir(dir.c_str(), 0700);
    return dir + "/" + name + ".tsv";
}

void NanoRetroAchievements::writeAchSetCache() {
    if (mRomPath.empty()) return;
    std::vector<AchievementInfo> snap;
    { std::lock_guard<std::mutex> lk(mUiDataMutex); snap = mAchList; }
    if (snap.empty()) return;   // never clobber a good cache with an empty set
    std::string path = achSetCachePath();
    std::string tmp = path + ".tmp";
    FILE* f = fopen(tmp.c_str(), "wb");
    if (!f) return;
    for (const auto& a : snap) {
        fprintf(f, "%u\t%u\t%d\t%s\t%s\t%s\t%s\n",
                a.id, a.points, a.unlocked ? 1 : 0,
                raSanitizeField(a.bucket).c_str(),
                raSanitizeField(a.title).c_str(),
                raSanitizeField(a.description).c_str(),
                raSanitizeField(a.badgeUrl).c_str());
    }
    // Only promote the new cache if it was written in full; never let a torn
    // write (disk full, etc.) replace a good cache with a partial set.
    fflush(f);
    bool werr = ferror(f);
    if (fclose(f) != 0 || werr) { unlink(tmp.c_str()); return; }
    rename(tmp.c_str(), path.c_str());
}

void NanoRetroAchievements::loadAchSetCache() {
    if (mRomPath.empty()) return;
    FILE* f = fopen(achSetCachePath().c_str(), "rb");
    if (!f) return;
    std::vector<AchievementInfo> snap;
    char line[2048];
    bool prevTruncated = false;
    while (fgets(line, sizeof(line), f)) {
        size_t len = strlen(line);
        bool ended = (len > 0 && line[len - 1] == '\n');
        bool continuation = prevTruncated;   // tail of a previous over-long line
        prevTruncated = !ended;              // this chunk did not finish the line
        // Only parse a chunk that is a complete standalone line: it must end in
        // a newline and not be the continuation of an over-long record. This
        // drops every piece of any line longer than the buffer instead of
        // turning it into garbage records.
        if (continuation || !ended) continue;
        std::string s(line);
        while (!s.empty() && (s.back() == '\n' || s.back() == '\r')) s.pop_back();
        // Seven tab-separated fields: id, points, unlocked, bucket, title,
        // description, badgeUrl (badgeUrl may be empty).
        std::string col[7];
        size_t start = 0;
        bool ok = true;
        for (int i = 0; i < 6; i++) {
            size_t t = s.find('\t', start);
            if (t == std::string::npos) { ok = false; break; }
            col[i] = s.substr(start, t - start);
            start = t + 1;
        }
        if (!ok) continue;
        col[6] = s.substr(start);
        AchievementInfo info;
        info.id = (uint32_t) strtoul(col[0].c_str(), nullptr, 10);
        info.points = (uint32_t) strtoul(col[1].c_str(), nullptr, 10);
        info.unlocked = (col[2] == "1");
        info.bucket = col[3];
        info.title = col[4];
        info.description = col[5];
        info.badgeUrl = col[6];
        if (info.id) snap.push_back(std::move(info));
    }
    fclose(f);
    if (snap.empty()) return;
    {
        std::lock_guard<std::mutex> lk(mUiDataMutex);
        if (!mAchList.empty()) return;   // live data already present; keep it
        mAchList = std::move(snap);
    }
    mUiGen.fetch_add(1, std::memory_order_relaxed);
    ALOGI("RA: loaded cached achievement set for the Achievements list");
}

// Emit an achievement unlock to the UI (banner + badge download + chime). Used
// both by the real rc_client trigger event and the ra_test_unlock debug hook.
void NanoRetroAchievements::emitUnlock(uint32_t id, const char* title,
                                       const char* description, uint32_t points,
                                       const char* badgeUrl) {
    RaUiEvent u;
    u.kind = RaUiEvent::Unlock;
    u.id = id;
    u.title = title ? title : "";
    u.subtitle = description ? description : "";
    u.points = points;
    u.badgeUrl = badgeUrl ? badgeUrl : "";
    pushUiEvent(u);
    // Do not request the badge here. The overlay's startNextBanner() requests it
    // for every banner exactly when that banner becomes visible (immediate or
    // dequeued), and badges are already prefetched to the on-disk cache. Asking
    // here as well caused the same badge to be downloaded and decoded twice,
    // which made the render thread create, destroy and re-create the banner's GL
    // texture within a single frame. The badge request is now owned solely by the
    // overlay, so each banner uploads its texture once.
    playTwinkle();
    // Move the just-unlocked achievement into the "Recently Unlocked" bucket so
    // the overlay's Achievements list reflects it. Deferred to the client loop
    // (not done here) because this can run inside an rc_client_do_frame event
    // callback, and re-entering rc_client_create_achievement_list there is best
    // avoided.
    mSnapshotDirty.store(true);
}

// ---------------------------------------------------------------------------
// client thread: owns rc_client, paces do_frame / idle, drains responses
// ---------------------------------------------------------------------------

void NanoRetroAchievements::buildUserAgent(char* out, size_t outSize) {
    char clause[128] = {0};
    if (mClient) rc_client_get_user_agent_clause(mClient, clause, sizeof(clause));
    char osrel[PROPERTY_VALUE_MAX] = {0};
    property_get("ro.build.version.release", osrel, "");
    // Format required by the RA server: <product>/<version> (<system>) <extensions>.
    // The product token has no spaces and the version is numeric so the server can
    // recognize the client once it is added to the approved-emulator list. Until
    // then this User-Agent is unknown to RA: the set shows a 0-point "Warning:
    // Unknown Emulator" entry and the server will not credit hardcore unlocks.
    snprintf(out, outSize, "GammaOS-DrasticNano/1.0.0 (Android %s) %s",
             osrel[0] ? osrel : "14", clause);
}

// Begin a login from stored credentials (client thread). Token is preferred,
// but after repeated token failures we fall back to the password (which also
// re-establishes a fresh token), so a stale token cannot lock the user out.
// mLoginInFlight prevents overlapping attempts; the loop retries on a timer.
void NanoRetroAchievements::attemptStoredLogin() {
    if (!mClient || mLoginInFlight.load()) return;
    std::string user, token, pass;
    if (!loadStoredCredentials(&user, &token, &pass)) return;
    const bool preferPassword = (mLoginFailures >= 3) && !pass.empty();
    if (!token.empty() && !preferPassword) {
        ALOGI("RA: logging in '%s' with stored token", user.c_str());
        mLoginInFlight.store(true);
        rc_client_begin_login_with_token(mClient, user.c_str(), token.c_str(),
                                         sLoginCallback, this);
    } else if (!pass.empty()) {
        ALOGI("RA: logging in '%s' with password", user.c_str());
        mLoginInFlight.store(true);
        rc_client_begin_login_with_password(mClient, user.c_str(), pass.c_str(),
                                            sLoginCallback, this);
    } else {
        ALOGW("RA: no usable stored credentials; idle until UI login");
    }
}

void NanoRetroAchievements::clientThreadMain() {
    mClient = rc_client_create(sReadMemory, sServerCall);
    if (!mClient) {
        ALOGE("RA: rc_client_create failed");
        return;
    }
    rc_client_set_userdata(mClient, this);
    rc_client_enable_logging(mClient, RC_CLIENT_LOG_LEVEL_WARN, sLogMessage);
    rc_client_set_event_handler(mClient, sEventHandler);

    {
        char ua[256];
        buildUserAgent(ua, sizeof(ua));
        std::lock_guard<std::mutex> lk(mHttpMutex);
        mUserAgent = ua;
    }
    ALOGI("RA: User-Agent %s", mUserAgent.c_str());

    // Hardcore: RetroAchievements recommends hardcore-on by default, but during
    // bring-up we honour persist.gammaos.drastic_nano.ra_hardcore (default 0 =
    // softcore) so an unvalidated build cannot put the user's account at risk.
    // Flip the default to 1 once the client is validated with the RA team.
    char hc[PROPERTY_VALUE_MAX] = {};
    property_get("persist.gammaos.drastic_nano.ra_hardcore", hc, "0");
    bool hcOn = (hc[0] == '1');
    // RetroAchievements forbids hardcore over a loaded save state. main.cpp is the
    // single decider of whether this session loaded a state (autoLoadSlot == 9) and
    // publishes it as ra_force_softcore. Drop hardcore ONLY when a state was
    // actually loaded, so a hardcore session that booted FRESH -- including a
    // hardcore Quick Resume, which now always boots fresh -- correctly STAYS
    // hardcore. Session-scoped: the persisted ra_hardcore setting is unchanged, so
    // a later softcore state-load or fresh hardcore launch behaves per this prop.
    if (hcOn && property_get_bool(
            "sys.gammaos.drastic_nano.ra_force_softcore", false)) {
        ALOGI("RA: a save state was loaded - dropping hardcore to softcore for this session");
        hcOn = false;
    }
    mHardcorePref.store(hcOn);
    rc_client_set_hardcore_enabled(mClient, hcOn ? 1 : 0);

    // Debug/testing aid (default off): load in-development "unofficial"
    // achievements too, so a set being authored against drastic-nano (for
    // example a Nintendo DS set that depends on Data TCM) can be exercised before
    // it is published. Production loads only the published core set.
    char unoff[PROPERTY_VALUE_MAX] = {};
    property_get("persist.gammaos.drastic_nano.ra_unofficial", unoff, "0");
    if (unoff[0] == '1') {
        rc_client_set_unofficial_enabled(mClient, 1);
        ALOGI("RA: unofficial (in-development) achievements ENABLED (debug)");
    }

    // Login from stored credentials (token preferred, else password). The game
    // is identified and loaded only after login succeeds (see onLoginResult),
    // so the authenticated load calls never race the login. If this first try
    // fails (e.g. the network is not up yet at boot), the loop below retries.
    attemptStoredLogin();

    auto lastLoginTry = steady_clock::now();
    auto lastLoadTry  = steady_clock::now();

    auto lastIdle = steady_clock::now();
    auto lastState = steady_clock::now();
    auto lastHeartbeat = steady_clock::now();
    // Offset so the first badge prefetch sweep fires a few seconds after start
    // (warming the cache from the pre-loaded set) rather than waiting a minute.
    auto lastBadgeSweep = steady_clock::now() - seconds(55);
    auto lastLbRefresh = steady_clock::now();
    uint64_t framesTotal = 0;
    mLastRenderTick = mRenderTick.load(std::memory_order_relaxed);
    std::string prevRp;

    while (!mStop.load()) {
        const auto now = steady_clock::now();
        // 1. Re-enter rc_client for any completed HTTP responses (this thread is
        //    the only one allowed to touch the client).
        for (;;) {
            HttpDone d;
            {
                std::lock_guard<std::mutex> lk(mDoneMutex);
                if (mDoneQueue.empty()) break;
                d = std::move(mDoneQueue.front());
                mDoneQueue.pop_front();
            }
            if (d.callback) {
                rc_api_server_response_t resp;
                memset(&resp, 0, sizeof(resp));
                resp.body = d.body.c_str();
                resp.body_length = d.body.size();
                resp.http_status_code = d.httpStatus;
                d.callback(&resp, d.callbackData);
            }
        }

        // 2. Reset request (raised when hardcore is toggled). Reset only the
        // rc_client achievement runtime here, never the emulator. drastic's
        // in-process soft reset (resetDS) is neutered by the boot-race longjmp
        // patch and only half-completes, which freezes the game, so it must not
        // be called. When the user ENABLES hardcore the overlay restarts the
        // game with a fresh process relaunch (mRestartFresh), which boots the
        // next session into hardcore from the persisted property; that is the
        // real "restart into hardcore". On the DISABLE path there is no relaunch,
        // and this rc_client_reset is all that is needed (the hardcore
        // restrictions are gated in the render loop on hardcoreActive()).
        if (mPendingReset.exchange(false)) {
            rc_client_reset(mClient);
        }

        // 2b. Login requested from the in-game UI.
        if (mPendingLogin.exchange(false)) {
            std::string u, p;
            {
                std::lock_guard<std::mutex> lk(mLoginMutex);
                u = mPendingUser;
                p = mPendingPass;
            }
            if (!u.empty() && !p.empty()) {
                ALOGI("RA: applying UI login for '%s'", u.c_str());
                mLoginInFlight.store(true);
                rc_client_begin_login_with_password(mClient, u.c_str(), p.c_str(),
                                                    sLoginCallback, this);
            }
        }

        // 2c. Auto-retry login if it has not succeeded yet (e.g. the network was
        //     down at boot). Retries every ~15s with stored credentials so a
        //     flaky-network start recovers on its own once connectivity returns.
        if (!mLoggedIn.load() && !mLoginInFlight.load() &&
            now - lastLoginTry >= seconds(15)) {
            lastLoginTry = now;
            attemptStoredLogin();
        }

        // 2d. Auto-retry the game load until it succeeds. The identify+load makes
        //     several server calls; a flaky network can time them out, so rather
        //     than give up (which left the Achievements view stuck on "Loading"),
        //     re-attempt every ~15s once logged in.
        if (mLoggedIn.load() && !mGameActive.load() && !mLoadInFlight.load() &&
            now - lastLoadTry >= seconds(15)) {
            lastLoadTry = now;
            attemptLoadGame();
        }

        // 3. Pause-spam query, evaluated only when the render thread actually
        //    wants to pause (calling can_pause every frame would reset its
        //    internal counter).
        if (mPauseQueryWanted.exchange(false)) {
            mCanPause.store(rc_client_can_pause(mClient, nullptr) != 0);
        }

        const bool paused = mPaused.load();

        bool framed = false;
        // Drive rc_client_do_frame off the render-loop vblank tick (~60Hz),
        // which matches the DS frame rate. The previous wall-clock pacing ran at
        // ~41Hz on this thread, under-sampling the emulator and missing
        // single-frame "edge" achievement triggers (level-complete flags that go
        // 1->0 for one frame). One do_frame per vblank samples ~once per emulated
        // frame, so those edges are caught.
        //
        // Gate on mPaused: opening the in-game overlay pauses the emulator (the
        // render loop keeps ticking to draw the menu, but DS Main RAM is frozen),
        // and so does sleep / screen-off. While paused we must NOT call do_frame
        // on the frozen memory image (the integration guide requires idle, not
        // do_frame, when the game is not advancing); we fall through to
        // rc_client_idle below instead. We still consume the render ticks here so
        // the pause does not leave a backlog to replay on resume.
        const int tickNow = mRenderTick.load(std::memory_order_relaxed);
        int tickDelta = tickNow - mLastRenderTick;
        if (tickDelta < 0) tickDelta = 0;           // wrapped/reset
        mLastRenderTick = tickNow;
        if (!paused && mGameActive.load() && tickDelta > 0) {
            // Call do_frame EXACTLY ONCE per advance, never in a batch. We read
            // live DS RAM here, and that RAM only ever holds the producer's most
            // recent frame, so looping do_frame for tickDelta>1 would feed
            // rc_client the SAME memory N times and collapse its Delta to equal
            // Current. That destroys single-frame edge triggers (a level-clear
            // flag that is 1 for one frame then 0): the 1 must survive as the
            // Delta on the very next call. One call keeps the previous poll's
            // value as the Delta, so a clean 1->0 edge is captured. A render
            // hitch (tickDelta>1) means the producer already overwrote the
            // skipped frames in RAM, so neither one call nor a batch can recover
            // them, and the batch only does harm.
            rc_client_do_frame(mClient);
            framed = true;
            framesTotal++;
        }

        if (framed) {
            lastIdle = now;
        } else if (now - lastIdle >= seconds(1)) {
            // Keep the player in the Active Players list while not processing.
            rc_client_idle(mClient);
            lastIdle = now;
        }

        // Debug (sys.gammaos.drastic_nano.ra_debug=1): edge-log the Sonic Rush
        // "Complete Zone N" trigger bytes whenever the level-clear flag 0x369ac8
        // changes, so we can see live which condition is (un)met when a level is
        // completed: stage 0x2c457c, character 0x2c4560 (0=Sonic), mode 0x2c457d,
        // progress 0x2c468c (the <=N condition), and the 0x369ac8 1->0 edge.
        if (mRaDebug && mRamBase && mGameActive.load()) {
            int v369 = mRamBase[0x369ac8 & mRamMask];
            if (v369 != mDbgLast369) {
                ALOGI("RA: DBG 369ac8=%d (was %d) | 2c457c(stage)=%d "
                      "2c4560(char)=%d 2c457d=%d 2c468c(prog)=%d",
                      v369, mDbgLast369,
                      mRamBase[0x2c457c & mRamMask], mRamBase[0x2c4560 & mRamMask],
                      mRamBase[0x2c457d & mRamMask], mRamBase[0x2c468c & mRamMask]);
                mDbgLast369 = v369;
            }
        }

        // An unlock (or the test hook) asked for a fresh achievement snapshot.
        // Done here, outside any do_frame event callback, to avoid re-entrancy.
        if (mSnapshotDirty.exchange(false)) {
            refreshAchievementSnapshot();
        }

        // Hardcore toggled from the in-game UI: apply it live. rc_client resets
        // the achievement runtime on a hardcore change, so refresh the snapshot.
        if (mHardcoreChange.exchange(false)) {
            const bool on = mHardcorePref.load();
            rc_client_set_hardcore_enabled(mClient, on ? 1 : 0);
            updateHardcoreActive();
            if (mGameActive.load()) refreshAchievementSnapshot();
            // Enabling hardcore live must restart the game fresh into hardcore
            // (the RetroAchievements convention). drastic's in-process soft reset
            // is unusable (a longjmp guard half-completes it and freezes the
            // game), so signal the render loop to do the same fresh process
            // relaunch as "Restart Game"; the next session boots into hardcore
            // from the persisted property. Disabling drops to softcore live with
            // no restart. This is intentionally not gated on the achievement set
            // having loaded: drastic-nano always has a ROM running, and the
            // restart must still happen when the RA load is mid-retry on a flaky
            // network (the relaunched session retries the load in hardcore).
            if (on) mHardcoreRestartPending.store(true);
            ALOGI("RA: hardcore %s (UI toggle)", on ? "ENABLED" : "disabled");
        }

        // 4. Refresh state the render thread reads (cheap, throttled).
        if (now - lastState >= milliseconds(400)) {
            lastState = now;
            updateHardcoreActive();
            char rp[256] = {0};
            rc_client_get_rich_presence_message(mClient, rp, sizeof(rp));
            {
                std::lock_guard<std::mutex> lk(mRichMutex);
                mRichPresence = rp;
            }
            // Logging rich presence when it changes proves the runtime is reading
            // Main RAM correctly (the presence string is evaluated from it).
            if (rp[0] && prevRp != rp) {
                prevRp = rp;
                ALOGI("RA: rich presence: %s", rp);
            }

            // Debug toggle for the trigger-byte edge log above.
            char dbg[PROPERTY_VALUE_MAX] = {};
            property_get("sys.gammaos.drastic_nano.ra_debug", dbg, "");
            mRaDebug = (dbg[0] == '1');

            // Debug: sys.gammaos.drastic_nano.ra_test_unlock=1 fires a synthetic
            // unlock (first achievement of the loaded set) so the banner + badge
            // + chime can be validated without reaching a real condition.
            char tu[PROPERTY_VALUE_MAX] = {};
            property_get("sys.gammaos.drastic_nano.ra_test_unlock", tu, "");
            if (tu[0] == '1') {
                property_set("sys.gammaos.drastic_nano.ra_test_unlock", "0");
                AchievementInfo pick;
                bool have = false;
                {
                    std::lock_guard<std::mutex> lk(mUiDataMutex);
                    // Prefer a real achievement so the test banner is
                    // representative. The server adds synthetic pseudo entries
                    // (e.g. the "Unknown Emulator" warning at id 101000001) at
                    // ids >= 100000000; real achievement ids are below that. Fall
                    // back to the first entry only if the set is all pseudos.
                    for (const auto& a : mAchList) {
                        if (a.id && a.id < 100000000) { pick = a; have = true; break; }
                    }
                    if (!have && !mAchList.empty()) { pick = mAchList.front(); have = true; }
                }
                if (have) {
                    ALOGI("RA: TEST unlock \"%s\" (%u pts)",
                          pick.title.c_str(), pick.points);
                    emitUnlock(pick.id, pick.title.c_str(),
                               pick.description.c_str(), pick.points,
                               pick.badgeUrl.c_str());
                }
            }

            // Debug: sys.gammaos.drastic_nano.ra_test_hardcore=1 enables (or =0
            // disables) hardcore live, exactly as the Achievements overlay toggle
            // does (setHardcorePref), so the hardcore-toggle path can be validated
            // without navigating the on-screen menu.
            char th[PROPERTY_VALUE_MAX] = {};
            property_get("sys.gammaos.drastic_nano.ra_test_hardcore", th, "");
            if (th[0] == '1' || th[0] == '0') {
                const bool on = (th[0] == '1');
                property_set("sys.gammaos.drastic_nano.ra_test_hardcore", "");
                ALOGI("RA: TEST hardcore %s (debug hook)",
                      on ? "enable" : "disable");
                setHardcorePref(on);
            }

            // Debug: sys.gammaos.drastic_nano.ra_test_indicator=1 raises a
            // synthetic challenge + measured-progress indicator for the first real
            // achievement (=0 clears them), so the in-gameplay indicators can be
            // checked without reaching a real primed/progress condition.
            char ind[PROPERTY_VALUE_MAX] = {};
            property_get("sys.gammaos.drastic_nano.ra_test_indicator", ind, "");
            if (ind[0] == '1' || ind[0] == '0') {
                property_set("sys.gammaos.drastic_nano.ra_test_indicator", "");
                AchievementInfo pick; bool have = false;
                {
                    std::lock_guard<std::mutex> lk(mUiDataMutex);
                    for (const auto& a : mAchList)
                        if (a.id && a.id < 100000000) { pick = a; have = true; break; }
                }
                if (have) {
                    ALOGI("RA: TEST indicator %s \"%s\"",
                          ind[0] == '1' ? "show" : "hide", pick.title.c_str());
                    if (ind[0] == '1') {
                        RaUiEvent c; c.kind = RaUiEvent::ChallengeShow;
                        c.id = pick.id; c.badgeUrl = pick.badgeUrl;
                        pushUiEvent(c);
                        RaUiEvent p; p.kind = RaUiEvent::ProgressShow;
                        p.id = pick.id; p.title = pick.title;
                        p.subtitle = "23/50"; p.badgeUrl = pick.badgeUrl;
                        pushUiEvent(p);
                    } else {
                        RaUiEvent c; c.kind = RaUiEvent::ChallengeHide;
                        c.id = pick.id; pushUiEvent(c);
                        RaUiEvent p; p.kind = RaUiEvent::ProgressHide;
                        pushUiEvent(p);
                    }
                }
            }
        }

        // Periodic heartbeat: shows the per-frame achievement evaluation is
        // running against live memory.
        if (now - lastHeartbeat >= seconds(5)) {
            lastHeartbeat = now;
            if (mGameActive.load()) {
                ALOGI("RA: processing - %llu emulated frames evaluated, hardcore=%d",
                      (unsigned long long) framesTotal, mHardcoreActive.load() ? 1 : 0);
            }
        }

        // Pull every achievement badge into the on-disk cache ahead of time so
        // an unlock banner always has its icon ready, even the first one. This
        // runs from whatever set is loaded: the cached set (pre-loaded at start,
        // so badges download before the live network load even finishes) or the
        // live set. It is gated only by a 60s timer (the first fires shortly
        // after start), NOT on gameActive, so a poor link still warms the cache.
        // prefetchAllBadges only queues what is missing and no-ops when the set
        // is empty, complete, or a batch is still pending, so this is cheap.
        if (now - lastBadgeSweep >= seconds(60)) {
            lastBadgeSweep = now;
            prefetchAllBadges();
        }

        // Refresh the leaderboard tracker values for the bottom-screen panel
        // (they change as the player progresses). Cheap; only while a game runs.
        if (mGameActive.load() && now - lastLbRefresh >= milliseconds(1500)) {
            lastLbRefresh = now;
            refreshLeaderboardSnapshot();
        }

        // Online leaderboard rankings fetch requested by the bottom-screen panel
        // (the user tapped a leaderboard). One in flight at a time.
        if (mGameActive.load() && !mLbFetchInFlight.load()) {
            uint32_t want = mPendingLbFetch.exchange(0);
            if (want) {
                mLbFetchingId.store(want);
                mLbFetchInFlight.store(true);
                rc_client_begin_fetch_leaderboard_entries(
                    mClient, want, 1, 25, sLbEntriesCallback, this);
            }
        }

        // Pace the loop. While a game is active, poll the render tick frequently
        // (every 2ms) so each vblank tick is turned into a do_frame with minimal
        // lag (the memory read stays close to the rendered frame). Otherwise idle
        // at a low rate.
        if (mGameActive.load()) {
            std::this_thread::sleep_for(milliseconds(2));
        } else {
            std::this_thread::sleep_for(milliseconds(8));
        }
    }

    rc_client_unload_game(mClient);
    rc_client_destroy(mClient);
    mClient = nullptr;
}

void NanoRetroAchievements::updateHardcoreActive() {
    if (!mClient) { mHardcoreActive.store(false); return; }
    int hc = rc_client_get_hardcore_enabled(mClient);
    int processing = rc_client_is_processing_required(mClient);
    mHardcoreActive.store(hc != 0 && processing != 0 && mGameActive.load());
}

// ---------------------------------------------------------------------------
// event handling
// ---------------------------------------------------------------------------

void NanoRetroAchievements::onEvent(const rc_client_event_t* event) {
    switch (event->type) {
        case RC_CLIENT_EVENT_ACHIEVEMENT_TRIGGERED: {
            const rc_client_achievement_t* a = event->achievement;
            // Banner (with the colour badge) + the achievement chime.
            emitUnlock(a->id, a->title, a->description, a->points, a->badge_url);
            ALOGI("RA: achievement unlocked id=%u \"%s\" (%u pts)",
                  a->id, a->title ? a->title : "", a->points);
            break;
        }
        case RC_CLIENT_EVENT_ACHIEVEMENT_CHALLENGE_INDICATOR_SHOW:
        case RC_CLIENT_EVENT_ACHIEVEMENT_CHALLENGE_INDICATOR_HIDE: {
            const rc_client_achievement_t* a = event->achievement;
            RaUiEvent u;
            u.kind = (event->type == RC_CLIENT_EVENT_ACHIEVEMENT_CHALLENGE_INDICATOR_SHOW)
                         ? RaUiEvent::ChallengeShow : RaUiEvent::ChallengeHide;
            u.id = a->id;
            u.badgeName = a->badge_name;
            u.badgeUrl = a->badge_url ? a->badge_url : "";
            pushUiEvent(u);
            break;
        }
        case RC_CLIENT_EVENT_ACHIEVEMENT_PROGRESS_INDICATOR_SHOW:
        case RC_CLIENT_EVENT_ACHIEVEMENT_PROGRESS_INDICATOR_UPDATE: {
            const rc_client_achievement_t* a = event->achievement;
            RaUiEvent u;
            u.kind = RaUiEvent::ProgressShow;
            u.id = a->id;
            u.title = a->title ? a->title : "";
            u.subtitle = a->measured_progress;
            u.badgeName = a->badge_name;
            // Use the colour (unlocked) badge URL, not badge_locked_url. The
            // on-disk badge cache is keyed by achievement id only, and the
            // prefetch, the unlock banner, the achievement list and the challenge
            // indicator all use the colour badge; using the grayscale URL here
            // would, on a slow link where prefetch has not yet covered this id,
            // write the grayscale variant under the same <id>.png and make the
            // colour consumers show grayscale. Keeping one colour badge per id is
            // consistent across the whole UI.
            u.badgeUrl = a->badge_url ? a->badge_url : "";
            pushUiEvent(u);
            break;
        }
        case RC_CLIENT_EVENT_ACHIEVEMENT_PROGRESS_INDICATOR_HIDE: {
            RaUiEvent u;
            u.kind = RaUiEvent::ProgressHide;
            pushUiEvent(u);
            break;
        }
        case RC_CLIENT_EVENT_GAME_COMPLETED: {
            RaUiEvent u;
            u.kind = RaUiEvent::Mastery;
            const rc_client_game_t* g = rc_client_get_game_info(mClient);
            if (g) {
                // rc_client_game_t exposes title/badge_name; keep it minimal here.
                u.title = trDyn("Game completed");
            }
            pushUiEvent(u);
            ALOGI("RA: game completed (mastery)");
            break;
        }
        case RC_CLIENT_EVENT_LEADERBOARD_STARTED:
        case RC_CLIENT_EVENT_LEADERBOARD_SUBMITTED:
        case RC_CLIENT_EVENT_LEADERBOARD_FAILED: {
            RaUiEvent u;
            u.kind = (event->type == RC_CLIENT_EVENT_LEADERBOARD_STARTED) ? RaUiEvent::LeaderboardStarted
                   : (event->type == RC_CLIENT_EVENT_LEADERBOARD_SUBMITTED) ? RaUiEvent::LeaderboardSubmitted
                   : RaUiEvent::LeaderboardFailed;
            if (event->leaderboard && event->leaderboard->title)
                u.title = event->leaderboard->title;
            pushUiEvent(u);
            break;
        }
        case RC_CLIENT_EVENT_RESET:
            // rc_client wants the emulated system reset (e.g. hardcore enabled).
            ALOGI("RA: reset requested by runtime");
            mPendingReset.store(true);
            break;
        case RC_CLIENT_EVENT_SERVER_ERROR: {
            RaUiEvent u;
            u.kind = RaUiEvent::ServerError;
            if (event->server_error && event->server_error->error_message)
                u.subtitle = event->server_error->error_message;
            pushUiEvent(u);
            ALOGW("RA: server error: %s", u.subtitle.c_str());
            break;
        }
        case RC_CLIENT_EVENT_DISCONNECTED:
            ALOGW("RA: server disconnected (unlocks pending)");
            break;
        case RC_CLIENT_EVENT_RECONNECTED:
            ALOGI("RA: server reconnected (pending unlocks flushed)");
            break;
        default:
            break;
    }
}

void NanoRetroAchievements::onLoginResult(int result, const char* error_message) {
    mLoginInFlight.store(false);
    if (result != RC_OK) {
        mLoginFailures++;
        ALOGW("RA: login failed (%d, attempt %d): %s; will retry",
              result, mLoginFailures, error_message ? error_message : "no message");
        RaUiEvent u;
        u.kind = RaUiEvent::Login;
        u.ok = false;
        u.subtitle = error_message ? error_message : trDyn("Login failed");
        pushUiEvent(u);
        mUiGen.fetch_add(1, std::memory_order_relaxed);
        return;
    }
    mLoginFailures = 0;
    mLoggedIn.store(true);
    mUiGen.fetch_add(1, std::memory_order_relaxed);
    const rc_client_user_t* user = rc_client_get_user_info(mClient);
    if (user) {
        if (user->username && user->token) storeToken(user->username, user->token);
        ALOGI("RA: logged in as %s (%u points)",
              user->display_name ? user->display_name : "?", user->score);
        {
            std::lock_guard<std::mutex> lk(mUiDataMutex);
            mDisplayName = user->display_name ? user->display_name
                          : (user->username ? user->username : "");
        }
        RaUiEvent u;
        u.kind = RaUiEvent::Login;
        u.ok = true;
        u.title = user->display_name ? user->display_name : "";
        pushUiEvent(u);
    }

    // Now that we are logged in, identify and load the game (rc_client hashes the
    // ROM and resolves it). Doing this here, rather than in parallel with the
    // login request, guarantees the authenticated load calls have a valid session.
    attemptLoadGame();
}

// Begin (or re-begin) the game identify+load (client thread). mLoadInFlight
// prevents overlapping attempts; the client loop re-attempts on a timer until it
// succeeds, so a flaky network during the load self-recovers instead of leaving
// the Achievements view stuck on "Loading...".
void NanoRetroAchievements::attemptLoadGame() {
    if (!mClient || mLoadInFlight.load() || mGameActive.load()) return;
    mLoadInFlight.store(true);
    ALOGI("RA: identifying and loading game");
    rc_client_begin_identify_and_load_game(mClient, RC_CONSOLE_NINTENDO_DS,
                                           mRomPath.c_str(), nullptr, 0,
                                           sLoadCallback, this);
}

void NanoRetroAchievements::onLoadResult(int result, const char* error_message) {
    mGameLoadAttempted.store(true);
    mLoadInFlight.store(false);
    if (result != RC_OK) {
        // Leave mGameActive false; the client loop re-attempts the load on a
        // timer until it succeeds, so a slow/flaky network (the identify+load
        // makes several server calls) self-recovers rather than giving up.
        mLoadFailures++;
        ALOGW("RA: game load failed (%d, attempt %d): %s; will retry",
              result, mLoadFailures, error_message ? error_message : "no message");
        mGameActive.store(false);
        mUiGen.fetch_add(1, std::memory_order_relaxed);
        return;
    }
    mLoadFailures = 0;
    mGameActive.store(true);

    // Build the achievement-list snapshot for the overlay (grouped by progress).
    refreshAchievementSnapshot();

    // Now connected with the set loaded: warm the on-disk badge cache so icons
    // survive the flaky wifi / a reboot and the unlock banner shows instantly.
    prefetchAllBadges();

    // Build the leaderboard list for the bottom-screen panel.
    refreshLeaderboardSnapshot();

    rc_client_user_game_summary_t summary;
    memset(&summary, 0, sizeof(summary));
    rc_client_get_user_game_summary(mClient, &summary);
    const rc_client_game_t* g = rc_client_get_game_info(mClient);

    // Diagnostic: the resolved game plus the achievement-set breakdown. The
    // unsupported count is the load-time result of rc_client_validate_addresses
    // running every memory reference through the read callback: any reference to
    // an address the integration does not back (e.g. Data TCM when it cannot be
    // resolved) lands here, disabled rather than mis-evaluated.
    if (g) {
        ALOGI("RA: game id=%u console=%u title='%s' hash=%s | "
              "achievements core=%u unofficial=%u unlocked=%u UNSUPPORTED=%u | dtcm=%s",
              g->id, g->console_id, g->title ? g->title : "",
              g->hash ? g->hash : "?",
              summary.num_core_achievements, summary.num_unofficial_achievements,
              summary.num_unlocked_achievements, summary.num_unsupported_achievements,
              mDtcmBase ? "served" : "unresolved");
    }

    // Per the integration guide: if the game has no RA processing, disable
    // hardcore while it is loaded.
    if (!rc_client_is_processing_required(mClient)) {
        rc_client_set_hardcore_enabled(mClient, 0);
        ALOGI("RA: game has no achievements; hardcore disabled for this game");
    }
    updateHardcoreActive();

    RaUiEvent u;
    u.kind = RaUiEvent::GamePlacard;
    u.title = trDyn("RetroAchievements");
    char msg[192];
    if (summary.num_core_achievements == 0) {
        snprintf(msg, sizeof(msg), "%s", trDyn("This game has no achievements."));
    } else {
        // Always state the active mode at game start so the player can see
        // whether this session is Hardcore or Softcore (RA compliance: the
        // hardcore state must be visibly indicated during play). On enabling
        // hardcore the game restarts and reloads, so this fires again and
        // announces "Hardcore mode" for the fresh hardcore session.
        snprintf(msg, sizeof(msg),
                 trDyn("%s. You have %u of %u achievements unlocked."),
                 mHardcoreActive.load() ? trDyn("Hardcore mode") : trDyn("Softcore mode"),
                 summary.num_unlocked_achievements, summary.num_core_achievements);
    }
    u.subtitle = msg;
    pushUiEvent(u);
    ALOGI("RA: game loaded - %s", msg);
}

// ---------------------------------------------------------------------------
// UI event queue + small queries
// ---------------------------------------------------------------------------

void NanoRetroAchievements::pushUiEvent(const RaUiEvent& ev) {
    std::lock_guard<std::mutex> lk(mUiMutex);
    if (mUiQueue.size() > 64) mUiQueue.pop_front();   // never grow unbounded
    mUiQueue.push_back(ev);
}

bool NanoRetroAchievements::popUiEvent(RaUiEvent* out) {
    std::lock_guard<std::mutex> lk(mUiMutex);
    if (mUiQueue.empty()) return false;
    *out = std::move(mUiQueue.front());
    mUiQueue.pop_front();
    return true;
}

bool NanoRetroAchievements::canPauseNow() {
    mPauseQueryWanted.store(true);
    return mCanPause.load();
}

std::string NanoRetroAchievements::richPresence() {
    std::lock_guard<std::mutex> lk(mRichMutex);
    return mRichPresence;
}

std::string NanoRetroAchievements::userDisplayName() {
    std::lock_guard<std::mutex> lk(mUiDataMutex);
    return mDisplayName;
}

void NanoRetroAchievements::setHardcorePref(bool on) {
    mHardcorePref.store(on);
    mHardcoreChange.store(true);   // applied on the client thread
    // Persist so the next launch starts in the same mode.
    property_set("persist.gammaos.drastic_nano.ra_hardcore", on ? "1" : "0");
}

bool NanoRetroAchievements::isEnabled() const {
    return property_get_bool("persist.gammaos.drastic_nano.ra_enabled", false);
}

void NanoRetroAchievements::setEnabled(bool on) {
    property_set("persist.gammaos.drastic_nano.ra_enabled", on ? "1" : "0");
    if (on) {
        // Start live this session if a game is loaded. startClient() auto-runs
        // attemptStoredLogin(), so a previously-credentialed account resumes
        // without re-entering a password; with no token the UI shows the login.
        if (!mStarted) startClient();
    } else {
        // Stop live (kills any in-flight curl, joins the worker threads). Quick
        // because the client is paused while the menu is open.
        if (mStarted) shutdown();
    }
    ALOGI("RA: master %s from UI", on ? "ENABLED" : "DISABLED");
}

void NanoRetroAchievements::requestLogin(const std::string& user, const std::string& pass) {
    if (user.empty() || pass.empty()) return;
    {
        std::lock_guard<std::mutex> lk(mLoginMutex);
        mPendingUser = user;
        mPendingPass = pass;
    }
    // The user explicitly logging in IS the signal to enable RetroAchievements.
    // Persist it so the client runs on this and every future launch -- without
    // this the client thread (the SOLE consumer of a login) may not be running
    // and the login silently no-ops, leaving the cred file behind. This is the
    // exact bug on a unit where ra_enabled was never set.
    property_set("persist.gammaos.drastic_nano.ra_enabled", "1");

    // Persist as the bootstrap credential. Drop any stale token first so the
    // fresh username/password win over a previous (possibly wrong) token when the
    // client reads stored credentials.
    std::string dir = mStateDir.empty() ? std::string("/data/system/nano_ra") : mStateDir;
    mkdir(dir.c_str(), 0700);
    unlink((dir + "/token").c_str());
    std::string cred = dir + "/cred";
    std::string tmp = cred + ".tmp";
    FILE* f = fopen(tmp.c_str(), "wb");
    if (f) {
        fprintf(f, "%s\n%s\n", user.c_str(), pass.c_str());
        fflush(f);
        fsync(fileno(f));
        fclose(f);
        chmod(tmp.c_str(), 0600);
        rename(tmp.c_str(), cred.c_str());
    }

    if (!mStarted) {
        // RA was not pre-enabled, so the client isn't running. Bring it up now;
        // its startup attemptStoredLogin() reads the cred we just wrote and logs
        // in this session (no mPendingLogin needed -- that would double-login).
        if (!startClient()) {
            ALOGW("RA: could not start the client now (RAM unresolved); the login "
                  "will apply on the next launch (RA is now enabled)");
            return;
        }
    } else {
        // Client already running: hand the new credentials to its loop.
        mPendingLogin.store(true);
    }
    ALOGI("RA: login requested from UI for '%s'", user.c_str());
}

std::vector<NanoRetroAchievements::AchievementInfo>
NanoRetroAchievements::achievementSnapshot() {
    std::lock_guard<std::mutex> lk(mUiDataMutex);
    return mAchList;
}

std::vector<NanoRetroAchievements::LeaderboardInfo>
NanoRetroAchievements::leaderboardSnapshot() {
    std::lock_guard<std::mutex> lk(mUiDataMutex);
    return mLbList;
}

void NanoRetroAchievements::requestLeaderboardEntries(uint32_t lbId) {
    mPendingLbFetch.store(lbId);
}

std::vector<NanoRetroAchievements::LeaderboardEntry>
NanoRetroAchievements::leaderboardEntriesSnapshot(uint32_t* outLbId, bool* outLoading) {
    std::lock_guard<std::mutex> lk(mUiDataMutex);
    if (outLoading) *outLoading = mLbFetchInFlight.load();
    if (outLbId) *outLbId = mLbEntriesId;
    return mLbEntries;
}

void NanoRetroAchievements::sLbEntriesCallback(int result, const char* /*error_message*/,
        rc_client_leaderboard_entry_list_t* list, rc_client_t* client, void* /*userdata*/) {
    NanoRetroAchievements* self = (NanoRetroAchievements*) rc_client_get_userdata(client);
    if (self) self->onLbEntries(result, list);
}

void NanoRetroAchievements::onLbEntries(int result, rc_client_leaderboard_entry_list_t* list) {
    std::vector<LeaderboardEntry> entries;
    if (result == RC_OK && list) {
        for (uint32_t i = 0; i < list->num_entries; i++) {
            const rc_client_leaderboard_entry_t& e = list->entries[i];
            LeaderboardEntry le;
            le.rank = e.rank;
            le.user = e.user ? e.user : "";
            le.score = e.display;   // fixed-size formatted score
            entries.push_back(std::move(le));
        }
    }
    if (list) rc_client_destroy_leaderboard_entry_list(list);
    const uint32_t id = mLbFetchingId.load();
    const size_t cnt = entries.size();
    {
        std::lock_guard<std::mutex> lk(mUiDataMutex);
        mLbEntries = std::move(entries);
        mLbEntriesId = id;
        // Clear the in-flight flag inside the lock so a reader never sees
        // "loading" together with the already-final entries (1-frame flicker).
        mLbFetchInFlight.store(false);
    }
    mUiGen.fetch_add(1, std::memory_order_relaxed);
    ALOGI("RA: leaderboard %u rankings: %zu entries (result %d)", id, cnt, result);
}

void NanoRetroAchievements::refreshLeaderboardSnapshot() {
    if (!mClient) return;
    rc_client_leaderboard_list_t* list = rc_client_create_leaderboard_list(
        mClient, RC_CLIENT_LEADERBOARD_LIST_GROUPING_NONE);
    std::vector<LeaderboardInfo> snap;
    if (list) {
        for (uint32_t b = 0; b < list->num_buckets; b++) {
            const rc_client_leaderboard_bucket_t& bk = list->buckets[b];
            for (uint32_t i = 0; i < bk.num_leaderboards; i++) {
                const rc_client_leaderboard_t* lb = bk.leaderboards[i];
                if (!lb) continue;
                LeaderboardInfo info;
                info.id = lb->id;
                info.title = lb->title ? lb->title : "";
                info.description = lb->description ? lb->description : "";
                info.value = lb->tracker_value ? lb->tracker_value : "";
                info.state = lb->state;
                snap.push_back(std::move(info));
            }
        }
        rc_client_destroy_leaderboard_list(list);
    }
    {
        std::lock_guard<std::mutex> lk(mUiDataMutex);
        mLbList = std::move(snap);
    }
}

bool NanoRetroAchievements::loadCachedBadge(uint32_t achId, std::vector<uint8_t>* rgba,
                                            int* w, int* h) {
    if (!achId || !rgba || !w || !h) return false;
    std::string path = "/data/system/nano_ra/badges/" + std::to_string(achId) + ".png";
    FILE* f = fopen(path.c_str(), "rb");
    if (!f) return false;
    std::string body;
    char buf[4096];
    size_t r;
    while ((r = fread(buf, 1, sizeof(buf), f)) > 0) body.append(buf, r);
    fclose(f);
    if (body.empty()) return false;
    int comp = 0;
    unsigned char* px = stbi_load_from_memory(
        reinterpret_cast<const unsigned char*>(body.data()),
        (int) body.size(), w, h, &comp, 4);
    if (!px || *w <= 0 || *h <= 0) { if (px) stbi_image_free(px); return false; }
    rgba->assign(px, px + (size_t)(*w) * (*h) * 4);
    stbi_image_free(px);
    return true;
}

// ---------------------------------------------------------------------------
// credential storage
// ---------------------------------------------------------------------------

bool NanoRetroAchievements::loadStoredCredentials(std::string* user, std::string* token,
                                                  std::string* password) {
    // token file: line1 username, line2 token
    if (readFileLines(mStateDir + "/token", user, token) && !user->empty() && !token->empty()) {
        if (password) password->clear();
        return true;
    }
    // cred file (one-time bootstrap for testing): line1 username, line2 password
    std::string u, p;
    if (readFileLines(mStateDir + "/cred", &u, &p) && !u.empty() && !p.empty()) {
        if (user) *user = u;
        if (token) token->clear();
        if (password) *password = p;
        return true;
    }
    return false;
}

void NanoRetroAchievements::storeToken(const std::string& user, const std::string& token) {
    std::string path = mStateDir + "/token";
    std::string tmp = path + ".tmp";
    FILE* f = fopen(tmp.c_str(), "wb");
    if (!f) return;
    fprintf(f, "%s\n%s\n", user.c_str(), token.c_str());
    fflush(f);
    fsync(fileno(f));
    fclose(f);
    chmod(tmp.c_str(), 0600);
    rename(tmp.c_str(), path.c_str());
    // A captured token supersedes any bootstrap password file.
    unlink((mStateDir + "/cred").c_str());
}

// ---------------------------------------------------------------------------
// on-device Main RAM proof (non-perturbing)
// ---------------------------------------------------------------------------

void NanoRetroAchievements::verifyMainRamOnDevice(const std::string& romPath) {
    // Read the ROM header once: the 12-byte title at offset 0 and the ARM9 load
    // address at 0x28.
    FILE* f = fopen(romPath.c_str(), "rb");
    if (!f) {
        ALOGW("RA: PROOF - cannot open ROM '%s' for verification", romPath.c_str());
        return;
    }
    unsigned char hdr[0x200];
    size_t n = fread(hdr, 1, sizeof(hdr), f);
    fclose(f);
    if (n < 0x160) {
        ALOGW("RA: PROOF - ROM header too short (%zu bytes)", n);
        return;
    }
    char title[13];
    memcpy(title, hdr, 12);
    title[12] = '\0';
    int titleNonZero = 0;
    for (int i = 0; i < 12; i++) if (hdr[i] > 0x20 && hdr[i] < 0x7f) titleNonZero++;
    uint32_t arm9Ram = (uint32_t)hdr[0x28] | ((uint32_t)hdr[0x29] << 8) |
                       ((uint32_t)hdr[0x2a] << 16) | ((uint32_t)hdr[0x2b] << 24);
    uint32_t arm9RamOff = (arm9Ram >= 0x02000000) ? (arm9Ram - 0x02000000) : 0;

    ALOGI("RA: PROOF - verifying DS Main RAM for title='%s' (arm9RamAddr=0x%x); "
          "retrying until cartridge load completes", title, arm9Ram);

    // The DS BIOS / cartridge load finishes a little after the first frame, so the
    // header mirror (0x027FFE00) and the ARM9 image are not all present at once.
    // Poll for up to 15 s, passing as soon as the data appears.
    const int kMaxTries = 75;          // 75 * 200 ms = 15 s
    for (int attempt = 0; attempt < kMaxTries && !mStop.load(); attempt++) {
        DrasticRunner::DsMainRam m = mRunner ? mRunner->dsMainRam()
                                             : DrasticRunner::DsMainRam{};
        uint8_t* base = m.valid() ? m.base : nullptr;
        if (base) {
            // Signal 1: the title appears in Main RAM (cart header is mirrored to
            // 0x027FFE00 once the firmware/cart load completes).
            int titleHits = 0;
            uint32_t firstHit = 0;
            if (titleNonZero >= 3) {
                if (memcmp(base + (0x3FFE00 & m.mask), hdr, 12) == 0) {
                    titleHits = 1;
                    firstHit = 0x3FFE00;
                } else {
                    for (uint32_t off = 0; off + 12 <= (m.mask + 1); off++) {
                        if (memcmp(base + off, hdr, 12) == 0) {
                            titleHits = 1;
                            firstHit = off;
                            break;
                        }
                    }
                }
            }
            // Signal 2: the ARM9 image region is populated (non-zero).
            int arm9NonZero = 0;
            uint32_t a9 = (arm9RamOff + 0x4000);
            for (int i = 0; i < 256; i++) if (base[(a9 + i) & m.mask]) arm9NonZero++;

            if (titleHits > 0) {
                ALOGI("RA: PROOF - PASS after %dms: in-process DS Main RAM confirmed "
                      "(base=%p, title at 0x%x, arm9NonZero=%d/256)",
                      attempt * 200, (void*)base, firstHit, arm9NonZero);
                // Confirm the Data TCM region too: that it resolves, holds live
                // data (the ARM9 stack, full of 0x02xxxxxx / 0x040000xx values),
                // and that the read callback serves it at the RetroAchievements
                // flat address 0x1000000 matching a direct read.
                DrasticRunner::DsDataTcm tcm = mRunner ? mRunner->dsDataTcm()
                                                       : DrasticRunner::DsDataTcm{};
                if (tcm.valid()) {
                    uint8_t direct[16] = {0}, viaCb[16] = {0};
                    memcpy(direct, tcm.base, sizeof(direct));
                    uint32_t got = readMemory(0x1000000, viaCb, sizeof(viaCb));
                    int dtcmNonZero = 0;
                    for (uint32_t i = 0; i <= tcm.mask; i++)
                        if (tcm.base[i]) dtcmNonZero++;
                    ALOGI("RA: PROOF - Data TCM confirmed (base=%p, 16KB, nonZero=%d/%u), "
                          "read callback 0x1000000 served=%u bytes, match=%d",
                          (void*)tcm.base, dtcmNonZero, tcm.mask + 1, got,
                          (got == sizeof(viaCb) &&
                           memcmp(direct, viaCb, sizeof(direct)) == 0) ? 1 : 0);
                } else {
                    ALOGW("RA: PROOF - Data TCM unresolved; DTCM-referencing achievements "
                          "would be marked unsupported (Main RAM is unaffected)");
                }
                return;
            }
        }
        usleep(200000);
    }
    DrasticRunner::DsMainRam m = mRunner ? mRunner->dsMainRam()
                                         : DrasticRunner::DsMainRam{};
    ALOGE("RA: PROOF - FAIL after 15s: title '%s' not found in Main RAM (base=%p); "
          "achievement reads may be unreliable", title,
          m.valid() ? (void*)m.base : nullptr);
}

} // namespace android
