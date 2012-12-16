freemarker-osgi-loader
======================

Flexible OSGI freemarker template loader.

More information at: http://dani.calidos.com/2010/08/11/templating-the-osgi-way-with-freemarker/

For now we're releasing directly to github using the maven release plugin tough it seems that the upload of artifacts is being ignored for some reason.

Tips and tricks
===============

Use ```
mvn release:prepare release:perform -Darguments='-DaltDeploymentRepository=REPO::default::file:/LOCAL-PATH/danigiri-maven-repo/releases'
```
