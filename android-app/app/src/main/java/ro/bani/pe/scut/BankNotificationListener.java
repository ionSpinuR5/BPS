package ro.bani.pe.scut;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class BankNotificationListener extends NotificationListenerService {
    public static final String ACTION_EXPENSE_ADDED = "ro.bani.pe.scut.EXPENSE_ADDED";
    public static final String ACTION_LISTENER_CONNECTED = "ro.bani.pe.scut.LISTENER_CONNECTED";
    public static final String PREF_NAME = "bani_pe_scurt_prefs";
    public static final String PREF_LOGS = "notification_diagnostic_logs";
    private static final String CHANNEL_ID = "bani_pe_scurt_tracking";

    public static volatile boolean isServiceConnected = false;

    private static final Set<String> KNOWN_BANK_PACKAGES = new HashSet<>();
    private static long lastCapturedTime = 0;
    private static String lastCapturedKey = "";

    static {
        // Google Wallet & Google Services NFC Tap
        KNOWN_BANK_PACKAGES.add("com.google.android.apps.walletnfcrel");
        KNOWN_BANK_PACKAGES.add("com.google.android.gms");
        KNOWN_BANK_PACKAGES.add("com.google.android.apps.nbu.paisa.user");

        // Revolut
        KNOWN_BANK_PACKAGES.add("com.revolut.revolut");

        // Banca Transilvania
        KNOWN_BANK_PACKAGES.add("ro.bancatransilvania.btpay");
        KNOWN_BANK_PACKAGES.add("ro.bancatransilvania.btrlmobile");
        KNOWN_BANK_PACKAGES.add("com.bancatransilvania.btpay");

        // BRD
        KNOWN_BANK_PACKAGES.add("com.brd.you");
        KNOWN_BANK_PACKAGES.add("ro.brd.mobile");
        KNOWN_BANK_PACKAGES.add("com.brd.mobile");

        // ING Bank
        KNOWN_BANK_PACKAGES.add("ro.ing.mobile.banking.android.activity");

        // BCR George
        KNOWN_BANK_PACKAGES.add("ro.bcr.george");

        // Raiffeisen
        KNOWN_BANK_PACKAGES.add("ro.raiffeisen.smartmobile");

        // CEC Bank
        KNOWN_BANK_PACKAGES.add("ro.cec.mobilebanking");

        // UniCredit
        KNOWN_BANK_PACKAGES.add("eu.unicreditgroup.romania.cib");
        KNOWN_BANK_PACKAGES.add("eu.unicreditgroup.romania");

        // Libra Bank
        KNOWN_BANK_PACKAGES.add("ro.librabank.mobilebanking");

        // Alpha Bank
        KNOWN_BANK_PACKAGES.add("ro.alphabank.alphamobilebanking");

        // Wise & Curve & PayPal
        KNOWN_BANK_PACKAGES.add("com.transferwise.android");
        KNOWN_BANK_PACKAGES.add("com.imaginecurve.curve");
        KNOWN_BANK_PACKAGES.add("com.paypal.android.p2pmobile");

        // SMS (Bank transaction messages)
        KNOWN_BANK_PACKAGES.add("com.google.android.apps.messaging");
        KNOWN_BANK_PACKAGES.add("com.android.mms");
        KNOWN_BANK_PACKAGES.add("com.miui.sms");
        KNOWN_BANK_PACKAGES.add("com.miui.mms");
    }

    // Flexible patterns matching amount before OR after currency symbol
    private static final Pattern PATTERN_AMOUNT_THEN_CURR = Pattern.compile("(?i)(\\d+[\\d.,]*)\\s*(?:lei|ron|eur|€|\\$|usd|gbp|£)");
    private static final Pattern PATTERN_CURR_THEN_AMOUNT = Pattern.compile("(?i)(?:lei|ron|eur|€|\\$|usd|gbp|£)\\s*(\\d+[\\d.,]*)");

    private static final Pattern MERCHANT_REGEX = Pattern.compile(
            "(?i)(?:la|at|catre|to|comerciant|merchant)\\s+([A-Za-z0-9\\u00C0-\\u024F\\s.,&'\\-_]+?)" +
                    "(?:\\s+(?:a fost|din contul|cu cardul|la data|in valoare|folosind|pe|with|from|using|was|has|\\.|,|$))"
    );

    @Override
    public void onListenerConnected() {
        super.onListenerConnected();
        isServiceConnected = true;
        sendBroadcast(new Intent(ACTION_LISTENER_CONNECTED));
        addDiagnosticLog(this, "SYSTEM", "Bani pe scurt", "Serviciu activat", "CONECTAT ✅: Ascultătorul este activ în fundal");
    }

    @Override
    public void onListenerDisconnected() {
        super.onListenerDisconnected();
        isServiceConnected = false;
        sendBroadcast(new Intent(ACTION_LISTENER_CONNECTED));
        addDiagnosticLog(this, "SYSTEM", "Bani pe scurt", "Serviciu deconectat", "DECONECTAT ⚠️: Sistemul a oprit serviciul");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            requestRebind(new ComponentName(this, BankNotificationListener.class));
        }
    }

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

        // Allow test notification from our own app, but skip normal self-notifications
        boolean isSelfTest = getPackageName().equals(packageName) && fullContent.contains("[TEST]");
        if (getPackageName().equals(packageName) && !isSelfTest) {
            return;
        }

        boolean isKnownBank = packageName != null && KNOWN_BANK_PACKAGES.contains(packageName);
        boolean hasPaymentKeyword = containsPaymentKeyword(lowerContent);
        long amountBani = extractAmountBani(fullContent);

        // Skip incoming transfers / salaries / income
        if (lowerContent.contains("incasare") || lowerContent.contains("transfer primit") ||
                lowerContent.contains("ai primit") || lowerContent.contains("alimentare cont") ||
                lowerContent.contains("received") || lowerContent.contains("incoming")) {
            addDiagnosticLog(this, packageName, title, text, "IGNORAT: Încasare / Transfer primit");
            return;
        }

        // Check if relevant
        if (amountBani <= 0) {
            if (isKnownBank) {
                addDiagnosticLog(this, packageName, title, text, "IGNORAT: Notificare bancară fără sumă detectabilă");
            }
            return;
        }

        if (!isKnownBank && !hasPaymentKeyword && !isSelfTest) {
            // Not a financial app and doesn't contain payment language
            return;
        }

        // Extract Merchant Name
        String merchant = extractMerchant(title, text, bigText, packageName);
        if (merchant.isEmpty()) merchant = "Plată card";

        // Clean merchant name
        merchant = cleanMerchant(merchant);

        // Deduplication: prevent duplicate notifications within 5 seconds
        String dedupeKey = amountBani + "|" + merchant;
        long now = System.currentTimeMillis();
        if (dedupeKey.equals(lastCapturedKey) && (now - lastCapturedTime) < 5000) {
            addDiagnosticLog(this, packageName, title, text, "DUPLICAT: Aceeași tranzacție recepționată în <5s");
            return;
        }
        lastCapturedKey = dedupeKey;
        lastCapturedTime = now;

        // Auto-categorize
        String category = ExpenseCategories.suggest(this, merchant);

        // Save Expense
        String dateIso = new SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(new Date(now));
        String id = "LIVE|" + now + "|" + (amountBani / 100.0);

        Expense expense = new Expense(id, dateIso, merchant, category, amountBani, false);
        saveExpenseToStorage(expense);

        // Broadcast to MainActivity
        sendBroadcast(new Intent(ACTION_EXPENSE_ADDED));

        // Show Confirmation Notification
        showSuccessNotification(expense);

        // Log to diagnostics
        double lei = amountBani / 100.0;
        addDiagnosticLog(this, packageName, title, text, String.format(Locale.US, "CAPURAT ✅: %.2f lei la %s [%s]", lei, merchant, category));
    }

    private static boolean containsPaymentKeyword(String s) {
        return s.contains("plata") || s.contains("platit") || s.contains("achitat") ||
                s.contains("cheltuit") || s.contains("autorizat") || s.contains("tranzactie") ||
                s.contains("pos") || s.contains("cumparat") || s.contains("debitat") ||
                s.contains("card") || s.contains("spent") || s.contains("paid") ||
                s.contains("payment") || s.contains("purchase") || s.contains("charged") ||
                s.contains("transaction") || s.contains("tap to pay");
    }

    public static long extractAmountBani(String text) {
        if (text == null || text.isEmpty()) return 0;

        // 1. Amount followed by currency (e.g. 45.50 lei, 45,50 RON)
        Matcher m1 = PATTERN_AMOUNT_THEN_CURR.matcher(text);
        if (m1.find()) {
            String raw = m1.group(1);
            long val = parseAmountStringToBani(raw);
            if (val > 0) return val;
        }

        // 2. Currency followed by amount (e.g. RON 45.50, €12.50)
        Matcher m2 = PATTERN_CURR_THEN_AMOUNT.matcher(text);
        if (m2.find()) {
            String raw = m2.group(1);
            long val = parseAmountStringToBani(raw);
            if (val > 0) return val;
        }

        return 0;
    }

    public static long parseAmountStringToBani(String raw) {
        if (raw == null) return 0;
        String s = raw.trim();

        if (s.contains(".") && s.contains(",")) {
            int lastDot = s.lastIndexOf('.');
            int lastComma = s.lastIndexOf(',');
            if (lastComma > lastDot) {
                s = s.replace(".", "").replace(',', '.');
            } else {
                s = s.replace(",", "");
            }
        } else if (s.contains(",")) {
            s = s.replace(',', '.');
        }

        s = s.replaceAll("[^0-9.]", "");
        if (s.isEmpty()) return 0;

        try {
            double val = Double.parseDouble(s);
            return Math.round(val * 100.0);
        } catch (Exception e) {
            return 0;
        }
    }

    private String extractMerchant(String title, String text, String bigText, String packageName) {
        String body = (text + " " + bigText).trim();

        // 1. Find "la [Comerciant]" pattern
        Matcher matcher = MERCHANT_REGEX.matcher(body);
        if (matcher.find()) {
            String candidate = matcher.group(1);
            if (candidate != null && candidate.trim().length() >= 2) {
                return candidate.trim();
            }
        }

        // 2. Google Wallet / Google Play Services: Title is typically the Merchant Name!
        if (packageName != null && (packageName.contains("wallet") || packageName.contains("gms") || packageName.contains("paisa"))) {
            if (!title.isEmpty() && !title.toLowerCase(Locale.ROOT).contains("wallet") &&
                    !title.toLowerCase(Locale.ROOT).contains("pay") && title.length() < 40) {
                return title;
            }
        }

        // 3. Fallback to Title if reasonable and not generic bank name
        if (!title.isEmpty() && title.length() < 35) {
            String lowerTitle = title.toLowerCase(Locale.ROOT);
            if (!lowerTitle.contains("banca") && !lowerTitle.contains("notificare") &&
                    !lowerTitle.contains("plata") && !lowerTitle.contains("payment") &&
                    !lowerTitle.contains("transact") && !lowerTitle.contains("wallet") &&
                    !lowerTitle.contains("google")) {
                return title;
            }
        }

        return "Plată comerciant";
    }

    private String cleanMerchant(String raw) {
        String clean = raw.replaceAll("(?i)\\b(SC|SRL|SA|PFA|RO|STR|BUCURESTI|CARD|NFC)\\b", "")
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

    public static synchronized void addDiagnosticLog(Context context, String pkg, String title, String text, String status) {
        try {
            SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
            String saved = prefs.getString(PREF_LOGS, "[]");
            JSONArray arr = new JSONArray(saved);

            JSONObject item = new JSONObject();
            item.put("time", System.currentTimeMillis());
            item.put("pkg", pkg != null ? pkg : "");
            item.put("title", title != null ? title : "");
            item.put("text", text != null ? text : "");
            item.put("status", status != null ? status : "");

            JSONArray updated = new JSONArray();
            updated.put(item);
            for (int i = 0; i < Math.min(14, arr.length()); i++) {
                updated.put(arr.getJSONObject(i));
            }
            prefs.edit().putString(PREF_LOGS, updated.toString()).apply();
        } catch (Exception ignored) {}
    }

    public static List<JSONObject> getDiagnosticLogs(Context context) {
        List<JSONObject> list = new ArrayList<>();
        try {
            SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
            String saved = prefs.getString(PREF_LOGS, "[]");
            JSONArray arr = new JSONArray(saved);
            for (int i = 0; i < arr.length(); i++) {
                list.add(arr.getJSONObject(i));
            }
        } catch (Exception ignored) {}
        return list;
    }

    public static void postSimulatedPaymentNotification(Context context) {
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) return;

        String testChannelId = "bani_pe_scurt_test_sim";
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(testChannelId, "Test Simulare Plată", NotificationManager.IMPORTANCE_HIGH);
            ch.setDescription("Canal pentru testarea ascultătorului de notificări");
            manager.createNotificationChannel(ch);
        }

        Notification.Builder builder = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                ? new Notification.Builder(context, testChannelId)
                : new Notification.Builder(context);

        builder.setContentTitle("Mega Image [TEST]")
                .setContentText("Plată aprobată: 35,50 lei la Mega Image cu cardul *9999")
                .setSmallIcon(android.R.drawable.stat_notify_sync)
                .setAutoCancel(true);

        try {
            manager.notify(777, builder.build());
        } catch (Exception ignored) {}
    }
}
