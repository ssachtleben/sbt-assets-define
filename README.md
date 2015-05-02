# sbt-assets-define

This plugin wraps specific compiled coffeescript files and precompiled handlebar templates in a define block.

Add the dependency in your project's `plugins.sbt` file:

    resolvers += Resolver.url("ssachtleben sbt repository (snapshots)", url("http://ssachtleben.github.io/sbt-plugins/repository/snapshots/"))(Resolver.ivyStylePatterns)

    addSbtPlugin("com.ssachtleben.sbt" % "sbt-assets-define" % "1.0.0")

See this plugin in action [here](https://github.com/ssachtleben/sbt-assets-example).

&copy; Sebastian Sachtleben, 2015

Check: https://github.com/sbt/sbt-web/blob/master/src/main/scala/com/typesafe/sbt/web/SbtWeb.scala