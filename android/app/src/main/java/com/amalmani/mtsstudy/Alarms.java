package com.amalmani.mtsstudy;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.Calendar;

/** Daily study reminders (routine blocks). They ring even when the app is closed, and come back after a restart. */
public class Alarms extends BroadcastReceiver {
    static final String PREFS = "mts", KEY = "alarms", CHANNEL = "study_alarm";

    static void save(Context c, String json) {
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, json).apply();
    }

    static JSONArray saved(Context c) {
        try { return new JSONArray(c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "[]")); }
        catch (Exception e) { return new JSONArray(); }
    }

    static PendingIntent pi(Context c, int i, JSONObject b) {
        Intent it = new Intent(c, Alarms.class).setAction("com.amalmani.mtsstudy.ALARM_" + i)
                .putExtra("i", i).putExtra("label", b.optString("label")).putExtra("id", b.optString("id"))
                .putExtra("from", b.optString("from")).putExtra("to", b.optString("to"));
        return PendingIntent.getBroadcast(c, 100 + i, it, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    static void scheduleSaved(Context c) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        for (int i = 0; i < 6; i++) am.cancel(pi(c, i, new JSONObject()));
        JSONArray a = saved(c);
        for (int i = 0; i < a.length() && i < 6; i++) {
            JSONObject b = a.optJSONObject(i);
            if (b != null) schedule(c, am, i, b);
        }
    }

    static void schedule(Context c, AlarmManager am, int i, JSONObject b) {
        String[] hm = b.optString("from", "").split(":");
        if (hm.length < 2) return;
        Calendar t = Calendar.getInstance();
        try {
            t.set(Calendar.HOUR_OF_DAY, Integer.parseInt(hm[0]));
            t.set(Calendar.MINUTE, Integer.parseInt(hm[1]));
        } catch (NumberFormatException e) { return; }
        t.set(Calendar.SECOND, 0);
        t.set(Calendar.MILLISECOND, 0);
        if (t.getTimeInMillis() <= System.currentTimeMillis() + 1000) t.add(Calendar.DAY_OF_MONTH, 1);
        PendingIntent p = pi(c, i, b);
        boolean exact = Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms();
        try {
            if (exact) {
                PendingIntent show = PendingIntent.getActivity(c, 200 + i, new Intent(c, MainActivity.class),
                        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
                am.setAlarmClock(new AlarmManager.AlarmClockInfo(t.getTimeInMillis(), show), p);
            } else if (Build.VERSION.SDK_INT >= 23) {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, t.getTimeInMillis(), p);
            } else {
                am.set(AlarmManager.RTC_WAKEUP, t.getTimeInMillis(), p);
            }
        } catch (SecurityException e) {
            am.set(AlarmManager.RTC_WAKEUP, t.getTimeInMillis(), p);
        }
    }

    @Override
    public void onReceive(Context c, Intent intent) {
        String action = intent.getAction() == null ? "" : intent.getAction();
        if (action.equals(Intent.ACTION_BOOT_COMPLETED) || action.equals("android.intent.action.MY_PACKAGE_REPLACED")) {
            scheduleSaved(c);
            return;
        }
        String id = intent.getStringExtra("id"), label = intent.getStringExtra("label");
        String from = intent.getStringExtra("from"), to = intent.getStringExtra("to");
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        Uri sound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
        if (Build.VERSION.SDK_INT >= 26 && nm != null && nm.getNotificationChannel(CHANNEL) == null) {
            NotificationChannel ch = new NotificationChannel(CHANNEL, "Study routine", NotificationManager.IMPORTANCE_HIGH);
            ch.setDescription("Morning study, daily mock and evening study reminders");
            ch.setSound(sound, new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build());
            ch.enableVibration(true);
            nm.createNotificationChannel(ch);
        }
        String hash = "am".equals(id) ? "wake" : "mock".equals(id) ? "practice" : "myday";
        Intent open = new Intent(c, MainActivity.class).putExtra("hash", hash)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent tap = PendingIntent.getActivity(c, 300 + intent.getIntExtra("i", 0), open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        String text = "am".equals(id) ? "Wake up! Answer the 3-question wake check and start studying (" + from + "–" + to + ")."
                : "mock".equals(id) ? "Time for today's compulsory mock test (" + from + ")."
                : "Evening study time (" + from + "–" + to + "). Open the app and press Start.";
        Notification.Builder nb = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(c, CHANNEL) : new Notification.Builder(c);
        nb.setSmallIcon(android.R.drawable.ic_lock_idle_alarm).setContentTitle("MTS Study: " + (label == null ? "Study time" : label))
                .setContentText(text).setStyle(new Notification.BigTextStyle().bigText(text))
                .setContentIntent(tap).setAutoCancel(true).setCategory(Notification.CATEGORY_ALARM);
        if (Build.VERSION.SDK_INT < 26) nb.setSound(sound).setPriority(Notification.PRIORITY_MAX).setDefaults(Notification.DEFAULT_VIBRATE);
        try { if (nm != null) nm.notify(500 + intent.getIntExtra("i", 0), nb.build()); } catch (SecurityException e) { }
        scheduleSaved(c);  // set tomorrow's reminders
    }
}
