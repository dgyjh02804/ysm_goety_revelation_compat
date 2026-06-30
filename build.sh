#!/bin/bash
# YSM GoetyRevelation Compat - Build Script
# Requires: JDK 17 and internet connection (for first build only)

echo "============================================"
echo "YSM GoetyRevelation Compat Mod Build Script"
echo "============================================"
echo ""

# Find JDK 17
if [ -d "$HOME/AppData/Roaming/RIP/JavaRuntime/jre-v64-220420/jdk17" ]; then
    export JAVA_HOME="$HOME/AppData/Roaming/RIP/JavaRuntime/jre-v64-220420/jdk17"
    echo "Using Minecraft bundled JDK 17: $JAVA_HOME"
fi

if [ -z "$JAVA_HOME" ]; then
    echo "WARNING: JAVA_HOME not set. Attempting to use system Java..."
fi

echo ""
echo "Building mod..."
echo ""

# Run Gradle build
./gradlew build

if [ $? -ne 0 ]; then
    echo ""
    echo "BUILD FAILED! Check errors above."
    exit 1
fi

echo ""
echo "============================================"
echo "BUILD SUCCESS!"
echo "Output: build/libs/ysm_goety_revelation_compat-1.0.0.jar"
echo "============================================"
echo ""
echo "To use this mod:"
echo "1. Place ysm_goety_revelation_compat-1.0.0.jar in your Minecraft mods folder"
echo "2. Ensure these mods are also in mods folder:"
echo "   - Goety (required by GoetyRevelation)"
echo "   - GoetyRevelation-2.3.1.jar"
echo "   - ysm-2.6.5-forge+mc1.20.1-release.jar"
echo "3. Launch Minecraft 1.20.1 Forge"
echo ""
