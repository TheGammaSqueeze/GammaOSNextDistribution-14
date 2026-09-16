// Nintendo DS ROM banner reader: the 32x32 icon and the title text every DS
// cartridge carries in its header banner, used by the DSi home theme as the
// carousel icon and game name when nothing scraped exists. Pure, bounded and
// exception-free: a bad file just returns false.
#pragma once
#include <cstdint>
#include <string>
#include <vector>

struct NdsBannerInfo {
    std::string title;           // UTF-8, the game's name line(s) without the publisher line
    std::vector<uint8_t> rgba;   // 32*32*4 bytes, index 0 of the palette rendered transparent
};

// path: a raw .nds, or a .zip whose (first .nds) entry is the ROM. Reads at most the
// header and the banner; a deflated zip entry is inflated only as far as the banner
// (capped at kNdsBannerMaxInflate bytes). Returns false for anything that does not
// validate (header CRC, banner CRC, offsets, sizes).
bool ndsReadBanner(const std::string& path, NdsBannerInfo& out);
