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

import static android.media.AudioManager.FLAG_FROM_KEY;
import static android.media.AudioManager.FLAG_PLAY_SOUND;
import static android.media.AudioManager.FLAG_SHOW_UI;

import static com.android.car.internal.ExcludeFromCodeCoverageGeneratedReport.BOILERPLATE_CODE;

import android.annotation.IntDef;
import android.annotation.NonNull;
import android.annotation.SystemApi;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.SparseArray;

import com.android.car.internal.ExcludeFromCodeCoverageGeneratedReport;
import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.util.Preconditions;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executor;

/**
 * Class to encapsulate car volume group event information.
 *
 * @hide
 */
@SystemApi
public final class CarVolumeGroupEvent implements Parcelable {

    /**
     * This event type indicates that the volume group gain index has changed.
     * The new gain index can be queried through
     * {@link android.car.media.CarVolumeGroupInfo#getVolumeGainIndex} on the
     * list of {@link android.car.media.CarVolumeGroupInfo} received here.
     */
    public static final int EVENT_TYPE_VOLUME_GAIN_INDEX_CHANGED = 1 << 0;

    /**
     * This event type indicates that the volume group minimum gain index has changed.
     * The new minimum gain index can be queried through
     * {@link android.car.media.CarVolumeGroupInfo#getMinVolumeGainIndex} on the
     * list of {@link android.car.media.CarVolumeGroupInfo} received here.
     */
    public static final int EVENT_TYPE_VOLUME_MIN_INDEX_CHANGED = 1 << 1;

    /**
     * This event type indicates that the volume group maximum gain index has changed.
     * The new maximum gain index can be queried through
     * {@link android.car.media.CarVolumeGroupInfo#getMaxVolumeGainIndex} on the
     * list of {@link android.car.media.CarVolumeGroupInfo} received here.
     */
    public static final int EVENT_TYPE_VOLUME_MAX_INDEX_CHANGED = 1 << 2;

    /**
     * This event type indicates that the volume group mute state changed or that the volume
     * group is muted by system while trying to unmute it. The new mute state can be queried
     * through {@link android.car.media.CarVolumeGroupInfo#isMuted} on the
     * list of {@link android.car.media.CarVolumeGroupInfo} received here.
     *
     * <p>Mute state can be changed from key event, API call, or system.
     */
    public static final int EVENT_TYPE_MUTE_CHANGED = 1 << 3;

    /**
     * This event type indicates that the volume group blocked state has changed.
     * The new state can be queried through
     * {@link android.car.media.CarVolumeGroupInfo#isBlocked} on the
     * list of {@link android.car.media.CarVolumeGroupInfo} received here.
     *
     * <p><b> Note: </b> When the volume group is blocked, the car audio framework may
     * reject incoming volume and mute change requests from the users.
     */
    public static final int EVENT_TYPE_VOLUME_BLOCKED_CHANGED = 1 << 4;

    /**
     * This event type indicates that the volume group attenuation state has changed.
     * The new state can be queried through
     * {@link android.car.media.CarVolumeGroupInfo#isAttenuated} on the
     * list of {@link android.car.media.CarVolumeGroupInfo} received here.
     *
     * <p> <b> Note: </b> The attenuation could be transient or permanent. More
     * context can be obtained from the included extra information.
     */
    public static final int EVENT_TYPE_ATTENUATION_CHANGED = 1 << 5;

    /**
     * This event type indicates that the car audio zone configuration of the volume group has
     * switched by {@link CarAudioManager#switchAudioZoneToConfig(CarAudioZoneConfigInfo, Executor,
     * SwitchAudioZoneConfigCallback)}. The new audio attributes can be queried through
     * {@link android.car.media.CarVolumeGroupInfo#getAudioAttributes()} on the
     * list of {@link android.car.media.CarVolumeGroupInfo} received here.
     *
     * <p><b> Note: </b> When the car audio zone configuration is switched, the volume groups
     * received here are completely new.
     */
    public static final int EVENT_TYPE_ZONE_CONFIGURATION_CHANGED = 1 << 6;

    /** @hide */
    @Retention(RetentionPolicy.SOURCE)
    @IntDef(flag = true, prefix = "EVENT_TYPE", value = {
            EVENT_TYPE_VOLUME_GAIN_INDEX_CHANGED,
            EVENT_TYPE_VOLUME_MIN_INDEX_CHANGED,
            EVENT_TYPE_VOLUME_MAX_INDEX_CHANGED,
            EVENT_TYPE_MUTE_CHANGED,
            EVENT_TYPE_VOLUME_BLOCKED_CHANGED,
            EVENT_TYPE_ATTENUATION_CHANGED,
            EVENT_TYPE_ZONE_CONFIGURATION_CHANGED,
    })
    public @interface EventTypeEnum {}

    /**
     * No additional information available
     */
    public static final int EXTRA_INFO_NONE = 100;

    /**
     * Indicates volume index changed by Car UI or other user facing apps
     */
    public static final int EXTRA_INFO_VOLUME_INDEX_CHANGED_BY_UI = 101;

    /**
     * Indicates volume index changed by keyevents from volume knob, steering wheel keys
     * etc. Equivalent to {@link android.media.AudioManager#FLAG_FROM_KEY} but specifically
     * for volume index changes.
     */
    public static final int EXTRA_INFO_VOLUME_INDEX_CHANGED_BY_KEYEVENT = 102;

    /**
     * Indicates volume index changed by the audio system (example - external amplifier)
     * asynchronously. This is typically in response to volume change requests from
     * car audio framework and needed to maintain sync.
     */
    public static final int EXTRA_INFO_VOLUME_INDEX_CHANGED_BY_AUDIO_SYSTEM = 103;

    /**
     * Indicates volume is attenuated due to min/max activation limits set by the OEM.
     *
     * <p>Some examples:
     * <ul>
     *     <li>Current media volume level is higher than allowed maximum activation volume</li>
     *     <li>Current call volume level is lower than expected minimum activation volume</li>
     * </ul>
     */
    public static final int EXTRA_INFO_ATTENUATION_ACTIVATION = 110;

    /**
     * Indicates volume is attenuated due to thermal throttling (overheating of amplifier
     * etc).
     */
    public static final int EXTRA_INFO_TRANSIENT_ATTENUATION_THERMAL = 120;

    /**
     * Indicates volume is temporarily attenuated due to active ducking (general).
     */
    public static final int EXTRA_INFO_TRANSIENT_ATTENUATION_DUCKED = 121;

    /**
     * Indicates volume is temporarily attenuated due to ducking initiated by
     * projection services.
     */
    public static final int EXTRA_INFO_TRANSIENT_ATTENUATION_PROJECTION = 122;

    /**
     * Indicates volume (typically for Media) is temporarily attenuated due to ducking for
     * navigation usecases.
     */
    public static final int EXTRA_INFO_TRANSIENT_ATTENUATION_NAVIGATION = 123;

    /**
     * Indicates volume is temporarily attenuated due to external (example: ADAS) events
     */
    public static final int EXTRA_INFO_TRANSIENT_ATTENUATION_EXTERNAL = 124;

    /**
     * Indicates volume group mute toggled by UI
     */
    public static final int EXTRA_INFO_MUTE_TOGGLED_BY_UI = 200;

    /**
     * Indicates volume group mute toggled by keyevent (example - volume knob, steering wheel keys
     * etc). Equivalent to {@link android.media.AudioManager#FLAG_FROM_KEY} but specifically
     * for mute toggle.
     */
    public static final int EXTRA_INFO_MUTE_TOGGLED_BY_KEYEVENT = 201;

    /**
     * Indicates volume group mute toggled by TCU or due to emergency event
     * (example: European eCall) in progress
     */
    public static final int EXTRA_INFO_MUTE_TOGGLED_BY_EMERGENCY = 202;

    /**
     * Indicates volume group mute toggled by the audio system. This could be due to
     * its internal states (shutdown, restart, recovery, sw update etc) or other concurrent high
     * prority audio activity.
     */
    public static final int EXTRA_INFO_MUTE_TOGGLED_BY_AUDIO_SYSTEM = 203;

    /**
     * Indicates volume group mute is locked
     * <p> <b>Note:</b> such a state may result in rejection of changes by the user
     */
    public static final int EXTRA_INFO_MUTE_LOCKED = 210;

    /**
     * Indicates that the client should show an UI for the event(s). Equivalent to
     * {@link android.media.AudioManager#FLAG_SHOW_UI}
     */
    public static final int EXTRA_INFO_SHOW_UI = 300;

    /**
     * Indicates that the client should play sound for the event(s). Equivalent to
     * {@link android.media.AudioManager#FLAG_PLAY_SOUND}
     */
    public static final int EXTRA_INFO_PLAY_SOUND = 301;

    private final @EventTypeEnum int mEventTypes;
    private final @NonNull List<Integer> mExtraInfos;
    private final @NonNull List<CarVolumeGroupInfo> mCarVolumeGroupInfos;

    private CarVolumeGroupEvent(@NonNull List<CarVolumeGroupInfo> volumeGroupInfos,
                                @EventTypeEnum int eventTypes,
                                @NonNull List<Integer> extraInfos) {
        this.mCarVolumeGroupInfos = Objects.requireNonNull(volumeGroupInfos,
                "Volume group infos can not be null");
        this.mExtraInfos = Objects.requireNonNull(extraInfos, "Extra infos can not be null");
        this.mEventTypes = eventTypes;
    }

    /**
     * Returns the list of {@link android.car.media.CarVolumeGroupInfo} that have changed.
     *
     * @return list of updated {@link android.car.media.CarVolumeGroupInfo}
     */
    public @NonNull List<CarVolumeGroupInfo> getCarVolumeGroupInfos() {
        return List.copyOf(mCarVolumeGroupInfos);
    }

    /**
     * Returns the event types flag
     *
     * <p>Conveys information on "what has changed". {@code EventTypesEnum}
     * can be used as a flag and supports bitwise operations.
     *
     * @return one or more {@code EventTypesEnum}. The returned value can be a combination
     *         of {@link #EVENT_TYPE_VOLUME_GAIN_INDEX_CHANGED},
     *         {@link #EVENT_TYPE_VOLUME_MIN_INDEX_CHANGED},
     *         {@link #EVENT_TYPE_VOLUME_MAX_INDEX_CHANGED},
     *         {@link #EVENT_TYPE_MUTE_CHANGED},
     *         {@link #EVENT_TYPE_VOLUME_BLOCKED_CHANGED},
     *         {@link #EVENT_TYPE_ATTENUATION_CHANGED}
     *         {@link #EVENT_TYPE_ZONE_CONFIGURATION_CHANGED}
     */
    @EventTypeEnum
    public int getEventTypes() {
        return mEventTypes;
    }

    /**
     * Returns list of extra/additional information related to the event types.
     *
     * <p>Conveys information on "why it has changed". This can be used by the client
     * to provide context to the user. It is expected that OEMs will customize the behavior
     * as they see fit. Some examples:
     * <ul>
     *     <li>On {@link #EXTRA_INFO_TRANSIENT_ATTENUATION_THERMAL} the client may notify
     *     the user that the volume is attenuated due to overheating of audio amplifier.</li>
     *     <li>On {@link #EXTRA_INFO_TRANSIENT_ATTENUATION_NAVIGATION} the client may initially
     *     gray out the volume bar with a toast message to inform the user the volume group is
     *     currently ducked.</li>
     *     <li>On {@link #EXTRA_INFO_MUTE_TOGGLED_BY_EMERGENCY} the client may notify the user
     *     that the volume group is muted due to concurrent emergency audio activity.</li>
     * </ul>
     *
     * @return list of extra info. The returned value can be {@link #EXTRA_INFO_NONE} or
     *         a list of {@link #EXTRA_INFO_VOLUME_INDEX_CHANGED_BY_UI},
     *         {@link #EXTRA_INFO_VOLUME_INDEX_CHANGED_BY_KEYEVENT},
     *         {@link #EXTRA_INFO_VOLUME_INDEX_CHANGED_BY_AUDIO_SYSTEM}
     *         {@link #EXTRA_INFO_ATTENUATION_ACTIVATION},
     *         {@link #EXTRA_INFO_TRANSIENT_ATTENUATION_THERMAL},
     *         {@link #EXTRA_INFO_TRANSIENT_ATTENUATION_DUCKED},
     *         {@link #EXTRA_INFO_TRANSIENT_ATTENUATION_PROJECTION},
     *         {@link #EXTRA_INFO_TRANSIENT_ATTENUATION_NAVIGATION},
     *         {@link #EXTRA_INFO_TRANSIENT_ATTENUATION_EXTERNAL},
     *         {@link #EXTRA_INFO_MUTE_TOGGLED_BY_UI},
     *         {@link #EXTRA_INFO_MUTE_TOGGLED_BY_KEYEVENT},
     *         {@link #EXTRA_INFO_MUTE_TOGGLED_BY_EMERGENCY},
     *         {@link #EXTRA_INFO_MUTE_TOGGLED_BY_AUDIO_SYSTEM},
     *         {@link #EXTRA_INFO_MUTE_LOCKED},
     *         {@link #EXTRA_INFO_SHOW_UI},
     *         {@link #EXTRA_INFO_PLAY_SOUND}
     */
    public @NonNull List<Integer> getExtraInfos() {
        return List.copyOf(mExtraInfos);
    }

    /**
     * Converts the list of extra info into flags.
     *
     * <p><b>Note:</b> Not all values of extra info can be converted into
     * {@link android.media.AudioManager#Flags}.
     *
     * @param extraInfos  list of extra info
     * @return flags One or more flags @link android.media.AudioManager#FLAG_SHOW_UI},
     *         {@link android.media.AudioManager#FLAG_PLAY_SOUND},
     *         {@link android.media.AudioManager#FLAG_FROM_KEY}
     */
    public static int convertExtraInfoToFlags(@NonNull List<Integer> extraInfos) {
        int flags = 0;
        if (extraInfos.contains(EXTRA_INFO_SHOW_UI)) {
            flags |= FLAG_SHOW_UI;
        }
        if (extraInfos.contains(EXTRA_INFO_PLAY_SOUND)) {
            flags |= FLAG_PLAY_SOUND;
        }
        if (extraInfos.contains(EXTRA_INFO_VOLUME_INDEX_CHANGED_BY_KEYEVENT)
                || extraInfos.contains(EXTRA_INFO_MUTE_TOGGLED_BY_KEYEVENT)) {
            flags |= FLAG_FROM_KEY;
        }
        return flags;
    }

    /**
     * Converts flags into extra info.
     *
     * <p><b>Note:</b> Not all extra info can be converted into flags.
     *
     * @param flags one or more flags.
     * @param eventTypes one or more event types.
     * @return list of extra info. The returned value can be {@link #EXTRA_INFO_NONE} or
     *         a list of {@link #EXTRA_INFO_VOLUME_INDEX_CHANGED_BY_KEYEVENT},
     *         {@link #EXTRA_INFO_MUTE_TOGGLED_BY_KEYEVENT},
     *         {@link #EXTRA_INFO_SHOW_UI},
     *         {@link #EXTRA_INFO_PLAY_SOUND}
     */
    @NonNull
    public static List<Integer> convertFlagsToExtraInfo(int flags, int eventTypes) {
        List<Integer> extraInfos = new ArrayList<>();

        if ((flags & FLAG_SHOW_UI) != 0) {
            extraInfos.add(EXTRA_INFO_SHOW_UI);
        }

        if ((flags & FLAG_PLAY_SOUND) != 0) {
            extraInfos.add(EXTRA_INFO_PLAY_SOUND);
        }

        if ((flags & FLAG_FROM_KEY) != 0) {
            if ((eventTypes & EVENT_TYPE_MUTE_CHANGED) != 0) {
                extraInfos.add(EXTRA_INFO_MUTE_TOGGLED_BY_KEYEVENT);
            } else if ((eventTypes & EVENT_TYPE_VOLUME_GAIN_INDEX_CHANGED) != 0) {
                extraInfos.add(EXTRA_INFO_VOLUME_INDEX_CHANGED_BY_KEYEVENT);
            }
        }

        if (extraInfos.isEmpty()) {
            extraInfos.add(EXTRA_INFO_NONE);
        }

        return extraInfos;
    }

    private static final SparseArray<String> EVENT_TYPE_NAMES = new SparseArray<>();

    static {
        EVENT_TYPE_NAMES.put(EVENT_TYPE_VOLUME_GAIN_INDEX_CHANGED,
                "EVENT_TYPE_VOLUME_GAIN_INDEX_CHANGED");
        EVENT_TYPE_NAMES.put(EVENT_TYPE_VOLUME_MIN_INDEX_CHANGED,
                "EVENT_TYPE_VOLUME_MIN_INDEX_CHANGED");
        EVENT_TYPE_NAMES.put(EVENT_TYPE_VOLUME_MAX_INDEX_CHANGED,
                "EVENT_TYPE_VOLUME_MAX_INDEX_CHANGED");
        EVENT_TYPE_NAMES.put(EVENT_TYPE_MUTE_CHANGED,
                "EVENT_TYPE_MUTE_CHANGED");
        EVENT_TYPE_NAMES.put(EVENT_TYPE_VOLUME_BLOCKED_CHANGED,
                "EVENT_TYPE_VOLUME_BLOCKED_CHANGED");
        EVENT_TYPE_NAMES.put(EVENT_TYPE_ATTENUATION_CHANGED,
                "EVENT_TYPE_ATTENUATION_CHANGED");
        EVENT_TYPE_NAMES.put(EVENT_TYPE_ZONE_CONFIGURATION_CHANGED,
                "EVENT_TYPE_ZONE_CONFIGURATION_CHANGED");
    }

    /**
     *  Return {@code EventTypesEnum} as a human-readable string
     *
     * @param eventTypes {@code EventTypeEnum}
     * @return human-readable string
     */
    @NonNull
    public static String eventTypeToString(@EventTypeEnum int eventTypes) {
        final StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 32; i++) {
            int eventType = eventTypes & (1 << i);
            if (eventType != 0) {
                if (sb.length() > 0) {
                    sb.append('|');
                }
                sb.append(EVENT_TYPE_NAMES.get(eventType,
                        "unknown event type: " + eventType));
            }
        }
        return sb.toString();
    }

    private static final SparseArray<String> EXTRA_INFO_NAMES = new SparseArray<>();

    static {
        EXTRA_INFO_NAMES.put(EXTRA_INFO_NONE,
                "EXTRA_INFO_NONE");
        EXTRA_INFO_NAMES.put(EXTRA_INFO_VOLUME_INDEX_CHANGED_BY_UI,
                "EXTRA_INFO_VOLUME_INDEX_CHANGED_BY_UI");
        EXTRA_INFO_NAMES.put(EXTRA_INFO_VOLUME_INDEX_CHANGED_BY_KEYEVENT,
                "EXTRA_INFO_VOLUME_INDEX_CHANGED_BY_KEYEVENT");
        EXTRA_INFO_NAMES.put(EXTRA_INFO_VOLUME_INDEX_CHANGED_BY_AUDIO_SYSTEM,
                "EXTRA_INFO_VOLUME_INDEX_CHANGED_BY_AUDIO_SYSTEM");
        EXTRA_INFO_NAMES.put(EXTRA_INFO_ATTENUATION_ACTIVATION,
                "EXTRA_INFO_ATTENUATION_ACTIVATION");
        EXTRA_INFO_NAMES.put(EXTRA_INFO_TRANSIENT_ATTENUATION_THERMAL,
                "EXTRA_INFO_TRANSIENT_ATTENUATION_THERMAL");
        EXTRA_INFO_NAMES.put(EXTRA_INFO_TRANSIENT_ATTENUATION_DUCKED,
                "EXTRA_INFO_TRANSIENT_ATTENUATION_DUCKED");
        EXTRA_INFO_NAMES.put(EXTRA_INFO_TRANSIENT_ATTENUATION_PROJECTION,
                "EXTRA_INFO_TRANSIENT_ATTENUATION_PROJECTION");
        EXTRA_INFO_NAMES.put(EXTRA_INFO_TRANSIENT_ATTENUATION_NAVIGATION,
                "EXTRA_INFO_TRANSIENT_ATTENUATION_NAVIGATION");
        EXTRA_INFO_NAMES.put(EXTRA_INFO_TRANSIENT_ATTENUATION_EXTERNAL,
                "EXTRA_INFO_TRANSIENT_ATTENUATION_EXTERNAL");
        EXTRA_INFO_NAMES.put(EXTRA_INFO_MUTE_TOGGLED_BY_UI,
                "EXTRA_INFO_MUTE_TOGGLED_BY_UI");
        EXTRA_INFO_NAMES.put(EXTRA_INFO_MUTE_TOGGLED_BY_KEYEVENT,
                "EXTRA_INFO_MUTE_TOGGLED_BY_KEYEVENT");
        EXTRA_INFO_NAMES.put(EXTRA_INFO_MUTE_TOGGLED_BY_EMERGENCY,
                "EXTRA_INFO_MUTE_TOGGLED_BY_EMERGENCY");
        EXTRA_INFO_NAMES.put(EXTRA_INFO_MUTE_TOGGLED_BY_AUDIO_SYSTEM,
                "EXTRA_INFO_MUTE_TOGGLED_BY_AUDIO_SYSTEM");
        EXTRA_INFO_NAMES.put(EXTRA_INFO_MUTE_LOCKED,
                "EXTRA_INFO_MUTE_LOCKED");
        EXTRA_INFO_NAMES.put(EXTRA_INFO_SHOW_UI,
                "EXTRA_INFO_SHOW_UI");
        EXTRA_INFO_NAMES.put(EXTRA_INFO_PLAY_SOUND,
                "EXTRA_INFO_PLAY_SOUND");
    }

    /**
     * Returns list of extra-infos as human-readable string
     *
     * @param extraInfos list of extra-info
     * @return human-readable string
     */
    @NonNull
    public static String extraInfosToString(@NonNull List<Integer> extraInfos) {
        final StringBuilder sb = new StringBuilder();
        for (int extraInfo : extraInfos) {
            if (sb.length() > 0) {
                sb.append(',');
            }
            sb.append(EXTRA_INFO_NAMES.get(extraInfo,
                    "unknown extra info: " + extraInfo));
        }
        return sb.toString();
    }

    @Override
    public String toString() {
        return new StringBuilder().append("CarVolumeGroupEvent { mCarVolumeGroupInfos = ")
                .append(mCarVolumeGroupInfos)
                .append(", mEventTypes = ").append(eventTypeToString(mEventTypes))
                .append(", mExtraInfos = ").append(extraInfosToString(mExtraInfos))
                .append(" }").toString();
    }

    @ExcludeFromCodeCoverageGeneratedReport(reason = BOILERPLATE_CODE)
    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        dest.writeParcelableList(mCarVolumeGroupInfos, flags);
        dest.writeInt(mEventTypes);
        dest.writeList(mExtraInfos);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }

        if (!(o instanceof CarVolumeGroupEvent)) {
            return false;
        }

        CarVolumeGroupEvent rhs = (CarVolumeGroupEvent) o;

        return mCarVolumeGroupInfos.equals(rhs.mCarVolumeGroupInfos)
                && (mEventTypes == rhs.mEventTypes)
                && mExtraInfos.equals(rhs.mExtraInfos);
    }

    /**
     * Creates volume group event from parcel
     *
     * @hide
     */
    @VisibleForTesting
    public CarVolumeGroupEvent(Parcel in) {
        List<CarVolumeGroupInfo> volumeGroupInfos = new ArrayList<>();
        in.readParcelableList(volumeGroupInfos, CarVolumeGroupInfo.class.getClassLoader(),
                CarVolumeGroupInfo.class);
        int eventTypes = in.readInt();
        List<Integer> extraInfos = new ArrayList<>();
        in.readList(extraInfos, Integer.class.getClassLoader(), java.lang.Integer.class);
        this.mCarVolumeGroupInfos = volumeGroupInfos;
        this.mEventTypes = eventTypes;
        this.mExtraInfos = extraInfos;
    }

    @NonNull
    public static final Creator<CarVolumeGroupEvent> CREATOR = new Creator<>() {
        @Override
        @NonNull
        public CarVolumeGroupEvent createFromParcel(@NonNull Parcel in) {
            return new CarVolumeGroupEvent(in);
        }

        @Override
        @NonNull
        public CarVolumeGroupEvent[] newArray(int size) {
            return new CarVolumeGroupEvent[size];
        }
    };

    @Override
    public int hashCode() {
        return Objects.hash(mCarVolumeGroupInfos, mEventTypes, mExtraInfos);
    }

    /**
     * A builder for {@link CarVolumeGroupEvent}
     */
    @SuppressWarnings("WeakerAccess")
    public static final class Builder {
        private static final long IS_USED_FIELD_SET = 0x01;
        private @NonNull List<CarVolumeGroupInfo> mCarVolumeGroupInfos;
        private @EventTypeEnum int mEventTypes;
        private @NonNull List<Integer> mExtraInfos;

        private long mBuilderFieldsSet;

        public Builder(@NonNull List<CarVolumeGroupInfo> volumeGroupInfos,
                       @EventTypeEnum int eventTypes) {
            Preconditions.checkArgument(volumeGroupInfos != null,
                    "Volume group infos can not be null");
            mCarVolumeGroupInfos = volumeGroupInfos;
            mEventTypes = eventTypes;
        }

        public Builder(@NonNull List<CarVolumeGroupInfo> volumeGroupInfos,
                       @EventTypeEnum int eventTypes,
                       @NonNull List<Integer> extraInfos) {
            Preconditions.checkArgument(volumeGroupInfos != null,
                    "Volume group infos can not be null");
            Preconditions.checkArgument(extraInfos != null, "Extra infos can not be null");
            // TODO (b/261647905) validate extra infos, make sure EXTRA_INFO_NONE
            //  is not part of list
            mCarVolumeGroupInfos = volumeGroupInfos;
            mEventTypes = eventTypes;
            mExtraInfos = extraInfos;
        }

        /** @see CarVolumeGroupEvent#getCarVolumeGroupInfos() **/
        @NonNull
        public Builder addCarVolumeGroupInfo(@NonNull CarVolumeGroupInfo volumeGroupInfo) {
            Preconditions.checkArgument(volumeGroupInfo != null,
                    "Volume group info can not be null");
            mCarVolumeGroupInfos.add(volumeGroupInfo);
            return this;
        }

        /** @see CarVolumeGroupEvent#getEventTypes()  **/
        @NonNull
        public Builder addEventType(@EventTypeEnum int eventType) {
            mEventTypes |= eventType;
            return this;
        }

        /** @see CarVolumeGroupEvent#getExtraInfos **/
        @NonNull
        public Builder setExtraInfos(@NonNull List<Integer> extraInfos) {
            Preconditions.checkArgument(extraInfos != null, "Extra infos can not be null");
            mExtraInfos = extraInfos;
            return this;
        }

        /** @see #setExtraInfos(List)  **/
        @NonNull
        public Builder addExtraInfo(int extraInfo) {
            if (mExtraInfos == null) {
                setExtraInfos(new ArrayList<>());
            }
            // TODO (b/261647905) validate extra infos, make sure EXTRA_INFO_NONE
            //  is not part of list
            if (!mExtraInfos.contains(extraInfo)) {
                mExtraInfos.add(extraInfo);
            }
            return this;
        }

        /** Builds the instance. This builder should not be touched after calling this! */
        @NonNull
        public CarVolumeGroupEvent build() {
            checkNotUsed();
            mBuilderFieldsSet |= IS_USED_FIELD_SET; // Mark builder used
            // mark as EXTRA_INFO_NONE if none is available
            if (mExtraInfos == null) {
                mExtraInfos = List.of(EXTRA_INFO_NONE);
            }

            return new CarVolumeGroupEvent(mCarVolumeGroupInfos, mEventTypes, mExtraInfos);
        }

        private void checkNotUsed() throws IllegalStateException {
            if ((mBuilderFieldsSet & IS_USED_FIELD_SET) != 0) {
                throw new IllegalStateException(
                        "This Builder should not be reused. Use a new Builder instance instead");
            }
        }
    }
}
