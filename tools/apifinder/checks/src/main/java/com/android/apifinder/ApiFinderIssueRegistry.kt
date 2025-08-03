package com.google.android.apifinder

import com.android.tools.lint.client.api.IssueRegistry
import com.android.tools.lint.detector.api.CURRENT_API
import com.google.auto.service.AutoService

@AutoService(IssueRegistry::class)
@Suppress("UnstableApiUsage")
class ApiFinderIssueRegistry : IssueRegistry() {
    override val api: Int
        get() = CURRENT_API
    override val issues = listOf(ApiFinderDetector.ISSUE)
}
