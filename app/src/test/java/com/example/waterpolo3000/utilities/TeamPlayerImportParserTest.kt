package com.example.waterpolo3000.utilities

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TeamPlayerImportParserTest {

    @Test
    fun parseCsv_withoutHeader_supportsJahrgangBeforeId() {
        val content = """
            1,Max,Mueller,2012,1001
            2,Jonas,Becker,2011,1002
        """.trimIndent()

        val result = TeamPlayerImportParser.parse(content)

        assertEquals(2, result.rows.size)
        assertEquals(1, result.rows[0].number)
        assertEquals("Max", result.rows[0].firstName)
        assertEquals("Mueller", result.rows[0].lastName)
        assertEquals("2012", result.rows[0].yearText)
        assertEquals("1001", result.rows[0].externalId)
    }

    @Test
    fun parseTxt_withoutHeader_allowsMissingId() {
        val content = """
            3 Tom Schneider 2010
            4 Leon Fischer 2011
        """.trimIndent()

        val result = TeamPlayerImportParser.parse(content)

        assertEquals(2, result.rows.size)
        assertEquals(3, result.rows[0].number)
        assertEquals("2010", result.rows[0].yearText)
        assertNull(result.rows[0].externalId)
    }

    @Test
    fun parseCsv_withHeader_jahrgangBeforeId_keepsIdOptional() {
        val content = """
            nummer,vorname,nachname,jahrgang,id
            5,Luca,Hartmann,2012,
            6,Paul,Werner,2011,3006
        """.trimIndent()

        val result = TeamPlayerImportParser.parse(content)

        assertEquals(2, result.rows.size)
        assertEquals("2012", result.rows[0].yearText)
        assertNull(result.rows[0].externalId)
        assertEquals("3006", result.rows[1].externalId)
    }
}
