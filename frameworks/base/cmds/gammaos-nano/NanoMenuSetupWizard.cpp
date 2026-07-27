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
#include "NanoMenuPS3.h"   // ps3:: dialog layout for the PS3-styled language step
#include "NanoI18n.h"      // trDyn() resource-file translations

namespace android {

// ---------------------------------------------------------------------------
// Timezone table
// ---------------------------------------------------------------------------

struct TzDef {
    const char* id;
    const char* label;
    int offsetMin;
    float lat;
    float lon;
};

// 1:1 with the web app TZ_LIST (index.html 8841-8866): the firmware Time Zone
// list (GMT offset + city) with each city's real latitude/longitude so the 3D
// globe rotates to that location. Each entry is paired here with the matching
// Android tz id used for `setprop persist.sys.timezone`. Offset-ordered, several
// cities per offset (the globe lat/lon is what distinguishes them).
static const TzDef kTimezones[] = {
    {"Pacific/Pago_Pago",  "GMT-11:00 Samoa",                      -660, -13.8f, -171.8f},
    {"Pacific/Honolulu",   "GMT-10:00 Hawaii",                     -600,  21.3f, -157.9f},
    {"America/Anchorage",  "GMT-09:00 Alaska",                     -540,  61.2f, -149.9f},
    {"America/Los_Angeles","GMT-08:00 Pacific Time (US & Canada)", -480,  34.1f, -118.2f},
    {"America/Tijuana",    "GMT-08:00 Tijuana",                    -480,  32.5f, -117.0f},
    {"America/Denver",     "GMT-07:00 Mountain Time (US & Canada)",-420,  39.7f, -104.99f},
    {"America/Chihuahua",  "GMT-07:00 Chihuahua",                  -420,  28.6f, -106.1f},
    {"America/Chicago",    "GMT-06:00 Central Time (US & Canada)", -360,  41.9f,  -87.6f},
    {"America/Mexico_City","GMT-06:00 Mexico City",                -360,  19.4f,  -99.1f},
    {"America/New_York",   "GMT-05:00 Eastern Time (US & Canada)", -300,  40.7f,  -74.0f},
    {"America/Bogota",     "GMT-05:00 Bogota",                     -300,   4.7f,  -74.1f},
    {"America/Lima",       "GMT-05:00 Lima",                       -300, -12.0f,  -77.0f},
    {"America/Halifax",    "GMT-04:00 Atlantic (Canada)",          -240,  44.6f,  -63.6f},
    {"America/Caracas",    "GMT-04:00 Caracas",                    -240,  10.5f,  -66.9f},
    {"America/Santiago",   "GMT-04:00 Santiago",                   -240, -33.4f,  -70.6f},
    {"America/St_Johns",   "GMT-03:30 Newfoundland",               -210,  47.6f,  -52.7f},
    {"America/Sao_Paulo",  "GMT-03:00 Sao Paulo",                  -180, -23.5f,  -46.6f},
    {"America/Argentina/Buenos_Aires", "GMT-03:00 Buenos Aires",   -180, -34.6f,  -58.4f},
    {"Atlantic/Azores",    "GMT-01:00 Azores",                      -60,  37.7f,  -25.7f},
    {"Europe/London",      "GMT+00:00 London",                        0,  51.5f,   -0.1f},
    {"Europe/Dublin",      "GMT+00:00 Dublin",                        0,  53.3f,   -6.3f},
    {"Europe/Lisbon",      "GMT+00:00 Lisbon",                        0,  38.7f,   -9.1f},
    {"Atlantic/Reykjavik", "GMT+00:00 Reykjavik",                     0,  64.1f,  -21.9f},
    {"Africa/Casablanca",  "GMT+00:00 Casablanca",                    0,  33.6f,   -7.6f},
    {"Europe/Paris",       "GMT+01:00 Paris",                        60,  48.9f,    2.4f},
    {"Europe/Amsterdam",   "GMT+01:00 Amsterdam",                    60,  52.4f,    4.9f},
    {"Europe/Berlin",      "GMT+01:00 Berlin",                       60,  52.5f,   13.4f},
    {"Europe/Rome",        "GMT+01:00 Rome",                         60,  41.9f,   12.5f},
    {"Europe/Madrid",      "GMT+01:00 Madrid",                       60,  40.4f,   -3.7f},
    {"Europe/Stockholm",   "GMT+01:00 Stockholm",                    60,  59.3f,   18.1f},
    {"Africa/Cairo",       "GMT+02:00 Cairo",                       120,  30.0f,   31.2f},
    {"Europe/Athens",      "GMT+02:00 Athens",                      120,  38.0f,   23.7f},
    {"Africa/Johannesburg","GMT+02:00 Johannesburg",                120, -26.2f,   28.0f},
    {"Europe/Helsinki",    "GMT+02:00 Helsinki",                    120,  60.2f,   24.9f},
    {"Europe/Moscow",      "GMT+03:00 Moscow",                      180,  55.8f,   37.6f},
    {"Europe/Istanbul",    "GMT+03:00 Istanbul",                    180,  41.0f,   28.9f},
    {"Africa/Nairobi",     "GMT+03:00 Nairobi",                     180,  -1.3f,   36.8f},
    {"Asia/Tehran",        "GMT+03:30 Tehran",                      210,  35.7f,   51.4f},
    {"Asia/Dubai",         "GMT+04:00 Dubai",                       240,  25.2f,   55.3f},
    {"Asia/Kabul",         "GMT+04:30 Kabul",                       270,  34.5f,   69.2f},
    {"Asia/Karachi",       "GMT+05:00 Karachi",                     300,  24.9f,   67.0f},
    {"Asia/Kolkata",       "GMT+05:30 New Delhi",                   330,  28.6f,   77.2f},
    {"Asia/Kathmandu",     "GMT+05:45 Kathmandu",                   345,  27.7f,   85.3f},
    {"Asia/Dhaka",         "GMT+06:00 Dhaka",                       360,  23.8f,   90.4f},
    {"Asia/Bangkok",       "GMT+07:00 Bangkok",                     420,  13.8f,  100.5f},
    {"Asia/Jakarta",       "GMT+07:00 Jakarta",                     420,  -6.2f,  106.8f},
    {"Asia/Shanghai",      "GMT+08:00 Beijing",                     480,  39.9f,  116.4f},
    {"Asia/Singapore",     "GMT+08:00 Singapore",                   480,   1.35f, 103.8f},
    {"Asia/Hong_Kong",     "GMT+08:00 Hong Kong",                   480,  22.3f,  114.2f},
    {"Asia/Tokyo",         "GMT+09:00 Tokyo",                       540,  35.7f,  139.7f},
    {"Asia/Seoul",         "GMT+09:00 Seoul",                       540,  37.6f,  127.0f},
    {"Australia/Adelaide", "GMT+09:30 Adelaide",                    570, -34.9f,  138.6f},
    {"Australia/Sydney",   "GMT+10:00 Sydney",                      600, -33.9f,  151.2f},
    {"Pacific/Guam",       "GMT+10:00 Guam",                        600,  13.4f,  144.8f},
    {"Pacific/Guadalcanal","GMT+11:00 Solomon Islands",             660,  -9.4f,  159.9f},
    {"Pacific/Auckland",   "GMT+12:00 Auckland",                    720, -36.8f,  174.8f},
    {"Pacific/Apia",       "GMT+13:00 Samoa",                       780, -13.8f, -171.8f},
};
static const int kNumTimezones = sizeof(kTimezones) / sizeof(kTimezones[0]);

// ---------------------------------------------------------------------------
// Setup script log file path
// ---------------------------------------------------------------------------

static const char* kSetupLogPath =
        "/data/data/org.lineageos.setupwizard/files/gammaos_setup.log";

// ---------------------------------------------------------------------------
// Lifecycle
// ---------------------------------------------------------------------------

void NanoMenu::startSetupWizard() {
    mSetupWizardActive = true;
    // Keep the display forced on for the whole wizard. On a framework-owned home (the RG DS
    // never grabs input) PowerManager idles the panel off on the normal timeout, and the
    // "waiting for Android services" step takes no user input, so the device would sleep
    // mid-setup. PowerManagerService.isNanoDisplayForcedOn honours this prop.
    property_set("sys.gammaos.nano.setup_active", "1");
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
    // first (triggers AMS's watchDeviceProvisioning() ContentObserver, which
    // mirrors it to the persisted persist.sys.device_provisioned property --
    // that mirror is what lets a later boot into normal Android mode see this
    // device as already provisioned and skip its own SetupWizard), then
    // USER_SETUP_COMPLETE (unblocks permission grants and storage). Run
    // synchronously so the framework processes each change before the next
    // one lands.
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
    property_set("sys.gammaos.nano.setup_active", "0");   // let the display idle off normally again
    mMenuState = MENU_MAIN;
    mXmbMode = true;
    property_set("persist.gammaos.nano.xmb_mode", "1");
    mDisplayDirty = true;

    // Refresh the Applications + Games lists now that setup is complete: first-boot
    // provisioning may have installed/enabled apps (e.g. the Files app) after nano's
    // initial load, and a WiFi connection made during setup can mount network shares
    // with new ROMs. finishSetupWizard runs on the render/input thread (pollInput ->
    // handleSetup*), so these are safe to call directly here - loadInstalledApps re-reads
    // packages.list, mPs3CatsStale rebuilds the XMB columns at the settled root, and
    // forceRescanAllSystems kicks a background ROM rescan (no-op if one is already running).
    loadInstalledApps();
    mPs3CatsStale = true;
    forceRescanAllSystems();

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

    mTzSelected = -1;
    for (int i = 0; i < kNumTimezones; i++) {
        TimezoneEntry e;
        e.id = kTimezones[i].id;
        e.display = kTimezones[i].label;
        e.offsetMinutes = kTimezones[i].offsetMin;
        e.lon = kTimezones[i].lon;
        e.lat = kTimezones[i].lat;
        mTzEntries.push_back(e);
        if (e.id == curTz) {
            mTzSelected = i;
        }
    }
    // Fallback when the saved tz id is not in the list: prefer London (the web
    // default), then index 0, so the globe always opens on a sensible location.
    if (mTzSelected < 0 || mTzSelected >= (int)mTzEntries.size()) {
        mTzSelected = 0;
        for (int i = 0; i < (int)mTzEntries.size(); i++)
            if (mTzEntries[i].display.find("London") != std::string::npos) { mTzSelected = i; break; }
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

    // Do not enter the Wi-Fi (Internet Connection) step until the system has finished booting.
    // That step runs the real cmd-wifi scan/connect, which needs WifiService up, and that only
    // happens at sys.boot_completed=1. Hold on the current step and auto-advance the moment boot
    // completes (renderSetupWizard polls mSetupWifiWaitPending) so the user never lands on a
    // Wi-Fi step that cannot scan.
    if (next == SETUP_WIFI) {
        char bd[PROPERTY_VALUE_MAX] = {0};
        property_get("sys.boot_completed", bd, "0");
        if (bd[0] != '1') { mSetupWifiWaitPending = true; return; }
    }
    mSetupWifiWaitPending = false;

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
            // The network step runs the full PS3 Internet Connection wizard (1:1
            // with the XMB one). Keep MENU_SETUP_WIZARD so the setup input
            // intercept stays active and routes to the wiz* handlers.
            mMenuState = MENU_SETUP_WIZARD;
            startNetWizard();
        } else if (mSetupStep == SETUP_BLUETOOTH) {
            // The Bluetooth step IS the Manage Bluetooth Devices wizard (1:1 with the
            // XMB one). Keep MENU_SETUP_WIZARD so the setup input intercept routes to
            // the wiz* handlers, exactly like the Wi-Fi step.
            mMenuState = MENU_SETUP_WIZARD;
            mSetupBtWizSeen = false;
            startBtWizard(0);
        } else if (mSetupStep == SETUP_INSTALLING) {
            mMenuState = MENU_SETUP_WIZARD;
            startSetupScript();
        } else if (mSetupStep == SETUP_TIMEZONE) {
            // The timezone step is the 1:1 web 3D-globe selector. Rebuild the zone
            // list (pre-selects the current tz), then start the globe cross-fade
            // aimed at that zone.
            mMenuState = MENU_SETUP_WIZARD;
            buildTimezoneList();
            beginTzGlobeFade();
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
        // The Manage Bluetooth wizard owns input while active (routed via the
        // mPs3WizActive branch in pollInput); nothing to do in the fallback.
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
    // Back closes the OSK first when it is open (wifi password / text fields),
    // exactly like handleBack(). Otherwise Back fell through to goBackSetupStep()
    // while the keyboard stayed up, so the previous step showed under a stuck OSK
    // and the next Enter ran oskConfirm() in the wrong step (broken input).
    if (mOskActive) { closeOsk(); return; }

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
        // O is handled by the Bluetooth wizard while active; in the fallback just
        // step back.
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
        break;   // the Bluetooth wizard owns navigation while active
    case SETUP_TIMEZONE:
        tzGlobeNav(-1);   // move selection (wraps) + ease the globe to the new city
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
        break;   // the Bluetooth wizard owns navigation while active
    case SETUP_TIMEZONE:
        tzGlobeNav(+1);   // move selection (wraps) + ease the globe to the new city
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
        if (mPs3WizActive) {
            // Skip the Internet Connection wizard: close it the same way a
            // normal finish does (renderSetupWizard sees mPs3WizExit>=0 and
            // advances to the next step).
            mPs3WizActive = false;
            mPs3WizExit = 1;
        } else {
            if (mMenuState == MENU_WIFI) {
                closeWifiScreen();
                mMenuState = MENU_SETUP_WIZARD;
            }
            advanceSetupStep();
        }
        break;
    case SETUP_BLUETOOTH:
        if (mPs3WizActive) {
            // Skip the Manage Bluetooth wizard (mSetupBtWizSeen is already set,
            // so renderSetupWizard advances once it sees the wizard closed).
            mPs3WizActive = false;
            mPs3WizExit = 1;
        } else {
            advanceSetupStep();
        }
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

void NanoMenu::renderSetupNdsBackdrop() {
    // DSi theme setup wizard: the wizard steps are forced to the (complete, working) XMB
    // chrome, but they must sit on the DSi background, not the PS3 wave. Draw the DSi home
    // background field here (a custom panel wallpaper when set, else the light #f3f3f3
    // dither field with #dbdbdb edge columns - identical to renderNdsCarousel), then a
    // dim + blue tint over it so the XMB wizard's light chrome/text stays readable and
    // reads like the XMB dialog backdrop (a blurred blue wave under a dark dim).
    setUiBlend();
    const float W = (float)mWidth, H = (float)mHeight;
    if (wallpaperActive(mRenderingPanel)) {
        drawWallpaperFill(mRenderingPanel);
    } else {
        drawQuad(0.0f, 0.0f, W, H, 0.953f, 0.953f, 0.953f, 1.0f);
        float dl = H / 192.0f * 2.0f; if (dl < 2.0f) dl = 2.0f;
        float lh = fmaxf(1.0f, H / 192.0f);
        for (float y = 0.0f; y < H; y += dl) drawQuad(0.0f, y, W, lh, 0.922f, 0.922f, 0.922f, 1.0f);
        float ec = fmaxf(1.0f, W / 256.0f);
        drawQuad(0.0f, 0.0f, ec, H, 0.859f, 0.859f, 0.859f, 1.0f);
        drawQuad(W - ec, 0.0f, ec, H, 0.859f, 0.859f, 0.859f, 1.0f);
    }
    // Dim + blue, like the XMB fullscreen dialogs (a dark dim over the blue frosted wave):
    // a low blue cast then a black dim, so the light DSi field darkens to a readable blue-grey
    // (~0.36 luma) that keeps the forced-XMB white chrome/text legible.
    drawQuad(0.0f, 0.0f, W, H, 0.10f, 0.18f, 0.42f, 0.30f);   // blue tint
    drawQuad(0.0f, 0.0f, W, H, 0.0f,  0.0f,  0.0f,  0.50f);   // dim
}

void NanoMenu::renderSetupWizard() {
    updateSetupTransition();
    // DSi theme: paint the DSi background + dim/blue behind the whole wizard first, so every
    // step (the XMB-forced Wi-Fi/Bluetooth net wizard, and the language/timezone/installing/
    // finish steps below) sits on the DSi backdrop instead of the PS3 wave. The net-wizard
    // steps early-return through renderNetWizard() below, so this must run before them.
    if (mNdsTheme) renderSetupNdsBackdrop();

    // A "next" into the Wi-Fi step is deferred until the system finishes first-boot setup: the
    // Wi-Fi wizard runs a real scan/connect that cannot work before sys.boot_completed. While
    // waiting, show an XMB-style "please wait" progress screen (reusing the net-wizard chrome), and
    // auto-advance into Wi-Fi the instant boot completes, with no second press.
    if (mSetupWifiWaitPending) {
        char bd[PROPERTY_VALUE_MAX] = {0};
        property_get("sys.boot_completed", bd, "0");
        if (bd[0] == '1') {
            mSetupWifiWaitPending = false;
            mPs3WizActive = false;          // drop the wait screen
            advanceSetupStep();             // now enters SETUP_WIFI and starts the real net wizard
        } else {
            if (!mPs3WizActive) startSetupBootWaitScreen();  // WK_PROGRESS net-wizard page (enum is file-local to NanoMenuPS3Menu.cpp)
            renderNetWizard();
            return;
        }
    }

    // Anti-alias the wizard text on every step AFTER the Hello/Welcome screen
    // (language, timezone, the Wi-Fi/Bluetooth net wizard, installing, finish).
    // The Welcome greeting is large animated text that should stay crisp, and
    // the OSK is forced back to GL_LINEAR in renderOsk(). Set before the
    // Wi-Fi/Bluetooth early dispatch below so renderNetWizard() inherits it.
    setGlyphAtlasAA(mSetupStep != SETUP_WELCOME);

    // The Wi-Fi step IS the full PS3 Internet Connection wizard. It renders its
    // own fullscreen chrome (blurred wave + dim + header/footer), so dispatch it
    // here and skip the setup chrome. When it closes, advance to the next step
    // (completed/forward) or step back (cancelled out of the intro).
    if (mSetupStep == SETUP_WIFI) {
        if (mPs3WizActive) {
            mSetupNetWizSeen = true;
            renderNetWizard();          // OSK (password fields) is drawn by the caller
            drawSetupSkipHint();        // Start skips the whole wireless step
            return;
        } else if (mSetupNetWizSeen) {
            mSetupNetWizSeen = false;
            if (mPs3WizExit >= 0) advanceSetupStep(); else goBackSetupStep();
        }
    }
    // The Bluetooth step IS the Manage Bluetooth Devices wizard. It renders its own
    // fullscreen chrome, so dispatch it here. Manage is a hub with no terminal
    // screen, so exiting it (backing out) means "done with Bluetooth" -> advance.
    if (mSetupStep == SETUP_BLUETOOTH) {
        if (mPs3WizActive) {
            mSetupBtWizSeen = true;
            renderNetWizard();
            drawSetupSkipHint();        // Start skips the whole Bluetooth step
            return;
        } else if (mSetupBtWizSeen) {
            mSetupBtWizSeen = false;
            advanceSetupStep();
        }
    }


    // Fade-in when not transitioning (lerp alpha toward 1.0)
    if (!mSetupTransitioning && mSetupTransitionAlpha < 1.0f) {
        mSetupTransitionAlpha += 0.06f;
        if (mSetupTransitionAlpha > 1.0f) mSetupTransitionAlpha = 1.0f;
        mSetupSlideOffset *= 0.85f; // ease slide to zero
    }

    // Light dim over wallpaper (skip on welcome for clean iOS-style look; skip on
    // the timezone step too - the 3D globe is its own opaque backdrop, matching
    // the web tzglobe screen which draws no dim panel). Under the DSi theme the
    // renderSetupNdsBackdrop() above already applied the dim + blue, so skip this
    // one to avoid crushing the background to black.
    if (!mNdsTheme && mSetupStep != SETUP_WELCOME && mSetupStep != SETUP_TIMEZONE) {
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

    // The timezone globe screen draws its own footer + has no progress dots in
    // the web tzglobe layout, so skip the setup dots there.
    if (mSetupStep != SETUP_WELCOME && mSetupStep != SETUP_TIMEZONE) {
        renderSetupProgressDots();
    }
}

void NanoMenu::drawSetupSkipHint() {
    // Top-right "Start: Skip" hint over the Wireless / Bluetooth net wizard, in the
    // same PS3 dialog metrics as the footer hints so it matches the chrome. Use a
    // fixed alpha: the Wi-Fi/Bluetooth branches return before renderSetupWizard's
    // fade-in runs, so mSetupTransitionAlpha is stuck at 0 on these steps.
    float alpha = 1.0f;
    { ps3::LayoutParams lp; lp.panelW = mWidth; lp.panelH = mHeight;
      lp.uiScale = mPs3UiScale; ps3::layoutCompute(lp); }
    float ui = mPs3UiScale; if (ui < 0.5f) ui = 0.5f; if (ui > 2.0f) ui = 2.0f;
    const float S = ps3::gScale / ui;
    const float offX = ps3::gFrameX + (ps3::gFrameW - S * ps3::XCF(ps3::VW)) * 0.5f;
    const float offY = ps3::gFrameY + ps3::gFrameH * 0.5f - S * (ps3::VH * 0.5f);
    auto XC = [&](float vx) { return S * ps3::XCF(vx) + offX; };
    auto Y  = [&](float vy) { return S * vy + offY; };
    int   savedMode  = mTextOutlineMode;
    float savedRatio = mTextOutlineRatio;
    mTextOutlineMode = 1; mTextOutlineRatio = 0.5f;
    // Centred, just under the Enter / Cancel footer row (the net wizard draws
    // those at Y(909)).
    ps3DlgHintG(XC(ps3::VW * 0.5f), 2, "Skip", Y(955.0f), S, alpha);
    mTextOutlineMode = savedMode;
    mTextOutlineRatio = savedRatio;
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
        snprintf(loadMsg, sizeof(loadMsg), "%s  %s", spinner[spinIdx], trDyn("Loading driver..."));
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
             0.9f, 0.9f, 0.95f, alpha * 0.85f);
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
        snprintf(loadMsg, sizeof(loadMsg), "%s  %s", spinner[spinIdx], trDyn("Loading driver..."));
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
             0.9f, 0.9f, 0.95f, alpha * 0.85f);
}

void NanoMenu::renderSetupTimezone() {
    // The setup wizard timezone step is the 1:1 web 3D-globe selector, shared
    // with the XMB Date and Time -> Time Zone view (NanoMenuPS3Menu.cpp). It
    // draws the raymarched Earth backdrop + the "Time Zone" header + the
    // right-aligned GMT zone list + footer, with its own cross-fade.
    renderTimezoneGlobe();
}

void NanoMenu::renderSetupInstalling() {
    float alpha = mSetupTransitionAlpha;
    // PS3 fullscreen-dialog layout (1:1 with the Wireless / Bluetooth wizard):
    // a left title with top/bottom dividers, the configuration log in the body,
    // and a Start-Continue footer hint once the script finishes.
    { ps3::LayoutParams lp; lp.panelW = mWidth; lp.panelH = mHeight;
      lp.uiScale = mPs3UiScale; ps3::layoutCompute(lp); }
    float ui = mPs3UiScale; if (ui < 0.5f) ui = 0.5f; if (ui > 2.0f) ui = 2.0f;
    const float S = ps3::gScale / ui;
    // Honor the wizard slide so this screen slides + cross-fades in like the
    // other steps (it used absolute coords before, so it only faded -> abrupt).
    const float offX = ps3::gFrameX + (ps3::gFrameW - S * ps3::XCF(ps3::VW)) * 0.5f
                     + mSetupSlideOffset;
    const float offY = ps3::gFrameY + ps3::gFrameH * 0.5f - S * (ps3::VH * 0.5f);
    auto X  = [&](float vx) { return S * vx + offX; };
    auto XC = [&](float vx) { return S * ps3::XCF(vx) + offX; };
    auto Y  = [&](float vy) { return S * vy + offY; };
    auto DS = [&](float v)  { return S * v; };
    const float fb = ps3DlgFontBoost();
    auto FS = [&](float px) { return S * px * fb / 16.0f; };
    const float VW = ps3::VW;
    const float innerTop = 199.0f, innerBot = 880.0f;

    int   savedMode  = mTextOutlineMode;
    float savedRatio = mTextOutlineRatio;
    mTextOutlineMode = 1; mTextOutlineRatio = 0.5f;

    // Header: title + dividers.
    const char* title = mSetupScriptDone ? tr(STR_SETUP_INSTALL_DONE)
                                         : tr(STR_SETUP_INSTALL_TITLE);
    ps3DlgText(title, X(160.0f), Y(187.0f), FS(28.0f), 1, 1, 1, alpha, 0);
    float divLw = fmaxf(1.0f, DS(1.0f));
    drawQuad(ps3::gFrameX, Y(innerTop), ps3::gFrameW, divLw, 1, 1, 1, 0.55f * alpha);
    drawQuad(ps3::gFrameX, Y(innerBot), ps3::gFrameW, divLw, 1, 1, 1, 0.55f * alpha);

    // Body: the configuration log. Word-wrapping every frame over the full (and
    // growing) log was O(total chars) of measureText per frame and tanked the FPS,
    // which persisted after the script finished because the log stayed large. Wrap
    // only the visible tail, cached, re-wrapping only when the log grows or the
    // frame width changes - so steady state (and the finished screen) is free.
    const float lfs = FS(17.0f);
    const float logLeft = X(160.0f);
    float maxLogW = X(ps3::VW - 160.0f) - logLeft;   // slide-invariant (offX cancels)
    if (maxLogW < 40.0f) maxLogW = 40.0f;
    const float logTopV = innerTop + 46.0f, logRowV = 28.0f;
    float availV = (innerBot - 26.0f) - logTopV;
    int visibleRows = (int)(availV / logRowV); if (visibleRows < 4) visibleRows = 4;
    size_t curLines;
    { std::lock_guard<std::mutex> lk(mSetupLogMutex); curLines = mSetupLogLines.size(); }
    if ((int)curLines != mSetupLogRowsForLines || maxLogW != mSetupLogRowsForW) {
        std::vector<std::string> tail;
        {
            std::lock_guard<std::mutex> lk(mSetupLogMutex);
            int from = (int)mSetupLogLines.size() - (visibleRows + 6);
            if (from < 0) from = 0;
            for (int i = from; i < (int)mSetupLogLines.size(); i++) tail.push_back(mSetupLogLines[i]);
        }
        mSetupLogRows.clear();
        for (const std::string& ln : tail) {
            int kind = 0;
            if (ln.find("Error") != std::string::npos ||
                ln.find("error") != std::string::npos) kind = 2;
            else if (ln.find("completed") != std::string::npos ||
                     ln.find("successfully") != std::string::npos) kind = 3;
            else if (ln.find("Installing") != std::string::npos ||
                     ln.find("Extracting") != std::string::npos) kind = 1;
            if (ln.empty()) { mSetupLogRows.push_back({"", kind}); continue; }
            // Greedy wrap: break at the last space that fits; hard-break a single
            // long token (a path) mid-token.
            std::string cur;
            for (size_t c = 0; c < ln.size(); c++) {
                std::string trial = cur + ln[c];
                if (!cur.empty() && measureText(trial.c_str(), lfs) > maxLogW) {
                    size_t sp = cur.find_last_of(' ');
                    if (sp != std::string::npos && sp > 0) {
                        mSetupLogRows.push_back({cur.substr(0, sp), kind});
                        cur = cur.substr(sp + 1) + ln[c];
                    } else {
                        mSetupLogRows.push_back({cur, kind});
                        cur = std::string(1, ln[c]);
                    }
                } else cur = trial;
            }
            if (!cur.empty()) mSetupLogRows.push_back({cur, kind});
        }
        mSetupLogRowsForLines = (int)curLines;
        mSetupLogRowsForW = maxLogW;
    }
    int totalRows = (int)mSetupLogRows.size();
    int startR = (totalRows > visibleRows) ? totalRows - visibleRows : 0;
    for (int i = startR; i < totalRows; i++) {
        int kind = mSetupLogRows[i].second;
        float r = 0.75f, g = 0.78f, b = 0.82f;
        if (kind == 1)      { r = 0.45f; g = 0.9f;  b = 0.55f; }
        else if (kind == 2) { r = 1.0f;  g = 0.45f; b = 0.45f; }
        else if (kind == 3) { r = 0.4f;  g = 0.95f; b = 0.6f;  }
        ps3DlgText(mSetupLogRows[i].first.c_str(), logLeft,
                   Y(logTopV + (float)(i - startR) * logRowV),
                   lfs, r, g, b, alpha * 0.92f, 0);
    }

    // Footer: Start-Continue once done, otherwise a centred "Please wait...".
    float hintY = Y(909.0f);
    if (mSetupScriptDone) {
        ps3DlgHintG(XC(VW * 0.5f), 2, "Continue", hintY, S, alpha);
    } else {
        ps3DlgText(tr(STR_SETUP_INSTALL_WAIT), XC(VW * 0.5f), Y(916.0f), FS(20.0f),
                   0.9f, 0.9f, 0.95f, 0.9f * alpha, 1);
    }

    mTextOutlineMode = savedMode;
    mTextOutlineRatio = savedRatio;
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
    // Live locale preview as the user scrolls, then the shared fullscreen language
    // list (1:1 with the Settings -> System Language picker). renderLanguageList
    // keeps the native names verbatim, so the English row stays "English".
    nanoSetLocale((NanoLocale)mLangSelected);
    renderLanguageList(tr(STR_SETUP_LANG_TITLE), mSetupTransitionAlpha);
}

} // namespace android
