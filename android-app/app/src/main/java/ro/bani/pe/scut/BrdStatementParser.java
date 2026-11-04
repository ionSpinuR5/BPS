package ro.bani.pe.scut;

import android.content.Context;

import com.tom_roush.pdfbox.android.PDFBoxResourceLoader;
import com.tom_roush.pdfbox.pdmodel.PDDocument;
import com.tom_roush.pdfbox.text.PDFTextStripper;
import com.tom_roush.pdfbox.text.TextPosition;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class BrdStatementParser {
    static final class ImportResult {
        final List<Expense> expenses = new ArrayList<>();
        int incomeCount;
        int skippedCount;
    }

    private static final Pattern DATE = Pattern.compile("\\d{2}/\\d{2}/\\d{4}");
    private static final Pattern ISO_DATE = Pattern.compile("\\d{4}-\\d{2}-\\d{2}");
    private static final Pattern AMOUNT = Pattern.compile("\\d[\\d.]*,\\d{2}");
    private static final Pattern OPERATION_REF = Pattern.compile("\\b(?:OPH?|NC)\\s*([A-Z0-9/]+)", Pattern.CASE_INSENSITIVE);
    private static final String[] GENERIC_LINES = {
            "UTILIZARE POS COMERCIANT ALTE BC", "UTILIZARE POS COMERCIANT BRD",
            "CARD/TERMINALE", "PLATA CU CARDUL", "PLATA POS"
    };

    private static final class PageLine {
        String date;
        String description;
        String debit;
        String credit;
        String valueDate;
    }

    private static final class TransactionBuilder {
        String date;
        String debit;
        String credit;
        String reference = "";
        final List<String> descriptionLines = new ArrayList<>();
    }

    private final List<PageLine> lines = new ArrayList<>();
    private final Map<String, Integer> occurrenceTracker = new HashMap<>();

    ImportResult parse(Context context, InputStream input) throws Exception {
        PDFBoxResourceLoader.init(context.getApplicationContext());
        lines.clear();
        occurrenceTracker.clear();
        try (PDDocument document = PDDocument.load(input)) {
            if (document.isEncrypted()) throw new IllegalArgumentException("PDF-ul este protejat cu parolă.");
            PDFTextStripper stripper = new PDFTextStripper() {
                @Override
                protected void writeString(String text, List<TextPosition> positions) {
                    PageLine line = splitLine(positions);
                    if (line != null) lines.add(line);
                }
            };
            stripper.setSortByPosition(true);
            stripper.getText(document);
        }
        if (lines.isEmpty()) throw new IllegalArgumentException("Nu am putut citi textul din PDF. Alege extrasul PDF original din YOU BRD, nu o fotografie sau o scanare.");
        return buildTransactions(context, lines);
    }

    ImportResult parseCsv(Context context, InputStream input) throws Exception {
        ImportResult result = new ImportResult();
        occurrenceTracker.clear();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
            String headerLine = reader.readLine();
            if (headerLine == null) throw new IllegalArgumentException("Fișierul CSV este gol.");
            if (headerLine.startsWith("\uFEFF")) {
                headerLine = headerLine.substring(1);
            }

            char delimiter = headerLine.contains(";") ? ';' : ',';
            String[] headers = splitCsvLine(headerLine, delimiter);
            int dateIdx = -1, descIdx = -1, amountIdx = -1, catIdx = -1, debitIdx = -1, creditIdx = -1;

            for (int i = 0; i < headers.length; i++) {
                String h = normalize(headers[i]);
                if (h.contains("DATA") || h.contains("DATE")) dateIdx = i;
                else if (h.contains("DESCRI") || h.contains("DETALII") || h.contains("COMERCIANT")) descIdx = i;
                else if (h.contains("CATEGOR")) catIdx = i;
                else if (h.contains("DEBIT")) debitIdx = i;
                else if (h.contains("CREDIT") || h.contains("INCASAR")) creditIdx = i;
                else if (h.contains("SUMA") || h.contains("AMOUNT") || h.contains("VALOARE")) amountIdx = i;
            }

            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;
                String[] parts = splitCsvLine(line, delimiter);
                if (parts.length <= Math.max(dateIdx, Math.max(descIdx, amountIdx))) continue;

                String rawDate = dateIdx >= 0 && dateIdx < parts.length ? parts[dateIdx].trim() : "";
                String rawDesc = descIdx >= 0 && descIdx < parts.length ? parts[descIdx].trim() : "Cheltuială";
                String rawCat = catIdx >= 0 && catIdx < parts.length ? parts[catIdx].trim() : "";

                String isoDate = null;
                if (rawDate.matches("\\d{4}-\\d{2}-\\d{2}")) {
                    isoDate = rawDate;
                } else if (rawDate.matches("\\d{2}/\\d{2}/\\d{4}")) {
                    isoDate = toIsoDate(rawDate);
                } else if (rawDate.matches("\\d{2}\\.\\d{2}\\.\\d{4}")) {
                    isoDate = toIsoDate(rawDate.replace('.', '/'));
                }
                if (isoDate == null) continue;

                long amountBani = 0;
                boolean isIncome = false;

                if (debitIdx >= 0 && debitIdx < parts.length && !parts[debitIdx].trim().isEmpty()) {
                    amountBani = parseAmountToBani(parts[debitIdx].trim());
                } else if (creditIdx >= 0 && creditIdx < parts.length && !parts[creditIdx].trim().isEmpty()) {
                    result.incomeCount++;
                    continue;
                } else if (amountIdx >= 0 && amountIdx < parts.length) {
                    String rawAmt = parts[amountIdx].trim();
                    if (rawAmt.startsWith("-")) {
                        amountBani = parseAmountToBani(rawAmt.substring(1));
                    } else if (rawAmt.startsWith("+")) {
                        result.incomeCount++;
                        continue;
                    } else {
                        amountBani = parseAmountToBani(rawAmt);
                    }
                }

                if (amountBani <= 0) {
                    result.skippedCount++;
                    continue;
                }

                String category = (!rawCat.isEmpty() && ExpenseCategories.indexOf(rawCat) >= 0)
                        ? rawCat
                        : ExpenseCategories.suggest(context, rawDesc);

                String baseKey = isoDate + "|" + amountBani + "|" + normalize(rawDesc);
                int count = occurrenceTracker.getOrDefault(baseKey, 0) + 1;
                occurrenceTracker.put(baseKey, count);
                String uniqueId = baseKey + (count > 1 ? ("#" + count) : "");

                Expense expense = new Expense(uniqueId, isoDate, rawDesc, category, amountBani, isIncome);
                result.expenses.add(expense);
            }
        }
        Collections.sort(result.expenses, (a, b) -> b.date.compareTo(a.date));
        return result;
    }

    private String[] splitCsvLine(String line, char delimiter) {
        List<String> tokens = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '\"') {
                inQuotes = !inQuotes;
            } else if (c == delimiter && !inQuotes) {
                tokens.add(sb.toString().trim());
                sb.setLength(0);
            } else {
                sb.append(c);
            }
        }
        tokens.add(sb.toString().trim());
        return tokens.toArray(new String[0]);
    }

    private PageLine splitLine(List<TextPosition> positions) {
        if (positions == null || positions.isEmpty()) return null;
        List<TextPosition> sorted = new ArrayList<>(positions);
        Collections.sort(sorted, Comparator.comparingDouble(TextPosition::getXDirAdj));
        StringBuilder date = new StringBuilder();
        StringBuilder description = new StringBuilder();
        StringBuilder debit = new StringBuilder();
        StringBuilder credit = new StringBuilder();
        StringBuilder valueDate = new StringBuilder();
        for (TextPosition position : sorted) {
            String character = position.getUnicode();
            if (character == null) continue;
            float x = position.getXDirAdj();
            if (x < 78) date.append(character);
            else if (x < 385) description.append(character);
            else if (x < 470) debit.append(character);
            else if (x < 518) credit.append(character);
            else valueDate.append(character);
        }
        PageLine line = new PageLine();
        line.date = findDate(date.toString());
        line.description = description.toString().replaceAll("\\s+", " ").trim();
        line.debit = findAmount(debit.toString());
        line.credit = findAmount(credit.toString());
        line.valueDate = findDate(valueDate.toString());
        if (line.date == null && line.description.isEmpty() && line.debit == null && line.credit == null) return null;
        return line;
    }

    private ImportResult buildTransactions(Context context, List<PageLine> pageLines) throws ParseException {
        ImportResult result = new ImportResult();
        Map<String, Expense> unique = new LinkedHashMap<>();
        TransactionBuilder current = null;
        for (PageLine line : pageLines) {
            if (line.date != null) {
                if (current != null) addTransaction(context, current, unique, result);
                current = new TransactionBuilder();
                current.date = toIsoDate(line.date);
                current.debit = line.debit;
                current.credit = line.credit;
                appendDescription(current, line.description);
            } else if (current != null) {
                if (line.debit != null && current.debit == null) current.debit = line.debit;
                if (line.credit != null && current.credit == null) current.credit = line.credit;
                appendDescription(current, line.description);
            }
        }
        if (current != null) addTransaction(context, current, unique, result);
        result.expenses.addAll(unique.values());
        Collections.sort(result.expenses, (a, b) -> b.date.compareTo(a.date));
        return result;
    }

    private void appendDescription(TransactionBuilder current, String raw) {
        if (raw == null || raw.isEmpty()) return;
        Matcher ref = OPERATION_REF.matcher(raw);
        if (ref.find()) current.reference = ref.group(1);
        String normalized = normalize(raw);
        if (normalized.startsWith("CARD NR") || normalized.startsWith("OP ") || normalized.startsWith("OPH") || normalized.startsWith("NC ")) return;
        if (normalized.matches(".*\\bRO\\d{20,}\\b.*")) return;
        for (String generic : GENERIC_LINES) if (normalized.equals(generic)) return;
        current.descriptionLines.add(raw.trim());
    }

    private void addTransaction(Context context, TransactionBuilder transaction, Map<String, Expense> unique, ImportResult result) {
        boolean debit = transaction.debit != null;
        boolean credit = transaction.credit != null;
        if (!debit && !credit) { result.skippedCount++; return; }
        if (debit && credit) { result.skippedCount++; return; }
        if (credit) { result.incomeCount++; return; }

        long amountBani = parseAmountToBani(transaction.debit);
        if (amountBani <= 0) { result.skippedCount++; return; }

        String description = cleanDescription(transaction.descriptionLines);
        if (description.isEmpty()) description = "Cheltuială BRD";

        String category = ExpenseCategories.suggest(context, description);

        // Safe unique ID generation preventing dropped twin transactions
        String id;
        if (!transaction.reference.isEmpty()) {
            id = "BRD|" + transaction.reference;
        } else {
            String baseKey = transaction.date + "|" + amountBani + "|" + normalize(description);
            int count = occurrenceTracker.getOrDefault(baseKey, 0) + 1;
            occurrenceTracker.put(baseKey, count);
            id = baseKey + (count > 1 ? ("#" + count) : "");
        }

        if (unique.containsKey(id)) {
            result.skippedCount++;
            return;
        }

        Expense expense = new Expense(id, transaction.date, description, category, amountBani, false);
        unique.put(expense.id, expense);
    }

    private String cleanDescription(List<String> lines) {
        StringBuilder result = new StringBuilder();
        for (String raw : lines) {
            String normalized = normalize(raw);
            if (normalized.isEmpty()) continue;
            if (normalized.startsWith("CARD NR") || normalized.startsWith("CARD TERMINALE")) continue;
            if (normalized.matches("^(OPH?|NC)\\s*[A-Z0-9/]+.*")) continue;
            if (normalized.matches(".*\\bRO\\d{20,}\\b.*")) continue;
            if (normalized.equals("AUTOMAT") || normalized.equals("MOBILE OMNICHANNEL")) continue;
            if (result.length() > 0) result.append(" · ");
            result.append(raw.trim());
        }
        return result.toString().replaceAll("\\s+", " ").trim();
    }

    private String findDate(String value) {
        Matcher matcher = DATE.matcher(value == null ? "" : value);
        return matcher.find() ? matcher.group() : null;
    }

    private String findAmount(String value) {
        String compact = (value == null ? "" : value).replaceAll("\\s+", "");
        Matcher matcher = AMOUNT.matcher(compact);
        return matcher.find() ? matcher.group() : null;
    }

    private long parseAmountToBani(String value) {
        if (value == null) return 0;
        String clean = value.replace(".", "").replace(',', '.').replaceAll("[^0-9.]", "");
        try {
            double parsed = Double.parseDouble(clean);
            return Math.round(parsed * 100.0);
        } catch (Exception e) {
            return 0;
        }
    }

    private String toIsoDate(String value) throws ParseException {
        SimpleDateFormat input = new SimpleDateFormat("dd/MM/yyyy", Locale.ROOT);
        input.setLenient(false);
        Date date = input.parse(value);
        return new SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(date);
    }

    static String normalize(String value) {
        String decomposed = Normalizer.normalize(value == null ? "" : value, Normalizer.Form.NFD);
        return decomposed.replaceAll("\\p{M}+", "").toUpperCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
    }
}
