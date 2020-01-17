/open https://github.com/sormuras/make-java/raw/master/src/main/Make.java
//open src/main/Make.java
/open src/.make-java/Build.java

int code = 0

try {
  Build.main();
} catch(Throwable throwable) {
  throwable.printStackTrace(System.err);
  code = 1;
}

/exit code
