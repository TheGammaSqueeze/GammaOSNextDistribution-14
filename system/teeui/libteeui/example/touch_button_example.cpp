/*
 * Copyright 2020, The Android Open Source Project
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

#include "touch_button_example.h"

#include <stddef.h>
#include <stdint.h>

#include "example_utils.h"
#include "layout/touch_button_layout.h"

namespace teeui {
namespace example {
namespace touch_button {

template <typename Layout> static void translateLabels(Layout* layout) {
    translateLabel<LabelOK>(layout);
    translateLabel<LabelCancel>(layout);
    translateLabel<LabelTitle>(layout);
    translateLabel<LabelHint>(layout);
}

class GUIStateTouch : public ITeeuiExample {
  private:
    bool okTapped_ = false;
    bool cancelTapped_ = false;
    EventResult eventResult_ = EventResult::NONE;

  public:
    bool inverted_;
    std::string confirmationMessage_;
    layout_t<ConfUILayout> layoutInstance_ = {};

    GUIStateTouch() : okTapped_(false), cancelTapped_(false), inverted_(false), layoutInstance_{} {}

    bool isOkTapped() const { return okTapped_; }
    bool isCancelTapped() const { return cancelTapped_; }

    Error tapOk(Event e) {
        if (e.event_ == EventType::KeyUp) {
            okTapped_ = true;
            eventResult_ = EventResult::CONFIRM;
        }
        return Error::OK;
    }

    Error tapCancel(Event e) {
        if (e.event_ == EventType::KeyUp) {
            cancelTapped_ = true;
            eventResult_ = EventResult::CANCEL;
        }
        return Error::OK;
    }

    void selectLanguage(const char* language_id) override {
        teeui::localization::selectLangId(language_id);
        translateLabels(&layoutInstance_);
    }

    void setConfirmationMessage(std::string confirmationMessage) override {
        confirmationMessage_ = std::move(confirmationMessage);
        std::get<LabelBody>(layoutInstance_)
            .setText({&*confirmationMessage_.begin(), &*confirmationMessage_.end()});
    }

    uint32_t setDeviceInfo(DeviceInfo device_info, bool magnified, bool inverted = false) override;

    EventResult onEvent(uint32_t x, uint32_t y, uint32_t) override;

    uint32_t renderUIIntoBuffer(uint32_t x, uint32_t y, uint32_t w, uint32_t h, uint32_t lineStride,
                                uint32_t* buffer,
                                size_t buffer_size_in_elements_not_bytes) override;
};

std::unique_ptr<ITeeuiExample> createTeeuiExample() {
    return std::make_unique<GUIStateTouch>();
}

EventResult GUIStateTouch::onEvent(uint32_t x, uint32_t y, uint32_t) {
    eventResult_ = EventResult::NONE;
    Event event{x, y, EventType::KeyUp};
    handleAllEvent(layoutInstance_, event);
    return eventResult_;
}

static context<ConfUIParameters> setLayoutParams(DeviceInfo& deviceInfo, bool magnified,
                                                 bool inverted) {
    context<ConfUIParameters> ctx(deviceInfo.mm2px_, deviceInfo.dp2px_);
    ctx.setParam<RightEdgeOfScreen>(pxs(deviceInfo.width_));
    ctx.setParam<BottomOfScreen>(pxs(deviceInfo.height_));
    if (magnified) {
        ctx.setParam<DefaultFontSize>(18_dp);
        ctx.setParam<BodyFontSize>(20_dp);
    } else {
        ctx.setParam<DefaultFontSize>(14_dp);
        ctx.setParam<BodyFontSize>(16_dp);
    }
    if (inverted) {
        ctx.setParam<ShieldColor>(kShieldColorInv);
        ctx.setParam<ColorText>(kTextColorInv);
        ctx.setParam<ColorBG>(kBackGroundColorInv);
        ctx.setParam<ColorButton>(kShieldColorInv);
    } else {
        ctx.setParam<ShieldColor>(kShieldColor);
        ctx.setParam<ColorText>(kTextColor);
        ctx.setParam<ColorBG>(kBackGroundColor);
        ctx.setParam<ColorButton>(kShieldColor);
    }
    return ctx;
}

uint32_t GUIStateTouch::setDeviceInfo(DeviceInfo device_info, bool magnified, bool inverted) {
    layoutInstance_ =
        instantiateLayout(ConfUILayout(), setLayoutParams(device_info, magnified, inverted));
    inverted_ = inverted;
    std::get<LabelOK>(layoutInstance_)
        .setCB(makeCallback<Error, Event>(
            [](Event e, void* p) -> Error { return reinterpret_cast<GUIStateTouch*>(p)->tapOk(e); },
            this));
    std::get<LabelCancel>(layoutInstance_)
        .setCB(makeCallback<Error, Event>(
            [](Event e, void* p) -> Error {
                return reinterpret_cast<GUIStateTouch*>(p)->tapCancel(e);
            },
            this));
    return 0;
}

uint32_t GUIStateTouch::renderUIIntoBuffer(uint32_t x, uint32_t y, uint32_t w, uint32_t h,
                                           uint32_t lineStride, uint32_t* buffer,
                                           size_t buffer_size_in_elements_not_bytes) {
    uint32_t afterLastPixelIndex = 0;
    if (__builtin_add_overflow(y, h, &afterLastPixelIndex) ||
        __builtin_add_overflow(afterLastPixelIndex, -1, &afterLastPixelIndex) ||
        __builtin_mul_overflow(afterLastPixelIndex, lineStride, &afterLastPixelIndex) ||
        __builtin_add_overflow(afterLastPixelIndex, x, &afterLastPixelIndex) ||
        __builtin_add_overflow(afterLastPixelIndex, w, &afterLastPixelIndex) ||
        afterLastPixelIndex > buffer_size_in_elements_not_bytes) {
        return uint32_t(Error::OutOfBoundsDrawing);
    }

    uint32_t* begin = buffer + (y * lineStride + x);

    Color bgColor = inverted_ ? kBackGroundColorInv : kBackGroundColor;

    for (uint32_t yi = 0; yi < h; ++yi) {
        for (uint32_t xi = 0; xi < w; ++xi) {
            begin[xi] = bgColor;
        }
        begin += lineStride;
    }
    FrameBuffer fb;
    fb.left_ = x;
    fb.top_ = y;
    fb.width_ = w;
    fb.height_ = h;
    fb.buffer_ = buffer;
    fb.size_in_elements_ = buffer_size_in_elements_not_bytes;
    fb.lineStride_ = lineStride;

    auto pixelDrawer = makePixelDrawer(
        [&fb](uint32_t x, uint32_t y, Color color) -> Error { return fb.drawPixel(x, y, color); });

    if (auto error = drawElements(layoutInstance_, pixelDrawer)) {
        return uint32_t(error.code());
    }

    return 0;  // OK
}

}  // namespace touch_button
}  // namespace example
}  // namespace teeui
