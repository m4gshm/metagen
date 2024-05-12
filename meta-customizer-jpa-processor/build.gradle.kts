plugins {
    `java-library`
    `maven-publish`
    signing
    id("com.gradleup.nmcp")
}

dependencies {
    compileOnly("org.projectlombok:lombok:1.18.30")
    annotationProcessor("org.projectlombok:lombok:1.18.30")

    testCompileOnly("org.projectlombok:lombok:1.18.30")
    testAnnotationProcessor("org.projectlombok:lombok:1.18.30")

    api(project(":meta-customizer-jpa-api"))
    api(project(":meta-processor"))

    implementation("io.github.jbock-java:javapoet:1.15")

    testImplementation(platform("org.junit:junit-bom:5.9.1"))
    testImplementation("org.junit.jupiter:junit-jupiter")
}

tasks.test {
    useJUnitPlatform()
}

tasks.compileJava {
//    options.isVerbose = true
}

java {
    withSourcesJar()
    withJavadocJar()
    targetCompatibility = JavaVersion.VERSION_17
    sourceCompatibility = JavaVersion.VERSION_17
    modularity.inferModulePath.set(true)
}

publishing {
    publications {
        create<MavenPublication>("java") {
            pom {
                description.set("Enumerated constants generator, based on bean properties and type parameters")
                url.set("https://github.com/m4gshm/metagen")
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
                    connection.set("scm:git:https://github.com/m4gshm/metagen.git")
                    developerConnection.set("scm:git:https://github.com/m4gshm/metagen.git")
                    url.set("https://github.com/m4gshm/metagen")
                }
                licenses {
                    license {
                        name.set("MIT License")
                        url.set("https://github.com/m4gshm/metagen?tab=MIT-1-ov-file#readme")
                    }
                }
            }
            from(components["java"])
        }
    }
    repositories {
        maven("file://$rootDir/../m4gshm.github.io/maven2") {
            name = "GithubMavenRepo"
        }
    }
}

if (project.properties["signing.keyId"] != null) {
    signing {
        val extension = extensions.getByName("publishing") as PublishingExtension
        sign(extension.publications)
    }
}

nmcp {
    publishAllPublications {}
}