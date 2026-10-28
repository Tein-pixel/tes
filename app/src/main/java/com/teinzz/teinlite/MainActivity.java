package com.teinzz.teinlite;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.text.InputType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Blank-first native launcher. No services, network, libraries or animations. */
public final class MainActivity extends Activity {
    private static final int BLACK = Color.BLACK, WHITE = Color.WHITE, MUTED = 0xFF929292;
    private final ArrayList<AppItem> all = new ArrayList<>(), home = new ArrayList<>(), shown = new ArrayList<>();
    private final Set<String> picked = new HashSet<>();
    private final ArrayList<Runnable> waiting = new ArrayList<>();
    private GridView homeGrid, drawerGrid;
    private AppAdapter homeAdapter, drawerAdapter;
    private EditText search;
    private boolean loaded, loading;
    private int screen;
    private float downY;

    private int dp(float n) { return (int)(n * getResources().getDisplayMetrics().density + .5f); }

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        Window w = getWindow();
        w.setStatusBarColor(BLACK);
        w.setNavigationBarColor(BLACK);
        w.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        w.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING);
        Set<String> saved = getSharedPreferences("home", 0).getStringSet("apps", Collections.<String>emptySet());
        picked.addAll(saved);
        showHome();
        if (!picked.isEmpty()) loadPicked();
    }

    private GridView grid(boolean homeScreen) {
        GridView g = new GridView(this) {
            @Override public boolean onInterceptTouchEvent(MotionEvent e) {
                if (e.getAction() == MotionEvent.ACTION_DOWN) downY = e.getY();
                if (e.getAction() == MotionEvent.ACTION_MOVE) {
                    float dy = e.getY() - downY;
                    if (homeScreen && downY > getHeight() * .72f && dy < -dp(55)) {
                        showDrawer();
                        return true;
                    }
                    if (!homeScreen && dy > dp(70)) {
                        showHome();
                        return true;
                    }
                }
                return super.onInterceptTouchEvent(e);
            }
        };
        g.setNumColumns(4);
        g.setColumnWidth(dp(72));
        g.setStretchMode(GridView.STRETCH_COLUMN_WIDTH);
        g.setVerticalSpacing(dp(8));
        g.setHorizontalSpacing(0);
        g.setPadding(dp(8), dp(14), dp(8), dp(12));
        g.setClipToPadding(false);
        g.setBackgroundColor(BLACK);
        g.setCacheColorHint(BLACK);
        return g;
    }

    private void showHome() {
        screen = 0;
        homeGrid = grid(true);
        homeAdapter = new AppAdapter(home);
        homeGrid.setAdapter(homeAdapter);
        homeGrid.setOnItemClickListener((p, v, pos, id) -> launch(home.get(pos)));
        homeGrid.setOnItemLongClickListener((p, v, pos, id) -> {
            remove(home.get(pos));
            return true;
        });
        homeGrid.setLongClickable(true);
        homeGrid.setOnLongClickListener(v -> { addPicker(); return true; });
        setContentView(homeGrid);
    }

    private void showDrawer() {
        screen = 1;
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BLACK);
        root.setPadding(dp(12), dp(8), dp(12), 0);
        TextView title = new TextView(this);
        title.setText("Apps");
        title.setTextColor(WHITE);
        title.setTextSize(18);
        title.setTypeface(Typeface.DEFAULT, Typeface.NORMAL);
        title.setGravity(Gravity.CENTER_VERTICAL);
        root.addView(title, new LinearLayout.LayoutParams(-1, dp(34)));
        search = new EditText(this);
        search.setSingleLine(true);
        search.setTextColor(WHITE);
        search.setHintTextColor(MUTED);
        search.setHint("Search apps");
        search.setTextSize(14);
        search.setPadding(dp(12), 0, dp(10), 0);
        search.setBackgroundColor(0xFF151515);
        search.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        search.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        root.addView(search, new LinearLayout.LayoutParams(-1, dp(42)));
        drawerGrid = grid(false);
        drawerAdapter = new AppAdapter(shown);
        drawerGrid.setAdapter(drawerAdapter);
        drawerGrid.setOnItemClickListener((p, v, pos, id) -> launch(shown.get(pos)));
        drawerGrid.setOnItemLongClickListener((p, v, pos, id) -> { add(shown.get(pos)); return true; });
        root.addView(drawerGrid, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(root);
        search.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int st, int c, int a) { }
            public void onTextChanged(CharSequence s, int st, int before, int count) { filter(s.toString()); }
            public void afterTextChanged(Editable e) { }
        });
        ensureLoaded(() -> filter(""));
    }

    private void ensureLoaded(Runnable done) {
        if (loaded) { done.run(); return; }
        waiting.add(done);
        if (loading) return;
        loading = true;
        new Thread(() -> {
            ArrayList<AppItem> result = new ArrayList<>();
            PackageManager pm = getPackageManager();
            Intent q = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
            for (ResolveInfo r : pm.queryIntentActivities(q, 0)) {
                try {
                    ComponentName c = new ComponentName(r.activityInfo.packageName, r.activityInfo.name);
                    if (getPackageName().equals(c.getPackageName())) continue;
                    result.add(item(pm, c));
                } catch (RuntimeException ignored) { }
            }
            Collections.sort(result, (a, b) -> a.label.compareToIgnoreCase(b.label));
            runOnUiThread(() -> {
                all.clear(); all.addAll(result);
                loaded = true; loading = false;
                ArrayList<Runnable> next = new ArrayList<>(waiting); waiting.clear();
                for (Runnable task : next) task.run();
            });
        }, "TeinLite-Apps").start();
    }

    private AppItem item(PackageManager pm, ComponentName c) throws PackageManager.NameNotFoundException {
        android.content.pm.ActivityInfo info = pm.getActivityInfo(c, 0);
        Intent i = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                .setComponent(c).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        CharSequence label = info.loadLabel(pm);
        return new AppItem(c.flattenToString(), label == null ? c.getPackageName() : label.toString(), i, info.loadIcon(pm));
    }

    private void loadPicked() {
        ArrayList<String> keys = new ArrayList<>(picked);
        new Thread(() -> {
            ArrayList<AppItem> result = new ArrayList<>();
            PackageManager pm = getPackageManager();
            for (String key : keys) {
                ComponentName c = ComponentName.unflattenFromString(key);
                if (c == null) continue;
                try { result.add(item(pm, c)); } catch (RuntimeException | PackageManager.NameNotFoundException ignored) { }
            }
            Collections.sort(result, (a, b) -> a.label.compareToIgnoreCase(b.label));
            runOnUiThread(() -> { home.clear(); home.addAll(result); if (homeAdapter != null) homeAdapter.notifyDataSetChanged(); });
        }, "TeinLite-Home").start();
    }

    private void filter(String text) {
        if (drawerAdapter == null) return;
        String q = text.trim().toLowerCase(Locale.ROOT);
        shown.clear();
        for (AppItem a : all) if (q.isEmpty() || a.label.toLowerCase(Locale.ROOT).contains(q)) shown.add(a);
        drawerAdapter.notifyDataSetChanged();
    }

    private void addPicker() {
        ensureLoaded(() -> {
            String[] labels = new String[all.size()];
            boolean[] checked = new boolean[all.size()];
            for (int i = 0; i < all.size(); i++) {
                labels[i] = all.get(i).label;
                checked[i] = picked.contains(all.get(i).key);
            }
            new AlertDialog.Builder(this).setTitle("Add to home")
                    .setMultiChoiceItems(labels, checked, (d, which, yes) -> checked[which] = yes)
                    .setNegativeButton("Cancel", null)
                    .setPositiveButton("Save", (d, w) -> {
                        picked.clear();
                        for (int i = 0; i < all.size(); i++) if (checked[i]) picked.add(all.get(i).key);
                        persist();
                        home.clear();
                        for (AppItem a : all) if (picked.contains(a.key)) home.add(a);
                        Collections.sort(home, (a, b) -> a.label.compareToIgnoreCase(b.label));
                        if (homeAdapter != null) homeAdapter.notifyDataSetChanged();
                    }).show();
        });
    }

    private void add(AppItem a) {
        if (picked.add(a.key)) {
            home.add(a);
            Collections.sort(home, (x, y) -> x.label.compareToIgnoreCase(y.label));
            persist();
            if (homeAdapter != null) homeAdapter.notifyDataSetChanged();
            Toast.makeText(this, "Added to home", Toast.LENGTH_SHORT).show();
        } else Toast.makeText(this, "Already on home", Toast.LENGTH_SHORT).show();
    }

    private void remove(AppItem a) {
        new AlertDialog.Builder(this).setMessage("Remove " + a.label + " from home?")
                .setNegativeButton("Cancel", null).setPositiveButton("Remove", (d, w) -> {
                    picked.remove(a.key);
                    home.remove(a);
                    persist();
                    if (homeAdapter != null) homeAdapter.notifyDataSetChanged();
                }).show();
    }

    private void persist() {
        getSharedPreferences("home", 0).edit().putStringSet("apps", new HashSet<>(picked)).apply();
    }

    private void launch(AppItem a) {
        if (screen == 1) showHome();
        try { startActivity(a.intent); } catch (RuntimeException ignored) { }
    }

    @Override public void onBackPressed() {
        if (screen == 1) showHome(); else super.onBackPressed();
    }

    private final class AppAdapter extends BaseAdapter {
        private final ArrayList<AppItem> data;
        AppAdapter(ArrayList<AppItem> d) { data = d; }
        public int getCount() { return data.size(); }
        public Object getItem(int p) { return data.get(p); }
        public long getItemId(int p) { return p; }
        public View getView(int p, View old, ViewGroup parent) {
            Tile t;
            if (old != null && old.getTag() instanceof Tile) t = (Tile)old.getTag();
            else {
                LinearLayout cell = new LinearLayout(MainActivity.this);
                cell.setOrientation(LinearLayout.VERTICAL);
                cell.setGravity(Gravity.CENTER);
                cell.setPadding(dp(2), dp(3), dp(2), dp(3));
                ImageView icon = new ImageView(MainActivity.this);
                icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
                cell.addView(icon, new LinearLayout.LayoutParams(dp(40), dp(40)));
                TextView label = new TextView(MainActivity.this);
                label.setTextColor(WHITE);
                label.setTextSize(10);
                label.setGravity(Gravity.TOP | Gravity.CENTER_HORIZONTAL);
                label.setMaxLines(2);
                label.setEllipsize(android.text.TextUtils.TruncateAt.END);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(30));
                lp.topMargin = dp(3);
                cell.addView(label, lp);
                t = new Tile(icon, label);
                cell.setTag(t); old = cell;
            }
            AppItem a = data.get(p);
            t.icon.setImageDrawable(a.icon);
            t.label.setText(a.label);
            return old;
        }
    }

    private static final class Tile {
        final ImageView icon; final TextView label;
        Tile(ImageView i, TextView l) { icon = i; label = l; }
    }
    private static final class AppItem {
        final String key, label; final Intent intent; final Drawable icon;
        AppItem(String k, String l, Intent i, Drawable d) { key = k; label = l; intent = i; icon = d; }
    }
}
