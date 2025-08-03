package com.android.server.art;

import static com.android.server.art.model.ArtFlags.BatchDexoptPass;

import android.annotation.NonNull;
import android.app.job.JobParameters;
import android.os.Build;

import androidx.annotation.RequiresApi;

import com.android.server.art.model.ArtFlags;
import com.android.server.art.model.DexoptResult;

import dalvik.system.DexFile;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * This is an helper class to report the background DexOpt job metrics to StatsD.
 *
 * @hide
 */
@RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
public class BackgroundDexoptJobStatsReporter {
    public static void reportFailure() {
        // The fatal error can occur during any pass, but we attribute it to the main pass for
        // simplicity.
        ArtStatsLog.write(ArtStatsLog.BACKGROUND_DEXOPT_JOB_ENDED,
                ArtStatsLog.BACKGROUND_DEXOPT_JOB_ENDED__STATUS__STATUS_FATAL_ERROR,
                JobParameters.STOP_REASON_UNDEFINED, 0 /* durationMs */, 0 /* deprecated */,
                0 /* optimizedPackagesCount */, 0 /* packagesDependingOnBootClasspathCount */,
                0 /* totalPackagesCount */,
                ArtStatsLog.BACKGROUND_DEXOPT_JOB_ENDED__PASS__PASS_MAIN);
    }

    public static void reportSuccess(@NonNull BackgroundDexoptJob.CompletedResult completedResult,
            Optional<Integer> stopReason) {
        for (var entry : completedResult.dexoptResultByPass().entrySet()) {
            reportPass(entry.getKey(), entry.getValue(),
                    completedResult.durationMsByPass().getOrDefault(entry.getKey(), 0l),
                    stopReason);
        }
    }

    public static void reportPass(@BatchDexoptPass int pass, @NonNull DexoptResult dexoptResult,
            long durationMs, Optional<Integer> stopReason) {
        // The job contains multiple passes, so the stop reason may not be for the current pass. We
        // shouldn't report the stop reason if the current pass finished before the job was
        // cancelled.
        int reportedStopReason = dexoptResult.getFinalStatus() == DexoptResult.DEXOPT_CANCELLED
                ? stopReason.orElse(JobParameters.STOP_REASON_UNDEFINED)
                : JobParameters.STOP_REASON_UNDEFINED;

        List<DexoptResult.PackageDexoptResult> packageDexoptResults =
                getFilteredPackageResults(dexoptResult);

        ArtStatsLog.write(ArtStatsLog.BACKGROUND_DEXOPT_JOB_ENDED,
                getStatusForStats(dexoptResult, stopReason), reportedStopReason, durationMs,
                0 /* deprecated */, getDexoptedPackagesCount(packageDexoptResults),
                getPackagesDependingOnBootClasspathCount(packageDexoptResults),
                packageDexoptResults.size(), toStatsdPassEnum(pass));
    }

    @NonNull
    private static List<DexoptResult.PackageDexoptResult> getFilteredPackageResults(
            @NonNull DexoptResult dexoptResult) {
        return dexoptResult.getPackageDexoptResults()
                .stream()
                .filter(packageResult
                        -> packageResult.getDexContainerFileDexoptResults().stream().anyMatch(
                                fileResult
                                -> (fileResult.getExtendedStatusFlags()
                                           & DexoptResult.EXTENDED_SKIPPED_NO_DEX_CODE)
                                        == 0))
                .collect(Collectors.toList());
    }

    private static int getStatusForStats(
            @NonNull DexoptResult dexoptResult, Optional<Integer> stopReason) {
        if (dexoptResult.getFinalStatus() == DexoptResult.DEXOPT_CANCELLED) {
            if (stopReason.isPresent()) {
                return ArtStatsLog
                        .BACKGROUND_DEXOPT_JOB_ENDED__STATUS__STATUS_ABORT_BY_CANCELLATION;
            } else {
                return ArtStatsLog.BACKGROUND_DEXOPT_JOB_ENDED__STATUS__STATUS_ABORT_BY_API;
            }
        }

        boolean isSkippedDueToStorageLow =
                dexoptResult.getPackageDexoptResults()
                        .stream()
                        .flatMap(packageResult
                                -> packageResult.getDexContainerFileDexoptResults().stream())
                        .anyMatch(fileResult
                                -> (fileResult.getExtendedStatusFlags()
                                           & DexoptResult.EXTENDED_SKIPPED_STORAGE_LOW)
                                        != 0);
        if (isSkippedDueToStorageLow) {
            return ArtStatsLog.BACKGROUND_DEXOPT_JOB_ENDED__STATUS__STATUS_ABORT_NO_SPACE_LEFT;
        }

        return ArtStatsLog.BACKGROUND_DEXOPT_JOB_ENDED__STATUS__STATUS_JOB_FINISHED;
    }

    private static int getDexoptedPackagesCount(
            @NonNull List<DexoptResult.PackageDexoptResult> packageResults) {
        return (int) packageResults.stream()
                .filter(result -> result.getStatus() == DexoptResult.DEXOPT_PERFORMED)
                .count();
    }

    private static int getPackagesDependingOnBootClasspathCount(
            @NonNull List<DexoptResult.PackageDexoptResult> packageResults) {
        return (int) packageResults.stream()
                .map(DexoptResult.PackageDexoptResult::getDexContainerFileDexoptResults)
                .filter(BackgroundDexoptJobStatsReporter::isDependentOnBootClasspath)
                .count();
    }

    private static boolean isDependentOnBootClasspath(
            @NonNull List<DexoptResult.DexContainerFileDexoptResult> filesResults) {
        return filesResults.stream()
                .map(DexoptResult.DexContainerFileDexoptResult::getActualCompilerFilter)
                .anyMatch(DexFile::isOptimizedCompilerFilter);
    }

    private static int toStatsdPassEnum(@BatchDexoptPass int pass) {
        switch (pass) {
            case ArtFlags.PASS_DOWNGRADE:
                return ArtStatsLog.BACKGROUND_DEXOPT_JOB_ENDED__PASS__PASS_DOWNGRADE;
            case ArtFlags.PASS_MAIN:
                return ArtStatsLog.BACKGROUND_DEXOPT_JOB_ENDED__PASS__PASS_MAIN;
            case ArtFlags.PASS_SUPPLEMENTARY:
                return ArtStatsLog.BACKGROUND_DEXOPT_JOB_ENDED__PASS__PASS_SUPPLEMENTARY;
        }
        throw new IllegalArgumentException("Unknown batch dexopt pass " + pass);
    }
}
