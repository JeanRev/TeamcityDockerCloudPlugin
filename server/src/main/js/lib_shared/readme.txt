Javascript modules contained in this folder are meant to be shared between the web front-end bundle, and the server-side
 jar for evaluation using the JDK bundled Javascript engine, Nashorn.

We are currently tied to the version 1.8 of the JDK, and the corresponding Nashorn version is limited to the ECMA 5.1
standard. Transpiling Javascript for inclusion in the server-side JAR is currently not an option (this would require to
load polyfills without guarantee of their behaviour in a non-browser environment).

The following set of restrictions apply therefore to the Javascript files stored in this folder:
- Their syntax and used data-structures must strictly stick to the ECMA 5.1 standard.
- They should not try to access any globals related to the browser environment (eg. JQuery).
- They should not try to invoke the Webpack runtime API (typically, to load other modules).