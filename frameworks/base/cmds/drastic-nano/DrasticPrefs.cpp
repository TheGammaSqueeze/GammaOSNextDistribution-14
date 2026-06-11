/*
 * Copyright (C) 2026 GammaOS
 */

#define LOG_TAG "DrasticNano.Prefs"

#include "DrasticPrefs.h"

#include <errno.h>
#include <fcntl.h>
#include <linux/input.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/stat.h>
#include <unistd.h>

#include <string>
#include <unordered_map>
#include <utility>
#include <vector>

#include <utils/Log.h>

namespace android {
namespace drastic_prefs {

namespace {

// ------------- tiny XML reader ------------------------------------
//
// SharedPreferences files look like:
//
//   <?xml version='1.0' encoding='utf-8' standalone='yes' ?>
//   <map>
//       <boolean name="_Hires3D" value="true" />
//       <int name="_Volume" value="8" />
//       <string name="_CurrentFx">Linear</string>
//       <float name="_AnalogDeadzone" value="0.05" />
//   </map>
//
// We walk the file once, emitting (tag, name, value) tuples into a
// visitor callback. Whitespace between entries is ignored.

struct XmlEntry {
    std::string tag;   // "boolean" | "int" | "string" | "float"
    std::string name;  // content of name=""
    std::string value; // inner text for <string>, otherwise content of value=""
};

std::string readFileAll(const std::string& path) {
    int fd = open(path.c_str(), O_RDONLY);
    if (fd < 0) return {};
    std::string out;
    char buf[4096];
    ssize_t n;
    while ((n = read(fd, buf, sizeof(buf))) > 0) {
        out.append(buf, (size_t)n);
    }
    close(fd);
    return out;
}

// Find the value of attrName="..." inside the attribute blob [start,end).
// Returns empty string if not found. Basic quoting only; drastic writes
// single-line attributes with double quotes.
std::string attrValue(const std::string& s, size_t start, size_t end,
                      const char* attrName) {
    std::string key = std::string(attrName) + "=\"";
    size_t pos = s.find(key, start);
    if (pos == std::string::npos || pos >= end) return {};
    size_t q1 = pos + key.size();
    size_t q2 = s.find('"', q1);
    if (q2 == std::string::npos || q2 > end) return {};
    return s.substr(q1, q2 - q1);
}

template <typename Visitor>
void walkXml(const std::string& src, Visitor&& v) {
    size_t i = 0;
    while (i < src.size()) {
        size_t lt = src.find('<', i);
        if (lt == std::string::npos) break;
        if (lt + 1 < src.size() && src[lt + 1] == '?') {
            size_t end = src.find("?>", lt + 2);
            if (end == std::string::npos) break;
            i = end + 2;
            continue;
        }
        if (lt + 1 < src.size() && src[lt + 1] == '/') {
            // closing tag, skip
            size_t gt = src.find('>', lt);
            if (gt == std::string::npos) break;
            i = gt + 1;
            continue;
        }
        size_t gt = src.find('>', lt);
        if (gt == std::string::npos) break;

        // Parse tag name.
        size_t tagStart = lt + 1;
        size_t tagEnd = tagStart;
        while (tagEnd < gt && src[tagEnd] != ' ' && src[tagEnd] != '/' &&
               src[tagEnd] != '>' && src[tagEnd] != '\t' &&
               src[tagEnd] != '\n') {
            tagEnd++;
        }
        std::string tag = src.substr(tagStart, tagEnd - tagStart);

        bool selfClosing = (src[gt - 1] == '/');
        XmlEntry e;
        e.tag = tag;
        e.name = attrValue(src, tagEnd, gt, "name");

        if (tag == "string" && !selfClosing) {
            // Body text between > and </string>
            size_t bodyStart = gt + 1;
            size_t close = src.find("</string>", bodyStart);
            if (close != std::string::npos) {
                e.value = src.substr(bodyStart, close - bodyStart);
                i = close + std::string("</string>").size();
            } else {
                i = gt + 1;
            }
        } else {
            e.value = attrValue(src, tagEnd, gt, "value");
            i = gt + 1;
        }

        if (!e.tag.empty() && !e.name.empty()) {
            v(e);
        } else if (e.tag == "string" && e.name.empty()) {
            // malformed, skip
        }
    }
}

bool parseBool(const std::string& v, bool def) {
    if (v == "true") return true;
    if (v == "false") return false;
    return def;
}

int parseInt(const std::string& v, int def) {
    if (v.empty()) return def;
    char* end = nullptr;
    long r = strtol(v.c_str(), &end, 10);
    if (!end || *end != '\0') return def;
    return (int)r;
}

float parseFloat(const std::string& v, float def) {
    if (v.empty()) return def;
    char* end = nullptr;
    float r = strtof(v.c_str(), &end);
    if (!end || *end != '\0') return def;
    return r;
}

// Parse a keymap entry name of the form "_KeyMapConfigs_<p>_<a>".
// On success returns true and writes p/a into *player/*action.
bool parseKeymapName(const std::string& name, int* player, int* action) {
    static const char* prefix = "_KeyMapConfigs_";
    if (name.compare(0, strlen(prefix), prefix) != 0) return false;
    const char* s = name.c_str() + strlen(prefix);
    char* end = nullptr;
    long p = strtol(s, &end, 10);
    if (!end || *end != '_') return false;
    const char* t = end + 1;
    long a = strtol(t, &end, 10);
    if (!end || *end != '\0') return false;
    if (p < 0 || p >= kNumPlayers) return false;
    if (a < 0 || a >= kNumActions) return false;
    *player = (int)p;
    *action = (int)a;
    return true;
}

} // anonymous namespace

// ------------------------------------------------------------------
// Public API
// ------------------------------------------------------------------

bool readPrefs(const std::string& xmlPath, Prefs* out) {
    if (!out) return false;
    std::string src = readFileAll(xmlPath);
    if (src.empty()) {
        ALOGW("DrasticPrefs::readPrefs: %s unreadable (errno=%d), "
              "using defaults", xmlPath.c_str(), errno);
        return false;
    }

    walkXml(src, [&](const XmlEntry& e) {
        if (e.tag == "boolean") {
            if (e.name == "_Hires3D")       out->hires3d       = parseBool(e.value, out->hires3d);
            else if (e.name == "_Threaded3D")       out->threaded3d    = parseBool(e.value, out->threaded3d);
            else if (e.name == "_DisableEdgeMarking") out->disableEdge  = parseBool(e.value, out->disableEdge);
            else if (e.name == "_SoundEnabled")     out->soundEnabled  = parseBool(e.value, out->soundEnabled);
            else if (e.name == "_MicEnabled")       out->micEnabled    = parseBool(e.value, out->micEnabled);
            else if (e.name == "_FrameskipSafe")    out->frameskipSafe = parseBool(e.value, out->frameskipSafe);
            else if (e.name == "_AnalogTouch")      out->analogTouch   = parseBool(e.value, out->analogTouch);
            else if (e.name == "_AnalogTriggers")   out->analogTriggers = parseBool(e.value, out->analogTriggers);
            else if (e.name == "_FrameSync")        out->frameSync     = parseBool(e.value, out->frameSync);
        } else if (e.tag == "int") {
            if (e.name == "_Volume")             out->volume         = parseInt(e.value, out->volume);
            else if (e.name == "_AudioLatency")  out->audioLatency   = parseInt(e.value, out->audioLatency);
            else if (e.name == "_MicLevel")      out->micLevel       = parseInt(e.value, out->micLevel);
            else if (e.name == "_FrameskipType") out->frameskipType  = parseInt(e.value, out->frameskipType);
            else if (e.name == "_FrameskipValue") out->frameskipValue = parseInt(e.value, out->frameskipValue);
            else if (e.name == "_AnalogStickMode") out->analogStickMode = parseInt(e.value, out->analogStickMode);
            else {
                int p, a;
                if (parseKeymapName(e.name, &p, &a)) {
                    out->keymap[p][a] = parseInt(e.value, -1);
                }
            }
        } else if (e.tag == "string") {
            if (e.name == "_CurrentFx") {
                if (!e.value.empty()) out->currentFx = e.value;
            }
        } else if (e.tag == "float") {
            if (e.name == "_AnalogDeadzone") out->analogDeadzone = parseFloat(e.value, out->analogDeadzone);
        }
    });
    ALOGI("DrasticPrefs::readPrefs: %s ok (currentFx=%s volume=%d "
          "hires3d=%d threaded3d=%d disableEdge=%d)",
          xmlPath.c_str(), out->currentFx.c_str(), out->volume,
          out->hires3d, out->threaded3d, out->disableEdge);
    return true;
}

namespace {

// Escape &, <, > for XML text nodes (our only string field is
// _CurrentFx which is a filename, so this is mostly defensive).
std::string xmlEscape(const std::string& s) {
    std::string out;
    out.reserve(s.size());
    for (char c : s) {
        switch (c) {
        case '&': out += "&amp;"; break;
        case '<': out += "&lt;"; break;
        case '>': out += "&gt;"; break;
        case '"': out += "&quot;"; break;
        case '\'': out += "&apos;"; break;
        default:  out += c; break;
        }
    }
    return out;
}

// Build the target set of (tag, name) -> serialized element for every
// key we manage. Unknown keys in the existing file are preserved.
struct KV {
    std::string name;
    std::string line; // full XML element including leading indent
};

std::vector<KV> buildManagedLines(const Prefs& p) {
    std::vector<KV> out;
    auto addBool = [&](const char* name, bool v) {
        char buf[128];
        snprintf(buf, sizeof(buf),
                 "    <boolean name=\"%s\" value=\"%s\" />",
                 name, v ? "true" : "false");
        out.push_back({name, buf});
    };
    auto addInt = [&](const char* name, int v) {
        char buf[128];
        snprintf(buf, sizeof(buf),
                 "    <int name=\"%s\" value=\"%d\" />",
                 name, v);
        out.push_back({name, buf});
    };
    auto addFloat = [&](const char* name, float v) {
        char buf[128];
        snprintf(buf, sizeof(buf),
                 "    <float name=\"%s\" value=\"%g\" />",
                 name, v);
        out.push_back({name, buf});
    };
    auto addString = [&](const char* name, const std::string& v) {
        char buf[256];
        snprintf(buf, sizeof(buf),
                 "    <string name=\"%s\">%s</string>",
                 name, xmlEscape(v).c_str());
        out.push_back({name, buf});
    };
    addString("_CurrentFx", p.currentFx);
    addBool("_Hires3D", p.hires3d);
    addBool("_Threaded3D", p.threaded3d);
    addBool("_DisableEdgeMarking", p.disableEdge);
    addBool("_SoundEnabled", p.soundEnabled);
    addBool("_MicEnabled", p.micEnabled);
    addBool("_FrameskipSafe", p.frameskipSafe);
    addBool("_AnalogTouch", p.analogTouch);
    addBool("_AnalogTriggers", p.analogTriggers);
    addBool("_FrameSync", p.frameSync);
    addInt("_Volume", p.volume);
    addInt("_AudioLatency", p.audioLatency);
    addInt("_MicLevel", p.micLevel);
    addInt("_FrameskipType", p.frameskipType);
    addInt("_FrameskipValue", p.frameskipValue);
    addInt("_AnalogStickMode", p.analogStickMode);
    addFloat("_AnalogDeadzone", p.analogDeadzone);
    for (int pl = 0; pl < kNumPlayers; pl++) {
        for (int a = 0; a < kNumActions; a++) {
            char nm[64];
            snprintf(nm, sizeof(nm), "_KeyMapConfigs_%d_%d", pl, a);
            addInt(nm, p.keymap[pl][a]);
        }
    }
    return out;
}

} // anonymous namespace

bool writePrefs(const std::string& xmlPath, const Prefs& p,
                uid_t appUid, gid_t appGid) {
    std::string src = readFileAll(xmlPath);
    std::vector<KV> managed = buildManagedLines(p);

    // Build a name->index lookup for quick inline replacement.
    std::unordered_map<std::string, int> idx;
    idx.reserve(managed.size());
    for (int i = 0; i < (int)managed.size(); i++) {
        idx[managed[i].name] = i;
    }
    std::vector<bool> used(managed.size(), false);

    std::string out;
    if (src.empty()) {
        // Skeleton a fresh SharedPreferences file.
        out = "<?xml version='1.0' encoding='utf-8' standalone='yes' ?>\n<map>\n";
        for (auto& kv : managed) {
            out += kv.line;
            out += "\n";
        }
        out += "</map>\n";
    } else {
        // Walk the source line-by-line. For any line that declares a
        // key we manage, replace with our managed line; otherwise
        // pass through verbatim. Append any managed keys that didn't
        // appear in the source before </map>.
        size_t i = 0;
        while (i < src.size()) {
            size_t eol = src.find('\n', i);
            if (eol == std::string::npos) eol = src.size();
            std::string line = src.substr(i, eol - i);
            i = eol + 1;

            // Find name="..." anywhere in the line; if the key is one
            // we manage, substitute.
            size_t nm = line.find("name=\"");
            if (nm != std::string::npos) {
                size_t q1 = nm + 6;
                size_t q2 = line.find('"', q1);
                if (q2 != std::string::npos) {
                    std::string name = line.substr(q1, q2 - q1);
                    auto it = idx.find(name);
                    if (it != idx.end() && !used[it->second]) {
                        out += managed[it->second].line;
                        out += "\n";
                        used[it->second] = true;
                        continue;
                    } else if (it != idx.end()) {
                        // Duplicate managed key in source -- skip.
                        continue;
                    }
                }
            }
            // Passthrough, but if the line is the closing </map>,
            // insert our unused managed keys just before it.
            if (line.find("</map>") != std::string::npos) {
                for (int k = 0; k < (int)managed.size(); k++) {
                    if (!used[k]) {
                        out += managed[k].line;
                        out += "\n";
                        used[k] = true;
                    }
                }
            }
            out += line;
            out += "\n";
        }
    }

    std::string tmp = xmlPath + ".tmp";
    int fd = open(tmp.c_str(), O_WRONLY | O_CREAT | O_TRUNC, 0660);
    if (fd < 0) {
        ALOGE("DrasticPrefs::writePrefs: open(%s) failed: %s",
              tmp.c_str(), strerror(errno));
        return false;
    }
    ssize_t total = 0;
    while (total < (ssize_t)out.size()) {
        ssize_t n = write(fd, out.data() + total, out.size() - total);
        if (n <= 0) {
            ALOGE("DrasticPrefs::writePrefs: write failed: %s",
                  strerror(errno));
            close(fd);
            unlink(tmp.c_str());
            return false;
        }
        total += n;
    }
    fsync(fd);
    close(fd);

    if (rename(tmp.c_str(), xmlPath.c_str()) != 0) {
        ALOGE("DrasticPrefs::writePrefs: rename %s -> %s failed: %s",
              tmp.c_str(), xmlPath.c_str(), strerror(errno));
        unlink(tmp.c_str());
        return false;
    }

    if (appUid != 0) {
        if (chown(xmlPath.c_str(), appUid, appGid) != 0) {
            ALOGW("DrasticPrefs::writePrefs: chown failed: %s",
                  strerror(errno));
        }
    }
    chmod(xmlPath.c_str(), 0660);
    ALOGI("DrasticPrefs::writePrefs: wrote %s (%zu bytes)",
          xmlPath.c_str(), out.size());
    return true;
}

long applyConfigBitsFrom(const Prefs& p) {
    // Packed applyConfig config word. Bit positions verified against the
    // smali Lf0/h;->n()J snapshot and the native converter at 0x17c58 /
    // applyConfig JNI 0x1a4a0. Every bit here is re-extracted by the
    // converter on each applyConfig call and re-read by the running
    // emulation each frame, so the whole word can be applied LIVE in-game
    // (see DrasticRunner::applyVideoConfigLive) the way the real drastic
    // app does. Bit 50 (_m0) is always set to match drastic's real-app
    // default. NOTE: bit 40 = _DisableEdgeMarking and bit 41 = _Hires3D
    // (NEON-extracted together); bit 39 is a different field, do not use
    // it. Audio latency (bits 8-9) is deliberately NOT packed here: the
    // converter ignores it and drastic reads it once at startGame when it
    // sizes the OpenSL buffer queue, so it cannot change live.
    long bits = 0x4000000000000L;                  // bit 50 _m0
    bits |= (long)(p.frameskipValue & 0xf);        // bits 0-3 _FrameskipValue
    bits |= ((long)(p.frameskipType & 0x3)) << 5;  // bits 5-6 _FrameskipType
    if (p.micEnabled)   bits |= 0x4000000L;        // bit 26 _MicEnabled
    if (p.threaded3d)   bits |= 0x10000000L;       // bit 28 _Threaded3D
    if (p.soundEnabled) bits |= 0x80000000L;       // bit 31 _SoundEnabled
    bits |= ((long)(p.micLevel & 0x3)) << 37;      // bits 37-38 _MicLevel
    if (p.disableEdge)  bits |= 0x10000000000L;    // bit 40 _DisableEdgeMarking
    if (p.hires3d)      bits |= 0x20000000000L;    // bit 41 _Hires3D
    if (p.frameskipSafe) bits |= 0x800000000000L;  // bit 47 _FrameskipSafe
    return bits;
}

bool requiresRelaunch(const Prefs& a, const Prefs& b) {
    // Only audioLatency genuinely cannot apply live: it sizes the OpenSL
    // buffer queue, which drastic reads once at startGame; the applyConfig
    // converter never re-extracts it. Everything else applies immediately
    // via applyVideoConfigLive -- including Hi-res 3D, which additionally
    // re-dims the DS textures on the render thread (DrasticRunner::
    // redimDsTextures), so it no longer needs a relaunch.
    return a.audioLatency != b.audioLatency;
}

// ------------------------------------------------------------------
// Action + keycode tables
// ------------------------------------------------------------------

const char* actionName(int a) {
    // Slot numbering matches drastic's internal action enum as observed
    // in the shared_prefs XML. Unknown slots get a generic label so the
    // Controls UI still renders them (user can bind if they know what
    // drastic does with that slot on their build).
    switch (a) {
    case 0:  return "X";
    case 1:  return "Y";
    case 2:  return "B";
    case 3:  return "A";
    case 4:  return "R";
    case 5:  return "L";
    case 6:  return "Start";
    case 7:  return "Select";
    case 12: return "D-Pad Up";
    case 13: return "D-Pad Right";
    case 14: return "D-Pad Down";
    case 15: return "D-Pad Left";
    case 16: return "Screen Swap";
    case 17: return "Fast Forward";
    case 20: return "Menu";
    case 28: return "Stylus Touch";
    default: {
        static thread_local char buf[16];
        snprintf(buf, sizeof(buf), "Action %d", a);
        return buf;
    }
    }
}

// Minimal evdev<->Android keycode table for the buttons handhelds
// typically expose. Sources: include/uapi/linux/input-event-codes.h
// + android.view.KeyEvent constants. Only the entries we need for
// drastic are listed.
namespace {
struct KeyMap { int evdev; int android; const char* label; };
const KeyMap kKeyTable[] = {
    // D-pad - some pads emit KEY_*, Xbox-style pads emit BTN_DPAD_*.
    {KEY_UP,        19, "D-Pad Up"},
    {KEY_DOWN,      20, "D-Pad Down"},
    {KEY_LEFT,      21, "D-Pad Left"},
    {KEY_RIGHT,     22, "D-Pad Right"},
    {BTN_DPAD_UP,    19, "D-Pad Up"},
    {BTN_DPAD_DOWN,  20, "D-Pad Down"},
    {BTN_DPAD_LEFT,  21, "D-Pad Left"},
    {BTN_DPAD_RIGHT, 22, "D-Pad Right"},
    // Face buttons
    {BTN_SOUTH,     96, "A"},
    {BTN_EAST,      97, "B"},
    {BTN_NORTH,     99, "X"},
    {BTN_WEST,      100, "Y"},
    // Shoulders
    {BTN_TL,        102, "L1"},
    {BTN_TR,        103, "R1"},
    {BTN_TL2,       104, "L2"},
    {BTN_TR2,       105, "R2"},
    // Thumbs
    {BTN_THUMBL,    106, "L3"},
    {BTN_THUMBR,    107, "R3"},
    // Start/Select
    {BTN_START,     108, "Start"},
    {BTN_SELECT,    109, "Select"},
    // Mode (guide), home, back
    {BTN_MODE,      110, "Mode"},
    {KEY_BACK,      4,   "Back"},
    {KEY_HOME,      3,   "Home"},
    {KEY_MENU,      82,  "Menu"},
    // Volume rockers occasionally live on handhelds
    {KEY_VOLUMEUP,    24, "Volume Up"},
    {KEY_VOLUMEDOWN,  25, "Volume Down"},
    {KEY_L,         102, "L"},  // some pads expose KEY_L instead of BTN_TL
    {KEY_R,         103, "R"},
};
} // anonymous namespace

int evdevToAndroidKeycode(int k) {
    for (auto& e : kKeyTable) if (e.evdev == k) return e.android;
    return 0;
}

int androidKeycodeToEvdev(int k) {
    // Reverse lookup prefers the "canonical" entry for each Android
    // keycode; because the table has duplicates (KEY_L <-> 102 same
    // as BTN_TL), we prefer BTN_* entries by scanning in table order.
    for (auto& e : kKeyTable) if (e.android == k) return e.evdev;
    return 0;
}

const char* androidKeycodeLabel(int k) {
    for (auto& e : kKeyTable) if (e.android == k) return e.label;
    if (k == -1) return "Unmapped";
    static thread_local char buf[32];
    snprintf(buf, sizeof(buf), "KC %d", k);
    return buf;
}

} // namespace drastic_prefs
} // namespace android
