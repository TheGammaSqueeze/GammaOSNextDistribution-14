/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.adservices.service.measurement.noising;

import com.android.adservices.service.measurement.PrivacyParams;

import com.google.common.math.DoubleMath;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Combinatorics utilities used for randomization.
 */
public class Combinatorics {

    /**
     * Computes the binomial coefficient aka {@code n} choose {@code k}.
     * https://en.wikipedia.org/wiki/Binomial_coefficient
     *
     * @return binomial coefficient for (n choose k)
     * @throws ArithmeticException if the result overflows an int
     */
    static long getBinomialCoefficient(int n, int k) {
        if (k > n) {
            return 0L;
        }
        if (k == n || n == 0) {
            return 1L;
        }
        // getBinomialCoefficient(n, k) == getBinomialCoefficient(n, n - k).
        if (k > n - k) {
            k = n - k;
        }
        // (n choose k) = n (n -1) ... (n - (k - 1)) / k!
        // = mul((n + 1 - i) / i), i from 1 -> k.
        //
        // You might be surprised that this algorithm works just fine with integer
        // division (i.e. division occurs cleanly with no remainder). However, this is
        // true for a very simple reason. Imagine a value of `i` causes division with
        // remainder in the below algorithm. This immediately implies that
        // (n choose i) is fractional, which we know is not the case.
        long result = 1L;
        long nLong = (long) n;
        long kLong = (long) k;
        for (long i = 1L; i <= kLong; i++) {
            result = Math.multiplyExact(result, nLong + 1 - i);
            result = result / i;
        }
        return result;
    }

    /**
     * Returns the k-combination associated with the number {@code combinationIndex}. In
     * other words, returns the combination of {@code k} integers uniquely indexed by
     * {@code combinationIndex} in the combinatorial number system.
     * https://en.wikipedia.org/wiki/Combinatorial_number_system
     *
     * @return combinationIndex-th lexicographically smallest k-combination.
     * @throws ArithmeticException in case of int overflow
     */
    static long[] getKCombinationAtIndex(long combinationIndex, int k) {
        // Computes the combinationIndex-th lexicographically smallest k-combination.
        // https://en.wikipedia.org/wiki/Combinatorial_number_system
        //
        // A k-combination is a sequence of k non-negative integers in decreasing order.
        // a_k > a_{k-1} > ... > a_2 > a_1 >= 0.
        // k-combinations can be ordered lexicographically, with the smallest
        // k-combination being a_k=k-1, a_{k-1}=k-2, .., a_1=0. Given an index
        // combinationIndex>=0, and an order k, this method returns the
        // combinationIndex-th smallest k-combination.
        //
        // Given an index combinationIndex, the combinationIndex-th k-combination
        // is the unique set of k non-negative integers
        // a_k > a_{k-1} > ... > a_2 > a_1 >= 0
        // such that combinationIndex = \sum_{i=1}^k {a_i}\choose{i}
        //
        // We find this set via a simple greedy algorithm.
        // http://math0.wvstateu.edu/~baker/cs405/code/Combinadics.html
        long[] result = new long[k];
        if (k == 0) {
            return result;
        }
        // To find a_k, iterate candidates upwards from 0 until we've found the
        // maximum a such that (a choose k) <= combinationIndex. Let a_k = a. Use
        // the previous binomial coefficient to compute the next one. Note: possible
        // to speed this up via something other than incremental search.
        long target = combinationIndex;
        long candidate = (long) k - 1L;
        long binomialCoefficient = 0L;
        long nextBinomialCoefficient = 1L;
        while (nextBinomialCoefficient <= target) {
            candidate++;
            binomialCoefficient = nextBinomialCoefficient;
            // (n + 1 choose k) = (n choose k) * (n + 1) / (n + 1 - k)
            nextBinomialCoefficient = Math.multiplyExact(binomialCoefficient, (candidate + 1));
            nextBinomialCoefficient /= candidate + 1 - k;
        }
        // We know from the k-combination definition, all subsequent values will be
        // strictly decreasing. Find them all by decrementing candidate.
        // Use the previous binomial coefficient to compute the next one.
        long currentK = (long) k;
        int currentIndex = 0;
        while (true) {
            if (binomialCoefficient <= target) {
                result[currentIndex] = candidate;
                currentIndex++;
                target -= binomialCoefficient;
                if (currentIndex == k) {
                    return result;
                }
                // (n - 1 choose k - 1) = (n choose k) * k / n
                binomialCoefficient = binomialCoefficient * currentK / candidate;
                currentK--;
            } else {
                // (n - 1 choose k) = (n choose k) * (n - k) / n
                binomialCoefficient = binomialCoefficient * (candidate - currentK) / candidate;
            }
            candidate--;
        }
    }

    /**
     * Returns the number of possible sequences of "stars and bars" sequences
     * https://en.wikipedia.org/wiki/Stars_and_bars_(combinatorics),
     * which is equivalent to (numStars + numBars choose numStars).
     *
     * @param numStars number of stars
     * @param numBars  number of bars
     * @return number of possible sequences
     */
    public static long getNumberOfStarsAndBarsSequences(int numStars, int numBars) {
        return getBinomialCoefficient(numStars + numBars, numStars);
    }

    /**
     * Returns an array of the indices of every star in the stars and bars sequence indexed by
     * {@code sequenceIndex}.
     *
     * @param numStars number of stars in the sequence
     * @param sequenceIndex index of the sequence
     * @return list of indices of every star in stars & bars sequence
     */
    public static long[] getStarIndices(int numStars, long sequenceIndex) {
        return getKCombinationAtIndex(sequenceIndex, numStars);
    }

    /**
     * From an array with the index of every star in a stars and bars sequence, returns an array
     * which, for every star, counts the number of bars preceding it.
     *
     * @param starIndices indices of the stars in descending order
     * @return count of bars preceding every star
     */
    public static long[] getBarsPrecedingEachStar(long[] starIndices) {
        for (int i = 0; i < starIndices.length; i++) {
            long starIndex = starIndices[i];
            // There are {@code starIndex} prior positions in the sequence, and `i` prior
            // stars, so there are {@code starIndex - i} prior bars.
            starIndices[i] = starIndex - ((long) starIndices.length - 1L - (long) i);
        }
        return starIndices;
    }

    /**
     * Compute number of states from the trigger specification
     *
     * @param numBucketIncrements number of bucket increments (equivalent to number of triggers)
     * @param numTriggerData number of trigger data. (equivalent to number of metadata)
     * @param numWindows number of reporting windows
     * @return number of states
     */
    public static long getNumStatesArithmetic(
            int numBucketIncrements, int numTriggerData, int numWindows) {
        int numStars = numBucketIncrements;
        int numBars = Math.multiplyExact(numTriggerData, numWindows);
        return getNumberOfStarsAndBarsSequences(numStars, numBars);
    }

    /**
     * Using dynamic programming to compute number of states. Assuming the parameter validation has
     * been checked to avoid overflow or out of memory error
     *
     * @param totalCap total incremental cap
     * @param perTypeNumWindowList reporting window per trigger data
     * @param perTypeCapList cap per trigger data
     * @return number of states
     */
    private static long getNumStatesRecursive(
            int totalCap, int[] perTypeNumWindowList, int[] perTypeCapList) {
        int index = perTypeNumWindowList.length - 1;
        return getNumStatesRecursive(
                totalCap,
                index,
                perTypeNumWindowList[index],
                perTypeCapList[index],
                perTypeNumWindowList,
                perTypeCapList,
                new HashMap<>());
    }

    private static long getNumStatesRecursive(
            int totalCap,
            int index,
            int winVal,
            int capVal,
            int[] perTypeNumWindowList,
            int[] perTypeCapList,
            Map<List<Integer>, Long> dp) {
        List<Integer> key = List.of(totalCap, index, winVal, capVal);
        if (!dp.containsKey(key)) {
            if (winVal == 0 && index == 0) {
                dp.put(key, 1L);
            } else if (winVal == 0) {
                dp.put(key, getNumStatesRecursive(
                        totalCap,
                        index - 1,
                        perTypeNumWindowList[index - 1],
                        perTypeCapList[index - 1],
                        perTypeNumWindowList,
                        perTypeCapList,
                        dp));
            } else {
                long result = 0L;
                for (int i = 0; i <= Math.min(totalCap, capVal); i++) {
                    result = Math.addExact(result, getNumStatesRecursive(
                            totalCap - i,
                            index,
                            winVal - 1,
                            capVal - i,
                            perTypeNumWindowList,
                            perTypeCapList,
                            dp));
                }
                dp.put(key, result);
            }
        }
        return dp.get(key);
    }

    /**
     * Compute number of states for flexible event report API
     *
     * @param totalCap number of total increments
     * @param perTypeNumWindowList reporting window for each trigger data
     * @param perTypeCapList limit of the increment of each trigger data
     * @return number of states
     */
    public static long getNumStatesFlexApi(
            int totalCap, int[] perTypeNumWindowList, int[] perTypeCapList) {
        if (perTypeNumWindowList.length == 0 || perTypeCapList.length == 0) {
            return 1;
        }
        for (int i = 1; i < perTypeNumWindowList.length; i++) {
            if (perTypeNumWindowList[i] != perTypeNumWindowList[i - 1]) {
                return getNumStatesRecursive(totalCap, perTypeNumWindowList, perTypeCapList);
            }
        }
        for (int n : perTypeCapList) {
            if (n < totalCap) {
                return getNumStatesRecursive(totalCap, perTypeNumWindowList, perTypeCapList);
            }
        }

        return getNumStatesArithmetic(totalCap, perTypeCapList.length, perTypeNumWindowList[0]);
    }

    /**
     * @param numOfStates Number of States
     * @return the probability to use fake reports
     */
    public static double getFlipProbability(long numOfStates) {
        double epsilon = (double) PrivacyParams.getPrivacyEpsilon();
        return numOfStates / (numOfStates + Math.exp(epsilon) - 1D);
    }

    private static double getBinaryEntropy(double x) {
        if (DoubleMath.fuzzyEquals(x, 0.0d, PrivacyParams.NUMBER_EQUAL_THRESHOLD)
                || DoubleMath.fuzzyEquals(x, 1.0d, PrivacyParams.NUMBER_EQUAL_THRESHOLD)) {
            return 0;
        }
        return (-1.0) * x * DoubleMath.log2(x) - (1 - x) * DoubleMath.log2(1 - x);
    }

    /**
     * @param numOfStates Number of States
     * @param flipProbability Flip Probability
     * @return the information gain
     */
    public static double getInformationGain(long numOfStates, double flipProbability) {
        if (numOfStates <= 1L) {
            return 0d;
        }
        double log2Q = DoubleMath.log2(numOfStates);
        double fakeProbability = flipProbability * (numOfStates - 1L) / numOfStates;
        return log2Q
                - getBinaryEntropy(fakeProbability)
                - fakeProbability * DoubleMath.log2(numOfStates - 1);
    }

    /**
     * Generate fake report set given a trigger specification and the rank order number
     *
     * @param totalCap total_cap
     * @param perTypeNumWindowList per type number of window list
     * @param perTypeCapList per type cap list
     * @return a report set based on the input rank
     */
    public static List<AtomReportState> getReportSetBasedOnRank(
            int totalCap,
            int[] perTypeNumWindowList,
            int[] perTypeCapList,
            long rank,
            Map<List<Integer>, Long> dp) {
        int triggerTypeIndex = perTypeNumWindowList.length - 1;

        return getReportSetBasedOnRankRecursive(
                totalCap,
                triggerTypeIndex,
                perTypeNumWindowList[triggerTypeIndex],
                perTypeCapList[triggerTypeIndex],
                rank,
                perTypeNumWindowList,
                perTypeCapList,
                dp);
    }

    private static List<AtomReportState> getReportSetBasedOnRankRecursive(
            int totalCap,
            int triggerTypeIndex,
            int winVal,
            int capVal,
            long rank,
            int[] perTypeNumWindowList,
            int[] perTypeCapList,
            Map<List<Integer>, Long> numStatesLookupTable) {

        if (winVal == 0 && triggerTypeIndex == 0) {
            return new ArrayList<>();
        } else if (winVal == 0) {
            return getReportSetBasedOnRankRecursive(
                    totalCap,
                    triggerTypeIndex - 1,
                    perTypeNumWindowList[triggerTypeIndex - 1],
                    perTypeCapList[triggerTypeIndex - 1],
                    rank,
                    perTypeNumWindowList,
                    perTypeCapList,
                    numStatesLookupTable);
        }
        for (int i = 0; i <= Math.min(totalCap, capVal); i++) {
            long currentNumStates =
                    getNumStatesRecursive(
                            totalCap - i,
                            triggerTypeIndex,
                            winVal - 1,
                            capVal - i,
                            perTypeNumWindowList,
                            perTypeCapList,
                            numStatesLookupTable);
            if (currentNumStates > rank) {
                // The triggers to be appended.
                List<AtomReportState> toAppend = new ArrayList<>();
                for (int k = 0; k < i; k++) {
                    toAppend.add(new AtomReportState(triggerTypeIndex, winVal - 1));
                }
                List<AtomReportState> otherReports =
                        getReportSetBasedOnRankRecursive(
                                totalCap - i,
                                triggerTypeIndex,
                                winVal - 1,
                                capVal - i,
                                rank,
                                perTypeNumWindowList,
                                perTypeCapList,
                                numStatesLookupTable);
                toAppend.addAll(otherReports);
                return toAppend;
            } else {
                rank -= currentNumStates;
            }
        }
        // will not reach here
        return new ArrayList<>();
    }

    /** A single report including triggerDataType and window index for the fake report generation */
    public static class AtomReportState {
        private final int mTriggerDataType;
        private final int mWindowIndex;

        public AtomReportState(int triggerDataType, int windowIndex) {
            this.mTriggerDataType = triggerDataType;
            this.mWindowIndex = windowIndex;
        }

        public int getTriggerDataType() {
            return mTriggerDataType;
        }
        ;

        public final int getWindowIndex() {
            return mWindowIndex;
        }
        ;

        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof AtomReportState)) {
                return false;
            }
            AtomReportState t = (AtomReportState) obj;
            return mTriggerDataType == t.mTriggerDataType && mWindowIndex == t.mWindowIndex;
        }

        @Override
        public int hashCode() {
            return Objects.hash(mWindowIndex, mTriggerDataType);
        }
    }
}
