/open https://github.com/junit-team/junit5-samples/raw/master/junit5-modular-world/BUILDING

exe("java", "--version")
del("work")

/*
 * Build "main" realm.
 */
var code = run("javac", "-d", "work/main", "src/main/Make.java")

/*
 * Build "test" realm.
 */

if (code == 0) {
  var junit = get("work/lib", "org.junit.platform", "junit-platform-console-standalone", "1.4.2");
  var args = new Arguments()
      .add("-d").add("work/test")
      .add("--class-path").addPath("work/main", "work/lib", junit.toString())
      .addAllFiles("src/test", ".java");
  code = run("javac", args.toArray());
}

if (code == 0) {
  var args = new Arguments()
      .add("--class-path").addPath("work/test", "work/main", "work/lib/*")
      .add("org.junit.platform.console.ConsoleLauncher")
      .add("--reports-dir").add("work/test-reports")
      .add("--scan-class-path");
  code = exe("java", args.toArray());
}

/exit code
