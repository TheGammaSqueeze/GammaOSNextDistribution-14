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

package android.telephony.ims.cts;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertNotNull;

import android.net.Uri;
import android.util.ArraySet;
import android.util.Base64;

import java.nio.ByteBuffer;
import java.util.Random;
import java.util.Set;

public class SipDialogAttributes {

    // Generates sequential string values starting from a random number.
    private static volatile int sNextStringCounter;
    static {
        sNextStringCounter = new Random().nextInt();
    }

    public final String branchId;
    public final String callId;
    public final String fromHeader;
    public final String fromTag;
    public final String toUri;
    public final String toHeader;
    private final String mFromUri;
    private final Set<String> mAcceptContactTags;
    private String mToTag;

    public SipDialogAttributes() {
        branchId = getNextString();
        callId = getNextString();
        mFromUri = getNextSipUri();
        fromHeader = generateContactUri(mFromUri);
        fromTag = getNextString();
        toUri = getNextSipUri();
        toHeader = generateContactUri(toUri);
        mAcceptContactTags = new ArraySet<>();
    }

    private SipDialogAttributes(String branchId, String callId, String fromUri,
            String fromTag, String toUri, String toTag, Set<String> acceptContactTags) {
        this.branchId = branchId;
        this.callId = callId;
        this.mFromUri = fromUri;
        this.fromHeader = generateContactUri(fromUri);
        this.fromTag = fromTag;
        this.toUri = toUri;
        this.toHeader = generateContactUri(toUri);
        mToTag = toTag;
        mAcceptContactTags = acceptContactTags;
    }

    public void setToTag() {
        if (mToTag == null) {
            mToTag = getNextString();
        }
    }

    public String getToTag() {
        return mToTag;
    }

    /**
     * Add a tag to the Accept-Contact header feature tag list.
     */
    public void addAcceptContactTag(String featureTag) {
        mAcceptContactTags.add(featureTag);
    }

    /**
     * @return The feature tags in the Accept-Contact feature tag list.
     */
    public Set<String> getAcceptContactTags() {
        return mAcceptContactTags;
    }

    /**
     * @return The same attributes with a refreshed via branch param.
     */
    public SipDialogAttributes copyWithNewBranch() {
        return new SipDialogAttributes(branchId, callId, mFromUri, fromTag, toUri,
                mToTag, mAcceptContactTags);
    }

    public SipDialogAttributes fromExisting() {
        return new SipDialogAttributes(branchId, callId, mFromUri, fromTag, toUri,
                null, mAcceptContactTags);
    }

    public SipDialogAttributes invertFromTo() {
        return new SipDialogAttributes(branchId, callId, toUri, fromTag, mFromUri, mToTag,
                mAcceptContactTags);
    }

    private String getNextSipUri() {
        return "sip:" + getNextString() + "@" + SipMessageUtils.BASE_ADDRESS;
    }

    private String getNextString() {
        // Get a string representation of the entry counter
        byte[] idByteArray = ByteBuffer.allocate(4).putInt(sNextStringCounter++).array();
        return Base64.encodeToString(idByteArray,
                Base64.NO_WRAP | Base64.NO_PADDING | Base64.URL_SAFE);
    }

    private String generateContactUri(String sipUri) {
        Uri uri = Uri.parse(sipUri);
        assertNotNull(uri);
        String[] user = uri.getSchemeSpecificPart().split("@", 2);
        assertNotNull(user);
        assertEquals(2, user.length);
        return user[0] + " <" + sipUri + ">";
    }
}
