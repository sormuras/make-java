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

import java.lang.System.Logger.Level;
import java.lang.module.ModuleDescriptor.Version;
import java.time.Duration;
import java.time.Instant;

/** Modular Java Build Tool. */
class Make implements Runnable {

  /** Version string. */
  public static final String VERSION = "1-ea";

  final Logger logger;
  final Project project;

  Make(Logger logger, Project project) {
    this.logger = logger;
    this.project = project;
    logger.log(Level.INFO, "%s", this);
  }

  @Override
  public void run() {
    logger.log(Level.INFO, "Make %s %s", project.name(), project.version());
  }

  @Override
  public String toString() {
    return "Make.java " + VERSION;
  }

  interface Logger {
    Logger log(Level level, String format, Object... args);
    static Logger ofSystem() {
      return new Logger() {
        final boolean verbose = Boolean.getBoolean("verbose");
        final Instant start = Instant.now();
        @Override
        public Logger log(Level level, String format, Object... args) {
          if (level.compareTo(Level.INFO) < 0 && !verbose) return this;
          var millis = Duration.between(start, Instant.now()).toMillis();
          var message = String.format(format, args);
          var stream = level.compareTo(Level.WARNING) < 0 ? System.out : System.err;
          stream.printf("%7d|%7s| %s%n", millis, level, message);
          return this;
        }
      };
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
