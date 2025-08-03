/*
 * Copyright (C) 2021 The Android Open Source Project
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
#include <cppbor_parse.h>

using namespace cppbor;

class FuzzParseClient : public ParseClient {
  public:
    virtual ParseClient* item(std::unique_ptr<Item>&, const uint8_t*, const uint8_t*,
                              const uint8_t*) override {
        return this;
    }
    virtual ParseClient* itemEnd(std::unique_ptr<Item>&, const uint8_t*, const uint8_t*,
                                 const uint8_t*) override {
        return this;
    }
    virtual void error(const uint8_t*, const std::string&) override {}
};

void FuzzParse(const uint8_t* data, size_t size) {
    const uint8_t* cursor = data;
    const uint8_t* end = data + size;
    while (cursor < end) {
        auto [item, newPos, errMsg] = parse(cursor, end);
        if (!item || !errMsg.empty()) {
            return;
        }
        cursor = newPos;
    }
}

void FuzzParseWithViews(const uint8_t* data, size_t size) {
    const uint8_t* cursor = data;
    const uint8_t* end = data + size;
    while (cursor < end) {
        auto [item, newPos, errMsg] = parseWithViews(cursor, end);
        if (!item || !errMsg.empty()) {
            return;
        }
        cursor = newPos;
    }
}

void FuzzParseWithClient(const uint8_t* data, size_t size) {
    const uint8_t* end = data + size;
    FuzzParseClient parseClient;
    parse(data, end, &parseClient);
}

void FuzzParseWithClientAndViews(const uint8_t* data, size_t size) {
    const uint8_t* end = data + size;
    FuzzParseClient parseClient;
    parseWithViews(data, end, &parseClient);
}

extern "C" int LLVMFuzzerTestOneInput(const uint8_t* data, size_t size) {
    FuzzParse(data, size);
    FuzzParseWithViews(data, size);
    FuzzParseWithClient(data, size);
    FuzzParseWithClientAndViews(data, size);
    return 0;
}
