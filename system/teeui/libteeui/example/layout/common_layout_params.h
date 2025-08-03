#pragma once

#include <teeui/utils.h>

#include "fonts.h"

namespace teeui {
namespace example {

DECLARE_PARAMETER(RightEdgeOfScreen);
DECLARE_PARAMETER(BottomOfScreen);
DECLARE_PARAMETER(DefaultFontSize);  // 14_dp regular and 18_dp magnified
DECLARE_PARAMETER(BodyFontSize);     // 16_dp regular and 20_dp magnified
DECLARE_TYPED_PARAMETER(ShieldColor, ::teeui::Color);
DECLARE_TYPED_PARAMETER(ColorText, ::teeui::Color);
DECLARE_TYPED_PARAMETER(ColorBG, ::teeui::Color);

CONSTANT(BorderWidth, 24_dp);

DECLARE_FONT_BUFFER(RobotoMedium, RobotoMedium, RobotoMedium_length);
DECLARE_FONT_BUFFER(RobotoRegular, RobotoRegular, RobotoRegular_length);
DECLARE_FONT_BUFFER(Shield, Shield, Shield_length);

CONSTANT(DefaultFont, FONT(RobotoRegular));

}  // namespace example
}  // namespace teeui