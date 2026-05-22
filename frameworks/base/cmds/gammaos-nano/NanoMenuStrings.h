/*
 * Copyright (C) 2026 GammaOS
 *
 * Localization string IDs for the NanoMenu UI. All user-visible text
 * goes through tr(StringId) which returns the string for the current
 * locale. The translation tables live in NanoMenuStrings.cpp.
 */

#ifndef GAMMAOS_NANO_STRINGS_H
#define GAMMAOS_NANO_STRINGS_H

namespace android {

enum StringId {
    // Main menu
    STR_APP_TITLE,               // "GammaOS Nano"
    STR_APP_VERSION,             // "v0.1 - Proof of Concept"
    STR_MENU_RETROARCH,          // "RetroArch (Nano)"
    STR_MENU_RECENTLY_PLAYED,    // "Recently Played"
    STR_MENU_APPLICATIONS,       // "Applications"
    STR_MENU_BOOT_ANDROID,       // "Boot Android"
    STR_MENU_RECOVERY,           // "Recovery Mode"
    STR_MENU_SAFE_MODE,          // "Safe Mode"
    STR_MENU_REBOOT,             // "Reboot"
    STR_MENU_POWER_OFF,          // "Power Off"
    STR_BACK,                    // "< Back"

    // Main menu footers
    STR_FOOTER_MAIN,             // "DPAD: Nav | A: Select | ..."
    STR_FOOTER_RECENT,           // "DPAD/VOL: Nav | A/PWR: Select | ..."
    STR_FOOTER_APPS,             // "DPAD/VOL: Nav | A/PWR: Select | ..."

    // Status messages
    STR_LOADING,                 // "Loading..."
    STR_PLEASE_WAIT_STORAGE,     // "Please wait, unlocking storage..."
    STR_NO_RECENT_GAMES,         // "No recent games found"
    STR_NO_INSTALLED_APPS,       // "No installed apps found"
    STR_BOOTING_UP,              // "Booting up..."
    STR_GAME_LAUNCH_SOON,        // "Your game will launch shortly"
    STR_QUICK_RESUMING,          // "Quick Resuming..."

    // XMB
    STR_XMB_SETTINGS,            // "Settings"
    STR_XMB_RECENTLY_PLAYED,     // "Recently Played"
    STR_XMB_NO_RECENT,           // "No recently played games"
    STR_XMB_NO_GAMES,            // "No games found"
    STR_XMB_QUICK_RESUME,        // "Quick Resume"
    STR_XMB_SEARCH,              // "Search"

    // Wi-Fi screen
    STR_WIFI_TITLE,              // "Wi-Fi"
    STR_WIFI_SCANNING,           // "Scanning..."
    STR_WIFI_NO_NETWORKS,        // "No Wi-Fi networks. Press X to rescan."
    STR_WIFI_CONNECTING,         // "Connecting..."
    STR_WIFI_CONNECTED,          // "Connected"
    STR_WIFI_SAVED,              // "Saved"
    STR_WIFI_FOOTER,             // "A: Connect | X: Rescan | B: Back"
    STR_WIFI_SEC_OPEN,           // "Open"
    STR_WIFI_SEC_WEP,            // "WEP"
    STR_WIFI_SEC_WPA2,           // "WPA2"
    STR_WIFI_SEC_WPA3,           // "WPA3"
    STR_WIFI_SEC_OWE,            // "OWE"
    STR_WIFI_ON,                 // "On"
    STR_WIFI_OFF,                // "Off"

    // Bluetooth screen
    STR_BT_TITLE,                // "Bluetooth"
    STR_BT_SCANNING,             // "Scanning for devices..."
    STR_BT_NO_DEVICES,           // "No devices.  Press X to scan for nearby Bluetooth devices."
    STR_BT_SCAN_COMPLETE,        // "Scan complete"
    STR_BT_PAIRING,              // "Pairing..."
    STR_BT_PAIRED,               // "Paired"
    STR_BT_PAIR_FAILED,          // "Pair failed"
    STR_BT_REMOVED,              // "Removed"
    STR_BT_UNPAIR_FAILED,        // "Unpair failed"
    STR_BT_CONNECTED,            // "Connected"
    STR_BT_AVAILABLE,            // "Available"
    STR_BT_FOOTER,               // "A: Pair/Connect | X: Scan | B: Back"
    STR_BT_FOOTER_BONDED,        // "A: Connect | Y: Unpair | X: Scan | B: Back"

    // Settings tree
    STR_SETTINGS_TITLE,          // "Settings"
    STR_SETTINGS_FOOTER,         // "A: Select | B: Back | L/R: Adjust"

    // Setup wizard
    STR_SETUP_WELCOME_TITLE,     // "Welcome to GammaOS"
    STR_SETUP_WELCOME_SUB,       // "Let's get your device set up"
    STR_SETUP_PRESS_A,           // "Press A to begin"
    STR_SETUP_SKIP,              // "Start: Skip setup"
    STR_SETUP_WIFI_TITLE,        // "Wi-Fi Setup"
    STR_SETUP_WIFI_HINT,         // "Connecting to Wi-Fi..."
    STR_SETUP_WIFI_FOOTER,       // "Start: Next step | B: Skip Wi-Fi"
    STR_SETUP_BT_TITLE,          // "Bluetooth Setup"
    STR_SETUP_BT_HINT,           // "Scanning for Bluetooth devices..."
    STR_SETUP_BT_FOOTER,         // "Start: Next step | B: Skip Bluetooth"
    STR_SETUP_TZ_TITLE,          // "Select Timezone"
    STR_SETUP_TZ_FOOTER,         // "A: Select timezone | Start: Next | B: Back"
    STR_SETUP_INSTALL_TITLE,     // "Configuring GammaOS..."
    STR_SETUP_INSTALL_DONE,      // "Configuration Complete"
    STR_SETUP_INSTALL_WAIT,      // "Please wait..."
    STR_SETUP_INSTALL_CONTINUE,  // "Start: Continue"
    STR_SETUP_INSTALL_STARTING,  // "Starting system configuration..."
    STR_SETUP_INSTALL_BOOT_WAIT, // "Waiting for system boot to complete..."
    STR_SETUP_FINISH_TITLE,      // "You're all set!"
    STR_SETUP_FINISH_SUB,        // "Your device is ready to use"
    STR_SETUP_FINISH_PRESS_A,    // "Press A to start"

    // Setup wizard - Language selection
    STR_SETUP_LANG_TITLE,        // "Select Language"
    STR_SETUP_LANG_FOOTER,       // "A: Select | D-Pad: Navigate"

    STR_COUNT
};

// Supported locales. The enum order matches the translation table
// column order in NanoMenuStrings.cpp.
enum NanoLocale {
    LOCALE_EN = 0,   // English
    LOCALE_ES,       // Spanish
    LOCALE_FR,       // French
    LOCALE_DE,       // German
    LOCALE_IT,       // Italian
    LOCALE_PT,       // Portuguese
    LOCALE_NL,       // Dutch
    LOCALE_RU,       // Russian
    LOCALE_JA,       // Japanese
    LOCALE_KO,       // Korean
    LOCALE_ZH_CN,    // Chinese (Simplified)
    LOCALE_ZH_TW,    // Chinese (Traditional)
    LOCALE_AR,       // Arabic
    LOCALE_TR,       // Turkish
    LOCALE_PL,       // Polish
    LOCALE_COUNT
};

struct LocaleInfo {
    const char* code;       // e.g. "en", "es", "fr"
    const char* regionCode; // e.g. "US", "ES", "FR" (default region)
    const char* nativeName; // e.g. "English", "Espanol"
    const char* englishName;
};

// Get the translated string for the current locale
const char* tr(StringId id);

// Get/set the active locale
NanoLocale nanoGetLocale();
void nanoSetLocale(NanoLocale locale);

// Get locale info for the language picker
const LocaleInfo& nanoGetLocaleInfo(NanoLocale locale);

// Initialize locale from system property (persist.sys.locale)
void nanoInitLocaleFromSystem();

// Apply the current locale to the Android system
void nanoApplyLocaleToSystem();

} // namespace android

#endif // GAMMAOS_NANO_STRINGS_H
