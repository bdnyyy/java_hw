# Диаграммы последовательностей

## SELECT по индексированному полю

```mermaid
sequenceDiagram
    actor User as Пользователь
    participant Main
    participant Parser as SQLParser
    participant DB as Database
    participant Table
    participant Index as IndexManager
    participant Cond as Condition
    User->>Main: SELECT name FROM equipment WHERE category = 'Зима';
    Main->>Parser: execute(sql)
    Parser->>DB: table("equipment")
    DB-->>Parser: Table
    Parser->>Parser: Проверка полей и разбор WHERE
    Parser->>Table: select(condition)
    Table->>Index: find(condition)
    alt Есть индекс и оператор =
        Index-->>Table: Список кандидатов из HashMap
    else Индекса нет или сравнение диапазонное
        Index-->>Table: null
        Table->>Table: Перебор всех строк
    end
    loop Для каждого кандидата
        Table->>Cond: matches(row)
        Cond-->>Table: true / false
    end
    Table-->>Parser: Найденные строки
    Parser->>Parser: Проекция полей и табличный вывод
    Parser-->>Main: Таблица и план INDEX / SCAN
    Main-->>User: Результат
```

## Изменение и сохранение

```mermaid
sequenceDiagram
    actor User as Пользователь
    participant Main
    participant Parser as SQLParser
    participant DB as Database
    participant Table
    participant Index as IndexManager
    participant Storage as StorageManager
    participant Disk as Файлы
    User->>Main: INSERT INTO rentals VALUES (...);
    Main->>Parser: execute(sql)
    Parser->>Parser: Разбор всей команды и преобразование типов
    Parser->>DB: insert(table, rows)
    DB->>Table: Временный список строк
    DB->>DB: validate(): типы, PK, FK всей базы
    alt Ограничение нарушено
        DB->>Table: Возврат прежнего списка строк
        DB-->>Main: Исключение через SQLParser
        Main-->>User: Сообщение об ошибке
    else Проверка успешна
        DB->>Index: rebuild(rows)
        DB-->>Parser: Количество записей
        Parser-->>Main: Результат и wasChanged = true
        Main->>Storage: save(database)
        Storage->>Disk: database.tmp, flush, sync
        Storage->>Disk: Предыдущий снимок в database.bak
        Storage->>Disk: Атомарная замена database.dat
        alt Сохранение успешно
            Storage-->>Main: Успех
            Main-->>User: Подтверждение команды
        else Ошибка ввода-вывода
            Storage-->>Main: IOException
            Main->>Storage: load() последнего снимка
            Storage-->>Main: Предыдущее сохранённое состояние
            Main-->>User: Ошибка сохранения
        end
    end
```

При старте `Main` вызывает `StorageManager.load()`. Снимок содержит таблицы,
строки, описание индексов и хеш-структуры со ссылками на те же строки.
Отдельного построения индексов при загрузке не требуется. Повреждённый основной
снимок заменяется пригодной резервной копией с предупреждением в консоли.
