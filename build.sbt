import scala.sys.process._ 

// Global / scalaJSStage := FullOptStage

val includeAddon: Boolean  = true // always include addon for now, can be disabled later if needed
val appOrganization        = sys.env.getOrElse("APP_ORGANIZATION","org.jorolicht")
val appVersion             = sys.env.getOrElse("APP_VERSION", "1.0.0")
val appDate                = sys.env.getOrElse("APP_DATE", "2026-08-15")
val appMaintainer          = sys.env.getOrElse("APP_MAINTAINER", "Joe Doe <joe.doe@example.com>")

ThisBuild / scalaVersion := "3.3.7"
ThisBuild / organization := appOrganization
ThisBuild / version      := appVersion
server / maintainer      := appMaintainer

def getAppEnv: String = {
  sys.props.get("app.env").orElse(sys.env.get("APP_ENV")).getOrElse("prod")
}

def getDockerDir(base: File, env: String): File = {
  if (env == "prod" || env == "prod_rolicht") base / "docker" / "prod"
  else base / "docker" / "dev"
}

def getDockerWpPluginDir(base: File, env: String): File = {
  getDockerDir(base, env) / "wp_data" / "wp-content" / "plugins" / "tourney"
}

val dockerHubUser = "jorolich"
val dockerPlatform = "linux/amd64"

val dockerBuildPlaysrv = taskKey[Unit]("Builds playsrv-image Docker image for linux/amd64 and tags for Docker Hub user jorolich")
val dockerPushPlaysrv  = taskKey[Unit]("Pushes playsrv-image Docker image to Docker Hub for user jorolich")

val dockerBuildWpCli   = taskKey[Unit]("Builds wp-cli-instance Docker image for linux/amd64 and tags for Docker Hub user jorolich")
val dockerPushWpCli    = taskKey[Unit]("Pushes wp-cli-instance Docker image to Docker Hub for user jorolich")

val dockerBuildWpGmp   = taskKey[Unit]("Builds wp-gmp-image Docker image for linux/amd64 and tags for Docker Hub user jorolich")
val dockerPushWpGmp    = taskKey[Unit]("Pushes wp-gmp-image Docker image to Docker Hub for user jorolich")

val dockerBuildImages  = taskKey[Unit]("Builds playsrv-image, wp-cli-instance, and wp-gmp-image Docker images for linux/amd64")
val dockerPushImages   = taskKey[Unit]("Pushes playsrv-image, wp-cli-instance, and wp-gmp-image Docker images to Docker Hub for user jorolich")

lazy val root = (project in file("."))
  .aggregate(server, client, shared.jvm, shared.js)

val genMsgFiles = taskKey[Unit]("Generate Message Files")  
val convertMessagesToJson = taskKey[Seq[File]]("Converts message files to JSON")

lazy val syncClientWpFiles = taskKey[Unit]("Copies main.js and main.js.map to wordpress")

lazy val server = project
  .settings(
    Universal / stage := {
      val stageDir = (Universal / stage).value   // erzeugt target/universal/stage

      val log = streams.value.log
      val env = getAppEnv
      log.info(s"Staging for environment: $env")

      val dockerDir = getDockerDir(baseDirectory.value, env)
      val targetDir = dockerDir / "playsrv" / "stage"

      // Zielverzeichnis sauber neu anlegen
      IO.delete(targetDir)
      IO.createDirectory(targetDir)

      // komplettes stage-Verzeichnis kopieren
      IO.copyDirectory(stageDir, targetDir, overwrite = true, preserveLastModified = true)

      log.info(s"Copied staged distribution to ${targetDir.getAbsolutePath}")

      stageDir
    },
    commands ++= Seq(hello, buildMsg),
    genMsgFiles := {
      val msgFileDe = baseDirectory.value  / "conf" / "messages.de"
      val msgFileEn = baseDirectory.value  / "conf" / "messages.en"
      val infoDe = baseDirectory.value  / "conf" / "messages" / "de" / "00_info.de"
      val infoEn = baseDirectory.value  / "conf" / "messages" / "en" / "00_info.en"
      val ymd = appDate.split("-")
      val yearMonth = s"${ymd(0)}-${ymd(1)}"
      IO.write(infoDe, s"""
                        |app.version = ${appVersion}DE${yearMonth}
                        |app.date    = ${appDate}
                        |app.lang    = DE
                        |\n""".stripMargin)
      IO.write(infoEn, s"""
                        |app.version = ${appVersion}EN${yearMonth}
                        |app.date    = ${appDate}
                        |app.lang    = EN
                        |\n""".stripMargin)
      val filesDe = (baseDirectory.value / "conf" / "messages" / "de" ** "*.de").get.sortBy(_.getName)
      val filesEn = (baseDirectory.value / "conf" / "messages" / "en" ** "*.en").get.sortBy(_.getName)
      IO.write(msgFileDe, filesDe.map(IO.read(_)).reduceLeft(_ ++ _))
      IO.write(msgFileEn, filesEn.map(IO.read(_)).reduceLeft(_ ++ _))
      println(s"Message files generated")
    },
    convertMessagesToJson := {
      genMsgFiles.value // run genMsgFiles task first
      val log = streams.value.log
      val confDir = baseDirectory.value / "conf"
      val targetDir = (Compile / resourceManaged).value / "messages"
      IO.createDirectory(targetDir)
      
      val msgFiles = Seq(confDir / "messages.de", confDir / "messages.en")
      
      msgFiles.map { msgFile =>
        log.info(s"Converting ${msgFile.getAbsolutePath} to JSON...")
        if (s"msgConverter ${msgFile.getAbsolutePath}".! != 0) {
          sys.error(s"msgConverter failed for $msgFile")
        }
        
        val generatedFile = new File(msgFile.getAbsolutePath + "_json")
        
        log.info(s"Sorting ${generatedFile.getAbsolutePath} alphabetically...")
        val lines = IO.readLines(generatedFile)
        val entries = lines.flatMap { line =>
          val cleanLine = line.trim
          if (cleanLine.startsWith("\"") && cleanLine.contains("\": \"")) {
            val idx = cleanLine.indexOf("\": \"")
            val key = cleanLine.substring(1, idx)
            val valueWithQuotes = cleanLine.substring(idx + 4)
            val value = if (valueWithQuotes.endsWith(",")) {
              valueWithQuotes.substring(0, valueWithQuotes.length - 2)
            } else {
              valueWithQuotes.substring(0, valueWithQuotes.length - 1)
            }
            Some((key, value))
          } else {
            None
          }
        }
        
        val sortedEntries = entries.sortBy(_._1)
        val jsonLines = sortedEntries.map { case (k, v) =>
          s"""  "$k": "$v""""
        }
        val sortedJsonContent = "{\n" + jsonLines.mkString(",\n") + "\n}"
        
        val lang = msgFile.name.split('.').last
        val targetFileSrv  = baseDirectory.value / ".." / "server" / "public" / "data" / ("msgs_" + lang + ".json")
        val targetFileWp   = baseDirectory.value / ".." / "wp-plugin" / "data" / ("msgs_" + lang + ".json")
        val env            = getAppEnv
        val targetFileDk   = getDockerWpPluginDir(baseDirectory.value, env) / "data" / ("msgs_" + lang + ".json")

        IO.createDirectory(targetFileWp.getParentFile)
        IO.createDirectory(targetFileSrv.getParentFile)
        IO.createDirectory(targetFileDk.getParentFile)

        IO.write(targetFileWp, sortedJsonContent)
        IO.write(targetFileSrv, sortedJsonContent)
        IO.write(targetFileDk, sortedJsonContent)
        
        log.info(s"Generated sorted JSON files at:")
        log.info(s"  - ${targetFileWp.getAbsolutePath}")
        log.info(s"  - ${targetFileSrv.getAbsolutePath}")
        log.info(s"  - ${targetFileDk.getAbsolutePath}")
        
        targetFileSrv
      }
    },
    Compile / resourceGenerators += convertMessagesToJson.taskValue,
    watchSources ++= (baseDirectory.value / "conf" / "messages" / "de" ** "*.de").get,
    watchSources ++= (baseDirectory.value / "conf" / "messages" / "en" ** "*.en").get,
    scalaJSProjects := Seq(client),
    Assets / pipelineStages  := Seq(scalaJSPipeline),
    pipelineStages := Seq(digest, gzip),
    Compile / compile := ((Compile / compile) dependsOn (scalaJSPipeline, syncClientWpFiles)).value,
    
    libraryDependencies += guice,
    libraryDependencies += jdbc,
    libraryDependencies += ws,
    libraryDependencies += evolutions,
    libraryDependencies += "com.mysql" % "mysql-connector-j" % "8.3.0",
    libraryDependencies += "org.playframework.anorm" %% "anorm" % "2.7.0",
    libraryDependencies += "com.vmunier" %% "scalajs-scripts" % "1.3.0",
    libraryDependencies += "com.lihaoyi" %% "upickle" % "3.3.1",
    libraryDependencies += "com.google.api-client" % "google-api-client" % "2.4.0",
    libraryDependencies += "org.playframework" %% "play-mailer" % "10.0.0",
    libraryDependencies += "org.playframework" %% "play-mailer-guice" % "10.0.0",
    libraryDependencies += "org.typelevel" %% "cats-core" % "2.12.0",
    libraryDependencies += "org.apache.pekko" %% "pekko-stream-typed" % "1.0.2",
    libraryDependencies += "com.lihaoyi" %% "sourcecode" % "0.4.2",
    syncClientWpFiles := {
      (client / Compile / fastLinkJS).value
      (client / Compile / fullLinkJS).value

      val log = streams.value.log
      val clientTargetDir = (client / Compile / fastLinkJS / scalaJSLinkerOutputDirectory).value
      val clientCssSource = file("server/public/css") 

      val wpJsDestination = file("wp-plugin/js")
      val wpCssDestination = file("wp-plugin/css")
      val env = getAppEnv
      val dockerWpPluginDir = getDockerWpPluginDir(baseDirectory.value, env)

      val wpJsDestination2 = dockerWpPluginDir / "js"
      val wpCssDestination2 = dockerWpPluginDir / "css"

      val wpPluginDir = baseDirectory.value / ".." / "wp-plugin"
      val siteContentDir = baseDirectory.value / ".." / "site" / "content"
      val siteDownloadDir = baseDirectory.value / ".." / "site" / "download"

      if (siteContentDir.exists()) {
        IO.copyDirectory(siteContentDir, wpPluginDir / "pages")
      }
      if (siteDownloadDir.exists()) {
        IO.copyDirectory(siteDownloadDir, wpPluginDir / "download")
      }

      IO.copyDirectory(clientTargetDir, wpJsDestination)
      IO.copyDirectory(clientCssSource, wpCssDestination)
      IO.copyDirectory(clientTargetDir, wpJsDestination2)
      IO.copyDirectory(wpCssDestination, wpCssDestination2)

      IO.copyFile(wpPluginDir / "tourney.php", dockerWpPluginDir / "tourney.php")
      IO.copyDirectory(wpPluginDir / "includes", dockerWpPluginDir / "includes")
      IO.copyDirectory(wpPluginDir / "pages", dockerWpPluginDir / "pages")
      if ((wpPluginDir / "img").exists()) {
        IO.copyDirectory(wpPluginDir / "img", dockerWpPluginDir / "img")
      }
      if ((wpPluginDir / "font").exists()) {
        IO.copyDirectory(wpPluginDir / "font", dockerWpPluginDir / "font")
      }
      if ((wpPluginDir / "download").exists()) {
        IO.copyDirectory(wpPluginDir / "download", dockerWpPluginDir / "download")
      }
      if ((wpPluginDir / "composer.json").exists()) {
        IO.copyFile(wpPluginDir / "composer.json", dockerWpPluginDir / "composer.json")
      }
      if ((wpPluginDir / "composer.lock").exists()) {
        IO.copyFile(wpPluginDir / "composer.lock", dockerWpPluginDir / "composer.lock")
      }
      if ((wpPluginDir / "data").exists()) {
        IO.copyDirectory(wpPluginDir / "data", dockerWpPluginDir / "data")
        IO.copyDirectory(wpPluginDir / "data", baseDirectory.value / ".." / "server" / "public" / "data")
      }

      log.info(s"Copied files to wordpress (including php, includes, and pages)")
    },
    Universal / dist := {
      val zipFile = (Universal / packageBin).value

      val log = streams.value.log
      val env = getAppEnv
      val targetDir = getDockerDir(baseDirectory.value, env) / "playsrv"
      IO.createDirectory(targetDir)

      val targetFile = targetDir / zipFile.getName
      IO.copyFile(zipFile, targetFile)

      log.info(s"Copied ${zipFile.getName} to ${targetDir}")
      zipFile
    },
    dockerBuildPlaysrv := {
      (Universal / stage).value
      val log = streams.value.log
      val env = getAppEnv
      val dockerDir = getDockerDir(baseDirectory.value, env)
      val versionTag = s"$dockerHubUser/playsrv-image:$appVersion"
      val latestTag = s"$dockerHubUser/playsrv-image:latest"

      log.info(s"Building Docker image $versionTag for platform $dockerPlatform...")
      val buildCmd = Process(
        Seq(
          "docker", "build",
          "--platform", dockerPlatform,
          "-t", versionTag,
          "-t", latestTag,
          "-f", (dockerDir / "playsrv" / "Dockerfile").getAbsolutePath,
          (dockerDir / "playsrv").getAbsolutePath
        )
      )
      if (buildCmd.! != 0) {
        sys.error(s"Docker build failed for $versionTag")
      }
      log.info(s"Successfully built $versionTag and $latestTag")
    },
    dockerPushPlaysrv := {
      dockerBuildPlaysrv.value
      val log = streams.value.log
      val versionTag = s"$dockerHubUser/playsrv-image:$appVersion"
      val latestTag = s"$dockerHubUser/playsrv-image:latest"

      log.info(s"Pushing $versionTag to Docker Hub...")
      if (Process(Seq("docker", "push", versionTag)).! != 0) {
        sys.error(s"Docker push failed for $versionTag")
      }
      log.info(s"Pushing $latestTag to Docker Hub...")
      if (Process(Seq("docker", "push", latestTag)).! != 0) {
        sys.error(s"Docker push failed for $latestTag")
      }
      log.info(s"Successfully pushed $versionTag and $latestTag")
    },
    dockerBuildWpCli := {
      val log = streams.value.log
      val env = getAppEnv
      val dockerDir = getDockerDir(baseDirectory.value, env)
      val versionTag = s"$dockerHubUser/wp-cli-instance:$appVersion"
      val latestTag = s"$dockerHubUser/wp-cli-instance:latest"
      val dockerfile = dockerDir / "wp-cli" / "Dockerfile"

      log.info(s"Building Docker image $versionTag for platform $dockerPlatform...")
      val buildCmd = Process(
        Seq(
          "docker", "build",
          "--platform", dockerPlatform,
          "-t", versionTag,
          "-t", latestTag,
          "-f", dockerfile.getAbsolutePath,
          dockerfile.getParentFile.getAbsolutePath
        )
      )
      if (buildCmd.! != 0) {
        sys.error(s"Docker build failed for $versionTag")
      }
      log.info(s"Successfully built $versionTag and $latestTag")
    },
    dockerPushWpCli := {
      dockerBuildWpCli.value
      val log = streams.value.log
      val versionTag = s"$dockerHubUser/wp-cli-instance:$appVersion"
      val latestTag = s"$dockerHubUser/wp-cli-instance:latest"

      log.info(s"Pushing $versionTag to Docker Hub...")
      if (Process(Seq("docker", "push", versionTag)).! != 0) {
        sys.error(s"Docker push failed for $versionTag")
      }
      log.info(s"Pushing $latestTag to Docker Hub...")
      if (Process(Seq("docker", "push", latestTag)).! != 0) {
        sys.error(s"Docker push failed for $latestTag")
      }
      log.info(s"Successfully pushed $versionTag and $latestTag")
    },
    dockerBuildWpGmp := {
      val log = streams.value.log
      val env = getAppEnv
      val dockerDir = getDockerDir(baseDirectory.value, env)
      val versionTag = s"$dockerHubUser/wp-gmp-image:$appVersion"
      val latestTag = s"$dockerHubUser/wp-gmp-image:latest"
      val dockerfile = dockerDir / "wordpress" / "Dockerfile"

      log.info(s"Building Docker image $versionTag for platform $dockerPlatform...")
      val buildCmd = Process(
        Seq(
          "docker", "build",
          "--platform", dockerPlatform,
          "-t", versionTag,
          "-t", latestTag,
          "-f", dockerfile.getAbsolutePath,
          dockerfile.getParentFile.getAbsolutePath
        )
      )
      if (buildCmd.! != 0) {
        sys.error(s"Docker build failed for $versionTag")
      }
      log.info(s"Successfully built $versionTag and $latestTag")
    },
    dockerPushWpGmp := {
      dockerBuildWpGmp.value
      val log = streams.value.log
      val versionTag = s"$dockerHubUser/wp-gmp-image:$appVersion"
      val latestTag = s"$dockerHubUser/wp-gmp-image:latest"

      log.info(s"Pushing $versionTag to Docker Hub...")
      if (Process(Seq("docker", "push", versionTag)).! != 0) {
        sys.error(s"Docker push failed for $versionTag")
      }
      log.info(s"Pushing $latestTag to Docker Hub...")
      if (Process(Seq("docker", "push", latestTag)).! != 0) {
        sys.error(s"Docker push failed for $latestTag")
      }
      log.info(s"Successfully pushed $versionTag and $latestTag")
    },
    dockerBuildImages := {
      dockerBuildPlaysrv.value
      dockerBuildWpCli.value
      dockerBuildWpGmp.value
    },
    dockerPushImages := {
      dockerPushPlaysrv.value
      dockerPushWpCli.value
      dockerPushWpGmp.value
    }
  )
  .enablePlugins(PlayScala)
  .enablePlugins(SbtWeb)
  .dependsOn(shared.jvm)

lazy val client = project
  .settings(
    (Compile / unmanagedSources / excludeFilter) := {         
      val baseFilter = HiddenFileFilter || "*~" || "*.tmp"
    
      if (includeAddon) {
        baseFilter
      } else {
        baseFilter || new SimpleFileFilter(file =>
          file.getAbsolutePath.contains("src/main/scala/addon")
        )
      }
    },

    Compile / sourceGenerators += Def.task {
      val out = (Compile / sourceManaged).value / "AddonConfig.scala"
      val code =
        if (includeAddon)
          """package addon
            |object Console { 
            |  val enabled = true
            |  def start() = DebugConsole.start()
            |}
            |""".stripMargin          

        else
          """package addon
            |object Console { 
            |  val enabled = false
            |  def start() = println("debug console not available")
            |}
            |""".stripMargin          

      IO.write(out, code)
      Seq(out)
    },

    scalaJSUseMainModuleInitializer := false,
    scalaJSLinkerConfig ~= { _.withModuleKind(ModuleKind.ESModule) }, 
    libraryDependencies += "org.scala-js" %%% "scalajs-dom" % "2.8.0",
    libraryDependencies += "com.lihaoyi" %%% "upickle" % "3.3.1",
    libraryDependencies += "org.rogach"  %%% "scallop" % "5.1.0",
    libraryDependencies += "org.typelevel" %%% "cats-core" % "2.12.0",
    libraryDependencies += "com.lihaoyi" %%% "sourcecode" % "0.4.2"
  )
  .enablePlugins(ScalaJSPlugin, ScalaJSWeb)
  .dependsOn(shared.js)
  .enablePlugins(SbtTwirl)

lazy val shared = crossProject(JSPlatform, JVMPlatform)
  .crossType(CrossType.Pure).in(file("shared"))
  .settings(
     name := "shared",
     libraryDependencies ++= Seq(
       "com.lihaoyi" %%% "upickle" % "3.3.1",
       "com.lihaoyi" %% "upickle" % "3.3.1",
       "org.typelevel" %%% "shapeless3-deriving" % "3.4.0",
       "com.lihaoyi" %%% "sourcecode" % "0.4.2",
       "com.lihaoyi" %% "sourcecode" % "0.4.2",
       "org.typelevel" %%% "cats-core" % "2.13.0"
     )
   )
  .jsConfigure(_.enablePlugins(ScalaJSWeb))

Global / onLoad := (Global / onLoad).value.andThen(state => "project server" :: state)

def hello = Command.command("hello") { state =>
  println(s"Hello")
  state
}

def buildMsg = Command.command("buildMsg") { state =>
  println("--- Starte Nachrichten-Generierung ---")
  val state1 = Command.process("genMsgFiles", state)
  val state2 = Command.process("convertMessagesToJson", state1)
  println("--- Nachrichten-Generierung abgeschlossen ---")
  state2
}
