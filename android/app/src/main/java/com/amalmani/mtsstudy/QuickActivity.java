package com.amalmani.mtsstudy;

import android.app.Activity;
import android.app.NotificationManager;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.Locale;

/** Small answer window for the pop-up quiz. Opens over any app; the main app stays closed. */
public class QuickActivity extends Activity {
    static final int BG = 0xFF171B23, INK = 0xFFEEF1F6, MUTED = 0xFF9AA3B2, ACC = 0xFFFF7A59, OK = 0xFF59D499, BAD = 0xFFFF6B6B, TRICK = 0xFFFFC65C;
    LinearLayout box;
    TextToSpeech tts;
    JSONObject q;
    boolean answered;

    int dp(int v) { return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics()); }

    GradientDrawable round(int fill, int stroke) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill); g.setCornerRadius(dp(12));
        if (stroke != 0) g.setStroke(dp(2), stroke);
        return g;
    }

    TextView text(String s, float sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s); t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp); t.setTextColor(color);
        t.setLineSpacing(0, 1.25f);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setPadding(0, dp(6), 0, dp(6));
        return t;
    }

    Button btn(String s, int fill, int ink) {
        Button b = new Button(this);
        b.setText(s); b.setAllCaps(false); b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16); b.setTextColor(ink);
        b.setBackground(round(fill, 0)); b.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        b.setPadding(dp(14), dp(12), dp(14), dp(12));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, dp(6), 0, dp(6));
        b.setLayoutParams(lp);
        return b;
    }

    View card(String head, String body, int color) {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable g = round((color & 0x00FFFFFF) | 0x22000000, 0);
        c.setBackground(g);
        c.setPadding(dp(14), dp(10), dp(14), dp(10));
        c.addView(text(head, 15, color, true));
        c.addView(text(body, 17, INK, false));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, dp(8), 0, dp(4));
        c.setLayoutParams(lp);
        return c;
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) nm.cancel(QuickQuiz.NOTE_ID);
        String key = getIntent().getStringExtra("key");
        try { q = new JSONObject(QuickQuiz.p(this).getString("q_" + key, "")); } catch (Exception e) { q = QuickQuiz.next(this); }
        tts = new TextToSpeech(this, s -> { if (s == TextToSpeech.SUCCESS) tts.setLanguage(new Locale("en", "IN")); });
        ScrollView sv = new ScrollView(this);
        sv.setBackground(round(BG, 0x33FFFFFF));
        box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(18), dp(16), dp(18), dp(16));
        sv.addView(box);
        setContentView(sv);
        getWindow().setLayout((int) (getResources().getDisplayMetrics().widthPixels * 0.94), ViewGroup.LayoutParams.WRAP_CONTENT);
        getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        draw();
    }

    void draw() {
        box.removeAllViews();
        answered = false;
        if (q == null) {
            box.addView(text("No questions yet. Open the MTS Study app once so it can load your questions.", 17, INK, false));
            Button c = btn("Close", ACC, 0xFF1A0D08); c.setOnClickListener(v -> finish()); box.addView(c);
            return;
        }
        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        TextView h = text("Quick question · " + QuickQuiz.subj(q.optString("s")), 14, MUTED, true);
        top.addView(h, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        Button say = new Button(this);
        say.setText("🔊 Read"); say.setAllCaps(false); say.setTextColor(INK); say.setBackground(round(0xFF232A36, 0));
        say.setOnClickListener(v -> speak(readText()));
        top.addView(say);
        box.addView(top);
        box.addView(text(q.optString("q"), 19, INK, true));
        JSONArray o = q.optJSONArray("o");
        final Button[] bs = new Button[4];
        for (int i = 0; o != null && i < o.length() && i < 4; i++) {
            final int k = i;
            bs[i] = btn("ABCD".charAt(i) + "   " + o.optString(i), 0xFF232A36, INK);
            bs[i].setOnClickListener(v -> choose(k, bs));
            box.addView(bs[i]);
        }
        Button close = btn("Not now", 0x00000000, MUTED);
        close.setGravity(Gravity.CENTER);
        close.setOnClickListener(v -> finish());
        box.addView(close);
    }

    String readText() {
        StringBuilder s = new StringBuilder(q.optString("q"));
        JSONArray o = q.optJSONArray("o");
        for (int i = 0; o != null && i < o.length(); i++) s.append(". Option ").append("ABCD".charAt(i)).append(": ").append(o.optString(i));
        return s.toString();
    }

    void speak(String s) { if (tts != null) tts.speak(s, TextToSpeech.QUEUE_FLUSH, null, "qq"); }

    void choose(int i, Button[] bs) {
        if (answered) return;
        answered = true;
        int a = q.optInt("a", 0);
        QuickQuiz.addResult(this, q, i);
        for (int k = 0; k < 4; k++) {
            if (bs[k] == null) continue;
            bs[k].setEnabled(false);
            if (k == a) bs[k].setBackground(round(0x3359D499, OK));
            else if (k == i) bs[k].setBackground(round(0x33FF6B6B, BAD));
        }
        // remove "Not now"
        box.removeViewAt(box.getChildCount() - 1);
        JSONArray o = q.optJSONArray("o");
        String right = o == null ? "" : o.optString(a);
        if (i == a) box.addView(text("Correct! Well done.", 18, OK, true));
        else {
            box.addView(text("Answer: " + "ABCD".charAt(a) + ". " + right, 18, OK, true));
            JSONArray w = q.optJSONArray("w");
            String why = w != null && !w.isNull(i) ? w.optString(i, "") : "";
            if (why.isEmpty()) why = "QUANT".equalsIgnoreCase(q.optString("s")) ? "Your answer does not match the working. Follow the steps below."
                    : "Read the right answer and the steps. This question comes back in your revision.";
            box.addView(card("Why it went wrong", "You chose " + (o == null ? "" : o.optString(i)) + ". " + why, BAD));
        }
        String e = q.optString("e", "");
        if (!e.isEmpty()) {
            String[] steps = e.split("(?<=[.!?])\\s+(?=[A-Z0-9₹(√\"'])");
            StringBuilder sb = new StringBuilder();
            for (int k = 0; k < steps.length; k++) sb.append(steps.length > 1 ? (k + 1) + ". " : "").append(steps[k].trim()).append(k < steps.length - 1 ? "\n" : "");
            box.addView(card(i == a ? "How to solve it" : "The right answer, step by step", sb.toString(), OK));
        }
        String tr = q.optString("tr", "");
        if (!tr.isEmpty()) box.addView(card("Remember", tr, TRICK));
        final String ml = q.optString("ml", "");
        if (!ml.isEmpty()) {
            final TextView mlT = text(ml, 17, INK, false);
            mlT.setVisibility(View.GONE);
            Button mb = btn("മലയാളം വിശദീകരണം", 0xFF232A36, INK);
            mb.setOnClickListener(v -> mlT.setVisibility(mlT.getVisibility() == View.GONE ? View.VISIBLE : View.GONE));
            box.addView(mb);
            box.addView(mlT);
        }
        Button rd = btn("🔊 Read the answer", 0xFF232A36, INK);
        rd.setOnClickListener(v -> speak("The answer is " + right + ". " + e));
        box.addView(rd);
        Button more = btn("One more question", ACC, 0xFF1A0D08);
        more.setGravity(Gravity.CENTER);
        more.setOnClickListener(v -> { q = QuickQuiz.next(this); draw(); });
        box.addView(more);
        Button done = btn("Close", 0x00000000, MUTED);
        done.setGravity(Gravity.CENTER);
        done.setOnClickListener(v -> finish());
        box.addView(done);
    }

    @Override protected void onDestroy() { if (tts != null) tts.shutdown(); super.onDestroy(); }
}
