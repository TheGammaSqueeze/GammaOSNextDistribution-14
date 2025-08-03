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

package android.hardware.google.pixel;

import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assert.fail;

import com.google.common.collect.ImmutableSet;
import com.google.protobuf.Descriptors;

public abstract class BasePixelAtomsTest {

    protected abstract ImmutableSet<Integer> getAllowlistedAtoms();

    protected void doTestPushedAtomsHasReverseDomainName(Descriptors.Descriptor vendorAtoms) {
        for (Descriptors.FieldDescriptor field : vendorAtoms.getFields()) {
            Descriptors.OneofDescriptor oneOfAtom = field.getContainingOneof();
            if (oneOfAtom == null) {
                fail("Atom not declared in a 'oneof' field");
            }
            if ("pushed".equals(oneOfAtom.getName())) {
                int atomId = field.getNumber();
                if (getAllowlistedAtoms().contains(atomId)) {
                    continue;
                }
                if (atomId < 100001 || atomId > 150000) {
                    fail("Atom id not in vendor range");
                }
                String atomName = field.getName();
                assertWithMessage(atomName + " field 1 should not be empty")
                        .that(field.getMessageType().findFieldByNumber(1))
                        .isNotNull();
                assertWithMessage(atomName + "field 1 should be of string type")
                        .that(field.getMessageType().findFieldByNumber(1).getType())
                        .isEqualTo(Descriptors.FieldDescriptor.Type.STRING);
                assertWithMessage(atomName +
                        " should contain reverse_domain_name as field 1.")
                        .that(field.getMessageType().findFieldByNumber(1).getName())
                        .isEqualTo("reverse_domain_name");
            }
        }
    }
}
