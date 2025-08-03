package android.platform.tests;

import static junit.framework.Assert.assertTrue;

import android.platform.helpers.HelperAccessor;
import android.platform.helpers.IAutoAppInfoSettingsHelper;
import android.platform.helpers.IAutoAppInfoSettingsHelper.State;
import android.platform.helpers.IAutoSettingHelper;
import android.platform.helpers.SettingsConstants;

import androidx.test.runner.AndroidJUnit4;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;

@RunWith(AndroidJUnit4.class)
public class AppInfoSettingTest {
    private HelperAccessor<IAutoAppInfoSettingsHelper> mAppInfoSettingsHelper;
    private HelperAccessor<IAutoSettingHelper> mSettingHelper;

    private static final String CONTACTS_APP = "Contacts";
    private static final String PHONE_PERMISSION = "Phone";
    private static final String CONTACT_PACKAGE = "com.android.contacts";

    public AppInfoSettingTest() throws Exception {
        mAppInfoSettingsHelper = new HelperAccessor<>(IAutoAppInfoSettingsHelper.class);
        mSettingHelper = new HelperAccessor<>(IAutoSettingHelper.class);
    }
    @Before
    public void openAppInfoFacet() {
        mSettingHelper.get().openSetting(SettingsConstants.APPS_SETTINGS);
        assertTrue(
                "Apps setting did not open.",
                mSettingHelper.get().checkMenuExists("Reset app grid to A-Z order"));
        mAppInfoSettingsHelper.get().showAllApps();
    }

    @After
    public void goBackToSettingsScreen() {
        mSettingHelper.get().exit();
    }

    @Test
    public void testDisableEnableApplication() {
        mAppInfoSettingsHelper.get().selectApp(CONTACTS_APP);
        mAppInfoSettingsHelper.get().enableDisableApplication(State.DISABLE);
        assertTrue(
                "Application is not disabled",
                mAppInfoSettingsHelper.get().isApplicationDisabled(CONTACT_PACKAGE));
        mAppInfoSettingsHelper.get().enableDisableApplication(State.ENABLE);
        assertTrue(
                "Application is not enabled",
                !mAppInfoSettingsHelper.get().isApplicationDisabled(CONTACT_PACKAGE));
    }

    @Test
    public void testApplicationPermissions() {
        mAppInfoSettingsHelper.get().selectApp(CONTACTS_APP);
        mAppInfoSettingsHelper.get().setAppPermission(PHONE_PERMISSION, State.ENABLE);
        assertTrue(
                "Permission is disabled",
                mAppInfoSettingsHelper.get().getCurrentPermissions().contains(PHONE_PERMISSION));
        mAppInfoSettingsHelper.get().setAppPermission(PHONE_PERMISSION, State.DISABLE);
        assertTrue(
                "Permission is not disabled",
                !mAppInfoSettingsHelper.get().getCurrentPermissions().contains(PHONE_PERMISSION));
    }

    @Test
    public void testAllowedAppNumber() {

        // Navigate to the app permission manager.
        mSettingHelper.get().openSetting(SettingsConstants.APPS_SETTINGS);
        mAppInfoSettingsHelper.get().openPermissionManager();

        // Get one specific Permission UI element (that we have not looked at before).
        // Check whether its displayed allowed apps matches its (internal) listed apps.
        List<Integer> results =
                mAppInfoSettingsHelper.get().validateAppsPermissionManager(CONTACTS_APP);
        int summaryAllowed = results.get(0);
        int summaryTotal = results.get(1);
        int listedAllowed = results.get(2);
        int listedTotal = results.get(3);

        assertTrue(
                String.format(
                        "Number of listed apps allowed does not match display."
                                + "\nSummary Value: %d \tListed: %d \t"
                                + results.toString(),
                        summaryAllowed,
                        listedAllowed),
                summaryAllowed == listedAllowed);

        assertTrue(
                String.format(
                        "Number of listed apps not allowed does not match display."
                                + "\nSummary Value: %d \tListed: %d",
                        summaryTotal, listedTotal),
                summaryTotal == listedTotal);
    }
}
