/*
 * Copyright (C) 2026 GammaOS
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

// Network Shares (Settings > Network Shares): add and edit SMB, NFS, WebDAV and FTP shares.
//
// This screen only edits configuration. The share itself is served by the gammaos-sharefs FUSE
// daemon, which init starts and stops purely off persist.gammaos.share.<n>.enabled, so nothing here
// needs to control a service. Storage and the credential encoding come from share_config.cpp, which
// Settings and TvSettings mirror in Java, so a share added in any of the three is the same share.
//
// Once a share is mounted it appears at /mnt/shares/<name>, which is an ordinary path: the folder
// picker behind "Search for Media Servers" lists it (see NanoMenuPS3Folder.cpp), the File Explorer
// copies to and from it, and the media players stream off it with no protocol knowledge at all.

#define LOG_TAG "GammaOSNano"

#include "NanoMenu.h"
#include "NanoI18n.h"      // trDyn() runtime translation of hardcoded UI strings

#include <share_config.h>

#include <stdio.h>
#include <stdlib.h>   // atoi, for the port field
#include <string.h>
#include <utils/Log.h>

namespace android {

using gammaos::sharefs::ShareConfig;
using gammaos::sharefs::ShareType;
using gammaos::sharefs::kMaxShares;

// Dialog theme keys owned by this screen (see applyThemeSetting).
static const int kNsTypeDlg   = 35;   // share-type chooser
static const int kNsRemoveDlg = 36;   // remove-share confirm

// The share-type chooser's option order, which is also the index applyThemeSetting hands back.
static const ShareType kNsTypeOrder[] = {
    ShareType::kSmb, ShareType::kNfs, ShareType::kWebdav, ShareType::kFtp,
};
static const int kNsTypeCount = (int)(sizeof(kNsTypeOrder) / sizeof(kNsTypeOrder[0]));

// What each protocol calls the thing that goes in the "path" field, and what a blank port means.
// Getting this wrong is the most common reason a share does not mount, so the row says the right
// word for the protocol the user picked rather than a generic "Path".
static const char* nsPathLabel(ShareType t) {
    switch (t) {
        case ShareType::kSmb:    return "Share Name";
        case ShareType::kNfs:    return "Export Path";
        case ShareType::kWebdav: return "Remote Folder";
        case ShareType::kFtp:    return "Remote Folder";
        default:                 return "Path";
    }
}

// What a blank port means. WebDAV moves to 443 once HTTPS is on, so the hint the row shows tracks
// the encryption toggle rather than being fixed per protocol.
static int nsDefaultPort(const ShareConfig& c) {
    switch (c.type) {
        case ShareType::kSmb:    return 445;
        case ShareType::kNfs:    return 2049;
        case ShareType::kWebdav: return c.useTls ? 443 : 80;
        case ShareType::kFtp:    return 21;
        default:                 return 0;
    }
}

// NFS authorises by client address, not by account, so a username/password would be a field that
// silently does nothing. Hide them rather than collect input we would throw away.
static bool nsUsesCredentials(ShareType t) { return t != ShareType::kNfs; }

// ---- list screen ----------------------------------------------------------

void NanoMenu::buildSharesList(Ps3Level& out) {
    out.items.clear(); out.sel = 0; out.screenKind = NS_LIST;
    out.title = "Network Shares";
    GLuint nmap = nmapForIcon(6);   // network glyph

    std::vector<ShareConfig> shares = gammaos::sharefs::loadShares();
    for (const ShareConfig& c : shares) {
        Ps3Item it; it.kind = PS3_NS_SHARE; it.a = c.slot;
        it.label = c.name;
        // The subtitle is what the user needs to tell two shares apart at a glance.
        it.desc = std::string(gammaos::sharefs::shareTypeName(c.type)) + "  " + c.host +
                  (c.path.empty() ? std::string() : "/" + c.path);
        // Distinguish "the user turned this off" from "it is on but not connected": the second is
        // a problem to look at, the first is not.
        if (!c.enabled)                                       it.value = "Off";
        else if (gammaos::sharefs::isShareMounted(c.name))    it.value = "Connected";
        else                                                  it.value = "Connecting...";
        it.iconTex = 0; it.nmapTex = nmap;
        it.iconR = it.iconG = it.iconB = 1.0f;
        out.items.push_back(it);
    }

    if ((int)shares.size() < kMaxShares) {
        Ps3Item it; it.kind = PS3_NS_ADD; it.label = "Add Share";
        it.desc = "Connect to an SMB, NFS, WebDAV or FTP server on your network.";
        it.iconTex = 0; it.nmapTex = nmapForIcon(22);
        it.iconR = it.iconG = it.iconB = 1.0f;
        out.items.push_back(it);
    } else {
        Ps3Item it; it.kind = PS3_DATA_LEAF;
        char b[80]; snprintf(b, sizeof(b), "All %d shares are in use", kMaxShares);
        it.label = b;
        it.desc = "Remove a share before adding another.";
        it.iconTex = 0; it.nmapTex = 0; it.iconR = it.iconG = it.iconB = 0.55f;
        out.items.push_back(it);
    }
}

void NanoMenu::nsOpenList() {
    std::vector<Ps3Item> ps = ps3CurItems(); int pSel = ps3CurSel();
    Ps3Level lvl; buildSharesList(lvl); mPs3Stack.push_back(lvl);
    mPs3SubParentItems = ps; mPs3SubParentIdx = pSel; mPs3SubChildItems = mPs3Stack.back().items;
    mPs3SubDir = 1; mPs3SubAnimStart = mEffectTime; mPs3SubAnim = 0.0f;
    mPs3AnimItem = 0.0f; mPs3ItemAnimStart = -1.0f;
}

// ---- editor screen --------------------------------------------------------

void NanoMenu::buildShareEditor(Ps3Level& out) {
    out.items.clear(); out.sel = 0; out.screenKind = NS_EDITOR;
    ShareConfig c;
    if (!gammaos::sharefs::loadShare(mNsEditSlot, &c)) {
        // A brand-new share has no name yet, so loadShare finds nothing. Show the blank form.
        c.slot = mNsEditSlot;
    }
    out.title = c.name.empty() ? "New Share" : c.name;

    auto add = [&](const char* label, int field, const std::string& value) {
        Ps3Item it; it.label = label; it.kind = PS3_NS_FIELD; it.a = field;
        it.value = value; it.iconTex = 0; it.nmapTex = 0;   // clean icon-free form rows
        it.iconR = it.iconG = it.iconB = 1.0f;
        out.items.push_back(it);
    };

    // Enabled sits at the top but is only meaningful once the share is complete; say why when it
    // is not, instead of letting the user turn on a mount that cannot succeed.
    const std::string problem = gammaos::sharefs::shareProblem(c);
    { Ps3Item it; it.label = "Enabled"; it.kind = PS3_NS_FIELD; it.a = NSF_ENABLED;
      it.value = c.enabled ? "On" : "Off";
      if (!problem.empty()) { it.value = "Off"; it.desc = problem; }
      it.iconTex = 0; it.nmapTex = 0;
      // Grey the row while the share is incomplete so it reads as unavailable, not broken.
      float g = problem.empty() ? 1.0f : 0.55f;
      it.iconR = it.iconG = it.iconB = g;
      out.items.push_back(it); }

    add("Name", NSF_NAME, c.name.empty() ? "(required)" : c.name);
    add("Type", NSF_TYPE, c.type == ShareType::kUnknown
                              ? "(required)" : gammaos::sharefs::shareTypeName(c.type));
    add("Server", NSF_HOST, c.host.empty() ? "(required)" : c.host);
    // No port row for NFS: the client asks the server's portmapper where nfsd and mountd are
    // listening, so a port typed here would be ignored.
    if (c.type != ShareType::kNfs) {
        char b[24];
        if (c.port > 0) snprintf(b, sizeof(b), "%d", c.port);
        else            snprintf(b, sizeof(b), "Default (%d)", nsDefaultPort(c));
        add("Port", NSF_PORT, b);
    }
    add(nsPathLabel(c.type), NSF_PATH, c.path.empty() ? "(none)" : c.path);

    if (nsUsesCredentials(c.type)) {
        add("Username", NSF_USER, c.user.empty() ? "Guest" : c.user);
        // Never show the stored password back, not even decrypted: a fixed-width mask also hides
        // its length. An empty one still has to read as empty so "no password set" is obvious.
        add("Password", NSF_PASS, c.password.empty() ? "(none)" : "********");
        if (c.type == ShareType::kSmb)
            add("Workgroup", NSF_DOMAIN, c.domain.empty() ? "(none)" : c.domain);
    } else {
        Ps3Item it; it.kind = PS3_DATA_LEAF; it.label = "Access";
        it.value = "By address";
        it.desc = "NFS servers grant access by device address rather than by account, so the "
                  "export on the server must allow this device.";
        it.iconTex = 0; it.nmapTex = 0; it.iconR = it.iconG = it.iconB = 0.55f;
        out.items.push_back(it);
    }

    // Encryption is its own row rather than being inferred from the port, because a NAS commonly
    // serves WebDAV over TLS on a port of its own. SMB negotiates its own; NFS has none.
    if (c.type == ShareType::kWebdav || c.type == ShareType::kFtp) {
        Ps3Item it; it.label = (c.type == ShareType::kWebdav) ? "Use HTTPS" : "Use FTPS";
        it.kind = PS3_NS_FIELD; it.a = NSF_TLS;
        it.value = c.useTls ? "On" : "Off";
        it.desc = (c.type == ShareType::kWebdav)
                      ? "Connect with https instead of http."
                      : "Connect with FTP over TLS.";
        it.iconTex = 0; it.nmapTex = 0; it.iconR = it.iconG = it.iconB = 1.0f;
        out.items.push_back(it);
    }

    add("Read Only", NSF_READONLY, c.readOnly ? "On" : "Off");

    // Live state, so a share that is on but not connecting is visible here rather than only by
    // noticing an empty folder later.
    { Ps3Item it; it.kind = PS3_NS_FIELD; it.a = NSF_STATUS; it.label = "Status";
      if (!c.enabled) it.value = "Not connected";
      else if (gammaos::sharefs::isShareMounted(c.name)) {
          it.value = "Connected";
          it.desc = "Available at /mnt/shares/" + c.name;
      } else {
          it.value = "Connecting...";
          it.desc = "If this does not change, check the server address and sign-in details.";
      }
      it.iconTex = 0; it.nmapTex = 0; it.iconR = it.iconG = it.iconB = 0.55f;
      out.items.push_back(it); }

    add("Remove Share", NSF_DELETE, "");
}

void NanoMenu::nsOpenEditor(int slot, bool isNew) {
    if (slot < 1 || slot > kMaxShares) return;
    mNsEditSlot = slot;
    mNsEditIsNew = isNew;
    std::vector<Ps3Item> ps = ps3CurItems(); int pSel = ps3CurSel();
    Ps3Level lvl; buildShareEditor(lvl); mPs3Stack.push_back(lvl);
    mPs3SubParentItems = ps; mPs3SubParentIdx = pSel; mPs3SubChildItems = mPs3Stack.back().items;
    mPs3SubDir = 1; mPs3SubAnimStart = mEffectTime; mPs3SubAnim = 0.0f;
    mPs3AnimItem = 0.0f; mPs3ItemAnimStart = -1.0f;
}

void NanoMenu::nsAddShare() {
    int slot = gammaos::sharefs::firstFreeSlot();
    if (slot == 0) return;   // the list screen already says why; nothing to do
    // Seed the slot with a sensible default so the editor has something to show and the share has
    // an identity from the moment it exists. SMB is the overwhelmingly common case on a home LAN.
    ShareConfig c;
    c.slot = slot;
    char b[32]; snprintf(b, sizeof(b), "Share %d", slot);
    c.name = b;
    c.type = ShareType::kSmb;
    gammaos::sharefs::saveShare(c);
    ALOGI("shares: created slot %d", slot);
    nsOpenEditor(slot, true);
}

void NanoMenu::nsDiscardIfUnconfigured() {
    if (!mNsEditIsNew || mNsEditSlot < 1) return;
    ShareConfig c;
    if (gammaos::sharefs::loadShare(mNsEditSlot, &c)) {
        // "Untouched" means the server was never entered, which is the one field a share is
        // useless without. Anything the user did type is worth keeping, even if incomplete.
        if (c.host.empty()) {
            ALOGI("shares: discarding unconfigured slot %d", mNsEditSlot);
            gammaos::sharefs::deleteShare(mNsEditSlot);
        }
    }
    mNsEditIsNew = false;
    // The list underneath is rebuilt by the pop that follows this call.
}

// Follow the mount state while a shares screen is open.
//
// Enabling a share only sets a property; init then starts the daemon, which connects to the server,
// which can take seconds. Rebuilding on a timer alone would fight the user (it resets nothing, but
// it would rebuild 60 times a second), so the rebuild is gated on the set of mounted shares
// actually changing. Reading /proc/self/mountinfo once a second is cheap and never blocks.
void NanoMenu::nsTick() {
    if (mPs3Stack.empty()) return;
    const int kind = mPs3Stack.back().screenKind;
    if (kind != NS_LIST && kind != NS_EDITOR) return;
    if (mEffectTime < mNsNextPoll) return;
    mNsNextPoll = mEffectTime + 1.0f;

    std::string sig;
    for (const std::string& n : mountedShareNames()) { sig += n; sig += '\n'; }
    if (sig == mNsMountSig) return;
    mNsMountSig = std::move(sig);
    nsRefreshStackLevels();
    mDisplayDirty = true;
}

void NanoMenu::nsRefreshStackLevels() {
    for (auto& lvl : mPs3Stack) {
        int keep = lvl.sel;
        if (lvl.screenKind == NS_LIST) {
            buildSharesList(lvl);
        } else if (lvl.screenKind == NS_EDITOR && mNsEditSlot > 0) {
            buildShareEditor(lvl);
        } else {
            continue;
        }
        int n = (int)lvl.items.size();
        if (keep >= n) keep = n - 1;
        lvl.sel = keep < 0 ? 0 : keep;
    }
}

// ---- field editing --------------------------------------------------------

void NanoMenu::nsToggleEnabled() {
    ShareConfig c;
    if (!gammaos::sharefs::loadShare(mNsEditSlot, &c)) return;
    if (!c.enabled) {
        // Refuse to enable something that cannot mount, and say what is missing. Otherwise the
        // daemon fails in the log and the UI just sits on "Connecting..." forever.
        const std::string problem = gammaos::sharefs::shareProblem(c);
        if (!problem.empty()) {
            feInfoDialog("Cannot Connect Yet", problem);
            return;
        }
    }
    gammaos::sharefs::setShareEnabled(mNsEditSlot, !c.enabled);
    ALOGI("shares: slot %d %s", mNsEditSlot, c.enabled ? "disabled" : "enabled");
    nsRefreshStackLevels();
}

void NanoMenu::nsSetType(int typeIdx) {
    if (typeIdx < 0 || typeIdx >= kNsTypeCount) return;
    ShareConfig c;
    if (!gammaos::sharefs::loadShare(mNsEditSlot, &c)) return;
    ShareType want = kNsTypeOrder[typeIdx];
    if (want == c.type) return;
    c.type = want;
    // A port that was the old protocol's default is meaningless under the new one, and the fields
    // themselves change shape (NFS drops the credentials), so reset the port to "default" rather
    // than leaving 445 on an FTP share.
    c.port = 0;
    gammaos::sharefs::saveShare(c);
    // Changing the protocol of a live mount would leave it serving the old one until something
    // restarted it, so take it down and let the user turn it back on when the details are right.
    if (c.enabled) gammaos::sharefs::setShareEnabled(c.slot, false);
    nsRefreshStackLevels();
}

void NanoMenu::nsOpenRemoveConfirm() {
    ShareConfig c;
    if (!gammaos::sharefs::loadShare(mNsEditSlot, &c)) return;
    mPs3DlgOptions.clear(); mPs3DlgSwatch.clear();
    mPs3DlgKind = 1; mPs3DlgThemeKey = kNsRemoveDlg;
    mPs3DlgTitle = "Remove " + c.name;
    mPs3DlgBody = "This disconnects the share and forgets its sign-in details. Nothing on the "
                  "server is deleted.";
    mPs3DlgOptions.push_back("Cancel");  mPs3DlgSwatch.push_back(-1);
    mPs3DlgOptions.push_back("Remove");  mPs3DlgSwatch.push_back(-1);
    mPs3DlgSel = 0; mPs3DlgOrigSel = 0;
    mPs3DlgIconTex = 0; mPs3DlgIconNmap = nmapForIcon(22);
    mPs3DlgIconR = mPs3DlgIconG = mPs3DlgIconB = 1.0f;
    mPs3DlgActive = true; mPs3DlgAnim = 0.0f; mPs3DlgBlurValid = false;
}

void NanoMenu::nsRemoveShare() {
    if (mNsEditSlot < 1) return;
    ALOGI("shares: removing slot %d", mNsEditSlot);
    gammaos::sharefs::deleteShare(mNsEditSlot);
    mNsEditSlot = 0; mNsEditIsNew = false;
    // Leave the editor, then refresh the list underneath it.
    if (!mPs3Stack.empty() && mPs3Stack.back().screenKind == NS_EDITOR) mPs3Stack.pop_back();
    nsRefreshStackLevels();
}

void NanoMenu::nsEditField(int field) {
    ShareConfig cur;
    if (!gammaos::sharefs::loadShare(mNsEditSlot, &cur)) return;

    switch (field) {
        case NSF_ENABLED:  nsToggleEnabled(); return;
        case NSF_STATUS:   return;   // inert
        case NSF_DELETE:   nsOpenRemoveConfirm(); return;
        case NSF_TLS:
        case NSF_READONLY: {
            ShareConfig c = cur;
            if (field == NSF_TLS) c.useTls = !c.useTls;
            else                  c.readOnly = !c.readOnly;
            gammaos::sharefs::saveShare(c);
            // Both flags are applied when the connection is made, so a running share has to be
            // restarted to pick the change up. Do that only when it is actually up.
            if (c.enabled && gammaos::sharefs::isShareMounted(c.name)) {
                gammaos::sharefs::setShareEnabled(c.slot, false);
                gammaos::sharefs::setShareEnabled(c.slot, true);
            }
            nsRefreshStackLevels();
            return;
        }
        case NSF_TYPE: {
            mPs3DlgOptions.clear(); mPs3DlgSwatch.clear();
            mPs3DlgKind = 1; mPs3DlgThemeKey = kNsTypeDlg;
            mPs3DlgTitle = "Share Type"; mPs3DlgBody.clear();
            mPs3DlgSel = 0;
            for (int i = 0; i < kNsTypeCount; i++) {
                mPs3DlgOptions.push_back(gammaos::sharefs::shareTypeName(kNsTypeOrder[i]));
                mPs3DlgSwatch.push_back(-1);
                if (kNsTypeOrder[i] == cur.type) mPs3DlgSel = i;
            }
            mPs3DlgOrigSel = mPs3DlgSel;
            mPs3DlgIconTex = 0; mPs3DlgIconNmap = nmapForIcon(22);
            mPs3DlgIconR = mPs3DlgIconG = mPs3DlgIconB = 1.0f;
            mPs3DlgActive = true; mPs3DlgAnim = 0.0f; mPs3DlgBlurValid = false;
            return;
        }
        default: break;
    }

    // Text fields, via the OSK prefilled with what is there now.
    std::string prefill, prompt;
    bool masked = false;
    switch (field) {
        case NSF_NAME:
            prefill = cur.name;
            prompt = "Name (also the folder name under Shares)";
            break;
        case NSF_HOST:
            prefill = cur.host;
            prompt = "Server address (name or IP, e.g. 192.168.1.10)";
            break;
        case NSF_PORT: {
            char b[24]; if (cur.port > 0) snprintf(b, sizeof(b), "%d", cur.port); else b[0] = '\0';
            prefill = b;
            char p[96];
            snprintf(p, sizeof(p), "Port (blank = default, %d)", nsDefaultPort(cur));
            prompt = p;
            break;
        }
        case NSF_PATH:
            prefill = cur.path;
            prompt = (cur.type == ShareType::kSmb)
                         ? "Share name on the server (e.g. media)"
                         : (cur.type == ShareType::kNfs
                                ? "Exported path on the server (e.g. /volume1/media)"
                                : "Remote folder (blank = the server's root)");
            break;
        case NSF_USER:
            prefill = cur.user;
            prompt = "Username (blank = connect as guest)";
            break;
        case NSF_PASS:
            // Deliberately not prefilled: showing the stored password back, even masked, means one
            // stray keypress can commit a truncated version of it. Blank clears it.
            prompt = "Password (blank = no password)";
            masked = true;
            break;
        case NSF_DOMAIN:
            prefill = cur.domain;
            prompt = "Workgroup or domain (usually blank)";
            break;
        default:
            return;
    }

    const int slot = mNsEditSlot;
    openOskForPassword(prompt, [this, slot, field](const std::string& val) {
        ShareConfig c;
        if (!gammaos::sharefs::loadShare(slot, &c)) {
            // A new share whose name has not been committed yet has no stored record to load.
            c = ShareConfig();
            c.slot = slot;
        }
        switch (field) {
            case NSF_NAME: {
                std::string v = val;
                // The name is a directory under /mnt/shares, so it cannot contain a separator.
                // Fix it quietly rather than rejecting the whole edit.
                for (char& ch : v) if (ch == '/') ch = '_';
                while (!v.empty() && v.front() == ' ') v.erase(v.begin());
                while (!v.empty() && v.back() == ' ') v.pop_back();
                if (v.empty() || v == "." || v == "..") return;   // keep the old name
                if (v == c.name) return;
                // The mount point is derived from the name, so a rename has to take the old mount
                // down; the user turns it back on and it comes up under the new folder.
                if (c.enabled) gammaos::sharefs::setShareEnabled(slot, false);
                c.name = v;
                break;
            }
            case NSF_HOST:   c.host = val; break;
            case NSF_PATH:   c.path = val; break;
            case NSF_USER:   c.user = val; break;
            case NSF_PASS:   c.password = val; break;
            case NSF_DOMAIN: c.domain = val; break;
            case NSF_PORT: {
                int p = atoi(val.c_str());
                // Anything outside a real port number means "use the protocol default" rather than
                // storing a value that could only ever fail to connect.
                c.port = (p > 0 && p <= 65535) ? p : 0;
                break;
            }
            default: return;
        }
        gammaos::sharefs::saveShare(c);
        nsRefreshStackLevels();
    });
    mOskPasswordMode = masked; mOskPlaintext = !masked;
    mOskQuery = prefill; mOsk.caret = (int)mOskQuery.size();
}

} // namespace android
