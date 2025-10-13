package com.obabichev

import org.junit.Test
import org.postgresql.util.PGobject
import java.sql.DriverManager
import kotlin.test.assertEquals

class PureJdbcSolution {
    @Test
    fun testPostgresEnumeration() {
        val url = "jdbc:postgresql://localhost:5444/mydb"
        val user = "postgres"
        val password = "postgres"

        DriverManager.getConnection(url, user, password).use { connection ->

            connection.prepareStatement("CREATE TABLE IF NOT EXISTS person (\"name\" TEXT NOT NULL, mood mood)")
                .use { createTableStmt ->
                    createTableStmt.executeUpdate()
                }

            // Clean up
            connection.prepareStatement("DELETE FROM person WHERE name = ?").use { deleteStmt ->
                deleteStmt.setString(1, "John")
                deleteStmt.executeUpdate()
            }

            // Insert a row
//            connection.prepareStatement("INSERT INTO person (name, mood) VALUES (?, ?::mood)").use { insertStmt ->
//                insertStmt.setString(1, "John")
//                insertStmt.setString(2, "happy")
//                insertStmt.executeUpdate()
//            }

            connection.prepareStatement("INSERT INTO person (name, mood) VALUES (?, ?)").use { insertStmt ->
                insertStmt.setString(1, "John")

                val mood = PGobject().also {
                    it.type = "mood"
                    it.value = "happy"
                }

//                insertStmt.setObject(2, "happy", java.sql.Types.OTHER)
                insertStmt.setObject(2, mood)
                insertStmt.executeUpdate()
            }

            // Read the row
            connection.prepareStatement("SELECT name, mood FROM person WHERE name = ?").use { selectStmt ->
                selectStmt.setString(1, "John")
                selectStmt.executeQuery().use { rs ->
                    assert(rs.next())
                    assertEquals("John", rs.getString("name"))
                    assertEquals("happy", rs.getObject("mood"))
                }
            }
        }
    }
}