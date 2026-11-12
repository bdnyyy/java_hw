import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Схема и записи таблицы; перебор записей основан на стандартном Iterable Java. */
public class Table implements Serializable {
    private static final long serialVersionUID = 1L;
    private final String name;
    private final List<Column> columns;
    private List<Row> rows = new ArrayList<>();
    private final IndexManager indexes = new IndexManager();
    private transient boolean lastSelectUsedIndex;

    /** Создаёт схему с ровно одним первичным ключом. */
    public Table(String name, List<Column> columns) {
        this.name = Column.identifier(name);
        this.columns = new ArrayList<>(columns);
        Set<String> names = new HashSet<>();
        int keys = 0;
        for (Column column : columns) {
            if (!names.add(column.getName())) {
                throw new IllegalArgumentException("Повтор столбца: " + column.getName());
            }
            if (column.isPrimaryKey()) { keys++; }
        }
        if (keys != 1) { throw new IllegalArgumentException("Нужен ровно один PRIMARY KEY"); }
    }

    /** Возвращает имя таблицы. */
    public String getName() { return name; }
    /** Возвращает схему только для чтения. */
    public List<Column> getColumns() { return Collections.unmodifiableList(columns); }
    /** Возвращает строки только для чтения. */
    public List<Row> getRows() { return Collections.unmodifiableList(rows); }
    /** Возвращает менеджер индексов таблицы. */
    public IndexManager getIndexes() { return indexes; }
    /** Показывает, использовался ли индекс последней выборкой. */
    public boolean wasIndexUsed() { return lastSelectUsedIndex; }

    /** Находит столбец или сообщает об ошибке. */
    public Column column(String name) {
        String normalized = Column.identifier(name);
        for (Column column : columns) {
            if (column.getName().equals(normalized)) { return column; }
        }
        throw new IllegalArgumentException("Нет столбца: " + name);
    }

    /** Возвращает столбец первичного ключа. */
    public Column primaryKey() {
        for (Column column : columns) {
            if (column.isPrimaryKey()) { return column; }
        }
        throw new IllegalStateException("Нет первичного ключа");
    }

    /** Создаёт запись, заполняя пропущенные поля значением NULL. */
    public Row makeRow(Map<String, Object> supplied) {
        for (String key : supplied.keySet()) { column(key); }
        Map<String, Object> values = new LinkedHashMap<>();
        for (Column column : columns) {
            Object value = supplied.get(column.getName());
            if (!column.getType().accepts(value)) {
                throw new IllegalArgumentException("Неверный тип: " + column.getName());
            }
            values.put(column.getName(), value);
        }
        return new Row(values);
    }

    /** Проверяет типы и уникальность непустого PK для списка записей. */
    public void validateRows(List<Row> candidate) {
        Set<Object> keys = new HashSet<>();
        for (Row row : candidate) {
            for (Column column : columns) {
                if (!column.getType().accepts(row.get(column.getName()))) {
                    throw new IllegalArgumentException("Неверный тип: " + column.getName());
                }
            }
            Object key = row.get(primaryKey().getName());
            if (key == null || !keys.add(key)) {
                throw new IllegalArgumentException("PRIMARY KEY пустой или повторяется: " + key);
            }
        }
    }

    /** Заменяет строки после проверки ограничений на уровне Database. */
    void replaceRows(List<Row> candidate) { rows = new ArrayList<>(candidate); }

    /** Выбирает строки, используя индекс при условии равенства. */
    public List<Row> select(Condition condition) {
        if (condition != null) { column(condition.getColumn()); }
        List<Row> source = indexes.find(condition);
        lastSelectUsedIndex = source != null;
        if (source == null) { source = rows; }
        List<Row> result = new ArrayList<>();
        for (Row row : source) {
            if (condition == null || condition.matches(row)) { result.add(row); }
        }
        return result;
    }
}
