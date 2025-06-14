import org.jetbrains.gradle.ext.Application
import org.jetbrains.gradle.ext.Gradle
import org.jetbrains.gradle.ext.RunConfigurationContainer

plugins {
	id("java-library")
	id("org.jetbrains.gradle.plugin.idea-ext") version "1.1.8"
	id("eclipse")
	id("com.gtnewhorizons.retrofuturagradle") version "1.4.0"
}


val mixinBooterVersion = "10.6"

// Project properties
group = "moe.chyyran"
version = "1.0.0"
val id = project.name.toLowerCase()

// Set the toolchain version to decouple the Java we run Gradle with from the Java used to compile and run the mod
java {
	toolchain {
		languageVersion.set(JavaLanguageVersion.of(8))
		// Azul covers the most platforms for Java 8 toolchains, crucially including MacOS arm64
		vendor.set(org.gradle.jvm.toolchain.JvmVendorSpec.AZUL)
	}
	// Generate sources and javadocs jars when building and publishing
	withSourcesJar()
	withJavadocJar()
}

// Most RFG configuration lives here, see the JavaDoc for com.gtnewhorizons.retrofuturagradle.MinecraftExtension
minecraft {
	mcVersion.set("1.12.2")

	// Username for client run configurations
	username.set("Developer")

	// Generate a field named VERSION with the mod version in the injected Tags class
	injectedTags.put("VERSION", project.version)

	// If you need the old replaceIn mechanism, prefer the injectTags task because it doesn't inject a javac plugin.
	// tagReplacementFiles.add("RfgExampleMod.java")

	// Enable assertions in the mod's package when running the client or server
	extraRunJvmArguments.add("-ea:${project.group}")

	// If needed, add extra tweaker classes like for mixins.
	// extraTweakClasses.add("org.spongepowered.asm.launch.MixinTweaker")

	// Exclude some Maven dependency groups from being automatically included in the reobfuscated runs
	groupsToExcludeFromAutoReobfMapping.addAll("com.diffplug", "com.diffplug.durian", "net.industrial-craft")
}


tasks {
//	arrayOf(deobfuscateMergedJarToSrg, srgifyBinpatchedJar).forEach {
//		it.configure {
//			accessTransformerFiles.from(project.files("src/main/resources/META-INF/${id}_at.cfg"))
//		}
//	}

	processResources {
		val expandProperties = mapOf(
			"version" to project.version,
			"name" to project.name,
			"id" to id
		)

		inputs.properties(expandProperties)

		filesMatching("**/*.*") {
			if (!path.endsWith(".png")) {
				if (path.endsWith("mixins.${id}.json")) {
					filter { line ->
						expandProperties.entries.fold(line) { acc, (key, value) ->
							acc.replace("\${$key}", value.toString())
						}
					}
				} else {
					expand(expandProperties)
				}
			}
		}
	}

	withType<Jar> {
		manifest {
			attributes(
				"ModSide" to "BOTH",
//				"FMLAT" to "${id}_at.cfg",
				"FMLCorePlugin" to "moe.chyyran.${id}.asm.LavaPushesMobsPlugin",
				"FMLCorePluginContainsFMLMod" to "true",
				"ForceLoadAsMod" to "true"
			)
		}
		duplicatesStrategy = DuplicatesStrategy.INCLUDE
	}

	withType<JavaCompile> {
		options.encoding = "UTF-8"

		options.isFork = true
		options.forkOptions.jvmArgs = listOf("-Xmx4G", "-XX:+UseStringDeduplication")
	}
}

// Add an access tranformer
// tasks.deobfuscateMergedJarToSrg.configure {accessTransformerFiles.from("src/main/resources/META-INF/mymod_at.cfg")}

// Dependencies
repositories {
	maven {
		name = "Cleanroom"
		url = uri("https://maven.cleanroommc.com")
		content {
			includeGroup("zone.rong")
		}
	}

	exclusiveContent {
		forRepository {
			maven {
				name = "Curse Maven"
				url = uri("https://cursemaven.com")
			}
		}
		filter {
			includeGroup("curse.maven")
		}
	}
}

dependencies {
	annotationProcessor("org.ow2.asm", "asm-debug-all", "5.2")
	annotationProcessor("com.google.guava", "guava", "32.1.2-jre")
	annotationProcessor("com.google.code.gson", "gson", "2.8.9")

	val mixinBooter: String = modUtils.enableMixins("zone.rong:mixinbooter:$mixinBooterVersion", "mixins.${id}.refmap.json") as String
	api(mixinBooter) {
		isTransitive = false
	}
	annotationProcessor(mixinBooter) {
		isTransitive = false
	}
}

idea {
	module {
		isDownloadJavadoc = true
		isDownloadSources = true
		inheritOutputDirs = true // Fix resources in IJ-Native runs
	}
	project {
		this.withGroovyBuilder {
			"settings" {
				"runConfigurations" {
					val self = this.delegate as RunConfigurationContainer
					self.add(Gradle("1. Run Client").apply {
						setProperty("taskNames", listOf("runClient"))
					})
					self.add(Gradle("2. Run Server").apply {
						setProperty("taskNames", listOf("runServer"))
					})
					self.add(Gradle("3. Run Obfuscated Client").apply {
						setProperty("taskNames", listOf("runObfClient"))
					})
					self.add(Gradle("4. Run Obfuscated Server").apply {
						setProperty("taskNames", listOf("runObfServer"))
					})
				}
				"compiler" {
					val self = this.delegate as org.jetbrains.gradle.ext.IdeaCompilerConfiguration
					afterEvaluate {
						self.javac.moduleJavacAdditionalOptions = mapOf(
							(project.name + ".main") to
									tasks.compileJava.get().options.compilerArgs.map { '"' + it + '"' }.joinToString(" ")
						)
					}
				}
			}
		}
	}
}

tasks.processIdeaSettings.configure {
	dependsOn(tasks.injectTags)
}