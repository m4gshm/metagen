plugins {
    id("java")
}

repositories {
    mavenCentral()
}

dependencies {
    annotationProcessor(project(":"))
    implementation(project(":"))

    testImplementation(platform("org.junit:junit-bom:5.9.1"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.mockito:mockito-junit-jupiter:5.11.0")
}

tasks.test {
    useJUnitPlatform()
}

tasks.withType<JavaCompile> {
    this.options.compilerArgs.addAll(listOf("-Ameta.createClassFiles=true"))
}
