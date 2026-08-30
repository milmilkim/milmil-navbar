#!/bin/bash
# Gradle 없이 APK를 직접 빌드한다: aapt2 -> javac -> d8 -> zipalign -> apksigner
set -e

SDK=/opt/homebrew/share/android-commandlinetools
BT=$SDK/build-tools/36.1.0
PLATFORM=$SDK/platforms/android-34/android.jar
OUT=build
KEYSTORE=$HOME/.android/debug.keystore

cd "$(dirname "$0")"
rm -rf $OUT
mkdir -p $OUT/gen $OUT/classes

# 디버그 서명키가 없으면 만든다
if [ ! -f "$KEYSTORE" ]; then
  mkdir -p "$(dirname "$KEYSTORE")"
  keytool -genkeypair -keystore "$KEYSTORE" -storepass android -keypass android \
    -alias androiddebugkey -keyalg RSA -keysize 2048 -validity 10000 \
    -dname "CN=Android Debug,O=Android,C=US" >/dev/null 2>&1
fi

echo "[1/5] 리소스 컴파일"
$BT/aapt2 compile --dir res -o $OUT/res.zip

echo "[2/5] 리소스 링크"
$BT/aapt2 link -o $OUT/unsigned.apk \
  -I $PLATFORM \
  --manifest AndroidManifest.xml \
  $OUT/res.zip \
  --java $OUT/gen \
  --min-sdk-version 30 \
  --target-sdk-version 33

echo "[3/5] 자바 컴파일"
javac -source 17 -target 17 -nowarn -encoding UTF-8 \
  -classpath $PLATFORM \
  -d $OUT/classes \
  $(find src $OUT/gen -name '*.java')

echo "[4/5] dex 변환"
$BT/d8 --lib $PLATFORM --min-api 30 --output $OUT $(find $OUT/classes -name '*.class')
(cd $OUT && zip -q -j unsigned.apk classes.dex)

echo "[5/5] 정렬 + 서명"
$BT/zipalign -f -p 4 $OUT/unsigned.apk $OUT/aligned.apk
$BT/apksigner sign --ks "$KEYSTORE" --ks-pass pass:android --key-pass pass:android \
  --out $OUT/navbar.apk $OUT/aligned.apk

echo "완료: $OUT/navbar.apk"
