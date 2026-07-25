/*
 * Copyright (C) 2026 GammaOS
 *
 * WebDAV and FTP backends, both built on libcurl.
 *
 * They share almost everything: one easy handle per backend, guarded by a lock, with reads done as
 * byte-range requests. They differ only in how a directory is listed (a WebDAV PROPFIND returning
 * XML, versus an FTP LIST returning a text listing) and in how the metadata operations are
 * expressed (HTTP verbs versus FTP commands sent as QUOTE).
 *
 * Neither protocol has a real seek, so a read is a fresh ranged request. That is fine for media,
 * which reads forward, and acceptable for copying; it would be poor for random access, which is
 * why SMB is the better backend where the server offers it.
 *
 * The platform libcurl has FTP compiled in for this (see the note in external/curl/lib/
 * curl_config.h). Its ftp.c also brings the reconnect and PASV handling that makes FTP tolerable
 * over a home network.
 */

#define LOG_TAG "gammaos-sharefs-curl"

#include <ctype.h>
#include <errno.h>
#include <log/log.h>
#include <string.h>

#include <algorithm>
#include <mutex>
#include <string>
#include <vector>

#include <curl/curl.h>

#include "sharefs.h"

namespace gammaos {
namespace sharefs {
namespace {

struct Buffer {
    std::string data;
    // For ranged reads: where to copy into, and how much room is left.
    char*  out = nullptr;
    size_t cap = 0;
    size_t got = 0;
};

size_t writeToString(char* ptr, size_t sz, size_t nm, void* user) {
    auto* b = static_cast<Buffer*>(user);
    b->data.append(ptr, sz * nm);
    return sz * nm;
}

size_t writeToBuffer(char* ptr, size_t sz, size_t nm, void* user) {
    auto* b = static_cast<Buffer*>(user);
    size_t n = sz * nm;
    if (b->got + n > b->cap) n = b->cap - b->got;
    if (n) {
        memcpy(b->out + b->got, ptr, n);
        b->got += n;
    }
    // Returning short deliberately aborts the transfer with CURLE_WRITE_ERROR. That is the only way
    // to stop a server that ignored the Range header from streaming the whole file into a buffer
    // sized for one read; readFile treats a full buffer as success, so the abort is not an error.
    return n;
}

struct UploadCtx {
    const char* data = nullptr;
    size_t size = 0;
    size_t sent = 0;
};

size_t readFromBuffer(char* ptr, size_t sz, size_t nm, void* user) {
    auto* u = static_cast<UploadCtx*>(user);
    size_t n = std::min(sz * nm, u->size - u->sent);
    if (n) { memcpy(ptr, u->data + u->sent, n); u->sent += n; }
    return n;
}

// Rewind the upload.
//
// curl replays a request more than once in normal operation: with CURLAUTH_ANY the first attempt
// goes out unauthenticated, the server answers 401 with a challenge, and curl repeats it with
// credentials. A redirect does the same. Without a way to rewind, the second attempt finds the read
// callback already exhausted and sends an EMPTY body, so the file lands on the server at zero
// length and the request comes back as an auth failure. This is what makes a PUT actually work.
int seekUpload(void* user, curl_off_t offset, int origin) {
    auto* u = static_cast<UploadCtx*>(user);
    curl_off_t target;
    switch (origin) {
        case SEEK_SET: target = offset; break;
        case SEEK_CUR: target = static_cast<curl_off_t>(u->sent) + offset; break;
        case SEEK_END: target = static_cast<curl_off_t>(u->size) + offset; break;
        default: return CURL_SEEKFUNC_CANTSEEK;
    }
    if (target < 0 || static_cast<size_t>(target) > u->size) return CURL_SEEKFUNC_FAIL;
    u->sent = static_cast<size_t>(target);
    return CURL_SEEKFUNC_OK;
}

// Percent-encode the parts of a path that would otherwise break a URL. Slashes are kept, since
// they are the path structure.
std::string urlEscapePath(const std::string& p) {
    static const char* hex = "0123456789ABCDEF";
    std::string out;
    for (unsigned char c : p) {
        if (isalnum(c) || strchr("/-_.~", c)) {
            out.push_back(static_cast<char>(c));
        } else {
            out.push_back('%');
            out.push_back(hex[c >> 4]);
            out.push_back(hex[c & 0xF]);
        }
    }
    return out;
}

std::string urlUnescape(const std::string& s) {
    std::string out;
    for (size_t i = 0; i < s.size(); i++) {
        if (s[i] == '%' && i + 2 < s.size() &&
            isxdigit((unsigned char)s[i + 1]) && isxdigit((unsigned char)s[i + 2])) {
            out.push_back(static_cast<char>(strtol(s.substr(i + 1, 2).c_str(), nullptr, 16)));
            i += 2;
        } else {
            out.push_back(s[i]);
        }
    }
    return out;
}

// Drop any scheme and authority, so "http://nas/dav/x" and "/dav/x" both come back as "/dav/x".
// PROPFIND responses use either form depending on the server.
std::string hrefPath(const std::string& href) {
    size_t p = href.find("://");
    if (p == std::string::npos) return href;
    size_t slash = href.find('/', p + 3);
    return slash == std::string::npos ? std::string("/") : href.substr(slash);
}

std::string stripTrailingSlashes(std::string s) {
    while (s.size() > 1 && s.back() == '/') s.pop_back();
    return s;
}

class CurlBackend : public Backend {
public:
    CurlBackend(const ShareConfig& cfg, bool webdav) : mCfg(cfg), mWebdav(webdav) {}
    ~CurlBackend() override { disconnect(); }

    int connect() override {
        std::lock_guard<std::mutex> lk(mLock);
        if (!mCurl) mCurl = curl_easy_init();
        if (!mCurl) return -ENOMEM;
        // Prove the server answers before the mount is published, so a wrong address or password is
        // reported when the share is enabled rather than as mysterious IO errors later.
        std::vector<DirEntry> tmp;
        int rc = readDirLocked("/", &tmp);
        if (rc != 0) {
            ALOGE("%s share '%s' failed to list its root: %d",
                  mWebdav ? "WebDAV" : "FTP", mCfg.name.c_str(), rc);
            mDead = true;
            return rc;
        }
        mDead = false;
        return 0;
    }

    void disconnect() override {
        std::lock_guard<std::mutex> lk(mLock);
        // Cleanup first: the handle still references the slists set on it, and they are ours to
        // free only once nothing can look at them.
        if (mCurl) { curl_easy_cleanup(mCurl); mCurl = nullptr; }
        freeLists();
    }

    int getAttr(const std::string& path, struct stat* out) override {
        memset(out, 0, sizeof(*out));
        if (path == "/" || path.empty()) {
            out->st_mode = S_IFDIR | 0755;
            out->st_nlink = 2;
            return 0;
        }
        // Neither protocol has a cheap stat, so ask the parent directory and pick the entry out.
        // The FUSE layer seeds its cache from readdir, so this is mostly a miss-path fallback.
        size_t slash = path.find_last_of('/');
        std::string parent = path.substr(0, slash);
        if (parent.empty()) parent = "/";
        std::string leaf = path.substr(slash + 1);

        std::vector<DirEntry> entries;
        std::lock_guard<std::mutex> lk(mLock);
        int rc = readDirLocked(parent, &entries);
        if (rc != 0) return rc;
        for (const auto& e : entries) {
            if (e.name != leaf) continue;
            out->st_mode = e.isDir ? (S_IFDIR | 0755) : (S_IFREG | 0644);
            out->st_nlink = e.isDir ? 2 : 1;
            out->st_size = static_cast<off_t>(e.size);
            out->st_mtime = e.mtime;
            out->st_blocks = static_cast<blkcnt_t>((e.size + 511) / 512);
            return 0;
        }
        return -ENOENT;
    }

    int readDir(const std::string& path, std::vector<DirEntry>* out) override {
        std::lock_guard<std::mutex> lk(mLock);
        return readDirLocked(path, out);
    }

    // Read, with readahead.
    //
    // Every read here is a fresh ranged request, and for FTP that means a whole new data connection
    // (PASV/EPSV plus the transfer). Measured on device, one 64KB read costs about 1.5 seconds, so
    // serving FUSE's reads one at a time gives roughly 5 KB/s and a 64MB file would take twenty
    // minutes. Fetching a larger window per round trip and serving the following sequential reads
    // out of it is what makes these protocols usable: playback and copying are both strictly
    // forward, so the window is nearly always hit.
    int readFile(const std::string& path, char* buf, size_t size, off_t offset) override {
        if (size == 0) return 0;
        std::lock_guard<std::mutex> lk(mLock);
        if (!mCurl) return -EIO;

        const uint64_t want = static_cast<uint64_t>(offset);
        // The window must cover the WHOLE request, not merely its start. Serving the tail of a
        // window as a short read looks like end of file to the kernel, which silently truncates
        // what the reader sees: the data is correct as far as it goes and simply stops early. A
        // request that runs past the window falls through and fetches a new one anchored here, so
        // the only short read ever returned is a genuine end of file.
        if (mCachePath == path && want >= mCacheOff &&
            want + size <= mCacheOff + mCache.size()) {
            memcpy(buf, mCache.data() + (want - mCacheOff), size);
            return static_cast<int>(size);
        }

        // Miss: pull a window starting where the caller asked. Anchoring on the request rather than
        // on a fixed grid keeps a seek to an arbitrary offset from fetching data ahead of it.
        const size_t window = std::max(size, readAheadBytes());
        mCache.assign(window, 0);
        Buffer b;
        b.out = mCache.data();
        b.cap = window;

        char range[64];
        snprintf(range, sizeof(range), "%llu-%llu", static_cast<unsigned long long>(want),
                 static_cast<unsigned long long>(want + window - 1));

        prepare(url(path));
        curl_easy_setopt(mCurl, CURLOPT_WRITEFUNCTION, writeToBuffer);
        curl_easy_setopt(mCurl, CURLOPT_WRITEDATA, &b);
        curl_easy_setopt(mCurl, CURLOPT_RANGE, range);
        CURLcode rc = curl_easy_perform(mCurl);

        // A short read at end of file, and the deliberate abort once the buffer is full, are both
        // successful reads as far as the caller is concerned.
        if (rc != CURLE_OK && rc != CURLE_PARTIAL_FILE &&
            !(rc == CURLE_WRITE_ERROR && b.got == b.cap)) {
            mCachePath.clear();
            mCache.clear();
            return mapError(rc);
        }

        mCache.resize(b.got);
        mCachePath = path;
        mCacheOff = want;
        const size_t n = std::min(size, b.got);
        if (n) memcpy(buf, mCache.data(), n);
        return static_cast<int>(n);
    }

    // Anything that changes a file drops the window, or a reader would keep seeing what was there
    // before the write.
    void invalidateCacheLocked(const std::string& path) {
        if (path.empty() || mCachePath == path) { mCachePath.clear(); mCache.clear(); }
    }

    // Stage the chunk. Neither protocol can patch a file in the middle: WebDAV PUT and FTP STOR
    // replace it whole. FUSE, though, delivers even a modest write as a run of chunks at
    // increasing offsets, so writing each chunk straight out would leave only the last one. The
    // chunks are collected here and sent as one body when the file is closed (flushFile).
    int writeFile(const std::string& path, const char* buf, size_t size, off_t offset) override {
        if (mCfg.readOnly) return -EROFS;
        std::lock_guard<std::mutex> lk(mLock);

        if (mStagePath != path) {
            // A different file: whatever was staged belongs to the previous one and its writer
            // never closed it. Push it out rather than silently dropping the data.
            if (!mStagePath.empty()) flushStagedLocked();
            mStagePath = path;
            mStage.clear();
        }
        const size_t end = static_cast<size_t>(offset) + size;
        // Bounded so a runaway or enormous copy cannot take the device down. 64MB covers the
        // realistic case (a ROM, a photo, a song); past that the copy is refused with a clear
        // error instead of the daemon being killed for memory halfway through.
        if (end > kMaxStagedBytes) {
            ALOGE("%s: refusing to stage %zu bytes for '%s' (limit %zu). WebDAV and FTP have to "
                  "upload a file whole, so a larger file cannot be written to this share type.",
                  mCfg.name.c_str(), end, path.c_str(), kMaxStagedBytes);
            mStage.clear();
            mStagePath.clear();
            return -EFBIG;
        }
        if (mStage.size() < end) mStage.resize(end, 0);
        memcpy(mStage.data() + offset, buf, size);
        return static_cast<int>(size);
    }

    // The writer closed the file, so the staged body is complete and can go out as one request.
    int flushFile(const std::string& path) override {
        std::lock_guard<std::mutex> lk(mLock);
        if (mStagePath != path || mStagePath.empty()) return 0;   // nothing staged for this file
        return flushStagedLocked();
    }

    int createFile(const std::string& path, mode_t) override {
        if (mCfg.readOnly) return -EROFS;
        // Create it empty on the server now, so the file exists as soon as it is opened, and start
        // a fresh staging buffer for the writes that follow.
        {
            std::lock_guard<std::mutex> lk(mLock);
            if (!mStagePath.empty() && mStagePath != path) flushStagedLocked();
            mStagePath = path;
            mStage.clear();
        }
        return uploadLocked(path, nullptr, 0);
    }

    int truncateFile(const std::string&, off_t size) override {
        // Only the truncate-to-empty that precedes a rewrite is meaningful here: the file is
        // replaced wholesale by the write that follows.
        return size == 0 ? 0 : -ENOTSUP;
    }

    int unlinkFile(const std::string& path) override {
        if (mCfg.readOnly) return -EROFS;
        std::lock_guard<std::mutex> lk(mLock);
        invalidateCacheLocked(path);
        if (mWebdav) return webdavVerb("DELETE", path, std::string());
        return ftpQuote({"DELE " + remotePath(path)});
    }

    int makeDir(const std::string& path, mode_t) override {
        if (mCfg.readOnly) return -EROFS;
        std::lock_guard<std::mutex> lk(mLock);
        // MKCOL is defined on the collection URL, and servers expect the trailing slash.
        if (mWebdav) return webdavVerb("MKCOL", path + "/", std::string());
        return ftpQuote({"MKD " + remotePath(path)});
    }

    int removeDir(const std::string& path) override {
        if (mCfg.readOnly) return -EROFS;
        std::lock_guard<std::mutex> lk(mLock);
        if (mWebdav) return webdavVerb("DELETE", path + "/", std::string());
        return ftpQuote({"RMD " + remotePath(path)});
    }

    int renamePath(const std::string& from, const std::string& to) override {
        if (mCfg.readOnly) return -EROFS;
        std::lock_guard<std::mutex> lk(mLock);
        invalidateCacheLocked(from);
        invalidateCacheLocked(to);
        if (mWebdav) return webdavVerb("MOVE", from, "Destination: " + url(to));
        return ftpQuote({"RNFR " + remotePath(from), "RNTO " + remotePath(to)});
    }

    int statFs(uint64_t*, uint64_t*) override { return -ENOSYS; }
    bool isDead() const override { return mDead; }

private:
    // Send the staged body and forget it. Caller holds mLock.
    int flushStagedLocked() {
        const std::string path = mStagePath;
        invalidateCacheLocked(path);
        std::vector<char> body;
        body.swap(mStage);
        mStagePath.clear();
        int rc = uploadLocked(path, body.data(), body.size());
        if (rc != 0) ALOGE("upload of '%s' (%zu bytes) failed: %d", path.c_str(), body.size(), rc);
        return rc;
    }

    // One PUT (WebDAV) or STOR (FTP) carrying the whole body. Caller holds mLock.
    int uploadLocked(const std::string& path, const char* data, size_t size) {
        if (!mCurl) return -EIO;
        UploadCtx up{data, size, 0};
        prepare(url(path));
        curl_easy_setopt(mCurl, CURLOPT_UPLOAD, 1L);
        curl_easy_setopt(mCurl, CURLOPT_READFUNCTION, readFromBuffer);
        curl_easy_setopt(mCurl, CURLOPT_READDATA, &up);
        // Without these the 401-then-retry that CURLAUTH_ANY performs uploads an empty body.
        curl_easy_setopt(mCurl, CURLOPT_SEEKFUNCTION, seekUpload);
        curl_easy_setopt(mCurl, CURLOPT_SEEKDATA, &up);
        curl_easy_setopt(mCurl, CURLOPT_INFILESIZE_LARGE, static_cast<curl_off_t>(size));
        CURLcode rc = curl_easy_perform(mCurl);
        if (rc != CURLE_OK) return mapError(rc);
        return 0;
    }

    // ---- URL construction ----

    const char* scheme() const {
        if (mWebdav) return mCfg.useTls ? "https://" : "http://";
        return mCfg.useTls ? "ftps://" : "ftp://";
    }

    // The share's root on the server, always starting with '/' and never ending with one.
    std::string rootPath() const {
        std::string root = mCfg.path;
        if (!root.empty() && root.front() != '/') root.insert(root.begin(), '/');
        while (!root.empty() && root.back() == '/') root.pop_back();
        return root;
    }

    // A share-relative path turned into the server-absolute one. FTP commands sent as QUOTE are
    // interpreted by the server, not by curl, so they need this rather than the mount-relative
    // path: on a share rooted at /volume1/media, deleting "/clip.mp4" is "DELE
    // /volume1/media/clip.mp4".
    std::string remotePath(const std::string& path) const {
        std::string p = path.empty() ? "/" : path;
        if (p.front() != '/') p.insert(p.begin(), '/');
        std::string full = rootPath() + p;
        return full.empty() ? "/" : full;
    }

    std::string base() const {
        std::string u = scheme() + mCfg.host;
        // Only append a port when it is not the protocol's own default, so the URL stays the
        // canonical one a server's virtual-host matching expects.
        int def = mWebdav ? (mCfg.useTls ? 443 : 80) : 21;
        if (mCfg.port > 0 && mCfg.port != def) u += ":" + std::to_string(mCfg.port);
        return u + urlEscapePath(rootPath());
    }

    std::string url(const std::string& path) const {
        std::string p = path.empty() ? "/" : path;
        if (p.front() != '/') p.insert(p.begin(), '/');
        return base() + urlEscapePath(p);
    }

    // ---- request plumbing ----

    void prepare(const std::string& u) {
        freeLists();
        curl_easy_reset(mCurl);
        curl_easy_setopt(mCurl, CURLOPT_URL, u.c_str());
        curl_easy_setopt(mCurl, CURLOPT_FOLLOWLOCATION, 1L);
        curl_easy_setopt(mCurl, CURLOPT_CONNECTTIMEOUT, 10L);
        // No overall timeout: a large sequential read over a slow link is legitimate. Instead give
        // up only when the transfer actually stalls.
        curl_easy_setopt(mCurl, CURLOPT_LOW_SPEED_TIME, 30L);
        curl_easy_setopt(mCurl, CURLOPT_LOW_SPEED_LIMIT, 1L);
        curl_easy_setopt(mCurl, CURLOPT_NOSIGNAL, 1L);
        if (!mCfg.user.empty()) {
            curl_easy_setopt(mCurl, CURLOPT_USERNAME, mCfg.user.c_str());
            curl_easy_setopt(mCurl, CURLOPT_PASSWORD, mCfg.password.c_str());
            if (mWebdav) curl_easy_setopt(mCurl, CURLOPT_HTTPAUTH, CURLAUTH_ANY);
        }
        if (mCfg.useTls && !mWebdav) {
            // Explicit FTPS (AUTH TLS on the standard port), which is what servers offering "FTP
            // over TLS" mean. Implicit FTPS on 990 is rare enough not to be worth a second toggle.
            curl_easy_setopt(mCurl, CURLOPT_USE_SSL, (long)CURLUSESSL_ALL);
        }
    }

    void freeLists() {
        if (mQuote) { curl_slist_free_all(mQuote); mQuote = nullptr; }
        if (mHeaders) { curl_slist_free_all(mHeaders); mHeaders = nullptr; }
    }

    // A WebDAV metadata request: a verb with no body, optionally carrying one extra header.
    int webdavVerb(const char* verb, const std::string& path, const std::string& header) {
        if (!mCurl) return -EIO;
        Buffer b;
        prepare(url(path));
        curl_easy_setopt(mCurl, CURLOPT_WRITEFUNCTION, writeToString);
        curl_easy_setopt(mCurl, CURLOPT_WRITEDATA, &b);
        curl_easy_setopt(mCurl, CURLOPT_CUSTOMREQUEST, verb);
        if (!header.empty()) {
            mHeaders = curl_slist_append(nullptr, header.c_str());
            curl_easy_setopt(mCurl, CURLOPT_HTTPHEADER, mHeaders);
        }
        CURLcode rc = curl_easy_perform(mCurl);
        return rc == CURLE_OK ? 0 : mapError(rc);
    }

    // FTP metadata commands. They are sent against the share root with no transfer, because the
    // paths in them are server-absolute and so do not depend on the working directory.
    int ftpQuote(const std::vector<std::string>& cmds) {
        if (!mCurl) return -EIO;
        Buffer b;
        prepare(base() + "/");
        curl_easy_setopt(mCurl, CURLOPT_WRITEFUNCTION, writeToString);
        curl_easy_setopt(mCurl, CURLOPT_WRITEDATA, &b);
        curl_easy_setopt(mCurl, CURLOPT_NOBODY, 1L);
        for (const std::string& c : cmds) mQuote = curl_slist_append(mQuote, c.c_str());
        curl_easy_setopt(mCurl, CURLOPT_QUOTE, mQuote);
        CURLcode rc = curl_easy_perform(mCurl);
        return rc == CURLE_OK ? 0 : mapError(rc);
    }

    int readDirLocked(const std::string& path, std::vector<DirEntry>* out) {
        if (!mCurl) return -EIO;
        Buffer b;
        std::string u = url(path);
        if (u.back() != '/') u += '/';
        prepare(u);
        curl_easy_setopt(mCurl, CURLOPT_WRITEFUNCTION, writeToString);
        curl_easy_setopt(mCurl, CURLOPT_WRITEDATA, &b);
        if (mWebdav) {
            // Depth 1 is this collection and its immediate children, which is exactly a readdir.
            mHeaders = curl_slist_append(nullptr, "Depth: 1");
            mHeaders = curl_slist_append(mHeaders, "Content-Type: text/xml");
            curl_easy_setopt(mCurl, CURLOPT_HTTPHEADER, mHeaders);
            curl_easy_setopt(mCurl, CURLOPT_CUSTOMREQUEST, "PROPFIND");
        } else {
            // A directory URL makes curl issue LIST; the reply is parsed below. DIRLISTONLY would
            // give bare names with no type or size, which is not enough to build a listing.
            curl_easy_setopt(mCurl, CURLOPT_DIRLISTONLY, 0L);
        }
        CURLcode rc = curl_easy_perform(mCurl);
        if (rc != CURLE_OK) return mapError(rc);

        if (mWebdav) parseWebdav(b.data, path, out);
        else         parseFtpList(b.data, out);
        return 0;
    }

    // ---- listing parsers ----

    // Pull hrefs and their collection/size properties out of a PROPFIND response. This is a
    // deliberately small parser rather than a real XML one: the response shape is fixed, and
    // pulling in an XML parser for it is not worth the size on this device. Namespace prefixes vary
    // between servers ("D:href", "d:href", plain "href"), so matching is on the local name.
    void parseWebdav(const std::string& xml, const std::string& dirPath,
                     std::vector<DirEntry>* out) {
        // The collection being listed is in its own reply; recognise and skip it by path.
        const std::string self = stripTrailingSlashes(rootPath() +
                (dirPath.empty() || dirPath == "/" ? std::string() : dirPath));

        size_t pos = 0;
        while (true) {
            size_t rs = xml.find("response", pos);
            if (rs == std::string::npos) break;
            size_t re = xml.find("response>", rs + 8);
            if (re == std::string::npos) break;
            std::string block = xml.substr(rs, re - rs);
            pos = re + 9;

            size_t hs = block.find("href>");
            if (hs == std::string::npos) continue;
            hs += 5;
            size_t he = block.find('<', hs);
            if (he == std::string::npos) continue;

            std::string href = stripTrailingSlashes(urlUnescape(hrefPath(block.substr(hs, he - hs))));
            if (href == self) continue;          // the collection itself
            size_t slash = href.find_last_of('/');
            std::string name = (slash == std::string::npos) ? href : href.substr(slash + 1);
            if (name.empty() || name == "." || name == "..") continue;

            DirEntry e;
            e.name = name;
            e.isDir = block.find("collection") != std::string::npos;
            size_t ls = block.find("getcontentlength>");
            if (ls != std::string::npos) {
                e.size = strtoull(block.c_str() + ls + 17, nullptr, 10);
            }
            out->push_back(std::move(e));
        }
    }

    // Parse an FTP LIST reply. Two dialects cover essentially every server: the unix "ls -l" form,
    // and the DOS form that IIS and a lot of router and camera firmware emit.
    void parseFtpList(const std::string& listing, std::vector<DirEntry>* out) {
        size_t pos = 0;
        while (pos < listing.size()) {
            size_t eol = listing.find('\n', pos);
            if (eol == std::string::npos) eol = listing.size();
            std::string line = listing.substr(pos, eol - pos);
            pos = eol + 1;
            while (!line.empty() && (line.back() == '\r' || line.back() == ' ')) line.pop_back();
            if (line.empty()) continue;

            DirEntry e;
            if (line[0] == 'd' || line[0] == '-' || line[0] == 'l') {
                if (!parseUnixListLine(line, &e)) continue;
            } else if (isdigit((unsigned char)line[0])) {
                if (!parseDosListLine(line, &e)) continue;
            } else {
                continue;
            }
            if (e.name.empty() || e.name == "." || e.name == "..") continue;
            out->push_back(std::move(e));
        }
    }

    // "drwxr-xr-x  2 user group  4096 Jan  1 12:00 name with spaces"
    // Eight whitespace-separated fields, then the name, which may itself contain spaces.
    static bool parseUnixListLine(const std::string& line, DirEntry* e) {
        std::vector<std::string> f;
        size_t i = 0;
        while (i < line.size() && f.size() < 8) {
            while (i < line.size() && line[i] == ' ') i++;
            size_t s = i;
            while (i < line.size() && line[i] != ' ') i++;
            if (i > s) f.push_back(line.substr(s, i - s));
        }
        if (f.size() < 8) return false;
        while (i < line.size() && line[i] == ' ') i++;
        std::string name = line.substr(i);
        // A symlink line ends with "name -> target"; keep the name and treat it as a plain entry.
        size_t arrow = name.find(" -> ");
        if (arrow != std::string::npos) name = name.substr(0, arrow);
        if (name.empty()) return false;

        e->name = name;
        e->isDir = (line[0] == 'd');
        e->size = strtoull(f[4].c_str(), nullptr, 10);
        return true;
    }

    // "01-25-26  12:00AM       <DIR>          name" / "... 1234567 name"
    static bool parseDosListLine(const std::string& line, DirEntry* e) {
        std::vector<std::string> f;
        size_t i = 0;
        while (i < line.size() && f.size() < 3) {
            while (i < line.size() && line[i] == ' ') i++;
            size_t s = i;
            while (i < line.size() && line[i] != ' ') i++;
            if (i > s) f.push_back(line.substr(s, i - s));
        }
        if (f.size() < 3) return false;
        while (i < line.size() && line[i] == ' ') i++;
        std::string name = line.substr(i);
        if (name.empty()) return false;

        e->name = name;
        e->isDir = (f[2] == "<DIR>");
        e->size = e->isDir ? 0 : strtoull(f[2].c_str(), nullptr, 10);
        return true;
    }

    int mapError(CURLcode rc) {
        long http = 0;
        curl_easy_getinfo(mCurl, CURLINFO_RESPONSE_CODE, &http);
        // For FTP this is the reply code, so the HTTP mappings below are guarded on the range.
        if (http >= 400 && http < 600) {
            if (http == 404 || http == 410) return -ENOENT;
            if (http == 401 || http == 403) return -EACCES;
            if (http == 405 || http == 501) return -ENOTSUP;
            if (http == 409) return -ENOENT;      // MKCOL/PUT into a missing parent
            if (http == 412) return -EEXIST;
            if (http == 507) return -ENOSPC;
        }
        switch (rc) {
            case CURLE_COULDNT_RESOLVE_HOST:
            case CURLE_COULDNT_CONNECT:
            case CURLE_OPERATION_TIMEDOUT:
            case CURLE_SEND_ERROR:
            case CURLE_RECV_ERROR:
                // The connection is gone; the next call reconnects rather than failing forever.
                mDead = true;
                return -EHOSTUNREACH;
            case CURLE_LOGIN_DENIED:
            case CURLE_AUTH_ERROR:
            case CURLE_REMOTE_ACCESS_DENIED:
                return -EACCES;
            case CURLE_REMOTE_FILE_NOT_FOUND:
                return -ENOENT;
            case CURLE_REMOTE_DISK_FULL:
                return -ENOSPC;
            case CURLE_REMOTE_FILE_EXISTS:
                return -EEXIST;
            case CURLE_UNSUPPORTED_PROTOCOL:
                // Would mean libcurl was built without this protocol, which the share UI offers.
                ALOGE("libcurl has no support for %s", scheme());
                return -ENOTSUP;
            default:
                ALOGW("%s error on '%s': %s (code %ld)", mWebdav ? "WebDAV" : "FTP",
                      mCfg.name.c_str(), curl_easy_strerror(rc), http);
                return -EIO;
        }
    }

    ShareConfig mCfg;
    bool        mWebdav;
    std::mutex  mLock;
    CURL*       mCurl = nullptr;
    curl_slist* mQuote = nullptr;
    curl_slist* mHeaders = nullptr;
    bool        mDead = false;

    // Staged body for the file currently being written. One at a time: a share being written by
    // two writers at once is not a case worth the memory here, and the second one flushes the
    // first rather than corrupting it.
    static constexpr size_t kMaxStagedBytes = 64u * 1024 * 1024;
    std::string       mStagePath;
    std::vector<char> mStage;

    // Readahead window, sized by what a round trip costs on each protocol. WebDAV rides an HTTP
    // keep-alive connection, so a miss is one request and 1MB is plenty. FTP has to build a fresh
    // data connection (PASV/EPSV, then the transfer) for every single range, measured at well over
    // a second each here, so it pays to fetch far more per trip. One buffer per share either way.
    size_t readAheadBytes() const { return mWebdav ? (1024u * 1024) : (4u * 1024 * 1024); }
    std::string       mCachePath;
    uint64_t          mCacheOff = 0;
    std::vector<char> mCache;
};

}  // namespace

Backend* makeWebdavBackend(const ShareConfig& cfg) { return new CurlBackend(cfg, true); }
Backend* makeFtpBackend(const ShareConfig& cfg) { return new CurlBackend(cfg, false); }

}  // namespace sharefs
}  // namespace gammaos
