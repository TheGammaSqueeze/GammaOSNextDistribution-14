/*
 * Copyright (C) 2022 The Android Open Source Project
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

#include <fcntl.h>
#include <inttypes.h>
#include <sys/epoll.h>
#include <sys/socket.h>
#include <chrono>
#include <limits>
#include <aidl/android/hardware/biometrics/common/BnCancellationSignal.h>
#include <android-base/unique_fd.h>
#include <log/log.h>
#include <qemud.h>
#include <utils/Timers.h>

#include "session.h"
#include "storage.h"

namespace aidl::android::hardware::biometrics::fingerprint {

using ::android::base::unique_fd;

namespace {
constexpr char kSensorServiceName[] = "fingerprintlisten";
constexpr char kSensorListenerQuitCmd = 'Q';

int64_t generateSeed(void* p) {
    auto now = std::chrono::high_resolution_clock::now();
    decltype(now) epoch;
    return (now - epoch).count() ^ reinterpret_cast<uintptr_t>(p);
}

int epollCtlAdd(int epollFd, int fd) {
    int ret;

    /* make the fd non-blocking */
    ret = TEMP_FAILURE_RETRY(fcntl(fd, F_GETFL));
    if (ret < 0) {
        return ret;
    }

    ret = TEMP_FAILURE_RETRY(fcntl(fd, F_SETFL, ret | O_NONBLOCK));
    if (ret < 0) {
        return ret;
    }

    struct epoll_event ev;
    ev.events  = EPOLLIN;
    ev.data.fd = fd;

    return TEMP_FAILURE_RETRY(epoll_ctl(epollFd, EPOLL_CTL_ADD, fd, &ev));
}

template <class T> std::string vec2str(const std::vector<T>& v) {
    if (v.empty()) {
        return "empty";
    } else {
        std::string result;

        for (const auto& x : v) {
            if (result.empty()) {
                result = std::to_string(x);
            } else {
                result += ",";
                result += std::to_string(x);
            }
        }

        return std::string("[") + result + std::string("]");
    }
}

const char* state2str(const Session::State s) {
    switch (s) {
    case Session::State::IDLE:                  return "IDLE";
    case Session::State::ENROLLING_START:       return "ENROLLING_START";
    case Session::State::ENROLLING_END:         return "ENROLLING_END";
    case Session::State::AUTHENTICATING:        return "AUTHENTICATING";
    case Session::State::DETECTING_INTERACTION: return "DETECTING_INTERACTION";
    default:                                    return "?";
    }
}

}  // namespace

struct CancellationSignal : public common::BnCancellationSignal {
    CancellationSignal(std::function<void()> cb) : mCB(std::move(cb)) {}

    ndk::ScopedAStatus  cancel() override {
        mCB();
        return ndk::ScopedAStatus::ok();
    }

    const std::function<void()> mCB;
};

Session::Session(const int32_t sensorId, const int32_t userId,
                 std::shared_ptr<ISessionCallback> scb)
    : mSessionCb(std::move(scb))
    , mStorage(sensorId, userId)
    , mRandom(generateSeed(this))
 {
    ALOGD("%p:%s: New session: sensorId=%d userId=%d",
          this, __func__, sensorId, userId);

    if (::android::base::Socketpair(AF_LOCAL, SOCK_STREAM, 0,
                                    &mCallerFd, &mSensorThreadFd)) {
        mSensorListener = std::thread(&Session::sensorListenerFunc, this);
    } else {
        mSensorListener = std::thread([](){});
        LOG_ALWAYS_FATAL("%p:%s: Socketpair failed", this, __func__);
    }
}

Session::~Session() {
    ALOGD("%p:%s: Terminating session", this, __func__);

    TEMP_FAILURE_RETRY(write(mCallerFd.get(), &kSensorListenerQuitCmd, 1));
    mSensorListener.join();
}

ndk::ScopedAStatus Session::generateChallenge() {
    while (true) {
        int64_t challenge;
        {
            std::lock_guard<std::mutex> lock(mMutex);
            challenge = generateInt64();
        }

        if (mChallenges.insert(challenge).second) {
            ALOGD("%p:%s: onChallengeGenerated(challenge=%" PRId64 ")",
                  this, __func__, challenge);
            mSessionCb->onChallengeGenerated(challenge);
            return ndk::ScopedAStatus::ok();
        }
    }
}

ndk::ScopedAStatus Session::revokeChallenge(const int64_t challenge) {
    mChallenges.erase(challenge);
    ALOGD("%p:%s: onChallengeRevoked(challenge=%" PRId64 ")",
          this, __func__, challenge);
    mSessionCb->onChallengeRevoked(challenge);
    return ndk::ScopedAStatus::ok();
}

ndk::ScopedAStatus Session::enroll(const keymaster::HardwareAuthToken& hat,
                                   std::shared_ptr<common::ICancellationSignal>* out) {
    const ErrorCode err = validateHat(hat);
    if (err == ErrorCode::OK) {
        State previousState;
        bool ok;
        {
            std::lock_guard<std::mutex> lock(mMutex);
            previousState = mState;
            if (previousState == State::IDLE) {
                mEnrollingSecUserId = hat.userId;
                mState = State::ENROLLING_START;
                ok = true;
            } else {
                ok = false;
            }
        }

        if (ok) {
            ALOGD("%p:%s: ENROLLING_START hat.userId=%" PRId64,
                  this, __func__, hat.userId);
            *out = SharedRefBase::make<CancellationSignal>([this](){ cancellEnroll(); });
        } else {
            ALOGE("%p:%s: onError(UNABLE_TO_PROCESS, %d): incorrect state, %s",
                  this, __func__, int(ErrorCode::E_INCORRECT_STATE),
                  state2str(previousState));
            mSessionCb->onError(Error::UNABLE_TO_PROCESS,
                                int(ErrorCode::E_INCORRECT_STATE));
        }
    } else {
        ALOGE("%p:%s: onError(UNABLE_TO_PROCESS, %d): `hat` is invalid",
              this, __func__, int(err));
        mSessionCb->onError(Error::UNABLE_TO_PROCESS, int(err));
    }

    return ndk::ScopedAStatus::ok();
}

ndk::ScopedAStatus Session::authenticate(const int64_t operationId,
                                         std::shared_ptr<common::ICancellationSignal>* out) {
    State previousState;
    bool ok;
    {
        std::lock_guard<std::mutex> lock(mMutex);
        previousState = mState;
        if (previousState == State::IDLE) {
            mAuthChallenge = operationId;
            mState = State::AUTHENTICATING;
            ok = true;
        } else {
            ok = false;
        }
    }

    if (ok) {
        ALOGD("%p:%s: AUTHENTICATING operationId=%" PRId64, this, __func__, operationId);
        *out = SharedRefBase::make<CancellationSignal>([this](){ cancellAuthenticate(); });
    } else {
        ALOGE("%p:%s: onError(UNABLE_TO_PROCESS, %d): incorrect state, %s",
              this, __func__, int(ErrorCode::E_INCORRECT_STATE),
              state2str(previousState));
        mSessionCb->onError(Error::UNABLE_TO_PROCESS,
                            int(ErrorCode::E_INCORRECT_STATE));
    }

    return ndk::ScopedAStatus::ok();
}

ndk::ScopedAStatus Session::detectInteraction(
        std::shared_ptr<common::ICancellationSignal>* out) {
    State previousState;
    bool ok;
    {
        std::lock_guard<std::mutex> lock(mMutex);
        previousState = mState;
        if (previousState == State::IDLE) {
            mState = State::DETECTING_INTERACTION;
            ok = true;
        } else {
            ok = false;
        }
    }

    if (ok) {
        ALOGD("%p:%s DETECTING_INTERACTION", this, __func__);
        *out = SharedRefBase::make<CancellationSignal>([this](){ cancellDetectInteraction(); });
    } else {
        ALOGE("%p:%s: onError(UNABLE_TO_PROCESS, %d): incorrect state, %s",
              this, __func__, int(ErrorCode::E_INCORRECT_STATE),
              state2str(previousState));
        mSessionCb->onError(Error::UNABLE_TO_PROCESS,
                            int(ErrorCode::E_INCORRECT_STATE));
    }

    return ndk::ScopedAStatus::ok();
}

ndk::ScopedAStatus Session::enumerateEnrollments() {
    std::vector<int32_t> enrollmentIds;
    {
        std::lock_guard<std::mutex> lock(mMutex);
        enrollmentIds = mStorage.enumerateEnrollments();
    }

    ALOGD("%p:%s: onEnrollmentsEnumerated(enrollmentIds=%s)",
          this, __func__, vec2str(enrollmentIds).c_str());
    mSessionCb->onEnrollmentsEnumerated(enrollmentIds);
    return ndk::ScopedAStatus::ok();
}

ndk::ScopedAStatus Session::removeEnrollments(const std::vector<int32_t>& enrollmentIds) {
    {
        std::lock_guard<std::mutex> lock(mMutex);
        mStorage.removeEnrollments(enrollmentIds);
    }

    ALOGD("%p:%s: onEnrollmentsRemoved(enrollmentIds=%s)",
          this, __func__, vec2str(enrollmentIds).c_str());
    mSessionCb->onEnrollmentsRemoved(enrollmentIds);
    return ndk::ScopedAStatus::ok();
}

ndk::ScopedAStatus Session::getAuthenticatorId() {
    int64_t authId;
    {
        std::lock_guard<std::mutex> lock(mMutex);
        authId = mStorage.getAuthenticatorId();
    }

    ALOGD("%p:%s: onAuthenticatorIdRetrieved(authId=%" PRId64 ")",
          this, __func__, authId);
    mSessionCb->onAuthenticatorIdRetrieved(authId);
    return ndk::ScopedAStatus::ok();
}

ndk::ScopedAStatus Session::invalidateAuthenticatorId() {
    int64_t authId;
    {
        std::lock_guard<std::mutex> lock(mMutex);
        authId = mStorage.invalidateAuthenticatorId(generateInt64());
    }

    ALOGD("%p:%s: onAuthenticatorIdInvalidated(authId=%" PRId64 ")",
          this, __func__, authId);
    mSessionCb->onAuthenticatorIdInvalidated(authId);
    return ndk::ScopedAStatus::ok();
}

ndk::ScopedAStatus Session::resetLockout(const keymaster::HardwareAuthToken& hat) {
    const ErrorCode err = validateHat(hat);
    if (err == ErrorCode::OK) {
        {
            std::lock_guard<std::mutex> lock(mMutex);
            mStorage.resetLockout();
        }

        ALOGD("%p:%s: onLockoutCleared", this, __func__);
        mSessionCb->onLockoutCleared();
    } else {
        ALOGE("%p:%s: onError(UNABLE_TO_PROCESS, %d): `hat` is invalid",
              this, __func__, int(err));
        mSessionCb->onError(Error::UNABLE_TO_PROCESS, int(err));
    }
    return ndk::ScopedAStatus::ok();
}

ndk::ScopedAStatus Session::close() {
    mChallenges.clear();
    ALOGD("%p:%s: onSessionClosed", this, __func__);
    mSessionCb->onSessionClosed();
    return ndk::ScopedAStatus::ok();
}

Session::ErrorCode Session::validateHat(const keymaster::HardwareAuthToken& hat) const {
    if (hat.mac.empty()) {
        return ErrorCode::E_HAT_MAC_EMPTY;
    }

    if (!mChallenges.count(hat.challenge)) {
        return ErrorCode::E_HAT_WRONG_CHALLENGE;
    }

    return ErrorCode::OK;
}

int64_t Session::generateInt64() {
    std::uniform_int_distribution<int64_t> distrib(1, std::numeric_limits<int64_t>::max());
    return distrib(mRandom);
}

void Session::onSenserEventOn(const int32_t enrollmentId) {
    std::lock_guard<std::mutex> lock(mMutex);
    switch (mState) {
    case State::ENROLLING_START:
    case State::ENROLLING_END:
        {
            ALOGD("%p:%s: onAcquired(GOOD, %d)", this, __func__, 0);
            mSessionCb->onAcquired(AcquiredInfo::GOOD, 0);

            const int left = int(State::ENROLLING_END) - int(mState);
            if (left > 0) {
                ALOGD("%p:%s: onEnrollmentProgress(enrollmentId=%d, left=%d)",
                      this, __func__, enrollmentId, left);
                mSessionCb->onEnrollmentProgress(enrollmentId, left);
                mState = State(int(mState) + 1);
            } else if (mStorage.enroll(enrollmentId, mEnrollingSecUserId, generateInt64())) {
                ALOGD("%p:%s: onEnrollmentProgress(enrollmentId=%d, left=%d)",
                      this, __func__, enrollmentId, left);
                mSessionCb->onEnrollmentProgress(enrollmentId, left);
                mState = State::IDLE;
            } else {
                ALOGE("%p:%s: onError(UNABLE_TO_PROCESS, %d): enrollmentId=%d, "
                      "secureIserId=%" PRId64 ,
                      this, __func__, int(ErrorCode::E_ENROLL_FAILED),
                      enrollmentId, mEnrollingSecUserId);
                mSessionCb->onError(Error::UNABLE_TO_PROCESS,
                                    int(ErrorCode::E_ENROLL_FAILED));
                mState = State::IDLE;
            }
        }
        break;

    case State::AUTHENTICATING:
        {
            const auto [res, lockoutDurationMillis, tok] =
                mStorage.authenticate(enrollmentId);
            if (res != Storage::AuthResult::LOCKED_OUT_PERMANENT) {
                ALOGD("%p:%s: onAcquired(GOOD, %d)", this, __func__, 0);
                mSessionCb->onAcquired(AcquiredInfo::GOOD, 0);
            }

            switch (res) {
            case Storage::AuthResult::OK: {
                    ALOGD("%p:%s: onAuthenticationSucceeded(enrollmentId=%d, "
                          "hat={ .challenge=%" PRId64 ", .userId=%" PRId64 ", "
                          ".authenticatorId=%" PRId64 " })",
                          this, __func__, enrollmentId, mAuthChallenge,
                          tok.userId, tok.authenticatorId);

                    keymaster::HardwareAuthToken hat;
                    hat.challenge = mAuthChallenge;
                    hat.userId = tok.userId;
                    hat.authenticatorId = tok.authenticatorId;
                    hat.authenticatorType = keymaster::HardwareAuthenticatorType::FINGERPRINT;
                    hat.timestamp.milliSeconds = ns2ms(systemTime(SYSTEM_TIME_BOOTTIME));
                    mSessionCb->onAuthenticationSucceeded(enrollmentId, hat);
                    mState = State::IDLE;
                }
                break;

            case Storage::AuthResult::FAILED:
                ALOGE("%p:%s: onAuthenticationFailed: enrollmentId=%d",
                      this, __func__, enrollmentId);
                mSessionCb->onAuthenticationFailed();
                break;

            case Storage::AuthResult::LOCKED_OUT_TIMED:
                ALOGE("%p:%s: onLockoutTimed(durationMillis=%d): enrollmentId=%d",
                      this, __func__, lockoutDurationMillis, enrollmentId);
                mSessionCb->onLockoutTimed(lockoutDurationMillis);
                mState = State::IDLE;
                break;

            case Storage::AuthResult::LOCKED_OUT_PERMANENT:
                ALOGE("%p:%s: onLockoutPermanent: enrollmentId=%d",
                      this, __func__, enrollmentId);
                mSessionCb->onLockoutPermanent();
                mState = State::IDLE;
                break;

            default:
                LOG_ALWAYS_FATAL("Unexpected result from `mStorage.authenticate`");
                break;
            }
        }
        break;

    case State::DETECTING_INTERACTION:
        mSessionCb->onInteractionDetected();
        mState = State::IDLE;
        break;

    case State::IDLE:
        break;

    default:
        LOG_ALWAYS_FATAL("Unexpected session state");
        break;
    }
}

void Session::onSenserEventOff() {}

void Session::cancellEnroll() {
    {
        std::lock_guard<std::mutex> lock(mMutex);
        if ((mState >= State::ENROLLING_START) && (mState <= State::ENROLLING_END)) {
            mState = State::IDLE;
        }
    }

    ALOGD("%p:%s: onError(CANCELED, %d)", this, __func__, 0);
    mSessionCb->onError(Error::CANCELED, 0);
}

void Session::cancellAuthenticate() {
    {
        std::lock_guard<std::mutex> lock(mMutex);
        if (mState == State::AUTHENTICATING) {
            mState = State::IDLE;
        }
    }

    ALOGD("%p:%s: onError(CANCELED, %d)", this, __func__, 0);
    mSessionCb->onError(Error::CANCELED, 0);
}

void Session::cancellDetectInteraction() {
    {
        std::lock_guard<std::mutex> lock(mMutex);
        if (mState == State::DETECTING_INTERACTION) {
            mState = State::IDLE;
        }
    }

    ALOGD("%p:%s: onError(CANCELED, %d)", this, __func__, 0);
    mSessionCb->onError(Error::CANCELED, 0);
}

bool Session::sensorListenerFuncImpl() {
    unique_fd sensorFd(qemud_channel_open(kSensorServiceName));
    LOG_ALWAYS_FATAL_IF(!sensorFd.ok(), "Could not open the sensor service: '%s'",
                        kSensorServiceName);

    const unique_fd epollFd(epoll_create1(EPOLL_CLOEXEC));
    epollCtlAdd(epollFd.get(), sensorFd.get());
    epollCtlAdd(epollFd.get(), mSensorThreadFd.get());

    qemud_channel_send(sensorFd.get(), "listen", 6);

    while (true) {
        const int kTimeoutMs = 250;
        struct epoll_event event;
        const int n = TEMP_FAILURE_RETRY(epoll_wait(epollFd.get(),
                                                    &event, 1,
                                                    kTimeoutMs));
        if (n <= 0) {
            bool lockoutCleared;
            {
                std::lock_guard<std::mutex> lock(mMutex);
                lockoutCleared = mStorage.checkIfLockoutCleared();
            }

            if (lockoutCleared) {
                ALOGD("%p:%s: onLockoutCleared", this, __func__);
                mSessionCb->onLockoutCleared();
            }
            continue;
        }

        const int fd = event.data.fd;
        const int ev_events = event.events;
        if (fd == sensorFd.get()) {
            if (ev_events & (EPOLLERR | EPOLLHUP)) {
                ALOGE("%p:%s: epoll_wait: devFd has an error, ev_events=%x",
                      this, __func__, ev_events);
                return true;
            } else if (ev_events & EPOLLIN) {
                char buf[64];
                int n = qemud_channel_recv(fd, buf, sizeof(buf) - 1);
                if (n > 0) {
                    buf[n] = 0;
                    int32_t fid;
                    if (sscanf(buf, "on:%d", &fid) == 1) {
                        if (fid > 0) {
                            onSenserEventOn(fid);
                        } else {
                            ALOGE("%p:%s: incorrect fingerprint: %d",
                                  this, __func__, fid);
                        }
                    } else if (!strcmp(buf, "off")) {
                        onSenserEventOff();
                    } else {
                        ALOGE("%p:%s: unexpected hw message: '%s'",
                              this, __func__, buf);
                        return true;
                    }
                } else {
                    ALOGE("%p:%s: hw read error, n=%d, errno=%d",
                          this, __func__, __LINE__, n, errno);
                    return true;
                }
            }
        } else if (fd == mSensorThreadFd.get()) {
            if (ev_events & (EPOLLERR | EPOLLHUP)) {
                LOG_ALWAYS_FATAL("%p:%s: epoll_wait: threadsFd has an error, ev_events=%x",
                                 this, __func__, ev_events);
            } else if (ev_events & EPOLLIN) {
                char cmd;
                int n = TEMP_FAILURE_RETRY(read(fd, &cmd, sizeof(cmd)));
                if (n == 1) {
                    switch (cmd) {
                    case kSensorListenerQuitCmd:
                        return false;  // quit

                    default:
                        LOG_ALWAYS_FATAL("%p:%s: unexpected command, cmd=%c",
                                         this, __func__, cmd);
                        break;
                    }
                } else {
                    LOG_ALWAYS_FATAL("%p:%s: error readind from mThreadsFd, errno=%d",
                                     this, __func__, errno);
                }
            }
        } else {
            ALOGE("%p:%s: epoll_wait() returned unexpected fd",
                  this, __func__);
        }
    }
}

}  // namespace aidl::android::hardware::biometrics::fingerprint
