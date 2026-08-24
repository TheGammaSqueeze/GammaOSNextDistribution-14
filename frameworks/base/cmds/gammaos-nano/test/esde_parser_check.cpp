// Host-side validation / diagnostic for the nano ES-DE theme parser (NanoEsdeTheme).
//
// The parser is deliberately GL-free, so it can be compiled and run on a normal
// host (no device, no Android) against any real ES-DE theme set. This tool loads a
// theme for a given (system, variant, colorScheme, aspect, screenAspect) and dumps
// the resolved gamelist grid/textlist element so you can confirm the theme-resolution
// layering (base < variant < aspectRatio, comma-listed name matching, ${variable} and
// per-system include substitution, colorScheme selection) produces the values the
// theme's own XML intends. When pointed at the Art Book Next reference theme it also
// runs a set of assertions with the theme's documented expected numbers.
//
// Build + run on the host (from the repo root):
//   c++ -std=c++17 -DESDE_HOST_TEST \
//       -I frameworks/base/cmds/gammaos-nano \
//       -I frameworks/base/cmds/gammaos-nano/test \
//       -I external/tinyxml2 \
//       frameworks/base/cmds/gammaos-nano/test/esde_parser_check.cpp \
//       frameworks/base/cmds/gammaos-nano/NanoEsdeTheme.cpp \
//       external/tinyxml2/tinyxml2.cpp \
//       -o /tmp/esde_parser_check
//   /tmp/esde_parser_check <themeDir> [system] [variant] [colorScheme] [aspect] [screenAspect]
//
// (test/log/log.h here is a tiny host stub for the single ALOGW the parser uses.)
#include "NanoEsdeTheme.h"

#include <cmath>
#include <cstdio>
#include <cstring>
#include <map>
#include <string>

using namespace nanoesde;

static int gFail = 0;
static void check(const char* what, bool ok) {
    printf("  [%s] %s\n", ok ? "PASS" : "FAIL", what);
    if (!ok) gFail++;
}
static bool near(float a, float b) { return std::fabs(a - b) < 0.0005f; }

static std::map<std::string, std::string> sysVars(const std::string& sys) {
    std::map<std::string, std::string> v;
    v["system.theme"] = sys;
    v["system.name"] = sys;
    v["system.fullName"] = sys;
    return v;
}

// Load helper: fontSize is fixed at "medium" for the reference assertions.
static bool loadT(Theme& t, const char* dir, const std::string& sys, const std::string& variant,
                  const std::string& scheme, const std::string& aspect, float screenAspect) {
    return t.load(dir, sys, variant, scheme, aspect, "medium", screenAspect, sysVars(sys));
}

static const Element* firstOfType(const Theme& t, const char* view, const char* type) {
    const View* v = t.view(view);
    if (!v) return nullptr;
    for (const Element* e : v->drawOrder)
        if (e->type == type) return e;
    return nullptr;
}

static void dumpElement(const char* label, const Element* e) {
    if (!e) { printf("    %s: (absent)\n", label); return; }
    printf("    %s <%s name=\"%s\">\n", label, e->type.c_str(), e->name.c_str());
    printf("      pos=%.5f,%.5f size=%.5f,%.5f itemSize=%.7f,%.7f itemScale=%.3f\n",
           e->getPair("pos", 0, -1), e->getPair("pos", 1, -1),
           e->getPair("size", 0, -1), e->getPair("size", 1, -1),
           e->getPair("itemSize", 0, -1), e->getPair("itemSize", 1, -1),
           e->getF("itemScale", -1));
    printf("      fractionalRows=%d scaleInwards=%d unfocusedItemOpacity=%.3f\n",
           e->getB("fractionalRows", false), e->getB("scaleInwards", false),
           e->getF("unfocusedItemOpacity", -1));
    float c[4] = {-1,-1,-1,-1};
    if (e->getColor("primaryColor", c))
        printf("      primaryColor=%.2f,%.2f,%.2f a=%.2f\n", c[0], c[1], c[2], c[3]);
}

// Assertions with Art Book Next's documented expected numbers (see the theme's own
// _inc/systems/_coversize/*.xml comments). Only run when --abn / an art-book-next dir.
static void runAbnAssertions(const char* dir) {
    printf("\n== Art Book Next assertions ==\n");

    printf("\n[1] snes grid-cover @4:3 (systemCoverSize=4-3 via ${system.theme} include)\n");
    {
        Theme t;
        if (loadT(t, dir, "snes", "gamelist-grid-cover", "dark-screenshots", "automatic", 4.0f/3.0f)) {
            const Element* g = firstOfType(t, "gamelist", "grid");
            check("gamelist has a <grid>", g != nullptr);
            if (g) {
                check("itemSize == 4-3 coversize 4:3 block (0.33125 x 0.33125)",
                      near(g->getPair("itemSize", 0, -1), 0.33125f) &&
                      near(g->getPair("itemSize", 1, -1), 0.33125f));
                check("itemScale 1.2 (base grid)", near(g->getF("itemScale", 0), 1.2f));
                check("scaleInwards true", g->getB("scaleInwards", false));
                check("fractionalRows true", g->getB("fractionalRows", false));
                check("unfocusedItemOpacity 0.5", near(g->getF("unfocusedItemOpacity", 0), 0.5f));
                check("grid pos.y 0.11667", near(g->getPair("pos", 1, 0), 0.116666f));
                dumpElement("grid", g);
            }
        } else printf("    load failed: %s\n", t.error().c_str());
    }

    printf("\n[2] unknown system grid-cover @4:3 (systemCoverSize default 1-1)\n");
    {
        Theme t;
        if (loadT(t, dir, "zzz_unknown", "gamelist-grid-cover", "dark-screenshots", "automatic", 4.0f/3.0f)) {
            const Element* g = firstOfType(t, "gamelist", "grid");
            check("itemSize == 1-1 coversize 4:3 block (0.2476562 x 0.3302083)",
                  g && near(g->getPair("itemSize", 0, -1), 0.2476562f) &&
                  near(g->getPair("itemSize", 1, -1), 0.3302083f));
        }
    }

    printf("\n[3] aspect adaptivity: grid itemSize differs 4:3 vs 16:9\n");
    {
        Theme a, b;
        loadT(a, dir, "snes", "gamelist-grid-cover", "dark-screenshots", "automatic", 4.0f/3.0f);
        loadT(b, dir, "snes", "gamelist-grid-cover", "dark-screenshots", "automatic", 16.0f/9.0f);
        const Element* ga = firstOfType(a, "gamelist", "grid");
        const Element* gb = firstOfType(b, "gamelist", "grid");
        check("aspectRatio layer changes itemSize across screen aspects",
              ga && gb && !near(ga->getPair("itemSize", 0, -1), gb->getPair("itemSize", 0, -1)));
    }

    printf("\n[4] colorScheme is not a wildcard: dark vs light textlist colours differ\n");
    {
        Theme d, l;
        loadT(d, dir, "snes", "gamelist-list-metadata-cover", "dark-screenshots", "automatic", 4.0f/3.0f);
        loadT(l, dir, "snes", "gamelist-list-metadata-cover", "light-screenshots", "automatic", 4.0f/3.0f);
        const Element* xd = firstOfType(d, "gamelist", "textlist");
        const Element* xl = firstOfType(l, "gamelist", "textlist");
        float cd[4] = {-1,-1,-1,-1}, cl[4] = {-1,-1,-1,-1};
        bool hd = xd && xd->getColor("primaryColor", cd);
        bool hl = xl && xl->getColor("primaryColor", cl);
        check("dark unselected near-white (ffffff..)", hd && near(cd[0], 1.0f) && near(cd[1], 1.0f));
        check("light unselected dark-grey 444444", hl && near(cl[0], 0x44/255.0f) && near(cl[3], 1.0f));
    }

    // [5] Automatic aspect selection across the range (constraint: scale to any panel).
    // snes uses the 4-3 cover size, so each screen aspect must resolve that file's block:
    // 8:7 is the key case - the old aspect map lacked 8:7 and would misroute an 8:7 panel
    // to the 5:4 (4:3,5:4) block; the fix resolves the distinct 8:7 block.
    printf("\n[5] aspect adaptivity matrix (snes / 4-3 cover size)\n");
    {
        struct { const char* label; float aspect; float ix, iy; } cases[] = {
            {"16:9  1.7778", 16.0f/9.0f, 0.2476553f, 0.3300781f},
            {"4:3   1.3333", 4.0f/3.0f,  0.33125f,   0.33125f},
            {"8:7   1.1429", 8.0f/7.0f,  0.3309047f, 0.2833333f},
            {"1:1   1.0000", 1.0f,       0.3305556f, 0.2479167f},
            {"20:9  2.2222", 20.0f/9.0f, 0.1980769f, 0.3229167f},  // grouped 19.5:9,20:9,21:9
            {"32:9  3.5555", 32.0f/9.0f, 0.1228019f, 0.3273438f},
        };
        for (auto& c : cases) {
            Theme t;
            loadT(t, dir, "snes", "gamelist-grid-cover", "dark-screenshots", "automatic", c.aspect);
            const Element* g = firstOfType(t, "gamelist", "grid");
            float ix = g ? g->getPair("itemSize", 0, -1) : -1;
            float iy = g ? g->getPair("itemSize", 1, -1) : -1;
            char msg[96];
            snprintf(msg, sizeof(msg), "%s -> itemSize %.7f,%.7f", c.label, ix, iy);
            check(msg, g && near(ix, c.ix) && near(iy, c.iy));
        }
    }

    // [6] Parser fixes from the source-audit pass: the capability <label> is read (it was dropped,
    // so the options menu showed the raw id), the base<variant<aspectRatio merge carries a text
    // element's containerType, and a textlist's themed lineSpacing resolves (it was hardcoded 1.5).
    printf("\n[6] capability labels + containerType merge + textlist lineSpacing\n");
    {
        Capabilities caps = Theme::parseCapabilities(dir);
        const Capability* v0 = caps.variants.empty() ? nullptr : &caps.variants[0];
        check("variant[0] has a label", v0 && !v0->label.empty());
        check("ABN variant[0] label == 'List: Metadata & Boxart'",
              v0 && v0->label == "List: Metadata & Boxart");
        const Capability* cs = nullptr;
        for (auto& c : caps.colorSchemes)
            if (c.name == "dark-screenshots") cs = &c;
        check("colorScheme dark-screenshots label == 'Dark [Screenshots]'",
              cs && cs->label == "Dark [Screenshots]");

        Theme t;
        loadT(t, dir, "snes", "gamelist-list-metadata-cover", "dark-screenshots", "4:3", 4.0f/3.0f);
        const Element* dev = t.element("gamelist", "text", "developer");
        check("developer containerType=='horizontal' survives base->aspect merge",
              dev && dev->getS("containerType", std::string()) == "horizontal");
        const Element* tl = firstOfType(t, "gamelist", "textlist");
        check("textlist lineSpacing == 1.9444 (theme value, not the 1.5 default)",
              tl && near(tl->getF("lineSpacing", 1.5f), 1.94444444f));
    }
}

int main(int argc, char** argv) {
    if (argc < 2) {
        printf("usage: %s <themeDir> [system] [variant] [colorScheme] [aspect] [screenAspect]\n",
               argv[0]);
        printf("  themeDir is a directory containing capabilities.xml + theme.xml.\n");
        printf("  Point it at an art-book-next-es-de checkout to also run its assertions.\n");
        return 2;
    }
    const char* dir = argv[1];
    std::string sys = argc > 2 ? argv[2] : "snes";
    std::string variant = argc > 3 ? argv[3] : "gamelist-grid-cover";
    std::string scheme = argc > 4 ? argv[4] : "dark-screenshots";
    std::string aspect = argc > 5 ? argv[5] : "automatic";
    float screenAspect = argc > 6 ? (float)atof(argv[6]) : 4.0f/3.0f;

    printf("== esde_parser_check: %s [%s/%s/%s/%s @%.4f] ==\n",
           dir, sys.c_str(), variant.c_str(), scheme.c_str(), aspect.c_str(), screenAspect);

    Theme t;
    if (!t.load(dir, sys, variant, scheme, aspect, "medium", screenAspect, sysVars(sys))) {
        printf("LOAD FAILED: %s\n", t.error().c_str());
        return 1;
    }
    const Capabilities& c = t.capabilities();
    printf("capabilities: %zu variants, %zu colorSchemes, %zu aspectRatios, %zu fontSizes\n",
           c.variants.size(), c.colorSchemes.size(), c.aspectRatios.size(), c.fontSizes.size());
    dumpElement("gamelist grid", firstOfType(t, "gamelist", "grid"));
    dumpElement("gamelist textlist", firstOfType(t, "gamelist", "textlist"));
    dumpElement("system carousel", firstOfType(t, "system", "carousel"));

    // Auto-run the reference assertions if this looks like Art Book Next.
    if (strstr(dir, "art-book-next") || (argc > 7 && !strcmp(argv[7], "--abn")))
        runAbnAssertions(dir);

    printf("\n== %s (%d failures) ==\n", gFail ? "FAILURES PRESENT" : "OK", gFail);
    return gFail ? 1 : 0;
}
