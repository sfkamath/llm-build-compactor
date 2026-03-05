package io.llmcompactor.core.context;

import io.llmcompactor.core.BuildSummary;
import io.llmcompactor.core.FixTarget;
import io.llmcompactor.core.git.GitDiffExtractor;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RepairContextBuilder {

    public static Map<String,Object> build(BuildSummary summary) {

        Map<String,Object> ctx = new HashMap<>();

        ctx.put("status", summary.status());

        List<String> changed = GitDiffExtractor.changedFiles();

        ctx.put("recentChanges", changed);

        ctx.put("fixTargets", summary.fixTargets());

        return ctx;

    }

}
