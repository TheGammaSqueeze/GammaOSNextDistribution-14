// NanoCtrTheme - the HOME Menu's themes: the colour blocks of a theme body written into the
// launcher layouts' materials (web theme/manager.ts + bindings.ts, FUN_001d3504), the top screen
// wall colour (top/scene3d.ts setTheme) and the folder banner colours.
//
// A layout is themed as it is created (Assets::onCreate) and again whenever the theme changes;
// colours a theme does not define come back from the layout's template, so switching back to
// the default theme restores every material exactly.
#pragma once

#include <array>
#include <cstdint>
#include <string>
#include <unordered_set>
#include <utility>
#include <vector>

#include "NanoCtrLayout.h"
#include "NanoCtrRom.h"

namespace android {
namespace ctr {

// A colour block of a theme body: named RGB fields and an optional alpha byte.
struct ThemeBlock {
    bool present = false;
    std::vector<std::pair<std::string, std::array<uint8_t, 3>>> rgb;
    int alpha = -1;
    const std::array<uint8_t, 3>* field(const std::string& name) const {
        for (const auto& f : rgb) if (f.first == name) return &f.second;
        return nullptr;
    }
};

// Theme body colour blocks, in the order the menu applies them (types.ts ThemeColors).
enum ThemeBlockId {
    TB_CURSOR, TB_FOLDER, TB_FILE, TB_ARROW_BUTTON, TB_ARROW, TB_OPEN_BUTTON, TB_CLOSE_BUTTON, TB_GAME_TEXT,
    TB_BOTTOM_SOLID, TB_BOTTOM_OUTER, TB_FOLDER_BACKGROUND, TB_FOLDER_ARROW, TB_BOTTOM_CORNER_BUTTON,
    TB_TOP_CORNER_BUTTON, TB_DEMO_TEXT, TB_COUNT
};

// Top screen background of a colour theme (types.ts ThemeTop): the wall colour and the
// gradient / opacity bytes (FUN_00243240 scales them by 0.01).
struct ThemeTop {
    std::string drawType;   // "none", "color", "colorTexture", "texture"
    bool hasColor = false;
    std::array<uint8_t, 3> rgb{};
    int gradient = 0, opacity = 0, unknown05 = 100, unknown06 = 100;
};

// A texture of a theme body decoded to RGBA8 in the CGFX row order (texture.py decode), at its
// stored size; the image is the w x h part the menu shows (theme.py _crop).
struct ThemeImage {
    uint32_t texW = 0, texH = 0, w = 0, h = 0;
    std::vector<uint8_t> rgba;
    bool empty() const { return rgba.empty(); }
};

struct ThemeBody {
    std::string name;
    ThemeTop top;
    std::string bottomDrawType;
    // how a background image follows the grid: scroll, static, animated, slowScroll, bounce
    std::string topFrameType, bottomFrameType;
    bool bgmEnabled = false;
    std::array<ThemeBlock, TB_COUNT> blocks;
    // custom themes (theme.py _textures): the top / bottom images, the wall patterns of a
    // colorTexture top, the folder and icon frame images, the sound effect section and the BGM
    ThemeImage topImage, bottomImage, topPattern, topPatternExtra, folderClosed, folderOpen, fileLarge, fileSmall;
    std::vector<uint8_t> soundEffects;
    std::string bgmPath;   // bgm.bcstm next to the body when the theme enables BGM
};

// A theme installed on the SD card the way theme managers lay them out: <root>/<name>/body_LZ.bin
// with an optional bgm.bcstm.
struct ThemeFile {
    std::string name;   // the folder name (the setting stores it)
    std::string dir;
    // info.smdh next to the body (theme managers write it): the theme's title per language
    bool hasTitles = false;
    SmdhTitle titles[16];
    // the title in a language (its English name), else English, else any, else the folder name
    std::string displayName(const std::string& language) const;
};
std::vector<ThemeFile> listSdThemes(const std::string& root);
// Parses a theme body (LZ11 compressed or not, theme.py parse) with its textures.
bool loadThemeBody(const std::string& dir, ThemeBody* out, std::string* error);

// The built-in colour themes (ThemeEntry type 1): Red, Blue, Yellow, Pink, Black.
const std::vector<ThemeBody>& builtinThemes();

// A colour theme for an accent colour (0..1 RGB), for nano's Colour setting: the console's own
// Blue theme turned to the accent's hue and saturation (Black for a dark grey accent), so every
// block keeps the designed relations (the cursor stays the complementary colour). False for a
// light grey accent, which is the default look.
bool makeColorTheme(float r, float g, float b, ThemeBody* out);

class ThemeManager {
public:
    explicit ThemeManager(Assets& assets);
    ~ThemeManager();

    // The theme to show (null = the default look). Re-themes every live layout.
    void setTheme(const ThemeBody* body);
    const ThemeBody* body() const { return mBody; }
    // Empty grid slots take the bottom colours on the root grid and the folder background
    // colours inside a folder (FUN_001dfb80).
    void setBlankFolder(bool folder);

private:
    void attach(LayoutInstance& l);
    void apply(LayoutInstance& l);
    Assets& A;
    const ThemeBody* mBody = nullptr;
    int mBlank = TB_BOTTOM_SOLID;
    std::unordered_set<LayoutInstance*> mLive;
};

}  // namespace ctr
}  // namespace android
