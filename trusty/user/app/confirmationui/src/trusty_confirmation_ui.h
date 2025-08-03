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

#pragma once

#include <stdint.h>
#include <sys/types.h>

#include <memory>
#include <vector>

#include <layouts/layout.h>

#include <teeui/error.h>
#include <teeui/utils.h>

#include <lib/secure_fb/secure_fb.h>

#include <secure_input/secure_input_proto.h>

#include <device_layout.h>

class TrustyConfirmationUI {
public:
    TrustyConfirmationUI() = default;

    /**
     * Renders and displays the dialog.
     * prompt: The message that is to be confirmed.
     * lang_id: The locale to use. Selects the language used for the button
     * texts and instructions. inverted: If set, the inverted color scheme is
     * used. magnified: If set, the magnified font profile is used.
     *
     * Returns ResponseCode::OK if the UI was successfully start.
     * Returns ResponseCode::UIError if the secure display could not be enabled
     * or if the framebuffer topology is off. Returns
     * ResponseCode::UIErrorMissingGlyph if the font rendering failed. Returns
     * ResponseCode::UIErrorMessageTooLong if the any of the strings could not
     * be fully rendered on the screen.
     */
    teeui::ResponseCode start(const char* promt,
                              const char* lang_id,
                              bool inverted,
                              bool magnified);
    /**
     * Toggles the color profile of the buttons/button labels indicating to the
     * user that input enabled (enable == true) or disabled (enabled == false).
     *
     * Returns ResponseCode::OK if the UI was successfully updated.
     * Returns ResponseCode::UIError if the secure framebuffer could not be
     * flipped. Other errors same as start() above.
     */
    teeui::ResponseCode showInstructions(bool enable);

    /**
     * Stops the secure display and frees up all of the related resources.
     */
    void stop();

    // TrustyConfirmationUI not copyable
    TrustyConfirmationUI& operator=(const TrustyConfirmationUI&) = delete;

    TrustyConfirmationUI& operator=(TrustyConfirmationUI&& other) {
        fb_info_ = other.fb_info_;
        return *this;
    }

private:
    teeui::Error updateTranslations(uint32_t idx);
    teeui::ResponseCode renderAndSwap(uint32_t idx);

    std::vector<secure_fb_info> fb_info_;
    std::vector<secure_fb_handle_t> secure_fb_handle_;

    bool inverted_;
    bool enabled_;

    std::vector<std::unique_ptr<teeui::layouts::ILayout>> layout_;
};
