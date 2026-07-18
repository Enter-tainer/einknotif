#!/usr/bin/env bash
# 构建 einknotif.apk (aapt2 + d8 + apksigner,无 gradle)
# 用法: bash einknotif/build.sh
set -e
cd "$(dirname "$0")"

BT="${LOCALAPPDATA:-$HOME/AppData/Local}/Android/Sdk/build-tools/36.0.0"
ANDROID="${LOCALAPPDATA:-$HOME/AppData/Local}/Android/Sdk/platforms/android-35/android.jar"
KS="$HOME/.android/debug.keystore"
OUT=build

[ -d "$BT" ] || { echo "build-tools 36.0.0 not found at $BT"; exit 1; }
[ -f "$ANDROID" ] || { echo "android-35 android.jar not found at $ANDROID"; exit 1; }
[ -f "$KS" ] || keytool -genkey -keystore "$KS" -storepass android -alias androiddebugkey -keypass android -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=Android Debug,O=Android,C=US"

rm -rf "$OUT" && mkdir -p "$OUT/cls" "$OUT/dexout" "$OUT/gen"

echo "[1/6] aapt2 compile res"
"$BT/aapt2.exe" compile --dir res -o "$OUT/res.zip"

echo "[2/6] aapt2 link -> R.java + base apk"
"$BT/aapt2.exe" link -I "$ANDROID" --manifest AndroidManifest.xml \
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
( cd "$OUT/dexout" && "$BT/aapt.exe" add ../app.apk classes.dex >/dev/null )
"$BT/zipalign.exe" -f -p 4 "$OUT/app.apk" "$OUT/aligned.apk"
"$BT/apksigner.bat" sign --ks "$KS" --ks-pass pass:android --key-pass pass:android --v2-signing-enabled true --out "$OUT/einknotif.apk" "$OUT/aligned.apk"

echo "OK: $OUT/einknotif.apk ($(stat -c%s $OUT/einknotif.apk) bytes)"
echo "install: adb -s A120G2502550000570 install -r $OUT/einknotif.apk"
