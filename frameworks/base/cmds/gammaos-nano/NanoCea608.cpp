// NanoCea608 - CEA-608 line-21 caption decoder. See NanoCea608.h.
// Ported from VLC modules/codec/cc.c (eia608 state machine + char map) and cc.h (GA94 cc_data).
#include "NanoCea608.h"

#include <cstring>

namespace android {

namespace {
// Status bits returned by the parse helpers (subset of cc.c's eia608_status_t that affects what
// is displayed). Any non-zero result means the displayed caption may have changed -> re-emit.
enum { S_DEFAULT = 0, S_CHANGED = 1, S_DISPLAY = 2, S_ENDED = 4, S_CLEARED = 8 };

// PAC column for the 32 mid-row/PAC attribute codes (cc.c pac2_attribs i_column).
const int kPacCol[32] = {
    0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
    0, 0, 4, 4, 8, 8, 12, 12, 16, 16, 20, 20, 24, 24, 28, 28
};
// PAC row index -> screen row (1-based; <=0 = not a PAC). cc.c pi_row.
const int kPacRow[16] = { 11, -1, 1, 2, 3, 4, 12, 13, 14, 15, 5, 6, 7, 8, 9, 10 };

// Odd-parity table for the low nibble (cc.c p4): used to validate the channel bytes.
const int kP4[16] = { 0, 1, 1, 0, 1, 0, 0, 1, 1, 0, 0, 1, 0, 1, 1, 0 };
}  // namespace

void NanoCea608::utf8(char out[4], uint8_t c) {
    struct E { uint8_t c; const char* s; };
    static const E tbl[] = {
        // line-21 base set exceptions (mostly ASCII otherwise)
        {0x2a,"\xc3\xa1"},{0x5c,"\xc3\xa9"},{0x5e,"\xc3\xad"},{0x5f,"\xc3\xb3"},{0x60,"\xc3\xba"},
        {0x7b,"\xc3\xa7"},{0x7c,"\xc3\xb7"},{0x7d,"\xc3\x91"},{0x7e,"\xc3\xb1"},{0x7f,"\xe2\x96\x88"},
        // extended 0x80-0x8f (from d1=0x11 double + d1=0x11 0x30-0x3f)
        {0x80,"\xc2\xae"},{0x81,"\xc2\xb0"},{0x82,"\xc2\xbd"},{0x83,"\xc2\xbf"},{0x84,"\xe2\x84\xa2"},
        {0x85,"\xc2\xa2"},{0x86,"\xc2\xa3"},{0x87,"\xe2\x99\xaa"},{0x88,"\xc3\xa0"},{0x89,"\xc2\xa0"},
        {0x8a,"\xc3\xa8"},{0x8b,"\xc3\xa2"},{0x8c,"\xc3\xaa"},{0x8d,"\xc3\xae"},{0x8e,"\xc3\xb4"},{0x8f,"\xc3\xbb"},
        // extended 0x90-0xaf (d1=0x12)
        {0x90,"\xc3\x81"},{0x91,"\xc3\x89"},{0x92,"\xc3\x93"},{0x93,"\xc3\x9a"},{0x94,"\xc3\x9c"},{0x95,"\xc3\xbc"},
        {0x96,"'"},{0x97,"\xc2\xa1"},{0x98,"*"},{0x99,"'"},{0x9a,"-"},{0x9b,"\xc2\xa9"},{0x9c,"\xe2\x84\xa0"},
        {0x9d,"."},{0x9e,"\xe2\x80\x9c"},{0x9f,"\xe2\x80\x9d"},
        {0xa0,"\xc3\x80"},{0xa1,"\xc3\x82"},{0xa2,"\xc3\x87"},{0xa3,"\xc3\x88"},{0xa4,"\xc3\x8a"},{0xa5,"\xc3\x8b"},
        {0xa6,"\xc3\xab"},{0xa7,"\xc3\x8e"},{0xa8,"\xc3\x8f"},{0xa9,"\xc3\xaf"},{0xaa,"\xc3\x94"},{0xab,"\xc3\x99"},
        {0xac,"\xc3\xb9"},{0xad,"\xc3\x9b"},{0xae,"\xc2\xab"},{0xaf,"\xc2\xbb"},
        // extended 0xb0-0xcf (d1=0x13)
        {0xb0,"\xc3\x83"},{0xb1,"\xc3\xa3"},{0xb2,"\xc3\x8d"},{0xb3,"\xc3\x8c"},{0xb4,"\xc3\xac"},{0xb5,"\xc3\x92"},
        {0xb6,"\xc3\xb2"},{0xb7,"\xc3\x95"},{0xb8,"\xc3\xb5"},{0xb9,"{"},{0xba,"}"},{0xbb,"\\"},{0xbc,"^"},
        {0xbd,"_"},{0xbe,"\xc2\xa6"},{0xbf,"~"},{0xc0,"\xc3\x84"},{0xc1,"\xc3\xa4"},{0xc2,"\xc3\x96"},
        {0xc3,"\xc3\xb6"},{0xc4,"\xc3\x9f"},{0xc5,"\xc2\xa5"},{0xc6,"\xc2\xa4"},{0xc7,"\xe2\x96\x88"},
        {0xc8,"\xc3\x85"},{0xc9,"\xc3\xa5"},{0xca,"\xc3\x98"},{0xcb,"\xc3\xb8"},
        {0xcc,"\xe2\x94\x8c"},{0xcd,"\xe2\x94\x90"},{0xce,"\xe2\x94\x94"},{0xcf,"\xe2\x94\x98"},
    };
    for (const E& e : tbl) if (e.c == c) { strcpy(out, e.s); return; }
    if (c >= 0x20 && c < 0x7f) { out[0] = (char)c; out[1] = 0; return; }
    out[0] = '?'; out[1] = 0;
}

void NanoCea608::setChannel(int ch) {
    if (ch < 0) ch = 0; if (ch > 3) ch = 3;
    mField = ch >> 1;
    mChannelSel = 1 + (ch & 1);
    reset();
}

void NanoCea608::reset() {
    initState();
    mSawData = false;
    mHasCaptions = false;
    mCues.clear();
    mLastText.clear();
}

void NanoCea608::initState() {
    for (int s = 0; s < 2; s++) clearScreen(s);
    mDisplayed = 0;
    mCursorRow = 0; mCursorCol = 0;
    mMode = MODE_POPUP;
    mRowRollup = 0;
    mChannel = 0;
    mLastD1 = mLastD2 = 0;
}

int NanoCea608::writingScreen() const {
    // Pop-up writes to the BACK buffer (1 - displayed); roll-up/paint-on write to the displayed.
    if (mMode == MODE_POPUP) return 1 - mDisplayed;
    return mDisplayed;   // ROLLUP_* / PAINTON
}

void NanoCea608::cursor(int dx) {
    mCursorCol += dx;
    if (mCursorCol < 0) mCursorCol = 0;
    else if (mCursorCol > kCols - 1) mCursorCol = kCols - 1;
}

void NanoCea608::clearRow(int scr, int row) {
    Screen& s = mScreen[scr];
    for (int x = 0; x < kCols; x++) s.ch[row][x] = ' ';
    s.ch[row][kCols] = 0;
    s.rowUsed[row] = false;
}

void NanoCea608::clearScreen(int scr) {
    for (int r = 0; r < kRows; r++) clearRow(scr, r);
}

void NanoCea608::eraseScreen(bool displayed) {
    clearScreen(displayed ? mDisplayed : (1 - mDisplayed));
}

void NanoCea608::write(uint8_t c) {
    if (mMode == MODE_TEXT) return;
    Screen& s = mScreen[writingScreen()];
    s.ch[mCursorRow][mCursorCol] = c;
    s.rowUsed[mCursorRow] = true;
    cursor(1);
}

void NanoCea608::backspace() {
    if (mMode == MODE_TEXT) return;
    int col = mCursorCol - 1;
    if (col < 0) return;
    mScreen[writingScreen()].ch[mCursorRow][col] = ' ';
    cursor(-1);
}

void NanoCea608::rollUp() {
    if (mMode == MODE_TEXT) return;
    int keep;
    if (mMode == MODE_ROLLUP_2) keep = 2;
    else if (mMode == MODE_ROLLUP_3) keep = 3;
    else if (mMode == MODE_ROLLUP_4) keep = 4;
    else return;
    int scr = writingScreen();
    Screen& s = mScreen[scr];
    mCursorCol = 0;
    for (int i = 0; i < mCursorRow - keep; i++) clearRow(scr, i);
    for (int i = 0; i < keep - 1; i++) {
        int row = mCursorRow - keep + i + 1;
        if (row < 0) continue;
        if (row + 1 < kRows) {
            memcpy(s.ch[row], s.ch[row + 1], sizeof(s.ch[row]));
            s.rowUsed[row] = s.rowUsed[row + 1];
        }
    }
    clearRow(scr, mCursorRow);
}

void NanoCea608::parseChannel(const uint8_t d[2]) {
    // Reject pairs that fail odd parity (corrupt / not a real control code).
    if (kP4[d[0] & 0xf] == kP4[d[0] >> 4] || kP4[d[1] & 0xf] == kP4[d[1] >> 4]) {
        mChannel = -1;
        return;
    }
    const int d1 = d[0] & 0x7f;
    if (d1 >= 0x10 && d1 <= 0x1f) mChannel = 1 + ((d1 & 0x08) != 0);
    else if (d1 < 0x10) mChannel = 3;
}

void NanoCea608::parseTextAttribute(uint8_t /*d2*/) {
    // Colour/font attribute: text-only renderer ignores the style, just advances the cursor.
    cursor(1);
}

void NanoCea608::parseSingle(uint8_t dx) { write(dx); }

void NanoCea608::parseDouble(uint8_t d2) { write((uint8_t)(d2 + 0x50)); }   // 0x30-0x3f -> 0x80-0x8f

void NanoCea608::parseExtended(uint8_t d1, uint8_t d2) {
    if (d1 == 0x12) d2 += 0x70;   // -> 0x90-0xaf
    else            d2 += 0x90;   // -> 0xb0-0xcf
    cursor(-1);                   // extended char replaces the standard one written before it
    write(d2);
}

int NanoCea608::parseCmd14(uint8_t d2) {
    int st = S_DEFAULT;
    switch (d2) {
        case 0x20: mMode = MODE_POPUP; break;                       // RCL resume caption loading
        case 0x21: backspace(); st = S_CHANGED; break;              // BS
        case 0x24: { Screen& s = mScreen[writingScreen()];          // DER delete to end of row
                     for (int x = mCursorCol; x < kCols; x++) s.ch[mCursorRow][x] = ' '; } break;
        case 0x25: case 0x26: case 0x27: {                          // RU2/RU3/RU4
            if (mMode == MODE_POPUP || mMode == MODE_PAINTON) {
                eraseScreen(true); eraseScreen(false); st = S_CHANGED | S_CLEARED;
            }
            Mode pm = (d2 == 0x25) ? MODE_ROLLUP_2 : (d2 == 0x26) ? MODE_ROLLUP_3 : MODE_ROLLUP_4;
            if (pm != mMode) { mMode = pm; mCursorCol = 0; mCursorRow = mRowRollup; }
        } break;
        case 0x29: mMode = MODE_PAINTON; break;                     // RDC resume direct captioning
        case 0x2b: mMode = MODE_TEXT; break;                        // RTD resume text display
        case 0x2c: eraseScreen(true); st = S_CHANGED | S_CLEARED; break;  // EDM erase displayed
        case 0x2d: rollUp(); st = S_CHANGED; break;                 // CR carriage return
        case 0x2e: eraseScreen(false); break;                      // ENM erase non-displayed
        case 0x2f:                                                  // EOC end of caption (flip)
            if (mMode != MODE_PAINTON) mDisplayed = 1 - mDisplayed;
            mMode = MODE_POPUP; mCursorCol = 0; mCursorRow = 0;
            st = S_CHANGED | S_ENDED;
            break;
        default: break;
    }
    return st;
}

void NanoCea608::parseCmd17(uint8_t d2) {
    if (d2 >= 0x21 && d2 <= 0x23) cursor(d2 - 0x20);   // TO1/TO2/TO3 tab offset
}

bool NanoCea608::parsePac(uint8_t d1, uint8_t d2) {
    int idx = ((d1 << 1) & 0x0e) | ((d2 >> 5) & 0x01);
    int row = kPacRow[idx];
    if (row <= 0) return false;
    if (mMode != MODE_TEXT) mCursorRow = row - 1;
    mRowRollup = row - 1;
    if (d2 >= 0x60) d2 -= 0x60; else if (d2 >= 0x40) d2 -= 0x40;
    if (d2 < 32) mCursorCol = kPacCol[d2];
    return false;
}

int NanoCea608::parseData(uint8_t d1, uint8_t d2) {
    int st = S_DEFAULT;
    if (d1 >= 0x18 && d1 <= 0x1f) d1 -= 8;   // map field/channel-2 control range down to 0x10-0x17
#define ON(lo, hi, expr) do { if (d2 >= (lo) && d2 <= (hi)) { expr; } } while (0)
    switch (d1) {
        case 0x11:
            ON(0x20, 0x2f, parseTextAttribute(d2); st = S_DEFAULT);
            ON(0x30, 0x3f, parseDouble(d2); st = S_CHANGED);
            break;
        case 0x12: case 0x13:
            ON(0x20, 0x3f, parseExtended(d1, d2); st = S_CHANGED);
            break;
        case 0x14: case 0x15:
            ON(0x20, 0x2f, st = parseCmd14(d2));
            break;
        case 0x17:
            ON(0x21, 0x23, parseCmd17(d2));
            ON(0x2e, 0x2f, parseTextAttribute(d2));
            break;
        default: break;
    }
    if (d1 == 0x10) ON(0x40, 0x5f, parsePac(d1, d2));
    else if (d1 >= 0x11 && d1 <= 0x17) ON(0x40, 0x7f, parsePac(d1, d2));
#undef ON
    if (d1 >= 0x20) {       // standard character pair
        parseSingle(d1); st = S_CHANGED;
        if (d2 >= 0x20) parseSingle(d2);
    }
    // Pop-up writes to the hidden buffer, so a plain CHANGED there is not visible yet.
    if (mMode == MODE_POPUP && st == S_CHANGED) st = S_DEFAULT;
    return st;
}

int NanoCea608::parsePair(const uint8_t d[2]) {
    const uint8_t d1 = d[0] & 0x7f;
    const uint8_t d2 = d[1] & 0x7f;
    if (d1 == 0 && d2 == 0) return S_DEFAULT;   // padding
    parseChannel(d);
    if (mChannel != mChannelSel) return S_DEFAULT;
    int st = S_DEFAULT;
    if (d1 >= 0x10) {
        if (d1 >= 0x20 || d1 != mLastD1 || d2 != mLastD2)   // control codes are sent twice; dedupe
            st = parseData(d1, d2);
        mLastD1 = d1; mLastD2 = d2;
    }
    // d1 0x01-0x0f = XDS, ignored.
    return st;
}

void NanoCea608::emit(double ptsSec) {
    // Build the displayed screen's text (rows top->bottom, trailing spaces trimmed, blank rows
    // dropped) and turn a change into a cue (close the previous one, open a new one).
    std::string text;
    const Screen& s = mScreen[mDisplayed];
    for (int r = 0; r < kRows; r++) {
        if (!s.rowUsed[r]) continue;
        int end = kCols - 1;
        while (end >= 0 && s.ch[r][end] == ' ') end--;
        if (end < 0) continue;
        std::string line;
        for (int x = 0; x <= end; x++) { char u[4]; utf8(u, s.ch[r][x]); line += u; }
        if (!text.empty()) text += "\n";
        text += line;
    }
    if (text == mLastText) return;
    if (!mCues.empty() && mCues.back().endSec > ptsSec) mCues.back().endSec = ptsSec;  // close prev
    if (!text.empty()) {
        Cue c; c.startSec = ptsSec; c.endSec = ptsSec + 10.0; c.text = text;   // 10s ephemeral cap
        mCues.push_back(std::move(c));
        mHasCaptions = true;
        // Bound the history: only the cues around the current position are rendered (live decode),
        // so old played captions are dropped to keep the footprint flat over a long title.
        if (mCues.size() > 256) mCues.erase(mCues.begin(), mCues.begin() + (mCues.size() - 256));
    }
    mLastText = text;
}

bool NanoCea608::feedGa94(const uint8_t* p, size_t len, double ptsSec) {
    // p points at the cc_data() after the 'GA94' 0x03 marker (cc.h CC_PAYLOAD_GA94):
    //   u1 reserved, u1 process_cc_data_flag, u1 additional_data_flag, u5 cc_count
    //   u8 reserved(0xff); cc_count * { u5 marker, u1 cc_valid, u2 cc_type, u8 cc_data_1, u8 cc_data_2 }
    if (len < 1) return false;
    const uint8_t* cc = p;
    int count = cc[0] & 0x1f;
    if (!(cc[0] & 0x40)) return false;           // process_cc_data_flag clear
    if (count <= 0) return false;
    if (len < (size_t)(2 + count * 3 + 1)) return false;   // truncated
    if (cc[2 + count * 3] != 0xff) return false; // trailing marker absent
    cc += 2;
    size_t before = mCues.size();
    bool prevEnd = !mCues.empty();
    double prevBackEnd = prevEnd ? mCues.back().endSec : 0.0;
    bool changed = false;
    for (int i = 0; i < count; i++, cc += 3) {
        uint8_t preamble = cc[0];
        if (!(preamble & 0x04)) continue;        // cc_valid bit
        int type = preamble & 0x03;              // 0/1 = NTSC field 1/2 (line-21); 2/3 = DTVCC
        if (type != mField) continue;            // only the selected line-21 field
        mSawData = true;
        int st = parsePair(&cc[1]);
        if (st != S_DEFAULT) changed = true;
    }
    if (changed) emit(ptsSec);
    // report a change if a cue was added OR the last cue's end was updated (close)
    return mCues.size() != before || (prevEnd && !mCues.empty() && mCues.back().endSec != prevBackEnd);
}

} // namespace android
