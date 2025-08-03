/*
 * Copyright (C) 2023 The Android Open Source Project
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

#ifndef MINIKIN_SCRIPT_UTILS_H
#define MINIKIN_SCRIPT_UTILS_H

#define LOG_TAG "Minikin"

#include <unicode/ubidi.h>

#include <memory>

#include "minikin/Layout.h"
#include "minikin/Macros.h"
#include "minikin/U16StringPiece.h"

namespace minikin {

// A helper class for iterating the bidi run transitions.
class ScriptText {
public:
    struct RunInfo {
        Range range;
        hb_script_t script;
    };

    ScriptText(const U16StringPiece& textBuf, uint32_t start, uint32_t end)
            : mTextBuf(textBuf), mRange(start, end) {}

    explicit ScriptText(const U16StringPiece& textBuf)
            : mTextBuf(textBuf), mRange(0, textBuf.size()) {}

    class iterator {
    public:
        inline bool operator==(const iterator& o) const {
            return mStart == o.mStart && mParent == o.mParent;
        }

        inline bool operator!=(const iterator& o) const { return !(*this == o); }

        inline std::pair<Range, hb_script_t> operator*() const {
            return std::make_pair(Range(mStart, mEnd), mScript);
        }

        inline iterator& operator++() {
            mStart = mEnd;
            std::tie(mEnd, mScript) = getScriptRun(mParent->mTextBuf, mParent->mRange, mStart);
            return *this;
        }

    private:
        friend class ScriptText;

        iterator(const ScriptText* parent, uint32_t start) : mParent(parent), mStart(start) {
            std::tie(mEnd, mScript) = getScriptRun(mParent->mTextBuf, mParent->mRange, mStart);
        }

        const ScriptText* mParent;
        uint32_t mStart;
        uint32_t mEnd;
        hb_script_t mScript;
    };

    inline iterator begin() const { return iterator(this, mRange.getStart()); }
    inline iterator end() const { return iterator(this, mRange.getEnd()); }

private:
    U16StringPiece mTextBuf;
    Range mRange;

    static std::pair<uint32_t, hb_script_t> getScriptRun(U16StringPiece text, Range range,
                                                         uint32_t pos);

    MINIKIN_PREVENT_COPY_AND_ASSIGN(ScriptText);
};

}  // namespace minikin

#endif  // MINIKIN_SCRIPT_UTILS_H
