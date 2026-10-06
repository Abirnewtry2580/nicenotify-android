package com.nicenotify.app;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

public final class MainActivity extends Activity {
    private final int background = Color.rgb(8, 13, 24);
    private final int accent = Color.rgb(34, 211, 238);

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(background);
        getWindow().setNavigationBarColor(background);

        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setGravity(Gravity.CENTER_VERTICAL);
        page.setPadding(dp(24), dp(32), dp(24), dp(32));
        page.setBackgroundColor(background);

        TextView title = new TextView(this);
        title.setText(R.string.app_name);
        title.setTextSize(30);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        title.setTextColor(Color.WHITE);
        page.addView(title, matchWrap());

        TextView subtitle = new TextView(this);
        subtitle.setText(R.string.home_subtitle);
        subtitle.setTextSize(16);
        subtitle.setTextColor(Color.rgb(148, 163, 184));
        LinearLayout.LayoutParams subtitleParams = matchWrap();
        subtitleParams.topMargin = dp(8);
        page.addView(subtitle, subtitleParams);

        Button access = new Button(this);
        access.setText(R.string.enable_access);
        access.setTextColor(background);
        access.setBackgroundTintList(android.content.res.ColorStateList.valueOf(accent));
        LinearLayout.LayoutParams buttonParams = matchWrap();
        buttonParams.topMargin = dp(28);
        page.addView(access, buttonParams);
        access.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)));

        TextView privacy = new TextView(this);
        privacy.setText(R.string.permission_explanation);
        privacy.setTextSize(13);
        privacy.setTextColor(Color.rgb(203, 213, 225));
        LinearLayout.LayoutParams privacyParams = matchWrap();
        privacyParams.topMargin = dp(18);
        page.addView(privacy, privacyParams);
        setContentView(page);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }
}
