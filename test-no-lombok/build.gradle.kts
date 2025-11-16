plugins {
    id("java")
}

repositories {
    mavenCentral()
}

dependencies {
    annotationProcessor(project(":meta-processor"))
    compileOnly(project(":meta-api"))

    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.mockito:mockito-junit-jupiter")
}

tasks.test {
    useJUnitPlatform()
}

testing {
    suites {
        "test"(JvmTestSuite::class) {
            useJUnitJupiter()
        }
    }
}
