/open https://github.com/sormuras/bach/raw/master/BUILDING

exe("java", "--version")
del("target")

/open src/main/Make.java

/*
 * Build "main" and "test" realm using Make.java's ClassicalBuilder.
 */
System.setProperty("Debug".substring(1), "true") // emit debug level messages
System.setProperty("Dry-run".substring(1), "false") // force normal run mode to assemble and build
System.setProperty("Do-launch-junit-platform".substring(1), "false") // don't launch test run, see below
var make = Make.of(Make.USER_PATH)
var code = make.run(System.out, System.err)

/*
 * Run tests in a dedicated process to prevent cyclical runtime loops...
 */
if (code == 0) {
  var sep = File.pathSeparator;
  var args = new Make.Args()
      .add("--class-path", "target/test/classes" + sep + "target/main/classes" + sep + "lib/test-runtime-platform/*")
      .add("org.junit.platform.console.ConsoleLauncher")
      .add("--reports-dir", "target/test-reports")
      .add("--fail-if-no-tests")
      .add("--scan-class-path");
  code = exe("java", args.toStringArray());
}

/exit code
