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

package android.health.connect.internal.datatypes;

import android.health.connect.datatypes.ExerciseCompletionGoal;
import android.health.connect.datatypes.units.Energy;
import android.health.connect.datatypes.units.Length;
import android.os.Parcel;

import java.time.Duration;

/**
 * Internal ExerciseBlock. Part of {@link PlannedExerciseSessionRecordInternal}.
 *
 * @hide
 */
public abstract class ExerciseCompletionGoalInternal {

    /** Convert to {@link ExerciseCompletionGoal}. */
    public abstract ExerciseCompletionGoal toExternalObject();

    /** Subclass identifier used during serialization/deserialization. */
    abstract int getTypeId();

    abstract void writeFieldsToParcel(Parcel parcel);

    /** Convert from external representation. */
    public static ExerciseCompletionGoalInternal fromExternalObject(
            ExerciseCompletionGoal externalObject) {
        if (externalObject instanceof ExerciseCompletionGoal.DistanceGoal) {
            ExerciseCompletionGoal.DistanceGoal goal =
                    (ExerciseCompletionGoal.DistanceGoal) externalObject;
            return new DistanceGoalInternal(goal.getDistance());
        } else if (externalObject instanceof ExerciseCompletionGoal.StepsGoal) {
            ExerciseCompletionGoal.StepsGoal goal =
                    (ExerciseCompletionGoal.StepsGoal) externalObject;
            return new StepsGoalInternal(goal.getSteps());
        } else if (externalObject instanceof ExerciseCompletionGoal.DurationGoal) {
            ExerciseCompletionGoal.DurationGoal goal =
                    (ExerciseCompletionGoal.DurationGoal) externalObject;
            return new DurationGoalInternal(goal.getDuration());
        } else if (externalObject instanceof ExerciseCompletionGoal.RepetitionsGoal) {
            ExerciseCompletionGoal.RepetitionsGoal goal =
                    (ExerciseCompletionGoal.RepetitionsGoal) externalObject;
            return new RepetitionsGoalInternal(goal.getRepetitions());
        } else if (externalObject instanceof ExerciseCompletionGoal.TotalCaloriesBurnedGoal) {
            ExerciseCompletionGoal.TotalCaloriesBurnedGoal goal =
                    (ExerciseCompletionGoal.TotalCaloriesBurnedGoal) externalObject;
            return new TotalCaloriesBurnedGoalInternal(goal.getTotalCalories());
        } else if (externalObject instanceof ExerciseCompletionGoal.ActiveCaloriesBurnedGoal) {
            ExerciseCompletionGoal.ActiveCaloriesBurnedGoal goal =
                    (ExerciseCompletionGoal.ActiveCaloriesBurnedGoal) externalObject;
            return new ActiveCaloriesBurnedGoalInternal(goal.getActiveCalories());
        } else if (externalObject instanceof ExerciseCompletionGoal.UnspecifiedGoal) {
            return UnspecifiedGoalInternal.INSTANCE;
        } else {
            return UnknownGoalInternal.INSTANCE;
        }
    }

    /** Serialize to parcel. */
    public void writeToParcel(Parcel parcel) {
        parcel.writeInt(getTypeId());
        writeFieldsToParcel(parcel);
    }

    /** Deserialize from parcel. */
    public static ExerciseCompletionGoalInternal readFromParcel(Parcel parcel) {
        int goalTypeId = parcel.readInt();
        switch (goalTypeId) {
            case DistanceGoalInternal.DISTANCE_GOAL_TYPE_ID:
                return DistanceGoalInternal.readFieldsFromParcel(parcel);
            case StepsGoalInternal.STEPS_GOAL_TYPE_ID:
                return StepsGoalInternal.readFieldsFromParcel(parcel);
            case DurationGoalInternal.DURATION_GOAL_TYPE_ID:
                return DurationGoalInternal.readFieldsFromParcel(parcel);
            case RepetitionsGoalInternal.REPETITIONS_GOAL_TYPE_ID:
                return RepetitionsGoalInternal.readFieldsFromParcel(parcel);
            case TotalCaloriesBurnedGoalInternal.TOTAL_CALORIES_BURNED_GOAL_TYPE_ID:
                return TotalCaloriesBurnedGoalInternal.readFieldsFromParcel(parcel);
            case ActiveCaloriesBurnedGoalInternal.ACTIVE_CALORIES_BURNED_GOAL_TYPE_ID:
                return ActiveCaloriesBurnedGoalInternal.readFieldsFromParcel(parcel);
            case UnspecifiedGoalInternal.UNSPECIFIED_GOAL_TYPE_ID:
                return UnspecifiedGoalInternal.INSTANCE;
            case UnknownGoalInternal.UNKNOWN_GOAL_TYPE_ID:
                // Fall through.
            default:
                // Can never happen. Client side and service side always have consistent version.
                return UnknownGoalInternal.INSTANCE;
        }
    }

    public static final class DistanceGoalInternal extends ExerciseCompletionGoalInternal {
        private static final int DISTANCE_GOAL_TYPE_ID = 2;

        @Override
        void writeFieldsToParcel(Parcel parcel) {
            parcel.writeDouble(this.mDistance.getInMeters());
        }

        /** Read from parcel. */
        public static DistanceGoalInternal readFieldsFromParcel(Parcel parcel) {
            return new DistanceGoalInternal(Length.fromMeters(parcel.readDouble()));
        }

        private final Length mDistance;

        public DistanceGoalInternal(Length distance) {
            this.mDistance = distance;
        }

        public Length getDistance() {
            return mDistance;
        }

        @Override
        public ExerciseCompletionGoal toExternalObject() {
            return new ExerciseCompletionGoal.DistanceGoal(mDistance);
        }

        @Override
        int getTypeId() {
            return DISTANCE_GOAL_TYPE_ID;
        }
    }

    public static final class StepsGoalInternal extends ExerciseCompletionGoalInternal {
        private static final int STEPS_GOAL_TYPE_ID = 3;

        @Override
        void writeFieldsToParcel(Parcel parcel) {
            parcel.writeInt(this.mSteps);
        }

        /** Read from parcel. */
        public static StepsGoalInternal readFieldsFromParcel(Parcel parcel) {
            return new StepsGoalInternal(parcel.readInt());
        }

        private final int mSteps;

        public StepsGoalInternal(int steps) {
            this.mSteps = steps;
        }

        public int getSteps() {
            return mSteps;
        }

        @Override
        public ExerciseCompletionGoal toExternalObject() {
            return new ExerciseCompletionGoal.StepsGoal(mSteps);
        }

        @Override
        int getTypeId() {
            return STEPS_GOAL_TYPE_ID;
        }
    }

    public static final class DurationGoalInternal extends ExerciseCompletionGoalInternal {
        private static final int DURATION_GOAL_TYPE_ID = 4;

        @Override
        void writeFieldsToParcel(Parcel parcel) {
            parcel.writeLong(this.mDuration.toMillis());
        }

        /** Read from parcel. */
        public static DurationGoalInternal readFieldsFromParcel(Parcel parcel) {
            return new DurationGoalInternal(Duration.ofMillis(parcel.readLong()));
        }

        private final Duration mDuration;

        public DurationGoalInternal(Duration duration) {
            this.mDuration = duration;
        }

        public Duration getDuration() {
            return mDuration;
        }

        @Override
        public ExerciseCompletionGoal toExternalObject() {
            return new ExerciseCompletionGoal.DurationGoal(mDuration);
        }

        @Override
        int getTypeId() {
            return DURATION_GOAL_TYPE_ID;
        }
    }

    public static final class RepetitionsGoalInternal extends ExerciseCompletionGoalInternal {
        private static final int REPETITIONS_GOAL_TYPE_ID = 5;

        @Override
        void writeFieldsToParcel(Parcel parcel) {
            parcel.writeInt(this.mReps);
        }

        /** Read from parcel. */
        public static RepetitionsGoalInternal readFieldsFromParcel(Parcel parcel) {
            return new RepetitionsGoalInternal(parcel.readInt());
        }

        private final int mReps;

        public RepetitionsGoalInternal(int reps) {
            this.mReps = reps;
        }

        public int getReps() {
            return mReps;
        }

        @Override
        public ExerciseCompletionGoal toExternalObject() {
            return new ExerciseCompletionGoal.RepetitionsGoal(mReps);
        }

        @Override
        int getTypeId() {
            return REPETITIONS_GOAL_TYPE_ID;
        }
    }

    public static final class TotalCaloriesBurnedGoalInternal
            extends ExerciseCompletionGoalInternal {
        private static final int TOTAL_CALORIES_BURNED_GOAL_TYPE_ID = 6;

        @Override
        void writeFieldsToParcel(Parcel parcel) {
            parcel.writeDouble(this.mTotalCalories.getInCalories());
        }

        /** Read from parcel. */
        public static TotalCaloriesBurnedGoalInternal readFieldsFromParcel(Parcel parcel) {
            return new TotalCaloriesBurnedGoalInternal(Energy.fromCalories(parcel.readDouble()));
        }

        private final Energy mTotalCalories;

        public TotalCaloriesBurnedGoalInternal(Energy totalCalories) {
            this.mTotalCalories = totalCalories;
        }

        public Energy getTotalCalories() {
            return mTotalCalories;
        }

        @Override
        public ExerciseCompletionGoal toExternalObject() {
            return new ExerciseCompletionGoal.TotalCaloriesBurnedGoal(mTotalCalories);
        }

        @Override
        int getTypeId() {
            return TOTAL_CALORIES_BURNED_GOAL_TYPE_ID;
        }
    }

    public static final class ActiveCaloriesBurnedGoalInternal
            extends ExerciseCompletionGoalInternal {
        private static final int ACTIVE_CALORIES_BURNED_GOAL_TYPE_ID = 7;

        @Override
        void writeFieldsToParcel(Parcel parcel) {
            parcel.writeDouble(this.mActiveCalories.getInCalories());
        }

        /** Read from parcel. */
        public static ActiveCaloriesBurnedGoalInternal readFieldsFromParcel(Parcel parcel) {
            return new ActiveCaloriesBurnedGoalInternal(Energy.fromCalories(parcel.readDouble()));
        }

        private final Energy mActiveCalories;

        public ActiveCaloriesBurnedGoalInternal(Energy activeCalories) {
            this.mActiveCalories = activeCalories;
        }

        public Energy getActiveCalories() {
            return mActiveCalories;
        }

        @Override
        public ExerciseCompletionGoal toExternalObject() {
            return new ExerciseCompletionGoal.ActiveCaloriesBurnedGoal(mActiveCalories);
        }

        @Override
        int getTypeId() {
            return ACTIVE_CALORIES_BURNED_GOAL_TYPE_ID;
        }
    }

    /**
     * Represents a goal that is not recognised by the platform. This could happen when e.g. a
     * version rollback occurs, i.e. a version is stored in the database that is not supported by
     * the current (older) version.
     */
    public static final class UnknownGoalInternal extends ExerciseCompletionGoalInternal {
        static final int UNKNOWN_GOAL_TYPE_ID = 0;

        public static final UnknownGoalInternal INSTANCE = new UnknownGoalInternal();

        @Override
        void writeFieldsToParcel(Parcel parcel) {
            // No fields to write.
        }

        UnknownGoalInternal() {}

        @Override
        public ExerciseCompletionGoal toExternalObject() {
            return ExerciseCompletionGoal.UnknownGoal.INSTANCE;
        }

        @Override
        int getTypeId() {
            return UNKNOWN_GOAL_TYPE_ID;
        }
    }

    /**
     * Represents an open goal that has no specific metric associated with completion. It is up to
     * the user to determine when the goal is complete.
     */
    public static final class UnspecifiedGoalInternal extends ExerciseCompletionGoalInternal {
        static final int UNSPECIFIED_GOAL_TYPE_ID = 1;

        public static final UnspecifiedGoalInternal INSTANCE = new UnspecifiedGoalInternal();

        @Override
        void writeFieldsToParcel(Parcel parcel) {
            // No fields to write.
        }

        UnspecifiedGoalInternal() {}

        @Override
        public ExerciseCompletionGoal toExternalObject() {
            return ExerciseCompletionGoal.UnspecifiedGoal.INSTANCE;
        }

        @Override
        int getTypeId() {
            return UNSPECIFIED_GOAL_TYPE_ID;
        }
    }
}
