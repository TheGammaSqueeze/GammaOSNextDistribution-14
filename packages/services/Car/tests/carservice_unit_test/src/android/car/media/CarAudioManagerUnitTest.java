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

package android.car.media;

import static android.car.media.CarAudioManager.AUDIO_FEATURE_DYNAMIC_ROUTING;
import static android.car.media.CarAudioManager.CONFIG_STATUS_CHANGED;
import static android.car.media.CarAudioManager.PRIMARY_AUDIO_ZONE;
import static android.media.AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE;
import static android.media.AudioAttributes.USAGE_GAME;
import static android.media.AudioAttributes.USAGE_MEDIA;
import static android.media.AudioDeviceInfo.TYPE_BUILTIN_MIC;
import static android.media.AudioDeviceInfo.TYPE_FM_TUNER;

import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.car.Car;
import android.car.CarOccupantZoneManager.OccupantZoneInfo;
import android.car.test.AbstractExpectableTestCase;
import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioDeviceAttributes;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.RemoteException;

import com.android.car.CarServiceUtils;
import com.android.car.audio.AudioDeviceInfoBuilder;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

@RunWith(MockitoJUnitRunner.class)
public final class CarAudioManagerUnitTest extends AbstractExpectableTestCase {

    private static final String Car_AUDIO_MANAGER_TEST_THREAD_NAME = "CarAudioManagerUnitTest";
    private static final String MICROPHONE_ADDRESS = "Built-In Mic";
    private static final String FM_TUNER_ADDRESS = "FM Tuner";
    private static final String MEDIA_TEST_DEVICE = "media_bus_device";
    private static final String NAVIGATION_TEST_DEVICE = "navigation_bus_device";
    private static final int TEST_REAR_LEFT_ZONE_ID = 1;
    private static final int TEST_REAR_RIGHT_ZONE_ID = 2;
    private static final int TEST_FRONT_ZONE_ID = 3;
    private static final int TEST_VOLUME_GROUP_ID = 1;
    private static final int TEST_VOLUME_GROUP_INDEX = 10;
    private static final int TEST_FLAGS = 0;
    private static final int TEST_GAIN_MIN_VALUE = -3000;
    private static final int TEST_GAIN_MAX_VALUE = -1000;
    private static final int TEST_GAIN_DEFAULT_VALUE = -2000;
    private static final int TEST_VOLUME_GROUP_COUNT = 2;
    private static final int TEST_USAGE = USAGE_MEDIA;
    private static final int TEST_UID = 15;
    private static final long TEST_TIME_OUT_MS = 500;
    private static final long TEST_REQUEST_ID = 1;

    private static final AudioDeviceInfo  TEST_MICROPHONE_INPUT_DEVICE = new
            AudioDeviceInfoBuilder().setAddressName(MICROPHONE_ADDRESS)
            .setType(TYPE_BUILTIN_MIC).setIsSource(true).build();
    private static final AudioDeviceInfo TEST_TUNER_INPUT_DEVICE = new AudioDeviceInfoBuilder()
                .setAddressName(FM_TUNER_ADDRESS).setType(TYPE_FM_TUNER).setIsSource(true).build();
    private static final AudioDeviceInfo TEST_MEDIA_OUTPUT_DEVICE = new AudioDeviceInfoBuilder()
            .setAddressName(MEDIA_TEST_DEVICE).build();
    private static final AudioDeviceInfo TEST_NAV_OUTPUT_DEVICE = new AudioDeviceInfoBuilder()
            .setAddressName(NAVIGATION_TEST_DEVICE).build();

    private static final Executor DIRECT_EXECUTOR = Runnable::run;

    private final HandlerThread mHandlerThread = CarServiceUtils.getHandlerThread(
            Car_AUDIO_MANAGER_TEST_THREAD_NAME);
    private final Handler mHandler = new Handler(mHandlerThread.getLooper());
    private final RemoteException mRemoteException = new RemoteException();

    @Mock
    private Car mCar;
    @Mock
    private IBinder mBinderMock;
    @Mock
    private ICarAudio mServiceMock;
    @Mock
    private AudioManager mAudioManagerMock;
    @Mock
    private Context mContextMock;
    @Mock
    private CarVolumeGroupInfo mCarVolumeGroupInfoMock1;
    @Mock
    private CarVolumeGroupInfo mCarVolumeGroupInfoMock2;
    @Mock
    private AudioAttributes mAudioAttributesMock1;
    @Mock
    private AudioAttributes mAudioAttributesMock2;
    @Mock
    private CarAudioManager.CarVolumeCallback mVolumeCallbackMock1;
    @Mock
    private CarAudioManager.CarVolumeCallback mVolumeCallbackMock2;
    @Mock
    private CarAudioZoneConfigInfo mZoneConfigInfoMock1;
    @Mock
    private CarAudioZoneConfigInfo mZoneConfigInfoMock2;
    @Mock
    private OccupantZoneInfo mOccupantZoneInfoMock;
    @Mock
    private SwitchAudioZoneConfigCallback mSwitchCallbackMock;
    @Mock
    private CarVolumeGroupEventCallback mVolumeGroupEventCallbackMock1;
    @Mock
    private CarVolumeGroupEventCallback mVolumeGroupEventCallbackMock2;
    @Mock
    private PrimaryZoneMediaAudioRequestCallback mPrimaryZoneMediaAudioRequestCallbackMock;
    @Mock
    private MediaAudioRequestStatusCallback mMediaAudioRequestStatusCallbackMock;
    @Mock
    private AudioZonesMirrorStatusCallback mAudioZonesMirrorStatusCallback;

    private CarAudioManager mCarAudioManager;
    private TestAudioZoneConfigurationsChangeCallback mTestConfigCallback;

    @Before
    public void setUp() throws Exception {
        when(mBinderMock.queryLocalInterface(anyString())).thenReturn(mServiceMock);
        when(mCar.getContext()).thenReturn(mContextMock);
        when(mCar.getEventHandler()).thenReturn(mHandler);
        when(mContextMock.getSystemService(AudioManager.class)).thenReturn(mAudioManagerMock);
        mCarAudioManager = new CarAudioManager(mCar, mBinderMock);
        doAnswer(invocation -> invocation.getArgument(1)).when(mCar)
                .handleRemoteExceptionFromCarService(any(RemoteException.class), any());
        mTestConfigCallback = new TestAudioZoneConfigurationsChangeCallback();
        when(mServiceMock.registerAudioZoneConfigsChangeCallback(any())).thenReturn(true);
    }

    @Test
    public void isAudioFeatureEnabled() throws Exception {
        when(mServiceMock.isAudioFeatureEnabled(AUDIO_FEATURE_DYNAMIC_ROUTING)).thenReturn(true);

        expectWithMessage("Dynamic audio routing enabled")
                .that(mCarAudioManager.isAudioFeatureEnabled(AUDIO_FEATURE_DYNAMIC_ROUTING))
                .isTrue();
    }

    @Test
    public void isAudioFeatureEnabled_withServiceRemoteException_returnsFalse() throws Exception {
        doThrow(mRemoteException).when(mServiceMock).isAudioFeatureEnabled(
                AUDIO_FEATURE_DYNAMIC_ROUTING);

        expectWithMessage("Dynamic audio routing disabled when service throws remote exception")
                .that(mCarAudioManager.isAudioFeatureEnabled(AUDIO_FEATURE_DYNAMIC_ROUTING))
                .isFalse();
    }

    @Test
    public void setGroupVolume_withoutZoneId() throws Exception {
        mCarAudioManager.setGroupVolume(TEST_VOLUME_GROUP_ID, TEST_VOLUME_GROUP_INDEX, TEST_FLAGS);

        verify(mServiceMock).setGroupVolume(PRIMARY_AUDIO_ZONE, TEST_VOLUME_GROUP_ID,
                TEST_VOLUME_GROUP_INDEX, TEST_FLAGS);
    }

    @Test
    public void setGroupVolume_withZoneId() throws Exception {
        mCarAudioManager.setGroupVolume(TEST_REAR_RIGHT_ZONE_ID, TEST_VOLUME_GROUP_ID,
                TEST_VOLUME_GROUP_INDEX, TEST_FLAGS);

        verify(mServiceMock).setGroupVolume(TEST_REAR_RIGHT_ZONE_ID, TEST_VOLUME_GROUP_ID,
                TEST_VOLUME_GROUP_INDEX, TEST_FLAGS);
    }

    @Test
    public void setGroupVolume_withServiceRemoteException() throws Exception {
        doThrow(mRemoteException).when(mServiceMock).setGroupVolume(TEST_REAR_RIGHT_ZONE_ID,
                TEST_VOLUME_GROUP_ID, TEST_VOLUME_GROUP_INDEX, TEST_FLAGS);

        mCarAudioManager.setGroupVolume(TEST_REAR_RIGHT_ZONE_ID, TEST_VOLUME_GROUP_ID,
                TEST_VOLUME_GROUP_INDEX, TEST_FLAGS);

        verify(mCar).handleRemoteExceptionFromCarService(mRemoteException);
    }

    @Test
    public void getGroupMaxVolume_withoutZoneId() throws Exception {
        when(mServiceMock.getGroupMaxVolume(PRIMARY_AUDIO_ZONE, TEST_VOLUME_GROUP_ID))
                .thenReturn(TEST_GAIN_MAX_VALUE);

        expectWithMessage("Primary zone max volume")
                .that(mCarAudioManager.getGroupMaxVolume(TEST_VOLUME_GROUP_ID))
                .isEqualTo(TEST_GAIN_MAX_VALUE);
    }

    @Test
    public void getGroupMaxVolume_withZoneId() throws Exception {
        when(mServiceMock.getGroupMaxVolume(TEST_REAR_RIGHT_ZONE_ID, TEST_VOLUME_GROUP_ID))
                .thenReturn(TEST_GAIN_MAX_VALUE);

        expectWithMessage("Rear right zone max volume").that(mCarAudioManager
                .getGroupMaxVolume(TEST_REAR_RIGHT_ZONE_ID, TEST_VOLUME_GROUP_ID))
                .isEqualTo(TEST_GAIN_MAX_VALUE);
    }

    @Test
    public void getGroupMaxVolume_withServiceRemoteException() throws Exception {
        doThrow(mRemoteException).when(mServiceMock).getGroupMaxVolume(TEST_REAR_RIGHT_ZONE_ID,
                TEST_VOLUME_GROUP_ID);

        expectWithMessage("Rear right zone max volume when service throws remote exception")
                .that(mCarAudioManager.getGroupMaxVolume(TEST_REAR_RIGHT_ZONE_ID,
                        TEST_VOLUME_GROUP_ID)).isEqualTo(0);
    }

    @Test
    public void getGroupMinVolume_withoutZoneId() throws Exception {
        when(mServiceMock.getGroupMinVolume(PRIMARY_AUDIO_ZONE, TEST_VOLUME_GROUP_ID))
                .thenReturn(TEST_GAIN_MIN_VALUE);

        expectWithMessage("Primary zone min volume")
                .that(mCarAudioManager.getGroupMinVolume(TEST_VOLUME_GROUP_ID))
                .isEqualTo(TEST_GAIN_MIN_VALUE);
    }

    @Test
    public void getGroupMinVolume_withZoneId() throws Exception {
        when(mServiceMock.getGroupMinVolume(TEST_REAR_RIGHT_ZONE_ID, TEST_VOLUME_GROUP_ID))
                .thenReturn(TEST_GAIN_MIN_VALUE);

        expectWithMessage("Rear right zone min volume").that(mCarAudioManager
                .getGroupMinVolume(TEST_REAR_RIGHT_ZONE_ID, TEST_VOLUME_GROUP_ID))
                .isEqualTo(TEST_GAIN_MIN_VALUE);
    }

    @Test
    public void getGroupMinVolume_withServiceRemoteException() throws Exception {
        doThrow(mRemoteException).when(mServiceMock).getGroupMinVolume(TEST_REAR_RIGHT_ZONE_ID,
                TEST_VOLUME_GROUP_ID);

        expectWithMessage("Rear right zone min volume when service throws remote exception")
                .that(mCarAudioManager.getGroupMinVolume(TEST_REAR_RIGHT_ZONE_ID,
                        TEST_VOLUME_GROUP_ID)).isEqualTo(0);
    }

    @Test
    public void getGroupVolume_withoutZoneId() throws Exception {
        when(mServiceMock.getGroupVolume(PRIMARY_AUDIO_ZONE, TEST_VOLUME_GROUP_ID))
                .thenReturn(TEST_GAIN_DEFAULT_VALUE);

        expectWithMessage("Primary zone volume")
                .that(mCarAudioManager.getGroupVolume(TEST_VOLUME_GROUP_ID))
                .isEqualTo(TEST_GAIN_DEFAULT_VALUE);
    }

    @Test
    public void getGroupVolume_withZoneId() throws Exception {
        when(mServiceMock.getGroupVolume(TEST_REAR_RIGHT_ZONE_ID, TEST_VOLUME_GROUP_ID))
                .thenReturn(TEST_GAIN_DEFAULT_VALUE);

        expectWithMessage("Rear right zone volume").that(mCarAudioManager.getGroupVolume(
                TEST_REAR_RIGHT_ZONE_ID, TEST_VOLUME_GROUP_ID)).isEqualTo(TEST_GAIN_DEFAULT_VALUE);
    }

    @Test
    public void getGroupVolume_withServiceRemoteException() throws Exception {
        doThrow(mRemoteException).when(mServiceMock).getGroupVolume(TEST_REAR_RIGHT_ZONE_ID,
                TEST_VOLUME_GROUP_ID);

        expectWithMessage("Rear right zone volume when service throws remote exception")
                .that(mCarAudioManager.getGroupVolume(TEST_REAR_RIGHT_ZONE_ID,
                        TEST_VOLUME_GROUP_ID)).isEqualTo(0);
    }

    @Test
    public void setFadeTowardFront() throws Exception {
        float value = 0.3f;

        mCarAudioManager.setFadeTowardFront(value);

        verify(mServiceMock).setFadeTowardFront(value);
    }

    @Test
    public void setFadeTowardFront_withServiceRemoteException() throws Exception {
        float value = 0.3f;
        doThrow(mRemoteException).when(mServiceMock).setFadeTowardFront(value);

        mCarAudioManager.setFadeTowardFront(value);

        verify(mCar).handleRemoteExceptionFromCarService(mRemoteException);
    }

    @Test
    public void setBalanceTowardRight() throws Exception {
        float value = 0.4f;

        mCarAudioManager.setBalanceTowardRight(value);

        verify(mServiceMock).setBalanceTowardRight(value);
    }

    @Test
    public void setBalanceTowardRight_withServiceRemoteException() throws Exception {
        float value = 0.4f;
        doThrow(mRemoteException).when(mServiceMock).setBalanceTowardRight(value);

        mCarAudioManager.setBalanceTowardRight(value);

        verify(mCar).handleRemoteExceptionFromCarService(mRemoteException);
    }

    @Test
    public void getVolumeGroupCount_withoutZoneId() throws Exception {
        when(mServiceMock.getVolumeGroupCount(PRIMARY_AUDIO_ZONE))
                .thenReturn(TEST_VOLUME_GROUP_COUNT);

        expectWithMessage("Primary zone volume group count")
                .that(mCarAudioManager.getVolumeGroupCount()).isEqualTo(TEST_VOLUME_GROUP_COUNT);
    }

    @Test
    public void getVolumeGroupCount_withZoneId() throws Exception {
        when(mServiceMock.getVolumeGroupCount(TEST_REAR_RIGHT_ZONE_ID))
                .thenReturn(TEST_VOLUME_GROUP_COUNT);

        expectWithMessage("Rear right zone volume group count")
                .that(mCarAudioManager.getVolumeGroupCount(TEST_REAR_RIGHT_ZONE_ID))
                .isEqualTo(TEST_VOLUME_GROUP_COUNT);
    }

    @Test
    public void getVolumeGroupCount_withServiceRemoteException() throws Exception {
        doThrow(mRemoteException).when(mServiceMock).getVolumeGroupCount(
                TEST_REAR_RIGHT_ZONE_ID);

        expectWithMessage("Rear right zone volume group count when service throws remote exception")
                .that(mCarAudioManager.getVolumeGroupCount(TEST_REAR_RIGHT_ZONE_ID)).isEqualTo(0);
    }

    @Test
    public void getVolumeGroupIdForUsage_withoutZoneId() throws Exception {
        when(mServiceMock.getVolumeGroupIdForUsage(PRIMARY_AUDIO_ZONE, TEST_USAGE))
                .thenReturn(TEST_VOLUME_GROUP_ID);

        expectWithMessage("Volume group id for media usage in primary zone")
                .that(mCarAudioManager.getVolumeGroupIdForUsage(TEST_USAGE))
                .isEqualTo(TEST_VOLUME_GROUP_ID);
    }

    @Test
    public void getVolumeGroupIdForUsage_withZoneId() throws Exception {
        when(mServiceMock.getVolumeGroupIdForUsage(TEST_REAR_RIGHT_ZONE_ID, TEST_USAGE))
                .thenReturn(TEST_VOLUME_GROUP_ID);

        expectWithMessage("Volume group id for media usage in rear right zone")
                .that(mCarAudioManager.getVolumeGroupIdForUsage(TEST_REAR_RIGHT_ZONE_ID,
                        TEST_USAGE)).isEqualTo(TEST_VOLUME_GROUP_ID);
    }

    @Test
    public void getVolumeGroupIdForUsage_withServiceRemoteException() throws Exception {
        doThrow(mRemoteException).when(mServiceMock).getVolumeGroupIdForUsage(
                TEST_REAR_RIGHT_ZONE_ID, TEST_USAGE);

        expectWithMessage("Volume group id for media usage in rear right zone when service "
                + "throws remote exception").that(mCarAudioManager.getVolumeGroupIdForUsage(
                        TEST_REAR_RIGHT_ZONE_ID, TEST_USAGE)).isEqualTo(0);
    }

    @Test
    public void getUsagesForVolumeGroupId_withoutZoneId() throws Exception {
        int[] volumeGroupUsages = new int[]{USAGE_MEDIA, USAGE_GAME};
        when(mServiceMock.getUsagesForVolumeGroupId(PRIMARY_AUDIO_ZONE, TEST_VOLUME_GROUP_ID))
                .thenReturn(volumeGroupUsages);

        expectWithMessage("Group %s usages in primary zone", TEST_VOLUME_GROUP_ID)
                .that(mCarAudioManager.getUsagesForVolumeGroupId(TEST_VOLUME_GROUP_ID))
                .isEqualTo(volumeGroupUsages);
    }

    @Test
    public void getUsagesForVolumeGroupId_withZoneId() throws Exception {
        int[] volumeGroupUsages = new int[]{USAGE_MEDIA, USAGE_GAME};
        when(mServiceMock.getUsagesForVolumeGroupId(TEST_REAR_RIGHT_ZONE_ID, TEST_VOLUME_GROUP_ID))
                .thenReturn(volumeGroupUsages);

        expectWithMessage("Group %s usages in rear right zone", TEST_VOLUME_GROUP_ID)
                .that(mCarAudioManager.getUsagesForVolumeGroupId(TEST_REAR_RIGHT_ZONE_ID,
                        TEST_VOLUME_GROUP_ID)).isEqualTo(volumeGroupUsages);
    }

    @Test
    public void getUsagesForVolumeGroupId_withServiceRemoteException_returnsEmptyList()
            throws Exception {
        doThrow(mRemoteException).when(mServiceMock).getUsagesForVolumeGroupId(
                TEST_REAR_RIGHT_ZONE_ID, TEST_VOLUME_GROUP_ID);

        expectWithMessage("Group %s usages in rear right zone when service throws remote exception",
                TEST_VOLUME_GROUP_ID).that(mCarAudioManager.getUsagesForVolumeGroupId(
                        TEST_REAR_RIGHT_ZONE_ID, TEST_VOLUME_GROUP_ID)).asList().isEmpty();
    }

    @Test
    public void getVolumeGroupInfo() throws Exception {
        when(mServiceMock.getVolumeGroupInfo(TEST_REAR_RIGHT_ZONE_ID, TEST_VOLUME_GROUP_ID))
                .thenReturn(mCarVolumeGroupInfoMock1);

        expectWithMessage("Volume group info with id %s in rear right zone")
                .that(mCarAudioManager.getVolumeGroupInfo(TEST_REAR_RIGHT_ZONE_ID,
                        TEST_VOLUME_GROUP_ID)).isEqualTo(mCarVolumeGroupInfoMock1);
    }

    @Test
    public void getVolumeGroupInfo_withServiceRemoteException_returnsNull() throws Exception {
        doThrow(mRemoteException).when(mServiceMock).getVolumeGroupInfo(
                TEST_REAR_RIGHT_ZONE_ID, TEST_VOLUME_GROUP_ID);

        expectWithMessage("Volume group info with id %s in rear right zone when service throws "
                + "remote exception").that(mCarAudioManager.getVolumeGroupInfo(
                        TEST_REAR_RIGHT_ZONE_ID, TEST_VOLUME_GROUP_ID)).isNull();
    }

    @Test
    public void getVolumeGroupInfosForZone() throws Exception {
        when(mServiceMock.getVolumeGroupInfosForZone(TEST_REAR_RIGHT_ZONE_ID))
                .thenReturn(List.of(mCarVolumeGroupInfoMock1, mCarVolumeGroupInfoMock2));

        expectWithMessage("Volume group infos in rear right zone")
                .that(mCarAudioManager.getVolumeGroupInfosForZone(TEST_REAR_RIGHT_ZONE_ID))
                .containsExactly(mCarVolumeGroupInfoMock1, mCarVolumeGroupInfoMock2);
    }

    @Test
    public void getVolumeGroupInfosForZone_withServiceRemoteException_returnsEmptyList()
            throws Exception {
        doThrow(mRemoteException).when(mServiceMock).getVolumeGroupInfosForZone(
                TEST_REAR_RIGHT_ZONE_ID);

        expectWithMessage("Volume group infos in rear right zone when service throws remote "
                + "exception").that(mCarAudioManager.getVolumeGroupInfosForZone(
                        TEST_REAR_RIGHT_ZONE_ID)).isEmpty();
    }

    @Test
    public void getAudioAttributesForVolumeGroup() throws Exception {
        List<AudioAttributes> attributesList = List.of(mAudioAttributesMock1,
                mAudioAttributesMock2);
        when(mServiceMock.getAudioAttributesForVolumeGroup(mCarVolumeGroupInfoMock1))
                .thenReturn(attributesList);

        expectWithMessage("Audio attributes for volume group").that(mCarAudioManager
                .getAudioAttributesForVolumeGroup(mCarVolumeGroupInfoMock1))
                .isEqualTo(attributesList);
    }

    @Test
    public void getAudioAttributesForVolumeGroup_withServiceRemoteException_returnsEmptyList()
            throws Exception {
        doThrow(mRemoteException).when(mServiceMock).getAudioAttributesForVolumeGroup(
                mCarVolumeGroupInfoMock1);

        expectWithMessage("Audio attributes for volume group when service throws remote exception")
                .that(mCarAudioManager.getAudioAttributesForVolumeGroup(mCarVolumeGroupInfoMock1))
                .isEmpty();
    }

    @Test
    public void isPlaybackOnVolumeGroupActive() throws Exception {
        when(mServiceMock.isPlaybackOnVolumeGroupActive(TEST_REAR_RIGHT_ZONE_ID,
                TEST_VOLUME_GROUP_ID)).thenReturn(true);

        expectWithMessage("Enabled playback on volume group %s in rear right zone",
                TEST_VOLUME_GROUP_ID).that(mCarAudioManager.isPlaybackOnVolumeGroupActive(
                TEST_REAR_RIGHT_ZONE_ID, TEST_VOLUME_GROUP_ID)).isTrue();
    }

    @Test
    public void isPlaybackOnVolumeGroupActive_withServiceRemoteException_returnsFalse()
            throws Exception {
        doThrow(mRemoteException).when(mServiceMock).isPlaybackOnVolumeGroupActive(
                TEST_REAR_RIGHT_ZONE_ID, TEST_VOLUME_GROUP_ID);

        expectWithMessage("Enabled playback on volume group %s in rear right zone when service "
                        + "throws remote exception", TEST_VOLUME_GROUP_ID).that(mCarAudioManager
                .isPlaybackOnVolumeGroupActive(TEST_REAR_RIGHT_ZONE_ID, TEST_VOLUME_GROUP_ID))
                .isFalse();
    }

    @Test
    public void getAudioZoneIds() throws Exception {
        int[] expectedZoneIds = new int[]{PRIMARY_AUDIO_ZONE, TEST_REAR_LEFT_ZONE_ID,
                TEST_REAR_RIGHT_ZONE_ID, TEST_FRONT_ZONE_ID};
        when(mServiceMock.getAudioZoneIds()).thenReturn(expectedZoneIds);

        expectWithMessage("Zone ids").that(mCarAudioManager.getAudioZoneIds())
                .containsExactly(PRIMARY_AUDIO_ZONE, TEST_REAR_LEFT_ZONE_ID,
                        TEST_REAR_RIGHT_ZONE_ID, TEST_FRONT_ZONE_ID).inOrder();
    }

    @Test
    public void getAudioZoneIds_withServiceRemoteException_returnsEmptyList() throws Exception {
        doThrow(mRemoteException).when(mServiceMock).getAudioZoneIds();

        expectWithMessage("Zone ids when service throws remote exception")
                .that(mCarAudioManager.getAudioZoneIds()).isEmpty();
    }

    @Test
    public void getZoneIdForUid() throws Exception {
        when(mServiceMock.getZoneIdForUid(TEST_UID)).thenReturn(TEST_REAR_LEFT_ZONE_ID);

        expectWithMessage("Zone id for uid %s", TEST_UID).that(mCarAudioManager
                .getZoneIdForUid(TEST_UID)).isEqualTo(TEST_REAR_LEFT_ZONE_ID);
    }

    @Test
    public void getZoneIdForUid_withServiceRemoteException() throws Exception {
        doThrow(mRemoteException).when(mServiceMock).getZoneIdForUid(TEST_UID);

        expectWithMessage("Zone id for uid %s when service throws remote exception", TEST_UID)
                .that(mCarAudioManager.getZoneIdForUid(TEST_UID)).isEqualTo(0);
    }

    @Test
    public void setZoneIdForUid() throws Exception {
        when(mServiceMock.setZoneIdForUid(TEST_REAR_RIGHT_ZONE_ID, TEST_UID)).thenReturn(true);

        expectWithMessage("Status for setting uid %s to rear right zone", TEST_UID)
                .that(mCarAudioManager.setZoneIdForUid(TEST_REAR_RIGHT_ZONE_ID, TEST_UID)).isTrue();
    }

    @Test
    public void setZoneIdForUid_withServiceRemoteException_returnsFalse() throws Exception {
        doThrow(mRemoteException).when(mServiceMock).setZoneIdForUid(TEST_REAR_RIGHT_ZONE_ID,
                TEST_UID);

        expectWithMessage("Status for setting uid %s to rear right zone when service throws "
                + "remote exception").that(mCarAudioManager.setZoneIdForUid(TEST_REAR_RIGHT_ZONE_ID,
                TEST_UID)).isFalse();
    }

    @Test
    public void clearZoneIdForUid() throws Exception {
        when(mServiceMock.clearZoneIdForUid(TEST_UID)).thenReturn(true);

        expectWithMessage("Status for clearing zone id for uid %s", TEST_UID)
                .that(mCarAudioManager.clearZoneIdForUid(TEST_UID)).isTrue();
    }

    @Test
    public void clearZoneIdForUid_withServiceRemoteException_returnsFalse() throws Exception {
        doThrow(mRemoteException).when(mServiceMock).clearZoneIdForUid(TEST_UID);

        expectWithMessage("Status for clearing zone id for uid when service throws remote "
                + "exception").that(mCarAudioManager.clearZoneIdForUid(TEST_UID)).isFalse();
    }

    @Test
    public void registerCarVolumeGroupEventCallback() throws Exception {
        when(mServiceMock.registerCarVolumeEventCallback(any())).thenReturn(true);

        expectWithMessage("Status for registering car volume group event callback")
                .that(mCarAudioManager.registerCarVolumeGroupEventCallback(DIRECT_EXECUTOR,
                        mVolumeGroupEventCallbackMock1)).isTrue();
    }

    @Test
    public void registerCarVolumeGroupEventCallback_withMultipleCallbacks() throws Exception {
        when(mServiceMock.registerCarVolumeEventCallback(any())).thenReturn(true);
        mCarAudioManager.registerCarVolumeGroupEventCallback(DIRECT_EXECUTOR,
                mVolumeGroupEventCallbackMock1);
        verify(mServiceMock).registerCarVolumeEventCallback(any());
        clearInvocations(mServiceMock);

        expectWithMessage("Status for registering second car volume group event callback")
                .that(mCarAudioManager.registerCarVolumeGroupEventCallback(DIRECT_EXECUTOR,
                        mVolumeGroupEventCallbackMock2)).isTrue();
        verify(mServiceMock, never()).registerCarVolumeEventCallback(any());
    }

    @Test
    public void registerCarVolumeGroupEventCallback_withServiceReturnsFalse_returnsFalse()
            throws Exception {
        when(mServiceMock.registerCarVolumeEventCallback(any())).thenReturn(false);

        expectWithMessage("Failure for registering car volume group event callback")
                .that(mCarAudioManager.registerCarVolumeGroupEventCallback(DIRECT_EXECUTOR,
                        mVolumeGroupEventCallbackMock1)).isFalse();
    }

    @Test
    public void registerCarVolumeGroupEventCallback_withSameCallbackforMultipleTimes_returnsFalse()
            throws Exception {
        when(mServiceMock.registerCarVolumeEventCallback(any())).thenReturn(true);
        mCarAudioManager.registerCarVolumeGroupEventCallback(DIRECT_EXECUTOR,
                mVolumeGroupEventCallbackMock1);

        expectWithMessage("Failure for registering car volume group event callback "
                + "for multiple times").that(mCarAudioManager.registerCarVolumeGroupEventCallback(
                        DIRECT_EXECUTOR, mVolumeGroupEventCallbackMock1)).isFalse();
    }

    @Test
    public void registerCarVolumeGroupEventCallback_withNullExecutor_fails() {
        NullPointerException thrown = assertThrows(NullPointerException.class, () ->
                mCarAudioManager.registerCarVolumeGroupEventCallback(/* executor= */ null,
                        mVolumeGroupEventCallbackMock1));

        expectWithMessage("Exception for registering car volume group event callback with "
                + "null executor").that(thrown).hasMessageThat()
                .contains("Executor can not be null");
    }

    @Test
    public void registerCarVolumeGroupEventCallback_withNullCallback_fails() {
        NullPointerException thrown = assertThrows(NullPointerException.class, () ->
                mCarAudioManager.registerCarVolumeGroupEventCallback(DIRECT_EXECUTOR,
                        /* callback= */ null));

        expectWithMessage("Exception for registering null car volume group event callback")
                .that(thrown).hasMessageThat()
                .contains("Car volume event callback can not be null");
    }

    @Test
    public void registerCarVolumeGroupEventCallback_withServiceRemoteException_returnsFalse()
            throws Exception {
        doThrow(mRemoteException).when(mServiceMock).registerCarVolumeEventCallback(any());

        expectWithMessage("Failure for registering car volume group event callback when service "
                + "throws remote exception").that(mCarAudioManager
                .registerCarVolumeGroupEventCallback(DIRECT_EXECUTOR,
                        mVolumeGroupEventCallbackMock1)).isFalse();
    }

    @Test
    public void unregisterCarVolumeGroupEventCallback_withPartOfCallbacks() throws Exception {
        when(mServiceMock.registerCarVolumeEventCallback(any())).thenReturn(true);
        ICarVolumeEventCallback serviceCallback = getCarVolumeEventCallbackImpl(
                mVolumeGroupEventCallbackMock1);
        mCarAudioManager.registerCarVolumeGroupEventCallback(DIRECT_EXECUTOR,
                mVolumeGroupEventCallbackMock2);

        mCarAudioManager.unregisterCarVolumeGroupEventCallback(mVolumeGroupEventCallbackMock1);

        verify(mServiceMock, never()).unregisterCarVolumeEventCallback(serviceCallback);
    }

    @Test
    public void unregisterCarVolumeGroupEventCallback_withAllCallbacks() throws Exception {
        when(mServiceMock.registerCarVolumeEventCallback(any())).thenReturn(true);
        ICarVolumeEventCallback serviceCallback = getCarVolumeEventCallbackImpl(
                mVolumeGroupEventCallbackMock1);
        mCarAudioManager.registerCarVolumeGroupEventCallback(DIRECT_EXECUTOR,
                mVolumeGroupEventCallbackMock2);
        mCarAudioManager.unregisterCarVolumeGroupEventCallback(mVolumeGroupEventCallbackMock1);
        verify(mServiceMock, never()).unregisterCarVolumeEventCallback(serviceCallback);

        mCarAudioManager.unregisterCarVolumeGroupEventCallback(mVolumeGroupEventCallbackMock2);

        verify(mServiceMock).unregisterCarVolumeEventCallback(serviceCallback);
    }

    @Test
    public void unregisterCarVolumeGroupEventCallback_withNullCallback_fails() {
        NullPointerException thrown = assertThrows(NullPointerException.class, () ->
                mCarAudioManager.unregisterCarVolumeGroupEventCallback(/* callback= */ null));

        expectWithMessage("Exception for unregistering null car volume group event callback")
                .that(thrown).hasMessageThat()
                .contains("Car volume event callback can not be null");
    }

    @Test
    public void unregisterCarVolumeGroupEventCallback_withServiceRemoteException()
            throws Exception {
        when(mServiceMock.registerCarVolumeEventCallback(any())).thenReturn(true);
        ICarVolumeEventCallback serviceCallback = getCarVolumeEventCallbackImpl(
                mVolumeGroupEventCallbackMock1);
        doThrow(mRemoteException).when(mServiceMock).unregisterCarVolumeEventCallback(
                serviceCallback);

        mCarAudioManager.unregisterCarVolumeGroupEventCallback(mVolumeGroupEventCallbackMock1);
    }

    @Test
    public void onVolumeGroupEvent_forICarVolumeEventCallback() throws Exception {
        List<CarVolumeGroupEvent> groupEvents = List.of(new CarVolumeGroupEvent.Builder(
                List.of(mCarVolumeGroupInfoMock1), CarVolumeGroupEvent.EVENT_TYPE_MUTE_CHANGED)
                        .build(), new CarVolumeGroupEvent.Builder(
                List.of(mCarVolumeGroupInfoMock2),
                CarVolumeGroupEvent.EVENT_TYPE_VOLUME_BLOCKED_CHANGED).build());
        when(mServiceMock.registerCarVolumeEventCallback(any())).thenReturn(true);
        ICarVolumeEventCallback serviceCallback = getCarVolumeEventCallbackImpl(
                mVolumeGroupEventCallbackMock1);
        mCarAudioManager.registerCarVolumeGroupEventCallback(DIRECT_EXECUTOR,
                mVolumeGroupEventCallbackMock2);

        serviceCallback.onVolumeGroupEvent(groupEvents);

        verify(mVolumeGroupEventCallbackMock1, timeout(TEST_TIME_OUT_MS)).onVolumeGroupEvent(
                groupEvents);
        verify(mVolumeGroupEventCallbackMock2, timeout(TEST_TIME_OUT_MS)).onVolumeGroupEvent(
                groupEvents);
    }

    @Test
    public void onMasterMuteChanged_forICarVolumeEventCallback() throws Exception {
        when(mServiceMock.registerCarVolumeEventCallback(any())).thenReturn(true);
        mCarAudioManager.registerCarVolumeCallback(mVolumeCallbackMock1);
        mCarAudioManager.registerCarVolumeCallback(mVolumeCallbackMock2);
        ICarVolumeEventCallback serviceCallback = getCarVolumeEventCallbackImpl(
                mVolumeGroupEventCallbackMock1);

        serviceCallback.onMasterMuteChanged(TEST_REAR_RIGHT_ZONE_ID, TEST_FLAGS);

        verify(mVolumeCallbackMock1, timeout(TEST_TIME_OUT_MS)).onMasterMuteChanged(
                TEST_REAR_RIGHT_ZONE_ID, TEST_FLAGS);
        verify(mVolumeCallbackMock2, timeout(TEST_TIME_OUT_MS)).onMasterMuteChanged(
                TEST_REAR_RIGHT_ZONE_ID, TEST_FLAGS);
    }

    @Test
    public void isVolumeGroupMuted() throws Exception {
        when(mServiceMock.isVolumeGroupMuted(TEST_REAR_RIGHT_ZONE_ID, TEST_VOLUME_GROUP_ID))
                .thenReturn(true);

        expectWithMessage("Muted volume group").that(mCarAudioManager.isVolumeGroupMuted(
                TEST_REAR_RIGHT_ZONE_ID, TEST_VOLUME_GROUP_ID)).isTrue();
    }

    @Test
    public void isVolumeGroupMuted_withServiceRemoteException_returnsFalse() throws Exception {
        doThrow(mRemoteException).when(mServiceMock).isVolumeGroupMuted(
                TEST_REAR_RIGHT_ZONE_ID, TEST_VOLUME_GROUP_ID);

        expectWithMessage("Muted volume group when service throws remote exception")
                .that(mCarAudioManager.isVolumeGroupMuted(TEST_REAR_RIGHT_ZONE_ID,
                        TEST_VOLUME_GROUP_ID)).isFalse();
    }

    @Test
    public void setVolumeGroupMute() throws Exception {
        mCarAudioManager.setVolumeGroupMute(TEST_REAR_RIGHT_ZONE_ID, TEST_VOLUME_GROUP_ID,
                /* mute= */ true, TEST_FLAGS);

        verify(mServiceMock).setVolumeGroupMute(TEST_REAR_RIGHT_ZONE_ID, TEST_VOLUME_GROUP_ID,
                /* mute= */ true, TEST_FLAGS);
    }

    @Test
    public void setVolumeGroupMute_withServiceRemoteException() throws Exception {
        doThrow(mRemoteException).when(mServiceMock).setVolumeGroupMute(
                TEST_REAR_RIGHT_ZONE_ID, TEST_VOLUME_GROUP_ID, /* mute= */ true, TEST_FLAGS);

        mCarAudioManager.setVolumeGroupMute(TEST_REAR_RIGHT_ZONE_ID, TEST_VOLUME_GROUP_ID,
                /* mute= */ true, TEST_FLAGS);

        verify(mCar).handleRemoteExceptionFromCarService(mRemoteException);
    }

    @Test
    public void registerCarVolumeCallback() throws Exception {
        mCarAudioManager.registerCarVolumeCallback(mVolumeCallbackMock1);

        verify(mServiceMock).registerVolumeCallback(any(IBinder.class));
    }

    @Test
    public void registerCarVolumeCallback_withMultipleCallbacks() throws Exception {
        mCarAudioManager.registerCarVolumeCallback(mVolumeCallbackMock1);
        verify(mServiceMock).registerVolumeCallback(any(IBinder.class));
        clearInvocations(mServiceMock);

        mCarAudioManager.registerCarVolumeCallback(mVolumeCallbackMock2);

        verify(mServiceMock, never()).registerVolumeCallback(any(IBinder.class));
    }

    @Test
    public void unregisterCarVolumeCallback_withPartOfCallbacks() throws Exception {
        mCarAudioManager.registerCarVolumeCallback(mVolumeCallbackMock1);
        mCarAudioManager.registerCarVolumeCallback(mVolumeCallbackMock2);

        mCarAudioManager.unregisterCarVolumeCallback(mVolumeCallbackMock1);

        verify(mServiceMock, never()).unregisterVolumeCallback(any(IBinder.class));
    }

    @Test
    public void unregisterCarVolumeCallback_withAllCallbacks() throws Exception {
        ArgumentCaptor<IBinder> captor = ArgumentCaptor.forClass(IBinder.class);
        mCarAudioManager.registerCarVolumeCallback(mVolumeCallbackMock1);
        verify(mServiceMock).registerVolumeCallback(captor.capture());
        mCarAudioManager.registerCarVolumeCallback(mVolumeCallbackMock2);

        mCarAudioManager.unregisterCarVolumeCallback(mVolumeCallbackMock1);
        mCarAudioManager.unregisterCarVolumeCallback(mVolumeCallbackMock2);

        verify(mServiceMock).unregisterVolumeCallback(captor.getValue());
    }

    @Test
    public void unregisterCarVolumeCallback_withServiceRemoteException() throws Exception {
        mCarAudioManager.registerCarVolumeCallback(mVolumeCallbackMock1);
        verify(mServiceMock).registerVolumeCallback(any());
        doThrow(mRemoteException).when(mServiceMock).unregisterVolumeCallback(any());

        mCarAudioManager.unregisterCarVolumeCallback(mVolumeCallbackMock1);

        verify(mCar).handleRemoteExceptionFromCarService(mRemoteException);
    }

    @Test
    public void onCarDisconnected() throws Exception {
        ICarVolumeCallback serviceVolCallback = getCarVolumeCallbackImpl(mVolumeCallbackMock1);
        ICarVolumeEventCallback serviceVolEventCallback = getCarVolumeEventCallbackImpl(
                mVolumeGroupEventCallbackMock1);

        mCarAudioManager.onCarDisconnected();

        verify(mServiceMock).unregisterVolumeCallback(serviceVolCallback.asBinder());
        verify(mServiceMock).unregisterCarVolumeEventCallback(serviceVolEventCallback);
    }

    @Test
    public void onGroupMuteChanged_forCarVolumeCallback() throws Exception {
        ICarVolumeCallback callbackImpl = getCarVolumeCallbackImpl(mVolumeCallbackMock1);

        callbackImpl.onGroupMuteChanged(TEST_REAR_RIGHT_ZONE_ID, TEST_VOLUME_GROUP_ID, TEST_FLAGS);

        verify(mVolumeCallbackMock1, timeout(TEST_TIME_OUT_MS)).onGroupMuteChanged(
                TEST_REAR_RIGHT_ZONE_ID, TEST_VOLUME_GROUP_ID, TEST_FLAGS);
    }

    @Test
    public void onGroupVolumeChanged_forCarVolumeCallback() throws Exception {
        ICarVolumeCallback callbackImpl = getCarVolumeCallbackImpl(mVolumeCallbackMock1);

        callbackImpl.onGroupVolumeChanged(PRIMARY_AUDIO_ZONE, TEST_VOLUME_GROUP_ID, TEST_FLAGS);

        verify(mVolumeCallbackMock1, timeout(TEST_TIME_OUT_MS)).onGroupVolumeChanged(
                PRIMARY_AUDIO_ZONE, TEST_VOLUME_GROUP_ID, TEST_FLAGS);
    }

    @Test
    public void onMasterMuteChanged_forCarVolumeCallback() throws Exception {
        ICarVolumeCallback callbackImpl = getCarVolumeCallbackImpl(mVolumeCallbackMock1);

        callbackImpl.onMasterMuteChanged(TEST_REAR_LEFT_ZONE_ID, TEST_FLAGS);

        verify(mVolumeCallbackMock1, timeout(TEST_TIME_OUT_MS)).onMasterMuteChanged(
                TEST_REAR_LEFT_ZONE_ID, TEST_FLAGS);
    }

    @Test
    public void getAudioZoneConfigInfos() throws Exception {
        List<CarAudioZoneConfigInfo> zoneConfigInfos = List.of(mZoneConfigInfoMock1,
                mZoneConfigInfoMock2);
        when(mServiceMock.getAudioZoneConfigInfos(TEST_REAR_RIGHT_ZONE_ID))
                .thenReturn(zoneConfigInfos);

        expectWithMessage("All configuration infos for rear right zone")
                .that(mCarAudioManager.getAudioZoneConfigInfos(TEST_REAR_RIGHT_ZONE_ID))
                .isEqualTo(zoneConfigInfos);
    }

    @Test
    public void getAudioZoneConfigInfos_withServiceRemoteException_returnsEmptyList()
            throws Exception {
        doThrow(mRemoteException).when(mServiceMock).getAudioZoneConfigInfos(
                TEST_REAR_RIGHT_ZONE_ID);

        expectWithMessage("All configuration infos for rear right zone when service throws"
                + " remote exception").that(mCarAudioManager.getAudioZoneConfigInfos(
                        TEST_REAR_RIGHT_ZONE_ID)).isEmpty();
    }

    @Test
    public void getCurrentAudioZoneConfigInfo() throws Exception {
        when(mServiceMock.getCurrentAudioZoneConfigInfo(TEST_REAR_RIGHT_ZONE_ID))
                .thenReturn(mZoneConfigInfoMock1);

        expectWithMessage("Current configuration info for rear right zone")
                .that(mCarAudioManager.getCurrentAudioZoneConfigInfo(TEST_REAR_RIGHT_ZONE_ID))
                .isEqualTo(mZoneConfigInfoMock1);
    }

    @Test
    public void getCurrentAudioZoneConfigInfo_whenServiceThrowsRemoteException_returnsNull()
            throws Exception {
        doThrow(mRemoteException).when(mServiceMock).getCurrentAudioZoneConfigInfo(
                TEST_REAR_RIGHT_ZONE_ID);

        expectWithMessage("Current configuration info for rear right zone when service throws "
                + "remote exception").that(mCarAudioManager.getCurrentAudioZoneConfigInfo(
                        TEST_REAR_RIGHT_ZONE_ID)).isNull();
    }

    @Test
    public void switchAudioZoneToConfig() throws Exception {
        doAnswer(invocation -> {
            CarAudioZoneConfigInfo configInfoToSwitch = invocation.getArgument(0);
            ISwitchAudioZoneConfigCallback serviceCallback = invocation.getArgument(1);
            serviceCallback.onAudioZoneConfigSwitched(configInfoToSwitch, /* isSuccessful= */ true);
            return null;
        }).when(mServiceMock).switchZoneToConfig(any(CarAudioZoneConfigInfo.class),
                any(ISwitchAudioZoneConfigCallback.class));

        mCarAudioManager.switchAudioZoneToConfig(mZoneConfigInfoMock1, DIRECT_EXECUTOR,
                mSwitchCallbackMock);

        verify(mSwitchCallbackMock).onAudioZoneConfigSwitched(mZoneConfigInfoMock1,
                /* isSuccessful= */ true);
    }

    @Test
    public void switchAudioZoneToConfig_withNullConfig_fails() {
        NullPointerException thrown = assertThrows(NullPointerException.class, () ->
                mCarAudioManager.switchAudioZoneToConfig(/* zoneConfig= */ null, DIRECT_EXECUTOR,
                        mSwitchCallbackMock));

        expectWithMessage("Exception for switching zone configuration with null executor")
                .that(thrown).hasMessageThat()
                .contains("Audio zone configuration can not be null");
    }

    @Test
    public void switchAudioZoneToConfig_withNullExecutor_fails() {
        NullPointerException thrown = assertThrows(NullPointerException.class, () ->
                mCarAudioManager.switchAudioZoneToConfig(mZoneConfigInfoMock1, /* executor= */ null,
                        mSwitchCallbackMock));

        expectWithMessage("Exception for switching zone configuration with null executor")
                .that(thrown).hasMessageThat()
                .contains("Executor can not be null");
    }

    @Test
    public void switchAudioZoneToConfig_withNullCallback_fails() {
        NullPointerException thrown = assertThrows(NullPointerException.class, () ->
                mCarAudioManager.switchAudioZoneToConfig(mZoneConfigInfoMock1, DIRECT_EXECUTOR,
                        /* callback= */ null));

        expectWithMessage("Exception for switching zone configuration with null callback")
                .that(thrown).hasMessageThat()
                .contains("Switching audio zone configuration result callback can not be null");
    }

    @Test
    public void switchAudioZoneToConfig_whenServiceThrowsRemoteException() throws Exception {
        doThrow(mRemoteException).when(mServiceMock).switchZoneToConfig(any(
                CarAudioZoneConfigInfo.class), any(ISwitchAudioZoneConfigCallback.class));

        mCarAudioManager.switchAudioZoneToConfig(mZoneConfigInfoMock1, DIRECT_EXECUTOR,
                mSwitchCallbackMock);

        verify(mCar).handleRemoteExceptionFromCarService(mRemoteException);
    }

    @Test
    public void setAudioZoneConfigsChangeCallback_withNullExecutor_fails() {
        NullPointerException thrown = assertThrows(NullPointerException.class, () ->
                mCarAudioManager.setAudioZoneConfigsChangeCallback(/* executor= */ null,
                        mTestConfigCallback));

        expectWithMessage("Null audio zone config callback executor exception").that(thrown)
                .hasMessageThat().contains("Executor");
    }

    @Test
    public void setAudioZoneConfigsChangeCallback_withNullCallback_fails() {
        NullPointerException thrown = assertThrows(NullPointerException.class, () ->
                mCarAudioManager.setAudioZoneConfigsChangeCallback(DIRECT_EXECUTOR,
                        /* callback= */ null));

        expectWithMessage("Null audio zone config callback exception").that(thrown)
                .hasMessageThat().contains("Audio zone configs change callback");
    }

    @Test
    public void setAudioZoneConfigsChangeCallback() throws Exception {
        boolean registered = mCarAudioManager.setAudioZoneConfigsChangeCallback(DIRECT_EXECUTOR,
                mTestConfigCallback);

        expectWithMessage("Audio zone configs callback registered status").that(registered)
                .isTrue();
        verify(mServiceMock).registerAudioZoneConfigsChangeCallback(any());
    }

    @Test
    public void setAudioZoneConfigsChangeCallback_multipleTimes_fails() {
        mCarAudioManager.setAudioZoneConfigsChangeCallback(DIRECT_EXECUTOR, mTestConfigCallback);

        IllegalStateException thrown = assertThrows(IllegalStateException.class, () ->
                mCarAudioManager.setAudioZoneConfigsChangeCallback(DIRECT_EXECUTOR,
                        mTestConfigCallback));

        expectWithMessage("Audio zone configs callback re-registered exception").that(thrown)
                .hasMessageThat().contains("already set");
    }

    @Test
    public void onAudioZoneConfigurationsChanged_onRegisteredCallback() throws Exception {
        mCarAudioManager.setAudioZoneConfigsChangeCallback(DIRECT_EXECUTOR,
                mTestConfigCallback);
        ArgumentCaptor<IAudioZoneConfigurationsChangeCallback> captor =
                ArgumentCaptor.forClass(IAudioZoneConfigurationsChangeCallback.class);
        verify(mServiceMock).registerAudioZoneConfigsChangeCallback(captor.capture());
        IAudioZoneConfigurationsChangeCallback remoteCallback = captor.getValue();

        remoteCallback.onAudioZoneConfigurationsChanged(
                List.of(mZoneConfigInfoMock1, mZoneConfigInfoMock2), CONFIG_STATUS_CHANGED);

        mTestConfigCallback.waitForCallback();
        expectWithMessage("Triggered callback configs").that(mTestConfigCallback.mInfos)
                .containsExactly(mZoneConfigInfoMock1, mZoneConfigInfoMock2);
        expectWithMessage("Triggered callback status").that(mTestConfigCallback.mStatus)
                .isEqualTo(CONFIG_STATUS_CHANGED);
    }

    @Test
    public void clearAudioZoneConfigsCallback() throws Exception {
        mCarAudioManager.setAudioZoneConfigsChangeCallback(DIRECT_EXECUTOR,
                mTestConfigCallback);

        mCarAudioManager.clearAudioZoneConfigsCallback();

        verify(mServiceMock).unregisterAudioZoneConfigsChangeCallback(any());
    }

    @Test
    public void clearAudioZoneConfigsCallback_withNoRegisteredCallback() throws Exception {
        mCarAudioManager.clearAudioZoneConfigsCallback();

        verify(mServiceMock, never()).unregisterAudioZoneConfigsChangeCallback(any());
    }

    @Test
    public void setAudioZoneConfigsChangeCallback_withServiceException_fails() throws Exception {
        RemoteException remoteException = new RemoteException("Register failed!");
        when(mServiceMock.registerAudioZoneConfigsChangeCallback(any())).thenThrow(remoteException);

        boolean registered = mCarAudioManager.setAudioZoneConfigsChangeCallback(DIRECT_EXECUTOR,
                mTestConfigCallback);

        expectWithMessage("Audio zone configs callback registered status after service exception")
                .that(registered).isFalse();
    }

    @Test
    public void setPrimaryZoneMediaAudioRequestCallback() throws Exception {
        when(mServiceMock.registerPrimaryZoneMediaAudioRequestCallback(any())).thenReturn(true);

        expectWithMessage("Status for setting primary zone media audio request callback")
                .that(mCarAudioManager.setPrimaryZoneMediaAudioRequestCallback(DIRECT_EXECUTOR,
                        mPrimaryZoneMediaAudioRequestCallbackMock)).isTrue();
    }

    @Test
    public void setPrimaryZoneMediaAudioRequestCallback_fails() throws Exception {
        when(mServiceMock.registerPrimaryZoneMediaAudioRequestCallback(any())).thenReturn(false);
        mCarAudioManager.setPrimaryZoneMediaAudioRequestCallback(DIRECT_EXECUTOR,
                mPrimaryZoneMediaAudioRequestCallbackMock);

        expectWithMessage("Failure for setting primary zone media audio request callback")
                .that(mCarAudioManager.setPrimaryZoneMediaAudioRequestCallback(DIRECT_EXECUTOR,
                        mPrimaryZoneMediaAudioRequestCallbackMock)).isFalse();
    }

    @Test
    public void setPrimaryZoneMediaAudioRequestCallback_withNullExecutor_fails() {
        NullPointerException thrown = assertThrows(NullPointerException.class, () ->
                mCarAudioManager.setPrimaryZoneMediaAudioRequestCallback(/* executor= */ null,
                        mPrimaryZoneMediaAudioRequestCallbackMock));

        expectWithMessage("Exception for setting primary zone media audio request callback with"
                + "null executor").that(thrown).hasMessageThat()
                .contains("Executor can not be null");
    }

    @Test
    public void setPrimaryZoneMediaAudioRequestCallback_withNullCallback_fails() {
        NullPointerException thrown = assertThrows(NullPointerException.class, () ->
                mCarAudioManager.setPrimaryZoneMediaAudioRequestCallback(DIRECT_EXECUTOR,
                        /* callback= */ null));

        expectWithMessage("Exception for setting null primary zone media audio request callback")
                .that(thrown).hasMessageThat()
                .contains("Audio media request callback can not be null");
    }

    @Test
    public void setPrimaryZoneMediaAudioRequestCallback_forMultipleTimes_fails() throws Exception {
        when(mServiceMock.registerPrimaryZoneMediaAudioRequestCallback(any())).thenReturn(true);
        mCarAudioManager.setPrimaryZoneMediaAudioRequestCallback(DIRECT_EXECUTOR,
                mPrimaryZoneMediaAudioRequestCallbackMock);

        IllegalStateException thrown = assertThrows(IllegalStateException.class, () ->
                mCarAudioManager.setPrimaryZoneMediaAudioRequestCallback(DIRECT_EXECUTOR,
                        mock(PrimaryZoneMediaAudioRequestCallback.class)));

        expectWithMessage("Exception for setting primary zone media audio request callback"
                + " for multiple times").that(thrown).hasMessageThat()
                .contains("Primary zone media audio request is already set");
    }

    @Test
    public void setPrimaryZoneMediaAudioRequestCallback_afterClearingPreviousCallbacks()
            throws Exception {
        when(mServiceMock.registerPrimaryZoneMediaAudioRequestCallback(any())).thenReturn(true);
        mCarAudioManager.setPrimaryZoneMediaAudioRequestCallback(DIRECT_EXECUTOR,
                mPrimaryZoneMediaAudioRequestCallbackMock);
        mCarAudioManager.clearPrimaryZoneMediaAudioRequestCallback();

        expectWithMessage("Status for setting primary zone media audio request callback"
                + " again after clearing previous callbacks").that(mCarAudioManager
                .setPrimaryZoneMediaAudioRequestCallback(DIRECT_EXECUTOR,
                        mock(PrimaryZoneMediaAudioRequestCallback.class))).isTrue();
    }

    @Test
    public void setPrimaryZoneMediaAudioRequestCallback_whenServiceThrowsRemoteException()
            throws Exception {
        doThrow(mRemoteException).when(mServiceMock)
                .registerPrimaryZoneMediaAudioRequestCallback(any());

        expectWithMessage("Status for setting primary zone media audio request callback "
                + "when service throws remote exception").that(mCarAudioManager
                .setPrimaryZoneMediaAudioRequestCallback(DIRECT_EXECUTOR,
                        mPrimaryZoneMediaAudioRequestCallbackMock)).isFalse();
    }

    @Test
    public void clearPrimaryZoneMediaAudioRequestCallback() throws Exception {
        IPrimaryZoneMediaAudioRequestCallback serviceCallback =
                getPrimaryZoneMediaAudioRequestCallbackImpl(
                        mPrimaryZoneMediaAudioRequestCallbackMock);

        mCarAudioManager.clearPrimaryZoneMediaAudioRequestCallback();

        verify(mServiceMock).unregisterPrimaryZoneMediaAudioRequestCallback(serviceCallback);
    }

    @Test
    public void clearPrimaryZoneMediaAudioRequestCallback_beforeRegisteringCallback()
            throws Exception {
        IPrimaryZoneMediaAudioRequestCallback serviceCallback =
                getPrimaryZoneMediaAudioRequestCallbackImpl(
                        mPrimaryZoneMediaAudioRequestCallbackMock);
        mCarAudioManager.clearPrimaryZoneMediaAudioRequestCallback();
        verify(mServiceMock).unregisterPrimaryZoneMediaAudioRequestCallback(serviceCallback);
        clearInvocations(mServiceMock);

        mCarAudioManager.clearPrimaryZoneMediaAudioRequestCallback();

        verify(mServiceMock, never()).unregisterPrimaryZoneMediaAudioRequestCallback(
                serviceCallback);
    }

    @Test
    public void clearPrimaryZoneMediaAudioRequestCallback_whenServiceThrowsRemoteException()
            throws Exception {
        IPrimaryZoneMediaAudioRequestCallback serviceCallback =
                getPrimaryZoneMediaAudioRequestCallbackImpl(
                        mPrimaryZoneMediaAudioRequestCallbackMock);
        doThrow(mRemoteException).when(mServiceMock)
                .unregisterPrimaryZoneMediaAudioRequestCallback(serviceCallback);

        mCarAudioManager.clearPrimaryZoneMediaAudioRequestCallback();

        verify(mCar).handleRemoteExceptionFromCarService(mRemoteException);
    }

    @Test
    public void onRequestMediaOnPrimaryZone_forIPrimaryZoneMediaAudioRequestCallback()
            throws Exception {
        IPrimaryZoneMediaAudioRequestCallback serviceCallback =
                getPrimaryZoneMediaAudioRequestCallbackImpl(
                        mPrimaryZoneMediaAudioRequestCallbackMock);

        serviceCallback.onRequestMediaOnPrimaryZone(mOccupantZoneInfoMock, TEST_REQUEST_ID);

        verify(mPrimaryZoneMediaAudioRequestCallbackMock).onRequestMediaOnPrimaryZone(
                mOccupantZoneInfoMock, TEST_REQUEST_ID);
    }

    @Test
    public void onRequestMediaOnPrimaryZone_afterClearPrimaryZoneMediaAudioRequestCallback()
            throws Exception {
        IPrimaryZoneMediaAudioRequestCallback serviceCallback =
                getPrimaryZoneMediaAudioRequestCallbackImpl(
                        mPrimaryZoneMediaAudioRequestCallbackMock);
        mCarAudioManager.clearPrimaryZoneMediaAudioRequestCallback();

        serviceCallback.onRequestMediaOnPrimaryZone(mOccupantZoneInfoMock, TEST_REQUEST_ID);

        verify(mPrimaryZoneMediaAudioRequestCallbackMock, never()).onRequestMediaOnPrimaryZone(
                mOccupantZoneInfoMock, TEST_REQUEST_ID);
    }

    @Test
    public void onMediaAudioRequestStatusChanged_forIPrimaryZoneMediaAudioRequestCallback()
            throws Exception {
        IPrimaryZoneMediaAudioRequestCallback serviceCallback =
                getPrimaryZoneMediaAudioRequestCallbackImpl(
                        mPrimaryZoneMediaAudioRequestCallbackMock);

        serviceCallback.onMediaAudioRequestStatusChanged(mOccupantZoneInfoMock, TEST_REQUEST_ID,
                CarAudioManager.AUDIO_REQUEST_STATUS_STOPPED);

        verify(mPrimaryZoneMediaAudioRequestCallbackMock).onMediaAudioRequestStatusChanged(
                mOccupantZoneInfoMock, TEST_REQUEST_ID,
                CarAudioManager.AUDIO_REQUEST_STATUS_STOPPED);
    }

    @Test
    public void requestMediaAudioOnPrimaryZone() throws Exception {
        int expectedRequestStatus = CarAudioManager.AUDIO_REQUEST_STATUS_APPROVED;
        doAnswer(invocation -> {
            IMediaAudioRequestStatusCallback callback = invocation.getArgument(0);
            OccupantZoneInfo info = invocation.getArgument(1);
            callback.onMediaAudioRequestStatusChanged(info, TEST_REQUEST_ID, expectedRequestStatus);
            return TEST_REQUEST_ID;
        }).when(mServiceMock).requestMediaAudioOnPrimaryZone(any(
                IMediaAudioRequestStatusCallback.class), any(OccupantZoneInfo.class));

        long requestId = mCarAudioManager.requestMediaAudioOnPrimaryZone(mOccupantZoneInfoMock,
                DIRECT_EXECUTOR, mMediaAudioRequestStatusCallbackMock);

        expectWithMessage("Request id for media audio on primary zone").that(requestId)
                .isEqualTo(TEST_REQUEST_ID);
        verify(mMediaAudioRequestStatusCallbackMock).onMediaAudioRequestStatusChanged(
                mOccupantZoneInfoMock, TEST_REQUEST_ID, expectedRequestStatus);
    }

    @Test
    public void requestMediaAudioOnPrimaryZone_withNullOccupantZoneInfo() {
        NullPointerException thrown = assertThrows(NullPointerException.class, () ->
                mCarAudioManager.requestMediaAudioOnPrimaryZone(/* info= */ null,
                        DIRECT_EXECUTOR, mMediaAudioRequestStatusCallbackMock));

        expectWithMessage("Exception for requesting media audio on primary zone with null info")
                .that(thrown).hasMessageThat().contains("Occupant zone info can not be null");
    }

    @Test
    public void requestMediaAudioOnPrimaryZone_withNullExecutor() {
        NullPointerException thrown = assertThrows(NullPointerException.class, () ->
                mCarAudioManager.requestMediaAudioOnPrimaryZone(mOccupantZoneInfoMock,
                        /* executor= */ null, mMediaAudioRequestStatusCallbackMock));

        expectWithMessage("Exception for requesting media audio on primary zone with null executor")
                .that(thrown).hasMessageThat().contains("Executor can not be null");
    }

    @Test
    public void requestMediaAudioOnPrimaryZone_withNullCallback() {
        NullPointerException thrown = assertThrows(NullPointerException.class, () ->
                mCarAudioManager.requestMediaAudioOnPrimaryZone(mOccupantZoneInfoMock,
                        DIRECT_EXECUTOR, /* callback= */ null));

        expectWithMessage("Exception for requesting media audio on primary zone with "
                + "null callback").that(thrown).hasMessageThat()
                .contains("Media audio request status callback can not be null");
    }

    @Test
    public void requestMediaAudioOnPrimaryZone_whenServiceThrowsRemoteException_returnsInvalidId()
            throws Exception {
        doThrow(mRemoteException).when(mServiceMock).requestMediaAudioOnPrimaryZone(any(
                IMediaAudioRequestStatusCallback.class), any(OccupantZoneInfo.class));

        expectWithMessage("Request id for media audio on primary zone when service throws "
                + "remote exception").that(mCarAudioManager.requestMediaAudioOnPrimaryZone(
                        mOccupantZoneInfoMock, DIRECT_EXECUTOR,
                mMediaAudioRequestStatusCallbackMock)).isEqualTo(
                        CarAudioManager.INVALID_REQUEST_ID);
    }

    @Test
    public void cancelMediaAudioOnPrimaryZone() throws Exception {
        when(mServiceMock.requestMediaAudioOnPrimaryZone(any(), any())).thenReturn(TEST_REQUEST_ID);
        long requestId = mCarAudioManager.requestMediaAudioOnPrimaryZone(mOccupantZoneInfoMock,
                DIRECT_EXECUTOR, mMediaAudioRequestStatusCallbackMock);
        when(mServiceMock.cancelMediaAudioOnPrimaryZone(requestId)).thenReturn(true);

        expectWithMessage("Status for canceling pending media audio on primary zone")
                .that(mCarAudioManager.cancelMediaAudioOnPrimaryZone(requestId)).isTrue();
    }

    @Test
    public void cancelMediaAudioOnPrimaryZone_withInvalidId() throws Exception {
        when(mServiceMock.requestMediaAudioOnPrimaryZone(any(), any()))
                .thenReturn(CarAudioManager.INVALID_REQUEST_ID);
        long requestId = mCarAudioManager.requestMediaAudioOnPrimaryZone(mOccupantZoneInfoMock,
                DIRECT_EXECUTOR, mMediaAudioRequestStatusCallbackMock);

        expectWithMessage("Status for canceling media audio request with invalid id")
                .that(mCarAudioManager.cancelMediaAudioOnPrimaryZone(requestId)).isTrue();
        verify(mServiceMock, never()).cancelMediaAudioOnPrimaryZone(requestId);
    }

    @Test
    public void cancelMediaAudioOnPrimaryZone_whenServiceThrowsRemoteException_returnsFalse()
            throws Exception {
        when(mServiceMock.requestMediaAudioOnPrimaryZone(any(), any())).thenReturn(TEST_REQUEST_ID);
        long requestId = mCarAudioManager.requestMediaAudioOnPrimaryZone(mOccupantZoneInfoMock,
                DIRECT_EXECUTOR, mMediaAudioRequestStatusCallbackMock);
        doThrow(mRemoteException).when(mServiceMock).cancelMediaAudioOnPrimaryZone(requestId);

        expectWithMessage("Status for canceling pending media audio on primary zone "
                + "throws remote exception").that(mCarAudioManager.cancelMediaAudioOnPrimaryZone(
                        requestId)).isFalse();
    }

    @Test
    public void allowMediaAudioOnPrimaryZone() throws Exception {
        IPrimaryZoneMediaAudioRequestCallback serviceCallback =
                getPrimaryZoneMediaAudioRequestCallbackImpl(
                        mPrimaryZoneMediaAudioRequestCallbackMock);
        when(mServiceMock.allowMediaAudioOnPrimaryZone(serviceCallback.asBinder(), TEST_REQUEST_ID,
                /* allow= */ true)).thenReturn(true);

        expectWithMessage("Status for allowing media audio on primary zone")
                .that(mCarAudioManager.allowMediaAudioOnPrimaryZone(TEST_REQUEST_ID,
                        /* allow= */ true)).isTrue();
    }

    @Test
    public void allowMediaAudioOnPrimaryZone_beforeSettingCallback() {
        IllegalStateException thrown = assertThrows(IllegalStateException.class, () ->
                mCarAudioManager.allowMediaAudioOnPrimaryZone(TEST_REQUEST_ID, /* allow= */ true));

        expectWithMessage("Exception for allowing media audio on primary zone before "
                + "setting primary zone media audio request callback").that(thrown)
                .hasMessageThat().contains("Primary zone media audio request callback");
    }

    @Test
    public void allowMediaAudioOnPrimaryZone_whenServiceThrowsRemoteException_returnsFalse()
            throws Exception {
        IPrimaryZoneMediaAudioRequestCallback serviceCallback =
                getPrimaryZoneMediaAudioRequestCallbackImpl(
                        mPrimaryZoneMediaAudioRequestCallbackMock);
        doThrow(mRemoteException).when(mServiceMock).allowMediaAudioOnPrimaryZone(
                serviceCallback.asBinder(), TEST_REQUEST_ID, /* allow= */ true);

        expectWithMessage("Status for allowing media audio on primary zone when service throws"
                + "remote exception").that(mCarAudioManager.allowMediaAudioOnPrimaryZone(
                        TEST_REQUEST_ID, /* allow= */ true)).isFalse();
    }

    @Test
    public void resetMediaAudioOnPrimaryZone() throws Exception {
        when(mServiceMock.resetMediaAudioOnPrimaryZone(mOccupantZoneInfoMock)).thenReturn(true);

        expectWithMessage("Status for resetting media audio on primary zone")
                .that(mCarAudioManager.resetMediaAudioOnPrimaryZone(mOccupantZoneInfoMock))
                .isTrue();
    }

    @Test
    public void resetMediaAudioOnPrimaryZone_whenServiceThrowsRemoteException_returnsFalse()
            throws Exception {
        doThrow(mRemoteException).when(mServiceMock).resetMediaAudioOnPrimaryZone(
                mOccupantZoneInfoMock);

        expectWithMessage("Status for resetting media audio on primary zone when service "
                + "throws remote exception").that(mCarAudioManager.resetMediaAudioOnPrimaryZone(
                        mOccupantZoneInfoMock)).isFalse();
    }

    @Test
    public void isMediaAudioAllowedInPrimaryZone() throws Exception {
        when(mServiceMock.isMediaAudioAllowedInPrimaryZone(mOccupantZoneInfoMock)).thenReturn(true);

        expectWithMessage("Media audio allowed in primary zone").that(mCarAudioManager
                .isMediaAudioAllowedInPrimaryZone(mOccupantZoneInfoMock)).isTrue();
    }

    @Test
    public void isMediaAudioAllowedInPrimaryZone_whenServiceThrowsRemoteException_returnsFalse()
            throws Exception {
        doThrow(mRemoteException).when(mServiceMock).isMediaAudioAllowedInPrimaryZone(
                mOccupantZoneInfoMock);

        expectWithMessage("Media audio allowed in primary zone when service "
                + "throws remote exception").that(mCarAudioManager.isMediaAudioAllowedInPrimaryZone(
                        mOccupantZoneInfoMock)).isFalse();
    }

    @Test
    public void setAudioZoneMirrorStatusCallback_succeeds() throws Exception {
        when(mServiceMock.registerAudioZonesMirrorStatusCallback(any())).thenReturn(true);

        expectWithMessage("Status for setting audio zone mirror status callback")
                .that(mCarAudioManager.setAudioZoneMirrorStatusCallback(DIRECT_EXECUTOR,
                        mAudioZonesMirrorStatusCallback)).isTrue();
    }

    @Test
    public void setAudioZoneMirrorStatusCallback_withServiceReturnsFalse_fails() throws Exception {
        when(mServiceMock.registerAudioZonesMirrorStatusCallback(any())).thenReturn(false);

        expectWithMessage("Status for setting audio zone mirror status callback")
                .that(mCarAudioManager.setAudioZoneMirrorStatusCallback(DIRECT_EXECUTOR,
                        mAudioZonesMirrorStatusCallback)).isFalse();
    }

    @Test
    public void setAudioZoneMirrorStatusCallback_forMultipleTimes_fails() throws Exception {
        when(mServiceMock.registerAudioZonesMirrorStatusCallback(any())).thenReturn(true);
        mCarAudioManager.setAudioZoneMirrorStatusCallback(DIRECT_EXECUTOR,
                mAudioZonesMirrorStatusCallback);

        IllegalStateException thrown = assertThrows(IllegalStateException.class, () ->
                mCarAudioManager.setAudioZoneMirrorStatusCallback(DIRECT_EXECUTOR,
                        mock(AudioZonesMirrorStatusCallback.class)));

        expectWithMessage("Exception for setting audio zone mirror status callback multiple times")
                .that(thrown).hasMessageThat().contains("callback is already set");
    }

    @Test
    public void setAudioZoneMirrorStatusCallback_withNullExecutor() {
        NullPointerException thrown = assertThrows(NullPointerException.class, () ->
                mCarAudioManager.setAudioZoneMirrorStatusCallback(/* executor= */ null,
                        mAudioZonesMirrorStatusCallback));

        expectWithMessage("Exception for setting audio zone mirror status callback with"
                + " null executor").that(thrown).hasMessageThat()
                .contains("Executor can not be null");
    }

    @Test
    public void setAudioZoneMirrorStatusCallback_withNullCallback() {
        NullPointerException thrown = assertThrows(NullPointerException.class, () ->
                mCarAudioManager.setAudioZoneMirrorStatusCallback(DIRECT_EXECUTOR,
                        /* callback= */ null));

        expectWithMessage("Exception for setting audio zone mirror status callback with "
                + "null callback").that(thrown).hasMessageThat()
                .contains("Audio zones mirror status callback can not be null");
    }

    @Test
    public void setAudioZoneMirrorStatusCallback_whenServiceThrowsRemoteException_returnsFalse()
            throws Exception {
        doThrow(mRemoteException).when(mServiceMock).registerAudioZonesMirrorStatusCallback(
                any());

        expectWithMessage("Status for setting audio zone mirror status callback when service "
                + "throws remote exception").that(mCarAudioManager.setAudioZoneMirrorStatusCallback(
                        DIRECT_EXECUTOR, mAudioZonesMirrorStatusCallback)).isFalse();
    }

    @Test
    public void clearAudioZonesMirrorStatusCallback() throws Exception {
        when(mServiceMock.registerAudioZonesMirrorStatusCallback(any())).thenReturn(true);
        ArgumentCaptor<IAudioZonesMirrorStatusCallback> captor = ArgumentCaptor.forClass(
                IAudioZonesMirrorStatusCallback.class);
        mCarAudioManager.setAudioZoneMirrorStatusCallback(DIRECT_EXECUTOR,
                mAudioZonesMirrorStatusCallback);
        verify(mServiceMock).registerAudioZonesMirrorStatusCallback(captor.capture());

        mCarAudioManager.clearAudioZonesMirrorStatusCallback();

        verify(mServiceMock).unregisterAudioZonesMirrorStatusCallback(captor.getValue());
    }

    @Test
    public void clearAudioZonesMirrorStatusCallback_withoutCallbackSet() throws Exception {
        mCarAudioManager.clearAudioZonesMirrorStatusCallback();

        verify(mServiceMock, never()).unregisterAudioZonesMirrorStatusCallback(any());
    }

    @Test
    public void clearAudioZonesMirrorStatusCallback_whenServiceThrowsRemoteException()
            throws Exception {
        when(mServiceMock.registerAudioZonesMirrorStatusCallback(any())).thenReturn(true);
        ArgumentCaptor<IAudioZonesMirrorStatusCallback> captor = ArgumentCaptor.forClass(
                IAudioZonesMirrorStatusCallback.class);
        mCarAudioManager.setAudioZoneMirrorStatusCallback(DIRECT_EXECUTOR,
                mAudioZonesMirrorStatusCallback);
        verify(mServiceMock).registerAudioZonesMirrorStatusCallback(captor.capture());
        doThrow(mRemoteException).when(mServiceMock).unregisterAudioZonesMirrorStatusCallback(
                captor.getValue());

        mCarAudioManager.clearAudioZonesMirrorStatusCallback();

        verify(mCar).handleRemoteExceptionFromCarService(mRemoteException);
    }

    @Test
    public void canEnableAudioMirror() throws Exception {
        when(mServiceMock.canEnableAudioMirror()).thenReturn(
                CarAudioManager.AUDIO_MIRROR_OUT_OF_OUTPUT_DEVICES);

        expectWithMessage("Audio mirror status").that(mCarAudioManager.canEnableAudioMirror())
                .isEqualTo(CarAudioManager.AUDIO_MIRROR_OUT_OF_OUTPUT_DEVICES);
    }

    @Test
    public void canEnableAudioMirror_whenServiceThrowsRemoteException_fails() throws Exception {
        doThrow(mRemoteException).when(mServiceMock).canEnableAudioMirror();

        expectWithMessage("Audio mirror status when service throws remote exception")
                .that(mCarAudioManager.canEnableAudioMirror()).isEqualTo(
                        CarAudioManager.AUDIO_MIRROR_INTERNAL_ERROR);
    }

    @Test
    public void enableMirrorForAudioZones() throws Exception {
        List<Integer> zonesToMirrorList = List.of(TEST_REAR_LEFT_ZONE_ID, TEST_REAR_RIGHT_ZONE_ID);
        IAudioZonesMirrorStatusCallback callback = getAudioZonesMirrorStatusCallbackWrapper();
        doAnswer(invocation -> {
            int[] zones = invocation.getArgument(0);
            callback.onAudioZonesMirrorStatusChanged(zones,
                    CarAudioManager.AUDIO_REQUEST_STATUS_APPROVED);
            return TEST_REQUEST_ID;
        }).when(mServiceMock).enableMirrorForAudioZones(any(int[].class));

        long requestId = mCarAudioManager.enableMirrorForAudioZones(zonesToMirrorList);

        expectWithMessage("Request id for enabling mirror for audio zones").that(requestId)
                .isEqualTo(TEST_REQUEST_ID);
        verify(mAudioZonesMirrorStatusCallback).onAudioZonesMirrorStatusChanged(zonesToMirrorList,
                CarAudioManager.AUDIO_REQUEST_STATUS_APPROVED);
    }

    @Test
    public void enableMirrorForAudioZones_withNullZoneList_fails() {
        NullPointerException thrown = assertThrows(NullPointerException.class, () ->
                mCarAudioManager.enableMirrorForAudioZones(/* audioZonesToMirror= */ null));

        expectWithMessage("Exception for enabling mirror on null zone list").that(thrown)
                .hasMessageThat().contains("Audio zones to mirror should not be null");
    }

    @Test
    public void enableMirrorForAudioZones_whenServiceThrowsRemoteException_returnsInvalidId()
            throws Exception {
        List<Integer> zonesToMirrorList = List.of(TEST_REAR_LEFT_ZONE_ID, TEST_REAR_RIGHT_ZONE_ID);
        getAudioZonesMirrorStatusCallbackWrapper();
        doThrow(mRemoteException).when(mServiceMock).enableMirrorForAudioZones(any(
                int[].class));

        expectWithMessage("Request id for enabling mirror for audio zones when service throws "
                + "remote exception").that(mCarAudioManager.enableMirrorForAudioZones(
                        zonesToMirrorList)).isEqualTo(CarAudioManager.INVALID_REQUEST_ID);
    }

    @Test
    public void extendAudioMirrorRequest() throws Exception {
        List<Integer> zonesToMirrorList = List.of(TEST_REAR_LEFT_ZONE_ID, TEST_FRONT_ZONE_ID);
        IAudioZonesMirrorStatusCallback callback = getAudioZonesMirrorStatusCallbackWrapper();
        doAnswer(invocation -> {
            int[] zones = invocation.getArgument(1);
            callback.onAudioZonesMirrorStatusChanged(zones,
                    CarAudioManager.AUDIO_REQUEST_STATUS_REJECTED);
            return null;
        }).when(mServiceMock).extendAudioMirrorRequest(eq(TEST_REQUEST_ID), any(int[].class));

        mCarAudioManager.extendAudioMirrorRequest(TEST_REQUEST_ID, zonesToMirrorList);

        verify(mAudioZonesMirrorStatusCallback).onAudioZonesMirrorStatusChanged(zonesToMirrorList,
                CarAudioManager.AUDIO_REQUEST_STATUS_REJECTED);
    }

    @Test
    public void extendAudioMirrorRequest_withNullZoneList_fails() {
        NullPointerException thrown = assertThrows(NullPointerException.class, () ->
                mCarAudioManager.extendAudioMirrorRequest(TEST_REQUEST_ID,
                        /* audioZonesToMirror= */ null));

        expectWithMessage("Exception for extending mirror on null zone list").that(thrown)
                .hasMessageThat().contains("Audio zones to mirror should not be null");
    }

    @Test
    public void extendAudioMirrorRequest_whenServiceThrowsRemoteException() throws Exception {
        List<Integer> zonesToMirrorList = List.of(TEST_REAR_LEFT_ZONE_ID, TEST_FRONT_ZONE_ID);
        getAudioZonesMirrorStatusCallbackWrapper();
        doThrow(mRemoteException).when(mServiceMock).extendAudioMirrorRequest(
                eq(TEST_REQUEST_ID), any(int[].class));

        mCarAudioManager.extendAudioMirrorRequest(TEST_REQUEST_ID, zonesToMirrorList);

        verify(mCar).handleRemoteExceptionFromCarService(mRemoteException);
    }

    @Test
    public void disableAudioMirror() throws Exception {
        List<Integer> zonesToMirrorList = List.of(TEST_REAR_LEFT_ZONE_ID, TEST_REAR_RIGHT_ZONE_ID);
        IAudioZonesMirrorStatusCallback callback = getAudioZonesMirrorStatusCallbackWrapper();
        long requestId = mCarAudioManager.enableMirrorForAudioZones(zonesToMirrorList);
        doAnswer(invocation -> {
            callback.onAudioZonesMirrorStatusChanged(new int[]{TEST_REAR_LEFT_ZONE_ID,
                    TEST_REAR_RIGHT_ZONE_ID}, CarAudioManager.AUDIO_REQUEST_STATUS_CANCELLED);
            return null;
        }).when(mServiceMock).disableAudioMirror(requestId);

        mCarAudioManager.disableAudioMirror(requestId);

        verify(mAudioZonesMirrorStatusCallback).onAudioZonesMirrorStatusChanged(zonesToMirrorList,
                CarAudioManager.AUDIO_REQUEST_STATUS_CANCELLED);
    }

    @Test
    public void disableAudioMirror_whenServiceThrowsRemoteException() throws Exception {
        List<Integer> zonesToMirrorList = List.of(TEST_REAR_LEFT_ZONE_ID, TEST_REAR_RIGHT_ZONE_ID);
        getAudioZonesMirrorStatusCallbackWrapper();
        long requestId = mCarAudioManager.enableMirrorForAudioZones(zonesToMirrorList);
        doThrow(mRemoteException).when(mServiceMock).disableAudioMirror(requestId);

        mCarAudioManager.disableAudioMirror(requestId);

        verify(mCar).handleRemoteExceptionFromCarService(mRemoteException);
    }

    @Test
    public void disableAudioMirrorForZone() throws Exception {
        List<Integer> zonesToMirrorList = List.of(TEST_REAR_LEFT_ZONE_ID, TEST_REAR_RIGHT_ZONE_ID);
        IAudioZonesMirrorStatusCallback callback = getAudioZonesMirrorStatusCallbackWrapper();
        doAnswer(invocation -> {
            callback.onAudioZonesMirrorStatusChanged(new int[]{TEST_REAR_LEFT_ZONE_ID,
                    TEST_REAR_RIGHT_ZONE_ID}, CarAudioManager.AUDIO_REQUEST_STATUS_CANCELLED);
            return null;
        }).when(mServiceMock).disableAudioMirrorForZone(TEST_REAR_RIGHT_ZONE_ID);

        mCarAudioManager.disableAudioMirrorForZone(TEST_REAR_RIGHT_ZONE_ID);

        verify(mAudioZonesMirrorStatusCallback).onAudioZonesMirrorStatusChanged(zonesToMirrorList,
                CarAudioManager.AUDIO_REQUEST_STATUS_CANCELLED);
    }

    @Test
    public void disableAudioMirrorForZone_whenServiceThrowsRemoteException() throws Exception {
        getAudioZonesMirrorStatusCallbackWrapper();
        doThrow(mRemoteException).when(mServiceMock).disableAudioMirrorForZone(
                TEST_REAR_RIGHT_ZONE_ID);

        mCarAudioManager.disableAudioMirrorForZone(TEST_REAR_RIGHT_ZONE_ID);

        verify(mCar).handleRemoteExceptionFromCarService(mRemoteException);
    }

    @Test
    public void getMirrorAudioZonesForAudioZone() throws Exception {
        when(mServiceMock.getMirrorAudioZonesForAudioZone(TEST_REAR_RIGHT_ZONE_ID)).thenReturn(
                new int[]{TEST_REAR_LEFT_ZONE_ID, TEST_FRONT_ZONE_ID});

        expectWithMessage("Mirror zones for rear right zone").that(mCarAudioManager
                .getMirrorAudioZonesForAudioZone(TEST_REAR_RIGHT_ZONE_ID)).containsExactly(
                TEST_REAR_LEFT_ZONE_ID, TEST_FRONT_ZONE_ID).inOrder();
    }

    @Test
    public void getMirrorAudioZonesForAudioZone_whenServiceThrowsRemoteException_returnsEmptyList()
            throws Exception {
        doThrow(mRemoteException).when(mServiceMock).getMirrorAudioZonesForAudioZone(
                TEST_REAR_RIGHT_ZONE_ID);

        expectWithMessage("Mirror zones for rear right zone when service throws remote exception")
                .that(mCarAudioManager.getMirrorAudioZonesForAudioZone(TEST_REAR_RIGHT_ZONE_ID))
                .isEmpty();
    }

    @Test
    public void getMirrorAudioZonesForMirrorRequest() throws Exception {
        when(mServiceMock.getMirrorAudioZonesForMirrorRequest(TEST_REQUEST_ID)).thenReturn(
                new int[]{TEST_REAR_LEFT_ZONE_ID, TEST_REAR_RIGHT_ZONE_ID});

        expectWithMessage("Mirror zones for request id").that(mCarAudioManager
                .getMirrorAudioZonesForMirrorRequest(TEST_REQUEST_ID)).containsExactly(
                TEST_REAR_LEFT_ZONE_ID, TEST_REAR_RIGHT_ZONE_ID).inOrder();
    }

    @Test
    public void getMirrorAudioZonesForMirrorRequest_whenServiceThrowsRemoteException()
            throws Exception {
        doThrow(mRemoteException).when(mServiceMock).getMirrorAudioZonesForMirrorRequest(
                TEST_REQUEST_ID);

        expectWithMessage("Mirror zones for request id when service throws remote exception")
                .that(mCarAudioManager.getMirrorAudioZonesForMirrorRequest(TEST_REQUEST_ID))
                .isEmpty();
    }

    @Test
    public void getOutputDeviceForUsage() throws Exception {
        when(mAudioManagerMock.getDevices(AudioManager.GET_DEVICES_OUTPUTS)).thenReturn(
                new AudioDeviceInfo[]{TEST_MEDIA_OUTPUT_DEVICE, TEST_NAV_OUTPUT_DEVICE});
        when(mServiceMock.getOutputDeviceAddressForUsage(PRIMARY_AUDIO_ZONE,
                USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)).thenReturn(NAVIGATION_TEST_DEVICE);

        AudioDeviceInfo outputDevice = mCarAudioManager.getOutputDeviceForUsage(PRIMARY_AUDIO_ZONE,
                USAGE_ASSISTANCE_NAVIGATION_GUIDANCE);

        expectWithMessage("Navigation output device").that(outputDevice)
                .isEqualTo(TEST_NAV_OUTPUT_DEVICE);
    }

    @Test
    public void getOutputDeviceForUsage_withDeviceAddressNotFound_returnsNull() throws Exception {
        String invalidDeviceAddress = "invalid_bus";
        when(mAudioManagerMock.getDevices(AudioManager.GET_DEVICES_OUTPUTS)).thenReturn(
                new AudioDeviceInfo[]{TEST_MEDIA_OUTPUT_DEVICE, TEST_NAV_OUTPUT_DEVICE});
        when(mServiceMock.getOutputDeviceAddressForUsage(PRIMARY_AUDIO_ZONE,
                USAGE_GAME)).thenReturn(invalidDeviceAddress);

        expectWithMessage("Null output device").that(mCarAudioManager.getOutputDeviceForUsage(
                PRIMARY_AUDIO_ZONE, USAGE_GAME)).isNull();
    }

    @Test
    public void getOutputDeviceForUsage_whenServiceThrowsRemoteException_returnsNull()
            throws Exception {
        doThrow(mRemoteException).when(mServiceMock).getOutputDeviceAddressForUsage(
                PRIMARY_AUDIO_ZONE, USAGE_ASSISTANCE_NAVIGATION_GUIDANCE);

        expectWithMessage("Navigation output device when service throws remote exception")
                .that(mCarAudioManager.getOutputDeviceForUsage(PRIMARY_AUDIO_ZONE,
                        USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)).isNull();
    }

    @Test
    public void getInputDevicesForZoneId() throws Exception {
        List<AudioDeviceAttributes> inputDevicesFromService = List.of(
                new AudioDeviceAttributes(TEST_MICROPHONE_INPUT_DEVICE), new AudioDeviceAttributes(
                        TEST_TUNER_INPUT_DEVICE));
        when(mServiceMock.getInputDevicesForZoneId(PRIMARY_AUDIO_ZONE)).thenReturn(
                inputDevicesFromService);
        when(mAudioManagerMock.getDevices(AudioManager.GET_DEVICES_INPUTS)).thenReturn(
                new AudioDeviceInfo[]{TEST_MICROPHONE_INPUT_DEVICE, TEST_TUNER_INPUT_DEVICE});

        List<AudioDeviceInfo> inputDevices = mCarAudioManager.getInputDevicesForZoneId(
                PRIMARY_AUDIO_ZONE);

        expectWithMessage("Input devices for primary zone").that(inputDevices)
                .containsExactly(TEST_MICROPHONE_INPUT_DEVICE, TEST_TUNER_INPUT_DEVICE);
    }

    @Test
    public void getInputDevicesForZoneId_withInvalidDevicesInAudioManager() throws Exception {
        List<AudioDeviceAttributes> inputDevicesFromService = List.of(new AudioDeviceAttributes(
                TEST_MICROPHONE_INPUT_DEVICE));
        when(mServiceMock.getInputDevicesForZoneId(PRIMARY_AUDIO_ZONE)).thenReturn(
                inputDevicesFromService);
        when(mAudioManagerMock.getDevices(AudioManager.GET_DEVICES_INPUTS)).thenReturn(
                new AudioDeviceInfo[]{TEST_MEDIA_OUTPUT_DEVICE, TEST_TUNER_INPUT_DEVICE});

        List<AudioDeviceInfo> inputDevices = mCarAudioManager.getInputDevicesForZoneId(
                PRIMARY_AUDIO_ZONE);

        expectWithMessage("No input devices found for primary zone").that(inputDevices).isEmpty();
    }

    @Test
    public void getInputDevicesForZoneId_whenServiceThrowsRemoteException_returnsEmptyList()
            throws Exception {
        doThrow(mRemoteException).when(mServiceMock).getInputDevicesForZoneId(
                PRIMARY_AUDIO_ZONE);

        expectWithMessage("Input devices for primary zone when service throws remote exception")
                .that(mCarAudioManager.getInputDevicesForZoneId(PRIMARY_AUDIO_ZONE)).isEmpty();
    }

    private ICarVolumeCallback getCarVolumeCallbackImpl(CarAudioManager.CarVolumeCallback
            callbackMock) throws Exception {
        ArgumentCaptor<IBinder> captor = ArgumentCaptor.forClass(IBinder.class);
        mCarAudioManager.registerCarVolumeCallback(callbackMock);
        verify(mServiceMock).registerVolumeCallback(captor.capture());
        return ICarVolumeCallback.Stub.asInterface(captor.getValue());
    }

    private ICarVolumeEventCallback getCarVolumeEventCallbackImpl(CarVolumeGroupEventCallback
            callbackMock) throws Exception {
        when(mServiceMock.registerCarVolumeEventCallback(any())).thenReturn(true);
        mCarAudioManager.registerCarVolumeGroupEventCallback(DIRECT_EXECUTOR, callbackMock);
        ArgumentCaptor<ICarVolumeEventCallback> captor = ArgumentCaptor.forClass(
                ICarVolumeEventCallback.class);
        verify(mServiceMock).registerCarVolumeEventCallback(captor.capture());
        return captor.getValue();
    }

    private IPrimaryZoneMediaAudioRequestCallback getPrimaryZoneMediaAudioRequestCallbackImpl(
            PrimaryZoneMediaAudioRequestCallback callbackMock) throws Exception {
        when(mServiceMock.registerPrimaryZoneMediaAudioRequestCallback(any())).thenReturn(true);
        ArgumentCaptor<IPrimaryZoneMediaAudioRequestCallback> captor = ArgumentCaptor.forClass(
                IPrimaryZoneMediaAudioRequestCallback.class);
        mCarAudioManager.setPrimaryZoneMediaAudioRequestCallback(DIRECT_EXECUTOR, callbackMock);
        verify(mServiceMock).registerPrimaryZoneMediaAudioRequestCallback(captor.capture());
        return captor.getValue();
    }

    private IAudioZonesMirrorStatusCallback getAudioZonesMirrorStatusCallbackWrapper()
            throws Exception {
        ArgumentCaptor<IAudioZonesMirrorStatusCallback> captor = ArgumentCaptor.forClass(
                IAudioZonesMirrorStatusCallback.class);
        when(mServiceMock.registerAudioZonesMirrorStatusCallback(
                any(IAudioZonesMirrorStatusCallback.class))).thenReturn(true);
        mCarAudioManager.setAudioZoneMirrorStatusCallback(DIRECT_EXECUTOR,
                mAudioZonesMirrorStatusCallback);
        verify(mServiceMock).registerAudioZonesMirrorStatusCallback(captor.capture());
        return captor.getValue();
    }

    private static final class  TestAudioZoneConfigurationsChangeCallback
            implements AudioZoneConfigurationsChangeCallback {

        private static final long TEST_CALLBACK_TIMEOUT_MS = 500;
        private List<CarAudioZoneConfigInfo> mInfos;
        private int mStatus;

        private CountDownLatch mStatusLatch = new CountDownLatch(1);
        @Override
        public void onAudioZoneConfigurationsChanged(List<CarAudioZoneConfigInfo> configs,
                int status) {
            mInfos = configs;
            mStatus = status;
            mStatusLatch.countDown();
        }

        private void waitForCallback() throws Exception {
            mStatusLatch.await(TEST_CALLBACK_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        }
    }
}
