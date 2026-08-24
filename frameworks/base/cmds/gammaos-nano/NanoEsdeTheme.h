// GammaOS Nano - ES-DE theme model + parser (GL-free).
//
// Parses an ES-DE (EmulationStation Desktop Edition) theme set into an in-memory
// element model that mirrors upstream es-core/src/ThemeData: a theme is
//   view -> (type+name key) -> element -> property -> typed value.
// This class holds no GL state; NanoThemeEngine.cpp (NanoMenu::renderEsde*) draws
// from it. See docs/THEME_ENGINE.md for the format and support matrix.
#pragma once

#include <map>
#include <string>
#include <vector>
#include <cstdint>

namespace tinyxml2 { class XMLElement; class XMLDocument; }

namespace nanoesde {

// Property value types, matching ThemeData::ElementPropertyType.
enum PropType {
    PT_NORMALIZED_RECT,
    PT_NORMALIZED_PAIR,
    PT_PATH,
    PT_STRING,
    PT_COLOR,
    PT_UINT,
    PT_FLOAT,
    PT_BOOL,
};

// One parsed property value. The active field follows the declared type; COLOR also
// expands into v[0..3] as linear-ish 0..1 RGBA for convenience.
struct Prop {
    PropType type = PT_STRING;
    float v[4] = {0, 0, 0, 0};   // PAIR: v[0..1]; RECT: v[0..3]; COLOR: r,g,b,a in 0..1
    std::string s;               // STRING; PATH stored already resolved to an absolute path
    unsigned u = 0;              // UINT
    float f = 0.0f;              // FLOAT
    bool b = false;              // BOOL
    uint32_t rgba = 0;           // COLOR packed 0xRRGGBBAA
};

// One themed element (an <image>, <text>, <carousel>, ...). `type` is the XML tag.
struct Element {
    std::string type;
    std::string name;
    std::map<std::string, Prop> props;

    bool has(const char* k) const { return props.find(k) != props.end(); }
    const Prop* get(const char* k) const {
        auto it = props.find(k);
        return it == props.end() ? nullptr : &it->second;
    }
    float getF(const char* k, float def = 0.0f) const {
        auto p = get(k); return p ? p->f : def;
    }
    unsigned getU(const char* k, unsigned def = 0) const {
        auto p = get(k); return p ? p->u : def;
    }
    bool getB(const char* k, bool def = false) const {
        auto p = get(k); return p ? p->b : def;
    }
    const std::string& getS(const char* k, const std::string& def) const {
        auto p = get(k); return p ? p->s : def;
    }
    // Pair/rect component accessors (normalized 0..1).
    float getPair(const char* k, int i, float def) const {
        auto p = get(k); return (p && i >= 0 && i < 4) ? p->v[i] : def;
    }
    // Color as 0..1 RGBA; returns false if absent (caller keeps its default).
    bool getColor(const char* k, float out[4]) const {
        auto p = get(k);
        if (!p) return false;
        out[0] = p->v[0]; out[1] = p->v[1]; out[2] = p->v[2]; out[3] = p->v[3];
        return true;
    }
    float zIndex(float def) const { return getF("zIndex", def); }
    // Absolute path for a PATH property, or "" if absent.
    const std::string& getPath(const char* k) const {
        static const std::string empty;
        auto p = get(k);
        return p ? p->s : empty;
    }
};

// All elements declared for one view (system / gamelist). Keyed by "type\x1fname" so a
// same type+name merges (last-wins) while different types never collide.
struct View {
    std::map<std::string, Element> elements;
    // Elements in draw order (zIndex ascending, then insertion). Rebuilt by finalize().
    std::vector<const Element*> drawOrder;
};

// A capability option (variant / colorScheme / ...). `triggers` holds the noVideos /
// noMedia override variant name for variants that declare one (empty otherwise).
struct Capability {
    std::string name;
    std::string label;
    bool selectable = true;
    std::string triggerNoVideos;   // variant to switch to when a system has no videos
    std::string triggerNoMedia;    // variant to switch to when a system has no media
    // Media types the noMedia trigger checks (e.g. miximage, screenshot, cover, video). The
    // trigger fires only when NO game in the system has ANY of these, matching ES-DE's
    // ViewController per-system scan. Empty unless a noMedia override is declared.
    std::vector<std::string> triggerNoMediaTypes;
};

// ES-DE view-transition animation for a single view change (ViewTransitionAnimation).
enum class XsAnim { INSTANT, SLIDE, FADE };

// One named <transitions> profile from capabilities.xml: the animation used for each view
// change. ES-DE's ThemeData::setThemeTransitions resolves the active profile (the first declared
// one when the ThemeTransitions setting is "automatic", INSTANT when none) and applies these per
// transition type.
struct TransitionProfile {
    std::string name;
    XsAnim systemToSystem = XsAnim::INSTANT;
    XsAnim systemToGamelist = XsAnim::INSTANT;
    XsAnim gamelistToGamelist = XsAnim::INSTANT;
    XsAnim gamelistToSystem = XsAnim::INSTANT;
};

struct Capabilities {
    std::string themeName;   // <themeName> display name ("Slate"); empty falls back to the dir name
    std::vector<Capability> variants;
    std::vector<Capability> colorSchemes;
    std::vector<std::string> fontSizes;
    std::vector<std::string> aspectRatios;
    std::vector<std::string> languages;
    std::vector<TransitionProfile> transitions;   // <transitions> profiles (view animations)
    bool valid = false;   // true once a capabilities.xml was found + parsed
};

// A fully parsed ES-DE theme for one system, resolved for the selected variant /
// colorScheme / aspectRatio / fontSize. Load is cheap enough to run on a system change
// and MUST NOT run per frame.
class Theme {
public:
    Theme() = default;
    ~Theme();                            // frees any XML documents left after a failed load
    Theme(const Theme&) = delete;        // owns raw XMLDocument* in mDocs; non-copyable
    Theme& operator=(const Theme&) = delete;

    // Parse <themeSetDir>/capabilities.xml then the system theme file (the per-system
    // <themeSetDir>/<system>/theme.xml if present, else the root theme.xml), resolving
    // includes, variables (seeded from sysVars, e.g. system.theme, system.name),
    // variants, colorSchemes, fontSizes, languages and aspectRatios. Returns false and
    // sets error() on failure (missing capabilities.xml, missing theme file, XML error).
    bool load(const std::string& themeSetDir,
              const std::string& systemName,
              const std::string& selectedVariant,
              const std::string& selectedColorScheme,
              const std::string& selectedAspectRatio,   // "" or "automatic" -> nearest match
              const std::string& selectedFontSize,
              float screenAspect,                        // width/height, for automatic
              const std::map<std::string, std::string>& sysVars);

    bool valid() const { return mValid; }
    const std::string& error() const { return mError; }
    const Capabilities& capabilities() const { return mCaps; }
    const std::string& rootDir() const { return mRootDir; }
    // The aspect ratio actually selected for this load: the ThemeAspectRatio setting if the theme
    // declares it, else "automatic" resolved to the declared ratio nearest the screen (ES-DE's
    // closest-match, seeded from 16:9). Used for diagnostics / the load log.
    const std::string& selectedAspect() const { return mSelAspect; }

    // The active transition animation for entering / leaving a gamelist, resolved at load from the
    // theme's first declared <transitions> profile (ES-DE's "automatic"), INSTANT when none.
    XsAnim xsSystemToGamelist() const { return mXsSysToGl; }
    XsAnim xsGamelistToSystem() const { return mXsGlToSys; }

    bool hasView(const std::string& v) const { return mViews.find(v) != mViews.end(); }
    const View* view(const std::string& v) const {
        auto it = mViews.find(v);
        return it == mViews.end() ? nullptr : &it->second;
    }
    // Convenience: find one element by type+name in a view (nullptr if absent).
    const Element* element(const std::string& view, const std::string& type,
                           const std::string& name) const;

    // Parse just the capabilities file (validity gate + option lists) without the views.
    static Capabilities parseCapabilities(const std::string& themeSetDir);

    // ${var} substitution. keepSystem leaves ${system.*} placeholders raw (used for path
    // properties so the renderer can resolve them per system). Public for the parse walk.
    std::string subst(const std::string& in, bool keepSystem = false) const;

private:
    // Recursive parse walk. phase 0 collects variables, phase 1 processes <view> nodes
    // whose layer (0 base, 1 variant, 2 aspectRatio) equals targetLayer. Follows
    // <include>s and honors the selected variant/colorScheme/fontSize/aspectRatio.
    void walk(tinyxml2::XMLElement* node, const std::string& baseDir, bool active,
              int layer, int phase, int targetLayer, int depth);
    void finalize();   // build per-view drawOrder

    bool mValid = false;
    std::string mError;
    std::string mRootDir;
    std::string mSelVariant, mSelColorScheme, mSelAspect, mSelFontSize;
    Capabilities mCaps;
    XsAnim mXsSysToGl = XsAnim::INSTANT;   // resolved enter-gamelist transition (automatic profile)
    XsAnim mXsGlToSys = XsAnim::INSTANT;   // resolved leave-gamelist transition
    std::map<std::string, std::string> mVars;
    std::map<std::string, View> mViews;
    // Documents kept alive for the duration of load() (nodes point into them).
    std::vector<tinyxml2::XMLDocument*> mDocs;
};

// The element/property type table (mirrors ThemeData::sElementMap). Returns nullptr for
// an unknown element type; otherwise maps a property name to its PropType (or nullptr if
// the property is unknown for that element).
const std::map<std::string, PropType>* elementPropertyMap(const std::string& elementType);

}  // namespace nanoesde
