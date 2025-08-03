package android.health.connect.aidl;

import android.health.connect.changelog.ChangeLogsResponse;
import android.health.connect.aidl.HealthConnectExceptionParcel;

/**
 * Callback for {@link HealthConnectManager#getChangeLogs}
 * {@hide}
 */
interface IChangeLogsResponseCallback {
    // Called on a successful operation
    oneway void onResult(in ChangeLogsResponse parcel);
    // Called when an error is hit
    oneway void onError(in HealthConnectExceptionParcel exception);
}
