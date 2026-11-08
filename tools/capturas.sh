#!/usr/bin/env bash
# Recorre la app en un emulador con fotos de ejemplo y guarda capturas en capturas/.
# Se ejecuta desde la raíz del repositorio con el emulador ya arrancado.
set -u

PKG=com.fotoorden.app
OUT=capturas
APK=app/build/outputs/apk/debug/app-debug.apk
A=adb
mkdir -p "$OUT"

log() { echo "== $*"; }
shot() { sleep "${2:-2}"; $A exec-out screencap -p > "$OUT/$1.png"; log "captura $1"; }
dump() {
  for _ in 1 2 3 4 5 6; do
    if $A shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1 && $A pull /sdcard/ui.xml /tmp/ui.xml >/dev/null 2>&1; then
      return 0
    fi
    sleep 1
  done
  return 1
}
find_xy() { dump && python3 tools/ui.py /tmp/ui.xml "$@"; }
tap() {
  local xy
  if xy=$(find_xy "$@") && [ -n "$xy" ]; then $A shell input tap $xy; sleep 1.5; return 0; fi
  log "no encontrado: $*"; return 1
}
longpress() {
  local xy
  if xy=$(find_xy "$@") && [ -n "$xy" ]; then $A shell input swipe $xy $xy 900; sleep 1.5; return 0; fi
  log "no encontrado: $*"; return 1
}
back() { $A shell input keyevent 4; sleep 1.5; }
demo() { $A shell am broadcast -a com.android.systemui.demo -e command "$@" >/dev/null 2>&1; }

$A wait-for-device
$A shell cmd uimode night no

# Barra de estado limpia
$A shell settings put global sysui_demo_allowed 1
demo enter
demo clock -e hhmm 1041
demo battery -e level 100 -e plugged false
demo network -e wifi show -e level 4 -e fully true
demo network -e mobile show -e datatype none -e level 4
demo notifications -e visible false

log "Copiando fotos de ejemplo"
$A push media/DCIM/Camera/. /sdcard/DCIM/Camera/ >/dev/null
$A push "media/Pictures/Vacaciones Asturias/." "/sdcard/Pictures/Vacaciones Asturias/" >/dev/null
$A push "media/Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Images/." \
        "/sdcard/Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Images/" >/dev/null

# Un par de capturas de pantalla de verdad
$A shell mkdir -p /sdcard/Pictures/Screenshots
$A shell input keyevent 3; sleep 2
$A shell screencap -p /sdcard/Pictures/Screenshots/Screenshot_20261106-213012.png
$A shell am start -a android.settings.SETTINGS >/dev/null; sleep 3
$A shell screencap -p /sdcard/Pictures/Screenshots/Screenshot_20261102-101544.png
$A shell am start -a android.settings.DISPLAY_SETTINGS >/dev/null; sleep 3
$A shell screencap -p /sdcard/Pictures/Screenshots/Screenshot_20261029-174730.png
$A shell touch -t 202611062130 /sdcard/Pictures/Screenshots/Screenshot_20261106-213012.png
$A shell touch -t 202611021015 /sdcard/Pictures/Screenshots/Screenshot_20261102-101544.png
$A shell touch -t 202610291747 /sdcard/Pictures/Screenshots/Screenshot_20261029-174730.png
$A shell input keyevent 3

log "Indexando la galería"
$A shell content call --uri content://media --method scan_volume --arg external_primary >/dev/null 2>&1
sleep 5
count() { $A shell content query --uri content://media/external/images/media --projection _id 2>/dev/null | grep -c "Row:"; }
n=$(count); log "fotos indexadas: $n"
if [ "${n:-0}" -lt 40 ]; then
  log "Indexando una a una"
  $A shell 'find /sdcard/DCIM /sdcard/Pictures /sdcard/Android/media -type f \( -name "*.jpg" -o -name "*.png" \)' | tr -d '\r' |
    while IFS= read -r f; do
      $A shell "am broadcast -a android.intent.action.MEDIA_SCANNER_SCAN_FILE -d 'file://$f'" >/dev/null 2>&1
    done
  sleep 10
  log "fotos indexadas: $(count)"
fi

log "Instalando la app"
$A install -r "$APK"
$A shell cmd locale set-app-locales $PKG --locales es-ES >/dev/null 2>&1

$A shell am start -W -n $PKG/.MainActivity >/dev/null
shot 01_permiso 4

$A shell am force-stop $PKG
$A shell pm grant $PKG android.permission.READ_MEDIA_IMAGES
$A shell appops set $PKG MANAGE_MEDIA allow
$A shell am start -W -n $PKG/.MainActivity >/dev/null
shot 02_analizando 6

log "Esperando a que termine el análisis"
for _ in $(seq 1 60); do
  if dump && ! grep -q "Analizando" /tmp/ui.xml; then break; fi
  sleep 5
done
sleep 4
shot 03_fotos 1

tap text "Personas" && shot 04_personas
tap text "Documentos" && shot 05_documentos
tap text "Paisajes" && shot 05b_paisajes
tap text "Todas"

longpress desc "IMG_" 2
tap desc "IMG_" 3
tap desc "IMG_" 5
shot 06_seleccion
tap text "Mover a" && shot 07_mover_album
back
back

tap desc "IMG_" 1 && shot 08_visor 3
back

tap text "Álbumes" last && shot 09_albumes 3
tap text "Vacaciones" && shot 09b_album 3
back

tap text "Limpieza" last && shot 10_limpieza 3
tap text "Fotos repetidas" && shot 11_repetidas 3
tap text "Borrar" last && shot 12_confirmar_borrado 1
tap text "Borrar" last && shot 13_tras_borrar 2
back
tap text "Borrosas" && shot 14_borrosas 3
back
tap text "Imágenes de WhatsApp" && shot 15_whatsapp 3
back

tap text "Papelera" last && shot 16_papelera 3

log "Hecho"
ls -la "$OUT"
