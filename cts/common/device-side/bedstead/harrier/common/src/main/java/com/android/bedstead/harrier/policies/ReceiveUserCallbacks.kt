package com.android.bedstead.harrier.policies

import com.android.bedstead.harrier.annotations.enterprise.EnterprisePolicy
import com.android.bedstead.harrier.annotations.enterprise.EnterprisePolicy.APPLIED_BY_DEVICE_OWNER
import com.android.bedstead.harrier.annotations.enterprise.EnterprisePolicy.APPLIED_BY_PROFILE_OWNER_PROFILE
import com.android.bedstead.harrier.annotations.enterprise.EnterprisePolicy.APPLIED_BY_PROFILE_OWNER_USER_WITH_NO_DO
import com.android.bedstead.harrier.annotations.enterprise.EnterprisePolicy.APPLIES_TO_OWN_USER
import com.android.bedstead.harrier.annotations.enterprise.EnterprisePolicy.APPLIES_TO_PARENT

/**
 * Policy for receiving a callback when user state changes.
 */
@EnterprisePolicy(
    dpc = [APPLIED_BY_DEVICE_OWNER or APPLIES_TO_OWN_USER]
)
class ReceiveUserCallbacks {
}