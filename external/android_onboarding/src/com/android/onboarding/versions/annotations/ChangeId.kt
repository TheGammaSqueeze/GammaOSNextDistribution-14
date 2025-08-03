package com.android.onboarding.versions.annotations

/**
 * Marker for a change made to an Onboarding component.
 *
 * All changes in Onboarding must be marked by a [ChangeId] and fully flagged.
 *
 * The value should be the ID of a Buganizer issue related to the change.
 *
 * @param bugId The bug this change relates to. This must match the value of the field being
 *   annotated.
 * @param owner A username who is responsible for this change
 * @param breaking Reasons that this change will cause breakages in current callers. Empty if none.
 * @param changeRadius The radius of impact of this change. See [ChangeRadius] docs for details.
 * @param dependsOn Another Change ID that must be enabled for this change to work. [DEFAULT] for
 *   none.
 * @param expedited A reason that this change may skip the usual rollout process.
 *   [ExpeditedReason.NOT_EXPEDITED] if none.
 * @param available The date (in format YYYY-MM-DD) that this change became available for use by
 *   apps.
 * @param released The date (in format YYYY-MM-DD) that this change was released (meaning that it is
 *   present in all active versions of apps and no longer needs to be checked).
 */
@Target(AnnotationTarget.FIELD)
@Retention(AnnotationRetention.SOURCE)
annotation class ChangeId(
  val bugId: Long,
  val owner: String,
  val breaking: Array<BreakingReason>,
  val changeRadius: ChangeRadius,
  val dependsOn: Long = DEFAULT,
  val expedited: ExpeditedReason = ExpeditedReason.NOT_EXPEDITED,
  val available: String = NOT_AVAILABLE,
  val released: String = NOT_RELEASED,
) {
  companion object {
    private const val DEFAULT = -1L

    const val NOT_AVAILABLE = "NOT_AVAILABLE"
    const val NOT_RELEASED = "NOT_RELEASED"

    /** Generate the code to represent this change ID in Kotlin. */
    fun generateChangeIdCode(changeId: ChangeId): String {
      return "ChangeId(bugId=${changeId.bugId}, owner=\"${changeId.owner}\", breaking=arrayOf(${changeId.breaking.joinToString(", ")}), changeRadius=${changeId.changeRadius}, dependsOn=${changeId.dependsOn}, expedited=${changeId.expedited}, available=\"${changeId.available}\", released=\"${changeId.released}\")"
    }
  }
}
