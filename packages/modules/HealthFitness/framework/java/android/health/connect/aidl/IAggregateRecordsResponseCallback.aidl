package android.health.connect.aidl;

import android.health.connect.aidl.AggregateDataResponseParcel;
import android.health.connect.aidl.HealthConnectExceptionParcel;

/**
 * Callback for {@link IHealthConnectService#aggregateRecords}.
 *
 * {@hide}
 */
interface IAggregateRecordsResponseCallback {
    // Called on a successful operation
    oneway void onResult(in AggregateDataResponseParcel parcel);
    // Called when an error is hit
    oneway void onError(in HealthConnectExceptionParcel exception);
}
