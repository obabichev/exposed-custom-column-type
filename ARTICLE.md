### Introduction

Exposed is an SQL library for Kotlin with DSL and DAO APIs for database interactions. While it comes with support for standard SQL data types, you can extend its functionality by creating custom column types.

Custom column types are useful when Exposed lacks support for specific database types (like PostgreSQL's `enum`, `inet` or `ltree`) or when you want to map columns to domain-specific types that better align with your business logic. By implementing custom columns, you gain control over data storage and retrieval while maintaining type safety.

In this article, we'll explore how to create custom column types in Exposed by creating a simple column type for PostgreSQL's `enum`.

### Working with PostgreSQL Enums via JDBC

Let's understand how PostgreSQL enums work at the JDBC level. PostgreSQL supports [enumerated types](https://www.postgresql.org/docs/current/datatype-enum.html). For this article, we'll use the example from the PostgreSQL documentation and create an enum type with the following SQL:

```sql
CREATE TYPE mood AS ENUM ('sad', 'ok', 'happy');
```

Exposed is built on top of JDBC, which in turn works over SQL. To understand how to implement custom column types in Exposed, it's helpful to first see how JDBC handles these values directly. Let's create a simple table that uses our `mood` enum:

```sql
CREATE TABLE person (
    name TEXT,
    mood mood
);
```

I will create JDBC connection using driver directly:

```kotlin
DriverManager.getConnection(url, user, password).use { connection ->
    // Database operations go here
}
```

To insert a new entry with an enum value, we should create a statement and set the parameters:

```kotlin
connection.prepareStatement("INSERT INTO person (name, mood) VALUES (?, ?)").use { insertStmt ->
    insertStmt.setString(1, "John")
    
    val mood = PGobject().also {
        it.type = "mood"
        it.value = "happy"
    }
    
    insertStmt.setObject(2, mood)
    insertStmt.executeUpdate()
}
```

There are at least two ways to set an enum parameter: using a `PGobject` (as shown above) or by specifying `java.sql.Types.OTHER` as the third argument to `setObject()`:

```kotlin
insertStmt.setObject(2, "happy", java.sql.Types.OTHER)
```

Reading the enum value back from the database is straightforward:

```kotlin
connection.prepareStatement("SELECT name, mood FROM person WHERE name = ?").use { selectStmt ->
    selectStmt.setString(1, "John")
    selectStmt.executeQuery().use { rs ->
        assert(rs.next())
        assertEquals("John", rs.getString("name"))
        assertEquals("happy", rs.getObject("mood"))
    }
}
```

Now that we understand how JDBC handles PostgreSQL enums, we can build a custom Exposed column type that provides the same functionality with type safety and convenience.

### Creating a Table with a New Column Type

Now that we understand how PostgreSQL enums work at the JDBC level, let's implement a custom column type in Exposed. We'll create a type-safe wrapper for our `mood` enum using this Kotlin enum class:

```kotlin
enum class Mood {
    SAD,
    OK,
    HAPPY
}
```


Custom column types in Exposed are created by extending the `ColumnType<T>` class, where `T` represents the Kotlin type we want to work with. Since we want our implementation to be reusable for any enum type, we'll create a generic class:

```kotlin
class PostgresEnumerationColumnType<T : Enum<T>>() : ColumnType<T>() {
    override fun sqlType(): String {
        TODO("Not yet implemented")
    }
    
    override fun valueFromDB(value: Any): T? {
        TODO("Not yet implemented")
    }
}
```

The `ColumnType` class requires us to implement two essential methods:

- **`sqlType()`** - Returns the SQL type name used in DDL statements (like `integer`, `text`, `boolean`, or in our case, the enum type name)
- **`valueFromDB()`** - Converts database values into Kotlin objects

For our PostgreSQL enum column type, the SQL type is the name of the enum we defined in the database. Since we want this class to work with any enum, we'll pass both the type name and the enum class as constructor parameters:

```kotlin
class PostgresEnumerationColumnType<T : Enum<T>>(
    val typeName: String,
    val enumClass: Class<T>
) : ColumnType<T>() {
    override fun sqlType() = typeName
    
    override fun valueFromDB(value: Any): T? {
        TODO("Not yet implemented")
    }
}
```

Exposed's way is defining extension functions on the `Table` class to register new columns. Let's create both a generic function for any enum and a convenience function specifically for our `Mood` type:

```kotlin
fun <T : Enum<T>> Table.pgEnum(name: String, typeName: String, enumClass: Class<T>): Column<T> =
    registerColumn(name, PostgresEnumerationColumnType(typeName, enumClass))

fun Table.mood(name: String): Column<Mood> = pgEnum(name, "mood", Mood::class.java)
```

Now we can now define our table:

```kotlin
object PersonTable : Table("person") {
    val name = text("name")
    val mood = mood("mood")
}
```

When we execute `SchemaUtils.create(PersonTable)`, Exposed generates the following SQL:

```sql
CREATE TABLE IF NOT EXISTS person ("name" TEXT NOT NULL, mood mood NOT NULL)
```

The `mood` type in the generated SQL comes directly from our `sqlType()` method.

### Inserting and reading Enum values

With our table definition in place, let's try to insert and retrieve data. Consider the following code:

```kotlin
PersonTable.insert {
    it[name] = "John"
    it[mood] = Mood.SAD
}
```

Running this code produces an error: `Transaction attempt #0 failed: Can't infer the SQL type to use for an instance of com.obabichev.Mood. Use setObject() with an explicit Types value to specify the type to use`.

Remember that Exposed operates on top of JDBC. When executing queries, Exposed uses parameterized statements and needs to bind our Kotlin values to JDBC statement parameters. By default, JDBC doesn't understand custom enum types, so we need to teach our column type how to convert enum values into a format JDBC can handle.

Exposed provides two methods for this conversion:

- **`valueToDB()`** (and its sibling `notNullValueToDB()`) - Converts Kotlin values into database-compatible objects
- **`setParameter()`** - Sets the converted value on the JDBC statement, providing access to methods like `setNull()`, `setArray()`, and `setObject()`

These methods work together in the flow: `columnType.setParameter(statement, index + 1, columnType.valueToDB(value))`.

As we saw in the JDBC section, PostgreSQL enums can be passed to statements either as a `PGobject` or using `java.sql.Types.OTHER`. Currently, as far as I know, the second variant is not supported by Exposed, so we will focus on `PGobject` approach.

Before implementing parameter binding, let's add helper methods for converting between enum values and strings. `PGobject` requires string values, and database results will also be returned as strings:

```kotlin
class PostgresEnumerationColumnType<T : Enum<T>>(
    val typeName: String,
    val enumClass: Class<T>
) : ColumnType<T>() {
    
    private val stringToEnumeration = enumClass.enumConstants.associateBy { it.name.lowercase() }
    
    private val enumerationToString = stringToEnumeration.map { (k, v) -> v to k }.toMap()
    
    private fun toEnumeration(name: String): T = stringToEnumeration[name.lowercase()]!!
    
    private fun fromEnumeration(value: T): String = enumerationToString[value]!!
    
    override fun sqlType() = typeName
    
    // ... rest of the implementation
}
```

#### Implementing Value Conversion

Now we can implement `notNullValueToDB()` to return a properly configured `PGobject`:

```kotlin
override fun notNullValueToDB(value: T): Any {
    return fromEnumeration(value).lowercase().let { enumValue ->
        PGobject().also {
            it.type = sqlType()
            it.value = enumValue
        }
    }
}
```

Alternatively, we can split the logic between `notNullValueToDB()` and `setParameter()` that technically will lead to the same result:

```kotlin
override fun notNullValueToDB(value: T): Any {
    return fromEnumeration(value).lowercase()
}

override fun setParameter(
    stmt: PreparedStatementApi,
    index: Int,
    value: Any?
) {
    if (value == null) {
        stmt.setNull(index, this)
    } else {
        PGobject().also {
            it.type = sqlType()
            // We can safely cast to String since we returned it from notNullValueToDB()
            it.value = value as String
        }.let {
            stmt.set(index, it, this)
        }
    }
}
```

Now that we can insert data, let's try reading it back:

```kotlin
val person = PersonTable.selectAll().first()

assertEquals("John", person[PersonTable.name])
assertEquals(Mood.SAD, person[PersonTable.mood])
```

This throws another error: `An operation is not implemented: Not yet implemented`. We've hit the second mandatory method we need to implement: `valueFromDB()`.

The type of value received in this method depends entirely on the database driver. We can discover what type is returned either by examining raw JDBC code or by adding debug output to our unimplemented method. For PostgreSQL enums, the driver returns a `String` value.

Since we already have a helper method to convert strings to enum values, the implementation is straightforward:

```kotlin
override fun valueFromDB(value: Any): T? {
    return when (value) {
        is String -> toEnumeration(value.uppercase())
        else -> error("Unexpected value $value of type ${value::class.qualifiedName}")
    }
}
```

With this final piece in place, we now have a fully functional custom column type that can insert and retrieve PostgreSQL enum values.

### Handling Column default values

Now that we have a working custom column type for PostgreSQL enums, let's explore some additional features. One important capability is defining default values for columns.

In Exposed, you can specify a default value using the `default()` modifier:

```kotlin
object PersonTable : Table("person") {
    val name = text("name")
    val mood = mood("mood").default(Mood.OK)
}
```

However, if we try to create this table with `SchemaUtils.create(PersonTable)`, we encounter an error:

```
CREATE TABLE IF NOT EXISTS person ("name" TEXT NOT NULL, mood mood DEFAULT ok NOT NULL)
org.postgresql.util.PSQLException: ERROR: cannot use column reference in DEFAULT expression
```

The problem is visible in the generated SQL—the default value `ok` appears without quotes, making PostgreSQL interpret it as a column reference rather than a literal enum value.

#### Implementing Default Value Formatting

The `ColumnType` class provides methods specifically for formatting default values in DDL statements: `valueAsDefaultString()` and its sibling `nonNullValueAsDefaultString()`. Let's implement the latter to format our enum default values:

```kotlin
override fun nonNullValueAsDefaultString(value: T) =
    fromEnumeration(value).lowercase().let { "'$it'::${sqlType()}" }
```

With this implementation, the generated SQL becomes:

```sql
CREATE TABLE IF NOT EXISTS person ("name" TEXT NOT NULL, mood mood DEFAULT 'ok'::mood NOT NULL)
```

This follows PostgreSQL's syntax for enum literals with an explicit type cast. However, the cast is actually optional for default values in this context. A simpler version works just as well:

```kotlin
override fun nonNullValueAsDefaultString(value: T) =
    fromEnumeration(value).lowercase().let { "'$it'" }
```

This generates:

```sql
CREATE TABLE IF NOT EXISTS person ("name" TEXT NOT NULL, mood mood DEFAULT 'ok' NOT NULL)
```

Both versions are valid PostgreSQL syntax and will work correctly. The explicit cast version is more verbose but makes the type relationship clearer, while the simpler version is more concise and relies on PostgreSQL's type inference.

### Creating String Literals

Sometimes you may want to inline values directly into SQL statements rather than using parameterized queries. Exposed provides literal functions like `intLiteral` and `stringLiteral` for this purpose. Let's create a similar literal function for our `mood` column type.

To create expressions that are inlined into SQL, we need to instantiate `LiteralOp`. For our mood enum, it looks like this:

```kotlin
fun moodLiteral(value: Mood): LiteralOp<Mood> =
    LiteralOp(PostgresEnumerationColumnType("mood", Mood::class.java), value)
```

This can be used in queries like so:

```kotlin
PersonTable.insert {
    it[name] = "John"
    it[mood] = moodLiteral(Mood.SAD)
}
```

However, running this code with our current implementation produces an error: `org.postgresql.util.PSQLException: ERROR: column "sad" does not exist`. Once again, the enum value isn't being properly formatted for SQL.

#### Implementing Value-to-String Conversion

To support literals, we need to implement the `valueToString()` method (or its sibling `nonNullValueToString()`). This method controls how values are converted to string representations in SQL statements.

Both `nonNullValueAsDefaultString()` and `nonNullValueToString()` need to produce the same output format for enum values—a quoted string with an optional type cast. We can refactor our code to avoid duplication:

```kotlin
override fun nonNullValueAsDefaultString(value: T) =
    nonNullValueToString(value)

override fun nonNullValueToString(value: T) =
    fromEnumeration(value).lowercase().let { "'$it'::${sqlType()}" }
```

With this implementation, the generated SQL now correctly inlines the enum value:

```sql
INSERT INTO person ("name", mood) VALUES (?, 'sad'::mood)
```

### Conclusion

In this article, we've walked through the process of creating a custom column type and explored how Exposed can be extended to write more expressive, type-safe code.

If you want to dive deeper, I recommend examining how Exposed's built-in types are implemented—such as JSON columns or date/time types. You'll find interesting patterns like `ArrayColumnType`, which wraps other column types, and column types that adapt their behavior based on the current database dialect.

I hope this article has been useful whether you're looking to create your own custom column types or simply want to better understand how column types work internally and communicate with the underlying JDBC driver.