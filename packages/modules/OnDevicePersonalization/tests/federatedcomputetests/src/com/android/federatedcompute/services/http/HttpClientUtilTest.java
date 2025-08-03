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

package com.android.federatedcompute.services.http;


import static com.google.common.truth.Truth.assertThat;

import android.content.Context;
import android.net.Uri;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;

public class HttpClientUtilTest {
    @Test
    public void compress_uncompress_success() throws Exception {
        String testUriPrefix =
                "android.resource://com.android.ondevicepersonalization.federatedcomputetests/raw/";
        Uri checkpointUri = Uri.parse(testUriPrefix + "federation_test_checkpoint_client");
        Context context = ApplicationProvider.getApplicationContext();
        InputStream in = context.getContentResolver().openInputStream(checkpointUri);
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int bytesRead;
        while ((bytesRead = in.read(buf)) != -1) {
            outputStream.write(buf, 0, bytesRead);
        }
        byte[] dataBeforeCompress = outputStream.toByteArray();

        byte[] dataAfterCompress = HttpClientUtil.compressWithGzip(dataBeforeCompress);
        assertThat(dataAfterCompress.length).isLessThan(dataBeforeCompress.length);

        byte[] unzipData = HttpClientUtil.uncompressWithGzip(dataAfterCompress);
        assertThat(unzipData).isEqualTo(dataBeforeCompress);
    }
}
