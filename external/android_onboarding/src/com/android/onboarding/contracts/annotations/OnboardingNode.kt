package com.android.onboarding.contracts.annotations

/** Container of metadata about a node definition in the onboarding graph */
@Retention(AnnotationRetention.RUNTIME)
annotation class OnboardingNode(
  /** Identifier of the component who owns the node. */
  val component: String,

  /**
   * Identifier of the node interface.
   *
   * Note that this must be at most 40 characters long, otherwise an exception will be thrown.
   */
  val name: String,

  /** The type of UI shown as part of this node. */
  val uiType: UiType,

  /** The type of specification given for this node. */
  val specificationType: SpecificationType = SpecificationType.LIGHT,
) {
  enum class UiType {
    /** This node shows no UI. */
    NONE,

    /** This node shows UI which does not fit any other type. */
    OTHER,

    /**
     * This node does not actually show UI but makes use of UI controls as a way of passing control.
     * This will be replaced by some other mechanism in future.
     */
    INVISIBLE,

    /**
     * This node shows a loading screen. The primary purpose is to control the screen while some
     * background work executes.
     */
    LOADING,

    /**
     * This node shows an education screen. The primary purpose is to educate the user about their
     * device, privacy, etc.
     */
    EDUCATION,

    /** This node's primary purpose is to input a decision or some data from the user. */
    INPUT,

    /**
     * This node's primary purpose is to host some other UI node. This node must not show UI itself
     * independently of another node.
     *
     * For example, an Activity which hosts fragments, where those fragments themselves are nodes,
     * could be marked as [HOST].
     */
    HOST
  }

  enum class SpecificationType {
    /**
     * This means that full Javadoc is provided, all arguments are fully defined, documented and
     * typed (no undefined Bundles or Intents), and return values are properly defined and typed.
     */
    V1,

    /**
     * No requirements.
     *
     * Most "Light" nodes will be lacking Javadoc, and using Intents and Bundles as arguments and
     * return values.
     */
    LIGHT
  }

  companion object {
    /** Returns the node [component] for given node's contract class. */
    fun extractComponentNameFromClass(nodeClass: Class<*>): String =
      nodeClass.getAnnotation(OnboardingNode::class.java)?.component
        ?: throw IllegalArgumentException("All nodes must be annotated @OnboardingNode")

    /** Returns the node [name] for given node's contract class. */
    fun extractNodeNameFromClass(nodeClass: Class<*>): String =
      nodeClass.getAnnotation(OnboardingNode::class.java)?.name?.also {
        require(it.length <= MAX_NODE_NAME_LENGTH) {
          "Node name length (${it.length}) exceeds maximum length of $MAX_NODE_NAME_LENGTH characters"
        }
      } ?: throw IllegalArgumentException("All nodes must be annotated @OnboardingNode")
  }
}

const val MAX_NODE_NAME_LENGTH: Int = 40
