package com.android.onboarding.common.annotations

/**
 * Annotation used for storing metadata about Onboarding components.
 *
 * @param name The human readable component name in English
 * @param packageName The Android package name of the component
 * @param subName If packageName does not uniquely identify this component, this is an additional
 *   short string to differentiate from others in the same package
 * @param blueprint Optional full path to a blueprint file representing this component
 * @param rapid Optional information about Rapid build for the component
 * @param updates The minimum Android SDK version which still updates to new builds of this
 *   component. The default is that no updates occur during setup.
 * @param triage Optional overrides to triage rules for bugs relating to this component. This will
 *   take precedence over definitions from blueprint and elsewhere.
 */
@Retention(AnnotationRetention.SOURCE)
annotation class OnboardingComponent(
  val name: String,
  val packageName: String,
  val subName: String = "",
  val rapid: RapidBuild = RapidBuild("NONE", "NONE"),
  val blueprint: String = "",
  val updates: Updates = Updates(-1), // Not updated during Onboarding
  val triage: Triage = Triage(),
)

/** Annotation used for storing metadata about Rapid builds. */
@Retention(AnnotationRetention.SOURCE)
annotation class RapidBuild(val projectName: String, val productionEnvironment: String)

/** Annotation used for storing metadata about component update strategies. */
@Retention(AnnotationRetention.SOURCE) annotation class Updates(val minSdkVersion: Int)

/** Annotation used for storing metadata about triage rules. */
@Retention(AnnotationRetention.SOURCE)
annotation class Triage(
  val buganizerComponentId: Long = -1,
  val assigneeEmailAddress: String = "",
  val hotlistIds: Array<String> = [],
)
