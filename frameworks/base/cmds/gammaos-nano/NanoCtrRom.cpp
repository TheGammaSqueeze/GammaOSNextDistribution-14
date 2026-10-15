// NanoCtrRom - see NanoCtrRom.h. Port of the 3ds-web pipeline title export
// (/work/3ds/web/pipeline: build_assets.py export_title / export_banner, ctr/ncch.py, smdh.py,
// cbmd.py, lz.py, cgfx.py, texture.py, gltf.py, audio.py) and of tools/ctr_pack.py
// (BjWriter, write_pack, pack_model, model_texture_png).
//
// The output has to be what the Python tools would have produced for the same ROM, because
// ModelResource::load and the 3D renderer were written against those files. The CGFX parse
// therefore builds the same value tree as cgfx.py (same keys in the same order, the same
// round(x, 7) of every float), the glTF builder emits the same JSON and the same binary
// buffer byte for byte, and BJS1 / CPK1 are encoded exactly like ctr_pack.py does.
//
// nano is built without C++ exceptions, so where the Python code raises, the readers here
// record a sticky error instead and the caller checks it.
#include "NanoCtrRom.h"

#include <dirent.h>
#include <errno.h>
#include <fcntl.h>
#include <sys/stat.h>
#include <unistd.h>
#include <zlib.h>

#include <algorithm>
#include <array>
#include <cinttypes>
#include <cmath>
#include <cstdarg>
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <map>
#include <memory>
#include <set>
#include <tuple>
#include <unordered_map>
#include <utility>

#include <log/log.h>

#include "NanoCtrPack.h"

#undef LOG_TAG
#define LOG_TAG "NanoCtrRom"

// nano compiles with -ffast-math. The glTF buffer and the JSON floats must match what numpy
// and CPython compute (float32 sums in order, no reciprocal division, no fused multiply add),
// so this file asks for IEEE semantics explicitly. The pragma covers what the compiler decides
// per instruction (reassociation, reciprocals); fusing a multiply into a following add is
// decided by the backend from the global -ffp-contract=fast (arm64 emits fmadd even inside the
// pragma), so every product that feeds an addition goes through keep() below.
#if defined(__clang__)
#pragma float_control(precise, on, push)
#pragma clang fp contract(off)
#endif

namespace android {
namespace ctr {

const char* const kSmdhLanguages[16] = {
    "japanese", "english", "french", "german", "italian", "spanish",
    "simplifiedChinese", "korean", "dutch", "portuguese", "russian",
    "traditionalChinese", "unused12", "unused13", "unused14", "unused15",
};

namespace {

// Bumped whenever the cache layout or the conversion output changes.
constexpr int kCacheVersion = 1;

// ================================================================== small helpers

std::string format(const char* fmt, ...) __attribute__((format(printf, 1, 2)));
std::string format(const char* fmt, ...) {
    char buf[512];
    va_list ap;
    va_start(ap, fmt);
    vsnprintf(buf, sizeof(buf), fmt, ap);
    va_end(ap);
    return buf;
}

inline uint16_t le16(const uint8_t* p) { return (uint16_t)(p[0] | p[1] << 8); }
inline uint32_t le32(const uint8_t* p) { return (uint32_t)p[0] | (uint32_t)p[1] << 8 | (uint32_t)p[2] << 16 | (uint32_t)p[3] << 24; }
inline uint64_t le64(const uint8_t* p) { return (uint64_t)le32(p) | (uint64_t)le32(p + 4) << 32; }
inline uint16_t be16(const uint8_t* p) { return (uint16_t)(p[0] << 8 | p[1]); }
inline uint32_t be32(const uint8_t* p) { return (uint32_t)p[0] << 24 | (uint32_t)p[1] << 16 | (uint32_t)p[2] << 8 | p[3]; }

void put32(std::vector<uint8_t>& o, uint32_t v) {
    o.push_back((uint8_t)v); o.push_back((uint8_t)(v >> 8)); o.push_back((uint8_t)(v >> 16)); o.push_back((uint8_t)(v >> 24));
}
void put16(std::vector<uint8_t>& o, uint16_t v) { o.push_back((uint8_t)v); o.push_back((uint8_t)(v >> 8)); }
void putBe32(std::vector<uint8_t>& o, uint32_t v) {
    o.push_back((uint8_t)(v >> 24)); o.push_back((uint8_t)(v >> 16)); o.push_back((uint8_t)(v >> 8)); o.push_back((uint8_t)v);
}
void set32(std::vector<uint8_t>& o, size_t at, uint32_t v) {
    o[at] = (uint8_t)v; o[at + 1] = (uint8_t)(v >> 8); o[at + 2] = (uint8_t)(v >> 16); o[at + 3] = (uint8_t)(v >> 24);
}

// An empty asm the value has to pass through: the compiler cannot fuse the operation that made
// it with the one that uses it, nor see through it to shrink or approximate.
#if defined(__aarch64__)
#define FP_KEEP(x) __asm__("" : "+w"(x))
#elif defined(__x86_64__) || defined(__i386__)
#define FP_KEEP(x) __asm__("" : "+x"(x))
#else
#define FP_KEEP(x) __asm__("" : "+m"(x))
#endif
inline double mulD(double a, double b) { double r = a * b; FP_KEEP(r); return r; }
inline float mulF(float a, float b) { float r = a * b; FP_KEEP(r); return r; }
// Correctly rounded float32 square root (numpy's): the double root rounded to float is exact,
// and the barrier keeps the compiler from narrowing it into an approximated sqrtf.
inline float sqrtExact(float x) {
    double d = x;
    FP_KEEP(d);
    double r = std::sqrt(d);
    FP_KEEP(r);
    return (float)r;
}

// Classification by bits: -ffast-math lets the compiler fold std::isnan / std::isfinite.
inline bool isNan(double x) { uint64_t u; memcpy(&u, &x, 8); return ((u >> 52) & 0x7ff) == 0x7ff && (u & 0xfffffffffffffULL) != 0; }
inline bool isFinite(double x) { uint64_t u; memcpy(&u, &x, 8); return ((u >> 52) & 0x7ff) != 0x7ff; }
inline bool isNanF(float x) { uint32_t u; memcpy(&u, &x, 4); return ((u >> 23) & 0xff) == 0xff && (u & 0x7fffff) != 0; }

// CPython round(x, n): the correctly rounded n digit decimal, read back as a double.
double pyRound(double x, int digits) {
    if (!isFinite(x)) return x;
    char buf[400];
    snprintf(buf, sizeof(buf), "%.*f", digits, x);
    return strtod(buf, nullptr);
}

// CPython float %: the result takes the sign of the divisor.
double pyMod(double a, double b) {
    double m = fmod(a, b);
    if (m != 0) {
        if ((b < 0) != (m < 0)) m += b;
    } else {
        m = copysign(0.0, b);
    }
    return m;
}

// CPython round(x) (no digits): half to even, to an integer.
bool pyRoundInt(double x, int64_t* out) {
    if (!isFinite(x)) return false;
    double r = std::nearbyint(x);   // the default rounding mode is to nearest, ties to even
    if (r < -9.2e18 || r > 9.2e18) return false;
    *out = (int64_t)r;
    return true;
}

bool validUtf8(const uint8_t* p, size_t n) {
    size_t i = 0;
    while (i < n) {
        const unsigned c = p[i];
        if (c < 0x80) { i++; continue; }
        size_t len;
        uint32_t cp;
        if (c >= 0xC2 && c <= 0xDF) { len = 2; cp = c & 0x1F; }
        else if (c >= 0xE0 && c <= 0xEF) { len = 3; cp = c & 0x0F; }
        else if (c >= 0xF0 && c <= 0xF4) { len = 4; cp = c & 0x07; }
        else return false;
        if (i + len > n) return false;
        for (size_t k = 1; k < len; k++) {
            if ((p[i + k] & 0xC0) != 0x80) return false;
            cp = cp << 6 | (p[i + k] & 0x3F);
        }
        if (len == 3 && (cp < 0x800 || (cp >= 0xD800 && cp <= 0xDFFF))) return false;
        if (len == 4 && (cp < 0x10000 || cp > 0x10FFFF)) return false;
        i += len;
    }
    return true;
}

void appendUtf8(std::string& s, uint32_t cp) {
    if (cp < 0x80) s += (char)cp;
    else if (cp < 0x800) { s += (char)(0xC0 | cp >> 6); s += (char)(0x80 | (cp & 0x3F)); }
    else if (cp < 0x10000) { s += (char)(0xE0 | cp >> 12); s += (char)(0x80 | (cp >> 6 & 0x3F)); s += (char)(0x80 | (cp & 0x3F)); }
    else { s += (char)(0xF0 | cp >> 18); s += (char)(0x80 | (cp >> 12 & 0x3F)); s += (char)(0x80 | (cp >> 6 & 0x3F)); s += (char)(0x80 | (cp & 0x3F)); }
}

// bytes.decode("latin-1") as UTF-8 (the CGFX block magics).
std::string latin1(const uint8_t* p, size_t n) {
    std::string s;
    for (size_t i = 0; i < n; i++) appendUtf8(s, p[i]);
    return s;
}

// smdh.py _utf16: decode("utf-16le", errors="replace"), then cut at the first NUL. CPython
// replaces a lone low surrogate, or a high surrogate not followed by a low one, by U+FFFD and
// resumes at the next code unit.
std::string utf16Field(const uint8_t* p, size_t bytes) {
    std::string s;
    const size_t n = bytes / 2;
    for (size_t i = 0; i < n; i++) {
        uint32_t u = le16(p + i * 2);
        if (u >= 0xD800 && u <= 0xDBFF) {
            if (i + 1 < n) {
                const uint32_t v = le16(p + (i + 1) * 2);
                if (v >= 0xDC00 && v <= 0xDFFF) {
                    u = 0x10000 + ((u - 0xD800) << 10) + (v - 0xDC00);
                    i++;
                } else {
                    u = 0xFFFD;
                }
            } else {
                u = 0xFFFD;
            }
        } else if (u >= 0xDC00 && u <= 0xDFFF) {
            u = 0xFFFD;
        }
        if (u == 0) break;
        appendUtf8(s, u);
    }
    return s;
}

// cgfx.py safe_name: every code point outside [A-Za-z0-9_.-] becomes "_".
std::string safeName(const std::string* name) {
    if (!name || name->empty()) return "unnamed";
    std::string out;
    const std::string& s = *name;
    for (size_t i = 0; i < s.size();) {
        const unsigned char c = (unsigned char)s[i];
        if (c < 0x80) {
            out += (isalnum(c) || c == '_' || c == '.' || c == '-') ? (char)c : '_';
            i++;
            continue;
        }
        out += '_';
        i += c >= 0xF0 ? 4 : c >= 0xE0 ? 3 : 2;
    }
    return out;
}

// os.path.splitext(os.path.basename(p))[0]
std::string pyStem(const std::string& path) {
    const size_t slash = path.rfind('/');
    const std::string base = slash == std::string::npos ? path : path.substr(slash + 1);
    const size_t dot = base.rfind('.');
    if (dot == std::string::npos) return base;
    for (size_t i = 0; i < dot; i++)
        if (base[i] != '.') return base.substr(0, dot);
    return base;   // leading dots only: no extension
}

std::string lower(std::string s) {
    for (char& c : s) c = (char)tolower((unsigned char)c);
    return s;
}

// ================================================================== value tree (the Python dicts)

struct JVal;
using JMembers = std::vector<std::pair<std::string, JVal>>;

// A JSON value as the Python pipeline holds it before json.dump. Objects keep insertion order
// and assigning an existing key replaces it in place, like a dict. Ext marks numpy data that
// cgfx.to_json replaces (vertex values, face indices, decoded images).
struct JVal {
    enum Kind : uint8_t { Null, Bool, Int, Num, Str, Arr, Obj, Ext };
    Kind kind = Null;
    bool b = false;
    int64_t i = 0;
    double d = 0;
    std::string s;
    std::vector<JVal> a;
    JMembers o;

    bool isNull() const { return kind == Null; }
    bool isStr() const { return kind == Str; }
    bool isNum() const { return kind == Int || kind == Num; }
    double num() const { return kind == Int ? (double)i : kind == Num ? d : kind == Bool ? (b ? 1 : 0) : 0; }
    int64_t integer() const { return kind == Int ? i : kind == Num ? (int64_t)d : kind == Bool ? (b ? 1 : 0) : 0; }
    // Python truthiness
    bool truthy() const {
        switch (kind) {
            case Null: return false;
            case Bool: return b;
            case Int: return i != 0;
            case Num: return d != 0 || isNan(d);
            case Str: return !s.empty();
            case Arr: return !a.empty();
            case Obj: return !o.empty();
            default: return true;
        }
    }
    JVal& set(const std::string& key, JVal v) {
        for (auto& m : o)
            if (m.first == key) { m.second = std::move(v); return m.second; }
        o.emplace_back(key, std::move(v));
        return o.back().second;
    }
    const JVal* find(const std::string& key) const {
        for (const auto& m : o) if (m.first == key) return &m.second;
        return nullptr;
    }
    JVal* find(const std::string& key) {
        for (auto& m : o) if (m.first == key) return &m.second;
        return nullptr;
    }
    const JVal& operator[](const std::string& key) const {
        static const JVal kNull;
        const JVal* v = find(key);
        return v ? *v : kNull;
    }
    JVal& push(JVal v) { a.push_back(std::move(v)); return a.back(); }
    bool is(const char* str) const { return kind == Str && s == str; }
};

JVal jnull() { return JVal(); }
JVal jbool(bool v) { JVal j; j.kind = JVal::Bool; j.b = v; return j; }
JVal jint(int64_t v) { JVal j; j.kind = JVal::Int; j.i = v; return j; }
JVal jnum(double v) { JVal j; j.kind = JVal::Num; j.d = v; return j; }
JVal jstr(std::string v) { JVal j; j.kind = JVal::Str; j.s = std::move(v); return j; }
JVal jarr() { JVal j; j.kind = JVal::Arr; return j; }
JVal jobj() { JVal j; j.kind = JVal::Obj; return j; }
enum ExtKind { ExtValues = 1, ExtIndices = 2, ExtImage = 3 };
JVal jext(int kind, size_t index) { JVal j; j.kind = JVal::Ext; j.b = false; j.i = (int64_t)index; j.d = kind; return j; }
int extKind(const JVal& v) { return v.kind == JVal::Ext ? (int)v.d : 0; }

// Dictionary key for a Python value used as a key (a name that may be None).
std::string pyKey(const JVal& v) {
    if (v.kind == JVal::Str) return "s:" + v.s;
    if (v.kind == JVal::Null) return "None";
    return "i:" + std::to_string(v.integer());
}
// json.dump of a dict key: str(k) for strings, "null" for None.
std::string jsonKey(const JVal& v) {
    if (v.kind == JVal::Str) return v.s;
    if (v.kind == JVal::Null) return "null";
    return std::to_string(v.integer());
}
// f"{value}" for the values that appear in names.
std::string pyStr(const JVal& v) {
    if (v.kind == JVal::Str) return v.s;
    if (v.kind == JVal::Null) return "None";
    return std::to_string(v.integer());
}
// Python == between two numbers / lists of numbers (NaN never equal).
bool listEquals(const JVal& v, std::initializer_list<double> ref) {
    if (v.kind != JVal::Arr || v.a.size() != ref.size()) return false;
    size_t k = 0;
    for (double r : ref) { if (!(v.a[k].num() == r)) return false; k++; }
    return true;
}

// ================================================================== BJS1 (ctr_pack.py BjWriter)

class BjWriter {
public:
    std::vector<uint8_t> encode(const JVal& root) {
        std::vector<uint8_t> body;
        value(root, body);
        std::vector<uint8_t> out = {'B', 'J', 'S', '1'};
        put32(out, (uint32_t)mOrder.size());
        uint32_t off = 0;
        for (const std::string& s : mOrder) { put32(out, off); off += (uint32_t)s.size(); }
        put32(out, off);
        for (const std::string& s : mOrder) out.insert(out.end(), s.begin(), s.end());
        out.insert(out.end(), body.begin(), body.end());
        return out;
    }
private:
    std::unordered_map<std::string, uint32_t> mIds;
    std::vector<std::string> mOrder;
    uint32_t sid(const std::string& s) {
        auto it = mIds.find(s);
        if (it != mIds.end()) return it->second;
        const uint32_t id = (uint32_t)mOrder.size();
        mIds.emplace(s, id);
        mOrder.push_back(s);
        return id;
    }
    static void f32(std::vector<uint8_t>& o, double v) {
        // the pipeline's values pass through json.dump / json.load, which turns every NaN into
        // the canonical positive quiet NaN
        const float f = (float)v;
        uint32_t u;
        memcpy(&u, &f, 4);
        if (isNan(v)) u = 0x7FC00000u;
        o.push_back(4);
        put32(o, u);
    }
    void value(const JVal& v, std::vector<uint8_t>& o) {
        switch (v.kind) {
            case JVal::Null: o.push_back(0); return;
            case JVal::Bool: o.push_back(v.b ? 2 : 1); return;
            case JVal::Int:
                if (v.i >= -2147483648LL && v.i < 2147483648LL) { o.push_back(3); put32(o, (uint32_t)(int32_t)v.i); }
                else f32(o, (double)v.i);
                return;
            case JVal::Num: {
                const double d = v.d;
                if (isFinite(d) && std::trunc(d) == d && d >= -2147483648.0 && d < 2147483648.0) {
                    o.push_back(3);
                    put32(o, (uint32_t)(int32_t)d);
                } else {
                    f32(o, d);
                }
                return;
            }
            case JVal::Str: o.push_back(5); put32(o, sid(v.s)); return;
            case JVal::Arr: {
                o.push_back(6);
                put32(o, (uint32_t)v.a.size());
                const size_t sizeAt = o.size();
                put32(o, 0);
                for (const JVal& x : v.a) value(x, o);
                set32(o, sizeAt, (uint32_t)(o.size() - sizeAt - 4));
                return;
            }
            case JVal::Obj: {
                o.push_back(7);
                put32(o, (uint32_t)v.o.size());
                const size_t sizeAt = o.size();
                put32(o, 0);
                for (const auto& m : v.o) { put32(o, sid(m.first)); value(m.second, o); }
                set32(o, sizeAt, (uint32_t)(o.size() - sizeAt - 4));
                return;
            }
            default: o.push_back(0); return;   // numpy data never reaches the encoder
        }
    }
};

std::vector<uint8_t> bj(const JVal& v) { return BjWriter().encode(v); }

// ================================================================== bounded reader (cgfx.py _Reader)

class Rd {
public:
    Rd(const uint8_t* d, size_t n) : mD(d), mN(n) {}
    const uint8_t* data() const { return mD; }
    size_t size() const { return mN; }
    bool ok() const { return mErr.empty(); }
    const std::string& error() const { return mErr; }
    void fail(const std::string& e) { if (mErr.empty()) mErr = e; }
    bool has(int64_t o, int64_t n) {
        if (o < 0 || n < 0 || (uint64_t)o + (uint64_t)n > mN) {
            fail(format("read of %" PRId64 " bytes at 0x%" PRIx64 " leaves the data", n, (uint64_t)o));
            return false;
        }
        return true;
    }
    uint8_t u8(int64_t o) { return has(o, 1) ? mD[o] : 0; }
    int16_t i16(int64_t o) { return has(o, 2) ? (int16_t)le16(mD + o) : 0; }
    uint16_t u16(int64_t o) { return has(o, 2) ? le16(mD + o) : 0; }
    uint32_t u32(int64_t o) { return has(o, 4) ? le32(mD + o) : 0; }
    int32_t i32(int64_t o) { return has(o, 4) ? (int32_t)le32(mD + o) : 0; }
    float rawF32(int64_t o) { float f = 0; if (has(o, 4)) memcpy(&f, mD + o, 4); return f; }
    double f32(int64_t o) { return pyRound(rawF32(o), 7); }
    std::vector<uint32_t> u32s(int64_t o, int64_t n) {
        std::vector<uint32_t> v;
        if (!has(o, n * 4)) return v;
        v.resize((size_t)n);
        for (int64_t k = 0; k < n; k++) v[(size_t)k] = le32(mD + o + k * 4);
        return v;
    }
    JVal f32s(int64_t o, int64_t n) {
        JVal v = jarr();
        if (!has(o, n * 4)) return v;
        for (int64_t k = 0; k < n; k++) v.push(jnum(f32(o + k * 4)));
        return v;
    }
    bool magicIs(int64_t o, const char* m) { return has(o, 4) && memcmp(mD + o, m, 4) == 0; }
    std::string magic(int64_t o) { return has(o, 4) ? latin1(mD + o, 4) : std::string(); }
    // Self-relative pointer; -1 is null.
    int64_t ptr(int64_t o) {
        const int32_t v = i32(o);
        if (!v) return -1;
        const int64_t t = o + v;
        if (t < 0 || t >= (int64_t)mN) { fail(format("pointer at 0x%" PRIx64 " leaves the file", (uint64_t)o)); return -1; }
        return t;
    }
    JVal string(int64_t o) {
        const int64_t t = ptr(o);
        if (t < 0) return jnull();
        const void* z = memchr(mD + t, 0, mN - (size_t)t);
        if (!z) { fail("unterminated string"); return jnull(); }
        const size_t n = (size_t)((const uint8_t*)z - (mD + t));
        if (!validUtf8(mD + t, n)) { fail("string is not UTF-8"); return jnull(); }
        return jstr(std::string((const char*)mD + t, n));
    }
    struct Entry { JVal name; int64_t off; };
    // (count, pointer) to a DICT patricia tree, entries in order.
    std::vector<Entry> dict(int64_t o) {
        std::vector<Entry> out;
        const uint32_t count = u32(o);
        const int64_t base = ptr(o + 4);
        if (!count || !ok()) return out;
        if (base < 0 || !magicIs(base, "DICT") || u32(base + 8) != count) { fail(format("bad DICT at 0x%" PRIx64, (uint64_t)base)); return out; }
        const int64_t nodes = base + 0x1C;
        if (!has(nodes, (int64_t)count * 16)) return out;
        for (uint32_t k = 0; k < count && ok(); k++) {
            Entry e;
            e.name = string(nodes + k * 16 + 8);
            e.off = ptr(nodes + k * 16 + 12);
            if (e.off < 0) fail("null DICT entry");
            out.push_back(std::move(e));
        }
        return out;
    }
    // (count, pointer) to an array of self-relative pointers (-1 entries are null).
    std::vector<int64_t> list(int64_t o) {
        std::vector<int64_t> out;
        const uint32_t count = u32(o);
        const int64_t base = ptr(o + 4);
        if (!count || !ok()) return out;
        if (base < 0) { fail("null list"); return out; }
        if (!has(base, (int64_t)count * 4)) return out;
        for (uint32_t k = 0; k < count && ok(); k++) out.push_back(ptr(base + k * 4));
        return out;
    }
    JVal array(int64_t o) {
        JVal v = jarr();
        const uint32_t count = u32(o);
        const int64_t base = ptr(o + 4);
        if (!count || !ok()) return v;
        if (base < 0) { fail("null array"); return v; }
        for (uint32_t x : u32s(base, count)) v.push(jint(x));
        return v;
    }
    // (byte count, pointer) to raw bytes; like a Python slice it stops at the end of the file.
    std::pair<const uint8_t*, size_t> blob(int64_t o) {
        const uint32_t n = u32(o);
        const int64_t base = ptr(o + 4);
        if (!n || !ok()) return {nullptr, 0};
        if (base < 0) { fail("null blob"); return {nullptr, 0}; }
        return {mD + base, std::min<size_t>(n, mN - (size_t)base)};
    }
private:
    const uint8_t* mD;
    size_t mN;
    std::string mErr;
};

// ================================================================== textures (texture.py)

struct Image { uint32_t w = 0, h = 0; std::vector<uint8_t> rgba; };

const char* const kTextureFormats[] = {"RGBA8", "RGB8", "RGBA5551", "RGB565", "RGBA4", "LA8", "HILO8", "L8", "A8",
                                       "LA4", "L4", "A4", "ETC1", "ETC1A4"};
const int kFormatBpp[] = {32, 24, 16, 16, 16, 16, 16, 8, 8, 8, 4, 4, 4, 8};

int formatIndex(const JVal& fmt) {
    if (fmt.kind != JVal::Str) return -1;
    for (int k = 0; k < 14; k++) if (fmt.s == kTextureFormats[k]) return k;
    return -1;
}

const int kEtc1Modifiers[8][4] = {{2, 8, -2, -8}, {5, 17, -5, -17}, {9, 29, -9, -29}, {13, 42, -13, -42},
                                  {18, 60, -18, -60}, {24, 80, -24, -80}, {33, 106, -33, -106}, {47, 183, -47, -183}};

inline uint8_t expandBits(uint32_t v, int bits) { return (uint8_t)((v << (8 - bits)) | (v >> (2 * bits - 8))); }

// texture.py decode: the top level of a PICA200 texture as RGBA8, rows in PICA t order
// (bottom row of the tiled data first, as the Python code flips with image[::-1]).
bool decodeTexture(const uint8_t* raw, size_t len, uint32_t w, uint32_t h, int fmt, Image* out, std::string* err) {
    if (w % 8 || h % 8) { *err = format("texture size %ux%u is not a multiple of 8", w, h); return false; }
    if (fmt < 0) { *err = "unsupported texture format"; return false; }
    if (!w || !h || (uint64_t)w * h > (1u << 24)) { *err = format("texture size %ux%u is not supported", w, h); return false; }
    const size_t count = (size_t)w * h;
    const size_t need = count * kFormatBpp[fmt] / 8;
    if (len < need) { *err = format("%s %ux%u needs %zu bytes, got %zu", kTextureFormats[fmt], w, h, need, len); return false; }
    out->w = w;
    out->h = h;
    out->rgba.assign(count * 4, 0);
    uint8_t* img = out->rgba.data();
    if (fmt == 12 || fmt == 13) {
        // _decode_etc1
        const bool alpha = fmt == 13;
        const size_t stride = alpha ? 16 : 8;
        const size_t blocks = count / 16;
        const uint32_t tilesX = w / 8;
        for (size_t b = 0; b < blocks; b++) {
            const uint8_t* blk = raw + b * stride;
            const uint64_t word = le64(blk + stride - 8);
            const int64_t hi = (int64_t)(word >> 32), lo = (int64_t)(word & 0xFFFFFFFFu);
            const int64_t diff = (hi >> 1) & 1, flip = hi & 1;
            const int64_t tables[2] = {(hi >> 5) & 7, (hi >> 2) & 7};
            int64_t base[2][3];
            const int shifts[3] = {24, 16, 8};
            for (int c = 0; c < 3; c++) {
                const int sh = shifts[c];
                if (diff) {
                    const int64_t b5 = (hi >> (sh + 3)) & 31;
                    int64_t d3 = (hi >> sh) & 7;
                    if (d3 >= 4) d3 -= 8;
                    const int64_t b5b = (b5 + d3) & 31;
                    base[0][c] = (b5 << 3) | (b5 >> 2);
                    base[1][c] = (b5b << 3) | (b5b >> 2);
                } else {
                    base[0][c] = ((hi >> (sh + 4)) & 15) * 17;
                    base[1][c] = ((hi >> sh) & 15) * 17;
                }
            }
            const uint64_t alphaWord = alpha ? le64(blk) : 0;
            // block b is (tile row, tile column, block y, block x) in that order
            const size_t tile = b / 4, sub = b % 4;
            const uint32_t tileRow = (uint32_t)(tile / tilesX), tileCol = (uint32_t)(tile % tilesX);
            const uint32_t by = (uint32_t)(sub / 2), bx = (uint32_t)(sub % 2);
            for (int idx = 0; idx < 16; idx++) {
                const int px = idx / 4, py = idx % 4, bit = idx;
                const int64_t sel = (((lo >> (16 + bit)) & 1) << 1) | ((lo >> bit) & 1);
                const int s = flip ? (py >= 2) : (px >= 2);
                const int mod = kEtc1Modifiers[tables[s]][sel];
                const uint32_t row = tileRow * 8 + by * 4 + py, col = tileCol * 8 + bx * 4 + px;
                uint8_t* o = img + ((size_t)(h - 1 - row) * w + col) * 4;
                for (int c = 0; c < 3; c++) o[c] = (uint8_t)std::clamp<int64_t>(base[s][c] + mod, 0, 255);
                o[3] = alpha ? (uint8_t)(((alphaWord >> (bit * 4)) & 15) * 17) : 255;
            }
        }
        return true;
    }
    // _decode_linear into tiled order, then _untile (8x8 Morton tiles, rows flipped)
    std::vector<uint8_t> px(count * 4);
    for (size_t k = 0; k < count; k++) {
        uint8_t* o = &px[k * 4];
        switch (fmt) {
            case 0: { const uint8_t* p = raw + k * 4; o[0] = p[3]; o[1] = p[2]; o[2] = p[1]; o[3] = p[0]; break; }
            case 1: { const uint8_t* p = raw + k * 3; o[0] = p[2]; o[1] = p[1]; o[2] = p[0]; o[3] = 255; break; }
            case 2: {
                const uint32_t v = le16(raw + k * 2);
                o[0] = expandBits((v >> 11) & 31, 5); o[1] = expandBits((v >> 6) & 31, 5); o[2] = expandBits((v >> 1) & 31, 5);
                o[3] = (uint8_t)((v & 1) * 255);
                break;
            }
            case 3: {
                const uint32_t v = le16(raw + k * 2);
                o[0] = expandBits((v >> 11) & 31, 5); o[1] = expandBits((v >> 5) & 63, 6); o[2] = expandBits(v & 31, 5); o[3] = 255;
                break;
            }
            case 4: {
                const uint32_t v = le16(raw + k * 2);
                o[0] = (uint8_t)(((v >> 12) & 15) * 17); o[1] = (uint8_t)(((v >> 8) & 15) * 17);
                o[2] = (uint8_t)(((v >> 4) & 15) * 17); o[3] = (uint8_t)((v & 15) * 17);
                break;
            }
            case 5: { const uint8_t* p = raw + k * 2; o[0] = o[1] = o[2] = p[1]; o[3] = p[0]; break; }
            case 6: { const uint8_t* p = raw + k * 2; o[0] = p[1]; o[1] = p[0]; o[2] = 0; o[3] = 255; break; }
            case 7: o[0] = o[1] = o[2] = raw[k]; o[3] = 255; break;
            case 8: o[0] = o[1] = o[2] = 255; o[3] = raw[k]; break;
            case 9: o[0] = o[1] = o[2] = (uint8_t)((raw[k] >> 4) * 17); o[3] = (uint8_t)((raw[k] & 15) * 17); break;
            case 10: case 11: {
                const uint8_t n = (uint8_t)(((k & 1) ? raw[k / 2] >> 4 : raw[k / 2] & 15) * 17);
                if (fmt == 10) { o[0] = o[1] = o[2] = n; o[3] = 255; }
                else { o[0] = o[1] = o[2] = 255; o[3] = n; }
                break;
            }
            default: break;
        }
    }
    const uint32_t tilesX = w / 8;
    for (size_t k = 0; k < count; k++) {
        const size_t tile = k / 64;
        const uint32_t m = (uint32_t)(k % 64);
        const uint32_t x = (m & 1) | ((m >> 1) & 2) | ((m >> 2) & 4);
        const uint32_t y = ((m >> 1) & 1) | ((m >> 2) & 2) | ((m >> 3) & 4);
        const uint32_t row = (uint32_t)(tile / tilesX) * 8 + y, col = (uint32_t)(tile % tilesX) * 8 + x;
        memcpy(img + ((size_t)(h - 1 - row) * w + col) * 4, &px[k * 4], 4);
    }
    return true;
}

// ================================================================== CGFX (cgfx.py)

struct FloatArray { uint32_t rows = 0, cols = 0; std::vector<float> v; };

struct Scene {
    JVal root;
    std::vector<FloatArray> values;               // stream attribute "values"
    std::vector<std::vector<uint32_t>> indices;   // face "indices" (astype uint32)
    std::vector<Image> images;                    // texture "image"
};

const char* const kSections[] = {"models", "textures", "lookupTables", "materials", "shaders", "cameras", "lights",
                                 "fogs", "environments", "skeletalAnimations", "materialAnimations",
                                 "visibilityAnimations", "cameraAnimations", "lightAnimations", "emitters"};
const char* const kAttributeNames[] = {"position", "normal", "tangent", "color", "texCoord0", "texCoord1", "texCoord2",
                                       "boneIndex", "boneWeight", "user0", "user1", "user2", "user3", "user4", "user5",
                                       "user6", "user7", "user8", "user9", "user10", "user11", "interleave"};
const char* const kSkinning[] = {"none", "rigid", "smooth"};
const char* const kPrimitives[] = {"triangles", "triangleStrip", "triangleFan"};
const char* const kTranslucency[] = {"opaque", "translucent", "subtractive", "additive"};
const char* const kMappingTypes[] = {"uv", "cameraCubeEnv", "cameraSphereEnv", "projection", "shadow", "shadowCube"};
const char* const kTransformTypes[] = {"maya", "softimage", "max"};
const char* const kTexCoordConfigs[] = {"0120", "0110", "0111", "0112", "0121", "0122"};
const char* const kFaceCulling[] = {"front", "back", "always", "never"};
const char* const kCullModes[] = {"none", "front", "back"};
const char* const kBlendModes[] = {"none", "blend", "separateBlend", "logicOp"};
const char* const kTestFuncs[] = {"never", "always", "equal", "notEqual", "less", "lessEqual", "greater", "greaterEqual"};
const char* const kStencilOps[] = {"keep", "zero", "replace", "increment", "decrement", "invert", "incrementWrap", "decrementWrap"};
const char* const kBlendEquations[] = {"add", "subtract", "reverseSubtract", "min", "max"};
const char* const kBlendFactors[] = {"zero", "one", "srcColor", "oneMinusSrcColor", "dstColor", "oneMinusDstColor",
                                     "srcAlpha", "oneMinusSrcAlpha", "dstAlpha", "oneMinusDstAlpha", "constantColor",
                                     "oneMinusConstantColor", "constantAlpha", "oneMinusConstantAlpha", "srcAlphaSaturate"};
const char* const kWrapModes[] = {"clampToEdge", "clampToBorder", "repeat", "mirroredRepeat"};
const char* const kMinFilters[] = {"nearest", "linear", "nearestMipmapNearest", "linearMipmapNearest",
                                   "nearestMipmapLinear", "linearMipmapLinear"};
const char* const kAlphaOperands[] = {"alpha", "oneMinusAlpha", "red", "oneMinusRed", "green", "oneMinusGreen", "blue", "oneMinusBlue"};
const char* const kCombiners[] = {"replace", "modulate", "add", "addSigned", "interpolate", "subtract",
                                  "dot3Rgb", "dot3Rgba", "multAdd", "addMult"};
const char* const kLutInputs[] = {"nh", "vh", "nv", "ln", "sp", "cp"};
const char* const kLutSlots[] = {"reflectanceR", "reflectanceG", "reflectanceB", "distribution0", "distribution1", "fresnel"};
const char* const kFresnel[] = {"none", "primary", "secondary", "both"};
const char* const kBumpModes[] = {"none", "bump", "tangent"};
const char* const kLightKinds[] = {"directional", "point", "spot"};
const char* const kBillboard[] = {"off", "world", "worldViewpoint", "screen", "screenViewpoint", "yAxial", "yAxialViewpoint"};
const char* const kLoopModes[] = {"once", "loop"};
const char* const kRepeatMethods[] = {"none", "repeat", "mirror", "relativeRepeat"};
const char* const kInterpolations[] = {"step", "linear", "hermite"};
const char* const kQuantizations[] = {"hermite128", "hermite64", "hermite48", "unifiedHermite96",
                                      "unifiedHermite48", "unifiedHermite32", "stepLinear64", "stepLinear32"};
const char* const kPrimitiveTypes[] = {"float", "int", "bool", "vector2", "vector3", "transform", "rgba",
                                       "texture", "quatTransform", "matrixTransform"};
const char* const kGroupObjects[] = {"bone", "materialColor", "textureSampler", "textureMapper", "blendOperation",
                                     "textureCoordinator", "model", "mesh", "meshNode", "transform", "viewUpdater",
                                     "projectionUpdater", "light", "fragmentLight"};
const char* const kMaterialColors[] = {"emission", "ambient", "diffuse", "specular0", "specular1",
                                       "constant0", "constant1", "constant2", "constant3", "constant4", "constant5"};
const char* const kLightColors[] = {"ambient", "diffuse", "specular0", "specular1"};
const char* const kTransformCurves[] = {"scaleX", "scaleY", "scaleZ", "rotationX", "rotationY", "rotationZ",
                                        "rotationW", "translationX", "translationY", "translationZ"};
const char* const kMaterialFlags[] = {"fragmentLighting", "vertexLighting", "hemiLighting", "hemiOcclusion", "fog", "polygonOffset"};
const char* const kFragmentFlags[] = {"clampHighlight", "distribution0", "distribution1", "geometricFactor0",
                                      "geometricFactor1", "reflection"};
const char* const kBoneFlags[] = {"identity", "translateZero", "rotateZero", "scaleOne", "uniformScale",
                                  "segmentScaleCompensate", "needRendering", "localMatrixCalculate",
                                  "worldMatrixCalculate", "hasSkinningMatrix"};

#define TABLE(t) t, sizeof(t) / sizeof(t[0])

// cgfx.py _enum for list tables: the name, or the raw value when out of range.
JVal enumOf(const char* const* table, size_t n, uint32_t v) { return v < n ? jstr(table[v]) : jint(v); }
JVal combineSource(uint32_t v) {
    static const char* const k[] = {"primaryColor", "fragmentPrimaryColor", "fragmentSecondaryColor",
                                    "texture0", "texture1", "texture2", "texture3"};
    if (v <= 6) return jstr(k[v]);
    if (v == 13) return jstr("previousBuffer");
    if (v == 14) return jstr("constant");
    if (v == 15) return jstr("previous");
    return jint(v);
}
JVal colorOperand(uint32_t v) {
    switch (v) {
        case 0: return jstr("color");
        case 1: return jstr("oneMinusColor");
        case 2: return jstr("alpha");
        case 3: return jstr("oneMinusAlpha");
        case 4: return jstr("red");
        case 5: return jstr("oneMinusRed");
        case 8: return jstr("green");
        case 9: return jstr("oneMinusGreen");
        case 12: return jstr("blue");
        case 13: return jstr("oneMinusBlue");
        default: return jint(v);
    }
}
JVal lutScale(uint32_t v) {
    switch (v) {
        case 0: return jnum(1.0);
        case 1: return jnum(2.0);
        case 2: return jnum(4.0);
        case 3: return jnum(8.0);
        case 6: return jnum(0.25);
        case 7: return jnum(0.5);
        default: return jint(v);
    }
}
JVal bitsOf(uint32_t value, const char* const* names, size_t n) {
    JVal v = jarr();
    for (size_t b = 0; b < n; b++) if (value >> b & 1) v.push(jstr(names[b]));
    return v;
}
JVal rgbaOf(uint32_t v) {
    JVal o = jarr();
    o.push(jint(v & 0xFF)); o.push(jint(v >> 8 & 0xFF)); o.push(jint(v >> 16 & 0xFF)); o.push(jint(v >> 24));
    return o;
}
JVal matrixOf(const JVal& values) {
    JVal m = jarr();
    for (size_t r = 0; r < 3; r++) {
        JVal row = jarr();
        for (size_t c = 0; c < 4; c++) if (r * 4 + c < values.a.size()) row.push(values.a[r * 4 + c]);
        m.push(std::move(row));
    }
    return m;
}
// PICA200 float20: sign 1, exponent 7 (bias 63), mantissa 12.
double float20(uint32_t v) {
    const double sign = (v >> 19 & 1) ? -1.0 : 1.0;
    const uint32_t e = v >> 12 & 0x7F, m = v & 0xFFF;
    if (!e && !m) return sign * 0.0;
    return sign * (1 + m / 4096.0) * ldexp(1.0, (int)e - 63);
}
// pica_commands: a PICA200 command list as (register, value) pairs.
std::vector<std::pair<uint32_t, uint32_t>> picaCommands(const std::vector<uint32_t>& w) {
    std::vector<std::pair<uint32_t, uint32_t>> out;
    size_t i = 0;
    while (i + 1 < w.size()) {
        const uint32_t param = w[i], header = w[i + 1];
        const uint32_t reg = header & 0xFFFF, extra = header >> 20 & 0x7FF, consecutive = header >> 31;
        std::vector<uint32_t> values = {param};
        for (size_t k = i + 2; k < i + 2 + extra && k < w.size(); k++) values.push_back(w[k]);
        for (size_t k = 0; k < values.size(); k++) out.push_back({consecutive ? reg + (uint32_t)k : reg, values[k]});
        i += 2 + extra;
        i += i & 1;
    }
    return out;
}
std::map<uint32_t, uint32_t> registers(const std::vector<uint32_t>& w) {
    std::map<uint32_t, uint32_t> m;
    for (const auto& c : picaCommands(w)) m[c.first] = c.second;
    return m;
}
uint32_t regGet(const std::map<uint32_t, uint32_t>& m, uint32_t reg) {
    auto it = m.find(reg);
    return it == m.end() ? 0 : it->second;
}

const int kKeySizes[8] = {16, 8, 6, 12, 6, 4, 8, 4};

class CgfxParser {
public:
    CgfxParser(const uint8_t* d, size_t n, Scene* scene) : r(d, n), mScene(scene) {}

    bool parse(std::string* err) {
        const uint8_t* d = r.data();
        const size_t n = r.size();
        if (n < 20 || memcmp(d, "CGFX", 4) != 0) { *err = "not a CGFX file"; return false; }
        const uint16_t bom = le16(d + 4), headerSize = le16(d + 6);
        const uint32_t version = le32(d + 8), fileSize = le32(d + 12);
        if (bom != 0xFEFF) { *err = "only little-endian CGFX is supported"; return false; }
        if (fileSize != n) { *err = "file size mismatch"; return false; }
        if (!r.magicIs(headerSize, "DATA")) { *err = "missing DATA block"; return false; }
        std::vector<std::vector<Rd::Entry>> sections;
        for (int k = 0; k < 15; k++) sections.push_back(r.dict(headerSize + 8 + k * 8));
        if (!r.ok()) { *err = r.error(); return false; }
        JVal out = jobj();
        out.set("version", jint(version));
        for (int k = 0; k < 15 && r.ok(); k++) {
            if (sections[k].empty()) continue;
            if (k == 3 || k == 7 || k == 14) { *err = format("section %s is not supported", kSections[k]); return false; }
            JVal list = jarr();
            for (const Rd::Entry& e : sections[k]) {
                if (!r.ok()) break;
                switch (k) {
                    case 0: list.push(model(e.off)); break;
                    case 1: list.push(texture(e.off)); break;
                    case 2: list.push(lookupTable(e.off)); break;
                    case 4: list.push(shader(e.off)); break;
                    case 5: list.push(camera(e.off)); break;
                    case 6: list.push(light(e.off)); break;
                    case 8: list.push(environment(e.off)); break;
                    default: list.push(animation(e.off)); break;
                }
            }
            out.set(kSections[k], std::move(list));
        }
        if (!r.ok()) { *err = r.error(); return false; }
        mScene->root = std::move(out);
        return true;
    }

private:
    Rd r;
    Scene* mScene;

    bool need(int64_t p, const char* what) {
        if (p >= 0) return true;
        r.fail(format("null %s pointer", what));
        return false;
    }

    // shared structures

    JVal header(int64_t o, const std::string& magicName) {
        if (r.magic(o + 4) != magicName) r.fail(format("expected %s at 0x%" PRIx64 ", got %s", magicName.c_str(), (uint64_t)(o + 4), r.magic(o + 4).c_str()));
        JVal out = jobj();
        out.set("name", r.string(o + 12));
        out.set("typeFlags", jint(r.u32(o)));
        out.set("revision", jint(r.u32(o + 8)));
        JVal meta = metadata(o + 16);
        if (!meta.o.empty()) out.set("metadata", std::move(meta));
        return out;
    }

    JVal metadata(int64_t o) {
        JVal m = jobj();
        for (const Rd::Entry& e : r.dict(o)) {
            JVal v = jobj();
            v.set("typeFlags", jint(r.u32(e.off)));
            m.set(jsonKey(e.name), std::move(v));
        }
        return m;
    }

    void node(JVal& out, int64_t o) {
        out.set("nodeFlags", jint(r.u32(o + 0x18)));
        out.set("isBranchVisible", jbool(r.u32(o + 0x1C) != 0));
        out.set("childCount", jint(r.u32(o + 0x20)));
        JVal groups = jarr();
        for (const Rd::Entry& e : r.dict(o + 0x28)) groups.push(animGroup(e.off));
        out.set("animationGroups", std::move(groups));
        out.set("scale", r.f32s(o + 0x30, 3));
        out.set("rotation", r.f32s(o + 0x3C, 3));
        out.set("translation", r.f32s(o + 0x48, 3));
        out.set("localMatrix", matrixOf(r.f32s(o + 0x54, 12)));
        out.set("worldMatrix", matrixOf(r.f32s(o + 0x84, 12)));
    }

    JVal animGroup(int64_t o) {
        JVal g = jobj();
        g.set("name", r.string(o + 8));
        g.set("typeFlags", jint(r.u32(o)));
        g.set("flags", jint(r.u32(o + 4)));
        g.set("memberType", jint(r.u32(o + 12)));
        g.set("blendOperations", r.array(o + 0x18));
        g.set("evaluationTiming", jint(r.u32(o + 0x20)));
        JVal elements = jarr();
        for (const Rd::Entry& e : r.dict(o + 0x10)) {
            const int64_t p = e.off;
            const uint32_t obj = r.u32(p + 28);
            JVal el = jobj();
            el.set("path", e.name);
            el.set("typeFlags", jint(r.u32(p)));
            el.set("object", enumOf(TABLE(kGroupObjects), obj));
            el.set("target", r.string(p + 8));
            el.set("member", r.string(p + 12));
            el.set("memberOffset", jint(r.u32(p + 16)));
            el.set("memberSize", jint(r.u32(p + 20)));
            el.set("blendOperationIndex", jint(r.u32(p + 24)));
            el.set("memberType", jint(r.u32(p + 32)));
            if (obj == 2 || obj == 3 || obj == 5) el.set("index", jint(r.u32(p + 44)));
            else if (obj == 7) el.set("index", jint(r.u32(p + 40)));
            elements.push(std::move(el));
        }
        g.set("elements", std::move(elements));
        return g;
    }

    JVal lutInput(int64_t o) {
        const int64_t ref = r.ptr(o + 8);
        JVal v = jobj();
        v.set("input", enumOf(TABLE(kLutInputs), r.u32(o)));
        v.set("scale", lutScale(r.u32(o + 4)));
        v.set("table", ref >= 0 ? r.string(ref + 4) : jnull());
        v.set("sampler", ref >= 0 ? r.string(ref + 8) : jnull());
        return v;
    }

    JVal reference(int64_t o) {
        JVal v = jobj();
        v.set("name", r.string(o + 12));
        v.set("path", r.string(o + 0x18));
        return v;
    }

    // models

    JVal model(int64_t o) {
        JVal out = header(o, "CMDL");
        node(out, o);
        const int64_t base = o + 0xB4;
        JVal meshes = jarr();
        for (int64_t p : r.list(base)) { if (!need(p, "mesh")) break; meshes.push(mesh(p)); }
        out.set("meshes", std::move(meshes));
        JVal materials = jarr();
        for (const Rd::Entry& e : r.dict(base + 8)) materials.push(material(e.off));
        out.set("materials", std::move(materials));
        JVal shapes = jarr();
        for (int64_t p : r.list(base + 0x10)) { if (!need(p, "shape")) break; shapes.push(shape(p)); }
        out.set("shapes", std::move(shapes));
        JVal vis = jarr();
        for (const Rd::Entry& e : r.dict(base + 0x18)) vis.push(e.name);
        out.set("meshNodeVisibilities", std::move(vis));
        out.set("flags", jint(r.u32(base + 0x20)));
        out.set("faceCulling", jint(r.u32(base + 0x24)));
        out.set("layerId", jint(r.u32(base + 0x28)));
        const bool hasSkeleton = (out["typeFlags"].integer() & 0x80) != 0;
        if (hasSkeleton) {
            const int64_t p = r.ptr(base + 0x2C);
            out.set("skeleton", need(p, "skeleton") ? skeleton(p) : jnull());
        } else {
            out.set("skeleton", jnull());
        }
        return out;
    }

    JVal mesh(int64_t o) {
        JVal out = header(o, "SOBJ");
        out.set("shapeIndex", jint(r.u32(o + 0x18)));
        out.set("materialIndex", jint(r.u32(o + 0x1C)));
        out.set("isVisible", jbool(r.u8(o + 0x24) != 0));
        out.set("renderPriority", jint(r.u8(o + 0x25)));
        out.set("meshNodeIndex", jint(r.i16(o + 0x26)));
        out.set("meshNodeName", r.string(o + 0x70));
        return out;
    }

    JVal skeleton(int64_t o) {
        JVal out = header(o, "SOBJ");
        std::vector<JVal> bones;
        for (const Rd::Entry& e : r.dict(o + 0x18)) {
            const int64_t p = e.off;
            JVal bone = jobj();
            bone.set("name", r.string(p));
            bone.set("flags", bitsOf(r.u32(p + 4), TABLE(kBoneFlags)));
            bone.set("index", jint(r.u32(p + 8)));
            bone.set("parentIndex", jint(r.i32(p + 12)));
            bone.set("scale", r.f32s(p + 0x20, 3));
            bone.set("rotation", r.f32s(p + 0x2C, 3));
            bone.set("translation", r.f32s(p + 0x38, 3));
            bone.set("localMatrix", matrixOf(r.f32s(p + 0x44, 12)));
            bone.set("worldMatrix", matrixOf(r.f32s(p + 0x74, 12)));
            bone.set("inverseWorldMatrix", matrixOf(r.f32s(p + 0xA4, 12)));
            bone.set("billboard", enumOf(TABLE(kBillboard), r.u32(p + 0xD4)));
            JVal meta = metadata(p + 0xD8);
            if (!meta.o.empty()) bone.set("metadata", std::move(meta));
            bones.push_back(std::move(bone));
        }
        std::stable_sort(bones.begin(), bones.end(), [](const JVal& a, const JVal& b) { return a["index"].integer() < b["index"].integer(); });
        const int64_t root = r.ptr(o + 0x20);
        out.set("rootBone", root >= 0 ? r.string(root) : jnull());
        out.set("scalingRule", jint(r.u32(o + 0x24)));
        out.set("flags", jint(r.u32(o + 0x28)));
        JVal list = jarr();
        list.a = std::move(bones);
        out.set("bones", std::move(list));
        return out;
    }

    JVal shape(int64_t o) {
        JVal out = header(o, "SOBJ");
        out.set("flags", jint(r.u32(o + 0x18)));
        const int64_t box = r.ptr(o + 0x1C);
        if (box < 0) {
            out.set("boundingBox", jnull());
        } else {
            JVal b = jobj();
            b.set("center", r.f32s(box + 4, 3));
            JVal orient = jarr();
            for (int k = 0; k < 3; k++) orient.push(r.f32s(box + 0x10 + k * 12, 3));
            b.set("orientation", std::move(orient));
            b.set("size", r.f32s(box + 0x34, 3));
            out.set("boundingBox", std::move(b));
        }
        out.set("positionOffset", r.f32s(o + 0x20, 3));
        JVal subs = jarr();
        for (int64_t p : r.list(o + 0x2C)) { if (!need(p, "submesh")) break; subs.push(submesh(p)); }
        out.set("subMeshes", std::move(subs));
        out.set("baseAddress", jint(r.u32(o + 0x34)));
        int64_t vertexCount = 0;
        JVal attributes = jarr();
        for (int64_t p : r.list(o + 0x38)) {
            if (!need(p, "vertex buffer")) break;
            int64_t count = 0;
            vertexBuffer(p, &count, &attributes);
            vertexCount = std::max(vertexCount, count);
        }
        out.set("vertexCount", jint(vertexCount));
        out.set("attributes", std::move(attributes));
        out.set("hasBlendShape", jbool(r.ptr(o + 0x40) >= 0));
        return out;
    }

    JVal submesh(int64_t o) {
        JVal faces = jarr();
        for (int64_t face : r.list(o + 12)) {
            if (!need(face, "face")) break;
            for (int64_t d : r.list(face)) {
                if (!need(d, "face data")) break;
                const uint32_t type = r.u32(d);
                int size = 0;
                const char* kind = nullptr;
                switch (type) {
                    case 0x1400: kind = "byte"; size = 1; break;
                    case 0x1401: kind = "ubyte"; size = 1; break;
                    case 0x1402: kind = "short"; size = 2; break;
                    case 0x1403: kind = "ushort"; size = 2; break;
                    case 0x1406: kind = "float"; size = 4; break;
                    default: r.fail(format("unknown index type 0x%x", type)); return jnull();
                }
                JVal f = jobj();
                f.set("format", jstr(kind));
                f.set("primitive", enumOf(TABLE(kPrimitives), r.u8(d + 4)));
                f.set("visible", jbool(r.u8(d + 5) != 0));
                auto blob = r.blob(d + 8);
                if (blob.second % size) { r.fail("index buffer size is not a multiple of the element size"); return jnull(); }
                std::vector<uint32_t> idx(blob.second / size);
                for (size_t k = 0; k < idx.size(); k++) {
                    const uint8_t* p = blob.first + k * size;
                    switch (type) {
                        case 0x1400: idx[k] = (uint32_t)(int32_t)(int8_t)p[0]; break;
                        case 0x1401: idx[k] = p[0]; break;
                        case 0x1402: idx[k] = (uint32_t)(int32_t)(int16_t)le16(p); break;
                        case 0x1403: idx[k] = le16(p); break;
                        default: { float fv; memcpy(&fv, p, 4); idx[k] = floatToU32(fv); break; }
                    }
                }
                mScene->indices.push_back(std::move(idx));
                f.set("indices", jext(ExtIndices, mScene->indices.size() - 1));
                faces.push(std::move(f));
            }
        }
        JVal out = jobj();
        out.set("boneIndices", r.array(o));
        out.set("skinning", enumOf(TABLE(kSkinning), r.u32(o + 8)));
        out.set("faces", std::move(faces));
        return out;
    }

    // numpy float32 -> uint32 cast for float index buffers (never used by real files).
    static uint32_t floatToU32(float f) {
        if (isNanF(f) || f <= -1.0f || f >= 4294967296.0f) return 0;
        return (uint32_t)(int64_t)f;
    }

    static int glTypeSize(uint32_t fmt, const char** name) {
        switch (fmt) {
            case 0x1400: *name = "byte"; return 1;
            case 0x1401: *name = "ubyte"; return 1;
            case 0x1402: *name = "short"; return 2;
            case 0x1403: *name = "ushort"; return 2;
            case 0x1406: *name = "float"; return 4;
            default: *name = nullptr; return 0;
        }
    }

    void vertexBuffer(int64_t o, int64_t* vertexCount, JVal* attributes) {
        const uint32_t kind = r.u32(o);
        if (kind == 0x40000002) {
            auto raw = r.blob(o + 0x14);
            const uint32_t stride = r.u32(o + 0x24);
            if (!stride) { r.fail("vertex stride is zero"); return; }
            const int64_t count = (int64_t)(raw.second / stride);
            for (int64_t p : r.list(o + 0x28)) {
                if (!need(p, "attribute")) return;
                attributes->push(streamAttribute(p, raw, stride, count, -1));
            }
            *vertexCount = count;
            return;
        }
        if (kind == 0x40000001) {
            const uint32_t fmt = r.u32(o + 0x24), elements = r.u32(o + 0x28);
            auto raw = r.blob(o + 0x14);
            const char* name;
            const int size = glTypeSize(fmt, &name);
            if (!size) { r.fail(format("unknown attribute format 0x%x", fmt)); return; }
            const uint64_t stride = (uint64_t)size * elements;
            if (!stride) { r.fail("vertex stride is zero"); return; }
            const int64_t count = (int64_t)(raw.second / stride);
            attributes->push(streamAttribute(o, raw, stride, count, 0));
            *vertexCount = count;
            return;
        }
        if (kind == 0x80000000) {
            JVal a = jobj();
            a.set("name", enumOf(TABLE(kAttributeNames), r.u32(o + 4)));
            a.set("kind", jstr("constant"));
            const char* name;
            if (!glTypeSize(r.u32(o + 0x0C), &name)) { r.fail("unknown attribute format"); return; }
            a.set("format", jstr(name));
            a.set("elements", jint(r.u32(o + 0x10)));
            const int64_t p = r.ptr(o + 0x1C);
            if (!need(p, "constant value")) return;
            a.set("value", r.f32s(p, r.u32(o + 0x18)));
            attributes->push(std::move(a));
            *vertexCount = 0;
            return;
        }
        r.fail(format("unknown vertex buffer kind 0x%x at 0x%" PRIx64, kind, (uint64_t)o));
    }

    JVal streamAttribute(int64_t o, std::pair<const uint8_t*, size_t> raw, uint64_t stride, int64_t count, int64_t offsetArg) {
        const uint32_t fmt = r.u32(o + 0x24), elements = r.u32(o + 0x28);
        const double scale = r.f32(o + 0x2C);
        const int64_t offset = offsetArg < 0 ? (int64_t)r.u32(o + 0x30) : offsetArg;
        const char* kind;
        const int size = glTypeSize(fmt, &kind);
        if (!size) { r.fail(format("unknown attribute format 0x%x", fmt)); return jnull(); }
        if (count > 0 && (uint64_t)offset + (uint64_t)size * elements > stride) { r.fail("attribute leaves the vertex"); return jnull(); }
        FloatArray arr;
        arr.rows = (uint32_t)count;
        arr.cols = elements;
        arr.v.resize((size_t)count * elements);
        // values.astype(np.float32) * np.float32(scale)
        const float sc = (float)scale;
        for (int64_t row = 0; row < count; row++) {
            const uint8_t* base = raw.first + row * stride + offset;
            for (uint32_t c = 0; c < elements; c++) {
                const uint8_t* p = base + c * size;
                float v = 0;
                switch (fmt) {
                    case 0x1400: v = (float)(int8_t)p[0]; break;
                    case 0x1401: v = (float)p[0]; break;
                    case 0x1402: v = (float)(int16_t)le16(p); break;
                    case 0x1403: v = (float)le16(p); break;
                    default: memcpy(&v, p, 4); break;
                }
                arr.v[(size_t)row * elements + c] = mulF(v, sc);
            }
        }
        mScene->values.push_back(std::move(arr));
        JVal a = jobj();
        a.set("name", enumOf(TABLE(kAttributeNames), r.u32(o + 4)));
        a.set("kind", jstr("stream"));
        a.set("format", jstr(kind));
        a.set("elements", jint(elements));
        a.set("scale", jnum(scale));
        a.set("offset", jint(offset));
        a.set("values", jext(ExtValues, mScene->values.size() - 1));
        return a;
    }

    // materials

    JVal material(int64_t o) {
        JVal out = header(o, "MTOB");
        out.set("flags", bitsOf(r.u32(o + 0x18), TABLE(kMaterialFlags)));
        out.set("texCoordConfig", enumOf(TABLE(kTexCoordConfigs), r.u32(o + 0x1C)));
        out.set("translucency", enumOf(TABLE(kTranslucency), r.u32(o + 0x20)));
        JVal colors = jobj();
        for (int k = 0; k < 11; k++) colors.set(kMaterialColors[k], r.f32s(o + 0x24 + k * 16, 4));
        out.set("colors", std::move(colors));
        JVal bytes = jobj();
        for (int k = 0; k < 11; k++) bytes.set(kMaterialColors[k], rgbaOf(r.u32(o + 0xD4 + k * 4)));
        out.set("colorBytes", std::move(bytes));
        const uint32_t cull = regGet(registers(r.u32s(o + 0x110, 2)), 0x40);
        JVal ras = jobj();
        ras.set("polygonOffsetEnabled", jbool(r.u32(o + 0x104) & 1));
        ras.set("faceCulling", enumOf(TABLE(kFaceCulling), r.u32(o + 0x108)));
        ras.set("polygonOffsetUnit", jnum(r.f32(o + 0x10C)));
        ras.set("cullMode", enumOf(TABLE(kCullModes), cull & 3));
        out.set("rasterization", std::move(ras));
        out.set("fragmentOperation", fragmentOperation(o + 0x118));
        const uint32_t coordCount = r.u32(o + 0x168);
        JVal coords = jarr();
        if (r.has(o + 0x16C, (int64_t)coordCount * 0x58))
            for (uint32_t k = 0; k < coordCount; k++) coords.push(texCoord(o + 0x16C + k * 0x58));
        out.set("textureCoordinators", std::move(coords));
        JVal mappers = jarr();
        for (int k = 0; k < 3; k++) {
            const int64_t p = r.ptr(o + 0x274 + k * 4);
            mappers.push(p < 0 ? jnull() : textureMapper(p));
        }
        out.set("textureMappers", std::move(mappers));
        out.set("hasProceduralTexture", jbool(r.ptr(o + 0x280) >= 0));
        const int64_t sh = r.ptr(o + 0x284);
        out.set("shader", sh < 0 ? jnull() : reference(sh));
        const int64_t fs = r.ptr(o + 0x288);
        out.set("fragmentShader", need(fs, "fragment shader") ? fragmentShader(fs) : jnull());
        out.set("shaderProgramDescriptionIndex", jint(r.u32(o + 0x28C)));
        out.set("shaderParameterCount", jint(r.u32(o + 0x290)));
        out.set("lightSetIndex", jint(r.i32(o + 0x298)));
        out.set("fogIndex", jint(r.i32(o + 0x29C)));
        return out;
    }

    JVal fragmentOperation(int64_t o) {
        const uint32_t depth = regGet(registers(r.u32s(o + 4, 4)), 0x107);
        const auto blend = registers(r.u32s(o + 0x28, 6));
        const auto stencil = registers(r.u32s(o + 0x40, 4));
        const uint32_t func = regGet(blend, 0x101);
        const uint32_t test = regGet(stencil, 0x105), op = regGet(stencil, 0x106);
        JVal d = jobj();
        d.set("flags", jint(r.u32(o)));
        d.set("testEnabled", jbool(depth & 1));
        d.set("function", enumOf(TABLE(kTestFuncs), depth >> 4 & 7));
        d.set("writeEnabled", jbool(depth >> 12 & 1));
        JVal mask = jarr();
        for (int k = 0; k < 4; k++) mask.push(jbool(depth >> (8 + k) & 1));
        d.set("colorMask", std::move(mask));
        JVal b = jobj();
        b.set("mode", enumOf(TABLE(kBlendModes), r.u32(o + 0x14)));
        b.set("color", r.f32s(o + 0x18, 4));
        b.set("enabled", jbool(regGet(blend, 0x100) >> 8 & 1));
        b.set("colorEquation", enumOf(TABLE(kBlendEquations), func & 7));
        b.set("alphaEquation", enumOf(TABLE(kBlendEquations), func >> 8 & 7));
        b.set("colorSrc", enumOf(TABLE(kBlendFactors), func >> 16 & 15));
        b.set("colorDst", enumOf(TABLE(kBlendFactors), func >> 20 & 15));
        b.set("alphaSrc", enumOf(TABLE(kBlendFactors), func >> 24 & 15));
        b.set("alphaDst", enumOf(TABLE(kBlendFactors), func >> 28 & 15));
        b.set("logicOp", jint(regGet(blend, 0x102) & 15));
        b.set("constantColor", rgbaOf(regGet(blend, 0x103)));
        JVal s = jobj();
        s.set("testEnabled", jbool(test & 1));
        s.set("function", enumOf(TABLE(kTestFuncs), test >> 4 & 7));
        s.set("writeMask", jint(test >> 8 & 0xFF));
        s.set("reference", jint(test >> 16 & 0xFF));
        s.set("mask", jint(test >> 24 & 0xFF));
        s.set("failOp", enumOf(TABLE(kStencilOps), op & 7));
        s.set("zFailOp", enumOf(TABLE(kStencilOps), op >> 4 & 7));
        s.set("zPassOp", enumOf(TABLE(kStencilOps), op >> 8 & 7));
        JVal out = jobj();
        out.set("depth", std::move(d));
        out.set("blend", std::move(b));
        out.set("stencil", std::move(s));
        return out;
    }

    JVal texCoord(int64_t o) {
        JVal v = jobj();
        v.set("sourceIndex", jint(r.u32(o)));
        v.set("mapping", enumOf(TABLE(kMappingTypes), r.u32(o + 4)));
        v.set("referenceCamera", jint(r.i32(o + 8)));
        v.set("transformType", enumOf(TABLE(kTransformTypes), r.u32(o + 12)));
        v.set("scale", r.f32s(o + 16, 2));
        v.set("rotation", jnum(r.f32(o + 24)));
        v.set("translation", r.f32s(o + 28, 2));
        v.set("flags", jint(r.u32(o + 36)));
        v.set("matrix", matrixOf(r.f32s(o + 40, 12)));
        return v;
    }

    JVal textureMapper(int64_t o) {
        const auto regs = registers(r.u32s(o + 0x10, r.u32(o + 0x48) / 4));
        static const uint32_t kUnitRegs[3] = {0x83, 0x93, 0x9B}, kBases[3] = {0x80, 0x90, 0x98};
        int unit = 0;
        for (int u = 0; u < 3; u++) if (regs.count(kUnitRegs[u])) { unit = u; break; }
        const uint32_t base = kBases[unit];
        const uint32_t param = regGet(regs, base + 3), lod = regGet(regs, base + 4);
        const int64_t tex = r.ptr(o + 8), sampler = r.ptr(o + 12);
        JVal out = jobj();
        out.set("texture", tex < 0 ? jnull() : reference(tex)["path"]);
        out.set("wrapS", enumOf(TABLE(kWrapModes), param >> 12 & 7));
        out.set("wrapT", enumOf(TABLE(kWrapModes), param >> 8 & 7));
        out.set("magFilter", jstr(param >> 1 & 1 ? "linear" : "nearest"));
        out.set("minFilter", jstr(param >> 2 & 1 ? "linear" : "nearest"));
        out.set("mipFilter", jstr(param >> 24 & 1 ? "linear" : "nearest"));
        out.set("borderColor", rgbaOf(regGet(regs, base + 1)));
        out.set("lodBias", jnum((double)((int32_t)((lod & 0x1FFF) ^ 0x1000) - 0x1000) / 256));
        out.set("maxLevel", jint(lod >> 16 & 15));
        out.set("minLevel", jint(lod >> 24 & 15));
        out.set("sampler", jnull());
        if (sampler >= 0) {
            JVal s = jobj();
            s.set("minFilter", enumOf(TABLE(kMinFilters), r.u32(sampler + 8)));
            s.set("borderColor", r.f32s(sampler + 12, 4));
            s.set("lodBias", jnum(r.f32(sampler + 28)));
            out.set("sampler", std::move(s));
        }
        return out;
    }

    JVal fragmentShader(int64_t o) {
        JVal out = jobj();
        out.set("bufferColor", r.f32s(o, 4));
        out.set("flags", bitsOf(r.u32(o + 0x10), TABLE(kFragmentFlags)));
        out.set("layerConfig", jint(r.u32(o + 0x14)));
        out.set("fresnel", enumOf(TABLE(kFresnel), r.u32(o + 0x18)));
        out.set("bumpTexture", jint(r.u32(o + 0x1C)));
        out.set("bumpMode", enumOf(TABLE(kBumpModes), r.u32(o + 0x20)));
        out.set("bumpRenormalize", jbool(r.u32(o + 0x24) != 0));
        out.set("lookupTables", jobj());
        JVal stages = jarr();
        for (int k = 0; k < 6; k++) {
            const int64_t s = o + 0x2C + k * 0x1C;
            stages.push(combiner(r.u32(s), r.u32s(s + 4, 6)));
        }
        out.set("stages", std::move(stages));
        const int64_t luts = r.ptr(o + 0x28);
        JVal* tables = out.find("lookupTables");
        for (int k = 0; k < 6; k++) {
            const int64_t p = luts < 0 ? -1 : r.ptr(luts + k * 4);
            if (p >= 0) tables->set(kLutSlots[k], lutInput(p));
        }
        const auto regs = registers(r.u32s(o + 0xD4, 8));
        const uint32_t alpha = regGet(regs, 0x104), update = regGet(regs, 0xE0);
        JVal at = jobj();
        at.set("enabled", jbool(alpha & 1));
        at.set("function", enumOf(TABLE(kTestFuncs), alpha >> 4 & 7));
        at.set("reference", jint(alpha >> 8 & 0xFF));
        out.set("alphaTest", std::move(at));
        JVal bi = jarr();
        for (int k = 0; k < 4; k++) {
            JVal e = jobj();
            e.set("color", jbool(update >> (8 + k) & 1));
            e.set("alpha", jbool(update >> (12 + k) & 1));
            bi.push(std::move(e));
        }
        out.set("bufferInput", std::move(bi));
        out.set("bufferColorBytes", rgbaOf(regGet(regs, 0xFD)));
        return out;
    }

    JVal combiner(uint32_t constant, const std::vector<uint32_t>& words) {
        const auto cmds = picaCommands(words);
        if (cmds.size() < 5) { r.fail("texture combiner has fewer than five registers"); return jnull(); }
        const uint32_t source = cmds[0].second, operand = cmds[1].second, combine = cmds[2].second,
                       color = cmds[3].second, scale = cmds[4].second;
        JVal out = jobj();
        out.set("constant", jint(constant));
        JVal cs = jarr(), as = jarr(), co = jarr(), ao = jarr();
        for (int k = 0; k < 3; k++) cs.push(combineSource(source >> (4 * k) & 15));
        for (int k = 0; k < 3; k++) as.push(combineSource(source >> (16 + 4 * k) & 15));
        for (int k = 0; k < 3; k++) co.push(colorOperand(operand >> (4 * k) & 15));
        for (int k = 0; k < 3; k++) ao.push(enumOf(TABLE(kAlphaOperands), operand >> (12 + 4 * k) & 7));
        out.set("colorSources", std::move(cs));
        out.set("alphaSources", std::move(as));
        out.set("colorOperands", std::move(co));
        out.set("alphaOperands", std::move(ao));
        out.set("colorCombiner", enumOf(TABLE(kCombiners), combine & 15));
        out.set("alphaCombiner", enumOf(TABLE(kCombiners), combine >> 16 & 15));
        out.set("constantColor", rgbaOf(color));
        out.set("colorScale", jint(1 << (scale & 3)));
        out.set("alphaScale", jint(1 << (scale >> 16 & 3)));
        return out;
    }

    // textures, lookup tables, shaders

    JVal texture(int64_t o) {
        JVal out = header(o, "TXOB");
        const uint32_t kind = (uint32_t)out["typeFlags"].integer();
        if (kind != 0x20000011) {
            out.set("kind", jstr(kind == 0x20000009 ? "cube" : "reference"));
            return out;
        }
        const uint32_t fmt = r.u32(o + 0x34);
        const int64_t image = r.ptr(o + 0x38);
        out.set("kind", jstr("image"));
        const uint32_t w = r.u32(o + 0x1C), h = r.u32(o + 0x18);
        out.set("width", jint(w));
        out.set("height", jint(h));
        out.set("glFormat", jint(r.u32(o + 0x20)));
        out.set("glType", jint(r.u32(o + 0x24)));
        out.set("mipmapLevels", jint(r.u32(o + 0x28)));
        out.set("format", enumOf(TABLE(kTextureFormats), fmt));
        if (!need(image, "texture image")) return jnull();
        out.set("bitsPerPixel", jint(r.u32(image + 0x14)));
        if (r.u32(image + 4) != w || r.u32(image) != h) {
            r.fail(format("texture %s image size mismatch", pyStr(out["name"]).c_str()));
            return jnull();
        }
        auto raw = r.blob(image + 8);
        if (!r.ok()) return jnull();
        Image img;
        std::string err;
        if (!decodeTexture(raw.first, raw.second, w, h, formatIndex(out["format"]), &img, &err)) { r.fail(err); return jnull(); }
        mScene->images.push_back(std::move(img));
        out.set("image", jext(ExtImage, mScene->images.size() - 1));
        return out;
    }

    JVal lookupTable(int64_t o) {
        JVal out = header(o, "LUTS");
        JVal samplers = jarr();
        for (const Rd::Entry& e : r.dict(o + 0x18)) {
            const int64_t p = e.off;
            auto blob = r.blob(p + 12);
            std::vector<uint32_t> words(blob.second / 4);
            for (size_t k = 0; k < words.size(); k++) words[k] = le32(blob.first + k * 4);
            JVal s = jobj();
            s.set("name", r.string(p + 4));
            s.set("isSizeDoubled", jbool(r.u32(p + 8) != 0));
            JVal values = jarr();
            for (const auto& c : picaCommands(words))
                if (c.first >= 0x1C8 && c.first <= 0x1CF) values.push(jnum(pyRound((c.second & 0xFFF) / 4095.0, 6)));
            s.set("values", std::move(values));
            samplers.push(std::move(s));
        }
        out.set("samplers", std::move(samplers));
        return out;
    }

    JVal shader(int64_t o) {
        JVal out = header(o, "SHDR");
        if (out["typeFlags"].integer() != 0x80000002) {
            out.set("kind", jstr("reference"));
            out.set("path", r.string(o + 0x18));
            return out;
        }
        auto blob = r.blob(o + 0x18);
        out.set("kind", jstr("binary"));
        out.set("binarySize", jint((int64_t)blob.second));
        out.set("programs", dvlbPrograms(blob.first, blob.second));
        return out;
    }

    // _dvlb_programs: DVLE programs with entry points and uniform names.
    JVal dvlbPrograms(const uint8_t* d, size_t n) {
        JVal programs = jarr();
        if (n < 4 || memcmp(d, "DVLB", 4) != 0) return programs;
        Rd b(d, n);
        const uint32_t count = b.u32(4);
        if (!b.has(8, (int64_t)count * 4)) { r.fail(b.error()); return programs; }
        for (uint32_t k = 0; k < count && b.ok(); k++) {
            const int64_t base = b.u32(8 + k * 4);
            if (!(base + 4 <= (int64_t)n && memcmp(d + base, "DVLE", 4) == 0)) continue;
            const uint32_t main = b.u32(base + 8), endMain = b.u32(base + 12);
            const uint32_t uniformOffset = b.u32(base + 0x30), uniformCount = b.u32(base + 0x34);
            const int64_t symbols = base + b.u32(base + 0x38);
            JVal uniforms = jarr();
            if (b.has(base + uniformOffset, (int64_t)uniformCount * 8)) {
                for (uint32_t u = 0; u < uniformCount && b.ok(); u++) {
                    const int64_t e = base + uniformOffset + u * 8;
                    const uint32_t nameOffset = b.u32(e);
                    const uint16_t start = b.u16(e + 4), end = b.u16(e + 6);
                    const int64_t p = symbols + nameOffset;
                    if (!b.has(p, 0) || p >= (int64_t)n) { b.fail("uniform name leaves the shader"); break; }
                    const void* z = memchr(d + p, 0, n - (size_t)p);
                    if (!z) { b.fail("unterminated uniform name"); break; }
                    const size_t len = (size_t)((const uint8_t*)z - (d + p));
                    for (size_t c = 0; c < len; c++) if (d[p + c] >= 0x80) b.fail("uniform name is not ASCII");
                    JVal un = jobj();
                    un.set("name", jstr(std::string((const char*)d + p, len)));
                    un.set("start", jint(start));
                    un.set("end", jint(end));
                    uniforms.push(std::move(un));
                }
            }
            JVal prog = jobj();
            prog.set("type", jstr(b.u8(base + 6) ? "geometry" : "vertex"));
            prog.set("mainOffset", jint(main));
            prog.set("endMainOffset", jint(endMain));
            prog.set("uniforms", std::move(uniforms));
            programs.push(std::move(prog));
        }
        if (!b.ok()) r.fail(b.error());
        return programs;
    }

    // cameras, lights, environments

    JVal camera(int64_t o) {
        JVal out = header(o, "CCAM");
        node(out, o);
        const int64_t base = o + 0xB4;
        out.set("viewType", jint(r.u32(base)));
        out.set("projectionType", jint(r.u32(base + 4)));
        const int64_t view = r.ptr(base + 8);
        if (!need(view, "camera view")) return jnull();
        const uint32_t kind = r.u32(view);
        JVal v = jobj();
        if (kind == 0x80000000) {
            v.set("kind", jstr("aim"));
            v.set("targetPosition", r.f32s(view + 8, 3));
            v.set("twist", jnum(r.f32(view + 20)));
        } else if (kind == 0x40000000) {
            v.set("kind", jstr("lookAt"));
            v.set("targetPosition", r.f32s(view + 8, 3));
            v.set("upVector", r.f32s(view + 20, 3));
        } else {
            v.set("kind", jstr("rotate"));
            v.set("rotation", r.f32s(view + 8, 3));
        }
        v.set("flags", jint(r.u32(view + 4)));
        out.set("view", std::move(v));
        const int64_t proj = r.ptr(base + 12);
        if (!need(proj, "camera projection")) return jnull();
        const uint32_t pk = r.u32(proj);
        JVal p = jobj();
        p.set("kind", pk == 0x80000000 ? jstr("frustum") : pk == 0x40000000 ? jstr("orthographic")
                      : pk == 0x20000000 ? jstr("perspective") : jint(pk));
        p.set("near", jnum(r.f32(proj + 4)));
        p.set("far", jnum(r.f32(proj + 8)));
        p.set("aspectRatio", jnum(r.f32(proj + 12)));
        p.set(pk == 0x20000000 ? "fovY" : "height", jnum(r.f32(proj + 16)));
        out.set("projection", std::move(p));
        out.set("wScale", jnum(r.f32(base + 16)));
        return out;
    }

    JVal light(int64_t o) {
        const std::string magicName = r.magic(o + 4);
        JVal out = header(o, magicName);
        node(out, o);
        const int64_t base = o + 0xB4;
        out.set("isEnabled", jbool(r.u32(base) != 0));
        if (magicName != "CFLT") {
            out.set("kind", jstr(magicName));
            return out;
        }
        const int64_t distance = r.ptr(base + 0x64), angle = r.ptr(base + 0x68);
        out.set("kind", enumOf(TABLE(kLightKinds), r.u32(base + 4)));
        JVal colors = jobj(), bytes = jobj();
        for (int k = 0; k < 4; k++) colors.set(kLightColors[k], r.f32s(base + 8 + k * 16, 4));
        for (int k = 0; k < 4; k++) bytes.set(kLightColors[k], rgbaOf(r.u32(base + 0x48 + k * 4)));
        out.set("colors", std::move(colors));
        out.set("colorBytes", std::move(bytes));
        out.set("direction", r.f32s(base + 0x58, 3));
        out.set("distanceLookupTable", distance < 0 ? jnull() : lutInput(distance));
        out.set("angleLookupTable", angle < 0 ? jnull() : lutInput(angle));
        out.set("distanceAttenuationStart", jnum(r.f32(base + 0x6C)));
        out.set("distanceAttenuationEnd", jnum(r.f32(base + 0x70)));
        out.set("distanceAttenuationScale", jnum(float20(r.u32(base + 0x74))));
        out.set("distanceAttenuationBias", jnum(float20(r.u32(base + 0x78))));
        out.set("flags", jint(r.u32(base + 0x7C)));
        return out;
    }

    JVal refs(int64_t p) {
        JVal list = jarr();
        for (int64_t q : r.list(p)) {
            if (!need(q, "reference")) break;
            JVal e = jobj();
            e.set("index", jint(r.u32(q)));
            e.set("name", r.string(q + 4));
            list.push(std::move(e));
        }
        return list;
    }

    JVal environment(int64_t o) {
        JVal out = header(o, "CENV");
        out.set("cameras", refs(o + 0x18));
        JVal sets = jarr();
        for (int64_t q : r.list(o + 0x20)) {
            if (!need(q, "light set")) break;
            JVal e = jobj();
            e.set("index", jint(r.u32(q)));
            e.set("lights", refs(q + 4));
            sets.push(std::move(e));
        }
        out.set("lightSets", std::move(sets));
        out.set("fogs", refs(o + 0x28));
        return out;
    }

    // animations

    JVal animation(int64_t o) {
        if (!r.magicIs(o, "CANM")) { r.fail(format("expected CANM at 0x%" PRIx64, (uint64_t)o)); return jnull(); }
        JVal out = jobj();
        out.set("name", r.string(o + 8));
        out.set("revision", jint(r.u32(o + 4)));
        out.set("targetAnimationGroup", r.string(o + 12));
        out.set("loopMode", enumOf(TABLE(kLoopModes), r.u32(o + 16)));
        out.set("frameCount", jnum(r.f32(o + 20)));
        JVal elements = jarr();
        for (const Rd::Entry& e : r.dict(o + 24)) elements.push(animElement(e.off));
        out.set("elements", std::move(elements));
        JVal meta = metadata(o + 32);
        if (!meta.o.empty()) out.set("metadata", std::move(meta));
        return out;
    }

    JVal animElement(int64_t o) {
        const uint32_t flags = r.u32(o), kind = r.u32(o + 16);
        JVal out = jobj();
        out.set("name", r.string(o + 4));
        out.set("target", r.string(o + 8));
        out.set("member", r.string(o + 12));
        out.set("type", enumOf(TABLE(kPrimitiveTypes), kind));
        out.set("flags", jint(flags));
        const int64_t body = o + 20;
        if (kind == 5) {
            JVal curves = jobj();
            for (int k = 0; k < 10; k++) {
                if (!(flags >> (16 + k) & 1)) {
                    JVal c = animValue(body + k * 4, flags >> (6 + k) & 1);
                    if (!c.isNull()) curves.set(kTransformCurves[k], std::move(c));
                }
            }
            out.set("curves", std::move(curves));
        } else if (kind == 3 || kind == 4 || kind == 6) {
            static const char* const k2[] = {"x", "y"};
            static const char* const k3[] = {"x", "y", "z"};
            static const char* const k4[] = {"r", "g", "b", "a"};
            const char* const* names = kind == 3 ? k2 : kind == 4 ? k3 : k4;
            const int n = kind == 3 ? 2 : kind == 4 ? 3 : 4;
            JVal curves = jobj();
            for (int k = 0; k < n; k++) {
                if (!(flags >> (n + k) & 1)) {
                    JVal c = animValue(body + k * 4, flags >> k & 1);
                    if (!c.isNull()) curves.set(names[k], std::move(c));
                }
            }
            out.set("curves", std::move(curves));
        } else if (kind == 0 || kind == 1) {
            out.set("curve", (flags >> 1 & 1) ? jnull() : animValue(body, flags & 1));
        } else if (kind == 7) {
            out.set("curve", animValue(body, flags & 1));
            JVal textures = jarr();
            for (int64_t p : r.list(body + 4)) {
                if (!need(p, "texture reference")) break;
                textures.push(reference(p)["path"]);
            }
            out.set("textures", std::move(textures));
        } else {
            r.fail(format("animation element type %s at 0x%" PRIx64 " is not supported",
                          pyStr(enumOf(TABLE(kPrimitiveTypes), kind)).c_str(), (uint64_t)o));
        }
        return out;
    }

    JVal animValue(int64_t o, bool constant) {
        if (constant) {
            JVal v = jobj();
            v.set("constant", jnum(r.f32(o)));
            return v;
        }
        const int64_t p = r.ptr(o);
        return p < 0 ? jnull() : curve(p);
    }

    JVal curve(int64_t o) {
        JVal out = jobj();
        out.set("startFrame", jnum(r.f32(o)));
        out.set("endFrame", jnum(r.f32(o + 4)));
        out.set("preRepeat", enumOf(TABLE(kRepeatMethods), r.u8(o + 8)));
        out.set("postRepeat", enumOf(TABLE(kRepeatMethods), r.u8(o + 9)));
        out.set("flags", jint(r.u32(o + 12)));
        const uint32_t count = r.u32(o + 16);
        JVal segs = jarr();
        if (r.has(o + 20, (int64_t)count * 4)) {
            for (uint32_t k = 0; k < count && r.ok(); k++) {
                const int64_t p = r.ptr(o + 20 + k * 4);
                if (!need(p, "curve segment")) break;
                segs.push(segment(p));
            }
        }
        out.set("segments", std::move(segs));
        return out;
    }

    JVal segment(int64_t o) {
        const uint32_t flags = r.u32(o + 8);
        JVal out = jobj();
        out.set("startFrame", jnum(r.f32(o)));
        out.set("endFrame", jnum(r.f32(o + 4)));
        out.set("flags", jint(flags));
        if (flags & 1) {
            out.set("interpolation", jstr("constant"));
            out.set("value", jnum(r.f32(o + 12)));
            return out;
        }
        const uint32_t quant = flags >> 5 & 7;
        out.set("interpolation", enumOf(TABLE(kInterpolations), flags >> 2 & 7));
        out.set("quantization", enumOf(TABLE(kQuantizations), quant));
        const uint32_t count = r.u32(o + 12);
        int64_t p = o + 20;
        double scale = 1.0, offset = 0.0;
        if (quant != 0 && quant != 3 && quant != 6) {
            scale = r.f32(p);
            offset = r.f32(p + 4);
            out.set("valueScale", jnum(scale));
            out.set("valueOffset", jnum(offset));
            p += 8;
        }
        const double start = out["startFrame"].num();
        JVal keys = jarr();
        if (r.has(p, (int64_t)count * kKeySizes[quant])) {
            const uint8_t* d = r.data();
            for (uint32_t k = 0; k < count; k++) {
                const uint8_t* q = d + p + (int64_t)k * kKeySizes[quant];
                std::vector<double> key;
                auto f = [&](int at) { float v; memcpy(&v, q + at, 4); return (double)v; };
                switch (quant) {
                    case 0: key = {f(0), f(4), f(8), f(12)}; break;
                    case 1: {
                        const uint32_t packed = le32(q);
                        const int16_t in = (int16_t)le16(q + 4), outS = (int16_t)le16(q + 6);
                        key = {(double)(packed & 0xFFF), mulD((double)(packed >> 12), scale) + offset, in / 256.0, outS / 256.0};
                        break;
                    }
                    case 2: {
                        const uint64_t v = (uint64_t)le32(q) | (uint64_t)le16(q + 4) << 32;
                        const int64_t in = (int64_t)((v >> 24 & 0xFFF) ^ 0x800) - 0x800;
                        const int64_t outS = (int64_t)((v >> 36 & 0xFFF) ^ 0x800) - 0x800;
                        key = {(double)(v & 0xFFF), mulD((double)(v >> 12 & 0xFFF), scale) + offset, in / 32.0, outS / 32.0};
                        break;
                    }
                    case 3: key = {f(0), f(4), f(8)}; break;
                    case 4: {
                        const uint16_t frame = le16(q), value = le16(q + 2);
                        const int16_t slope = (int16_t)le16(q + 4);
                        key = {frame / 32.0, mulD(value, scale) + offset, slope / 256.0};
                        break;
                    }
                    case 5: {
                        const uint32_t v = le32(q);
                        const int64_t slope = (int64_t)((v >> 20) ^ 0x800) - 0x800;
                        key = {(double)(v & 0xFF), mulD((double)(v >> 8 & 0xFFF), scale) + offset, slope / 32.0};
                        break;
                    }
                    case 6: key = {f(0), f(4)}; break;
                    default: {
                        const uint32_t v = le32(q);
                        key = {(double)(v & 0xFFF), mulD((double)(v >> 12), scale) + offset};
                        break;
                    }
                }
                JVal kv = jarr();
                for (size_t c = 0; c < key.size(); c++) {
                    double x = pyRound(key[c], 6);
                    if (c == 0) x = pyRound(x + start, 6);
                    kv.push(jnum(x));
                }
                keys.push(std::move(kv));
            }
        }
        out.set("keys", std::move(keys));
        return out;
    }
};

// cgfx.py to_json: images become their file URIs, vertex arrays their bounds, index arrays
// their counts.
JVal toJson(const Scene& sc, const JVal& v, const std::map<std::string, std::string>& uris) {
    if (v.kind == JVal::Obj) {
        JVal out = jobj();
        for (const auto& m : v.o) {
            if (m.first == "image") {
                auto it = uris.find(pyKey(v["name"]));
                out.set("uri", it == uris.end() ? jnull() : jstr(it->second));
            } else if (m.first == "values" && extKind(m.second) == ExtValues) {
                const FloatArray& a = sc.values[(size_t)m.second.i];
                JVal mn = jarr(), mx = jarr();
                if (a.rows) {
                    for (uint32_t c = 0; c < a.cols; c++) {
                        float lo = a.v[c], hi = a.v[c];
                        bool nan = isNanF(lo);
                        for (uint32_t row = 1; row < a.rows; row++) {
                            const float x = a.v[(size_t)row * a.cols + c];
                            if (isNanF(x)) nan = true;
                            if (x < lo) lo = x;
                            if (x > hi) hi = x;
                        }
                        mn.push(jnum(nan ? NAN : pyRound(lo, 6)));
                        mx.push(jnum(nan ? NAN : pyRound(hi, 6)));
                    }
                }
                out.set("min", std::move(mn));
                out.set("max", std::move(mx));
            } else if (m.first == "indices") {
                out.set("indexCount", jint(extKind(m.second) == ExtIndices ? (int64_t)sc.indices[(size_t)m.second.i].size() : 0));
            } else {
                out.set(m.first, toJson(sc, m.second, uris));
            }
        }
        return out;
    }
    if (v.kind == JVal::Arr) {
        JVal out = jarr();
        out.a.reserve(v.a.size());
        for (const JVal& x : v.a) out.push(toJson(sc, x, uris));
        return out;
    }
    return v;
}

// ================================================================== glTF (gltf.py)

constexpr int kFloat = 5126, kUnsignedShort = 5123, kUnsignedInt = 5125;
constexpr int kArrayBuffer = 34962, kElementArrayBuffer = 34963;

// euler_to_quaternion: XYZ euler angles (radians) to [x, y, z, w].
std::array<double, 4> eulerToQuaternion(double x, double y, double z) {
    const double cx = cos(x / 2), sx = sin(x / 2), cy = cos(y / 2), sy = sin(y / 2), cz = cos(z / 2), sz = sin(z / 2);
    return {mulD(mulD(sx, cy), cz) - mulD(mulD(cx, sy), sz), mulD(mulD(cx, sy), cz) + mulD(mulD(sx, cy), sz),
            mulD(mulD(cx, cy), sz) - mulD(mulD(sx, sy), cz), mulD(mulD(cx, cy), cz) + mulD(mulD(sx, sy), sz)};
}

// cgfx.py evaluate_curve / _evaluate_segment in double precision.
bool evaluateCurve(const JVal& curve, double frame, double* out) {
    if (const JVal* c = curve.find("constant")) { *out = c->num(); return true; }
    const double start = curve["startFrame"].num(), end = curve["endFrame"].num();
    const double length = end - start;
    if (length > 0 && (frame < start || frame > end)) {
        const JVal& method = frame < start ? curve["preRepeat"] : curve["postRepeat"];
        if (method.is("repeat") || method.is("relativeRepeat")) {
            frame = start + pyMod(frame - start, length);
        } else if (method.is("mirror")) {
            const double cycle = pyMod(frame - start, 2 * length);
            frame = start + (cycle <= length ? cycle : 2 * length - cycle);
        }
    }
    const JVal& segments = curve["segments"];
    if (segments.a.empty()) return false;
    const JVal* seg = &segments.a.back();
    for (const JVal& s : segments.a)
        if (frame <= s["endFrame"].num()) { seg = &s; break; }
    if ((*seg)["interpolation"].is("constant")) { *out = (*seg)["value"].num(); return true; }
    const JVal& keys = (*seg)["keys"];
    if (keys.a.empty()) return false;
    auto at = [](const JVal& key, size_t k) { return k < key.a.size() ? key.a[k].num() : 0.0; };
    if (frame <= at(keys.a[0], 0)) { if (keys.a[0].a.size() < 2) return false; *out = at(keys.a[0], 1); return true; }
    if (frame >= at(keys.a.back(), 0)) { if (keys.a.back().a.size() < 2) return false; *out = at(keys.a.back(), 1); return true; }
    if (keys.a.size() < 2) return false;
    const JVal* a = &keys.a[0];
    const JVal* b = &keys.a[1];
    for (size_t k = 0; k + 1 < keys.a.size(); k++) {
        a = &keys.a[k];
        b = &keys.a[k + 1];
        if (at(*a, 0) <= frame && frame < at(*b, 0)) break;
    }
    if (a->a.size() < 2 || b->a.size() < 2) return false;
    const JVal& interp = (*seg)["interpolation"];
    if (interp.is("step")) { *out = at(*a, 1); return true; }
    const double span = at(*b, 0) - at(*a, 0);
    if (span == 0) return false;
    const double t = (frame - at(*a, 0)) / span;
    if (interp.is("linear")) { *out = at(*a, 1) + mulD(at(*b, 1) - at(*a, 1), t); return true; }
    if (a->a.size() < 3 || b->a.size() < 3) return false;
    const double outSlope = a->a.size() == 4 ? at(*a, 3) : at(*a, 2);
    const double inSlope = at(*b, 2);
    // ((2t3 - 3t2 + 1) a + (t3 - 2t2 + t) span out + (-2t3 + 3t2) b + (t3 - t2) span in), each
    // product rounded on its own like CPython
    const double t2 = mulD(t, t), t3 = mulD(mulD(t, t), t);
    const double term1 = mulD(mulD(2, t3) - mulD(3, t2) + 1, at(*a, 1));
    const double term2 = mulD(mulD(t3 - mulD(2, t2) + t, span), outSlope);
    const double term3 = mulD(mulD(-2, t3) + mulD(3, t2), at(*b, 1));
    const double term4 = mulD(mulD(t3 - t2, span), inSlope);
    *out = term1 + term2 + term3 + term4;
    return true;
}

class GltfBuilder {
public:
    GltfBuilder(const Scene& sc, const std::map<std::string, std::string>& uris) : mSc(sc), mUris(uris) {
        g = jobj();
        JVal asset = jobj();
        asset.set("version", jstr("2.0"));
        asset.set("generator", jstr("3ds-web cgfx exporter"));
        g.set("asset", std::move(asset));
        g.set("scene", jint(0));
        JVal scene = jobj();
        JVal nodes = jarr();
        nodes.push(jint(0));
        scene.set("nodes", std::move(nodes));
        JVal scenes = jarr();
        scenes.push(std::move(scene));
        g.set("scenes", std::move(scenes));
        for (const char* k : {"nodes", "meshes", "materials", "textures", "images", "samplers", "accessors",
                              "bufferViews", "buffers", "animations", "skins"})
            g.set(k, jarr());
    }

    bool ok() const { return mErr.empty(); }
    const std::string& error() const { return mErr; }

    // gltf.py build: one parsed CGFX model (with its numpy data) to glTF.
    void build(const JVal& model, const JVal& animations, int fps) {
        JVal root = jobj();
        root.set("name", model["name"]);
        trs(root, model);
        const int rootIndex = node(std::move(root), -1);

        const JVal& skeleton = model["skeleton"];
        static const JVal kEmpty = jarr();
        const JVal& bones = skeleton.kind == JVal::Obj ? skeleton["bones"] : kEmpty;
        std::map<std::string, int> boneNodes;
        for (const JVal& bone : bones.a) {
            JVal n = jobj();
            n.set("name", bone["name"]);
            trs(n, bone);
            if (!bone["billboard"].is("off")) {
                JVal extras = jobj();
                extras.set("billboard", bone["billboard"]);
                n.set("extras", std::move(extras));
            }
            const int64_t parentIndex = bone["parentIndex"].integer();
            int parent = rootIndex;
            if (parentIndex >= 0) {
                if ((size_t)parentIndex >= bones.a.size()) return fail("bone parent index out of range");
                auto it = boneNodes.find(pyKey(bones.a[(size_t)parentIndex]["name"]));
                if (it == boneNodes.end()) return fail("bone parent defined after its child");
                parent = it->second;
            }
            boneNodes[pyKey(bone["name"])] = node(std::move(n), parent);
        }
        std::vector<int> boneNodeList;
        for (const JVal& b : bones.a) boneNodeList.push_back(boneNodes[pyKey(b["name"])]);

        std::vector<int> materials;
        for (const JVal& m : model["materials"].a) {
            materials.push_back(material(m));
            if (!ok()) return;
        }
        std::map<int64_t, std::pair<JVal, std::map<std::string, FloatArray>>> shapeCache;
        const JVal& meshes = model["meshes"];
        for (size_t meshIndex = 0; meshIndex < meshes.a.size(); meshIndex++) {
            const JVal& mesh = meshes.a[meshIndex];
            const int64_t shapeIndex = mesh["shapeIndex"].integer();
            if (shapeIndex < 0 || (size_t)shapeIndex >= model["shapes"].a.size()) return fail("mesh shape index out of range");
            const JVal& shape = model["shapes"].a[(size_t)shapeIndex];
            if (!shapeCache.count(shapeIndex)) {
                auto& entry = shapeCache[shapeIndex];
                shapeAccessors(shape, &entry.first, &entry.second);
                if (!ok()) return;
            }
            const JVal& attributes = shapeCache[shapeIndex].first;
            const auto& arrays = shapeCache[shapeIndex].second;
            const int64_t materialIndex = mesh["materialIndex"].integer();
            if (materialIndex < 0 || (size_t)materialIndex >= model["materials"].a.size()) return fail("mesh material index out of range");
            const JVal& mat = model["materials"].a[(size_t)materialIndex];
            const int64_t vertexCount = shape["vertexCount"].integer();
            const JVal& subs = shape["subMeshes"];
            for (size_t subIndex = 0; subIndex < subs.a.size(); subIndex++) {
                const JVal& submesh = subs.a[subIndex];
                JVal primAttributes = attributes;
                const bool skinned = !submesh["skinning"].is("none") && !bones.a.empty();
                if (skinned) {
                    skinAttributes(arrays, submesh, (size_t)vertexCount, &primAttributes);
                    if (!ok()) return;
                }
                JVal primitives = jarr();
                for (const JVal& face : submesh["faces"].a) {
                    const std::vector<uint32_t>& idx = mSc.indices[(size_t)face["indices"].i];
                    JVal prim = jobj();
                    prim.set("attributes", primAttributes);
                    int acc;
                    if (vertexCount < 65536) {
                        std::vector<uint16_t> v(idx.size());
                        for (size_t k = 0; k < idx.size(); k++) v[k] = (uint16_t)idx[k];
                        acc = accessor(v.data(), v.size() * 2, kUnsignedShort, v.size(), 1, kElementArrayBuffer, false);
                    } else {
                        acc = accessor(idx.data(), idx.size() * 4, kUnsignedInt, idx.size(), 1, kElementArrayBuffer, false);
                    }
                    if (!ok()) return;
                    prim.set("indices", jint(acc));
                    prim.set("material", jint(materials[(size_t)materialIndex]));
                    const JVal& p = face["primitive"];
                    const int mode = p.is("triangles") ? 4 : p.is("triangleStrip") ? 5 : p.is("triangleFan") ? 6 : -1;
                    if (mode < 0) return fail("unknown primitive mode");
                    prim.set("mode", jint(mode));
                    primitives.push(std::move(prim));
                }
                const std::string name = (mesh["name"].truthy() ? pyStr(mesh["name"]) : pyStr(mat["name"])) + "_" +
                                         std::to_string(meshIndex) + "_" + std::to_string(subIndex);
                JVal gm = jobj();
                gm.set("name", jstr(name));
                gm.set("primitives", std::move(primitives));
                JVal* gmeshes = g.find("meshes");
                gmeshes->push(std::move(gm));
                JVal n = jobj();
                n.set("name", jstr(name));
                n.set("mesh", jint((int64_t)gmeshes->a.size() - 1));
                JVal extras = jobj();
                extras.set("visible", mesh["isVisible"]);
                extras.set("renderPriority", mesh["renderPriority"]);
                extras.set("material", mat["name"]);
                extras.set("skinning", submesh["skinning"]);
                n.set("extras", std::move(extras));
                int parent = rootIndex;
                if (skinned) {
                    const bool rigid = submesh["skinning"].is("rigid");
                    std::vector<float> inverse;
                    for (const JVal& b : bones.a) {
                        float m[16] = {1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1};
                        if (!rigid) {
                            // _column_major: the 3x4 matrix plus [0, 0, 0, 1], transposed
                            const JVal& iw = b["inverseWorldMatrix"];
                            for (int row = 0; row < 3; row++)
                                for (int c = 0; c < 4; c++) {
                                    const JVal& rv = iw.a.size() > (size_t)row ? iw.a[row] : kEmpty;
                                    m[c * 4 + row] = rv.a.size() > (size_t)c ? (float)rv.a[c].num() : 0.0f;
                                }
                        }
                        inverse.insert(inverse.end(), m, m + 16);
                    }
                    JVal skin = jobj();
                    JVal joints = jarr();
                    for (int j : boneNodeList) joints.push(jint(j));
                    skin.set("joints", std::move(joints));
                    skin.set("inverseBindMatrices", jint(accessor(inverse.data(), inverse.size() * 4, kFloat, bones.a.size(), 16, 0, false)));
                    if (!ok()) return;
                    JVal* skins = g.find("skins");
                    skins->push(std::move(skin));
                    n.set("skin", jint((int64_t)skins->a.size() - 1));
                } else if (!bones.a.empty() && submesh["boneIndices"].truthy()) {
                    const int64_t bi = submesh["boneIndices"].a[0].integer();
                    if (bi < 0 || (size_t)bi >= boneNodeList.size()) return fail("submesh bone index out of range");
                    parent = boneNodeList[(size_t)bi];
                }
                node(std::move(n), parent);
            }
        }
        std::map<std::string, const JVal*> rest;
        for (const JVal& b : bones.a) rest[pyKey(b["name"])] = &b;
        for (const JVal& anim : animations.a) {
            animation(anim, boneNodes, rest, fps);
            if (!ok()) return;
        }
    }

    // _Builder.finish
    void finish(const std::string& binName, JVal* gltf, std::vector<uint8_t>* bin) {
        while (mBuffer.size() % 4) mBuffer.push_back(0);
        JVal buf = jobj();
        buf.set("uri", jstr(binName));
        buf.set("byteLength", jint((int64_t)mBuffer.size()));
        g.find("buffers")->push(std::move(buf));
        for (const char* key : {"textures", "images", "samplers", "animations", "skins", "materials"}) {
            auto it = std::find_if(g.o.begin(), g.o.end(), [&](const std::pair<std::string, JVal>& m) { return m.first == key; });
            if (it != g.o.end() && it->second.a.empty()) g.o.erase(it);
        }
        if (!mExtensionsUsed.empty()) {
            JVal used = jarr();
            for (const std::string& e : mExtensionsUsed) used.push(jstr(e));   // std::set: sorted
            g.set("extensionsUsed", std::move(used));
        }
        *gltf = std::move(g);
        *bin = std::move(mBuffer);
    }

private:
    const Scene& mSc;
    const std::map<std::string, std::string>& mUris;
    JVal g;
    std::vector<uint8_t> mBuffer;
    std::map<std::string, int> mImages;
    std::map<std::string, int> mSamplers;
    std::set<std::string> mExtensionsUsed;
    std::string mErr;

    void fail(const std::string& e) { if (mErr.empty()) mErr = e; }

    // _Builder.accessor. With `bounds` the data is float32 and gets min / max (an empty array
    // fails there like numpy's reduction does).
    int accessor(const void* data, size_t nbytes, int component, size_t count, int width, int target, bool bounds) {
        while (mBuffer.size() % 4) mBuffer.push_back(0);
        JVal view = jobj();
        view.set("buffer", jint(0));
        view.set("byteOffset", jint((int64_t)mBuffer.size()));
        view.set("byteLength", jint((int64_t)nbytes));
        if (target) view.set("target", jint(target));
        const uint8_t* p = (const uint8_t*)data;
        mBuffer.insert(mBuffer.end(), p, p + nbytes);
        JVal* views = g.find("bufferViews");
        views->push(std::move(view));
        const char* type = width == 16 ? "MAT4" : width == 1 ? "SCALAR" : width == 2 ? "VEC2" : width == 3 ? "VEC3" : width == 4 ? "VEC4" : nullptr;
        if (!type) { fail(format("no glTF type for %d components", width)); return 0; }
        JVal acc = jobj();
        acc.set("bufferView", jint((int64_t)views->a.size() - 1));
        acc.set("componentType", jint(component));
        acc.set("count", jint((int64_t)count));
        acc.set("type", jstr(type));
        if (bounds) {
            if (!count) { fail("bounds of an empty accessor"); return 0; }
            const float* f = (const float*)data;
            JVal mn = jarr(), mx = jarr();
            for (int c = 0; c < width; c++) {
                float lo = f[c], hi = f[c];
                bool nan = isNanF(lo);
                for (size_t row = 1; row < count; row++) {
                    const float x = f[row * width + c];
                    if (isNanF(x)) nan = true;
                    if (x < lo) lo = x;
                    if (x > hi) hi = x;
                }
                mn.push(jnum(nan ? NAN : (double)lo));
                mx.push(jnum(nan ? NAN : (double)hi));
            }
            acc.set("min", std::move(mn));
            acc.set("max", std::move(mx));
        }
        JVal* accs = g.find("accessors");
        accs->push(std::move(acc));
        return (int)accs->a.size() - 1;
    }
    int accessorF(const FloatArray& a, int target, bool bounds) {
        return accessor(a.v.data(), a.v.size() * 4, kFloat, a.rows, (int)a.cols, target, bounds);
    }

    int node(JVal n, int parent) {
        JVal* nodes = g.find("nodes");
        nodes->push(std::move(n));
        const int index = (int)nodes->a.size() - 1;
        if (parent >= 0) {
            JVal& p = nodes->a[(size_t)parent];
            JVal* children = p.find("children");
            if (!children) children = &p.set("children", jarr());
            children->push(jint(index));
        }
        return index;
    }

    static void trs(JVal& n, const JVal& source) {
        if (!listEquals(source["translation"], {0, 0, 0})) n.set("translation", source["translation"]);
        if (!listEquals(source["rotation"], {0, 0, 0})) {
            const JVal& r = source["rotation"];
            auto q = eulerToQuaternion(r.a.size() > 0 ? r.a[0].num() : 0, r.a.size() > 1 ? r.a[1].num() : 0, r.a.size() > 2 ? r.a[2].num() : 0);
            JVal v = jarr();
            for (double x : q) v.push(jnum(x));
            n.set("rotation", std::move(v));
        }
        if (!listEquals(source["scale"], {1, 1, 1})) n.set("scale", source["scale"]);
    }

    static int wrapCode(const JVal& v) {
        if (v.is("clampToEdge") || v.is("clampToBorder")) return 33071;
        if (v.is("repeat")) return 10497;
        if (v.is("mirroredRepeat")) return 33648;
        return -1;
    }
    static int filterCode(const JVal& v) { return v.is("nearest") ? 9728 : v.is("linear") ? 9729 : -1; }

    int texture(const std::string& uri, const JVal& mapper) {
        if (!mImages.count(uri)) {
            JVal img = jobj();
            img.set("uri", jstr(uri));
            img.set("name", jstr(pyStem(uri)));
            JVal* images = g.find("images");
            images->push(std::move(img));
            mImages[uri] = (int)images->a.size() - 1;
        }
        const std::string key = pyKey(mapper["wrapS"]) + "|" + pyKey(mapper["wrapT"]) + "|" + pyKey(mapper["magFilter"]) + "|" + pyKey(mapper["minFilter"]);
        if (!mSamplers.count(key)) {
            const int ws = wrapCode(mapper["wrapS"]), wt = wrapCode(mapper["wrapT"]);
            const int mag = filterCode(mapper["magFilter"]), min = filterCode(mapper["minFilter"]);
            if (ws < 0 || wt < 0 || mag < 0 || min < 0) { fail("texture mapper has an unknown wrap or filter mode"); return 0; }
            JVal s = jobj();
            s.set("wrapS", jint(ws));
            s.set("wrapT", jint(wt));
            s.set("magFilter", jint(mag));
            s.set("minFilter", jint(min));
            JVal* samplers = g.find("samplers");
            samplers->push(std::move(s));
            mSamplers[key] = (int)samplers->a.size() - 1;
        }
        JVal t = jobj();
        t.set("source", jint(mImages[uri]));
        t.set("sampler", jint(mSamplers[key]));
        JVal* textures = g.find("textures");
        textures->push(std::move(t));
        return (int)textures->a.size() - 1;
    }

    // _uv_transform: a CGFX texture matrix (GL texture space, v up) as KHR_texture_transform.
    static bool uvTransform(const JVal& matrix, JVal* out) {
        auto at = [&](size_t row, size_t c) {
            return matrix.a.size() > row && matrix.a[row].a.size() > c ? matrix.a[row].a[c].num() : 0.0;
        };
        const double a = at(0, 0), b = at(0, 1), c = at(0, 3), d = at(1, 0), e = at(1, 1), f = at(1, 3);
        const double t[2][3] = {{a, -b, b + c}, {-d, e, 1 - e - f}};
        const double ref[2][3] = {{1, 0, 0}, {0, 1, 0}};
        bool close = true;
        for (int row = 0; row < 2; row++)
            for (int k = 0; k < 3; k++) {
                const double diff = fabs(t[row][k] - ref[row][k]);
                if (!(diff <= 1e-6 + 1e-5 * fabs(ref[row][k]))) close = false;
            }
        if (close) return false;
        const double rotation = atan2(-t[1][0], t[0][0]);
        const double scaleX = hypot(t[0][0], t[1][0]);
        const double scaleY = mulD(t[1][1], cos(rotation)) + mulD(t[0][1], sin(rotation));
        JVal o = jobj();
        JVal offset = jarr();
        offset.push(jnum(t[0][2]));
        offset.push(jnum(t[1][2]));
        JVal scale = jarr();
        scale.push(jnum(scaleX));
        scale.push(jnum(scaleY));
        o.set("offset", std::move(offset));
        o.set("scale", std::move(scale));
        if (fabs(rotation) > 1e-6) o.set("rotation", jnum(rotation));
        *out = std::move(o);
        return true;
    }

    static bool contains(const JVal& list, const char* s) {
        for (const JVal& v : list.a) if (v.is(s)) return true;
        return false;
    }

    // _material
    int material(const JVal& m) {
        JVal out = jobj();
        out.set("name", m["name"]);
        JVal extras = jobj();
        extras.set("cgfxMaterial", m["name"]);
        out.set("extras", std::move(extras));
        const bool lit = contains(m["flags"], "fragmentLighting") || contains(m["flags"], "vertexLighting");
        JVal pbr = jobj();
        pbr.set("metallicFactor", jnum(0.0));
        pbr.set("roughnessFactor", jnum(1.0));
        if (lit) {
            JVal f = jarr();
            for (const JVal& c : m["colors"]["diffuse"].a) {
                // min(max(c, 0.0), 1.0) with Python's argument order semantics
                double v = c.num();
                if (0.0 > v) v = 0.0;
                if (1.0 < v) v = 1.0;
                f.push(jnum(v));
            }
            pbr.set("baseColorFactor", std::move(f));
        }
        JVal extensions = jobj();
        const JVal& mappers = m["textureMappers"];
        const JVal& mapper = mappers.a.empty() ? mappers : mappers.a[0];
        const JVal* coordinator = m["textureCoordinators"].a.empty() ? nullptr : &m["textureCoordinators"].a[0];
        if (mapper.kind == JVal::Obj && mapper.truthy()) {
            auto it = mUris.find(pyKey(mapper["texture"]));
            if (it != mUris.end()) {
                JVal info = jobj();
                info.set("index", jint(texture(it->second, mapper)));
                if (!ok()) return 0;
                if (coordinator && coordinator->truthy()) {
                    info.set("texCoord", (*coordinator)["sourceIndex"]);
                    JVal transform;
                    if ((*coordinator)["mapping"].is("uv") && uvTransform((*coordinator)["matrix"], &transform)) {
                        JVal ext = jobj();
                        ext.set("KHR_texture_transform", std::move(transform));
                        info.set("extensions", std::move(ext));
                        mExtensionsUsed.insert("KHR_texture_transform");
                    }
                }
                pbr.set("baseColorTexture", std::move(info));
            }
        }
        out.set("pbrMetallicRoughness", std::move(pbr));
        if (!lit) {
            extensions.set("KHR_materials_unlit", jobj());
            mExtensionsUsed.insert("KHR_materials_unlit");
        }
        if (!extensions.o.empty()) out.set("extensions", std::move(extensions));
        const JVal& blend = m["fragmentOperation"]["blend"];
        const JVal& alphaTest = m["fragmentShader"]["alphaTest"];
        if (!m["translucency"].is("opaque") || blend["enabled"].truthy()) {
            out.set("alphaMode", jstr("BLEND"));
        } else if (alphaTest["enabled"].truthy() && (alphaTest["function"].is("greater") || alphaTest["function"].is("greaterEqual"))) {
            out.set("alphaMode", jstr("MASK"));
            out.set("alphaCutoff", jnum(alphaTest["reference"].num() / 255));
        }
        if (m["rasterization"]["cullMode"].is("none")) out.set("doubleSided", jbool(true));
        JVal* materials = g.find("materials");
        materials->push(std::move(out));
        return (int)materials->a.size() - 1;
    }

    // _attribute_arrays + _shape_accessors
    void shapeAccessors(const JVal& shape, JVal* out, std::map<std::string, FloatArray>* arrays) {
        const size_t count = (size_t)shape["vertexCount"].integer();
        for (const JVal& attr : shape["attributes"].a) {
            FloatArray arr;
            if (attr["kind"].is("constant")) {
                const JVal& value = attr["value"];
                arr.rows = (uint32_t)count;
                arr.cols = (uint32_t)value.a.size();
                arr.v.resize(count * arr.cols);
                for (size_t row = 0; row < count; row++)
                    for (uint32_t c = 0; c < arr.cols; c++) arr.v[row * arr.cols + c] = (float)value.a[c].num();
            } else {
                arr = mSc.values[(size_t)attr["values"].i];
            }
            (*arrays)[pyKey(attr["name"])] = std::move(arr);
        }
        *out = jobj();
        auto it = arrays->find("s:position");
        if (it == arrays->end()) return fail("shape has no position attribute");
        {
            const FloatArray& p = it->second;
            const JVal& off = shape["positionOffset"];
            float o3[3];
            for (int c = 0; c < 3; c++) o3[c] = off.a.size() > (size_t)c ? (float)off.a[c].num() : 0.0f;
            if (p.cols != 1 && p.cols < 3) return fail("position has fewer than three components");
            FloatArray pos;
            pos.rows = p.rows;
            pos.cols = 3;
            pos.v.resize((size_t)p.rows * 3);
            for (uint32_t row = 0; row < p.rows; row++)
                for (int c = 0; c < 3; c++) pos.v[(size_t)row * 3 + c] = p.v[(size_t)row * p.cols + (p.cols == 1 ? 0 : c)] + o3[c];
            out->set("POSITION", jint(accessorF(pos, kArrayBuffer, true)));
        }
        it = arrays->find("s:normal");
        if (it != arrays->end()) {
            const FloatArray& nm = it->second;
            if (nm.cols < 3) return fail("normal has fewer than three components");
            FloatArray n;
            n.rows = nm.rows;
            n.cols = 3;
            n.v.resize((size_t)nm.rows * 3);
            const float eps = (float)1e-8;
            for (uint32_t row = 0; row < nm.rows; row++) {
                const float* s = &nm.v[(size_t)row * nm.cols];
                // np.linalg.norm(axis=1) in float32: squares summed in order, then sqrt
                float sq = mulF(s[0], s[0]);
                sq = sq + mulF(s[1], s[1]);
                sq = sq + mulF(s[2], s[2]);
                const float len = sqrtExact(sq);
                const float div = len > eps ? len : eps;
                for (int c = 0; c < 3; c++) n.v[(size_t)row * 3 + c] = len > eps ? s[c] / div : (c == 2 ? 1.0f : 0.0f);
            }
            out->set("NORMAL", jint(accessorF(n, kArrayBuffer, false)));
        }
        it = arrays->find("s:color");
        if (it != arrays->end()) {
            const FloatArray& cl = it->second;
            FloatArray c;
            c.rows = cl.rows;
            c.cols = cl.cols == 3 ? 4 : cl.cols;
            c.v.resize((size_t)c.rows * c.cols);
            for (uint32_t row = 0; row < cl.rows; row++)
                for (uint32_t k = 0; k < c.cols; k++) {
                    float v = k < cl.cols ? cl.v[(size_t)row * cl.cols + k] : 1.0f;
                    // np.clip(color, 0, 1): NaN stays NaN
                    if (v < 0.0f) v = 0.0f;
                    if (v > 1.0f) v = 1.0f;
                    c.v[(size_t)row * c.cols + k] = v;
                }
            out->set("COLOR_0", jint(accessorF(c, kArrayBuffer, false)));
        }
        for (int k = 0; k < 3; k++) {
            it = arrays->find("s:texCoord" + std::to_string(k));
            if (it == arrays->end()) continue;
            const FloatArray& uv = it->second;
            if (uv.cols < 2) return fail("texture coordinate has fewer than two components");
            FloatArray f;
            f.rows = uv.rows;
            f.cols = 2;
            f.v.resize((size_t)uv.rows * 2);
            for (uint32_t row = 0; row < uv.rows; row++) {
                f.v[(size_t)row * 2] = uv.v[(size_t)row * uv.cols];
                f.v[(size_t)row * 2 + 1] = 1.0f - uv.v[(size_t)row * uv.cols + 1];
            }
            out->set("TEXCOORD_" + std::to_string(k), jint(accessorF(f, kArrayBuffer, false)));
        }
    }

    // _skin_attributes
    void skinAttributes(const std::map<std::string, FloatArray>& arrays, const JVal& submesh, size_t count, JVal* attributes) {
        std::vector<uint16_t> palette;
        for (const JVal& v : submesh["boneIndices"].a) palette.push_back((uint16_t)v.integer());
        auto it = arrays.find("s:boneIndex");
        FloatArray indices;
        if (it == arrays.end()) {
            indices.rows = (uint32_t)count;
            indices.cols = 1;
            indices.v.assign(count, 0.0f);
        } else {
            indices = it->second;
        }
        if (indices.cols > 4) return fail("more than four bone indices per vertex");
        auto wit = arrays.find("s:boneWeight");
        FloatArray weights;
        if (wit == arrays.end() || submesh["skinning"].is("rigid")) {
            weights.rows = indices.rows;
            weights.cols = indices.cols;
            weights.v.assign((size_t)indices.rows * indices.cols, 0.0f);
            for (uint32_t row = 0; row < weights.rows && weights.cols; row++) weights.v[(size_t)row * weights.cols] = 1.0f;
        } else {
            weights = wit->second;
        }
        if (weights.cols > 4) return fail("more than four bone weights per vertex");
        if (indices.rows != count || weights.rows != count) return fail("skinning attributes do not match the vertex count");
        if (palette.empty() && indices.cols) return fail("skinned submesh has no bone palette");
        std::vector<uint16_t> joints(count * 4, 0);
        for (size_t row = 0; row < count; row++)
            for (uint32_t c = 0; c < indices.cols; c++) {
                int64_t v = (int64_t)indices.v[row * indices.cols + c];   // astype(np.int64)
                v = std::clamp<int64_t>(v, 0, (int64_t)palette.size() - 1);
                joints[row * 4 + c] = palette[(size_t)v];
            }
        std::vector<float> padded(count * 4, 0.0f);
        const float eps = (float)1e-8;
        for (size_t row = 0; row < count; row++) {
            float* p = &padded[row * 4];
            for (uint32_t c = 0; c < weights.cols; c++) p[c] = weights.v[row * weights.cols + c];
            float total = p[0];
            total = total + p[1];
            total = total + p[2];
            total = total + p[3];
            if (total > 0) {
                const float div = total > eps ? total : eps;
                for (int c = 0; c < 4; c++) p[c] = p[c] / div;
            } else {
                p[0] = 1; p[1] = 0; p[2] = 0; p[3] = 0;
            }
        }
        attributes->set("JOINTS_0", jint(accessor(joints.data(), joints.size() * 2, kUnsignedShort, count, 4, kArrayBuffer, false)));
        attributes->set("WEIGHTS_0", jint(accessor(padded.data(), padded.size() * 4, kFloat, count, 4, kArrayBuffer, false)));
    }

    // _animation: skeletal curves sampled once per frame.
    void animation(const JVal& anim, const std::map<std::string, int>& boneNodes,
                   const std::map<std::string, const JVal*>& rest, int fps) {
        int64_t frames = 0;
        if (!pyRoundInt(anim["frameCount"].num(), &frames)) return fail("animation frame count is not finite");
        frames = std::max<int64_t>(frames, 0);
        if (frames > 1000000) return fail("animation is too long");
        std::vector<float> times((size_t)frames + 1);
        for (size_t k = 0; k < times.size(); k++) times[k] = (float)k / (float)fps;
        int timeAccessor = -1;
        JVal channels = jarr(), samplers = jarr();
        static const char* const kPaths[3] = {"translation", "rotation", "scale"};
        static const char* const kNames[3][3] = {{"translationX", "translationY", "translationZ"},
                                                {"rotationX", "rotationY", "rotationZ"},
                                                {"scaleX", "scaleY", "scaleZ"}};
        for (const JVal& element : anim["elements"].a) {
            auto bn = boneNodes.find(pyKey(element["name"]));
            const JVal* curves = element.find("curves");
            if (bn == boneNodes.end() || !element["type"].is("transform") || !curves || !curves->truthy()) continue;
            const JVal& bone = *rest.at(pyKey(element["name"]));
            for (int path = 0; path < 3; path++) {
                bool any = false;
                for (int axis = 0; axis < 3; axis++) if (curves->find(kNames[path][axis])) any = true;
                if (!any) continue;
                std::vector<double> values(times.size() * 3);
                for (int axis = 0; axis < 3; axis++) {
                    const JVal* curve = curves->find(kNames[path][axis]);
                    const JVal& restv = bone[kPaths[path]];
                    for (size_t k = 0; k < times.size(); k++) {
                        double v;
                        if (!curve) {
                            v = restv.a.size() > (size_t)axis ? restv.a[(size_t)axis].num() : 0.0;
                        } else if (!evaluateCurve(*curve, (double)k, &v)) {
                            return fail("animation curve cannot be evaluated");
                        }
                        values[k * 3 + axis] = v;
                    }
                }
                std::vector<float> out;
                int width = 3;
                if (path == 1) {
                    width = 4;
                    std::vector<std::array<double, 4>> quats;
                    for (size_t k = 0; k < times.size(); k++)
                        quats.push_back(eulerToQuaternion(values[k * 3], values[k * 3 + 1], values[k * 3 + 2]));
                    for (size_t k = 1; k < quats.size(); k++) {
                        const double dot = quats[k][0] * quats[k - 1][0] + quats[k][1] * quats[k - 1][1] +
                                           quats[k][2] * quats[k - 1][2] + quats[k][3] * quats[k - 1][3];
                        if (dot < 0)
                            for (double& q : quats[k]) q = -q;
                    }
                    for (const auto& q : quats) for (double x : q) out.push_back((float)x);
                } else {
                    for (double v : values) out.push_back((float)v);
                }
                if (timeAccessor < 0) {
                    timeAccessor = accessor(times.data(), times.size() * 4, kFloat, times.size(), 1, 0, true);
                    if (!ok()) return;
                }
                JVal s = jobj();
                s.set("input", jint(timeAccessor));
                s.set("output", jint(accessor(out.data(), out.size() * 4, kFloat, times.size(), width, 0, false)));
                s.set("interpolation", jstr("LINEAR"));
                samplers.push(std::move(s));
                JVal c = jobj();
                c.set("sampler", jint((int64_t)samplers.a.size() - 1));
                JVal target = jobj();
                target.set("node", jint(bn->second));
                target.set("path", jstr(kPaths[path]));
                c.set("target", std::move(target));
                channels.push(std::move(c));
            }
        }
        if (!channels.a.empty()) {
            JVal a = jobj();
            a.set("name", anim["name"]);
            a.set("channels", std::move(channels));
            a.set("samplers", std::move(samplers));
            JVal extras = jobj();
            extras.set("loopMode", anim["loopMode"]);
            extras.set("frameCount", anim["frameCount"]);
            extras.set("fps", jint(fps));
            a.set("extras", std::move(extras));
            g.find("animations")->push(std::move(a));
        }
    }
};

// ================================================================== PNG

void pngChunk(std::vector<uint8_t>& out, const char* type, const uint8_t* data, size_t n) {
    putBe32(out, (uint32_t)n);
    const size_t start = out.size();
    out.insert(out.end(), type, type + 4);
    if (n) out.insert(out.end(), data, data + n);
    uLong crc = crc32(0L, Z_NULL, 0);
    crc = crc32(crc, out.data() + start, (uInt)(n + 4));
    putBe32(out, (uint32_t)crc);
}

inline uint8_t paeth(int a, int b, int c) {
    const int p = a + b - c, pa = abs(p - a), pb = abs(p - b), pc = abs(p - c);
    return (uint8_t)(pa <= pb && pa <= pc ? a : pb <= pc ? b : c);
}

}  // namespace

std::vector<uint8_t> encodePng(const uint8_t* rgba, int width, int height) {
    // Each row takes the filter with the smallest sum of absolute differences (the usual
    // libpng heuristic), then the stream is deflated.
    const size_t stride = (size_t)width * 4;
    std::vector<uint8_t> raw;
    raw.reserve((stride + 1) * height);
    std::vector<uint8_t> cand[5];
    for (auto& c : cand) c.resize(stride);
    std::vector<uint8_t> zeroRow(stride, 0);
    for (int y = 0; y < height; y++) {
        const uint8_t* row = rgba + (size_t)y * stride;
        const uint8_t* up = y ? rgba + (size_t)(y - 1) * stride : zeroRow.data();
        uint64_t best = UINT64_MAX;
        int bestF = 0;
        for (int f = 0; f < 5; f++) {
            uint64_t sum = 0;
            for (size_t x = 0; x < stride; x++) {
                const int a = x >= 4 ? row[x - 4] : 0, b = up[x], c = x >= 4 ? up[x - 4] : 0;
                uint8_t v = row[x];
                switch (f) {
                    case 1: v = (uint8_t)(v - a); break;
                    case 2: v = (uint8_t)(v - b); break;
                    case 3: v = (uint8_t)(v - ((a + b) >> 1)); break;
                    case 4: v = (uint8_t)(v - paeth(a, b, c)); break;
                    default: break;
                }
                cand[f][x] = v;
                sum += v < 128 ? v : 256 - v;
            }
            if (sum < best) { best = sum; bestF = f; }
        }
        raw.push_back((uint8_t)bestF);
        raw.insert(raw.end(), cand[bestF].begin(), cand[bestF].end());
    }
    uLongf zlen = compressBound((uLong)raw.size());
    std::vector<uint8_t> z(zlen);
    if (compress2(z.data(), &zlen, raw.data(), (uLong)raw.size(), Z_DEFAULT_COMPRESSION) != Z_OK) return {};
    z.resize(zlen);
    std::vector<uint8_t> out = {0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'};
    std::vector<uint8_t> ihdr;
    putBe32(ihdr, (uint32_t)width);
    putBe32(ihdr, (uint32_t)height);
    ihdr.insert(ihdr.end(), {8, 6, 0, 0, 0});
    pngChunk(out, "IHDR", ihdr.data(), ihdr.size());
    pngChunk(out, "IDAT", z.data(), z.size());
    pngChunk(out, "IEND", nullptr, 0);
    return out;
}

// ================================================================== CPK1 (ctr_pack.py write_pack)

std::vector<uint8_t> writeCpk(const std::vector<std::pair<std::string, std::vector<uint8_t>>>& input) {
    // sorted(entries): a dict, so a repeated name keeps its last data
    std::map<std::string, const std::vector<uint8_t>*> entries;
    for (const auto& e : input) entries[e.first] = &e.second;
    const size_t headSize = 8 + 16 * entries.size();
    std::vector<uint8_t> nameBlob;
    std::vector<uint32_t> nameOffs;
    for (const auto& e : entries) {
        nameOffs.push_back((uint32_t)(headSize + nameBlob.size()));
        nameBlob.insert(nameBlob.end(), e.first.begin(), e.first.end());
    }
    const size_t dataStart = (headSize + nameBlob.size() + 3) & ~(size_t)3;
    std::vector<uint8_t> data;
    std::vector<uint32_t> dataOffs;
    for (const auto& e : entries) {
        while (data.size() % 4) data.push_back(0);
        dataOffs.push_back((uint32_t)(dataStart + data.size()));
        data.insert(data.end(), e.second->begin(), e.second->end());
    }
    std::vector<uint8_t> out = {'C', 'P', 'K', '1'};
    put32(out, (uint32_t)entries.size());
    size_t k = 0;
    for (const auto& e : entries) {
        put32(out, nameOffs[k]);
        put32(out, (uint32_t)e.first.size());
        put32(out, dataOffs[k]);
        put32(out, (uint32_t)e.second->size());
        k++;
    }
    out.insert(out.end(), nameBlob.begin(), nameBlob.end());
    out.resize(dataStart, 0);
    out.insert(out.end(), data.begin(), data.end());
    return out;
}

// ================================================================== LZ (lz.py decompress)

bool decodePicaTexture(const uint8_t* raw, size_t size, uint32_t w, uint32_t h, const char* format,
                       std::vector<uint8_t>* rgba, std::string* error) {
    int fmt = -1;
    for (int k = 0; k < 14; k++) if (!strcmp(format, kTextureFormats[k])) fmt = k;
    Image img;
    std::string err;
    if (!decodeTexture(raw, size, w, h, fmt, &img, &err)) {
        if (error) *error = err;
        return false;
    }
    rgba->swap(img.rgba);
    return true;
}

bool lzDecompress(const uint8_t* data, size_t n, std::vector<uint8_t>* out) {
    if (n < 4) return false;
    const uint8_t kind = data[0];
    if (kind != 0x10 && kind != 0x11) return false;
    size_t size = le32(data) >> 8;
    size_t pos = 4;
    if (size == 0) {
        if (n < 8) return false;
        size = le32(data + 4);
        pos = 8;
    }
    if (size > (256u << 20)) return false;
    out->clear();
    out->reserve(size);
    while (out->size() < size) {
        if (pos >= n) return false;
        const uint8_t flags = data[pos++];
        for (int bit = 0; bit < 8; bit++) {
            if (out->size() >= size) break;
            if (!(flags & (0x80 >> bit))) {
                if (pos >= n) return false;
                out->push_back(data[pos++]);
                continue;
            }
            size_t length, disp;
            if (kind == 0x10) {
                if (pos + 2 > n) return false;
                const uint8_t b0 = data[pos], b1 = data[pos + 1];
                pos += 2;
                length = (size_t)(b0 >> 4) + 3;
                disp = ((size_t)(b0 & 0xF) << 8 | b1) + 1;
            } else {
                if (pos >= n) return false;
                const uint8_t b0 = data[pos];
                const int ind = b0 >> 4;
                if (ind == 0) {
                    if (pos + 3 > n) return false;
                    length = ((size_t)(b0 & 0xF) << 4 | data[pos + 1] >> 4) + 0x11;
                    disp = ((size_t)(data[pos + 1] & 0xF) << 8 | data[pos + 2]) + 1;
                    pos += 3;
                } else if (ind == 1) {
                    if (pos + 4 > n) return false;
                    length = ((size_t)(b0 & 0xF) << 12 | (size_t)data[pos + 1] << 4 | data[pos + 2] >> 4) + 0x111;
                    disp = ((size_t)(data[pos + 2] & 0xF) << 8 | data[pos + 3]) + 1;
                    pos += 4;
                } else {
                    if (pos + 2 > n) return false;
                    length = (size_t)ind + 1;
                    disp = ((size_t)(b0 & 0xF) << 8 | data[pos + 1]) + 1;
                    pos += 2;
                }
            }
            if (disp > out->size()) return false;
            // the Python loop copies the whole run even past `size`
            const size_t start = out->size() - disp;
            for (size_t k = 0; k < length; k++) out->push_back((*out)[start + k]);
        }
    }
    return true;
}

// ================================================================== WAV (audio.py wav_bytes)

std::vector<uint8_t> wavBytes(const RomSound& s) {
    std::vector<uint8_t> out;
    const uint32_t dataLen = (uint32_t)(s.pcm.size() * 2);
    out.insert(out.end(), {'R', 'I', 'F', 'F'});
    put32(out, 36 + dataLen);
    out.insert(out.end(), {'W', 'A', 'V', 'E', 'f', 'm', 't', ' '});
    put32(out, 16);
    put16(out, 1);
    put16(out, (uint16_t)s.channels);
    put32(out, (uint32_t)s.sampleRate);
    put32(out, (uint32_t)(s.channels * s.sampleRate * 2));
    put16(out, (uint16_t)(s.channels * 2));
    put16(out, 16);
    out.insert(out.end(), {'d', 'a', 't', 'a'});
    put32(out, dataLen);
    for (int16_t v : s.pcm) put16(out, (uint16_t)v);
    if (s.loop) {
        int64_t period = 0;
        pyRoundInt(1e9 / s.sampleRate, &period);
        std::vector<uint8_t> smpl;
        for (uint32_t v : {0u, 0u, (uint32_t)period, 60u, 0u, 0u, 0u, 1u, 0u}) put32(smpl, v);
        const uint32_t end = (uint32_t)std::max<int64_t>(s.loopStart, (int64_t)s.loopEnd - 1);
        for (uint32_t v : {0u, 0u, s.loopStart, end, 0u, 0u}) put32(smpl, v);
        out.insert(out.end(), {'s', 'm', 'p', 'l'});
        put32(out, (uint32_t)smpl.size());
        out.insert(out.end(), smpl.begin(), smpl.end());
        set32(out, 4, (uint32_t)(out.size() - 8));
    }
    return out;
}

namespace {

// ================================================================== CWAV (audio.py)

const int kImaSteps[89] = {
    7, 8, 9, 10, 11, 12, 13, 14, 16, 17, 19, 21, 23, 25, 28, 31, 34, 37, 41, 45,
    50, 55, 60, 66, 73, 80, 88, 97, 107, 118, 130, 143, 157, 173, 190, 209, 230,
    253, 279, 307, 337, 371, 408, 449, 494, 544, 598, 658, 724, 796, 876, 963,
    1060, 1166, 1282, 1411, 1552, 1707, 1878, 2066, 2272, 2499, 2749, 3024, 3327,
    3660, 4026, 4428, 4871, 5358, 5894, 6484, 7132, 7845, 8630, 9493, 10442,
    11487, 12635, 13899, 15289, 16818, 18500, 20350, 22385, 24623, 27086, 29794,
    32767,
};
const int kImaIndex[8] = {-1, -1, -1, -1, 2, 4, 6, 8};

// decode_cwav (parse_cwav + _decode_dsp / _decode_ima)
bool decodeCwav(const uint8_t* d, size_t n, RomSound* out, std::string* err) {
    Rd r(d, n);
    if (!r.magicIs(0, "CWAV")) { *err = "expected CWAV"; return false; }
    if (r.u8(4) != 0xFF || r.u8(5) != 0xFE) { *err = "big-endian sound files are not supported"; return false; }
    const uint16_t count = r.u16(0x10);
    std::map<uint16_t, int64_t> secs;
    for (uint16_t k = 0; k < count && r.ok(); k++) secs[r.u16(0x14 + k * 12)] = r.i32(0x14 + k * 12 + 4);
    if (!r.ok()) { *err = r.error(); return false; }
    if (!secs.count(0x7000) || !secs.count(0x7001)) { *err = "CWAV is missing its INFO or DATA block"; return false; }
    const int64_t info = secs[0x7000] + 8, body = secs[0x7001] + 8;
    const uint8_t enc = r.u8(info), loop = r.u8(info + 1);
    const uint32_t rate = r.u32(info + 4), loopStart = r.u32(info + 8), frames = r.u32(info + 12);
    const int64_t table = info + 0x14;
    const uint32_t channels = r.u32(table);
    if (!r.ok()) { *err = r.error(); return false; }
    static const char* const kEnc[4] = {"pcm8", "pcm16", "dspAdpcm", "imaAdpcm"};
    if (enc > 3) { *err = format("unsupported encoding %d", enc); return false; }
    if (!channels || channels > 16) { *err = format("unsupported channel count %u", channels); return false; }
    if (!rate) { *err = "bad frame rate"; return false; }
    if (frames > (64u << 20)) { *err = "sound is too long"; return false; }
    out->encoding = kEnc[enc];
    out->sampleRate = (int)rate;
    out->channels = (int)channels;
    out->frames = frames;
    out->loop = loop != 0;
    out->loopStart = loopStart;
    out->loopEnd = frames;
    out->pcm.assign((size_t)frames * channels, 0);
    for (uint32_t c = 0; c < channels; c++) {
        const int64_t ch = table + r.i32(table + 8 + c * 8);
        const int64_t sampleOff = body + r.i32(ch + 4);
        const uint16_t ctxKind = r.u16(ch + 8);
        const int64_t ctxOff = r.i32(ch + 12);
        if (!r.ok()) { *err = r.error(); return false; }
        int16_t* o = out->pcm.data() + c;
        if (enc == 0) {
            if (!r.has(sampleOff, frames)) { *err = r.error(); return false; }
            for (uint32_t k = 0; k < frames; k++) o[(size_t)k * channels] = (int16_t)((int8_t)d[sampleOff + k] * 256);
        } else if (enc == 1) {
            if (!r.has(sampleOff, (int64_t)frames * 2)) { *err = r.error(); return false; }
            for (uint32_t k = 0; k < frames; k++) o[(size_t)k * channels] = (int16_t)le16(d + sampleOff + k * 2);
        } else if (enc == 2) {
            if (ctxKind != 0x0300) { *err = "DSP-ADPCM channel has no coefficients"; return false; }
            const int64_t cx = ch + ctxOff;
            int coefs[16];
            for (int k = 0; k < 16; k++) coefs[k] = r.i16(cx + k * 2);
            int h1 = r.i16(cx + 0x22), h2 = r.i16(cx + 0x24);
            if (!r.ok()) { *err = r.error(); return false; }
            int64_t off = sampleOff;
            uint32_t k = 0;
            while (k < frames) {
                if (!r.has(off, 8)) { *err = r.error(); return false; }
                const uint8_t header = d[off];
                const int scale = 1 << (header & 0xF);
                const int pair = header >> 4;
                if (pair > 7) { *err = "DSP-ADPCM predictor index out of range"; return false; }
                const int c1 = coefs[pair * 2], c2 = coefs[pair * 2 + 1];
                for (int b = 1; b < 8; b++) {
                    const uint8_t byte = d[off + b];
                    for (int half = 0; half < 2; half++) {
                        if (k >= frames) break;
                        int nib = half ? byte & 0xF : byte >> 4;
                        if (nib >= 8) nib -= 16;
                        // ((nib * scale << 11) + 1024 + c1 * h1 + c2 * h2) >> 11, floor division
                        const int64_t acc = ((int64_t)nib * scale * 2048) + 1024 + (int64_t)c1 * h1 + (int64_t)c2 * h2;
                        int64_t s = acc >= 0 ? acc / 2048 : -((-acc + 2047) / 2048);
                        s = std::clamp<int64_t>(s, -32768, 32767);
                        o[(size_t)k * channels] = (int16_t)s;
                        h2 = h1;
                        h1 = (int)s;
                        k++;
                    }
                }
                off += 8;
            }
        } else {
            if (ctxKind != 0x0301) { *err = "IMA-ADPCM channel has no context"; return false; }
            const int64_t cx = ch + ctxOff;
            int pred = r.i16(cx);
            int index = r.u8(cx + 2);
            if (!r.ok()) { *err = r.error(); return false; }
            if (frames && !r.has(sampleOff, ((int64_t)frames - 1) / 2 + 1)) { *err = r.error(); return false; }
            for (uint32_t k = 0; k < frames; k++) {
                const uint8_t byte = d[sampleOff + (k >> 1)];
                const int nib = (k & 1) ? byte >> 4 : byte & 0xF;
                if (index < 0 || index > 88) { *err = "IMA-ADPCM step index out of range"; return false; }
                const int step = kImaSteps[index];
                int diff = step >> 3;
                if (nib & 1) diff += step >> 2;
                if (nib & 2) diff += step >> 1;
                if (nib & 4) diff += step;
                pred = (nib & 8) ? pred - diff : pred + diff;
                pred = std::clamp(pred, -32768, 32767);
                index = std::clamp(index + kImaIndex[nib & 7], 0, 88);
                o[(size_t)k * channels] = (int16_t)pred;
            }
        }
    }
    return true;
}

// ================================================================== SMDH (smdh.py)

constexpr size_t kSmdhSize = 0x36C0;
constexpr size_t kIconOffset = 0x2040;

// ================================================================== CSTM (web audio/cstm.ts)

// A BCSTM stream (a theme's BGM): CSTM header, INFO, DATA; the samples are interleaved per block
// (every channel's part of a block in turn). DSP-ADPCM, PCM16 and PCM8 decode to 16 bit PCM.
bool decodeCstm(const uint8_t* d, size_t n, RomSound* out, std::string* err) {
    Rd r(d, n);
    if (!r.magicIs(0, "CSTM")) { *err = "expected CSTM"; return false; }
    if (r.u8(4) != 0xFF || r.u8(5) != 0xFE) { *err = "big-endian streams are not supported"; return false; }
    const uint16_t count = r.u16(0x10);
    int64_t infoAt = -1, dataAt = -1;
    for (uint16_t k = 0; k < count && r.ok(); k++) {
        const uint16_t type = r.u16(0x14 + k * 12);
        if (type == 0x4000) infoAt = r.i32(0x14 + k * 12 + 4);
        else if (type == 0x4002) dataAt = r.i32(0x14 + k * 12 + 4);
    }
    if (!r.ok()) { *err = r.error(); return false; }
    if (infoAt < 0 || dataAt < 0) { *err = "CSTM is missing its INFO or DATA block"; return false; }
    const int64_t body = infoAt + 8;
    const int64_t stream = body + r.i32(body + 4);
    const int64_t channelTable = body + r.i32(body + 0x14);
    const uint8_t enc = r.u8(stream);
    const bool loop = r.u8(stream + 1) != 0;
    const int channels = r.u8(stream + 2);
    const uint32_t rate = r.u32(stream + 4), loopStart = r.u32(stream + 8), sampleCount = r.u32(stream + 0xC);
    const uint32_t blockCount = r.u32(stream + 0x10), blockSize = r.u32(stream + 0x14), blockSamples = r.u32(stream + 0x18);
    const uint32_t lastSize = r.u32(stream + 0x1C), lastSamples = r.u32(stream + 0x20), lastPadded = r.u32(stream + 0x24);
    const int64_t sampleData = dataAt + 8 + r.i32(stream + 0x34);
    if (!r.ok()) { *err = r.error(); return false; }
    if (channels < 1 || channels > 2 || !rate || !sampleCount || !blockCount || !blockSamples || enc > 2) {
        *err = format("unsupported CSTM (encoding %d, %d channels)", enc, channels);
        return false;
    }
    if ((uint64_t)sampleCount * channels > (64u << 20)) { *err = "CSTM too long"; return false; }
    struct Dsp { int coefs[16]; int h1, h2; };
    std::vector<Dsp> dsp((size_t)channels);
    if (enc == 2) {
        const uint32_t n2 = r.u32(channelTable);
        for (int c = 0; c < channels; c++) {
            if ((uint32_t)c >= n2) { *err = "CSTM channel table is short"; return false; }
            const int64_t ch = channelTable + r.i32(channelTable + 4 + c * 8 + 4);
            const int64_t adpcm = ch + r.i32(ch + 4);
            for (int k = 0; k < 16; k++) dsp[c].coefs[k] = r.i16(adpcm + k * 2);
            dsp[c].h1 = r.i16(adpcm + 0x22);
            dsp[c].h2 = r.i16(adpcm + 0x24);
        }
        if (!r.ok()) { *err = r.error(); return false; }
    }
    out->pcm.assign((size_t)sampleCount * channels, 0);
    int64_t offset = sampleData;
    for (uint32_t block = 0; block < blockCount; block++) {
        const bool last = block == blockCount - 1;
        const uint32_t size = last ? lastSize : blockSize;
        const uint32_t stride = last ? lastPadded : blockSize;
        const uint32_t samples = last ? lastSamples : blockSamples;
        const uint64_t start = (uint64_t)block * blockSamples;
        if (start >= sampleCount) break;
        const uint32_t todo = (uint32_t)std::min<uint64_t>(samples, sampleCount - start);
        for (int c = 0; c < channels; c++) {
            const int64_t at = offset + (int64_t)c * stride;
            int16_t* o = out->pcm.data() + start * channels + c;
            if (enc == 1) {
                if (!r.has(at, (int64_t)todo * 2)) { *err = r.error(); return false; }
                for (uint32_t i = 0; i < todo; i++) o[(size_t)i * channels] = (int16_t)le16(d + at + i * 2);
            } else if (enc == 0) {
                if (!r.has(at, todo)) { *err = r.error(); return false; }
                for (uint32_t i = 0; i < todo; i++) o[(size_t)i * channels] = (int16_t)((int8_t)d[at + i] * 256);
            } else {
                // Nintendo DSP-ADPCM: 8 byte frames of a predictor / scale byte and 14 nibbles
                if (!r.has(at, ((int64_t)todo + 13) / 14 * 8)) { *err = r.error(); return false; }
                Dsp& s = dsp[c];
                uint32_t k = 0;
                int64_t f = at;
                while (k < todo) {
                    const uint8_t header = d[f];
                    const int scale = 1 << (header & 0xF);
                    const int c1 = s.coefs[(header >> 4) * 2], c2 = s.coefs[(header >> 4) * 2 + 1];
                    for (int i = 1; i < 8 && k < todo; i++) {
                        const uint8_t byte = d[f + i];
                        for (int shift = 4; shift >= 0 && k < todo; shift -= 4) {
                            int nib = (byte >> shift) & 0xF;
                            if (nib >= 8) nib -= 16;
                            const int64_t v = ((int64_t)nib * scale * 2048 + 1024 + (int64_t)c1 * s.h1 + (int64_t)c2 * s.h2);
                            int sample = (int)(v >= 0 ? v / 2048 : -((-v + 2047) / 2048));   // floor division
                            sample = std::clamp(sample, -32768, 32767);
                            o[(size_t)k * channels] = (int16_t)sample;
                            k++;
                            s.h2 = s.h1;
                            s.h1 = sample;
                        }
                    }
                    f += 8;
                }
            }
        }
        offset += (int64_t)channels * (last ? stride : size);
    }
    out->channels = channels;
    out->sampleRate = (int)rate;
    out->frames = sampleCount;
    out->loop = loop && loopStart < sampleCount;
    out->loopStart = loopStart;
    out->loopEnd = sampleCount;
    out->encoding = enc == 2 ? "dspAdpcm" : enc == 1 ? "pcm16" : "pcm8";
    return true;
}

void decodeIcon(const uint8_t* data, int size, std::vector<uint8_t>* out) {
    out->assign((size_t)size * size * 4, 0);
    size_t i = 0;
    for (int ty = 0; ty < size; ty += 8)
        for (int tx = 0; tx < size; tx += 8)
            for (int m = 0; m < 64; m++, i++) {
                const int x = (m & 1) | (m >> 1 & 2) | (m >> 2 & 4);
                const int y = (m >> 1 & 1) | (m >> 2 & 2) | (m >> 3 & 4);
                const uint32_t v = le16(data + i * 2);
                uint8_t* o = out->data() + ((size_t)(ty + y) * size + tx + x) * 4;
                o[0] = (uint8_t)((v >> 11 & 0x1F) * 255 / 31);
                o[1] = (uint8_t)((v >> 5 & 0x3F) * 255 / 63);
                o[2] = (uint8_t)((v & 0x1F) * 255 / 31);
                o[3] = 255;
            }
}

// smdh.parse + to_json
bool parseSmdh(const std::vector<uint8_t>& d, RomAssets* out, std::string* err) {
    if (d.size() < kSmdhSize || memcmp(d.data(), "SMDH", 4) != 0) { *err = "no SMDH icon"; return false; }
    const uint8_t* p = d.data();
    JVal j = jobj();
    j.set("version", jint(le16(p + 4)));
    JVal titles = jobj();
    for (int k = 0; k < 16; k++) {
        const uint8_t* base = p + 8 + k * 0x200;
        SmdhTitle& t = out->titles[k];
        t.shortDescription = utf16Field(base, 0x80);
        t.longDescription = utf16Field(base + 0x80, 0x100);
        t.publisher = utf16Field(base + 0x180, 0x80);
        JVal tv = jobj();
        tv.set("shortDescription", jstr(t.shortDescription));
        tv.set("longDescription", jstr(t.longDescription));
        tv.set("publisher", jstr(t.publisher));
        titles.set(kSmdhLanguages[k], std::move(tv));
    }
    j.set("titles", std::move(titles));
    static const char* const kBoards[11] = {"CERO", "ESRB", nullptr, "USK", "PEGI", nullptr, "PEGI_PRT", "PEGI_BBFC", "COB", "GRB", "CGSRR"};
    JVal ratings = jobj();
    for (int k = 0; k < 16; k++) {
        const uint8_t v = p[0x2008 + k];
        const char* board = k < 11 ? kBoards[k] : nullptr;
        if (!v && !board) continue;
        JVal rv = jobj();
        rv.set("enabled", jbool(v & 0x80));
        rv.set("pending", jbool(v & 0x40));
        rv.set("noRestriction", jbool(v & 0x20));
        rv.set("age", jint(v & 0x1F));
        ratings.set(board ? board : std::to_string(k), std::move(rv));
    }
    j.set("ratings", std::move(ratings));
    const uint32_t region = le32(p + 0x2018), matchId = le32(p + 0x201C), flags = le32(p + 0x2028);
    const uint64_t matchBitId = le64(p + 0x2020);
    j.set("regionLockout", jint(region));
    JVal regions = jarr();
    static const char* const kRegions[7] = {"japan", "northAmerica", "europe", "australia", "china", "korea", "taiwan"};
    if (region == 0x7FFFFFFF) regions.push(jstr("all"));
    else for (int b = 0; b < 7; b++) if (region >> b & 1) regions.push(jstr(kRegions[b]));
    j.set("regions", std::move(regions));
    j.set("matchMakerId", jint(matchId));
    j.set("matchMakerBitId", jnum((double)matchBitId));
    j.set("flags", jint(flags));
    static const std::pair<int, const char*> kFlags[] = {{0, "visible"}, {1, "autoBoot"}, {2, "allow3D"}, {3, "requireEula"},
                                                         {4, "autoSaveOnExit"}, {5, "extendedBanner"}, {6, "ratingRequired"},
                                                         {7, "usesSaveData"}, {8, "recordUsage"}, {10, "disableSaveBackups"},
                                                         {12, "new3dsExclusive"}};
    JVal flagNames = jarr();
    for (const auto& f : kFlags) if (flags >> f.first & 1) flagNames.push(jstr(f.second));
    j.set("flagNames", std::move(flagNames));
    JVal eula = jarr();
    eula.push(jint(p[0x202D]));
    eula.push(jint(p[0x202C]));
    j.set("eulaVersion", std::move(eula));
    float frame;
    memcpy(&frame, p + 0x2030, 4);
    j.set("optimalBannerFrame", jnum(frame));
    j.set("streetPassId", jint(le32(p + 0x2034)));
    out->smdh = bj(j);
    decodeIcon(p + kIconOffset, 24, &out->icon24);
    decodeIcon(p + kIconOffset + 24 * 24 * 2, 48, &out->icon48);
    return true;
}

// ================================================================== banner (build_assets.py export_banner)

const char* const kCbmdSlots[14] = {"common", "EUR-ENG", "EUR-FRE", "EUR-GER", "EUR-ITA", "EUR-SPA", "EUR-DUT", "EUR-POR",
                                    "EUR-RUS", "JPN-JPN", "USA-ENG", "USA-FRE", "USA-SPA", "USA-POR"};

using Blobs = std::vector<std::pair<std::string, std::vector<uint8_t>>>;

struct BannerData {
    Blobs model;                        // cgfx, gltf, bin/0, tex/<name> (pack_model entries)
    // CBMD language slot -> the same entries for that slot's own CGFX (gltf / bin/0 only when
    // it holds a model that converts); its tex/<name> entries are the replacement textures
    std::map<std::string, Blobs> slots;
    bool hasSound = false;
    std::string soundError;
    RomSound sound;
};

// model_texture_png: alpha only formats sample as (0, 0, 0, a).
std::vector<uint8_t> modelTexturePng(const Image& img, const JVal& fmt) {
    if (fmt.is("A4") || fmt.is("A8")) {
        std::vector<uint8_t> px = img.rgba;
        for (size_t k = 0; k < px.size(); k += 4) px[k] = px[k + 1] = px[k + 2] = 0;
        return encodePng(px.data(), (int)img.w, (int)img.h);
    }
    return encodePng(img.rgba.data(), (int)img.w, (int)img.h);
}

// The URI every texture with an image gets in cgfx.export (textures/<safe name>.png), and for
// each URI the image saved there last (two names with the same safe name share one file).
void textureUris(const Scene& sc, std::map<std::string, std::string>* uris, std::map<std::string, size_t>* fileImage) {
    for (const JVal& t : sc.root["textures"].a) {
        const JVal* img = t.find("image");
        if (!img || extKind(*img) != ExtImage) continue;
        const JVal& name = t["name"];
        const std::string uri = "textures/" + safeName(name.isStr() ? &name.s : nullptr) + ".png";
        (*uris)[pyKey(name)] = uri;
        (*fileImage)[uri] = (size_t)img->i;
    }
}

// cgfx.export followed by ctr_pack.py pack_model, in memory: the pack entries of one CGFX.
// Returns false when the CGFX does not parse. A model whose glTF cannot be built leaves
// `gltfError` set and the entries without gltf / bin/0.
bool exportCgfx(const std::vector<uint8_t>& data, Blobs* entries, std::string* gltfError, std::string* err) {
    Scene sc;
    {
        CgfxParser p(data.data(), data.size(), &sc);
        if (!p.parse(err)) return false;
    }
    std::map<std::string, std::string> uris;
    std::map<std::string, size_t> fileImage;
    textureUris(sc, &uris, &fileImage);
    JVal* modelsJ = sc.root.find("models");
    JVal gltf;
    std::vector<uint8_t> bin;
    bool haveGltf = false;
    if (modelsJ && !modelsJ->a.empty()) {
        static const JVal kNoAnims = jarr();
        const JVal* anims = sc.root.find("skeletalAnimations");
        const std::string first = safeName(modelsJ->a[0]["name"].isStr() ? &modelsJ->a[0]["name"].s : nullptr);
        for (JVal& m : modelsJ->a) {
            const std::string stem = safeName(m["name"].isStr() ? &m["name"].s : nullptr);
            GltfBuilder b(sc, uris);
            b.build(m, anims ? *anims : kNoAnims, 30);
            if (!b.ok()) {
                *gltfError = b.error();
                haveGltf = false;
                break;
            }
            JVal gj;
            std::vector<uint8_t> gb;
            b.finish(stem + ".bin", &gj, &gb);
            // pack_model reads the first model's file; a later model with the same safe
            // name overwrote it
            if (stem == first) { gltf = std::move(gj); bin = std::move(gb); haveGltf = true; }
            m.set("gltf", jstr(stem + ".gltf"));
        }
    }
    JVal json = toJson(sc, sc.root, uris);
    entries->push_back({"cgfx", bj(json)});
    if (haveGltf) {
        entries->push_back({"gltf", bj(gltf)});
        entries->push_back({"bin/0", std::move(bin)});
    }
    for (const JVal& t : json["textures"].a) {
        const JVal& uri = t["uri"];
        if (!uri.isStr() || !t["name"].isStr()) continue;
        auto fi = fileImage.find(uri.s);
        if (fi == fileImage.end()) continue;
        entries->push_back({"tex/" + t["name"].s, modelTexturePng(sc.images[fi->second], t["format"])});
    }
    return true;
}

bool convertBanner(const std::vector<uint8_t>& data, BannerData* out, std::string* err) {
    // cbmd.parse
    if (data.size() < 0x88 || memcmp(data.data(), "CBMD", 4) != 0) { *err = "not a CBMD banner"; return false; }
    uint32_t offsets[14];
    for (int k = 0; k < 14; k++) offsets[k] = le32(data.data() + 0x08 + k * 4);
    const uint32_t soundOffset = le32(data.data() + 0x84);
    if (soundOffset > data.size()) { *err = format("CBMD sound offset 0x%x past the end", soundOffset); return false; }
    std::vector<uint64_t> starts;
    for (uint32_t o : offsets) if (o) starts.push_back(o);
    if (soundOffset) starts.push_back(soundOffset);
    starts.push_back(data.size());
    std::sort(starts.begin(), starts.end());
    starts.erase(std::unique(starts.begin(), starts.end()), starts.end());
    std::map<int, std::vector<uint8_t>> models;
    for (int k = 0; k < 14; k++) {
        const uint64_t off = offsets[k];
        if (!off) continue;
        // the block ends where the next present block (or the sound, or the file) starts
        auto next = std::upper_bound(starts.begin(), starts.end(), off);
        if (next == starts.end()) { *err = format("CBMD %s block starts at the end of the file", kCbmdSlots[k]); return false; }
        const uint64_t end = *next;
        std::vector<uint8_t> blob;
        const uint64_t from = std::min<uint64_t>(off, data.size());
        const uint64_t to = std::max(from, std::min<uint64_t>(end, data.size()));
        if (!lzDecompress(data.data() + from, (size_t)(to - from), &blob)) { *err = format("CBMD %s block does not decompress", kCbmdSlots[k]); return false; }
        if (blob.size() < 4 || memcmp(blob.data(), "CGFX", 4) != 0) { *err = format("CBMD %s block is not CGFX", kCbmdSlots[k]); return false; }
        models[k] = std::move(blob);
    }
    if (!models.count(0)) { *err = "CBMD has no common CGFX"; return false; }

    // the common model (cgfx.export + pack_model)
    std::string gltfError;
    if (!exportCgfx(models[0], &out->model, &gltfError, err)) return false;
    if (!gltfError.empty()) { *err = gltfError; return false; }

    // language slots. export_banner only parses them (a slot that does not parse fails the
    // banner) and keeps their textures; their own model is converted too when it can be.
    for (const auto& m : models) {
        if (m.first == 0) continue;
        std::string slotGltfError;
        if (!exportCgfx(m.second, &out->slots[kCbmdSlots[m.first]], &slotGltfError, err)) return false;
        if (!slotGltfError.empty()) ALOGW("CBMD %s model not converted: %s", kCbmdSlots[m.first], slotGltfError.c_str());
    }

    // audio.cwav_to_wav. A sound that does not decode costs the banner its sound only.
    if (soundOffset && soundOffset + 4 <= data.size() && memcmp(data.data() + soundOffset, "CWAV", 4) == 0) {
        std::string serr;
        if (decodeCwav(data.data() + soundOffset, data.size() - soundOffset, &out->sound, &serr)) {
            out->hasSound = true;
        } else {
            out->soundError = serr;
            out->sound = RomSound();
        }
    }
    return true;
}

// titlebanner.ts bannerTextures: the region's slot for the language, else the region's
// English, else its Japanese, else none (common textures only).
std::string chooseSlot(const std::vector<std::string>& slots, const std::string& region, const std::string& language) {
    static const std::pair<const char*, const char*> kCodes[] = {{"japanese", "JPN"}, {"english", "ENG"}, {"french", "FRE"},
                                                                 {"german", "GER"}, {"italian", "ITA"}, {"spanish", "SPA"},
                                                                 {"dutch", "DUT"}, {"portuguese", "POR"}, {"russian", "RUS"}};
    const std::string lang = lower(language);
    std::string code = "ENG";
    for (const auto& c : kCodes)
        if (lang == c.first || lang == lower(c.second)) code = c.second;
    std::string reg = region;
    for (char& ch : reg) ch = (char)toupper((unsigned char)ch);
    for (const std::string& want : {reg + "-" + code, reg + "-ENG", reg + "-JPN"})
        if (std::find(slots.begin(), slots.end(), want) != slots.end()) return want;
    return std::string();
}

// The common model pack with the slot's textures replacing (or joining) common textures of
// the same name, and the slot's own model as a pack when it has one.
void buildModelPacks(const Blobs& model, const Blobs* slot, std::vector<uint8_t>* pack, std::vector<uint8_t>* languagePack) {
    Blobs entries = model;
    bool slotModel = false;
    if (slot) {
        for (const auto& e : *slot) {
            if (e.first.rfind("tex/", 0) == 0) entries.push_back(e);   // later entries win
            if (e.first == "gltf") slotModel = true;
        }
    }
    *pack = writeCpk(entries);
    languagePack->clear();
    if (slotModel) *languagePack = writeCpk(*slot);
}

// ================================================================== ROM containers (ncch.py)

class RomFile {
public:
    explicit RomFile(const std::string& path) { mFd = ::open(path.c_str(), O_RDONLY | O_CLOEXEC); }
    ~RomFile() { if (mFd >= 0) ::close(mFd); }
    bool valid() const { return mFd >= 0; }
    uint64_t size() const {
        struct stat st;
        return fstat(mFd, &st) == 0 ? (uint64_t)st.st_size : 0;
    }
    bool read(uint64_t off, void* dst, size_t n) const {
        uint8_t* p = (uint8_t*)dst;
        while (n) {
            const ssize_t got = pread(mFd, p, n, (off_t)off);
            if (got < 0 && errno == EINTR) continue;
            if (got <= 0) return false;
            p += got;
            off += (uint64_t)got;
            n -= (size_t)got;
        }
        return true;
    }
private:
    int mFd = -1;
};

uint64_t align64(uint64_t v) { return (v + 63) & ~(uint64_t)63; }

// The offset of the main NCCH: NCSD partition 0, a bare NCCH, or the first CIA content.
bool locateNcch(const RomFile& f, uint64_t* ncch, std::string* err) {
    uint8_t hdr[0x200];
    if (!f.read(0, hdr, sizeof(hdr))) { *err = "file is too short"; return false; }
    if (memcmp(hdr + 0x100, "NCSD", 4) == 0) {
        const uint64_t unit = 0x200ull << std::min<int>(hdr[0x18E], 20);
        const uint64_t off = le32(hdr + 0x120) * unit;
        if (!off || !le32(hdr + 0x124)) { *err = "NCSD has no partition 0"; return false; }
        *ncch = off;
        return true;
    }
    if (memcmp(hdr + 0x100, "NCCH", 4) == 0) { *ncch = 0; return true; }
    // CIA: header, certificate chain, ticket, TMD, then the contents, each 64 byte aligned
    const uint32_t headerSize = le32(hdr);
    if (headerSize != 0x2020) { *err = "not a 3DS ROM (no NCSD, NCCH or CIA header)"; return false; }
    const uint32_t certSize = le32(hdr + 0x08), ticketSize = le32(hdr + 0x0C), tmdSize = le32(hdr + 0x10);
    const uint64_t certOff = align64(headerSize);
    const uint64_t ticketOff = align64(certOff + certSize);
    const uint64_t tmdOff = align64(ticketOff + ticketSize);
    const uint64_t contentOff = align64(tmdOff + tmdSize);
    std::vector<uint8_t> bitmap(0x2000);
    if (!f.read(0x20, bitmap.data(), bitmap.size())) { *err = "CIA header is truncated"; return false; }
    if (tmdSize < 0x200 || tmdSize > (1u << 20)) { *err = "CIA TMD size is invalid"; return false; }
    std::vector<uint8_t> tmd(tmdSize);
    if (!f.read(tmdOff, tmd.data(), tmd.size())) { *err = "CIA TMD is truncated"; return false; }
    size_t sig;
    switch (be32(tmd.data())) {
        case 0x010000: case 0x010003: sig = 0x200 + 0x3C; break;
        case 0x010001: case 0x010004: sig = 0x100 + 0x3C; break;
        case 0x010002: case 0x010005: sig = 0x3C + 0x40; break;
        default: *err = "CIA TMD has an unknown signature type"; return false;
    }
    const size_t th = 4 + sig;
    if (th + 0xC4 + 64 * 0x24 > tmd.size()) { *err = "CIA TMD is truncated"; return false; }
    const uint16_t contents = be16(tmd.data() + th + 0x9E);
    const size_t chunks = th + 0xC4 + 64 * 0x24;
    if (chunks + (size_t)contents * 0x30 > tmd.size()) { *err = "CIA TMD is truncated"; return false; }
    const uint64_t off = contentOff;
    for (uint16_t k = 0; k < contents; k++) {
        const uint8_t* c = tmd.data() + chunks + k * 0x30;
        const uint16_t index = be16(c + 4), type = be16(c + 6);
        if (!(bitmap[index >> 3] & (0x80 >> (index & 7)))) continue;   // content not in this CIA
        // the first content present is the main NCCH (content index 0 for an application)
        uint8_t nh[0x200];
        if (!f.read(off, nh, sizeof(nh))) { *err = "CIA content is truncated"; return false; }
        if (memcmp(nh + 0x100, "NCCH", 4) != 0) {
            *err = (type & 1) ? "CIA content is encrypted (title key crypto)" : "CIA content is not an NCCH";
            return false;
        }
        *ncch = off;
        return true;
    }
    *err = "CIA has no content";
    return false;
}

// An ExeFS header whose file names are printable ASCII, NUL padded, with sane ranges is
// plaintext: an encrypted one is random bytes. The NoCrypto flag is not trusted, decrypted
// dumps often keep the original flags.
bool exefsReadable(const uint8_t* h, uint64_t exefsSize) {
    int named = 0;
    for (int k = 0; k < 10; k++) {
        const uint8_t* e = h + k * 16;
        bool ended = false;
        for (int c = 0; c < 8; c++) {
            if (!e[c]) { ended = true; continue; }
            if (ended || e[c] < 0x20 || e[c] > 0x7E) return false;
        }
        if (!e[0]) continue;
        named++;
        const uint64_t off = le32(e + 8), size = le32(e + 12);
        if (0x200 + off + size > exefsSize) return false;
    }
    return named > 0;
}

}  // namespace

bool readSmdhTitles(const std::string& path, SmdhTitle out[16]) {
    // only the title block is read (8 + 16 * 0x200 bytes)
    const size_t need = 8 + 16 * 0x200;
    std::vector<uint8_t> d(need);
    FILE* f = fopen(path.c_str(), "rb");
    if (!f) return false;
    const bool ok = fread(d.data(), 1, need, f) == need;
    fclose(f);
    if (!ok || memcmp(d.data(), "SMDH", 4) != 0) return false;
    for (int k = 0; k < 16; k++) {
        const uint8_t* base = d.data() + 8 + k * 0x200;
        out[k].shortDescription = utf16Field(base, 0x80);
        out[k].longDescription = utf16Field(base + 0x80, 0x100);
        out[k].publisher = utf16Field(base + 0x180, 0x80);
    }
    return true;
}

int smdhLanguageIndex(const std::string& englishName) {
    std::string l;
    for (char c : englishName) l += (char)tolower((unsigned char)c);
    if (l.find("chinese") != std::string::npos)
        return l.find("trad") != std::string::npos ? 11 : 6;
    for (int k = 0; k < 12; k++) {
        std::string n = kSmdhLanguages[k];
        for (char& c : n) c = (char)tolower((unsigned char)c);
        if (!l.empty() && n.rfind(l, 0) == 0) return k;
    }
    return -1;
}


bool decodeCtrWave(const uint8_t* data, size_t size, RomSound* out, std::string* error) {
    std::string err;
    const bool ok = decodeCwav(data, size, out, &err);
    if (!ok && error) *error = err;
    return ok;
}

bool decodeCtrStream(const uint8_t* data, size_t size, RomSound* out, std::string* error) {
    std::string err;
    const bool ok = decodeCstm(data, size, out, &err);
    if (!ok && error) *error = err;
    return ok;
}

bool readRomExeFs(const std::string& romPath, std::vector<uint8_t>* icon, std::vector<uint8_t>* banner,
                  uint64_t* programId, std::string* productCode, std::string* error) {
    icon->clear();
    banner->clear();
    RomFile f(romPath);
    if (!f.valid()) { *error = format("cannot open: %s", strerror(errno)); return false; }
    uint64_t ncch = 0;
    if (!locateNcch(f, &ncch, error)) return false;
    uint8_t hdr[0x200];
    if (!f.read(ncch, hdr, sizeof(hdr)) || memcmp(hdr + 0x100, "NCCH", 4) != 0) { *error = "not an NCCH"; return false; }
    if (programId) *programId = le64(hdr + 0x118);
    if (productCode) {
        size_t n = 16;
        while (n && !hdr[0x150 + n - 1]) n--;
        *productCode = validUtf8(hdr + 0x150, n) ? std::string((const char*)hdr + 0x150, n) : std::string();
    }
    const uint64_t unit = 0x200ull << std::min<int>(hdr[0x18E], 20);
    const uint64_t exefs = (uint64_t)le32(hdr + 0x1A0) * unit, exefsSize = (uint64_t)le32(hdr + 0x1A4) * unit;
    if (!exefsSize) { *error = "NCCH has no ExeFS"; return false; }
    uint8_t eh[0x200];
    if (!f.read(ncch + exefs, eh, sizeof(eh))) { *error = "ExeFS is truncated"; return false; }
    if (!exefsReadable(eh, exefsSize)) {
        *error = (hdr[0x18F] & 4) ? "ExeFS header is not readable (corrupt dump)" : "NCCH is encrypted";
        return false;
    }
    for (int k = 0; k < 10; k++) {
        const uint8_t* e = eh + k * 16;
        char name[9] = {0};
        memcpy(name, e, 8);
        std::vector<uint8_t>* dst = !strcmp(name, "icon") ? icon : !strcmp(name, "banner") ? banner : nullptr;
        if (!dst) continue;
        const uint32_t off = le32(e + 8), size = le32(e + 12);
        if (size > (32u << 20)) { *error = format("ExeFS %s is too large", name); return false; }
        dst->resize(size);
        if (size && !f.read(ncch + exefs + 0x200 + off, dst->data(), size)) { *error = format("ExeFS %s is truncated", name); return false; }
    }
    return true;
}

const SmdhTitle& RomAssets::title(const std::string& language) const {
    static const std::pair<const char*, int> kMenuNames[] = {
        {"japanese", 0}, {"english", 1}, {"french", 2}, {"german", 3}, {"italian", 4}, {"spanish", 5},
        {"simplified chinese", 6}, {"chinese", 6}, {"korean", 7}, {"dutch", 8}, {"portuguese", 9}, {"russian", 10},
        {"traditional chinese", 11}, {"taiwanese", 11}};
    const std::string l = lower(language);
    int index = 1;
    for (int k = 0; k < 16; k++) if (l == lower(kSmdhLanguages[k])) index = k;
    for (const auto& m : kMenuNames) if (l == m.first) index = m.second;
    return titles[index].shortDescription.empty() ? titles[1] : titles[index];
}

bool extractRom(const std::string& romPath, RomAssets* out, std::string* error, const std::string& region,
                const std::string& language) {
    *out = RomAssets();
    std::vector<uint8_t> icon, banner;
    if (!readRomExeFs(romPath, &icon, &banner, &out->programId, &out->productCode, error)) return false;
    if (!parseSmdh(icon, out, error)) return false;
    if (banner.empty()) return true;
    BannerData bd;
    std::string berr;
    if (!convertBanner(banner, &bd, &berr)) {
        out->bannerError = "banner: " + berr;
        ALOGW("%s: %s", romPath.c_str(), out->bannerError.c_str());
        return true;
    }
    std::vector<std::string> slots;
    for (const auto& s : bd.slots) slots.push_back(s.first);
    out->bannerSlot = chooseSlot(slots, region, language);
    buildModelPacks(bd.model, out->bannerSlot.empty() ? nullptr : &bd.slots[out->bannerSlot], &out->modelPack, &out->languageModelPack);
    out->hasBanner = true;
    out->hasSound = bd.hasSound;
    out->soundError = bd.soundError;
    out->sound = std::move(bd.sound);
    return true;
}

// ================================================================== cache

std::string cacheKey(const std::string& romPath) {
    struct stat st;
    uint64_t size = 0, mtime = 0;
    if (stat(romPath.c_str(), &st) == 0) {
        size = (uint64_t)st.st_size;
        mtime = (uint64_t)st.st_mtim.tv_sec * 1000000000ull + (uint64_t)st.st_mtim.tv_nsec;
    }
    // FNV-1a 64 over path, size, mtime and the cache version
    uint64_t h = 0xcbf29ce484222325ull;
    auto mix = [&](const void* p, size_t n) {
        const uint8_t* b = (const uint8_t*)p;
        for (size_t k = 0; k < n; k++) { h ^= b[k]; h *= 0x100000001b3ull; }
    };
    mix(romPath.data(), romPath.size());
    mix(&size, sizeof(size));
    mix(&mtime, sizeof(mtime));
    const int version = kCacheVersion;
    mix(&version, sizeof(version));
    char buf[17];
    snprintf(buf, sizeof(buf), "%016" PRIx64, h);
    return buf;
}

namespace {

bool readWholeFile(const std::string& path, std::vector<uint8_t>* out) {
    RomFile f(path);
    if (!f.valid()) return false;
    const uint64_t n = f.size();
    if (n > (256u << 20)) return false;
    out->resize((size_t)n);
    return n == 0 || f.read(0, out->data(), (size_t)n);
}

bool writeFileAtomic(const std::string& path, const std::vector<uint8_t>& data) {
    const std::string tmp = path + format(".tmp%d", (int)getpid());
    const int fd = ::open(tmp.c_str(), O_WRONLY | O_CREAT | O_TRUNC | O_CLOEXEC, 0644);
    if (fd < 0) return false;
    size_t done = 0;
    while (done < data.size()) {
        const ssize_t n = ::write(fd, data.data() + done, data.size() - done);
        if (n < 0 && errno == EINTR) continue;
        if (n <= 0) { ::close(fd); unlink(tmp.c_str()); return false; }
        done += (size_t)n;
    }
    if (fsync(fd) != 0 || ::close(fd) != 0) { unlink(tmp.c_str()); return false; }
    if (rename(tmp.c_str(), path.c_str()) != 0) { unlink(tmp.c_str()); return false; }
    return true;
}

bool makeDirs(const std::string& dir) {
    if (dir.empty()) return false;
    std::string cur;
    for (size_t k = 0; k <= dir.size(); k++) {
        if (k == dir.size() || dir[k] == '/') {
            if (!cur.empty() && mkdir(cur.c_str(), 0755) != 0 && errno != EEXIST) return false;
        }
        if (k < dir.size()) cur += dir[k];
    }
    return true;
}

// All entries of a CPK1 pack in memory, bounds checked.
bool readCpk(const std::vector<uint8_t>& d, std::vector<std::pair<std::string, std::pair<size_t, size_t>>>* out) {
    if (d.size() < 8 || memcmp(d.data(), "CPK1", 4) != 0) return false;
    const uint32_t count = le32(d.data() + 4);
    if (8 + (uint64_t)count * 16 > d.size()) return false;
    for (uint32_t k = 0; k < count; k++) {
        const uint8_t* e = d.data() + 8 + k * 16;
        const uint64_t no = le32(e), nl = le32(e + 4), dof = le32(e + 8), dl = le32(e + 12);
        if (no + nl > d.size() || dof + dl > d.size()) return false;
        out->push_back({std::string((const char*)d.data() + no, nl), {(size_t)dof, (size_t)dl}});
    }
    return true;
}

std::vector<uint8_t> iconEntry(const std::vector<uint8_t>& rgba, uint32_t size) {
    std::vector<uint8_t> o;
    put32(o, size);
    put32(o, size);
    o.insert(o.end(), rgba.begin(), rgba.end());
    return o;
}

bool loadCache(const std::string& path, RomAssets* out, const std::string& region, const std::string& language) {
    std::vector<uint8_t> file;
    std::vector<std::pair<std::string, std::pair<size_t, size_t>>> entries;
    if (!readWholeFile(path, &file) || !readCpk(file, &entries)) return false;
    std::map<std::string, std::vector<uint8_t>> e;
    for (const auto& x : entries) e[x.first] = std::vector<uint8_t>(file.begin() + x.second.first, file.begin() + x.second.first + x.second.second);
    auto bjDoc = [&](const char* name, BjDoc* doc) { auto it = e.find(name); return it != e.end() && doc->open(it->second.data(), it->second.size()); };
    BjDoc info;
    if (!bjDoc("info", &info) || info.root()["version"].asInt() != kCacheVersion) return false;
    *out = RomAssets();
    BjValue ir = info.root();
    out->programId = strtoull(ir["programId"].str().c_str(), nullptr, 16);
    out->productCode = ir["productCode"].str();
    out->bannerError = ir["bannerError"].str();
    out->soundError = ir["soundError"].str();
    BjDoc smdh;
    if (!bjDoc("smdh", &smdh)) return false;
    out->smdh = e["smdh"];
    for (int k = 0; k < 16; k++) {
        BjValue t = smdh.root()["titles"][kSmdhLanguages[k]];
        out->titles[k].shortDescription = t["shortDescription"].str();
        out->titles[k].longDescription = t["longDescription"].str();
        out->titles[k].publisher = t["publisher"].str();
    }
    for (auto [name, size, dst] : {std::make_tuple("icon48", 48u, &out->icon48), std::make_tuple("icon24", 24u, &out->icon24)}) {
        const std::vector<uint8_t>& v = e[name];
        if (v.size() != 8 + (size_t)size * size * 4 || le32(v.data()) != size || le32(v.data() + 4) != size) return false;
        dst->assign(v.begin() + 8, v.end());
    }
    if (ir["hasBanner"].asBool()) {
        Blobs model;
        std::map<std::string, Blobs> slots;
        for (auto& x : e) {
            const std::string& n = x.first;
            if (n == "cgfx" || n == "gltf" || n.rfind("bin/", 0) == 0 || n.rfind("tex/", 0) == 0) {
                model.push_back({n, x.second});
            } else if (n.rfind("slot/", 0) == 0) {
                const size_t sl = n.find('/', 5);
                if (sl == std::string::npos) return false;
                slots[n.substr(5, sl - 5)].push_back({n.substr(sl + 1), x.second});
            }
        }
        for (BjValue s : ir["slots"]) slots[s.str()];   // a slot with no entries still counts
        std::vector<std::string> names;
        for (const auto& s : slots) names.push_back(s.first);
        out->bannerSlot = chooseSlot(names, region, language);
        buildModelPacks(model, out->bannerSlot.empty() ? nullptr : &slots[out->bannerSlot], &out->modelPack, &out->languageModelPack);
        out->hasBanner = true;
    }
    BjDoc si;
    if (bjDoc("soundInfo", &si) && si.root()["present"].asBool()) {
        // the PCM comes back out of the WAV data chunk
        const std::vector<uint8_t>& w = e["sound"];
        size_t pos = 12;
        bool found = false;
        while (pos + 8 <= w.size()) {
            const uint32_t len = le32(w.data() + pos + 4);
            if (memcmp(w.data() + pos, "data", 4) == 0 && pos + 8 + (uint64_t)len <= w.size()) {
                out->sound.pcm.resize(len / 2);
                for (size_t k = 0; k < out->sound.pcm.size(); k++) out->sound.pcm[k] = (int16_t)le16(w.data() + pos + 8 + k * 2);
                found = true;
                break;
            }
            pos += 8 + len + (len & 1);
        }
        if (!found) return false;
        BjValue s = si.root();
        out->sound.encoding = s["encoding"].str();
        out->sound.sampleRate = s["sampleRate"].asInt();
        out->sound.channels = s["channels"].asInt();
        out->sound.frames = (uint32_t)s["frames"].asInt();
        out->sound.loop = s["loop"].asBool();
        out->sound.loopStart = (uint32_t)s["loopStart"].asInt();
        out->sound.loopEnd = (uint32_t)s["loopEnd"].asInt();
        if (out->sound.channels <= 0 || out->sound.pcm.size() != (size_t)out->sound.frames * out->sound.channels) return false;
        out->hasSound = true;
    }
    return true;
}

}  // namespace

bool loadOrExtract(const std::string& romPath, const std::string& cacheDir, RomAssets* out, std::string* error,
                   const std::string& region, const std::string& language) {
    const std::string path = cacheDir + "/" + cacheKey(romPath) + ".cpk";
    if (loadCache(path, out, region, language)) return true;

    *out = RomAssets();
    std::vector<uint8_t> icon, banner;
    if (!readRomExeFs(romPath, &icon, &banner, &out->programId, &out->productCode, error)) return false;
    if (!parseSmdh(icon, out, error)) return false;
    BannerData bd;
    bool hasBanner = false;
    if (!banner.empty()) {
        std::string berr;
        if (convertBanner(banner, &bd, &berr)) hasBanner = true;
        else out->bannerError = "banner: " + berr;
    }
    Blobs entries;
    JVal info = jobj();
    info.set("version", jint(kCacheVersion));
    info.set("programId", jstr(format("%016" PRIx64, out->programId)));
    info.set("productCode", jstr(out->productCode));
    info.set("hasBanner", jbool(hasBanner));
    info.set("bannerError", jstr(out->bannerError));
    info.set("soundError", jstr(bd.soundError));
    JVal slotNames = jarr();
    for (const auto& s : bd.slots) slotNames.push(jstr(s.first));
    info.set("slots", std::move(slotNames));
    entries.push_back({"info", bj(info)});
    entries.push_back({"smdh", out->smdh});
    entries.push_back({"icon48", iconEntry(out->icon48, 48)});
    entries.push_back({"icon24", iconEntry(out->icon24, 24)});
    JVal si = jobj();
    si.set("present", jbool(bd.hasSound));
    if (bd.hasSound) {
        si.set("encoding", jstr(bd.sound.encoding));
        si.set("sampleRate", jint(bd.sound.sampleRate));
        si.set("channels", jint(bd.sound.channels));
        si.set("frames", jint(bd.sound.frames));
        si.set("loop", jbool(bd.sound.loop));
        si.set("loopStart", jint(bd.sound.loopStart));
        si.set("loopEnd", jint(bd.sound.loopEnd));
        entries.push_back({"sound", wavBytes(bd.sound)});
    }
    entries.push_back({"soundInfo", bj(si)});
    if (hasBanner) {
        for (const auto& m : bd.model) entries.push_back(m);
        for (const auto& s : bd.slots)
            for (const auto& t : s.second) entries.push_back({"slot/" + s.first + "/" + t.first, t.second});
        std::vector<std::string> slots;
        for (const auto& s : bd.slots) slots.push_back(s.first);
        out->bannerSlot = chooseSlot(slots, region, language);
        buildModelPacks(bd.model, out->bannerSlot.empty() ? nullptr : &bd.slots[out->bannerSlot], &out->modelPack, &out->languageModelPack);
        out->hasBanner = true;
        out->hasSound = bd.hasSound;
        out->soundError = bd.soundError;
        out->sound = std::move(bd.sound);
    }
    if (!makeDirs(cacheDir) || !writeFileAtomic(path, writeCpk(entries)))
        ALOGW("cannot write the banner cache %s: %s", path.c_str(), strerror(errno));
    return true;
}

}  // namespace ctr
}  // namespace android

#if defined(__clang__)
#pragma float_control(pop)
#endif
