plugins {
//    kotlin("jvm")
    `java-library`
    `maven-publish`
}

group = "com.github.m4gshm"
version = "0.1-SNAPSHOT"

allprojects {
    repositories {
        mavenCentral()
    }
}

dependencies {
    compileOnly("org.projectlombok:lombok:1.18.30")
    annotationProcessor("org.projectlombok:lombok:1.18.30")

    testCompileOnly("org.projectlombok:lombok:1.18.30")
    testAnnotationProcessor("org.projectlombok:lombok:1.18.30")

    api("io.github.jbock-java:javapoet:1.15")

    testImplementation(platform("org.junit:junit-bom:5.9.1"))
    testImplementation("org.junit.jupiter:junit-jupiter")
//    implementation(kotlin("stdlib-jdk8"))
}

tasks.test {
    useJUnitPlatform()
}

tasks.compileJava {
    this.options.isVerbose = true
}
//kotlin {
//    jvmToolchain(17)
//}

java {
    withSourcesJar()
    targetCompatibility = JavaVersion.VERSION_17
    sourceCompatibility = JavaVersion.VERSION_17
    modularity.inferModulePath.set(true)
}

publishing {
    publications {
        create<MavenPublication>("java") {
            pom {
                properties.put("maven.compiler.target", "${java.targetCompatibility}")
                properties.put("maven.compiler.source", "${java.sourceCompatibility}")
                developers {
                    developer {
                        id.set("m4gshm")
                        name.set("Bulgakov Alexander")
                        email.set("mfourgeneralsherman@gmail.com")
                    }
                }
                scm {
                    connection.set("scm:git:https://github.com/m4gshm/matador.git")
                    developerConnection.set("scm:git:https://github.com/m4gshm/matador.git")
                    url.set("https://github.com/m4gshm/matador")
                }
            }
            from(components["java"])
        }
    }
}