#!/bin/bash
#
# Install the Android system image the virtual phones boot from.
#
# sdkmanager starts the 1.8 GB download again after every failure. This script
# downloads the same file with curl, which continues where it stopped, checks
# it, and unpacks it where the SDK expects it.
#
# Usage:  sh image.sh
#
# Run it again after a failure. It continues from what is already downloaded.
# Requires: curl, unzip, shasum

. "$(dirname "$0")/common.sh"

# --- The image, as listed in Google's SDK repository ---

URL="https://dl.google.com/android/repository/sys-img/google_apis/arm64-v8a-35_r09.zip"
SIZE_BYTES=1778933980
SHA1="16f5bceca236b2737008977c4aaf826e46a8de7d"
MAXIMUM_ATTEMPTS=200

# --- Paths ---

need curl unzip shasum
find_sdk

TARGET=$(image_folder)
PARENT=$(dirname "$TARGET")
CACHE="$HOME/.cache/pocora"
ZIP="$CACHE/$(basename "$URL")"

if [ -f "$TARGET/system.img" ] && [ -f "$TARGET/package.xml" ]; then
    echo "Already installed: $TARGET"
    exit 0
fi

# --- 1. Download, continuing from what is already there ---

mkdir -p "$CACHE"
size() { [ -f "$ZIP" ] && stat -f%z "$ZIP" 2>/dev/null || echo 0; }

attempt=0
while [ "$(size)" -lt "$SIZE_BYTES" ]; do
    attempt=$((attempt + 1))
    [ "$attempt" -le "$MAXIMUM_ATTEMPTS" ] || fail "gave up after $MAXIMUM_ATTEMPTS attempts. Run again to continue."
    echo "Downloading, attempt $attempt, have $(($(size) / 1048576)) of $((SIZE_BYTES / 1048576)) MB"
    curl -L -C - --fail --connect-timeout 30 --speed-time 60 --speed-limit 1024 -o "$ZIP" "$URL" || sleep 3
done

# --- 2. Check ---

echo "Checking the download..."
if [ "$(shasum -a 1 "$ZIP" | cut -d' ' -f1)" != "$SHA1" ]; then
    rm -f "$ZIP"
    fail "the downloaded file is damaged and was removed. Run again to download it afresh."
fi

# --- 3. Unpack ---

echo "Unpacking..."
mkdir -p "$PARENT"
rm -rf "$TARGET"
unzip -q -o "$ZIP" -d "$PARENT"
[ -f "$TARGET/system.img" ] || fail "unpacking failed."

# --- 4. Tell the SDK the package is installed ---

LICENSE='<license id="android-sdk-license" type="text">Android Software Development Kit License Agreement</license>'

cat > "$TARGET/package.xml" <<XML
<?xml version="1.0" encoding="UTF-8" standalone="yes"?><ns2:repository xmlns:ns2="http://schemas.android.com/repository/android/common/02" xmlns:ns14="http://schemas.android.com/sdk/android/repo/sys-img2/05">$LICENSE<localPackage path="$IMAGE_PACKAGE" obsolete="false"><type-details xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:type="ns14:sysImgDetailsType"><api-level>35</api-level><extension-level>13</extension-level><base-extension>true</base-extension><tag><id>google_apis</id><display>Google APIs</display></tag><vendor><id>google</id><display>Google Inc.</display></vendor><abi>arm64-v8a</abi><abis>arm64-v8a</abis></type-details><revision><major>9</major></revision><display-name>Google APIs ARM 64 v8a System Image</display-name><uses-license ref="android-sdk-license"/><dependencies><dependency path="emulator"><min-revision><major>33</major><minor>1</minor><micro>24</micro></min-revision></dependency></dependencies></localPackage></ns2:repository>
XML

rm -f "$ZIP"
echo "Done: $TARGET"
