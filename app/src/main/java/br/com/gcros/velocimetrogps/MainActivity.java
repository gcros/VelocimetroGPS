package br.com.gcros.velocimetrogps;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
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
    private LocationManager locationManager;
    private TextView speedText, statusText;
    private ToneGenerator tones;
    private int lastAlert = 0;
    private long lastAlertTime = 0;

    private final LocationListener listener = new LocationListener() {
        @Override public void onLocationChanged(Location location) {
            if (location.hasAccuracy() && location.getAccuracy() > 60) {
                statusText.setText("SINAL GPS FRACO");
                return;
            }
            double kmh = location.hasSpeed() ? location.getSpeed() * 3.6 : 0;
            int speed = Math.max(0, (int)Math.round(kmh));
            speedText.setText(String.valueOf(speed));
            statusText.setText("GPS • km/h");
            handleAlerts(speed);
        }
        @Override public void onProviderEnabled(String provider) {}
        @Override public void onProviderDisabled(String provider) {
            statusText.setText("ATIVE A LOCALIZAÇÃO");
        }
        @Override public void onStatusChanged(String provider, int status, Bundle extras) {}
    };

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().getDecorView().setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_LAYOUT_STABLE |
            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
        tones = new ToneGenerator(AudioManager.STREAM_ALARM, 90);
        buildUi();
        locationManager = (LocationManager)getSystemService(LOCATION_SERVICE);
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, REQ_LOCATION);
        } else startGps();
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setBackgroundColor(Color.rgb(15,15,17));
        root.setPadding(24, 18, 24, 18);

        TextView title = new TextView(this);
        title.setText("VELOCIDADE");
        title.setTextColor(Color.WHITE);
        title.setTextSize(20);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        title.setGravity(Gravity.CENTER);
        root.addView(title, new LinearLayout.LayoutParams(-1, 0, 1));

        LinearLayout sign = new LinearLayout(this);
        sign.setOrientation(LinearLayout.VERTICAL);
        sign.setGravity(Gravity.CENTER);
        sign.setBackgroundColor(Color.WHITE);
        sign.setPadding(8, 8, 8, 8);
        sign.setElevation(12);
        android.graphics.drawable.GradientDrawable border = new android.graphics.drawable.GradientDrawable();
        border.setShape(android.graphics.drawable.GradientDrawable.OVAL);
        border.setColor(Color.WHITE);
        border.setStroke(12, Color.rgb(210, 25, 32));
        sign.setBackground(border);

        speedText = new TextView(this);
        speedText.setText("—");
        speedText.setTextColor(Color.rgb(20,20,20));
        speedText.setTextSize(100);
        speedText.setTypeface(Typeface.create("sans-serif-condensed", Typeface.BOLD));
        speedText.setGravity(Gravity.CENTER);
        sign.addView(speedText, new LinearLayout.LayoutParams(-1, 0, 1));

        TextView unit = new TextView(this);
        unit.setText("km/h");
        unit.setTextColor(Color.rgb(20,20,20));
        unit.setTextSize(25);
        unit.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        unit.setGravity(Gravity.CENTER);
        sign.addView(unit, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout.LayoutParams signParams = new LinearLayout.LayoutParams(0, 0, 5);
        signParams.width = (int)(getResources().getDisplayMetrics().widthPixels * 0.78f);
        signParams.height = signParams.width;
        signParams.gravity = Gravity.CENTER;
        root.addView(sign, signParams);

        statusText = new TextView(this);
        statusText.setText("AGUARDANDO GPS…");
        statusText.setTextColor(Color.LTGRAY);
        statusText.setTextSize(14);
        statusText.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams statusParams = new LinearLayout.LayoutParams(-1, 0, 1);
        statusParams.topMargin = 10;
        root.addView(statusText, statusParams);
        setContentView(root);
    }

    private void startGps() {
        try {
            if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
                checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) return;
            boolean gps = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
            if (!gps) {
                statusText.setText("ATIVE A LOCALIZAÇÃO/GPS");
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
        if (level == 0) { lastAlert = 0; return; }
        long now = System.currentTimeMillis();
        if (level > lastAlert && now - lastAlertTime > 1800) {
            if (level == 1) tones.startTone(ToneGenerator.TONE_PROP_BEEP, 180);
            else if (level == 2) { tones.startTone(ToneGenerator.TONE_PROP_ACK, 160); }
            else { tones.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 650); }
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
