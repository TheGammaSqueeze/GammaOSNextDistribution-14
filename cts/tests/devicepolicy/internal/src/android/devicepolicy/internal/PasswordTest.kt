package android.devicepolicy.internal


import android.app.admin.DevicePolicyManager
import com.android.bedstead.harrier.BedsteadJUnit4
import com.android.bedstead.harrier.DeviceState
import com.android.bedstead.harrier.annotations.HiddenApiTest
import com.android.bedstead.nene.TestApis
import org.junit.Assert.assertThrows
import org.junit.ClassRule
import org.junit.Rule
import org.junit.runner.RunWith

@RunWith(BedsteadJUnit4::class)
class PasswordTest {

    companion object {
        @JvmField @ClassRule @Rule
        val deviceState = DeviceState()
        val localDevicePolicyManager = TestApis.context().instrumentedContext()
                .getSystemService(DevicePolicyManager::class.java)!!
    }

    @HiddenApiTest
    fun canProfileOwnerResetPasswordWhenLocked_nonSystemUid_throwsException() {
        assertThrows(SecurityException::class.java) {
            localDevicePolicyManager.canProfileOwnerResetPasswordWhenLocked(
                    TestApis.users().instrumented().id())
        }
    }

}
