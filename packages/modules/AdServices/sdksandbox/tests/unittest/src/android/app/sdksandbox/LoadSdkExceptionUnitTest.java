/*
 * Copyright (C) 2024 The Android Open Source Project
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

package android.app.sdksandbox;

import static com.google.common.truth.Truth.assertThat;

import android.os.Bundle;
import android.os.Parcel;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class LoadSdkExceptionUnitTest {
    @Test
    public void testLoadSdkExceptionWriteToParcel() {
        Bundle bundle = new Bundle();
        bundle.putChar("testKey", /*testValue=*/ 'C');
        Exception cause = new Exception(/*errorMessage=*/ "Error Message");

        LoadSdkException exception = new LoadSdkException(cause, bundle);

        Parcel parcel = Parcel.obtain();
        exception.writeToParcel(parcel, /*flags=*/ 0);

        // Create LoadSdkException with the same parcel
        parcel.setDataPosition(0); // rewind
        LoadSdkException exceptionCheck = LoadSdkException.CREATOR.createFromParcel(parcel);

        assertThat(exceptionCheck.getLoadSdkErrorCode()).isEqualTo(exception.getLoadSdkErrorCode());
        assertThat(exceptionCheck.getMessage()).isEqualTo(exception.getMessage());
        assertThat(exceptionCheck.getExtraInformation().getChar("testKey"))
                .isEqualTo(exception.getExtraInformation().getChar("testKey"));
        assertThat(exceptionCheck.getExtraInformation().keySet()).containsExactly("testKey");
    }

    @Test
    public void testLoadSdkExceptionDescribeContents() throws Exception {
        LoadSdkException exception = new LoadSdkException(new Exception(), new Bundle());
        assertThat(exception.describeContents()).isEqualTo(0);
    }
}
