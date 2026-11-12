import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Репозиторий таблиц: координирует операции и проверяет связи между таблицами. */
public class Database implements Serializable {
    private static final long serialVersionUID = 1L;
    private final Map<String, Table> tables = new LinkedHashMap<>();

    /** Возвращает таблицу по имени. */
    public Table table(String name) {
        Table table = tables.get(Column.identifier(name));
        if (table == null) { throw new IllegalArgumentException("Нет таблицы: " + name); }
        return table;
    }

    /** Возвращает таблицы только для чтения. */
    public Collection<Table> getTables() { return Collections.unmodifiableCollection(tables.values()); }

    /** Создаёт таблицу; ссылка разрешена только на PK того же типа. */
    public void createTable(String name, List<Column> columns) {
        Table created = new Table(name, columns);
        if (tables.containsKey(created.getName())) {
            throw new IllegalArgumentException("Таблица уже существует: " + name);
        }
        for (Column column : columns) {
            if (column.getReferenceTable() != null) {
                Table parent = column.getReferenceTable().equals(created.getName())
                        ? created : table(column.getReferenceTable());
                Column key = parent.column(column.getReferenceColumn());
                if (!key.isPrimaryKey() || key.getType() != column.getType()) {
                    throw new IllegalArgumentException("REFERENCES должен указывать на PK того же типа");
                }
            }
        }
        tables.put(created.getName(), created);
    }

    /** Удаляет таблицу, если на её схему не ссылается другая таблица. */
    public void dropTable(String name) {
        Table target = table(name);
        for (Table other : tables.values()) {
            if (other == target) { continue; }
            for (Column column : other.getColumns()) {
                if (target.getName().equals(column.getReferenceTable())) {
                    throw new IllegalArgumentException("На таблицу ссылается " + other.getName());
                }
            }
        }
        tables.remove(target.getName());
    }

    /** Вставляет весь пакет атомарно: ошибка одной строки отменяет пакет. */
    public int insert(String name, List<Row> added) {
        Table target = table(name);
        List<Row> candidate = new ArrayList<>(target.getRows());
        candidate.addAll(added);
        apply(target, candidate);
        return added.size();
    }

    /** Изменяет все строки, удовлетворяющие условию, с общей проверкой ключей. */
    public int update(String name, Map<String, Object> changes, Condition condition) {
        Table target = table(name);
        for (String column : changes.keySet()) { target.column(column); }
        if (condition != null) { target.column(condition.getColumn()); }
        List<Row> candidate = new ArrayList<>();
        int count = 0;
        for (Row row : target.getRows()) {
            if (condition == null || condition.matches(row)) {
                candidate.add(row.updated(changes));
                count++;
            } else {
                candidate.add(row);
            }
        }
        apply(target, candidate);
        return count;
    }

    /** Удаляет записи; зависимые внешние ключи запрещают удаление родителя. */
    public int delete(String name, Condition condition) {
        Table target = table(name);
        if (condition != null) { target.column(condition.getColumn()); }
        List<Row> candidate = new ArrayList<>();
        for (Row row : target.getRows()) {
            if (condition != null && !condition.matches(row)) { candidate.add(row); }
        }
        int count = target.getRows().size() - candidate.size();
        apply(target, candidate);
        return count;
    }

    /** Создаёт индекс с уникальным в базе именем. */
    public void createIndex(String name, String tableName, String columnName) {
        String normalized = Column.identifier(name);
        for (Table table : tables.values()) {
            if (table.getIndexes().hasName(normalized)) {
                throw new IllegalArgumentException("Имя индекса уже занято: " + name);
            }
        }
        Table target = table(tableName);
        String column = target.column(columnName).getName();
        target.getIndexes().create(normalized, column, target.getRows());
    }

    /** Проверяет PK, типы и все внешние ключи, в том числе после загрузки. */
    public void validate() {
        Map<String, Set<Object>> primaryKeys = new HashMap<>();
        for (Table table : tables.values()) {
            table.validateRows(table.getRows());
            Set<Object> keys = new HashSet<>();
            for (Row row : table.getRows()) { keys.add(row.get(table.primaryKey().getName())); }
            primaryKeys.put(table.getName(), keys);
        }
        for (Table table : tables.values()) {
            for (Column column : table.getColumns()) {
                if (column.getReferenceTable() == null) { continue; }
                Table parent = table(column.getReferenceTable());
                Column parentKey = parent.column(column.getReferenceColumn());
                if (!parentKey.isPrimaryKey() || parentKey.getType() != column.getType()) {
                    throw new IllegalArgumentException("Неверная схема внешнего ключа");
                }
                Set<Object> keys = primaryKeys.get(parent.getName());
                for (Row row : table.getRows()) {
                    Object value = row.get(column.getName());
                    if (value != null && !keys.contains(value)) {
                        throw new IllegalArgumentException("Нарушен внешний ключ " + table.getName()
                                + "." + column.getName() + ": " + value);
                    }
                }
            }
        }
    }

    private void apply(Table target, List<Row> candidate) {
        List<Row> previous = target.getRows();
        target.replaceRows(candidate);
        try {
            validate();
        } catch (RuntimeException error) {
            target.replaceRows(previous);
            throw error;
        }
        target.getIndexes().rebuild(target.getRows());
    }
}
