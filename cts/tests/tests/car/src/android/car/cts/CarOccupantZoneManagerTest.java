/*
 * Copyright (C) 2019 The Android Open Source Project
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

package android.car.cts;

import static android.car.feature.Flags.carDumpToProto;
import static android.view.Display.DEFAULT_DISPLAY;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assert.assertThrows;
import static org.junit.Assume.assumeTrue;

import static java.util.stream.Collectors.toList;

import android.app.UiAutomation;
import android.car.Car;
import android.car.CarOccupantZoneManager;
import android.car.CarOccupantZoneManager.OccupantZoneConfigChangeListener;
import android.car.CarOccupantZoneManager.OccupantZoneInfo;
import android.car.PlatformVersion;
import android.car.cts.utils.ProtoDumpUtils;
import android.car.input.CarInputManager;
import android.hardware.display.DisplayManager;
import android.os.Process;
import android.os.UserHandle;
import android.platform.test.annotations.AppModeFull;
import android.util.Log;
import android.view.Display;

import androidx.test.filters.SmallTest;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import com.android.car.occupantzone.CarOccupantZoneDumpProto;
import com.android.car.occupantzone.CarOccupantZoneDumpProto.DisplayConfigProto;
import com.android.car.occupantzone.CarOccupantZoneDumpProto.DisplayPortConfigsProto;
import com.android.car.occupantzone.CarOccupantZoneDumpProto.DisplayUniqueIdConfigsProto;
import com.android.compatibility.common.util.ApiTest;
import com.android.compatibility.common.util.ShellUtils;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@SmallTest
@RunWith(AndroidJUnit4.class)
@AppModeFull(reason = "Test relies on other server to connect to.")
public final class CarOccupantZoneManagerTest extends AbstractCarTestCase {

    private static String TAG = CarOccupantZoneManagerTest.class.getSimpleName();

    private static final String SERVICE_NAME = "CarOccupantZoneService";

    private OccupantZoneInfo mDriverZoneInfo;

    private CarOccupantZoneManager mCarOccupantZoneManager;

    private List<OccupantZoneInfo> mAllZones;

    private UiAutomation mUiAutomation;

    @Before
    public void setUp() throws Exception {
        mUiAutomation = InstrumentationRegistry.getInstrumentation().getUiAutomation();
        mCarOccupantZoneManager =
                (CarOccupantZoneManager) getCar().getCarManager(Car.CAR_OCCUPANT_ZONE_SERVICE);

        mAllZones = mCarOccupantZoneManager.getAllOccupantZones();

        if (mCarOccupantZoneManager.hasDriverZone()) {
            // Retrieve the current driver zone info (disregarding the driver's seat - LHD or RHD).
            // Ensures there is only one driver zone info.
            List<OccupantZoneInfo> drivers =
                    mAllZones.stream().filter(
                            o -> o.occupantType
                                    == CarOccupantZoneManager.OCCUPANT_TYPE_DRIVER).collect(
                            toList());
            assertWithMessage("One driver zone").that(drivers).hasSize(1);
            mDriverZoneInfo = drivers.get(0);
        }
    }

    @After
    public void tearDown() throws Exception {
        mUiAutomation.dropShellPermissionIdentity();
    }

    @Test
    @ApiTest(apis = {"android.car.CarOccupantZoneManager#getDisplayType(Display)"})
    public void testGetDisplayType_mainDisplay() {
        DisplayManager displayManager = mContext.getSystemService(DisplayManager.class);
        Display defaultDisplay = displayManager.getDisplay(DEFAULT_DISPLAY);

        assertThat(mCarOccupantZoneManager.getDisplayType(defaultDisplay)).isEqualTo(
                CarOccupantZoneManager.DISPLAY_TYPE_MAIN);
    }

    @Test
    @ApiTest(apis = {"android.car.CarOccupantZoneManager#getUserForOccupant(OccupantZoneInfo)"})
    public void testUserMustBeUserInTheirOccupantZone() {
        int myUid = Process.myUid();
        assertWithMessage("User in occupant zone must correspond to user id (Process.myUid: %s)",
                myUid).that(
                UserHandle.getUserId(myUid)).isEqualTo(
                mCarOccupantZoneManager.getUserForOccupant(
                        mCarOccupantZoneManager.getMyOccupantZone()));
    }

    @Test
    @ApiTest(apis = {"android.car.CarOccupantZoneManager#hasDriverZone",
            "android.car.CarOccupantZoneManager#hasPassengerZones"})
    public void testHasDriverOrPassengerZone() {
        boolean hasDriverZone = false;
        boolean hasPassengerZone = false;

        for (OccupantZoneInfo info : mAllZones) {
            if (info.occupantType == CarOccupantZoneManager.OCCUPANT_TYPE_DRIVER) {
                hasDriverZone = true;
            } else {
                hasPassengerZone = true;
            }
        }

        assertWithMessage("hasDriverZone() should match config").that(
                mCarOccupantZoneManager.hasDriverZone()).isEqualTo(hasDriverZone);
        assertWithMessage("hasPassengerZone() should match config").that(
                mCarOccupantZoneManager.hasPassengerZones()).isEqualTo(hasPassengerZone);
        assertWithMessage("should have driver or passenger zone").that(
                hasDriverZone || hasPassengerZone).isTrue();
    }

    @Test
    @ApiTest(apis = {"android.car.CarOccupantZoneManager#getAllOccupantZones"})
    public void testAtLeastOneZone() {
        assertWithMessage("Should have at least one zone").that(mAllZones).isNotEmpty();
    }

    @Test
    @ApiTest(apis = {"android.car.CarOccupantZoneManager#getAllOccupantZones"})
    public void testZoneConfigs() {
        List<OccupantZoneInfo> driverZones = new LinkedList<>();
        List<OccupantZoneInfo> frontPassengerZones = new LinkedList<>();
        HashMap<Integer, List<OccupantZoneInfo>> rearPassengerZones = new HashMap<>();
        List<OccupantZoneInfo> invalidZones = new LinkedList<>();

        for (OccupantZoneInfo info : mAllZones) {
            if (info.occupantType == CarOccupantZoneManager.OCCUPANT_TYPE_DRIVER) {
                driverZones.add(info);
            } else if (info.occupantType == CarOccupantZoneManager.OCCUPANT_TYPE_FRONT_PASSENGER) {
                frontPassengerZones.add(info);
            } else if (info.occupantType == CarOccupantZoneManager.OCCUPANT_TYPE_REAR_PASSENGER) {
                List<OccupantZoneInfo> list = rearPassengerZones.get(info.seat);
                if (list == null) {
                    list = new LinkedList<>();
                    rearPassengerZones.put(info.seat, list);
                }
                list.add(info);
            } else {
                invalidZones.add(info);
            }
        }

        assertWithMessage("Max one driver zone can exist").that(driverZones.size()).isAtMost(1);
        assertWithMessage("Max one front passenger zone can exist").that(
                frontPassengerZones.size()).isAtMost(1);
        for (Integer seat : rearPassengerZones.keySet()) {
            assertWithMessage("Max one zone can exist for seat " + seat).that(
                    rearPassengerZones.get(seat).size()).isAtMost(1);
        }
    }

    @Test
    @ApiTest(apis = {"android.car.CarOccupantZoneManager#getMyOccupantZone"})
    public void testMyZone() {
        OccupantZoneInfo info = mCarOccupantZoneManager.getMyOccupantZone();
        assumeTrue("Test user has no zone", info != null);

        OccupantZoneInfo info2 = mCarOccupantZoneManager.getOccupantZone(info.occupantType,
                info.seat);

        assertWithMessage("getMyOccupantZone and getOccupantZone should match").that(
                info).isEqualTo(info2);
        assertWithMessage("User Id for my zone").that(
                mCarOccupantZoneManager.getUserForOccupant(info)).isEqualTo(UserHandle.myUserId());
    }

    @Test
    @ApiTest(apis = {"android.car.CarOccupantZoneManager#getAllOccupantZones"})
    public void testExpectAtLeastDriverZoneExists() {
        assumeDriverZone();

        assertWithMessage(
                "Driver zone is expected to exist. Make sure a driver zone is properly defined in"
                        + " config.xml/config_occupant_display_mapping")
                .that(mCarOccupantZoneManager.getAllOccupantZones())
                .contains(mDriverZoneInfo);
    }

    @Test
    @ApiTest(apis = {
            "android.car.CarOccupantZoneManager#getAllDisplaysForOccupant(OccupantZoneInfo)"
    })
    public void testDriverHasMainDisplay() {
        assumeDriverZone();

        assertWithMessage("Driver is expected to be associated with main display")
                .that(mCarOccupantZoneManager.getAllDisplaysForOccupant(mDriverZoneInfo))
                .contains(getDriverDisplay());
    }

    @Test
    @ApiTest(apis = {
            "android.car.CarOccupantZoneManager#getDisplayForOccupant"
    })
    public void testDriverDisplayIdIsDefaultDisplay() {
        assumeDriverZone();

        assertThat(getDriverDisplay().getDisplayId()).isEqualTo(DEFAULT_DISPLAY);
    }

    @Test
    @ApiTest(apis = {
            "android.car.CarOccupantZoneManager#registerOccupantZoneConfigChangeListener"
    })
    public void testCanRegisterOccupantZoneConfigChangeListener() {
        OccupantZoneConfigChangeListener occupantZoneConfigChangeListener
                = createOccupantZoneConfigChangeListener();
        mCarOccupantZoneManager
                .registerOccupantZoneConfigChangeListener(occupantZoneConfigChangeListener);

        mCarOccupantZoneManager
                .unregisterOccupantZoneConfigChangeListener(occupantZoneConfigChangeListener);
    }

    @Test
    @ApiTest(apis = {
            "android.car.CarOccupantZoneManager#assignVisibleUserToOccupantZone"
    })
    public void testReassigningAlreadyAssignedZone() {
        mUiAutomation.adoptShellPermissionIdentity(Car.PERMISSION_MANAGE_OCCUPANT_ZONE);

        for (OccupantZoneInfo info : mAllZones) {
            int userId = mCarOccupantZoneManager.getUserForOccupant(info);
            if (userId != CarOccupantZoneManager.INVALID_USER_ID) {
                int result = mCarOccupantZoneManager.assignVisibleUserToOccupantZone(info,
                        UserHandle.of(userId));
                assertWithMessage(
                        "Re-assigning the same user to zoneId:%s", info.zoneId).that(
                        result).isEqualTo(CarOccupantZoneManager.USER_ASSIGNMENT_RESULT_OK);
            }
        }
    }

    @Test
    @ApiTest(apis = {"android.car.CarOccupantZoneManager#INVALID_USER_ID"})
    public void testInvalidUserId() {
        assertWithMessage("INVALID_USER_ID does not match with UserHandle.USER_NULL").that(
                CarOccupantZoneManager.INVALID_USER_ID).isEqualTo(UserHandle.USER_NULL);
    }

    // TODO(b/243698156) Assign invisible user and confirm failure. U only
    // @Test
    // @ApiTest(apis = {"android.car.CarOccupantZoneManager#assignVisibleUserToOccupantZone"})
    // public void testAssignInvisibleUser()

    // TODO(b/243698156) Create new visible user, assign it and stop the user and confirm that
    //  the zone is unassigned. U only
    // @Test
    // @ApiTest(apis = {"android.car.CarOccupantZoneManager#assignVisibleUserToOccupantZone"})
    // public void testAssignAndStopUser()

    // TODO(b/243698156) Switch current user and confirm that non-current visible users'
    // assignment stays. U only
    // @Test
    // @ApiTest(apis = {"android.car.CarOccupantZoneManager#assignVisibleUserToOccupantZone"})
    // public void testAssignAndSwitch()

    @Test
    @ApiTest(apis = {
            "android.car.CarOccupantZoneManager#assignVisibleUserToOccupantZone"
    })
    public void testZoneAssignmentWithoutPermission() {
        assertThrows(SecurityException.class, () ->
                mCarOccupantZoneManager.assignVisibleUserToOccupantZone(mAllZones.get(0),
                        UserHandle.CURRENT));
    }

    @Test
    @ApiTest(apis = {
            "android.car.CarOccupantZoneManager#assignVisibleUserToOccupantZone"
    })
    public void testAssignInvalidUser() {
        mUiAutomation.adoptShellPermissionIdentity(Car.PERMISSION_MANAGE_OCCUPANT_ZONE);

        OccupantZoneInfo zone = mAllZones.get(0);
        // We would not create this many users. So keep it simple.
        UserHandle invalidUser = UserHandle.of(Integer.MAX_VALUE);
        assertWithMessage(
                "Check USER_ASSIGNMENT_RESULT_FAIL_NON_VISIBLE_USER").that(
                mCarOccupantZoneManager.assignVisibleUserToOccupantZone(zone, invalidUser))
                        .isEqualTo(
                CarOccupantZoneManager.USER_ASSIGNMENT_RESULT_FAIL_NON_VISIBLE_USER);
    }

    @Test
    @ApiTest(apis = {
            "android.car.CarOccupantZoneManager#unassignOccupantZone"
    })
    public void testUnassignDriverZone() {
        assumeDriverZone();

        mUiAutomation.adoptShellPermissionIdentity(Car.PERMISSION_MANAGE_OCCUPANT_ZONE);

        OccupantZoneInfo driverZone = mCarOccupantZoneManager.getOccupantZone(
                CarOccupantZoneManager.OCCUPANT_TYPE_DRIVER, 0);
        assertWithMessage("Driver zone must exist").that(driverZone).isNotNull();
        assertWithMessage("Unassigning driver zone should fail").that(
                mCarOccupantZoneManager.unassignOccupantZone(driverZone)).isEqualTo(
                CarOccupantZoneManager.USER_ASSIGNMENT_RESULT_FAIL_DRIVER_ZONE);
    }

    @Test
    @ApiTest(apis = {
            "android.car.CarOccupantZoneManager#unassignOccupantZone"
    })
    public void testUnassignPassengerZones() {
        assumeTrue("No passenger zone", mCarOccupantZoneManager.hasPassengerZones());

        mUiAutomation.adoptShellPermissionIdentity(Car.PERMISSION_MANAGE_OCCUPANT_ZONE);

        for (OccupantZoneInfo zone : mCarOccupantZoneManager.getAllOccupantZones()) {
            if (zone.occupantType == CarOccupantZoneManager.OCCUPANT_TYPE_DRIVER) {
                continue;
            }
            int originalUser = mCarOccupantZoneManager.getUserForOccupant(zone);
            assertWithMessage("Unassigning passenger zone should work").that(
                    mCarOccupantZoneManager.unassignOccupantZone(zone)).isEqualTo(
                    CarOccupantZoneManager.USER_ASSIGNMENT_RESULT_OK);
            if (originalUser == CarOccupantZoneManager.INVALID_USER_ID) {
                continue;
            }
            assertWithMessage("Reassigning the same valid user should work").that(
                    mCarOccupantZoneManager.assignVisibleUserToOccupantZone(zone,
                            UserHandle.of(originalUser))).isEqualTo(
                    CarOccupantZoneManager.USER_ASSIGNMENT_RESULT_OK);
        }
    }

    @Test
    @ApiTest(apis = {"android.car.CarOccupantZoneManager#getDisplayIdForDriver(int)"})
    public void testClusterDisplayIsPrivate() {
        assumeDriverZone();

        mUiAutomation.adoptShellPermissionIdentity(Car.PERMISSION_MANAGE_OCCUPANT_ZONE,
                Car.ACCESS_PRIVATE_DISPLAY_ID);

        int clusterDisplayId = mCarOccupantZoneManager.getDisplayIdForDriver(
                CarOccupantZoneManager.DISPLAY_TYPE_INSTRUMENT_CLUSTER);
        assumeTrue("No cluster display", clusterDisplayId != Display.INVALID_DISPLAY);

        DisplayManager dm = mContext.getSystemService(DisplayManager.class);
        Display clusterDisplay = dm.getDisplay(clusterDisplayId);
        // Fetching the private display expects null.
        if (clusterDisplay != null) {
            assertWithMessage("Cluster display#%s is private", clusterDisplayId)
                    .that((clusterDisplay.getFlags() & Display.FLAG_PRIVATE) != 0).isTrue();
        }
    }

    @Test
    @ApiTest(apis = {"android.car.CarOccupantZoneManager#getOccupantZoneForUser"})
    public void testGetOccupantZoneForInvalidUser() {
        UserHandle invalidUser = UserHandle.of(UserHandle.USER_NULL);

        assertWithMessage("Occupant Zone for an invalid user(%s)", invalidUser)
                .that(mCarOccupantZoneManager.getOccupantZoneForUser(invalidUser))
                .isNull();
    }

    @Test
    @ApiTest(apis = {"android.car.CarOccupantZoneManager#getOccupantZoneForUser"})
    public void testGetOccupantZoneForNullUser() {
        NullPointerException thrown = assertThrows(NullPointerException.class,
                () -> mCarOccupantZoneManager.getOccupantZoneForUser(null));

        assertWithMessage("Occupant Zone for null user")
                .that(thrown).hasMessageThat()
                .contains("User cannot be null");
    }

    @Test
    @ApiTest(apis = {"android.car.CarOccupantZoneManager#getOccupantZoneForUser"})
    public void testGetOccupantZoneForAllUsers() {
        for (OccupantZoneInfo info : mAllZones) {
            int userId = mCarOccupantZoneManager.getUserForOccupant(info);
            OccupantZoneInfo result =
                    mCarOccupantZoneManager.getOccupantZoneForUser(UserHandle.of(userId));
            if (userId == UserHandle.USER_NULL) {
                expectWithMessage("Occupant zone for user: %s", userId)
                        .that(result).isNull();
                continue;
            }
            expectWithMessage("Occupant zone for user: %s", userId)
                    .that(result).isEqualTo(info);
        }
    }

    @Test
    @ApiTest(apis = {"android.car.CarOccupantZoneManager#getOccupantZoneForDisplayId"})
    public void testGetOccupantZoneForInvalidDisplayId() {
        assertWithMessage("Occupant Zone for an invalid display id (%s)", Display.INVALID_DISPLAY)
                .that(mCarOccupantZoneManager.getOccupantZoneForDisplayId(Display.INVALID_DISPLAY))
                .isNull();
    }

    @Test
    @ApiTest(apis = {"android.car.CarOccupantZoneManager#getOccupantZoneForDisplayId"})
    public void testGetOccupantZoneForDriverDisplayId() {
        assumeDriverZone();

        int displayId = getDriverDisplay().getDisplayId();
        assertWithMessage("Occupant zone for driver display id (%s)", displayId)
                .that(mCarOccupantZoneManager.getOccupantZoneForDisplayId(displayId))
                .isEqualTo(mDriverZoneInfo);
    }

    @Test
    @ApiTest(apis = {"android.car.CarOccupantZoneManager#getSupportedInputTypes"})
    public void testGetSupportedInputTypes_validatedInputTypes() throws Exception {
        assumeTrue((Car.getPlatformVersion().isAtLeast(
                PlatformVersion.VERSION_CODES.UPSIDE_DOWN_CAKE_0)));
        List<DisplayConfig> configs;
        if (carDumpToProto()) {
            configs = parseDisplayConfigsFromDumpProto();
        } else {
            String dump = ShellUtils.runShellCommand(
                    "dumpsys car_service --services CarOccupantZoneService");
            configs = parseDisplayConfigsFromDump(dump);
        }
        assumeTrue("No display config found for device", configs.size() > 0);

        // Ensure that input types match the ones from dumpsys
        for (DisplayConfig c : configs) {
            Optional<OccupantZoneInfo> occupantZoneInfo = mAllZones.stream().filter(
                    z -> z.zoneId == c.occupantZoneId).findFirst();
            if (occupantZoneInfo.isEmpty()) {
                // If occupant zone is not active then we skip
                continue;
            }
            List<Integer> inputTypes = mCarOccupantZoneManager.getSupportedInputTypes(
                    occupantZoneInfo.get(), c.displayType);
            assertWithMessage("Expected same input types").that(
                    inputTypes).containsExactlyElementsIn(c.inputTypes);
        }
    }

    @Test
    @ApiTest(apis = {"android.car.CarOccupantZoneManager#getSupportedInputTypes"})
    public void testGetSupportedInputTypes_validatedInputTypesForAndroidU() throws Exception {
        assumeTrue((Car.getPlatformVersion().isAtLeast(
                PlatformVersion.VERSION_CODES.TIRAMISU_0)));
        List<DisplayConfig> configs;
        if (carDumpToProto()) {
            configs = parseDisplayConfigsFromDumpProto();
        } else {
            String dump = ShellUtils.runShellCommand(
                    "dumpsys car_service --services CarOccupantZoneService");
            configs = parseDisplayConfigsFromDump(dump);
        }
        assertWithMessage("Device's display config cannot be empty").that(configs).isNotEmpty();

        for (DisplayConfig c : configs) {
            // Ensure that all input types have at least one item
            assertWithMessage("Display {%s} must have at least one input type defined",
                    c.displayType).that(
                    c.inputTypes).isNotEmpty();

            // Ensure that display MAIN display has at least TOUCH_SCREEN
            if (c.displayType == CarOccupantZoneManager.DISPLAY_TYPE_MAIN) {
                assertWithMessage(
                        "MAIN display must have at least TOUCH_SCREEN as input types")
                        .that(c.inputTypes).contains(CarInputManager.INPUT_TYPE_TOUCH_SCREEN);
            }
        }
    }

    // Parses the content from `mDisplayConfigs` field displayed in
    // `adb shell dumpsys car_service --services CarOccupantZoneService`
    //
    // Output example:
    // ** Dumping CarOccupantZoneService
    //
    // *OccupantZoneService*
    // **mOccupantsConfig**
    // zoneId=0 info=OccupantZoneInfo{zoneId=0 type=0 seat=1}
    // zoneId=1 info=OccupantZoneInfo{zoneId=1 type=1 seat=4}
    // **mDisplayConfigs**
    // port=0 config={displayType=1 occupantZoneId=0}
    // port=2 config={displayType=1 occupantZoneId=1}
    // **mAudioZoneIdToOccupantZoneIdMapping**
    // audioZoneId=0 zoneId=0
    // audioZoneId=1 zoneId=1
    // **mActiveOccupantConfigs**
    // zoneId=0 config={userId=10 displays={displayId=0 displayType=1}
    //         {displayId=8 displayType=2} audioZoneId=0}
    // zoneId=1 config={userId=-10000 displays={displayId=3 displayType=1} audioZoneId=1}
    // mEnableProfileUserAssignmentForMultiDisplay:true
    // mEnableSourcePreferred:true
    // mSourcePreferredComponents:
    //         [ComponentInfo{com.google.android.apps.maps/com.google.android.maps.MapsActivity}]
    private List<DisplayConfig> parseDisplayConfigsFromDump(String dump) {
        Pattern dumpPattern = Pattern.compile("\\*\\*mDisplayConfigs\\*\\*(.+?)\\*\\*",
                Pattern.DOTALL);
        Matcher dumpMatcher = dumpPattern.matcher(dump);
        if (!dumpMatcher.find()) {
            return new ArrayList<>();
        }
        String displayConfigsString = dumpMatcher.group(1);
        Pattern displayConfigPattern = Pattern.compile(
                "config=\\{displayType=(.*?) occupantZoneId=(.*?) inputTypes=\\[(.*?)\\]");
        Matcher inputTypeMatcher = displayConfigPattern.matcher(displayConfigsString);
        List<DisplayConfig> configs = new ArrayList<>();
        while (inputTypeMatcher.find()) {
            int displayId = Integer.parseInt(inputTypeMatcher.group(1));
            int occupantZoneId = Integer.parseInt(inputTypeMatcher.group(2));
            List<Integer> inputTypes = new ArrayList<>();
            String inputTypesString = inputTypeMatcher.group(3);
            if (!inputTypesString.isEmpty()) {
                for (String inputTypeString : inputTypesString.split(",")) {
                    inputTypes.add(Integer.parseInt(inputTypeString.trim()));
                }
            }
            configs.add(new DisplayConfig(displayId, occupantZoneId, inputTypes));
        }
        return configs;
    }

    private List<DisplayConfig> parseDisplayConfigsFromDumpProto() throws Exception {
        List<DisplayConfig> configs = new ArrayList<>();
        CarOccupantZoneDumpProto carOccupantZoneDumpProto = CarOccupantZoneDumpProto.parseFrom(
                ProtoDumpUtils.executeProtoDumpShellCommand(SERVICE_NAME));
        if (carOccupantZoneDumpProto.getDisplayPortConfigsCount() > 0) {
            List<DisplayPortConfigsProto> displayPortConfigsList =
                    carOccupantZoneDumpProto.getDisplayPortConfigsList();
            for (int i = 0; i < displayPortConfigsList.size(); i++) {
                DisplayPortConfigsProto displayPortConfigsProto = displayPortConfigsList.get(i);
                DisplayConfigProto displayConfigProto =
                        displayPortConfigsProto.getDisplayConfigPort().getDisplayConfig();
                int displayType = displayConfigProto.getDisplayType();
                int occupantZoneId = displayConfigProto.getOccupantZoneId();
                List<Integer> inputTypes = new ArrayList<>();
                if (displayConfigProto.getInputTypesCount() > 0) {
                    inputTypes = displayConfigProto.getInputTypesList();
                } else {
                    throwExceptionFromProto(carOccupantZoneDumpProto);
                }
                configs.add(new DisplayConfig(displayType, occupantZoneId, inputTypes));
            }
        }

        if (carOccupantZoneDumpProto.getDisplayUniqueIdConfigsCount() > 0) {
            List<DisplayUniqueIdConfigsProto> displayUniqueIdConfigsList =
                    carOccupantZoneDumpProto.getDisplayUniqueIdConfigsList();
            for (int i = 0; i < displayUniqueIdConfigsList.size(); i++) {
                DisplayUniqueIdConfigsProto displayUniqueIdConfigsProto =
                        displayUniqueIdConfigsList.get(i);
                DisplayConfigProto displayConfigProto =
                        displayUniqueIdConfigsProto.getDisplayConfigUniqueId().getDisplayConfig();
                int displayType = displayConfigProto.getDisplayType();
                int occupantZoneId = displayConfigProto.getOccupantZoneId();
                List<Integer> inputTypes = new ArrayList<>();
                if (displayConfigProto.getInputTypesCount() > 0) {
                    inputTypes = displayConfigProto.getInputTypesList();
                } else {
                    throwExceptionFromProto(carOccupantZoneDumpProto);
                }
                configs.add(new DisplayConfig(displayType, occupantZoneId, inputTypes));
            }
        }
        return configs;
    }

    @SuppressWarnings("LiteProtoToString")
    private void throwExceptionFromProto(CarOccupantZoneDumpProto carOccupantZoneDumpProto) {
        throw new IllegalStateException("Proto doesn't have all fields\n proto:"
                + carOccupantZoneDumpProto.toString());
    }

    void assumeDriverZone() {
        assumeTrue("No driver zone", mCarOccupantZoneManager.hasDriverZone());
    }

    private Display getDriverDisplay() {
        Display driverDisplay =
                mCarOccupantZoneManager.getDisplayForOccupant(
                        mDriverZoneInfo, CarOccupantZoneManager.DISPLAY_TYPE_MAIN);
        assertWithMessage(
                "No display set for driver. Make sure a default display is set in"
                        + " config.xml/config_occupant_display_mapping")
                .that(driverDisplay)
                .isNotNull();
        return driverDisplay;
    }

    private OccupantZoneConfigChangeListener createOccupantZoneConfigChangeListener() {
        return new OccupantZoneConfigChangeListener() {
            public void onOccupantZoneConfigChanged(int changeFlags) {
                Log.i(TAG, "Got a confing change, flags: " + changeFlags);
            }
        };
    }

    private static class DisplayConfig {
        public final int displayType;
        public final int occupantZoneId;
        public final List<Integer> inputTypes;

        DisplayConfig(int displayType, int occupantZoneId, List<Integer> inputTypes) {
            this.displayType = displayType;
            this.occupantZoneId = occupantZoneId;
            this.inputTypes = Collections.unmodifiableList(inputTypes);
        }
    }
}
