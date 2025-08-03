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
#include <unistd.h>
#include <cstdio>
#include <android-base/unique_fd.h>
#include <log/log.h>
#include "storage.h"

namespace aidl::android::hardware::biometrics::fingerprint {

namespace {
using ::android::base::unique_fd;

constexpr uint32_t kFileSignature = 0x46507261;

unique_fd openFile(const int32_t sensorId, const int32_t userId, const bool output) {
    char filename[64];
    ::snprintf(filename, sizeof(filename), "/data/vendor_de/%d/fpdata/sensor%d.bin",
               userId, sensorId);

    int fd;
    if (output) {
        fd = ::open(filename, O_CLOEXEC | O_WRONLY | O_CREAT | O_TRUNC, S_IRUSR | S_IWUSR);
    } else {
        fd = ::open(filename, O_CLOEXEC | O_RDONLY);
    }

    if (fd >= 0) {
        return unique_fd(fd);
    } else {
        ALOGE("%s:%d open('%s', output=%d) failed with errno=%d",
              __func__, __LINE__, filename, output, errno);
        return {};
    }
}

std::vector<uint8_t> loadFile(const int fd) {
    constexpr size_t kChunkSize = 256;
    std::vector<uint8_t> result;
    size_t size = 0;

    while (true) {
        result.resize(size + kChunkSize);
        const int n = TEMP_FAILURE_RETRY(::read(fd, &result[size], kChunkSize));
        if (n > 0) {
            size += n;
        } else if (n < 0) {
            ALOGE("%s:%d error reading from a file, errno=%d",
                  __func__, __LINE__, errno);
            return {};
        } else {
            result.resize(size);
            return result;
        }
    }
}

bool saveFile(const int fd, const uint8_t* i, size_t size) {
    while (size > 0) {
        const int n = TEMP_FAILURE_RETRY(::write(fd, i, size));
        if (n > 0) {
            i += n;
            size -= n;
        } else if (n < 0) {
            ALOGE("%s:%d error writing to a file, errno=%d",
                  __func__, __LINE__, errno);
            return false;
        } else {
            ALOGE("%s:%d `write` returned zero, size=%zu, errno=%d",
                  __func__, __LINE__, size, errno);
            return false;
        }
    }
    return true;
}

template <class T> bool loadT(const uint8_t** i, const uint8_t* end, T* x) {
    const uint8_t* p = *i;
    if ((p + sizeof(*x)) <= end) {
        memcpy(x, p, sizeof(*x));
        *i = p + sizeof(*x);
        return true;
    } else {
        return false;
    }
}

template <class T> std::vector<uint8_t>& operator<<(std::vector<uint8_t>& v, const T& x) {
    const uint8_t* x8 = reinterpret_cast<const uint8_t*>(&x);
    v.insert(v.end(), x8, x8 + sizeof(x));
    return v;
}

std::vector<uint8_t>& operator<<(std::vector<uint8_t>& v, const uint8_t x) {
    v.push_back(x);
    return v;
}

}  // namespace

Storage::Storage(const int32_t sensorId, const int32_t userId)
        : mSensorId(sensorId), mUserId(userId) {
    unique_fd file(openFile(sensorId, mUserId, false));
    if (!file.ok()) {
        return;
    }

    const std::vector<uint8_t> data = loadFile(file.get());
    const uint8_t* i = data.data();
    const uint8_t* const end = i + data.size();

    uint32_t signature;
    if (!loadT(&i, end, &signature)) {
        ALOGE("%s:%d", __func__, __LINE__);
        return;
    }
    if (signature != kFileSignature) {
        ALOGE("%s:%d", __func__, __LINE__);
        return;
    }
    if (!loadT(&i, end, &mAuthId)) {
        ALOGE("%s:%d", __func__, __LINE__);
        return;
    }
    if (!loadT(&i, end, &mSecureUserId)) {
        ALOGE("%s:%d", __func__, __LINE__);
        return;
    }
    uint8_t nEnrollments;
    if (!loadT(&i, end, &nEnrollments)) {
        ALOGE("%s:%d", __func__, __LINE__);
        return;
    }
    for (; nEnrollments > 0; --nEnrollments) {
        int32_t enrollmentId;
        if (loadT(&i, end, &enrollmentId)) {
            mEnrollments.insert(enrollmentId);
        } else {
            ALOGE("%s:%d", __func__, __LINE__);
            return;
        }
    }
}

void Storage::save() const {
    unique_fd file(openFile(mSensorId, mUserId, true));
    if (file.ok()) {
        const std::vector<uint8_t> data = serialize();
        saveFile(file.get(), data.data(), data.size());
    }
}

std::vector<uint8_t> Storage::serialize() const {
    std::vector<uint8_t> result;

    result << kFileSignature << mAuthId << mSecureUserId << uint8_t(mEnrollments.size());
    for (const int32_t enrollmentId : mEnrollments) {
        result << enrollmentId;
    }

    return result;
}

int64_t Storage::invalidateAuthenticatorId(const int64_t newAuthId) {
    mAuthId = newAuthId;
    save();
    return newAuthId;
}

std::vector<int32_t> Storage::enumerateEnrollments() const {
    return {mEnrollments.begin(), mEnrollments.end()};
}

bool Storage::enroll(const int enrollmentId,
            const int64_t secureUserId,
            const int64_t newAuthId) {
    if (mEnrollments.insert(enrollmentId).second) {
        mSecureUserId = secureUserId;
        mAuthId = newAuthId;
        save();
        return true;
    } else {
        return false;
    }
}

void Storage::removeEnrollments(const std::vector<int32_t>& enrollmentIds) {
    for (const int enrollmentId : enrollmentIds) {
        mEnrollments.erase(enrollmentId);
    }
    save();
}

std::tuple<Storage::AuthResult, int32_t, Storage::AuthToken>
Storage::authenticate(const int32_t enrollmentId) {
    const auto now = std::chrono::steady_clock::now();

    switch (mLockOut.state) {
    default:
    case LockOut::State::NO:
        break;

    case LockOut::State::TIMED:
    case LockOut::State::TIMED_LOCKED:
        if (mLockOut.nextAttempt > now) {
            mLockOut.state = LockOut::State::TIMED_LOCKED;
            const int64_t inMs =
                std::chrono::duration_cast<
                    std::chrono::milliseconds>(mLockOut.nextAttempt - now).count();
            return {AuthResult::LOCKED_OUT_TIMED, inMs, {}};
        }
        break;

    case LockOut::State::PERMANENT:
        return {AuthResult::LOCKED_OUT_PERMANENT, 0, {}};
    }

    if (mEnrollments.count(enrollmentId) > 0) {
        mLockOut.state = LockOut::State::NO;
        AuthToken tok;
        tok.userId = mSecureUserId;
        tok.authenticatorId = mAuthId;
        return {AuthResult::OK, 0, tok};
    } else {
        const int failedAttempts =
            (mLockOut.state == LockOut::State::NO)
                ? 1 : ++mLockOut.failedAttempts;

        if (failedAttempts >= 10) {
            mLockOut.state = LockOut::State::PERMANENT;
            return {AuthResult::LOCKED_OUT_PERMANENT, 0, {}};
        }

        mLockOut.state = LockOut::State::TIMED;
        if (failedAttempts >= 5) {
            mLockOut.nextAttempt = now + std::chrono::seconds(10);
            mLockOut.expiration = now + std::chrono::minutes(10);
        } else if (failedAttempts >= 3) {
            mLockOut.nextAttempt = now + std::chrono::seconds(3);
            mLockOut.expiration = now + std::chrono::minutes(1);
        } else {
            mLockOut.nextAttempt = now + std::chrono::milliseconds(500);
            mLockOut.expiration = now + std::chrono::seconds(10);
        }

        return {AuthResult::FAILED, 0, {}};
    }
}

void Storage::resetLockout() {
    mLockOut.state = LockOut::State::NO;
}

bool Storage::checkIfLockoutCleared() {
    if (mLockOut.state != LockOut::State::TIMED_LOCKED) {
        return false;
    }

    const auto now = std::chrono::steady_clock::now();
    if (now > mLockOut.expiration) {
        mLockOut.state = LockOut::State::NO;
        return true;
    } else if (now > mLockOut.nextAttempt) {
        mLockOut.state = LockOut::State::TIMED;
        return true;
    } else {
        return false;
    }
}

} // namespace aidl::android::hardware::biometrics::fingerprint
