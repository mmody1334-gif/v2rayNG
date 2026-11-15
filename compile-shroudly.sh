#!/bin/bash
set -o errexit
set -o pipefail
set -o nounset
# Set magic variables for current file & dir
__dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
__file="${__dir}/$(basename "${BASH_SOURCE[0]}")"
__base="$(basename "${__file}" .sh)"
if [[ ! -d ${NDK_HOME:-} ]]; then
  echo "Android NDK: NDK_HOME not found. please set env \$NDK_HOME"
  exit 1
fi
TMPDIR=$(mktemp -d)
clear_tmp () {
  rm -rf "$TMPDIR"
}
trap 'echo -e "Aborted, error $? in command: $BASH_COMMAND"; trap ERR; clear_tmp; exit 1' ERR INT

ABIS="armeabi-v7a arm64-v8a x86 x86_64"

mkdir -p "$TMPDIR/jni/core"
cp "$__dir/third_party/Shroudly/android/app/src/main/cpp/engine_jni.c" "$TMPDIR/jni/"
cp "$__dir/third_party/Shroudly/core/src/"* "$TMPDIR/jni/core/"

cat > "$TMPDIR/jni/Android.mk" << 'EOF'
LOCAL_PATH := $(call my-dir)
SHROUDLY_CORE := core

include $(CLEAR_VARS)
LOCAL_MODULE := shroudlycore_jni
LOCAL_SRC_FILES := engine_jni.c \
    $(addprefix $(SHROUDLY_CORE)/,$(notdir $(sort $(wildcard $(LOCAL_PATH)/$(SHROUDLY_CORE)/*.c))))
LOCAL_C_INCLUDES := $(LOCAL_PATH)/$(SHROUDLY_CORE)
LOCAL_CFLAGS := -std=c11 -O2 -Wall -Wextra -D_GNU_SOURCE -DSHROUDLY_NO_MAIN \
    -fvisibility=hidden -ffunction-sections -fdata-sections \
    -fstack-protector-strong -D_FORTIFY_SOURCE=2
LOCAL_LDFLAGS := -Wl,--gc-sections -Wl,-z,relro -Wl,-z,now \
    -Wl,-z,max-page-size=16384 -Wl,-z,common-page-size=16384
include $(BUILD_SHARED_LIBRARY)
EOF

pushd "$TMPDIR"

NDK_BUILD="$NDK_HOME/ndk-build"
if [[ ! -f "$NDK_BUILD" && -f "$NDK_HOME/ndk-build.cmd" ]]; then
  NDK_BUILD="$NDK_HOME/ndk-build.cmd"
fi

"$NDK_BUILD" \
    NDK_PROJECT_PATH=. \
    APP_BUILD_SCRIPT=jni/Android.mk \
    "APP_ABI=$ABIS" \
    APP_PLATFORM=android-24 \
    APP_SUPPORT_FLEXIBLE_PAGE_SIZES=true \
    NDK_LIBS_OUT="$TMPDIR/libs" \
    NDK_OUT="$TMPDIR/obj"

mkdir -p "$__dir/libs"
cp -r "$TMPDIR/libs/"* "$__dir/libs/"

popd
clear_tmp
trap - ERR INT
echo "Shroudly native build completed successfully."
