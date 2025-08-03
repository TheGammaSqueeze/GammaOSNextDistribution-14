package com.android.onboarding.contracts.setupwizard

import com.android.onboarding.contracts.NodeAwareIntentScope
import com.android.onboarding.contracts.NodeAwareIntentSerializer
import com.android.onboarding.contracts.OnboardingNodeId
import com.google.android.setupcompat.util.WizardManagerHelper
import javax.inject.Inject

class SuwArgumentsSerializer @Inject constructor(@OnboardingNodeId override val nodeId: Long) :
  NodeAwareIntentSerializer<SuwArguments> {

  override fun NodeAwareIntentScope.write(value: SuwArguments) {
    intent[WizardManagerHelper.EXTRA_IS_SUW_SUGGESTED_ACTION_FLOW] = value::isSuwSuggestedActionFlow
    intent[WizardManagerHelper.EXTRA_IS_SETUP_FLOW] = value::isSetupFlow
    intent[WizardManagerHelper.EXTRA_IS_PRE_DEFERRED_SETUP] = value::preDeferredSetup
    intent[WizardManagerHelper.EXTRA_IS_DEFERRED_SETUP] = value::deferredSetup
    intent[WizardManagerHelper.EXTRA_IS_FIRST_RUN] = value::firstRun
    intent[WizardManagerHelper.EXTRA_IS_PORTAL_SETUP] = value::portalSetup
    intent[EXTRA_WIZARD_BUNDLE] = value::wizardBundle
    intent[WizardManagerHelper.EXTRA_THEME] = value::theme
    intent[EXTRA_HAS_MULTIPLE_USERS] = value::hasMultipleUsers
    intent[EXTRA_IS_SUBACTIVITY_FIRST_LAUNCHED] = value::isSubactivityFirstLaunched
  }

  override fun NodeAwareIntentScope.read(): SuwArguments =
    object : SuwArguments {
      override val isSuwSuggestedActionFlow by
        boolean(WizardManagerHelper.EXTRA_IS_SUW_SUGGESTED_ACTION_FLOW).required
      override val isSetupFlow by boolean(WizardManagerHelper.EXTRA_IS_SETUP_FLOW).required
      override val preDeferredSetup by
        boolean(WizardManagerHelper.EXTRA_IS_PRE_DEFERRED_SETUP).required
      override val deferredSetup by boolean(WizardManagerHelper.EXTRA_IS_DEFERRED_SETUP).required
      override val firstRun by boolean(WizardManagerHelper.EXTRA_IS_FIRST_RUN).required
      override val portalSetup by boolean(WizardManagerHelper.EXTRA_IS_PORTAL_SETUP).required
      override val wizardBundle by bundle(EXTRA_WIZARD_BUNDLE).required
      override val theme by string(WizardManagerHelper.EXTRA_THEME).required
      override val hasMultipleUsers by boolean(EXTRA_HAS_MULTIPLE_USERS)
      override val isSubactivityFirstLaunched by boolean(EXTRA_IS_SUBACTIVITY_FIRST_LAUNCHED)
    }

  private companion object {
    const val EXTRA_IS_SUBACTIVITY_FIRST_LAUNCHED = "isSubactivityFirstLaunched"
    const val EXTRA_HAS_MULTIPLE_USERS = "hasMultipleUsers"
    const val EXTRA_WIZARD_BUNDLE = "wizardBundle"
  }
}
