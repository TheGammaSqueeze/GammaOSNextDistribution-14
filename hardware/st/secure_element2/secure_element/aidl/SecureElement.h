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
#ifndef ANDROID_HARDWARE_SECURE_ELEMENT_V1_0_AIDL_SECUREELEMENT_H
#define ANDROID_HARDWARE_SECURE_ELEMENT_V1_0_AIDL_SECUREELEMENT_H

#include <aidl/android/hardware/secure_element/BnSecureElement.h>
#include <android-base/hex.h>
#include <android-base/logging.h>
#include <android/binder_manager.h>
#include <android/binder_process.h>
#include <algorithm>

using aidl::android::hardware::secure_element::BnSecureElement;
using aidl::android::hardware::secure_element::ISecureElementCallback;
using aidl::android::hardware::secure_element::LogicalChannelResponse;
using android::base::HexString;
using ndk::ScopedAStatus;

namespace se {
struct SecureElement : public BnSecureElement {
    SecureElement(const char* ese_name);
    ScopedAStatus init(const std::shared_ptr<ISecureElementCallback>& clientCallback) override;
    ScopedAStatus openLogicalChannel(const std::vector<uint8_t>& aid, int8_t p2, ::aidl::android::hardware::secure_element::LogicalChannelResponse* aidl_return) override;
    ScopedAStatus openBasicChannel(const std::vector<uint8_t>& aid, int8_t p2, std::vector<uint8_t>* aidl_return) override;
    ScopedAStatus getAtr(std::vector<uint8_t>* aidl_return) override;
    ScopedAStatus transmit(const std::vector<uint8_t>& data, std::vector<uint8_t>* aidl_return) override;
    ScopedAStatus isCardPresent(bool* aidl_return) override;
    ScopedAStatus closeChannel(int8_t channelNumber) override;
    ScopedAStatus reset() override;


    private:
    uint8_t nbrOpenChannel = 0;
    bool isBasicChannelOpen = false;
    bool checkSeUp = false;
    uint8_t atr[32];
    uint8_t atr_size;
    char config_filename[100];
    char ese_flag_name[5];
    std::shared_ptr<ISecureElementCallback> internalClientCallback;
    int initializeSE();
    int deinitializeSE();
    static int run_apdu(struct se_gto_ctx *ctx, const uint8_t *apdu, uint8_t *resp, int n, int verbose);
    static int toint(char c);
    static void dump_bytes(const char *pf, char sep, const uint8_t *p, int n, FILE *out);
    int resetSE();
    int openConfigFile(int verbose);
    int parseConfigFile(FILE *f, int verbose);
    void notify(bool state, const char *message);
};

} //se
#endif  // ANDROID_HARDWARE_SECURE_ELEMENT_V1_0_AIDL_SECUREELEMENT_H
