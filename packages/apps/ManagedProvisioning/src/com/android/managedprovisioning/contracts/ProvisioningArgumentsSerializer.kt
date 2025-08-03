package com.android.managedprovisioning.contracts

import android.content.ComponentName
import com.android.managedprovisioning.annotations.LegacyApi
import com.android.managedprovisioning.model.PackageDownloadInfo
import com.android.managedprovisioning.model.ProvisioningParams
import com.android.managedprovisioning.model.WifiInfo
import com.android.onboarding.contracts.NodeAwareIntentScope
import com.android.onboarding.contracts.NodeAwareIntentSerializer
import com.android.onboarding.contracts.NodeId
import com.android.onboarding.contracts.OnboardingNodeId
import com.android.onboarding.contracts.provisioning.EXTRAS
import javax.inject.Inject

interface WithProvisioningParams : WithOptionalProvisioningParams {
    override val provisioningParams: ProvisioningParams
}

interface WithOptionalProvisioningParams {
    /** Only needed to preserve data parity when passing provisioning params bundle along */
    val provisioningParams: ProvisioningParams?
}

enum class FlowType {
    Unspecified,
    Legacy,
    AdminIntegrated
}

interface BaseProvisioningArguments : WithProvisioningParams {
    val flowType: FlowType
    val deviceAdminDownloadInfo: PackageDownloadInfo?
    val deviceAdminComponentName: ComponentName?
    val wifiInfo: WifiInfo?
    val useMobileData: Boolean
    val isNfc: Boolean
    val isQr: Boolean

    companion object {
        @JvmStatic
        @JvmName("of")
        operator fun invoke(
                provisioningParams: ProvisioningParams,
                flowType: FlowType,
                deviceAdminDownloadInfo: PackageDownloadInfo?,
                deviceAdminComponentName: ComponentName?,
                wifiInfo: WifiInfo?,
                useMobileData: Boolean,
                isNfc: Boolean,
                isQr: Boolean,
        ): BaseProvisioningArguments = object : BaseProvisioningArguments {
            override val provisioningParams = provisioningParams
            override val flowType = flowType
            override val deviceAdminDownloadInfo = deviceAdminDownloadInfo
            override val deviceAdminComponentName = deviceAdminComponentName
            override val wifiInfo = wifiInfo
            override val useMobileData = useMobileData
            override val isNfc = isNfc
            override val isQr = isQr
        }

        @LegacyApi
        @JvmStatic
        @JvmName("of")
        operator fun invoke(
                provisioningParams: ProvisioningParams
        ): BaseProvisioningArguments = invoke(
                provisioningParams = provisioningParams,
                flowType = FlowType.entries[provisioningParams.flowType],
                deviceAdminDownloadInfo = provisioningParams.deviceAdminDownloadInfo,
                deviceAdminComponentName = provisioningParams.deviceAdminComponentName,
                wifiInfo = provisioningParams.wifiInfo,
                useMobileData = provisioningParams.useMobileData,
                isNfc = provisioningParams.isNfc,
                isQr = provisioningParams.isQrProvisioning,
        )
    }
}

class ProvisioningArgumentsSerializer @Inject constructor(@OnboardingNodeId override val nodeId: NodeId) :
        NodeAwareIntentSerializer<BaseProvisioningArguments> {

    override fun NodeAwareIntentScope.write(value: BaseProvisioningArguments) {
        val params =
                value::provisioningParams.map {
                    it
                            .toBuilder()
                            .setFlowType(value.flowType.ordinal)
                            .setDeviceAdminDownloadInfo(value.deviceAdminDownloadInfo)
                            .setDeviceAdminComponentName(value.deviceAdminComponentName)
                            .setWifiInfo(value.wifiInfo)
                            .setUseMobileData(value.useMobileData)
                            .setIsNfc(value.isNfc)
                            .setIsQrProvisioning(value.isQr)
                            .build()
                }
        this[EXTRAS.EXTRA_PROVISIONING_PARAMS] = params
    }

    override fun NodeAwareIntentScope.read(): BaseProvisioningArguments {
        val params = parcelable<ProvisioningParams>(EXTRAS.EXTRA_PROVISIONING_PARAMS).required
        return object : BaseProvisioningArguments {
            override val provisioningParams by params
            override val flowType by params.map(ProvisioningParams::flowType).map(FlowType.entries::get)
            override val deviceAdminDownloadInfo by params.map(ProvisioningParams::deviceAdminDownloadInfo)
            override val deviceAdminComponentName by params.map(ProvisioningParams::deviceAdminComponentName)
            override val wifiInfo by params.map(ProvisioningParams::wifiInfo)
            override val useMobileData by params.map(ProvisioningParams::useMobileData)
            override val isNfc by params.map(ProvisioningParams::isNfc)
            override val isQr by params.map(ProvisioningParams::isQrProvisioning)
        }
    }

}
