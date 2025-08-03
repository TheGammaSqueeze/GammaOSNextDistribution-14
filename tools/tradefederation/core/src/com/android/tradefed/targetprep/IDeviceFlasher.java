/*
 * Copyright (C) 2010 The Android Open Source Project
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

package com.android.tradefed.targetprep;

import com.android.tradefed.build.IDeviceBuildInfo;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.util.CommandStatus;

import java.util.Collection;
import java.util.Collections;
import java.util.Set;

/**
 * Flashes a device image on a device.
 */
public interface IDeviceFlasher {

    /**
     * Enum of options for handling the userdata image
     */
    public enum UserDataFlashOption {
        /** flash the given userdata image on device */
        FLASH,
        /** flash the userdata image included in device image zip */
        FLASH_IMG_ZIP,
        /** wipe the device's userdata partition using fastboot erase, if supported by device */
        WIPE,
        /** wipe the device's userdata partition using fastboot erase, even if it's unadvised */
        FORCE_WIPE,
        /** delete content from the device's /data partition using adb shell rm */
        WIPE_RM,
        /** push the contents of the tests zip file onto the device's userdata partition */
        TESTS_ZIP,
        /** leave the userdata partition as is */
        RETAIN;
    }

    /**
     * Override options for a device. Used to override default option values if the defaults are not
     * supported by a particular device.
     *
     * @param device
     */
    public void overrideDeviceOptions(ITestDevice device);

    /**
     * Sets the mechanism by which the flasher can retrieve resource files for flashing.
     *
     * @param retriever the {@link IFlashingResourcesRetriever} to use
     */
    public void setFlashingResourcesRetriever(IFlashingResourcesRetriever retriever);

    /**
     * Toggles whether the user data image should be flashed, wiped, or retained
     *
     * @param flashOption
     */
    public void setUserDataFlashOption(UserDataFlashOption flashOption);

    /**
     * Sets the list of paths under {@code /data} to avoid clearing when using
     * {@link ITestsZipInstaller}
     * <p />
     * Note that the granularity of the skip list is direct children of {@code /data}.
     */
    public void setDataWipeSkipList(Collection<String> dataWipeSkipList);

    /**
     * Gets whether the user data image should be flashed, wiped, or retained
     *
     * @return Whether the user data image should be flashed, wiped, or retained
     */
    public UserDataFlashOption getUserDataFlashOption();

    /**
     * Set the timeout for wiping the data.
     */
    public void setWipeTimeout(long timeout);

    /**
     * Sets if system should always be flashed even if running current build
     * @param forceSystemFlash
     */
    public void setForceSystemFlash(boolean forceSystemFlash);

    /**
     * All setup operations & checks that must occur before actual flashing critical section. None
     * of those operations will be included in the concurrency-controlled critical section.
     *
     * @param device the {@link ITestDevice} to flash
     * @param deviceBuild the {@link IDeviceBuildInfo} to flash
     * @throws TargetSetupError if any setup fails
     * @throws DeviceNotAvailableException if device becomes unresponsive
     */
    public default void preFlashOperations(ITestDevice device, IDeviceBuildInfo deviceBuild)
            throws TargetSetupError, DeviceNotAvailableException {
        // Empty by default
    }

    /**
     * Flashes build on device.
     * <p/>
     * Returns immediately after flashing is complete. Callers should wait for device to be
     * online and available before proceeding with testing.
     *
     * @param device the {@link ITestDevice} to flash
     * @param deviceBuild the {@link IDeviceBuildInfo} to flash
     *
     * @throws TargetSetupError if failed to flash build
     * @throws DeviceNotAvailableException if device becomes unresponsive
     */
    public void flash(ITestDevice device, IDeviceBuildInfo deviceBuild) throws TargetSetupError,
            DeviceNotAvailableException;

    /**
     * All clean up operations & checks that must occur after actual flashing critical section. None
     * of those operations will be included in the concurrency-controlled critical section.
     *
     * @param device the {@link ITestDevice} to flash
     * @param deviceBuild the {@link IDeviceBuildInfo} to flash
     * @throws TargetSetupError if any setup fails
     * @throws DeviceNotAvailableException if device becomes unresponsive
     */
    public default void postFlashOperations(ITestDevice device, IDeviceBuildInfo deviceBuild)
            throws TargetSetupError, DeviceNotAvailableException {
        // Empty by default
    }

    /**
     * Retrieve the command execution status for flashing primary system partitions.
     * <p>
     * Note that if system partitions are not flashed (system already has the build to be flashed)
     * the command status may be <code>null</code>
     */
    public CommandStatus getSystemFlashingStatus();

    /**
     * Sets if an additional ramdisk should be flashed after updating device via image zip
     *
     * @param shouldFlashRamdisk
     */
    public default void setShouldFlashRamdisk(boolean shouldFlashRamdisk) {
        // Ignore
    }

    /**
     * Sets ramdisk partition
     *
     * @param ramdiskPartition
     */
    public default void setRamdiskPartition(String ramdiskPartition) {
        // Ignore
    }

    /**
     * Checks if the flasher is set to have an additional ramdisk should be flashed after updating
     * device via image zip
     */
    public default boolean shouldFlashRamdisk() {
        return false;
    }

    /**
     * Whether the device that implements the flasher can be flashed in fastbootd mode.
     */
    public default boolean supportsFlashingInFastbootD() {
        return false;
    }

    /**
     * Get filters for additional build artifacts.
     *
     * <p>Some flashers need certain build artifacts which are not among the default ones used. Such
     * flashers should override this method, so every user of the flashing tools doesn't need to
     * know about these files and provide command line options.
     *
     * <p>Note that this method is invoked on a temporary instance of the IDeviceFlasher, so no
     * state beyond that provided in the default constructor should be relied upon.
     *
     * <p>Strings in the Set are interpreted as file patterns, and all files matching the pattern
     * will be retrieved.
     *
     * <p>By default, this method returns an empty Set.
     */
    public default Set<String> getAdditionalFileFilters() {
        return Collections.<String>emptySet();
    }
}
