import sbt._

object Dependencies {
  lazy val scalaTest = "org.scalatest" %% "scalatest" % "3.0.3"
  lazy val netty = "io.netty" % "netty-all" % "4.1.25.Final"
  lazy val logging = "com.typesafe.scala-logging" %% "scala-logging" % "3.9.0"
  lazy val logback =  "ch.qos.logback" % "logback-classic" % "1.2.3"
  // lazy val poi = "org.apache.poi" % "poi" % "3.16"
  // lazy val poiOoxml = "org.apache.poi" % "poi-ooxml" % "3.16"
  // lazy val poiOoxmlSchemas = "org.apache.poi" % "poi-ooxml-schemas" % "3.16"
  // lazy val staxApi = "stax" % "stax-api"
  // lazy val scalaCompiler = "org.scala-lang" % "scala-compiler" % "2.10.6"
}
