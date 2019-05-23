/open https://github.com/sormuras/bach/raw/master/BUILDING

exe("java", "--version")
del("target")

/open src/main/Make.java

/*
 * Build "main" and "test" realm using Make.java's ClassicalBuilder.
 */
var make = Make.of(Make.USER_PATH)
var code = make.run(System.out, System.err)

/*
 * Run tests "externally" to prevent cyclical runtime loops...
 */
if (code == 0) {
  var sep = File.pathSeparator;
  var args = new Make.Args()
      .add("--class-path", "target/test/classes" + sep + "target/main/classes" + sep + "lib/test-runtime-platform/*")
      .add("org.junit.platform.console.ConsoleLauncher")
      .add("--reports-dir", "target/test-reports")
      .add("--scan-class-path");
  code = exe("java", args.toStringArray());
}

/exit code
