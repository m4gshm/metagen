plugins {
    id("java")
}

dependencies {
//    annotationProcessor("io.github.m4gshm:meta-processor:0.0.1-rc7")
//    implementation("io.github.m4gshm:meta-api:0.0.1-rc7")

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
