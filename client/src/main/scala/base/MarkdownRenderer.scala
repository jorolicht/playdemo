package base

/**
 * Utility object for parsing and rendering Markdown strings to HTML.
 * Supports headings, bold/italic text, links, lists, and paragraph formatting.
 */
object MarkdownRenderer:

  /**
   * Renders a Markdown formatted string into an HTML string suitable for display.
   *
   * @param md The raw Markdown formatted string.
   * @return The parsed HTML representation.
   */
  def render(md: String): String =
    if md == null || md.trim.isEmpty then return ""
    var html = md.trim

    // Basic HTML escaping for security
    html = html.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

    // Headings
    html = html.replaceAll("(?m)^### (.*)$", "<h5 class=\"fw-bold mt-3 mb-2 text-primary\">$1</h5>")
    html = html.replaceAll("(?m)^## (.*)$", "<h4 class=\"fw-bold mt-3 mb-2 text-primary\">$1</h4>")
    html = html.replaceAll("(?m)^# (.*)$", "<h3 class=\"fw-bold mt-3 mb-2 text-primary\">$1</h3>")

    // Bold & Italic
    html = html.replaceAll("\\*\\*(.*?)\\*\\*", "<strong>$1</strong>")
    html = html.replaceAll("__(.*?)__", "<strong>$1</strong>")
    html = html.replaceAll("\\*(.*?)\\*", "<em>$1</em>")
    html = html.replaceAll("_(.*?)_", "<em>$1</em>")

    // Links [text](url)
    html = html.replaceAll("\\[(.*?)\\]\\((.*?)\\)", "<a href=\"$2\" target=\"_blank\" class=\"text-primary text-decoration-underline\">$1</a>")

    // Process lists and paragraphs line by line
    val lines = html.split("\n")
    val sb = new StringBuilder()
    var inList = false

    for (line <- lines) {
      val trimmed = line.trim
      if (trimmed.startsWith("- ") || trimmed.startsWith("* ")) {
        if (!inList) {
          sb.append("<ul class=\"mb-3 ps-3\">\n")
          inList = true
        }
        val item = trimmed.substring(2)
        sb.append(s"<li>$item</li>\n")
      } else {
        if (inList) {
          sb.append("</ul>\n")
          inList = false
        }
        if (trimmed.nonEmpty && !trimmed.startsWith("<h")) {
          sb.append(s"<p class=\"mb-2 text-secondary\">$trimmed</p>\n")
        } else {
          sb.append(s"$line\n")
        }
      }
    }
    if (inList) {
      sb.append("</ul>\n")
    }

    sb.toString()
