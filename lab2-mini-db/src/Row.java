import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;

/** Запись таблицы; изменения выполняются созданием новой записи. */
public class Row implements Serializable {
    private static final long serialVersionUID = 1L;
    private final Map<String, Object> values;

    /** Копирует значения в порядке столбцов. */
    public Row(Map<String, Object> values) {
        this.values = new LinkedHashMap<>(values);
    }

    /** Возвращает значение столбца. */
    public Object get(String column) { return values.get(column); }

    /** Возвращает новую запись с указанными изменениями. */
    public Row updated(Map<String, Object> changes) {
        Map<String, Object> result = new LinkedHashMap<>(values);
        result.putAll(changes);
        return new Row(result);
    }
}
