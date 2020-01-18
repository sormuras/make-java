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

/*
 * Declare constants.
 */
String VERSION = "master"
var source = new URL("https://github.com/sormuras/make-java/raw/" + VERSION + "/src/.make-java/")
var target = Path.of("src/.make-java")
var make = target.resolve("Make.java")
var build = target.resolve("Build.java")

/*
 * Source print method into this JShell session.
 */
/open PRINTING

/*
 * Banner!
 */
println()
println("Bootstrap make-java in directory: " + Path.of("").toAbsolutePath())

/*
 * Download build tool and other assets from GitHub to local directory.
 */
println()
println("Download assets to " + target.toAbsolutePath() + "...")
Files.createDirectories(target)
for (var asset : Set.of(make, build)) {
  if (Files.exists(asset)) {
    println("  skip download -- using existing file: " + asset);
  } else {
    var remote = new URL(source, asset.getFileName().toString());
    println("Load " + remote + "...");
    try (var stream = remote.openStream()) {
      Files.copy(stream, asset, StandardCopyOption.REPLACE_EXISTING);
    }
    println("  -> " + asset);
  }
}

/*
 * Generate local launchers.
 */
var javac = "javac -d .make-java/classes " + make + " " + build
var java = "java  -cp .make-java/classes Build"
println()
println("Generating local launchers and initial configuration...")
println("  -> make-java")
Files.write(Path.of("make-java"), List.of("/usr/bin/env " + javac, "/usr/bin/env " + java + " \"$@\"")).toFile().setExecutable(true)
println("  -> make-java.bat")
Files.write(Path.of("make-java.bat"), List.of("@ECHO OFF", javac, java + " %*"))

/*
 * Print some help and wave goodbye.
 */
println()
println("Bootstrap finished. Use the following command to launch your build program:")
println()
println("    Linux: ./make-java <args...>")
println("  Windows: make-java <args...>")

/*
 * Source build tool and build program into this JShell session.
 */
/open src/.make-java/Make.java
/open src/.make-java/Build.java

/*
 * Launch build program and report exit code.
 */
println()
println("Launch build program: " + build)
println()
int code = 0
try {
  Build.main();
} catch(Throwable throwable) {
  throwable.printStackTrace(System.err);
  code = 1;
}

println()
println("Have fun! https://github.com/sponsors/sormuras (-:")
println()

/exit code
