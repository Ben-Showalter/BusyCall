// BusyCall's own additions to the app build, applied from app/build.gradle.kts with one line
// (UPSTREAM.md), so upstream's build file stays almost untouched.

dependencies {
    // JVM unit tests for BusyCall code (app/src/test/kotlin). They never touch Android or baresip.
    "testImplementation"("junit:junit:4.13.2")
}
