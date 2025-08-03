/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.car;

import static android.car.builtin.view.DisplayHelper.INVALID_PORT;
import static android.car.drivingstate.CarUxRestrictionsConfiguration.Builder.SpeedRange.MAX_SPEED;
import static android.car.drivingstate.CarUxRestrictionsManager.UX_RESTRICTION_MODE_BASELINE;

import android.annotation.Nullable;
import android.annotation.XmlRes;
import android.car.CarOccupantZoneManager;
import android.car.builtin.util.Slogf;
import android.car.drivingstate.CarDrivingStateEvent;
import android.car.drivingstate.CarUxRestrictions;
import android.car.drivingstate.CarUxRestrictionsConfiguration;
import android.car.drivingstate.CarUxRestrictionsConfiguration.Builder;
import android.car.drivingstate.CarUxRestrictionsConfiguration.DrivingStateRestrictions;
import android.content.Context;
import android.content.res.XmlResourceParser;
import android.util.Log;

import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * @hide
 */
public final class CarUxRestrictionsConfigurationXmlParser {
    private static final String TAG = CarLog.tagFor(CarUxRestrictionsConfigurationXmlParser.class);
    private static final boolean DBG = Slogf.isLoggable(TAG, Log.DEBUG);
    private static final int UX_RESTRICTIONS_UNKNOWN = -1;
    private static final float INVALID_SPEED = -1f;
    private static final String XML_NAMESPACE = null;

    // XML tags
    private static final String XML_ROOT_ELEMENT = "UxRestrictions";
    private static final String XML_RESTRICTION_MAPPING = "RestrictionMapping";
    private static final String XML_RESTRICTION_PARAMETERS = "RestrictionParameters";
    private static final String XML_DRIVING_STATE = "DrivingState";
    private static final String XML_RESTRICTIONS = "Restrictions";
    private static final String XML_STRING_RESTRICTIONS = "StringRestrictions";
    private static final String XML_CONTENT_RESTRICTIONS = "ContentRestrictions";

    // XML attributes
    private static final String XML_PHYSICAL_PORT = "physicalPort";
    private static final String XML_OCCUPANT_ZONE_ID = "occupantZoneId";
    private static final String XML_DISPLAY_TYPE = "displayType";

    private static final String XML_STATE = "state";
    private static final String XML_MIN_SPEED = "minSpeed";
    private static final String XML_MAX_SPEED = "maxSpeed";
    private static final String XML_MODE = "mode";
    private static final String XML_UXR = "uxr";
    private static final String XML_REQUIRES_DISTRACTION_OPTIMIZATION =
            "requiresDistractionOptimization";
    private static final String XML_MAX_LENGTH = "maxLength";
    private static final String XML_MAX_CUMULATIVE_ITEMS = "maxCumulativeItems";
    private static final String XML_MAX_DEPTH = "maxDepth";

    // XML attribute values
    private static final String XML_STATE_PARKED = "parked";
    private static final String XML_STATE_IDLING = "idling";
    private static final String XML_STATE_MOVING = "moving";
    private static final String XML_UXR_BASELINE = "baseline";
    private static final String XML_UXR_NO_DIALPAD = "no_dialpad";
    private static final String XML_UXR_NO_FILTERING = "no_filtering";
    private static final String XML_UXR_LIMIT_STRING_LENGTH = "limit_string_length";
    private static final String XML_UXR_NO_KEYBOARD = "no_keyboard";
    private static final String XML_UXR_NO_VIDEO = "no_video";
    private static final String XML_UXR_LIMIT_CONTENT = "limit_content";
    private static final String XML_UXR_NO_SETUP = "no_setup";
    private static final String XML_UXR_NO_TEXT_MESSAGE = "no_text_message";
    private static final String XML_UXR_NO_VOICE_TRANSCRIPTION = "no_voice_transcription";
    private static final String XML_UXR_FULLY_RESTRICTED = "fully_restricted";

    private final Context mContext;

    private int mGlobalMaxRestrictedStringLength = UX_RESTRICTIONS_UNKNOWN;
    private int mGlobalMaxCumulativeContentItems = UX_RESTRICTIONS_UNKNOWN;
    private int mGlobalMaxContentDepth = UX_RESTRICTIONS_UNKNOWN;
    private final List<CarUxRestrictionsConfiguration.Builder> mConfigBuilders = new ArrayList<>();

    private CarUxRestrictionsConfigurationXmlParser(Context context) {
        mContext = context;
    }

    /**
     * Loads the UX restrictions related information from the XML resource.
     *
     * @return parsed CarUxRestrictionsConfiguration; {@code null} if the XML is malformed.
     */
    @Nullable
    public static List<CarUxRestrictionsConfiguration> parse(
            Context context, @XmlRes int xmlResource)
            throws IOException, XmlPullParserException {
        return new CarUxRestrictionsConfigurationXmlParser(context).parse(xmlResource);
    }

    @Nullable
    private List<CarUxRestrictionsConfiguration> parse(@XmlRes int xmlResource)
            throws IOException, XmlPullParserException {

        XmlResourceParser parser = mContext.getResources().getXml(xmlResource);
        if (parser == null) {
            Slogf.e(TAG, "Invalid Xml resource");
            return null;
        }

        if (!traverseUntilStartTag(parser)) {
            Slogf.e(TAG, "XML root element invalid: " + parser.getName());
            return null;
        }

        if (!traverseUntilEndOfDocument(parser)) {
            Slogf.e(TAG, "Could not parse XML to end");
            return null;
        }

        List<CarUxRestrictionsConfiguration> configs = new ArrayList<>();
        for (int i = 0, length = mConfigBuilders.size(); i < length; i++) {
            CarUxRestrictionsConfiguration.Builder builder = mConfigBuilders.get(i);
            builder.setMaxStringLengthIfNotSet(mGlobalMaxRestrictedStringLength)
                    .setMaxCumulativeContentItemsIfNotSet(mGlobalMaxCumulativeContentItems)
                    .setMaxContentDepthIfNotSet(mGlobalMaxContentDepth);
            configs.add(builder.build());
        }
        return configs;
    }

    private boolean traverseUntilStartTag(XmlResourceParser parser)
            throws IOException, XmlPullParserException {
        int type;
        // Traverse till the first tag is hit
        while ((type = parser.next()) != XmlResourceParser.END_DOCUMENT
                && type != XmlResourceParser.START_TAG) {
            // Do nothing.
        }
        return XML_ROOT_ELEMENT.equals(parser.getName());
    }

    private boolean traverseUntilEndOfDocument(XmlResourceParser parser)
            throws XmlPullParserException, IOException {
        while (parser.getEventType() != XmlResourceParser.END_DOCUMENT) {
            // Every time a start tag is hit, check for the type of the tag
            // and load the corresponding information.
            if (parser.next() == XmlResourceParser.START_TAG) {
                switch (parser.getName()) {
                    case XML_RESTRICTION_MAPPING:
                        // Each RestrictionMapping tag represents a new set of rules.
                        CarUxRestrictionsConfiguration.Builder builder =
                                new CarUxRestrictionsConfiguration.Builder();
                        mConfigBuilders.add(builder);

                        if (!mapDrivingStateToRestrictions(parser)) {
                            Slogf.e(TAG, "Could not map driving state to restriction.");
                            return false;
                        }
                        parseLocalRestrictionParameters(parser, builder);
                        break;
                    case XML_RESTRICTION_PARAMETERS:
                        if (!parseGlobalRestrictionParameters(parser)) {
                            // Failure to parse is automatically handled by falling back to
                            // defaults. Just log the information here.
                            Slogf.w(TAG, "Error reading restrictions parameters. "
                                    + "Falling back to platform defaults.");
                        }
                        break;
                    default:
                        Slogf.w(TAG, "Unknown class:" + parser.getName());
                }
            }
        }
        return true;
    }

    /**
     * Parses the information in the local {@code RestrictionParameters} within a
     * {@code RestrictionMapping} container tag to read the parameters for the applicable UX
     * restrictions.
     */
    private void parseLocalRestrictionParameters(XmlResourceParser parser,
            CarUxRestrictionsConfiguration.Builder builder)
            throws IOException, XmlPullParserException {
        if (parser == null) {
            Slogf.e(TAG, "Parser is null");
            return;
        }

        if (XML_RESTRICTION_MAPPING.equals(parser.getName())) {
            if (DBG) {
                Slogf.d(TAG,
                        "No local restriction parameters for this, the global restriction "
                                + "parameters will be used");
            }
            return;
        }
        if (!XML_RESTRICTION_PARAMETERS.equals(parser.getName())) {
            if (DBG) {
                Slogf.d(TAG,
                        "Unsupported Restriction Parameters in XML: %s, the global restriction "
                                + "parameters will be used", parser.getName());
            }
            return;
        }
        parseRestrictionParameters(parser, builder);
    }

    private void parseRestrictionParameters(XmlResourceParser parser,
            @Nullable CarUxRestrictionsConfiguration.Builder builder)
            throws IOException, XmlPullParserException {
        while (parser.getEventType() != XmlResourceParser.END_DOCUMENT) {
            int type = parser.next();
            // Break if all <RestrictionParameters> are parsed
            if (type == XmlResourceParser.END_TAG && XML_RESTRICTION_PARAMETERS.equals(
                    parser.getName())) {
                return;
            }

            if (type == XmlResourceParser.START_TAG) {
                switch (parser.getName()) {
                    case XML_STRING_RESTRICTIONS:
                        setStringRestrictionParameters(builder, parser);
                        break;
                    case XML_CONTENT_RESTRICTIONS:
                        setContentRestrictionParameters(builder, parser);
                        break;
                    default:
                        Slogf.i(TAG, "Unsupported Restriction Parameters in XML: %s",
                                parser.getName());
                        break;
                }
            }
        }
    }

    /**
     * Adds string restriction parameters to the {@link CarUxRestrictionsConfiguration.Builder}
     * object.
     */
    private void setStringRestrictionParameters(CarUxRestrictionsConfiguration.Builder builder,
            XmlResourceParser parser) {
        int maxRestrictedStringLength = parser.getAttributeIntValue(XML_NAMESPACE, XML_MAX_LENGTH,
                UX_RESTRICTIONS_UNKNOWN);
        if (builder != null) {
            builder.setMaxStringLengthIfNotSet(maxRestrictedStringLength);
        } else {
            mGlobalMaxRestrictedStringLength = maxRestrictedStringLength;
        }
    }

    /**
     * Adds content restriction parameters to the {@link CarUxRestrictionsConfiguration.Builder}
     * object.
     */
    private void setContentRestrictionParameters(CarUxRestrictionsConfiguration.Builder builder,
            XmlResourceParser parser) {
        int maxCumulativeContentItems = parser.getAttributeIntValue(XML_NAMESPACE,
                XML_MAX_CUMULATIVE_ITEMS, UX_RESTRICTIONS_UNKNOWN);
        int maxContentDepth = parser.getAttributeIntValue(XML_NAMESPACE, XML_MAX_DEPTH,
                UX_RESTRICTIONS_UNKNOWN);
        if (builder != null) {
            // Set local restriction parameters
            builder.setMaxContentDepthIfNotSet(maxContentDepth);
            builder.setMaxCumulativeContentItemsIfNotSet(maxCumulativeContentItems);
        } else {
            // Set global restriction parameters
            mGlobalMaxCumulativeContentItems = maxCumulativeContentItems;
            mGlobalMaxContentDepth = maxContentDepth;
        }
    }

    /**
     * Parses the information in the <restrictionMapping> tag to construct the mapping from
     * driving state to UX restrictions.
     */
    private boolean mapDrivingStateToRestrictions(XmlResourceParser parser)
            throws IOException, XmlPullParserException {
        if (parser == null) {
            Slogf.e(TAG, "Invalid arguments");
            return false;
        }
        // The parser should be at the <RestrictionMapping> tag at this point.
        if (!XML_RESTRICTION_MAPPING.equals(parser.getName())) {
            Slogf.e(TAG, "Parser not at RestrictionMapping element: " + parser.getName());
            return false;
        }
        // read port
        int portValue = parser.getAttributeIntValue(XML_NAMESPACE, XML_PHYSICAL_PORT,
                INVALID_PORT);
        if (portValue != INVALID_PORT) {
            int port = CarUxRestrictionsConfiguration.Builder.validatePort(portValue);
            getCurrentBuilder().setPhysicalPort(port);
        }

        // read occupant zone id
        int zoneIdValue = parser.getAttributeIntValue(XML_NAMESPACE, XML_OCCUPANT_ZONE_ID,
                CarOccupantZoneManager.OccupantZoneInfo.INVALID_ZONE_ID);
        if (zoneIdValue != CarOccupantZoneManager.OccupantZoneInfo.INVALID_ZONE_ID) {
            int zoneId = CarUxRestrictionsConfiguration.Builder.validateOccupantZoneId(zoneIdValue);
            getCurrentBuilder().setOccupantZoneId(zoneId);
        }

        // read occupant zone id
        int displayTypeValue = parser.getAttributeIntValue(XML_NAMESPACE, XML_DISPLAY_TYPE,
                CarOccupantZoneManager.DISPLAY_TYPE_UNKNOWN);
        if (displayTypeValue != CarOccupantZoneManager.DISPLAY_TYPE_UNKNOWN) {
            int displayType = CarUxRestrictionsConfiguration.Builder.validateDisplayType(
                    displayTypeValue);
            getCurrentBuilder().setDisplayType(displayType);
        }

        if (!traverseToTag(parser, XML_DRIVING_STATE)) {
            Slogf.e(TAG, "No <" + XML_DRIVING_STATE + "> tag in XML");
            return false;
        }
        // Handle all the <DrivingState> tags.
        while (XML_DRIVING_STATE.equals(parser.getName())) {
            if (parser.getEventType() == XmlResourceParser.START_TAG) {
                // 1. Get the driving state attributes: driving state and speed range
                int drivingState = getDrivingState(
                        parser.getAttributeValue(XML_NAMESPACE, XML_STATE));
                float minSpeed = 0;
                try {
                    minSpeed = Float
                            .parseFloat(parser.getAttributeValue(XML_NAMESPACE, XML_MIN_SPEED));
                } catch (NullPointerException | NumberFormatException e) {
                    minSpeed = INVALID_SPEED;
                }

                float maxSpeed = 0;
                try {
                    maxSpeed = Float
                            .parseFloat(parser.getAttributeValue(XML_NAMESPACE, XML_MAX_SPEED));
                } catch (NullPointerException | NumberFormatException e) {
                    maxSpeed = MAX_SPEED;
                }

                // 2. Traverse to the <Restrictions> tag
                if (!traverseToTag(parser, XML_RESTRICTIONS)) {
                    Slogf.e(TAG, "No <" + XML_RESTRICTIONS + "> tag in XML");
                    return false;
                }

                // 3. Parse the restrictions for this driving state
                Builder.SpeedRange speedRange = parseSpeedRange(minSpeed, maxSpeed);
                if (!parseAllRestrictions(parser, drivingState, speedRange)) {
                    Slogf.e(TAG, "Could not parse restrictions for driving state:" + drivingState);
                    return false;
                }
            }
            parser.next();
        }
        return true;
    }

    private int getDrivingState(String state) {
        if (state == null) {
            return CarDrivingStateEvent.DRIVING_STATE_UNKNOWN;
        }

        switch (state.trim().toLowerCase(Locale.ROOT)) {
            case XML_STATE_PARKED:
                return CarDrivingStateEvent.DRIVING_STATE_PARKED;
            case XML_STATE_IDLING:
                return CarDrivingStateEvent.DRIVING_STATE_IDLING;
            case XML_STATE_MOVING:
                return CarDrivingStateEvent.DRIVING_STATE_MOVING;
            default:
                return CarDrivingStateEvent.DRIVING_STATE_UNKNOWN;
        }
    }

    /**
     * Parses all <restrictions> tags nested with <drivingState> tag.
     */
    private boolean parseAllRestrictions(XmlResourceParser parser,
            int drivingState, Builder.SpeedRange speedRange)
            throws IOException, XmlPullParserException {
        if (parser == null) {
            Slogf.e(TAG, "Invalid arguments");
            return false;
        }
        // The parser should be at the <Restrictions> tag at this point.
        if (!XML_RESTRICTIONS.equals(parser.getName())) {
            Slogf.e(TAG, "Parser not at Restrictions element: " + parser.getName());
            return false;
        }
        while (XML_RESTRICTIONS.equals(parser.getName())) {
            if (parser.getEventType() == XmlResourceParser.START_TAG) {
                // Parse one restrictions tag.
                List<DrivingStateRestrictions> restrictions = parseRestrictions(parser);
                if (restrictions == null || restrictions.isEmpty()) {
                    Slogf.e(TAG, "");
                    return false;
                }
                // Update the builder if the driving state and restrictions info are valid.
                if (drivingState != CarDrivingStateEvent.DRIVING_STATE_UNKNOWN
                        && restrictions != null) {
                    for (int i = 0; i < restrictions.size(); i++) {
                        DrivingStateRestrictions restriction = restrictions.get(i);
                        restriction.setSpeedRange(speedRange);

                        if (DBG) {
                            Slogf.d(TAG, "Map " + drivingState + " : " + restriction);
                        }
                        getCurrentBuilder().setUxRestrictions(drivingState, restriction);
                    }
                }
            }
            parser.next();
        }
        return true;
    }

    /**
     * Parses the <restrictions> tag nested with the <drivingState>.  This provides the restrictions
     * for the enclosing driving state.
     *
     * @return Driving state restrictions list. The list would contain more than one value if mode
     * attribute defines more than one mode separated by "|".
     */
    @Nullable
    private List<DrivingStateRestrictions> parseRestrictions(XmlResourceParser parser)
            throws IOException, XmlPullParserException {
        if (parser == null) {
            Slogf.e(TAG, "Invalid Arguments");
            return null;
        }

        int restrictions = UX_RESTRICTIONS_UNKNOWN;
        List<String> restrictionModes =  new ArrayList<>();
        boolean requiresOpt = true;
        if (XML_RESTRICTIONS.equals(parser.getName())
                && parser.getEventType() == XmlResourceParser.START_TAG) {
            restrictions = getRestrictions(parser.getAttributeValue(XML_NAMESPACE, XML_UXR));
            updateModes(parser.getAttributeValue(XML_NAMESPACE, XML_MODE), restrictionModes);
            requiresOpt = parser.getAttributeBooleanValue(XML_NAMESPACE,
                    XML_REQUIRES_DISTRACTION_OPTIMIZATION, true);
            parser.next();
        }

        List<DrivingStateRestrictions> drivingStateRestrictions = new ArrayList<>();

        for (int i = 0; i < restrictionModes.size(); i++) {
            drivingStateRestrictions.add(new DrivingStateRestrictions()
                    .setDistractionOptimizationRequired(requiresOpt)
                    .setRestrictions(restrictions)
                    .setMode(restrictionModes.get(i)));
        }

        return drivingStateRestrictions;
    }

    private void updateModes(String allModes, List<String> modes) {
        if (allModes == null) {
            modes.add(UX_RESTRICTION_MODE_BASELINE);
            return;
        }
        String[] allModesArray = allModes.split("\\|");

        for (int i = 0; i < allModesArray.length; i++) {
            modes.add(allModesArray[i].trim());
        }
    }

    private int getRestrictions(String allRestrictions) {
        if (allRestrictions == null) {
            return CarUxRestrictions.UX_RESTRICTIONS_FULLY_RESTRICTED;
        }
        int restrictionsValue = 0;
        String[] restrictions = allRestrictions.split("\\|");
        for (int i = 0; i < restrictions.length; i++) {
            String restriction = restrictions[i].trim().toLowerCase(Locale.ROOT);
            switch (restriction) {
                case XML_UXR_BASELINE:
                    restrictionsValue = restrictionsValue
                            | CarUxRestrictions.UX_RESTRICTIONS_BASELINE;
                    break;
                case XML_UXR_NO_DIALPAD:
                    restrictionsValue = restrictionsValue
                            | CarUxRestrictions.UX_RESTRICTIONS_NO_DIALPAD;
                    break;
                case XML_UXR_NO_FILTERING:
                    restrictionsValue = restrictionsValue
                            | CarUxRestrictions.UX_RESTRICTIONS_NO_FILTERING;
                    break;
                case XML_UXR_LIMIT_STRING_LENGTH:
                    restrictionsValue = restrictionsValue
                            | CarUxRestrictions.UX_RESTRICTIONS_LIMIT_STRING_LENGTH;
                    break;
                case XML_UXR_NO_KEYBOARD:
                    restrictionsValue = restrictionsValue
                            | CarUxRestrictions.UX_RESTRICTIONS_NO_KEYBOARD;
                    break;
                case XML_UXR_NO_VIDEO:
                    restrictionsValue = restrictionsValue
                            | CarUxRestrictions.UX_RESTRICTIONS_NO_VIDEO;
                    break;
                case XML_UXR_LIMIT_CONTENT:
                    restrictionsValue = restrictionsValue
                            | CarUxRestrictions.UX_RESTRICTIONS_LIMIT_CONTENT;
                    break;
                case XML_UXR_NO_SETUP:
                    restrictionsValue = restrictionsValue
                            | CarUxRestrictions.UX_RESTRICTIONS_NO_SETUP;
                    break;
                case XML_UXR_NO_TEXT_MESSAGE:
                    restrictionsValue = restrictionsValue
                            | CarUxRestrictions.UX_RESTRICTIONS_NO_TEXT_MESSAGE;
                    break;
                case XML_UXR_NO_VOICE_TRANSCRIPTION:
                    restrictionsValue = restrictionsValue
                            | CarUxRestrictions.UX_RESTRICTIONS_NO_VOICE_TRANSCRIPTION;
                    break;
                case XML_UXR_FULLY_RESTRICTED:
                    restrictionsValue = restrictionsValue
                            | CarUxRestrictions.UX_RESTRICTIONS_FULLY_RESTRICTED;
                    break;
                default:
                    break;
            }
        }
        return restrictionsValue;
    }

    @Nullable
    private Builder.SpeedRange parseSpeedRange(float minSpeed, float maxSpeed) {
        if (Float.compare(minSpeed, 0) < 0 || Float.compare(maxSpeed, 0) < 0) {
            return null;
        }
        return new CarUxRestrictionsConfiguration.Builder.SpeedRange(minSpeed, maxSpeed);
    }

    private boolean traverseToTag(XmlResourceParser parser, String tag)
            throws IOException, XmlPullParserException {
        if (tag == null || parser == null) {
            return false;
        }
        int type;
        while ((type = parser.next()) != XmlResourceParser.END_DOCUMENT) {
            if (type == XmlResourceParser.START_TAG && parser.getName().equals(tag)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Parses the information in the global {@code RestrictionParameters} in a
     * {@code UxRestrictions} tag to read the parameters for the applicable UX restrictions.
     */
    private boolean parseGlobalRestrictionParameters(XmlResourceParser parser)
            throws IOException, XmlPullParserException {
        if (parser == null) {
            Slogf.e(TAG, "Parser is null");
            return false;
        }
        // The parser should be at the <RestrictionParameters> tag at this point.
        if (!XML_RESTRICTION_PARAMETERS.equals(parser.getName())) {
            Slogf.e(TAG, "Parser not at RestrictionParameters element: %s", parser.getName());
            return false;
        }
        parseRestrictionParameters(parser, null);
        return true;
    }

    private CarUxRestrictionsConfiguration.Builder getCurrentBuilder() {
        return mConfigBuilders.get(mConfigBuilders.size() - 1);
    }
}
