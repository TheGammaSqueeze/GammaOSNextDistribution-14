// File Explorer (Settings > File Explorer).
//
// A controller-first file manager for GammaOS Nano. It reuses the existing folder-picker
// navigation idiom (opendir/readdir, storage roots, ".." up, rebuild-the-top-level in place)
// from NanoMenuPS3Folder.cpp's buildFolderBrowser, but lists files AND folders. The per-item
// actions (Copy / Move / Delete / Rename / Information) are surfaced through the SAME XMB
// X/Triangle side menu used everywhere else (openXmbOpt -> xmbOptAction routes "fe*" here),
// so the look and feel is 1:1 with the rest of the menu.
//
// Copy / Move / Delete run on a detached worker that touches only a heap result block + value-
// captured paths, never `this` - so a teardown mid-op cannot use-after-free, and a large copy
// never blocks the render thread (the watchdog hazard, see nano_render_thread_blocking).

#include "NanoMenu.h"

#include <dirent.h>
#include <unistd.h>
#include <sys/stat.h>
#include <stdio.h>
#include <string.h>
#include <strings.h>
#include <errno.h>
#include <time.h>
#include <algorithm>
#include <vector>
#include <string>
#include <memory>
#include <thread>
#include <utils/Log.h>

namespace android {

// ---- path helpers ---------------------------------------------------------
static bool feIsStorageRoot(const std::string& p) {
    if (p == "/storage/emulated/0") return true;
    if (p.compare(0, 9, "/storage/") == 0 && p.find('/', 9) == std::string::npos) return true;
    if (p.compare(0, 15, "/mnt/media_rw/") == 0 && p.find('/', 15) == std::string::npos) return true;
    return false;
}
static std::string feBaseName(const std::string& p) {
    size_t sl = p.find_last_of('/');
    return (sl == std::string::npos) ? p : p.substr(sl + 1);
}
static std::string feParentDir(const std::string& p) {
    size_t sl = p.find_last_of('/');
    if (sl == std::string::npos) return "";
    if (sl == 0) return "/";
    return p.substr(0, sl);
}
static std::string feHumanSize(long long bytes) {
    char b[40];
    double v = (double)bytes;
    if (v >= 1024.0 * 1024.0 * 1024.0) snprintf(b, sizeof(b), "%.2f GB", v / (1024.0 * 1024.0 * 1024.0));
    else if (v >= 1024.0 * 1024.0)     snprintf(b, sizeof(b), "%.1f MB", v / (1024.0 * 1024.0));
    else if (v >= 1024.0)              snprintf(b, sizeof(b), "%.1f KB", v / 1024.0);
    else                               snprintf(b, sizeof(b), "%lld B", bytes);
    return b;
}

// ---- filesystem ops (worker-thread / non-render only) ---------------------
static bool feCopyFile(const std::string& src, const std::string& dst) {
    FILE* in = fopen(src.c_str(), "rb");
    if (!in) return false;
    FILE* out = fopen(dst.c_str(), "wb");
    if (!out) { fclose(in); return false; }
    char buf[1 << 16];
    size_t n; bool ok = true;
    while ((n = fread(buf, 1, sizeof(buf), in)) > 0) {
        if (fwrite(buf, 1, n, out) != n) { ok = false; break; }
    }
    if (ferror(in)) ok = false;
    fclose(in); fclose(out);
    if (ok) { struct stat st; if (stat(src.c_str(), &st) == 0) chmod(dst.c_str(), st.st_mode & 07777); }
    else unlink(dst.c_str());
    return ok;
}
static bool feCopyRecursive(const std::string& src, const std::string& dst) {
    struct stat st;
    if (lstat(src.c_str(), &st) != 0) return false;
    if (S_ISDIR(st.st_mode)) {
        if (mkdir(dst.c_str(), st.st_mode & 07777) != 0 && errno != EEXIST) return false;
        DIR* d = opendir(src.c_str());
        if (!d) return false;
        bool ok = true; struct dirent* e;
        while ((e = readdir(d)) != nullptr) {
            if (!strcmp(e->d_name, ".") || !strcmp(e->d_name, "..")) continue;
            if (!feCopyRecursive(src + "/" + e->d_name, dst + "/" + e->d_name)) ok = false;
        }
        closedir(d);
        return ok;
    }
    if (S_ISREG(st.st_mode)) return feCopyFile(src, dst);
    return true;   // skip special files (sockets/fifos/devices) without failing the whole op
}
static bool feRemoveRecursive(const std::string& p) {
    struct stat st;
    if (lstat(p.c_str(), &st) != 0) return false;
    if (S_ISDIR(st.st_mode)) {
        DIR* d = opendir(p.c_str());
        if (!d) return false;
        bool ok = true; struct dirent* e;
        while ((e = readdir(d)) != nullptr) {
            if (!strcmp(e->d_name, ".") || !strcmp(e->d_name, "..")) continue;
            if (!feRemoveRecursive(p + "/" + e->d_name)) ok = false;
        }
        closedir(d);
        return rmdir(p.c_str()) == 0 && ok;
    }
    return unlink(p.c_str()) == 0;
}
// If `dst` already exists, append " copy" / " copy N" (before the extension) so a paste into the
// same directory never clobbers the source or an existing file.
static std::string feUniqueDest(const std::string& dst) {
    struct stat st;
    if (stat(dst.c_str(), &st) != 0) return dst;
    std::string dir = feParentDir(dst), base = feBaseName(dst), stem = base, ext;
    size_t dot = base.find_last_of('.');
    if (dot != std::string::npos && dot != 0) { stem = base.substr(0, dot); ext = base.substr(dot); }
    for (int i = 1; i < 1000; i++) {
        char suf[24];
        if (i == 1) snprintf(suf, sizeof(suf), " copy");
        else        snprintf(suf, sizeof(suf), " copy %d", i);
        std::string cand = dir + "/" + stem + suf + ext;
        if (stat(cand.c_str(), &st) != 0) return cand;
    }
    return dst;
}

// ---- browser screen -------------------------------------------------------
void NanoMenu::buildFileBrowser(const std::string& path, Ps3Level& out) {
    out.items.clear(); out.sel = 0; out.screenKind = FE_BROWSE;
    mFeBrowsePath = path;
    GLuint folderNmap = nmapForIcon(62);   // folder glyph
    GLuint fileNmap   = nmapForIcon(25);   // document/page glyph

    if (path.empty()) {
        // Storage roots (same set as the folder picker).
        out.title = "File Explorer";
        auto addRoot = [&](const std::string& label, const std::string& target) {
            Ps3Item it; it.label = label; it.kind = PS3_FE_DIR; it.payloadStr = target;
            it.iconTex = 0; it.nmapTex = folderNmap; it.iconR = it.iconG = it.iconB = 1.0f;
            out.items.push_back(it);
        };
        addRoot("Internal storage", "/storage/emulated/0");
        DIR* d = opendir("/storage");
        if (d) { struct dirent* e; while ((e = readdir(d)) != nullptr) {
            if (e->d_name[0] == '.') continue;
            if (!strcmp(e->d_name, "emulated") || !strcmp(e->d_name, "self")) continue;
            addRoot(std::string("SD: ") + e->d_name, std::string("/storage/") + e->d_name);
        } closedir(d); }
        d = opendir("/mnt/media_rw");
        if (d) { struct dirent* e; while ((e = readdir(d)) != nullptr) {
            if (e->d_name[0] == '.') continue;
            addRoot(std::string("Removable: ") + e->d_name, std::string("/mnt/media_rw/") + e->d_name);
        } closedir(d); }
        return;
    }

    out.title = path;
    // ".." up (a storage root climbs back to the roots list).
    { Ps3Item it; it.label = ".."; it.kind = PS3_FE_DIR;
      it.payloadStr = feIsStorageRoot(path) ? std::string("") : feParentDir(path);
      it.iconTex = 0; it.nmapTex = folderNmap; it.iconR = it.iconG = it.iconB = 1.0f;
      out.items.push_back(it); }

    std::vector<std::string> dirs, files;
    DIR* d = opendir(path.c_str());
    if (d) { struct dirent* e; while ((e = readdir(d)) != nullptr) {
        if (e->d_name[0] == '.') continue;   // skip dotfiles (matches the folder picker)
        std::string child = path + "/" + e->d_name;
        struct stat st;
        if (stat(child.c_str(), &st) != 0) continue;
        if (S_ISDIR(st.st_mode)) dirs.push_back(e->d_name);
        else if (S_ISREG(st.st_mode)) files.push_back(e->d_name);
    } closedir(d); }
    auto ci = [](const std::string& a, const std::string& b) { return strcasecmp(a.c_str(), b.c_str()) < 0; };
    std::sort(dirs.begin(), dirs.end(), ci);
    std::sort(files.begin(), files.end(), ci);

    for (const auto& n : dirs) {
        Ps3Item it; it.label = n; it.kind = PS3_FE_DIR; it.payloadStr = path + "/" + n;
        it.iconTex = 0; it.nmapTex = folderNmap; it.iconR = it.iconG = it.iconB = 1.0f;
        out.items.push_back(it);
    }
    for (const auto& n : files) {
        Ps3Item it; it.label = n; it.kind = PS3_FE_FILE; it.payloadStr = path + "/" + n;
        struct stat st; if (stat(it.payloadStr.c_str(), &st) == 0) it.value = feHumanSize((long long)st.st_size);
        it.iconTex = 0; it.nmapTex = fileNmap; it.iconR = it.iconG = it.iconB = 1.0f;
        out.items.push_back(it);
    }
    if (dirs.empty() && files.empty()) {
        // Inert placeholder so an empty directory is not a blank screen.
        Ps3Item it; it.label = "(empty folder)"; it.kind = PS3_GS_FIELD; it.payloadStr = "";
        it.iconTex = 0; it.nmapTex = 0; it.iconR = it.iconG = it.iconB = 0.55f;
        out.items.push_back(it);
    }
}

void NanoMenu::feOpen() {
    // Push the browser level at storage roots, reusing the standard submenu slide-in animation.
    std::vector<Ps3Item> ps = ps3CurItems(); int pSel = ps3CurSel();
    Ps3Level lvl; buildFileBrowser("", lvl); mPs3Stack.push_back(lvl);
    mPs3SubParentItems = ps; mPs3SubParentIdx = pSel; mPs3SubChildItems = mPs3Stack.back().items;
    mPs3SubDir = 1; mPs3SubAnimStart = mEffectTime; mPs3SubAnim = 0.0f;
    mPs3AnimItem = 0.0f; mPs3ItemAnimStart = -1.0f;
}

void NanoMenu::feNavigate(const std::string& path) {
    if (mPs3Stack.empty() || mPs3Stack.back().screenKind != FE_BROWSE) return;
    buildFileBrowser(path, mPs3Stack.back());   // rebuild the top level in place (no push)
}

void NanoMenu::feRefresh() {
    if (mPs3Stack.empty() || mPs3Stack.back().screenKind != FE_BROWSE) return;
    int keep = mPs3Stack.back().sel;
    buildFileBrowser(mFeBrowsePath, mPs3Stack.back());
    int n = (int)mPs3Stack.back().items.size();
    if (keep >= n) keep = n - 1;
    if (keep < 0) keep = 0;
    mPs3Stack.back().sel = keep;
}

bool NanoMenu::feBack() {
    if (mPs3Stack.empty() || mPs3Stack.back().screenKind != FE_BROWSE) return false;
    if (mFeBrowsePath.empty()) return false;   // at the roots list: let the level pop (exit to Settings)
    std::string parent = feIsStorageRoot(mFeBrowsePath) ? std::string("") : feParentDir(mFeBrowsePath);
    buildFileBrowser(parent, mPs3Stack.back());
    return true;
}

void NanoMenu::feShowInfo(const std::string& path) {
    if (path.empty()) return;
    struct stat st;
    bool haveStat = (stat(path.c_str(), &st) == 0);
    bool isDir = haveStat && S_ISDIR(st.st_mode);
    std::string body;
    auto row = [&](const char* label, const std::string& v) {
        if (v.empty()) return;
        char pad[20]; snprintf(pad, sizeof(pad), "%-12s", label);
        body += pad; body += v; body += "\n";
    };
    row("Name", feBaseName(path));
    row("Type", isDir ? "Folder" : "File");
    if (haveStat) {
        if (isDir) {
            int count = 0;
            DIR* d = opendir(path.c_str());
            if (d) { struct dirent* e; while ((e = readdir(d)) != nullptr) {
                if (e->d_name[0] == '.') continue; count++;
            } closedir(d); }
            char c[24]; snprintf(c, sizeof(c), "%d item%s", count, count == 1 ? "" : "s");
            row("Contents", c);
        } else {
            row("Size", feHumanSize((long long)st.st_size));
        }
        char when[40]; struct tm tmv;
        time_t mt = (time_t)st.st_mtime;
        if (localtime_r(&mt, &tmv)) { strftime(when, sizeof(when), "%Y-%m-%d %H:%M", &tmv); row("Modified", when); }
    }
    row("Path", path);
    if (body.empty()) body = "No information is available.";
    feInfoDialog(feBaseName(path), body);
}

void NanoMenu::feStartOp(int kind, const std::string& src, const std::string& dst) {
    if (mFeOp) return;   // one op at a time
    auto op = std::make_shared<FeOp>();
    op->kind = kind; op->name = feBaseName(src);
    mFeOp = op;
    bool move = (kind == 2);
    std::string s = src, dt = dst;
    // Worker captures only the shared result block + value strings (no `this`).
    std::thread([op, s, dt, kind, move]() {
        bool ok;
        if (kind == 3) {
            ok = feRemoveRecursive(s);
        } else {
            ok = feCopyRecursive(s, dt);
            if (ok && move) ok = feRemoveRecursive(s);
        }
        op->ok.store(ok, std::memory_order_release);
        op->done.store(true, std::memory_order_release);
    }).detach();
}

void NanoMenu::feTick() {
    if (!mFeOp) return;
    if (!mFeOp->done.load(std::memory_order_acquire)) return;
    std::shared_ptr<FeOp> op = mFeOp;
    mFeOp.reset();
    bool ok = op->ok.load(std::memory_order_acquire);
    if (op->kind == 2 && ok) mFeClipPath.clear();   // a Move consumed the clipboard
    feRefresh();
    const char* verb = (op->kind == 1) ? "Copy" : (op->kind == 2) ? "Move" : "Delete";
    std::string msg = std::string(verb) + (ok ? " completed." : " failed.");
    feInfoDialog(verb, msg);
}

void NanoMenu::feAction(const std::string& act) {
    if (mFeOp) return;   // busy with a copy/move/delete; ignore further ops until it finishes
    std::string path = mPs3OptCtxPayload;
    std::string name = feBaseName(path);

    if (act == "feopen") { if (!path.empty()) feNavigate(path); return; }

    if (act == "fecopy") { if (!path.empty()) { mFeClipPath = path; mFeClipMove = false; } return; }
    if (act == "femove") { if (!path.empty()) { mFeClipPath = path; mFeClipMove = true;  } return; }

    if (act == "fepaste") {
        if (mFeClipPath.empty() || mFeBrowsePath.empty()) return;
        std::string dst = mFeBrowsePath + "/" + feBaseName(mFeClipPath);
        // Refuse to paste a folder into itself or its own subtree (would recurse forever).
        if (mFeClipMove && (mFeBrowsePath == feParentDir(mFeClipPath))) { mFeClipPath.clear(); return; }
        if (mFeBrowsePath == mFeClipPath ||
            mFeBrowsePath.compare(0, mFeClipPath.size() + 1, mFeClipPath + "/") == 0) {
            feInfoDialog("Paste", "Cannot paste a folder into itself.");
            return;
        }
        dst = feUniqueDest(dst);
        feStartOp(mFeClipMove ? 2 : 1, mFeClipPath, dst);
        return;
    }

    if (act == "ferename") {
        if (path.empty()) return;
        std::string dir = feParentDir(path);
        std::string cur = name;
        openOskForPassword("Rename", [this, dir, path](const std::string& typed) {
            std::string nn = typed;
            for (char& c : nn) if (c == '/') c = '_';   // never allow a path separator
            // trim spaces
            while (!nn.empty() && nn.front() == ' ') nn.erase(nn.begin());
            while (!nn.empty() && nn.back() == ' ') nn.pop_back();
            if (nn.empty() || nn == feBaseName(path)) return;
            std::string ndst = dir + "/" + nn;
            struct stat st;
            if (stat(ndst.c_str(), &st) == 0) { feInfoDialog("Rename", "A file with that name already exists."); return; }
            if (rename(path.c_str(), ndst.c_str()) == 0) feRefresh();
            else feInfoDialog("Rename", "Rename failed.");
        });
        mOskPasswordMode = false; mOskPlaintext = true;   // plain text, not masked
        mOskQuery = cur; mOsk.caret = (int)mOskQuery.size();
        return;
    }

    if (act == "fedelete") {
        if (path.empty()) return;
        mFeDeleteTarget = path;
        mPs3DlgOptions.clear(); mPs3DlgSwatch.clear();
        mPs3DlgKind = 1; mPs3DlgThemeKey = 30; mPs3DlgTitle = std::string("Delete ") + name; mPs3DlgBody.clear();
        mPs3DlgOptions.push_back("Cancel");  mPs3DlgSwatch.push_back(-1);
        mPs3DlgOptions.push_back("Delete");  mPs3DlgSwatch.push_back(-1);
        mPs3DlgSel = 0; mPs3DlgOrigSel = 0;
        mPs3DlgIconTex = 0; mPs3DlgIconNmap = nmapForIcon(22);
        mPs3DlgIconR = mPs3DlgIconG = mPs3DlgIconB = 1.0f;
        mPs3DlgActive = true; mPs3DlgAnim = 0.0f; mPs3DlgBlurValid = false;
        return;
    }

    if (act == "feinfo") { feShowInfo(path); return; }
}

// Generic XMB info dialog (kind 0), mirrors the "info" action in xmbOptAction.
void NanoMenu::feInfoDialog(const std::string& title, const std::string& body) {
    mPs3DlgOptions.clear(); mPs3DlgSwatch.clear();
    mPs3DlgKind = 0; mPs3DlgType = 0; mPs3DlgThemeKey = 0; mPs3DlgBinding = nullptr;
    mPs3DlgIllust = 0; mPs3DlgNotice.clear(); mPs3DlgRomInfo = false;
    mPs3DlgTitle = title;
    mPs3DlgBody  = body;
    mPs3DlgSel = 0; mPs3DlgOrigSel = 0;
    mPs3DlgIconTex = 0; mPs3DlgIconNmap = 0;
    mPs3DlgIconR = mPs3DlgIconG = mPs3DlgIconB = 1.0f;
    mPs3DlgActive = true; mPs3DlgAnim = 0.0f; mPs3DlgBlurValid = false;
}

} // namespace android
