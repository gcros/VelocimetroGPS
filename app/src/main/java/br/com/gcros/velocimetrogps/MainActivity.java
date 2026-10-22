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
import android.widget.ScrollView;
import android.widget.TextView;
import android.media.AudioManager;
import android.media.ToneGenerator;

public class MainActivity extends Activity {
    private static final int REQ_LOCATION = 41;
    private static final int BG = Color.rgb(8, 10, 22);
    private static final int PANEL = Color.rgb(17, 21, 40);
    private static final int CYAN = Color.rgb(0, 220, 240);
    private static final int PINK = Color.rgb(255, 45, 155);
    private static final int MUTED = Color.rgb(145, 160, 190);

    private LocationManager locationManager;
    private TextView speedText, statusText, gpsDot;
    private ToneGenerator tones;
    private int lastAlert;
    private long lastAlertTime;

    private final LocationListener listener = new LocationListener() {
        @Override public void onLocationChanged(Location location) {
            if (speedText == null || statusText == null || gpsDot == null) return;
            if (location.hasAccuracy() && location.getAccuracy() > 60) {
                statusText.setText("SINAL GPS FRACO • ±" + (int) location.getAccuracy() + " m");
                gpsDot.setTextColor(PINK);
                return;
            }
            int speed = Math.max(0, (int) Math.round(location.hasSpeed() ? location.getSpeed() * 3.6 : 0));
            speedText.setText(String.valueOf(speed));
            statusText.setText("GPS ATIVO • PRECISÃO ±" +
                    (location.hasAccuracy() ? (int) location.getAccuracy() : "?") + " m");
            gpsDot.setTextColor(CYAN);
            handleAlerts(speed);
        }
        @Override public void onProviderEnabled(String provider) {
            if (statusText != null) statusText.setText("GPS ATIVO • BUSCANDO SINAL");
        }
        @Override public void onProviderDisabled(String provider) {
            if (statusText != null) statusText.setText("LOCALIZAÇÃO DESATIVADA");
            if (gpsDot != null) gpsDot.setTextColor(PINK);
        }
        @Override public void onStatusChanged(String provider, int status, Bundle extras) {}
    };

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        buildUi();
        try {
            locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
        } catch (RuntimeException e) {
            statusText.setText("NÃO FOI POSSÍVEL INICIAR O GPS");
            return;
        }
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
                checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION}, REQ_LOCATION);
        } else {
            startGps();
        }
    }

    private GradientDrawable shape(int color, int strokeColor, int stroke, int radius) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(radius));
        if (stroke > 0) d.setStroke(dp(stroke), strokeColor);
        return d;
    }

    private TextView text(String value, int size, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextColor(color);
        t.setTextSize(size);
        t.setGravity(Gravity.CENTER);
        t.setTypeface(Typeface.create(bold ? "sans-serif-condensed" : "sans-serif",
                bold ? Typeface.BOLD : Typeface.NORMAL));
        t.setIncludeFontPadding(false);
        return t;
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(BG);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(dp(18), dp(20), dp(18), dp(18));
        scroll.addView(root, new ScrollView.LayoutParams(-1, -2));

        TextView brand = text("N E O N  /  D R I V E", 13, CYAN, true);
        root.addView(brand, new LinearLayout.LayoutParams(-1, dp(26)));
        TextView title = text("VELOCÍMETRO GPS", 24, Color.WHITE, true);
        root.addView(title, new LinearLayout.LayoutParams(-1, dp(38)));
        TextView subtitle = text("MONITOR DE VELOCIDADE", 10, MUTED, false);
        root.addView(subtitle, new LinearLayout.LayoutParams(-1, dp(22)));

        View line = new View(this);
        line.setBackgroundColor(Color.rgb(35, 62, 88));
        LinearLayout.LayoutParams lineLp = new LinearLayout.LayoutParams(-1, dp(1));
        lineLp.setMargins(0, dp(12), 0, dp(14));
        root.addView(line, lineLp);

        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setGravity(Gravity.CENTER);
        panel.setBackground(shape(PANEL, Color.rgb(31, 48, 78), 1, 26));
        panel.setPadding(dp(12), dp(18), dp(12), dp(18));
        root.addView(panel, new LinearLayout.LayoutParams(-1, -2));

        panel.addView(text("VELOCIDADE ATUAL", 11, MUTED, true),
                new LinearLayout.LayoutParams(-1, dp(28)));

        int screenW = getResources().getDisplayMetrics().widthPixels;
        int available = screenW - dp(80);
        int dialSize = Math.max(dp(190), Math.min(available, dp(290)));
        LinearLayout dial = new LinearLayout(this);
        dial.setOrientation(LinearLayout.VERTICAL);
        dial.setGravity(Gravity.CENTER);
        GradientDrawable ring = new GradientDrawable();
        ring.setShape(GradientDrawable.OVAL);
        ring.setColor(Color.rgb(11, 17, 32));
        ring.setStroke(dp(4), CYAN);
        dial.setBackground(ring);
        dial.setElevation(dp(4));
        LinearLayout.LayoutParams dialLp = new LinearLayout.LayoutParams(dialSize, dialSize);
        dialLp.gravity = Gravity.CENTER;
        dialLp.setMargins(0, dp(8), 0, dp(12));
        panel.addView(dial, dialLp);

        dial.addView(text("KM / H", 12, PINK, true),
                new LinearLayout.LayoutParams(-1, dp(30)));
        speedText = text("—", 88, Color.WHITE, true);
        speedText.setShadowLayer(dp(10), 0, 0, Color.rgb(0, 100, 150));
        dial.addView(speedText, new LinearLayout.LayoutParams(-1, 0, 1));
        dial.addView(text("km/h", 17, CYAN, true),
                new LinearLayout.LayoutParams(-1, dp(32)));

        LinearLayout statusRow = new LinearLayout(this);
        statusRow.setOrientation(LinearLayout.HORIZONTAL);
        statusRow.setGravity(Gravity.CENTER);
        gpsDot = text("●", 12, MUTED, true);
        statusRow.addView(gpsDot, new LinearLayout.LayoutParams(dp(22), dp(30)));
        statusText = text("AGUARDANDO SINAL GPS…", 11, MUTED, false);
        statusRow.addView(statusText, new LinearLayout.LayoutParams(0, dp(30), 1));
        panel.addView(statusRow, new LinearLayout.LayoutParams(-1, dp(34)));

        TextView alertsTitle = text("ALERTAS DE VELOCIDADE", 11, MUTED, true);
        LinearLayout.LayoutParams alertTitleLp = new LinearLayout.LayoutParams(-1, dp(32));
        alertTitleLp.setMargins(0, dp(14), 0, dp(2));
        root.addView(alertsTitle, alertTitleLp);

        LinearLayout chips = new LinearLayout(this);
        chips.setOrientation(LinearLayout.HORIZONTAL);
        chips.setGravity(Gravity.CENTER);
        int[] values = {80, 100, 120};
        int[] colors = {CYAN, Color.rgb(255, 174, 0), PINK};
        for (int i = 0; i < values.length; i++) {
            TextView chip = text(values[i] + " km/h", 13, colors[i], true);
            chip.setBackground(shape(Color.rgb(18, 25, 43), colors[i], 1, 18));
            chip.setPadding(dp(4), dp(8), dp(4), dp(8));
            LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(0, dp(42), 1);
            cp.setMargins(dp(3), dp(3), dp(3), dp(3));
            chips.addView(chip, cp);
        }
        root.addView(chips, new LinearLayout.LayoutParams(-1, dp(48)));
        root.addView(text("MANTENHA A LOCALIZAÇÃO ATIVA", 9,
                Color.rgb(88, 104, 134), false),
                new LinearLayout.LayoutParams(-1, dp(28)));
        setContentView(scroll);
    }

    private int dp(float value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private void startGps() {
        if (locationManager == null) return;
        try {
            if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
                    checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                statusText.setText("PERMITA O ACESSO À LOCALIZAÇÃO");
                return;
            }
            if (!locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                statusText.setText("ATIVE A LOCALIZAÇÃO / GPS");
                gpsDot.setTextColor(PINK);
                return;
            }
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 0f,
                    listener, Looper.getMainLooper());
            Location last = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            if (last != null) listener.onLocationChanged(last);
        } catch (SecurityException e) {
            statusText.setText("PERMISSÃO DE GPS NECESSÁRIA");
        } catch (RuntimeException e) {
            statusText.setText("ERRO AO INICIAR GPS");
            gpsDot.setTextColor(PINK);
        }
    }

    private void handleAlerts(int speed) {
        int level = speed >= 120 ? 3 : speed >= 100 ? 2 : speed >= 80 ? 1 : 0;
        if (level == 0) {
            lastAlert = 0;
            return;
        }
        long now = System.currentTimeMillis();
        if (level > lastAlert && now - lastAlertTime > 1800) {
            try {
                if (tones == null) tones = new ToneGenerator(AudioManager.STREAM_ALARM, 75);
                int tone = level == 1 ? ToneGenerator.TONE_PROP_BEEP :
                        level == 2 ? ToneGenerator.TONE_PROP_ACK : ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD;
                tones.startTone(tone, level == 3 ? 500 : 200);
            } catch (RuntimeException ignored) {
                // Audio alerts are optional; a sound failure must not close the speedometer.
            }
            lastAlert = level;
            lastAlertTime = now;
        }
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode != REQ_LOCATION) return;
        if (results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) startGps();
        else statusText.setText("PERMITA O ACESSO À LOCALIZAÇÃO");
    }

    @Override protected void onDestroy() {
        if (locationManager != null) {
            try { locationManager.removeUpdates(listener); } catch (RuntimeException ignored) {}
        }
        if (tones != null) {
            try { tones.release(); } catch (RuntimeException ignored) {}
            tones = null;
        }
        super.onDestroy();
    }
}