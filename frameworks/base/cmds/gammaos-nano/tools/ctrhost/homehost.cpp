// Host harness for the 3DS home runtime (NanoCtrHome): a fake host fed from a flat item list
// (folder|kind|id|slot|name|icon), a script of inputs, and a 400x480 composite like the web
// screenshot tool (top screen above, bottom screen at x 40).
//
//   ctrhome <pack dir> <items.txt> <romfs dir> <out prefix> <script>
// script: "step N", "hold A|B|UP|DOWN|LEFT|RIGHT|X|Y|L|R N", "tap x y N" (bottom pixels),
//         "shot name" (writes <prefix>name.ppm)
#include <EGL/egl.h>
#include <EGL/eglext.h>
#include <GLES2/gl2.h>

#include <cstdio>
#include <cstdlib>
#include <algorithm>
#include <cstring>
#include <fstream>
#include <map>
#include <sstream>
#include <string>
#include <vector>

#include "../../NanoCtrHome.h"
#include "../../stb_image.h"

using namespace android::ctr;

bool initEgl(int w, int h);

struct FakeHost : CtrHomeHost {
    std::map<std::string, std::vector<HomeItem>> folders;
    std::map<std::string, std::string> icons;
    std::string romfs;
    std::vector<std::string> log;
    std::vector<HomeItem> items(const std::string& folderId) override { return folders[folderId]; }
    bool titleIcon(const HomeItem& item, std::vector<uint8_t>* rgba, int* w, int* h) override {
        std::string path = icons[item.id];
        bool flip = false;
        if (path == "GIFT") { path = "lyt/launcher/timg/LncIconGift_48.png"; flip = true; }
        if (path.empty()) return false;
        int c;
        stbi_set_flip_vertically_on_load(flip);
        unsigned char* px = stbi_load((romfs + "/" + path).c_str(), w, h, &c, 4);
        stbi_set_flip_vertically_on_load(0);
        if (!px) return false;
        rgba->assign(px, px + (size_t)(*w) * (*h) * 4);
        stbi_image_free(px);
        return true;
    }
    bool launch(const HomeItem& item) override { log.push_back("launch " + item.id); return true; }
    void appletBar(int b) override { log.push_back("bar " + std::to_string(b)); }
    HomeStatus status() override {
        HomeStatus s;
        s.wifiConnected = true;
        s.wifiSignal = 3;
        s.batteryLevel = 5;
        s.adapter = true;
        s.charging = true;
        return s;
    }
    std::string appletName(int b) override {
        static const char* n[6] = {"", "Game Notes", "Friend List", "Notifications", "Internet Browser", "Miiverse"};
        return b >= 1 && b <= 5 ? n[b] : "";
    }
    int playSound(const char* name) override { log.push_back(std::string("sound ") + name); return 1; }
};

static uint32_t buttonOf(const std::string& n) {
    if (n == "A") return CTR_A; if (n == "B") return CTR_B; if (n == "X") return CTR_X; if (n == "Y") return CTR_Y;
    if (n == "UP") return CTR_UP; if (n == "DOWN") return CTR_DOWN; if (n == "LEFT") return CTR_LEFT;
    if (n == "RIGHT") return CTR_RIGHT; if (n == "L") return CTR_L; if (n == "R") return CTR_R;
    if (n == "START") return CTR_START; if (n == "SELECT") return CTR_SELECT;
    return 0;
}

int main(int argc, char** argv) {
    if (argc < 6) { fprintf(stderr, "usage: ctrhome <packdir> <items.txt> <romfs> <outprefix> <script>\n"); return 2; }
    const int sc = getenv("CTR_SCALE") ? atoi(getenv("CTR_SCALE")) : 1;
    const float topW = getenv("CTR_TOPW") ? (float)atof(getenv("CTR_TOPW")) : 400.0f;
    const float botW = getenv("CTR_BOTW") ? (float)atof(getenv("CTR_BOTW")) : 320.0f;
    const int W = (int)(std::max(topW, botW + 80.0f * (botW <= 320 ? 1 : 0)) * sc);
    const int H = 480 * sc;
    if (!initEgl(W, H)) return 1;
    GLuint fbo, tex;
    glGenTextures(1, &tex);
    glBindTexture(GL_TEXTURE_2D, tex);
    glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, W, H, 0, GL_RGBA, GL_UNSIGNED_BYTE, nullptr);
    glGenFramebuffers(1, &fbo);
    glBindFramebuffer(GL_FRAMEBUFFER, fbo);
    glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, tex, 0);

    FakeHost host;
    host.romfs = argv[3];
    {
        std::ifstream f(argv[2]);
        std::string line;
        while (std::getline(f, line)) {
            if (line.empty()) continue;
            std::vector<std::string> p;
            std::stringstream ss(line);
            std::string tok;
            while (std::getline(ss, tok, '|')) p.push_back(tok);
            while (p.size() < 6) p.push_back("");
            HomeItem it;
            it.kind = p[1] == "folder" ? HomeItemKind::Folder : HomeItemKind::Title;
            it.id = p[2];
            it.slot = atoi(p[3].c_str());
            it.name = p[4];
            it.folderFilled = false;
            host.icons[it.id] = p[5];
            host.folders[p[0]].push_back(it);
        }
        for (auto& kv : host.folders)
            for (HomeItem& it : kv.second)
                if (it.kind == HomeItemKind::Folder) it.folderFilled = !host.folders[it.id].empty();
    }
    CtrHome home(argv[1], &host);
    home.setMinRootSlots(240);
    home.setScreenWidths(topW, botW);
    home.setLanguage("English");
    if (!home.load()) { fprintf(stderr, "load failed\n"); return 1; }
    const float rot[4] = {1, 0, 0, 1};
    auto shot = [&](const std::string& name) {
        glViewport(0, 0, W, H);
        glClearColor(0, 0, 0, 1);
        glClear(GL_COLOR_BUFFER_BIT);
        // top screen clear (banner.ts TOP_CLEAR) and the bottom screen area
        glEnable(GL_SCISSOR_TEST);
        glScissor(0, H - 240 * sc, (int)(topW * sc), 240 * sc);
        glClearColor(232 / 255.0f, 232 / 255.0f, 238 / 255.0f, 1);
        glClear(GL_COLOR_BUFFER_BIT);
        glDisable(GL_SCISSOR_TEST);
        float pt[16], pb[16];
        CtrHome::projection(pt, topW, 240, 0, 0, topW * sc, 240 * sc, W, H, rot);
        const float bx = (W / sc - botW) / 2;
        CtrHome::projection(pb, botW, 240, bx * sc, 240 * sc, botW * sc, 240 * sc, W, H, rot);
        home.drawTop(pt, 0, 0, W, H);
        home.drawBottom(pb, 0, 0, W, H);
        glFinish();
        std::vector<uint8_t> px((size_t)W * H * 4);
        glReadPixels(0, 0, W, H, GL_RGBA, GL_UNSIGNED_BYTE, px.data());
        std::string out = std::string(argv[4]) + name + ".ppm";
        FILE* f = fopen(out.c_str(), "wb");
        fprintf(f, "P6\n%d %d\n255\n", W, H);
        for (int y = H - 1; y >= 0; y--)
            for (int x = 0; x < W; x++) fwrite(&px[((size_t)y * W + x) * 4], 1, 3, f);
        fclose(f);
    };
    std::ifstream script(argv[5]);
    std::string line;
    while (std::getline(script, line)) {
        std::stringstream ss(line);
        std::string cmd;
        ss >> cmd;
        if (cmd == "step") { int n; ss >> n; home.step(n); }
        else if (cmd == "hold") {
            std::string b; int n; ss >> b >> n;
            home.setHeld(buttonOf(b));
            home.step(n);
            home.setHeld(0);
        } else if (cmd == "tap") {
            float x, y; int n; ss >> x >> y >> n;
            CtrTouch t; t.held = true; t.x = x - home.bottomWidth() / 2; t.y = 120 - y;
            home.setTouch(t);
            home.step(n);
            t.held = false;
            home.setTouch(t);
        } else if (cmd == "home") {
            home.pressHome();
        } else if (cmd == "shot") {
            std::string n; ss >> n;
            shot(n);
        }
    }
    for (auto& l : host.log) if (getenv("CTR_LOG")) fprintf(stderr, "%s\n", l.c_str());
    return 0;
}
