// NanoCtrRom - 3DS ROM title metadata and banner extraction for the 3DS theme.
//
// Port of the 3ds-web pipeline's title export (/work/3ds/web/pipeline/build_assets.py
// export_title / export_banner and ctr/ncch.py, smdh.py, cbmd.py, lz.py, cgfx.py, texture.py,
// gltf.py, audio.py) plus the model packer of tools/ctr_pack.py (pack_model, BjWriter,
// write_pack). From a .3ds/.cci (NCSD), .cxi (NCCH), .cia or .3dsx (SMDH only, no banner),
// plain or inside Azahar's Z3DS compressed container (.zcci/.zcxi/.z3dsx), it reads only the
// headers and the ExeFS `icon` (SMDH) and `banner` (CBMD) files, and produces:
//   - the SMDH titles of every language and the 48x48 / 24x24 icons as RGBA8,
//   - the CBMD common CGFX as a model pack (CPK1 with cgfx / gltf / bin/0 / tex/<name>, the
//     same entries and encodings pack_model writes) that ModelResource::load reads, with the
//     textures of the chosen language slot replacing common textures of the same name
//     (menu/top/titlebanner.ts bannerTextures); load it with ModelResource::load(pack, &pack)
//     so a slot texture the common model does not list is found through the shared path too,
//   - the BCWAV banner sound decoded to 16 bit PCM.
// Only plaintext content is supported: no keys are available on the device, so encrypted
// NCCH / CIA content is reported as an error.
#pragma once

#include <cstdint>
#include <string>
#include <vector>

namespace android {
namespace ctr {

// SMDH title text of one language (smdh.py parse "titles"), UTF-8.
struct SmdhTitle {
    std::string shortDescription, longDescription, publisher;
};

// SMDH language slots in file order (smdh.py LANGUAGES).
extern const char* const kSmdhLanguages[16];
// The titles of an SMDH file (a theme's info.smdh): false when the file is not one.
bool readSmdhTitles(const std::string& path, SmdhTitle out[16]);
// The slot for a language's English name ("English", "French"...), -1 when it has none.
int smdhLanguageIndex(const std::string& englishName);

// The decoded banner sound (audio.py decode_cwav).
struct RomSound {
    std::vector<int16_t> pcm;   // interleaved, frames * channels
    int channels = 0;
    int sampleRate = 0;
    uint32_t frames = 0;
    bool loop = false;
    uint32_t loopStart = 0, loopEnd = 0;
    std::string encoding;       // pcm8 / pcm16 / dspAdpcm / imaAdpcm
};

struct RomAssets {
    uint64_t programId = 0;
    std::string productCode;
    // SMDH
    SmdhTitle titles[16];                 // indexed like kSmdhLanguages
    std::vector<uint8_t> smdh;            // BJS1 of smdh.to_json (titles, ratings, regions, flags...)
    std::vector<uint8_t> icon48, icon24;  // RGBA8, top row first (48*48*4, 24*24*4 bytes)
    // CBMD banner
    bool hasBanner = false;
    std::string bannerError;              // why the banner is missing, empty when the ROM has none
    std::string bannerSlot;               // CBMD slot whose textures were merged ("" = common only)
    std::vector<uint8_t> modelPack;       // CPK1 model pack (Pack::openMemory + ModelResource::load)
    // The chosen slot's own CGFX model as a second pack of the same format, empty when the slot
    // has no model. The web app (and modelPack) only take a slot's textures, yet some banners
    // (Kirby Planet Robobot) keep the whole localized title logo as a model in every slot. A
    // caller that wants it draws it next to the common model; its animations are named after
    // the slot's model (USA_EN for Kirby) instead of COMMON.
    std::vector<uint8_t> languageModelPack;
    bool hasSound = false;
    std::string soundError;
    RomSound sound;

    // Title text for a language name (kSmdhLanguages entry or a menu name such as "English",
    // case insensitive), falling back to English when that language is empty.
    const SmdhTitle& title(const std::string& language) const;
};

// Extracts everything from a ROM. `region` is the menu region (EUR, USA, JPN) and `language`
// the menu language (Japanese, English, French, German, Italian, Spanish, Dutch, Portuguese,
// Russian; others select English) used to pick the CBMD language slot. Returns false (with
// `error`) when the ROM cannot be read, is encrypted or has no SMDH; a banner that fails to
// convert leaves hasBanner false with bannerError set and still returns true.
bool extractRom(const std::string& romPath, RomAssets* out, std::string* error,
                const std::string& region = "USA", const std::string& language = "English");

// Cache file stem for a ROM: a hash of the path, size, mtime and the cache format version.
std::string cacheKey(const std::string& romPath);

// Like extractRom, but reuses <cacheDir>/<cacheKey>.cpk when present and writes it otherwise.
// The cache holds the model entries, every CBMD language slot's entries, `smdh` (BJS1),
// `icon48` / `icon24` (u32 width, u32 height, RGBA8), `sound` (a WAV file) and `soundInfo`, so
// one cache file serves every region and language.
bool loadOrExtract(const std::string& romPath, const std::string& cacheDir, RomAssets* out,
                   std::string* error, const std::string& region = "USA",
                   const std::string& language = "English");

// Lower level pieces (also used by the host test, tools/ctrrom).

// Reads the ExeFS `icon` and `banner` files of a ROM (either may come back empty).
bool readRomExeFs(const std::string& romPath, std::vector<uint8_t>* icon, std::vector<uint8_t>* banner,
                  uint64_t* programId, std::string* productCode, std::string* error);

// A CWAV sound (a banner or theme sound effect) and a BCSTM stream (a theme's BGM) as 16 bit PCM.
bool decodeCtrWave(const uint8_t* data, size_t size, RomSound* out, std::string* error);
bool decodeCtrStream(const uint8_t* data, size_t size, RomSound* out, std::string* error);

// LZ10 / LZ11 decompression (lz.py decompress).
bool lzDecompress(const uint8_t* data, size_t size, std::vector<uint8_t>* out);

// Decodes a tiled PICA200 texture ("RGB565", "RGB8", "L8", ... as texture.py names them) to
// RGBA8 with the same row order as the CGFX textures (texture.py decode).
bool decodePicaTexture(const uint8_t* raw, size_t size, uint32_t w, uint32_t h, const char* format,
                       std::vector<uint8_t>* rgba, std::string* error);

// Builds a CPK1 pack (ctr_pack.py write_pack) from named blobs; names are sorted.
std::vector<uint8_t> writeCpk(const std::vector<std::pair<std::string, std::vector<uint8_t>>>& entries);

// Encodes RGBA8 pixels (top row first) as a PNG.
std::vector<uint8_t> encodePng(const uint8_t* rgba, int width, int height);

// Encodes interleaved 16 bit PCM as a WAV file (audio.py wav_bytes, smpl chunk when looping).
std::vector<uint8_t> wavBytes(const RomSound& sound);

}  // namespace ctr
}  // namespace android
