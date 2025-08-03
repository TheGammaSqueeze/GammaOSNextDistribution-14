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

package android.platform.helpers;

import static android.media.AudioAttributes.USAGE_ALARM;
import static android.media.AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE;
import static android.media.AudioAttributes.USAGE_MEDIA;
import static android.media.AudioAttributes.USAGE_VOICE_COMMUNICATION;

import android.app.Instrumentation;
import android.app.UiAutomation;
import android.car.Car;
import android.car.media.CarAudioManager;
import android.content.Context;
import android.platform.helpers.ScrollUtility.ScrollActions;
import android.platform.helpers.ScrollUtility.ScrollDirection;

import androidx.test.InstrumentationRegistry;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.BySelector;
import androidx.test.uiautomator.UiObject2;

import java.util.List;

/** Sound setting app helper file */
public class SettingsSoundsHelperImpl extends AbstractStandardAppHelper
        implements IAutoSoundsSettingHelper {

    private ScrollUtility mScrollUtility;
    private ScrollActions mScrollAction;
    private BySelector mBackwardButtonSelector;
    private BySelector mForwardButtonSelector;
    private BySelector mScrollableElementSelector;
    private ScrollDirection mScrollDirection;
    private static final int VOLUME_FLAGS = 0;
    private static final int USAGE_INVALID = -1;
    private static final int MINIMUM_NUMBER_OF_CHILDREN = 2;

    private Context mContext;
    private CarAudioManager mCarAudioManager;
    private final UiAutomation mUiAutomation =
            InstrumentationRegistry.getInstrumentation().getUiAutomation();

    public SettingsSoundsHelperImpl(Instrumentation instr) {
        super(instr);
        mContext = InstrumentationRegistry.getContext();
        Car car = Car.createCar(mContext);
        mUiAutomation.adoptShellPermissionIdentity(
                "android.car.permission.CAR_CONTROL_AUDIO_VOLUME",
                "android.car.permission.CAR_CONTROL_AUDIO_SETTINGS");
        mCarAudioManager = (CarAudioManager) car.getCarManager(Car.AUDIO_SERVICE);
        mScrollUtility = ScrollUtility.getInstance(getSpectatioUiUtil());
        mScrollAction =
                ScrollActions.valueOf(
                        getActionFromConfig(
                                AutomotiveConfigConstants.SOUND_SETTINGS_LIST_SCROLL_ACTION));
        mBackwardButtonSelector =
                getUiElementFromConfig(
                        AutomotiveConfigConstants.SOUND_SETTINGS_SCROLL_BACKWARD_BUTTON);
        mForwardButtonSelector =
                getUiElementFromConfig(
                        AutomotiveConfigConstants.SOUND_SETTINGS_SCROLL_FORWARD_BUTTON);
        mScrollableElementSelector =
                getUiElementFromConfig(AutomotiveConfigConstants.SOUND_SETTINGS_SCROLL_ELEMENT);
        mScrollDirection =
                ScrollDirection.valueOf(
                        getActionFromConfig(
                                AutomotiveConfigConstants.SOUND_SETTINGS_LIST_SCROLL_DIRECTION));
        mScrollUtility.setScrollValues(
                Integer.valueOf(
                        getActionFromConfig(
                                AutomotiveConfigConstants.SOUND_SETTINGS_SCROLL_MARGIN)),
                Integer.valueOf(
                        getActionFromConfig(
                                AutomotiveConfigConstants.SOUND_SETTINGS_SCROLL_WAIT_TIME)));
    }

    /** {@inheritDoc} */
    @Override
    public String getPackage() {
        return getPackageFromConfig(AutomotiveConfigConstants.SETTINGS_PACKAGE);
    }

    /** {@inheritDoc} */
    @Override
    public String getLauncherName() {
        throw new UnsupportedOperationException("Operation not supported.");
    }

    /** {@inheritDoc} */
    @Override
    public void dismissInitialDialogs() {
        // Nothing to dismiss
    }

    /** {@inheritDoc} */
    @Override
    public void setVolume(VolumeType volumeType, int index) {
        int audioAttribute = USAGE_INVALID;
        switch (volumeType) {
            case MEDIA:
                audioAttribute = USAGE_MEDIA;
                break;
            case ALARM:
                audioAttribute = USAGE_ALARM;
                break;
            case NAVIGATION:
                audioAttribute = USAGE_ASSISTANCE_NAVIGATION_GUIDANCE;
                break;
            case INCALL:
                audioAttribute = USAGE_VOICE_COMMUNICATION;
                break;
        }
        int volumeGroupId = mCarAudioManager.getVolumeGroupIdForUsage(audioAttribute);
        mCarAudioManager.setGroupVolume(volumeGroupId, index, VOLUME_FLAGS);
        getSpectatioUiUtil().wait1Second();
    }

    /** {@inheritDoc} */
    @Override
    public int getVolume(VolumeType volumeType) {
        int audioAttribute = USAGE_INVALID;
        switch (volumeType) {
            case MEDIA:
                audioAttribute = USAGE_MEDIA;
                break;
            case ALARM:
                audioAttribute = USAGE_ALARM;
                break;
            case NAVIGATION:
                audioAttribute = USAGE_ASSISTANCE_NAVIGATION_GUIDANCE;
                break;
            case INCALL:
                audioAttribute = USAGE_VOICE_COMMUNICATION;
                break;
        }
        int volumeGroupId = mCarAudioManager.getVolumeGroupIdForUsage(audioAttribute);
        int volume = mCarAudioManager.getGroupVolume(volumeGroupId);
        return volume;
    }

    /** {@inheritDoc} */
    @Override
    public void setSound(SoundType soundType, String sound) {
        String type = "";
        switch (soundType) {
            case ALARM:
                type = "Default alarm sound";
                break;
            case NOTIFICATION:
                type = "Default notification sound";
                break;
            case RINGTONE:
                type = "Phone ringtone";
                break;
        }
        BySelector typeSelector = By.text(type);
        UiObject2 object =
                mScrollUtility.scrollAndFindUiObject(
                        mScrollAction,
                        mScrollDirection,
                        mForwardButtonSelector,
                        mBackwardButtonSelector,
                        mScrollableElementSelector,
                        typeSelector,
                        String.format("Scroll on sound to find %s", type));
        String currentSound = getSound(soundType);
        getSpectatioUiUtil().validateUiObject(object, String.format("sound type %s", type));
        getSpectatioUiUtil().clickAndWait(object);
        getSpectatioUiUtil().wait1Second();
        BySelector soundSelector = By.text(sound);
        UiObject2 soundObject =
                mScrollUtility.scrollAndFindUiObject(
                        mScrollAction,
                        mScrollDirection,
                        mForwardButtonSelector,
                        mBackwardButtonSelector,
                        mScrollableElementSelector,
                        soundSelector,
                        String.format("Scroll on sound list to find %s", sound));
        getSpectatioUiUtil().validateUiObject(soundObject, String.format("sound %s", sound));
        getSpectatioUiUtil().clickAndWait(soundObject);
        UiObject2 saveButton =
                getSpectatioUiUtil()
                        .findUiObject(
                                getUiElementFromConfig(
                                        AutomotiveConfigConstants.SOUND_SETTINGS_SAVE_BUTTON));
        getSpectatioUiUtil().validateUiObject(saveButton, "save button");
        getSpectatioUiUtil().clickAndWait(saveButton);
    }

    /** {@inheritDoc} */
    @Override
    public String getSound(SoundType soundType) {
        String type = "";
        switch (soundType) {
            case ALARM:
                type = "Default alarm sound";
                break;
            case NOTIFICATION:
                type = "Default notification sound";
                break;
            case RINGTONE:
                type = "Phone ringtone";
                break;
        }
        getSpectatioUiUtil().wait1Second();
        BySelector typeSelector = By.text(type);
        UiObject2 object =
                mScrollUtility.scrollAndFindUiObject(
                        mScrollAction,
                        mScrollDirection,
                        mForwardButtonSelector,
                        mBackwardButtonSelector,
                        mScrollableElementSelector,
                        typeSelector,
                        String.format("Scroll on sound to find %s", type));
        getSpectatioUiUtil().validateUiObject(object, String.format("sound type %s", type));
        List<UiObject2> list = object.getParent().getChildren();
        if (list.size() < 2) {
            mScrollUtility.scrollForward(
                    mScrollAction,
                    mScrollDirection,
                    mForwardButtonSelector,
                    mScrollableElementSelector,
                    String.format("Scroll on sound to find %s", type));
            getSpectatioUiUtil().validateUiObject(object, String.format("sound type %s", type));
            list = object.getParent().getChildren();
        }
        UiObject2 summary = list.get(1);
        return summary.getText();
    }

}
