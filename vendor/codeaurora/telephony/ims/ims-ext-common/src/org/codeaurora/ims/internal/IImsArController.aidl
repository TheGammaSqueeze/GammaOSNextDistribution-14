/*
 * Copyright (c) 2023 Qualcomm Innovation Center, Inc. All rights reserved.
 * SPDX-License-Identifier: BSD-3-Clause-Clear.
 */

package org.codeaurora.ims.internal;

import org.codeaurora.ims.internal.IImsArListener;

/**
 * Used by client application to communicate with vendor code
 * {@hide}
 */
oneway interface IImsArController {
    /**
     * Used by client to register call back listener with vendor for
     * camera parameters and recording surface updates.
     * @param listener call back listener
     */
    void setListener(in IImsArListener listener);

    /**
     * Used by client for start/stop AR call
     * cameraId is null to stop AR. cameraId is not null to
     * start AR with front/rear camera.
     * @param cameraId camera id
     */
    void enableArMode(String cameraId);

    /**
     * Used by client for set the delay of local rendering
     * @param delay - the delay is measured in millisecond
     */
    void setLocalRenderingDelay(int delay);
}
