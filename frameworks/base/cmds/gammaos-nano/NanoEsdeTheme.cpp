// GammaOS Nano - ES-DE theme parser (see NanoEsdeTheme.h, docs/THEME_ENGINE.md).
//
// Mirrors es-core/src/ThemeData: capabilities.xml is the validity gate; the system
// theme file is parsed into a view -> (type+name) -> element -> typed property model,
// resolving includes, ${variables}, and the base < variant < aspectRatio layering.
#define LOG_TAG "GammaOSNano"

#include "NanoEsdeTheme.h"

#include <tinyxml2.h>

#include <log/log.h>

#include <algorithm>
#include <cctype>
#include <cmath>
#include <cstdlib>
#include <cstring>
#include <unistd.h>

using tinyxml2::XMLDocument;
using tinyxml2::XMLElement;

namespace nanoesde {

// ------------------------------------------------------------------ element table

// Element type -> (property -> type). Mirrors ThemeData::sElementMap (MVP-relevant
// properties). Unknown elements/properties are skipped gracefully at parse time.
const std::map<std::string, PropType>* elementPropertyMap(const std::string& type) {
    static const std::map<std::string, std::map<std::string, PropType>> kMap = {
        {"image", {
            {"pos", PT_NORMALIZED_PAIR}, {"size", PT_NORMALIZED_PAIR},
            {"gameselector", PT_STRING},
            {"maxSize", PT_NORMALIZED_PAIR}, {"cropSize", PT_NORMALIZED_PAIR},
            {"cropPos", PT_NORMALIZED_PAIR}, {"origin", PT_NORMALIZED_PAIR},
            {"rotation", PT_FLOAT}, {"rotationOrigin", PT_NORMALIZED_PAIR},
            {"path", PT_PATH}, {"default", PT_PATH}, {"imageType", PT_STRING},
            {"tile", PT_BOOL}, {"tileSize", PT_NORMALIZED_PAIR},
            {"color", PT_COLOR}, {"colorEnd", PT_COLOR}, {"gradientType", PT_STRING},
            {"cornerRadius", PT_FLOAT}, {"brightness", PT_FLOAT}, {"opacity", PT_FLOAT},
            {"saturation", PT_FLOAT}, {"visible", PT_BOOL}, {"zIndex", PT_FLOAT},
            {"interpolation", PT_STRING},
            {"metadataElement", PT_BOOL}, {"flipHorizontal", PT_BOOL}, {"flipVertical", PT_BOOL},
        }},
        {"video", {
            {"pos", PT_NORMALIZED_PAIR}, {"size", PT_NORMALIZED_PAIR},
            {"maxSize", PT_NORMALIZED_PAIR}, {"origin", PT_NORMALIZED_PAIR},
            {"imageMaxSize", PT_NORMALIZED_PAIR}, {"cropSize", PT_NORMALIZED_PAIR},
            {"cropPos", PT_NORMALIZED_PAIR},
            {"path", PT_PATH}, {"default", PT_PATH}, {"defaultImage", PT_PATH},
            {"imageType", PT_STRING}, {"gameselector", PT_STRING},
            {"audio", PT_BOOL}, {"pillarboxes", PT_BOOL},
            {"color", PT_COLOR}, {"opacity", PT_FLOAT}, {"visible", PT_BOOL},
            {"zIndex", PT_FLOAT}, {"cornerRadius", PT_FLOAT}, {"videoCornerRadius", PT_FLOAT},
            {"imageCornerRadius", PT_FLOAT}, {"brightness", PT_FLOAT}, {"saturation", PT_FLOAT},
            {"interpolation", PT_STRING},
        }},
        {"animation", {
            {"pos", PT_NORMALIZED_PAIR}, {"size", PT_NORMALIZED_PAIR},
            {"maxSize", PT_NORMALIZED_PAIR}, {"origin", PT_NORMALIZED_PAIR},
            {"rotation", PT_FLOAT}, {"rotationOrigin", PT_NORMALIZED_PAIR},
            {"scaleFactor", PT_FLOAT}, {"stationary", PT_STRING},
            {"metadataElement", PT_BOOL}, {"iterationCount", PT_UINT},
            {"interpolation", PT_STRING}, {"cornerRadius", PT_FLOAT},
            {"color", PT_COLOR}, {"colorEnd", PT_COLOR}, {"gradientType", PT_STRING},
            {"brightness", PT_FLOAT}, {"saturation", PT_FLOAT},
            {"path", PT_PATH}, {"speed", PT_FLOAT}, {"direction", PT_STRING},
            {"opacity", PT_FLOAT}, {"visible", PT_BOOL}, {"zIndex", PT_FLOAT},
        }},
        {"badges", {
            {"pos", PT_NORMALIZED_PAIR}, {"size", PT_NORMALIZED_PAIR},
            {"origin", PT_NORMALIZED_PAIR}, {"horizontalAlignment", PT_STRING},
            {"direction", PT_STRING}, {"lines", PT_UINT}, {"itemsPerLine", PT_UINT},
            {"itemMargin", PT_NORMALIZED_PAIR}, {"slots", PT_STRING},
            {"badgeIconColor", PT_COLOR}, {"badgeIconColorEnd", PT_COLOR},
            {"controllerSize", PT_FLOAT}, {"folderLinkSize", PT_FLOAT},
            {"controllerIconColor", PT_COLOR}, {"folderLinkIconColor", PT_COLOR},
            {"opacity", PT_FLOAT}, {"visible", PT_BOOL}, {"zIndex", PT_FLOAT},
        }},
        {"text", {
            {"pos", PT_NORMALIZED_PAIR}, {"size", PT_NORMALIZED_PAIR},
            {"origin", PT_NORMALIZED_PAIR}, {"rotation", PT_FLOAT},
            {"text", PT_STRING}, {"systemdata", PT_STRING}, {"metadata", PT_STRING},
            {"defaultValue", PT_STRING}, {"container", PT_BOOL}, {"containerType", PT_STRING},
            {"containerScrollSpeed", PT_FLOAT}, {"containerStartDelay", PT_FLOAT},
            {"containerResetDelay", PT_FLOAT}, {"containerScrollGap", PT_FLOAT},
            {"fontPath", PT_PATH}, {"fontSize", PT_FLOAT},
            {"horizontalAlignment", PT_STRING}, {"verticalAlignment", PT_STRING},
            {"color", PT_COLOR}, {"backgroundColor", PT_COLOR},
            {"backgroundMargins", PT_NORMALIZED_PAIR}, {"backgroundCornerRadius", PT_FLOAT},
            {"letterCase", PT_STRING}, {"lineSpacing", PT_FLOAT},
            {"opacity", PT_FLOAT}, {"visible", PT_BOOL}, {"zIndex", PT_FLOAT},
            {"systemNameSuffix", PT_BOOL}, {"metadataElement", PT_BOOL},
        }},
        {"datetime", {
            {"pos", PT_NORMALIZED_PAIR}, {"size", PT_NORMALIZED_PAIR},
            {"origin", PT_NORMALIZED_PAIR}, {"metadata", PT_STRING},
            {"defaultValue", PT_STRING}, {"fontPath", PT_PATH}, {"fontSize", PT_FLOAT},
            {"horizontalAlignment", PT_STRING}, {"verticalAlignment", PT_STRING},
            {"color", PT_COLOR}, {"backgroundColor", PT_COLOR}, {"letterCase", PT_STRING},
            {"format", PT_STRING}, {"displayRelative", PT_BOOL},
            {"opacity", PT_FLOAT}, {"visible", PT_BOOL}, {"zIndex", PT_FLOAT},
        }},
        {"gamelistinfo", {
            {"pos", PT_NORMALIZED_PAIR}, {"size", PT_NORMALIZED_PAIR},
            {"origin", PT_NORMALIZED_PAIR}, {"fontPath", PT_PATH}, {"fontSize", PT_FLOAT},
            {"horizontalAlignment", PT_STRING}, {"verticalAlignment", PT_STRING},
            {"color", PT_COLOR}, {"backgroundColor", PT_COLOR},
            {"opacity", PT_FLOAT}, {"visible", PT_BOOL}, {"zIndex", PT_FLOAT},
        }},
        {"rating", {
            {"pos", PT_NORMALIZED_PAIR}, {"size", PT_NORMALIZED_PAIR},
            {"origin", PT_NORMALIZED_PAIR}, {"rotation", PT_FLOAT},
            {"hideIfZero", PT_BOOL}, {"color", PT_COLOR},
            {"filledPath", PT_PATH}, {"unfilledPath", PT_PATH}, {"overlay", PT_BOOL},
            {"opacity", PT_FLOAT}, {"visible", PT_BOOL}, {"zIndex", PT_FLOAT},
        }},
        {"carousel", {
            {"pos", PT_NORMALIZED_PAIR}, {"size", PT_NORMALIZED_PAIR},
            {"origin", PT_NORMALIZED_PAIR}, {"type", PT_STRING},
            {"staticImage", PT_PATH}, {"imageType", PT_STRING},
            {"defaultImage", PT_PATH}, {"defaultFolderImage", PT_PATH},
            {"maxItemCount", PT_FLOAT}, {"itemsBeforeCenter", PT_UINT},
            {"itemsAfterCenter", PT_UINT}, {"itemSize", PT_NORMALIZED_PAIR},
            {"itemScale", PT_FLOAT}, {"itemRotation", PT_FLOAT},
            {"itemRotationOrigin", PT_NORMALIZED_PAIR},
            {"horizontalOffset", PT_FLOAT}, {"verticalOffset", PT_FLOAT},
            {"selectedItemMargins", PT_NORMALIZED_PAIR},
            {"selectedItemOffset", PT_NORMALIZED_PAIR},
            {"imageInterpolation", PT_STRING},
            {"itemHorizontalAlignment", PT_STRING}, {"itemVerticalAlignment", PT_STRING},
            {"unfocusedItemOpacity", PT_FLOAT}, {"unfocusedItemDimming", PT_FLOAT},
            {"imageSaturation", PT_FLOAT}, {"unfocusedItemSaturation", PT_FLOAT},
            {"imageCornerRadius", PT_FLOAT},
            {"color", PT_COLOR}, {"colorEnd", PT_COLOR},
            {"text", PT_STRING}, {"textColor", PT_COLOR}, {"textBackgroundColor", PT_COLOR},
            {"textSelectedColor", PT_COLOR}, {"fontPath", PT_PATH}, {"fontSize", PT_FLOAT},
            {"letterCase", PT_STRING}, {"imageFit", PT_STRING}, {"imageCropPos", PT_NORMALIZED_PAIR},
            {"imageColor", PT_COLOR}, {"imageSelectedColor", PT_COLOR}, {"zIndex", PT_FLOAT},
        }},
        {"grid", {
            {"pos", PT_NORMALIZED_PAIR}, {"size", PT_NORMALIZED_PAIR},
            {"origin", PT_NORMALIZED_PAIR}, {"staticImage", PT_PATH}, {"imageType", PT_STRING},
            {"defaultImage", PT_PATH}, {"defaultFolderImage", PT_PATH},
            {"itemSize", PT_NORMALIZED_PAIR}, {"itemScale", PT_FLOAT},
            {"itemSpacing", PT_NORMALIZED_PAIR}, {"scaleInwards", PT_BOOL},
            {"fractionalRows", PT_BOOL},
            // image (cover) cell
            {"imageFit", PT_STRING}, {"imageCropPos", PT_NORMALIZED_PAIR}, {"imageRelativeScale", PT_FLOAT},
            {"imageCornerRadius", PT_FLOAT}, {"imageColor", PT_COLOR},
            {"imageColorEnd", PT_COLOR}, {"imageSelectedColor", PT_COLOR},
            {"imageBrightness", PT_FLOAT}, {"imageSaturation", PT_FLOAT},
            {"imageInterpolation", PT_STRING},
            // background plate
            {"backgroundImage", PT_PATH}, {"backgroundColor", PT_COLOR},
            {"backgroundColorEnd", PT_COLOR}, {"backgroundRelativeScale", PT_FLOAT},
            {"backgroundCornerRadius", PT_FLOAT},
            // selector (focused cell)
            {"selectorImage", PT_PATH}, {"selectorColor", PT_COLOR},
            {"selectorColorEnd", PT_COLOR}, {"selectorRelativeScale", PT_FLOAT},
            {"selectorCornerRadius", PT_FLOAT}, {"selectorLayer", PT_STRING},
            // text label + its plate
            {"text", PT_STRING}, {"textColor", PT_COLOR}, {"textSelectedColor", PT_COLOR},
            {"textRelativeScale", PT_FLOAT}, {"textBackgroundColor", PT_COLOR},
            {"textBackgroundCornerRadius", PT_FLOAT}, {"textHorizontalScrolling", PT_BOOL},
            // focus dimming/opacity + transitions
            {"unfocusedItemOpacity", PT_FLOAT}, {"unfocusedItemSaturation", PT_FLOAT},
            {"unfocusedItemDimming", PT_FLOAT}, {"itemTransitions", PT_STRING},
            {"interpolation", PT_STRING},
            {"rowTransitions", PT_STRING},
            {"fontPath", PT_PATH}, {"fontSize", PT_FLOAT}, {"lineSpacing", PT_FLOAT},
            {"letterCase", PT_STRING}, {"systemNameSuffix", PT_BOOL},
            {"visible", PT_BOOL}, {"zIndex", PT_FLOAT},
        }},
        {"textlist", {
            {"pos", PT_NORMALIZED_PAIR}, {"size", PT_NORMALIZED_PAIR},
            {"origin", PT_NORMALIZED_PAIR}, {"selectorWidth", PT_FLOAT},
            {"selectorHeight", PT_FLOAT}, {"selectorHorizontalOffset", PT_FLOAT},
            {"selectorVerticalOffset", PT_FLOAT}, {"selectorColor", PT_COLOR},
            {"selectorColorEnd", PT_COLOR}, {"selectorGradientType", PT_STRING},
            {"selectorImagePath", PT_PATH}, {"selectorImageTile", PT_BOOL},
            {"primaryColor", PT_COLOR}, {"secondaryColor", PT_COLOR},
            {"selectedColor", PT_COLOR}, {"selectedSecondaryColor", PT_COLOR},
            {"selectedBackgroundColor", PT_COLOR},
            {"selectedBackgroundCornerRadius", PT_FLOAT},
            {"selectedBackgroundMargins", PT_NORMALIZED_PAIR}, {"fontPath", PT_PATH},
            {"fontSize", PT_FLOAT}, {"horizontalAlignment", PT_STRING},
            {"horizontalMargin", PT_FLOAT}, {"letterCase", PT_STRING},
            {"lineSpacing", PT_FLOAT}, {"indicators", PT_STRING}, {"zIndex", PT_FLOAT},
        }},
        {"gameselector", {
            {"selection", PT_STRING}, {"gameCount", PT_UINT}, {"allowDuplicates", PT_BOOL},
        }},
        {"helpsystem", {
            {"pos", PT_NORMALIZED_PAIR}, {"origin", PT_NORMALIZED_PAIR},
            {"textColor", PT_COLOR}, {"textColorDimmed", PT_COLOR},
            {"iconColor", PT_COLOR}, {"iconColorDimmed", PT_COLOR},
            {"fontPath", PT_PATH}, {"fontSize", PT_FLOAT}, {"entrySpacing", PT_FLOAT},
            {"iconTextSpacing", PT_FLOAT}, {"letterCase", PT_STRING},
            {"entryRelativeScale", PT_FLOAT}, {"scope", PT_STRING},
            {"entries", PT_STRING},
            {"backgroundColor", PT_COLOR}, {"backgroundCornerRadius", PT_FLOAT},
            {"backgroundHorizontalPadding", PT_NORMALIZED_PAIR},
            {"backgroundVerticalPadding", PT_NORMALIZED_PAIR}, {"opacity", PT_FLOAT},
        }},
        {"systemstatus", {
            {"pos", PT_NORMALIZED_PAIR}, {"height", PT_FLOAT}, {"origin", PT_NORMALIZED_PAIR},
            {"fontPath", PT_PATH}, {"scope", PT_STRING},
            {"color", PT_COLOR}, {"backgroundColor", PT_COLOR},
            {"backgroundHorizontalPadding", PT_NORMALIZED_PAIR},
            {"backgroundVerticalPadding", PT_NORMALIZED_PAIR},
            {"backgroundCornerRadius", PT_FLOAT}, {"entrySpacing", PT_FLOAT},
            {"entries", PT_STRING}, {"textRelativeScale", PT_FLOAT}, {"opacity", PT_FLOAT},
        }},
        {"clock", {
            {"pos", PT_NORMALIZED_PAIR}, {"size", PT_NORMALIZED_PAIR},
            {"origin", PT_NORMALIZED_PAIR}, {"fontPath", PT_PATH}, {"fontSize", PT_FLOAT},
            {"horizontalAlignment", PT_STRING}, {"verticalAlignment", PT_STRING},
            {"scope", PT_STRING},
            {"color", PT_COLOR}, {"backgroundColor", PT_COLOR},
            {"backgroundHorizontalPadding", PT_NORMALIZED_PAIR},
            {"backgroundVerticalPadding", PT_NORMALIZED_PAIR},
            {"backgroundCornerRadius", PT_FLOAT}, {"format", PT_STRING}, {"opacity", PT_FLOAT},
        }},
        {"sound", {{"path", PT_PATH}}},
    };
    auto it = kMap.find(type);
    return it == kMap.end() ? nullptr : &it->second;
}

// The fixed ES-DE aspect-ratio identifiers -> aspect float (width/height).
static const std::map<std::string, float>& aspectRatioMap() {
    // Values copied verbatim from ES-DE ThemeData.cpp sAspectRatioMap so automatic
    // detection picks the same identifier ES-DE would on every panel.
    static const std::map<std::string, float> m = {
        {"16:9", 1.7777f}, {"16:9_vertical", 0.5625f},
        {"16:10", 1.6f}, {"16:10_vertical", 0.625f},
        {"3:2", 1.5f}, {"3:2_vertical", 0.6667f},
        {"4:3", 1.3333f}, {"4:3_vertical", 0.75f},
        {"5:3", 1.6667f}, {"5:3_vertical", 0.6f},
        {"5:4", 1.25f}, {"5:4_vertical", 0.8f},
        {"8:7", 1.1429f}, {"8:7_vertical", 0.875f},
        {"19.5:9", 2.1667f}, {"19.5:9_vertical", 0.4615f},
        {"20:9", 2.2222f}, {"20:9_vertical", 0.45f},
        {"21:9", 2.3703f}, {"21:9_vertical", 0.4219f},
        {"32:9", 3.5555f}, {"32:9_vertical", 0.2813f},
        {"1:1", 1.0f},
    };
    return m;
}

// ------------------------------------------------------------------ helpers

static std::string dirOf(const std::string& path) {
    size_t s = path.find_last_of('/');
    return s == std::string::npos ? std::string(".") : path.substr(0, s);
}

// Resolve a theme-relative path (leading "./" or bare) against baseDir -> absolute.
static std::string resolvePath(const std::string& p, const std::string& baseDir) {
    if (p.empty() || p[0] == '/') return p;
    std::string rel = p;
    if (rel.size() >= 2 && rel[0] == '.' && rel[1] == '/') rel = rel.substr(2);
    return baseDir + "/" + rel;
}

static bool fileExists(const std::string& p) { return access(p.c_str(), F_OK) == 0; }

static std::string trim(const std::string& s) {
    size_t a = 0, b = s.size();
    while (a < b && std::isspace((unsigned char)s[a])) a++;
    while (b > a && std::isspace((unsigned char)s[b - 1])) b--;
    return s.substr(a, b - a);
}

static void parseColor(const std::string& in, Prop& out) {
    std::string h = trim(in);
    if (!h.empty() && h[0] == '#') h = h.substr(1);
    if (h.size() == 6) h += "FF";
    if (h.size() != 8) { out.rgba = 0xFFFFFFFF; out.v[0]=out.v[1]=out.v[2]=out.v[3]=1.0f; return; }
    unsigned long val = strtoul(h.c_str(), nullptr, 16);
    out.rgba = (uint32_t)val;
    out.v[0] = ((val >> 24) & 0xFF) / 255.0f;
    out.v[1] = ((val >> 16) & 0xFF) / 255.0f;
    out.v[2] = ((val >> 8) & 0xFF) / 255.0f;
    out.v[3] = (val & 0xFF) / 255.0f;
}

static int splitFloats(const std::string& in, float* out, int max) {
    int n = 0;
    const char* p = in.c_str();
    while (*p && n < max) {
        while (*p && std::isspace((unsigned char)*p)) p++;
        if (!*p) break;
        char* end = nullptr;
        float f = strtof(p, &end);
        if (end == p) break;
        out[n++] = f;
        p = end;
    }
    return n;
}

// Split a comma/space separated list of names.
static std::vector<std::string> splitNames(const char* in) {
    std::vector<std::string> out;
    if (!in) return out;
    std::string s = in;
    for (size_t i = 0; i < s.size();) {
        size_t j = s.find_first_of(", ", i);
        std::string one = trim(s.substr(i, j == std::string::npos ? j : j - i));
        if (!one.empty()) out.push_back(one);
        if (j == std::string::npos) break;
        i = j + 1;
    }
    return out;
}

// Load a theme file's <theme> root, keeping the document alive in `docs`.
static XMLElement* loadThemeRoot(const std::string& path, std::vector<XMLDocument*>& docs) {
    if (!fileExists(path)) return nullptr;
    XMLDocument* doc = new XMLDocument();
    if (doc->LoadFile(path.c_str()) != tinyxml2::XML_SUCCESS) {
        ALOGW("esde: XML parse failed: %s", path.c_str());
        delete doc;
        return nullptr;
    }
    docs.push_back(doc);
    return doc->FirstChildElement("theme");
}

// ------------------------------------------------------------------ Theme

Theme::~Theme() {
    for (auto* d : mDocs) delete d;   // finalize() frees these on success; this covers error paths
}

std::string Theme::subst(const std::string& in, bool keepSystem) const {
    if (in.find("${") == std::string::npos) return in;
    std::string s = in;
    for (int pass = 0; pass < 8; pass++) {
        bool any = false;
        size_t pos = s.find("${");
        while (pos != std::string::npos) {
            size_t end = s.find('}', pos);
            if (end == std::string::npos) break;
            std::string name = s.substr(pos + 2, end - pos - 2);
            if (keepSystem && name.rfind("system.", 0) == 0) {
                pos = s.find("${", end + 1);   // leave ${system.*} raw for the renderer
                continue;
            }
            auto it = mVars.find(name);
            std::string rep = it == mVars.end() ? std::string() : it->second;
            s = s.substr(0, pos) + rep + s.substr(end + 1);
            any = true;
            pos = s.find("${", pos + rep.size());
        }
        if (!any) break;
    }
    return s;
}

// Pick the label to show for a <variant>/<colorScheme>. ES-DE allows one <label> per language and
// displays the one for the active UI language, defaulting to en_US (a theme like Art Book Next uses
// a single attribute-less <label> instead). nano's options menu is English, so prefer en_US, then
// en_GB, then an attribute-less label, then the first one seen. Returns "" if there is no label.
static std::string pickCapabilityLabel(XMLElement* node) {
    std::string enUS, enGB, noLang, first;
    for (XMLElement* l = node->FirstChildElement("label"); l;
         l = l->NextSiblingElement("label")) {
        if (!l->GetText()) continue;
        std::string txt = trim(l->GetText());
        if (first.empty()) first = txt;
        const char* lang = l->Attribute("language");
        if (!lang) { if (noLang.empty()) noLang = txt; }
        else if (std::string(lang) == "en_US" && enUS.empty()) enUS = txt;
        else if (std::string(lang) == "en_GB" && enGB.empty()) enGB = txt;
    }
    if (!enUS.empty()) return enUS;
    if (!enGB.empty()) return enGB;
    if (!noLang.empty()) return noLang;
    return first;
}

Capabilities Theme::parseCapabilities(const std::string& themeSetDir) {
    Capabilities caps;
    std::string path = themeSetDir + "/capabilities.xml";
    if (!fileExists(path)) return caps;
    XMLDocument doc;
    if (doc.LoadFile(path.c_str()) != tinyxml2::XML_SUCCESS) return caps;
    XMLElement* root = doc.FirstChildElement("themeCapabilities");
    if (!root) return caps;
    for (XMLElement* c = root->FirstChildElement(); c; c = c->NextSiblingElement()) {
        std::string tag = c->Name();
        if (tag == "variant") {
            Capability cap;
            cap.name = c->Attribute("name") ? c->Attribute("name") : "";
            cap.label = pickCapabilityLabel(c);
            for (XMLElement* ch = c->FirstChildElement(); ch; ch = ch->NextSiblingElement()) {
                std::string t = ch->Name();
                if (t == "selectable")
                    cap.selectable = ch->GetText() && trim(ch->GetText()) == "true";
                else if (t == "override") {
                    // ES-DE's <override> uses CHILD elements <trigger>/<mediaType>/<useVariant>
                    // (ThemeData::parseThemeCapabilities), NOT a trigger= attribute. Read them as
                    // children; an override with no useVariant is ignored, exactly as ES-DE does.
                    XMLElement* tg = ch->FirstChildElement("trigger");
                    XMLElement* uv = ch->FirstChildElement("useVariant");
                    std::string trig = (tg && tg->GetText()) ? trim(tg->GetText()) : "";
                    std::string use  = (uv && uv->GetText()) ? trim(uv->GetText()) : "";
                    if (!use.empty() && trig == "noVideos") {
                        cap.triggerNoVideos = use;
                    } else if (!use.empty() && trig == "noMedia") {
                        cap.triggerNoMedia = use;
                        // <mediaType> is a comma- or whitespace-separated list; ES-DE turns any
                        // whitespace into a separator then splits on comma. Match that.
                        XMLElement* mt = ch->FirstChildElement("mediaType");
                        if (mt && mt->GetText()) {
                            std::string csv = mt->GetText();
                            for (char& c : csv) if (isspace((unsigned char)c)) c = ',';
                            size_t start = 0;
                            while (start < csv.size()) {
                                size_t comma = csv.find(',', start);
                                std::string one = trim(csv.substr(
                                    start, comma == std::string::npos ? std::string::npos : comma - start));
                                if (!one.empty()) cap.triggerNoMediaTypes.push_back(one);
                                if (comma == std::string::npos) break;
                                start = comma + 1;
                            }
                        }
                        // ES-DE defaults an empty noMedia list to "miximage".
                        if (cap.triggerNoMediaTypes.empty())
                            cap.triggerNoMediaTypes.push_back("miximage");
                    }
                }
            }
            if (!cap.name.empty()) caps.variants.push_back(cap);
        } else if (tag == "colorScheme") {
            Capability cap;
            cap.name = c->Attribute("name") ? c->Attribute("name") : "";
            cap.label = pickCapabilityLabel(c);
            if (!cap.name.empty()) caps.colorSchemes.push_back(cap);
        } else if (tag == "themeName" && c->GetText()) {
            caps.themeName = trim(c->GetText());   // ES-DE display name (ThemeData.cpp:1228)
        } else if (tag == "fontSize" && c->GetText()) {
            caps.fontSizes.push_back(trim(c->GetText()));
        } else if (tag == "aspectRatio" && c->GetText()) {
            caps.aspectRatios.push_back(trim(c->GetText()));
        } else if (tag == "language" && c->GetText()) {
            caps.languages.push_back(trim(c->GetText()));
        } else if (tag == "transitions") {
            // A named view-transition profile: the per-view-change animation (slide / fade /
            // instant). ES-DE's setThemeTransitions applies the active profile's animations.
            auto parseXsAnim = [](const std::string& s) {
                if (s == "slide") return XsAnim::SLIDE;
                if (s == "fade")  return XsAnim::FADE;
                return XsAnim::INSTANT;
            };
            TransitionProfile prof;
            prof.name = c->Attribute("name") ? c->Attribute("name") : "";
            for (XMLElement* ch = c->FirstChildElement(); ch; ch = ch->NextSiblingElement()) {
                std::string t = ch->Name();
                const char* txt = ch->GetText();
                std::string v = txt ? trim(txt) : "";
                if (t == "systemToSystem")          prof.systemToSystem = parseXsAnim(v);
                else if (t == "systemToGamelist")   prof.systemToGamelist = parseXsAnim(v);
                else if (t == "gamelistToGamelist") prof.gamelistToGamelist = parseXsAnim(v);
                else if (t == "gamelistToSystem")   prof.gamelistToSystem = parseXsAnim(v);
            }
            if (!prof.name.empty()) caps.transitions.push_back(prof);
        }
    }
    caps.valid = true;
    return caps;
}

const Element* Theme::element(const std::string& view, const std::string& type,
                              const std::string& name) const {
    auto v = mViews.find(view);
    if (v == mViews.end()) return nullptr;
    auto e = v->second.elements.find(type + "\x1f" + name);
    return e == v->second.elements.end() ? nullptr : &e->second;
}

void Theme::walk(XMLElement* node, const std::string& baseDir, bool active,
                 int layer, int phase, int targetLayer, int depth) {
    if (!node || depth > 40) return;
    // ES-DE parses a file's <variables> before its <includes> (parseVariables runs before
    // parseIncludes), so a variable is available to every include path in the same file even when the
    // <variables> block appears AFTER the include that references it - artflix defines <rootpath>
    // below the two ${rootpath}/${system.theme} metadata includes that use it. nano walks children in
    // document order, so without this the includes resolved with rootpath still empty and the
    // per-system metadata file (systemDescription, systemReleaseYear, systemManufacturer, ...) never
    // loaded, blanking every ${...} that reads from it. Collect this node's own <variables> up front
    // in the variable-gathering pass so sibling includes below them resolve correctly.
    if (phase == 0 && active) {
        for (XMLElement* c = node->FirstChildElement(); c; c = c->NextSiblingElement()) {
            if (strcmp(c->Name(), "variables") != 0) continue;
            for (XMLElement* v = c->FirstChildElement(); v; v = v->NextSiblingElement())
                mVars[v->Name()] = v->GetText() ? std::string(v->GetText()) : std::string();
        }
    }
    for (XMLElement* c = node->FirstChildElement(); c; c = c->NextSiblingElement()) {
        std::string tag = c->Name();

        if (tag == "include") {
            if (!active) continue;
            const char* txt = c->GetText();
            if (!txt) continue;
            // ES-DE resolvePlaceholders() runs on the include text before the path is
            // resolved, so per-system includes like ./${system.theme}/colors.xml and
            // ./_inc/systems/_coversize/${systemCoverSize}.xml load correctly.
            std::string inc = resolvePath(subst(trim(txt), /*keepSystem=*/false), baseDir);
            XMLElement* iroot = loadThemeRoot(inc, mDocs);
            if (iroot)
                walk(iroot, dirOf(inc), active, layer, phase, targetLayer, depth + 1);

        } else if (tag == "variables") {
            if (phase == 0 && active)
                for (XMLElement* v = c->FirstChildElement(); v; v = v->NextSiblingElement()) {
                    const char* t = v->GetText();
                    mVars[v->Name()] = t ? std::string(t) : std::string();
                }

        } else if (tag == "variant") {
            bool sel = false;
            for (auto& one : splitNames(c->Attribute("name")))
                if (one == mSelVariant || one == "all") sel = true;
            walk(c, baseDir, active && sel, std::max(layer, 1), phase, targetLayer, depth + 1);

        } else if (tag == "colorScheme") {
            // name is a comma/space list; a block applies only if the selected scheme is
            // one of the listed tokens. ES-DE has no "all"/"custom" wildcard for
            // colorScheme (unlike variant): "custom" is a real, selectable scheme name in
            // themes like art-book-next, so treating it as a wildcard would leak that
            // block into every other scheme.
            bool sel = false;
            for (auto& one : splitNames(c->Attribute("name")))
                if (one == mSelColorScheme) sel = true;
            walk(c, baseDir, active && sel, layer, phase, targetLayer, depth + 1);

        } else if (tag == "fontSize") {
            bool sel = false;
            for (auto& one : splitNames(c->Attribute("name")))
                if (one == mSelFontSize || one == "all") sel = true;
            walk(c, baseDir, active && sel, layer, phase, targetLayer, depth + 1);

        } else if (tag == "language") {
            const char* nm = c->Attribute("name");
            bool sel = !nm || strcmp(nm, "en_US") == 0 || strcmp(nm, "en_GB") == 0;
            walk(c, baseDir, active && sel, layer, phase, targetLayer, depth + 1);

        } else if (tag == "aspectRatio") {
            // name is a comma/space list (e.g. "4:3,5:4"); match any listed token, the
            // same way ES-DE parseAspectRatios splits and compares each viewKey.
            bool sel = false;
            for (auto& one : splitNames(c->Attribute("name")))
                if (one == mSelAspect) sel = true;
            walk(c, baseDir, active && sel, std::max(layer, 2),
                 phase, targetLayer, depth + 1);

        } else if (tag == "view") {
            if (phase != 1 || !active || layer != targetLayer) continue;
            for (auto& vnameRaw : splitNames(c->Attribute("name"))) {
                // ES-DE "all" is a wildcard applying the block to both real views.
                std::vector<std::string> targets;
                if (vnameRaw == "all") targets = {"system", "gamelist"};
                else if (vnameRaw == "system" || vnameRaw == "gamelist") targets = {vnameRaw};
                else continue;
                for (const auto& vname : targets) {
                View& view = mViews[vname];
                for (XMLElement* e = c->FirstChildElement(); e; e = e->NextSiblingElement()) {
                    std::string etype = e->Name();
                    const auto* pmap = elementPropertyMap(etype);
                    if (!pmap) continue;                       // unknown element type
                    auto enames = splitNames(e->Attribute("name"));
                    if (enames.empty()) continue;              // name is mandatory
                    for (auto& ename : enames) {
                        Element& elem = view.elements[etype + "\x1f" + ename];
                        elem.type = etype; elem.name = ename;
                        for (XMLElement* p = e->FirstChildElement(); p; p = p->NextSiblingElement()) {
                            // Attribute-keyed custom icons (systemstatus customIcon,
                            // helpsystem customButtonIcon, badges customBadgeIcon): store each
                            // as a PATH prop keyed "<tag>:<attr>" so the renderer can look it up.
                            const char* pn = p->Name();
                            if (!strcmp(pn, "customIcon") || !strcmp(pn, "customButtonIcon") ||
                                !strcmp(pn, "customBadgeIcon")) {
                                const char* key = p->Attribute("icon");
                                if (!key) key = p->Attribute("button");
                                if (!key) key = p->Attribute("badge");
                                const char* raw = p->GetText();
                                if (key && raw && *raw) {
                                    Prop pr; pr.type = PT_PATH;
                                    pr.s = resolvePath(trim(subst(raw, /*keepSystem=*/true)), baseDir);
                                    elem.props[std::string(pn) + ":" + key] = pr;
                                }
                                continue;
                            }
                            auto pit = pmap->find(p->Name());
                            if (pit == pmap->end()) continue;  // unknown property
                            const char* raw = p->GetText();
                            // Paths keep ${system.*} raw so the renderer resolves them per
                            // system (slate uses a folder segment, Art Book Next a filename).
                            std::string val = trim(subst(raw ? raw : "",
                                                         /*keepSystem=*/pit->second == PT_PATH));
                            if (val.empty()) continue;
                            Prop pr; pr.type = pit->second;
                            switch (pit->second) {
                                case PT_NORMALIZED_PAIR: {
                                    float f[4] = {0,0,0,0}; splitFloats(val, f, 2);
                                    pr.v[0]=f[0]; pr.v[1]=f[1]; break; }
                                case PT_NORMALIZED_RECT: {
                                    float f[4] = {0,0,0,0}; splitFloats(val, f, 4);
                                    for (int k=0;k<4;k++) pr.v[k]=f[k]; break; }
                                case PT_COLOR: parseColor(val, pr); break;
                                case PT_PATH: pr.s = resolvePath(val, baseDir); break;
                                case PT_STRING: pr.s = val; break;
                                case PT_UINT: pr.u = (unsigned)strtoul(val.c_str(), nullptr, 10); break;
                                case PT_FLOAT: pr.f = strtof(val.c_str(), nullptr); break;
                                case PT_BOOL: pr.b = (val == "true" || val == "1"); break;
                            }
                            elem.props[p->Name()] = pr;
                        }
                    }
                }
                }
            }
        }
        // themeName / transitions / other tags: ignored
    }
}

void Theme::finalize() {
    auto defZ = [](const std::string& t) -> float {
        if (t == "image" || t == "video") return 30.0f;
        if (t == "animation" || t == "badges") return 35.0f;
        if (t == "text" || t == "datetime") return 40.0f;
        if (t == "gamelistinfo" || t == "rating") return 45.0f;
        if (t == "carousel" || t == "grid" || t == "textlist") return 50.0f;
        if (t == "helpsystem" || t == "systemstatus" || t == "clock") return 1000.0f;
        return 40.0f;
    };
    for (auto& kv : mViews) {
        View& v = kv.second;
        v.drawOrder.clear();
        v.drawOrder.reserve(v.elements.size());
        for (auto& e : v.elements) v.drawOrder.push_back(&e.second);
        std::stable_sort(v.drawOrder.begin(), v.drawOrder.end(),
                         [&](const Element* a, const Element* b) {
                             return a->zIndex(defZ(a->type)) < b->zIndex(defZ(b->type));
                         });
    }
    for (auto* d : mDocs) delete d;
    mDocs.clear();
}

bool Theme::load(const std::string& themeSetDir, const std::string& systemName,
                 const std::string& selectedVariant, const std::string& selectedColorScheme,
                 const std::string& selectedAspectRatio, const std::string& selectedFontSize,
                 float screenAspect, const std::map<std::string, std::string>& sysVars) {
    mValid = false; mError.clear(); mViews.clear(); mVars.clear();
    for (auto* d : mDocs) delete d;
    mDocs.clear();
    mRootDir = themeSetDir;

    mCaps = parseCapabilities(themeSetDir);
    if (!mCaps.valid) { mError = "missing or invalid capabilities.xml"; return false; }

    // Resolve the active view-transition animations (ES-DE ThemeData::setThemeTransitions with the
    // "automatic" setting: the first declared <transitions> profile, INSTANT when the theme declares
    // none). The renderer consumes these to slide / fade / cut between the system and gamelist views.
    mXsSysToGl = XsAnim::INSTANT; mXsGlToSys = XsAnim::INSTANT;
    if (!mCaps.transitions.empty()) {
        mXsSysToGl = mCaps.transitions.front().systemToGamelist;
        mXsGlToSys = mCaps.transitions.front().gamelistToSystem;
    }

    auto resolveOne = [](const std::string& want, const std::vector<Capability>& opts) {
        if (opts.empty()) return want;
        for (auto& o : opts) if (o.name == want) return want;
        return opts.front().name;
    };
    mSelVariant = resolveOne(selectedVariant, mCaps.variants);
    mSelColorScheme = resolveOne(selectedColorScheme, mCaps.colorSchemes);
    // ES-DE's default ThemeFontSize is "medium": ThemeData sorts a theme's declared sizes into the
    // canonical sSupportedFontSizes order (medium, large, small, x-large, x-small) and takes front(),
    // so the effective default is "medium" even when a theme's capabilities list them small-first
    // (art-book-next does). An unset/invalid selection must therefore fall back to "medium" when the
    // theme supports it - NOT the capabilities-file first entry, which would wrongly select the
    // <fontSize name="small"> blocks and their variables (e.g. helpGamelistViewEntries).
    mSelFontSize = selectedFontSize;
    bool fsOk = false;
    for (auto& f : mCaps.fontSizes) if (f == mSelFontSize) { fsOk = true; break; }
    if (!fsOk) {
        bool hasMedium = false;
        for (auto& f : mCaps.fontSizes) if (f == "medium") { hasMedium = true; break; }
        mSelFontSize = hasMedium || mCaps.fontSizes.empty() ? std::string("medium")
                                                            : mCaps.fontSizes.front();
    }
    // ES-DE (ThemeData.cpp) builds the effective aspect-ratio list as "automatic" followed by the
    // theme's declared ratios, then: if the requested ratio is declared it is used verbatim,
    // otherwise it falls back to the list front ("automatic"). Only a theme that declares at least
    // one ratio takes part; a theme with none gets no aspect include regardless of the request. A
    // request that names a ratio the theme does NOT declare must therefore auto-detect the closest
    // declared one, exactly as "automatic" does - not be kept literal (which would match no
    // <aspectRatio> block and silently render the base layout). This matters when a ratio persisted
    // from one theme is carried into another that lacks it.
    mSelAspect = selectedAspectRatio;
    if (!mCaps.aspectRatios.empty()) {
        bool declared = (mSelAspect == "automatic");
        for (auto& a : mCaps.aspectRatios) if (a == mSelAspect) { declared = true; break; }
        if (!declared) mSelAspect = "automatic";
        if (mSelAspect == "automatic") {
            // ES-DE seeds the selection AND the running best distance with "16:9", then replaces it
            // only with a declared ratio that is STRICTLY closer to the real screen aspect. So the
            // fallback stays 16:9 even when 16:9 is not declared: if every declared ratio is farther
            // from the screen than 16:9 is, ES-DE keeps "16:9" (no <aspectRatio> block matches -> the
            // base layout is used). Seeding best=inf instead (picking the nearest declared ratio
            // unconditionally) diverges on, e.g., a 16:9 panel with a theme that declares 16:10 but
            // not 16:9: ES-DE uses the base layout, we would wrongly apply the 16:10 include.
            std::string bestName = "16:9";
            auto it16 = aspectRatioMap().find("16:9");
            float best = (it16 != aspectRatioMap().end()) ? std::fabs(it16->second - screenAspect) : 1e9f;
            for (auto& a : mCaps.aspectRatios) {
                if (a == "automatic") continue;
                auto it = aspectRatioMap().find(a);
                if (it == aspectRatioMap().end()) continue;
                float d = std::fabs(it->second - screenAspect);
                if (d < best) { best = d; bestName = a; }
            }
            mSelAspect = bestName;
        }
    }

    mVars = sysVars;

    std::string themeFile = themeSetDir + "/" + systemName + "/theme.xml";
    if (!fileExists(themeFile)) themeFile = themeSetDir + "/theme.xml";
    if (!fileExists(themeFile)) { mError = "missing theme.xml"; return false; }

    XMLElement* root = loadThemeRoot(themeFile, mDocs);
    if (!root) { mError = "theme.xml parse error"; return false; }
    std::string baseDir = dirOf(themeFile);

    walk(root, baseDir, true, 0, /*phase=*/0, 0, 0);                  // collect variables
    for (int L = 0; L <= 2; L++)
        walk(root, baseDir, true, 0, /*phase=*/1, /*targetLayer=*/L, 0);   // base<variant<aspect

    finalize();
    mValid = true;
    return true;
}

}  // namespace nanoesde
