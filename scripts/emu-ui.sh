#!/bin/zsh
# 에뮬레이터 화면 조작 보조 (Pleos Connect, AAOS user 10). 개발 확인용이며 앱 코드와 무관하다.
#   scripts/emu-ui.sh tap "텍스트 일부" [n]   텍스트가 들어간 n번째 요소 중앙을 누른다 (n=0 이면 마지막)
#   scripts/emu-ui.sh tap "=정확한 텍스트"     정확히 같은 텍스트만
#   scripts/emu-ui.sh swipe                    오른쪽 패널을 위로 스크롤
#   scripts/emu-ui.sh shot 파일이름            화면을 $OUT/파일이름.png 로 저장 (기본 OUT=/tmp)
# 주의: 버튼이 촘촘한 화면에서는 다른 버튼을 누를 수 있다. 상태를 바꾸는 탭 뒤에는 서버 상태를 curl 로 확인할 것.
#       한글은 adb input text 로 입력할 수 없다.
ADB="$HOME/Library/Android/sdk/platform-tools/adb -s ${SERIAL:-emulator-5554}"
OUT=${OUT:-/tmp}
case "$1" in
  tap)
    eval $ADB shell uiautomator dump --user 10 /sdcard/ui.xml >/dev/null 2>&1 || eval $ADB shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1
    eval $ADB exec-out cat /sdcard/ui.xml > $OUT/ui.xml
    xy=$(python3 - "$2" "$OUT/ui.xml" "${3:-1}" <<'PY'
import re,sys
want=sys.argv[1]; n=int(sys.argv[3]); x=open(sys.argv[2],encoding='utf-8').read()
hits=[m for m in re.finditer(r'text="([^"]*)"[^>]*?bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"',x) if (m.group(1)==want[1:] if want.startswith("=") else want in m.group(1))]
if n==0 and hits: n=len(hits)
if n>0 and len(hits)>=n:
    a,b,c,d=map(int,hits[n-1].groups()[1:]); print((a+c)//2,(b+d)//2)
PY
)
    [ -z "$xy" ] && { echo "NOT FOUND: $2"; exit 1; }
    eval $ADB shell input tap $xy; echo "tap '$2' @ $xy";;
  swipe) eval $ADB shell input swipe 1900 1100 1900 400 300; echo swiped;;
  shot) eval $ADB exec-out screencap -p > $OUT/$2.png; echo "$OUT/$2.png";;
  *) sed -n 2,8p "$0";;
esac
