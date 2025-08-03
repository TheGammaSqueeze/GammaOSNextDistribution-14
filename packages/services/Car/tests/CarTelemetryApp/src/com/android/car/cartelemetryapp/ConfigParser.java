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

package com.android.car.cartelemetryapp;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.car.telemetry.TelemetryProto.CarTelemetrydPublisher;
import android.car.telemetry.TelemetryProto.ConnectivityPublisher;
import android.car.telemetry.TelemetryProto.ConnectivityPublisher.OemType;
import android.car.telemetry.TelemetryProto.ConnectivityPublisher.Transport;
import android.car.telemetry.TelemetryProto.MetricsConfig;
import android.car.telemetry.TelemetryProto.Publisher;
import android.car.telemetry.TelemetryProto.StatsPublisher;
import android.car.telemetry.TelemetryProto.StatsPublisher.SystemMetric;
import android.car.telemetry.TelemetryProto.Subscriber;
import android.car.telemetry.TelemetryProto.VehiclePropertyPublisher;
import android.content.Context;
import android.content.res.Resources.NotFoundException;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Utility class for parsing metrics configs from xml files. */
public class ConfigParser {
    private static final String TAG = ConfigParser.class.getSimpleName();
    private static final int PRIORITY_LOW = 100;

    private static final String CONFIG_TAG = "config";
    private static final String CONFIG_NAME_ATTR = "name";
    private static final String CONFIG_VERSION_ATTR = "version";
    private static final String CONFIG_SCRIPT_NAME_ATTR = "script_name";
    private static final String SUBSCRIBERS_TAG = "subscribers";
    private static final String SUBSCRIBER_TAG = "subscriber";
    private static final String SUBSCRIBER_HANDLER_ATTR = "handler";
    private static final String SUBSCRIBER_PRIORITY_ATTR = "priority";
    private static final String PUBLISHER_TAG = "publisher";
    private static final String PUBLISHER_TYPE_ATTR = "type";
    private static final String VEHICLE_PROP_PUB_PROP_ID_TAG = "vehicle_property_id";
    private static final String VEHICLE_PROP_PUB_READ_RATE_TAG = "read_rate";
    private static final String CAR_TELEMETRYD_PUB_ID_TAG = "id";
    private static final String STATS_PUB_SYSTEM_METRIC_TAG = "system_metric";
    private static final String CONNECTIVITY_PUB_TRANSPORT_TAG = "transport";
    private static final String CONNECTIVITY_PUB_OEM_TYPE_TAG = "oem_type";
    private static final String PUBLISHER_TYPE_VEHICLE_PROPERTY = "vehicle_property";
    private static final String PUBLISHER_TYPE_CARTELEMETRYD = "cartelemetryd";
    private static final String PUBLISHER_TYPE_STATS = "stats";
    private static final String PUBLISHER_TYPE_CONNECTIVITY = "connectivity";

    private Context mContext;
    private StringBuilder mLogBuilder = new StringBuilder();

    ConfigParser(Context context) {
        mContext = context;
    }

    public String dumpLogs() {
        String logs = mLogBuilder.toString();
        mLogBuilder.setLength(0);
        return logs;
    }

    /**
     * Parses XML config from res/xml/configs.xml to map of metrics config names to
     * {@link MetricsConfig}.
     *
     * @return metrics config map from name to {@link MetricsConfig}.
     */
    @NonNull
    public Map<String, MetricsConfig> getConfigs() {
        return getConfigs(mContext.getResources().getXml(R.xml.configs));
    }

    /**
    * Parses XML config to map of metrics config names to {@link MetricsConfig}.
    *
    * @param parser parser to read from.
    * @return metrics config map from name to {@link MetricsConfig}.
    */
    @NonNull
    Map<String, MetricsConfig> getConfigs(@NonNull XmlPullParser parser) {
        Map<String, MetricsConfig> configs = new HashMap<>();
        try {
            int eventType = parser.getEventType();
            while (eventType != XmlPullParser.END_DOCUMENT) {
                if (eventType == XmlPullParser.START_TAG && Objects.equals(parser.getName(),
                        CONFIG_TAG)) {
                    MetricsConfig config = parseMetricsConfig(parser);
                    if (config != null) {
                        configs.put(config.getName(), config);
                    }
                }
                eventType = parser.next();
            }
        } catch (XmlPullParserException | IOException e) {
            log("Error parsing XML file! " + parser.getPositionDescription()
                    + " Error: " + e.getMessage());
        }
        log("Finished parsing configs: " + configs.size());
        return configs;
    }

    /** Parses a metrics config. Parser must start at config start tag. */
    @Nullable
    private MetricsConfig parseMetricsConfig(@NonNull XmlPullParser parser)
            throws XmlPullParserException, IOException {
        MetricsConfig.Builder config = MetricsConfig.newBuilder();
        String name = parser.getAttributeValue(null, CONFIG_NAME_ATTR);
        if (name == null || name.isEmpty()) {
            log("Config name cannot be empty at " + parser.getPositionDescription());
            return null;
        }
        config.setName(name);
        String version = parser.getAttributeValue(null, CONFIG_VERSION_ATTR);
        if (version == null || version.isEmpty()) {
            log("Config " + name + " version cannot be empty at "
                    + parser.getPositionDescription());
            return null;
        }
        try {
            config.setVersion(Integer.parseInt(version));
        } catch (NumberFormatException e) {
            log("Config " + name + " version is not an integer "
                    + parser.getPositionDescription());
            return null;
        }
        String scriptName = parser.getAttributeValue(null, CONFIG_SCRIPT_NAME_ATTR);
        if (scriptName == null || scriptName.isEmpty()) {
            log("Config " + name + " script name cannot be empty at "
                    + parser.getPositionDescription());
            return null;
        }
        int resId = mContext.getResources().getIdentifier(
                scriptName, "raw", mContext.getPackageName());
        String script = readScript(resId);
        if (script == null) {
            log("Could not read script " + scriptName + " at "
                    + parser.getPositionDescription());
            return null;
        }
        config.setScript(script);

        int eventType = parser.next();
        List<Subscriber> subscribers = null;
        while (eventType != XmlPullParser.END_TAG || !Objects.equals(parser.getName(),
                CONFIG_TAG)) {
            if (eventType == XmlPullParser.START_TAG
                    && Objects.equals(parser.getName(), SUBSCRIBERS_TAG)) {
                subscribers = parseSubscribers(parser);
                for (Subscriber sub : subscribers) {
                    config.addSubscribers(sub);
                }
            }
            eventType = parser.next();
        }
        if (subscribers == null || subscribers.size() == 0) {
            log("There must be at least one valid subscriber for config "
                    + name + " at " + parser.getPositionDescription());
            return null;
        }
        return config.build();
    }

    /** Parses subscribers. Parser must start at subscribers start tag. */
    @Nullable
    private List<Subscriber> parseSubscribers(@NonNull XmlPullParser parser)
            throws XmlPullParserException, IOException {
        List<Subscriber> subscribers = new ArrayList<>();
        int eventType = parser.next();
        while (eventType != XmlPullParser.END_TAG
                || !Objects.equals(parser.getName(), SUBSCRIBERS_TAG)) {
            if (eventType == XmlPullParser.START_TAG
                    && Objects.equals(parser.getName(), SUBSCRIBER_TAG)) {
                Subscriber sub = parseSubscriber(parser);
                if (sub != null) {
                    subscribers.add(sub);
                }
            }
            eventType = parser.next();
        }
        return subscribers;
    }

    /** Parses a subscriber. Parser must start at subscriber start tag. */
    @Nullable
    private Subscriber parseSubscriber(@NonNull XmlPullParser parser)
            throws XmlPullParserException, IOException {
        Subscriber.Builder sub = Subscriber.newBuilder();
        String handler = parser.getAttributeValue(null, SUBSCRIBER_HANDLER_ATTR);
        if (handler == null || handler.isEmpty()) {
            log("Handler for subscriber cannot be empty at "
                    + parser.getPositionDescription());
            return null;
        }
        sub.setHandler(handler);
        String priority = parser.getAttributeValue(null, SUBSCRIBER_PRIORITY_ATTR);
        if (priority == null || priority.isEmpty()) {
            log("Priority for subscriber cannot be empty at "
                    + parser.getPositionDescription());
            return null;
        }
        try {
            sub.setPriority(Integer.parseInt(priority));
        } catch (NumberFormatException e) {
            sub.setPriority(PRIORITY_LOW);
        }

        int eventType = parser.next();
        Publisher publisher = null;
        while (eventType != XmlPullParser.END_TAG
                || !Objects.equals(parser.getName(), SUBSCRIBER_TAG)) {
            if (eventType == XmlPullParser.START_TAG
                    && Objects.equals(parser.getName(), PUBLISHER_TAG)) {
                publisher = parsePublisher(parser);
            }
            eventType = parser.next();
        }
        if (publisher == null) {
            log("Subscriber with handler " + handler + " must have a valid publisher at "
                    + parser.getPositionDescription());
            return null;
        }
        sub.setPublisher(publisher);
        return sub.build();
    }

    /** Parses a publisher. Parser must start at publisher start tag. */
    @Nullable
    private Publisher parsePublisher(@NonNull XmlPullParser parser)
            throws XmlPullParserException, IOException {
        Publisher.Builder pub = Publisher.newBuilder();
        String type = parser.getAttributeValue(null, PUBLISHER_TYPE_ATTR);
        if (type == null || type.isEmpty()) {
            log("Publisher type cannot be empty at "
                    + parser.getPositionDescription());
            return null;
        }

        if (Objects.equals(type, PUBLISHER_TYPE_VEHICLE_PROPERTY)) {
            VehiclePropertyPublisher subPub = parseVehiclePropertyPublisher(parser);
            if (subPub == null) return null;
            pub.setVehicleProperty(subPub);
        } else if (Objects.equals(type, PUBLISHER_TYPE_CARTELEMETRYD)) {
            CarTelemetrydPublisher subPub = parseCarTelemetrydPublisher(parser);
            if (subPub == null) return null;
            pub.setCartelemetryd(subPub);
        } else if (Objects.equals(type, PUBLISHER_TYPE_STATS)) {
            StatsPublisher subPub = parseStatsPublisher(parser);
            if (subPub == null) return null;
            pub.setStats(subPub);
        } else if (Objects.equals(type, PUBLISHER_TYPE_CONNECTIVITY)) {
            ConnectivityPublisher subPub = parseConnectivityPublisher(parser);
            if (subPub == null) return null;
            pub.setConnectivity(subPub);
        } else {
            log("Publisher type does not match any existing type at "
                    + parser.getPositionDescription());
            return null;
        }
        return pub.build();
    }

    /** Parses a VehiclePropertyPublisher. Parser must start at publisher start tag. */
    @Nullable
    private VehiclePropertyPublisher parseVehiclePropertyPublisher(@NonNull XmlPullParser parser)
            throws XmlPullParserException, IOException {
        VehiclePropertyPublisher.Builder pub = VehiclePropertyPublisher.newBuilder();
        int eventType = parser.next();
        while (eventType != XmlPullParser.END_TAG
                || !Objects.equals(parser.getName(), PUBLISHER_TAG)) {
            if (eventType == XmlPullParser.START_TAG
                    && Objects.equals(parser.getName(), VEHICLE_PROP_PUB_PROP_ID_TAG)) {
                parser.next();
                String vehiclePropertyId = parser.getText();
                try {
                    pub.setVehiclePropertyId(Integer.parseInt(vehiclePropertyId));
                } catch (NumberFormatException e) {
                    log("Property id not valid integer for VehiclePropertyPublisher at "
                            + parser.getPositionDescription());
                    return null;
                }
            } else if (eventType == XmlPullParser.START_TAG
                    && Objects.equals(parser.getName(), VEHICLE_PROP_PUB_READ_RATE_TAG)) {
                parser.next();
                String readRate = parser.getText();
                try {
                    pub.setReadRate(Float.parseFloat(readRate));
                } catch (NumberFormatException | NullPointerException e) {
                    log("Read rate not valid float for VehiclePropertyPublisher at "
                            + parser.getPositionDescription());
                    return null;
                }
            }
            eventType = parser.next();
        }
        return pub.build();
    }

    /** Parses a CarTelemetrydPublisher. Parser must start at publisher start tag. */
    @Nullable
    private CarTelemetrydPublisher parseCarTelemetrydPublisher(@NonNull XmlPullParser parser)
            throws XmlPullParserException, IOException {
        CarTelemetrydPublisher.Builder pub = CarTelemetrydPublisher.newBuilder();
        int eventType = parser.next();
        while (eventType != XmlPullParser.END_TAG
                || !Objects.equals(parser.getName(), PUBLISHER_TAG)) {
            if (eventType == XmlPullParser.START_TAG
                    && Objects.equals(parser.getName(), CAR_TELEMETRYD_PUB_ID_TAG)) {
                parser.next();
                String id = parser.getText();
                try {
                    pub.setId(Integer.parseInt(id));
                } catch (NumberFormatException e) {
                    log("Id is not a valid integer for CarTelemetrydPublisher config at "
                            + parser.getPositionDescription());
                    return null;
                }
            }
            eventType = parser.next();
        }
        return pub.build();
    }

    /** Parses a StatsPublisher. Parser must start at publisher start tag. */
    @Nullable
    private StatsPublisher parseStatsPublisher(@NonNull XmlPullParser parser)
            throws XmlPullParserException, IOException {
        StatsPublisher.Builder pub = StatsPublisher.newBuilder();
        int eventType = parser.next();
        while (eventType != XmlPullParser.END_TAG
                || !Objects.equals(parser.getName(), PUBLISHER_TAG)) {
            if (eventType == XmlPullParser.START_TAG
                    && Objects.equals(parser.getName(), STATS_PUB_SYSTEM_METRIC_TAG)) {
                parser.next();
                String systemMetric = parser.getText();
                try {
                    pub.setSystemMetric(SystemMetric.valueOf(systemMetric));
                } catch (IllegalArgumentException | NullPointerException e) {
                    log("System metric type for StatsPublisher config malformed at "
                            + parser.getPositionDescription() + " Error: " + e.getMessage());
                    return null;
                }
            }
            eventType = parser.next();
        }
        return pub.build();
    }

    /** Parses a ConnectivityPublisher. Parser must start at publisher start tag. */
    @Nullable
    private ConnectivityPublisher parseConnectivityPublisher(@NonNull XmlPullParser parser)
            throws XmlPullParserException, IOException {
        ConnectivityPublisher.Builder pub = ConnectivityPublisher.newBuilder();
        int eventType = parser.next();
        while (eventType != XmlPullParser.END_TAG
                || !Objects.equals(parser.getName(), PUBLISHER_TAG)) {
            if (eventType == XmlPullParser.START_TAG
                    && Objects.equals(parser.getName(), CONNECTIVITY_PUB_TRANSPORT_TAG)) {
                parser.next();
                String transport = parser.getText();
                try {
                    pub.setTransport(Transport.valueOf(transport));
                } catch (IllegalArgumentException | NullPointerException e) {
                    log("Transport for StatsPublisher malformed " + e.getMessage());
                    return null;
                }
            } else if (eventType == XmlPullParser.START_TAG
                    && Objects.equals(parser.getName(), CONNECTIVITY_PUB_OEM_TYPE_TAG)) {
                parser.next();
                String oemType = parser.getText();
                try {
                    pub.setOemType(OemType.valueOf(oemType));
                } catch (IllegalArgumentException | NullPointerException e) {
                    log("OemType for StatsPublisher malformed! " + e.getMessage());
                    return null;
                }
            }
            eventType = parser.next();
        }
        return pub.build();
    }

    /** Read the lua script with particular resource id in res/raw into string. */
    @Nullable
    private String readScript(int resId) {
        try (InputStream is = mContext.getResources().openRawResource(resId)) {
            byte[] bytes = new byte[is.available()];
            is.read(bytes);
            return new String(bytes);
        } catch (IOException | NotFoundException e) {
            log("Error while reading script. " + e.getMessage());
            return null;
        }
    }

    private void log(String str) {
        mLogBuilder.append(LocalDateTime.now(ZoneId.systemDefault())
                    .format(DateTimeFormatter.ofPattern("HH:mm:ss.SSS")) + ": ");
        mLogBuilder.append(str);
    }
}
