# Диаграмма классов

```mermaid
classDiagram
    class Main {
        +main(String[] args) void
    }
    class SQLParser {
        -Database database
        +execute(String sql) String
        +wasChanged() boolean
    }
    class Database {
        -Map tables
        +createTable(name, columns) void
        +dropTable(name) void
        +insert(name, rows) int
        +update(name, changes, condition) int
        +delete(name, condition) int
        +createIndex(name, table, column) void
        +validate() void
    }
    class Table {
        -String name
        -List columns
        -List rows
        +makeRow(values) Row
        +select(condition) List
        +validateRows(rows) void
    }
    class Column {
        -String name
        -DataType type
        -boolean primaryKey
        -String referenceTable
        -String referenceColumn
    }
    class Row {
        -Map values
        +get(column) Object
        +updated(changes) Row
    }
    class DataType {
        <<enumeration>>
        INTEGER
        DOUBLE
        STRING
        DATE
        BOOLEAN
        +parse(literal) Object
    }
    class Condition {
        -String column
        -String operator
        -Object value
        +matches(row) boolean
        +isEquality() boolean
    }
    class IndexManager {
        -Map columns
        -Map indexes
        +create(name, column, rows) void
        +rebuild(rows) void
        +find(condition) List
    }
    class StorageManager {
        -Path file
        -Path backup
        +load() Database
        +save(database) void
    }
    Main --> SQLParser
    Main --> StorageManager
    SQLParser --> Database
    SQLParser --> Condition
    Database "1" *-- "0..*" Table
    Table "1" *-- "1..*" Column
    Table "1" *-- "0..*" Row
    Table "1" *-- "1" IndexManager
    Column --> DataType
    Table --> Condition
    StorageManager --> Database : Serializable
```

`Database` выполняет роль Repository, `StorageManager` — роль DAO файлового
хранилища. Отдельные интерфейсы для единственной реализации не вводятся.
Строки перебираются через стандартные коллекции Java и их Iterator (`for-each`).

## Связь демонстрационных таблиц

```mermaid
erDiagram
    equipment ||--o{ rentals : "выдаётся в прокат"
    equipment {
        INTEGER id PK
        STRING name
        STRING category
        DOUBLE price
        BOOLEAN available
    }
    rentals {
        INTEGER id PK
        INTEGER equipment_id FK
        STRING customer
        DATE start_date
        INTEGER days
        DOUBLE total_price
        BOOLEAN returned
    }
```

Структура задаётся SQL-командами, а не зашита в Java. `equipment_id` ссылается
на `equipment.id`. Любой внешний ключ может быть NULL; если значение задано,
родительская запись обязана существовать. Первичный ключ всегда непустой.
