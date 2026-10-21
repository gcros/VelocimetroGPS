package br.com.gcros.velocimetrogps;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.media.AudioManager;
import android.media.ToneGenerator;

public class MainActivity extends Activity {
    private static final int REQ_LOCATION = 41;
    private final int BG = Color.rgb(8, 10, 22);
    private final int PANEL = Color.rgb(17, 21, 40);
    private final int CYAN = Color.rgb(0, 245, 255);
    private final int PINK = Color.rgb(255, 45, 155);
    private final int MUTED = Color.rgb(145, 160, 190);
    private LocationManager locationManager;
    private TextView speedText, statusText, gpsDot, alertText;
    private ToneGenerator tones;
    private int lastAlert = 0;
    private long lastAlertTime = 0;

    private final LocationListener listener = new LocationListener() {
        @Override public void onLocationChanged(Location location) {
            if (location.hasAccuracy() && location.getAccuracy() > 60) {
                statusText.setText("SINAL GPS FRACO  •  ±" + (int)location.getAccuracy() + " m");
                gpsDot.setTextColor(PINK);
                return;
            }
            int speed = Math.max(0, (int)Math.round(location.hasSpeed() ? location.getSpeed() * 3.6 : 0));
            speedText.setText(String.valueOf(speed));
            statusText.setText("GPS ATIVO  •  PRECISÃO ±" + (location.hasAccuracy() ? (int)location.getAccuracy() : "?") + " m");
            gpsDot.setTextColor(CYAN);
            handleAlerts(speed);
        }
        @Override public void onProviderEnabled(String provider) { statusText.setText("GPS ATIVO  •  BUSCANDO SINAL"); }
        @Override public void onProviderDisabled(String provider) {
            statusText.setText("LOCALIZAÇÃO DESATIVADA");
            gpsDot.setTextColor(PINK);
        }
        @Override public void onStatusChanged(String provider, int status, Bundle extras) {}
    };

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        getWindow().getDecorView().setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_LAYOUT_STABLE |
            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
        tones = new ToneGenerator(AudioManager.STREAM_ALARM, 85);
        buildUi();
        locationManager = (LocationManager)getSystemService(LOCATION_SERVICE);
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, REQ_LOCATION);
        } else startGps();
    }

    private GradientDrawable shape(int color, int strokeColor, int stroke, int radius) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(radius);
        if (stroke > 0) d.setStroke(stroke, strokeColor);
        return d;
    }

    private TextView text(String value, int size, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextColor(color);
        t.setTextSize(size);
        t.setGravity(Gravity.CENTER);
        t.setTypeface(Typeface.create(bold ? "sans-serif-condensed" : "sans-serif", bold ? Typeface.BOLD : Typeface.NORMAL));
        t.setIncludeFontPadding(false);
        return t;
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setBackgroundColor(BG);
        int pad = dp(22);
        root.setPadding(pad, dp(18), pad, dp(18));

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setGravity(Gravity.CENTER);
        TextView brand = text("N E O N   /   D R I V E", 13, CYAN, true);
        header.addView(brand, new LinearLayout.LayoutParams(-1, dp(25)));
        TextView title = text("VELOCÍMETRO GPS", 23, Color.WHITE, true);
        header.addView(title, new LinearLayout.LayoutParams(-1, dp(36)));
        TextView subtitle = text("MONITOR DE VELOCIDADE", 10, MUTED, false);
        header.addView(subtitle, new LinearLayout.LayoutParams(-1, dp(20)));
        root.addView(header, new LinearLayout.LayoutParams(-1, -2));

        View line = new View(this);
        line.setBackgroundColor(Color.rgb(35, 62, 88));
        LinearLayout.LayoutParams lpLine = new LinearLayout.LayoutParams(-1, dp(1));
        lpLine.topMargin = dp(10); lpLine.bottomMargin = dp(12);
        root.addView(line, lpLine);

        LinearLayout center = new LinearLayout(this);
        center.setOrientation(LinearLayout.VERTICAL);
        center.setGravity(Gravity.CENTER);
        center.setBackground(shape(PANEL, Color.rgb(31, 48, 78), dp(1), dp(28)));
        center.setPadding(dp(12), dp(16), dp(12), dp(16));

        TextView label = text("VELOCIDADE ATUAL", 11, MUTED, true);
        center.addView(label, new LinearLayout.LayoutParams(-1, dp(25)));

        // Use a square dial sized from the available screen width to avoid overflow on small phones.
        int screenW = getResources().getDisplayMetrics().widthPixels;
        int dialSize = Math.min(screenW - dp(88), dp(310));
        dialSize = Math.max(dp(205), dialSize);
        LinearLayout dial = new LinearLayout(this);
        dial.setOrientation(LinearLayout.VERTICAL);
        dial.setGravity(Gravity.CENTER);
        GradientDrawable ring = new GradientDrawable();
        ring.setShape(GradientDrawable.OVAL);
        ring.setColor(Color.rgb(11, 17, 32));
        ring.setStroke(dp(5), CYAN);
        dial.setBackground(ring);
        dial.setElevation(dp(5));
        LinearLayout.LayoutParams dialLp = new LinearLayout.LayoutParams(dialSize, dialSize);
        dialLp.gravity = Gravity.CENTER;
        dialLp.topMargin = dp(6);
        dialLp.bottomMargin = dp(8);
        center.addView(dial, dialLp);

        TextView tiny = text("KM / H", 12, PINK, true);
        dial.addView(tiny, new LinearLayout.LayoutParams(-1, dp(28)));
        speedText = text("—", 94, Color.WHITE, true);
        speedText.setShadowLayer(dp(14), 0, 0, Color.rgb(0, 120, 160));
        dial.addView(speedText, new LinearLayout.LayoutParams(-1, 0, 1));
        TextView unit = text("km/h", 18, CYAN, true);
        dial.addView(unit, new LinearLayout.LayoutParams(-1, dp(34)));

        statusText = text("AGUARDANDO SINAL GPS…", 11, MUTED, false);
        LinearLayout.LayoutParams statusLp = new LinearLayout.LayoutParams(-1, dp(30));
        statusLp.topMargin = dp(4);
        center.addView(statusText, statusLp);
        root.addView(center, new LinearLayout.LayoutParams(-1, 0, 1));

        LinearLayout footer = new LinearLayout(this);
        footer.setOrientation(LinearLayout.VERTICAL);
        footer.setGravity(Gravity.CENTER);
        footer.setPadding(0, dp(12), 0, 0);
        TextView alertsTitle = text("ALERTAS DE VELOCIDADE", 11, MUTED, true);
        footer.addView(alertsTitle, new LinearLayout.LayoutParams(-1, dp(22)));
        LinearLayout chips = new LinearLayout(this);
        chips.setOrientation(LinearLayout.HORIZONTAL);
        chips.setGravity(Gravity.CENTER);
        int[] values = {80, 100, 120};
        int[] colors = {CYAN, Color.rgb(255, 174, 0), PINK};
        for (int i = 0; i < values.length; i++) {
            TextView chip = text(values[i] + " km/h", 13, colors[i], true);
            chip.setBackground(shape(Color.rgb(18, 25, 43), colors[i], dp(1), dp(18)));
            chip.setPadding(dp(12), dp(8), dp(12), dp(8));
            LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(0, dp(38), 1);
            cp.setMargins(dp(4), dp(4), dp(4), 0);
            chips.addView(chip, cp);
        }
        footer.addView(chips, new LinearLayout.LayoutParams(-1, dp(44)));
        TextView note = text("MANTENHA O GPS ATIVO DURANTE O USO", 9, Color.rgb(88, 104, 134), false);
        footer.addView(note, new LinearLayout.LayoutParams(-1, dp(24)));
        root.addView(footer, new LinearLayout.LayoutParams(-1, -2));
        setContentView(root);
    }

    private int dp(float value) {
        return (int)(value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private void startGps() {
        try {
            if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
                checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) return;
            if (!locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                statusText.setText("ATIVE A LOCALIZAÇÃO / GPS");
                gpsDot.setTextColor(PINK);
                return;
            }
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 500, 0, listener, Looper.getMainLooper());
            Location last = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            if (last != null) listener.onLocationChanged(last);
        } catch (SecurityException e) {
            statusText.setText("PERMISSÃO DE GPS NECESSÁRIA");
        }
    }

    private void handleAlerts(int speed) {
        int level = speed >= 120 ? 3 : speed >= 100 ? 2 : speed >= 80 ? 1 : 0;
        if (level == 0) { lastAlert = 0; alertText = null; return; }
        long now = System.currentTimeMillis();
        if (level > lastAlert && now - lastAlertTime > 1800) {
            if (level == 1) tones.startTone(ToneGenerator.TONE_PROP_BEEP, 180);
            else if (level == 2) tones.startTone(ToneGenerator.TONE_PROP_ACK, 260);
            else tones.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 650);
            lastAlert = level;
            lastAlertTime = now;
        }
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode == REQ_LOCATION && results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) startGps();
        else statusText.setText("PERMITA O ACESSO À LOCALIZAÇÃO");
    }

    @Override protected void onDestroy() {
        super.onDestroy();
        if (locationManager != null) locationManager.removeUpdates(listener);
        if (tones != null) tones.release();
    }
}