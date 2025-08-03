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

package android.car;

import static com.android.car.internal.ExcludeFromCodeCoverageGeneratedReport.BOILERPLATE_CODE;

import android.annotation.IntDef;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.RequiresPermission;
import android.annotation.SystemApi;
import android.annotation.UserIdInt;
import android.hardware.display.DisplayManager;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Parcel;
import android.os.Parcelable;
import android.os.RemoteException;
import android.os.UserHandle;
import android.os.UserManager;
import android.util.Log;
import android.view.Display;

import com.android.car.internal.ExcludeFromCodeCoverageGeneratedReport;
import com.android.car.internal.util.Lists;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * API to get information on displays and users in the car.
 *
 * <p>From car version {@link CarVersion.VERSION_CODES#UPSIDE_DOWN_CAKE_0}, system without the
 * driver zone is allowed and the current user will not be the driver.
 */
public class CarOccupantZoneManager extends CarManagerBase {

    private static final String TAG = CarOccupantZoneManager.class.getSimpleName();

    /**
     * Display type is not known. In some system, some displays may be just public display without
     * any additional information and such displays will be treated as unknown.
     */
    public static final int DISPLAY_TYPE_UNKNOWN = 0;

    /**
     * Main display users are interacting with. UI for the user will be launched to this display by
     * default. {@link Display#DEFAULT_DISPLAY} will be always have this type. But there can be
     * multiple of this type as each passenger can have their own main display.
     */
    public static final int DISPLAY_TYPE_MAIN = 1;

    /** Instrument cluster display. This may exist only for driver. */
    public static final int DISPLAY_TYPE_INSTRUMENT_CLUSTER = 2;

    /** Head Up Display. This may exist only for driver. */
    public static final int DISPLAY_TYPE_HUD = 3;

    /** Dedicated display for showing IME for {@link #DISPLAY_TYPE_MAIN} */
    public static final int DISPLAY_TYPE_INPUT = 4;

    /**
     * Auxiliary display which can provide additional screen for {@link #DISPLAY_TYPE_MAIN}.
     * Activity running in {@link #DISPLAY_TYPE_MAIN} may use {@link android.app.Presentation} to
     * show additional information.
     */
    public static final int DISPLAY_TYPE_AUXILIARY = 5;

    /**
     * Auxiliary display which can provide additional screen for {@link #DISPLAY_TYPE_MAIN}.
     * Activities running in {@link #DISPLAY_TYPE_MAIN} or other auxiliary displays may be moved
     * to this type of display.
     * @hide
     */
    public static final int DISPLAY_TYPE_AUXILIARY_2 = 6;

    /**
     * Auxiliary display which can provide additional screen for {@link #DISPLAY_TYPE_MAIN}.
     * Activities running in {@link #DISPLAY_TYPE_MAIN} or other auxiliary displays may be moved
     * to this type of display.
     * @hide
     */
    public static final int DISPLAY_TYPE_AUXILIARY_3 = 7;

    /**
     * Auxiliary display which can provide additional screen for {@link #DISPLAY_TYPE_MAIN}.
     * Activities running in {@link #DISPLAY_TYPE_MAIN} or other auxiliary displays may be moved
     * to this type of display.
     * @hide
     */
    public static final int DISPLAY_TYPE_AUXILIARY_4 = 8;

    /**
     * Auxiliary display which can provide additional screen for {@link #DISPLAY_TYPE_MAIN}.
     * Activities running in {@link #DISPLAY_TYPE_MAIN} or other auxiliary displays may be moved
     * to this type of display.
     * @hide
     */
    public static final int DISPLAY_TYPE_AUXILIARY_5 = 9;

    /** @hide */
    @Retention(RetentionPolicy.SOURCE)
    @IntDef(prefix = "DISPLAY_TYPE_", value = {
            DISPLAY_TYPE_UNKNOWN,
            DISPLAY_TYPE_MAIN,
            DISPLAY_TYPE_INSTRUMENT_CLUSTER,
            DISPLAY_TYPE_HUD,
            DISPLAY_TYPE_INPUT,
            DISPLAY_TYPE_AUXILIARY,
            DISPLAY_TYPE_AUXILIARY_2,
            DISPLAY_TYPE_AUXILIARY_3,
            DISPLAY_TYPE_AUXILIARY_4,
            DISPLAY_TYPE_AUXILIARY_5,
    })
    @Target({ElementType.TYPE_USE})
    public @interface DisplayTypeEnum {}

    /** @hide */
    public static final int OCCUPANT_TYPE_INVALID = -1;

    /**
     * Represents the driver. There can be one or zero driver for the system. Zero driver situation
     * can happen if the system is configured to support only passengers.
     */
    public static final int OCCUPANT_TYPE_DRIVER = 0;

    /**
     * Represents front passengers who sit in front side of car. Most cars will have only
     * one passenger of this type but this can be multiple.
     */
    public static final int OCCUPANT_TYPE_FRONT_PASSENGER = 1;

    /** Represents passengers in rear seats. There can be multiple passengers of this type. */
    public static final int OCCUPANT_TYPE_REAR_PASSENGER = 2;

    /** @hide */
    @Retention(RetentionPolicy.SOURCE)
    @IntDef(prefix = "OCCUPANT_TYPE_", value = {
            OCCUPANT_TYPE_DRIVER,
            OCCUPANT_TYPE_FRONT_PASSENGER,
            OCCUPANT_TYPE_REAR_PASSENGER,
    })
    @Target({ElementType.TYPE_USE})
    public @interface OccupantTypeEnum {}

    /**
     * Represents an occupant zone in a car.
     *
     * <p>Each occupant does not necessarily represent single person but it is for mapping to one
     * set of displays. For example, for display located in center rear seat, both left and right
     * side passengers may use it but it is abstracted as a single occupant zone.</p>
     */
    public static final class OccupantZoneInfo implements Parcelable {
        /** @hide */
        public static final int INVALID_ZONE_ID = -1;

        /**
         * This is an unique id to distinguish each occupant zone.
         *
         * <p>This can be helpful to distinguish different zones when {@link #occupantType} and
         * {@link #seat} are the same for multiple occupant / passenger zones.</p>
         *
         * <p>This id will remain the same for the same zone across configuration changes like
         * user switching or display changes</p>
         */
        public int zoneId;
        /** Represents type of passenger */
        @OccupantTypeEnum
        public final int occupantType;
        /**
         * Represents seat assigned for the occupant. In some system, this can have value of
         * {@link VehicleAreaSeat#SEAT_UNKNOWN}.
         */
        @VehicleAreaSeat.Enum
        public final int seat;

        /** @hide */
        public OccupantZoneInfo(int zoneId, @OccupantTypeEnum int occupantType,
                @VehicleAreaSeat.Enum int seat) {
            this.zoneId = zoneId;
            this.occupantType = occupantType;
            this.seat = seat;
        }

        /** @hide */
        public OccupantZoneInfo(Parcel in) {
            zoneId = in.readInt();
            occupantType = in.readInt();
            seat = in.readInt();
        }

        @Override
        @ExcludeFromCodeCoverageGeneratedReport(reason = BOILERPLATE_CODE)
        public int describeContents() {
            return 0;
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            dest.writeInt(zoneId);
            dest.writeInt(occupantType);
            dest.writeInt(seat);
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof OccupantZoneInfo)) {
                return false;
            }
            OccupantZoneInfo that = (OccupantZoneInfo) other;
            return zoneId == that.zoneId && occupantType == that.occupantType
                    && seat == that.seat;
        }

        @Override
        public int hashCode() {
            int hash = 23;
            hash = hash * 17 + zoneId;
            hash = hash * 17 + occupantType;
            hash = hash * 17 + seat;
            return hash;
        }

        public static final Parcelable.Creator<OccupantZoneInfo> CREATOR =
                new Parcelable.Creator<>() {
                    public OccupantZoneInfo createFromParcel(Parcel in) {
                        return new OccupantZoneInfo(in);
                    }

                    public OccupantZoneInfo[] newArray(int size) {
                        return new OccupantZoneInfo[size];
                    }
                };

        @Override
        public String toString() {
            StringBuilder b = new StringBuilder(64);
            b.append("OccupantZoneInfo{zoneId=");
            b.append(zoneId);
            b.append(" type=");
            b.append(occupantType);
            b.append(" seat=");
            b.append(Integer.toHexString(seat));
            b.append("}");
            return b.toString();
        }
    }

    /**
     * Zone config change caused by display changes. A display could have been added / removed.
     * Besides change in display itself. this can lead into removal / addition of passenger zones.
     */
    public static final int ZONE_CONFIG_CHANGE_FLAG_DISPLAY = 0x1;

    /** Zone config change caused by user change. Assigned user for passenger zones have changed. */
    public static final int ZONE_CONFIG_CHANGE_FLAG_USER = 0x2;

    /**
     * Zone config change caused by audio zone change.
     * Assigned audio zone for passenger zones have changed.
     **/
    public static final int ZONE_CONFIG_CHANGE_FLAG_AUDIO = 0x4;

    /** @hide */
    @IntDef(flag = true, prefix = {"ZONE_CONFIG_CHANGE_FLAG_"}, value = {
            ZONE_CONFIG_CHANGE_FLAG_DISPLAY,
            ZONE_CONFIG_CHANGE_FLAG_USER,
            ZONE_CONFIG_CHANGE_FLAG_AUDIO,
    })
    @Retention(RetentionPolicy.SOURCE)
    @interface ZoneConfigChangeFlags {}

    /**
     * The assignment was successful.
     *
     * @hide
     */
    @SystemApi
    public static final int USER_ASSIGNMENT_RESULT_OK = 0;

    /**
     * The operation has failed as the user is already assigned to other zone. If the goal is to
     * move the user, the current zone should be unassigned first.
     *
     * @hide
     */
    @SystemApi
    public static final int USER_ASSIGNMENT_RESULT_FAIL_ALREADY_ASSIGNED = 1;

    /**
     * The assigned user is not a {@link UserManager#isUserVisible() visible user}.
     *
     * @hide
     */
    @SystemApi
    public static final int USER_ASSIGNMENT_RESULT_FAIL_NON_VISIBLE_USER = 2;

    /**
     * Assigning non-current user to driver zone or un-assigning driver zone will fail with this
     * error.
     *
     * @hide
     */
    @SystemApi
    public static final int USER_ASSIGNMENT_RESULT_FAIL_DRIVER_ZONE = 3;

    /** @hide */
    @IntDef(flag = false, prefix = {"USER_ASSIGNMENT_RESULT_"}, value = {
            USER_ASSIGNMENT_RESULT_OK,
            USER_ASSIGNMENT_RESULT_FAIL_ALREADY_ASSIGNED,
            USER_ASSIGNMENT_RESULT_FAIL_NON_VISIBLE_USER,
            USER_ASSIGNMENT_RESULT_FAIL_DRIVER_ZONE,
    })
    @Retention(RetentionPolicy.SOURCE)
    @interface UserAssignmentResult {}

    /**
     * Invalid user ID. Zone with this user ID has no allocated user. Should have the same value
     * with {@link UserHandle#USER_NULL}.
     */
    public static final @UserIdInt int INVALID_USER_ID = -10000;

    /**
     * Listener to monitor any Occupant Zone configuration change. The configuration change can
     * involve some displays removed or new displays added. Also it can happen when assigned user
     * for any zone changes.
     */
    public interface OccupantZoneConfigChangeListener {

        /**
         * Configuration for occupant zones has changed. Apps should re-check all
         * occupant zone configs. This can be caused by events like user switching and
         * display addition / removal.
         *
         * @param changeFlags Reason for the zone change.
         */
        void onOccupantZoneConfigChanged(@ZoneConfigChangeFlags int changeFlags);
    }

    private final DisplayManager mDisplayManager;
    private final EventHandler mEventHandler;

    private final ICarOccupantZone mService;

    private final ICarOccupantZoneCallbackImpl mBinderCallback;

    private final CopyOnWriteArrayList<OccupantZoneConfigChangeListener> mListeners =
            new CopyOnWriteArrayList<>();

    /**
     * Gets an instance of the CarOccupantZoneManager.
     *
     * <p>Should not be obtained directly by clients, use {@link Car#getCarManager(String)} instead.
     *
     * @hide
     */
    public CarOccupantZoneManager(Car car, IBinder service) {
        super(car);
        mService = ICarOccupantZone.Stub.asInterface(service);
        mBinderCallback = new ICarOccupantZoneCallbackImpl(this);
        mDisplayManager = getContext().getSystemService(DisplayManager.class);
        mEventHandler = new EventHandler(getEventHandler().getLooper());
    }

    /**
     * Returns all available occupants in the system. If no occupant zone is defined in the system
     * or none is available at the moment, it will return empty list.
     */
    @NonNull
    public List<OccupantZoneInfo> getAllOccupantZones() {
        try {
            return mService.getAllOccupantZones();
        } catch (RemoteException e) {
            return handleRemoteExceptionFromCarService(e, Collections.emptyList());
        }
    }

    /**
     * Returns all displays assigned for the given occupant zone. If no display is available for
     * the passenger, it will return empty list.
     */
    @NonNull
    public List<Display> getAllDisplaysForOccupant(@NonNull OccupantZoneInfo occupantZone) {
        assertNonNullOccupant(occupantZone);
        try {
            int[] displayIds = mService.getAllDisplaysForOccupantZone(occupantZone.zoneId);
            ArrayList<Display> displays = new ArrayList<>(displayIds.length);
            for (int i = 0; i < displayIds.length; i++) {
                // quick confidence check while getDisplay can still handle invalid display
                if (displayIds[i] == Display.INVALID_DISPLAY) {
                    continue;
                }
                Display display = mDisplayManager.getDisplay(displayIds[i]);
                if (display != null) { // necessary as display list could have changed.
                    displays.add(display);
                }
            }
            return displays;
        } catch (RemoteException e) {
            return handleRemoteExceptionFromCarService(e, Collections.emptyList());
        }
    }

    /**
     * Gets the display for the occupant for the specified display type, or returns {@code null}
     * if no display matches the requirements.
     *
     * @param displayType This should be a valid display type and passing
     *                    {@link #DISPLAY_TYPE_UNKNOWN} will always lead into {@code null} return.
     */
    @Nullable
    public Display getDisplayForOccupant(@NonNull OccupantZoneInfo occupantZone,
            @DisplayTypeEnum int displayType) {
        assertNonNullOccupant(occupantZone);
        try {
            int displayId = mService.getDisplayForOccupant(occupantZone.zoneId, displayType);
            // quick confidence check while getDisplay can still handle invalid display
            if (displayId == Display.INVALID_DISPLAY) {
                return null;
            }
            return mDisplayManager.getDisplay(displayId);
        } catch (RemoteException e) {
            return handleRemoteExceptionFromCarService(e, null);
        }
    }

    /**
     * Returns the display id for the driver.
     *
     * <p>This method just returns the display id for the requested type. The returned display id
     * may correspond to a private display and the client may not have access to it.
     *
     * @param displayType the display type
     * @return the driver's display id or {@link Display#INVALID_DISPLAY} when no such display
     * exists or if the driver zone does not exist.
     *
     * @hide
     */
    @SystemApi
    @RequiresPermission(Car.ACCESS_PRIVATE_DISPLAY_ID)
    public int getDisplayIdForDriver(@DisplayTypeEnum int displayType) {
        try {
            return mService.getDisplayIdForDriver(displayType);
        } catch (RemoteException e) {
            return handleRemoteExceptionFromCarService(e, Display.INVALID_DISPLAY);
        }
    }

    /**
     * Gets the audio zone id for the occupant, or returns
     * {@code CarAudioManager.INVALID_AUDIO_ZONE} if no audio zone matches the requirements.
     * throws InvalidArgumentException if occupantZone does not exist.
     *
     * @hide
     */
    @SystemApi
    @RequiresPermission(Car.PERMISSION_CAR_CONTROL_AUDIO_SETTINGS)
    public int getAudioZoneIdForOccupant(@NonNull OccupantZoneInfo occupantZone) {
        assertNonNullOccupant(occupantZone);
        try {
            return mService.getAudioZoneIdForOccupant(occupantZone.zoneId);
        } catch (RemoteException e) {
            return handleRemoteExceptionFromCarService(e, null);
        }
    }

    /**
     * Gets occupant for the audio zone id, or returns {@code null}
     * if no audio zone matches the requirements.
     *
     * @hide
     */
    @Nullable
    @SystemApi
    @RequiresPermission(Car.PERMISSION_CAR_CONTROL_AUDIO_SETTINGS)
    public OccupantZoneInfo getOccupantForAudioZoneId(int audioZoneId) {
        try {
            return mService.getOccupantForAudioZoneId(audioZoneId);
        } catch (RemoteException e) {
            return handleRemoteExceptionFromCarService(e, null);
        }
    }

    /**
     * Returns assigned display type for the display. It will return {@link #DISPLAY_TYPE_UNKNOWN}
     * if type is not specified or if display is no longer available.
     */
    @DisplayTypeEnum
    public int getDisplayType(@NonNull Display display) {
        assertNonNullDisplay(display);
        try {
            return mService.getDisplayType(display.getDisplayId());
        } catch (RemoteException e) {
            return handleRemoteExceptionFromCarService(e, DISPLAY_TYPE_UNKNOWN);
        }
    }

    /**
     * Returns android user id assigned for the given zone. It will return
     * {@link #INVALID_USER_ID} if user is not assigned or if zone is not available.
     */
    @UserIdInt
    public int getUserForOccupant(@NonNull OccupantZoneInfo occupantZone) {
        assertNonNullOccupant(occupantZone);
        try {
            return mService.getUserForOccupant(occupantZone.zoneId);
        } catch (RemoteException e) {
            return handleRemoteExceptionFromCarService(e, INVALID_USER_ID);
        }
    }

    /**
     * Returns assigned user id for the given display id.
     *
     * @param displayId Should be valid display id. Passing invalid display id will lead into
     *        getting {@link #INVALID_USER_ID} result.
     * @return Valid user id or {@link #INVALID_USER_ID} if no user is assigned for the display.
     */
    @UserIdInt
    public int getUserForDisplayId(int displayId) {
        try {
            return mService.getUserForDisplayId(displayId);
        } catch (RemoteException e) {
            return handleRemoteExceptionFromCarService(e, INVALID_USER_ID);
        }
    }

    /**
     * Returns the info for the occupant zone that has the display identified by the given
     * {@code displayId}.
     *
     * @param displayId Should be valid display id. Passing in invalid display id will lead into
     *        getting {@code null} occupant zone info result.
     * @return Occupant zone info or {@code null} if no occupant zone is found which has the given
     * display.
     *
     * @hide
     */
    @SystemApi
    @Nullable
    public OccupantZoneInfo getOccupantZoneForDisplayId(int displayId) {
        try {
            return mService.getOccupantZoneForDisplayId(displayId);
        } catch (RemoteException e) {
            return handleRemoteExceptionFromCarService(e, null);
        }
    }

    /**
     * Assigns the given profile {@code userId} to the {@code occupantZone}. Returns true when the
     * request succeeds.
     *
     * <p>Note that only non-driver zone can be assigned with this call. Calling this for driver
     * zone will lead into {@code IllegalArgumentException}.
     *
     * @param occupantZone Zone to assign user.
     * @param userId       profile user id to assign. Passing {@link #INVALID_USER_ID} leads into
     *                     removing the current user assignment.
     * @return true if the request succeeds or if the user is already assigned to the zone.
     * @deprecated Use {@link #assignVisibleUserToOccupantZone(OccupantZoneInfo, UserHandle)}
     * instead.
     *
     * @hide
     */
    @RequiresPermission(anyOf = {android.Manifest.permission.MANAGE_USERS,
            Car.PERMISSION_MANAGE_OCCUPANT_ZONE})
    @Deprecated
    public boolean assignProfileUserToOccupantZone(@NonNull OccupantZoneInfo occupantZone,
            @UserIdInt int userId) {
        assertNonNullOccupant(occupantZone);
        try {
            return mService.assignProfileUserToOccupantZone(occupantZone.zoneId, userId);
        } catch (RemoteException e) {
            return handleRemoteExceptionFromCarService(e, false);
        }
    }

    /**
     * Assign a visible user, which gets {@code true} from ({@link UserManager#isUserVisible()},
     * to the specified occupant zone.
     *
     * <p>This API handles occupant zone change.
     *
     * <p>This API can take a long time, so it is recommended to call this from non-main thread.
     *
     * <p> The return value is {@link #USER_ASSIGNMENT_RESULT_OK} when the assignment succeeds or if
     * the user is already allocated to the zone. Note that new error code can be added in the
     * future. For now, following error codes will be returned for a Failure:
     * <ul>
     *   <li>{@link #USER_ASSIGNMENT_RESULT_FAIL_NON_VISIBLE_USER} for non-visible user.
     *   <li>{@link #USER_ASSIGNMENT_RESULT_FAIL_ALREADY_ASSIGNED} if the user is already assigned
     *       to other zone. New error code can be added in future.
     *   <li>{@link #USER_ASSIGNMENT_RESULT_FAIL_DRIVER_ZONE} if non-current user is assigned to the
     *       driver zone.
     * </ul>
     *
     * <p>The system requires one user to  have one zone and moving user from one zone to another
     * requires unassigning the zone using {@link #unassignOccupantZone(OccupantZoneInfo)}
     * first.
     *
     * @param occupantZone the occupant zone to change user allocation
     * @param user the user to allocate. {@code null} user removes the allocation for the zone
     *             {@link UserHandle#CURRENT} will assign the current user to the zone
     * @return Check the above
     *
     * @hide
     */
    @SystemApi
    @RequiresPermission(anyOf = {android.Manifest.permission.MANAGE_USERS,
            Car.PERMISSION_MANAGE_OCCUPANT_ZONE})
    @UserAssignmentResult
    public int assignVisibleUserToOccupantZone(@NonNull OccupantZoneInfo occupantZone,
            @NonNull UserHandle user) {
        assertNonNullOccupant(occupantZone);
        try {
            return mService.assignVisibleUserToOccupantZone(occupantZone.zoneId, user);
        } catch (RemoteException e) {
            // Return any error code if car service is gone.
            return handleRemoteExceptionFromCarService(e,
                    USER_ASSIGNMENT_RESULT_FAIL_ALREADY_ASSIGNED);
        }
    }

    /**
     * Un-assign user from the specified occupant zone. The zone will return
     * {@link #INVALID_USER_ID} for {@link #getUserForOccupant(OccupantZoneInfo)} after this call.
     *
     * @param occupantZone Zone to unassign.
     * @return {@link #USER_ASSIGNMENT_RESULT_OK} if the zone is unassigned by the call or was
     * already unassigned. Error code of {@link #USER_ASSIGNMENT_RESULT_FAIL_DRIVER_ZONE}
     * will be returned if driver zone is asked.
     *
     * @hide
     */
    @SystemApi
    @RequiresPermission(anyOf = {android.Manifest.permission.MANAGE_USERS,
            Car.PERMISSION_MANAGE_OCCUPANT_ZONE})
    @UserAssignmentResult
    public int unassignOccupantZone(@NonNull OccupantZoneInfo occupantZone) {
        try {
            return mService.unassignOccupantZone(occupantZone.zoneId);
        } catch (RemoteException e) {
            // Return any error code if car service is gone.
            return handleRemoteExceptionFromCarService(e, USER_ASSIGNMENT_RESULT_FAIL_DRIVER_ZONE);
        }
    }

    private void assertNonNullOccupant(OccupantZoneInfo occupantZone) {
        if (occupantZone == null) {
            throw new IllegalArgumentException("null OccupantZoneInfo");
        }
    }

    private void assertNonNullDisplay(Display display) {
        if (display == null) {
            throw new IllegalArgumentException("null Display");
        }
    }

    /**
     * Registers the listener for occupant zone config change. Registering multiple listeners are
     * allowed.
     */
    public void registerOccupantZoneConfigChangeListener(
            @NonNull OccupantZoneConfigChangeListener listener) {
        if (mListeners.addIfAbsent(listener)) {
            if (mListeners.size() == 1) {
                try {
                    mService.registerCallback(mBinderCallback);
                } catch (RemoteException e) {
                    handleRemoteExceptionFromCarService(e);
                }
            }
        }
    }

    /**
     * Unregisters the listener. Listeners not registered before will be ignored.
     */
    public void unregisterOccupantZoneConfigChangeListener(
            @NonNull OccupantZoneConfigChangeListener listener) {
        if (mListeners.remove(listener)) {
            if (mListeners.size() == 0) {
                try {
                    mService.unregisterCallback(mBinderCallback);
                } catch (RemoteException ignored) {
                    // ignore for unregistering
                }
            }
        }
    }

    /**
     * Returns {@link OccupantZoneInfo} for the calling process's android user.
     * It will return {@code null} if there is no occupant zone assigned for the user.
     *
     * <p>When there is no occupant zone allocated for the user, most likely the user is not allowed
     * to run Activity or play audio, which are the main use cases to get the zone. So apps should
     * not try such tasks when {@code null} {@code OccupantZoneInfo} is returned. There can be an
     * exception for system user running under
     * {@link UserManager#isHeadlessSystemUserMode() Headless System User Mode}: The system user
     * apps may show UI even if there is no zone allocated.
     */
    @Nullable
    public OccupantZoneInfo getMyOccupantZone() {
        try {
            return mService.getMyOccupantZone();
        } catch (RemoteException e) {
            return handleRemoteExceptionFromCarService(e, null);
        }
    }

    /**
     * Returns {@code OccupantZoneInfo} associated with the given {@code UserHandle}. In the case
     * that the user is associated with multiple zones, this API returns the first matched zone.
     *
     * @param user The user to find.
     * @return Matching occupant zone or {@code null} if the user is not assigned or user has a
     * userId of {@code UserHandle#USER_NULL}.
     */
    @SuppressWarnings("UserHandle")
    @Nullable
    public OccupantZoneInfo getOccupantZoneForUser(@NonNull UserHandle user) {
        try {
            return mService.getOccupantZoneForUser(user);
        } catch (RemoteException e) {
            return handleRemoteExceptionFromCarService(e, /* returnValue= */null);
        }
    }

    /**
     * Finds {@code OccupantZoneInfo} for the given occupant type and seat.
     * <p>For{@link #OCCUPANT_TYPE_DRIVER} and {@link #OCCUPANT_TYPE_FRONT_PASSENGER}, {@code seat}
     * argument will be ignored.
     *
     * @param occupantType should be one of {@link #OCCUPANT_TYPE_DRIVER},
     *                     {@link #OCCUPANT_TYPE_FRONT_PASSENGER},
     *                     {@link #OCCUPANT_TYPE_FRONT_PASSENGER}
     * @param seat         Seat of the occupant. This is necessary for
     *                     {@link #OCCUPANT_TYPE_REAR_PASSENGER}.
     * @return Matching occupant zone or {@code null} if such zone does not exist.
     */
    @Nullable
    public OccupantZoneInfo getOccupantZone(@OccupantTypeEnum int occupantType,
            @VehicleAreaSeat.Enum int seat) {
        try {
            return mService.getOccupantZone(occupantType, seat);
        } catch (RemoteException e) {
            return handleRemoteExceptionFromCarService(e, null);
        }

    }

    /**
     * Returns {@code true} if the system has a driver zone. It will return false for system with
     * only passenger zones.
     *
     * <p> Note that at least one zone must be present and following system configurations are
     * possible:
     * <ul>
     *     <li>One driver zone only.
     *     <li>One driver zone with at least one passenger zone.
     *     <li>At least one passenger zone.
     * </ul>
     */
    public boolean hasDriverZone() {
        try {
            return mService.hasDriverZone();
        } catch (RemoteException e) {
            return handleRemoteExceptionFromCarService(e, false);
        }
    }

    /**
     * Returns {@code true} if the system has front or rear passenger zones. Check
     * {@link #hasDriverZone()} for possible system configurations.
     */
    public boolean hasPassengerZones() {
        try {
            return mService.hasPassengerZones();
        } catch (RemoteException e) {
            return handleRemoteExceptionFromCarService(e, false);
        }
    }

    private void handleOnOccupantZoneConfigChanged(int flags) {
        for (OccupantZoneConfigChangeListener listener : mListeners) {
            listener.onOccupantZoneConfigChanged(flags);
        }
    }

    private final class EventHandler extends Handler {
        private static final int MSG_ZONE_CHANGE = 1;

        private EventHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MSG_ZONE_CHANGE:
                    handleOnOccupantZoneConfigChanged(msg.arg1);
                    break;
                default:
                    Log.e(TAG, "Unknown msg not handdled:" + msg.what);
                    break;
            }
        }

        private void dispatchOnOccupantZoneConfigChanged(int flags) {
            sendMessage(obtainMessage(MSG_ZONE_CHANGE, flags, 0));
        }
    }

    private static class ICarOccupantZoneCallbackImpl extends ICarOccupantZoneCallback.Stub {
        private final WeakReference<CarOccupantZoneManager> mManager;

        private ICarOccupantZoneCallbackImpl(CarOccupantZoneManager manager) {
            mManager = new WeakReference<>(manager);
        }

        @Override
        public void onOccupantZoneConfigChanged(int flags) {
            CarOccupantZoneManager manager = mManager.get();
            if (manager != null) {
                manager.mEventHandler.dispatchOnOccupantZoneConfigChanged(flags);
            }
        }
    }

    /** @hide */
    @Override
    public void onCarDisconnected() {
        // nothing to do
    }

    /**
     * Returns the supported input types for the occupant zone info and display type passed as
     * the argument.
     *
     * <p>It returns an empty list if the input type is unknown. Starting in Android
     * {@link android.os.Build.VERSION_CODES#UPSIDE_DOWN_CAKE}, all associated occupant zones and
     * display types in {@code config_occupant_display_mapping} must define at least one input type.
     *
     * <p>If the display doesn't have any input type associated, then it should return a list
     * containing {@link android.car.input.CarInputManager#INPUT_TYPE_NONE} only.
     *
     * <p>This is the list of all available input types this method may return:
     * <ul>
     *   <li>{@link android.car.input.CarInputManager#INPUT_TYPE_ROTARY_NAVIGATION}</li>
     *   <li>{@link android.car.input.CarInputManager#INPUT_TYPE_ROTARY_VOLUME}</li>
     *   <li>{@link android.car.input.CarInputManager#INPUT_TYPE_DPAD_KEYS}</li>
     *   <li>{@link android.car.input.CarInputManager#INPUT_TYPE_NAVIGATE_KEYS}</li>
     *   <li>{@link android.car.input.CarInputManager#INPUT_TYPE_SYSTEM_NAVIGATE_KEYS}</li>
     *   <li>{@link android.car.input.CarInputManager#INPUT_TYPE_CUSTOM_INPUT_EVENT}</li>
     *   <li>{@link android.car.input.CarInputManager#INPUT_TYPE_TOUCH_SCREEN}</li>
     *   <li>{@link android.car.input.CarInputManager#INPUT_TYPE_NONE}</li>
     * </ul>
     *
     * @param occupantZoneInfo the occupant zone info of the supported input types to find
     * @param displayType      the display type of the supported input types to find
     * @return the supported input types for the occupant zone info and display type passed in as
     * the argument (see the full list of supported input types in the above)
     */
    @NonNull
    public List<Integer> getSupportedInputTypes(@NonNull OccupantZoneInfo occupantZoneInfo,
            @DisplayTypeEnum int displayType) {
        assertNonNullOccupant(occupantZoneInfo);
        List<Integer> inputTypes;
        try {
            int[] ints = mService.getSupportedInputTypes(occupantZoneInfo.zoneId, displayType);
            inputTypes = Lists.asImmutableList(ints);
        } catch (RemoteException e) {
            inputTypes = handleRemoteExceptionFromCarService(e, Collections.emptyList());
        }
        return inputTypes;
    }
}
