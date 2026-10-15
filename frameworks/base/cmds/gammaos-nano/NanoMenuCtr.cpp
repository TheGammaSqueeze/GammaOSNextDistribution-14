// NanoMenuCtr - nano side of the 3DS HOME Menu theme (persist.gammaos.nano.ndstheme=4).
//
// NanoCtrHome draws and runs the menu; this file feeds it nano's content (the Game category:
// game systems as folders, their games and subfolders, Recently Played, Applications,
// collections), launches through nano's own launch paths, maps raw input onto 3DS buttons and
// places the two 3DS screens on the panels (two panels: top on the primary, bottom on the
// secondary; one panel: stacked or bottom only, by aspect).
#include "NanoMenu.h"
#include "NanoMenuDrm.h"
#include "NanoCtrHome.h"
#include "NanoCtrBoot.h"
#include "NanoCtrAudio.h"
#include "NanoCtrPanel.h"
#include "NanoCtrArt.h"
#include "NanoCtrTheme.h"
#include "NanoVideo.h"
#include "NanoMenuUtils.h"
#include "NanoI18n.h"
#include "NanoMenuStrings.h"

#include <random>
#include <linux/input.h>
#include <dirent.h>
#include <sys/stat.h>
#include <sys/system_properties.h>
// exported by libc, declared only in the internal header (see NanoMenuInput.cpp)
extern "C" uint32_t __system_property_serial(const prop_info* __pi);

#include <android-base/properties.h>
#include <cutils/properties.h>
#include <log/log.h>
#include <utils/SystemClock.h>
#include <utils/Timers.h>

#include "stb_image.h"

#include <EGL/egl.h>
#include <GLES2/gl2ext.h>

#undef LOG_TAG
#define LOG_TAG "NanoCtr"

namespace android {

static const char* kCtrAssetDirs[] = {
    "/data/local/tmp/ctr_assets",   // development override (pushed by the test scripts)
    "/system/etc/nano_ctr",
};

// ---------------------------------------------------------------------------------------- host

class NanoCtrHost : public ctr::CtrHomeHost, public ctr::MyMenuStore {
public:
    explicit NanoCtrHost(NanoMenu* m) : mMenu(m) {}

    // Identity of a container: the Ps3Item that opens it, by id.
    std::map<std::string, NanoMenu::Ps3Item> mFolders;
    std::map<std::string, NanoMenu::Ps3Item> mTitles;

    static bool isContainer(int kind) {
        switch (kind) {
            case NanoMenu::PS3_SYSTEM: case NanoMenu::PS3_ROM_FOLDER: case NanoMenu::PS3_RECENT_LIST:
            case NanoMenu::PS3_APP_LIST: case NanoMenu::PS3_COLLECTIONS_LIST: case NanoMenu::PS3_COLLECTION:
            case NanoMenu::PS3_FAVORITES_LIST: case NanoMenu::PS3_PINNED_APPS_LIST:
                return true;
            default:
                return false;
        }
    }

    // ---- identity: stable across rescans (paths, system ids, packages), so a layout outlives a
    // rescan that renumbers nano's lists
    std::string idOf(const NanoMenu::Ps3Item& it) {
        const auto& systems = mMenu->mXmbSystems;
        switch (it.kind) {
            case NanoMenu::PS3_ROM:
                if (it.a >= 0 && it.a < (int)systems.size() && it.b >= 0 && it.b < (int)systems[it.a].roms.size())
                    return "rom:" + systems[it.a].roms[it.b];
                break;
            case NanoMenu::PS3_RECENT:
                if (it.a >= 0 && it.a < (int)mMenu->mXmbRecent.size()) return "recent:" + mMenu->mXmbRecent[it.a].romPath;
                break;
            case NanoMenu::PS3_SYSTEM:
                if (it.a >= 0 && it.a < (int)systems.size()) return "sys:" + systems[it.a].id;
                break;
            case NanoMenu::PS3_ROM_FOLDER: return "romdir:" + it.payloadStr;
            case NanoMenu::PS3_APP: case NanoMenu::PS3_LAUNCH_PKG:
                if (!it.payloadStr.empty()) return "app:" + it.payloadStr;
                break;
            case NanoMenu::PS3_RECENT_LIST: case NanoMenu::PS3_APP_LIST: case NanoMenu::PS3_COLLECTIONS_LIST:
            case NanoMenu::PS3_FAVORITES_LIST: case NanoMenu::PS3_PINNED_APPS_LIST:
                return "list:" + std::to_string(it.kind);
            case NanoMenu::PS3_COLLECTION: return "col:" + it.label;
            default: break;
        }
        char buf[64];
        snprintf(buf, sizeof(buf), "%d:%d:%d:", it.kind, it.a, it.b);
        return std::string(buf) + it.payloadStr + ":" + it.label;
    }

    ctr::HomeItem homeItem(const NanoMenu::Ps3Item& it) {
        ctr::HomeItem h;
        h.id = idOf(it);
        h.name = it.label;
        if (isContainer(it.kind)) {
            h.kind = ctr::HomeItemKind::Folder;
            // PICO-8's is the one system icon kept in its own colours (initIconTextures)
            h.monoIcon = it.nmapTex != 0 && !it.flatOwnTint && it.iconTex != mMenu->mIconTextures[14];
            mFolders[h.id] = it;
        } else {
            h.kind = ctr::HomeItemKind::Title;
            h.monoIcon = it.nmapTex != 0 && !it.flatOwnTint;
            mTitles[h.id] = it;
            if (it.kind == NanoMenu::PS3_ROM && it.a >= 0 && it.a < (int)mMenu->mXmbSystems.size())
                h.publisher = mMenu->mXmbSystems[it.a].name;
            // a DS game named from its banner shows the banner as authored on the name plate
            // (its lines and publisher, as the console shows a DS card); a rename keeps its name
            std::string ds;
            if (mMenu->ndsRomTitleEnabled() && mMenu->ndsIsDsRomItem(it, &ds)) {
                const std::string bt = mMenu->ndsBannerTitleFor(ds);
                if (!bt.empty() && bt == it.label) h.plate = mMenu->ndsBannerTextFor(ds);
            }
        }
        return h;
    }

    std::vector<ctr::HomeItem> fromLevel(const std::vector<NanoMenu::Ps3Item>& list) {
        std::vector<ctr::HomeItem> out;
        for (const NanoMenu::Ps3Item& it : list) {
            if (it.kind == NanoMenu::PS3_DATA_LEAF && it.action == 0) continue;   // "There are no titles"
            out.push_back(homeItem(it));
        }
        return out;
    }

    int gameCat() const {
        for (size_t i = 0; i < mMenu->mPs3Cats.size(); i++)
            if (mMenu->mPs3Cats[i].name == "Game") return (int)i;
        return -1;
    }

    // ---- the layout (state.ts Launcher.dat): root slots and the user's folders, by stable id.
    // /data/system/nano_ctr_layout.txt, one record a line: "R <slot> <id>", "F <folder> <name>",
    // "S <folder> <slot> <id>".
    struct UserFolder {
        std::string name;
        std::map<int, std::string> slots;
    };
    struct Layout {
        std::map<int, std::string> root;
        std::map<std::string, UserFolder> folders;   // "uf:<n>"
    };
    Layout mLayout;
    bool mLayoutLoaded = false;
    static constexpr const char* kLayoutPath = "/data/system/nano_ctr_layout.txt";
    static constexpr int kFolderSlots = 60;   // state.ts FOLDER_SLOTS
    static constexpr int kMaxFolders = 60;

    void writeLayoutRecords(FILE* f) const {
        for (const auto& r : mLayout.root) fprintf(f, "R %d %s\n", r.first, r.second.c_str());
        for (const auto& uf : mLayout.folders) {
            fprintf(f, "F %s %s\n", uf.first.c_str(), uf.second.name.c_str());
            for (const auto& s : uf.second.slots) fprintf(f, "S %s %d %s\n", uf.first.c_str(), s.first, s.second.c_str());
        }
    }

    // One record of the layout file ("R <slot> <id>", "F <folder> <name>", "S <folder> <slot> <id>").
    static void parseLayoutRecord(const std::string& l, Layout& into) {
        if (l.size() < 3 || l[1] != ' ') return;
        const std::string rest = l.substr(2);
        if (l[0] == 'R') {
            const size_t sp = rest.find(' ');
            if (sp == std::string::npos) return;
            into.root[atoi(rest.substr(0, sp).c_str())] = rest.substr(sp + 1);
        } else if (l[0] == 'F') {
            const size_t sp = rest.find(' ');
            if (sp == std::string::npos) return;
            into.folders[rest.substr(0, sp)].name = rest.substr(sp + 1);
        } else if (l[0] == 'S') {
            const size_t a = rest.find(' ');
            const size_t b = a == std::string::npos ? a : rest.find(' ', a + 1);
            if (b == std::string::npos) return;
            into.folders[rest.substr(0, a)].slots[atoi(rest.substr(a + 1, b - a - 1).c_str())] = rest.substr(b + 1);
        }
    }

    void loadLayout() {
        if (mLayoutLoaded) return;
        mLayoutLoaded = true;
        FILE* f = fopen(kLayoutPath, "r");
        if (!f) return;
        char line[4096];
        while (fgets(line, sizeof(line), f)) {
            std::string l(line);
            while (!l.empty() && (l.back() == '\n' || l.back() == '\r')) l.pop_back();
            if (l.size() < 3 || l[1] != ' ') continue;
            const std::string rest = l.substr(2);
            if (l[0] == 'R') {
                const size_t sp = rest.find(' ');
                if (sp == std::string::npos) continue;
                mLayout.root[atoi(rest.substr(0, sp).c_str())] = rest.substr(sp + 1);
            } else if (l[0] == 'F') {
                const size_t sp = rest.find(' ');
                if (sp == std::string::npos) continue;
                mLayout.folders[rest.substr(0, sp)].name = rest.substr(sp + 1);
            } else if (l[0] == 'S') {
                const size_t a = rest.find(' ');
                const size_t b = a == std::string::npos ? a : rest.find(' ', a + 1);
                if (b == std::string::npos) continue;
                mLayout.folders[rest.substr(0, a)].slots[atoi(rest.substr(a + 1, b - a - 1).c_str())] = rest.substr(b + 1);
            }
        }
        fclose(f);
    }

    // ---- Save/Load Layout (My Menu): /data/system/nano_ctr_mymenu, "mng" once the management
    // information exists, slot<N>.txt per saved layout ("T <theme>", "Z <zoom>", "C <cursor>",
    // then the layout file's records), slot<N>_top.bin / _bottom.bin the thumbnails (the scene's)
    static constexpr const char* kMyMenuDir = "/data/system/nano_ctr_mymenu";
    ctr::MyMenuStore* myMenuStore() override { return this; }
    std::string myMenuSlot(int slot) const { return std::string(kMyMenuDir) + "/slot" + std::to_string(slot) + ".txt"; }
    std::string thumbDir() override { return kMyMenuDir; }
    bool created() override { return access((std::string(kMyMenuDir) + "/mng").c_str(), F_OK) == 0; }
    void create() override {
        mkdir(kMyMenuDir, 0755);
        if (FILE* f = fopen((std::string(kMyMenuDir) + "/mng").c_str(), "w")) fclose(f);
    }
    bool used(int slot) override { return slot >= 0 && slot < 8 && access(myMenuSlot(slot).c_str(), F_OK) == 0; }
    static std::string currentTheme() {
        char v[PROPERTY_VALUE_MAX] = {};
        property_get("persist.gammaos.nano.ctr.theme", v, "-1");
        return v;
    }
    bool currentShuffle() override { return currentTheme() == "shuffle"; }
    bool shuffle(int slot) override {
        FILE* f = fopen(myMenuSlot(slot).c_str(), "r");
        if (!f) return false;
        char line[512] = {};
        const bool sh = fgets(line, sizeof(line), f) && strncmp(line, "T shuffle", 9) == 0;
        fclose(f);
        return sh;
    }
    bool save(int slot) override {
        if (!mMenu->mCtrHome) return false;
        loadLayout();
        mkdir(kMyMenuDir, 0755);
        int zoom = 0, cursor = 0;
        mMenu->mCtrHome->layoutState(&zoom, &cursor);
        const std::string path = myMenuSlot(slot), tmp = path + ".tmp";
        FILE* f = fopen(tmp.c_str(), "w");
        if (!f) return false;
        fprintf(f, "T %s\nZ %d\nC %d\n", currentTheme().c_str(), zoom, cursor);
        writeLayoutRecords(f);
        const bool ok = fflush(f) == 0 && fsync(fileno(f)) == 0;
        fclose(f);
        if (ok) rename(tmp.c_str(), path.c_str());
        else unlink(tmp.c_str());
        return ok;
    }
    void remove(int slot) override { unlink(myMenuSlot(slot).c_str()); }
    bool apply(int slot) override {
        FILE* f = fopen(myMenuSlot(slot).c_str(), "r");
        if (!f) return false;
        std::string theme = "-1";
        int zoom = 0, cursor = 0;
        Layout next;
        char line[4096];
        while (fgets(line, sizeof(line), f)) {
            std::string l(line);
            while (!l.empty() && (l.back() == '\n' || l.back() == '\r')) l.pop_back();
            if (l.size() < 3 || l[1] != ' ') continue;
            if (l[0] == 'T') theme = l.substr(2);
            else if (l[0] == 'Z') zoom = atoi(l.c_str() + 2);
            else if (l[0] == 'C') cursor = atoi(l.c_str() + 2);
            else parseLayoutRecord(l, next);
        }
        fclose(f);
        mLayout = std::move(next);
        mLayoutLoaded = true;
        saveLayout();
        // the theme of the slot (the menu polls the property and applies it)
        if (theme != currentTheme()) property_set("persist.gammaos.nano.ctr.theme", theme.c_str());
        if (mMenu->mCtrHome) mMenu->mCtrHome->applyLayoutState(zoom, cursor);
        return true;
    }

    void saveLayout() {
        const std::string tmp = std::string(kLayoutPath) + ".tmp";
        FILE* f = fopen(tmp.c_str(), "w");
        if (!f) { ALOGW("ctr: layout not saved"); return; }
        writeLayoutRecords(f);
        const bool ok = fflush(f) == 0 && fsync(fileno(f)) == 0;
        fclose(f);
        if (ok) rename(tmp.c_str(), kLayoutPath);
        else unlink(tmp.c_str());
    }

    // A title by its stable id (a game or an app anywhere in nano), for a reference on the root
    // or in a user folder. The systems' lists are built once per change of the library.
    std::map<int, NanoMenu::Ps3Level> mSystemLevels;
    std::unordered_map<std::string, std::pair<int, int>> mRomIndex;   // path -> system, level index
    std::vector<NanoMenu::Ps3Item> mApps;
    uint64_t mResolveSig = 0;
    bool resolve(const std::string& id, NanoMenu::Ps3Item* out) {
        const uint64_t sig = mMenu->ctrModelSignature();
        if (sig != mResolveSig) {
            mResolveSig = sig;
            mSystemLevels.clear();
            mRomIndex.clear();
            mApps.clear();
            NanoMenu::Ps3Level apps;
            mMenu->buildAppSubmenu(apps);
            mApps = apps.items;
        }
        // a Recently Played entry put on the root or into a folder stands for its game
        if (id.rfind("rom:", 0) == 0 || id.rfind("recent:", 0) == 0) {
            const std::string path = id.substr(id[0] == 'r' && id[1] == 'o' ? 4 : 7);
            if (mRomIndex.empty()) {
                for (int s = 0; s < (int)mMenu->mXmbSystems.size(); s++)
                    for (const std::string& r : mMenu->mXmbSystems[s].roms) mRomIndex.emplace(r, std::make_pair(s, -1));
            }
            auto it = mRomIndex.find(path);
            if (it == mRomIndex.end()) return false;
            const int s = it->second.first;
            if (!mSystemLevels.count(s)) {
                NanoMenu::Ps3Level lvl;
                mMenu->buildRomSubmenu(s, lvl);
                mSystemLevels[s] = std::move(lvl);
                for (int i = 0; i < (int)mSystemLevels[s].items.size(); i++) {
                    const NanoMenu::Ps3Item& x = mSystemLevels[s].items[i];
                    if (x.kind == NanoMenu::PS3_ROM && x.b >= 0 && x.b < (int)mMenu->mXmbSystems[s].roms.size())
                        mRomIndex[mMenu->mXmbSystems[s].roms[x.b]] = std::make_pair(s, i);
                }
            }
            const auto again = mRomIndex.find(path);
            if (again == mRomIndex.end() || again->second.second < 0) return false;
            *out = mSystemLevels[s].items[again->second.second];
            return true;
        }
        if (id.rfind("app:", 0) == 0) {
            for (const auto& a : mApps)
                if (a.payloadStr == id.substr(4)) { *out = a; return true; }
        }
        return false;
    }

    // The root: nano's Game entries, the user's folders and the titles put on the root, each on
    // its laid out slot; what has no slot yet fills the free ones in nano's order.
    // ---- nano's other categories on the grid (L / R, applet bar buttons 1..5)
    static const char* catName(int c) {
        static const char* const kNames[NanoMenu::CTR_CAT_COUNT] = {"Game", "Quick Menu", "Music", "Video", "Photo", "Network"};
        return c >= 0 && c < NanoMenu::CTR_CAT_COUNT ? kNames[c] : "";
    }
    int catIndex(int c) const {
        for (size_t i = 0; i < mMenu->mPs3Cats.size(); i++)
            if (mMenu->mPs3Cats[i].name == catName(c)) return (int)i;
        return -1;
    }
    std::map<int, int> mCatCursor;   // the root cursor each category was left on
    // The lists the grid shows outside Games, by folder id ("cat:<name>" a category's own), and
    // the list each title came from: a title opens through ps3XmbSelect on its whole list, so a
    // player gets its neighbours as the queue.
    std::map<std::string, std::vector<NanoMenu::Ps3Item>> mLevels;
    std::map<std::string, std::string> mTitleLevel;

    // Folder colours of a category (dark, main): Games keeps the theme's.
    static const float* catColors(int c) {
        static const float kColors[NanoMenu::CTR_CAT_COUNT][6] = {
            {0, 0, 0, 0, 0, 0},
            {0.80f, 0.58f, 0.04f, 0.99f, 0.81f, 0.18f},   // Quick Menu: gold
            {0.78f, 0.38f, 0.06f, 1.00f, 0.62f, 0.20f},   // Music: orange
            {0.45f, 0.28f, 0.70f, 0.70f, 0.50f, 0.95f},   // Video: purple
            {0.22f, 0.55f, 0.20f, 0.45f, 0.82f, 0.40f},   // Photo: green
            {0.72f, 0.20f, 0.32f, 0.98f, 0.45f, 0.55f},   // Network: pink red
        };
        return c > 0 && c < NanoMenu::CTR_CAT_COUNT ? kColors[c] : nullptr;
    }
    void applyCatColors() {
        if (!mMenu->mCtrHome) return;
        const float* c = catColors(mMenu->mCtrCat);
        mMenu->mCtrHome->setFolderColors(c, c ? c + 3 : nullptr);
        mMenu->mCtrHome->setActiveBar(mMenu->mCtrCat);   // categories 1..5 are bar buttons 1..5
    }

    // The categories' pictograms (the RetroArch set nano's other themes use, the XMB globe for
    // Network: Wi-Fi would repeat the status bar): bar buttons 1..5.
    static const char* catIconFile(int c) {
        static const char* const kFiles[NanoMenu::CTR_CAT_COUNT] = {
            nullptr, "icons_retroarch/menu_power.png", "icons_retroarch/music.png", "icons_retroarch/movie.png",
            "icons_retroarch/image.png", "icons/xmb_icon_006.png"};
        return c > 0 && c < NanoMenu::CTR_CAT_COUNT ? kFiles[c] : nullptr;
    }
    static bool loadCatIcon(int c, std::vector<uint8_t>* rgba, int* w, int* h) {
        const char* f = catIconFile(c);
        if (!f) return false;
        for (const char* dir : {"/data/system/nano_xmb/", "/system/etc/nano_xmb/"}) {
            int n = 0;
            unsigned char* px = stbi_load((std::string(dir) + f).c_str(), w, h, &n, 4);
            if (!px) continue;
            rgba->assign(px, px + (size_t)(*w) * (*h) * 4);
            stbi_image_free(px);
            return true;
        }
        ALOGW("ctr: category icon %s missing", f);
        return false;
    }
    void applyBarIcons() {
        if (!mMenu->mCtrHome) return;
        for (int c = 1; c < NanoMenu::CTR_CAT_COUNT; c++) {
            std::vector<uint8_t> px;
            int w = 0, h = 0;
            if (!loadCatIcon(c, &px, &w, &h)) continue;
            const float* col = catColors(c);
            // the ink a shade deeper than the folders' main colour, its light part the main colour
            float ink[3], light[3];
            for (int k = 0; k < 3; k++) {
                ink[k] = col[3 + k] * 0.7f + col[k] * 0.3f;
                light[k] = col[3 + k];
            }
            mMenu->mCtrHome->setBarIcon(c, px.data(), w, h, ink, light);
        }
    }

    void setCategory(int c) {
        if (c == mMenu->mCtrCat || (c != NanoMenu::CTR_CAT_GAMES && catIndex(c) < 0) || !mMenu->mCtrHome) return;
        int zoom = 0, cursor = 0;
        mMenu->mCtrHome->layoutState(&zoom, &cursor);
        mCatCursor[mMenu->mCtrCat] = cursor;
        mMenu->mCtrCat = c;
        mLevels.clear();
        mTitleLevel.clear();
        // nano's actions read the category they run in (Search for Media Servers, Playlists)
        const int ci = catIndex(c);
        if (ci >= 0) mMenu->mPs3CatIdx = ci;
        // the libraries load the first time their category shows (the XMB's column focus)
        if (c == NanoMenu::CTR_CAT_MUSIC) mMenu->musicEnsureLoaded();
        else if (c == NanoMenu::CTR_CAT_VIDEO) mMenu->videoEnsureLoaded();
        else if (c == NanoMenu::CTR_CAT_PHOTO) mMenu->photoEnsureLoaded();
        applyCatColors();
        mMenu->mCtrHome->showCategory(mCatCursor[c]);
        mMenu->mCtrModelSig = mMenu->ctrModelSignature();   // the switch builds the grid itself
        ALOGI("ctr: category %s", catName(c));
    }

    void stepCategory(int dir) override {
        int c = mMenu->mCtrCat;
        for (int n = 0; n < NanoMenu::CTR_CAT_COUNT; n++) {
            c = (c + dir + NanoMenu::CTR_CAT_COUNT) % NanoMenu::CTR_CAT_COUNT;
            if (c == NanoMenu::CTR_CAT_GAMES || catIndex(c) >= 0) break;
        }
        setCategory(c);
    }

    ctr::HomeItem catItem(const NanoMenu::Ps3Item& it, const std::string& level) {
        ctr::HomeItem h;
        h.id = idOf(it);
        h.name = it.label;
        h.monoIcon = it.nmapTex != 0 && !it.flatOwnTint;
        if (mMenu->ctrMediaFolder(it)) {
            h.kind = ctr::HomeItemKind::Folder;
            mFolders[h.id] = it;
        } else {
            h.kind = ctr::HomeItemKind::Title;
            mTitles[h.id] = it;
            mTitleLevel[h.id] = level;
        }
        return h;
    }

    std::vector<ctr::HomeItem> levelItems(const std::string& level, const std::vector<NanoMenu::Ps3Item>& list) {
        mLevels[level] = list;
        std::vector<ctr::HomeItem> out;
        for (const NanoMenu::Ps3Item& it : list) {
            if (it.kind == NanoMenu::PS3_DATA_LEAF && it.label == "Photo Gallery") continue;   // an info row
            out.push_back(catItem(it, level));
        }
        return out;
    }

    std::vector<ctr::HomeItem> rootItems() {
        if (mMenu->mCtrCat != NanoMenu::CTR_CAT_GAMES) {
            const int ci = catIndex(mMenu->mCtrCat);
            if (ci < 0) return {};
            return levelItems(std::string("cat:") + catName(mMenu->mCtrCat), mMenu->mPs3Cats[ci].items);
        }
        loadLayout();
        std::vector<ctr::HomeItem> out;
        const int gc = gameCat();
        if (gc >= 0) out = fromLevel(mMenu->mPs3Cats[gc].items);
        std::set<std::string> have;
        for (const auto& h : out) have.insert(h.id);
        for (const auto& uf : mLayout.folders) {
            ctr::HomeItem h;
            h.kind = ctr::HomeItemKind::Folder;
            h.id = uf.first;
            h.name = uf.second.name;
            h.folderFilled = !uf.second.slots.empty();
            out.push_back(h);
            have.insert(h.id);
        }
        for (const auto& r : mLayout.root) {
            if (have.count(r.second)) continue;
            NanoMenu::Ps3Item it;
            if (!resolve(r.second, &it)) continue;   // gone for now (its card is out): the slot stays reserved
            ctr::HomeItem h = homeItem(it);
            h.id = r.second;
            mTitles[h.id] = it;
            out.push_back(h);
            have.insert(h.id);
        }
        std::map<std::string, int> slotOf;
        std::set<int> used;
        for (const auto& r : mLayout.root)
            if (have.count(r.second) && !slotOf.count(r.second)) { slotOf[r.second] = r.first; used.insert(r.first); }
        int next = 0;
        for (auto& h : out) {
            auto s = slotOf.find(h.id);
            if (s != slotOf.end()) { h.slot = s->second; continue; }
            while (used.count(next)) next++;
            h.slot = next;
            used.insert(next);
        }
        return out;
    }

    std::vector<ctr::HomeItem> userFolderItems(const UserFolder& uf) {
        std::vector<ctr::HomeItem> out;
        for (const auto& s : uf.slots) {
            NanoMenu::Ps3Item it;
            if (!resolve(s.second, &it)) continue;
            ctr::HomeItem h = homeItem(it);
            h.id = s.second;
            mTitles[h.id] = it;
            h.slot = s.first;
            out.push_back(h);
        }
        return out;
    }

    std::vector<ctr::HomeItem> items(const std::string& folderId) override {
        if (folderId.empty()) return rootItems();
        auto u = mLayout.folders.find(folderId);
        if (u != mLayout.folders.end()) return userFolderItems(u->second);
        auto f = mFolders.find(folderId);
        if (f == mFolders.end()) return {};
        const NanoMenu::Ps3Item it = f->second;
        NanoMenu::Ps3Level lvl;
        if (mMenu->mCtrCat != NanoMenu::CTR_CAT_GAMES && mMenu->ctrMediaFolder(it)) {
            mMenu->ctrMediaLevel(it, lvl);
            return levelItems(folderId, lvl.items);
        }
        switch (it.kind) {
            case NanoMenu::PS3_SYSTEM: mMenu->buildRomSubmenu(it.a, lvl); break;
            case NanoMenu::PS3_ROM_FOLDER: lvl.romFolder = it.payloadStr; mMenu->buildRomSubmenu(it.a, lvl); break;
            case NanoMenu::PS3_RECENT_LIST: mMenu->buildRecentSubmenu(lvl); break;
            case NanoMenu::PS3_APP_LIST: mMenu->buildAppSubmenu(lvl); break;
            case NanoMenu::PS3_COLLECTIONS_LIST: mMenu->buildCollectionsSubmenu(lvl); break;
            case NanoMenu::PS3_COLLECTION: mMenu->buildCollectionSubmenu(it.a, lvl); break;
            case NanoMenu::PS3_FAVORITES_LIST: mMenu->buildFavoritesSubmenu(lvl); break;
            case NanoMenu::PS3_PINNED_APPS_LIST: mMenu->buildPinnedAppsSubmenu(lvl); break;
            default: break;
        }
        return fromLevel(lvl.items);
    }

    // ---- arranging
    bool editable(const std::string& folderId) override {
        return mMenu->mCtrCat == NanoMenu::CTR_CAT_GAMES && (folderId.empty() || userFolder(folderId));
    }
    bool userFolder(const std::string& folderId) override { return mLayout.folders.count(folderId) != 0; }

    void layoutChanged(const std::string& folderId, const std::vector<std::pair<int, std::string>>& slots) override {
        std::map<int, std::string>* dst = nullptr;
        if (folderId.empty()) dst = &mLayout.root;
        else if (auto u = mLayout.folders.find(folderId); u != mLayout.folders.end()) dst = &u->second.slots;
        if (!dst) return;
        if (folderId.empty()) {
            // keep the reserved slots of titles that are gone for now (an unplugged card)
            std::set<std::string> shown;
            for (const auto& s : slots) shown.insert(s.second);
            std::map<int, std::string> keep;
            for (const auto& r : *dst) {
                if (shown.count(r.second)) continue;
                NanoMenu::Ps3Item it;
                if (r.second.rfind("uf:", 0) != 0 && !resolve(r.second, &it)) keep.insert(r);
            }
            *dst = keep;
        } else {
            dst->clear();
        }
        for (const auto& s : slots) (*dst)[s.first] = s.second;
        saveLayout();
    }

    std::string createFolder(int slot, const std::string& name) override {
        loadLayout();
        if ((int)mLayout.folders.size() >= kMaxFolders) return std::string();
        int n = 1;
        while (mLayout.folders.count("uf:" + std::to_string(n))) n++;
        const std::string id = "uf:" + std::to_string(n);
        std::string label = name;
        const size_t pct = label.find("%d");
        if (pct != std::string::npos) label.replace(pct, 2, std::to_string(n));
        mLayout.folders[id].name = label;
        mLayout.root[slot] = id;
        saveLayout();
        return id;
    }

    void folderSettings(const std::string& folderId) override { mMenu->ctrFolderSettings(folderId); }
    void notice(const std::string& label, const std::string& text) override {
        (void)label;
        if (!text.empty()) mMenu->feInfoDialog(std::string(), text);
    }

    void renameFolder(const std::string& id, const std::string& name) {
        auto u = mLayout.folders.find(id);
        if (u == mLayout.folders.end() || name.empty()) return;
        u->second.name = name;
        saveLayout();
    }

    // The folder's titles go back onto free root slots (the console empties a deleted folder).
    void deleteFolder(const std::string& id) {
        auto u = mLayout.folders.find(id);
        if (u == mLayout.folders.end()) return;
        int folderSlot = -1;
        for (auto it = mLayout.root.begin(); it != mLayout.root.end(); ++it)
            if (it->second == id) { folderSlot = it->first; mLayout.root.erase(it); break; }
        std::set<int> used;
        for (const auto& r : mLayout.root) used.insert(r.first);
        int next = folderSlot >= 0 ? folderSlot : 0;
        for (const auto& s : u->second.slots) {
            while (used.count(next)) next++;
            mLayout.root[next] = s.second;
            used.insert(next);
        }
        mLayout.folders.erase(u);
        saveLayout();
    }

    bool titleIcon(const ctr::HomeItem& item, std::vector<uint8_t>* rgba, int* w, int* h) override {
        auto t = mTitles.find(item.id);
        if (t == mTitles.end()) return false;
        const NanoMenu::Ps3Item& it = t->second;
        if (it.kind == NanoMenu::PS3_APP && !it.payloadStr.empty()) {
            const std::string path = "/data/system/nano_app_icons/" + it.payloadStr + ".png";
            int c = 0;
            unsigned char* px = stbi_load(path.c_str(), w, h, &c, 4);
            if (!px) return false;
            rgba->assign(px, px + (size_t)(*w) * (*h) * 4);
            stbi_image_free(px);
            return true;
        }
        return false;
    }

    // The ROM a grid title stands for ("" for apps and other entries).
    std::string romPathOf(const NanoMenu::Ps3Item& it) {
        if (it.kind == NanoMenu::PS3_ROM && it.a >= 0 && it.a < (int)mMenu->mXmbSystems.size()) {
            const auto& sys = mMenu->mXmbSystems[it.a];
            if (it.b >= 0 && it.b < (int)sys.roms.size()) return sys.roms[it.b];
        } else if (it.kind == NanoMenu::PS3_RECENT && it.a >= 0 && it.a < (int)mMenu->mXmbRecent.size()) {
            return mMenu->mXmbRecent[it.a].romPath;
        }
        return std::string();
    }

    // A title's own art: the SMDH icon of a 3DS ROM, the banner icon of a DS ROM, else the
    // scraped cover (when Boxart is on); other entries nano's system glyph.
    GLuint titleIconTexture(const ctr::HomeItem& item, int* w, int* h, IconArt* art) override {
        auto t = mTitles.find(item.id);
        if (t == mTitles.end()) {
            // a game system's folder shows the system's icon instead of its initial, and so do the
            // root's lists (the glyphs the other themes show for them: Recently Played the clock,
            // Applications the app grid, Pinned Apps the pin, Collections, Favorites the heart)
            auto f = mFolders.find(item.id);
            if (f == mFolders.end() || !f->second.iconTex) return 0;
            switch (f->second.kind) {
                case NanoMenu::PS3_SYSTEM:
                case NanoMenu::PS3_RECENT_LIST:
                case NanoMenu::PS3_APP_LIST:
                case NanoMenu::PS3_PINNED_APPS_LIST:
                case NanoMenu::PS3_COLLECTIONS_LIST:
                case NanoMenu::PS3_FAVORITES_LIST:
                // the categories' lists of their own (IPTV, Internet Radio, Playlists, Power)
                case NanoMenu::PS3_DATA_LEAF:
                case NanoMenu::PS3_QUICK:
                    break;
                default:
                    return 0;   // a ROM folder, a user folder or an album keeps the folder look
            }
            *w = *h = 48;
            art->mono = item.monoIcon;
            return f->second.iconTex;
        }
        const NanoMenu::Ps3Item& it = t->second;
        if (it.kind == NanoMenu::PS3_APP) return 0;   // decoded from the icon cache (titleIcon)
        const std::string rom = romPathOf(it);
        if (!rom.empty()) {
            if (mMenu->mCtrArt && ctr::RomArt::isCtrRom(rom)) {
                if (GLuint g = mMenu->mCtrArt->icon(rom)) { *w = *h = 48; return g; }
            }
            std::string ds;
            if (mMenu->ndsIsDsRomItem(it, &ds)) {
                if (GLuint g = mMenu->ndsBannerTex(ds)) { *w = *h = 32; art->pixelArt = true; return g; }
            }
            if (mMenu->mScrapeBoxartOn) {
                float ar = 1.0f;
                if (GLuint g = mMenu->romBoxartTex(rom, &ar)) {
                    *w = *h = 256;
                    art->coverAspect = ar > 0 ? ar : 1.0f;
                    return g;
                }
            }
            return 0;   // a game without art: the home draws the generic box with its initial
        }
        if (it.iconTex) {
            *w = *h = 48;
            art->mono = item.monoIcon;
            return it.iconTex;
        }
        return 0;
    }
    uint64_t artGeneration() override { return mMenu->mArtGeneration; }

    void titleArt(const ctr::HomeItem& item, TitleArt* out) override {
        auto t = mTitles.find(item.id);
        if (t == mTitles.end()) return;
        const NanoMenu::Ps3Item& it = t->second;
        const std::string rom = romPathOf(it);
        if (rom.empty()) return;
        if (mMenu->mCtrArt && ctr::RomArt::isCtrRom(rom)) {
            out->cbmd = mMenu->mCtrArt->banner(rom, &out->pending);
            if (out->cbmd || out->pending) return;
            // a 3DS title without a banner: its icon on the card
            if (GLuint g = mMenu->mCtrArt->icon(rom)) { out->icon = g; out->w = out->h = 48; }
            return;
        }
        std::string ds;
        if (mMenu->ndsIsDsRomItem(it, &ds)) {
            out->dsCard = true;
            if (GLuint g = mMenu->ndsBannerTex(ds)) { out->icon = g; out->w = out->h = 32; out->dsIcon = true; }
            return;
        }
        if (mMenu->mScrapeBoxartOn) {
            float ar = 1.0f;
            if (GLuint g = mMenu->romBoxartTex(rom, &ar)) {
                out->icon = g;
                out->w = out->h = 256;
                out->art.coverAspect = ar > 0 ? ar : 1.0f;
            }
        }
    }

    // A 3DS banner's BCWAV plays once when the banner shows; leaving the title stops it.
    void playBannerSound(const std::shared_ptr<const ctr::RomBanner>& b) override {
        stopBannerSound();
        if (!mMenu->mCtrAudio || !b || !b->hasSound || b->sound.pcm.empty()) return;
        char v[PROPERTY_VALUE_MAX] = {};
        property_get("persist.gammaos.nano.nav_sounds", v, "1");
        if (v[0] == '0' || v[0] == 'f' || v[0] == 'F') return;
        const ctr::RomSound& s = b->sound;
        // banner BCWAVs are mastered at full level: no makeup gain (the effects get 2x)
        mBannerVoice = mMenu->mCtrAudio->playPcm(b, s.pcm.data(), s.frames, s.channels, s.sampleRate, false, 0, 0, 0.5f);
    }
    void stopBannerSound() override {
        if (mBannerVoice && mMenu->mCtrAudio) mMenu->mCtrAudio->stop(mBannerVoice, 10);
        mBannerVoice = 0;
    }
    int mBannerVoice = 0;

    const NanoMenu::Ps3Item* titleOf(const std::string& id) const {
        auto t = mTitles.find(id);
        return t == mTitles.end() ? nullptr : &t->second;
    }

    // A title of a category's content: ps3XmbSelect on the list it was shown in.
    bool openInHost(const ctr::HomeItem& item) override {
        auto lv = mTitleLevel.find(item.id);
        if (lv == mTitleLevel.end()) return false;
        auto l = mLevels.find(lv->second);
        if (l == mLevels.end()) return false;
        const std::vector<NanoMenu::Ps3Item>& list = l->second;
        int sel = -1;
        for (int i = 0; i < (int)list.size() && sel < 0; i++)
            if (idOf(list[i]) == item.id) sel = i;
        if (sel < 0) return false;
        // a status row ("Loading channel list...", "No stations") does nothing
        if (list[sel].kind == NanoMenu::PS3_GS_FIELD && list[sel].a < 0) return true;
        std::string title = catName(mMenu->mCtrCat);
        if (auto f = mFolders.find(lv->second); f != mFolders.end()) title = f->second.label;
        mMenu->ctrOpenInLevel(title, list, sel);
        return true;
    }

    bool launch(const ctr::HomeItem& item) override {
        auto t = mTitles.find(item.id);
        if (t == mTitles.end()) return false;
        return mMenu->ctrLaunchItem(t->second);
    }

    // Settings opens nano's Settings list; the others show their category on the grid (again:
    // back to Games).
    void appletBar(int button) override {
        if (button <= 0 || button >= NanoMenu::CTR_CAT_COUNT || catIndex(button) < 0) {
            mMenu->ctrAppletBar(button);
            return;
        }
        setCategory(mMenu->mCtrCat == button ? NanoMenu::CTR_CAT_GAMES : button);
    }

    void backAtRoot() override {
        if (mMenu->mOverlayMode) mMenu->overlayResume();
    }

    // The fade back into the suspended title has played: the overlay goes, the title has the
    // screens again.
    void resumeApp() override {
        mMenu->mCtrResumeNow = true;
        mMenu->overlayResume();
        mMenu->mCtrResumeNow = false;
    }

    // Close was confirmed on the suspended title: end it (the closing notice plays meanwhile and
    // the overlay becomes the launcher).
    void closeApp() override {
        if (mMenu->mOverlayMode) mMenu->overlayQuitToHome();
    }
    // A game system's icon tint (Game Systems > the system > Icon Colour): its solid icon's colour.
    // White is the default "no tint".
    bool itemTint(const ctr::HomeItem& item, float rgb[3]) override {
        auto f = mFolders.find(item.id);
        if (f == mFolders.end() || f->second.kind != NanoMenu::PS3_SYSTEM) return false;
        const NanoMenu::Ps3Item& it = f->second;
        if (it.iconR >= 0.999f && it.iconG >= 0.999f && it.iconB >= 0.999f) return false;
        rgb[0] = it.iconR;
        rgb[1] = it.iconG;
        rgb[2] = it.iconB;
        return true;
    }
    // Enlarge / Shrink Icons: kept for the next session (ctrEnsure applies them)
    void zoomChanged(bool folder, int zoom) override {
        property_set(folder ? "persist.gammaos.nano.ctr.folder_zoom" : "persist.gammaos.nano.ctr.zoom",
                     std::to_string(zoom).c_str());
    }

    std::string appletName(int button) override {
        return button >= 1 && button < NanoMenu::CTR_CAT_COUNT ? std::string(trDyn(catName(button))) : std::string();
    }

    // Menu sounds: effects follow Nav Sounds, the BGM its own toggle (Theme Settings).
    int playSound(const char* name) override {
        if (!mMenu->mCtrAudio) return 0;
        const bool bgm = !strncmp(name, "BGM_", 4);
        char v[PROPERTY_VALUE_MAX] = {};
        // the BGM follows Theme Settings > Menu Music (shared with the DSi theme's music)
        property_get(bgm ? "persist.gammaos.nano.nds.ambiance" : "persist.gammaos.nano.nav_sounds", v, "1");
        if (v[0] == '0' || v[0] == 'f' || v[0] == 'F') return 0;
        // no BGM while a game starts or runs, over a live app, or with a media player
        if (bgm && !mMenu->ctrBgmAllowed()) return 0;
        return mMenu->mCtrAudio->play(name);
    }
    bool soundPlaying(int handle) override {
        return mMenu->mCtrAudio && mMenu->mCtrAudio->playing(handle);
    }
    void stopSound(int handle, int fadeFrames) override {
        if (mMenu->mCtrAudio && handle) mMenu->mCtrAudio->stop(handle, fadeFrames);
    }

    ctr::HomeStatus status() override {
        ctr::HomeStatus s;
        s.wifiConnected = mMenu->mWifiLevel == NanoMenu::kWifiLevel_Connected;
        s.wifiSignal = mMenu->mWifiLevel == NanoMenu::kWifiLevel_Off ? -1
                     : s.wifiConnected ? std::min(3, std::max(0, mMenu->mWifiBars - 1)) : 0;
        const int pct = mMenu->mBatteryPercent;
        s.batteryLevel = pct < 0 ? 5 : pct <= 5 ? 0 : pct <= 10 ? 1 : pct <= 25 ? 2 : pct <= 50 ? 3 : pct <= 75 ? 4 : 5;
        s.adapter = mMenu->mBatteryCharging;
        s.charging = mMenu->mBatteryCharging && pct < 100;
        return s;
    }

private:
    NanoMenu* mMenu;
};

// ---------------------------------------------------------------------------------------- setup

static void ctrRestoreReturn(ctr::CtrHome* home);   // below, next to ctrLaunchItem

bool NanoMenu::ctrEnsure() {
    if (mCtrHome && mCtrHome->loaded()) return true;
    if (!mCtrHome) {
        std::string root;
        for (const char* d : kCtrAssetDirs) {
            struct stat st;
            if (stat((std::string(d) + "/lyt_launcher.cpk").c_str(), &st) == 0) { root = d; break; }
        }
        if (root.empty()) {
            static bool warned = false;
            if (!warned) { warned = true; ALOGE("ctr: 3DS theme assets not found"); }
            return false;
        }
        if (!mCtrHost) mCtrHost = new NanoCtrHost(this);
        if (!mCtrAudio) mCtrAudio = new ctr::CtrAudio(root + "/sound");
        gNanoCtrAudio = mCtrAudio;
        if (!mCtrArt) mCtrArt = new ctr::RomArt("/data/system/nano_ctr_rom");
        mCtrHome = new ctr::CtrHome(root, mCtrHost);
        ctrShuffleEvent(true, 0);   // Shuffle: the next favourite once per boot
        ALOGI("ctr: 3DS theme assets at %s", root.c_str());
    }
    mCtrHome->setLanguage("English");
    {
        // text fields and the top screen's rebuilt icons follow the panel's magnification of the
        // 400 x 240 top screen (known before the layouts load; ctrDrawScreen refines it)
        const float W = (float)mWidth, H = ctrDualPanel() ? (float)mHeight : mHeight * 0.5f;
        if (W > 0 && H > 0) ctr::Font::setVectorScale((int)ceilf(std::min(W / 400.0f, H / 240.0f) - 0.1f));
        ctr::Font::setGlyphCacheDir("/data/system/nano_cache/ctr_glyphs");
        ctr::setProgramCacheDir("/data/system/nano_cache/ctr_shaders");
    }
    if (!mCtrHome->load()) {
        ALOGE("ctr: 3DS theme failed to load");
        return false;
    }
    // the home just loaded: the user's zoom, then back on the title the last session launched
    mCtrHome->setZooms(property_get_int32("persist.gammaos.nano.ctr.zoom", 0),
                       property_get_int32("persist.gammaos.nano.ctr.folder_zoom", 2));
    ctrRestoreReturn(mCtrHome);
    mCtrHost->applyCatColors();
    mCtrHost->applyBarIcons();
    if (!mCtrPanel) {
        auto sound = [this](const char* name) { if (mCtrHost) mCtrHost->playSound(name); };
        mCtrPanel = new ctr::CtrPanel(mCtrHome->assets(), sound);
        mCtrDialog = new ctr::CtrDialog(mCtrHome->assets(), sound);
        mCtrHome->overlay = [this](ctr::Renderer& r, bool top) {
            if (top) { mCtrPanel->drawTop(r); mCtrDialog->drawTop(r); }
            else { mCtrPanel->drawBottom(r); mCtrDialog->drawBottom(r); }
        };
        mCtrHome->onTick = [this]() { mCtrPanel->tick(); mCtrDialog->tick(); };
        // a theme's animated bottom image plays its frame sound on each new frame (background.ts)
        mCtrHome->onBackgroundFrame = [this](int phase) {
            if (mCtrHost) mCtrHost->playSound(("THEME_FRAME" + std::to_string(phase)).c_str());
        };
    }
    return true;
}

void NanoMenu::ctrRelease() {
    if (mCtrArt) { mCtrArt->releaseGl(); mArtGeneration++; }
    for (CtrWallpaper& t : mCtrWp) {
        if (t.fbo) glDeleteFramebuffers(1, &t.fbo);
        if (t.tex) glDeleteTextures(1, &t.tex);
        t = CtrWallpaper();
    }
    if (mCtrPanel) { mCtrPanel->releaseGl(); delete mCtrPanel; mCtrPanel = nullptr; }
    if (mCtrDialog) { mCtrDialog->releaseGl(); delete mCtrDialog; mCtrDialog = nullptr; }
    if (mCtrHome) {
        mCtrHome->releaseGl();
        delete mCtrHome;
        mCtrHome = nullptr;
    }
}

// ---------------------------------------------------------------------------------------- actions

// The home is a new process after most games and apps (azahar-nano, retroarch, an Android app):
// the grid path to what was launched (the folders open, then the title) is kept in a file the next
// home start reads once, like the DSi theme's sys.gammaos.nano.nds_return. ROM paths are too long
// for a property; the property only says the file is from this boot.
static const char* kCtrReturnPath = "/data/system/nano_ctr_return.txt";

static void ctrSaveReturn(ctr::CtrHome* home) {
    if (!home) return;
    const std::vector<std::string> ids = home->focusPath();
    if (ids.empty()) return;
    const std::string tmp = std::string(kCtrReturnPath) + ".tmp";
    FILE* f = fopen(tmp.c_str(), "w");
    if (!f) return;
    for (const std::string& id : ids) fprintf(f, "%s\n", id.c_str());
    fflush(f);
    fsync(fileno(f));
    fclose(f);
    if (rename(tmp.c_str(), kCtrReturnPath) == 0) property_set("sys.gammaos.nano.ctr.return", "1");
}

// Once, on the first load of a home process: back on what the last session launched.
static void ctrRestoreReturn(ctr::CtrHome* home) {
    if (!property_get_bool("sys.gammaos.nano.ctr.return", false)) return;
    property_set("sys.gammaos.nano.ctr.return", "0");
    std::vector<std::string> ids;
    if (FILE* f = fopen(kCtrReturnPath, "r")) {
        char line[4096];
        while (fgets(line, sizeof(line), f)) {
            size_t n = strlen(line);
            while (n && (line[n - 1] == '\n' || line[n - 1] == '\r')) line[--n] = 0;
            if (n) ids.emplace_back(line);
        }
        fclose(f);
    }
    unlink(kCtrReturnPath);
    if (!ids.empty()) {
        home->restorePath(ids);
        ALOGI("ctr: back on %s (%zu levels)", ids.back().c_str(), ids.size());
    }
}

bool NanoMenu::ctrLaunchItem(const Ps3Item& it) {
    switch (it.kind) {
        case PS3_ROM:
        case PS3_RECENT: {
            if (!isLaunchReady()) { showLaunchBusyToast(); return false; }
            ctrSaveReturn(mCtrHome);
            mXmbSystemIndex = it.kind == PS3_ROM ? it.a : -1;
            mXmbGameIndex = it.kind == PS3_ROM ? it.b : it.a;
            mSearchActive = false;
            if (mOverlayMode) { overlayLaunchGame(); return true; }
            launchXmbGame();
            // the 3DS fade already covers the screen: hand off once it is black (launchFadeHoldMs)
            if (mWaitForRelease && mLaunchFadeStart == 0) mLaunchFadeStart = uptimeMillis();
            return true;
        }
        case PS3_APP:
        case PS3_LAUNCH_PKG: {
            if (it.payloadStr.empty()) return false;
            ctrSaveReturn(mCtrHome);
            if (mOverlayMode) { overlayLaunchPackage(it.payloadStr); return true; }
            if (!isLaunchReady()) { showLaunchBusyToast(); return false; }
            property_set("sys.gammaos.nano.launch_app", it.payloadStr.c_str());
            property_set("sys.gammaos.nano.launched_pkg", it.payloadStr.c_str());
            setLaunchRomPath("");
            property_set("sys.gammaos.nano.launch_core", "");
            property_set("persist.gammaos.nano.qr_prepared", "0");
            property_set("persist.gammaos.nano.qr_core", "");
            property_set("sys.gammaos.nano.return_apps", "1");
            armAppLaunchTrigger();
            property_set("sys.gammaos.nano.drop_input", "1");
            mWaitForRelease = true;
            if (mLaunchFadeStart == 0) mLaunchFadeStart = uptimeMillis();
            return true;
        }
        default: {
            // anything else (a settings or media entry on the grid) opens as a nano list
            Ps3Level lvl;
            lvl.title = it.label;
            lvl.items.push_back(it);
            lvl.sel = 0;
            mPs3Stack.push_back(lvl);
            ps3XmbSelect();
            if (mPs3Stack.size() >= 2) mPs3Stack.erase(mPs3Stack.end() - 2);
            else mPs3Stack.clear();
            mDisplayDirty = true;
            return false;
        }
    }
}

// ---------------------------------------------------------------------------------------- categories

// What a category's grid shows as a folder: albums, playlists, IPTV / radio groups, the Playlists
// screens, IPTV and Internet Radio once their notice was accepted (before that they are titles
// that show it), the Quick Menu's Power.
bool NanoMenu::ctrMediaFolder(const Ps3Item& it) {
    switch (it.kind) {
        case PS3_MUSIC_ALBUM: case PS3_MUSIC_PLAYLIST: case PS3_VIDEO_FOLDER: case PS3_VIDEO_PLAYLIST:
        case PS3_IPTV_GROUP: case PS3_IPTV_COUNTRY: case PS3_RADIO_GROUP: case PS3_RADIO_BUCKET:
            return true;
        case PS3_QUICK:
            return ctrQuickSubmenu(it, nullptr);
        case PS3_DATA_LEAF:
            if (it.label == "Playlists")
                return mCtrCat == CTR_CAT_MUSIC || mCtrCat == CTR_CAT_VIDEO || mCtrCat == CTR_CAT_PHOTO;
            if (it.label == "IPTV") return property_get_bool("persist.gammaos.nano.iptv.agreed", false);
            if (it.label == "Internet Radio") return property_get_bool("persist.gammaos.nano.radio.agreed", false);
            return false;
        default:
            return false;
    }
}

// A category folder's content: the list ps3XmbSelect would open for it.
void NanoMenu::ctrMediaLevel(const Ps3Item& it, Ps3Level& out) {
    switch (it.kind) {
        case PS3_MUSIC_ALBUM: buildMusicAlbumSubmenu(it.a, out); return;
        case PS3_MUSIC_PLAYLIST: buildMusicPlaylistSubmenu(it.a, out); return;
        case PS3_VIDEO_FOLDER: buildVideoFolderSubmenu(it.payloadStr, out); return;
        case PS3_VIDEO_PLAYLIST: buildVideoPlaylistSubmenu(it.a, out); return;
        case PS3_IPTV_GROUP: {   // a single country goes straight to its channels
            int countries;
            {
                std::lock_guard<std::mutex> lk(mIptvMutex);
                countries = it.a >= 0 && it.a < (int)mIptvCats.size() ? (int)mIptvCats[it.a].countries.size() : 0;
            }
            if (countries <= 1) buildIptvChannelSubmenu(it.a, 0, out);
            else buildIptvCountrySubmenu(it.a, out);
            return;
        }
        case PS3_IPTV_COUNTRY: buildIptvChannelSubmenu(it.a, it.b, out); return;
        case PS3_RADIO_GROUP: {   // a single bucket goes straight to its stations
            int buckets;
            {
                std::lock_guard<std::mutex> lk(mRadioMutex);
                buckets = it.a >= 0 && it.a < (int)mRadioCats.size() ? (int)mRadioCats[it.a].buckets.size() : 0;
            }
            if (buckets <= 1) buildRadioStationSubmenu(it.a, 0, out);
            else buildRadioBucketSubmenu(it.a, out);
            return;
        }
        case PS3_RADIO_BUCKET: buildRadioStationSubmenu(it.a, it.b, out); return;
        case PS3_QUICK: ctrQuickSubmenu(it, &out); return;
        case PS3_DATA_LEAF:
            if (it.label == "IPTV") {
                iptvEnsureLoaded();
                buildIptvCategoriesScreen(out);
            } else if (it.label == "Internet Radio") {
                radioEnsureLoaded();
                buildRadioRootScreen(out);
            } else if (it.label == "Playlists") {
                if (mCtrCat == CTR_CAT_MUSIC) { musicEnsureLoaded(); buildMusicPlaylistsScreen(out); }
                else if (mCtrCat == CTR_CAT_VIDEO) { videoEnsureLoaded(); buildVideoPlaylistsScreen(out); }
                else if (mCtrCat == CTR_CAT_PHOTO) { photoEnsureLoaded(); buildPhotoPlaylistsScreen(out); }
            }
            return;
        default:
            return;
    }
}

// Opens a category title the way the XMB does: its list as the current level with the title
// selected, then ps3XmbSelect. A list it opens stays (alone) over the home; a player or a dialog
// leaves the stack empty again.
void NanoMenu::ctrOpenInLevel(const std::string& title, const std::vector<Ps3Item>& items, int sel) {
    Ps3Level lvl;
    lvl.title = title;
    lvl.items = items;
    lvl.sel = sel;
    mPs3Stack.push_back(std::move(lvl));
    const size_t depth = mPs3Stack.size();
    ps3XmbSelect();
    if (mPs3Stack.size() > depth) mPs3Stack.erase(mPs3Stack.begin() + (depth - 1));
    else if (mPs3Stack.size() == depth) mPs3Stack.pop_back();
    mDisplayDirty = true;
}

// Applet bar button 0 (HOME Menu Settings): nano's Settings as a list over the home. Buttons 1..5
// switch the grid's category (NanoCtrHost::appletBar); a category missing from the home (hidden in
// Home Categories) opens here as a list instead.
void NanoMenu::ctrAppletBar(int button) {
    static const char* kCats[6] = {"Settings", "Quick Menu", "Music", "Video", "Photo", "Network"};
    if (button < 0 || button > 5) return;
    for (size_t i = 0; i < mPs3Cats.size(); i++) {
        if (mPs3Cats[i].name != kCats[button]) continue;
        Ps3Level lvl;
        lvl.title = mPs3Cats[i].name;
        lvl.items = mPs3Cats[i].items;
        lvl.sel = 0;
        mPs3Stack.push_back(lvl);
        mDisplayDirty = true;
        return;
    }
}

// ---------------------------------------------------------------------------------------- input

bool NanoMenu::ctrOwnsInput() const {
    return mCtrTheme && mPs3Xmb && mCtrHome && mCtrHome->loaded() && !mPs3BootActive && mPs3Stack.empty() &&
           !ndsInModal() && !mSetupWizardActive && !mPs3WizActive && !(mPspClockOn || mPspClockReveal > 0.0f);
}

bool NanoMenu::ctrInputEvent(int type, int code, int value) {
    // the events taken here skip nano's own input path: they still count as input for the idle
    // frame governor (else a minute after the last touch the home drops to the idle rate)
    auto taken = [this]() { mLastInputMs = (int64_t)android::uptimeMillis(); return true; };
    if (ctrSwitchKey(type, code, value)) return taken();
    if (!ctrOwnsInput()) return false;
    auto setBit = [&](uint32_t bit, bool on) {
        if (on) mCtrHeld |= bit; else mCtrHeld &= ~bit;
    };
    if (type == EV_KEY) {
        if (value == 2) return taken();   // key repeat: the menu repeats on its own
        const bool on = value != 0;
        switch (code) {
            case BTN_SOUTH: case KEY_ENTER: setBit(ctr::CTR_A, on); return taken();
            case BTN_EAST: case KEY_BACK: case KEY_ESC: setBit(ctr::CTR_B, on); return taken();
            case BTN_NORTH: setBit(ctr::CTR_X, on); return taken();
            case BTN_WEST: setBit(ctr::CTR_Y, on); return taken();
            case BTN_TL: case KEY_L: setBit(ctr::CTR_L, on); return taken();
            case BTN_TR: case KEY_R: setBit(ctr::CTR_R, on); return taken();
            case BTN_TL2: setBit(ctr::CTR_ZL, on); return taken();
            case BTN_TR2: setBit(ctr::CTR_ZR, on); return taken();
            case BTN_DPAD_UP: case KEY_UP: setBit(ctr::CTR_UP, on); return taken();
            case BTN_DPAD_DOWN: case KEY_DOWN: setBit(ctr::CTR_DOWN, on); return taken();
            case BTN_DPAD_LEFT: case KEY_LEFT: setBit(ctr::CTR_LEFT, on); return taken();
            case BTN_DPAD_RIGHT: case KEY_RIGHT: setBit(ctr::CTR_RIGHT, on); return taken();
            case BTN_START:
                // the focused title's options (Information, collections, ...) like the other themes'
                if (on && ctrOpenOptions()) return taken();
                return false;
            default: return false;   // SELECT / system keys stay with nano
        }
    }
    if (type == EV_ABS) {
        if (code == ABS_HAT0X) {
            setBit(ctr::CTR_LEFT, value < 0);
            setBit(ctr::CTR_RIGHT, value > 0);
            return taken();
        }
        if (code == ABS_HAT0Y) {
            setBit(ctr::CTR_UP, value < 0);
            setBit(ctr::CTR_DOWN, value > 0);
            return taken();
        }
        if (code == ABS_X || code == ABS_Y) {
            // circle pad as the D-pad. The console engages past 40 of 156 (input.ts), but handheld
            // sticks rest off centre: the Duo Lite's left stick sat at 575 of 850 untouched, which
            // read as Right held (the cursor walked by itself and a bar button never took its
            // second tap). Like nano's own stick navigation, a direction needs 90% of the range
            // and lets go under 50%. Only signed (centred) axes are sticks; a touchscreen's
            // ABS_X/Y run from 0 and are left to the touch path.
            struct input_absinfo a{};
            int lo = -32768, hi = 32767;
            if (ioctl(mCurrentInputFd, EVIOCGABS(code), &a) == 0 && a.maximum > a.minimum) {
                if (a.minimum >= 0) return false;
                lo = a.minimum;
                hi = a.maximum;
            }
            const float mid = (lo + hi) * 0.5f, half = (hi - lo) * 0.5f;
            const float v = half > 0 ? (value - mid) / half : 0.0f;
            int& axis = code == ABS_X ? mCtrStickX : mCtrStickY;
            if (v >= 0.9f) axis = 1;
            else if (v <= -0.9f) axis = -1;
            else if (fabsf(v) < 0.5f) axis = 0;   // between 50% and 90% a held direction stays
            return taken();
        }
    }
    return false;
}

// A touch frame: nano has the raw touch state (mTouchDown / mTouchRawX/Y); map it into bottom
// screen layout space through the rectangle the bottom screen is drawn in.
void NanoMenu::ctrTouchFrame() {
    if (mOskActive || (mGSearchActive && !ctrSearchThemed())) return;   // nano's own touch handling has them
    const bool modal = ctrModalKind() == CTR_MODAL_PANEL;
    if (!ctrOwnsInput() && !modal) return;
    float px = 0, py = 0;
    if (!touchMapRaw(mTouchRawX, mTouchRawY, px, py)) return;
    float rx, ry, rw, rh;
    ctrBottomRect(rx, ry, rw, rh);
    if (ctrDualPanel()) {
        // touchMapRaw scales the (calibrated) digitizer to the primary panel's logical size; the
        // bottom screen lives on its own panel, whose rectangle is in that panel's pixels
        float bw = (float)mWidth, bh = (float)mHeight;
        if (sAhbTargetSecondary.glFbo) {
            bw = (float)sAhbTargetSecondary.w;
            bh = (float)sAhbTargetSecondary.h;
        } else if (!mSecondaryCreatedSize.empty() && mSecondaryCreatedSize[0].first > 0) {
            bw = (float)mSecondaryCreatedSize[0].first;
            bh = (float)mSecondaryCreatedSize[0].second;
        }
        px = px / (float)mWidth * bw;
        py = py / (float)mHeight * bh;
    }
    ctr::CtrTouch t;
    t.held = mTouchDown;
    const float lw = mCtrHome->bottomWidth();
    t.x = (px - rx) / rw * lw - lw / 2;
    t.y = 120.0f - (py - ry) / rh * 240.0f;
    if (modal) {
        ctrModalTouch(t.x, t.y);
        return;
    }
    mCtrTouchPrev = false;
    mCtrHome->setTouch(t);
}

// ---------------------------------------------------------------------------- themes

namespace {
const char* kCtrThemeRoot = "/sdcard/Themes";

// The themes on the SD card, listed again at most every 2 s (the settings rows ask every frame).
const std::vector<ctr::ThemeFile>& ctrSdThemes() {
    static std::vector<ctr::ThemeFile> list;
    static int64_t at = -1;
    const int64_t now = (int64_t)android::uptimeMillis();
    if (at < 0 || now - at > 2000) {
        list = ctr::listSdThemes(kCtrThemeRoot);
        at = now;
    }
    return list;
}
}  // namespace

std::vector<SettingListOption> ctrThemeOptions() {
    std::vector<SettingListOption> out = {{"-1", "Default"}, {"shuffle", "Shuffle"}, {"0", "Red"}, {"1", "Blue"},
                                          {"2", "Yellow"}, {"3", "Pink"}, {"4", "Black"}};
    const std::string lang = nanoGetLocaleInfo(nanoGetLocale()).englishName;
    for (const ctr::ThemeFile& t : ctrSdThemes()) out.push_back({"sd/" + t.name, t.displayName(lang)});
    return out;
}

// Theme Settings > 3DS Theme (persist.gammaos.nano.ctr.theme): Default, a built-in colour theme
// or a theme from the SD card. Default follows Theme Settings > Colour: any colour but Original
// colours the menu with a theme made from it. Resolved again when either setting changes and for
// a new home (GL context loss), which starts on the default look.
// ---------------------------------------------------------------------------------- shuffle
// theme/shuffle.ts: up to ten favourites play in a shuffled order; past the last one the order is
// shuffled again (Fisher-Yates) so that the theme just shown does not come first. The order, the
// position in it and the current favourite are kept in properties so a restart continues.
namespace {
const char* kShuffleFavs = "persist.gammaos.nano.ctr.shuffle_favs";
const char* kShuffleOrder = "persist.gammaos.nano.ctr.shuffle_order";
const char* kShufflePos = "persist.gammaos.nano.ctr.shuffle_pos";
const char* kShuffleCur = "persist.gammaos.nano.ctr.shuffle_cur";
const int kShuffleMax = 10;

std::vector<std::string> ctrSplit(const char* prop) {
    char v[PROPERTY_VALUE_MAX] = {};
    property_get(prop, v, "");
    std::vector<std::string> out;
    std::string cur;
    for (const char* c = v; ; c++) {
        if (*c == ',' || !*c) { if (!cur.empty()) out.push_back(cur); cur.clear(); if (!*c) break; }
        else cur += *c;
    }
    return out;
}

std::string ctrJoin(const std::vector<std::string>& v) {
    std::string s;
    for (const std::string& e : v) s += (s.empty() ? "" : ",") + e;
    return s;
}
}  // namespace

bool NanoMenu::ctrShuffleToggle(const std::string& key) {
    std::vector<std::string> favs = ctrSplit(kShuffleFavs);
    auto at = std::find(favs.begin(), favs.end(), key);
    if (at != favs.end()) favs.erase(at);
    else if ((int)favs.size() >= kShuffleMax) return false;   // the console's ten (CHECK_BTN_INVALID)
    else favs.push_back(key);
    // a property value holds 91 bytes: long theme folder names cannot all fit
    std::string joined = ctrJoin(favs);
    if (joined.size() >= PROPERTY_VALUE_MAX) return false;
    property_set(kShuffleFavs, joined.c_str());
    property_set(kShuffleOrder, "");   // a new list starts a new order (FUN_001fbe5c on OK)
    property_set(kShufflePos, "-1");
    // with Shuffle on, a favourite taken away (or the first one added) changes the theme now
    char t[PROPERTY_VALUE_MAX] = {};
    property_get("persist.gammaos.nano.ctr.theme", t, "-1");
    if (!strcmp(t, "shuffle")) {
        char cur[PROPERTY_VALUE_MAX] = {};
        property_get(kShuffleCur, cur, "");
        if (std::find(favs.begin(), favs.end(), std::string(cur)) == favs.end()) ctrShuffleNext();
    }
    return true;
}

// The console steps Shuffle at power on and after a sleep in the menu (180 frames or more). The
// home process restarts after many app exits, so "power on" is once per boot (a sys. property).
void NanoMenu::ctrShuffleEvent(bool boot, int64_t sleptMs) {
    if (mOverlayMode || !mCtrTheme) return;
    char t[PROPERTY_VALUE_MAX] = {};
    property_get("persist.gammaos.nano.ctr.theme", t, "-1");
    if (strcmp(t, "shuffle") != 0) return;
    if (boot) {
        if (property_get_bool("sys.gammaos.nano.ctr.shuffle_boot", false)) return;
        property_set("sys.gammaos.nano.ctr.shuffle_boot", "1");
    } else if (sleptMs < 3000) {
        return;
    }
    ctrShuffleNext();
}

void NanoMenu::ctrShuffleNext() {
    const std::vector<std::string> favs = ctrSplit(kShuffleFavs);
    const int n = (int)favs.size();
    if (n == 0) { property_set(kShuffleCur, ""); return; }
    std::vector<int> order;
    for (const std::string& e : ctrSplit(kShuffleOrder)) order.push_back(atoi(e.c_str()));
    bool valid = (int)order.size() == n;
    for (int i : order) valid = valid && i >= 0 && i < n;
    char cur[PROPERTY_VALUE_MAX] = {};
    property_get(kShuffleCur, cur, "");
    int pos = property_get_int32(kShufflePos, -1);
    auto reshuffle = [&](bool avoidCurrent) {
        static std::mt19937 rng((uint32_t)systemTime(SYSTEM_TIME_MONOTONIC));
        for (int attempt = 0; attempt < 64; attempt++) {
            order.resize(n);
            for (int i = 0; i < n; i++) order[i] = i;
            for (int i = n; i > 1; i--) std::swap(order[i - 1], order[std::uniform_int_distribution<int>(0, i - 1)(rng)]);
            if (!avoidCurrent || n < 2 || favs[order[0]] != cur) return;
        }
    };
    if (!valid) { reshuffle(true); pos = -1; }
    pos++;
    if (pos >= n) { pos = 0; reshuffle(true); }
    std::vector<std::string> os;
    for (int i : order) os.push_back(std::to_string(i));
    property_set(kShuffleOrder, ctrJoin(os).c_str());
    property_set(kShufflePos, std::to_string(pos).c_str());
    property_set(kShuffleCur, favs[order[pos]].c_str());
    ALOGI("ctr: shuffle -> %s (%d of %d)", favs[order[pos]].c_str(), pos + 1, n);
}

void NanoMenu::ctrApplyTheme() {
    static const prop_info* tpi = nullptr;
    static uint32_t tser = ~0u;
    static std::string key;
    if (!tpi) tpi = __system_property_find("persist.gammaos.nano.ctr.theme");
    const uint32_t ser = tpi ? __system_property_serial(tpi) : 0;
    if (ser != tser) {
        tser = ser;
        char v[PROPERTY_VALUE_MAX] = {};
        property_get("persist.gammaos.nano.ctr.theme", v, "-1");
        key = v;
    }
    // Shuffle shows its current favourite (stepped at start and after a sleep, ctrShuffleNext)
    std::string theme = key;
    if (key == "shuffle") {
        static const prop_info* cpi = nullptr;
        static uint32_t cser = ~0u;
        static std::string cur;
        if (!cpi) cpi = __system_property_find("persist.gammaos.nano.ctr.shuffle_cur");
        const uint32_t cs = cpi ? __system_property_serial(cpi) : 0;
        if (cs != cser || !cpi) {
            cser = cs;
            char v[PROPERTY_VALUE_MAX] = {};
            property_get("persist.gammaos.nano.ctr.shuffle_cur", v, "");
            cur = v;
        }
        if (cur.empty() && !mOverlayMode && !ctrSplit(kShuffleFavs).empty()) {
            ctrShuffleNext();   // just turned on: the first favourite now
            return;             // the property change brings it next frame
        }
        theme = cur.empty() ? "-1" : cur;
    }
    const std::string want = theme + "|" + std::to_string(mPs3ColorIdx) + "|" + mPs3ColorCustomHex;
    if (want == mCtrThemeKey && mCtrHome->theme() == mCtrThemeApplied) return;
    mCtrThemeKey = want;
    const ctr::ThemeBody* body = nullptr;
    if (!mCtrThemeBody) mCtrThemeBody = std::make_shared<ctr::ThemeBody>();
    if (theme.rfind("sd/", 0) == 0) {
        std::string err;
        const std::string dir = std::string(kCtrThemeRoot) + "/" + theme.substr(3);
        if (ctr::loadThemeBody(dir, mCtrThemeBody.get(), &err)) body = mCtrThemeBody.get();
        else ALOGW("ctr: theme %s not loaded: %s", dir.c_str(), err.c_str());
    } else {
        const int n = atoi(theme.c_str());
        const std::vector<ctr::ThemeBody>& themes = ctr::builtinThemes();
        if (theme != "-1" && !theme.empty() && n >= 0 && n < (int)themes.size()) {
            body = &themes[n];
        } else if (mPs3ColorIdx != 0) {
            float r, g, b;
            minimaAccent(r, g, b);
            if (ctr::makeColorTheme(r, g, b, mCtrThemeBody.get())) body = mCtrThemeBody.get();
        }
    }
    mCtrHome->setTheme(body);
    mCtrThemeApplied = body;
    ctrApplyThemeSounds(body);
    // the background images are textures now: their pixels are not kept (a new home loads the
    // theme again)
    if (body == mCtrThemeBody.get()) {
        for (ctr::ThemeImage* img : {&mCtrThemeBody->topImage, &mCtrThemeBody->bottomImage}) {
            img->rgba.clear();
            img->rgba.shrink_to_fit();
        }
    }
    mDisplayDirty = true;
}

// A theme's sounds (theme/sounds.ts, theme.py parse_sound_effects): the slots of its sound effect
// section replace menu sounds (cursor for the icon touch and select, launch for START, ok for the
// buttons, cancel for back), the frame slots play on the bottom image's frames, and its BCSTM
// replaces the menu BGM. The stream is decoded once into a WAV in the art cache, mapped like the
// menu's own BGM (page cache, not heap).
void NanoMenu::ctrApplyThemeSounds(const ctr::ThemeBody* body) {
    if (!mCtrAudio) return;
    mCtrAudio->clearOverrides();
    // The playing BGM is stopped (the keep-alive starts the new one, without its intro) only when
    // the theme's BGM changes. Stopping it every time cut the menu's boot BGM: the first theme
    // application runs right after the home loaded and started BGM_CTR_HOME with its intro.
    auto restartBgmIf = [this](const std::string& bgm) {
        if (bgm == mCtrBgmApplied) return;
        mCtrBgmApplied = bgm;
        mCtrAudio->stopBgm(10);
    };
    if (!body) { restartBgmIf(std::string()); return; }   // the menu's own BGM
    const std::vector<uint8_t>& se = body->soundEffects;
    auto u32 = [&](size_t o) -> uint32_t {
        return o + 4 <= se.size() ? (uint32_t)se[o] | se[o + 1] << 8 | se[o + 2] << 16 | (uint32_t)se[o + 3] << 24 : 0;
    };
    // decoded slots, by name
    std::map<std::string, std::pair<std::shared_ptr<ctr::RomSound>, float>> slots;
    if (se.size() >= 8) {
        size_t off = 8;
        auto slot = [&](const char* name, size_t hdr, size_t dataAt, uint32_t size, uint32_t vol) {
            (void)hdr;
            if (!size || dataAt + size > se.size()) return;
            auto s = std::make_shared<ctr::RomSound>();
            std::string err;
            if (ctr::decodeCtrWave(se.data() + dataAt, size, s.get(), &err) && !s->pcm.empty())
                slots[name] = {s, vol * 0.01f};
            else
                ALOGW("ctr: theme sound %s: %s", name, err.c_str());
        };
        for (const char* n : {"cursor", "launch", "ok", "cancel"}) {
            const uint32_t size = u32(off), vol = u32(off + 4);
            slot(n, off, off + 8, size, vol);
            off += 8 + size;
        }
        {   // the held scroll sound: size, volume, then nine words of its pitch and loop settings
            const uint32_t size = u32(off), vol = u32(off + 4);
            slot("scroll", off, off + 0x2C, size, vol);
            off += 0x2C + size;
        }
        for (const char* n : {"frame0", "frame1", "frame2", "resume"}) {
            const uint32_t size = u32(off), vol = u32(off + 4);
            slot(n, off, off + 8, size, vol);
            off += 8 + size;
        }
    }
    if (!slots.empty()) {
        const float se1 = 1.0f / mCtrAudio->seMaster();   // the theme's own levels, not the render makeup
        auto use = [&](const char* name, const char* slotName) {
            auto it = slots.find(slotName);
            if (it == slots.end()) return;
            const ctr::RomSound& s = *it->second.first;
            mCtrAudio->overridePcm(name, it->second.first, s.pcm.data(), s.frames, s.channels, s.sampleRate, s.loop,
                                   s.loopStart, s.loopEnd, it->second.second * se1);
        };
        static const struct { const char* name; const char* slot; } kReplaced[] = {
            {"SE_CTR_HOME_ICON_TOUCH", "cursor"}, {"SE_CTR_HOME_ICON_SELECT", "cursor"}, {"SE_CTR_HOME_START", "launch"},
            {"SE_CTR_COMMON_BUTTON", "ok"}, {"SE_CTR_COMMON_OK", "ok"}, {"SE_CTR_COMMON_RETURN", "cancel"},
            {"SE_CTR_COMMON_CANCEL", "cancel"}};
        for (const auto& r : kReplaced) use(r.name, r.slot);
        mCtrAudio->silence("SE_CTR_HOME_EX_ICON_SCROLL");
        if (slots.count("launch")) mCtrAudio->silence("SE_CTR_HOME_START_EFFECT");
        if (slots.count("scroll")) use("SE_CTR_HOME_ICON_SCROLL", "scroll");
        use("THEME_FRAME0", "frame0");
        use("THEME_FRAME1", "frame1");
        use("THEME_FRAME2", "frame2");
    }
    // the BGM
    std::string bgmWav;
    if (body->bgmEnabled && !body->bgmPath.empty()) {
        struct stat st;
        if (stat(body->bgmPath.c_str(), &st) == 0) {
            uint64_t h = 1469598103934665603ull;
            for (char c : body->bgmPath) { h ^= (unsigned char)c; h *= 1099511628211ull; }
            h ^= (uint64_t)st.st_size * 31 + (uint64_t)st.st_mtime;
            char wav[160];
            snprintf(wav, sizeof(wav), "/data/system/nano_ctr_rom/theme_bgm_%016llx.wav", (unsigned long long)h);
            struct stat ws;
            if (stat(wav, &ws) != 0 && !ctrWriteThemeBgm(body->bgmPath, wav))
                ALOGW("ctr: theme BGM %s not decoded", body->bgmPath.c_str());
            if (stat(wav, &ws) == 0) {
                const float g = 1.0f / mCtrAudio->bgmMaster();   // a stream at its own level
                mCtrAudio->overrideWav("BGM_CTR_HOME", wav, g);
                mCtrAudio->overrideWav("BGM_CTR_HOME_NO_INTRO", wav, g);
                bgmWav = wav;
            }
        }
    }
    restartBgmIf(bgmWav);   // the keep-alive starts the (new) BGM
}

// Decodes a theme's BCSTM into a 16 bit WAV with its loop (a smpl chunk, as the menu's renders),
// replacing an older theme's file.
bool NanoMenu::ctrWriteThemeBgm(const std::string& bcstm, const std::string& wavPath) {
    std::vector<uint8_t> data;
    {
        FILE* f = fopen(bcstm.c_str(), "rb");
        if (!f) return false;
        fseek(f, 0, SEEK_END);
        const long n = ftell(f);
        fseek(f, 0, SEEK_SET);
        if (n <= 0 || n > 32 * 1024 * 1024) { fclose(f); return false; }
        data.resize((size_t)n);
        const bool ok = fread(data.data(), 1, data.size(), f) == data.size();
        fclose(f);
        if (!ok) return false;
    }
    ctr::RomSound s;
    std::string err;
    if (!ctr::decodeCtrStream(data.data(), data.size(), &s, &err)) {
        ALOGW("ctr: %s: %s", bcstm.c_str(), err.c_str());
        return false;
    }
    // one theme BGM file at a time
    if (DIR* d = opendir("/data/system/nano_ctr_rom")) {
        while (dirent* e = readdir(d))
            if (!strncmp(e->d_name, "theme_bgm_", 10)) unlink((std::string("/data/system/nano_ctr_rom/") + e->d_name).c_str());
        closedir(d);
    }
    const std::string tmp = wavPath + ".tmp";
    FILE* f = fopen(tmp.c_str(), "wb");
    if (!f) return false;
    auto w32 = [&](uint32_t v) { fwrite(&v, 4, 1, f); };
    auto w16 = [&](uint16_t v) { fwrite(&v, 2, 1, f); };
    const uint32_t dataBytes = (uint32_t)(s.pcm.size() * 2);
    const uint32_t smplBytes = s.loop ? 36 + 24 : 0;
    fwrite("RIFF", 1, 4, f);
    w32(4 + 8 + 16 + 8 + dataBytes + (s.loop ? 8 + smplBytes : 0));
    fwrite("WAVEfmt ", 1, 8, f);
    w32(16); w16(1); w16((uint16_t)s.channels); w32((uint32_t)s.sampleRate);
    w32((uint32_t)(s.sampleRate * s.channels * 2)); w16((uint16_t)(s.channels * 2)); w16(16);
    if (s.loop) {
        fwrite("smpl", 1, 4, f);
        w32(smplBytes);
        for (int i = 0; i < 7; i++) w32(0);
        w32(1);   // one loop
        w32(0);
        w32(0); w32(0);   // cue id, forward
        w32(s.loopStart); w32(s.loopEnd - 1);   // inclusive end
        w32(0); w32(0);
    }
    fwrite("data", 1, 4, f);
    w32(dataBytes);
    const bool ok = fwrite(s.pcm.data(), 2, s.pcm.size(), f) == s.pcm.size();
    if (fclose(f) != 0 || !ok) { unlink(tmp.c_str()); return false; }
    return rename(tmp.c_str(), wavPath.c_str()) == 0;
}

// ---------------------------------------------------------------------------- wallpapers

// Theme Settings wallpapers on the 3DS screens: the Wallpaper effect on both, else each screen's
// picture (the top one may be a video), drawn into a texture of the screen background's shape
// (cover fit, with the Wallpaper Dimming) that the home shows where the console shows a theme's
// background images. A picture is drawn once; a video or an effect every frame.
void NanoMenu::ctrUpdateWallpapers() {
    const bool effect = mCurrentEffect != 0;
    GLint prevFbo = 0;
    GLint vp[4] = {0, 0, 0, 0};
    bool saved = false;
    for (int p = 0; p < 2; p++) {
        const bool top = p == 0;
        const bool video = top && !effect && mWpTopIsVideo && wallpaperActive(0);
        const bool still = !effect && !video && wallpaperActive(p);
        CtrWallpaper& t = mCtrWp[p];
        if (!effect && !video && !still) {
            mCtrHome->setWallpaper(top, 0, 0, 0, false);
            t.sig.clear();
            continue;
        }
        // the shape of P_Bg_00: the top one is 12 wider than the screen (the 3D margin)
        const float lw = top ? mCtrHome->topWidth() + 12 : mCtrHome->bottomWidth();
        const float lh = top ? mCtrHome->topHeight() : 240.0f;
        const int h = 480, w = std::max(2, (int)lroundf(h * lw / lh));
        const bool dynamic = effect || video;
        char sig[64];
        snprintf(sig, sizeof(sig), "%dx%d|%.3f|", w, h, wallpaperScrimAlpha());
        const std::string want = std::string(sig) + (top ? mWpPathTop : mWpPathBottom);
        if (!dynamic && t.tex && want == t.sig) {
            mCtrHome->setWallpaper(top, t.tex, w, h, false, true);
            continue;
        }
        if (!saved) {
            glGetIntegerv(GL_FRAMEBUFFER_BINDING, &prevFbo);
            glGetIntegerv(GL_VIEWPORT, vp);
            saved = true;
        }
        if (!t.tex) glGenTextures(1, &t.tex);
        if (!t.fbo) glGenFramebuffers(1, &t.fbo);
        if (t.w != w || t.h != h) {
            glBindTexture(GL_TEXTURE_2D, t.tex);
            glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, w, h, 0, GL_RGBA, GL_UNSIGNED_BYTE, nullptr);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
            glBindFramebuffer(GL_FRAMEBUFFER, t.fbo);
            glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, t.tex, 0);
            t.w = w;
            t.h = h;
        }
        glBindFramebuffer(GL_FRAMEBUFFER, t.fbo);
        if (glCheckFramebufferStatus(GL_FRAMEBUFFER) != GL_FRAMEBUFFER_COMPLETE) {
            mCtrHome->setWallpaper(top, 0, 0, 0, false);
            continue;
        }
        glViewport(0, 0, w, h);
        glDisable(GL_SCISSOR_TEST);
        glClearColor(0, 0, 0, 1);
        glClear(GL_COLOR_BUFFER_BIT);
        // nano's 2D drawing works in mWidth x mHeight, turned by the panel's rotation: the target
        // is logical (the home turns it with the screen), so the pass draws unrotated
        const int sw = mWidth, sh = mHeight, sp = mRenderingPanel;
        mWidth = w;
        mHeight = h;
        mRenderingPanel = p;
        float rot[4];
        memcpy(rot, sDrmRotMat, sizeof(rot));
        static const float kIdent[4] = {1.0f, 0.0f, 0.0f, 1.0f};
        auto uploadRotation = [this](const float* m) {
            const GLuint progs[] = {mShaderProgram, mTextProgram, mParticleProgram, mFxProgram, mXmbProgram};
            const GLint locs[] = {mLocRotation, mTextLocRotation, mParticleLocRotation, mFxLocRotation, mXmbLocRotation};
            for (int i = 0; i < 5; i++) {
                if (!progs[i] || locs[i] < 0) continue;
                glUseProgram(progs[i]);
                glUniformMatrix2fv(locs[i], 1, GL_FALSE, m);
            }
        };
        memcpy(sDrmRotMat, kIdent, sizeof(kIdent));
        uploadRotation(kIdent);
        if (effect) {
            renderEffect();
        } else if (video) {
            static const float kId[4] = {1.0f, 0.0f, 0.0f, 1.0f};   // the target is logical
            mWpVideoTop->updateFrame();
            mWpVideoTop->draw(w, h, 0.0f, 0.0f, (float)w, (float)h, 1.0f, /*cover=*/1, kId);
            const float sa = wallpaperScrimAlpha();
            if (sa > 0.001f) { setUiBlend(); drawQuad(0.0f, 0.0f, (float)w, (float)h, 0.0f, 0.0f, 0.0f, sa); }
        } else {
            drawWallpaperFill(p);   // cover fit + dimming
        }
        memcpy(sDrmRotMat, rot, sizeof(rot));
        uploadRotation(rot);
        mWidth = sw;
        mHeight = sh;
        mRenderingPanel = sp;
        t.sig = dynamic ? std::string() : want;
        if (!dynamic) ctr::gTextureEpoch++;   // same texture, new pixels: the cached bottom redraws
        mCtrHome->setWallpaper(top, t.tex, w, h, dynamic, true);
    }
    if (saved) {
        glBindFramebuffer(GL_FRAMEBUFFER, (GLuint)prevFbo);
        glViewport(vp[0], vp[1], vp[2], vp[3]);
    }
    if (effect) mDisplayDirty = true;   // an animation keeps the frames coming
}

// ---------------------------------------------------------------------------- lists and dialogs

// The global search results (the query keyboard closed) as the 3DS panel.
bool NanoMenu::ctrSearchThemed() const {
    return mCtrTheme && mGSearchActive && !mOskActive && !mSetupWizardActive && !mPs3BootActive &&
           mCtrHome && mCtrHome->loaded();
}

bool NanoMenu::ctrWizThemed() const {
    return mCtrTheme && mPs3WizActive && !mSetupWizardActive && !mPs3BootActive && mCtrHome && mCtrHome->loaded();
}

int NanoMenu::ctrModalKind() const {
    if (!mCtrTheme || !mCtrHome || !mCtrHome->loaded() || mPs3BootActive || mSetupWizardActive) return CTR_MODAL_NONE;
    if (ctrWizThemed()) return CTR_MODAL_PANEL;   // the network / Bluetooth wizard as the panel
    if (mScrapeProgActive || mMtpActive) return CTR_MODAL_PANEL;   // the scraper / MTP as dialogs
    if (ctrSearchThemed()) return CTR_MODAL_PANEL;                  // search results as the panel
    // screens without a 3DS look yet: the shared XMB chrome
    // (the keyboard and the search results draw on their own over the 3DS screens: ctrRenderOverlays)
    const bool other = mVidActive || mMpActive || mPvActive ||
                       mPhotoMultiActive ||
                       mPvPlChooserActive || mVidPlChooserActive || mMpPlChooserActive ||
                       ps3TopScreenKind() == PHOTO_GRID;
    if (other) return CTR_MODAL_FALLBACK;
    if (mPs3DlgActive || mPs3OptActive || mPs3TzActive || mPs3LangActive || mPs3BrightSlider || !mPs3Stack.empty())
        return CTR_MODAL_PANEL;
    return CTR_MODAL_NONE;
}

namespace {
uint64_t ctrLevelId(int kind, size_t depth, const std::string& title, size_t rows) {
    uint64_t h = 1469598103934665603ull;
    auto mix = [&](uint64_t v) { h ^= v; h *= 1099511628211ull; };
    mix((uint64_t)kind);
    mix((uint64_t)depth);
    for (char c : title) mix((uint64_t)(unsigned char)c);
    (void)rows;
    return h;
}

bool ctrIsSwitch(const std::string& v, bool* on) {
    static const char* kOn[] = {"On", "ON", "on", "Enabled", "Yes"};
    static const char* kOff[] = {"Off", "OFF", "off", "Disabled", "No"};
    for (const char* k : kOn) if (v == k || v == trDyn(k)) { *on = true; return true; }
    for (const char* k : kOff) if (v == k || v == trDyn(k)) { *on = false; return true; }
    return false;
}
}  // namespace

void NanoMenu::ctrModalUpdate() {
    if (!mCtrPanel || !mCtrDialog) return;
    // a playing BGM fades out the moment the home stops being the home (a launch, an app, a player)
    if (mCtrAudio && !ctrBgmAllowed() && mCtrAudio->bgmPlaying()) mCtrAudio->stopBgm(20);
    // The closing flags belong to the theme that animates the close (every theme clears them
    // when its fade ends; input waits on them): option menus and choosers leave the panel at
    // once, a message dialog when the 3DS dialog finished fading out.
    if (mPs3OptClosing && !mPs3OptActive) { mPs3OptClosing = false; mPs3OptCloseAnim = 0.0f; }
    if (mPs3DlgClosing && !mPs3DlgActive && (ndsDlgIsSidePanel() || !mCtrDialog->active())) {
        mPs3DlgClosing = false;
        mPs3DlgCloseAnim = 0.0f;
    }
    if (mPs3OptActive) mPs3OptAnim = 1.0f;
    if (mPs3DlgActive) mPs3DlgAnim = 1.0f;
    const float bw = mCtrHome->bottomWidth();
    mCtrPanel->setWidth(bw);
    mCtrDialog->setWidth(bw);
    mCtrPanel->setTopSize(mCtrHome->topWidth(), mCtrHome->topHeight());
    mCtrDialog->setTopSize(mCtrHome->topWidth(), mCtrHome->topHeight());
    if (ctrModalKind() != CTR_MODAL_PANEL) {
        mCtrHome->setTopBannerHidden(false);
        mCtrPanel->hide();
        mCtrDialog->hide(true);
        mCtrPanelWasShown = false;
        return;
    }
    if (ctrWizThemed()) {
        ctrWizUpdate();
        return;
    }
    if (ctrSearchThemed()) {
        ctrSearchUpdate();
        return;
    }
    // ---- the panel: an option menu, a list-style dialog, or the current list level ----
    std::vector<ctr::PanelRow> rows;
    std::string title;
    int sel = 0;
    uint64_t level = 0;
    // a list-style dialog (a chooser) shows as the panel while it is open; a closing one is gone
    const bool dlgSide = ndsDlgIsSidePanel();
    // a numeric slider is the console's progress dialog (a window with the bar), not a list
    const bool slider = ctrSliderActive();
    // an information page (a game's details, App Information, the guide): paragraphs in the panel
    const bool info = ctrInfoActive();
    const bool dlgList = mPs3DlgActive && dlgSide && !slider && !info;
    const bool picker = mPs3TzActive || mPs3LangActive;
    if (!info) mCtrInfoNav = 0;
    if (info) {
        auto para = [&](const std::string& text) {
            if (text.empty()) return;
            ctr::PanelRow r;
            r.kind = ctr::PanelRow::Text;
            r.label = text;
            rows.push_back(r);
        };
        auto fact = [&](const char* label, const std::string& v) {
            if (!v.empty()) para(std::string(trDyn(label)) + ": " + v);
        };
        if (mPs3DlgRomInfo) {
            fact("Genre", mPs3RomInfoGenre);
            fact("Players", mPs3RomInfoPlayers);
            fact("Rating", mPs3RomInfoRating);
            fact("Released", mPs3RomInfoDate);
            fact("Developer", mPs3RomInfoDev);
            fact("Publisher", mPs3RomInfoPub);
            para(mPs3RomInfoSyn);
            fact("System", mPs3RomInfoSystem);
            fact(mPs3RomInfoCoreIsApp ? "App" : "Core", mPs3RomInfoCore);
            fact("Size", mPs3RomInfoSize);
            fact("File", mPs3RomInfoFileName);
            fact("Folder", mPs3RomInfoDir);
        } else {
            // a text body: one paragraph per line; the column padding of "Label     value" lines
            // (laid out for a fixed width font) becomes "Label: value"
            const std::string body = trDyn(mPs3DlgBody.c_str());
            size_t p = 0;
            while (p <= body.size()) {
                size_t q = body.find('\n', p);
                std::string line = body.substr(p, q == std::string::npos ? std::string::npos : q - p);
                const size_t gap = line.find("  ");
                if (gap != std::string::npos && gap > 0) {
                    const size_t v = line.find_first_not_of(' ', gap);
                    if (v != std::string::npos) line = line.substr(0, gap) + ": " + line.substr(v);
                }
                para(line);
                if (q == std::string::npos) break;
                p = q + 1;
            }
        }
        sel = -1;
        title = mPs3DlgTitle.empty() ? trDyn("Information") : trDyn(mPs3DlgTitle.c_str());
        level = ctrLevelId(6, mPs3Stack.size(), title, rows.size());
    } else if (picker) {
        // Time Zone / System Language: a list of choices; the keys stay with nano's picker handlers
        if (mPs3TzActive) {
            for (const auto& z : mTzEntries) {
                ctr::PanelRow r;
                r.label = z.display;
                rows.push_back(r);
            }
            sel = mTzSelected;
            title = trDyn("Time Zone");
        } else {
            nanoSetLocale((NanoLocale)mLangSelected);   // live preview as the cursor moves
            // a name the console's fonts cannot draw (Arabic) shows its English name
            std::shared_ptr<ctr::Font> font = mCtrHome->assets().font("cbf_std");
            for (int i = 0; i < LOCALE_COUNT; i++) {
                const LocaleInfo& li = nanoGetLocaleInfo((NanoLocale)i);
                ctr::PanelRow r;
                r.label = li.nativeName;
                if (font) {
                    for (char32_t c : ctr::utf8ToU32(r.label))
                        if (c > ' ' && !font->has(c)) { r.label = li.englishName; break; }
                }
                rows.push_back(r);
            }
            sel = mLangSelected;
            title = trDyn("System Language");
        }
        level = ctrLevelId(mPs3TzActive ? 4 : 5, mPs3Stack.size(), title, rows.size());
    } else if (mPs3OptActive) {
        const bool subOpen = mPs3OptSubOpen && mPs3OptSel >= 0 && mPs3OptSel < (int)mPs3OptSubRows.size() &&
                             !mPs3OptSubRows[mPs3OptSel].empty();
        if (subOpen) {
            for (const auto& sr : mPs3OptSubRows[mPs3OptSel]) {
                ctr::PanelRow r;
                r.label = trDyn(sr.label.c_str());
                rows.push_back(r);
            }
            sel = mPs3OptSubSel;
            title = mPs3OptSel < (int)mPs3OptLabels.size() ? trDyn(mPs3OptLabels[mPs3OptSel].c_str()) : trDyn("Options");
        } else {
            for (int i = 0; i < (int)mPs3OptLabels.size(); i++) {
                if (i < (int)mPs3OptSep.size() && mPs3OptSep[i]) continue;
                if (i == mPs3OptSel) sel = (int)rows.size();
                ctr::PanelRow r;
                r.label = trDyn(mPs3OptLabels[i].c_str());
                rows.push_back(r);
            }
            title = mPs3OptCtxLabel.empty() ? trDyn("Options") : trDyn(mPs3OptCtxLabel.c_str());
        }
        level = ctrLevelId(subOpen ? 2 : 1, mPs3Stack.size(), title, rows.size());
    } else if (dlgList) {
        for (const auto& o : mPs3DlgOptions) {
            ctr::PanelRow r;
            r.label = trDyn(o.c_str());
            rows.push_back(r);
        }
        sel = mPs3DlgSel;
        title = mPs3DlgTitle.empty() ? trDyn("Options") : trDyn(mPs3DlgTitle.c_str());
        level = ctrLevelId(3, mPs3Stack.size(), title, rows.size());
    } else if (!mPs3Stack.empty()) {
        const Ps3Level& lv = mPs3Stack.back();
        // every item is a row (nano's selection indexes the items): one that does nothing when
        // chosen (an information line, the empty list's note) is shown dimmed
        for (const Ps3Item& it : lv.items) {
            ctr::PanelRow r;
            r.label = trDyn(it.label.c_str());
            r.enabled = !(it.kind == PS3_DATA_LEAF && it.action == 0);
            bool on = false;
            if (ctrItemSwitch(it, &on)) { r.kind = ctr::PanelRow::Toggle; r.on = on; }
            else {
                const std::string v = it.binding ? resolvePs3ItemValue(it) : it.value;
                if (!v.empty()) { r.kind = ctr::PanelRow::Value; r.value = trDyn(v.c_str()); }
            }
            rows.push_back(r);
        }
        sel = lv.sel;
        title = trDyn(lv.title.c_str());
        level = ctrLevelId(0, mPs3Stack.size(), lv.title, rows.size());
    }
    const bool panel = info || picker || mPs3OptActive || dlgList || !mPs3Stack.empty();
    mCtrHome->setTopBannerHidden(panel);
    if (panel) {
        mCtrPanel->set(title, rows, sel, level);
        mCtrPanel->show();
        // the D-pad scrolls a page a line (24) at a time, L / R a screenful
        if (info && mCtrInfoNav) mCtrPanel->scrollBy(24.0f * (float)mCtrInfoNav);
        mCtrInfoNav = 0;
        mCtrPanelWasShown = true;
    } else {
        mCtrPanel->hide();
        mCtrPanelWasShown = false;
    }
    // ---- the message dialog (confirmations, notices) over everything ----
    CtrStatusView st;
    if (ctrStatusView(&st)) {
        if (st.fraction >= 0) {
            mCtrDialog->setWait(std::string());
            mCtrDialog->set(std::string(), st.buttons, 0);
            mCtrDialog->setProgress(st.text, st.fraction);
        } else if (st.wait) {
            mCtrDialog->setProgress(std::string(), -1);
            mCtrDialog->set(std::string(), st.buttons, 0);
            mCtrDialog->setWait(st.text);
        } else {
            mCtrDialog->setProgress(std::string(), -1);
            mCtrDialog->setWait(std::string());
            mCtrDialog->set(st.text, st.buttons, 0);
        }
        mCtrDialog->show();
        return;
    }
    mCtrDialog->setWait(std::string());
    if (mPs3BrightSlider) {
        // the Quick Menu brightness: live, in percent like the other themes' bar
        char pct[16];
        snprintf(pct, sizeof(pct), "%d%%", mBrightness * 100 / 255);
        mCtrDialog->set(std::string(), {}, 0);
        mCtrDialog->setProgress(std::string(trDyn("Brightness")) + "\n\n" + pct, (float)mBrightness / 255.0f);
        mCtrDialog->show();
    } else if (slider) {
        const float span = mPs3DlgSldMax - mPs3DlgSldMin;
        const float frac = span > 0 ? std::clamp((mPs3DlgSldVal - mPs3DlgSldMin) / span, 0.0f, 1.0f) : 0.0f;
        char num[32];
        snprintf(num, sizeof(num), "%.*f", std::clamp(mPs3DlgSldScale, 0, 4), mPs3DlgSldVal);
        std::string text = mPs3DlgTitle.empty() ? std::string() : std::string(trDyn(mPs3DlgTitle.c_str())) + "\n\n";
        mCtrDialog->set(std::string(), {}, 0);
        mCtrDialog->setProgress(text + num, frac);
        mCtrDialog->show();
    } else if ((mPs3DlgActive || mPs3DlgClosing) && !dlgSide && !info && !(!mPs3DlgActive && ctrInfoDialog())) {
        mCtrDialog->setProgress(std::string(), -1);
        std::string text;
        if (!mPs3DlgTitle.empty()) text = trDyn(mPs3DlgTitle.c_str());
        if (!mPs3DlgBody.empty()) text += (text.empty() ? "" : "\n\n") + std::string(trDyn(mPs3DlgBody.c_str()));
        std::vector<std::string> buttons;
        for (const auto& o : mPs3DlgOptions) buttons.push_back(trDyn(o.c_str()));
        mCtrDialog->set(text, buttons, mPs3DlgSel);
        if (mPs3DlgActive) mCtrDialog->show();
        else mCtrDialog->hide(false);
    } else {
        mCtrDialog->hide(true);
    }
}

// The network / Bluetooth wizard in the 3DS look: lists in the panel (its title band names the
// wizard), notices and questions in the dialog, busy screens in the wait dialog. The keys stay
// with the wiz* handlers; the wizard's state machine runs here so it also runs where nothing
// else draws the wizard (one panel).
void NanoMenu::ctrWizUpdate() {
    netWizardTick();
    CtrWizView v;
    ctrWizView(&v);
    const bool list = v.kind == CtrWizView::List;
    mCtrHome->setTopBannerHidden(list);
    if (list) {
        std::vector<ctr::PanelRow> rows;
        for (const auto& w : v.rows) {
            ctr::PanelRow r;
            r.label = w.label;
            if (w.text) r.kind = ctr::PanelRow::Text;
            else if (!w.value.empty()) { r.kind = ctr::PanelRow::Value; r.value = w.value; }
            r.enabled = w.text || w.act != -1;
            rows.push_back(r);
        }
        mCtrPanel->set(v.title, rows, v.sel, ctrLevelId(7, (size_t)mPs3WizId, v.title, rows.size()));
        mCtrPanel->show();
        mCtrPanelWasShown = true;
    } else {
        mCtrPanel->hide();
        mCtrPanelWasShown = false;
    }
    if (v.kind == CtrWizView::Dialog || v.kind == CtrWizView::Wait) {
        mCtrDialog->setProgress(std::string(), -1);
        if (v.kind == CtrWizView::Wait) {
            mCtrDialog->set(std::string(), {}, 0);
            mCtrDialog->setWait(v.body);
        } else {
            mCtrDialog->setWait(std::string());
            mCtrDialog->set(v.body, v.buttons, v.buttonSel);
        }
        mCtrDialog->show();
    } else {
        mCtrDialog->setWait(std::string());
        mCtrDialog->hide(true);
    }
}

// The boxart scraper (and the boxart folder move) and the MTP screen as the console's dialogs:
// a running scrape is the progress dialog with Cancel, its result a notice with OK; MTP is a
// notice with the wait icon turning while it (re)starts, and Stop ends it.
bool NanoMenu::ctrStatusView(CtrStatusView* v) {
    *v = CtrStatusView();
    if (mMtpActive) {
        std::string status; bool failed;
        { std::lock_guard<std::mutex> lk(mMtpMutex); status = mMtpStatus; failed = mMtpFailed; }
        if (mMtpUsbConnected) {
            std::string line = status.empty() ? std::string(trDyn("Starting MTP...")) : status;
            if (mMtpRejigging.load()) {
                // busy: the trailing dots count up (the screen's own animation)
                while (!line.empty() && line.back() == '.') line.pop_back();
                line += std::string(1 + (mMtpPollFrames / 2) % 3, '.');
            }
            v->text = std::string(trDyn("MTP Active")) + "\n\n" + line + "\n" +
                      trDyn(failed ? "Reconnect the cable to try again" : "Transfer files from your computer");
        } else {
            v->text = std::string(trDyn("USB Disconnected")) + "\n\n" + trDyn("Connect a USB cable to a computer") +
                      "\n" + trDyn("MTP starts again as soon as it is plugged in");
        }
        v->buttons = {trDyn("Stop MTP")};
        return true;
    }
    if (mScrapeProgActive) {
        int done, total, hits, fail; bool running; std::string status, err;
        {
            std::lock_guard<std::mutex> lk(mScrapeMutex);
            done = mScrapeDone; total = mScrapeTotal; hits = mScrapeHits; fail = mScrapeFail;
            running = mScrapeRunning; status = mScrapeStatus; err = mScrapeError;
        }
        const bool moving = mScrapeMoveMode;
        const std::string title = trDyn(moving ? "Boxart Folder" : "Boxart Scraper");
        char line[256];
        if (!running && mScrapeDoneFlag) {
            if (!err.empty() && hits == 0) snprintf(line, sizeof(line), "%s", err.c_str());
            else if (moving) snprintf(line, sizeof(line), trDyn("Done. %d files moved, %d failed."), hits, fail);
            else snprintf(line, sizeof(line), hits == 1 ? trDyn("Done. %d game with art, %d not found.")
                                                         : trDyn("Done. %d games with art, %d not found."), hits, fail);
            v->text = title + "\n\n" + line;
            v->buttons = {trDyn("OK")};
        } else {
            snprintf(line, sizeof(line), trDyn(moving ? "%d / %d files moved" : "%d / %d   (%d found)"), done, total, hits);
            v->text = title + "\n" + line + "\n" + status;
            v->fraction = total > 0 ? std::clamp((float)done / (float)total, 0.0f, 1.0f) : 0.0f;
            // the console's progress dialog has no buttons: B cancels (as in the other themes)
        }
        return true;
    }
    return false;
}

// Search results: a header row per section (Games, Music, ...) and a row per result with its
// system / artist / date as the value. The keys stay with the gsearch* handlers.
static const char* const kCtrSearchSection[5] = {"Games", "Music", "Photos", "Videos", "IPTV"};

void NanoMenu::ctrSearchRows(std::vector<ctr::PanelRow>* rows, std::vector<int>* rowResult, int* sel) {
    rows->clear();
    rowResult->clear();
    *sel = -1;
    int prev = -1;
    for (int k = 0; k < (int)mGSearchResults.size(); k++) {
        const GSearchResult& g = mGSearchResults[k];
        if (g.section != prev) {
            prev = g.section;
            ctr::PanelRow h;
            h.kind = ctr::PanelRow::Text;
            h.label = trDyn(kCtrSearchSection[g.section >= 0 && g.section < 5 ? g.section : 0]);
            rows->push_back(h);
            rowResult->push_back(-1);
        }
        ctr::PanelRow r;
        r.label = g.label;
        if (!g.sub.empty()) { r.kind = ctr::PanelRow::Value; r.value = g.sub; }
        if (k == mGSearchSel) *sel = (int)rows->size();
        rows->push_back(r);
        rowResult->push_back(k);
    }
    if (mGSearchResults.empty()) {
        ctr::PanelRow r;
        r.kind = ctr::PanelRow::Text;
        r.label = std::string(trDyn("No results for")) + " \"" + mGSearchQuery + "\"";
        rows->push_back(r);
        rowResult->push_back(-1);
    }
}

void NanoMenu::ctrSearchUpdate() {
    std::vector<ctr::PanelRow> rows;
    std::vector<int> map;
    int sel = -1;
    ctrSearchRows(&rows, &map, &sel);
    const std::string title = std::string(trDyn("Search")) + ": " + mGSearchQuery;
    mCtrHome->setTopBannerHidden(true);
    mCtrPanel->set(title, rows, sel, ctrLevelId(8, 0, title, rows.size()));
    mCtrPanel->show();
    mCtrPanelWasShown = true;
    mCtrDialog->setWait(std::string());
    mCtrDialog->hide(true);
}

bool NanoMenu::ctrWizTouch(bool down, bool held, bool up, float x, float y) {
    CtrWizView v;
    ctrWizView(&v);
    auto act = [&](int a) {
        if (a == 0) { wizBack(); return; }
        if (a >= 2) mPs3WizSel = a - 2;
        wizConfirm();
    };
    if (v.kind == CtrWizView::Dialog && mCtrDialog->active()) {
        const int b = mCtrDialog->touch(down, up, x, y);
        if (b >= 0 && b < (int)v.buttonAct.size()) act(v.buttonAct[b]);
        mDisplayDirty = true;
        return true;
    }
    if (v.kind != CtrWizView::List || !mCtrPanel->active()) return true;
    const ctr::PanelHit h = mCtrPanel->touch(down, held, up, x, y);
    if (h.what == ctr::PanelHit::Row && h.row >= 0 && h.row < (int)v.rows.size()) {
        const int a = v.rows[h.row].act;
        if (a == -2) wizConfirm();
        else if (a >= 0) { mPs3WizSel = a; wizConfirm(); }
    } else if (h.what == ctr::PanelHit::Close || h.what == ctr::PanelHit::Outside) {
        wizBack();
    }
    mDisplayDirty = true;
    return true;
}

bool NanoMenu::ctrModalTouch(float x, float y) {
    const bool down = mTouchDown && !mCtrTouchPrev, up = !mTouchDown && mCtrTouchPrev, held = mTouchDown && mCtrTouchPrev;
    mCtrTouchPrev = mTouchDown;
    if (ctrWizThemed()) return mOskActive ? false : ctrWizTouch(down, held, up, x, y);
    if (ctrSearchThemed()) {
        if (!mCtrPanel->active()) return true;
        const ctr::PanelHit h = mCtrPanel->touch(down, held, up, x, y);
        if (h.what == ctr::PanelHit::Row) {
            std::vector<ctr::PanelRow> rows;
            std::vector<int> map;
            int sel;
            ctrSearchRows(&rows, &map, &sel);
            if (h.row >= 0 && h.row < (int)map.size() && map[h.row] >= 0) {
                mGSearchSel = map[h.row];
                gsearchActivate();
                if (mWaitForRelease && !mOverlayMode && mLaunchFadeStart == 0) mLaunchFadeStart = uptimeMillis();
            }
        } else if (h.what == ctr::PanelHit::Close || h.what == ctr::PanelHit::Outside) {
            gsearchClose();
        }
        mDisplayDirty = true;
        return true;
    }
    if (mMtpActive || mScrapeProgActive) {
        // the dialog's one button is B: Stop MTP or close the scrape's result (a one button
        // window takes a tap anywhere, like the notices); a running scrape waits for B
        if (mCtrDialog->active()) {
            const int btn = mCtrDialog->touch(down, up, x, y);
            CtrStatusView st;
            const bool hasButton = ctrStatusView(&st) && !st.buttons.empty();
            if (hasButton && (btn == 0 || (btn < 0 && up))) handleBack();
        }
        mDisplayDirty = true;
        return true;
    }
    auto launched = [this]() {
        if (mWaitForRelease && !mOverlayMode && mLaunchFadeStart == 0) mLaunchFadeStart = uptimeMillis();
        mDisplayDirty = true;
    };
    // the Quick Menu brightness: a drag sets it live, a tap elsewhere closes it
    if (mPs3BrightSlider && mCtrDialog->active()) {
        const float f = mCtrDialog->progressTouch(down, held, up, x, y);
        if (down) mCtrSliderTap = f < 0;
        if (f >= 0) {
            const int b = std::clamp((int)lroundf(f * 255.0f), 1, 255);
            if (b != mBrightness) {
                mBrightness = b;
                applyBrightness();
                mDisplayDirty = true;
            }
        } else if (up && mCtrSliderTap) {
            mCtrSliderTap = false;
            mPs3BrightSlider = false;
            mShowBrightnessBar = false;
            mBrightnessBarTimer = 0;
            mDisplayDirty = true;
        }
        return true;
    }
    // a slider: drag the bar; a tap anywhere else keeps the value (A), like the notices
    if (ctrSliderActive() && mCtrDialog->active()) {
        const float f = mCtrDialog->progressTouch(down, held, up, x, y);
        if (down) mCtrSliderTap = f < 0;
        if (f >= 0) ctrSliderSet(f);
        else if (up && mCtrSliderTap) {
            mCtrSliderTap = false;
            ps3XmbSelect();
            launched();
        }
        return true;
    }
    // the dialog is in front
    if (mCtrDialog->active() && (mPs3DlgActive || mPs3DlgClosing) && !ndsDlgIsSidePanel()) {
        const int b = mCtrDialog->touch(down, up, x, y);
        if (b >= 0 && b < (int)mPs3DlgOptions.size()) {
            mPs3DlgSel = b;
            ps3XmbSelect();
            launched();
        } else if (b < 0 && up && mPs3DlgOptions.size() <= 1 && mPs3DlgActive) {
            ps3XmbSelect();   // a notice: any tap is OK
            launched();
        }
        return true;
    }
    if (!mCtrPanel->active()) return false;
    const ctr::PanelHit h = mCtrPanel->touch(down, held, up, x, y);
    switch (h.what) {
        case ctr::PanelHit::Row: {
            if (mPs3TzActive || mPs3LangActive) {
                if (mPs3TzActive) { mTzSelected = h.row; closeTimezoneGlobe(true); }
                else { mLangSelected = h.row; closeLanguagePicker(true); }
            } else if (mPs3OptActive) {
                const bool subOpen = mPs3OptSubOpen && mPs3OptSel >= 0 && mPs3OptSel < (int)mPs3OptSubRows.size() &&
                                     !mPs3OptSubRows[mPs3OptSel].empty();
                if (subOpen) {
                    mPs3OptSubSel = h.row;
                } else {
                    int vis = 0;
                    for (int i = 0; i < (int)mPs3OptLabels.size(); i++) {
                        if (i < (int)mPs3OptSep.size() && mPs3OptSep[i]) continue;
                        if (vis++ == h.row) { mPs3OptSel = i; break; }
                    }
                }
                xmbOptEnter();
            } else if ((mPs3DlgActive || mPs3DlgClosing) && ndsDlgIsSidePanel()) {
                mPs3DlgSel = h.row;
                ps3XmbSelect();
            } else if (!mPs3Stack.empty()) {
                Ps3Level& lv = mPs3Stack.back();
                const int idx = h.row;
                if (idx < 0 || idx >= (int)lv.items.size()) break;
                // a switch: the tapped side is set at once (the console's OFF / ON pair)
                bool on = false;
                lv.sel = idx;
                if (ctrItemSwitch(lv.items[idx], &on) && h.column >= 0) {
                    ctrSetSwitch(idx, h.column == 1);
                    break;
                }
                ps3XmbSelect();
            }
            launched();
            break;
        }
        case ctr::PanelHit::Close:
            // the close button leaves the whole panel
            if (mPs3TzActive) { closeTimezoneGlobe(false); break; }
            if (mPs3LangActive) { closeLanguagePicker(false); break; }
            if (mPs3OptActive || mPs3DlgActive) { ps3XmbBack(); break; }
            for (int guard = 0; guard < 16 && !mPs3Stack.empty() && !mPs3OptActive && !mPs3DlgActive; guard++) {
                const size_t depth = mPs3Stack.size();
                ps3XmbBack();
                if (mPs3Stack.size() == depth) break;
            }
            mDisplayDirty = true;
            break;
        case ctr::PanelHit::Outside:
            if (mPs3TzActive) { closeTimezoneGlobe(false); break; }
            if (mPs3LangActive) { closeLanguagePicker(false); break; }
            ps3XmbBack();
            mDisplayDirty = true;
            break;
        default:
            break;
    }
    return true;
}

// Opens nano's option menu for the title under the 3DS cursor: the menu takes its context from
// the current list, so the title is offered as a one item list while it opens.
bool NanoMenu::ctrOpenOptions() {
    if (!mCtrHome || !mCtrHost) return false;
    // a folder's Start is its Settings (a user folder: Rename, Move, Delete; a system's: Move)
    if (const ctr::HomeItem* f = mCtrHome->focusedItem(); f && f->kind == ctr::HomeItemKind::Folder) {
        ctrFolderSettings(f->id);
        mCtrHeld = 0;
        return mPs3DlgActive;
    }
    const ctr::HomeItem* h = mCtrHome->focusedTitle();
    const Ps3Item* it = h ? mCtrHost->titleOf(h->id) : nullptr;
    if (!it) return false;
    Ps3Level lvl;
    lvl.title = it->label;
    lvl.items.push_back(*it);
    lvl.sel = 0;
    mPs3Stack.push_back(lvl);
    openXmbOpt();
    mPs3Stack.pop_back();
    if (mPs3OptActive && mCtrHome->canMoveFocused()) {
        // Move first: arranging the HOME Menu is what the 3DS theme adds to nano's options
        mPs3OptLabels.insert(mPs3OptLabels.begin(), "Move");
        mPs3OptActs.insert(mPs3OptActs.begin(), "ctrmove");
        mPs3OptStart.insert(mPs3OptStart.begin(), 0);
        mPs3OptSep.insert(mPs3OptSep.begin(), 0);
        mPs3OptHasSub.insert(mPs3OptHasSub.begin(), 0);
        mPs3OptSubDef.insert(mPs3OptSubDef.begin(), 0);
        mPs3OptSubRows.insert(mPs3OptSubRows.begin(), std::vector<Ps3OptSub>());
        mPs3OptSel++;   // the row A picked before stays the default
    }
    mCtrHeld = 0;   // the keys held now belong to the menu
    mDisplayDirty = true;
    return mPs3OptActive;
}

// A user folder's Settings button (folder-settings.ts): Rename (nano's keyboard, prefilled) or
// Delete Folder (its titles go back onto the root, as on the console).
void NanoMenu::ctrFolderSettings(const std::string& folderId) {
    if (!mCtrHost || !mCtrHome) return;
    const bool user = mCtrHost->userFolder(folderId);
    const ctr::HomeItem* f = mCtrHome->focusedItem();
    const bool focused = f && f->id == folderId;
    // a system's folder only moves; a user folder also renames and deletes
    mCtrFolderOpts.clear();
    if (user) mCtrFolderOpts.push_back("Rename");
    if (focused && mCtrHome->canMoveFocused()) mCtrFolderOpts.push_back("Move");
    if (user) mCtrFolderOpts.push_back("Delete Folder");
    if (mCtrFolderOpts.empty()) return;
    mCtrFolderEdit = folderId;
    mPs3DlgOptions.clear();
    mPs3DlgSwatch.clear();
    mPs3DlgKind = 1; mPs3DlgType = 0; mPs3DlgThemeKey = 60; mPs3DlgBinding = nullptr;
    mPs3DlgTitle = user ? mCtrHost->mLayout.folders[folderId].name : (focused ? f->name : std::string());
    mPs3DlgBody.clear();
    for (const std::string& o : mCtrFolderOpts) { mPs3DlgOptions.push_back(o); mPs3DlgSwatch.push_back(-1); }
    mPs3DlgSel = 0; mPs3DlgOrigSel = 0;
    mPs3DlgIconTex = 0; mPs3DlgIconNmap = nmapForIcon(22);
    mPs3DlgIconR = mPs3DlgIconG = mPs3DlgIconB = 1.0f;
    mPs3DlgActive = true; mPs3DlgAnim = 0.0f; mPs3DlgClosing = false; mPs3DlgBlurValid = false;
    mDisplayDirty = true;
}

void NanoMenu::ctrFolderSettingsChosen(int sel) {
    const std::string id = mCtrFolderEdit;
    mCtrFolderEdit.clear();
    if (!mCtrHost || sel < 0 || sel >= (int)mCtrFolderOpts.size()) return;
    const std::string opt = mCtrFolderOpts[sel];
    if (opt == "Move") { ctrStartMove(); return; }
    if (!mCtrHost->userFolder(id)) return;
    if (opt == "Rename") {
        const std::string cur = mCtrHost->mLayout.folders[id].name;
        openOskForPassword("Folder Name", [this, id](const std::string& typed) {
            std::string name = typed;
            while (!name.empty() && name.front() == ' ') name.erase(name.begin());
            while (!name.empty() && name.back() == ' ') name.pop_back();
            if (name.empty() || !mCtrHost) return;
            mCtrHost->renameFolder(id, name);
            if (mCtrHome) mCtrHome->reloadItems();
            mDisplayDirty = true;
        });
        mOskPasswordMode = false; mOskPlaintext = true;   // a name is plain text, not masked
        mOskQuery = cur; mOsk.caret = (int)mOskQuery.size();   // prefill after open (which clears it)
    } else if (opt == "Delete Folder") {
        mCtrHost->deleteFolder(id);
        if (mCtrHome) mCtrHome->reloadItems();
        mDisplayDirty = true;
    }
}

// Move (nano, for buttons): the focused icon is lifted and the D-pad carries it. The keys held
// as the menu closes belong to it, so the home sees the next presses only.
void NanoMenu::ctrStartMove() {
    if (!mCtrHome || !mCtrHome->startMove()) return;
    mCtrHeld = 0;
    mDisplayDirty = true;
}

// A kind 0 information dialog: shown as a page of paragraphs in the panel. ctrInfoDialog() also
// holds while one closes (the message dialog must not take it over for its fade).
bool NanoMenu::ctrInfoDialog() const {
    return mPs3DlgKind == 0 && (mPs3DlgRomInfo || mPs3DlgGameInfo || mPs3DlgAppInfo) && (int)mPs3DlgOptions.size() <= 1;
}

bool NanoMenu::ctrInfoActive() const {
    return mPs3DlgActive && ctrInfoDialog();
}

bool NanoMenu::ctrSliderActive() const {
    return mPs3DlgActive && mPs3DlgKind == 1 && mPs3DlgSlider && mPs3DlgOptions.empty();
}

// Sets the open slider to a fraction of its range on its step grid, with the same live previews
// as the D-pad steps (screen calibration, shader parameters).
void NanoMenu::ctrSliderSet(float frac) {
    if (mPs3DlgSldStep <= 0.0f || mPs3DlgSldMax <= mPs3DlgSldMin) return;
    float v = mPs3DlgSldMin + std::clamp(frac, 0.0f, 1.0f) * (mPs3DlgSldMax - mPs3DlgSldMin);
    v = mPs3DlgSldMin + roundf((v - mPs3DlgSldMin) / mPs3DlgSldStep) * mPs3DlgSldStep;
    v = std::clamp(v, mPs3DlgSldMin, mPs3DlgSldMax);
    if (v == mPs3DlgSldVal) return;
    mPs3DlgSldVal = v;
    mDisplayDirty = true;
    screenCalSliderPreview();
    if (mShaderParamEdit >= 0 && mShaderParamEdit < (int)mShaderParams.size()) {
        mShaderParams[mShaderParamEdit].cur = v;
        shaderApplyParamLive(mShaderParamEdit);
    }
}

bool NanoMenu::ctrItemSwitch(const Ps3Item& it, bool* on) {
    if (it.checkState >= 0) { *on = it.checkState == 1; return true; }
    const std::string v = it.binding ? resolvePs3ItemValue(it) : it.value;
    return !v.empty() && ctrIsSwitch(v, on);
}

// A switch row set to OFF or ON. nano edits such a setting through a chooser (or flips a check
// row on select): open it and pick the wanted choice, so the value is stored by nano's own path.
bool NanoMenu::ctrSetSwitch(int itemIndex, bool on) {
    if (mPs3Stack.empty()) return false;
    Ps3Level& lv = mPs3Stack.back();
    if (itemIndex < 0 || itemIndex >= (int)lv.items.size()) return false;
    bool cur = false;
    if (!ctrItemSwitch(lv.items[itemIndex], &cur)) return false;
    lv.sel = itemIndex;
    mDisplayDirty = true;
    if (cur == on) return true;
    // one toggle sound for the flip, not the chooser's open and confirm sounds (the console's
    // switch, theme/settings/panel.ts decide)
    mCtrSfxMute = true;
    struct Unmute { bool* f; ~Unmute() { *f = false; } } unmute{&mCtrSfxMute};
    if (mCtrHost) mCtrHost->playSound("SE_CTR_COMMON_TOGGLE");
    ps3XmbSelect();
    if (mPs3DlgActive) {
        static const char* kOn[] = {"On", "ON", "on", "Enabled", "Yes"};
        static const char* kOff[] = {"Off", "OFF", "off", "Disabled", "No"};
        for (int k = 0; k < (int)mPs3DlgOptions.size(); k++) {
            const std::string& o = mPs3DlgOptions[k];
            bool match = false;
            for (const char* w : on ? kOn : kOff) if (o == w || o == trDyn(w)) match = true;
            if (match) { mPs3DlgSel = k; ps3XmbSelect(); return true; }
        }
        ps3XmbBack();   // not an On / Off chooser after all: leave it as it was
        return false;
    }
    return true;
}

// LEFT / RIGHT choose OFF / ON and A flips, on a switch row of a settings list.
bool NanoMenu::ctrSwitchKey(int type, int code, int value) {
    if (!mCtrTheme || ctrModalKind() != CTR_MODAL_PANEL || mPs3OptActive || mPs3DlgActive || mPs3DlgClosing ||
        mPs3WizActive || mPs3Stack.empty()) {
        mCtrSwitchKeyHeld = false;
        return false;
    }
    Ps3Level& lv = mPs3Stack.back();
    if (lv.sel < 0 || lv.sel >= (int)lv.items.size()) return false;
    bool on = false;
    const bool isSwitch = ctrItemSwitch(lv.items[lv.sel], &on);
    // the release (or the centred hat) of a key used here is ours too
    const bool release = (type == EV_KEY && value == 0) || (type == EV_ABS && code == ABS_HAT0X && value == 0);
    if (release && mCtrSwitchKeyHeld &&
        ((type == EV_KEY && (code == BTN_SOUTH || code == KEY_ENTER || code == BTN_DPAD_LEFT || code == BTN_DPAD_RIGHT ||
                             code == KEY_LEFT || code == KEY_RIGHT)) || type == EV_ABS)) {
        mCtrSwitchKeyHeld = false;
        return true;
    }
    if (!isSwitch) return false;
    int want = -1;
    if (type == EV_KEY && value == 1) {
        if (code == BTN_SOUTH || code == KEY_ENTER) want = on ? 0 : 1;
        else if (code == BTN_DPAD_LEFT || code == KEY_LEFT) want = 0;
        else if (code == BTN_DPAD_RIGHT || code == KEY_RIGHT) want = 1;
    } else if (type == EV_KEY && value == 2 &&
               (code == BTN_SOUTH || code == KEY_ENTER || code == BTN_DPAD_LEFT || code == BTN_DPAD_RIGHT || code == KEY_LEFT || code == KEY_RIGHT)) {
        return true;   // no repeat on a switch
    } else if (type == EV_ABS && code == ABS_HAT0X && value != 0) {
        want = value > 0 ? 1 : 0;
    }
    if (want < 0) return false;
    mCtrSwitchKeyHeld = true;
    if ((want == 1) != on) ctrSetSwitch(lv.sel, want == 1);
    else if (mCtrHost) mCtrHost->playSound("SE_CTR_COMMON_NOMOVE");
    return true;
}

// The home's music only plays on the home itself: not while a launch is under way, not while an
// app runs (sys.gammaos.nano.app_launched is the live signal; mOverlayWallpaper is stale after a
// launch from the overlay), not over a live app and not with a media player up. Same rule as the
// DSi theme's ambiance (NanoMenuRender ndsAmbianceTick).
bool NanoMenu::ctrBgmAllowed() {
    return !mPs3BootActive && !ndsPlayerActive() && !(mOverlayMode && !mOverlayWallpaper) &&
           mLaunchFadeStart == 0 && !mOverlayLaunchPending &&
           !property_get_bool("sys.gammaos.nano.app_launched", false) &&
           property_get_bool("persist.gammaos.nano.nds.ambiance", true);   // Menu Music, applied live
}

// The title behind the in-game overlay as the grid knows it: a game by its ROM (its system's
// entry and its Recently Played one), an app by its package.
std::vector<std::string> NanoMenu::ctrSuspendedIds() {
    std::vector<std::string> ids;
    const std::string rom = readPathFile("/data/system/nano_launch_rom.txt");
    if (!rom.empty()) {
        ids.push_back("rom:" + rom);
        ids.push_back("recent:" + rom);
        return ids;
    }
    std::string pkg = mOverlayPausedPkg;
    if (pkg.empty()) {
        char la[PROPERTY_VALUE_MAX] = {};
        property_get("sys.gammaos.nano.launched_pkg", la, "");
        pkg = la;
    }
    if (!pkg.empty()) ids.push_back("app:" + pkg);
    return ids;
}

// Raised over a running title: its screens as they are now (the overlay layer is still hidden),
// for the home's pause fade. Handed over by renderCtrPrimary once the home is loaded.
void NanoMenu::ctrCaptureSuspend() {
    ctrDropSuspend();
    int tw = 0, th = 0, bw = 0, bh = 0;
    const GLuint top = overlayCaptureInProcess(&tw, &th, false);
    if (!top) {
        ALOGW("ctr: in-game overlay without a capture of the title");
        return;
    }
    const GLuint bottom = overlayCaptureInProcess(&bw, &bh, true);
    mCtrSuspendTop = top;
    mCtrSuspendBottom = bottom;
    mCtrSuspendTopW = tw; mCtrSuspendTopH = th;
    mCtrSuspendBotW = bw; mCtrSuspendBotH = bh;
    mCtrSuspendPending = true;
    ALOGI("ctr: title captured for the suspend (%dx%d, %dx%d)", tw, th, bw, bh);
}

void NanoMenu::ctrDropSuspend() {
    if (mCtrSuspendTop) { GLuint t = mCtrSuspendTop; glDeleteTextures(1, &t); }
    if (mCtrSuspendBottom) { GLuint t = mCtrSuspendBottom; glDeleteTextures(1, &t); }
    mCtrSuspendTop = mCtrSuspendBottom = 0;
    mCtrSuspendPending = false;
}

void NanoMenu::ctrLeave() {
    if (mCtrAudio) {
        mCtrAudio->stopAll(0);
        mCtrAudio->release();
    }
    ctrRelease();
    mCtrHeld = 0;
    mCtrStickX = mCtrStickY = 0;
    mCtrModelSig = 0;
}

// nano's navigation sounds (PS3_SFX_*) as the console's effects
void NanoMenu::ctrSfx(int which) {
    if (!mCtrHost || mCtrSfxMute) return;
    static const char* kNames[] = {"SE_CTR_HOME_SELECT", "SE_CTR_COMMON_BUTTON", "SE_CTR_COMMON_CANCEL",
                                   "SE_CTR_COMMON_BUTTON", "SE_CTR_COMMON_DIALOG", "SE_CTR_COMMON_ERROR"};
    if (which < 0 || which >= (int)(sizeof(kNames) / sizeof(kNames[0]))) return;
    mCtrHost->playSound(kNames[which]);
}

// ---------------------------------------------------------------------------------------- render

// Frame cost probe (sys.gammaos.nano.ctr.perf: 1 = CPU times, 2 = also GPU via glFinish), logged
// every 120 frames: frame interval, home update, top screen, bottom screen.
namespace {
// GPU time per pass (sys.gammaos.nano.ctr.perf 3): EXT_disjoint_timer_query elapsed-time queries,
// read back a few frames later, logged with the CPU probe.
struct CtrGpuTimer {
    PFNGLGENQUERIESEXTPROC gen = nullptr;
    PFNGLBEGINQUERYEXTPROC begin = nullptr;
    PFNGLENDQUERYEXTPROC end = nullptr;
    PFNGLGETQUERYOBJECTUI64VEXTPROC result = nullptr;
    PFNGLGETQUERYOBJECTUIVEXTPROC avail = nullptr;
    bool tried = false, open = false;
    static const int kPasses = 16, kDepth = 4;
    GLuint q[kDepth][kPasses] = {};
    bool used[kDepth][kPasses] = {};
    int frame = 0;
    double sum[kPasses] = {};
    int n[kPasses] = {};
    std::string names[kPasses];
    int count = 0;
    bool ok() {
        if (!tried) {
            tried = true;
            const char* ext = (const char*)glGetString(GL_EXTENSIONS);
            if (ext && strstr(ext, "GL_EXT_disjoint_timer_query")) {
                gen = (PFNGLGENQUERIESEXTPROC)eglGetProcAddress("glGenQueriesEXT");
                begin = (PFNGLBEGINQUERYEXTPROC)eglGetProcAddress("glBeginQueryEXT");
                end = (PFNGLENDQUERYEXTPROC)eglGetProcAddress("glEndQueryEXT");
                result = (PFNGLGETQUERYOBJECTUI64VEXTPROC)eglGetProcAddress("glGetQueryObjectui64vEXT");
                avail = (PFNGLGETQUERYOBJECTUIVEXTPROC)eglGetProcAddress("glGetQueryObjectuivEXT");
            }
            if (gen) gen(kDepth * kPasses, &q[0][0]);
            ALOGI("ctr perf: gpu timers %s", gen ? "on" : "unavailable");
        }
        return gen != nullptr;
    }
    // ends the running span (if any) and starts the one named `next` (nullptr: none)
    void mark(const char* next) {
        if (!ok()) return;
        const int slot = frame % kDepth;
        if (open) { end(GL_TIME_ELAPSED_EXT); open = false; }
        if (!next) return;
        int idx = 0;
        while (idx < count && names[idx] != next) idx++;
        if (idx == count) {
            if (count == kPasses) return;
            names[count++] = next;
        }
        if (used[slot][idx]) return;   // a span runs once per frame
        begin(GL_TIME_ELAPSED_EXT, q[slot][idx]);
        used[slot][idx] = true;
        open = true;
    }
    void frameStart() {
        if (!ok()) return;
        mark(nullptr);
        frame++;
        const int slot = frame % kDepth;   // the oldest set: read it, then reuse it
        for (int p = 0; p < count; p++) {
            if (!used[slot][p]) continue;
            GLuint a = 0;
            avail(q[slot][p], GL_QUERY_RESULT_AVAILABLE_EXT, &a);
            if (a) {
                GLuint64 ns = 0;
                result(q[slot][p], GL_QUERY_RESULT_EXT, &ns);
                sum[p] += ns / 1e6;
                n[p]++;
            }
            used[slot][p] = false;
        }
    }
    void log() {
        std::string line;
        double total = 0;
        for (int p = 0; p < count; p++) {
            const double v = n[p] ? sum[p] / n[p] : 0;
            total += v;
            char b[64];
            snprintf(b, sizeof(b), " %s %.2f", names[p].c_str(), v);
            line += b;
            sum[p] = 0;
            n[p] = 0;
        }
        ALOGI("ctr perf gpu ms:%s (total %.2f)", line.c_str(), total);
    }
};
CtrGpuTimer sCtrGpu;

ctr::CtrHome* sCtrPerfHome = nullptr;   // the home the probe reads cache statistics from
struct CtrPerf {
    int mode = 0, frames = 0;
    double interval = 0, worst = 0, update = 0, top = 0, bottom = 0;
    nsecs_t last = 0;
    static double ms(nsecs_t a, nsecs_t b) { return (double)(b - a) / 1e6; }
    void refresh() {
        static const prop_info* pi = nullptr;
        static uint32_t ser = ~0u;
        if (!pi) pi = __system_property_find("sys.gammaos.nano.ctr.perf");
        if (pi && __system_property_serial(pi) != ser) {
            ser = __system_property_serial(pi);
            mode = property_get_int32("sys.gammaos.nano.ctr.perf", 0);
        }
    }
    nsecs_t mark() {
        if (mode == 2) glFinish();
        return systemTime(SYSTEM_TIME_MONOTONIC);
    }
    void frame(nsecs_t now) {
        if (last) {
            const double d = ms(last, now);
            interval += d;
            worst = std::max(worst, d);
        }
        last = now;
        if (++frames < 120) return;
        if (mode == 3) sCtrGpu.log();
        int hits = 0, misses = 0;
        if (sCtrPerfHome) sCtrPerfHome->takeCacheStats(&hits, &misses);
        ALOGI("ctr perf: interval %.2f ms (worst %.2f) update %.2f top %.2f bottom %.2f%s cache %d/%d", interval / frames, worst,
              update / frames, top / frames, bottom / frames, mode >= 2 ? " (gpu synced)" : "", hits, misses);
        *this = CtrPerf{mode, 0, 0, 0, 0, 0, 0, now};
    }
};
CtrPerf sCtrPerf;

}  // namespace

bool NanoMenu::ctrDualPanel() const {
    if (mCtrForceSingle) return false;
    return sAhbTargetSecondary.glFbo != 0 || !mSecondaryEglSurfaces.empty();
}

// Layout width a screen gets in a rectangle: wider than its design width when the rectangle is
// (widescreen adaptation, persist.gammaos.nano.ctr.widen, default on), capped at 2.4:1.
static float ctrScreenWidth(bool top, float rw, float rh) {
    const float design = top ? 400.0f : 320.0f;
    if (rh <= 0 || !property_get_bool("persist.gammaos.nano.ctr.widen", true)) return design;
    return std::clamp(240.0f * rw / rh, design, 240.0f * 2.4f);
}

// Layout height of the top screen in a rectangle: taller than its design 240 when the rectangle
// is narrower than 5:3 (3:2, 4:3), up to 4:3. Beyond that the wall model's curved ends would show
// at the top and bottom, so squarer and portrait panels letterbox the rest.
static float ctrTopHeight(float rw, float rh) {
    if (rw <= 0 || !property_get_bool("persist.gammaos.nano.ctr.widen", true)) return 240.0f;
    return std::clamp(400.0f * rh / rw, 240.0f, 300.0f);
}

// Test hook: sys.gammaos.nano.ctr.screensize="WxH" draws each screen of a dual panel device (the
// top one on its panel, the bottom one on its own) into a centred W x H pixel rectangle, as a panel
// of that size shows it (640x480 on the 1080p Duo Lite). False when unset. Read when it changes.
bool ctrTestScreenRect(float W, float H, float* rx, float* ry, float* rw, float* rh) {
    static const prop_info* pi = nullptr;
    static uint32_t ser = ~0u;
    static int sw = 0, sh = 0;
    if (!pi) pi = __system_property_find("sys.gammaos.nano.ctr.screensize");
    if (pi && __system_property_serial(pi) != ser) {
        ser = __system_property_serial(pi);
        char v[PROPERTY_VALUE_MAX] = {};
        property_get("sys.gammaos.nano.ctr.screensize", v, "");
        if (sscanf(v, "%dx%d", &sw, &sh) != 2 || sw <= 0 || sh <= 0) sw = sh = 0;
    }
    if (!sw || !sh) return false;
    *rw = std::min(W, (float)sw);
    *rh = std::min(H, (float)sh);
    *rx = floorf((W - *rw) / 2);
    *ry = floorf((H - *rh) / 2);
    return true;
}

// Test hook: sys.gammaos.nano.ctr.topaspect="w:h" draws the top screen of a dual panel device
// into a centred rectangle of that aspect (checks 4:3, 3:2... on a 16:9 panel).
static void ctrTestTopRect(float W, float H, float* rx, float* ry, float* rw, float* rh) {
    if (ctrTestScreenRect(W, H, rx, ry, rw, rh)) return;
    char v[PROPERTY_VALUE_MAX] = {};
    property_get("sys.gammaos.nano.ctr.topaspect", v, "");
    float a = 0, b = 0;
    if (sscanf(v, "%f:%f", &a, &b) != 2 || a <= 0 || b <= 0) return;
    const float want = a / b;
    if (W / H > want) { *rw = H * want; *rh = H; } else { *rw = W; *rh = W / want; }
    *rx = (W - *rw) / 2;
    *ry = (H - *rh) / 2;
}

// Contain-fit a screen of lw x lh layout units into a rectangle (centred).
static void ctrFit(float lw, float lh, float rx, float ry, float rw, float rh, float* ox, float* oy, float* ow, float* oh) {
    const float s = std::min(rw / lw, rh / lh);
    *ow = lw * s;
    *oh = lh * s;
    *ox = rx + (rw - *ow) * 0.5f;
    *oy = ry + (rh - *oh) * 0.5f;
}

void NanoMenu::ctrBottomRect(float& rx, float& ry, float& rw, float& rh) const {
    // the bottom screen's own panel (dual) or its part of this one
    float x = 0, y = 0, w = (float)mWidth, h = (float)mHeight;
    if (ctrDualPanel()) {
        if (sAhbTargetSecondary.glFbo) {   // DRM: the bottom panel's scanout buffer
            w = (float)sAhbTargetSecondary.w;
            h = (float)sAhbTargetSecondary.h;
        } else if (!mSecondaryCreatedSize.empty() && mSecondaryCreatedSize[0].first > 0) {
            w = (float)mSecondaryCreatedSize[0].first;
            h = (float)mSecondaryCreatedSize[0].second;
        }
        ctrTestScreenRect(w, h, &x, &y, &w, &h);
    } else {   // one panel: the two screens stacked, the bottom one in the lower half
        y = h * 0.5f;
        h *= 0.5f;
    }
    const float lw = ctrScreenWidth(false, w, h);
    ctrFit(lw, 240, x, y, w, h, &rx, &ry, &rw, &rh);
}

void NanoMenu::ctrDrawScreen(bool top, float rx, float ry, float rw, float rh) {
    if (sCtrPerf.mode == 4) return;   // probe: the present path's own ceiling
    const float lw = top ? mCtrHome->topWidth() : mCtrHome->bottomWidth();
    const float lh = top ? mCtrHome->topHeight() : 240.0f;
    float ox, oy, ow, oh;
    ctrFit(lw, lh, rx, ry, rw, rh, &ox, &oy, &ow, &oh);
    // vector glyphs are drawn from fields as fine as the larger screen magnifies the layout (one
    // value for both screens: the atlases rebuild when it changes)
    {
        static float sMag[2] = {0, 0};
        sMag[top ? 0 : 1] = lw > 0 ? ow / lw : 1.0f;
        ctr::Font::setVectorScale((int)ceilf(std::max(sMag[0], sMag[1]) - 0.1f));
    }
    // letterbox bars in the screen's own ground colour (only outside the screen: every pixel
    // filled twice costs fill rate on low end GPUs)
    const float g = top ? 232.0f / 255.0f : 0.0f, gb = top ? 238.0f / 255.0f : 0.0f;
    if (oy > ry + 0.5f) drawQuad(rx, ry, rw, oy - ry, g, g, gb, 1.0f);
    if (oy + oh < ry + rh - 0.5f) drawQuad(rx, oy + oh, rw, ry + rh - oy - oh, g, g, gb, 1.0f);
    if (ox > rx + 0.5f) drawQuad(rx, oy, ox - rx, oh, g, g, gb, 1.0f);
    if (ox + ow < rx + rw - 0.5f) drawQuad(ox + ow, oy, rx + rw - ox - ow, oh, g, g, gb, 1.0f);
    // the top screen ground (banner.ts TOP_CLEAR) under the layouts, unless the opaque 3D wall
    // covers it; the bottom base layout is drawn onto the panel's black clear
    if (top && !mCtrHome->topCovered()) drawQuad(ox, oy, ow, oh, 232.0f / 255.0f, 232.0f / 255.0f, 238.0f / 255.0f, 1.0f);
    float proj[16];
    ctr::CtrHome::projection(proj, lw, lh, ox, oy, ow, oh, (float)mWidth, (float)mHeight, sDrmRotMat);
    GLint vp[4];
    glGetIntegerv(GL_VIEWPORT, vp);
    if (top) mCtrHome->drawTop(proj, vp[0], vp[1], vp[2], vp[3]);
    else mCtrHome->drawBottom(proj, vp[0], vp[1], vp[2], vp[3]);
}

// Cheap signature of what the grid shows: the Game category entries and every system's game
// count. Checked every 30 frames; a change (scan finished, settings changed) reloads the grids.
uint64_t NanoMenu::ctrModelSignature() const {
    uint64_t h = 1469598103934665603ull;
    auto mix = [&](uint64_t v) { h ^= v; h *= 1099511628211ull; };
    for (const Ps3Cat& c : mPs3Cats) {
        if (c.name != "Game") continue;
        for (const Ps3Item& it : c.items) {
            mix((uint64_t)it.kind);
            mix((uint64_t)(uint32_t)it.a);
            mix((uint64_t)(uint32_t)it.b);
            // a system's icon tint (its solid icon's colour) changes without a rescan
            mix((uint64_t)(uint32_t)(it.iconR * 255) << 16 | (uint32_t)(it.iconG * 255) << 8 | (uint32_t)(it.iconB * 255));
            for (char ch : it.label) mix((uint64_t)(unsigned char)ch);
        }
    }
    for (const XmbSystem& s : mXmbSystems) mix((uint64_t)s.roms.size());
    mix((uint64_t)mXmbRecent.size());
    mix((uint64_t)mNdsBannerNamesRev);
    if (mCtrCat != CTR_CAT_GAMES) {
        // the category on the grid: its rows (a library scan, the Quick Menu's live rows), the
        // lists its folders show (playlists, the IPTV and radio lists arriving) and its notices
        static const char* const kNames[CTR_CAT_COUNT] = {"Game", "Quick Menu", "Music", "Video", "Photo", "Network"};
        for (const Ps3Cat& c : mPs3Cats) {
            if (c.name != kNames[mCtrCat]) continue;
            for (const Ps3Item& it : c.items) {
                mix((uint64_t)it.kind);
                mix((uint64_t)(uint32_t)it.a);
                mix((uint64_t)(uint32_t)it.b);
                for (char ch : it.label) mix((uint64_t)(unsigned char)ch);
            }
        }
        mix(mMusicPlaylists.size());
        mix(mVideoPlaylists.size());
        mix(mPhotoPlaylists.size());
        mix(property_get_bool("persist.gammaos.nano.iptv.agreed", false));
        mix(property_get_bool("persist.gammaos.nano.radio.agreed", false));
        NanoMenu* self = const_cast<NanoMenu*>(this);
        {
            std::lock_guard<std::mutex> lk(self->mIptvMutex);
            mix(mIptvCats.size());
            mix(std::hash<std::string>()(mIptvStatus));
        }
        {
            std::lock_guard<std::mutex> lk(self->mRadioMutex);
            mix(mRadioCats.size());
            mix(std::hash<std::string>()(mRadioStatus));
        }
    }
    return h;
}


void NanoMenu::renderCtrPrimary() {
    {   // test hook: lay out as a single panel device (sys.gammaos.nano.ctr.single=1)
        static const prop_info* spi = nullptr;
        static uint32_t sser = ~0u;
        if (!spi) spi = __system_property_find("sys.gammaos.nano.ctr.single");
        if (spi && __system_property_serial(spi) != sser) {
            sser = __system_property_serial(spi);
            mCtrForceSingle = property_get_bool("sys.gammaos.nano.ctr.single", false);
        }
    }
    sCtrPerf.refresh();
    sCtrPerfHome = mCtrHome;
    if (sCtrPerf.mode != 3) ctr::CtrHome::perfMark = nullptr;
    if (sCtrPerf.mode == 3) {
        sCtrGpu.frameStart();
        ctr::CtrHome::perfMark = [](const char* n) { sCtrGpu.mark(n); };
        sCtrGpu.mark("pre");
    }
    const nsecs_t perfStart = sCtrPerf.mode ? sCtrPerf.mark() : 0;
    if (sCtrPerf.mode) sCtrPerf.frame(perfStart);
    if (!mPs3MenuBuilt) initPs3Menu();   // the shared XMB hierarchy feeds the grid (systems, apps)
    if (ctrEnsure()) ctrModalUpdate();
    if (!ctrEnsure()) {
        renderPs3Xmb();
        return;
    }
    if (mCtrSuspendPending) {
        // the in-game overlay's first frame: the pause fade starts from the title's screens
        mCtrSuspendPending = false;
        mCtrHome->suspendApp(ctrSuspendedIds(), mCtrSuspendTop, mCtrSuspendTopW, mCtrSuspendTopH, mCtrSuspendBottom,
                             mCtrSuspendBotW, mCtrSuspendBotH);
        mCtrSuspendTop = mCtrSuspendBottom = 0;   // the home owns them now
    }
    if (++mCtrSigFrame >= 30) {
        mCtrSigFrame = 0;
        const uint64_t sig = ctrModelSignature();
        if (sig != mCtrModelSig) {
            mCtrModelSig = sig;
            mCtrHome->reloadItems();
        }
    }
    // input for this frame: held buttons (+ the circle pad as the D-pad), then the 60 Hz ticks
    uint32_t held = mCtrHeld;
    if (mCtrStickX < 0) held |= ctr::CTR_LEFT;
    if (mCtrStickX > 0) held |= ctr::CTR_RIGHT;
    if (mCtrStickY < 0) held |= ctr::CTR_UP;
    if (mCtrStickY > 0) held |= ctr::CTR_DOWN;
    // scripted keys for tests (sys.gammaos.nano.ctr.keys), read only when the prop changes
    {
        static const prop_info* pi = nullptr;
        static uint32_t ser = 0;
        if (!pi) pi = __system_property_find("sys.gammaos.nano.ctr.keys");
        if (pi && __system_property_serial(pi) != ser) {
            ser = __system_property_serial(pi);
            char v[PROPERTY_VALUE_MAX] = {};
            property_get("sys.gammaos.nano.ctr.keys", v, "");
            static const struct { const char* n; uint32_t b; } kNames[] = {
                {"A", ctr::CTR_A}, {"B", ctr::CTR_B}, {"X", ctr::CTR_X}, {"Y", ctr::CTR_Y},
                {"L", ctr::CTR_L}, {"R", ctr::CTR_R}, {"UP", ctr::CTR_UP}, {"DOWN", ctr::CTR_DOWN},
                {"LEFT", ctr::CTR_LEFT}, {"RIGHT", ctr::CTR_RIGHT}, {"HOME", 0x80000000u}};
            std::string list = v;
            size_t p = 0;
            while (!list.empty() && p <= list.size()) {
                size_t q = list.find(',', p);
                const std::string k = list.substr(p, q == std::string::npos ? std::string::npos : q - p);
                for (const auto& n : kNames) if (k == n.n) mCtrScript.push_back(n.b);
                if (q == std::string::npos) break;
                p = q + 1;
            }
        }
        // test hook: sys.gammaos.nano.ctr.open=<root item name> opens that folder (retried while
        // a folder closes first)
        {
            static const prop_info* opi = nullptr;
            static uint32_t oser = 0;
            static std::string want;
            if (!opi) opi = __system_property_find("sys.gammaos.nano.ctr.open");
            if (opi && __system_property_serial(opi) != oser) {
                oser = __system_property_serial(opi);
                char v[PROPERTY_VALUE_MAX] = {};
                property_get("sys.gammaos.nano.ctr.open", v, "");
                want = v;
            }
            if (!want.empty() && mCtrHome->testOpen(want)) want.clear();
        }
        if (!mCtrScript.empty()) {
            const int f = mCtrScriptFrame++;
            if (f < 6) {
                if (mCtrScript.front() == 0x80000000u) { if (f == 0) mCtrHome->pressHome(); }
                else held |= mCtrScript.front();
            } else if (f >= 12) {
                mCtrScript.erase(mCtrScript.begin());
                mCtrScriptFrame = 0;
            }
        }
    }
    if (!ctrOwnsInput()) {
        // a nano list or dialog took the keys: nothing stays held behind it
        held = 0;
        mCtrHeld = 0;
        mCtrStickX = mCtrStickY = 0;
    }
    mCtrHome->setHeld(held);
    ctrApplyTheme();
    // screen widths from the rectangles each screen is drawn in
    {
        const float W = (float)mWidth, H = (float)mHeight;
        float topW = 400, botW = 320;
        float bx, by, bw, bh;
        ctrBottomRect(bx, by, bw, bh);
        botW = ctrScreenWidth(false, bw, bh);
        float tx = 0, ty = 0, tw = W, th = ctrDualPanel() ? H : H * 0.5f;
        if (ctrDualPanel()) ctrTestTopRect(W, H, &tx, &ty, &tw, &th);
        topW = ctrScreenWidth(true, tw, th);
        mCtrHome->setScreenWidths(topW, botW);
        mCtrHome->setTopHeight(ctrTopHeight(tw, th));
    }
    {
        // debug boot recording (sys.gammaos.nano.bootrec): the home's entry after the splash runs on
        // the recorder's clock too, one 60 Hz tick per frame written, so it is captured as it plays
        static int64_t sRecNs = 0;
        int64_t now = systemTime(SYSTEM_TIME_MONOTONIC);
        if (gNanoBootRec.on) now = sRecNs = sRecNs ? sRecNs + 16666667 : now;
        else sRecNs = 0;
        mCtrHome->update(now);
    }
    ctrUpdateWallpapers();
    const nsecs_t perfUpdated = sCtrPerf.mode ? sCtrPerf.mark() : 0;
    if (sCtrPerf.mode) sCtrPerf.update += CtrPerf::ms(perfStart, perfUpdated);
    const float W = (float)mWidth, H = (float)mHeight;
    if (ctrDualPanel()) {
        float tx = 0, ty = 0, tw = W, th = H;
        ctrTestTopRect(W, H, &tx, &ty, &tw, &th);
        if (tw < W || th < H) drawQuad(0, 0, W, H, 0, 0, 0, 1);   // test: the rest of the panel black
        ctrDrawScreen(true, tx, ty, tw, th);
        if (sCtrPerf.mode) sCtrPerf.top += CtrPerf::ms(perfUpdated, sCtrPerf.mark());
    } else {
        // one panel, any aspect: the top screen over the bottom one (the usual single screen
        // layout of the 3DS), each widened to its half like the screens of a dual panel device
        ctrDrawScreen(true, 0, 0, W, H * 0.5f);
        ctrDrawScreen(false, 0, H * 0.5f, W, H * 0.5f);
    }
    // nano's screens the 3DS style does not cover yet (pickers, keyboard, wizard, players) keep
    // the shared XMB chrome; lists, option menus and dialogs are the 3DS panel and dialog
    if (ctrModalKind() == CTR_MODAL_FALLBACK) renderPs3Xmb();
    else ctrRenderOverlays();
    if (sCtrPerf.mode == 3) sCtrGpu.mark("rest");   // the rest of nano's frame until the next bottom pass
}

// The keyboard and the global search results over the 3DS screens, without the XMB under them.
// A dual panel device draws them on the bottom panel (the secondary passes call renderGlobalSearch
// and renderOsk), so the top screen only dims behind them; one panel draws them here.
void NanoMenu::ctrRenderOverlays() {
    if (!mOskActive && !mGSearchActive) return;
    if (ctrSearchThemed()) return;   // the results are the 3DS panel (ctrSearchUpdate)
    if (ctrDualPanel()) {
        setUiBlend();   // the 3DS renderer leaves its own blend state
        drawQuad(0, 0, (float)mWidth, (float)mHeight, 0, 0, 0, 0.45f);
        return;
    }
    if (mGSearchActive && !ctrSearchThemed()) renderGlobalSearch();
    renderOsk();
}

// ------------------------------------------------------------------------------------ cold boot

// The splash's top screen in a rectangle of the current panel (the whole top panel, or the upper
// half of one panel), laid out like the HOME Menu's top screen there.
void NanoMenu::ctrBootLogo(float rx, float ry, float rw, float rh, double leaveAt) {
    if (!mCtrBoot) {
        std::string root;
        for (const char* d : kCtrAssetDirs) {
            struct stat st;
            if (stat((std::string(d) + "/lyt_logo.cpk").c_str(), &st) == 0) { root = d; break; }
        }
        if (root.empty()) ALOGE("ctr: boot splash (lyt_logo.cpk) not found");
        ctr::setProgramCacheDir("/data/system/nano_cache/ctr_shaders");
        mCtrBoot = new ctr::CtrBootSplash(root.empty() ? std::string(kCtrAssetDirs[1]) : root);
        if (root.empty() || !mCtrBoot->load()) return;   // black with the notice, nothing to retry
    }
    if (!mCtrBoot->pose(mDsiBootFrame, leaveAt)) return;
    // test hook sys.gammaos.nano.ctr.topaspect: the top panel of a dual panel device shows the
    // splash in a centred rectangle of that aspect, like the HOME Menu's top screen (renderCtrPrimary)
    if (ctrDualPanel() && rx == 0 && ry == 0 && rw == (float)mWidth && rh == (float)mHeight)
        ctrTestTopRect(rw, rh, &rx, &ry, &rw, &rh);
    // The logo at the magnification the HOME Menu's top screen gets in this rectangle; the layout
    // then spans the WHOLE rectangle at that scale (any aspect, portrait and square included), so
    // the sweeps reach every edge instead of stopping at a letterbox.
    const float hw = ctrScreenWidth(true, rw, rh), hh = ctrTopHeight(rw, rh);
    float ox, oy, ow, oh;
    ctrFit(hw, hh, rx, ry, rw, rh, &ox, &oy, &ow, &oh);
    const float scale = ow / hw;
    if (scale <= 0.0f) return;
    const float lw = std::max(400.0f, rw / scale), lh = std::max(240.0f, rh / scale);
    ctrFit(lw, lh, rx, ry, rw, rh, &ox, &oy, &ow, &oh);
    float proj[16];
    ctr::CtrHome::projection(proj, lw, lh, ox, oy, ow, oh, (float)mWidth, (float)mHeight, sDrmRotMat);
    GLint vp[4];
    glGetIntegerv(GL_VIEWPORT, vp);
    mCtrBoot->draw(proj, vp[0], vp[1], vp[2], vp[3], lw, lh, ow, oh);
    setUiBlend();   // the layout renderer leaves its own blend state
}

void NanoMenu::ctrBootRelease() {
    if (!mCtrBoot) return;
    mCtrBoot->releaseGl();
    delete mCtrBoot;
    mCtrBoot = nullptr;
}

void NanoMenu::renderCtrSecondary() {
    if (!mCtrHome || !mCtrHome->loaded() || mCtrForceSingle) return;
    ctrModalUpdate();   // the secondary pass runs first in a frame
    const nsecs_t t0 = sCtrPerf.mode ? sCtrPerf.mark() : 0;
    if (sCtrPerf.mode == 3) sCtrGpu.mark("bottom");   // also ends the "rest of frame" span
    float x = 0, y = 0, w = (float)mWidth, h = (float)mHeight;
    if (ctrTestScreenRect(w, h, &x, &y, &w, &h)) drawQuad(0, 0, (float)mWidth, (float)mHeight, 0, 0, 0, 1);
    ctrDrawScreen(false, x, y, w, h);
    if (sCtrPerf.mode == 3) sCtrGpu.mark(nullptr);
    if (sCtrPerf.mode) sCtrPerf.bottom += CtrPerf::ms(t0, sCtrPerf.mark());
}

}  // namespace android
