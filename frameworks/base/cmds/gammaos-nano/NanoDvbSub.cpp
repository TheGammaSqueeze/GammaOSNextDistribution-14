// NanoDvbSub - self-contained DVB subtitle decoder. See NanoDvbSub.h.
//
// Faithful translation of VLC's modules/codec/dvbsub.c (ETSI EN 300 743) plus a
// minimal MPEG-TS demuxer good enough to find and reassemble the subtitle PES.

#include "NanoDvbSub.h"

#include <cmath>
#include <cstdio>
#include <cstring>
#include <map>

#include <fcntl.h>
#include <unistd.h>

namespace {

// ---------------------------------------------------------------------------
// Big-endian MSB-first bit reader (mirrors VLC's bs_t for the bits we use).
// ---------------------------------------------------------------------------
class BitReader {
public:
    BitReader(const uint8_t* data, size_t len)
        : mStart(data), mEnd(data + len), mLen(len) {}

    // Total bytes in the buffer.
    size_t bytes() const { return mLen; }

    // Current bit position from the start (in bits).
    size_t pos() const { return mBitPos; }

    // Pointer to the start of the buffer (for object-data field slicing).
    const uint8_t* start() const { return mStart; }
    const uint8_t* end() const { return mEnd; }

    bool eof() const { return (mBitPos >> 3) >= mLen; }

    // Read up to 32 bits MSB-first. Reads past the end yield zero bits.
    uint32_t read(int n) {
        uint32_t v = 0;
        for (int i = 0; i < n; i++) {
            size_t byteIdx = mBitPos >> 3;
            int bit = 0;
            if (byteIdx < mLen) {
                int shift = 7 - (int)(mBitPos & 7);
                bit = (mStart[byteIdx] >> shift) & 1;
            }
            v = (v << 1) | (uint32_t)bit;
            mBitPos++;
        }
        return v;
    }

    void skip(int n) { mBitPos += n; }

    // Skip whole bytes.
    void skipBytes(size_t n) { mBitPos += n * 8; }

    // Align to the next byte boundary.
    void align() {
        if (mBitPos & 7) mBitPos = (mBitPos + 7) & ~size_t(7);
    }

private:
    const uint8_t* mStart;
    const uint8_t* mEnd;
    size_t mLen;
    size_t mBitPos = 0;
};

// ---------------------------------------------------------------------------
// DVB subtitle decoder state model (mirrors VLC's decoder_sys_t).
// ---------------------------------------------------------------------------

struct Color {  // a single CLUT entry stored as YCbCr + transparency
    uint8_t Y = 0, Cr = 0, Cb = 0, T = 0xff;
};

struct Clut {
    int id = 0;
    int version = -1;
    Color c2b[4];
    Color c4b[16];
    Color c8b[256];
};

struct ObjectDef {
    int id = 0;
    int type = 0;
    int x = 0;
    int y = 0;
};

struct Region {
    int id = 0;
    int version = -1;
    int width = 0;
    int height = 0;
    int levelComp = 0;
    int depth = 0;       // 1 = 2bpp, 2 = 4bpp, 3 = 8bpp
    int clut = 0;
    std::vector<uint8_t> pixbuf;  // width*height palette indices
    std::vector<ObjectDef> objects;
};

struct RegionDef {
    int id = 0;
    int x = 0;
    int y = 0;
};

struct Page {
    int version = -1;
    int timeout = 0;
    std::vector<RegionDef> regionDefs;
};

struct Display {
    int version = 0xff;
    int widthMinus1 = 720 - 1;
    int heightMinus1 = 576 - 1;
    bool windowed = false;
    int x = 0, y = 0, maxX = 0, maxY = 0;
};

// Segment types (EN 300-743 table 2).
enum {
    ST_PAGE_COMPOSITION   = 0x10,
    ST_REGION_COMPOSITION = 0x11,
    ST_CLUT_DEFINITION    = 0x12,
    ST_OBJECT_DATA        = 0x13,
    ST_DISPLAY_DEFINITION = 0x14,
    ST_ALTERNATE_CLUT     = 0x16,
    ST_ENDOFDISPLAY       = 0x80,
    ST_STUFFING           = 0xff,
};

// Page composition state (EN 300-743 7.2.1 table 3).
enum {
    PCS_STATE_ACQUISITION = 0x01,
    PCS_STATE_CHANGE      = 0x02,
};

// ---------------------------------------------------------------------------
// The decoder. One per stream; fed PES payloads with their PTS.
// ---------------------------------------------------------------------------
class DvbSubDecoder {
public:
    // Output produced for one completed display set.
    struct OutRegion {
        int x = 0, y = 0, w = 0, h = 0;
        std::vector<uint8_t> rgba;  // straight RGBA8, w*h*4
    };
    struct DisplaySet {
        double pts = 0;
        std::vector<OutRegion> regions;  // empty = clearing display set
    };

    DvbSubDecoder() {
        defaultClutInit();
        defaultDdsInit();
    }

    int displayWidth() const { return mDisplay.widthMinus1 + 1; }
    int displayHeight() const { return mDisplay.heightMinus1 + 1; }

    // Feed one DVB subtitle PES payload (starting at data_identifier) plus its
    // presentation time in seconds. Appends at most one DisplaySet to out.
    void decode(const uint8_t* buf, size_t len, double ptsSec,
                std::vector<DisplaySet>& out) {
        if (len < 2) return;

        // A change of PTS means a new display set; reset the DDS to SD default
        // (VLC does this: a new PTS is a good sign we may get a fresh DDS).
        if (mHasPts && ptsSec != mPts) defaultDdsInit();
        mPts = ptsSec;
        mHasPts = true;

        BitReader bs(buf, len);
        if (bs.read(8) != 0x20) return;  // data_identifier
        if (bs.read(8) != 0x00) return;  // subtitle_stream_id

        mPageUpdated = false;

        uint32_t sync = bs.read(8);
        while (sync == 0x0f) {
            decodeSegment(bs);
            sync = bs.read(8);
        }
        // End marker: (sync & 0x3f) == 0x3f, i.e. 0xff.

        // VLC renders when a page exists and was (re)composed in this packet.
        if (mHasPage && mPageUpdated) {
            DisplaySet ds;
            ds.pts = mPts;
            render(ds);
            out.push_back(std::move(ds));
        }
    }

private:
    // ----- default CLUTs (EN 300-743 section 10), as in VLC ------------------
    static int16_t rgbToY(int r, int g, int b) { return (int16_t)((77 * r + 150 * g + 29 * b) / 256); }
    static int16_t rgbToU(int r, int g, int b) { return (int16_t)((-44 * r - 87 * g + 131 * b) / 256); }
    static int16_t rgbToV(int r, int g, int b) { return (int16_t)((131 * r - 110 * g - 21 * b) / 256); }

    void defaultClutInit() {
        // 4-entry CLUT.
        for (int i = 0; i < 4; i++) {
            int R = 0, G = 0, B = 0, T = 0;
            if (!(i & 0x2) && !(i & 0x1)) T = 0xFF;
            else if (!(i & 0x2) && (i & 0x1)) R = G = B = 0xFF;
            else if ((i & 0x2) && !(i & 0x1)) R = G = B = 0;
            else R = G = B = 0x7F;
            mDefaultClut.c2b[i].Y  = (uint8_t)rgbToY(R, G, B);
            mDefaultClut.c2b[i].Cb = (uint8_t)rgbToV(R, G, B);  // note: VLC stores V into Cb here
            mDefaultClut.c2b[i].Cr = (uint8_t)rgbToU(R, G, B);  // and U into Cr (matches VLC source)
            mDefaultClut.c2b[i].T  = (uint8_t)T;
        }
        // 16-entry CLUT.
        for (int i = 0; i < 16; i++) {
            int R = 0, G = 0, B = 0, T = 0;
            if (!(i & 0x8)) {
                if (!(i & 0x4) && !(i & 0x2) && !(i & 0x1)) {
                    T = 0xFF;
                } else {
                    R = (i & 0x1) ? 0xFF : 0;
                    G = (i & 0x2) ? 0xFF : 0;
                    B = (i & 0x4) ? 0xFF : 0;
                }
            } else {
                R = (i & 0x1) ? 0x7F : 0;
                G = (i & 0x2) ? 0x7F : 0;
                B = (i & 0x4) ? 0x7F : 0;
            }
            mDefaultClut.c4b[i].Y  = (uint8_t)rgbToY(R, G, B);
            mDefaultClut.c4b[i].Cr = (uint8_t)rgbToV(R, G, B);
            mDefaultClut.c4b[i].Cb = (uint8_t)rgbToU(R, G, B);
            mDefaultClut.c4b[i].T  = (uint8_t)T;
        }
        // 256-entry CLUT: VLC memsets to 0xFF (opaque white-ish placeholder).
        for (int i = 0; i < 256; i++) {
            mDefaultClut.c8b[i].Y = mDefaultClut.c8b[i].Cr =
                mDefaultClut.c8b[i].Cb = mDefaultClut.c8b[i].T = 0xFF;
        }
    }

    void defaultDdsInit() {
        mDisplay.version = 0xff;  // an invalid version so it is always different
        mDisplay.widthMinus1 = 720 - 1;
        mDisplay.heightMinus1 = 576 - 1;
        mDisplay.windowed = false;
    }

    void freeAll() {
        mCluts.clear();
        mRegions.clear();
        mHasPage = false;
        mPage = Page();
    }

    Region* findRegion(int id) {
        for (auto& r : mRegions) if (r.id == id) return &r;
        return nullptr;
    }
    Clut* findClut(int id) {
        for (auto& c : mCluts) if (c.id == id) return &c;
        return nullptr;
    }

    // ----- segment dispatch -------------------------------------------------
    void decodeSegment(BitReader& bs) {
        int type = (int)bs.read(8);
        bs.read(16);  // page_id - we accept any page id (single-program file)
        int size = (int)bs.read(16);

        switch (type) {
            case ST_PAGE_COMPOSITION:   decodePageComposition(bs, size); break;
            case ST_REGION_COMPOSITION: decodeRegionComposition(bs, size); break;
            case ST_CLUT_DEFINITION:    decodeClut(bs, size); break;
            case ST_OBJECT_DATA:        decodeObject(bs, size); break;
            case ST_DISPLAY_DEFINITION: decodeDisplayDefinition(bs, size); break;
            case ST_ALTERNATE_CLUT:     bs.skipBytes(size); break;  // 10-bit/HDR not needed
            case ST_ENDOFDISPLAY:       bs.skipBytes(size); break;
            case ST_STUFFING:           bs.skipBytes(size); break;
            default:                    bs.skipBytes(size); break;
        }
    }

    void decodePageComposition(BitReader& bs, int segLen) {
        int timeout = (int)bs.read(8);
        int version = (int)bs.read(4);
        int state = (int)bs.read(2);
        bs.skip(2);  // reserved

        if (state == PCS_STATE_CHANGE) {
            // End of an epoch: reset the decoder buffers.
            freeAll();
        }

        // Version unchanged: nothing to do.
        if (mHasPage && mPage.version == version) {
            bs.skipBytes(segLen - 2);
            return;
        }

        // New version: drop the old region defs.
        mPage.regionDefs.clear();
        mPage.version = version;
        mPage.timeout = timeout;
        mHasPage = true;
        mPageUpdated = true;

        int n = (segLen - 2) / 6;
        if (n <= 0) return;
        mPage.regionDefs.reserve(n);
        for (int i = 0; i < n; i++) {
            RegionDef rd;
            rd.id = (int)bs.read(8);
            bs.skip(8);  // reserved
            rd.x = (int)bs.read(16);
            rd.y = (int)bs.read(16);
            mPage.regionDefs.push_back(rd);
        }
    }

    void decodeRegionComposition(BitReader& bs, int segLen) {
        int id = (int)bs.read(8);
        int version = (int)bs.read(4);

        Region* region = findRegion(id);
        if (region && region->version == version) {
            bs.skip(8 * (segLen - 1) - 4);
            return;
        }
        if (!region) {
            mRegions.emplace_back();
            region = &mRegions.back();
        }

        region->id = id;
        region->version = version;
        bool fill = bs.read(1) != 0;
        bs.skip(3);  // reserved

        int width = (int)bs.read(16);
        int height = (int)bs.read(16);
        int levelComp = (int)bs.read(3);
        int depth = (int)bs.read(3);
        bs.skip(2);  // reserved
        int clut = (int)bs.read(8);

        int bg8 = (int)bs.read(8);
        int bg4 = (int)bs.read(4);
        int bg2 = (int)bs.read(2);
        bs.skip(2);  // reserved

        region->objects.clear();

        // (Re)allocate the pixel buffer when the size changes.
        if (region->width != width || region->height != height) {
            size_t alloc = (size_t)width * (size_t)height;
            region->pixbuf.assign(alloc, 0);
            fill = !region->pixbuf.empty();
            // depth is implicitly reset to be re-set below
        }

        if (fill && !region->pixbuf.empty()) {
            int bg = (depth == 1) ? bg2 : (depth == 2) ? bg4 : bg8;
            std::memset(region->pixbuf.data(), bg, region->pixbuf.size());
        }

        region->width = width;
        region->height = height;
        region->levelComp = levelComp;
        region->depth = depth;
        region->clut = clut;

        // Object definitions follow (6 or 8 bytes each).
        int processed = 10;
        while (processed < segLen) {
            ObjectDef o;
            o.id = (int)bs.read(16);
            o.type = (int)bs.read(2);
            bs.skip(2);  // provider
            o.x = (int)bs.read(12);
            bs.skip(4);  // reserved
            o.y = (int)bs.read(12);
            processed += 6;
            if (o.type == 0x01 || o.type == 0x02) {  // basic char / composite string
                bs.read(8);  // fg pixel code
                bs.read(8);  // bg pixel code
                processed += 2;
            }
            region->objects.push_back(o);
        }
    }

    void decodeClut(BitReader& bs, int segLen) {
        int id = (int)bs.read(8);
        int version = (int)bs.read(4);

        Clut* clut = findClut(id);
        if (clut && clut->version == version) {
            bs.skip(8 * segLen - 12);
            return;
        }
        if (!clut) {
            mCluts.emplace_back();
            clut = &mCluts.back();
        }

        // Initialise to the default CLUT, then apply the entries (VLC resets
        // the CLUT to the default before parsing a new version).
        std::memcpy(clut->c2b, mDefaultClut.c2b, sizeof(clut->c2b));
        std::memcpy(clut->c4b, mDefaultClut.c4b, sizeof(clut->c4b));
        std::memcpy(clut->c8b, mDefaultClut.c8b, sizeof(clut->c8b));

        clut->id = id;
        clut->version = version;
        bs.skip(4);  // reserved

        int processed = 2;
        while (processed < segLen) {
            int cid = (int)bs.read(8);
            int type = (int)bs.read(3);
            bs.skip(4);
            bool fullRange = bs.read(1) != 0;
            uint8_t y, cr, cb, t;
            if (fullRange) {
                y  = (uint8_t)bs.read(8);
                cr = (uint8_t)bs.read(8);
                cb = (uint8_t)bs.read(8);
                t  = (uint8_t)bs.read(8);
                processed += 6;
            } else {
                y  = (uint8_t)(bs.read(6) << 2);
                cr = (uint8_t)(bs.read(4) << 4);
                cb = (uint8_t)(bs.read(4) << 4);
                t  = (uint8_t)(bs.read(2) << 6);
                processed += 4;
            }
            // VLC: luma 0 is treated as fully transparent.
            if (y == 0) { cr = cb = 0; t = 0xff; }

            if ((type & 0x04) && cid < 4) {
                clut->c2b[cid] = {y, cr, cb, t};
            }
            if ((type & 0x02) && cid < 16) {
                clut->c4b[cid] = {y, cr, cb, t};
            }
            if (type & 0x01) {
                clut->c8b[cid] = {y, cr, cb, t};
            }
        }
    }

    void decodeDisplayDefinition(BitReader& bs, int segLen) {
        int version = (int)bs.read(4);
        if (mDisplay.version == version) {
            bs.skip(8 * segLen - 4);
            return;
        }
        mDisplay.version = version;
        mDisplay.windowed = bs.read(1) != 0;
        bs.skip(3);  // reserved
        mDisplay.widthMinus1 = (int)bs.read(16);
        mDisplay.heightMinus1 = (int)bs.read(16);
        if (mDisplay.windowed) {
            mDisplay.x = (int)bs.read(16);
            mDisplay.maxX = (int)bs.read(16);
            mDisplay.y = (int)bs.read(16);
            mDisplay.maxY = (int)bs.read(16);
        } else {
            mDisplay.x = 0;
            mDisplay.maxX = mDisplay.widthMinus1;
            mDisplay.y = 0;
            mDisplay.maxY = mDisplay.heightMinus1;
        }
    }

    void decodeObject(BitReader& bs, int segLen) {
        int id = (int)bs.read(16);
        bs.skip(4);  // version
        int codingMethod = (int)bs.read(2);

        if (codingMethod > 1) {
            bs.skip(8 * (segLen - 2) - 6);
            return;
        }

        // Render only if some region references this object.
        bool needed = false;
        for (auto& r : mRegions) {
            for (auto& o : r.objects) if (o.id == id) { needed = true; break; }
            if (needed) break;
        }
        if (!needed) {
            bs.skip(8 * (segLen - 2) - 6);
            return;
        }

        bs.skip(1);  // non_modifying_colour_flag
        bs.skip(1);  // reserved

        if (codingMethod == 0x00) {
            int topLen = (int)bs.read(16);
            int botLen = (int)bs.read(16);
            // Field data follows immediately, byte-aligned at this point.
            const uint8_t* base = bs.start() + bs.pos() / 8;
            const uint8_t* topField = base;
            const uint8_t* botField = topField + topLen;

            bs.skip(8 * (segLen - 7));

            // Sanity check.
            if (segLen < topLen + botLen + 7) return;
            if (topField + topLen + botLen > bs.end()) return;

            for (auto& r : mRegions) {
                for (auto& o : r.objects) {
                    if (o.id != id) continue;
                    renderPdata(r, o.x, o.y, topField, topLen);
                    if (botLen) {
                        renderPdata(r, o.x, o.y + 1, botField, botLen);
                    } else {
                        // Duplicate the top field into the bottom field rows.
                        renderPdata(r, o.x, o.y + 1, topField, topLen);
                    }
                }
            }
        }
        // codingMethod == 1 (characters) is not produced by typical encoders and
        // is not rendered here (VLC stores the raw codes but cannot map them).
    }

    // Decode one field of pixel-code data into a region (mirrors VLC's
    // dvbsub_render_pdata + the 2/4/8bpp run-length decoders).
    void renderPdata(Region& region, int x, int y,
                     const uint8_t* field, int fieldLen) {
        if (region.pixbuf.empty()) return;
        if (y < 0 || x < 0 || y >= region.height || x >= region.width) return;

        BitReader bs(field, (size_t)fieldLen);
        int offset = 0;
        uint8_t* rowBase = region.pixbuf.data() + (size_t)y * region.width;

        while (!bs.eof()) {
            if (y >= region.height) return;
            uint8_t code = (uint8_t)bs.read(8);
            switch (code) {
                case 0x10:
                    pdata2bpp(bs, rowBase + x, region.width - x, offset);
                    break;
                case 0x11:
                    pdata4bpp(bs, rowBase + x, region.width - x, offset);
                    break;
                case 0x12:
                    pdata8bpp(bs, rowBase + x, region.width - x, offset);
                    break;
                case 0x20:
                case 0x21:
                case 0x22:
                    // Map tables: VLC does not use them (indices index the CLUT
                    // directly at the region depth). Skip.
                    break;
                case 0xf0:  // end of line
                    rowBase += 2 * region.width;
                    offset = 0;
                    y += 2;
                    break;
                default:
                    break;
            }
        }
    }

    static void emit(uint8_t* p, int width, int& off, int count, int color) {
        if (count <= 0) return;
        if (off + count > width) { off = width + 1; return; }  // signal overflow
        if (count == 1) p[off] = (uint8_t)color;
        else std::memset(p + off, color, count);
        off += count;
    }

    void pdata2bpp(BitReader& s, uint8_t* p, int width, int& off) {
        bool stop = false;
        while (!stop && !s.eof()) {
            int count = 0, color = 0;
            color = (int)s.read(2);
            if (color != 0x00) {
                count = 1;
            } else {
                if (s.read(1) == 0x01) {            // switch 1
                    count = 3 + (int)s.read(3);
                    color = (int)s.read(2);
                } else {
                    if (s.read(1) == 0x00) {        // switch 2
                        switch (s.read(2)) {        // switch 3
                            case 0x00: stop = true; break;
                            case 0x01: count = 2; break;
                            case 0x02: count = 12 + (int)s.read(4); color = (int)s.read(2); break;
                            case 0x03: count = 29 + (int)s.read(8); color = (int)s.read(2); break;
                            default: break;
                        }
                    } else {
                        count = 1;  // 1 pixel of colour 0
                    }
                }
            }
            if (!count) continue;
            if (off + count > width) break;
            emit(p, width, off, count, color);
            if (off > width) break;
        }
        s.align();
    }

    void pdata4bpp(BitReader& s, uint8_t* p, int width, int& off) {
        bool stop = false;
        while (!stop && !s.eof()) {
            int count = 0, color = 0;
            color = (int)s.read(4);
            if (color != 0x00) {
                count = 1;
            } else {
                if (s.read(1) == 0x00) {            // switch 1
                    count = (int)s.read(3);
                    if (count != 0x00) count += 2;
                    else stop = true;
                } else {
                    if (s.read(1) == 0x00) {        // switch 2
                        count = 4 + (int)s.read(2);
                        color = (int)s.read(4);
                    } else {
                        switch (s.read(2)) {        // switch 3
                            case 0x0: count = 1; break;
                            case 0x1: count = 2; break;
                            case 0x2: count = 9 + (int)s.read(4); color = (int)s.read(4); break;
                            case 0x3: count = 25 + (int)s.read(8); color = (int)s.read(4); break;
                            default: break;
                        }
                    }
                }
            }
            if (!count) continue;
            if (off + count > width) break;
            emit(p, width, off, count, color);
            if (off > width) break;
        }
        s.align();
    }

    void pdata8bpp(BitReader& s, uint8_t* p, int width, int& off) {
        bool stop = false;
        while (!stop && !s.eof()) {
            int count = 0, color = 0;
            color = (int)s.read(8);
            if (color != 0x00) {
                count = 1;
            } else {
                if (s.read(1) == 0x00) {            // switch 1
                    count = (int)s.read(7);
                    if (count == 0x00) stop = true;
                } else {
                    count = (int)s.read(7);
                    color = (int)s.read(8);
                }
            }
            if (!count) continue;
            if (off + count > width) break;
            emit(p, width, off, count, color);
            if (off > width) break;
        }
        s.align();
    }

    // ----- CLUT colour conversion (BT.601, straight RGBA8) ------------------
    static uint8_t clamp8(int v) { return v < 0 ? 0 : v > 255 ? 255 : (uint8_t)v; }

    static void ycbcrToRgba(const Color& c, uint8_t out[4]) {
        int Y = c.Y, Cr = c.Cr, Cb = c.Cb;
        int R = (int)std::lround(Y + 1.402 * (Cr - 128));
        int G = (int)std::lround(Y - 0.344136 * (Cb - 128) - 0.714136 * (Cr - 128));
        int B = (int)std::lround(Y + 1.772 * (Cb - 128));
        out[0] = clamp8(R);
        out[1] = clamp8(G);
        out[2] = clamp8(B);
        out[3] = (uint8_t)(255 - c.T);  // T = transparency, alpha = 255 - T
    }

    // ----- compose the current page into output regions ---------------------
    void render(DisplaySet& ds) {
        if (!mHasPage) return;

        int baseX = 0, baseY = 0;
        if (mDisplay.windowed) {
            baseX += mDisplay.x;
            baseY += mDisplay.y;
        }

        for (auto& rd : mPage.regionDefs) {
            Region* region = findRegion(rd.id);
            if (!region || region->pixbuf.empty()) continue;

            Clut* clut = findClut(region->clut);

            // Resolve the palette for this region depth, falling back to the
            // default CLUTs when the referenced CLUT is missing (per spec).
            const Color* palette;
            int paletteSize;
            if (region->depth == 1) {
                palette = clut ? clut->c2b : mDefaultClut.c2b;
                paletteSize = 4;
            } else if (region->depth == 2) {
                palette = clut ? clut->c4b : mDefaultClut.c4b;
                paletteSize = 16;
            } else {
                palette = clut ? clut->c8b : mDefaultClut.c8b;
                paletteSize = 256;
            }

            OutRegion outr;
            outr.x = baseX + rd.x;
            outr.y = baseY + rd.y;
            outr.w = region->width;
            outr.h = region->height;
            outr.rgba.resize((size_t)outr.w * outr.h * 4);

            for (int py = 0; py < region->height; py++) {
                const uint8_t* src = region->pixbuf.data() + (size_t)py * region->width;
                uint8_t* dst = outr.rgba.data() + (size_t)py * region->width * 4;
                for (int px = 0; px < region->width; px++) {
                    int idx = src[px];
                    if (idx >= paletteSize) idx = 0;
                    ycbcrToRgba(palette[idx], dst + px * 4);
                }
            }

            ds.regions.push_back(std::move(outr));
        }
    }

    // State.
    double mPts = 0;
    bool mHasPts = false;
    bool mHasPage = false;
    bool mPageUpdated = false;

    Page mPage;
    Display mDisplay;
    Clut mDefaultClut;
    std::vector<Region> mRegions;
    std::vector<Clut> mCluts;
};

// ---------------------------------------------------------------------------
// Minimal MPEG-TS demuxer: find the subtitle PID and reassemble its PES.
// ---------------------------------------------------------------------------

constexpr int TS_PACKET = 188;

// Find the byte offset of the first TS sync byte (0x47) such that 0x47 recurs
// every 188 bytes; handles files with a leading offset.
long findTsAlignment(const std::vector<uint8_t>& d) {
    for (long off = 0; off < TS_PACKET && off + 4 * TS_PACKET <= (long)d.size(); off++) {
        bool ok = true;
        for (int k = 0; k < 4; k++) {
            if (d[off + (size_t)k * TS_PACKET] != 0x47) { ok = false; break; }
        }
        if (ok) return off;
    }
    // Fallback: first 0x47.
    for (size_t i = 0; i < d.size(); i++) if (d[i] == 0x47) return (long)i;
    return -1;
}

// Section reassembler for PSI (PAT/PMT). Collects payload across TS packets
// using payload_unit_start_indicator and the pointer_field.
struct SectionAssembler {
    std::vector<uint8_t> buf;
    int expectLen = -1;  // total section length (incl. header) or -1

    // Feed a TS payload. Returns a complete section in 'section' if one is ready.
    bool feed(const uint8_t* payload, int len, bool pusi, std::vector<uint8_t>& section) {
        const uint8_t* p = payload;
        int remaining = len;
        if (pusi) {
            if (remaining < 1) return false;
            int ptr = p[0];
            p += 1; remaining -= 1;
            if (ptr > remaining) ptr = remaining;
            // Bytes before the pointer belong to a previous section; ignore for
            // simplicity (PAT/PMT here start fresh at the pointer).
            p += ptr; remaining -= ptr;
            buf.clear();
            expectLen = -1;
        }
        if (buf.empty() && !pusi) return false;  // waiting for a start
        buf.insert(buf.end(), p, p + remaining);
        if (expectLen < 0 && buf.size() >= 3) {
            int sectionLen = ((buf[1] & 0x0f) << 8) | buf[2];
            expectLen = sectionLen + 3;  // 3-byte prefix before section_length field count
        }
        if (expectLen > 0 && (int)buf.size() >= expectLen) {
            section.assign(buf.begin(), buf.begin() + expectLen);
            buf.clear();
            expectLen = -1;
            return true;
        }
        return false;
    }
};

// Parse the PAT section, return the first program's PMT PID (-1 if none).
int parsePat(const std::vector<uint8_t>& s) {
    if (s.size() < 8) return -1;
    int sectionLen = ((s[1] & 0x0f) << 8) | s[2];
    int end = 3 + sectionLen - 4;  // minus CRC32
    int i = 8;  // skip table_id, len, transport_stream_id(2), ver/cur(1), sec_num, last_sec_num
    while (i + 4 <= end && i + 4 <= (int)s.size()) {
        int programNumber = (s[i] << 8) | s[i + 1];
        int pid = ((s[i + 2] & 0x1f) << 8) | s[i + 3];
        i += 4;
        if (programNumber != 0) return pid;  // network PID has program_number 0
    }
    return -1;
}

// Parse a PMT section. Sets subtitlePid to the elementary PID whose stream_type
// is 0x06 carrying a DVB subtitling_descriptor (tag 0x59). Returns true if found.
bool parsePmt(const std::vector<uint8_t>& s, int& subtitlePid) {
    if (s.size() < 12) return false;
    int sectionLen = ((s[1] & 0x0f) << 8) | s[2];
    int end = 3 + sectionLen - 4;  // minus CRC32
    if (end > (int)s.size()) end = (int)s.size();
    int programInfoLen = ((s[10] & 0x0f) << 8) | s[11];
    int i = 12 + programInfoLen;

    int firstStreamType06 = -1;  // fallback: any private-data stream
    while (i + 5 <= end) {
        int streamType = s[i];
        int pid = ((s[i + 1] & 0x1f) << 8) | s[i + 2];
        int esInfoLen = ((s[i + 3] & 0x0f) << 8) | s[i + 4];
        int descStart = i + 5;
        int descEnd = descStart + esInfoLen;
        if (descEnd > end) descEnd = end;

        if (streamType == 0x06) {
            if (firstStreamType06 < 0) firstStreamType06 = pid;
            // Scan descriptors for the DVB subtitling_descriptor (tag 0x59).
            int d = descStart;
            while (d + 2 <= descEnd) {
                int tag = s[d];
                int dlen = s[d + 1];
                if (tag == 0x59) {
                    subtitlePid = pid;
                    return true;
                }
                d += 2 + dlen;
            }
        }
        i = descEnd;
    }
    // No explicit subtitling_descriptor: fall back to the first 0x06 stream.
    if (firstStreamType06 >= 0) { subtitlePid = firstStreamType06; return true; }
    return false;
}

// PES reassembler for the subtitle PID.
struct PesAssembler {
    std::vector<uint8_t> buf;
    bool started = false;
    int lastCc = -1;

    // Called with the accumulated PES at each start. Returns whether a complete
    // PES was flushed (always emits the previous one when a new start arrives).
    void feed(const uint8_t* payload, int len, bool pusi, int cc,
              std::vector<std::vector<uint8_t>>& pesOut) {
        // Continuity check (best-effort; the test file is clean).
        if (started && lastCc >= 0) {
            int expected = (lastCc + 1) & 0x0f;
            if (cc != expected) {
                // Discontinuity: keep going, the PES length still bounds us.
            }
        }
        lastCc = cc;

        if (pusi) {
            if (started && !buf.empty()) {
                pesOut.push_back(buf);
            }
            buf.clear();
            buf.insert(buf.end(), payload, payload + len);
            started = true;
        } else if (started) {
            buf.insert(buf.end(), payload, payload + len);
        }
    }

    void flush(std::vector<std::vector<uint8_t>>& pesOut) {
        if (started && !buf.empty()) pesOut.push_back(buf);
        buf.clear();
        started = false;
    }
};

// Extract the 33-bit PTS (seconds) and the payload start from a PES packet.
// Returns false if no valid PTS / not a private_stream_1 PES.
bool parsePesHeader(const std::vector<uint8_t>& pes, double& ptsSec,
                    const uint8_t*& payload, size_t& payloadLen) {
    if (pes.size() < 9) return false;
    if (!(pes[0] == 0x00 && pes[1] == 0x00 && pes[2] == 0x01)) return false;
    // stream_id pes[3]; for DVB subtitles it is private_stream_1 (0xBD).
    int pesPacketLen = (pes[4] << 8) | pes[5];
    (void)pesPacketLen;

    // Optional PES header present for stream ids other than padding/etc.
    if ((pes[6] & 0xc0) != 0x80) {
        // Not the expected '10' marker; bail.
        return false;
    }
    int ptsDtsFlags = (pes[7] >> 6) & 0x03;
    int headerDataLen = pes[8];
    size_t headerEnd = 9 + (size_t)headerDataLen;
    if (headerEnd > pes.size()) return false;

    if (ptsDtsFlags & 0x02) {
        if (pes.size() < 14) return false;
        const uint8_t* p = pes.data() + 9;
        uint64_t pts =
            ((uint64_t)(p[0] & 0x0e) << 29) |
            ((uint64_t)(p[1]) << 22) |
            ((uint64_t)(p[2] & 0xfe) << 14) |
            ((uint64_t)(p[3]) << 7) |
            ((uint64_t)(p[4] & 0xfe) >> 1);
        ptsSec = (double)pts / 90000.0;
    } else {
        ptsSec = -1.0;  // no PTS
    }

    payload = pes.data() + headerEnd;
    payloadLen = pes.size() - headerEnd;
    return true;
}

}  // namespace

// ---------------------------------------------------------------------------
// NanoDvbSub public API
// ---------------------------------------------------------------------------

NanoDvbSub::~NanoDvbSub() { close(); }

void NanoDvbSub::close() {
    mRegions.clear();
    mRegions.shrink_to_fit();
    mDisplayWidth = 720;
    mDisplayHeight = 576;
    mOpen = false;
}

bool NanoDvbSub::probe(const std::string& path, int& outPid) {
    outPid = -1;
    int fd = ::open(path.c_str(), O_RDONLY);
    if (fd < 0) return false;
    std::vector<uint8_t> head(64 * 1024);
    ssize_t hn = ::read(fd, head.data(), head.size());
    if (hn < 4 * TS_PACKET) { ::close(fd); return false; }
    head.resize((size_t)hn);
    long align = findTsAlignment(head);
    if (align < 0) { ::close(fd); return false; }
    head.clear(); head.shrink_to_fit();

    int pmtPid = -1, subPid = -1;
    SectionAssembler patAsm;
    std::map<int, SectionAssembler> pmtAsm;
    const int kChunkPkts = 512;
    const int64_t kBudget = 8 * 1024 * 1024;        // PAT/PMT live at the head; cap the scan
    std::vector<uint8_t> chunk((size_t)TS_PACKET * kChunkPkts);
    int64_t pos = align;
    while (subPid < 0 && pos - align < kBudget) {
        ssize_t n = pread(fd, chunk.data(), chunk.size(), pos);
        if (n < TS_PACKET) break;
        int nPkts = (int)(n / TS_PACKET);
        pos += (int64_t)nPkts * TS_PACKET;
        for (int i = 0; i < nPkts && subPid < 0; i++) {
            const uint8_t* pkt = chunk.data() + (size_t)i * TS_PACKET;
            if (pkt[0] != 0x47) continue;
            int p = ((pkt[1] & 0x1f) << 8) | pkt[2];
            bool pusi = (pkt[1] & 0x40) != 0;
            int afc = (pkt[3] >> 4) & 0x03;
            int payloadOff = 4;
            if (afc == 0x02) continue;
            if (afc == 0x03) { int afLen = pkt[4]; payloadOff = 5 + afLen; }
            if (payloadOff >= TS_PACKET) continue;
            const uint8_t* payload = pkt + payloadOff;
            int payloadLen = TS_PACKET - payloadOff;
            if (p == 0x0000) {
                std::vector<uint8_t> section;
                if (patAsm.feed(payload, payloadLen, pusi, section)) {
                    int found = parsePat(section);
                    if (found >= 0) { pmtPid = found; pmtAsm.emplace(pmtPid, SectionAssembler()); }
                }
            } else if (pmtPid >= 0 && p == pmtPid) {
                std::vector<uint8_t> section;
                if (pmtAsm[pmtPid].feed(payload, payloadLen, pusi, section)) {
                    int found = -1;
                    if (parsePmt(section, found)) subPid = found;
                }
            }
        }
        if (n < (ssize_t)chunk.size()) break;
    }
    ::close(fd);
    outPid = subPid;
    return subPid >= 0;
}

bool NanoDvbSub::open(const std::string& path, int pid, const std::atomic<bool>* abort) {
    close();

    // Stream the file in TS-packet-aligned chunks (never load the whole file -
    // real DVB .ts recordings can be gigabytes; only the small decoded subtitle
    // timeline is kept). Single pass: discover PAT -> PMT -> the subtitle PID and
    // reassemble that PID's PES along the way. The PMT sits at the very start of a
    // well-formed stream, before any subtitle PES, so nothing is missed.
    int fd = ::open(path.c_str(), O_RDONLY);
    if (fd < 0) return false;

    // Detect the sync alignment from a header chunk.
    std::vector<uint8_t> head(64 * 1024);
    ssize_t hn = ::read(fd, head.data(), head.size());
    if (hn < 4 * TS_PACKET) { ::close(fd); return false; }
    head.resize((size_t)hn);
    long align = findTsAlignment(head);
    if (align < 0) { ::close(fd); return false; }
    head.clear(); head.shrink_to_fit();

    int pmtPid = -1;
    int subPid = pid;  // if >= 0, use directly (auto-detect skipped)
    SectionAssembler patAsm;
    std::map<int, SectionAssembler> pmtAsm;
    PesAssembler pesAsm;
    std::vector<std::vector<uint8_t>> pesList;

    const int kChunkPkts = 512;                                  // ~94 KB per read
    std::vector<uint8_t> chunk((size_t)TS_PACKET * kChunkPkts);
    int64_t pos = align;
    for (;;) {
        if (abort && abort->load()) { ::close(fd); close(); return false; }   // cancelled (deselect/teardown)
        ssize_t n = pread(fd, chunk.data(), chunk.size(), pos);
        if (n < TS_PACKET) break;
        int nPkts = (int)(n / TS_PACKET);
        pos += (int64_t)nPkts * TS_PACKET;
        for (int i = 0; i < nPkts; i++) {
            const uint8_t* pkt = chunk.data() + (size_t)i * TS_PACKET;
            if (pkt[0] != 0x47) continue;
            int p = ((pkt[1] & 0x1f) << 8) | pkt[2];
            bool pusi = (pkt[1] & 0x40) != 0;
            int cc = pkt[3] & 0x0f;
            int afc = (pkt[3] >> 4) & 0x03;
            int payloadOff = 4;
            if (afc == 0x02) continue;       // adaptation only, no payload
            if (afc == 0x03) { int afLen = pkt[4]; payloadOff = 5 + afLen; }
            if (payloadOff >= TS_PACKET) continue;
            const uint8_t* payload = pkt + payloadOff;
            int payloadLen = TS_PACKET - payloadOff;

            if (subPid < 0) {
                if (p == 0x0000) {
                    std::vector<uint8_t> section;
                    if (patAsm.feed(payload, payloadLen, pusi, section)) {
                        int found = parsePat(section);
                        if (found >= 0) { pmtPid = found; pmtAsm.emplace(pmtPid, SectionAssembler()); }
                    }
                } else if (pmtPid >= 0 && p == pmtPid) {
                    std::vector<uint8_t> section;
                    if (pmtAsm[pmtPid].feed(payload, payloadLen, pusi, section)) {
                        int found = -1;
                        if (parsePmt(section, found)) subPid = found;
                    }
                }
            }
            if (subPid >= 0 && p == subPid)
                pesAsm.feed(payload, payloadLen, pusi, cc, pesList);
        }
        if (n < (ssize_t)chunk.size()) break;   // short read => EOF
    }
    ::close(fd);
    pesAsm.flush(pesList);

    if (pesList.empty()) return false;

    // Decode each PES payload into display sets, carrying the PTS.
    DvbSubDecoder dec;
    std::vector<DvbSubDecoder::DisplaySet> sets;
    double lastPts = 0;
    bool haveLastPts = false;

    for (auto& pes : pesList) {
        double ptsSec = -1.0;
        const uint8_t* payload = nullptr;
        size_t payloadLen = 0;
        if (!parsePesHeader(pes, ptsSec, payload, payloadLen)) continue;
        if (ptsSec < 0) {
            // Use the previous PTS for non-dated stuffing packets.
            if (!haveLastPts) continue;
            ptsSec = lastPts;
        } else {
            lastPts = ptsSec;
            haveLastPts = true;
        }
        if (!payload || payloadLen < 2) continue;
        dec.decode(payload, payloadLen, ptsSec, sets);
    }

    mDisplayWidth = dec.displayWidth();
    mDisplayHeight = dec.displayHeight();

    // Build the timeline. Each display set with regions becomes visible until
    // the next display set for the same page; a clearing (empty) display set
    // ends the currently visible regions.
    //
    // We track the index range of regions emitted by the last non-empty set so
    // a subsequent set (clearing or new) can close their endSec.
    constexpr double kLastDuration = 8.0;
    size_t firstOfCurrent = 0;
    bool haveCurrent = false;

    for (auto& ds : sets) {
        // A new display set ends the previously open regions.
        if (haveCurrent) {
            for (size_t i = firstOfCurrent; i < mRegions.size(); i++) {
                mRegions[i].endSec = ds.pts;
            }
            haveCurrent = false;
        }

        if (ds.regions.empty()) continue;  // clearing set: emit nothing new

        firstOfCurrent = mRegions.size();
        for (auto& r : ds.regions) {
            Region out;
            out.startSec = ds.pts;
            out.endSec = ds.pts + kLastDuration;  // provisional; fixed by next set
            out.x = r.x;
            out.y = r.y;
            out.w = r.w;
            out.h = r.h;
            out.rgba = std::move(r.rgba);
            mRegions.push_back(std::move(out));
        }
        haveCurrent = true;
    }

    mOpen = !mRegions.empty();
    return mOpen;
}
