package ro.bani.pe.scut;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.Locale;
import java.util.Map;

final class ExpenseCategories {
    static final String[] ALL = {
            "Mâncare",
            "Transport",
            "Locuință",
            "Cumpărături",
            "Sănătate",
            "Timp liber",
            "Altele"
    };

    private static final String PREF_LEARNED = "bani_learned_categories";

    static String suggest(Context context, String description) {
        if (context != null) {
            String learned = getLearnedCategory(context, description);
            if (learned != null) {
                return learned;
            }
        }
        return suggestDefault(description);
    }

    static String suggestDefault(String description) {
        String text = BrdStatementParser.normalize(description);
        if (containsAny(text, "LIDL", "KAUFLAND", "CARREFOUR", "AUCHAN", "MEGA IMAGE", "PROFI", "PENNY", "SELGROS",
                "SUPERMARKET", "MARKET", "GROCERY", "RESTAURANT", "CANTINA", "PIZZA", "COFFEE", "CAFE", "PATISERIE",
                "BRUTARIE", "FOOD", "GLOVO", "TAZZ", "BOLT FOOD", "MCDONALD", "KFC", "SUBWAY", "STARBUCKS", "5TOGO")) {
            return "Mâncare";
        }
        if (containsAny(text, "BOLT", "UBER", "TAXI", "MOL ", "OMV", "PETROM", "ROMPETROL", "LUKOIL", "SHELL", "SOCAR",
                "PARKING", "PARCARE", "CTP", "STB", "METROREX", "CFR", "TRAIN", "TREN", "AUTOBUZ", "TRAMVAI", "WIZZ", "RYANAIR", "TAROM")) {
            return "Transport";
        }
        if (containsAny(text, "E.ON", "EON ", "ENGIE", "ELECTRICA", "ENEL", "HIDROELECTRICA", "DELGAZ", "PPC ", "NOVA POWER",
                "APA CANAL", "AQUA", "DIGI", "RCS", "RDS", "ORANGE", "VODAFONE", "TELEKOM", "CHIRIE", "RENT", "GAZ", "CURENT", "INTRETINERE", "ASOCIATIE DE PROPRIETARI")) {
            return "Locuință";
        }
        if (containsAny(text, "FARMACIA", "FARMACII", "DR MAX", "HELP NET", "CATENA", "SENSI", "DONA", "MEDLIFE", "REGINA MARIA",
                "SANADOR", "SYNEVO", "BIOCLINICA", "CLINICA", "SPITAL", "DENT", "STOMATOLOG", "PHARM", "MEDIC", "ANALIZE", "OPTIK", "LENS")) {
            return "Sănătate";
        }
        if (containsAny(text, "NETFLIX", "SPOTIFY", "HBO", "MAX", "DISNEY", "YOUTUBE", "CINEMA", "CINEMACITY", "TEATRU",
                "CONCERT", "MUSEUM", "MUZEU", "STEAM", "PLAYSTATION", "XBOX", "NINTENDO", "BOOKING", "AIRBNB", "HOTEL",
                "PENSIUNE", "TICKET", "BILET", "EVENTIM", "IABILET", "WORLD CLASS", "FITNESS", "SALA")) {
            return "Timp liber";
        }
        if (containsAny(text, "EMAG", "CARTURESTI", "CĂRTUREȘTI", "ZARA", "H&M", "HM ", "DECATHLON", "IKEA", "JYSK", "DEDEMAN", "LEROY", "BRICO", "HORNBACH",
                "DEICHMANN", "DM ", "DOUGLAS", "SEPHORA", "NOTINO", "FASHION", "SHOP", "MAGAZIN", "RETAIL", "PEPCO", "SINSAY", "RESERVED", "ALINTEX", "ALTEX", "FLANCO")) {
            return "Cumpărături";
        }
        return "Altele";
    }

    static void learn(Context context, String description, String category) {
        if (context == null || description == null || category == null) return;
        String merchantToken = extractMerchantKey(description);
        if (merchantToken.length() >= 3) {
            SharedPreferences prefs = context.getSharedPreferences(PREF_LEARNED, Context.MODE_PRIVATE);
            prefs.edit().putString(merchantToken, category).apply();
        }
    }

    private static String getLearnedCategory(Context context, String description) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_LEARNED, Context.MODE_PRIVATE);
        Map<String, ?> all = prefs.getAll();
        if (all.isEmpty()) return null;
        String normalizedDesc = BrdStatementParser.normalize(description);
        for (Map.Entry<String, ?> entry : all.entrySet()) {
            String key = entry.getKey();
            if (normalizedDesc.contains(key)) {
                return (String) entry.getValue();
            }
        }
        return null;
    }

    static String extractMerchantKey(String description) {
        String clean = BrdStatementParser.normalize(description);
        clean = clean.replaceAll("\\b(SC|SRL|SA|PFA|RO|STR|BUCURESTI|CLUJ|IASI|TIMISOARA)\\b", "").trim();
        String[] words = clean.split("\\s+");
        if (words.length >= 2 && words[0].length() + words[1].length() < 20) {
            return (words[0] + " " + words[1]).trim();
        } else if (words.length >= 1) {
            return words[0].trim();
        }
        return clean;
    }

    static int indexOf(String category) {
        for (int i = 0; i < ALL.length; i++) {
            if (ALL[i].equalsIgnoreCase(category)) return i;
        }
        return ALL.length - 1; // "Altele"
    }

    private static boolean containsAny(String text, String... terms) {
        String upper = text.toUpperCase(Locale.ROOT);
        for (String term : terms) {
            if (upper.contains(term.toUpperCase(Locale.ROOT))) return true;
        }
        return false;
    }

    private ExpenseCategories() {}
}
