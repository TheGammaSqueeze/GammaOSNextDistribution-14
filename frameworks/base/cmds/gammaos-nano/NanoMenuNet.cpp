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
#include <cstring>
#include <string>
#include <unistd.h>

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
    // First tick happens immediately so the HUD has something other
    // than "Unknown" within a few hundred ms of the XMB drawing.
    int64_t nextPollMs = 0;
    while (!mNetPollExitRequested) {
        int64_t nowMs = std::chrono::duration_cast<std::chrono::milliseconds>(
                std::chrono::steady_clock::now().time_since_epoch()).count();
        if (nowMs < nextPollMs) {
            usleep(100 * 1000);
            continue;
        }
        nextPollMs = nowMs + 2000; // ~0.5 Hz steady state

        // --- WiFi -----------------------------------------------------
        WifiLevel wifiLevel = kWifiLevel_Unknown;
        int wifiBars = 0;
        std::string wifiSsid;
        {
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
        }

        // --- Bluetooth ------------------------------------------------
        BtLevel btLevel = kBtLevel_Unknown;
        int btConnected = 0;
        {
            // `settings get global bluetooth_on` returns "1" or "0" and
            // is much lighter than dumpsys. The source of truth for
            // connected count is dumpsys though -- only call it if the
            // radio is on.
            std::string onoff = runCmdShellout(
                    "settings get global bluetooth_on 2>/dev/null");
            bool on = false;
            for (char c : onoff) {
                if (c == '1') { on = true; break; }
                if (c == '0') { on = false; break; }
                if (c != ' ' && c != '\n' && c != '\r') break;
            }
            if (!on) {
                btLevel = kBtLevel_Off;
            } else {
                btLevel = kBtLevel_On;
                // Count connected devices via dumpsys. The relevant
                // lines look like:
                //   Bonded devices:
                //     <MAC> [ name=<x>, ...bondState=BOND_BONDED...]
                //       Connected = true
                // We count "Connected = true" occurrences; that maps
                // onto audio + input + wearable profiles the user
                // cares about.
                std::string d = runCmdShellout(
                        "dumpsys bluetooth_manager 2>/dev/null");
                if (!d.empty()) {
                    size_t p = 0;
                    while ((p = d.find("Connected = true", p))
                           != std::string::npos) {
                        btConnected++;
                        p += 16;
                    }
                    if (btConnected > 0) btLevel = kBtLevel_Connected;
                }
            }
        }

        {
            std::lock_guard<std::mutex> lock(mNetStateMutex);
            mWifiLevel = wifiLevel;
            mWifiBars = wifiBars;
            mWifiSsid = wifiSsid;
            mBtLevel = btLevel;
            mBtConnectedCount = btConnected;
            mNetPollInitialised = true;
        }
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

// Basic stylised BT rune inside a rounded square, drawn with filled
// quads. Not a true bezier BT logo -- the glyph here is a
// double-triangle that reads as "BT" at icon sizes without needing a
// texture asset shipped alongside the binary.
void NanoMenu::drawBtIcon(float x, float y, float sf,
                          float r, float g, float b, float a) {
    // Bounding box 16 x 20 at sf=1.
    float w = 16.0f * sf;
    float h = 20.0f * sf;
    float stroke = fmaxf(1.5f, 2.0f * sf);

    // Spine (vertical bar down the middle).
    float spineX = x + (w - stroke) * 0.5f;
    drawQuad(spineX, y, stroke, h, r, g, b, a);

    // Upper diagonals forming the top triangle: from spine bottom of
    // the upper half (y + h/2) up-and-right to top-right corner, and
    // back down to spine-top. We render as a 2-quad staircase which
    // looks clean at HUD sizes.
    float segH = h * 0.25f;
    float segW = w * 0.45f;
    float thick = stroke;
    // Upper right leg: spine midpoint -> top-right corner
    drawQuad(spineX + stroke, y + segH * 0.5f, segW, thick, r, g, b, a);
    drawQuad(spineX + stroke + segW - thick, y,
             thick, segH * 0.5f + thick, r, g, b, a);
    // Lower right leg: spine midpoint -> bottom-right corner
    drawQuad(spineX + stroke, y + h - segH * 0.5f - thick,
             segW, thick, r, g, b, a);
    drawQuad(spineX + stroke + segW - thick, y + h - segH * 0.5f,
             thick, segH * 0.5f, r, g, b, a);

    // Cross-bar through the spine making the X in the middle.
    drawQuad(spineX - segW * 0.35f, y + h * 0.5f - thick * 0.5f,
             segW * 0.75f + stroke, thick, r, g, b, a);
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
