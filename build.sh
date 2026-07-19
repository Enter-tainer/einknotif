#!/usr/bin/env bash
# 构建 einknotif.apk (aapt2 + d8 + apksigner,无 gradle)
# 用法: bash einknotif/build.sh
# 跨平台: Windows(本地)用 build-tools 36.0.0 自带的 .exe;Linux(CI)用同名无后缀二进制。
set -e
cd "$(dirname "$0")"

# Android SDK 根目录: 优先 LOCALAPPDATA(Windows),否则 $HOME/Android/Sdk(Linux/Mac)。
SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-${LOCALAPPDATA:-$HOME/AppData/Local}/Android/Sdk}}"
[ -d "$SDK" ] || SDK="$HOME/Android/Sdk"
BT="$SDK/build-tools/36.0.0"
ANDROID="$SDK/platforms/android-35/android.jar"
KS="${KEYSTORE:-$HOME/.android/debug.keystore}"

[ -d "$BT" ] || { echo "build-tools 36.0.0 not found at $BT"; exit 1; }
[ -f "$ANDROID" ] || { echo "android-35 android.jar not found at $ANDROID"; exit 1; }

# 解析 build-tools 下每个工具的可执行名: Windows 上是 aapt2.exe,Linux/Mac 上是 aapt2。
exe() {
  local name="$1"
  if [ -x "$BT/$name.exe" ]; then printf '%s' "$BT/$name.exe"
  else printf '%s' "$BT/$name"; fi
}
AAPT2=$(exe aapt2)
AAPT=$(exe aapt)
ZIPALIGN=$(exe zipalign)
# apksigner: Windows 上是 apksigner.bat,Linux 上是 apksigner(shell 脚本)。
if [ -f "$BT/apksigner.bat" ]; then APKSIGNER="$BT/apksigner.bat"
elif [ -f "$BT/apksigner" ]; then APKSIGNER="$BT/apksigner"
else echo "apksigner not found in $BT"; exit 1; fi

# keystore: 没有就生成一个 debug keystore(先确保父目录存在)。
if [ ! -f "$KS" ]; then
  mkdir -p "$(dirname "$KS")"
  keytool -genkey -keystore "$KS" -storepass android -alias androiddebugkey \
    -keypass android -keyalg RSA -keysize 2048 -validity 10000 \
    -dname "CN=Android Debug,O=Android,C=US"
fi

OUT=build
rm -rf "$OUT" && mkdir -p "$OUT/cls" "$OUT/dexout" "$OUT/gen"

echo "[1/6] aapt2 compile res"
"$AAPT2" compile --dir res -o "$OUT/res.zip"

echo "[2/6] aapt2 link -> R.java + base apk"
"$AAPT2" link -I "$ANDROID" --manifest AndroidManifest.xml \
  --java "$OUT/gen" -o "$OUT/base.apk" \
  --min-sdk-version 26 --target-sdk-version 34 "$OUT/res.zip"

echo "[3/6] javac (含生成的 R.java)"
javac -encoding UTF-8 -source 8 -target 8 -classpath "$ANDROID" \
  -sourcepath "$OUT/gen" -d "$OUT/cls" \
  src/com/hweink/einknotif/*.java "$OUT/gen/com/hweink/einknotif/R.java"

echo "[4/6] jar"
( cd "$OUT/cls" && jar cf ../classes.jar com/ )

echo "[5/6] d8 -> dex"
java -cp "$BT/lib/d8.jar" com.android.tools.r8.D8 --output "$OUT/dexout" "$OUT/classes.jar" --min-api 26

echo "[6/6] 打包 dex 进 base.apk + 签名"
cp "$OUT/base.apk" "$OUT/app.apk"
( cd "$OUT/dexout" && "$AAPT" add ../app.apk classes.dex >/dev/null )
"$ZIPALIGN" -f -p 4 "$OUT/app.apk" "$OUT/aligned.apk"
"$APKSIGNER" sign --ks "$KS" --ks-pass pass:android --key-pass pass:android --v2-signing-enabled true --out "$OUT/einknotif.apk" "$OUT/aligned.apk"

echo "OK: $OUT/einknotif.apk ($(stat -c%s "$OUT/einknotif.apk" 2>/dev/null || stat -f%z "$OUT/einknotif.apk") bytes)"
