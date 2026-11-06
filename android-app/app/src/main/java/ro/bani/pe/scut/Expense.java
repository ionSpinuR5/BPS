package ro.bani.pe.scut;

import org.json.JSONException;
import org.json.JSONObject;

import java.math.BigDecimal;
import java.math.RoundingMode;

final class Expense {
    String id;
    String date; // YYYY-MM-DD
    String description;
    String category;
    long amountBani; // integer cents (bani, eurocents, cents)
    double amount;   // Kept synchronized for compatibility
    boolean income;
    String currency = "RON"; // "RON", "EUR", "USD", "GBP"

    Expense() {}

    Expense(String id, String date, String description, String category, long amountBani, boolean income) {
        this(id, date, description, category, amountBani, income, "RON");
    }

    Expense(String id, String date, String description, String category, long amountBani, boolean income, String currency) {
        this.id = id;
        this.date = date;
        this.description = description;
        this.category = category;
        setAmountBani(amountBani);
        this.income = income;
        this.currency = (currency != null && !currency.trim().isEmpty()) ? currency.trim().toUpperCase() : "RON";
    }

    String getCurrencySymbol() {
        if ("EUR".equalsIgnoreCase(currency)) return "€";
        if ("USD".equalsIgnoreCase(currency)) return "$";
        if ("GBP".equalsIgnoreCase(currency)) return "£";
        return "lei";
    }

    String formatAmount() {
        double val = amountBani / 100.0;
        if ("EUR".equalsIgnoreCase(currency)) return String.format(java.util.Locale.GERMANY, "%,.2f €", val);
        if ("USD".equalsIgnoreCase(currency)) return String.format(java.util.Locale.US, "$%,.2f", val);
        if ("GBP".equalsIgnoreCase(currency)) return String.format(java.util.Locale.UK, "£%,.2f", val);
        return String.format(java.util.Locale.GERMANY, "%,.2f lei", val);
    }

    void setAmountBani(long bani) {
        this.amountBani = bani;
        this.amount = BigDecimal.valueOf(bani)
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP)
                .doubleValue();
    }

    void setAmount(double amt) {
        this.amountBani = Math.round(amt * 100.0);
        this.amount = BigDecimal.valueOf(this.amountBani)
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP)
                .doubleValue();
    }

    JSONObject toJson() throws JSONException {
        JSONObject object = new JSONObject();
        object.put("id", id);
        object.put("date", date);
        object.put("description", description);
        object.put("category", category);
        object.put("amount", amount);
        object.put("amountBani", amountBani);
        object.put("income", income);
        object.put("currency", currency != null ? currency : "RON");
        return object;
    }

    static Expense fromJson(JSONObject object) throws JSONException {
        Expense expense = new Expense();
        expense.id = object.getString("id");
        expense.date = object.getString("date");
        expense.description = object.getString("description");
        expense.category = object.optString("category", "Altele");
        if (object.has("amountBani")) {
            expense.setAmountBani(object.getLong("amountBani"));
        } else {
            expense.setAmount(object.getDouble("amount"));
        }
        expense.income = object.optBoolean("income", false);
        expense.currency = object.optString("currency", "RON");
        return expense;
    }
}
