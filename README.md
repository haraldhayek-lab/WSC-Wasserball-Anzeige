# WSC-Wasserball-Anzeige

Wasserball Spielsteuerung und Spielprotokoll mit Bluetooth-Verbindung zur LED-Tafel.

Optimiert fuer Full-HD-Tablets im Landscape-Modus.

## Dropdown-Menue: Einstellmoeglichkeiten

In der Spiel-Ansicht ist rechts oben ein Dropdown-Menue (3-Punkte-Menue) verfuegbar.
Folgende Eintraege stehen dort zur Verfuegung (in Reihenfolge):

- Neues Spiel: Startet den Dialog fuer ein neues Spiel.
- Spiel laden: Laedt ein gespeichertes Spiel aus Datei.
- Spiel speichern: Speichert den aktuellen Spielstand als Datei.
- Spiel beenden: Beendet das laufende Spiel mit Abschlussdialog.
- Hauptzeit bearbeiten: Oeffnet die manuelle Bearbeitung der Hauptzeit.
- Pause kuerzen/bearbeiten: Oeffnet die Bearbeitung der aktiven Pause.
- Shotclock bearbeiten: Oeffnet die manuelle Bearbeitung der Shotclock.
- Spiel-Einstellungen: Oeffnet die allgemeinen Spielparameter.
- Spiel-Einstellungen - Spielzeit (Min/Sek).
- Spiel-Einstellungen - Shotclock lang/kurz.
- Spiel-Einstellungen - Pausenzeiten.
- Spiel-Einstellungen - Timeout-Laenge.
- Spiel-Einstellungen - Anzahl Timeouts.
- Spiel-Einstellungen - Anzahl Viertel.
- Spiel-Einstellungen - OT/PSO.
- Spiel-Einstellungen - Zeitart netto/brutto.
- Unentschieden-Modus: Oeffnet den Dialog fuer Fortsetzung (z. B. OT/PSO).
- Teams: Oeffnet Teamnamen- und Teamimport-Einstellungen.
- Lautstaerke: Oeffnet die System-Lautstaerke-Einstellungen des Geraets.

## LED- und Bluetooth-Menue (Tafel/Shotclocks)

Zusaetzlich gibt es in der Spiel-Ansicht ein eigenes LED-/Bluetooth-Menue (Icon mit Verbindungsstatus).
Dort sind folgende Funktionen verfuegbar:

- Helligkeit alle: Setzt die Helligkeit fuer Haupttafel und alle verbundenen Shotclocks gemeinsam.
- Verbinde alle: Startet den Verbindungsaufbau fuer Haupttafel und Shotclocks in einem Schritt.
- Haupttafel:
- Haupttafel - Helligkeit: Stellt die Helligkeit nur der Haupttafel ein.
- Haupttafel - Verbinden: Verbindet gezielt nur die Haupttafel.
- Shotclock 1:
- Shotclock 1 - Helligkeit: Stellt die Helligkeit nur fuer Shotclock 1 ein.
- Shotclock 1 - Verbinden: Verbindet gezielt Shotclock 1.
- Shotclock 2:
- Shotclock 2 - Helligkeit: Stellt die Helligkeit nur fuer Shotclock 2 ein.
- Shotclock 2 - Verbinden: Verbindet gezielt Shotclock 2.
- Shotclock 3:
- Shotclock 3 - Helligkeit: Stellt die Helligkeit nur fuer Shotclock 3 ein.
- Shotclock 3 - Verbinden: Verbindet gezielt Shotclock 3.
- Shotclock 4:
- Shotclock 4 - Helligkeit: Stellt die Helligkeit nur fuer Shotclock 4 ein.
- Shotclock 4 - Verbinden: Verbindet gezielt Shotclock 4.

Hinweis: Je nach Verbindungsstatus sind einzelne Menuepunkte aktiviert oder deaktiviert.

## Team- und Spieler-Import (CSV/TXT/JSON)

Im Dialog Teams gibt es zwei getrennte Import-Aktionen in dieser Reihenfolge:

- Team White: Spieler aus Datei importieren
- Team Blue: Spieler aus Datei importieren

Die ausgewaehlte Datei wird immer dem angeklickten Team zugeordnet (White oder Blue).

Wenn in der Datei ein Vereinsname (`verein` oder `team`) vorhanden ist, wird dieser automatisch in das entsprechende Teamnamen-Feld uebernommen.
Wenn kein Vereinsname in der Datei vorhanden ist, wird der Teamname aus dem Dialog-Textfeld verwendet.

### Unterstuetzte Dateitypen

- CSV
- TXT
- JSON

CSV wird mit `,`, `;` und auch mit Leerzeichen/Tab als Spaltentrenner unterstuetzt.
TXT wird wie CSV geparst (gleiche Trennzeichen-Regeln).

Kopfzeile ist optional fuer CSV/TXT:

- Mit Kopfzeile werden Felder ueber Namen erkannt.
- Ohne Kopfzeile gilt positionsbasiert: `nummer, vorname, nachname, jahrgang, id`.
- `jahrgang` und `id` sind optional.

### Felder pro Spielerzeile

Pflichtfelder:

- `nummer` (oder `nr`, `number`) -> Spielernummer 1..14
- `vorname` (oder `firstname`)
- `nachname` (oder `lastname`)

Optionale Felder:

- `verein` (oder `team`, `mannschaft`, `club`)
- `jahrgang` (oder `year`, `birthyear`, `geburtsjahr`)
- `id` (oder `spielerId`, `playerId`, `lizenz`, `license`) -> wird als numerische Spieler-ID gespeichert

Nicht verwendet:

- `cap`/Farbe-Feld wird ignoriert

### Verhalten bei fehlenden Spielernummern

Wenn eine Nummer 1..14 in der Importdatei fuer ein Team fehlt, wird der Spielername fuer diese Nummer intern auf ein Leerzeichen (` `) gesetzt.
Damit bleiben die Zeilen im Protokoll stabil sichtbar.

### Anzeige auf der Protokoll-Seite

- Spieler werden als `Vorname Nachname [Jahrgang] [ID]` angezeigt (nur vorhandene Werte).
- Jahrgang und ID sind optional und werden ohne Klammern, jeweils mit Leerzeichen getrennt, angezeigt.

## Testdateien im Repository

- `test-import-teams-asv.csv` (ASV, Nummern 1 bis 10)
- `test-import-teams-wsc.csv` (WSC, Nummern 1 bis 4 sowie 6 bis 12)

## Beispiel CSV

```csv
nummer,vorname,nachname,id
1,Max,Mueller,1001
2,Jonas,Becker,1002
3,Tim,Schneider,2001
4,Leon,Fischer,2002
```

## Beispiel CSV mit Leerzeichen als Trennzeichen

```csv
nummer vorname nachname id
1 Max Mueller 1001
2 Jonas Becker 1002
3 Tim Schneider 2001
4 Leon Fischer 2002
```

## Beispiel JSON (Array)

```json
[
  {
    "verein": "SC Weiss",
    "nummer": 1,
    "vorname": "Max",
    "nachname": "Mueller",
    "id": "1001"
  },
  {
    "verein": "SV Blau",
    "nummer": 2,
    "vorname": "Leon",
    "nachname": "Fischer",
    "id": "2002"
  }
]
```

## Beispiel JSON (Objekt mit players)

```json
{
  "players": [
    {
      "team": "SC Weiss",
      "number": 3,
      "firstname": "Noah",
      "lastname": "Klein",
      "playerId": "1003"
    },
    {
      "team": "SV Blau",
      "number": 4,
      "firstname": "Paul",
      "lastname": "Wolf",
      "playerId": "2004"
    }
  ]
}
```
