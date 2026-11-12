import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;

/** Консольная точка входа: одна SQL-команда в строке, EXIT для завершения. */
public class Main {
    /** Запускает СУБД; первый необязательный аргумент задаёт директорию данных. */
    public static void main(String[] args) {
        Path directory = Paths.get(args.length == 0 ? "data" : args[0]);
        StorageManager storage = new StorageManager(directory);
        try {
            Database database = storage.load();
            SQLParser parser = new SQLParser(database);
            BufferedReader input = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
            System.out.println("MiniDB — прокат спортивного инвентаря");
            System.out.println("Одна SQL-команда в строке. HELP — справка, EXIT — выход.");
            while (true) {
                System.out.print("sql> ");
                String line = input.readLine();
                if (line == null || line.trim().equalsIgnoreCase("EXIT")) { break; }
                if (line.trim().isEmpty()) { continue; }
                if (line.trim().equalsIgnoreCase("HELP")) {
                    System.out.println("CREATE TABLE, DROP TABLE, INSERT INTO, SELECT, UPDATE, DELETE, CREATE INDEX");
                    System.out.println("Типы: INTEGER, DOUBLE, STRING, DATE, BOOLEAN. Примеры — в README.md.");
                    continue;
                }
                try {
                    String result = parser.execute(line);
                    if (parser.wasChanged()) {
                        try {
                            storage.save(database);
                        } catch (IOException error) {
                            System.err.println("Сохранение не выполнено: " + error.getMessage());
                            database = storage.load();
                            parser = new SQLParser(database);
                            System.err.println("Восстановлено последнее сохранённое состояние.");
                            continue;
                        }
                    }
                    System.out.println(result);
                } catch (IllegalArgumentException error) {
                    System.out.println("Ошибка: " + error.getMessage());
                }
            }
        } catch (IOException error) {
            System.err.println("Работа остановлена: " + error.getMessage());
            System.exit(1);
        }
    }
}
