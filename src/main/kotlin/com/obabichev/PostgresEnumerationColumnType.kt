package com.obabichev


import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.ColumnType
import org.jetbrains.exposed.v1.core.LiteralOp
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.statements.api.PreparedStatementApi
import org.postgresql.util.PGobject


class PostgresEnumerationColumnType<T : Enum<T>>(
    val typeName: String,
    enumClass: Class<T>,
) : ColumnType<T>() {

    private val stringToEnumeration = enumClass.enumConstants.associateBy { it.name.lowercase() }

    private val enumerationToString = stringToEnumeration.map { (k, v) -> v to k }.toMap()

    fun toEnumeration(name: String): T = stringToEnumeration[name.lowercase()]!!

    fun fromEnumeration(value: T): String = enumerationToString[value]!!

    override fun sqlType(): String {
        return typeName
    }

    override fun valueFromDB(value: Any): T? {
        return when (value) {
            is String -> toEnumeration(value.uppercase())
            else -> error("Unexpected value of type Int: $value of ${value::class.qualifiedName}")
        }
    }

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

    override fun nonNullValueAsDefaultString(value: T): String {
        return nonNullValueToString(value)
    }

    override fun nonNullValueToString(value: T): String {
        return fromEnumeration(value).lowercase().let { "'$it'::${sqlType()}" }
    }

//    override fun createStatement(): List<String> {
//        val values = enumeration.joinToString { "'$it'" }
//
//        return listOf(
//            "CREATE TYPE ${sqlType()} AS ENUM ($values);"
//        )
//    }
}

fun moodColumnType(): ColumnType<Mood> = PostgresEnumerationColumnType("mood", Mood::class.java)

fun Table.mood(name: String): Column<Mood> =
    registerColumn(name, moodColumnType())

fun moodLiteral(value: Mood): LiteralOp<Mood> = LiteralOp(moodColumnType(), value)

