/open src/main/Make.java
var make = new Make()

make.var.out = System.out::println
make.var.err = System.err::println

var compileMain = new Make.Action.Tool("javac", "-d", "build/main", "src/main/Make.java")
var downloadJUnit = new Make.Action.Download(Path.of("build"), URI.create(make.var.get(Make.Property.TOOL_JUNIT_URI)))
var junit = Path.of("build/junit-platform-console-standalone-1.4.0.jar")
var compileTest = new Make.Action.Tool(new Make.Command("javac").add("-d").add("build/test").add("-cp").add(List.of(Path.of("build/main"), junit)).addAllJavaFiles(List.of(Path.of("src/test"))))
var copyTestResources = new Make.Action.TreeCopy(Path.of("src/test-resources"), Path.of("build/test"))
var runTest = new Make.Action.Tool(new Make.Command("java").add("-ea").add("-cp").add(List.of(Path.of("build/test"), Path.of("build/main"), junit)).add("org.junit.platform.console.ConsoleLauncher").add("--scan-class-path"))

/exit make.run(compileMain, downloadJUnit, compileTest, copyTestResources, runTest)
