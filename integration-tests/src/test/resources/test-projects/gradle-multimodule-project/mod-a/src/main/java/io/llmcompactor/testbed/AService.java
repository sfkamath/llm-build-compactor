package io.llmcompactor.testbed;

import java.util.ArrayList;
import java.util.List;

public class AService {

    // Raw-type usage triggers unchecked/rawtypes javac warnings under -Xlint:all. Intentionally
    // NOT suppressed: the warnings must fire so the test can verify the compactor hides them.
    public List build() {
        List items = new ArrayList();
        items.add("a");
        return items;
    }
}
