package com.android.onboarding.tasks

import android.os.Parcel
import android.os.Parcelable

/**
 * Represents a token to an onboarding task executor along with its associated contract.
 *
 * This class is used to uniquely identify and reference an onboarding task executor and its
 * contract.
 */
data class OnboardingTaskToken(val taskClass: String, val taskContractClass: String) : Parcelable {
  constructor(
    parcel: Parcel
  ) : this(
    taskClass = parcel.readString() ?: error("taskClass is missing in the parcel"),
    taskContractClass = parcel.readString() ?: error("taskContractClass is missing in the parcel"),
  )

  override fun writeToParcel(parcel: Parcel, flags: Int) {
    parcel.writeString(taskClass)
    parcel.writeString(taskContractClass)
  }

  override fun describeContents(): Int = 0

  companion object CREATOR : Parcelable.Creator<OnboardingTaskToken> {
    override fun createFromParcel(parcel: Parcel): OnboardingTaskToken {
      return OnboardingTaskToken(parcel)
    }

    override fun newArray(size: Int): Array<OnboardingTaskToken?> {
      return arrayOfNulls(size)
    }
  }
}
