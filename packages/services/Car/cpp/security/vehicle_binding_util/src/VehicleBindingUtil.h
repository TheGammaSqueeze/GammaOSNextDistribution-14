/*
 * Copyright (C) 2020 The Android Open Source Project
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
#ifndef CPP_SECURITY_VEHICLE_BINDING_UTIL_SRC_VEHICLEBINDINGUTIL_H_
#define CPP_SECURITY_VEHICLE_BINDING_UTIL_SRC_VEHICLEBINDINGUTIL_H_

#include "android/hardware/automotive/vehicle/2.0/types.h"

#include <utils/StrongPointer.h>

#include <IVhalClient.h>

#include <cstdint>
#include <vector>

namespace android {
namespace automotive {
namespace security {

constexpr size_t SEED_BYTE_SIZE = 16;

// Possible results of attempting to set the vehicle binding seed.
enum class BindingStatus {
    OK,
    NOT_SUPPORTED,
    ERROR,
    WAIT_VHAL_TIMEOUT,
};

template <typename EnumT>
constexpr auto toInt(const EnumT value) {
    return static_cast<typename std::underlying_type<EnumT>::type>(value);
}

// Interface for getting cryptographically secure random byte strings
class Csrng {
public:
    virtual ~Csrng() = default;

    // Fill the given buffer with random bytes. Returns false if there is
    // an unrecoverable error getting bits.
    virtual bool fill(void* buffer, size_t size) const = 0;
};

// Csrng that relies on `/dev/urandom` to supply bits. We have to rely on
// urandom so that we don't block boot-up. Devices that wish to supply very
// high-quality random bits at boot should seed the linux PRNG at boot with
// entropy.
class DefaultCsrng : public Csrng {
public:
    bool fill(void* buffer, size_t size) const override;
};

// Interface for forking and executing a child process.
class Executor {
public:
    virtual ~Executor() = default;

    // Run the given command line and its arguments. Returns 0 on success, -1
    // if an internal error occurred, and -ECHILD if the child process did not
    // exit properly.
    //
    // On exit, `exit_code` is set to the child's exit status.
    virtual int run(const std::vector<std::string>& cmd_args, int* exit_code) const = 0;
};

// Default Executor which forks, execs, and logs output from the child process.
class DefaultExecutor : public Executor {
    int run(const std::vector<std::string>& cmd_args, int* exit_code) const override;
};

// Set the seed in vold that is used to bind the encryption keys to the vehicle.
// This is used to guard against headunit removal and subsequent scraping of
// the filesystem for sensitive data (e.g. PII).
//
// The seed is read from the VHAL property STORAGE_ENCRYPTION_BINDING_SEED. If
// the property has not yet been set, a random byte value is generated and
// saved in the VHAL for reuse on future boots.
BindingStatus setVehicleBindingSeed(
        std::shared_ptr<android::frameworks::automotive::vhal::IVhalClient> vehicle,
        const Executor& executor, const Csrng& csrng);

}  // namespace security
}  // namespace automotive
}  // namespace android

#endif  // CPP_SECURITY_VEHICLE_BINDING_UTIL_SRC_VEHICLEBINDINGUTIL_H_
