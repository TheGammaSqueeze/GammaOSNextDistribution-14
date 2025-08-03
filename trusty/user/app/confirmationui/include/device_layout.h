/*
 * Copyright 2023, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#pragma once

#include <stdint.h>
#include <teeui/error.h>
#include <teeui/utils.h>
#include <string>

namespace teeui {
namespace layouts {

static constexpr teeui::Color kColorEnabled = 0xff242120;
static constexpr teeui::Color kColorDisabled = 0xffbdbdbd;
static constexpr teeui::Color kColorEnabledInv = 0xffdedede;
static constexpr teeui::Color kColorDisabledInv = 0xff424242;
static constexpr teeui::Color kColorShieldInv = 0xfff69d66;
static constexpr teeui::Color kColorShield = 0xffe8731a;
static constexpr teeui::Color kColorHintInv = 0xffa6a09a;
static constexpr teeui::Color kColorHint = 0xff68635f;
static constexpr teeui::Color kColorButton = 0xffe8731a;
static constexpr teeui::Color kColorButtonInv = 0xfff69d66;
static constexpr teeui::Color kColorBackground = 0xffffffff;
static constexpr teeui::Color kColorBackgroundInv = 0xff000000;

class ILayout {
public:
    explicit ILayout(bool inverted) : inverted_(inverted) {}

    virtual void setLanguage(const char*) = 0;
    virtual void setConfirmationMessage(const char*) = 0;
    virtual void showInstructions(bool) = 0;
    virtual teeui::Error drawElements(const teeui::PixelDrawer& drawPixel) = 0;
    virtual ~ILayout() = default;

protected:
    bool inverted_;
};

}  // namespace layouts
}  // namespace teeui
