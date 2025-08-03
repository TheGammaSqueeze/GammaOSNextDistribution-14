/*
 * Copyright 2019, The Android Open Source Project
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

#define TLOG_TAG "confirmationui"

#include "trusty_confirmation_ui.h"
#include "trusty_operation.h"

#include "device_parameters.h"

#include <interface/secure_fb/secure_fb.h>
#include <inttypes.h>
#include <layouts/layout.h>
#include <stdio.h>
#include <teeui/error.h>
#include <teeui/localization/ConfirmationUITranslations.h>
#include <teeui/utils.h>
#include <trusty_log.h>

using teeui::ResponseCode;

template <typename Context>
static void updateColorScheme(Context* ctx, bool inverted) {
    using namespace teeui::layouts;
    using namespace teeui;

    if (inverted) {
        ctx->template setParam<ShieldColor>(kColorShieldInv);
        ctx->template setParam<ColorText>(kColorBackground);
        ctx->template setParam<ColorBG>(kColorBackgroundInv);
        ctx->template setParam<ColorButton>(kColorButtonInv);
        ctx->template setParam<ColorButtonBG>(kColorEnabled);
        ctx->template setParam<ColorTextHint>(kColorHintInv);
    } else {
        ctx->template setParam<ShieldColor>(kColorShield);
        ctx->template setParam<ColorText>(kColorEnabled);
        ctx->template setParam<ColorBG>(kColorBackground);
        ctx->template setParam<ColorButton>(kColorButton);
        ctx->template setParam<ColorButtonBG>(kColorBackground);
        ctx->template setParam<ColorTextHint>(kColorHint);
    }
    return;
}

static teeui::Color alfaCombineChannel(uint32_t shift,
                                       double alfa,
                                       teeui::Color a,
                                       teeui::Color b) {
    a >>= shift;
    a &= 0xff;
    b >>= shift;
    b &= 0xff;
    double acc = alfa * a + (1 - alfa) * b;
    if (acc <= 0)
        return 0;
    uint32_t result = acc;
    if (result > 255)
        return 255 << shift;
    return result << shift;
}

static ResponseCode teeuiError2ResponseCode(const teeui::Error& e) {
    switch (e.code()) {
    case teeui::Error::OK:
        return ResponseCode::OK;
    case teeui::Error::NotInitialized:
        return ResponseCode::UIError;
    case teeui::Error::FaceNotLoaded:
        return ResponseCode::UIErrorMissingGlyph;
    case teeui::Error::CharSizeNotSet:
        return ResponseCode::UIError;
    case teeui::Error::GlyphNotLoaded:
        return ResponseCode::UIErrorMissingGlyph;
    case teeui::Error::GlyphNotRendered:
        return ResponseCode::UIErrorMissingGlyph;
    case teeui::Error::GlyphNotExtracted:
        return ResponseCode::UIErrorMissingGlyph;
    case teeui::Error::UnsupportedPixelFormat:
        return ResponseCode::UIError;
    case teeui::Error::OutOfBoundsDrawing:
        return ResponseCode::UIErrorMessageTooLong;
    case teeui::Error::BBoxComputation:
        return ResponseCode::UIErrorMessageTooLong;
    case teeui::Error::OutOfMemory:
        return ResponseCode::UIErrorMessageTooLong;
    case teeui::Error::Localization:
        return ResponseCode::UIError;
    default:
        return ResponseCode::UIError;
    }
}

ResponseCode TrustyConfirmationUI::start(const char* prompt,
                                         const char* lang_id,
                                         bool inverted,
                                         bool magnified) {
    ResponseCode render_error = ResponseCode::OK;

    enabled_ = true;
    inverted_ = inverted;

    using namespace teeui;

    const int displayCount = devices::getDisplayCount();

    if (displayCount < 1) {
        TLOGE("Invalid displayCount:  %d\n", displayCount);
        return ResponseCode::UIError;
    }

    fb_info_.resize(displayCount);
    secure_fb_handle_.resize(displayCount);
    layout_.resize(displayCount);

    for (int i = 0; i < displayCount; ++i) {
        if (auto rc = secure_fb_open(&secure_fb_handle_[i], &fb_info_[i], i)) {
            TLOGE("secure_fb_open returned  %d\n", rc);
            stop();
            return ResponseCode::UIError;
        }

        if (fb_info_[i].pixel_format != TTUI_PF_RGBA8) {
            TLOGE("Unknown pixel format %u\n", fb_info_[i].pixel_format);
            stop();
            return ResponseCode::UIError;
        }

        std::optional<context<ConUIParameters>> ctx =
                devices::getDisplayContext(fb_info_[i].display_index, magnified);
        if (!ctx) {
            TLOGE("Failed to get device context: %d\n", i);
            stop();
            return ResponseCode::UIError;
        }

        /* Get rotated frame buffer dimensions */
        uint32_t rwidth, rheight;

        if (fb_info_[i].rotation == TTUI_DRAW_ROTATION_90 ||
            fb_info_[i].rotation == TTUI_DRAW_ROTATION_270) {
            rwidth = fb_info_[i].height;
            rheight = fb_info_[i].width;
        } else {
            rwidth = fb_info_[i].width;
            rheight = fb_info_[i].height;
        }

        /* Check the layout context and framebuffer agree on dimensions */
        if (*ctx->getParam<RightEdgeOfScreen>() != pxs(rwidth) ||
            *ctx->getParam<BottomOfScreen>() != pxs(rheight)) {
            TLOGE("Framebuffer dimensions do not match panel configuration\n");
            stop();
            return ResponseCode::UIError;
        }

        /* Set the colours */
        updateColorScheme(&ctx.value(), inverted_);

        /* Get the layout for the display */
        std::optional<std::unique_ptr<teeui::layouts::ILayout>> layout =
                devices::getDisplayLayout(fb_info_[i].display_index, inverted,
                                          *ctx);
        if (!layout) {
            TLOGE("Failed to get device layout: %d\n", i);
            stop();
            return ResponseCode::UIError;
        }

        layout_[i] = std::move(*layout);

        /* Configure the layout */
        layout_[i]->setLanguage(lang_id);
        layout_[i]->setConfirmationMessage(prompt);
        layout_[i]->showInstructions(true /* enable */);

        render_error = renderAndSwap(i);
        if (render_error != ResponseCode::OK) {
            stop();
            return render_error;
        }
    }
    return ResponseCode::OK;
}

ResponseCode TrustyConfirmationUI::renderAndSwap(uint32_t idx) {
    /* All display will be rendering the same content */
    auto drawPixel = teeui::makePixelDrawer([&, this](uint32_t x, uint32_t y,
                                                      teeui::Color color)
                                                    -> teeui::Error {
        uint32_t temp;

        TLOGD("px %u %u: %08x", x, y, color);

        /* Transform co-ordinates for rotation */
        switch (fb_info_[idx].rotation) {
        case TTUI_DRAW_ROTATION_0:
            break;

        case TTUI_DRAW_ROTATION_90:
            temp = y;
            y = x;
            x = (fb_info_[idx].width - temp) - 1;
            break;

        case TTUI_DRAW_ROTATION_180:
            x = (fb_info_[idx].width - x) - 1;
            y = (fb_info_[idx].height - y) - 1;
            break;

        case TTUI_DRAW_ROTATION_270:
            temp = x;
            x = y;
            y = (fb_info_[idx].height - temp) - 1;
            break;

        default:
            return teeui::Error::UnsupportedPixelFormat;
        }

        size_t pos =
                y * fb_info_[idx].line_stride + x * fb_info_[idx].pixel_stride;
        TLOGD("pos: %zu, bufferSize: %" PRIu32 "\n", pos, fb_info_[idx].size);
        if (pos >= fb_info_[idx].size) {
            return teeui::Error::OutOfBoundsDrawing;
        }
        double alfa = (color & 0xff000000) >> 24;
        alfa /= 255.0;
        auto& pixel =
                *reinterpret_cast<teeui::Color*>(fb_info_[idx].buffer + pos);

        pixel = alfaCombineChannel(0, alfa, color, pixel) |
                alfaCombineChannel(8, alfa, color, pixel) |
                alfaCombineChannel(16, alfa, color, pixel);
        return teeui::Error::OK;
    });

    TLOGI("begin rendering\n");

    teeui::Color bgColor = inverted_ ? teeui::layouts::kColorBackgroundInv
                                     : teeui::layouts::kColorBackground;

    uint8_t* line_iter = fb_info_[idx].buffer;
    for (uint32_t yi = 0; yi < fb_info_[idx].height; ++yi) {
        auto pixel_iter = line_iter;
        for (uint32_t xi = 0; xi < fb_info_[idx].width; ++xi) {
            *reinterpret_cast<uint32_t*>(pixel_iter) = bgColor;
            pixel_iter += fb_info_[idx].pixel_stride;
        }
        line_iter += fb_info_[idx].line_stride;
    }

    if (auto error = layout_[idx]->drawElements(drawPixel)) {
        TLOGE("Element drawing failed: %u\n", error.code());
        return teeuiError2ResponseCode(error);
    }

    if (auto rc = secure_fb_display_next(secure_fb_handle_[idx],
                                         &fb_info_[idx])) {
        TLOGE("secure_fb_display_next returned  %d\n", rc);
        return ResponseCode::UIError;
    }

    return ResponseCode::OK;
}

ResponseCode TrustyConfirmationUI::showInstructions(bool enable) {
    using namespace teeui;

    if (enabled_ == enable)
        return ResponseCode::OK;
    enabled_ = enable;

    ResponseCode rc = ResponseCode::OK;

    for (auto i = 0; i < (int)layout_.size(); ++i) {
        layout_[i]->showInstructions(enable);

        if (enable) {
            rc = renderAndSwap(i);
            if (rc != ResponseCode::OK) {
                stop();
                break;
            }
        }
    }

    return rc;
}

void TrustyConfirmationUI::stop() {
    TLOGI("calling gui stop\n");
    for (auto& secure_fb_handle: secure_fb_handle_) {
        secure_fb_close(secure_fb_handle);
        secure_fb_handle = NULL;
    }
    TLOGI("calling gui stop - done\n");
}
