plugins {
    id("java")
}

repositories {
    mavenCentral()
}

dependencies {
    compileOnly("org.projectlombok:lombok:1.18.38")
    annotationProcessor("org.projectlombok:lombok:1.18.38")

    testCompileOnly("org.projectlombok:lombok:1.18.38")
    testAnnotationProcessor("org.projectlombok:lombok:1.18.38")

//    annotationProcessor("io.github.m4gshm:metagen:0.0.1-rc1")
//    implementation("io.github.m4gshm:metagen:0.0.1-rc1")
    annotationProcessor(project(":meta-processor"))
    annotationProcessor(project(":meta-customizer-jpa-processor"))
    implementation(project(":meta-api"))
    implementation(project(":meta-customizer-jpa-api"))

    implementation("org.hibernate.javax.persistence:hibernate-jpa-2.1-api:1.0.2.Final")

    implementation("org.springframework.data:spring-data-jpa:3.2.1")

    testImplementation(platform("org.junit:junit-bom:5.9.1"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.mockito:mockito-junit-jupiter:5.17.0")
}

tasks.test {
    useJUnitPlatform()
}
