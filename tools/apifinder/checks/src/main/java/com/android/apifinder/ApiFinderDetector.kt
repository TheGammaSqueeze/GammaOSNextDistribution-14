package com.google.android.apifinder

import com.android.tools.lint.client.api.UElementHandler
import com.android.tools.lint.detector.api.*
import com.intellij.lang.jvm.JvmModifier
import com.intellij.psi.*
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.USimpleNameReferenceExpression

@Suppress("UnstableApiUsage")
class ApiFinderDetector : Detector(), Detector.UastScanner {

    override fun getApplicableUastTypes(): List<Class<out UElement>>? =
      listOf(UCallExpression::class.java, USimpleNameReferenceExpression::class.java,
             UMethod::class.java)

    override fun createUastHandler(context: JavaContext): UElementHandler? =
      object : UElementHandler() {
          // Visits all methods that the module itself has defined in the source code.
          override fun visitMethod(node: UMethod) {
              val method = node.sourcePsi as? PsiMethod ?: return
              visitGenericMethod(method, node, isModuleMethod = true)
          }

          // Visits all method call expressions in the source code.
          override fun visitCallExpression(node: UCallExpression) {
              val method = node.resolve() ?: return
              visitGenericMethod(method, node)
          }

          // When Kotlin code refers to a Java `getFoo()` or `setFoo()` method with property syntax
          // * (`obj.foo`), "foo" is represented by a [USimpleNameReferenceExpression].
          // This ensures that we visit these method calls as well.
          override fun visitSimpleNameReferenceExpression(node: USimpleNameReferenceExpression) {
              val method = node.resolve() as? PsiMethod ?: return
              visitGenericMethod(method, node)
          }

          private fun visitGenericMethod(
              method: PsiMethod, node: UElement, isModuleMethod: Boolean = false
          ) {
              // Exclude non-public/protected calls.
              if (!method.hasModifier(JvmModifier.PUBLIC) && !method.hasModifier(JvmModifier.PROTECTED)) {
                  return
              }
              var containingClass = method.containingClass
              while (containingClass != null) {
                  if (!containingClass.hasModifier(JvmModifier.PUBLIC) && !containingClass.hasModifier(JvmModifier.PROTECTED)) {
                      return
                  }
                  containingClass = containingClass.containingClass
              }

              // Construct message as enclosingClassName.className.methodName(parameterNames).
              // e.g. <com.android.server.wifi>.<WifiNetworkFactory>.<hasConnectionRequests>()
              val className = method.containingClass!!.qualifiedName!!
              val methodName = if (method.isConstructor) {
                  val containingClasses = mutableListOf<PsiClass>()
                  containingClass = method.containingClass
                  while (containingClass != null) {
                      containingClasses += containingClass
                      containingClass = containingClass.containingClass
                  }
                  containingClasses.asReversed().joinToString(".") { it.name!! }
              } else {
                  method.name
              }
              val parameterNames = method.parameterList.parameters.map {
                  it.type.canonicalText.replace(Regex("<.*>"), "")
              }.joinToString(", ")

              val methodCall = "$className.$methodName($parameterNames)"
              if (isModuleMethod) {
                  val message = "ModuleMethod:" + methodCall
                  context.report(ISSUE, node, context.getLocation(node), message)
              } else {
                  val message = "Method:" + methodCall
                  context.report(ISSUE, context.getLocation(node), message)
              }
          }
      }

    companion object {
        @JvmField
        val ISSUE = Issue.create(
          id = "JavaKotlinApiUsedByModule",
          briefDescription = "API used by Android module",
          explanation = "Any public/protected items used by an Android module.",
          category = Category.TESTING,
          priority = 6,
          severity = Severity.INFORMATIONAL,
          implementation = Implementation(ApiFinderDetector::class.java, Scope.JAVA_FILE_SCOPE)
        )
    }
}

