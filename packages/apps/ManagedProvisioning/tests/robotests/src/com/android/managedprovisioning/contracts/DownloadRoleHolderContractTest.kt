package com.android.managedprovisioning.contracts

import android.os.Build
import com.android.onboarding.contracts.NodeId
import com.android.onboarding.contracts.OnboardingNodeId
import com.android.onboarding.contracts.testing.NodeAwareIntentSerializerTest
import com.android.onboarding.contracts.testing.TEST_NODE_ID
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import org.junit.Before
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import javax.inject.Inject

@RunWith(RobolectricTestRunner::class)
@Config(minSdk = Build.VERSION_CODES.VANILLA_ICE_CREAM, application = HiltTestApplication::class)
@HiltAndroidTest
class DownloadRoleHolderContractTest : NodeAwareIntentSerializerTest<DownloadRoleHolderArguments>() {
    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @BindValue
    @OnboardingNodeId
    @Suppress("Unused")
    val nodeId: NodeId = TEST_NODE_ID

    @Inject
    override lateinit var target: DownloadRoleHolderContract
    override val data = requireNotNull(aProvisioningParams.roleHolderDownloadInfo).let {
        DownloadRoleHolderArguments(
                suwArguments = aSuwArguments,
                provisioningParams = aProvisioningParams,
                location = it.location,
                checksum = Checksum.SignatureChecksum(it.signatureChecksum),
                cookieHeader = it.cookieHeader,
                minVersion = it.minVersion,
        )
    }

    @Before
    fun setUp() {
        hiltRule.inject()
    }
}
