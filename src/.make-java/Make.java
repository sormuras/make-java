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
import java.util.function.Consumer;
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
  private final Tool.Planner planner;
  private final List<String> log;

  Make(Logger logger, Folder folder, Project project, Tool.Planner planner) {
    this.logger = logger;
    this.folder = folder;
    this.project = project;
    this.planner = planner;
    this.log = new ArrayList<>();
    log(Level.INFO, "%s", this);
    log(Level.DEBUG, "Java %s", Runtime.version());
    log(Level.DEBUG, "Folder %s", folder);
    log(Level.DEBUG, "Project %s", project);
    log(Level.DEBUG, "Planner %s", planner);
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

  private synchronized String log(Level level, String format, Object... args) {
    var message = String.format(format, args);
    log.add(Instant.now() + "|" + level + "|" + Thread.currentThread().getName() + "|" + message);
    logger.log(level, message);
    return message;
  }

  public Make run() {
    log(Level.INFO, "Make %s %s", project.name(), project.version());
    var plan = planner.run(this, List.of());
    if (Boolean.getBoolean("dry-run")) {
      Tool.print(plan);
      return this;
    }
    return run(plan);
  }

  public Make run(Tool.Call call) {
    return run(call, "");
  }

  private Make run(Tool.Call call, String indent) {
    log(Level.DEBUG, indent + "run(%s)", call);

    if (call instanceof Tool.Plan) {
      var plan = ((Tool.Plan) call);
      var calls = plan.calls();
      if (calls.isEmpty()) return this;
      var stream = plan.parallel() ? calls.stream().parallel() : calls.stream();
      stream.forEach(child -> run(child, indent + " "));
      log(Level.DEBUG, indent + "end(%s)", call.name());
      return this;
    }

    if (Boolean.getBoolean("dry-run")) return this;

    var tool = ToolProvider.findFirst(call.name());
    if (tool.isPresent()) {
      var out = new StringWriter();
      var err = new StringWriter();
      var array = call.args().toArray(String[]::new);
      var code = tool.get().run(new PrintWriter(out, true), new PrintWriter(err, true), array);
      out.toString().lines().forEach(line -> log(Level.TRACE, indent + "  %s", line));
      err.toString().lines().forEach(line -> log(Level.WARNING, indent + "  %s", line));
      if (code != 0) {
        var message = log(Level.ERROR, "%s run failed: %d", call.name(), code);
        throw new Error(message, new RuntimeException(err.toString()));
      }
      return this;
    }

    try {
      Tool.Default.valueOf(call.name()).run(this, call.args());
    } catch (Exception e) {
      var message = log(Level.ERROR, "%s run failed: %s -> ", call.name(), e.getMessage());
      throw new Error(message, e);
    }

    return this;
  }

  @Override
  public String toString() {
    return "Make.java " + VERSION;
  }

  /** Simple Logger API. */
  public interface Logger {
    /** Log the formatted message at the specified level. */
    Logger log(Level level, String format, Object... args);

    /** Create default logger printing to {@link System#out} and {@link System#err}. */
    static Logger ofSystem() {
      return ofSystem(Boolean.getBoolean("verbose"));
    }

    /** Create default logger printing to {@link System#out} and {@link System#err}. */
    static Logger ofSystem(boolean verbose) {
      class SystemLogger implements Logger {
        private final Instant start = Instant.now();

        @Override
        public Logger log(Level level, String format, Object... args) {
          if (level.compareTo(Level.INFO) < 0 && !verbose) return this;
          var millis = Duration.between(start, Instant.now()).toMillis();
          var message = String.format(format, args);
          var stream = level.compareTo(Level.WARNING) < 0 ? System.out : System.err;
          stream.printf(verbose ? "%7d|%7s| %s%n" : "%3$s%n", millis, level, message);
          return this;
        }
      }
      return new SystemLogger();
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
    static void print(Call call) {
      print(call, "", "\t", System.out::println);
    }

    static void print(Call call, String indent, String increment, Consumer<String> consumer) {
      consumer.accept(indent + call);
      if (call instanceof Tool.Plan) {
        var plan = ((Tool.Plan) call);
        for (var child : plan.calls()) print(child, indent + increment, increment, consumer);
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
      /** Writes all log messages to file specified by the first argument. */
      WRITE_SUMMARY {
        @Override
        public Make run(Make make, List<String> arguments) throws Exception {
          Files.write(Path.of(arguments.get(0)), make.log);
          return make;
        }
      };

      Call call(String... args) {
        return Call.of(name(), args);
      }
    }

    /** A tool call is composed of a name and the arguments. */
    interface Call {

      String name();

      List<String> args();

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

      static /*record*/ Plan of(String name, boolean parallel, Call... calls) {
        return new Plan() {

          private final String $ = name + " {" + calls.length + (parallel ? " + parallel}" : "}");

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
            return List.of(calls);
          }
        };
      }
    }

    /** Planner plans plans. */
    class Planner implements Tool<Plan> {

      /** Plan all realm compilations. */
      public Plan compile(Make make) {
        var compileRealms = make.project().realms().stream().map(realm -> compile(make, realm));
        return Plan.of("Compile", false, compileRealms.toArray(Call[]::new));
      }

      /** Plan single realm compilation. */
      public Plan compile(Make make, Project.Realm realm) {
        if (realm.modules().isEmpty()) {
          return Plan.of(String.format("No modules in %s realm", realm.name()), false);
        }
        var folder = make.folder();
        var moduleSourcePath =
            realm.moduleSourcePaths().stream()
                .map(path -> folder.src().resolve(path))
                .map(Path::toString)
                .collect(Collectors.joining(File.pathSeparator))
                .replace("${REALM}", realm.path().toString())
                .replace("${MODULE}", "*");
        var modulePath =
            realm.dependencies().stream()
                .map(other -> folder.out("modules", other.name())) // or "classes"
                .map(Path::toString)
                .collect(Collectors.joining(File.pathSeparator));
        var destination = folder.out("classes", realm.path().toString());
        return Plan.of(
            String.format("Compile %s realm", realm.name()),
            false,
            Call.newCall("javac")
                .add("--module", String.join(",", realm.modules()))
                .add("--module-source-path", moduleSourcePath)
                .add(!modulePath.isEmpty(), "--module-path", modulePath)
                .add("-d", destination)
                .build(),
            jar(make, realm));
      }

      public Plan jar(Make make, Project.Realm realm) {
        if (realm.modules().isEmpty()) {
          return Plan.of(String.format("No modules in %s realm", realm.name()), false);
        }
        var folder = make.folder();
        var destination = folder.out("modules", realm.path().toString());
        var calls = new ArrayList<Call>();
        for (var module : realm.modules()) {
          var file = destination.resolve(module + "-" + make.project().version() + ".jar");
          var content = folder.out("classes", realm.path().toString(), module);
          calls.add(
              Call.newCall("jar")
                  .add("--create")
                  .add("--file", file)
                  // .add(make.logger.verbose(), "--verbose")
                  .add("-C", content)
                  .add(".")
                  .build());
        }
        return Plan.of(
            String.format("Jar %s module(s)", realm.name()),
            false,
            Default.CREATE_DIRECTORIES.call(destination.toString()),
            Plan.of("Jar calls", true, calls.toArray(Call[]::new)));
      }

      /** Creates the master build plan. */
      @Override
      public Plan run(Make make, List<String> arguments) {
        var folder = make.folder();
        return Plan.of(
            "/",
            false,
            Default.CREATE_DIRECTORIES.call(folder.out().toString()),
            Plan.of(
                "Print version of each provided tool",
                true,
                Call.of("javac", "--version"),
                Call.of("jar", "--version"),
                Call.of("javadoc", "--version")),
            compile(make),
            Default.WRITE_SUMMARY.call(folder.out("summary.log").toString()));
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
}
