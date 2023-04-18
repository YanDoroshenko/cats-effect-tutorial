name := "cats-effect-tutorial"

version := "3.4.9"

scalaVersion := "3.2.2"

libraryDependencies += "org.typelevel" %% "cats-effect" % "3.4.9" withSources() withJavadoc()

scalacOptions ++= Seq(
  "-feature",
  "-deprecation",
  "-unchecked",
  "-language:postfixOps"
)
