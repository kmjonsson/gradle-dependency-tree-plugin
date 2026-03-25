package nu.fot.gradle.deptree;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ResolvedDependency;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.tasks.TaskProvider;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class DependencyTreePlugin implements Plugin<Project> {

    private static final String ALL_TASK_NAME = "allDependencyTrees";
    private static final String ALL_FILES_PROP = "_dependencyTreeFiles";

    @Override
    public void apply(Project project) {
        Project root = project.getRootProject();

        ConfigurableFileCollection allFiles;
        if (root.getExtensions().getExtraProperties().has(ALL_FILES_PROP)) {
            allFiles = (ConfigurableFileCollection) root.getExtensions().getExtraProperties().get(ALL_FILES_PROP);
        } else {
            allFiles = root.files();
            root.getExtensions().getExtraProperties().set(ALL_FILES_PROP, allFiles);
            root.getTasks().register(ALL_TASK_NAME, AllDependencyTreesTask.class, task -> {
                task.setGroup("reporting");
                task.setDescription("Combines dependency trees from all subprojects into a single file.");
                task.getProjectFiles().from(allFiles);
                task.getOutputFile().convention(
                        root.getLayout().getBuildDirectory().file("dependency-tree/all.json")
                );
            });
        }

        TaskProvider<DependencyTreeTask> depTreeTask = project.getTasks().register("dependencyTree", DependencyTreeTask.class, task -> {
            task.setGroup("reporting");
            task.setDescription("Generates a dependency tree report split into build-time and runtime dependencies.");
            task.getOutputFile().convention(
                    project.getRootProject().getLayout().getBuildDirectory()
                            .file("dependency-tree/" + project.getName() + ".json")
            );
            task.getProjectName().set(project.getName());
            task.getBuildDependenciesJson().set(
                    project.provider(() -> categorizedConfigsJson(project, false).toString())
            );
            task.getRuntimeDependenciesJson().set(
                    project.provider(() -> categorizedConfigsJson(project, true).toString())
            );
            task.getPluginsJson().set(
                    project.provider(() -> pluginsJson(project).toString())
            );
        });

        allFiles.from(depTreeTask.flatMap(DependencyTreeTask::getOutputFile));
    }

    /**
     * A configuration is considered production-runtime if:
     * 1. Its root ancestors (leaf nodes in the hierarchy — configurations with no parents)
     *    include "runtimeOnly". This identifies the configuration as runtime-scoped,
     *    regardless of what the resolvable configuration is named.
     * 2. The configuration itself does not have a name starting with "test".
     *    Only the configuration itself is checked — not its ancestors — because
     *    some non-test configurations (e.g. Spring Boot's testAndDevelopmentOnly) have
     *    "test"-prefixed names in the hierarchy while still feeding into production runtime.
     */
    private static boolean isProductionRuntime(Configuration config) {
        boolean hasRuntimeRoot = config.getHierarchy().stream()
                .filter(c -> c.getExtendsFrom().isEmpty())
                .anyMatch(c -> c.getName().equals("runtimeOnly"));

        return hasRuntimeRoot && !config.getName().startsWith("test");
    }

    private static JSONObject categorizedConfigsJson(Project project, boolean productionRuntime) {
        Set<String> projectModules = projectModules(project);
        JSONObject result = new JSONObject();
        project.getConfigurations().stream()
                .filter(c -> c.isCanBeResolved() && !c.isCanBeConsumed())
                .filter(c -> isProductionRuntime(c) == productionRuntime)
                .sorted((a, b) -> a.getName().compareTo(b.getName()))
                .forEach(c -> result.put(c.getName(), configToJson(c, projectModules)));
        return result;
    }

    private static Set<String> projectModules(Project project) {
        return project.getRootProject().getAllprojects().stream()
                .map(p -> p.getGroup() + ":" + p.getName())
                .collect(Collectors.toSet());
    }

    private static JSONArray pluginsJson(Project project) {
        Configuration classpath = project.getBuildscript().getConfigurations().findByName("classpath");
        if (classpath == null || !classpath.isCanBeResolved()) {
            return new JSONArray();
        }
        return configToJson(classpath, projectModules(project));
    }

    private static JSONArray configToJson(Configuration config, Set<String> projectModules) {
        Set<ResolvedDependency> firstLevel;
        try {
            firstLevel = config.getResolvedConfiguration().getFirstLevelModuleDependencies();
        } catch (Exception e) {
            JSONArray error = new JSONArray();
            error.put(new JSONObject().put("error", e.getMessage()));
            return error;
        }

        List<ResolvedDependency> sorted = firstLevel.stream()
                .filter(d -> !projectModules.contains(d.getModuleGroup() + ":" + d.getModuleName()))
                .sorted((a, b) -> a.getModule().getId().toString().compareTo(b.getModule().getId().toString()))
                .collect(Collectors.toList());

        return depsToJson(sorted, new HashSet<>());
    }

    private static JSONArray depsToJson(List<ResolvedDependency> deps, Set<String> visited) {
        JSONArray array = new JSONArray();
        for (ResolvedDependency dep : deps) {
            array.put(depToJson(dep, new HashSet<>(visited)));
        }
        return array;
    }

    private static JSONObject depToJson(ResolvedDependency dep, Set<String> visited) {
        String key = dep.getModuleGroup() + ":" + dep.getModuleName();
        JSONObject obj = new JSONObject();
        obj.put("group", dep.getModuleGroup());
        obj.put("name", dep.getModuleName());
        obj.put("version", dep.getModuleVersion());

        if (visited.contains(key)) {
            obj.put("dependencies", "(*)");
        } else {
            visited.add(key);
            List<ResolvedDependency> children = dep.getChildren().stream()
                    .sorted((a, b) -> a.getModule().getId().toString().compareTo(b.getModule().getId().toString()))
                    .collect(Collectors.toList());
            obj.put("dependencies", depsToJson(children, visited));
        }
        return obj;
    }
}
