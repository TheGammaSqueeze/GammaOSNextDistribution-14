package com.android.server.deviceconfig;

import static com.android.server.deviceconfig.Flags.enableRebootNotification;
import static com.android.server.deviceconfig.Flags.enableUnattendedReboot;

import java.io.IOException;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.HashSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import android.aconfig.Aconfig.parsed_flags;
import android.aconfig.Aconfig.parsed_flag;
import android.content.Intent;
import android.annotation.NonNull;
import android.annotation.SystemApi;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.os.AsyncTask;
import android.os.Binder;
import android.content.IntentFilter;
import android.provider.DeviceConfig;
import android.provider.UpdatableDeviceConfigServiceReadiness;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.content.ComponentName;
import android.util.Slog;
import com.android.modules.utils.build.SdkLevel;
import com.android.server.SystemService;

/** @hide */
@SystemApi(client = SystemApi.Client.SYSTEM_SERVER)
public class DeviceConfigInit {
    private static final String TAG = "DEVICE_CONFIG_INIT";
    private static final String STAGED_NAMESPACE = "staged";

    private static final String SYSTEM_FLAGS_PATH = "/system/etc/aconfig_flags.pb";
    private static final String SYSTEM_EXT_FLAGS_PATH = "/system_ext/etc/aconfig_flags.pb";
    private static final String VENDOR_FLAGS_PATH = "/vendor/etc/aconfig_flags.pb";

    private static final String CONFIGURATION_NAMESPACE = "configuration";
    private static final String BOOT_NOTIFICATION_FLAG =
        "ConfigInfraFlags__enable_boot_notification";
    private static final String UNATTENDED_REBOOT_FLAG =
        "ConfigInfraFlags__enable_unattended_reboot";

    private DeviceConfigInit() {
        // do not instantiate
    }

    /** @hide */
    @SystemApi(client = SystemApi.Client.SYSTEM_SERVER)
    public static class Lifecycle extends SystemService {
        private DeviceConfigServiceImpl mService;
        private UnattendedRebootManager mUnattendedRebootManager;

        /** @hide */
        @SystemApi(client = SystemApi.Client.SYSTEM_SERVER)
        public Lifecycle(@NonNull Context context) {
            super(context);
            // this service is always instantiated but should only launch subsequent service(s)
            // if the module is ready
            if (UpdatableDeviceConfigServiceReadiness.shouldStartUpdatableService()) {
                mService = new DeviceConfigServiceImpl(getContext());
                publishBinderService(DeviceConfig.SERVICE_NAME, mService);
            }
            applyBootstrapValues();
        }

        /**
         * @hide
         */
        @Override
        public void onStart() {
            boolean notificationEnabled =
                DeviceConfig.getBoolean(CONFIGURATION_NAMESPACE, BOOT_NOTIFICATION_FLAG, false);
            if (notificationEnabled && enableRebootNotification()) {
                Map<String, Set<String>> aconfigFlags = new HashMap<>();
                try {
                    addAconfigFlagsFromFile(aconfigFlags, SYSTEM_FLAGS_PATH);
                    addAconfigFlagsFromFile(aconfigFlags, SYSTEM_EXT_FLAGS_PATH);
                    addAconfigFlagsFromFile(aconfigFlags, VENDOR_FLAGS_PATH);
                } catch (IOException e) {
                    Slog.e(TAG, "error loading aconfig flags", e);
                }

                BootNotificationCreator notifCreator = new BootNotificationCreator(
                    getContext().getApplicationContext(),
                    aconfigFlags);

                DeviceConfig.addOnPropertiesChangedListener(
                    STAGED_NAMESPACE,
                    AsyncTask.THREAD_POOL_EXECUTOR,
                    notifCreator);
            }

            boolean unattendedRebootEnabled =
                DeviceConfig.getBoolean(CONFIGURATION_NAMESPACE, UNATTENDED_REBOOT_FLAG, false);
            if (unattendedRebootEnabled && enableUnattendedReboot()) {
                // Only schedule a reboot if this is the first boot since an OTA.
                if (!getContext().getPackageManager().isDeviceUpgrading()) {
                    return;
                }

                mUnattendedRebootManager =
                    new UnattendedRebootManager(getContext().getApplicationContext());
                getContext()
                    .registerReceiver(
                        new BroadcastReceiver() {
                            @Override
                            public void onReceive(Context context, Intent intent) {
                                mUnattendedRebootManager.prepareUnattendedReboot();
                                mUnattendedRebootManager.scheduleReboot();
                            }
                        },
                        new IntentFilter(Intent.ACTION_BOOT_COMPLETED));
            }
        }

        private void addAconfigFlagsFromFile(Map<String, Set<String>> aconfigFlags,
                                             String fileName) throws IOException {
            byte[] contents = (new FileInputStream(fileName)).readAllBytes();
            parsed_flags parsedFlags = parsed_flags.parseFrom(contents);
            for (parsed_flag flag : parsedFlags.getParsedFlagList()) {
                if (aconfigFlags.get(flag.getNamespace()) == null) {
                    aconfigFlags.put(flag.getNamespace(), new HashSet<>());
                    aconfigFlags.get(flag.getNamespace()).add(flag.getName());
                } else {
                    aconfigFlags.get(flag.getNamespace()).add(flag.getName());
                }
            }
        }

        private void applyBootstrapValues() {
            if (SdkLevel.isAtLeastV()) {
                try {
                    new DeviceConfigBootstrapValues().applyValuesIfNeeded();
                } catch (RuntimeException e) {
                    Slog.e(TAG, "Failed to load boot overrides", e);
                    throw e;
                } catch (IOException e) {
                    Slog.e(TAG, "Failed to load boot overrides", e);
                    throw new RuntimeException(e);
                }
            }
        }
    }
}
