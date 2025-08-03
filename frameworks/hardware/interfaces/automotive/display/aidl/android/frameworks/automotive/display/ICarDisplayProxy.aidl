/*
 * Copyright (C) 2022 The Android Open Source Project
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

package android.frameworks.automotive.display;

import android.frameworks.automotive.display.DisplayDesc;
import android.hardware.common.NativeHandle;
import android.view.Surface;

/**
 * ICarDisplayProxy is an interface implemented by the car display proxy daemon and allows
 * the vendor processes to render their contents on the display via SurfaceFlinger.
 *
 * A client can obtain a Surface associated with the target display via
 * ICarDisplayProxy.getSurface() and control the visibility of the target surface via
 * ICarDisplayProxy.showWindow() and ICarDisplayProxy.hideWindow().
 *
 * If a client uses ICarDisplayProxy.getHGraphicBufferProducer(), which is deprecated with
 * android.frameworks.automotive.display-V2, a client should call
 * ICarDisplayProxy.getHGraphicBufferProducer() and convert a returned NativeHandle into
 * HGraphicBufferProducer.  libbufferqueueconverter provides getSurfaceFromHGPB() to get
 * the surface from a converted HGraphicBufferProducer.
 */
@VintfStability
interface ICarDisplayProxy {
    /**
     * Returns the stable identifiers of all available displays.
     *
     * @return A list of stable display identifiers.
     */
    long[] getDisplayIdList();

    /**
     * Returns the descriptor of the target display.
     *
     * @param  in id A stable ID of a target display.
     * @return A display descriptor
     * @throws STATUS_BAD_VALUE if a given display id is invalid
     */
    DisplayDesc getDisplayInfo(in long id);

    /**
     * Gets an HGraphicBufferProducer instance from the service.
     *
     * @param  in id A stable ID of a target display.
     * @return HGraphicBufferProducer object in the form of NativeHandle.
     * @throws STATUS_FAILED_TRANSACTION if it fails to create the surface or read the display
     *         information
     *         STATUS_BAD_VALUE if it fails to create HGraphicBufferProducer
     * @deprecated As of android.frameworks.automotive.display-V2, this method is deprecated and
     *             replaced with getSurface().
     */
    NativeHandle getHGraphicBufferProducer(in long id);

    /**
     * Sets the ANativeWindow, which is associated with the IGraphicBufferProducer,
     * to be invisible and to release the control over display.
     *
     * @param  in id A stable ID of a target display.
     */
    void hideWindow(in long id);

    /**
     * Sets the ANativeWindow, which is associated with the IGraphicBufferProducer,
     * to be visible and to take over the display.
     *
     * @param  in id A stable ID of a target display.
     * @throws STATUS_BAD_VALUE if a given display id or a display token is invalid.
     *         STATUS_NAME_NOT_FOUND if it fails to find a display associated with the
     *         display token.
     *         Other STATUS_* if it fails to apply a SurfaceFlinger transaction.
     */
    void showWindow(in long id);

    /**
     * Gets a Surface associated with the target display.
     *
     * @param  in id A stable ID of a target display.
     * @return android.view.Surface object.
     * @throws STATUS_FAILED_TRANSACTION if it fails to create the surface or read the display
     *         information
     *         STATUS_BAD_VALUE if it fails to create a Surface object.
     */
    Surface getSurface(in long id);
}
