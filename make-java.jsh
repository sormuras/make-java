/open https://github.com/sormuras/make-java/raw/master/src/main/Make.java
var code = 0
try {
  Make.main();
} catch(Throwable throwable) {
  System.err.println(throwable.getMessage());
  code = 1;
}
/exit code
