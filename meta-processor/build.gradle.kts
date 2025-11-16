dependencies {
    api(project(":meta-api"))
    api("io.github.jbock-java:javapoet:1.15")
    runtimeOnly("io.github.jbock-java:javapoet:1.15")

    testImplementation("org.junit.jupiter:junit-jupiter")
}
