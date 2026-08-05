package com.gammaos.secondaryhome;

import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Minimal launcher shown on the secondary display in desktop/TV mode. Lists every launchable
 * activity as an icon + label grid, fully d-pad navigable, and launches the chosen app on this
 * (the secondary) display. Intentionally tiny: no widgets, no workspace, no persistent services.
 */
public class SecondaryHomeActivity extends AppCompatActivity {

    private RecyclerView mGrid;
    private final List<AppEntry> mApps = new ArrayList<>();
    private AppsAdapter mAdapter;
    private final Handler mMain = new Handler(Looper.getMainLooper());
    private volatile boolean mLoading = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);

        mGrid = findViewById(R.id.appGrid);
        mGrid.setLayoutManager(new GridLayoutManager(this, computeSpanCount()));
        mGrid.setHasFixedSize(true);
        mAdapter = new AppsAdapter();
        mGrid.setAdapter(mAdapter);

        loadApps();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Refresh in case apps were installed/removed while we were backgrounded.
        loadApps();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        // singleTask home: a re-launch just re-focuses the grid.
        focusFirst();
    }

    private int computeSpanCount() {
        final DisplayMetrics dm = getResources().getDisplayMetrics();
        // Aim for roughly 108dp cells; clamp so tiny second screens still get a couple of columns.
        final float cellDp = 108f;
        final float widthDp = dm.widthPixels / dm.density;
        int span = (int) (widthDp / cellDp);
        if (span < 2) span = 2;
        if (span > 6) span = 6;
        return span;
    }

    private void loadApps() {
        if (mLoading) return;
        mLoading = true;
        new Thread(() -> {
            final List<AppEntry> loaded = queryLaunchers();
            mMain.post(() -> {
                mApps.clear();
                mApps.addAll(loaded);
                mAdapter.notifyDataSetChanged();
                focusFirst();
                mLoading = false;
            });
        }, "SecondaryHome-load").start();
    }

    private List<AppEntry> queryLaunchers() {
        final List<AppEntry> out = new ArrayList<>();
        final PackageManager pm = getPackageManager();
        final Intent main = new Intent(Intent.ACTION_MAIN);
        main.addCategory(Intent.CATEGORY_LAUNCHER);
        final List<ResolveInfo> ris = pm.queryIntentActivities(main, 0);
        if (ris == null) return out;
        final String self = getPackageName();
        for (ResolveInfo ri : ris) {
            if (ri.activityInfo == null) continue;
            final String pkg = ri.activityInfo.packageName;
            if (self.equals(pkg)) continue;
            final String act = ri.activityInfo.name;
            CharSequence lc = ri.loadLabel(pm);
            final String label = lc != null ? lc.toString() : pkg;
            Drawable icon;
            try {
                icon = ri.loadIcon(pm);
            } catch (Throwable t) {
                icon = null;
            }
            out.add(new AppEntry(label, pkg, act, icon));
        }
        Collections.sort(out, (a, b) -> a.label.toLowerCase(Locale.ROOT)
                .compareTo(b.label.toLowerCase(Locale.ROOT)));
        return out;
    }

    private void focusFirst() {
        mGrid.post(() -> {
            final RecyclerView.ViewHolder vh = mGrid.findViewHolderForAdapterPosition(0);
            if (vh != null && vh.itemView != null) {
                vh.itemView.requestFocus();
            } else {
                mGrid.requestFocus();
            }
        });
    }

    private void launch(AppEntry entry) {
        try {
            final Intent i = new Intent(Intent.ACTION_MAIN);
            i.addCategory(Intent.CATEGORY_LAUNCHER);
            i.setComponent(new ComponentName(entry.pkg, entry.activity));
            i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                    | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
            // startActivity from this activity's context keeps the launch on our display
            // (the secondary display), which is exactly what a secondary home wants.
            startActivity(i);
        } catch (Throwable t) {
            Toast.makeText(this, getString(R.string.launch_failed, entry.label),
                    Toast.LENGTH_SHORT).show();
        }
    }

    private static final class AppEntry {
        final String label;
        final String pkg;
        final String activity;
        final Drawable icon;

        AppEntry(String label, String pkg, String activity, Drawable icon) {
            this.label = label;
            this.pkg = pkg;
            this.activity = activity;
            this.icon = icon;
        }
    }

    private final class AppsAdapter extends RecyclerView.Adapter<AppsAdapter.VH> {
        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            final View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_home_app, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH holder, int position) {
            final AppEntry e = mApps.get(position);
            holder.label.setText(e.label);
            if (e.icon != null) {
                holder.icon.setImageDrawable(e.icon);
            } else {
                holder.icon.setImageResource(android.R.drawable.sym_def_app_icon);
            }
            holder.itemView.setOnClickListener(v -> launch(e));
        }

        @Override
        public int getItemCount() {
            return mApps.size();
        }

        final class VH extends RecyclerView.ViewHolder {
            final ImageView icon;
            final TextView label;

            VH(@NonNull View itemView) {
                super(itemView);
                icon = itemView.findViewById(R.id.appIcon);
                label = itemView.findViewById(R.id.appLabel);
            }
        }
    }
}
