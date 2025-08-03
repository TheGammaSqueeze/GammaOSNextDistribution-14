package com.android.testutils

import android.os.SystemClock
import java.util.concurrent.CyclicBarrier
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertNull
import kotlin.test.assertTrue

// The table contains pairs associating a regexp with the code to run. The statement is matched
// against each matcher in sequence and when a match is found the associated code is run, passing
// it the TrackRecord under test and the result of the regexp match.
typealias InterpretMatcher<T> = Pair<Regex, (ConcurrentInterpreter<T>, T, MatchResult) -> Any?>

// The default unit of time for interpreted tests
const val INTERPRET_TIME_UNIT = 60L // ms

/**
 * A small interpreter for testing parallel code.
 *
 * The interpreter will read a list of lines consisting of "|"-separated statements, e.g. :
 *   sleep 2 ; unblock thread2 | wait thread2 time 2..5
 *   sendMessage "x"           | obtainMessage = "x" time 0..1
 *
 * Each column runs in a different concurrent thread and all threads wait for each other in
 * between lines. Each statement is split on ";" then matched with regular expressions in the
 * instructionTable constant, which contains the code associated with each statement. The
 * interpreter supports an object being passed to the interpretTestSpec() method to be passed
 * in each lambda (think about the object under test), and an optional transform function to be
 * executed on the object at the start of every thread.
 *
 * The time unit is defined in milliseconds by the interpretTimeUnit member, which has a default
 * value but can be passed to the constructor. Whitespace is ignored.
 *
 * The interpretation table has to be passed as an argument. It's a table associating a regexp
 * with the code that should execute, as a function taking three arguments : the interpreter,
 * the regexp match, and the object. See the individual tests for the DSL of that test.
 * Implementors for new interpreting languages are encouraged to look at the defaultInterpretTable
 * constant below for an example of how to write an interpreting table.
 * Some expressions already exist by default and can be used by all interpreters. Refer to
 * getDefaultInstructions() below for a list and documentation.
 */
open class ConcurrentInterpreter<T>(localInterpretTable: List<InterpretMatcher<T>>) {
    private val interpretTable: List<InterpretMatcher<T>> =
            localInterpretTable + getDefaultInstructions()
    // The last time the thread became blocked, with base System.currentTimeMillis(). This should
    // be set immediately before any time the thread gets blocked.
    internal val lastBlockedTime = ThreadLocal<Long>()

    // Split the line into multiple statements separated by ";" and execute them. Return whatever
    // the last statement returned.
    fun interpretMultiple(instr: String, r: T): Any? {
        return instr.split(";").map { interpret(it.trim(), r) }.last()
    }

    // Match the statement to a regex and interpret it.
    fun interpret(instr: String, r: T): Any? {
        val (matcher, code) =
                interpretTable.find { instr matches it.first } ?: throw SyntaxException(instr)
        val match = matcher.matchEntire(instr) ?: throw SyntaxException(instr)
        return code(this, r, match)
    }

    /**
     * Spins as many threads as needed by the test spec and interpret each program concurrently.
     *
     * All threads wait on a CyclicBarrier after each line.
     * |lineShift| says how many lines after the call the spec starts. This is used for error
     * reporting. Unfortunately AFAICT there is no way to get the line of an argument rather
     * than the line at which the expression starts.
     *
     * This method is mostly meant for implementations that extend the ConcurrentInterpreter
     * class to add their own directives and instructions. These may need to operate on some
     * data, which can be passed in |initial|. For example, an interpreter specialized in callbacks
     * may want to pass the callback there. In some cases, it's necessary that each thread
     * performs a transformation *after* it starts on that value before starting ; in this case,
     * the transformation can be passed to |threadTransform|. The default is to return |initial| as
     * is. Look at some existing child classes of this interpreter for some examples of how this
     * can be used.
     *
     * @param spec The test spec, as a string of lines separated by pipes.
     * @param initial An initial value passed to all threads.
     * @param lineShift How many lines after the call the spec starts, for error reporting.
     * @param threadTransform an optional transformation that each thread will apply to |initial|
     */
    fun interpretTestSpec(
        spec: String,
        initial: T,
        lineShift: Int = 0,
        threadTransform: (T) -> T = { it }
    ) {
        // For nice stack traces
        val callSite = getCallingMethod()
        val lines = spec.trim().trim('\n').split("\n").map { it.split("|") }
        // |lines| contains arrays of strings that make up the statements of a thread : in other
        // words, it's an array that contains a list of statements for each column in the spec.
        // E.g. if the string is """
        //   a | b | c
        //   d | e | f
        // """, then lines is [ [ "a", "b", "c" ], [ "d", "e", "f" ] ].
        val threadCount = lines[0].size
        assertTrue(lines.all { it.size == threadCount })
        val threadInstructions = (0 until threadCount).map { i -> lines.map { it[i].trim() } }
        // |threadInstructions| is a list where each element is the list of instructions for the
        // thread at the index. In other words, it's just |lines| transposed. In the example
        // above, it would be [ [ "a", "d" ], [ "b", "e" ], [ "c", "f" ] ]
        // mapIndexed below will pass in |instructions| the list of instructions for this thread.
        val barrier = CyclicBarrier(threadCount)
        var crash: InterpretException? = null
        threadInstructions.mapIndexed { threadIndex, instructions ->
            Thread {
                val threadLocal = threadTransform(initial)
                lastBlockedTime.set(System.currentTimeMillis())
                barrier.await()
                var lineNum = 0
                instructions.forEach {
                    if (null != crash) return@Thread
                    lineNum += 1
                    try {
                        interpretMultiple(it, threadLocal)
                    } catch (e: Throwable) {
                        // If fail() or some exception was called, the thread will come here ; if
                        // the exception isn't caught the process will crash, which is not nice for
                        // testing. Instead, catch the exception, cancel other threads, and report
                        // nicely. Catch throwable because fail() is AssertionError, which inherits
                        // from Error.
                        crash = InterpretException(threadIndex, it,
                                callSite.lineNumber + lineNum + lineShift,
                                callSite.className, callSite.methodName, callSite.fileName, e)
                    }
                    lastBlockedTime.set(System.currentTimeMillis())
                    barrier.await()
                }
            }.also { it.start() }
        }.forEach { it.join() }
        // If the test failed, crash with line number
        crash?.let { throw it }
    }

    // Helper to get the stack trace for a calling method
    private fun getCallingStackTrace(): Array<StackTraceElement> {
        try {
            throw RuntimeException()
        } catch (e: RuntimeException) {
            return e.stackTrace
        }
    }

    // Find the calling method. This is the first method in the stack trace that is annotated
    // with @Test.
    fun getCallingMethod(): StackTraceElement {
        val stackTrace = getCallingStackTrace()
        return stackTrace.find { element ->
            val clazz = Class.forName(element.className)
            // Because the stack trace doesn't list the formal arguments, find all methods with
            // this name and return this name if any of them is annotated with @Test.
            clazz.declaredMethods
                    .filter { method -> method.name == element.methodName }
                    .any { method -> method.getAnnotation(org.junit.Test::class.java) != null }
        } ?: stackTrace[3]
        // If no method is annotated return the 4th one, because that's what it usually is :
        // 0 is getCallingStackTrace, 1 is this method, 2 is ConcurrentInterpreter#interpretTestSpec
    }
}

/**
 * Default instructions available to all interpreters.
 * sleep(x) : sleeps for x time units and returns Unit ; sleep alone means sleep(1)
 * EXPR = VALUE : asserts that EXPR equals VALUE. EXPR is interpreted. VALUE can either be the
 *   string "null" or an int. Returns Unit.
 * EXPR time x..y : measures the time taken by EXPR and asserts it took at least x and at most
 *   y time units.
 * EXPR // any text : comments are ignored.
 * EXPR fails : checks that EXPR throws some exception.
 */
private fun <T> getDefaultInstructions() = listOf<InterpretMatcher<T>>(
    // Interpret an empty line as doing nothing.
    Regex("") to { _, _, _ -> null },
    // Ignore comments.
    Regex("(.*)//.*") to { i, t, r -> i.interpret(r.strArg(1), t) },
    // Interpret "XXX time x..y" : run XXX and check it took at least x and not more than y
    Regex("""(.*)\s*time\s*(\d+)\.\.(\d+)""") to { i, t, r ->
        val lateStart = System.currentTimeMillis()
        i.interpret(r.strArg(1), t)
        val end = System.currentTimeMillis()
        // There is uncertainty in measuring time.
        // It takes some (small) time for the thread to even measure the time at which it
        // starts interpreting the instruction. It is therefore possible that thread A sleeps for
        // n milliseconds, and B expects to have waited for at least n milliseconds, but because
        // B started measuring after 1ms or so, B thinks it didn't wait long enough.
        // To avoid this, when the `time` instruction tests the instruction took at least X and
        // at most Y, it tests X against a time measured since *before* the thread blocked but
        // Y against a time measured as late as possible. This ensures that the timer is
        // sufficiently lenient in both directions that there are no flaky measures.
        val minTime = end - lateStart
        val maxTime = end - i.lastBlockedTime.get()!!

        assertTrue(maxTime >= r.timeArg(2),
                "Should have taken at least ${r.timeArg(2)} but took less than $maxTime")
        assertTrue(minTime <= r.timeArg(3),
                "Should have taken at most ${r.timeArg(3)} but took more than $minTime")
    },
    // Interpret "XXX = YYY" : run XXX and assert its return value is equal to YYY. "null" supported
    Regex("""(.*)\s*=\s*(null|\d+)""") to { i, t, r ->
        i.interpret(r.strArg(1), t).also {
            if ("null" == r.strArg(2)) assertNull(it) else assertEquals(r.intArg(2), it)
        }
    },
    // Interpret sleep. Optional argument for the count, in INTERPRET_TIME_UNIT units.
    Regex("""sleep(\((\d+)\))?""") to { i, t, r ->
        SystemClock.sleep(if (r.strArg(2).isEmpty()) INTERPRET_TIME_UNIT else r.timeArg(2))
    },
    Regex("""(.*)\s*fails""") to { i, t, r ->
        assertFails { i.interpret(r.strArg(1), t) }
    }
)

class SyntaxException(msg: String, cause: Throwable? = null) : RuntimeException(msg, cause)
class InterpretException(
    threadIndex: Int,
    instr: String,
    lineNum: Int,
    className: String,
    methodName: String,
    fileName: String,
    cause: Throwable
) : RuntimeException("Failure: $instr", cause) {
    init {
        stackTrace = arrayOf(StackTraceElement(
                className,
                "$methodName:thread$threadIndex",
                fileName,
                lineNum)) + super.getStackTrace()
    }
}

// Some small helpers to avoid to say the large ".groupValues[index].trim()" every time
fun MatchResult.strArg(index: Int) = this.groupValues[index].trim()
fun MatchResult.intArg(index: Int) = strArg(index).toInt()
fun MatchResult.timeArg(index: Int) = INTERPRET_TIME_UNIT * intArg(index)
