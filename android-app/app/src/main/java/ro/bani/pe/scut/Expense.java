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
    long amountBani; // 100 bani = 1 RON, prevents floating-point inaccuracies
    double amount;   // Kept synchronized for compatibility
    boolean income;

    Expense() {}

    Expense(String id, String date, String description, String category, long amountBani, boolean income) {
        this.id = id;
        this.date = date;
        this.description = description;
        this.category = category;
        setAmountBani(amountBani);
        this.income = income;
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
        return expense;
    }
}
