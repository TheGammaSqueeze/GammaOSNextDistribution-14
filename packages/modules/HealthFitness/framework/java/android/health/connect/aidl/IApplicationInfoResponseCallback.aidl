package android.health.connect.aidl;

import android.health.connect.aidl.ApplicationInfoResponseParcel;
import android.health.connect.aidl.HealthConnectExceptionParcel;

/**
 * Callback for {@link IHealthConnectService#getApplicationInfo}.
 *
 * {@hide}
 */
interface IApplicationInfoResponseCallback {
    // Called on a successful operation
    oneway void onResult(in ApplicationInfoResponseParcel parcel);
    // Called when an error is hit
    oneway void onError(in HealthConnectExceptionParcel exception);
}