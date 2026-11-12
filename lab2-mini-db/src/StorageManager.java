import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/** DAO файлового хранения: сериализует всю базу и индексы одним снимком. */
public class StorageManager {
    private final Path file;
    private final Path backup;

    /** Задаёт рабочую директорию; данные будут в database.dat. */
    public StorageManager(Path directory) {
        file = directory.resolve("database.dat");
        backup = directory.resolve("database.bak");
    }

    /** Загружает снимок, при повреждении основного файла пробует резервный. */
    public Database load() throws IOException {
        if (!Files.exists(file) && !Files.exists(backup)) { return new Database(); }
        try {
            return read(file);
        } catch (IOException mainError) {
            try {
                Database recovered = read(backup);
                System.err.println("Восстановлена резервная копия; последняя команда могла быть потеряна.");
                replace(backup, file, false);
                return recovered;
            } catch (IOException backupError) {
                mainError.addSuppressed(backupError);
                throw new IOException("Не удалось загрузить базу. Сохраните файлы для восстановления.", mainError);
            }
        }
    }

    /** Сохраняет синхронизированный снимок через временный файл и атомарную замену. */
    public void save(Database database) throws IOException {
        Files.createDirectories(file.getParent());
        Path temporary = file.resolveSibling("database.tmp");
        try {
            try (FileOutputStream bytes = new FileOutputStream(temporary.toFile());
                 ObjectOutputStream output = new ObjectOutputStream(bytes)) {
                output.writeObject(database);
                output.flush();
                bytes.getFD().sync();
            }
            if (Files.exists(file)) { replace(file, backup, false); }
            replace(temporary, file, true);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private Database read(Path source) throws IOException {
        try (ObjectInputStream input = new ObjectInputStream(Files.newInputStream(source))) {
            Object loaded = input.readObject();
            if (!(loaded instanceof Database)) { throw new IOException("Неверный формат базы"); }
            Database database = (Database) loaded;
            database.validate();
            return database;
        } catch (ClassNotFoundException | RuntimeException error) {
            throw new IOException("Повреждён снимок: " + source, error);
        }
    }

    private void replace(Path source, Path destination, boolean move) throws IOException {
        Path prepared = source;
        if (!move) {
            prepared = destination.resolveSibling(destination.getFileName() + ".tmp");
            Files.copy(source, prepared, StandardCopyOption.REPLACE_EXISTING);
        }
        try {
            Files.move(prepared, destination, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException error) {
            Files.move(prepared, destination, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
