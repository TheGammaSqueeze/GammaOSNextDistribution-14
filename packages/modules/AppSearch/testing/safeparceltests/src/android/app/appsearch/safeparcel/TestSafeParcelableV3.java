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
package android.app.appsearch.safeparcel;

import android.os.Parcel;

@SafeParcelable.Class(creator = "TestSafeParcelableV3Creator")
public final class TestSafeParcelableV3 extends AbstractSafeParcelable {

    public static final Creator<TestSafeParcelableV3> CREATOR = new TestSafeParcelableV3Creator();

    @VersionField(id = 1)
    public final int versionCode;

    @Field(id = 2)
    public String publicString;

    @Field(id = 3, getter = "getProtectedString")
    protected String protectedString;

    @Field(id = 4, getter = "getPrivateString")
    private String privateString;

    @Field(id = 5)
    String packageString;

    @Field(id = 6)
    public final String publicFinalString;

    @Field(id = 7, getter = "getProtectedFinalString")
    protected final String protectedFinalString;

    @Field(id = 8, getter = "getPrivateFinalString")
    private final String privateFinalString;

    @Field(id = 9)
    final String packageFinalString;

    @Field(id = 10, defaultValue = "10")
    public final int publicFinalIntWithDefaultValue;

    @Constructor
    public TestSafeParcelableV3(
            @Param(id = 1) int versionCode,
            @Param(id = 2) String publicString,
            @Param(id = 3) String protectedString,
            @Param(id = 4) String privateString,
            @Param(id = 5) String packageString,
            @Param(id = 6) String publicFinalString,
            @Param(id = 7) String protectedFinalString,
            @Param(id = 8) String privateFinalString,
            @Param(id = 9) String packageFinalString,
            @Param(id = 10) int publicFinalIntWithDefaultValue) {
        this.versionCode = versionCode;
        this.publicString = publicString;
        this.protectedString = protectedString;
        this.privateString = privateString;
        this.packageString = packageString;
        this.publicFinalString = publicFinalString;
        this.protectedFinalString = protectedFinalString;
        this.privateFinalString = privateFinalString;
        this.packageFinalString = packageFinalString;
        this.publicFinalIntWithDefaultValue = publicFinalIntWithDefaultValue;
    }

    public TestSafeParcelableV3(
            String publicString,
            String protectedString,
            String privateString,
            String packageString,
            String publicFinalString,
            String protectedFinalString,
            String privateFinalString,
            String packageFinalString) {
        this(
                1,
                publicString,
                protectedString,
                privateString,
                packageString,
                publicFinalString,
                protectedFinalString,
                privateFinalString,
                packageFinalString,
                /* Using default value here */ 10);
    }

    @Override
    public void writeToParcel(Parcel out, int flags) {
        TestSafeParcelableV3Creator.writeToParcel(this, out, flags);
    }

    public String getProtectedString() {
        return protectedString;
    }

    public String getPrivateString() {
        return privateString;
    }

    public String getProtectedFinalString() {
        return protectedFinalString;
    }

    public String getPrivateFinalString() {
        return privateFinalString;
    }

    // TODO(b/37774152): implement hashCode() (go/equals-hashcode-lsc)
    @SuppressWarnings("EqualsHashCode")
    @Override
    public boolean equals(Object object) {
        if (object instanceof TestSafeParcelableV3) {
            TestSafeParcelableV3 p = (TestSafeParcelableV3) object;
            return publicString.equals(p.publicString)
                    && protectedString.equals(p.protectedString)
                    && privateString.equals(p.privateString)
                    && packageString.equals(p.packageString)
                    && publicFinalString.equals(p.publicFinalString)
                    && protectedFinalString.equals(p.protectedFinalString)
                    && privateFinalString.equals(p.privateFinalString)
                    && packageFinalString.equals(p.packageFinalString)
                    && publicFinalIntWithDefaultValue == p.publicFinalIntWithDefaultValue;
        }
        return false;
    }
}
