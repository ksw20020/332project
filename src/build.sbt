
ThisBuild / version := "0.1.0-SNAPSHOT"
ThisBuild / scalaVersion := "3.3.7"

lazy val master = (project in file("master"))
  .settings(
    name := "master",
    libraryDependencies ++= Seq(
      "io.grpc" % "grpc-netty" % scalapb.compiler.Version.grpcJavaVersion,
      "com.thesamet.scalapb" %% "scalapb-runtime-grpc" % scalapb.compiler.Version.scalapbVersion
    ),
    Compile / PB.targets := Seq(
      scalapb.gen() -> (Compile / sourceManaged).value
    ),
    Compile / PB.protoSources += (ThisBuild / baseDirectory).value / "master" / "src" / "main" / "proto"
  )

lazy val worker = (project in file("worker"))
  .settings(
    name := "worker",
    libraryDependencies ++= Seq(
      "io.grpc" % "grpc-netty" % scalapb.compiler.Version.grpcJavaVersion,
      "com.thesamet.scalapb" %% "scalapb-runtime-grpc" % scalapb.compiler.Version.scalapbVersion
    ),
    Compile / PB.targets := Seq(
      scalapb.gen() -> (Compile / sourceManaged).value
    ),
    Compile / PB.protoSources += (ThisBuild / baseDirectory).value / "worker" / "src" / "main" / "proto"
  )

lazy val root = (project in file("."))
  .aggregate(master, worker)

