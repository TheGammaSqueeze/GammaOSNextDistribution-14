package com.gammaos.secondarydisplaycontrol;

import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

public class MainActivity extends AppCompatActivity {

    private static final String PROP_SECONDARY_ENABLED = "persist.gammaos.secondary_display.enabled";
    private static final String PROP_SECONDARY_PACKAGES = "persist.gammaos.secondary_display.packages";
 
    private static final int MAX_PROP_VALUE_LEN = 90;
    private static final Pattern PACKAGE_NAME_PATTERN =
            Pattern.compile("^[A-Za-z0-9_]+(\\.[A-Za-z0-9_]+)+$");

    private SwitchCompat switchSecondaryDisplay;
    private RecyclerView recyclerApps;

    private final List<AppEntry> appEntries = new ArrayList<>();
    private final Set<String> selectedPackages = new HashSet<>();

    private AppsAdapter adapter;

    private boolean updatingSwitchProgrammatically = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        switchSecondaryDisplay = findViewById(R.id.switchSecondaryDisplay);
        recyclerApps = findViewById(R.id.recyclerApps);

        // Load current global enable
        boolean enabled = getSystemPropertyBool(PROP_SECONDARY_ENABLED, false);
        updatingSwitchProgrammatically = true;
        switchSecondaryDisplay.setChecked(enabled);
        updatingSwitchProgrammatically = false;

        switchSecondaryDisplay.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (updatingSwitchProgrammatically) return;
            setSystemProperty(PROP_SECONDARY_ENABLED, isChecked ? "1" : "0");
        });

        // Load current package list from property
        loadSelectedPackagesFromProperty();

        // Set up app list
        recyclerApps.setLayoutManager(new LinearLayoutManager(this));
        adapter = new AppsAdapter(appEntries);
        recyclerApps.setAdapter(adapter);

        loadInstalledUserApps();
    }

    // ---------------------------------------------------------------------
    // System property helpers
    // ---------------------------------------------------------------------

    private String getSystemProperty(String key, String def) {
        try {
            Class<?> spClass = Class.forName("android.os.SystemProperties");
            Method getMethod = spClass.getMethod("get", String.class, String.class);
            return (String) getMethod.invoke(null, key, def);
        } catch (Exception e) {
            return def;
        }
    }

    private void setSystemProperty(String key, String value) {
        try {
            Class<?> spClass = Class.forName("android.os.SystemProperties");
            Method setMethod = spClass.getMethod("set", String.class, String.class);
            setMethod.invoke(null, key, value);
        } catch (Exception ignored) {
        }
    }

    private boolean getSystemPropertyBool(String key, boolean def) {
        String v = getSystemProperty(key, def ? "1" : "0");
        return "1".equals(v) || "true".equalsIgnoreCase(v);
    }

    // ---------------------------------------------------------------------
    // Secondary display package list handling
    // ---------------------------------------------------------------------

    private void loadSelectedPackagesFromProperty() {
        selectedPackages.clear();
        final String raw = getSystemPropertyMulti(PROP_SECONDARY_PACKAGES);
        if (TextUtils.isEmpty(raw)) {
            return;
        }
        // Tokens can be separated by commas and/or whitespace, matching the parsing logic
        // in frameworks.
        final String[] parts = raw.split("[,\\s]+");
        for (int i = 0; i < parts.length; i++) {
            final String pkg = sanitizePackageToken(parts[i]);
            if (pkg != null) {
                selectedPackages.add(pkg);
            }
        }
    }

    private void writeSelectedPackagesToProperty() {
        if (selectedPackages.isEmpty()) {
            writeSystemPropertySegments(PROP_SECONDARY_PACKAGES, Collections.emptyList());
            return;
        }
        final List<String> list = new ArrayList<>(selectedPackages.size());
        for (String pkg : selectedPackages) {
            final String sanitized = sanitizePackageToken(pkg);
            if (sanitized != null) {
                list.add(sanitized);
            }
        }
        Collections.sort(list, String::compareToIgnoreCase);

        final List<String> segments = splitTokensIntoPropSegments(list, MAX_PROP_VALUE_LEN);
        writeSystemPropertySegments(PROP_SECONDARY_PACKAGES, segments);
    }

    private void onAppCheckedChanged(AppEntry entry, boolean isChecked) {
        if (isChecked) {
            selectedPackages.add(entry.packageName);
        } else {
            selectedPackages.remove(entry.packageName);
        }
        writeSelectedPackagesToProperty();
    }
 
    private String getSystemPropertyMulti(String baseKey) {
        final StringBuilder out = new StringBuilder();
        appendPropSegment(out, getSystemProperty(baseKey, ""));
        for (int idx = 1; ; idx++) {
            final String seg = getSystemProperty(baseKey + "_" + idx, "");
            if (TextUtils.isEmpty(seg) || seg.trim().isEmpty()) {
                break;
            }
            appendPropSegment(out, seg);
        }
        return out.toString();
    }

    private void writeSystemPropertySegments(String baseKey, List<String> segments) {
        if (segments == null || segments.isEmpty()) {
            setSystemProperty(baseKey, "");
        } else {
            setSystemProperty(baseKey, segments.get(0));
        }

        // Write continuations: <prop>_1, <prop>_2, ...
        if (segments != null) {
            for (int idx = 1; idx < segments.size(); idx++) {
                setSystemProperty(baseKey + "_" + idx, segments.get(idx));
            }
        }

        // Clear any stale continuations from previous writes.
        for (int idx = (segments == null ? 1 : Math.max(1, segments.size())); ; idx++) {
            final String key = baseKey + "_" + idx;
            final String old = getSystemProperty(key, "");
            if (TextUtils.isEmpty(old) || old.trim().isEmpty()) {
                break;
            }
            setSystemProperty(key, "");
        }
    }

    private static void appendPropSegment(StringBuilder out, String segment) {
        if (segment == null) {
            return;
        }
        final String s = segment.trim();
        if (s.isEmpty()) {
            return;
        }
        if (out.length() > 0) {
            out.append(',');
        }
        out.append(s);
    }

    private static List<String> splitTokensIntoPropSegments(List<String> tokens, int maxLen) {
        final List<String> segments = new ArrayList<>();
        if (tokens == null || tokens.isEmpty()) {
            return segments;
        }

        final StringBuilder cur = new StringBuilder();
        for (int i = 0; i < tokens.size(); i++) {
            final String token = tokens.get(i);
            if (TextUtils.isEmpty(token)) {
                continue;
            }

            final int extra = (cur.length() == 0) ? token.length() : (1 + token.length());
            if (cur.length() > 0 && (cur.length() + extra) > maxLen) {
                segments.add(cur.toString());
                cur.setLength(0);
            }

            if (cur.length() > 0) {
                cur.append(',');
            }
            cur.append(token);
        }

        if (cur.length() > 0) {
            segments.add(cur.toString());
        }
        return segments;
    }

    private static String sanitizePackageToken(String raw) {
        if (raw == null) {
            return null;
        }
        final String s = raw.trim();
        if (s.isEmpty()) {
            return null;
        }
        if (!PACKAGE_NAME_PATTERN.matcher(s).matches()) {
            return null;
        }
        return s;
    }

    // ---------------------------------------------------------------------
    // Package listing
    // ---------------------------------------------------------------------

    private void loadInstalledUserApps() {
        appEntries.clear();

        PackageManager pm = getPackageManager();
        List<ApplicationInfo> apps = pm.getInstalledApplications(0);
        if (apps == null) {
            adapter.notifyDataSetChanged();
            return;
        }

        for (ApplicationInfo ai : apps) {
            if (isSystemApp(ai)) {
                continue;
            }

            String pkg = ai.packageName;
            CharSequence labelCs = ai.loadLabel(pm);
            String label = labelCs != null ? labelCs.toString() : pkg;

            boolean checked = selectedPackages.contains(pkg);
            appEntries.add(new AppEntry(label, pkg, checked));
        }

        Collections.sort(appEntries, (a, b) -> a.label.toLowerCase(Locale.ROOT)
                .compareTo(b.label.toLowerCase(Locale.ROOT)));

        adapter.notifyDataSetChanged();
    }

    private boolean isSystemApp(ApplicationInfo ai) {
        final int flags = ai.flags;
        return (flags & ApplicationInfo.FLAG_SYSTEM) != 0
                || (flags & ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0;
    }

    // ---------------------------------------------------------------------
    // Data model & adapter
    // ---------------------------------------------------------------------

    private static class AppEntry {
        final String label;
        final String packageName;
        boolean checked;

        AppEntry(String label, String packageName, boolean checked) {
            this.label = label;
            this.packageName = packageName;
            this.checked = checked;
        }
    }

    private class AppsAdapter extends RecyclerView.Adapter<AppsAdapter.ViewHolder> {

        private final List<AppEntry> data;

        AppsAdapter(List<AppEntry> data) {
            this.data = data;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_app_entry, parent, false);
            return new ViewHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            AppEntry entry = data.get(position);
            holder.textAppLabel.setText(entry.label);
            holder.textPackageName.setText(entry.packageName);

            holder.checkEnabled.setOnCheckedChangeListener(null);
            holder.checkEnabled.setChecked(entry.checked);
            holder.checkEnabled.setOnCheckedChangeListener((buttonView, isChecked) -> {
                entry.checked = isChecked;
                onAppCheckedChanged(entry, isChecked);
            });

            holder.itemView.setOnClickListener(v -> {
                boolean newChecked = !holder.checkEnabled.isChecked();
                holder.checkEnabled.setChecked(newChecked);
            });
        }

        @Override
        public int getItemCount() {
            return data.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            final TextView textAppLabel;
            final TextView textPackageName;
            final CheckBox checkEnabled;

            ViewHolder(@NonNull View itemView) {
                super(itemView);
                textAppLabel = itemView.findViewById(R.id.textAppLabel);
                textPackageName = itemView.findViewById(R.id.textPackageName);
                checkEnabled = itemView.findViewById(R.id.checkEnabled);
            }
        }
    }
}
