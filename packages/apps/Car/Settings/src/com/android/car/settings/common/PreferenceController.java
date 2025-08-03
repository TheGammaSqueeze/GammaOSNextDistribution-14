/*
 * Copyright 2018 The Android Open Source Project
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

package com.android.car.settings.common;

import static com.android.car.settings.common.PreferenceXmlParser.PREF_AVAILABILITY_STATUS_HIDDEN;
import static com.android.car.settings.common.PreferenceXmlParser.PREF_AVAILABILITY_STATUS_READ;

import android.car.CarOccupantZoneManager;
import android.car.drivingstate.CarUxRestrictions;
import android.car.drivingstate.CarUxRestrictionsManager.OnUxRestrictionsChangedListener;
import android.content.Context;
import android.os.SystemClock;
import android.widget.Toast;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.LifecycleOwner;
import androidx.preference.Preference;
import androidx.preference.PreferenceGroup;

import com.android.car.settings.CarSettingsApplication;
import com.android.car.settings.R;
import com.android.car.ui.preference.ClickableWhileDisabledPreference;
import com.android.car.ui.preference.UxRestrictablePreference;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Controller which encapsulates the business logic associated with a {@link Preference}. All car
 * settings controllers should extend this class.
 *
 * <p>Controllers are responsible for populating and modifying the presentation of an associated
 * preference while responding to changes in system state. This is enabled via {@link
 * SettingsFragment} which registers controllers as observers on its lifecycle and dispatches
 * {@link CarUxRestrictions} change events to the controllers via the {@link
 * OnUxRestrictionsChangedListener} interface.
 *
 * <p>Controllers should be instantiated from XML. To do so, define a preference and include the
 * {@code controller} attribute in the preference tag and assign the fully qualified class name.
 *
 * <p>For example:
 * <pre>{@code
 * <Preference
 *     android:key="my_preference_key"
 *     android:title="@string/my_preference_title"
 *     android:icon="@drawable/ic_settings"
 *     android:fragment="com.android.settings.foo.MyFragment"
 *     settings:controller="com.android.settings.foo.MyPreferenceController"/>
 * }</pre>
 *
 * <p>Subclasses must implement {@link #getPreferenceType()} to define the upper bound type on the
 * {@link Preference} that the controller is associated with. For example, a bound of {@link
 * androidx.preference.PreferenceGroup} indicates that the controller will utilize preference group
 * methods in its operation. {@link #setPreference(Preference)} will throw an {@link
 * IllegalArgumentException} if not passed a subclass of the upper bound type.
 *
 * <p>Subclasses may implement any or all of the following methods (see method Javadocs for more
 * information):
 *
 * <ul>
 * <li>{@link #checkInitialized()}
 * <li>{@link #onCreateInternal()}
 * <li>{@link #getAvailabilityStatus()}
 * <li>{@link #onStartInternal()}
 * <li>{@link #onResumeInternal()}
 * <li>{@link #onPauseInternal()}
 * <li>{@link #onStopInternal()}
 * <li>{@link #onDestroyInternal()}
 * <li>{@link #updateState(Preference)}
 * <li>{@link #onApplyUxRestrictions(CarUxRestrictions)}
 * <li>{@link #handlePreferenceChanged(Preference, Object)}
 * <li>{@link #handlePreferenceClicked(Preference)}
 * </ul>
 *
 * @param <V> the upper bound on the type of {@link Preference} on which the controller
 *            expects to operate.
 */
public abstract class PreferenceController<V extends Preference> implements
        DefaultLifecycleObserver,
        OnUxRestrictionsChangedListener {
    private static final Logger LOG = new Logger(PreferenceController.class);

    /**
     * Denotes the availability of a setting.
     *
     * @see #getAvailabilityStatus()
     */
    @Retention(RetentionPolicy.SOURCE)
    @IntDef({AVAILABLE, CONDITIONALLY_UNAVAILABLE, UNSUPPORTED_ON_DEVICE, DISABLED_FOR_PROFILE,
            AVAILABLE_FOR_VIEWING})
    public @interface AvailabilityStatus {
    }

    /**
     * The setting is available.
     */
    public static final int AVAILABLE = 0;

    /**
     * The setting is currently unavailable but may become available in the future. Use
     * {@link #DISABLED_FOR_PROFILE} if it describes the condition more accurately.
     */
    public static final int CONDITIONALLY_UNAVAILABLE = 1;

    /**
     * The setting is not and will not be supported by this device.
     */
    public static final int UNSUPPORTED_ON_DEVICE = 2;

    /**
     * The setting cannot be changed by the current profile.
     */
    public static final int DISABLED_FOR_PROFILE = 3;

    /**
     * The setting cannot be changed.
     */
    public static final int AVAILABLE_FOR_VIEWING = 4;

    /**
     * Denotes the availability of a setting for the current zone.
     *
     * @see #getAvailabilityStatusForZone()
     */
    @Retention(RetentionPolicy.SOURCE)
    @IntDef({AVAILABLE_FOR_ZONE, AVAILABLE_FOR_VIEWING_FOR_ZONE, HIDDEN_FOR_ZONE})
    public @interface AvailabilityStatusForZone {
    }

    /**
     * The setting is available for this zone
     */
    public static final int AVAILABLE_FOR_ZONE = 0;
    /**
     * The setting cannot be changed for this zone
     */
    public static final int AVAILABLE_FOR_VIEWING_FOR_ZONE = 1;
    /**
     * The setting is hidden for this zone.
     */
    public static final int HIDDEN_FOR_ZONE = 2;

    /**
     * Indicates whether all Preferences are configured to ignore UX Restrictions Event.
     */
    private final boolean mAlwaysIgnoreUxRestrictions;

    /**
     * Set of the keys of Preferences that ignore UX Restrictions. When mAlwaysIgnoreUxRestrictions
     * is configured to be false, then only the Preferences whose keys are contained in this Set
     * ignore UX Restrictions.
     */
    private final Set<String> mPreferencesIgnoringUxRestrictions;

    private final Context mContext;
    private final String mPreferenceKey;
    private final FragmentController mFragmentController;
    private final String mRestrictedWhileDrivingMessage;
    private final String mRestrictedForDriversMessage;
    private final String mRestrictedForPassengersMessage;
    private final int mDebounceIntervalMs;

    private CarUxRestrictions mUxRestrictions;
    private V mPreference;
    private boolean mIsCreated;
    private boolean mIsStarted;
    private long mDebounceStartTimeMs;
    private int mAvailabilityStatusForZone;

    /**
     * Controllers should be instantiated from XML. To pass additional arguments see
     * {@link SettingsFragment#use(Class, int)}.
     */
    public PreferenceController(Context context, String preferenceKey,
            FragmentController fragmentController, CarUxRestrictions uxRestrictions) {
        mContext = context;
        mPreferenceKey = preferenceKey;
        mFragmentController = fragmentController;
        mUxRestrictions = uxRestrictions;
        mPreferencesIgnoringUxRestrictions = new HashSet<String>(Arrays.asList(
                mContext.getResources().getStringArray(R.array.config_ignore_ux_restrictions)));
        mAlwaysIgnoreUxRestrictions =
                mContext.getResources().getBoolean(R.bool.config_always_ignore_ux_restrictions);
        mRestrictedWhileDrivingMessage =
                mContext.getResources().getString(R.string.car_ui_restricted_while_driving);
        mRestrictedForDriversMessage =
                mContext.getResources().getString(R.string.restricted_for_driver);
        mRestrictedForPassengersMessage =
                mContext.getResources().getString(R.string.restricted_for_passenger);
        mDebounceIntervalMs =
                mContext.getResources().getInteger(R.integer.config_preference_onclick_debounce_ms);
    }

    /**
     * Sets the setting's availabilityStatus for this zone.
     * Defaults to {@link #AVAILABLE_FOR_ZONE}.
     */
    @VisibleForTesting(otherwise = VisibleForTesting.PROTECTED)
    public final void setAvailabilityStatusForZone(@Nullable String availabilityStatusForZone) {
        if (PREF_AVAILABILITY_STATUS_READ.equals(availabilityStatusForZone)) {
            mAvailabilityStatusForZone = AVAILABLE_FOR_VIEWING_FOR_ZONE;
        } else if (PREF_AVAILABILITY_STATUS_HIDDEN.equals(availabilityStatusForZone)) {
            mAvailabilityStatusForZone = HIDDEN_FOR_ZONE;
        } else {
            mAvailabilityStatusForZone = AVAILABLE_FOR_ZONE;
        }
    }

    /**
     * Returns the context used to construct the controller.
     */
    protected final Context getContext() {
        return mContext;
    }

    /**
     * Returns the key for the preference managed by this controller set at construction.
     */
    protected final String getPreferenceKey() {
        return mPreferenceKey;
    }

    /**
     * Returns the {@link FragmentController} used to launch fragments and go back to previous
     * fragments. This is set at construction.
     */
    protected final FragmentController getFragmentController() {
        return mFragmentController;
    }

    /**
     * Returns the current {@link CarUxRestrictions} applied to the controller. Subclasses may use
     * this to limit which content is displayed in the associated preference. May be called anytime.
     */
    protected final CarUxRestrictions getUxRestrictions() {
        return mUxRestrictions;
    }

    /**
     * Returns the preference associated with this controller. This may be used in any of the
     * lifecycle methods, as the preference is set before they are called..
     */
    protected final V getPreference() {
        return mPreference;
    }

    /**
     * Called by {@link SettingsFragment} to associate the controller with its preference after the
     * screen is created. This is guaranteed to be called before {@link #onCreateInternal()}.
     *
     * @throws IllegalArgumentException if the given preference does not match the type
     *                                  returned by {@link #getPreferenceType()}
     * @throws IllegalStateException    if subclass defined initialization is not
     *                                  complete.
     */
    final void setPreference(Preference preference) {
        PreferenceUtil.requirePreferenceType(preference, getPreferenceType());
        mPreference = getPreferenceType().cast(preference);
        mPreference.setOnPreferenceChangeListener(
                (changedPref, newValue) -> handlePreferenceChanged(
                        getPreferenceType().cast(changedPref), newValue));
        mPreference.setOnPreferenceClickListener(
                clickedPref -> {
                    // Debounce onClick() calls
                    long curTime = SystemClock.elapsedRealtime();
                    if (mDebounceStartTimeMs != 0
                            && curTime < (mDebounceStartTimeMs + mDebounceIntervalMs)) {
                        LOG.i("OnClick event dropped due to debouncing");
                        return true;
                    }
                    mDebounceStartTimeMs = curTime;
                    return handlePreferenceClicked(getPreferenceType().cast(clickedPref));
                });
        checkInitialized();
    }

    /**
     * Called by {@link SettingsFragment} to notify that the applied ux restrictions have changed.
     * The controller will refresh its UI accordingly unless it is not yet created. In that case,
     * the UI will refresh once created.
     */
    @Override
    public final void onUxRestrictionsChanged(CarUxRestrictions uxRestrictions) {
        mUxRestrictions = uxRestrictions;
        refreshUi();
    }

    /**
     * Updates the preference presentation based on its {@link #getAvailabilityStatus()} status. If
     * the controller is available, the associated preference is shown and a call to {@link
     * #updateState(Preference)} and {@link #onApplyUxRestrictions(CarUxRestrictions)} are
     * dispatched to allow the controller to modify the presentation for the current state. If the
     * controller is not available, the associated preference is hidden from the screen. This is a
     * no-op if the controller is not yet created.
     */
    public final void refreshUi() {
        if (!mIsCreated) {
            return;
        }

        if (isAvailable()) {
            mPreference.setVisible(true);
            mPreference.setEnabled(getAvailabilityStatus() != AVAILABLE_FOR_VIEWING);
            updateState(mPreference);
            onApplyUxRestrictions(mUxRestrictions);
        } else {
            mPreference.setVisible(false);
        }
    }

    private boolean isAvailable() {
        int availabilityStatus = getAvailabilityStatus();
        return availabilityStatus == AVAILABLE || availabilityStatus == AVAILABLE_FOR_VIEWING;
    }

    // Controller lifecycle ========================================================================

    /**
     * Dispatches a call to {@link #onCreateInternal()} and {@link #refreshUi()} to enable
     * controllers to setup initial state before a preference is visible. If the controller is
     * {@link #UNSUPPORTED_ON_DEVICE}, the preference is hidden and no further action is taken.
     */
    @Override
    public final void onCreate(@NonNull LifecycleOwner owner) {
        if (getAvailabilityStatus() == UNSUPPORTED_ON_DEVICE) {
            mPreference.setVisible(false);
            return;
        }
        onCreateInternal();
        if (isPreferenceDisabledForZone()) {
            setClickableWhileDisabledInternal(getPreference(), /* clickable= */ true,
                    getZoneDisabledPreferenceOnClick());
        }
        mIsCreated = true;
        refreshUi();
    }

    /**
     * Dispatches a call to {@link #onStartInternal()} and {@link #refreshUi()} to account for any
     * state changes that may have occurred while the controller was stopped. Returns immediately
     * if the controller is {@link #UNSUPPORTED_ON_DEVICE}.
     */
    @Override
    public final void onStart(@NonNull LifecycleOwner owner) {
        if (getAvailabilityStatus() == UNSUPPORTED_ON_DEVICE) {
            return;
        }
        onStartInternal();
        mIsStarted = true;
        refreshUi();
    }

    /**
     * Notifies that the controller is resumed by dispatching a call to {@link #onResumeInternal()}.
     * Returns immediately if the controller is {@link #UNSUPPORTED_ON_DEVICE}.
     */
    @Override
    public final void onResume(@NonNull LifecycleOwner owner) {
        if (getAvailabilityStatus() == UNSUPPORTED_ON_DEVICE) {
            return;
        }
        onResumeInternal();
    }

    /**
     * Notifies that the controller is paused by dispatching a call to {@link #onPauseInternal()}.
     * Returns immediately if the controller is {@link #UNSUPPORTED_ON_DEVICE}.
     */
    @Override
    public final void onPause(@NonNull LifecycleOwner owner) {
        if (getAvailabilityStatus() == UNSUPPORTED_ON_DEVICE) {
            return;
        }
        onPauseInternal();
    }

    /**
     * Notifies that the controller is stopped by dispatching a call to {@link #onStopInternal()}.
     * Returns immediately if the controller is {@link #UNSUPPORTED_ON_DEVICE}.
     */
    @Override
    public final void onStop(@NonNull LifecycleOwner owner) {
        if (getAvailabilityStatus() == UNSUPPORTED_ON_DEVICE) {
            return;
        }
        mIsStarted = false;
        onStopInternal();
    }

    /**
     * Notifies that the controller is destroyed by dispatching a call to {@link
     * #onDestroyInternal()}. Returns immediately if the controller is
     * {@link #UNSUPPORTED_ON_DEVICE}.
     */
    @Override
    public final void onDestroy(@NonNull LifecycleOwner owner) {
        if (getAvailabilityStatus() == UNSUPPORTED_ON_DEVICE) {
            return;
        }
        mIsCreated = false;
        onDestroyInternal();
    }

    // Methods for override ========================================================================

    /**
     * Returns the upper bound type of the preference on which this controller will operate.
     */
    protected abstract Class<V> getPreferenceType();

    /**
     * Subclasses may override this method to throw {@link IllegalStateException} if any expected
     * post-instantiation setup is not completed using {@link SettingsFragment#use(Class, int)}
     * prior to associating the controller with its preference. This will be called before the
     * controller lifecycle begins.
     */
    protected void checkInitialized() {
    }

    /**
     * Returns the {@link AvailabilityStatus} for the setting. This status is used as the final
     * result to determine if the setting should be shown, hidden, or disabled. Defaults to
     * {@link #AVAILABLE}. It is determined by considering the return value of
     * {@link #getDefaultAvailabilityStatus()} and the availabilityStatus for zone with and the
     * availabilityStatus for the current CarOccupantZone of the display where Settings are shown.
     * This will be called before the controller lifecycle begins and on refresh events.
     */
    @AvailabilityStatus
    @VisibleForTesting(otherwise = VisibleForTesting.PROTECTED)
    public final int getAvailabilityStatus() {
        int defaultStatus = getDefaultAvailabilityStatus();
        switch (defaultStatus) {
            case CONDITIONALLY_UNAVAILABLE: // fall through
            case UNSUPPORTED_ON_DEVICE: // fall through
            case DISABLED_FOR_PROFILE:
                return defaultStatus;
            case AVAILABLE_FOR_VIEWING:
                switch (mAvailabilityStatusForZone) {
                    case HIDDEN_FOR_ZONE:
                        return CONDITIONALLY_UNAVAILABLE;
                    case AVAILABLE_FOR_ZONE: // fall through
                    case AVAILABLE_FOR_VIEWING_FOR_ZONE: // fall through
                    default:
                        return AVAILABLE_FOR_VIEWING;
                }
            case AVAILABLE: // fall through
            default:
                switch (mAvailabilityStatusForZone) {
                    case AVAILABLE_FOR_VIEWING_FOR_ZONE:
                        return AVAILABLE_FOR_VIEWING;
                    case HIDDEN_FOR_ZONE:
                        return CONDITIONALLY_UNAVAILABLE;
                    case AVAILABLE_FOR_ZONE: // fall through
                    default:
                        return AVAILABLE;
                }
        }
    }

    /**
     * Returns the {@link AvailabilityStatus} for the setting. This status is used
     * with the availabilityStatus for zone within {@link #getAvailabilityStatus()} to determine
     * if the setting should be shown, hidden, or disabled according to menu settings.
     * Defaults to {@link #AVAILABLE}.
     */
    @AvailabilityStatus
    protected int getDefaultAvailabilityStatus() {
        return AVAILABLE;
    }

    /**
     * Subclasses may override this method to complete any operations needed at creation time e.g.
     * loading static configuration.
     *
     * <p>Note: this will not be called on {@link #UNSUPPORTED_ON_DEVICE} controllers.
     */
    protected void onCreateInternal() {
    }

    /**
     * Subclasses may override this method to complete any operations needed each time the
     * controller is started e.g. registering broadcast receivers.
     *
     * <p>Note: this will not be called on {@link #UNSUPPORTED_ON_DEVICE} controllers.
     */
    protected void onStartInternal() {
    }

    /**
     * Subclasses may override this method to complete any operations needed each time the
     * controller is resumed. Prefer to use {@link #onStartInternal()} unless absolutely necessary
     * as controllers may not be resumed in a multi-display scenario.
     *
     * <p>Note: this will not be called on {@link #UNSUPPORTED_ON_DEVICE} controllers.
     */
    protected void onResumeInternal() {
    }

    /**
     * Subclasses may override this method to complete any operations needed each time the
     * controller is paused. Prefer to use {@link #onStartInternal()} unless absolutely necessary
     * as controllers may not be resumed in a multi-display scenario.
     *
     * <p>Note: this will not be called on {@link #UNSUPPORTED_ON_DEVICE} controllers.
     */
    protected void onPauseInternal() {
    }

    /**
     * Subclasses may override this method to complete any operations needed each time the
     * controller is stopped e.g. unregistering broadcast receivers.
     *
     * <p>Note: this will not be called on {@link #UNSUPPORTED_ON_DEVICE} controllers.
     */
    protected void onStopInternal() {
    }

    /**
     * Subclasses may override this method to complete any operations needed when the controller is
     * destroyed e.g. freeing up held resources.
     *
     * <p>Note: this will not be called on {@link #UNSUPPORTED_ON_DEVICE} controllers.
     */
    protected void onDestroyInternal() {
    }

    /**
     * Subclasses may override this method to update the presentation of the preference for the
     * current system state (summary, switch state, etc). If the preference has dynamic content
     * (such as preferences added to a group), it may be updated here as well.
     *
     * <p>Important: Operations should be idempotent as this may be called multiple times.
     *
     * <p>Note: this will only be called when the following are true:
     * <ul>
     * <li>{@link #getAvailabilityStatus()} returns {@link #AVAILABLE}
     * <li>{@link #onCreateInternal()} has completed.
     * </ul>
     */
    protected void updateState(V preference) {
    }

    /**
     * Updates the preference enabled status given the {@code restrictionInfo}. This will be called
     * before the controller lifecycle begins and on refresh events. The preference is disabled by
     * default when {@link CarUxRestrictions#UX_RESTRICTIONS_NO_SETUP} is set in {@code
     * uxRestrictions}. Subclasses may override this method to modify enabled state based on
     * additional driving restrictions.
     */
    protected void onApplyUxRestrictions(CarUxRestrictions uxRestrictions) {
        boolean restrict = shouldApplyUxRestrictions(uxRestrictions);

        restrictPreference(mPreference, restrict);
    }

    /**
     * Decides whether or not this {@link PreferenceController} should apply {@code uxRestrictions}
     * based on the type of restrictions currently present, and the value of the {@code
     * config_always_ignore_ux_restrictions} and
     * {@code config_ignore_ux_restrictions} config flags.
     * <p>
     * It is not expected that subclasses will override this functionality. If they do, it is
     * important to respect the config flags being consulted here.
     *
     * @return true if {@code uxRestrictions} should be applied and false otherwise.
     */
    protected boolean shouldApplyUxRestrictions(CarUxRestrictions uxRestrictions) {
        return !isUxRestrictionsIgnored(mAlwaysIgnoreUxRestrictions,
                mPreferencesIgnoringUxRestrictions)
                && CarUxRestrictionsHelper.isNoSetup(uxRestrictions)
                && getAvailabilityStatus() != AVAILABLE_FOR_VIEWING;
    }

    /**
     * Updates the UxRestricted state and action for a preference. This will also update all child
     * preferences with the same state and action when {@param preference} is a PreferenceGroup.
     *
     * @param preference the preference to update
     * @param restrict whether or not the preference should be restricted
     */
    protected void restrictPreference(Preference preference, boolean restrict) {
        if (preference instanceof UxRestrictablePreference) {
            UxRestrictablePreference restrictablePreference = (UxRestrictablePreference) preference;
            restrictablePreference.setUxRestricted(restrict);
            restrictablePreference.setOnClickWhileRestrictedListener(p ->
                    Toast.makeText(mContext, mRestrictedWhileDrivingMessage,
                            Toast.LENGTH_LONG).show());
        }
        if (preference instanceof PreferenceGroup) {
            PreferenceGroup preferenceGroup = (PreferenceGroup) preference;
            for (int i = 0; i < preferenceGroup.getPreferenceCount(); i++) {
                restrictPreference(preferenceGroup.getPreference(i), restrict);
            }
        }
    }

    /**
     * Updates the clickable while disabled state and action for a preference. This will also
     * update all child preferences with the same state and action when {@param preference}
     * is a PreferenceGroup. If the preference is only available for viewing for the zone,
     * this won't apply since an action will have already been assigned.
     *
     * @param preference the preference to update
     * @param clickable whether or not the preference should be clickable when disabled
     * @param disabledClickAction the action that should be taken when clicked while disabled.
     */
    protected void setClickableWhileDisabled(Preference preference, boolean clickable,
            @Nullable Consumer<Preference> disabledClickAction) {
        // Preferences disabled for zone message has highest priority
        if (isPreferenceDisabledForZone()) {
            return;
        }
        setClickableWhileDisabledInternal(preference, clickable, disabledClickAction);
    }

    private void setClickableWhileDisabledInternal(Preference preference, boolean clickable,
            @Nullable Consumer<Preference> disabledClickAction) {
        if (preference instanceof ClickableWhileDisabledPreference) {
            ClickableWhileDisabledPreference pref = (ClickableWhileDisabledPreference) preference;
            pref.setClickableWhileDisabled(clickable);
            pref.setDisabledClickListener(disabledClickAction);
        }
        if (preference instanceof PreferenceGroup) {
            PreferenceGroup preferenceGroup = (PreferenceGroup) preference;
            for (int i = 0; i < preferenceGroup.getPreferenceCount(); i++) {
                setClickableWhileDisabledInternal(preferenceGroup.getPreference(i), clickable,
                        disabledClickAction);
            }
        }
    }

    /**
     * Called when the associated preference is changed by the user. This is called before the state
     * of the preference is updated and before the state is persisted.
     *
     * @param preference the changed preference.
     * @param newValue   the new value of the preference.
     * @return {@code true} to update the state of the preference with the new value. Defaults to
     * {@code true}.
     */
    protected boolean handlePreferenceChanged(V preference, Object newValue) {
        return true;
    }

    /**
     * Called when the preference associated with this controller is clicked. Subclasses may
     * choose to handle the click event.
     *
     * @param preference the clicked preference.
     * @return {@code true} if click is handled and further propagation should cease. Defaults to
     * {@code false}.
     */
    protected boolean handlePreferenceClicked(V preference) {
        return false;
    }

    protected boolean isUxRestrictionsIgnored(boolean allIgnores, Set prefsThatIgnore) {
        return allIgnores || prefsThatIgnore.contains(mPreferenceKey);
    }

    protected final boolean isStarted() {
        return mIsStarted;
    }

    private Consumer<Preference> getZoneDisabledPreferenceOnClick() {
        int zoneType = ((CarSettingsApplication) getContext().getApplicationContext())
                .getMyOccupantZoneType();
        String message = zoneType == CarOccupantZoneManager.OCCUPANT_TYPE_DRIVER
                ? mRestrictedForDriversMessage : mRestrictedForPassengersMessage;
        return p -> Toast.makeText(mContext, message, Toast.LENGTH_LONG).show();
    }

    private boolean isPreferenceDisabledForZone() {
        return mAvailabilityStatusForZone == AVAILABLE_FOR_VIEWING_FOR_ZONE
                && (getDefaultAvailabilityStatus() == AVAILABLE_FOR_VIEWING
                || getDefaultAvailabilityStatus() == AVAILABLE);
    }
}
