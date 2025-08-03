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

#include <hardware/keymaster_defs.h>
#include <keymaster/remote_provisioning_context.h>

#include <cppbor.h>

#include <cstdint>
#include <mutex>
#include <string>
#include <string_view>
#include <vector>

namespace keymaster {

/**
 * SoftKeymasterContext provides the context for a non-secure implementation of AndroidKeymaster.
 */
class PureSoftRemoteProvisioningContext : public RemoteProvisioningContext {
  public:
    explicit PureSoftRemoteProvisioningContext(keymaster_security_level_t security_level);
    ~PureSoftRemoteProvisioningContext() override = default;
    std::vector<uint8_t> DeriveBytesFromHbk(const std::string& context,
                                            size_t numBytes) const override;
    std::unique_ptr<cppbor::Map> CreateDeviceInfo(uint32_t csrVersion) const override;
    cppcose::ErrMsgOr<std::vector<uint8_t>>
    BuildProtectedDataPayload(bool isTestMode,                     //
                              const std::vector<uint8_t>& macKey,  //
                              const std::vector<uint8_t>& aad) const override;
    std::optional<cppcose::HmacSha256>
    GenerateHmacSha256(const cppcose::bytevec& input) const override;
    void GetHwInfo(GetHwInfoResponse* hwInfo) const override;
    cppcose::ErrMsgOr<cppbor::Array> BuildCsr(const std::vector<uint8_t>& challenge,
                                              cppbor::Array keysToSign) const override;

    void SetSystemVersion(uint32_t os_version, uint32_t os_patchlevel);
    void SetVendorPatchlevel(uint32_t vendor_patchlevel);
    void SetBootPatchlevel(uint32_t boot_patchlevel);
    void SetVerifiedBootInfo(std::string_view boot_state, std::string_view bootloader_state,
                             const std::vector<uint8_t>& vbmeta_digest);

  private:
    // Initialize the BCC if it has not yet happened.
    void LazyInitProdBcc() const;

    std::pair<std::vector<uint8_t>, cppbor::Array> GenerateBcc(bool testMode) const;

    keymaster_security_level_t security_level_;
    std::optional<uint32_t> os_version_;
    std::optional<uint32_t> os_patchlevel_;
    std::optional<uint32_t> vendor_patchlevel_;
    std::optional<uint32_t> boot_patchlevel_;
    std::optional<std::string> verified_boot_state_;
    std::optional<std::string> bootloader_state_;
    std::optional<std::vector<uint8_t>> vbmeta_digest_;

    mutable std::once_flag bccInitFlag_;

    // Always call LazyInitProdBcc before accessing these values, as they are
    // lazy-initialized.
    mutable std::vector<uint8_t> devicePrivKey_;
    mutable cppbor::Array bcc_;
};

}  // namespace keymaster
