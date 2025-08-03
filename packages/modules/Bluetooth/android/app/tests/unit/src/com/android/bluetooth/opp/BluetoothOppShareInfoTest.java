/*
 * Copyright 2022 The Android Open Source Project
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

package com.android.bluetooth.opp;

import static com.google.common.truth.Truth.assertThat;

import android.net.Uri;

import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class BluetoothOppShareInfoTest {
    private BluetoothOppShareInfo mBluetoothOppShareInfo;

    private Uri uri = Uri.parse("file://Idontknow//Justmadeitup");
    private String hintString = "this is a object that take 4 bytes";
    private String filename = "random.jpg";
    private String mimetype = "image/jpeg";
    private int direction = BluetoothShare.DIRECTION_INBOUND;
    private String destination = "01:23:45:67:89:AB";
    private int visibility = BluetoothShare.VISIBILITY_VISIBLE;
    private int confirm = BluetoothShare.USER_CONFIRMATION_CONFIRMED;
    private int status = BluetoothShare.STATUS_PENDING;
    private int totalBytes = 1023;
    private int currentBytes = 42;
    private int timestamp = 123456789;
    private boolean mediaScanned = false;

    @Before
    public void setUp() throws Exception {
        mBluetoothOppShareInfo = new BluetoothOppShareInfo(0, uri, hintString, filename,
                mimetype, direction, destination, visibility, confirm, status, totalBytes,
                currentBytes, timestamp, mediaScanned);
    }

    @Test
    public void testConstructor() {
        assertThat(mBluetoothOppShareInfo.mUri).isEqualTo(uri);
        assertThat(mBluetoothOppShareInfo.mFilename).isEqualTo(filename);
        assertThat(mBluetoothOppShareInfo.mMimetype).isEqualTo(mimetype);
        assertThat(mBluetoothOppShareInfo.mDirection).isEqualTo(direction);
        assertThat(mBluetoothOppShareInfo.mDestination).isEqualTo(destination);
        assertThat(mBluetoothOppShareInfo.mVisibility).isEqualTo(visibility);
        assertThat(mBluetoothOppShareInfo.mConfirm).isEqualTo(confirm);
        assertThat(mBluetoothOppShareInfo.mStatus).isEqualTo(status);
        assertThat(mBluetoothOppShareInfo.mTotalBytes).isEqualTo(totalBytes);
        assertThat(mBluetoothOppShareInfo.mCurrentBytes).isEqualTo(currentBytes);
        assertThat(mBluetoothOppShareInfo.mTimestamp).isEqualTo(timestamp);
        assertThat(mBluetoothOppShareInfo.mMediaScanned).isEqualTo(mediaScanned);
    }

    @Test
    public void testReadyToStart() {
        assertThat(mBluetoothOppShareInfo.isReadyToStart()).isTrue();

        mBluetoothOppShareInfo.mDirection = BluetoothShare.DIRECTION_OUTBOUND;
        assertThat(mBluetoothOppShareInfo.isReadyToStart()).isTrue();

        mBluetoothOppShareInfo.mStatus = BluetoothShare.STATUS_RUNNING;
        assertThat(mBluetoothOppShareInfo.isReadyToStart()).isFalse();
    }

    @Test
    public void testHasCompletionNotification() {
        assertThat(mBluetoothOppShareInfo.hasCompletionNotification()).isFalse();

        mBluetoothOppShareInfo.mStatus = BluetoothShare.STATUS_CANCELED;
        assertThat(mBluetoothOppShareInfo.hasCompletionNotification()).isTrue();

        mBluetoothOppShareInfo.mVisibility = BluetoothShare.VISIBILITY_HIDDEN;
        assertThat(mBluetoothOppShareInfo.hasCompletionNotification()).isFalse();
    }

    @Test
    public void testIsObsolete() {
        assertThat(mBluetoothOppShareInfo.isObsolete()).isFalse();
        mBluetoothOppShareInfo.mStatus = BluetoothShare.STATUS_RUNNING;
        assertThat(mBluetoothOppShareInfo.isObsolete()).isTrue();
    }
}
