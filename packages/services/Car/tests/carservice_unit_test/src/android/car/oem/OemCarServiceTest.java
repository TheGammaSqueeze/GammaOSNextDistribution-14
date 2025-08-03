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

package android.car.oem;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assert.assertThrows;

import android.car.CarVersion;
import android.car.test.mocks.AbstractExtendedMockitoTestCase;
import android.content.pm.PackageManager;

import androidx.annotation.NonNull;

import com.android.internal.annotations.GuardedBy;

import org.junit.Test;
import org.mockito.Mock;

public final class OemCarServiceTest extends AbstractExtendedMockitoTestCase {

    private final CarVersion mCarVersionForTesting = CarVersion.VERSION_CODES.UPSIDE_DOWN_CAKE_0;

    private OemCarService mDefaultOemCarService = new DefaultOemCarService();
    private TestOemCarService mTestOemCarService = new TestOemCarService();
    private IOemCarService mOemCarService = IOemCarService.Stub
            .asInterface(mTestOemCarService.onBind(null));

    @Mock
    private IOemCarServiceCallback mIOemCarServiceCallback;

    @Mock
    private OemCarAudioFocusService mOemCarAudioFocusService;

    @Mock
    private OemCarAudioVolumeService mOemCarAudioVolumeService;

    @Mock
    private OemCarAudioDuckingService mOemCarAudioDuckingService;

    @Test
    public void testPermissionCheckForAll() throws Exception {
        // Without correct permission, SecurityException will be thrown.
        assertThrows(SecurityException.class,
                () -> mOemCarService.getSupportedCarVersion());
        assertThrows(SecurityException.class,
                () -> mOemCarService.getOemAudioFocusService());
        assertThrows(SecurityException.class,
                () -> mOemCarService.onCarServiceReady(mIOemCarServiceCallback));
    }

    @Test
    public void testGetSupportedCarVersion() throws Exception {
        mockCallerPemission();

        assertThat(mOemCarService.getSupportedCarVersion()).isEqualTo(mCarVersionForTesting);
    }

    @Test
    public void testGetOemAudioFocusService_notNull() throws Exception {
        TestOemCarService testOemCarService = new TestOemCarService();
        testOemCarService.mockCheckCallingPermission();
        testOemCarService.mockOemAudioFocusService();
        testOemCarService.onCreate();
        IOemCarService oemCarService = IOemCarService.Stub
                .asInterface(testOemCarService.onBind(null));

        assertThat(oemCarService.getOemAudioFocusService()).isNotNull();
    }

    @Test
    public void testGetOemAudioFocusService_null() throws Exception {
        mockCallerPemission();

        assertThat(mOemCarService.getOemAudioFocusService()).isNull();
    }

    @Test
    public void getOemAudioFocusService_onDefaultOemService_null() throws Exception {
        assertWithMessage("Audio focus service for default implementation")
                .that(mDefaultOemCarService.getOemAudioFocusService()).isNull();
    }

    @Test
    public void getOemAudioVolumeService_notNull() throws Exception {
        TestOemCarService testOemCarService = new TestOemCarService();
        testOemCarService.mockCheckCallingPermission();
        testOemCarService.mockOemAudioVolumeService();
        testOemCarService.onCreate();
        IOemCarService oemCarService = IOemCarService.Stub
                .asInterface(testOemCarService.onBind(null));

        assertWithMessage("Oem audio volume service")
                .that(oemCarService.getOemAudioVolumeService()).isNotNull();
    }

    @Test
    public void getOemAudioVolumeServiceo_onDefault_null() throws Exception {
        mockCallerPemission();

        assertWithMessage("Default oem audio volume service")
                .that(mOemCarService.getOemAudioVolumeService()).isNull();
    }

    @Test
    public void getOemAudioVolumeService_onDefaultOemService_null() throws Exception {
        assertWithMessage("Audio volume service for default implementation")
                .that(mDefaultOemCarService.getOemAudioVolumeService()).isNull();
    }

    @Test
    public void getOemAudioDuckingService_notNull() throws Exception {
        TestOemCarService testOemCarService = new TestOemCarService();
        testOemCarService.mockCheckCallingPermission();
        testOemCarService.mockOemAudioDuckingService();
        testOemCarService.onCreate();
        IOemCarService oemCarService = IOemCarService.Stub
                .asInterface(testOemCarService.onBind(null));

        assertWithMessage("Oem audio ducking service")
                .that(oemCarService.getOemAudioDuckingService()).isNotNull();
    }

    @Test
    public void getOemAudioDuckingService_onDefault_null() throws Exception {
        mockCallerPemission();

        assertWithMessage("Default oem audio ducking service")
                .that(mOemCarService.getOemAudioDuckingService()).isNull();
    }

    @Test
    public void getOemAudioDuckingService_onDefaultOemService_null() throws Exception {
        assertWithMessage("Audio ducking service for default implementation")
                .that(mDefaultOemCarService.getOemAudioDuckingService()).isNull();
    }

    private void mockCallerPemission() {
        mTestOemCarService.mockCheckCallingPermission();
    }

    private final class TestOemCarService extends OemCarService {

        private final Object mLock = new Object();

        @GuardedBy("mLock")
        private boolean mMockAudioFocusService;
        @GuardedBy("mLock")
        private boolean mMockAudioVolumeService;
        @GuardedBy("mLock")
        private boolean mMockAudioDuckingService;
        @GuardedBy("mLock")
        private boolean mAllowCallingPermission;

        @Override
        public CarVersion getSupportedCarVersion() {
            return mCarVersionForTesting;
        }

        @Override
        public OemCarAudioFocusService getOemAudioFocusService() {
            synchronized (mLock) {
                if (mMockAudioFocusService) {
                    return mOemCarAudioFocusService;
                }
            }
            return null;
        }

        @Override
        public OemCarAudioVolumeService getOemAudioVolumeService() {
            synchronized (mLock) {
                if (mMockAudioVolumeService) {
                    return mOemCarAudioVolumeService;
                }
            }
            return null;
        }

        @Override
        public OemCarAudioDuckingService getOemAudioDuckingService() {
            synchronized (mLock) {
                if (mMockAudioDuckingService) {
                    return mOemCarAudioDuckingService;
                }
            }
            return null;
        }

        @Override
        public int checkCallingPermission(String permission) {
            synchronized (mLock) {
                return mAllowCallingPermission ? PackageManager.PERMISSION_GRANTED
                        : PackageManager.PERMISSION_DENIED;
            }
        }

        void mockCheckCallingPermission() {
            synchronized (mLock) {
                mAllowCallingPermission = true;
            }
        }

        void mockOemAudioFocusService() {
            synchronized (mLock) {
                mMockAudioFocusService = true;
            }
        }

        void mockOemAudioVolumeService() {
            synchronized (mLock) {
                mMockAudioVolumeService = true;
            }
        }

        void mockOemAudioDuckingService() {
            synchronized (mLock) {
                mMockAudioDuckingService = true;
            }
        }

        @Override
        public void onCarServiceReady() {
        }
    }

    private static final class DefaultOemCarService extends OemCarService {

        @NonNull
        @Override
        public CarVersion getSupportedCarVersion() {
            return null;
        }

        @Override
        public void onCarServiceReady() {

        }
    }
}
