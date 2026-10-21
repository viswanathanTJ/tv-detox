#!/bin/sh
# Recreates this TV setup on a new Android TV over adb (run from the laptop, on the same Wi-Fi).
#
#   sh setup/setup-new-tv.sh <TV_IP> [APK_DIR]
#
# APK_DIR (default: ./release) must contain TVHome-release.apk and TVMonitor-release.apk.
# Build them first, or use the release files you already have. Nothing is uninstalled except the
# home screen (com.ansub.tvhome) when it must be replaced with a copy signed by a different key.
# Apps are only turned off, never deleted. undo: see README.md.
set -e
TARGET="$1"; APK_DIR="${2:-$(dirname "$0")/../release}"
[ -n "$TARGET" ] || { echo "Usage: sh setup/setup-new-tv.sh <TV_IP> [APK_DIR]" >&2; exit 1; }
case "$TARGET" in *:*) TV=$TARGET ;; *) TV=$TARGET:5555 ;; esac
adb_tv() { adb -s "$TV" "$@"; }
sh_tv() { adb -s "$TV" shell "$@" | tr -d '\r'; }

command -v adb >/dev/null 2>&1 || { echo "adb is missing. Install android platform-tools first." >&2; exit 1; }
[ -f "$APK_DIR/TVHome-release.apk" ] && [ -f "$APK_DIR/TVMonitor-release.apk" ] || {
  echo "Missing APKs in $APK_DIR (need TVHome-release.apk and TVMonitor-release.apk)." >&2; exit 1; }

echo "Connecting to $TV ..."
adb connect "$TV" >/dev/null 2>&1 || true
case "$(adb -s "$TV" get-state 2>&1 || true)" in
  device) ;;
  *unauthorized*) echo "The TV is asking \"Allow debugging?\". Press Allow (tick Always allow), then run this again." >&2; exit 1 ;;
  *) echo "Can't reach $TV. Turn on USB debugging (Developer options) and check the Wi-Fi." >&2; exit 1 ;;
esac
echo "Connected to $(sh_tv getprop ro.product.model)."

echo "1. Installing TV Monitor"
adb_tv install -r "$APK_DIR/TVMonitor-release.apk" | tail -1

echo "2. Installing the home screen"
# The home screen's copy of its apps and shortcuts; an uninstall would delete it, so save it first.
CONFIG=/sdcard/Android/data/com.ansub.tvhome/files/home-config.txt
SAVED=$(mktemp)
adb_tv pull "$CONFIG" "$SAVED" >/dev/null 2>&1 || rm -f "$SAVED"
if ! out=$(adb_tv install -r "$APK_DIR/TVHome-release.apk" 2>&1); then
  case "$out" in
    *UPDATE_INCOMPATIBLE*) echo "  Replacing an older copy signed with another key (your home screen apps are kept)."
      adb_tv uninstall com.ansub.tvhome >/dev/null 2>&1 || true
      adb_tv install "$APK_DIR/TVHome-release.apk" | tail -1
      if [ -f "$SAVED" ]; then
        sh_tv mkdir -p "${CONFIG%/*}" && adb_tv push "$SAVED" "$CONFIG" >/dev/null || echo "  Couldn't put the home screen apps back." >&2
      fi ;;
    *) echo "$out" >&2; exit 1 ;;
  esac
else
  echo "$out" | tail -1
fi
sh_tv cmd role add-role-holder --user 0 android.app.role.HOME com.ansub.tvhome >/dev/null 2>&1 || true
sh_tv cmd package set-home-activity com.ansub.tvhome/.MainActivity >/dev/null 2>&1 || true
sh_tv cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME | tail -1

# The weather key, from weather.key next to this repo's build.sh, if there is one.
KEYFILE="$(dirname "$0")/../weather.key"
if [ -f "$KEYFILE" ]; then
  sh_tv mkdir -p "${CONFIG%/*}" && adb_tv push "$KEYFILE" "${CONFIG%/*}/weather.key" >/dev/null || echo "  Couldn't copy weather.key." >&2
fi

echo "3. Letting the home screen change TV DNS (AdGuard tile)"
sh_tv pm grant com.ansub.tvhome android.permission.WRITE_SECURE_SETTINGS >/dev/null 2>&1 || echo "  (could not grant; the AdGuard tile will say so)"

echo "4. Turning off apps and TV services that are not needed (not uninstalled)"
for p in ch.protonvpn.android com.android.htmlviewer com.android.printspooler com.anilab.android.tv \
  com.google.android.backdrop com.google.android.feedback com.google.android.play.games \
  com.google.android.tvlauncher com.google.android.tvrecommendations com.google.android.videos \
  com.google.android.youtube.tv com.google.android.youtube.tvmusic com.mitv.tvhome.atv \
  com.mitv.tvhome.michannel com.mitv.tvhome.mitvplus com.miui.tv.analytics com.sonyliv \
  com.spotify.tv.android com.tcl.browser com.xiaomi.mitv.manualhelp com.xiaomi.mitv.tvmanager \
  com.xiaomi.mitv.updateservice com.xiaomi.statistic nextapp.fx; do
  if sh_tv pm list packages "$p" | grep -qx "package:$p"; then
    sh_tv pm disable-user --user 0 "$p" >/dev/null 2>&1 && echo "  off: $p" || echo "  could not turn off: $p"
  fi
done

echo "5. Screen: animations off, screensaver off, sleep after 5 minutes"
sh_tv settings put global window_animation_scale 0
sh_tv settings put global transition_animation_scale 0
sh_tv settings put global animator_duration_scale 0
sh_tv settings put secure screensaver_enabled 0
sh_tv settings put secure sleep_timeout 300000
sh_tv settings put system screen_off_timeout 300000

echo "6. AdGuard DNS (on)"
sh_tv settings put global private_dns_specifier dns.adguard-dns.com
sh_tv settings put global private_dns_mode hostname

echo "7. Background activity blocked for Stremio and SmartTube (apps still open normally)"
for p in com.stremio.one org.smarttube.stable; do
  if sh_tv pm list packages "$p" | grep -qx "package:$p"; then
    sh_tv cmd appops set "$p" RUN_ANY_IN_BACKGROUND ignore
    sh_tv cmd appops set "$p" RUN_IN_BACKGROUND ignore
  fi
done

echo "Done. Press Home on the remote to see the new home screen."
echo "Apps you install from the store (Netflix, Hotstar, Prime Video...) are not part of this script."
