// GammaOS Nano: the "MTP active" screen.
//
// Selecting File Transfer (MTP) in any theme opens a modal screen that owns the display until the
// user stops MTP with Back / B. While it is up nano keeps the transfer working by force: on entry,
// and again every time a USB cable is connected, it "rejigs" the gadget (functions none, then mtp).
// A plain switch to mtp links the function but does not reliably start MtpService (measured on the
// RG DS Plus: the function appeared, the service count stayed 0); the none -> mtp toggle re-fires the
// USB_STATE broadcast that makes MediaProvider's MtpReceiver start MtpService, and it does so even
// when the media module process was reaped earlier (the broadcast re-launches it). After each rejig
// the media module process is pinned at oom_score_adj -1000 so the low memory killer never reaps it
// again while this screen is up, and a watchdog re-rejigs if the process or the function disappears
// anyway. Leaving the screen switches the gadget back to charge-only and restores the priority.
//
// USB presence is read from /sys/class/power_supply/usb/online; sys.usb.state is empty on this
// device so it is never used for that.
#include "NanoMenu.h"
#include "NanoI18n.h"    // trDyn() runtime translation of the screen strings

#include <dirent.h>
#include <fcntl.h>
#include <unistd.h>
#include <sys/stat.h>
#include <atomic>
#include <chrono>
#include <cstring>
#include <string>
#include <thread>

namespace android {

namespace {

int64_t mtpNowMs() {
    return std::chrono::duration_cast<std::chrono::milliseconds>(
               std::chrono::steady_clock::now().time_since_epoch()).count();
}

bool readSmallFile(const char* path, std::string& out) {
    int fd = open(path, O_RDONLY | O_CLOEXEC);
    if (fd < 0) return false;
    char buf[256]; ssize_t n = read(fd, buf, sizeof(buf) - 1); close(fd);
    if (n <= 0) return false;
    buf[n] = 0; out.assign(buf);
    while (!out.empty() && (out.back() == '\n' || out.back() == ' ')) out.pop_back();
    return true;
}

bool writeSmallFile(const char* path, const char* val) {
    int fd = open(path, O_WRONLY | O_CLOEXEC);
    if (fd < 0) return false;
    ssize_t n = write(fd, val, strlen(val)); close(fd);
    return n == (ssize_t)strlen(val);
}

// A USB host is connected. The charger supply's online flag flips with the cable on this device;
// the UDC state ("configured") is the second opinion so a dumb charger does not read as a PC.
bool mtpUsbOnline() {
    std::string s;
    if (readSmallFile("/sys/class/power_supply/usb/online", s) && s == "1") return true;
    // Fall back to the gadget controller's own state when the supply node is missing.
    DIR* d = opendir("/sys/class/udc"); if (!d) return false;
    bool on = false;
    while (dirent* e = readdir(d)) {
        if (e->d_name[0] == '.') continue;
        std::string p = std::string("/sys/class/udc/") + e->d_name + "/state";
        if (readSmallFile(p.c_str(), s) && s == "configured") { on = true; break; }
    }
    closedir(d);
    return on;
}

// The ffs.mtp function is linked into the active gadget configuration.
bool mtpFunctionLinked() {
    DIR* d = opendir("/config/usb_gadget/g1/configs/b.1"); if (!d) return false;
    bool linked = false;
    while (dirent* e = readdir(d)) {
        if (strncmp(e->d_name, "function", 8) != 0) continue;
        std::string p = std::string("/config/usb_gadget/g1/configs/b.1/") + e->d_name;
        char tgt[256]; ssize_t n = readlink(p.c_str(), tgt, sizeof(tgt) - 1);
        if (n > 0) { tgt[n] = 0; if (strstr(tgt, "ffs.mtp")) { linked = true; break; } }
    }
    closedir(d);
    return linked;
}

// pid of the MediaProvider module process that hosts MtpService, 0 when it is not running.
int mtpMediaModulePid() {
    DIR* d = opendir("/proc"); if (!d) return 0;
    int found = 0;
    while (dirent* e = readdir(d)) {
        if (e->d_name[0] < '0' || e->d_name[0] > '9') continue;
        std::string p = std::string("/proc/") + e->d_name + "/cmdline";
        int fd = open(p.c_str(), O_RDONLY | O_CLOEXEC); if (fd < 0) continue;
        char buf[128] = {}; ssize_t n = read(fd, buf, sizeof(buf) - 1); close(fd);
        if (n > 0 && strcmp(buf, "com.android.providers.media.module") == 0) { found = atoi(e->d_name); break; }
    }
    closedir(d);
    return found;
}

// Pin (or restore) the media module's OOM priority. -1000 is OOM_SCORE_ADJ_MIN: neither the kernel
// OOM killer nor lmkd will reap it. -700 is what the framework gives it (a persistent process).
void mtpSetMediaModuleAdj(int pid, int adj) {
    if (pid <= 0) return;
    char p[64], v[16];
    snprintf(p, sizeof p, "/proc/%d/oom_score_adj", pid);
    snprintf(v, sizeof v, "%d", adj);
    writeSmallFile(p, v);
}

}  // namespace

// Rejig the gadget on a detached worker: none -> mtp, then wait for the function and the service
// host, pin the host, and report. Never run two at once; the caller checks mMtpRejigging.
void NanoMenu::mtpRejigAsync(const char* reason) {
    if (mMtpRejigging.exchange(true)) return;
    const int gen = ++mMtpGen;
    { std::lock_guard<std::mutex> lk(mMtpMutex); mMtpStatus = trDyn("Starting MTP..."); mMtpFailed = false; }
    mDisplayDirty = true;
    ALOGI("mtp: rejig (%s)", reason ? reason : "");
    std::thread([this, gen] {
        bool ok = false;
        for (int attempt = 0; attempt < 3 && !ok && mMtpActive && gen == mMtpGen; attempt++) {
            // Drop the transfer function first so the switch back to mtp is a real state change
            // (a switch that only adds mtp on top of the same state does not start MtpService).
            (void)system("gammaos-net usb none 2>/dev/null");
            usleep(1500 * 1000);
            (void)system("gammaos-net usb mtp 2>/dev/null");
            // The gadget re-enumerates and MediaProvider's receiver launches MtpService; give it
            // up to ~10 s, polling for both the linked function and a live host process.
            for (int i = 0; i < 40 && mMtpActive && gen == mMtpGen; i++) {
                usleep(250 * 1000);
                const int pid = mtpMediaModulePid();
                if (pid > 0 && mtpFunctionLinked()) {
                    mtpSetMediaModuleAdj(pid, -1000);
                    mMtpMediaPid = pid; ok = true; break;
                }
            }
            if (!ok) ALOGW("mtp: rejig attempt %d did not bring MTP up", attempt + 1);
        }
        {
            std::lock_guard<std::mutex> lk(mMtpMutex);
            if (ok) { mMtpStatus = trDyn("MTP is active"); mMtpFailed = false; }
            else    { mMtpStatus = trDyn("MTP could not be started"); mMtpFailed = true; }
        }
        mMtpLastRejigMs = mtpNowMs();
        mMtpRejigging = false;
        mDisplayDirty = true;
    }).detach();
}

void NanoMenu::openMtpScreen() {
    if (mMtpActive) return;
    mMtpActive = true;
    mMtpUsbConnected = mtpUsbOnline();
    mMtpPollFrames = 0;
    mMtpMediaPid = 0;
    { std::lock_guard<std::mutex> lk(mMtpMutex); mMtpStatus.clear(); mMtpFailed = false; }
    cancelPendingLaunch();
    mDisplayDirty = true;
    mtpRejigAsync("screen opened");
}

// Back / B: the only way out. Stops the transfer (charge-only), lets the host be reaped again.
void NanoMenu::closeMtpScreen() {
    if (!mMtpActive) return;
    mMtpActive = false;
    mMtpGen++;                       // a worker still polling stops caring
    const int pid = mMtpMediaPid; mMtpMediaPid = 0; mMtpMissCount = 0;
    std::thread([pid] {
        (void)system("gammaos-net usb none 2>/dev/null");
        // Hand the host back to the framework's own priority, but only the process this screen
        // pinned and only if it is still the one alive at -1000 (a restarted host is already the
        // framework's to manage).
        if (pid > 0) {
            char p[64]; snprintf(p, sizeof p, "/proc/%d/oom_score_adj", pid);
            std::string cur; if (readSmallFile(p, cur) && cur == "-1000") mtpSetMediaModuleAdj(pid, -700);
        }
    }).detach();
    showXmbMessage(trDyn("USB: Charge Only"), trDyn("File transfer is switched off"), 240);
    mDisplayDirty = true;
}

// Called from the main loop every iteration while the screen is up (NOT from the render pass:
// the home idles at 10 fps and the DSi theme can skip render() entirely when idle, so this is
// paced on the wall clock): cable edge detection at 4 Hz and the service watchdog every 3 s.
void NanoMenu::mtpTick() {
    if (!mMtpActive) return;
    const int64_t now = mtpNowMs();
    if (now - mMtpLastPollMs < 250) return;
    mMtpLastPollMs = now;
    mMtpPollFrames++;   // 4 Hz counter, drives the spinner dots
    const bool on = mtpUsbOnline();
    if (on != mMtpUsbConnected) {
        mMtpUsbConnected = on;
        mDisplayDirty = true;
        if (on) mtpRejigAsync("cable connected");
        else { std::lock_guard<std::mutex> lk(mMtpMutex); mMtpStatus.clear(); }
        return;
    }
    // Watchdog: connected, idle, and the host process or the function has gone (reaped under
    // memory pressure, or the gadget reset) -> force it back. Rate limited so a host that keeps
    // failing does not thrash the gadget.
    if (on && !mMtpRejigging && now - mMtpLastWatchMs >= 3000 && now - mMtpLastRejigMs > 6000) {
        mMtpLastWatchMs = now;
        const int pid = mtpMediaModulePid();
        if (pid <= 0) {
            // Android restarts the persistent host on its own within about a second; only force
            // the gadget when it is still missing on the next check, so a reap the system is
            // already fixing does not also re-enumerate USB.
            if (++mMtpMissCount >= 2) { mMtpMissCount = 0; mtpRejigAsync("host process gone"); }
        } else if (!mtpFunctionLinked()) { mMtpMissCount = 0; mtpRejigAsync("function unlinked"); }
        else if (pid != mMtpMediaPid) {
            mMtpMissCount = 0;
            // The system restarted the host on its own (persistent process): pin the new one.
            mtpSetMediaModuleAdj(pid, -1000); mMtpMediaPid = pid;
            ALOGI("mtp: host restarted as pid %d, pinned", pid);
        } else mMtpMissCount = 0;
    }
}

void NanoMenu::renderMtpScreen() {
    if (!mMtpActive) return;
    mDisplayDirty = true;   // keep redrawing: the status and the spinner change without input
    const float W = (float)mWidth, H = (float)mHeight;
    float sf = fminf(W / 1080.0f, H / 720.0f); if (sf < 0.5f) sf = 0.5f;

    // Same theme-matched chrome as the scraper modal: XMB dark panel + blue accent, DSi light box
    // + favColour blue, Minima flat card + the Colour accent.
    float panR, panG, panB, panA, accR, accG, accB, t1R, t1G, t1B, t2R, t2G, t2B, dimA, rad;
    if (mNdsTheme) {
        panR = 0.97f; panG = 0.97f; panB = 0.98f; panA = 1.0f;
        accR = 0.16f; accG = 0.42f; accB = 0.85f;
        t1R = 0.20f; t1G = 0.20f; t1B = 0.22f; t2R = 0.40f; t2G = 0.42f; t2B = 0.48f;
        dimA = 0.42f; rad = 8.0f * sf;
    } else if (mMinimaTheme) {
        minimaAccent(accR, accG, accB);
        panR = 0.06f; panG = 0.07f; panB = 0.09f; panA = 0.97f;
        t1R = 1.0f; t1G = 1.0f; t1B = 1.0f; t2R = 0.72f; t2G = 0.75f; t2B = 0.80f;
        dimA = 0.55f; rad = 10.0f * sf;
    } else {
        panR = 0.07f; panG = 0.08f; panB = 0.10f; panA = 0.94f;
        accR = 0.47f; accG = 0.78f; accB = 1.0f;
        t1R = 1.0f; t1G = 1.0f; t1B = 1.0f; t2R = 0.75f; t2G = 0.78f; t2B = 0.82f;
        dimA = 0.55f; rad = 0.0f;
    }
    // Disconnected: warn in amber instead of the theme accent.
    if (!mMtpUsbConnected) { accR = 0.98f; accG = 0.72f; accB = 0.20f; }

    drawQuad(0, 0, W, H, 0.0f, 0.0f, 0.0f, dimA);
    const float pw = W * 0.62f, ph = H * 0.34f;
    const float px = (W - pw) * 0.5f, py = (H - ph) * 0.5f;
    if (rad > 0.0f) {
        drawRoundedRect(px, py + 3.0f * sf, pw, ph, rad, 0.0f, 0.0f, 0.0f, 0.35f);
        drawRoundedRect(px, py, pw, ph, rad, panR, panG, panB, panA);
        drawRoundedRect(px, py, pw, 3.0f * sf, rad, accR, accG, accB, 0.95f);
    } else {
        drawQuad(px, py, pw, ph, panR, panG, panB, panA);
        drawQuad(px, py, pw, 3.0f * sf, accR, accG, accB, 0.9f);
    }
    const float cx = W * 0.5f;
    auto centered = [&](const char* s, float y, float scale, float r, float g, float b, float a) {
        float w = measureText(s, scale);
        drawText(s, cx - w * 0.5f, y, scale, r, g, b, a);
    };

    std::string status; bool failed;
    { std::lock_guard<std::mutex> lk(mMtpMutex); status = mMtpStatus; failed = mMtpFailed; }
    const bool busy = mMtpRejigging.load();

    if (mMtpUsbConnected) {
        centered(trDyn("MTP Active"), py + ph * 0.18f, 1.7f * sf, accR, accG, accB, 1.0f);
        std::string line = status.empty() ? std::string(trDyn("Starting MTP...")) : status;
        if (busy) {   // animate the trailing dots (the status text carries its own "...": replace them)
            while (!line.empty() && line.back() == '.') line.pop_back();
            line += std::string(1 + (mMtpPollFrames / 2) % 3, '.');
        }
        centered(line.c_str(), py + ph * 0.46f, 1.2f * sf, t1R, t1G, t1B, 1.0f);
        centered(failed ? trDyn("Reconnect the cable to try again")
                        : trDyn("Transfer files from your computer"),
                 py + ph * 0.62f, 0.95f * sf, t2R, t2G, t2B, 1.0f);
    } else {
        centered(trDyn("USB Disconnected"), py + ph * 0.18f, 1.7f * sf, accR, accG, accB, 1.0f);
        centered(trDyn("Connect a USB cable to a computer"), py + ph * 0.46f, 1.2f * sf, t1R, t1G, t1B, 1.0f);
        centered(trDyn("MTP starts again as soon as it is plugged in"), py + ph * 0.62f, 0.95f * sf, t2R, t2G, t2B, 1.0f);
    }
    centered(themeButtonText(trDyn("Press Circle to stop MTP")).c_str(), py + ph * 0.86f, 0.9f * sf, t2R, t2G, t2B, 0.9f);
}

} // namespace android
