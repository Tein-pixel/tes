package com.teinzz.teinlite;

import android.app.Activity;
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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/** Small offline launcher: one screen, native widgets, no external libraries. */
public final class MainActivity extends Activity {
    private static final int BLACK = Color.BLACK;
    private static final int WHITE = Color.WHITE;
    private static final int MUTED = 0xFF929292;
    private static final int FIELD = 0xFF151515;

    private final ArrayList<AppItem> allApps = new ArrayList<>();
    private final ArrayList<AppItem> shownApps = new ArrayList<>();
    private AppAdapter adapter;
    private EditText search;
    private TextView status;

    private int dp(float value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        Window w = getWindow();
        w.setStatusBarColor(BLACK);
        w.setNavigationBarColor(BLACK);
        w.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        w.getDecorView().setSystemUiVisibility(0);
        w.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BLACK);
        root.setPadding(dp(12), dp(8), dp(12), dp(4));

        TextView title = new TextView(this);
        title.setText("Apps");
        title.setTextColor(WHITE);
        title.setTextSize(23);
        title.setTypeface(Typeface.DEFAULT, Typeface.NORMAL);
        title.setGravity(Gravity.CENTER_VERTICAL);
        title.setPadding(dp(4), dp(2), 0, dp(8));
        root.addView(title, new LinearLayout.LayoutParams(-1, dp(42)));

        search = new EditText(this);
        search.setSingleLine(true);
        search.setTextColor(WHITE);
        search.setHintTextColor(MUTED);
        search.setHint("Search apps");
        search.setTextSize(15);
        search.setPadding(dp(12), 0, dp(10), 0);
        search.setBackgroundColor(FIELD);
        search.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        search.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        root.addView(search, new LinearLayout.LayoutParams(-1, dp(46)));

        status = new TextView(this);
        status.setTextColor(MUTED);
        status.setTextSize(12);
        status.setGravity(Gravity.CENTER_VERTICAL);
        status.setText("Loading apps…");
        root.addView(status, new LinearLayout.LayoutParams(-1, dp(26)));

        GridView grid = new GridView(this);
        grid.setNumColumns(4);
        grid.setColumnWidth(dp(72));
        grid.setStretchMode(GridView.STRETCH_COLUMN_WIDTH);
        grid.setVerticalSpacing(dp(4));
        grid.setHorizontalSpacing(0);
        grid.setPadding(0, dp(4), 0, dp(4));
        grid.setClipToPadding(false);
        grid.setBackgroundColor(BLACK);
        grid.setCacheColorHint(BLACK);
        root.addView(grid, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(root);

        adapter = new AppAdapter();
        grid.setAdapter(adapter);
        grid.setOnItemClickListener((parent, view, position, id) -> launch(shownApps.get(position)));
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                filter(s == null ? "" : s.toString());
            }
            @Override public void afterTextChanged(Editable e) { }
        });
        loadAppsOffMainThread();
    }

    private void loadAppsOffMainThread() {
        Thread worker = new Thread(() -> {
            ArrayList<AppItem> result = new ArrayList<>();
            PackageManager pm = getPackageManager();
            Intent query = new Intent(Intent.ACTION_MAIN);
            query.addCategory(Intent.CATEGORY_LAUNCHER);
            List<ResolveInfo> resolved = pm.queryIntentActivities(query, 0);
            for (ResolveInfo ri : resolved) {
                try {
                    String pkg = ri.activityInfo.packageName;
                    if (getPackageName().equals(pkg)) continue;
                    CharSequence label = ri.loadLabel(pm);
                    Drawable icon = ri.loadIcon(pm);
                    Intent intent = new Intent(Intent.ACTION_MAIN);
                    intent.addCategory(Intent.CATEGORY_LAUNCHER);
                    intent.setComponent(new ComponentName(pkg, ri.activityInfo.name));
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    result.add(new AppItem(label == null ? pkg : label.toString(), intent, icon));
                } catch (RuntimeException ignored) { }
            }
            Collections.sort(result, (a, b) -> a.label.compareToIgnoreCase(b.label));
            runOnUiThread(() -> {
                allApps.clear();
                allApps.addAll(result);
                filter(search.getText().toString());
            });
        }, "TeinLite-AppScan");
        worker.setPriority(Thread.NORM_PRIORITY - 1);
        worker.start();
    }

    private void filter(String query) {
        if (adapter == null) return;
        String q = query.trim().toLowerCase(Locale.ROOT);
        shownApps.clear();
        for (AppItem app : allApps) {
            if (q.isEmpty() || app.label.toLowerCase(Locale.ROOT).contains(q)) shownApps.add(app);
        }
        adapter.notifyDataSetChanged();
        if (status != null) status.setText(allApps.isEmpty() ? "" : shownApps.size() + " apps");
    }

    private void launch(AppItem app) {
        try { startActivity(app.intent); }
        catch (RuntimeException ignored) { }
    }

    private final class AppAdapter extends BaseAdapter {
        @Override public int getCount() { return shownApps.size(); }
        @Override public Object getItem(int position) { return shownApps.get(position); }
        @Override public long getItemId(int position) { return position; }

        @Override public View getView(int position, View convertView, ViewGroup parent) {
            Tile tile;
            if (convertView instanceof LinearLayout && convertView.getTag() instanceof Tile) {
                tile = (Tile) convertView.getTag();
            } else {
                LinearLayout cell = new LinearLayout(MainActivity.this);
                cell.setOrientation(LinearLayout.VERTICAL);
                cell.setGravity(Gravity.CENTER);
                cell.setPadding(dp(2), dp(4), dp(2), dp(3));
                ImageView icon = new ImageView(MainActivity.this);
                icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
                cell.addView(icon, new LinearLayout.LayoutParams(dp(42), dp(42)));
                TextView label = new TextView(MainActivity.this);
                label.setTextColor(WHITE);
                label.setTextSize(11);
                label.setGravity(Gravity.TOP | Gravity.CENTER_HORIZONTAL);
                label.setMaxLines(2);
                label.setEllipsize(android.text.TextUtils.TruncateAt.END);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(32));
                lp.topMargin = dp(3);
                cell.addView(label, lp);
                tile = new Tile(icon, label);
                cell.setTag(tile);
                convertView = cell;
            }
            AppItem item = shownApps.get(position);
            tile.icon.setImageDrawable(item.icon);
            tile.label.setText(item.label);
            return convertView;
        }
    }

    private static final class Tile {
        final ImageView icon;
        final TextView label;
        Tile(ImageView i, TextView l) { icon = i; label = l; }
    }

    private static final class AppItem {
        final String label;
        final Intent intent;
        final Drawable icon;
        AppItem(String l, Intent i, Drawable d) { label = l; intent = i; icon = d; }
    }
}