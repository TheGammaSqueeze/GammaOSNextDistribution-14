package com.android.onboarding.nodes.decoder

import com.android.onboarding.nodes.OnboardingGraphLog

/** Decode base64 encoded onboarding event proto to a string. */
class Base64ToProtoString(val str: String) {

  fun run() {
    println(OnboardingGraphLog.OnboardingEvent.deserialize(str))
  }

  companion object {
    @JvmStatic
    fun main(args: Array<String>) {
      if (args.isEmpty()) return
      Base64ToProtoString(args[0]).run()
    }
  }
}
