sbtPlugin := true

organization := "com.ssachtleben"

name := """sbt-assets-define"""

version := "1.0.1-SNAPSHOT"

scalaVersion := "2.10.4"

scalacOptions += "-feature"

autoScalaLibrary := false

crossPaths := false

publishArtifact in(Compile, packageDoc) := false

resolvers ++= Seq(
  "Typesafe Releases Repository" at "http://repo.typesafe.com/typesafe/releases/",
  Resolver.url("sbt snapshot plugins", url("http://repo.scala-sbt.org/scalasbt/sbt-plugin-snapshots"))(Resolver.ivyStylePatterns),
  Resolver.sonatypeRepo("snapshots"),
  "Typesafe Snapshots Repository" at "http://repo.typesafe.com/typesafe/snapshots/",
  Resolver.mavenLocal
)

libraryDependencies ++= Seq(
  "commons-io" % "commons-io" % "2.4",
  "org.apache.commons" % "commons-lang3" % "3.3.2",
  "org.webjars" % "mkdirp" % "0.3.5"
)

addSbtPlugin("com.typesafe.sbt" % "sbt-js-engine" % "1.0.0")

addSbtPlugin("com.typesafe.sbt" % "sbt-coffeescript" % "1.0.0")

addSbtPlugin("com.ssachtleben" % "sbt-assets-handlebars" % "1.0.0")

scriptedSettings

scriptedLaunchOpts <+= version apply { v => s"-Dproject.version=$v" }