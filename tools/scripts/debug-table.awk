BEGIN { block = "" }
{ if (/DEBUG/) {
    line = gensub(/^\[DEBUG\] */, "", "g", $0)
    if (substr (line, 0, 1) == ",") {
      block = block "\\n" line
    } else {
      if (block != "") { system("echo -e \"" block "\" | column -s, -t -o\" \"") }
      block = line
    }
  }
}
END {
  if (block != "") {
    system("echo -e \"" block "\" | column -s, -t -o\" \"")
  }
}
