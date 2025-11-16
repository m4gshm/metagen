import io.spring.gradle.dependencymanagement.dsl.DependencyManagementExtension

plugins {
//    `java-library`
//    `maven-publish`
//    signing apply false
    id("com.gradleup.nmcp").version("0.0.7")
    id("org.asciidoctor.jvm.convert") version "4.0.1"
    id("io.spring.dependency-management") version "1.1.7" apply false
}

val javaVersion = JavaVersion.VERSION_17

allprojects {
    apply(plugin = "io.spring.dependency-management")

    group = "io.github.m4gshm"
    version = "0.0.1-rc7"
    repositories {
        mavenCentral()
    }

    the<DependencyManagementExtension>().apply {
        imports {
            mavenBom("org.junit:junit-bom:5.9.1")
        }
        dependencies {
            dependency("org.projectlombok:lombok:1.18.42")
            dependency("io.github.jbock-java:javapoet:1.15")
            dependency("org.mockito:mockito-junit-jupiter:5.20.0")
        }
    }
}

subprojects {
    apply(plugin = "java-library")
    apply(plugin = "maven-publish")
    apply(plugin = "signing")

    the<JavaPluginExtension>().apply {
        withSourcesJar()
        withJavadocJar()
        targetCompatibility = javaVersion
        sourceCompatibility = javaVersion
        modularity.inferModulePath.set(true)
    }

    tasks.named<Test>("test") {
        useJUnitPlatform()
    }

    the<PublishingExtension>().apply {
        publications {
            create<MavenPublication>("java") {
                pom {
                    description.set("Enumerated constants generator, based on bean properties and type parameters")
                    url.set("https://github.com/m4gshm/metagen")
                    properties.put("maven.compiler.target", "${javaVersion}")
                    properties.put("maven.compiler.source", "${javaVersion}")
                    name.set(project.name)
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
//        repositories {
//            maven("file://$rootDir/../m4gshm.github.io/maven2") {
//                name = "GithubMavenRepo"
//            }
//        }
    }

    if (!project.name.contains("no-lombok")) {
        plugins.findPlugin(JavaLibraryPlugin::class).let { javaLibraryPlugin ->
            dependencies {
                listOf("compileOnly", "annotationProcessor", "testCompileOnly", "testAnnotationProcessor").forEach {
                    add(it, "org.projectlombok:lombok")
                }
            }
        }
    }

    if (project.properties["signing.keyId"] != null) {
        the<SigningExtension>().apply {
            val extension = extensions.getByName("publishing") as PublishingExtension
            sign(extension.publications)
        }
    }
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

nmcp {
    publishAggregation {
        project(":meta-api")
        project(":meta-processor")
        project(":meta-processor-utils")
        project(":meta-customizer-jpa-api")
        project(":meta-customizer-jpa-processor")
        val ossrhUsername = project.properties["ossrhUsername"] as String?
        val ossrhPassword = project.properties["ossrhPassword"] as String?
        username.set(ossrhUsername)
        password.set(ossrhPassword)
        publicationType = "USER_MANAGED"
    }
}
