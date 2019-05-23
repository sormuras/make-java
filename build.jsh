/open https://github.com/sormuras/bach/raw/master/BUILDING

exe("java", "--version")
del("target")

/open src/main/Make.java

/*
 * Build "main" and "test" realm using Make.java's ClassicalBuilder.
 */
var run = new Make.Run(System.Logger.Level.ALL, new PrintWriter(System.out), new PrintWriter(System.err))
var make = Make.of(Make.USER_PATH)
var builder = make.new ClassicalBuilder(run)
make.assemble(run)
builder.compile(make.main)
builder.jarClasses(make.main)
builder.compile(make.test)
// DO NOT CALL builder.junit()

/*
 * Run tests "externally" to prevent cyclical runtime loops...
 */
var code = 0
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
