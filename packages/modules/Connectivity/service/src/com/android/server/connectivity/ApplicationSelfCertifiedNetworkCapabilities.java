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

package com.android.server.connectivity;


import android.annotation.NonNull;
import android.net.NetworkCapabilities;
import android.util.Log;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.util.ArrayDeque;


/**
 * The class for parsing and checking the self-declared application network capabilities.
 *
 * ApplicationSelfCertifiedNetworkCapabilities is an immutable class that
 * can parse the self-declared application network capabilities in the application resources. The
 * class also provides a helper method to check whether the requested network capabilities
 * already self-declared.
 */
public final class ApplicationSelfCertifiedNetworkCapabilities {

    public static final String PRIORITIZE_LATENCY = "NET_CAPABILITY_PRIORITIZE_LATENCY";
    public static final String PRIORITIZE_BANDWIDTH = "NET_CAPABILITY_PRIORITIZE_BANDWIDTH";

    private static final String TAG =
            ApplicationSelfCertifiedNetworkCapabilities.class.getSimpleName();
    private static final String NETWORK_CAPABILITIES_DECLARATION_TAG =
            "network-capabilities-declaration";
    private static final String USES_NETWORK_CAPABILITY_TAG = "uses-network-capability";
    private static final String NAME_TAG = "name";

    private long mRequestedNetworkCapabilities = 0;

    /**
     * Creates {@link ApplicationSelfCertifiedNetworkCapabilities} from a xml parser.
     *
     * <p> Here is an example of the xml syntax:
     *
     * <pre>
     * {@code
     *  <network-capabilities-declaration xmlns:android="http://schemas.android.com/apk/res/android">
     *     <uses-network-capability android:name="NET_CAPABILITY_PRIORITIZE_LATENCY"/>
     *     <uses-network-capability android:name="NET_CAPABILITY_PRIORITIZE_BANDWIDTH"/>
     * </network-capabilities-declaration>
     * }
     * </pre>
     * <p>
     *
     * @param xmlParser The underlying {@link XmlPullParser} that will read the xml.
     * @return An ApplicationSelfCertifiedNetworkCapabilities object.
     * @throws InvalidTagException    if the capabilities in xml config contains invalid tag.
     * @throws XmlPullParserException if xml parsing failed.
     * @throws IOException            if unable to read the xml file properly.
     */
    @NonNull
    public static ApplicationSelfCertifiedNetworkCapabilities createFromXml(
            @NonNull final XmlPullParser xmlParser)
            throws InvalidTagException, XmlPullParserException, IOException {
        return new ApplicationSelfCertifiedNetworkCapabilities(parseXml(xmlParser));
    }

    private static long parseXml(@NonNull final XmlPullParser xmlParser)
            throws InvalidTagException, XmlPullParserException, IOException {
        long requestedNetworkCapabilities = 0;
        final ArrayDeque<String> openTags = new ArrayDeque<>();

        while (checkedNextTag(xmlParser, openTags) != XmlPullParser.START_TAG) {
            continue;
        }

        // Validates the tag is "network-capabilities-declaration"
        if (!xmlParser.getName().equals(NETWORK_CAPABILITIES_DECLARATION_TAG)) {
            throw new InvalidTagException("Invalid tag: " + xmlParser.getName());
        }

        checkedNextTag(xmlParser, openTags);
        int eventType = xmlParser.getEventType();
        while (eventType != XmlPullParser.END_DOCUMENT) {
            switch (eventType) {
                case XmlPullParser.START_TAG:
                    // USES_NETWORK_CAPABILITY_TAG should directly be declared under
                    // NETWORK_CAPABILITIES_DECLARATION_TAG.
                    if (xmlParser.getName().equals(USES_NETWORK_CAPABILITY_TAG)
                            && openTags.size() == 1) {
                        int capability = parseDeclarationTag(xmlParser);
                        if (capability >= 0) {
                            requestedNetworkCapabilities |= 1L << capability;
                        }
                    } else {
                        Log.w(TAG, "Unknown tag: " + xmlParser.getName() + " ,tags stack size: "
                                + openTags.size());
                    }
                    break;
                default:
                    break;
            }
            eventType = checkedNextTag(xmlParser, openTags);
        }
        // Checks all the tags are parsed.
        if (!openTags.isEmpty()) {
            throw new InvalidTagException("Unbalanced tag: " + openTags.peek());
        }
        return requestedNetworkCapabilities;
    }

    private static int parseDeclarationTag(@NonNull final XmlPullParser xmlParser) {
        String name = null;
        for (int i = 0; i < xmlParser.getAttributeCount(); i++) {
            final String attrName = xmlParser.getAttributeName(i);
            if (attrName.equals(NAME_TAG)) {
                name = xmlParser.getAttributeValue(i);
            } else {
                Log.w(TAG, "Unknown attribute name: " + attrName);
            }
        }
        if (name != null) {
            switch (name) {
                case PRIORITIZE_BANDWIDTH:
                    return NetworkCapabilities.NET_CAPABILITY_PRIORITIZE_BANDWIDTH;
                case PRIORITIZE_LATENCY:
                    return NetworkCapabilities.NET_CAPABILITY_PRIORITIZE_LATENCY;
                default:
                    Log.w(TAG, "Unknown capability declaration name: " + name);
            }
        } else {
            Log.w(TAG, "uses-network-capability name must be specified");
        }
        // Invalid capability
        return -1;
    }

    private static int checkedNextTag(@NonNull final XmlPullParser xmlParser,
            @NonNull final ArrayDeque<String> openTags)
            throws XmlPullParserException, IOException, InvalidTagException {
        if (xmlParser.getEventType() == XmlPullParser.START_TAG) {
            openTags.addFirst(xmlParser.getName());
        } else if (xmlParser.getEventType() == XmlPullParser.END_TAG) {
            if (!openTags.isEmpty() && openTags.peekFirst().equals(xmlParser.getName())) {
                openTags.removeFirst();
            } else {
                throw new InvalidTagException("Unbalanced tag: " + xmlParser.getName());
            }
        }
        return xmlParser.next();
    }

    private ApplicationSelfCertifiedNetworkCapabilities(long requestedNetworkCapabilities) {
        mRequestedNetworkCapabilities = requestedNetworkCapabilities;
    }

    /**
     * Enforces self-certified capabilities are declared.
     *
     * @param networkCapabilities the input NetworkCapabilities to check against.
     * @throws SecurityException if the capabilities are not properly self-declared.
     */
    public void enforceSelfCertifiedNetworkCapabilitiesDeclared(
            @NonNull final NetworkCapabilities networkCapabilities) {
        if (networkCapabilities.hasCapability(
                NetworkCapabilities.NET_CAPABILITY_PRIORITIZE_BANDWIDTH)
                && !hasPrioritizeBandwidth()) {
            throw new SecurityException(
                    "Missing " + ApplicationSelfCertifiedNetworkCapabilities.PRIORITIZE_BANDWIDTH
                            + " declaration");
        }
        if (networkCapabilities.hasCapability(
                NetworkCapabilities.NET_CAPABILITY_PRIORITIZE_LATENCY)
                && !hasPrioritizeLatency()) {
            throw new SecurityException(
                    "Missing " + ApplicationSelfCertifiedNetworkCapabilities.PRIORITIZE_LATENCY
                            + " declaration");
        }
    }

    /**
     * Checks if NET_CAPABILITY_PRIORITIZE_LATENCY is declared.
     */
    private boolean hasPrioritizeLatency() {
        return (mRequestedNetworkCapabilities & (1L
                << NetworkCapabilities.NET_CAPABILITY_PRIORITIZE_LATENCY)) != 0;
    }

    /**
     * Checks if NET_CAPABILITY_PRIORITIZE_BANDWIDTH is declared.
     */
    private boolean hasPrioritizeBandwidth() {
        return (mRequestedNetworkCapabilities & (1L
                << NetworkCapabilities.NET_CAPABILITY_PRIORITIZE_BANDWIDTH)) != 0;
    }
}
