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
#include <fuzzer/FuzzedDataProvider.h>

#include <cppbor.h>
#include <cppbor_parse.h>

void FuzzStringParsing(const uint8_t* data, size_t size) {
    FuzzedDataProvider dataProvider(data, size);
    std::string contents = dataProvider.ConsumeRemainingBytesAsString();
    cppbor::Tstr val(contents);
    cppbor::parse(val.encode());
}

void FuzzVectorParsing(const uint8_t* data, size_t size) {
    FuzzedDataProvider dataProvider(data, size);
    size_t keySize = dataProvider.remaining_bytes() / 4;
    std::string key1 = dataProvider.ConsumeBytesAsString(keySize);
    std::string key2 = dataProvider.ConsumeBytesAsString(keySize);
    std::vector<uint8_t> contentsBytes = dataProvider.ConsumeRemainingBytes<uint8_t>();
    cppbor::Map map;
    map.add(key1, cppbor::Array().add(cppbor::Map().add(key2, contentsBytes)));
    cppbor::parse(map.encode());
}

extern "C" int LLVMFuzzerTestOneInput(const uint8_t* data, size_t size) {
    FuzzStringParsing(data, size);
    FuzzVectorParsing(data, size);
    return 0;
}
