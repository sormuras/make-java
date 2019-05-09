import static java.lang.System.Logger.Level.DEBUG;
import static java.lang.System.Logger.Level.ERROR;
import static java.lang.System.Logger.Level.INFO;
import static java.lang.System.Logger.Level.WARNING;

import java.io.File;
import java.io.PrintWriter;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.spi.ToolProvider;
import java.util.stream.Collectors;

/** Modular project model and maker. */
@SuppressWarnings("WeakerAccess")
class Make implements ToolProvider {

  /** Version is either {@code master} or {@link Runtime.Version#parse(String)}-compatible. */
  static final String VERSION = "master";

  /** Convenient short-cut to {@code "user.dir"} as a path. */
  static final Path USER_PATH = Path.of(System.getProperty("user.dir"));

  /** Set "single line" logging format system property, unless the property is already set. */
  static void installSingleLineSimpleFormatterFormat() {
    var format = "java.util.logging.SimpleFormatter.format";
    if (System.getProperty(format) != null) {
      return;
    }
    System.setProperty(format, "| %5$s%6$s%n");
  }

  /** Main entry-point. */
  public static void main(String... args) {
    installSingleLineSimpleFormatterFormat();
    var code = new Make().run(System.out, System.err, args);
    if (code != 0) {
      throw new Error("Make.java failed with error code: " + code);
    }
  }

  /** Logger instance. */
  final System.Logger logger;
  /** Logger level. */
  final System.Logger.Level level;
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

  Make() {
    this(
        System.getLogger("Make.java"),
        Boolean.getBoolean("ebug") ? INFO : DEBUG,
        Boolean.getBoolean("ry-run"),
        "project",
        "1.0.0-SNAPSHOT",
        USER_PATH,
        USER_PATH.resolve("work"),
        List.of(new Realm("main", Path.of("src", "main"))));
  }

  Make(
      System.Logger logger,
      System.Logger.Level level,
      boolean dryRun,
      String project,
      String version,
      Path home,
      Path work,
      List<Realm> realms) {
    this.logger = logger;
    this.level = level;
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
    logger.log(INFO, "{0} - {1}", name(), VERSION);
    logger.log(level, "  args = {0}", List.of(args));
    logger.log(INFO, "Building {0} {1}", project, version);
    logger.log(level, "  home = {0}", home.toUri());
    logger.log(level, "  work = {0}", work.base.toUri());
    for (int i = 0; i < realms.size(); i++) {
      logger.log(level, "  realms[{0}] = {1}", i, realms.get(i));
    }
    if (dryRun) {
      logger.log(level, "Dry-run ends here.");
      return 0;
    }
    var run = new Run(out, err);
    try {
      Files.createDirectories(work.base);
      for (var realm : realms) {
        build(run, realm);
      }
      logger.log(level, "Build successful after {0} ms.", run.toDurationMillis());
      return 0;
    } catch (Throwable t) {
      logger.log(ERROR, "Build failed: " + t, t);
      return 1;
    }
  }

  private void build(Run run, Realm realm) throws Exception {
    var moduleSourcePath = home.resolve(realm.source);
    if (Files.notExists(moduleSourcePath)) {
      logger.log(WARNING, "Source path of {0} realm not found: {1}", realm.name, moduleSourcePath);
      return;
    }
    var modules = Util.listDirectoryNames(moduleSourcePath);
    if (modules.isEmpty()) {
      throw new Error("No module directories found in source path: " + moduleSourcePath);
    }
    Files.createDirectories(work.packagedModules);
    // multi-release modules
    var regularModules = new ArrayList<>(modules);
    var regularModulesIterator = regularModules.listIterator();
    while (regularModulesIterator.hasNext()) {
      var module = regularModulesIterator.next();
      if (Files.notExists(moduleSourcePath.resolve(module).resolve("module-info.java"))) {
        logger.log(level, "multi-release: {0}", module);
        var builder = new MultiReleaseBuilder(run, realm);
        builder.build(module);
        regularModulesIterator.remove();
      }
    }
    // compile and package regular "jigsaw" modules
    if (!regularModules.isEmpty()) {
      logger.log(level, "regular modules: {0}", regularModules);
      var args =
          new Args()
              .with("-d", work.compiledModules)
              .with("--module-version", version)
              .with("--module-source-path", moduleSourcePath)
              .with("--module", String.join(",", regularModules));
      run.tool("javac", args.toStringArray());
      for (var module : regularModules) {
        var file = work.packagedModules.resolve(module + "@" + version + ".jar");
        args =
            new Args()
                .with("--create")
                .with("--file", file)
                .with("-C", work.compiledModules.resolve(module))
                .with(".");
        run.tool("jar", args.toStringArray());
      }
    }
  }

  /** Workspace paths and other assets. */
  class Work {
    final Path base;
    final Path compiledBase;
    final Path compiledModules;
    final Path compiledMulti;
    final Path packagedModules;

    Work(Path base) {
      this.base = base;
      compiledBase = base.resolve("compiled");
      compiledModules = compiledBase.resolve("modules");
      compiledMulti = compiledBase.resolve("multi-releases");
      packagedModules = base.resolve("modules");
    }
  }

  /** Command-line program argument list builder. */
  static class Args extends ArrayList<String> {
    /** Add single argument by invoking {@link Object#toString()} on the given argument. */
    Args with(Object argument) {
      add(argument.toString());
      return this;
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
  class Run {
    /** Stream to which "expected" output should be written. */
    final PrintWriter out;
    /** Stream to which any error messages should be written. */
    final PrintWriter err;
    /** Time instant recorded on creation of this instance. */
    final Instant start;

    Run(Writer writer) {
      this(new PrintWriter(writer), new PrintWriter(writer));
    }

    Run(PrintWriter out, PrintWriter err) {
      this.out = out;
      this.err = err;
      this.start = Instant.now();
    }

    void tool(String name, String... args) {
      logger.log(level, "Running tool named {0} with: {1}", name, List.of(args));
      var tool = ToolProvider.findFirst(name).orElseThrow();
      var code = tool.run(out, err, args);
      if (code == 0) {
        logger.log(level, "Tool {0} successfully executed.", name);
        return;
      }
      throw new Error("Tool " + name + " execution failed with error code: " + code);
    }

    long toDurationMillis() {
      return TimeUnit.MILLISECONDS.convert(Duration.between(start, Instant.now()));
    }
  }

  /** Building block, source set, scope, directory, named context: {@code main}, {@code test}. */
  static class Realm {
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
        compileMultiReleaseModule(module, base, release);
      }
      packageMultiReleaseModule(module, base);
    }

    private void compileMultiReleaseModule(String module, int base, int release) {
      var moduleSourcePath = home.resolve(realm.source);
      var javaR = "java-" + release;
      var source = moduleSourcePath.resolve(module).resolve(javaR);
      if (Files.notExists(source)) {
        logger.log(WARNING, "Source path not found: " + source);
        return;
      }
      var destination = work.compiledMulti.resolve(javaR);
      var javac = new Args();
      // if (debug) {
      //   javac.add("-verbose");
      // }
      javac.with("--release", release);
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

    private void packageMultiReleaseModule(String module, int base) {
      var file = work.packagedModules.resolve(module + '@' + VERSION + ".jar");
      var jar =
          new Args()
              // if (debug) {
              //   jar.add("--verbose");
              // }
              .with("--create")
              .with("--file", file)
              // "base" classes
              .with("-C", work.compiledMulti.resolve("java-" + base).resolve(module))
              .with(".");

      // "base" + 1 .. N classes
      for (var release = base + 1; release <= Runtime.version().feature(); release++) {
        var path = work.compiledMulti.resolve("java-" + release).resolve(module);
        if (Files.notExists(path)) {
          continue;
        }
        jar.with("--release", release);
        jar.with("-C", path);
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
