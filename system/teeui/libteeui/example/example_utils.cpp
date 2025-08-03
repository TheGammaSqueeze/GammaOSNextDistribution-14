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

#include <stddef.h>

#include <teeui/label.h>
#include <teeui/localization/ConfirmationUITranslations.h>

#include "example_utils.h"

namespace teeui {
namespace example {

uint32_t alfaCombineChannel(uint32_t shift, double alfa, uint32_t a, uint32_t b) {
    a >>= shift;
    a &= 0xff;
    b >>= shift;
    b &= 0xff;
    double acc = alfa * a + (1 - alfa) * b;
    if (acc <= 0) return 0;
    uint32_t result = acc;
    if (result > 255) return 255 << shift;
    return result << shift;
}

Error FrameBuffer::drawPixel(uint32_t x, uint32_t y, uint32_t color) const {
    size_t pos = (top_ + y) * lineStride_ + x + left_;
    if (pos >= size_in_elements_) {
        return Error::OutOfBoundsDrawing;
    }
    double alfa = (color & 0xff000000) >> 24;
    alfa /= 255.0;
    auto acc = buffer_[pos];
    buffer_[pos] = alfaCombineChannel(0, alfa, color, acc) |
                   alfaCombineChannel(8, alfa, color, acc) |
                   alfaCombineChannel(16, alfa, color, acc);
    return Error::OK;
}

void translate(LabelImpl* label) {
    uint64_t textId = label->textId();
    const char* translation =
        localization::lookup(static_cast<localization::TranslationId>(textId));
    label->setText({&translation[0], &translation[strlen(translation)]});
}

}  // namespace example
}  // namespace teeui