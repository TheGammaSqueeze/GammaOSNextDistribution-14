/*
 * Copyright (C) 2024 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package android.tools.flicker.junit

import android.os.Bundle
import android.platform.test.util.TestFilter
import android.tools.AndroidLogger
import android.tools.CrossPlatform
import android.tools.Scenario
import android.tools.TimestampFactory
import android.tools.traces.formatRealTimestamp
import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import java.util.Collections
import java.util.concurrent.locks.Lock
import java.util.concurrent.locks.ReentrantLock
import org.junit.FixMethodOrder
import org.junit.Ignore
import org.junit.internal.AssumptionViolatedException
import org.junit.internal.runners.model.EachTestNotifier
import org.junit.internal.runners.model.ReflectiveCallable
import org.junit.internal.runners.statements.Fail
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runner.manipulation.Filter
import org.junit.runner.manipulation.InvalidOrderingException
import org.junit.runner.manipulation.NoTestsRemainException
import org.junit.runner.manipulation.Orderable
import org.junit.runner.manipulation.Orderer
import org.junit.runner.manipulation.Sorter
import org.junit.runner.notification.RunNotifier
import org.junit.runner.notification.StoppedByUserException
import org.junit.runners.BlockJUnit4ClassRunner
import org.junit.runners.model.FrameworkMethod
import org.junit.runners.model.InvalidTestClassError
import org.junit.runners.model.RunnerScheduler
import org.junit.runners.model.Statement

class FlickerServiceJUnit4ClassRunner
@JvmOverloads
constructor(
    testClass: Class<*>?,
    paramString: String? = null,
    private val arguments: Bundle = InstrumentationRegistry.getArguments()
) : BlockJUnit4ClassRunner(testClass), IFlickerJUnitDecorator {

    private val onlyBlocking: Boolean
        get() = arguments.getString(Scenario.FAAS_BLOCKING)?.toBoolean() ?: true

    private val flickerDecorator: FlickerServiceDecorator =
        FlickerServiceDecorator(
            this.testClass,
            paramString = paramString,
            onlyBlocking,
            inner = this
        )

    private var initialized: Boolean? = null

    init {
        CrossPlatform.setLogger(AndroidLogger())
            .setTimestampFactory(TimestampFactory { formatRealTimestamp(it) })

        val errors = mutableListOf<Throwable>()
        flickerDecorator.doValidateInstanceMethods().let { errors.addAll(it) }
        flickerDecorator.doValidateConstructor().let { errors.addAll(it) }

        if (errors.isNotEmpty()) {
            throw InvalidTestClassError(testClass, errors)
        }

        initialized = true
    }

    override fun run(notifier: RunNotifier) {
        val testNotifier = EachTestNotifier(notifier, description)
        testNotifier.fireTestSuiteStarted()
        try {
            val statement = childrenInvoker(notifier)
            statement.evaluate()
        } catch (e: AssumptionViolatedException) {
            testNotifier.addFailedAssumption(e)
        } catch (e: StoppedByUserException) {
            throw e
        } catch (e: Throwable) {
            testNotifier.addFailure(e)
        } finally {
            testNotifier.fireTestSuiteFinished()
        }
    }

    /**
     * Implementation of Filterable and Sortable Based on JUnit's ParentRunner implementation but
     * with a minor modification to ensure injected FaaS tests are not filtered out.
     */
    @Throws(NoTestsRemainException::class)
    override fun filter(filter: Filter) {
        childrenLock.lock()
        try {
            val children: MutableList<FrameworkMethod> = getFilteredChildren().toMutableList()
            val iter: MutableIterator<FrameworkMethod> = children.iterator()
            while (iter.hasNext()) {
                val each: FrameworkMethod = iter.next()
                if (isInjectedFaasTest(each)) {
                    // Don't filter out injected FaaS tests
                    continue
                }
                if (shouldRun(filter, each)) {
                    try {
                        filter.apply(each)
                    } catch (e: NoTestsRemainException) {
                        iter.remove()
                    }
                } else {
                    iter.remove()
                }
            }
            filteredChildren = Collections.unmodifiableList(children)
            if (filteredChildren!!.isEmpty()) {
                throw NoTestsRemainException()
            }
        } finally {
            childrenLock.unlock()
        }
    }

    private fun isInjectedFaasTest(method: FrameworkMethod): Boolean {
        return method is FlickerServiceCachedTestCase
    }

    override fun isIgnored(child: FrameworkMethod): Boolean {
        return child.getAnnotation(Ignore::class.java) != null
    }

    /**
     * Returns the methods that run tests. Is ran after validateInstanceMethods, so
     * flickerBuilderProviderMethod should be set.
     */
    public override fun computeTestMethods(): List<FrameworkMethod> {
        val result = mutableListOf<FrameworkMethod>()
        if (initialized != null) {
            val testInstance = createTest()
            result.addAll(flickerDecorator.getTestMethods(testInstance))
        } else {
            result.addAll(getTestMethods({} /* placeholder param */))
        }
        return result
    }

    override fun describeChild(method: FrameworkMethod): Description {
        return flickerDecorator.getChildDescription(method)
    }

    /** {@inheritDoc} */
    override fun getChildren(): MutableList<FrameworkMethod> {
        val validChildren =
            super.getChildren().filter {
                val childDescription = describeChild(it)
                TestFilter.isFilteredOrUnspecified(arguments, childDescription)
            }
        return validChildren.toMutableList()
    }

    override fun methodInvoker(method: FrameworkMethod, test: Any): Statement {
        return flickerDecorator.getMethodInvoker(method, test)
    }

    /** IFlickerJunitDecorator implementation */
    override fun getTestMethods(test: Any): List<FrameworkMethod> = super.computeTestMethods()

    override fun getChildDescription(method: FrameworkMethod): Description {
        return super.describeChild(method)
    }

    override fun doValidateInstanceMethods(): List<Throwable> {
        val errors = mutableListOf<Throwable>()
        super.validateInstanceMethods(errors)
        return errors
    }

    override fun doValidateConstructor(): List<Throwable> {
        val result = mutableListOf<Throwable>()
        super.validateConstructor(result)
        return result
    }

    override fun getMethodInvoker(method: FrameworkMethod, test: Any): Statement {
        return super.methodInvoker(method, test)
    }

    override fun shouldRunBeforeOn(method: FrameworkMethod): Boolean = true

    override fun shouldRunAfterOn(method: FrameworkMethod): Boolean = true

    /**
     * ********************************************************************************************
     * START of code copied from ParentRunner to have local access to filteredChildren to ensure
     * FaaS injected tests are not filtered out.
     */

    // Guarded by childrenLock
    @Volatile private var filteredChildren: List<FrameworkMethod>? = null
    private val childrenLock: Lock = ReentrantLock()

    @Volatile
    private var scheduler: RunnerScheduler =
        object : RunnerScheduler {
            override fun schedule(childStatement: Runnable) {
                childStatement.run()
            }

            override fun finished() {
                // do nothing
            }
        }

    /**
     * Sets a scheduler that determines the order and parallelization of children. Highly
     * experimental feature that may change.
     */
    override fun setScheduler(scheduler: RunnerScheduler) {
        this.scheduler = scheduler
    }

    private fun shouldRun(filter: Filter, each: FrameworkMethod): Boolean {
        return filter.shouldRun(describeChild(each))
    }

    override fun sort(sorter: Sorter) {
        if (shouldNotReorder()) {
            return
        }
        childrenLock.lock()
        filteredChildren =
            try {
                for (each in getFilteredChildren()) {
                    sorter.apply(each)
                }
                val sortedChildren: List<FrameworkMethod> =
                    ArrayList<FrameworkMethod>(getFilteredChildren())
                Collections.sort(sortedChildren, comparator(sorter))
                Collections.unmodifiableList(sortedChildren)
            } finally {
                childrenLock.unlock()
            }
    }

    /**
     * Implementation of [Orderable.order].
     *
     * @since 4.13
     */
    @Throws(InvalidOrderingException::class)
    override fun order(orderer: Orderer) {
        if (shouldNotReorder()) {
            return
        }
        childrenLock.lock()
        try {
            var children: List<FrameworkMethod> = getFilteredChildren()
            // In theory, we could have duplicate Descriptions. De-dup them before ordering,
            // and add them back at the end.
            val childMap: MutableMap<Description, MutableList<FrameworkMethod>> =
                LinkedHashMap(children.size)
            for (child in children) {
                val description = describeChild(child)
                var childrenWithDescription: MutableList<FrameworkMethod>? = childMap[description]
                if (childrenWithDescription == null) {
                    childrenWithDescription = ArrayList<FrameworkMethod>(1)
                    childMap[description] = childrenWithDescription
                }
                childrenWithDescription.add(child)
                orderer.apply(child)
            }
            val inOrder = orderer.order(childMap.keys)
            children = ArrayList<FrameworkMethod>(children.size)
            for (description in inOrder) {
                children.addAll(childMap[description]!!)
            }
            filteredChildren = Collections.unmodifiableList(children)
        } finally {
            childrenLock.unlock()
        }
    }

    private fun shouldNotReorder(): Boolean {
        // If the test specifies a specific order, do not reorder.
        return description.getAnnotation(FixMethodOrder::class.java) != null
    }

    private fun getFilteredChildren(): List<FrameworkMethod> {
        childrenLock.lock()
        val filteredChildren =
            try {
                if (filteredChildren != null) {
                    filteredChildren!!
                } else {
                    Collections.unmodifiableList(ArrayList<FrameworkMethod>(children))
                }
            } finally {
                childrenLock.unlock()
            }
        return filteredChildren
    }

    override fun getDescription(): Description {
        val clazz = testClass.javaClass
        // if subclass overrides `getName()` then we should use it
        // to maintain backwards compatibility with JUnit 4.12
        val description: Description =
            if (clazz == null || clazz.name != name) {
                Description.createSuiteDescription(name, *runnerAnnotations)
            } else {
                Description.createSuiteDescription(clazz, *runnerAnnotations)
            }
        for (child in getFilteredChildren()) {
            description.addChild(describeChild(child))
        }
        return description
    }

    /**
     * Returns a [Statement]: Call [.runChild] on each object returned by [.getChildren] (subject to
     * any imposed filter and sort)
     */
    override fun childrenInvoker(notifier: RunNotifier): Statement {
        return object : Statement() {
            override fun evaluate() {
                Log.d("PAB", "RUNNING MY CHILD INVOKER")
                runChildren(notifier)
            }
        }
    }

    private fun runChildren(notifier: RunNotifier) {
        val currentScheduler = scheduler
        try {
            for (each in getFilteredChildren()) {
                currentScheduler.schedule { this.runChild(each, notifier) }
            }
        } finally {
            currentScheduler.finished()
        }
    }

    //
    // Implementation of ParentRunner
    //
    override fun runChild(method: FrameworkMethod, notifier: RunNotifier) {
        val description = describeChild(method)
        if (isIgnored(method)) {
            notifier.fireTestIgnored(description)
        } else {
            val statement: Statement =
                object : Statement() {
                    @Throws(Throwable::class)
                    override fun evaluate() {
                        methodBlock(method).evaluate()
                    }
                }
            runLeaf(statement, description, notifier)
        }
    }

    override fun methodBlock(method: FrameworkMethod?): Statement {
        val test: Any =
            try {
                object : ReflectiveCallable() {
                        @Throws(Throwable::class)
                        override fun runReflectiveCall(): Any {
                            return createTest(method)
                        }
                    }
                    .run()
            } catch (e: Throwable) {
                return Fail(e)
            }
        var statement: Statement? = methodInvoker(method!!, test)
        statement = possiblyExpectingExceptions(method, test, statement)
        statement = withPotentialTimeout(method, test, statement)

        if (method.declaringClass != InjectedTestCase::class.java) {
            if (flickerDecorator.shouldRunBeforeOn(method)) {
                statement = withBefores(method, test, statement)
            }
            if (flickerDecorator.shouldRunAfterOn(method)) {
                statement = withAfters(method, test, statement)
            }
            statement = withRules(method, test, statement)
        }

        statement = withInterruptIsolation(statement)
        return statement
    }

    private fun comparator(sorter: Sorter): Comparator<in FrameworkMethod> {
        return Comparator { o1, o2 -> sorter.compare(describeChild(o1), describeChild(o2)) }
    }

    private fun withRules(method: FrameworkMethod, target: Any, statement: Statement): Statement? {
        val ruleContainer = RuleContainer()
        CURRENT_RULE_CONTAINER.set(ruleContainer)
        try {
            val testRules = getTestRules(target)
            for (each in rules(target)) {
                if (!(each is TestRule && testRules.contains(each))) {
                    ruleContainer.add(each)
                }
            }
            for (rule in testRules) {
                ruleContainer.add(rule)
            }
        } finally {
            CURRENT_RULE_CONTAINER.remove()
        }
        return ruleContainer.apply(method, describeChild(method), target, statement)
    }

    companion object {
        private val CURRENT_RULE_CONTAINER = ThreadLocal<RuleContainer>()
    }

    /**
     * END of code copied from ParentRunner to have local access to filteredChildren to ensure FaaS
     * injected tests are not filtered out.
     */
}
