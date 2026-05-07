#!/bin/bash
set -e


NDK_ROOT="/mnt/localdisk3/Anonymous/Android/android-ndk-r27d"
API_LEVEL=21
PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/src"
GO_SRC_DIR="$PROJECT_DIR/go"
C_SRC_DIR="$PROJECT_DIR/c"
OUTPUT_BASE_DIR="$PROJECT_DIR/android-libs"


ABIS=("arm64-v8a" "armeabi-v7a" "x86_64")


get_toolchain_info() {
    case "$1" in
        "arm64-v8a")
            echo "arm64 aarch64"
            ;;
        "armeabi-v7a")
            echo "arm armv7a"
            ;;
        "x86_64")
            echo "amd64 x86_64"
            ;;
        *)
            echo "❌ Unsupported ABI: $1" >&2
            exit 1
            ;;
    esac
}


build_abi() {
    local abi=$1
    read -r GOARCH NDK_ARCH <<< $(get_toolchain_info "$abi")

    local OUTPUT_DIR="$OUTPUT_BASE_DIR/$abi"
    mkdir -p "$OUTPUT_DIR"

    echo "🚀 Building for ABI: $abi (GOARCH=$GOARCH, NDK=$NDK_ARCH)"

    # === STEP 1: Go c-shared  ===  
    cd "$GO_SRC_DIR"

    
    if [ "$abi" = "armeabi-v7a" ]; then
        export CC="$NDK_ROOT/toolchains/llvm/prebuilt/linux-x86_64/bin/${NDK_ARCH}-linux-androideabi$API_LEVEL-clang"
        export CGO_CFLAGS="-march=armv7-a -mfpu=vfpv3-d16 -mfloat-abi=softfp"
    else
        export CC="$NDK_ROOT/toolchains/llvm/prebuilt/linux-x86_64/bin/${NDK_ARCH}-linux-android$API_LEVEL-clang"
        unset CGO_CFLAGS
    fi

    GOOS=android GOARCH="$GOARCH" CGO_ENABLED=1 \
        go build -buildmode=c-shared -o "$OUTPUT_DIR/libgo_kcp_client.so" .

    # === STEP 2: compile C glue layer ===
    cd "$PROJECT_DIR"

    if [ "$abi" = "armeabi-v7a" ]; then
        CLANG_BIN="${NDK_ARCH}-linux-androideabi$API_LEVEL-clang"
    else
        CLANG_BIN="${NDK_ARCH}-linux-android$API_LEVEL-clang"
    fi

    "$NDK_ROOT/toolchains/llvm/prebuilt/linux-x86_64/bin/$CLANG_BIN" \
        -I"$OUTPUT_DIR" \
        -I"$C_SRC_DIR" \
        -fPIC -shared \
        -o "$OUTPUT_DIR/libkcp_client.so" \
        "$C_SRC_DIR/clientstub_helper.c" \
        "$C_SRC_DIR/serverstub_helper.c" \
        "$C_SRC_DIR/consumer_helper.c" \
        "$C_SRC_DIR/dupclient.c" \
        "$C_SRC_DIR/jni_helper.c" \
        -L"$OUTPUT_DIR" \
        -lgo_kcp_client \
        -llog

    echo "  ✅ Done: $OUTPUT_DIR/libkcp_client.so"
}


echo "🔧 Starting multi-ABI build for: ${ABIS[*]}"

for abi in "${ABIS[@]}"; do
    build_abi "$abi"
done

echo ""
echo "✅ All builds completed!"
echo "📁 Output directories:"
for abi in "${ABIS[@]}"; do
    echo "   $OUTPUT_BASE_DIR/$abi/libkcp_client.so"
done