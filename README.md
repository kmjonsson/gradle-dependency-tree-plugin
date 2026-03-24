# Gradle Dependency Tree Plugin

A Gradle plugin that generates a JSON report of a project's dependencies, organized by build-time and runtime configurations. Compatible with Gradle's [configuration cache](https://docs.gradle.org/current/userguide/configuration_cache.html).

## How it works

The plugin registers a task named `dependencyTree` in the `reporting` group. When the task runs, it resolves the following configurations and writes a structured JSON tree to a file:

**Build-time configurations**
- `compileClasspath`
- `testCompileClasspath`
- `annotationProcessor`
- `testAnnotationProcessor`

**Runtime configurations**
- `runtimeClasspath`
- `testRuntimeClasspath`

Each dependency entry includes its `group`, `name`, and `version`, along with a recursive list of transitive dependencies. Circular dependencies are detected and marked with `"(*)"` to prevent infinite loops.

The plugin is configuration cache compatible. Dependency resolution happens inside lazy providers that are evaluated and serialized during the configuration cache store phase. On a cache hit, the stored values are restored directly without re-resolving.

## Requirements

- Java 21
- Gradle 8+

## Usage

### Single-project build

Add the plugin to your `settings.gradle` and `build.gradle`:

**settings.gradle**
```groovy
pluginManagement {
    includeBuild '../gradle-dependency-tree-plugin'
}
```

**build.gradle**
```groovy
plugins {
    id 'nu.fot.dependency-tree'
}
```

Then run the task:

```sh
./gradlew dependencyTree
```

The report is written to `build/reports/dependency-tree.json` by default.

### Multi-project build

Apply the plugin to each subproject individually:

**subproject/build.gradle**
```groovy
plugins {
    id 'nu.fot.dependency-tree'
}
```

In a multi-project build the output files are written to the root project's build directory, one file per subproject:

```
build/
└── dependency-tree/
    ├── app.json
    ├── service.json
    └── model.json
```

Run for all subprojects at once from the root:

```sh
./gradlew dependencyTree
```

Or for a specific subproject:

```sh
./gradlew :app:dependencyTree
```

### With configuration cache

```sh
./gradlew dependencyTree --configuration-cache
```

## Output format

```json
{
  "project": "my-project",
  "buildDependencies": {
    "compileClasspath": [
      {
        "group": "org.springframework.boot",
        "name": "spring-boot-starter-web",
        "version": "3.4.3",
        "dependencies": [
          {
            "group": "org.springframework.boot",
            "name": "spring-boot-starter",
            "version": "3.4.3",
            "dependencies": []
          }
        ]
      }
    ]
  },
  "runtimeDependencies": {
    "runtimeClasspath": []
  }
}
```

## Plugin details

| Property | Value |
|---|---|
| Plugin ID | `nu.fot.dependency-tree` |
| Group | `nu.fot` |
| Version | `1.0.0` |
| Task name | `dependencyTree` |
| Task group | `reporting` |
| Implementation class | `nu.fot.gradle.deptree.DependencyTreePlugin` |
