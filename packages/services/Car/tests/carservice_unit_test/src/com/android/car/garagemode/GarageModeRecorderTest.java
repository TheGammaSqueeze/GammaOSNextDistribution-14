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

package com.android.car.garagemode;

import static com.android.car.garagemode.GarageModeRecorder.GARAGE_MODE_RECORDER_IS_SACTIVE;
import static com.android.car.garagemode.GarageModeRecorder.NOT;
import static com.android.car.garagemode.GarageModeRecorder.SESSION_DURATION;
import static com.android.car.garagemode.GarageModeRecorder.SESSION_FINISH_TIME;
import static com.android.car.garagemode.GarageModeRecorder.SESSION_START_TIME;
import static com.android.car.garagemode.GarageModeRecorder.SESSION_WAS_CANCELLED;
import static com.android.car.garagemode.GarageModeRecorder.TIME_UNIT_MS;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.when;

import android.car.builtin.os.BuildHelper;
import android.car.builtin.util.Slogf;
import android.car.test.mocks.AbstractExtendedMockitoTestCase;

import com.android.car.CarLocalServices;
import com.android.car.internal.util.IndentingPrintWriter;
import com.android.car.systeminterface.SystemInterface;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.file.Files;
import java.sql.Date;
import java.text.SimpleDateFormat;
import java.time.Clock;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

public final class GarageModeRecorderTest extends AbstractExtendedMockitoTestCase {

    private static final String TAG = "GarageMode";
    @Mock
    private SystemInterface mSystemInterface;
    @Mock
    private Clock mClock;
    private File mTempTestDir;
    private GarageModeRecorder mGarageModeRecorder;
    private final SimpleDateFormat mDateFormat = new SimpleDateFormat(
            GarageModeRecorder.DATE_FORMAT);
    private long mSessionStartTime;
    private long mSessionFinishTime;

    public GarageModeRecorderTest() {
        super(NO_LOG_TAGS);
    }

    @Override
    protected void onSessionBuilder(CustomMockitoSessionBuilder builder) {
        builder
            .spyStatic(BuildHelper.class)
            .spyStatic(SystemInterface.class)
            .spyStatic(CarLocalServices.class);
    }

    @Before
    public void setup() throws IOException {
        mTempTestDir = Files.createTempDirectory("garagemode_test").toFile();
        Slogf.v(TAG, "Using temp dir: %s", mTempTestDir.getAbsolutePath());
        mSessionStartTime = 1620000000000L;
        mSessionFinishTime = mSessionStartTime + TimeUnit.SECONDS.toMillis(20L);

        doReturn(mSystemInterface).when(() -> CarLocalServices.getService(SystemInterface.class));
        when(mSystemInterface.getSystemCarDir()).thenReturn(mTempTestDir);
        when(mClock.millis()).thenReturn(mSessionStartTime, mSessionFinishTime);

        mGarageModeRecorder = new GarageModeRecorder(mClock);
    }

    @Test
    public void testIsRecorderEnabled() {
        setRecorderEnabled(true);
        assertThat(mGarageModeRecorder.isRecorderEnabled()).isTrue();

        setRecorderEnabled(false);
        assertThat(mGarageModeRecorder.isRecorderEnabled()).isFalse();
    }

    @Test
    public void testRecorderStatusDump() {
        setRecorderEnabled(true);
        verifyDumpsys(getRecorderActiveString(true));

        setRecorderEnabled(false);
        verifyDumpsys(getRecorderActiveString(false));
    }

    private void setRecorderEnabled(boolean status) {
        doReturn(!status).when(BuildHelper::isUserBuild);
    }

    @Test
    public void testStartSession() {
        mGarageModeRecorder.startSession();

        verifyDumpsys(getRecorderActiveString(true), getSessionStartString());
    }

    @Test
    public void testStartSessionCalledTwice() {
        mGarageModeRecorder.startSession();
        mGarageModeRecorder.startSession();

        verifyDumpsys(getRecorderActiveString(true), getSessionStartString());
    }


    @Test
    public void testFinishSession() {
        mGarageModeRecorder.startSession();
        mGarageModeRecorder.finishSession();

        verifyDumpsys(getRecorderActiveString(true), getSessionStartString(),
                getSessionFinishString(), getSessionDurationString());
    }

    @Test
    public void testCancelSession() {
        mGarageModeRecorder.startSession();
        mGarageModeRecorder.cancelSession();

        verifyDumpsys(getRecorderActiveString(true), getSessionStartString(),
                getSessionCancelString());
    }

    private String getRecorderActiveString(boolean isActive) {
        return String.format(GARAGE_MODE_RECORDER_IS_SACTIVE.replace("\n", ""),
                isActive ? "" : NOT);
    }

    private String getSessionCancelString() {
        return SESSION_WAS_CANCELLED + mDateFormat.format(new Date(mSessionFinishTime));
    }

    private String getSessionStartString() {
        return SESSION_START_TIME + mDateFormat.format(new Date(mSessionStartTime));
    }

    private String getSessionFinishString() {
        return SESSION_FINISH_TIME + mDateFormat.format(new Date(mSessionFinishTime));
    }

    private String getSessionDurationString() {
        return SESSION_DURATION + (mSessionFinishTime - mSessionStartTime) + TIME_UNIT_MS;
    }


    private void verifyDumpsys(String...expectedStrings) {
        StringWriter stringWriter = new StringWriter();
        mGarageModeRecorder.dump(new IndentingPrintWriter(stringWriter));

        List<String> result = Arrays.asList(stringWriter.getBuffer().toString().split("\n"));
        assertThat(result).containsExactlyElementsIn(expectedStrings);
    }
}
