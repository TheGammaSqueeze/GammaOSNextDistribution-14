package com.android.interactive.steps.enterprise.bugreport

import android.widget.Button
import android.widget.TextView
import com.android.bedstead.nene.TestApis
import com.android.interactive.Automation
import com.android.interactive.annotations.AutomationFor
import com.android.interactive.Nothing
import com.android.interactive.Nothing.NOTHING

import androidx.test.uiautomator.UiSelector

@AutomationFor("com.android.interactive.steps.enterprise.bugreport.BugReportNotificationDeclineToShareBugReportStep")
class BugReportNotificationDeclineToShareBugReportStepAutomation : Automation<Nothing> {
    override fun automate(): Nothing {
        TestApis.ui().device().openNotification()


        // Select notification
        TestApis.ui().device().findObject(
            UiSelector().packageName("com.android.systemui").textContains("Taking bug report")
                .resourceId("android:id/title").className(TextView::class.java)
        ).click()

        // Select share
        TestApis.ui().device().findObject(
            UiSelector().packageName("com.android.settings").textContains("DECLINE")
                .resourceId("android:id/button2").className(Button::class.java)
        ).click()

        return NOTHING
    }
}