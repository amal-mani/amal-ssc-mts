package com.amalmani.mtsstudy;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.media.AudioAttributes;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.os.PowerManager;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.Locale;

/** Listen mode: reads study notes aloud like an audiobook. Keeps playing with the screen off. */
public class ListenService extends Service {
    static final String CH = "listen", A_PLAY = "play", A_TOGGLE = "toggle", A_NEXT = "next", A_PREV = "prev", A_STOP = "stop", A_SPEED = "speed";
    static ListenService self;
    static StatusListener listener;
    interface StatusListener { void on(String json); }

    private TextToSpeech tts;
    private boolean ready = false, playing = false;
    private final ArrayList<String[]> items = new ArrayList<>();
    private int idx = 0;
    private float rate = 0.9f;
    private String title = "MTS Study";
    private PowerManager.WakeLock wl;

    @Override public IBinder onBind(Intent i) { return null; }

    @Override
    public void onCreate() {
        super.onCreate();
        self = this;
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        if (pm != null) { wl = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "mts:listen"); wl.setReferenceCounted(false); }
        tts = new TextToSpeech(this, st -> {
            if (st == TextToSpeech.SUCCESS) {
                tts.setLanguage(new Locale("en", "IN"));
                tts.setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build());
                ready = true;
                if (playing) speakCurrent();
            }
        });
        tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
            @Override public void onStart(String id) { }
            @Override public void onDone(String id) {
                if (!playing || !("i" + idx).equals(id)) return;
                if (idx + 1 < items.size()) { idx++; speakCurrent(); } else { playing = false; update(); stopForegroundCompat(); }
            }
            @Override public void onError(String id) { onDone(id); }
        });
    }

    @Override
    public int onStartCommand(Intent in, int flags, int startId) {
        String a = in == null || in.getAction() == null ? "" : in.getAction();
        try {
            if (A_PLAY.equals(a)) {
                JSONArray arr = new JSONArray(in.getStringExtra("list"));
                items.clear();
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject o = arr.getJSONObject(i);
                    items.add(new String[]{o.optString("title"), o.optString("text")});
                }
                title = in.getStringExtra("name") == null ? "MTS Study" : in.getStringExtra("name");
                idx = Math.max(0, Math.min(in.getIntExtra("start", 0), Math.max(0, items.size() - 1)));
                rate = in.getFloatExtra("rate", rate);
                playing = !items.isEmpty();
                if (playing) speakCurrent();
            } else if (A_TOGGLE.equals(a)) {
                if (playing) { playing = false; tts.stop(); } else if (!items.isEmpty()) { playing = true; speakCurrent(); }
            } else if (A_NEXT.equals(a)) {
                if (idx + 1 < items.size()) { idx++; playing = true; speakCurrent(); }
            } else if (A_PREV.equals(a)) {
                if (idx > 0) idx--; playing = true; speakCurrent();
            } else if (A_SPEED.equals(a)) {
                rate = in.getFloatExtra("rate", rate); if (playing) speakCurrent();
            } else if (A_STOP.equals(a)) {
                playing = false; tts.stop(); items.clear(); update(); stopForegroundCompat(); stopSelf(); return START_NOT_STICKY;
            }
        } catch (Exception e) { }
        update();
        return START_NOT_STICKY;
    }

    private void speakCurrent() {
        if (items.isEmpty()) return;
        if (wl != null && !wl.isHeld()) wl.acquire(3 * 60 * 60 * 1000L);
        update();
        if (!ready) return;
        String[] it = items.get(idx);
        tts.setSpeechRate(rate);
        Bundle b = new Bundle();
        tts.speak(it[0] + ". " + it[1], TextToSpeech.QUEUE_FLUSH, b, "i" + idx);
    }

    private PendingIntent act(String a, int rc) {
        return PendingIntent.getService(this, rc, new Intent(this, ListenService.class).setAction(a), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private void update() {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= 26 && nm != null && nm.getNotificationChannel(CH) == null) {
            NotificationChannel ch = new NotificationChannel(CH, "Listen mode", NotificationManager.IMPORTANCE_LOW);
            ch.setDescription("Study notes read aloud");
            nm.createNotificationChannel(ch);
        }
        String now = items.isEmpty() ? "Finished" : items.get(idx)[0];
        Notification.Builder nb = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(this, CH) : new Notification.Builder(this);
        PendingIntent open = PendingIntent.getActivity(this, 1, new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        nb.setSmallIcon(android.R.drawable.ic_lock_silent_mode_off).setContentTitle("🎧 " + title)
          .setContentText((items.isEmpty() ? 0 : idx + 1) + " of " + items.size() + ": " + now)
          .setContentIntent(open).setOngoing(playing).setVisibility(Notification.VISIBILITY_PUBLIC)
          .addAction(new Notification.Action.Builder(android.R.drawable.ic_media_previous, "Back", act(A_PREV, 11)).build())
          .addAction(new Notification.Action.Builder(playing ? android.R.drawable.ic_media_pause : android.R.drawable.ic_media_play, playing ? "Pause" : "Play", act(A_TOGGLE, 12)).build())
          .addAction(new Notification.Action.Builder(android.R.drawable.ic_media_next, "Next", act(A_NEXT, 13)).build())
          .addAction(new Notification.Action.Builder(android.R.drawable.ic_menu_close_clear_cancel, "Stop", act(A_STOP, 14)).build())
          .setStyle(new Notification.MediaStyle().setShowActionsInCompactView(0, 1, 2));
        Notification n = nb.build();
        try {
            if (playing) {
                if (Build.VERSION.SDK_INT >= 29) startForeground(77, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
                else startForeground(77, n);
            } else if (nm != null) nm.notify(77, n);
        } catch (Exception e) { }
        if (!playing && wl != null && wl.isHeld()) wl.release();
        if (listener != null) {
            try {
                JSONObject s = new JSONObject();
                s.put("playing", playing); s.put("i", idx); s.put("n", items.size()); s.put("title", now); s.put("name", title); s.put("rate", rate);
                listener.on(s.toString());
            } catch (Exception e) { }
        }
    }

    private void stopForegroundCompat() {
        try { if (Build.VERSION.SDK_INT >= 24) stopForeground(STOP_FOREGROUND_DETACH); else stopForeground(false); } catch (Exception e) { }
    }

    @Override
    public void onDestroy() {
        playing = false;
        if (tts != null) tts.shutdown();
        if (wl != null && wl.isHeld()) wl.release();
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (nm != null) nm.cancel(77);
        self = null;
        super.onDestroy();
    }
}
