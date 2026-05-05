#!/bin/bash
# 🧪 Testing Setup - Automated Checklist
# Esegui questo script per verificare che tutto è configurato correttamente

echo "========================================"
echo "🧪 Testing Setup Verification"
echo "========================================"
echo ""

# Check 1: Gradle
echo "✓ Checking Gradle..."
if [ -f "gradlew" ]; then
    echo "  ✅ gradlew found"
else
    echo "  ❌ gradlew NOT found"
    exit 1
fi

# Check 2: Test files
echo "✓ Checking test files..."
if [ -d "app/src/test/java/com/emabuia/pokevault" ]; then
    echo "  ✅ Unit test directory exists"
    TEST_COUNT=$(find app/src/test -name "*Test.kt" | wc -l)
    echo "     Found $TEST_COUNT unit test files"
else
    echo "  ❌ Unit test directory NOT found"
fi

if [ -d "app/src/androidTest/java/com/emabuia/pokevault" ]; then
    echo "  ✅ Instrumented test directory exists"
    ANDROID_TEST_COUNT=$(find app/src/androidTest -name "*Test.kt" | wc -l)
    echo "     Found $ANDROID_TEST_COUNT instrumented test files"
else
    echo "  ❌ Instrumented test directory NOT found"
fi

# Check 3: GitHub Actions
echo "✓ Checking GitHub Actions..."
if [ -f ".github/workflows/android-tests.yml" ]; then
    echo "  ✅ Basic workflow found"
else
    echo "  ⚠️  Basic workflow NOT found"
fi

if [ -f ".github/workflows/android-advanced-tests.yml" ]; then
    echo "  ✅ Advanced workflow found"
else
    echo "  ⚠️  Advanced workflow NOT found"
fi

# Check 4: Documentation
echo "✓ Checking documentation..."
DOCS=(
    "TESTING_GUIDE.md"
    "QUICK_START_TESTING.md"
    "README_TESTING.md"
    "GITHUB_SETUP.md"
)

for doc in "${DOCS[@]}"; do
    if [ -f "$doc" ]; then
        echo "  ✅ $doc"
    else
        echo "  ❌ $doc NOT found"
    fi
done

# Check 5: Dependencies
echo "✓ Checking test dependencies in gradle..."
if grep -q "testImplementation(libs.junit)" app/build.gradle.kts; then
    echo "  ✅ JUnit dependency found"
else
    echo "  ❌ JUnit dependency NOT found"
fi

if grep -q "testImplementation(libs.mockk)" app/build.gradle.kts; then
    echo "  ✅ Mockk dependency found"
else
    echo "  ❌ Mockk dependency NOT found"
fi

if grep -q "androidTestImplementation(libs.androidx.espresso.core)" app/build.gradle.kts; then
    echo "  ✅ Espresso dependency found"
else
    echo "  ❌ Espresso dependency NOT found"
fi

# Check 6: Jacoco
echo "✓ Checking Jacoco setup..."
if grep -q "jacoco {" app/build.gradle.kts; then
    echo "  ✅ Jacoco configured"
else
    echo "  ⚠️  Jacoco NOT configured (coverage reports may not work)"
fi

echo ""
echo "========================================"
echo "✅ Setup Verification Complete!"
echo "========================================"
echo ""
echo "Next steps:"
echo "1. Run: ./gradlew testDebugUnitTest"
echo "2. Read: QUICK_START_TESTING.md"
echo "3. View: TESTING_GUIDE.md for detailed info"
echo ""
