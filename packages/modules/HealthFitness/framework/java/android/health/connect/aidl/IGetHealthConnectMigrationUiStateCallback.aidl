package android.health.connect.aidl;

import android.health.connect.migration.HealthConnectMigrationUiState;
import android.health.connect.aidl.HealthConnectExceptionParcel;
/**
 * Callback for {@link IHealthConnectService#getHealthConnectMigrationUiState}.
 * @hide
 */
interface IGetHealthConnectMigrationUiStateCallback {
    oneway void onResult(in HealthConnectMigrationUiState migrationUiState);
    oneway void onError(in HealthConnectExceptionParcel exception);
}