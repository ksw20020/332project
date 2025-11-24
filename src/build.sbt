
ThisBuild / version := "0.1.0-SNAPSHOT"
ThisBuild / scalaVersion := "3.3.7"

// [중요] gRPC/Netty 사용 시 발생하는 파일 충돌 해결 전략
lazy val commonAssemblySettings = Seq(
  assembly / assemblyMergeStrategy := {
    case PathList("META-INF", "versions", xs @ _*) => MergeStrategy.discard
    case x if x.endsWith("module-info.class") => MergeStrategy.discard
    case x if x.endsWith("io.netty.versions.properties") => MergeStrategy.discard
    case "module-info.class" => MergeStrategy.discard
    case "META-INF/io.netty.versions.properties" => MergeStrategy.discard
    case PathList("META-INF", xs @ _*) =>
      xs map {_.toLowerCase} match {
        case "manifest.mf" :: Nil | "index.list" :: Nil | "dependencies" :: Nil =>
          MergeStrategy.discard
        case ps @ (x :: xs) if ps.last.endsWith(".sf") || ps.last.endsWith(".dsa") =>
          MergeStrategy.discard
        case "services" :: xs =>
          MergeStrategy.filterDistinctLines
        case _ => MergeStrategy.discard
      }
    case _ => MergeStrategy.first
  }
)

lazy val master = (project in file("master"))
  .settings(
    name := "master",
    // [설정] Master의 메인 클래스 지정 (패키지명.클래스명 정확히 입력)
    // 예: mainClass in assembly := Some("MasterApp"),
    assembly / mainClass := Some("master.MasterApp"),
    commonAssemblySettings, // 위에서 정의한 합치기 전략 적용

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
    // [설정] Worker의 메인 클래스 지정
    assembly / mainClass := Some("worker.WorkerApp"),
    commonAssemblySettings,

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
