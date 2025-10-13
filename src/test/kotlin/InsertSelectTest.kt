package com.obabichev

import org.jetbrains.exposed.v1.core.StdOutSqlLogger
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.greater
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.junit.Test
import kotlin.test.assertEquals


class InsertSelectTest : BaseTest() {

    object PersonTable : Table("person") {
        val name = text("name")

        val mood = mood("mood")
    }

    @Test
    fun test1() {
        withTable(PersonTable) {
            addLogger(StdOutSqlLogger)

            PersonTable.insert {
                it[name] = "John"
                it[mood] = Mood.SAD
            }

            PersonTable.selectAll()
                .forEach { println("${it[PersonTable.name]} is ${it[PersonTable.mood]}") }

            val person = PersonTable.selectAll()
                .first()

            assertEquals("John", person[PersonTable.name])
            assertEquals(Mood.SAD, person[PersonTable.mood])
        }
    }

    @Test
    fun test2() {
        withTable(PersonTable) {
            addLogger(StdOutSqlLogger)

            PersonTable.insert {
                it[name] = "John"
//                it[mood] = Mood.SAD
                it[mood] = moodLiteral(Mood.SAD)
            }

        }
    }

    @Test
    fun test3() {
        withTable(PersonTable) {
            addLogger(StdOutSqlLogger)

            PersonTable.insert {
                it[name] = "John"
                it[mood] = Mood.HAPPY
            }

            val person = PersonTable.selectAll()
                .where { PersonTable.mood greater Mood.OK }
                .first()

            assertEquals(Mood.HAPPY, person[PersonTable.mood])
        }
    }

    @Test
    fun test4() {
        withTable(PersonTable) {
            addLogger(StdOutSqlLogger)
            PersonTable.insert {
                it[name] = "John"
                it[mood] = Mood.HAPPY
            }

            val expression = PersonTable.mood.asText()

            val mood = PersonTable.select(expression)
                .first()[expression]

            assertEquals("happy", mood)

        }
    }
}