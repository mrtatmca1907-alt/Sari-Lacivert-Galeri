package com.atmaca.uclsim;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

public class MainActivity extends Activity {

    private static class Team {
        final String name;
        final String country;
        final int pot;
        Team(String name, String country, int pot) {
            this.name = name;
            this.country = country;
            this.pot = pot;
        }
        @Override public String toString() { return name; }
    }

    private static class Match {
        final Team opponent;
        final boolean home;
        Match(Team opponent, boolean home) {
            this.opponent = opponent;
            this.home = home;
        }
    }

    private final Random random = new Random();
    private final List<Team> teams = new ArrayList<>();
    private Spinner teamSpinner;
    private LinearLayout resultsBox;
    private TextView summary;
    private TextView potsText;
    private boolean potsVisible = false;

    private final int NAVY = Color.rgb(6, 18, 45);
    private final int NAVY2 = Color.rgb(12, 31, 69);
    private final int BLUE = Color.rgb(31, 91, 196);
    private final int YELLOW = Color.rgb(244, 196, 48);
    private final int WHITE = Color.rgb(245, 248, 255);
    private final int MUTED = Color.rgb(173, 188, 219);
    private final int GREEN = Color.rgb(43, 155, 100);
    private final int RED = Color.rgb(195, 72, 72);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(NAVY);
        getWindow().setNavigationBarColor(NAVY);
        seedTeams();
        buildUi();
    }

    private void seedTeams() {
        // Pot 1
        add("Paris Saint-Germain", "FRA", 1);
        add("Bayern Münih", "GER", 1);
        add("Real Madrid", "ESP", 1);
        add("Liverpool", "ENG", 1);
        add("Inter", "ITA", 1);
        add("Manchester City", "ENG", 1);
        add("Arsenal", "ENG", 1);
        add("Barcelona", "ESP", 1);
        add("Atletico Madrid", "ESP", 1);

        // Pot 2
        add("Borussia Dortmund", "GER", 2);
        add("Roma", "ITA", 2);
        add("Sporting CP", "POR", 2);
        add("Aston Villa", "ENG", 2);
        add("Porto", "POR", 2);
        add("Manchester United", "ENG", 2);
        add("Club Brugge", "BEL", 2);
        add("Real Betis", "ESP", 2);
        add("PSV", "NED", 2);

        // Pot 3
        add("Feyenoord", "NED", 3);
        add("Lille", "FRA", 3);
        add("Bodo/Glimt", "NOR", 3);
        add("Napoli", "ITA", 3);
        add("RB Leipzig", "GER", 3);
        add("Villarreal", "ESP", 3);
        add("Fenerbahçe", "TUR", 3);
        add("Shakhtar Donetsk", "UKR", 3);
        add("Galatasaray", "TUR", 3);

        // Pot 4
        add("Slavia Prag", "CZE", 4);
        add("Slovan Bratislava", "SVK", 4);
        add("Stuttgart", "GER", 4);
        add("AEK Atina", "GRE", 4);
        add("LASK", "AUT", 4);
        add("Como", "ITA", 4);
        add("Lens", "FRA", 4);
        add("Viking", "NOR", 4);
        add("Sabah", "AZE", 4);
    }

    private void add(String name, String country, int pot) {
        teams.add(new Team(name, country, pot));
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(NAVY);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(18), dp(16), dp(30));
        scroll.addView(root, new ScrollView.LayoutParams(-1, -2));

        TextView title = text("ŞAMPİYONLAR LİGİ\nKURA SİMÜLATÖRÜ", 27, WHITE, true);
        title.setGravity(Gravity.CENTER);
        root.addView(title, lp(-1, -2, 0, 0, 0, 8));

        TextView season = text("2026/27 • 36 takım • 4 torba", 14, YELLOW, true);
        season.setGravity(Gravity.CENTER);
        root.addView(season, lp(-1, -2, 0, 0, 0, 18));

        LinearLayout card = box(NAVY2, 18);
        card.setPadding(dp(14), dp(14), dp(14), dp(14));
        root.addView(card, lp(-1, -2, 0, 0, 0, 14));

        TextView choose = text("Takımını seç", 16, WHITE, true);
        card.addView(choose, lp(-1, -2, 0, 0, 0, 8));

        List<String> names = new ArrayList<>();
        int fenerIndex = 0;
        for (int i = 0; i < teams.size(); i++) {
            Team t = teams.get(i);
            names.add("T" + t.pot + "  •  " + t.name + "  [" + t.country + "]");
            if (t.name.equals("Fenerbahçe")) fenerIndex = i;
        }

        teamSpinner = new Spinner(this);
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_dropdown_item, names) {
            @Override
            public View getView(int position, View convertView, android.view.ViewGroup parent) {
                View v = super.getView(position, convertView, parent);
                if (v instanceof TextView) {
                    ((TextView) v).setTextColor(WHITE);
                    ((TextView) v).setTextSize(16);
                }
                return v;
            }
        };
        teamSpinner.setAdapter(adapter);
        teamSpinner.setSelection(fenerIndex);
        card.addView(teamSpinner, lp(-1, dp(52), 0, 0, 0, 12));

        Button draw = button("🎲  KURA ÇEK", YELLOW, NAVY, true);
        draw.setOnClickListener(v -> runDraw());
        card.addView(draw, lp(-1, dp(54), 0, 0, 0, 8));

        Button showPots = button("Torbalari göster / gizle", BLUE, WHITE, false);
        showPots.setOnClickListener(v -> {
            potsVisible = !potsVisible;
            potsText.setVisibility(potsVisible ? View.VISIBLE : View.GONE);
        });
        card.addView(showPots, lp(-1, dp(48), 0, 0, 0, 0));

        summary = text("Fenerbahçe seçili. Hazırsan kurayı çek 😄", 15, MUTED, false);
        summary.setGravity(Gravity.CENTER);
        root.addView(summary, lp(-1, -2, 0, 2, 0, 12));

        resultsBox = new LinearLayout(this);
        resultsBox.setOrientation(LinearLayout.VERTICAL);
        root.addView(resultsBox, lp(-1, -2, 0, 0, 0, 14));

        potsText = text(buildPotsText(), 13, MUTED, false);
        potsText.setBackground(makeBg(NAVY2, 16));
        potsText.setPadding(dp(14), dp(14), dp(14), dp(14));
        potsText.setVisibility(View.GONE);
        root.addView(potsText, lp(-1, -2, 0, 0, 0, 16));

        TextView rules = text("Simülasyon kuralları: Her torbadan 2 rakip • her torbadan 1 iç saha + 1 deplasman • aynı ülke rakibi yok • aynı ülkeden en fazla 2 rakip. Resmî kura değildir.", 12, MUTED, false);
        rules.setGravity(Gravity.CENTER);
        root.addView(rules, lp(-1, -2, 0, 0, 0, 0));

        setContentView(scroll);
    }

    private void runDraw() {
        Team selected = teams.get(teamSpinner.getSelectedItemPosition());
        List<Match> draw = null;

        for (int attempt = 0; attempt < 300 && draw == null; attempt++) {
            draw = tryDraw(selected);
        }

        if (draw == null) {
            summary.setText("Bu kombinasyonda kura üretilemedi. Tekrar bas.");
            return;
        }

        resultsBox.removeAllViews();
        boolean psg = false;
        boolean bayern = false;

        for (int i = 0; i < draw.size(); i++) {
            Match m = draw.get(i);
            psg |= m.opponent.name.contains("Paris");
            bayern |= m.opponent.name.contains("Bayern");

            LinearLayout row = box(i % 2 == 0 ? Color.rgb(15, 37, 78) : Color.rgb(11, 30, 64), 14);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(12), dp(11), dp(12), dp(11));

            TextView venue = text(m.home ? "🏠" : "✈️", 22, WHITE, false);
            row.addView(venue, lp(dp(42), -2, 0, 0, 8, 0));

            LinearLayout info = new LinearLayout(this);
            info.setOrientation(LinearLayout.VERTICAL);
            row.addView(info, new LinearLayout.LayoutParams(0, -2, 1f));

            TextView opponent = text(m.opponent.name, 17, WHITE, true);
            info.addView(opponent);
            TextView detail = text("Torba " + m.opponent.pot + " • " + m.opponent.country + " • " + (m.home ? "İÇ SAHA" : "DEPLASMAN"), 12, m.home ? GREEN : YELLOW, true);
            info.addView(detail);

            resultsBox.addView(row, lp(-1, -2, 0, 0, 0, 7));
        }

        String mood;
        if (psg && bayern) mood = "PSG + Bayern aynı anda geldi… Allah kolaylık versin 😂";
        else if (psg) mood = "PSG yine çıktı aq 😂";
        else if (bayern) mood = "Bayern geldi, geçmiş olsun 😅";
        else mood = "PSG/Bayern yok. Şimdilik hayattayız 😂";

        summary.setText(selected.name + " için 8 rakip çekildi.\n" + mood);
    }

    private List<Match> tryDraw(Team selected) {
        List<Match> result = new ArrayList<>();
        Map<String, Integer> countryCounts = new HashMap<>();

        for (int pot = 1; pot <= 4; pot++) {
            List<Team> candidates = new ArrayList<>();
            for (Team t : teams) {
                if (t.pot != pot) continue;
                if (t.name.equals(selected.name)) continue;
                if (t.country.equals(selected.country)) continue;
                candidates.add(t);
            }
            Collections.shuffle(candidates, random);

            Team first = null;
            Team second = null;
            for (Team t : candidates) {
                if (countryCounts.getOrDefault(t.country, 0) >= 2) continue;
                if (first == null) {
                    first = t;
                    countryCounts.put(t.country, countryCounts.getOrDefault(t.country, 0) + 1);
                } else if (!t.name.equals(first.name) && countryCounts.getOrDefault(t.country, 0) < 2) {
                    second = t;
                    countryCounts.put(t.country, countryCounts.getOrDefault(t.country, 0) + 1);
                    break;
                }
            }

            if (first == null || second == null) return null;

            if (random.nextBoolean()) {
                result.add(new Match(first, true));
                result.add(new Match(second, false));
            } else {
                result.add(new Match(first, false));
                result.add(new Match(second, true));
            }
        }
        return result;
    }

    private String buildPotsText() {
        StringBuilder b = new StringBuilder();
        for (int pot = 1; pot <= 4; pot++) {
            b.append("TORBA ").append(pot).append("\n");
            for (Team t : teams) {
                if (t.pot == pot) b.append("• ").append(t.name).append(" (").append(t.country).append(")\n");
            }
            if (pot < 4) b.append("\n");
        }
        return b.toString();
    }

    private LinearLayout box(int color, int radiusDp) {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setBackground(makeBg(color, radiusDp));
        return l;
    }

    private GradientDrawable makeBg(int color, int radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(radiusDp));
        return g;
    }

    private Button button(String text, int bg, int fg, boolean bold) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextColor(fg);
        b.setTextSize(15);
        b.setAllCaps(false);
        if (bold) b.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        b.setBackground(makeBg(bg, 14));
        return b;
    }

    private TextView text(String value, int sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(sp);
        t.setTextColor(color);
        t.setLineSpacing(0, 1.08f);
        if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return t;
    }

    private LinearLayout.LayoutParams lp(int w, int h, int left, int top, int right, int bottom) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(w, h);
        p.setMargins(dp(left), dp(top), dp(right), dp(bottom));
        return p;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
