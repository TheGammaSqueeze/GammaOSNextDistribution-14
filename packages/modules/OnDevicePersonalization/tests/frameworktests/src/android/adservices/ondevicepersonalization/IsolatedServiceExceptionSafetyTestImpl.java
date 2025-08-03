/*
 * Copyright 2024 The Android Open Source Project
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

package android.adservices.ondevicepersonalization;

import android.os.OutcomeReceiver;

import androidx.annotation.NonNull;

import java.lang.reflect.InvocationTargetException;

public class IsolatedServiceExceptionSafetyTestImpl extends IsolatedService {

    @NonNull
    @Override
    public IsolatedWorker onRequest(@NonNull RequestToken requestToken) {
        return new SampleWorker();
    }

    static class SampleWorker implements IsolatedWorker {

        @Override
        public void onExecute(
                @NonNull ExecuteInput input,
                @NonNull OutcomeReceiver<ExecuteOutput, IsolatedServiceException> receiver) {
            String exName = input.getAppParams().getString("ex", "n/a");
            constructAndThrowException(exName);
        }

        @Override
        public void onDownloadCompleted(
                @NonNull DownloadCompletedInput input,
                @NonNull
                        OutcomeReceiver<DownloadCompletedOutput, IsolatedServiceException>
                                receiver) {
            KeyValueStore downloadedContents = input.getDownloadedContents();
            String exName = new String(downloadedContents.get("ex"));
            constructAndThrowException(exName);
        }

        @Override
        public void onRender(
                @NonNull RenderInput input,
                @NonNull OutcomeReceiver<RenderOutput, IsolatedServiceException> receiver) {
            String exName = input.getRenderingConfig().getKeys().get(0);
            constructAndThrowException(exName);
        }

        @Override
        public void onEvent(
                @NonNull EventInput input,
                @NonNull OutcomeReceiver<EventOutput, IsolatedServiceException> receiver) {
            String exName = input.getParameters().getString("ex", "n/a");
            constructAndThrowException(exName);
        }

        @Override
        public void onTrainingExamples(
                @NonNull TrainingExamplesInput input,
                @NonNull
                        OutcomeReceiver<TrainingExamplesOutput, IsolatedServiceException>
                                receiver) {
            String exName = input.getTaskName();
            constructAndThrowException(exName);
        }

        @Override
        public void onWebTrigger(
                @NonNull WebTriggerInput input,
                @NonNull OutcomeReceiver<WebTriggerOutput, IsolatedServiceException> receiver) {
            String exName = input.getAppPackageName();
            constructAndThrowException(exName);
        }

        private static void constructAndThrowException(String exName) {
            Class<?> aClass;
            try {
                aClass = Class.forName(exName);
                throw (RuntimeException) aClass.getDeclaredConstructor().newInstance();
            } catch (ClassNotFoundException
                    | NoSuchMethodException
                    | InvocationTargetException
                    | InstantiationException
                    | IllegalAccessException e) {
                throw new RuntimeException(e);
            }
        }
    }
}
