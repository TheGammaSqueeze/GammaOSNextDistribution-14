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
package com.android.wallpaper.picker.customization.animation.shader

import android.graphics.RuntimeShader
import com.android.systemui.surfaceeffects.shaderutil.SdfShaderLibrary

/**
 * Reveals whatever is behind the given image in a circular fashion. Imagine a hole in the given
 * image that grows until it's invisible.
 */
// TODO (b/281878827): remove this and use loading animation in SystemUIShaderLib when available
class CircularRevealShader : RuntimeShader(REVEAL_SHADER) {
    // language=AGSL
    companion object {

        private const val UNIFORMS =
            """
            uniform shader in_src;
            uniform half in_radius;
            uniform vec2 in_maskCenter;
            uniform half in_blur;
        """

        private const val MAIN_SHADER =
            """vec4 main(vec2 p) {
            half4 src = in_src.eval(p);
            half mask = soften(sdCircle(p - in_maskCenter, in_radius), in_blur);

            return src * mask;
        }
        """

        private const val REVEAL_SHADER =
            UNIFORMS +
                SdfShaderLibrary.SHADER_SDF_OPERATION_LIB +
                SdfShaderLibrary.CIRCLE_SDF +
                MAIN_SHADER
    }

    fun setCenter(x: Float, y: Float) {
        setFloatUniform("in_maskCenter", x, y)
    }

    fun setRadius(radius: Float) {
        setFloatUniform("in_radius", radius)
    }

    fun setBlur(blur: Float) {
        setFloatUniform("in_blur", blur)
    }
}
