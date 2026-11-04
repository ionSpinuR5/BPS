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
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.Spinner;
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

    private static final int INK = Color.rgb(36, 41, 35);
    private static final int MUTED = Color.rgb(130, 138, 128);
    private static final int GREEN = Color.rgb(60, 112, 78);
    private static final int GREEN_DARK = Color.rgb(46, 92, 64);
    private static final int PAPER = Color.rgb(246, 245, 241);
    private static final int LINE = Color.rgb(235, 237, 231);
    private static final int PEACH_BG = Color.rgb(251, 239, 228);
    private static final int PEACH_TEXT = Color.rgb(194, 127, 75);
    private static final int MINT_BG = Color.rgb(230, 242, 232);

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final List<Expense> expenses = new ArrayList<>();
    private final List<Expense> drafts = new ArrayList<>();

    private YearMonth selectedMonth = YearMonth.now();
    private String currentSearchQuery = "";
    private String importedFileName = "";

    // UI View References
    private LinearLayout root;
    private LinearLayout draftSection;
    private LinearLayout draftRows;
    private LinearLayout transactionRows;
    private LinearLayout syncCard;
    private TextView totalText;
    private TextView countText;
    private TextView monthText;
    private TextView categorySummary;
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
        getWindow().setStatusBarColor(PAPER);
        getWindow().setNavigationBarColor(Color.WHITE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            getWindow().setDecorFitsSystemWindows(true);
            getWindow().getInsetsController().setSystemBarsAppearance(
                    android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS |
                            android.view.WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS,
                    android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS |
                            android.view.WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
            );
        } else {
            getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        }

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

        initUi();
        updateDashboard();
        renderTransactions();
        if (!drafts.isEmpty()) {
            draftSection.setVisibility(View.VISIBLE);
            renderDrafts();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        IntentFilter filter = new IntentFilter(BankNotificationListener.ACTION_EXPENSE_ADDED);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(expenseReceiver, filter, RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(expenseReceiver, filter);
        }

        loadSavedExpenses();
        updateDashboard();
        renderTransactions();
        updateSyncCard();
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
        scroll.setBackgroundColor(PAPER);

        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(18), dp(20), dp(28));
        scroll.addView(root);

        // Header / Brand
        LinearLayout headerRow = new LinearLayout(this);
        headerRow.setOrientation(LinearLayout.HORIZONTAL);
        headerRow.setGravity(Gravity.CENTER_VERTICAL);

        TextView brand = text("bani pe scurt", 14, GREEN, true);
        brand.setLetterSpacing(.04f);
        headerRow.addView(brand, new LinearLayout.LayoutParams(0, WRAP, 1));

        Button exportBtn = smallHeaderButton("Export CSV ↗");
        exportBtn.setOnClickListener(v -> showExportDialog());
        headerRow.addView(exportBtn, new LinearLayout.LayoutParams(WRAP, dp(34)));

        root.addView(headerRow, margin(MATCH, WRAP, 0, 0, 0, dp(18)));
        root.addView(text("PANOU PERSONAL", 10, MUTED, true), margin(WRAP, WRAP, 0, 0, 0, dp(4)));

        TextView title = text("Cheltuieli,\nmai ușor de înțeles.", 26, INK, true);
        title.setLineSpacing(dp(1), 1f);
        root.addView(title, margin(WRAP, WRAP, 0, 0, 0, dp(5)));
        root.addView(text("Plățile se sincronizează automat din notificări sau prin import extras.", 13, MUTED, false), margin(WRAP, WRAP, 0, 0, 0, dp(16)));

        // Real-Time Sync Status Card
        syncCard = card();
        updateSyncCard();
        root.addView(syncCard, margin(MATCH, WRAP, 0, 0, 0, dp(14)));

        // Action Buttons
        LinearLayout actionButtons = new LinearLayout(this);
        actionButtons.setOrientation(LinearLayout.HORIZONTAL);

        Button importPdfButton = button("Importă extras (PDF/CSV) ＋", true);
        importPdfButton.setOnClickListener(v -> openFilePicker());
        actionButtons.addView(importPdfButton, new LinearLayout.LayoutParams(0, dp(50), 1));

        Button addManualBtn = button("Adaugă manual", false);
        addManualBtn.setOnClickListener(v -> openManualAddDialog());
        LinearLayout.LayoutParams manualParams = new LinearLayout.LayoutParams(WRAP, dp(50));
        manualParams.leftMargin = dp(8);
        actionButtons.addView(addManualBtn, manualParams);

        root.addView(actionButtons, margin(MATCH, WRAP, 0, 0, 0, dp(8)));

        TextView localNote = text("Datele sunt procesate și păstrate exclusiv pe telefonul tău.", 11, MUTED, false);
        localNote.setGravity(Gravity.CENTER);
        root.addView(localNote, margin(MATCH, WRAP, 0, 0, 0, dp(18)));

        // Month Selector Bar
        LinearLayout monthBar = new LinearLayout(this);
        monthBar.setGravity(Gravity.CENTER_VERTICAL);
        monthBar.setOrientation(LinearLayout.HORIZONTAL);
        monthBar.setPadding(dp(12), dp(10), dp(12), dp(10));
        monthBar.setBackground(round(Color.WHITE, dp(12), LINE));

        Button previous = smallMonthButton("‹");
        previous.setOnClickListener(v -> {
            selectedMonth = selectedMonth.minusMonths(1);
            updateDashboard();
            renderTransactions();
        });

        Button next = smallMonthButton("›");
        next.setOnClickListener(v -> {
            selectedMonth = selectedMonth.plusMonths(1);
            updateDashboard();
            renderTransactions();
        });

        monthText = text("", 14, INK, true);
        monthText.setGravity(Gravity.CENTER);

        Button currentMonthBtn = smallHeaderButton("Azi");
        currentMonthBtn.setOnClickListener(v -> {
            selectedMonth = YearMonth.now();
            updateDashboard();
            renderTransactions();
        });

        monthBar.addView(previous, new LinearLayout.LayoutParams(dp(40), dp(38)));
        monthBar.addView(monthText, new LinearLayout.LayoutParams(0, WRAP, 1));
        monthBar.addView(next, new LinearLayout.LayoutParams(dp(40), dp(38)));
        monthBar.addView(currentMonthBtn, new LinearLayout.LayoutParams(WRAP, dp(34)));
        root.addView(monthBar, margin(MATCH, WRAP, 0, 0, 0, dp(10)));

        // Total Spent Card
        LinearLayout totalCard = card();
        totalCard.addView(text("TOTAL CHELTUIT ÎN LUNA ALEASĂ", 10, MUTED, true));
        totalText = text("0,00 lei", 27, INK, true);
        totalCard.addView(totalText, margin(WRAP, WRAP, 0, dp(4), 0, dp(2)));
        countText = text("0 cheltuieli", 11, MUTED, false);
        totalCard.addView(countText);
        root.addView(totalCard, margin(MATCH, WRAP, 0, 0, 0, dp(10)));

        // Breakdown Card
        LinearLayout categoryCard = card();
        categoryCard.addView(text("REPARTIZARE PE CATEGORII", 10, MUTED, true));
        categorySummary = text("Importă extrasul ca să vezi repartizarea cheltuielilor.", 12, MUTED, false);
        categorySummary.setLineSpacing(dp(5), 1f);
        categoryCard.addView(categorySummary, margin(MATCH, WRAP, 0, dp(10), 0, 0));
        root.addView(categoryCard, margin(MATCH, WRAP, 0, 0, 0, dp(12)));

        // Draft Section for Reviewing Imported Transactions
        draftSection = new LinearLayout(this);
        draftSection.setOrientation(LinearLayout.VERTICAL);
        draftSection.setVisibility(View.GONE);

        LinearLayout draftCard = card();
        draftCard.addView(text("VERIFICĂ IMPORTUL", 10, GREEN, true));
        statusText = text("", 12, INK, true);
        draftCard.addView(statusText, margin(MATCH, WRAP, 0, dp(6), 0, dp(6)));

        progressBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progressBar.setIndeterminate(true);
        progressBar.setVisibility(View.GONE);
        draftCard.addView(progressBar, margin(MATCH, dp(3), 0, 0, 0, dp(8)));

        draftRows = new LinearLayout(this);
        draftRows.setOrientation(LinearLayout.VERTICAL);
        draftCard.addView(draftRows);

        Button saveDraftsButton = button("Salvează toate cheltuielile", true);
        saveDraftsButton.setOnClickListener(v -> saveDrafts());
        draftCard.addView(saveDraftsButton, margin(MATCH, dp(46), 0, dp(14), 0, dp(6)));

        Button cancelDraftsButton = button("Renunță la import", false);
        cancelDraftsButton.setOnClickListener(v -> {
            drafts.clear();
            draftSection.setVisibility(View.GONE);
        });
        draftCard.addView(cancelDraftsButton, margin(MATCH, dp(40), 0, 0, 0, 0));

        draftSection.addView(draftCard);
        root.addView(draftSection, margin(MATCH, WRAP, 0, 0, 0, dp(12)));

        // Transactions Header with Search Filter
        LinearLayout transHeader = new LinearLayout(this);
        transHeader.setOrientation(LinearLayout.HORIZONTAL);
        transHeader.setGravity(Gravity.CENTER_VERTICAL);
        transactionListTitle = text("CHELTUIELI ÎNREGISTRATE", 10, MUTED, true);
        transHeader.addView(transactionListTitle, new LinearLayout.LayoutParams(0, WRAP, 1));
        root.addView(transHeader, margin(MATCH, WRAP, 0, dp(6), 0, dp(6)));

        // Search Input
        searchInput = new EditText(this);
        searchInput.setHint("Caută comerciant sau categorie…");
        searchInput.setTextSize(13);
        searchInput.setPadding(dp(12), dp(10), dp(12), dp(10));
        searchInput.setBackground(round(Color.WHITE, dp(10), LINE));
        searchInput.setTextColor(INK);
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

        // Transactions Container Card
        LinearLayout transactionsCard = card();
        transactionRows = new LinearLayout(this);
        transactionRows.setOrientation(LinearLayout.VERTICAL);
        transactionsCard.addView(transactionRows);
        root.addView(transactionsCard, margin(MATCH, WRAP, 0, 0, 0, dp(16)));

        // Footer
        TextView footer = text("Bani pe scurt · Confidențialitate 100% offline", 11, MUTED, false);
        footer.setGravity(Gravity.CENTER);
        root.addView(footer, margin(MATCH, WRAP, 0, dp(4), 0, 0));

        setContentView(scroll);
    }

    private boolean isNotificationListenerEnabled() {
        String pkgName = getPackageName();
        String flat = Settings.Secure.getString(getContentResolver(), "enabled_notification_listeners");
        return flat != null && flat.contains(pkgName);
    }

    private void updateSyncCard() {
        if (syncCard == null) return;
        syncCard.removeAllViews();

        boolean isEnabled = isNotificationListenerEnabled();

        if (isEnabled) {
            syncCard.setBackground(round(MINT_BG, dp(12), GREEN));
            TextView header = text("SINCRONIZARE ÎN TIMP REAL · ACTIVĂ ⚡", 10, GREEN, true);
            syncCard.addView(header);

            TextView desc = text("Plățile din Google Wallet, YOU BRD, Revolut sau BT Pay sunt înregistrate automat la atingerea POS-ului.", 12, INK, false);
            desc.setLineSpacing(dp(3), 1f);
            syncCard.addView(desc, margin(MATCH, WRAP, 0, dp(4), 0, dp(8)));

            Button testBtn = smallHeaderButton("Trimite plată de test (Demo live) ＋");
            testBtn.setOnClickListener(v -> triggerTestPayment());
            syncCard.addView(testBtn, new LinearLayout.LayoutParams(WRAP, dp(34)));
        } else {
            syncCard.setBackground(round(Color.WHITE, dp(12), LINE));
            TextView header = text("PLĂȚI ÎN TIMP REAL ⚡", 10, GREEN, true);
            syncCard.addView(header);

            TextView desc = text("Vrei ca plățile să se adauge automat când atingi telefonul de POS? Activează ascultarea notificărilor bancare (Google Wallet, YOU BRD etc.).", 12, INK, false);
            desc.setLineSpacing(dp(3), 1f);
            syncCard.addView(desc, margin(MATCH, WRAP, 0, dp(4), 0, dp(10)));

            Button enableBtn = button("Activează plăți în timp real →", true);
            enableBtn.setOnClickListener(v -> {
                try {
                    startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS));
                    Toast.makeText(this, "Găsește „Bani pe scurt” și activează comutatorul.", Toast.LENGTH_LONG).show();
                } catch (Exception e) {
                    Toast.makeText(this, "Deschide Setări > Aplicații > Notificări speciale.", Toast.LENGTH_SHORT).show();
                }
            });
            syncCard.addView(enableBtn, margin(MATCH, dp(44), 0, 0, 0, 0));
        }
    }

    private void triggerTestPayment() {
        long now = System.currentTimeMillis();
        String dateIso = LocalDate.now().toString();
        Expense sample = new Expense("LIVE|" + now + "|45.50", dateIso, "Kaufland Barbu Văcărescu", "Mâncare", 4550, false);
        expenses.add(sample);
        saveExpenses();
        updateDashboard();
        renderTransactions();
        Toast.makeText(this, "Plată simulată adăugată: 45,50 lei la Kaufland!", Toast.LENGTH_SHORT).show();
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
        statusText.setText("Citesc extrasul local: " + importedFileName + "…");
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
                    statusText.setText("" + drafts.size() + (drafts.size() == 1 ? " plată nouă găsită" : " plăți noi găsite") +
                            (result.incomeCount > 0 ? " · " + result.incomeCount + " încasări omise" : "") +
                            (duplicates > 0 ? " · " + duplicates + " deja existente" : "") +
                            "\n" + importedFileName + "\nPoți ajusta categoria oricărei plăți înainte de salvare.");
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
            TextView empty = text("Nu au rămas tranzacții noi de adăugat.", 12, MUTED, false);
            draftRows.addView(empty, margin(MATCH, WRAP, 0, dp(8), 0, 0));
            return;
        }

        int limit = drafts.size();
        for (int i = 0; i < limit; i++) {
            Expense expense = drafts.get(i);
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.VERTICAL);
            row.setPadding(0, dp(8), 0, dp(8));

            TextView top = text(expense.description, 13, INK, true);
            row.addView(top);

            TextView details = text(formatDate(expense.date) + "   ·   " + moneyBani(expense.amountBani), 12, MUTED, false);
            row.addView(details, margin(MATCH, WRAP, 0, dp(2), 0, dp(4)));

            Spinner spinner = categorySpinner(expense.category);
            spinner.setOnItemSelectedListener(new SimpleItemSelectedListener(position -> {
                String chosenCategory = ExpenseCategories.ALL[position];
                expense.category = chosenCategory;
                ExpenseCategories.learn(MainActivity.this, expense.description, chosenCategory);
            }));
            row.addView(spinner, new LinearLayout.LayoutParams(MATCH, dp(42)));

            draftRows.addView(row);
            if (i + 1 < limit) draftRows.addView(divider());
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
            String msg = currentSearchQuery.isEmpty()
                    ? "Nicio cheltuială în luna aceasta. Folosește butonul de import sau adăugare manuală."
                    : "Niciun rezultat pentru căutarea curentă.";
            TextView empty = text(msg, 12, MUTED, false);
            empty.setLineSpacing(dp(4), 1f);
            transactionRows.addView(empty);
            return;
        }

        int limit = Math.min(month.size(), 50);
        for (int i = 0; i < limit; i++) {
            Expense expense = month.get(i);
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.VERTICAL);
            row.setPadding(0, dp(8), 0, dp(8));
            row.setBackground(round(Color.TRANSPARENT, dp(8), Color.TRANSPARENT));

            // Row header: description & amount
            LinearLayout first = new LinearLayout(this);
            first.setGravity(Gravity.CENTER_VERTICAL);
            first.setOrientation(LinearLayout.HORIZONTAL);

            TextView desc = text(expense.description, 13, INK, true);
            desc.setMaxLines(2);
            first.addView(desc, new LinearLayout.LayoutParams(0, WRAP, 1));

            TextView amountView = text(moneyBani(expense.amountBani), 13, INK, true);
            first.addView(amountView);

            // Edit button (pencil)
            TextView editBtn = text("  ✎", 14, GREEN, false);
            editBtn.setPadding(dp(6), 0, dp(4), 0);
            editBtn.setOnClickListener(v -> showEditExpenseDialog(expense));
            first.addView(editBtn);

            // Delete button
            TextView remove = text("  ×", 18, MUTED, false);
            remove.setPadding(dp(6), 0, 0, 0);
            remove.setOnClickListener(v -> new AlertDialog.Builder(this)
                    .setTitle("Ștergi cheltuiala?")
                    .setMessage(expense.description + " — " + moneyBani(expense.amountBani))
                    .setNegativeButton("Păstrează", null)
                    .setPositiveButton("Șterge", (dialog, which) -> {
                        expenses.remove(expense);
                        saveExpenses();
                        updateDashboard();
                        renderTransactions();
                        Toast.makeText(this, "Cheltuială ștearsă.", Toast.LENGTH_SHORT).show();
                    }).show());
            first.addView(remove);
            row.addView(first);

            // Second row: Date and Category spinner
            LinearLayout second = new LinearLayout(this);
            second.setGravity(Gravity.CENTER_VERTICAL);
            second.addView(text(formatDate(expense.date), 11, MUTED, false), new LinearLayout.LayoutParams(0, dp(38), 1));

            Spinner spinner = categorySpinner(expense.category);
            spinner.setOnItemSelectedListener(new SimpleItemSelectedListener(position -> {
                String chosen = ExpenseCategories.ALL[position];
                if (!chosen.equals(expense.category)) {
                    expense.category = chosen;
                    ExpenseCategories.learn(MainActivity.this, expense.description, chosen);
                    saveExpenses();
                    updateDashboard();
                }
            }));
            second.addView(spinner, new LinearLayout.LayoutParams(dp(150), dp(38)));

            row.addView(second);
            transactionRows.addView(row);
            if (i + 1 < limit) transactionRows.addView(divider());
        }

        if (month.size() > limit) {
            TextView more = text("Și încă " + (month.size() - limit) + " cheltuieli în luna aceasta.", 11, MUTED, false);
            transactionRows.addView(more, margin(MATCH, WRAP, 0, dp(8), 0, 0));
        }
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
        countText.setText(month.size() + (month.size() == 1 ? " cheltuială în luna aleasă" : " cheltuieli în luna aleasă"));

        if (byCategory.isEmpty()) {
            categorySummary.setText("Nicio cheltuială înregistrată pentru luna " + monthName + ".");
            return;
        }

        List<Map.Entry<String, Long>> ranked = new ArrayList<>(byCategory.entrySet());
        ranked.sort((a, b) -> Long.compare(b.getValue(), a.getValue()));

        StringBuilder summary = new StringBuilder();
        for (Map.Entry<String, Long> entry : ranked) {
            int percent = totalBani == 0 ? 0 : (int) Math.round((double) entry.getValue() / totalBani * 100);
            if (summary.length() > 0) summary.append('\n');
            summary.append("•  ").append(entry.getKey()).append("   ").append(percent).append("%   ").append(moneyBani(entry.getValue()));
        }
        categorySummary.setText(summary.toString());
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
        builder.setTitle("Adaugă cheltuială manuală");

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(20), dp(10), dp(20), dp(10));

        final EditText descInput = new EditText(this);
        descInput.setHint("Comerciant / descriere");
        layout.addView(descInput);

        final EditText amountInput = new EditText(this);
        amountInput.setHint("Sumă (ex: 25.50)");
        amountInput.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        layout.addView(amountInput);

        final Spinner catSpinner = categorySpinner("Mâncare");
        layout.addView(catSpinner, margin(MATCH, dp(44), 0, dp(8), 0, 0));

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
                String cat = ExpenseCategories.ALL[catSpinner.getSelectedItemPosition()];
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
        layout.setPadding(dp(20), dp(10), dp(20), dp(10));

        final EditText descInput = new EditText(this);
        descInput.setText(expense.description);
        layout.addView(descInput);

        final EditText amountInput = new EditText(this);
        amountInput.setText(String.format(Locale.US, "%.2f", expense.amount));
        amountInput.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        layout.addView(amountInput);

        final Spinner catSpinner = categorySpinner(expense.category);
        layout.addView(catSpinner, margin(MATCH, dp(44), 0, dp(8), 0, 0));

        builder.setView(layout);
        builder.setPositiveButton("Salvează", (dialog, which) -> {
            String newDesc = descInput.getText().toString().trim();
            String amtStr = amountInput.getText().toString().trim().replace(',', '.');
            if (!newDesc.isEmpty() && !amtStr.isEmpty()) {
                try {
                    expense.description = newDesc;
                    expense.setAmount(Double.parseDouble(amtStr));
                    expense.category = ExpenseCategories.ALL[catSpinner.getSelectedItemPosition()];
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

    private Spinner categorySpinner(String selected) {
        Spinner spinner = new Spinner(this);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, ExpenseCategories.ALL);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
        spinner.setSelection(ExpenseCategories.indexOf(selected), false);
        return spinner;
    }

    private Button button(String label, boolean primary) {
        Button button = new Button(this);
        button.setText(label);
        button.setTextSize(14);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setAllCaps(false);
        button.setTextColor(primary ? Color.WHITE : GREEN);
        button.setBackground(round(primary ? GREEN : Color.WHITE, dp(10), primary ? GREEN : LINE));
        return button;
    }

    private Button smallMonthButton(String label) {
        Button button = new Button(this);
        button.setText(label);
        button.setTextSize(25);
        button.setPadding(0, 0, 0, dp(3));
        button.setTextColor(GREEN);
        button.setBackground(round(PAPER, dp(20), PAPER));
        return button;
    }

    private Button smallHeaderButton(String label) {
        Button button = new Button(this);
        button.setText(label);
        button.setTextSize(12);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setAllCaps(false);
        button.setPadding(dp(12), dp(4), dp(12), dp(4));
        button.setTextColor(GREEN);
        button.setBackground(round(Color.WHITE, dp(8), LINE));
        return button;
    }

    private LinearLayout card() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(14), dp(16), dp(14));
        card.setBackground(round(Color.WHITE, dp(12), LINE));
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

    private View divider() {
        View divider = new View(this);
        divider.setBackgroundColor(LINE);
        return divider;
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

    private interface PositionChange { void onPosition(int position); }

    private static final class SimpleItemSelectedListener implements android.widget.AdapterView.OnItemSelectedListener {
        private final PositionChange change;
        SimpleItemSelectedListener(PositionChange change) { this.change = change; }
        @Override public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) { change.onPosition(position); }
        @Override public void onNothingSelected(android.widget.AdapterView<?> parent) { }
    }
}
