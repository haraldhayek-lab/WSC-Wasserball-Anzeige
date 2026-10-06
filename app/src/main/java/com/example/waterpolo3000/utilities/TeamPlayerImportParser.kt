package com.example.waterpolo3000.utilities

import org.json.JSONArray
import org.json.JSONObject

data class ImportedPlayerRow(
    val teamName: String?,
    val number: Int?,
    val firstName: String,
    val lastName: String,
    val yearText: String?,
    val externalId: String?
)

data class TeamPlayerImportResult(
    val rows: List<ImportedPlayerRow>,
    val warnings: List<String>
)

object TeamPlayerImportParser {

    private val knownHeaderAliases = setOf(
        "nummer", "nr", "number",
        "vorname", "firstname", "first_name", "name",
        "nachname", "lastname", "last_name", "surname",
        "jahrgang", "year", "birthyear", "geburtsjahr",
        "id", "spielerid", "playerid", "lizenz", "license",
        "verein", "team", "mannschaft", "club"
    )

    fun parse(fileContent: String): TeamPlayerImportResult {
        val trimmed = fileContent.trim()
        if (trimmed.isEmpty()) {
            return TeamPlayerImportResult(emptyList(), listOf("Datei ist leer."))
        }
        return if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
            parseJson(trimmed)
        } else {
            parseCsv(trimmed)
        }
    }

    private fun parseJson(text: String): TeamPlayerImportResult {
        val warnings = mutableListOf<String>()
        val rows = mutableListOf<ImportedPlayerRow>()
        var hasTeamNameInRows = false

        val entries = if (text.startsWith("[")) {
            JSONArray(text)
        } else {
            val root = JSONObject(text)
            when {
                root.has("players") -> root.optJSONArray("players")
                root.has("spieler") -> root.optJSONArray("spieler")
                root.has("data") -> root.optJSONArray("data")
                root.has("entries") -> root.optJSONArray("entries")
                else -> null
            } ?: JSONArray()
        }

        if (entries.length() == 0) {
            warnings.add("Keine Spielerdaten im JSON gefunden.")
        }

        for (index in 0 until entries.length()) {
            val item = entries.optJSONObject(index)
            if (item == null) {
                warnings.add("Zeile ${index + 1}: Kein JSON-Objekt.")
                continue
            }
            val row = fromMap(item.keys().asSequence().associateWith { key -> item.opt(key) }, index + 1)
            if (row != null) {
                rows.add(row)
                if (!row.teamName.isNullOrBlank()) {
                    hasTeamNameInRows = true
                }
            } else {
                warnings.add("Zeile ${index + 1}: Pflichtfelder unvollstaendig.")
            }
        }
        if (rows.isNotEmpty() && !hasTeamNameInRows) {
            warnings.add("Kein Verein in Datei: Teamname aus Dialogfeld wird verwendet.")
        }
        return TeamPlayerImportResult(rows, warnings)
    }

    private fun parseCsv(text: String): TeamPlayerImportResult {
        val warnings = mutableListOf<String>()
        val lines = text.lines().filter { it.isNotBlank() }
        if (lines.isEmpty()) {
            return TeamPlayerImportResult(emptyList(), listOf("CSV/TXT enthaelt keine Zeilen."))
        }

        val firstLine = lines.first()
        val semicolonCount = firstLine.count { it == ';' }
        val commaCount = firstLine.count { it == ',' }
        val useWhitespaceDelimiter = semicolonCount == 0 && commaCount == 0
        val delimiter = if (semicolonCount >= commaCount) ';' else ','

        val parsedLines = lines.map { line ->
            if (useWhitespaceDelimiter) {
                parseWhitespaceSeparatedLine(line)
            } else {
                parseCsvLine(line, delimiter)
            }
        }

        val firstColumns = parsedLines.firstOrNull().orEmpty()
        val hasHeader = isLikelyHeaderLine(firstColumns)
        val headerColumns = if (hasHeader) {
            firstColumns.map { normalizeKey(it) }
        } else {
            defaultColumnsForHeaderlessRow(firstColumns.size)
        }
        val firstDataLineIndex = if (hasHeader) 1 else 0

        val rows = mutableListOf<ImportedPlayerRow>()
        val hasTeamColumn = headerColumns.any { isTeamAlias(it) }
        var hasTeamNameInRows = false

        for (lineIndex in firstDataLineIndex until parsedLines.size) {
            val columns = parsedLines[lineIndex]
            val valueMap = mutableMapOf<String, Any?>()
            headerColumns.forEachIndexed { index, header ->
                valueMap[header] = columns.getOrNull(index)?.trim()
            }
            val row = fromMap(valueMap, lineIndex + 1)
            if (row != null) {
                rows.add(row)
                if (!row.teamName.isNullOrBlank()) {
                    hasTeamNameInRows = true
                }
            } else {
                warnings.add("Zeile ${lineIndex + 1}: Pflichtfelder unvollstaendig.")
            }
        }

        if (rows.isEmpty()) {
            warnings.add("Keine gueltigen Spielerdaten in CSV gefunden.")
        } else if (!hasTeamColumn || !hasTeamNameInRows) {
            warnings.add("Kein Verein in Datei: Teamname aus Dialogfeld wird verwendet.")
        }
        return TeamPlayerImportResult(rows, warnings)
    }

    private fun isLikelyHeaderLine(columns: List<String>): Boolean {
        return columns
            .map { normalizeKey(it) }
            .any { normalized -> normalized in knownHeaderAliases }
    }

    private fun defaultColumnsForHeaderlessRow(columnCount: Int): List<String> {
        val defaults = listOf("nummer", "vorname", "nachname", "jahrgang", "id", "verein")
        if (columnCount <= defaults.size) {
            return defaults.take(columnCount)
        }

        val extraCount = columnCount - defaults.size
        return defaults + (1..extraCount).map { "extra$it" }
    }

    private fun isTeamAlias(normalizedHeader: String): Boolean {
        return normalizedHeader == "verein" ||
            normalizedHeader == "team" ||
            normalizedHeader == "mannschaft" ||
            normalizedHeader == "club"
    }

    // Supports space/tab separated rows and keeps quoted values intact.
    private fun parseWhitespaceSeparatedLine(line: String): List<String> {
        val result = mutableListOf<String>()
        val current = StringBuilder()
        var inQuotes = false
        var index = 0

        while (index < line.length) {
            val ch = line[index]
            when {
                ch == '"' && index + 1 < line.length && line[index + 1] == '"' -> {
                    current.append('"')
                    index++
                }
                ch == '"' -> inQuotes = !inQuotes
                ch.isWhitespace() && !inQuotes -> {
                    if (current.isNotEmpty()) {
                        result.add(current.toString())
                        current.clear()
                    }
                }
                else -> current.append(ch)
            }
            index++
        }

        if (current.isNotEmpty()) {
            result.add(current.toString())
        }
        return result
    }

    private fun fromMap(map: Map<String, Any?>, rowNumber: Int): ImportedPlayerRow? {
        val teamName = findValue(map, "verein", "team", "mannschaft", "club")
        val numberText = findValue(map, "nummer", "nr", "number")
        val firstName = findValue(map, "vorname", "firstname", "first_name", "name") ?: ""
        val lastName = findValue(map, "nachname", "lastname", "last_name", "surname") ?: ""
        val yearText = findValue(map, "jahrgang", "year", "birthyear", "geburtsjahr")
        val idText = findValue(map, "id", "spielerid", "playerid", "lizenz", "license")

        val team = teamName?.trim()
        val first = firstName.trim()
        val last = lastName.trim()
        val number = numberText?.trim()?.toIntOrNull()

        if (first.isEmpty() && last.isEmpty()) {
            return null
        }

        if (number == null) {
            return null
        }

        if (number < 1) {
            return null
        }

        return ImportedPlayerRow(
            teamName = team,
            number = number,
            firstName = if (first.isNotEmpty()) first else "Vorname$rowNumber",
            lastName = if (last.isNotEmpty()) last else "Nachname$rowNumber",
            yearText = yearText?.trim(),
            externalId = idText?.trim()
        )
    }

    private fun findValue(map: Map<String, Any?>, vararg aliases: String): String? {
        aliases.forEach { alias ->
            val normalizedAlias = normalizeKey(alias)
            map.entries.firstOrNull { normalizeKey(it.key) == normalizedAlias }?.value?.let { value ->
                val asString = value.toString().trim()
                if (!asString.isNullOrEmpty() && asString != "null") {
                    return asString
                }
            }
        }
        return null
    }

    private fun normalizeKey(key: String): String {
        return key.lowercase()
            .replace("ä", "ae")
            .replace("ö", "oe")
            .replace("ü", "ue")
            .replace("ß", "ss")
            .replace("_", "")
            .replace("-", "")
            .replace(" ", "")
            .trim()
    }

    // Minimal CSV parser with quoted field support.
    private fun parseCsvLine(line: String, delimiter: Char): List<String> {
        val result = mutableListOf<String>()
        val current = StringBuilder()
        var inQuotes = false
        var index = 0

        while (index < line.length) {
            val ch = line[index]
            when {
                ch == '"' && index + 1 < line.length && line[index + 1] == '"' -> {
                    current.append('"')
                    index++
                }
                ch == '"' -> inQuotes = !inQuotes
                ch == delimiter && !inQuotes -> {
                    result.add(current.toString())
                    current.clear()
                }
                else -> current.append(ch)
            }
            index++
        }
        result.add(current.toString())
        return result
    }
}