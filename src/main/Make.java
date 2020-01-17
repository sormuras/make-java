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

import java.lang.module.ModuleDescriptor.Version;

/**
 * Modular Java Build Tool.
 */
class Make {

  /**
   * Main entry-point.
   */
  public static void main(String... args) {
    var make = new Make();
    var project = new Project("project", Version.parse("1-ea"));
    System.out.println(project);
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
