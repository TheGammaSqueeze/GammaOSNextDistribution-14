/******************************************************************************
 *
 * Copyright (C) 2022 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 *****************************************************************************
 */
#include <fuzzer/FuzzedDataProvider.h>

#include "LocaleListCache.h"
#include "minikin/LocaleList.h"

using namespace minikin;

const SubtagBits subtangBits[] = {SubtagBits::EMPTY,  SubtagBits::LANGUAGE, SubtagBits::SCRIPT,
                                  SubtagBits::REGION, SubtagBits::VARIANT,  SubtagBits::EMOJI,
                                  SubtagBits::ALL};

extern "C" int LLVMFuzzerTestOneInput(const uint8_t* data, size_t size) {
    FuzzedDataProvider fdp(data, size);

    uint32_t id = registerLocaleList(fdp.ConsumeRandomLengthString());
    uint32_t localeListId = fdp.ConsumeIntegralInRange<uint32_t>(0, id);
    const LocaleList& locales = LocaleListCache::getById(localeListId);
    std::string langTag = getLocaleString(localeListId);

    for (size_t i = 0; i < locales.size(); i++) {
        locales[i].getPartialLocale(fdp.PickValueInArray(subtangBits));
        locales[i].supportsScript(fdp.ConsumeIntegral<uint32_t>());
        locales[i].calcScoreFor(locales);
    }

    BufferWriter fakeWriter(nullptr);
    LocaleListCache::writeTo(&fakeWriter, LocaleListCache::getId(langTag));
    std::vector<uint8_t> buffer(fakeWriter.size());
    BufferWriter writer(buffer.data());
    LocaleListCache::writeTo(&writer, LocaleListCache::getId(langTag));
    BufferReader reader(buffer.data());
    LocaleListCache::readFrom(&reader);

    return 0;
}
