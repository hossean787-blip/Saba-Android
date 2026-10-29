# Independent Kotlin arithmetic verification — 2026-10-29

## Scope
A Kotlin standalone harness reproduced the current `MoneyArithmetic` implementation (the test runner did not clone the private repository). It was compiled with locally available `kotlinc` and executed using `java -jar`.

## Result
PASS 10/10 cases:
1. Exact multiplication
2. Exact sum
3. Zero quantity
4. Empty list
5. Maximum representable amount
6. Negative quantity rejected
7. Negative unit price rejected
8. Negative line amount rejected
9. Multiplication overflow rejected
10. Sum overflow rejected

Command pattern: `kotlinc MoneyArithmetic.kt Test.kt -include-runtime -d check.jar && java -jar check.jar`

## Limitations
- This is **not** an Android Gradle build, nor a JUnit or instrumented test execution.
- The standalone fixture mirrors the source and is not proof the entire app compiles.
- New JUnit boundary cases were committed to the main branch for future CI execution.
- Android SDK/Gradle are still unavailable in the execution environment, and CI has not reported a green build.
- Real Moadian connectivity, offline database transactions, tax rounding, and APK installation remain unverified.
