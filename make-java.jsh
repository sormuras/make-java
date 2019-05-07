System.setProperty("java.util.logging.SimpleFormatter.format", "%1$tY-%1$tm-%1$td %1$tH:%1$tM:%1$tS %4$-7s | %5$s%6$s%n")
/open https://raw.githubusercontent.com/sormuras/make-java/master/src/main/Make.java
var make = new Make()
var code = make.run()
/exit code
