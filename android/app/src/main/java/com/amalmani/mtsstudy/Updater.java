package com.amalmani.mtsstudy;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInstaller;
import android.os.Build;
import android.widget.Toast;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/** Self-update: downloads the new APK from GitHub releases and asks Android to install it over this one. */
public class Updater extends BroadcastReceiver {
    interface Progress { void on(int pct, String msg); }

    static void run(Activity a, String url, Progress p) {
        new Thread(() -> {
            try {
                if (!url.startsWith("https://github.com/amal-mani/amal-ssc-mts/releases/")) throw new Exception("bad link");
                File f = new File(a.getCacheDir(), "update.apk");
                HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
                c.setInstanceFollowRedirects(true);
                c.setConnectTimeout(20000);
                c.setReadTimeout(30000);
                int code = c.getResponseCode();
                if (code >= 300 && code < 400) { // follow GitHub's redirect to the download server
                    String loc = c.getHeaderField("Location");
                    c.disconnect();
                    c = (HttpURLConnection) new URL(loc).openConnection();
                    code = c.getResponseCode();
                }
                if (code != 200) throw new Exception("download error " + code);
                long total = c.getContentLength(), got = 0;
                int last = -1;
                try (InputStream in = c.getInputStream(); OutputStream out = new FileOutputStream(f)) {
                    byte[] b = new byte[65536];
                    int n;
                    while ((n = in.read(b)) > 0) {
                        out.write(b, 0, n);
                        got += n;
                        int pct = total > 0 ? (int) (got * 100 / total) : 0;
                        if (pct != last) { last = pct; p.on(pct, ""); }
                    }
                }
                p.on(100, "installing");
                PackageInstaller pi = a.getPackageManager().getPackageInstaller();
                PackageInstaller.SessionParams sp = new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL);
                int id = pi.createSession(sp);
                try (PackageInstaller.Session s = pi.openSession(id)) {
                    try (OutputStream out = s.openWrite("mts", 0, f.length()); InputStream in = new FileInputStream(f)) {
                        byte[] b = new byte[65536];
                        int n;
                        while ((n = in.read(b)) > 0) out.write(b, 0, n);
                        s.fsync(out);
                    }
                    int flags = PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 31 ? PendingIntent.FLAG_MUTABLE : 0);
                    PendingIntent done = PendingIntent.getBroadcast(a, id, new Intent(a, Updater.class).setAction("com.amalmani.mtsstudy.INSTALLED"), flags);
                    s.commit(done.getIntentSender());
                }
            } catch (Exception e) {
                p.on(-1, e.getMessage() == null ? "error" : e.getMessage());
            }
        }).start();
    }

    @Override
    public void onReceive(Context c, Intent i) {
        int st = i.getIntExtra(PackageInstaller.EXTRA_STATUS, -999);
        if (st == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            Intent confirm = i.getParcelableExtra(Intent.EXTRA_INTENT);
            if (confirm != null) { confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); c.startActivity(confirm); }
        } else if (st != PackageInstaller.STATUS_SUCCESS) {
            String m = i.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE);
            Toast.makeText(c, "Update not installed" + (m == null ? "" : ": " + m), Toast.LENGTH_LONG).show();
        }
    }
}
