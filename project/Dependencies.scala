import sbt._

object Dependencies {
  lazy val rootDependencies = "io.github.resilience4j" % "resilience4j-circuitbreaker" % "1.6.0"
  lazy val scalaTest = "org.scalatest" %% "scalatest" % "3.2.2"
}
