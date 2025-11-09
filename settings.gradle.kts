//pluginManagement {
//    plugins {
//        id("kotlin-jvm").version("2.2.21")
//    }
//}
//plugins {
//    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
//}
rootProject.name = "metagen"

include(":test")
include(":test-no-lombok")
include(":test-ecj-no-lombok")
include(":meta-api")
include(":meta-processor")
include(":meta-customizer-jpa-processor")
include(":meta-customizer-jpa-api")
