package com.android.tv.settings.system;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.storage.DiskInfo;
import android.os.storage.StorageManager;
import android.os.storage.VolumeInfo;
import android.provider.Settings;
import android.util.Log;
import android.view.KeyEvent;
import android.widget.Toast;

import com.android.tv.settings.R;
import com.android.tv.settings.util.ConvertiblePort;

import java.io.File;
import java.nio.file.AccessDeniedException;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ShieldMtpActivity extends Activity {
    private static final String TAG = "ShieldMtpActivity";
    private boolean DEBUG = false;

    public static final String CALLER = "isCalledFromDevOption";
    public static final String PAGE_NUMBER = "pageToDisplay";

    public static final int PORT_BUSY_PAGE = 0;
    public static final int MTP_INFO_PAGE = 1;
    public static final int USB_DEBUG_INFO_PAGE = 2;

    public static final String DEVICE_MODE = "device";
    public static final String HOST_MODE = "host";

    private static final String NAME_USB_OTG = "usb_otg";
    private static final String NAME_VIRTUAL_ID = "virtual_id";
    private static final String ROOT_EXTCON_PATH = "/sys/class/extcon/";

    private static final List<List<String>> USB_MODE_VALUES = Arrays.asList(
            Arrays.asList(HOST_MODE, "USB_HOST=1", "0x1"),
            Arrays.asList(DEVICE_MODE, "USB_HOST=0", "0x0")
        );

    private static final String ENUMERATED_USB_PATH = "/sys/bus/usb/devices/";
    private static final String USB2_PATH = "1-1";
    private static final String USB3_PATH = "2-1";

    private Toast mToast;
    private static Map<String, Path> mUsbPathMap = new HashMap();

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        final boolean isDevOption = getIntent().getBooleanExtra(CALLER, false);
        final int pageNumber = getIntent().getIntExtra(PAGE_NUMBER, -1);
        if (DEBUG) {
            Log.d(TAG, "onCreate, called from Dev:" + Boolean.toString(isDevOption) +
                    ", display page: " + Integer.toString(pageNumber));
        }
        switch (pageNumber) {
            case PORT_BUSY_PAGE:
                setContentView(R.layout.port_in_use);
                return;
            case MTP_INFO_PAGE:
                setContentView(R.layout.mtp_enable);
                if (!setPortMode(this, DEVICE_MODE, false)) {
                    showToast(getString(R.string.mtp_retry_error));
                }
                return;
            case USB_DEBUG_INFO_PAGE:
                setContentView(R.layout.usb_debug_enable);
                if (!setPortMode(this, DEVICE_MODE, true)) {
                    showToast(getString(R.string.mtp_retry_error));
                }
                return;
            default:
                showToast(getString(R.string.mtp_unexpected_error));
                return;
        }
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getAction() == 0 && (event.getKeyCode() == 66 ||
                event.getKeyCode() == 96 || event.getKeyCode() == 97 ||
                event.getKeyCode() == 4 || event.getKeyCode() == 3)) {
            onBackPressed();
            return true;
        }
        super.dispatchKeyEvent(event);
        return false;
    }

    @Override
    public void onStart() {
        if (DEBUG) {
            Log.d(TAG, "onStart");
        }
        super.onStart();
    }

    @Override
    public void onResume() {
        if (DEBUG) {
            Log.d(TAG, "onResume");
        }
        super.onResume();
    }

    @Override
    public void onPause() {
        if (DEBUG) {
            Log.d(TAG, "onPause");
        }
        super.onPause();
    }

    @Override
    public void onStop() {
        if (DEBUG) {
            Log.d(TAG, "onStop");
        }
        super.onStop();
    }

    @Override
    public void onBackPressed() {
        if (DEBUG) {
            Log.d(TAG, "onBackPressed");
        }
        finishAndExit(-1);
    }

    public static class BootReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (ConvertiblePort.hasConvertiblePort(context)) {
                Log.i(TAG, "Starting to initialize USB settings on boot complete.");
                String currentMode = ConvertiblePort.getConvertiblePortSetting(context);

                if (new File("/proc/device-tree/chosen/nvidia,safe_mode_adb").exists()) {
                    currentMode = DEVICE_MODE;
                    Log.i(TAG, "Setting USB as DEVICE on boot due to safe_mode_adb.");
                } else if (!(currentMode.equals(DEVICE_MODE) ||
                        currentMode.equals(HOST_MODE))) {
                    currentMode = HOST_MODE;
                    Log.i(TAG, "Setting USB as HOST on boot due to unset property.");
                }

                if (currentMode.equals(HOST_MODE)) {
                    if (!setPortMode(context, currentMode, false)) {
                        Log.e(TAG, "Failed to initialize USB as HOST on boot.");
                    }
                } else if (currentMode.equals(DEVICE_MODE)) {
                    if (!setPortMode(context, currentMode, false)) {
                        Log.e(TAG, "Failed to initialize USB as DEVICE on boot.");
                    }
                }
            }
        }
    }

    public static boolean setPortMode(Context context, String targetMode,
            boolean fromDeveloperOption) {
        if (updateUsbModeSysfs(targetMode)) {
            return updateSysProperties(context, targetMode, fromDeveloperOption);
        }
        Log.e(TAG, "Failed to update sysfs node.");
        return false;
    }

    public static boolean isConvPortBusy(Context context) {
        String otgState = null;
        String currentMode = ConvertiblePort.getConvertiblePortSetting(context);
        if (currentMode == null || currentMode.isEmpty()) {
            Log.d(TAG, "Trying to get property from sysfs node!");
            String state = readExtconStatePathNoCheck(NAME_USB_OTG);
            if (state != null) {
                currentMode = USB_MODE_VALUES.stream().filter(
                        vals -> state.contains(vals.get(1))).findFirst().map(
                                vals -> vals.get(0)).orElse("");
                otgState = state;
            }
        }
        if (currentMode == null) {
            Log.w(TAG, "Failed to get current mode!");
            return false;
        }
        if (currentMode.equals(HOST_MODE)) {
            if (new File(ENUMERATED_USB_PATH + USB2_PATH).exists()) {
                Log.d(TAG, "Convertible port is connected with USB 2.x device.");
                return true;
            } else if (new File(ENUMERATED_USB_PATH + USB3_PATH).exists()) {
                Log.d(TAG, "Convertible port is connected with USB 3.x device.");
                return true;
            }
        } else if (currentMode.equals(DEVICE_MODE)) {
            String state2 = otgState == null ?
                    readExtconStatePathNoCheck(NAME_USB_OTG) : otgState;
            Log.d(TAG, "Read state from usb_otg: " + state2);
            if (state2 != null && state2.contains("USB=1")) {
                return true;
            }
        } else {
            StringBuilder sb = new StringBuilder();
            sb.append("Invalid USB mode：");
            sb.append(currentMode.isEmpty() ? "[Empty]" : currentMode);
            Log.w(TAG, sb.toString());
        }
        return false;
    }

    private static Path buildStatePath(Path root) {
        return Paths.get(root.toString(), "state");
    }

    private static String readPath(Path path, String... subs) {
        try {
            return (String) Files.readAllLines(Paths.get(path.toString(),
                    subs)).stream().collect(Collectors.joining());
        } catch (AccessDeniedException e) {
            return "";
        } catch (Exception e2) {
            Log.w(TAG, "Failed to read path: " + path + ", error: " + e2);
            return "";
        }
    }

    private static Path getExtconPathByNameNoCheck(String name) {
        Path caPath = mUsbPathMap.get(name);
        if (caPath != null) {
            return caPath;
        }
        try (Stream<Path> extcons = Files.list(Paths.get(ROOT_EXTCON_PATH))) {
            for (Path path : extcons.toArray(Path[]::new)) {
                if (readPath(path, "name").contains(name)) {
                    Log.i(TAG, "Found extcon path <"+ path + "> for name: " + name);
                    mUsbPathMap.put(name, path);
                    return path;
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to find extcon path for: " + name + ", error: " + e);
        }
        Log.i(TAG, "Did not find path for " + name);
        return null;
    }

    private static String readExtconStatePathNoCheck(Path path) {
        if (path == null) {
            return null;
        }
        try {
            return readPath(buildStatePath(path), new String[0]);
        } catch (Exception e) {
            Log.e(TAG, "Failed to read extcon state: " + path + ", error: " + e);
            return null;
        }
    }

    private static String readExtconStatePathNoCheck(String name) {
        return readExtconStatePathNoCheck(getExtconPathByNameNoCheck(name));
    }

    private void finishAndExit(int result) {
        if (this.DEBUG) {
            Log.d(TAG, "finishAndExit");
        }
        setResult(result);
        finish();
    }

    private static boolean updateUsbModeSysfs(String mode) {
        String sysfsVal = USB_MODE_VALUES.stream().filter(
                vals -> vals.contains(mode)).findFirst().map(vals -> vals.get(2)).orElse(null);
        if (sysfsVal == null) {
            Log.e(TAG, "Invalid USB mode: " + mode);
            return false;
        }
        Path usbModePath = getExtconPathByNameNoCheck(NAME_VIRTUAL_ID);
        if (usbModePath == null) {
            return false;
        }
        if (isTheSameMode(readExtconStatePathNoCheck(usbModePath), sysfsVal)) {
            Log.i(TAG, "Already set properly - sysfs val: " + sysfsVal +
                    ", selected mode: " + mode);
            return true;
        }
        try {
            Files.write(buildStatePath(usbModePath), sysfsVal.getBytes(), new OpenOption[0]);
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Failed to write state to: " + usbModePath.toString());
            return false;
        }
    }

    private static boolean isTheSameMode(String readVal, String writeVal) {
        return USB_MODE_VALUES.stream().anyMatch(
                vals -> vals.contains(readVal) && vals.contains(writeVal));
    }

    private static boolean updateSysProperties(Context context, String targetMode,
            boolean fromDeveloperOption) {
        int status = ConvertiblePort.setConvertiblePortSetting(context, targetMode);
        if (status != 0) {
            Log.e(TAG, "Failed to update system property, error = " + status);
            return false;
        }
        if (targetMode.equals(HOST_MODE)) {
            Settings.Global.putInt(context.getContentResolver(), "adb_enabled", 0);
            if (Settings.Global.getInt(context.getContentResolver(), "adb_enabled", 1) != 0) {
                Log.e(TAG, "Failed to update ADB_ENABLED to 0.");
                return false;
            }
        } else if (fromDeveloperOption && targetMode.equals(DEVICE_MODE)) {
            Settings.Global.putInt(context.getContentResolver(), "adb_enabled", 1);
            if (Settings.Global.getInt(context.getContentResolver(), "adb_enabled", 0) != 1) {
                Log.e(TAG, "Failed to update ADB_ENABLED to 1.");
                return false;
            }
        }
        return true;
    }

    private static VolumeInfo findConvPortStorage(Context context) {
        DiskInfo disk;
        StorageManager storage = context.getSystemService(StorageManager.class);
        for (VolumeInfo vol : storage.getVolumes()) {
            if (vol.getState() == 2 && (disk = vol.getDisk()) != null && disk.isUsb()) {
                if (disk.sysPath.toLowerCase().contains(USB2_PATH) ||
                        disk.sysPath.toLowerCase().contains(USB3_PATH)) {
                    Log.d(TAG, "Mounted USB storage device:" + disk);
                    return vol;
                }
            }
        }
        return null;
    }

    private void showToast(String toastString) {
        if (mToast != null) {
            mToast.cancel();
        }
        mToast = Toast.makeText(this, toastString, 0);
        mToast.show();
    }
}
