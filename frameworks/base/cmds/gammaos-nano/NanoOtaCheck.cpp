/*
 * Copyright (C) 2026 GammaOS
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

// GammaOS OTA online update-check and download. See NanoOtaCheck.h.
//
// The version-compare rule is a 1:1 port of Updater misc/Utils.java:
//
//   isCompatible():  version.compareTo(ro.gammaos.build.version) >= 0    (>= 0, not < 0
//                    which Java rejects) AND (allow_downgrading OR datetime > ro.build.date.utc)
//                    AND romtype.equalsIgnoreCase(ro.lineage.releasetype)
//   canInstall():    (allow_downgrading OR datetime > ro.build.date.utc) AND
//                    version.compareTo(ro.gammaos.build.version) >= 0
//
//   GammaOS relaxed the upstream LineageOS exact-version-match to ">= current" so point
//   releases (1.3.0 -> 1.3.1) upgrade cleanly over OTA. We implement canInstall() (which
//   subsumes isCompatible's version/timestamp checks) plus the romtype match.
//
// The server URL is Utils.getServerURL(): the R.string.updater_server_url template
//   "https://ota.gammaos.sh/api/v1/{device}/{variant}" with {device}=ro.gammaos.device
//   and {variant}=ro.gammaos.variant (a real vendor-set property, NOT derived from the
//   bgN/bvN/bvS build suffix). If either prop is empty the check is skipped, matching Java.

#include "NanoOtaCheck.h"

#include <android-base/properties.h>
#include <android/log.h>

#include <sys/stat.h>
#include <sys/types.h>
#include <sys/wait.h>
#include <unistd.h>
#include <fcntl.h>
#include <errno.h>
#include <signal.h>
#include <time.h>

#include <cctype>
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <string>
#include <vector>

#define NOC_TAG "GammaOSNano"
#define NOC_I(...) __android_log_print(ANDROID_LOG_INFO, NOC_TAG, __VA_ARGS__)
#define NOC_W(...) __android_log_print(ANDROID_LOG_WARN, NOC_TAG, __VA_ARGS__)
#define NOC_E(...) __android_log_print(ANDROID_LOG_ERROR, NOC_TAG, __VA_ARGS__)

namespace nano {

// ---- Properties (mirrors Updater misc/Constants.java) -----------------------
static const char* kPropGammaDevice     = "ro.gammaos.device";          // {device}
static const char* kPropGammaVariant    = "ro.gammaos.variant";         // {variant}
static const char* kPropBuildVersion    = "ro.gammaos.build.version";   // compared to entry "version"
static const char* kPropBuildVerIncr    = "ro.build.version.incremental";// currentVersion fallback
static const char* kPropBuildDateUtc    = "ro.build.date.utc";          // current build timestamp (s)
static const char* kPropReleaseType     = "ro.lineage.releasetype";     // matched to entry "romtype"
static const char* kPropAllowDowngrade  = "lineage.updater.allow_downgrading";
static const char* kPropUpdaterUri      = "lineage.updater.uri";        // optional override template

// R.string.updater_server_url (values/strings.xml). Template placeholders {device}/{variant}.
static const char* kDefaultServerUrl =
        "https://ota.gammaos.sh/api/v1/{device}/{variant}";

static const char* kOtaDir      = "/data/gammaos_ota";
static const char* kPackageDir  = "/data/gammaos_ota/package";

// -----------------------------------------------------------------------------
// Tiny hand-rolled JSON scanning. The manifest is small and flat; we only need
// the first object's string/number fields inside "response".
// -----------------------------------------------------------------------------

// Replace every occurrence of `from` in `s` with `to`.
static void replaceAll(std::string& s, const std::string& from, const std::string& to) {
    if (from.empty()) return;
    size_t pos = 0;
    while ((pos = s.find(from, pos)) != std::string::npos) {
        s.replace(pos, from.size(), to);
        pos += to.size();
    }
}

// Find the byte offset just past the first "response" array's opening '['.
// Returns std::string::npos if not present.
static size_t findResponseArrayStart(const std::string& j) {
    size_t k = j.find("\"response\"");
    if (k == std::string::npos) return std::string::npos;
    size_t br = j.find('[', k);
    if (br == std::string::npos) return std::string::npos;
    return br + 1;
}

// Extract a JSON string value for `key` starting the search at `from`. Handles the
// common escapes we expect in URLs/filenames (\/ \\ \"). Returns true and fills out
// on success; leaves *nextPos at the byte after the closing quote.
static bool scanStringField(const std::string& j, size_t from, const char* key,
                            std::string* out, size_t* nextPos) {
    std::string needle = std::string("\"") + key + "\"";
    size_t k = j.find(needle, from);
    if (k == std::string::npos) return false;
    size_t colon = j.find(':', k + needle.size());
    if (colon == std::string::npos) return false;
    // Skip whitespace to the opening quote.
    size_t p = colon + 1;
    while (p < j.size() && std::isspace(static_cast<unsigned char>(j[p]))) p++;
    if (p >= j.size() || j[p] != '"') return false;
    p++;  // past opening quote
    std::string val;
    while (p < j.size()) {
        char c = j[p];
        if (c == '\\' && p + 1 < j.size()) {
            char n = j[p + 1];
            switch (n) {
                case '/':  val.push_back('/');  break;
                case '\\': val.push_back('\\'); break;
                case '"':  val.push_back('"');  break;
                case 'n':  val.push_back('\n'); break;
                case 't':  val.push_back('\t'); break;
                case 'r':  val.push_back('\r'); break;
                default:   val.push_back(n);    break;
            }
            p += 2;
            continue;
        }
        if (c == '"') { p++; break; }
        val.push_back(c);
        p++;
    }
    *out = val;
    if (nextPos) *nextPos = p;
    return true;
}

// Extract a JSON numeric value for `key` starting the search at `from`.
static bool scanNumberField(const std::string& j, size_t from, const char* key,
                            std::string* out) {
    std::string needle = std::string("\"") + key + "\"";
    size_t k = j.find(needle, from);
    if (k == std::string::npos) return false;
    size_t colon = j.find(':', k + needle.size());
    if (colon == std::string::npos) return false;
    size_t p = colon + 1;
    while (p < j.size() && std::isspace(static_cast<unsigned char>(j[p]))) p++;
    std::string val;
    // Accept leading sign, digits, and a decimal point (sizes/datetimes are integers,
    // but be tolerant).
    if (p < j.size() && (j[p] == '-' || j[p] == '+')) { val.push_back(j[p]); p++; }
    while (p < j.size() && (std::isdigit(static_cast<unsigned char>(j[p])) || j[p] == '.')) {
        val.push_back(j[p]);
        p++;
    }
    if (val.empty() || val == "-" || val == "+") return false;
    *out = val;
    return true;
}

// -----------------------------------------------------------------------------
// Process helpers (fork + execvp, no shell). Returns child exit status (0 == ok),
// or -1 on spawn failure.
// -----------------------------------------------------------------------------
static int runProcess(const std::vector<std::string>& argv) {
    if (argv.empty()) return -1;
    std::vector<char*> cargv;
    cargv.reserve(argv.size() + 1);
    for (const auto& a : argv) cargv.push_back(const_cast<char*>(a.c_str()));
    cargv.push_back(nullptr);

    pid_t pid = fork();
    if (pid < 0) {
        NOC_E("ota: fork failed: %s", strerror(errno));
        return -1;
    }
    if (pid == 0) {
        // Child: silence stdout/stderr, then exec.
        int devnull = open("/dev/null", O_WRONLY);
        if (devnull >= 0) {
            dup2(devnull, STDOUT_FILENO);
            dup2(devnull, STDERR_FILENO);
            if (devnull > STDERR_FILENO) close(devnull);
        }
        execvp(cargv[0], cargv.data());
        _exit(127);  // exec failed
    }
    // Parent.
    int status = 0;
    while (waitpid(pid, &status, 0) < 0) {
        if (errno != EINTR) {
            NOC_E("ota: waitpid failed: %s", strerror(errno));
            return -1;
        }
    }
    if (WIFEXITED(status)) return WEXITSTATUS(status);
    return -1;
}

// Read an entire small file into a string. Returns false on error.
static bool readFileToString(const std::string& path, std::string* out) {
    FILE* f = fopen(path.c_str(), "rb");
    if (!f) return false;
    out->clear();
    char buf[8192];
    size_t n;
    while ((n = fread(buf, 1, sizeof(buf), f)) > 0) {
        out->append(buf, n);
    }
    bool ok = (ferror(f) == 0);
    fclose(f);
    return ok;
}

static uint64_t fileSizeBytes(const std::string& path) {
    struct stat st{};
    if (stat(path.c_str(), &st) != 0) return 0;
    if (!S_ISREG(st.st_mode)) return 0;
    return static_cast<uint64_t>(st.st_size);
}

static bool fileExists(const std::string& path) {
    struct stat st{};
    return stat(path.c_str(), &st) == 0;
}

// Best-effort recursive delete of a directory's contents (not the dir itself).
static void removeTree(const std::string& path) {
    // Use the on-device rm -rf; simpler and robust than a manual walk here.
    // Only ever called on our own /data/gammaos_ota subtree.
    std::vector<std::string> argv = {"/system/bin/rm", "-rf", path};
    runProcess(argv);
}

static void mkdirs(const std::string& path) {
    std::vector<std::string> argv = {"/system/bin/mkdir", "-p", path};
    runProcess(argv);
}

// -----------------------------------------------------------------------------
// Public API
// -----------------------------------------------------------------------------

bool otaCheckForUpdate(OtaUpdateInfo& info) {
    info = OtaUpdateInfo{};

    // Current build identity.
    std::string device  = android::base::GetProperty(kPropGammaDevice, "");
    std::string variant = android::base::GetProperty(kPropGammaVariant, "");

    // currentVersion: ro.gammaos.build.version, fall back to ro.build.version.incremental.
    std::string curVersion = android::base::GetProperty(kPropBuildVersion, "");
    if (curVersion.empty()) {
        curVersion = android::base::GetProperty(kPropBuildVerIncr, "");
    }
    info.currentVersion = curVersion;

    // For the version >= compare, use ro.gammaos.build.version verbatim (no incremental
    // fallback) to match Java Utils.canInstall()/isCompatible() exactly.
    std::string cmpVersion = android::base::GetProperty(kPropBuildVersion, "");

    if (device.empty()) {
        info.error = "ro.gammaos.device is not set";
        NOC_W("ota: %s, skipping check", info.error.c_str());
        return false;
    }
    if (variant.empty()) {
        info.error = "ro.gammaos.variant is not set";
        NOC_W("ota: %s, skipping check", info.error.c_str());
        return false;
    }

    // Build the manifest URL from the template (optionally overridden by the prop).
    std::string tmpl = android::base::GetProperty(kPropUpdaterUri, "");
    // trim
    size_t b = tmpl.find_first_not_of(" \t\r\n");
    if (b == std::string::npos) tmpl.clear();
    else tmpl = tmpl.substr(b, tmpl.find_last_not_of(" \t\r\n") - b + 1);
    if (tmpl.empty()) tmpl = kDefaultServerUrl;

    std::string url = tmpl;
    replaceAll(url, "{device}", device);
    replaceAll(url, "{variant}", variant);
    // Java Utils.getServerURL also substitutes {type} (releasetype, lowercased) and {incr}
    // (build.version.incremental). The default template uses neither, but an override might.
    {
        std::string t = android::base::GetProperty(kPropReleaseType, "");
        for (char& c : t) c = static_cast<char>(tolower(static_cast<unsigned char>(c)));
        replaceAll(url, "{type}", t);
        replaceAll(url, "{incr}", android::base::GetProperty(kPropBuildVerIncr, ""));
    }

    NOC_I("ota: checking %s", url.c_str());

    // Fetch to a temp file with curl. -sfL: silent, fail on HTTP error, follow redirects.
    // A unique temp path keyed on pid avoids collision with a concurrent caller.
    std::string tmpFile = std::string(kOtaDir) + "/manifest_check_" +
                          std::to_string(static_cast<long>(getpid())) + ".json";
    mkdirs(kOtaDir);
    // Remove any stale temp file.
    unlink(tmpFile.c_str());

    std::vector<std::string> curlArgv = {
        "/system/bin/curl",
        "-sfL",
        "--max-time", "20",
        "-o", tmpFile,
        url,
    };
    int rc = runProcess(curlArgv);
    if (rc != 0) {
        unlink(tmpFile.c_str());
        info.error = "network fetch failed (curl rc " + std::to_string(rc) + ")";
        NOC_W("ota: %s for %s", info.error.c_str(), url.c_str());
        return false;
    }

    std::string body;
    if (!readFileToString(tmpFile, &body) || body.empty()) {
        unlink(tmpFile.c_str());
        info.error = "empty or unreadable manifest response";
        NOC_W("ota: %s", info.error.c_str());
        return false;
    }
    unlink(tmpFile.c_str());

    // Locate the response array. An empty array means "no build published".
    size_t respStart = findResponseArrayStart(body);
    if (respStart == std::string::npos) {
        info.error = "malformed manifest (no response array)";
        NOC_W("ota: %s", info.error.c_str());
        return false;
    }
    // If the array is empty ("response":[]) -> successful query, no update.
    {
        size_t p = respStart;
        while (p < body.size() && std::isspace(static_cast<unsigned char>(body[p]))) p++;
        if (p < body.size() && body[p] == ']') {
            NOC_I("ota: server has no build for %s/%s", device.c_str(), variant.c_str());
            info.available = false;
            return true;  // query succeeded
        }
    }

    // Current build reference values.
    long long curDate = 0;
    {
        std::string d = android::base::GetProperty(kPropBuildDateUtc, "0");
        curDate = atoll(d.c_str());
    }
    bool allowDowngrade = android::base::GetBoolProperty(kPropAllowDowngrade, false);
    std::string curReleaseType = android::base::GetProperty(kPropReleaseType, "");

    // Iterate the entries. Track the newest compatible build (highest datetime).
    // Entries are flat JSON objects separated by "},{"; scan each object's fields.
    // We find object boundaries by braces to keep field scans within one entry.
    bool foundAny = false;
    long long bestDate = curDate;  // must beat the current build (unless allowDowngrade)

    size_t pos = respStart;
    while (pos < body.size()) {
        size_t objStart = body.find('{', pos);
        if (objStart == std::string::npos) break;
        // find matching close brace (entries are flat: no nested objects expected,
        // but handle nesting defensively).
        int depth = 0;
        size_t objEnd = std::string::npos;
        for (size_t q = objStart; q < body.size(); ++q) {
            char c = body[q];
            if (c == '{') depth++;
            else if (c == '}') {
                depth--;
                if (depth == 0) { objEnd = q; break; }
            }
        }
        if (objEnd == std::string::npos) break;

        std::string obj = body.substr(objStart, objEnd - objStart + 1);
        pos = objEnd + 1;

        std::string sVersion, sFilename, sUrl, sRomtype;
        std::string sDatetime, sSize;
        scanStringField(obj, 0, "version",  &sVersion,  nullptr);
        scanStringField(obj, 0, "filename", &sFilename, nullptr);
        scanStringField(obj, 0, "url",      &sUrl,      nullptr);
        scanStringField(obj, 0, "romtype",  &sRomtype,  nullptr);
        scanNumberField(obj, 0, "datetime", &sDatetime);
        scanNumberField(obj, 0, "size",     &sSize);

        long long entryDate = atoll(sDatetime.c_str());
        uint64_t  entrySize = static_cast<uint64_t>(strtoull(sSize.c_str(), nullptr, 10));

        // --- Compatibility (port of Utils.canInstall + the romtype check) ---
        // 1) timestamp: newer than current, unless downgrading is allowed.
        bool tsOk = allowDowngrade || (entryDate > curDate);
        // 2) version: entry version must NOT be older than the current version
        //    (String.compareTo >= 0). This is the GammaOS-relaxed rule.
        bool verOk = (strcmp(sVersion.c_str(), cmpVersion.c_str()) >= 0);
        // 3) romtype must match the current release type, case-insensitive.
        bool typeOk = !curReleaseType.empty() &&
                      (strcasecmp(sRomtype.c_str(), curReleaseType.c_str()) == 0);

        if (!(tsOk && verOk && typeOk)) {
            NOC_I("ota: skip entry %s (ts=%d ver=%d type=%d, datetime=%lld cur=%lld)",
                  sFilename.c_str(), tsOk, verOk, typeOk, entryDate, curDate);
            continue;
        }

        // Newest wins.
        if (!foundAny || entryDate > bestDate) {
            foundAny         = true;
            bestDate         = entryDate;
            info.available   = true;
            info.version     = sVersion;
            info.filename    = sFilename;
            info.url         = sUrl;
            info.size        = entrySize;
        }
    }

    if (info.available) {
        NOC_I("ota: update available %s (%s) size=%llu url=%s",
              info.version.c_str(), info.filename.c_str(),
              (unsigned long long)info.size, info.url.c_str());
    } else {
        NOC_I("ota: no compatible update for %s/%s (current %s)",
              device.c_str(), variant.c_str(), curVersion.c_str());
    }
    return true;  // query succeeded regardless of whether an update was found
}

bool otaDownloadAndStage(const OtaUpdateInfo& info,
                         const std::function<void(int, const char*)>& progress) {
    auto report = [&](int pct, const char* phase) {
        if (progress) progress(pct, phase);
    };

    if (info.url.empty() || info.filename.empty()) {
        NOC_E("ota: download requested with empty url/filename");
        report(0, "Invalid update");
        return false;
    }

    // Reject a filename with path separators (never write outside the OTA dir).
    if (info.filename.find('/') != std::string::npos ||
        info.filename.find("..") != std::string::npos) {
        NOC_E("ota: refusing unsafe filename '%s'", info.filename.c_str());
        report(0, "Invalid update");
        return false;
    }

    mkdirs(kOtaDir);

    std::string zipPath = std::string(kOtaDir) + "/" + info.filename;
    // Start from a clean slate for this download.
    unlink(zipPath.c_str());

    report(0, "Downloading");

    // Spawn curl in the background so the caller thread can poll the growing file
    // size against info.size and report download progress (0..90). We do NOT rely
    // on curl's own progress meter (it goes to a /dev/null'd stderr).
    std::vector<std::string> curlArgv = {
        "/system/bin/curl",
        "-fL",                  // fail on HTTP error, follow redirects
        "--connect-timeout", "30",
        "-o", zipPath,
        info.url,
    };

    // Build argv for a manual fork (we want the pid to poll + waitpid).
    std::vector<char*> cargv;
    cargv.reserve(curlArgv.size() + 1);
    for (const auto& a : curlArgv) cargv.push_back(const_cast<char*>(a.c_str()));
    cargv.push_back(nullptr);

    pid_t pid = fork();
    if (pid < 0) {
        NOC_E("ota: fork(curl) failed: %s", strerror(errno));
        report(0, "Download failed");
        return false;
    }
    if (pid == 0) {
        int devnull = open("/dev/null", O_WRONLY);
        if (devnull >= 0) {
            dup2(devnull, STDOUT_FILENO);
            dup2(devnull, STDERR_FILENO);
            if (devnull > STDERR_FILENO) close(devnull);
        }
        execvp(cargv[0], cargv.data());
        _exit(127);
    }

    // Parent: poll while curl runs.
    int status = 0;
    for (;;) {
        pid_t w = waitpid(pid, &status, WNOHANG);
        if (w == pid) break;               // curl exited
        if (w < 0) {
            if (errno == EINTR) continue;
            NOC_E("ota: waitpid(curl) failed: %s", strerror(errno));
            break;
        }
        // Still running: report growing-file progress mapped to 0..90.
        if (info.size > 0) {
            uint64_t have = fileSizeBytes(zipPath);
            if (have > info.size) have = info.size;
            int pct = static_cast<int>((have * 90ULL) / info.size);
            if (pct < 0) pct = 0;
            if (pct > 90) pct = 90;
            report(pct, "Downloading");
        } else {
            report(0, "Downloading");
        }
        // Poll ~4x/sec.
        struct timespec ts { 0, 250L * 1000L * 1000L };
        nanosleep(&ts, nullptr);
    }

    int curlRc = WIFEXITED(status) ? WEXITSTATUS(status) : -1;
    if (curlRc != 0) {
        NOC_E("ota: download failed (curl rc %d) url=%s", curlRc, info.url.c_str());
        unlink(zipPath.c_str());  // clean up partial
        report(0, "Download failed");
        return false;
    }

    // Sanity: the downloaded size should match the manifest if it was provided.
    uint64_t got = fileSizeBytes(zipPath);
    if (got == 0) {
        NOC_E("ota: download produced an empty file");
        unlink(zipPath.c_str());
        report(0, "Download failed");
        return false;
    }
    if (info.size > 0 && got != info.size) {
        NOC_W("ota: size mismatch (got %llu, expected %llu) for %s",
              (unsigned long long)got, (unsigned long long)info.size,
              info.filename.c_str());
        unlink(zipPath.c_str());
        report(0, "Download failed");
        return false;
    }

    NOC_I("ota: downloaded %s (%llu bytes)", zipPath.c_str(), (unsigned long long)got);
    report(90, "Extracting");

    // Fresh package dir.
    removeTree(kPackageDir);
    mkdirs(kPackageDir);

    // Extract with unzip -o <zip> -d <packageDir>.
    std::vector<std::string> unzipArgv = {
        "/system/bin/unzip",
        "-o",
        zipPath,
        "-d", kPackageDir,
    };
    int unzipRc = runProcess(unzipArgv);
    if (unzipRc != 0) {
        NOC_E("ota: unzip failed (rc %d) for %s", unzipRc, zipPath.c_str());
        removeTree(kPackageDir);
        // Leave the (verified) zip in place so a retry can re-extract without
        // re-downloading; but a failed extract usually means a corrupt zip, so
        // drop it to force a clean re-download.
        unlink(zipPath.c_str());
        report(90, "Extract failed");
        return false;
    }

    // Verify the staged package.
    std::string manifest = std::string(kPackageDir) + "/manifest.json";
    if (!fileExists(manifest)) {
        NOC_E("ota: no manifest.json in staged package %s", kPackageDir);
        removeTree(kPackageDir);
        unlink(zipPath.c_str());
        report(90, "Invalid package");
        return false;
    }

    NOC_I("ota: package staged at %s (manifest present)", kPackageDir);
    report(100, "Ready");
    return true;
}

}  // namespace nano
