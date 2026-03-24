# Gradle Dependency Tree Plugin

A Gradle plugin that generates JSON reports of a project's dependencies, organized by plugins, build-time, and runtime configurations. Compatible with Gradle's [configuration cache](https://docs.gradle.org/current/userguide/configuration_cache.html).

## How it works

The plugin registers two tasks in the `reporting` group:

**`dependencyTree`** — runs per project and resolves the following configurations:

| Section | Configurations |
|---|---|
| `plugins` | buildscript `classpath` |
| `buildDependencies` | `compileClasspath`, `testCompileClasspath`, `annotationProcessor`, `testAnnotationProcessor` |
| `runtimeDependencies` | `runtimeClasspath`, `testRuntimeClasspath` |

Each dependency entry includes its `group`, `name`, and `version`, along with a recursive list of transitive dependencies. Circular dependencies are detected and marked with `"(*)"` to prevent infinite loops. Project dependencies (other subprojects in the same build) are excluded — only external dependencies are listed.

**`allDependencyTrees`** — runs on the root project and combines the output of all `dependencyTree` tasks into a single file with a `projects` list.

The plugin is configuration cache compatible. Dependency resolution happens inside lazy providers that are evaluated and serialized during the configuration cache store phase. On a cache hit, the stored values are restored directly without re-resolving.

## Requirements

- Java 21
- Gradle 8+

## Usage

### Single-project build

**settings.gradle**
```groovy
pluginManagement {
    includeBuild '../gradle-dependency-tree-plugin'
}
```

**build.gradle**
```groovy
plugins {
    id 'java'
    id 'nu.fot.dependency-tree'
}
```

```sh
./gradlew dependencyTree
```

Output: `build/dependency-tree/<projectName>.json`

### Multi-project build

Apply the plugin to each subproject individually:

**subproject/build.gradle**
```groovy
plugins {
    id 'java'
    id 'nu.fot.dependency-tree'
}
```

All output files are written to the root project's build directory:

```
build/
└── dependency-tree/
    ├── all.json      ← combined report (allDependencyTrees)
    ├── app.json
    ├── model.json
    └── service.json
```

Run `dependencyTree` for all subprojects, then combine:

```sh
./gradlew allDependencyTrees
```

Or for a specific subproject only:

```sh
./gradlew :app:dependencyTree
```

### With configuration cache

```sh
./gradlew allDependencyTrees --configuration-cache
```

## Output format

### Per-project file (`<name>.json`)

```json
{
  "project": "app",
  "plugins": [
    {
      "group": "org.springframework.boot",
      "name": "org.springframework.boot.gradle.plugin",
      "version": "3.4.3",
      "dependencies": []
    }
  ],
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
    ],
    "testCompileClasspath": [],
    "annotationProcessor": [],
    "testAnnotationProcessor": []
  },
  "runtimeDependencies": {
    "runtimeClasspath": [],
    "testRuntimeClasspath": []
  }
}
```

### Combined file (`all.json`)

```json
{
  "projects": [
    {
      "project": "app",
      "plugins": [],
      "buildDependencies": {},
      "runtimeDependencies": {}
    },
    {
      "project": "model",
      "plugins": [],
      "buildDependencies": {},
      "runtimeDependencies": {}
    }
  ]
}
```

## Plugin details

| Property | Value |
|---|---|
| Plugin ID | `nu.fot.dependency-tree` |
| Group | `nu.fot` |
| Version | `1.0.0` |
| Task name | `dependencyTree` |
| Aggregation task name | `allDependencyTrees` |
| Task group | `reporting` |
| Implementation class | `nu.fot.gradle.deptree.DependencyTreePlugin` |
