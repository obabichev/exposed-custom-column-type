package com.obabichev

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.Transaction
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.transactions.transaction


abstract class BaseTest {
    val db by lazy {
        Database.connect(
            "jdbc:postgresql://localhost:5444/mydb?user=postgres&password=postgres&ssl=false",
            driver = "org.postgresql.Driver"
        )
    }

    fun withTable(table: Table, block: Transaction.() -> Unit) {
        transaction(db) {
            try {
                SchemaUtils.drop(table)
                SchemaUtils.create(table)
                block()
            } finally {
                SchemaUtils.drop(table)
            }
        }
    }
}