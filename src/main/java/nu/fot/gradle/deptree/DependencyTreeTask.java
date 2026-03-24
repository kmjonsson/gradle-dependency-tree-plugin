package nu.fot.gradle.deptree;

import org.gradle.api.DefaultTask;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;

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

    @OutputFile
    public abstract RegularFileProperty getOutputFile();

    @TaskAction
    public void printDependencyTree() {
        String json = "{\n" +
                "  \"project\": \"" + getProjectName().get() + "\",\n" +
                "  \"buildDependencies\": " + getBuildDependenciesJson().get() + ",\n" +
                "  \"runtimeDependencies\": " + getRuntimeDependenciesJson().get() + "\n" +
                "}";

        File output = getOutputFile().getAsFile().get();
        output.getParentFile().mkdirs();
        try {
            Files.writeString(output.toPath(), json);
        } catch (IOException e) {
            throw new RuntimeException("Kunde inte skriva till fil: " + output, e);
        }
    }
}
