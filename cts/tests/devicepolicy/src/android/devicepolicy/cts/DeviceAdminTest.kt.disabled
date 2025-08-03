package android.devicepolicy.cts

import android.app.admin.DevicePolicyManager
import com.android.bedstead.harrier.BedsteadJUnit4
import com.android.bedstead.harrier.DeviceState
import com.android.bedstead.harrier.annotations.RequireFeature
import com.android.bedstead.nene.TestApis
import com.android.bedstead.nene.packages.CommonPackages
import org.junit.ClassRule
import org.junit.Rule
import org.junit.runner.RunWith


@RunWith(BedsteadJUnit4::class)
@RequireFeature(CommonPackages.FEATURE_DEVICE_ADMIN)
class DeviceAdminTest {

    companion object {
        @JvmField
        @ClassRule
        @Rule
        val deviceState = DeviceState()

        val context = TestApis.context().instrumentedContext()!!

        val localDevicePolicyManager = context.getSystemService(DevicePolicyManager::class.java)!!
    }

    // TODO: b/301249077 Add tests of the disableRequested callback and disabledCallback
}