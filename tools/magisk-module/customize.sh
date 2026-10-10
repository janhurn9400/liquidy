# Magisk-Installation: KEIN eigener App-Code im Modul — das Skript nimmt die bereits installierte Launcher-App
# (Play Store / APK) und blendet sie als System-App (priv-app) ein. Updates kommen weiter über den normalen Weg.
PKG=io.github.janhurn9400.liquidy
APK=$(pm path $PKG 2>/dev/null | head -n1 | sed 's/^package://')
if [ -z "$APK" ] || [ ! -f "$APK" ]; then
  abort "! Bitte zuerst die iOS-Home-App installieren."
fi
ui_print "- Launcher gefunden: $APK"
mkdir -p "$MODPATH/system/priv-app/IosHome"
cp -f "$APK" "$MODPATH/system/priv-app/IosHome/IosHome.apk"
set_perm_recursive "$MODPATH/system/priv-app" 0 0 0755 0644
set_perm "$MODPATH/system/etc/permissions/privapp-permissions-ioshome.xml" 0 0 0644
set_perm "$MODPATH/system/etc/sysconfig/ioshome-hiddenapi.xml" 0 0 0644
ui_print "- Fertig. Bitte neu starten."
