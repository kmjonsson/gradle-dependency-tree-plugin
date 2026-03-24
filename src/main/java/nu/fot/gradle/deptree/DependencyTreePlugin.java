package nu.fot.gradle.deptree;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ResolvedDependency;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class DependencyTreePlugin implements Plugin<Project> {

    private static final List<String> BUILD_CONFIGS = List.of(
            "compileClasspath",
            "testCompileClasspath",
            "annotationProcessor",
            "testAnnotationProcessor"
    );

    private static final List<String> RUNTIME_CONFIGS = List.of(
            "runtimeClasspath",
            "testRuntimeClasspath"
    );

    @Override
    public void apply(Project project) {
        project.getTasks().register("dependencyTree", DependencyTreeTask.class, task -> {
            task.setGroup("reporting");
            task.setDescription("Visar ett träd över alla beroenden uppdelade på byggtid och körtid.");
            task.getOutputFile().convention(
                    project.getRootProject().getLayout().getBuildDirectory()
                            .file("dependency-tree/" + project.getName() + ".json")
            );
            task.getProjectName().set(project.getName());
            task.getBuildDependenciesJson().set(
                    project.provider(() -> configsToJson(project, BUILD_CONFIGS, "  "))
            );
            task.getRuntimeDependenciesJson().set(
                    project.provider(() -> configsToJson(project, RUNTIME_CONFIGS, "  "))
            );
        });
    }

    private static Set<String> projectModules(Project project) {
        return project.getRootProject().getAllprojects().stream()
                .map(p -> p.getGroup() + ":" + p.getName())
                .collect(Collectors.toSet());
    }

    private static String configsToJson(Project project, List<String> names, String indent) {
        Map<String, Configuration> configs = new LinkedHashMap<>();
        for (String name : names) {
            Configuration config = project.getConfigurations().findByName(name);
            if (config != null && config.isCanBeResolved()) {
                configs.put(name, config);
            }
        }

        if (configs.isEmpty()) {
            return "{}";
        }

        Set<String> projectModules = projectModules(project);
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        List<Map.Entry<String, Configuration>> entries = List.copyOf(configs.entrySet());
        for (int i = 0; i < entries.size(); i++) {
            Map.Entry<String, Configuration> entry = entries.get(i);
            sb.append(indent).append("  \"").append(entry.getKey()).append("\": ");
            sb.append(configToJson(entry.getValue(), projectModules, indent + "  "));
            if (i < entries.size() - 1) sb.append(",");
            sb.append("\n");
        }
        sb.append(indent).append("}");
        return sb.toString();
    }

    private static String configToJson(Configuration config, Set<String> projectModules, String indent) {
        Set<ResolvedDependency> firstLevel;
        try {
            firstLevel = config.getResolvedConfiguration().getFirstLevelModuleDependencies();
        } catch (Exception e) {
            return "{ \"error\": \"" + e.getMessage().replace("\"", "\\\"") + "\" }";
        }

        List<ResolvedDependency> sorted = firstLevel.stream()
                .filter(d -> !projectModules.contains(d.getModuleGroup() + ":" + d.getModuleName()))
                .sorted((a, b) -> a.getModule().getId().toString().compareTo(b.getModule().getId().toString()))
                .collect(Collectors.toList());

        return depsToJson(sorted, indent, new HashSet<>());
    }

    private static String depsToJson(List<ResolvedDependency> deps, String indent, Set<String> visited) {
        if (deps.isEmpty()) {
            return "[]";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("[\n");
        for (int i = 0; i < deps.size(); i++) {
            sb.append(depToJson(deps.get(i), indent + "  ", new HashSet<>(visited)));
            if (i < deps.size() - 1) sb.append(",");
            sb.append("\n");
        }
        sb.append(indent).append("]");
        return sb.toString();
    }

    private static String depToJson(ResolvedDependency dep, String indent, Set<String> visited) {
        String key = dep.getModuleGroup() + ":" + dep.getModuleName();
        StringBuilder sb = new StringBuilder();
        sb.append(indent).append("{\n");
        sb.append(indent).append("  \"group\": \"").append(dep.getModuleGroup()).append("\",\n");
        sb.append(indent).append("  \"name\": \"").append(dep.getModuleName()).append("\",\n");
        sb.append(indent).append("  \"version\": \"").append(dep.getModuleVersion()).append("\",\n");

        if (visited.contains(key)) {
            sb.append(indent).append("  \"dependencies\": \"(*)\"").append("\n");
        } else {
            visited.add(key);
            List<ResolvedDependency> children = dep.getChildren().stream()
                    .sorted((a, b) -> a.getModule().getId().toString().compareTo(b.getModule().getId().toString()))
                    .collect(Collectors.toList());
            sb.append(indent).append("  \"dependencies\": ").append(depsToJson(children, indent + "  ", visited)).append("\n");
        }

        sb.append(indent).append("}");
        return sb.toString();
    }
}
