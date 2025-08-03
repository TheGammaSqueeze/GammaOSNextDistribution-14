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

package android.mediapc.cts;

import static org.junit.Assert.*;

import android.media.MediaDrm;

import java.io.IOException;

/*
 * The ProvisionRequester is used to provision an unprovisioned device.
 * This is likely a single use class, as devices should not need to be
 * continually re-provisioned during playback.
 */
public final class ProvisionRequester {

    private final MediaDrm mDrm;
    private Exception mException = null;

    public ProvisionRequester(MediaDrm drm) {
        mDrm = drm;
    }

    private final Thread provisionThread = new Thread() {
        @Override
        public void run() {
            try {
                final MediaDrm.ProvisionRequest request = mDrm.getProvisionRequest();

                final byte[] data = request.getData();

                final String signedUrl = String.format(
                        "%s&signedRequest=%s",
                        request.getDefaultUrl(),
                        new String(data));

                final Post post = new Post(signedUrl, null);

                post.setProperty("Accept", "*/*");
                post.setProperty("User-Agent", "Widevine CDM v1.0");
                post.setProperty("Content-Type", "application/json");
                post.setProperty("Connection", "close");

                final Post.Response response = post.send();

                if (response.code != 200) {
                    throw new IOException("Server returned HTTP error code " + response.code);
                }

                if (response.body == null) {
                    throw new IOException("No response from provisioning server");
                }

                if (response.body.length == 0) {
                    throw new IOException("Empty response from provisioning server");
                }

                mDrm.provideProvisionResponse(response.body);
            } catch(Exception e) {
                mException = e;
            }
        }
    };

    public void send() {
        try {
            provisionThread.start();
            provisionThread.join();
            assertNull("Got an Exception in provisioning: " + mException, mException);
        } catch (InterruptedException ex) {
            fail("Failed to provision");
        }
    }
}
