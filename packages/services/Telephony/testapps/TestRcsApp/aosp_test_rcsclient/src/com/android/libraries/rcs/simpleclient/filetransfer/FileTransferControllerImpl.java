/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.libraries.rcs.simpleclient.filetransfer;

import com.android.libraries.rcs.simpleclient.filetransfer.requestexecutor.HttpRequestExecutor;

import com.google.common.util.concurrent.ListenableFuture;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;

/** FileTransferController implementation. */
public class FileTransferControllerImpl implements FileTransferController {

    private final FileUploadController fileUploadController;

    public FileTransferControllerImpl(HttpRequestExecutor requestExecutor,
            String contentServerUri, String carrierName) {
        this.fileUploadController = new FileUploadController(requestExecutor, contentServerUri,
                carrierName);
    }

    @Override
    public ListenableFuture<InputStream> downloadFile(String fileUrl) {
        throw new UnsupportedOperationException("File download not supported");
    }

    @Override
    public ListenableFuture<String> uploadFile(
            String transactionId, File file)
            throws IOException {
        return fileUploadController.uploadFile(transactionId, file);
    }
}
