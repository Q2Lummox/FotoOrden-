"""
Busca un elemento en un volcado de uiautomator y escribe el centro "x y".

  python3 tools/ui.py ui.xml text|desc PREFIJO [N|last]
"""
import re
import sys
import xml.etree.ElementTree as ET

path, kind, prefix = sys.argv[1], sys.argv[2], sys.argv[3]
pick = sys.argv[4] if len(sys.argv) > 4 else "1"
attr = "text" if kind == "text" else "content-desc"

matches = []
for node in ET.parse(path).iter("node"):
    value = node.get(attr) or ""
    if value.startswith(prefix):
        m = re.match(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", node.get("bounds", ""))
        if m:
            x1, y1, x2, y2 = map(int, m.groups())
            if x2 > x1 and y2 > y1:
                matches.append(((x1 + x2) // 2, (y1 + y2) // 2))

if not matches:
    sys.exit(1)
i = len(matches) - 1 if pick == "last" else min(int(pick), len(matches)) - 1
print(*matches[i])
