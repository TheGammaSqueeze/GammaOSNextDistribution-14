package android.health.connect.aidl;

import android.health.connect.aidl.GetPriorityResponseParcel;
import android.health.connect.aidl.HealthConnectExceptionParcel;

/**
 * Callback for {@link IHealthConnectService#getCurrentPriority}.
 *
 * {@hide}
 */
interface IGetPriorityResponseCallback {
    // Called on a successful operation
    oneway void onResult(in GetPriorityResponseParcel parcel);
    // Called when an error is hit
    oneway void onError(in HealthConnectExceptionParcel exception);
}
