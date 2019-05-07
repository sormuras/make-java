System.setProperty("java.util.logging.SimpleFormatter.format", "%4$-7s | %5$s%6$s%n")
/open https://raw.githubusercontent.com/sormuras/make-java/master/src/main/Make.java
var make = new Make()
var code = make.run(System.out, System.err)
/exit code
