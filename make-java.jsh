/*
 * Create default build program unless it already exists.
 */
var build = Path.of("src/.make-java/Build.java")
if (!Files.exists(build)) {
  Files.createDirectories(build.getParent());
  Files.write(build, List.of(
    "class Build {",
    "  public static void main(String... args) {",
    "    Make.main(args);",
    "  }",
    "}"
  ));
}

/*
 * Source remote build tool and local build program into this JShell session.
 */
/open https://github.com/sormuras/make-java/raw/master/src/main/Make.java
/open src/.make-java/Build.java

/*
 * Launch build program and report exit code.
 */
int code = 0
try {
  Build.main();
} catch(Throwable throwable) {
  throwable.printStackTrace(System.err);
  code = 1;
}

/exit code
