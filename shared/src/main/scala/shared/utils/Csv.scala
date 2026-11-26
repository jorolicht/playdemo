package shared.utils

/**
 * CSV-Dateien für Excel (deutsche Einstellungen): Trennzeichen Semikolon, Zeilenende CRLF, UTF-8 mit BOM, damit Excel
 * Umlaute richtig erkennt.
 *
 * Schutz vor Formel-Einschleusung: Zellen, die mit `=`, `+`, `@`, Tabulator oder Wagenrücklauf beginnen (bzw. mit `-`,
 * ohne eine Zahl zu sein), erhalten ein vorangestelltes Hochkomma, damit Excel sie als Text und nicht als Formel liest
 * (Namen stammen z. B. aus der öffentlichen Anmeldung).
 */
object Csv:

  /** Byte Order Mark für UTF-8. */
  val Bom: String = "﻿"

  private val Number = "^-?\\d+([.,]\\d+)?$".r

  /**
   * Bereitet eine Zelle auf: Formel-Schutz, Anführungszeichen bei Trennzeichen, Anführungszeichen oder Zeilenumbruch.
   *
   * @param value Inhalt
   * @return Zelle für die CSV-Datei
   */
  def cell(value: String): String =
    val v = Option(value).getOrElse("")
    val safe =
      if v.nonEmpty && ("=+@\t\r".contains(v.head) || (v.head == '-' && Number.matches(v.trim) == false)) then "'" + v
      else v
    if safe.exists(c => c == ';' || c == '"' || c == '\n' || c == '\r') then "\"" + safe.replace("\"", "\"\"") + "\""
    else safe

  /**
   * Erzeugt den Inhalt einer CSV-Datei.
   *
   * @param header Spaltenüberschriften
   * @param rows   Zeilen
   * @return Dateiinhalt mit BOM
   */
  def document(header: Seq[String], rows: Seq[Seq[String]]): String =
    Bom + (header +: rows).map(_.map(cell).mkString(";")).mkString("", "\r\n", "\r\n")
