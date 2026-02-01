package com.gammaos.launchguardcontrol;

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

public class MainActivity extends AppCompatActivity {

    private static final String PROP_LAUNCH_GUARD_ENABLED = "persist.gammaos.launch.guard.enabled";
    private static final String PROP_LAUNCH_GUARD_CALLERS = "persist.gammaos.launch.guard.callers";
    private static final String PROP_LAUNCH_GUARD_TARGETS = "persist.gammaos.launch.guard.targets";

    private SwitchCompat switchLaunchGuard;
    private RecyclerView recyclerCallers;
    private RecyclerView recyclerTargets;

    private final List<AppEntry> callerEntries = new ArrayList<>();
    private final List<AppEntry> targetEntries = new ArrayList<>();

    private final Set<String> selectedCallers = new HashSet<>();
    private final Set<String> selectedTargets = new HashSet<>();

    private AppsAdapter callersAdapter;
    private AppsAdapter targetsAdapter;

    private boolean updatingSwitchProgrammatically = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        switchLaunchGuard = findViewById(R.id.switchLaunchGuard);
        recyclerCallers = findViewById(R.id.recyclerCallers);
        recyclerTargets = findViewById(R.id.recyclerTargets);

        // Load current global enable state
        boolean enabled = getSystemPropertyBool(PROP_LAUNCH_GUARD_ENABLED, false);
        updatingSwitchProgrammatically = true;
        switchLaunchGuard.setChecked(enabled);
        updatingSwitchProgrammatically = false;

        switchLaunchGuard.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (updatingSwitchProgrammatically) return;
            setSystemProperty(PROP_LAUNCH_GUARD_ENABLED, isChecked ? "1" : "0");
        });

        // Load caller/target lists from properties
        loadSelectedPackagesFromProperty(PROP_LAUNCH_GUARD_CALLERS, selectedCallers);
        loadSelectedPackagesFromProperty(PROP_LAUNCH_GUARD_TARGETS, selectedTargets);

        // Set up RecyclerViews
        recyclerCallers.setLayoutManager(new LinearLayoutManager(this));
        recyclerTargets.setLayoutManager(new LinearLayoutManager(this));

        callersAdapter = new AppsAdapter(callerEntries, (entry, isChecked) -> {
            onAppCheckedChanged(PROP_LAUNCH_GUARD_CALLERS, selectedCallers, entry, isChecked);
        });
        targetsAdapter = new AppsAdapter(targetEntries, (entry, isChecked) -> {
            onAppCheckedChanged(PROP_LAUNCH_GUARD_TARGETS, selectedTargets, entry, isChecked);
        });

        recyclerCallers.setAdapter(callersAdapter);
        recyclerTargets.setAdapter(targetsAdapter);

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
    // CSV package list handling
    // ---------------------------------------------------------------------

    private void loadSelectedPackagesFromProperty(String propKey, Set<String> outSet) {
        outSet.clear();
        String raw = getSystemProperty(propKey, "");
        if (TextUtils.isEmpty(raw)) {
            return;
        }
        String[] parts = raw.split(",");
        for (String p : parts) {
            if (p == null) continue;
            String pkg = p.trim();
            if (!pkg.isEmpty()) {
                outSet.add(pkg);
            }
        }
    }

    private void writeSelectedPackagesToProperty(String propKey, Set<String> set) {
        if (set.isEmpty()) {
            setSystemProperty(propKey, "");
            return;
        }
        List<String> list = new ArrayList<>(set);
        Collections.sort(list, String::compareToIgnoreCase);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(list.get(i));
        }
        setSystemProperty(propKey, sb.toString());
    }

    private void onAppCheckedChanged(String propKey, Set<String> backingSet,
            AppEntry entry, boolean isChecked) {
        if (isChecked) {
            backingSet.add(entry.packageName);
        } else {
            backingSet.remove(entry.packageName);
        }
        writeSelectedPackagesToProperty(propKey, backingSet);
    }

    // ---------------------------------------------------------------------
    // Package listing
    // ---------------------------------------------------------------------

    private void loadInstalledUserApps() {
        callerEntries.clear();
        targetEntries.clear();

        PackageManager pm = getPackageManager();
        List<ApplicationInfo> apps = pm.getInstalledApplications(0);
        if (apps == null) {
            callersAdapter.notifyDataSetChanged();
            targetsAdapter.notifyDataSetChanged();
            return;
        }

        final List<BasicAppInfo> base = new ArrayList<>();
        for (ApplicationInfo ai : apps) {
            if (isSystemApp(ai)) {
                continue;
            }
            String pkg = ai.packageName;
            CharSequence labelCs = ai.loadLabel(pm);
            String label = labelCs != null ? labelCs.toString() : pkg;
            base.add(new BasicAppInfo(label, pkg));
        }

        Collections.sort(base, (a, b) -> a.label.toLowerCase(Locale.ROOT)
                .compareTo(b.label.toLowerCase(Locale.ROOT)));

        for (BasicAppInfo info : base) {
            callerEntries.add(new AppEntry(info.label, info.packageName,
                    selectedCallers.contains(info.packageName)));
            targetEntries.add(new AppEntry(info.label, info.packageName,
                    selectedTargets.contains(info.packageName)));
        }

        callersAdapter.notifyDataSetChanged();
        targetsAdapter.notifyDataSetChanged();
    }

    private boolean isSystemApp(ApplicationInfo ai) {
        final int flags = ai.flags;
        return (flags & ApplicationInfo.FLAG_SYSTEM) != 0
                || (flags & ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0;
    }

    private static class BasicAppInfo {
        final String label;
        final String packageName;

        BasicAppInfo(String label, String packageName) {
            this.label = label;
            this.packageName = packageName;
        }
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

    private interface OnAppCheckedChangeListener {
        void onChanged(AppEntry entry, boolean isChecked);
    }

    private static class AppsAdapter extends RecyclerView.Adapter<AppsAdapter.ViewHolder> {

        private final List<AppEntry> data;
        private final OnAppCheckedChangeListener listener;

        AppsAdapter(List<AppEntry> data, OnAppCheckedChangeListener listener) {
            this.data = data;
            this.listener = listener;
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
                if (listener != null) {
                    listener.onChanged(entry, isChecked);
                }
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

        static class ViewHolder extends RecyclerView.ViewHolder {
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
