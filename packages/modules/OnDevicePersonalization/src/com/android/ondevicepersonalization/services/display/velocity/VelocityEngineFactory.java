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

package com.android.ondevicepersonalization.services.display.velocity;

import android.annotation.NonNull;
import android.content.Context;
import android.os.PersistableBundle;

import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.VelocityEngine;
import org.apache.velocity.app.event.EventCartridge;
import org.apache.velocity.app.event.ReferenceInsertionEventHandler;

import java.util.Properties;

/**
 * VelocityEngine factory.
 */
public class VelocityEngineFactory {
    private static VelocityEngine sSingleton;

    private static final int MAX_FOREACH = 100;

    private VelocityEngineFactory() {
    }

    /**
     * Returns a singleton of VelocityEngine.
     */
    @NonNull
    public static synchronized VelocityEngine getVelocityEngine(Context context) {
        synchronized (VelocityEngineFactory.class) {
            if (sSingleton == null) {
                sSingleton = new VelocityEngine();
                Properties props = getProperties(context);
                sSingleton.init(props);
            }
            return sSingleton;
        }
    }

    /**
     * Create a Velocity context populating it with the given bundle.
     */
    @NonNull
    public static org.apache.velocity.context.Context createVelocityContext(
            PersistableBundle bundle) {
        VelocityContext ctx = new VelocityContext();
        OnDevicePersonalizationVelocityTool tool = new OnDevicePersonalizationVelocityTool();
        // TODO(b/263180569): Determine what name to provide this as.
        ctx.put("tool", tool);
        EventCartridge eventCartridge = new EventCartridge();
        eventCartridge.attachToContext(ctx);
        eventCartridge.addReferenceInsertionEventHandler(new ReferenceInsertionEventHandler() {
            @Override
            public Object referenceInsert(org.apache.velocity.context.Context context,
                    String reference, Object value) {
                // TODO(b/263180569): Implement default encoding behavior.
                return value;
            }
        });

        for (String key : bundle.keySet()) {
            ctx.put(key, bundle.get(key));
        }
        return ctx;
    }

    @NonNull
    private static Properties getProperties(Context context) {
        Properties props = new Properties();
        // Set default template path to cache dir.
        props.put("file.resource.loader.path", context.getCacheDir().getAbsolutePath());

        // Set max parse depth to 1. Templates cannot use other template files.
        props.put("directive.parse.max.depth", 1);

        // TODO(b/262001121): Set to SecureUberspector for now. Consider using custom uberspector.
        props.put("introspector.uberspect.class",
                "org.apache.velocity.util.introspection.SecureUberspector");

        // Limit the maximum allowed number of loops for a #foreach() statement.
        props.put("directive.foreach.max_loops", MAX_FOREACH);

        // Default input encoding for templates.
        props.put("resource.default_encoding", "UTF-8");

        // Do not allow defining global macros.
        props.put("velocimacro.permissions.allow.inline.local.scope", true);

        return props;
    }
}
