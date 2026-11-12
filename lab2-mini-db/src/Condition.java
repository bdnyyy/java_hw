import java.util.Objects;

/** Одно типизированное условие WHERE; NULL проверяется через IS NULL / IS NOT NULL. */
public class Condition {
    private final String column;
    private final String operator;
    private final Object value;

    /** Создаёт условие сравнения. */
    public Condition(String column, String operator, Object value) {
        this.column = Column.identifier(column);
        if (!operator.matches("=|!=|<>|>=|<=|>|<|IS NULL|IS NOT NULL")) {
            throw new IllegalArgumentException("Неизвестный оператор: " + operator);
        }
        this.operator = operator;
        this.value = value;
    }

    /** Возвращает имя проверяемого столбца. */
    public String getColumn() { return column; }
    /** Возвращает искомое значение. */
    public Object getValue() { return value; }
    /** Показывает, можно ли использовать хеш-индекс. */
    public boolean isEquality() { return operator.equals("=") && value != null; }

    /** Проверяет запись. Обычное сравнение с NULL всегда ложно. */
    @SuppressWarnings({"rawtypes", "unchecked"})
    public boolean matches(Row row) {
        Object actual = row.get(column);
        if (operator.equals("IS NULL")) { return actual == null; }
        if (operator.equals("IS NOT NULL")) { return actual != null; }
        if (actual == null || value == null) { return false; }
        if (operator.equals("=")) { return Objects.equals(actual, value); }
        if (operator.equals("!=") || operator.equals("<>")) { return !Objects.equals(actual, value); }
        int result = ((Comparable) actual).compareTo(value);
        switch (operator) {
            case ">": return result > 0;
            case "<": return result < 0;
            case ">=": return result >= 0;
            case "<=": return result <= 0;
            default: throw new IllegalArgumentException("Неверное сравнение");
        }
    }
}
