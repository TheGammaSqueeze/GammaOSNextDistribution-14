// Host unit test for the Hangul dubeolsik composition automaton. Compares the
// decoded output codepoints against expected values (computed, not hand-typed
// UTF-8 bytes).
//   g++ -std=c++17 -I ../../frameworks/base/cmds/gammaos-nano test_hangul.cpp \
//       -o /tmp/osk_hangul && /tmp/osk_hangul

#include <cstdio>
#include <string>
#include <vector>
#include "NanoOskHangul.h"

using namespace android::hangul;

static int fails = 0;

static std::vector<uint32_t> decode(const std::string& s) {
    std::vector<uint32_t> out;
    size_t i = 0, n = s.size();
    while (i < n) {
        unsigned char c = (unsigned char)s[i];
        uint32_t cp; int len;
        if (c < 0x80) { cp = c; len = 1; }
        else if ((c & 0xE0) == 0xC0) { cp = c & 0x1F; len = 2; }
        else if ((c & 0xF0) == 0xE0) { cp = c & 0x0F; len = 3; }
        else { cp = c & 0x07; len = 4; }
        for (int k = 1; k < len && i + k < n; k++) cp = (cp << 6) | ((unsigned char)s[i + k] & 0x3F);
        out.push_back(cp); i += len;
    }
    return out;
}

static std::string typeJamo(std::initializer_list<int> jamo) {
    Composer c; std::string out;
    for (int j : jamo) out += c.input((uint32_t)j);
    out += c.flush();
    return out;
}

static void expect(const char* name, const std::string& got, std::initializer_list<uint32_t> want) {
    std::vector<uint32_t> g = decode(got), w(want);
    bool ok = (g == w);
    printf("%s %-10s got=[", ok ? "ok  " : "FAIL", name);
    for (auto cp : g) printf("%04X ", cp);
    printf("] want=[");
    for (auto cp : w) printf("%04X ", cp);
    printf("]\n");
    if (!ok) fails++;
}

int main() {
    const int G=0x3131, N=0x3134, D=0x3137, R=0x3139, B=0x3142,
              S=0x3145, NG=0x3147, J=0x3148, H=0x314E;
    const int A=0x314F, EO=0x3153, YEO=0x3155, O=0x3157, U=0x315C,
              EU=0x3161, I=0x3163;
    (void)U;

    expect("ga",       typeJamo({G,A}),            {0xAC00});          // 가
    expect("annyeong", typeJamo({NG,A,N,N,YEO,NG}),{0xC548,0xB155});   // 안녕
    expect("hangeul",  typeJamo({H,A,N,G,EU,R}),   {0xD55C,0xAE00});   // 한글
    expect("gabs",     typeJamo({G,A,B,S}),        {0xAC12});          // 값  (compound jong ㅄ)
    expect("gwa",      typeJamo({G,O,A}),          {0xACFC});          // 과  (compound jung ㅘ)
    expect("gaga",     typeJamo({G,A,G,A}),        {0xAC00,0xAC00});   // 가가 (jong ㄱ splits off)
    expect("ilgda",    typeJamo({NG,I,R,G,D,A}),   {0xC77D,0xB2E4});   // 읽다 (ㄺ compound, then ㄷ breaks it)
    expect("deobseun", typeJamo({D,EO,B,S,EU,N}),  {0xB365,0xC2A8});   // 덥슨 (ㅄ splits: ㅂ stays, ㅅ leads)
    expect("ganja",    typeJamo({G,A,N,J,A}),      {0xAC04,0xC790});   // 간자 (ㄵ splits: ㄴ stays, ㅈ leads)
    expect("lone_v",   typeJamo({A,A}),            {0x314F,0x314F});   // two bare vowels

    // Live composing + backspace.
    {
        Composer c; std::string pre;
        pre += c.input(H); pre += c.input(A); pre += c.input(N);   // 한
        expect("compose",  c.composing(), {0xD55C});               // 한
        c.backspace(); expect("bs1", c.composing(), {0xD558});     // 하
        c.backspace(); expect("bs2", c.composing(), {0x314E});     // ㅎ
        c.backspace();
        bool empty = !c.isComposing();
        printf("%s bs3_empty\n", empty ? "ok  " : "FAIL"); if (!empty) fails++;
        (void)pre;
    }

    printf("\n%s (%d failures)\n", fails == 0 ? "ALL PASS" : "FAILURES", fails);
    return fails ? 1 : 0;
}
