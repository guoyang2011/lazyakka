name := "RemoteAkka"

version := "1.0"

scalaVersion := "2.10.4"

libraryDependencies += "com.typesafe.akka" %% "akka-actor" % "2.3.14" withJavadoc() withSources()

libraryDependencies += "com.typesafe.akka" %% "akka-remote" % "2.3.14" withJavadoc() withSources()

libraryDependencies += "com.typesafe.akka" %% "akka-cluster" % "2.3.14" withJavadoc() withSources()

    