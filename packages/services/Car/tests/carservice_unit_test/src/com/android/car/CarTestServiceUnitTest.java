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

package com.android.car;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import android.car.Car;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.ParcelFileDescriptor;
import android.os.ServiceSpecificException;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.List;

@RunWith(MockitoJUnitRunner.class)
public final class CarTestServiceUnitTest {

    @Mock
    private Context mContext;
    @Mock
    private ICarImpl mCarImpl;
    private CarTestService mService;
    private static final long WAIT_TIMEOUT_MS = 100;

    @Before
    public void setUp() {
        when(mContext.checkCallingOrSelfPermission(Car.PERMISSION_CAR_TEST_SERVICE)).thenReturn(
                PackageManager.PERMISSION_GRANTED);

        mService = new CarTestService(mContext, mCarImpl);
    }

    @Test
    public void testDumpVhal() throws Exception {
        List<String> options = mock(List.class);
        StringBuilder resultBuilder = new StringBuilder();
        String testString = "abcdefgh";
        for (int i = 0; i < 1000; i++) {
            resultBuilder.append(testString);
        }
        String testResult = resultBuilder.toString();
        int resultCount = 10;
        doAnswer(invocation -> {
            ParcelFileDescriptor fd = (ParcelFileDescriptor) invocation.getArgument(0);
            ParcelFileDescriptor.AutoCloseOutputStream outputStream =
                    new ParcelFileDescriptor.AutoCloseOutputStream(fd);
            for (int i = 0; i < resultCount; i++) {
                outputStream.write(testResult.getBytes());
            }
            outputStream.close();
            return null;
        }).when(mCarImpl).dumpVhal(any(), any());
        String result = mService.dumpVhal(options, WAIT_TIMEOUT_MS);

        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < resultCount; i++) {
            builder.append(testResult);
        }

        assertThat(result).isEqualTo(builder.toString());
    }

    @Test
    public void testDumpVhal_VhalNotClosingFd() throws Exception {
        // A holder for ParcelFileDescriptor to keep it alive.
        ParcelFileDescriptor[] fdHolder = new ParcelFileDescriptor[1];
        doAnswer(invocation -> {
            ParcelFileDescriptor fd = (ParcelFileDescriptor) invocation.getArgument(0);
            // Duplicate the fd and never close it. Make sure the copied fd is not closed until
            // the end of the test by storing it.
            fdHolder[0] = fd.dup();
            return null;
        }).when(mCarImpl).dumpVhal(any(), any());

        assertThrows(ServiceSpecificException.class, () -> {
            mService.dumpVhal(List.of(""), WAIT_TIMEOUT_MS);
        });
    }
}
