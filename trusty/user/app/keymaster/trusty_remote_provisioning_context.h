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
#pragma once

#include <keymaster/attestation_context.h>
#include <keymaster/remote_provisioning_context.h>
#include <keymaster/serializable.h>

#include <cppbor.h>

namespace keymaster {

struct BootParams {
    uint32_t boot_os_version = 0;
    uint32_t boot_os_patchlevel = 0;
    Buffer verified_boot_key;
    keymaster_verified_boot_t verified_boot_state = KM_VERIFIED_BOOT_UNVERIFIED;
    bool device_locked = false;
    Buffer verified_boot_hash;
};

/**
 * TrustyKeymasterContext provides the context for a secure implementation of
 * RemoteProvisioningContext.
 */
class TrustyRemoteProvisioningContext : public RemoteProvisioningContext {
public:
    TrustyRemoteProvisioningContext(){};
    ~TrustyRemoteProvisioningContext() override{};
    std::vector<uint8_t> DeriveBytesFromHbk(const std::string& context,
                                            size_t numBytes) const override;
    std::unique_ptr<cppbor::Map> CreateDeviceInfo(
            uint32_t csrVersion) const override;
    cppcose::ErrMsgOr<std::vector<uint8_t>> BuildProtectedDataPayload(
            bool testMode,
            const std::vector<uint8_t>& macKey,
            const std::vector<uint8_t>& aad) const override;
    std::optional<cppcose::HmacSha256> GenerateHmacSha256(
            const cppcose::bytevec& input) const override;
    void GetHwInfo(GetHwInfoResponse* hwInfo) const override;
    cppcose::ErrMsgOr<cppbor::Array> BuildCsr(
            const std::vector<uint8_t>& challenge,
            cppbor::Array keysToSign) const override;

    void SetBootParams(const BootParams* bootParams);
    void SetVendorPatchlevel(uint32_t vendor_patchlevel) {
        vendor_patchlevel_ = vendor_patchlevel;
    }

    void SetBootPatchlevel(uint32_t boot_patchlevel) {
        boot_patchlevel_ = boot_patchlevel;
    }

private:
    bool bootParamsSet_ = false;
    const BootParams* bootParams_ = nullptr;
    uint32_t vendor_patchlevel_ = 0;
    uint32_t boot_patchlevel_ = 0;
};

}  // namespace keymaster
