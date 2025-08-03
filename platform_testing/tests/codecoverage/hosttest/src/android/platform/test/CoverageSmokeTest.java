package android.platform.test.coverage;

import static com.google.common.base.Verify.verifyNotNull;
import static com.google.common.truth.Truth.assertThat;

import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.metrics.proto.MetricMeasurement.Metric;
import com.android.tradefed.result.TestRunResult;
import com.android.tradefed.targetprep.TargetSetupError;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;
import com.android.tradefed.testtype.junit4.BaseHostJUnit4Test;
import com.android.tradefed.testtype.junit4.DeviceTestRunOptions;
import com.android.tradefed.util.AdbRootElevator;
import com.android.tradefed.util.FileUtil;
import com.google.common.collect.ImmutableMap;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;

import org.jacoco.core.analysis.Analyzer;
import org.jacoco.core.analysis.CoverageBuilder;
import org.jacoco.core.analysis.IBundleCoverage;
import org.jacoco.core.tools.ExecFileLoader;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Runs an instrumentation test and verifies the coverage report. */
@RunWith(DeviceJUnit4ClassRunner.class)
public final class CoverageSmokeTest extends BaseHostJUnit4Test {

    private static final String COVERAGE_MEASUREMENT_KEY = "coverageFilePath";
    private static final String INNER_JAR_PATH =
            "out/target/common/obj/APPS/CoverageInstrumentationSampleTest_intermediates/jacoco-report-classes.jar";

    @Before
    public void runCoverageDeviceTests() throws DeviceNotAvailableException, TargetSetupError {
        installPackage("CoverageInstrumentationSampleTest.apk");
        DeviceTestRunOptions options =
                new DeviceTestRunOptions("android.platform.test.coverage")
                        .setTestClassName(
                                "android.platform.test.coverage.CoverageInstrumentationTest")
                        .setTestMethodName("testCoveredMethod")
                        .setDisableHiddenApiCheck(true)
                        .addInstrumentationArg("coverage", "true");
        runDeviceTests(options);
    }

    @Test
    public void instrumentationTest_generatesJavaCoverage()
            throws DeviceNotAvailableException, IOException {
        TestRunResult testRunResult = getLastDeviceRunResults();
        Metric devicePathMetric = testRunResult.getRunProtoMetrics().get(COVERAGE_MEASUREMENT_KEY);
        String testCoveragePath = devicePathMetric.getMeasurements().getSingleString();
        ExecFileLoader execFileLoader = new ExecFileLoader();
        File coverageFile = null;
        try (AdbRootElevator adbRoot = new AdbRootElevator(getDevice())) {
            coverageFile = getDevice().pullFile(testCoveragePath);
            execFileLoader.load(coverageFile);
        } catch (DeviceNotAvailableException | IOException e) {
            throw new RuntimeException(e);
        } finally {
            FileUtil.deleteFile(coverageFile);
        }
        CoverageBuilder builder = new CoverageBuilder();
        Analyzer analyzer = new Analyzer(execFileLoader.getExecutionDataStore(), builder);
        IBuildInfo buildInfo = getBuild();
        File jacocoAllClassesJar =
                verifyNotNull(
                        buildInfo.getFile("jacoco-report-classes-all.jar"),
                        "Could not get jacoco-report-classes-all.jar from the build.");
        if (jacocoAllClassesJar.isDirectory()) {
            // If we downloaded directly the subset of files, it will be a directory
            File jacocoReport = FileUtil.findFile(jacocoAllClassesJar, "jacoco-report-classes.jar");
            verifyNotNull(
                    jacocoReport,
                    "jacoco-report-classes.jar missing from the directory downloaded.");
            try (InputStream in = new FileInputStream(jacocoReport)) {
                analyzer.analyzeAll(in, "jacoco-report-classes.jar");
            }
        } else {
            URI uri = URI.create("jar:file:" + jacocoAllClassesJar.getPath());
            try (FileSystem zip = FileSystems.newFileSystem(uri, ImmutableMap.of())) {
                try (InputStream in = Files.newInputStream(zip.getPath(INNER_JAR_PATH))) {
                    analyzer.analyzeAll(in, "jacoco-report-classes.jar");
                }
            }
        }
        IBundleCoverage coverage = builder.getBundle("JaCoCo Coverage Report");

        assertThat(coverage).isNotNull();
        assertThat(coverage.getPackages()).isNotEmpty();
    }
}
