package com.android.onboarding.versions

import com.android.onboarding.versions.annotations.ChangeId
import java.time.LocalDate

/**
 * A class representing a specific compliance date which can be compared to other compliance dates.
 *
 * <p>If not specified, or is earlier than {@link EARLIEST_COMPLIANCE_DATE}, this will default to
 * the {@link EARLIEST_COMPLIANCE_DATE}.
 */
@JvmInline
value class ComplianceDate(private val complianceDate: LocalDate) {
  constructor(
    version: String? = null
  ) : this(
    version?.let(LocalDate::parse)?.takeUnless { it.isBefore(EARLIEST_COMPLIANCE_DATE_LOCALDATE) }
      ?: EARLIEST_COMPLIANCE_DATE_LOCALDATE
  )

  /** True if the compliance date is at least {@code version} */
  fun isAtLeast(version: String): Boolean = isAtLeast(ComplianceDate(version))

  /** True if the compliance date is at least {@code version} */
  fun isAtLeast(version: ComplianceDate): Boolean = !version.complianceDate.isAfter(complianceDate)

  /** True if the package's version is at most {@code version} */
  fun isAtMost(version: String): Boolean = isAtMost(ComplianceDate(version))

  /** True if the package's version is at most {@code version} */
  fun isAtMost(version: ComplianceDate): Boolean = !complianceDate.isAfter(version.complianceDate)

  /** True if the package's version is the same as {@code version} */
  fun isEqualTo(version: String): Boolean = isEqualTo(ComplianceDate(version))

  /** True if the package's version is the same as {@code version} */
  fun isEqualTo(version: ComplianceDate): Boolean = version.complianceDate == complianceDate

  /** True if the package's version is less than {@code version} */
  fun isLessThan(version: String): Boolean = isLessThan(ComplianceDate(version))

  /** True if the package's version is less than {@code version} */
  fun isLessThan(version: ComplianceDate): Boolean = version.complianceDate.isAfter(complianceDate)

  /**
   * True if the [complianceDate] is greater than or equal to the available date of the [changeId].
   */
  fun isAvailable(changeId: ChangeId) =
    if (changeId.available == ChangeId.NOT_AVAILABLE) {
      false
    } else {
      isAtLeast(changeId.available)
    }

  companion object {
    /**
     * The default for any APK for whom no compliance date is specified.
     *
     * <p>This represents the oldest API version which must be supported by all onboarding
     * components. Any code dealing with versions older than this can be safely removed.
     */
    val EARLIEST_COMPLIANCE_DATE_STRING = "2023-07-05"

    /**
     * The default for any APK for whom no compliance date is specified.
     *
     * <p>This represents the oldest API version which must be supported by all onboarding
     * components. Any code dealing with versions older than this can be safely removed.
     */
    val EARLIEST_COMPLIANCE_DATE_LOCALDATE = LocalDate.parse(EARLIEST_COMPLIANCE_DATE_STRING)

    /**
     * The default for any APK for whom no compliance date is specified.
     *
     * <p>This represents the oldest API version which must be supported by all onboarding
     * components. Any code dealing with versions older than this can be safely removed.
     */
    val EARLIEST = ComplianceDate(EARLIEST_COMPLIANCE_DATE_LOCALDATE)
  }
}
