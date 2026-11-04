// BusyCall's own additions to the app build, applied from app/build.gradle.kts with one line
// (UPSTREAM.md), so upstream's build file stays almost untouched.

dependencies {
    // QR setup codes (PLAN.md §5.1): ZXing's core decoder. Apache-2.0, pure Java, no transitive
    // dependencies, works offline, no Google services (guide §5). Only QRCodeReader is used.
    "implementation"("com.google.zxing:core:3.5.3")

    // JVM unit tests for BusyCall code (app/src/test/kotlin). They never touch Android or baresip.
    "testImplementation"("junit:junit:4.13.2")
}

// The offline setup page ships in assets/, copied from tools/setup-code/ on every build
// (AGENTS.md §6a: never hand-edit the asset copy). app/src/main/assets/.gitignore keeps the
// copy out of git.
val copySetupPage by tasks.registering(Copy::class) {
    from(rootProject.file("tools/setup-code/busycall-setup.html"))
    into(project.file("src/main/assets"))
}

// preBuild comes before every variant task; lint reads src/main/assets without depending on it.
tasks.matching { it.name == "preBuild" || it.name.startsWith("lint") }.configureEach {
    if (name != copySetupPage.name) dependsOn(copySetupPage)
}
