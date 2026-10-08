#!/usr/bin/env bash
# Render completo: N processi in parallelo, poi concatenazione e audio.
#   tools/render_all.sh <audio.mp3> <uscita.mp4> [processi] [fps]
set -euo pipefail
cd "$(dirname "$0")/.."
AUDIO="$1"; OUT="$2"; N="${3:-4}"; FPS="${4:-30}"
export NODE_PATH="${NODE_PATH:-/opt/node22/lib/node_modules}"
DUR=$(ffprobe -v error -show_entries format=duration -of csv=p=0 "$AUDIO")
TOTAL=$(python3 -c "import math;print(math.ceil($DUR*$FPS))")
mkdir -p out
rm -f out/chunk_*.mp4 out/list.txt
echo "fotogrammi: $TOTAL in $N pezzi"
for i in $(seq 0 $((N-1))); do
  A=$(( TOTAL*i/N )); B=$(( TOTAL*(i+1)/N ))
  node tools/render.cjs chunk "out/chunk_$i.mp4" $A $B $FPS > "out/chunk_$i.log" 2>&1 &
  echo "file 'chunk_$i.mp4'" >> out/list.txt
done
wait
ffmpeg -hide_banner -loglevel error -y -f concat -safe 0 -i out/list.txt -i "$AUDIO" \
  -map 0:v -map 1:a -c:v copy -c:a aac -b:a 256k -shortest -movflags +faststart "$OUT"
echo "fatto: $OUT"
