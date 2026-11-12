import java.io.Serializable;
import java.util.Locale;

/** Описание столбца, первичного ключа и необязательной ссылки на другую таблицу. */
public class Column implements Serializable {
    private static final long serialVersionUID = 1L;
    private final String name;
    private final DataType type;
    private final boolean primaryKey;
    private final String referenceTable;
    private final String referenceColumn;

    /** Создаёт обычный столбец или первичный ключ. */
    public Column(String name, DataType type, boolean primaryKey) {
        this(name, type, primaryKey, null, null);
    }

    /** Создаёт столбец с необязательным внешним ключом. */
    public Column(String name, DataType type, boolean primaryKey, String table, String column) {
        this.name = identifier(name);
        if (type == null || (table == null) != (column == null)) {
            throw new IllegalArgumentException("Неверное описание столбца");
        }
        this.type = type;
        this.primaryKey = primaryKey;
        this.referenceTable = table == null ? null : identifier(table);
        this.referenceColumn = column == null ? null : identifier(column);
    }

    /** Проверяет имя и приводит его к нижнему регистру. */
    public static String identifier(String name) {
        if (name == null || !name.matches("[a-zA-Z_][a-zA-Z0-9_]*")) {
            throw new IllegalArgumentException("Недопустимое имя: " + name);
        }
        return name.toLowerCase(Locale.ROOT);
    }

    /** Возвращает имя столбца. */
    public String getName() { return name; }
    /** Возвращает тип данных. */
    public DataType getType() { return type; }
    /** Показывает, является ли столбец первичным ключом. */
    public boolean isPrimaryKey() { return primaryKey; }
    /** Возвращает имя родительской таблицы или null. */
    public String getReferenceTable() { return referenceTable; }
    /** Возвращает имя родительского ключа или null. */
    public String getReferenceColumn() { return referenceColumn; }
}
