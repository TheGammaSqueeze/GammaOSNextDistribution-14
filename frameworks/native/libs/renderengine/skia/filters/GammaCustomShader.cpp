#undef LOG_TAG
#define LOG_TAG "RenderEngine"

#include "GammaCustomShader.h"

#include <SkCanvas.h>
#include <SkColor.h>
#include <SkColorSpace.h>
#include <SkData.h>
#include <SkImage.h>
#include <SkMatrix.h>
#include <SkPaint.h>
#include <SkRuntimeEffect.h>
#include <SkSamplingOptions.h>
#include <SkStream.h>
#include <android-base/properties.h>

#include <algorithm>
#include <chrono>
#include <cmath>
#include <cstring>
#include <fstream>
#include <mutex>
// regex removed — all patterns replaced with manual string ops for ARM performance
#include <set>
#include <sstream>
#include <string>
#include <unordered_map>
#include <unordered_set>
#include <vector>

#include <dirent.h>
#include <sys/stat.h>
#include <log/log.h>

#include "../ColorSpaces.h"
#include "../debug/SkiaCapture.h"

namespace android {
namespace renderengine {
namespace skia {

using android::base::GetBoolProperty;
using android::base::GetProperty;

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

static bool fileExists(const std::string& path) {
    struct stat st;
    return stat(path.c_str(), &st) == 0 && S_ISREG(st.st_mode);
}

static std::string readFileToString(const std::string& path) {
    std::ifstream f(path, std::ios::in | std::ios::binary);
    if (!f.is_open()) return "";
    std::ostringstream ss;
    ss << f.rdbuf();
    return ss.str();
}

static std::string dirName(const std::string& path) {
    auto pos = path.find_last_of('/');
    if (pos == std::string::npos) return ".";
    return path.substr(0, pos);
}

// SurfaceFlinger cannot access /sdcard or /storage/emulated/0 through FUSE.
// Translate to /data/media/0 which is the backing store and accessible to system UID.
static std::string resolveStoragePath(const std::string& path) {
    static const std::pair<std::string, std::string> prefixes[] = {
        {"/sdcard/", "/data/media/0/"},
        {"/storage/emulated/0/", "/data/media/0/"},
    };
    for (const auto& [from, to] : prefixes) {
        if (path.compare(0, from.size(), from) == 0) {
            return to + path.substr(from.size());
        }
    }
    return path;
}

static std::string resolvePath(const std::string& base, const std::string& rel) {
    if (!rel.empty() && rel[0] == '/') return resolveStoragePath(rel);
    return resolveStoragePath(base + "/" + rel);
}

static std::string trim(const std::string& s) {
    size_t start = s.find_first_not_of(" \t\r\n");
    if (start == std::string::npos) return "";
    size_t end = s.find_last_not_of(" \t\r\n");
    return s.substr(start, end - start + 1);
}

static std::string unquote(const std::string& s) {
    std::string t = trim(s);
    if (t.size() >= 2 && t.front() == '"' && t.back() == '"') {
        return t.substr(1, t.size() - 2);
    }
    return t;
}

// ---------------------------------------------------------------------------
// .slangp preset parser
// ---------------------------------------------------------------------------

bool parseSlangPreset(const std::string& path, SlangPreset& out) {
    out = SlangPreset{};
    out.presetPath = path;
    out.presetDir = dirName(path);

    std::string content = readFileToString(path);
    if (content.empty()) return false;

    // Simple INI parser: key = value lines, # comments
    std::unordered_map<std::string, std::string> kv;
    std::istringstream stream(content);
    std::string line;
    while (std::getline(stream, line)) {
        line = trim(line);
        if (line.empty() || line[0] == '#') continue;
        auto eq = line.find('=');
        if (eq == std::string::npos) continue;
        std::string key = trim(line.substr(0, eq));
        std::string val = trim(line.substr(eq + 1));
        // Strip inline comments (but not inside quotes)
        if (val.front() != '"') {
            auto hashPos = val.find('#');
            if (hashPos != std::string::npos) {
                val = trim(val.substr(0, hashPos));
            }
        }
        val = unquote(val);
        kv[key] = val;
    }

    // Number of passes
    auto itShaders = kv.find("shaders");
    if (itShaders == kv.end()) return false;
    int numPasses = std::max(0, std::min(16, atoi(itShaders->second.c_str())));
    if (numPasses == 0) return false;

    // Feedback pass
    auto itFb = kv.find("feedback_pass");
    if (itFb != kv.end()) out.feedbackPass = atoi(itFb->second.c_str());

    // Parse passes
    for (int i = 0; i < numPasses; i++) {
        SlangPass pass;
        std::string idx = std::to_string(i);

        auto itPath = kv.find("shader" + idx);
        if (itPath == kv.end()) return false;
        pass.shaderPath = resolvePath(out.presetDir, itPath->second);

        auto it = kv.find("alias" + idx);
        if (it != kv.end()) pass.alias = it->second;

        it = kv.find("filter_linear" + idx);
        if (it != kv.end()) pass.filterLinear = (it->second == "true" || it->second == "1");

        it = kv.find("srgb_framebuffer" + idx);
        if (it != kv.end()) pass.srgbFbo = (it->second == "true" || it->second == "1");

        it = kv.find("float_framebuffer" + idx);
        if (it != kv.end()) pass.floatFbo = (it->second == "true" || it->second == "1");

        it = kv.find("mipmap_input" + idx);
        if (it != kv.end()) pass.mipmap = (it->second == "true" || it->second == "1");

        it = kv.find("frame_count_mod" + idx);
        if (it != kv.end()) pass.frameCountMod = atoi(it->second.c_str());

        // Scaling
        auto parseScaleType = [](const std::string& s) -> int {
            if (s == "source") return 0;
            if (s == "absolute") return 1;
            if (s == "viewport") return 2;
            return 0;
        };

        // scale_type (applies to both x and y)
        it = kv.find("scale_type" + idx);
        if (it != kv.end()) {
            pass.scaleTypeX = pass.scaleTypeY = parseScaleType(it->second);
        }
        it = kv.find("scale_type_x" + idx);
        if (it != kv.end()) pass.scaleTypeX = parseScaleType(it->second);
        it = kv.find("scale_type_y" + idx);
        if (it != kv.end()) pass.scaleTypeY = parseScaleType(it->second);

        // scale (applies to both x and y)
        it = kv.find("scale" + idx);
        if (it != kv.end()) {
            float s = atof(it->second.c_str());
            if (pass.scaleTypeX == 1) pass.absX = (int)s;
            else pass.scaleX = s;
            if (pass.scaleTypeY == 1) pass.absY = (int)s;
            else pass.scaleY = s;
        }
        it = kv.find("scale_x" + idx);
        if (it != kv.end()) {
            float s = atof(it->second.c_str());
            if (pass.scaleTypeX == 1) pass.absX = (int)s;
            else pass.scaleX = s;
        }
        it = kv.find("scale_y" + idx);
        if (it != kv.end()) {
            float s = atof(it->second.c_str());
            if (pass.scaleTypeY == 1) pass.absY = (int)s;
            else pass.scaleY = s;
        }

        out.passes.push_back(std::move(pass));
    }

    // Parse LUT textures
    auto itTex = kv.find("textures");
    if (itTex != kv.end()) {
        // semicolon-separated list of texture IDs
        std::istringstream texStream(itTex->second);
        std::string texId;
        while (std::getline(texStream, texId, ';')) {
            texId = trim(texId);
            if (texId.empty()) continue;

            SlangLut lut;
            lut.id = texId;

            auto itTex = kv.find(texId);
            if (itTex != kv.end()) {
                lut.path = resolvePath(out.presetDir, itTex->second);
            }

            auto itLin = kv.find(texId + "_linear");
            if (itLin != kv.end()) lut.filterLinear = (itLin->second == "true" || itLin->second == "1");

            auto itMip = kv.find(texId + "_mipmap");
            if (itMip != kv.end()) lut.mipmap = (itMip->second == "true" || itMip->second == "1");

            out.luts.push_back(std::move(lut));
        }
    }

    // Parse parameters: first extract #pragma parameter from .slang files
    // (done during loadAndCompile), then load overrides from preset
    // We defer parameter extraction to loadAndCompilePreset() since we need
    // to read the .slang files for that.
    // Store raw kv for parameter override lookup later.
    // We'll do a second pass after parameter extraction.

    out.valid = true;
    return true;
}

// ---------------------------------------------------------------------------
// #include resolution
// ---------------------------------------------------------------------------

static std::string resolveIncludes(const std::string& content, const std::string& baseDir,
                                    int depth = 0) {
    if (depth > 10) return content; // prevent infinite recursion
    std::ostringstream result;
    std::istringstream stream(content);
    std::string line;
    while (std::getline(stream, line)) {
        std::string trimmed = trim(line);
        if (trimmed.find("#include") == 0) {
            size_t q1 = trimmed.find('"');
            size_t q2 = (q1 != std::string::npos) ? trimmed.find('"', q1 + 1) : std::string::npos;
            if (q1 != std::string::npos && q2 != std::string::npos) {
                std::string incFile = trimmed.substr(q1 + 1, q2 - q1 - 1);
                std::string incPath = baseDir + "/" + incFile;
                std::string incContent = readFileToString(incPath);
                if (!incContent.empty()) {
                    result << resolveIncludes(incContent, dirName(incPath), depth + 1) << "\n";
                }
            }
            continue;
        }
        result << line << "\n";
    }
    return result.str();
}

// ---------------------------------------------------------------------------
// Vertex shader varying extraction
// ---------------------------------------------------------------------------

static std::unordered_map<std::string, std::string> extractVertexVaryings(const std::string& path) {
    std::unordered_map<std::string, std::string> result;
    std::string rawContent = readFileToString(path);
    if (rawContent.empty()) return result;
    std::string content = resolveIncludes(rawContent, dirName(path));

    std::istringstream stream(content);
    std::string line;
    bool inVertex = false;
    bool inMain = false;
    int braceDepth = 0;

    while (std::getline(stream, line)) {
        std::string trimmed = trim(line);
        if (trimmed.find("#pragma stage") == 0) {
            if (trimmed.find("vertex") != std::string::npos) inVertex = true;
            else inVertex = false;
            continue;
        }
        if (!inVertex) continue;
        if (!inMain && trimmed.find("void main()") != std::string::npos) {
            inMain = true;
            braceDepth = 0;
            for (char c : trimmed) { if (c == '{') braceDepth++; if (c == '}') braceDepth--; }
            continue;
        }
        if (!inMain) continue;
        for (char c : trimmed) { if (c == '{') braceDepth++; if (c == '}') braceDepth--; }
        if (braceDepth <= 0) { inMain = false; continue; }
        // Skip gl_Position
        if (trimmed.find("gl_Position") != std::string::npos) continue;
        // Look for "varName = EXPR;" or "varName *= EXPR;" etc.
        size_t eq = trimmed.find('=');
        if (eq == std::string::npos || eq == 0) continue;
        if (trimmed[eq-1] == '!' || trimmed[eq-1] == '<' || trimmed[eq-1] == '>') continue;
        // Only simple assignment (not *=, +=, etc.)
        if (eq > 0 && (trimmed[eq-1] == '*' || trimmed[eq-1] == '+' || trimmed[eq-1] == '-' || trimmed[eq-1] == '/')) continue;
        if (eq + 1 < trimmed.size() && trimmed[eq+1] == '=') continue; // ==
        std::string lhs = trimmed.substr(0, eq);
        while (!lhs.empty() && (lhs.back() == ' ' || lhs.back() == '\t')) lhs.pop_back();
        // Extract just the variable name
        size_t sp = lhs.rfind(' ');
        if (sp == std::string::npos) sp = lhs.rfind('\t');
        std::string varName = (sp != std::string::npos) ? lhs.substr(sp + 1) : lhs;
        std::string rhs = trimmed.substr(eq + 1);
        while (!rhs.empty() && (rhs.front() == ' ' || rhs.front() == '\t')) rhs.erase(0, 1);
        if (!rhs.empty() && rhs.back() == ';') rhs.pop_back();
        while (!rhs.empty() && (rhs.back() == ' ' || rhs.back() == '\t')) rhs.pop_back();
        if (!varName.empty() && !rhs.empty()) {
            result[varName] = rhs;
        }
    }
    return result;
}

// ---------------------------------------------------------------------------
// .slang fragment extractor
// ---------------------------------------------------------------------------

std::string extractSlangFragment(const std::string& path) {
    std::string rawContent = readFileToString(path);
    if (rawContent.empty()) return "";

    // Resolve #include directives
    std::string content = resolveIncludes(rawContent, dirName(path));

    // Find #pragma stage fragment, take everything after it until
    // #pragma stage vertex or EOF.
    // Also include shared code (before first #pragma stage) which may
    // contain #define constants and helper function declarations.

    if (content.find("#pragma stage") == std::string::npos) {
        // No stage pragmas - treat entire file as fragment shader
        return content;
    }

    std::istringstream stream(content);
    std::string line;
    bool inFragment = false;
    bool beforeFirstStage = true;
    std::ostringstream shared;
    std::ostringstream frag;

    while (std::getline(stream, line)) {
        std::string trimmed = trim(line);
        if (trimmed.find("#pragma stage") == 0) {
            beforeFirstStage = false;
            if (trimmed.find("fragment") != std::string::npos) {
                inFragment = true;
            } else {
                inFragment = false;
            }
            continue;
        }
        if (beforeFirstStage) {
            shared << line << "\n";
        } else if (inFragment) {
            frag << line << "\n";
        }
    }

    return shared.str() + frag.str();
}

// Extract #pragma parameter declarations from a .slang file
static std::vector<SlangParam> extractPragmaParams(const std::string& content) {
    std::vector<SlangParam> params;
    std::istringstream stream(content);
    std::string line;
    while (std::getline(stream, line)) {
        std::string trimmed = trim(line);
        if (trimmed.find("#pragma parameter") != 0) continue;

        // Format: #pragma parameter ID "Description" initial min max [step]
        // Parse after "#pragma parameter "
        std::string rest = trimmed.substr(strlen("#pragma parameter"));
        rest = trim(rest);

        SlangParam p;
        // Extract ID (first token)
        size_t sp = rest.find_first_of(" \t");
        if (sp == std::string::npos) continue;
        p.id = rest.substr(0, sp);
        // Skip parameters with hyphens (UI spacers, never referenced in shader code)
        if (p.id.find('-') != std::string::npos) continue;
        rest = trim(rest.substr(sp));

        // Extract description in quotes
        if (rest.empty() || rest[0] != '"') continue;
        size_t closeQuote = rest.find('"', 1);
        if (closeQuote == std::string::npos) continue;
        p.desc = rest.substr(1, closeQuote - 1);
        rest = trim(rest.substr(closeQuote + 1));

        // Parse initial, min, max, [step]
        std::istringstream nums(rest);
        if (!(nums >> p.initial)) continue;
        if (!(nums >> p.minimum)) continue;
        if (!(nums >> p.maximum)) continue;
        if (!(nums >> p.step)) {
            p.step = (p.maximum - p.minimum) * 0.1f;
            if (p.step <= 0.0f) p.step = 0.01f;
        }
        p.current = p.initial;
        params.push_back(std::move(p));
    }
    return params;
}

// ---------------------------------------------------------------------------
// Transpiler utilities
// ---------------------------------------------------------------------------

// Word-boundary-safe string replacement
static void replaceAllWord(std::string& s, const std::string& from, const std::string& to) {
    size_t pos = 0;
    while ((pos = s.find(from, pos)) != std::string::npos) {
        bool leftOk = (pos == 0 || !(isalnum(s[pos-1]) || s[pos-1] == '_'));
        size_t end = pos + from.size();
        bool rightOk = (end >= s.size() || !(isalnum(s[end]) || s[end] == '_'));
        if (leftOk && rightOk) {
            s.replace(pos, from.size(), to);
            pos += to.size();
        } else {
            pos++;
        }
    }
}

// Strip "prefix." from identifiers: "params.foo" → "foo", "global.bar" → "bar"
static void stripDotPrefix(std::string& code, const std::string& prefix) {
    std::string needle = prefix + ".";
    size_t pos = 0;
    while ((pos = code.find(needle, pos)) != std::string::npos) {
        // Check left word boundary
        bool leftOk = (pos == 0 || !(isalnum(code[pos-1]) || code[pos-1] == '_'));
        if (!leftOk) { pos++; continue; }
        // Check that char after dot is a word char (identifier start)
        size_t afterDot = pos + needle.size();
        if (afterDot >= code.size() || !(isalpha(code[afterDot]) || code[afterDot] == '_')) {
            pos++; continue;
        }
        // Erase the prefix including dot
        code.erase(pos, needle.size());
        // Don't advance — rescan from same position
    }
}

// Replace a word followed by trailing digits with a replacement string.
// e.g., replaceWordWithTrailingDigits(code, "PassFeedback", "src.eval(_coord)")
// matches PassFeedback0, PassFeedback12, etc.
static void replaceWordWithTrailingDigits(std::string& code, const std::string& word,
                                           const std::string& replacement) {
    size_t pos = 0;
    while ((pos = code.find(word, pos)) != std::string::npos) {
        bool leftOk = (pos == 0 || !(isalnum(code[pos-1]) || code[pos-1] == '_'));
        if (!leftOk) { pos++; continue; }
        size_t end = pos + word.size();
        // Consume trailing digits
        while (end < code.size() && isdigit(code[end])) end++;
        // Check right boundary
        bool rightOk = (end >= code.size() || !(isalnum(code[end]) || code[end] == '_'));
        if (!rightOk) { pos++; continue; }
        code.replace(pos, end - pos, replacement);
        pos += replacement.size();
    }
}

// Check if a string starts with one of the given type keywords followed by whitespace
static bool startsWithType(const std::string& s) {
    static const char* types[] = {
        "float4x4", "float3x3", "float2x2",
        "float4", "float3", "float2", "float",
        "half4", "half3", "half2", "half",
        "int4", "int3", "int2", "int",
        "void", "bool", nullptr
    };
    for (const char** t = types; *t; ++t) {
        size_t len = strlen(*t);
        if (s.size() > len && s.compare(0, len, *t) == 0 &&
            (s[len] == ' ' || s[len] == '\t')) {
            return true;
        }
    }
    return false;
}

// Parse "type name = expr;" from a trimmed line. Returns false if not a match.
static bool parseVarDecl(const std::string& trimmed,
                          std::string& outType, std::string& outName, std::string& outExpr) {
    static const char* types[] = {
        "float4x4", "float3x3", "float2x2",
        "float4", "float3", "float2", "float",
        "half4", "half3", "half2", "half",
        "int4", "int3", "int2", "int",
        "bool", nullptr
    };
    for (const char** t = types; *t; ++t) {
        size_t tlen = strlen(*t);
        if (trimmed.size() <= tlen) continue;
        if (trimmed.compare(0, tlen, *t) != 0) continue;
        if (trimmed[tlen] != ' ' && trimmed[tlen] != '\t') continue;
        // Found type, now extract name
        size_t nameStart = tlen + 1;
        while (nameStart < trimmed.size() && (trimmed[nameStart] == ' ' || trimmed[nameStart] == '\t'))
            nameStart++;
        if (nameStart >= trimmed.size() || !(isalpha(trimmed[nameStart]) || trimmed[nameStart] == '_'))
            continue;
        size_t nameEnd = nameStart;
        while (nameEnd < trimmed.size() && (isalnum(trimmed[nameEnd]) || trimmed[nameEnd] == '_'))
            nameEnd++;
        // Skip whitespace after name
        size_t eqPos = nameEnd;
        while (eqPos < trimmed.size() && (trimmed[eqPos] == ' ' || trimmed[eqPos] == '\t'))
            eqPos++;
        if (eqPos >= trimmed.size() || trimmed[eqPos] != '=') continue;
        // Must end with ;
        if (trimmed.back() != ';') continue;
        outType = std::string(*t);
        outName = trimmed.substr(nameStart, nameEnd - nameStart);
        outExpr = trim(trimmed.substr(eqPos + 1, trimmed.size() - eqPos - 2)); // strip = and ;
        return true;
    }
    return false;
}

// Remove "sampler2D name" from a parameter list (handles leading/trailing commas)
static void removeSampler2DParams(std::string& code) {
    // Pattern 1: "sampler2D word, " at start of params
    {
        size_t pos = 0;
        while ((pos = code.find("sampler2D", pos)) != std::string::npos) {
            if (pos > 0 && (isalnum(code[pos-1]) || code[pos-1] == '_')) { pos++; continue; }
            size_t end = pos + 9; // after "sampler2D"
            // Skip whitespace
            while (end < code.size() && (code[end] == ' ' || code[end] == '\t')) end++;
            // Skip identifier
            if (end < code.size() && (isalpha(code[end]) || code[end] == '_')) {
                while (end < code.size() && (isalnum(code[end]) || code[end] == '_')) end++;
            }
            // Skip optional trailing comma and whitespace
            size_t afterId = end;
            while (afterId < code.size() && (code[afterId] == ' ' || code[afterId] == '\t')) afterId++;
            if (afterId < code.size() && code[afterId] == ',') {
                afterId++;
                while (afterId < code.size() && (code[afterId] == ' ' || code[afterId] == '\t')) afterId++;
                code.erase(pos, afterId - pos);
            } else {
                // Check for leading comma: ", sampler2D name"
                size_t start = pos;
                if (start > 0) {
                    size_t commaCheck = start - 1;
                    while (commaCheck > 0 && (code[commaCheck] == ' ' || code[commaCheck] == '\t'))
                        commaCheck--;
                    if (code[commaCheck] == ',') {
                        code.erase(commaCheck, end - commaCheck);
                        pos = commaCheck;
                        continue;
                    }
                }
                // Sole param
                code.erase(pos, end - pos);
            }
        }
    }
}

// Find user-defined function names in helpers code (without regex)
static std::set<std::string> findFuncNames(const std::string& helpers) {
    std::set<std::string> funcNames;
    static const char* types[] = {
        "float4x4", "float3x3", "float2x2",
        "float4", "float3", "float2", "float",
        "half4", "half3", "half2", "half",
        "int4", "int3", "int2", "int",
        "void", "bool", nullptr
    };
    // Scan line by line
    std::istringstream stream(helpers);
    std::string line;
    while (std::getline(stream, line)) {
        std::string t = trim(line);
        if (t.empty()) continue;
        for (const char** tp = types; *tp; ++tp) {
            size_t tlen = strlen(*tp);
            if (t.size() <= tlen) continue;
            if (t.compare(0, tlen, *tp) != 0) continue;
            if (t[tlen] != ' ' && t[tlen] != '\t') continue;
            // Found type prefix, look for "name("
            size_t nameStart = tlen + 1;
            while (nameStart < t.size() && (t[nameStart] == ' ' || t[nameStart] == '\t'))
                nameStart++;
            if (nameStart >= t.size() || !(isalpha(t[nameStart]) || t[nameStart] == '_'))
                break;
            size_t nameEnd = nameStart;
            while (nameEnd < t.size() && (isalnum(t[nameEnd]) || t[nameEnd] == '_'))
                nameEnd++;
            // Check for ( after name (skip whitespace)
            size_t parenPos = nameEnd;
            while (parenPos < t.size() && (t[parenPos] == ' ' || t[parenPos] == '\t'))
                parenPos++;
            if (parenPos < t.size() && t[parenPos] == '(') {
                std::string name = t.substr(nameStart, nameEnd - nameStart);
                if (name != "main" && name != "if" && name != "for" && name != "while") {
                    funcNames.insert(name);
                }
            }
            break;
        }
    }
    return funcNames;
}

// Replace "textureSize(identifier, digit)" with "sourceSize" (manual, no regex)
static void replaceTextureSizeCalls(std::string& code) {
    const std::string fn = "textureSize";
    size_t pos = 0;
    while ((pos = code.find(fn, pos)) != std::string::npos) {
        if (pos > 0 && (isalnum(code[pos-1]) || code[pos-1] == '_')) { pos++; continue; }
        size_t after = pos + fn.size();
        while (after < code.size() && (code[after] == ' ' || code[after] == '\t')) after++;
        if (after >= code.size() || code[after] != '(') { pos = after; continue; }
        // Find matching )
        int depth = 1;
        size_t p = after + 1;
        while (p < code.size() && depth > 0) {
            if (code[p] == '(') depth++;
            else if (code[p] == ')') depth--;
            p++;
        }
        if (depth == 0) {
            code.replace(pos, p - pos, "sourceSize");
            pos += 10; // "sourceSize".size()
        } else {
            pos = after;
        }
    }
}

// Replace "return;" with "return half4(_fragColor);" (manual, no regex)
static void replaceBareReturns(std::string& code) {
    size_t pos = 0;
    while ((pos = code.find("return", pos)) != std::string::npos) {
        bool leftOk = (pos == 0 || !(isalnum(code[pos-1]) || code[pos-1] == '_'));
        if (!leftOk) { pos++; continue; }
        size_t after = pos + 6; // "return"
        // Check right word boundary
        if (after < code.size() && (isalnum(code[after]) || code[after] == '_')) { pos++; continue; }
        // Skip whitespace
        size_t ws = after;
        while (ws < code.size() && (code[ws] == ' ' || code[ws] == '\t')) ws++;
        if (ws < code.size() && code[ws] == ';') {
            std::string repl = "return half4(_fragColor);";
            code.replace(pos, ws + 1 - pos, repl);
            pos += repl.size();
        } else {
            pos = after;
        }
    }
}

// Replace "[N.0]" with "[N]" in array subscripts (manual, no regex)
static void fixFloatArraySubscripts(std::string& code) {
    size_t pos = 0;
    while ((pos = code.find("[", pos)) != std::string::npos) {
        size_t start = pos + 1;
        // Read digits
        size_t numEnd = start;
        while (numEnd < code.size() && isdigit(code[numEnd])) numEnd++;
        if (numEnd == start || numEnd + 2 >= code.size()) { pos = start; continue; }
        if (code[numEnd] == '.' && code[numEnd+1] == '0' && code[numEnd+2] == ']') {
            // Replace [N.0] with [N]
            code.erase(numEnd, 2); // remove ".0"
            pos = numEnd + 1;
        } else {
            pos = start;
        }
    }
}

// Remove unsized array declarations: "type name[];" → remove/comment out
static void removeUnsizedArrayDecls(std::string& code) {
    static const char* types[] = {
        "float4", "float3", "float2", "float",
        "int4", "int3", "int2", "int",
        "half4", "half3", "half2", "half",
        "bool", nullptr
    };
    // Work line by line
    std::istringstream stream(code);
    std::string line;
    std::ostringstream result;
    while (std::getline(stream, line)) {
        std::string t = trim(line);
        bool removed = false;
        for (const char** tp = types; *tp; ++tp) {
            size_t tlen = strlen(*tp);
            if (t.size() <= tlen) continue;
            if (t.compare(0, tlen, *tp) != 0) continue;
            if (t[tlen] != ' ' && t[tlen] != '\t') continue;
            // Found type, check for "name[];"
            size_t nameStart = tlen + 1;
            while (nameStart < t.size() && (t[nameStart] == ' ' || t[nameStart] == '\t'))
                nameStart++;
            size_t nameEnd = nameStart;
            while (nameEnd < t.size() && (isalnum(t[nameEnd]) || t[nameEnd] == '_'))
                nameEnd++;
            // Check for [] and ;
            size_t bp = nameEnd;
            while (bp < t.size() && (t[bp] == ' ' || t[bp] == '\t')) bp++;
            if (bp + 1 < t.size() && t[bp] == '[') {
                size_t cb = bp + 1;
                while (cb < t.size() && (t[cb] == ' ' || t[cb] == '\t')) cb++;
                if (cb < t.size() && t[cb] == ']') {
                    size_t sc = cb + 1;
                    while (sc < t.size() && (t[sc] == ' ' || t[sc] == '\t')) sc++;
                    if (sc < t.size() && t[sc] == ';') {
                        result << "/* unsized array removed */\n";
                        removed = true;
                        break;
                    }
                }
            }
            break;
        }
        if (!removed) {
            result << line << "\n";
        }
    }
    code = result.str();
}

// Check if all identifiers in a value are "const-allowed" (type constructors, literals)
static bool isConstExpression(const std::string& value,
                               const std::unordered_set<std::string>& definedNames) {
    static const std::unordered_set<std::string> constAllowed = {
        "float", "float2", "float3", "float4",
        "float2x2", "float3x3", "float4x4",
        "int", "int2", "int3", "int4",
        "half", "half2", "half3", "half4",
        "bool", "true", "false",
        "M_PI", "M_E"
    };
    size_t i = 0;
    while (i < value.size()) {
        if (isalpha(value[i]) || value[i] == '_') {
            size_t start = i;
            while (i < value.size() && (isalnum(value[i]) || value[i] == '_')) i++;
            std::string word = value.substr(start, i - start);
            if (constAllowed.find(word) == constAllowed.end() &&
                definedNames.find(word) == definedNames.end()) {
                return false;
            }
        } else {
            i++;
        }
    }
    return true;
}

// Find functions with sampler2D parameters (manual, no regex)
struct SamplerFunc { std::string name; int paramIndex; };
static std::vector<SamplerFunc> findSamplerFuncs(const std::string& code) {
    std::vector<SamplerFunc> result;
    // Scan for "sampler2D" in parameter lists of function definitions
    size_t pos = 0;
    while ((pos = code.find("sampler2D", pos)) != std::string::npos) {
        // Walk backwards to find the function name and return type
        // First, find the opening paren before this sampler2D
        size_t searchBack = pos;
        int parenDepth = 0;
        bool foundOpen = false;
        while (searchBack > 0) {
            searchBack--;
            if (code[searchBack] == ')') parenDepth++;
            else if (code[searchBack] == '(') {
                if (parenDepth == 0) { foundOpen = true; break; }
                parenDepth--;
            }
        }
        if (!foundOpen) { pos += 9; continue; }

        // Extract all params between ( and matching )
        size_t openParen = searchBack;
        int depth = 1;
        size_t p = openParen + 1;
        while (p < code.size() && depth > 0) {
            if (code[p] == '(') depth++;
            else if (code[p] == ')') depth--;
            p++;
        }
        if (depth != 0) { pos += 9; continue; }
        size_t closeParen = p - 1;

        // Get function name (word before the open paren)
        size_t nameEnd = openParen;
        while (nameEnd > 0 && (code[nameEnd-1] == ' ' || code[nameEnd-1] == '\t')) nameEnd--;
        if (nameEnd == 0) { pos += 9; continue; }
        size_t nameStart = nameEnd;
        while (nameStart > 0 && (isalnum(code[nameStart-1]) || code[nameStart-1] == '_')) nameStart--;
        if (nameStart == nameEnd) { pos += 9; continue; }
        std::string funcName = code.substr(nameStart, nameEnd - nameStart);

        // Parse params to find which index has sampler2D
        std::string paramStr = code.substr(openParen + 1, closeParen - openParen - 1);
        std::istringstream ps(paramStr);
        std::string param;
        int idx = 0;
        while (std::getline(ps, param, ',')) {
            std::string tp = trim(param);
            if (tp.find("sampler2D") == 0 &&
                (tp.size() == 9 || tp[9] == ' ' || tp[9] == '\t')) {
                result.push_back({funcName, idx});
            }
            idx++;
        }
        pos = closeParen;
    }
    return result;
}

// Parse array initializer: "type name[N] = {a,b,...};" or "type name[] = {a,b,...};"
// Returns true if found, fills in components. Manual replacement for arrInit regex.
static bool parseArrayBraceInit(const std::string& code, size_t searchFrom,
                                  size_t& matchStart, size_t& matchEnd,
                                  std::string& type, std::string& varName,
                                  std::string& sizeStr, std::vector<std::string>& elems,
                                  int& secondDim) {
    static const char* types[] = {
        "float4", "float3", "float2", "float",
        "int4", "int3", "int2", "int",
        "half4", "half3", "half2", "half",
        "bool", nullptr
    };
    secondDim = 0;
    // Search for opening brace after "= {"
    size_t bracePos = code.find("= {", searchFrom);
    if (bracePos == std::string::npos) return false;

    // Walk backward from bracePos to find "type name[N]"
    // Skip whitespace before =
    size_t eqPos = bracePos;
    // Find the bracket part: name[N] or name[]
    size_t lineStart = code.rfind('\n', eqPos);
    lineStart = (lineStart == std::string::npos) ? 0 : lineStart + 1;
    std::string beforeEq = code.substr(lineStart, eqPos - lineStart);
    std::string bt = trim(beforeEq);

    // Try to match "type name[N]" or "type name[]" or "type name[N][M]"
    for (const char** tp = types; *tp; ++tp) {
        size_t tlen = strlen(*tp);
        if (bt.size() <= tlen) continue;
        if (bt.compare(0, tlen, *tp) != 0) continue;
        if (bt[tlen] != ' ' && bt[tlen] != '\t') continue;

        size_t nameStart = tlen + 1;
        while (nameStart < bt.size() && (bt[nameStart] == ' ' || bt[nameStart] == '\t'))
            nameStart++;
        size_t nameEnd = nameStart;
        while (nameEnd < bt.size() && (isalnum(bt[nameEnd]) || bt[nameEnd] == '_'))
            nameEnd++;
        if (nameEnd == nameStart) continue;

        // Check for [
        size_t bp = nameEnd;
        while (bp < bt.size() && (bt[bp] == ' ' || bt[bp] == '\t')) bp++;
        if (bp >= bt.size() || bt[bp] != '[') continue;
        bp++;
        size_t sizeStart = bp;
        while (bp < bt.size() && bt[bp] != ']') bp++;
        if (bp >= bt.size()) continue;

        type = std::string(*tp);
        varName = bt.substr(nameStart, nameEnd - nameStart);
        sizeStr = bt.substr(sizeStart, bp - sizeStart);

        // Check for second dimension [M]
        bp++; // past first ]
        while (bp < bt.size() && (bt[bp] == ' ' || bt[bp] == '\t')) bp++;
        if (bp < bt.size() && bt[bp] == '[') {
            bp++;
            size_t s2Start = bp;
            while (bp < bt.size() && bt[bp] != ']') bp++;
            if (bp < bt.size()) {
                std::string s2 = bt.substr(s2Start, bp - s2Start);
                secondDim = std::atoi(s2.c_str());
            }
        }

        matchStart = lineStart;

        // Now parse the brace-enclosed elements
        size_t braceOpen = bracePos + 2; // position of {
        int depth = 0;
        size_t closePos = std::string::npos;
        for (size_t i = braceOpen + 1; i < code.size(); i++) {
            if (code[i] == '{') depth++;
            else if (code[i] == '}') {
                if (depth == 0) { closePos = i; break; }
                depth--;
            }
        }
        if (closePos == std::string::npos) return false;

        std::string elemsStr = code.substr(braceOpen + 1, closePos - braceOpen - 1);

        // For 2D arrays, strip inner { } to flatten
        if (secondDim > 0) {
            std::string flat;
            for (char c : elemsStr) {
                if (c != '{' && c != '}') flat += c;
            }
            elemsStr = flat;
        }

        elems.clear();
        int d2 = 0;
        size_t eStart = 0;
        for (size_t i = 0; i <= elemsStr.size(); i++) {
            if (i == elemsStr.size() || (elemsStr[i] == ',' && d2 == 0)) {
                std::string e = trim(elemsStr.substr(eStart, i - eStart));
                if (!e.empty()) elems.push_back(e);
                eStart = i + 1;
            } else if (elemsStr[i] == '(') d2++;
            else if (elemsStr[i] == ')') d2--;
        }

        matchEnd = closePos + 1;
        while (matchEnd < code.size() && (code[matchEnd] == ';' || code[matchEnd] == ' ')) matchEnd++;
        return true;
    }
    return false;
}

// Parse array constructor: "type name[N] = type[N](a,b,...);" — manual replacement for arrCtor regex
static bool parseArrayCtorInit(const std::string& code, size_t searchFrom,
                                 size_t& matchStart, size_t& matchEnd,
                                 std::string& baseType, std::string& varName,
                                 std::vector<std::string>& elems) {
    static const char* types[] = {
        "float4", "float3", "float2", "float",
        "int4", "int3", "int2", "int",
        "half4", "half3", "half2", "half",
        "bool", nullptr
    };
    // Look for pattern "= type[" after a "type name["
    size_t eqPos = code.find("= ", searchFrom);
    while (eqPos != std::string::npos) {
        // Check if what follows = is "type[N]("
        size_t afterEq = eqPos + 2;
        while (afterEq < code.size() && (code[afterEq] == ' ' || code[afterEq] == '\t')) afterEq++;

        bool foundType = false;
        std::string rhsType;
        for (const char** tp = types; *tp; ++tp) {
            size_t tlen = strlen(*tp);
            if (afterEq + tlen >= code.size()) continue;
            if (code.compare(afterEq, tlen, *tp) != 0) continue;
            // Check for [ after type
            size_t bp = afterEq + tlen;
            while (bp < code.size() && (code[bp] == ' ' || code[bp] == '\t')) bp++;
            if (bp < code.size() && code[bp] == '[') {
                foundType = true;
                rhsType = *tp;
                break;
            }
        }

        if (!foundType) {
            eqPos = code.find("= ", eqPos + 1);
            continue;
        }

        // Now walk backward from eqPos to find "type name[N]" or "const type name[N]"
        size_t lineStart = code.rfind('\n', eqPos);
        lineStart = (lineStart == std::string::npos) ? 0 : lineStart + 1;
        std::string beforeEq = trim(code.substr(lineStart, eqPos - lineStart));

        // Try to match "[const] type name[N]"
        std::string bt = beforeEq;
        bool isConst = false;
        if (bt.find("const ") == 0) {
            isConst = true;
            bt = trim(bt.substr(6));
        }

        bool matched = false;
        for (const char** tp = types; *tp; ++tp) {
            size_t tlen = strlen(*tp);
            if (bt.size() <= tlen) continue;
            if (bt.compare(0, tlen, *tp) != 0) continue;
            if (bt[tlen] != ' ' && bt[tlen] != '\t') continue;

            size_t nameStart = tlen + 1;
            while (nameStart < bt.size() && (bt[nameStart] == ' ' || bt[nameStart] == '\t'))
                nameStart++;
            size_t nameEnd = nameStart;
            while (nameEnd < bt.size() && (isalnum(bt[nameEnd]) || bt[nameEnd] == '_'))
                nameEnd++;
            size_t bp = nameEnd;
            while (bp < bt.size() && (bt[bp] == ' ' || bt[bp] == '\t')) bp++;
            if (bp >= bt.size() || bt[bp] != '[') continue;

            baseType = std::string(*tp);
            varName = bt.substr(nameStart, nameEnd - nameStart);
            matchStart = lineStart;
            matched = true;
            break;
        }

        if (!matched) {
            eqPos = code.find("= ", eqPos + 1);
            continue;
        }

        // Find the ( on the RHS after type[N]
        size_t openParen = code.find('(', afterEq);
        if (openParen == std::string::npos) {
            eqPos = code.find("= ", eqPos + 1);
            continue;
        }

        // Find matching )
        int pd = 1;
        size_t cp = openParen + 1;
        while (cp < code.size() && pd > 0) {
            if (code[cp] == '(') pd++;
            else if (code[cp] == ')') pd--;
            cp++;
        }
        if (pd != 0) {
            eqPos = code.find("= ", eqPos + 1);
            continue;
        }

        // Parse elements
        std::string elemsStr = code.substr(openParen + 1, cp - openParen - 2);
        elems.clear();
        int d2 = 0;
        size_t eStart = 0;
        for (size_t i = 0; i <= elemsStr.size(); i++) {
            if (i == elemsStr.size() || (elemsStr[i] == ',' && d2 == 0)) {
                std::string e = trim(elemsStr.substr(eStart, i - eStart));
                if (!e.empty()) elems.push_back(e);
                eStart = i + 1;
            } else if (elemsStr[i] == '(') d2++;
            else if (elemsStr[i] == ')') d2--;
        }

        matchEnd = cp;
        while (matchEnd < code.size() && (code[matchEnd] == ';' || code[matchEnd] == ' ')) matchEnd++;
        return true;
    }
    return false;
}

// Convert non-square matrix constructors before type names are changed,
// so we can distinguish mat4x3 (needs padding) from mat4 (already correct).
static void fixNonSquareMatrixCtors(std::string& code) {
    struct MatInfo { const char* name; const char* target; int numCols; int colSize; };
    static const MatInfo mats[] = {
        {"mat4x3", "float4x4", 4, 3},
        {"mat3x4", "float4x4", 3, 4},
        {"mat4x2", "float4x4", 4, 2},
        {"mat2x4", "float4x4", 2, 4},
        {"mat3x2", "float3x3", 3, 2},
        {"mat2x3", "float3x3", 2, 3},
    };
    for (const auto& mi : mats) {
        std::string needle = std::string(mi.name) + "(";
        size_t pos = 0;
        while ((pos = code.find(needle, pos)) != std::string::npos) {
            if (pos > 0 && (isalnum(code[pos-1]) || code[pos-1] == '_')) { pos++; continue; }
            size_t start = pos + needle.size();
            int depth = 1;
            size_t p = start;
            std::vector<std::string> args;
            size_t argStart = start;
            while (p < code.size() && depth > 0) {
                if (code[p] == '(') depth++;
                else if (code[p] == ')') {
                    depth--;
                    if (depth == 0) {
                        std::string a = trim(code.substr(argStart, p - argStart));
                        if (!a.empty()) args.push_back(a);
                    }
                } else if (code[p] == ',' && depth == 1) {
                    args.push_back(trim(code.substr(argStart, p - argStart)));
                    argStart = p + 1;
                }
                p++;
            }
            if (depth != 0) { pos++; continue; }

            // If arg count matches column count, each arg is a column vector that needs padding
            if ((int)args.size() == mi.numCols) {
                int targetDim = (std::string(mi.target) == "float4x4") ? 4 : 3;
                int pad = targetDim - mi.colSize;
                std::string padStr;
                for (int i = 0; i < pad; i++) padStr += ", 0.0";
                std::string colType = "float" + std::to_string(targetDim);

                std::string fixed = std::string(mi.target) + "(";
                for (int i = 0; i < mi.numCols; i++) {
                    if (i > 0) fixed += ", ";
                    fixed += colType + "(" + args[i] + padStr + ")";
                }
                // Pad missing columns with zero
                for (int i = mi.numCols; i < targetDim; i++) {
                    fixed += ", " + colType + "(0.0)";
                }
                fixed += ")";
                code.replace(pos, p - pos, fixed);
                pos += fixed.size();
            } else {
                pos = p;
            }
        }
    }
}

// Apply GLSL→SkSL type conversions
static void applyTypeConversions(std::string& code) {
    // Fix non-square matrix constructors BEFORE renaming types
    fixNonSquareMatrixCtors(code);

    replaceAllWord(code, "vec2", "float2");
    replaceAllWord(code, "vec3", "float3");
    replaceAllWord(code, "vec4", "float4");
    // Non-square matrices (not supported in SkSL) — constructors already fixed above
    replaceAllWord(code, "mat4x3", "float4x4");
    replaceAllWord(code, "mat3x4", "float4x4");
    replaceAllWord(code, "mat4x2", "float4x4");
    replaceAllWord(code, "mat2x4", "float4x4");
    replaceAllWord(code, "mat3x2", "float3x3");
    replaceAllWord(code, "mat2x3", "float3x3");
    // Square matrices
    replaceAllWord(code, "mat2", "float2x2");
    replaceAllWord(code, "mat3", "float3x3");
    replaceAllWord(code, "mat4", "float4x4");
    replaceAllWord(code, "ivec2", "int2");
    replaceAllWord(code, "ivec3", "int3");
    replaceAllWord(code, "ivec4", "int4");
    replaceAllWord(code, "bvec2", "bool2");
    replaceAllWord(code, "bvec3", "bool3");
    replaceAllWord(code, "bvec4", "bool4");
    // GLSL highp/mediump/lowp qualifiers
    replaceAllWord(code, "highp", "");
    replaceAllWord(code, "mediump", "");
    replaceAllWord(code, "lowp", "");
    // C-style integer types
    replaceAllWord(code, "uint32_t", "int");
    replaceAllWord(code, "uint", "int");
    replaceAllWord(code, "uvec2", "int2");
    replaceAllWord(code, "uvec3", "int3");
    replaceAllWord(code, "uvec4", "int4");
    // Sampler2D handling is done separately via fixSampler2DFunctions() on helpers+body together

    // Strip GLSL float suffix 'f' from numeric literals (e.g. 0.5f → 0.5, 1f → 1)
    // SkSL doesn't support the 'f' suffix
    {
        size_t pos = 0;
        while (pos < code.size()) {
            if (code[pos] == 'f' || code[pos] == 'F') {
                // Check if preceded by a digit or '.'
                if (pos > 0 && (isdigit(code[pos-1]) || code[pos-1] == '.')) {
                    // Check if followed by a non-word character (not part of an identifier)
                    size_t after = pos + 1;
                    if (after >= code.size() || !(isalnum(code[after]) || code[after] == '_')) {
                        code.erase(pos, 1);
                        continue; // don't advance, re-check same position
                    }
                }
            }
            pos++;
        }
    }
}

// Remove sampler2D params from function definitions and fix call sites across helpers and body
static void fixSampler2DFunctions(std::string& helpers, std::string& body) {
    // Find function definitions with sampler2D params in helpers
    auto samplerFuncs = findSamplerFuncs(helpers);
    if (samplerFuncs.empty()) return;

    // Remove sampler2D params from definitions
    removeSampler2DParams(helpers);

    // Remove corresponding arguments from call sites in BOTH helpers and body
    auto fixCallSites = [&](std::string& code) {
        for (const auto& sf : samplerFuncs) {
            size_t pos = 0;
            while ((pos = code.find(sf.name, pos)) != std::string::npos) {
                if (pos > 0 && (isalnum(code[pos-1]) || code[pos-1] == '_')) { pos++; continue; }
                size_t after = pos + sf.name.size();
                while (after < code.size() && (code[after] == ' ' || code[after] == '\t')) after++;
                if (after >= code.size() || code[after] != '(') { pos = after; continue; }

                // Check if this is a definition (preceded by a type keyword) — skip
                size_t ls = code.rfind('\n', pos);
                ls = (ls == std::string::npos) ? 0 : ls + 1;
                std::string before = code.substr(ls, pos - ls);
                size_t bfs = before.find_first_not_of(" \t");
                bool isDef = false;
                if (bfs != std::string::npos) {
                    static const char* typeKws[] = {
                        // SkSL types
                        "float4x4", "float3x3", "float2x2",
                        "float4", "float3", "float2", "float",
                        "half4", "half3", "half2", "half",
                        "int4", "int3", "int2", "int",
                        "void", "bool",
                        // GLSL types (before type conversion)
                        "vec4", "vec3", "vec2",
                        "ivec4", "ivec3", "ivec2",
                        "mat4", "mat3", "mat2",
                        "mat4x3", "mat3x4", "mat4x2", "mat2x4", "mat3x2", "mat2x3"
                    };
                    // Extract the last word from the text before the function name
                    // (handles "const vec4", "static inline void", etc.)
                    std::string bt = trim(before);
                    auto lastSpace = bt.rfind(' ');
                    if (lastSpace != std::string::npos) bt = bt.substr(lastSpace + 1);
                    for (const char* tk : typeKws) {
                        if (bt == tk) { isDef = true; break; }
                    }
                }
                if (isDef) { pos = after; continue; }

                // Find all args with proper paren matching
                int depth = 1;
                size_t p = after + 1;
                std::vector<std::pair<size_t,size_t>> argRanges;
                size_t argStart = p;
                while (p < code.size() && depth > 0) {
                    if (code[p] == '(') depth++;
                    else if (code[p] == ')') {
                        depth--;
                        if (depth == 0) {
                            argRanges.push_back({argStart, p});
                        }
                    } else if (code[p] == ',' && depth == 1) {
                        argRanges.push_back({argStart, p});
                        argStart = p + 1;
                    }
                    p++;
                }
                if (depth != 0 || sf.paramIndex >= (int)argRanges.size()) { pos = p; continue; }

                // Remove the argument at paramIndex
                auto [aStart, aEnd] = argRanges[sf.paramIndex];
                if (sf.paramIndex == 0 && argRanges.size() > 1) {
                    size_t commaEnd = aEnd + 1;
                    while (commaEnd < code.size() && code[commaEnd] == ' ') commaEnd++;
                    code.erase(aStart, commaEnd - aStart);
                } else if (sf.paramIndex > 0) {
                    size_t commaStart = aStart;
                    if (commaStart > 0) {
                        commaStart--;
                        while (commaStart > 0 && code[commaStart] == ' ') commaStart--;
                        if (code[commaStart] == ',') {
                            code.erase(commaStart, aEnd - commaStart);
                        }
                    }
                } else {
                    if (aEnd > aStart) {
                        code.erase(aStart, aEnd - aStart);
                    }
                }
                pos += sf.name.size();
            }
        }
    };
    fixCallSites(helpers);
    fixCallSites(body);
}

// Apply semantic conversions (params.XXX→XXX, sizes, texture sampling, etc.)
static void applySemanticConversions(std::string& code, const std::vector<SlangLut>& luts) {
    // Rename local variables named "src" to avoid collision with the src shader uniform
    // Must run BEFORE texture conversion (which creates src.eval references)
    {
        size_t pos = 0;
        while ((pos = code.find("src", pos)) != std::string::npos) {
            bool leftOk = (pos == 0 || !(isalnum(code[pos-1]) || code[pos-1] == '_'));
            size_t end = pos + 3;
            bool rightOk = (end >= code.size() || !(isalnum(code[end]) || code[end] == '_'));
            if (leftOk && rightOk) {
                size_t lineStart = code.rfind('\n', pos);
                lineStart = (lineStart == std::string::npos) ? 0 : lineStart + 1;
                std::string before = trim(code.substr(lineStart, pos - lineStart));
                static const char* types[] = {"float4","float3","float2","float","half4","half3","half2","half","int4","int3","int2","int",nullptr};
                bool isDecl = false;
                for (const char** t = types; *t; ++t) {
                    if (before == *t) { isDecl = true; break; }
                }
                if (isDecl) {
                    replaceAllWord(code, "src", "_local_src");
                    break;
                }
            }
            pos = end;
        }
    }

    // Convert params.XXX / global.XXX / registers.XXX → XXX (manual, no regex)
    stripDotPrefix(code, "params");
    stripDotPrefix(code, "param");
    stripDotPrefix(code, "global");
    stripDotPrefix(code, "registers");

    // Size variable mappings (specific swizzles first, then generic)
    replaceAllWord(code, "SourceSize.xy", "sourceSize");
    replaceAllWord(code, "SourceSize.zw", "(1.0 / sourceSize)");
    replaceAllWord(code, "SourceSize.x", "sourceSize.x");
    replaceAllWord(code, "SourceSize.y", "sourceSize.y");
    replaceAllWord(code, "SourceSize.z", "(1.0 / sourceSize.x)");
    replaceAllWord(code, "SourceSize.w", "(1.0 / sourceSize.y)");
    replaceAllWord(code, "SourceSize", "float4(sourceSize, 1.0/sourceSize)");

    replaceAllWord(code, "OutputSize.xy", "outputSize");
    replaceAllWord(code, "OutputSize.zw", "(1.0 / outputSize)");
    replaceAllWord(code, "OutputSize.x", "outputSize.x");
    replaceAllWord(code, "OutputSize.y", "outputSize.y");
    replaceAllWord(code, "OutputSize.z", "(1.0 / outputSize.x)");
    replaceAllWord(code, "OutputSize.w", "(1.0 / outputSize.y)");
    replaceAllWord(code, "OutputSize", "float4(outputSize, 1.0/outputSize)");

    replaceAllWord(code, "OriginalSize.xy", "originalSize");
    replaceAllWord(code, "OriginalSize.zw", "(1.0 / originalSize)");
    replaceAllWord(code, "OriginalSize.x", "originalSize.x");
    replaceAllWord(code, "OriginalSize.y", "originalSize.y");
    replaceAllWord(code, "OriginalSize.z", "(1.0 / originalSize.x)");
    replaceAllWord(code, "OriginalSize.w", "(1.0 / originalSize.y)");
    replaceAllWord(code, "OriginalSize", "float4(originalSize, 1.0/originalSize)");

    replaceAllWord(code, "FrameCount", "frameCount");
    replaceAllWord(code, "FrameDirection", "1.0"); // always forward playback
    replaceAllWord(code, "CurrentSubFrame", "1");
    replaceAllWord(code, "TotalSubFrames", "1");
    replaceAllWord(code, "Rotation", "0.0");

    // HLSL → GLSL/SkSL function name conversions
    replaceAllWord(code, "lerp", "mix");
    replaceAllWord(code, "frac", "fract");

    // Convert texture()/tex2D()/texelFetch()/textureOffset()/textureSize() to SkSL
    // Uses proper paren matching instead of regex to handle nested parens
    {
        // Helper: find matching close paren, returns position after ')'
        auto findMatchingParen = [](const std::string& s, size_t openPos) -> size_t {
            int depth = 1;
            size_t p = openPos + 1;
            while (p < s.size() && depth > 0) {
                if (s[p] == '(') depth++;
                else if (s[p] == ')') depth--;
                p++;
            }
            return (depth == 0) ? p : std::string::npos;
        };

        // Helper: split args at top-level commas (respecting nesting)
        auto splitArgs = [](const std::string& s) -> std::vector<std::string> {
            std::vector<std::string> args;
            int depth = 0;
            size_t start = 0;
            for (size_t i = 0; i <= s.size(); i++) {
                if (i == s.size() || (s[i] == ',' && depth == 0)) {
                    std::string a = s.substr(start, i - start);
                    size_t fs = a.find_first_not_of(" \t\r\n");
                    size_t fe = a.find_last_not_of(" \t\r\n");
                    if (fs != std::string::npos) a = a.substr(fs, fe - fs + 1);
                    else a = "";
                    args.push_back(a);
                    start = i + 1;
                } else if (s[i] == '(') depth++;
                else if (s[i] == ')') depth--;
            }
            return args;
        };

        // Build sampler→eval mapping: Source→src, Original→src, LUTs→lut.id
        std::vector<std::pair<std::string, std::string>> samplerMap;
        samplerMap.push_back({"Source", "src"});
        samplerMap.push_back({"Original", "src"});
        for (const auto& lut : luts) {
            samplerMap.push_back({lut.id, lut.id});
        }

        // Process texture() and tex2D() calls with proper paren matching
        for (const char* funcName : {"texture", "tex2D"}) {
            size_t pos = 0;
            std::string fn = funcName;
            while ((pos = code.find(fn, pos)) != std::string::npos) {
                // Word boundary check
                if (pos > 0 && (isalnum(code[pos-1]) || code[pos-1] == '_')) { pos++; continue; }
                size_t after = pos + fn.size();
                // Skip whitespace to find (
                while (after < code.size() && (code[after] == ' ' || code[after] == '\t')) after++;
                if (after >= code.size() || code[after] != '(') { pos = after; continue; }

                size_t closePos = findMatchingParen(code, after);
                if (closePos == std::string::npos) { pos = after; continue; }

                std::string inner = code.substr(after + 1, closePos - after - 2);
                auto args = splitArgs(inner);
                if (args.size() < 2) { pos = closePos; continue; }

                std::string samplerName = args[0];
                std::string uvExpr = args[1];

                // Find which eval target to use
                std::string evalTarget = "src";
                std::string sizeVar = "sourceSize";
                for (const auto& [sName, eTarget] : samplerMap) {
                    if (samplerName == sName) {
                        evalTarget = eTarget;
                        if (sName == "Original") sizeVar = "originalSize";
                        else if (sName != "Source") sizeVar = sName + "Size";
                        break;
                    }
                }

                std::string replacement = evalTarget + ".eval((" + uvExpr + ") * " + sizeVar + ")";
                code.replace(pos, closePos - pos, replacement);
                pos += replacement.size();
            }
        }

        // texelFetch(sampler, coord, lod) → src.eval(float2(coord) + 0.5)
        {
            size_t pos = 0;
            while ((pos = code.find("texelFetch", pos)) != std::string::npos) {
                if (pos > 0 && (isalnum(code[pos-1]) || code[pos-1] == '_')) { pos++; continue; }
                size_t after = pos + 10;
                while (after < code.size() && (code[after] == ' ' || code[after] == '\t')) after++;
                if (after >= code.size() || code[after] != '(') { pos = after; continue; }
                size_t closePos = findMatchingParen(code, after);
                if (closePos == std::string::npos) { pos = after; continue; }
                std::string inner = code.substr(after + 1, closePos - after - 2);
                auto args = splitArgs(inner);
                if (args.size() >= 2) {
                    std::string replacement = "src.eval(float2(" + args[1] + ") + 0.5)";
                    code.replace(pos, closePos - pos, replacement);
                    pos += replacement.size();
                } else { pos = closePos; }
            }
        }

        // textureSize(sampler, lod) → sourceSize (manual, no regex)
        replaceTextureSizeCalls(code);

        // textureOffset(sampler, coord, offset) → src.eval((coord + float2(offset)/sourceSize) * sourceSize)
        {
            size_t pos = 0;
            while ((pos = code.find("textureOffset", pos)) != std::string::npos) {
                if (pos > 0 && (isalnum(code[pos-1]) || code[pos-1] == '_')) { pos++; continue; }
                size_t after = pos + 13;
                while (after < code.size() && (code[after] == ' ' || code[after] == '\t')) after++;
                if (after >= code.size() || code[after] != '(') { pos = after; continue; }
                size_t closePos = findMatchingParen(code, after);
                if (closePos == std::string::npos) { pos = after; continue; }
                std::string inner = code.substr(after + 1, closePos - after - 2);
                auto args = splitArgs(inner);
                if (args.size() >= 3) {
                    std::string replacement = "src.eval((" + args[1] + " + float2(" + args[2] + ")/sourceSize) * sourceSize)";
                    code.replace(pos, closePos - pos, replacement);
                    pos += replacement.size();
                } else { pos = closePos; }
            }
        }
    }

    // Convert bare Source identifier (after SourceSize is already handled above)
    replaceAllWord(code, "Source", "src");

    // Stub dFdx/dFdy/fwidth (not available in SkSL) → replace with 0.0 * arg
    for (const char* dfn : {"dFdx", "dFdy", "fwidth"}) {
        std::string fn = dfn;
        size_t pos = 0;
        while ((pos = code.find(fn, pos)) != std::string::npos) {
            bool leftOk = (pos == 0 || !(isalnum(code[pos-1]) || code[pos-1] == '_'));
            size_t after = pos + fn.size();
            bool rightOk = (after < code.size() && code[after] == '(');
            if (!leftOk || !rightOk) { pos = after; continue; }
            // Find matching )
            int depth = 1;
            size_t p = after + 1;
            while (p < code.size() && depth > 0) {
                if (code[p] == '(') depth++;
                else if (code[p] == ')') depth--;
                p++;
            }
            if (depth != 0) { pos = after; continue; }
            std::string arg = code.substr(after + 1, p - after - 2);
            std::string repl = "/*" + fn + "*/(0.0 * " + arg + ")";
            code.replace(pos, p - pos, repl);
            pos += repl.size();
        }
    }

    // modf(x, outvar) → (outvar = floor(x), x - floor(x))
    // SkSL modf only supports float, not float2/float3/float4
    {
        size_t pos = 0;
        while ((pos = code.find("modf", pos)) != std::string::npos) {
            bool leftOk = (pos == 0 || !(isalnum(code[pos-1]) || code[pos-1] == '_'));
            size_t after = pos + 4;
            bool rightOk = (after < code.size() && code[after] == '(');
            if (!leftOk || !rightOk) { pos = after; continue; }
            int depth = 1;
            size_t p = after + 1;
            while (p < code.size() && depth > 0) {
                if (code[p] == '(') depth++;
                else if (code[p] == ')') depth--;
                p++;
            }
            if (depth != 0) { pos = after; continue; }
            // Extract args: modf(expr, outvar)
            std::string inner = code.substr(after + 1, p - after - 2);
            // Find the comma separating the two args (at depth 0)
            int cdepth = 0;
            size_t comma = std::string::npos;
            for (size_t ci = 0; ci < inner.size(); ci++) {
                if (inner[ci] == '(' || inner[ci] == '[') cdepth++;
                else if (inner[ci] == ')' || inner[ci] == ']') cdepth--;
                else if (inner[ci] == ',' && cdepth == 0) { comma = ci; break; }
            }
            if (comma == std::string::npos) { pos = p; continue; }
            std::string expr = trim(inner.substr(0, comma));
            std::string outvar = trim(inner.substr(comma + 1));
            std::string repl = "(" + outvar + " = floor(" + expr + "), " + expr + " - floor(" + expr + "))";
            code.replace(pos, p - pos, repl);
            pos += repl.size();
        }
    }

    // HLSL saturate(x) → clamp(x, 0.0, 1.0)
    {
        size_t pos = 0;
        while ((pos = code.find("saturate", pos)) != std::string::npos) {
            bool leftOk = (pos == 0 || !(isalnum(code[pos-1]) || code[pos-1] == '_'));
            size_t after = pos + 8;
            bool rightOk = (after < code.size() && code[after] == '(');
            if (!leftOk || !rightOk) { pos = after; continue; }
            int depth = 1;
            size_t p = after + 1;
            while (p < code.size() && depth > 0) {
                if (code[p] == '(') depth++;
                else if (code[p] == ')') depth--;
                p++;
            }
            if (depth != 0) { pos = after; continue; }
            std::string arg = code.substr(after + 1, p - after - 2);
            std::string repl = "clamp(" + arg + ", 0.0, 1.0)";
            code.replace(pos, p - pos, repl);
            pos += repl.size();
        }
    }

    // PassFeedback/PassOutput/OriginalHistory references → src (manual, no regex)
    // Handle PassFeedbackSizeN and PassOutputSizeN FIRST (longer match)
    {
        for (const char* prefix : {"PassFeedbackSize", "PassOutputSize"}) {
            size_t pos = 0;
            std::string pfx = prefix;
            while ((pos = code.find(pfx, pos)) != std::string::npos) {
                bool leftOk = (pos == 0 || !(isalnum(code[pos-1]) || code[pos-1] == '_'));
                if (!leftOk) { pos++; continue; }
                size_t end = pos + pfx.size();
                while (end < code.size() && isdigit(code[end])) end++;
                bool rightOk = (end >= code.size() || !(isalnum(code[end]) || code[end] == '_'));
                if (!rightOk) { pos++; continue; }
                // Check for .xy, .zw, .x, etc. swizzles
                std::string repl;
                if (end < code.size() && code[end] == '.') {
                    size_t swzEnd = end + 1;
                    while (swzEnd < code.size() && isalpha(code[swzEnd])) swzEnd++;
                    std::string swz = code.substr(end + 1, swzEnd - end - 1);
                    if (swz == "xy") repl = "sourceSize";
                    else if (swz == "zw") repl = "(1.0 / sourceSize)";
                    else if (swz == "x") repl = "sourceSize.x";
                    else if (swz == "y") repl = "sourceSize.y";
                    else if (swz == "z") repl = "(1.0 / sourceSize.x)";
                    else if (swz == "w") repl = "(1.0 / sourceSize.y)";
                    else repl = "float4(sourceSize, 1.0/sourceSize)";
                    end = swzEnd;
                } else {
                    repl = "float4(sourceSize, 1.0/sourceSize)";
                }
                code.replace(pos, end - pos, repl);
                pos += repl.size();
            }
        }
    }
    replaceWordWithTrailingDigits(code, "PassFeedback", "src.eval(_coord)");
    replaceWordWithTrailingDigits(code, "PassOutput", "src.eval(_coord)");

    // OriginalHistoryN → treat as current source
    // Handle OriginalHistory<digits>Size first (longer match), then OriginalHistory<digits>
    {
        size_t pos = 0;
        while ((pos = code.find("OriginalHistory", pos)) != std::string::npos) {
            bool leftOk = (pos == 0 || !(isalnum(code[pos-1]) || code[pos-1] == '_'));
            if (!leftOk) { pos++; continue; }
            size_t end = pos + 15; // after "OriginalHistory"
            // Consume trailing digits
            while (end < code.size() && isdigit(code[end])) end++;
            // Check if followed by "Size"
            if (end + 4 <= code.size() && code.compare(end, 4, "Size") == 0) {
                size_t fullEnd = end + 4;
                bool rightOk = (fullEnd >= code.size() || !(isalnum(code[fullEnd]) || code[fullEnd] == '_'));
                if (rightOk) {
                    std::string repl = "float4(sourceSize, 1.0/sourceSize)";
                    code.replace(pos, fullEnd - pos, repl);
                    pos += repl.size();
                    continue;
                }
            }
            // Check right boundary for OriginalHistory<digits>
            bool rightOk = (end >= code.size() || !(isalnum(code[end]) || code[end] == '_'));
            if (rightOk) {
                code.replace(pos, end - pos, "src");
                pos += 3;
            } else {
                pos++;
            }
        }
    }

    // Note: equal(), notEqual(), all(), any() are valid SkSL built-ins — no conversion needed

    // Convert mix(a, b, boolExpr) to mix(a, b, float(boolExpr)) for bool select
    // SkSL mix() requires float for the third argument
    // (We don't do this generically — just handle common patterns)

    // textureDimensions → sourceSize (WGSL function)
    replaceAllWord(code, "textureDimensions", "sourceSize");

    // round() is not available in SkSL — convert to floor(x + 0.5)
    {
        size_t pos = 0;
        while ((pos = code.find("round(", pos)) != std::string::npos) {
            if (pos > 0 && (isalnum(code[pos-1]) || code[pos-1] == '_')) { pos++; continue; }
            // Find matching close paren
            size_t openParen = pos + 5;
            int depth = 1;
            size_t p = openParen + 1;
            while (p < code.size() && depth > 0) {
                if (code[p] == '(') depth++;
                else if (code[p] == ')') depth--;
                p++;
            }
            if (depth == 0) {
                std::string arg = code.substr(openParen + 1, p - openParen - 2);
                std::string replacement = "floor(" + arg + " + 0.5)";
                code.replace(pos, p - pos, replacement);
                pos += replacement.size();
            } else {
                pos++;
            }
        }
    }
}

// Add _gp parameter to helper functions that use varyings, and update call sites
static void fixVaryingsInHelpers(
        std::string& helpers,
        std::string& mainBody,
        const std::vector<std::pair<std::string,std::string>>& varyings) {
    if (varyings.empty()) return;

    // Check if helpers actually reference any varying
    bool needsFix = false;
    for (const auto& [name, type] : varyings) {
        size_t pos = 0;
        while ((pos = helpers.find(name, pos)) != std::string::npos) {
            bool leftOk = (pos == 0 || !(isalnum(helpers[pos-1]) || helpers[pos-1] == '_'));
            size_t end = pos + name.size();
            bool rightOk = (end >= helpers.size() || !(isalnum(helpers[end]) || helpers[end] == '_'));
            if (leftOk && rightOk) { needsFix = true; break; }
            pos++;
        }
        if (needsFix) break;
    }
    if (!needsFix) return;

    // Find user-defined function names (manual, no regex)
    std::set<std::string> funcNames = findFuncNames(helpers);
    if (funcNames.empty()) return;

    // Modify function definitions: add float2 _gp parameter and per-function varying computation
    for (const auto& fn : funcNames) {
        size_t pos = 0;
        while ((pos = helpers.find(fn, pos)) != std::string::npos) {
            // Word boundary check
            bool leftOk = (pos == 0 || !(isalnum(helpers[pos-1]) || helpers[pos-1] == '_'));
            size_t after = pos + fn.size();
            if (!leftOk || after >= helpers.size()) { pos++; continue; }
            // Check that this is preceded by a type keyword (it's a definition)
            size_t ls = helpers.rfind('\n', pos);
            ls = (ls == std::string::npos) ? 0 : ls + 1;
            std::string before = trim(helpers.substr(ls, pos - ls));
            if (!startsWithType(before + " x")) {
                // Not a type keyword before function name — skip
                // Check exact match: before should be exactly a type keyword
                bool isDef = false;
                static const char* types[] = {
                    "float4x4", "float3x3", "float2x2",
                    "float4", "float3", "float2", "float",
                    "half4", "half3", "half2", "half",
                    "int4", "int3", "int2", "int",
                    "void", "bool", nullptr
                };
                for (const char** t = types; *t; ++t) {
                    if (before == *t) { isDef = true; break; }
                }
                if (!isDef) { pos = after; continue; }
            }
            // Skip whitespace to find (
            size_t pp = after;
            while (pp < helpers.size() && (helpers[pp] == ' ' || helpers[pp] == '\t')) pp++;
            if (pp >= helpers.size() || helpers[pp] != '(') { pos = after; continue; }
            // Find matching )
            int depth = 1;
            size_t p = pp + 1;
            while (p < helpers.size() && depth > 0) {
                if (helpers[p] == '(') depth++;
                else if (helpers[p] == ')') depth--;
                p++;
            }
            if (depth != 0) { pos = after; continue; }
            size_t closeParen = p - 1;
            // Find { after )
            size_t bracePos = closeParen + 1;
            while (bracePos < helpers.size() && (helpers[bracePos] == ' ' || helpers[bracePos] == '\t' || helpers[bracePos] == '\n'))
                bracePos++;
            if (bracePos >= helpers.size() || helpers[bracePos] != '{') { pos = after; continue; }
            // Extract function body to check for conflicting local declarations
            // Find matching } for the function body
            int bodyDepth = 1;
            size_t bodyEnd = bracePos + 1;
            while (bodyEnd < helpers.size() && bodyDepth > 0) {
                if (helpers[bodyEnd] == '{') bodyDepth++;
                else if (helpers[bodyEnd] == '}') bodyDepth--;
                bodyEnd++;
            }
            std::string funcBody = helpers.substr(bracePos + 1, bodyEnd - bracePos - 2);
            // Build per-function varying computation, skipping names that have local declarations
            std::string funcVaryingComp;
            for (const auto& [vName, vType] : varyings) {
                // Check if function body has a local declaration like "type vName ="
                bool hasLocal = false;
                size_t searchPos = 0;
                while ((searchPos = funcBody.find(vName, searchPos)) != std::string::npos) {
                    size_t ve = searchPos + vName.size();
                    bool wl = (searchPos == 0 || !(isalnum(funcBody[searchPos-1]) || funcBody[searchPos-1] == '_'));
                    bool wr = (ve >= funcBody.size() || !(isalnum(funcBody[ve]) || funcBody[ve] == '_'));
                    if (wl && wr) {
                        // Check if preceded by a type keyword on the same line
                        size_t lls = funcBody.rfind('\n', searchPos);
                        lls = (lls == std::string::npos) ? 0 : lls + 1;
                        std::string lb = funcBody.substr(lls, searchPos - lls);
                        std::string tl = trim(lb);
                        static const char* dtypes[] = {
                            "float4", "float3", "float2", "float",
                            "half4", "half3", "half2", "half",
                            "int4", "int3", "int2", "int",
                            "const float4", "const float3", "const float2", "const float",
                            nullptr
                        };
                        for (const char** dt = dtypes; *dt; ++dt) {
                            if (tl == *dt) { hasLocal = true; break; }
                        }
                        if (hasLocal) break;
                    }
                    searchPos++;
                }
                if (!hasLocal) {
                    if (vType == "float2") {
                        funcVaryingComp += " " + vType + " " + vName + " = _gp / outputSize;";
                    } else if (vType == "float") {
                        funcVaryingComp += " " + vType + " " + vName + " = 0.0;";
                    } else {
                        funcVaryingComp += " " + vType + " " + vName + " = " + vType + "(0.0);";
                    }
                }
            }
            // Extract params
            std::string params = helpers.substr(pp + 1, closeParen - pp - 1);
            // Insert _gp param and varying computation
            std::string newParam = trim(params).empty() ? "float2 _gp" : params + ", float2 _gp";
            std::string replacement = helpers.substr(pos, pp + 1 - pos) + newParam + ")" +
                                       helpers.substr(closeParen + 1, bracePos + 1 - closeParen - 1) +
                                       funcVaryingComp;
            helpers.replace(pos, bracePos + 1 - pos, replacement);
            pos += replacement.size();
        }
    }

    // List of type keywords for distinguishing definitions from calls
    static const char* typeKws[] = {
        "float4x4", "float3x3", "float2x2",
        "float4", "float3", "float2", "float",
        "half4", "half3", "half2", "half",
        "int4", "int3", "int2", "int",
        "void", "bool"
    };

    // Helper lambda: add an argument to all calls of user functions in code
    auto addCallArg = [&](std::string& code, const std::string& arg) {
        for (const auto& fn : funcNames) {
            std::string result;
            result.reserve(code.size() + code.size() / 10);
            size_t pos = 0;

            while (pos < code.size()) {
                size_t idx = code.find(fn, pos);
                if (idx == std::string::npos) {
                    result += code.substr(pos);
                    break;
                }

                // Word boundary check
                bool leftOk = (idx == 0 || !(isalnum(code[idx-1]) || code[idx-1] == '_'));
                size_t after = idx + fn.size();
                bool rightOk = (after >= code.size() || !(isalnum(code[after]) || code[after] == '_'));

                if (!leftOk || !rightOk) {
                    result += code.substr(pos, idx + 1 - pos);
                    pos = idx + 1;
                    continue;
                }

                // Skip whitespace to find (
                size_t pp = after;
                while (pp < code.size() && (code[pp] == ' ' || code[pp] == '\t')) pp++;

                if (pp >= code.size() || code[pp] != '(') {
                    result += code.substr(pos, after - pos);
                    pos = after;
                    continue;
                }

                // Check if this is a definition (preceded by a type keyword)
                size_t ls = code.rfind('\n', idx);
                ls = (ls == std::string::npos) ? 0 : ls + 1;
                std::string before = trim(code.substr(ls, idx - ls));
                bool isDef = false;
                for (const char* tk : typeKws) {
                    if (before == tk) { isDef = true; break; }
                }

                if (isDef) {
                    result += code.substr(pos, after - pos);
                    pos = after;
                    continue;
                }

                // This is a call - find matching )
                int depth = 1;
                size_t p = pp + 1;
                while (p < code.size() && depth > 0) {
                    if (code[p] == '(') depth++;
                    if (code[p] == ')') depth--;
                    p++;
                }
                size_t cp = p - 1;

                result += code.substr(pos, cp - pos);
                std::string args = code.substr(pp + 1, cp - pp - 1);
                if (trim(args).empty()) {
                    result += arg;
                } else {
                    result += ", " + arg;
                }
                result += ")";
                pos = cp + 1;
            }

            code = result;
        }
    };

    // Update call sites in helpers (pass _gp) and in main body (pass p)
    addCallArg(helpers, "_gp");
    addCallArg(mainBody, "_coord");
}

// ---------------------------------------------------------------------------
// GLSL → SkSL transpiler
// ---------------------------------------------------------------------------

std::string transpileToSkSL(const std::string& glslFragment,
                            const std::vector<SlangParam>& params,
                            int passIndex, int totalPasses,
                            const std::vector<SlangLut>& luts,
                            const std::unordered_map<std::string, std::string>& vtxVaryings = {}) {
    const bool debugLog = GetBoolProperty("persist.gammaos.shader.debug", false);

    std::ostringstream out;

    // Emit SkSL header with uniform declarations
    out << "// Auto-transpiled from .slang by GammaOS\n";
    out << "uniform shader src;\n";
    out << "uniform float2 outputSize;\n";
    out << "uniform float2 sourceSize;\n";
    out << "uniform float2 originalSize;\n";
    out << "uniform float frameCount;\n";

    // Emit LUT texture uniforms
    for (const auto& lut : luts) {
        out << "uniform shader " << lut.id << ";\n";
        out << "uniform float2 " << lut.id << "Size;\n";
    }

    // Emit parameters as const declarations with inlined values
    // (avoids uniform buffer offset issues with many float uniforms)
    for (const auto& p : params) {
        out << "const float " << p.id << " = " << std::to_string(p.current) << ";\n";
    }
    out << "\n";

    // Process the GLSL fragment line by line
    std::istringstream stream(glslFragment);
    std::string line;
    bool inMain = false;
    int braceDepth = 0;
    std::ostringstream mainBody;
    std::ostringstream helperCode;

    // Track varying inputs from the vertex shader
    std::vector<std::pair<std::string,std::string>> varyingInputs; // {name, skslType}

    // Track vertex/fragment stage — skip vertex stage code entirely
    bool inVertexStage = false;
    // Varying initializer expressions from vertex main (pre-extracted)
    std::unordered_map<std::string, std::string> varyingInitExprs = vtxVaryings;

    // Track block comments (/* ... */) to avoid parsing comment text as code
    bool inBlockComment = false;

    // Track uniform block state (to skip member declarations)
    bool inUniformBlock = false;

    // Track preprocessor conditional nesting (#ifdef/#ifndef/#if/#else/#endif)
    struct PPState { bool active; bool taken; bool isElif; };
    std::vector<PPState> ppStack;

    // Track already-defined #define names to avoid redefinition
    std::unordered_set<std::string> definedNames;

    // Track only truly-const defines (for isConstExpression checks)
    std::unordered_set<std::string> constDefinedNames;

    // Function-like #define macros for later expansion
    struct DefineMacro { std::string name; std::string args; std::string body; };
    std::vector<DefineMacro> defineMacros;

    // Non-const #define values that must be deferred into main body
    std::vector<std::string> deferredDefines;

    // Simple #define aliases (name → replacement) for post-processing
    std::vector<std::pair<std::string,std::string>> defineAliases;

    int _lineNum = 0;
    auto _loopStart = std::chrono::steady_clock::now();
    while (std::getline(stream, line)) {
        _lineNum++;
        // Handle backslash line continuation (GLSL allows, SkSL does not)
        while (!line.empty() && line.back() == '\\') {
            line.pop_back(); // remove trailing backslash
            std::string nextLine;
            if (std::getline(stream, nextLine)) {
                _lineNum++;
                line += " " + trim(nextLine);
            } else break;
        }
        std::string trimmed = trim(line);

        // Skip lines we don't need
        if (trimmed.find("#version") == 0) continue;
        // Track vertex/fragment stage — skip all vertex stage code
        if (trimmed.find("#pragma stage") == 0) {
            if (trimmed.find("vertex") != std::string::npos) {
                inVertexStage = true;
            } else if (trimmed.find("fragment") != std::string::npos) {
                inVertexStage = false;
            }
            continue;
        }
        if (inVertexStage) continue;

        // Track block comments (/* ... */) — pass through to output but don't parse as code
        if (inBlockComment) {
            if (trimmed.find("*/") != std::string::npos) {
                inBlockComment = false;
            }
            // Pass comment lines through to helper code (valid SkSL)
            if (!inMain) helperCode << line << "\n";
            else mainBody << line << "\n";
            continue;
        }
        if (trimmed.find("/*") != std::string::npos && trimmed.find("*/") == std::string::npos) {
            inBlockComment = true;
            if (!inMain) helperCode << line << "\n";
            else mainBody << line << "\n";
            continue;
        }

        if (trimmed.find("#pragma parameter") == 0) continue;
        if (trimmed.find("#pragma ") == 0) continue; // strip all remaining #pragma
        if (trimmed.find("#undef ") == 0) continue;
        if (trimmed.find("#include ") == 0) continue;
        if (trimmed.find("precision ") == 0) continue;
        if (trimmed.find("typedef ") == 0) continue; // unsupported in SkSL

        // Handle preprocessor conditionals (#ifdef/#ifndef/#if/#else/#elif/#endif)
        // Simple approach: track nesting, assume FRAGMENT and PARAMETER_UNIFORM are defined,
        // for unknown conditions assume defined (keep #ifdef branch, skip #else branch)
        if (trimmed.find("#ifdef ") == 0 || trimmed.find("#ifndef ") == 0) {
            bool isIfndef = (trimmed.find("#ifndef") == 0);
            std::string cond = trim(trimmed.substr(isIfndef ? 8 : 7));
            // Known conditions
            bool condDefined = true; // default: assume defined
            if (cond == "VERTEX") condDefined = false;
            else if (cond == "FRAGMENT") condDefined = true;
            else if (cond == "PARAMETER_UNIFORM") condDefined = true;
            bool active = isIfndef ? !condDefined : condDefined;
            ppStack.push_back({active, active, false});
            continue;
        }
        if (trimmed.find("#if ") == 0 || trimmed == "#if") {
            // Handle #if defined(X) / #if !defined(X)
            bool condResult = true; // default: assume true
            std::string cond = trim(trimmed.substr(4));
            if (cond.find("defined(") != std::string::npos || cond.find("defined (") != std::string::npos) {
                bool negated = (cond.find("!defined") != std::string::npos || cond.find("! defined") != std::string::npos);
                size_t lp = cond.find('(');
                if (lp != std::string::npos) {
                    size_t rp = cond.find(')', lp);
                    if (rp != std::string::npos) {
                        std::string macroName = trim(cond.substr(lp + 1, rp - lp - 1));
                        bool isDefined = definedNames.count(macroName) > 0;
                        condResult = negated ? !isDefined : isDefined;
                    }
                }
            }
            ppStack.push_back({condResult, condResult, false});
            continue;
        }
        if (trimmed.find("#elif ") == 0 || trimmed == "#elif") {
            if (!ppStack.empty()) {
                if (ppStack.back().taken) {
                    ppStack.back().active = false; // already took a branch
                } else {
                    ppStack.back().active = true;
                    ppStack.back().taken = true;
                }
            }
            continue;
        }
        if (trimmed == "#else") {
            if (!ppStack.empty()) {
                ppStack.back().active = !ppStack.back().taken;
            }
            continue;
        }
        if (trimmed == "#endif") {
            if (!ppStack.empty()) ppStack.pop_back();
            continue;
        }
        // If inside an inactive preprocessor branch, skip the line
        if (!ppStack.empty() && !ppStack.back().active) continue;

        // Track and skip push_constant / UBO blocks entirely
        // Must check BEFORE the generic layout skip so we detect block starts
        if (trimmed.find("push_constant") != std::string::npos ||
            trimmed.find("uniform Push") != std::string::npos ||
            trimmed.find("uniform UBO") != std::string::npos ||
            trimmed.find("uniform Global") != std::string::npos) {
            inUniformBlock = true;
            continue;
        }
        if (inUniformBlock) {
            if (trimmed.find("} params;") != std::string::npos ||
                trimmed.find("} global;") != std::string::npos ||
                trimmed.find("} registers;") != std::string::npos ||
                trimmed == "};" ||
                (trimmed.find("} ") == 0 && trimmed.back() == ';')) {
                inUniformBlock = false;
            }
            continue;
        }

        // Skip layout declarations, but extract varying info from layout(...) in declarations
        if (trimmed.find("layout(") == 0 || trimmed.find("layout (") == 0) {
            // Check if this is a layout(...) in <type> <name> declaration (manual)
            {
                size_t inPos = trimmed.find(") in ");
                if (inPos == std::string::npos) inPos = trimmed.find(")in ");
                if (inPos != std::string::npos) {
                    // Skip past ") in "
                    size_t typeStart = trimmed.find("in ", inPos);
                    if (typeStart != std::string::npos) {
                        typeStart += 3;
                        while (typeStart < trimmed.size() && trimmed[typeStart] == ' ') typeStart++;
                        // Read type
                        size_t typeEnd = typeStart;
                        while (typeEnd < trimmed.size() && (isalnum(trimmed[typeEnd]) || trimmed[typeEnd] == '_'))
                            typeEnd++;
                        std::string glslType = trimmed.substr(typeStart, typeEnd - typeStart);
                        // Read name
                        size_t nameStart = typeEnd;
                        while (nameStart < trimmed.size() && trimmed[nameStart] == ' ') nameStart++;
                        size_t nameEnd = nameStart;
                        while (nameEnd < trimmed.size() && (isalnum(trimmed[nameEnd]) || trimmed[nameEnd] == '_'))
                            nameEnd++;
                        if (nameEnd > nameStart) {
                            std::string vName = trimmed.substr(nameStart, nameEnd - nameStart);
                            std::string skslType = glslType;
                            if (glslType == "vec2") skslType = "float2";
                            else if (glslType == "vec3") skslType = "float3";
                            else if (glslType == "vec4") skslType = "float4";
                            else if (glslType == "mat2") skslType = "float2x2";
                            else if (glslType == "mat3") skslType = "float3x3";
                            else if (glslType == "mat4") skslType = "float4x4";
                            varyingInputs.push_back({vName, skslType});
                        }
                    }
                }
            }
            continue;
        }

        // Parse in declarations — capture varying info instead of just skipping
        if (trimmed.find("out ") == 0 && trimmed.find("void") == std::string::npos) continue;
        if (trimmed.find("in ") == 0 && trimmed.find("void") == std::string::npos) {
            // Parse "in type name;" manually
            {
                // Skip "in " prefix
                size_t typeStart = 3;
                while (typeStart < trimmed.size() && trimmed[typeStart] == ' ') typeStart++;
                size_t typeEnd = typeStart;
                while (typeEnd < trimmed.size() && (isalnum(trimmed[typeEnd]) || trimmed[typeEnd] == '_'))
                    typeEnd++;
                std::string glslType = trimmed.substr(typeStart, typeEnd - typeStart);
                size_t nameStart = typeEnd;
                while (nameStart < trimmed.size() && trimmed[nameStart] == ' ') nameStart++;
                size_t nameEnd = nameStart;
                while (nameEnd < trimmed.size() && (isalnum(trimmed[nameEnd]) || trimmed[nameEnd] == '_'))
                    nameEnd++;
                if (nameEnd > nameStart) {
                    std::string vName = trimmed.substr(nameStart, nameEnd - nameStart);
                    std::string skslType = glslType;
                    // Only accept known GLSL types — reject comment text parsed as "in"
                    if (glslType == "vec2") skslType = "float2";
                    else if (glslType == "vec3") skslType = "float3";
                    else if (glslType == "vec4") skslType = "float4";
                    else if (glslType == "ivec2") skslType = "int2";
                    else if (glslType == "ivec3") skslType = "int3";
                    else if (glslType == "ivec4") skslType = "int4";
                    else if (glslType == "mat2") skslType = "float2x2";
                    else if (glslType == "mat3") skslType = "float3x3";
                    else if (glslType == "mat4") skslType = "float4x4";
                    else if (glslType == "float" || glslType == "int" || glslType == "bool") { /* ok */ }
                    else continue; // unknown type — not a real varying declaration
                    varyingInputs.push_back({vName, skslType});
                }
            }
            continue;
        }

        // Skip uniform sampler2D declarations
        if (trimmed.find("uniform sampler2D") != std::string::npos) continue;

        // Handle #define directives — SkSL doesn't support them
        if (trimmed.find("#define ") == 0) {
            std::string rest = trimmed.substr(8); // skip "#define "
            size_t sp = rest.find_first_of(" \t(");
            if (sp != std::string::npos) {
                std::string name = rest.substr(0, sp);
                // Skip if name clashes with a declared parameter uniform
                {
                    bool isParam = false;
                    for (const auto& p : params) {
                        if (p.id == name) { isParam = true; break; }
                    }
                    if (isParam) continue;
                }
                // Skip duplicate #define (e.g., FXAA presets with multiple #if blocks)
                if (definedNames.count(name) && rest[sp] != '(') {
                    continue;
                }
                // Skip #define of type keywords (self-referential after type conversion)
                {
                    static const std::unordered_set<std::string> typeKeywords = {
                        "float", "float2", "float3", "float4",
                        "float2x2", "float3x3", "float4x4",
                        "int", "int2", "int3", "int4",
                        "half", "half2", "half3", "half4",
                        "bool", "bool2", "bool3", "bool4",
                        "void", "sampler2D", "texture2D"
                    };
                    if (typeKeywords.count(name)) continue;
                }
                // Check for function-like macro: #define FOO(args) body
                if (sp < rest.size() && rest[sp] == '(') {
                    size_t cp = rest.find(')', sp);
                    if (cp != std::string::npos) {
                        std::string argList = rest.substr(sp + 1, cp - sp - 1);
                        std::string mbody = trim(rest.substr(cp + 1));
                        // Strip inline comments from macro body
                        {
                            size_t mcpos = mbody.find("//");
                            if (mcpos != std::string::npos) mbody = trim(mbody.substr(0, mcpos));
                        }
                        if (!mbody.empty()) {
                            applyTypeConversions(mbody);
                            // Pre-convert params./global./registers. accesses (manual)
                            stripDotPrefix(mbody, "params");
                            stripDotPrefix(mbody, "param");
                            stripDotPrefix(mbody, "global");
                            stripDotPrefix(mbody, "registers");
                            // Store as a macro for later text substitution
                            defineMacros.push_back({name, argList, mbody});
                        }
                    }
                    continue;
                }
                std::string value = trim(rest.substr(sp));
                // Strip inline comments from #define values
                {
                    size_t commentPos = value.find("//");
                    if (commentPos != std::string::npos) {
                        value = trim(value.substr(0, commentPos));
                    }
                }
                if (!value.empty()) {
                    // Simple aliases to params/global/registers:
                    // e.g., #define modulate params.acc_modulate → replace modulate with acc_modulate
                    if ((value.find("params.") == 0 || value.find("param.") == 0 ||
                         value.find("global.") == 0 || value.find("registers.") == 0) &&
                        value.find(' ') == std::string::npos &&
                        value.find('(') == std::string::npos) {
                        // Extract the member name after the dot
                        auto dotPos = value.find('.');
                        std::string memberName = value.substr(dotPos + 1);
                        // If alias name differs from member name, store for text replacement
                        if (name != memberName) {
                            defineAliases.push_back({name, memberName});
                        }
                        continue;
                    }
                    // Strip sampler/texture aliases (PassFeedback, PassOutput, Original, Source)
                    if (value.find("PassFeedback") == 0 || value.find("PassOutput") == 0 ||
                        value == "Source" || value == "Original") {
                        continue;
                    }
                    // Apply type conversions to the value
                    applyTypeConversions(value);
                    // Infer type from the value
                    std::string type;
                    if (value.find("float4x4(") == 0 || value.find("mat4(") == 0) type = "float4x4";
                    else if (value.find("float3x3(") == 0 || value.find("mat3(") == 0) type = "float3x3";
                    else if (value.find("float4(") == 0 || value.find("vec4(") == 0) type = "float4";
                    else if (value.find("float3(") == 0 || value.find("vec3(") == 0) type = "float3";
                    else if (value.find("float2(") == 0 || value.find("vec2(") == 0) type = "float2";
                    else if (value.find("int(") == 0) type = "int";
                    else if (value.find("bool(") == 0) type = "bool";
                    else if (value == "true" || value == "false") type = "bool";
                    else {
                        // Check if the value is a pure integer expression (no decimal point)
                        // Integer literals: "16", "0xFF", "0x1", bitwise: "1 << 2", "0x01 | 0x02"
                        bool looksInt = true;
                        bool hasDigit = false;
                        for (size_t ci = 0; ci < value.size(); ci++) {
                            char c = value[ci];
                            if (isdigit(c)) { hasDigit = true; continue; }
                            if (c == 'x' || c == 'X' || c == 'u' || c == 'U') continue; // hex/unsigned
                            if ((c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F')) continue; // hex digits
                            if (c == ' ' || c == '\t' || c == '(' || c == ')') continue;
                            if (c == '<' || c == '>' || c == '|' || c == '&' || c == '^' || c == '~') continue; // bitwise
                            if (c == '+' || c == '-' || c == '*') continue;
                            if (c == '.') { looksInt = false; break; } // decimal point → float
                            if (c == '_' || isalpha(c)) {
                                // Could be an identifier reference — check if it's a known int define
                                size_t idStart = ci;
                                while (ci < value.size() && (isalnum(value[ci]) || value[ci] == '_')) ci++;
                                // Just treat as non-int-literal to be safe
                                looksInt = false;
                                break;
                            }
                            // Unknown char
                            looksInt = false;
                            break;
                        }
                        if (looksInt && hasDigit) type = "int";
                        else type = "float";
                    }

                    // Ensure float-typed values are float literals (SkSL rejects float x = 0;)
                    if (type == "float") {
                        size_t si = 0;
                        if (!value.empty() && (value[0] == '-' || value[0] == '+')) si = 1;
                        if (si < value.size() && isdigit(value[si])) {
                            bool allDigit = true;
                            for (size_t ci = si; ci < value.size(); ci++) {
                                if (!isdigit(value[ci])) { allDigit = false; break; }
                            }
                            if (allDigit) value += ".0";
                        }
                    }

                    // Check if value is a simple constant expression (manual, no regex)
                    // Use constDefinedNames — only truly-const defines count
                    bool isConst = isConstExpression(value, constDefinedNames);

                    std::string decl;
                    if (isConst) {
                        decl = "const " + type + " " + name + " = " + value + ";";
                    } else {
                        // Not a constant expression — just use a regular variable
                        decl = type + " " + name + " = " + value + ";";
                    }
                    definedNames.insert(name);
                    if (isConst) constDefinedNames.insert(name);
                    if (inMain) {
                        mainBody << decl << "\n";
                    } else {
                        // Non-const globals must go inside main
                        if (!isConst) {
                            deferredDefines.push_back(decl);
                        } else {
                            helperCode << decl << "\n";
                        }
                    }
                }
            } else {
                // #define NAME (no value) — record in definedNames for #if defined() checks
                std::string name = trim(rest);
                if (!name.empty()) definedNames.insert(name);
            }
            continue;
        }

        // Detect void main()
        if (!inMain && trimmed.find("void main()") != std::string::npos) {
            inMain = true;
            braceDepth = 0;
            for (char c : trimmed) {
                if (c == '{') braceDepth++;
                if (c == '}') braceDepth--;
            }
            continue;
        }

        if (inMain) {
            // If we haven't seen the opening brace yet (it's on a separate line),
            // consume it without emitting
            if (braceDepth == 0 && trimmed == "{") {
                braceDepth = 1;
                continue;
            }
            for (char c : trimmed) {
                if (c == '{') braceDepth++;
                if (c == '}') braceDepth--;
            }
            if (braceDepth <= 0 && trimmed.find('}') != std::string::npos) {
                inMain = false;
                continue;
            }
            mainBody << line << "\n";
        } else {
            // Non-main code (helper functions, constants, etc.)
            // Type conversions are applied in bulk post-processing, not per-line
            helperCode << line << "\n";
        }
    }

    {
        auto loopDt = std::chrono::steady_clock::now() - _loopStart;
        auto loopMs = std::chrono::duration_cast<std::chrono::milliseconds>(loopDt).count();
        ALOGI("GammaCustomShader: line-by-line loop done (%d lines, %lldms)", _lineNum, (long long)loopMs);
    }

    // ---------------------------------------------------------------
    // Post-process helper code and main body
    // ---------------------------------------------------------------
    auto _t0 = std::chrono::steady_clock::now();
    auto _tLog = [&_t0](const char* label) {
        auto dt = std::chrono::steady_clock::now() - _t0;
        auto ms = std::chrono::duration_cast<std::chrono::milliseconds>(dt).count();
        ALOGI("GammaCustomShader: transpile [%s] at %lldms", label, (long long)ms);
    };
    _tLog("post-process start");

    std::string helpers = helperCode.str();
    std::string body = mainBody.str();

    // Fix sampler2D params: remove from definitions, fix call sites across helpers+body
    fixSampler2DFunctions(helpers, body);
    _tLog("after sampler2D fixup");

    // Apply GLSL→SkSL type conversions to helper code (done in bulk, not per-line)
    applyTypeConversions(helpers);
    _tLog("after helper type conversions");

    // Move non-const global variable declarations that use uniforms into main body
    // Detect lines like: int x = int(mode); or bool debug = bool(debug_toggle);
    // These reference uniform values and can't be global in SkSL
    {
        std::istringstream hStream(helpers);
        std::string hLine;
        std::ostringstream newHelpers;
        // Build set of uniform/param names for detection
        std::unordered_set<std::string> uniformNames;
        for (const auto& p : params) uniformNames.insert(p.id);
        uniformNames.insert("outputSize");
        uniformNames.insert("sourceSize");
        uniformNames.insert("originalSize");
        uniformNames.insert("frameCount");
        uniformNames.insert("FrameCount"); // before semantic conversion
        for (const auto& lut : luts) {
            uniformNames.insert(lut.id);
            uniformNames.insert(lut.id + "Size");
        }

        // Collect inlining pairs to apply after full loop (declarations may appear
        // before their usage sites in helpers)
        std::vector<std::pair<std::string, std::string>> inlinePairs;
        int helperBraceDepth = 0; // track if we're inside a function body

        while (std::getline(hStream, hLine)) {
            std::string ht = trim(hLine);
            // Track brace depth to distinguish global scope from function bodies
            for (char c : ht) {
                if (c == '{') helperBraceDepth++;
                else if (c == '}') helperBraceDepth--;
            }
            // Skip empty lines and comments
            if (ht.empty() || ht[0] == '/' || ht[0] == '*') {
                newHelpers << hLine << "\n";
                continue;
            }
            // Only relocate variables at global scope (braceDepth == 0)
            // Variables inside helper functions must stay where they are
            bool isGlobalVarWithUniform = false;
            std::string declType, declName, initExpr;
            // Handle const-prefixed declarations too — GLSL const on
            // uniform-referencing vars is invalid in SkSL global scope
            // Strip inline comments before parsing (e.g., "float timer = mod(fc, 4268.); // Global timer")
            std::string htNoComment = ht;
            {
                size_t cpos = htNoComment.find("//");
                if (cpos != std::string::npos) htNoComment = trim(htNoComment.substr(0, cpos));
            }
            std::string parseTarget = htNoComment;
            bool lineHadConst = false;
            if (parseTarget.compare(0, 6, "const ") == 0) {
                lineHadConst = true;
                parseTarget = trim(parseTarget.substr(6));
            }
            if (helperBraceDepth == 0 && parseVarDecl(parseTarget, declType, declName, initExpr)) {
                // Check if the initializer references any uniform (directly or via params./global./registers.)
                if (initExpr.find("params.") != std::string::npos ||
                    initExpr.find("param.") != std::string::npos ||
                    initExpr.find("global.") != std::string::npos ||
                    initExpr.find("registers.") != std::string::npos) {
                    isGlobalVarWithUniform = true;
                }
                if (!isGlobalVarWithUniform) {
                    for (const auto& uname : uniformNames) {
                        size_t pos = initExpr.find(uname);
                        if (pos != std::string::npos) {
                            bool leftOk = (pos == 0 || !(isalnum(initExpr[pos-1]) || initExpr[pos-1] == '_'));
                            size_t end = pos + uname.size();
                            bool rightOk = (end >= initExpr.size() || !(isalnum(initExpr[end]) || initExpr[end] == '_'));
                            if (leftOk && rightOk) {
                                isGlobalVarWithUniform = true;
                                break;
                            }
                        }
                    }
                }
            }
            if (isGlobalVarWithUniform) {
                // Only move to main if the variable is NOT used by helper functions
                // (i.e., it only appears in this declaration, not elsewhere in helpers)
                std::string varName = declName;
                // Count occurrences of varName in all of helpers (excluding this line)
                size_t usageCount = 0;
                std::istringstream checkStream(helpers);
                std::string checkLine;
                while (std::getline(checkStream, checkLine)) {
                    if (trim(checkLine) == ht) continue; // skip the declaration itself
                    // Word boundary search
                    size_t sp = 0;
                    while ((sp = checkLine.find(varName, sp)) != std::string::npos) {
                        bool lOk = (sp == 0 || !(isalnum(checkLine[sp-1]) || checkLine[sp-1] == '_'));
                        size_t ep = sp + varName.size();
                        bool rOk = (ep >= checkLine.size() || !(isalnum(checkLine[ep]) || checkLine[ep] == '_'));
                        if (lOk && rOk) { usageCount++; break; }
                        sp++;
                    }
                    if (usageCount > 0) break;
                }
                // Also check if the variable appears in any function-like macro body
                // that is used in helpers (macros expand AFTER this pass)
                if (usageCount == 0) {
                    for (const auto& macro : defineMacros) {
                        // Check if macro body references this variable
                        size_t mpos = macro.body.find(varName);
                        if (mpos == std::string::npos) continue;
                        bool mlOk = (mpos == 0 || !(isalnum(macro.body[mpos-1]) || macro.body[mpos-1] == '_'));
                        size_t mep = mpos + varName.size();
                        bool mrOk = (mep >= macro.body.size() || !(isalnum(macro.body[mep]) || macro.body[mep] == '_'));
                        if (!mlOk || !mrOk) continue;
                        // Macro references the var — check if this macro is invoked in helpers
                        size_t hpos = 0;
                        while ((hpos = helpers.find(macro.name, hpos)) != std::string::npos) {
                            bool hlOk = (hpos == 0 || !(isalnum(helpers[hpos-1]) || helpers[hpos-1] == '_'));
                            size_t hep = hpos + macro.name.size();
                            if (hlOk && hep < helpers.size() && helpers[hep] == '(') {
                                usageCount++;
                                break;
                            }
                            hpos++;
                        }
                        if (usageCount > 0) break;
                    }
                }
                if (usageCount == 0) {
                    // Safe to move to main — strip const (value references runtime uniforms)
                    deferredDefines.push_back(declType + " " + declName + " = " + initExpr + ";");
                } else {
                    // Used in helpers AND references a uniform — can't be global in SkSL.
                    // Collect for deferred inlining after full loop.
                    inlinePairs.push_back({declName, "(" + initExpr + ")"});
                    // Don't emit the declaration
                }
            } else {
                newHelpers << hLine << "\n";
            }
        }
        helpers = newHelpers.str();

        // Apply deferred inlining to the full helpers, body, AND macro bodies
        for (const auto& ip : inlinePairs) {
            replaceAllWord(helpers, ip.first, ip.second);
            replaceAllWord(body, ip.first, ip.second);
            for (auto& macro : defineMacros) {
                replaceAllWord(macro.body, ip.first, ip.second);
            }
        }
    }

    _tLog("after global var relocation");

    // Strip const from helper declarations where the initializer isn't a true
    // compile-time constant (e.g., GLSL const referencing non-const expressions)
    {
        std::istringstream hStream2(helpers);
        std::string hLine2;
        std::ostringstream newHelpers2;
        int hbd2 = 0;
        while (std::getline(hStream2, hLine2)) {
            std::string ht2 = trim(hLine2);
            for (char c : ht2) {
                if (c == '{') hbd2++;
                else if (c == '}') hbd2--;
            }
            if (hbd2 == 0 && ht2.compare(0, 6, "const ") == 0) {
                std::string pt2 = trim(ht2.substr(6));
                std::string ct2, cn2, ce2;
                if (parseVarDecl(pt2, ct2, cn2, ce2)) {
                    if (!isConstExpression(ce2, constDefinedNames)) {
                        // Strip const — value isn't a compile-time constant
                        // Move to deferredDefines (inside main body)
                        deferredDefines.push_back(ct2 + " " + cn2 + " = " + ce2 + ";");
                        continue;
                    }
                }
            }
            newHelpers2 << hLine2 << "\n";
        }
        helpers = newHelpers2.str();
    }

    _tLog("after const stripping");

    // SkSL runtime effects only allow uniform or const globals.
    // For non-const global variable declarations at scope 0:
    // - Matrix types (float4x4, etc.): relocate into functions that use them
    //   (const float4x4 with complex initializers may not work on all GPUs)
    // - Scalar/vector types: add 'const' prefix
    {
        static const std::vector<std::string> globalTypes = {
            "float4x4", "float3x3", "float2x2",
            "float4", "float3", "float2", "float",
            "int4", "int3", "int2", "int",
            "half4", "half3", "half2", "half",
            "bool"
        };
        static const std::unordered_set<std::string> matrixTypes = {
            "float4x4", "float3x3", "float2x2"
        };
        // Collect non-const global declarations to relocate
        struct GlobalDecl {
            std::string varName;
            std::string fullDecl; // complete declaration including semicolon
            std::string typeName;
            size_t startPos;
            size_t endPos; // position after semicolon+newline
        };
        std::vector<GlobalDecl> globalsToRelocate;

        int gbd = 0;
        size_t pos = 0;
        while (pos < helpers.size()) {
            size_t eol = helpers.find('\n', pos);
            if (eol == std::string::npos) eol = helpers.size();
            std::string line = helpers.substr(pos, eol - pos);
            std::string tl = trim(line);
            for (char c : tl) {
                if (c == '{') gbd++;
                else if (c == '}') gbd--;
            }
            if (gbd == 0 && !tl.empty() && tl[0] != '/' && tl[0] != '*' && tl[0] != '#' &&
                tl.compare(0, 6, "const ") != 0 && tl.compare(0, 8, "uniform ") != 0) {
                for (const auto& typN : globalTypes) {
                    if (tl.compare(0, typN.size(), typN) == 0 &&
                        tl.size() > typN.size() && tl[typN.size()] == ' ') {
                        size_t eqPos = tl.find('=');
                        size_t parenPos = tl.find('(');
                        if (eqPos == std::string::npos || (parenPos != std::string::npos && parenPos < eqPos)) break;
                        size_t semi = helpers.find(';', pos);
                        if (semi != std::string::npos) {
                            std::string fullDecl = helpers.substr(pos, semi - pos + 1);
                            if (fullDecl.find('{') != std::string::npos) break;
                            // Extract variable name
                            std::string afterType = trim(tl.substr(typN.size()));
                            size_t nameEnd = 0;
                            while (nameEnd < afterType.size() && (isalnum(afterType[nameEnd]) || afterType[nameEnd] == '_')) nameEnd++;
                            std::string varName = afterType.substr(0, nameEnd);

                            if (matrixTypes.count(typN)) {
                                // Matrix: collect for relocation into function body
                                size_t endPos = semi + 1;
                                if (endPos < helpers.size() && helpers[endPos] == '\n') endPos++;
                                globalsToRelocate.push_back({varName, trim(fullDecl), typN, pos, endPos});
                            } else {
                                // Scalar/vector: add const prefix
                                size_t insertAt = pos;
                                while (insertAt < helpers.size() && (helpers[insertAt] == ' ' || helpers[insertAt] == '\t'))
                                    insertAt++;
                                helpers.insert(insertAt, "const ");
                                eol += 6;
                            }
                            pos = semi + 1;
                            if (pos < helpers.size() && helpers[pos] == '\n') pos++;
                            goto next_global_line;
                        }
                        break;
                    }
                }
            }
            pos = eol + 1;
            next_global_line:;
        }

        // Remove global matrix declarations and inject into functions that use them
        // Process in reverse order to preserve positions
        for (int gi = (int)globalsToRelocate.size() - 1; gi >= 0; gi--) {
            const auto& gd = globalsToRelocate[gi];
            helpers.erase(gd.startPos, gd.endPos - gd.startPos);
        }
        // Now inject each matrix declaration into functions that reference it
        for (const auto& gd : globalsToRelocate) {
            // Find all function bodies in helpers that reference this variable
            size_t searchPos = 0;
            bool injected = false;
            while (searchPos < helpers.size()) {
                // Find the variable name in the remaining text
                size_t ref = helpers.find(gd.varName, searchPos);
                if (ref == std::string::npos) break;
                // Check word boundaries
                bool lOk = (ref == 0 || !(isalnum(helpers[ref-1]) || helpers[ref-1] == '_'));
                size_t rEnd = ref + gd.varName.size();
                bool rOk = (rEnd >= helpers.size() || !(isalnum(helpers[rEnd]) || helpers[rEnd] == '_'));
                if (!lOk || !rOk) { searchPos = ref + 1; continue; }
                // Found a reference — find the enclosing function's opening '{'
                // Walk backwards to find the function start
                int bd = 0;
                size_t bp = ref;
                bool foundOpen = false;
                while (bp > 0) {
                    bp--;
                    if (helpers[bp] == '}') bd++;
                    else if (helpers[bp] == '{') {
                        if (bd > 0) bd--;
                        else { foundOpen = true; break; }
                    }
                }
                if (foundOpen) {
                    // Insert the declaration right after the '{'
                    std::string injection = "\n    " + gd.fullDecl + "\n";
                    helpers.insert(bp + 1, injection);
                    injected = true;
                    break; // Only inject once per function
                }
                searchPos = ref + 1;
            }
            // If not injected into any function (used only in main), add to deferredDefines
            if (!injected) {
                deferredDefines.push_back(gd.fullDecl);
            }
        }
    }

    _tLog("after global const promotion");

    // Apply simple #define aliases (e.g., modulate → acc_modulate)
    for (const auto& [aliasName, replacement] : defineAliases) {
        replaceAllWord(helpers, aliasName, replacement);
        replaceAllWord(body, aliasName, replacement);
    }

    _tLog("after aliases");
    // Expand function-like macros in both helpers and body
    for (const auto& macro : defineMacros) {
        // Parse argument names
        std::vector<std::string> argNames;
        {
            std::istringstream argStream(macro.args);
            std::string arg;
            while (std::getline(argStream, arg, ',')) {
                arg = trim(arg);
                if (!arg.empty()) argNames.push_back(arg);
            }
        }

        // Expand macro invocations: NAME(arg1, arg2, ...)
        auto expandIn = [&](std::string& code) {
            size_t pos = 0;
            while ((pos = code.find(macro.name, pos)) != std::string::npos) {
                // Word boundary check
                bool leftOk = (pos == 0 || !(isalnum(code[pos-1]) || code[pos-1] == '_'));
                size_t after = pos + macro.name.size();
                if (!leftOk || after >= code.size() || code[after] != '(') {
                    pos = after;
                    continue;
                }
                // Find matching closing paren
                int depth = 1;
                size_t start = after + 1;
                size_t p = start;
                std::vector<std::string> actualArgs;
                size_t argStart = start;
                while (p < code.size() && depth > 0) {
                    if (code[p] == '(') depth++;
                    else if (code[p] == ')') {
                        depth--;
                        if (depth == 0) {
                            actualArgs.push_back(trim(code.substr(argStart, p - argStart)));
                        }
                    } else if (code[p] == ',' && depth == 1) {
                        actualArgs.push_back(trim(code.substr(argStart, p - argStart)));
                        argStart = p + 1;
                    }
                    p++;
                }
                if (depth != 0) { pos = after; continue; }

                // Build replacement by substituting args into body
                std::string expanded = macro.body;
                for (size_t i = 0; i < argNames.size() && i < actualArgs.size(); i++) {
                    replaceAllWord(expanded, argNames[i], actualArgs[i]);
                }

                code.replace(pos, p - pos, expanded);
                pos += expanded.size();
            }
        };
        expandIn(helpers);
        expandIn(body);
    }

    _tLog("after macro expansion");
    // Apply type conversions to main body
    applyTypeConversions(body);

    _tLog("after type conversions");
    // Apply semantic conversions (params, sizes, texture, etc.) to BOTH
    applySemanticConversions(helpers, luts);
    applySemanticConversions(body, luts);

    _tLog("after semantic conversions");
    // Fix float4x4 constructors with wrong arg count (from mat4x3/mat3x4 conversion)
    // mat4x3 has 12 elements → pad to 16 for float4x4
    {
        auto fixMatrixCtor = [](std::string& code) {
            // Find float4x4( and count args; if 12 args, pad each column with 0.0
            size_t pos = 0;
            while ((pos = code.find("float4x4(", pos)) != std::string::npos) {
                // Check word boundary
                if (pos > 0 && (isalnum(code[pos-1]) || code[pos-1] == '_')) {
                    pos++;
                    continue;
                }
                size_t start = pos + 9; // after "float4x4("
                int depth = 1;
                size_t p = start;
                std::vector<std::string> args;
                size_t argStart = start;
                while (p < code.size() && depth > 0) {
                    if (code[p] == '(') depth++;
                    else if (code[p] == ')') {
                        depth--;
                        if (depth == 0) {
                            std::string a = trim(code.substr(argStart, p - argStart));
                            if (!a.empty()) args.push_back(a);
                        }
                    } else if (code[p] == ',' && depth == 1) {
                        args.push_back(trim(code.substr(argStart, p - argStart)));
                        argStart = p + 1;
                    }
                    p++;
                }
                if (depth != 0) { pos++; continue; }

                // If exactly 12 scalar args (from mat4x3), rearrange to 4x4:
                // mat4x3 cols: (a,b,c), (d,e,f), (g,h,i), (j,k,l)
                // float4x4 cols: (a,b,c,0), (d,e,f,0), (g,h,i,0), (j,k,l,0)
                else if (args.size() == 12) {
                    std::string fixed = "float4x4(";
                    for (int col = 0; col < 4; col++) {
                        if (col > 0) fixed += ", ";
                        fixed += args[col*3] + ", " + args[col*3+1] + ", " + args[col*3+2] + ", 0.0";
                    }
                    fixed += ")";
                    code.replace(pos, p - pos, fixed);
                    pos += fixed.size();
                }
                // If exactly 6 args (from mat3x2/mat2x3 → float3x3)
                else if (args.size() == 6) {
                    std::string fixed = "float4x4(";
                    fixed += args[0] + ", " + args[1] + ", 0.0, 0.0, ";
                    fixed += args[2] + ", " + args[3] + ", 0.0, 0.0, ";
                    fixed += args[4] + ", " + args[5] + ", 0.0, 0.0, ";
                    fixed += "0.0, 0.0, 0.0, 0.0)";
                    code.replace(pos, p - pos, fixed);
                    pos += fixed.size();
                }
                // If exactly 8 args (from mat4x2/mat2x4 → float4x4)
                else if (args.size() == 8) {
                    std::string fixed = "float4x4(";
                    for (int col = 0; col < 4; col++) {
                        if (col > 0) fixed += ", ";
                        fixed += args[col*2] + ", " + args[col*2+1] + ", 0.0, 0.0";
                    }
                    fixed += ")";
                    code.replace(pos, p - pos, fixed);
                    pos += fixed.size();
                }
                else {
                    pos = p;
                }
            }
        };
        fixMatrixCtor(helpers);
        fixMatrixCtor(body);
    }

    _tLog("after matrix ctors");

    // Fix float3 * float4x4 → float4(float3, 0.0) * float4x4
    // (from mat4x3/mat3x4 conversions where dimensions no longer match)
    {
        // Collect typed variable names from code
        auto collectTypedVars = [](const std::string& code, const std::string& typeName) -> std::unordered_set<std::string> {
            std::unordered_set<std::string> result;
            std::string pattern = typeName + " ";
            size_t patLen = pattern.size();
            size_t pos = 0;
            while ((pos = code.find(pattern, pos)) != std::string::npos) {
                if (pos > 0 && (isalnum(code[pos-1]) || code[pos-1] == '_')) { pos++; continue; }
                size_t ns = pos + patLen;
                while (ns < code.size() && (code[ns] == ' ' || code[ns] == '\t')) ns++;
                if (ns >= code.size() || !(isalpha(code[ns]) || code[ns] == '_')) { pos++; continue; }
                size_t ne = ns;
                while (ne < code.size() && (isalnum(code[ne]) || code[ne] == '_')) ne++;
                result.insert(code.substr(ns, ne - ns));
                pos = ne;
            }
            return result;
        };

        // Collect float4x4 and float3 vars from BOTH helpers and body
        auto mat4Vars = collectTypedVars(helpers, "float4x4");
        auto bodyMat4 = collectTypedVars(body, "float4x4");
        mat4Vars.insert(bodyMat4.begin(), bodyMat4.end());

        auto float3Vars = collectTypedVars(helpers, "float3");
        auto bodyFloat3 = collectTypedVars(body, "float3");
        float3Vars.insert(bodyFloat3.begin(), bodyFloat3.end());
        // Also collect "const float3" declarations
        auto constFloat3H = collectTypedVars(helpers, "const float3");
        auto constFloat3B = collectTypedVars(body, "const float3");
        float3Vars.insert(constFloat3H.begin(), constFloat3H.end());
        float3Vars.insert(constFloat3B.begin(), constFloat3B.end());

        auto fixMatMulDim = [&mat4Vars, &float3Vars](std::string& code) {
            // Find all * operators where one side is float3 and the other is float4x4
            size_t pos = 0;
            while (pos < code.size()) {
                // Find next * operator
                pos = code.find('*', pos);
                if (pos == std::string::npos) break;
                // Skip *= and **
                if (pos + 1 < code.size() && code[pos+1] == '=') { pos += 2; continue; }
                // Check right operand for float4x4 variable or constructor
                size_t rStart = pos + 1;
                while (rStart < code.size() && (code[rStart] == ' ' || code[rStart] == '\t')) rStart++;
                bool rightIsMat4 = false;
                if (rStart + 9 <= code.size() && code.compare(rStart, 9, "float4x4(") == 0) {
                    rightIsMat4 = true;
                } else if (rStart < code.size() && (isalpha(code[rStart]) || code[rStart] == '_')) {
                    size_t re = rStart;
                    while (re < code.size() && (isalnum(code[re]) || code[re] == '_')) re++;
                    if (mat4Vars.count(code.substr(rStart, re - rStart))) rightIsMat4 = true;
                }
                if (!rightIsMat4) { pos++; continue; }
                // Check left operand for float3
                size_t lEnd = pos;
                while (lEnd > 0 && (code[lEnd-1] == ' ' || code[lEnd-1] == '\t')) lEnd--;
                size_t lStart = lEnd;
                while (lStart > 0 && (isalnum(code[lStart-1]) || code[lStart-1] == '_' || code[lStart-1] == '.')) lStart--;
                if (lStart == lEnd) { pos++; continue; }
                std::string leftExpr = code.substr(lStart, lEnd - lStart);
                bool isFloat3 = false;
                if (leftExpr.size() >= 4) {
                    std::string suffix = leftExpr.substr(leftExpr.size() - 4);
                    if (suffix == ".rgb" || suffix == ".xyz") isFloat3 = true;
                }
                if (!isFloat3) {
                    // Check collected float3 vars from both helpers and body
                    if (float3Vars.count(leftExpr)) isFloat3 = true;
                }
                if (isFloat3) {
                    std::string wrapped = "float4(" + leftExpr + ", 0.0)";
                    code.replace(lStart, lEnd - lStart, wrapped);
                    pos = lStart + wrapped.size() + 1;
                } else {
                    pos++;
                }
            }
        };
        fixMatMulDim(helpers);
        fixMatMulDim(body);
    }

    // Fix mat4x3-converted float4x4 multiplication result:
    // mat4x3 * vec4 = vec3, but after conversion float4x4 * float4 = float4
    // Detect "padded" float4x4 (all columns end with , 0.0) and append .xyz to mul result
    {
        // Collect padded float4x4 variable names (originally mat4x3)
        auto collectMat4x3Vars = [](const std::string& code) -> std::unordered_set<std::string> {
            std::unordered_set<std::string> result;
            size_t pos = 0;
            while ((pos = code.find("float4x4 ", pos)) != std::string::npos) {
                if (pos > 0 && (isalnum(code[pos-1]) || code[pos-1] == '_')) { pos++; continue; }
                size_t ns = pos + 9;
                while (ns < code.size() && code[ns] == ' ') ns++;
                if (ns >= code.size() || !(isalpha(code[ns]) || code[ns] == '_')) { pos++; continue; }
                size_t ne = ns;
                while (ne < code.size() && (isalnum(code[ne]) || code[ne] == '_')) ne++;
                std::string varName = code.substr(ns, ne - ns);
                // Look for = float4x4(
                size_t eq = ne;
                while (eq < code.size() && code[eq] == ' ') eq++;
                if (eq >= code.size() || code[eq] != '=') { pos = ne; continue; }
                eq++;
                while (eq < code.size() && code[eq] == ' ') eq++;
                if (eq + 9 > code.size() || code.compare(eq, 9, "float4x4(") != 0) { pos = ne; continue; }
                // Find matching paren
                int depth = 1; size_t p = eq + 9;
                while (p < code.size() && depth > 0) {
                    if (code[p] == '(') depth++;
                    else if (code[p] == ')') depth--;
                    p++;
                }
                if (depth != 0) { pos = ne; continue; }
                // Check all column args end with ", 0.0)"
                std::string ctorBody = code.substr(eq + 9, p - 1 - (eq + 9));
                bool allPadded = true; int f4Count = 0;
                size_t fp = 0;
                while ((fp = ctorBody.find("float4(", fp)) != std::string::npos) {
                    f4Count++;
                    int d = 1; size_t ep = fp + 7;
                    while (ep < ctorBody.size() && d > 0) {
                        if (ctorBody[ep] == '(') d++;
                        else if (ctorBody[ep] == ')') d--;
                        ep++;
                    }
                    if (d != 0) { allPadded = false; break; }
                    std::string inner = ctorBody.substr(fp + 7, ep - 1 - (fp + 7));
                    size_t lc = inner.rfind(',');
                    if (lc == std::string::npos) { allPadded = false; break; }
                    std::string la = trim(inner.substr(lc + 1));
                    if (la != "0.0") { allPadded = false; break; }
                    fp = ep;
                }
                if (allPadded && f4Count >= 3) result.insert(varName);
                pos = ne;
            }
            return result;
        };

        auto mat4x3VarsH = collectMat4x3Vars(helpers);
        auto mat4x3VarsB = collectMat4x3Vars(body);
        mat4x3VarsH.insert(mat4x3VarsB.begin(), mat4x3VarsB.end());

        // Helper: find right operand boundary after * (stops at +, -, ;, , or ) at depth 0)
        auto findRightOpEnd = [](const std::string& code, size_t rStart) -> size_t {
            size_t rEnd = rStart;
            int depth = 0;
            while (rEnd < code.size()) {
                char c = code[rEnd];
                if (c == '(' || c == '[') depth++;
                else if (c == ')' || c == ']') {
                    if (depth == 0) break;
                    depth--;
                } else if (depth == 0 && (c == '+' || c == '-' || c == ';' || c == ',')) break;
                rEnd++;
            }
            return rEnd;
        };

        // Fix inline: float4x4(float4(X, 0.0), ...) * EXPR → (float4x4(...) * EXPR).xyz
        auto fixInlinePaddedMul = [&findRightOpEnd](std::string& code) {
            size_t pos = 0;
            while ((pos = code.find("float4x4(float4(", pos)) != std::string::npos) {
                if (pos > 0 && (isalnum(code[pos-1]) || code[pos-1] == '_')) { pos++; continue; }
                // Find matching paren of float4x4(...)
                int depth = 1; size_t p = pos + 9;
                while (p < code.size() && depth > 0) {
                    if (code[p] == '(') depth++;
                    else if (code[p] == ')') depth--;
                    p++;
                }
                if (depth != 0) { pos++; continue; }
                // Check all columns are padded with 0.0
                std::string ctorBody = code.substr(pos + 9, p - 1 - (pos + 9));
                bool allPadded = true; int f4Count = 0;
                size_t fp = 0;
                while ((fp = ctorBody.find("float4(", fp)) != std::string::npos) {
                    f4Count++;
                    int d = 1; size_t ep = fp + 7;
                    while (ep < ctorBody.size() && d > 0) {
                        if (ctorBody[ep] == '(') d++;
                        else if (ctorBody[ep] == ')') d--;
                        ep++;
                    }
                    if (d != 0) { allPadded = false; break; }
                    std::string inner = ctorBody.substr(fp + 7, ep - 1 - (fp + 7));
                    size_t lc = inner.rfind(',');
                    if (lc == std::string::npos) { allPadded = false; break; }
                    std::string la = trim(inner.substr(lc + 1));
                    if (la != "0.0") { allPadded = false; break; }
                    fp = ep;
                }
                if (!allPadded || f4Count < 3) { pos = p; continue; }
                // Check for * after constructor
                size_t s = p;
                while (s < code.size() && (code[s] == ' ' || code[s] == '\t')) s++;
                if (s >= code.size() || code[s] != '*') { pos = p; continue; }
                if (s + 1 < code.size() && code[s+1] == '=') { pos = p; continue; }
                size_t rStart = s + 1;
                while (rStart < code.size() && (code[rStart] == ' ' || code[rStart] == '\t')) rStart++;
                size_t rEnd = findRightOpEnd(code, rStart);
                // Wrap (float4x4(...) * EXPR).xyz
                std::string mulExpr = code.substr(pos, rEnd - pos);
                std::string wrapped = "(" + mulExpr + ").xyz";
                code.replace(pos, rEnd - pos, wrapped);
                pos += wrapped.size();
            }
        };

        // Fix named: mat4x3_var * EXPR → (mat4x3_var * EXPR).xyz
        auto fixNamedPaddedMul = [&mat4x3VarsH, &findRightOpEnd](std::string& code) {
            for (const auto& varName : mat4x3VarsH) {
                size_t pos = 0;
                while ((pos = code.find(varName, pos)) != std::string::npos) {
                    bool lOk = (pos == 0 || !(isalnum(code[pos-1]) || code[pos-1] == '_'));
                    size_t after = pos + varName.size();
                    bool rOk = (after >= code.size() || !(isalnum(code[after]) || code[after] == '_'));
                    if (!lOk || !rOk) { pos = after; continue; }
                    // Check for * after varName
                    size_t s = after;
                    while (s < code.size() && (code[s] == ' ' || code[s] == '\t')) s++;
                    if (s >= code.size() || code[s] != '*') { pos = after; continue; }
                    if (s + 1 < code.size() && code[s+1] == '=') { pos = after; continue; }
                    size_t rStart = s + 1;
                    while (rStart < code.size() && (code[rStart] == ' ' || code[rStart] == '\t')) rStart++;
                    size_t rEnd = findRightOpEnd(code, rStart);
                    std::string mulExpr = code.substr(pos, rEnd - pos);
                    std::string wrapped = "(" + mulExpr + ").xyz";
                    code.replace(pos, rEnd - pos, wrapped);
                    pos += wrapped.size();
                }
            }
        };

        fixInlinePaddedMul(helpers);
        fixInlinePaddedMul(body);
        fixNamedPaddedMul(helpers);
        fixNamedPaddedMul(body);
    }

    // Convert array initializers (manual, no regex)
    // Handles: type name[N] = {a,b,...}; type name[] = {a,b,...}; type name[N] = type[N](a,b,...);
    // For global scope (helpers): declare array, defer init to beginning of main body
    // For function scope (body): use element-by-element assignments in-place
    {
        std::string deferredArrayInit; // collected array inits for prepending to body

        // fixArrayBrace: handle type name[N] = {a,b,...}; and 2D arrays
        auto fixArrayBrace = [&deferredArrayInit, &helpers, &body](std::string& code, bool isGlobal) {
            size_t searchFrom = 0;
            size_t matchStart, matchEnd;
            std::string type, varName, sizeStr;
            std::vector<std::string> elems;
            int secondDim;
            while (parseArrayBraceInit(code, searchFrom, matchStart, matchEnd,
                                        type, varName, sizeStr, elems, secondDim)) {
                std::string actualSize;
                if (secondDim > 0) {
                    actualSize = std::to_string(elems.size());
                } else {
                    actualSize = sizeStr.empty() ? std::to_string(elems.size()) : sizeStr;
                }
                std::string replacement;
                std::string assignments;
                for (size_t i = 0; i < elems.size(); i++) {
                    assignments += "    " + varName + "[" + std::to_string(i) + "] = " + elems[i] + ";\n";
                }
                if (isGlobal) {
                    // Global: just the declaration here, init deferred to main body
                    replacement = type + " " + varName + "[" + actualSize + "];\n";
                    deferredArrayInit += assignments;
                } else {
                    // Function scope: declaration + assignments in-place
                    replacement = type + " " + varName + "[" + actualSize + "];\n" + assignments;
                }
                code.replace(matchStart, matchEnd - matchStart, replacement);
                searchFrom = matchStart + replacement.size();

                // For 2D arrays, convert access name[i][j] → name[(i)*M + (j)]
                if (secondDim > 0) {
                    std::string M = std::to_string(secondDim);
                    // Fix in both code being processed AND the other string
                    auto fix2D = [&](std::string& s) {
                        size_t pos = 0;
                        while ((pos = s.find(varName + "[", pos)) != std::string::npos) {
                            size_t brk1 = pos + varName.size();
                            if (brk1 >= s.size() || s[brk1] != '[') { pos++; continue; }
                            size_t end1 = brk1 + 1;
                            int bd = 1;
                            while (end1 < s.size() && bd > 0) {
                                if (s[end1] == '[') bd++;
                                else if (s[end1] == ']') bd--;
                                end1++;
                            }
                            if (bd != 0) { pos++; continue; }
                            if (end1 >= s.size() || s[end1] != '[') { pos++; continue; }
                            size_t brk2 = end1;
                            size_t end2 = brk2 + 1;
                            bd = 1;
                            while (end2 < s.size() && bd > 0) {
                                if (s[end2] == '[') bd++;
                                else if (s[end2] == ']') bd--;
                                end2++;
                            }
                            if (bd != 0) { pos++; continue; }
                            std::string idxI = s.substr(brk1 + 1, end1 - brk1 - 2);
                            std::string idxJ = s.substr(brk2 + 1, end2 - brk2 - 2);
                            std::string newAccess = varName + "[(" + idxI + ")*" + M + " + (" + idxJ + ")]";
                            s.replace(pos, end2 - pos, newAccess);
                            pos += newAccess.size();
                        }
                    };
                    fix2D(code);
                    // Also fix 2D access in the other string (helpers↔body)
                    if (isGlobal) fix2D(body);
                    else fix2D(helpers);
                }
            }
        };

        // fixArrayCtor: handle type name[N] = type[N](a,b,...);
        auto fixArrayCtor = [&deferredArrayInit](std::string& code, bool isGlobal) {
            size_t searchFrom = 0;
            size_t matchStart, matchEnd;
            std::string baseType, varName;
            std::vector<std::string> elems;
            while (parseArrayCtorInit(code, searchFrom, matchStart, matchEnd,
                                       baseType, varName, elems)) {
                std::string replacement;
                std::string assignments;
                for (size_t i = 0; i < elems.size(); i++) {
                    assignments += "    " + varName + "[" + std::to_string(i) + "] = " + elems[i] + ";\n";
                }
                if (isGlobal) {
                    replacement = baseType + " " + varName + "[" + std::to_string(elems.size()) + "];\n";
                    deferredArrayInit += assignments;
                } else {
                    replacement = baseType + " " + varName + "[" + std::to_string(elems.size()) + "];\n" + assignments;
                }
                code.replace(matchStart, matchEnd - matchStart, replacement);
                searchFrom = matchStart + replacement.size();
            }
        };

        fixArrayBrace(helpers, true);
        fixArrayCtor(helpers, true);
        fixArrayBrace(body, false);
        fixArrayCtor(body, false);

        // Prepend deferred array inits to beginning of body
        if (!deferredArrayInit.empty()) {
            body = deferredArrayInit + body;
        }
    }

    // Fix unsized array declarations: type name[]; → remove (manual, no regex)
    removeUnsizedArrayDecls(helpers);
    removeUnsizedArrayDecls(body);

    // Convert % operator to mod() ONLY for float operands
    // SkSL supports % on integers but not floats. Keep % when operands look integer.
    {
        auto fixModulo = [](std::string& code) {
            auto looksInteger = [](const std::string& expr) -> bool {
                // Pure digit literal (possibly hex)
                if (expr.empty()) return false;
                bool allDigit = true;
                for (char c : expr) {
                    if (!isdigit(c) && c != 'x' && c != 'X' && c != 'u' && c != 'U' &&
                        !((c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F'))) {
                        allDigit = false;
                        break;
                    }
                }
                if (allDigit && !expr.empty() && isdigit(expr[0])) return true;
                // No decimal point and looks like a simple identifier or integer expression
                if (expr.find('.') != std::string::npos) return false;
                return false; // Conservative: if unsure, treat as float
            };

            size_t pos = 0;
            while (pos < code.size()) {
                size_t pct = code.find('%', pos);
                if (pct == std::string::npos) break;
                size_t lEnd = pct;
                while (lEnd > 0 && code[lEnd-1] == ' ') lEnd--;
                if (lEnd == 0) { pos = pct + 1; continue; }
                size_t lStart;
                if (code[lEnd - 1] == ')') {
                    int d = 1;
                    size_t p = lEnd - 2;
                    while (p > 0 && d > 0) {
                        if (code[p] == ')') d++;
                        else if (code[p] == '(') d--;
                        if (d > 0) p--;
                    }
                    lStart = p;
                } else if (isalnum(code[lEnd-1]) || code[lEnd-1] == '_' || code[lEnd-1] == '.') {
                    lStart = lEnd - 1;
                    while (lStart > 0 && (isalnum(code[lStart-1]) || code[lStart-1] == '_' || code[lStart-1] == '.')) lStart--;
                } else {
                    pos = pct + 1;
                    continue;
                }
                size_t rStart = pct + 1;
                while (rStart < code.size() && code[rStart] == ' ') rStart++;
                if (rStart >= code.size()) { pos = pct + 1; continue; }
                size_t rEnd;
                if (code[rStart] == '(') {
                    int d = 1;
                    size_t p = rStart + 1;
                    while (p < code.size() && d > 0) {
                        if (code[p] == '(') d++;
                        else if (code[p] == ')') d--;
                        p++;
                    }
                    rEnd = p;
                } else if (isalnum(code[rStart]) || code[rStart] == '_') {
                    rEnd = rStart + 1;
                    while (rEnd < code.size() && (isalnum(code[rEnd]) || code[rEnd] == '_' || code[rEnd] == '.')) rEnd++;
                } else {
                    pos = pct + 1;
                    continue;
                }
                std::string left = code.substr(lStart, lEnd - lStart);
                std::string right = code.substr(rStart, rEnd - rStart);

                // Only convert to mod() if operands look like floats
                if (looksInteger(left) && looksInteger(right)) {
                    // Both operands are integer literals — keep %
                    pos = rEnd;
                } else {
                    std::string replacement = "mod(float(" + left + "), float(" + right + "))";
                    code.replace(lStart, rEnd - lStart, replacement);
                    pos = lStart + replacement.size();
                }
            }
        };
        fixModulo(helpers);
        fixModulo(body);
    }

    // Convert mix(a, b, boolExpr) to mix(a, b, float(boolExpr))
    // SkSL's mix() requires float for the third argument, not bool
    {
        auto fixMixBool = [](std::string& code) {
            size_t pos = 0;
            while ((pos = code.find("mix(", pos)) != std::string::npos) {
                if (pos > 0 && (isalnum(code[pos-1]) || code[pos-1] == '_')) { pos++; continue; }
                size_t start = pos + 4;
                // Find matching close paren with proper nesting
                int depth = 1;
                size_t p = start;
                std::vector<size_t> commaPositions;
                while (p < code.size() && depth > 0) {
                    if (code[p] == '(') depth++;
                    else if (code[p] == ')') depth--;
                    else if (code[p] == ',' && depth == 1) commaPositions.push_back(p);
                    p++;
                }
                if (depth != 0 || commaPositions.size() != 2) { pos = start; continue; }
                // Third arg is between second comma and close paren
                size_t thirdStart = commaPositions[1] + 1;
                size_t thirdEnd = p - 1;
                std::string thirdArg = code.substr(thirdStart, thirdEnd - thirdStart);
                // Trim
                size_t fs = thirdArg.find_first_not_of(" \t");
                size_t fe = thirdArg.find_last_not_of(" \t");
                if (fs == std::string::npos) { pos = p; continue; }
                std::string trimmedThird = thirdArg.substr(fs, fe - fs + 1);
                // Check if 3rd arg contains comparison operators (likely bool)
                bool looksBool = false;
                if (trimmedThird.find("==") != std::string::npos ||
                    trimmedThird.find("!=") != std::string::npos ||
                    trimmedThird.find(">=") != std::string::npos ||
                    trimmedThird.find("<=") != std::string::npos ||
                    trimmedThird == "true" || trimmedThird == "false") {
                    looksBool = true;
                }
                // Also check for simple < or > (but not << or >>)
                if (!looksBool) {
                    for (size_t i = 0; i < trimmedThird.size(); i++) {
                        if ((trimmedThird[i] == '<' || trimmedThird[i] == '>') &&
                            (i + 1 >= trimmedThird.size() || trimmedThird[i+1] != trimmedThird[i]) &&
                            (i == 0 || trimmedThird[i-1] != trimmedThird[i])) {
                            looksBool = true;
                            break;
                        }
                    }
                }
                if (looksBool) {
                    std::string wrapped = " float(" + trimmedThird + ")";
                    code.replace(thirdStart, thirdEnd - thirdStart, wrapped);
                    pos = thirdStart + wrapped.size() + 1;
                } else {
                    pos = p;
                }
            }
        };
        fixMixBool(helpers);
        fixMixBool(body);
    }

    _tLog("after array init");
    // Convert do-while loops to for(;;) with break (SkSL doesn't support do-while)
    {
        auto fixDoWhile = [](std::string& code) {
            size_t pos = 0;
            while ((pos = code.find("do", pos)) != std::string::npos) {
                // Check word boundaries
                if (pos > 0 && (isalnum(code[pos-1]) || code[pos-1] == '_')) { pos++; continue; }
                size_t afterDo = pos + 2;
                if (afterDo < code.size() && (isalnum(code[afterDo]) || code[afterDo] == '_')) { pos++; continue; }
                // Skip whitespace/newlines after 'do'
                size_t braceStart = afterDo;
                while (braceStart < code.size() && (code[braceStart] == ' ' || code[braceStart] == '\t' ||
                       code[braceStart] == '\n' || code[braceStart] == '\r')) braceStart++;
                if (braceStart >= code.size() || code[braceStart] != '{') { pos++; continue; }
                // Find matching }
                int depth = 1;
                size_t braceEnd = braceStart + 1;
                while (braceEnd < code.size() && depth > 0) {
                    if (code[braceEnd] == '{') depth++;
                    else if (code[braceEnd] == '}') depth--;
                    braceEnd++;
                }
                if (depth != 0) { pos++; continue; }
                // braceEnd is past the closing }
                // Find "while" after the closing }
                size_t whilePos = braceEnd;
                while (whilePos < code.size() && (code[whilePos] == ' ' || code[whilePos] == '\t' ||
                       code[whilePos] == '\n' || code[whilePos] == '\r')) whilePos++;
                if (whilePos + 5 >= code.size() || code.compare(whilePos, 5, "while") != 0) { pos++; continue; }
                size_t afterWhile = whilePos + 5;
                while (afterWhile < code.size() && (code[afterWhile] == ' ' || code[afterWhile] == '\t')) afterWhile++;
                if (afterWhile >= code.size() || code[afterWhile] != '(') { pos++; continue; }
                // Find matching )
                int pd = 1;
                size_t condEnd = afterWhile + 1;
                while (condEnd < code.size() && pd > 0) {
                    if (code[condEnd] == '(') pd++;
                    else if (code[condEnd] == ')') pd--;
                    condEnd++;
                }
                if (pd != 0) { pos++; continue; }
                // condEnd is past the ), find ;
                size_t semiPos = condEnd;
                while (semiPos < code.size() && (code[semiPos] == ' ' || code[semiPos] == '\t')) semiPos++;
                if (semiPos < code.size() && code[semiPos] == ';') semiPos++;
                // Extract condition
                std::string cond = code.substr(afterWhile + 1, condEnd - afterWhile - 2);
                // Extract body (between braces, without the braces themselves)
                std::string doBody = code.substr(braceStart + 1, braceEnd - braceStart - 2);
                // Build replacement using bounded for loop (SkSL requires simple counter conditions)
                // for (int _dw=0; _dw<10000; _dw++) { body; if (!(cond)) break; }
                static int doWhileCounter = 0;
                std::string dwVar = "_dw" + std::to_string(doWhileCounter++);
                std::string replacement = "for (int " + dwVar + " = 0; " + dwVar + " < 256; " + dwVar + "++) {" +
                    doBody + "\n    if (!(" + cond + ")) break;\n}";
                code.replace(pos, semiPos - pos, replacement);
                // Don't advance past replacement — inner do-while loops may be nested
                // The outer while(true){ won't match 'do' so no infinite loop risk
            }
        };
        fixDoWhile(helpers);
        fixDoWhile(body);
    }

    // Convert while loops to for loops (SkSL doesn't support while loops)
    {
        auto fixWhileLoop = [](std::string& code) {
            size_t pos = 0;
            static int whileCounter = 0;
            while ((pos = code.find("while", pos)) != std::string::npos) {
                // Check word boundaries
                if (pos > 0 && (isalnum(code[pos-1]) || code[pos-1] == '_')) { pos++; continue; }
                size_t afterWhile = pos + 5;
                if (afterWhile < code.size() && (isalnum(code[afterWhile]) || code[afterWhile] == '_')) { pos++; continue; }
                // Skip whitespace
                size_t parenStart = afterWhile;
                while (parenStart < code.size() && (code[parenStart] == ' ' || code[parenStart] == '\t')) parenStart++;
                if (parenStart >= code.size() || code[parenStart] != '(') { pos++; continue; }
                // Find matching )
                int pd = 1;
                size_t parenEnd = parenStart + 1;
                while (parenEnd < code.size() && pd > 0) {
                    if (code[parenEnd] == '(') pd++;
                    else if (code[parenEnd] == ')') pd--;
                    parenEnd++;
                }
                if (pd != 0) { pos++; continue; }
                // Check that next non-whitespace is {
                size_t braceStart = parenEnd;
                while (braceStart < code.size() && (code[braceStart] == ' ' || code[braceStart] == '\t' ||
                       code[braceStart] == '\n' || code[braceStart] == '\r')) braceStart++;
                if (braceStart >= code.size() || code[braceStart] != '{') { pos++; continue; }
                // Find matching }
                int depth = 1;
                size_t braceEnd = braceStart + 1;
                while (braceEnd < code.size() && depth > 0) {
                    if (code[braceEnd] == '{') depth++;
                    else if (code[braceEnd] == '}') depth--;
                    braceEnd++;
                }
                if (depth != 0) { pos++; continue; }
                // Extract condition and body
                std::string cond = code.substr(parenStart + 1, parenEnd - parenStart - 2);
                std::string whileBody = code.substr(braceStart + 1, braceEnd - braceStart - 2);
                // Build: for (int _wl=0; _wl<10000; _wl++) { if (!(cond)) break; body }
                std::string wVar = "_wl" + std::to_string(whileCounter++);
                std::string replacement = "for (int " + wVar + " = 0; " + wVar + " < 256; " + wVar + "++) {\n    if (!(" + cond + ")) break;" +
                    whileBody + "\n}";
                code.replace(pos, braceEnd - pos, replacement);
                // Don't advance past — handle nested while loops
            }
        };
        fixWhileLoop(helpers);
        fixWhileLoop(body);
    }

    // Strip 'const' from variable declarations where the initializer isn't a true
    // compile-time constant (SkSL is far stricter than GLSL about const)
    {
        auto stripNonConstConst = [](std::string& code) {
            // Find "const TYPE NAME = EXPR;" patterns and strip const if EXPR
            // contains function calls, variable references, or other non-const elements
            static const char* types[] = {
                "float4x4 ", "float3x3 ", "float2x2 ",
                "float4 ", "float3 ", "float2 ", "float ",
                "half4 ", "half3 ", "half2 ", "half ",
                "int4 ", "int3 ", "int2 ", "int ",
                "bool ", nullptr
            };
            size_t pos = 0;
            while ((pos = code.find("const ", pos)) != std::string::npos) {
                // Word boundary check on left
                if (pos > 0 && (isalnum(code[pos-1]) || code[pos-1] == '_')) { pos++; continue; }
                size_t afterConst = pos + 6;
                // Check if followed by a type keyword
                bool foundType = false;
                for (const char** tp = types; *tp; ++tp) {
                    size_t tlen = strlen(*tp);
                    if (afterConst + tlen <= code.size() && code.compare(afterConst, tlen, *tp) == 0) {
                        foundType = true;
                        break;
                    }
                }
                if (!foundType) { pos++; continue; }
                // Find the = sign on this line
                size_t nl = code.find('\n', pos);
                if (nl == std::string::npos) nl = code.size();
                size_t eq = code.find('=', afterConst);
                if (eq == std::string::npos || eq >= nl) { pos++; continue; }
                if (eq + 1 < code.size() && code[eq + 1] == '=') { pos++; continue; } // skip ==
                // Get the initializer expression
                size_t exprStart = eq + 1;
                while (exprStart < nl && (code[exprStart] == ' ' || code[exprStart] == '\t')) exprStart++;
                // Find the semicolon
                size_t semi = code.rfind(';', nl);
                if (semi == std::string::npos || semi <= eq) { pos++; continue; }
                std::string expr = code.substr(exprStart, semi - exprStart);
                // Check if the expression is NOT a simple constant
                // A simple constant: only has literals, type constructors, basic arithmetic
                bool isSimpleConst = true;
                // If contains function calls (identifier followed by '('), it's not const
                for (size_t i = 0; i < expr.size(); i++) {
                    if (isalpha(expr[i]) || expr[i] == '_') {
                        size_t ws = i;
                        while (i < expr.size() && (isalnum(expr[i]) || expr[i] == '_')) i++;
                        std::string word = expr.substr(ws, i - ws);
                        // Skip type constructors and built-in consts
                        static const std::unordered_set<std::string> ok = {
                            "float", "float2", "float3", "float4",
                            "float2x2", "float3x3", "float4x4",
                            "int", "int2", "int3", "int4",
                            "half", "half2", "half3", "half4",
                            "bool", "true", "false", "M_PI", "M_E"
                        };
                        if (ok.find(word) != ok.end()) continue;
                        // If followed by (, it's a function call — not const
                        size_t pp = i;
                        while (pp < expr.size() && (expr[pp] == ' ' || expr[pp] == '\t')) pp++;
                        if (pp < expr.size() && expr[pp] == '(') {
                            isSimpleConst = false;
                            break;
                        }
                        // It's a variable reference — not const unless it's a known const
                        // (Conservative: assume any identifier reference makes it non-const)
                        isSimpleConst = false;
                        break;
                    }
                }
                if (!isSimpleConst) {
                    // Strip "const " (6 chars)
                    code.erase(pos, 6);
                    // Don't advance — recheck from same position
                } else {
                    pos++;
                }
            }
        };
        stripNonConstConst(helpers);
        stripNonConstConst(body);
    }

    // Fix integer literals in float variable declarations (SkSL requires float literals)
    // Converts "float x = 0;" to "float x = 0.0;", "float3 x = float3(0, 1, 0);" etc.
    {
        auto fixIntLiterals = [](std::string& code) {
            // Pattern 1: float TYPE = INTEGER_LITERAL;
            // e.g., "float x = 0;" → "float x = 0.0;"
            static const char* floatTypes[] = {
                "float ", nullptr
            };
            for (const char** ft = floatTypes; *ft; ++ft) {
                size_t pos = 0;
                std::string ftype = *ft;
                while ((pos = code.find(ftype, pos)) != std::string::npos) {
                    // Word boundary check
                    if (pos > 0 && (isalnum(code[pos-1]) || code[pos-1] == '_')) { pos++; continue; }
                    // Skip float2, float3, float4, float2x2, etc.
                    size_t afterType = pos + ftype.size();
                    if (afterType < code.size() && (isdigit(code[afterType]) || code[afterType] == 'x')) { pos++; continue; }
                    // Find = sign (assignment, not == comparison)
                    size_t eqPos = code.find('=', afterType);
                    if (eqPos == std::string::npos) { pos = afterType; continue; }
                    if (eqPos + 1 < code.size() && code[eqPos + 1] == '=') { pos = afterType; continue; }
                    if (eqPos > 0 && (code[eqPos - 1] == '!' || code[eqPos - 1] == '<' ||
                        code[eqPos - 1] == '>' || code[eqPos - 1] == '+' ||
                        code[eqPos - 1] == '-' || code[eqPos - 1] == '*' ||
                        code[eqPos - 1] == '/')) { pos = afterType; continue; }
                    // Make sure we're on the same line
                    size_t nl = code.find('\n', afterType);
                    if (nl != std::string::npos && nl < eqPos) { pos = nl; continue; }
                    // Check the value after =
                    size_t valStart = eqPos + 1;
                    while (valStart < code.size() && (code[valStart] == ' ' || code[valStart] == '\t')) valStart++;
                    // Check for bare integer literal (optional sign + digits + ;)
                    size_t vi = valStart;
                    if (vi < code.size() && (code[vi] == '-' || code[vi] == '+')) vi++;
                    if (vi >= code.size() || !isdigit(code[vi])) { pos = afterType; continue; }
                    size_t numStart = vi;
                    while (vi < code.size() && isdigit(code[vi])) vi++;
                    // Must be followed by ; (with optional whitespace)
                    size_t si = vi;
                    while (si < code.size() && (code[si] == ' ' || code[si] == '\t')) si++;
                    if (si >= code.size() || code[si] != ';') { pos = afterType; continue; }
                    // It's a bare integer literal assigned to float — add .0
                    code.insert(vi, ".0");
                    pos = vi + 2;
                }
            }

            // Pattern 2: integer literals inside float constructors: float2(0, 1) → float2(0.0, 1.0)
            static const char* ctors[] = {"float2(", "float3(", "float4(", nullptr};
            for (const char** ct = ctors; *ct; ++ct) {
                size_t pos = 0;
                std::string ctor = *ct;
                while ((pos = code.find(ctor, pos)) != std::string::npos) {
                    if (pos > 0 && (isalnum(code[pos-1]) || code[pos-1] == '_')) { pos++; continue; }
                    size_t start = pos + ctor.size();
                    // Find matching )
                    int depth = 1;
                    size_t p = start;
                    while (p < code.size() && depth > 0) {
                        if (code[p] == '(') depth++;
                        else if (code[p] == ')') depth--;
                        p++;
                    }
                    if (depth != 0) { pos++; continue; }
                    // Scan inside the constructor for bare integer literals
                    // Work backwards to avoid index shifting issues
                    size_t end = p - 1; // position of )
                    for (size_t i = end; i > start; ) {
                        i--;
                        if (!isdigit(code[i])) continue;
                        // Found a digit — scan backwards to get the full number
                        size_t numEnd = i + 1;
                        while (i > start && isdigit(code[i-1])) i--;
                        // Check left boundary: must be , or ( or whitespace or operator
                        // Exclude [ (array subscript) and letters/digits (part of identifier)
                        if (i > 0 && (isalpha(code[i-1]) || code[i-1] == '_' || code[i-1] == '.' || code[i-1] == '[')) continue;
                        // Check right boundary: must be , or ) or whitespace or operator
                        // Exclude ] (array subscript), . (decimal), letters (identifier)
                        if (numEnd < code.size() && (code[numEnd] == '.' || isalpha(code[numEnd]) || code[numEnd] == '_' || code[numEnd] == ']')) continue;
                        // This is a bare integer literal — add .0
                        code.insert(numEnd, ".0");
                        end += 2; // adjust for insertion
                        p += 2;
                    }
                    pos = p;
                }
            }
        };
        fixIntLiterals(helpers);
        fixIntLiterals(body);
        for (auto& dd : deferredDefines) {
            fixIntLiterals(dd);
        }
    }

    // Fix int/float type mismatches (SkSL has no implicit int↔float conversion)
    {
        struct IntVar { std::string name; std::string floatType; };
        static const char* itypes[] = {"const int4 ", "const int3 ", "const int2 ", "const int ", "int4 ", "int3 ", "int2 ", "int ", nullptr};
        static const char* ftypes[] = {"float4", "float3", "float2", "float", "float4", "float3", "float2", "float"};

        // Collect int vars from a given string
        auto collectIntVars = [](const std::string& code) -> std::vector<IntVar> {
            std::vector<IntVar> vars;
            for (int ti = 0; itypes[ti]; ti++) {
                std::string itype = itypes[ti];
                size_t pos = 0;
                while ((pos = code.find(itype, pos)) != std::string::npos) {
                    if (pos > 0 && (isalnum(code[pos-1]) || code[pos-1] == '_')) { pos++; continue; }
                    size_t ns = pos + itype.size();
                    while (ns < code.size() && (code[ns] == ' ' || code[ns] == '\t')) ns++;
                    if (ns >= code.size() || !(isalpha(code[ns]) || code[ns] == '_')) { pos++; continue; }
                    size_t ne = ns;
                    while (ne < code.size() && (isalnum(code[ne]) || code[ne] == '_')) ne++;
                    std::string name = code.substr(ns, ne - ns);
                    size_t an = ne;
                    while (an < code.size() && (code[an] == ' ' || code[an] == '\t')) an++;
                    if (an < code.size() && code[an] == '(') { pos = ne; continue; }
                    if (an < code.size() && code[an] == '[') { pos = ne; continue; }
                    vars.push_back({name, ftypes[ti]});
                    // Also collect comma-separated variables: int w, z = 0;
                    {
                        size_t sc = an;
                        // Skip past initializer if present
                        if (sc < code.size() && code[sc] == '=') {
                            // Skip to next , or ;
                            while (sc < code.size() && code[sc] != ',' && code[sc] != ';' && code[sc] != '\n') sc++;
                        }
                        while (sc < code.size() && code[sc] == ',') {
                            sc++; // skip comma
                            while (sc < code.size() && (code[sc] == ' ' || code[sc] == '\t')) sc++;
                            if (sc < code.size() && (isalpha(code[sc]) || code[sc] == '_')) {
                                size_t cs = sc;
                                while (sc < code.size() && (isalnum(code[sc]) || code[sc] == '_')) sc++;
                                std::string cname = code.substr(cs, sc - cs);
                                // Skip if followed by ( — it's a function
                                size_t ca = sc;
                                while (ca < code.size() && (code[ca] == ' ' || code[ca] == '\t')) ca++;
                                if (ca < code.size() && code[ca] == '(') continue;
                                vars.push_back({cname, ftypes[ti]});
                                // Skip past initializer if present
                                if (ca < code.size() && code[ca] == '=') {
                                    sc = ca;
                                    while (sc < code.size() && code[sc] != ',' && code[sc] != ';' && code[sc] != '\n') sc++;
                                }
                            }
                        }
                    }
                    pos = ne;
                }
            }
            return vars;
        };

        // Collect float vars from a given string
        auto collectFloatVars = [](const std::string& code) -> std::unordered_set<std::string> {
            std::unordered_set<std::string> result;
            static const char* ftypesDecl[] = {"float4 ", "float3 ", "float2 ", "float ", nullptr};
            for (int ti = 0; ftypesDecl[ti]; ti++) {
                std::string ftype = ftypesDecl[ti];
                size_t pos = 0;
                while ((pos = code.find(ftype, pos)) != std::string::npos) {
                    if (pos > 0 && (isalnum(code[pos-1]) || code[pos-1] == '_')) { pos++; continue; }
                    size_t ns = pos + ftype.size();
                    while (ns < code.size() && (code[ns] == ' ' || code[ns] == '\t')) ns++;
                    if (ns >= code.size() || !(isalpha(code[ns]) || code[ns] == '_')) { pos++; continue; }
                    size_t ne = ns;
                    while (ne < code.size() && (isalnum(code[ne]) || code[ne] == '_')) ne++;
                    std::string name = code.substr(ns, ne - ns);
                    size_t an = ne;
                    while (an < code.size() && (code[an] == ' ' || code[an] == '\t')) an++;
                    if (an < code.size() && code[an] == '(') { pos = ne; continue; }
                    result.insert(name);
                    pos = ne;
                }
            }
            return result;
        };

        // Collect from helpers, body, AND deferredDefines
        auto intVarsH = collectIntVars(helpers);
        auto intVarsB = collectIntVars(body);
        std::vector<IntVar> allIntVars;
        allIntVars.insert(allIntVars.end(), intVarsH.begin(), intVarsH.end());
        allIntVars.insert(allIntVars.end(), intVarsB.begin(), intVarsB.end());
        // Also collect from deferredDefines (non-const globals moved into main)
        for (const auto& dd : deferredDefines) {
            auto intVarsD = collectIntVars(dd);
            allIntVars.insert(allIntVars.end(), intVarsD.begin(), intVarsD.end());
        }

        auto floatVarsH = collectFloatVars(helpers);
        auto floatVarsB = collectFloatVars(body);
        std::unordered_set<std::string> allFloatVars;
        allFloatVars.insert(floatVarsH.begin(), floatVarsH.end());
        allFloatVars.insert(floatVarsB.begin(), floatVarsB.end());
        for (const auto& dd : deferredDefines) {
            auto floatVarsD = collectFloatVars(dd);
            allFloatVars.insert(floatVarsD.begin(), floatVarsD.end());
        }

        auto fixIntFloatMixing = [&allIntVars, &allFloatVars](std::string& code) {
            // Use pre-collected int and float vars from both scopes
            const auto& intVars = allIntVars;
            const auto& floatVarNames = allFloatVars;

            // Helper: check if char is a binary operator
            auto isBinOp = [](char c) -> bool {
                return c == '*' || c == '/' || c == '+' || c == '-' ||
                       c == '<' || c == '>' || c == '=';
            };

            // Helper: check if a number at position has a decimal point (is float)
            auto isFloatLiteral = [](const std::string& code, size_t start, size_t end) -> bool {
                for (size_t i = start; i < end; i++) {
                    if (code[i] == '.') return true;
                }
                return false;
            };

            // Helper: extract an identifier ending at position (scan backwards)
            auto extractIdentBefore = [](const std::string& code, size_t endPos) -> std::string {
                size_t e = endPos;
                // skip trailing ++ or --
                if (e >= 2 && ((code[e-1] == '+' && code[e-2] == '+') || (code[e-1] == '-' && code[e-2] == '-'))) e -= 2;
                while (e > 0 && (code[e-1] == ' ' || code[e-1] == '\t')) e--;
                size_t s = e;
                while (s > 0 && (isalnum(code[s-1]) || code[s-1] == '_')) s--;
                return code.substr(s, e - s);
            };

            // Helper: extract an identifier starting at position (scan forwards)
            auto extractIdentAfter = [](const std::string& code, size_t startPos) -> std::string {
                size_t s = startPos;
                while (s < code.size() && (code[s] == ' ' || code[s] == '\t')) s++;
                if (s >= code.size() || !(isalpha(code[s]) || code[s] == '_')) return "";
                size_t e = s;
                while (e < code.size() && (isalnum(code[e]) || code[e] == '_')) e++;
                return code.substr(s, e - s);
            };

            // For each int variable, wrap in float() when used in mixed-type expressions
            for (const auto& iv : intVars) {
                size_t pos = 0;
                while ((pos = code.find(iv.name, pos)) != std::string::npos) {
                    bool lOk = (pos == 0 || !(isalnum(code[pos-1]) || code[pos-1] == '_'));
                    size_t end = pos + iv.name.size();
                    bool rOk = (end >= code.size() || !(isalnum(code[end]) || code[end] == '_'));
                    if (!lOk || !rOk) { pos++; continue; }
                    // Skip swizzle components (e.g. ".x", ".y") — not the int variable
                    if (pos > 0 && code[pos-1] == '.') { pos++; continue; }
                    // Skip declaration lines (line starts with int type)
                    {
                        size_t ls = code.rfind('\n', pos);
                        ls = (ls == std::string::npos) ? 0 : ls + 1;
                        std::string lb = code.substr(ls, pos - ls);
                        size_t fb = lb.find_first_not_of(" \t");
                        if (fb != std::string::npos) {
                            std::string tl = lb.substr(fb);
                            for (int ti2 = 0; itypes[ti2]; ti2++) {
                                if (tl.compare(0, strlen(itypes[ti2]), itypes[ti2]) == 0) {
                                    goto skip_var;
                                }
                            }
                        }
                    }
                    {
                        // Get the full expression: var possibly followed by swizzle
                        size_t checkEnd = end;
                        if (checkEnd < code.size() && code[checkEnd] == '.') {
                            checkEnd++;
                            while (checkEnd < code.size() && isalpha(code[checkEnd])) checkEnd++;
                        }
                        // Check right context: OP followed by float value
                        size_t after = checkEnd;
                        while (after < code.size() && (code[after] == ' ' || code[after] == '\t')) after++;
                        bool needsCast = false;
                        if (after < code.size() && isBinOp(code[after])) {
                            // Skip ==, ++, --, !=, <=, >=, etc. (two-char ops that aren't mixed-type)
                            char op = code[after];
                            size_t opEnd = after + 1;
                            // Skip ++ and --
                            if (opEnd < code.size() && ((op == '+' && code[opEnd] == '+') || (op == '-' && code[opEnd] == '-'))) goto skip_var;
                            // For == comparisons, the int var needs cast if the other side is float
                            // For = (assignment), skip — we don't cast the target
                            if (op == '=' && (opEnd >= code.size() || code[opEnd] != '=')) goto skip_var;
                            while (opEnd < code.size() && (code[opEnd] == '=' || code[opEnd] == '<' || code[opEnd] == '>')) opEnd++; // skip compound ops
                            while (opEnd < code.size() && (code[opEnd] == ' ' || code[opEnd] == '\t')) opEnd++;
                            // Scan for float literal or float-typed identifier after the operator
                            size_t ns = opEnd;
                            if (ns < code.size() && (code[ns] == '-' || code[ns] == '+')) ns++;
                            size_t numStart = ns;
                            while (ns < code.size() && (isdigit(code[ns]) || code[ns] == '.')) ns++;
                            if (ns > numStart && isFloatLiteral(code, numStart, ns)) needsCast = true;
                            // Also check if it's a known float variable or float type constructor
                            if (!needsCast) {
                                std::string rWord = extractIdentAfter(code, opEnd);
                                if (rWord == "float" || rWord == "float2" || rWord == "float3" || rWord == "float4") needsCast = true;
                                if (!needsCast && floatVarNames.count(rWord)) needsCast = true;
                            }
                        }
                        // Check left context: float_literal OP intVar
                        if (!needsCast) {
                            size_t before = pos;
                            while (before > 0 && (code[before-1] == ' ' || code[before-1] == '\t')) before--;
                            if (before > 0 && isBinOp(code[before-1])) {
                                char op = code[before-1];
                                if (op == '+' && before >= 2 && code[before-2] == '+') goto skip_var;
                                if (op == '-' && before >= 2 && code[before-2] == '-') goto skip_var;
                                // Scan backwards past the operator (including compound: +=, -=, *=, /=, ==, !=, <=, >=)
                                size_t opStart = before - 1;
                                while (opStart > 0 && (code[opStart-1] == '=' || code[opStart-1] == '!' || code[opStart-1] == '<' || code[opStart-1] == '>' || code[opStart-1] == '+' || code[opStart-1] == '-' || code[opStart-1] == '*' || code[opStart-1] == '/')) opStart--;
                                // Scan backwards for a number or float variable
                                size_t ne2 = opStart;
                                while (ne2 > 0 && (code[ne2-1] == ' ' || code[ne2-1] == '\t')) ne2--;
                                size_t ns2 = ne2;
                                while (ns2 > 0 && (isdigit(code[ns2-1]) || code[ns2-1] == '.')) ns2--;
                                if (ne2 > ns2 && isFloatLiteral(code, ns2, ne2)) needsCast = true;
                                // Also check for float variable (possibly with ++ or --)
                                if (!needsCast) {
                                    std::string lWord = extractIdentBefore(code, opStart);
                                    if (floatVarNames.count(lWord)) needsCast = true;
                                }
                            }
                        }
                        if (needsCast) {
                            std::string castType;
                            if (checkEnd > end) {
                                size_t swizzleLen = checkEnd - end - 1;
                                if (swizzleLen == 1) castType = "float";
                                else if (swizzleLen == 2) castType = "float2";
                                else if (swizzleLen == 3) castType = "float3";
                                else castType = "float4";
                            } else {
                                castType = iv.floatType;
                            }
                            std::string varExpr = code.substr(pos, checkEnd - pos);
                            code.replace(pos, checkEnd - pos, castType + "(" + varExpr + ")");
                            pos += castType.size() + varExpr.size() + 2;
                            continue;
                        }
                    }
                    skip_var:
                    pos = end;
                }
            }

            // Handle: FLOAT_VAR = int(EXPR); → FLOAT_VAR = float(int(EXPR));
            // Matches assignments where LHS is float-typed (.r, .g, .b, .x, etc.)
            // and RHS starts with int(
            {
                size_t pos = 0;
                while ((pos = code.find("= int(", pos)) != std::string::npos) {
                    // Check it's not ==
                    if (pos > 0 && code[pos-1] == '=') { pos += 6; continue; }
                    if (pos > 0 && code[pos-1] == '!') { pos += 6; continue; }
                    size_t intStart = pos + 2; // position of 'i' in "int("
                    // Word boundary on left of int
                    if (intStart > 0 && (isalnum(code[intStart-1]) || code[intStart-1] == '_')) { pos += 6; continue; }
                    // Check if LHS is a float variable (scan backwards to find the assignment target)
                    // Just look for .r, .g, .b, .x, .y, .z, .w or a float-typed var before =
                    // Simple heuristic: if the line doesn't start with "int ", wrap
                    size_t ls = code.rfind('\n', pos);
                    ls = (ls == std::string::npos) ? 0 : ls + 1;
                    std::string lb = code.substr(ls, pos - ls);
                    size_t fb = lb.find_first_not_of(" \t");
                    bool isIntDecl = false;
                    if (fb != std::string::npos) {
                        std::string tl = lb.substr(fb);
                        for (int ti2 = 0; itypes[ti2]; ti2++) {
                            if (tl.compare(0, strlen(itypes[ti2]), itypes[ti2]) == 0) {
                                isIntDecl = true;
                                break;
                            }
                        }
                    }
                    // Also check if LHS variable is a known int variable
                    if (!isIntDecl) {
                        // Extract identifier before "= int("
                        size_t eq = pos; // position of '='
                        size_t e2 = eq;
                        while (e2 > 0 && (code[e2-1] == ' ' || code[e2-1] == '\t')) e2--;
                        size_t ne2 = e2;
                        while (ne2 > 0 && (isalnum(code[ne2-1]) || code[ne2-1] == '_')) ne2--;
                        std::string lhsVar = code.substr(ne2, e2 - ne2);
                        if (!lhsVar.empty()) {
                            for (const auto& iv2 : intVars) {
                                if (iv2.name == lhsVar) {
                                    isIntDecl = true;
                                    break;
                                }
                            }
                        }
                    }
                    if (!isIntDecl) {
                        // Find matching ) for int(
                        int depth = 1;
                        size_t p = intStart + 4;
                        while (p < code.size() && depth > 0) {
                            if (code[p] == '(') depth++;
                            else if (code[p] == ')') depth--;
                            p++;
                        }
                        if (depth == 0) {
                            // Wrap: int(EXPR) → float(int(EXPR))
                            code.insert(p, ")");
                            code.insert(intStart, "float(");
                            pos = p + 7;
                            continue;
                        }
                    }
                    pos += 6;
                }
            }
        };
        fixIntFloatMixing(helpers);
        fixIntFloatMixing(body);
        for (auto& dd : deferredDefines) {
            fixIntFloatMixing(dd);
        }
    }

    // Convert int arrays to float arrays (shader lookup tables are typically used in float arithmetic)
    // int arr[N] → float arr[N]  (SkSL doesn't allow implicit int→float from array access)
    {
        auto convertIntArrays = [](std::string& code) {
            size_t pos = 0;
            while ((pos = code.find("int ", pos)) != std::string::npos) {
                if (pos > 0 && (isalnum(code[pos-1]) || code[pos-1] == '_')) { pos++; continue; }
                // Skip int2, int3, int4
                if (pos + 4 < code.size() && isdigit(code[pos+3])) { pos++; continue; }
                size_t ns = pos + 4;
                while (ns < code.size() && (code[ns] == ' ' || code[ns] == '\t')) ns++;
                if (ns >= code.size() || !(isalpha(code[ns]) || code[ns] == '_')) { pos++; continue; }
                size_t ne = ns;
                while (ne < code.size() && (isalnum(code[ne]) || code[ne] == '_')) ne++;
                // Check if followed by [
                size_t ab = ne;
                while (ab < code.size() && (code[ab] == ' ' || code[ab] == '\t')) ab++;
                if (ab >= code.size() || code[ab] != '[') { pos = ne; continue; }
                // It's an int array — convert to float
                code.replace(pos, 3, "float");
                pos += 5; // "float" is 5 chars vs "int" 3 chars
            }
        };
        convertIntArrays(helpers);
        convertIntArrays(body);
    }

    // Fix array sizes that are float expressions (SkSL requires int array sizes)
    // Convert float literal array sizes like [3.0] to [3] (manual, no regex)
    fixFloatArraySubscripts(helpers);
    fixFloatArraySubscripts(body);

    _tLog("after float array fix");
    // Handle varyings: add _gp parameter to helper functions, update call sites
    fixVaryingsInHelpers(helpers, body, varyingInputs);

    _tLog("after varyings fix");
    // Handle gl_FragCoord in main body
    replaceAllWord(body, "gl_FragCoord.xy", "_coord");
    replaceAllWord(body, "gl_FragCoord", "float4(_coord, 0.0, 1.0)");

    // FragColor → _fragColor (main body only)
    replaceAllWord(body, "FragColor", "_fragColor");

    // Convert bare "return;" in main body to "return half4(_fragColor);" (manual, no regex)
    replaceBareReturns(body);

    // Check which varyings are re-declared as locals in main body with the SAME type.
    // If redeclared with a different type (e.g., varying float4 t1 vs local float t1),
    // we still declare the varying and rename the local to avoid conflict.
    std::unordered_set<std::string> varyingsRedeclaredInBody;
    for (const auto& [name, type] : varyingInputs) {
        // Check for SAME-type redeclaration: "TYPE name" where TYPE matches the varying type
        std::string sameTypePattern = type + " " + name;
        size_t pos = 0;
        bool sameType = false;
        while ((pos = body.find(sameTypePattern, pos)) != std::string::npos) {
            bool leftOk = (pos == 0 || !(isalnum(body[pos-1]) || body[pos-1] == '_'));
            size_t afterName = pos + sameTypePattern.size();
            if (leftOk && afterName < body.size() &&
                (body[afterName] == '=' || body[afterName] == ';' || body[afterName] == ',' || body[afterName] == ' ')) {
                sameType = true;
                break;
            }
            pos++;
        }
        if (sameType) {
            varyingsRedeclaredInBody.insert(name);
            continue;
        }

        // Check for DIFFERENT-type redeclaration: rename the local to _local_NAME
        static const char* types[] = {
            "float4", "float3", "float2", "float",
            "int4", "int3", "int2", "int",
            "half4", "half3", "half2", "half",
            "bool", nullptr
        };
        for (const char** t = types; *t; ++t) {
            if (std::string(*t) == type) continue; // same type, already checked
            std::string pattern = std::string(*t) + " " + name;
            pos = 0;
            while ((pos = body.find(pattern, pos)) != std::string::npos) {
                bool leftOk = (pos == 0 || !(isalnum(body[pos-1]) || body[pos-1] == '_'));
                size_t afterName = pos + pattern.size();
                if (leftOk && afterName < body.size() &&
                    (body[afterName] == '=' || body[afterName] == ';' || body[afterName] == ',' || body[afterName] == ' ')) {
                    // Rename this local declaration: "TYPE name" → "TYPE _local_name"
                    std::string newLocal = std::string(*t) + " _local_" + name;
                    body.replace(pos, pattern.size(), newLocal);
                    // Also rename all subsequent uses of this local variable in body
                    // (after the declaration point)
                    size_t renameFrom = pos + newLocal.size();
                    std::string oldName = name;
                    std::string newName = "_local_" + name;
                    // Simple word-boundary replacement from the declaration point onward
                    size_t rp = renameFrom;
                    while ((rp = body.find(oldName, rp)) != std::string::npos) {
                        bool lOk = (rp == 0 || !(isalnum(body[rp-1]) || body[rp-1] == '_'));
                        size_t ep = rp + oldName.size();
                        bool rOk = (ep >= body.size() || !(isalnum(body[ep]) || body[ep] == '_'));
                        if (lOk && rOk) {
                            // Check if this use is AFTER a varying-type use of the same name
                            // by checking if it's the varying swizzle (.xy, .zw etc.)
                            // If the char after the name is '.', it's likely the varying
                            if (ep < body.size() && body[ep] == '.' &&
                                type.find("float4") != std::string::npos) {
                                // This looks like a varying access (e.g., t1.xy) — skip
                                rp = ep;
                                continue;
                            }
                            body.replace(rp, oldName.size(), newName);
                            rp += newName.size();
                        } else {
                            rp++;
                        }
                    }
                    break;
                }
                pos++;
            }
        }
    }

    // Emit processed helpers
    out << helpers << "\n";

    // Emit SkSL main function
    out << "half4 main(float2 _coord) {\n";

    // Define varyings, using vertex shader expressions when available
    // Apply type/semantic conversions to captured vertex shader expressions
    for (auto& [vn, expr] : varyingInitExprs) {
        applyTypeConversions(expr);
        stripDotPrefix(expr, "params");
        stripDotPrefix(expr, "param");
        stripDotPrefix(expr, "global");
        stripDotPrefix(expr, "registers");
        applySemanticConversions(expr, luts);
    }
    // Replace vertex-only inputs in captured expressions
    for (auto& [vn, expr] : varyingInitExprs) {
        // TexCoord is the vertex UV input → maps to _coord / outputSize
        if (expr == "TexCoord" || expr == "TexCoord.xy") {
            expr = "_coord / outputSize";
        }
        // Replace remaining TexCoord references within expressions
        {
            size_t p = 0;
            while ((p = expr.find("TexCoord", p)) != std::string::npos) {
                bool lb = (p == 0 || !(isalnum(expr[p-1]) || expr[p-1] == '_'));
                bool rb = (p + 8 >= expr.size() || !(isalnum(expr[p+8]) || expr[p+8] == '_'));
                if (lb && rb) {
                    expr.replace(p, 8, "(_coord / outputSize)");
                    p += 21;
                } else {
                    p++;
                }
            }
        }
    }
    for (const auto& [name, type] : varyingInputs) {
        if (varyingsRedeclaredInBody.count(name)) continue;
        auto it = varyingInitExprs.find(name);
        if (it != varyingInitExprs.end()) {
            // Use the captured vertex shader expression
            out << "    " << type << " " << name << " = " << it->second << ";\n";
        } else if (type == "float2") {
            out << "    " << type << " " << name << " = _coord / outputSize;\n";
        } else if (type == "float") {
            out << "    " << type << " " << name << " = 0.0;\n";
        } else {
            out << "    " << type << " " << name << " = " << type << "(0.0);\n";
        }
    }
    // Fallback: if no varyings detected but body uses vTexCoord, define it
    if (varyingInputs.empty() && body.find("vTexCoord") != std::string::npos) {
        out << "    float2 vTexCoord = _coord / outputSize;\n";
    }

    out << "    float4 _fragColor = float4(0.0);\n";

    // Emit deferred (non-const) #define values, with semantic conversions applied
    for (auto d : deferredDefines) {
        applySemanticConversions(d, luts);
        out << "    " << d << "\n";
    }

    out << body;
    out << "    _fragColor.a = 1.0;\n";
    out << "    return half4(_fragColor);\n";
    out << "}\n";

    std::string result = out.str();

    if (debugLog) {
        ALOGD("GammaCustomShader: transpiled SkSL (%zu bytes) for pass %d", result.size(), passIndex);
    }
    return result;
}

// ---------------------------------------------------------------------------
// Load, transpile, compile
// ---------------------------------------------------------------------------

bool loadAndCompilePreset(SlangPreset& preset) {
    const bool debugLog = GetBoolProperty("persist.gammaos.shader.debug", false);

    if (preset.passes.empty()) return false;

    // First pass: extract parameters from all .slang files
    std::unordered_set<std::string> seenParams;
    for (auto& pass : preset.passes) {
        std::string content = readFileToString(pass.shaderPath);
        if (content.empty()) {
            ALOGE("GammaCustomShader: cannot read %s", pass.shaderPath.c_str());
            return false;
        }
        // Resolve #include before extracting parameters (params may be in included files)
        std::string resolvedContent = resolveIncludes(content, dirName(pass.shaderPath));
        auto fileParams = extractPragmaParams(resolvedContent);
        for (auto& p : fileParams) {
            if (seenParams.count(p.id)) continue;
            seenParams.insert(p.id);
            preset.params.push_back(std::move(p));
        }
    }

    // Load parameter overrides from the preset's kv pairs
    {
        std::string content = readFileToString(preset.presetPath);
        std::istringstream stream(content);
        std::string line;
        while (std::getline(stream, line)) {
            line = trim(line);
            if (line.empty() || line[0] == '#') continue;
            auto eq = line.find('=');
            if (eq == std::string::npos) continue;
            std::string key = trim(line.substr(0, eq));
            std::string val = trim(line.substr(eq + 1));
            val = unquote(val);

            for (auto& p : preset.params) {
                if (p.id == key) {
                    p.current = atof(val.c_str());
                    break;
                }
            }
        }
    }

    // Transpile and compile each pass
    for (int i = 0; i < (int)preset.passes.size(); i++) {
        auto& pass = preset.passes[i];

        std::string fragGlsl = extractSlangFragment(pass.shaderPath);
        if (fragGlsl.empty()) {
            // Try the whole file as fragment
            fragGlsl = readFileToString(pass.shaderPath);
        }
        if (fragGlsl.empty()) {
            ALOGE("GammaCustomShader: empty fragment for pass %d (%s)", i, pass.shaderPath.c_str());
            return false;
        }

        // Extract vertex varying assignments for this pass
        auto vtxVaryings = extractVertexVaryings(pass.shaderPath);

        ALOGI("GammaCustomShader: transpiling pass %d (%zu lines, %zu vtx varyings, %s)",
              i, std::count(fragGlsl.begin(), fragGlsl.end(), '\n'),
              vtxVaryings.size(), pass.shaderPath.c_str());
        {
            auto t0 = std::chrono::steady_clock::now();
            pass.sksl = transpileToSkSL(fragGlsl, preset.params, i,
                                         (int)preset.passes.size(), preset.luts,
                                         vtxVaryings);
            auto dt = std::chrono::steady_clock::now() - t0;
            auto ms = std::chrono::duration_cast<std::chrono::milliseconds>(dt).count();
            ALOGI("GammaCustomShader: transpile pass %d took %lldms", i, (long long)ms);
        }

        auto [effect, err] = SkRuntimeEffect::MakeForShader(SkString(pass.sksl.c_str()));
        if (!effect) {
            ALOGE("GammaCustomShader: SkSL compile failed for pass %d: %s",
                  i, err.c_str());
            // Dump the failing SkSL to a file for diagnostics
            {
                std::string dumpPath = "/data/media/0/GammaShader/.shader_debug_sksl";
                std::ofstream dump(dumpPath);
                if (dump.is_open()) {
                    dump << pass.sksl;
                    dump.close();
                    chmod(dumpPath.c_str(), 0666);
                }
                ALOGE("GammaCustomShader: failing SkSL dumped to %s (%zu bytes)",
                      dumpPath.c_str(), pass.sksl.size());
            }
            return false;
        }
        pass.effect = effect;
        // Debug: dump all uniform metadata from compiled effect
        ALOGI("GammaCustomShader: pass %d compiled OK (%s)", i, pass.shaderPath.c_str());
        // Dump successful SkSL too when debug mode is on
        if (debugLog) {
            std::string dumpPath = "/data/media/0/GammaShader/.shader_debug_sksl";
            std::ofstream dump(dumpPath);
            if (dump.is_open()) {
                dump << pass.sksl;
                dump.close();
                chmod(dumpPath.c_str(), 0666);
            }
        }
    }

    ALOGI("GammaCustomShader: Custom shader compiled OK (%zu passes)", preset.passes.size());
    return true;
}

// ---------------------------------------------------------------------------
// Cached preset state
// ---------------------------------------------------------------------------

static std::mutex gPresetMutex;
static SlangPreset gPreset;
static std::string gLastPresetPath;
static uint32_t gFrameCount = 0;

// Reload preset if the path changed or on explicit request
static bool ensurePresetLoaded(bool debugLog) {
    std::string presetPath = resolveStoragePath(
            GetProperty("persist.gammaos.shader.custom.preset", ""));
    if (presetPath.empty()) return false;

    std::lock_guard<std::mutex> lock(gPresetMutex);

    if (presetPath == gLastPresetPath) {
        return gPreset.valid; // already loaded (or already failed)
    }

    // Check if the path is accessible (CE storage might not be ready)
    if (!fileExists(presetPath)) {
        if (debugLog) {
            ALOGD("GammaCustomShader: preset not accessible yet: %s", presetPath.c_str());
        }
        return false;
    }

    ALOGI("GammaCustomShader: loading preset %s", presetPath.c_str());

    SlangPreset newPreset;
    if (!parseSlangPreset(presetPath, newPreset)) {
        ALOGE("GammaCustomShader: failed to parse %s", presetPath.c_str());
        gLastPresetPath = presetPath;
        gPreset.valid = false;
        return false;
    }
    ALOGI("GammaCustomShader: parsed preset OK (%zu passes)", newPreset.passes.size());

    if (!loadAndCompilePreset(newPreset)) {
        ALOGE("GammaCustomShader: failed to compile %s", presetPath.c_str());
        gLastPresetPath = presetPath;
        gPreset.valid = false;
        return false;
    }

    gPreset = std::move(newPreset);
    gLastPresetPath = presetPath;
    gFrameCount = 0;
    ALOGI("GammaCustomShader: loaded %zu passes, %zu params, %zu LUTs",
          gPreset.passes.size(), gPreset.params.size(), gPreset.luts.size());

    // Write parameter metadata so the ShaderControl app can build UI sliders.
    // Format: one line per param: id|desc|initial|min|max|step
    {
        const std::string metaDir = "/data/media/0/GammaShader";
        mkdir(metaDir.c_str(), 0775);
        const std::string metaPath = metaDir + "/.shader_param_meta";
        std::ofstream meta(metaPath);
        if (meta.is_open()) {
            for (const auto& p : gPreset.params) {
                meta << p.id << "|" << p.desc << "|"
                     << p.initial << "|" << p.minimum << "|"
                     << p.maximum << "|" << p.step << "\n";
            }
            meta.close();
            // Make readable by apps
            chmod(metaPath.c_str(), 0664);
        }
        // Clear any stale param overrides
        const std::string paramsPath = metaDir + "/.shader_params";
        std::ofstream clear(paramsPath, std::ios::trunc);
        clear.close();
        chmod(paramsPath.c_str(), 0666);
    }

    return true;
}

// ---------------------------------------------------------------------------
// Compute pass output dimensions
// ---------------------------------------------------------------------------

static void computePassSize(const SlangPass& pass,
                            int srcW, int srcH,
                            int viewW, int viewH,
                            int& outW, int& outH) {
    // X
    switch (pass.scaleTypeX) {
        case 1: // absolute
            outW = pass.absX > 0 ? pass.absX : srcW;
            break;
        case 2: // viewport
            outW = std::max(1, (int)(viewW * pass.scaleX));
            break;
        default: // source
            outW = std::max(1, (int)(srcW * pass.scaleX));
            break;
    }
    // Y
    switch (pass.scaleTypeY) {
        case 1: // absolute
            outH = pass.absY > 0 ? pass.absY : srcH;
            break;
        case 2: // viewport
            outH = std::max(1, (int)(viewH * pass.scaleY));
            break;
        default: // source
            outH = std::max(1, (int)(srcH * pass.scaleY));
            break;
    }
}

// ---------------------------------------------------------------------------
// apply()
// ---------------------------------------------------------------------------

bool GammaCustomShader::apply(SkSurface* dstSurface,
                               SkSurface* srcSurface,
                               SkiaCapture* capture,
                               ui::Dataspace outDataspace,
                               bool isProtected,
                               bool testOverlay,
                               bool /*ctmBfiBlack*/,
                               float /*defaultScanAngleDeg*/) {
    if (!dstSurface || !srcSurface || !capture) return false;

    const bool debugLog = GetBoolProperty("persist.gammaos.shader.debug", false);

    const bool shaderOn = GetBoolProperty("persist.gammaos.shader.enable", false);
    const std::string type = GetProperty("persist.gammaos.shader.type", "crt-simple");
    if (!shaderOn || type != "custom") return false;

    // Don't attempt shader loading until boot is complete — transpilation
    // blocks the RenderEngine thread and prevents system_server from starting
    if (!GetBoolProperty("sys.boot_completed", false)) return false;

    if (!ensurePresetLoaded(debugLog)) return false;

    std::lock_guard<std::mutex> lock(gPresetMutex);
    if (!gPreset.valid || gPreset.passes.empty()) return false;

    // Apply parameter overrides from params file
    // The ShaderControl app writes id=value lines to /data/media/0/GammaShader/.shader_params
    // Since params are inlined as const declarations, changing them requires recompilation.
    {
        static std::string sLastParamsContent;
        const std::string paramsPath = "/data/media/0/GammaShader/.shader_params";
        std::string content = readFileToString(paramsPath);
        if (content != sLastParamsContent) {
            sLastParamsContent = content;
            // Reset all params to initial values first
            for (auto& p : gPreset.params) {
                p.current = p.initial;
            }
            // Parse overrides
            std::istringstream pStream(content);
            std::string pLine;
            while (std::getline(pStream, pLine)) {
                std::string trimLine = trim(pLine);
                if (trimLine.empty() || trimLine[0] == '#') continue;
                size_t eq = trimLine.find('=');
                if (eq == std::string::npos) continue;
                std::string pId = trim(trimLine.substr(0, eq));
                std::string pVal = trim(trimLine.substr(eq + 1));
                for (auto& p : gPreset.params) {
                    if (p.id == pId) {
                        char* end = nullptr;
                        float v = strtof(pVal.c_str(), &end);
                        if (end != pVal.c_str()) {
                            p.current = std::clamp(v, p.minimum, p.maximum);
                        }
                        break;
                    }
                }
            }
            // Recompile with new const values baked in
            if (!loadAndCompilePreset(gPreset)) {
                ALOGE("GammaCustomShader: recompile after param change failed");
                gPreset.valid = false;
                return false;
            }
            ALOGI("GammaCustomShader: recompiled with updated params");
        }
    }

    // Increment frame counter
    gFrameCount++;

    SkCanvas* dstCanvas = capture->tryCapture(dstSurface);
    if (!dstCanvas) return false;

    if (isProtected) {
        // Don't sample protected content, just draw gray overlay
        SkPaint p;
        p.setColor(SkColorSetARGB(255, 128, 128, 128));
        p.setBlendMode(SkBlendMode::kMultiply);
        dstCanvas->save();
        dstCanvas->resetMatrix();
        dstCanvas->drawRect(SkRect::MakeWH(dstSurface->width(), dstSurface->height()), p);
        dstCanvas->restore();
        return true;
    }

    if (testOverlay) {
        SkPaint p;
        p.setColor(SkColorSetARGB(255, 128, 128, 128));
        p.setBlendMode(SkBlendMode::kMultiply);
        dstCanvas->save();
        dstCanvas->resetMatrix();
        dstCanvas->drawRect(SkRect::MakeWH(dstSurface->width(), dstSurface->height()), p);
        dstCanvas->restore();
        return true;
    }

    const int viewW = dstSurface->width();
    const int viewH = dstSurface->height();

    // Start with the source surface image
    sk_sp<SkImage> currentImage = srcSurface->makeImageSnapshot();
    if (!currentImage) return false;

    const int originalW = currentImage->width();
    const int originalH = currentImage->height();

    // Base resolution scale: downsample the source before the shader chain
    // to reduce GPU load. Options: "full" (1.0), "3/4" (0.75), "1/2" (0.5), "1/4" (0.25)
    const std::string resScale = GetProperty("persist.gammaos.shader.custom.res_scale", "full");
    float scaleFactor = 1.0f;
    if (resScale == "3/4" || resScale == "0.75") scaleFactor = 0.75f;
    else if (resScale == "1/2" || resScale == "0.5" || resScale == "0.50") scaleFactor = 0.5f;
    else if (resScale == "1/4" || resScale == "0.25") scaleFactor = 0.25f;
    else if (resScale == "1/3" || resScale == "0.33") scaleFactor = 1.0f/3.0f;

    if (scaleFactor < 1.0f) {
        int scaledW = std::max(1, (int)(originalW * scaleFactor));
        int scaledH = std::max(1, (int)(originalH * scaleFactor));
        SkImageInfo scaledInfo = dstSurface->imageInfo().makeWH(scaledW, scaledH);
        sk_sp<SkSurface> scaledSurface = dstSurface->makeSurface(scaledInfo);
        if (scaledSurface) {
            SkCanvas* sc = scaledSurface->getCanvas();
            SkPaint sp;
            sp.setBlendMode(SkBlendMode::kSrc);
            sc->save();
            sc->resetMatrix();
            sc->scale((float)scaledW / originalW, (float)scaledH / originalH);
            sc->drawImage(currentImage, 0, 0, SkSamplingOptions(SkFilterMode::kNearest), &sp);
            sc->restore();
            currentImage = scaledSurface->makeImageSnapshot();
            if (debugLog) {
                ALOGD("GammaCustomShader: downscaled source %dx%d → %dx%d (scale=%s)",
                      originalW, originalH, scaledW, scaledH, resScale.c_str());
            }
        }
    }

    // Run each pass
    for (int i = 0; i < (int)gPreset.passes.size(); i++) {
        const auto& pass = gPreset.passes[i];
        if (!pass.effect) continue;

        const int srcW = currentImage->width();
        const int srcH = currentImage->height();

        // Determine output size for this pass
        int passW, passH;
        bool isFinalPass = (i == (int)gPreset.passes.size() - 1);
        if (isFinalPass) {
            passW = viewW;
            passH = viewH;
        } else {
            computePassSize(pass, srcW, srcH, viewW, viewH, passW, passH);
        }

        // Build the runtime shader
        SkRuntimeShaderBuilder builder(pass.effect);

        // Bind source texture
        SkFilterMode fm = pass.filterLinear ? SkFilterMode::kLinear : SkFilterMode::kNearest;
        SkMipmapMode mm = pass.mipmap ? SkMipmapMode::kLinear : SkMipmapMode::kNone;
        sk_sp<SkShader> srcShader = currentImage->makeShader(
                SkSamplingOptions(fm, mm));
        if (!srcShader) continue;
        builder.child("src") = srcShader;

        // Bind uniforms
        builder.uniform("outputSize") = SkV2{(float)passW, (float)passH};
        builder.uniform("sourceSize") = SkV2{(float)srcW, (float)srcH};
        builder.uniform("originalSize") = SkV2{(float)originalW, (float)originalH};

        float fc = (float)gFrameCount;
        if (pass.frameCountMod > 0) {
            fc = (float)(gFrameCount % (uint32_t)pass.frameCountMod);
        }
        builder.uniform("frameCount") = fc;

        // Parameters are inlined as const declarations during transpilation,
        // so no uniform binding needed here.

        // Bind LUT textures (if any and if the pass references them)
        // Note: LUT loading is deferred/simplified - we'd need to decode images
        // For now, we bind a 1x1 white pixel as placeholder for LUTs not yet loaded
        for (const auto& lut : gPreset.luts) {
            if (pass.sksl.find(lut.id) == std::string::npos) continue;

            // TODO: load actual LUT image from lut.path
            // For now, create a 1x1 white placeholder
            SkImageInfo info = SkImageInfo::MakeN32Premul(1, 1);
            uint32_t white = 0xFFFFFFFF;
            sk_sp<SkData> data = SkData::MakeWithCopy(&white, sizeof(white));
            sk_sp<SkImage> lutImage = SkImages::RasterFromData(info, data, sizeof(uint32_t));
            if (lutImage) {
                builder.child(lut.id.c_str()) = lutImage->makeShader(
                        SkSamplingOptions(SkFilterMode::kLinear));
                builder.uniform((lut.id + "Size").c_str()) = SkV2{1.0f, 1.0f};
            }
        }

        // Create the shader
        sk_sp<SkShader> shader = builder.makeShader();
        if (!shader) {
            if (debugLog) ALOGE("GammaCustomShader: makeShader failed for pass %d", i);
            continue;
        }

        if (isFinalPass) {
            // Final pass: draw directly to dst
            SkPaint paint;
            paint.setShader(shader);
            paint.setBlendMode(SkBlendMode::kSrc);
            dstCanvas->save();
            dstCanvas->resetMatrix();
            dstCanvas->drawRect(SkRect::MakeWH(viewW, viewH), paint);
            dstCanvas->restore();
        } else {
            // Intermediate pass: draw to a temporary surface
            SkImageInfo passInfo = dstSurface->imageInfo().makeWH(passW, passH);
            sk_sp<SkSurface> passSurface = dstSurface->makeSurface(passInfo);
            if (!passSurface) {
                if (debugLog) ALOGE("GammaCustomShader: cannot allocate pass %d surface (%dx%d)",
                                    i, passW, passH);
                continue;
            }

            SkCanvas* passCanvas = passSurface->getCanvas();
            SkPaint paint;
            paint.setShader(shader);
            paint.setBlendMode(SkBlendMode::kSrc);
            passCanvas->save();
            passCanvas->resetMatrix();
            passCanvas->drawRect(SkRect::MakeWH(passW, passH), paint);
            passCanvas->restore();

            currentImage = passSurface->makeImageSnapshot();
        }
    }

    if (debugLog) {
        ALOGD("GammaCustomShader: applied %zu passes (frame %u)",
              gPreset.passes.size(), gFrameCount);
    }

    return true;
}

} // namespace skia
} // namespace renderengine
} // namespace android
