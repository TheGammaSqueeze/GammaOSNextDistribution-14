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

#define TLOG_TAG "binder-test-service"

#include <inttypes.h>
#include <lib/tipc/tipc.h>
#include <lk/err_ptr.h>
#include <stdio.h>
#include <sys/mman.h>
#include <trusty/sys/mman.h>
#include <trusty_log.h>
#include <uapi/mm.h>

#include <vector>

#include <binder/RpcServerTrusty.h>

#include <com/android/trusty/binder/test/BnTestService.h>

using android::String16;
using android::binder::Status;

using com::android::trusty::binder::test::BnTestService;
using com::android::trusty::binder::test::ByteEnum;
using com::android::trusty::binder::test::IntEnum;
using com::android::trusty::binder::test::ITestService;
using com::android::trusty::binder::test::LongEnum;

template <typename T>
Status ReverseArray(const std::vector<T>& input,
                    std::vector<T>* repeated,
                    std::vector<T>* _aidl_return) {
    TLOGI("Reversing array of length %zu\n", input.size());
    *repeated = input;
    *_aidl_return = input;
    std::reverse(_aidl_return->begin(), _aidl_return->end());
    return Status::ok();
}

class TestService : public BnTestService {
public:
    Status RepeatBoolean(bool token, bool* _aidl_return) override {
        return Repeat(token, _aidl_return);
    }
    Status RepeatByte(int8_t token, int8_t* _aidl_return) override {
        return Repeat(token, _aidl_return);
    }
    Status RepeatChar(char16_t token, char16_t* _aidl_return) override {
        return Repeat(token, _aidl_return);
    }
    Status RepeatInt(int32_t token, int32_t* _aidl_return) override {
        return Repeat(token, _aidl_return);
    }
    Status RepeatLong(int64_t token, int64_t* _aidl_return) override {
        return Repeat(token, _aidl_return);
    }
    Status RepeatFloat(float token, float* _aidl_return) override {
        return Repeat(token, _aidl_return);
    }
    Status RepeatDouble(double token, double* _aidl_return) override {
        return Repeat(token, _aidl_return);
    }
    Status RepeatString(const String16& token,
                        String16* _aidl_return) override {
        return Repeat(token, _aidl_return);
    }
    Status RepeatByteEnum(ByteEnum token, ByteEnum* _aidl_return) override {
        return Repeat(token, _aidl_return);
    }
    Status RepeatIntEnum(IntEnum token, IntEnum* _aidl_return) override {
        return Repeat(token, _aidl_return);
    }
    Status RepeatLongEnum(LongEnum token, LongEnum* _aidl_return) override {
        return Repeat(token, _aidl_return);
    }

    Status ReverseBoolean(const std::vector<bool>& input,
                          std::vector<bool>* repeated,
                          std::vector<bool>* _aidl_return) override {
        return ReverseArray(input, repeated, _aidl_return);
    }
    Status ReverseByte(const std::vector<uint8_t>& input,
                       std::vector<uint8_t>* repeated,
                       std::vector<uint8_t>* _aidl_return) override {
        return ReverseArray(input, repeated, _aidl_return);
    }
    Status ReverseChar(const std::vector<char16_t>& input,
                       std::vector<char16_t>* repeated,
                       std::vector<char16_t>* _aidl_return) override {
        return ReverseArray(input, repeated, _aidl_return);
    }
    Status ReverseInt(const std::vector<int32_t>& input,
                      std::vector<int32_t>* repeated,
                      std::vector<int32_t>* _aidl_return) override {
        return ReverseArray(input, repeated, _aidl_return);
    }
    Status ReverseLong(const std::vector<int64_t>& input,
                       std::vector<int64_t>* repeated,
                       std::vector<int64_t>* _aidl_return) override {
        return ReverseArray(input, repeated, _aidl_return);
    }
    Status ReverseFloat(const std::vector<float>& input,
                        std::vector<float>* repeated,
                        std::vector<float>* _aidl_return) override {
        return ReverseArray(input, repeated, _aidl_return);
    }
    Status ReverseDouble(const std::vector<double>& input,
                         std::vector<double>* repeated,
                         std::vector<double>* _aidl_return) override {
        return ReverseArray(input, repeated, _aidl_return);
    }
    Status ReverseString(const std::vector<String16>& input,
                         std::vector<String16>* repeated,
                         std::vector<String16>* _aidl_return) override {
        return ReverseArray(input, repeated, _aidl_return);
    }
    Status ReverseByteEnum(const std::vector<ByteEnum>& input,
                           std::vector<ByteEnum>* repeated,
                           std::vector<ByteEnum>* _aidl_return) override {
        return ReverseArray(input, repeated, _aidl_return);
    }
    Status ReverseIntEnum(const std::vector<IntEnum>& input,
                          std::vector<IntEnum>* repeated,
                          std::vector<IntEnum>* _aidl_return) override {
        return ReverseArray(input, repeated, _aidl_return);
    }
    Status ReverseLongEnum(const std::vector<LongEnum>& input,
                           std::vector<LongEnum>* repeated,
                           std::vector<LongEnum>* _aidl_return) override {
        return ReverseArray(input, repeated, _aidl_return);
    }

private:
    template <typename T>
    Status Repeat(const T& in, T* out) {
        *out = in;
        return Status::ok();
    }
};

int main(void) {
    TLOGI("Starting service\n");

    tipc_hset* hset = tipc_hset_create();
    if (IS_ERR(hset)) {
        TLOGE("Failed to create handle set (%d)\n", PTR_ERR(hset));
        return EXIT_FAILURE;
    }

    const auto port_acl = android::RpcServerTrusty::PortAcl{
            .flags = IPC_PORT_ALLOW_TA_CONNECT | IPC_PORT_ALLOW_NS_CONNECT,
    };

    // message size needs to be large enough to cover all messages sent by the
    // tests
    constexpr size_t max_msg_size = 256;
    auto srv = android::RpcServerTrusty::make(
            hset, ITestService::PORT().c_str(),
            std::make_shared<const android::RpcServerTrusty::PortAcl>(port_acl),
            max_msg_size);
    if (!srv.ok()) {
        TLOGE("Failed to create RpcServer (%d)\n", srv.error());
        return EXIT_FAILURE;
    }

    android::sp<TestService> test_srv = android::sp<TestService>::make();
    (*srv)->setRootObject(test_srv);

    return tipc_run_event_loop(hset);
}
