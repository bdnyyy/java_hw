import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Сравнение одного и того же SELECT по равенству без форматирования и дискового ввода-вывода. */
public class PerformanceTest {
    private static volatile long checksum;

    /** Выводит медиану семи серий; аргументы: число строк и запросов в серии. */
    public static void main(String[] args) {
        int count = args.length > 0 ? Integer.parseInt(args[0]) : 100000;
        int queries = args.length > 1 ? Integer.parseInt(args[1]) : 500;
        if (count < 1000 || queries <= 0) { throw new IllegalArgumentException("Нужно >= 1000 строк и > 0 запросов"); }
        Database database = new Database();
        database.createTable("equipment", Arrays.asList(
                new Column("id", DataType.INTEGER, true),
                new Column("name", DataType.STRING, false),
                new Column("category", DataType.STRING, false),
                new Column("price", DataType.DOUBLE, false),
                new Column("available", DataType.BOOLEAN, false)));
        Table table = database.table("equipment");
        List<Row> rows = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            Map<String, Object> values = new LinkedHashMap<>();
            values.put("id", i);
            values.put("name", "Инвентарь " + i);
            values.put("category", "category_" + (i % 1000));
            values.put("price", 100.0 + (i % 500));
            values.put("available", true);
            rows.add(table.makeRow(values));
        }
        database.insert("equipment", rows);
        Condition[] conditions = new Condition[1000];
        for (int i = 0; i < conditions.length; i++) {
            conditions[i] = new Condition("category", "=", "category_" + i);
        }
        run(table, conditions, 300);
        long scanChecksumBefore = checksum;
        double scan = measure(table, conditions, queries);
        long scanChecksum = checksum - scanChecksumBefore;
        List<Row> expected = table.select(conditions[42]);
        long buildStarted = System.nanoTime();
        database.createIndex("idx_category", "equipment", "category");
        double build = (System.nanoTime() - buildStarted) / 1_000_000.0;
        if (!expected.equals(table.select(conditions[42])) || !table.wasIndexUsed()) {
            throw new AssertionError("Индекс вернул неверный результат");
        }
        run(table, conditions, 300);
        long indexChecksumBefore = checksum;
        double indexed = measure(table, conditions, queries);
        if (scanChecksum != checksum - indexChecksumBefore) {
            throw new AssertionError("Контрольные суммы SCAN и INDEX различаются");
        }
        System.out.println("Java: " + System.getProperty("java.version"));
        System.out.println("OS: " + System.getProperty("os.name") + " " + System.getProperty("os.arch"));
        System.out.println("Rows=" + count + ", queries/series=" + queries + ", series=7, categories=1000");
        System.out.printf(java.util.Locale.ROOT, "SCAN median: %.3f ms/series; %.6f ms/query%n", scan, scan / queries);
        System.out.printf(java.util.Locale.ROOT, "INDEX median: %.3f ms/series; %.6f ms/query%n", indexed, indexed / queries);
        System.out.printf(java.util.Locale.ROOT, "Speedup: %.2fx; index build: %.3f ms%n", scan / indexed, build);
        System.out.println("Checksum=" + checksum + "; equal results verified");
    }

    private static double measure(Table table, Condition[] conditions, int queries) {
        double[] times = new double[7];
        for (int i = 0; i < times.length; i++) {
            long started = System.nanoTime();
            run(table, conditions, queries);
            times[i] = (System.nanoTime() - started) / 1_000_000.0;
        }
        Arrays.sort(times);
        return times[times.length / 2];
    }

    private static void run(Table table, Condition[] conditions, int queries) {
        long result = 0;
        for (int i = 0; i < queries; i++) {
            List<Row> selected = table.select(conditions[i % conditions.length]);
            result += selected.size();
            if (!selected.isEmpty()) { result += (Integer) selected.get(0).get("id"); }
        }
        checksum += result;
    }
}
