// NanoCea608 - CEA-608 / EIA-608 line-21 closed-caption decoder.
//
// Ported from VLC's modules/codec/cc.c (eia608 state machine) + cc.h (the ATSC "GA94"
// cc_data extraction). ATSC / HDHomeRun MPEG-2 captures carry line-21 captions inside the
// video picture user_data (00 00 01 B2 'GA94' 0x03 cc_data); NanoTsDemux pulls that payload
// out per picture and feeds it here with the picture PTS. We decode the selected caption
// channel (CC1..CC4) into timed text cues that the video player renders like a text subtitle.
//
// Self-contained + cheap: a few fixed-size screen buffers, no allocation per frame, nothing
// resident (NanoTsDemux owns one instance only while a .ts plays and resets it on seek/close).
#ifndef GAMMAOS_NANO_CEA608_H
#define GAMMAOS_NANO_CEA608_H

#include <cstdint>
#include <string>
#include <vector>

namespace android {

class NanoCea608 {
public:
    struct Cue { double startSec = 0, endSec = 0; std::string text; };

    NanoCea608() { reset(); }

    // Select the line-21 caption channel to decode: 0 = CC1, 1 = CC2, 2 = CC3, 3 = CC4.
    // (field = ch>>1, channel = 1 + (ch&1), matching cc.c.) Re-initialises the decoder.
    void setChannel(int ch);
    int  channel() const { return (mField << 1) | (mChannelSel - 1); }

    void reset();          // clear all screen state (on seek / track switch / open)

    // Feed one picture's GA94 cc_data payload (the bytes AFTER the 'GA94' 0x03 marker, i.e.
    // starting at the cc_count byte) at the picture PTS (seconds). Extracts the cc triplets,
    // routes the selected field/channel through the eia608 state machine, and appends a Cue
    // whenever the displayed caption changes. Returns true if the cue list changed.
    bool feedGa94(const uint8_t* p, size_t len, double ptsSec);

    bool sawData() const { return mSawData; }     // any cc_data seen at all (presence probe)
    bool hasCaptions() const { return mHasCaptions; } // a non-empty caption was produced

    void copyCues(std::vector<Cue>& out) const { out = mCues; }
    size_t cueCount() const { return mCues.size(); }

private:
    // ---- eia608 screen model (cc.c) ----
    static constexpr int kRows = 15;
    static constexpr int kCols = 32;
    enum Mode { MODE_POPUP, MODE_ROLLUP_2, MODE_ROLLUP_3, MODE_ROLLUP_4, MODE_PAINTON, MODE_TEXT };
    struct Screen {
        uint8_t ch[kRows][kCols + 1];   // line-21 char codes (0x20.. + our 0x80.. extended); +1 NUL
        bool rowUsed[kRows];
    };

    void initState();
    int  writingScreen() const;             // index of the screen we write to (pop-up = back buffer)
    void cursor(int dx);
    void clearRow(int scr, int row);
    void clearScreen(int scr);
    void eraseScreen(bool displayed);
    void write(uint8_t c);
    void backspace();
    void rollUp();
    void parseChannel(const uint8_t d[2]);
    bool parsePac(uint8_t d1, uint8_t d2);
    void parseTextAttribute(uint8_t d2);
    void parseSingle(uint8_t dx);
    void parseDouble(uint8_t d2);
    void parseExtended(uint8_t d1, uint8_t d2);
    int  parseCmd14(uint8_t d2);            // returns status bits (changed / display / ended)
    void parseCmd17(uint8_t d2);
    int  parseData(uint8_t d1, uint8_t d2); // returns status bits
    // Decode one byte pair (after parity strip) for the selected channel; returns status bits.
    int  parsePair(const uint8_t d[2]);
    // Build the UTF-8 text of the currently DISPLAYED screen; emit/extend a cue at pts.
    void emit(double ptsSec);
    static void utf8(char out[4], uint8_t c);

    // selection
    int mField = 0;          // 0 = field 1, 1 = field 2
    int mChannelSel = 1;     // 1 or 2 (the in-band channel within a field)

    // state
    Screen mScreen[2];
    int mDisplayed = 0;      // displayed screen index
    int mCursorRow = 0, mCursorCol = 0;
    Mode mMode = MODE_POPUP;
    int mRowRollup = 0;
    int mChannel = 0;        // current decoded channel (from control codes); -1 = reject
    uint8_t mLastD1 = 0, mLastD2 = 0;  // last command pair (dedupe)

    bool mSawData = false;
    bool mHasCaptions = false;
    std::vector<Cue> mCues;
    std::string mLastText;   // last emitted displayed text (to close/extend cues)
};

} // namespace android

#endif // GAMMAOS_NANO_CEA608_H
