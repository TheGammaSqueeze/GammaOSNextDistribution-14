package com.android.managedprovisioning.contracts

import android.content.Context
import android.content.Intent
import com.android.managedprovisioning.ManagedProvisioningScreens.DOWNLOAD_ROLE_HOLDER
import com.android.managedprovisioning.ScreenManager
import com.android.managedprovisioning.annotations.LegacyApi
import com.android.managedprovisioning.contracts.Checksum.SignatureChecksum
import com.android.managedprovisioning.model.PackageDownloadInfo
import com.android.managedprovisioning.model.ProvisioningParams
import com.android.onboarding.common.MANAGED_PROVISIONING
import com.android.onboarding.contracts.NodeAwareIntentScope
import com.android.onboarding.contracts.NodeAwareIntentSerializer
import com.android.onboarding.contracts.NodeId
import com.android.onboarding.contracts.OnboardingNodeId
import com.android.onboarding.contracts.VoidOnboardingActivityApiContract
import com.android.onboarding.contracts.annotations.OnboardingNode
import com.android.onboarding.contracts.provisioning.EXTRAS
import com.android.onboarding.contracts.require
import com.android.onboarding.contracts.setupwizard.SuwArguments
import com.android.onboarding.contracts.setupwizard.SuwArgumentsSerializer
import com.android.onboarding.contracts.setupwizard.WithSuwArguments
import javax.inject.Inject

/**
 * @property location Url where the package (.apk) can be downloaded from. {@code null} if there is no download location specified.
 * @property cookieHeader Cookie header for http request.
 * @property checksum SHA-256 hash of the signature in the .apk file
 * @property minVersion Minimum supported version code of the downloaded package.
 */
interface DownloadRoleHolderArguments : WithProvisioningParams, WithSuwArguments {
    val location: String
    val checksum: SignatureChecksum
    val cookieHeader: String?
    val minVersion: Int

    companion object {
        @JvmStatic
        @JvmName("of")
        operator fun invoke(
                suwArguments: SuwArguments,
                provisioningParams: ProvisioningParams,
                location: String,
                checksum: SignatureChecksum,
                cookieHeader: String? = null,
                minVersion: Int = Int.MAX_VALUE,
        ): DownloadRoleHolderArguments = object : DownloadRoleHolderArguments {
            override val suwArguments = suwArguments
            override val provisioningParams = provisioningParams
            override val location = location
            override val checksum = checksum
            override val cookieHeader = cookieHeader
            override val minVersion = minVersion
        }

        @LegacyApi
        @JvmStatic
        @JvmName("of")
        operator fun invoke(
                suwArguments: SuwArguments,
                provisioningParams: ProvisioningParams,
        ): DownloadRoleHolderArguments = invoke(
                suwArguments = suwArguments,
                provisioningParams = provisioningParams,
                location = provisioningParams.roleHolderDownloadInfo?.location
                        ?.require("Download location must not be empty.", String::isNotBlank)
                        ?: error("Missing download location."),
                checksum = provisioningParams.roleHolderDownloadInfo.signatureChecksum
                        .require("Checksum is missing", ByteArray::isNotEmpty)
                        .let(Checksum::SignatureChecksum),
                cookieHeader = provisioningParams.roleHolderDownloadInfo.cookieHeader,
                minVersion = provisioningParams.roleHolderDownloadInfo.minVersion
        )
    }
}

@OnboardingNode(
        component = MANAGED_PROVISIONING,
        name = "DownloadRoleHolder",
        uiType = OnboardingNode.UiType.LOADING)
class DownloadRoleHolderContract
@Inject
constructor(
        @OnboardingNodeId override val nodeId: NodeId,
        private val screenManager: ScreenManager,
        val suwArgumentsSerializer: SuwArgumentsSerializer
) : VoidOnboardingActivityApiContract<DownloadRoleHolderArguments>(),
        NodeAwareIntentSerializer<DownloadRoleHolderArguments> {

    override fun performCreateIntent(context: Context, arg: DownloadRoleHolderArguments): Intent =
            Intent(context, screenManager.getActivityClassForScreen(DOWNLOAD_ROLE_HOLDER))
                    .also { write(it, arg) }

    override fun performExtractArgument(intent: Intent): DownloadRoleHolderArguments = read(intent)
    override fun NodeAwareIntentScope.write(value: DownloadRoleHolderArguments) {
        this[suwArgumentsSerializer] = value::suwArguments
        this[EXTRAS.EXTRA_PROVISIONING_PARAMS] =
                value::provisioningParams.map {
                    it.toBuilder()
                            .setRoleHolderDownloadInfo(
                                    PackageDownloadInfo.Builder()
                                            .setLocation(value.location)
                                            .setCookieHeader(value.cookieHeader)
                                            .setMinVersion(value.minVersion)
                                            .setSignatureChecksum(value.checksum.bytes)
                                            .build())
                            .build()
                }
    }

    override fun NodeAwareIntentScope.read(): DownloadRoleHolderArguments {
        val provisioningParams =
                parcelable<ProvisioningParams>(EXTRAS.EXTRA_PROVISIONING_PARAMS).required
        val params = provisioningParams.map {
            it.roleHolderDownloadInfo ?: error("Missing role holder extras")
        }
        return object : DownloadRoleHolderArguments {
            override val suwArguments by read(suwArgumentsSerializer)
            override val provisioningParams by provisioningParams
            override val location by params.map(PackageDownloadInfo::location)
            override val checksum by params.map(PackageDownloadInfo::signatureChecksum).map(::SignatureChecksum)
            override val cookieHeader by params.map(PackageDownloadInfo::cookieHeader)
            override val minVersion by params.map(PackageDownloadInfo::minVersion)
        }
    }
}