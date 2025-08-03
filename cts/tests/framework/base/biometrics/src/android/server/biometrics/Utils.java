/*
 * Copyright (C) 2020 The Android Open Source Project
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

package android.server.biometrics;

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;

import android.content.ComponentName;
import android.hardware.biometrics.BiometricManager;
import android.hardware.biometrics.BiometricPrompt;
import android.hardware.biometrics.Flags;
import android.hardware.biometrics.SensorProperties;
import android.os.ParcelFileDescriptor;
import android.os.Process;
import android.os.SystemProperties;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.server.wm.Condition;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.server.biometrics.nano.BiometricServiceStateProto;

import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;

public class Utils {

    private static final String TAG = "BiometricTestUtils";
    private static final String KEYSTORE_PROVIDER = "AndroidKeyStore";

    /** adb command for dumping the biometric proto */
    public static final String DUMPSYS_BIOMETRIC = "dumpsys biometric --proto";

    /**
     * Retrieves the current SensorStates.
     */
    public interface SensorStatesSupplier {
        SensorStates getSensorStates() throws Exception;
    }

    /**
     * The Biometric {@link com.android.server.biometrics.SensorConfig}.
     * Parsed sensor config. See core/res/res/values/config.xml config_biometric_sensors
     */
    public static class SensorConfig {
        public final int id;
        public final int modality;
        public final int strength;

        public SensorConfig(String config) {
            String[] elems = config.split(":");
            id = Integer.parseInt(elems[0]);
            modality = Integer.parseInt(elems[1]);
            strength = Integer.parseInt(elems[2]);
        }
    }

    /** Waits for the service to become idle. */
    public static void waitForIdleService() throws Exception {
        waitForIdleService(() -> getBiometricServiceCurrentState().mSensorStates);
    }

    /**
     * Waits for the service to become idle
     * @throws Exception
     */
    public static void waitForIdleService(@NonNull SensorStatesSupplier supplier) throws Exception {
        for (int i = 0; i < 20; i++) {
            if (!supplier.getSensorStates().areAllSensorsIdle()) {
                Log.d(TAG, "Not idle yet..");
                Thread.sleep(300);
            } else {
                return;
            }
        }
        Log.d(TAG, "Timed out waiting for idle");
    }


    /** Waits for the specified sensor to become non-idle. */
    public static void waitForBusySensor(int sensorId) throws Exception {
        waitForBusySensor(sensorId, () -> getBiometricServiceCurrentState().mSensorStates);
    }

    /**
     * Waits for the specified sensor to become non-idle
     */
    public static void waitForBusySensor(int sensorId, @NonNull SensorStatesSupplier supplier)
            throws Exception {
        for (int i = 0; i < 10; i++) {
            if (!supplier.getSensorStates().sensorStates.get(sensorId).isBusy()) {
                Log.d(TAG, "Not busy yet..");
                Thread.sleep(300);
            } else {
                return;
            }
        }
        Log.d(TAG, "Timed out waiting to become busy");
    }

    public static void waitFor(@NonNull String message, @NonNull BooleanSupplier condition) {
        waitFor(message, condition, null /* onFailure */);
    }

    public static void waitFor(@NonNull String message, @NonNull BooleanSupplier condition,
            @Nullable Consumer<Object> onFailure) {
        Condition.waitFor(new Condition<>(message, condition)
                .setRetryIntervalMs(500)
                .setRetryLimit(20)
                .setOnFailure(onFailure));
    }

    /**
     * Retrieves the current states of all biometric sensor services (e.g. FingerprintService,
     * FaceService, etc).
     *
     * Note that the states are retrieved from BiometricService, instead of individual services.
     * This is because 1) BiometricService is the source of truth for all public API-facing things,
     * and 2) This to include other information, such as UI states, etc as well.
     */
    @NonNull
    public static BiometricServiceState getBiometricServiceCurrentState() throws Exception {
        final byte[] dump = Utils.executeShellCommand(DUMPSYS_BIOMETRIC);
        final BiometricServiceStateProto proto = BiometricServiceStateProto.parseFrom(dump);
        return BiometricServiceState.parseFrom(proto);
    }

    /**
     * Runs a shell command, similar to running "adb shell ..." from the command line.
     * @param cmd A command, without the preceding "adb shell" portion. For example,
     *            passing in "dumpsys fingerprint" would be the equivalent of running
     *            "adb shell dumpsys fingerprint" from the command line.
     * @return The result of the command.
     */
    public static byte[] executeShellCommand(String cmd) {
        Log.d(TAG, "execute: " + cmd);
        try {
            ParcelFileDescriptor pfd = getInstrumentation().getUiAutomation()
                    .executeShellCommand(cmd);
            byte[] buf = new byte[512];
            int bytesRead;
            FileInputStream fis = new ParcelFileDescriptor.AutoCloseInputStream(pfd);
            ByteArrayOutputStream stdout = new ByteArrayOutputStream();
            while ((bytesRead = fis.read(buf)) != -1) {
                stdout.write(buf, 0, bytesRead);
            }
            fis.close();
            return stdout.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static void forceStopActivity(ComponentName componentName) {
        executeShellCommand("am force-stop " + componentName.getPackageName()
                + " " + componentName.getShortClassName().replaceAll("\\.", ""));
    }

    public static int numberOfSpecifiedOperations(@NonNull BiometricServiceState state,
            int sensorId, int operation) {
        int count = 0;
        final List<Integer> recentOps = state.mSensorStates.sensorStates.get(sensorId)
                .getSchedulerState().getRecentOperations();
        for (Integer i : recentOps) {
            if (i == operation) {
                count++;
            }
        }
        return count;
    }

    public static void createTimeBoundSecretKey_deprecated(String keyName, boolean useStrongBox)
            throws Exception {
        KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
        keyStore.load(null);
        KeyGenerator keyGenerator = KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");

        // Set the alias of the entry in Android KeyStore where the key will appear
        // and the constrains (purposes) in the constructor of the Builder
        keyGenerator.init(new KeyGenParameterSpec.Builder(keyName,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_CBC)
                .setUserAuthenticationRequired(true)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_PKCS7)
                .setIsStrongBoxBacked(useStrongBox)
                .setUserAuthenticationValidityDurationSeconds(5 /* seconds */)
                .build());
        keyGenerator.generateKey();
    }

    static void createTimeBoundSecretKey(String keyName, int authTypes, boolean useStrongBox)
            throws Exception {
        KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
        keyStore.load(null);
        KeyGenerator keyGenerator = KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");

        // Set the alias of the entry in Android KeyStore where the key will appear
        // and the constrains (purposes) in the constructor of the Builder
        keyGenerator.init(new KeyGenParameterSpec.Builder(keyName,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_CBC)
                .setUserAuthenticationRequired(true)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_PKCS7)
                .setIsStrongBoxBacked(useStrongBox)
                .setUserAuthenticationParameters(1 /* seconds */, authTypes)
                .build());
        keyGenerator.generateKey();
    }

    public static void generateBiometricBoundKey(String keyName, boolean useStrongBox)
            throws Exception {
        final KeyStore keystore = KeyStore.getInstance(KEYSTORE_PROVIDER);
        keystore.load(null);
        KeyGenParameterSpec.Builder builder = new KeyGenParameterSpec.Builder(
                keyName,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_CBC)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_PKCS7)
                .setUserAuthenticationRequired(true)
                .setInvalidatedByBiometricEnrollment(true)
                .setIsStrongBoxBacked(useStrongBox)
                .setUserAuthenticationParameters(0, KeyProperties.AUTH_BIOMETRIC_STRONG);

        KeyGenerator keyGenerator = KeyGenerator
                .getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_PROVIDER);
        keyGenerator.init(builder.build());

        // Generates and stores the key in Android KeyStore under the keystoreAlias (keyName)
        // specified in the builder.
        keyGenerator.generateKey();
    }

    public static BiometricPrompt.CryptoObject initializeCryptoObject(String keyName)
            throws Exception {
        final KeyStore keystore = KeyStore.getInstance(KEYSTORE_PROVIDER);
        keystore.load(null);
        final SecretKey secretKey = (SecretKey) keystore.getKey(
                keyName, null /* password */);
        final Cipher cipher = Cipher.getInstance(KeyProperties.KEY_ALGORITHM_AES + "/"
                + KeyProperties.BLOCK_MODE_CBC + "/"
                + KeyProperties.ENCRYPTION_PADDING_PKCS7);
        cipher.init(Cipher.ENCRYPT_MODE, secretKey);

        final BiometricPrompt.CryptoObject cryptoObject =
                new BiometricPrompt.CryptoObject(cipher);
        return cryptoObject;
    }

    public static boolean isPublicAuthenticatorConstant(int authenticator) {
        switch (authenticator) {
            case BiometricManager.Authenticators.BIOMETRIC_STRONG:
            case BiometricManager.Authenticators.BIOMETRIC_WEAK:
            case BiometricManager.Authenticators.DEVICE_CREDENTIAL:
                return true;
            default:
                return false;
        }
    }

    public static int testApiStrengthToAuthenticatorStrength(int testApiStrength) {
        switch (testApiStrength) {
            case SensorProperties.STRENGTH_STRONG:
                return BiometricManager.Authenticators.BIOMETRIC_STRONG;
            case SensorProperties.STRENGTH_WEAK:
                return BiometricManager.Authenticators.BIOMETRIC_WEAK;
            default:
                throw new IllegalArgumentException("Unable to convert testApiStrength: "
                        + testApiStrength);
        }
    }

    public static boolean isFirstApiLevel29orGreater() {
        int firstApiLevel = SystemProperties.getInt("ro.product.first_api_level", 0);
        if (firstApiLevel >= 29) {
            return true;
        }
        return false;
    }

    /** Find the sensor id of the AIDL fingerprint HAL, or -1 if not present. */
    public static int getAidlFingerprintSensorId() {
        return getAidlSensorId("dumpsys fingerprint", ", provider: FingerprintProvider");
    }

    /** Find all of the sensor ids of the AIDL fingerprint HALs */
    public static List<Integer> getAidlFingerprintSensorIds() {
        return getAidlSensorIds("dumpsys fingerprint", ", provider: FingerprintProvider");
    }

    /** Find the sensor id of the AIDL face HAL, or -1 if not present. */
    public static int getAidlFaceSensorId() {
        return getAidlSensorId("dumpsys face", ", provider: FaceProvider");
    }

    /** Find current user id. */
    public static int getUserId() {
        return Process.myUserHandle().getIdentifier();
    }

    private static int getAidlSensorId(String adbCommand, String providerRegex) {
        List<Integer> ids = getAidlSensorIds(adbCommand, providerRegex);

        if (ids.isEmpty()) {
            return -1;
        }

        return ids.get(0);
    }

    private static List<Integer> getAidlSensorIds(String adbCommand, String providerRegex) {
        final byte[] dump = executeShellCommand(adbCommand);
        final String fpsDumpSys = new String(dump, StandardCharsets.UTF_8);
        final Matcher matcher =
                Pattern.compile("sensorId: (\\d+)" + providerRegex).matcher(fpsDumpSys);

        final List<Integer> ids = new ArrayList<>();
        while (matcher.find()) {
            ids.add(Integer.parseInt(matcher.group(1)));
        }

        return ids;
    }

    /**
     * @param isContentViewNull  Whether the content view is null.
     * @param isBiometricAllowed Whether there is any biometric authenticator allowed.
     * @return Whether biometric prompt without icon should show prior to credential view.
     */
    public static boolean shouldShowBpWithoutIconForCredential(boolean isContentViewNull,
            boolean isBiometricAllowed) {
        return Flags.customBiometricPrompt() && !isContentViewNull && !isBiometricAllowed;
    }
}
