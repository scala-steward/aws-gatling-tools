import com.typesafe.sbt.SbtNativePackager.autoImport._
import com.typesafe.sbt.packager.archetypes.scripts.BashStartScriptPlugin.autoImport._
import com.typesafe.sbt.packager.docker.Cmd
import com.typesafe.sbt.packager.docker.DockerPlugin.autoImport._
import sbt.Keys._
import sbt._
import Settings._

val `api-server` = (project in file("api-server"))
  .enablePlugins(AshScriptPlugin, JavaAgent, EcrPlugin)
  .settings(baseSettings)
  .settings(dockerCommonSettings)
  .settings(ecrSettings)
  .settings(
    name := "api-server",
    mainClass in (Compile, run) := Some("example.api.server.ApiServer"),
    mainClass in reStart := Some("example.api.server.ApiServer"),
    dockerEntrypoint := Seq("/opt/docker/bin/api-server"),
    dockerUsername := Some("j5ik2o"),
    dockerExposedPorts ++= Seq(8080),
    fork in run := true,
    javaAgents += "org.aspectj" % "aspectjweaver" % "1.8.13",
    javaAgents += "org.mortbay.jetty.alpn" % "jetty-alpn-agent" % "2.0.9" % "runtime;test",
    javaOptions in Universal += "-Dorg.aspectj.tracing.factory=default",
    javaOptions in run ++= Seq(
      s"-Dcom.sun.management.jmxremote.port=${sys.env.getOrElse("JMX_PORT", "8999")}",
      "-Dcom.sun.management.jmxremote.authenticate=false",
      "-Dcom.sun.management.jmxremote.ssl=false",
      "-Dcom.sun.management.jmxremote.local.only=false",
      "-Dcom.sun.management.jmxremote"
    ),
    javaOptions in Universal ++= Seq(
      "-Dcom.sun.management.jmxremote",
      "-Dcom.sun.management.jmxremote.local.only=true",
      "-Dcom.sun.management.jmxremote.authenticate=false"
    ),
    libraryDependencies ++= Seq(
      "com.typesafe.akka" %% "akka-http" % "10.1.11",
      "com.typesafe.akka" %% "akka-stream" % akka26Version,
      "com.typesafe.akka" %% "akka-slf4j" % akka26Version,
      "com.github.scopt" %% "scopt" % "4.0.0-RC2",
      "net.logstash.logback" % "logstash-logback-encoder" % "4.11" excludeAll (
        ExclusionRule(
          organization = "com.fasterxml.jackson.core",
          name = "jackson-core"
        ),
        ExclusionRule(
          organization = "com.fasterxml.jackson.core",
          name = "jackson-databind"
        )
      ),
      "org.slf4j" % "jul-to-slf4j" % "1.7.26",
      "ch.qos.logback" % "logback-classic" % "1.2.3"
    )
  )

lazy val `gatling-test` = (project in file("gatling-test"))
  .enablePlugins(GatlingPlugin)
  .settings(gatlingCommonSettings)
  .settings(
    name := "api-server-gatling-test",
    libraryDependencies ++= Seq(
      "io.gatling.highcharts" % "gatling-charts-highcharts" % gatlingVersion,
      "io.gatling" % "gatling-test-framework" % gatlingVersion,
      "com.amazonaws" % "aws-java-sdk-core" % awsSdkVersion,
      "com.amazonaws" % "aws-java-sdk-s3" % awsSdkVersion,
      "io.circe" %% "circe-core" % circeVersion,
      "io.circe" %% "circe-generic" % circeVersion,
      "io.circe" %% "circe-parser" % circeVersion
    ),
    publishArtifact in (GatlingIt, packageBin) := true
  )
  .settings(
    addArtifact(artifact in (GatlingIt, packageBin), packageBin in GatlingIt)
  )

lazy val `gatling-runner` = (project in file("gatling-runner"))
  .enablePlugins(JavaAppPackaging, EcrPlugin)
  .settings(gatlingCommonSettings)
  .settings(gatlingRunnerEcrSettings)
  .settings(
    name := "api-server-gatling-runner",
    libraryDependencies ++= Seq(
      "io.gatling" % "gatling-app" % gatlingVersion,
      "com.amazonaws" % "aws-java-sdk-core" % awsSdkVersion,
      "com.amazonaws" % "aws-java-sdk-s3" % awsSdkVersion
    ),
    mainClass in (Compile, bashScriptDefines) := Some(
      "com.github.j5ik2o.gatling.runner.Runner"
    ),
    dockerBaseImage := "openjdk:8",
    dockerUsername := Some("j5ik2o"),
    packageName in Docker := "api-server-gatling-runner",
    dockerUpdateLatest := true,
    dockerCommands ++= Seq(
      Cmd("USER", "root"),
      Cmd("RUN", "mkdir /var/log/gatling"),
      Cmd("RUN", "chown daemon:daemon /var/log/gatling"),
      Cmd("ENV", "GATLING_RESULT_DIR=/var/log/gatling")
    )
  )
  .dependsOn(`gatling-test` % "compile->gatling-it")

lazy val `gatling-s3-reporter` = (project in file("gatling-s3-reporter"))
  .settings(name := "api-server-gatling-s3-reporter")

lazy val `gatling-aggregate-runner` =
  (project in file("gatling-aggregate-runner"))
    .enablePlugins(JavaAppPackaging, EcrPlugin)
    .settings(gatlingCommonSettings)
    .settings(gatlingAggregateRunnerEcrSettings)
    .settings(gatlingAggregateRunTaskSettings)
    .settings(
      name := "api-server-gatling-aggregate-runner",
      mainClass in (Compile, bashScriptDefines) := Some(
        "com.github.j5ik2o.gatling.runner.Runner"
      ),
      dockerBaseImage := "openjdk:8",
      dockerUsername := Some("j5ik2o"),
      packageName in Docker := "api-server-gatling-aggregate-runner",
      dockerUpdateLatest := true,
      libraryDependencies ++= Seq(
        "org.slf4j" % "slf4j-api" % "1.7.26",
        "ch.qos.logback" % "logback-classic" % "1.2.3",
        "org.codehaus.janino" % "janino" % "3.0.6",
        "com.iheart" %% "ficus" % "1.4.6",
        "com.github.j5ik2o" %% "reactive-aws-ecs-core" % "1.1.3",
        "org.scalaj" %% "scalaj-http" % "2.4.2"
      )
    )

val `aws-gatling-tools` =
  (project in file("."))
    .settings(baseSettings)
    .settings(name := "aws-gatling-tools")
    .aggregate(
      `api-server`,
      `gatling-test`,
      `gatling-runner`,
      `gatling-s3-reporter`,
      `gatling-aggregate-runner`
    )