#!/bin/sh
# Builds out/tvmon.apk with the Android SDK in ~/claude/android-sdk (no Gradle).
set -e
cd "$(dirname "$0")"
SDK=/home/16931/claude/android-sdk
BT=$SDK/build-tools/34.0.0
JAR=$SDK/platforms/android-34/android.jar
rm -rf out && mkdir -p out/classes out/dex
javac -nowarn --release 8 -classpath "$JAR" -d out/classes src/dev/tvmon/*.java
$BT/d8 --lib "$JAR" --min-api 21 --output out/dex $(find out/classes -name '*.class')
$BT/aapt2 link -o out/base.apk -I "$JAR" --manifest AndroidManifest.xml --min-sdk-version 21 --target-sdk-version 30
cp out/base.apk out/unsigned.apk
(cd out/dex && zip -q -j ../unsigned.apk classes.dex)
$BT/zipalign -f 4 out/unsigned.apk out/aligned.apk
[ -f key.jks ] || keytool -genkeypair -keystore key.jks -storepass tvmonpass -keypass tvmonpass -alias tvmon -keyalg RSA -keysize 2048 -validity 9125 -dname "CN=tvmon" >/dev/null 2>&1
$BT/apksigner sign --ks key.jks --ks-pass pass:tvmonpass --key-pass pass:tvmonpass --out out/tvmon.apk out/aligned.apk
ls -l out/tvmon.apk
