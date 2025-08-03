# Writing an MPC Test

Using
[this CL](https://android-review.googlesource.com/c/platform/cts/+/2128521) as a
guide focusing on requirement
[8.2/H-1-1](https://source.android.com/docs/compatibility/13/android-13-cdd#2274_performance):

-   R: MUST ensure a sequential write performance of at least 100MB/s.
-   S and Tiramisu: MUST ensure a sequential write performance of at least
    125MB/s.

## Define Requirements

### Define Constants Under RequirementConstants

For our requirement,
[8.2/H-1-1](https://source.android.com/docs/compatibility/13/android-13-cdd#2274_performance),
we have one required measurement, so we will create one constant,
`FILESYSTEM_IO_RATE`, to track that. This constant eventually will make its way
to the internal cts_media_performance_class_test_metrics.proto, so the
string-value we choose for it needs to structured like a proto field name and
should include units at the end:

```
public static final String FILESYSTEM_IO_RATE = "filesystem_io_rate_mbps";
```

Additionally, we may need to create a new BiPredicate for our requirement. The
BiPredicate defines the operator to test measurements for our requirement with.
For our case, we want to test if an I/O rate is at or above a certain value, so
we will use a GTE operator. We will additionally be storing I/O rates as
doubles, leading us to define the BiPredicate DOUBLE_GTE:

```
public static final BiPredicate<Double, Double> DOUBLE_GTE = RequirementConstants.gte();
```

### Define Requirement Class Under PerformanceClassEvaluator

In PerformanceClassEvaluator.java, we will define a new requirement class. This
class should be defined as nested class under PerformanceClassEvaluator:

```
// used for requirements [8.2/H-1-1], [8.2/H-1-2], [8.2/H-1-3], [8.2/H-1-4]
public static class FileSystemRequirement extends Requirement {

  private static final String TAG = FileSystemRequirement.class.getSimpleName();

  ...

}
```

#### Define Constructor

The constructors for requirement classes are very standardized. They are always
private; they always take in two inputs: `String id, RequiredMeasurement<?> ...
reqs`; and they always contain one line: `super(id, reqs)`:

```
private FileSystemRequirement(String id, RequiredMeasurement<?> ... reqs) {
  super(id, reqs);
}
```

#### Define Set Measured Value Method(s)

Requirement classes need to define a method for each required measurement. These
methods always contain one line: a function call to Requirement’s
`setMeausredValue` method. For
[8.2/H-1-1](https://source.android.com/docs/compatibility/13/android-13-cdd#2274_performance),
we only have one required measurement so only need to make one method for it:

```
/**
 * Set the Filesystem I/O Rate in MB/s.
 */
public void setFilesystemIoRate(double filesystemIoRate) {
  this.setMeasuredValue(RequirementConstants.FILESYSTEM_IO_RATE, filesystemIoRate);
}
```

#### Define Create Method

The last thing we need to make for our requirement class is a create method.
This method defines each of the required measurements. Each
RequiredMeasurement.java is created through a builder and defining the
following:

*   The datatype of the measurement. This is during the call to the
    RequiredMeasurement’s builder function, ex: `<Double>builder()`
*   The ID for the measurement. This is the String constant we defined earlier.
*   The predicate for the measurement. This is the BiPredicate we defined
    earlier.
*   A required value for each achievable performance class. For example, using
    the GTE a BiPredicate:
    *   `addRequiredValue(Build.VERSION_CODES.R, 100.0)` says that if a
        requirement measurement is greater than or equal to to 100, the device
        makes performance class R
    *   `addRequiredValue(Build.VERSION_CODES.TIRAMISU, 125.0)` says that if a
        requirement measurement is greater than or equal to to 125, the device
        makes performance class Tiramisu

Note: if a device meets multiple performance classes for a requirement, the
system automatically chooses to record the higher classification

For requirement
[8.2/H-1-1](https://source.android.com/docs/compatibility/13/android-13-cdd#2274_performance)
we define the following create method:

```
/**
 * [8.2/H-1-1] MUST ensure a sequential write performance of
 * at least 100(R) / 125(S & T) MB/s.
 */
public static FileSystemRequirement createR8_2__H_1_1() {
  RequiredMeasurement<Double> filesystem_io_rate =
      RequiredMeasurement.<Double>builder()
      .setId(RequirementConstants.FILESYSTEM_IO_RATE)
      .setPredicate(RequirementConstants.DOUBLE_GTE)
      .addRequiredValue(Build.VERSION_CODES.R, 100.0)
      .addRequiredValue(Build.VERSION_CODES.TIRAMISU, 125.0)
      .build();

  return new FileSystemRequirement(RequirementConstants.R8_2__H_1_1,
      filesystem_io_rate);
}
```

Note: a requirement class can be and often is used for multiple requirements. If
so, a create method must be defined for each.

#### Define Add Method at the Bottom of PerformanceClassEvaluator

After all of that we just need to define an add method at the bottom of
PerformacneClassEvaluator for our requirement. All it does is call
PerformanceClassEvaluator’s `addRequirement` method using the create method we
defined earlier.

```
public FileSystemRequirement addR8_2__H_1_1() {
  return this.addRequirement(FileSystemRequirement.createR8_2__H_1_1());
}
```

## Update Test to Report Data Using PerformanceClassEvaluator

Now that we have a requirement defined we just need to update our test to use
PerformanceClassEvaluator.

First we need to add the following to our test class: @Rule public final
TestName mTestName = new TestName();

Next we will create the evaluator and add our newly defined requirement. This
can be done at any point during the test, but typically test writers choose to
do this at the end of the test:

```
PerformanceClassEvaluator pce = new PerformanceClassEvaluator(this.mTestName);
PerformanceClassEvaluator.FileSystemRequirement r8_2__H_1_1 = pce.addR8_2__H_1_1();
```

After the test, once our required measurement(s) have been calculated, we use
the set measurement method(s) we defined to report them:

```
r8_2__H_1_1.setFilesystemIoRate(stat.mAverage);
```

Finally, we just need to submit our results. The submit method should be called
only once at the very end of the test. If we are writing our test CTS, we should
use `submitAndCheck`; if we are writing our test under CTS-Verifier or ITS, we
should use `submitAndVerify`. Ex:

```
pce.submitAndCheck();
```

The test results are then processed and reported creating a file
cts_media_performance_class_test_cases.reportlog.json which will eventually have
its data upload and processed.
