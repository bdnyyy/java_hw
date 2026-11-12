import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Хеш-индексы одной таблицы; сами структуры сериализуются вместе с данными. */
public class IndexManager implements Serializable {
    private static final long serialVersionUID = 1L;
    private final Map<String, String> columns = new LinkedHashMap<>();
    private final Map<String, Map<Object, List<Row>>> indexes = new LinkedHashMap<>();

    /** Добавляет описание индекса и строит его по текущим строкам. */
    public void create(String name, String column, List<Row> rows) {
        if (columns.containsKey(name) || columns.containsValue(column)) {
            throw new IllegalArgumentException("Индекс с таким именем или столбцом уже существует");
        }
        columns.put(name, column);
        rebuild(rows);
    }

    /** Перестраивает индексы после успешного изменения таблицы. */
    public void rebuild(List<Row> rows) {
        indexes.clear();
        for (Map.Entry<String, String> entry : columns.entrySet()) {
            Map<Object, List<Row>> index = new LinkedHashMap<>();
            for (Row row : rows) {
                index.computeIfAbsent(row.get(entry.getValue()), key -> new ArrayList<>()).add(row);
            }
            indexes.put(entry.getKey(), index);
        }
    }

    /** Возвращает кандидатов из индекса либо null, если подходящего индекса нет. */
    public List<Row> find(Condition condition) {
        if (condition != null && condition.isEquality()) {
            for (Map.Entry<String, String> entry : columns.entrySet()) {
                if (entry.getValue().equals(condition.getColumn())) {
                    return indexes.get(entry.getKey()).getOrDefault(condition.getValue(), Collections.emptyList());
                }
            }
        }
        return null;
    }

    /** Проверяет занятость имени индекса. */
    public boolean hasName(String name) { return columns.containsKey(name); }
}
