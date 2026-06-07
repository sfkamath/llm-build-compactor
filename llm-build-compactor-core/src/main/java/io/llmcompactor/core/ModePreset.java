package io.llmcompactor.core;

public enum ModePreset {
  NONE {
    @Override
    public boolean overrideOutputAsJson(boolean v) {
      return v;
    }

    @Override
    public boolean overrideShowFixTargets(boolean v) {
      return v;
    }

    @Override
    public boolean overrideShowFailedTestLogs(boolean v) {
      return v;
    }
  },
  AGENT {
    @Override
    public boolean overrideOutputAsJson(boolean v) {
      return true;
    }

    @Override
    public boolean overrideShowFixTargets(boolean v) {
      return true;
    }

    @Override
    public boolean overrideShowFailedTestLogs(boolean v) {
      return false;
    }
  },
  DEBUG {
    @Override
    public boolean overrideOutputAsJson(boolean v) {
      return true;
    }

    @Override
    public boolean overrideShowFixTargets(boolean v) {
      return true;
    }

    @Override
    public boolean overrideShowFailedTestLogs(boolean v) {
      return true;
    }
  },
  HUMAN {
    @Override
    public boolean overrideOutputAsJson(boolean v) {
      return false;
    }

    @Override
    public boolean overrideShowFixTargets(boolean v) {
      return true;
    }

    @Override
    public boolean overrideShowFailedTestLogs(boolean v) {
      return false;
    }
  };

  public abstract boolean overrideOutputAsJson(boolean current);

  public abstract boolean overrideShowFixTargets(boolean current);

  public abstract boolean overrideShowFailedTestLogs(boolean current);

  public static ModePreset from(String mode) {
    if (mode == null || mode.isEmpty()) {
      return NONE;
    }
    switch (mode.toLowerCase()) {
      case "agent":
        return AGENT;
      case "debug":
        return DEBUG;
      case "human":
        return HUMAN;
      default:
        return NONE;
    }
  }
}
