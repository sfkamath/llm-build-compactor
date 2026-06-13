package io.llmcompactor.gradle;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.gradle.api.tasks.testing.Test;
import org.gradle.api.tasks.testing.TestDescriptor;
import org.gradle.api.tasks.testing.TestListener;
import org.gradle.api.tasks.testing.TestResult;

/** Suppresses Gradle's TestCountLogger output via reflection. */
public final class TestCountLogger {

  private static final Logger LOG = Logger.getLogger(TestCountLogger.class.getName());

  private TestCountLogger() {}

  static void suppressTestCountLogger(Test task) {
    task.addTestListener(
        new TestListener() {
          @Override
          public void beforeSuite(TestDescriptor descriptor) {}

          @Override
          public void afterSuite(TestDescriptor d, TestResult r) {
            if (d.getParent() != null) {
              return;
            }
            neuter(task);
          }

          @Override
          public void beforeTest(TestDescriptor d) {}

          @Override
          public void afterTest(TestDescriptor d, TestResult r) {}
        });
  }

  private static void neuter(Test task) {
    try {
      Object tcl = findTestCountLogger(task);
      if (tcl == null) {
        return;
      }
      Class<?> tclClass = tcl.getClass();
      while (tclClass != null) {
        for (Field pf : tclClass.getDeclaredFields()) {
          String fieldName = pf.getName();
          if (!"progressLogger".equals(fieldName) && !"logger".equals(fieldName)) {
            continue;
          }
          pf.setAccessible(true);
          Object pl = pf.get(tcl);
          if (pl == null || Proxy.isProxyClass(pl.getClass())) {
            continue;
          }

          Class<?> type = pf.getType();
          if (!type.isInterface()) {
            continue;
          }

          try {
            Method completedMethod = pl.getClass().getMethod("completed");
            completedMethod.setAccessible(true);
            completedMethod.invoke(pl);
          } catch (NoSuchMethodException ignored) {
          }

          Object noop =
              Proxy.newProxyInstance(
                  type.getClassLoader(),
                  new Class<?>[] {type},
                  (proxy, method, args) -> defaultReturnValue(method.getReturnType()));
          pf.set(tcl, noop);
        }
        tclClass = tclClass.getSuperclass();
      }
    } catch (Exception e) {
      LOG.log(Level.FINE, "neuter failed", e);
    }
  }

  private static Object findTestCountLogger(Test t) {
    try {
      Class<?> clazz = t.getClass();
      while (clazz != null) {
        for (Field f : clazz.getDeclaredFields()) {
          f.setAccessible(true);
          Object subs;
          try {
            subs = f.get(t);
          } catch (Exception e) {
            continue;
          }
          if (subs == null) {
            continue;
          }

          if (subs.getClass().getName().contains("ListenerBroadcast")
              || f.getName().toLowerCase().contains("listener")) {
            Class<?> subsClass = subs.getClass();
            while (subsClass != null) {
              for (Field f2 : subsClass.getDeclaredFields()) {
                f2.setAccessible(true);
                Object val;
                try {
                  val = f2.get(subs);
                } catch (Exception e) {
                  continue;
                }
                if (val instanceof List) {
                  for (Object listener : (List<?>) val) {
                    if (listener == null) continue;
                    String name = listener.getClass().getName();
                    if (name.startsWith("org.gradle.")
                        && (name.contains("TestCountLogger")
                            || name.contains("TestSummaryListener"))) {
                      return listener;
                    }
                  }
                } else {
                  LOG.fine(
                      "findTestCountLogger expected List but got "
                          + (val == null ? "null" : val.getClass().getName()));
                }
              }
              subsClass = subsClass.getSuperclass();
            }
          }
        }
        clazz = clazz.getSuperclass();
      }
    } catch (Exception e) {
      LOG.log(Level.FINE, "findTestCountLogger failed", e);
    }
    return null;
  }

  private static Object defaultReturnValue(Class<?> returnType) {
    if (!returnType.isPrimitive()) {
      return null;
    }
    if (returnType == boolean.class) {
      return false;
    }
    if (returnType == int.class) {
      return 0;
    }
    if (returnType == long.class) {
      return 0L;
    }
    if (returnType == double.class) {
      return 0.0;
    }
    if (returnType == float.class) {
      return 0.0f;
    }
    if (returnType == short.class) {
      return (short) 0;
    }
    if (returnType == byte.class) {
      return (byte) 0;
    }
    if (returnType == char.class) {
      return '\0';
    }
    return null;
  }
}
