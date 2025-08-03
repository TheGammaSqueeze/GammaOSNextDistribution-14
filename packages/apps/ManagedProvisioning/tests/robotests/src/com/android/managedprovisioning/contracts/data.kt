package com.android.managedprovisioning.contracts

import android.content.ComponentName
import android.os.Bundle
import com.android.managedprovisioning.model.DisclaimersParam
import com.android.managedprovisioning.model.PackageDownloadInfo
import com.android.managedprovisioning.model.ProvisioningParams
import com.android.managedprovisioning.model.WifiInfo
import com.android.onboarding.contracts.provisioning.ACTIONS
import com.android.onboarding.contracts.provisioning.FLAGS
import com.android.onboarding.contracts.setupwizard.SuwArguments

/**
 * Stub [SuwArguments] prebuilt for testing. Further customisation is available via
 * [SuwArguments.copy] method
 */
val aSuwArguments =
    SuwArguments(
        isSubactivityFirstLaunched = true,
        isSuwSuggestedActionFlow = true,
        isSetupFlow = true,
        preDeferredSetup = true,
        deferredSetup = false,
        firstRun = true,
        portalSetup = false,
        hasMultipleUsers = false,
        theme = "dark",
        wizardBundle = Bundle.EMPTY
    )

val aProvisioningParams: ProvisioningParams = run {
    val flowType = FlowType.AdminIntegrated
    val deviceAdminDownloadInfo =
        PackageDownloadInfo.Builder()
                .setLocation("some/body")
                .setMinVersion(69)
                .setPackageChecksum("checksum".toByteArray())
                .build()
    val deviceAdminComponentName = ComponentName("org.test", "Test")
    val wifiInfo = WifiInfo.Builder().setSsid("MyFi").setPassword("secret").build()
    val packageDownloadInfo = PackageDownloadInfo.Builder()
            .setLocation("http://test.local")
            .setMinVersion(420)
            .setSignatureChecksum("69".toByteArray())
            .setCookieHeader("I'm a cookie monster, gimmie all your cookies or else!")
            .build()
    ProvisioningParams.Builder()
            .setProvisioningAction(ACTIONS.ACTION_PROVISION_MANAGED_PROFILE)
            .setDeviceAdminComponentName(ComponentName("org.test", "local"))
            .setFlowType(flowType.ordinal)
            .setDeviceAdminDownloadInfo(deviceAdminDownloadInfo)
            .setDeviceAdminComponentName(deviceAdminComponentName)
            .setWifiInfo(wifiInfo)
            .setUseMobileData(true)
            .setIsNfc(true)
            .setIsQrProvisioning(true)
            .setSupportUrl("https://help.test")
            .setInitiatorRequestedProvisioningModes(
                FLAGS.FLAG_SUPPORTED_MODES_ORGANIZATION_OWNED or FLAGS.FLAG_SUPPORTED_MODES_DEVICE_OWNER
            )
            .setDisclaimersParam(
                DisclaimersParam.Builder()
                        .setDisclaimers(
                            arrayOf(
                                DisclaimersParam.Disclaimer("a", "a.html"),
                                DisclaimersParam.Disclaimer("b", "b.html"),
                            )
                        )
                        .build()
            )
            .setRoleHolderDownloadInfo(packageDownloadInfo)
            .setIsOrganizationOwnedProvisioning(true)
            .setSkipEducationScreens(true)
            .setReturnBeforePolicyCompliance(true)
            .setDeviceOwnerPermissionGrantOptOut(true)
            .setAllowProvisioningAfterUserSetupComplete(true)
            .build()
}

val aProvisioningArguments: BaseProvisioningArguments = BaseProvisioningArguments(
    provisioningParams = aProvisioningParams,
    flowType = FlowType.entries[aProvisioningParams.flowType],
    deviceAdminDownloadInfo = aProvisioningParams.deviceAdminDownloadInfo,
    deviceAdminComponentName = aProvisioningParams.deviceAdminComponentName,
    wifiInfo = aProvisioningParams.wifiInfo,
    useMobileData = aProvisioningParams.useMobileData,
    isNfc = aProvisioningParams.isNfc,
    isQr = aProvisioningParams.isQrProvisioning,
)
