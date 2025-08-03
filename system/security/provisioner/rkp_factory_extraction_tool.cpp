/*
 * Copyright 2021 The Android Open Source Project
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

#include <aidl/android/hardware/drm/IDrmFactory.h>
#include <aidl/android/hardware/security/keymint/IRemotelyProvisionedComponent.h>
#include <android/binder_manager.h>
#include <cppbor.h>
#include <gflags/gflags.h>
#include <keymaster/cppcose/cppcose.h>
#include <openssl/base64.h>
#include <remote_prov/remote_prov_utils.h>
#include <sys/random.h>

#include <string>
#include <vector>

#include "DrmRkpAdapter.h"
#include "rkp_factory_extraction_lib.h"

using aidl::android::hardware::drm::IDrmFactory;
using aidl::android::hardware::security::keymint::IRemotelyProvisionedComponent;
using aidl::android::hardware::security::keymint::remote_prov::jsonEncodeCsrWithBuild;

using namespace cppbor;
using namespace cppcose;

DEFINE_string(output_format, "build+csr", "How to format the output. Defaults to 'build+csr'.");
DEFINE_bool(self_test, true,
            "If true, this tool performs a self-test, validating the payload for correctness. "
            "This checks that the device on the factory line is producing valid output "
            "before attempting to upload the output to the device info service.");
DEFINE_string(serialno_prop, "ro.serialno",
              "The property of getting serial number. Defaults to 'ro.serialno'.");

namespace {

// Various supported --output_format values.
constexpr std::string_view kBinaryCsrOutput = "csr";     // Just the raw csr as binary
constexpr std::string_view kBuildPlusCsr = "build+csr";  // Text-encoded (JSON) build
                                                         // fingerprint plus CSR.

std::string getFullServiceName(const char* descriptor, const char* name) {
    return  std::string(descriptor) + "/" + name;
}

void writeOutput(const std::string instance_name, const Array& csr) {
    if (FLAGS_output_format == kBinaryCsrOutput) {
        auto bytes = csr.encode();
        std::copy(bytes.begin(), bytes.end(), std::ostream_iterator<char>(std::cout));
    } else if (FLAGS_output_format == kBuildPlusCsr) {
        auto [json, error] = jsonEncodeCsrWithBuild(instance_name, csr, FLAGS_serialno_prop);
        if (!error.empty()) {
            std::cerr << "Error JSON encoding the output: " << error;
            exit(1);
        }
        std::cout << json << std::endl;
    } else {
        std::cerr << "Unexpected output_format '" << FLAGS_output_format << "'" << std::endl;
        std::cerr << "Valid formats:" << std::endl;
        std::cerr << "  " << kBinaryCsrOutput << std::endl;
        std::cerr << "  " << kBuildPlusCsr << std::endl;
        exit(1);
    }
}

void getCsrForIRpc(const char* descriptor, const char* name, IRemotelyProvisionedComponent* irpc) {
    auto [request, errMsg] = getCsr(name, irpc, FLAGS_self_test);
    auto fullName = getFullServiceName(descriptor, name);
    if (!request) {
        std::cerr << "Unable to build CSR for '" << fullName << ": " << errMsg << std::endl;
        exit(-1);
    }

    writeOutput(std::string(name), *request);
}

// Callback for AServiceManager_forEachDeclaredInstance that writes out a CSR
// for every IRemotelyProvisionedComponent.
void getCsrForInstance(const char* name, void* /*context*/) {
    auto fullName = getFullServiceName(IRemotelyProvisionedComponent::descriptor, name);
    AIBinder* rkpAiBinder = AServiceManager_getService(fullName.c_str());
    ::ndk::SpAIBinder rkp_binder(rkpAiBinder);
    auto rkp_service = IRemotelyProvisionedComponent::fromBinder(rkp_binder);
    if (!rkp_service) {
        std::cerr << "Unable to get binder object for '" << fullName << "', skipping.";
        exit(-1);
    }

    getCsrForIRpc(IRemotelyProvisionedComponent::descriptor, name, rkp_service.get());
}

}  // namespace

int main(int argc, char** argv) {
    gflags::ParseCommandLineFlags(&argc, &argv, /*remove_flags=*/true);

    AServiceManager_forEachDeclaredInstance(IRemotelyProvisionedComponent::descriptor,
                                            /*context=*/nullptr, getCsrForInstance);

    // Append drm csr's
    for (auto const& e : android::mediadrm::getDrmRemotelyProvisionedComponents()) {
        getCsrForIRpc(IDrmFactory::descriptor, e.first.c_str(), e.second.get());
    }

    return 0;
}
