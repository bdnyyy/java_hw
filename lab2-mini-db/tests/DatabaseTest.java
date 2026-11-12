import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/** Самостоятельные функциональные тесты без JUnit; ошибка завершает процесс с кодом 1. */
public class DatabaseTest {
    private static int checks;

    /** Запускает проверки SQL, ограничений, индексов и файлового хранения. */
    public static void main(String[] args) throws Exception {
        Database database = new Database();
        SQLParser sql = new SQLParser(database);
        sql.execute("CREATE TABLE equipment (id INTEGER PRIMARY KEY, name STRING, category STRING, price DOUBLE, available BOOLEAN);");
        sql.execute("CREATE TABLE rentals (id INTEGER PRIMARY KEY, equipment_id INTEGER REFERENCES equipment(id), customer STRING, start_date DATE, days INTEGER, total_price DOUBLE, returned BOOLEAN);");
        check(database.getTables().size() == 2, "Две связанные таблицы");
        sql.execute("INSERT INTO equipment VALUES (1, 'Лыжи', 'Зима', 1200.5, TRUE), (2, 'Мяч', 'Лето', 300, FALSE), (3, 'Велосипед', 'Лето', 1500, TRUE);");
        check(database.table("equipment").getRows().size() == 3, "Пакетная вставка");
        sql.execute("INSERT INTO rentals VALUES (1, 1, 'О''Нил, Игорь; (VIP)', '2026-11-12', 2, 2401.0, FALSE);");
        Row rental = database.table("rentals").getRows().get(0);
        check(rental.get("start_date").equals(java.time.LocalDate.of(2026, 11, 12)), "Тип DATE");
        check(rental.get("customer").equals("О'Нил, Игорь; (VIP)"), "Кавычки и разделители в строке");
        check(rental.get("returned").equals(false), "Тип BOOLEAN");
        check(rental.get("days").equals(2), "Тип INTEGER");
        check(rental.get("total_price").equals(2401.0), "Тип DOUBLE");
        check(sql.execute("SELECT name, price FROM equipment WHERE price > 1000;").contains("Записей: 2"), "SELECT и проекция");
        check(sql.execute("SELECT id FROM equipment WHERE price >= 1200.5;").contains("Записей: 2"), "Оператор >=");
        check(sql.execute("SELECT id FROM equipment WHERE price < 301;").contains("Записей: 1"), "Оператор <");
        check(sql.execute("SELECT id FROM equipment WHERE price <= 300;").contains("Записей: 1"), "Оператор <=");
        check(sql.execute("SELECT id FROM equipment WHERE id != 1;").contains("Записей: 2"), "Оператор !=");
        check(sql.execute("SELECT id FROM equipment WHERE id <> 1;").contains("Записей: 2"), "Оператор <>");
        check(sql.execute("SELECT id FROM equipment WHERE available = TRUE;").contains("Записей: 2"), "Условие BOOLEAN");
        check(sql.execute("SELECT id FROM rentals WHERE start_date > '2026-11-01';").contains("Записей: 1"), "Сравнение дат");
        fails(sql, "INSERT INTO equipment VALUES (1, 'Дубль', 'Зима', 10, TRUE);", "Повтор PK");
        fails(sql, "INSERT INTO equipment (id) VALUES (4), (2);", "Атомарность INSERT");
        check(database.table("equipment").getRows().size() == 3, "Откат всего пакета INSERT");
        fails(sql, "INSERT INTO rentals (id, equipment_id) VALUES (2, 99);", "Несуществующий FK");
        fails(sql, "DELETE FROM equipment WHERE id = 1;", "Защита родителя от DELETE");
        fails(sql, "UPDATE equipment SET id = 8 WHERE id = 1;", "Защита родителя от UPDATE PK");
        fails(sql, "UPDATE equipment SET id = 5;", "Повтор ключа при массовом UPDATE");
        fails(sql, "UPDATE rentals SET equipment_id = 99;", "Откат неверного FK при UPDATE");
        check(database.table("rentals").getRows().get(0).get("equipment_id").equals(1), "Сохранение строки после ошибки UPDATE");
        fails(sql, "DROP TABLE equipment;", "Запрет DROP при ссылке в схеме");
        fails(sql, "CREATE TABLE bad (id INTEGER PRIMARY KEY, x STRING REFERENCES equipment(id));", "Тип FK");
        fails(sql, "CREATE TABLE bad (id INTEGER PRIMARY KEY, x DOUBLE REFERENCES equipment(price));", "Ссылка только на PK");
        fails(sql, "CREATE TABLE bad (id INTEGER);", "Обязательный PK");
        fails(sql, "CREATE TABLE bad (id INTEGER PRIMARY KEY, x INTEGER PRIMARY KEY);", "Один PK");
        fails(sql, "CREATE TABLE bad (id INTEGER PRIMARY KEY, id STRING);", "Уникальность имён столбцов");
        check(database.getTables().size() == 2, "Ошибка CREATE не оставляет таблицу");
        sql.execute("INSERT INTO equipment (id, name) VALUES (4, 'Ракетка');");
        check(sql.execute("SELECT * FROM equipment WHERE price IS NULL;").contains("Записей: 1"), "Пропуски становятся NULL");
        check(sql.execute("SELECT id FROM equipment WHERE price = NULL;").contains("Записей: 0"), "SQL-сравнение с NULL");
        check(sql.execute("SELECT id FROM equipment WHERE price IS NOT NULL;").contains("Записей: 3"), "IS NOT NULL");
        fails(sql, "INSERT INTO equipment (name) VALUES ('Нет ключа');", "NULL в PK запрещён");
        List<String> invalid = Arrays.asList(
                "INSERT INTO equipment (id) VALUES ('5');",
                "INSERT INTO equipment (id, price) VALUES (5, 1e999);",
                "INSERT INTO equipment (id, available) VALUES (5, 1);",
                "INSERT INTO rentals (id, start_date) VALUES (2, '2026-02-30');",
                "INSERT INTO equipment (id) VALUES (2147483648);",
                "INSERT INTO equipment (id, id) VALUES (5, 6);",
                "INSERT INTO equipment (unknown) VALUES (1);",
                "SELECT unknown FROM equipment;",
                "SELECT * FROM equipment WHERE unknown = 1;",
                "UPDATE equipment SET price = 1, price = 2;",
                "UPDATE equipment SET price = 'bad';",
                "DELETE FROM equipment WHERE unknown = 1;",
                "INSERT INTO equipment (id) VALUES (5); SELECT * FROM equipment;",
                "INSERT INTO equipment (id) VALUES (5) garbage;",
                "SELECT * FROM equipment WHERE id = 1 OR id = 2;",
                "SELECT * FROM equipment WHERE id == 1;",
                "SELECT * FROM equipment WHERE name = 'незакрытая;",
                "SELECT * FROM equipment #;",
                "", "VACUUM;");
        for (String query : invalid) { fails(sql, query, "Ошибка SQL: " + query); }
        check(database.table("equipment").getRows().size() == 4, "Ошибки SQL не изменяют таблицу");
        Condition summer = new Condition("category", "=", "Лето");
        List<Row> scan = database.table("equipment").select(summer);
        check(!database.table("equipment").wasIndexUsed(), "Полный перебор до CREATE INDEX");
        sql.execute("CREATE INDEX idx_category ON equipment (category);");
        check(scan.equals(database.table("equipment").select(summer)), "Равенство результатов SCAN и INDEX");
        check(database.table("equipment").wasIndexUsed(), "Реальное использование индекса");
        check(sql.execute("SELECT * FROM equipment WHERE category = 'Нет';").contains("Записей: 0"), "Отсутствующий ключ индекса");
        check(sql.execute("SELECT * FROM equipment WHERE category > 'А';").contains("план: SCAN"), "Диапазон использует SCAN");
        fails(sql, "CREATE INDEX idx_category ON rentals (customer);", "Глобальная уникальность имени индекса");
        fails(sql, "CREATE INDEX another ON equipment (category);", "Повторный индекс столбца");
        sql.execute("UPDATE equipment SET category = 'Лето', price = 450 WHERE id = 4;");
        check(database.table("equipment").select(summer).size() == 3, "Индекс после UPDATE");
        sql.execute("INSERT INTO equipment VALUES (5, 'Самокат', 'Лето', 600, TRUE);");
        check(database.table("equipment").select(summer).size() == 4, "Индекс после INSERT");
        sql.execute("DELETE FROM equipment WHERE id = 5;");
        check(database.table("equipment").select(summer).size() == 3, "Индекс после DELETE");
        fails(sql, "UPDATE equipment SET id = 1 WHERE category = 'Лето';", "Откат с построенным индексом");
        check(database.table("equipment").select(summer).size() == 3, "Индекс после отката");
        sql.execute("UPDATE equipment SET available = TRUE WHERE category = 'Лето';");
        check(sql.execute("SELECT id FROM equipment WHERE available = TRUE;").contains("Записей: 4"), "Массовый UPDATE");
        sql.execute("CREATE TABLE measurements (id INTEGER PRIMARY KEY, value DOUBLE);");
        sql.execute("INSERT INTO measurements VALUES (1, -0.0), (2, 0.0), (3, -1e2);");
        sql.execute("CREATE INDEX idx_value ON measurements (value);");
        check(sql.execute("SELECT * FROM measurements WHERE value = 0;").contains("Записей: 2"), "Нормализация -0.0 для индекса");
        sql.execute("CREATE TABLE nodes (id INTEGER PRIMARY KEY, parent INTEGER REFERENCES nodes(id));");
        sql.execute("INSERT INTO nodes VALUES (1, NULL), (2, 1);");
        fails(sql, "DELETE FROM nodes WHERE id = 1;", "Самоссылка: запрет удаления родителя");
        sql.execute("DELETE FROM nodes;");
        check(database.table("nodes").getRows().isEmpty(), "Удаление всей самосвязанной группы");
        sql.execute("DROP TABLE nodes;");
        testStorage(database, summer);
        sql.execute("DELETE FROM rentals;");
        sql.execute("DELETE FROM equipment WHERE id = 1;");
        check(database.table("equipment").getRows().size() == 3, "Удаление родителя после дочерних строк");
        sql.execute("DROP TABLE rentals;");
        sql.execute("DROP TABLE equipment;");
        fails(sql, "SELECT * FROM equipment;", "DROP TABLE выполнен");
        System.out.println("PASS: " + checks + " проверок");
    }

    private static void testStorage(Database database, Condition condition) throws Exception {
        Path directory = Files.createTempDirectory("minidb-test-");
        try {
            StorageManager storage = new StorageManager(directory);
            check(storage.load().getTables().isEmpty(), "Первый запуск без файлов");
            storage.save(database);
            Database loaded = storage.load();
            check(loaded.table("equipment").select(condition).size() == 3, "Данные после загрузки");
            check(loaded.table("equipment").wasIndexUsed(), "Индекс восстановлен из снимка");
            fails(new SQLParser(loaded), "INSERT INTO rentals (id, equipment_id) VALUES (9, 99);", "FK после загрузки");
            new SQLParser(database).execute("UPDATE equipment SET name = 'Новое имя' WHERE id = 2;");
            storage.save(database);
            check(Files.exists(directory.resolve("database.bak")), "Резервная копия создана");
            Files.write(directory.resolve("database.tmp"), new byte[] {1, 2});
            check(storage.load().table("equipment").getRows().get(1).get("name").equals("Новое имя"), "Незавершённый временный снимок игнорируется");
            Files.write(directory.resolve("database.dat"), "broken".getBytes(StandardCharsets.UTF_8));
            loaded = storage.load();
            check(loaded.table("equipment").getRows().get(1).get("name").equals("Мяч"), "Восстановлена предыдущая версия");
            check(loaded.table("equipment").select(condition).size() == 3 && loaded.table("equipment").wasIndexUsed(), "Индекс из резервной копии");
            check(storage.load().table("equipment").getRows().get(1).get("name").equals("Мяч"), "Основной файл восстановлен");
            Files.delete(directory.resolve("database.dat"));
            check(storage.load().getTables().size() == database.getTables().size(), "Восстановление при отсутствии основного файла");
            Files.write(directory.resolve("database.dat"), new byte[] {0});
            Files.write(directory.resolve("database.bak"), new byte[] {0});
            boolean rejected = false;
            try { storage.load(); } catch (java.io.IOException expected) { rejected = true; }
            check(rejected, "Повреждение обоих файлов не создаёт пустую базу");
        } finally {
            try (Stream<Path> paths = Files.walk(directory)) {
                for (Path path : (Iterable<Path>) paths.sorted(Comparator.reverseOrder())::iterator) {
                    Files.deleteIfExists(path);
                }
            }
        }
    }

    private static void fails(SQLParser sql, String query, String message) {
        boolean rejected = false;
        try { sql.execute(query); } catch (IllegalArgumentException expected) { rejected = true; }
        check(rejected, message);
        check(!sql.wasChanged(), "Неуспешная команда не помечена как изменение");
    }

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) { throw new AssertionError(message); }
    }
}
