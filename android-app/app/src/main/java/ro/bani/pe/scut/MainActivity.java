package ro.bani.pe.scut;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
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
import android.widget.FrameLayout;
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

    // Modern Fintech Palette (Matching Web App)
    private static final int BG_COLOR = Color.rgb(246, 248, 246);       // #F6F8F6 Soft paper background
    private static final int SURFACE_COLOR = Color.WHITE;               // #FFFFFF Pure white card
    private static final int PRIMARY_DARK = Color.rgb(27, 94, 32);       // #1B5E20 Deep forest green
    private static final int PRIMARY_GREEN = Color.rgb(46, 125, 50);     // #2E7D32 Emerald accent
    private static final int ACCENT_LIGHT = Color.rgb(232, 245, 233);    // #E8F5E9 Soft mint
    private static final int TEXT_MAIN = Color.rgb(26, 32, 27);          // #1A201B Crisp text
    private static final int TEXT_MUTED = Color.rgb(115, 128, 118);      // #738076 Subtitle gray
    private static final int BORDER_COLOR = Color.rgb(230, 235, 230);    // #E6EBE6 Soft divider
    private static final int DANGER_RED = Color.rgb(211, 47, 47);        // #D32F2F Accent red

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

    private static final Map<String, CategoryStyle> CATEGORY_STYLES = new LinkedHashMap<>();
    static {
        CATEGORY_STYLES.put("Mâncare", new CategoryStyle("Mâncare", "🛒", Color.rgb(232, 245, 233), Color.rgb(46, 125, 50), Color.rgb(67, 160, 71)));
        CATEGORY_STYLES.put("Transport", new CategoryStyle("Transport", "🚗", Color.rgb(255, 243, 224), Color.rgb(230, 81, 0), Color.rgb(251, 140, 0)));
        CATEGORY_STYLES.put("Locuință", new CategoryStyle("Locuință", "🏠", Color.rgb(227, 242, 253), Color.rgb(21, 101, 192), Color.rgb(30, 136, 229)));
        CATEGORY_STYLES.put("Cumpărături", new CategoryStyle("Cumpărături", "🛍️", Color.rgb(243, 229, 245), Color.rgb(123, 31, 162), Color.rgb(142, 36, 170)));
        CATEGORY_STYLES.put("Sănătate", new CategoryStyle("Sănătate", "💊", Color.rgb(255, 235, 238), Color.rgb(198, 40, 40), Color.rgb(229, 57, 53)));
        CATEGORY_STYLES.put("Timp liber", new CategoryStyle("Timp liber", "🍿", Color.rgb(224, 242, 241), Color.rgb(0, 105, 92), Color.rgb(0, 137, 123)));
        CATEGORY_STYLES.put("Altele", new CategoryStyle("Altele", "📦", Color.rgb(236, 239, 241), Color.rgb(69, 90, 100), Color.rgb(96, 125, 139)));
    }

    private static CategoryStyle getCategoryStyle(String name) {
        CategoryStyle style = CATEGORY_STYLES.get(name);
        return style != null ? style : CATEGORY_STYLES.get("Altele");
    }

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final List<Expense> expenses = new ArrayList<>();
    private final List<Expense> drafts = new ArrayList<>();

    private YearMonth selectedMonth = YearMonth.now();
    private String currentSearchQuery = "";
    private String selectedCategoryFilter = "Toate";
    private String importedFileName = "";

    // UI View References
    private LinearLayout root;
    private LinearLayout draftSection;
    private LinearLayout draftRows;
    private LinearLayout transactionRows;
    private LinearLayout categoryBreakdownContainer;
    private LinearLayout chipsContainer;
    private LinearLayout syncCard;
    private TextView totalText;
    private TextView countText;
    private TextView avgDailyText;
    private TextView monthText;
    private TextView statusText;
    private TextView transactionListTitle;
    private EditText searchInput;
    private ProgressBar progressBar;

    // Real-time notification broadcast listener
    private final BroadcastReceiver expenseReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            loadSavedExpenses();
            updateDashboard();
            renderTransactions();
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
            // 1. Build and attach UI first so Window DecorView is initialized
            initUi();

            // 2. Safely configure system bars
            setupWindowBars();

            // 3. Load saved expenses
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

    private void setupWindowBars() {
        try {
            getWindow().setStatusBarColor(BG_COLOR);
            getWindow().setNavigationBarColor(Color.WHITE);
            View decorView = getWindow().getDecorView();
            if (decorView != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    android.view.WindowInsetsController controller = decorView.getWindowInsetsController();
                    if (controller == null) {
                        controller = getWindow().getInsetsController();
                    }
                    if (controller != null) {
                        controller.setSystemBarsAppearance(
                                android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS |
                                        android.view.WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS,
                                android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS |
                                        android.view.WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
                        );
                    } else {
                        decorView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
                    }
                } else {
                    decorView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
                }
            }
        } catch (Throwable ignored) {
            // Defensive: protect against custom ROM styling quirks
        }
    }

    private void showCrashFallbackUi(Throwable t) {
        try {
            ScrollView sv = new ScrollView(this);
            sv.setBackgroundColor(BG_COLOR);
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
            subtitle.setTextColor(TEXT_MAIN);
            layout.addView(subtitle, margin(MATCH, WRAP, 0, dp(8), 0, dp(12)));

            TextView details = new TextView(this);
            details.setText(android.util.Log.getStackTraceString(t));
            details.setTextSize(11);
            details.setTypeface(Typeface.MONOSPACE);
            details.setTextColor(Color.rgb(80, 80, 80));
            details.setPadding(dp(12), dp(12), dp(12), dp(12));
            details.setBackground(round(Color.WHITE, dp(8), BORDER_COLOR));
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
            IntentFilter filter = new IntentFilter(BankNotificationListener.ACTION_EXPENSE_ADDED);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(expenseReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
            } else {
                registerReceiver(expenseReceiver, filter);
            }
        } catch (Throwable ignored) {}

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
        scroll.setBackgroundColor(BG_COLOR);

        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(16), dp(18), dp(32));
        scroll.addView(root);

        // 1. Header / Brand Bar
        LinearLayout headerRow = new LinearLayout(this);
        headerRow.setOrientation(LinearLayout.HORIZONTAL);
        headerRow.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout brandBlock = new LinearLayout(this);
        brandBlock.setOrientation(LinearLayout.VERTICAL);

        TextView brand = text("Bani pe scurt", 19, PRIMARY_DARK, true);
        brandBlock.addView(brand);

        TextView tagline = text("Controlul cheltuielilor tale", 12, TEXT_MUTED, false);
        brandBlock.addView(tagline);
        headerRow.addView(brandBlock, new LinearLayout.LayoutParams(0, WRAP, 1));

        Button exportBtn = smallHeaderButton("Export CSV ↗");
        exportBtn.setOnClickListener(v -> showExportDialog());
        headerRow.addView(exportBtn, new LinearLayout.LayoutParams(WRAP, dp(34)));

        root.addView(headerRow, margin(MATCH, WRAP, 0, 0, 0, dp(14)));

        // 2. Month Selector Bar
        LinearLayout monthBar = new LinearLayout(this);
        monthBar.setGravity(Gravity.CENTER_VERTICAL);
        monthBar.setOrientation(LinearLayout.HORIZONTAL);
        monthBar.setPadding(dp(12), dp(8), dp(12), dp(8));
        monthBar.setBackground(round(SURFACE_COLOR, dp(14), BORDER_COLOR));

        Button prevBtn = circularNavButton("‹");
        prevBtn.setOnClickListener(v -> {
            selectedMonth = selectedMonth.minusMonths(1);
            updateDashboard();
            renderTransactions();
        });

        monthText = text("", 15, TEXT_MAIN, true);
        monthText.setGravity(Gravity.CENTER);

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
        root.addView(monthBar, margin(MATCH, WRAP, 0, 0, 0, dp(12)));

        // 3. Hero Card: Total Monthly Spending
        LinearLayout totalCard = card();
        totalCard.setBackground(round(SURFACE_COLOR, dp(16), BORDER_COLOR));
        totalCard.setPadding(dp(18), dp(16), dp(18), dp(16));

        TextView totalLabel = text("TOTAL LUNA ACEASTA", 11, TEXT_MUTED, true);
        totalLabel.setLetterSpacing(0.06f);
        totalCard.addView(totalLabel);

        totalText = text("0,00 lei", 32, PRIMARY_DARK, true);
        totalCard.addView(totalText, margin(WRAP, WRAP, 0, dp(4), 0, dp(8)));

        LinearLayout statsRow = new LinearLayout(this);
        statsRow.setOrientation(LinearLayout.HORIZONTAL);
        statsRow.setGravity(Gravity.CENTER_VERTICAL);

        countText = text("0 cheltuieli", 13, TEXT_MUTED, false);
        statsRow.addView(countText, new LinearLayout.LayoutParams(0, WRAP, 1));

        avgDailyText = text("", 11, PRIMARY_GREEN, true);
        avgDailyText.setPadding(dp(8), dp(3), dp(8), dp(3));
        avgDailyText.setBackground(round(ACCENT_LIGHT, dp(10), Color.TRANSPARENT));
        statsRow.addView(avgDailyText);

        totalCard.addView(statsRow);
        root.addView(totalCard, margin(MATCH, WRAP, 0, 0, 0, dp(12)));

        // 4. Quick Action Buttons
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

        // 5. Real-Time Sync Status Card
        syncCard = card();
        updateSyncCard();
        root.addView(syncCard, margin(MATCH, WRAP, 0, 0, 0, dp(12)));

        // 6. Category Breakdown Card (Visual Graphical Progress Bars)
        LinearLayout categoryCard = card();
        categoryCard.setBackground(round(SURFACE_COLOR, dp(16), BORDER_COLOR));
        categoryCard.setPadding(dp(18), dp(16), dp(18), dp(16));

        TextView catTitle = text("REPARTIZARE PE CATEGORII", 11, TEXT_MUTED, true);
        catTitle.setLetterSpacing(0.06f);
        categoryCard.addView(catTitle, margin(MATCH, WRAP, 0, 0, 0, dp(12)));

        categoryBreakdownContainer = new LinearLayout(this);
        categoryBreakdownContainer.setOrientation(LinearLayout.VERTICAL);
        categoryCard.addView(categoryBreakdownContainer);

        root.addView(categoryCard, margin(MATCH, WRAP, 0, 0, 0, dp(14)));

        // 7. Draft Section for Reviewing Imported Transactions
        draftSection = new LinearLayout(this);
        draftSection.setOrientation(LinearLayout.VERTICAL);
        draftSection.setVisibility(View.GONE);

        LinearLayout draftCard = card();
        draftCard.addView(text("VERIFICĂ TRANZACȚIILE IMPORTATE", 11, PRIMARY_GREEN, true));
        statusText = text("", 12, TEXT_MAIN, false);
        statusText.setLineSpacing(dp(2), 1f);
        draftCard.addView(statusText, margin(MATCH, WRAP, 0, dp(6), 0, dp(6)));

        progressBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progressBar.setIndeterminate(true);
        progressBar.setVisibility(View.GONE);
        draftCard.addView(progressBar, margin(MATCH, dp(4), 0, 0, 0, dp(8)));

        draftRows = new LinearLayout(this);
        draftRows.setOrientation(LinearLayout.VERTICAL);
        draftCard.addView(draftRows);

        Button saveDraftsButton = button("Salvează toate cheltuielile", true);
        saveDraftsButton.setOnClickListener(v -> saveDrafts());
        draftCard.addView(saveDraftsButton, margin(MATCH, dp(46), 0, dp(14), 0, dp(8)));

        Button cancelDraftsButton = button("Renunță la import", false);
        cancelDraftsButton.setOnClickListener(v -> {
            drafts.clear();
            draftSection.setVisibility(View.GONE);
        });
        draftCard.addView(cancelDraftsButton, margin(MATCH, dp(40), 0, 0, 0, 0));

        draftSection.addView(draftCard);
        root.addView(draftSection, margin(MATCH, WRAP, 0, 0, 0, dp(14)));

        // 8. Transactions Section Title
        LinearLayout transHeader = new LinearLayout(this);
        transHeader.setOrientation(LinearLayout.HORIZONTAL);
        transHeader.setGravity(Gravity.CENTER_VERTICAL);

        transactionListTitle = text("ISTORIC CHELTUIELI", 11, TEXT_MUTED, true);
        transactionListTitle.setLetterSpacing(0.06f);
        transHeader.addView(transactionListTitle, new LinearLayout.LayoutParams(0, WRAP, 1));
        root.addView(transHeader, margin(MATCH, WRAP, 0, dp(4), 0, dp(8)));

        // 9. Modern Search Bar
        searchInput = new EditText(this);
        searchInput.setHint("🔍 Caută comerciant, magazin sau categorie…");
        searchInput.setTextSize(13);
        searchInput.setPadding(dp(14), dp(11), dp(14), dp(11));
        searchInput.setBackground(round(SURFACE_COLOR, dp(12), BORDER_COLOR));
        searchInput.setTextColor(TEXT_MAIN);
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

        // 10. Horizontal Category Filter Chips
        HorizontalScrollView chipScroll = new HorizontalScrollView(this);
        chipScroll.setHorizontalScrollBarEnabled(false);
        chipsContainer = new LinearLayout(this);
        chipsContainer.setOrientation(LinearLayout.HORIZONTAL);
        chipsContainer.setPadding(0, dp(2), 0, dp(4));
        chipScroll.addView(chipsContainer);
        renderCategoryChips();
        root.addView(chipScroll, margin(MATCH, WRAP, 0, 0, 0, dp(10)));

        // 11. Transaction List Rows Container
        transactionRows = new LinearLayout(this);
        transactionRows.setOrientation(LinearLayout.VERTICAL);
        root.addView(transactionRows, margin(MATCH, WRAP, 0, 0, 0, dp(20)));

        // 12. Footer
        TextView footer = text("Bani pe scurt · Confidențialitate 100% offline pe dispozitiv", 11, TEXT_MUTED, false);
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
                chip.setBackground(round(PRIMARY_GREEN, dp(16), PRIMARY_GREEN));
            } else {
                chip.setTextColor(TEXT_MUTED);
                chip.setBackground(round(SURFACE_COLOR, dp(16), BORDER_COLOR));
            }

            chip.setOnClickListener(v -> {
                selectedCategoryFilter = cat;
                renderCategoryChips();
                renderTransactions();
            });

            chipsContainer.addView(chip, margin(WRAP, dp(34), 0, 0, dp(6), 0));
        }
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

        if (isEnabled) {
            syncCard.setBackground(round(ACCENT_LIGHT, dp(14), PRIMARY_GREEN));
            syncCard.setPadding(dp(16), dp(12), dp(16), dp(12));

            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);

            LinearLayout textCol = new LinearLayout(this);
            textCol.setOrientation(LinearLayout.VERTICAL);

            TextView header = text("⚡ Sincronizare plăți activă", 12, PRIMARY_DARK, true);
            textCol.addView(header);

            TextView desc = text("Detectare automată la POS (Google Wallet & bănci)", 11, TEXT_MUTED, false);
            textCol.addView(desc);
            row.addView(textCol, new LinearLayout.LayoutParams(0, WRAP, 1));

            Button testBtn = smallHeaderButton("Test live");
            testBtn.setOnClickListener(v -> triggerTestPayment());
            row.addView(testBtn, new LinearLayout.LayoutParams(WRAP, dp(32)));

            syncCard.addView(row);
        } else {
            syncCard.setBackground(round(SURFACE_COLOR, dp(14), BORDER_COLOR));
            syncCard.setPadding(dp(16), dp(14), dp(16), dp(14));

            TextView header = text("⚡ Sincronizare în timp real", 12, PRIMARY_DARK, true);
            syncCard.addView(header);

            TextView desc = text("Vrei ca plățile să se adauge automat la atingerea POS-ului? Activează captarea notificărilor din Google Wallet, Revolut sau YOU BRD.", 12, TEXT_MUTED, false);
            desc.setLineSpacing(dp(3), 1f);
            syncCard.addView(desc, margin(MATCH, WRAP, 0, dp(4), 0, dp(10)));

            Button enableBtn = button("Activează captarea automată →", true);
            enableBtn.setOnClickListener(v -> {
                try {
                    startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS));
                    Toast.makeText(this, "Activează „Bani pe scurt” în lista de notificări.", Toast.LENGTH_LONG).show();
                } catch (Exception e) {
                    Toast.makeText(this, "Deschide Setări > Aplicații > Acces special.", Toast.LENGTH_SHORT).show();
                }
            });
            syncCard.addView(enableBtn, margin(MATCH, dp(42), 0, 0, 0, 0));
        }
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
                            (duplicates > 0 ? " · " + duplicates + " duplicate omise" : "") +
                            "\nVerifică categoriile de mai jos înainte de salvare.");
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
        if (drafts.isEmpty()) {
            TextView empty = text("Nu au rămas tranzacții noi de adăugat.", 12, TEXT_MUTED, false);
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
            row.setBackground(round(SURFACE_COLOR, dp(12), BORDER_COLOR));

            TextView iconView = new TextView(this);
            iconView.setText(style.icon);
            iconView.setTextSize(18);
            iconView.setGravity(Gravity.CENTER);
            iconView.setBackground(round(style.bgColor, dp(20), style.bgColor));
            row.addView(iconView, new LinearLayout.LayoutParams(dp(40), dp(40)));

            LinearLayout middle = new LinearLayout(this);
            middle.setOrientation(LinearLayout.VERTICAL);
            middle.setPadding(dp(10), 0, dp(8), 0);

            TextView top = text(expense.description, 13, TEXT_MAIN, true);
            top.setMaxLines(1);
            top.setEllipsize(TextUtils.TruncateAt.END);
            middle.addView(top);

            LinearLayout subRow = new LinearLayout(this);
            subRow.setOrientation(LinearLayout.HORIZONTAL);
            subRow.setGravity(Gravity.CENTER_VERTICAL);

            TextView details = text(formatDate(expense.date), 11, TEXT_MUTED, false);
            subRow.addView(details);

            TextView dot = text("  •  ", 10, BORDER_COLOR, false);
            subRow.addView(dot);

            TextView catPill = text(expense.category + " ▾", 11, style.textColor, true);
            catPill.setPadding(dp(6), dp(1), dp(6), dp(1));
            catPill.setBackground(round(style.bgColor, dp(6), Color.TRANSPARENT));
            catPill.setOnClickListener(v -> showCategoryPickerDialog(expense, true));
            subRow.addView(catPill);

            middle.addView(subRow, margin(WRAP, WRAP, 0, dp(2), 0, 0));
            row.addView(middle, new LinearLayout.LayoutParams(0, WRAP, 1));

            TextView amountView = text(moneyBani(expense.amountBani), 13, TEXT_MAIN, true);
            row.addView(amountView);

            draftRows.addView(row, margin(MATCH, WRAP, 0, dp(4), 0, dp(4)));
        }
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

        if (month.isEmpty()) {
            LinearLayout emptyCard = card();
            emptyCard.setGravity(Gravity.CENTER);
            emptyCard.setPadding(dp(20), dp(32), dp(20), dp(32));

            TextView emoji = text("🍃", 32, PRIMARY_GREEN, false);
            emoji.setGravity(Gravity.CENTER);
            emptyCard.addView(emoji);

            String msg = currentSearchQuery.isEmpty()
                    ? ("Toate".equals(selectedCategoryFilter)
                    ? "Nicio cheltuială în această lună.\nAtinge „＋ Adaugă” sau importă un extras."
                    : "Nicio cheltuială la categoria „" + selectedCategoryFilter + "”.")
                    : "Niciun rezultat pentru căutarea curentă.";

            TextView empty = text(msg, 13, TEXT_MUTED, false);
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
            TextView more = text("Și încă " + (month.size() - limit) + " cheltuieli în luna aceasta.", 12, TEXT_MUTED, false);
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
        card.setBackground(round(SURFACE_COLOR, dp(14), BORDER_COLOR));

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
        title.setTextColor(TEXT_MAIN);
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
        dateView.setTextColor(TEXT_MUTED);
        subRow.addView(dateView);

        TextView dot = new TextView(this);
        dot.setText("  •  ");
        dot.setTextSize(10);
        dot.setTextColor(BORDER_COLOR);
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
        amountView.setText(moneyBani(expense.amountBani));
        amountView.setTextSize(14);
        amountView.setTextColor(TEXT_MAIN);
        amountView.setTypeface(Typeface.DEFAULT_BOLD);
        right.addView(amountView);

        card.addView(right);

        // Tap on transaction opens edit / delete dialog
        card.setOnClickListener(v -> showEditExpenseDialog(expense));

        return card;
    }

    private void showCategoryPickerDialog(Expense expense, boolean isDraft) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Schimbă categoria");

        int currentIdx = ExpenseCategories.indexOf(expense.category);
        builder.setSingleChoiceItems(ExpenseCategories.ALL, currentIdx, (dialog, which) -> {
            String newCat = ExpenseCategories.ALL[which];
            expense.category = newCat;
            ExpenseCategories.learn(this, expense.description, newCat);
            if (!isDraft) {
                saveExpenses();
                updateDashboard();
                renderTransactions();
            } else {
                renderDrafts();
            }
            dialog.dismiss();
            Toast.makeText(this, "Categorie schimbată: " + newCat, Toast.LENGTH_SHORT).show();
        });
        builder.setNegativeButton("Anulează", null);
        builder.show();
    }

    private void updateDashboard() {
        if (monthText == null) return;
        Locale locale = new Locale("ro", "RO");
        String monthName = selectedMonth.format(DateTimeFormatter.ofPattern("LLLL yyyy", locale));
        monthText.setText(monthName.substring(0, 1).toUpperCase(locale) + monthName.substring(1));

        List<Expense> month = monthExpenses();
        long totalBani = 0;
        Map<String, Long> byCategory = new LinkedHashMap<>();
        for (Expense expense : month) {
            totalBani += expense.amountBani;
            byCategory.put(expense.category, byCategory.getOrDefault(expense.category, 0L) + expense.amountBani);
        }

        totalText.setText(moneyBani(totalBani));
        countText.setText(month.size() + (month.size() == 1 ? " cheltuială înregistrată" : " cheltuieli înregistrate"));

        // Daily average calculation
        if (totalBani > 0) {
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

        // Render graphical category progress bars
        renderCategoryBreakdown(byCategory, totalBani);
    }

    private void renderCategoryBreakdown(Map<String, Long> byCategory, long totalBani) {
        if (categoryBreakdownContainer == null) return;
        categoryBreakdownContainer.removeAllViews();

        if (byCategory.isEmpty() || totalBani == 0) {
            TextView empty = text("Nicio cheltuială înregistrată pentru această lună.", 12, TEXT_MUTED, false);
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

            TextView nameView = text(style.icon + "  " + category, 13, TEXT_MAIN, true);
            labels.addView(nameView, new LinearLayout.LayoutParams(0, WRAP, 1));

            TextView valView = text(moneyBani(bani) + "  ·  " + String.format(Locale.US, "%.0f%%", percent), 12, TEXT_MAIN, true);
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
        track.setBackground(round(Color.rgb(238, 241, 238), dp(4), Color.TRANSPARENT));
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
        for (Expense expense : expenses) {
            if (expense.date != null && expense.date.startsWith(prefix)) {
                month.add(expense);
            }
        }
        return month;
    }

    private void openManualAddDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Adaugă cheltuială");

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(22), dp(14), dp(22), dp(10));

        final EditText descInput = new EditText(this);
        descInput.setHint("Comerciant (ex: Kaufland, Bolt, Farmacie)");
        descInput.setTextSize(14);
        descInput.setPadding(dp(12), dp(10), dp(12), dp(10));
        descInput.setBackground(round(Color.WHITE, dp(8), BORDER_COLOR));
        layout.addView(descInput, margin(MATCH, WRAP, 0, 0, 0, dp(10)));

        final EditText amountInput = new EditText(this);
        amountInput.setHint("Sumă în lei (ex: 35.50)");
        amountInput.setTextSize(14);
        amountInput.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        amountInput.setPadding(dp(12), dp(10), dp(12), dp(10));
        amountInput.setBackground(round(Color.WHITE, dp(8), BORDER_COLOR));
        layout.addView(amountInput, margin(MATCH, WRAP, 0, 0, 0, dp(12)));

        TextView catLabel = text("Alege categoria:", 12, TEXT_MUTED, true);
        layout.addView(catLabel, margin(MATCH, WRAP, 0, 0, 0, dp(6)));

        final int[] selectedCatIdx = {0};
        TextView categorySelector = text("🛒  Mâncare  ▾", 13, PRIMARY_DARK, true);
        categorySelector.setPadding(dp(14), dp(10), dp(14), dp(10));
        categorySelector.setBackground(round(ACCENT_LIGHT, dp(8), PRIMARY_GREEN));
        categorySelector.setOnClickListener(v -> {
            new AlertDialog.Builder(this)
                    .setTitle("Selectează categoria")
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
                expense.setAmount(amt);
                expense.income = false;

                expenses.add(expense);
                saveExpenses();
                updateDashboard();
                renderTransactions();
                Toast.makeText(this, "Cheltuială adăugată!", Toast.LENGTH_SHORT).show();
            } catch (Exception e) {
                Toast.makeText(this, "Sumă invalidă.", Toast.LENGTH_SHORT).show();
            }
        });
        builder.setNegativeButton("Anulează", null);
        builder.show();
    }

    private void showEditExpenseDialog(Expense expense) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Editează cheltuiala");

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(22), dp(14), dp(22), dp(10));

        final EditText descInput = new EditText(this);
        descInput.setText(expense.description);
        descInput.setTextSize(14);
        descInput.setPadding(dp(12), dp(10), dp(12), dp(10));
        descInput.setBackground(round(Color.WHITE, dp(8), BORDER_COLOR));
        layout.addView(descInput, margin(MATCH, WRAP, 0, 0, 0, dp(10)));

        final EditText amountInput = new EditText(this);
        amountInput.setText(String.format(Locale.US, "%.2f", expense.amount));
        amountInput.setTextSize(14);
        amountInput.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        amountInput.setPadding(dp(12), dp(10), dp(12), dp(10));
        amountInput.setBackground(round(Color.WHITE, dp(8), BORDER_COLOR));
        layout.addView(amountInput, margin(MATCH, WRAP, 0, 0, 0, dp(12)));

        TextView catLabel = text("Categoria:", 12, TEXT_MUTED, true);
        layout.addView(catLabel, margin(MATCH, WRAP, 0, 0, 0, dp(6)));

        final int[] selectedCatIdx = {Math.max(0, ExpenseCategories.indexOf(expense.category))};
        CategoryStyle style = getCategoryStyle(expense.category);
        TextView categorySelector = text(style.icon + "  " + expense.category + "  ▾", 13, PRIMARY_DARK, true);
        categorySelector.setPadding(dp(14), dp(10), dp(14), dp(10));
        categorySelector.setBackground(round(ACCENT_LIGHT, dp(8), PRIMARY_GREEN));
        categorySelector.setOnClickListener(v -> {
            new AlertDialog.Builder(this)
                    .setTitle("Schimbă categoria")
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

        StringBuilder csv = new StringBuilder();
        csv.append("Data,Descriere,Categorie,Suma\n");
        for (Expense e : expenses) {
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
        button.setTextColor(primary ? Color.WHITE : PRIMARY_DARK);
        button.setBackground(round(primary ? PRIMARY_GREEN : SURFACE_COLOR, dp(12), primary ? PRIMARY_GREEN : BORDER_COLOR));
        return button;
    }

    private Button circularNavButton(String label) {
        Button button = new Button(this);
        button.setText(label);
        button.setTextSize(22);
        button.setPadding(0, 0, 0, dp(3));
        button.setTextColor(PRIMARY_DARK);
        button.setBackground(round(BG_COLOR, dp(18), Color.TRANSPARENT));
        return button;
    }

    private Button smallHeaderButton(String label) {
        Button button = new Button(this);
        button.setText(label);
        button.setTextSize(12);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setAllCaps(false);
        button.setPadding(dp(12), dp(4), dp(12), dp(4));
        button.setTextColor(PRIMARY_DARK);
        button.setBackground(round(SURFACE_COLOR, dp(8), BORDER_COLOR));
        return button;
    }

    private LinearLayout card() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(14), dp(16), dp(14));
        card.setBackground(round(SURFACE_COLOR, dp(14), BORDER_COLOR));
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
