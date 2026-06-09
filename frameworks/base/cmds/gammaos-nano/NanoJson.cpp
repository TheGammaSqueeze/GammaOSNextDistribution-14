// NanoJson implementation - see NanoJson.h.
#define LOG_TAG "GammaOSNano"

#include "NanoJson.h"

#include <cstdlib>
#include <cstdio>
#include <cstring>

namespace android {
namespace njson {

namespace {

struct Parser {
    const char* p;
    const char* end;

    explicit Parser(const std::string& s) : p(s.c_str()), end(s.c_str() + s.size()) {}

    void skipWs() {
        while (p < end) {
            char c = *p;
            if (c == ' ' || c == '\t' || c == '\n' || c == '\r') { p++; continue; }
            break;
        }
    }

    bool atEnd() { skipWs(); return p >= end; }

    // Append the UTF-8 encoding of a Unicode code point to out.
    static void appendUtf8(std::string& out, unsigned cp) {
        if (cp <= 0x7F) {
            out.push_back((char)cp);
        } else if (cp <= 0x7FF) {
            out.push_back((char)(0xC0 | (cp >> 6)));
            out.push_back((char)(0x80 | (cp & 0x3F)));
        } else if (cp <= 0xFFFF) {
            out.push_back((char)(0xE0 | (cp >> 12)));
            out.push_back((char)(0x80 | ((cp >> 6) & 0x3F)));
            out.push_back((char)(0x80 | (cp & 0x3F)));
        } else {
            out.push_back((char)(0xF0 | (cp >> 18)));
            out.push_back((char)(0x80 | ((cp >> 12) & 0x3F)));
            out.push_back((char)(0x80 | ((cp >> 6) & 0x3F)));
            out.push_back((char)(0x80 | (cp & 0x3F)));
        }
    }

    bool parseHex4(unsigned* out) {
        if (end - p < 4) return false;
        unsigned v = 0;
        for (int i = 0; i < 4; i++) {
            char c = p[i];
            v <<= 4;
            if (c >= '0' && c <= '9')      v |= (unsigned)(c - '0');
            else if (c >= 'a' && c <= 'f') v |= (unsigned)(c - 'a' + 10);
            else if (c >= 'A' && c <= 'F') v |= (unsigned)(c - 'A' + 10);
            else return false;
        }
        p += 4;
        *out = v;
        return true;
    }

    bool parseString(std::string* out) {
        if (p >= end || *p != '"') return false;
        p++;
        out->clear();
        while (p < end) {
            char c = *p++;
            if (c == '"') return true;
            if (c == '\\') {
                if (p >= end) return false;
                char e = *p++;
                switch (e) {
                    case '"':  out->push_back('"');  break;
                    case '\\': out->push_back('\\'); break;
                    case '/':  out->push_back('/');  break;
                    case 'b':  out->push_back('\b'); break;
                    case 'f':  out->push_back('\f'); break;
                    case 'n':  out->push_back('\n'); break;
                    case 'r':  out->push_back('\r'); break;
                    case 't':  out->push_back('\t'); break;
                    case 'u': {
                        unsigned cp;
                        if (!parseHex4(&cp)) return false;
                        // Handle UTF-16 surrogate pairs.
                        if (cp >= 0xD800 && cp <= 0xDBFF) {
                            if (end - p >= 2 && p[0] == '\\' && p[1] == 'u') {
                                p += 2;
                                unsigned lo;
                                if (!parseHex4(&lo)) return false;
                                if (lo >= 0xDC00 && lo <= 0xDFFF) {
                                    cp = 0x10000 + (((cp - 0xD800) << 10) | (lo - 0xDC00));
                                } else {
                                    // Unpaired; emit both as-is.
                                    appendUtf8(*out, cp);
                                    cp = lo;
                                }
                            }
                        }
                        appendUtf8(*out, cp);
                        break;
                    }
                    default: return false;
                }
            } else {
                out->push_back(c);
            }
        }
        return false; // unterminated
    }

    bool parseValue(Value* out) {
        skipWs();
        if (p >= end) return false;
        char c = *p;
        switch (c) {
            case '{': return parseObject(out);
            case '[': return parseArray(out);
            case '"': {
                out->type = Type::String;
                return parseString(&out->str);
            }
            case 't':
                if (end - p >= 4 && strncmp(p, "true", 4) == 0) {
                    p += 4; out->type = Type::Bool; out->b = true; return true;
                }
                return false;
            case 'f':
                if (end - p >= 5 && strncmp(p, "false", 5) == 0) {
                    p += 5; out->type = Type::Bool; out->b = false; return true;
                }
                return false;
            case 'n':
                if (end - p >= 4 && strncmp(p, "null", 4) == 0) {
                    p += 4; out->type = Type::Null; return true;
                }
                return false;
            default:
                return parseNumber(out);
        }
    }

    bool parseNumber(Value* out) {
        const char* start = p;
        if (p < end && (*p == '-' || *p == '+')) p++;
        bool any = false;
        while (p < end && ((*p >= '0' && *p <= '9') || *p == '.' ||
                           *p == 'e' || *p == 'E' || *p == '+' || *p == '-')) {
            p++; any = true;
        }
        if (!any) return false;
        std::string tok(start, p - start);
        char* eptr = nullptr;
        double v = strtod(tok.c_str(), &eptr);
        if (eptr == tok.c_str()) return false;
        out->type = Type::Number;
        out->num = v;
        return true;
    }

    bool parseArray(Value* out) {
        if (p >= end || *p != '[') return false;
        p++;
        out->type = Type::Array;
        skipWs();
        if (p < end && *p == ']') { p++; return true; }
        while (true) {
            Value elem;
            if (!parseValue(&elem)) return false;
            out->arr.push_back(std::move(elem));
            skipWs();
            if (p >= end) return false;
            if (*p == ',') { p++; continue; }
            if (*p == ']') { p++; return true; }
            return false;
        }
    }

    bool parseObject(Value* out) {
        if (p >= end || *p != '{') return false;
        p++;
        out->type = Type::Object;
        skipWs();
        if (p < end && *p == '}') { p++; return true; }
        while (true) {
            skipWs();
            std::string key;
            if (!parseString(&key)) return false;
            skipWs();
            if (p >= end || *p != ':') return false;
            p++;
            Value val;
            if (!parseValue(&val)) return false;
            out->obj.emplace_back(std::move(key), std::move(val));
            skipWs();
            if (p >= end) return false;
            if (*p == ',') { p++; continue; }
            if (*p == '}') { p++; return true; }
            return false;
        }
    }
};

void escapeTo(std::string& out, const std::string& s) {
    out.push_back('"');
    for (unsigned char c : s) {
        switch (c) {
            case '"':  out += "\\\""; break;
            case '\\': out += "\\\\"; break;
            case '\b': out += "\\b";  break;
            case '\f': out += "\\f";  break;
            case '\n': out += "\\n";  break;
            case '\r': out += "\\r";  break;
            case '\t': out += "\\t";  break;
            default:
                if (c < 0x20) {
                    char buf[8];
                    snprintf(buf, sizeof(buf), "\\u%04x", c);
                    out += buf;
                } else {
                    out.push_back((char)c); // pass UTF-8 bytes through unescaped
                }
        }
    }
    out.push_back('"');
}

void numberTo(std::string& out, double n) {
    // Emit integers without a trailing ".0", everything else with %g.
    if (n == (double)(long long)n && n < 1e15 && n > -1e15) {
        char buf[32];
        snprintf(buf, sizeof(buf), "%lld", (long long)n);
        out += buf;
    } else {
        char buf[32];
        snprintf(buf, sizeof(buf), "%.6g", n);
        out += buf;
    }
}

void serializeTo(std::string& out, const Value& v, bool pretty, int depth) {
    auto indent = [&](int d) {
        if (pretty) for (int i = 0; i < d; i++) out += "  ";
    };
    switch (v.type) {
        case Type::Null:   out += "null"; break;
        case Type::Bool:   out += v.b ? "true" : "false"; break;
        case Type::Number: numberTo(out, v.num); break;
        case Type::String: escapeTo(out, v.str); break;
        case Type::Array:
            if (v.arr.empty()) { out += "[]"; break; }
            out.push_back('[');
            if (pretty) out.push_back('\n');
            for (size_t i = 0; i < v.arr.size(); i++) {
                indent(depth + 1);
                serializeTo(out, v.arr[i], pretty, depth + 1);
                if (i + 1 < v.arr.size()) out.push_back(',');
                if (pretty) out.push_back('\n');
            }
            indent(depth);
            out.push_back(']');
            break;
        case Type::Object:
            if (v.obj.empty()) { out += "{}"; break; }
            out.push_back('{');
            if (pretty) out.push_back('\n');
            for (size_t i = 0; i < v.obj.size(); i++) {
                indent(depth + 1);
                escapeTo(out, v.obj[i].first);
                out += pretty ? ": " : ":";
                serializeTo(out, v.obj[i].second, pretty, depth + 1);
                if (i + 1 < v.obj.size()) out.push_back(',');
                if (pretty) out.push_back('\n');
            }
            indent(depth);
            out.push_back('}');
            break;
    }
}

} // namespace

bool parse(const std::string& text, Value* out) {
    if (!out) return false;
    *out = Value();
    Parser parser(text);
    if (!parser.parseValue(out)) return false;
    // Allow trailing whitespace only.
    parser.skipWs();
    return parser.p >= parser.end;
}

std::string serialize(const Value& v, bool pretty) {
    std::string out;
    serializeTo(out, v, pretty, 0);
    if (pretty) out.push_back('\n');
    return out;
}

} // namespace njson
} // namespace android
