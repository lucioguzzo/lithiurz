"""Genera data.js (versi con tempi + beat) dai JSON dell'analisi audio."""
import json, sys
lines, beats, out = sys.argv[1], sys.argv[2], sys.argv[3]
L = json.load(open(lines)); B = json.load(open(beats))
with open(out, "w") as f:
    f.write("// generato da tools/make_data.py\n")
    f.write("const LINES = " + json.dumps([{"t": l["text"], "a": l["start"], "b": l["end"]} for l in L], ensure_ascii=False) + ";\n")
    f.write("const BEATS = " + json.dumps([round(b, 3) for b in B["beats"]]) + ";\n")
    f.write(f"const DURATION = {B['duration']:.3f};\n")
