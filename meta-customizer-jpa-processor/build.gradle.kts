dependencies {
    api(project(":meta-customizer-jpa-api"))
    api(project(":meta-processor"))

    implementation("io.github.jbock-java:javapoet:1.15")

    testImplementation("org.junit.jupiter:junit-jupiter")
}
