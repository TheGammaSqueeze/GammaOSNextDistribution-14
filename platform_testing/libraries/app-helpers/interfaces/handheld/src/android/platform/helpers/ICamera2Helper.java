/*
 * Copyright (C) 2020 The Android Open Source Project
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

package android.platform.helpers;

/** An App Helper interface for Camera2 */
public interface ICamera2Helper extends IAppHelper {
    /**
     * Setup expectations: Camera2 app is open and skipped the initial dialogs.
     *
     * <p>Opens a stream preview of the specified camera and mode combination.
     *
     * @param mode - Enum value for the requested stream mode.
     * @param camera - Enum value for the requested camera.
     */
    public void goToPreview(ModeType mode, CameraType camera);

    /** Available modes for the stream set up. */
    public enum ModeType {
        VIDEO("video"),
        STILL_CAPTURE("still-capture");

        private String name;

        /** Stores the provided name as an enum field. */
        ModeType(String name) {
            this.name = name;
        }

        /** Returns a programmer-friendly name for the {@code ModeType}. */
        @Override
        public String toString() {
            return name;
        }

        /**
         * Searches for a {@code ModeType} matching the provided name.
         *
         * @param name The name to search for.
         */
        public static ModeType fromString(String name) {
            for (ModeType mode : values()) {
                if (mode.toString().equals(name)) return mode;
            }
            throw new IllegalArgumentException();
        }
    };

    /** Available cameras for the stream set up. */
    public enum CameraType {
        FRONT("front"),
        BACK("back");

        private String name;

        /** Stores the provided name as an enum field. */
        CameraType(String name) {
            this.name = name;
        }

        /** Returns a programmer-friendly name for the {@CameraType}. */
        @Override
        public String toString() {
            return name;
        }

        /**
         * Searches for a {@CameraType} matching the provided name.
         *
         * @param name The name to search for.
         */
        public static CameraType fromString(String name) {
            for (CameraType camera : values()) {
                if (camera.toString().equals(name)) return camera;
            }
            throw new IllegalArgumentException();
        }
    };

    /** Names for option keys that can be re-used between tests */
    public enum TestOptionKey {
        CAMERA_FACING("camera-facing"),
        STREAM_DURATION("stream-duration"),
        CAPTURE_MODE("capture-mode"),
        RECORD_VIDEO("record-video");

        private String name;

        /** Stores the provided name as an enum field. */
        TestOptionKey(String name) {
            this.name = name;
        }

        /** Returns a more programmer-friendly name for the {@TestOptionKey}. */
        @Override
        public String toString() {
            return name;
        }
    }

    /**
     * Setup expectations: On any video preview page and not recording a video.
     *
     * <p>Start recording a video
     */
    public void startRecording();

    /**
     * Setup expectations: Recording a video.
     *
     * <p>Stop recording a video
     */
    public void stopRecording();
}
