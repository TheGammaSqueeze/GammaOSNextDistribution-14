/*
 * Copyright (C) 2018 The Android Open Source Project
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

import static android.car.CarOccupantZoneManager.DISPLAY_TYPE_MAIN;
import static android.car.drivingstate.CarDrivingStateEvent.DRIVING_STATE_IDLING;
import static android.car.drivingstate.CarDrivingStateEvent.DRIVING_STATE_MOVING;
import static android.car.drivingstate.CarDrivingStateEvent.DRIVING_STATE_PARKED;
import static android.car.drivingstate.CarDrivingStateEvent.DRIVING_STATE_UNKNOWN;
import static android.car.drivingstate.CarUxRestrictions.UX_RESTRICTIONS_BASELINE;
import static android.car.drivingstate.CarUxRestrictions.UX_RESTRICTIONS_FULLY_RESTRICTED;
import static android.car.drivingstate.CarUxRestrictionsConfiguration.Builder.SpeedRange.MAX_SPEED;
import static android.car.drivingstate.CarUxRestrictionsManager.UX_RESTRICTION_MODE_BASELINE;

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;

import static com.android.car.CarUxRestrictionsManagerService.CONFIG_FILENAME_PRODUCTION;
import static com.android.car.CarUxRestrictionsManagerService.CONFIG_FILENAME_STAGED;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import android.car.Car;
import android.car.VehiclePropertyIds;
import android.car.drivingstate.CarDrivingStateEvent;
import android.car.drivingstate.CarUxRestrictions;
import android.car.drivingstate.CarUxRestrictionsConfiguration;
import android.car.drivingstate.CarUxRestrictionsConfiguration.Builder;
import android.car.drivingstate.ICarDrivingStateChangeListener;
import android.car.hardware.CarPropertyValue;
import android.car.hardware.property.CarPropertyEvent;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.hardware.automotive.vehicle.VehicleProperty;
import android.os.RemoteException;
import android.os.SystemClock;
import android.util.JsonWriter;
import android.view.Display;

import androidx.test.filters.MediumTest;
import androidx.test.runner.AndroidJUnit4;

import com.android.car.internal.util.IntArray;
import com.android.car.systeminterface.SystemInterface;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.xmlpull.v1.XmlPullParserException;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;

@RunWith(AndroidJUnit4.class)
@MediumTest
public class CarUxRestrictionsManagerServiceTest {

    private static final String UX_RESTRICTION_MODE_PASSENGER = "passenger";

    private CarUxRestrictionsManagerService mService;

    @Rule
    public final MockitoRule rule = MockitoJUnit.rule();

    @Mock
    private CarDrivingStateService mMockDrivingStateService;
    @Mock
    private CarPropertyService mMockCarPropertyService;
    @Mock
    private CarOccupantZoneService mMockCarOccupantZoneService;
    @Mock
    private SystemInterface mMockSystemInterface;

    private Context mSpyContext;
    private SystemInterface mOriginalSystemInterface;
    private File mTempSystemCarDir;

    @Before
    public void setUp() throws Exception {
        // Spy context because service needs to access xml resource during init.
        mSpyContext = spy(getInstrumentation().getTargetContext());
        mOriginalSystemInterface = CarLocalServices.getService(SystemInterface.class);
        CarLocalServices.removeServiceForTest(SystemInterface.class);
        CarLocalServices.addService(SystemInterface.class, mMockSystemInterface);

        mTempSystemCarDir = Files.createTempDirectory("uxr_test").toFile();
        when(mMockSystemInterface.getSystemCarDir()).thenReturn(mTempSystemCarDir);

        setUpMockParkedState();

        when(mMockCarOccupantZoneService.getAllDisplayIdsForDriver(eq(DISPLAY_TYPE_MAIN)))
                .thenReturn(IntArray.wrap(new int[Display.DEFAULT_DISPLAY]));

        mService = new CarUxRestrictionsManagerService(mSpyContext,
                mMockDrivingStateService, mMockCarPropertyService, mMockCarOccupantZoneService);
    }

    @After
    public void tearDown() throws Exception {
        mService = null;
        CarLocalServices.removeServiceForTest(SystemInterface.class);
        CarLocalServices.addService(SystemInterface.class, mOriginalSystemInterface);
    }

    @Test
    public void testSaveConfig_WriteStagedFile() throws Exception {
        File staged = setupMockFile(CONFIG_FILENAME_STAGED, null);
        CarUxRestrictionsConfiguration config = createEmptyConfig();

        assertTrue(mService.saveUxRestrictionsConfigurationForNextBoot(Arrays.asList(config)));

        String expectedConfig = "{\"schema_version\":2,\"restrictions\":[{\"physical_port\":null,"
                + "\"occupant_zone_id\":null,\"display_type\":null,"
                + "\"max_content_depth\":-1,\"max_cumulative_content_items\":-1,"
                + "\"max_string_length\":-1,\"baseline\":{\"parked_restrictions\":[{\"req_opt"
                + "\":true,\"restrictions\":511}],\"idling_restrictions\":[{\"req_opt\":true,"
                + "\"restrictions\":511}],\"moving_restrictions\":[{\"req_opt\":true,"
                + "\"restrictions\":511}],\"unknown_restrictions\":[{\"req_opt\":true,"
                + "\"restrictions\":511}]}}]}";
        assertEquals(readFile(staged.toPath()), expectedConfig);
        // Verify prod config file was not created.
        assertFalse(new File(mTempSystemCarDir, CONFIG_FILENAME_PRODUCTION).exists());
    }

    @Test
    public void testSaveConfig_ReturnFalseOnException() throws Exception {
        CarUxRestrictionsConfiguration spyConfig = spy(createEmptyConfig());
        doThrow(new IOException()).when(spyConfig).writeJson(any(JsonWriter.class));

        assertFalse(mService.saveUxRestrictionsConfigurationForNextBoot(Arrays.asList(spyConfig)));
    }

    // TODO(b/241589812): Change assertions to go/truth expectatons.
    @Test
    public void testLoadConfig_UseDefaultConfigWhenNoSavedConfigFileNoXml() {
        // Prevent R.xml.car_ux_restrictions_map being returned.
        Resources spyResources = spy(mSpyContext.getResources());
        doReturn(spyResources).when(mSpyContext).getResources();
        doReturn(null).when(spyResources).getXml(anyInt());

        for (CarUxRestrictionsConfiguration config : mService.loadConfig()) {
            CarUxRestrictions movingRestrictions = config.getUxRestrictions(DRIVING_STATE_MOVING,
                    MAX_SPEED);
            assertTrue(movingRestrictions.isRequiresDistractionOptimization());
            assertEquals(movingRestrictions.getActiveRestrictions(),
                    UX_RESTRICTIONS_FULLY_RESTRICTED);

            CarUxRestrictions parkedRestrictions = config.getUxRestrictions(DRIVING_STATE_PARKED,
                    0f);
            assertFalse(parkedRestrictions.isRequiresDistractionOptimization());
            assertEquals(parkedRestrictions.getActiveRestrictions(),
                    UX_RESTRICTIONS_BASELINE);

            CarUxRestrictions idlingRestrictions = config.getUxRestrictions(DRIVING_STATE_IDLING,
                    0f);
            assertFalse(idlingRestrictions.isRequiresDistractionOptimization());
            assertEquals(idlingRestrictions.getActiveRestrictions(),
                    UX_RESTRICTIONS_BASELINE);
        }
    }

    @Test
    public void testLoadConfig_UseXml() throws IOException, XmlPullParserException {
        List<CarUxRestrictionsConfiguration> expected =
                CarUxRestrictionsConfigurationXmlParser.parse(
                        mSpyContext, R.xml.car_ux_restrictions_map);

        List<CarUxRestrictionsConfiguration> actual = mService.loadConfig();

        assertTrue(actual.equals(expected));
    }

    @Test
    public void testLoadConfig_UseProdConfig() throws IOException {
        CarUxRestrictionsConfiguration expected = createEmptyConfig();
        setupMockFile(CONFIG_FILENAME_PRODUCTION, List.of(expected));

        CarUxRestrictionsConfiguration actual = mService.loadConfig().get(0);

        assertEquals(actual, expected);
    }

    @Test
    public void testLoadConfig_SupportsLegacyV1() throws IOException {
        String v1LegacyJsonFormat = "[{\"physical_port\":1,\"max_content_depth\":2,"
                + "\"max_cumulative_content_items\":20,\"max_string_length\":21,"
                + "\"parked_restrictions\":[{\"req_opt\":false,\"restrictions\":0}],"
                + "\"idling_restrictions\":[{\"req_opt\":true,\"restrictions\":7}],"
                + "\"moving_restrictions\":[{\"req_opt\":true,\"restrictions\":8}],"
                + "\"unknown_restrictions\":[{\"req_opt\":true,\"restrictions\":511}],"
                + "\"passenger_parked_restrictions\":[{\"req_opt\":false,\"restrictions\":0}],"
                + "\"passenger_idling_restrictions\":[{\"req_opt\":true,\"restrictions\":56}],"
                + "\"passenger_moving_restrictions\":[{\"req_opt\":true,\"restrictions\":57}],"
                + "\"passenger_unknown_restrictions\":[{\"req_opt\":true,\"restrictions\":510}]}]";
        setupMockFileFromString(CONFIG_FILENAME_PRODUCTION, v1LegacyJsonFormat);

        CarUxRestrictionsConfiguration actual = mService.loadConfig().get(0);

        CarUxRestrictionsConfiguration expectedConfig = new Builder()
                .setPhysicalPort(1)
                .setMaxContentDepth(2)
                .setMaxCumulativeContentItems(20)
                .setMaxStringLength(21)
                .setUxRestrictions(DRIVING_STATE_PARKED,
                        new CarUxRestrictionsConfiguration.DrivingStateRestrictions()
                                .setDistractionOptimizationRequired(false)
                                .setRestrictions(UX_RESTRICTIONS_BASELINE)
                                .setMode(UX_RESTRICTION_MODE_BASELINE))
                .setUxRestrictions(DRIVING_STATE_IDLING,
                        new CarUxRestrictionsConfiguration.DrivingStateRestrictions()
                                .setDistractionOptimizationRequired(true)
                                .setRestrictions(7)
                                .setMode(UX_RESTRICTION_MODE_BASELINE))
                .setUxRestrictions(DRIVING_STATE_MOVING,
                        new CarUxRestrictionsConfiguration.DrivingStateRestrictions()
                                .setDistractionOptimizationRequired(true)
                                .setRestrictions(8)
                                .setMode(UX_RESTRICTION_MODE_BASELINE))
                .setUxRestrictions(DRIVING_STATE_UNKNOWN,
                        new CarUxRestrictionsConfiguration.DrivingStateRestrictions()
                                .setDistractionOptimizationRequired(true)
                                .setRestrictions(511)
                                .setMode(UX_RESTRICTION_MODE_BASELINE))
                .setUxRestrictions(DRIVING_STATE_PARKED,
                        new CarUxRestrictionsConfiguration.DrivingStateRestrictions()
                                .setDistractionOptimizationRequired(false)
                                .setRestrictions(UX_RESTRICTIONS_BASELINE)
                                .setMode(UX_RESTRICTION_MODE_PASSENGER))
                .setUxRestrictions(DRIVING_STATE_IDLING,
                        new CarUxRestrictionsConfiguration.DrivingStateRestrictions()
                                .setDistractionOptimizationRequired(true)
                                .setRestrictions(56)
                                .setMode(UX_RESTRICTION_MODE_PASSENGER))
                .setUxRestrictions(DRIVING_STATE_MOVING,
                        new CarUxRestrictionsConfiguration.DrivingStateRestrictions()
                                .setDistractionOptimizationRequired(true)
                                .setRestrictions(57)
                                .setMode(UX_RESTRICTION_MODE_PASSENGER))
                .setUxRestrictions(DRIVING_STATE_UNKNOWN,
                        new CarUxRestrictionsConfiguration.DrivingStateRestrictions()
                                .setDistractionOptimizationRequired(true)
                                .setRestrictions(510)
                                .setMode(UX_RESTRICTION_MODE_PASSENGER))
                .build();
        assertEquals(actual, expectedConfig);
    }

    @Test
    public void testLoadConfig_PromoteStagedFileWhenParked() throws Exception {
        CarUxRestrictionsConfiguration expected = createEmptyConfig();
        // Staged file contains actual config. Ignore prod since it should be overwritten by staged.
        File staged = setupMockFile(CONFIG_FILENAME_STAGED, List.of(expected));
        // Set up temp file for prod to avoid polluting other tests.
        setupMockFile(CONFIG_FILENAME_PRODUCTION, null);

        CarUxRestrictionsConfiguration actual = mService.loadConfig().get(0);

        // Staged file should be moved as production.
        assertFalse(staged.exists());
        assertEquals(actual, expected);
    }

    @Test
    public void testLoadConfig_NoPromoteStagedFileWhenMoving() throws Exception {
        CarUxRestrictionsConfiguration expected = createEmptyConfig();
        File staged = setupMockFile(CONFIG_FILENAME_STAGED, null);
        // Prod file contains actual config. Ignore staged since it should not be promoted.
        setupMockFile(CONFIG_FILENAME_PRODUCTION, List.of(expected));

        setUpMockDrivingStateWithFakeSpeed(30f);
        CarUxRestrictionsConfiguration actual = mService.loadConfig().get(0);

        // Staged file should be untouched.
        assertTrue(staged.exists());
        assertEquals(actual, expected);
    }

    @Test
    public void testLoadConfig_NoPromoteStagedFileWhenMovingBackwards() throws Exception {
        CarUxRestrictionsConfiguration expected = createEmptyConfig();
        File staged = setupMockFile(CONFIG_FILENAME_STAGED, null);
        // Prod file contains actual config. Ignore staged since it should not be promoted.
        setupMockFile(CONFIG_FILENAME_PRODUCTION, List.of(expected));

        setUpMockDrivingStateWithFakeSpeed(-30f);
        CarUxRestrictionsConfiguration actual = mService.loadConfig().get(0);

        // Staged file should be untouched.
        assertTrue(staged.exists());
        assertEquals(actual, expected);
    }

    @Test
    public void testValidateConfigs_SingleConfigNotSetPort() throws Exception {
        CarUxRestrictionsConfiguration config = createEmptyConfig();
        mService.validateConfigs(Arrays.asList(config));
    }

    @Test
    public void testValidateConfigs_SingleConfigWithPort() throws Exception {
        CarUxRestrictionsConfiguration config = createEmptyConfig(0);
        mService.validateConfigs(Arrays.asList(config));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testValidateConfigs_MultipleConfigsMustHavePort() throws Exception {
        mService.validateConfigs(Arrays.asList(createEmptyConfig(null), createEmptyConfig(null)));
    }

    @Test(expected = IllegalStateException.class)
    public void testValidateConfigs_MultipleConfigsMustHaveUniquePort() throws Exception {
        mService.validateConfigs(Arrays.asList(
                createEmptyConfig(0), createEmptyConfig(0)));
    }

    @Test
    public void testGetCurrentUxRestrictions_UnknownDisplayId_ReturnsFullRestrictions()
            throws Exception {
        mService.init();
        // there would never actually be a display id of 100,000 - this is just an example of an id
        // that doesn't exist on the device
        int unknownDisplayId = 100_000;
        CarUxRestrictions restrictions = mService.getCurrentUxRestrictions(unknownDisplayId);
        CarUxRestrictions expected = new CarUxRestrictions.Builder(
                /*reqOpt= */ true,
                CarUxRestrictions.UX_RESTRICTIONS_FULLY_RESTRICTED,
                SystemClock.elapsedRealtimeNanos()).build();
        assertTrue(restrictions.toString(), expected.isSameRestrictions(restrictions));
    }

    // This test only involves calling a few methods and should finish very quickly. If it doesn't
    // finish in 20s, we probably encountered a deadlock.
    @Test(timeout = 20000)
    public void testInitService_NoDeadlockWithCarDrivingStateService()
            throws Exception {

        CarDrivingStateService drivingStateService = new CarDrivingStateService(mSpyContext,
                mMockCarPropertyService);
        CarUxRestrictionsManagerService uxRestrictionsService = new CarUxRestrictionsManagerService(
                mSpyContext, drivingStateService, mMockCarPropertyService,
                mMockCarOccupantZoneService);

        CountDownLatch dispatchingStartedSignal = new CountDownLatch(1);
        CountDownLatch initCompleteSignal = new CountDownLatch(1);

        // A deadlock can exist when the dispatching of a listener is synchronized. For instance,
        // the CarUxRestrictionsManagerService#init() method registers a callback like this one. The
        // deadlock risk occurs if:
        // 1. CarUxRestrictionsManagerService has registered a listener with CarDrivingStateService
        // 2. A synchronized method of CarUxRestrictionsManagerService starts to run
        // 3. While the method from (2) is running, a property event occurs on a different thread
        //    that triggers a drive state event in CarDrivingStateService. If CarDrivingStateService
        //    handles the property event in a synchronized method, then CarDrivingStateService is
        //    locked. The listener from (1) will wait until the lock on
        //    CarUxRestrictionsManagerService is released.
        // 4. The synchronized method from (2) attempts to access CarDrivingStateService. For
        //    example, the implementation below attempts to read the restriction mode.
        //
        // In the above steps, both CarUxRestrictionsManagerService and CarDrivingStateService are
        // locked and waiting on each other, hence the deadlock.
        drivingStateService.registerDrivingStateChangeListener(
                new ICarDrivingStateChangeListener.Stub() {
                    @Override
                    public void onDrivingStateChanged(CarDrivingStateEvent event)
                            throws RemoteException {
                        // EVENT 2 [new thread]: this callback is called from within
                        // handlePropertyEvent(), which might (but shouldn't) lock
                        // CarDrivingStateService

                        // Notify that the dispatching process has started
                        dispatchingStartedSignal.countDown();

                        try {
                            // EVENT 3b [new thread]: Wait until init() has finished. If these
                            // threads don't have lock dependencies, there is no reason there
                            // would be an issue with waiting.
                            //
                            // In the real world, this wait could represent a long-running
                            // task, or hitting the below line that attempts to access the
                            // CarUxRestrictionsManagerService (which might be locked while init
                            // () is running).
                            //
                            // If there is a deadlock while waiting for init to complete, we will
                            // never progress past this line.
                            initCompleteSignal.await();
                        } catch (InterruptedException e) {
                            Assert.fail("onDrivingStateChanged thread interrupted");
                        }

                        // Attempt to access CarUxRestrictionsManagerService. If
                        // CarUxRestrictionsManagerService is locked because it is doing its own
                        // work, then this will wait.
                        //
                        // This line won't execute in the deadlock flow. However, it is an example
                        // of a real-world piece of code that would serve the same role as the above
                        uxRestrictionsService.getCurrentUxRestrictions();
                    }
                });

        // EVENT 1 [new thread]: handlePropertyEvent() is called, which locks CarDrivingStateService
        // Ideally CarPropertyService would trigger the change event, but since that is mocked
        // we manually trigger the event. This event is what eventually triggers the dispatch to
        // ICarDrivingStateChangeListener that was defined above.
        Runnable propertyChangeEventRunnable =
                () -> drivingStateService.handlePropertyEventLocked(
                        new CarPropertyEvent(CarPropertyEvent.PROPERTY_EVENT_PROPERTY_CHANGE,
                                new CarPropertyValue<>(
                                        VehiclePropertyIds.PERF_VEHICLE_SPEED, 0, 100f)));
        Thread thread = new Thread(propertyChangeEventRunnable);
        thread.start();

        // Wait until propertyChangeEventRunnable has triggered and the
        // ICarDrivingStateChangeListener callback declared above started to run.
        dispatchingStartedSignal.await();

        // EVENT 3a [main thread]: init() is called, which locks CarUxRestrictionsManagerService
        // If init() is synchronized, thereby locking CarUxRestrictionsManagerService, and it
        // internally attempts to access CarDrivingStateService, and if CarDrivingStateService has
        // been locked because of the above listener, then both classes are locked and waiting on
        // each other, so we would encounter a deadlock.
        uxRestrictionsService.init();

        // If there is a deadlock in init(), then this will never be called
        initCompleteSignal.countDown();

        // wait for thread to join to leave in a deterministic state
        try {
            thread.join(5000);
        } catch (InterruptedException e) {
            Assert.fail("Thread failed to join");
        }
    }

    // This test only involves calling a few methods and should finish very quickly. If it doesn't
    // finish in 20s, we probably encountered a deadlock.
    @Test(timeout = 20000)
    public void testSetUxRChangeBroadcastEnabled_NoDeadlockWithCarDrivingStateService()
            throws Exception {

        CarDrivingStateService drivingStateService = new CarDrivingStateService(mSpyContext,
                mMockCarPropertyService);
        CarUxRestrictionsManagerService uxRestrictionService = new CarUxRestrictionsManagerService(
                mSpyContext, drivingStateService, mMockCarPropertyService,
                mMockCarOccupantZoneService);

        CountDownLatch dispatchingStartedSignal = new CountDownLatch(1);
        CountDownLatch initCompleteSignal = new CountDownLatch(1);

        // See testInitService_NoDeadlockWithCarDrivingStateService for details on why a deadlock
        // may occur. This test could fail for the same reason, except the callback we register here
        // is purely to introduce a delay, and the deadlock actually happens inside the callback
        // that CarUxRestrictionsManagerService#init() registers internally.
        drivingStateService.registerDrivingStateChangeListener(
                new ICarDrivingStateChangeListener.Stub() {
                    @Override
                    public void onDrivingStateChanged(CarDrivingStateEvent event)
                            throws RemoteException {
                        // EVENT 2 [new thread]: this callback is called from within
                        // handlePropertyEvent(), which might (but shouldn't) lock
                        // CarDrivingStateService

                        // Notify that the dispatching process has started
                        dispatchingStartedSignal.countDown();

                        try {
                            // EVENT 3b [new thread]: Wait until init() has finished. If these
                            // threads don't have lock dependencies, there is no reason there
                            // would be an issue with waiting.
                            //
                            // In the real world, this wait could represent a long-running
                            // task, or hitting the line inside
                            // CarUxRestrictionsManagerService#init()'s internal registration
                            // that attempts to access the CarUxRestrictionsManagerService (which
                            // might be locked while init() is running).
                            //
                            // If there is a deadlock while waiting for init to complete, we will
                            // never progress past this line.
                            initCompleteSignal.await();
                        } catch (InterruptedException e) {
                            Assert.fail("onDrivingStateChanged thread interrupted");
                        }
                    }
                });

        // The init() method internally registers a callback to CarDrivingStateService
        uxRestrictionService.init();

        // EVENT 1 [new thread]: handlePropertyEvent() is called, which locks CarDrivingStateService
        // Ideally CarPropertyService would trigger the change event, but since that is mocked
        // we manually trigger the event. This event eventually triggers the dispatch to
        // ICarDrivingStateChangeListener that was defined above and a dispatch to the registration
        // that CarUxRestrictionsManagerService internally made to CarDrivingStateService in
        // CarUxRestrictionsManagerService#init().
        Runnable propertyChangeEventRunnable =
                () -> drivingStateService.handlePropertyEventLocked(
                        new CarPropertyEvent(CarPropertyEvent.PROPERTY_EVENT_PROPERTY_CHANGE,
                                new CarPropertyValue<>(
                                        VehiclePropertyIds.PERF_VEHICLE_SPEED, 0, 100f)));
        Thread thread = new Thread(propertyChangeEventRunnable);
        thread.start();

        // Wait until propertyChangeEventRunnable has triggered and the
        // ICarDrivingStateChangeListener callback declared above started to run.
        dispatchingStartedSignal.await();

        // EVENT 3a [main thread]: a synchronized method is called, which locks
        // CarUxRestrictionsManagerService
        //
        // Any synchronized method that internally accesses CarDrivingStateService could encounter a
        // deadlock if the above setup locks CarDrivingStateService.
        uxRestrictionService.setUxRChangeBroadcastEnabled(true);

        // If there is a deadlock in init(), then this will never be called
        initCompleteSignal.countDown();

        // wait for thread to join to leave in a deterministic state
        try {
            thread.join(5000);
        } catch (InterruptedException e) {
            Assert.fail("Thread failed to join");
        }
    }

    @Test(expected = SecurityException.class)
    public void testSetRestrictionMode_missingPermission_throwsException() throws Exception {
        when(mSpyContext.checkCallingOrSelfPermission(
                Car.PERMISSION_CAR_UX_RESTRICTIONS_CONFIGURATION))
                .thenReturn(PackageManager.PERMISSION_DENIED);
        mService.setRestrictionMode(UX_RESTRICTION_MODE_BASELINE);
    }

    @Test(expected = SecurityException.class)
    public void testGetRestrictionMode_missingPermission_throwsException() throws Exception {
        when(mSpyContext.checkCallingOrSelfPermission(
                Car.PERMISSION_CAR_UX_RESTRICTIONS_CONFIGURATION))
                .thenReturn(PackageManager.PERMISSION_DENIED);
        mService.getRestrictionMode();
    }

    @Test(expected = SecurityException.class)
    public void testSaveUxRestrictionsConfigurationForNextBoot_missingPermission_throwsException()
            throws Exception {
        when(mSpyContext.checkCallingOrSelfPermission(
                Car.PERMISSION_CAR_UX_RESTRICTIONS_CONFIGURATION))
                .thenReturn(PackageManager.PERMISSION_DENIED);
        mService.saveUxRestrictionsConfigurationForNextBoot(new ArrayList<>());
    }

    private CarUxRestrictionsConfiguration createEmptyConfig() {
        return createEmptyConfig(null);
    }

    private CarUxRestrictionsConfiguration createEmptyConfig(Integer port) {
        Builder builder = new Builder();
        if (port != null) {
            builder.setPhysicalPort(port);
        }
        return builder.build();
    }

    private void setUpMockParkedState() {
        when(mMockDrivingStateService.getCurrentDrivingState()).thenReturn(
                new CarDrivingStateEvent(CarDrivingStateEvent.DRIVING_STATE_PARKED, 0));

        CarPropertyValue<Float> speed = new CarPropertyValue<>(VehicleProperty.PERF_VEHICLE_SPEED,
                0, 0f);
        when(mMockCarPropertyService.getPropertySafe(VehicleProperty.PERF_VEHICLE_SPEED, 0))
                .thenReturn(speed);
    }

    private void setUpMockDrivingStateWithFakeSpeed(float fakeSpeed) {
        when(mMockDrivingStateService.getCurrentDrivingState()).thenReturn(
                new CarDrivingStateEvent(CarDrivingStateEvent.DRIVING_STATE_MOVING, 0));

        CarPropertyValue<Float> speed = new CarPropertyValue<>(VehicleProperty.PERF_VEHICLE_SPEED,
                0, fakeSpeed);
        when(mMockCarPropertyService.getPropertySafe(VehicleProperty.PERF_VEHICLE_SPEED, 0))
                .thenReturn(speed);
    }

    private File setupMockFile(String filename, List<CarUxRestrictionsConfiguration> configs)
            throws IOException {
        File f = new File(mTempSystemCarDir, filename);
        assertTrue(f.createNewFile());

        if (configs != null) {
            try (JsonWriter writer = new JsonWriter(
                    new OutputStreamWriter(new FileOutputStream(f), "UTF-8"))) {
                mService.writeJson(writer, configs);
            }
        }
        return f;
    }

    private File setupMockFileFromString(String filename, String config)
            throws IOException {
        File f = new File(mTempSystemCarDir, filename);
        assertTrue(f.createNewFile());

        if (config != null) {
            try (FileOutputStream writer = new FileOutputStream(f)) {
                byte[] bytes = config.getBytes();
                writer.write(bytes);
            }
        }
        return f;
    }

    private String readFile(Path path) throws Exception {
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }
}
