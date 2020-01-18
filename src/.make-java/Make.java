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
import java.util.spi.ToolProvider;

/** Modular Java Build Tool. */
class Make implements Runnable {

  /** Version string. */
  public static final String VERSION = "1-ea";

  private final Logger logger;
  private final Project project;
  private final List<String> log;

  Make(Logger logger, Project project) {
    this.logger = logger;
    this.project = project;
    this.log = new ArrayList<>();
    log(Level.INFO, "%s", this);
    log(Level.DEBUG, "Java %s", Runtime.version());
  }

  private synchronized String log(Level level, String format, Object... args) {
    var message = String.format(format, args);
    log.add(Instant.now() + "|" + level + "|" + Thread.currentThread().getName() + "|" + message);
    logger.log(level, message);
    return message;
  }

  @Override
  public void run() {
    log(Level.INFO, "Make %s %s", project.name(), project.version());
    var plan =
        Tool.Plan.of(
            "/",
            false,
            Tool.Default.CREATE_DIRECTORIES.call(".make-java"),
            Tool.Plan.of(
                "Print version of each provided tool",
                true,
                Tool.Call.of("javac", "--version"),
                Tool.Call.of("jar", "--version"),
                Tool.Call.of("javadoc", "--version")),
            Tool.Default.WRITE_SUMMARY.call(".make-java/summary.log"));
    run(plan);
  }

  public void run(Tool.Call call) {
    run(call, "");
  }

  private void run(Tool.Call call, String indent) {
    var name = call.name();
    var args = call.args();
    var join = args.isEmpty() ? "" : " " + String.join(" ", args);
    log(Level.DEBUG, indent + "run(%s%s)", name, join);

    if (call instanceof Tool.Plan) {
      var plan = ((Tool.Plan) call);
      var stream = plan.parallel() ? plan.calls().stream().parallel() : plan.calls().stream();
      stream.forEach(child -> run(child, indent + " "));
      log(Level.DEBUG, indent + "end(%s)", name);
      return;
    }

    if (Boolean.getBoolean("dry-run")) return;

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
        throw new Error(message);
      }
      return;
    }

    try {
      Tool.Default.valueOf(name).run(this, args);
    } catch (Exception e) {
      var message = log(Level.ERROR, "%s run failed: %s -> ", name, e.getMessage());
      throw new Error(message, e);
    }
  }

  @Override
  public String toString() {
    return "Make.java " + VERSION;
  }

  /** Simple Logger API. */
  interface Logger {
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

  /** Tool API with tool call plan support. */
  interface Tool {

    /** Run this tool. */
    void run(Make make, List<String> arguments) throws Exception;

    /** Built-in tool implementations. */
    enum Default implements Tool {
      /** @see Files#createDirectories(Path, java.nio.file.attribute.FileAttribute[]) */
      CREATE_DIRECTORIES {
        @Override
        public void run(Make make, List<String> arguments) throws Exception {
          Files.createDirectories(Path.of(arguments.get(0)));
        }
      },
      /** Writes all log messages to file specified by the first argument. */
      WRITE_SUMMARY {
        @Override
        public void run(Make make, List<String> arguments) throws Exception {
          Files.write(Path.of(arguments.get(0)), make.log);
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
  }

  /*record*/ static class Project {

    final String name;
    final Version version;

    Project(String name, String version) {
      this.name = name;
      this.version = Version.parse(version);
    }

    public String name() {
      return name;
    }

    public Version version() {
      return version;
    }
  }
}
