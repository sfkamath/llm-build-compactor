package io.llmcompactor.testbed;

import java.util.ArrayList;
import java.util.List;

public class BService {

    // Same raw-type unchecked warning as mod-a. This module does NOT declare the compactor plugin;
    // its lint noise must still be suppressed by the root application across all modules.
    public List build() {
        List items = new ArrayList();
        items.add("b");
        return items;
    }
}
