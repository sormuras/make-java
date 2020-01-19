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
import java.lang.module.ModuleDescriptor.Version;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.spi.ToolProvider;
import java.util.stream.Collectors;

/** Modular Java Build Tool. */
public class Make {

  /** Version string. */
  public static final String VERSION = "1-ea";

  private final Logger logger;
  private final Folder folder;
  private final Project project;
  private final Tool.Planner planner;
  private final List<String> log;

  Make(Logger logger) {
    this(logger, Folder.of(), Project.Builder.of(logger, Path.of("")).build(), new Tool.Planner());
  }

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
      Tool.print(new PrintWriter(System.out, true), plan, "");
      return this;
    }
    return run(plan);
  }

  public Make run(Tool.Call call) {
    return run(call, "");
  }

  private Make run(Tool.Call call, String indent) {
    var name = call.name();
    var args = call.args();
    var join = args.isEmpty() ? "" : " " + String.join(" ", args);
    log(Level.DEBUG, indent + "run(%s%s)", name, join);

    if (call instanceof Tool.Plan) {
      var plan = ((Tool.Plan) call);
      var calls = plan.calls();
      if (calls.isEmpty()) return this;
      var stream = plan.parallel() ? calls.stream().parallel() : calls.stream();
      stream.forEach(child -> run(child, indent + " "));
      log(Level.DEBUG, indent + "end(%s)", name);
      return this;
    }

    if (Boolean.getBoolean("dry-run")) return this;

    var tool = ToolProvider.findFirst(name);
    if (tool.isPresent()) {
      var out = new StringWriter();
      var err = new StringWriter();
      var array = args.toArray(String[]::new);
      var code = tool.get().run(new PrintWriter(out, true), new PrintWriter(err, true), array);
      out.toString().lines().forEach(line -> log(Level.TRACE, indent + "  %s", line));
      err.toString().lines().forEach(line -> log(Level.WARNING, indent + "  %s", line));
      if (code != 0) {
        var message = log(Level.ERROR, "%s run failed: %d", name, code);
        throw new Error(message, new RuntimeException(err.toString()));
      }
      return this;
    }

    try {
      Tool.Default.valueOf(name).run(this, args);
    } catch (Exception e) {
      var message = log(Level.ERROR, "%s run failed: %s -> ", name, e.getMessage());
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
        final Instant start = Instant.now();

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

  public /*record*/ static final class Folder {

    public static Folder of() {
      return of(Path.of(""));
    }

    public static Folder of(Path base) {
      return new Folder(base, base.resolve("src"), base.resolve("lib"), base.resolve(".make-java"));
    }

    private static Path resolve(Path path, String... more) {
      if (more.length == 0) return path;
      return path.resolve(String.join("/", more));
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

    static void print(PrintWriter writer, Tool.Call call, String indent) {
      writer.println(indent + call.name() + " " + call.args());
      if (call instanceof Tool.Plan) {
        var plan = ((Tool.Plan) call);
        for (var child : plan.calls()) print(writer, child, indent + "\t");
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

      static /*record*/ Call of(String name, String... args) {
        return new Call() {
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
    }

    /** A list of tool calls. */
    interface Plan extends Call {

      boolean parallel();

      List<Call> calls();

      static /*record*/ Plan of(String name, boolean parallel, Call... calls) {
        return new Plan() {

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

      /** Plan main and test realm compilation. */
      public Plan compile(Make make) {
        var main = make.project().main();
        var test = make.project().test();
        return Plan.of("Compile", false, compile(make, main), compile(make, test));
      }

      /** Plan single realm compilation. */
      public Plan compile(Make make, Project.Realm realm) {
        if (realm.modules().isEmpty()) {
          return Plan.of(String.format("No modules in %s realm", realm.name()), false);
        }
        var folder = make.folder();
        var moduleSourcePath =
            realm.getModuleSourcePaths().stream()
                .map(path -> folder.base().resolve(path))
                .map(Path::toString)
                .collect(Collectors.joining(File.pathSeparator))
                .replace("${MODULE}", "*");
        return Plan.of(
            String.format("Compile %s realm", realm.name()),
            false,
            Call.of(
                "javac",
                "-d",
                folder.out("classes", realm.name()).toString(),
                "--module-source-path",
                moduleSourcePath,
                "--module",
                String.join(",", realm.modules())));
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

    final String name;
    final Version version;
    final Realm main;
    final Realm test;

    Project(String name, Version version, Realm main, Realm test) {
      this.name = name;
      this.version = version;
      this.main = main;
      this.test = test;
    }

    public String name() {
      return name;
    }

    public Version version() {
      return version;
    }

    public Realm main() {
      return main;
    }

    public Realm test() {
      return test;
    }

    public static class Builder {

      private String name = "project";
      private String version = "1-ea";
      private Realm main = new Realm.Builder("main").build();
      private Realm test = new Realm.Builder("test").build();

      public static Builder of(Logger logger, Path base) {
        var builder = new Builder();
        var absolute = base.toAbsolutePath();
        logger.log(Level.TRACE, "Parsing directory '%s' for project properties.", absolute);
        Optional.ofNullable(absolute.getFileName()).map(Path::toString).ifPresent(builder::setName);
        return builder;
      }

      public Project build() {
        return new Project(name, Version.parse(version), main, test);
      }

      public Builder setName(String name) {
        this.name = name;
        return this;
      }

      public Builder setVersion(String version) {
        this.version = version;
        return this;
      }

      public Builder setMain(Realm main) {
        this.main = main;
        return this;
      }

      public Builder setTest(Realm test) {
        this.test = test;
        return this;
      }
    }

    public /*record*/ static final class Realm {

      final String name;
      final List<String> modules;
      final List<Path> moduleSourcePaths;

      public Realm(String name, List<String> modules, List<Path> moduleSourcePaths) {
        this.name = name;
        this.modules = List.copyOf(modules);
        this.moduleSourcePaths = List.copyOf(moduleSourcePaths);
      }

      public String name() {
        return name;
      }

      public List<String> modules() {
        return modules;
      }

      public List<Path> getModuleSourcePaths() {
        return moduleSourcePaths;
      }

      public static class Builder {

        final String name;
        List<String> modules;
        List<Path> moduleSourcePaths;

        public Realm build() {
          return new Realm(name, modules, moduleSourcePaths);
        }

        public Builder(String name) {
          this.name = name;
          setModules(List.of());
          setModuleSourcePaths(List.of(Path.of("src", "${MODULE}", name, "java")));
        }

        public Builder setModules(List<String> modules) {
          this.modules = modules;
          return this;
        }

        public Builder setModuleSourcePaths(List<Path> moduleSourcePaths) {
          this.moduleSourcePaths = moduleSourcePaths;
          return this;
        }
      }
    }
  }
}
