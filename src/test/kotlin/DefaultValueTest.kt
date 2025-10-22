package com.obabichev

import org.jetbrains.exposed.v1.core.StdOutSqlLogger
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import kotlin.test.Test
import kotlin.test.assertEquals

class DefaultValueTest : BaseTest() {
    object PersonTable : Table("person") {
        val name = text("name")

        val mood = mood("mood")
            .default(Mood.OK)
    }

    @Test
    fun test1() {
        transaction(db) {
            addLogger(StdOutSqlLogger)

            SchemaUtils.drop(PersonTable)

            SchemaUtils.create(PersonTable)

            PersonTable.insert {
                it[name] = "John"
            }

            assertEquals(1, PersonTable.selectAll().count())

            assertEquals(Mood.OK, PersonTable.selectAll().first()[PersonTable.mood])
        }
    }
}