plugins {
    `java-library`
    kotlin("jvm")
    kotlin("plugin.serialization")
}

base {
    archivesName = "lavasrc"
}

java {
    withJavadocJar()
    withSourcesJar()
    sourceCompatibility = JavaVersion.VERSION_11
}

dependencies {
    api("com.github.topi314.lavasearch:lavasearch:1.0.0")
    api("com.github.topi314.lavalyrics:lavalyrics:01bf4e7")
    compileOnly("dev.arbjerg:lavaplayer:2.0.4")
    implementation("org.jsoup:jsoup:1.15.3")
    implementation("commons-io:commons-io:2.7")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.5.1")
    implementation("org.jetbrains.kotlin:kotlin-annotations-jvm:1.9.0")
    implementation("com.auth0:java-jwt:4.4.0")
    compileOnly("org.slf4j:slf4j-api:2.0.7")

    lyricsDependency("protocol")
    lyricsDependency("client")
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            pom {
                artifactId = base.archivesName.get()
                from(components["java"])
            }
        }
    }
}

kotlin {
    jvmToolchain(11)
}


fun DependencyHandlerScope.lyricsDependency(module: String) {
    implementation("dev.schlaubi.lyrics", "$module-jvm", "2.2.2") {
        isTransitive = false
    }
}