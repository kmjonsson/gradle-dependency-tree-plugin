package nu.fot.gradle.deptree;

import org.gradle.api.DefaultTask;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public abstract class AllDependencyTreesTask extends DefaultTask {

    @InputFiles
    public abstract ConfigurableFileCollection getProjectFiles();

    @OutputFile
    public abstract RegularFileProperty getOutputFile();

    @TaskAction
    public void combine() throws IOException {
        List<File> sorted = new ArrayList<>(getProjectFiles().getFiles());
        sorted.sort(Comparator.comparing(File::getName));

        JSONArray projects = new JSONArray();
        for (File file : sorted) {
            projects.put(new JSONObject(Files.readString(file.toPath())));
        }

        JSONObject root = new JSONObject();
        root.put("projects", projects);

        File output = getOutputFile().getAsFile().get();
        output.getParentFile().mkdirs();
        Files.writeString(output.toPath(), root.toString(2));
    }
}
