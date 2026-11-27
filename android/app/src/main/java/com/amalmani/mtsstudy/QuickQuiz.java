package com.amalmani.mtsstudy;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import org.json.JSONArray;
import org.json.JSONObject;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

/** Pop-up quiz during work hours: one question as a notification; tapping it opens a small answer window. */
public class QuickQuiz extends BroadcastReceiver {
    static final String PREFS = "mtsqq", CHANNEL = "quick_quiz_sound", CHANNEL_QUIET = "quick_quiz_quiet", ACTION = "com.amalmani.mtsstudy.QUICK", DISMISS = "com.amalmani.mtsstudy.QUICK_LATER";
    static final int NOTE_ID = 700;

    static SharedPreferences p(Context c) { return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE); }

    static void save(Context c, String cfg, String qs) {
        boolean same = cfg.equals(p(c).getString("cfg", ""));
        p(c).edit().putString("cfg", cfg).putString("qs", qs).putInt("idx", 0).apply();
        // a new question pack alone must not push the next pop-up later; only a settings change does
        if (!same || p(c).getLong("next", 0) <= System.currentTimeMillis()) schedule(c);
    }

    static JSONObject cfg(Context c) {
        try { return new JSONObject(p(c).getString("cfg", "{}")); } catch (Exception e) { return new JSONObject(); }
    }

    static JSONArray qs(Context c) {
        try { return new JSONArray(p(c).getString("qs", "[]")); } catch (Exception e) { return new JSONArray(); }
    }

    /** next question from the pack (wraps around until the app sends a fresh pack) */
    static JSONObject next(Context c) {
        JSONArray a = qs(c);
        if (a.length() == 0) return null;
        int i = p(c).getInt("idx", 0) % a.length();
        p(c).edit().putInt("idx", i + 1).apply();
        return a.optJSONObject(i);
    }

    static synchronized void addResult(Context c, JSONObject q, int chosen) {
        try {
            JSONObject r = new JSONObject(q.toString());
            r.put("i", chosen);
            r.put("at", new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date()));
            JSONArray a;
            try { a = new JSONArray(p(c).getString("res", "[]")); } catch (Exception e) { a = new JSONArray(); }
            a.put(r);
            JSONArray keep = new JSONArray();
            for (int k = Math.max(0, a.length() - 300); k < a.length(); k++) keep.put(a.get(k));
            p(c).edit().putString("res", keep.toString()).apply();
        } catch (Exception e) { }
    }

    static synchronized String take(Context c) {
        String s = p(c).getString("res", "[]");
        p(c).edit().putString("res", "[]").apply();
        return s;
    }

    static int mins(String hm, int def) {
        try { String[] x = hm.split(":"); return Integer.parseInt(x[0]) * 60 + Integer.parseInt(x[1]); } catch (Exception e) { return def; }
    }

    static boolean dayOn(JSONObject cfg, int dow) { // dow: 0 = Sunday
        JSONArray d = cfg.optJSONArray("days");
        if (d == null) return dow != 0;
        for (int i = 0; i < d.length(); i++) if (d.optInt(i, -1) == dow) return true;
        return false;
    }

    /** time of the next pop-up inside the work window, or -1 when switched off */
    static long nextTime(JSONObject cfg, long now) {
        if (!cfg.optBoolean("on", false)) return -1;
        int from = mins(cfg.optString("from", "10:00"), 600), to = mins(cfg.optString("to", "17:00"), 1020);
        int every = Math.max(5, cfg.optInt("every", 60));
        if (to <= from) return -1;
        for (int d = 0; d < 8; d++) {
            Calendar day = Calendar.getInstance();
            day.setTimeInMillis(now);
            day.add(Calendar.DAY_OF_MONTH, d);
            day.set(Calendar.HOUR_OF_DAY, 0); day.set(Calendar.MINUTE, 0); day.set(Calendar.SECOND, 0); day.set(Calendar.MILLISECOND, 0);
            if (!dayOn(cfg, day.get(Calendar.DAY_OF_WEEK) - 1)) continue;
            long start = day.getTimeInMillis() + from * 60000L, end = day.getTimeInMillis() + to * 60000L;
            long t = d == 0 && now >= start ? now + every * 60000L : start;
            if (d == 0 && now < start) t = start;
            if (t <= end && t > now) return t;
        }
        return -1;
    }

    static PendingIntent alarmPi(Context c) {
        return PendingIntent.getBroadcast(c, 701, new Intent(c, QuickQuiz.class).setAction(ACTION),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    static void schedule(Context c) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        PendingIntent pi = alarmPi(c);
        am.cancel(pi);
        long t = nextTime(cfg(c), System.currentTimeMillis());
        p(c).edit().putLong("next", Math.max(t, 0)).apply();
        if (t < 0) return;
        try {
            if (Build.VERSION.SDK_INT >= 31 && !am.canScheduleExactAlarms()) am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, t, pi);
            else if (Build.VERSION.SDK_INT >= 23) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, t, pi);
            else am.setExact(AlarmManager.RTC_WAKEUP, t, pi);
        } catch (SecurityException e) {
            am.set(AlarmManager.RTC_WAKEUP, t, pi);
        }
    }

    static String subj(String s) {
        switch (s == null ? "" : s) {
            case "Quant": return "Maths";
            case "GA": return "GK";
            case "CA": return "Current affairs";
            default: return s;
        }
    }

    static void post(Context c, JSONObject q) {
        if (q == null) return;
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;
        boolean sound = cfg(c).optBoolean("sound", true);
        String chId = sound ? CHANNEL : CHANNEL_QUIET;
        if (Build.VERSION.SDK_INT >= 26) {
            if (nm.getNotificationChannel("quick_quiz") != null) nm.deleteNotificationChannel("quick_quiz");
            if (nm.getNotificationChannel(CHANNEL) == null) {
                NotificationChannel ch = new NotificationChannel(CHANNEL, "Pop-up quiz (with sound)", NotificationManager.IMPORTANCE_HIGH);
                ch.setDescription("One quick question during work hours, with a sound");
                ch.enableVibration(true);
                ch.setVibrationPattern(new long[]{0, 250, 150, 250});
                ch.setSound(android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_NOTIFICATION),
                        new android.media.AudioAttributes.Builder().setUsage(android.media.AudioAttributes.USAGE_NOTIFICATION)
                                .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION).build());
                nm.createNotificationChannel(ch);
            }
            if (nm.getNotificationChannel(CHANNEL_QUIET) == null) {
                NotificationChannel quiet = new NotificationChannel(CHANNEL_QUIET, "Pop-up quiz (silent)", NotificationManager.IMPORTANCE_DEFAULT);
                quiet.setSound(null, null);
                quiet.enableVibration(false);
                nm.createNotificationChannel(quiet);
            }
        }
        String key = String.valueOf(System.currentTimeMillis());
        p(c).edit().putString("q_" + key, q.toString()).apply();
        Intent open = new Intent(c, QuickActivity.class).putExtra("key", key)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent tap = PendingIntent.getActivity(c, 702, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        PendingIntent later = PendingIntent.getBroadcast(c, 703, new Intent(c, QuickQuiz.class).setAction(DISMISS),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        JSONArray o = q.optJSONArray("o");
        StringBuilder big = new StringBuilder(q.optString("q"));
        if (o != null) for (int i = 0; i < o.length() && i < 4; i++) big.append("\n").append("ABCD".charAt(i)).append(") ").append(o.optString(i));
        Notification.Builder nb = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(c, chId) : new Notification.Builder(c);
        nb.setSmallIcon(android.R.drawable.ic_menu_help).setContentTitle("Quick question · " + subj(q.optString("s")))
                .setContentText(q.optString("q")).setStyle(new Notification.BigTextStyle().bigText(big.toString()))
                .setContentIntent(tap).setAutoCancel(true)
                .addAction(new Notification.Action.Builder(null, "Answer", tap).build())
                .addAction(new Notification.Action.Builder(null, "Not now", later).build());
        if (Build.VERSION.SDK_INT >= 26) nb.setTimeoutAfter(Math.max(5, cfg(c).optInt("every", 60)) * 60000L);
        else nb.setPriority(Notification.PRIORITY_HIGH).setDefaults(sound ? Notification.DEFAULT_SOUND | Notification.DEFAULT_VIBRATE : 0);
        try { nm.notify(NOTE_ID, nb.build()); } catch (SecurityException e) { }
        // keep only the last 5 stored questions
        SharedPreferences.Editor ed = p(c).edit();
        java.util.List<String> ks = new java.util.ArrayList<>();
        for (String k : p(c).getAll().keySet()) if (k.startsWith("q_")) ks.add(k);
        java.util.Collections.sort(ks);
        for (int i = 0; i < ks.size() - 5; i++) ed.remove(ks.get(i));
        ed.apply();
    }

    static void showNow(Context c) { post(c, next(c)); }

    @Override
    public void onReceive(Context c, Intent intent) {
        String a = intent.getAction() == null ? "" : intent.getAction();
        if (DISMISS.equals(a)) {
            NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null) nm.cancel(NOTE_ID);
            return;
        }
        if (ACTION.equals(a)) {
            JSONObject cfg = cfg(c);
            Calendar now = Calendar.getInstance();
            int m = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE);
            if (cfg.optBoolean("on", false) && dayOn(cfg, now.get(Calendar.DAY_OF_WEEK) - 1)
                    && m >= mins(cfg.optString("from", "10:00"), 600) - 1 && m <= mins(cfg.optString("to", "17:00"), 1020))
                post(c, next(c));
        }
        schedule(c);
    }
}
