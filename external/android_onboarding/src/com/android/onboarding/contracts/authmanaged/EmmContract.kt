package com.android.onboarding.contracts.authmanaged

import android.accounts.Account
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.annotation.RequiresApi
import com.android.onboarding.common.AUTH_MANAGED
import com.android.onboarding.contracts.ContractResult
import com.android.onboarding.contracts.NodeAwareIntentScope
import com.android.onboarding.contracts.NodeAwareIntentSerializer
import com.android.onboarding.contracts.NodeId
import com.android.onboarding.contracts.OnboardingActivityApiContract
import com.android.onboarding.contracts.OnboardingNodeId
import com.android.onboarding.contracts.annotations.OnboardingNode
import com.android.onboarding.contracts.setupwizard.SuwArguments
import com.android.onboarding.contracts.setupwizard.SuwArgumentsSerializer
import com.android.onboarding.contracts.setupwizard.WithSuwArguments
import javax.inject.Inject

// LINT.IfChange
const val UNMANAGED_WORK_PROFILE_MODE_UNSPECIFIED: Int = 0

// LINT.ThenChange(//depot/google3/java/com/google/android/gmscore/integ/libs/common_auth/src/com/google/android/gms/common/auth/ui/ManagedAccountUtil.java)

interface EmmArguments : WithSuwArguments {
  val account: Account
  val options: Bundle
  val flow: Int?
  val dmStatus: String?
  val isSetupWizard: Boolean
  val suppressDeviceManagement: Boolean
  val callingPackage: String
  val isMainUser: Boolean
  val isUnicornAccount: Boolean
  val unmanagedWorkProfileMode: Int

  companion object {
    @JvmStatic
    @JvmName("of")
    operator fun invoke(
      suwArguments: SuwArguments,
      account: Account,
      options: Bundle = Bundle.EMPTY,
      flow: Int? = null,
      dmStatus: String? = null,
      isSetupWizard: Boolean = false,
      suppressDeviceManagement: Boolean = false,
      callingPackage: String = "",
      isMainUser: Boolean = false,
      isUnicornAccount: Boolean = false,
      unmanagedWorkProfileMode: Int = UNMANAGED_WORK_PROFILE_MODE_UNSPECIFIED,
    ): EmmArguments =
      object : EmmArguments {
        override val suwArguments = suwArguments
        override val account = account
        override val options = options
        override val flow = flow
        override val dmStatus = dmStatus
        override val isSetupWizard = isSetupWizard
        override val suppressDeviceManagement = suppressDeviceManagement
        override val callingPackage = callingPackage
        override val isMainUser = isMainUser
        override val isUnicornAccount = isUnicornAccount
        override val unmanagedWorkProfileMode = unmanagedWorkProfileMode
      }
  }
}

sealed interface EmmResult {
  val code: Int

  sealed interface Success : EmmResult {
    data object OK : Success {
      override val code = Activity.RESULT_OK
    }

    data class Unknown(override val code: Int) : Success
  }

  sealed interface Failure : EmmResult {
    data object LaunchAppFailed : Failure {
      override val code = RESULTS.RESULT_LAUNCH_APP_FAILED
    }

    data object FetchAppInfoFailed : Failure {
      override val code = RESULTS.RESULT_FETCH_APP_INFO_FAILED
    }

    data object DownloadInstallFailed : Failure {
      override val code = RESULTS.RESULT_DOWNLOAD_INSTALL_FAILED
    }

    data object DmNotSupported : Failure {
      override val code = RESULTS.RESULT_DM_NOT_SUPPORTED
    }

    data object DownloadInstallCanceled : Failure {
      override val code = RESULTS.RESULT_DOWNLOAD_INSTALL_CANCELED
    }

    data object ShouldSkipDm : Failure {
      override val code = RESULTS.RESULT_SHOULD_SKIP_DM
    }

    data object SetupSuppressedByCaller : Failure {
      override val code = RESULTS.RESULT_SETUP_SUPPRESSED_BY_CALLER
    }

    data object DisallowAddingManagedAccount : Failure {
      override val code = RESULTS.RESULT_DISALLOW_ADDING_MANAGED_ACCOUNT
    }

    data object CancelledForceRemoveAccount : Failure {
      override val code = RESULTS.RESULT_CANCELLED_FORCE_REMOVE_ACCOUNT
    }

    data object Cancelled : Failure {
      override val code = Activity.RESULT_CANCELED
    }

    data class Unknown(override val code: Int) : Failure
  }
}

/**
 * Contract for {@link EmmChimeraActivity}. Note that the component of this contract intent is
 * assumed to be set by the caller if launching via Component
 */
@OnboardingNode(component = AUTH_MANAGED, name = "Emm", uiType = OnboardingNode.UiType.INVISIBLE)
@RequiresApi(Build.VERSION_CODES.Q)
class EmmContract
@Inject
constructor(
  @OnboardingNodeId override val nodeId: NodeId,
  val suwArgumentsSerializer: SuwArgumentsSerializer,
) :
  OnboardingActivityApiContract<EmmArguments, EmmResult>(),
  NodeAwareIntentSerializer<EmmArguments> {

  override fun performCreateIntent(context: Context, arg: EmmArguments): Intent = Intent(arg = arg)

  override fun performExtractArgument(intent: Intent): EmmArguments = read(intent)

  override fun performParseResult(result: ContractResult): EmmResult =
    when {
      result.resultCode == RESULTS.RESULT_LAUNCH_APP_FAILED -> EmmResult.Failure.LaunchAppFailed
      result.resultCode == RESULTS.RESULT_FETCH_APP_INFO_FAILED ->
        EmmResult.Failure.FetchAppInfoFailed
      result.resultCode == RESULTS.RESULT_DOWNLOAD_INSTALL_FAILED ->
        EmmResult.Failure.DownloadInstallFailed
      result.resultCode == RESULTS.RESULT_DM_NOT_SUPPORTED -> EmmResult.Failure.DmNotSupported
      result.resultCode == RESULTS.RESULT_DOWNLOAD_INSTALL_CANCELED ->
        EmmResult.Failure.DownloadInstallCanceled
      result.resultCode == RESULTS.RESULT_SHOULD_SKIP_DM -> EmmResult.Failure.ShouldSkipDm
      result.resultCode == RESULTS.RESULT_SETUP_SUPPRESSED_BY_CALLER ->
        EmmResult.Failure.SetupSuppressedByCaller
      result.resultCode == RESULTS.RESULT_DISALLOW_ADDING_MANAGED_ACCOUNT ->
        EmmResult.Failure.DisallowAddingManagedAccount
      result.resultCode == RESULTS.RESULT_CANCELLED_FORCE_REMOVE_ACCOUNT ->
        EmmResult.Failure.CancelledForceRemoveAccount
      result.resultCode == Activity.RESULT_CANCELED -> EmmResult.Failure.Cancelled
      result.resultCode == Activity.RESULT_OK -> EmmResult.Success.OK
      result.resultCode > 0 -> EmmResult.Success.Unknown(result.resultCode)
      else -> EmmResult.Failure.Unknown(result.resultCode)
    }

  override fun performSetResult(result: EmmResult): ContractResult =
    if (result is EmmResult.Success) {
      ContractResult.Success(result.code)
    } else {
      ContractResult.Failure(result.code)
    }

  override fun NodeAwareIntentScope.write(value: EmmArguments) {
    intent[suwArgumentsSerializer] = value::suwArguments
    intent[EXTRAS.EXTRA_ACCOUNT] = value::account
    intent[EXTRAS.EXTRA_IS_SETUP_WIZARD] = value::isSetupWizard
    intent[EXTRAS.EXTRA_SUPPRESS_DEVICE_MANAGEMENT] = value::suppressDeviceManagement
    intent[EXTRAS.EXTRA_CALLING_PACKAGE] = value::callingPackage
    intent[EXTRAS.EXTRA_IS_USER_OWNER] = value::isMainUser
    intent[EXTRAS.EXTRA_DM_STATUS] = value::dmStatus
    intent[EXTRAS.EXTRA_IS_UNICORN_ACCOUNT] = value::isUnicornAccount
    intent[EXTRAS.EXTRA_FLOW] = value::flow
    intent[EXTRAS.EXTRA_OPTIONS] = value::options
    intent[EXTRAS.EXTRA_UNMANAGED_WORK_PROFILE_MODE] = value::unmanagedWorkProfileMode
  }

  override fun NodeAwareIntentScope.read(): EmmArguments =
    object : EmmArguments {
      override val suwArguments by read(suwArgumentsSerializer)
      override val account by parcelable<Account>(EXTRAS.EXTRA_ACCOUNT).required
      override val options by parcelable<Bundle>(EXTRAS.EXTRA_OPTIONS) or Bundle.EMPTY
      override val flow by int(EXTRAS.EXTRA_FLOW)
      override val dmStatus by string(EXTRAS.EXTRA_DM_STATUS)
      override val isSetupWizard by boolean(EXTRAS.EXTRA_IS_SETUP_WIZARD) or false
      override val suppressDeviceManagement by
        boolean(EXTRAS.EXTRA_SUPPRESS_DEVICE_MANAGEMENT) or false
      override val callingPackage by string(EXTRAS.EXTRA_CALLING_PACKAGE) or ""
      override val isMainUser by boolean(EXTRAS.EXTRA_IS_USER_OWNER) or false
      override val isUnicornAccount by boolean(EXTRAS.EXTRA_IS_UNICORN_ACCOUNT) or false
      override val unmanagedWorkProfileMode by
        int(EXTRAS.EXTRA_UNMANAGED_WORK_PROFILE_MODE) or UNMANAGED_WORK_PROFILE_MODE_UNSPECIFIED
    }
}
