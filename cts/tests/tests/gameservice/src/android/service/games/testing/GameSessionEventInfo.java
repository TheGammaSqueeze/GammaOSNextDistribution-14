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

package android.service.games.testing;


import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Objects;

public final class GameSessionEventInfo implements Parcelable {
    @NonNull
    public static final Parcelable.Creator<GameSessionEventInfo> CREATOR =
            new Parcelable.Creator<GameSessionEventInfo>() {
                @Override
                public GameSessionEventInfo createFromParcel(Parcel source) {
                    return new GameSessionEventInfo(
                            source.readString(),
                            source.readInt(),
                            source.readInt());
                }

                @Override
                public GameSessionEventInfo[] newArray(int size) {
                    return new GameSessionEventInfo[0];
                }
            };

    @IntDef(value = {
        GAME_SESSION_EVENT_CREATED,
        GAME_SESSION_EVENT_DESTROYED,
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface GameSessionEvent {}

    public static final int GAME_SESSION_EVENT_CREATED = 1;
    public static final int GAME_SESSION_EVENT_DESTROYED = 2;

    private final String mGamePackageName;
    private final int mTaskId;
    @GameSessionEvent
    private final int mEvent;

    public static GameSessionEventInfo create(
            String gamePackageName,
            int taskId,
            @GameSessionEvent int event) {
        return new GameSessionEventInfo(gamePackageName, taskId, event);
    }

    private GameSessionEventInfo(String gamePackageName, int taskId, @GameSessionEvent int event) {
        mGamePackageName = gamePackageName;
        mTaskId = taskId;
        mEvent = event;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        dest.writeString(mGamePackageName);
        dest.writeInt(mTaskId);
        dest.writeInt(mEvent);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof GameSessionEventInfo)) return false;
        GameSessionEventInfo that = (GameSessionEventInfo) o;
        return mTaskId == that.mTaskId && mEvent == that.mEvent && Objects.equals(
                mGamePackageName, that.mGamePackageName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mGamePackageName, mTaskId, mEvent);
    }

    @Override
    public String toString() {
        return "GameSessionEventInfo{"
                + "mGamePackageName='"
                + mGamePackageName
                + '\''
                + ", mTaskId="
                + mTaskId
                + ", mEvent="
                + mEvent
                + '}';
    }
}
