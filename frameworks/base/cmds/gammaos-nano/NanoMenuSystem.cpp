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

// Sysfs helpers, brightness + volume control (including the HUD bars), and
// the Quick Resume / shutdown plumbing. Extracted from NanoMenu.cpp with no
// behavior changes.

#define LOG_TAG "GammaOSNano"

#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <dirent.h>
#include <fcntl.h>
#include <thread>
#include <unistd.h>
#include <math.h>
#include <string>

#include <aidl/android/hardware/health/BatteryStatus.h>
#include <aidl/android/hardware/health/IHealth.h>
#include <aidl/android/hardware/light/ILights.h>
#include <aidl/android/hardware/light/HwLight.h>
#include <aidl/android/hardware/light/HwLightState.h>
#include <aidl/android/hardware/light/LightType.h>
#include <android/binder_manager.h>

#include <cutils/properties.h>
#include <utils/Log.h>

#include "NanoMenu.h"
#include "NanoMenuShaders.h"

namespace android {

// ---------------------------------------------------------------------------
// Sysfs int helpers
// ---------------------------------------------------------------------------

int NanoMenu::readSysfsInt(const char* path, int fallback) {
    int fd = open(path, O_RDONLY);
    if (fd < 0) return fallback;
    char buf[32] = {};
    read(fd, buf, sizeof(buf) - 1);
    close(fd);
    return atoi(buf);
}

void NanoMenu::writeSysfsInt(const char* path, int value) {
    int fd = open(path, O_WRONLY);
    if (fd < 0) { ALOGE("Cannot write %s", path); return; }
    char buf[32];
    int len = snprintf(buf, sizeof(buf), "%d", value);
    write(fd, buf, len);
    close(fd);
}

// ---------------------------------------------------------------------------
// Brightness
// ---------------------------------------------------------------------------

// Set backlight brightness via the ILights AIDL HAL (portable across devices).
// Returns true if HAL call succeeded, false if HAL not available yet.
bool NanoMenu::setBrightnessViaHal(int brightness) {
    using aidl::android::hardware::light::ILights;
    using aidl::android::hardware::light::HwLight;
    using aidl::android::hardware::light::HwLightState;
    using aidl::android::hardware::light::LightType;

    ndk::SpAIBinder binder(
            AServiceManager_checkService("android.hardware.light.ILights/default"));
    if (!binder.get()) {
        return false;
    }
    std::shared_ptr<ILights> hal = ILights::fromBinder(binder);
    if (!hal) {
        return false;
    }

    std::vector<HwLight> lights;
    hal->getLights(&lights);
    for (const auto& light : lights) {
        if (light.type == LightType::BACKLIGHT) {
            HwLightState state{};
            // Standard Android convention: brightness in alpha channel of ARGB
            state.color = 0xFF000000 | (brightness << 16) | (brightness << 8) | brightness;
            hal->setLightState(light.id, state);
            return true;
        }
    }
    return false;
}

void NanoMenu::adjustBrightness(int direction) {
    // mBrightness is in Android 0-255 range
    int step = 26; // ~10% of 255
    mBrightness += step * direction;
    if (mBrightness < 1) mBrightness = 1;
    if (mBrightness > 255) mBrightness = 255;
    applyBrightness();
    mShowBrightnessBar = true;
    mBrightnessBarTimer = 90; // ~1.5s at 60fps
}

void NanoMenu::applyBrightness() {
    // Convert Android 0-255 to sysfs range and write
    int sysfs_val = mBrightness * mMaxBrightness / 255;
    if (sysfs_val < 1) sysfs_val = 1;
    writeSysfsInt("/sys/class/backlight/panel0-backlight/brightness", sysfs_val);
    setBrightnessViaHal(sysfs_val);
    syncBrightnessToAndroid();
}

void NanoMenu::syncBrightnessToAndroid() {
    char cmd[128];
    snprintf(cmd, sizeof(cmd), "settings put system screen_brightness %d &", mBrightness);
    system(cmd);
    // Also persist for early boot before settings provider is up
    char buf[16];
    snprintf(buf, sizeof(buf), "%d", mBrightness);
    property_set("persist.gammaos.nano.brightness", buf);
}

int NanoMenu::readAndroidBrightness() {
    FILE* fp = popen("settings get system screen_brightness 2>/dev/null", "r");
    if (!fp) return -1;
    char buf[32] = {};
    if (fgets(buf, sizeof(buf), fp)) {
        pclose(fp);
        int val = atoi(buf);
        if (val >= 1 && val <= 255) return val;
    } else {
        pclose(fp);
    }
    return -1;
}

void NanoMenu::renderBrightnessBar() {
    if (!mShowBrightnessBar) return;
    if (--mBrightnessBarTimer <= 0) {
        mShowBrightnessBar = false;
        return;
    }

    float sf = fminf((float)mWidth / 1080.0f, (float)mHeight / 720.0f);
    if (sf < 0.5f) sf = 0.5f;

    float barW = 250.0f * sf;
    float barH = 20.0f * sf;
    float pad = 12.0f * sf;
    float iconScale = 1.5f * sf;
    float textScale = 1.5f * sf;
    float iconW = measureText("*", iconScale);
    float bgW = iconW + pad + barW + pad + 50.0f * sf;
    float bgH = barH + pad * 2;
    float bgX = (mWidth - bgW) / 2.0f;
    float bgY = pad;

    // Background
    drawQuad(bgX, bgY, bgW, bgH, 0.0f, 0.0f, 0.0f, 0.8f);

    // Sun icon "*"
    float iconX = bgX + pad;
    float iconY = bgY + (bgH - FONT_CHAR_H * iconScale) / 2.0f;
    drawText("*", iconX, iconY, iconScale, 1.0f, 0.9f, 0.3f, 1.0f);

    // Progress bar background
    float barX = iconX + iconW;
    float barY = bgY + (bgH - barH) / 2.0f;
    drawQuad(barX, barY, barW, barH, 0.3f, 0.3f, 0.3f, 1.0f);

    // Progress bar fill (mBrightness is 0-255, Android range)
    int pct = mBrightness * 100 / 255;
    float fillW = barW * pct / 100.0f;
    drawQuad(barX, barY, fillW, barH, 1.0f, 0.9f, 0.3f, 1.0f);

    // Percentage text
    char pctStr[8];
    snprintf(pctStr, sizeof(pctStr), "%d%%", pct);
    float textX = barX + barW + pad;
    float textY = bgY + (bgH - FONT_CHAR_H * textScale) / 2.0f;
    drawText(pctStr, textX, textY, textScale, 1.0f, 1.0f, 1.0f, 1.0f);
}

// ---------------------------------------------------------------------------
// Volume
// ---------------------------------------------------------------------------

void NanoMenu::adjustVolume(int direction) {
    mVolume += direction;
    if (mVolume < 0) mVolume = 0;
    if (mVolume > mMaxVolume) mVolume = mMaxVolume;
    // Persist NanoMenu's own 0-mMaxVolume UI value for the bar display.
    char buf[32];
    snprintf(buf, sizeof(buf), "%d", mVolume);
    property_set("persist.gammaos.nano.volume", buf);

    // Actually tell Android's AudioService to change STREAM_MUSIC.
    // NanoMenu has the input devices grabbed via EVIOCGRAB, so physical volume
    // keys never reach PhoneWindowManager the usual way. We inject a synthetic
    // KeyEvent via `input keyevent`, which goes through InputManager →
    // InputDispatcher → PhoneWindowManager.handleVolumeKey → AudioService.
    // That path updates volume_music_speaker AND the gammaos per-display
    // volume map, so it persists across reboots.
    const char* keyCode = (direction > 0) ? "KEYCODE_VOLUME_UP" : "KEYCODE_VOLUME_DOWN";
    std::string cmd = std::string("/system/bin/input keyevent ") + keyCode + " 2>/dev/null";
    std::thread([cmd]() {
        int rc = system(cmd.c_str());
        if (rc != 0) {
            ALOGW("NanoMenu: input keyevent volume failed rc=%d", rc);
        }
    }).detach();

    mShowVolumeBar = true;
    mVolumeBarTimer = 90; // ~1.5s at 60fps
}

void NanoMenu::renderVolumeBar() {
    if (!mShowVolumeBar) return;
    if (--mVolumeBarTimer <= 0) {
        mShowVolumeBar = false;
        return;
    }

    float sf = fminf((float)mWidth / 1080.0f, (float)mHeight / 720.0f);
    if (sf < 0.5f) sf = 0.5f;

    // Same layout as brightness bar
    float barW = 250.0f * sf;
    float barH = 20.0f * sf;
    float pad = 12.0f * sf;
    float iconScale = 1.5f * sf;
    float textScale = 1.5f * sf;
    float iconW = measureText("*", iconScale); // same width reference
    float bgW = iconW + pad + barW + pad + 50.0f * sf;
    float bgH = barH + pad * 2;
    float bgX = (mWidth - bgW) / 2.0f;
    // Stack below brightness bar if both showing
    float bgY = mShowBrightnessBar ? (pad + bgH + pad) : pad;

    // Background
    drawQuad(bgX, bgY, bgW, bgH, 0.0f, 0.0f, 0.0f, 0.8f);

    // Volume icon
    float iconX = bgX + pad;
    float iconY = bgY + (bgH - FONT_CHAR_H * iconScale) / 2.0f;
    drawText(mVolume == 0 ? "x" : "+", iconX, iconY, iconScale,
             0.4f, 0.7f, 1.0f, 1.0f);

    // Progress bar background
    float barX = iconX + iconW;
    float barY = bgY + (bgH - barH) / 2.0f;
    drawQuad(barX, barY, barW, barH, 0.3f, 0.3f, 0.3f, 1.0f);

    // Progress bar fill
    int pct = (mMaxVolume > 0) ? (mVolume * 100 / mMaxVolume) : 0;
    float fillW = barW * pct / 100.0f;
    drawQuad(barX, barY, fillW, barH, 0.4f, 0.7f, 1.0f, 1.0f);

    // Percentage text
    char pctStr[8];
    snprintf(pctStr, sizeof(pctStr), "%d%%", pct);
    float textX = barX + barW + pad;
    float textY = bgY + (bgH - FONT_CHAR_H * textScale) / 2.0f;
    drawText(pctStr, textX, textY, textScale, 1.0f, 1.0f, 1.0f, 1.0f);
}

// ---------------------------------------------------------------------------
// Battery indicator
// ---------------------------------------------------------------------------

// Try the framework's IHealth AIDL HAL for battery state. This is the same
// source of truth BatteryService reads, so values stay in sync with the
// rest of Android (settings, system UI, apps). Returns true on success.
// The cached std::shared_ptr<IHealth> avoids re-resolving the binder every
// second; if the service restarts, isOk() will fail and we drop the handle.
static bool queryHealthHal(int* outPercent, bool* outCharging) {
    using aidl::android::hardware::health::BatteryStatus;
    using aidl::android::hardware::health::IHealth;

    static std::shared_ptr<IHealth> sHal;
    if (!sHal) {
        ndk::SpAIBinder binder(AServiceManager_checkService(
                "android.hardware.health.IHealth/default"));
        if (!binder.get()) return false;
        sHal = IHealth::fromBinder(binder);
        if (!sHal) return false;
    }

    int32_t cap = -1;
    auto s1 = sHal->getCapacity(&cap);
    if (!s1.isOk()) {
        sHal.reset();
        return false;
    }
    if (cap < 0) cap = 0;
    if (cap > 100) cap = 100;
    *outPercent = cap;

    BatteryStatus status = BatteryStatus::UNKNOWN;
    auto s2 = sHal->getChargeStatus(&status);
    // If status query fails we still return a valid percent; charging stays
    // whatever it was. Better than dropping the whole reading.
    if (s2.isOk()) {
        *outCharging = (status == BatteryStatus::CHARGING
                        || status == BatteryStatus::FULL);
    }
    return true;
}

// Read battery percentage and charging state. IHealth HAL is the primary
// source (matches what BatteryService exposes to the rest of the system);
// sysfs is the fallback if the HAL is not reachable (e.g. during early
// boot before android.hardware.health/default has registered). Called once
// per second from the render loop.
void NanoMenu::pollBattery() {
    if (--mBatteryPollTicks > 0) return;
    mBatteryPollTicks = 60; // ~1s at 60fps

    int pct = -1;
    bool charging = false;
    if (queryHealthHal(&pct, &charging)) {
        mBatteryPercent = pct;
        mBatteryCharging = charging;
        return;
    }

    // Fallback: read the standard Android power_supply sysfs nodes.
    mBatteryPercent = readSysfsInt(
            "/sys/class/power_supply/battery/capacity", -1);
    mBatteryCharging = false;
    int fd = open("/sys/class/power_supply/battery/status", O_RDONLY);
    if (fd >= 0) {
        char buf[32] = {};
        read(fd, buf, sizeof(buf) - 1);
        close(fd);
        if (strncmp(buf, "Charging", 8) == 0
                || strncmp(buf, "Full", 4) == 0) {
            mBatteryCharging = true;
        }
    }
}

float NanoMenu::renderBatteryIndicator() {
    if (mBatteryPercent < 0) return 15.0f; // no battery node / read failed: leftmost padding

    int pct = mBatteryPercent;
    if (pct < 0) pct = 0;
    if (pct > 100) pct = 100;

    float sf = fminf((float)mWidth / 1080.0f, (float)mHeight / 720.0f);
    if (sf < 0.5f) sf = 0.5f;

    // Layout: top-left, symmetric with the Quick Resume HUD in the top-right.
    float pad = 15.0f * sf;
    float textScale = 1.5f * sf;

    char txt[24];
    if (mBatteryCharging) {
        snprintf(txt, sizeof(txt), "+%d%%", pct);
    } else {
        snprintf(txt, sizeof(txt), "%d%%", pct);
    }

    float bodyW = 40.0f * sf;
    float bodyH = 18.0f * sf;
    float capW  = 4.0f * sf;
    float capH  = 10.0f * sf;
    float gap   = 6.0f * sf;
    float border = fmaxf(1.5f, 2.0f * sf);
    float innerPad = fmaxf(1.0f, 2.0f * sf);
    float rowH = fmaxf(bodyH, FONT_CHAR_H * textScale);

    // Color by state.
    float cr, cg, cb;
    if (mBatteryCharging) {
        cr = 0.25f; cg = 0.90f; cb = 0.35f;   // green
    } else if (pct <= 15) {
        cr = 0.95f; cg = 0.25f; cb = 0.25f;   // red
    } else if (pct <= 30) {
        cr = 0.95f; cg = 0.75f; cb = 0.15f;   // amber
    } else {
        cr = 0.90f; cg = 0.90f; cb = 0.95f;   // white
    }

    float x = pad;
    float y = pad;
    float bodyX = x;
    float bodyY = y + (rowH - bodyH) / 2.0f;
    float capX  = bodyX + bodyW;
    float capY  = bodyY + (bodyH - capH) / 2.0f;

    // Battery body outline (four rails).
    drawQuad(bodyX, bodyY, bodyW, border, cr, cg, cb, 0.95f);
    drawQuad(bodyX, bodyY + bodyH - border, bodyW, border,
             cr, cg, cb, 0.95f);
    drawQuad(bodyX, bodyY, border, bodyH, cr, cg, cb, 0.95f);
    drawQuad(bodyX + bodyW - border, bodyY, border, bodyH,
             cr, cg, cb, 0.95f);

    // Fill proportional to percentage.
    float fillMaxW = bodyW - 2 * innerPad;
    float fillW = fillMaxW * ((float)pct / 100.0f);
    if (fillW < 0.0f) fillW = 0.0f;
    drawQuad(bodyX + innerPad, bodyY + innerPad,
             fillW, bodyH - 2 * innerPad, cr, cg, cb, 1.0f);

    // Positive terminal cap.
    drawQuad(capX, capY, capW, capH, cr, cg, cb, 0.95f);

    // Text to the right of the icon, vertically centered with the body.
    float tx = capX + capW + gap;
    float ty = y + (rowH - FONT_CHAR_H * textScale) / 2.0f;
    drawText(txt, tx, ty, textScale, cr, cg, cb, 1.0f);

    // Right-edge X of the whole HUD (text is the rightmost thing). Used by
    // renderNetworkIndicators to chain WiFi + BT icons in the same row.
    return tx + measureText(txt, textScale);
}

// ---------------------------------------------------------------------------
// Quick Resume helpers
// ---------------------------------------------------------------------------

bool NanoMenu::isRetroArchRunning() {
    DIR* dir = opendir("/proc");
    if (!dir) return false;
    struct dirent* entry;
    while ((entry = readdir(dir)) != nullptr) {
        if (entry->d_type != DT_DIR) continue;
        char* end;
        long pid = strtol(entry->d_name, &end, 10);
        if (*end != '\0' || pid <= 0) continue;
        char cmdPath[64];
        snprintf(cmdPath, sizeof(cmdPath), "/proc/%ld/cmdline", pid);
        int fd = open(cmdPath, O_RDONLY);
        if (fd < 0) continue;
        char cmdline[256] = {};
        read(fd, cmdline, sizeof(cmdline) - 1);
        close(fd);
        if (strstr(cmdline, "retroarch")) {
            closedir(dir);
            return true;
        }
    }
    closedir(dir);
    return false;
}

void NanoMenu::prepareShutdown(const char* action) {
    // Always close RetroArch gracefully if running (saves state via ESC)
    if (isRetroArchRunning()) {
        ALOGI("NanoMenu: RetroArch still running, sending ESC to close gracefully");
        property_set("sys.gammaos.nano.qr_send_esc", "1");
        for (int i = 0; i < 50 && isRetroArchRunning(); i++) {
            usleep(100000); // 100ms
        }
        if (isRetroArchRunning()) {
            ALOGW("NanoMenu: RetroArch did not exit after ESC, proceeding anyway");
        }
    }

    // Quick Resume is primed at game launch time (handleSelect) and by
    // ShutdownThread when the user reboots from within RetroArch via legacy
    // global actions.  Do NOT re-prime here — the nano menu only runs after
    // the user has exited RetroArch, so priming here would cause a stale
    // game to auto-launch on the next boot.

    // Proceed with the requested action
    property_set("service.bootanim.nano_action", action);
}

} // namespace android
