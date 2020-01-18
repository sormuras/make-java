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
import java.util.Arrays;
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
        new Tool.Plan(
            "/",
            false,
            new Tool.Call("CreateDirectories", ".make-java"),
            new Tool.Plan(
                "Print version of each provided tool",
                true,
                new Tool.Call("javac", "--version"),
                new Tool.Call("jar", "--version"),
                new Tool.Call("javadoc", "--version")),
            new Tool.Call("WriteLog", ".make-java/log.txt"));
    run(plan);
  }

  public void run(Tool.Call call) {
    run(call, "");
  }

  private void run(Tool.Call call, String indent) {
    var name = call.name;
    var args = call.args;
    var arguments = args.length == 0 ? "" : " " + String.join(" ", args);
    log(Level.DEBUG, "%srun(%s%s)", indent, name, arguments);

    if (call instanceof Tool.Plan) {
      var plan = ((Tool.Plan) call);
      var stream = plan.parallel ? Arrays.stream(plan.calls).parallel() : Arrays.stream(plan.calls);
      stream.forEach(child -> run(child, indent + " "));
      log(Level.DEBUG, "%send(%s)", indent, name);
      return;
    }

    if (Boolean.getBoolean("dry-run")) return;

    try {
      switch (name) {
        case "CreateDirectories":
          Files.createDirectories(Path.of(args[0]));
          return;
        case "WriteLog":
          Files.write(Path.of(args[0]), log);
          return;
      }
    } catch (Exception e) {
      var message = log(Level.ERROR, "Tool %s run failed: %d -> " + call.name, e.getMessage());
      throw new Error(message, e);
    }

    var tool = ToolProvider.findFirst(name).orElseThrow();
    var out = new StringWriter();
    var err = new StringWriter();

    var code = tool.run(new PrintWriter(out, true), new PrintWriter(err, true), args);
    out.toString().lines().forEach(line -> log(Level.TRACE, "%s  %s", indent, line));
    err.toString().lines().forEach(line -> log(Level.WARNING, "%s  %s", indent, line));
    if (code != 0) {
      var message = log(Level.ERROR, "Tool %s run failed: %d", call.name, code);
      throw new Error(message);
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

    class Call {

      final String name;
      final String[] args;

      Call(String name, String... args) {
        this.name = name;
        this.args = args;
      }
    }

    class Plan extends Call {

      final boolean parallel;
      final Call[] calls;

      Plan(String name, boolean parallel, Call... calls) {
        super(name);
        this.parallel = parallel;
        this.calls = calls;
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
