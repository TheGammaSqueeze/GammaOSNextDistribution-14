// Host unit test for the OSK locale->layout chain. Verifies oskPickLayout maps
// the way LeanbackKeyboardContainer.initKeyboards() does. Build + run:
//   g++ -std=c++17 -I ../../frameworks/base/cmds/gammaos-nano \
//       test_pick_layout.cpp -o /tmp/osk_pick && /tmp/osk_pick
//
// The function under test is copied verbatim from NanoOsk.cpp (it only depends
// on the OskKbId enum in NanoOskTypes.h and <cstring>), so this checks the
// chain logic without needing the full Android build.

#include <cstdio>
#include <cstring>
#include "NanoOskTypes.h"

using namespace android;

struct OskLayoutChoice { int abcId; int symId; };

// ---- verbatim copy of NanoOsk.cpp oskPickLayout ----
static OskLayoutChoice oskPickLayout(const char* code, const char* region) {
    const char* c = code ? code : "";
    const char* r = region ? region : "";
    auto M = [&](const char* lang, const char* country) -> bool {
        if (lang[0] && strcmp(c, lang) != 0) return false;
        if (country[0] && strcmp(r, country) != 0) return false;
        return true;
    };
    if (M("en", "GB")) return { OSK_KB_QWERTY_EN_GB, OSK_KB_SYM_EN_GB };
    if (M("en", "IN")) return { OSK_KB_QWERTY_EN_IN, OSK_KB_SYM_EN_IN };
    if (M("es", "ES") || M("gl", "ES") || M("eu", "ES"))
        return { OSK_KB_QWERTY_ES_EU, OSK_KB_SYM_EU };
    if (M("es", ""))   return { OSK_KB_QWERTY_ES_US, OSK_KB_SYM_US };
    if (M("az", ""))   return { OSK_KB_QWERTY_AZ, OSK_KB_SYM_EU };
    if (M("ca", ""))   return { OSK_KB_QWERTY_CA, OSK_KB_SYM_EU };
    if (M("da", ""))   return { OSK_KB_QWERTY_DA, OSK_KB_SYM_EU };
    if (M("et", ""))   return { OSK_KB_QWERTY_ET, OSK_KB_SYM_EU };
    if (M("fi", ""))   return { OSK_KB_QWERTY_FI, OSK_KB_SYM_EU };
    if (M("nb", ""))   return { OSK_KB_QWERTY_NB, OSK_KB_SYM_US };
    if (M("sv", ""))   return { OSK_KB_QWERTY_SV, OSK_KB_SYM_EU };
    if (M("en", "") || M("fr", "CA")) return { OSK_KB_QWERTY_US, OSK_KB_SYM_US };
    if (M("de", "CH") || M("it", "CH")) return { OSK_KB_QWERTZ_CH, OSK_KB_SYM_EU };
    if (M("de", "") || M("hr", "") || M("cs", "") || M("fr", "CH") ||
        M("it", "CH") || M("hu", "") || M("sr", "") || M("sl", "") || M("sq", ""))
        return { OSK_KB_QWERTZ, OSK_KB_SYM_EU };
    if (M("fr", "") || M("nl", "BE")) return { OSK_KB_AZERTY, OSK_KB_SYM_AZERTY };
    return { OSK_KB_QWERTY_EU, OSK_KB_SYM_EU };
}
// ----------------------------------------------------

static int fails = 0;
static void chk(const char* code, const char* region, int abc, int sym) {
    OskLayoutChoice c = oskPickLayout(code, region);
    bool ok = (c.abcId == abc && c.symId == sym);
    if (!ok) {
        fails++;
        printf("FAIL %s_%s -> abc=%d sym=%d (expected abc=%d sym=%d)\n",
               code, region, c.abcId, c.symId, abc, sym);
    } else {
        printf("ok   %s_%s -> abc=%d sym=%d\n", code, region, c.abcId, c.symId);
    }
}

int main() {
    // The 15 Nano UI locales (spec section 3.2 table).
    chk("en", "US", OSK_KB_QWERTY_US,    OSK_KB_SYM_US);
    chk("es", "ES", OSK_KB_QWERTY_ES_EU, OSK_KB_SYM_EU);
    chk("fr", "FR", OSK_KB_AZERTY,       OSK_KB_SYM_AZERTY);
    chk("de", "DE", OSK_KB_QWERTZ,       OSK_KB_SYM_EU);
    chk("it", "IT", OSK_KB_QWERTY_EU,    OSK_KB_SYM_EU);   // it_IT not special
    chk("pt", "BR", OSK_KB_QWERTY_EU,    OSK_KB_SYM_EU);
    chk("nl", "NL", OSK_KB_QWERTY_EU,    OSK_KB_SYM_EU);   // only nl_BE is azerty
    chk("ru", "RU", OSK_KB_QWERTY_EU,    OSK_KB_SYM_EU);
    chk("ja", "JP", OSK_KB_QWERTY_EU,    OSK_KB_SYM_EU);
    chk("ko", "KR", OSK_KB_QWERTY_EU,    OSK_KB_SYM_EU);
    chk("zh", "CN", OSK_KB_QWERTY_EU,    OSK_KB_SYM_EU);
    chk("zh", "TW", OSK_KB_QWERTY_EU,    OSK_KB_SYM_EU);
    chk("ar", "SA", OSK_KB_QWERTY_EU,    OSK_KB_SYM_EU);
    chk("tr", "TR", OSK_KB_QWERTY_EU,    OSK_KB_SYM_EU);
    chk("pl", "PL", OSK_KB_QWERTY_EU,    OSK_KB_SYM_EU);

    // Edge cases from the Leanback chain.
    chk("en", "GB", OSK_KB_QWERTY_EN_GB, OSK_KB_SYM_EN_GB);
    chk("en", "IN", OSK_KB_QWERTY_EN_IN, OSK_KB_SYM_EN_IN);
    chk("en", "AU", OSK_KB_QWERTY_US,    OSK_KB_SYM_US);   // generic en
    chk("es", "MX", OSK_KB_QWERTY_ES_US, OSK_KB_SYM_US);   // es non-ES
    chk("fr", "CA", OSK_KB_QWERTY_US,    OSK_KB_SYM_US);   // canadian french
    chk("fr", "CH", OSK_KB_QWERTZ,       OSK_KB_SYM_EU);   // swiss french
    chk("de", "CH", OSK_KB_QWERTZ_CH,    OSK_KB_SYM_EU);
    chk("it", "CH", OSK_KB_QWERTZ_CH,    OSK_KB_SYM_EU);
    chk("nl", "BE", OSK_KB_AZERTY,       OSK_KB_SYM_AZERTY);
    chk("da", "DK", OSK_KB_QWERTY_DA,    OSK_KB_SYM_EU);
    chk("fi", "FI", OSK_KB_QWERTY_FI,    OSK_KB_SYM_EU);
    chk("sv", "SE", OSK_KB_QWERTY_SV,    OSK_KB_SYM_EU);
    chk("nb", "NO", OSK_KB_QWERTY_NB,    OSK_KB_SYM_US);
    chk("az", "AZ", OSK_KB_QWERTY_AZ,    OSK_KB_SYM_EU);
    chk("ca", "ES", OSK_KB_QWERTY_CA,    OSK_KB_SYM_EU);
    chk("et", "EE", OSK_KB_QWERTY_ET,    OSK_KB_SYM_EU);
    chk("hr", "HR", OSK_KB_QWERTZ,       OSK_KB_SYM_EU);

    printf("\n%s (%d failures)\n", fails == 0 ? "ALL PASS" : "FAILURES", fails);
    return fails ? 1 : 0;
}
