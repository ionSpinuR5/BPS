package ro.bani.pe.scut;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.Editable;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;

import java.io.InputStream;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class MainActivity extends Activity {
    private static final int PICK_FILE = 102;
    private static final String PREF_NAME = "bani_pe_scurt_prefs";
    private static final String PREF_KEY_DARK_MODE = "dark_mode_enabled";
    private static final String PREF_KEY_BUDGET = "monthly_budget_bani";

    private static final int DANGER_RED = Color.rgb(211, 47, 47);        // #D32F2F

    // Dynamic Theme Color Getters (matching Web App variables)
    private boolean isDarkMode = false;

    private int bgColor() {
        return isDarkMode ? Color.rgb(17, 22, 19) : Color.rgb(246, 248, 246);       // #111613 vs #F6F8F6
    }

    private int surfaceColor() {
        return isDarkMode ? Color.rgb(24, 33, 26) : Color.WHITE;                    // #18211A vs #FFFFFF
    }

    private int primaryDark() {
        return isDarkMode ? Color.rgb(120, 201, 142) : Color.rgb(27, 94, 32);       // #78C98E vs #1B5E20
    }

    private int primaryGreen() {
        return isDarkMode ? Color.rgb(79, 164, 105) : Color.rgb(46, 125, 50);       // #4FA469 vs #2E7D32
    }

    private int accentLight() {
        return isDarkMode ? Color.rgb(28, 50, 34) : Color.rgb(232, 245, 233);       // #1C3222 vs #E8F5E9
    }

    private int textMain() {
        return isDarkMode ? Color.rgb(232, 237, 230) : Color.rgb(26, 32, 27);       // #E8EDE6 vs #1A201B
    }

    private int textMuted() {
        return isDarkMode ? Color.rgb(149, 158, 146) : Color.rgb(115, 128, 118);    // #959E92 vs #738076
    }

    private int borderColor() {
        return isDarkMode ? Color.rgb(38, 49, 40) : Color.rgb(230, 235, 230);       // #263128 vs #E6EBE6
    }

    private int inputBg() {
        return isDarkMode ? Color.rgb(19, 25, 20) : Color.WHITE;                    // #131914 vs #FFFFFF
    }

    private int trackBg() {
        return isDarkMode ? Color.rgb(32, 42, 34) : Color.rgb(238, 241, 238);
    }

    // Category Styling Definitions
    static final class CategoryStyle {
        final String name;
        final String icon;
        final int bgColor;
        final int textColor;
        final int barColor;

        CategoryStyle(String name, String icon, int bgColor, int textColor, int barColor) {
            this.name = name;
            this.icon = icon;
            this.bgColor = bgColor;
            this.textColor = textColor;
            this.barColor = barColor;
        }
    }

    private CategoryStyle getCategoryStyle(String name) {
        if (isDarkMode) {
            switch (name) {
                case "Mâncare":
                    return new CategoryStyle("Mâncare", "🛒", Color.rgb(28, 50, 34), Color.rgb(120, 201, 142), Color.rgb(79, 164, 105));
                case "Transport":
                    return new CategoryStyle("Transport", "🚗", Color.rgb(54, 36, 23), Color.rgb(224, 159, 110), Color.rgb(251, 140, 0));
                case "Locuință":
                    return new CategoryStyle("Locuință", "🏠", Color.rgb(26, 44, 61), Color.rgb(130, 177, 255), Color.rgb(30, 136, 229));
                case "Cumpărături":
                    return new CategoryStyle("Cumpărături", "🛍️", Color.rgb(46, 30, 54), Color.rgb(206, 147, 216), Color.rgb(171, 71, 188));
                case "Sănătate":
                    return new CategoryStyle("Sănătate", "💊", Color.rgb(58, 28, 28), Color.rgb(239, 154, 154), Color.rgb(229, 57, 53));
                case "Timp liber":
                    return new CategoryStyle("Timp liber", "🍿", Color.rgb(20, 47, 44), Color.rgb(128, 203, 196), Color.rgb(0, 137, 123));
                case "Altele":
                default:
                    return new CategoryStyle("Altele", "📦", Color.rgb(35, 43, 46), Color.rgb(176, 190, 197), Color.rgb(120, 144, 156));
            }
        } else {
            switch (name) {
                case "Mâncare":
                    return new CategoryStyle("Mâncare", "🛒", Color.rgb(232, 245, 233), Color.rgb(46, 125, 50), Color.rgb(67, 160, 71));
                case "Transport":
                    return new CategoryStyle("Transport", "🚗", Color.rgb(255, 243, 224), Color.rgb(230, 81, 0), Color.rgb(251, 140, 0));
                case "Locuință":
                    return new CategoryStyle("Locuință", "🏠", Color.rgb(227, 242, 253), Color.rgb(21, 101, 192), Color.rgb(30, 136, 229));
                case "Cumpărături":
                    return new CategoryStyle("Cumpărături", "🛍️", Color.rgb(243, 229, 245), Color.rgb(123, 31, 162), Color.rgb(142, 36, 170));
                case "Sănătate":
                    return new CategoryStyle("Sănătate", "💊", Color.rgb(255, 235, 238), Color.rgb(198, 40, 40), Color.rgb(229, 57, 53));
                case "Timp liber":
                    return new CategoryStyle("Timp liber", "🍿", Color.rgb(224, 242, 241), Color.rgb(0, 105, 92), Color.rgb(0, 137, 123));
                case "Altele":
                default:
                    return new CategoryStyle("Altele", "📦", Color.rgb(236, 239, 241), Color.rgb(69, 90, 100), Color.rgb(96, 125, 139));
            }
        }
    }

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final List<Expense> expenses = new ArrayList<>();
    private final List<Expense> drafts = new ArrayList<>();

    private YearMonth selectedMonth = YearMonth.now();
    private String currentSearchQuery = "";
    private String selectedCategoryFilter = "Toate";
    private String selectedCurrencyTab = "RON"; // "RON" or "VALUTA"
    private String importedFileName = "";
    private long monthlyBudgetBani = 0; // 0 = no budget set

    // UI View References
    private LinearLayout root;
    private LinearLayout draftSection;
    private LinearLayout draftRows;
    private LinearLayout transactionRows;
    private LinearLayout categoryBreakdownContainer;
    private LinearLayout chipsContainer;
    private LinearLayout syncCard;
    private LinearLayout budgetCard;
    private LinearLayout filterSummaryCard;
    private TextView filterSummaryText;
    private TextView totalLabel;
    private TextView totalText;
    private TextView countText;
    private TextView avgDailyText;
    private TextView topExpenseText;
    private TextView monthText;
    private TextView statusText;
    private TextView transactionListTitle;
    private EditText searchInput;
    private ProgressBar progressBar;
    private Button draftSaveBtnTop;
    private Button draftSaveBtnBottom;
    private Button ronTabBtn;
    private Button valutaTabBtn;

    // Real-time notification broadcast listener
    private final BroadcastReceiver expenseReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent != null && BankNotificationListener.ACTION_LISTENER_CONNECTED.equals(intent.getAction())) {
                updateSyncCard();
                return;
            }
            loadSavedExpenses();
            updateDashboard();
            renderTransactions();
            updateSyncCard();
            Toast.makeText(MainActivity.this, "Plată nouă detectată în timp real! ⚡", Toast.LENGTH_SHORT).show();
        }
    };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            android.util.Log.e("BaniPeScurt", "Uncaught exception", throwable);
        });

        try {
            // Load theme preference (defaults to system setting on AMOLED Xiaomi devices)
            loadThemePreference();

            // 1. Build and attach UI first so Window DecorView is initialized
            initUi();

            // 2. Safely configure system bars
            setupWindowBars();

            // 2.5 Request POST_NOTIFICATIONS runtime permission on Android 13+ (API 33+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                    requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, 101);
                }
            }

            // 3. Load saved expenses & budget
            loadSavedExpenses();

            if (savedInstanceState != null) {
                String monthStr = savedInstanceState.getString("selectedMonth", "");
                if (!monthStr.isEmpty()) {
                    try { selectedMonth = YearMonth.parse(monthStr); } catch (Exception ignored) {}
                }
                importedFileName = savedInstanceState.getString("importedFileName", "");
                String draftsJson = savedInstanceState.getString("drafts", "[]");
                try {
                    JSONArray arr = new JSONArray(draftsJson);
                    for (int i = 0; i < arr.length(); i++) {
                        drafts.add(Expense.fromJson(arr.getJSONObject(i)));
                    }
                } catch (Exception ignored) {}
            }

            updateDashboard();
            renderTransactions();
            if (!drafts.isEmpty()) {
                draftSection.setVisibility(View.VISIBLE);
                renderDrafts();
            }
        } catch (Throwable t) {
            android.util.Log.e("BaniPeScurt", "Fatal error during onCreate", t);
            showCrashFallbackUi(t);
        }
    }

    private void loadThemePreference() {
        SharedPreferences prefs = getSharedPreferences(PREF_NAME, MODE_PRIVATE);
        if (prefs.contains(PREF_KEY_DARK_MODE)) {
            isDarkMode = prefs.getBoolean(PREF_KEY_DARK_MODE, false);
        } else {
            int nightModeFlags = getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
            isDarkMode = (nightModeFlags == Configuration.UI_MODE_NIGHT_YES);
        }
    }

    private void toggleTheme() {
        isDarkMode = !isDarkMode;
        getSharedPreferences(PREF_NAME, MODE_PRIVATE).edit().putBoolean(PREF_KEY_DARK_MODE, isDarkMode).apply();
        setupWindowBars();
        initUi();
        updateDashboard();
        renderTransactions();
        Toast.makeText(this, isDarkMode ? "Mod întunecat activat 🌙" : "Mod luminos activat ☀️", Toast.LENGTH_SHORT).show();
    }

    private void setupWindowBars() {
        try {
            getWindow().setStatusBarColor(bgColor());
            getWindow().setNavigationBarColor(surfaceColor());
            View decorView = getWindow().getDecorView();
            if (decorView != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    android.view.WindowInsetsController controller = decorView.getWindowInsetsController();
                    if (controller == null) {
                        controller = getWindow().getInsetsController();
                    }
                    if (controller != null) {
                        int flags = isDarkMode ? 0 : (
                                android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS |
                                        android.view.WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
                        );
                        controller.setSystemBarsAppearance(
                                flags,
                                android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS |
                                        android.view.WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
                        );
                    } else {
                        decorView.setSystemUiVisibility(isDarkMode ? 0 : (View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR));
                    }
                } else {
                    decorView.setSystemUiVisibility(isDarkMode ? 0 : (View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR));
                }
            }
        } catch (Throwable ignored) {}
    }

    private void showCrashFallbackUi(Throwable t) {
        try {
            ScrollView sv = new ScrollView(this);
            sv.setBackgroundColor(bgColor());
            sv.setFillViewport(true);

            LinearLayout layout = new LinearLayout(this);
            layout.setOrientation(LinearLayout.VERTICAL);
            layout.setPadding(dp(20), dp(40), dp(20), dp(20));

            TextView title = new TextView(this);
            title.setText("Bani pe scurt — Diagnostic");
            title.setTextSize(18);
            title.setTextColor(DANGER_RED);
            title.setTypeface(Typeface.DEFAULT_BOLD);
            layout.addView(title);

            TextView subtitle = new TextView(this);
            subtitle.setText("Aplicația a întâmpinat o problemă la pornire:");
            subtitle.setTextSize(13);
            subtitle.setTextColor(textMain());
            layout.addView(subtitle, margin(MATCH, WRAP, 0, dp(8), 0, dp(12)));

            TextView details = new TextView(this);
            details.setText(android.util.Log.getStackTraceString(t));
            details.setTextSize(11);
            details.setTypeface(Typeface.MONOSPACE);
            details.setTextColor(textMuted());
            details.setPadding(dp(12), dp(12), dp(12), dp(12));
            details.setBackground(round(surfaceColor(), dp(8), borderColor()));
            layout.addView(details, margin(MATCH, WRAP, 0, 0, 0, dp(16)));

            Button retryBtn = button("Resetează datele locale și repornește", true);
            retryBtn.setOnClickListener(v -> {
                getSharedPreferences(PREF_NAME, MODE_PRIVATE).edit().clear().apply();
                recreate();
            });
            layout.addView(retryBtn, new LinearLayout.LayoutParams(MATCH, dp(48)));

            sv.addView(layout);
            setContentView(sv);
        } catch (Throwable ignored) {}
    }

    @Override
    protected void onResume() {
        super.onResume();
        try {
            IntentFilter filter = new IntentFilter();
            filter.addAction(BankNotificationListener.ACTION_EXPENSE_ADDED);
            filter.addAction(BankNotificationListener.ACTION_LISTENER_CONNECTED);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(expenseReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
            } else {
                registerReceiver(expenseReceiver, filter);
            }
        } catch (Throwable ignored) {}

        if (isNotificationListenerEnabled() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            try {
                android.service.notification.NotificationListenerService.requestRebind(
                        new android.content.ComponentName(this, BankNotificationListener.class)
                );
            } catch (Throwable t) {
                android.util.Log.e("BaniPeScurt", "Rebind request failed", t);
            }
        }

        try {
            loadSavedExpenses();
            updateDashboard();
            renderTransactions();
            updateSyncCard();
        } catch (Throwable t) {
            android.util.Log.e("BaniPeScurt", "Error in onResume", t);
        }
    }

    @Override
    protected void onPause() {
        try {
            unregisterReceiver(expenseReceiver);
        } catch (Exception ignored) {}
        super.onPause();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString("selectedMonth", selectedMonth.toString());
        outState.putString("importedFileName", importedFileName);
        JSONArray arr = new JSONArray();
        for (Expense e : drafts) {
            try { arr.put(e.toJson()); } catch (Exception ignored) {}
        }
        outState.putString("drafts", arr.toString());
    }

    @Override
    protected void onDestroy() {
        executor.shutdown();
        super.onDestroy();
    }

    private void initUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(bgColor());

        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(16), dp(18), dp(32));
        scroll.addView(root);

        // 1. Header / Brand Bar with Dark Mode Toggle
        LinearLayout headerRow = new LinearLayout(this);
        headerRow.setOrientation(LinearLayout.HORIZONTAL);
        headerRow.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout brandBlock = new LinearLayout(this);
        brandBlock.setOrientation(LinearLayout.VERTICAL);

        TextView brand = text("Bani pe scurt", 19, primaryDark(), true);
        brandBlock.addView(brand);

        TextView tagline = text("Controlul cheltuielilor tale", 12, textMuted(), false);
        brandBlock.addView(tagline);
        headerRow.addView(brandBlock, new LinearLayout.LayoutParams(0, WRAP, 1));

        // Dark / Light Theme Toggle Button
        Button themeBtn = smallHeaderButton(isDarkMode ? "☀️" : "🌙");
        themeBtn.setOnClickListener(v -> toggleTheme());
        headerRow.addView(themeBtn, margin(WRAP, dp(34), 0, 0, dp(6), 0));

        Button exportBtn = smallHeaderButton("Export CSV ↗");
        exportBtn.setOnClickListener(v -> showExportDialog());
        headerRow.addView(exportBtn, new LinearLayout.LayoutParams(WRAP, dp(34)));

        root.addView(headerRow, margin(MATCH, WRAP, 0, 0, 0, dp(14)));

        // 2. Month Selector Bar (Clickable for Month Jump Picker!)
        LinearLayout monthBar = new LinearLayout(this);
        monthBar.setGravity(Gravity.CENTER_VERTICAL);
        monthBar.setOrientation(LinearLayout.HORIZONTAL);
        monthBar.setPadding(dp(12), dp(8), dp(12), dp(8));
        monthBar.setBackground(round(surfaceColor(), dp(14), borderColor()));

        Button prevBtn = circularNavButton("‹");
        prevBtn.setOnClickListener(v -> {
            selectedMonth = selectedMonth.minusMonths(1);
            updateDashboard();
            renderTransactions();
        });

        monthText = text("", 15, textMain(), true);
        monthText.setGravity(Gravity.CENTER);
        monthText.setOnClickListener(v -> openMonthPickerDialog());

        Button nextBtn = circularNavButton("›");
        nextBtn.setOnClickListener(v -> {
            selectedMonth = selectedMonth.plusMonths(1);
            updateDashboard();
            renderTransactions();
        });

        Button currentMonthBtn = smallHeaderButton("Azi");
        currentMonthBtn.setOnClickListener(v -> {
            selectedMonth = YearMonth.now();
            updateDashboard();
            renderTransactions();
        });

        monthBar.addView(prevBtn, new LinearLayout.LayoutParams(dp(36), dp(36)));
        monthBar.addView(monthText, new LinearLayout.LayoutParams(0, WRAP, 1));
        monthBar.addView(nextBtn, new LinearLayout.LayoutParams(dp(36), dp(36)));
        monthBar.addView(currentMonthBtn, margin(WRAP, dp(32), dp(8), 0, 0, 0));
        root.addView(monthBar, margin(MATCH, WRAP, 0, 0, 0, dp(10)));

        // 2.5 Currency Tabs Switcher (RON vs VALUTĂ)
        LinearLayout currencyTabContainer = new LinearLayout(this);
        currencyTabContainer.setOrientation(LinearLayout.HORIZONTAL);
        currencyTabContainer.setGravity(Gravity.CENTER);
        currencyTabContainer.setPadding(dp(4), dp(4), dp(4), dp(4));
        currencyTabContainer.setBackground(round(surfaceColor(), dp(14), borderColor()));

        ronTabBtn = new Button(this);
        ronTabBtn.setText("🇷🇴 Cheltuieli Lei (RON)");
        ronTabBtn.setTextSize(12);
        ronTabBtn.setAllCaps(false);
        ronTabBtn.setPadding(dp(10), dp(6), dp(10), dp(6));
        ronTabBtn.setOnClickListener(v -> {
            selectedCurrencyTab = "RON";
            renderCurrencyTabs();
            updateDashboard();
            renderTransactions();
        });

        valutaTabBtn = new Button(this);
        valutaTabBtn.setText("🌍 Alte Valute (EUR / USD)");
        valutaTabBtn.setTextSize(12);
        valutaTabBtn.setAllCaps(false);
        valutaTabBtn.setPadding(dp(10), dp(6), dp(10), dp(6));
        valutaTabBtn.setOnClickListener(v -> {
            selectedCurrencyTab = "VALUTA";
            renderCurrencyTabs();
            updateDashboard();
            renderTransactions();
        });

        LinearLayout.LayoutParams tabParams1 = new LinearLayout.LayoutParams(0, dp(36), 1f);
        LinearLayout.LayoutParams tabParams2 = new LinearLayout.LayoutParams(0, dp(36), 1.1f);
        tabParams2.leftMargin = dp(4);

        currencyTabContainer.addView(ronTabBtn, tabParams1);
        currencyTabContainer.addView(valutaTabBtn, tabParams2);
        root.addView(currencyTabContainer, margin(MATCH, WRAP, 0, 0, 0, dp(10)));
        renderCurrencyTabs();

        // 3. Hero Card: Total Monthly Spending & Insights
        LinearLayout totalCard = card();
        totalCard.setBackground(round(surfaceColor(), dp(16), borderColor()));
        totalCard.setPadding(dp(18), dp(16), dp(18), dp(16));

        totalLabel = text("TOTAL LUNA ACEASTA", 11, textMuted(), true);
        totalLabel.setLetterSpacing(0.06f);
        totalCard.addView(totalLabel);

        totalText = text("0,00 lei", 32, primaryDark(), true);
        totalCard.addView(totalText, margin(WRAP, WRAP, 0, dp(4), 0, dp(8)));

        LinearLayout statsRow = new LinearLayout(this);
        statsRow.setOrientation(LinearLayout.HORIZONTAL);
        statsRow.setGravity(Gravity.CENTER_VERTICAL);

        countText = text("0 cheltuieli", 13, textMuted(), false);
        statsRow.addView(countText, new LinearLayout.LayoutParams(0, WRAP, 1));

        avgDailyText = text("", 11, primaryGreen(), true);
        avgDailyText.setPadding(dp(8), dp(3), dp(8), dp(3));
        avgDailyText.setBackground(round(accentLight(), dp(10), Color.TRANSPARENT));
        statsRow.addView(avgDailyText);

        totalCard.addView(statsRow);

        topExpenseText = text("", 11, textMuted(), false);
        totalCard.addView(topExpenseText, margin(MATCH, WRAP, 0, dp(8), 0, 0));

        root.addView(totalCard, margin(MATCH, WRAP, 0, 0, 0, dp(10)));

        // 4. Monthly Budget Tracker Card (QoL Feature)
        budgetCard = card();
        budgetCard.setBackground(round(surfaceColor(), dp(14), borderColor()));
        budgetCard.setPadding(dp(16), dp(12), dp(16), dp(12));
        budgetCard.setOnClickListener(v -> openSetBudgetDialog());
        root.addView(budgetCard, margin(MATCH, WRAP, 0, 0, 0, dp(12)));

        // 5. Quick Action Buttons
        LinearLayout actionRow = new LinearLayout(this);
        actionRow.setOrientation(LinearLayout.HORIZONTAL);

        Button addManualBtn = button("＋ Adaugă", true);
        addManualBtn.setOnClickListener(v -> openManualAddDialog());
        actionRow.addView(addManualBtn, new LinearLayout.LayoutParams(0, dp(48), 1f));

        Button importPdfButton = button("📄 Extras bancar", false);
        importPdfButton.setOnClickListener(v -> openFilePicker());
        LinearLayout.LayoutParams importParams = new LinearLayout.LayoutParams(0, dp(48), 1f);
        importParams.leftMargin = dp(10);
        actionRow.addView(importPdfButton, importParams);

        root.addView(actionRow, margin(MATCH, WRAP, 0, 0, 0, dp(12)));

        // 6. Real-Time Sync Status Card
        syncCard = card();
        updateSyncCard();
        root.addView(syncCard, margin(MATCH, WRAP, 0, 0, 0, dp(12)));

        // 7. Category Breakdown Card (Visual Graphical Progress Bars)
        LinearLayout categoryCard = card();
        categoryCard.setBackground(round(surfaceColor(), dp(16), borderColor()));
        categoryCard.setPadding(dp(18), dp(16), dp(18), dp(16));

        TextView catTitle = text("REPARTIZARE PE CATEGORII", 11, textMuted(), true);
        catTitle.setLetterSpacing(0.06f);
        categoryCard.addView(catTitle, margin(MATCH, WRAP, 0, 0, 0, dp(12)));

        categoryBreakdownContainer = new LinearLayout(this);
        categoryBreakdownContainer.setOrientation(LinearLayout.VERTICAL);
        categoryCard.addView(categoryBreakdownContainer);

        root.addView(categoryCard, margin(MATCH, WRAP, 0, 0, 0, dp(14)));

        // 8. Draft Section for Reviewing Imported Transactions (BUTTONS AT THE TOP!)
        draftSection = new LinearLayout(this);
        draftSection.setOrientation(LinearLayout.VERTICAL);
        draftSection.setVisibility(View.GONE);

        LinearLayout draftCard = card();
        draftCard.addView(text("VERIFICĂ TRANZACȚIILE IMPORTATE", 11, primaryGreen(), true));
        statusText = text("", 12, textMain(), false);
        statusText.setLineSpacing(dp(2), 1f);
        draftCard.addView(statusText, margin(MATCH, WRAP, 0, dp(6), 0, dp(8)));

        progressBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progressBar.setIndeterminate(true);
        progressBar.setVisibility(View.GONE);
        draftCard.addView(progressBar, margin(MATCH, dp(4), 0, 0, 0, dp(8)));

        // TOP ACTION ROW - User requested: Save button right here at the top!
        LinearLayout draftTopActions = new LinearLayout(this);
        draftTopActions.setOrientation(LinearLayout.HORIZONTAL);

        draftSaveBtnTop = button("✓ Salvează toate", true);
        draftSaveBtnTop.setOnClickListener(v -> saveDrafts());
        draftTopActions.addView(draftSaveBtnTop, new LinearLayout.LayoutParams(0, dp(44), 1.3f));

        Button cancelDraftsTop = button("✕ Renunță", false);
        cancelDraftsTop.setOnClickListener(v -> cancelImport());
        LinearLayout.LayoutParams cancelParams = new LinearLayout.LayoutParams(0, dp(44), 0.7f);
        cancelParams.leftMargin = dp(8);
        draftTopActions.addView(cancelDraftsTop, cancelParams);

        draftCard.addView(draftTopActions, margin(MATCH, WRAP, 0, 0, 0, dp(8)));

        TextView smartTip = text("💡 Sfat: Când schimbi categoria unei plăți, toate tranzacțiile similare se actualizează automat!", 11, textMuted(), false);
        draftCard.addView(smartTip, margin(MATCH, WRAP, 0, 0, 0, dp(10)));

        draftRows = new LinearLayout(this);
        draftRows.setOrientation(LinearLayout.VERTICAL);
        draftCard.addView(draftRows);

        // Bottom action button (for convenience if user scrolled down)
        draftSaveBtnBottom = button("✓ Salvează toate cheltuielile", true);
        draftSaveBtnBottom.setOnClickListener(v -> saveDrafts());
        draftCard.addView(draftSaveBtnBottom, margin(MATCH, dp(44), 0, dp(12), 0, dp(4)));

        draftSection.addView(draftCard);
        root.addView(draftSection, margin(MATCH, WRAP, 0, 0, 0, dp(14)));

        // 9. Transactions Section Title
        LinearLayout transHeader = new LinearLayout(this);
        transHeader.setOrientation(LinearLayout.HORIZONTAL);
        transHeader.setGravity(Gravity.CENTER_VERTICAL);

        transactionListTitle = text("ISTORIC CHELTUIELI", 11, textMuted(), true);
        transactionListTitle.setLetterSpacing(0.06f);
        transHeader.addView(transactionListTitle, new LinearLayout.LayoutParams(0, WRAP, 1));
        root.addView(transHeader, margin(MATCH, WRAP, 0, dp(4), 0, dp(8)));

        // 10. Modern Search Bar
        searchInput = new EditText(this);
        searchInput.setHint("🔍 Caută comerciant, magazin sau sumă…");
        searchInput.setHintTextColor(textMuted());
        searchInput.setTextSize(13);
        searchInput.setPadding(dp(14), dp(11), dp(14), dp(11));
        searchInput.setBackground(round(inputBg(), dp(12), borderColor()));
        searchInput.setTextColor(textMain());
        searchInput.setSingleLine(true);
        searchInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                currentSearchQuery = s.toString().trim().toLowerCase(Locale.ROOT);
                renderTransactions();
            }
            @Override public void afterTextChanged(Editable s) {}
        });
        root.addView(searchInput, margin(MATCH, WRAP, 0, 0, 0, dp(8)));

        // 11. Horizontal Category Filter Chips
        HorizontalScrollView chipScroll = new HorizontalScrollView(this);
        chipScroll.setHorizontalScrollBarEnabled(false);
        chipsContainer = new LinearLayout(this);
        chipsContainer.setOrientation(LinearLayout.HORIZONTAL);
        chipsContainer.setPadding(0, dp(2), 0, dp(4));
        chipScroll.addView(chipsContainer);
        renderCategoryChips();
        root.addView(chipScroll, margin(MATCH, WRAP, 0, 0, 0, dp(8)));

        // 12. Active Filter & Live Sum Summary Card (QoL Feature)
        filterSummaryCard = card();
        filterSummaryCard.setBackground(round(accentLight(), dp(12), primaryGreen()));
        filterSummaryCard.setPadding(dp(14), dp(10), dp(14), dp(10));
        filterSummaryCard.setVisibility(View.GONE);

        LinearLayout filterRow = new LinearLayout(this);
        filterRow.setOrientation(LinearLayout.HORIZONTAL);
        filterRow.setGravity(Gravity.CENTER_VERTICAL);

        filterSummaryText = text("", 12, primaryDark(), true);
        filterRow.addView(filterSummaryText, new LinearLayout.LayoutParams(0, WRAP, 1));

        Button resetFilterBtn = smallHeaderButton("✕ Resetează");
        resetFilterBtn.setOnClickListener(v -> {
            selectedCategoryFilter = "Toate";
            currentSearchQuery = "";
            searchInput.setText("");
            renderCategoryChips();
            renderTransactions();
        });
        filterRow.addView(resetFilterBtn, new LinearLayout.LayoutParams(WRAP, dp(30)));

        filterSummaryCard.addView(filterRow);
        root.addView(filterSummaryCard, margin(MATCH, WRAP, 0, 0, 0, dp(10)));

        // 13. Transaction List Rows Container
        transactionRows = new LinearLayout(this);
        transactionRows.setOrientation(LinearLayout.VERTICAL);
        root.addView(transactionRows, margin(MATCH, WRAP, 0, 0, 0, dp(20)));

        // 14. Footer
        TextView footer = text("Bani pe scurt · Confidențialitate 100% offline pe dispozitiv", 11, textMuted(), false);
        footer.setGravity(Gravity.CENTER);
        root.addView(footer, margin(MATCH, WRAP, 0, dp(4), 0, dp(8)));

        setContentView(scroll);
    }

    private void renderCategoryChips() {
        if (chipsContainer == null) return;
        chipsContainer.removeAllViews();

        List<String> categories = new ArrayList<>();
        categories.add("Toate");
        for (String cat : ExpenseCategories.ALL) categories.add(cat);

        for (String cat : categories) {
            boolean isSelected = cat.equals(selectedCategoryFilter);
            Button chip = new Button(this);
            String label = "Toate".equals(cat) ? "Toate" : (getCategoryStyle(cat).icon + " " + cat);
            chip.setText(label);
            chip.setTextSize(12);
            chip.setTypeface(Typeface.DEFAULT, isSelected ? Typeface.BOLD : Typeface.NORMAL);
            chip.setAllCaps(false);
            chip.setPadding(dp(14), dp(2), dp(14), dp(2));

            if (isSelected) {
                chip.setTextColor(Color.WHITE);
                chip.setBackground(round(primaryGreen(), dp(16), primaryGreen()));
            } else {
                chip.setTextColor(textMuted());
                chip.setBackground(round(surfaceColor(), dp(16), borderColor()));
            }

            chip.setOnClickListener(v -> {
                selectedCategoryFilter = cat;
                renderCategoryChips();
                renderTransactions();
            });

            chipsContainer.addView(chip, margin(WRAP, dp(34), 0, 0, dp(6), 0));
        }
    }

    private void renderCurrencyTabs() {
        if (ronTabBtn == null || valutaTabBtn == null) return;
        boolean isRon = "RON".equalsIgnoreCase(selectedCurrencyTab);

        ronTabBtn.setTypeface(Typeface.DEFAULT, isRon ? Typeface.BOLD : Typeface.NORMAL);
        ronTabBtn.setTextColor(isRon ? Color.WHITE : textMuted());
        ronTabBtn.setBackground(isRon ? round(primaryGreen(), dp(10), primaryGreen()) : round(Color.TRANSPARENT, dp(10), Color.TRANSPARENT));

        valutaTabBtn.setTypeface(Typeface.DEFAULT, !isRon ? Typeface.BOLD : Typeface.NORMAL);
        valutaTabBtn.setTextColor(!isRon ? Color.WHITE : textMuted());
        valutaTabBtn.setBackground(!isRon ? round(primaryGreen(), dp(10), primaryGreen()) : round(Color.TRANSPARENT, dp(10), Color.TRANSPARENT));
    }

    private boolean isNotificationListenerEnabled() {
        try {
            String pkgName = getPackageName();
            String flat = Settings.Secure.getString(getContentResolver(), "enabled_notification_listeners");
            return flat != null && flat.contains(pkgName);
        } catch (Throwable t) {
            return false;
        }
    }

    private void updateSyncCard() {
        if (syncCard == null) return;
        syncCard.removeAllViews();

        boolean isEnabled = isNotificationListenerEnabled();
        boolean isConnected = BankNotificationListener.isServiceConnected;

        if (isEnabled) {
            syncCard.setBackground(round(accentLight(), dp(14), primaryGreen()));
            syncCard.setPadding(dp(16), dp(12), dp(16), dp(12));

            LinearLayout topRow = new LinearLayout(this);
            topRow.setOrientation(LinearLayout.HORIZONTAL);
            topRow.setGravity(Gravity.CENTER_VERTICAL);

            LinearLayout textCol = new LinearLayout(this);
            textCol.setOrientation(LinearLayout.VERTICAL);

            String statusTitle = isConnected ? "⚡ Sincronizare plăți activă & conectată" : "⚡ Sincronizare activată (Necesită rebind)";
            TextView header = text(statusTitle, 12, primaryDark(), true);
            textCol.addView(header);

            String statusDesc = isConnected
                    ? "Gata să detecteze plăți la POS (Google Wallet, bănci, SMS)"
                    : "Serviciul necesită reconectare în fundal (Xiaomi HyperOS)";
            TextView desc = text(statusDesc, 11, textMuted(), false);
            textCol.addView(desc);
            topRow.addView(textCol, new LinearLayout.LayoutParams(0, WRAP, 1));

            syncCard.addView(topRow);

            // Action buttons row: [ Diagnostic & Jurnal ] + [ Trimite test ]
            LinearLayout btnsRow = new LinearLayout(this);
            btnsRow.setOrientation(LinearLayout.HORIZONTAL);

            Button diagBtn = smallHeaderButton("🛠️ Diagnostic & Jurnal live");
            diagBtn.setOnClickListener(v -> showNotificationDiagnosticDialog());
            btnsRow.addView(diagBtn, new LinearLayout.LayoutParams(0, dp(34), 1f));

            Button testBtn = smallHeaderButton("⚡ Trimite test");
            testBtn.setOnClickListener(v -> {
                BankNotificationListener.postSimulatedPaymentNotification(this);
                Toast.makeText(this, "Am trimis notificare de test! Trage în jos bara de sus.", Toast.LENGTH_SHORT).show();
            });
            LinearLayout.LayoutParams testParams = new LinearLayout.LayoutParams(WRAP, dp(34));
            testParams.leftMargin = dp(8);
            btnsRow.addView(testBtn, testParams);

            syncCard.addView(btnsRow, margin(MATCH, WRAP, 0, dp(8), 0, 0));
        } else {
            syncCard.setBackground(round(surfaceColor(), dp(14), borderColor()));
            syncCard.setPadding(dp(16), dp(14), dp(16), dp(14));

            TextView header = text("⚡ Sincronizare în timp real", 12, primaryDark(), true);
            syncCard.addView(header);

            TextView desc = text("Vrei ca plățile să se adauge automat la atingerea POS-ului? Activează captarea notificărilor din Google Wallet, Revolut sau YOU BRD.", 12, textMuted(), false);
            desc.setLineSpacing(dp(3), 1f);
            syncCard.addView(desc, margin(MATCH, WRAP, 0, dp(4), 0, dp(10)));

            LinearLayout btnsRow = new LinearLayout(this);
            btnsRow.setOrientation(LinearLayout.HORIZONTAL);

            Button enableBtn = button("Activează captarea automată →", true);
            enableBtn.setOnClickListener(v -> {
                try {
                    startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS));
                    Toast.makeText(this, "Activează „Bani pe scurt” în lista de notificări.", Toast.LENGTH_LONG).show();
                } catch (Exception e) {
                    Toast.makeText(this, "Deschide Setări > Aplicații > Acces special.", Toast.LENGTH_SHORT).show();
                }
            });
            btnsRow.addView(enableBtn, new LinearLayout.LayoutParams(0, dp(44), 1.3f));

            Button diagBtn = smallHeaderButton("Ghid Xiaomi 📱");
            diagBtn.setOnClickListener(v -> showNotificationDiagnosticDialog());
            LinearLayout.LayoutParams diagParams = new LinearLayout.LayoutParams(0, dp(44), 0.7f);
            diagParams.leftMargin = dp(8);
            btnsRow.addView(diagBtn, diagParams);

            syncCard.addView(btnsRow);
        }
    }

    private void showNotificationDiagnosticDialog() {
        AlertDialog.Builder builder = createDialogBuilder("Diagnostic Notificări în Timp Real");

        ScrollView sv = new ScrollView(this);
        sv.setFillViewport(true);

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(18), dp(14), dp(18), dp(16));

        // 1. Status Section
        boolean isEnabled = isNotificationListenerEnabled();
        boolean isConnected = BankNotificationListener.isServiceConnected;

        TextView permStatus = text("• Permisiune Android: " + (isEnabled ? "ACTIVATĂ ✅" : "DEZACTIVATĂ ❌"), 13, isEnabled ? primaryGreen() : DANGER_RED, true);
        layout.addView(permStatus);

        TextView connStatus = text("• Serviciu în fundal: " + (isConnected ? "CONECTAT ✅ (Activ)" : "DECONECTAT ⚠️ (Necesită rebind)"), 13, isConnected ? primaryGreen() : DANGER_RED, true);
        layout.addView(connStatus, margin(MATCH, WRAP, 0, dp(4), 0, dp(10)));

        // Action Buttons: Rebind & Test Notification
        LinearLayout actionRow = new LinearLayout(this);
        actionRow.setOrientation(LinearLayout.HORIZONTAL);

        Button rebindBtn = smallHeaderButton("🔄 Reconectează");
        rebindBtn.setOnClickListener(v -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                try {
                    android.service.notification.NotificationListenerService.requestRebind(
                            new android.content.ComponentName(this, BankNotificationListener.class)
                    );
                    Toast.makeText(this, "Reconectare solicitată! Verifică din nou peste 2 secunde.", Toast.LENGTH_SHORT).show();
                } catch (Throwable t) {
                    Toast.makeText(this, "Eroare rebind: " + t.getMessage(), Toast.LENGTH_SHORT).show();
                }
            }
        });
        actionRow.addView(rebindBtn, new LinearLayout.LayoutParams(0, dp(36), 1f));

        Button testBtn = smallHeaderButton("⚡ Notificare bară");
        testBtn.setOnClickListener(v -> {
            BankNotificationListener.postSimulatedPaymentNotification(this);
        });
        LinearLayout.LayoutParams testParams = new LinearLayout.LayoutParams(0, dp(36), 1.1f);
        testParams.leftMargin = dp(6);
        actionRow.addView(testBtn, testParams);

        Button directBtn = smallHeaderButton("🧪 Test direct");
        directBtn.setOnClickListener(v -> {
            BankNotificationListener.simulateDirectPayment(this, 3550, "Mega Image [TEST]", "Mâncare", "RON");
            loadSavedExpenses();
            updateDashboard();
            renderTransactions();
        });
        LinearLayout.LayoutParams directParams = new LinearLayout.LayoutParams(0, dp(36), 1f);
        directParams.leftMargin = dp(6);
        actionRow.addView(directBtn, directParams);

        layout.addView(actionRow, margin(MATCH, WRAP, 0, 0, 0, dp(14)));

        // 2. Xiaomi Specific Instructions
        TextView xiaomiHeader = text("📱 Pași esențiali pe Xiaomi (HyperOS / MIUI):", 12, primaryDark(), true);
        layout.addView(xiaomiHeader);

        String xiaomiTips = "1. Mergi în Setări telefon > Aplicații > Gestionare aplicații > Bani pe scurt.\n" +
                "2. Bifează „Pornire automată” (Autostart) – altfel Xiaomi oprește serviciul când închizi aplicația!\n" +
                "3. La „Economizor baterie”, alege „Fără restricții”.\n" +
                "4. Asigură-te că Google Wallet / aplicația băncii tale are permisiunea de a afișa notificări!";
        TextView xiaomiText = text(xiaomiTips, 11, textMuted(), false);
        xiaomiText.setLineSpacing(dp(3), 1f);
        layout.addView(xiaomiText, margin(MATCH, WRAP, 0, dp(4), 0, dp(14)));

        // 3. Notification Diagnostic Logs
        TextView logsHeader = text("📋 Ultimele notificări văzute de telefon:", 12, textMain(), true);
        layout.addView(logsHeader);

        List<org.json.JSONObject> logs = BankNotificationListener.getDiagnosticLogs(this);
        if (logs.isEmpty()) {
            TextView noLogs = text("Nicio notificare nu a fost interceptată încă de la pornire.\nFă o plată sau trimite o notificare de test pentru a verifica.", 11, textMuted(), false);
            noLogs.setLineSpacing(dp(3), 1f);
            layout.addView(noLogs, margin(MATCH, WRAP, 0, dp(4), 0, 0));
        } else {
            java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("HH:mm:ss", Locale.ROOT);
            for (org.json.JSONObject log : logs) {
                long time = log.optLong("time", 0);
                String pkg = log.optString("pkg", "");
                String title = log.optString("title", "");
                String textStr = log.optString("text", "");
                String status = log.optString("status", "");

                LinearLayout logItem = new LinearLayout(this);
                logItem.setOrientation(LinearLayout.VERTICAL);
                logItem.setPadding(dp(8), dp(6), dp(8), dp(6));
                logItem.setBackground(round(bgColor(), dp(6), borderColor()));

                String timeStr = time > 0 ? sdf.format(new java.util.Date(time)) : "";
                TextView top = text(timeStr + " · " + (title.isEmpty() ? pkg : title), 11, textMain(), true);
                logItem.addView(top);

                if (!textStr.isEmpty()) {
                    TextView body = text(textStr, 10, textMuted(), false);
                    logItem.addView(body);
                }

                int statusColor = status.startsWith("CAPURAT") ? primaryGreen() : (status.startsWith("CONECTAT") ? primaryGreen() : textMuted());
                TextView stat = text(status, 10, statusColor, true);
                logItem.addView(stat, margin(MATCH, WRAP, 0, dp(2), 0, 0));

                layout.addView(logItem, margin(MATCH, WRAP, 0, dp(4), 0, dp(4)));
            }
        }

        sv.addView(layout);
        builder.setView(sv);
        builder.setPositiveButton("Închide", null);
        builder.show();
    }

    private void triggerTestPayment() {
        long now = System.currentTimeMillis();
        String dateIso = LocalDate.now().toString();
        Expense sample = new Expense("LIVE|" + now + "|45.50", dateIso, "Mega Image Concept", "Mâncare", 4550, false);
        expenses.add(sample);
        saveExpenses();
        updateDashboard();
        renderTransactions();
        Toast.makeText(this, "Plată simulată adăugată: 45,50 lei la Mega Image!", Toast.LENGTH_SHORT).show();
    }

    private void openFilePicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        String[] mimeTypes = {"application/pdf", "text/csv", "text/comma-separated-values", "text/plain"};
        intent.putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes);
        startActivityForResult(intent, PICK_FILE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != PICK_FILE || resultCode != RESULT_OK || data == null || data.getData() == null) return;

        Uri uri = data.getData();
        importedFileName = queryDisplayName(uri);
        drafts.clear();
        draftSection.setVisibility(View.VISIBLE);
        progressBar.setVisibility(View.VISIBLE);
        statusText.setText("Citesc extrasul: " + importedFileName + "…");
        draftRows.removeAllViews();

        executor.execute(() -> {
            try (InputStream input = getContentResolver().openInputStream(uri)) {
                if (input == null) throw new IllegalArgumentException("Nu am putut deschide fișierul selectat.");

                boolean isCsv = importedFileName.toLowerCase(Locale.ROOT).endsWith(".csv") ||
                        "text/csv".equalsIgnoreCase(getContentResolver().getType(uri));

                BrdStatementParser.ImportResult result;
                if (isCsv) {
                    result = new BrdStatementParser().parseCsv(this, input);
                } else {
                    result = new BrdStatementParser().parse(this, input);
                }

                Set<String> knownIds = new HashSet<>();
                synchronized (expenses) {
                    for (Expense item : expenses) knownIds.add(item.id);
                }

                int alreadyImported = 0;
                for (Expense item : result.expenses) {
                    if (knownIds.contains(item.id)) {
                        alreadyImported++;
                    } else {
                        drafts.add(item);
                    }
                }

                final int duplicates = alreadyImported;
                mainHandler.post(() -> {
                    progressBar.setVisibility(View.GONE);
                    statusText.setText("" + drafts.size() + (drafts.size() == 1 ? " cheltuială nouă găsită" : " cheltuieli noi găsite") +
                            (result.incomeCount > 0 ? " · " + result.incomeCount + " încasări omise" : "") +
                            (duplicates > 0 ? " · " + duplicates + " duplicate omise" : ""));
                    renderDrafts();
                });
            } catch (Exception error) {
                mainHandler.post(() -> {
                    progressBar.setVisibility(View.GONE);
                    statusText.setText(error.getMessage() == null ? "Nu am putut citi acest fișier." : error.getMessage());
                    Toast.makeText(this, "Importul nu a reușit: " + error.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private String queryDisplayName(Uri uri) {
        try (android.database.Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME);
                if (index >= 0) return cursor.getString(index);
            }
        } catch (Exception ignored) { }
        return "Extras";
    }

    private void renderDrafts() {
        draftRows.removeAllViews();
        if (draftSaveBtnTop != null) draftSaveBtnTop.setText("✓ Salvează (" + drafts.size() + ")");
        if (draftSaveBtnBottom != null) draftSaveBtnBottom.setText("✓ Salvează (" + drafts.size() + ")");

        if (drafts.isEmpty()) {
            TextView empty = text("Nu au rămas tranzacții noi de adăugat.", 12, textMuted(), false);
            draftRows.addView(empty, margin(MATCH, WRAP, 0, dp(8), 0, 0));
            return;
        }

        int limit = drafts.size();
        for (int i = 0; i < limit; i++) {
            Expense expense = drafts.get(i);
            CategoryStyle style = getCategoryStyle(expense.category);

            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(12), dp(10), dp(12), dp(10));
            row.setBackground(round(surfaceColor(), dp(12), borderColor()));

            TextView iconView = new TextView(this);
            iconView.setText(style.icon);
            iconView.setTextSize(18);
            iconView.setGravity(Gravity.CENTER);
            iconView.setBackground(round(style.bgColor, dp(20), style.bgColor));
            row.addView(iconView, new LinearLayout.LayoutParams(dp(40), dp(40)));

            LinearLayout middle = new LinearLayout(this);
            middle.setOrientation(LinearLayout.VERTICAL);
            middle.setPadding(dp(10), 0, dp(8), 0);

            TextView top = text(expense.description, 13, textMain(), true);
            top.setMaxLines(1);
            top.setEllipsize(TextUtils.TruncateAt.END);
            middle.addView(top);

            LinearLayout subRow = new LinearLayout(this);
            subRow.setOrientation(LinearLayout.HORIZONTAL);
            subRow.setGravity(Gravity.CENTER_VERTICAL);

            TextView details = text(formatDate(expense.date), 11, textMuted(), false);
            subRow.addView(details);

            TextView dot = text("  •  ", 10, borderColor(), false);
            subRow.addView(dot);

            TextView catPill = text(expense.category + " ▾", 11, style.textColor, true);
            catPill.setPadding(dp(6), dp(1), dp(6), dp(1));
            catPill.setBackground(round(style.bgColor, dp(6), Color.TRANSPARENT));
            catPill.setOnClickListener(v -> showCategoryPickerDialog(expense, true));
            subRow.addView(catPill);

            middle.addView(subRow, margin(WRAP, WRAP, 0, dp(2), 0, 0));
            row.addView(middle, new LinearLayout.LayoutParams(0, WRAP, 1));

            TextView amountView = text(moneyBani(expense.amountBani), 13, textMain(), true);
            row.addView(amountView);

            // Quick exclude/delete button for draft items
            TextView removeDraftBtn = text("  ✕", 15, textMuted(), false);
            removeDraftBtn.setPadding(dp(8), dp(4), dp(4), dp(4));
            removeDraftBtn.setOnClickListener(v -> {
                drafts.remove(expense);
                renderDrafts();
                Toast.makeText(this, "Plată omisă din import.", Toast.LENGTH_SHORT).show();
            });
            row.addView(removeDraftBtn);

            draftRows.addView(row, margin(MATCH, WRAP, 0, dp(4), 0, dp(4)));
        }
    }

    private void cancelImport() {
        drafts.clear();
        draftSection.setVisibility(View.GONE);
        Toast.makeText(this, "Import anulat.", Toast.LENGTH_SHORT).show();
    }

    private void saveDrafts() {
        if (drafts.isEmpty()) {
            Toast.makeText(this, "Nu există cheltuieli noi de salvat.", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!drafts.get(0).date.isEmpty() && drafts.get(0).date.length() >= 7) {
            try {
                selectedMonth = YearMonth.parse(drafts.get(0).date.substring(0, 7));
            } catch (Exception ignored) {}
        }
        Set<String> existing = new HashSet<>();
        for (Expense expense : expenses) existing.add(expense.id);

        int added = 0;
        for (Expense expense : drafts) {
            if (existing.add(expense.id)) {
                expenses.add(expense);
                added++;
            }
        }
        saveExpenses();
        drafts.clear();
        draftSection.setVisibility(View.GONE);
        updateDashboard();
        renderTransactions();
        Toast.makeText(this, added + (added == 1 ? " cheltuială salvată." : " cheltuieli salvate."), Toast.LENGTH_SHORT).show();
    }

    private void renderTransactions() {
        transactionRows.removeAllViews();
        List<Expense> month = monthExpenses();
        Collections.sort(month, (a, b) -> b.date.compareTo(a.date));

        // Filter by category chip
        if (!"Toate".equals(selectedCategoryFilter)) {
            List<Expense> catFiltered = new ArrayList<>();
            for (Expense e : month) {
                if (selectedCategoryFilter.equals(e.category)) {
                    catFiltered.add(e);
                }
            }
            month = catFiltered;
        }

        // Filter by search query
        if (!currentSearchQuery.isEmpty()) {
            List<Expense> filtered = new ArrayList<>();
            for (Expense e : month) {
                if (e.description.toLowerCase(Locale.ROOT).contains(currentSearchQuery) ||
                        e.category.toLowerCase(Locale.ROOT).contains(currentSearchQuery)) {
                    filtered.add(e);
                }
            }
            month = filtered;
        }

        // Update Filter & Search Live Summary Card
        long filteredTotalBani = 0;
        for (Expense e : month) filteredTotalBani += e.amountBani;

        if (filterSummaryCard != null) {
            if (!"Toate".equals(selectedCategoryFilter) || !currentSearchQuery.isEmpty()) {
                filterSummaryCard.setVisibility(View.VISIBLE);
                String label = !"Toate".equals(selectedCategoryFilter)
                        ? (getCategoryStyle(selectedCategoryFilter).icon + " " + selectedCategoryFilter)
                        : ("„" + currentSearchQuery + "”");
                filterSummaryText.setText(label + "  ·  " + moneyBani(filteredTotalBani) + " (" + month.size() + (month.size() == 1 ? " plată" : " plăți") + ")");
            } else {
                filterSummaryCard.setVisibility(View.GONE);
            }
        }

        if (month.isEmpty()) {
            LinearLayout emptyCard = card();
            emptyCard.setGravity(Gravity.CENTER);
            emptyCard.setPadding(dp(20), dp(32), dp(20), dp(32));

            TextView emoji = text("🍃", 32, primaryGreen(), false);
            emoji.setGravity(Gravity.CENTER);
            emptyCard.addView(emoji);

            String msg = currentSearchQuery.isEmpty()
                    ? ("Toate".equals(selectedCategoryFilter)
                    ? "Nicio cheltuială în această lună.\nAtinge „＋ Adaugă” sau importă un extras."
                    : "Nicio cheltuială la categoria „" + selectedCategoryFilter + "”.")
                    : "Niciun rezultat pentru căutarea curentă.";

            TextView empty = text(msg, 13, textMuted(), false);
            empty.setGravity(Gravity.CENTER);
            empty.setLineSpacing(dp(3), 1f);
            emptyCard.addView(empty, margin(MATCH, WRAP, 0, dp(8), 0, 0));

            transactionRows.addView(emptyCard);
            return;
        }

        int limit = Math.min(month.size(), 60);
        for (int i = 0; i < limit; i++) {
            Expense expense = month.get(i);
            View row = createTransactionRow(expense);
            transactionRows.addView(row, margin(MATCH, WRAP, 0, 0, 0, dp(8)));
        }

        if (month.size() > limit) {
            TextView more = text("Și încă " + (month.size() - limit) + " cheltuieli în luna aceasta.", 12, textMuted(), false);
            more.setGravity(Gravity.CENTER);
            transactionRows.addView(more, margin(MATCH, WRAP, 0, dp(8), 0, 0));
        }
    }

    private View createTransactionRow(Expense expense) {
        CategoryStyle style = getCategoryStyle(expense.category);

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(12), dp(12), dp(14), dp(12));
        card.setBackground(round(surfaceColor(), dp(14), borderColor()));

        // 1. Left: Circle Category Icon
        TextView iconView = new TextView(this);
        iconView.setText(style.icon);
        iconView.setTextSize(18);
        iconView.setGravity(Gravity.CENTER);
        iconView.setBackground(round(style.bgColor, dp(21), style.bgColor));
        card.addView(iconView, new LinearLayout.LayoutParams(dp(42), dp(42)));

        // 2. Middle: Description + Date + Category Pill
        LinearLayout middle = new LinearLayout(this);
        middle.setOrientation(LinearLayout.VERTICAL);
        middle.setPadding(dp(12), 0, dp(8), 0);

        TextView title = new TextView(this);
        title.setText(expense.description);
        title.setTextSize(14);
        title.setTextColor(textMain());
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setMaxLines(1);
        title.setEllipsize(TextUtils.TruncateAt.END);
        middle.addView(title);

        LinearLayout subRow = new LinearLayout(this);
        subRow.setOrientation(LinearLayout.HORIZONTAL);
        subRow.setGravity(Gravity.CENTER_VERTICAL);

        TextView dateView = new TextView(this);
        dateView.setText(formatDate(expense.date));
        dateView.setTextSize(11);
        dateView.setTextColor(textMuted());
        subRow.addView(dateView);

        TextView dot = new TextView(this);
        dot.setText("  •  ");
        dot.setTextSize(10);
        dot.setTextColor(borderColor());
        subRow.addView(dot);

        // Clickable Category Pill
        TextView catPill = new TextView(this);
        catPill.setText(expense.category + " ▾");
        catPill.setTextSize(11);
        catPill.setTextColor(style.textColor);
        catPill.setTypeface(Typeface.DEFAULT_BOLD);
        catPill.setPadding(dp(6), dp(1), dp(6), dp(1));
        catPill.setBackground(round(style.bgColor, dp(6), Color.TRANSPARENT));
        catPill.setOnClickListener(v -> showCategoryPickerDialog(expense, false));
        subRow.addView(catPill);

        middle.addView(subRow, margin(WRAP, WRAP, 0, dp(3), 0, 0));
        card.addView(middle, new LinearLayout.LayoutParams(0, WRAP, 1));

        // 3. Right: Amount
        LinearLayout right = new LinearLayout(this);
        right.setOrientation(LinearLayout.VERTICAL);
        right.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);

        TextView amountView = new TextView(this);
        amountView.setText(expense.formatAmount());
        amountView.setTextSize(14);
        amountView.setTextColor(textMain());
        amountView.setTypeface(Typeface.DEFAULT_BOLD);
        right.addView(amountView);

        card.addView(right);

        // Tap on transaction opens edit / delete dialog
        card.setOnClickListener(v -> showEditExpenseDialog(expense));

        return card;
    }

    private void showCategoryPickerDialog(Expense expense, boolean isDraft) {
        AlertDialog.Builder builder = createDialogBuilder("Schimbă categoria");

        int currentIdx = ExpenseCategories.indexOf(expense.category);
        builder.setSingleChoiceItems(ExpenseCategories.ALL, currentIdx, (dialog, which) -> {
            String newCat = ExpenseCategories.ALL[which];
            expense.category = newCat;
            ExpenseCategories.learn(this, expense.description, newCat);

            if (isDraft) {
                // Smart bulk propagation: update all other matching draft items!
                String normTarget = BrdStatementParser.normalize(expense.description);
                int updatedCount = 0;
                for (Expense d : drafts) {
                    if (d != expense && BrdStatementParser.normalize(d.description).equals(normTarget)) {
                        d.category = newCat;
                        updatedCount++;
                    }
                }
                renderDrafts();
                if (updatedCount > 0) {
                    Toast.makeText(this, "Actualizat " + (updatedCount + 1) + " tranzacții similare la „" + newCat + "”!", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(this, "Categorie schimbată: " + newCat, Toast.LENGTH_SHORT).show();
                }
            } else {
                saveExpenses();
                updateDashboard();
                renderTransactions();
                Toast.makeText(this, "Categorie schimbată: " + newCat, Toast.LENGTH_SHORT).show();
            }
            dialog.dismiss();
        });
        builder.setNegativeButton("Anulează", null);
        builder.show();
    }

    private void openMonthPickerDialog() {
        AlertDialog.Builder builder = createDialogBuilder("Alege luna");

        List<YearMonth> recentMonths = new ArrayList<>();
        YearMonth now = YearMonth.now();
        for (int i = 2; i >= -12; i--) {
            recentMonths.add(now.plusMonths(i));
        }

        String[] monthNames = new String[recentMonths.size()];
        int selectedIndex = 0;
        Locale roLocale = new Locale("ro", "RO");
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("LLLL yyyy", roLocale);

        for (int i = 0; i < recentMonths.size(); i++) {
            YearMonth ym = recentMonths.get(i);
            String name = ym.format(fmt);
            monthNames[i] = name.substring(0, 1).toUpperCase(roLocale) + name.substring(1);
            if (ym.equals(selectedMonth)) selectedIndex = i;
        }

        builder.setSingleChoiceItems(monthNames, selectedIndex, (dialog, which) -> {
            selectedMonth = recentMonths.get(which);
            updateDashboard();
            renderTransactions();
            dialog.dismiss();
        });
        builder.setNegativeButton("Anulează", null);
        builder.show();
    }

    private void openSetBudgetDialog() {
        AlertDialog.Builder builder = createDialogBuilder("Buget lunar");

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(22), dp(14), dp(22), dp(10));

        TextView info = text("Setează suma maximă pe care dorești să o cheltuiești în această lună:", 12, textMuted(), false);
        layout.addView(info, margin(MATCH, WRAP, 0, 0, 0, dp(10)));

        final EditText budgetInput = new EditText(this);
        budgetInput.setHint("Ex: 2500");
        budgetInput.setHintTextColor(textMuted());
        budgetInput.setTextColor(textMain());
        budgetInput.setTextSize(16);
        budgetInput.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        budgetInput.setPadding(dp(12), dp(10), dp(12), dp(10));
        budgetInput.setBackground(round(inputBg(), dp(8), borderColor()));
        if (monthlyBudgetBani > 0) {
            budgetInput.setText(String.format(Locale.US, "%.0f", monthlyBudgetBani / 100.0));
        }
        layout.addView(budgetInput);

        builder.setView(layout);
        builder.setPositiveButton("Salvează", (dialog, which) -> {
            String str = budgetInput.getText().toString().trim().replace(',', '.');
            if (!str.isEmpty()) {
                try {
                    double val = Double.parseDouble(str);
                    monthlyBudgetBani = Math.round(val * 100.0);
                    getSharedPreferences(PREF_NAME, MODE_PRIVATE).edit().putLong(PREF_KEY_BUDGET, monthlyBudgetBani).apply();
                    updateDashboard();
                    Toast.makeText(this, "Buget salvat: " + moneyBani(monthlyBudgetBani), Toast.LENGTH_SHORT).show();
                } catch (Exception e) {
                    Toast.makeText(this, "Sumă invalidă.", Toast.LENGTH_SHORT).show();
                }
            }
        });
        if (monthlyBudgetBani > 0) {
            builder.setNeutralButton("Șterge bugetul", (dialog, which) -> {
                monthlyBudgetBani = 0;
                getSharedPreferences(PREF_NAME, MODE_PRIVATE).edit().remove(PREF_KEY_BUDGET).apply();
                updateDashboard();
                Toast.makeText(this, "Buget eliminat.", Toast.LENGTH_SHORT).show();
            });
        }
        builder.setNegativeButton("Anulează", null);
        builder.show();
    }

    private void updateDashboard() {
        if (monthText == null) return;
        Locale locale = new Locale("ro", "RO");
        String monthName = selectedMonth.format(DateTimeFormatter.ofPattern("LLLL yyyy", locale));
        monthText.setText(monthName.substring(0, 1).toUpperCase(locale) + monthName.substring(1) + " ▾");

        boolean isRonTab = "RON".equalsIgnoreCase(selectedCurrencyTab);
        if (totalLabel != null) {
            totalLabel.setText(isRonTab ? "TOTAL LUNA ACEASTA (LEI)" : "TOTAL VALUTĂ LUNA ACEASTA");
        }
        if (transactionListTitle != null) {
            transactionListTitle.setText(isRonTab ? "ISTORIC CHELTUIELI (LEI)" : "ISTORIC CHELTUIELI ÎN VALUTĂ");
        }

        List<Expense> month = monthExpenses();
        long totalBani = 0;
        Map<String, Long> byCategory = new LinkedHashMap<>();
        Expense maxExpense = null;

        for (Expense expense : month) {
            totalBani += expense.amountBani;
            byCategory.put(expense.category, byCategory.getOrDefault(expense.category, 0L) + expense.amountBani);
            if (maxExpense == null || expense.amountBani > maxExpense.amountBani) {
                maxExpense = expense;
            }
        }

        if (isRonTab) {
            totalText.setText(moneyBani(totalBani));
        } else {
            Map<String, Long> byCurr = new LinkedHashMap<>();
            for (Expense e : month) {
                String c = (e.currency != null && !e.currency.isEmpty()) ? e.currency.toUpperCase() : "EUR";
                byCurr.put(c, byCurr.getOrDefault(c, 0L) + e.amountBani);
            }
            if (byCurr.isEmpty()) {
                totalText.setText("0,00 €");
            } else {
                StringBuilder sb = new StringBuilder();
                for (Map.Entry<String, Long> ent : byCurr.entrySet()) {
                    if (sb.length() > 0) sb.append("  ·  ");
                    double val = ent.getValue() / 100.0;
                    if ("EUR".equalsIgnoreCase(ent.getKey())) sb.append(String.format(Locale.GERMANY, "%,.2f €", val));
                    else if ("USD".equalsIgnoreCase(ent.getKey())) sb.append(String.format(Locale.US, "$%,.2f", val));
                    else if ("GBP".equalsIgnoreCase(ent.getKey())) sb.append(String.format(Locale.UK, "£%,.2f", val));
                    else sb.append(String.format(Locale.GERMANY, "%,.2f %s", val, ent.getKey()));
                }
                totalText.setText(sb.toString());
            }
        }

        countText.setText(month.size() + (month.size() == 1 ? " cheltuială înregistrată" : " cheltuieli înregistrate"));

        // Daily average calculation (only on RON tab)
        if (isRonTab && totalBani > 0) {
            int days = selectedMonth.lengthOfMonth();
            if (selectedMonth.equals(YearMonth.now())) {
                days = Math.max(1, LocalDate.now().getDayOfMonth());
            }
            long dailyAvg = totalBani / days;
            avgDailyText.setText("~ " + moneyBani(dailyAvg) + " / zi");
            avgDailyText.setVisibility(View.VISIBLE);
        } else {
            avgDailyText.setVisibility(View.GONE);
        }

        // Top Single Expense Insight
        if (maxExpense != null && maxExpense.amountBani > 0) {
            topExpenseText.setText("⭐ Cea mai mare: " + maxExpense.formatAmount() + " · " + maxExpense.description);
            topExpenseText.setVisibility(View.VISIBLE);
        } else {
            topExpenseText.setVisibility(View.GONE);
        }

        // Budget Section (only on RON tab)
        if (!isRonTab) {
            budgetCard.setVisibility(View.GONE);
        } else {
            budgetCard.setVisibility(View.VISIBLE);
            updateBudgetSection(totalBani);
        }

        // Render graphical category progress bars
        renderCategoryBreakdown(byCategory, totalBani);
    }

    private void updateBudgetSection(long totalBani) {
        if (budgetCard == null) return;
        budgetCard.removeAllViews();

        if (monthlyBudgetBani <= 0) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);

            TextView prompt = text("🎯 Setează un buget lunar pentru economii", 12, textMuted(), false);
            row.addView(prompt, new LinearLayout.LayoutParams(0, WRAP, 1));

            TextView setLabel = text("Setează ＋", 12, primaryGreen(), true);
            row.addView(setLabel);

            budgetCard.addView(row);
            return;
        }

        long remaining = monthlyBudgetBani - totalBani;
        int percent = (int) Math.round((double) totalBani / monthlyBudgetBani * 100);
        int barColor = (percent > 100) ? DANGER_RED : ((percent > 85) ? Color.rgb(245, 124, 0) : primaryGreen());

        LinearLayout topRow = new LinearLayout(this);
        topRow.setOrientation(LinearLayout.HORIZONTAL);
        topRow.setGravity(Gravity.CENTER_VERTICAL);

        TextView label = text("BUGET LUNAR", 10, textMuted(), true);
        label.setLetterSpacing(0.06f);
        topRow.addView(label, new LinearLayout.LayoutParams(0, WRAP, 1));

        TextView spentLabel = text(percent + "%  ·  " + moneyBani(totalBani) + " / " + moneyBani(monthlyBudgetBani), 12, textMain(), true);
        topRow.addView(spentLabel);
        budgetCard.addView(topRow);

        // Visual progress bar
        LinearLayout bar = createProgressBar(Math.min(100f, percent), barColor);
        budgetCard.addView(bar, margin(MATCH, dp(7), 0, dp(6), 0, dp(6)));

        LinearLayout btmRow = new LinearLayout(this);
        btmRow.setOrientation(LinearLayout.HORIZONTAL);
        btmRow.setGravity(Gravity.CENTER_VERTICAL);

        String remStr = remaining >= 0
                ? ("Disponibil: " + moneyBani(remaining))
                : ("Ai depășit bugetul cu " + moneyBani(-remaining) + " ⚠️");
        int remColor = remaining >= 0 ? primaryGreen() : DANGER_RED;
        TextView remView = text(remStr, 12, remColor, true);
        btmRow.addView(remView, new LinearLayout.LayoutParams(0, WRAP, 1));

        TextView editHint = text("Modifică ✎", 11, textMuted(), false);
        btmRow.addView(editHint);

        budgetCard.addView(btmRow);
    }

    private void renderCategoryBreakdown(Map<String, Long> byCategory, long totalBani) {
        if (categoryBreakdownContainer == null) return;
        categoryBreakdownContainer.removeAllViews();

        if (byCategory.isEmpty() || totalBani == 0) {
            TextView empty = text("Nicio cheltuială înregistrată pentru această lună.", 12, textMuted(), false);
            empty.setPadding(0, dp(4), 0, dp(4));
            categoryBreakdownContainer.addView(empty);
            return;
        }

        List<Map.Entry<String, Long>> ranked = new ArrayList<>(byCategory.entrySet());
        ranked.sort((a, b) -> Long.compare(b.getValue(), a.getValue()));

        for (int i = 0; i < ranked.size(); i++) {
            Map.Entry<String, Long> entry = ranked.get(i);
            String category = entry.getKey();
            long bani = entry.getValue();
            float percent = (float) Math.round((double) bani / totalBani * 1000.0) / 10.0f;
            CategoryStyle style = getCategoryStyle(category);

            LinearLayout item = new LinearLayout(this);
            item.setOrientation(LinearLayout.VERTICAL);
            item.setPadding(0, dp(5), 0, dp(6));

            // Labels row
            LinearLayout labels = new LinearLayout(this);
            labels.setOrientation(LinearLayout.HORIZONTAL);
            labels.setGravity(Gravity.CENTER_VERTICAL);

            TextView nameView = text(style.icon + "  " + category, 13, textMain(), true);
            labels.addView(nameView, new LinearLayout.LayoutParams(0, WRAP, 1));

            TextView valView = text(moneyBani(bani) + "  ·  " + String.format(Locale.US, "%.0f%%", percent), 12, textMain(), true);
            labels.addView(valView);
            item.addView(labels, margin(MATCH, WRAP, 0, 0, 0, dp(6)));

            // Visual Progress Bar
            LinearLayout bar = createProgressBar(percent, style.barColor);
            item.addView(bar, margin(MATCH, dp(7), 0, 0, 0, dp(4)));

            // Tap on category in breakdown filters the list!
            item.setOnClickListener(v -> {
                selectedCategoryFilter = category;
                renderCategoryChips();
                renderTransactions();
                Toast.makeText(this, "Filtru: " + category, Toast.LENGTH_SHORT).show();
            });

            categoryBreakdownContainer.addView(item);
        }
    }

    private LinearLayout createProgressBar(float percent, int color) {
        LinearLayout track = new LinearLayout(this);
        track.setOrientation(LinearLayout.HORIZONTAL);
        track.setBackground(round(trackBg(), dp(4), Color.TRANSPARENT));
        track.setWeightSum(100f);

        View progress = new View(this);
        progress.setBackground(round(color, dp(4), color));
        LinearLayout.LayoutParams fillParams = new LinearLayout.LayoutParams(0, dp(7), Math.max(1f, percent));
        track.addView(progress, fillParams);

        if (percent < 100f) {
            View empty = new View(this);
            track.addView(empty, new LinearLayout.LayoutParams(0, dp(7), 100f - percent));
        }
        return track;
    }

    private List<Expense> monthExpenses() {
        List<Expense> month = new ArrayList<>();
        String prefix = selectedMonth.toString();
        boolean isRonTab = "RON".equalsIgnoreCase(selectedCurrencyTab);

        for (Expense expense : expenses) {
            if (expense.date != null && expense.date.startsWith(prefix)) {
                boolean isExpenseRon = expense.currency == null || "RON".equalsIgnoreCase(expense.currency);
                if (isRonTab && isExpenseRon) {
                    month.add(expense);
                } else if (!isRonTab && !isExpenseRon) {
                    month.add(expense);
                }
            }
        }
        return month;
    }

    private AlertDialog.Builder createDialogBuilder(String title) {
        AlertDialog.Builder builder;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
            builder = new AlertDialog.Builder(this, isDarkMode ? AlertDialog.THEME_DEVICE_DEFAULT_DARK : AlertDialog.THEME_DEVICE_DEFAULT_LIGHT);
        } else {
            builder = new AlertDialog.Builder(this);
        }
        if (title != null) builder.setTitle(title);
        return builder;
    }

    private void openManualAddDialog() {
        AlertDialog.Builder builder = createDialogBuilder("Adaugă cheltuială");

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(22), dp(14), dp(22), dp(10));

        final EditText descInput = new EditText(this);
        descInput.setHint("Comerciant (ex: Kaufland, Bolt, Farmacie)");
        descInput.setHintTextColor(textMuted());
        descInput.setTextColor(textMain());
        descInput.setTextSize(14);
        descInput.setPadding(dp(12), dp(10), dp(12), dp(10));
        descInput.setBackground(round(inputBg(), dp(8), borderColor()));
        layout.addView(descInput, margin(MATCH, WRAP, 0, 0, 0, dp(10)));

        final EditText amountInput = new EditText(this);
        amountInput.setHint("Sumă (ex: 35.50)");
        amountInput.setHintTextColor(textMuted());
        amountInput.setTextColor(textMain());
        amountInput.setTextSize(14);
        amountInput.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        amountInput.setPadding(dp(12), dp(10), dp(12), dp(10));
        amountInput.setBackground(round(inputBg(), dp(8), borderColor()));
        layout.addView(amountInput, margin(MATCH, WRAP, 0, 0, 0, dp(10)));

        final String[] currencies = {"RON", "EUR", "USD", "GBP"};
        final int[] selectedCurrIdx = { "VALUTA".equalsIgnoreCase(selectedCurrencyTab) ? 1 : 0 };
        TextView currSelector = text("Monedă: " + currencies[selectedCurrIdx[0]] + " ▾", 12, primaryDark(), true);
        currSelector.setPadding(dp(12), dp(8), dp(12), dp(8));
        currSelector.setBackground(round(accentLight(), dp(8), borderColor()));
        currSelector.setOnClickListener(v -> {
            createDialogBuilder("Selectează moneda")
                    .setSingleChoiceItems(currencies, selectedCurrIdx[0], (d, which) -> {
                        selectedCurrIdx[0] = which;
                        currSelector.setText("Monedă: " + currencies[which] + " ▾");
                        d.dismiss();
                    }).show();
        });
        layout.addView(currSelector, margin(MATCH, WRAP, 0, 0, 0, dp(10)));

        TextView catLabel = text("Alege categoria:", 12, textMuted(), true);
        layout.addView(catLabel, margin(MATCH, WRAP, 0, 0, 0, dp(6)));

        final int[] selectedCatIdx = {0};
        TextView categorySelector = text("🛒  Mâncare  ▾", 13, primaryDark(), true);
        categorySelector.setPadding(dp(14), dp(10), dp(14), dp(10));
        categorySelector.setBackground(round(accentLight(), dp(8), primaryGreen()));
        categorySelector.setOnClickListener(v -> {
            createDialogBuilder("Selectează categoria")
                    .setSingleChoiceItems(ExpenseCategories.ALL, selectedCatIdx[0], (d, which) -> {
                        selectedCatIdx[0] = which;
                        String cat = ExpenseCategories.ALL[which];
                        categorySelector.setText(getCategoryStyle(cat).icon + "  " + cat + "  ▾");
                        d.dismiss();
                    }).show();
        });
        layout.addView(categorySelector, margin(MATCH, WRAP, 0, 0, 0, dp(4)));

        builder.setView(layout);
        builder.setPositiveButton("Salvează", (dialog, which) -> {
            String desc = descInput.getText().toString().trim();
            String amtStr = amountInput.getText().toString().trim().replace(',', '.');
            if (desc.isEmpty() || amtStr.isEmpty()) {
                Toast.makeText(this, "Completează descrierea și suma.", Toast.LENGTH_SHORT).show();
                return;
            }
            try {
                double amt = Double.parseDouble(amtStr);
                String cat = ExpenseCategories.ALL[selectedCatIdx[0]];
                Expense expense = new Expense();
                expense.id = "MANUAL|" + System.currentTimeMillis();
                expense.date = LocalDate.now().toString();
                expense.description = desc;
                expense.category = cat;
                expense.currency = currencies[selectedCurrIdx[0]];
                expense.setAmount(amt);
                expense.income = false;

                expenses.add(expense);
                saveExpenses();
                updateDashboard();
                renderTransactions();
                Toast.makeText(this, "Cheltuială adăugată: " + expense.formatAmount(), Toast.LENGTH_SHORT).show();
            } catch (Exception e) {
                Toast.makeText(this, "Sumă invalidă.", Toast.LENGTH_SHORT).show();
            }
        });
        builder.setNegativeButton("Anulează", null);
        builder.show();
    }

    private void showEditExpenseDialog(Expense expense) {
        AlertDialog.Builder builder = createDialogBuilder("Editează cheltuiala");

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(22), dp(14), dp(22), dp(10));

        final EditText descInput = new EditText(this);
        descInput.setText(expense.description);
        descInput.setTextColor(textMain());
        descInput.setTextSize(14);
        descInput.setPadding(dp(12), dp(10), dp(12), dp(10));
        descInput.setBackground(round(inputBg(), dp(8), borderColor()));
        layout.addView(descInput, margin(MATCH, WRAP, 0, 0, 0, dp(10)));

        final EditText amountInput = new EditText(this);
        amountInput.setText(String.format(Locale.US, "%.2f", expense.amount));
        amountInput.setTextColor(textMain());
        amountInput.setTextSize(14);
        amountInput.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        amountInput.setPadding(dp(12), dp(10), dp(12), dp(10));
        amountInput.setBackground(round(inputBg(), dp(8), borderColor()));
        layout.addView(amountInput, margin(MATCH, WRAP, 0, 0, 0, dp(12)));

        TextView catLabel = text("Categoria:", 12, textMuted(), true);
        layout.addView(catLabel, margin(MATCH, WRAP, 0, 0, 0, dp(6)));

        final int[] selectedCatIdx = {Math.max(0, ExpenseCategories.indexOf(expense.category))};
        CategoryStyle style = getCategoryStyle(expense.category);
        TextView categorySelector = text(style.icon + "  " + expense.category + "  ▾", 13, primaryDark(), true);
        categorySelector.setPadding(dp(14), dp(10), dp(14), dp(10));
        categorySelector.setBackground(round(accentLight(), dp(8), primaryGreen()));
        categorySelector.setOnClickListener(v -> {
            createDialogBuilder("Schimbă categoria")
                    .setSingleChoiceItems(ExpenseCategories.ALL, selectedCatIdx[0], (d, which) -> {
                        selectedCatIdx[0] = which;
                        String cat = ExpenseCategories.ALL[which];
                        categorySelector.setText(getCategoryStyle(cat).icon + "  " + cat + "  ▾");
                        d.dismiss();
                    }).show();
        });
        layout.addView(categorySelector, margin(MATCH, WRAP, 0, 0, 0, dp(4)));

        builder.setView(layout);
        builder.setPositiveButton("Salvează", (dialog, which) -> {
            String newDesc = descInput.getText().toString().trim();
            String amtStr = amountInput.getText().toString().trim().replace(',', '.');
            if (!newDesc.isEmpty() && !amtStr.isEmpty()) {
                try {
                    expense.description = newDesc;
                    expense.setAmount(Double.parseDouble(amtStr));
                    expense.category = ExpenseCategories.ALL[selectedCatIdx[0]];
                    ExpenseCategories.learn(this, newDesc, expense.category);
                    saveExpenses();
                    updateDashboard();
                    renderTransactions();
                    Toast.makeText(this, "Modificările au fost salvate.", Toast.LENGTH_SHORT).show();
                } catch (Exception e) {
                    Toast.makeText(this, "Sumă invalidă.", Toast.LENGTH_SHORT).show();
                }
            }
        });
        builder.setNeutralButton("Șterge", (dialog, which) -> {
            expenses.remove(expense);
            saveExpenses();
            updateDashboard();
            renderTransactions();
            Toast.makeText(this, "Cheltuială ștearsă.", Toast.LENGTH_SHORT).show();
        });
        builder.setNegativeButton("Anulează", null);
        builder.show();
    }

    private void showExportDialog() {
        if (expenses.isEmpty()) {
            Toast.makeText(this, "Nu există cheltuieli de exportat.", Toast.LENGTH_SHORT).show();
            return;
        }

        Locale roLocale = new Locale("ro", "RO");
        String currentMonthName = selectedMonth.format(DateTimeFormatter.ofPattern("LLLL yyyy", roLocale));
        String[] options = {
                "Doar luna selectată (" + currentMonthName + ")",
                "Tot istoricul (" + expenses.size() + " cheltuieli)"
        };

        AlertDialog.Builder builder = createDialogBuilder("Exportă cheltuieli (CSV)");
        builder.setItems(options, (dialog, which) -> {
            boolean onlyCurrentMonth = (which == 0);
            executeExportCsv(onlyCurrentMonth);
        });
        builder.setNegativeButton("Anulează", null);
        builder.show();
    }

    private void executeExportCsv(boolean onlyCurrentMonth) {
        List<Expense> toExport = onlyCurrentMonth ? monthExpenses() : expenses;
        if (toExport.isEmpty()) {
            Toast.makeText(this, "Nu există cheltuieli pentru intervalul selectat.", Toast.LENGTH_SHORT).show();
            return;
        }

        StringBuilder csv = new StringBuilder();
        csv.append("Data,Descriere,Categorie,Suma\n");
        for (Expense e : toExport) {
            String safeDesc = e.description.replace("\"", "\"\"");
            csv.append(e.date).append(",\"")
                    .append(safeDesc).append("\",")
                    .append(e.category).append(",")
                    .append(String.format(Locale.US, "%.2f", e.amount))
                    .append("\n");
        }

        try {
            android.content.ClipboardManager clipboard = (android.content.ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            android.content.ClipData clip = android.content.ClipData.newPlainText("Bani pe scurt CSV", csv.toString());
            if (clipboard != null) {
                clipboard.setPrimaryClip(clip);
            }

            Intent sendIntent = new Intent(Intent.ACTION_SEND);
            sendIntent.setType("text/plain");
            sendIntent.putExtra(Intent.EXTRA_SUBJECT, "Bani pe scurt - Export CSV");
            sendIntent.putExtra(Intent.EXTRA_TEXT, csv.toString());
            startActivity(Intent.createChooser(sendIntent, "Exportă cheltuielile (CSV copiat și în clipboard)"));
        } catch (Throwable t) {
            Toast.makeText(this, "Eroare la export: " + t.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void loadSavedExpenses() {
        SharedPreferences prefs = getSharedPreferences(PREF_NAME, MODE_PRIVATE);
        monthlyBudgetBani = prefs.getLong(PREF_KEY_BUDGET, 0);

        String saved = prefs.getString("expenses", null);
        if (saved == null) {
            saved = getPreferences(MODE_PRIVATE).getString("expenses", "[]");
        }
        try {
            expenses.clear();
            JSONArray array = new JSONArray(saved);
            for (int i = 0; i < array.length(); i++) {
                expenses.add(Expense.fromJson(array.getJSONObject(i)));
            }
        } catch (Exception ignored) {
            expenses.clear();
        }
    }

    private void saveExpenses() {
        JSONArray array = new JSONArray();
        for (Expense expense : expenses) {
            try { array.put(expense.toJson()); } catch (Exception ignored) { }
        }
        getSharedPreferences(PREF_NAME, MODE_PRIVATE).edit().putString("expenses", array.toString()).apply();
    }

    private Button button(String label, boolean primary) {
        Button button = new Button(this);
        button.setText(label);
        button.setTextSize(14);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setAllCaps(false);
        button.setTextColor(primary ? Color.WHITE : primaryDark());
        button.setBackground(round(primary ? primaryGreen() : surfaceColor(), dp(12), primary ? primaryGreen() : borderColor()));
        return button;
    }

    private Button circularNavButton(String label) {
        Button button = new Button(this);
        button.setText(label);
        button.setTextSize(22);
        button.setPadding(0, 0, 0, dp(3));
        button.setTextColor(primaryDark());
        button.setBackground(round(bgColor(), dp(18), Color.TRANSPARENT));
        return button;
    }

    private Button smallHeaderButton(String label) {
        Button button = new Button(this);
        button.setText(label);
        button.setTextSize(12);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setAllCaps(false);
        button.setPadding(dp(12), dp(4), dp(12), dp(4));
        button.setTextColor(primaryDark());
        button.setBackground(round(surfaceColor(), dp(8), borderColor()));
        return button;
    }

    private LinearLayout card() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(14), dp(16), dp(14));
        card.setBackground(round(surfaceColor(), dp(14), borderColor()));
        return card;
    }

    private TextView text(String value, float size, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setTypeface(Typeface.DEFAULT, bold ? Typeface.BOLD : Typeface.NORMAL);
        return view;
    }

    private GradientDrawable round(int color, int radius, int stroke) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(radius);
        drawable.setStroke(dp(1), stroke);
        return drawable;
    }

    private LinearLayout.LayoutParams margin(int width, int height, int left, int top, int right, int bottom) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(width, height);
        params.setMargins(left, top, right, bottom);
        return params;
    }

    private String moneyBani(long bani) {
        double lei = bani / 100.0;
        NumberFormat format = NumberFormat.getNumberInstance(new Locale("ro", "RO"));
        format.setMinimumFractionDigits(2);
        format.setMaximumFractionDigits(2);
        return format.format(lei) + " lei";
    }

    private String formatDate(String iso) {
        try {
            return LocalDate.parse(iso).format(DateTimeFormatter.ofPattern("d MMM yyyy", new Locale("ro", "RO")));
        } catch (Exception ignored) {
            return iso;
        }
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private static final int MATCH = ViewGroup.LayoutParams.MATCH_PARENT;
    private static final int WRAP = ViewGroup.LayoutParams.WRAP_CONTENT;
}
