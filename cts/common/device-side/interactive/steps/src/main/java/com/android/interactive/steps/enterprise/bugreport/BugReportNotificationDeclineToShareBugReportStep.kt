package com.android.interactive.steps.enterprise.bugreport

import com.android.interactive.steps.ActAndConfirmStep

/** See Step Instruction.  */
class BugReportNotificationDeclineToShareBugReportStep : ActAndConfirmStep(
    "Find the notification with the title 'Taking Bug Report...' or 'Share bug report?' and tap on it. Select the option that " +
            "declines to share the bug report.")