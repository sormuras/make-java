import static java.lang.System.Logger.Level.DEBUG;
import static java.lang.System.Logger.Level.ERROR;
import static java.lang.System.Logger.Level.INFO;
import static java.lang.System.Logger.Level.WARNING;

import java.io.File;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.spi.ToolProvider;
import java.util.stream.Collectors;

/** Modular project model and maker. */
class Make implements ToolProvider {

  /** Version is either {@code master} or {@link Runtime.Version#parse(String)}-compatible. */
  static final String VERSION = "master";

  /** Convenient short-cut to {@code "user.dir"} as a path. */
  static final Path USER_PATH = Path.of(System.getProperty("user.dir"));

  /** Main entry-point. */
  public static void main(String... args) {
    var code = Make.of(USER_PATH).run(System.out, System.err, args);
    if (code != 0) {
      throw new Error("Make.java failed with error code: " + code);
    }
  }

  /** Create instance using the given path as the home of project. */
  static Make of(Path home) {
    var debug = Boolean.getBoolean("ebug");
    var dryRun = Boolean.getBoolean("ry-run");
    var project = System.getProperty("project.name", home.getFileName().toString());
    var version = System.getProperty("project.version", "1.0.0-SNAPSHOT");
    var realms = List.of(Realm.of(home, "main"));
    return new Make(debug, dryRun, project, version, home, home.resolve("work"), realms);
  }

  /** Debug flag. */
  final boolean debug;
  /** Dry-run flag. */
  final boolean dryRun;
  /** Name of the project. */
  final String project;
  /** Version of the project. */
  final String version;
  /** Root path of this project. */
  final Path home;
  /** Set of well-known output directories and files. */
  final Work work;
  /** Realms of this project. */
  final List<Realm> realms;

  Make(
      boolean debug,
      boolean dryRun,
      String project,
      String version,
      Path home,
      Path work,
      List<Realm> realms) {
    this.debug = debug;
    this.dryRun = dryRun;
    this.project = project;
    this.version = version;
    this.home = home;
    this.work = new Work(work);
    this.realms = realms;
  }

  @Override
  public String name() {
    return "Make.java";
  }

  @Override
  public int run(PrintWriter out, PrintWriter err, String... args) {
    var run = new Run(debug ? System.Logger.Level.ALL : System.Logger.Level.INFO, out, err);
    return run(run, args);
  }

  int run(Run run, String... args) {
    run.log(INFO, "%s - %s", name(), VERSION);
    run.log(DEBUG, "  args = %s", List.of(args));
    run.log(DEBUG, "  java = %s", Runtime.version());
    run.log(INFO, "Building project '%s', version %s...", project, version);
    run.log(DEBUG, "  home = %s", home.toUri());
    run.log(DEBUG, "  work = %s", work.base.toUri());
    for (int i = 0; i < realms.size(); i++) {
      run.log(DEBUG, "  realms[%d] = %s", i, realms.get(i));
    }
    if (dryRun) {
      run.log(INFO, "Dry-run ends here.");
      return 0;
    }
    try {
      Files.createDirectories(work.base);
      for (var realm : realms) {
        build(run, realm);
      }
      var jars = Util.listFiles(List.of(work.packagedModules), Util::isJarFile);
      run.log(INFO, "Modular jars: " + jars);
      if (debug) {
        var jdeps =
            new Make.Args()
                .with("--module-path", work.packagedModules)
                .with("--add-modules", "ALL-MODULE-PATH")
                .with("--multi-release", "base")
                .with("-summary");
        run.tool("jdeps", jdeps.toStringArray());
      }
      // TODO Launch JUnit Platform
      // TODO Create "-javadoc.jar" for each module
      run.log(INFO, "Build successful after %d ms.", run.toDurationMillis());
      return 0;
    } catch (Throwable t) {
      run.log(ERROR, "Build failed: %s", t.getMessage());
      t.printStackTrace(run.err);
      return 1;
    }
  }

  private void build(Run run, Realm realm) throws Exception {
    var moduleSourcePath = home.resolve(realm.source);
    if (Files.notExists(moduleSourcePath)) {
      run.log(WARNING, "Source path of %s realm not found: %s", realm.name, moduleSourcePath);
      return;
    }
    var modules = Util.listDirectoryNames(moduleSourcePath);
    if (modules.isEmpty()) {
      throw new Error("No module directories found in source path: " + moduleSourcePath);
    }
    Files.createDirectories(work.packagedJavadoc);
    Files.createDirectories(work.packagedModules);
    Files.createDirectories(work.packagedSources);
    // multi-release modules
    var regularModules = new ArrayList<>(modules);
    var regularModulesIterator = regularModules.listIterator();
    while (regularModulesIterator.hasNext()) {
      var module = regularModulesIterator.next();
      if (Files.notExists(moduleSourcePath.resolve(module).resolve("module-info.java"))) {
        run.log(DEBUG, "Building multi-release module: %s", module);
        var builder = new MultiReleaseBuilder(run, realm);
        builder.build(module);
        regularModulesIterator.remove();
      }
    }
    // compile and package regular "jigsaw" modules
    if (!regularModules.isEmpty()) {
      run.log(DEBUG, "Building %d module(s): %s", regularModules.size(), regularModules);
      var args =
          new Args()
              .with(false, "-verbose")
              .with("-encoding", "UTF-8")
              .with("-Xlint")
              .with("-d", work.compiledModules)
              .with("--module-path", work.packagedModules)
              .with("--module-version", version)
              .with("--module-source-path", moduleSourcePath)
              .with("--module", String.join(",", regularModules));
      run.tool("javac", args.toStringArray());
      for (var module : regularModules) {
        var modularJar = work.packagedModules.resolve(module + "@" + version + ".jar");
        args =
            new Args()
                .with(debug, "--verbose")
                .with("--create")
                .with("--file", modularJar)
                .with("-C", work.compiledModules.resolve(module))
                .with(".");
        run.tool("jar", args.toStringArray());
        var sourcesJar = work.packagedSources.resolve(module + "@" + version + "-sources.jar");
        args =
            new Args()
                .with(debug, "--verbose")
                .with("--create")
                .with("--file", sourcesJar)
                .with("-C", moduleSourcePath.resolve(module))
                .with(".");
        run.tool("jar", args.toStringArray());
      }
      // javadoc
      var javaSources = new ArrayList<String>();
      javaSources.add(moduleSourcePath.toString());
      for (var release = Runtime.version().feature(); release >= 7; release--) {
        javaSources.add(
            String.join(File.separator, moduleSourcePath.toString(), "*", "java-" + release));
      }
      args =
          new Args()
              .with(false, "-verbose")
              .with("-encoding", "UTF-8")
              .with("-quiet")
              .with("-windowtitle", project + " " +version)
              .with("-d", work.compiledJavadoc)
              .with("--module-source-path", String.join(File.pathSeparator, javaSources))
              .with("--module", String.join(",", modules));
      run.tool("javadoc", args.toStringArray());
    }
  }

  /** Workspace paths and other assets. */
  class Work {
    final Path base;
    final Path compiledBase;
    final Path compiledJavadoc;
    final Path compiledModules;
    final Path compiledMulti;
    final Path packagedJavadoc;
    final Path packagedModules;
    final Path packagedSources;

    Work(Path base) {
      this.base = base;
      compiledBase = base.resolve("compiled");
      compiledJavadoc = compiledBase.resolve("javadoc");
      compiledModules = compiledBase.resolve("modules");
      compiledMulti = compiledBase.resolve("multi-release");
      packagedJavadoc = base.resolve("javadoc");
      packagedModules = base.resolve("modules");
      packagedSources = base.resolve("sources");
    }
  }

  /** Command-line program argument list builder. */
  static class Args extends ArrayList<String> {
    /** Add single argument by invoking {@link Object#toString()} on the given argument. */
    Args with(Object argument) {
      add(argument.toString());
      return this;
    }

    Args with(boolean condition, Object argument) {
      return condition ? with(argument) : this;
    }

    /** Add two arguments by invoking {@link #with(Object)} for the key and value elements. */
    Args with(Object key, Object value) {
      return with(key).with(value);
    }

    /** Add all arguments by invoking {@link #with(Object)} for each element. */
    Args withEach(Iterable<?> arguments) {
      arguments.forEach(this::with);
      return this;
    }

    String[] toStringArray() {
      return toArray(String[]::new);
    }
  }

  /** Runtime context information. */
  static class Run {
    /** Current logging level threshold. */
    final System.Logger.Level threshold;
    /** Stream to which "expected" output should be written. */
    final PrintWriter out;
    /** Stream to which any error messages should be written. */
    final PrintWriter err;
    /** Time instant recorded on creation of this instance. */
    final Instant start;

    Run(System.Logger.Level threshold, PrintWriter out, PrintWriter err) {
      this.threshold = threshold;
      this.out = out;
      this.err = err;
      this.start = Instant.now();
    }

    /** Log message unless threshold suppresses it. */
    void log(System.Logger.Level level, String format, Object... args) {
      if (level.getSeverity() < threshold.getSeverity()) {
        return;
      }
      var consumer = level.getSeverity() < WARNING.getSeverity() ? out : err;
      var message = String.format(format, args);
      consumer.println(message);
    }

    /** Run provided tool. */
    void tool(String name, String... args) {
      log(DEBUG, "Running tool '%s' with: %s", name, List.of(args));
      var tool = ToolProvider.findFirst(name).orElseThrow();
      var code = tool.run(out, err, args);
      if (code == 0) {
        log(DEBUG, "Tool '%s' successfully executed.", name);
        return;
      }
      throw new Error("Tool '" + name + "' execution failed with error code: " + code);
    }

    long toDurationMillis() {
      return TimeUnit.MILLISECONDS.convert(Duration.between(start, Instant.now()));
    }
  }

  /** Building block, source set, scope, directory, named context: {@code main}, {@code test}. */
  static class Realm {
    /** Create realm by guessing the module source path using its name. */
    static Realm of(Path home, String name) {
      var source =
          Util.findFirstDirectory(home, "src/" + name + "/java", "src/" + name, name)
              .orElseThrow(() -> new Error("Couldn't find module source path!"));
      return new Realm(name, source);
    }

    /** Logical name of the realm. */
    final String name;
    /** Module source path. */
    final Path source;

    Realm(String name, Path source) {
      this.name = name;
      this.source = source;
    }

    @Override
    public String toString() {
      return "Realm{" + "name=" + name + ", source=" + source + '}';
    }
  }

  class MultiReleaseBuilder {
    final Run run;
    final Realm realm;

    MultiReleaseBuilder(Run run, Realm realm) {
      this.run = run;
      this.realm = realm;
    }

    void build(String module) {
      int base = 8; // TODO Find declared low base number: "java-*"
      for (var release = base; release <= Runtime.version().feature(); release++) {
        compile(module, base, release);
      }
      jarModule(module, base);
      jarSources(module, base);
    }

    private void compile(String module, int base, int release) {
      var moduleSourcePath = home.resolve(realm.source);
      var javaR = "java-" + release;
      var source = moduleSourcePath.resolve(module).resolve(javaR);
      if (Files.notExists(source)) {
        run.log(DEBUG, "Skipping %s, no source path exists: %s", javaR, source);
        return;
      }
      var destination = work.compiledMulti.resolve(javaR);
      var javac =
          new Args()
              .with(false, "-verbose")
              .with("-encoding", "UTF-8")
              .with("-Xlint")
              .with("--release", release);
      if (release < 9) {
        javac.with("-d", destination.resolve(module));
        // TODO "-cp" ...
        javac.withEach(Util.listJavaFiles(source)); // javac.with("**/*.java");
      } else {
        javac.with("-d", destination);
        javac.with("--module-version", version);
        // TODO javac.with("--module-path", ...);
        var pathR = moduleSourcePath + File.separator + "*" + File.separator + javaR;
        var sources = List.of(pathR, "" + moduleSourcePath);
        javac.with("--module-source-path", String.join(File.pathSeparator, sources));
        javac.with(
            "--patch-module",
            module + '=' + work.compiledMulti.resolve("java-" + base).resolve(module));
        javac.with("--module", module);
      }
      run.tool("javac", javac.toStringArray());
    }

    private void jarModule(String module, int base) {
      var file = work.packagedModules.resolve(module + '@' + version + ".jar");
      var source = work.compiledMulti;
      var javaBase = source.resolve("java-" + base).resolve(module);
      var jar =
          new Args()
              .with(debug, "--verbose")
              .with("--create")
              .with("--file", file)
              // "base" classes
              .with("-C", javaBase)
              .with(".");
      // "base" + 1 .. N files
      for (var release = base + 1; release <= Runtime.version().feature(); release++) {
        var javaRelease = source.resolve("java-" + release).resolve(module);
        if (Files.notExists(javaRelease)) {
          continue;
        }
        jar.with("--release", release);
        jar.with("-C", javaRelease);
        jar.with(".");
      }
      run.tool("jar", jar.toStringArray());
    }

    private void jarSources(String module, int base) {
      var file = work.packagedSources.resolve(module + '@' + version + "-sources.jar");
      var source = home.resolve(realm.source).resolve(module);
      var javaBase = source.resolve("java-" + base);
      var jar =
          new Args()
              .with(debug, "--verbose")
              .with("--create")
              .with("--file", file)
              // "base" classes
              .with("-C", javaBase)
              .with(".");
      // "base" + 1 .. N files
      for (var release = base + 1; release <= Runtime.version().feature(); release++) {
        var javaRelease = source.resolve("java-" + release);
        if (Files.notExists(javaRelease)) {
          continue;
        }
        jar.with("--release", release);
        jar.with("-C", javaRelease);
        jar.with(".");
      }
      run.tool("jar", jar.toStringArray());
    }
  }

  /** Static helpers. */
  static final class Util {
    /** No instance permitted. */
    Util() {
      throw new Error();
    }

    /** Find first subdirectory below the given home path. */
    static Optional<Path> findFirstDirectory(Path home, String... paths) {
      return Arrays.stream(paths).map(home::resolve).filter(Files::isDirectory).findFirst();
    }

    /** Test supplied path for pointing to a regular Java source compilation unit file. */
    static boolean isJavaFile(Path path) {
      if (Files.isRegularFile(path)) {
        var name = path.getFileName().toString();
        if (name.endsWith(".java")) {
          return name.indexOf('.') == name.length() - 5; // single dot in filename
        }
      }
      return false;
    }

    /** Test supplied path for pointing to a regular Java archive file. */
    static boolean isJarFile(Path path) {
      return Files.isRegularFile(path) && path.getFileName().toString().endsWith(".jar");
    }

    /** Return list of child directories directly present in {@code root} path. */
    static List<Path> listDirectories(Path root) {
      if (Files.notExists(root)) {
        return List.of();
      }
      try (var paths = Files.find(root, 1, (path, attr) -> Files.isDirectory(path))) {
        return paths.filter(path -> !root.equals(path)).collect(Collectors.toList());
      } catch (Exception e) {
        throw new Error("findDirectories failed for root: " + root, e);
      }
    }

    /** Return sorted list of child directory names directly present in {@code root} path. */
    static List<String> listDirectoryNames(Path root) {
      return listDirectories(root).stream()
          .map(root::relativize)
          .map(Path::toString)
          .sorted()
          .collect(Collectors.toList());
    }

    /** List all regular files matching the given filter. */
    static List<Path> listFiles(Collection<Path> roots, Predicate<Path> filter) {
      var files = new ArrayList<Path>();
      for (var root : roots) {
        try (var stream = Files.walk(root)) {
          stream.filter(Files::isRegularFile).filter(filter).forEach(files::add);
        } catch (Exception e) {
          throw new Error("Finding files failed for: " + roots, e);
        }
      }
      return files;
    }

    /** List all regular Java files in given root directory. */
    static List<Path> listJavaFiles(Path root) {
      return listFiles(List.of(root), Util::isJavaFile);
    }
  }
}
