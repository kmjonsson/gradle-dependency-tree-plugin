package nu.fot.gradle.deptree;

import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class DependencyTreePluginTest {

    private void write(Path file, String content) throws IOException {
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
    }

    private GradleRunner runner(Path projectDir, String... args) {
        return GradleRunner.create()
                .withProjectDir(projectDir.toFile())
                .withPluginClasspath()
                .withArguments(args);
    }

    private String readJson(Path projectDir, String name) throws IOException {
        return Files.readString(projectDir.resolve("build/dependency-tree/" + name + ".json"));
    }

    @Test
    void taskIsRegisteredInReportingGroup(@TempDir Path projectDir) throws IOException {
        write(projectDir.resolve("settings.gradle"), "rootProject.name = 'test'");
        write(projectDir.resolve("build.gradle"), "plugins { id 'nu.fot.dependency-tree' }");

        BuildResult result = runner(projectDir, "tasks", "--group=reporting").build();

        assertTrue(result.getOutput().contains("dependencyTree"));
    }

    @Test
    void outputFileIsCreatedInRootBuildDir(@TempDir Path projectDir) throws IOException {
        write(projectDir.resolve("settings.gradle"), "rootProject.name = 'myproject'");
        write(projectDir.resolve("build.gradle"), "plugins { id 'nu.fot.dependency-tree' }");

        runner(projectDir, "dependencyTree").build();

        assertTrue(Files.exists(projectDir.resolve("build/dependency-tree/myproject.json")));
    }

    @Test
    void jsonContainsProjectName(@TempDir Path projectDir) throws IOException {
        write(projectDir.resolve("settings.gradle"), "rootProject.name = 'myproject'");
        write(projectDir.resolve("build.gradle"), "plugins { id 'nu.fot.dependency-tree' }");

        runner(projectDir, "dependencyTree").build();

        assertTrue(readJson(projectDir, "myproject").contains("\"project\": \"myproject\""));
    }

    @Test
    void emptyProjectProducesEmptyDependencyArrays(@TempDir Path projectDir) throws IOException {
        write(projectDir.resolve("settings.gradle"), "rootProject.name = 'empty'");
        write(projectDir.resolve("build.gradle"), """
                plugins {
                    id 'java'
                    id 'nu.fot.dependency-tree'
                }
                """);

        runner(projectDir, "dependencyTree").build();

        org.json.JSONObject root = new org.json.JSONObject(readJson(projectDir, "empty"));
        assertEquals(0, root.getJSONArray("runtimeDependencies").length(), "runtimeDependencies should be empty");
        assertEquals(0, root.getJSONArray("buildDependencies").length(), "buildDependencies should be empty");
    }

    @Test
    void dependencyIncludesConfigurationsAttribute(@TempDir Path projectDir) throws IOException {
        write(projectDir.resolve("settings.gradle"), "rootProject.name = 'with-configs'");
        write(projectDir.resolve("build.gradle"), """
                plugins {
                    id 'java'
                    id 'nu.fot.dependency-tree'
                }
                repositories { mavenCentral() }
                dependencies {
                    implementation 'org.apache.commons:commons-lang3:3.14.0'
                }
                """);

        runner(projectDir, "dependencyTree").build();

        org.json.JSONObject root = new org.json.JSONObject(readJson(projectDir, "with-configs"));
        org.json.JSONArray runtime = root.getJSONArray("runtimeDependencies");
        org.json.JSONObject dep = runtime.getJSONObject(0);
        assertEquals("commons-lang3", dep.getString("name"));
        org.json.JSONArray configs = dep.getJSONArray("configurations");
        assertTrue(configs.toList().contains("runtimeClasspath"), "runtimeClasspath should be listed in configurations");

        // implementation deps also land in compileClasspath (build), so verify that config is present there
        org.json.JSONArray build = root.getJSONArray("buildDependencies");
        boolean foundInBuild = false;
        for (int i = 0; i < build.length(); i++) {
            org.json.JSONObject buildDep = build.getJSONObject(i);
            if ("commons-lang3".equals(buildDep.getString("name"))) {
                assertTrue(buildDep.getJSONArray("configurations").toList().contains("compileClasspath"),
                        "build occurrence should list compileClasspath in configurations");
                foundInBuild = true;
                break;
            }
        }
        assertTrue(foundInBuild, "commons-lang3 should also appear in buildDependencies via compileClasspath");
    }

    @Test
    void externalPluginsAreIncludedInOutput(@TempDir Path projectDir) throws IOException {
        write(projectDir.resolve("settings.gradle"), "rootProject.name = 'with-plugin'");
        write(projectDir.resolve("build.gradle"), """
                buildscript {
                    repositories { mavenCentral() }
                    dependencies {
                        classpath 'org.apache.commons:commons-lang3:3.14.0'
                    }
                }
                plugins {
                    id 'nu.fot.dependency-tree'
                }
                """);

        runner(projectDir, "dependencyTree").build();

        String json = readJson(projectDir, "with-plugin");
        assertTrue(json.contains("\"plugins\""), "JSON should contain plugins key");
        assertTrue(json.contains("\"name\": \"commons-lang3\""), "Plugin-beroende ska synas");
    }

    @Test
    void externalDependencyAppearsInOutput(@TempDir Path projectDir) throws IOException {
        write(projectDir.resolve("settings.gradle"), "rootProject.name = 'with-deps'");
        write(projectDir.resolve("build.gradle"), """
                plugins {
                    id 'java'
                    id 'nu.fot.dependency-tree'
                }
                repositories { mavenCentral() }
                dependencies {
                    implementation 'org.apache.commons:commons-lang3:3.14.0'
                }
                """);

        runner(projectDir, "dependencyTree").build();

        String json = readJson(projectDir, "with-deps");
        assertTrue(json.contains("\"name\": \"commons-lang3\""));
        assertTrue(json.contains("\"version\": \"3.14.0\""));
        assertTrue(json.contains("\"group\": \"org.apache.commons\""));
    }

    @Test
    void projectDependencyIsExcludedFromOutput(@TempDir Path projectDir) throws IOException {
        write(projectDir.resolve("settings.gradle"), """
                rootProject.name = 'multi'
                include 'core', 'app'
                """);
        write(projectDir.resolve("build.gradle"), "");
        write(projectDir.resolve("core/build.gradle"), """
                plugins { id 'java' }
                group = 'com.example'
                """);
        write(projectDir.resolve("app/build.gradle"), """
                plugins {
                    id 'java'
                    id 'nu.fot.dependency-tree'
                }
                group = 'com.example'
                repositories { mavenCentral() }
                dependencies {
                    implementation project(':core')
                    implementation 'org.apache.commons:commons-lang3:3.14.0'
                }
                """);

        runner(projectDir, ":app:dependencyTree").build();

        String json = readJson(projectDir, "app");
        assertFalse(json.contains("\"name\": \"core\""), "Projektberoenden ska exkluderas");
        assertTrue(json.contains("\"name\": \"commons-lang3\""), "Externa beroenden ska inkluderas");
    }

    @Test
    void transitiveDependenciesAreIncluded(@TempDir Path projectDir) throws IOException {
        write(projectDir.resolve("settings.gradle"), "rootProject.name = 'transitive'");
        write(projectDir.resolve("build.gradle"), """
                plugins {
                    id 'java'
                    id 'nu.fot.dependency-tree'
                }
                repositories { mavenCentral() }
                dependencies {
                    implementation 'org.springframework:spring-context:6.2.4'
                }
                """);

        runner(projectDir, "dependencyTree").build();

        String json = readJson(projectDir, "transitive");
        assertTrue(json.contains("\"name\": \"spring-context\""), "Direktberoende ska finnas");
        assertTrue(json.contains("\"name\": \"spring-core\""), "Transitivt beroende ska finnas");
    }

    @Test
    void multiProjectOutputGoesToRootBuildDir(@TempDir Path projectDir) throws IOException {
        write(projectDir.resolve("settings.gradle"), """
                rootProject.name = 'multi'
                include 'app'
                """);
        write(projectDir.resolve("build.gradle"), "");
        write(projectDir.resolve("app/build.gradle"), """
                plugins {
                    id 'java'
                    id 'nu.fot.dependency-tree'
                }
                """);

        runner(projectDir, ":app:dependencyTree").build();

        assertTrue(Files.exists(projectDir.resolve("build/dependency-tree/app.json")),
                "Filen ska skapas i topprojectets build-katalog");
        assertFalse(Files.exists(projectDir.resolve("app/build/dependency-tree/app.json")),
                "Filen ska inte skapas i subprojektets build-katalog");
    }
}
