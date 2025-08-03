/*
 * Copyright (C) 2016 The Android Open Source Project
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
 * limitations under the License
 */

package com.android.tv.settings.connectivity;

import static com.android.tv.settings.overlay.FlavorUtils.FLAVOR_CLASSIC;
import static com.android.tv.settings.overlay.FlavorUtils.FLAVOR_TWO_PANEL;
import static com.android.tv.settings.overlay.FlavorUtils.FLAVOR_VENDOR;
import static com.android.tv.settings.overlay.FlavorUtils.FLAVOR_X;
import static com.android.tv.settings.util.InstrumentationUtils.logEntrySelected;
import static com.android.tv.settings.util.InstrumentationUtils.logToggleInteracted;

import android.app.tvsettings.TvSettingsEnums;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.SystemClock;
import android.os.SystemProperties;
import android.os.UserManager;
import android.provider.Settings;
import android.util.ArrayMap;
import android.view.View;

import androidx.annotation.Keep;
import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceManager;
import androidx.preference.TwoStatePreference;

import com.android.settingslib.RestrictedPreference;
import com.android.tv.settings.MainFragment;
import com.android.tv.settings.R;
import com.android.tv.settings.RestrictedPreferenceAdapter;
import com.android.tv.settings.SettingsPreferenceFragment;
import com.android.tv.settings.basic.BasicModeFeatureProvider;
import com.android.tv.settings.library.network.AccessPoint;
import com.android.tv.settings.overlay.FlavorUtils;
import com.android.tv.settings.util.SliceUtils;
import com.android.tv.settings.widget.AccessPointPreference;
import com.android.tv.settings.widget.CustomContentDescriptionSwitchPreference;
import com.android.tv.settings.widget.TvAccessPointPreference;
import com.android.tv.twopanelsettings.slices.SlicePreference;
import com.android.wifitrackerlib.WifiEntry;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Fragment for controlling network connectivity
 */
@Keep
public class NetworkFragment extends SettingsPreferenceFragment implements
        ConnectivityListener.Listener, ConnectivityListener.WifiNetworkListener,
        AccessPoint.AccessPointListener {

    private static final String KEY_WIFI_ENABLE = "wifi_enable";
    private static final String KEY_WIFI_LIST = "wifi_list";
    private static final String KEY_WIFI_COLLAPSE = "wifi_collapse";
    private static final String KEY_WIFI_OTHER = "wifi_other";
    private static final String KEY_WIFI_ADD = "wifi_add";
    private static final String KEY_WIFI_ADD_EASYCONNECT = "wifi_add_easyconnect";
    private static final String KEY_WIFI_ALWAYS_SCAN = "wifi_always_scan";
    private static final String KEY_ETHERNET = "ethernet";
    private static final String KEY_ETHERNET_STATUS = "ethernet_status";
    private static final String KEY_ETHERNET_PROXY = "ethernet_proxy";
    private static final String KEY_ETHERNET_DHCP = "ethernet_dhcp";
    private static final String KEY_NETWORK_DIAGNOSTICS = "network_diagnostics";

    private static final int INITIAL_UPDATE_DELAY = 500;

    private static final String NETWORK_DIAGNOSTICS_ACTION =
            "com.android.tv.settings.network.NETWORK_DIAGNOSTICS";

    private ConnectivityListener mConnectivityListener;
    private WifiManager mWifiManager;
    private ConnectivityManager mConnectivityManager;
    private TvAccessPointPreference.UserBadgeCache mUserBadgeCache;

    private TwoStatePreference mEnableWifiPref;
    private CollapsibleCategory mWifiNetworksCategory;
    private Preference mCollapsePref;
    private RestrictedPreference mAddPref;
    private RestrictedPreference mAddEasyConnectPref;
    private TwoStatePreference mAlwaysScan;
    private PreferenceCategory mEthernetCategory;
    private Preference mEthernetStatusPref;
    private Preference mEthernetProxyPref;
    private Preference mEthernetDhcpPref;
    private Map<WifiEntry, RestrictedPreferenceAdapter<TvAccessPointPreference>> mPrefMap =
            Collections.emptyMap();

    private final Handler mHandler = new Handler();
    private long mNoWifiUpdateBeforeMillis;
    private Runnable mInitialUpdateWifiListRunnable = new Runnable() {
        @Override
        public void run() {
            mNoWifiUpdateBeforeMillis = 0;
            updateWifiList();
        }
    };
    private boolean mIsWifiHardwarePresent;

    public static NetworkFragment newInstance() {
        return new NetworkFragment();
    }

    private final List<AccessPoint> mCurrentAccessPoints = new ArrayList<>();

    @Override
    public void onCreate(Bundle savedInstanceState) {
        mIsWifiHardwarePresent = getContext().getPackageManager()
                .hasSystemFeature(PackageManager.FEATURE_WIFI);
        mConnectivityListener = new ConnectivityListener(
                getContext(), this, getSettingsLifecycle());
        mWifiManager = getContext().getSystemService(WifiManager.class);
        mConnectivityManager = getContext().getSystemService(ConnectivityManager.class);
        mUserBadgeCache =
                new TvAccessPointPreference.UserBadgeCache(getContext().getPackageManager());
        super.onCreate(savedInstanceState);
    }

    @Override
    public void onStart() {
        super.onStart();
        mConnectivityListener.setWifiListener(this);
        mNoWifiUpdateBeforeMillis = SystemClock.elapsedRealtime() + INITIAL_UPDATE_DELAY;
        updateWifiList();
    }

    @Override
    public void onResume() {
        super.onResume();
        // There doesn't seem to be an API to listen to everything this could cover, so
        // tickle it here and hope for the best.
        updateConnectivity();
    }


    @Override
    public void onStop() {
        mConnectivityListener.setListener(null);
        clearCurrentAccessPoints();
        super.onStop();
    }

    private int getPreferenceScreenResId() {
        switch (FlavorUtils.getFlavor(getContext())) {
            case FLAVOR_CLASSIC:
            case FLAVOR_TWO_PANEL:
                return R.xml.network;
            case FLAVOR_X:
            case FLAVOR_VENDOR:
                return R.xml.network_x;
            default:
                return R.xml.network;
        }
    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        getPreferenceManager().setPreferenceComparisonCallback(
                new PreferenceManager.SimplePreferenceComparisonCallback());
        setPreferencesFromResource(getPreferenceScreenResId(), null);

        mEnableWifiPref = (TwoStatePreference) findPreference(KEY_WIFI_ENABLE);
        mWifiNetworksCategory = (CollapsibleCategory) findPreference(KEY_WIFI_LIST);
        mCollapsePref = findPreference(KEY_WIFI_COLLAPSE);
        mAddPref = (RestrictedPreference) findPreference(KEY_WIFI_ADD);
        mAddEasyConnectPref = (RestrictedPreference) findPreference(KEY_WIFI_ADD_EASYCONNECT);
        mAlwaysScan = (TwoStatePreference) findPreference(KEY_WIFI_ALWAYS_SCAN);

        mEthernetCategory = (PreferenceCategory) findPreference(KEY_ETHERNET);
        mEthernetStatusPref = findPreference(KEY_ETHERNET_STATUS);
        mEthernetProxyPref = findPreference(KEY_ETHERNET_PROXY);
        mEthernetDhcpPref = findPreference(KEY_ETHERNET_DHCP);

        if (!mIsWifiHardwarePresent) {
            mEnableWifiPref.setVisible(false);
        }

        Preference networkDiagnosticsPref = findPreference(KEY_NETWORK_DIAGNOSTICS);
        Intent networkDiagnosticsIntent = makeNetworkDiagnosticsIntent();
        if (networkDiagnosticsIntent != null) {
            networkDiagnosticsPref.setVisible(true);
            networkDiagnosticsPref.setIntent(networkDiagnosticsIntent);
        } else {
            networkDiagnosticsPref.setVisible(false);
        }

        final UserManager userManager = UserManager.get(getContext());

        mAddPref.checkRestrictionAndSetDisabled(UserManager.DISALLOW_CONFIG_WIFI);
        mAddEasyConnectPref.checkRestrictionAndSetDisabled(UserManager.DISALLOW_CONFIG_WIFI);

        if (!mAddPref.isDisabledByAdmin()) {
            mAddPref.checkRestrictionAndSetDisabled(UserManager.DISALLOW_ADD_WIFI_CONFIG);
            mAddEasyConnectPref.checkRestrictionAndSetDisabled(
                    UserManager.DISALLOW_ADD_WIFI_CONFIG);
        }

        if (userManager.hasUserRestriction(UserManager.DISALLOW_CONFIG_WIFI)
                || userManager.hasUserRestriction(UserManager.DISALLOW_ADD_WIFI_CONFIG)) {
            mAddPref.setFragment(null);
            mAddEasyConnectPref.setFragment(null);

            if (!mAddPref.isDisabledByAdmin()) {
                mAddPref.setEnabled(false);
            }
            if (!mAddEasyConnectPref.isDisabledByAdmin()) {
                mAddEasyConnectPref.setEnabled(false);
            }
        }
    }

    @Override
    public boolean onPreferenceTreeClick(Preference preference) {
        if (preference.getKey() == null) {
            return super.onPreferenceTreeClick(preference);
        }
        switch (preference.getKey()) {
            case KEY_WIFI_ENABLE:
                BasicModeFeatureProvider provider = FlavorUtils.getFeatureFactory(
                        getContext()).getBasicModeFeatureProvider();
                if (mEnableWifiPref.isChecked() &&
                        Settings.Global.getInt(
                                getContext().getContentResolver(), Settings.Global.ADB_ENABLED, 0)
                                != 1 && SystemProperties.getInt(
                        "ro.product.first_api_level", Build.VERSION.SDK_INT)
                        >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE && provider.isBasicMode(
                        getContext())
                        && !provider.isStoreDemoMode(getContext())) {
                    // WiFi turned ON + not developer + launched on U+ + basic mode.
                    // Prevent WiFi connection by launching dialog instead.
                    provider.startBasicModeInternetBlock(getActivity());
                } else {
                    mConnectivityListener.setWifiEnabled(mEnableWifiPref.isChecked());
                }
                logToggleInteracted(
                        TvSettingsEnums.NETWORK_WIFI_ON_OFF, mEnableWifiPref.isChecked());
                return true;
            case KEY_WIFI_COLLAPSE:
                final boolean collapse = !mWifiNetworksCategory.isCollapsed();
                View collapsePrefView = getListView().getChildAt(mCollapsePref.getOrder());
                String wifiCollapseTitle = getContext().getString(collapse
                        ? R.string.wifi_setting_see_all : R.string.wifi_setting_see_fewer);
                mCollapsePref.setTitle(wifiCollapseTitle);
                if (collapsePrefView != null) {
                    collapsePrefView.setAccessibilityPaneTitle(wifiCollapseTitle);
                }
                mWifiNetworksCategory.setCollapsed(collapse);
                logEntrySelected(
                        collapse
                                ? TvSettingsEnums.NETWORK_SEE_FEWER
                                : TvSettingsEnums.NETWORK_SEE_ALL);
                return true;
            case KEY_WIFI_ALWAYS_SCAN:
                Settings.Global.putInt(getActivity().getContentResolver(),
                        Settings.Global.WIFI_SCAN_ALWAYS_AVAILABLE,
                        mAlwaysScan.isChecked() ? 1 : 0);
                logToggleInteracted(
                        TvSettingsEnums.NETWORK_ALWAYS_SCANNING_NETWORKS, mAlwaysScan.isChecked());
                return true;
            case KEY_ETHERNET_STATUS:
                return true;
            case KEY_WIFI_ADD:
                logEntrySelected(TvSettingsEnums.NETWORK_ADD_NEW_NETWORK);
                break;
            case KEY_WIFI_ADD_EASYCONNECT:
                startActivity(AddWifiNetworkActivity.createEasyConnectIntent(getContext()));
                break;
        }
        return super.onPreferenceTreeClick(preference);
    }

    private boolean isConnected() {
        NetworkInfo activeNetworkInfo = mConnectivityManager.getActiveNetworkInfo();
        return activeNetworkInfo != null && activeNetworkInfo.isConnected()
                && ConnectivityManager.TYPE_ETHERNET != activeNetworkInfo.getType();
    }

    private void updateConnectivity() {
        if (!isAdded()) {
            return;
        }

        final boolean wifiEnabled = mIsWifiHardwarePresent
                && mConnectivityListener.isWifiEnabledOrEnabling();
        mEnableWifiPref.setChecked(wifiEnabled);

        mWifiNetworksCategory.setVisible(wifiEnabled);
        mCollapsePref.setVisible(wifiEnabled && mWifiNetworksCategory.shouldShowCollapsePref());
        mAddPref.setVisible(wifiEnabled);
        if (mAddEasyConnectPref != null) {
            mAddEasyConnectPref.setVisible(isEasyConnectEnabled());
        }

        if (!wifiEnabled) {
            updateWifiList();
        }

        int scanAlwaysAvailable = 0;
        try {
            scanAlwaysAvailable = Settings.Global.getInt(getContext().getContentResolver(),
                    Settings.Global.WIFI_SCAN_ALWAYS_AVAILABLE);
        } catch (Settings.SettingNotFoundException e) {
            // Ignore
        }
        mAlwaysScan.setChecked(scanAlwaysAvailable == 1);
        if (mAlwaysScan instanceof CustomContentDescriptionSwitchPreference) {
            ((CustomContentDescriptionSwitchPreference) mAlwaysScan).setContentDescription(
                    getResources()
                            .getString(
                                    R.string.wifi_setting_always_scan_content_description));
        }

        final boolean ethernetAvailable = mConnectivityListener.isEthernetAvailable();
        mEthernetCategory.setVisible(ethernetAvailable);
        mEthernetStatusPref.setVisible(ethernetAvailable);
        mEthernetProxyPref.setVisible(ethernetAvailable);
        if (ethernetAvailable) {
            mEthernetProxyPref.setIntent(EditProxySettingsActivity.createEthernetIntent(
                    getContext(),
                    mConnectivityListener.getEthernetInterfaceName(),
                    mConnectivityListener.getEthernetIpConfiguration()));
        }
        mEthernetProxyPref.setOnPreferenceClickListener(
                preference -> {
                    logEntrySelected(TvSettingsEnums.NETWORK_ETHERNET_PROXY_SETTINGS);
                    return false;
                });

        mEthernetDhcpPref.setVisible(ethernetAvailable);
        if (ethernetAvailable) {
            mEthernetDhcpPref.setIntent(EditIpSettingsActivity.createEthernetIntent(getContext(),
                    mConnectivityListener.getEthernetInterfaceName(),
                    mConnectivityListener.getEthernetIpConfiguration()));
        }
        mEthernetDhcpPref.setOnPreferenceClickListener(
                preference -> {
                    logEntrySelected(TvSettingsEnums.NETWORK_ETHERNET_IP_SETTINGS);
                    return false;
                });

        if (ethernetAvailable) {
            final boolean ethernetConnected =
                    mConnectivityListener.isEthernetConnected();
            mEthernetStatusPref.setTitle(ethernetConnected
                    ? R.string.connected : R.string.not_connected);
            mEthernetStatusPref.setSummary(mConnectivityListener.getEthernetIpAddress());
        }
    }

    private void updateWifiList() {
        if (!isAdded()) {
            return;
        }

        if (!mIsWifiHardwarePresent || !mConnectivityListener.isWifiEnabledOrEnabling()) {
            mWifiNetworksCategory.removeAll();
            mNoWifiUpdateBeforeMillis = 0;
            return;
        }

        final long now = SystemClock.elapsedRealtime();
        if (mNoWifiUpdateBeforeMillis > now) {
            mHandler.removeCallbacks(mInitialUpdateWifiListRunnable);
            mHandler.postDelayed(mInitialUpdateWifiListRunnable,
                    mNoWifiUpdateBeforeMillis - now);
            return;
        }

        final int existingCount = mWifiNetworksCategory.getRealPreferenceCount();
        final Set<Preference> toRemove = new HashSet<>(existingCount);
        for (int i = 0; i < existingCount; i++) {
            toRemove.add(mWifiNetworksCategory.getPreference(i));
        }

        final Context themedContext = getPreferenceManager().getContext();
        final Collection<AccessPoint> newAccessPoints =
                mConnectivityListener.getAvailableNetworks();
        int index = 0;

        final Map<WifiEntry, RestrictedPreferenceAdapter<TvAccessPointPreference>> newPrefMap =
                new ArrayMap<>();
        for (final AccessPoint accessPoint : newAccessPoints) {
            accessPoint.setListener(this);
            RestrictedPreferenceAdapter<TvAccessPointPreference> restrictedPref =
                    mPrefMap.get(accessPoint.getWifiEntry());
            Preference pref;
            if (restrictedPref == null) {
                pref = new TvAccessPointPreference(accessPoint, themedContext, mUserBadgeCache,
                        false);
                List<String> userRestrictions = new ArrayList<>();
                userRestrictions.add(UserManager.DISALLOW_CONFIG_WIFI);
                userRestrictions.add(UserManager.DISALLOW_ADD_WIFI_CONFIG);
                restrictedPref = new RestrictedPreferenceAdapter(themedContext, pref,
                        userRestrictions);
                restrictedPref.setApSaved(accessPoint.isSaved());
            } else {
                restrictedPref.setApSaved(accessPoint.isSaved());
                toRemove.remove(restrictedPref.getPreference());
                pref = restrictedPref.getOriginalPreference();
            }
            newPrefMap.put(accessPoint.getWifiEntry(), restrictedPref);

            if (isCaptivePortal(accessPoint)) {
                pref.setFragment(null);
                pref.setIntent(null);
                pref.setOnPreferenceClickListener(preference -> {
                    accessPoint.getWifiEntry().signIn(null);
                    return true;
                });
            } else if (accessPoint.isActive()) {
                pref.setFragment(WifiDetailsFragment.class.getName());
                // No need to track entry selection as new page will be focused
                pref.setOnPreferenceClickListener(preference -> false);
                WifiDetailsFragment.prepareArgs(pref.getExtras(), accessPoint);
                pref.setIntent(null);
            } else {
                pref.setFragment(null);
                pref.setIntent(WifiConnectionActivity.createIntent(getContext(), accessPoint));
                pref.setOnPreferenceClickListener(
                        preference -> {
                            logEntrySelected(TvSettingsEnums.NETWORK_NOT_CONNECTED_AP);
                            return false;
                        });
            }
            pref.setVisible(!restrictedPref.isRestricted(UserManager.DISALLOW_CONFIG_WIFI)
                    || accessPoint.isSaved());
            pref.setOrder(index++);
            pref.setSummary(WifiUtils.getConnectionStatus(accessPoint.getWifiEntry()));
            restrictedPref.updatePreference();

            Preference restrictedChild = restrictedPref.getPreference();
            if (restrictedChild.getParent() != null &&
                    restrictedChild.getParent() != mWifiNetworksCategory) {
                // Remove first if added to parent from old fragment.
                restrictedChild.getParent().removePreference(restrictedChild);
            }
            mWifiNetworksCategory.addPreference(restrictedChild);
        }

        for (final Preference preference : toRemove) {
            mWifiNetworksCategory.removePreference(preference);
        }

        mCollapsePref.setVisible(mWifiNetworksCategory.shouldShowCollapsePref());
        mPrefMap = newPrefMap;

        clearCurrentAccessPoints();
        mCurrentAccessPoints.addAll(newAccessPoints);
    }

    private void clearCurrentAccessPoints() {
        for (AccessPoint accessPoint : mCurrentAccessPoints) {
            accessPoint.setListener(null);
        }
        mCurrentAccessPoints.clear();
    }

    private boolean isCaptivePortal(AccessPoint accessPoint) {
        return accessPoint.getWifiEntry().canSignIn();
    }

    private Intent makeNetworkDiagnosticsIntent() {
        Intent intent = new Intent();
        intent.setAction(NETWORK_DIAGNOSTICS_ACTION);

        ResolveInfo resolveInfo = MainFragment.systemIntentIsHandled(getContext(), intent);
        if (resolveInfo == null || resolveInfo.activityInfo == null) {
            return null;
        }

        intent.setPackage(resolveInfo.activityInfo.packageName);

        return intent;
    }

    @Override
    public void onConnectivityChange() {
        updateConnectivity();
    }

    @Override
    public void onWifiListChanged() {
        updateWifiList();
    }

    @Override
    public void onAccessPointChanged(AccessPoint accessPoint) {
        RestrictedPreferenceAdapter<TvAccessPointPreference> restrictedPref =
                mPrefMap.get(accessPoint.getWifiEntry());
        restrictedPref.updatePreference(AccessPointPreference::refresh);
    }

    @Override
    public void onLevelChanged(AccessPoint accessPoint) {
        RestrictedPreferenceAdapter<TvAccessPointPreference> restrictedPref =
                mPrefMap.get(accessPoint.getWifiEntry());
        restrictedPref.updatePreference(AccessPointPreference::onLevelChanged);
    }

    @Override
    protected int getPageId() {
        return TvSettingsEnums.NETWORK;
    }

    private boolean isEasyConnectEnabled() {
        final boolean wifiEnabled = mIsWifiHardwarePresent
                && mConnectivityListener.isWifiEnabledOrEnabling();

        if (!wifiEnabled || !mWifiManager.isEasyConnectSupported()) {
            return false;
        }

        return getContext().getResources().getBoolean(R.bool.config_easyconnect_enabled);
    }
}
