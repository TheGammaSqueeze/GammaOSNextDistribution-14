/*
 * Copyright (C) 2026 GammaOS
 *
 * Settings column: pseudo-system on the XMB column bar that hosts
 * Wi-Fi + Bluetooth configuration screens.
 *
 * gammaos-nano runs as root (see gammaos-nano.rc) so `cmd wifi` /
 * `cmd bluetooth_manager` / `dumpsys bluetooth_manager` accept the
 * caller uid check directly. All network calls below go through
 * runCmd() which is a thin popen() wrapper -- no shell proxy, no
 * init property triggers, no result files.
 *
 * Blocking calls (list-scan-results, dumpsys) must NOT run on the
 * render / input thread -- they can stall for hundreds of ms. The
 * screen-open paths push the initial refresh onto the existing
 * wifi/bt scan threads so the UI never freezes when opening
 * Settings -> Wi-Fi or Settings -> Bluetooth.
 */

#define LOG_TAG "GammaOSNano"

#include <algorithm>
#include <cctype>
#include <cerrno>
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

#include <log/log.h>

#include <GLES2/gl2.h>

#include "NanoMenu.h"
#include "NanoMenuShaders.h"

namespace android {

namespace {

// Serialize framework shell-outs so the HUD poll thread and the
// XMB Settings threads don't pile up concurrent `cmd wifi` forks
// (each one briefly holds a WifiService binder reply slot).
std::mutex& netHelperMutex() {
    static std::mutex m;
    return m;
}

// Thin popen() wrapper. gammaos-nano runs as root, so `cmd wifi` /
// `cmd bluetooth_manager` / `dumpsys bluetooth_manager` pass their
// Binder.getCallingUid() == ROOT_UID check and return real data.
// Redirects stderr into stdout so the few tools that log warnings
// on stderr (cmd wifi connect-network when the SSID is already
// saved) don't bleed onto the render thread's stderr fd.
std::string runCmd(const std::string& cmdline) {
    std::lock_guard<std::mutex> lk(netHelperMutex());
    std::string result;
    result.reserve(4096);
    std::string full = cmdline;
    if (full.find("2>") == std::string::npos) full += " 2>&1";
    FILE* f = popen(full.c_str(), "r");
    if (!f) {
        ALOGE("NanoMenu runCmd popen failed for '%s': %s",
              full.c_str(), strerror(errno));
        return result;
    }
    char buf[4096];
    const size_t kMax = 256 * 1024;
    while (true) {
        size_t n = fread(buf, 1, sizeof(buf), f);
        if (n == 0) break;
        result.append(buf, n);
        if (result.size() > kMax) break;
    }
    (void)pclose(f);
    return result;
}

// POSIX single-quote wrap with escape for embedded single quotes.
// Used for SSID / password values that may contain shell metacharacters.
std::string shellQuote(const std::string& s) {
    std::string out;
    out.reserve(s.size() + 2);
    out += '\'';
    for (char c : s) {
        if (c == '\'') out += "'\\''";
        else out += c;
    }
    out += '\'';
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

// Merge saved + scan results, dedup by SSID.
//
// `cmd wifi list-networks` emits one line per auth type a saved profile
// supports, so a single saved network that advertises WPA2-PSK and
// WPA3-SAE transition mode appears twice. Scan results are per-BSSID,
// so a mesh / multi-AP network shows up once per radio. Both need to
// collapse down to one entry per SSID before rendering, otherwise the
// list is full of apparent duplicates.
std::vector<NanoMenu::WifiNetEntry> mergeWifiLists(
        std::vector<NanoMenu::WifiNetEntry> saved,
        std::vector<NanoMenu::WifiNetEntry> scanned,
        const std::string& connectedSsid) {
    // Dedup saved by SSID: keep the lowest network id (that's what
    // connect-saved will target) and upgrade the security tier if a
    // later entry advertises a stronger one (WPA3 > WPA2 > WEP).
    std::vector<NanoMenu::WifiNetEntry> savedDedup;
    for (auto& v : saved) {
        bool merged = false;
        for (auto& keep : savedDedup) {
            if (keep.ssid == v.ssid) {
                if (v.savedNetId >= 0
                        && (keep.savedNetId < 0 || v.savedNetId < keep.savedNetId)) {
                    keep.savedNetId = v.savedNetId;
                }
                if (v.security > keep.security) keep.security = v.security;
                merged = true;
                break;
            }
        }
        if (!merged) savedDedup.push_back(std::move(v));
    }

    // Dedup scanned by SSID: keep the strongest RSSI and prefer the
    // strongest security hint across the APs.
    std::vector<NanoMenu::WifiNetEntry> scannedDedup;
    for (auto& s : scanned) {
        if (s.ssid.empty()) continue;
        bool merged = false;
        for (auto& keep : scannedDedup) {
            if (keep.ssid == s.ssid) {
                if (s.rssi > keep.rssi) {
                    keep.rssi = s.rssi;
                    keep.bssid = s.bssid;
                }
                if (s.security > keep.security) keep.security = s.security;
                merged = true;
                break;
            }
        }
        if (!merged) scannedDedup.push_back(std::move(s));
    }

    // Cross-annotate: copy saved netId onto the in-range scan entry and mark
    // connected state. Keep the SCAN security (the AP's actually-advertised
    // capability) - do NOT upgrade it from the saved profile. A saved WPA2/WPA3
    // transition profile reports wpa3-sae even for a WPA2-only AP, and connecting
    // such an AP with the SAE token then fails to associate (the AP has no SAE).
    for (auto& s : scannedDedup) {
        for (auto& v : savedDedup) {
            if (v.ssid == s.ssid) {
                s.savedNetId = v.savedNetId;
                break;
            }
        }
        if (!connectedSsid.empty() && s.ssid == connectedSsid) s.connected = true;
    }
    // Append saved networks that weren't in scan results (shown as greyed).
    for (auto& v : savedDedup) {
        bool found = false;
        for (auto& s : scannedDedup) {
            if (s.ssid == v.ssid) { found = true; break; }
        }
        if (!found) {
            if (!connectedSsid.empty() && v.ssid == connectedSsid)
                v.connected = true;
            scannedDedup.push_back(std::move(v));
        }
    }
    // Sort: connected first, then saved, then by rssi desc.
    std::sort(scannedDedup.begin(), scannedDedup.end(),
              [](const NanoMenu::WifiNetEntry& a,
                 const NanoMenu::WifiNetEntry& b) {
        if (a.connected != b.connected) return a.connected;
        bool aSaved = a.savedNetId >= 0, bSaved = b.savedNetId >= 0;
        if (aSaved != bSaved) return aSaved;
        return a.rssi > b.rssi;
    });
    return scannedDedup;
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

// Parse `gammaos-net bt list-bonded` output. TSV lines:
//    AA:BB:CC:DD:EE:FF<TAB>Device Name<TAB>ClassOfDevice<TAB>Connected
// The Connected column is "1" if the ACL link is live (reflected via
// BluetoothDevice.isConnected()), "0" otherwise. Older output without
// the 4th column is accepted for forward-compat; connected defaults
// to false in that case.
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
        if (line.size() < 17) continue;
        if (!isxdigit((unsigned char)line[0]) || !isxdigit((unsigned char)line[1]))
            continue;
        size_t t1 = line.find('\t');
        if (t1 == std::string::npos) continue;
        NanoMenu::BtDevEntry d{};
        d.address = line.substr(0, t1);
        size_t t2 = line.find('\t', t1 + 1);
        size_t t3 = (t2 == std::string::npos) ? std::string::npos
                                              : line.find('\t', t2 + 1);
        if (t2 == std::string::npos) {
            d.name = line.substr(t1 + 1);
        } else {
            d.name = line.substr(t1 + 1, t2 - t1 - 1);
        }
        if (d.name.empty()) d.name = d.address;
        d.bonded = true;
        d.connected = false;
        // 3rd column = class-of-device, 4th = connected.
        if (t2 != std::string::npos) {
            const char* cp = line.c_str() + t2 + 1;
            char* ce = nullptr; long c = strtol(cp, &ce, 10);
            if (ce != cp) d.cod = (int)c;
        }
        if (t3 != std::string::npos) {
            const char* startp = line.c_str() + t3 + 1;
            char* endp = nullptr;
            long v = strtol(startp, &endp, 10);
            d.connected = (endp != startp) && v == 1;
        }
        out.push_back(std::move(d));
    }
    return out;
}

// Parse `gammaos-net bt scan` output. TSV lines:
//    AA:BB:CC:DD:EE:FF<TAB>Name<TAB>RSSI<TAB>ClassOfDevice<TAB>BondState
// BondState is the android.bluetooth BOND_* int: 10=NONE, 11=BONDING, 12=BONDED.
std::vector<NanoMenu::BtDevEntry> parseBtScanResults(const std::string& text) {
    std::vector<NanoMenu::BtDevEntry> out;
    size_t p = 0;
    while (p < text.size()) {
        size_t eol = text.find('\n', p);
        if (eol == std::string::npos) eol = text.size();
        std::string line = text.substr(p, eol - p);
        p = eol + 1;
        while (!line.empty() && (line.back() == '\r' || line.back() == ' '))
            line.pop_back();
        if (line.size() < 17) continue;
        if (!isxdigit((unsigned char)line[0]) || !isxdigit((unsigned char)line[1]))
            continue;
        size_t t1 = line.find('\t');
        if (t1 == std::string::npos) continue;
        size_t t2 = line.find('\t', t1 + 1);
        size_t t3 = (t2 == std::string::npos) ? std::string::npos
                                              : line.find('\t', t2 + 1);
        size_t t4 = (t3 == std::string::npos) ? std::string::npos
                                              : line.find('\t', t3 + 1);
        NanoMenu::BtDevEntry d{};
        d.address = line.substr(0, t1);
        d.name = (t2 == std::string::npos) ? line.substr(t1 + 1)
                                           : line.substr(t1 + 1, t2 - t1 - 1);
        if (d.name.empty()) d.name = d.address;
        // 4th column = class-of-device (between t3 and t4).
        if (t3 != std::string::npos) {
            const char* cp = line.c_str() + t3 + 1;
            char* ce = nullptr; long c = strtol(cp, &ce, 10);
            if (ce != cp) d.cod = (int)c;
        }
        int bondState = 10; // BOND_NONE
        if (t4 != std::string::npos) {
            // -fno-exceptions in AOSP builds rules out std::stoi; strtol
            // silently returns 0 on a bad field, which we remap to BOND_NONE.
            const char* startp = line.c_str() + t4 + 1;
            char* endp = nullptr;
            long v = strtol(startp, &endp, 10);
            bondState = (endp != startp) ? static_cast<int>(v) : 10;
        }
        d.bonded = (bondState == 12); // BOND_BONDED
        d.connected = false;
        out.push_back(std::move(d));
    }
    return out;
}

} // anonymous namespace

// ---------------------------------------------------------------------------
// Settings column lifecycle
// ---------------------------------------------------------------------------

void NanoMenu::initSettingsItems() {
    mSettingsItems.clear();
    mSettingsItems.push_back({"Settings", 2});
    mSettingsSelectedIndex = 0;
    mSettingsTreeSelected = 0;
    mSettingsTreeScrollTop = 0;
    mSettingsEditNodeIdx = -1;
    mSettingsValuesDirty = false;
    buildSettingsTree();
}

// Settings is the leftmost XMB column, identified by sentinel index -2.
// Order is: -2 Settings | -1 Recently Played | 0..N-1 Systems.
bool NanoMenu::isOnSettingsColumn() const {
    return mXmbMode
        && mXmbSystemIndex == -2
        && !mSettingsItems.empty();
}

// ---------------------------------------------------------------------------
// Wi-Fi screen
// ---------------------------------------------------------------------------

void NanoMenu::openWifiScreen() {
    mMenuState = MENU_WIFI;
    mWifiEntrySelected = 0;
    mWifiScrollTop = 0;
    // Don't block here -- list-networks + list-scan-results + status can
    // take ~300 ms cold and would visibly freeze the XMB. The existing
    // scan thread does a refresh at the end of its cycle so we get a
    // populated list with a "Scanning..." status in the meantime.
    mWifiStatusMsg = "Loading...";
    mWifiStatusMsgUntilMs = std::chrono::duration_cast<std::chrono::milliseconds>(
            std::chrono::steady_clock::now().time_since_epoch()).count() + 4000;
    mDisplayDirty = true;
    startWifiScanAsync();
}

void NanoMenu::closeWifiScreen() {
    if (mWifiScanThread.joinable()) {
        mWifiScanInProgress = false;
        mWifiScanThread.detach();
    }
    if (!mSettingsNavStack.empty()) {
        mMenuState = MENU_SETTINGS;
    } else {
        mMenuState = MENU_MAIN;
    }
    mDisplayDirty = true;
}

void NanoMenu::refreshWifiList() {
    std::string statusText = runCmd("cmd wifi status");
    // Detect whether the Wi-Fi radio is currently on. When it is off,
    // list-networks / list-scan-results return empty; we still want
    // the toggle row visible so the user can flip it back on.
    bool radioOn = !statusText.empty()
                && statusText.find("Wifi is disabled") == std::string::npos
                && statusText.find("is disabled") == std::string::npos;
    std::vector<NanoMenu::WifiNetEntry> merged;
    // Prepend a synthetic toggle row. Sentinel: savedNetId == kWifiToggleSentinel,
    // bssid == "__TOGGLE__". renderWifiScreen / handleWifiScreenSelect
    // detect it via bssid.
    {
        NanoMenu::WifiNetEntry toggle{};
        toggle.ssid = radioOn ? "Wi-Fi: On" : "Wi-Fi: Off";
        toggle.bssid = "__TOGGLE__";
        toggle.rssi = -127;
        toggle.security = 0;
        toggle.savedNetId = radioOn ? -3 : -4; // both negative so it can't match a real id
        toggle.connected = false;
        merged.push_back(std::move(toggle));
    }
    if (radioOn) {
        std::string savedText = runCmd("cmd wifi list-networks");
        std::string scanText = runCmd("cmd wifi list-scan-results");
        auto saved = parseSavedNetworks(savedText);
        auto scanned = parseScanResults(scanText);
        std::string connSsid = connectedSsidFromStatus(statusText);
        auto body = mergeWifiLists(std::move(saved), std::move(scanned), connSsid);
        for (auto& e : body) merged.push_back(std::move(e));
    }
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
    // Force the render pipeline to pick up the fresh list. Without this
    // the Wi-Fi screen keeps drawing stale entries (old "(Connected)"
    // marker, missing toggle-row state flip, etc.) until the next input
    // event happens to set mDisplayDirty.
    mDisplayDirty = true;
}

void NanoMenu::wifiScanThreadFunc() {
    // Show the saved/last-known list immediately so the UI doesn't
    // look empty during the scan window.
    refreshWifiList();
    // Kick a fresh scan on the wifi radio and wait briefly for results
    // to land before re-reading list-scan-results.
    (void)runCmd("cmd wifi start-scan");
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
    // AOSP Android 14's `cmd wifi` has no "connect to saved network by
    // ID" subcommand. Kicking a scan and hoping auto-join picks the
    // right network doesn't actually switch between two saved networks
    // that are both in range -- the supplicant stays glued to whichever
    // one it's already associated with. We call our own helper which
    // drives WifiManager.connect(netId, ActionListener) directly, which
    // is what Settings' NetworkProviderSettings does.
    //
    // gammaos-net waits up to 35s for the actual SSID transition, so
    // dispatch it to a detached thread. Running it on the input thread
    // froze the UI until the switch completed, making it look like the
    // action had no effect. The status message sticks around for the
    // same 35s so it remains visible throughout the full
    // disconnect-scan-connect-maybe-retry cycle (cross-band switches
    // on congested 5 GHz need 20-25s end-to-end).
    mWifiStatusMsg = "Connecting...";
    mWifiStatusMsgUntilMs = std::chrono::duration_cast<std::chrono::milliseconds>(
            std::chrono::steady_clock::now().time_since_epoch()).count() + 35000;
    mDisplayDirty = true;
    std::thread([this, savedNetId]() {
        char cmd[96];
        snprintf(cmd, sizeof(cmd),
                 "gammaos-net wifi connect-saved %d", savedNetId);
        std::string result = runCmd(cmd);
        bool ok = result.find("OK") != std::string::npos;
        if (!ok) {
            ALOGW("connectToSavedWifi id=%d failed: %s",
                  savedNetId, result.c_str());
        }
        // Stop showing "Connecting..." the moment the helper returns.
        // Without this the HUD ran out the full 35s budget regardless
        // of how fast the real switch landed, so the UI looked frozen
        // long after the switch had already completed.
        if (ok) {
            mWifiStatusMsg.clear();
            mWifiStatusMsgUntilMs = 0;
        }
        // Rebuild the list immediately so the connected marker and
        // selection land before the follow-up scan fires. refreshWifiList
        // sets mDisplayDirty, so the frame updates on the next tick.
        refreshWifiList();
        // Kick a scan for fresh RSSI / new networks in the background.
        startWifiScanAsync();
    }).detach();
}

void NanoMenu::addAndConnectWifi(const std::string& ssid, int security,
                                 const std::string& password) {
    // cmd wifi connect-network SSID <security> <password>
    // Accepts: open, owe, wpa2, wpa3, wep per WifiShellCommand#connectNetwork.
    const char* secTok = "open";
    switch (security) {
    case 0: secTok = "open"; break;
    case 1: secTok = "wep";  break;
    case 2: secTok = "wpa2"; break;
    case 3: secTok = "wpa3"; break;
    case 4: secTok = "owe";  break;
    }
    std::string cmdline = "cmd wifi connect-network "
                        + shellQuote(ssid) + " " + secTok;
    if (security != 0 && security != 4) {
        cmdline += " " + shellQuote(password);
    }
    mWifiStatusMsg = "Connecting...";
    mWifiStatusMsgUntilMs = std::chrono::duration_cast<std::chrono::milliseconds>(
            std::chrono::steady_clock::now().time_since_epoch()).count() + 12000;
    mDisplayDirty = true;
    std::string ssidCapture = ssid;
    std::thread([this, cmdline, ssidCapture]() {
        // Don't tear down a working link: if we are already associated with this
        // SSID, skip the (disruptive) reconnect. connect-network re-creates the
        // profile and re-associates, which briefly drops an otherwise-good
        // connection - and the wizard runs the connectivity test right after.
        std::string st = runCmd("cmd wifi status 2>/dev/null");
        if (connectedSsidFromStatus(st) != ssidCapture) {
            (void)runCmd(cmdline);
        }
        startWifiScanAsync();
    }).detach();
}

// Build a `gammaos-net wifi configure` command from the wizard's collected
// advanced settings and run it. gammaos-net (system-signed) applies a static
// WifiManager IpConfiguration (IP/gateway/DNS), an HTTP ProxyInfo and the MTU -
// the things `cmd wifi connect-network` cannot express. Used by the Internet
// Connection wizard's Save step when the user chose any manual setting.
void NanoMenu::connectWithWizardSettings() {
    const char* secTok = "open";
    switch (mPs3WizSecTok) {
        case 1: secTok = "wep";  break;
        case 2: secTok = "wpa2"; break;
        case 3: secTok = "wpa3"; break;
        case 4: secTok = "owe";  break;
        default: secTok = "open"; break;
    }
    std::string cmd = "gammaos-net wifi configure "
                    + shellQuote(mPs3WizSsid) + " " + secTok;
    if (mPs3WizSecTok != 0 && mPs3WizSecTok != 4)
        cmd += " " + shellQuote(mPs3WizKey);
    if (mPs3WizIpMode == "Manual") {
        cmd += " --ip " + shellQuote(mPs3WizIpAddr);
        if (!mPs3WizSubnet.empty()) cmd += " --subnet " + shellQuote(mPs3WizSubnet);
        if (!mPs3WizRouter.empty()) cmd += " --gw " + shellQuote(mPs3WizRouter);
        if (!mPs3WizPdns.empty()) cmd += " --dns1 " + shellQuote(mPs3WizPdns);
        if (!mPs3WizSdns.empty()) cmd += " --dns2 " + shellQuote(mPs3WizSdns);
    } else if (mPs3WizDnsMode == "Manual" && !mPs3WizPdns.empty()) {
        cmd += " --dns1 " + shellQuote(mPs3WizPdns);
        if (!mPs3WizSdns.empty()) cmd += " --dns2 " + shellQuote(mPs3WizSdns);
        cmd += " --dns-only 1";   // DHCP address, manual DNS (re-pinned from lease)
    }
    if (mPs3WizProxyMode == "Use" && !mPs3WizProxyAddr.empty()) {
        cmd += " --proxy-host " + shellQuote(mPs3WizProxyAddr);
        if (!mPs3WizProxyPort.empty()) cmd += " --proxy-port " + shellQuote(mPs3WizProxyPort);
    }
    if (mPs3WizMtuMode == "Manual" && !mPs3WizMtu.empty())
        cmd += " --mtu " + shellQuote(mPs3WizMtu);
    mWifiStatusMsg = "Applying settings...";
    mWifiStatusMsgUntilMs = std::chrono::duration_cast<std::chrono::milliseconds>(
            std::chrono::steady_clock::now().time_since_epoch()).count() + 20000;
    mDisplayDirty = true;
    std::thread([this, cmd]() {
        (void)runCmd(cmd);
        startWifiScanAsync();
    }).detach();
}

void NanoMenu::forgetWifiNetwork(int savedNetId) {
    char cmd[128];
    snprintf(cmd, sizeof(cmd),
             "cmd wifi forget-network %d", savedNetId);
    (void)runCmd(cmd);
    // Refresh on the scan thread so we don't block the caller.
    startWifiScanAsync();
}

bool NanoMenu::wifiRadioEnabled() {
    std::string st = runCmd("cmd wifi status 2>/dev/null");
    // "Wifi is enabled" / "Wifi is connected to ..." vs "Wifi is disabled".
    if (st.find("Wifi is disabled") != std::string::npos) return false;
    return st.find("Wifi is enabled") != std::string::npos
        || st.find("is connected to") != std::string::npos;
}

void NanoMenu::toggleWifiRadio(bool on) {
    (void)runCmd(on ? "cmd wifi set-wifi-enabled enabled"
                    : "cmd wifi set-wifi-enabled disabled");
    { std::lock_guard<std::mutex> lk(mNetStateMutex); mWifiRadioOn = on; }
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
    if (e.bssid == "__TOGGLE__") {
        // savedNetId of -3 means the row currently shows "On" so we turn it off;
        // -4 means currently off so we turn it on.
        bool turningOn = (e.savedNetId == -4);
        toggleWifiRadio(turningOn);
        return;
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

void NanoMenu::handleWifiScreenY() {
    WifiNetEntry e;
    {
        std::lock_guard<std::mutex> lk(mWifiListMutex);
        if (mWifiEntries.empty()
                || mWifiEntrySelected < 0
                || mWifiEntrySelected >= (int)mWifiEntries.size()) return;
        e = mWifiEntries[mWifiEntrySelected];
    }
    int64_t nowMs = std::chrono::duration_cast<std::chrono::milliseconds>(
            std::chrono::steady_clock::now().time_since_epoch()).count();
    if (e.bssid == "__TOGGLE__") return;
    if (e.savedNetId >= 0) {
        // forget-network removes the saved config (and disconnects if it is the
        // currently associated network), then a rescan repopulates the list.
        forgetWifiNetwork(e.savedNetId);
        mWifiStatusMsg = e.connected ? "Disconnected and removed" : "Removed saved network";
    } else {
        mWifiStatusMsg = "Network is not saved";
    }
    mWifiStatusMsgUntilMs = nowMs + 2500;
    mDisplayDirty = true;
}

// ---------------------------------------------------------------------------
// Network Settings dialogs backed by the live system state
// ---------------------------------------------------------------------------

// Trim leading/trailing whitespace + newlines from shell output.
static std::string netTrim(std::string s) {
    while (!s.empty() && (s.back() == '\n' || s.back() == '\r'
                       || s.back() == ' ' || s.back() == '\t')) s.pop_back();
    size_t b = 0;
    while (b < s.size() && (s[b] == ' ' || s[b] == '\t')) b++;
    return s.substr(b);
}

// Parse the WIFI network's LinkProperties out of `dumpsys connectivity` for the
// default-route gateway and DNS servers. The legacy dhcp.wlan0.gateway / net.dns1
// system properties are unset on modern Android (DHCP results live in
// ConnectivityService's LinkProperties), and direct ip/ip route do not work in
// nano's domain - but dumpsys (binder) does.
static void netGwDnsFromDumpsys(std::string& gw, std::string& dns1, std::string& dns2) {
    std::string d = runCmd("dumpsys connectivity 2>/dev/null");
    size_t at = d.find("InterfaceName: wlan0");
    if (at == std::string::npos) return;
    size_t end = d.find("}  nc{", at);
    if (end == std::string::npos) end = d.find("NetworkAgentInfo{", at + 1);
    if (end == std::string::npos) end = d.size();
    std::string b = d.substr(at, end - at);
    auto splitCsv = [](const std::string& s) {
        std::vector<std::string> out; std::string cur;
        for (char c : s) { if (c == ',') { out.push_back(cur); cur.clear(); } else cur += c; }
        if (!cur.empty()) out.push_back(cur);
        return out;
    };
    // DnsAddresses: [ 8.8.8.8,8.8.4.4 ]   (comma-joined, each may have a leading '/')
    { size_t p = b.find("DnsAddresses: [");
      if (p != std::string::npos) { p += 15; size_t e = b.find(']', p);
        std::string inside = b.substr(p, (e == std::string::npos ? b.size() : e) - p);
        int idx = 0;
        for (auto& s : splitCsv(inside)) {
            std::string t = netTrim(s);
            if (!t.empty() && t[0] == '/') t = t.substr(1);
            if (t.empty()) continue;
            if (idx == 0) dns1 = t; else { dns2 = t; break; }
            idx++;
        }
      }
    }
    // Routes: [ 0.0.0.0/0 -> 192.168.0.1 wlan0 mtu 0, ... ]
    { size_t p = b.find("Routes: [");
      if (p != std::string::npos) { p += 9; size_t e = b.find(']', p);
        std::string inside = b.substr(p, (e == std::string::npos ? b.size() : e) - p);
        const std::string v4p = "0.0.0.0/0 -> ", v6p = "::/0 -> ";
        std::string v4gw, v6gw;
        for (auto& r : splitCsv(inside)) {
            std::string t = netTrim(r);
            if (t.rfind(v4p, 0) == 0) {
                std::string g = t.substr(v4p.size());
                g = g.substr(0, g.find(' '));
                if (g != "0.0.0.0" && !g.empty()) v4gw = g;
            } else if (t.rfind(v6p, 0) == 0) {
                std::string g = t.substr(v6p.size());
                g = g.substr(0, g.find(' '));
                if (g != "::" && !g.empty()) v6gw = g;
            }
        }
        gw = !v4gw.empty() ? v4gw : v6gw;
      }
    }
}

std::string NanoMenu::buildNetStatusBody() {
    std::string ssid; bool connected = false;
    {
        std::lock_guard<std::mutex> lk(mNetStateMutex);
        ssid = mWifiSsid;
        connected = (mWifiLevel == kWifiLevel_Connected);
    }
    // IP from the framework status (binder); gateway + DNS from the connectivity
    // dump's LinkProperties (the legacy dhcp.*/net.dns* props are unset). MAC via
    // sysfs. Direct ip/ip route do not work in nano (see startNetTest).
    std::string st = runCmd("cmd wifi status 2>/dev/null");
    std::string ip;
    { size_t p = st.find("IP: /");
      if (p != std::string::npos) { p += 5; size_t e = p;
        while (e < st.size() && (isdigit((unsigned char)st[e]) || st[e] == '.')) e++;
        ip = st.substr(p, e - p); if (ip == "0.0.0.0") ip.clear(); } }
    std::string gw, dns, dns2;
    if (connected) netGwDnsFromDumpsys(gw, dns, dns2);
    std::string mac = netTrim(runCmd("cat /sys/class/net/wlan0/address 2>/dev/null"));
    auto orDash = [](const std::string& s) { return s.empty() ? std::string("-") : s; };
    std::string body;
    body += "Connection Method  Wireless (Wi-Fi)\n";
    body += std::string("Connection Status  ") + (connected ? "Connected" : "Not connected") + "\n";
    body += std::string("SSID               ") + orDash(connected ? ssid : std::string("")) + "\n";
    body += std::string("IP Address         ") + orDash(ip) + "\n";
    body += std::string("Default Gateway    ") + orDash(gw) + "\n";
    body += std::string("Primary DNS        ") + orDash(dns) + "\n";
    body += std::string("Secondary DNS      ") + orDash(dns2) + "\n";
    body += std::string("MAC Address        ") + orDash(mac) + "\n";
    return body;
}

void NanoMenu::stopNetTest() {
    mPs3NetTestActive = false;
    if (mPs3NetTestThread.joinable()) mPs3NetTestThread.join();
}

void NanoMenu::startNetTest() {
    stopNetTest();
    {
        std::lock_guard<std::mutex> lk(mPs3NetTestMutex);
        mPs3NetTestBody = "Testing the Internet connection.\nPlease wait...\n";
    }
    mPs3NetTestActive = true;
    mPs3NetTestThread = std::thread([this]() {
        auto pub = [this](const std::string& s) {
            std::lock_guard<std::mutex> lk(mPs3NetTestMutex);
            mPs3NetTestBody = s;
        };
        auto alive = [this]() { return mPs3NetTestActive.load(); };
        const std::string L1 = "Obtain IP Address            ";
        const std::string L2 = "Internet Connection          ";
        const std::string L3 = "Name Resolution              ";
        // Sleep in 100ms chunks so stopNetTest() stays responsive.
        auto nap = [&](int ms) { for (int k = 0; k < ms / 100 && alive(); k++)
            std::this_thread::sleep_for(std::chrono::milliseconds(100)); };
        // Single source of truth that works in nano's restricted runtime: the
        // framework "cmd wifi status" (binder). Direct ip/ping do NOT work here -
        // ping needs CAP_NET_RAW (nano's init capabilities allowlist omits it) and
        // the RTNETLINK read for `ip` is unavailable to the bootanim domain, so
        // both return empty and every test row read "Failed" even when online.
        // `cmd wifi status` reports the DHCP IP and Android's own INTERNET /
        // VALIDATED capability (VALIDATED = the OS confirmed reachability + DNS).
        auto ipFromStatus = [](const std::string& st) -> std::string {
            size_t p = st.find("IP: /");
            if (p == std::string::npos) return "";
            p += 5; size_t e = p;
            while (e < st.size() && (isdigit((unsigned char)st[e]) || st[e] == '.')) e++;
            std::string v = st.substr(p, e - p);
            return (v == "0.0.0.0") ? std::string() : v;
        };
        // 1. Address from DHCP - poll up to ~12s (a fresh connect may settle).
        // WifiInfo carries the IPv4 address; on a dual-stack or IPv6-only network
        // the OS also assigns IPv6, which WifiInfo does not surface, so a VALIDATED
        // link (it has a working v4 OR v6 address) also counts as "address obtained".
        pub(L1 + "Testing...\n");
        std::string ip, st; bool validated = false;
        for (int t = 0; t < 24 && alive(); t++) {
            st = runCmd("cmd wifi status 2>/dev/null");
            ip = ipFromStatus(st);
            validated = st.find("VALIDATED") != std::string::npos;
            if (!ip.empty() || validated) break;
            nap(500);
        }
        bool haveIp = !ip.empty() || validated;
        if (!alive()) return;
        // 2. Internet reachability - poll for the framework's VALIDATED capability
        // (Android runs its own connectivity validation a few seconds after assoc).
        pub(L1 + (haveIp ? "Succeeded" : "Failed") + "\n" + L2 + "Testing...\n");
        bool inet = validated;
        for (int t = 0; t < 16 && haveIp && alive() && !inet; t++) {
            nap(700);
            st = runCmd("cmd wifi status 2>/dev/null");
            if (st.find("VALIDATED") != std::string::npos) inet = true;
        }
        if (!alive()) return;
        // 3. Name resolution - Android's validation probe resolves a hostname over
        // DNS, so a VALIDATED network has working name resolution.
        pub(L1 + (haveIp ? "Succeeded" : "Failed") + "\n"
          + L2 + (inet ? "Succeeded" : "Failed") + "\n" + L3 + "Testing...\n");
        bool dnsOk = inet;
        if (!alive()) return;
        std::string out = L1 + (haveIp ? "Succeeded" : "Failed") + "\n"
                        + L2 + (inet ? "Succeeded" : "Failed") + "\n"
                        + L3 + (dnsOk ? "Succeeded" : "Failed") + "\n"
                        + "\nIP Address                   "
                        + (!ip.empty() ? ip : (haveIp ? std::string("(IPv6)") : std::string("-"))) + "\n";
        pub(out);
        mPs3NetTestActive = false;
    });
}

// ---------------------------------------------------------------------------
// Bluetooth screen
// ---------------------------------------------------------------------------

void NanoMenu::openBtScreen() {
    mMenuState = MENU_BT;
    mBtEntrySelected = 0;
    mBtScrollTop = 0;
    // `dumpsys bluetooth_manager` is slow (sometimes > 500 ms) so we
    // never run it inline. Show a loading state and let the scan
    // thread populate the list.
    mBtStatusMsg = "Loading...";
    mBtStatusMsgUntilMs = std::chrono::duration_cast<std::chrono::milliseconds>(
            std::chrono::steady_clock::now().time_since_epoch()).count() + 3000;
    mDisplayDirty = true;
    startBtScanAsync();
}

void NanoMenu::closeBtScreen() {
    if (mBtScanThread.joinable()) {
        mBtScanInProgress = false;
        mBtScanThread.detach();
    }
    if (mBtDiscoveryThread.joinable()) {
        mBtDiscoveryThread.detach();
    }
    if (!mSettingsNavStack.empty()) {
        mMenuState = MENU_SETTINGS;
    } else {
        mMenuState = MENU_MAIN;
    }
    mDisplayDirty = true;
}

void NanoMenu::refreshBtList() {
    // gammaos-net bt list-bonded is the same getBondedDevices() call
    // Settings uses; it works whether or not `cmd bluetooth_manager`
    // ever shipped a list-bonded-devices subcommand. We still parse
    // dumpsys once to flip the connected bit on live links, because
    // BluetoothManager has no public "is this one connected right now"
    // query for classic HID/A2DP profiles.
    //
    // Detect radio state so the toggle row reflects reality. When BT is
    // off we still want the list to render with just the toggle row.
    std::string btState = runCmd("settings get global bluetooth_on");
    bool btOn = false;
    for (char c : btState) {
        if (c == '1') { btOn = true; break; }
        if (c == '0') { btOn = false; break; }
        if (c != ' ' && c != '\n' && c != '\r') break;
    }
    std::string bondText = btOn ? runCmd("gammaos-net bt list-bonded")
                                : std::string();
    auto devs = parseBondedDevices(bondText);
    // Connection state now rides the 4th TSV column from gammaos-net bt
    // list-bonded (BluetoothDevice.isConnected() on the framework side).
    // The old dumpsys scrape was slow (~500 ms) and brittle.
    // If an active discovery landed non-bonded devices, preserve those
    // between refreshes so the list doesn't flash back to "bonded only"
    // while the user is still deciding which result to pair with.
    std::vector<NanoMenu::BtDevEntry> preservedUnbonded;
    {
        std::lock_guard<std::mutex> lk(mBtListMutex);
        for (auto& d : mBtEntries) {
            if (d.address == "__TOGGLE__") continue;
            if (!d.bonded) preservedUnbonded.push_back(d);
        }
    }
    if (btOn) {
        for (auto& u : preservedUnbonded) {
            bool alreadyBonded = false;
            for (auto& d : devs) {
                if (d.address == u.address) { alreadyBonded = true; break; }
            }
            if (!alreadyBonded) devs.push_back(std::move(u));
        }
    }
    // Prepend a synthetic toggle row. Sentinel: address == "__TOGGLE__".
    // renderBtScreen / handleBtScreenSelect detect it via address.
    std::vector<NanoMenu::BtDevEntry> merged;
    {
        NanoMenu::BtDevEntry toggle{};
        toggle.address = "__TOGGLE__";
        toggle.name = btOn ? "Bluetooth: On" : "Bluetooth: Off";
        toggle.bonded = false;
        toggle.connected = false;
        merged.push_back(std::move(toggle));
    }
    for (auto& d : devs) merged.push_back(std::move(d));
    {
        std::lock_guard<std::mutex> lk(mBtListMutex);
        mBtEntries.swap(merged);
        mBtListDirty = true;
    }
    if (mBtEntrySelected >= (int)mBtEntries.size()) {
        mBtEntrySelected = mBtEntries.empty() ? 0
                         : (int)mBtEntries.size() - 1;
    }
    if (mBtEntrySelected < 0) mBtEntrySelected = 0;
    // Force render redraw; see refreshWifiList() for rationale.
    mDisplayDirty = true;
}

void NanoMenu::discoverBtDevices() {
    // `gammaos-net bt scan` blocks until discovery finishes or the
    // timeout elapses (~8 s by default). Must NOT run on the UI thread;
    // callers go through startBtDiscoveryAsync() which spawns a thread.
    std::string scanText = runCmd("gammaos-net bt scan 8");
    auto scanned = parseBtScanResults(scanText);
    // Merge into the live list: update existing entries in place so
    // their connected state isn't clobbered, and append new ones.
    std::lock_guard<std::mutex> lk(mBtListMutex);
    for (auto& s : scanned) {
        bool found = false;
        for (auto& d : mBtEntries) {
            if (d.address == s.address) {
                // Don't downgrade bonded state from a live bond query.
                if (s.bonded) d.bonded = true;
                if (!s.name.empty() && d.name == d.address) d.name = s.name;
                found = true;
                break;
            }
        }
        if (!found) mBtEntries.push_back(std::move(s));
    }
    mBtListDirty = true;
    mDisplayDirty = true;
}

void NanoMenu::btScanThreadFunc() {
    // Populate the list immediately (runs on this background thread,
    // so the UI stays responsive).
    refreshBtList();
    // Idle and re-poll connected state a couple of times so devices
    // that transition from Paired to Connected after a reconnect appear
    // quickly. Real inquiry (ACL discovery) is a separate, heavier
    // path driven by startBtDiscoveryAsync().
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

void NanoMenu::btDiscoveryThreadFunc() {
    // Actively inquire for nearby devices via BluetoothAdapter.startDiscovery(),
    // merged into mBtEntries. This is the slow path (~8 s radio airtime)
    // triggered by the "Scan" button.
    discoverBtDevices();
    mBtDiscoveryInProgress = false;
    mBtLastScanMs = std::chrono::duration_cast<std::chrono::milliseconds>(
            std::chrono::steady_clock::now().time_since_epoch()).count();
    mBtStatusMsg = "Scan complete";
    mBtStatusMsgUntilMs = std::chrono::duration_cast<std::chrono::milliseconds>(
            std::chrono::steady_clock::now().time_since_epoch()).count() + 2500;
    mDisplayDirty = true;
}

void NanoMenu::startBtDiscoveryAsync() {
    if (mBtDiscoveryInProgress) return;
    if (mBtDiscoveryThread.joinable()) mBtDiscoveryThread.detach();
    mBtDiscoveryInProgress = true;
    mBtStatusMsg = "Scanning for devices...";
    mBtStatusMsgUntilMs = std::chrono::duration_cast<std::chrono::milliseconds>(
            std::chrono::steady_clock::now().time_since_epoch()).count() + 10000;
    mDisplayDirty = true;
    mBtDiscoveryThread = std::thread([this]() { btDiscoveryThreadFunc(); });
}

void NanoMenu::toggleBtRadio(bool on) {
    (void)runCmd(on ? "cmd bluetooth_manager enable"
                    : "cmd bluetooth_manager disable");
    mBtStatusMsg = on ? "Enabling Bluetooth..." : "Disabling Bluetooth...";
    mBtStatusMsgUntilMs = std::chrono::duration_cast<std::chrono::milliseconds>(
            std::chrono::steady_clock::now().time_since_epoch()).count() + 2000;
    if (on) startBtScanAsync();
}

void NanoMenu::pairBtDevice(const std::string& mac) {
    // `gammaos-net bt pair` drives BluetoothDevice.createBond() and
    // auto-accepts consent / passkey-confirmation / 0000-pin pairing
    // variants (the set Settings' BluetoothPairingController handles
    // without user input). Classic gamepads / headsets typically use
    // just-works or 0000, so this works without any extra UI from us.
    mBtStatusMsg = "Pairing...";
    mBtStatusMsgUntilMs = std::chrono::duration_cast<std::chrono::milliseconds>(
            std::chrono::steady_clock::now().time_since_epoch()).count() + 30000;
    mDisplayDirty = true;
    // createBond() blocks for up to ~30 s on the pairing confirmation,
    // so run it on a thread and refresh the list when it resolves so
    // the newly-bonded entry snaps into the bonded section.
    std::string macCopy = mac;
    std::thread([this, macCopy]() {
        std::string cmd = "gammaos-net bt pair " + macCopy;
        std::string result = runCmd(cmd.c_str());
        bool ok = result.find("OK") != std::string::npos;
        if (!ok) ALOGW("pairBtDevice %s failed: %s",
                       macCopy.c_str(), result.c_str());
        mBtStatusMsg = ok ? "Paired" : "Pair failed";
        mBtStatusMsgUntilMs = std::chrono::duration_cast<std::chrono::milliseconds>(
                std::chrono::steady_clock::now().time_since_epoch()).count() + 3000;
        refreshBtList();
        mDisplayDirty = true;
    }).detach();
}

void NanoMenu::unpairBtDevice(const std::string& mac) {
    std::string cmd = "gammaos-net bt unpair " + mac;
    std::string result = runCmd(cmd.c_str());
    bool ok = result.find("OK") != std::string::npos;
    if (!ok) ALOGW("unpairBtDevice %s failed: %s",
                   mac.c_str(), result.c_str());
    mBtStatusMsg = ok ? "Removed" : "Unpair failed";
    mBtStatusMsgUntilMs = std::chrono::duration_cast<std::chrono::milliseconds>(
            std::chrono::steady_clock::now().time_since_epoch()).count() + 2500;
    startBtScanAsync();
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
    // No AOSP CLI exposes direct connect; fall back to a radio toggle so
    // bonded audio/input devices re-pair on the next enable.
    (void)runCmd("cmd bluetooth_manager disable");
    usleep(400 * 1000);
    (void)runCmd("cmd bluetooth_manager enable");
    (void)mac;
    mBtStatusMsg = "Reconnecting...";
    mBtStatusMsgUntilMs = std::chrono::duration_cast<std::chrono::milliseconds>(
            std::chrono::steady_clock::now().time_since_epoch()).count() + 3000;
    startBtScanAsync();
}

// ---------------------------------------------------------------------------
// Accessory Settings Bluetooth wizard backend (PS3 UI). These mirror the
// legacy BT-screen helpers above but write to wizard-private lists/flags
// (mBtWiz*) so the PS3 wizard and the old settings-tree screen don't fight
// over mBtEntries. gammaos-net auto-enables the adapter, so no on/off check.
// All run on detached background threads (the gammaos-net app_process spawn +
// the radio work take seconds and must never block the render thread).
// ---------------------------------------------------------------------------
void NanoMenu::btWizRefreshBondedAsync() {
    std::thread([this]() {
        std::string txt = runCmd("gammaos-net bt list-bonded");
        auto devs = parseBondedDevices(txt);
        { std::lock_guard<std::mutex> lk(mBtWizMutex); mBtWizBonded.swap(devs); }
        mDisplayDirty = true;
    }).detach();
}

void NanoMenu::btWizScanAsync() {
    mBtWizBusy = true;
    std::thread([this]() {
        std::string txt = runCmd("gammaos-net bt scan 8");
        auto devs = parseBtScanResults(txt);
        { std::lock_guard<std::mutex> lk(mBtWizMutex); mBtWizScan.swap(devs); }
        mBtWizBusy = false;
        mDisplayDirty = true;
    }).detach();
}

void NanoMenu::btWizPairAsync(const std::string& addr) {
    mBtWizBusy = true; mBtWizOpOk = false;
    std::string a = addr;
    std::thread([this, a]() {
        std::string r = runCmd(("gammaos-net bt pair " + a).c_str());
        mBtWizOpOk = r.find("OK") != std::string::npos;
        if (!mBtWizOpOk) ALOGW("btWizPair %s failed: %s", a.c_str(), r.c_str());
        // Refresh bonded so the freshly paired device appears in Manage.
        std::string txt = runCmd("gammaos-net bt list-bonded");
        auto devs = parseBondedDevices(txt);
        { std::lock_guard<std::mutex> lk(mBtWizMutex); mBtWizBonded.swap(devs); }
        mBtWizBusy = false;
        mDisplayDirty = true;
    }).detach();
}

void NanoMenu::btWizConnectAsync(const std::string& addr) {
    mBtWizBusy = true; mBtWizOpOk = false;
    std::string a = addr;
    std::thread([this, a]() {
        std::string r = runCmd(("gammaos-net bt connect " + a).c_str());
        mBtWizOpOk = r.find("OK") != std::string::npos;
        usleep(1200 * 1000);   // let the profiles attach before re-reading state
        std::string txt = runCmd("gammaos-net bt list-bonded");
        auto devs = parseBondedDevices(txt);
        { std::lock_guard<std::mutex> lk(mBtWizMutex); mBtWizBonded.swap(devs); }
        mBtWizBusy = false;
        mDisplayDirty = true;
    }).detach();
}

void NanoMenu::btWizDisconnectAsync(const std::string& addr) {
    mBtWizBusy = true; mBtWizOpOk = false;
    std::string a = addr;
    std::thread([this, a]() {
        std::string r = runCmd(("gammaos-net bt disconnect " + a).c_str());
        mBtWizOpOk = r.find("OK") != std::string::npos;
        usleep(800 * 1000);
        std::string txt = runCmd("gammaos-net bt list-bonded");
        auto devs = parseBondedDevices(txt);
        { std::lock_guard<std::mutex> lk(mBtWizMutex); mBtWizBonded.swap(devs); }
        mBtWizBusy = false;
        mDisplayDirty = true;
    }).detach();
}

void NanoMenu::btWizUnpairAsync(const std::string& addr) {
    mBtWizBusy = true; mBtWizOpOk = false;
    std::string a = addr;
    std::thread([this, a]() {
        std::string r = runCmd(("gammaos-net bt unpair " + a).c_str());
        mBtWizOpOk = r.find("OK") != std::string::npos;
        std::string txt = runCmd("gammaos-net bt list-bonded");
        auto devs = parseBondedDevices(txt);
        { std::lock_guard<std::mutex> lk(mBtWizMutex); mBtWizBonded.swap(devs); }
        mBtWizBusy = false;
        mDisplayDirty = true;
    }).detach();
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
    // X (square) = start/refresh device discovery. The refresh for
    // bonded state happens implicitly inside the discovery thread so
    // we don't need to kick the lightweight refresh here.
    startBtDiscoveryAsync();
}

void NanoMenu::handleBtScreenY() {
    // Y (triangle) = unpair the currently-selected bonded device.
    BtDevEntry d;
    {
        std::lock_guard<std::mutex> lk(mBtListMutex);
        if (mBtEntries.empty()) return;
        if (mBtEntrySelected < 0
                || mBtEntrySelected >= (int)mBtEntries.size()) return;
        d = mBtEntries[mBtEntrySelected];
    }
    if (d.address == "__TOGGLE__" || !d.bonded) return;
    unpairBtDevice(d.address);
    {
        std::lock_guard<std::mutex> lk(mBtListMutex);
        for (auto it = mBtEntries.begin(); it != mBtEntries.end(); ++it) {
            if (it->address == d.address) { mBtEntries.erase(it); break; }
        }
        if (mBtEntrySelected >= (int)mBtEntries.size()) {
            mBtEntrySelected = std::max(0, (int)mBtEntries.size() - 1);
        }
    }
    startBtScanAsync();
    mDisplayDirty = true;
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
    if (d.address == "__TOGGLE__") {
        bool turningOn = (d.name == "Bluetooth: Off");
        toggleBtRadio(turningOn);
        return;
    }
    if (!d.bonded) {
        // Discovered-but-not-bonded: initiate pairing. If it succeeds,
        // the bond is persisted to Android's bonded device list so the
        // device stays paired across reboots.
        pairBtDevice(d.address);
    } else {
        // Already bonded: nudge the radio so a dropped link reassociates.
        connectBtDevice(d.address);
    }
}

// ---------------------------------------------------------------------------
// OSK password prompt
// ---------------------------------------------------------------------------

// openOskForPassword + maskPassword now live in NanoOsk.cpp (the Leanback-
// derived multi-script keyboard). Callers that pre-seed mOskQuery (the settings
// text editor) must also set mOsk.caret = mOskQuery.size() after the call.

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

    // Dim background (setup wizard draws its own dim)
    if (!mSetupWizardActive)
        drawQuad(0, 0, mWidth, mHeight, 0.0f, 0.0f, 0.0f, 0.65f);

    float pad = 20.0f * sf;
    float titleScale = 2.6f * sf;
    float rowScale = 1.9f * sf;
    float footScale = 1.3f * sf;

    if (!mSetupWizardActive) {
        const char* title = "Wi-Fi";
        drawText(title, pad, pad, titleScale, 0.95f, 0.95f, 1.0f, 1.0f);
    }

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
            bool isToggle = (e.bssid == "__TOGGLE__");

            if (sel) {
                drawQuad(pad - 6.0f * sf, y - 4.0f * sf,
                         mWidth - pad * 2.0f + 12.0f * sf,
                         rowH, 0.15f, 0.35f, 0.70f, 0.65f);
            }

            if (isToggle) {
                // Render as a simple text row with an on/off pill on the right.
                bool on = (e.savedNetId == -3);
                float textX = pad + 4.0f * sf;
                float textY = y + rowH * 0.15f;
                drawText(e.ssid.c_str(), textX, textY, rowScale,
                         sel ? 1.0f : 0.90f,
                         sel ? 1.0f : 0.90f,
                         sel ? 1.0f : 0.92f, 1.0f);
                const char* pill = on ? "[ On ]" : "[ Off ]";
                float pScale = rowScale * 0.9f;
                float pw = measureText(pill, pScale);
                drawText(pill, mWidth - pw - pad, y + rowH * 0.18f,
                         pScale,
                         on ? 0.4f : 0.85f,
                         on ? 0.95f : 0.45f,
                         on ? 0.4f : 0.45f, 1.0f);
                continue;
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

    // Footer (suppressed during setup wizard - it draws its own)
    if (!mSetupWizardActive) {
        // Offer "Y: Forget" only when a saved network is selected.
        bool selSaved = false;
        if (mWifiEntrySelected >= 0 && mWifiEntrySelected < (int)mWifiEntries.size()) {
            const WifiNetEntry& se = mWifiEntries[mWifiEntrySelected];
            selSaved = (se.bssid != "__TOGGLE__" && se.savedNetId >= 0);
        }
        const char* footer = selSaved ? "A: Connect | Y: Forget | X: Rescan | B: Back"
                                       : "A: Connect | X: Rescan | B: Back";
        float fw = measureText(footer, footScale);
        drawText(footer, (mWidth - fw) / 2.0f,
                 mHeight - FONT_CHAR_H * footScale - 12.0f * sf,
                 footScale, 0.60f, 0.60f, 0.65f, 0.90f);
    }

    // The OSK (including its password preview line) is drawn last in render(),
    // on top of this screen, by NanoMenu::renderOsk().
}

// ---------------------------------------------------------------------------
// Rendering: Bluetooth screen
// ---------------------------------------------------------------------------

void NanoMenu::renderBtScreen() {
    float sf = fminf((float)mWidth / 1080.0f, (float)mHeight / 720.0f);
    if (sf < 0.5f) sf = 0.5f;

    if (!mSetupWizardActive)
        drawQuad(0, 0, mWidth, mHeight, 0.0f, 0.0f, 0.0f, 0.65f);

    float pad = 20.0f * sf;
    float titleScale = 2.6f * sf;
    float rowScale = 1.9f * sf;
    float footScale = 1.3f * sf;

    if (!mSetupWizardActive)
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
    } else if (mBtDiscoveryInProgress) {
        drawText("Scanning for devices...", pad, statusY,
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
        drawText("No devices.  Press X to scan for nearby Bluetooth devices.",
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
            bool isToggle = (d.address == "__TOGGLE__");
            if (sel) {
                drawQuad(pad - 6.0f * sf, y - 4.0f * sf,
                         mWidth - pad * 2.0f + 12.0f * sf,
                         rowH, 0.15f, 0.35f, 0.70f, 0.65f);
            }
            if (isToggle) {
                bool on = (d.name == "Bluetooth: On");
                float textX = pad + 4.0f * sf;
                float textY = y + rowH * 0.15f;
                drawText(d.name.c_str(), textX, textY, rowScale,
                         sel ? 1.0f : 0.90f,
                         sel ? 1.0f : 0.90f,
                         sel ? 1.0f : 0.92f, 1.0f);
                const char* pill = on ? "[ On ]" : "[ Off ]";
                float pScale = rowScale * 0.9f;
                float pw = measureText(pill, pScale);
                drawText(pill, mWidth - pw - pad, y + rowH * 0.18f,
                         pScale,
                         on ? 0.4f : 0.85f,
                         on ? 0.95f : 0.45f,
                         on ? 0.4f : 0.45f, 1.0f);
                continue;
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
                               : (d.bonded ? "Paired" : "Available");
            float statusR, statusG, statusB;
            if (d.connected) { statusR = 0.4f; statusG = 0.95f; statusB = 0.4f; }
            else if (d.bonded) { statusR = 0.6f; statusG = 0.6f; statusB = 0.65f; }
            else { statusR = 0.95f; statusG = 0.75f; statusB = 0.25f; }
            float sScale = rowScale * 0.7f;
            float sw = measureText(status, sScale);
            drawText(status, mWidth - sw - pad, y + rowH * 0.25f,
                     sScale, statusR, statusG, statusB, 0.95f);
        }
    }

    bool selBonded = false;
    if (!devs.empty()
            && mBtEntrySelected >= 0
            && mBtEntrySelected < (int)devs.size()) {
        const auto& sel = devs[mBtEntrySelected];
        selBonded = sel.bonded && sel.address != "__TOGGLE__";
    }
    if (!mSetupWizardActive) {
        const char* footer = selBonded
                ? "A: Connect | Y: Unpair | X: Scan | B: Back"
                : "A: Pair/Connect | X: Scan | B: Back";
        float fw = measureText(footer, footScale);
        drawText(footer, (mWidth - fw) / 2.0f,
                 mHeight - FONT_CHAR_H * footScale - 12.0f * sf,
                 footScale, 0.60f, 0.60f, 0.65f, 0.90f);
    }
}

// ---------------------------------------------------------------------------
// Password prompt overlay (renders above the OSK)
// ---------------------------------------------------------------------------

// renderPasswordPromptOverlay was removed: the password prompt + masked value
// are now rendered inline in the keyboard panel's preview line by renderOsk().

} // namespace android
