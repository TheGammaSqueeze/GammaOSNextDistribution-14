/*
 * Copyright (C) 2021 The Android Open Source Project
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
package android.contentcaptureservice.cts;

import android.contentcaptureservice.cts.CtsContentCaptureService.Session;
import android.os.Bundle;

import androidx.annotation.NonNull;

public class NullTextViewActivity extends AbstractRootViewActivity {

    /*
     * We are trying to test a specific case where multi-line layouts associated with text views
     * which return null from getText() cause crashes, so we need to use a long multi-line
     * string as the text content.
     */
    private static final String LOREM_IPSUM = "Lorem ipsum dolor sit amet, consectetur adipiscing"
            + " elit. Nunc tempor molestie risus, quis accumsan nisi varius non. In hac habitasse"
            + " platea dictumst. Nullam et felis a ligula accumsan gravida non a justo. Praesent "
            + "sit amet ante tincidunt quam congue rhoncus. Ut rutrum augue vitae magna posuere, "
            + "et dictum ligula dapibus. Fusce sodales arcu nec commodo malesuada. Curabitur elit"
            + " enim, auctor at massa in, fermentum aliquam tellus. Cras pharetra nisl ut arcu "
            + "condimentum lacinia sed in libero. Suspendisse condimentum congue gravida. "
            + "Curabitur ac sem eget est suscipit efficitur.\n"
            + "\n"
            + "Nunc dignissim velit dui, id sollicitudin turpis ultrices sed. Cras venenatis, "
            + "enim in placerat tempus, urna eros sollicitudin augue, quis ornare lectus mi quis "
            + "tellus. Nam ultricies non magna a varius. Quisque sit amet sapien eget lectus "
            + "vulputate gravida. Orci varius natoque penatibus et magnis dis parturient montes, "
            + "nascetur ridiculus mus. Mauris sit amet dui non mi iaculis pretium quis in massa. "
            + "Ut luctus a diam ac faucibus. Integer ut felis maximus, imperdiet nulla quis, "
            + "semper dolor. Nam molestie dapibus magna in fringilla.\n"
            + "\n"
            + "Nunc id mi non elit malesuada euismod et et justo. Quisque sollicitudin molestie "
            + "lobortis. Vestibulum dapibus ante ac eros tempus fermentum. Nunc ut massa laoreet,"
            + " placerat lorem sit amet, aliquam lectus. Sed imperdiet massa eu laoreet commodo. "
            + "In blandit iaculis lectus, faucibus tristique ligula. Nam vehicula orci nisl, quis"
            + " tristique lectus aliquet nec. Class aptent taciti sociosqu ad litora torquent per"
            + " conubia nostra, per inceptos himenaeos. Cras gravida accumsan nulla non mattis.\n"
            + "\n"
            + "Proin sed vulputate neque. In eget tristique enim. Donec faucibus velit et "
            + "vulputate viverra. Nullam feugiat risus id dolor pharetra, ut rhoncus massa "
            + "egestas. Duis ut suscipit diam. Phasellus in felis eu risus porttitor maximus "
            + "vitae a ex. Suspendisse pharetra luctus eleifend. Nam viverra eleifend nunc eget "
            + "dapibus. Maecenas nec dapibus neque. Sed ut quam sed diam mattis pellentesque. "
            + "Vivamus in placerat massa. Nunc ut quam eu arcu laoreet facilisis. Sed eget libero"
            + " a libero egestas tincidunt nec vel dui. Proin maximus tortor et odio pharetra, "
            + "eget tempor tellus ultrices.\n"
            + "\n"
            + "Cras sodales urna vitae ligula fermentum, ac pretium est auctor. Aliquam hendrerit"
            + " neque non augue placerat, quis ultrices ex suscipit. Morbi congue nisl ac "
            + "sagittis finibus. Nulla semper lectus dolor, sit amet vestibulum orci luctus eget."
            + " Cras sed efficitur nisl. Donec mollis diam nec dolor fermentum, at dictum metus "
            + "varius. Maecenas sit amet magna consequat lorem mollis rutrum. Nunc convallis "
            + "sodales mi, imperdiet porttitor justo. Curabitur in massa vestibulum, pulvinar "
            + "elit nec, efficitur tortor. Praesent luctus diam sit amet ex tempus venenatis. "
            + "Vestibulum quis odio fermentum, imperdiet tellus non, varius ligula. Nunc "
            + "ultricies quam non magna semper maximus et vel justo. Fusce rutrum sagittis turpis"
            + " quis egestas. Fusce euismod dui a felis scelerisque tristique. Nam eget enim at "
            + "libero tempor volutpat sit amet vel justo.";

    @Override
    protected void setContentViewOnCreate(Bundle savedInstanceState) {
        setContentView(R.layout.null_textview_activity);

        NullTextView view = findViewById(R.id.main_textview);
        view.setText(LOREM_IPSUM);
    }

    @Override
    public void assertDefaultEvents(@NonNull Session session) {
    }
}
