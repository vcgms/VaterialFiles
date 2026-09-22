#!/usr/bin/env bash
#
# Android App 构建脚本
# 用法：
#   ./build.sh            # 构建 Debug
#   ./build.sh release    # 构建 Release（需先配置 signing.properties）
#   JAVA_HOME=/path/to/jdk ./build.sh   # 指定 JDK
#
# 每次执行会先将 app/build.gradle 的 versionCode 递增 1，
# 并将 versionName（x.y.z）的 z 段同步为新的 versionCode；
# 构建成功后，APK 以 名称_版本号_日期时间.apk 拷贝到 apks/ 目录。

set -euo pipefail

PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$PROJECT_DIR"

APP_NAME="VaterialFiles"
OUT_DIR="$PROJECT_DIR/apks"

# ---------- JDK：默认使用 Android Studio 自带 JBR，可被 JAVA_HOME 覆盖 ----------
if [ -z "${JAVA_HOME:-}" ]; then
    STUDIO_JBR="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
    if [ -x "$STUDIO_JBR/bin/java" ]; then
        export JAVA_HOME="$STUDIO_JBR"
    else
        echo "错误：未设置 JAVA_HOME，且未找到 Android Studio 自带 JDK" >&2
        echo "请设置 JAVA_HOME 指向 JDK 17+，例如：" >&2
        echo "  export JAVA_HOME=\"$STUDIO_JBR\"" >&2
        exit 1
    fi
fi
export JAVA_HOME
echo "==> JAVA_HOME: $JAVA_HOME"

# ---------- 版本号自增：versionCode +1，versionName 的 z 段与 versionCode 一致 ----------
BUILD_GRADLE="$PROJECT_DIR/app/build.gradle"
if [ ! -f "$BUILD_GRADLE" ]; then
    echo "错误：未找到 $BUILD_GRADLE" >&2
    exit 1
fi

OLD_CODE="$(grep -oE 'versionCode[[:space:]]+[0-9]+' "$BUILD_GRADLE" \
    | head -n 1 | grep -oE '[0-9]+' | tail -n 1)"
OLD_NAME="$(grep -oE "versionName[[:space:]]+'[^']*'" "$BUILD_GRADLE" \
    | head -n 1 | sed -E "s/.*'([^']*)'.*/\1/")"
if [ -z "$OLD_CODE" ] || [ -z "$OLD_NAME" ]; then
    echo "错误：无法从 $BUILD_GRADLE 解析 versionCode / versionName" >&2
    exit 1
fi

NEW_CODE=$((OLD_CODE + 1))
# 保留 versionName 的 x.y. 前缀，z 段替换为新 versionCode
NAME_PREFIX="$(printf '%s' "$OLD_NAME" | sed -E 's/([0-9]+\.[0-9]+\.).*$/\1/')"
if [ -z "$NAME_PREFIX" ] || [ "$NAME_PREFIX" = "$OLD_NAME" ]; then
    # 非 x.y.z 格式时的兜底：直接追加版本号
    NEW_NAME="${OLD_NAME}.${NEW_CODE}"
else
    NEW_NAME="${NAME_PREFIX}${NEW_CODE}"
fi

sed -i '' -E "s/(versionCode[[:space:]]+)[0-9]+/\1${NEW_CODE}/" "$BUILD_GRADLE"
sed -i '' -E "s/(versionName[[:space:]]+)'[^']*'/\1'${NEW_NAME}'/" "$BUILD_GRADLE"
echo "==> 版本号：${OLD_NAME}(${OLD_CODE}) -> ${NEW_NAME}(${NEW_CODE})"

# ---------- 构建 ----------
VARIANT="debug"
if [ "${1:-}" = "release" ]; then
    VARIANT="release"
fi

if [ "$VARIANT" = "release" ]; then
    # Release 签名在 signing.gradle / signing.properties 中配置
    TASK=":app:assembleRelease"
else
    TASK=":app:assembleDebug"
fi

echo "==> 开始构建（${VARIANT}）..."
./gradlew "$TASK"

# ---------- 收集产物并拷贝 ----------
APK_DIR="$PROJECT_DIR/app/build/outputs/apk/$VARIANT"
SRC_APK="$(ls "$APK_DIR"/*.apk 2>/dev/null | head -n 1 || true)"

if [ -z "$SRC_APK" ]; then
    echo "错误：构建完成但未找到 APK 产物：$APK_DIR" >&2
    exit 1
fi

# 从 output-metadata.json 读取版本号（格式："versionName": "x.y.z"，容忍空格）
VERSION=""
METADATA="$APK_DIR/output-metadata.json"
if [ -f "$METADATA" ]; then
    VERSION="$(grep -oE '"versionName"[[:space:]]*:[[:space:]]*"[^"]*"' "$METADATA" \
        | head -n 1 \
        | sed -E 's/.*"versionName"[[:space:]]*:[[:space:]]*"([^"]*)".*/\1/' \
        || true)"
fi
if [ -z "$VERSION" ]; then
    VERSION="unknown"
fi

TIMESTAMP="$(date +%Y%m%d_%H%M%S)"
DEST_NAME="${APP_NAME}_${VERSION}_${TIMESTAMP}.apk"

mkdir -p "$OUT_DIR"
# 删除 apks 目录内的旧安装包，仅保留当前构建产物
find "$OUT_DIR" -maxdepth 1 -type f -name "*.apk" -delete
cp "$SRC_APK" "$OUT_DIR/$DEST_NAME"

echo "==> 构建成功"
echo "    源文件：$SRC_APK"
echo "    已清理旧包并拷贝：$OUT_DIR/$DEST_NAME"
