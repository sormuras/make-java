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

import static java.lang.System.Logger.Level.DEBUG;
import static java.lang.System.Logger.Level.INFO;

import java.lang.module.ModuleDescriptor.Version;

/**
 * Modular Java Build Tool.
 */
class Make {

  /** Version string. */
  public static final String VERSION = "1-ea";

  private static final System.Logger LOGGER = System.getLogger("Make.java");

  /**
   * Main entry-point.
   */
  public static void main(String... args) {
    LOGGER.log(DEBUG, "");
    var project = new Project("project", Version.parse("1-ea"));
    new Make().make(project);
  }

  public void make(Project project) {
    LOGGER.log(DEBUG, this);
    LOGGER.log(INFO, project);
  }

  @Override
  public String toString() {
    return "Make.java " + VERSION;
  }

  /*record*/ static class Project {

    final String name;
    final Version version;

    Project(String name, Version version) {
      this.name = name;
      this.version = version;
    }

    public String name() {
      return name;
    }

    public Version version() {
      return version;
    }

    @Override
    public String toString() {
      return "Project{" +
             "name='" + name + '\'' +
             ", version=" + version +
             '}';
    }
  }

}
