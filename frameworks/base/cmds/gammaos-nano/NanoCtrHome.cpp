// NanoCtrHome - 3DS HOME Menu runtime (see NanoCtrHome.h). Port of /work/3ds/web/app/src/menu:
// scenes/launcher (grid, cursor, applet bar, arrows, scroll bar, bottom buttons, balloon,
// folders, zoom, touch), scroll/scroller.ts, hud/hud.ts and fade.ts. Constants carry the
// addresses the web code cites (docs/re/launcher.md).
#include "NanoCtrHome.h"
#include "NanoCtrTheme.h"
#include "NanoCtrTop.h"

#include <algorithm>
#include <map>
#include <set>
#include <cmath>
#include <cstring>
#include <ctime>
#include <unordered_map>

#include <log/log.h>

#undef LOG_TAG
#define LOG_TAG "NanoCtr"

namespace android {
namespace ctr {

namespace {

// ------------------------------------------------------------------ geometry (geometry.ts)

const int ZOOM_LEVELS = 6;
const int MAX_ZOOM = 5;
const int ROWS_ROOT[6] = {1, 2, 3, 4, 5, 6};
const int ROWS_FOLDER[6] = {1, 1, 2, 3, 4, 5};
const int COLUMNS[6] = {3, 3, 5, 7, 9, 10};
const float ORIGIN_ROOT[6][2] = {{76, 161}, {76, 82}, {52, 70}, {40, 64}, {32, 60}, {34, 54}};
const float ORIGIN_FOLDER[6][2] = {{76, 161}, {76, 137}, {52, 109}, {40, 95}, {32, 87}, {34, 80}};
const float PITCH[6][2] = {{84, 82}, {84, 84}, {54, 54}, {40, 40}, {32, 32}, {28, 28}};
const float CULL_PITCH[6] = {84.35f, 84.35f, 54, 40, 32, 28};
const float TOUCH_BOX[6] = {72, 72, 50, 36, 28, 24};
const float ICON_SIZE[6] = {48, 48, 36.57f, 27.42f, 18.28f, 18.28f};
const int SMALL_ICON_ZOOM = 2;
const int MIN_FOLDER_ZOOM = 1;
const int PAGE_FRAMES = 16;
const int PAGE = 60;            // slots per page (FUN_00218044)
const int ROOT_SLOTS = 360;

const int UP_TO_BAR[6][10] = {
    {1, 3, 5}, {1, 3, 5}, {0, 2, 3, 4, 6}, {0, 1, 2, 3, 4, 5, 6},
    {0, 1, 1, 2, 3, 4, 5, 5, 6}, {0, 1, 1, 2, 3, 3, 4, 5, 5, 6}};
const int DOWN_FROM_BAR[6][8] = {
    {0, 0, 1, 1, 1, 2, 2, 2}, {0, 0, 1, 1, 1, 2, 2, 2}, {0, 0, 1, 2, 3, 4, 4, 4},
    {0, 1, 2, 3, 4, 5, 6, 6}, {0, 1, 3, 4, 5, 7, 8, 8}, {0, 1, 3, 4, 6, 7, 9, 9}};
const int BAR_BUTTONS = 8;
enum Bar { BarSettings = 0, BarNotes, BarFriends, BarNews, BarWeb, BarMvs, BarZoomOut, BarZoomIn };
const char* BAR_ANCHORS[8] = {"N_CPos_Lgt_00", "N_CPos_Memo_00", "N_CPos_Frd_00", "N_CPos_News_00",
                              "N_CPos_Web_00", "N_CPos_Mvs_00", "N_CPos_Dw_00", "N_CPos_Up_00"};
const char* BAR_TOUCH[8] = {"B_Lgt_00", "B_Memo_00", "B_Frd_00", "B_News_00", "B_Web_00", "B_Mvs_00", "B_Dw_00", "B_Up_00"};
const char* BAR_GROUPS[8] = {"G_Light_00", "G_Memo_00", "G_Friend_00", "G_News_00", "G_Web_00", "G_Mvs_00", "G_Dw_00", "G_Up_00"};

float ease(float t) {
    const float c = std::clamp(t, 0.0f, 1.0f);
    return c * c * (3 - 2 * c);
}
float lerpf(float a, float b, float t) { return a + (b - a) * t; }

float zoomTable(const float* table, float zoom) {
    const float z = std::clamp(zoom, 0.0f, (float)(ZOOM_LEVELS - 1));
    const int low = (int)floorf(z);
    const int high = std::min(ZOOM_LEVELS - 1, low + 1);
    return table[low] + (table[high] - table[low]) * (z - low);
}

int clampZoom(int zoom, bool folder) { return std::max(folder ? MIN_FOLDER_ZOOM : 0, std::min(MAX_ZOOM, zoom)); }

struct Metrics {
    int zoom = 0;
    bool folder = false;
    int rows = 1, columns = 3;
    float originX = 76, originY = 161, pitchX = 84, pitchY = 82;
};

// Widescreen: the bottom screen's width beyond 320 layout units (CtrHome::setScreenWidths).
float sExtraWidth = 0;

int extraColumns(int zoom) { return (int)floorf(sExtraWidth / PITCH[zoom][0]); }

Metrics metricsFor(int zoom, bool folder) {
    Metrics m;
    m.zoom = clampZoom(zoom, folder);
    m.folder = folder;
    m.rows = (folder ? ROWS_FOLDER : ROWS_ROOT)[m.zoom];
    const int extra = extraColumns(m.zoom);
    m.columns = COLUMNS[m.zoom] + extra;
    const float* o = folder ? ORIGIN_FOLDER[m.zoom] : ORIGIN_ROOT[m.zoom];
    // the visible columns stay symmetric around the centre: the first one moves left by half the
    // added columns
    m.originX = o[0] - extra * PITCH[m.zoom][0] * 0.5f;
    m.originY = o[1];
    m.pitchX = PITCH[m.zoom][0];
    m.pitchY = PITCH[m.zoom][1];
    return m;
}

float originXAt(float zoom, bool folder) {
    float t[6];
    for (int i = 0; i < 6; i++) t[i] = (folder ? ORIGIN_FOLDER : ORIGIN_ROOT)[i][0];
    return zoomTable(t, zoom);
}

struct Point { float x = 0, y = 0; };

Point slotPos(const Metrics& m, int slot) {
    const int column = slot / m.rows, row = slot % m.rows;
    return {-160 + m.originX + column * m.pitchX, 120 - m.originY - row * m.pitchY};
}

int slotCountFor(int lastUsed, bool folder) {
    // root rule of FUN_00218044 (pages of 60, one more when the last page is two thirds full);
    // folders use the same pages here (nano folders are not limited to 60 entries)
    int count = (int)ceil((lastUsed + 1) / (double)PAGE) * PAGE;
    if (lastUsed >= 0 && lastUsed % PAGE >= (2 * PAGE) / 3 - 1) count = (lastUsed / PAGE + 2) * PAGE;
    count = std::max(count, PAGE);
    if (!folder) count = std::max(count, std::min(ROOT_SLOTS, count));
    return count;
}

// ------------------------------------------------------------------ helpers

struct Box { float left, right, top, bottom; };

Box paneBox(const Pane* p) {
    const float* m = p->global.m;
    float l, t, r, b;
    p->rect(&l, &t, &r, &b);
    const float x0 = m[12] + l * m[0], x1 = m[12] + r * m[0];
    const float y0 = m[13] + t * m[5], y1 = m[13] + b * m[5];
    return {std::min(x0, x1), std::max(x0, x1), std::max(y0, y1), std::min(y0, y1)};
}

bool boxContains(const Box& b, float x, float y) { return x >= b.left && x <= b.right && y <= b.top && y >= b.bottom; }

bool hitPane(LayoutInstance* l, const char* name, float x, float y) {
    Pane* p = l ? l->pane(name) : nullptr;
    return p && boxContains(paneBox(p), x, y);
}

PlayOptions groupsOpt() {
    PlayOptions o;
    o.groups = 1;
    return o;
}

PlayOptions namedGroups(std::initializer_list<const char*> names) {
    PlayOptions o;
    o.groups = 2;
    for (const char* n : names) o.groupNames.push_back(n);
    return o;
}

std::u32string folderInitial(const std::string& name) {
    std::u32string s = utf8ToU32(name);
    for (char32_t c : s) {
        if (c == U' ' || c == U'\t') continue;
        if (c >= U'a' && c <= U'z') c = c - U'a' + U'A';
        return std::u32string(1, c);
    }
    return std::u32string();
}

// ------------------------------------------------------------------ scroller (scroll/scroller.ts)

struct Scroller {
    enum State { Idle, Pressed, Dragging, Inertia, MoveTo };
    State state = Idle;
    float delta = 0, velocity = 0, last = 0, accumulated = 0, remaining = 0;
    bool claimed = true;
    const float dragStart = 11, fling = 1, decay = 0.95f, stopSpeed = 4.5f, moveSpeed = 4.275f;

    bool active() const { return state != Idle; }
    void press(float pos, bool claim = true) {
        const bool coasting = state == Inertia || state == MoveTo;
        state = coasting ? Dragging : Pressed;
        last = pos;
        accumulated = velocity = delta = 0;
        claimed = claim;
    }
    bool move(float pos) {
        delta = 0;
        if (state == Pressed) {
            const float step = pos - last;
            if (fabsf(step) > 4) { accumulated += fabsf(step); last = pos; }
            if (claimed && accumulated < dragStart) return false;
            last = pos;
            state = Dragging;
            return true;
        }
        if (state == Dragging) {
            velocity = pos - last;
            delta = velocity;
            last = pos;
        }
        return false;
    }
    bool release() {
        delta = 0;
        if (state == Dragging) {
            velocity *= fling;
            delta = velocity;
            state = Inertia;
            return true;
        }
        if (state == Pressed) state = Idle;
        return false;
    }
    bool update() {
        delta = 0;
        if (state == Inertia) {
            velocity *= decay;
            if (fabsf(velocity) < stopSpeed) { state = Idle; return true; }
            delta = velocity;
            return false;
        }
        if (state == MoveTo) {
            const float step = std::min(moveSpeed, fabsf(remaining));
            delta = (remaining > 0 ? 1.0f : -1.0f) * step;
            remaining -= delta;
            if (fabsf(remaining) <= 1e-6f) { remaining = 0; state = Idle; }
        }
        return false;
    }
    void moveBy(float d) { remaining = d; state = fabsf(d) > 1e-6f ? MoveTo : Idle; }
    void stop() { state = Idle; velocity = delta = remaining = 0; }
};

}  // namespace

// ------------------------------------------------------------------ the runtime

enum LState {
    StIdle = 0x00, StColumnScroll = 0x03, StZoom = 0x05, StAppStart = 0x07,
    StGrab = 0x0e, StMoveIcon = 0x0f,   // moving.ts: an icon held under the stylus, then its flight
    StFolderOpen = 0x101, StFolderClose = 0x102, StAppRunning = 0x103, StAppReturn = 0x104,
};

// moving.ts constants
const int LONG_PRESS_FRAMES = 22;          // touch.ts: a press this long picks the icon up
const float FLIGHT_SHARE = 0.2f, FLIGHT_MIN = 4, FLIGHT_MAX = 20, FLIGHT_AIM = 0.08f;
const int SWAP_REST_FRAMES = 5;
const float SHRINK_STEP = 0.01f, SHRINK_MIN = 0.6f, FADE_STEP = 8, FADE_MIN = 32;
const float HOVER_SHRINK_STEP = 0.07f, HOVER_SHRINK_MIN = 0.58f;
const float HELD_TOP = 108;
const int EDGE_DELAY_FRAMES = 4, FOLDER_HOVER_FRAMES = 30;
const int FOLDER_SLOTS = 60;               // state.ts FOLDER_SLOTS

struct Grid {
    std::string folderId;               // "" root
    std::string folderName;
    std::vector<HomeItem> items;        // as the host listed them
    std::vector<int> slotItem;          // slot -> index into items, -1 empty
    int slots = PAGE;
    Metrics metrics;
    int cursor = 0, firstColumn = 0;
    float scroll = 0;
    bool onBar = false;
    int barButton = 0, barReturnColumn = -1;
    // zoom blend
    bool blending = false;
    Metrics from, to;
    float t = 0;
    int fromFirstSlot = 0, toFirstSlot = 0;

    // moving.ts slot markers: the held item's hole, a slot not drawn (an icon flying to it),
    // the glow under the slot the held icon would land on, the folder about to take it
    int hiddenSlot = -1, skipSlot = -1, hoverSlot = -1, openSlot = -1;

    bool isFolder() const { return !folderId.empty(); }
    int zoom() const { return metrics.zoom; }
    float zoomValue() const { return blending ? lerpf((float)from.zoom, (float)to.zoom, t) : (float)metrics.zoom; }
    int rows() const { return metrics.rows; }
    int columns() const { return metrics.columns; }
    int columnCount() const { return (slots + rows() - 1) / rows(); }
    int cursorColumn() const { return cursor / rows(); }
    int cursorRow() const { return cursor % rows(); }
    int maxFirstColumn() const { return std::max(0, columnCount() - columns()); }
    float scrollFor(int column) const { return column * metrics.pitchX; }
    float maxScroll() const { return scrollFor(maxFirstColumn()); }
    int columnAt(float s) const { return (int)lroundf(s / metrics.pitchX); }
    bool columnVisible(int c) const { return c >= firstColumn && c < firstColumn + columns(); }
    const HomeItem* item(int slot) const {
        if (slot < 0 || slot >= (int)slotItem.size()) return nullptr;
        const int i = slotItem[slot];
        return i >= 0 ? &items[i] : nullptr;
    }

    Point slotPosition(int slot) const {
        if (!blending) return slotPos(metrics, slot);
        Point a = slotPos(from, slot), b = slotPos(to, slot);
        return {lerpf(a.x, b.x, t), lerpf(a.y, b.y, t)};
    }
    Point slotOnScreen(int slot) const {
        Point p = slotPosition(slot);
        p.x -= scroll;
        return p;
    }
    int slotAt(float x, float y, float box) const {
        const Metrics& m = metrics;
        const int column = (int)lroundf((x + scroll + 160 - m.originX) / m.pitchX);
        const int row = (int)lroundf((120 - y - m.originY) / m.pitchY);
        if (column < 0 || row < 0 || row >= m.rows) return -1;
        const int slot = column * m.rows + row;
        if (slot >= slots) return -1;
        Point c = slotOnScreen(slot);
        const float half = box / 2;
        return fabsf(x - c.x) <= half && fabsf(y - c.y) <= half ? slot : -1;
    }
    void setZoom(int z) { metrics = metricsFor(z, isFolder()); clampView(); }
    void clampView() {
        cursor = std::clamp(cursor, 0, std::max(0, slots - 1));
        const int column = cursorColumn();
        if (column < firstColumn) firstColumn = column;
        if (column >= firstColumn + columns()) firstColumn = column - columns() + 1;
        firstColumn = std::clamp(firstColumn, 0, maxFirstColumn());
        scroll = scrollFor(firstColumn);
    }
    // The bar tables are per design column; with added columns the index scales proportionally.
    int barButtonAboveCursor() const {
        const int z = zoom();
        const int count = COLUMNS[z];
        int c = std::clamp(cursorColumn() - firstColumn, 0, columns() - 1);
        if (columns() > count && columns() > 1) c = (int)lroundf((float)c * (count - 1) / (columns() - 1));
        return UP_TO_BAR[z][std::clamp(c, 0, count - 1)];
    }
    int slotBelowBar() const {
        int design = DOWN_FROM_BAR[zoom()][barButton];
        const int count = COLUMNS[zoom()];
        if (columns() > count && count > 1) design = (int)lroundf((float)design * (columns() - 1) / (count - 1));
        const int column = barReturnColumn >= 0 ? barReturnColumn : design;
        const int slot = (firstColumn + std::min(column, columns() - 1)) * rows();
        return std::min(slot, slots - 1);
    }
};

// One icon layout drawn many times (IconTemplate in icons.ts).
struct IconTemplate {
    std::unique_ptr<LayoutInstance> layout;
    Animator* scale = nullptr;
    Animator* picToggle = nullptr;
    float offsetX = 0;
    std::vector<std::string> hidden, baseHidden;
    float posed = -1000;

    // icons.ts setPicture: the theme picture look (PicToggle frame 1): N_Pic_<group> with the
    // theme's folder or icon frame image instead of the coloured N_Color_<group> body
    void setPicture(bool on, const char* group = "00") {
        if (!picToggle) {
            if (!on || !layout->hasAnimation("PicToggle")) return;
            PlayOptions o;
            o.groups = 1;
            o.paused = true;
            o.loop = 0;
            picToggle = layout->play("PicToggle", o);
            if (!picToggle) return;
        }
        picToggle->seek(on ? 1 : 0);
        const std::string color = std::string("N_Color_") + group, pic = std::string("N_Pic_") + group;
        auto inBase = [&](const std::string& n) { return std::find(baseHidden.begin(), baseHidden.end(), n) != baseHidden.end(); };
        hidden.clear();
        for (const std::string& n : baseHidden) if (!(on && n == pic)) hidden.push_back(n);
        if (on) hidden.push_back(color);
        if (!inBase(color)) layout->setVisible(color, !on);
        if (!inBase(pic) || on) layout->setVisible(pic, on);
        posed = -1000;
        layout->refresh();
    }

    void pose(float zoom) {
        if (zoom == posed) return;
        posed = zoom;
        if (scale) scale->seek(zoom);
        layout->refresh();
        for (const std::string& n : hidden) layout->setVisible(n, false);
    }
};

struct Trail {
    std::unique_ptr<LayoutInstance> layout;
    Animator* scale = nullptr;
    bool active = false;
};

struct CtrHome::Impl {
    CtrHome* self;
    Assets* A;
    CtrHomeHost* host;

    // input (input.ts)
    uint32_t held = 0, down = 0, up = 0, repeat = 0, prevHeld = 0;
    int holdCounter = 0;
    CtrTouch touch;
    bool homeEvent = false;

    // layouts (bottom)
    std::unique_ptr<LayoutInstance> base, palette, arrows, buttons, cursor, folder, balloon, indicatorL;
    // top
    std::unique_ptr<LayoutInstance> top, hud;
    bool topBarsHidden = false;
    // Save/Load Layout over the home, and the frames its chrome stays hidden after it left
    // (mymenu/index.ts MyMenuReturn, 30 frames)
    std::unique_ptr<CtrMyMenu> myMenu;
    int myMenuReturn = 0;
    bool myMenuOn() const { return myMenu && myMenu->active(); }
    bool chromeHidden() const { return myMenuOn() || myMenuReturn > 0; }
    // ------------------------------------------------------------ suspended title (scenes/app)
    // application.ts steps from HOME onwards (the title's own boot and run are nano's app)
    enum AppStep { AppNone, AppSuspending, AppSuspended, AppResuming, AppGone, AppQuitting, AppClosing };
    AppStep appStep = AppNone;
    int appFrame = 0;
    std::vector<std::string> appIds;
    std::unique_ptr<LayoutInstance> pause, appBar;   // LncPauseFade_D_00, the suspended LncBtmBtn_02
    Animator* pauseClip = nullptr;
    Animator* appBarClip = nullptr;
    IconTemplate sleepIcon;          // LncIconSleep_00 over the suspended title's grid icon
    std::string sleepOn;             // the id it is drawn on (kept while it fades out)
    bool sleepLeaving = false;
    TexturePtr appTopTex, appBottomTex, menuTex, windowIcon;
    bool appBarShown = false;
    int appTouched = -1;
    std::unique_ptr<CtrDialog> appDialog;
    int appDialogKind = 0;           // 1 the Close confirmation, 2 the closing notice
    int appConfirmSel = 1;
    bool capturing = false;          // drawing the menu for P_Lnc_00
    bool windowShown = false;
    std::string windowFor;
    GLuint remapProgram = 0;
    bool pauseDone = false, appBarDone = true;
    // bumped for every pause / bar clip: replacing a clip stops (and so finishes) the one before,
    // whose callback must not count for the new one
    int pauseGen = 0, appBarGen = 0;
    bool appActive() const { return appStep != AppNone; }
    bool appCovers() const { return appStep == AppSuspending || appStep == AppResuming || appStep == AppGone; }
    bool isSuspended(const std::string& id) const {
        return appStep != AppNone && appStep != AppGone && std::find(appIds.begin(), appIds.end(), id) != appIds.end();
    }
    void appSuspend(const std::vector<std::string>& ids, GLuint top, int tw, int th, GLuint bottom, int bw, int bh);
    bool appUpdate();
    void appFinishSuspend();
    void appResume();
    void appStartResume();
    void appConfirmClose();
    void appQuit();
    void appClose();
    void appEnd();
    void appShowBar(bool shown);
    void appSleep(bool on);
    void appWindow();
    void appDark(bool dark);
    void setMenuTopOpacity(float fraction);
    TexturePtr renderMenuBottom();
    TexturePtr remapCopy(GLuint src, int w, int h, const float a[3], const float b[3]);
    // fades
    std::unique_ptr<LayoutInstance> fadeTop, fadeBottom;
    bool fadeActive = false;

    // icons
    IconTemplate blank, tile, folderIcon, icon;
    // moving.ts: the lifted title / folder, the hole, the hover glow, an open folder
    IconTemplate pickUp, pickUpFolder, pickUpBlank, hoverGlow, folderOpenT, folderOpenB;
    // folders.ts FolderMaker: a new folder pops in over its slot (LncIconFolderMake_00_Scale_*)
    std::unique_ptr<LayoutInstance> maker;
    Animator* makerToggle = nullptr;
    int makerSlot = -1, makerFrame = 0;
    HomeItem makerItem;
    void makerPicture(bool on) {
        if (!maker) return;
        if (!makerToggle && on && maker->hasAnimation("PicToggle")) {
            PlayOptions o = groupsOpt();
            o.paused = true;
            o.loop = 0;
            makerToggle = maker->play("PicToggle", o);
        }
        if (makerToggle) makerToggle->seek(on ? 1 : 0);
        if (on && themeFolder) maker->replaceTexture("P_Folder_00", themeFolder);
        maker->refresh();
    }
    struct Held {
        HomeItem item;
        int gridIndex = 0, sourceIndex = 0;   // grids[] index under the stylus / picked from
        int origin = -1;
        Point at{0, 0};
        int hover = -1, folderHover = 0;
        int edge = -1, edgeFrames = 0;        // arrow disc under the icon: 0 left 1 right
        float scrollFrom = 0, scrollTo = 0;
        int scrollFrame = PAGE_FRAMES;
        int target = -1;
        bool hasSwapped = false;
        HomeItem swapped;
        Point swapAt{0, 0};
        std::string into;                     // the folder the title flies into
        float scale = 1, alpha = 255;
        int rest = 0;
        bool copy = false;                    // lifted out of a generated folder
        bool buttons = false;                 // carried with the D-pad (startMove)
        int cell = -1;                        // its slot then (-2: the folder header)
    };
    bool holding = false, reloadAfterDrop = false;
    Held mover;
    void startGrab(int slot, float x, float y);
    // Arranged grids move their items; a title of a generated folder (a system's games) can be
    // lifted out as a copy: the header puts a shortcut onto the root, it stays where it was
    bool canGrab(const Grid& g, const HomeItem& it) const {
        if (!host) return false;
        if (host->editable(g.folderId)) return true;
        return g.isFolder() && it.kind == HomeItemKind::Title;
    }
    void updateGrab();
    void updateGrabButtons();
    void moveCell(int dx, int dy);
    void updateDrop();
    void finishDrop();
    void flyBack();
    void dropHeld(float x, float y);
    void dropAcross(float x, float y);
    void hoverSlotAt(int slot);
    void edgeScroll(float x, float y, bool touching);
    int dropKind(int slot) const;
    bool arrowDisc(int side, float x, float y);
    std::vector<std::pair<int, std::string>> slotList(const Grid& g) const;
    void saveGrid(const Grid& g);
    void putIntoFolder(const std::string& folderId);
    void drawLifted(Renderer& r, const HomeItem& it, float x, float y, float zoom, float scale, float alpha);
    void drawFolderFace(Renderer& r, const HomeItem& it, float x, float y, float zoom, float scale);
    void drawFolderFaceSized(Renderer& r, const HomeItem& it, float x, float cy, float size);
    void drawTitleArt(Renderer& r, const HomeItem& it, float x, float y, float size, float scale);
    std::unique_ptr<LayoutInstance> letter;
    Trail trail[2];
    int trailNext = 0;
    Animator* cursorScale = nullptr;

    // grids: [0] root, [1..] open folders
    std::vector<Grid> grids;
    int state = StIdle, previousState = StIdle, stateFrame = 0;
    int scrollSteps = 0;
    float scrollFrom = 0, scrollTo = 0;
    int scrollDuration = 0;
    int cursorColumnOnScreen = 0;
    bool paging = false;
    int bumpFrames = 0;
    bool showNameBalloon = false;
    // zoom run
    float zoomScrollFrom = 0, zoomScrollTo = 0;
    int zoomFirstColumn = 0;
    // folder close: the grid being closed stays drawn under the fading sheet
    bool closingFolder = false;
    Grid closing;

    Scroller scroller;
    // touch control
    enum TKind { TNone, TGrid, TBar, TArrow, TBottom, TBack };
    TKind tKind = TNone;
    int tSlot = -1, tBar = -1, tSide = 0, tBottomSlot = -1;
    int tHeld = 0;
    bool tDragged = false;
    float tickScroll = 0;
    int64_t tickFrame = -4;
    int snapColumn = -1;

    // arrows
    bool arrowShown[2] = {false, false};
    // indicator
    float indFade = 0, indFlash = 0;
    int indFlashFrames = 0;
    Rgba indBlackA{}, indWhiteA{}, indBlackB{}, indWhiteB{};
    bool indShown = false;
    // bottom bar
    std::string bbCurrent = "none", bbShown = "none", bbPending;
    bool bbHasPending = false;
    int bbWait = 0;
    bool bbEnabled = true;
    int bbPressed = -1;
    // balloon
    std::string balloonText;
    std::string balloonItem, balloonName, balloonWrapped;
    bool balloonVisible = false;
    // palette sheets
    float sheetMargin = 10, sheetShadowExtra = 0, folderShadowExtra = 0;
    // icon textures
    std::unordered_map<std::string, TexturePtr> iconTex;
    // hud
    int netTimer = 0, batTimer = 0, clockTimer = 0;
    int shownSecond = -1, shownMinute = -1, shownHour = -1, shownDay = -1, shownMonth = -1;
    bool chargeBlink = false, lowBlink = false;
    Animator* hudNetMode = nullptr;
    Animator* hudAntenna = nullptr;
    Animator* hudBattery = nullptr;
    // the battery percentage: the HUD's play coin count (T_Coin_00, unused in nano) moved next to
    // the gauge, the date and the clock shifted left to make room
    int batFrame = 3;          // the gauge frame of the real level (0..3)
    bool pctPlaced = false;
    float pctShift = 0;
    float dateX[4] = {0, 0, 0, 0};
    void hudPercent(const HomeStatus& st);
    int netFrame = -100;
    // top screen 3D scene
    std::unique_ptr<CtrTop> top3d;
    ThemeManager* theme = nullptr;
    std::string topFocusKey;
    // launch
    HomeItem launching;
    int bgm = 0;
    int bgmCheck = 0;
    bool entered = false;

    Grid& grid() { return grids.back(); }
    Grid& root() { return grids.front(); }
    bool inFolder() const { return grids.size() > 1; }

    // ------------------------------------------------------------ setup
    bool load();
    void loadGrid(Grid& g);
    std::unique_ptr<LayoutInstance> make(const char* archive, const char* name) {
        auto l = A->create(archive, name);
        if (!l) ALOGE("layout %s/%s missing", archive, name);
        return l;
    }
    bool makeTemplate(IconTemplate& t, const char* name, float offsetX, std::vector<std::string> hidden,
                      std::vector<std::string> shown = {}) {
        t.layout = make("launcher", name);
        if (!t.layout) return false;
        t.offsetX = offsetX;
        t.hidden = hidden;
        t.baseHidden = hidden;
        if (t.layout->hasAnimation("Scale")) {
            PlayOptions o = groupsOpt();
            o.paused = true;
            t.scale = t.layout->play("Scale", o);
        }
        for (const std::string& n : hidden) t.layout->setVisible(n, false);
        for (const std::string& n : shown) t.layout->setVisible(n, true);
        return true;
    }

    void sound(const char* name) { if (host) host->playSound(name); }

    // ------------------------------------------------------------ frame
    void tick();
    void pollInput();
    void mainUpdate();
    void setState(int id) {
        // anything else taking over (HOME, an app starting) puts a held icon down where it was
        if (holding && id != StGrab && id != StMoveIcon && id != StFolderOpen) {
            for (Grid& g_ : grids) g_.skipSlot = g_.hiddenSlot = g_.hoverSlot = g_.openSlot = -1;
            holding = false;
        }
        previousState = state;
        state = id;
        stateFrame = 0;
    }
    void handleIdleInput();
    void activate(int source);   // 0 key 1 touch 2 button
    void createFolder();
    bool requestZoom(int dir, int source);
    void decideBar(int button);
    void decideBottom();
    void moveCursorTo(std::function<void()> change, bool snd = true);
    void ensureCursorVisible();
    void moveHorizontal(int dir, bool trigger);
    void moveUp();
    void moveDown();
    void bump() { indFlashFrames = 1; bumpFrames = 20; }
    void scrollColumns(int columns, int duration = 0);
    void scrollPage(int dir);
    void updateColumnScroll();
    bool scrollingCursor(Point* p);
    bool cursorHidden() const;
    void startZoom(int target);
    void updateZoom();
    void openFolder(int slot, bool quiet = false);
    int folderZoom = 2;   // the zoom folders open at (NEW_FOLDER_ZOOM, then the last one used)
    void closeFolder();
    void launch(const HomeItem& item);
    void updateChrome();
    void spawnTrail(float x, float y, float frame);
    void placeCursor();
    void cursorPose(const Grid& g, float* x, float* y, float* frame);
    void updateArrows();
    void setArrow(int side, bool visible);
    void updateIndicator();
    void bottomSet(const std::string& name);
    void bottomApply(const std::string& name);
    void bottomUpdate();
    std::string bottomSetFor();
    void updateBalloon();
    void balloonShow(const std::string& text, bool label, float x, float y, float zoom);
    void balloonHide();
    void updateSheet(LayoutInstance* l, const Grid& g, float extra);
    // touch
    bool touchUpdate();
    void touchBegin(float x, float y);
    void touchHold(float x, float y);
    void touchEnd(float x, float y);
    void touchCoast();
    void snap(float velocity);
    void applyDelta(float delta);
    void tapSlot(int slot, float x, float y);
    void tapBar(int button);
    // hud
    void hudBuild();
    void hudUpdate(bool force);
    // draw
    void drawIcons(Renderer& r, const Grid& g, const Box* clip);
    void stamp(Renderer& r, IconTemplate& t, float x, float y, float scale = 1);
    void drawInstance(Renderer& r, LayoutInstance* l) {
        if (!l) return;
        l->layout().calculate();
        r.drawLayout(l->layout());
    }
    TexturePtr titleTexture(const HomeItem& it);
    TexturePtr titleTextureOwn(const HomeItem& it);
    // rgb: a colour in place of the grey (a category's title glyphs)
    TexturePtr tintedCopy(GLuint src, int w, int h, float grey, float coverAspect = 0, bool flipY = false, bool onWhite = false,
                          const float* rgb = nullptr);
    std::unordered_map<GLuint, TexturePtr> hostTex;  // wrappers of host textures
    uint64_t artGen = 0;
    Renderer::CacheSlot bottomCache;   // background, sheets and icons of the bottom screen
    uint64_t bottomSig = 0;   // the inputs of the cached group last recorded
    int bottomReuseRun = 0;
    uint64_t themeRev = 0;    // bumped by every setTheme (colours change without textures)
    // the host's category switch (showCategory): waits for an idle root grid
    bool catPending = false;
    int catCursor = 0;
    void applyCategory();
    // the category's folder colours (setFolderColors) over the theme's folder block
    // nano's category icons of the bar buttons (setBarIcon): the applet banners' solids
    TexturePtr barGlyph[6], barFace[6], barFaceActive[6], barPlate[6];
    int barActive = 0;   // the bar button drawn as the active one (0 none)
    float barTint[6][3] = {};
    bool folderColorsOn = false;
    float folderDark[3] = {0, 0, 0}, folderMain[3] = {0, 0, 0};
    uint64_t bottomSignature(const float* proj, int vx, int vy, int vw, int vh, float botW, uint64_t salt);
    // theme/background.ts: the background image of each screen (LncBgSlide_U_00 behind the 3D
    // pass, LncBgSlide_D_00 under the base's NN_LncBgSlide_00): a theme image or the host's
    // wallpaper, which wins
    struct Background {
        std::unique_ptr<LayoutInstance> lay;
        TexturePtr theme, host, bound;
        std::string themeFrame;
        bool hostDynamic = false, hostFlip = false;
        int phase = -1;
        const TexturePtr& image() const { return host ? host : theme; }
        bool shown() const { return lay && image(); }
    };
    Background bgTop, bgBottom;
    uint64_t bgFrame = 0;
    // a theme's folder and icon frame images (icons.ts): null keeps the coloured bodies
    TexturePtr themeFolder, themeFolderOpen, themeFileLarge, themeFileSmall;
    bool blankPicture = false;
    void followBackground(Background& b, bool top, bool follow);
    void updateBackgrounds();
    std::map<std::pair<GLuint, int>, TexturePtr> tinted;   // derived icons by source texture + kind
    std::vector<TexturePtr> tintedRetired;                 // the last category's, freed at the next switch
    GLuint tintProgram = 0;
    // a title without art: a box with its initial (grid) or the box's face (top screen card),
    // kept per initial
    std::map<std::pair<char32_t, bool>, TexturePtr> boxArt;
    int boxBuilt = 0;   // initials drawn this frame (a folder of new initials spreads them over frames)
    TexturePtr boxTexture(const HomeItem& it, bool face, bool budgeted = true);
};

// ================================================================== setup

bool CtrHome::Impl::load() {
    base = make("launcher", "LncBase_D_01");
    palette = make("launcher", "LncPlt_00");
    bgTop.lay = make("launcher", "LncBgSlide_U_00");
    bgBottom.lay = make("launcher", "LncBgSlide_D_00");
    arrows = make("launcher", "LncArw_00");
    buttons = make("launcher", "LncBtmBtn_02");
    cursor = make("launcher", "LncCsr_00");
    folder = make("launcher", "LncFolder_00");
    balloon = make("launcher", "LncBlln_00");
    indicatorL = make("scrollindicator", "ScrollIndicatorLnc");
    top = make("launcher", "LncBase_U_00");
    hud = make("hud", "HudMenu_00");
    if (!base || !palette || !arrows || !buttons || !cursor || !folder || !balloon || !indicatorL || !top || !hud) return false;
    makeTemplate(blank, "LncIconSetSrc_00", 32, {"P_BtnShdw_00", "N_IconRoot_00"});
    makeTemplate(tile, "LncIconSetSrc_00", -32, {"N_Color_01", "N_Pic_01"});
    makeTemplate(folderIcon, "LncIconFolder_00", 0, {"N_PrizeBlank_00"});
    makeTemplate(icon, "LncIconDist_01", 0, {});
    makeTemplate(pickUp, "LncIconPickUp_00", 0, {});
    // PicToggle frame 0: a held folder shows its colour body
    // P_Icon_00 is the tile the console renders the initial into (IconDmy until then); the
    // initial is stamped over it here
    makeTemplate(pickUpFolder, "LncIconFolderPickUp_00", 0, {"P_IconPrize_00", "N_Pic_00", "P_Icon_00"}, {"N_Color_00"});
    makeTemplate(pickUpBlank, "LncIconPickUpBlank_00", 0, {});
    // the hover glow is a second LncIconSleep_00 held on its PickUp pose
    if (makeTemplate(hoverGlow, "LncIconSleep_00", 0, {})) {
        PlayOptions o = groupsOpt();
        o.paused = true;
        o.loop = 0;
        hoverGlow.layout->play("PickUp", o);
    }
    makeTemplate(sleepIcon, "LncIconSleep_00", 0, {});
    makeTemplate(folderOpenT, "LncIconFolderInT_00", 0, {"N_Pic_00"}, {"N_Color_00"});
    // only read for where the initial goes (icons.ts drawOpenFolder)
    makeTemplate(folderOpenB, "LncIconFolderInB_00", 0, {"P_Icon_00", "P_IconPrize_00"});
    icon.layout->setAlpha("P_IconBtnDmy_00", 0);
    blank.layout->layout().opacity = 0x80;
    maker = make("launcher", "LncIconFolderMake_00");
    letter = make("launcher", "LncIconFolderText_00");
    if (letter) {
        letter->setVisible("P_Bg_00", false);
        letter->setVisible("P_Icon_00", false);
    }
    for (Trail& t : trail) {
        t.layout = make("launcher", "LncCsrEfct_00");
        if (t.layout) {
            PlayOptions o = groupsOpt();
            o.paused = true;
            t.scale = t.layout->play("Scale", o);
        }
    }
    folder->layout().root->visible = false;
    balloon->layout().root->visible = false;
    arrows->setVisible("N_arwL_00", false);
    arrows->setVisible("N_arwR_00", false);
    {
        PlayOptions o = groupsOpt();
        o.paused = true;
        cursorScale = cursor->play("Scale", o);
        cursor->play("Loop", groupsOpt());
    }
    // palette sheet geometry (palette.ts)
    if (Pane* s = palette->pane("W_Plt_00")) {
        sheetMargin = (320 - s->size[0]) * 0.5f;
        if (Pane* sh = palette->pane("W_Shdw_00")) sheetShadowExtra = sh->size[0] - s->size[0];
    }
    if (Pane* s = folder->pane("W_Plt_00")) {
        if (Pane* sh = folder->pane("W_Shdw_00")) folderShadowExtra = sh->size[0] - s->size[0];
    }
    // indicator colours (indicator.ts)
    Pane* sp = indicatorL->pane("StartPic");
    Pane* ep = indicatorL->pane("EndPic");
    if (sp && ep && sp->material >= 0 && ep->material >= 0) {
        Layout& L = indicatorL->layout();
        indBlackA = L.materials[sp->material].bufferColor;
        indWhiteA = L.materials[sp->material].konst[0];
        indBlackB = L.materials[ep->material].bufferColor;
        indWhiteB = L.materials[ep->material].konst[0];
    }
    bottomApply("none");

    grids.clear();
    grids.emplace_back();
    loadGrid(root());
    root().metrics = metricsFor(0, false);
    root().clampView();
    hudBuild();
    top3d = std::make_unique<CtrTop>(*A);
    top3d->setTheme(theme ? theme->body() : nullptr);
    if (!top3d->load()) top3d.reset();
    if (top3d) {
        top3d->onBannerSound = [this](const std::shared_ptr<const RomBanner>& b) { if (host) host->playBannerSound(b); };
        top3d->onBannerLeave = [this]() { if (host) host->stopBannerSound(); };
    }
    else top3d->sceneIn();
    // enter (launcher.ts enterBegin): BGM, bottom bar and arrows scene in, logo fade in
    bgm = host ? host->playSound("BGM_CTR_HOME") : 0;
    buttons->play("SceneIn", groupsOpt());
    fadeTop = make("common", "CmnFadeNinLogo_U_00");
    fadeBottom = make("common", "CmnFadeNinLogo_D_00");
    if (fadeTop && fadeBottom) {
        fadeActive = true;
        Animator* a = fadeBottom->play("SceneIn");
        fadeTop->play("SceneIn");
        if (a) a->onFinish = [this]() { fadeActive = false; entered = true; };
    } else {
        entered = true;
    }
    top->play("SceneIn")->onFinish = [this]() {
        top->stop("SceneIn");
        if (Animator* ap = top->play("Appear")) ap->onFinish = [this]() { top->stop("Appear"); top->setVisible("N_Wndw_00", false); };
    };
    updateChrome();
    return true;
}

void CtrHome::Impl::loadGrid(Grid& g) {
    g.items = host ? host->items(g.folderId) : std::vector<HomeItem>();
    g.slotItem.clear();
    int next = 0;
    for (size_t i = 0; i < g.items.size(); i++) {
        int slot = g.items[i].slot >= 0 ? g.items[i].slot : next;
        while (slot < (int)g.slotItem.size() && g.slotItem[slot] >= 0) slot++;   // occupied: next free
        if ((int)g.slotItem.size() <= slot) g.slotItem.resize(slot + 1, -1);
        g.slotItem[slot] = (int)i;
        next = slot + 1;
    }
    g.slots = slotCountFor((int)g.slotItem.size() - 1, g.isFolder());
    if (!g.isFolder()) g.slots = std::max(g.slots, self->minRootSlots());
}

// ================================================================== input + tick

void CtrHome::Impl::pollInput() {
    down = held & ~prevHeld;
    up = prevHeld & ~held;
    repeat = 0;
    if (down) holdCounter = 0;
    else if (held && held == prevHeld) {
        holdCounter++;
        if (holdCounter > 0x13 && holdCounter % 5 == 0) repeat = held;
    } else holdCounter = 0;
    prevHeld = held;
}

void CtrHome::Impl::tick() {
    pollInput();
    if (appActive() && appUpdate()) {
        // the suspended title's scene took this tick's buttons, touch and HOME
        down = up = repeat = 0;
        const float tx = touch.x, ty = touch.y;
        touch = CtrTouch();
        touch.x = tx;
        touch.y = ty;
        homeEvent = false;
    }
    if (entered) mainUpdate();
    else updateChrome();
    // animation pass (every drawable of both screens)
    for (LayoutInstance* l : {base.get(), palette.get(), arrows.get(), buttons.get(), cursor.get(), folder.get(),
                              balloon.get(), top.get(), hud.get(), fadeTop.get(), fadeBottom.get()})
        if (l) l->update(1);
    for (Trail& t : trail) if (t.active && t.layout) t.layout->update(1);
    if (top3d) top3d->update();
    if (makerSlot >= 0 && maker) {
        maker->update(1);
        if (++makerFrame >= 16) {   // MAKE_FRAMES
            if (root().skipSlot == makerSlot) root().skipSlot = -1;
            makerSlot = -1;
        }
    }
    // indicator
    {
        indFade = indShown ? std::min(30.0f, indFade + 4) : std::max(0.0f, indFade - 1);
        if (indFlashFrames > 0) { indFlashFrames--; indFlash = std::min(10.0f, indFlash + 6); }
        else indFlash = std::max(0.0f, indFlash - 1);
    }
    hudUpdate(false);
    homeEvent = false;
    // the home's BGM keeps playing while the home is up: if its voice is gone (an output
    // error, a stolen voice, the toggle switched back on) it comes back without the intro
    if (entered && ++bgmCheck >= 60) {
        bgmCheck = 0;
        if (host && state != StAppStart && state != StAppRunning && (!bgm || !host->soundPlaying(bgm)))
            bgm = host->playSound("BGM_CTR_HOME_NO_INTRO");
    }
}

void CtrHome::Impl::mainUpdate() {
    stateFrame++;
    switch (state) {
        case StIdle:
            if (catPending) {
                if (inFolder()) closeFolder();
                else applyCategory();
                break;
            }
            handleIdleInput();
            break;
        case StGrab: updateGrab(); break;
        case StMoveIcon: updateDrop(); break;
        case StColumnScroll: updateColumnScroll(); break;
        case StZoom: updateZoom(); break;
        case StFolderOpen:
            if (stateFrame >= 17) setState(StIdle);
            break;
        case StFolderClose:
            if (stateFrame >= 17) {
                folder->layout().root->visible = inFolder();
                closingFolder = false;
                if (inFolder()) {
                    // a folder inside a folder closed (a category's IPTV group, a game subfolder):
                    // the sheet collapsed, the parent folder's sheet opens again over the grid
                    folder->play("FadeIn", groupsOpt());
                    setState(StFolderOpen);
                } else {
                    setState(StIdle);
                }
            }
            break;
        case StAppStart:
            if (stateFrame >= 60) setState(StAppRunning);
            break;
        case StAppRunning:
            if (homeEvent) {
                sound("SE_CTR_HOME_HOMEBUTTON");
                setState(StAppReturn);
                fadeActive = true;
                fadeTop->play("SceneIn");
                if (Animator* a = fadeBottom->play("SceneIn")) a->onFinish = [this]() { fadeActive = false; };
                buttons->play("SceneIn", groupsOpt());
                bbEnabled = true;
                if (host) bgm = host->playSound("BGM_CTR_HOME_NO_INTRO");
            }
            break;
        case StAppReturn:
            if (!fadeActive) setState(StIdle);
            break;
    }
    if (up & (CTR_LEFT | CTR_RIGHT)) scrollSteps = 0;
    bottomUpdate();
    updateChrome();
}

// ================================================================== launcher input (launcher.ts)

void CtrHome::Impl::handleIdleInput() {
    if (touchUpdate()) return;
    if (scroller.active()) return;
    Grid& g = grid();
    if (homeEvent && g.onBar) {
        moveCursorTo([&]() { g.cursor = g.slotBelowBar(); g.onBar = false; g.barReturnColumn = -1; }, false);
        return;
    }
    const uint32_t pressed = down | repeat;
    if (down & (CTR_L | CTR_R)) {
        if (host) host->stepCategory(down & CTR_R ? 1 : -1);
        return;
    }
    if (down & CTR_A) { activate(0); return; }
    if (down & CTR_B) {
        if (inFolder()) closeFolder();
        else if (host) host->backAtRoot();
        return;
    }
    if (pressed & CTR_UP) moveUp();
    else if (pressed & CTR_DOWN) moveDown();
    if (pressed & CTR_RIGHT) moveHorizontal(1, (down & CTR_RIGHT) != 0);
    else if (pressed & CTR_LEFT) moveHorizontal(-1, (down & CTR_LEFT) != 0);
}

void CtrHome::Impl::activate(int source) {
    Grid& g = grid();
    if (g.onBar) {
        const int b = g.barButton;
        if (b == BarZoomOut || b == BarZoomIn) { requestZoom(b == BarZoomIn ? 1 : -1, source); return; }
        if (source != 2) decideBottom();
        decideBar(b);
        return;
    }
    const HomeItem* it = g.item(g.cursor);
    if (!it) {
        // A on an empty root slot presses Create Folder (devices without a touch screen)
        if (source == 0 && bbShown == "createFolder") {
            decideBottom();
            sound("SE_CTR_COMMON_BUTTON");
            createFolder();
        }
        return;
    }
    if (source != 2) decideBottom();
    if (it->kind == HomeItemKind::Folder) openFolder(g.cursor);
    else launch(*it);
}

// folders.ts createFolder: an empty root slot gets a folder named from lau_2b_folder_name
// ("%d (New Folder)", the host numbers it); the host keeps it in the layout.
void CtrHome::Impl::createFolder() {
    Grid& g = grid();
    if (g.isFolder() || g.onBar || g.item(g.cursor) || !host) return;
    std::string name = A->messageText("lau_2b_folder_name");
    if (name.empty()) name = "%d (New Folder)";
    const int slot = g.cursor;
    if (host->createFolder(slot, name).empty()) {
        sound("SE_CTR_COMMON_ERROR");
        return;
    }
    self->reloadItems();
    // the pop-in (FolderMaker.start): the slot's icon waits under it for the clip's 16 frames
    Grid& root_ = root();
    if (maker && root_.item(slot)) {
        static const char* const MAKE_CLIPS[6] = {"Scale_01", "Scale_01", "Scale_03", "Scale_04", "Scale_05", "Scale_06"};
        makerItem = *root_.item(slot);
        makerSlot = slot;
        makerFrame = 0;
        root_.skipSlot = slot;
        PlayOptions o = groupsOpt();
        o.loop = 0;
        maker->play(MAKE_CLIPS[std::clamp(root_.zoom(), 0, 5)], o);
    }
    updateChrome();
}

bool CtrHome::Impl::requestZoom(int dir, int source) {
    Grid& g = grid();
    const int target = g.zoom() + dir;
    const int min = g.isFolder() ? MIN_FOLDER_ZOOM : 0;
    if (target < min || target > MAX_ZOOM) {
        if (source == 1) base->play("Invalid", namedGroups({dir > 0 ? "G_Up_00" : "G_Dw_00"}));
        return false;
    }
    sound(dir > 0 ? "SE_CTR_HOME_LINE_INC" : "SE_CTR_HOME_LINE_DEC");
    if (source != 1) decideBottom();
    startZoom(target);
    return true;
}

void CtrHome::Impl::decideBar(int button) {
    base->play("Decide", namedGroups({BAR_GROUPS[button]}));
    if (button == BarSettings) sound("SE_CTR_HOME_LIGHT");
    if (host) host->appletBar(button);
}

void CtrHome::Impl::decideBottom() {
    // the A key presses the bar's A button (every set's A button is its Open / Start)
    if (!bbEnabled || bbHasPending || bbShown == "none" || bbShown == "createFolder") return;
    const char* group = (bbShown == "folder" || bbShown == "applet") ? "G_BtnW_R_02" : "G_BtnW_C_01";
    buttons->play("Decide", namedGroups({group}));
}

void CtrHome::Impl::moveCursorTo(std::function<void()> change, bool snd) {
    Grid& g = grid();
    float bx, by, bf;
    cursorPose(g, &bx, &by, &bf);
    const int beforeBar = g.onBar ? 1 : 0, beforeKey = g.onBar ? g.barButton : g.cursor;
    change();
    const int afterBar = g.onBar ? 1 : 0, afterKey = g.onBar ? g.barButton : g.cursor;
    if (beforeBar == afterBar && beforeKey == afterKey) return;
    showNameBalloon = false;
    spawnTrail(bx, by, bf);
    if (snd) sound(g.onBar ? "SE_CTR_HOME_SELECT" : "SE_CTR_HOME_ICON_SELECT");
}

void CtrHome::Impl::ensureCursorVisible() {
    Grid& g = grid();
    if (g.onBar || g.columnVisible(g.cursorColumn())) return;
    const int column = g.firstColumn + g.columns() / 2;
    g.cursor = std::min(g.slots - 1, column * g.rows() + g.cursorRow());
}

void CtrHome::Impl::moveHorizontal(int dir, bool trigger) {
    Grid& g = grid();
    if (g.onBar) {
        moveCursorTo([&]() { g.barButton = (g.barButton + dir + BAR_BUTTONS) % BAR_BUTTONS; g.barReturnColumn = -1; });
        return;
    }
    if (!g.columnVisible(g.cursorColumn())) { moveCursorTo([&]() { ensureCursorVisible(); }); return; }
    const int target = g.cursor + dir * g.rows();
    if (target < 0 || target >= g.slots) {
        if (trigger) { sound("SE_CTR_HOME_ICON_SCROLL_INVALID"); bump(); }
        return;
    }
    const int column = target / g.rows();
    if (g.columnVisible(column)) { moveCursorTo([&]() { g.cursor = target; }); return; }
    cursorColumnOnScreen = g.cursorColumn() - g.firstColumn;
    moveCursorTo([&]() { g.cursor = target; });
    showNameBalloon = true;
    scrollColumns(dir);
}

void CtrHome::Impl::moveUp() {
    Grid& g = grid();
    if (g.onBar) return;
    if (!g.columnVisible(g.cursorColumn())) { moveCursorTo([&]() { ensureCursorVisible(); }); return; }
    moveCursorTo([&]() {
        if (g.cursorRow() > 0) g.cursor--;
        else {
            g.barReturnColumn = g.cursorColumn() - g.firstColumn;
            g.barButton = g.barButtonAboveCursor();
            g.onBar = true;
        }
    });
}

void CtrHome::Impl::moveDown() {
    Grid& g = grid();
    if (!g.onBar && !g.columnVisible(g.cursorColumn())) { moveCursorTo([&]() { ensureCursorVisible(); }); return; }
    moveCursorTo([&]() {
        if (g.onBar) { g.cursor = g.slotBelowBar(); g.onBar = false; g.barReturnColumn = -1; }
        else if (g.cursorRow() < g.rows() - 1 && g.cursor + 1 < g.slots) g.cursor++;
    });
}

void CtrHome::Impl::scrollColumns(int columns, int duration) {
    Grid& g = grid();
    const int first = std::clamp(g.firstColumn + columns, 0, g.maxFirstColumn());
    if (first == g.firstColumn) return;
    scrollSteps++;
    scrollFrom = g.scroll;
    scrollTo = g.scrollFor(first);
    scrollDuration = duration ? duration : (scrollSteps <= 5 ? 10 : 5);
    g.firstColumn = first;
    setState(StColumnScroll);
}

void CtrHome::Impl::scrollPage(int dir) {
    Grid& g = grid();
    const int first = std::clamp(g.firstColumn + dir * g.columns(), 0, g.maxFirstColumn());
    if (first == g.firstColumn) return;
    scrollSteps = 0;
    paging = true;
    scrollColumns(first - g.firstColumn, PAGE_FRAMES);
    scrollSteps = 0;
}

void CtrHome::Impl::updateColumnScroll() {
    Grid& g = grid();
    const float t = (float)stateFrame / scrollDuration;
    const float before = g.scroll;
    g.scroll = scrollFrom + (scrollTo - scrollFrom) * ease(t);
    if (g.columnAt(before) != g.columnAt(g.scroll)) sound("SE_CTR_HOME_ICON_SCROLL");
    if (t >= 1) {
        g.scroll = scrollTo;
        if (paging) moveCursorTo([&]() { ensureCursorVisible(); }, false);
        paging = false;
        setState(StIdle);
        handleIdleInput();
    }
}

bool CtrHome::Impl::scrollingCursor(Point* p) {
    if (state != StColumnScroll || grid().onBar || paging) return false;
    Grid& g = grid();
    Point pos = g.slotPosition(g.cursor);
    const int onScreen = std::clamp(cursorColumnOnScreen, 0, g.columns() - 1);
    p->x = pos.x - g.scrollFor(g.cursorColumn() - onScreen);
    p->y = pos.y;
    return true;
}

bool CtrHome::Impl::cursorHidden() const {
    return state == StGrab || state == StMoveIcon || state == StFolderOpen || state == StFolderClose || state == StAppRunning;
}

// ------------------------------------------------------------ zoom (zoom.ts)

void CtrHome::Impl::startZoom(int target) {
    Grid& g = grid();
    const Metrics from = g.metrics;
    const Metrics to = metricsFor(target, g.isFolder());
    const int firstSlot = g.firstColumn * from.rows;
    int first = firstSlot / to.rows;
    const int cursorColumn = g.cursor / to.rows;
    if (!g.onBar) {
        if (cursorColumn < first) first = cursorColumn;
        if (cursorColumn >= first + to.columns) first = cursorColumn - to.columns + 1;
    }
    const int columns = (g.slots + to.rows - 1) / to.rows;
    first = std::clamp(first, 0, std::max(0, columns - to.columns));
    zoomScrollFrom = g.scroll;
    zoomScrollTo = first * to.pitchX;
    zoomFirstColumn = first;
    g.blending = true;
    g.from = from;
    g.to = to;
    g.t = 0;
    g.fromFirstSlot = firstSlot;
    g.toFirstSlot = first * to.rows;
    setState(StZoom);
}

void CtrHome::Impl::updateZoom() {
    Grid& g = grid();
    if (!g.blending) { setState(StIdle); return; }
    const float t = ease((float)stateFrame / 15);
    g.t = t;
    g.scroll = lerpf(zoomScrollFrom, zoomScrollTo, t);
    if (stateFrame < 15) return;
    g.blending = false;
    g.metrics = g.to;
    g.firstColumn = zoomFirstColumn;
    g.scroll = zoomScrollTo;
    if (g.isFolder()) folderZoom = g.zoom();
    if (host) host->zoomChanged(g.isFolder(), g.zoom());
    setState(StIdle);
}

// ------------------------------------------------------------ folders (folders.ts)

void CtrHome::Impl::openFolder(int slot, bool quiet) {
    Grid& parent = grid();
    const HomeItem* it = parent.item(slot);
    if (!it) return;
    if (!quiet) sound("SE_CTR_HOME_OPEN_FOLDER");
    parent.onBar = false;
    Grid g;
    g.folderId = it->id;
    g.folderName = it->name;
    loadGrid(g);
    g.metrics = metricsFor(folderZoom, true);   // NEW_FOLDER_ZOOM (2), or the user's last folder zoom
    g.clampView();
    grids.push_back(std::move(g));
    if (theme) theme->setBlankFolder(true);   // empty slots in a folder: the folder background colours
    folder->layout().root->visible = true;
    folder->play("FadeIn", groupsOpt());
    setState(StFolderOpen);
}

void CtrHome::Impl::closeFolder() {
    if (!inFolder()) return;
    sound("SE_CTR_HOME_CLOSE_FOLDER");
    closing = std::move(grids.back());
    grids.pop_back();
    if (theme) theme->setBlankFolder(inFolder());   // still in a folder: its background colours
    closingFolder = true;
    PlayOptions o = groupsOpt();
    o.speed = -1;
    folder->play("FadeIn", o);
    setState(StFolderClose);
}

// ------------------------------------------------------------ launch (launch.ts)

void CtrHome::Impl::launch(const HomeItem& item) {
    // the suspended title's icon resumes it (application.ts)
    if (appStep == AppSuspended && isSuspended(item.id)) {
        appResume();
        return;
    }
    if (host && host->openInHost(item)) return;   // a nano list or player over the home
    if (host && !host->launch(item)) {
        sound("SE_CTR_HOME_BANNER_INVALID");
        return;
    }
    appEnd();   // another title: the host has ended the suspended one
    sound("SE_CTR_HOME_START");
    cursor->play("Decide", groupsOpt());
    sound("SE_CTR_HOME_START_EFFECT");
    launching = item;
    tKind = TNone;
    scroller.stop();
    bbEnabled = false;
    buttons->play("SceneOut", groupsOpt());
    if (host && bgm) host->stopSound(bgm, 30);
    bgm = 0;
    setState(StAppStart);
    fadeActive = true;
    fadeTop->play("SceneOutA");
    fadeBottom->play("SceneOutA");
}

// ------------------------------------------------------------ suspended title (scenes/app)
//
// application.ts from HOME on: the pause fade (LncPauseFade_D_00_SceneIn, 41 frames: the title's
// lower screen shrinks and darkens, the menu zooms in over it; the wall brings the title's upper
// screen back in darkened with BannerBG_AppPause, the menu's upper layouts fade in over frames
// 20..40), the suspended menu (bottom bar Close / Resume in place of the launcher's, the title's
// icon pulsing, the "Suspended software" window), the fade back (SceneOut, AppRestart) and Close
// (lau_dlg_quit0, then the closing notice lau_dlg_quit4 while the wall plays AppQuit).

namespace {
const int MENU_FADE_START = 20, MENU_FADE_FRAMES = 20;
const int CLOSE_NOTICE_SHOW = 6, QUIT_WALL_DELAY = 8, CLOSE_NOTICE_DISMISS = 108;
const float WINDOW_ALPHA = 180;
// the title's screens are kept at three times the console's 256 x 512 framebuffer layout
const int APP_TEX_W = 256 * 3, APP_TEX_H = 512 * 3;
struct AppButton { const char* node; const char* picture; const char* group; const char* texts[3]; const char* label; };
// Close uses the dark variant of the left button (N_BtnB_L_03); the console's Manual (centre) has
// no counterpart in nano and stays hidden
const AppButton APP_CLOSE = {"N_BtnB_L_03", "P_BtnB_L_03", "G_BtnB_L_03", {"T_BtnFB_L_03", "T_BtnBB_L_03", "T_BtnPB_L_03"}, "lau_3b_quit"};
const AppButton APP_RESUME = {"N_BtnW_R_03", "P_BtnW_R_03", "G_BtnW_R_03", {"T_BtnFW_R_03", "T_BtnBW_R_03", "T_BtnPW_R_03"}, "lau_3b_restart"};
const AppButton* const APP_BUTTONS[2] = {&APP_CLOSE, &APP_RESUME};
}  // namespace

// Copies `src` into a new w x h texture: the copy's texel (u, v) (v = 0 its first row) samples src
// at (a . (u, v, 1), b . (u, v, 1)); outside src the copy is transparent black.
TexturePtr CtrHome::Impl::remapCopy(GLuint src, int w, int h, const float a[3], const float b[3]) {
    if (!src) return nullptr;
    if (!remapProgram) {
        static const char* vs =
            "attribute vec2 p; varying vec2 uv;\n"
            "void main() { uv = p * 0.5 + 0.5; gl_Position = vec4(p, 0.0, 1.0); }\n";
        static const char* fs =
            "precision highp float; uniform sampler2D t; uniform vec3 a; uniform vec3 b; varying vec2 uv;\n"
            "void main() {\n"
            "  vec3 q = vec3(uv, 1.0);\n"
            "  vec2 s = vec2(dot(a, q), dot(b, q));\n"
            "  if (s.x < 0.0 || s.x > 1.0 || s.y < 0.0 || s.y > 1.0) gl_FragColor = vec4(0.0);\n"
            "  else gl_FragColor = vec4(texture2D(t, s).rgb, 1.0);\n"
            "}\n";
        auto sh = [](GLenum type, const char* text) {
            GLuint s = glCreateShader(type);
            glShaderSource(s, 1, &text, nullptr);
            glCompileShader(s);
            return s;
        };
        GLuint v = sh(GL_VERTEX_SHADER, vs), f = sh(GL_FRAGMENT_SHADER, fs);
        remapProgram = glCreateProgram();
        glAttachShader(remapProgram, v);
        glAttachShader(remapProgram, f);
        glBindAttribLocation(remapProgram, 0, "p");
        glLinkProgram(remapProgram);
        glDeleteShader(v);
        glDeleteShader(f);
    }
    TexturePtr t = A->rgbaTexture(nullptr, w, h);
    GLint prevFbo = 0, vp[4];
    glGetIntegerv(GL_FRAMEBUFFER_BINDING, &prevFbo);
    glGetIntegerv(GL_VIEWPORT, vp);
    GLuint fbo = 0;
    glGenFramebuffers(1, &fbo);
    glBindFramebuffer(GL_FRAMEBUFFER, fbo);
    glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, t->id, 0);
    glViewport(0, 0, w, h);
    glDisable(GL_BLEND);
    glDisable(GL_SCISSOR_TEST);
    glColorMask(GL_TRUE, GL_TRUE, GL_TRUE, GL_TRUE);
    glUseProgram(remapProgram);
    glUniform1i(glGetUniformLocation(remapProgram, "t"), 0);
    glUniform3f(glGetUniformLocation(remapProgram, "a"), a[0], a[1], a[2]);
    glUniform3f(glGetUniformLocation(remapProgram, "b"), b[0], b[1], b[2]);
    glActiveTexture(GL_TEXTURE0);
    glBindTexture(GL_TEXTURE_2D, src);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
    const float quad[8] = {-1, -1, 1, -1, -1, 1, 1, 1};
    glBindBuffer(GL_ARRAY_BUFFER, 0);
    glEnableVertexAttribArray(0);
    glVertexAttribPointer(0, 2, GL_FLOAT, GL_FALSE, 0, quad);
    glDrawArrays(GL_TRIANGLE_STRIP, 0, 4);
    glDisableVertexAttribArray(0);
    glUseProgram(0);
    glBindFramebuffer(GL_FRAMEBUFFER, (GLuint)prevFbo);
    glDeleteFramebuffers(1, &fbo);
    glViewport(vp[0], vp[1], vp[2], vp[3]);
    gTextureEpoch++;
    return t;
}

// application.ts renderMenuBottom: the lower screen as it is now, without the pause layer, for
// P_Lnc_00 (stored bottom row first, as flipVertically leaves it there).
TexturePtr CtrHome::Impl::renderMenuBottom() {
    constexpr int K = 2;
    const int w = 320 * K, h = 240 * K;
    TexturePtr t = A->rgbaTexture(nullptr, w, h);
    GLint prevFbo = 0, vp[4];
    glGetIntegerv(GL_FRAMEBUFFER_BINDING, &prevFbo);
    glGetIntegerv(GL_VIEWPORT, vp);
    GLuint fbo = 0;
    glGenFramebuffers(1, &fbo);
    glBindFramebuffer(GL_FRAMEBUFFER, fbo);
    glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, t->id, 0);
    glViewport(0, 0, w, h);
    glDisable(GL_SCISSOR_TEST);
    glClearColor(0, 0, 0, 1);
    glClear(GL_COLOR_BUFFER_BIT);
    const float proj[16] = {2.0f / 320, 0, 0, 0, 0, 2.0f / 240, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1};
    capturing = true;
    self->drawBottom(proj, 0, 0, w, h);
    capturing = false;
    // a screen is opaque (the blended passes leave coverage in the alpha)
    glColorMask(GL_FALSE, GL_FALSE, GL_FALSE, GL_TRUE);
    glClearColor(0, 0, 0, 1);
    glClear(GL_COLOR_BUFFER_BIT);
    glColorMask(GL_TRUE, GL_TRUE, GL_TRUE, GL_TRUE);
    glBindFramebuffer(GL_FRAMEBUFFER, (GLuint)prevFbo);
    glDeleteFramebuffers(1, &fbo);
    glViewport(vp[0], vp[1], vp[2], vp[3]);
    return t;
}

// application.ts setMenuTopOpacity: the upper screen's layouts over the wall
void CtrHome::Impl::setMenuTopOpacity(float fraction) {
    const float o = roundf(255 * std::clamp(fraction, 0.0f, 1.0f));
    for (LayoutInstance* l : {top.get(), hud.get()}) if (l) l->layout().opacity = o;
}

// banner.ts setDark: WhiteBlack frame 1 over the suspended title (the camera and screenshot bars
// turn dark grey with white glyphs), frame 0 the file's own state
void CtrHome::Impl::appDark(bool dark) {
    if (!top || !top->hasAnimation("WhiteBlack")) return;
    PlayOptions o = groupsOpt();
    o.paused = true;
    o.loop = 0;
    if (Animator* wb = top->play("WhiteBlack", o)) {
        wb->seek(dark ? 1 : 0);
        wb->apply();
        top->stop("WhiteBlack");
    }
}

void CtrHome::Impl::appSuspend(const std::vector<std::string>& ids, GLuint topTex, int tw, int th, GLuint bottomTex,
                               int bw, int bh) {
    (void)bw; (void)bh;
    appEnd();
    if (!pause) pause = make("launcher", "LncPauseFade_D_00");
    if (!pause || !topTex) {
        if (topTex) glDeleteTextures(1, &topTex);
        if (bottomTex) glDeleteTextures(1, &bottomTex);
        return;
    }
    sound("SE_CTR_HOME_HOMEBUTTON");
    // a launch from this instance left the launcher in its app state behind the logo fade: the
    // menu is simply there again under the pause fade
    if (state == StAppStart || state == StAppRunning) {
        fadeActive = false;
        setState(StIdle);
        PlayOptions o = groupsOpt();
        o.frame = 100000;   // the bar in place (it is replaced while the title is suspended)
        buttons->play("SceneIn", o);
    }
    appIds = ids;
    // scene3d.ts framebufferLayout: the upper screen a quarter turn into 256 x 512, its top edge at
    // u = 240 / 256, its columns centred along v, right to left. The capture keeps its own aspect
    // (a wide panel's is wider than 400 columns, at most the 512 there are).
    const float wl = std::clamp(240.0f * (float)tw / (float)std::max(th, 1), 1.0f, 512.0f);
    const float ta[3] = {0, -512 / wl, (512 + wl) / (2 * wl)};
    const float tb[3] = {-256.0f / 240, 0, 1};
    appTopTex = remapCopy(topTex, APP_TEX_W, APP_TEX_H, ta, tb);
    // application.ts captureLayout: the lower screen a quarter turn into 256 x 512 (P_App_00 maps
    // u 0.9375..0 down the pane, v 0..0.625 across it)
    const float ba[3] = {0, 512.0f / 320, 0};
    const float bb[3] = {-256.0f / 240, 0, 1};
    appBottomTex = remapCopy(bottomTex ? bottomTex : topTex, APP_TEX_W, APP_TEX_H, ba, bb);
    glDeleteTextures(1, &topTex);
    if (bottomTex) glDeleteTextures(1, &bottomTex);
    if (top3d && appTopTex) {
        top3d->setAppImage(appTopTex->id, appTopTex->width, appTopTex->height);
        top3d->setSuspendedKeys(ids);
        top3d->setWallOut();
        top3d->followApp(CtrTop::AppState::Suspended, CtrTop::AppBanner::Pause);
    }
    menuTex = renderMenuBottom();
    pause->replaceTexture("P_App_00", appBottomTex);
    pause->replaceTexture("P_Lnc_00", menuTex);
    pause->setAlpha("P_Lnc_00", 0);   // the clip's first frame, not the layout's rest pose
    pauseDone = false;
    const int gen = ++pauseGen;
    if (Animator* c = pause->play("SceneIn")) c->onFinish = [this, gen]() { if (gen == pauseGen) pauseDone = true; };
    else pauseDone = true;
    pause->refresh();
    setMenuTopOpacity(0);
    ALOGI("ctr: pause fade from the title (%zu ids)", ids.size());
    appStep = AppSuspending;
    appFrame = 0;
}

void CtrHome::Impl::appFinishSuspend() {
    setMenuTopOpacity(1);
    appShowBar(true);
    appSleep(true);
    appDark(true);
    appStep = AppSuspended;
    appFrame = 0;
}

// Resume (HOME, the Resume button or the title's icon): the menu stops taking input at once and
// fades back into the title (the console's 17 frames of the title answering are not waited for:
// nano's app never stopped).
void CtrHome::Impl::appResume() {
    if (appStep != AppSuspended || (appDialog && appDialog->active())) return;
    sound("SE_CTR_HOME_HOMEBUTTON_RETURN");
    appStartResume();
}

void CtrHome::Impl::appStartResume() {
    menuTex = renderMenuBottom();   // with the suspended bar, as it is now
    pause->replaceTexture("P_Lnc_00", menuTex);
    pause->setAlpha("P_Lnc_00", 255);
    appShowBar(false);
    appSleep(false);
    appDark(false);
    if (windowShown || !windowFor.empty()) {
        top->stop("Sleep");
        top->setVisible("N_Wndw_00", false);
        windowShown = false;
        windowFor.clear();
    }
    pauseDone = false;
    const int gen = ++pauseGen;
    if (Animator* c = pause->play("SceneOut")) c->onFinish = [this, gen]() { if (gen == pauseGen) pauseDone = true; };
    else pauseDone = true;
    if (top3d) top3d->followApp(CtrTop::AppState::Resumed, CtrTop::AppBanner::Restart);
    ALOGI("ctr: resume fade into the suspended title");
    appStep = AppResuming;
    appFrame = 0;
}

// Close: lau_dlg_quit0 (Cancel / Close); nano cannot tell which titles save on exit, so it always
// asks, as the console does for titles without autoSaveOnExit
void CtrHome::Impl::appConfirmClose() {
    if (!appDialog) appDialog = std::make_unique<CtrDialog>(*A, [this](const char* n) { sound(n); });
    appDialogKind = 1;
    appConfirmSel = 1;
    appDialog->set(A->messageText("lau_dlg_quit0"), {A->messageText("lau_dlg_2b_canc0"), A->messageText("lau_2b_close")},
                   appConfirmSel);
    appDialog->show();
}

// The menu stops taking input, the window goes, the wall plays AppQuit while the closing notice
// comes up over the lower screen; the host ends the title meanwhile.
void CtrHome::Impl::appQuit() {
    appStep = AppQuitting;
    appFrame = 0;
    appDialogKind = 2;
    if (windowShown || !windowFor.empty()) {
        top->stop("Sleep");
        top->setVisible("N_Wndw_00", false);
        windowShown = false;
        windowFor.clear();
    }
    if (host) host->closeApp();
}

void CtrHome::Impl::appClose() {
    appDialogKind = 0;
    if (top3d) top3d->followApp(CtrTop::AppState::Closed, CtrTop::AppBanner::None);
    appDark(false);
    appBarDone = true;
    if (appBarShown && appBar) {
        appBarDone = false;
        const int gen = ++appBarGen;
        if (Animator* c = appBar->play("SceneOut", groupsOpt())) c->onFinish = [this, gen]() { if (gen == appBarGen) appBarDone = true; };
        else appBarDone = true;
    }
    appStep = AppClosing;
    appFrame = 0;
}

void CtrHome::Impl::appEnd() {
    if (appStep == AppNone && !pause && !appBar) return;
    appShowBar(false);
    appSleep(false);
    sleepOn.clear();
    appDark(false);
    setMenuTopOpacity(1);
    if (windowShown || !windowFor.empty()) {
        top->stop("Sleep");
        top->setVisible("N_Wndw_00", false);
        windowShown = false;
        windowFor.clear();
    }
    if (top3d) {
        top3d->clearAppImage();
        top3d->setSuspendedKeys({});
        topFocusKey.clear();   // the focused title's banner comes back
    }
    if (appDialog) appDialog.reset();
    appDialogKind = 0;
    pause.reset();
    appBar.reset();
    appTopTex.reset();
    appBottomTex.reset();
    menuTex.reset();
    windowIcon.reset();
    appIds.clear();
    appStep = AppNone;
    appFrame = 0;
}

// application.ts showButtons / hideButtons: the suspended bar replaces the launcher's
void CtrHome::Impl::appShowBar(bool shown) {
    if (shown) {
        if (!appBar) {
            appBar = make("launcher", "LncBtmBtn_02");
            if (!appBar) return;
            if (Pane* scene = appBar->pane("N_Scene_00"))
                for (Pane* c : scene->children)
                    c->visible = !strcmp(c->name.c_str(), APP_CLOSE.node) || !strcmp(c->name.c_str(), APP_RESUME.node);
            for (const AppButton* b : APP_BUTTONS)
                for (const char* t : b->texts) if (appBar->pane(t)) appBar->setMessage(t, b->label);
        }
        ++appBarGen;
        appBar->play("SceneIn", groupsOpt());
        appBarShown = true;
        bbEnabled = false;
    } else {
        appBarShown = false;
        appTouched = -1;
        bbEnabled = true;
    }
}

// sleepicon.ts: LncIconSleep_00 over the suspended title's grid icon, Appear then the Sleep loop,
// DisAppear when it is no longer suspended
void CtrHome::Impl::appSleep(bool on) {
    if (!sleepIcon.layout) return;
    if (on) {
        sleepOn = "on";
        sleepLeaving = false;
        if (Animator* a = sleepIcon.layout->play("Appear", groupsOpt()))
            a->onFinish = [this]() {
                if (sleepOn.empty() || sleepLeaving) return;
                PlayOptions o = groupsOpt();
                o.loop = 1;
                sleepIcon.layout->play("Sleep", o);
            };
    } else if (!sleepOn.empty() && !sleepLeaving) {
        sleepLeaving = true;
        sleepIcon.layout->stop("Sleep");
        if (Animator* a = sleepIcon.layout->play("DisAppear", groupsOpt()))
            a->onFinish = [this]() { if (sleepLeaving) { sleepOn.clear(); sleepLeaving = false; } };
        else sleepOn.clear();
    }
}

// banner.ts show: the "Suspended software" window while the suspended title is focused (its name,
// its icon in the corner 0..0.75 of P_Icon_00, ScaleUpDown then the Sleep pulse)
void CtrHome::Impl::appWindow() {
    const Grid& g = grid();
    const HomeItem* it = g.onBar ? nullptr : g.item(g.cursor);
    const bool want = appStep == AppSuspended && it && isSuspended(it->id) && state == StIdle;
    if (!want) {
        if (windowShown || !windowFor.empty()) {
            // cleared first: stopping ScaleUpDown runs its callback, which then shows nothing
            windowShown = false;
            windowFor.clear();
            top->stop("Sleep");
            top->stop("ScaleUpDown");
            top->setVisible("N_Wndw_00", false);
        }
        return;
    }
    if (windowFor == it->id) return;
    windowFor = it->id;
    windowShown = false;
    top->setMessage("T_TextTop_00", "lau_pose_title_u");
    top->setText("T_AppTitle_00", it->name);
    top->setMessage("T_TextBtmR_00", "lau_rest_comm_u");
    if (TexturePtr icon = titleTexture(*it)) {
        const float a[3] = {1 / 0.75f, 0, 0}, b[3] = {0, 1 / 0.75f, 0};
        windowIcon = remapCopy(icon->id, 256, 256, a, b);
        if (windowIcon) top->replaceTexture("P_Icon_00", windowIcon);
    }
    const std::string id = it->id;
    if (Animator* a = top->play("ScaleUpDown", groupsOpt())) {
        a->onFinish = [this, id]() {
            top->stop("ScaleUpDown");
            if (windowFor != id) return;
            for (const char* n : {"N_Wndw_00", "W_Wndw_00", "T_TextTop_00", "T_AppTitle_00", "N_TextBtm_00", "T_TextBtmR_00", "N_IconWrp_00"}) {
                top->setVisible(n, true);
                top->setAlpha(n, !strcmp(n, "W_Wndw_00") ? WINDOW_ALPHA : 255);
            }
            top->setVisible("P_Sleep_00", true);
            PlayOptions o = groupsOpt();
            o.loop = 1;
            top->play("Sleep", o);
            windowShown = true;
        };
    }
}

// One tick of the suspended title; true when it took this tick's input.
bool CtrHome::Impl::appUpdate() {
    appFrame++;
    if (pause && appCovers()) pause->update(1);
    if (appBar && (appBarShown || appStep == AppClosing)) appBar->update(1);
    if (sleepIcon.layout && !sleepOn.empty()) sleepIcon.layout->update(1);
    if (appDialog) {
        appDialog->setWidth(self->bottomWidth());
        appDialog->setTopSize(self->topWidth(), self->topHeight());
        appDialog->tick();
    }
    switch (appStep) {
        case AppSuspending:
            setMenuTopOpacity((float)(appFrame - MENU_FADE_START) / MENU_FADE_FRAMES);
            if (pauseDone) appFinishSuspend();
            return true;
        case AppSuspended: {
            appWindow();
            if (appDialogKind == 1 && appDialog && appDialog->active()) {
                appDialog->set(A->messageText("lau_dlg_quit0"),
                               {A->messageText("lau_dlg_2b_canc0"), A->messageText("lau_2b_close")}, appConfirmSel);
                int result = appDialog->touch(touch.down, touch.up, touch.x, touch.y);
                if (result < 0) {
                    if (down & CTR_A) result = 1;
                    else if (down & CTR_B) result = 0;
                }
                if (result == 0 || result == 1) {
                    sound(result == 1 ? "SE_CTR_COMMON_OK" : "SE_CTR_COMMON_CANCEL");
                    appDialog->hide(result == 0);
                    if (result == 1) appQuit();
                    else appDialogKind = 0;
                }
                return true;
            }
            if (homeEvent) { appResume(); return true; }
            // nano: X closes from the buttons (the console's Close is touch only)
            if (down & CTR_X) { sound("SE_CTR_COMMON_BUTTON"); appConfirmClose(); return true; }
            if (touch.down) {
                appTouched = -1;
                for (int i = 0; i < 2; i++)
                    if (appBar && hitPane(appBar.get(), APP_BUTTONS[i]->picture, touch.x, touch.y)) appTouched = i;
                if (appTouched >= 0) {
                    appBar->play("Select", namedGroups({APP_BUTTONS[appTouched]->group}));
                    return true;
                }
                return false;
            }
            if (appTouched >= 0 && (touch.held || touch.up)) {
                if (touch.up) {
                    const int i = appTouched;
                    appTouched = -1;
                    if (appBar && hitPane(appBar.get(), APP_BUTTONS[i]->picture, touch.x, touch.y)) {
                        appBar->play("Decide", namedGroups({APP_BUTTONS[i]->group}));
                        if (APP_BUTTONS[i] == &APP_RESUME) appResume();
                        else appConfirmClose();
                    }
                }
                return true;
            }
            return false;
        }
        case AppResuming:
            setMenuTopOpacity(1 - (float)appFrame / MENU_FADE_FRAMES);
            if (pauseDone) {
                // the last frame is the title's screens: held until the host has them shown
                appStep = AppGone;
                ALOGI("ctr: resume fade done after %d frames", appFrame);
                if (host) host->resumeApp();
            }
            return true;
        case AppGone:
            return true;
        case AppQuitting:
            if (appFrame == QUIT_WALL_DELAY) {
                if (top3d) top3d->followApp(CtrTop::AppState::Closing, CtrTop::AppBanner::Quit);
                appSleep(false);
            }
            if (appDialog) {
                appDialog->set(A->messageText("lau_dlg_quit4"), {}, -1);
                if (appFrame == CLOSE_NOTICE_SHOW) appDialog->show();
                if (appFrame == CLOSE_NOTICE_DISMISS) appDialog->hide(false);
                if (appFrame > CLOSE_NOTICE_DISMISS && !appDialog->active()) appClose();
            } else if (appFrame >= CLOSE_NOTICE_DISMISS) {
                appClose();
            }
            return true;
        case AppClosing:
            if (appBarDone) appEnd();
            return true;
        default:
            return false;
    }
}

// ================================================================== chrome

void CtrHome::Impl::cursorPose(const Grid& g, float* x, float* y, float* frame) {
    if (g.onBar) {
        Pane* a = base->pane(BAR_ANCHORS[g.barButton]);
        *x = a ? a->translate[0] : 0;
        *y = a ? a->translate[1] : 0;
        *frame = g.barButton == BarSettings ? 10.0f : g.barButton >= BarZoomOut ? 12.0f : 11.0f;
        return;
    }
    Point p;
    if (&g == &grids.back() && scrollingCursor(&p)) { *x = p.x; *y = p.y; }
    else { Point q = g.slotOnScreen(g.cursor); *x = q.x; *y = q.y; }
    *frame = g.zoomValue();
}

void CtrHome::Impl::placeCursor() {
    float x, y, f;
    cursorPose(grid(), &x, &y, &f);
    Pane* r = cursor->layout().root;
    r->translate[0] = x;
    r->translate[1] = y;
    if (cursorScale) { cursorScale->seek(f); cursorScale->apply(); }
    r->visible = !cursorHidden();
}

void CtrHome::Impl::spawnTrail(float x, float y, float frame) {
    Trail& t = trail[trailNext];
    trailNext = (trailNext + 1) % 2;
    if (!t.layout) return;
    Pane* r = t.layout->layout().root;
    r->translate[0] = x;
    r->translate[1] = y;
    if (t.scale) { t.scale->seek(frame); t.scale->apply(); }
    t.active = true;
    Animator* a = t.layout->play("DisAppear", groupsOpt());
    Trail* tp = &t;
    if (a) a->onFinish = [tp, a]() { if (tp->layout->find("DisAppear") == a) tp->active = false; };
}

void CtrHome::Impl::setArrow(int side, bool visible) {
    if (arrowShown[side] == visible) return;
    arrowShown[side] = visible;
    arrows->setVisible(side == 0 ? "N_arwL_00" : "N_arwR_00", true);
    arrows->play(visible ? "Appear" : "DisAppear", namedGroups({side == 0 ? "G_SceneL_00" : "G_SceneR_00"}));
}

void CtrHome::Impl::updateArrows() {
    Grid& g = grid();
    const bool suppressed = state == StAppStart || state == StAppRunning;
    const float pitch = g.metrics.pitchX;
    setArrow(0, !suppressed && g.scroll > pitch / 2);
    setArrow(1, !suppressed && g.scroll < g.maxScroll() - pitch / 2);
}

void CtrHome::Impl::updateIndicator() {
    Grid& g = grid();
    if (bumpFrames > 0) bumpFrames--;
    indShown = state == StColumnScroll || scroller.state == Scroller::Dragging || scroller.state == Scroller::Inertia ||
               scroller.state == Scroller::MoveTo || bumpFrames > 0;
    Pane* sp = indicatorL->pane("StartPic");
    Pane* ep = indicatorL->pane("EndPic");
    if (!sp || !ep) return;
    const float t = indFlash / 10.0f;
    Layout& L = indicatorL->layout();
    for (Pane* p : {sp, ep}) {
        Material& m = L.materials[p->material];
        for (int i = 0; i < 4; i++) {
            m.bufferColor[i] = roundf(indBlackA[i] + (indBlackB[i] - indBlackA[i]) * t);
            m.konst[0][i] = roundf(indWhiteA[i] + (indWhiteB[i] - indWhiteA[i]) * t);
        }
    }
    const float W = 320 + sExtraWidth;
    const float range = std::max(1.0f, g.maxScroll());
    const float width = std::clamp((float)(int)(102400 / (range + 320) * W / 320), 32.0f, W);
    const float half = (float)(int)(width / 2);
    const float left = std::clamp(g.scroll, 0.0f, range) / range * (W - width) - W / 2;
    const float alpha = roundf(indFade / 30 * 255);
    const float xs[2] = {left, left + half};
    Pane* ps[2] = {sp, ep};
    for (int i = 0; i < 2; i++) {
        ps[i]->translate[0] = xs[i];
        ps[i]->translate[1] = 89;
        ps[i]->scale[0] = half / 8;
        ps[i]->alpha = alpha;
    }
    indicatorL->refresh();
}

struct BottomSlotPanes { const char* node; const char* touch; const char* group; const char* texts[3]; };
static const BottomSlotPanes kBottomFull = {"N_BtnW_C_01", "B_BtnW_C_01", "G_BtnW_C_01", {"T_BtnFW_C_01", "T_BtnBW_C_01", "T_BtnPW_C_01"}};
static const BottomSlotPanes kBottomRight = {"N_BtnW_R_02", "B_BtnW_R_02", "G_BtnW_R_02", {"T_BtnFW_R_02", "T_BtnBW_R_02", "T_BtnPW_R_02"}};
static const BottomSlotPanes kBottomLeft = {"N_BtnW_L_03", "B_BtnW_L_03", "G_BtnW_L_03", {"T_BtnFW_L_03", "T_BtnBW_L_03", "T_BtnPW_L_03"}};

struct BottomButtonDef { const BottomSlotPanes* slot; const char* label; const char* sound; const char* action; };

static std::vector<BottomButtonDef> bottomButtons(const std::string& set) {
    if (set == "title") return {{&kBottomFull, "lau_1b_start", "SE_CTR_HOME_START", "open"}};
    if (set == "createFolder") return {{&kBottomFull, "lau_1b_make_folder", "SE_CTR_COMMON_BUTTON", "createFolder"}};
    if (set == "folder") return {{&kBottomRight, "lau_2b_folder_open", "SE_CTR_HOME_START", "open"},
                                 {&kBottomLeft, "lau_2b_folder_setting", "SE_CTR_COMMON_BUTTON", "folderSettings"}};
    if (set == "applet") return {{&kBottomRight, "lau_2b_start", "SE_CTR_HOME_START", "open"},
                                 {&kBottomLeft, "lau_2b_manual", "SE_CTR_COMMON_BUTTON", "manual"}};
    if (set == "settings") return {{&kBottomFull, "lau_1b_petit_open", "SE_CTR_HOME_LIGHT", "open"}};
    if (set == "zoomIn") return {{&kBottomFull, "lau_1b_icon_small", "SE_CTR_HOME_LINE_INC", "open"}};
    if (set == "zoomOut") return {{&kBottomFull, "lau_1b_icon_big", "SE_CTR_HOME_LINE_DEC", "open"}};
    return {};
}

void CtrHome::Impl::bottomApply(const std::string& name) {
    bbShown = name;
    std::vector<BottomButtonDef> defs = bottomButtons(name);
    if (Pane* scene = buttons->pane("N_Scene_00")) {
        for (Pane* c : scene->children) {
            bool shown = false;
            for (const auto& d : defs) if (c->name == d.slot->node) shown = true;
            c->visible = shown;
        }
    }
    for (const auto& d : defs)
        for (const char* t : d.slot->texts) buttons->setMessage(t, d.label);
}

void CtrHome::Impl::bottomSet(const std::string& name) {
    if (name == bbCurrent) return;
    bbCurrent = name;
    if (bbShown == "none") {
        bottomApply(name);
        buttons->play("ChangeUp", groupsOpt());
        return;
    }
    bbPending = name;
    bbHasPending = true;
    bbWait = 7;
    buttons->play("ChangeDw", groupsOpt());
}

void CtrHome::Impl::bottomUpdate() {
    if (!bbHasPending || --bbWait > 0) return;
    bbHasPending = false;
    bottomApply(bbPending);
    if (bbPending != "none") buttons->play("ChangeUp", groupsOpt());
}

std::string CtrHome::Impl::bottomSetFor() {
    Grid& g = grid();
    if (g.onBar) {
        if (g.barButton == BarSettings) return "settings";
        if (g.barButton == BarZoomIn) return "zoomIn";
        if (g.barButton == BarZoomOut) return "zoomOut";
        return "title";
    }
    const HomeItem* it = g.item(g.cursor);
    // an empty root slot makes a folder (bottombar.ts set 3); a generated folder (a system's
    // games) has no Settings, only the user's own do
    if (!it) return !g.isFolder() && host && host->editable("") ? "createFolder" : "none";
    if (it->kind == HomeItemKind::Folder) return host && host->userFolder(it->id) ? "folder" : "title";
    return "title";
}

// The balloon's lines: nano's names run longer than a title's SMDH name, so the name is word
// wrapped at the box width onto two lines (three without a publisher), the way the console's
// long names come split, keeping the balloon's font size; a name beyond that ends in "...".
static std::string balloonLines(const Pane* p, const std::string& name, const std::string& publisher) {
    if (!p || !p->font) return publisher.empty() ? name : name + "\n" + publisher;
    const Font& f = *p->font;
    const double sx = f.width ? (double)p->fontSize[0] / f.width : 1.0;
    auto width = [&](const std::u32string& t) {
        double w = 0;
        for (char32_t ch : t) { const Glyph* g = f.glyph(ch); w += (g ? g->charWidth : 0) * sx; }
        if (t.size() > 1) w += p->charSpace * (double)(t.size() - 1);
        return w;
    };
    const double room = p->size[0];
    std::vector<std::u32string> lines;
    std::u32string line, word;
    auto put = [&](const std::u32string& w_) {
        std::u32string trial = line.empty() ? w_ : line + U' ' + w_;
        if (width(trial) <= room) { line = trial; return; }
        if (!line.empty()) { lines.push_back(line); line.clear(); }
        // a word wider than the box breaks between characters
        for (char32_t ch : w_) {
            if (!line.empty() && width(line + ch) > room) { lines.push_back(line); line.clear(); }
            line += ch;
        }
    };
    for (char32_t ch : utf8ToU32(name)) {
        if (ch == U' ' || ch == U'\n') { if (!word.empty()) put(word); word.clear(); }
        else word += ch;
    }
    if (!word.empty()) put(word);
    if (!line.empty()) lines.push_back(line);
    const size_t maxLines = publisher.empty() || lines.size() == 3 ? 3 : 2;
    const bool withPublisher = !publisher.empty() && lines.size() <= 2;
    if (lines.size() > maxLines) {
        lines.resize(maxLines);
        std::u32string& last = lines.back();
        while (!last.empty() && width(last + U"...") > room) last.pop_back();
        while (!last.empty() && last.back() == U' ') last.pop_back();
        last += U"...";
    }
    std::string out;
    for (size_t i = 0; i < lines.size(); i++) {
        if (i) out += '\n';
        for (char32_t c : lines[i]) {
            if (c < 0x80) out += (char)c;
            else if (c < 0x800) { out += (char)(0xC0 | (c >> 6)); out += (char)(0x80 | (c & 0x3F)); }
            else if (c < 0x10000) { out += (char)(0xE0 | (c >> 12)); out += (char)(0x80 | ((c >> 6) & 0x3F)); out += (char)(0x80 | (c & 0x3F)); }
            else { out += (char)(0xF0 | (c >> 18)); out += (char)(0x80 | ((c >> 12) & 0x3F)); out += (char)(0x80 | ((c >> 6) & 0x3F)); out += (char)(0x80 | (c & 0x3F)); }
        }
    }
    if (withPublisher) out += "\n" + publisher;
    return out;
}

void CtrHome::Impl::balloonShow(const std::string& text, bool label, float x, float y, float zoom) {
    if (Pane* base_ = balloon->pane("N_Base_00")) {
        Pane* box = balloon->pane("N_LR_00");
        const float limit = 160 + sExtraWidth * 0.5f - 136 - 16;
        base_->translate[0] = x;
        base_->translate[1] = y + zoomTable(TOUCH_BOX, zoom) / 2 + 5;
        if (box) box->translate[0] = std::clamp(x, -limit, limit) - x;
    }
    const std::string key = (label ? "#" : "") + text;
    if (key != balloonText) {
        balloonText = key;
        if (label) balloon->setMessage("T_Blln_00", text);
        else balloon->setText("T_Blln_00", text);
    }
    if (balloonVisible) return;
    balloonVisible = true;
    balloon->layout().root->visible = true;
    balloon->play("Appear", groupsOpt());
}

void CtrHome::Impl::balloonHide() {
    if (!balloonVisible) return;
    balloonVisible = false;
    Animator* a = balloon->play("DisAppear", groupsOpt());
    if (a) a->onFinish = [this]() { if (!balloonVisible) balloon->layout().root->visible = false; };
}

void CtrHome::Impl::updateBalloon() {
    Grid& g = grid();
    const bool blocked = state == StZoom || state == StAppStart || cursorHidden();
    const bool zoomed = g.zoom() != 0 || (g.blending && g.to.zoom != 0);
    if (blocked || zoomed || g.onBar || !showNameBalloon) { balloonHide(); return; }
    const HomeItem* it = g.item(g.cursor);
    if (!it) { balloonHide(); return; }
    float x, y, f;
    cursorPose(g, &x, &y, &f);
    const std::string publisher = it->kind == HomeItemKind::Title ? it->publisher : std::string();
    // wrapped once per item (the name and the box do not change while it is shown)
    if (it->id != balloonItem || it->name + "\n" + publisher != balloonName) {
        balloonItem = it->id;
        balloonName = it->name + "\n" + publisher;
        balloonWrapped = balloonLines(balloon->pane("T_Blln_00"), it->name, publisher);
    }
    balloonShow(balloonWrapped, false, x, y, g.zoomValue());
}

void CtrHome::Impl::updateSheet(LayoutInstance* l, const Grid& g, float extra) {
    Pane* sheet = l->pane("W_Plt_00");
    Pane* shadow = l->pane("W_Shdw_00");
    if (!sheet || !shadow) return;
    const float inset = originXAt(g.zoomValue(), g.isFolder()) - sheetMargin;
    const float left = g.slotPosition(0).x - inset;
    const float right = g.slotPosition(g.slots - 1).x + inset;
    const float width = roundf((right - left) * 0.5f) * 2;
    bool anchoredRight = false;
    if (g.blending && g.from.zoom != g.to.zoom) {
        const int last = g.slots - 1;
        auto inLast = [&](int rows, int columns, int firstSlot) {
            const int start = columns * rows + firstSlot - rows;
            return last >= start && last < start + rows;
        };
        anchoredRight = inLast(g.from.rows, g.from.columns, g.fromFirstSlot) && inLast(g.to.rows, g.to.columns, g.toFirstSlot);
    }
    const float rightEdge = 160 + extraColumns(g.blending ? g.to.zoom : g.zoom()) * PITCH[g.blending ? g.to.zoom : g.zoom()][0] * 0.5f;
    const float x = anchoredRight ? rightEdge - sheetMargin - width * 0.5f : left + width * 0.5f - g.scroll;
    sheet->size[0] = width;
    shadow->size[0] = width + extra;
    sheet->translate[0] = x;
    shadow->translate[0] = x;
    l->refresh();
}

void CtrHome::Impl::updateChrome() {
    updateSheet(palette.get(), root(), sheetShadowExtra);
    // the closing folder's sheet while it collapses (a parent folder may stay open under it)
    if (closingFolder) updateSheet(folder.get(), closing, folderShadowExtra);
    else if (inFolder()) updateSheet(folder.get(), grid(), folderShadowExtra);
    placeCursor();
    updateArrows();
    Grid& g = grid();
    const int min = g.isFolder() ? MIN_FOLDER_ZOOM : 0;
    const int zoom = g.blending ? g.to.zoom : g.zoom();
    base->setAlpha("P_Dw_20", zoom <= min ? 120 : 255);
    base->setAlpha("P_Up_20", zoom >= MAX_ZOOM ? 120 : 255);
    updateIndicator();
    updateBalloon();
    const bool pagingHeld = showNameBalloon && (held & (CTR_LEFT | CTR_RIGHT));
    const bool hide = pagingHeld || state == StAppStart || state == StAppRunning;
    bottomSet(hide ? "none" : bottomSetFor());
    // top screen banner follows the focus, except while a folder opens or closes
    if (top3d && state != StFolderOpen && state != StFolderClose) {
        TopFocus f;
        if (g.onBar) {
            f.kind = TopFocus::Bar;
            f.bar = g.barButton;
            f.key = "bar" + std::to_string(g.barButton);
            if (host) f.plate = host->appletName(g.barButton);
            if (g.barButton >= 1 && g.barButton <= 5 && barGlyph[g.barButton]) {
                const TexturePtr& t = barGlyph[g.barButton];
                f.glyph = t->id;
                f.glyphW = t->width;
                f.glyphH = t->height;
                f.glyphTinted = true;
                for (int k = 0; k < 3; k++) f.glyphTint[k] = barTint[g.barButton][k];
            }
        } else if (const HomeItem* it = g.item(g.cursor)) {
            f.kind = it->kind == HomeItemKind::Folder ? TopFocus::Folder : TopFocus::Title;
            f.key = it->id;
            f.plate = it->name;
            if (it->kind == HomeItemKind::Folder && host) {
                // a folder with a monochrome icon (a game system's, Recently Played's...): the
                // icon as a solid on the top screen instead of the folder
                int gw = 0, gh = 0;
                CtrHomeHost::IconArt art;
                const GLuint gl = host->titleIconTexture(*it, &gw, &gh, &art);
                if (gl && art.mono) {
                    if (TexturePtr t = titleTextureOwn(*it)) {
                        f.glyph = t->id;
                        f.glyphW = t->texWidth > 0 ? t->texWidth : t->width;
                        f.glyphH = t->texHeight > 0 ? t->texHeight : t->height;
                        f.glyphTinted = host->itemTint(*it, f.glyphTint);
                    }
                } else if (gl && art.coverAspect <= 0 && !art.pixelArt) {
                    // a full colour icon (PICO-8's blocks): the solid keeps its own colours
                    if (TexturePtr t = tintedCopy(gl, gw, gh, 0.999f)) {
                        f.glyph = t->id;
                        f.glyphW = t->texWidth > 0 ? t->texWidth : t->width;
                        f.glyphH = t->texHeight > 0 ? t->texHeight : t->height;
                        f.glyphColor = true;
                    }
                }
            }
            if (it->kind == HomeItemKind::Title && !it->plate.empty()) f.plate = it->plate;
            else if (it->kind == HomeItemKind::Title && !it->publisher.empty()) f.plate += "\n" + it->publisher;
            if (it->kind == HomeItemKind::Title && host) {
                CtrHomeHost::TitleArt ta;
                host->titleArt(*it, &ta);
                // a 3DS banner still being read keeps the shown banner (no 2D banner flash)
                if (ta.pending) return;
                f.cbmd = ta.cbmd;
                f.dsIcon = ta.dsIcon || ta.dsCard;
                f.pixelArt = ta.dsIcon;
                // banner models take their textures stored upside down (like the name plates).
                // The cards' icon materials take only the icon's colour (their alpha is the
                // label mask), so an icon is flattened onto white like the console's composed one.
                if (ta.icon) {
                    if (TexturePtr c = tintedCopy(ta.icon, ta.w, ta.h, 1.0f, ta.art.coverAspect, true, true)) {
                        f.icon = c->id;
                        f.iconW = c->width;
                        f.iconH = c->height;
                    }
                } else if (!ta.cbmd) {
                    // no banner art: the grid's own icon (an app's icon) on the card, else the
                    // generic box face with the title's initial (on the DS cartridge for a DS game)
                    TexturePtr own = ta.dsCard ? nullptr : titleTextureOwn(*it);
                    TexturePtr c = own ? tintedCopy(own->id, own->width, own->height, 1.0f, 0, true, true) : boxTexture(*it, true, false);
                    if (c) {
                        f.icon = c->id;
                        f.iconW = c->width;
                        f.iconH = c->height;
                    }
                }
            }
        } else {
            f.kind = TopFocus::Empty;
            f.key = "empty";
        }
        const std::string k = std::to_string((int)f.kind) + "|" + f.key + "|" + f.plate + "|" + std::to_string(f.icon) +
                              "|" + std::to_string(f.glyph) + (f.glyphColor ? "c" : "") +
                              (f.glyphTinted ? "|" + std::to_string(f.glyphTint[0]) + "," + std::to_string(f.glyphTint[1]) +
                                                   "," + std::to_string(f.glyphTint[2])
                                             : std::string()) +
                              (f.cbmd ? "|cbmd" : "");
        if (k != topFocusKey) {
            topFocusKey = k;
            top3d->focus(f);
        }
    }
}

// ================================================================== moving icons (moving.ts)

namespace {
enum DropKindId { DK_INTO_FOLDER = 0, DK_FOLDER_ON_FOLDER = 1, DK_FULL_FOLDER = 3, DK_SLOT = 4 };
}

// Picks up the icon in `slot` (long press, FUN_0029c1f4 -> state 0xe): ICON_GRAB, BADGE_FLOAT
// for a folder; the hole LncIconPickUpBlank_00 is left behind.
void CtrHome::Impl::startGrab(int slot, float x, float y) {
    Grid& g = grid();
    const HomeItem* it = g.item(slot);
    if (!it) return;
    mover = Held();
    mover.item = *it;
    mover.gridIndex = mover.sourceIndex = (int)grids.size() - 1;
    mover.origin = slot;
    mover.at = {x, y};
    mover.hover = slot;
    mover.scrollFrom = mover.scrollTo = g.scroll;
    mover.target = slot;
    mover.copy = host && !host->editable(g.folderId);
    holding = true;
    g.hiddenSlot = mover.copy ? -1 : slot;   // a copy leaves its title in place
    balloonHide();
    sound(it->kind == HomeItemKind::Folder ? "SE_CTR_BADGE_FLOAT" : "SE_CTR_HOME_ICON_GRAB");
    setState(StGrab);
}

bool CtrHome::Impl::arrowDisc(int side, float x, float y) {
    if (!arrowShown[side]) return false;
    for (int i = 0; i < 7; i++) {
        char n[16];
        snprintf(n, sizeof(n), side == 0 ? "B_arwL_0%d" : "B_arwR_0%d", i);
        if (hitPane(arrows.get(), n, x, y)) return true;
    }
    return false;
}

// What a drop on a slot would do (FUN_001d9e8c): into a user folder (unless full), a folder over
// a folder (a swap), or onto a plain slot (a move or a swap). A generated folder takes nothing.
int CtrHome::Impl::dropKind(int slot) const {
    const Grid& g = grids[mover.gridIndex];
    const HomeItem* t = g.item(slot);
    if (!t || t->kind != HomeItemKind::Folder) return DK_SLOT;
    if (mover.item.kind == HomeItemKind::Folder) return DK_FOLDER_ON_FOLDER;
    if (!host || !host->userFolder(t->id)) return DK_SLOT;
    return (int)host->items(t->id).size() >= FOLDER_SLOTS ? DK_FULL_FOLDER : DK_INTO_FOLDER;
}

// Hovering (FUN_002a1224): a plain slot or a folder under a held folder glows with ICON_OVER;
// a folder that would take the icon plays ICON_DROP_FOLDER and opens after a while.
void CtrHome::Impl::hoverSlotAt(int slot) {
    Grid& g = grids[mover.gridIndex];
    const int hover = mover.copy || (slot == mover.origin && mover.gridIndex == mover.sourceIndex) ? -1 : slot;
    const int kind = hover >= 0 ? dropKind(hover) : -1;
    const bool glow = kind == DK_SLOT || kind == DK_FOLDER_ON_FOLDER;
    g.hoverSlot = glow ? hover : -1;
    g.openSlot = kind == DK_INTO_FOLDER ? hover : -1;
    if (hover == mover.hover) {
        if (kind >= 0 && !glow && !g.isFolder()) mover.folderHover++;
        return;
    }
    mover.hover = hover;
    mover.folderHover = 0;
    if (kind >= 0) sound(glow ? "SE_CTR_HOME_ICON_OVER" : "SE_CTR_HOME_ICON_DROP_FOLDER");
}

// The held icon in an arrow disc pages the grid a view at a time over 16 eased frames, a few
// frames after it entered; every column crossed plays ICON_SCROLL. A page runs to its end.
void CtrHome::Impl::edgeScroll(float x, float y, bool touching) {
    Grid& g = grids[mover.gridIndex];
    mover.edge = !touching ? -1 : arrowDisc(0, x, y) ? 0 : arrowDisc(1, x, y) ? 1 : -1;
    mover.edgeFrames = mover.edge >= 0 ? mover.edgeFrames + 1 : 0;
    if (mover.scrollFrame >= PAGE_FRAMES && mover.edgeFrames > EDGE_DELAY_FRAMES && mover.edge >= 0) {
        const int dir = mover.edge == 1 ? 1 : -1;
        const int first = std::clamp(g.firstColumn + dir * g.columns(), 0, g.maxFirstColumn());
        if (first != g.firstColumn) {
            g.firstColumn = first;
            mover.scrollFrom = g.scroll;
            mover.scrollTo = g.scrollFor(first);
            mover.scrollFrame = 0;
            arrows->play("Repeat", namedGroups({mover.edge == 0 ? "G_arwL_00" : "G_arwR_00"}));
        }
    }
    if (mover.scrollFrame >= PAGE_FRAMES) return;
    mover.scrollFrame++;
    const int before = g.columnAt(g.scroll);
    g.scroll = lerpf(mover.scrollFrom, mover.scrollTo, ease((float)mover.scrollFrame / PAGE_FRAMES));
    if (g.columnAt(g.scroll) != before) sound("SE_CTR_HOME_ICON_SCROLL");
}

// State 0xe: the icon follows the stylus; a lift drops it.
void CtrHome::Impl::updateGrab() {
    if (!holding) { setState(StIdle); return; }
    if (mover.buttons) { updateGrabButtons(); return; }
    Grid& g = grids[mover.gridIndex];
    if (touch.held) {
        mover.at = {touch.x, touch.y};
        edgeScroll(touch.x, touch.y, true);
        if (mover.edge < 0) hoverSlotAt(g.slotAt(touch.x, touch.y, zoomTable(TOUCH_BOX, g.zoomValue())));
        const bool into = mover.hover >= 0 && g.openSlot == mover.hover;
        mover.scale = into ? std::max(HOVER_SHRINK_MIN, mover.scale - HOVER_SHRINK_STEP) : std::min(1.0f, mover.scale + HOVER_SHRINK_STEP);
        if (mover.folderHover > FOLDER_HOVER_FRAMES) {
            // a folder hovered long enough opens under the held title (state 0x2b, back to 0xe)
            const HomeItem* f = g.item(mover.hover);
            mover.folderHover = 0;
            if (f && f->kind == HomeItemKind::Folder) {
                g.hoverSlot = g.openSlot = -1;
                openFolder(mover.hover);
                setState(StGrab);
                mover.gridIndex = (int)grids.size() - 1;
                mover.hover = -1;
                mover.scrollFrom = mover.scrollTo = grids[mover.gridIndex].scroll;
            }
        }
        return;
    }
    if (mover.scrollFrame < PAGE_FRAMES) { edgeScroll(-1000, -1000, false); return; }
    dropHeld(mover.at.x, mover.at.y);
}

// Moving with buttons (nano): the held icon sits lifted over the slot it would land on. The
// D-pad steps it a slot at a time (a column sideways, a row up or down); a step past the view
// pages it like the arrow discs. In a folder, up from the top row reaches the header strip.
void CtrHome::Impl::moveCell(int dx, int dy) {
    Grid& g = grids[mover.gridIndex];
    const int rows = g.rows();
    int cell = mover.cell;
    if (cell == -2) {
        if (dy > 0) cell = std::clamp(g.firstColumn * rows, 0, g.slots - 1);   // back down from the header
        else return;
    } else if (dy != 0) {
        const int row = cell % rows + dy;
        if (row < 0) {
            if (g.isFolder() && mover.item.kind == HomeItemKind::Title) cell = -2;
            else return;
        } else if (row >= rows) {
            return;
        } else {
            cell += dy;
        }
    } else {
        const int next = cell + dx * rows;
        if (next < 0 || next >= g.slots) return;
        cell = next;
    }
    if (cell >= g.slots) cell = g.slots - 1;
    mover.cell = cell;
    // a slot plays its hover sound (hoverSlotAt); the header strip sounds like a slot
    if (cell < 0) { sound("SE_CTR_HOME_ICON_OVER"); return; }
    const int column = cell / rows;
    if (g.columnVisible(column)) return;
    // page so the column is in view, the arrow disc's eased page
    const int first = std::clamp(column < g.firstColumn ? column : column - g.columns() + 1, 0, g.maxFirstColumn());
    g.firstColumn = first;
    mover.scrollFrom = g.scroll;
    mover.scrollTo = g.scrollFor(first);
    mover.scrollFrame = 0;
}

void CtrHome::Impl::updateGrabButtons() {
    Grid& g = grids[mover.gridIndex];
    const uint32_t pressed = down | repeat;
    if (mover.scrollFrame < PAGE_FRAMES) edgeScroll(-1000, -1000, false);
    if (down & CTR_B) {
        sound("SE_CTR_HOME_ICON_RELEASE");
        g.hoverSlot = g.openSlot = -1;
        flyBack();
        return;
    }
    if (pressed & CTR_UP) moveCell(0, -1);
    else if (pressed & CTR_DOWN) moveCell(0, 1);
    else if (pressed & CTR_LEFT) moveCell(-1, 0);
    else if (pressed & CTR_RIGHT) moveCell(1, 0);
    if (mover.cell == -2) {
        // over the header strip (B_T_00)
        Pane* h = folder ? folder->pane("B_T_00") : nullptr;
        if (h) {
            const Box b = paneBox(h);
            mover.at = {(b.left + b.right) / 2, (b.top + b.bottom) / 2};
        }
        g.hoverSlot = g.openSlot = -1;
        mover.hover = -1;
    } else {
        mover.at = g.slotOnScreen(mover.cell);
        // the lift is drawn above the slot; the slot itself is what a drop takes
        hoverSlotAt(mover.cell);
        mover.folderHover = 0;   // with buttons a folder takes the title on A, it does not open
    }
    const bool into = mover.hover >= 0 && g.openSlot == mover.hover;
    mover.scale = into ? std::max(HOVER_SHRINK_MIN, mover.scale - HOVER_SHRINK_STEP) : std::min(1.0f, mover.scale + HOVER_SHRINK_STEP);
    if ((down & CTR_A) && mover.scrollFrame >= PAGE_FRAMES) dropHeld(mover.at.x, mover.at.y);
}

std::vector<std::pair<int, std::string>> CtrHome::Impl::slotList(const Grid& g) const {
    std::vector<std::pair<int, std::string>> out;
    for (int i = 0; i < (int)g.slotItem.size(); i++)
        if (g.slotItem[i] >= 0) out.emplace_back(i, g.items[g.slotItem[i]].id);
    return out;
}

void CtrHome::Impl::saveGrid(const Grid& g) {
    if (host) host->layoutChanged(g.folderId, slotList(g));
}

static void placeItem(Grid& g, int slot, const HomeItem& it) {
    if ((int)g.slotItem.size() <= slot) g.slotItem.resize(slot + 1, -1);
    g.items.push_back(it);
    g.slotItem[slot] = (int)g.items.size() - 1;
}

// Release (FUN_001d4e74): onto an empty slot the icon lands at once, onto another item the two
// swap and fly, onto a folder the title flies in shrinking and fading, anywhere else it flies
// back. Inside a user folder the header strip takes a title back out to the root.
void CtrHome::Impl::dropHeld(float x, float y) {
    Grid& g = grids[mover.gridIndex];
    g.hoverSlot = g.openSlot = -1;
    if (mover.gridIndex != mover.sourceIndex) { dropAcross(x, y); return; }
    mover.at = {mover.at.x, std::min(HELD_TOP, mover.at.y + std::trunc(zoomTable(TOUCH_BOX, g.zoomValue()) * 0.2f))};
    if (g.isFolder() && mover.item.kind == HomeItemKind::Title && hitPane(folder.get(), "B_T_00", x, y)) {
        // takeOut: the title goes back to the first free root slot
        Grid& root_ = root();
        bool onRoot = false;   // a copy of a title the root already shows is not doubled
        for (const HomeItem& r_ : root_.items) onRoot = onRoot || r_.id == mover.item.id;
        int free_ = 0;
        while (root_.item(free_)) free_++;
        if (!(mover.copy && onRoot)) placeItem(root_, free_, mover.item);
        if (!mover.copy) {
            g.slotItem[mover.origin] = -1;
            saveGrid(g);
        }
        saveGrid(root_);
        sound("SE_CTR_HOME_ICON_RELEASE");
        mover.target = -1;
        reloadAfterDrop = true;
        setState(StMoveIcon);
        return;
    }
    if (mover.copy) {
        // a generated folder keeps its order: anywhere but the header, the copy flies back
        sound("SE_CTR_HOME_ICON_RELEASE");
        flyBack();
        return;
    }
    const int slot = g.slotAt(x, y, zoomTable(TOUCH_BOX, g.zoomValue()));
    const int target = slot == mover.origin ? -1 : slot;
    const int kind = target >= 0 ? dropKind(target) : -1;
    const HomeItem* targetItem = target >= 0 ? g.item(target) : nullptr;
    if (kind == DK_FULL_FOLDER) {
        // a full folder takes nothing more (state 0x8e, the 0x8a refusal: error sound)
        sound("SE_CTR_COMMON_ERROR");
        if (host) host->notice("lau_dlg_folder_icon_full", A->messageText("lau_dlg_folder_icon_full"));
        flyBack();
        return;
    }
    if (kind == DK_INTO_FOLDER && targetItem) {
        mover.target = target;
        mover.into = targetItem->id;
        g.skipSlot = -1;
        g.openSlot = target;
        setState(StMoveIcon);
        return;
    }
    if (target < 0 || (g.isFolder() && mover.item.kind != HomeItemKind::Title)) {
        sound("SE_CTR_HOME_ICON_RELEASE");
        flyBack();
        return;
    }
    // swap the two slots (FUN_0029cd0c); the root grows to the slot when needed
    const HomeItem swappedCopy = targetItem ? *targetItem : HomeItem();
    const bool hadTarget = targetItem != nullptr;
    if ((int)g.slotItem.size() <= target) g.slotItem.resize(target + 1, -1);
    std::swap(g.slotItem[mover.origin], g.slotItem[target]);
    saveGrid(g);
    if (!hadTarget) {
        sound("SE_CTR_HOME_ICON_EXCHANGE");
        g.cursor = target;
        g.onBar = false;
        g.hiddenSlot = -1;
        holding = false;
        setState(StIdle);
        return;
    }
    sound("SE_CTR_HOME_ICON_RELEASE");
    mover.hasSwapped = true;
    mover.swapped = swappedCopy;
    mover.swapAt = g.slotOnScreen(target);
    mover.target = target;
    g.hiddenSlot = mover.origin;
    g.skipSlot = target;
    setState(StMoveIcon);
}

// Release in a user folder that opened under a held root title (FUN_002adc78): onto an empty
// folder slot the title moves in, onto a title the two trade places, anywhere else it stays.
void CtrHome::Impl::dropAcross(float x, float y) {
    Grid& g = grids[mover.gridIndex];
    Grid& src = grids[mover.sourceIndex];
    const int slot = g.slotAt(x, y, zoomTable(TOUCH_BOX, g.zoomValue()));
    const bool header = hitPane(folder.get(), "B_T_00", x, y);
    mover.target = -1;
    if (slot < 0 || header || mover.item.kind != HomeItemKind::Title || !host || !host->userFolder(g.folderId)) {
        sound("SE_CTR_HOME_ICON_RELEASE");
        finishDrop();
        return;
    }
    const HomeItem* other = g.item(slot);
    const bool hadOther = other != nullptr;
    const HomeItem otherCopy = hadOther ? *other : HomeItem();
    placeItem(g, slot, mover.item);
    if (hadOther) placeItem(src, mover.origin, otherCopy);
    else src.slotItem[mover.origin] = -1;
    saveGrid(src);
    saveGrid(g);
    sound(hadOther ? "SE_CTR_HOME_ICON_RELEASE" : "SE_CTR_HOME_ICON_EXCHANGE");
    g.cursor = slot;
    g.onBar = false;
    reloadAfterDrop = true;
    finishDrop();
}

void CtrHome::Impl::flyBack() {
    Grid& g = grids[mover.gridIndex];
    mover.target = mover.origin;
    g.hiddenSlot = -1;
    g.skipSlot = mover.origin;
    setState(StMoveIcon);
}

// The title goes into the folder once its flight ended (FUN_002a2620 -> FUN_001c0214).
void CtrHome::Impl::putIntoFolder(const std::string& folderId) {
    if (mover.item.kind != HomeItemKind::Title) return;
    Grid f;
    f.folderId = folderId;
    loadGrid(f);
    int free_ = 0;
    while (free_ < FOLDER_SLOTS && f.item(free_)) free_++;
    if (free_ >= FOLDER_SLOTS) return;
    placeItem(f, free_, mover.item);
    saveGrid(f);
    Grid& g = grids[mover.gridIndex];
    g.slotItem[mover.origin] = -1;
    saveGrid(g);
    reloadAfterDrop = true;
}

namespace {
Point flightTarget(const Grid& g, int slot) {
    Point p = g.slotOnScreen(slot);
    p.y += std::trunc(zoomTable(TOUCH_BOX, g.zoomValue()) * FLIGHT_AIM);
    return p;
}

// One frame of flight toward `to`: a fifth of the distance, 4..20; done when landed or off screen.
bool fly(const Grid& g, Point& at, const Point& to) {
    const float dx = to.x - at.x, dy = to.y - at.y;
    const float distance = hypotf(dx, dy);
    const float step = std::max(FLIGHT_MIN, std::min(FLIGHT_MAX, distance * FLIGHT_SHARE));
    if (distance <= step) { at = to; return true; }
    at.x += dx / distance * step;
    at.y += dy / distance * step;
    const float half = zoomTable(TOUCH_BOX, g.zoomValue()) / 2;
    const float edge = 160 + sExtraWidth / 2;
    return at.x + half < -edge || at.x - half > edge;
}
}  // namespace

// State 0xf: the dropped icon (and a swapped one) fly onto their slots, then the cursor comes.
void CtrHome::Impl::updateDrop() {
    if (!holding || mover.target < 0) { finishDrop(); return; }
    const Grid& g = grids[mover.gridIndex];
    bool landed = fly(g, mover.at, flightTarget(g, mover.target));
    if (!mover.into.empty()) {
        mover.scale = std::max(SHRINK_MIN, mover.scale - SHRINK_STEP);
        mover.alpha = mover.alpha < FADE_MIN + FADE_STEP ? FADE_MIN : mover.alpha - FADE_STEP;
        if (!landed || mover.scale > SHRINK_MIN || mover.alpha > FADE_MIN) return;
        putIntoFolder(mover.into);
        mover.into.clear();
        sound("SE_CTR_HOME_ICON_EXCHANGE");
    }
    if (mover.hasSwapped) {
        const bool other = fly(g, mover.swapAt, flightTarget(g, mover.origin));
        if (landed && other) mover.rest++;
        landed = mover.rest >= SWAP_REST_FRAMES;
    }
    if (landed) finishDrop();
}

void CtrHome::Impl::finishDrop() {
    if (holding) {
        for (int i : {mover.gridIndex, mover.sourceIndex}) {
            if (i < 0 || i >= (int)grids.size()) continue;
            Grid& g = grids[i];
            g.skipSlot = g.hiddenSlot = g.hoverSlot = g.openSlot = -1;
        }
        if (mover.target >= 0 && mover.gridIndex < (int)grids.size()) {
            grids[mover.gridIndex].cursor = mover.target;
            grids[mover.gridIndex].onBar = false;
        }
    }
    holding = false;
    setState(StIdle);
    if (reloadAfterDrop) {
        reloadAfterDrop = false;
        self->reloadItems();
    }
}

// The held item (LncIconPickUp_00 / LncIconFolderPickUp_00) at the stylus; `scale` and
// `alpha` shrink and fade a title flying into a folder.
void CtrHome::Impl::drawLifted(Renderer& r, const HomeItem& it, float x, float y, float zoom, float scale, float alpha) {
    for (IconTemplate* t : {&pickUp, &pickUpFolder, &icon}) {
        if (!t->layout) continue;
        t->pose(zoom);
        t->layout->layout().opacity = alpha;
    }
    if (it.kind == HomeItemKind::Folder) {
        if (pickUpFolder.layout) stamp(r, pickUpFolder, x, y, scale);
        drawFolderFace(r, it, x, y, zoom, 1.08f * scale);
    } else if (pickUp.layout) {
        pickUp.layout->setVisible("P_Icon_00", false);
        if (themeFileLarge && themeFileSmall)
            pickUp.layout->replaceTexture("P_Btn_10", zoom >= SMALL_ICON_ZOOM ? themeFileSmall : themeFileLarge);
        stamp(r, pickUp, x, y, scale);
        Pane* ip = pickUp.layout->pane("P_Icon_00");
        drawTitleArt(r, it, x, y, ip ? ip->size[0] : zoomTable(ICON_SIZE, zoom) * 1.08f, scale);
    }
    for (IconTemplate* t : {&pickUp, &pickUpFolder, &icon}) if (t->layout) t->layout->layout().opacity = 255;
}

// ================================================================== touch (touch.ts)

bool CtrHome::Impl::touchUpdate() {
    if (touch.down) { touchBegin(touch.x, touch.y); return true; }
    if (touch.held && tKind != TNone) { touchHold(touch.x, touch.y); return true; }
    if (touch.up && tKind != TNone) { touchEnd(touch.x, touch.y); return true; }
    touchCoast();
    return scroller.active();
}

void CtrHome::Impl::touchBegin(float x, float y) {
    tHeld = 0;
    tDragged = false;
    // bottom buttons
    if (bbEnabled && !bbHasPending) {
        for (const auto& d : bottomButtons(bbShown)) {
            if (hitPane(buttons.get(), d.slot->touch, x, y)) {
                tKind = TBottom;
                tBottomSlot = d.slot == &kBottomFull ? 0 : d.slot == &kBottomRight ? 1 : 2;
                bbPressed = tBottomSlot;
                buttons->play("Select", namedGroups({d.slot->group}));
                sound("SE_CTR_COMMON_TOUCH");
                return;
            }
        }
    }
    if (inFolder() && hitPane(folder.get(), "Bounding_00", x, y)) {
        tKind = TBack;
        folder->play("Select", groupsOpt());
        return;
    }
    for (int b = 0; b < BAR_BUTTONS; b++) {
        if (!hitPane(base.get(), BAR_TOUCH[b], x, y)) continue;
        tKind = TBar;
        tBar = b;
        base->play("Select", namedGroups({BAR_GROUPS[b]}));
        return;
    }
    for (int side = 0; side < 2; side++) {
        if (!arrowShown[side]) continue;
        bool hit = false;
        for (int i = 0; i < 7 && !hit; i++) {
            char n[16];
            snprintf(n, sizeof(n), side == 0 ? "B_arwL_0%d" : "B_arwR_0%d", i);
            hit = hitPane(arrows.get(), n, x, y);
        }
        if (hit) {
            tKind = TArrow;
            tSide = side;
            arrows->play("Select", namedGroups({side == 0 ? "G_arwL_00" : "G_arwR_00"}));
            return;
        }
    }
    if (y > 88 || y < -106) { tKind = TNone; return; }
    Grid& g = grid();
    const bool coasting = scroller.active();
    scroller.press(x);
    const int slot = coasting ? -1 : g.slotAt(x, y, zoomTable(TOUCH_BOX, (float)g.zoom()));
    tKind = TGrid;
    tSlot = slot;
    tickScroll = g.scroll;
    if (slot >= 0 && g.item(slot)) sound("SE_CTR_HOME_ICON_TOUCH");
}

void CtrHome::Impl::touchHold(float x, float y) {
    tHeld++;
    if (tKind != TGrid) return;
    if (scroller.move(x)) { tDragged = true; return; }
    if (scroller.state == Scroller::Dragging) { tDragged = true; applyDelta(scroller.delta); return; }
    // touch.ts: a long press on an item of an arranged grid picks it up
    Grid& g = grid();
    if (tSlot >= 0 && !tDragged && tHeld >= LONG_PRESS_FRAMES && g.item(tSlot) && canGrab(g, *g.item(tSlot))) {
        scroller.stop();
        tKind = TNone;
        startGrab(tSlot, x, y);
    }
}

void CtrHome::Impl::touchEnd(float x, float y) {
    const TKind kind = tKind;
    tKind = TNone;
    switch (kind) {
        case TBottom: {
            std::vector<BottomButtonDef> defs = bottomButtons(bbShown);
            const BottomSlotPanes* sp = tBottomSlot == 0 ? &kBottomFull : tBottomSlot == 1 ? &kBottomRight : &kBottomLeft;
            bbPressed = -1;
            const bool inside = hitPane(buttons.get(), sp->touch, x, y);
            buttons->play("Decide", namedGroups({sp->group}));
            if (!inside) return;
            for (const auto& d : defs) {
                if (d.slot != sp) continue;
                if (!strcmp(d.action, "open")) activate(2);
                else if (!strcmp(d.action, "createFolder")) { sound(d.sound); createFolder(); }
                else if (!strcmp(d.action, "folderSettings")) {
                    sound(d.sound);
                    if (const HomeItem* it = grid().item(grid().cursor)) if (host) host->folderSettings(it->id);
                }
                else sound(d.sound);
            }
            return;
        }
        case TBack:
            folder->play("Decide", groupsOpt());
            if (hitPane(folder.get(), "Bounding_00", x, y)) closeFolder();
            return;
        case TBar:
            base->play("Decide", namedGroups({BAR_GROUPS[tBar]}));
            if (hitPane(base.get(), BAR_TOUCH[tBar], x, y)) tapBar(tBar);
            return;
        case TArrow:
            arrows->play("Decide", namedGroups({tSide == 0 ? "G_arwL_00" : "G_arwR_00"}));
            scrollPage(tSide == 1 ? 1 : -1);
            return;
        case TGrid: {
            const bool flung = scroller.release();
            if (flung || tDragged) return;
            if (tSlot >= 0) tapSlot(tSlot, x, y);
            return;
        }
        default:
            return;
    }
}

void CtrHome::Impl::tapSlot(int slot, float x, float y) {
    Grid& g = grid();
    if (g.slotAt(x, y, zoomTable(TOUCH_BOX, (float)g.zoom())) != slot) return;
    if (!g.onBar && g.cursor == slot) {
        if (g.item(slot)) {
            cursor->play("Decide", groupsOpt());
            activate(1);
        }
        return;
    }
    if (!g.item(slot)) sound("SE_CTR_HOME_TOUCH");
    moveCursorTo([&]() { g.onBar = false; g.barReturnColumn = -1; g.cursor = slot; }, false);
}

void CtrHome::Impl::tapBar(int button) {
    Grid& g = grid();
    if (button == BarZoomOut || button == BarZoomIn) { requestZoom(button == BarZoomIn ? 1 : -1, 1); return; }
    if (button == BarSettings || (g.onBar && g.barButton == button)) { decideBar(button); return; }
    moveCursorTo([&]() {
        if (!g.onBar) g.barReturnColumn = std::max(0, g.cursorColumn() - g.firstColumn);
        g.onBar = true;
        g.barButton = button;
    });
}

void CtrHome::Impl::touchCoast() {
    if (!scroller.active()) return;
    const bool stopped = scroller.update();
    if (scroller.delta != 0) applyDelta(scroller.delta);
    if (stopped) snap(scroller.velocity);
    if (!scroller.active() && snapColumn >= 0) {
        Grid& g = grid();
        g.firstColumn = snapColumn;
        g.scroll = g.scrollFor(snapColumn);
        snapColumn = -1;
        ensureCursorVisible();
    }
}

void CtrHome::Impl::snap(float velocity) {
    Grid& g = grid();
    const float exact = g.scroll / g.metrics.pitchX;
    int column = velocity < 0 ? (int)ceilf(exact - 1e-6f) : velocity > 0 ? (int)floorf(exact + 1e-6f) : (int)lroundf(exact);
    column = std::clamp(column, 0, g.maxFirstColumn());
    snapColumn = column;
    scroller.moveBy(g.scroll - g.scrollFor(column));
}

void CtrHome::Impl::applyDelta(float delta) {
    Grid& g = grid();
    const float next = g.scroll - delta;
    const float clamped = std::clamp(next, 0.0f, g.maxScroll());
    if (clamped != next && g.scroll != clamped) { sound("SE_CTR_COMMON_SCROLL_END"); bump(); }
    if (clamped != next && scroller.state == Scroller::Inertia) scroller.stop();
    if (clamped != next && scroller.state == Scroller::Idle) snap(0);
    g.scroll = clamped;
    g.firstColumn = std::clamp(g.columnAt(clamped), 0, g.maxFirstColumn());
    if (fabsf(clamped - tickScroll) > 20 && self->mFrame - tickFrame >= 4) {
        tickScroll = clamped;
        tickFrame = self->mFrame;
        sound("SE_CTR_COMMON_SCROLL_TEXT");
    }
}

// ================================================================== HUD (hud.ts)

void CtrHome::Impl::hudBuild() {
    PlayOptions manual = groupsOpt();
    manual.paused = true;
    if (Animator* wb = hud->play("WhiteBlack", manual)) { wb->seek(0); wb->apply(); hud->stop("WhiteBlack"); }
    hudNetMode = hud->play("NetMode", manual);
    hudBattery = hud->play("Bat", manual);
    hud->play("WalkCoin", groupsOpt());
    hud->setText("T_TimeC_00", ":");
    // steps and coins: nano has no pedometer; the step count shows 0 and the coins stay hidden
    hud->setText("T_Walk_00", "0");
    hud->setVisible("P_Coin_00", false);
    if (Animator* wc = hud->find("WalkCoin")) { wc->playing = false; wc->seek(0); }
    hudUpdate(true);
    hud->refresh();
    hud->play("SceneIn", groupsOpt())->onFinish = [this]() { hud->stop("SceneIn"); };
}

static std::string formatDate(Assets* A, const std::string& pattern, const struct tm& t) {
    auto label = [&](const std::string& n) { return A->messageText(n); };
    static const char* kWeek[] = {"sun", "mon", "tue", "wed", "thu", "fri", "sat"};
    std::string out;
    char buf[16];
    for (size_t i = 0; i < pattern.size(); i++) {
        if (pattern[i] != '%' || i + 1 >= pattern.size()) { out += pattern[i]; continue; }
        const char c = pattern[++i];
        const int hours = t.tm_hour, month = t.tm_mon + 1;
        switch (c) {
            case 'd': out += label("day_" + std::to_string(t.tm_mday)); break;
            case 'M': out += label("month_" + std::to_string(month)); break;
            case 'N': out += label("month_full_" + std::to_string(month)); break;
            case 'n': out += label("month_abbr_" + std::to_string(month)); break;
            case 'w': out += label(std::string("week_") + kWeek[t.tm_wday]); break;
            case 'Y': out += std::to_string(t.tm_year + 1900); break;
            case 'y': snprintf(buf, sizeof(buf), "%02d", (t.tm_year + 1900) % 100); out += buf; break;
            case 'I': snprintf(buf, sizeof(buf), "%02d", hours); out += buf; break;
            case 'H': snprintf(buf, sizeof(buf), "%2d", hours); out += buf; break;
            case 'h': snprintf(buf, sizeof(buf), "%2d", hours % 12); out += buf; break;
            case 'm': snprintf(buf, sizeof(buf), "%02d", t.tm_min); out += buf; break;
            case 't': out += label(hours < 12 ? "am" : "pm"); break;
            default: out += '%'; out += c; break;
        }
    }
    return out;
}

void CtrHome::Impl::hudUpdate(bool force) {
    // network (FUN_001ef654), every 60 frames
    if (force || netTimer-- <= 0) {
        netTimer = 0x3c;
        HomeStatus st = host ? host->status() : HomeStatus();
        const bool internet = st.wifiConnected;
        const int modeFrame = internet ? 0 : (st.wifiSignal >= 0 ? 3 : 4);
        const char* modeLabel = internet ? "lau_connect0" : (st.wifiSignal >= 0 ? "lau_connect3" : "lau_connect4");
        hud->setMessage("T_NetMode_00", modeLabel);
        if (hudNetMode) { hudNetMode->seek((float)modeFrame); hudNetMode->apply(); }
        const int antenna = internet ? std::clamp(st.wifiSignal, 0, 3) : (st.wifiSignal >= 0 ? 8 : 9);
        if (antenna != netFrame) {
            netFrame = antenna;
            PlayOptions o = groupsOpt();
            o.paused = true;
            if (!hudAntenna) hudAntenna = hud->play("NetAtn", o);
            if (hudAntenna) { hudAntenna->seek((float)antenna); hudAntenna->apply(); }
        }
        // battery (FUN_001ef538). The console blinks a full gauge while charging; nano blinks the
        // real level and the bar above it, so the gauge shows the charge while it rises.
        chargeBlink = st.adapter && st.charging;
        lowBlink = !st.adapter && st.batteryLevel <= 1;
        batFrame = st.batteryLevel <= 2 ? 0 : st.batteryLevel - 2;
        if (hudBattery) {
            if (st.adapter && !st.charging) hudBattery->seek(6);
            else hudBattery->seek((float)batFrame);
            hudBattery->apply();
        }
        hudPercent(st);
    }
    // clock (FUN_001ef30c)
    if (force || clockTimer <= 0) {
        time_t now = time(nullptr);
        struct tm t;
        localtime_r(&now, &t);
        if (force || t.tm_sec != shownSecond) {
            shownSecond = t.tm_sec;
            clockTimer = 0x3b;
            if (force || t.tm_min != shownMinute || t.tm_hour != shownHour) {
                shownMinute = t.tm_min;
                hud->setText("T_TimeR_00", formatDate(A, A->messageText("lau_minutes"), t));
                if (force || t.tm_hour != shownHour) {
                    shownHour = t.tm_hour;
                    hud->setText("T_TimeL_00", formatDate(A, A->messageText("lau_hours"), t));
                    if (force || t.tm_mday != shownDay || t.tm_mon != shownMonth) {
                        shownDay = t.tm_mday;
                        shownMonth = t.tm_mon;
                        hud->setText("T_Date_00", formatDate(A, A->messageText("lau_date"), t));
                    }
                }
            }
        }
    } else {
        clockTimer--;
    }
    // colon and battery blink on the seconds parity
    const bool odd = shownSecond % 2 == 1;
    hud->setVisible("T_TimeC_00", !odd);
    hud->setVisible("P_Bat_00", !(odd && lowBlink));
    if (chargeBlink && !lowBlink && hudBattery) {
        hudBattery->seek((float)(odd ? std::min(3, batFrame + 1) : batFrame));
        hudBattery->apply();
    }
}

void CtrHome::Impl::hudPercent(const HomeStatus& st) {
    static const char* const kShifted[4] = {"T_Date_00", "T_TimeL_00", "T_TimeC_00", "T_TimeR_00"};
    Pane* t = hud->pane("T_Coin_00");
    Pane* scene = hud->pane("N_Scene_00");
    Pane* bat = hud->pane("P_Bat_00");
    if (!t || !scene || !bat) return;
    if (!pctPlaced) {
        pctPlaced = true;
        for (int i = 0; i < 4; i++)
            if (Pane* p = hud->pane(kShifted[i])) dateX[i] = p->translate[0];
        // out of the coin icon (kept invisible) into the scene, right aligned against the gauge
        if (t->parent) {
            auto& sib = t->parent->children;
            sib.erase(std::remove(sib.begin(), sib.end(), t), sib.end());
        }
        t->parent = scene;
        scene->children.push_back(t);
        t->translate[0] = bat->translate[0] - t->size[0] - 1;
        t->translate[1] = bat->translate[1] - 1;
        t->textPosX = 2;
        t->visible = false;
        // the status font (Hud.bcfnt) has digits and the date's letters but no '%', and its cells
        // differ from the system font's (no glyph borrowing): the number is set in the layout's
        // system font (the "Internet" label's) at the same size
        Pane* sys = hud->pane("T_NetMode_00");
        if (sys && sys->font && t->font && !t->font->has('%')) {
            t->font = sys->font;
            t->fontResource = sys->fontResource;
        }
    }
    const bool show = st.showPercent && st.percent >= 0;
    // the date and the time move left by the number's width (its glyphs' advances at the pane's
    // font size) and a gap
    char buf[8] = "";
    if (show) snprintf(buf, sizeof(buf), "%d%%", std::min(100, st.percent));
    float shift = 0;
    if (show && t->font && t->font->width > 0) {
        float adv = 0;
        for (const char* c = buf; *c; c++)
            if (const Glyph* g = t->font->glyph((unsigned char)*c)) adv += g->charWidth;
        shift = adv * t->fontSize[0] / (float)t->font->width + 5.0f;
    } else if (show) {
        shift = 10.0f * (float)strlen(buf) + 5.0f;
    }
    if (show) hud->setText("T_Coin_00", buf);
    t->visible = show;
    if (shift != pctShift) {
        pctShift = shift;
        for (int i = 0; i < 4; i++)
            if (Pane* p = hud->pane(kShifted[i])) p->translate[0] = dateX[i] - shift;
    }
}

// ================================================================== draw

// A copy of a GL texture with its colour scaled to `grey` (alpha kept), rendered once into a
// new texture. Copies are shared per source texture.
// A derived copy of a GL texture, rendered once: colour scaled to `grey` (alpha kept), and for a
// cover (coverAspect > 0) fitted whole into a transparent square. Copies are kept per source.
TexturePtr CtrHome::Impl::tintedCopy(GLuint src, int w, int h, float grey, float coverAspect, bool flipY, bool onWhite,
                                      const float* rgb) {
    // grey just under 1: a full colour icon copied at the glyph size (not the folder letters' grey)
    int kind = (grey < 1 ? 1 : 0) | (coverAspect > 0 ? 2 : 0) | (flipY ? 4 : 0) | (onWhite ? 8 : 0) |
               (grey >= 0.99f && grey < 1 ? 16 : 0);
    float col[3] = {grey, grey, grey};
    if (rgb) {
        // the colour's bytes are part of the key: one copy per colour
        int packed = 0;
        for (int k = 0; k < 3; k++) {
            const int b = std::clamp((int)lroundf(rgb[k] * 255.0f), 0, 255);
            col[k] = b / 255.0f;
            packed = packed << 8 | b;
        }
        kind |= 32 | packed << 6;
    }
    auto cached = tinted.find({src, kind});
    if (cached != tinted.end()) return cached->second;
    static const char* vs = "attribute vec2 p; uniform vec2 s; uniform float f; varying vec2 uv;"
                            "void main(){ uv = p * 0.5 + 0.5; uv.y = mix(uv.y, 1.0 - uv.y, f);"
                            " gl_Position = vec4(p * s, 0.0, 1.0); }";
    static const char* fs = "#ifdef GL_FRAGMENT_PRECISION_HIGH\nprecision highp float;\n#else\nprecision mediump float;\n#endif\nvarying vec2 uv; uniform sampler2D t; uniform vec3 g; uniform float o;"
                            "void main(){ vec4 c = texture2D(t, uv); vec4 r = vec4(c.rgb * g, c.a);"
                            " gl_FragColor = mix(r, vec4(mix(vec3(1.0), r.rgb, c.a), 1.0), o); }";
    if (!tintProgram) {
        auto sh = [](GLenum type, const char* text) {
            GLuint s = glCreateShader(type);
            glShaderSource(s, 1, &text, nullptr);
            glCompileShader(s);
            return s;
        };
        GLuint v = sh(GL_VERTEX_SHADER, vs), f = sh(GL_FRAGMENT_SHADER, fs);
        tintProgram = glCreateProgram();
        glAttachShader(tintProgram, v);
        glAttachShader(tintProgram, f);
        glBindAttribLocation(tintProgram, 0, "p");
        glLinkProgram(tintProgram);
        glDeleteShader(v);
        glDeleteShader(f);
    }
    float sx = 1, sy = 1;
    if (coverAspect > 0) {
        w = h = 192;   // sharp at the largest grid zoom on a 4x panel
        if (coverAspect < 1) sx = coverAspect; else sy = 1 / coverAspect;
    }
    // a system glyph (the host's icons are 256 px, mipmapped): copied at that size with its own
    // mipmaps, so a folder's glyph is sharp at the largest zoom and calm at the smallest (the
    // reported 48 px made a soft, stretched copy)
    const bool glyph = grey < 1 && coverAspect <= 0;
    if (glyph) w = h = 256;
    std::vector<uint8_t> none((size_t)w * h * 4, 0);
    TexturePtr t = A->rgbaTexture(none.data(), w, h);
    GLint prevFbo = 0, vp[4];
    glGetIntegerv(GL_FRAMEBUFFER_BINDING, &prevFbo);
    glGetIntegerv(GL_VIEWPORT, vp);
    GLuint fbo;
    glGenFramebuffers(1, &fbo);
    glBindFramebuffer(GL_FRAMEBUFFER, fbo);
    glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, t->id, 0);
    glViewport(0, 0, w, h);
    glDisable(GL_BLEND);
    glDisable(GL_SCISSOR_TEST);
    glColorMask(GL_TRUE, GL_TRUE, GL_TRUE, GL_TRUE);
    glUseProgram(tintProgram);
    glUniform1i(glGetUniformLocation(tintProgram, "t"), 0);
    glUniform3f(glGetUniformLocation(tintProgram, "g"), col[0], col[1], col[2]);
    glUniform2f(glGetUniformLocation(tintProgram, "s"), sx, sy);
    glUniform1f(glGetUniformLocation(tintProgram, "f"), flipY ? 1.0f : 0.0f);
    glUniform1f(glGetUniformLocation(tintProgram, "o"), onWhite ? 1.0f : 0.0f);
    glActiveTexture(GL_TEXTURE0);
    glBindTexture(GL_TEXTURE_2D, src);
    // the copy keeps the source's row order (the FBO's row 0 receives uv.y = 0)
    const float quad[8] = {-1, -1, 1, -1, -1, 1, 1, 1};
    glBindBuffer(GL_ARRAY_BUFFER, 0);
    glEnableVertexAttribArray(0);
    glVertexAttribPointer(0, 2, GL_FLOAT, GL_FALSE, 0, quad);
    glDrawArrays(GL_TRIANGLE_STRIP, 0, 4);
    glDisableVertexAttribArray(0);
    glUseProgram(0);
    glBindFramebuffer(GL_FRAMEBUFFER, (GLuint)prevFbo);
    glDeleteFramebuffers(1, &fbo);
    glViewport(vp[0], vp[1], vp[2], vp[3]);
    if (glyph) {
        glBindTexture(GL_TEXTURE_2D, t->id);
        glGenerateMipmap(GL_TEXTURE_2D);
        t->mipmapped = true;
    }
    tinted[{src, kind}] = t;
    gTextureEpoch++;
    return t;
}

namespace {

// Coverage of a point by a convex polygon (clockwise or not), antialiased over one pixel.
float polyCover(const float (*v)[2], int n, float x, float y) {
    float inside = 1e9f;
    float area = 0;
    for (int i = 0; i < n; i++) {
        const float* a = v[i];
        const float* b = v[(i + 1) % n];
        area += a[0] * b[1] - b[0] * a[1];
    }
    const float sign = area > 0 ? 1.0f : -1.0f;
    for (int i = 0; i < n; i++) {
        const float* a = v[i];
        const float* b = v[(i + 1) % n];
        const float ex = b[0] - a[0], ey = b[1] - a[1];
        const float len = std::sqrt(ex * ex + ey * ey);
        if (len <= 0) continue;
        // signed distance to the edge's line, positive inside
        const float d = sign * (ex * (y - a[1]) - ey * (x - a[0])) / len;
        inside = std::min(inside, d);
    }
    return std::min(1.0f, std::max(0.0f, inside + 0.5f));
}

void blendPx(uint8_t* p, float r, float g, float b, float a) {
    if (a <= 0) return;
    const float da = p[3] / 255.0f;
    const float oa = a + da * (1 - a);
    if (oa <= 0) return;
    const float k = da * (1 - a);
    p[0] = (uint8_t)std::lround((r * a + p[0] * k) / oa);
    p[1] = (uint8_t)std::lround((g * a + p[1] * k) / oa);
    p[2] = (uint8_t)std::lround((b * a + p[2] * k) / oa);
    p[3] = (uint8_t)std::lround(oa * 255);
}

}  // namespace

// The generic box of a title without art: a white box in a three quarter view with the title's
// initial on its front (the grid icon), or the front alone filling the square (the top screen
// card, stored upside down like the banner models' textures). The initial is drawn from the
// folder letters' font through its distance field, so it stays sharp at any size.
TexturePtr CtrHome::Impl::boxTexture(const HomeItem& it, bool face, bool budgeted) {
    std::u32string init = folderInitial(it.name);
    const char32_t c = init.empty() ? U'?' : init[0];
    auto cached = boxArt.find({c, face});
    if (cached != boxArt.end()) return cached->second;
    // at most two new initials a frame: the rest come the next frames, the folder opens on time
    if (budgeted && boxBuilt >= 2) return nullptr;
    if (budgeted) boxBuilt++;

    constexpr int N = 256;
    // the box (or the card face) is the same for every title: drawn once, the initials go on copies
    static std::vector<uint8_t> base[2];
    std::vector<uint8_t>& b = base[face ? 1 : 0];
    // the front face and where the initial sits on it
    float fx0, fy0, fx1, fy1;
    const float s = 166, d = 46;
    if (face) {
        fx0 = fy0 = 0;
        fx1 = fy1 = N;
    } else {
        const float ox = (N - s - d) * 0.5f, oy = (N - s - d) * 0.5f + d;
        fx0 = ox; fy0 = oy; fx1 = ox + s; fy1 = oy + s;
    }
    if (b.empty()) {
        b.assign((size_t)N * N * 4, 0);
        auto bat = [&](int x, int y) { return &b[((size_t)y * N + x) * 4]; };
        if (face) {
            for (int y = 0; y < N; y++) {
                const float t = y / (float)(N - 1);
                const float v = 252 - 26 * t;   // white to light grey, top to bottom
                for (int x = 0; x < N; x++) blendPx(bat(x, y), v, v, v, 1);
            }
        } else {
            // front square, lid and right side in a cabinet view, centred in the square
            const float front[4][2] = {{fx0, fy0}, {fx1, fy0}, {fx1, fy1}, {fx0, fy1}};
            const float lid[4][2] = {{fx0, fy0}, {fx0 + d, fy0 - d}, {fx1 + d, fy0 - d}, {fx1, fy0}};
            const float side[4][2] = {{fx1, fy0}, {fx1 + d, fy0 - d}, {fx1 + d, fy1 - d}, {fx1, fy1}};
            // the outline: each face grown by the line's width, drawn first in grey
            const float line = 3;
            auto grow = [&](const float (*v)[2], float (*o)[2]) {
                float cx = 0, cy = 0;
                for (int i = 0; i < 4; i++) { cx += v[i][0] / 4; cy += v[i][1] / 4; }
                for (int i = 0; i < 4; i++) {
                    const float dx = v[i][0] - cx, dy = v[i][1] - cy;
                    const float len = std::sqrt(dx * dx + dy * dy);
                    o[i][0] = v[i][0] + dx / len * line;
                    o[i][1] = v[i][1] + dy / len * line;
                }
            };
            float gf[4][2], gl[4][2], gs[4][2];
            grow(front, gf);
            grow(lid, gl);
            grow(side, gs);
            // one sample at each texel centre: polyCover already anti-aliases the edge over a texel
            for (int y = 0; y < N; y++) {
                for (int x = 0; x < N; x++) {
                    const float sx = x + 0.5f, sy = y + 0.5f;
                    const float edge = std::max({polyCover(gf, 4, sx, sy), polyCover(gl, 4, sx, sy), polyCover(gs, 4, sx, sy)});
                    uint8_t* p = bat(x, y);
                    blendPx(p, 150, 150, 150, edge);
                    const float tf = (y - fy0) / (fy1 - fy0);
                    const float fv = 250 - 22 * std::min(1.0f, std::max(0.0f, tf));
                    blendPx(p, 255, 255, 255, polyCover(lid, 4, sx, sy));
                    blendPx(p, 206, 206, 206, polyCover(side, 4, sx, sy));
                    blendPx(p, fv, fv, fv, polyCover(front, 4, sx, sy));
                }
            }
        }
    }
    std::vector<uint8_t> px = b;
    auto at = [&](int x, int y) { return &px[((size_t)y * N + x) * 4]; };

    // the initial, grey like the folder letters (icons.ts LETTER_COLOR), centred on the front
    Pane* tp = letter ? letter->pane("T_Icon_00") : nullptr;
    std::shared_ptr<Font> font = tp ? tp->font : nullptr;
    const Glyph* g = font ? font->glyph(c) : nullptr;
    if (g) {
        const float boxH = fy1 - fy0;
        const float target = boxH * (face ? 0.56f : 0.6f);   // ink height of the letter
        const int scale = std::max(2, std::min(12, (int)std::ceil(target * 1.6f / std::max(1, font->cellHeight))));
        std::vector<uint8_t> sdf;
        if (font->cellSdf(g, scale, sdf)) {
            const int sw = (font->cellWidth + 2) * scale, sh = (font->cellHeight + 2) * scale;
            int x0 = sw, y0 = sh, x1 = -1, y1 = -1;
            for (int y = 0; y < sh; y++)
                for (int x = 0; x < sw; x++)
                    if (sdf[(size_t)y * sw + x] > 128) {
                        x0 = std::min(x0, x); x1 = std::max(x1, x);
                        y0 = std::min(y0, y); y1 = std::max(y1, y);
                    }
            if (x1 >= x0 && y1 >= y0) {
                const float inkW = x1 - x0 + 1, inkH = y1 - y0 + 1;
                // a wide glyph is held to the face's width too
                const float k = std::max(inkH / target, inkW / ((fx1 - fx0) * 0.72f));   // sdf px per image px
                const float icx = (x0 + x1 + 1) * 0.5f, icy = (y0 + y1 + 1) * 0.5f;
                const float cx = (fx0 + fx1) * 0.5f, cy = (fy0 + fy1) * 0.5f;
                auto sample = [&](float sx, float sy) {
                    sx -= 0.5f; sy -= 0.5f;
                    const int ix = (int)std::floor(sx), iy = (int)std::floor(sy);
                    const float ax = sx - ix, ay = sy - iy;
                    auto v = [&](int x, int y) -> float {
                        if (x < 0 || y < 0 || x >= sw || y >= sh) return 0;
                        return sdf[(size_t)y * sw + x];
                    };
                    return (v(ix, iy) * (1 - ax) + v(ix + 1, iy) * ax) * (1 - ay) +
                           (v(ix, iy + 1) * (1 - ax) + v(ix + 1, iy + 1) * ax) * ay;
                };
                const int bx0 = std::max(0, (int)std::floor(cx - (icx - x0 + 8) / k));
                const int bx1 = std::min(N - 1, (int)std::ceil(cx + (x1 - icx + 8) / k));
                const int by0 = std::max(0, (int)std::floor(cy - (icy - y0 + 8) / k));
                const int by1 = std::min(N - 1, (int)std::ceil(cy + (y1 - icy + 8) / k));
                for (int y = by0; y <= by1; y++) {
                    for (int x = bx0; x <= bx1; x++) {
                        const float v = sample(icx + (x + 0.5f - cx) * k, icy + (y + 0.5f - cy) * k);
                        // distance in sdf px, then in image px
                        const float dist = (v - 128) * Font::kSdfRange / 127 / k;
                        const float a = std::min(1.0f, std::max(0.0f, dist + 0.5f));
                        blendPx(at(x, y), 80, 80, 80, a);
                    }
                }
            }
        }
    }
    if (face) {
        // banner models take their textures upside down
        for (int y = 0; y < N / 2; y++) std::swap_ranges(at(0, y), at(0, y) + N * 4, at(0, N - 1 - y));
    }
    TexturePtr t = A->rgbaTexture(px.data(), N, N);
    glBindTexture(GL_TEXTURE_2D, t->id);
    glGenerateMipmap(GL_TEXTURE_2D);
    t->mipmapped = true;
    boxArt[{c, face}] = t;
    return t;
}

// A title's art, else (a game without an icon or cover) the generic box with its initial.
TexturePtr CtrHome::Impl::titleTexture(const HomeItem& it) {
    if (TexturePtr t = titleTextureOwn(it)) return t;
    return it.kind == HomeItemKind::Title ? boxTexture(it, false) : nullptr;
}

TexturePtr CtrHome::Impl::titleTextureOwn(const HomeItem& it) {
    if (host) {
        const uint64_t gen = host->artGeneration();
        if (gen != artGen) {
            artGen = gen;
            tinted.clear();
            tintedRetired.clear();
            boxArt.clear();
            hostTex.clear();   // a reused GL name carries other sampler state
            gTextureEpoch++;
        }
        int w = 0, h = 0;
        CtrHomeHost::IconArt art;
        if (GLuint gl = host->titleIconTexture(it, &w, &h, &art)) {
            if (art.mono) {
                // white glyph art reads as the folder letters' grey (icons.ts LETTER_COLOR): a
                // darkened copy (the icon material ignores vertex colour)
                // a title the host colours (a category's): its glyph in that colour
                float rgb[3];
                const bool coloured = it.kind == HomeItemKind::Title && host->itemTint(it, rgb);
                if (TexturePtr t = tintedCopy(gl, w, h, 80 / 255.0f, 0, false, false, coloured ? rgb : nullptr)) return t;
            } else if (art.coverAspect > 0) {
                if (TexturePtr t = tintedCopy(gl, w, h, 1.0f, art.coverAspect)) return t;
            }
            auto f = hostTex.find(gl);
            if (f != hostTex.end()) return f->second;
            auto t = std::make_shared<Texture>();   // the host owns the GL object
            t->id = gl;
            t->width = t->texWidth = w;
            t->height = t->texHeight = h;
            t->pixelArt = art.pixelArt;
            hostTex[gl] = t;
            return t;
        }
    }
    auto f = iconTex.find(it.id);
    if (f != iconTex.end()) return f->second;
    std::vector<uint8_t> px;
    int w = 0, h = 0;
    if (!host || !host->titleIcon(it, &px, &w, &h) || w <= 0 || h <= 0) return nullptr;
    TexturePtr t = A->rgbaTexture(px.data(), w, h);
    iconTex[it.id] = t;
    return t;
}

void CtrHome::Impl::stamp(Renderer& r, IconTemplate& t, float x, float y, float scale) {
    Pane* root_ = t.layout->layout().root;
    root_->translate[0] = roundf(x + t.offsetX * scale);
    root_->translate[1] = roundf(y);
    root_->scale[0] = root_->scale[1] = scale;
    t.layout->layout().calculate();
    r.drawLayout(t.layout->layout());
}

// Letter tile size and y offset per zoom (icons.ts, after LncIconFolderPickUp_00_Scale P_Icon_00).
static const float LETTER_SIZE[6] = {33, 33, 25.5f, 20.5f, 18.5f, 16.5f};
static const float LETTER_Y[6] = {-4, -4, -2, -1, -1, 1};

// A folder's face (icons.ts FolderLetter): the host's icon (a game system) or the outlined
// initial, at the slot's zoom, scaled with a lifted folder.
void CtrHome::Impl::drawFolderFace(Renderer& r, const HomeItem& it, float x, float y, float zoom, float scale) {
    drawFolderFaceSized(r, it, x, y + zoomTable(LETTER_Y, zoom) * scale, zoomTable(LETTER_SIZE, zoom) * scale);
}

// The face as a tile `size` px wide centred at (x, cy) (icons.ts FolderLetter.stampSized).
void CtrHome::Impl::drawFolderFaceSized(Renderer& r, const HomeItem& it, float x, float cy, float size) {
    if (TexturePtr tex = titleTexture(it)) {
        Pane* ip = icon.layout->pane("P_Icon_00");
        if (ip) {
            icon.layout->replaceTexture("P_Icon_00", tex);
            ip->size[0] = ip->size[1] = size * 1.15f;
            stamp(r, icon, x, cy, 1);
        }
        return;
    }
    if (!letter) return;
    const std::u32string init = folderInitial(it.name);
    if (init.empty()) return;
    Pane* tp = letter->pane("T_Icon_00");
    if (tp && (tp->runs.empty() || tp->runs[0].text != init)) letter->setTextU32("T_Icon_00", init);
    const float ls = size / 32;
    Pane* lr = letter->layout().root;
    lr->scale[0] = lr->scale[1] = ls;
    const float width = std::max(0.6f, ls * 1.4f);
    static const float OFF[8][2] = {{-1, 0}, {1, 0}, {0, -1}, {0, 1}, {-0.7f, -0.7f}, {0.7f, -0.7f}, {-0.7f, 0.7f}, {0.7f, 0.7f}};
    if (tp) tp->vertexColors = {Rgba{255, 255, 255, 255}, Rgba{255, 255, 255, 255}, Rgba{255, 255, 255, 255}, Rgba{255, 255, 255, 255}};
    for (auto& o : OFF) {
        lr->translate[0] = x + o[0] * width;
        lr->translate[1] = cy + o[1] * width;
        letter->layout().calculate();
        r.drawLayout(letter->layout());
    }
    if (tp) tp->vertexColors = {Rgba{80, 80, 80, 255}, Rgba{80, 80, 80, 255}, Rgba{80, 80, 80, 255}, Rgba{80, 80, 80, 255}};
    lr->translate[0] = x;
    lr->translate[1] = cy;
    letter->layout().calculate();
    r.drawLayout(letter->layout());
}

// A title's art on the icon template at `size` (icons.ts drawTitle).
void CtrHome::Impl::drawTitleArt(Renderer& r, const HomeItem& it, float x, float y, float size, float scale) {
    TexturePtr tex = titleTexture(it);
    Pane* ip = icon.layout->pane("P_Icon_00");
    if (!tex || !ip) return;
    icon.layout->replaceTexture("P_Icon_00", tex);
    ip->size[0] = ip->size[1] = size;
    stamp(r, icon, x, y, scale);
}

// Everything the cached bottom group reads besides its layouts' animations: the view, the
// theme and texture epochs, the background, and per drawn grid its view, markers, items and the
// texture each visible slot would show (art that arrives changes it).
uint64_t CtrHome::Impl::bottomSignature(const float* proj, int vx, int vy, int vw, int vh, float botW, uint64_t salt) {
    uint64_t h = 1469598103934665603ull;
    auto mix = [&](uint64_t v) { h ^= v; h *= 1099511628211ull; };
    auto mixf = [&](float f) { uint32_t u; memcpy(&u, &f, 4); mix(u); };
    auto mixs = [&](const std::string& t) { mix(std::hash<std::string>()(t)); };
    for (int i = 0; i < 16; i++) mixf(proj[i]);
    mix((uint64_t)vx); mix((uint64_t)vy); mix((uint64_t)vw); mix((uint64_t)vh);
    mixf(botW);
    mix(salt);
    mix(gTextureEpoch);
    mix(themeRev);
    mix(host ? host->artGeneration() : 0);
    mix(blankPicture);
    mix(bgBottom.shown());
    mix(bgBottom.image() ? (uint64_t)bgBottom.image()->id : 0);
    mix((uint64_t)bgBottom.phase);
    mix((uint64_t)state);
    mix(state == StFolderOpen || state == StFolderClose ? (uint64_t)stateFrame : 0);
    mix(closingFolder);
    auto grid_ = [&](const Grid& g) {
        mixf(g.scroll); mixf(g.zoomValue()); mix(g.blending); mixf(g.t);
        mix((uint64_t)g.slots); mix((uint64_t)g.zoom());
        mix((uint64_t)(g.hiddenSlot + 2)); mix((uint64_t)(g.skipSlot + 2));
        mix((uint64_t)(g.hoverSlot + 2)); mix((uint64_t)(g.openSlot + 2));
        for (int si : g.slotItem) mix((uint64_t)(si + 2));
        for (const HomeItem& it : g.items) { mixs(it.id); mixs(it.name); mix(it.folderFilled); }
        // the art of the slots on screen (the same culling as drawIcons)
        const float zoom = g.zoomValue();
        const float limit = 160 + sExtraWidth * 0.5f + zoomTable(CULL_PITCH, zoom) * 0.5f;
        for (int slot = 0; slot < g.slots; slot++) {
            const Point p = g.slotOnScreen(slot);
            if (p.x > limit || p.x < -limit) continue;
            if (const HomeItem* it = g.item(slot)) {
                TexturePtr t = titleTexture(*it);
                mix(t ? (uint64_t)t->id : 0);
            }
        }
    };
    grid_(root());
    if (inFolder()) grid_(grid());
    if (closingFolder) grid_(closing);
    return h;
}

void CtrHome::Impl::drawIcons(Renderer& r, const Grid& g, const Box* clip) {
    const float zoom = g.zoomValue();
    for (IconTemplate* t : {&blank, &tile, &folderIcon, &icon, &pickUpBlank, &hoverGlow, &folderOpenT, &folderOpenB})
        if (t->layout) t->pose(zoom);
    const float limit = 160 + sExtraWidth * 0.5f + zoomTable(CULL_PITCH, zoom) * 0.5f;
    if (clip) r.setScissor(true, clip->left, clip->bottom, clip->right - clip->left, clip->top - clip->bottom);
    // moving.ts markers: the glow under a slot the held icon would land on
    if (g.hoverSlot >= 0 && hoverGlow.layout) {
        Point p = g.slotOnScreen(g.hoverSlot);
        stamp(r, hoverGlow, p.x, p.y);
    }
    for (int slot = 0; slot < g.slots; slot++) {
        Point p = g.slotOnScreen(slot);
        if (p.x > limit || p.x < -limit || slot == g.skipSlot) continue;
        const HomeItem* it = g.item(slot);
        if (slot == g.hiddenSlot && pickUpBlank.layout) { stamp(r, pickUpBlank, p.x, p.y); continue; }   // the hole
        if (!it) { stamp(r, blank, p.x, p.y); continue; }
        if (it->kind == HomeItemKind::Folder) {
            if (slot == g.openSlot && folderOpenT.layout) {
                // a folder about to take the held title opens (LncIconFolderInT_00)
                folderOpenT.layout->setVisible("P_FolderHad_01", it->folderFilled);
                stamp(r, folderOpenT, p.x, p.y);
                // the initial sits where LncIconFolderInB_00 holds its tile
                Pane* root_ = folderOpenB.layout ? folderOpenB.layout->pane("N_FolderRoot_00") : nullptr;
                Pane* tile_ = folderOpenB.layout ? folderOpenB.layout->pane("P_Icon_00") : nullptr;
                if (root_ && tile_) drawFolderFaceSized(r, *it, p.x, p.y + root_->translate[1] + tile_->translate[1], tile_->size[0]);
                continue;
            }
            folderIcon.layout->setVisible("N_Had_02", it->folderFilled);
            stamp(r, folderIcon, p.x, p.y);
            drawFolderFace(r, *it, p.x, p.y, zoom, 1);
            continue;
        }
        // the theme's icon frame image for the art size shown (icons.ts frameImage)
        if (themeFileLarge && themeFileSmall) {
            const int z = g.blending ? (g.t < 0.5f ? g.from.zoom : g.to.zoom) : g.zoom();
            tile.layout->replaceTexture("P_Btn_10", z >= SMALL_ICON_ZOOM ? themeFileSmall : themeFileLarge);
        }
        stamp(r, tile, p.x, p.y);
        drawTitleArt(r, *it, p.x, p.y, zoomTable(ICON_SIZE, zoom), 1);
    }
    if (clip) r.setScissor(false);
}

// ================================================================== CtrHome

CtrHome::CtrHome(std::string assetRoot, CtrHomeHost* host) {
    mAssets = std::make_unique<Assets>(std::move(assetRoot));
    mTheme = std::make_unique<ThemeManager>(*mAssets);   // before any layout: it themes them as they are made
    mImpl = std::make_unique<Impl>();
    mImpl->theme = mTheme.get();
    mImpl->self = this;
    mImpl->A = mAssets.get();
    mImpl->host = host;
}

CtrHome::~CtrHome() {
    mImpl.reset();
    mTheme.reset();   // after the home's layouts, before the assets it hooks
    mAssets.reset();
}

bool CtrHome::load() {
    if (mLoaded) return true;
    if (!mAssets->ok()) return false;
    mLoaded = mImpl->load();
    return mLoaded;
}

void CtrHome::releaseGl() {
    gTextureEpoch++;
    if (mImpl) mImpl->myMenu.reset();   // its layouts go before the assets' GL objects
    if (mImpl) mImpl->appEnd();
    if (mImpl && mImpl->remapProgram) { glDeleteProgram(mImpl->remapProgram); mImpl->remapProgram = 0; }
    if (mImpl) mAssets->renderer().releaseCache(mImpl->bottomCache);
    if (mImpl) mImpl->iconTex.clear();
    if (mImpl) mImpl->tinted.clear();
    if (mImpl) mImpl->tintedRetired.clear();
    if (mImpl) mImpl->hostTex.clear();
    if (mImpl && mImpl->tintProgram) { glDeleteProgram(mImpl->tintProgram); mImpl->tintProgram = 0; }
    if (mImpl && mImpl->top3d) mImpl->top3d->releaseGl();
    if (mAssets) mAssets->releaseGl();
}

void CtrHome::parkGl() {
    if (!mImpl) return;
    Impl& I = *mImpl;
    gTextureEpoch++;
    mAssets->renderer().releaseCache(I.bottomCache);
    I.bottomSig = 0;
    I.iconTex.clear();
    I.tinted.clear();
    I.tintedRetired.clear();
    I.hostTex.clear();
    I.boxArt.clear();
    if (I.tintProgram) { glDeleteProgram(I.tintProgram); I.tintProgram = 0; }
    if (I.remapProgram) { glDeleteProgram(I.remapProgram); I.remapProgram = 0; }
    // the resumed title's screens were only held until the host showed it
    if (I.appStep == Impl::AppGone) I.appEnd();
    if (I.top3d) I.top3d->parkGl();
    mAssets->renderer().releaseGl();   // programs come back from the binary cache
    for (const auto& f : mAssets->loadedFonts()) f->releaseGl();   // glyphs from the glyph files
}

void CtrHome::setLanguage(const std::string& language) { mAssets->setLanguage(language); }

void CtrHome::setTouch(const CtrTouch& t) {
    // presses and releases between two ticks are latched so a quick tap still counts
    if (t.held && !mTouchNext.held) mTouchDownLatched = true;
    if (!t.held && mTouchNext.held) mTouchUpLatched = true;
    mTouchNext.held = t.held;
    if (t.held) { mTouchNext.x = t.x; mTouchNext.y = t.y; }
}

void CtrHome::tick() {
    Impl& I = *mImpl;
    I.held = mHeldNext;
    I.homeEvent = mHomeNext;
    mHomeNext = false;
    CtrTouch t;
    t.x = mTouchNext.x;
    t.y = mTouchNext.y;
    const bool wasHeld = mTouchWasHeld;   // (I.touch is emptied while My Menu takes the touch)
    t.down = mTouchDownLatched || (mTouchNext.held && !wasHeld);
    t.up = mTouchUpLatched || (!mTouchNext.held && wasHeld);
    t.held = mTouchNext.held || (t.down && !t.up);
    mTouchDownLatched = mTouchUpLatched = false;
    I.touch = t;
    mTouchWasHeld = t.held;
    mFrame++;
    if (I.myMenuOn()) {
        // the scene takes the buttons, the touch and HOME; the launcher underneath keeps running
        MyMenuInput in;
        in.held = I.held;
        in.home = I.homeEvent;
        in.touchDown = t.down;
        in.touchHeld = t.held;
        in.touchUp = t.up;
        in.x = t.x;
        in.y = t.y;
        I.myMenu->tick(in);
        I.held = 0;
        I.homeEvent = false;
        I.touch = CtrTouch();
        I.touch.x = t.x;
        I.touch.y = t.y;
    } else if (I.myMenuReturn > 0) {
        I.myMenuReturn--;
    }
    I.tick();
    if (onTick) onTick();
}

bool CtrHome::myMenuActive() const { return mLoaded && mImpl->myMenuOn(); }

void CtrHome::layoutState(int* zoom, int* cursor) const {
    Impl& I = *mImpl;
    *zoom = mLoaded ? I.root().zoom() : 0;
    *cursor = mLoaded ? I.root().cursor : 0;
}

void CtrHome::applyLayoutState(int zoom, int cursor) {
    if (!mLoaded) return;
    Impl& I = *mImpl;
    while (I.grids.size() > 1) I.grids.pop_back();
    Grid& g = I.root();
    I.loadGrid(g);
    g.setZoom(zoom);
    g.cursor = std::max(0, std::min(cursor, (int)g.slotItem.size() - 1));
    g.clampView();
    I.iconTex.clear();
    gTextureEpoch++;
}

// The thumbnails Save/Load Layout shows and stores (mymenu/thumbnail.ts captureLayout): the top
// screen's wall without banners or the status bar, the bottom screen's sheet, icons, open folder
// and name balloon without the cursor, scroll bar, arrows or buttons. Two texels a layout unit.
bool CtrHome::openMyMenu() {
    if (!mLoaded || !mImpl->host) return false;
    Impl& I = *mImpl;
    MyMenuStore* store = I.host->myMenuStore();
    if (!store || I.myMenuOn()) return false;
    if (!I.myMenu) {
        I.myMenu = std::make_unique<CtrMyMenu>(*mAssets, [&I](const char* n) { I.sound(n); }, store);
        I.myMenu->onClose = [&I](bool loaded) { I.myMenuReturn = loaded ? 0 : 30; };
    }
    constexpr int K = 2;
    auto capture = [&](int lw, bool top) -> TexturePtr {
        const int w = lw * K, h = 240 * K;
        std::vector<uint8_t> none((size_t)w * h * 4, 0);
        TexturePtr t = mAssets->rgbaTexture(none.data(), w, h);
        GLint prevFbo = 0, vp[4];
        glGetIntegerv(GL_FRAMEBUFFER_BINDING, &prevFbo);
        glGetIntegerv(GL_VIEWPORT, vp);
        GLuint fbo = 0;
        glGenFramebuffers(1, &fbo);
        glBindFramebuffer(GL_FRAMEBUFFER, fbo);
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, t->id, 0);
        glViewport(0, 0, w, h);
        glDisable(GL_SCISSOR_TEST);
        glClearColor(top ? 232 / 255.0f : 0, top ? 232 / 255.0f : 0, top ? 238 / 255.0f : 0, 1);
        glClear(GL_COLOR_BUFFER_BIT);
        // rows top first, as the layouts' textures are stored (a GL target fills bottom up)
        const float proj[16] = {2.0f / lw, 0, 0, 0, 0, -2.0f / 240, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1};
        Renderer& r = mAssets->renderer();
        if (top) {
            if (I.top3d) {
                // (the underlay drawTop set for the theme's top image stays in place)
                I.top3d->setBannerHidden(true);
                I.top3d->draw(proj, 0, 0, w, h, (float)lw, 240);
                I.top3d->setBannerHidden(I.topBarsHidden);
            }
        } else {
            r.setViewport(0, 0, w, h);
            r.beginWith(proj);
            if (I.bgBottom.shown()) {
                I.base->layout().calculate();
                I.bgBottom.lay->layout().root->parent = I.base->pane("NN_LncBgSlide_00");
                I.drawInstance(r, I.bgBottom.lay.get());
            }
            I.drawInstance(r, I.base.get());
            I.drawInstance(r, I.palette.get());
            I.drawIcons(r, I.root(), nullptr);
            if (I.inFolder()) {
                I.drawInstance(r, I.folder.get());
                Pane* sheet = I.folder->pane("W_Plt_00");
                Box clip = sheet ? paneBox(sheet) : Box{-160, 160, 120, -120};
                I.drawIcons(r, I.grid(), &clip);
            }
            I.drawInstance(r, I.balloon.get());
            r.end();
        }
        // a screen is opaque: the blended passes leave coverage in the alpha, which would make
        // the thumbnail translucent where the scene draws it
        glColorMask(GL_FALSE, GL_FALSE, GL_FALSE, GL_TRUE);
        glClearColor(0, 0, 0, 1);
        glClear(GL_COLOR_BUFFER_BIT);
        glColorMask(GL_TRUE, GL_TRUE, GL_TRUE, GL_TRUE);
        glBindFramebuffer(GL_FRAMEBUFFER, (GLuint)prevFbo);
        glDeleteFramebuffers(1, &fbo);
        glViewport(vp[0], vp[1], vp[2], vp[3]);
        return t;
    };
    TexturePtr top = capture(400, true), bottom = capture(320, false);
    return I.myMenu->open(top, bottom);
}

void CtrHome::step(int frames) {
    for (int i = 0; i < frames; i++) tick();
    if (mLoaded) mImpl->updateBackgrounds();
}

void CtrHome::update(int64_t nowNs) {
    if (!mLoaded) return;
    if (mLastNs == 0) mLastNs = nowNs;
    mAccum += (nowNs - mLastNs) / 1e9 * 60.0;
    mLastNs = nowNs;
    int n = (int)mAccum;
    mAccum -= n;
    if (n > 8) n = 8;   // at most 8 ticks per drawn frame (runtime.ts)
    for (int i = 0; i < n; i++) tick();
    mImpl->updateBackgrounds();
}

bool CtrHome::busy() const { return true; }

void CtrHome::projection(float out[16], float lw, float lh, float rx, float ry, float rw, float rh, float W, float H,
                         const float rot[4]) {
    // layout (x, y) -> logical pixel -> NDC -> rotated clip
    const float sx = rw / lw, sy = rh / lh;
    const float cx = rx + rw / 2, cy = ry + rh / 2;
    // ndc = (px / W * 2 - 1, 1 - py / H * 2)
    const float ax = sx * 2 / W, bx = cx * 2 / W - 1;
    const float ay = sy * 2 / H, by = 1 - cy * 2 / H;   // py = cy - y * sy -> ndc_y = by + y * ay
    float m[16] = {0};
    m[0] = ax; m[12] = bx;
    m[5] = ay; m[13] = by;
    m[10] = 0; m[15] = 1;
    float r[16] = {0};
    r[0] = rot[0]; r[1] = rot[1]; r[4] = rot[2]; r[5] = rot[3]; r[10] = 1; r[15] = 1;
    for (int c = 0; c < 4; c++)
        for (int row = 0; row < 4; row++) {
            float s = 0;
            for (int k = 0; k < 4; k++) s += r[k * 4 + row] * m[c * 4 + k];
            out[c * 4 + row] = s;
        }
}

void (*CtrHome::perfMark)(const char*) = nullptr;

void CtrHome::setTopBannerHidden(bool hidden) {
    if (!mLoaded) return;
    if (mImpl->top3d) mImpl->top3d->setBannerHidden(hidden);
    // the camera / screenshot bars leave with it (hud/topbars.ts setHidden on settings:open)
    if (mImpl->top && mImpl->topBarsHidden != hidden) {
        mImpl->topBarsHidden = hidden;
        mImpl->top->setVisible("N_Btm_00", !hidden);
    }
}

bool CtrHome::topCovered() const { return mLoaded && mImpl->top3d && !getenv("CTR_NO3D"); }

void CtrHome::setTopHeight(float h) { mTopH = std::max(240.0f, h); }

void CtrHome::drawTop(const float proj[16], int vpX, int vpY, int vpW, int vpH) {
    if (!mLoaded) return;
    Impl& I = *mImpl;
    Renderer& r = mAssets->renderer();
    for (LayoutInstance* l : {I.top.get(), I.hud.get()}) {
        if (!l) continue;
        l->layout().adaptWidth(mTopW, Layout::Adapt::Split);
        l->layout().adaptHeight(mTopH, Layout::Adapt::Edge);   // status bar up, hints down
    }
    if (I.fadeTop) {
        I.fadeTop->layout().adaptWidth(mTopW, Layout::Adapt::None);
        I.fadeTop->layout().adaptHeight(mTopH, Layout::Adapt::None);
    }
    if (I.top3d) {
        // LncBgSlide_U_00 behind the 3D pass (background.ts TOP_PRIORITY): drawn into the scene's
        // target under the banners
        I.top3d->underlay = [this, &I, &r](int w, int h, const float lp[16]) {
            LayoutInstance* l = I.bgTop.lay.get();
            if (!l) return;
            l->layout().adaptWidth(mTopW, Layout::Adapt::Split);
            l->layout().adaptHeight(mTopH, Layout::Adapt::Edge);
            r.setViewport(0, 0, w, h);
            r.beginWith(lp);
            I.drawInstance(r, l);
            r.end();
        };
    }
    if (perfMark) perfMark("top3d");
    // Save/Load Layout: over the home (which it covers once shown), its chrome hidden until it
    // left (the banner, the camera / screenshot bars, the status bar until the scene entered)
    const bool mm = I.myMenuOn();
    const bool covered = mm && I.myMenu->coversTop();
    const bool noChrome = I.chromeHidden();
    if (I.top3d && !getenv("CTR_NO3D") && !covered) {
        if (noChrome) I.top3d->setBannerHidden(true);
        I.top3d->draw(proj, vpX, vpY, vpW, vpH, mTopW, mTopH);
        if (noChrome) I.top3d->setBannerHidden(I.topBarsHidden);
    }
    if (perfMark) perfMark("topLayouts");
    r.setViewport(vpX, vpY, vpW, vpH);
    r.beginWith(proj);
    r.setClip(-mTopW / 2, -mTopH / 2, mTopW, mTopH);
    if (!covered && !noChrome) I.drawInstance(r, I.top.get());
    if (mm) {
        I.myMenu->setScreens(mTopW, mTopH, mBotW);
        I.myMenu->drawTop(r);
        if (I.myMenu->entered()) I.drawInstance(r, I.hud.get());
    } else if (I.myMenuReturn <= 0) {
        I.drawInstance(r, I.hud.get());
    }
    // the Close confirmation dims both screens; the closing notice only the lower one
    if (I.appDialog && I.appDialog->active() && I.appDialogKind == 1) I.appDialog->drawTop(r);
    if (overlay) overlay(r, true);
    if (I.fadeActive || I.state == StAppStart || I.state == StAppRunning) I.drawInstance(r, I.fadeTop.get());
    r.end();
}

void CtrHome::drawBottom(const float proj[16], int vpX, int vpY, int vpW, int vpH) {
    if (!mLoaded) return;
    Impl& I = *mImpl;
    I.boxBuilt = 0;
    Renderer& r = mAssets->renderer();
    r.setViewport(vpX, vpY, vpW, vpH);
    for (LayoutInstance* l : {I.base.get(), I.arrows.get()}) if (l) l->layout().adaptWidth(mBotW, Layout::Adapt::Edge);
    for (LayoutInstance* l : {I.palette.get(), I.folder.get(), I.buttons.get(), I.balloon.get(), I.fadeBottom.get()})
        if (l) l->layout().adaptWidth(mBotW, Layout::Adapt::None);
    r.beginWith(proj);
    r.setClip(-mBotW / 2, -120, mBotW, 240);
    if (I.appCovers() && I.pause && !I.capturing) {
        // the pause fade covers the lower screen (P_BG_00 under the title's screen and the menu)
        I.pause->layout().adaptWidth(mBotW, Layout::Adapt::None);
        I.drawInstance(r, I.pause.get());
        if (overlay) overlay(r, false);
        r.end();
        return;
    }
    const bool mm = I.myMenuOn();
    if (mm && I.myMenu->coversBottom()) {
        // Save/Load Layout paints the whole screen: the home is not drawn under it
        I.myMenu->drawBottom(r);
        if (overlay) overlay(r, false);
        r.end();
        return;
    }
    const bool noChrome = I.chromeHidden();
    // The background, sheets and icons change only when the grid moves: an exact cache
    // (Renderer::endGroup) copies them while they hold still; the cursor and chrome draw on top.
    // a wallpaper that moves by itself (video, animation) changes the cached bottom every frame
    const uint64_t salt = I.bgBottom.shown() && I.bgBottom.host && I.bgBottom.hostDynamic ? ++I.bgFrame : 0;
    r.setGroupSalt(salt);
    // When nothing the group draws can have changed (its inputs' signature is the same and none of
    // its layouts animates), the cached pixels are used without recording the group at all; every
    // 15th frame records anyway, so the exact check above is never far behind.
    const uint64_t sig = I.bottomSignature(proj, vpX, vpY, vpW, vpH, mBotW, salt);
    bool animating = I.makerSlot >= 0;
    for (LayoutInstance* l : {I.base.get(), I.palette.get(), I.folder.get(), I.bgBottom.lay.get()})
        animating = animating || (l && l->anyPlaying());
    // (a copy of the menu for the pause fade is drawn whole, outside the cache)
    const bool cacheOn = !getenv("CTR_NO_CACHE") && !I.capturing;
    const bool reuse = cacheOn && !animating && sig == I.bottomSig && ++I.bottomReuseRun < 15 &&
                       r.reuseGroup(I.bottomCache);
    if (!reuse) {
        I.bottomReuseRun = 0;
        I.bottomSig = cacheOn ? sig : 0;
        r.beginGroup();
        if (I.bgBottom.shown()) {
            // hung under the base's NN_LncBgSlide_00, its first node: drawn before the base
            I.bgBottom.lay->layout().adaptWidth(mBotW, Layout::Adapt::Edge);
            I.base->layout().calculate();
            I.bgBottom.lay->layout().root->parent = I.base->pane("NN_LncBgSlide_00");
            I.drawInstance(r, I.bgBottom.lay.get());
        }
        I.drawInstance(r, I.base.get());
        I.drawInstance(r, I.palette.get());
        I.drawIcons(r, I.root(), nullptr);
        if (I.makerSlot >= 0 && I.maker) {
            // FolderMaker.draw: the clip over its slot, the initial scaled with N_FolderRoot_01
            const Grid& rg = I.root();
            const Point p = rg.slotOnScreen(I.makerSlot);
            Pane* mr = I.maker->layout().root;
            mr->translate[0] = p.x;
            mr->translate[1] = p.y;
            // P_Icon_00 is the tile the console renders the initial into (IconDmy until then):
            // the initial is stamped over it here
            I.maker->setVisible("P_Icon_00", false);
            I.maker->layout().calculate();
            r.drawLayout(I.maker->layout());
            Pane* fr = I.maker->pane("N_FolderRoot_01");
            I.drawFolderFace(r, I.makerItem, p.x, p.y, rg.zoomValue(), fr ? fr->scale[0] : 1.0f);
        }
        if (I.inFolder() || I.closingFolder) {
            I.drawInstance(r, I.folder.get());
            const Grid& fg = I.inFolder() ? I.grid() : I.closing;
            // a nested folder closing collapses the sheet with no icons in it (the parent's open after)
            const bool showIcons = I.inFolder() && !(I.state == StFolderOpen && I.stateFrame < 6) &&
                                   !(I.state == StFolderClose && I.closingFolder);
            if (showIcons) {
                Pane* sheet = I.folder->pane("W_Plt_00");
                Box clip = sheet ? paneBox(sheet) : Box{-160, 160, 120, -120};
                I.drawIcons(r, fg, &clip);
            }
        }
        static const float kBlack[3] = {0, 0, 0};   // the bottom letterbox (NanoMenuCtr ctrDrawScreen)
        if (cacheOn) r.endGroup(I.bottomCache, kBlack);
    }
    if (perfMark) { r.end(); perfMark("b.chrome"); r.beginWith(proj); r.setClip(-mBotW / 2, -120, mBotW, 240); }
    if (!I.sleepOn.empty() && I.sleepIcon.layout) {
        // sleepicon.ts: over the grid icons, under the cursor trail, on the suspended title's slot
        Grid& g = I.grid();
        for (int slot = 0; slot < g.slots; slot++) {
            const HomeItem* it = g.item(slot);
            if (!it || std::find(I.appIds.begin(), I.appIds.end(), it->id) == I.appIds.end()) continue;
            const Point p = g.slotOnScreen(slot);
            if (fabsf(p.x) > mBotW / 2 + 40) continue;
            I.sleepIcon.pose(g.zoomValue());
            I.stamp(r, I.sleepIcon, p.x, p.y);
        }
    }
    if (!noChrome) {
        for (Trail& t : I.trail) if (t.active) I.drawInstance(r, t.layout.get());
        I.drawInstance(r, I.cursor.get());
        if (I.indFade > 0) I.drawInstance(r, I.indicatorL.get());
        I.drawInstance(r, I.arrows.get());
        I.drawInstance(r, I.balloon.get());
        if (I.appBarShown || I.appStep == Impl::AppClosing) {
            if (I.appBar) {
                I.appBar->layout().adaptWidth(mBotW, Layout::Adapt::None);
                I.drawInstance(r, I.appBar.get());
            }
        } else {
            I.drawInstance(r, I.buttons.get());
        }
    }
    if (I.holding && I.mover.gridIndex < (int)I.grids.size()) {
        // moving.ts IconMover: the held icon above everything on the bottom screen
        const Grid& hg = I.grids[I.mover.gridIndex];
        const float zoom = hg.zoomValue();
        if (I.mover.hasSwapped && I.mover.target >= 0 && I.state == StMoveIcon)
            I.drawLifted(r, I.mover.swapped, I.mover.swapAt.x, I.mover.swapAt.y, zoom, 1, 255);
        const float lift = std::trunc(zoomTable(TOUCH_BOX, zoom) * 0.2f);
        const float y = I.state == StGrab ? std::min(HELD_TOP, I.mover.at.y + lift) : I.mover.at.y;
        I.drawLifted(r, I.mover.item, I.mover.at.x, y, zoom, I.mover.scale, I.mover.alpha);
    }
    if (mm) I.myMenu->drawBottom(r);
    if (I.appDialog && I.appDialog->active() && !I.capturing) I.appDialog->drawBottom(r);
    if (overlay && !I.capturing) overlay(r, false);
    if (I.fadeActive || I.state == StAppStart || I.state == StAppRunning) I.drawInstance(r, I.fadeBottom.get());
    r.end();
}

void CtrHome::setScreenWidths(float top, float bottom) {
    top = std::max(400.0f, top);
    bottom = std::max(320.0f, bottom);
    if (top == mTopW && bottom == mBotW) return;
    mTopW = top;
    mBotW = bottom;
    sExtraWidth = bottom - 320;
    if (!mLoaded) return;
    for (Grid& g : mImpl->grids) {
        g.metrics = metricsFor(g.zoom(), g.isFolder());
        g.blending = false;
        g.clampView();
    }
    mImpl->updateChrome();
}

void CtrHome::suspendApp(const std::vector<std::string>& ids, unsigned top, int topW, int topH, unsigned bottom, int botW,
                         int botH) {
    if (!mLoaded) {
        if (top) { GLuint t = top; glDeleteTextures(1, &t); }
        if (bottom) { GLuint t = bottom; glDeleteTextures(1, &t); }
        return;
    }
    mImpl->appSuspend(ids, top, topW, topH, bottom, botW, botH);
}

bool CtrHome::appSuspended() const {
    return mLoaded && mImpl->appStep != Impl::AppNone && mImpl->appStep != Impl::AppGone;
}

bool CtrHome::appResuming() const { return mLoaded && mImpl->appStep == Impl::AppResuming; }

void CtrHome::resumeSuspended() {
    if (mLoaded) mImpl->appResume();
}

void CtrHome::resumeFromApp() {
    if (!mLoaded) return;
    Impl& I = *mImpl;
    // the title the home faded back into has ended (or the home is shown again without it)
    if (I.appStep == Impl::AppGone) I.appEnd();
    if (I.state == StAppStart || I.state == StAppRunning) {
        I.homeEvent = true;
        I.setState(StAppRunning);
        I.mainUpdate();
    }
}

// ------------------------------------------------------------------ background images

namespace {
// background.ts: a scrolling image is 1008 wide; P_Bg_00 shows 412 (top, the 3D margin) or 320
const float kScrollWidth = 1008;
const float kPhaseLength = 82;   // animated / bounce frames change every 82 px of grid scroll

QuadUv uvQuad(float u0, float u1) { return QuadUv{Vec2{u0, 0}, Vec2{u1, 0}, Vec2{u0, 1}, Vec2{u1, 1}}; }
float cRem(float v, float size) { return v - std::trunc(v / size) * size; }

// A theme image as a texture of exactly the shown part (theme.py _crop), rows top first.
TexturePtr themeTexture(Assets& A, const ThemeImage& img) {
    if (img.empty() || !img.w || !img.h) return nullptr;
    std::vector<uint8_t> px((size_t)img.w * img.h * 4);
    // the decoder gives the CGFX row order (the stored first row at the bottom)
    for (uint32_t y = 0; y < img.h; y++) {
        const uint8_t* src = img.rgba.data() + ((size_t)(img.texH - 1 - y) * img.texW) * 4;
        memcpy(px.data() + (size_t)y * img.w * 4, src, (size_t)img.w * 4);
    }
    return A.rgbaTexture(px.data(), (int)img.w, (int)img.h);
}
}  // namespace

// FUN_00267500 (background.ts Slide.follow): texture coordinates of P_Bg_00 for the root grid's
// scroll position. Set 0 is the BgSplit mask, sets 1 and 2 the image.
void CtrHome::Impl::followBackground(Background& b, bool top, bool follow) {
    Pane* p = b.lay ? b.lay->pane("P_Bg_00") : nullptr;
    if (!p) return;
    const TexturePtr& tex = b.image();
    if (tex != b.bound) {
        b.bound = tex;
        b.phase = -1;
        if (tex) {
            b.lay->replaceTexture("P_Bg_00", tex, 1);
            b.lay->replaceTexture("P_Bg_00", tex, 2);
        }
    }
    if (!tex) return;
    if (p->texCoords.size() < 3) p->texCoords.resize(3);
    if (!follow && b.phase >= 0) return;
    const std::string frame = b.host ? std::string("static") : b.themeFrame;
    const QuadUv full = uvQuad(0, 1);
    const float position = std::trunc(root().scroll);
    if (frame == "scroll" || frame == "slowScroll") {
        const bool slow = frame == "slowScroll";
        const float offset = top ? (slow ? 0x4e5 : 0x3ea) : 0;
        const float pos = slow ? std::trunc((position + offset) * 0.8f) : position + offset;
        const float width = top ? 412 : 320;
        const float m = cRem(pos, kScrollWidth);
        const float mask = (m + 4) * 0.125f;
        p->texCoords[0] = uvQuad(-126 + mask, (width - kScrollWidth) * 0.125f + mask);
        p->texCoords[1] = uvQuad(m / kScrollWidth - 1, m / kScrollWidth + (width - kScrollWidth) / kScrollWidth);
        p->texCoords[2] = uvQuad(m / kScrollWidth, (m + width) / kScrollWidth);
    } else if (frame == "animated" || (frame == "bounce" && !top)) {
        const bool bounce = frame == "bounce";
        int phase = (int)std::trunc(cRem(position, kPhaseLength * (bounce ? 4 : 3)) / kPhaseLength);
        if (phase == 3) phase = 1;
        const QuadUv f = uvQuad(phase / 3.0f, (phase + 1) / 3.0f);
        p->texCoords[0] = full;
        p->texCoords[1] = f;
        p->texCoords[2] = f;
        if (phase != b.phase) {
            // a new bottom frame plays the theme's frame sound (background.ts follow)
            if (b.phase >= 0 && !top && !b.host && self->onBackgroundFrame) self->onBackgroundFrame(phase);
            b.phase = phase;
        }
    } else {
        p->texCoords[0] = full;
        p->texCoords[1] = p->texCoords[2] =
            b.host && b.hostFlip ? QuadUv{Vec2{0, 1}, Vec2{1, 1}, Vec2{0, 0}, Vec2{1, 0}} : full;
    }
}

// FUN_001d4788 and the plate switch of FUN_001d3504: the images show over the wall and in
// place of the grid plates and the bottom background panes.
void CtrHome::Impl::updateBackgrounds() {
    const bool topShown = bgTop.shown(), bottomShown = bgBottom.shown();
    if (bgTop.lay) bgTop.lay->layout().root->visible = topShown;
    if (bgBottom.lay) bgBottom.lay->layout().root->visible = bottomShown;
    for (const char* n : {"W_Plt_00", "W_Shdw_00"})
        if (Pane* p = palette ? palette->pane(n) : nullptr) p->visible = !bottomShown;
    for (const char* n : {"P_BgTop_00", "P_BgL_00", "P_BgR_00", "P_BgBtm_00"})
        if (Pane* p = base ? base->pane(n) : nullptr) p->visible = !bottomShown;
    // a top image hides the wall, not while a title is suspended (its screen is in the wall)
    if (top3d) top3d->setWallHidden(topShown && !appActive());
    // icons.ts setBlankPicture: empty slots as the W_Blank_00 window over a bottom image, at 32
    if (bottomShown != blankPicture && blank.layout) {
        blankPicture = bottomShown;
        blank.layout->layout().opacity = bottomShown ? 0x20 : 0x80;
        blank.setPicture(bottomShown, "01");
        if (pickUpBlank.layout) pickUpBlank.setPicture(bottomShown, "01");
    }
    // the images follow the root grid, not a folder's (FUN_001d41e4)
    // (the texture is bound in any case; the coordinates keep the root grid's last position)
    if (topShown) followBackground(bgTop, true, !inFolder());
    if (bottomShown) followBackground(bgBottom, false, !inFolder());
}

void CtrHome::setTheme(const ThemeBody* body) {
    if (mImpl) mImpl->themeRev++;
    mThemeBody = body;
    mTheme->setTheme(body);
    if (mLoaded && mImpl->folderColorsOn) applyFolderColors();   // over the new theme's folder block
    Impl& I = *mImpl;
    if (I.top3d) I.top3d->setTheme(body);
    // background.ts setTheme: the images of texture draw types
    I.bgTop.theme = body && body->top.drawType == "texture" ? themeTexture(*mAssets, body->topImage) : nullptr;
    I.bgTop.themeFrame = body ? body->topFrameType : std::string();
    I.bgBottom.theme = body && body->bottomDrawType == "texture" ? themeTexture(*mAssets, body->bottomImage) : nullptr;
    I.bgBottom.themeFrame = body ? body->bottomFrameType : std::string();
    // icons.ts: the folder and icon frame images (both file sizes needed, as setThemeArt)
    I.themeFolder = body ? themeTexture(*mAssets, body->folderClosed) : nullptr;
    I.themeFolderOpen = body ? themeTexture(*mAssets, body->folderOpen) : nullptr;
    // icons.ts setThemeArt: folders take the picture look only with both images
    if (!I.themeFolder || !I.themeFolderOpen) I.themeFolder = I.themeFolderOpen = nullptr;
    I.themeFileLarge = body ? themeTexture(*mAssets, body->fileLarge) : nullptr;
    I.themeFileSmall = body ? themeTexture(*mAssets, body->fileSmall) : nullptr;
    if (!I.themeFileLarge || !I.themeFileSmall) I.themeFileLarge = I.themeFileSmall = nullptr;
    if (mLoaded) {
        if (I.tile.layout) I.tile.setPicture(I.themeFileLarge != nullptr);
        if (I.pickUp.layout) I.pickUp.setPicture(I.themeFileLarge != nullptr);
        const bool folderArt = I.themeFolder != nullptr;
        if (I.folderIcon.layout) {
            I.folderIcon.setPicture(folderArt);
            if (folderArt) I.folderIcon.layout->replaceTexture("P_Folder_10", I.themeFolder);
        }
        if (I.pickUpFolder.layout) {
            I.pickUpFolder.setPicture(folderArt);
            if (folderArt) for (const char* p : {"P_Folder_20", "P_Folder_21"}) I.pickUpFolder.layout->replaceTexture(p, I.themeFolder);
        }
        if (I.folderOpenT.layout) {
            I.folderOpenT.setPicture(folderArt);
            if (folderArt) I.folderOpenT.layout->replaceTexture("P_FolderIn_10", I.themeFolderOpen);
        }
        I.makerPicture(folderArt);
    }
}

void CtrHome::setWallpaper(bool top, GLuint tex, int w, int h, bool dynamic, bool flipY) {
    Impl::Background& b = top ? mImpl->bgTop : mImpl->bgBottom;
    b.hostDynamic = tex && dynamic;
    b.hostFlip = flipY;
    if (!tex) { b.host.reset(); return; }
    if (b.host && b.host->id == tex && b.host->width == w && b.host->height == h) return;
    // the host owns the GL texture: the wrapper never deletes it
    b.host = std::shared_ptr<Texture>(new Texture(), [](Texture* t) { gTextureEpoch++; delete t; });
    b.host->id = tex;
    b.host->width = b.host->texWidth = w;
    b.host->height = b.host->texHeight = h;
    gTextureEpoch++;
}

void CtrHome::takeCacheStats(int* hits, int* misses) {
    Renderer::CacheSlot& c = mImpl->bottomCache;
    *hits = c.hits;
    *misses = c.misses;
    c.hits = c.misses = 0;
}

bool CtrHome::canMoveFocused() const {
    if (!mLoaded) return false;
    Impl& I = *mImpl;
    if (I.state != StIdle) return false;
    const Grid& g = I.grid();
    const HomeItem* it = g.onBar ? nullptr : g.item(g.cursor);
    return it && I.canGrab(g, *it);
}

bool CtrHome::startMove() {
    if (!canMoveFocused()) return false;
    Impl& I = *mImpl;
    Grid& g = I.grid();
    const Point p = g.slotOnScreen(g.cursor);
    I.startGrab(g.cursor, p.x, p.y);
    if (!I.holding) return false;
    I.mover.buttons = true;
    I.mover.cell = g.cursor;
    return true;
}

const HomeItem* CtrHome::focusedItem() const {
    if (!mLoaded) return nullptr;
    Impl& I = *mImpl;
    if (I.state != StIdle) return nullptr;
    Grid& g = I.grid();
    return g.onBar ? nullptr : g.item(g.cursor);
}

const HomeItem* CtrHome::focusedTitle() const {
    if (!mLoaded) return nullptr;
    Impl& I = *mImpl;
    if (I.state != StIdle) return nullptr;
    Grid& g = I.grid();
    if (g.onBar) return nullptr;
    const HomeItem* it = g.item(g.cursor);
    return it && it->kind == HomeItemKind::Title ? it : nullptr;
}

bool CtrHome::testOpen(const std::string& name) {
    if (!mLoaded) return false;
    Impl& I = *mImpl;
    if (I.state != StIdle) return false;
    if (I.inFolder()) {
        I.closeFolder();
        return false;
    }
    Grid& g = I.root();
    for (int s = 0; s < g.slots; s++) {
        const HomeItem* it = g.item(s);
        if (!it || it->name != name) continue;
        g.onBar = false;
        g.cursor = s;
        g.clampView();   // scroll to it (ensureCursorVisible moves the cursor into the view instead)
        if (it->kind == HomeItemKind::Folder) I.openFolder(s);
        I.updateChrome();
        return true;
    }
    ALOGW("ctr: no root item named %s", name.c_str());
    return true;   // nothing to wait for
}

std::vector<std::string> CtrHome::focusPath() const {
    std::vector<std::string> ids;
    if (!mLoaded) return ids;
    const Impl& I = *mImpl;
    for (size_t k = 1; k < I.grids.size(); k++) ids.push_back(I.grids[k].folderId);
    const Grid& g = I.grids.back();
    if (const HomeItem* it = g.onBar ? nullptr : g.item(g.cursor)) ids.push_back(it->id);
    return ids;
}

void CtrHome::restorePath(const std::vector<std::string>& ids) {
    if (!mLoaded || ids.empty()) return;
    Impl& I = *mImpl;
    while (I.grids.size() > 1) I.grids.pop_back();
    for (size_t k = 0; k < ids.size(); k++) {
        Grid& g = I.grid();
        int slot = -1;
        for (int s = 0; s < (int)g.slotItem.size() && slot < 0; s++) {
            const HomeItem* it = g.item(s);
            if (it && it->id == ids[k]) slot = s;
        }
        if (slot < 0) break;   // gone (a rescan): stay where the path still leads
        g.onBar = false;
        g.cursor = slot;
        g.clampView();
        const HomeItem* it = g.item(slot);
        if (k + 1 < ids.size() && it->kind == HomeItemKind::Folder) I.openFolder(slot, true);
        else break;
    }
    I.updateChrome();
}

void CtrHome::setZooms(int root, int folder) {
    if (!mLoaded) return;
    Impl& I = *mImpl;
    I.folderZoom = std::clamp(folder, MIN_FOLDER_ZOOM, MAX_ZOOM);
    I.root().setZoom(std::clamp(root, 0, MAX_ZOOM));
    for (size_t k = 1; k < I.grids.size(); k++) I.grids[k].setZoom(I.folderZoom);
}

void CtrHome::showCategory(int cursor) {
    if (!mLoaded) return;
    mImpl->catPending = true;
    mImpl->catCursor = std::max(0, cursor);
}

// The root grid with the host's new content: the cursor on the remembered slot (or the bar it
// was on), no folder open.
void CtrHome::Impl::applyCategory() {
    catPending = false;
    Grid& g = root();
    loadGrid(g);
    g.cursor = std::min(catCursor, std::max(0, (int)g.slotItem.size() - 1));
    g.barReturnColumn = -1;
    g.clampView();
    scroller.stop();
    showNameBalloon = false;
    iconTex.clear();
    // the last category's coloured glyph copies (about 340 KB each) and the copies made from them
    // leave the cache (a return redraws them). They are released at the next switch, not now: the
    // leaving banner may still draw one, and a freed GL name could come back under a stale key.
    tintedRetired.clear();
    std::set<GLuint> retired;
    for (auto it = tinted.begin(); it != tinted.end();) {
        if (it->first.second & 32) {
            if (it->second) retired.insert(it->second->id);
            tintedRetired.push_back(std::move(it->second));
            it = tinted.erase(it);
        } else {
            ++it;
        }
    }
    for (auto it = tinted.begin(); it != tinted.end();) {
        if (retired.count(it->first.first)) {
            tintedRetired.push_back(std::move(it->second));
            it = tinted.erase(it);
        } else {
            ++it;
        }
    }
    gTextureEpoch++;
    sound("SE_CTR_HOME_ICON_SCROLL");
}

void CtrHome::setFolderColors(const float* dark, const float* main) {
    if (!mLoaded) return;
    Impl& I = *mImpl;
    const bool on = dark && main;
    if (on == I.folderColorsOn &&
        (!on || (!memcmp(dark, I.folderDark, sizeof(I.folderDark)) && !memcmp(main, I.folderMain, sizeof(I.folderMain)))))
        return;
    I.folderColorsOn = on;
    if (on) {
        memcpy(I.folderDark, dark, sizeof(I.folderDark));
        memcpy(I.folderMain, main, sizeof(I.folderMain));
    }
    applyFolderColors();
}

// The folder block the screens use: the theme's with the category's dark / main / light.
void CtrHome::applyFolderColors() {
    Impl& I = *mImpl;
    I.themeRev++;
    if (!I.folderColorsOn) {
        mTheme->setFolderBlock(nullptr);
        if (I.top3d) I.top3d->setFolderBlock(nullptr);
        return;
    }
    ThemeBlock b;
    if (mThemeBody && mThemeBody->blocks[TB_FOLDER].present) b = mThemeBody->blocks[TB_FOLDER];
    b.present = true;
    auto byte = [](float v) { return (uint8_t)std::clamp((int)lroundf(v * 255.0f), 0, 255); };
    auto put = [&](const char* name, float r, float g, float bl) {
        const std::array<uint8_t, 3> v = {byte(r), byte(g), byte(bl)};
        for (auto& f : b.rgb)
            if (f.first == name) { f.second = v; return; }
        b.rgb.emplace_back(name, v);
    };
    put("dark", I.folderDark[0], I.folderDark[1], I.folderDark[2]);
    put("main", I.folderMain[0], I.folderMain[1], I.folderMain[2]);
    // the lit edge: the main colour taken halfway to white
    put("light", I.folderMain[0] + (1 - I.folderMain[0]) * 0.5f, I.folderMain[1] + (1 - I.folderMain[1]) * 0.5f,
        I.folderMain[2] + (1 - I.folderMain[2]) * 0.5f);
    mTheme->setFolderBlock(&b);
    if (I.top3d) I.top3d->setFolderBlock(&b);
}

// The console's bar buttons are 36 x 36 unit pictures from textures of about one texel a unit,
// symmetric pictograms mirrored from a half texture. Ours are drawn whole at four texels a unit:
// map 0 the button body (luminance), map 1 the pictogram's ink (luminance, dark = ink) with the
// button's shape in alpha; the material's combiner then shades them as it does the console's.
void CtrHome::setBarIcon(int button, const uint8_t* rgba, int w, int h, const float ink[3], const float inkLight[3]) {
    static const char* const kPanes[6] = {nullptr, "P_Memo_10", "P_Frd_10", "P_News_10", "P_Web_10", "P_Mvs_10"};
    if (!mLoaded || button < 1 || button > 5 || !rgba || w <= 0 || h <= 0) return;
    Impl& I = *mImpl;
    LayoutInstance* L = I.base.get();
    Pane* p = L ? L->pane(kPanes[button]) : nullptr;
    if (!p || p->material < 0) return;
    Layout& lay = L->layout();
    Material& m = lay.materials[p->material];
    if (m.texMaps.size() < 2) return;
    constexpr int K = 4, U = 36, N = U * K;
    // the pictogram's extent (alpha above a trace)
    int x0 = w, y0 = h, x1 = -1, y1 = -1;
    for (int y = 0; y < h; y++)
        for (int x = 0; x < w; x++)
            if (rgba[((size_t)y * w + x) * 4 + 3] > 8) {
                x0 = std::min(x0, x); x1 = std::max(x1, x);
                y0 = std::min(y0, y); y1 = std::max(y1, y);
            }
    if (x1 < x0) return;
    const float gw = (float)(x1 - x0 + 1), gh = (float)(y1 - y0 + 1);
    // fitted into a 21 unit box on the face, centred a little below the button's middle (the
    // console's pictograms sit there, clear of the glow at the top)
    const float fit = 21.0f / std::max(gw, gh);
    const float gcx = x0 + gw * 0.5f, gcy = y0 + gh * 0.5f, ucx = 18.0f, ucy = 19.0f;
    // signed distance to a rounded rectangle (units, negative inside)
    auto roundRect = [](float x, float y, float l, float t, float r, float b, float rad) {
        const float qx = fabsf(x - (l + r) * 0.5f) - ((r - l) * 0.5f - rad);
        const float qy = fabsf(y - (t + b) * 0.5f) - ((b - t) * 0.5f - rad);
        const float ox = std::max(qx, 0.0f), oy = std::max(qy, 0.0f);
        return sqrtf(ox * ox + oy * oy) + std::min(std::max(qx, qy), 0.0f) - rad;
    };
    auto cover = [](float d) { return std::clamp(0.5f - d * K, 0.0f, 1.0f); };   // one texel of edge
    auto alphaAt = [&](float x, float y) {   // bilinear, outside = 0
        x -= 0.5f; y -= 0.5f;
        const int ix = (int)floorf(x), iy = (int)floorf(y);
        const float fx = x - ix, fy = y - iy;
        auto a = [&](int px, int py) {
            return px < 0 || py < 0 || px >= w || py >= h ? 0.0f : rgba[((size_t)py * w + px) * 4 + 3] / 255.0f;
        };
        return (a(ix, iy) * (1 - fx) + a(ix + 1, iy) * fx) * (1 - fy) + (a(ix, iy + 1) * (1 - fx) + a(ix + 1, iy + 1) * fx) * fy;
    };
    // supersampling: the pictogram is several source pixels a texel
    const int ss = std::clamp((int)ceilf(1.0f / (fit * K)), 1, 6);
    // face: the pictogram in ink on the white button; active: the button in ink with the
    // pictogram white (the category on the grid)
    std::vector<uint8_t> body((size_t)N * N * 4), face((size_t)N * N * 4), active((size_t)N * N * 4);
    for (int ty = 0; ty < N; ty++) {
        const size_t row = (size_t)ty * N;   // rows top first (uv 0, 0 is the top left)
        for (int tx = 0; tx < N; tx++) {
            const float ux = (tx + 0.5f) / K, uy = (ty + 0.5f) / K;
            // the body fills the shape (a body short of it shows the combiner's dark edge)
            const float b = cover(roundRect(ux, uy, 3.5f, 3.0f, 32.5f, 34.0f, 6.5f));
            const float shape = cover(roundRect(ux, uy, 4.0f, 3.5f, 32.0f, 33.5f, 6.0f));
            float a = 0;
            for (int sy = 0; sy < ss; sy++)
                for (int sx = 0; sx < ss; sx++) {
                    const float px = (tx + (sx + 0.5f) / ss) / K, py = (ty + (sy + 0.5f) / ss) / K;
                    a += alphaAt(gcx + (px - ucx) / fit, gcy + (py - ucy) / fit);
                }
            a /= ss * ss;
            uint8_t* B = &body[(row + tx) * 4];
            B[0] = B[1] = B[2] = (uint8_t)lroundf(b * 255);
            B[3] = 255;
            uint8_t* F = &face[(row + tx) * 4];
            F[0] = F[1] = F[2] = (uint8_t)lroundf((1 - a) * 255);
            F[3] = (uint8_t)lroundf(shape * 255);
            uint8_t* X = &active[(row + tx) * 4];
            X[0] = X[1] = X[2] = (uint8_t)lroundf(a * 255);
            X[3] = F[3];
        }
    }
    // the pictogram itself for the top screen's applet banner (a solid in its glass cube)
    I.barGlyph[button] = mAssets->rgbaTexture(rgba, w, h);
    for (int k = 0; k < 3; k++) I.barTint[button][k] = inkLight[k];
    // the empty slot banner's floating plate for this applet (the console's DefBnr textures):
    // a rounded plate with an ink outline ring and the pictogram, luminance white = plate, black =
    // ink, alpha the plate's silhouette; rows bottom first, as the banner models sample them
    {
        constexpr int P = 128;   // texels (the console's are 64)
        constexpr float S = 64.0f / P;   // in 64 unit space
        std::vector<uint8_t> plate((size_t)P * P * 4);
        const float pfit = 34.0f / std::max(gw, gh);   // the pictogram in a 34 of 64 box
        const int pss = std::clamp((int)ceilf(1.0f / (pfit / S)), 1, 6);
        for (int ty = 0; ty < P; ty++) {
            const size_t row = (size_t)(P - 1 - ty) * P;
            for (int tx = 0; tx < P; tx++) {
                const float ux = (tx + 0.5f) * S, uy = (ty + 0.5f) * S;
                const float d = roundRect(ux, uy, 5.0f, 5.0f, 59.0f, 59.0f, 11.0f);
                const float shape = std::clamp(0.5f - d / S, 0.0f, 1.0f);
                // the ink ring: 4.5 units wide, 1.5 inside the edge (the console's outlined plates)
                const float ring = std::clamp(0.5f - (fabsf(d + 3.75f) - 2.25f) / S, 0.0f, 1.0f);
                float a = 0;
                for (int sy = 0; sy < pss; sy++)
                    for (int sx = 0; sx < pss; sx++) {
                        const float px = (tx + (sx + 0.5f) / pss) * S, py = (ty + (sy + 0.5f) / pss) * S;
                        a += alphaAt(gcx + (px - 32.0f) / pfit, gcy + (py - 32.0f) / pfit);
                    }
                a /= pss * pss;
                const float inked = std::max(ring, a);
                uint8_t* o = &plate[(row + tx) * 4];
                o[0] = o[1] = o[2] = (uint8_t)lroundf((1 - inked) * 255);
                o[3] = (uint8_t)lroundf(shape * 255);
            }
        }
        I.barPlate[button] = mAssets->rgbaTexture(plate.data(), P, P);
        glBindTexture(GL_TEXTURE_2D, I.barPlate[button]->id);
        glGenerateMipmap(GL_TEXTURE_2D);
        I.barPlate[button]->mipmapped = true;
        if (I.top3d) I.top3d->setDefaultIcon(button, I.barPlate[button]->id, P, P, ink);
    }
    m.texMaps[0].texture = mAssets->rgbaTexture(body.data(), N, N);
    I.barFace[button] = mAssets->rgbaTexture(face.data(), N, N);
    I.barFaceActive[button] = mAssets->rgbaTexture(active.data(), N, N);
    m.texMaps[1].texture = I.barActive == button ? I.barFaceActive[button] : I.barFace[button];
    for (int k = 0; k < 2; k++) m.texMaps[k].wrapS = m.texMaps[k].wrapT = GL_CLAMP_TO_EDGE;
    if (!p->texCoords.empty()) p->texCoords[0] = QuadUv{Vec2{0, 0}, Vec2{1, 0}, Vec2{0, 1}, Vec2{1, 1}};
    // the ink colours (constants 4 and 5, the console's per applet colours)
    const int mi = p->material;
    for (int k = 0; k < 3; k++) {
        m.konst[4][k] = ink[k] * 255.0f;
        m.konst[5][k] = inkLight[k] * 255.0f;
        if (mi < (int)lay.fileColors.size()) {
            lay.fileColors[mi].konst[4][k] = m.konst[4][k];
            lay.fileColors[mi].konst[5][k] = m.konst[5][k];
        }
    }
    L->refresh();
    gTextureEpoch++;
}

void CtrHome::setActiveBar(int button) {
    static const char* const kPanes[6] = {nullptr, "P_Memo_10", "P_Frd_10", "P_News_10", "P_Web_10", "P_Mvs_10"};
    if (!mLoaded) return;
    Impl& I = *mImpl;
    if (button == I.barActive) return;
    I.barActive = button;
    LayoutInstance* L = I.base.get();
    if (!L) return;
    for (int b = 1; b <= 5; b++) {
        Pane* p = L->pane(kPanes[b]);
        if (!p || p->material < 0 || !I.barFace[b]) continue;
        Material& m = L->layout().materials[p->material];
        if (m.texMaps.size() >= 2) m.texMaps[1].texture = b == button ? I.barFaceActive[b] : I.barFace[b];
    }
    gTextureEpoch++;
}

void CtrHome::reloadItems() {
    if (!mLoaded) return;
    Impl& I = *mImpl;
    for (Grid& g : I.grids) {
        const HomeItem* ci = g.item(g.cursor);
        const std::string cur = ci ? ci->id : std::string();
        I.loadGrid(g);
        for (size_t s = 0; s < g.slotItem.size(); s++)
            if (g.slotItem[s] >= 0 && g.items[g.slotItem[s]].id == cur) g.cursor = (int)s;
        g.clampView();
    }
    I.iconTex.clear();
    gTextureEpoch++;   // host icons may have been reloaded under the same GL names
}

}  // namespace ctr
}  // namespace android
