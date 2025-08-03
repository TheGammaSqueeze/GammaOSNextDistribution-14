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

#include "minikin/SystemFonts.h"

#include <gtest/gtest.h>

#include "minikin/FontCollection.h"

#include "FontTestUtils.h"
#include "PathUtils.h"

namespace minikin {
namespace {

class TestableSystemFonts : public SystemFonts {
public:
    TestableSystemFonts() : SystemFonts() {}
    virtual ~TestableSystemFonts() {}

    std::shared_ptr<FontCollection> findFontCollection(const std::string& familyName) {
        return findFontCollectionInternal(familyName);
    }

    void addFontMap(std::shared_ptr<FontCollection>&& collections) {
        addFontMapInternal(std::move(collections));
    }

    void getFontSet(std::function<void(const std::vector<std::shared_ptr<Font>>&)> func) {
        getFontSetInternal(func);
    }

    void registerFallback(const std::string& familyName,
                          const std::shared_ptr<FontCollection>& fc) {
        registerFallbackInternal(familyName, fc);
    }

    void registerDefault(const std::shared_ptr<FontCollection>& fc) { registerDefaultInternal(fc); }
};

TEST(SystemFontsTest, registerAndLookup) {
    TestableSystemFonts systemFonts;
    auto fc = buildFontCollection("Ascii.ttf");
    systemFonts.registerFallback("sans", fc);
    EXPECT_EQ(fc, systemFonts.findFontCollection("sans"));
}

TEST(SystemFontsTest, registerDefaultAndLookup) {
    TestableSystemFonts systemFonts;
    auto fc = buildFontCollection("Ascii.ttf");
    systemFonts.registerDefault(fc);
    EXPECT_EQ(fc, systemFonts.findFontCollection("unknown-name"));
}

TEST(SystemFontsTest, registerDefaultAndFallback) {
    TestableSystemFonts systemFonts;
    auto fc1 = buildFontCollection("Ascii.ttf");
    auto fc2 = buildFontCollection("Bold.ttf");
    systemFonts.registerDefault(fc1);
    systemFonts.registerFallback("sans", fc2);
    EXPECT_EQ(fc1, systemFonts.findFontCollection("unknown-name"));
    EXPECT_EQ(fc2, systemFonts.findFontCollection("sans"));
}

TEST(SystemFontsTest, updateDefaultAndFallback) {
    TestableSystemFonts systemFonts;
    auto fc1 = buildFontCollection("Ascii.ttf");
    auto fc2 = buildFontCollection("Bold.ttf");
    systemFonts.registerDefault(fc1);
    systemFonts.registerFallback("sans", fc2);
    systemFonts.registerDefault(fc2);
    systemFonts.registerFallback("sans", fc1);
    EXPECT_EQ(fc2, systemFonts.findFontCollection("unknown-name"));
    EXPECT_EQ(fc1, systemFonts.findFontCollection("sans"));
}

TEST(SystemFontTest, getAvailableFont_dedupFonts) {
    TestableSystemFonts systemFonts;
    auto asciiFamily = buildFontFamily("Ascii.ttf");
    auto boldFamily = buildFontFamily("Bold.ttf");
    auto boldItalicFamily = buildFontFamily("BoldItalic.ttf");

    auto fc1Families = std::vector<std::shared_ptr<FontFamily>>{asciiFamily, boldItalicFamily};
    auto fc2Families = std::vector<std::shared_ptr<FontFamily>>{boldFamily, boldItalicFamily};
    auto fc1 = FontCollection::create(std::move(fc1Families));
    auto fc2 = FontCollection::create(std::move(fc2Families));

    systemFonts.addFontMap(std::move(fc1));
    systemFonts.addFontMap(std::move(fc2));

    systemFonts.getFontSet([](const std::vector<std::shared_ptr<Font>>& fonts) {
        EXPECT_EQ(3u, fonts.size());  // Ascii, Bold and BoldItalic
        std::unordered_set<std::string> fontPaths;
        for (const auto& font : fonts) {
            fontPaths.insert(getBasename(font->baseTypeface()->GetFontPath()));
        }

        EXPECT_TRUE(fontPaths.find("Ascii.ttf") != fontPaths.end());
        EXPECT_TRUE(fontPaths.find("Bold.ttf") != fontPaths.end());
        EXPECT_TRUE(fontPaths.find("BoldItalic.ttf") != fontPaths.end());
    });
}

}  // namespace
}  // namespace minikin
