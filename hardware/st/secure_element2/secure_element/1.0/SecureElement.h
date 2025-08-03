/*****************************************************************************
 * Copyright ©2017-2019 Gemalto – a Thales Company. All rights Reserved.
 *
 * This copy is licensed under the Apache License, Version 2.0 (the "License");
 * You may not use this file except in compliance with the License.
 * You may obtain a copy of the License at:
 *     http://www.apache.org/licenses/LICENSE-2.0 or https://www.apache.org/licenses/LICENSE-2.0.html
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and limitations under the License.

 ****************************************************************************/
#ifndef ANDROID_HARDWARE_SECURE_ELEMENT_V1_0_SECUREELEMENT_H
#define ANDROID_HARDWARE_SECURE_ELEMENT_V1_0_SECUREELEMENT_H

#include <android/hardware/secure_element/1.0/ISecureElement.h>
#include <hidl/MQDescriptor.h>
#include <hidl/Status.h>

namespace android {
namespace hardware {
namespace secure_element {
namespace V1_0 {
namespace implementation {

using ::android::hardware::hidl_array;
using ::android::hardware::hidl_memory;
using ::android::hardware::hidl_string;
using ::android::hardware::hidl_vec;
using ::android::hardware::Return;
using ::android::hardware::Void;
using ::android::sp;


struct SecureElement : public ISecureElement , public hidl_death_recipient {
    // Methods from ::android::hardware::secure_element::V1_0::ISecureElement follow.
    SecureElement(const char* ese_name);
    Return<void> init(const sp<V1_0::ISecureElementHalCallback>& clientCallback) override;
    Return<void> openLogicalChannel(const hidl_vec<uint8_t>& aid, uint8_t p2, openLogicalChannel_cb _hidl_cb) override;
    Return<void> openBasicChannel(const hidl_vec<uint8_t>& aid, uint8_t p2, openBasicChannel_cb _hidl_cb) override;
    Return<void> getAtr(getAtr_cb _hidl_cb) override;
    Return<void> transmit(const hidl_vec<uint8_t>& data, transmit_cb _hidl_cb) override;
    Return<bool> isCardPresent() override;
    Return<::android::hardware::secure_element::V1_0::SecureElementStatus> closeChannel(uint8_t channelNumber) override;


    // Methods from ::android::hidl::base::V1_0::IBase follow.
    private:
    uint8_t nbrOpenChannel = 0;
    bool isBasicChannelOpen = false;
    bool checkSeUp = false;
    uint8_t atr[32];
    uint8_t atr_size;
    char config_filename[100];
    static sp<V1_0::ISecureElementHalCallback> internalClientCallback;
    int initializeSE();
    Return<::android::hardware::secure_element::V1_0::SecureElementStatus> deinitializeSE();
    void serviceDied(uint64_t, const wp<IBase>&) override;
    static int run_apdu(struct se_gto_ctx *ctx, const uint8_t *apdu, uint8_t *resp, int n, int verbose);
    static int toint(char c);
    static void dump_bytes(const char *pf, char sep, const uint8_t *p, int n, FILE *out);
    int resetSE();
    int openConfigFile(int verbose);
    int parseConfigFile(FILE *f, int verbose);
};

}  // namespace implementation
}  // namespace V1_0
}  // namespace secure_element
}  // namespace hardware
}  // namespace android

#endif  // ANDROID_HARDWARE_SECURE_ELEMENT_V1_0_SECUREELEMENT_H
