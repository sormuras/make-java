## JUnit with isolated realms

```java
    /** Run JUnit Platform Launcher in custom class loader. */
    void junit(Realm realm, String... args) throws Exception {
      var parent = ClassLoader.getPlatformClassLoader();
      var loader = new URLClassLoader("junit-loader", new URL[] {jar.toUri().toURL()}, parent);
      var launcher = loader.loadClass("org.junit.platform.console.ConsoleLauncher");
      var execute =
          launcher.getMethod("execute", PrintStream.class, PrintStream.class, String[].class);
      var context = Thread.currentThread().getContextClassLoader();
      Object result;
      try {
        Thread.currentThread().setContextClassLoader(loader);
        result = execute.invoke(null, System.out, System.err, args);
      } catch (Throwable t) {
        throw new Error("ConsoleLauncher.execute(...) failed: " + t, t);
      }
      finally{
        Thread.currentThread().setContextClassLoader(context);
      }
      var code = (int) result.getClass().getMethod("getExitCode").invoke(result);
    }
```