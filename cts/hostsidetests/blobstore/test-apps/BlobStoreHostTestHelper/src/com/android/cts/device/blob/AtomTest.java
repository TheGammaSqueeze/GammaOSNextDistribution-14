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

package com.android.cts.device.blob;

import static com.google.common.truth.Truth.assertWithMessage;

import android.app.blob.BlobStoreManager;

import com.android.utils.blob.FakeBlobData;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.google.common.io.BaseEncoding;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@RunWith(AndroidJUnit4.class)
public class AtomTest extends BaseBlobStoreDeviceTest {
    // Constants for testBlobStore
    private static final long BLOB_COMMIT_CALLBACK_TIMEOUT_SEC = 5;
    private static final long BLOB_EXPIRY_DURATION_MS = 24 * 60 * 60 * 1000;
    private static final long BLOB_FILE_SIZE_BYTES = 23 * 1024L;
    private static final long BLOB_LEASE_EXPIRY_DURATION_MS = 60 * 60 * 1000;
    private static final byte[] FAKE_PKG_CERT_SHA256 = BaseEncoding.base16().decode(
            "187E3D3172F2177D6FEC2EA53785BF1E25DFF7B2E5F6E59807E365A7A837E6C3");

    @Test
    public void testBlobStoreOps() throws Exception {
        final long leaseExpiryMs = System.currentTimeMillis() + BLOB_LEASE_EXPIRY_DURATION_MS;

        final FakeBlobData blobData = new FakeBlobData.Builder(mContext)
                .setExpiryDurationMs(BLOB_EXPIRY_DURATION_MS)
                .setFileSize(BLOB_FILE_SIZE_BYTES)
                .build();

        blobData.prepare();
        try {
            // Commit the Blob, should result in BLOB_COMMITTED atom event
            commitBlob(blobData);

            // Lease the Blob, should result in BLOB_LEASED atom event
            mBlobStoreManager.acquireLease(blobData.getBlobHandle(), "", leaseExpiryMs);

            // Open the Blob, should result in BLOB_OPENED atom event
            mBlobStoreManager.openBlob(blobData.getBlobHandle());
        } finally {
            blobData.delete();
        }
    }

    private void commitBlob(FakeBlobData blobData) throws Exception {
        final long sessionId = createSession(blobData.getBlobHandle());
        try (BlobStoreManager.Session session = mBlobStoreManager.openSession(sessionId)) {
            blobData.writeToSession(session);
            session.allowPackageAccess("fake.package.name", FAKE_PKG_CERT_SHA256);

            final CompletableFuture<Integer> callback = new CompletableFuture<>();
            session.commit(mContext.getMainExecutor(), callback::complete);
            assertWithMessage("Session failed to commit within timeout").that(
                    callback.get(BLOB_COMMIT_CALLBACK_TIMEOUT_SEC, TimeUnit.SECONDS)).isEqualTo(0);
        }
    }
}
