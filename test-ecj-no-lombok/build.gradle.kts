plugins {
    id("io.github.themrmilchmann.ecj") version "0.2.0"
}
repositories {
    mavenCentral()
}

//configurations {
//    create("ecj")
//}

dependencies {
    annotationProcessor(project(":meta-processor"))

    compileOnly(project(":meta-api"))
    compileOnly("org.eclipse.jdt:ecj:3.37.0")

    ecj(project(":meta-api"))
    ecj(project(":meta-processor"))
    ecj("org.eclipse.jdt:ecj:3.37.0")

    testImplementation(platform("org.junit:junit-bom:5.9.1"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.mockito:mockito-junit-jupiter:5.17.0")
}

tasks.test {
    useJUnitPlatform()
}

tasks.compileJava {
    options.isDebug = true
    options.isFork = true
//    options.forkOptions.jvmArgs = listOf("-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=*:5005")
}

