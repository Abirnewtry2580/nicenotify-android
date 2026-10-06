package com.nicenotify.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class MainActivity extends Activity {
    private static final int BG = Color.rgb(8, 13, 24);
    private static final int CARD = Color.rgb(17, 27, 44);
    private static final int ACCENT = Color.rgb(34, 211, 238);
    private static final int MUTED = Color.rgb(148, 163, 184);

    private final ExecutorService reads = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ArrayList<AppChoice> appChoices = new ArrayList<>();
    private List<NotificationRecord> records = new ArrayList<>();
    private LinearLayout content;
    private TextView accessBanner;
    private EditText search;
    private Spinner appFilter;
    private Spinner dateFilter;
    private int mode;
    private int dateRange;
    private String query = "";
    private String selectedPackage = "";
    private boolean passedPinGate;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        buildShell();
        loadHistory();
    }

    private void buildShell() {
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(18), dp(14), dp(18), dp(10));
        page.setBackgroundColor(BG);

        LinearLayout heading = new LinearLayout(this);
        heading.setGravity(Gravity.CENTER_VERTICAL);
        heading.setOrientation(LinearLayout.HORIZONTAL);
        TextView brand = new TextView(this);
        brand.setText(R.string.app_name);
        brand.setTextSize(25);
        brand.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        brand.setTextColor(Color.WHITE);
        heading.addView(brand, new LinearLayout.LayoutParams(0, dp(40), 1));
        Button clearButton = smallButton("Clear active");
        heading.addView(clearButton, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(40)));
        clearButton.setOnClickListener(v -> clearActiveNotifications());
        page.addView(heading);

        TextView subtitle = new TextView(this);
        subtitle.setText("On-device notification history");
        subtitle.setTextColor(MUTED);
        subtitle.setTextSize(12);
        LinearLayout.LayoutParams subParams = wrap();
        subParams.bottomMargin = dp(10);
        page.addView(subtitle, subParams);

        accessBanner = new TextView(this);
        accessBanner.setText(R.string.permission_explanation);
        accessBanner.setTextColor(Color.WHITE);
        accessBanner.setTextSize(13);
        accessBanner.setPadding(dp(12), dp(10), dp(12), dp(10));
        accessBanner.setBackground(roundRect(Color.rgb(27, 44, 63), dp(12)));
        LinearLayout.LayoutParams bannerParams = wrap();
        bannerParams.bottomMargin = dp(10);
        page.addView(accessBanner, bannerParams);
        accessBanner.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)));
        updateAccessBanner();

        LinearLayout tabs = new LinearLayout(this);
        tabs.setOrientation(LinearLayout.HORIZONTAL);
        Button all = tabButton("All");
        Button unread = tabButton("Unread");
        Button apps = tabButton("Apps");
        Button settings = tabButton("Settings");
        tabs.addView(all, new LinearLayout.LayoutParams(0, dp(40), 1));
        tabs.addView(unread, new LinearLayout.LayoutParams(0, dp(40), 1));
        tabs.addView(apps, new LinearLayout.LayoutParams(0, dp(40), 1));
        tabs.addView(settings, new LinearLayout.LayoutParams(0, dp(40), 1));
        page.addView(tabs);
        all.setOnClickListener(v -> { mode = 0; render(); });
        unread.setOnClickListener(v -> { mode = 1; render(); });
        apps.setOnClickListener(v -> { mode = 2; render(); });
        settings.setOnClickListener(v -> { mode = 3; render(); });

        search = new EditText(this);
        search.setSingleLine(true);
        search.setTextSize(14);
        search.setTextColor(Color.WHITE);
        search.setHintTextColor(MUTED);
        search.setHint("Search notifications");
        search.setPadding(dp(12), 0, dp(12), 0);
        search.setBackground(roundRect(CARD, dp(11)));
        LinearLayout.LayoutParams searchParams = wrap();
        searchParams.topMargin = dp(9);
        searchParams.bottomMargin = dp(8);
        page.addView(search, searchParams);
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                query = s == null ? "" : s.toString().trim().toLowerCase(Locale.ROOT);
                render();
            }
            @Override public void afterTextChanged(Editable s) {}
        });

        LinearLayout filters = new LinearLayout(this);
        filters.setOrientation(LinearLayout.HORIZONTAL);
        appFilter = new Spinner(this);
        dateFilter = new Spinner(this);
        configureSpinner(dateFilter, new String[]{"Any time", "Last 24 hours", "Last 7 days", "Last 30 days"});
        filters.addView(appFilter, new LinearLayout.LayoutParams(0, dp(42), 1));
        filters.addView(dateFilter, new LinearLayout.LayoutParams(0, dp(42), 1));
        LinearLayout.LayoutParams filtersParams = wrap();
        filtersParams.bottomMargin = dp(8);
        page.addView(filters, filtersParams);
        appFilter.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (position >= 0 && position < appChoices.size()) selectedPackage = appChoices.get(position).packageName;
                render();
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });
        dateFilter.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                dateRange = Math.max(0, position);
                render();
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });

        ScrollView scroller = new ScrollView(this);
        scroller.setFillViewport(true);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(0, dp(2), 0, dp(24));
        scroller.addView(content);
        page.addView(scroller, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        setContentView(page);
    }

    private void configureSpinner(Spinner spinner, String[] values) {
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, values);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
        spinner.setBackgroundTintList(android.content.res.ColorStateList.valueOf(MUTED));
    }

    private void rebuildAppFilter() {
        String oldPackage = selectedPackage;
        appChoices.clear();
        appChoices.add(new AppChoice("", "All apps"));
        LinkedHashMap<String, String> apps = new LinkedHashMap<>();
        for (NotificationRecord record : records) apps.put(record.packageName, record.appName);
        for (Map.Entry<String, String> entry : apps.entrySet()) appChoices.add(new AppChoice(entry.getKey(), entry.getValue()));
        String[] labels = new String[appChoices.size()];
        int selected = 0;
        for (int i = 0; i < appChoices.size(); i++) {
            labels[i] = appChoices.get(i).label;
            if (appChoices.get(i).packageName.equals(oldPackage)) selected = i;
        }
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, labels);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        appFilter.setAdapter(adapter);
        appFilter.setSelection(selected, false);
        selectedPackage = appChoices.get(selected).packageName;
    }

    private void loadHistory() {
        reads.execute(() -> {
            NotificationDatabase database = NotificationDatabase.getInstance(getApplicationContext());
            database.pruneOlderThanDays(NotificationPreferences.getRetentionDays(getApplicationContext()));
            List<NotificationRecord> result = database.recent(1000);
            mainHandler.post(() -> {
                if (isFinishing()) return;
                records = result;
                rebuildAppFilter();
                render();
            });
        });
    }

    private void render() {
        if (content == null) return;
        content.removeAllViews();
        boolean appMode = mode == 2;
        boolean settingsMode = mode == 3;
        appFilter.setVisibility(appMode || settingsMode ? View.GONE : View.VISIBLE);
        dateFilter.setVisibility(appMode || settingsMode ? View.GONE : View.VISIBLE);
        search.setVisibility(settingsMode ? View.GONE : View.VISIBLE);
        search.setHint(appMode ? "Search apps" : "Search notifications");
        if (appMode) renderApps();
        else if (settingsMode) renderSettings();
        else renderNotifications();
    }

    private void renderNotifications() {
        long cutoff = dateRange == 0 ? 0 : System.currentTimeMillis() - dateRangeMillis();
        int count = 0;
        for (NotificationRecord record : records) {
            if (mode == 1 && record.read) continue;
            if (!selectedPackage.isEmpty() && !selectedPackage.equals(record.packageName)) continue;
            if (cutoff > 0 && record.postedAt < cutoff) continue;
            String allText = (record.appName + " " + record.title + " " + record.body).toLowerCase(Locale.ROOT);
            if (!query.isEmpty() && !allText.contains(query)) continue;
            addNotificationCard(record);
            count++;
        }
        if (count == 0) addEmptyState("No matching notifications", "New notifications will appear here after access is enabled.");
    }

    private long dateRangeMillis() {
        if (dateRange == 1) return 24L * 60L * 60L * 1000L;
        if (dateRange == 2) return 7L * 24L * 60L * 60L * 1000L;
        if (dateRange == 3) return 30L * 24L * 60L * 60L * 1000L;
        return 0;
    }

    private void addNotificationCard(NotificationRecord record) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(13), dp(11), dp(13), dp(10));
        card.setBackground(roundRect(CARD, dp(14)));
        LinearLayout.LayoutParams cardParams = wrap();
        cardParams.bottomMargin = dp(9);
        content.addView(card, cardParams);

        TextView app = new TextView(this);
        app.setText(record.appName + "  ·  " + DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(record.postedAt));
        app.setTextColor(ACCENT);
        app.setTextSize(11);
        card.addView(app, wrap());

        TextView title = new TextView(this);
        title.setText(record.title.isEmpty() ? record.appName : record.title);
        title.setTextColor(Color.WHITE);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        title.setTextSize(14);
        LinearLayout.LayoutParams titleParams = wrap();
        titleParams.topMargin = dp(5);
        card.addView(title, titleParams);

        if (!record.body.isEmpty()) {
            TextView body = new TextView(this);
            body.setText(record.body);
            body.setTextColor(Color.rgb(203, 213, 225));
            body.setTextSize(13);
            body.setMaxLines(8);
            LinearLayout.LayoutParams bodyParams = wrap();
            bodyParams.topMargin = dp(4);
            card.addView(body, bodyParams);
        }

        LinearLayout actions = new LinearLayout(this);
        actions.setGravity(Gravity.END);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams actionParams = wrap();
        actionParams.topMargin = dp(7);
        card.addView(actions, actionParams);
        Button open = smallButton("Open app");
        Button dismiss = smallButton("Dismiss");
        Button delete = smallButton("Delete");
        actions.addView(open);
        actions.addView(dismiss);
        actions.addView(delete);
        open.setOnClickListener(v -> openSourceApp(record));
        dismiss.setOnClickListener(v -> {
            if (!NotificationCaptureService.dismiss(record.sourceKey)) {
                Toast.makeText(this, "This notification is no longer active.", Toast.LENGTH_SHORT).show();
            } else Toast.makeText(this, "Notification dismissed; history kept.", Toast.LENGTH_SHORT).show();
        });
        delete.setOnClickListener(v -> new AlertDialog.Builder(this)
                .setTitle("Delete saved notification?")
                .setMessage("This only deletes the history entry on this device.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Delete", (dialog, which) -> {
                    reads.execute(() -> {
                        NotificationDatabase.getInstance(getApplicationContext()).delete(record.id);
                        loadHistory();
                    });
                }).show());
    }

    private void openSourceApp(NotificationRecord record) {
        reads.execute(() -> NotificationDatabase.getInstance(getApplicationContext()).markRead(record.id));
        Intent launch = getPackageManager().getLaunchIntentForPackage(record.packageName);
        if (launch == null) {
            Toast.makeText(this, "The source app cannot be opened.", Toast.LENGTH_SHORT).show();
            return;
        }
        try { startActivity(launch); }
        catch (Exception e) { Toast.makeText(this, "Could not open the source app.", Toast.LENGTH_SHORT).show(); }
    }

    private void clearActiveNotifications() {
        new AlertDialog.Builder(this)
                .setTitle("Clear active notifications?")
                .setMessage("This dismisses clearable notifications from the notification shade. Saved history stays here.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Clear", (dialog, which) -> {
                    if (NotificationCaptureService.clearActive()) {
                        Toast.makeText(this, "Active notifications cleared; history kept.", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(this, "Enable notification access first.", Toast.LENGTH_SHORT).show();
                    }
                }).show();
    }

    private void renderApps() {
        LinkedHashMap<String, AppSummary> apps = new LinkedHashMap<>();
        for (NotificationRecord record : records) {
            if (!query.isEmpty() && !(record.appName + " " + record.packageName).toLowerCase(Locale.ROOT).contains(query)) continue;
            AppSummary summary = apps.get(record.packageName);
            if (summary == null) {
                summary = new AppSummary(record.appName, record.packageName);
                apps.put(record.packageName, summary);
            }
            summary.count++;
        }
        if (apps.isEmpty()) {
            addEmptyState("No apps yet", "Apps appear after their first saved notification.");
            return;
        }
        for (AppSummary app : apps.values()) {
            LinearLayout row = new LinearLayout(this);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(13), dp(10), dp(10), dp(10));
            row.setBackground(roundRect(CARD, dp(13)));
            LinearLayout.LayoutParams rowParams = wrap();
            rowParams.bottomMargin = dp(8);
            content.addView(row, rowParams);

            LinearLayout labels = new LinearLayout(this);
            labels.setOrientation(LinearLayout.VERTICAL);
            row.addView(labels, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
            TextView name = new TextView(this);
            name.setText(app.name);
            name.setTextColor(Color.WHITE);
            name.setTextSize(14);
            name.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            labels.addView(name);
            TextView detail = new TextView(this);
            detail.setText(app.count + " saved · " + app.packageName);
            detail.setTextColor(MUTED);
            detail.setTextSize(10);
            labels.addView(detail);
            Switch save = new Switch(this);
            save.setText("Save");
            save.setTextColor(Color.WHITE);
            save.setTextSize(11);
            save.setChecked(!NotificationPreferences.isExcluded(this, app.packageName));
            save.setOnCheckedChangeListener((button, checked) -> {
                NotificationPreferences.setExcluded(this, app.packageName, !checked);
                Toast.makeText(this, checked ? "Saving enabled for future alerts." : "Saving paused for this app.", Toast.LENGTH_SHORT).show();
            });
            row.addView(save);
        }
    }

    private void renderSettings() {
        addSettingCard("Privacy on this device", "Notification text is encrypted with an Android Keystore key. No account or cloud sync is used.");
        addSettingAction("App lock", PinManager.hasPin(this) ? "Change PIN" : "Set PIN", this::showPinSettingsDialog);
        if (PinManager.hasPin(this)) addSettingAction("App lock", "Remove PIN", this::showRemovePinDialog);
        int days = NotificationPreferences.getRetentionDays(this);
        String retention = days == 0 ? "Never" : days + " days";
        addSettingAction("Auto-delete history", retention, this::showRetentionDialog);
        addSettingAction("App capture choices", "Choose apps", () -> { mode = 2; render(); });
        addSettingAction("Saved history", "Delete all", this::confirmClearHistory);
    }

    private void addSettingAction(String titleText, String actionText, Runnable action) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(13), dp(12), dp(12), dp(12));
        row.setBackground(roundRect(CARD, dp(13)));
        LinearLayout.LayoutParams params = wrap();
        params.bottomMargin = dp(8);
        content.addView(row, params);
        TextView title = new TextView(this);
        title.setText(titleText);
        title.setTextColor(Color.WHITE);
        title.setTextSize(14);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        row.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        Button button = smallButton(actionText);
        row.addView(button);
        button.setOnClickListener(v -> action.run());
    }

    private void addSettingCard(String titleText, String bodyText) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(13), dp(12), dp(13), dp(12));
        card.setBackground(roundRect(CARD, dp(13)));
        LinearLayout.LayoutParams params = wrap();
        params.bottomMargin = dp(8);
        content.addView(card, params);
        TextView title = new TextView(this);
        title.setText(titleText);
        title.setTextColor(ACCENT);
        title.setTextSize(13);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        card.addView(title);
        TextView body = new TextView(this);
        body.setText(bodyText);
        body.setTextColor(MUTED);
        body.setTextSize(12);
        LinearLayout.LayoutParams bodyParams = wrap();
        bodyParams.topMargin = dp(5);
        card.addView(body, bodyParams);
    }

    private void showRetentionDialog() {
        String[] labels = {"Never", "7 days", "30 days", "90 days"};
        int[] days = {0, 7, 30, 90};
        int selected = 0;
        int current = NotificationPreferences.getRetentionDays(this);
        for (int i = 0; i < days.length; i++) if (days[i] == current) selected = i;
        new AlertDialog.Builder(this).setTitle("Auto-delete saved history")
                .setSingleChoiceItems(labels, selected, (dialog, which) -> {
                    NotificationPreferences.setRetentionDays(this, days[which]);
                    reads.execute(() -> NotificationDatabase.getInstance(getApplicationContext()).pruneOlderThanDays(days[which]));
                    dialog.dismiss();
                    loadHistory();
                    render();
                }).setNegativeButton("Cancel", null).show();
    }

    private void confirmClearHistory() {
        new AlertDialog.Builder(this).setTitle("Delete all saved history?")
                .setMessage("This permanently deletes saved notification records on this device. Active notifications will not be dismissed.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Delete all", (dialog, which) -> reads.execute(() -> {
                    NotificationDatabase.getInstance(getApplicationContext()).clearAll();
                    loadHistory();
                })).show();
    }

    private void showPinSettingsDialog() {
        boolean changing = PinManager.hasPin(this);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(16), dp(4), dp(16), 0);
        EditText oldPin = null;
        if (changing) {
            oldPin = pinInput("Current PIN");
            box.addView(oldPin, wrap());
        }
        EditText nextPin = pinInput("New PIN (at least 4 digits)");
        EditText confirmPin = pinInput("Confirm new PIN");
        box.addView(nextPin, wrap());
        LinearLayout.LayoutParams confirmParams = wrap();
        confirmParams.topMargin = dp(8);
        box.addView(confirmPin, confirmParams);
        EditText finalOldPin = oldPin;
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle(changing ? "Change app PIN" : "Set app PIN")
                .setView(box).setNegativeButton("Cancel", null).setPositiveButton("Save", null).create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String newValue = nextPin.getText().toString();
            if (changing && !PinManager.verify(this, finalOldPin.getText().toString())) {
                finalOldPin.setError("Current PIN is incorrect");
                return;
            }
            if (newValue.length() < 4 || !newValue.matches("[0-9]+")) {
                nextPin.setError("Use at least 4 digits");
                return;
            }
            if (!newValue.equals(confirmPin.getText().toString())) {
                confirmPin.setError("PINs do not match");
                return;
            }
            try {
                PinManager.setPin(this, newValue);
                passedPinGate = true;
                getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE);
                Toast.makeText(this, "App lock enabled.", Toast.LENGTH_SHORT).show();
                dialog.dismiss();
                render();
            } catch (Exception e) {
                Toast.makeText(this, "Could not save the app PIN.", Toast.LENGTH_SHORT).show();
            }
        }));
        dialog.show();
        dialog.getWindow().setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
    }

    private void showRemovePinDialog() {
        EditText pin = pinInput("Current PIN");
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("Remove app PIN").setView(pin)
                .setNegativeButton("Cancel", null).setPositiveButton("Remove", null).create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            if (!PinManager.verify(this, pin.getText().toString())) {
                pin.setError("PIN is incorrect");
                return;
            }
            PinManager.clear(this);
            passedPinGate = true;
            getWindow().clearFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE);
            Toast.makeText(this, "App lock removed.", Toast.LENGTH_SHORT).show();
            dialog.dismiss();
            render();
        }));
        dialog.show();
    }

    private EditText pinInput(String hint) {
        EditText input = new EditText(this);
        input.setHint(hint);
        input.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        input.setTextColor(Color.WHITE);
        input.setHintTextColor(MUTED);
        input.setSingleLine(true);
        return input;
    }

    private void showPinGate() {
        EditText pin = pinInput("Enter your PIN");
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("NiceNotify is locked")
                .setMessage("Enter your app PIN to view notification history.")
                .setView(pin).setCancelable(false).setPositiveButton("Unlock", null).create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            if (PinManager.verify(this, pin.getText().toString())) {
                passedPinGate = true;
                dialog.dismiss();
            } else {
                pin.setError("Incorrect PIN");
                pin.setText("");
            }
        }));
        dialog.setCanceledOnTouchOutside(false);
        dialog.show();
    }

    private void addEmptyState(String titleText, String bodyText) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER);
        card.setPadding(dp(18), dp(28), dp(18), dp(28));
        card.setBackground(roundRect(CARD, dp(14)));
        TextView title = new TextView(this);
        title.setText(titleText);
        title.setTextColor(Color.WHITE);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        title.setTextSize(16);
        title.setGravity(Gravity.CENTER);
        card.addView(title, wrap());
        TextView body = new TextView(this);
        body.setText(bodyText);
        body.setTextColor(MUTED);
        body.setTextSize(12);
        body.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams bodyParams = wrap();
        bodyParams.topMargin = dp(8);
        card.addView(body, bodyParams);
        content.addView(card, wrap());
    }

    private void updateAccessBanner() {
        if (accessBanner == null) return;
        boolean enabled = isListenerEnabled();
        accessBanner.setText(enabled ? "Notification access is on · Tap to manage" : "Tap here to enable Notification Access");
        accessBanner.setBackground(roundRect(enabled ? Color.rgb(14, 55, 55) : Color.rgb(27, 44, 63), dp(12)));
    }

    private boolean isListenerEnabled() {
        String enabled = Settings.Secure.getString(getContentResolver(), "enabled_notification_listeners");
        if (enabled == null) return false;
        ComponentName component = new ComponentName(this, NotificationCaptureService.class);
        for (String item : enabled.split(":")) {
            ComponentName parsed = ComponentName.unflattenFromString(item);
            if (component.equals(parsed)) return true;
        }
        return false;
    }

    private Button tabButton(String label) {
        Button button = new Button(this);
        button.setText(label);
        button.setTextSize(12);
        button.setTextColor(Color.WHITE);
        button.setBackgroundTintList(android.content.res.ColorStateList.valueOf(CARD));
        return button;
    }

    private Button smallButton(String label) {
        Button button = new Button(this);
        button.setText(label);
        button.setTextSize(9);
        button.setMinWidth(0);
        button.setMinimumWidth(0);
        button.setPadding(dp(5), 0, dp(5), 0);
        button.setTextColor(ACCENT);
        button.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.rgb(28, 40, 58)));
        return button;
    }

    private GradientDrawable roundRect(int color, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(radius);
        return drawable;
    }

    private LinearLayout.LayoutParams wrap() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateAccessBanner();
        if (PinManager.hasPin(this)) {
            getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE);
            if (!passedPinGate) showPinGate();
        } else {
            getWindow().clearFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE);
            passedPinGate = true;
        }
        loadHistory();
    }

    @Override
    protected void onStop() {
        passedPinGate = false;
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        reads.shutdown();
        super.onDestroy();
    }

    private static final class AppChoice {
        final String packageName;
        final String label;
        AppChoice(String packageName, String label) { this.packageName = packageName; this.label = label; }
    }

    private static final class AppSummary {
        final String name;
        final String packageName;
        int count;
        AppSummary(String name, String packageName) { this.name = name; this.packageName = packageName; }
    }
}
