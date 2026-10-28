package com.teinzz.teinlite;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.BaseAdapter;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public final class MainActivity extends Activity {
    private final ArrayList<App> apps = new ArrayList<>(), home = new ArrayList<>();
    private final HashSet<String> picked = new HashSet<>();
    private GridView homeGrid, drawerGrid;
    private AppsAdapter homeAdapter, drawerAdapter;
    private boolean drawer;

    private int dp(float n) { return (int)(n * getResources().getDisplayMetrics().density + .5f); }

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        Window w = getWindow();
        w.setStatusBarColor(Color.BLACK);
        w.setNavigationBarColor(Color.BLACK);
        Set<String> old = getSharedPreferences("home", 0).getStringSet("apps", null);
        if (old != null) picked.addAll(old);
        showHome();
        loadApps();
    }

    private void loadApps() {
        new Thread(() -> {
            ArrayList<App> result = new ArrayList<>();
            PackageManager pm = getPackageManager();
            Intent query = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
            for (ResolveInfo r : pm.queryIntentActivities(query, 0)) {
                if (getPackageName().equals(r.activityInfo.packageName)) continue;
                try {
                    ComponentName c = new ComponentName(r.activityInfo.packageName, r.activityInfo.name);
                    CharSequence label = r.loadLabel(pm);
                    result.add(new App(c.flattenToString(), label == null ? c.getPackageName() : label.toString(),
                            new Intent(query).setComponent(c), r.loadIcon(pm)));
                } catch (RuntimeException ignored) { }
            }
            Collections.sort(result, (a, b) -> a.label.compareToIgnoreCase(b.label));
            runOnUiThread(() -> {
                apps.addAll(result);
                for (App a : apps) if (picked.contains(a.key)) home.add(a);
                if (homeAdapter != null) homeAdapter.notifyDataSetChanged();
                if (drawerAdapter != null) drawerAdapter.notifyDataSetChanged();
            });
        }, "tl-apps").start();
    }

    private GridView grid(boolean homeScreen) {
        GridView g = new GridView(this) {
            float down;
            @Override public boolean onInterceptTouchEvent(MotionEvent e) {
                if (e.getAction() == MotionEvent.ACTION_DOWN) down = e.getY();
                if (e.getAction() == MotionEvent.ACTION_MOVE) {
                    float dy = e.getY() - down;
                    if (homeScreen && (home.isEmpty() || down > getHeight() * .72f) && dy < -dp(48)) {
                        showDrawer();
                        return true;
                    }
                    if (!homeScreen && dy > dp(48)) {
                        showHome();
                        return true;
                    }
                }
                return super.onInterceptTouchEvent(e);
            }
        };
        g.setNumColumns(4);
        g.setColumnWidth(dp(68));
        g.setStretchMode(GridView.STRETCH_COLUMN_WIDTH);
        g.setVerticalSpacing(dp(8));
        g.setPadding(dp(5), dp(10), dp(5), dp(10));
        g.setClipToPadding(false);
        g.setBackgroundColor(Color.BLACK);
        g.setCacheColorHint(Color.BLACK);
        return g;
    }

    private void showHome() {
        drawer = false;
        homeGrid = grid(true);
        homeAdapter = new AppsAdapter(home);
        homeGrid.setAdapter(homeAdapter);
        homeGrid.setOnItemClickListener((p, v, pos, id) -> launch(home.get(pos)));
        homeGrid.setOnItemLongClickListener((p, v, pos, id) -> {
            App a = home.remove(pos);
            picked.remove(a.key);
            save();
            homeAdapter.notifyDataSetChanged();
            return true;
        });
        homeGrid.setOnLongClickListener(v -> {
            if (!home.isEmpty()) return false;
            showDrawer();
            return true;
        });
        homeGrid.setLongClickable(true);
        setContentView(homeGrid);
    }

    private void showDrawer() {
        drawer = true;
        drawerGrid = grid(false);
        drawerAdapter = new AppsAdapter(apps);
        drawerGrid.setAdapter(drawerAdapter);
        drawerGrid.setOnItemClickListener((p, v, pos, id) -> launch(apps.get(pos)));
        drawerGrid.setOnItemLongClickListener((p, v, pos, id) -> {
            App a = apps.get(pos);
            if (picked.add(a.key)) {
                home.add(a);
                Collections.sort(home, (x, y) -> x.label.compareToIgnoreCase(y.label));
                save();
                if (homeAdapter != null) homeAdapter.notifyDataSetChanged();
            }
            return true;
        });
        setContentView(drawerGrid);
    }

    private void save() {
        getSharedPreferences("home", 0).edit().putStringSet("apps", new HashSet<>(picked)).apply();
    }

    private void launch(App a) {
        showHome();
        try { startActivity(a.intent); } catch (RuntimeException ignored) { }
    }

    @Override public void onBackPressed() {
        if (drawer) showHome(); else super.onBackPressed();
    }

    private final class AppsAdapter extends BaseAdapter {
        private final ArrayList<App> data;
        AppsAdapter(ArrayList<App> data) { this.data = data; }
        public int getCount() { return data.size(); }
        public Object getItem(int p) { return data.get(p); }
        public long getItemId(int p) { return p; }
        public View getView(int p, View old, ViewGroup parent) {
            Tile t;
            if (old != null && old.getTag() instanceof Tile) t = (Tile) old.getTag();
            else {
                LinearLayout cell = new LinearLayout(MainActivity.this);
                cell.setOrientation(LinearLayout.VERTICAL);
                cell.setGravity(Gravity.CENTER);
                ImageView icon = new ImageView(MainActivity.this);
                icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
                cell.addView(icon, new LinearLayout.LayoutParams(dp(40), dp(40)));
                TextView label = new TextView(MainActivity.this);
                label.setTextColor(Color.WHITE);
                label.setTextSize(10);
                label.setGravity(Gravity.TOP | Gravity.CENTER_HORIZONTAL);
                label.setMaxLines(2);
                label.setEllipsize(android.text.TextUtils.TruncateAt.END);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(30));
                lp.topMargin = dp(3);
                cell.addView(label, lp);
                t = new Tile(icon, label);
                cell.setTag(t);
            }
            App a = data.get(p);
            t.icon.setImageDrawable(a.icon);
            t.label.setText(a.label);
            return (View) ((View) (old == null ? t.icon.getParent() : old));
        }
    }

    private static final class Tile {
        final ImageView icon;
        final TextView label;
        Tile(ImageView i, TextView l) { icon = i; label = l; }
    }

    private static final class App {
        final String key, label;
        final Intent intent;
        final Drawable icon;
        App(String k, String l, Intent i, Drawable d) { key = k; label = l; intent = i; icon = d; }
    }
}
