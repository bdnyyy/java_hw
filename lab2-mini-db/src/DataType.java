import java.io.Serializable;
import java.time.LocalDate;

/** Пять поддерживаемых типов значений SQL. */
public enum DataType implements Serializable {
    INTEGER, DOUBLE, STRING, DATE, BOOLEAN;

    /** Преобразует SQL-литерал; NULL разрешён для любого типа. */
    public Object parse(String literal) {
        String value = literal.trim();
        if (value.equalsIgnoreCase("NULL")) {
            return null;
        }
        try {
            switch (this) {
                case INTEGER:
                    return Integer.valueOf(value);
                case DOUBLE:
                    double number = Double.parseDouble(value);
                    if (!Double.isFinite(number)) {
                        throw new IllegalArgumentException("Число должно быть конечным");
                    }
                    return number == 0.0 ? 0.0 : number;
                case BOOLEAN:
                    if (!value.equalsIgnoreCase("TRUE") && !value.equalsIgnoreCase("FALSE")) {
                        throw new IllegalArgumentException("Ожидалось TRUE или FALSE");
                    }
                    return Boolean.valueOf(value);
                case STRING:
                    return unquote(value);
                case DATE:
                    return LocalDate.parse(unquote(value));
                default:
                    throw new IllegalArgumentException("Неизвестный тип");
            }
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("Значение " + literal + " не соответствует типу " + this, e);
        }
    }

    /** Проверяет Java-тип уже преобразованного значения. */
    public boolean accepts(Object value) {
        if (value == null) {
            return true;
        }
        switch (this) {
            case INTEGER: return value instanceof Integer;
            case DOUBLE: return value instanceof Double && Double.isFinite((Double) value);
            case STRING: return value instanceof String;
            case DATE: return value instanceof LocalDate;
            case BOOLEAN: return value instanceof Boolean;
            default: return false;
        }
    }

    private static String unquote(String value) {
        if (value.length() < 2 || value.charAt(0) != '\'' || value.charAt(value.length() - 1) != '\'') {
            throw new IllegalArgumentException("Строка или дата должна быть в одинарных кавычках");
        }
        String body = value.substring(1, value.length() - 1);
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < body.length(); i++) {
            char c = body.charAt(i);
            if (c == '\'') {
                if (i + 1 >= body.length() || body.charAt(i + 1) != '\'') {
                    throw new IllegalArgumentException("Кавычка внутри строки записывается как ''");
                }
                i++;
            }
            result.append(c);
        }
        return result.toString();
    }
}
