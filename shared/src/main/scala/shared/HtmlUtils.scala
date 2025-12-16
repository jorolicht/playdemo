package shared

object HtmlUtils:
  def attrId(id: NamedId): String = { s""" id='${id.name}' """ }

  def escapeJsString(s: String): String =
    s.flatMap {
      case '"'  => "\\\""
      case '\'' => "\\'"
      case '\\' => "\\\\"
      case '\n' => "\\n"
      case '\r' => "\\r"
      case '\t' => "\\t"
      case c    => c.toString
    }

