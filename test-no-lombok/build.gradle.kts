plugins {
    id("java")
}

repositories {
    mavenCentral()
}

dependencies {
    annotationProcessor(project(":meta-processor"))
    compileOnly(project(":meta-api"))

    testImplementation(platform("org.junit:junit-bom:5.9.1"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.mockito:mockito-junit-jupiter:5.17.0")
}

tasks.test {
    useJUnitPlatform()
}
