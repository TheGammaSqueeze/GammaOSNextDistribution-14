package android.health.connect;

/**
 * Represents the state of HealthConnect data as it goes through one of the following operations:
 * <li>Data Restore: fetching and restoring the data either from the cloud or from another device.
 * <li>Data Migration: migrating the data from the app using the data-migration APIs: {@link
 *     HealthConnectManager#startMigration}, {@link HealthConnectManager#writeMigrationData}, and
 *     {@link HealthConnectManager#finishMigration}
 *
 * @hide
 */
parcelable HealthConnectDataState;