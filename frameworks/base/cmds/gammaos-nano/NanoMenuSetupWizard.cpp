/*
 * Copyright (C) 2026 GammaOS
 *
 * NanoMenuSetupWizard: First-boot setup wizard for Nano mode devices.
 * Runs before the XMB menu on unprovisioned devices. Steps:
 *   1. Welcome
 *   2. Wi-Fi (reuses existing WiFi screen)
 *   3. Bluetooth (reuses existing BT screen)
 *   4. Timezone selection
 *   5. System configuration (runs setup.sh, shows progress)
 *   6. Finish (marks device provisioned)
 *
 * The XMB wallpaper renders in the background throughout. Each step
 * transition uses a slide+fade animation.
 */

#define LOG_TAG "GammaOSNano"

#include <algorithm>
#include <chrono>
#include <cmath>
#include <cstdio>
#include <cstring>
#include <mutex>
#include <string>
#include <thread>
#include <unistd.h>
#include <vector>

#include <cutils/properties.h>
#include <log/log.h>
#include <utils/SystemClock.h>

#include <GLES2/gl2.h>

#include "NanoMenu.h"
#include "NanoMenuShaders.h"
#include "NanoMenuStrings.h"

namespace android {

// ---------------------------------------------------------------------------
// Timezone table
// ---------------------------------------------------------------------------

struct TzDef {
    const char* id;
    const char* label;
    int offsetMin;
};

static const TzDef kTimezones[] = {
    {"Pacific/Midway",       "UTC-11:00  Midway",              -660},
    {"Pacific/Honolulu",     "UTC-10:00  Hawaii",              -600},
    {"America/Anchorage",    "UTC-09:00  Alaska",              -540},
    {"America/Los_Angeles",  "UTC-08:00  Pacific Time (US)",   -480},
    {"America/Denver",       "UTC-07:00  Mountain Time (US)",  -420},
    {"America/Chicago",      "UTC-06:00  Central Time (US)",   -360},
    {"America/New_York",     "UTC-05:00  Eastern Time (US)",   -300},
    {"America/Caracas",      "UTC-04:00  Venezuela",           -240},
    {"America/Halifax",      "UTC-04:00  Atlantic Time",       -240},
    {"America/St_Johns",     "UTC-03:30  Newfoundland",        -210},
    {"America/Sao_Paulo",    "UTC-03:00  Brasilia",            -180},
    {"America/Argentina/Buenos_Aires", "UTC-03:00  Buenos Aires", -180},
    {"Atlantic/South_Georgia","UTC-02:00  Mid-Atlantic",       -120},
    {"Atlantic/Azores",      "UTC-01:00  Azores",               -60},
    {"UTC",                  "UTC+00:00  UTC / GMT",              0},
    {"Europe/London",        "UTC+00:00  London",                 0},
    {"Europe/Paris",         "UTC+01:00  Paris / Berlin",        60},
    {"Europe/Madrid",        "UTC+01:00  Madrid",                60},
    {"Europe/Rome",          "UTC+01:00  Rome",                  60},
    {"Africa/Lagos",         "UTC+01:00  Lagos",                 60},
    {"Europe/Athens",        "UTC+02:00  Athens",               120},
    {"Europe/Istanbul",      "UTC+03:00  Istanbul",             180},
    {"Europe/Moscow",        "UTC+03:00  Moscow",               180},
    {"Asia/Dubai",           "UTC+04:00  Dubai",                240},
    {"Asia/Kolkata",         "UTC+05:30  India",                330},
    {"Asia/Kathmandu",       "UTC+05:45  Nepal",                345},
    {"Asia/Dhaka",           "UTC+06:00  Dhaka",                360},
    {"Asia/Bangkok",         "UTC+07:00  Bangkok",              420},
    {"Asia/Ho_Chi_Minh",     "UTC+07:00  Ho Chi Minh",         420},
    {"Asia/Shanghai",        "UTC+08:00  China",                480},
    {"Asia/Hong_Kong",       "UTC+08:00  Hong Kong",            480},
    {"Asia/Taipei",          "UTC+08:00  Taipei",               480},
    {"Asia/Singapore",       "UTC+08:00  Singapore",            480},
    {"Asia/Seoul",           "UTC+09:00  Seoul",                540},
    {"Asia/Tokyo",           "UTC+09:00  Tokyo",                540},
    {"Australia/Sydney",     "UTC+10:00  Sydney",               600},
    {"Pacific/Guam",         "UTC+10:00  Guam",                 600},
    {"Pacific/Noumea",       "UTC+11:00  New Caledonia",        660},
    {"Pacific/Auckland",     "UTC+12:00  Auckland",             720},
    {"Pacific/Fiji",         "UTC+12:00  Fiji",                 720},
};
static const int kNumTimezones = sizeof(kTimezones) / sizeof(kTimezones[0]);

// ---------------------------------------------------------------------------
// Setup script log file path
// ---------------------------------------------------------------------------

static const char* kSetupLogPath =
        "/data/data/org.lineageos.setupwizard/files/gammaos_setup.log";

// ---------------------------------------------------------------------------
// Provisioning check
// ---------------------------------------------------------------------------

bool NanoMenu::checkDeviceProvisioned() {
    FILE* f = popen("settings get global device_provisioned 2>/dev/null", "r");
    if (!f) return false;
    char buf[64] = {};
    if (fgets(buf, sizeof(buf), f)) {
        pclose(f);
        // "1" means provisioned, anything else means not
        return (buf[0] == '1');
    }
    pclose(f);
    return false;
}

// ---------------------------------------------------------------------------
// Lifecycle
// ---------------------------------------------------------------------------

void NanoMenu::startSetupWizard() {
    mSetupWizardActive = true;
    mSetupStep = SETUP_WELCOME;
    mSetupTransitionAlpha = 1.0f;
    mSetupSlideOffset = 0.0f;
    mSetupTransitioning = false;
    mSetupBootWaited = false;
    mMenuState = MENU_SETUP_WIZARD;
    mLangSelected = 0;
    mLangScrollTop = 0;
    mGreetingIndex = rand() % 14;
    nanoInitLocaleFromSystem();
    mLangSelected = (int)nanoGetLocale();
    buildTimezoneList();
    ALOGI("NanoMenu: setup wizard started");
}

void NanoMenu::finishSetupWizard() {
    // Mark provisioned via settings DB. Order matters: DEVICE_PROVISIONED
    // first (triggers AMS ContentObserver that sets ro.sys.device_provisioned),
    // then USER_SETUP_COMPLETE (unblocks permission grants and storage).
    // Run synchronously so the framework processes each change before the
    // next one lands.
    system("settings put global device_provisioned 1 2>/dev/null");
    system("settings put secure user_setup_complete 1 2>/dev/null");
    system("settings put secure tv_user_setup_complete 1 2>/dev/null");

    // Disable lockscreen (no swipe to unlock)
    system("locksettings clear --old \"\" 2>/dev/null");

    // Fast-path property for next boot
    property_set("persist.gammaos.nano.setup_done", "1");

    // Stop log thread if still running
    stopSetupLogThread();

    mSetupWizardActive = false;
    mMenuState = MENU_MAIN;
    mXmbMode = true;
    property_set("persist.gammaos.nano.xmb_mode", "1");
    mDisplayDirty = true;

    ALOGI("NanoMenu: setup wizard finished, device provisioned");
}

// ---------------------------------------------------------------------------
// Timezone list
// ---------------------------------------------------------------------------

void NanoMenu::buildTimezoneList() {
    mTzEntries.clear();
    mTzEntries.reserve(kNumTimezones);

    // Read current timezone to pre-select it
    char curTz[PROPERTY_VALUE_MAX] = {};
    property_get("persist.sys.timezone", curTz, "UTC");

    for (int i = 0; i < kNumTimezones; i++) {
        TimezoneEntry e;
        e.id = kTimezones[i].id;
        e.display = kTimezones[i].label;
        e.offsetMinutes = kTimezones[i].offsetMin;
        mTzEntries.push_back(e);
        if (e.id == curTz) {
            mTzSelected = i;
        }
    }
}

// ---------------------------------------------------------------------------
// Setup script
// ---------------------------------------------------------------------------

void NanoMenu::startSetupScript() {
    if (mSetupScriptRunning) return;

    // The init trigger requires sys.boot_completed=1. Wait for it in the
    // log tail thread if it hasn't fired yet. The UI shows "Waiting for
    // system boot..." until the script actually starts.
    char bootDone[PROPERTY_VALUE_MAX] = {};
    property_get("sys.boot_completed", bootDone, "0");
    bool booted = (strcmp(bootDone, "1") == 0);

    // Reset state
    {
        std::lock_guard<std::mutex> lk(mSetupLogMutex);
        mSetupLogLines.clear();
        if (booted) {
            mSetupLogLines.push_back(tr(STR_SETUP_INSTALL_STARTING));
        } else {
            mSetupLogLines.push_back(tr(STR_SETUP_INSTALL_BOOT_WAIT));
        }
    }
    mSetupLogScrollTop = 0;
    mSetupScriptDone = false;
    mSetupScriptRunning = true;
    mSetupLogExitRequested = false;

    // Clear previous run state and trigger. If boot hasn't completed,
    // init will queue the service start until both properties match.
    property_set("persist.gammaos.setupwizard_done", "0");
    property_set("persist.gammaos.setupwizard_exit_code", "0");
    property_set("persist.gammaos.setupwizard_run", "0");
    property_set("persist.gammaos.setupwizard_run", "1");

    ALOGI("NanoMenu: setup script triggered (boot_completed=%s)", bootDone);

    // Start log tail thread
    mSetupLogThread = std::thread(&NanoMenu::setupLogTailThreadFunc, this);
}

void NanoMenu::stopSetupLogThread() {
    mSetupLogExitRequested = true;
    if (mSetupLogThread.joinable()) {
        mSetupLogThread.join();
    }
    mSetupScriptRunning = false;
}

void NanoMenu::setupLogTailThreadFunc() {
    long pos = 0;

    while (!mSetupLogExitRequested) {
        // Check if script is done
        char done[PROPERTY_VALUE_MAX] = {};
        property_get("persist.gammaos.setupwizard_done", done, "0");
        if (strcmp(done, "1") == 0) {
            // Read any remaining log lines
            FILE* f = fopen(kSetupLogPath, "r");
            if (f) {
                fseek(f, pos, SEEK_SET);
                char line[1024];
                while (fgets(line, sizeof(line), f)) {
                    size_t len = strlen(line);
                    if (len > 0 && line[len - 1] == '\n') line[len - 1] = '\0';
                    if (strlen(line) > 0) {
                        std::lock_guard<std::mutex> lk(mSetupLogMutex);
                        mSetupLogLines.push_back(line);
                    }
                }
                fclose(f);
            }
            mSetupScriptDone = true;
            mSetupScriptRunning = false;
            ALOGI("NanoMenu: setup script finished");
            return;
        }

        // Read new lines from log file
        FILE* f = fopen(kSetupLogPath, "r");
        if (f) {
            fseek(f, 0, SEEK_END);
            long len = ftell(f);
            if (len < pos) pos = 0;

            if (len > pos) {
                fseek(f, pos, SEEK_SET);
                char line[1024];
                while (fgets(line, sizeof(line), f)) {
                    size_t slen = strlen(line);
                    if (slen > 0 && line[slen - 1] == '\n') line[slen - 1] = '\0';
                    if (strlen(line) > 0) {
                        std::lock_guard<std::mutex> lk(mSetupLogMutex);
                        mSetupLogLines.push_back(line);
                    }
                }
                pos = ftell(f);
            }
            fclose(f);
        }

        usleep(250 * 1000); // 250ms
    }
}

// ---------------------------------------------------------------------------
// Transitions
// ---------------------------------------------------------------------------

void NanoMenu::advanceSetupStep() {
    if (mSetupTransitioning) return;
    int next = (int)mSetupStep + 1;
    if (next >= SETUP_STEP_COUNT) return;

    mSetupTransitioning = true;
    mSetupTransitionTarget = (SetupWizardStep)next;
    mSetupTransitionAlpha = 1.0f;
    mSetupSlideOffset = 0.0f;
}

void NanoMenu::goBackSetupStep() {
    if (mSetupTransitioning) return;
    if (mSetupStep == SETUP_WELCOME) return;
    if (mSetupStep == SETUP_INSTALLING) return; // can't go back during install
    if (mSetupStep == SETUP_FINISH) return;

    int prev = (int)mSetupStep - 1;
    mSetupTransitioning = true;
    mSetupTransitionTarget = (SetupWizardStep)prev;
    mSetupTransitionAlpha = 1.0f;
    mSetupSlideOffset = 0.0f;
}

void NanoMenu::updateSetupTransition() {
    if (!mSetupTransitioning) return;

    bool forward = (int)mSetupTransitionTarget > (int)mSetupStep;
    float speed = 0.08f;

    mSetupTransitionAlpha -= speed;
    float slideDir = forward ? -1.0f : 1.0f;
    mSetupSlideOffset += slideDir * speed * (float)mWidth * 0.5f;

    if (mSetupTransitionAlpha <= 0.0f) {
        // Switch step at midpoint, then fade in
        mSetupStep = mSetupTransitionTarget;

        // Trigger step-specific actions
        if (mSetupStep == SETUP_WIFI) {
            openWifiScreen();
            mMenuState = MENU_WIFI;
        } else if (mSetupStep == SETUP_BLUETOOTH) {
            openBtScreen();
            mMenuState = MENU_BT;
        } else if (mSetupStep == SETUP_INSTALLING) {
            mMenuState = MENU_SETUP_WIZARD;
            startSetupScript();
        } else {
            mMenuState = MENU_SETUP_WIZARD;
        }

        // Start the fade-in phase
        mSetupTransitionAlpha = 0.0f;
        mSetupSlideOffset = -slideDir * (float)mWidth * 0.3f;
        mSetupTransitioning = false;
        // The fade-in is handled by lerping alpha toward 1.0
    }
}

// ---------------------------------------------------------------------------
// Input handling
// ---------------------------------------------------------------------------

void NanoMenu::handleSetupSelect() {
    if (mSetupTransitioning) return;

    switch (mSetupStep) {
    case SETUP_LANGUAGE:
        handleSetupLanguageSelect();
        advanceSetupStep();
        break;
    case SETUP_WELCOME:
        advanceSetupStep();
        break;
    case SETUP_WIFI:
        // A in WiFi mode is handled by the WiFi screen
        handleWifiScreenSelect();
        break;
    case SETUP_BLUETOOTH:
        handleBtScreenSelect();
        break;
    case SETUP_TIMEZONE:
        // A selects the highlighted timezone and advances
        if (!mTzEntries.empty()) {
            char cmd[256];
            snprintf(cmd, sizeof(cmd), "setprop persist.sys.timezone %s",
                     mTzEntries[mTzSelected].id.c_str());
            system(cmd);
            ALOGI("NanoMenu: timezone set to %s", mTzEntries[mTzSelected].id.c_str());
        }
        advanceSetupStep();
        break;
    case SETUP_INSTALLING:
        // Can't skip, wait for completion
        break;
    case SETUP_FINISH:
        finishSetupWizard();
        break;
    default:
        break;
    }
}

void NanoMenu::handleSetupBack() {
    if (mSetupTransitioning) return;

    switch (mSetupStep) {
    case SETUP_WELCOME:
        break;
    case SETUP_LANGUAGE:
        goBackSetupStep();
        break;
    case SETUP_WIFI:
        if (mMenuState == MENU_WIFI) {
            closeWifiScreen();
            mMenuState = MENU_SETUP_WIZARD;
        }
        goBackSetupStep();
        break;
    case SETUP_BLUETOOTH:
        if (mMenuState == MENU_BT) {
            closeBtScreen();
            mMenuState = MENU_SETUP_WIZARD;
        }
        goBackSetupStep();
        break;
    case SETUP_TIMEZONE:
        goBackSetupStep();
        break;
    case SETUP_INSTALLING:
        // Can't go back during install
        break;
    case SETUP_FINISH:
        // No going back from finish
        break;
    default:
        break;
    }
}

void NanoMenu::handleSetupUp() {
    switch (mSetupStep) {
    case SETUP_LANGUAGE:
        if (mLangSelected > 0) mLangSelected--;
        break;
    case SETUP_WIFI:
        handleWifiScreenUp();
        break;
    case SETUP_BLUETOOTH:
        handleBtScreenUp();
        break;
    case SETUP_TIMEZONE:
        if (mTzSelected > 0) mTzSelected--;
        break;
    case SETUP_INSTALLING: {
        std::lock_guard<std::mutex> lk(mSetupLogMutex);
        if (mSetupLogScrollTop > 0) mSetupLogScrollTop--;
        break;
    }
    default:
        break;
    }
}

void NanoMenu::handleSetupDown() {
    switch (mSetupStep) {
    case SETUP_LANGUAGE:
        if (mLangSelected < LOCALE_COUNT - 1) mLangSelected++;
        break;
    case SETUP_WIFI:
        handleWifiScreenDown();
        break;
    case SETUP_BLUETOOTH:
        handleBtScreenDown();
        break;
    case SETUP_TIMEZONE:
        if (mTzSelected < (int)mTzEntries.size() - 1) mTzSelected++;
        break;
    case SETUP_INSTALLING: {
        std::lock_guard<std::mutex> lk(mSetupLogMutex);
        int maxScroll = (int)mSetupLogLines.size() - 1;
        if (maxScroll < 0) maxScroll = 0;
        if (mSetupLogScrollTop < maxScroll) mSetupLogScrollTop++;
        break;
    }
    default:
        break;
    }
}

void NanoMenu::handleSetupStart() {
    if (mSetupTransitioning) return;

    // Start button advances to next step (skip current)
    switch (mSetupStep) {
    case SETUP_LANGUAGE:
        handleSetupLanguageSelect();
        advanceSetupStep();
        break;
    case SETUP_WELCOME:
        advanceSetupStep();
        break;
    case SETUP_WIFI:
        if (mMenuState == MENU_WIFI) {
            closeWifiScreen();
            mMenuState = MENU_SETUP_WIZARD;
        }
        advanceSetupStep();
        break;
    case SETUP_BLUETOOTH:
        if (mMenuState == MENU_BT) {
            closeBtScreen();
            mMenuState = MENU_SETUP_WIZARD;
        }
        advanceSetupStep();
        break;
    case SETUP_TIMEZONE:
        // Apply selected timezone before advancing
        if (!mTzEntries.empty()) {
            char cmd[256];
            snprintf(cmd, sizeof(cmd), "setprop persist.sys.timezone %s",
                     mTzEntries[mTzSelected].id.c_str());
            system(cmd);
        }
        advanceSetupStep();
        break;
    case SETUP_INSTALLING:
        if (mSetupScriptDone) advanceSetupStep();
        break;
    case SETUP_FINISH:
        finishSetupWizard();
        break;
    default:
        break;
    }
}

// ---------------------------------------------------------------------------
// Rendering
// ---------------------------------------------------------------------------

void NanoMenu::renderSetupProgressDots() {
    float sf = fminf((float)mWidth / 1080.0f, (float)mHeight / 720.0f);
    if (sf < 0.5f) sf = 0.5f;

    float dotSize = 8.0f * sf;
    float dotSpacing = 24.0f * sf;
    float totalW = SETUP_STEP_COUNT * dotSpacing;
    float startX = ((float)mWidth - totalW) / 2.0f;
    float y = (float)mHeight - 40.0f * sf;

    for (int i = 0; i < SETUP_STEP_COUNT; i++) {
        float x = startX + i * dotSpacing;
        bool active = (i == (int)mSetupStep);
        float r = active ? 0.3f : 0.4f;
        float g = active ? 0.8f : 0.4f;
        float b = active ? 1.0f : 0.5f;
        float a = active ? 1.0f : 0.5f;
        float sz = active ? dotSize * 1.4f : dotSize;
        float offset = (sz - dotSize) / 2.0f;
        drawQuad(x - offset, y - offset, sz, sz, r, g, b, a);
    }
}

void NanoMenu::renderSetupWizard() {
    updateSetupTransition();

    // Fade-in when not transitioning (lerp alpha toward 1.0)
    if (!mSetupTransitioning && mSetupTransitionAlpha < 1.0f) {
        mSetupTransitionAlpha += 0.06f;
        if (mSetupTransitionAlpha > 1.0f) mSetupTransitionAlpha = 1.0f;
        mSetupSlideOffset *= 0.85f; // ease slide to zero
    }

    // Light dim over wallpaper (skip on welcome for clean iOS-style look)
    if (mSetupStep != SETUP_WELCOME) {
        drawQuad(0, 0, mWidth, mHeight, 0.0f, 0.0f, 0.0f, 0.4f);
    }

    // Apply slide offset via a viewport-like translate. Since we don't
    // have a proper transform matrix pipeline, we pass the offset to
    // each render function and they add it to their x coordinates.
    // For simplicity, each step renderer accepts the current alpha and
    // slide offset implicitly through member vars.
    float savedAlpha = mSetupTransitionAlpha;
    float savedSlide = mSetupSlideOffset;

    switch (mSetupStep) {
    case SETUP_LANGUAGE:   renderSetupLanguage();      break;
    case SETUP_WELCOME:    renderSetupWelcome();       break;
    case SETUP_WIFI:       renderSetupWifiStep();      break;
    case SETUP_BLUETOOTH:  renderSetupBluetoothStep(); break;
    case SETUP_TIMEZONE:   renderSetupTimezone();      break;
    case SETUP_INSTALLING: renderSetupInstalling();    break;
    case SETUP_FINISH:     renderSetupFinish();        break;
    default: break;
    }

    if (mSetupStep != SETUP_WELCOME) {
        renderSetupProgressDots();
    }
}

void NanoMenu::renderSetupWelcome() {
    // iOS-style greeting animation: large centered greeting word that
    // cycles through languages with a smooth cross-fade + vertical slide.
    // Hold ~3s, fade out ~0.5s, fade in next ~0.5s.
    static const struct { const char* greeting; const char* lang; } kGreetings[] = {
        {"Hello",
         "English"},
        {"Hola",
         "Espa\xC3\xB1ol"},
        {"Bonjour",
         "Fran\xC3\xA7""ais"},
        {"Hallo",
         "Deutsch"},
        {"Ciao",
         "Italiano"},
        {"Ol\xC3\xA1",
         "Portugu\xC3\xAAs"},
        {"Hallo",
         "Nederlands"},
        {"\xD0\x9F\xD1\x80\xD0\xB8\xD0\xB2\xD0\xB5\xD1\x82",
         "\xD0\xA0\xD1\x83\xD1\x81\xD1\x81\xD0\xBA\xD0\xB8\xD0\xB9"},
        {"\xE3\x81\x93\xE3\x82\x93\xE3\x81\xAB\xE3\x81\xA1\xE3\x81\xAF",
         "\xE6\x97\xA5\xE6\x9C\xAC\xE8\xAA\x9E"},
        {"\xEC\x95\x88\xEB\x85\x95\xED\x95\x98\xEC\x84\xB8\xEC\x9A\x94",
         "\xED\x95\x9C\xEA\xB5\xAD\xEC\x96\xB4"},
        {"\xE4\xBD\xA0\xE5\xA5\xBD",
         "\xE4\xB8\xAD\xE6\x96\x87"},
        {"\xD9\x85\xD8\xB1\xD8\xAD\xD8\xA8\xD8\xA7",
         "\xD8\xA7\xD9\x84\xD8\xB9\xD8\xB1\xD8\xA8\xD9\x8A\xD8\xA9"},
        {"Merhaba",
         "T\xC3\xBCrk\xC3\xA7""e"},
        {"Cze\xC5\x9B\xC4\x87",
         "Polski"},
    };
    static const int kNumGreetings = sizeof(kGreetings) / sizeof(kGreetings[0]);

    float sf = fminf((float)mWidth / 1080.0f, (float)mHeight / 720.0f);
    if (sf < 0.5f) sf = 0.5f;
    float alpha = mSetupTransitionAlpha;

    // Transition types:
    //   0 = slide up        1 = slide down
    //   2 = zoom in         3 = zoom out
    //   4 = slide left      5 = slide right
    static const int kNumTransTypes = 6;
    static const float kHoldTime = 3.0f;
    static const float kFadeTime = 0.6f;

    mGreetingTimer += mFrameDt;

    if (!mGreetingFadingOut) {
        if (mGreetingFade < 1.0f) {
            mGreetingFade += mFrameDt / kFadeTime;
            if (mGreetingFade > 1.0f) mGreetingFade = 1.0f;
        }
        if (mGreetingTimer >= kHoldTime + kFadeTime) {
            mGreetingFadingOut = true;
        }
    } else {
        mGreetingFade -= mFrameDt / kFadeTime;
        if (mGreetingFade <= 0.0f) {
            mGreetingFade = 0.0f;
            int next = mGreetingIndex;
            while (next == mGreetingIndex)
                next = rand() % kNumGreetings;
            mGreetingIndex = next;
            mGreetingTransType = rand() % kNumTransTypes;
            mGreetingTimer = 0.0f;
            mGreetingFadingOut = false;
        }
    }

    // Compute transition progress (0 = just appeared/about to vanish, 1 = fully visible)
    // t goes 0->1 during fade-in, stays 1 during hold, 1->0 during fade-out
    float t = mGreetingFade;
    // Ease curve: smooth step for more organic motion
    float easeT = t * t * (3.0f - 2.0f * t);

    // Apply transition-specific offsets
    float offsetX = 0.0f, offsetY = 0.0f;
    float scaleMul = 1.0f;
    float maxSlide = 60.0f * sf;
    float maxSlideX = 120.0f * sf;

    switch (mGreetingTransType) {
    case 0: // slide up
        offsetY = -(1.0f - easeT) * maxSlide;
        if (mGreetingFadingOut) offsetY = (1.0f - easeT) * maxSlide * -1.0f;
        else offsetY = (1.0f - easeT) * maxSlide;
        break;
    case 1: // slide down
        if (mGreetingFadingOut) offsetY = (1.0f - easeT) * maxSlide;
        else offsetY = -(1.0f - easeT) * maxSlide;
        break;
    case 2: // zoom in (start small, grow to full)
        scaleMul = 0.6f + 0.4f * easeT;
        break;
    case 3: // zoom out (start large, shrink to normal)
        if (mGreetingFadingOut) scaleMul = 1.0f + (1.0f - easeT) * 0.5f;
        else scaleMul = 1.5f - 0.5f * easeT;
        break;
    case 4: // slide from left
        if (mGreetingFadingOut) offsetX = -(1.0f - easeT) * maxSlideX;
        else offsetX = -(1.0f - easeT) * maxSlideX;
        break;
    case 5: // slide from right
        if (mGreetingFadingOut) offsetX = (1.0f - easeT) * maxSlideX;
        else offsetX = (1.0f - easeT) * maxSlideX;
        break;
    }

    float greetScale = 6.0f * sf * scaleMul;
    const char* greeting = kGreetings[mGreetingIndex].greeting;
    float greetW = measureText(greeting, greetScale);
    float greetX = ((float)mWidth - greetW) / 2.0f + offsetX;
    float greetY = (float)mHeight * 0.38f + offsetY;
    float gAlpha = easeT * alpha;

    // Soft glow layer
    float glowAlpha = gAlpha * 0.25f;
    drawText(greeting, greetX - 2.0f * sf, greetY - 1.5f * sf,
             greetScale, 0.4f, 0.7f, 1.0f, glowAlpha);
    drawText(greeting, greetX + 1.0f * sf, greetY + 0.5f * sf,
             greetScale, 0.3f, 0.6f, 0.9f, glowAlpha * 0.6f);
    // Sharp text
    drawText(greeting, greetX, greetY,
             greetScale, 0.85f, 0.92f, 1.0f, gAlpha);

    // Language label with matched transition
    float langScale = 1.6f * sf * fminf(scaleMul, 1.1f);
    const char* langName = kGreetings[mGreetingIndex].lang;
    float langW = measureText(langName, langScale);
    float langX = ((float)mWidth - langW) / 2.0f + offsetX * 0.6f;
    float langY = greetY + FONT_CHAR_H * greetScale + 14.0f * sf + offsetY * 0.3f;
    drawText(langName, langX, langY, langScale,
             0.55f, 0.62f, 0.75f, gAlpha * 0.65f);

    // Pulsing "Press A to begin" at the bottom
    float promptScale = 1.8f * sf;
    float pulse = 0.5f + 0.5f * sinf((float)elapsedRealtime() * 0.003f);
    const char* prompt = tr(STR_SETUP_PRESS_A);
    float promptW = measureText(prompt, promptScale);
    float promptX = ((float)mWidth - promptW) / 2.0f;
    float promptY = (float)mHeight - 80.0f * sf;
    drawText(prompt, promptX, promptY, promptScale,
             0.8f, 0.85f, 0.95f, alpha * pulse * 0.8f);
}

void NanoMenu::renderSetupWifiStep() {
    float sf = fminf((float)mWidth / 1080.0f, (float)mHeight / 720.0f);
    if (sf < 0.5f) sf = 0.5f;
    float alpha = mSetupTransitionAlpha;

    // Step header
    float headerScale = 2.8f * sf;
    float slideX = mSetupSlideOffset;
    const char* header = tr(STR_SETUP_WIFI_TITLE);
    float headerW = measureText(header, headerScale);
    float headerX = ((float)mWidth - headerW) / 2.0f + slideX;
    float headerY = 15.0f * sf;
    drawText(header, headerX, headerY, headerScale,
             0.3f, 0.85f, 1.0f, alpha);

    // Check if boot is complete (WiFi driver needs system_server)
    char bootDone[PROPERTY_VALUE_MAX] = {};
    property_get("sys.boot_completed", bootDone, "0");
    bool booted = (strcmp(bootDone, "1") == 0);

    if (mMenuState == MENU_WIFI) {
        renderWifiScreen();
    }

    if (!booted) {
        // Driver loading overlay with spinning indicator
        float loadScale = 1.6f * sf;
        const char* spinner[] = {"|", "/", "-", "\\"};
        int spinIdx = ((int)(elapsedRealtime() / 150)) % 4;
        char loadMsg[64];
        snprintf(loadMsg, sizeof(loadMsg), "%s  Loading driver...", spinner[spinIdx]);
        float loadW = measureText(loadMsg, loadScale);
        drawText(loadMsg, (float)mWidth - loadW - 12.0f * sf,
                 15.0f * sf + FONT_CHAR_H * 2.8f * sf + 6.0f * sf,
                 loadScale, 0.9f, 0.8f, 0.2f, alpha * 0.9f);
    }

    // Setup footer
    float footScale = 1.3f * sf;
    const char* footer = tr(STR_SETUP_WIFI_FOOTER);
    float footW = measureText(footer, footScale);
    drawText(footer, ((float)mWidth - footW) / 2.0f,
             (float)mHeight - 70.0f * sf, footScale,
             0.5f, 0.5f, 0.55f, alpha * 0.8f);
}

void NanoMenu::renderSetupBluetoothStep() {
    float sf = fminf((float)mWidth / 1080.0f, (float)mHeight / 720.0f);
    if (sf < 0.5f) sf = 0.5f;
    float alpha = mSetupTransitionAlpha;
    float slideX = mSetupSlideOffset;

    float headerScale = 2.8f * sf;
    const char* header = tr(STR_SETUP_BT_TITLE);
    float headerW = measureText(header, headerScale);
    float headerX = ((float)mWidth - headerW) / 2.0f + slideX;
    float headerY = 15.0f * sf;
    drawText(header, headerX, headerY, headerScale,
             0.3f, 0.85f, 1.0f, alpha);

    char btBootDone[PROPERTY_VALUE_MAX] = {};
    property_get("sys.boot_completed", btBootDone, "0");
    bool btBooted = (strcmp(btBootDone, "1") == 0);

    if (mMenuState == MENU_BT) {
        renderBtScreen();
    }

    if (!btBooted) {
        float loadScale = 1.6f * sf;
        const char* spinner[] = {"|", "/", "-", "\\"};
        int spinIdx = ((int)(elapsedRealtime() / 150)) % 4;
        char loadMsg[64];
        snprintf(loadMsg, sizeof(loadMsg), "%s  Loading driver...", spinner[spinIdx]);
        float loadW = measureText(loadMsg, loadScale);
        drawText(loadMsg, (float)mWidth - loadW - 12.0f * sf,
                 15.0f * sf + FONT_CHAR_H * 2.8f * sf + 6.0f * sf,
                 loadScale, 0.9f, 0.8f, 0.2f, alpha * 0.9f);
    }

    float footScale = 1.3f * sf;
    const char* footer = tr(STR_SETUP_BT_FOOTER);
    float footW = measureText(footer, footScale);
    drawText(footer, ((float)mWidth - footW) / 2.0f,
             (float)mHeight - 70.0f * sf, footScale,
             0.5f, 0.5f, 0.55f, alpha * 0.8f);
}

void NanoMenu::renderSetupTimezone() {
    float sf = fminf((float)mWidth / 1080.0f, (float)mHeight / 720.0f);
    if (sf < 0.5f) sf = 0.5f;
    float alpha = mSetupTransitionAlpha;
    float slideX = mSetupSlideOffset;
    float pad = 20.0f * sf;

    // Title
    float titleScale = 2.8f * sf;
    const char* title = tr(STR_SETUP_TZ_TITLE);
    float titleW = measureText(title, titleScale);
    float titleX = ((float)mWidth - titleW) / 2.0f + slideX;
    drawText(title, titleX, pad, titleScale,
             0.3f, 0.85f, 1.0f, alpha);

    // List
    float rowScale = 1.8f * sf;
    float rowH = FONT_CHAR_H * rowScale + 6.0f * sf;
    float listTop = pad + FONT_CHAR_H * titleScale + 16.0f * sf;
    float listBottom = (float)mHeight - 80.0f * sf;
    int visibleRows = (int)((listBottom - listTop) / rowH);
    if (visibleRows < 4) visibleRows = 4;

    // Scrolling
    if (mTzSelected < mTzScrollTop) mTzScrollTop = mTzSelected;
    if (mTzSelected >= mTzScrollTop + visibleRows)
        mTzScrollTop = mTzSelected - visibleRows + 1;
    if (mTzScrollTop < 0) mTzScrollTop = 0;

    int end = mTzScrollTop + visibleRows;
    if (end > (int)mTzEntries.size()) end = (int)mTzEntries.size();

    for (int i = mTzScrollTop; i < end; i++) {
        float y = listTop + (i - mTzScrollTop) * rowH;
        bool sel = (i == mTzSelected);

        if (sel) {
            drawQuad(pad - 4.0f * sf + slideX, y - 3.0f * sf,
                     (float)mWidth - pad * 2.0f + 8.0f * sf, rowH,
                     0.15f, 0.35f, 0.70f, alpha * 0.65f);
        }

        drawText(mTzEntries[i].display.c_str(),
                 pad + 8.0f * sf + slideX, y + rowH * 0.12f,
                 rowScale,
                 sel ? 1.0f : 0.85f,
                 sel ? 1.0f : 0.85f,
                 sel ? 1.0f : 0.90f,
                 alpha * (sel ? 1.0f : 0.85f));
    }

    // Scroll indicators
    if (mTzScrollTop > 0) {
        float arrowScale = 1.5f * sf;
        drawText("^", (float)mWidth / 2.0f + slideX, listTop - 14.0f * sf,
                 arrowScale, 0.6f, 0.6f, 0.8f, alpha * 0.6f);
    }
    if (end < (int)mTzEntries.size()) {
        float arrowScale = 1.5f * sf;
        drawText("v", (float)mWidth / 2.0f + slideX, listBottom - 4.0f * sf,
                 arrowScale, 0.6f, 0.6f, 0.8f, alpha * 0.6f);
    }

    // Footer
    float footScale = 1.3f * sf;
    const char* footer = tr(STR_SETUP_TZ_FOOTER);
    float footW = measureText(footer, footScale);
    drawText(footer, ((float)mWidth - footW) / 2.0f,
             (float)mHeight - 70.0f * sf, footScale,
             0.5f, 0.5f, 0.55f, alpha * 0.8f);
}

void NanoMenu::renderSetupInstalling() {
    float sf = fminf((float)mWidth / 1080.0f, (float)mHeight / 720.0f);
    if (sf < 0.5f) sf = 0.5f;
    float alpha = mSetupTransitionAlpha;
    float slideX = mSetupSlideOffset;
    float pad = 20.0f * sf;

    // Title
    float titleScale = 2.8f * sf;
    const char* title = mSetupScriptDone ? tr(STR_SETUP_INSTALL_DONE)
                                         : tr(STR_SETUP_INSTALL_TITLE);
    float titleW = measureText(title, titleScale);
    float titleX = ((float)mWidth - titleW) / 2.0f + slideX;
    drawText(title, titleX, pad, titleScale,
             0.3f, 0.85f, 1.0f, alpha);

    // Spinning indicator when not done
    if (!mSetupScriptDone) {
        float spinScale = 1.6f * sf;
        const char* spinner[] = {"|", "/", "-", "\\"};
        int spinIdx = ((int)(elapsedRealtime() / 200)) % 4;
        float spinW = measureText(spinner[spinIdx], spinScale);
        drawText(spinner[spinIdx],
                 (float)mWidth - pad - spinW + slideX,
                 pad + 4.0f * sf, spinScale,
                 0.8f, 0.8f, 0.2f, alpha);
    }

    // Log output
    float logScale = 1.3f * sf;
    float logTop = pad + FONT_CHAR_H * titleScale + 16.0f * sf;
    float logRowH = FONT_CHAR_H * logScale + 3.0f * sf;
    float logBottom = (float)mHeight - 80.0f * sf;
    int visibleLines = (int)((logBottom - logTop) / logRowH);
    if (visibleLines < 4) visibleLines = 4;

    std::vector<std::string> lines;
    {
        std::lock_guard<std::mutex> lk(mSetupLogMutex);
        lines = mSetupLogLines;
    }

    // Auto-scroll to bottom when new lines arrive
    int totalLines = (int)lines.size();
    if (totalLines > visibleLines) {
        mSetupLogScrollTop = totalLines - visibleLines;
    }

    int start = mSetupLogScrollTop;
    if (start < 0) start = 0;
    int end = start + visibleLines;
    if (end > totalLines) end = totalLines;

    for (int i = start; i < end; i++) {
        float y = logTop + (i - start) * logRowH;

        // Color the line based on content
        float r = 0.75f, g = 0.75f, b = 0.80f;
        if (lines[i].find("Installing") != std::string::npos ||
            lines[i].find("Extracting") != std::string::npos) {
            r = 0.4f; g = 0.9f; b = 0.5f;
        } else if (lines[i].find("Error") != std::string::npos ||
                   lines[i].find("error") != std::string::npos) {
            r = 1.0f; g = 0.4f; b = 0.4f;
        } else if (lines[i].find("completed") != std::string::npos ||
                   lines[i].find("successfully") != std::string::npos) {
            r = 0.3f; g = 0.95f; b = 0.6f;
        }

        drawText(lines[i].c_str(),
                 pad + slideX, y, logScale,
                 r, g, b, alpha * 0.9f);
    }

    // Footer
    float footScale = 1.3f * sf;
    const char* footer = mSetupScriptDone
            ? tr(STR_SETUP_INSTALL_CONTINUE)
            : tr(STR_SETUP_INSTALL_WAIT);
    float footW = measureText(footer, footScale);
    drawText(footer, ((float)mWidth - footW) / 2.0f,
             (float)mHeight - 70.0f * sf, footScale,
             0.5f, 0.5f, 0.55f, alpha * 0.8f);
}

void NanoMenu::renderSetupFinish() {
    float sf = fminf((float)mWidth / 1080.0f, (float)mHeight / 720.0f);
    if (sf < 0.5f) sf = 0.5f;
    float alpha = mSetupTransitionAlpha;
    float slideX = mSetupSlideOffset;

    // Title with a green tint
    float titleScale = 4.0f * sf;
    const char* title = tr(STR_SETUP_FINISH_TITLE);
    float titleW = measureText(title, titleScale);
    float titleX = ((float)mWidth - titleW) / 2.0f + slideX;
    float titleY = (float)mHeight * 0.30f;
    drawText(title, titleX, titleY, titleScale,
             0.3f, 1.0f, 0.5f, alpha);

    // Subtitle
    float subScale = 2.0f * sf;
    const char* sub = tr(STR_SETUP_FINISH_SUB);
    float subW = measureText(sub, subScale);
    float subX = ((float)mWidth - subW) / 2.0f + slideX;
    float subY = titleY + FONT_CHAR_H * titleScale + 20.0f * sf;
    drawText(sub, subX, subY, subScale,
             0.6f, 0.7f, 0.65f, alpha * 0.9f);

    // Pulsing prompt
    float promptScale = 2.2f * sf;
    float pulse = 0.6f + 0.4f * sinf((float)elapsedRealtime() * 0.004f);
    const char* prompt = tr(STR_SETUP_FINISH_PRESS_A);
    float promptW = measureText(prompt, promptScale);
    float promptX = ((float)mWidth - promptW) / 2.0f + slideX;
    float promptY = (float)mHeight * 0.62f;
    drawText(prompt, promptX, promptY, promptScale,
             0.95f, 0.95f, 1.0f, alpha * pulse);
}

// ---------------------------------------------------------------------------
// Language selection step
// ---------------------------------------------------------------------------

void NanoMenu::handleSetupLanguageSelect() {
    nanoSetLocale((NanoLocale)mLangSelected);
    nanoApplyLocaleToSystem();
}

void NanoMenu::renderSetupLanguage() {
    float sf = fminf((float)mWidth / 1080.0f, (float)mHeight / 720.0f);
    if (sf < 0.5f) sf = 0.5f;
    float alpha = mSetupTransitionAlpha;
    float pad = 20.0f * sf;

    // Update locale preview as user scrolls
    nanoSetLocale((NanoLocale)mLangSelected);
    float titleScale = 2.8f * sf;
    const char* title = tr(STR_SETUP_LANG_TITLE);
    float titleW = measureText(title, titleScale);
    float titleX = ((float)mWidth - titleW) / 2.0f;
    drawText(title, titleX, pad, titleScale, 0.3f, 0.85f, 1.0f, alpha);

    // Language list
    float rowScale = 2.0f * sf;
    float rowH = FONT_CHAR_H * rowScale + 8.0f * sf;
    float listTop = pad + FONT_CHAR_H * titleScale + 20.0f * sf;
    float listBottom = (float)mHeight - 80.0f * sf;
    int visibleRows = (int)((listBottom - listTop) / rowH);
    if (visibleRows < 4) visibleRows = 4;

    if (mLangSelected < mLangScrollTop) mLangScrollTop = mLangSelected;
    if (mLangSelected >= mLangScrollTop + visibleRows)
        mLangScrollTop = mLangSelected - visibleRows + 1;
    if (mLangScrollTop < 0) mLangScrollTop = 0;

    int end = mLangScrollTop + visibleRows;
    if (end > LOCALE_COUNT) end = LOCALE_COUNT;

    for (int i = mLangScrollTop; i < end; i++) {
        const LocaleInfo& info = nanoGetLocaleInfo((NanoLocale)i);
        float y = listTop + (i - mLangScrollTop) * rowH;
        bool sel = (i == mLangSelected);

        if (sel) {
            drawQuad(0, y - 3.0f * sf,
                     (float)mWidth, rowH,
                     0.15f, 0.35f, 0.70f, alpha * 0.65f);
        }

        drawText(info.nativeName,
                 pad, y + rowH * 0.12f,
                 rowScale,
                 sel ? 1.0f : 0.85f,
                 sel ? 1.0f : 0.85f,
                 sel ? 1.0f : 0.90f,
                 alpha * (sel ? 1.0f : 0.85f));

        float engScale = rowScale * 0.7f;
        float engW = measureText(info.englishName, engScale);
        drawText(info.englishName,
                 (float)mWidth - engW - pad,
                 y + rowH * 0.20f,
                 engScale, 0.5f, 0.5f, 0.6f, alpha * 0.7f);
    }

    if (mLangScrollTop > 0) {
        float arrowScale = 1.5f * sf;
        drawText("^", (float)mWidth / 2.0f, listTop - 14.0f * sf,
                 arrowScale, 0.6f, 0.6f, 0.8f, alpha * 0.6f);
    }
    if (end < LOCALE_COUNT) {
        float arrowScale = 1.5f * sf;
        drawText("v", (float)mWidth / 2.0f, listBottom - 4.0f * sf,
                 arrowScale, 0.6f, 0.6f, 0.8f, alpha * 0.6f);
    }

    // Footer
    float footScale = 1.3f * sf;
    const char* footer = tr(STR_SETUP_LANG_FOOTER);
    float footW = measureText(footer, footScale);
    drawText(footer, ((float)mWidth - footW) / 2.0f,
             (float)mHeight - 70.0f * sf, footScale,
             0.5f, 0.5f, 0.55f, alpha * 0.8f);
}

} // namespace android
