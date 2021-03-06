/*
 * Make.java - Modular Java Build Tool
 * Copyright (C) 2020 Christian Stein
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

// default package

import java.io.File;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.System.Logger.Level;
import java.lang.module.ModuleDescriptor;
import java.lang.module.ModuleDescriptor.Version;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.regex.Pattern;
import java.util.spi.ToolProvider;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** Modular Java Build Tool. */
public class Make {

  /** Version string. */
  public static final String VERSION = "1-ea";

  private final Logger logger;
  private final Folder folder;
  private final Project project;
  private final Tool.Plan plan;
  private final Summary summary;

  Make(Logger logger, Folder folder, Project project, Tool.Plan plan) {
    this.logger = logger;
    this.folder = folder;
    this.project = project;
    this.plan = plan;
    this.summary = new Summary();
    log(Level.INFO, "%s", this);
    log(Level.DEBUG, "Java %s", Runtime.version());
    log(Level.DEBUG, "Folder %s", folder());
    log(Level.DEBUG, "Project %s", project());
    log(Level.DEBUG, "Plan %s", plan());
  }

  public Logger logger() {
    return logger;
  }

  public Folder folder() {
    return folder;
  }

  public Project project() {
    return project;
  }

  public Tool.Plan plan() {
    return plan;
  }

  private synchronized String log(Level level, String format, Object... args) {
    var message = String.format(format, args);
    var entry = Logger.Entry.of(level, message);
    summary.entries.add(entry);
    logger().log(entry);
    return message;
  }

  public Make run() {
    log(Level.INFO, "Make %s %s", project().name(), project().version());
    if (logger().verbose()) Tool.print(plan());
    if (Boolean.getBoolean("dry-run")) return this;
    if (project().realms().stream().mapToLong(realm -> realm.modules().size()).sum() == 0) {
      log(Level.WARNING, "No modules defined in project!");
      return this;
    }
    return run(plan());
  }

  public Make run(Tool.Call call) {
    if (call instanceof Tool.Plan) {
      var plan = ((Tool.Plan) call);
      var calls = plan.calls();
      if (calls.isEmpty()) return this;
      // log(Level.DEBUG, "┌─ %s", call);
      var stream = plan.parallel() ? calls.stream().parallel() : calls.stream();
      var start = Instant.now();
      stream.forEach(this::run);
      var duration = Duration.between(start, Instant.now()).toMillis();
      log(Level.DEBUG, "%d ms for running: %s", duration, plan.name());
      return this;
    }
    runTool(call);
    return this;
  }

  private void runTool(Tool.Call call) {
    if (call instanceof Tool.Plan) throw new IllegalArgumentException("No plan!");
    log(Level.DEBUG, "· %s", call);
    if (Boolean.getBoolean("dry-run")) return;

    var tool = ToolProvider.findFirst(call.name());
    if (tool.isPresent()) {
      var out = new StringWriter();
      var err = new StringWriter();
      var array = call.args().toArray(String[]::new);
      var code = tool.get().run(new PrintWriter(out), new PrintWriter(err), array);
      out.toString().lines().forEach(line -> log(Level.TRACE, "  %s", line));
      err.toString().lines().forEach(line -> log(Level.WARNING, "  %s", line));
      if (code != 0) {
        var message = log(Level.ERROR, "%s run failed: %d", call.name(), code);
        throw new Error(message, new RuntimeException(err.toString()));
      }
      return;
    }

    try {
      Tool.Default.valueOf(call.name()).run(this, call.args());
    } catch (Exception e) {
      var message = log(Level.ERROR, "%s run failed: %s -> ", call.name(), e.getMessage());
      throw new Error(message, e);
    }
  }

  @Override
  public String toString() {
    return "Make.java " + VERSION;
  }

  /** Simple Logger API. */
  public interface Logger {

    /** Log the formatted message at the specified level. */
    default Logger log(Level level, String format, Object... args) {
      return log(Entry.of(level, String.format(format, args)));
    }

    /** Log the given entry. */
    Logger log(Entry entry);

    default boolean verbose() {
      return false;
    }

    interface /*record*/ Entry {
      long thread();

      Instant instant();

      Level level();

      String message();

      default String toString(Instant start) {
        var level = level().getName().substring(0, 1);
        var split = Duration.between(start, instant()).toMillis();
        var thread = thread() == 1 ? "main" : String.format("%4X", thread());
        return String.format("%s %5s %s| %s", level, split, thread, message());
      }

      static Entry of(Level level, String message) {
        var thread = Thread.currentThread().getId();
        var instant = Instant.now();
        return new Entry() {

          @Override
          public long thread() {
            return thread;
          }

          @Override
          public Instant instant() {
            return instant;
          }

          @Override
          public Level level() {
            return level;
          }

          @Override
          public String message() {
            return message;
          }
        };
      }
    }

    /** Create default logger printing to {@link System#out} and {@link System#err}. */
    static Logger ofSystem() {
      return ofSystem(Boolean.getBoolean("verbose"));
    }

    /** Create default logger printing to {@link System#out} and {@link System#err}. */
    static Logger ofSystem(boolean verbose) {
      class SystemLogger implements Logger {
        private final Instant start = Instant.now();

        @Override
        public Logger log(Entry entry) {
          var level = entry.level();
          if (level.compareTo(Level.INFO) < 0 && !verbose) return this;
          var stream = level.compareTo(Level.WARNING) < 0 ? System.out : System.err;
          stream.println(verbose ? entry.toString(start) : entry.message());
          return this;
        }

        @Override
        public boolean verbose() {
          return verbose;
        }
      }
      return new SystemLogger();
    }
  }

  /** Build summary. */
  private class Summary {

    List<Logger.Entry> entries = new ArrayList<>();

    void write(Path file) throws Exception {
      var lines = new ArrayList<String>();
      lines.add("# Project Build Summary");
      lines.add("## Plan");
      Tool.print(plan(), "", "  ", (indent, call) -> lines.add(indent + " - " + call.toMarkDown()));
      lines.add("## Log");
      lines.add("```log");
      entries.forEach(entry -> lines.add(" - " + entry.toString(Instant.EPOCH)));
      lines.add("```");
      Files.write(file, lines);
    }
  }

  /** Well-known directory and file locations. */
  public /*record*/ static final class Folder {

    public static Folder ofCurrentWorkingDirectory() {
      return of(Path.of(""));
    }

    public static Folder of(Path base) {
      return new Folder(base, base.resolve("src"), base.resolve("lib"), base.resolve(".make-java"));
    }

    private static Path resolve(Path path, String... more) {
      if (more.length == 0) return path;
      return path.resolve(String.join("/", more)).normalize();
    }

    private final Path base;
    private final Path src;
    private final Path lib;
    private final Path out;

    public Folder(Path base, Path src, Path lib, Path out) {
      this.base = base;
      this.src = src;
      this.lib = lib;
      this.out = out;
    }

    public Path base() {
      return base;
    }

    public Path base(String... more) {
      return resolve(base, more);
    }

    public Path src() {
      return src;
    }

    public Path src(String... more) {
      return resolve(src, more);
    }

    public Path lib() {
      return lib;
    }

    public Path out() {
      return out;
    }

    public Path out(String... more) {
      return resolve(out, more);
    }
  }

  /** Tool API with tool call, tool plan, and planner support. */
  public interface Tool<R> {

    /** Run this tool. */
    R run(Make make, List<String> arguments) throws Exception;

    /** Recursively print the given tool call to {@link System#out} */
    static void print(Call root) {
      print(root, "", "\t", (indent, call) -> System.out.printf("%s%s%n", indent, call));
    }

    static void print(Call call, String indent, String inc, BiConsumer<String, Call> consumer) {
      consumer.accept(indent, call);
      if (call instanceof Tool.Plan) {
        var plan = ((Tool.Plan) call);
        for (var child : plan.calls()) print(child, indent + inc, inc, consumer);
      }
    }

    /** Built-in tool implementations. */
    enum Default implements Tool<Make> {
      /** @see Files#createDirectories(Path, java.nio.file.attribute.FileAttribute[]) */
      CREATE_DIRECTORIES {
        @Override
        public Make run(Make make, List<String> arguments) throws Exception {
          Files.createDirectories(Path.of(arguments.get(0)));
          return make;
        }
      },
      /** Writes all log messages to the file specified by the first argument. */
      WRITE_SUMMARY {
        @Override
        public Make run(Make make, List<String> arguments) throws Exception {
          make.summary.write(Path.of(arguments.get(0)));
          return make;
        }
      };

      Call args(Object... args) {
        return Call.newCall(name(), args).build();
      }
    }

    /** A tool call is composed of a name and the arguments. */
    interface Call {

      String name();

      List<String> args();

      default String toMarkDown() {
        return "`" + toString() + "`";
      }

      static Builder newCall(String name, Object... initials) {
        return new Builder(name, initials);
      }

      static /*record*/ Call of(String name, String... args) {
        return new Call() {

          private final String $ = name + (args.length == 0 ? "" : " " + String.join(" ", args));

          @Override
          public String toString() {
            return $;
          }

          @Override
          public String name() {
            return name;
          }

          @Override
          public List<String> args() {
            return List.of(args);
          }
        };
      }

      class Builder {

        private final String name;
        private final List<Object> args;

        Builder(String name, Object... initials) {
          this.name = name;
          this.args = new ArrayList<>();
          for (var initial : initials) add(initial);
        }

        public Call build() {
          return Call.of(name, args.stream().map(Object::toString).toArray(String[]::new));
        }

        public Builder add(Object arg) {
          args.add(arg);
          return this;
        }

        public Builder add(String key, Object value) {
          return add(key).add(value);
        }

        public Builder add(boolean iff, Object arg) {
          if (iff) add(arg);
          return this;
        }

        public Builder add(boolean iff, String key, Object value) {
          if (iff) add(key, value);
          return this;
        }

        public <T> Builder forEach(Iterable<T> iterable, BiConsumer<Builder, T> visitor) {
          iterable.forEach(item -> visitor.accept(this, item));
          return this;
        }
      }
    }

    /** A list of tool calls. */
    interface Plan extends Call {

      boolean parallel();

      List<Call> calls();

      @Override
      default String toMarkDown() {
        return toString();
      }

      static Plan of(Logger logger, Folder folder, Project project) {
        return new Make.Planner(logger, folder, project).build();
      }

      static /*record*/ Plan of(String name, boolean parallel, Call... calls) {
        return of(name, parallel, List.of(calls));
      }

      static /*record*/ Plan of(String name, boolean parallel, List<Call> calls) {
        return new Plan() {

          private final String $ = name + " {" + calls.size() + (parallel ? " + parallel}" : "}");

          @Override
          public String toString() {
            return $;
          }

          @Override
          public String name() {
            return name;
          }

          @Override
          public List<String> args() {
            return List.of();
          }

          @Override
          public boolean parallel() {
            return parallel;
          }

          @Override
          public List<Call> calls() {
            return calls;
          }
        };
      }
    }
  }

  /** Project model. */
  public /*record*/ static class Project {

    private final String name;
    private final Version version;
    private final Layout layout;
    private final List<Realm> realms;

    Project(String name, Version version, Layout layout, List<Realm> realms) {
      this.name = name;
      this.version = version;
      this.layout = layout;
      this.realms = List.copyOf(realms);
    }

    public String name() {
      return name;
    }

    public Version version() {
      return version;
    }

    public Layout layout() {
      return layout;
    }

    public List<Realm> realms() {
      return realms;
    }

    public static class Builder {

      private String name = "project";
      private String version = "1-ea";
      private Layout layout = Layout.DEFAULT;
      private List<Realm> realms = new ArrayList<>();

      public static Builder of(Logger logger, Folder folder) {
        var builder = new Builder();
        var absolute = folder.base().toAbsolutePath();
        logger.log(Level.TRACE, "Parsing directory '%s' for project properties.", absolute);
        Optional.ofNullable(absolute.getFileName()).map(Path::toString).ifPresent(builder::setName);
        var layout = Layout.valueOf(folder.src()).orElse(Layout.DEFAULT);
        builder.setLayout(layout);
        switch (layout) {
          case DEFAULT:
            var main = Realm.Builder.of(logger, "main", Path.of("main"), folder, layout).build();
            var test =
                Realm.Builder.of(logger, "test", Path.of("test"), folder, layout)
                    .setRealms(List.of(main))
                    .build();
            builder.setRealms(List.of(main, test));
            break;
          case JIGSAW:
            var realm = Realm.Builder.of(logger, "default", Path.of("."), folder, layout).build();
            builder.setRealms(List.of(realm));
            break;
        }
        return builder;
      }

      public Project build() {
        return new Project(name, Version.parse(version), layout, realms);
      }

      public Builder setName(String name) {
        this.name = name;
        return this;
      }

      public Builder setVersion(String version) {
        this.version = version;
        return this;
      }

      public Builder setLayout(Layout layout) {
        this.layout = layout;
        return this;
      }

      public Builder setRealms(List<Realm> realms) {
        this.realms = realms;
        return this;
      }
    }

    /** Module declaration information. */
    public /*record*/ static class Info {

      private final Path path;
      private final ModuleDescriptor descriptor;

      public Info(Path path, ModuleDescriptor descriptor) {
        this.path = path;
        this.descriptor = descriptor;
      }

      public Path path() {
        return path;
      }

      public ModuleDescriptor descriptor() {
        return descriptor;
      }

      public String name() {
        return descriptor().name();
      }
    }

    /** Module source directory tree layout. */
    public enum Layout {
      /**
       * Default tree layout of main/test realms with nested java/resources directories.
       *
       * <ul>
       *   <li>{@code src/${MODULE}/${REALM}/java/module-info.java}
       * </ul>
       *
       * Module source path examples:
       *
       * <ul>
       *   <li>{@code --module-source-path src/ * /main/java}
       *   <li>{@code --module-source-path src/ * /test/java:src/ * /test/module}
       * </ul>
       */
      DEFAULT(
          Pattern.compile(".+/(main|test)/(java|module)"),
          List.of(Path.of("${MODULE}", "main", "java")),
          List.of(Path.of("${MODULE}", "test", "java"), Path.of("${MODULE}", "test", "module"))),

      /**
       * Simple directory tree layout.
       *
       * <ul>
       *   <li>{@code src/${MODULE}/module-info.java}
       * </ul>
       *
       * Module source path example:
       *
       * <ul>
       *   <li>{@code --module-source-path src}
       * </ul>
       *
       * @see <a href="https://openjdk.java.net/projects/jigsaw/quick-start">Project Jigsaw: Module
       *     System Quick-Start Guide</a>
       */
      JIGSAW(Pattern.compile(".+"), List.of(Path.of("")), List.of(Path.of("")));

      private final Pattern pattern;
      private final List<Path> mainPaths;
      private final List<Path> testPaths;
      private final long separators;

      Layout(Pattern pattern, List<Path> mainPaths, List<Path> testPaths) {
        this.pattern = pattern;
        this.separators = pattern.toString().chars().filter(c -> c == '/').count();
        this.mainPaths = mainPaths;
        this.testPaths = testPaths;
      }

      public List<Path> paths(String realm) {
        switch (realm) {
          case "default":
          case "main":
            return mainPaths;
          case "test":
            return testPaths;
        }
        throw new IllegalArgumentException("Unsupported realm name: " + realm);
      }

      public List<Path> paths(String realm, String module) {
        return paths(realm).stream()
            .map(Path::toString)
            .map(s -> s.contains("${MODULE}") ? s.replace("${MODULE}", module) : module)
            .map(Path::of)
            .collect(Collectors.toList());
      }

      public boolean matches(String string) {
        return separators == string.chars().filter(c -> c == '/').count()
            && pattern.matcher(string).matches();
      }

      public Set<Info> find(Folder folder, String realm) {
        var root = folder.src();
        try (var stream = Files.find(root, 5, (path, __) -> path.endsWith("module-info.java"))) {
          switch (this) {
            case DEFAULT:
              return stream
                  .map(root::relativize)
                  .filter(path -> path.getName(1).toString().equals(realm))
                  .map(
                      path ->
                          new Info(
                              path, ModuleDescriptor.newModule(path.getName(0).toString()).build()))
                  .collect(Collectors.toSet());
            case JIGSAW:
              return stream
                  .map(root::relativize)
                  .map(
                      path ->
                          new Info(
                              path, ModuleDescriptor.newModule(path.getName(0).toString()).build()))
                  .collect(Collectors.toSet());
          }
        } catch (Exception e) {
          throw new RuntimeException(e);
        }
        return Set.of();
      }

      /** Return modular layout constant of the specified root directory. */
      public static Optional<Layout> valueOf(Path root) {
        try (var stream = Files.find(root, 5, (path, __) -> path.endsWith("module-info.java"))) {
          return valueOf(stream.map(root::relativize));
        } catch (Exception e) {
          throw new RuntimeException(e);
        }
      }

      /** Return modular layout constant matching all given paths. */
      public static Optional<Layout> valueOf(Stream<Path> paths) {
        var strings =
            paths
                .map(Path::getParent)
                .map(Path::toString)
                .map(string -> string.replace(File.separatorChar, '/'))
                .collect(Collectors.toList());
        if (strings.isEmpty()) return Optional.empty();
        for (var layout : values()) {
          if (strings.stream().allMatch(layout::matches)) return Optional.of(layout);
        }
        return Optional.empty();
      }
    }

    /** A named realm configuration, like "main" or "test". */
    public /*record*/ static final class Realm {

      private final String name;
      private final Path path;
      private final List<String> modules;
      private final List<Path> moduleSourcePaths;
      private final List<Realm> dependencies;

      public Realm(
          String name,
          Path path,
          List<String> modules,
          List<Path> moduleSourcePaths,
          Realm... dependencies) {
        this.name = name;
        this.path = path;
        this.modules = List.copyOf(modules);
        this.moduleSourcePaths = List.copyOf(moduleSourcePaths);
        this.dependencies = List.of(dependencies);
      }

      public String name() {
        return name;
      }

      public Path path() {
        return path;
      }

      public List<String> modules() {
        return modules;
      }

      public List<Path> moduleSourcePaths() {
        return moduleSourcePaths;
      }

      public List<Realm> dependencies() {
        return dependencies;
      }

      public String moduleSourcePath(Folder folder) {
        return moduleSourcePaths().stream()
            .map(path -> folder.src().resolve(path))
            .map(Path::toString)
            .collect(Collectors.joining(File.pathSeparator))
            .replace("${REALM}", path().toString())
            .replace("${MODULE}", "*");
      }

      public String modulePath(Folder folder) {
        return dependencies().stream()
            .map(dependency -> folder.out("modules", dependency.name())) // or "classes"
            .map(Path::toString)
            .collect(Collectors.joining(File.pathSeparator));
      }

      public static class Builder {

        public static Builder of(
            Logger logger, String realm, Path path, Folder folder, Layout layout) {
          var src = folder.src();
          logger.log(Level.TRACE, "Parsing '%s' folder for %s realm assets: %s", src, realm, path);
          var info = layout.find(folder, realm);
          return new Builder(realm)
              .setPath(path)
              .setModules(info.stream().map(Info::name).collect(Collectors.toList()))
              .setModuleSourcePaths(layout.paths(realm));
        }

        private final String name;
        private Path path;
        private List<String> modules;
        private List<Path> moduleSourcePaths;
        private List<Realm> realms;

        public Realm build() {
          return new Realm(name, path, modules, moduleSourcePaths, realms.toArray(Realm[]::new));
        }

        public Builder(String name) {
          this.name = name;
          this.setPath(Path.of(name.isBlank() ? "." : name))
              .setModules(List.of())
              .setModuleSourcePaths(Layout.DEFAULT.paths(name))
              .setRealms(List.of());
        }

        public Builder setPath(Path path) {
          this.path = path;
          return this;
        }

        public Builder setModules(List<String> modules) {
          this.modules = modules;
          return this;
        }

        public Builder setModuleSourcePaths(List<Path> moduleSourcePaths) {
          this.moduleSourcePaths = moduleSourcePaths;
          return this;
        }

        public Builder setRealms(List<Realm> realms) {
          this.realms = realms;
          return this;
        }
      }
    }
  }

  /** Planner plans plans. */
  public static class Planner {

    private final Logger logger;
    private final Folder folder;
    private final Project project;

    public Planner(Logger logger, Folder folder, Project project) {
      this.logger = logger;
      this.folder = folder;
      this.project = project;
    }

    public Logger logger() {
      return logger;
    }

    public Folder folder() {
      return folder;
    }

    public Project project() {
      return project;
    }

    /** Plan all realm compilations. */
    public Tool.Plan compile() {
      var compileRealms = project().realms().stream().map(this::compile);
      return Tool.Plan.of("Compile", false, compileRealms.collect(Collectors.toList()));
    }

    /** Plan single realm compilation. */
    public Tool.Plan compile(Project.Realm realm) {
      if (realm.modules().isEmpty()) {
        return Tool.Plan.of(String.format("No modules in %s realm", realm.name()), false);
      }
      var modulePath = realm.modulePath(folder);
      var classes = folder.out("classes", realm.path().toString());
      return Tool.Plan.of(
          String.format("Compile %s realm", realm.name()),
          false,
          Tool.Call.newCall("javac")
              .add("--module", String.join(",", realm.modules()))
              .add("--module-source-path", realm.moduleSourcePath(folder))
              .add(!modulePath.isEmpty(), "--module-path", modulePath)
              .add("-d", classes)
              .build(),
          jar(realm));
    }

    public Tool.Plan jar(Project.Realm realm) {
      if (realm.modules().isEmpty()) {
        return Tool.Plan.of(String.format("No modules in %s realm", realm.name()), false);
      }
      var folder = folder();
      var layout = project().layout();
      var realmPath = realm.path().toString();
      var modules = folder.out("modules", realmPath);
      var sources = folder.out("sources", realmPath);
      var calls = new ArrayList<Tool.Call>();
      for (var module : realm.modules()) {
        var file = module + "-" + project().version();
        var classes = folder.out("classes", realmPath, module);
        calls.add(
            Tool.Call.newCall("jar")
                .add("--create")
                .add("--file", modules.resolve(file + ".jar"))
                .add(logger().verbose(), "--verbose")
                .add("-C", classes)
                .add(".")
                .build());
        calls.add(
            Tool.Call.newCall("jar")
                .add("--create")
                .add("--file", sources.resolve(file + "-sources.jar"))
                .add(logger().verbose(), "--verbose")
                .add("--no-manifest")
                .forEach(
                    layout.paths(realm.name, module),
                    (call, path) -> {
                      var content = folder.src().resolve(path);
                      if (Files.isDirectory(content)) call.add("-C", content).add(".");
                    })
                .build());
      }
      return Tool.Plan.of(
          String.format("Jar %s modules and sources", realm.name()),
          false,
          Tool.Default.CREATE_DIRECTORIES.args(modules),
          Tool.Default.CREATE_DIRECTORIES.args(sources),
          Tool.Plan.of("Jar calls", true, calls.toArray(Tool.Call[]::new)));
    }

    public Tool.Plan javadoc() {
      var main = project().realms().get(0); // main
      return javadoc(main);
    }

    public Tool.Plan javadoc(Project.Realm realm) {
      if (realm.modules().isEmpty()) {
        return Tool.Plan.of(String.format("No modules in %s realm", realm.name()), false);
      }
      var file = project.name() + "-" + project.version();
      var modulePath = realm.modulePath(folder);
      var javadoc = folder.out("documentation", "javadoc");
      return Tool.Plan.of(
          "Generate API documentation and jar generated site",
          false,
          Tool.Default.CREATE_DIRECTORIES.args(javadoc),
          Tool.Call.newCall("javadoc")
              .add("--module", String.join(",", realm.modules()))
              .add("--module-source-path", realm.moduleSourcePath(folder))
              .add(!modulePath.isEmpty(), "--module-path", modulePath)
              .add("-d", javadoc)
              .add(!logger().verbose(), "-quiet")
              .build(),
          Tool.Call.newCall("jar")
              .add("--create")
              .add("--file", javadoc.getParent().resolve(file + "-javadoc.jar"))
              .add(logger().verbose(), "--verbose")
              .add("--no-manifest")
              .add("-C", javadoc)
              .add(".")
              .build());
    }

    /** Creates the build plan. */
    public Tool.Plan build() {
      return Tool.Plan.of(
          String.format("Build project '%s' version '%s'", project().name(), project().version()),
          false,
          Tool.Default.CREATE_DIRECTORIES.args(folder().out()),
          Tool.Plan.of(
              "Print version of each provided tool",
              true,
              Tool.Call.of("javac", "--version"),
              Tool.Call.of("jar", "--version"),
              Tool.Call.of("javadoc", "--version")),
          Tool.Plan.of("Compile and generate API documentation", true, compile(), javadoc()),
          Tool.Default.WRITE_SUMMARY.args(folder().out("summary.md")));
    }
  }
}
