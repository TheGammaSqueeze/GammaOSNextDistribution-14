// NanoCtrTheme - see NanoCtrTheme.h.
#include "NanoCtrTheme.h"

#include <dirent.h>
#include <strings.h>
#include <sys/stat.h>

#include <algorithm>
#include <cmath>
#include <cstdio>
#include <cstring>
#include <map>

#include "NanoCtrRom.h"

namespace android {
namespace ctr {

namespace {

ThemeBody makeBody(const char* name, ThemeTop top, const char* bottom, bool bgm, std::array<ThemeBlock, TB_COUNT> blocks) {
    ThemeBody b;
    b.name = name;
    b.top = std::move(top);
    b.bottomDrawType = bottom;
    b.bgmEnabled = bgm;
    b.blocks = std::move(blocks);
    return b;
}

// Material colour slot as FUN_001d4980 indexes it: 0 the combiner buffer colour, 1..6 the
// constant colours 0..5.
using Slot = int;
// Which material of a pane takes the colours (bindings.ts MaterialTarget).
enum Target { PANE, FRAME, WINDOW };

struct Binding {
    ThemeBlockId block;
    std::vector<std::string> layouts;
    std::vector<std::string> panes;
    std::vector<std::pair<Slot, std::string>> colors;
    std::string alpha;          // block field written to the alpha of constant colour 0
    Target target = PANE;
    bool blank = false;         // empty slot colours: the block is chosen per grid
};

const std::vector<std::pair<Slot, std::string>> DML = {{0, "dark"}, {2, "main"}, {1, "light"}};

std::vector<std::string> prefixed(const char* prefix, const std::vector<std::string>& names, const char* suffix = "") {
    std::vector<std::string> out;
    for (const std::string& n : names) out.push_back(std::string(prefix) + n + suffix);
    return out;
}

std::vector<std::string> concat(std::vector<std::string> a, const std::vector<std::string>& b) {
    a.insert(a.end(), b.begin(), b.end());
    return a;
}

// bindings.ts THEME_BINDINGS: where the menu writes each theme colour block, in application order.
const std::vector<Binding>& bindings() {
    static const std::vector<Binding> list = [] {
        const std::vector<std::string> buttonPanes = {"C_01", "R_02", "L_03", "R_03", "C_03", "R_04", "C_04", "L_04", "RC_04", "LL_05"};
        const std::vector<std::string> buttonTexts = {"C_01", "R_02", "L_03", "R_03", "C_03", "R_04", "C_04", "RC_04", "LL_05"};
        std::vector<std::string> cardSides, cardGlows;
        for (const char* side : {"L", "R"})
            for (int n = 0; n < 4; n++) {
                cardSides.push_back(std::string("P_Card") + side + "_0" + std::to_string(n));
                cardGlows.push_back(std::string("P_Card") + side + "_1" + std::to_string(n));
            }
        std::vector<std::string> openPanes;
        for (const std::string& s : buttonPanes) { openPanes.push_back("P_BtnW_" + s); openPanes.push_back("P_EdgeW_" + s); }
        std::vector<Binding> b = {
            // cursor: FUN_0029d1d8
            {TB_CURSOR, {"LncCsr_00"}, {"W_CsrF_00"}, DML, "", FRAME},
            {TB_CURSOR, {"LncCsr_00"}, {"W_CsrLgt_00"}, {{1, "glow"}}, "", FRAME},
            {TB_CURSOR, {"LncCsrEfct_00"}, {"W_CsrEfct_00"}, {{1, "glow"}}, "", FRAME},
            {TB_CURSOR, {"LncCsrEfct_01"}, {"P_CsrEfct_00", "P_CsrEfct_01"}, {{0, "glow"}}},
            {TB_CURSOR, {"LncIconCardPickUp_00", "LncIconCardPickUp_01"}, cardSides, {{5, "dark"}}},
            {TB_CURSOR, {"LncIconCardPickUp_00", "LncIconCardPickUp_01"}, cardGlows, DML},
            {TB_CURSOR, {"LncIconCardPickUp_02"}, {"P_Card_00"}, {{5, "dark"}}},
            {TB_CURSOR, {"LncIconCardPickUp_02"}, {"P_Card_01"}, {{0, "dark"}}},
            {TB_CURSOR, {"LncIconPickUp_00"}, {"P_Btn_00", "P_Btn_10"}, {{5, "dark"}}},
            {TB_CURSOR, {"LncIconPickUp_00"}, {"P_Btn_01", "P_Btn_11"}, DML},
            {TB_CURSOR, {"LncIconFolderPickUp_00"}, {"P_Folder_01", "P_Folder_20", "P_FolderHad_00"}, {{5, "dark"}}},
            {TB_CURSOR, {"LncIconFolderPickUp_00"}, {"P_Folder_11", "P_Folder_21", "P_FolderHad_10"}, DML},
            {TB_CURSOR, {"LncIconPickUpBlank_00"}, {"P_Effect_00"}, {{0, "main"}}},
            {TB_CURSOR, {"LncIconPrizePickup_00"}, {"P_Icon_00"}, {{5, "dark"}}},
            {TB_CURSOR, {"LncIconPrizePickup_00"}, {"P_Icon_01"}, DML},

            // folder: FUN_001e0018
            {TB_FOLDER, {"LncIconFolder_00"}, {"P_Folder_00", "P_Folder_01", "P_Folder_02", "P_Folder_03"}, DML},
            {TB_FOLDER, {"LncIconFolder_00"}, {"P_BaseShdw_00"}, {{0, "shadow"}}},
            {TB_FOLDER, {"LncIconFolderMake_00", "LncIconFolderPickUp_00"}, {"P_Folder_01"}, DML},
            {TB_FOLDER, {"LncIconFolderInT_00"}, {"P_FolderIn_01"}, DML},
            {TB_FOLDER, {"LncIconFolderInT_00"}, {"P_BaseShdw_00", "P_BaseShdw_01"}, {{0, "shadow"}}},

            // file: FUN_002a30e4
            {TB_FILE, {"LncIconSetSrc_00"}, {"P_Btn_00", "P_Btn_01", "P_Btn_02", "P_Btn_03"}, DML},
            {TB_FILE, {"LncIconSetSrc_00", "LncIconPickUp_00"}, {"P_BtnShdw_00"}, {{0, "shadow"}}, "alpha"},
            {TB_FILE, {"LncIconPickUp_00"}, {"P_Btn_00"}, DML},
            {TB_FILE, {"LncIconFolder_00"}, {"P_FolderHad_00", "P_FolderHad_01", "P_FolderHad_02", "P_FolderHad_03"}, DML},
            {TB_FILE, {"LncIconFolderPickUp_00"}, {"P_FolderHad_00"}, DML},
            {TB_FILE, {"LncIconFolderInT_00"}, {"P_FolderHad_01"}, DML},

            // arrowButton: FUN_002ad280
            {TB_ARROW_BUTTON, {"LncArw_00"}, {"P_arwL_00", "P_arwR_00"}, DML},
            {TB_ARROW_BUTTON, {"LncArw_00"}, {"P_arwIconL_00", "P_arwIconR_00"}, {{4, "main"}}},
            {TB_ARROW_BUTTON, {"LncArw_00"}, {"P_arwShdwL_00"}, {{0, "shadow"}}, "alpha"},
            {TB_ARROW_BUTTON, {"LncArw_00"}, {"P_arwShdwR_00"}, {}, "alpha"},

            // arrow: FUN_002ae968
            {TB_ARROW, {"LncArw_00"}, {"P_arwIconL_00", "P_arwIconR_00"}, {{0, "border"}, {1, "unpressed"}, {6, "pressed"}}},

            // openButton: FUN_002ace78
            {TB_OPEN_BUTTON, {"LncBtmBtn_02"}, openPanes, DML},
            {TB_OPEN_BUTTON, {"LncBtmBtn_02"}, concat({"P_EdgeW_C_01_S"}, prefixed("P_BtnW_", buttonPanes, "_S")), {{0, "shadow"}, {4, "glow"}}, "alpha"},
            {TB_OPEN_BUTTON, {"LncBtmBtn_02"}, prefixed("T_BtnBW_", buttonTexts), {{1, "textShadow"}}},
            {TB_OPEN_BUTTON, {"LncBtmBtn_02"}, {"P_IconBW_L_04"}, {{0, "textShadow"}}},
            {TB_OPEN_BUTTON, {"LncBtmBtn_02"}, prefixed("T_BtnFW_", buttonTexts), {{1, "textMain"}}},
            {TB_OPEN_BUTTON, {"LncBtmBtn_02"}, {"P_IconFW_L_04"}, {{0, "textMain"}}},
            {TB_OPEN_BUTTON, {"LncBtmBtn_02"}, prefixed("T_BtnPW_", buttonTexts), {{1, "textSelected"}}},
            {TB_OPEN_BUTTON, {"LncBtmBtn_02"}, {"P_IconPW_L_04"}, {{0, "textSelected"}}},

            // closeButton: FUN_002ab250
            {TB_CLOSE_BUTTON, {"LncBtmBtn_02"}, {"P_BtnB_L_03", "P_EdgeB_L_03"}, DML},
            {TB_CLOSE_BUTTON, {"LncBtmBtn_02"}, {"P_BtnB_L_03_S"}, {{0, "shadow"}, {4, "glow"}}, "alpha"},
            {TB_CLOSE_BUTTON, {"LncBtmBtn_02"}, {"T_BtnBB_L_03"}, {{1, "textShadow"}}},
            {TB_CLOSE_BUTTON, {"LncBtmBtn_02"}, {"T_BtnFB_L_03"}, {{1, "textMain"}}},
            {TB_CLOSE_BUTTON, {"LncBtmBtn_02"}, {"T_BtnPB_L_03"}, {{1, "textSelected"}}},

            // gameText (the bottom screen balloon): FUN_002ac8c0
            {TB_GAME_TEXT, {"LncBlln_00"}, {"P_BllnL_00", "P_BllnR_00", "P_Pnt_00"}, {{0, "backgroundMain"}, {1, "backgroundLight"}}},
            {TB_GAME_TEXT, {"LncBlln_00"}, {"P_BllnShdwL_00", "P_BllnShdwR_00", "P_PntShdw_00"}, {{0, "shadow"}}, "alpha"},
            {TB_GAME_TEXT, {"LncBlln_00"}, {"T_Blln_00"}, {{1, "text"}}},

            // bottomSolid (grid palette and empty slots): FUN_002a697c, FUN_002a673c
            {TB_BOTTOM_SOLID, {"LncPlt_00"}, {"W_Plt_00"}, DML, "", WINDOW},
            {TB_BOTTOM_SOLID, {"LncPlt_00"}, {"W_Shdw_00"}, {{0, "shadow"}}, "alpha", FRAME},
            {TB_BOTTOM_SOLID, {"LncIconSetSrc_00"}, {"P_Blank_00", "P_Blank_01", "P_Blank_02", "P_Blank_03"}, DML, "", PANE, true},
            {TB_BOTTOM_SOLID, {"LncIconPickUpBlank_00"}, {"P_Blank_00"}, DML, "", PANE, true},

            // bottomOuter: FUN_002a0c70
            {TB_BOTTOM_OUTER, {"LncBase_D_01"}, {"P_BgBtm_00"}, {{4, "dark"}, {1, "main"}, {2, "light"}}},
            {TB_BOTTOM_OUTER, {"LncBase_D_01"}, {"P_BgTop_00", "P_BgL_00", "P_BgR_00"}, {{1, "main"}, {2, "light"}}},

            // folderBackground: FUN_002ab7cc
            {TB_FOLDER_BACKGROUND, {"LncFolder_00"}, {"W_Plt_00"}, DML, "", WINDOW},
            {TB_FOLDER_BACKGROUND, {"LncFolder_00"}, {"W_Shdw_00"}, {{0, "shadow"}}, "alpha", WINDOW},

            // folderArrow (folder back button): FUN_002ae650
            {TB_FOLDER_ARROW, {"LncFolder_00"}, {"P_BtnL_00", "P_BtnR_00"}, DML},
            {TB_FOLDER_ARROW, {"LncFolder_00"}, {"P_BtnShdwL_00", "P_BtnShdwR_00"}, {{0, "shadow"}, {2, "glow"}}, "alpha"},
            {TB_FOLDER_ARROW, {"LncFolder_00"}, {"P_PictB_00"}, {{0, "textShadow"}}},
            {TB_FOLDER_ARROW, {"LncFolder_00"}, {"P_PictF_00"}, {{0, "textMain"}, {2, "textSelected"}}},

            // bottomCornerButton (applet bar settings button and zoom buttons): FUN_002ad4f0
            {TB_BOTTOM_CORNER_BUTTON, {"LncBase_D_01"}, {"P_PetitBase_00", "P_UpDwBase_00", "P_UpDwLine_00"}, DML},
            {TB_BOTTOM_CORNER_BUTTON, {"LncBase_D_01"}, {"P_PetitShdw_00", "P_UpDwShdw_00"}, {{0, "shadow"}}},
            {TB_BOTTOM_CORNER_BUTTON, {"LncBase_D_01"}, {"P_Petit_20", "P_Dw_20", "P_Up_20"}, {{5, "main"}, {6, "light"}}},
            {TB_BOTTOM_CORNER_BUTTON, {"LncBase_D_01"}, {"P_Petit_20", "P_PetitP_20", "P_PetitP_21", "P_Up_20", "P_Dw_20", "P_UpP_20", "P_DwP_20"},
             {{0, "iconMain"}, {1, "iconLight"}}},

            // topCornerButton (camera and screenshot bars): FUN_002ab500
            {TB_TOP_CORNER_BUTTON, {"LncBase_U_00"}, {"CameraBase_00"}, {{0, "main"}, {1, "light"}}},
            {TB_TOP_CORNER_BUTTON, {"LncBase_U_00"}, {"CameraBaseS_00"}, {{0, "shadow"}}},
            {TB_TOP_CORNER_BUTTON, {"LncBase_U_00"}, {"P_PictCam_00", "P_PictL_00", "P_PictR_00", "P_Plus_00", "P_PictCam_01", "P_PictL_01"},
             {{0, "text"}}},
            {TB_TOP_CORNER_BUTTON, {"LncBase_U_00"}, {"T_CamText_00", "T_CamText_02"}, {{1, "text"}}},

            // demoText: FUN_002a822c
            {TB_DEMO_TEXT, {"LncBase_U_00"}, {"P_BnrText_00"}, {{1, "background"}}},
            {TB_DEMO_TEXT, {"LncBase_U_00"}, {"T_BnrText_00", "T_BnrText_01"}, {{1, "text"}}},
        };
        return b;
    }();
    return list;
}

// Bindings grouped by layout name.
const std::map<std::string, std::vector<const Binding*>>& bindingsByLayout() {
    static const std::map<std::string, std::vector<const Binding*>> map = [] {
        std::map<std::string, std::vector<const Binding*>> m;
        for (const Binding& b : bindings())
            for (const std::string& l : b.layouts) m[l].push_back(&b);
        return m;
    }();
    return map;
}

// The materials of a pane a binding writes (manager.ts materialsOf).
void materialsOf(const Pane* p, Target target, std::vector<int>* out) {
    out->clear();
    if (p->kind == PaneKind::Window) {
        const int frame = p->frames.empty() ? -1 : p->frames[0].material;
        if (target == FRAME) {
            if (frame >= 0) out->push_back(frame);
            return;
        }
        if (p->material >= 0) out->push_back(p->material);
        if (frame >= 0 && frame != p->material) out->push_back(frame);
        return;
    }
    if ((p->kind == PaneKind::Picture || p->kind == PaneKind::Text) && p->material >= 0) out->push_back(p->material);
}

}  // namespace

// ------------------------------------------------------------------ custom theme bodies

namespace {

uint32_t u32(const std::vector<uint8_t>& d, size_t off) {
    if (off + 4 > d.size()) return 0;
    return d[off] | d[off + 1] << 8 | d[off + 2] << 16 | (uint32_t)d[off + 3] << 24;
}

// theme.py COLOR_BLOCKS: enable flag offset, data offset field, the fields in file order. A
// field is "name" (3 RGB bytes), "#name" (1 byte) or "%name" (4 byte float).
struct BlockSpec {
    ThemeBlockId id;
    uint32_t enable, data;
    std::vector<const char*> fields;
};

const std::vector<BlockSpec>& blockSpecs() {
    static const std::vector<const char*> shaded = {"dark", "main", "light", "shadow", "#alpha"};
    static const std::vector<const char*> button = {"%textShadowPos", "dark", "main", "light", "shadow", "#alpha",
                                                    "glow", "textShadow", "textMain", "textSelected"};
    static const std::vector<BlockSpec> specs = {
        {TB_CURSOR, 0x2C, 0x30, {"dark", "main", "light", "glow"}},
        {TB_FOLDER, 0x34, 0x38, {"dark", "main", "light", "shadow"}},
        {TB_FILE, 0x48, 0x4C, shaded},
        {TB_ARROW_BUTTON, 0x5C, 0x60, shaded},
        {TB_ARROW, 0x64, 0x68, {"border", "unpressed", "pressed"}},
        {TB_OPEN_BUTTON, 0x6C, 0x70, button},
        {TB_CLOSE_BUTTON, 0x6C, 0x74, button},
        {TB_GAME_TEXT, 0x78, 0x7C, {"backgroundMain", "backgroundLight", "shadow", "#alpha", "text"}},
        {TB_BOTTOM_SOLID, 0x80, 0x84, shaded},
        {TB_BOTTOM_OUTER, 0x88, 0x8C, {"dark", "main", "light"}},
        {TB_FOLDER_BACKGROUND, 0x90, 0x94, shaded},
        {TB_FOLDER_ARROW, 0x98, 0x9C, button},
        {TB_BOTTOM_CORNER_BUTTON, 0xA0, 0xA4, {"dark", "main", "light", "shadow", "iconMain", "iconLight", "iconShadow"}},
        {TB_TOP_CORNER_BUTTON, 0xA8, 0xAC, {"main", "light", "shadow", "text"}},
        {TB_DEMO_TEXT, 0xB0, 0xB4, {"background", "text"}},
    };
    return specs;
}

const char* frameType(uint32_t v) {
    static const char* names[] = {"scroll", "static", "animated", "slowScroll", "bounce"};
    return v < 5 ? names[v] : "";
}

// A body texture (theme.py _texture) at its stored size; w x h is the part shown.
bool readImage(const std::vector<uint8_t>& d, uint32_t off, const char* format, int bpp, uint32_t texW, uint32_t texH,
               uint32_t w, uint32_t h, ThemeImage* out) {
    const size_t need = (size_t)texW * texH * bpp;
    if (!off || off + need > d.size()) return false;
    std::string err;
    if (!decodePicaTexture(d.data() + off, need, texW, texH, format, &out->rgba, &err)) return false;
    out->texW = texW;
    out->texH = texH;
    out->w = w;
    out->h = h;
    return true;
}

}  // namespace

std::vector<ThemeFile> listSdThemes(const std::string& root) {
    std::vector<ThemeFile> out;
    DIR* d = opendir(root.c_str());
    if (!d) return out;
    while (dirent* e = readdir(d)) {
        if (e->d_name[0] == '.') continue;
        const std::string dir = root + "/" + e->d_name;
        struct stat st;
        if (stat((dir + "/body_LZ.bin").c_str(), &st) != 0 || !S_ISREG(st.st_mode)) continue;
        out.push_back({e->d_name, dir});
    }
    closedir(d);
    std::sort(out.begin(), out.end(), [](const ThemeFile& a, const ThemeFile& b) { return strcasecmp(a.name.c_str(), b.name.c_str()) < 0; });
    return out;
}

bool loadThemeBody(const std::string& dir, ThemeBody* out, std::string* error) {
    auto fail = [&](const char* why) { if (error) *error = why; return false; };
    std::vector<uint8_t> raw;
    {
        FILE* f = fopen((dir + "/body_LZ.bin").c_str(), "rb");
        if (!f) return fail("no body_LZ.bin");
        fseek(f, 0, SEEK_END);
        const long n = ftell(f);
        fseek(f, 0, SEEK_SET);
        if (n <= 0 || n > 8 * 1024 * 1024) { fclose(f); return fail("body size"); }
        raw.resize((size_t)n);
        const bool ok = fread(raw.data(), 1, raw.size(), f) == raw.size();
        fclose(f);
        if (!ok) return fail("read error");
    }
    std::vector<uint8_t> d;
    if (raw[0] == 0x11 || raw[0] == 0x10) {
        if (!lzDecompress(raw.data(), raw.size(), &d)) return fail("LZ11 data is damaged");
    } else {
        d.swap(raw);
    }
    if (d.size() < 0xD0 || u32(d, 0) != 1) return fail("not a theme body");

    ThemeBody b;
    const size_t slash = dir.find_last_of('/');
    b.name = slash == std::string::npos ? dir : dir.substr(slash + 1);
    b.bgmEnabled = d[5] != 0;
    static const char* topDraw[] = {"none", "color", "colorTexture", "texture"};
    static const char* bottomDraw[] = {"none", "color", "texture", "texture"};
    const uint32_t td = u32(d, 0x0C), tf = u32(d, 0x10), tc = u32(d, 0x14), tt = u32(d, 0x18), te = u32(d, 0x1C);
    const uint32_t bd = u32(d, 0x20), bf = u32(d, 0x24), bt = u32(d, 0x28);
    b.top.drawType = td < 4 ? topDraw[td] : "none";
    b.bottomDrawType = bd < 4 ? bottomDraw[bd] : "none";
    b.topFrameType = frameType(tf);
    b.bottomFrameType = frameType(bf);
    // the top colour: 5 bytes for color, 7 for colorTexture (FUN_00243240)
    if ((td == 1 || td == 2) && tc && tc + 7 <= d.size()) {
        b.top.hasColor = true;
        b.top.rgb = {d[tc], d[tc + 1], d[tc + 2]};
        b.top.gradient = d[tc + 3];
        b.top.opacity = d[tc + 4];
        b.top.unknown05 = d[tc + 5];
        if (td == 2) b.top.unknown06 = d[tc + 6];
    }
    for (const BlockSpec& s : blockSpecs()) {
        if (!u32(d, s.enable)) continue;
        size_t off = u32(d, s.data);
        ThemeBlock& blk = b.blocks[s.id];
        blk.present = true;
        for (const char* f : s.fields) {
            if (f[0] == '%') { off += 4; continue; }
            if (f[0] == '#') { if (off < d.size()) blk.alpha = d[off]; off += 1; continue; }
            if (off + 3 > d.size()) break;
            blk.rgb.push_back({f, {d[off], d[off + 1], d[off + 2]}});
            off += 3;
        }
    }
    // textures (theme.py _textures); a frame type the menu has no image size for shows nothing
    if (td == 2) {
        readImage(d, tt, "L8", 1, 64, 64, 64, 64, &b.topPattern);
        readImage(d, te, "L8", 1, 64, 64, 64, 64, &b.topPatternExtra);
    } else if (td == 3) {
        if (tf == 0 || tf == 3) readImage(d, tt, "RGB565", 2, 1024, 256, 1008, 240, &b.topImage);
        else if (tf == 1) readImage(d, tt, "RGB565", 2, 512, 256, 412, 240, &b.topImage);
    }
    if (bd == 2 || bd == 3) {
        if (bf == 0 || bf == 3) readImage(d, bt, "RGB565", 2, 1024, 256, 1008, 240, &b.bottomImage);
        else if (bf == 1) readImage(d, bt, "RGB565", 2, 512, 256, 320, 240, &b.bottomImage);
        else if (bf == 2 || bf == 4) readImage(d, bt, "RGB565", 2, 1024, 256, 960, 240, &b.bottomImage);
    }
    if (u32(d, 0x3C)) {
        readImage(d, u32(d, 0x40), "RGB8", 3, 128, 64, 74, 64, &b.folderClosed);
        readImage(d, u32(d, 0x44), "RGB8", 3, 128, 64, 82, 64, &b.folderOpen);
    }
    if (u32(d, 0x50)) {
        readImage(d, u32(d, 0x54), "RGB8", 3, 64, 128, 36, 72, &b.fileLarge);
        readImage(d, u32(d, 0x58), "RGB8", 3, 32, 64, 25, 50, &b.fileSmall);
    }
    const uint32_t se = u32(d, 0xB8), seSize = u32(d, 0xBC), seOff = u32(d, 0xC0);
    if (se && seSize && seOff + (size_t)seSize <= d.size()) b.soundEffects.assign(d.begin() + seOff, d.begin() + seOff + seSize);
    if (b.bgmEnabled) {
        struct stat st;
        const std::string bgm = dir + "/bgm.bcstm";
        if (stat(bgm.c_str(), &st) == 0 && S_ISREG(st.st_mode)) b.bgmPath = bgm;
    }
    *out = std::move(b);
    return true;
}

const std::vector<ThemeBody>& builtinThemes() {
    static const std::vector<ThemeBody> themes = {
#include "NanoCtrThemeBuiltin.inc"
    };
    return themes;
}

namespace {
void rgbToHsv(float r, float g, float b, float* h, float* s, float* v) {
    const float mx = std::max({r, g, b}), mn = std::min({r, g, b}), d = mx - mn;
    *v = mx;
    *s = mx > 0 ? d / mx : 0;
    if (d <= 0) { *h = 0; return; }
    float hh;
    if (mx == r) hh = (g - b) / d;
    else if (mx == g) hh = 2 + (b - r) / d;
    else hh = 4 + (r - g) / d;
    hh /= 6;
    *h = hh < 0 ? hh + 1 : hh;
}

void hsvToRgb(float h, float s, float v, float* r, float* g, float* b) {
    h = h - std::floor(h);
    const float f = h * 6;
    const int i = (int)f % 6;
    const float fr = f - std::floor(f);
    const float p = v * (1 - s), q = v * (1 - s * fr), t = v * (1 - s * (1 - fr));
    switch (i) {
        case 0: *r = v; *g = t; *b = p; break;
        case 1: *r = q; *g = v; *b = p; break;
        case 2: *r = p; *g = v; *b = t; break;
        case 3: *r = p; *g = q; *b = v; break;
        case 4: *r = t; *g = p; *b = v; break;
        default: *r = v; *g = p; *b = q; break;
    }
}
}  // namespace

bool makeColorTheme(float r, float g, float b, ThemeBody* out) {
    float ha, sa, va;
    rgbToHsv(r, g, b, &ha, &sa, &va);
    const std::vector<ThemeBody>& themes = builtinThemes();   // Red, Blue, Yellow, Pink, Black
    if (sa < 0.15f) {
        if (va >= 0.5f) return false;   // white or light grey: the console's own look
        *out = themes[4];
        out->name = "Colour";
        return true;
    }
    const ThemeBody& base = themes[1];
    // the reference hue and saturation: the folder colour, the theme's signature
    const auto* ref = base.blocks[TB_FOLDER].field("main");
    float hr, sr, vr;
    rgbToHsv((*ref)[0] / 255.0f, (*ref)[1] / 255.0f, (*ref)[2] / 255.0f, &hr, &sr, &vr);
    const float dh = ha - hr, ks = sr > 0 ? sa / sr : 1;
    auto turn = [&](float x, float y, float z, float scale, std::array<uint8_t, 3>* o) {
        float h, s, v;
        rgbToHsv(x / scale, y / scale, z / scale, &h, &s, &v);
        float nr, ng, nb;
        hsvToRgb(h + dh, std::min(1.0f, s * ks), v, &nr, &ng, &nb);
        *o = {(uint8_t)std::lround(nr * scale), (uint8_t)std::lround(ng * scale), (uint8_t)std::lround(nb * scale)};
    };
    *out = base;
    out->name = "Colour";
    for (ThemeBlock& blk : out->blocks)
        for (auto& f : blk.rgb) turn(f.second[0], f.second[1], f.second[2], 255.0f, &f.second);
    // the wall colour block is in percent (FUN_00243240 scales it by 0.01)
    if (out->top.hasColor) turn(out->top.rgb[0], out->top.rgb[1], out->top.rgb[2], 100.0f, &out->top.rgb);
    return true;
}

ThemeManager::ThemeManager(Assets& assets) : A(assets) {
    A.onCreate = [this](LayoutInstance& l) { attach(l); };
    A.onDestroy = [this](LayoutInstance& l) { mLive.erase(&l); };
}

ThemeManager::~ThemeManager() {
    A.onCreate = nullptr;
    A.onDestroy = nullptr;
}

void ThemeManager::attach(LayoutInstance& l) {
    if (!bindingsByLayout().count(l.name())) return;
    mLive.insert(&l);
    apply(l);
}

void ThemeManager::setTheme(const ThemeBody* body) {
    mBody = body;   // always applied: a body may be loaded again into the same storage
    for (LayoutInstance* l : mLive) apply(*l);
}

void ThemeManager::setBlankFolder(bool folder) {
    const int block = folder ? TB_FOLDER_BACKGROUND : TB_BOTTOM_SOLID;
    if (block == mBlank) return;
    mBlank = block;
    for (LayoutInstance* l : mLive)
        if (l->name() == "LncIconSetSrc_00" || l->name() == "LncIconPickUpBlank_00") apply(*l);
}

// Writes every binding of the layout (manager.ts apply): the file colours first, then the
// blocks the theme defines.
void ThemeManager::apply(LayoutInstance& l) {
    auto found = bindingsByLayout().find(l.name());
    if (found == bindingsByLayout().end()) return;
    Layout& lay = l.layout();
    std::vector<int> mats;
    for (const Binding* b : found->second)
        for (const std::string& name : b->panes) {
            const Pane* p = lay.findPane(name);
            if (!p) continue;
            materialsOf(p, b->target, &mats);
            for (int m : mats) {
                if (m < 0 || m >= (int)lay.materials.size() || m >= (int)lay.fileColors.size()) continue;
                lay.materials[m].bufferColor = lay.fileColors[m].buffer;
                lay.materials[m].konst = lay.fileColors[m].konst;
            }
        }
    if (mBody) {
        for (const Binding* b : found->second) {
            const int id = b->blank ? mBlank : b->block;
            const ThemeBlock& block = mBody->blocks[id];
            if (!block.present) continue;
            for (const std::string& name : b->panes) {
                const Pane* p = lay.findPane(name);
                if (!p) continue;
                materialsOf(p, b->target, &mats);
                for (int m : mats) {
                    if (m < 0 || m >= (int)lay.materials.size()) continue;
                    Material& mat = lay.materials[m];
                    for (const auto& c : b->colors) {
                        const std::array<uint8_t, 3>* v = block.field(c.second);
                        if (!v) continue;
                        Rgba& dst = c.first == 0 ? mat.bufferColor : mat.konst[c.first - 1];
                        dst[0] = (*v)[0];
                        dst[1] = (*v)[1];
                        dst[2] = (*v)[2];
                    }
                    if (!b->alpha.empty() && block.alpha >= 0) mat.konst[0][3] = (float)block.alpha;
                }
            }
        }
    }
    l.refresh();
}

}  // namespace ctr
}  // namespace android
