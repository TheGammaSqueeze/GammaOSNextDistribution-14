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

#pragma once

#include <device_layout.h>
#include <layouts/layout.h>
#include <stdint.h>
#include <optional>

namespace devices {

/**
 * Get the count of displays on which the confirmational UI should be
 * simultaneously rendered.  Normally this is 1.
 */
int getDisplayCount();

/** Get the display context containing parameters needed to render a layout.
 * This will be called for each active display (normally just once) to ge the
 * context for rendering the layout.  The context contains parameters such as
 * screen dimensions, button placements and so on.
 *
 * The display_index will identify the active display(s) for devices that
 * have more than one screen.
 *
 * @param display_index  Index of the display for which the context is needed.
 * @param magnified      If true, provide parameters for a magnified display.
 * @return Either a context, or std::nullop.
 */
std::optional<teeui::context<teeui::ConUIParameters>> getDisplayContext(
        int display_index,
        bool magnified);

/**
 * Get the layout for some display.
 */
std::optional<std::unique_ptr<teeui::layouts::ILayout>> getDisplayLayout(
        int display_index,
        bool inverted,
        const teeui::context<teeui::ConUIParameters>& ctx);

}  // namespace devices
