plugins {
    id("io.github.themrmilchmann.ecj") version "0.2.0"
}

dependencies {
    annotationProcessor(project(":meta-processor"))

    compileOnly(project(":meta-api"))
    compileOnly("org.eclipse.jdt:ecj:3.37.0")

    ecj(project(":meta-api"))
    ecj(project(":meta-processor"))
    ecj("org.eclipse.jdt:ecj:3.37.0")

    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.mockito:mockito-junit-jupiter")
}

tasks.test {
    useJUnitPlatform()
}

tasks.compileJava {
    options.isDebug = true
    options.isFork = true
//    options.forkOptions.jvmArgs = listOf("-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=*:5005")
}

testing {
    suites {
        "test"(JvmTestSuite::class) {
            useJUnitJupiter()
        }
    }
}
