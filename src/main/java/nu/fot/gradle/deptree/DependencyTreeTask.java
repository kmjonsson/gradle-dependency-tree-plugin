package nu.fot.gradle.deptree;

import org.gradle.api.DefaultTask;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

public abstract class DependencyTreeTask extends DefaultTask {

    @Input
    public abstract Property<String> getProjectName();

    @Input
    public abstract Property<String> getBuildDependenciesJson();

    @Input
    public abstract Property<String> getRuntimeDependenciesJson();

    @Input
    public abstract Property<String> getPluginsJson();

    @OutputFile
    public abstract RegularFileProperty getOutputFile();

    @TaskAction
    public void printDependencyTree() {
        JSONObject root = new JSONObject();
        root.put("project", getProjectName().get());
        root.put("plugins", new JSONArray(getPluginsJson().get()));
        root.put("buildDependencies", new JSONObject(getBuildDependenciesJson().get()));
        root.put("runtimeDependencies", new JSONObject(getRuntimeDependenciesJson().get()));

        File output = getOutputFile().getAsFile().get();
        output.getParentFile().mkdirs();
        try {
            Files.writeString(output.toPath(), root.toString(2));
        } catch (IOException e) {
            throw new RuntimeException("Kunde inte skriva till fil: " + output, e);
        }
    }
}
