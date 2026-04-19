/*
 * Copyright (C) 2026 GammaOS
 *
 * Settings column: pseudo-system on the XMB column bar that hosts
 * Wi-Fi + Bluetooth configuration screens. Screens shell out to the
 * framework `cmd wifi` / `cmd bluetooth_manager` CLIs because the
 * bootanim SELinux domain can't talk AIDL to those services directly
 * but it can fork-exec a shell-out.
 *
 *   cmd wifi list-networks         -> saved networks (id, ssid, security)
 *   cmd wifi start-scan            -> kicks an async radio scan
 *   cmd wifi list-scan-results     -> last scan results (bssid, rssi, flags, ssid)
 *   cmd wifi connect-network ...   -> connect to either saved-id or new open/wpa2 net
 *   cmd wifi forget-network <id>   -> remove saved net
 *   cmd wifi set-wifi-enabled on|off
 *
 *   cmd bluetooth_manager list-bonded-devices
 *   dumpsys bluetooth_manager      -> name, connected status, bonded devices
 *   cmd bluetooth_manager enable|disable
 *   sm pair <mac>                  -- no standalone pair CLI; we use "bluetoothctl" which
 *                                      isn't on AOSP, so we just flip the "enabled" state and
 *                                      rely on a cached bond being re-connected automatically.
 */

#define LOG_TAG "GammaOSNano"

#include <algorithm>
#include <cctype>
#include <chrono>
#include <cmath>
#include <cstdio>
#include <cstring>
#include <functional>
#include <mutex>
#include <string>
#include <thread>
#include <unistd.h>
#include <utility>
#include <vector>

#include <android-base/properties.h>
#include <cutils/properties.h>
#include <log/log.h>
#include <utils/SystemClock.h>

#include <GLES2/gl2.h>

#include "NanoMenu.h"
#include "NanoMenuShaders.h"

namespace android {

namespace {

// runShellout: bounded popen() that caps total output + time so a hung
// shell-out can't freeze the settings UI thread.
std::string runShellout(const char* command, int timeoutSec = 4) {
    (void)timeoutSec;
    std::string out;
    out.reserve(1024);
    FILE* f = popen(command, "r");
    if (!f) return out;
    char buf[512];
    const size_t kMax = 64 * 1024;
    while (fgets(buf, sizeof(buf), f)) {
        out.append(buf);
        if (out.size() > kMax) { out.resize(kMax); break; }
    }
    pclose(f);
    return out;
}

int rssiToBars(int rssi) {
    if (rssi >= -55) return 4;
    if (rssi >= -66) return 3;
    if (rssi >= -77) return 2;
    if (rssi >= -88) return 1;
    return 0;
}

int securityFromFlags(const std::string& flags) {
    // Scan-result "flags" column looks like: [WPA2-PSK-CCMP][ESS] or [ESS] (open)
    // We just pick the strongest hint.
    std::string f = flags;
    for (auto& c : f) c = (char)toupper((unsigned char)c);
    if (f.find("WPA3") != std::string::npos
            || f.find("SAE") != std::string::npos) return 3;
    if (f.find("WPA2") != std::string::npos
            || f.find("WPA") != std::string::npos
            || f.find("PSK") != std::string::npos) return 2;
    if (f.find("WEP") != std::string::npos) return 1;
    if (f.find("OWE") != std::string::npos) return 4;
    return 0;
}

// Parse `cmd wifi list-networks` tabular output.
// Typical line:
//    Network Id  SSID                            Security type
//     0          MyHomeWiFi                      WPA2_PSK
std::vector<NanoMenu::WifiNetEntry> parseSavedNetworks(
        const std::string& text) {
    std::vector<NanoMenu::WifiNetEntry> out;
    size_t p = 0;
    bool skippedHeader = false;
    while (p < text.size()) {
        size_t eol = text.find('\n', p);
        if (eol == std::string::npos) eol = text.size();
        std::string line = text.substr(p, eol - p);
        p = eol + 1;
        // Strip trailing \r
        while (!line.empty() && (line.back() == '\r' || line.back() == ' '))
            line.pop_back();
        if (line.empty()) continue;
        // Skip the "Network Id" header line.
        if (!skippedHeader) {
            if (line.find("Network Id") != std::string::npos
                    || line.find("No networks") != std::string::npos) {
                skippedHeader = true;
                continue;
            }
            skippedHeader = true;
        }
        // Try to read an integer id at the start.
        size_t i = 0;
        while (i < line.size() && isspace((unsigned char)line[i])) i++;
        if (i >= line.size() || !isdigit((unsigned char)line[i])) continue;
        int id = atoi(line.c_str() + i);
        while (i < line.size() && !isspace((unsigned char)line[i])) i++;
        while (i < line.size() && isspace((unsigned char)line[i])) i++;
        if (i >= line.size()) continue;
        // SSID can contain spaces; consume up to the last run of
        // non-space characters for the Security column.
        size_t secEnd = line.size();
        while (secEnd > 0 && !isspace((unsigned char)line[secEnd - 1])) secEnd--;
        // Now secEnd is the start of the security token.
        size_t secStart = secEnd;
        while (secEnd < line.size() && !isspace((unsigned char)line[secEnd])) secEnd++;
        std::string secTok = line.substr(secStart, secEnd - secStart);
        // SSID spans from i up to secStart with trailing spaces trimmed.
        size_t ssidEnd = secStart;
        while (ssidEnd > i && isspace((unsigned char)line[ssidEnd - 1])) ssidEnd--;
        std::string ssid = line.substr(i, ssidEnd - i);
        if (ssid.empty()) continue;

        NanoMenu::WifiNetEntry e{};
        e.ssid = ssid;
        e.savedNetId = id;
        e.rssi = -127;
        std::string sec = secTok;
        for (auto& c : sec) c = (char)toupper((unsigned char)c);
        if (sec.find("SAE") != std::string::npos || sec == "WPA3_PSK") e.security = 3;
        else if (sec.find("WPA") != std::string::npos || sec.find("PSK") != std::string::npos) e.security = 2;
        else if (sec.find("WEP") != std::string::npos) e.security = 1;
        else if (sec.find("OWE") != std::string::npos) e.security = 4;
        else e.security = 0;
        e.connected = false;
        out.push_back(std::move(e));
    }
    return out;
}

// Parse `cmd wifi list-scan-results` column output.
// Header line:
//    BSSID   Frequency       RSSI    Age(sec)        SSID    Flags
std::vector<NanoMenu::WifiNetEntry> parseScanResults(const std::string& text) {
    std::vector<NanoMenu::WifiNetEntry> out;
    size_t p = 0;
    bool sawHeader = false;
    while (p < text.size()) {
        size_t eol = text.find('\n', p);
        if (eol == std::string::npos) eol = text.size();
        std::string line = text.substr(p, eol - p);
        p = eol + 1;
        while (!line.empty() && (line.back() == '\r' || line.back() == ' '))
            line.pop_back();
        if (line.empty()) continue;
        if (!sawHeader) {
            if (line.find("BSSID") != std::string::npos
                    && line.find("SSID") != std::string::npos) {
                sawHeader = true;
                continue;
            }
            // Allow parsing even without the header (some tools skip it).
        }
        // Whitespace split into up to 6 columns.
        std::vector<std::string> cols;
        size_t i = 0;
        while (i < line.size()) {
            while (i < line.size() && isspace((unsigned char)line[i])) i++;
            if (i >= line.size()) break;
            size_t j = i;
            while (j < line.size() && !isspace((unsigned char)line[j])) j++;
            cols.push_back(line.substr(i, j - i));
            i = j;
        }
        if (cols.size() < 6) continue;
        // bssid, freq, rssi, age, ssid, flags
        NanoMenu::WifiNetEntry e{};
        e.bssid = cols[0];
        e.rssi = atoi(cols[2].c_str());
        if (e.rssi >= 0 || e.rssi < -120) e.rssi = -127;
        e.ssid = cols[4];
        // Strip surrounding quotes
        if (e.ssid.size() >= 2
                && e.ssid.front() == '"' && e.ssid.back() == '"') {
            e.ssid = e.ssid.substr(1, e.ssid.size() - 2);
        }
        // Flags is everything after ssid.
        std::string flags;
        for (size_t k = 5; k < cols.size(); k++) {
            if (!flags.empty()) flags += ' ';
            flags += cols[k];
        }
        e.security = securityFromFlags(flags);
        e.savedNetId = -1;
        e.connected = false;
        if (!e.ssid.empty() && e.ssid != "<unknown ssid>") {
            out.push_back(std::move(e));
        }
    }
    return out;
}

// Merge saved + scan results, dedup by SSID. A saved network shadows a
// scan result with the same SSID (we keep the rssi/security from the
// scan if available for better display).
std::vector<NanoMenu::WifiNetEntry> mergeWifiLists(
        std::vector<NanoMenu::WifiNetEntry> saved,
        std::vector<NanoMenu::WifiNetEntry> scanned,
        const std::string& connectedSsid) {
    // Index saved by SSID
    for (auto& s : scanned) {
        int foundId = -1;
        int foundSec = s.security;
        for (auto& v : saved) {
            if (v.ssid == s.ssid) {
                foundId = v.savedNetId;
                if (v.security != 0) foundSec = v.security;
                break;
            }
        }
        s.savedNetId = foundId;
        s.security = foundSec;
        if (!connectedSsid.empty() && s.ssid == connectedSsid) s.connected = true;
    }
    // Append saved networks that weren't in scan results (shown as greyed).
    for (auto& v : saved) {
        bool found = false;
        for (auto& s : scanned) {
            if (s.ssid == v.ssid) { found = true; break; }
        }
        if (!found) {
            if (!connectedSsid.empty() && v.ssid == connectedSsid)
                v.connected = true;
            scanned.push_back(std::move(v));
        }
    }
    // Sort: connected first, then saved, then by rssi desc.
    std::sort(scanned.begin(), scanned.end(),
              [](const NanoMenu::WifiNetEntry& a,
                 const NanoMenu::WifiNetEntry& b) {
        if (a.connected != b.connected) return a.connected;
        bool aSaved = a.savedNetId >= 0, bSaved = b.savedNetId >= 0;
        if (aSaved != bSaved) return aSaved;
        return a.rssi > b.rssi;
    });
    return scanned;
}

// Extract currently-connected SSID from `cmd wifi status`.
std::string connectedSsidFromStatus(const std::string& text) {
    const char* key = "SSID:";
    size_t p = text.find(key);
    while (p != std::string::npos) {
        size_t s = p + strlen(key);
        while (s < text.size() && text[s] == ' ') s++;
        size_t e = text.find(", BSSID:", s);
        if (e == std::string::npos) e = text.find('\n', s);
        if (e == std::string::npos) e = text.size();
        std::string ssid = text.substr(s, e - s);
        if (ssid.size() >= 2 && ssid.front() == '"' && ssid.back() == '"')
            ssid = ssid.substr(1, ssid.size() - 2);
        if (!ssid.empty() && ssid != "<unknown ssid>" && ssid != "<none>")
            return ssid;
        p = text.find(key, p + 1);
    }
    return "";
}

// Parse `cmd bluetooth_manager list-bonded-devices` output.
// Typical line format from AOSP source:
//    DE:AD:BE:EF:00:01: "Controller", BTClass=1234, Trans=Unknown
std::vector<NanoMenu::BtDevEntry> parseBondedDevices(const std::string& text) {
    std::vector<NanoMenu::BtDevEntry> out;
    size_t p = 0;
    while (p < text.size()) {
        size_t eol = text.find('\n', p);
        if (eol == std::string::npos) eol = text.size();
        std::string line = text.substr(p, eol - p);
        p = eol + 1;
        while (!line.empty() && (line.back() == '\r' || line.back() == ' '))
            line.pop_back();
        if (line.empty()) continue;
        if (line.find(':') == std::string::npos) continue;
        // MAC is first 17 chars if the line starts with a hex pair.
        if (line.size() < 17) continue;
        if (!isxdigit((unsigned char)line[0]) || !isxdigit((unsigned char)line[1]))
            continue;
        std::string mac = line.substr(0, 17);
        NanoMenu::BtDevEntry d{};
        d.address = mac;
        d.bonded = true;
        d.connected = false;
        // Try to pull name: look for first '"' after MAC.
        size_t qStart = line.find('"', 17);
        if (qStart != std::string::npos) {
            size_t qEnd = line.find('"', qStart + 1);
            if (qEnd != std::string::npos) {
                d.name = line.substr(qStart + 1, qEnd - qStart - 1);
            }
        }
        if (d.name.empty()) d.name = mac;
        out.push_back(std::move(d));
    }
    return out;
}

// Mark connected devices using dumpsys output.
void markConnectedBt(const std::string& dump,
                     std::vector<NanoMenu::BtDevEntry>* devs) {
    for (auto& d : *devs) {
        size_t p = dump.find(d.address);
        if (p == std::string::npos) continue;
        size_t eol = dump.find('\n', p);
        if (eol == std::string::npos) eol = dump.size();
        // Look a few lines ahead for "Connected = true"
        size_t look = eol;
        for (int i = 0; i < 6 && look < dump.size(); i++) {
            size_t nl = dump.find('\n', look + 1);
            if (nl == std::string::npos) nl = dump.size();
            std::string seg = dump.substr(look, nl - look);
            if (seg.find("Connected = true") != std::string::npos
                    || seg.find("Connected: true") != std::string::npos) {
                d.connected = true;
                break;
            }
            look = nl + 1;
        }
    }
}

} // anonymous namespace

// ---------------------------------------------------------------------------
// Settings column lifecycle
// ---------------------------------------------------------------------------

void NanoMenu::initSettingsItems() {
    mSettingsItems.clear();
    mSettingsItems.push_back({"Wi-Fi", 0});
    mSettingsItems.push_back({"Bluetooth", 1});
    mSettingsSelectedIndex = 0;
}

bool NanoMenu::isOnSettingsColumn() const {
    return mXmbMode
        && mXmbSystemIndex == (int)mXmbSystems.size()
        && !mSettingsItems.empty();
}

// ---------------------------------------------------------------------------
// Wi-Fi screen
// ---------------------------------------------------------------------------

void NanoMenu::openWifiScreen() {
    mMenuState = MENU_WIFI;
    mWifiEntrySelected = 0;
    mWifiScrollTop = 0;
    mDisplayDirty = true;
    refreshWifiList();
    startWifiScanAsync();
}

void NanoMenu::closeWifiScreen() {
    // Stop background scan thread if still running.
    if (mWifiScanThread.joinable()) {
        mWifiScanInProgress = false;
        // Don't join here — thread is short-lived. Detach.
        mWifiScanThread.detach();
    }
    mMenuState = MENU_MAIN;
    mDisplayDirty = true;
}

void NanoMenu::refreshWifiList() {
    std::string savedText = runShellout("cmd wifi list-networks 2>/dev/null");
    std::string scanText = runShellout("cmd wifi list-scan-results 2>/dev/null");
    std::string statusText = runShellout("cmd wifi status 2>/dev/null");
    auto saved = parseSavedNetworks(savedText);
    auto scanned = parseScanResults(scanText);
    std::string connSsid = connectedSsidFromStatus(statusText);
    auto merged = mergeWifiLists(std::move(saved), std::move(scanned), connSsid);
    {
        std::lock_guard<std::mutex> lk(mWifiListMutex);
        mWifiEntries.swap(merged);
        mWifiListDirty = true;
    }
    if (mWifiEntrySelected >= (int)mWifiEntries.size()) {
        mWifiEntrySelected = mWifiEntries.empty() ? 0
                           : (int)mWifiEntries.size() - 1;
    }
    if (mWifiEntrySelected < 0) mWifiEntrySelected = 0;
}

void NanoMenu::wifiScanThreadFunc() {
    // Kick a fresh scan on the wifi radio and wait briefly for results
    // to land before re-reading list-scan-results.
    (void)runShellout("cmd wifi start-scan 2>/dev/null");
    // 3 second sleep so the radio has time to produce a fresh scan.
    for (int i = 0; i < 30 && mWifiScanInProgress; i++) {
        usleep(100 * 1000);
    }
    if (mWifiScanInProgress) refreshWifiList();
    mWifiScanInProgress = false;
    mWifiLastScanMs = std::chrono::duration_cast<std::chrono::milliseconds>(
            std::chrono::steady_clock::now().time_since_epoch()).count();
}

void NanoMenu::startWifiScanAsync() {
    if (mWifiScanInProgress) return;
    if (mWifiScanThread.joinable()) mWifiScanThread.detach();
    mWifiScanInProgress = true;
    mWifiStatusMsg = "Scanning...";
    mWifiStatusMsgUntilMs = std::chrono::duration_cast<std::chrono::milliseconds>(
            std::chrono::steady_clock::now().time_since_epoch()).count() + 4000;
    mWifiScanThread = std::thread([this]() { wifiScanThreadFunc(); });
}

void NanoMenu::connectToSavedWifi(int savedNetId) {
    char cmd[128];
    snprintf(cmd, sizeof(cmd),
             "cmd wifi connect-network %d saved 2>&1", savedNetId);
    std::string result = runShellout(cmd);
    ALOGI("connectToSavedWifi id=%d result=%s", savedNetId, result.c_str());
    mWifiStatusMsg = "Connecting...";
    mWifiStatusMsgUntilMs = std::chrono::duration_cast<std::chrono::milliseconds>(
            std::chrono::steady_clock::now().time_since_epoch()).count() + 6000;
    // Re-read status shortly via a scan re-run; the poll thread will also
    // pick up the new connected state for the HUD.
    startWifiScanAsync();
}

void NanoMenu::addAndConnectWifi(const std::string& ssid, int security,
                                 const std::string& password) {
    // cmd wifi connect-network SSID <security> <password>
    // Accepts: open, owe, wpa2, wpa3 per WifiShellCommand#connectNetwork.
    const char* secTok = "open";
    bool needsPass = true;
    switch (security) {
    case 0: secTok = "open"; needsPass = false; break;
    case 1: secTok = "wpa2"; break; // WEP not supported by the CLI; approximate with wpa2
    case 2: secTok = "wpa2"; break;
    case 3: secTok = "wpa3"; break;
    case 4: secTok = "owe";  needsPass = false; break;
    }
    std::string cmd = "cmd wifi connect-network ";
    // Quote SSID for spaces.
    cmd += "\"";
    for (char c : ssid) {
        if (c == '"' || c == '\\') cmd += '\\';
        cmd += c;
    }
    cmd += "\" ";
    cmd += secTok;
    if (needsPass) {
        cmd += " \"";
        for (char c : password) {
            if (c == '"' || c == '\\' || c == '$' || c == '`') cmd += '\\';
            cmd += c;
        }
        cmd += "\"";
    }
    cmd += " 2>&1";
    std::string result = runShellout(cmd.c_str());
    ALOGI("addAndConnectWifi ssid=%s result=%s", ssid.c_str(), result.c_str());
    mWifiStatusMsg = "Connecting...";
    mWifiStatusMsgUntilMs = std::chrono::duration_cast<std::chrono::milliseconds>(
            std::chrono::steady_clock::now().time_since_epoch()).count() + 8000;
    startWifiScanAsync();
}

void NanoMenu::forgetWifiNetwork(int savedNetId) {
    char cmd[128];
    snprintf(cmd, sizeof(cmd),
             "cmd wifi forget-network %d 2>&1", savedNetId);
    (void)runShellout(cmd);
    refreshWifiList();
}

void NanoMenu::toggleWifiRadio(bool on) {
    std::string cmd = "cmd wifi set-wifi-enabled ";
    cmd += (on ? "enabled" : "disabled");
    cmd += " 2>&1";
    (void)runShellout(cmd.c_str());
    mWifiStatusMsg = on ? "Enabling Wi-Fi..." : "Disabling Wi-Fi...";
    mWifiStatusMsgUntilMs = std::chrono::duration_cast<std::chrono::milliseconds>(
            std::chrono::steady_clock::now().time_since_epoch()).count() + 2000;
    if (on) startWifiScanAsync();
}

void NanoMenu::handleWifiScreenUp() {
    if (mWifiEntries.empty()) return;
    if (mWifiEntrySelected > 0) mWifiEntrySelected--;
    mDisplayDirty = true;
}

void NanoMenu::handleWifiScreenDown() {
    if (mWifiEntries.empty()) return;
    if (mWifiEntrySelected + 1 < (int)mWifiEntries.size()) mWifiEntrySelected++;
    mDisplayDirty = true;
}

void NanoMenu::handleWifiScreenX() {
    startWifiScanAsync();
}

void NanoMenu::handleWifiScreenSelect() {
    WifiNetEntry e;
    {
        std::lock_guard<std::mutex> lk(mWifiListMutex);
        if (mWifiEntries.empty()) return;
        if (mWifiEntrySelected < 0
                || mWifiEntrySelected >= (int)mWifiEntries.size()) return;
        e = mWifiEntries[mWifiEntrySelected];
    }
    if (e.connected) {
        // Already connected -- no-op (could offer disconnect later).
        mWifiStatusMsg = "Already connected";
        mWifiStatusMsgUntilMs = std::chrono::duration_cast<std::chrono::milliseconds>(
                std::chrono::steady_clock::now().time_since_epoch()).count() + 2000;
        return;
    }
    if (e.savedNetId >= 0) {
        connectToSavedWifi(e.savedNetId);
        return;
    }
    if (e.security == 0 || e.security == 4) {
        // Open / OWE net: connect directly.
        addAndConnectWifi(e.ssid, e.security, "");
        return;
    }
    // Secure unsaved net: prompt for password.
    mWifiPendingSsid = e.ssid;
    mWifiPendingSecurity = e.security;
    std::string prompt = "Wi-Fi password for \"" + e.ssid + "\"";
    openOskForPassword(prompt,
        [this](const std::string& pw) {
            if (pw.empty()) {
                mWifiStatusMsg = "Cancelled";
                mWifiStatusMsgUntilMs = std::chrono::duration_cast<std::chrono::milliseconds>(
                        std::chrono::steady_clock::now().time_since_epoch()).count() + 2000;
                return;
            }
            addAndConnectWifi(mWifiPendingSsid, mWifiPendingSecurity, pw);
        });
}

// ---------------------------------------------------------------------------
// Bluetooth screen
// ---------------------------------------------------------------------------

void NanoMenu::openBtScreen() {
    mMenuState = MENU_BT;
    mBtEntrySelected = 0;
    mBtScrollTop = 0;
    mDisplayDirty = true;
    refreshBtList();
    startBtScanAsync();
}

void NanoMenu::closeBtScreen() {
    if (mBtScanThread.joinable()) {
        mBtScanInProgress = false;
        mBtScanThread.detach();
    }
    mMenuState = MENU_MAIN;
    mDisplayDirty = true;
}

void NanoMenu::refreshBtList() {
    std::string bonded = runShellout(
            "cmd bluetooth_manager list-bonded-devices 2>/dev/null");
    std::string dump = runShellout("dumpsys bluetooth_manager 2>/dev/null");
    auto devs = parseBondedDevices(bonded);
    markConnectedBt(dump, &devs);
    {
        std::lock_guard<std::mutex> lk(mBtListMutex);
        mBtEntries.swap(devs);
        mBtListDirty = true;
    }
    if (mBtEntrySelected >= (int)mBtEntries.size()) {
        mBtEntrySelected = mBtEntries.empty() ? 0
                         : (int)mBtEntries.size() - 1;
    }
    if (mBtEntrySelected < 0) mBtEntrySelected = 0;
}

void NanoMenu::btScanThreadFunc() {
    // No AOSP CLI for ACL scan start (cmd bluetooth_manager has no
    // discovery subcommand). We rely on the on-radio state and just
    // refresh the bonded / connected views periodically. If we need
    // true scan-and-pair later we will add a helper app to invoke
    // BluetoothAdapter.startDiscovery via a system-signed intent.
    for (int i = 0; i < 8 && mBtScanInProgress; i++) {
        usleep(250 * 1000);
        if (!mBtScanInProgress) break;
    }
    if (mBtScanInProgress) refreshBtList();
    mBtScanInProgress = false;
    mBtLastScanMs = std::chrono::duration_cast<std::chrono::milliseconds>(
            std::chrono::steady_clock::now().time_since_epoch()).count();
}

void NanoMenu::startBtScanAsync() {
    if (mBtScanInProgress) return;
    if (mBtScanThread.joinable()) mBtScanThread.detach();
    mBtScanInProgress = true;
    mBtStatusMsg = "Refreshing...";
    mBtStatusMsgUntilMs = std::chrono::duration_cast<std::chrono::milliseconds>(
            std::chrono::steady_clock::now().time_since_epoch()).count() + 2000;
    mBtScanThread = std::thread([this]() { btScanThreadFunc(); });
}

void NanoMenu::toggleBtRadio(bool on) {
    std::string cmd = "cmd bluetooth_manager ";
    cmd += (on ? "enable" : "disable");
    cmd += " 2>&1";
    (void)runShellout(cmd.c_str());
    mBtStatusMsg = on ? "Enabling Bluetooth..." : "Disabling Bluetooth...";
    mBtStatusMsgUntilMs = std::chrono::duration_cast<std::chrono::milliseconds>(
            std::chrono::steady_clock::now().time_since_epoch()).count() + 2000;
    if (on) startBtScanAsync();
}

void NanoMenu::pairBtDevice(const std::string& /*mac*/) {
    // AOSP doesn't expose a shell command to initiate pairing for an
    // unbonded device. For the XMB flow, users pair devices outside
    // nano (from stock Settings) and then come back -- the Settings ->
    // Bluetooth screen lists bonded devices and can toggle their
    // connected state.
    mBtStatusMsg = "Use Android Settings to pair new devices";
    mBtStatusMsgUntilMs = std::chrono::duration_cast<std::chrono::milliseconds>(
            std::chrono::steady_clock::now().time_since_epoch()).count() + 3500;
}

void NanoMenu::unpairBtDevice(const std::string& /*mac*/) {
    // No standalone CLI either; fall through to the same guidance.
    pairBtDevice("");
}

void NanoMenu::connectBtDevice(const std::string& mac) {
    // `cmd bluetooth_manager connect-profile <profile> <mac>` exists on
    // recent AOSP, but profile ids vary by HIDL version. Invoke a
    // profile-agnostic reconnect by toggling bonded-connect via
    // "cmd bluetooth_manager factory-reset"? No -- that wipes bonds.
    //
    // Practical compromise for now: issue a generic connect via
    // `cmd bluetooth_manager connect <mac>` (present on A13+) and
    // fall back to a disable+enable cycle if that subcommand is
    // missing. The radio auto-reconnects cached bonded audio/input
    // devices on re-enable.
    std::string cmd = "cmd bluetooth_manager connect ";
    cmd += mac;
    cmd += " 2>&1";
    std::string out = runShellout(cmd.c_str());
    if (out.find("Unknown command") != std::string::npos
            || out.find("usage") != std::string::npos) {
        (void)runShellout("cmd bluetooth_manager disable 2>&1");
        usleep(400 * 1000);
        (void)runShellout("cmd bluetooth_manager enable 2>&1");
    }
    mBtStatusMsg = "Reconnecting...";
    mBtStatusMsgUntilMs = std::chrono::duration_cast<std::chrono::milliseconds>(
            std::chrono::steady_clock::now().time_since_epoch()).count() + 3000;
    startBtScanAsync();
}

void NanoMenu::handleBtScreenUp() {
    if (mBtEntries.empty()) return;
    if (mBtEntrySelected > 0) mBtEntrySelected--;
    mDisplayDirty = true;
}

void NanoMenu::handleBtScreenDown() {
    if (mBtEntries.empty()) return;
    if (mBtEntrySelected + 1 < (int)mBtEntries.size()) mBtEntrySelected++;
    mDisplayDirty = true;
}

void NanoMenu::handleBtScreenX() {
    startBtScanAsync();
}

void NanoMenu::handleBtScreenSelect() {
    BtDevEntry d;
    {
        std::lock_guard<std::mutex> lk(mBtListMutex);
        if (mBtEntries.empty()) return;
        if (mBtEntrySelected < 0
                || mBtEntrySelected >= (int)mBtEntries.size()) return;
        d = mBtEntries[mBtEntrySelected];
    }
    connectBtDevice(d.address);
}

// ---------------------------------------------------------------------------
// OSK password prompt
// ---------------------------------------------------------------------------

void NanoMenu::openOskForPassword(const std::string& prompt,
                                  std::function<void(const std::string&)> onSubmit) {
    mOskPasswordMode = true;
    mOskPasswordPrompt = prompt;
    mOskPasswordCallback = std::move(onSubmit);
    mOskQuery.clear();
    mOskCursorX = 0;
    mOskCursorY = 0;
    mOskActive = true;
    mDisplayDirty = true;
}

std::string NanoMenu::maskPassword(const std::string& s) {
    std::string out;
    out.resize(s.size(), '*');
    return out;
}

// ---------------------------------------------------------------------------
// Rendering: Settings vertical list (called from renderXmb when Settings
// column is selected)
// ---------------------------------------------------------------------------

void NanoMenu::renderSettingsList(float selIconX, float iconBarY,
                                  float iconSpacingV,
                                  float iconSize, float sf,
                                  float textScale, float selTextScale) {
    (void)selIconX; (void)iconSize;
    int numItems = (int)mSettingsItems.size();
    if (numItems == 0) return;

    float catActiveZoom = 1.0f;
    float selCatSz = iconSize * catActiveZoom;
    float itemListTop = iconBarY + selCatSz / 2.0f + FONT_CHAR_H * 1.8f * sf + 140.0f * sf;
    float itemIconBaseX = selIconX + (iconSize * catActiveZoom) / 2.0f;
    float contentRight = mWidth * 0.93f;
    (void)contentRight;

    for (int i = 0; i < numItems; i++) {
        float fy = itemListTop + (float)(i - mSettingsSelectedIndex) * iconSpacingV;
        bool isSel = (i == mSettingsSelectedIndex);
        float iAlpha = isSel ? 1.0f : 0.55f;
        float tSc = isSel ? selTextScale : textScale;

        float itemIconSz = isSel ? 50.0f * sf : 30.0f * sf;
        float iconX = itemIconBaseX - itemIconSz / 2.0f;
        float iconY = fy - itemIconSz / 2.0f;
        drawIcon(16, iconX, iconY, itemIconSz,
                 isSel ? 0.85f : 0.45f,
                 isSel ? 0.85f : 0.45f,
                 isSel ? 0.85f : 0.45f, iAlpha);
        float tx = iconX + itemIconSz + 10.0f * sf;
        float ty = fy - FONT_CHAR_H * tSc * 0.4f;
        float tr = isSel ? 1.0f : 0.6f;
        float tg = isSel ? 1.0f : 0.6f;
        float tb = isSel ? 1.0f : 0.6f;
        drawText(mSettingsItems[i].label.c_str(), tx, ty, tSc,
                 tr, tg, tb, iAlpha);
    }
}

// ---------------------------------------------------------------------------
// Rendering: Wi-Fi screen (full-screen modal)
// ---------------------------------------------------------------------------

void NanoMenu::renderWifiScreen() {
    float sf = fminf((float)mWidth / 1080.0f, (float)mHeight / 720.0f);
    if (sf < 0.5f) sf = 0.5f;

    // Dim background
    drawQuad(0, 0, mWidth, mHeight, 0.0f, 0.0f, 0.0f, 0.65f);

    float pad = 20.0f * sf;
    float titleScale = 2.6f * sf;
    float rowScale = 1.9f * sf;
    float footScale = 1.3f * sf;

    const char* title = "Wi-Fi";
    drawText(title, pad, pad, titleScale, 0.95f, 0.95f, 1.0f, 1.0f);

    std::vector<WifiNetEntry> entries;
    {
        std::lock_guard<std::mutex> lk(mWifiListMutex);
        entries = mWifiEntries;
    }

    // Status message (scan in progress, connecting, etc.)
    int64_t nowMs = std::chrono::duration_cast<std::chrono::milliseconds>(
            std::chrono::steady_clock::now().time_since_epoch()).count();
    float statusY = pad + FONT_CHAR_H * titleScale + 4.0f * sf;
    if (!mWifiStatusMsg.empty() && nowMs < mWifiStatusMsgUntilMs) {
        drawText(mWifiStatusMsg.c_str(), pad, statusY,
                 footScale, 0.80f, 0.75f, 0.15f, 0.95f);
    } else if (mWifiScanInProgress) {
        drawText("Scanning...", pad, statusY,
                 footScale, 0.80f, 0.75f, 0.15f, 0.95f);
    }

    float listTop = statusY + FONT_CHAR_H * footScale + 8.0f * sf;
    float rowH = FONT_CHAR_H * rowScale + 8.0f * sf;
    int visibleRows = (int)((mHeight - listTop - 60.0f * sf) / rowH);
    if (visibleRows < 4) visibleRows = 4;

    if (entries.empty()) {
        drawText("No Wi-Fi networks. Press X to rescan.",
                 pad, listTop, rowScale, 0.6f, 0.6f, 0.65f, 0.85f);
    } else {
        // Ensure selection is visible.
        if (mWifiEntrySelected < mWifiScrollTop) mWifiScrollTop = mWifiEntrySelected;
        if (mWifiEntrySelected >= mWifiScrollTop + visibleRows)
            mWifiScrollTop = mWifiEntrySelected - visibleRows + 1;
        if (mWifiScrollTop < 0) mWifiScrollTop = 0;

        int end = mWifiScrollTop + visibleRows;
        if (end > (int)entries.size()) end = (int)entries.size();

        for (int i = mWifiScrollTop; i < end; i++) {
            const auto& e = entries[i];
            float y = listTop + (i - mWifiScrollTop) * rowH;
            bool sel = (i == mWifiEntrySelected);

            if (sel) {
                drawQuad(pad - 6.0f * sf, y - 4.0f * sf,
                         mWidth - pad * 2.0f + 12.0f * sf,
                         rowH, 0.15f, 0.35f, 0.70f, 0.65f);
            }

            // Signal bars (left side)
            int bars = rssiToBars(e.rssi);
            if (e.rssi == -127 && e.savedNetId >= 0) bars = 0; // saved but not in range
            drawWifiIcon(pad + 4.0f * sf, y + rowH * 0.22f, sf * 1.1f, bars,
                         sel ? 0.95f : 0.85f,
                         sel ? 0.95f : 0.85f,
                         sel ? 1.0f  : 0.9f,
                         sel ? 1.0f  : 0.85f);

            // SSID
            float textX = pad + 40.0f * sf;
            float textY = y + rowH * 0.15f;
            float tr = sel ? 1.0f : 0.90f;
            float tg = sel ? 1.0f : 0.90f;
            float tb = sel ? 1.0f : 0.92f;
            drawText(e.ssid.c_str(), textX, textY, rowScale, tr, tg, tb, 1.0f);

            // Security + connected markers (right side)
            const char* secLabel = "Open";
            switch (e.security) {
            case 1: secLabel = "WEP"; break;
            case 2: secLabel = "WPA2"; break;
            case 3: secLabel = "WPA3"; break;
            case 4: secLabel = "OWE"; break;
            }
            char marker[64];
            const char* status = "";
            if (e.connected) status = " (Connected)";
            else if (e.savedNetId >= 0) status = " (Saved)";
            snprintf(marker, sizeof(marker), "%s%s", secLabel, status);
            float mScale = rowScale * 0.7f;
            float mw = measureText(marker, mScale);
            drawText(marker, mWidth - mw - pad, y + rowH * 0.25f,
                     mScale,
                     e.connected ? 0.4f : 0.6f,
                     e.connected ? 0.95f : 0.6f,
                     e.connected ? 0.4f : 0.65f, 0.95f);
        }
    }

    // Footer
    const char* footer = "A: Connect | X: Rescan | B: Back";
    float fw = measureText(footer, footScale);
    drawText(footer, (mWidth - fw) / 2.0f,
             mHeight - FONT_CHAR_H * footScale - 12.0f * sf,
             footScale, 0.60f, 0.60f, 0.65f, 0.90f);

    // Password OSK overlay
    if (mOskActive && mOskPasswordMode) {
        renderPasswordPromptOverlay();
    }
}

// ---------------------------------------------------------------------------
// Rendering: Bluetooth screen
// ---------------------------------------------------------------------------

void NanoMenu::renderBtScreen() {
    float sf = fminf((float)mWidth / 1080.0f, (float)mHeight / 720.0f);
    if (sf < 0.5f) sf = 0.5f;

    drawQuad(0, 0, mWidth, mHeight, 0.0f, 0.0f, 0.0f, 0.65f);

    float pad = 20.0f * sf;
    float titleScale = 2.6f * sf;
    float rowScale = 1.9f * sf;
    float footScale = 1.3f * sf;

    drawText("Bluetooth", pad, pad, titleScale, 0.95f, 0.95f, 1.0f, 1.0f);

    std::vector<BtDevEntry> devs;
    {
        std::lock_guard<std::mutex> lk(mBtListMutex);
        devs = mBtEntries;
    }

    int64_t nowMs = std::chrono::duration_cast<std::chrono::milliseconds>(
            std::chrono::steady_clock::now().time_since_epoch()).count();
    float statusY = pad + FONT_CHAR_H * titleScale + 4.0f * sf;
    if (!mBtStatusMsg.empty() && nowMs < mBtStatusMsgUntilMs) {
        drawText(mBtStatusMsg.c_str(), pad, statusY,
                 footScale, 0.80f, 0.75f, 0.15f, 0.95f);
    } else if (mBtScanInProgress) {
        drawText("Refreshing...", pad, statusY,
                 footScale, 0.80f, 0.75f, 0.15f, 0.95f);
    }

    float listTop = statusY + FONT_CHAR_H * footScale + 8.0f * sf;
    float rowH = FONT_CHAR_H * rowScale + 8.0f * sf;
    int visibleRows = (int)((mHeight - listTop - 60.0f * sf) / rowH);
    if (visibleRows < 4) visibleRows = 4;

    if (devs.empty()) {
        drawText("No paired devices.  Pair via Android Settings first, then return here.",
                 pad, listTop, rowScale * 0.85f, 0.6f, 0.6f, 0.65f, 0.85f);
    } else {
        if (mBtEntrySelected < mBtScrollTop) mBtScrollTop = mBtEntrySelected;
        if (mBtEntrySelected >= mBtScrollTop + visibleRows)
            mBtScrollTop = mBtEntrySelected - visibleRows + 1;
        if (mBtScrollTop < 0) mBtScrollTop = 0;

        int end = mBtScrollTop + visibleRows;
        if (end > (int)devs.size()) end = (int)devs.size();

        for (int i = mBtScrollTop; i < end; i++) {
            const auto& d = devs[i];
            float y = listTop + (i - mBtScrollTop) * rowH;
            bool sel = (i == mBtEntrySelected);
            if (sel) {
                drawQuad(pad - 6.0f * sf, y - 4.0f * sf,
                         mWidth - pad * 2.0f + 12.0f * sf,
                         rowH, 0.15f, 0.35f, 0.70f, 0.65f);
            }
            drawBtIcon(pad + 4.0f * sf, y + rowH * 0.22f, sf * 1.05f,
                       sel ? 0.55f : 0.45f,
                       sel ? 0.80f : 0.70f,
                       sel ? 1.0f  : 0.90f,
                       sel ? 1.0f  : 0.85f);
            float textX = pad + 40.0f * sf;
            float textY = y + rowH * 0.15f;
            drawText(d.name.c_str(), textX, textY, rowScale,
                     sel ? 1.0f : 0.88f,
                     sel ? 1.0f : 0.88f,
                     sel ? 1.0f : 0.90f, 1.0f);
            const char* status = d.connected ? "Connected"
                               : (d.bonded ? "Paired" : "");
            if (*status) {
                float sScale = rowScale * 0.7f;
                float sw = measureText(status, sScale);
                drawText(status, mWidth - sw - pad, y + rowH * 0.25f,
                         sScale,
                         d.connected ? 0.4f : 0.6f,
                         d.connected ? 0.95f : 0.6f,
                         d.connected ? 0.4f : 0.65f, 0.95f);
            }
        }
    }

    const char* footer = "A: Connect | X: Refresh | B: Back";
    float fw = measureText(footer, footScale);
    drawText(footer, (mWidth - fw) / 2.0f,
             mHeight - FONT_CHAR_H * footScale - 12.0f * sf,
             footScale, 0.60f, 0.60f, 0.65f, 0.90f);
}

// ---------------------------------------------------------------------------
// Password prompt overlay (renders above the OSK)
// ---------------------------------------------------------------------------

void NanoMenu::renderPasswordPromptOverlay() {
    if (!mOskActive || !mOskPasswordMode) return;
    float sf = fminf((float)mWidth / 1080.0f, (float)mHeight / 720.0f);
    if (sf < 0.5f) sf = 0.5f;

    // Translucent band across top for the prompt + masked chars.
    float bandH = 80.0f * sf;
    drawQuad(0, 0, mWidth, bandH, 0.05f, 0.05f, 0.1f, 0.90f);

    float pad = 20.0f * sf;
    float titleScale = 1.8f * sf;
    float valueScale = 2.4f * sf;

    drawText(mOskPasswordPrompt.c_str(), pad, pad * 0.5f,
             titleScale, 0.90f, 0.90f, 1.0f, 1.0f);

    std::string masked = maskPassword(mOskQuery);
    if (masked.empty()) masked = "_";
    drawText(masked.c_str(), pad, pad * 0.5f + FONT_CHAR_H * titleScale + 4.0f * sf,
             valueScale, 1.0f, 1.0f, 0.5f, 1.0f);
}

} // namespace android
