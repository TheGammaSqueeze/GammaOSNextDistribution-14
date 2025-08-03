/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.camera.one.v2.commands;

import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CaptureRequest;

import com.android.camera.one.v2.camera2proxy.CameraCaptureSessionClosedException;
import com.android.camera.one.v2.commands.CameraCommand;
import com.android.camera.one.v2.core.FrameServer;
import com.android.camera.one.v2.core.RequestBuilder;
import com.android.camera.one.v2.core.ResourceAcquisitionFailedException;

import java.util.Arrays;

import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;

/**
 * Update repeating request.
 */
@ParametersAreNonnullByDefault
public final class UpdateRequestCommand implements CameraCommand {
    private final FrameServer mFrameServer;
    private final RequestBuilder.Factory mBuilderFactory;
    private final int mTemplateType;

    /**
     * @param frameServer Used for sending requests to the camera.
     * @param builder Used for building requests.
     * @param templateType See
     *            {@link android.hardware.camera2.CameraDevice#createCaptureRequest}
     */
    public UpdateRequestCommand(FrameServer frameServer, RequestBuilder.Factory builder, int
            templateType) {
        mFrameServer = frameServer;
        mBuilderFactory = builder;
        mTemplateType = templateType;
    }

    /**
     * Update repeating request.
     */
    @Override
    public void run() throws InterruptedException, CameraAccessException,
            CameraCaptureSessionClosedException, ResourceAcquisitionFailedException {
        FrameServer.Session session = mFrameServer.tryCreateExclusiveSession();
        if (session == null) {
            // If there are already other commands interacting with the
            // FrameServer just abort.
            return;
        }

        try {
            RequestBuilder builder = mBuilderFactory.create(mTemplateType);
            session.submitRequest(Arrays.asList(builder.build()),
                    FrameServer.RequestType.REPEATING);
        } finally {
            session.close();
        }
    }
}
