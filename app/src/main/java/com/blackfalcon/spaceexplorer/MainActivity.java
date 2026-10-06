package com.blackfalcon.spaceexplorer;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.hardware.GeomagneticField;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.Surface;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.Locale;

public class MainActivity extends Activity implements SensorEventListener, LocationListener {

    private static final int GOLD = Color.parseColor("#FFC107");
    private static final int BG = Color.parseColor("#050510");
    private static final int CARD = Color.parseColor("#12122A");
    private static final int GRAY = Color.parseColor("#A0A0C0");
    private static final String[] MOON_NAME = {"New Moon", "Waxing Crescent", "First Quarter", "Waxing Gibbous",
            "Full Moon", "Waning Gibbous", "Last Quarter", "Waning Crescent"};
    private static final String[] DIR = {"North", "North-East", "East", "South-East", "South", "South-West", "West", "North-West"};

    // sensors / location
    private SensorManager sm;
    private Sensor rot;
    private LocationManager lm;
    private SharedPreferences sp;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable timeout;
    private final float[] rotMat = new float[9], remapped = new float[9], orient = new float[3];
    private float sinAvg = 0f, cosAvg = 1f;
    private boolean firstSample = true;
    private float declination = 0f;
    private double lat, lon;
    private boolean hasLoc = false, searching = false, wasSearching = false;
    private int locStatus = 0; // 0 none, 1 searching, 2 live, 3 saved, 4 gps off, 5 no permission, 6 no fix

    // data
    private Astro.Result sky;
    private int moonPhase = 0;
    private double moonLit = 0;
    private int selected = 1;

    // views
    private SkyDialView dial;
    private LinearLayout skyPage, panelBox;
    private ScrollView panelScroll, planetsPage;
    private TextView facingTv, moonTv, infoTitle, infoVis, infoFun, statusTv, refreshBtn, tabSky, tabPlanets;

    private final Runnable tick = new Runnable() {
        @Override public void run() { updateSky(); handler.postDelayed(this, 5000); }
    };

    // ------------------------------------------------------------ lifecycle
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        sm = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        rot = sm.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
        lm = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        sp = getSharedPreferences("space", MODE_PRIVATE);

        buildUi();

        if (sp.contains("lat") && sp.contains("lon")) {
            applyLocation(Double.longBitsToDouble(sp.getLong("lat", 0)), Double.longBitsToDouble(sp.getLong("lon", 0)));
            locStatus = 3;
        }
        if (hasPerm()) {
            refreshLocation();
        } else {
            locStatus = 5;
            if (Build.VERSION.SDK_INT >= 23) {
                requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 1);
            }
        }
        updateStatus();
        updateSky();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (rot != null) sm.registerListener(this, rot, SensorManager.SENSOR_DELAY_GAME);
        handler.post(tick);
        if (wasSearching) { wasSearching = false; refreshLocation(); }
    }

    @Override
    protected void onPause() {
        super.onPause();
        sm.unregisterListener(this);
        handler.removeCallbacks(tick);
        wasSearching = searching;
        stopLocation();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        applyOrientation();
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] results) {
        super.onRequestPermissionsResult(code, perms, results);
        if (hasPerm()) refreshLocation(); else { locStatus = 5; updateStatus(); }
    }

    private boolean hasPerm() {
        return Build.VERSION.SDK_INT < 23
                || checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }

    // ------------------------------------------------------------------ UI
    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);

        FrameLayout content = new FrameLayout(this);

        // ---- Sky page ----
        skyPage = new LinearLayout(this);
        dial = new SkyDialView(this);
        dial.setSelected(selected);
        dial.setListener(i -> { selected = i; updateInfo(); });

        panelScroll = new ScrollView(this);
        panelBox = new LinearLayout(this);
        panelBox.setOrientation(LinearLayout.VERTICAL);
        panelBox.setPadding(dp(14), dp(8), dp(14), dp(14));

        TextView title = new TextView(this);
        title.setText("\uD83E\uDDED Sky Compass");
        title.setTextColor(Color.WHITE);
        title.setTextSize(22);
        title.setTypeface(Typeface.DEFAULT_BOLD);

        facingTv = tv(15, Color.WHITE, true);
        moonTv = tv(15, GOLD, false);

        LinearLayout infoCard = new LinearLayout(this);
        infoCard.setOrientation(LinearLayout.VERTICAL);
        infoCard.setPadding(dp(14), dp(12), dp(14), dp(12));
        infoCard.setBackground(round(CARD, GOLD, 18));
        infoTitle = tv(18, GOLD, true);
        infoVis = tv(15, Color.WHITE, false);
        infoVis.setPadding(0, dp(6), 0, dp(6));
        infoFun = tv(14, GRAY, false);
        infoCard.addView(infoTitle);
        infoCard.addView(infoVis);
        infoCard.addView(infoFun);

        TextView hint = tv(12, GRAY, false);
        hint.setText("Tap a planet to learn about it. Near the middle = high in the sky. Near the edge = close to the ground. Tip: your fist at arm's length is about 10\u00B0 wide.");
        hint.setPadding(0, dp(8), 0, dp(4));

        statusTv = tv(12, GRAY, false);

        refreshBtn = pill("\u21BB  Update my sky");
        refreshBtn.setOnClickListener(v -> refreshLocation());

        panelBox.addView(title);
        panelBox.addView(facingTv);
        panelBox.addView(moonTv);
        LinearLayout.LayoutParams icp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        icp.setMargins(0, dp(10), 0, 0);
        panelBox.addView(infoCard, icp);
        panelBox.addView(hint);
        panelBox.addView(statusTv);
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        bp.setMargins(0, dp(10), 0, 0);
        panelBox.addView(refreshBtn, bp);
        panelScroll.addView(panelBox);

        skyPage.addView(dial);
        skyPage.addView(panelScroll);
        applyOrientation();

        // ---- Planets page ----
        planetsPage = new ScrollView(this);
        planetsPage.setVisibility(View.GONE);
        planetsPage.addView(buildPlanetsList());

        content.addView(skyPage, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        content.addView(planetsPage, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        // ---- credit + tabs ----
        TextView credit = tv(13, Color.WHITE, true);
        credit.setText("By: Black Falcon \uD83E\uDD85");
        credit.setGravity(Gravity.RIGHT);
        credit.setPadding(dp(14), dp(4), dp(14), dp(4));
        credit.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);

        LinearLayout tabs = new LinearLayout(this);
        tabs.setOrientation(LinearLayout.HORIZONTAL);
        tabs.setBackgroundColor(Color.parseColor("#0C0C20"));
        tabSky = tab("\uD83E\uDDED  Sky");
        tabPlanets = tab("\uD83E\uDE90  Planets");
        tabSky.setOnClickListener(v -> showTab(true));
        tabPlanets.setOnClickListener(v -> showTab(false));
        tabs.addView(tabSky, new LinearLayout.LayoutParams(0, dp(52), 1f));
        tabs.addView(tabPlanets, new LinearLayout.LayoutParams(0, dp(52), 1f));

        root.addView(content, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        root.addView(credit);
        root.addView(tabs);
        setContentView(root);
        showTab(true);
    }

    private void applyOrientation() {
        boolean land = getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
        skyPage.setOrientation(land ? LinearLayout.HORIZONTAL : LinearLayout.VERTICAL);
        dial.setLayoutParams(land
                ? new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
                : new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1.1f));
        panelScroll.setLayoutParams(land
                ? new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
                : new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 0.9f));
    }

    private void showTab(boolean sky) {
        skyPage.setVisibility(sky ? View.VISIBLE : View.GONE);
        planetsPage.setVisibility(sky ? View.GONE : View.VISIBLE);
        tabSky.setTextColor(sky ? GOLD : GRAY);
        tabPlanets.setTextColor(sky ? GRAY : GOLD);
    }

    private TextView tab(String s) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(16);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setGravity(Gravity.CENTER);
        t.setClickable(true);
        return t;
    }

    private TextView tv(int sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setTextSize(sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    private GradientDrawable round(int fill, int stroke, int radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill);
        g.setStroke(dp(2), stroke);
        g.setCornerRadius(dp(radiusDp));
        return g;
    }

    private TextView pill(String s) {
        TextView b = tv(16, GOLD, true);
        b.setText(s);
        b.setGravity(Gravity.CENTER);
        b.setPadding(dp(12), dp(13), dp(12), dp(13));
        b.setBackground(round(Color.parseColor("#2A2200"), GOLD, 26));
        b.setClickable(true);
        return b;
    }

    private View planetDot(int color, int sizeDp) {
        View v = new View(this);
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        g.setColor(color);
        v.setBackground(g);
        v.setLayoutParams(new LinearLayout.LayoutParams(dp(sizeDp), dp(sizeDp)));
        return v;
    }

    // ------------------------------------------------------- planets page
    private View buildPlanetsList() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(14), dp(14), dp(14), dp(14));

        TextView h = tv(24, Color.WHITE, true);
        h.setText("\uD83E\uDE90 Our Solar System");
        TextView sub = tv(15, GRAY, false);
        sub.setText("The Sun is a star. Eight planets travel around it, and the Moon travels around Earth. Tap a card to learn more!");
        sub.setPadding(0, dp(4), 0, dp(8));
        TextView trick = tv(14, GOLD, false);
        trick.setText("Order from the Sun: Mercury, Venus, Earth, Mars, Jupiter, Saturn, Uranus, Neptune.\nTrick: \"My Very Eager Mother Just Served Us Noodles!\"");
        trick.setPadding(dp(12), dp(10), dp(12), dp(10));
        trick.setBackground(round(CARD, Color.parseColor("#3A3A60"), 14));

        box.addView(h);
        box.addView(sub);
        box.addView(trick);

        for (Info info : Info.LIST) box.addView(planetCard(info));
        return box;
    }

    private View planetCard(final Info info) {
        final LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(14), dp(12), dp(14), dp(12));
        card.setBackground(round(CARD, info.color, 18));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, dp(10), 0, 0);
        card.setLayoutParams(lp);
        card.setClickable(true);

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        View dot = planetDot(info.color, 44);
        top.addView(dot);

        LinearLayout names = new LinearLayout(this);
        names.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams np = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        np.setMargins(dp(14), 0, dp(8), 0);
        TextView n = tv(20, Color.WHITE, true);
        n.setText(info.name);
        TextView tag = tv(14, GRAY, false);
        tag.setText(info.tagline);
        names.addView(n);
        names.addView(tag);
        top.addView(names, np);

        final TextView arrow = tv(20, info.color, true);
        arrow.setText("\u25BE");
        top.addView(arrow);
        card.addView(top);

        final LinearLayout details = new LinearLayout(this);
        details.setOrientation(LinearLayout.VERTICAL);
        details.setVisibility(View.GONE);
        details.setPadding(0, dp(10), 0, 0);
        for (String[] f : info.facts) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setPadding(0, dp(3), 0, dp(3));
            TextView a = tv(14, GRAY, false);
            a.setText(f[0]);
            TextView b = tv(14, Color.WHITE, true);
            b.setText(f[1]);
            row.addView(a, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
            row.addView(b, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.4f));
            details.addView(row);
        }
        TextView fun = tv(14, info.color, false);
        fun.setText("\u2B50 Did you know? " + info.fun);
        fun.setPadding(0, dp(10), 0, 0);
        details.addView(fun);
        card.addView(details);

        card.setOnClickListener(v -> {
            boolean open = details.getVisibility() == View.VISIBLE;
            details.setVisibility(open ? View.GONE : View.VISIBLE);
            arrow.setText(open ? "\u25BE" : "\u25B4");
        });
        return card;
    }

    // ------------------------------------------------------------ sky info
    private String dirWord(double azimuth) {
        return DIR[(int) Math.round(azimuth / 45.0) % 8];
    }

    private void updateSky() {
        if (!hasLoc) { updateInfo(); return; }
        sky = Astro.compute(System.currentTimeMillis(), lat, lon);
        moonPhase = ((int) Math.floor((sky.moonElong + 22.5) / 45.0)) % 8;
        moonLit = (1 - Math.cos(Math.toRadians(sky.moonElong))) / 2 * 100;
        dial.setBodies(sky.az, sky.alt, moonPhase);
        moonTv.setText(String.format(Locale.US, "Moon today: %s %s \u2022 %d%% lit",
                SkyDialView.MOON_EMOJI[moonPhase], MOON_NAME[moonPhase], Math.round(moonLit)));
        updateInfo();
    }

    private void updateInfo() {
        Info info = Info.byName(Astro.NAMES[selected]);
        infoTitle.setText(info.name + " \u2014 " + info.tagline);
        infoFun.setText("\u2B50 " + info.fun);

        if (!hasLoc || sky == null) {
            infoVis.setText("Tap \"Update my sky\" so I can find where it is above you!");
            return;
        }
        double a = sky.alt[selected], z = sky.az[selected];
        String s;
        if (a > 0) {
            int fists = Math.max(1, (int) Math.round(a / 10.0));
            s = info.name + " is up in the sky right now! Look " + dirWord(z) + ", about " + fists
                    + (fists == 1 ? " fist" : " fists") + " above the ground.";
            if (selected >= 2 && sky.alt[0] > -6) s += " But the sky is too bright to see it \u2014 try after sunset.";
        } else if (selected == 0) {
            s = "The Sun is on the other side of the Earth right now, so it is night time here.";
        } else {
            s = info.name + " is below the horizon right now \u2014 hiding on the other side of the Earth.";
        }
        infoVis.setText(s);
    }

    private void updateStatus() {
        switch (locStatus) {
            case 1: statusTv.setText("Finding your place on Earth\u2026"); break;
            case 2: statusTv.setText("Using your GPS location \u2022 works offline"); break;
            case 3: statusTv.setText("Using your saved location \u2022 tap Update to refresh"); break;
            case 4: statusTv.setText("GPS is off \u2014 turn it on, then tap Update"); break;
            case 5: statusTv.setText("Please allow location so I can find your sky"); break;
            case 6: statusTv.setText(hasLoc ? "No GPS fix \u2014 using your saved location" : "No GPS fix yet \u2014 go outside and tap Update"); break;
            default: statusTv.setText("Tap Update to find your sky");
        }
    }

    // ------------------------------------------------------------ location
    private void refreshLocation() {
        if (!hasPerm()) {
            locStatus = 5; updateStatus();
            if (Build.VERSION.SDK_INT >= 23) {
                requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 1);
            }
            return;
        }
        stopLocation();
        boolean any = false;
        try {
            for (String pr : new String[]{LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER}) {
                if (lm.isProviderEnabled(pr)) { lm.requestLocationUpdates(pr, 0, 0, this); any = true; }
            }
            if (!hasLoc) {
                Location last = null;
                for (String pr : new String[]{LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER}) {
                    Location l = lm.getLastKnownLocation(pr);
                    if (l != null && (last == null || l.getTime() > last.getTime())) last = l;
                }
                if (last != null) { applyLocation(last.getLatitude(), last.getLongitude()); updateSky(); }
            }
        } catch (SecurityException ignored) { }

        if (!any) { locStatus = 4; updateStatus(); return; }
        searching = true;
        locStatus = 1;
        updateStatus();
        timeout = () -> {
            if (!searching) return;
            stopLocation();
            locStatus = 6;
            updateStatus();
        };
        handler.postDelayed(timeout, 25000);
    }

    private void stopLocation() {
        searching = false;
        if (timeout != null) { handler.removeCallbacks(timeout); timeout = null; }
        try { lm.removeUpdates(this); } catch (SecurityException ignored) { }
    }

    private void applyLocation(double la, double lo) {
        lat = la; lon = lo; hasLoc = true;
        declination = new GeomagneticField((float) la, (float) lo, 0f, System.currentTimeMillis()).getDeclination();
    }

    @Override
    public void onLocationChanged(Location l) {
        if (!searching) return;
        stopLocation();
        sp.edit().putLong("lat", Double.doubleToRawLongBits(l.getLatitude()))
                .putLong("lon", Double.doubleToRawLongBits(l.getLongitude())).apply();
        applyLocation(l.getLatitude(), l.getLongitude());
        locStatus = 2;
        updateStatus();
        updateSky();
    }

    @Override public void onStatusChanged(String p, int s, Bundle e) { }
    @Override public void onProviderEnabled(String p) { }
    @Override public void onProviderDisabled(String p) { }

    // -------------------------------------------------------------- compass
    @SuppressWarnings("deprecation")
    private int displayRotation() {
        return getWindowManager().getDefaultDisplay().getRotation();
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() != Sensor.TYPE_ROTATION_VECTOR) return;
        SensorManager.getRotationMatrixFromVector(rotMat, event.values);
        int xAxis, yAxis;
        switch (displayRotation()) {
            case Surface.ROTATION_90: xAxis = SensorManager.AXIS_Y; yAxis = SensorManager.AXIS_MINUS_X; break;
            case Surface.ROTATION_180: xAxis = SensorManager.AXIS_MINUS_X; yAxis = SensorManager.AXIS_MINUS_Y; break;
            case Surface.ROTATION_270: xAxis = SensorManager.AXIS_MINUS_Y; yAxis = SensorManager.AXIS_X; break;
            default: xAxis = SensorManager.AXIS_X; yAxis = SensorManager.AXIS_Y; break;
        }
        SensorManager.remapCoordinateSystem(rotMat, xAxis, yAxis, remapped);
        SensorManager.getOrientation(remapped, orient);

        float azimuth = (float) Math.toDegrees(orient[0]) + declination;
        double rad = Math.toRadians(azimuth);
        float s = (float) Math.sin(rad), c = (float) Math.cos(rad);
        if (firstSample) { sinAvg = s; cosAvg = c; firstSample = false; }
        else { sinAvg += 0.15f * (s - sinAvg); cosAvg += 0.15f * (c - cosAvg); }

        float heading = (float) Math.toDegrees(Math.atan2(sinAvg, cosAvg));
        if (heading < 0) heading += 360f;
        dial.setHeading(heading);
        String[] d8 = {"N", "NE", "E", "SE", "S", "SW", "W", "NW"};
        facingTv.setText(String.format(Locale.US, "You are facing: %d\u00B0 %s", Math.round(heading) % 360, d8[Math.round(heading / 45f) % 8]));
    }

    @Override public void onAccuracyChanged(Sensor sensor, int accuracy) { }
}
