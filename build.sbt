import Dependencies._

enablePlugins(JavaAppPackaging)


lazy val commonSettings = Seq(
  organization := "com.github.jamsa.jtv",
  version := "0.1.0",
  scalaVersion := "2.12.6",
  libraryDependencies += scalaTest % Test,
  libraryDependencies += netty,
  libraryDependencies += logging,
  libraryDependencies += logback
  /*,
    libraryDependencies += scalaCompiler,
    libraryDependencies += poi,
    libraryDependencies += poiOoxml,
    libraryDependencies += poiOoxmlSchemas,
    excludeDependencies += staxApi*/
)

lazy val common = (project in file("common"))
  .settings(
    commonSettings,
    name := "jtv-common"
  )

lazy val client = (project in file("client")).dependsOn(common)
  .settings(
    commonSettings,
    name := "jtv-client"
  )

lazy val server = (project in file("server")).dependsOn(common)
  .settings(
    commonSettings,
    name := "jtv-server"
  )


lazy val root = (project in file(".")).aggregate(common, client, server)
  .settings(
    name := "jtv"
  )


