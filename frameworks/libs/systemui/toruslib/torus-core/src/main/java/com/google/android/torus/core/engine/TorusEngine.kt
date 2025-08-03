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

package com.google.android.torus.core.engine

import com.google.android.torus.core.wallpaper.LiveWallpaper

/**
 * Interface that defines a Live Wallpaper Engine and its different states. You need to implement
 * this class to render using [LiveWallpaper].
 */
interface TorusEngine {
    /**
     * Called when the engine is created. You should load the assets and initialize the
     * resources here.
     *
     * IMPORTANT: When this function is called, the surface used to render the engine has to be
     * ready.
     *
     * @param isFirstActiveInstance Whether this is the first Engine instance (since the last time
     * that all instances were destroyed).
     */
    fun create(isFirstActiveInstance: Boolean = true)

    /**
     * Called when the [TorusEngine] resumes.
     */
    fun resume()

    /**
     * Called when the [TorusEngine] is paused.
     */
    fun pause()

    /**
     * Called when the surface holding the [TorusEngine] has changed its size.
     *
     * @param width The new width of the surface holding the [TorusEngine].
     * @param height The new height of the surface holding the [TorusEngine].
     */
    fun resize(width: Int, height: Int)

    /**
     * Called when we need to destroy the surface.
     *
     * @param isLastActiveInstance Whether this was the last Engine instance in our Service.
     */
    fun destroy(isLastActiveInstance: Boolean = true)
}
