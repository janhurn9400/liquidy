# Magisk installation: NO own app code in the module — the script takes the already installed launcher app
# (Play Store / APK) and overlays it as a system app (priv-app). Updates keep coming the normal way.
PKG=io.github.janhurn9400.liquidy
APK=$(pm path $PKG 2>/dev/null | head -n1 | sed 's/^package://')
if [ -z "$APK" ] || [ ! -f "$APK" ]; then
  abort "! Please install the Liquidy app first."
fi
ui_print "- Launcher found: $APK"
mkdir -p "$MODPATH/system/priv-app/IosHome"
cp -f "$APK" "$MODPATH/system/priv-app/IosHome/IosHome.apk"
set_perm_recursive "$MODPATH/system/priv-app" 0 0 0755 0644
set_perm "$MODPATH/system/etc/permissions/privapp-permissions-ioshome.xml" 0 0 0644
set_perm "$MODPATH/system/etc/sysconfig/ioshome-hiddenapi.xml" 0 0 0644
ui_print "- Done. Please reboot."
