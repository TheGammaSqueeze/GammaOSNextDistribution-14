// Host test for NanoCtrRom (3DS ROM SMDH + CBMD banner extraction). Not part of the device build.
//
//   ctrrom extract <rom> <outdir> [region] [language]
//       Runs extractRom and dumps what it produced (model.cpk, smdh.bjs, icons, sound.wav, the
//       raw ExeFS icon / banner), then checks that loadOrExtract writes a cache file and that a
//       second call served from that cache returns the same assets.
//   ctrrom render <model.cpk> <out prefix> [--language <language.cpk>] [--wall <ctr3ds data dir>]
//                 [--scale n] frame...
//       Loads the pack with ModelResource::load and renders the banner through BannerCamera with
//       Renderer3d (Mesa EGL + GLES2), one PNG per animation frame. --language draws the CBMD
//       slot's own model (RomAssets::languageModelPack) as well, --wall the BannerBG wall.
#ifndef CTRROM_NO_GL
#include <EGL/egl.h>
#include <EGL/eglext.h>
#include <GLES2/gl2.h>
#endif
#include <sys/stat.h>
#include <sys/time.h>
#include <unistd.h>

#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <memory>
#include <string>
#include <vector>

#ifndef CTRROM_NO_GL
#include "../../NanoCtr3d.h"
#endif
#include "../../NanoCtrPack.h"
#include "../../NanoCtrRom.h"

using namespace android::ctr;

static double now() {
    timeval tv;
    gettimeofday(&tv, nullptr);
    return tv.tv_sec + tv.tv_usec / 1e6;
}

static bool writeFile(const std::string& path, const std::vector<uint8_t>& d) {
    FILE* f = fopen(path.c_str(), "wb");
    if (!f) { fprintf(stderr, "cannot write %s\n", path.c_str()); return false; }
    fwrite(d.data(), 1, d.size(), f);
    fclose(f);
    return true;
}

[[maybe_unused]] static bool readFile(const std::string& path, std::vector<uint8_t>* d) {
    FILE* f = fopen(path.c_str(), "rb");
    if (!f) return false;
    fseek(f, 0, SEEK_END);
    d->resize((size_t)ftell(f));
    fseek(f, 0, SEEK_SET);
    const size_t n = fread(d->data(), 1, d->size(), f);
    fclose(f);
    return n == d->size();
}

static bool sameAssets(const RomAssets& a, const RomAssets& b, std::string* why) {
    for (int k = 0; k < 16; k++)
        if (a.titles[k].shortDescription != b.titles[k].shortDescription || a.titles[k].longDescription != b.titles[k].longDescription ||
            a.titles[k].publisher != b.titles[k].publisher) { *why = "titles"; return false; }
    if (a.smdh != b.smdh) { *why = "smdh"; return false; }
    if (a.icon48 != b.icon48 || a.icon24 != b.icon24) { *why = "icons"; return false; }
    if (a.hasBanner != b.hasBanner || a.bannerSlot != b.bannerSlot || a.modelPack != b.modelPack ||
        a.languageModelPack != b.languageModelPack) { *why = "model pack"; return false; }
    if (a.programId != b.programId || a.productCode != b.productCode) { *why = "ids"; return false; }
    if (a.hasSound != b.hasSound || a.sound.pcm != b.sound.pcm || a.sound.sampleRate != b.sound.sampleRate ||
        a.sound.channels != b.sound.channels || a.sound.loop != b.sound.loop || a.sound.loopStart != b.sound.loopStart ||
        a.sound.loopEnd != b.sound.loopEnd || a.sound.frames != b.sound.frames || a.sound.encoding != b.sound.encoding) {
        *why = "sound";
        return false;
    }
    return true;
}

static int extract(int argc, char** argv) {
    const std::string rom = argv[2], out = argv[3];
    const std::string region = argc > 4 ? argv[4] : "USA", language = argc > 5 ? argv[5] : "English";
    mkdir(out.c_str(), 0755);
    std::vector<uint8_t> icon, banner;
    uint64_t pid = 0;
    std::string code, err;
    if (readRomExeFs(rom, &icon, &banner, &pid, &code, &err)) {
        writeFile(out + "/exefs_icon.bin", icon);
        writeFile(out + "/exefs_banner.bin", banner);
    }
    RomAssets a;
    double t0 = now();
    if (!extractRom(rom, &a, &err, region, language)) {
        printf("extract failed: %s\n", err.c_str());
        return 1;
    }
    const double tExtract = now() - t0;
    printf("program %016llx product %s\n", (unsigned long long)a.programId, a.productCode.c_str());
    for (int k = 0; k < 16; k++)
        if (!a.titles[k].shortDescription.empty())
            printf("  %-18s %s | %s | %s\n", kSmdhLanguages[k], a.titles[k].shortDescription.c_str(), a.titles[k].longDescription.c_str(),
                   a.titles[k].publisher.c_str());
    printf("banner %d slot '%s' error '%s' pack %zu bytes, language model pack %zu bytes\n", a.hasBanner, a.bannerSlot.c_str(),
           a.bannerError.c_str(), a.modelPack.size(), a.languageModelPack.size());
    printf("sound %d %s %d Hz %d ch %u frames loop %d %u..%u error '%s'\n", a.hasSound, a.sound.encoding.c_str(), a.sound.sampleRate,
           a.sound.channels, a.sound.frames, a.sound.loop, a.sound.loopStart, a.sound.loopEnd, a.soundError.c_str());
    printf("extract %.3f s\n", tExtract);
    writeFile(out + "/smdh.bjs", a.smdh);
    writeFile(out + "/icon48.rgba", a.icon48);
    writeFile(out + "/icon24.rgba", a.icon24);
    writeFile(out + "/icon48.png", encodePng(a.icon48.data(), 48, 48));
    writeFile(out + "/icon24.png", encodePng(a.icon24.data(), 24, 24));
    if (a.hasBanner) writeFile(out + "/model.cpk", a.modelPack);
    if (!a.languageModelPack.empty()) writeFile(out + "/language.cpk", a.languageModelPack);
    if (a.hasSound) writeFile(out + "/sound.wav", wavBytes(a.sound));
    FILE* tf = fopen((out + "/titles.txt").c_str(), "wb");
    if (tf) {
        for (int k = 0; k < 16; k++)
            fprintf(tf, "%s\t%s\t%s\t%s\n", kSmdhLanguages[k], a.titles[k].shortDescription.c_str(), a.titles[k].longDescription.c_str(),
                    a.titles[k].publisher.c_str());
        fclose(tf);
    }
    // cache round trip
    const std::string cache = out + "/cache";
    const std::string file = cache + "/" + cacheKey(rom) + ".cpk";
    unlink(file.c_str());
    RomAssets c1, c2;
    t0 = now();
    if (!loadOrExtract(rom, cache, &c1, &err, region, language)) { printf("loadOrExtract failed: %s\n", err.c_str()); return 1; }
    const double tFirst = now() - t0;
    struct stat st;
    if (stat(file.c_str(), &st) != 0) { printf("cache file %s was not written\n", file.c_str()); return 1; }
    t0 = now();
    if (!loadOrExtract(rom, cache, &c2, &err, region, language)) { printf("cached loadOrExtract failed: %s\n", err.c_str()); return 1; }
    const double tSecond = now() - t0;
    std::string why;
    if (!sameAssets(a, c1, &why)) { printf("first loadOrExtract differs from extractRom: %s\n", why.c_str()); return 1; }
    if (!sameAssets(a, c2, &why)) { printf("cached loadOrExtract differs from extractRom: %s\n", why.c_str()); return 1; }
    printf("cache %s (%lld bytes): miss %.3f s, hit %.3f s, identical\n", file.c_str(), (long long)st.st_size, tFirst, tSecond);
    return 0;
}

// ------------------------------------------------------------------ render
#ifndef CTRROM_NO_GL

static bool initEgl(int w, int h) {
    EGLDisplay dpy = EGL_NO_DISPLAY;
    auto getPlatformDisplay = (PFNEGLGETPLATFORMDISPLAYEXTPROC)eglGetProcAddress("eglGetPlatformDisplayEXT");
    if (getPlatformDisplay) dpy = getPlatformDisplay(EGL_PLATFORM_SURFACELESS_MESA, EGL_DEFAULT_DISPLAY, nullptr);
    if (dpy == EGL_NO_DISPLAY) dpy = eglGetDisplay(EGL_DEFAULT_DISPLAY);
    if (!eglInitialize(dpy, nullptr, nullptr)) { fprintf(stderr, "eglInitialize failed\n"); return false; }
    eglBindAPI(EGL_OPENGL_ES_API);
    const EGLint cfgAttr[] = {EGL_RENDERABLE_TYPE, EGL_OPENGL_ES2_BIT, EGL_SURFACE_TYPE, EGL_PBUFFER_BIT,
                              EGL_RED_SIZE, 8, EGL_GREEN_SIZE, 8, EGL_BLUE_SIZE, 8, EGL_ALPHA_SIZE, 8, EGL_NONE};
    EGLConfig cfg;
    EGLint n = 0;
    if (!eglChooseConfig(dpy, cfgAttr, &cfg, 1, &n) || n == 0) {
        const EGLint cfg2[] = {EGL_RENDERABLE_TYPE, EGL_OPENGL_ES2_BIT, EGL_NONE};
        if (!eglChooseConfig(dpy, cfg2, &cfg, 1, &n) || n == 0) { fprintf(stderr, "no EGL config\n"); return false; }
    }
    const EGLint ctxAttr[] = {EGL_CONTEXT_CLIENT_VERSION, 2, EGL_NONE};
    EGLContext ctx = eglCreateContext(dpy, cfg, EGL_NO_CONTEXT, ctxAttr);
    if (ctx == EGL_NO_CONTEXT) { fprintf(stderr, "no context\n"); return false; }
    const EGLint pb[] = {EGL_WIDTH, w, EGL_HEIGHT, h, EGL_NONE};
    EGLSurface surf = eglCreatePbufferSurface(dpy, cfg, pb);
    if (!eglMakeCurrent(dpy, surf, surf, ctx)) { fprintf(stderr, "makeCurrent failed\n"); return false; }
    return true;
}

static int render(int argc, char** argv) {
    const std::string packPath = argv[2], prefix = argv[3];
    std::string wallDir, languagePath;
    int scale = 2;
    std::vector<float> frames;
    for (int i = 4; i < argc; i++) {
        if (!strcmp(argv[i], "--wall") && i + 1 < argc) wallDir = argv[++i];
        else if (!strcmp(argv[i], "--language") && i + 1 < argc) languagePath = argv[++i];
        else if (!strcmp(argv[i], "--scale") && i + 1 < argc) scale = atoi(argv[++i]);
        else frames.push_back((float)atof(argv[i]));
    }
    if (frames.empty()) frames.push_back(0);
    const int w = 400 * scale, h = 240 * scale;
    if (!initEgl(w, h)) return 1;

    std::vector<uint8_t> bytes;
    if (!readFile(packPath, &bytes)) { fprintf(stderr, "cannot read %s\n", packPath.c_str()); return 1; }
    Pack pack;
    if (!pack.openMemory(std::move(bytes))) { fprintf(stderr, "not a CPK1 pack\n"); return 1; }
    auto res = std::make_shared<ModelResource>();
    // the pack itself is the shared texture source, so language textures the common model
    // does not list are found too
    if (!res->load(pack, &pack)) { fprintf(stderr, "ModelResource::load failed\n"); return 1; }
    printf("model %s: %zu materials, %zu meshes, %zu primitives, %zu textures, %zu bones, %zu skeletal + %zu material animations\n",
           res->name.c_str(), res->materials.size(), res->meshes.size(), res->primitives.size(), res->textures.size(), res->bones.size(),
           res->skeletal.size(), res->material.size());
    for (const auto& a : res->skeletal) printf("  skeletal %s (%g frames)\n", a.name.c_str(), a.frameCount);
    for (const auto& a : res->material) printf("  material %s (%g frames)\n", a.name.c_str(), a.frameCount);

    std::shared_ptr<ModelResource> langRes;
    Pack langPack;
    if (!languagePath.empty()) {
        std::vector<uint8_t> lb;
        if (!readFile(languagePath, &lb) || !langPack.openMemory(std::move(lb))) { fprintf(stderr, "cannot open %s\n", languagePath.c_str()); return 1; }
        langRes = std::make_shared<ModelResource>();
        if (!langRes->load(langPack, &langPack)) { fprintf(stderr, "language ModelResource::load failed\n"); return 1; }
        printf("language model %s: %zu primitives, %zu textures, %zu bones\n", langRes->name.c_str(), langRes->primitives.size(),
               langRes->textures.size(), langRes->bones.size());
    }

    std::shared_ptr<ModelResource> wallRes;
    Pack wallPack, texPack;
    if (!wallDir.empty() && wallPack.open(wallDir + "/model_BannerBG.cpk") && texPack.open(wallDir + "/model_Textures.cpk")) {
        wallRes = std::make_shared<ModelResource>();
        if (!wallRes->load(wallPack, &texPack)) wallRes.reset();
    }

    Camera3d cam;   // BannerCamera: eye (0, 1, 44.786), target (0, 1, 0), fovY 0.5236, aspect 5/3, near 26.5, far 1000
    cam.update();
    Renderer3d renderer;
    const float clear[3] = {232 / 255.0f, 232 / 255.0f, 238 / 255.0f};   // NanoCtrTop TOP_CLEAR
    GLuint readFbo;
    glGenFramebuffers(1, &readFbo);
    for (float frame : frames) {
        ModelInstance banner(res);
        banner.layer = 1;
        // titlebanner.ts plays the CBMD's "COMMON" animation in repeat mode
        if (!banner.play("COMMON", 1, 1, frame)) {
            for (const auto& a : res->skeletal) banner.play(a.name, 1, 1, frame);
            for (const auto& a : res->material) banner.play(a.name, 1, 1, frame);
        }
        banner.update(0);
        std::vector<ModelInstance*> models;
        std::unique_ptr<ModelInstance> wall;
        if (wallRes) {
            wall = std::make_unique<ModelInstance>(wallRes);
            wall->layer = 0;
            wall->play("BannerBG_Loop", 1, 1, 2 + frame);
            wall->update(0);
            models.push_back(wall.get());
        }
        models.push_back(&banner);
        std::unique_ptr<ModelInstance> lang;
        if (langRes) {
            lang = std::make_unique<ModelInstance>(langRes);
            lang->layer = 1;
            for (const auto& a : langRes->skeletal) lang->play(a.name, 1, 1, frame);
            for (const auto& a : langRes->material) lang->play(a.name, 1, 1, frame);
            lang->update(0);
            models.push_back(lang.get());
        }
        const GLuint tex = renderer.render(cam, models, w, h, clear);
        glBindFramebuffer(GL_FRAMEBUFFER, readFbo);
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, tex, 0);
        std::vector<uint8_t> px((size_t)w * h * 4), flipped(px.size());
        glReadPixels(0, 0, w, h, GL_RGBA, GL_UNSIGNED_BYTE, px.data());
        glBindFramebuffer(GL_FRAMEBUFFER, 0);
        size_t differ = 0;
        for (int y = 0; y < h; y++) {
            memcpy(&flipped[(size_t)y * w * 4], &px[(size_t)(h - 1 - y) * w * 4], (size_t)w * 4);
            for (int x = 0; x < w; x++) {
                const uint8_t* p = &px[((size_t)y * w + x) * 4];
                if (abs(p[0] - 232) > 2 || abs(p[1] - 232) > 2 || abs(p[2] - 238) > 2) differ++;
                flipped[((size_t)y * w + x) * 4 + 3] = 255;
            }
        }
        char name[64];
        snprintf(name, sizeof(name), "_f%03d.png", (int)frame);
        writeFile(prefix + name, encodePng(flipped.data(), w, h));
        printf("frame %g: %s%s, %.1f%% of the pixels not background, GL error 0x%x\n", frame, prefix.c_str(), name,
               100.0 * differ / ((double)w * h), glGetError());
    }
    return 0;
}

#endif  // CTRROM_NO_GL

int main(int argc, char** argv) {
    if (argc >= 4 && !strcmp(argv[1], "extract")) return extract(argc, argv);
#ifndef CTRROM_NO_GL
    if (argc >= 4 && !strcmp(argv[1], "render")) return render(argc, argv);
#endif
    fprintf(stderr,
            "usage: ctrrom extract <rom> <outdir> [region] [language]\n"
            "       ctrrom render <model.cpk> <out prefix> [--language <language.cpk>] [--wall <ctr3ds data dir>] [--scale n] frame...\n");
    return 2;
}
