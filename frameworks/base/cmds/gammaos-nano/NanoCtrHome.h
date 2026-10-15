// NanoCtrHome - the 3DS HOME Menu theme for nano (launcher, top screen, status bar).
//
// A port of the 3ds-web menu runtime (/work/3ds/web/app/src/menu, docs/menu.md and
// docs/re/launcher.md) onto the NanoCtrLayout engine. It is self contained: nano feeds it the
// items to show and receives launches through CtrHomeHost, drives it with buttons and touches,
// and asks it to draw the top and bottom screens into rectangles of its panels. Timing follows
// the console's 60 Hz frames; update() runs whole ticks from real time.
#pragma once

#include <cstdint>
#include <functional>
#include <memory>
#include <string>
#include <vector>

#include "NanoCtrLayout.h"
#include "NanoCtrMyMenu.h"

namespace android {
namespace ctr {

struct RomBanner;

// 3DS HID bits (docs/menu.md "Input").
enum CtrButton : uint32_t {
    CTR_A = 0x1, CTR_B = 0x2, CTR_SELECT = 0x4, CTR_START = 0x8,
    CTR_RIGHT = 0x10, CTR_LEFT = 0x20, CTR_UP = 0x40, CTR_DOWN = 0x80,
    CTR_R = 0x100, CTR_L = 0x200, CTR_X = 0x400, CTR_Y = 0x800,
    CTR_ZL = 0x4000, CTR_ZR = 0x8000,
};

enum class HomeItemKind : uint8_t { Title, Folder };

// One grid entry as nano describes it.
struct HomeItem {
    HomeItemKind kind = HomeItemKind::Title;
    std::string id;            // host identity (stable across rebuilds)
    std::string name;          // title name / folder name
    std::string publisher;     // second line of the name balloon (system, publisher)
    std::string plate;         // titles: the whole top screen name plate when set (a DS banner's lines)
    std::string folderMark;    // folders: drawn on the folder instead of the initial (unused yet)
    int slot = -1;             // explicit grid slot (empty slots before it), -1 = the next slot
    bool folderFilled = true;  // folders: something inside (the icon shows its contents tab)
    bool monoIcon = false;     // the art is a white glyph (nano system icons): drawn dark
};

struct HomeStatus {
    int wifiSignal = -1;       // -1 off, 0..3 bars
    bool wifiConnected = false;
    int batteryLevel = 5;      // 0..5 (0 and 1 blink)
    bool charging = false;
    bool adapter = false;
};

class ThemeManager;
struct ThemeBody;

class CtrHomeHost {
public:
    virtual ~CtrHomeHost() = default;
    // Items of a folder ("" = the root grid), in grid order.
    virtual std::vector<HomeItem> items(const std::string& folderId) = 0;
    // 48x48 RGBA art of a title (top row first); false while it is not available yet.
    virtual bool titleIcon(const HomeItem& item, std::vector<uint8_t>* rgba, int* w, int* h) = 0;
    // Or an existing GL texture of the host (not owned; top row first). 0 = none.
    // What a title's icon texture is: nano's white system glyph (shown in the folder letters'
    // grey), a square colour icon (3DS SMDH, DS banner icon), or a cover of `coverAspect`
    // (width / height) fitted whole into the square icon.
    // pixelArt: a pixel icon (a DS cartridge's 32x32) drawn with nearest neighbour, never blurred.
    struct IconArt { bool mono = false; float coverAspect = 0; bool pixelArt = false; };
    // Asked every frame for the titles on screen: art that arrives later replaces the glyph.
    virtual GLuint titleIconTexture(const HomeItem& item, int* w, int* h, IconArt* art) {
        (void)item; (void)w; (void)h; (void)art; return 0;
    }
    // A title's art for its top screen banner: a 3DS ROM's CBMD banner (`pending` while it is
    // being read), else an icon (a DS cartridge's own icon, or art like a cover).
    struct TitleArt {
        std::shared_ptr<const RomBanner> cbmd;
        bool pending = false;
        GLuint icon = 0;
        int w = 0, h = 0;
        IconArt art;
        bool dsIcon = false;   // `icon` is a DS banner icon
        bool dsCard = false;   // a DS game (the cartridge model, with or without its icon)
    };
    virtual void titleArt(const HomeItem& item, TitleArt* out) { (void)item; (void)out; }
    virtual void playBannerSound(const std::shared_ptr<const RomBanner>& banner) { (void)banner; }
    virtual void stopBannerSound() {}
    // Changes when the host freed or replaced art textures (their GL names may be reused).
    virtual uint64_t artGeneration() { return 0; }
    // Starts a title. False when it cannot start now (the menu stays as it is).
    virtual bool launch(const HomeItem& item) = 0;
    // Applet bar buttons 0..5 (0 = HOME Menu Settings).
    virtual void appletBar(int button) = 0;
    // Name on the applet banner plate of bar button 1..5 ("" = none).
    virtual std::string appletName(int button) { (void)button; return std::string(); }
    virtual HomeStatus status() = 0;
    // B on the root grid (nothing to close): nano resumes the app it is the overlay of.
    virtual void backAtRoot() {}
    // ---- arranging (moving.ts, folders.ts): the root grid and the user's own folders can be
    // arranged and the host keeps their layout; generated folders (a system's games) are not
    // arranged, their titles can be put into the user's folders or onto the root
    virtual bool editable(const std::string& folderId) { (void)folderId; return false; }   // "" is the root
    virtual bool userFolder(const std::string& folderId) { (void)folderId; return false; }
    // The slots of an arranged grid after a move (slot, item id); the host saves them.
    virtual void layoutChanged(const std::string& folderId, const std::vector<std::pair<int, std::string>>& slots) {
        (void)folderId; (void)slots;
    }
    // A new folder on an empty root slot; its id ("" when there cannot be another).
    virtual std::string createFolder(int slot, const std::string& name) { (void)slot; (void)name; return std::string(); }
    // A folder's Settings button (rename, delete).
    virtual void folderSettings(const std::string& folderId) { (void)folderId; }
    // A notice the menu shows as a dialog with OK (a message label of the menu, e.g.
    // lau_dlg_folder_icon_full) and its text in the active language.
    virtual void notice(const std::string& label, const std::string& text) { (void)label; (void)text; }
    // Plays a menu sound by BCSAR name (SE_CTR_*, BGM_*); returns a handle for loops.
    virtual int playSound(const char* name) { (void)name; return 0; }
    // The saved layouts of Save/Load Layout (My Menu); null leaves the scene unavailable.
    virtual MyMenuStore* myMenuStore() { return nullptr; }
    virtual void stopSound(int handle, int fadeFrames) { (void)handle; (void)fadeFrames; }
    virtual bool soundPlaying(int handle) { (void)handle; return true; }
    // The suspended title (CtrHome::suspendApp): the fade back into it has played, the title
    // takes the screens again; Close was confirmed, the title is to end now (the closing notice
    // keeps showing while it goes).
    virtual void resumeApp() {}
    virtual void closeApp() {}
    // Enlarge / Shrink Icons settled on a new zoom: of the root grid, or of a folder (folders all
    // open at the last folder zoom). The host keeps them for the next session (setZooms).
    virtual void zoomChanged(bool folder, int zoom) { (void)folder; (void)zoom; }
    // A folder's own colour (a game system's icon tint, Game Systems settings): its solid icon on
    // the top screen takes it instead of the theme's folder colours. False: no colour of its own.
    virtual bool itemTint(const HomeItem& item, float rgb[3]) { (void)item; (void)rgb; return false; }
    // L / R on the grid: the next or the previous of the host's categories (the host calls
    // showCategory once it switched).
    virtual void stepCategory(int dir) { (void)dir; }
    // A title the host opens itself without leaving the home (a nano list, a player): true when
    // it did, the home plays no start animation.
    virtual bool openInHost(const HomeItem& item) { (void)item; return false; }
};

struct CtrTouch {
    bool down = false, held = false, up = false;
    float x = 0, y = 0;   // bottom screen layout space (x - 160, 120 - y)
};

class CtrHome {
public:
    CtrHome(std::string assetRoot, CtrHomeHost* host);
    ~CtrHome();

    // Loads layouts (needs the GL context current). False when the assets are missing.
    bool load();
    bool loaded() const { return mLoaded; }
    void releaseGl();
    // While an app runs (the home parked): frees the GPU memory the next frame can rebuild
    // (render targets, the bottom cache, derived icons, glyph atlases, programs), keeping the
    // layouts and models it would otherwise have to load again.
    void parkGl();
    void setLanguage(const std::string& language);

    // Input for the next ticks: held buttons (BTN_*), HOME presses, bottom screen touch.
    void setHeld(uint32_t held) { mHeldNext = held; }
    void pressHome() { mHomeNext = true; }
    void setTouch(const CtrTouch& t);
    // Runs the 60 Hz ticks due since the last call (nowNs = a monotonic clock).
    void update(int64_t nowNs);
    void step(int frames);    // deterministic stepping (tests)
    // True while something animates or input is being handled (the caller can idle otherwise).
    bool busy() const;

    // Draws a screen into a rectangle of the current framebuffer. `proj` maps layout space
    // (origin at the centre, y up, 400x240 top / 320x240 bottom) to clip space.
    void drawTop(const float proj[16], int vpX, int vpY, int vpW, int vpH);
    void drawBottom(const float proj[16], int vpX, int vpY, int vpW, int vpH);

    // Builds a projection for a screen of layout size lw x lh drawn into the logical rectangle
    // (rx, ry, rw, rh) of a W x H panel whose clip space is rotated by rot (column major 2x2).
    // Optional probe called between the passes of a screen ("3d" after the top screen's 3D).
    static void (*perfMark)(const char* pass);
    // Drawn over each screen inside its pass (nano's lists and dialogs in the 3DS style), and
    // called on every 60 Hz tick of the home.
    std::function<void(Renderer& r, bool top)> overlay;
    std::function<void()> onTick;
    // A theme's animated bottom image changed frame (0..2): the theme's frame sound plays.
    std::function<void(int phase)> onBackgroundFrame;
    // True when the top screen paints every pixel of its rectangle itself (the 3D wall).
    bool topCovered() const;
    // Leaves the focused banner and the camera / screenshot bars off the top screen (a panel's
    // title band takes their place).
    void setTopBannerHidden(bool hidden);
    // Save/Load Layout (My Menu, HOME Menu Settings): opens over the home with thumbnails of the
    // current layout; the host's store (CtrHomeHost::myMenuStore) keeps the slots. A load calls
    // the store's apply, then reloadLayout() rebuilds the grid under the scene's fading screen.
    bool openMyMenu();
    bool myMenuActive() const;
    // The root grid's zoom and cursor (what a saved layout keeps besides the host's files), and
    // putting them back with the grid rebuilt from the host (folders closed).
    void layoutState(int* zoom, int* cursor) const;
    void applyLayoutState(int zoom, int cursor);
    static void projection(float out[16], float lw, float lh, float rx, float ry, float rw, float rh,
                           float W, float H, const float rot[4]);

    // Widescreen: the top and bottom screens are laid out this many layout units wide (at least
    // 400 and 320, the design widths, each 240 tall). The grid shows more columns, edge items
    // move out to the edges, backgrounds stretch, the 3D camera widens.
    void setScreenWidths(float top, float bottom);
    float topWidth() const { return mTopW; }
    float topHeight() const { return mTopH; }
    // Layout height of the top screen: taller than 240 on panels narrower than 5:3 (4:3, 3:2,
    // square), so it fills them instead of being letterboxed.
    void setTopHeight(float h);
    float bottomWidth() const { return mBotW; }
    // Smallest root grid (the console keeps at least the stored count, 240 after first boot).
    void setMinRootSlots(int n) { mMinRootSlots = n; }
    int minRootSlots() const { return mMinRootSlots; }
    // The launched title is running / the menu came back to the front (HOME).
    void resumeFromApp();
    // A title runs behind the home and HOME suspended it (scenes/app/application.ts): the pause
    // fade from its screens into the menu, then the menu with the title suspended (its icon
    // pulsing, the "Suspended software" window, the bottom bar Close / Resume). `ids` are the
    // grid identities of the title; `top` / `bottom` its screens as captured (GL textures, rows
    // top first, any size), which the home takes over and deletes. A resume (HOME, Resume, the
    // title's icon, resumeSuspended) fades back into the title and ends in host->resumeApp(); a
    // confirmed Close ends in host->closeApp() and the closing notice.
    void suspendApp(const std::vector<std::string>& ids, unsigned top, int topW, int topH, unsigned bottom, int botW,
                    int botH);
    bool appSuspended() const;      // from suspendApp until the resume or the close has played
    bool appResuming() const;       // the fade back into the title is playing
    void resumeSuspended();         // HOME from the host (power, Back at the top level)
    // Items changed (rescan): rebuild the grids, keeping the cursor on the same item.
    void reloadItems();
    // The host switched the root grid to other content (nano's categories, L / R and the applet
    // bar): open folders close, then the root grid is built again with the cursor on `cursor`.
    void showCategory(int cursor);
    // Folder colours of the content shown (0..1 RGB, a category's), in place of the theme's
    // folder block on both screens; null brings the theme's back.
    void setFolderColors(const float* dark, const float* main);
    // Applet bar button 1..5 shows the host's pictogram (RGBA, rows top first, its alpha the shape)
    // on a rounded button, the ink in `ink` (and `inkLight` where the console lightens it).
    void setBarIcon(int button, const uint8_t* rgba, int w, int h, const float ink[3], const float inkLight[3]);
    // The ids from the root to the item under the cursor: each open folder, then the item (none
    // when the cursor is on the bar). restorePath opens those folders again without animation and
    // puts the cursor on the item (a game the home launched, when the home starts again after it).
    std::vector<std::string> focusPath() const;
    void restorePath(const std::vector<std::string>& ids);
    // The zoom of the root grid and of the folders opened from now on (a saved session's).
    void setZooms(int root, int folder);
    // Test hook: puts the cursor on the root item named `name` and opens it when it is a folder.
    // From inside a folder it closes the folder first (call again once it closed). True when done.
    bool testOpen(const std::string& name);
    // The title under the cursor while the grid is idle (null on the bar, a folder or an empty
    // slot): what the host's options menu acts on.
    const HomeItem* focusedTitle() const;
    const HomeItem* focusedItem() const;   // any item under the cursor (a folder too)
    // Bottom screen cache statistics since the last call (perf probe): hits, misses.
    void takeCacheStats(int* hits, int* misses);
    // Moving with buttons (devices without touch): whether the item under the cursor can be
    // moved, and lifting it. The D-pad then carries it slot by slot (up from a folder's top row
    // reaches the header that takes a title out), A puts it down, B takes it back.
    bool canMoveFocused() const;
    bool startMove();
    // The HOME Menu theme (null = the default look): a built-in colour theme (builtinThemes()),
    // a theme from the SD card or one made from a colour. The body must outlive its use.
    void setTheme(const ThemeBody* body);
    const ThemeBody* theme() const { return mThemeBody; }
    // The user's wallpaper on a screen (a picture, a video frame or an animation the host
    // renders, texture `tex`, rows top first): drawn where the console draws a theme's background
    // image (LncBgSlide), over a theme image, the wall (top) or the grid plates (bottom) hidden
    // under it. 0 removes it. `dynamic`: its pixels change every frame (video, animation);
    // `flipY`: its rows are stored bottom first (a texture rendered by GL).
    void setWallpaper(bool top, GLuint tex, int w, int h, bool dynamic, bool flipY = false);
    Assets& assets() { return *mAssets; }
    int64_t frame() const { return mFrame; }

private:
    struct Impl;
    std::unique_ptr<Impl> mImpl;
    std::unique_ptr<Assets> mAssets;
    std::unique_ptr<ThemeManager> mTheme;
    void applyFolderColors();
    const ThemeBody* mThemeBody = nullptr;
    bool mLoaded = false;
    int mMinRootSlots = 60;
    float mTopW = 400, mBotW = 320, mTopH = 240;
    uint32_t mHeldNext = 0;
    bool mHomeNext = false;
    CtrTouch mTouchNext;
    bool mTouchDownLatched = false, mTouchUpLatched = false;
    bool mTouchWasHeld = false;
    int64_t mLastNs = 0;
    double mAccum = 0;
    int64_t mFrame = 0;
    void tick();
};

}  // namespace ctr
}  // namespace android
