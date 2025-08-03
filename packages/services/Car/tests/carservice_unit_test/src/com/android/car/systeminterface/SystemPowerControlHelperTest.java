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

package com.android.car.systeminterface;

import static com.android.car.systeminterface.SystemPowerControlHelper.SUSPEND_RESULT_SUCCESS;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import static org.mockito.Mockito.when;

import android.car.test.mocks.AbstractExtendedMockitoTestCase;
import android.car.test.util.TemporaryFile;

import libcore.io.IoUtils;

import org.junit.Test;

import java.io.FileWriter;
import java.util.function.BooleanSupplier;
import java.util.function.IntSupplier;

/**
 * Unit tests for {@link SystemPowerControlHelper}
 *
 * <p> Run:
 * {@code atest SystemPowerControlHelper}
 */
public final class SystemPowerControlHelperTest extends AbstractExtendedMockitoTestCase {
    private static final String POWER_STATE_FILE = "power_state";

    public SystemPowerControlHelperTest() {
        super(SystemPowerControlHelper.TAG);
    }

    @Override
    protected void onSessionBuilder(CustomMockitoSessionBuilder session) {
        session.spyStatic(SystemPowerControlHelper.class);
    }

    @Test
    public void testForceDeepSleep() throws Exception {
        testHelperMockedFileWrite(SystemPowerControlHelper::forceDeepSleep,
                SystemPowerControlHelper.SUSPEND_TYPE_MEM);
    }

    @Test
    public void testForceHibernate() throws Exception {
        testHelperMockedFileWrite(SystemPowerControlHelper::forceHibernate,
                SystemPowerControlHelper.SUSPEND_TYPE_DISK);
    }

    private void testHelperMockedFileWrite(IntSupplier consumer, String target) throws Exception {
        assertSpied(SystemPowerControlHelper.class);
        try (TemporaryFile powerStateControlFile = new TemporaryFile(POWER_STATE_FILE)) {
            when(SystemPowerControlHelper.getSysFsPowerControlFile())
                    .thenReturn(powerStateControlFile.getFile().getAbsolutePath());

            assertThat(consumer.getAsInt()).isEqualTo(SUSPEND_RESULT_SUCCESS);
            assertThat(IoUtils.readFileAsString(powerStateControlFile.getFile().getAbsolutePath()))
                    .isEqualTo(target);
        }
    }

    @Test
    public void testIsSystemSupportingDeepSleep() throws Exception {
        testHelperMockedFileRead(SystemPowerControlHelper::isSystemSupportingDeepSleep,
                "freeze mem standby disk", true);

        testHelperMockedFileRead(SystemPowerControlHelper::isSystemSupportingDeepSleep,
                "freeze standby disk mem\n", true);

        testHelperMockedFileRead(SystemPowerControlHelper::isSystemSupportingDeepSleep,
                "freeze standby disk mem ", true);

        testHelperMockedFileRead(SystemPowerControlHelper::isSystemSupportingDeepSleep,
                SystemPowerControlHelper.SUSPEND_TYPE_MEM, true);

        testHelperMockedFileRead(SystemPowerControlHelper::isSystemSupportingDeepSleep,
                "", false);

        testHelperMockedFileRead(SystemPowerControlHelper::isSystemSupportingDeepSleep,
                SystemPowerControlHelper.SUSPEND_TYPE_DISK, false);
    }

    @Test
    public void testIsSystemSupportingHibernation() throws Exception {
        testHelperMockedFileRead(SystemPowerControlHelper::isSystemSupportingHibernation,
                "freeze mem standby disk", true);

        testHelperMockedFileRead(SystemPowerControlHelper::isSystemSupportingHibernation,
                "freeze mem standby disk\n", true);

        testHelperMockedFileRead(SystemPowerControlHelper::isSystemSupportingHibernation,
                "freeze mem standby disk ", true);

        testHelperMockedFileRead(SystemPowerControlHelper::isSystemSupportingHibernation,
                SystemPowerControlHelper.SUSPEND_TYPE_DISK, true);

        testHelperMockedFileRead(SystemPowerControlHelper::isSystemSupportingHibernation,
                "", false);

        testHelperMockedFileRead(SystemPowerControlHelper::isSystemSupportingHibernation,
                SystemPowerControlHelper.SUSPEND_TYPE_MEM, false);
    }

    @Test
    public void testSystemSupportsSuspend_NoControlFile() throws Exception {
        String fileName;
        try (TemporaryFile temporaryFile = new TemporaryFile(POWER_STATE_FILE)) {
            fileName = temporaryFile.getFile().getAbsolutePath();
        }

        when(SystemPowerControlHelper.getSysFsPowerControlFile()).thenReturn(fileName);
        assertThat(SystemPowerControlHelper.isSystemSupportingDeepSleep()).isFalse();
        assertThat(SystemPowerControlHelper.isSystemSupportingHibernation()).isFalse();

    }

    private void testHelperMockedFileRead(BooleanSupplier consumer, String fileContents,
            boolean result) throws Exception {
        assertSpied(SystemPowerControlHelper.class);

        try (TemporaryFile powerStateControlFile = new TemporaryFile(POWER_STATE_FILE)) {
            when(SystemPowerControlHelper.getSysFsPowerControlFile())
                    .thenReturn(powerStateControlFile.getFile().getAbsolutePath());

            FileWriter fileWriter = powerStateControlFile.newFileWriter();
            fileWriter.write(fileContents);
            fileWriter.flush();

            assertWithMessage("Check failed, fileContents = %s", fileContents).that(
                    consumer.getAsBoolean()).isEqualTo(result);
        }
    }

}
