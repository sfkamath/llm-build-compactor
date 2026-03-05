package io.llmcompactor.core.git;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

public class GitDiffExtractor {

    public static List<String> changedFiles() {

        List<String> files = new ArrayList<>();

        try {

            Process p = new ProcessBuilder(
                    "git",
                    "diff",
                    "--name-only",
                    "HEAD"
            ).start();

            BufferedReader reader =
                    new BufferedReader(
                            new InputStreamReader(p.getInputStream()));

            String line;

            while ((line = reader.readLine()) != null) {
                files.add(line);
            }

        } catch (Exception ignored) {}

        return files;

    }

}
