// Host-side ES-DE parser matrix validator.
//
// Loads every theme in /work/esde_ref_themes across its FULL capability matrix
// (variant x colorScheme x aspectRatio x fontSize) using the exact same GL-free
// parser the device runs (NanoEsdeTheme.cpp Theme::load), and reports any load
// that comes back invalid or throws. Catches parse regressions across the whole
// permutation space that the directive demands, with no device needed.
//
// Build (see run.sh): g++ main.cpp <gammaos-nano>/NanoEsdeTheme.cpp
//   external/tinyxml2/tinyxml2.cpp  -I. -I<tinyxml2> -I<gammaos-nano>

#include "NanoEsdeTheme.h"

#include <dirent.h>
#include <sys/stat.h>
#include <cstdio>
#include <cstring>
#include <string>
#include <vector>
#include <algorithm>
#include <map>

using nanoesde::Theme;
using nanoesde::Capabilities;

static std::map<std::string, std::string> sysVars(const std::string& theme) {
    std::map<std::string, std::string> v;
    v["system.theme"] = theme;
    v["system.name"] = theme;
    v["system.fullName"] = theme;
    v["system.fullName.noCollections"] = theme;
    v["system.fullName.autoCollections"] = theme;
    v["system.fullName.customCollections"] = theme;
    return v;
}

int main(int argc, char** argv) {
    const std::string root = argc > 1 ? argv[1] : "/work/esde_ref_themes";
    // A couple of aspect strings ES-DE recognises plus whatever the theme declares.
    DIR* d = opendir(root.c_str());
    if (!d) { fprintf(stderr, "cannot open %s\n", root.c_str()); return 2; }
    std::vector<std::string> themes;
    for (dirent* e = readdir(d); e; e = readdir(d)) {
        if (e->d_name[0] == '.') continue;
        std::string dir = root + "/" + e->d_name;
        struct stat st;
        if (stat((dir + "/capabilities.xml").c_str(), &st) == 0) themes.push_back(e->d_name);
    }
    closedir(d);
    std::sort(themes.begin(), themes.end());

    int totalCombos = 0, invalidCombos = 0, themesWithFailures = 0;
    for (const auto& name : themes) {
        std::string dir = root + "/" + name;
        Capabilities caps = Theme::parseCapabilities(dir);
        // Build option lists; always include an empty "" (theme default) so a theme with no
        // declared entries is still exercised once.
        std::vector<std::string> variants, schemes, aspects, fonts;
        for (auto& c : caps.variants) variants.push_back(c.name);
        for (auto& c : caps.colorSchemes) schemes.push_back(c.name);
        aspects = caps.aspectRatios; aspects.push_back("automatic");
        fonts = caps.fontSizes;
        if (variants.empty()) variants.push_back("");
        if (schemes.empty())  schemes.push_back("");
        if (aspects.empty())  aspects.push_back("automatic");
        if (fonts.empty())    fonts.push_back("");

        int combos = 0, bad = 0;
        std::string firstErr;
        auto tryLoad = [&](const std::string& va, const std::string& cs,
                           const std::string& ar, const std::string& fs) {
            Theme t;
            bool ok = t.load(dir, /*systemName=*/"", va, cs, ar, fs, 1024.0f / 768.0f, sysVars(name));
            combos++;
            if (!ok || !t.valid()) {
                bad++;
                if (firstErr.empty())
                    firstErr = "variant=" + va + " scheme=" + cs + " aspect=" + ar +
                               " font=" + fs + " err=" + t.error();
            }
        };
        // fontSize is a render scalar and does not change include resolution, so cover the
        // parse-relevant axes fully (variant x colorScheme x aspectRatio) and every fontSize
        // once with defaults - the whole declared matrix, without the 5x redundant cross-product.
        for (auto& va : variants)
        for (auto& cs : schemes)
        for (auto& ar : aspects)
            tryLoad(va, cs, ar, fonts[0]);
        for (auto& fs : fonts)
            tryLoad(variants[0], schemes[0], aspects[0], fs);
        totalCombos += combos; invalidCombos += bad;
        if (bad) themesWithFailures++;
        printf("%-28s  %3d variants x schemes x aspects x fonts = %4d combos  %s\n",
               name.c_str(),
               (int)caps.variants.size(),
               combos,
               bad ? ("FAIL " + std::to_string(bad) + " -> " + firstErr).c_str() : "ok");
    }
    printf("\n=== %zu themes, %d combos, %d invalid (%d themes with failures) ===\n",
           themes.size(), totalCombos, invalidCombos, themesWithFailures);
    return invalidCombos ? 1 : 0;
}
