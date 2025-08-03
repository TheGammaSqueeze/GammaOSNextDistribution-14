/*
 * Copyright (C) 2018 The Android Open Source Project
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

#ifndef MINIKIN_TEST_FREE_TYPE_MINIKIN_FONT_FOR_TEST_H
#define MINIKIN_TEST_FREE_TYPE_MINIKIN_FONT_FOR_TEST_H

#include <ft2build.h>

#include <string>

#include "minikin/Buffer.h"
#include "minikin/Font.h"
#include "minikin/MinikinFont.h"
#include "minikin/MinikinFontFactory.h"
#include FT_FREETYPE_H

#include "minikin/Macros.h"

namespace minikin {

class FreeTypeMinikinFontForTest : public MinikinFont {
public:
    FreeTypeMinikinFontForTest(const std::string& font_path, int index,
                               const std::vector<FontVariation>& axes);
    FreeTypeMinikinFontForTest(const std::string& font_path, int index)
            : FreeTypeMinikinFontForTest(font_path, index, std::vector<FontVariation>()) {}
    FreeTypeMinikinFontForTest(const std::string& font_path)
            : FreeTypeMinikinFontForTest(font_path, 0, std::vector<FontVariation>()) {}
    virtual ~FreeTypeMinikinFontForTest();

    // MinikinFont overrides.
    float GetHorizontalAdvance(uint32_t glyph_id, const MinikinPaint& paint,
                               const FontFakery& fakery) const override;
    void GetBounds(MinikinRect* bounds, uint32_t glyph_id, const MinikinPaint& paint,
                   const FontFakery& fakery) const override;
    void GetFontExtent(MinikinExtent* extent, const MinikinPaint& paint,
                       const FontFakery& fakery) const override;

    const std::string& GetFontPath() const override { return mFontPath; }
    const void* GetFontData() const { return mFontData; }
    size_t GetFontSize() const { return mFontSize; }
    int GetFontIndex() const { return mFontIndex; }
    const std::vector<minikin::FontVariation>& GetAxes() const { return mAxes; }
    std::shared_ptr<MinikinFont> createFontWithVariation(const std::vector<FontVariation>&) const;

private:
    const std::string mFontPath;
    const int mFontIndex;
    void* mFontData;
    size_t mFontSize;
    std::vector<minikin::FontVariation> mAxes;

    FT_Library mFtLibrary;
    FT_Face mFtFace;

    MINIKIN_PREVENT_COPY_AND_ASSIGN(FreeTypeMinikinFontForTest);
};

class FreeTypeMinikinFontForTestFactory : MinikinFontFactory {
private:
    FreeTypeMinikinFontForTestFactory();

public:
    static void init();

    void write(BufferWriter* writer, const MinikinFont* typeface) const override;

    std::shared_ptr<MinikinFont> create(BufferReader reader) const override;

    void skip(BufferReader* reader) const override;
};

}  // namespace minikin

#endif  // MINIKIN_TEST_FREE_TYPE_MINIKIN_FONT_FOR_TEST_H
