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
#include <time.h>

#include <algorithm>
#include <atomic>
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

// Accumulate a response body, with a ceiling.
//
// This collects directory listings: a WebDAV PROPFIND document or an FTP LIST reply. Both are
// sized by the server, and neither had any limit - a share with a very large flat directory, or a
// server that simply keeps sending, grew this until the daemon was OOM-killed on a device with
// under a gigabyte of RAM. Returning short aborts the transfer, which the caller reports as an
// error rather than silently handing back a truncated listing.
constexpr size_t kMaxListingBytes = 16u * 1024 * 1024;

size_t writeToString(char* ptr, size_t sz, size_t nm, void* user) {
    auto* b = static_cast<Buffer*>(user);
    const size_t n = sz * nm;
    if (b->data.size() + n > kMaxListingBytes) return 0;   // abort: listing is implausibly large
    b->data.append(ptr, n);
    return n;
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

    // Throw the easy handle away and start a new one. Caller must hold mLock.
    //
    // curl_easy_reset() puts the options back but does not reliably put an FTP handle's protocol
    // state back, and a windowed read has to abort its data transfer to stop at the end of the
    // window (the end of CURLOPT_RANGE is enforced by curl, not by the server, so a 4MB window of a
    // 64MB file is a RETR that gets ABORed part way through - the server logged 14MB sent for a 4MB
    // request). After that the handle is one reply out of step, and the next operation reads the
    // previous command's response: PASV coming back with 250, which is the reply to the preceding
    // CWD, or RETR coming back with 227, which is the reply to PASV. Reads then fail with EIO at
    // random under sustained use. Uploads already avoided this with a fresh connection; a fresh
    // connection is not enough for reads because the damage is in the handle, not the socket.
    void resetHandleLocked() {
        if (mCurl) curl_easy_cleanup(mCurl);
        freeLists();
        mCurl = curl_easy_init();
        // If a new handle cannot be had, say the session is dead rather than leaving a null handle
        // for the next call to trip over: the reconnect path already knows how to rebuild one.
        if (!mCurl) mDead = true;
    }

    int getAttr(const std::string& path, struct stat* out) override {
        memset(out, 0, sizeof(*out));
        if (path == "/" || path.empty()) {
            out->st_mode = S_IFDIR | 0770;
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
            out->st_mode = e.isDir ? (S_IFDIR | 0770) : (S_IFREG | 0660);
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
            mNextSeqOff = want + size;   // where a sequential reader would go next
            return static_cast<int>(size);
        }

        // Grow the readahead only for a reader that is actually streaming.
        //
        // A fixed large window is the wrong default. Reading a few KB of ID3 tag from a 3.5MB track
        // used to fetch a 4MB window, so scanning an album for metadata pulled essentially the whole
        // album: the server logged 36 transfers, 133.8MB and nearly four minutes for 35 files, 27 of
        // them aborted part way because the reader had long since got what it wanted. Playback and
        // copying do read forward and do want a big window, so the size is earned rather than
        // assumed - start small, and quadruple it each time a read continues exactly where the last
        // one ended, up to the old maximum. A seek or a different file starts over.
        const bool sequential = (mCachePath == path && want == mNextSeqOff);
        if (sequential) mWindow = std::min(mWindow * 4, readAheadBytes());
        else            mWindow = kMinWindowBytes;
        const size_t window = std::max(size, mWindow);
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
        const bool ok = rc == CURLE_OK || rc == CURLE_PARTIAL_FILE ||
                        (rc == CURLE_WRITE_ERROR && b.got == b.cap);

        // Work the error out first, while the handle that performed the transfer is still the one
        // mCurl points at: mapError() reads CURLINFO_RESPONSE_CODE off it, and against a
        // freshly-created handle that reads back as 0, quietly losing the 404 / 401 / 507 mappings
        // and turning a "no such file" into a generic failure.
        const int err = ok ? 0 : mapError(rc);

        // On FTP, any transfer that did not run to completion leaves the handle out of step with
        // the control connection, so it cannot be used again. That includes the successful cases:
        // stopping at the end of the window is exactly the abort that causes the problem. Doing
        // this on the way out costs one connection setup per window, which the readahead already
        // amortises over megabytes, and it is the difference between FTP working under sustained
        // reads and failing with EIO every few files.
        if (!mWebdav && rc != CURLE_OK) resetHandleLocked();

        if (!ok) {
            mCachePath.clear();
            mCache.clear();
            return err;
        }

        mCache.resize(b.got);
        mCachePath = path;
        mCacheOff = want;
        mNextSeqOff = want + std::min(size, b.got);
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
        //
        // uploadLocked() must be called with mLock held, as its name says: it drives the one shared
        // CURL* handle. It used to be called just after this scope closed, which left another FUSE
        // thread free to run a read or a listing on the same handle at the same time - libfuse is
        // multi-threaded, so that is an ordinary interleaving rather than a rare one, and curl's
        // per-handle state does not survive it.
        std::lock_guard<std::mutex> lk(mLock);
        if (!mStagePath.empty() && mStagePath != path) flushStagedLocked();
        mStagePath = path;
        mStage.clear();
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
        if (!mWebdav) {
            // Upload on a connection of its own.
            //
            // prepare() calls curl_easy_reset() on a handle that may still hold a live FTP control
            // connection, and reusing it across operations left curl's reply parsing out of step:
            // an upload failed with "unknown PASV reply (code 250)", 250 being the reply to the CWD
            // issued just before, so it was matching one command's response to another's request.
            // A fresh connection per upload sidesteps the whole class of problem, and costs little
            // because writes are staged and sent once per file rather than per chunk.
            curl_easy_setopt(mCurl, CURLOPT_FRESH_CONNECT, 1L);
            curl_easy_setopt(mCurl, CURLOPT_FORBID_REUSE, 1L);
        }
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
        if (!mWebdav) {
            // Plain PASV rather than EPSV. Uploads here failed with CURLE_FTP_WEIRD_PASV_REPLY
            // ("unknown PASV reply, code 229") when negotiating extended passive mode, while the
            // same server and the same library handled downloads. PASV is understood by every FTP
            // server that matters over IPv4, and a data connection that is established is worth
            // more than one that is negotiated more elegantly.
            curl_easy_setopt(mCurl, CURLOPT_FTP_USE_EPSV, 0L);
            // Some servers report an unroutable address in the PASV reply (NAT, containers). Reuse
            // the control connection's address for the data connection instead of trusting it.
            curl_easy_setopt(mCurl, CURLOPT_FTP_SKIP_PASV_IP, 1L);
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
        // No handle reset here, unlike the FTP paths: HTTP carries no per-connection command
        // state, so a failed verb leaves nothing out of step for the next request to trip over.
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
        if (rc == CURLE_OK) return 0;
        // A failed QUOTE leaves the control connection out of step exactly as a failed LIST or a
        // half-read RETR does, and this path carries DELE, MKD, RMD and the RNFR/RNTO pair - so a
        // rename onto an existing name, or a delete on a full or read-only disk, would otherwise
        // poison every operation that followed it. mapError() first: it reads the reply code off
        // the handle that is about to be thrown away.
        const int err = mapError(rc);
        resetHandleLocked();   // FTP only: ftpQuote is never reached on the WebDAV path
        return err;
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
        if (rc != CURLE_OK) {
            // Same reasoning as readFile: a failed FTP LIST leaves the control connection one
            // reply out of step, and the next operation reads the orphaned reply as its own. The
            // error is worked out first, because mapError() reads the response code off the handle
            // that performed the request.
            const int err = mapError(rc);
            if (!mWebdav) resetHandleLocked();
            return err;
        }

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
            // getlastmodified is an RFC 1123 date ("Tue, 15 Nov 1994 12:45:26 GMT"). Without it
            // every entry reported 1970, so a media scanner saw the whole share as new on every
            // pass and re-scanned all of it.
            size_t ms = block.find("getlastmodified>");
            if (ms != std::string::npos) {
                ms += 16;
                size_t me = block.find('<', ms);
                if (me != std::string::npos) {
                    struct tm t = {};
                    if (strptime(block.substr(ms, me - ms).c_str(), "%a, %d %b %Y %H:%M:%S", &t)) {
                        e.mtime = timegm(&t);   // the header is GMT by definition
                    }
                }
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


// Turn an FTP LIST date into a time_t.
//
// Without this every WebDAV and FTP entry reported mtime 0, i.e. 1970. That is not cosmetic: a media
// scanner uses mtime to decide what has changed, so everything looked new on every pass and the
// whole share was re-scanned each time - expensive on a protocol where one scan already moves tens
// of megabytes. SMB and NFS both report a real mtime, so only these two were affected.
//
// Unix listings give "Mon DD HH:MM" for recent files and "Mon DD  YYYY" for older ones, with no
// year in the first form and no time in the second. The missing year is assumed to be the most
// recent one that does not put the date in the future, which is the same rule ls itself uses.
static time_t ftpListTime(const std::string& mon, const std::string& day, const std::string& last) {
    static const char* kMon[] = {"Jan","Feb","Mar","Apr","May","Jun",
                                 "Jul","Aug","Sep","Oct","Nov","Dec"};
    int m = -1;
    for (int i = 0; i < 12; i++) if (mon == kMon[i]) { m = i; break; }
    if (m < 0) return 0;
    const int d = atoi(day.c_str());
    if (d < 1 || d > 31) return 0;

    const time_t now = time(nullptr);
    struct tm nowtm = {};
    localtime_r(&now, &nowtm);

    struct tm t = {};
    t.tm_mon = m;
    t.tm_mday = d;
    t.tm_isdst = -1;
    if (last.find(':') != std::string::npos) {
        t.tm_hour = atoi(last.c_str());
        const size_t c = last.find(':');
        t.tm_min = atoi(last.c_str() + c + 1);
        t.tm_year = nowtm.tm_year;
        time_t v = mktime(&t);
        // No year in this form: if that lands in the future it belongs to last year.
        if (v > now + 24 * 3600) { t.tm_year--; t.tm_isdst = -1; v = mktime(&t); }
        return v;
    }
    t.tm_year = atoi(last.c_str()) - 1900;
    if (t.tm_year < 70 || t.tm_year > 200) return 0;
    return mktime(&t);
}

// "MM-DD-YY  HH:MMAM" as sent by IIS-style servers.
static time_t dosListTime(const std::string& date, const std::string& tm) {
    if (date.size() < 8 || tm.size() < 5) return 0;
    struct tm t = {};
    t.tm_mon  = atoi(date.substr(0, 2).c_str()) - 1;
    t.tm_mday = atoi(date.substr(3, 2).c_str());
    int yy = atoi(date.substr(6, 2).c_str());
    t.tm_year = (yy < 70 ? yy + 100 : yy);          // 70..99 -> 1970s..1990s, 00..69 -> 2000s
    int hh = atoi(tm.substr(0, 2).c_str());
    const size_t c = tm.find(':');
    if (c == std::string::npos) return 0;
    t.tm_min = atoi(tm.c_str() + c + 1);
    const bool pm = tm.find("PM") != std::string::npos || tm.find("pm") != std::string::npos;
    if (pm && hh != 12) hh += 12;
    if (!pm && hh == 12) hh = 0;
    t.tm_hour = hh;
    t.tm_isdst = -1;
    if (t.tm_mon < 0 || t.tm_mon > 11 || t.tm_mday < 1 || t.tm_mday > 31) return 0;
    return mktime(&t);
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
        e->mtime = ftpListTime(f[5], f[6], f[7]);
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
        e->mtime = dosListTime(f[0], f[1]);
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
    // Atomic because isDead() is read by the FUSE threads without taking mLock (it is the
    // cheap 'should I retry this operation' check), while every write happens under it.
    std::atomic<bool>        mDead {false};

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
    // Adaptive readahead: the window a streaming reader has earned, and the offset that would
    // continue the last read. See readFile().
    static constexpr size_t kMinWindowBytes = 256u * 1024;
    size_t            mWindow = kMinWindowBytes;
    uint64_t          mNextSeqOff = 0;
};

}  // namespace

Backend* makeWebdavBackend(const ShareConfig& cfg) { return new CurlBackend(cfg, true); }
Backend* makeFtpBackend(const ShareConfig& cfg) { return new CurlBackend(cfg, false); }

}  // namespace sharefs
}  // namespace gammaos
