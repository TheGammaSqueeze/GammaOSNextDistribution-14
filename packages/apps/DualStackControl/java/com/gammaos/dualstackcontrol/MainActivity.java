package com.gammaos.dualstackcontrol;

import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.TextView;
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

public class MainActivity extends AppCompatActivity {

    private static final String PROP_DUALSTACK_ENABLED = "persist.gammaos.dualstack.enabled";
    private static final String PROP_DUALSTACK_PKGS = "persist.gammaos.dualstack.pkgs";
    private static final String PROP_SF_SURFACEVIEW_ONLY =
            "persist.gammaos.dualstack.sf.surfaceview_only";

    // Android system properties have a hard value length limit (typically ~90 chars usable).
    // To support large allowlists, DualStackControl transparently splits/reads the allowlist
    // across:
    //  - persist.gammaos.dualstack.pkgs
    //  - persist.gammaos.dualstack.pkgs_1, persist.gammaos.dualstack.pkgs_2, ...
    private static final int MAX_PKG_PROP_VALUE_LENGTH = 90;

    private SwitchCompat switchDualstack;
    private SwitchCompat switchSurfaceViewOnly;
    private RecyclerView recyclerApps;

    private final List<AppEntry> appEntries = new ArrayList<>();
    private final Set<String> selectedPackages = new HashSet<>();

    private AppsAdapter adapter;

    private boolean updatingSwitchProgrammatically = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        switchDualstack = findViewById(R.id.switchDualstack);
        switchSurfaceViewOnly = findViewById(R.id.switchSurfaceViewOnly);
        recyclerApps = findViewById(R.id.recyclerApps);

        // Load current state for toggles
        final boolean enabled = getSystemPropertyBool(PROP_DUALSTACK_ENABLED, false);
        final boolean surfaceViewOnly = getSystemPropertyBool(PROP_SF_SURFACEVIEW_ONLY, false);
        updatingSwitchProgrammatically = true;
        switchDualstack.setChecked(enabled);
        switchSurfaceViewOnly.setChecked(surfaceViewOnly);
        updatingSwitchProgrammatically = false;

        switchDualstack.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (updatingSwitchProgrammatically) return;
            setSystemProperty(PROP_DUALSTACK_ENABLED, isChecked ? "1" : "0");
        });
 
        switchSurfaceViewOnly.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (updatingSwitchProgrammatically) return;
            setSystemProperty(PROP_SF_SURFACEVIEW_ONLY, isChecked ? "1" : "0");
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
    // DualStack package list handling
    // ---------------------------------------------------------------------
 
    private static String pkgPropKey(int index) {
        return (index <= 0) ? PROP_DUALSTACK_PKGS : (PROP_DUALSTACK_PKGS + "_" + index);
    }

    private static boolean isValidAndroidPackageName(String pkg) {
        if (pkg == null) return false;

        final int n = pkg.length();
        if (n < 3) return false; // smallest plausible: "a.b"

        boolean hasDot = false;
        boolean segmentStart = true;

        for (int i = 0; i < n; i++) {
            final char c = pkg.charAt(i);
            if (c == '.') {
                if (segmentStart) return false;
                hasDot = true;
                segmentStart = true;
                continue;
            }

            final boolean isLetter =
                    (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z');
            final boolean isDigit = (c >= '0' && c <= '9');
            final boolean isUnderscore = (c == '_');

            if (!(isLetter || isDigit || isUnderscore)) {
                return false;
            }

            if (segmentStart) {
                // First character of each segment must be a letter.
                if (!isLetter) return false;
                segmentStart = false;
            }
        }

        if (segmentStart) return false; // trailing dot
        return hasDot;
    }

    private void addSelectedPackagesFromRaw(String raw) {
        if (TextUtils.isEmpty(raw)) return;

        String[] parts = raw.split(",");
        for (String p : parts) {
            if (p == null) continue;
            String pkg = p.trim();
            if (!pkg.isEmpty() && isValidAndroidPackageName(pkg)) {
                selectedPackages.add(pkg);
            }
        }
    }

    private void loadSelectedPackagesFromProperty() {
        selectedPackages.clear();

        // Base property.
        addSelectedPackagesFromRaw(getSystemProperty(PROP_DUALSTACK_PKGS, ""));

        // Continuation properties:
        //   persist.gammaos.dualstack.pkgs_1, persist.gammaos.dualstack.pkgs_2, ...
        for (int i = 1; ; i++) {
            String raw = getSystemProperty(pkgPropKey(i), "");
            if (TextUtils.isEmpty(raw)) {
                break;
            }
            addSelectedPackagesFromRaw(raw);
        }
    }

    private List<String> buildPkgPropChunks(List<String> sortedPackages) {
        final List<String> chunks = new ArrayList<>();
        final StringBuilder sb = new StringBuilder();

        for (String pkg : sortedPackages) {
            if (TextUtils.isEmpty(pkg)) continue;
            final String p = pkg.trim();
            if (p.isEmpty() || !isValidAndroidPackageName(p)) continue;

            if (p.length() > MAX_PKG_PROP_VALUE_LENGTH) {
                // Too long to ever fit in a property value. Skip.
                continue;
            }

            if (sb.length() == 0) {
                sb.append(p);
                continue;
            }

            final int projectedLen = sb.length() + 1 + p.length();
            if (projectedLen <= MAX_PKG_PROP_VALUE_LENGTH) {
                sb.append(',').append(p);
            } else {
                chunks.add(sb.toString());
                sb.setLength(0);
                sb.append(p);
            }
        }

        if (sb.length() > 0) {
            chunks.add(sb.toString());
        }
        return chunks;
    }

    private void clearDualStackPkgPropsFrom(int startIndex) {
        // startIndex refers to the continuation index (>= 1). Index 0 is the base property.
        int i = Math.max(1, startIndex);
        while (true) {
            final String key = pkgPropKey(i);
            final String existing = getSystemProperty(key, "");
            if (TextUtils.isEmpty(existing)) {
                break;
            }
            setSystemProperty(key, "");
            i++;
        }
    }

    private void writeSelectedPackagesToProperty() {
        // Sanitize + sort deterministically.
        List<String> list = new ArrayList<>();
        for (String p : selectedPackages) {
            if (p == null) continue;
            final String pkg = p.trim();
            if (!pkg.isEmpty() && isValidAndroidPackageName(pkg)) {
                list.add(pkg);
            }
        }

        if (list.isEmpty()) {
            setSystemProperty(PROP_DUALSTACK_PKGS, "");
            clearDualStackPkgPropsFrom(1 /* startIndex */);
            return;
        }

        Collections.sort(list, String::compareToIgnoreCase);
        final List<String> chunks = buildPkgPropChunks(list);

        if (chunks.isEmpty()) {
            // Everything got filtered out (e.g. all tokens too long).
            setSystemProperty(PROP_DUALSTACK_PKGS, "");
            clearDualStackPkgPropsFrom(1 /* startIndex */);
            return;
        }
        // Write base + continuation properties.
        setSystemProperty(PROP_DUALSTACK_PKGS, chunks.get(0));
        for (int i = 1; i < chunks.size(); i++) {
            setSystemProperty(pkgPropKey(i), chunks.get(i));
        }

        // Clear any stale continuation properties from previous larger allowlists.
        clearDualStackPkgPropsFrom(chunks.size() /* startIndex */);
    }

    private void onAppCheckedChanged(AppEntry entry, boolean isChecked) {
        if (isChecked) {
            selectedPackages.add(entry.packageName);
        } else {
            selectedPackages.remove(entry.packageName);
        }
        writeSelectedPackagesToProperty();
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
