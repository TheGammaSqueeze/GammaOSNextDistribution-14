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
#include <minikin/Hyphenator.h>

#include <iostream>
#include <string>

#include "HyphenatorMap.h"
#include "Locale.h"
#include "LocaleListCache.h"
#include "MinikinInternal.h"
#include "UnicodeUtils.h"
#include "minikin/LocaleList.h"
#include "minikin/U16StringPiece.h"

using namespace minikin;

const EndHyphenEdit EndHyphenEdits[] = {
        EndHyphenEdit::NO_EDIT,
        EndHyphenEdit::REPLACE_WITH_HYPHEN,
        EndHyphenEdit::INSERT_HYPHEN,
        EndHyphenEdit::INSERT_ARMENIAN_HYPHEN,
        EndHyphenEdit::INSERT_MAQAF,
        EndHyphenEdit::INSERT_UCAS_HYPHEN,
        EndHyphenEdit::INSERT_ZWJ_AND_HYPHEN,
};

const StartHyphenEdit StartHyphenEdits[] = {
        StartHyphenEdit::NO_EDIT,
        StartHyphenEdit::INSERT_HYPHEN,
        StartHyphenEdit::INSERT_ZWJ,
};

const HyphenationType HyphenationTypes[] = {
        HyphenationType::DONT_BREAK,
        HyphenationType::BREAK_AND_INSERT_HYPHEN,
        HyphenationType::BREAK_AND_INSERT_ARMENIAN_HYPHEN,
        HyphenationType::BREAK_AND_INSERT_MAQAF,
        HyphenationType::BREAK_AND_INSERT_UCAS_HYPHEN,
        HyphenationType::BREAK_AND_DONT_INSERT_HYPHEN,
        HyphenationType::BREAK_AND_REPLACE_WITH_HYPHEN,
        HyphenationType::BREAK_AND_INSERT_HYPHEN_AT_NEXT_LINE,
        HyphenationType::BREAK_AND_INSERT_HYPHEN_AND_ZWJ,
};

uint16_t specialChars[] = {
        0x000A, 0x000D, 0x0009, 0x002D, 0x00A0, 0x00AD,
        0x00B7, 0x058A, 0x05BE, 0x1400, 0x200D, 0x2010,
};

const uint16_t MAX_STR_LEN = 256;

// Function to generate StringPiece from a vector by pushing random valued elements using fdp
U16StringPiece generateStringPiece(FuzzedDataProvider* fdp) {
    uint16_t size = fdp->ConsumeIntegralInRange<uint16_t>(0, (fdp->remaining_bytes() / 3));

    std::vector<uint16_t> v;
    for (uint16_t i = 0; i < size; ++i) {
        // To randomize the insertion of special characters
        if (fdp->ConsumeBool()) {
            v.push_back(fdp->PickValueInArray(specialChars));
        } else {
            v.push_back(fdp->ConsumeIntegral<uint16_t>());
        }
    }

    return U16StringPiece(v);
}

extern "C" int LLVMFuzzerTestOneInput(const uint8_t* data, size_t size) {
    FuzzedDataProvider fdp(data, size);

    uint8_t minPrefix = fdp.ConsumeIntegral<size_t>();
    uint8_t minSuffix = fdp.ConsumeIntegral<size_t>();
    std::string locale = fdp.ConsumeRandomLengthString(MAX_STR_LEN);
    std::vector<uint8_t> patternData(fdp.ConsumeIntegralInRange<uint32_t>(0, 256));

    Hyphenator* hyphenator = Hyphenator::loadBinary(&patternData[0], minPrefix, minSuffix, locale);

    // To randomize the API calls
    while (fdp.remaining_bytes() > 0) {
        auto func = fdp.PickValueInArray<const std::function<void()>>({
                [&]() { addHyphenator(locale, hyphenator); },
                [&]() {
                    auto fromLocaleString = fdp.ConsumeRandomLengthString(MAX_STR_LEN);
                    auto toLocaleString = fdp.ConsumeRandomLengthString(MAX_STR_LEN);
                    addHyphenatorAlias(fromLocaleString, toLocaleString);
                },
                [&]() {
                    packHyphenEdit(fdp.PickValueInArray(StartHyphenEdits),
                                   fdp.PickValueInArray(EndHyphenEdits));
                },
                [&]() {
                    auto textBuf = generateStringPiece(&fdp);
                    std::vector<HyphenationType> result;
                    result.push_back(fdp.PickValueInArray(HyphenationTypes));
                    hyphenator->hyphenate(textBuf, &result);
                },
                // Get the list of locales and invoke the API for each one of them
                [&]() {
                    uint32_t id = registerLocaleList(fdp.ConsumeRandomLengthString(MAX_STR_LEN));
                    const LocaleList& locales = LocaleListCache::getById(id);
                    for (size_t i = 0; i < locales.size(); ++i) {
                        HyphenatorMap::lookup(locales[i]);
                    }
                },
                [&]() { getHyphenString(endHyphenEdit(fdp.ConsumeIntegral<uint8_t>())); },
                [&]() { getHyphenString(startHyphenEdit(fdp.ConsumeIntegral<uint8_t>())); },
                [&]() { isInsertion(endHyphenEdit(fdp.ConsumeIntegral<uint8_t>())); },
                [&]() { isInsertion(startHyphenEdit(fdp.ConsumeIntegral<uint8_t>())); },
                [&]() { editForThisLine(fdp.PickValueInArray(HyphenationTypes)); },
                [&]() { editForNextLine(fdp.PickValueInArray(HyphenationTypes)); },
                [&]() { isReplacement(endHyphenEdit(fdp.ConsumeIntegral<uint8_t>())); },
        });

        func();
    }

    return 0;
}
