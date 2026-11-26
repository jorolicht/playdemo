package shared.utils

/**
 * Anzeige von Spielernamen im Format „Nachname, Vorname“.
 */
object PlayerName:

  /** Höchstlänge des Namens, ab der der Vorname gekürzt wird (z. B. in der Ergebniseingabe). */
  val MaxLength: Int = 20

  /**
   * Voller Name „Nachname, Vorname“ (ohne Vorname nur der Nachname).
   *
   * @param last  Nachname
   * @param first Vorname
   * @return z. B. „Lichtenegger, Robert“
   */
  def full(last: String, first: String): String =
    val (l, f) = (Option(last).getOrElse("").trim, Option(first).getOrElse("").trim)
    if f.isEmpty then l else if l.isEmpty then f else s"$l, $f"

  /**
   * Kurzer Name: wie [[full]]; ist er länger als `max` Zeichen, wird der Vorname auf den ersten Buchstaben mit Punkt
   * gekürzt (der Nachname bleibt immer vollständig).
   *
   * @param last  Nachname
   * @param first Vorname
   * @param max   Höchstlänge (Standard [[MaxLength]])
   * @return z. B. „Lichtenegger, Robert“ bzw. „Müller-Lüdenscheidt, H.“
   */
  def short(last: String, first: String, max: Int = MaxLength): String =
    val name = full(last, first)
    val (l, f) = (Option(last).getOrElse("").trim, Option(first).getOrElse("").trim)
    if name.length <= max || l.isEmpty || f.isEmpty then name else s"$l, ${f.head}."
