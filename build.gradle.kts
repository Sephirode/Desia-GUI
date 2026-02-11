plugins {
    id("java")
    application
    id("org.openjfx.javafxplugin") version "0.0.14"
}

group = "desia"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

application {
    // JavaFX entry point
    mainClass.set("desia.gui.FxApp")
}

javafx {
    // Use JavaFX 17 to match typical JDK 17 setups
    version = "17.0.10"
    // Audio uses javafx.media (Media/MediaPlayer)
    modules = listOf("javafx.controls", "javafx.graphics", "javafx.media")
}

dependencies {

    implementation ("com.fasterxml.jackson.core:jackson-databind:2.17.1")

    testImplementation(platform("org.junit:junit-bom:5.9.1"))
    testImplementation("org.junit.jupiter:junit-jupiter")

    compileOnly("org.projectlombok:lombok:1.18.32")
    annotationProcessor("org.projectlombok:lombok:1.18.32")

    testCompileOnly("org.projectlombok:lombok:1.18.32")
    testAnnotationProcessor("org.projectlombok:lombok:1.18.32")
}


tasks.test {
    useJUnitPlatform()
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
}
