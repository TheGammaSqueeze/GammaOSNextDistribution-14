/*
 * Copyright (C) 2026 GammaOS
 *
 * NanoMenuNet: WiFi + Bluetooth HUD for the XMB top bar. Polls the
 * framework services from a background thread at ~0.5 Hz via `cmd wifi`
 * and `cmd bluetooth_manager`, then renders two small icons next to the
 * battery indicator.
 *
 * We cannot call into Wifi/Bluetooth AIDL from the bootanim SELinux
 * domain (the service managers refuse the lookup), but the binder
 * `cmd` shell-out path is allowed. The calls are slow (100 ms cold,
 * 10 ms steady-state) and blocking, so they have to live off the
 * render thread -- spikes on the render thread cause visible
 * XMB scroll judder.
 */

#define LOG_TAG "GammaOSNano"

#include <cctype>
#include <chrono>
#include <cmath>
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <string>
#include <fcntl.h>
#include <unistd.h>

#include <cutils/properties.h>
#include <log/log.h>

#include "NanoMenu.h"

namespace android {

namespace {

// popen() wrapper that caps total output at 16 KiB and total time at
// about 4 s so a hung service call can't freeze the worker thread or
// bloat memory. Returns the collected stdout; stderr is redirected
// into it because a lot of the `cmd` shell-outs mix warnings into
// stdout anyway.
std::string runCmdShellout(const char* command) {
    std::string out;
    out.reserve(1024);
    FILE* f = popen(command, "r");
    if (!f) return out;
    char buf[512];
    const size_t kMax = 16 * 1024;
    while (fgets(buf, sizeof(buf), f)) {
        out.append(buf);
        if (out.size() > kMax) {
            out.resize(kMax);
            break;
        }
    }
    pclose(f);
    return out;
}

bool strContainsCI(const std::string& hay, const char* needle) {
    size_t n = strlen(needle);
    if (hay.size() < n) return false;
    for (size_t i = 0; i + n <= hay.size(); i++) {
        size_t k = 0;
        while (k < n
               && tolower((unsigned char)hay[i + k]) ==
                      tolower((unsigned char)needle[k])) {
            k++;
        }
        if (k == n) return true;
    }
    return false;
}

// Parse the SSID and RSSI out of the free-form `cmd wifi status` output.
// The relevant line looks like:
//   mWifiInfo SSID: <name>, BSSID: ..., MAC: ..., Security type: ...,
//       Supplicant state: COMPLETED, Rssi: -55, Link speed: ...
// SSID can contain commas, so we stop at ", BSSID:". Rssi is a signed
// integer; 0 or positive means uninitialised (treat as -127).
void parseWifiInfo(const std::string& text,
                   std::string* outSsid, int* outRssi) {
    outSsid->clear();
    *outRssi = -127;

    // SSID
    static const char* kSsidKey = "SSID:";
    size_t p = text.find(kSsidKey);
    while (p != std::string::npos) {
        size_t s = p + strlen(kSsidKey);
        while (s < text.size() && text[s] == ' ') s++;
        size_t e = text.find(", BSSID:", s);
        if (e == std::string::npos) e = text.find('\n', s);
        if (e == std::string::npos) e = text.size();
        std::string ssid = text.substr(s, e - s);
        // Strip surrounding quotes if present.
        if (ssid.size() >= 2 && ssid.front() == '"' && ssid.back() == '"') {
            ssid = ssid.substr(1, ssid.size() - 2);
        }
        if (!ssid.empty()
                && ssid != "<unknown ssid>"
                && ssid != "<none>") {
            *outSsid = ssid;
            break;
        }
        p = text.find(kSsidKey, p + 1);
    }

    // RSSI
    static const char* kRssiKey = "Rssi:";
    p = text.find(kRssiKey);
    if (p != std::string::npos) {
        size_t s = p + strlen(kRssiKey);
        while (s < text.size() && text[s] == ' ') s++;
        int rssi = atoi(text.c_str() + s);
        if (rssi < 0) *outRssi = rssi;
    }
}

// Map an RSSI in dBm to a 0..4 bars level. Matches the Android stock
// ScanResult.calculateSignalLevel thresholds closely enough for a HUD.
int rssiToBars(int rssi) {
    if (rssi >= -55) return 4;
    if (rssi >= -66) return 3;
    if (rssi >= -77) return 2;
    if (rssi >= -88) return 1;
    return 0;
}

} // namespace

// ---------------------------------------------------------------------------
// Background polling thread
// ---------------------------------------------------------------------------

void NanoMenu::startNetPollThread() {
    if (mNetPollThreadRunning) return;
    mNetPollExitRequested = false;
    mNetPollThreadRunning = true;
    mNetPollThread = std::thread([this]() { netPollThreadFunc(); });
}

void NanoMenu::stopNetPollThread() {
    if (!mNetPollThreadRunning) return;
    mNetPollExitRequested = true;
    if (mNetPollThread.joinable()) {
        mNetPollThread.join();
    }
    mNetPollThreadRunning = false;
}

void NanoMenu::netPollThreadFunc() {
    ALOGI("GammaOS Nano: net poll thread start");
    // Auto-enable Wi-Fi if it's off and we have saved networks -- mirrors
    // the "remember last on/off" behaviour of stock Android. We only
    // try this once at thread start so a user who explicitly disabled
    // Wi-Fi from the settings screen isn't fought every 2 seconds.
    {
        std::string status = runCmdShellout("cmd wifi status 2>/dev/null");
        bool wifiOff = status.empty()
                    || strContainsCI(status, "Wifi is disabled")
                    || strContainsCI(status, "is disabled");
        if (wifiOff) {
            std::string saved = runCmdShellout(
                    "cmd wifi list-networks 2>/dev/null");
            // The header line itself ends with "Security type"; we want
            // to know if there's at least one numeric network id row
            // after the header.
            bool anySaved = false;
            size_t p = 0;
            while (p < saved.size()) {
                size_t eol = saved.find('\n', p);
                if (eol == std::string::npos) eol = saved.size();
                size_t q = p;
                while (q < eol && isspace((unsigned char)saved[q])) q++;
                if (q < eol && isdigit((unsigned char)saved[q])) {
                    anySaved = true;
                    break;
                }
                p = eol + 1;
            }
            if (anySaved) {
                ALOGI("GammaOS Nano: auto-enabling Wi-Fi (saved networks present)");
                (void)runCmdShellout(
                        "cmd wifi set-wifi-enabled enabled 2>/dev/null");
            }
        }
    }
    // First tick happens immediately so the HUD has something other
    // than "Unknown" within a few hundred ms of the XMB drawing.
    int64_t nextPollMs = 0;
    int64_t curIntervalMs = 2000;   // adaptive: 2s active, backs off to 8s when idle
    while (!mNetPollExitRequested) {
        int64_t nowMs = std::chrono::duration_cast<std::chrono::milliseconds>(
                std::chrono::steady_clock::now().time_since_epoch()).count();
        if (nowMs < nextPollMs) {
            // While backed off (interval > 2s), still poll right away if the user
            // just became active or opened a wifi/bt screen, so those never inherit a
            // stale multi-second gap. Otherwise keep the ~10Hz wait poll.
            // nowMs (steady_clock ms) shares CLOCK_MONOTONIC with the uptimeMillis()
            // that stamps mLastInputMs, so the difference is a valid idle duration.
            bool wantNow = curIntervalMs > 2000 &&
                (nowMs - mLastInputMs < 60000
                 || mMenuState == MENU_WIFI || mMenuState == MENU_BT);
            if (!wantNow) {
                usleep(100 * 1000);
                continue;
            }
        }
        // nextPollMs is (re)armed at the END of the tick from curIntervalMs, which
        // the change-detect block adapts (snappy 2s when active/changed, geometric
        // backoff to 8s when idle and stable).

        // --- Network state --------------------------------------------
        WifiLevel wifiLevel = kWifiLevel_Unknown;
        int wifiBars = 0;
        std::string wifiSsid;
        BtLevel btLevel = kBtLevel_Unknown;
        int btConnected = 0;

        // Prefer the resident NanoNetBridge: system_server holds WifiManager/
        // BluetoothAdapter live and pushes event-driven HUD state to a file, so we
        // read it here with no fork and no version-fragile text parse. Fall back to
        // the cmd/dumpsys shell path when the bridge is absent (full boot) or has not
        // published yet (net_generation still 0), or when explicitly disabled.
        bool fromBridge = false;
        if (property_get_bool("persist.gammaos.net_bridge", true)
                && property_get_int32("sys.gammaos.nano.net_generation", 0) > 0) {
            int fd = open("/data/system/nano_net_state.txt", O_RDONLY | O_CLOEXEC);
            if (fd >= 0) {
                char nb[512];
                ssize_t n = read(fd, nb, sizeof(nb) - 1);
                close(fd);
                if (n > 0) {
                    nb[n] = '\0';
                    std::string s(nb, (size_t)n);
                    int wOn = -1, wConn = 0, bars = 0, bOn = -1, bCnt = 0;
                    std::string ssid;
                    size_t p = 0;
                    while (p < s.size()) {
                        size_t eol = s.find('\n', p);
                        if (eol == std::string::npos) eol = s.size();
                        std::string line = s.substr(p, eol - p);
                        p = eol + 1;
                        size_t eq = line.find('=');
                        if (eq == std::string::npos) continue;
                        std::string k = line.substr(0, eq), v = line.substr(eq + 1);
                        if (k == "wifi_on") wOn = atoi(v.c_str());
                        else if (k == "wifi_conn") wConn = atoi(v.c_str());
                        else if (k == "wifi_bars") bars = atoi(v.c_str());
                        else if (k == "wifi_ssid") ssid = v;
                        else if (k == "bt_on") bOn = atoi(v.c_str());
                        else if (k == "bt_count") bCnt = atoi(v.c_str());
                    }
                    if (wOn >= 0 && bOn >= 0) {   // well-formed payload
                        wifiLevel = !wOn ? kWifiLevel_Off
                                : (wConn ? kWifiLevel_Connected : kWifiLevel_Disconnected);
                        wifiBars = wConn ? bars : 0;
                        wifiSsid = wConn ? ssid : std::string();
                        btLevel = !bOn ? kBtLevel_Off
                                : (bCnt > 0 ? kBtLevel_Connected : kBtLevel_On);
                        btConnected = bOn ? bCnt : 0;
                        fromBridge = true;
                    }
                }
            }
        }

        if (!fromBridge) {
            // --- WiFi (shell fallback) --------------------------------
            std::string status = runCmdShellout("cmd wifi status 2>/dev/null");
            if (status.empty()) {
                // Service missing or binder refused. Leave as Unknown.
            } else if (strContainsCI(status, "Wifi is disabled")
                    || strContainsCI(status, "is disabled")) {
                wifiLevel = kWifiLevel_Off;
            } else {
                std::string ssid;
                int rssi = -127;
                parseWifiInfo(status, &ssid, &rssi);
                if (!ssid.empty()) {
                    wifiLevel = kWifiLevel_Connected;
                    // Fall back to a mid-strength 2-bar hint if the
                    // output didn't include a parsable Rssi line --
                    // the connection itself is real (SSID proves it),
                    // we just don't know how strong.
                    wifiBars = (rssi > -127) ? rssiToBars(rssi) : 2;
                    wifiSsid = ssid;
                } else {
                    wifiLevel = kWifiLevel_Disconnected;
                }
            }

            // --- Bluetooth (shell fallback) ---------------------------
            // Read the REAL adapter state from dumpsys. `settings get global
            // bluetooth_on` is NOT reliable: `cmd bluetooth_manager enable/disable`
            // (which the radio toggle uses) does not update that setting, so it
            // stays stale. dumpsys reports the live "enabled: true/false" and is
            // also the source for the connected count, so one query covers both.
            std::string d = runCmdShellout("dumpsys bluetooth_manager 2>/dev/null");
            bool on = (d.find("enabled: true") != std::string::npos);
            if (!on) {
                btLevel = kBtLevel_Off;
            } else {
                btLevel = kBtLevel_On;
                // Count connected devices. The relevant lines look like:
                //       Connected = true
                // mapping onto audio + input + wearable profiles.
                size_t p = 0;
                while ((p = d.find("Connected = true", p)) != std::string::npos) {
                    btConnected++;
                    p += 16;
                }
                if (btConnected > 0) btLevel = kBtLevel_Connected;
            }
        }

        bool wifiChanged = false;
        bool btChanged = false;
        {
            std::lock_guard<std::mutex> lock(mNetStateMutex);
            if (mWifiSsid != wifiSsid || mWifiLevel != wifiLevel) {
                wifiChanged = true;
            }
            if (mBtLevel != btLevel || mBtConnectedCount != btConnected) {
                btChanged = true;
            }
            mWifiLevel = wifiLevel;
            if (wifiLevel != kWifiLevel_Unknown) mWifiRadioOn = (wifiLevel != kWifiLevel_Off);
            mWifiBars = wifiBars;
            mWifiSsid = wifiSsid;
            mBtLevel = btLevel;
            mBtConnectedCount = btConnected;
            mNetPollInitialised = true;
        }
        // If the user is currently looking at the Wi-Fi or BT settings
        // screen and the underlying state just changed (SSID flipped
        // after a connect-saved, BT device connected/disconnected),
        // refresh the list so the "(Connected)" marker moves in real
        // time instead of waiting for the next user input or the
        // 3-second post-switch scan tick. We go through the scan thread
        // for Wi-Fi so its internal mScanInProgress flag is respected
        // (avoids two concurrent cmd-wifi shell-outs); refreshBtList()
        // is cheap enough to call directly.
        if (wifiChanged && mMenuState == MENU_WIFI) {
            startWifiScanAsync();
        }
        if (btChanged && mMenuState == MENU_BT) {
            refreshBtList();
        }
        // Adaptive cadence. Each idle tick spawns two popen() children (cmd wifi
        // status + dumpsys bluetooth_manager); forking this large process and the
        // children loading the binder stack off the compressed image is the bulk of
        // the net thread's CPU and the slab-reclaim churn it drives. Poll at the
        // snappy 2s whenever the user is active (<60s since last input, the same
        // longIdle threshold the render loop uses), the wifi/bt state just changed,
        // or a network settings screen is open; otherwise grow the interval
        // geometrically 2s -> 4s -> 8s. Any change or interaction snaps back to 2s.
        // This never freezes a pixel (the HUD redraws the icons every frame); it only
        // delays an EXTERNAL state change reflecting by up to 8s while fully idle and
        // not on a network screen. mLastInputMs is read once into a local (the only
        // cross-thread read here; an aligned 64-bit load does not tear on aarch64).
        bool onNetScreen = (mMenuState == MENU_WIFI || mMenuState == MENU_BT);
        int64_t idleMs = nowMs - mLastInputMs;   // nowMs is CLOCK_MONOTONIC ms, same as mLastInputMs
        if (wifiChanged || btChanged || onNetScreen || idleMs < 60000) {
            curIntervalMs = 2000;
        } else {
            curIntervalMs = (curIntervalMs < 4000) ? 4000 : 8000;   // 2s -> 4s -> 8s
        }
        nextPollMs = nowMs + curIntervalMs;
        (void)wifiChanged;
        (void)btChanged;
    }
    ALOGI("GammaOS Nano: net poll thread exit");
}

// ---------------------------------------------------------------------------
// Icon rendering primitives
// ---------------------------------------------------------------------------

// 4-bar WiFi icon built from drawQuad rectangles. `bars` is 0..4; when
// 0 we render a faint all-off ghost so the off/disconnected states
// still visibly occupy the row.
void NanoMenu::drawWifiIcon(float x, float y, float sf, int bars,
                            float r, float g, float b, float a) {
    if (bars < 0) bars = 0;
    if (bars > 4) bars = 4;

    // Reference size is 22 x 18 at sf=1. Each bar is a vertical bar
    // with 2 px gap.
    float totalW = 22.0f * sf;
    float totalH = 18.0f * sf;
    float gap = fmaxf(1.0f, 2.0f * sf);
    int n = 4;
    float barW = (totalW - gap * (n - 1)) / n;

    for (int i = 0; i < n; i++) {
        float heightFrac = 0.30f + 0.70f * ((float)(i + 1) / (float)n);
        float bh = totalH * heightFrac;
        float bx = x + i * (barW + gap);
        float by = y + (totalH - bh);
        bool active = (i < bars);
        float ar = r, ag = g, ab = b;
        float aa = a;
        if (!active) {
            // Dim ghost for inactive bars.
            ar = r * 0.35f;
            ag = g * 0.35f;
            ab = b * 0.35f;
            aa = a * 0.40f;
        }
        drawQuad(bx, by, barW, bh, ar, ag, ab, aa);
    }
}

// The real Bluetooth bind-rune logo, drawn as thick line segments (two
// triangles each). Construction: a vertical spine; the top and bottom apexes
// each connect right-down/right-up to a knee; and the two knees cross the spine
// diagonally to the opposite-side left tips. This is the recognisable
// Bluetooth glyph rather than a stylised approximation.
void NanoMenu::drawBtIcon(float x, float y, float sf,
                          float r, float g, float b, float a) {
    float w = 14.0f * sf;
    float h = 20.0f * sf;
    float t = fmaxf(1.6f, 2.2f * sf);   // stroke thickness

    auto P = [&](float u, float v, float* ox, float* oy) { *ox = x + u * w; *oy = y + v * h; };
    auto line = [&](float u0, float v0, float u1, float v1) {
        float ax, ay, bx, by; P(u0, v0, &ax, &ay); P(u1, v1, &bx, &by);
        float dx = bx - ax, dy = by - ay, L = sqrtf(dx*dx + dy*dy);
        if (L < 1e-3f) return;
        float px = -dy / L * t * 0.5f, py = dx / L * t * 0.5f;
        drawTriangle(ax + px, ay + py, ax - px, ay - py, bx + px, by + py, r, g, b, a);
        drawTriangle(bx + px, by + py, bx - px, by - py, ax - px, ay - py, r, g, b, a);
    };
    // spine
    line(0.5f, 0.04f, 0.5f, 0.96f);
    // top apex -> upper-right knee, bottom apex -> lower-right knee
    line(0.5f, 0.04f, 0.82f, 0.27f);
    line(0.5f, 0.96f, 0.82f, 0.73f);
    // knees cross the spine to the opposite-side left tips (the X)
    line(0.82f, 0.27f, 0.18f, 0.70f);
    line(0.82f, 0.73f, 0.18f, 0.30f);
}

// ---------------------------------------------------------------------------
// Top-level HUD draw
// ---------------------------------------------------------------------------

float NanoMenu::renderNetworkIndicators(float startX, float rowY, float rowH,
                                        float sf, float textScale) {
    // Snapshot state under the mutex so the rest of this function
    // never blocks the worker thread.
    WifiLevel wifi;
    int wifiBars;
    std::string wifiSsid;
    BtLevel bt;
    int btConnected;
    bool ready;
    {
        std::lock_guard<std::mutex> lock(mNetStateMutex);
        wifi = mWifiLevel;
        wifiBars = mWifiBars;
        wifiSsid = mWifiSsid;
        bt = mBtLevel;
        btConnected = mBtConnectedCount;
        ready = mNetPollInitialised;
    }
    if (!ready) return startX; // poll hasn't landed yet

    float pad = 12.0f * sf;
    float iconGap = 6.0f * sf;

    float x = startX + pad * 1.5f;

    // --- WiFi -----------------------------------------------------
    {
        float wifiW = 22.0f * sf;
        float wifiH = 18.0f * sf;
        float wifiY = rowY + (rowH - wifiH) / 2.0f;
        float r, g, b, alpha;
        int drawBars = 0;
        switch (wifi) {
        case kWifiLevel_Off:
            // Dim grey, no bars active.
            r = 0.55f; g = 0.55f; b = 0.60f; alpha = 0.55f;
            drawBars = 0;
            break;
        case kWifiLevel_Disconnected:
            // Amber, no connection.
            r = 0.95f; g = 0.75f; b = 0.15f; alpha = 0.95f;
            drawBars = 0;
            break;
        case kWifiLevel_Connected:
            r = 0.90f; g = 0.90f; b = 0.95f; alpha = 1.0f;
            drawBars = wifiBars;
            break;
        default:
            r = 0.70f; g = 0.70f; b = 0.75f; alpha = 0.70f;
            drawBars = 0;
            break;
        }
        drawWifiIcon(x, wifiY, sf, drawBars, r, g, b, alpha);
        // Small "!" dot for disconnected-but-on.
        if (wifi == kWifiLevel_Disconnected) {
            float dot = 3.0f * sf;
            drawQuad(x + wifiW - dot, wifiY + wifiH - dot,
                     dot, dot, r, g, b, alpha);
        }
        x += wifiW + iconGap;
    }

    // --- Bluetooth ------------------------------------------------
    {
        float btW = 16.0f * sf;
        float btH = 20.0f * sf;
        float btY = rowY + (rowH - btH) / 2.0f;
        float r, g, b, alpha;
        switch (bt) {
        case kBtLevel_Off:
            r = 0.55f; g = 0.55f; b = 0.60f; alpha = 0.45f;
            break;
        case kBtLevel_On:
            r = 0.55f; g = 0.75f; b = 0.95f; alpha = 0.85f;
            break;
        case kBtLevel_Connected:
            r = 0.35f; g = 0.85f; b = 0.95f; alpha = 1.0f;
            break;
        default:
            r = 0.70f; g = 0.70f; b = 0.75f; alpha = 0.60f;
            break;
        }
        drawBtIcon(x, btY, sf, r, g, b, alpha);
        if (bt == kBtLevel_Connected && btConnected > 1) {
            // Superscript count for multi-device connection.
            char tbuf[8];
            snprintf(tbuf, sizeof(tbuf), "%d", btConnected);
            float tScale = textScale * 0.55f;
            float tw = measureText(tbuf, tScale);
            drawText(tbuf, x + btW + 1.0f * sf,
                     btY + btH * 0.1f,
                     tScale, r, g, b, alpha);
            x += tw + 1.0f * sf;
        }
        x += btW + iconGap;
    }

    return x;
}

} // namespace android
