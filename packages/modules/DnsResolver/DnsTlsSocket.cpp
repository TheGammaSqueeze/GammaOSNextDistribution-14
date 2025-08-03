/*
 * Copyright (C) 2018 The Android Open Source Project
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

#define LOG_TAG "resolv"

#include "DnsTlsSocket.h"

#include <arpa/inet.h>
#include <arpa/nameser.h>
#include <errno.h>
#include <linux/tcp.h>
#include <openssl/err.h>
#include <openssl/sha.h>
#include <sys/eventfd.h>
#include <sys/poll.h>
#include <unistd.h>
#include <algorithm>

#include "DnsTlsSessionCache.h"
#include "IDnsTlsSocketObserver.h"

#include <android-base/logging.h>
#include <netdutils/SocketOption.h>
#include <netdutils/ThreadUtil.h>

#include "Experiments.h"
#include "netd_resolv/resolv.h"
#include "private/android_filesystem_config.h"  // AID_DNS
#include "resolv_private.h"

namespace android {

using netdutils::enableSockopt;
using netdutils::enableTcpKeepAlives;
using netdutils::isOk;
using netdutils::setThreadName;
using netdutils::Slice;
using netdutils::Status;

namespace net {
namespace {

constexpr const char kCaCertDir[] = "/system/etc/security/cacerts";

int waitForReading(int fd, int timeoutMs = -1) {
    pollfd fds = {.fd = fd, .events = POLLIN};
    return TEMP_FAILURE_RETRY(poll(&fds, 1, timeoutMs));
}

int waitForWriting(int fd, int timeoutMs = -1) {
    pollfd fds = {.fd = fd, .events = POLLOUT};
    return TEMP_FAILURE_RETRY(poll(&fds, 1, timeoutMs));
}

}  // namespace

Status DnsTlsSocket::tcpConnect() {
    if (mServer.protocol != IPPROTO_TCP) return Status(EPROTONOSUPPORT);

    LOG(INFO) << fmt::format("Connecting to {} with mark 0x{:x}", mServer.toString(), mMark);

    mSslFd.reset(socket(mServer.ss.ss_family, SOCK_STREAM | SOCK_NONBLOCK | SOCK_CLOEXEC, 0));
    if (mSslFd.get() == -1) {
        const int err = errno;
        PLOG(ERROR) << "Failed to create socket, errno=" << err;
        return Status(err);
    }

    resolv_tag_socket(mSslFd.get(), AID_DNS, NET_CONTEXT_INVALID_PID);

    const socklen_t len = sizeof(mMark);
    if (setsockopt(mSslFd.get(), SOL_SOCKET, SO_MARK, &mMark, len)) {
        const int err = errno;
        PLOG(ERROR) << "Failed to set socket mark, errno=" << err;
        mSslFd.reset();
        return Status(err);
    }

    // Set TCP MSS to a suitably low value to be more reliable.
    const int v = (mServer.ss.ss_family == AF_INET) ? 1212 : 1220;
    if (setsockopt(mSslFd.get(), SOL_TCP, TCP_MAXSEG, &v, sizeof(v))) {
        const int err = errno;
        LOG(WARNING) << "Failed to set TCP_MAXSEG, errno=" << err;
    }

    const Status tfo = enableSockopt(mSslFd.get(), SOL_TCP, TCP_FASTOPEN_CONNECT);
    if (!isOk(tfo) && tfo.code() != ENOPROTOOPT) {
        LOG(WARNING) << "Failed to enable TFO: " << tfo.msg();
    }

    // Send 5 keepalives, 3 seconds apart, after 15 seconds of inactivity.
    enableTcpKeepAlives(mSslFd.get(), 15U, 5U, 3U).ignoreError();

    if (connect(mSslFd.get(), reinterpret_cast<const struct sockaddr *>(&mServer.ss),
                sizeof(mServer.ss)) != 0 &&
            errno != EINPROGRESS) {
        const int err = errno;
        PLOG(WARNING) << "Socket failed to connect, errno=" << err;
        mSslFd.reset();
        return Status(err);
    }

    return netdutils::status::ok;
}

bool DnsTlsSocket::setTestCaCertificate() {
    bssl::UniquePtr<BIO> bio(
            BIO_new_mem_buf(mServer.certificate.data(), mServer.certificate.size()));
    bssl::UniquePtr<X509> cert(PEM_read_bio_X509(bio.get(), nullptr, nullptr, nullptr));
    if (!cert) {
        LOG(ERROR) << "Failed to read cert";
        return false;
    }

    X509_STORE* cert_store = SSL_CTX_get_cert_store(mSslCtx.get());
    if (!X509_STORE_add_cert(cert_store, cert.get())) {
        LOG(ERROR) << "Failed to add cert";
        return false;
    }
    return true;
}

// TODO: Try to use static sSslCtx instead of mSslCtx
bool DnsTlsSocket::initialize() {
    // This method is called every time when a new SSL connection is created.
    // This lock only serves to help catch bugs in code that calls this method.
    std::lock_guard guard(mLock);
    if (mSslCtx) {
        // This is a bug in the caller.
        return false;
    }
    mSslCtx.reset(SSL_CTX_new(TLS_method()));
    if (!mSslCtx) {
        return false;
    }

    // Load system CA certs from CAPath for hostname verification.
    //
    // For discussion of alternative, sustainable approaches see b/71909242.
    if (!mServer.certificate.empty()) {
        // Inject test CA certs from ResolverParamsParcel.caCertificate for INTERNAL TESTING ONLY.
        // This is only allowed by DnsResolverService if the caller is AID_ROOT.
        LOG(WARNING) << "Setting test CA certificate. This should never happen in production code.";
        if (!setTestCaCertificate()) {
            LOG(ERROR) << "Failed to set test CA certificate";
            return false;
        }
    } else {
        if (SSL_CTX_load_verify_locations(mSslCtx.get(), nullptr, kCaCertDir) != 1) {
            LOG(ERROR) << "Failed to load CA cert dir: " << kCaCertDir;
            return false;
        }
    }

    // Enable TLS false start
    SSL_CTX_set_false_start_allowed_without_alpn(mSslCtx.get(), 1);
    SSL_CTX_set_mode(mSslCtx.get(), SSL_MODE_ENABLE_FALSE_START);

    // Enable session cache
    mCache->prepareSslContext(mSslCtx.get());

    mEventFd.reset(eventfd(0, EFD_NONBLOCK | EFD_CLOEXEC));
    mShutdownEvent.reset(eventfd(0, EFD_NONBLOCK | EFD_CLOEXEC));

    const Experiments* const instance = Experiments::getInstance();
    mConnectTimeoutMs = instance->getFlag("dot_connect_timeout_ms", kDotConnectTimeoutMs);
    if (mConnectTimeoutMs < 1000) mConnectTimeoutMs = 1000;

    mAsyncHandshake = instance->getFlag("dot_async_handshake", 0);
    LOG(DEBUG) << "DnsTlsSocket is initialized with { mConnectTimeoutMs: " << mConnectTimeoutMs
               << ", mAsyncHandshake: " << mAsyncHandshake << " }";

    transitionState(State::UNINITIALIZED, State::INITIALIZED);

    return true;
}

bool DnsTlsSocket::startHandshake() {
    std::lock_guard guard(mLock);
    if (mState != State::INITIALIZED) {
        LOG(ERROR) << "Calling startHandshake in unexpected state " << static_cast<int>(mState);
        return false;
    }
    transitionState(State::INITIALIZED, State::CONNECTING);

    if (!mAsyncHandshake) {
        if (Status status = tcpConnect(); !status.ok()) {
            transitionState(State::CONNECTING, State::WAIT_FOR_DELETE);
            LOG(WARNING) << "TCP Handshake failed: " << status.code();
            return false;
        }
        if (mSsl = sslConnect(mSslFd.get()); !mSsl) {
            transitionState(State::CONNECTING, State::WAIT_FOR_DELETE);
            LOG(WARNING) << "TLS Handshake failed";
            return false;
        }
    }

    // Start the I/O loop.
    mLoopThread.reset(new std::thread(&DnsTlsSocket::loop, this));

    return true;
}

bssl::UniquePtr<SSL> DnsTlsSocket::prepareForSslConnect(int fd) {
    if (!mSslCtx) {
        LOG(ERROR) << "Internal error: context is null in sslConnect";
        return nullptr;
    }
    if (!SSL_CTX_set_min_proto_version(mSslCtx.get(), TLS1_2_VERSION)) {
        LOG(ERROR) << "Failed to set minimum TLS version";
        return nullptr;
    }

    bssl::UniquePtr<SSL> ssl(SSL_new(mSslCtx.get()));
    // This file descriptor is owned by mSslFd, so don't let libssl close it.
    bssl::UniquePtr<BIO> bio(BIO_new_socket(fd, BIO_NOCLOSE));
    SSL_set_bio(ssl.get(), bio.get(), bio.get());
    (void)bio.release();

    if (!mCache->prepareSsl(ssl.get())) {
        return nullptr;
    }

    if (!mServer.name.empty()) {
        LOG(VERBOSE) << "Checking DNS over TLS hostname = " << mServer.name.c_str();
        if (SSL_set_tlsext_host_name(ssl.get(), mServer.name.c_str()) != 1) {
            LOG(ERROR) << "Failed to set SNI to " << mServer.name;
            return nullptr;
        }
        X509_VERIFY_PARAM* param = SSL_get0_param(ssl.get());
        if (X509_VERIFY_PARAM_set1_host(param, mServer.name.data(), mServer.name.size()) != 1) {
            LOG(ERROR) << "Failed to set verify host param to " << mServer.name;
            return nullptr;
        }
        // This will cause the handshake to fail if certificate verification fails.
        SSL_set_verify(ssl.get(), SSL_VERIFY_PEER, nullptr);
    }

    bssl::UniquePtr<SSL_SESSION> session = mCache->getSession();
    if (session) {
        LOG(DEBUG) << "Setting session";
        SSL_set_session(ssl.get(), session.get());
    } else {
        LOG(DEBUG) << "No session available";
    }

    return ssl;
}

bssl::UniquePtr<SSL> DnsTlsSocket::sslConnect(int fd) {
    bssl::UniquePtr<SSL> ssl;
    if (ssl = prepareForSslConnect(fd); !ssl) {
        return nullptr;
    }

    for (;;) {
        LOG(DEBUG) << " Calling SSL_connect with mark 0x" << std::hex << mMark;
        int ret = SSL_connect(ssl.get());
        LOG(INFO) << " SSL_connect returned " << ret << " with mark 0x" << std::hex << mMark;
        if (ret == 1) break;  // SSL handshake complete;

        const int ssl_err = SSL_get_error(ssl.get(), ret);
        switch (ssl_err) {
            case SSL_ERROR_WANT_READ:
                // SSL_ERROR_WANT_READ is returned because the application data has been sent during
                // the TCP connection handshake, the device is waiting for the SSL handshake reply
                // from the server.
                if (int err = waitForReading(fd, mConnectTimeoutMs); err <= 0) {
                    PLOG(WARNING) << "SSL_connect read error " << err << ", mark 0x" << std::hex
                                  << mMark;
                    return nullptr;
                }
                break;
            case SSL_ERROR_WANT_WRITE:
                // If no application data is sent during the TCP connection handshake, the
                // device is waiting for the connection established to perform SSL handshake.
                if (int err = waitForWriting(fd, mConnectTimeoutMs); err <= 0) {
                    PLOG(WARNING) << "SSL_connect write error " << err << ", mark 0x" << std::hex
                                  << mMark;
                    return nullptr;
                }
                break;
            default:
                PLOG(WARNING) << "SSL_connect ssl error =" << ssl_err << ", mark 0x" << std::hex
                              << mMark;
                return nullptr;
        }
    }

    LOG(DEBUG) << mMark << " handshake complete";

    return ssl;
}

bssl::UniquePtr<SSL> DnsTlsSocket::sslConnectV2(int fd) {
    bssl::UniquePtr<SSL> ssl;
    if (ssl = prepareForSslConnect(fd); !ssl) {
        return nullptr;
    }

    for (;;) {
        LOG(DEBUG) << " Calling SSL_connect with mark 0x" << std::hex << mMark;
        int ret = SSL_connect(ssl.get());
        LOG(INFO) << " SSL_connect returned " << ret << " with mark 0x" << std::hex << mMark;
        if (ret == 1) break;  // SSL handshake complete;

        enum { SSLFD = 0, EVENTFD = 1 };
        pollfd fds[2] = {
                {.fd = mSslFd.get(), .events = 0},
                {.fd = mShutdownEvent.get(), .events = POLLIN},
        };

        const int ssl_err = SSL_get_error(ssl.get(), ret);
        switch (ssl_err) {
            case SSL_ERROR_WANT_READ:
                fds[SSLFD].events = POLLIN;
                break;
            case SSL_ERROR_WANT_WRITE:
                fds[SSLFD].events = POLLOUT;
                break;
            default:
                PLOG(WARNING) << "SSL_connect ssl error =" << ssl_err << ", mark 0x" << std::hex
                              << mMark;
                return nullptr;
        }

        int n = TEMP_FAILURE_RETRY(poll(fds, std::size(fds), mConnectTimeoutMs));
        if (n <= 0) {
            PLOG(WARNING) << ((n == 0) ? "handshake timeout" : "Poll failed");
            return nullptr;
        }

        if (fds[EVENTFD].revents & (POLLIN | POLLERR)) {
            LOG(WARNING) << "Got shutdown request during handshake";
            return nullptr;
        }
        if (fds[SSLFD].revents & POLLERR) {
            LOG(WARNING) << "Got POLLERR on SSLFD during handshake";
            return nullptr;
        }
    }

    LOG(DEBUG) << mMark << " handshake complete";

    return ssl;
}

void DnsTlsSocket::sslDisconnect() {
    if (mSsl) {
        SSL_shutdown(mSsl.get());
        mSsl.reset();
    }
    mSslFd.reset();
}

bool DnsTlsSocket::sslWrite(const Slice buffer) {
    LOG(DEBUG) << mMark << " Writing " << buffer.size() << " bytes";
    for (;;) {
        int ret = SSL_write(mSsl.get(), buffer.base(), buffer.size());
        if (ret == int(buffer.size())) break;  // SSL write complete;

        if (ret < 1) {
            const int ssl_err = SSL_get_error(mSsl.get(), ret);
            switch (ssl_err) {
                case SSL_ERROR_WANT_WRITE:
                    if (int err = waitForWriting(mSslFd.get()); err <= 0) {
                        PLOG(WARNING) << "Poll failed in sslWrite, error " << err;
                        return false;
                    }
                    continue;
                case 0:
                    break;  // SSL write complete;
                default:
                    LOG(DEBUG) << "SSL_write error " << ssl_err;
                    return false;
            }
        }
    }
    LOG(DEBUG) << mMark << " Wrote " << buffer.size() << " bytes";
    return true;
}

void DnsTlsSocket::loop() {
    std::lock_guard guard(mLock);
    std::deque<std::vector<uint8_t>> q;
    const int timeout_msecs = DnsTlsSocket::kIdleTimeout.count() * 1000;

    setThreadName(fmt::format("TlsListen_{}", mMark & 0xffff));

    if (mAsyncHandshake) {
        if (Status status = tcpConnect(); !status.ok()) {
            LOG(WARNING) << "TCP Handshake failed: " << status.code();
            mObserver->onClosed();
            transitionState(State::CONNECTING, State::WAIT_FOR_DELETE);
            return;
        }
        if (mSsl = sslConnectV2(mSslFd.get()); !mSsl) {
            LOG(WARNING) << "TLS Handshake failed";
            mObserver->onClosed();
            transitionState(State::CONNECTING, State::WAIT_FOR_DELETE);
            return;
        }
        LOG(DEBUG) << "Handshaking succeeded";
    }

    transitionState(State::CONNECTING, State::CONNECTED);

    while (true) {
        // poll() ignores negative fds
        struct pollfd fds[2] = { { .fd = -1 }, { .fd = -1 } };
        enum { SSLFD = 0, EVENTFD = 1 };

        // Always listen for a response from server.
        fds[SSLFD].fd = mSslFd.get();
        fds[SSLFD].events = POLLIN;

        // If we have pending queries, wait for space to write one.
        // Otherwise, listen for new queries.
        // Note: This blocks the destructor until q is empty, i.e. until all pending
        // queries are sent or have failed to send.
        if (!q.empty()) {
            fds[SSLFD].events |= POLLOUT;
        } else {
            fds[EVENTFD].fd = mEventFd.get();
            fds[EVENTFD].events = POLLIN;
        }

        const int s = TEMP_FAILURE_RETRY(poll(fds, std::size(fds), timeout_msecs));
        if (s == 0) {
            LOG(DEBUG) << "Idle timeout";
            break;
        }
        if (s < 0) {
            PLOG(WARNING) << "Poll failed";
            break;
        }
        if (fds[SSLFD].revents & (POLLIN | POLLERR | POLLHUP)) {
            bool readFailed = false;

            // readResponse() only reads one DNS (and consumes exact bytes) from ssl.
            // Keep doing so until ssl has no pending data.
            // TODO: readResponse() can block until it reads a complete DNS response. Consider
            // refactoring it to not get blocked in any case.
            do {
                if (!readResponse()) {
                    LOG(INFO) << "SSL remote close or read error.";
                    readFailed = true;
                }
            } while (SSL_pending(mSsl.get()) > 0 && !readFailed);

            if (readFailed) {
                break;
            }
        }
        if (fds[EVENTFD].revents & (POLLIN | POLLERR)) {
            int64_t num_queries;
            ssize_t res = read(mEventFd.get(), &num_queries, sizeof(num_queries));
            if (res < 0) {
                LOG(WARNING) << "Error during eventfd read";
                break;
            } else if (res == 0) {
                LOG(WARNING) << "eventfd closed; disconnecting";
                break;
            } else if (res != sizeof(num_queries)) {
                LOG(ERROR) << "Int size mismatch: " << res << " != " << sizeof(num_queries);
                break;
            } else if (num_queries < 0) {
                LOG(DEBUG) << "Negative eventfd read indicates destructor-initiated shutdown";
                break;
            }
            // Take ownership of all pending queries.  (q is always empty here.)
            mQueue.swap(q);
        } else if (fds[SSLFD].revents & POLLOUT) {
            // q cannot be empty here.
            // Sending the entire queue here would risk a TCP flow control deadlock, so
            // we only send a single query on each cycle of this loop.
            // TODO: Coalesce multiple pending queries if there is enough space in the
            // write buffer.
            if (!sendQuery(q.front())) {
                break;
            }
            q.pop_front();
        }
    }
    LOG(INFO) << fmt::format("Disconnecting {}, mark 0x{:x}", mServer.toString(), mMark);
    sslDisconnect();
    LOG(DEBUG) << "Calling onClosed";
    mObserver->onClosed();
    transitionState(State::CONNECTED, State::WAIT_FOR_DELETE);
    LOG(DEBUG) << "Ending loop";
}

DnsTlsSocket::~DnsTlsSocket() {
    LOG(DEBUG) << "Destructor";
    // This will trigger an orderly shutdown in loop().
    requestLoopShutdown();
    {
        // Wait for the orderly shutdown to complete.
        std::lock_guard guard(mLock);
        if (mLoopThread && std::this_thread::get_id() == mLoopThread->get_id()) {
            LOG(ERROR) << "Violation of re-entrance precondition";
            return;
        }
    }
    if (mLoopThread) {
        LOG(DEBUG) << "Waiting for loop thread to terminate";
        mLoopThread->join();
        mLoopThread.reset();
    }
    LOG(DEBUG) << "Destructor completed";
}

bool DnsTlsSocket::query(uint16_t id, const Slice query) {
    // Compose the entire message in a single buffer, so that it can be
    // sent as a single TLS record.
    std::vector<uint8_t> buf(query.size() + 4);
    // Write 2-byte length
    uint16_t len = query.size() + 2;  // + 2 for the ID.
    buf[0] = len >> 8;
    buf[1] = len;
    // Write 2-byte ID
    buf[2] = id >> 8;
    buf[3] = id;
    // Copy body
    std::memcpy(buf.data() + 4, query.base(), query.size());

    mQueue.push(std::move(buf));
    // Increment the mEventFd counter by 1.
    return incrementEventFd(1);
}

void DnsTlsSocket::requestLoopShutdown() {
    if (mEventFd != -1) {
        // Write a negative number to the eventfd.  This triggers an immediate shutdown.
        incrementEventFd(INT64_MIN);
    }
    if (mShutdownEvent != -1) {
        if (eventfd_write(mShutdownEvent.get(), INT64_MIN) == -1) {
            PLOG(ERROR) << "Failed to write to mShutdownEvent";
        }
    }
}

bool DnsTlsSocket::incrementEventFd(const int64_t count) {
    if (mEventFd == -1) {
        LOG(ERROR) << "eventfd is not initialized";
        return false;
    }
    ssize_t written = write(mEventFd.get(), &count, sizeof(count));
    if (written != sizeof(count)) {
        LOG(ERROR) << "Failed to increment eventfd by " << count;
        return false;
    }
    return true;
}

void DnsTlsSocket::transitionState(State from, State to) {
    if (mState != from) {
        LOG(WARNING) << "BUG: transitioning from an unexpected state " << static_cast<int>(mState)
                     << ", expect: from " << static_cast<int>(from) << " to "
                     << static_cast<int>(to);
    }
    mState = to;
}

// Read exactly len bytes into buffer or fail with an SSL error code
int DnsTlsSocket::sslRead(const Slice buffer, bool wait) {
    size_t remaining = buffer.size();
    while (remaining > 0) {
        int ret = SSL_read(mSsl.get(), buffer.limit() - remaining, remaining);
        if (ret == 0) {
            if (remaining < buffer.size())
                LOG(WARNING) << "SSL closed with " << remaining << " of " << buffer.size()
                             << " bytes remaining";
            return SSL_ERROR_ZERO_RETURN;
        }

        if (ret < 0) {
            const int ssl_err = SSL_get_error(mSsl.get(), ret);
            if (wait && ssl_err == SSL_ERROR_WANT_READ) {
                if (int err = waitForReading(mSslFd.get()); err <= 0) {
                    PLOG(WARNING) << "Poll failed in sslRead, error " << err;
                    return SSL_ERROR_SYSCALL;
                }
                continue;
            } else {
                LOG(DEBUG) << "SSL_read error " << ssl_err;
                return ssl_err;
            }
        }

        remaining -= ret;
        wait = true;  // Once a read is started, try to finish.
    }
    return SSL_ERROR_NONE;
}

bool DnsTlsSocket::sendQuery(const std::vector<uint8_t>& buf) {
    if (!sslWrite(netdutils::makeSlice(buf))) {
        return false;
    }
    LOG(DEBUG) << mMark << " SSL_write complete";
    return true;
}

bool DnsTlsSocket::readResponse() {
    LOG(DEBUG) << "reading response";
    uint8_t responseHeader[2];
    int err = sslRead(Slice(responseHeader, 2), false);
    if (err == SSL_ERROR_WANT_READ) {
        LOG(DEBUG) << "Ignoring spurious wakeup from server";
        return true;
    }
    if (err != SSL_ERROR_NONE) {
        return false;
    }
    // Truncate responses larger than MAX_SIZE.  This is safe because a DNS packet is
    // always invalid when truncated, so the response will be treated as an error.
    constexpr uint16_t MAX_SIZE = 8192;
    const uint16_t responseSize = (responseHeader[0] << 8) | responseHeader[1];
    LOG(DEBUG) << mMark << " Expecting response of size " << responseSize;
    std::vector<uint8_t> response(std::min(responseSize, MAX_SIZE));
    if (sslRead(netdutils::makeSlice(response), true) != SSL_ERROR_NONE) {
        LOG(DEBUG) << mMark << " Failed to read " << response.size() << " bytes";
        return false;
    }
    uint16_t remainingBytes = responseSize - response.size();
    while (remainingBytes > 0) {
        constexpr uint16_t CHUNK_SIZE = 2048;
        std::vector<uint8_t> discard(std::min(remainingBytes, CHUNK_SIZE));
        if (sslRead(netdutils::makeSlice(discard), true) != SSL_ERROR_NONE) {
            LOG(DEBUG) << mMark << " Failed to discard " << discard.size() << " bytes";
            return false;
        }
        remainingBytes -= discard.size();
    }
    LOG(DEBUG) << mMark << " SSL_read complete";

    mObserver->onResponse(std::move(response));
    return true;
}

}  // end of namespace net
}  // end of namespace android
