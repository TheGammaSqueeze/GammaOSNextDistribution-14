package android.health.connect.aidl;

import android.health.connect.aidl.ActivityDatesResponseParcel;
import android.health.connect.aidl.HealthConnectExceptionParcel;

/**
 * Callback for {@link IHealthConnectService#getActivityDates}.
 *
 * {@hide}
 */
interface IActivityDatesResponseCallback {
    // Called on a successful operation
    oneway void onResult(in ActivityDatesResponseParcel parcel);
    // Called when an error is hit
    oneway void onError(in HealthConnectExceptionParcel exception);
}