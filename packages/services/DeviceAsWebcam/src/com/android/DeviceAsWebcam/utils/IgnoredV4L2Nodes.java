package com.android.DeviceAsWebcam.utils;

import android.content.Context;
import android.util.JsonReader;
import android.util.Log;

import com.android.DeviceAsWebcam.R;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

public class IgnoredV4L2Nodes {
    private static final String TAG = IgnoredV4L2Nodes.class.getSimpleName();
    private static final String V4L2_NODE_PREFIX = "/dev/video";

    private static String[] kIgnoredNodes = null;

    public synchronized static String[] getIgnoredNodes(Context context) {
        if (kIgnoredNodes != null) {
            return kIgnoredNodes;
        }

        List<String> ignoredNodes = new ArrayList<>();
        try(InputStream in = context.getResources().openRawResource(R.raw.ignored_v4l2_nodes);
            JsonReader jsonReader = new JsonReader(new InputStreamReader(in))) {
            jsonReader.beginArray();
            while (jsonReader.hasNext()) {
                String node = jsonReader.nextString();
                // Don't track nodes that won't be in our search anyway.
                if (node.startsWith(V4L2_NODE_PREFIX)) {
                    ignoredNodes.add(node);
                }
            }
            jsonReader.endArray();
        } catch (IOException e) {
            Log.e(TAG, "Failed to parse JSON. Running with a partial ignored list", e);
        }

        kIgnoredNodes = ignoredNodes.toArray(new String[0]);
        return kIgnoredNodes;
    }
}
