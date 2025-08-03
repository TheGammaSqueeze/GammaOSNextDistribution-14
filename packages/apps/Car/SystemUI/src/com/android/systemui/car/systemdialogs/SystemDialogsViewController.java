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

package com.android.systemui.car.systemdialogs;

import static android.app.admin.DevicePolicyManager.DEVICE_OWNER_TYPE_FINANCED;
import static android.view.WindowInsets.Type.statusBars;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.TextView;

import androidx.annotation.Nullable;

import com.android.systemui.R;
import com.android.systemui.dagger.SysUISingleton;
import com.android.systemui.settings.UserTracker;
import com.android.systemui.statusbar.policy.SecurityController;

import javax.inject.Inject;

/**
 * A controller that can create and control the visibility of various system dialogs.
 */
@SysUISingleton
public class SystemDialogsViewController {
    private final Context mContext;
    private final SecurityController mSecurityController;
    private final AlertDialog.OnClickListener mOnDeviceMonitoringDialogClickListener;

    @Inject
    public SystemDialogsViewController(
            Context context,
            SecurityController securityController,
            UserTracker userTracker) {
        mContext = context;
        mSecurityController = securityController;
        mOnDeviceMonitoringDialogClickListener = (dialog, which) -> {
            dialog.dismiss();
            if (which == DialogInterface.BUTTON_NEUTRAL) {
                Intent intent = new Intent(Settings.ACTION_ENTERPRISE_PRIVACY_SETTINGS);
                mContext.startActivityAsUser(intent, userTracker.getUserHandle());
            }
        };
    }

    protected void showDeviceMonitoringDialog() {
        AlertDialog dialog = new AlertDialog.Builder(mContext,
                com.android.internal.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setView(createDialogView())
                .setPositiveButton(R.string.ok, mOnDeviceMonitoringDialogClickListener)
                .setNeutralButton(R.string.monitoring_button_view_policies,
                        mOnDeviceMonitoringDialogClickListener)
                .create();

        applyCarSysUIDialogFlags(dialog);
        dialog.show();
    }

    private View createDialogView() {
        View dialogView = LayoutInflater.from(mContext)
                .inflate(R.layout.device_management_dialog, null, false);

        CharSequence deviceOwnerOrganization = mSecurityController.getDeviceOwnerOrganizationName();
        boolean isDeviceManaged = mSecurityController.isDeviceManaged();

        // device management section
        TextView deviceManagementSubtitle =
                dialogView.requireViewById(R.id.device_management_subtitle);
        deviceManagementSubtitle.setText(getDeviceMonitoringTitle(deviceOwnerOrganization));

        CharSequence managementMessage = getDeviceMonitoringMessage(deviceOwnerOrganization);
        if (managementMessage == null) {
            dialogView.requireViewById(R.id.device_management_disclosures).setVisibility(View.GONE);
        } else {
            dialogView.requireViewById(R.id.device_management_disclosures)
                    .setVisibility(View.VISIBLE);
            TextView deviceManagementWarning =
                    dialogView.requireViewById(R.id.device_management_warning);
            deviceManagementWarning.setText(managementMessage);
        }

        // CA certificate section
        CharSequence caCertsMessage = getCaCertsMessage(isDeviceManaged);
        if (caCertsMessage == null) {
            dialogView.requireViewById(R.id.ca_certs_disclosures).setVisibility(View.GONE);
        } else {
            dialogView.requireViewById(R.id.ca_certs_disclosures).setVisibility(View.VISIBLE);
            TextView caCertsWarning = dialogView.requireViewById(R.id.ca_certs_warning);
            caCertsWarning.setText(caCertsMessage);
        }

        // network logging section
        CharSequence networkLoggingMessage = getNetworkLoggingMessage(isDeviceManaged);
        if (networkLoggingMessage == null) {
            dialogView.requireViewById(R.id.network_logging_disclosures).setVisibility(View.GONE);
        } else {
            dialogView.requireViewById(R.id.network_logging_disclosures)
                    .setVisibility(View.VISIBLE);
            TextView networkLoggingWarning =
                    dialogView.requireViewById(R.id.network_logging_warning);
            networkLoggingWarning.setText(networkLoggingMessage);
        }

        // VPN section
        CharSequence vpnMessage = getVpnMessage(isDeviceManaged);
        if (vpnMessage == null) {
            dialogView.requireViewById(R.id.vpn_disclosures).setVisibility(View.GONE);
        } else {
            dialogView.requireViewById(R.id.vpn_disclosures).setVisibility(View.VISIBLE);
            TextView vpnWarning = dialogView.requireViewById(R.id.vpn_warning);
            vpnWarning.setText(vpnMessage);
        }

        return dialogView;
    }

    private CharSequence getDeviceMonitoringTitle(CharSequence deviceOwnerOrganization) {
        if (deviceOwnerOrganization != null && isFinancedDevice()) {
            return mContext.getString(R.string.monitoring_title_financed_device,
                    deviceOwnerOrganization);
        } else {
            return mContext.getString(R.string.monitoring_title_device_owned);
        }
    }

    private CharSequence getDeviceMonitoringMessage(CharSequence deviceOwnerOrganization) {
        if (deviceOwnerOrganization != null) {
            if (isFinancedDevice()) {
                return mContext.getString(R.string.monitoring_financed_description_named_management,
                        deviceOwnerOrganization, deviceOwnerOrganization);
            } else {
                return mContext.getString(
                        R.string.monitoring_description_named_management, deviceOwnerOrganization);
            }
        }
        return mContext.getString(R.string.monitoring_description_management);
    }

    @Nullable
    private CharSequence getCaCertsMessage(boolean isDeviceManaged) {
        boolean hasCACerts = mSecurityController.hasCACertInCurrentUser();
        boolean hasCACertsInWorkProfile = mSecurityController.hasCACertInWorkProfile();
        if (!(hasCACerts || hasCACertsInWorkProfile)) return null;
        if (isDeviceManaged) {
            return mContext.getString(R.string.monitoring_description_management_ca_certificate);
        }
        if (hasCACertsInWorkProfile) {
            return mContext.getString(
                    R.string.monitoring_description_managed_profile_ca_certificate);
        }
        return mContext.getString(R.string.monitoring_description_ca_certificate);
    }

    @Nullable
    private CharSequence getNetworkLoggingMessage(boolean isDeviceManaged) {
        boolean isNetworkLoggingEnabled = mSecurityController.isNetworkLoggingEnabled();
        if (!isNetworkLoggingEnabled) return null;
        if (isDeviceManaged) {
            return mContext.getString(R.string.monitoring_description_management_network_logging);
        } else {
            return mContext.getString(
                    R.string.monitoring_description_managed_profile_network_logging);
        }
    }

    @Nullable
    private CharSequence getVpnMessage(boolean isDeviceManaged) {
        boolean hasWorkProfile = mSecurityController.hasWorkProfile();
        String vpnName = mSecurityController.getPrimaryVpnName();
        String vpnNameWorkProfile = mSecurityController.getWorkProfileVpnName();
        if (vpnName == null && vpnNameWorkProfile == null) return null;
        if (isDeviceManaged) {
            if (vpnName != null && vpnNameWorkProfile != null) {
                return mContext.getString(R.string.monitoring_description_two_named_vpns,
                        vpnName, vpnNameWorkProfile);
            } else {
                return mContext.getString(R.string.monitoring_description_managed_device_named_vpn,
                        vpnName != null ? vpnName : vpnNameWorkProfile);
            }
        } else {
            if (vpnName != null && vpnNameWorkProfile != null) {
                return mContext.getString(R.string.monitoring_description_two_named_vpns,
                        vpnName, vpnNameWorkProfile);
            } else if (vpnNameWorkProfile != null) {
                return mContext.getString(R.string.monitoring_description_managed_profile_named_vpn,
                        vpnNameWorkProfile);
            } else if (hasWorkProfile) {
                return mContext.getString(
                        R.string.monitoring_description_personal_profile_named_vpn, vpnName);
            } else {
                return mContext.getString(R.string.monitoring_description_named_vpn, vpnName);
            }
        }
    }

    private boolean isFinancedDevice() {
        return mSecurityController.isDeviceManaged()
                && mSecurityController.getDeviceOwnerType(
                mSecurityController.getDeviceOwnerComponentOnAnyUser())
                == DEVICE_OWNER_TYPE_FINANCED;
    }

    private void applyCarSysUIDialogFlags(AlertDialog dialog) {
        Window window = dialog.getWindow();
        window.setType(WindowManager.LayoutParams.TYPE_KEYGUARD_DIALOG);
        window.addFlags(WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM
                | WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED);
        window.getAttributes().setFitInsetsTypes(
                window.getAttributes().getFitInsetsTypes() & ~statusBars());
    }
}
