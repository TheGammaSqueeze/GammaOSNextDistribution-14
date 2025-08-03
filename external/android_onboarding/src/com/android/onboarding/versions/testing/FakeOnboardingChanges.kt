package com.android.onboarding.versions.testing

import com.android.onboarding.versions.ComplianceDate
import com.android.onboarding.versions.OnboardingChanges
import com.android.onboarding.versions.annotations.ChangeId
import com.android.onboarding.versions.annotations.ChangeRadius
import com.android.onboarding.versions.changes.ALL_CHANGE_IDS
import java.lang.IllegalArgumentException
import java.lang.IllegalStateException
import java.lang.UnsupportedOperationException

/** Fake implementation of [OnboardingChanges]. */
class FakeOnboardingChanges(private val component: String) : OnboardingChanges {

  private val supportedChanges = mutableMapOf<String, MutableMap<Long, Boolean>>()
  private val packageComplianceDates = mutableMapOf<String, ComplianceDate>()

  /** Set the compliance date of a component. Defaults to [ComplianceDate.EARLIEST]. */
  fun setComplianceDate(component: String, complianceDate: ComplianceDate) {
    packageComplianceDates[extractPackageFromComponent(component)] = complianceDate
  }

  /** Get the compliance date of a component. Defaults to [ComplianceDate.EARLIEST]. */
  fun getComplianceDate(component: String) =
    packageComplianceDates.computeIfAbsent(extractPackageFromComponent(component)) {
      ComplianceDate.EARLIEST
    }

  /** The compliance date of the executing component. */
  var complianceDate: ComplianceDate
    get() = getComplianceDate(extractPackageFromComponent(component))
    set(value) {
      setComplianceDate(extractPackageFromComponent(component), value)
    }

  /** Forces a change to be enabled. */
  fun enableChange(changeId: Long) {
    enableChange(component, changeId)
  }

  /** Forces a change to be enabled. */
  fun enableChange(component: String, changeId: Long) {
    supportedChanges
      .computeIfAbsent(extractPackageFromComponent(component)) { mutableMapOf() }[changeId] = true
  }

  /** Forces a change to be disabled. */
  fun disableChange(changeId: Long) {
    disableChange(component, changeId)
  }

  /** Forces a change to be disabled. */
  fun disableChange(component: String, changeId: Long) {
    supportedChanges
      .computeIfAbsent(extractPackageFromComponent(component)) { mutableMapOf() }[changeId] = false
  }

  /** Resets a change's availability to whatever is correct for the [complianceDate]. */
  fun resetChange(changeId: Long) {
    resetChange(component, changeId)
  }

  /** Resets a change's availability to whatever is correct for the [complianceDate]. */
  fun resetChange(component: String, changeId: Long) {
    supportedChanges
      .computeIfAbsent(extractPackageFromComponent(component)) { mutableMapOf() }
      .remove(changeId)
  }

  override fun currentProcessSupportsChange(changeId: Long): Boolean {
    return componentSupportsChange(component, changeId)
  }

  override fun requireCurrentProcessSupportsChange(changeId: Long) {
    if (currentProcessSupportsChange(changeId)) {
      return
    }

    // currentProcessSupportsChange will throw if this is null
    val changeData = ALL_CHANGE_IDS[changeId]!!

    if (changeData.available == ChangeId.NOT_AVAILABLE) {
      throw UnsupportedOperationException(
        "This API is not available. The change ($changeId) is set to not available. See OnboardingChanges for options for enabling this change during test and development."
      )
    }

    throw UnsupportedOperationException(
      "This API is not available. The change ($changeId) is available as of ${changeData.available} but the compliance date of this package is ${complianceDate}"
    )
  }

  /** This is non-functional on the fake. Set compliance dates directly. */
  override fun loadSupportedChanges(component: String) {}

  override fun componentSupportsChange(component: String, changeId: Long): Boolean {
    val packageName = extractPackageFromComponent(component)

    val changeData =
      ALL_CHANGE_IDS[changeId]
        ?: throw IllegalStateException(
          "Invalid change ID $changeId - this should have been blocked by the conformance checker"
        )

    if (
      changeData.changeRadius == ChangeRadius.SINGLE_COMPONENT &&
        packageName != extractPackageFromComponent(this.component)
    ) {
      // This should be blocked by conformance
      throw IllegalArgumentException(
        "You cannot query changes which are SINGLE_COMPONENT across process boundaries."
      )
    }

    val overrideSupport = supportedChanges[packageName]?.get(changeId)
    if (overrideSupport != null) {
      return overrideSupport
    }

    // (this call should be blocked by conformance)
    if (changeData.released != ChangeId.NOT_RELEASED) {
      return true
    }

    // The fake does not support the "if it cannot be found then we assume no support" functionality

    return getComplianceDate(packageName).isAvailable(changeData)
  }

  private fun extractPackageFromComponent(component: String) = component.split("/", limit = 2)[0]
}
