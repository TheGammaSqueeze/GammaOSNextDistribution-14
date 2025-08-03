package com.android.tv.settings.library.about;

import android.annotation.ColorInt;
import android.annotation.Nullable;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.pm.Signature;
import android.content.res.ColorStateList;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.drawable.Drawable;
import android.media.AudioManager;
import android.net.NetworkCapabilities;
import android.net.vcn.VcnTransportInfo;
import android.net.wifi.WifiInfo;
import android.os.BatteryManager;
import android.telephony.AccessNetworkConstants;
import android.telephony.NetworkRegistrationInfo;
import android.telephony.ServiceState;
import android.telephony.TelephonyManager;

import androidx.annotation.NonNull;
import androidx.core.graphics.drawable.RoundedBitmapDrawable;
import androidx.core.graphics.drawable.RoundedBitmapDrawableFactory;

import java.text.NumberFormat;

public class Utils {

    /** Formats a double from 0.0..100.0 with an option to round **/
    public static String formatPercentage(double percentage, boolean round) {
        final int localPercentage = round ? Math.round((float) percentage) : (int) percentage;
        return formatPercentage(localPercentage);
    }

    /** Formats the ratio of amount/total as a percentage. */
    public static String formatPercentage(long amount, long total) {
        return formatPercentage(((double) amount) / total);
    }

    /** Formats an integer from 0..100 as a percentage. */
    public static String formatPercentage(int percentage) {
        return formatPercentage(((double) percentage) / 100.0);
    }

    /** Formats a double from 0.0..1.0 as a percentage. */
    public static String formatPercentage(double percentage) {
        return NumberFormat.getPercentInstance().format(percentage);
    }

    public static int getBatteryLevel(Intent batteryChangedIntent) {
        int level = batteryChangedIntent.getIntExtra(BatteryManager.EXTRA_LEVEL, 0);
        int scale = batteryChangedIntent.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
        return (level * 100) / scale;
    }

    public static ColorStateList getColorAccent(Context context) {
        return getColorAttr(context, android.R.attr.colorAccent);
    }

    public static ColorStateList getColorError(Context context) {
        return getColorAttr(context, android.R.attr.colorError);
    }

    @ColorInt
    public static int getColorAccentDefaultColor(Context context) {
        return getColorAttrDefaultColor(context, android.R.attr.colorAccent);
    }

    @ColorInt
    public static int getColorErrorDefaultColor(Context context) {
        return getColorAttrDefaultColor(context, android.R.attr.colorError);
    }

    @ColorInt
    public static int getColorStateListDefaultColor(Context context, int resId) {
        final ColorStateList list =
                context.getResources().getColorStateList(resId, context.getTheme());
        return list.getDefaultColor();
    }

    /**
     * This method computes disabled color from normal color
     *
     * @param context    the context
     * @param inputColor normal color.
     * @return disabled color.
     */
    @ColorInt
    public static int getDisabled(Context context, int inputColor) {
        return applyAlphaAttr(context, android.R.attr.disabledAlpha, inputColor);
    }

    @ColorInt
    public static int applyAlphaAttr(Context context, int attr, int inputColor) {
        TypedArray ta = context.obtainStyledAttributes(new int[]{attr});
        float alpha = ta.getFloat(0, 0);
        ta.recycle();
        return applyAlpha(alpha, inputColor);
    }

    @ColorInt
    public static int applyAlpha(float alpha, int inputColor) {
        alpha *= Color.alpha(inputColor);
        return Color.argb((int) (alpha), Color.red(inputColor), Color.green(inputColor),
                Color.blue(inputColor));
    }

    @ColorInt
    public static int getColorAttrDefaultColor(Context context, int attr) {
        TypedArray ta = context.obtainStyledAttributes(new int[]{attr});
        @ColorInt int colorAccent = ta.getColor(0, 0);
        ta.recycle();
        return colorAccent;
    }

    public static ColorStateList getColorAttr(Context context, int attr) {
        TypedArray ta = context.obtainStyledAttributes(new int[]{attr});
        ColorStateList stateList = null;
        try {
            stateList = ta.getColorStateList(0);
        } finally {
            ta.recycle();
        }
        return stateList;
    }

    public static int getThemeAttr(Context context, int attr) {
        return getThemeAttr(context, attr, 0);
    }

    public static int getThemeAttr(Context context, int attr, int defaultValue) {
        TypedArray ta = context.obtainStyledAttributes(new int[]{attr});
        int theme = ta.getResourceId(0, defaultValue);
        ta.recycle();
        return theme;
    }

    public static Drawable getDrawable(Context context, int attr) {
        TypedArray ta = context.obtainStyledAttributes(new int[]{attr});
        Drawable drawable = ta.getDrawable(0);
        ta.recycle();
        return drawable;
    }

    /**
     * Create a color matrix suitable for a ColorMatrixColorFilter that modifies only the color but
     * preserves the alpha for a given drawable
     *
     * @return a color matrix that uses the source alpha and given color
     */
    public static ColorMatrix getAlphaInvariantColorMatrixForColor(@ColorInt int color) {
        int r = Color.red(color);
        int g = Color.green(color);
        int b = Color.blue(color);

        ColorMatrix cm = new ColorMatrix(new float[]{
                0, 0, 0, 0, r,
                0, 0, 0, 0, g,
                0, 0, 0, 0, b,
                0, 0, 0, 1, 0});

        return cm;
    }

    /**
     * Create a ColorMatrixColorFilter to tint a drawable but retain its alpha characteristics
     *
     * @return a ColorMatrixColorFilter which changes the color of the output but is invariant on
     * the source alpha
     */
    public static ColorFilter getAlphaInvariantColorFilterForColor(@ColorInt int color) {
        return new ColorMatrixColorFilter(getAlphaInvariantColorMatrixForColor(color));
    }


    private static Signature getFirstSignature(PackageInfo pkg) {
        if (pkg != null && pkg.signatures != null && pkg.signatures.length > 0) {
            return pkg.signatures[0];
        }
        return null;
    }

    private static Signature getSystemSignature(PackageManager pm) {
        try {
            final PackageInfo sys = pm.getPackageInfo("android", PackageManager.GET_SIGNATURES);
            return getFirstSignature(sys);
        } catch (NameNotFoundException e) {
        }
        return null;
    }

    public static boolean isWifiOnly(Context context) {
        return !context.getSystemService(TelephonyManager.class).isDataCapable();
    }



    /**
     * get that {@link AudioManager#getMode()} is in ringing/call/communication(VoIP) status.
     */
    public static boolean isAudioModeOngoingCall(Context context) {
        final AudioManager audioManager = context.getSystemService(AudioManager.class);
        final int audioMode = audioManager.getMode();
        return audioMode == AudioManager.MODE_RINGTONE
                || audioMode == AudioManager.MODE_IN_CALL
                || audioMode == AudioManager.MODE_IN_COMMUNICATION;
    }

    /**
     * Return the service state is in-service or not.
     * To make behavior consistent with SystemUI and Settings/AboutPhone/SIM status UI
     *
     * @param serviceState Service state. {@link ServiceState}
     */
    public static boolean isInService(ServiceState serviceState) {
        if (serviceState == null) {
            return false;
        }
        int state = getCombinedServiceState(serviceState);
        return state != ServiceState.STATE_POWER_OFF
                && state != ServiceState.STATE_OUT_OF_SERVICE
                && state != ServiceState.STATE_EMERGENCY_ONLY;
    }

    /**
     * Return the combined service state.
     * To make behavior consistent with SystemUI and Settings/AboutPhone/SIM status UI
     *
     * @param serviceState Service state. {@link ServiceState}
     */
    public static int getCombinedServiceState(ServiceState serviceState) {
        if (serviceState == null) {
            return ServiceState.STATE_OUT_OF_SERVICE;
        }

        // Consider the device to be in service if either voice or data
        // service is available. Some SIM cards are marketed as data-only
        // and do not support voice service, and on these SIM cards, we
        // want to show signal bars for data service as well as the "no
        // service" or "emergency calls only" text that indicates that voice
        // is not available. Note that we ignore the IWLAN service state
        // because that state indicates the use of VoWIFI and not cell service
        final int state = serviceState.getState();
        final int dataState = serviceState.getDataRegistrationState();

        if (state == ServiceState.STATE_OUT_OF_SERVICE
                || state == ServiceState.STATE_EMERGENCY_ONLY) {
            if (dataState == ServiceState.STATE_IN_SERVICE && isNotInIwlan(serviceState)) {
                return ServiceState.STATE_IN_SERVICE;
            }
        }
        return state;
    }

    private static boolean isNotInIwlan(ServiceState serviceState) {
        final NetworkRegistrationInfo networkRegWlan = serviceState.getNetworkRegistrationInfo(
                NetworkRegistrationInfo.DOMAIN_PS,
                AccessNetworkConstants.TRANSPORT_TYPE_WLAN);
        if (networkRegWlan == null) {
            return true;
        }

        final boolean isInIwlan = (networkRegWlan.getRegistrationState()
                == NetworkRegistrationInfo.REGISTRATION_STATE_HOME)
                || (networkRegWlan.getRegistrationState()
                == NetworkRegistrationInfo.REGISTRATION_STATE_ROAMING);
        return !isInIwlan;
    }

    /**
     * Returns a bitmap with rounded corner.
     *
     * @param context      application context.
     * @param source       bitmap to apply round corner.
     * @param cornerRadius corner radius value.
     */
    public static Bitmap convertCornerRadiusBitmap(@NonNull Context context,
            @NonNull Bitmap source, @NonNull float cornerRadius) {
        final Bitmap roundedBitmap = Bitmap.createBitmap(source.getWidth(), source.getHeight(),
                Bitmap.Config.ARGB_8888);
        final RoundedBitmapDrawable drawable =
                RoundedBitmapDrawableFactory.create(context.getResources(), source);
        drawable.setAntiAlias(true);
        drawable.setCornerRadius(cornerRadius);
        final Canvas canvas = new Canvas(roundedBitmap);
        drawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
        drawable.draw(canvas);
        return roundedBitmap;
    }

    /**
     * Returns the WifiInfo for the underlying WiFi network of the VCN network, returns null if the
     * input NetworkCapabilities is not for a VCN network with underlying WiFi network.
     *
     * @param networkCapabilities NetworkCapabilities of the network.
     */
    @Nullable
    public static WifiInfo tryGetWifiInfoForVcn(NetworkCapabilities networkCapabilities) {
        if (networkCapabilities.getTransportInfo() == null
                || !(networkCapabilities.getTransportInfo() instanceof VcnTransportInfo)) {
            return null;
        }
        VcnTransportInfo vcnTransportInfo =
                (VcnTransportInfo) networkCapabilities.getTransportInfo();
        return vcnTransportInfo.getWifiInfo();
    }
}
