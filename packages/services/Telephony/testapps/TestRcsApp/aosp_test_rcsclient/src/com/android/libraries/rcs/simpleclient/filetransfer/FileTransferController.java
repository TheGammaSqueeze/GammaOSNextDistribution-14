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

import com.google.common.util.concurrent.ListenableFuture;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;

/** File transfer functionality. */
public interface FileTransferController {

    /**
     * Downloads a file from the content server.
     *
     * @param fileUrl http URL to the file content on the server.
     * @return the response for the file download.
     */
    ListenableFuture<InputStream> downloadFile(String fileUrl);

    /**
     * Uploads a file to the content server.
     *
     * @param transactionId the transaction id of the file upload.
     * @param file          the file to be uploaded.
     * @return the XML response for the file upload, as defined in RCC.07.0-v19.0. This can then be
     * parsed by the FileInfoParse to get the URL to be used for the download.
     */
    ListenableFuture<String> uploadFile(
            String transactionId, File file)
            throws IOException;
}
