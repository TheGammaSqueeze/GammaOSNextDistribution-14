// NanoCtrHome - 3DS HOME Menu runtime (see NanoCtrHome.h). Port of /work/3ds/web/app/src/menu:
// scenes/launcher (grid, cursor, applet bar, arrows, scroll bar, bottom buttons, balloon,
// folders, zoom, touch), scroll/scroller.ts, hud/hud.ts and fade.ts. Constants carry the
// addresses the web code cites (docs/re/launcher.md).
#include "NanoCtrHome.h"
#include "NanoCtrTop.h"

#include <algorithm>
#include <map>
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
    StFolderOpen = 0x101, StFolderClose = 0x102, StAppRunning = 0x103, StAppReturn = 0x104,
};

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
    float offsetX = 0;
    std::vector<std::string> hidden;
    float posed = -1000;

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
    // fades
    std::unique_ptr<LayoutInstance> fadeTop, fadeBottom;
    bool fadeActive = false;

    // icons
    IconTemplate blank, tile, folderIcon, icon;
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
    int netFrame = -100;
    // top screen 3D scene
    std::unique_ptr<CtrTop> top3d;
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
        previousState = state;
        state = id;
        stateFrame = 0;
    }
    void handleIdleInput();
    void activate(int source);   // 0 key 1 touch 2 button
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
    void openFolder(int slot);
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
    TexturePtr tintedCopy(GLuint src, int w, int h, float grey, float coverAspect = 0, bool flipY = false);
    std::unordered_map<GLuint, TexturePtr> hostTex;  // wrappers of host textures
    uint64_t artGen = 0;
    Renderer::CacheSlot bottomCache;   // background, sheets and icons of the bottom screen
    std::map<std::pair<GLuint, int>, TexturePtr> tinted;   // derived icons by source texture + kind
    GLuint tintProgram = 0;
};

// ================================================================== setup

bool CtrHome::Impl::load() {
    base = make("launcher", "LncBase_D_01");
    palette = make("launcher", "LncPlt_00");
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
    icon.layout->setAlpha("P_IconBtnDmy_00", 0);
    blank.layout->layout().opacity = 0x80;
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
    if (entered) mainUpdate();
    else updateChrome();
    // animation pass (every drawable of both screens)
    for (LayoutInstance* l : {base.get(), palette.get(), arrows.get(), buttons.get(), cursor.get(), folder.get(),
                              balloon.get(), top.get(), hud.get(), fadeTop.get(), fadeBottom.get()})
        if (l) l->update(1);
    for (Trail& t : trail) if (t.active && t.layout) t.layout->update(1);
    if (top3d) top3d->update();
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
        case StIdle: handleIdleInput(); break;
        case StColumnScroll: updateColumnScroll(); break;
        case StZoom: updateZoom(); break;
        case StFolderOpen:
            if (stateFrame >= 17) setState(StIdle);
            break;
        case StFolderClose:
            if (stateFrame >= 17) {
                folder->layout().root->visible = inFolder();
                closingFolder = false;
                setState(StIdle);
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
    if (!it) return;
    if (source != 2) decideBottom();
    if (it->kind == HomeItemKind::Folder) openFolder(g.cursor);
    else launch(*it);
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
    return state == StFolderOpen || state == StFolderClose || state == StAppRunning;
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
    setState(StIdle);
}

// ------------------------------------------------------------ folders (folders.ts)

void CtrHome::Impl::openFolder(int slot) {
    Grid& parent = grid();
    const HomeItem* it = parent.item(slot);
    if (!it) return;
    sound("SE_CTR_HOME_OPEN_FOLDER");
    parent.onBar = false;
    Grid g;
    g.folderId = it->id;
    g.folderName = it->name;
    loadGrid(g);
    g.metrics = metricsFor(2, true);   // NEW_FOLDER_ZOOM
    g.clampView();
    grids.push_back(std::move(g));
    folder->layout().root->visible = true;
    folder->play("FadeIn", groupsOpt());
    setState(StFolderOpen);
}

void CtrHome::Impl::closeFolder() {
    if (!inFolder()) return;
    sound("SE_CTR_HOME_CLOSE_FOLDER");
    closing = std::move(grids.back());
    grids.pop_back();
    closingFolder = true;
    PlayOptions o = groupsOpt();
    o.speed = -1;
    folder->play("FadeIn", o);
    setState(StFolderClose);
}

// ------------------------------------------------------------ launch (launch.ts)

void CtrHome::Impl::launch(const HomeItem& item) {
    if (host && !host->launch(item)) {
        sound("SE_CTR_HOME_BANNER_INVALID");
        return;
    }
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
    if (!it) return "none";
    return it->kind == HomeItemKind::Folder ? "folder" : "title";
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
    std::string text = it->name;
    if (it->kind == HomeItemKind::Title && !it->publisher.empty()) text += "\n" + it->publisher;
    balloonShow(text, false, x, y, g.zoomValue());
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
    if (inFolder()) updateSheet(folder.get(), grid(), folderShadowExtra);
    else if (closingFolder) updateSheet(folder.get(), closing, folderShadowExtra);
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
        } else if (const HomeItem* it = g.item(g.cursor)) {
            f.kind = it->kind == HomeItemKind::Folder ? TopFocus::Folder : TopFocus::Title;
            f.key = it->id;
            f.plate = it->name;
            if (it->kind == HomeItemKind::Title && !it->publisher.empty()) f.plate += "\n" + it->publisher;
            if (it->kind == HomeItemKind::Title && host) {
                CtrHomeHost::TitleArt ta;
                host->titleArt(*it, &ta);
                // a 3DS banner still being read keeps the shown banner (no 2D banner flash)
                if (ta.pending) return;
                f.cbmd = ta.cbmd;
                f.dsIcon = ta.dsIcon;
                // banner models take their textures stored upside down (like the name plates)
                if (ta.icon) {
                    if (TexturePtr c = tintedCopy(ta.icon, ta.w, ta.h, 1.0f, ta.art.coverAspect, true)) {
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
                              (f.cbmd ? "|cbmd" : "");
        if (k != topFocusKey) {
            topFocusKey = k;
            top3d->focus(f);
        }
    }
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
    (void)y;
    tHeld++;
    if (tKind != TGrid) return;
    if (scroller.move(x)) { tDragged = true; return; }
    if (scroller.state == Scroller::Dragging) { tDragged = true; applyDelta(scroller.delta); }
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
        // battery (FUN_001ef538)
        chargeBlink = st.adapter && st.charging;
        lowBlink = !st.adapter && st.batteryLevel <= 1;
        if (hudBattery) {
            if (st.adapter && !st.charging) hudBattery->seek(6);
            else if (!st.adapter) hudBattery->seek(st.batteryLevel <= 2 ? 0.0f : (float)(st.batteryLevel - 2));
            hudBattery->apply();
        }
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
    if (chargeBlink && !lowBlink && hudBattery) { hudBattery->seek(odd ? 4.0f : 5.0f); hudBattery->apply(); }
}

// ================================================================== draw

// A copy of a GL texture with its colour scaled to `grey` (alpha kept), rendered once into a
// new texture. Copies are shared per source texture.
// A derived copy of a GL texture, rendered once: colour scaled to `grey` (alpha kept), and for a
// cover (coverAspect > 0) fitted whole into a transparent square. Copies are kept per source.
TexturePtr CtrHome::Impl::tintedCopy(GLuint src, int w, int h, float grey, float coverAspect, bool flipY) {
    const int kind = (grey < 1 ? 1 : 0) | (coverAspect > 0 ? 2 : 0) | (flipY ? 4 : 0);
    auto cached = tinted.find({src, kind});
    if (cached != tinted.end()) return cached->second;
    static const char* vs = "attribute vec2 p; uniform vec2 s; uniform float f; varying vec2 uv;"
                            "void main(){ uv = p * 0.5 + 0.5; uv.y = mix(uv.y, 1.0 - uv.y, f);"
                            " gl_Position = vec4(p * s, 0.0, 1.0); }";
    static const char* fs = "#ifdef GL_FRAGMENT_PRECISION_HIGH\nprecision highp float;\n#else\nprecision mediump float;\n#endif\nvarying vec2 uv; uniform sampler2D t; uniform float g;"
                            "void main(){ vec4 c = texture2D(t, uv); gl_FragColor = vec4(c.rgb * g, c.a); }";
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
    glUniform1f(glGetUniformLocation(tintProgram, "g"), grey);
    glUniform2f(glGetUniformLocation(tintProgram, "s"), sx, sy);
    glUniform1f(glGetUniformLocation(tintProgram, "f"), flipY ? 1.0f : 0.0f);
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
    tinted[{src, kind}] = t;
    gTextureEpoch++;
    return t;
}

TexturePtr CtrHome::Impl::titleTexture(const HomeItem& it) {
    if (host) {
        const uint64_t gen = host->artGeneration();
        if (gen != artGen) {
            artGen = gen;
            tinted.clear();
            hostTex.clear();   // a reused GL name carries other sampler state
            gTextureEpoch++;
        }
        int w = 0, h = 0;
        CtrHomeHost::IconArt art;
        if (GLuint gl = host->titleIconTexture(it, &w, &h, &art)) {
            if (art.mono) {
                // white glyph art reads as the folder letters' grey (icons.ts LETTER_COLOR): a
                // darkened copy (the icon material ignores vertex colour)
                if (TexturePtr t = tintedCopy(gl, w, h, 80 / 255.0f)) return t;
            } else if (art.coverAspect > 0) {
                if (TexturePtr t = tintedCopy(gl, w, h, 1.0f, art.coverAspect)) return t;
            }
            auto f = hostTex.find(gl);
            if (f != hostTex.end()) return f->second;
            auto t = std::make_shared<Texture>();   // the host owns the GL object
            t->id = gl;
            t->width = t->texWidth = w;
            t->height = t->texHeight = h;
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

void CtrHome::Impl::drawIcons(Renderer& r, const Grid& g, const Box* clip) {
    const float zoom = g.zoomValue();
    for (IconTemplate* t : {&blank, &tile, &folderIcon, &icon}) if (t->layout) t->pose(zoom);
    const float limit = 160 + sExtraWidth * 0.5f + zoomTable(CULL_PITCH, zoom) * 0.5f;
    if (clip) r.setScissor(true, clip->left, clip->bottom, clip->right - clip->left, clip->top - clip->bottom);
    for (int slot = 0; slot < g.slots; slot++) {
        Point p = g.slotOnScreen(slot);
        if (p.x > limit || p.x < -limit) continue;
        const HomeItem* it = g.item(slot);
        if (!it) { stamp(r, blank, p.x, p.y); continue; }
        if (it->kind == HomeItemKind::Folder) {
            folderIcon.layout->setVisible("N_Had_02", it->folderFilled);
            stamp(r, folderIcon, p.x, p.y);
            if (letter) {
                // FolderLetter.stamp: outlined initial (icons.ts)
                static const float LETTER_SIZE[6] = {33, 33, 25.5f, 20.5f, 18.5f, 16.5f};
                static const float LETTER_Y[6] = {-4, -4, -2, -1, -1, 1};
                const std::u32string init = folderInitial(it->name);
                if (!init.empty()) {
                    Pane* tp = letter->pane("T_Icon_00");
                    if (tp && (tp->runs.empty() || tp->runs[0].text != init)) letter->setTextU32("T_Icon_00", init);
                    const float size = zoomTable(LETTER_SIZE, zoom);
                    const float cy = p.y + zoomTable(LETTER_Y, zoom);
                    const float scale = size / 32;
                    Pane* lr = letter->layout().root;
                    lr->scale[0] = lr->scale[1] = scale;
                    const float width = std::max(0.6f, scale * 1.4f);
                    static const float OFF[8][2] = {{-1, 0}, {1, 0}, {0, -1}, {0, 1}, {-0.7f, -0.7f}, {0.7f, -0.7f}, {-0.7f, 0.7f}, {0.7f, 0.7f}};
                    if (tp) tp->vertexColors = {Rgba{255, 255, 255, 255}, Rgba{255, 255, 255, 255}, Rgba{255, 255, 255, 255}, Rgba{255, 255, 255, 255}};
                    for (auto& o : OFF) {
                        lr->translate[0] = p.x + o[0] * width;
                        lr->translate[1] = cy + o[1] * width;
                        letter->layout().calculate();
                        r.drawLayout(letter->layout());
                    }
                    if (tp) tp->vertexColors = {Rgba{80, 80, 80, 255}, Rgba{80, 80, 80, 255}, Rgba{80, 80, 80, 255}, Rgba{80, 80, 80, 255}};
                    lr->translate[0] = p.x;
                    lr->translate[1] = cy;
                    letter->layout().calculate();
                    r.drawLayout(letter->layout());
                }
            }
            continue;
        }
        stamp(r, tile, p.x, p.y);
        TexturePtr tex = titleTexture(*it);
        Pane* ip = icon.layout->pane("P_Icon_00");
        if (tex && ip) {
            icon.layout->replaceTexture("P_Icon_00", tex);
            ip->size[0] = ip->size[1] = zoomTable(ICON_SIZE, zoom);
            stamp(r, icon, p.x, p.y);
        }
    }
    if (clip) r.setScissor(false);
}

// ================================================================== CtrHome

CtrHome::CtrHome(std::string assetRoot, CtrHomeHost* host) {
    mAssets = std::make_unique<Assets>(std::move(assetRoot));
    mImpl = std::make_unique<Impl>();
    mImpl->self = this;
    mImpl->A = mAssets.get();
    mImpl->host = host;
}

CtrHome::~CtrHome() {
    mImpl.reset();
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
    if (mImpl) mAssets->renderer().releaseCache(mImpl->bottomCache);
    if (mImpl) mImpl->iconTex.clear();
    if (mImpl) mImpl->tinted.clear();
    if (mImpl) mImpl->hostTex.clear();
    if (mImpl && mImpl->tintProgram) { glDeleteProgram(mImpl->tintProgram); mImpl->tintProgram = 0; }
    if (mImpl && mImpl->top3d) mImpl->top3d->releaseGl();
    if (mAssets) mAssets->releaseGl();
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
    const bool wasHeld = I.touch.held;
    t.down = mTouchDownLatched || (mTouchNext.held && !wasHeld);
    t.up = mTouchUpLatched || (!mTouchNext.held && wasHeld);
    t.held = mTouchNext.held || (t.down && !t.up);
    mTouchDownLatched = mTouchUpLatched = false;
    I.touch = t;
    mFrame++;
    I.tick();
    if (onTick) onTick();
}

void CtrHome::step(int frames) {
    for (int i = 0; i < frames; i++) tick();
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

void CtrHome::drawTop(const float proj[16], int vpX, int vpY, int vpW, int vpH) {
    if (!mLoaded) return;
    Impl& I = *mImpl;
    Renderer& r = mAssets->renderer();
    for (LayoutInstance* l : {I.top.get(), I.hud.get()}) if (l) l->layout().adaptWidth(mTopW, Layout::Adapt::Split);
    if (I.fadeTop) I.fadeTop->layout().adaptWidth(mTopW, Layout::Adapt::None);
    if (perfMark) perfMark("top3d");
    if (I.top3d && !getenv("CTR_NO3D")) I.top3d->draw(proj, vpX, vpY, vpW, vpH, mTopW);
    if (perfMark) perfMark("topLayouts");
    r.setViewport(vpX, vpY, vpW, vpH);
    r.beginWith(proj);
    r.setClip(-mTopW / 2, -120, mTopW, 240);
    I.drawInstance(r, I.top.get());
    I.drawInstance(r, I.hud.get());
    if (overlay) overlay(r, true);
    if (I.fadeActive || I.state == StAppStart || I.state == StAppRunning) I.drawInstance(r, I.fadeTop.get());
    r.end();
}

void CtrHome::drawBottom(const float proj[16], int vpX, int vpY, int vpW, int vpH) {
    if (!mLoaded) return;
    Impl& I = *mImpl;
    Renderer& r = mAssets->renderer();
    r.setViewport(vpX, vpY, vpW, vpH);
    for (LayoutInstance* l : {I.base.get(), I.arrows.get()}) if (l) l->layout().adaptWidth(mBotW, Layout::Adapt::Edge);
    for (LayoutInstance* l : {I.palette.get(), I.folder.get(), I.buttons.get(), I.balloon.get(), I.fadeBottom.get()})
        if (l) l->layout().adaptWidth(mBotW, Layout::Adapt::None);
    r.beginWith(proj);
    r.setClip(-mBotW / 2, -120, mBotW, 240);
    // The background, sheets and icons change only when the grid moves: an exact cache
    // (Renderer::endGroup) copies them while they hold still; the cursor and chrome draw on top.
    r.beginGroup();
    I.drawInstance(r, I.base.get());
    I.drawInstance(r, I.palette.get());
    I.drawIcons(r, I.root(), nullptr);
    if (I.inFolder() || I.closingFolder) {
        I.drawInstance(r, I.folder.get());
        const Grid& fg = I.inFolder() ? I.grid() : I.closing;
        const bool showIcons = I.inFolder() && !(I.state == StFolderOpen && I.stateFrame < 6);
        if (showIcons) {
            Pane* sheet = I.folder->pane("W_Plt_00");
            Box clip = sheet ? paneBox(sheet) : Box{-160, 160, 120, -120};
            I.drawIcons(r, fg, &clip);
        }
    }
    static const float kBlack[3] = {0, 0, 0};   // the bottom letterbox (NanoMenuCtr ctrDrawScreen)
    if (!getenv("CTR_NO_CACHE")) r.endGroup(I.bottomCache, kBlack);
    if (perfMark) { r.end(); perfMark("b.chrome"); r.beginWith(proj); r.setClip(-mBotW / 2, -120, mBotW, 240); }
    for (Trail& t : I.trail) if (t.active) I.drawInstance(r, t.layout.get());
    I.drawInstance(r, I.cursor.get());
    if (I.indFade > 0) I.drawInstance(r, I.indicatorL.get());
    I.drawInstance(r, I.arrows.get());
    I.drawInstance(r, I.balloon.get());
    I.drawInstance(r, I.buttons.get());
    if (overlay) overlay(r, false);
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

void CtrHome::resumeFromApp() {
    if (!mLoaded) return;
    Impl& I = *mImpl;
    if (I.state == StAppStart || I.state == StAppRunning) {
        I.homeEvent = true;
        I.setState(StAppRunning);
        I.mainUpdate();
    }
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
        I.ensureCursorVisible();
        if (it->kind == HomeItemKind::Folder) I.openFolder(s);
        I.updateChrome();
        return true;
    }
    ALOGW("ctr: no root item named %s", name.c_str());
    return true;   // nothing to wait for
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
