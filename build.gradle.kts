plugins {
    `java-library`
    `maven-publish`
    signing
    id("com.gradleup.nmcp").version("0.0.4")
    id("org.asciidoctor.jvm.convert") version "4.0.1"
}

group = "io.github.m4gshm"
version = "0.0.1-rc2"

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

tasks.asciidoctor {
    dependsOn(":test:classes")
    baseDirFollowsSourceFile()
    outputOptions {
        backends("docbook")
    }
}

tasks.create<Exec>("pandoc") {
    dependsOn("asciidoctor")
    group = "documentation"
    commandLine = "pandoc -f docbook -t gfm $buildDir/docs/asciidoc/readme.xml -o $rootDir/README.md".split(" ")
}

tasks.build {
    if (properties["no-pandoc"] == null) {
        dependsOn("pandoc")
    }
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
    publishAllProjectsProbablyBreakingProjectIsolation {
        val ossrhUsername = project.properties["ossrhUsername"] as String?
        val ossrhPassword = project.properties["ossrhPassword"] as String?
        username.set(ossrhUsername)
        password.set(ossrhPassword)
        publicationType = "USER_MANAGED"
//        publicationType = "AUTOMATIC"
    }
}