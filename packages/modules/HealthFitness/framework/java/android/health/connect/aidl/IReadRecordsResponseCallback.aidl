package android.health.connect.aidl;

import android.health.connect.aidl.ReadRecordsResponseParcel;
import android.health.connect.aidl.HealthConnectExceptionParcel;

/**
 * Callback for {@link IHealthConnectService#readRecord}.
 *
 * {@hide}
 */
interface IReadRecordsResponseCallback {
    // Called on a successful operation
    oneway void onResult(in ReadRecordsResponseParcel parcel);
    // Called when an error is hit
    oneway void onError(in HealthConnectExceptionParcel exception);
}
