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

package android.cts.statsdatom.gamemanager;

import static com.google.common.truth.Truth.assertThat;

import android.app.GameMode;
import android.cts.statsdatom.lib.AtomTestUtils;
import android.cts.statsdatom.lib.ConfigUtils;
import android.cts.statsdatom.lib.DeviceUtils;
import android.cts.statsdatom.lib.ReportUtils;

import com.android.os.AtomsProto;
import com.android.os.AtomsProto.GameStateChanged.State;
import com.android.os.StatsLog;
import com.android.os.agif.GameModeChanged;
import com.android.os.agif.GameModeConfiguration;
import com.android.os.agif.GameModeConfigurationChanged;
import com.android.os.agif.GameModeInfo;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.testtype.DeviceTestCase;
import com.android.tradefed.testtype.IBuildReceiver;
import com.android.tradefed.util.RunUtil;

import java.util.List;
import java.util.Set;

/**
 * Test for Game Manager stats.
 *
 *  <p>Build/Install/Run:
 *  atest CtsStatsdAtomHostTestCases:GameManagerStatsTests
 */
public class GameManagerStatsTests extends DeviceTestCase implements IBuildReceiver {
    private IBuildInfo mCtsBuild;
    private int mStatsdAtomTestUid;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        assertThat(mCtsBuild).isNotNull();
        ConfigUtils.removeConfig(getDevice());
        ReportUtils.clearReports(getDevice());
        DeviceUtils.installStatsdTestApp(getDevice(), mCtsBuild);
        RunUtil.getDefault().sleep(AtomTestUtils.WAIT_TIME_LONG);
        mStatsdAtomTestUid = DeviceUtils.getAppUid(getDevice(), DeviceUtils.STATSD_ATOM_TEST_PKG);
    }

    @Override
    protected void tearDown() throws Exception {
        ConfigUtils.removeConfig(getDevice());
        ReportUtils.clearReports(getDevice());
        DeviceUtils.uninstallStatsdTestApp(getDevice());
        super.tearDown();
    }

    @Override
    public void setBuild(IBuildInfo buildInfo) {
        mCtsBuild = buildInfo;
    }

    public void testGameStateStatsd() throws Exception {
        ConfigUtils.uploadConfigForPushedAtom(getDevice(), DeviceUtils.STATSD_ATOM_TEST_PKG,
                AtomsProto.Atom.GAME_STATE_CHANGED_FIELD_NUMBER);
        DeviceUtils.runDeviceTestsOnStatsdApp(getDevice(), ".AtomTests", "testGameState");
        RunUtil.getDefault().sleep(AtomTestUtils.WAIT_TIME_LONG);

        List<StatsLog.EventMetricData> data = ReportUtils.getEventMetricDataList(getDevice());
        assertThat(data.size()).isAtLeast(1);
        AtomsProto.GameStateChanged a0 = data.get(0).getAtom().getGameStateChanged();
        assertThat(a0.getUid()).isGreaterThan(10000);  // Not a system service UID.
        assertThat(a0.getPackageName()).isEqualTo(DeviceUtils.STATSD_ATOM_TEST_PKG);
        assertThat(a0.getBoostEnabled()).isEqualTo(false);  // Test app does not qualify.
        assertThat(a0.getIsLoading()).isEqualTo(true);
        assertThat(a0.getState()).isEqualTo(State.MODE_CONTENT);
        assertThat(a0.getLabel()).isEqualTo(1);
        assertThat(a0.getQuality()).isEqualTo(2);
    }

    public void testGameModeChangedIsPushed() throws Exception {
        ConfigUtils.uploadConfigForPushedAtom(getDevice(), DeviceUtils.STATSD_ATOM_TEST_PKG,
                AtomsProto.Atom.GAME_MODE_CHANGED_FIELD_NUMBER);
        DeviceUtils.runDeviceTestsOnStatsdApp(getDevice(), ".AtomTests", "testSetGameMode");
        RunUtil.getDefault().sleep(AtomTestUtils.WAIT_TIME_LONG);
        List<StatsLog.EventMetricData> data = ReportUtils.getEventMetricDataList(getDevice());

        assertThat(data.size()).isAtLeast(2);
        GameModeChanged a0 = data.get(0).getAtom().getGameModeChanged();
        assertThat(a0.getCallerUid()).isEqualTo(mStatsdAtomTestUid);
        assertThat(a0.getGameUid()).isEqualTo(mStatsdAtomTestUid);
        assertThat(a0.getGameModeTo()).isEqualTo(GameMode.GAME_MODE_PERFORMANCE);

        GameModeChanged a1 = data.get(1).getAtom().getGameModeChanged();
        assertThat(a1.getCallerUid()).isEqualTo(mStatsdAtomTestUid);
        assertThat(a1.getGameUid()).isEqualTo(mStatsdAtomTestUid);
        assertThat(a1.getGameModeTo()).isEqualTo(GameMode.GAME_MODE_BATTERY);
    }

    public void testGameModeConfigurationChangedIsPushed() throws Exception {
        ConfigUtils.uploadConfigForPushedAtom(getDevice(), DeviceUtils.STATSD_ATOM_TEST_PKG,
                AtomsProto.Atom.GAME_MODE_CONFIGURATION_CHANGED_FIELD_NUMBER);
        DeviceUtils.runDeviceTestsOnStatsdApp(getDevice(), ".AtomTests",
                "testUpdateCustomGameModeConfiguration");
        RunUtil.getDefault().sleep(AtomTestUtils.WAIT_TIME_LONG);
        List<StatsLog.EventMetricData> data = ReportUtils.getEventMetricDataList(getDevice());

        assertThat(data.size()).isAtLeast(2);
        GameModeConfigurationChanged a0 = data.get(
                0).getAtom().getGameModeConfigurationChanged();
        assertThat(a0.getGameMode()).isEqualTo(GameMode.GAME_MODE_CUSTOM);
        assertThat(a0.getCallerUid()).isEqualTo(mStatsdAtomTestUid);
        assertThat(a0.getGameUid()).isEqualTo(mStatsdAtomTestUid);
        assertThat(a0.getScalingFactorTo()).isEqualTo(0.5f);
        assertThat(a0.getFpsOverrideTo()).isEqualTo(30);

        GameModeConfigurationChanged a1 = data.get(
                1).getAtom().getGameModeConfigurationChanged();
        assertThat(a1.getGameMode()).isEqualTo(GameMode.GAME_MODE_CUSTOM);
        assertThat(a1.getCallerUid()).isEqualTo(mStatsdAtomTestUid);
        assertThat(a1.getGameUid()).isEqualTo(mStatsdAtomTestUid);
        assertThat(a1.getScalingFactorFrom()).isEqualTo(0.5f);
        assertThat(a1.getFpsOverrideFrom()).isEqualTo(30);
        assertThat(a1.getScalingFactorTo()).isEqualTo(0.9f);
        assertThat(a1.getFpsOverrideTo()).isEqualTo(60);
    }

    public void testGameModeInfoIsPulled() throws Exception {
        ConfigUtils.uploadConfigForPulledAtom(getDevice(), DeviceUtils.STATSD_ATOM_TEST_PKG,
                AtomsProto.Atom.GAME_MODE_INFO_FIELD_NUMBER);

        DeviceUtils.putDeviceConfigFeature(getDevice(), "game_overlay",
                DeviceUtils.STATSD_ATOM_TEST_PKG,
                " mode=2,downscaleFactor=1.0,fps=90:mode=3,downscaleFactor=0.1,fps=30");
        RunUtil.getDefault().sleep(2000);
        AtomTestUtils.sendAppBreadcrumbReportedAtom(getDevice());
        RunUtil.getDefault().sleep(AtomTestUtils.WAIT_TIME_LONG);

        List<AtomsProto.Atom> data = ReportUtils.getGaugeMetricAtoms(getDevice());
        assertThat(data.size()).isAtLeast(1);
        GameModeInfo gameModeInfo = data.get(0).getGameModeInfo();
        assertThat(gameModeInfo.getGameUid()).isEqualTo(mStatsdAtomTestUid);
        Set<GameMode> reportedAvailableModes = Set.copyOf(gameModeInfo.getAvailableGameModesList());
        Set<GameMode> expectedAvailableGameModes = Set.of(GameMode.GAME_MODE_STANDARD,
                GameMode.GAME_MODE_CUSTOM, GameMode.GAME_MODE_PERFORMANCE,
                GameMode.GAME_MODE_BATTERY
        );
        assertThat(reportedAvailableModes).isEqualTo(expectedAvailableGameModes);


        Set<GameMode> reportedOverriddenModes = Set.copyOf(
                gameModeInfo.getOverriddenGameModesList());
        Set<GameMode> expectedOverriddenGameModes = Set.of(GameMode.GAME_MODE_BATTERY);
        assertThat(reportedOverriddenModes).isEqualTo(expectedOverriddenGameModes);
    }

    public void testGameModeConfigurationIsPulled() throws Exception {
        ConfigUtils.uploadConfigForPulledAtom(getDevice(), DeviceUtils.STATSD_ATOM_TEST_PKG,
                AtomsProto.Atom.GAME_MODE_CONFIGURATION_FIELD_NUMBER);

        DeviceUtils.putDeviceConfigFeature(getDevice(), "game_overlay",
                DeviceUtils.STATSD_ATOM_TEST_PKG,
                "mode=4:mode=2,downscaleFactor=0.7,fps=90:mode=3,downscaleFactor=0.1,fps=30");
        RunUtil.getDefault().sleep(2000);
        AtomTestUtils.sendAppBreadcrumbReportedAtom(getDevice());
        RunUtil.getDefault().sleep(AtomTestUtils.WAIT_TIME_LONG);

        List<AtomsProto.Atom> data = ReportUtils.getGaugeMetricAtoms(getDevice());
        assertThat(data.size()).isAtLeast(2);
        GameModeConfiguration config1 = data.get(0).getGameModeConfiguration();
        GameModeConfiguration config2 = data.get(1).getGameModeConfiguration();
        GameModeConfiguration performanceConfig =
                config1.getGameMode() == GameMode.GAME_MODE_PERFORMANCE ? config1 : config2;
        GameModeConfiguration customConfig =
                performanceConfig == config1 ? config2 : config1;
        assertThat(customConfig.getGameMode()).isEqualTo(GameMode.GAME_MODE_CUSTOM);
        assertThat(customConfig.getGameUid()).isEqualTo(mStatsdAtomTestUid);

        assertThat(performanceConfig.getScalingFactor()).isEqualTo(0.7f);
        assertThat(performanceConfig.getFpsOverride()).isEqualTo(90);
        assertThat(performanceConfig.getGameUid()).isEqualTo(
                mStatsdAtomTestUid);
    }
}
