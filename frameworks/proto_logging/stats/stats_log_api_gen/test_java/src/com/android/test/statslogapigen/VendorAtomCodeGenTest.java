/*
* Copyright (C) 2023 The Android Open Source Project
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

package com.android.test.statslogapigen;

import static com.google.common.truth.Truth.assertThat;

import android.frameworks.stats.AnnotationId;
import android.frameworks.stats.VendorAtom;
import com.android.test.statslogapigen.VendorAtomsLog;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Runs the stats-log-api-gen tests for vendor atoms java generated code
 */
@RunWith(JUnit4.class)
public class VendorAtomCodeGenTest {

    /**
     * Tests Java auto generated code for specific vendor atom contains proper ids
     */
    @Test
    public void testAtomIdConstantsGeneration() throws Exception {
        assertThat(VendorAtomsLog.VENDOR_ATOM1).isEqualTo(105501);
        assertThat(VendorAtomsLog.VENDOR_ATOM2).isEqualTo(105502);
        assertThat(VendorAtomsLog.VENDOR_ATOM4).isEqualTo(105504);
    }

    /**
     * Tests Java auto generated code for specific vendor atom contains proper enums
     */
    @Test
    public void testAtomEnumConstantsGeneration() throws Exception {
        assertThat(VendorAtomsLog.VENDOR_ATOM1__ENUM_TYPE__TYPE_UNKNOWN).isEqualTo(0);
        assertThat(VendorAtomsLog.VENDOR_ATOM1__ENUM_TYPE__TYPE_1).isEqualTo(1);
        assertThat(VendorAtomsLog.VENDOR_ATOM1__ENUM_TYPE__TYPE_2).isEqualTo(2);
        assertThat(VendorAtomsLog.VENDOR_ATOM1__ENUM_TYPE__TYPE_3).isEqualTo(3);

        assertThat(VendorAtomsLog.VENDOR_ATOM1__ENUM_TYPE2__ANOTHER_TYPE_UNKNOWN).isEqualTo(0);
        assertThat(VendorAtomsLog.VENDOR_ATOM1__ENUM_TYPE2__ANOTHER_TYPE_1).isEqualTo(1);
        assertThat(VendorAtomsLog.VENDOR_ATOM1__ENUM_TYPE2__ANOTHER_TYPE_2).isEqualTo(2);
        assertThat(VendorAtomsLog.VENDOR_ATOM1__ENUM_TYPE2__ANOTHER_TYPE_3).isEqualTo(3);

        assertThat(VendorAtomsLog.VENDOR_ATOM2__ENUM_TYPE__TYPE_UNKNOWN).isEqualTo(0);
        assertThat(VendorAtomsLog.VENDOR_ATOM2__ENUM_TYPE__TYPE_1).isEqualTo(1);
        assertThat(VendorAtomsLog.VENDOR_ATOM2__ENUM_TYPE__TYPE_2).isEqualTo(2);
        assertThat(VendorAtomsLog.VENDOR_ATOM2__ENUM_TYPE__TYPE_3).isEqualTo(3);

        assertThat(VendorAtomsLog.VENDOR_ATOM2__ENUM_TYPE2__ANOTHER_TYPE_UNKNOWN).isEqualTo(0);
        assertThat(VendorAtomsLog.VENDOR_ATOM2__ENUM_TYPE2__ANOTHER_TYPE_1).isEqualTo(1);
        assertThat(VendorAtomsLog.VENDOR_ATOM2__ENUM_TYPE2__ANOTHER_TYPE_2).isEqualTo(2);
        assertThat(VendorAtomsLog.VENDOR_ATOM2__ENUM_TYPE2__ANOTHER_TYPE_3).isEqualTo(3);

        assertThat(VendorAtomsLog.VENDOR_ATOM4__ENUM_TYPE4__TYPE_UNKNOWN).isEqualTo(0);
        assertThat(VendorAtomsLog.VENDOR_ATOM4__ENUM_TYPE4__TYPE_1).isEqualTo(1);
    }

    final int kTestIntValue = 100;
    final long kTestLongValue = Long.MAX_VALUE - kTestIntValue;
    final float kTestFloatValue = (float)kTestIntValue / kTestLongValue;
    final boolean kTestBoolValue = true;
    final String kTestStringValue = "test_string";
    final String kTestStringValue2 = "test_string2";
    final int kTestStateValue =
            VendorAtomsLog.VENDOR_ATOM_WITH_TRUNCATE_TIMESTAMP__TEST_STATE__TEST_STATE_1;

    private interface CreateVendorAtom1ApiWrapper {
        // Method signatures of pointed method
        VendorAtom methodSignature(int atomId, String rdn, int enumField1,
                int enumField2, int intField, long longField, float floatField,
                boolean booleanField, int enumField3, int enumField4);
    }

    @Test
    public void testCreateVendorAtom1ApiGen() throws Exception {
        CreateVendorAtom1ApiWrapper funcWrapper = VendorAtomsLog::createVendorAtom;

        VendorAtom atom = funcWrapper.methodSignature(VendorAtomsLog.VENDOR_ATOM1,
                kTestStringValue,
                VendorAtomsLog.VENDOR_ATOM1__ENUM_TYPE__TYPE_1,
                VendorAtomsLog.VENDOR_ATOM1__ENUM_TYPE__TYPE_2,
                kTestIntValue, kTestLongValue, kTestFloatValue, kTestBoolValue,
                VendorAtomsLog.VENDOR_ATOM2__ENUM_TYPE2__ANOTHER_TYPE_2,
                VendorAtomsLog.VENDOR_ATOM2__ENUM_TYPE2__ANOTHER_TYPE_3);

        assertThat(atom.atomId).isEqualTo(VendorAtomsLog.VENDOR_ATOM1);
        assertThat(atom.reverseDomainName).isEqualTo(kTestStringValue);
        assertThat(atom.values.length).isEqualTo(8);
        assertThat(atom.values[0].getIntValue()).isEqualTo(
                VendorAtomsLog.VENDOR_ATOM1__ENUM_TYPE__TYPE_1);
        assertThat(atom.values[1].getIntValue()).isEqualTo(
                VendorAtomsLog.VENDOR_ATOM1__ENUM_TYPE__TYPE_2);
        assertThat(atom.values[2].getIntValue()).isEqualTo(kTestIntValue);
        assertThat(atom.values[3].getLongValue()).isEqualTo(kTestLongValue);
        assertThat(atom.values[4].getFloatValue()).isEqualTo(kTestFloatValue);
        assertThat(atom.values[5].getBoolValue()).isEqualTo(kTestBoolValue);
        assertThat(atom.values[6].getIntValue()).isEqualTo(
                VendorAtomsLog.VENDOR_ATOM2__ENUM_TYPE2__ANOTHER_TYPE_2);
        assertThat(atom.values[7].getIntValue()).isEqualTo(
                VendorAtomsLog.VENDOR_ATOM2__ENUM_TYPE2__ANOTHER_TYPE_3);

        assertThat(atom.valuesAnnotations).isNull();
        assertThat(atom.atomAnnotations).isNull();
    }

    private interface CreateVendorAtom3ApiWrapper {
        // Method signatures of pointed method
        VendorAtom methodSignature(int atomId, String rdn, int arg1);
    }

    @Test
    public void testCreateVendorAtom3ApiGen() throws Exception {
        CreateVendorAtom3ApiWrapper funcWrapper = VendorAtomsLog::createVendorAtom;

        VendorAtom atom = funcWrapper.methodSignature(VendorAtomsLog.VENDOR_ATOM3,
                kTestStringValue, kTestIntValue);

        assertThat(atom.atomId).isEqualTo(VendorAtomsLog.VENDOR_ATOM3);
        assertThat(atom.reverseDomainName).isEqualTo(kTestStringValue);
        assertThat(atom.values.length).isEqualTo(1);
        assertThat(atom.values[0].getIntValue()).isEqualTo(kTestIntValue);

        assertThat(atom.valuesAnnotations).isNull();
        assertThat(atom.atomAnnotations).isNull();
    }

    @Test
    public void testCreateVendorAtom6ApiGen() throws Exception {
        // The api signature should be equal to vendor_atom_3
        // since the enum state field is translated into ant int
        CreateVendorAtom3ApiWrapper funcWrapper = VendorAtomsLog::createVendorAtom;

        VendorAtom atom = funcWrapper.methodSignature(VendorAtomsLog.VENDOR_ATOM6,
                kTestStringValue, kTestStateValue);

        assertThat(atom.atomId).isEqualTo(VendorAtomsLog.VENDOR_ATOM6);
        assertThat(atom.reverseDomainName).isEqualTo(kTestStringValue);
        assertThat(atom.values.length).isEqualTo(1);
        assertThat(atom.values[0].getIntValue()).isEqualTo(kTestStateValue);

        assertThat(atom.valuesAnnotations).isNull();
        assertThat(atom.atomAnnotations).isNull();
    }

    private interface CreateVendorAtom4ApiWrapper {
        // Method signatures of pointed method
        VendorAtom methodSignature(int atomId, String rdn, float arg2, int arg3,
                long arg4, boolean arg5, int arg6, boolean[] arg7,
                float[] arg8, int[] arg9, long[] arg10, String[] arg11, int[] arg12);
    }

    @Test
    public void testCreateVendorAtom4ApiGen() throws Exception {
        CreateVendorAtom4ApiWrapper funcWrapper = VendorAtomsLog::createVendorAtom;

        final boolean[] repeatedBool = {true, false, true};
        final float[] repeatedFloat = {
                kTestFloatValue, kTestFloatValue + 1.f, kTestFloatValue + 2.f};
        final int[] repeatedInt = {kTestIntValue, kTestIntValue + 1, kTestIntValue + 2};
        final long[] repeatedLong = {kTestLongValue, kTestLongValue + 1, kTestLongValue + 2};
        final String[] repeatedString = {kTestStringValue, kTestStringValue2, kTestStringValue};
        final int[] repeatedEnum = {
                VendorAtomsLog.VENDOR_ATOM4__ENUM_TYPE4__TYPE_1,
                VendorAtomsLog.VENDOR_ATOM4__ENUM_TYPE4__TYPE_UNKNOWN,
                VendorAtomsLog.VENDOR_ATOM4__ENUM_TYPE4__TYPE_1};

        VendorAtom atom = funcWrapper.methodSignature(VendorAtomsLog.VENDOR_ATOM4, kTestStringValue,
                kTestFloatValue, kTestIntValue, kTestLongValue, kTestBoolValue,
                VendorAtomsLog.VENDOR_ATOM4__ENUM_TYPE4__TYPE_1, repeatedBool, repeatedFloat,
                repeatedInt, repeatedLong, repeatedString, repeatedEnum);

        assertThat(atom.atomId).isEqualTo(VendorAtomsLog.VENDOR_ATOM4);
        assertThat(atom.reverseDomainName).isEqualTo(kTestStringValue);
        assertThat(atom.values.length).isEqualTo(11);
        assertThat(atom.values[0].getFloatValue()).isEqualTo(kTestFloatValue);
        assertThat(atom.values[1].getIntValue()).isEqualTo(kTestIntValue);
        assertThat(atom.values[2].getLongValue()).isEqualTo(kTestLongValue);
        assertThat(atom.values[3].getBoolValue()).isEqualTo(kTestBoolValue);
        assertThat(atom.values[4].getIntValue()).isEqualTo(
                VendorAtomsLog.VENDOR_ATOM4__ENUM_TYPE4__TYPE_1);

        assertThat(atom.values[5].getRepeatedBoolValue()).isEqualTo(repeatedBool);
        assertThat(atom.values[6].getRepeatedFloatValue()).isEqualTo(repeatedFloat);
        assertThat(atom.values[7].getRepeatedIntValue()).isEqualTo(repeatedInt);
        assertThat(atom.values[8].getRepeatedLongValue()).isEqualTo(repeatedLong);
        assertThat(atom.values[9].getRepeatedStringValue()).isNotNull();
        assertThat(atom.values[9].getRepeatedStringValue().length).isEqualTo(
                  repeatedString.length);
        final String[] repeatedStringValue = atom.values[9].getRepeatedStringValue();
        for (int i = 0; i < repeatedString.length; i++) {
            assertThat(repeatedString[i]).isEqualTo(repeatedStringValue[i]);
        }
        assertThat(atom.values[10].getRepeatedIntValue()).isEqualTo(repeatedEnum);

        assertThat(atom.valuesAnnotations).isNull();
        assertThat(atom.atomAnnotations).isNull();
    }

    private interface CreateAtomWithTruncateTimestampApiWrapper {
        // Method signatures of pointed method
        VendorAtom methodSignature(int atomId, String rdn, int arg1);
    }

    @Test
    public void testCreateAtomWithTruncateTimestampApiGen() throws Exception {
        // same signature is used to build 4 different atoms with annotations
        CreateAtomWithTruncateTimestampApiWrapper funcWrapper =
                VendorAtomsLog::createVendorAtom;

        /**
         * Expected signature equal to CreateAtomWithTruncateTimestampApiWrapper to log 4 different
         * atoms with similar definitions:
         *      VendorAtomWithState3 stateAtom3 = 105508
         * which has 1 exclusive_state field annotations associated
         * and 3 different atoms with truncate_timestamp
         *      VendorAtomWithTruncateTimestamp truncateTimestampAtom1 = 105510 [
         *          (android.os.statsd.truncate_timestamp) = true
         *      ];
         *      VendorAtomWithTruncateTimestamp2 truncateTimestampAtom2 = 105511 [
         *          (android.os.statsd.truncate_timestamp) = true
         *      ];
         *      VendorAtomWithTruncateTimestamp3 truncateTimestampAtom3 = 105512 [
         *          (android.os.statsd.truncate_timestamp) = true
         *      ];
         *
         */
        VendorAtom atom = funcWrapper.methodSignature(
                VendorAtomsLog.TRUNCATE_TIMESTAMP_ATOM1, kTestStringValue,
                kTestStateValue);

        assertThat(atom.atomId).isEqualTo(VendorAtomsLog.TRUNCATE_TIMESTAMP_ATOM1);
        assertThat(atom.reverseDomainName).isEqualTo(kTestStringValue);
        assertThat(atom.values.length).isEqualTo(1);
        assertThat(atom.values[0].getIntValue()).isEqualTo(kTestStateValue);

        assertThat(atom.valuesAnnotations).isNull();
        assertThat(atom.atomAnnotations).isNotNull();
        assertThat(atom.atomAnnotations.length).isEqualTo(1);
        assertThat(atom.atomAnnotations[0].annotationId).isEqualTo(AnnotationId.TRUNCATE_TIMESTAMP);
        assertThat(atom.atomAnnotations[0].value.getBoolValue()).isEqualTo(true);

        final int kTestStateValue2 =
                VendorAtomsLog
                        .VENDOR_ATOM_WITH_TRUNCATE_TIMESTAMP2__TEST_STATE__TEST_STATE_1;

        VendorAtom atom2 = funcWrapper.methodSignature(
                VendorAtomsLog.TRUNCATE_TIMESTAMP_ATOM2, kTestStringValue,
                kTestStateValue2);

        assertThat(atom2.atomId).isEqualTo(VendorAtomsLog.TRUNCATE_TIMESTAMP_ATOM2);
        assertThat(atom2.reverseDomainName).isEqualTo(kTestStringValue);
        assertThat(atom2.values.length).isEqualTo(1);
        assertThat(atom2.values[0].getIntValue()).isEqualTo(kTestStateValue2);

        assertThat(atom2.valuesAnnotations).isNull();
        assertThat(atom2.atomAnnotations).isNotNull();
        assertThat(atom2.atomAnnotations.length).isEqualTo(1);
        assertThat(atom2.atomAnnotations[0].annotationId)
                .isEqualTo(AnnotationId.TRUNCATE_TIMESTAMP);
        assertThat(atom2.atomAnnotations[0].value.getBoolValue()).isEqualTo(true);

        VendorAtom atom3 = funcWrapper.methodSignature(
                VendorAtomsLog.TRUNCATE_TIMESTAMP_ATOM3, kTestStringValue,
                kTestIntValue);

        assertThat(atom3.atomId).isEqualTo(VendorAtomsLog.TRUNCATE_TIMESTAMP_ATOM3);
        assertThat(atom3.reverseDomainName).isEqualTo(kTestStringValue);
        assertThat(atom3.values.length).isEqualTo(1);
        assertThat(atom3.values[0].getIntValue()).isEqualTo(kTestIntValue);

        assertThat(atom3.valuesAnnotations).isNull();
        assertThat(atom3.atomAnnotations).isNotNull();
        assertThat(atom3.atomAnnotations.length).isEqualTo(1);
        assertThat(atom3.atomAnnotations[0].annotationId)
                .isEqualTo(AnnotationId.TRUNCATE_TIMESTAMP);
        assertThat(atom3.atomAnnotations[0].value.getBoolValue()).isEqualTo(true);

        final int kTestStateValue3 =
                VendorAtomsLog.VENDOR_ATOM_WITH_STATE3__TEST_STATE__TEST_STATE_1;

        VendorAtom atom4 = funcWrapper.methodSignature(
                VendorAtomsLog.STATE_ATOM3, kTestStringValue, kTestStateValue3);

        assertThat(atom4.atomId).isEqualTo(VendorAtomsLog.STATE_ATOM3);
        assertThat(atom4.reverseDomainName).isEqualTo(kTestStringValue);
        assertThat(atom4.values.length).isEqualTo(1);
        assertThat(atom4.values[0].getIntValue()).isEqualTo(kTestStateValue3);

        assertThat(atom4.valuesAnnotations).isNotNull();
        assertThat(atom4.valuesAnnotations.length).isEqualTo(1);
        assertThat(atom4.valuesAnnotations[0].valueIndex).isEqualTo(0);
        assertThat(atom4.valuesAnnotations[0].annotations.length).isEqualTo(1);
        assertThat(atom4.valuesAnnotations[0].annotations[0].annotationId)
                .isEqualTo(AnnotationId.EXCLUSIVE_STATE);
        assertThat(atom4.valuesAnnotations[0].annotations[0].value.getBoolValue()).isEqualTo(true);
        assertThat(atom4.atomAnnotations).isNull();
    }

    private interface CreateStateAtomApiWrapper {
        // Method signatures of pointed method
        VendorAtom methodSignature(int atomId, String rdn, int arg1, int arg2);
    }

    @Test
    public void testCreateAtomWithStateAnnotationsApiGen() throws Exception {
        CreateStateAtomApiWrapper funcWrapper = VendorAtomsLog::createVendorAtom;

        final int kTestStateValue =
                VendorAtomsLog.VENDOR_ATOM_WITH_STATE__TEST_STATE__TEST_STATE_2;

        /**
         * Expected signature equal to CreateStateAtomApiWrapper to log atom
         *      VendorAtomWithState stateAtom1 = 105506
         * which has 1 primary_field & 1 exclusive_state annotations associated with 2 fields
         */
        VendorAtom atom = funcWrapper.methodSignature(VendorAtomsLog.STATE_ATOM1,
                kTestStringValue, kTestIntValue, kTestStateValue);

        assertThat(atom.atomId).isEqualTo(VendorAtomsLog.STATE_ATOM1);
        assertThat(atom.reverseDomainName).isEqualTo(kTestStringValue);
        assertThat(atom.values.length).isEqualTo(2);
        assertThat(atom.values[0].getIntValue()).isEqualTo(kTestIntValue);
        assertThat(atom.values[1].getIntValue()).isEqualTo(kTestStateValue);

        assertThat(atom.valuesAnnotations).isNotNull();
        assertThat(atom.valuesAnnotations.length).isEqualTo(2);
        assertThat(atom.valuesAnnotations[0].valueIndex).isEqualTo(0);
        assertThat(atom.valuesAnnotations[0].annotations.length).isEqualTo(1);
        assertThat(atom.valuesAnnotations[0].annotations[0].annotationId)
                .isEqualTo(AnnotationId.PRIMARY_FIELD);
        assertThat(atom.valuesAnnotations[0].annotations[0].value.getBoolValue()).isEqualTo(true);

        assertThat(atom.valuesAnnotations[1].valueIndex).isEqualTo(1);
        assertThat(atom.valuesAnnotations[1].annotations.length).isEqualTo(1);
        assertThat(atom.valuesAnnotations[1].annotations[0].annotationId)
                .isEqualTo(AnnotationId.EXCLUSIVE_STATE);
        assertThat(atom.valuesAnnotations[1].annotations[0].value.getBoolValue()).isEqualTo(true);

        assertThat(atom.atomAnnotations).isNull();
    }

    private interface CreateStateAtom2ApiWrapper {
        // Method signatures of pointed method
        VendorAtom methodSignature(int atomId, String rdn, int arg1, int arg2, int arg3);
    }

    @Test
    public void testCreateAtomWithMultipleStateAnnotationsApiGen() throws Exception {
        CreateStateAtom2ApiWrapper funcWrapper = VendorAtomsLog::createVendorAtom;

        final int kTestStateValue =
                VendorAtomsLog.VENDOR_ATOM_WITH_STATE2__TEST_STATE__TEST_STATE_2;
        /**
         * Expected signature equal to CreateStateAtom2ApiWrapper to log atom
         *      VendorAtomWithState2 stateAtom2 = 105507
         * which has 2 primary_field & 1 exclusive_state annotations associated with 3 fields
         */
        VendorAtom atom = funcWrapper.methodSignature(VendorAtomsLog.STATE_ATOM2,
                kTestStringValue, kTestIntValue, kTestIntValue, kTestStateValue);

        assertThat(atom.atomId).isEqualTo(VendorAtomsLog.STATE_ATOM2);
        assertThat(atom.reverseDomainName).isEqualTo(kTestStringValue);
        assertThat(atom.values.length).isEqualTo(3);
        assertThat(atom.values[0].getIntValue()).isEqualTo(kTestIntValue);
        assertThat(atom.values[1].getIntValue()).isEqualTo(kTestIntValue);
        assertThat(atom.values[2].getIntValue()).isEqualTo(kTestStateValue);

        assertThat(atom.valuesAnnotations).isNotNull();
        assertThat(atom.valuesAnnotations.length).isEqualTo(3);
        assertThat(atom.valuesAnnotations[0].valueIndex).isEqualTo(0);
        assertThat(atom.valuesAnnotations[0].annotations.length).isEqualTo(1);
        assertThat(atom.valuesAnnotations[0].annotations[0].annotationId)
                .isEqualTo(AnnotationId.PRIMARY_FIELD);
        assertThat(atom.valuesAnnotations[0].annotations[0].value.getBoolValue()).isEqualTo(true);

        assertThat(atom.valuesAnnotations[1].valueIndex).isEqualTo(1);
        assertThat(atom.valuesAnnotations[1].annotations.length).isEqualTo(1);
        assertThat(atom.valuesAnnotations[1].annotations[0].annotationId)
                .isEqualTo(AnnotationId.PRIMARY_FIELD);
        assertThat(atom.valuesAnnotations[1].annotations[0].value.getBoolValue()).isEqualTo(true);

        assertThat(atom.valuesAnnotations[2].valueIndex).isEqualTo(2);
        assertThat(atom.valuesAnnotations[2].annotations.length).isEqualTo(1);
        assertThat(atom.valuesAnnotations[2].annotations[0].annotationId)
                .isEqualTo(AnnotationId.EXCLUSIVE_STATE);
        assertThat(atom.valuesAnnotations[2].annotations[0].value.getBoolValue()).isEqualTo(true);

        assertThat(atom.atomAnnotations).isNull();
    }

    private interface CreateStateAtom4ApiWrapper {
        // Method signatures of pointed method
        VendorAtom methodSignature(int atomId, String rdn, int arg1, boolean arg2);
    }

    @Test
    public void testCreateAtomWithMultipleAnnotationsPerValueApiGen() throws Exception {
        CreateStateAtom4ApiWrapper funcWrapper = VendorAtomsLog::createVendorAtom;

        final int kTestStateValue = VendorAtomsLog.VENDOR_ATOM_WITH_STATE4__STATE__ON;

        /**
         * Expected signature equal to CreateStateAtom4ApiWrapper to log atom
         *      VendorAtomWithState4 stateAtom4 = 105509
         * which has 4 state annotations associated with single value field (index 0)
         * testing that TRIGGER_STATE_RESET not be added
         */
        VendorAtom atom = funcWrapper.methodSignature(VendorAtomsLog.STATE_ATOM4,
                kTestStringValue, kTestStateValue, kTestBoolValue);

        assertThat(atom.atomId).isEqualTo(VendorAtomsLog.STATE_ATOM4);
        assertThat(atom.reverseDomainName).isEqualTo(kTestStringValue);
        assertThat(atom.values.length).isEqualTo(2);
        assertThat(atom.values[0].getIntValue()).isEqualTo(kTestStateValue);
        assertThat(atom.values[1].getBoolValue()).isEqualTo(kTestBoolValue);

        assertThat(atom.valuesAnnotations).isNotNull();
        assertThat(atom.valuesAnnotations.length).isEqualTo(2);
        assertThat(atom.valuesAnnotations[0].valueIndex).isEqualTo(0);
        assertThat(atom.valuesAnnotations[0].annotations.length).isEqualTo(2);
        assertThat(atom.valuesAnnotations[0].annotations[0].annotationId)
                .isEqualTo(AnnotationId.EXCLUSIVE_STATE);
        assertThat(atom.valuesAnnotations[0].annotations[0].value.getBoolValue()).isEqualTo(true);
        assertThat(atom.valuesAnnotations[0].annotations[1].annotationId)
                .isEqualTo(AnnotationId.STATE_NESTED);
        assertThat(atom.valuesAnnotations[0].annotations[1].value.getBoolValue()).isEqualTo(true);
        assertThat(atom.valuesAnnotations[1].valueIndex).isEqualTo(1);
        assertThat(atom.valuesAnnotations[1].annotations.length).isEqualTo(1);
        assertThat(atom.valuesAnnotations[1].annotations[0].annotationId)
                .isEqualTo(AnnotationId.PRIMARY_FIELD);
        assertThat(atom.valuesAnnotations[1].annotations[0].value.getBoolValue()).isEqualTo(true);

        assertThat(atom.atomAnnotations).isNull();
    }

    @Test
    public void testCreateAtomWithTriggerResetApiGen() throws Exception {
        CreateStateAtom4ApiWrapper funcWrapper = VendorAtomsLog::createVendorAtom;

        final int kDefaultStateValue =
                VendorAtomsLog.VENDOR_ATOM_WITH_STATE4__STATE__OFF;

        final int kTriggerResetStateValue =
                VendorAtomsLog.VENDOR_ATOM_WITH_STATE4__STATE__RESET;

        /**
         * Expected signature equal to CreateStateAtom4ApiWrapper to log atom
         *      VendorAtomWithState4 stateAtom4 = 105509
         * which has 4 state annotations associated with single value field (index 0)
         * testing that TRIGGER_STATE_RESET will be added
         */
        VendorAtom atom = funcWrapper.methodSignature(VendorAtomsLog.STATE_ATOM4,
                kTestStringValue, kTriggerResetStateValue, kTestBoolValue);

        assertThat(atom.atomId).isEqualTo(VendorAtomsLog.STATE_ATOM4);
        assertThat(atom.reverseDomainName).isEqualTo(kTestStringValue);
        assertThat(atom.values.length).isEqualTo(2);
        assertThat(atom.values[0].getIntValue()).isEqualTo(kTriggerResetStateValue);
        assertThat(atom.values[1].getBoolValue()).isEqualTo(kTestBoolValue);

        assertThat(atom.valuesAnnotations).isNotNull();
        assertThat(atom.valuesAnnotations.length).isEqualTo(2);
        assertThat(atom.valuesAnnotations[0].valueIndex).isEqualTo(0);
        assertThat(atom.valuesAnnotations[0].annotations.length).isEqualTo(3);
        assertThat(atom.valuesAnnotations[0].annotations[0].annotationId)
                .isEqualTo(AnnotationId.EXCLUSIVE_STATE);
        assertThat(atom.valuesAnnotations[0].annotations[0].value.getBoolValue()).isEqualTo(true);
        assertThat(atom.valuesAnnotations[0].annotations[1].annotationId)
                .isEqualTo(AnnotationId.STATE_NESTED);
        assertThat(atom.valuesAnnotations[0].annotations[1].value.getBoolValue()).isEqualTo(true);
        assertThat(atom.valuesAnnotations[0].annotations[2].annotationId)
                .isEqualTo(AnnotationId.TRIGGER_STATE_RESET);
        assertThat(atom.valuesAnnotations[0].annotations[2].value.getIntValue())
                .isEqualTo(kDefaultStateValue);
        assertThat(atom.valuesAnnotations[1].valueIndex).isEqualTo(1);
        assertThat(atom.valuesAnnotations[1].annotations.length).isEqualTo(1);
        assertThat(atom.valuesAnnotations[1].annotations[0].annotationId)
                .isEqualTo(AnnotationId.PRIMARY_FIELD);
        assertThat(atom.valuesAnnotations[1].annotations[0].value.getBoolValue()).isEqualTo(true);

        assertThat(atom.atomAnnotations).isNull();
    }
}
