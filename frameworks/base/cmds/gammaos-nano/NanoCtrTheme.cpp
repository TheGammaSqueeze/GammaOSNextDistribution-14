// NanoCtrTheme - see NanoCtrTheme.h.
#include "NanoCtrTheme.h"

#include <cstring>
#include <map>

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

const std::vector<ThemeBody>& builtinThemes() {
    static const std::vector<ThemeBody> themes = {
#include "NanoCtrThemeBuiltin.inc"
    };
    return themes;
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
    if (body == mBody) return;
    mBody = body;
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
