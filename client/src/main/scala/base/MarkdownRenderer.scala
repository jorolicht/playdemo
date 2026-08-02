package base

/**
 * Utility object for parsing and rendering Markdown strings to HTML.
 * Supports headings, bold/italic text, links, lists, and paragraph formatting safely in Scala.js.
 */
object MarkdownRenderer:

  /**
   * Renders a Markdown formatted string into an HTML string suitable for display.
   *
   * @param md The raw Markdown formatted string.
   * @return The parsed HTML representation.
   */
  def render(md: String): String =
    try {
      if md == null || md.trim.isEmpty then return ""
      val rawLines = md.trim.split("\n")
      val sb = new StringBuilder()
      var inList = false

      for (line <- rawLines) {
        val trimmed = line.trim
        if (trimmed.startsWith("- ") || trimmed.startsWith("* ")) {
          if (!inList) {
            sb.append("<ul class=\"mb-3 ps-3\">\n")
            inList = true
          }
          val itemContent = renderInline(trimmed.substring(2))
          sb.append(s"<li>$itemContent</li>\n")
        } else {
          if (inList) {
            sb.append("</ul>\n")
            inList = false
          }
          if (trimmed.startsWith("# ")) {
            val title = renderInline(trimmed.substring(2))
            sb.append(s"<h3 class=\"fw-bold mt-3 mb-2 text-primary\">$title</h3>\n")
          } else if (trimmed.startsWith("## ")) {
            val title = renderInline(trimmed.substring(3))
            sb.append(s"<h4 class=\"fw-bold mt-3 mb-2 text-primary\">$title</h4>\n")
          } else if (trimmed.startsWith("### ")) {
            val title = renderInline(trimmed.substring(4))
            sb.append(s"<h5 class=\"fw-bold mt-3 mb-2 text-primary\">$title</h5>\n")
          } else if (trimmed.nonEmpty) {
            val pContent = renderInline(trimmed)
            sb.append(s"<p class=\"mb-2 text-secondary\">$pContent</p>\n")
          }
        }
      }

      if (inList) {
        sb.append("</ul>\n")
      }

      sb.toString()
    } catch {
      case ex: Throwable =>
        Logging.error(s"MarkdownRenderer error: ${ex.getMessage}")
        if (md != null) md.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\n", "<br>") else ""
    }

  /**
   * Helper to render inline formatting (bold, italic, links, HTML escaping).
   */
  private def renderInline(text: String): String =
    if (text == null) return ""
    var s = text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

    // Bold & Italic
    s = s.replaceAll("\\*\\*(.*?)\\*\\*", "<strong>$1</strong>")
    s = s.replaceAll("__(.*?)__", "<strong>$1</strong>")
    s = s.replaceAll("\\*(.*?)\\*", "<em>$1</em>")
    s = s.replaceAll("_(.*?)_", "<em>$1</em>")

    // Links [text](url)
    s = s.replaceAll("\\[(.*?)\\]\\((.*?)\\)", "<a href=\"$2\" target=\"_blank\" class=\"text-primary text-decoration-underline\">$1</a>")

    s
