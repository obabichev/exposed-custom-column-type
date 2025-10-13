package com.obabichev

import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.ExpressionWithColumnType
import org.jetbrains.exposed.v1.core.QueryBuilder
import org.jetbrains.exposed.v1.core.TextColumnType


class TextCastExpression<T>(val column: Column<T>) : ExpressionWithColumnType<String>() {
    override val columnType = TextColumnType()

    override fun toQueryBuilder(queryBuilder: QueryBuilder) {
        column.toQueryBuilder(queryBuilder)
        queryBuilder.append("::text")
    }
}

fun <T> Column<T>.asText() = TextCastExpression(this)