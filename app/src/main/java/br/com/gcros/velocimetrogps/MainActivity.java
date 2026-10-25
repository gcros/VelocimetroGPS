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
import android.view.inputmethod.InputMethodManager;
import android.content.Context;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.text.TextUtils;

import org.json.JSONArray;
import org.json.JSONObject;
import org.osmdroid.config.Configuration;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.Polyline;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private static final int REQ_LOCATION = 41;
    private static final int BG = Color.rgb(8, 10, 22), PANEL = Color.rgb(17, 21, 40);
    private static final int CYAN = Color.rgb(0, 220, 240), PINK = Color.rgb(255, 45, 155);
    private static final int MUTED = Color.rgb(145, 160, 190);
    private LocationManager locationManager;
    private TextView speedText, statusText, gpsDot, navInfo;
    private MapView map;
    private FrameLayout content;
    private LinearLayout speedScreen, mapScreen;
    private EditText destinationInput;
    private ToneGenerator tones;
    private int lastAlert;
    private long lastAlertTime;
    private Location currentLocation;
    private Polyline routeLine;
    private Marker userMarker;
    private final ExecutorService network = Executors.newSingleThreadExecutor();

    private final LocationListener listener = new LocationListener() {
        @Override public void onLocationChanged(Location loc) {
            currentLocation = loc;
            if (speedText == null) return;
            if (loc.hasAccuracy() && loc.getAccuracy() > 60) {
                statusText.setText("SINAL GPS FRACO • ±" + (int)loc.getAccuracy() + " m");
                gpsDot.setTextColor(PINK);
                return;
            }
            int speed = Math.max(0, (int)Math.round(loc.hasSpeed() ? loc.getSpeed()*3.6 : 0));
            speedText.setText(String.valueOf(speed));
            statusText.setText("GPS ATIVO • PRECISÃO ±" + (loc.hasAccuracy() ? (int)loc.getAccuracy() : "?") + " m");
            gpsDot.setTextColor(CYAN);
            handleAlerts(speed);
            if (map != null) {
                GeoPoint p = new GeoPoint(loc.getLatitude(), loc.getLongitude());
                if (userMarker == null) {
                    userMarker = new Marker(map); userMarker.setTitle("Você"); userMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
                    map.getOverlays().add(userMarker);
                }
                userMarker.setPosition(p);
                if (map.getZoomLevelDouble() < 14) map.getController().setZoom(16.0);
                map.getController().animateTo(p);
                map.invalidate();
            }
        }
        @Override public void onProviderEnabled(String p) { if(statusText!=null)statusText.setText("GPS ATIVO • BUSCANDO SINAL"); }
        @Override public void onProviderDisabled(String p) { if(statusText!=null)statusText.setText("LOCALIZAÇÃO DESATIVADA"); if(gpsDot!=null)gpsDot.setTextColor(PINK); }
        @Override public void onStatusChanged(String p,int s,Bundle b) {}
    };

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().setStatusBarColor(BG); getWindow().setNavigationBarColor(BG);
        Configuration.getInstance().setUserAgentValue(getPackageName());
        Configuration.getInstance().setTileFileSystemCacheMaxBytes(150L * 1024 * 1024);
        buildUi();
        locationManager = (LocationManager)getSystemService(LOCATION_SERVICE);
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED &&
            checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)!=PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION},REQ_LOCATION);
        } else startGps();
    }

    private int dp(float v) { return (int)(v*getResources().getDisplayMetrics().density+0.5f); }
    private GradientDrawable shape(int color,int strokeColor,int stroke,int radius) {
        GradientDrawable d=new GradientDrawable(); d.setColor(color); d.setCornerRadius(dp(radius));
        if(stroke>0)d.setStroke(dp(stroke),strokeColor); return d;
    }
    private TextView text(String value,int size,int color,boolean bold) {
        TextView t=new TextView(this); t.setText(value); t.setTextColor(color); t.setTextSize(size); t.setGravity(Gravity.CENTER);
        t.setTypeface(Typeface.create(bold?"sans-serif-condensed":"sans-serif",bold?Typeface.BOLD:Typeface.NORMAL));
        t.setIncludeFontPadding(false); return t;
    }
    private Button button(String label) {
        Button b=new Button(this); b.setText(label); b.setTextColor(Color.WHITE); b.setAllCaps(false);
        b.setTextSize(13); b.setBackground(shape(Color.rgb(20,36,58),CYAN,1,14)); return b;
    }
    private LinearLayout column() { LinearLayout l=new LinearLayout(this); l.setOrientation(LinearLayout.VERTICAL); return l; }

    private void buildUi() {
        content=new FrameLayout(this); content.setBackgroundColor(BG);
        speedScreen=column(); speedScreen.setGravity(Gravity.CENTER_HORIZONTAL); speedScreen.setPadding(dp(18),dp(20),dp(18),dp(16));
        speedScreen.addView(text("N E O N  /  D R I V E",13,CYAN,true),new LinearLayout.LayoutParams(-1,dp(26)));
        speedScreen.addView(text("VELOCÍMETRO GPS",24,Color.WHITE,true),new LinearLayout.LayoutParams(-1,dp(38)));
        speedScreen.addView(text("MONITOR DE VELOCIDADE",10,MUTED,false),new LinearLayout.LayoutParams(-1,dp(22)));
        LinearLayout panel=column(); panel.setGravity(Gravity.CENTER); panel.setBackground(shape(PANEL,Color.rgb(31,48,78),1,26));
        panel.setPadding(dp(12),dp(18),dp(12),dp(18));
        LinearLayout.LayoutParams pp=new LinearLayout.LayoutParams(-1,0,1); pp.setMargins(0,dp(14),0,dp(12)); speedScreen.addView(panel,pp);
        panel.addView(text("VELOCIDADE ATUAL",11,MUTED,true),new LinearLayout.LayoutParams(-1,dp(28)));
        int size=Math.max(dp(190),Math.min(getResources().getDisplayMetrics().widthPixels-dp(80),dp(280)));
        LinearLayout dial=column(); dial.setGravity(Gravity.CENTER);
        GradientDrawable ring=new GradientDrawable(); ring.setShape(GradientDrawable.OVAL); ring.setColor(Color.rgb(11,17,32)); ring.setStroke(dp(4),CYAN); dial.setBackground(ring);
        LinearLayout.LayoutParams dl=new LinearLayout.LayoutParams(size,size); dl.gravity=Gravity.CENTER; dl.setMargins(0,dp(8),0,dp(12)); panel.addView(dial,dl);
        dial.addView(text("KM / H",12,PINK,true),new LinearLayout.LayoutParams(-1,dp(30)));
        speedText=text("—",82,Color.WHITE,true); speedText.setShadowLayer(dp(10),0,0,Color.rgb(0,100,150)); dial.addView(speedText,new LinearLayout.LayoutParams(-1,0,1));
        dial.addView(text("km/h",17,CYAN,true),new LinearLayout.LayoutParams(-1,dp(32)));
        LinearLayout status=new LinearLayout(this); status.setGravity(Gravity.CENTER);
        gpsDot=text("●",12,MUTED,true); status.addView(gpsDot,new LinearLayout.LayoutParams(dp(22),dp(30)));
        statusText=text("AGUARDANDO SINAL GPS…",11,MUTED,false); status.addView(statusText,new LinearLayout.LayoutParams(0,dp(30),1));
        panel.addView(status,new LinearLayout.LayoutParams(-1,dp(34)));
        panel.addView(text("ALERTAS: 80  •  100  •  120 km/h",12,MUTED,true),new LinearLayout.LayoutParams(-1,dp(36)));
        Button openMap=button("⌖  ABRIR MAPA E NAVEGAÇÃO"); openMap.setOnClickListener(v->showMap());
        speedScreen.addView(openMap,new LinearLayout.LayoutParams(-1,dp(52)));
        mapScreen=column(); mapScreen.setPadding(dp(10),dp(10),dp(10),dp(10));
        LinearLayout top=new LinearLayout(this); top.setGravity(Gravity.CENTER_VERTICAL);
        Button back=button("‹ Velocímetro"); back.setOnClickListener(v->showSpeed());
        top.addView(back,new LinearLayout.LayoutParams(dp(135),dp(48)));
        top.addView(text("MAPA OSM",17,CYAN,true),new LinearLayout.LayoutParams(0,dp(48),1));
        mapScreen.addView(top);
        LinearLayout searchRow=new LinearLayout(this); searchRow.setGravity(Gravity.CENTER_VERTICAL);
        destinationInput=new EditText(this); destinationInput.setSingleLine(true); destinationInput.setTextColor(Color.WHITE);
        destinationInput.setHintTextColor(MUTED); destinationInput.setHint("Destino (cidade, endereço ou local)");
        destinationInput.setTextSize(14); destinationInput.setPadding(dp(12),0,dp(8),0); destinationInput.setBackground(shape(PANEL,Color.rgb(40,70,95),1,12));
        searchRow.addView(destinationInput,new LinearLayout.LayoutParams(0,dp(50),1));
        Button go=button("IR"); go.setOnClickListener(v->calculateRoute());
        LinearLayout.LayoutParams gp=new LinearLayout.LayoutParams(dp(65),dp(50)); gp.setMargins(dp(6),0,0,0); searchRow.addView(go,gp);
        mapScreen.addView(searchRow,new LinearLayout.LayoutParams(-1,dp(56)));
        navInfo=text("Mapa gratuito • tiles vistos ficam em cache para uso sem internet",11,MUTED,false);
        navInfo.setGravity(Gravity.CENTER_VERTICAL); mapScreen.addView(navInfo,new LinearLayout.LayoutParams(-1,dp(34)));
        map=new MapView(this); map.setTileSource(TileSourceFactory.MAPNIK); map.setMultiTouchControls(true);
        map.setTilesScaledToDpi(true); map.getController().setZoom(15.0);
        GeoPoint initial=new GeoPoint(-30.9,-53.25); map.getController().setCenter(initial);
        mapScreen.addView(map,new LinearLayout.LayoutParams(-1,0,1));
        Button gpsBtn=button("◎  CENTRALIZAR NO MEU GPS"); gpsBtn.setOnClickListener(v->{if(currentLocation!=null)map.getController().animateTo(new GeoPoint(currentLocation.getLatitude(),currentLocation.getLongitude()));else Toast.makeText(this,"Aguardando sinal GPS",Toast.LENGTH_SHORT).show();});
        LinearLayout.LayoutParams glp=new LinearLayout.LayoutParams(-1,dp(48)); glp.setMargins(0,dp(6),0,0); mapScreen.addView(gpsBtn,glp);
        content.addView(speedScreen); setContentView(content);
    }
    private void showMap() { content.removeAllViews(); content.addView(mapScreen); if(map!=null)map.onResume(); if(currentLocation!=null)map.getController().animateTo(new GeoPoint(currentLocation.getLatitude(),currentLocation.getLongitude())); }
    private void showSpeed() { if(map!=null)map.onPause(); content.removeAllViews(); content.addView(speedScreen); }
    private void startGps() {
        try {
            if(checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED &&
               checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)!=PackageManager.PERMISSION_GRANTED)return;
            if(!locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)){statusText.setText("ATIVE A LOCALIZAÇÃO / GPS");gpsDot.setTextColor(PINK);return;}
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER,1000L,0f,listener,Looper.getMainLooper());
            Location last=locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER); if(last!=null)listener.onLocationChanged(last);
        } catch(Exception e){statusText.setText("ERRO AO INICIAR GPS");}
    }
    private void calculateRoute() {
        String dest=destinationInput.getText().toString().trim();
        if(TextUtils.isEmpty(dest)){destinationInput.setError("Digite o destino");return;}
        if(currentLocation==null){Toast.makeText(this,"Aguardando localização GPS para traçar a rota",Toast.LENGTH_LONG).show();return;}
        ((InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(destinationInput.getWindowToken(),0);
        navInfo.setText("Buscando destino e calculando rota…");
        network.execute(()->{
            try {
                String q=URLEncoder.encode(dest,"UTF-8");
                JSONObject geo=getJson("https://nominatim.openstreetmap.org/search?format=jsonv2&limit=1&q="+q);
                JSONArray found=geo.optJSONArray("items");
                if(found==null||found.length()==0)throw new Exception("Destino não encontrado");
                JSONObject target=found.getJSONObject(0);
                double lat=target.getDouble("lat"), lon=target.getDouble("lon");
                Location origin=currentLocation;
                JSONObject route=getJson("https://router.project-osrm.org/route/v1/driving/"+origin.getLongitude()+","+origin.getLatitude()+";"+lon+","+lat+"?overview=full&geometries=geojson&steps=true");
                JSONArray routes=route.getJSONArray("routes"); if(routes.length()==0)throw new Exception("Rota indisponível");
                JSONObject r=routes.getJSONObject(0); JSONArray coords=r.getJSONObject("geometry").getJSONArray("coordinates");
                java.util.ArrayList<GeoPoint> points=new java.util.ArrayList<>();
                for(int i=0;i<coords.length();i++){JSONArray c=coords.getJSONArray(i);points.add(new GeoPoint(c.getDouble(1),c.getDouble(0)));}
                double km=r.getDouble("distance")/1000.0; int mins=(int)Math.ceil(r.getDouble("duration")/60.0);
                runOnUiThread(()->{
                    if(routeLine!=null)map.getOverlays().remove(routeLine);
                    routeLine=new Polyline(); routeLine.setPoints(points); routeLine.setColor(CYAN); routeLine.setWidth(dp(6)); map.getOverlays().add(routeLine);
                    Marker end=new Marker(map); end.setPosition(new GeoPoint(lat,lon)); end.setTitle(target.optString("display_name",dest)); end.setAnchor(Marker.ANCHOR_CENTER,Marker.ANCHOR_BOTTOM); map.getOverlays().add(end);
                    map.getController().setCenter(points.get(points.size()/2)); map.getController().setZoom(10.0); map.invalidate();
                    navInfo.setText(String.format(Locale.getDefault(),"Rota: %.1f km • cerca de %d min",km,mins));
                    Toast.makeText(this,"Rota calculada. Siga a linha no mapa.",Toast.LENGTH_LONG).show();
                });
            } catch(Exception e){runOnUiThread(()->navInfo.setText("Não foi possível calcular: "+e.getMessage()));}
        });
    }
    private JSONObject getJson(String address) throws Exception {
        HttpURLConnection c=(HttpURLConnection)new URL(address).openConnection(); c.setConnectTimeout(12000); c.setReadTimeout(15000);
        c.setRequestProperty("User-Agent","VelocimetroGPS/2.0 (Android OSM navigation)");
        try(InputStream in=c.getInputStream(); BufferedReader br=new BufferedReader(new InputStreamReader(in,"UTF-8"))){
            StringBuilder sb=new StringBuilder(); String line; while((line=br.readLine())!=null)sb.append(line);
            String s=sb.toString(); if(s.startsWith("[")){JSONArray a=new JSONArray(s);JSONObject o=new JSONObject();o.put("items",a);return o;}
            return new JSONObject(s);
        } finally {c.disconnect();}
    }
    private void handleAlerts(int speed) {
        int level=speed>=120?3:speed>=100?2:speed>=80?1:0;
        if(level==0){lastAlert=0;return;} long now=System.currentTimeMillis();
        if(level>lastAlert&&now-lastAlertTime>1800)try{
            if(tones==null)tones=new ToneGenerator(AudioManager.STREAM_ALARM,75);
            tones.startTone(level==1?ToneGenerator.TONE_PROP_BEEP:level==2?ToneGenerator.TONE_PROP_ACK:ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD,level==3?500:200);
            lastAlert=level;lastAlertTime=now;
        }catch(RuntimeException ignored){}
    }
    @Override public void onRequestPermissionsResult(int requestCode,String[] permissions,int[] results){
        super.onRequestPermissionsResult(requestCode,permissions,results);
        if(requestCode==REQ_LOCATION&&results.length>0&&results[0]==PackageManager.PERMISSION_GRANTED)startGps();
        else if(requestCode==REQ_LOCATION)statusText.setText("PERMITA O ACESSO À LOCALIZAÇÃO");
    }
    @Override protected void onResume(){super.onResume();if(map!=null)map.onResume();}
    @Override protected void onPause(){if(map!=null)map.onPause();super.onPause();}
    @Override protected void onDestroy(){
        if(locationManager!=null)try{locationManager.removeUpdates(listener);}catch(Exception ignored){}
        if(tones!=null)try{tones.release();}catch(Exception ignored){}
        network.shutdownNow(); if(map!=null)map.onDetach(); super.onDestroy();
    }
}