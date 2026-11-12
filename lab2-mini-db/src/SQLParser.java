import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Разбор ограниченного SQL: одна команда, одно условие WHERE, без JOIN и выражений. */
public class SQLParser {
    private static final String ID = "[a-zA-Z_][a-zA-Z0-9_]*";
    private final Database database;
    private boolean changed;

    /** Связывает парсер с базой данных. */
    public SQLParser(Database database) { this.database = database; }

    /** Показывает, изменила ли последняя успешная команда состояние базы. */
    public boolean wasChanged() { return changed; }

    /** Разбирает одну команду и возвращает сообщение или таблицу результатов. */
    public String execute(String sql) {
        changed = false;
        List<String> tokens = tokenize(sql);
        Cursor cursor = new Cursor(tokens);
        String command = cursor.take().toUpperCase(java.util.Locale.ROOT);
        String result;
        switch (command) {
            case "CREATE": result = create(cursor); break;
            case "DROP":
                cursor.expect("TABLE");
                String dropped = cursor.identifier();
                cursor.end();
                database.dropTable(dropped);
                result = "Таблица удалена: " + dropped;
                break;
            case "INSERT": result = insert(cursor); break;
            case "SELECT": return select(cursor);
            case "UPDATE": result = update(cursor); break;
            case "DELETE":
                cursor.expect("FROM");
                String table = cursor.identifier();
                Condition condition = where(cursor, database.table(table));
                cursor.end();
                result = "Удалено записей: " + database.delete(table, condition);
                break;
            default: throw new IllegalArgumentException("Неизвестная команда: " + command);
        }
        changed = true;
        return result;
    }

    private String create(Cursor cursor) {
        if (cursor.accept("INDEX")) {
            String name = cursor.identifier();
            cursor.expect("ON");
            String table = cursor.identifier();
            cursor.expect("(");
            String column = cursor.identifier();
            cursor.expect(")");
            cursor.end();
            database.createIndex(name, table, column);
            return "Индекс создан: " + name;
        }
        cursor.expect("TABLE");
        String table = cursor.identifier();
        cursor.expect("(");
        List<Column> columns = new ArrayList<>();
        do {
            String name = cursor.identifier();
            DataType type;
            try {
                type = DataType.valueOf(cursor.take().toUpperCase(java.util.Locale.ROOT));
            } catch (IllegalArgumentException error) {
                throw new IllegalArgumentException("Типы: INTEGER, DOUBLE, STRING, DATE, BOOLEAN");
            }
            boolean primaryKey = false;
            String parent = null;
            String parentColumn = null;
            if (cursor.accept("PRIMARY")) {
                cursor.expect("KEY");
                primaryKey = true;
            }
            if (cursor.accept("REFERENCES")) {
                parent = cursor.identifier();
                cursor.expect("(");
                parentColumn = cursor.identifier();
                cursor.expect(")");
            }
            columns.add(new Column(name, type, primaryKey, parent, parentColumn));
        } while (cursor.accept(","));
        cursor.expect(")");
        cursor.end();
        database.createTable(table, columns);
        return "Таблица создана: " + table;
    }

    private String insert(Cursor cursor) {
        cursor.expect("INTO");
        String name = cursor.identifier();
        Table table = database.table(name);
        List<String> columns = new ArrayList<>();
        if (cursor.accept("(")) {
            do { columns.add(table.column(cursor.identifier()).getName()); } while (cursor.accept(","));
            cursor.expect(")");
        } else {
            for (Column column : table.getColumns()) { columns.add(column.getName()); }
        }
        if (new HashSet<>(columns).size() != columns.size()) {
            throw new IllegalArgumentException("Повтор столбца в INSERT");
        }
        cursor.expect("VALUES");
        List<Row> rows = new ArrayList<>();
        do {
            cursor.expect("(");
            Map<String, Object> values = new LinkedHashMap<>();
            for (int i = 0; i < columns.size(); i++) {
                if (i > 0) { cursor.expect(","); }
                String column = columns.get(i);
                values.put(column, table.column(column).getType().parse(cursor.take()));
            }
            cursor.expect(")");
            rows.add(table.makeRow(values));
        } while (cursor.accept(","));
        cursor.end();
        return "Добавлено записей: " + database.insert(name, rows);
    }

    private String select(Cursor cursor) {
        List<String> columns = new ArrayList<>();
        boolean all = cursor.accept("*");
        if (!all) {
            do { columns.add(cursor.identifier()); } while (cursor.accept(","));
        }
        cursor.expect("FROM");
        Table table = database.table(cursor.identifier());
        if (all) {
            for (Column column : table.getColumns()) { columns.add(column.getName()); }
        }
        for (String column : columns) { table.column(column); }
        Condition condition = where(cursor, table);
        cursor.end();
        List<Row> rows = table.select(condition);
        return format(columns, rows) + "Записей: " + rows.size()
                + "; план: " + (table.wasIndexUsed() ? "INDEX" : "SCAN");
    }

    private String update(Cursor cursor) {
        String name = cursor.identifier();
        Table table = database.table(name);
        cursor.expect("SET");
        Map<String, Object> changes = new LinkedHashMap<>();
        do {
            String column = table.column(cursor.identifier()).getName();
            cursor.expect("=");
            if (changes.containsKey(column)) { throw new IllegalArgumentException("Повтор столбца в SET"); }
            changes.put(column, table.column(column).getType().parse(cursor.take()));
        } while (cursor.accept(","));
        Condition condition = where(cursor, table);
        cursor.end();
        return "Обновлено записей: " + database.update(name, changes, condition);
    }

    private Condition where(Cursor cursor, Table table) {
        if (!cursor.accept("WHERE")) { return null; }
        String name = table.column(cursor.identifier()).getName();
        if (cursor.accept("IS")) {
            boolean not = cursor.accept("NOT");
            cursor.expect("NULL");
            return new Condition(name, not ? "IS NOT NULL" : "IS NULL", null);
        }
        String operator = cursor.take();
        Object value = table.column(name).getType().parse(cursor.take());
        return new Condition(name, operator, value);
    }

    private static String format(List<String> columns, List<Row> rows) {
        List<List<String>> cells = new ArrayList<>();
        cells.add(columns);
        int[] widths = new int[columns.size()];
        for (Row row : rows) {
            List<String> values = new ArrayList<>();
            for (String column : columns) {
                Object value = row.get(column);
                values.add(value == null ? "NULL" : value.toString().replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t"));
            }
            cells.add(values);
        }
        for (List<String> line : cells) {
            for (int i = 0; i < widths.length; i++) { widths[i] = Math.max(widths[i], line.get(i).length()); }
        }
        StringBuilder border = new StringBuilder("+");
        for (int width : widths) {
            for (int i = 0; i < width + 2; i++) { border.append('-'); }
            border.append('+');
        }
        border.append('\n');
        StringBuilder output = new StringBuilder(border);
        for (int r = 0; r < cells.size(); r++) {
            output.append('|');
            for (int i = 0; i < widths.length; i++) {
                String value = cells.get(r).get(i);
                output.append(' ').append(value);
                for (int padding = value.length(); padding <= widths[i]; padding++) { output.append(' '); }
                output.append('|');
            }
            output.append('\n');
            if (r == 0) { output.append(border); }
        }
        return output.append(border).toString();
    }

    private static List<String> tokenize(String sql) {
        if (sql == null) { throw new IllegalArgumentException("Пустая команда"); }
        Pattern pattern = Pattern.compile("'([^']|'')*'|[a-zA-Z_][a-zA-Z0-9_]*|[+-]?(?:[0-9]+(?:\\.[0-9]*)?|\\.[0-9]+)(?:[eE][+-]?[0-9]+)?|>=|<=|<>|!=|[(),;*=<>]");
        Matcher matcher = pattern.matcher(sql);
        List<String> tokens = new ArrayList<>();
        int position = 0;
        while (matcher.find()) {
            if (!sql.substring(position, matcher.start()).trim().isEmpty()) {
                throw new IllegalArgumentException("Недопустимый фрагмент SQL около позиции " + position);
            }
            tokens.add(matcher.group());
            position = matcher.end();
        }
        if (!sql.substring(position).trim().isEmpty() || tokens.isEmpty()) {
            throw new IllegalArgumentException("Незавершённая или пустая команда");
        }
        return tokens;
    }

    private static class Cursor {
        private final List<String> tokens;
        private int position;

        Cursor(List<String> tokens) { this.tokens = tokens; }
        String take() {
            if (position >= tokens.size()) { throw new IllegalArgumentException("Неожиданный конец команды"); }
            return tokens.get(position++);
        }
        boolean accept(String expected) {
            if (position < tokens.size() && tokens.get(position).equalsIgnoreCase(expected)) {
                position++;
                return true;
            }
            return false;
        }
        void expect(String expected) {
            if (!accept(expected)) { throw new IllegalArgumentException("Ожидалось: " + expected); }
        }
        String identifier() { return Column.identifier(take()); }
        void end() {
            accept(";");
            if (position != tokens.size()) { throw new IllegalArgumentException("Лишние слова в конце команды"); }
        }
    }
}
