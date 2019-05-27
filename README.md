
[![Build Status](https://travis-ci.com/sormuras/make-java.svg?branch=master)](https://travis-ci.com/sormuras/make-java)

# make.java
Build a Java project via

```bash
jshell https://bit.ly/make-java
```

## featuring

- zero installation required (besides JDK 11+)
- considers compile (`javac`) and package (`jar`) as an atomic step
- single-pass multi-module processing (`--module-source-path`)
- multi-release modules (`java-7`, `java-8`, `java-9`, ..., `java-N`)
- 3rd-party library resolution (`lib/../module-uri.properties`) in plain sight
- check (`junit-platform`)
- document (`javadoc`)

## directory layout

```
├───lib
│   ├───main
│   ├───main-compile-only
│   ├───main-runtime-only
│   ├───test
│   ├───test-compile-only
│   ├───test-runtime-only
│   └───test-runtime-platform
└───src
    ├───main
    │   ├───com.greetings       // an application module
    │   │   └───...
    │   └───org.astro           // some astro-related module
    │       └───...
    └───test
        ├───integration         // "requires" the application and the astro
        │   └───...             //  module testing their published API
        └───org.astro
            └───org             // package-private tests of the astro module
                └───astro
```

### links

- `https://bit.ly/make-java` expands to [`make-java.jsh`](make-java.jsh)
