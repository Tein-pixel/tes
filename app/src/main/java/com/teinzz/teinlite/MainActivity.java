package com.teinzz.teinlite;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.InputType;
import android.view.inputmethod.EditorInfo;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

public final class MainActivity extends Activity {
    private final ArrayList<App> apps = new ArrayList<>(), home = new ArrayList<>(), shown = new ArrayList<>();
    private EditText searchInput;
    private final HashSet<String> picked = new HashSet<>();
    private GridView homeGrid, drawerGrid;
    private AppsAdapter homeAdapter, drawerAdapter;
    private boolean drawer;
    private int touchSlop;

    private int dp(float n) { return (int)(n * getResources().getDisplayMetrics().density + .5f); }

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        Window w = getWindow();
        w.setStatusBarColor(Color.BLACK);
        w.setNavigationBarColor(Color.BLACK);
        touchSlop = ViewConfiguration.get(this).getScaledTouchSlop();
        Set<String> old = getSharedPreferences("home", 0).getStringSet("apps", null);
        if (old != null) picked.addAll(old);
        showHome();
        loadApps();
        if (homeGrid != null) homeGrid.post(this::promptDefaultHomeIfNeeded);
    }

    private boolean isDefaultHome() {
        Intent homeIntent = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
        ResolveInfo homeInfo = getPackageManager().resolveActivity(homeIntent, PackageManager.MATCH_DEFAULT_ONLY);
        return homeInfo != null && homeInfo.activityInfo != null
                && getPackageName().equals(homeInfo.activityInfo.packageName);
    }

    private void promptDefaultHomeIfNeeded() {
        if (isFinishing() || isDefaultHome()) return;
        android.content.SharedPreferences setup = getSharedPreferences("setup", 0);
        if (setup.getBoolean("home_prompt_shown", false)) return;
        setup.edit().putBoolean("home_prompt_shown", true).apply();
        new AlertDialog.Builder(this)
                .setTitle("Jadikan TeinLite launcher utama?")
                .setMessage("Pilih TeinLite di pengaturan Home agar tombol Home tidak kembali ke launcher bawaan.")
                .setNegativeButton("Nanti", null)
                .setPositiveButton("Pilih sekarang", (dialog, which) -> {
                    try {
                        startActivity(new Intent(Settings.ACTION_HOME_SETTINGS));
                    } catch (ActivityNotFoundException ignored) {
                        try { startActivity(new Intent(Settings.ACTION_SETTINGS)); }
                        catch (ActivityNotFoundException ignoredAgain) { }
                    }
                })
                .show();
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        showHome();
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
                if (drawerAdapter != null) filterApps(searchInput == null ? "" : searchInput.getText().toString());
            });
        }, "tl-apps").start();
    }

    private GridView grid() {
        GridView g = new GridView(this);
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
        GestureRoot root = new GestureRoot(true);
        root.setBackgroundColor(Color.BLACK);
        homeGrid = grid();
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
        root.addView(homeGrid, new FrameLayout.LayoutParams(-1, -1));
        DrawerIndicator indicator = new DrawerIndicator();
        FrameLayout.LayoutParams indicatorParams = new FrameLayout.LayoutParams(dp(30), dp(14), Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        indicatorParams.bottomMargin = dp(5);
        root.addView(indicator, indicatorParams);
        setContentView(root);
    }

    private void showDrawer() {
        drawer = true;
        GestureRoot root = new GestureRoot(false);
        root.setPadding(dp(10), dp(8), dp(10), 0);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setBackgroundColor(Color.BLACK);
        root.addView(content, new FrameLayout.LayoutParams(-1, -1));

        searchInput = new EditText(this);
        searchInput.setSingleLine(true);
        searchInput.setTextColor(Color.WHITE);
        searchInput.setHintTextColor(0xFF909090);
        searchInput.setHint("Cari aplikasi");
        searchInput.setTextSize(14);
        searchInput.setPadding(dp(14), 0, dp(12), 0);
        searchInput.setBackgroundColor(0xFF191919);
        searchInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        searchInput.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        LinearLayout.LayoutParams searchParams = new LinearLayout.LayoutParams(-1, dp(46));
        searchParams.bottomMargin = dp(5);
        content.addView(searchInput, searchParams);

        shown.clear();
        shown.addAll(apps);
        drawerGrid = grid();
        drawerGrid.setPadding(dp(3), dp(5), dp(3), dp(12));
        drawerAdapter = new AppsAdapter(shown);
        drawerGrid.setAdapter(drawerAdapter);
        drawerGrid.setOnItemClickListener((p, v, pos, id) -> launch(shown.get(pos)));
        drawerGrid.setOnItemLongClickListener((p, v, pos, id) -> {
            App a = shown.get(pos);
            if (picked.add(a.key)) {
                home.add(a);
                Collections.sort(home, (x, y) -> x.label.compareToIgnoreCase(y.label));
                save();
                if (homeAdapter != null) homeAdapter.notifyDataSetChanged();
            }
            return true;
        });
        content.addView(drawerGrid, new LinearLayout.LayoutParams(-1, 0, 1));
        searchInput.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            public void onTextChanged(CharSequence s, int start, int before, int count) { filterApps(s.toString()); }
            public void afterTextChanged(Editable e) { }
        });
        setContentView(root);
    }

    private void filterApps(String query) {
        if (drawerAdapter == null) return;
        String q = query.trim().toLowerCase(Locale.ROOT);
        shown.clear();
        for (App a : apps) if (q.isEmpty() || a.label.toLowerCase(Locale.ROOT).contains(q)) shown.add(a);
        drawerAdapter.notifyDataSetChanged();
    }
    private void save() {
        getSharedPreferences("home", 0).edit().putStringSet("apps", new HashSet<>(picked)).apply();
    }

    private void launch(App a) {
        showHome();
        try { startActivity(a.intent); } catch (RuntimeException ignored) { }
    }

    @Override public void onBackPressed() {
        if (drawer) showHome();
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
                cell.addView(icon, new LinearLayout.LayoutParams(dp(54), dp(54)));
                TextView label = new TextView(MainActivity.this);
                label.setTextColor(Color.WHITE);
                label.setTextSize(11);
                label.setGravity(Gravity.TOP | Gravity.CENTER_HORIZONTAL);
                label.setMaxLines(2);
                label.setEllipsize(android.text.TextUtils.TruncateAt.END);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(34));
                lp.topMargin = dp(4);
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


    private final class GestureRoot extends FrameLayout {
        private final boolean homeSurface;
        private float downX, downY;
        private boolean startedAtTop;
        private boolean transitionPosted;
        private int transition;

        GestureRoot(boolean homeSurface) {
            super(MainActivity.this);
            this.homeSurface = homeSurface;
            setBackgroundColor(Color.BLACK);
        }

        private boolean drawerIsAtTop() {
            if (drawerGrid == null || drawerGrid.getChildCount() == 0) return true;
            return drawerGrid.getFirstVisiblePosition() == 0
                    && drawerGrid.getChildAt(0).getTop() >= drawerGrid.getPaddingTop() - dp(2);
        }

        @Override public boolean onInterceptTouchEvent(MotionEvent event) {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    downX = event.getX();
                    downY = event.getY();
                    startedAtTop = homeSurface || drawerIsAtTop();
                    transition = 0;
                    transitionPosted = false;
                    break;
                case MotionEvent.ACTION_MOVE:
                    if (transition == 0) {
                        float dx = event.getX() - downX;
                        float dy = event.getY() - downY;
                        if (Math.abs(dy) >= touchSlop * 2
                                && Math.abs(dy) > Math.abs(dx) * 1.2f) {
                            if (homeSurface && dy < 0) {
                                transition = 1;
                                return true;
                            }
                            if (!homeSurface && startedAtTop && dy > 0) {
                                transition = 2;
                                return true;
                            }
                        }
                    }
                    break;
            }
            return super.onInterceptTouchEvent(event);
        }

        @Override public boolean onTouchEvent(MotionEvent event) {
            if (transition == 0) return super.onTouchEvent(event);
            if (event.getActionMasked() == MotionEvent.ACTION_MOVE && !transitionPosted) {
                transitionPosted = true;
                final int next = transition;
                post(() -> {
                    if (isFinishing()) return;
                    if (next == 1 && !drawer) showDrawer();
                    else if (next == 2 && drawer) showHome();
                });
            }
            return true;
        }
    }

    private final class DrawerIndicator extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        DrawerIndicator() {
            super(MainActivity.this);
            setContentDescription("Geser ke atas untuk membuka laci aplikasi");
            setClickable(false);
            setFocusable(false);
            setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        }
        @Override protected void onDraw(Canvas canvas) {
            float cx = getWidth() / 2f, cy = getHeight() / 2f;
            paint.setColor(0xFF777777);
            float gap = dp(5), radius = dp(1.1f);
            for (int col = -1; col <= 1; col++)
                canvas.drawCircle(cx + col * gap, cy, radius, paint);
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
