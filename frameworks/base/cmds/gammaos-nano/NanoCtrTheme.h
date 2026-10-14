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

struct ThemeBody {
    std::string name;
    ThemeTop top;
    std::string bottomDrawType;
    bool bgmEnabled = false;
    std::array<ThemeBlock, TB_COUNT> blocks;
};

// The built-in colour themes (ThemeEntry type 1): Red, Blue, Yellow, Pink, Black.
const std::vector<ThemeBody>& builtinThemes();

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
