package com.obabichev


import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.ColumnType
import org.jetbrains.exposed.v1.core.LiteralOp
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.statements.api.PreparedStatementApi
import org.postgresql.util.PGobject

class PostgresEnumerationColumnType<T : Enum<T>>(
    val typeName: String,
    val enumClass: Class<T>
) : ColumnType<T>() {
    override fun sqlType() = typeName

    private val stringToEnumeration = enumClass.enumConstants.associateBy { it.name.lowercase() }

    private val enumerationToString = stringToEnumeration.map { (k, v) -> v to k }.toMap()

    private fun toEnumeration(name: String): T = stringToEnumeration[name.lowercase()]!!

    private fun fromEnumeration(value: T): String = enumerationToString[value]!!


    override fun valueFromDB(value: Any): T? {
        return when (value) {
            is String -> toEnumeration(value.uppercase())
            else -> error("Unexpected value $value of type ${value::class.qualifiedName}")
        }
    }

    override fun nonNullValueAsDefaultString(value: T) =
        nonNullValueToString(value)

    override fun nonNullValueToString(value: T) =
        fromEnumeration(value).lowercase().let { "'$it'::${sqlType()}" }

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
            PGobject()
                .also {
                    it.type = sqlType()
                    it.value = value as String
                }
                .let {
                    stmt.set(index, it, this)
                }
        }
    }
}

fun moodColumnType(): ColumnType<Mood> = PostgresEnumerationColumnType("mood", Mood::class.java)

fun <T : Enum<T>> Table.pgEnum(name: String, typeName: String, enumClass: Class<T>): Column<T> =
    registerColumn(name, PostgresEnumerationColumnType(typeName, enumClass))

fun Table.mood(name: String): Column<Mood> = pgEnum(name, "mood", Mood::class.java)

fun moodLiteral(value: Mood): LiteralOp<Mood> =
    LiteralOp(PostgresEnumerationColumnType("mood", Mood::class.java), value)

