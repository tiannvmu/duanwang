#!/usr/bin/env bash
set -e

PROJECT_DIR="/storage/emulated/0/杂物区/联网自控"
BUILD_DIR="/tmp/duanwang_build"
SDK_DIR="/tmp/android-sdk"
SDKMANAGER="$SDK_DIR/cmdline-tools/latest/bin/sdkmanager"
JAVAC_ANDROID_JAR="$SDK_DIR/platforms/android-35/android.jar"
ANDROID_JAR="/usr/lib/android-sdk/platforms/android-23/android.jar"
BUILD_TOOLS="$SDK_DIR/build-tools/35.0.0"
AAPT2_BIN="/usr/bin/aapt2"
AAPT_BIN="/usr/bin/aapt"
ZIPALIGN_BIN="/usr/bin/zipalign"
R8_JAR="/tmp/r8.jar"
OUT_DIR="$PROJECT_DIR/已编译APK"
OUT_APK="$OUT_DIR/断网自控.apk"

if [ ! -s "$JAVAC_ANDROID_JAR" ]; then
  mkdir -p "$SDK_DIR/cmdline-tools/latest"
  if [ ! -x "$SDKMANAGER" ]; then
    cd /tmp || exit 1
    curl -L -o commandlinetools.zip "https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip"
    rm -rf cmdline-tools
    unzip -q commandlinetools.zip
    cp -r /tmp/cmdline-tools/* "$SDK_DIR/cmdline-tools/latest/"
  fi
  yes | "$SDKMANAGER" --sdk_root="$SDK_DIR" "platforms;android-35" "build-tools;35.0.0" "platform-tools"
fi

if [ ! -s "$R8_JAR" ]; then
  curl -L -o "$R8_JAR" "https://dl.google.com/dl/android/maven2/com/android/tools/r8/8.5.35/r8-8.5.35.jar"
fi

rm -rf "$BUILD_DIR"
mkdir -p "$BUILD_DIR/src" "$BUILD_DIR/out/classes" "$BUILD_DIR/out/dex" "$BUILD_DIR/out/res" "$BUILD_DIR/out/apk" "$OUT_DIR"
cp -r "$PROJECT_DIR/app/src/main/"* "$BUILD_DIR/src/"

cd "$BUILD_DIR" || exit 1
javac -encoding UTF-8 -source 8 -target 8 -cp "$JAVAC_ANDROID_JAR" -d out/classes $(find src/java -name '*.java')
java -cp "$R8_JAR" com.android.tools.r8.D8 --output out/dex --lib "$JAVAC_ANDROID_JAR" $(find out/classes -name '*.class')
"$AAPT2_BIN" compile --dir src/res -o out/res/resources.zip
"$AAPT2_BIN" link -o out/apk/unsigned.apk -I "$ANDROID_JAR" --manifest src/AndroidManifest.xml -A src/assets --min-sdk-version 23 --target-sdk-version 28 out/res/resources.zip
cd out/dex || exit 1
"$AAPT_BIN" add ../apk/unsigned.apk classes.dex
cd "$BUILD_DIR" || exit 1
keytool -genkeypair -keystore out/debug.keystore -alias androiddebugkey -keyalg RSA -keysize 2048 -validity 10000 -storepass android -keypass android -dname 'CN=Android Debug,O=Android,C=US'
jarsigner -sigalg SHA256withRSA -digestalg SHA-256 -keystore out/debug.keystore -storepass android -keypass android out/apk/unsigned.apk androiddebugkey
"$ZIPALIGN_BIN" -f 4 out/apk/unsigned.apk out/apk/duanwangzikong.apk
cp out/apk/duanwangzikong.apk "$OUT_APK"
ls -lh "$OUT_APK"