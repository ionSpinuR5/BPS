package ro.bani.pe.scut;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

import org.json.JSONArray;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class BankNotificationListener extends NotificationListenerService {
    public static final String ACTION_EXPENSE_ADDED = "ro.bani.pe.scut.EXPENSE_ADDED";
    public static final String PREF_NAME = "bani_pe_scurt_prefs";
    private static final String CHANNEL_ID = "bani_pe_scurt_tracking";
    private static final Set<String> KNOWN_BANK_PACKAGES = new HashSet<>();
    private static long lastCapturedTime = 0;
    private static String lastCapturedKey = "";

    static {
        // Google Wallet
        KNOWN_BANK_PACKAGES.add("com.google.android.apps.walletnfcrel");
        // BRD
        KNOWN_BANK_PACKAGES.add("com.brd.you");
        KNOWN_BANK_PACKAGES.add("ro.brd.mobile");
        KNOWN_BANK_PACKAGES.add("com.brd.mobile");
        // Revolut
        KNOWN_BANK_PACKAGES.add("com.revolut.revolut");
        // Banca Transilvania
        KNOWN_BANK_PACKAGES.add("ro.bancatransilvania.btpay");
        KNOWN_BANK_PACKAGES.add("ro.bancatransilvania.btrlmobile");
        // ING Bank
        KNOWN_BANK_PACKAGES.add("ro.ing.mobile.banking.android.activity");
        // BCR George
        KNOWN_BANK_PACKAGES.add("ro.bcr.george");
        // Raiffeisen
        KNOWN_BANK_PACKAGES.add("ro.raiffeisen.smartmobile");
        // CEC Bank
        KNOWN_BANK_PACKAGES.add("ro.cec.mobilebanking");
    }

    private static final Pattern AMOUNT_REGEX = Pattern.compile("(?i)(\\d+[\\d.,]*)\\s*(?:lei|ron|eur|€)");
    private static final Pattern MERCHANT_REGEX = Pattern.compile("(?i)(?:la|comerciant|catre)\\s+([A-Za-z0-9\\s.,&'\\-_]+?)(?:\\s+(?:a fost|din contul|cu cardul|la data|in valoare|folosind|pe|\\.|,|$))");

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        if (sbn == null || sbn.getNotification() == null) return;

        String packageName = sbn.getPackageName();
        Notification notification = sbn.getNotification();
        Bundle extras = notification.extras;
        if (extras == null) return;

        CharSequence titleCs = extras.getCharSequence(Notification.EXTRA_TITLE);
        CharSequence textCs = extras.getCharSequence(Notification.EXTRA_TEXT);
        CharSequence bigTextCs = extras.getCharSequence(Notification.EXTRA_BIG_TEXT);

        String title = titleCs == null ? "" : titleCs.toString();
        String text = textCs == null ? "" : textCs.toString();
        String bigText = bigTextCs == null ? "" : bigTextCs.toString();

        String fullContent = (title + " " + text + " " + bigText).trim();
        String lowerContent = fullContent.toLowerCase(Locale.ROOT);

        // Check if package is a known bank or notification contains payment keywords
        boolean isBankPkg = packageName != null && KNOWN_BANK_PACKAGES.contains(packageName);
        boolean isPaymentText = lowerContent.contains("plata") || lowerContent.contains("platit") ||
                lowerContent.contains("achitat") || lowerContent.contains("cheltuit") ||
                lowerContent.contains("autorizat") || lowerContent.contains("tranzactie") ||
                lowerContent.contains("pos") || (isBankPkg && (lowerContent.contains("lei") || lowerContent.contains("ron")));

        if (!isBankPkg && !isPaymentText) return;

        // Skip incoming transfers / salaries / income
        if (lowerContent.contains("incasare") || lowerContent.contains("transfer primit") ||
                lowerContent.contains("ai primit") || lowerContent.contains("alimentare")) {
            return;
        }

        // 1. Extract Amount
        long amountBani = extractAmountBani(fullContent);
        if (amountBani <= 0) return;

        // 2. Extract Merchant Name
        String merchant = extractMerchant(title, text, bigText, packageName);
        if (merchant.isEmpty()) merchant = "Plată card";

        // Clean merchant name
        merchant = cleanMerchant(merchant);

        // Deduplication: prevent duplicate events within 5 seconds for the same amount and merchant
        String dedupeKey = amountBani + "|" + merchant;
        long now = System.currentTimeMillis();
        if (dedupeKey.equals(lastCapturedKey) && (now - lastCapturedTime) < 5000) {
            return;
        }
        lastCapturedKey = dedupeKey;
        lastCapturedTime = now;

        // 3. Auto-categorize (with learning check)
        String category = ExpenseCategories.suggest(this, merchant);

        // 4. Save Expense
        String dateIso = new SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(new Date(now));
        String id = "LIVE|" + now + "|" + (amountBani / 100.0);

        Expense expense = new Expense(id, dateIso, merchant, category, amountBani, false);
        saveExpenseToStorage(expense);

        // 5. Send Broadcast to MainActivity
        sendBroadcast(new Intent(ACTION_EXPENSE_ADDED));

        // 6. Show Confirmation Notification
        showSuccessNotification(expense);
    }

    private long extractAmountBani(String text) {
        Matcher matcher = AMOUNT_REGEX.matcher(text);
        if (matcher.find()) {
            String raw = matcher.group(1);
            if (raw != null) {
                String clean = raw.replace(".", "").replace(',', '.').replaceAll("[^0-9.]", "");
                try {
                    double amt = Double.parseDouble(clean);
                    return Math.round(amt * 100.0);
                } catch (Exception ignored) {}
            }
        }
        return 0;
    }

    private String extractMerchant(String title, String text, String bigText, String packageName) {
        String body = (text + " " + bigText).trim();

        // 1. Try finding "la [Comerciant]" pattern
        Matcher matcher = MERCHANT_REGEX.matcher(body);
        if (matcher.find()) {
            String candidate = matcher.group(1);
            if (candidate != null && candidate.trim().length() >= 2) {
                return candidate.trim();
            }
        }

        // 2. In Google Wallet, Title is very often the Merchant name (e.g. "Kaufland", "Mega Image")
        if (packageName != null && packageName.contains("wallet")) {
            if (!title.isEmpty() && !title.toLowerCase(Locale.ROOT).contains("wallet") && title.length() < 40) {
                return title;
            }
        }

        // 3. Fallback to title if reasonable
        if (!title.isEmpty() && title.length() < 35 && !title.toLowerCase(Locale.ROOT).contains("banca") &&
                !title.toLowerCase(Locale.ROOT).contains("brd") && !title.toLowerCase(Locale.ROOT).contains("notificare")) {
            return title;
        }

        return "Plată comerciant";
    }

    private String cleanMerchant(String raw) {
        String clean = raw.replaceAll("(?i)\\b(SC|SRL|SA|PFA|RO|STR|BUCURESTI)\\b", "")
                .replaceAll("\\s+", " ")
                .trim();
        if (clean.endsWith(".") || clean.endsWith(",")) {
            clean = clean.substring(0, clean.length() - 1).trim();
        }
        return clean.isEmpty() ? raw : clean;
    }

    private synchronized void saveExpenseToStorage(Expense expense) {
        try {
            SharedPreferences prefs = getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
            String saved = prefs.getString("expenses", null);
            if (saved == null) {
                saved = getSharedPreferences("MainActivity", Context.MODE_PRIVATE).getString("expenses", "[]");
            }
            JSONArray array = new JSONArray(saved);
            array.put(expense.toJson());
            prefs.edit().putString("expenses", array.toString()).apply();
        } catch (Exception ignored) {}
    }

    private void showSuccessNotification(Expense expense) {
        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) return;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Plăți înregistrate automat",
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("Notificări de confirmare când o plată bancară este salvată");
            manager.createNotificationChannel(channel);
        }

        double lei = expense.amountBani / 100.0;
        String content = String.format(Locale.US, "%.2f lei · %s (%s)", lei, expense.description, expense.category);

        Notification.Builder builder;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder = new Notification.Builder(this, CHANNEL_ID);
        } else {
            builder = new Notification.Builder(this);
        }

        builder.setContentTitle("Bani pe scurt — Plată detectată ⚡")
                .setContentText(content)
                .setSmallIcon(android.R.drawable.stat_notify_sync)
                .setAutoCancel(true);

        try {
            manager.notify((int) System.currentTimeMillis(), builder.build());
        } catch (Exception ignored) {}
    }
}
