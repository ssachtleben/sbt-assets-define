package com.ssachtleben.sbt.define

import java.io.File
import java.nio.charset.Charset
import java.util.regex.Pattern

import com.ssachtleben.sbt.handlebars.Import.HandlebarsKeys.handlebars
import com.typesafe.sbt.coffeescript.Import.CoffeeScriptKeys.coffeescript
import com.typesafe.sbt.web.pipeline.Pipeline
import com.typesafe.sbt.web.{PathMapping, SbtWeb}
import org.apache.commons.io.FilenameUtils
import org.apache.commons.lang3.StringUtils
import sbt.Keys._
import sbt._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{Await, Future}
import scala.concurrent.duration._

object Import {

  val define = TaskKey[Pipeline.Stage]("coffee-define", "wraps coffee scripts with a define block")

  object Define {
    val files = SettingKey[sbt.PathFinder]("coffee-define-files", "List of ConcatGroup files")
  }

}

object DefinePlugin extends sbt.AutoPlugin {

  override def requires = SbtWeb

  override def trigger = AllRequirements

  val autoImport = Import
  val utf8 = Charset.forName("UTF-8")

  import SbtWeb.autoImport._
  import WebKeys._
  import autoImport._
  import Define._

  override def projectSettings: Seq[Setting[_]] = Seq(
    resourceManaged in define in Assets := webTarget.value / define.key.label / "main",
    resourceManaged in define in TestAssets := webTarget.value / define.key.label / "test",
    define in Assets := (define in Assets).dependsOn(coffeescript in Assets).dependsOn(handlebars in Assets).value,
    define in TestAssets := (define in TestAssets).dependsOn(coffeescript in TestAssets).dependsOn(handlebars in TestAssets).value,
    includeFilter in define := "*.js",
    excludeFilter in define := HiddenFileFilter,
    files := (resourceManaged in coffeescript in Assets).value / "javascripts" ** "*.js" +++
      (resourceManaged in handlebars in Assets).value ** "*.js",
    define := transformFiles.value)




  private def checkFolder(logger: Logger, inputfolders: Seq[java.io.File], outputfolder: java.io.File, files: PathFinder, mappings: Seq[PathMapping]): Seq[PathMapping] = {
    var reducedMappings = Seq.empty[PathMapping]
    val reducedFiles = files.filter(f => f.isFile)
    val items = reducedFiles.pair(relativeTo(inputfolders) | flat)
    val block = Boolean.box(true)
    logger.info("Define updating " + items.size + " source(s)")


    val futures = items.map((tuple: (sbt.File, String)) => Future {
      val matchedFile = mappings.filter(f => f._2.equals(tuple._2)).head
      val paths = tuple._2.split(Pattern.quote(File.separator)).toList
      val isHandlebar = matchedFile._1.getAbsolutePath().indexOf("handlebars" + File.separator) > -1
      val defineName = paths.drop(2).mkString("/").dropRight(FilenameUtils.getExtension(tuple._2).length() + 1)
      val fileContent = IO.read(matchedFile._1, utf8)

      if (StringUtils.isNotBlank(fileContent) && !fileContent.trim().startsWith("define")) {
        //  logger.info("Define " + defineName + " -> " + matchedFile._1.getAbsolutePath())
        val newFile = new java.io.File(outputfolder, tuple._2)
        if (!newFile.exists() || newFile.lastModified() < matchedFile._1.lastModified()) {
          if (StringUtils.isNotBlank(defineName)) {
            IO.write(newFile, "define(\"" + defineName + "\", function() { " + (if (isHandlebar) "return " else "") + fileContent.trim() + " });", utf8)
          } else {
            IO.write(newFile, fileContent.trim(), utf8)
          }
        }
        block.synchronized {
          reducedMappings = reducedMappings ++ Seq.apply(matchedFile);
        }
      }
    })

    logger.info("Define - Wait for Read/Write Completion")
    Await.ready( Future.sequence(futures), Duration.Inf)
    logger.info("Define - Done - size of reduced Mapping = " + reducedMappings.size)

    reducedMappings
  }

  def transformFiles: Def.Initialize[Task[Pipeline.Stage]] = Def.task { (mappings: Seq[PathMapping]) =>
    val outputfolder = (resourceManaged in define in Assets).value

    if (outputfolder.exists() && outputfolder.isDirectory()) {
      //TODO only clean dir on clean command
      // org.apache.commons.io.FileUtils.cleanDirectory(outputfolder);
    }
    var reducedMappings = Seq.empty[PathMapping]
    val inputFolders = Seq.apply((resourceManaged in coffeescript in Assets).value) ++ Seq.apply((resourceManaged in handlebars in Assets).value)
    val inputFiles = (files.value ** ((includeFilter in define in Assets).value -- (excludeFilter in define in Assets).value)).get
    reducedMappings = reducedMappings ++ checkFolder(streams.value.log, inputFolders, outputfolder, inputFiles, mappings)
    val compiled = outputfolder.***.get.filter(f => f.isFile && f.getAbsolutePath.startsWith(outputfolder.getAbsolutePath)).pair(relativeTo(outputfolder))
    (mappings.toSet -- reducedMappings.toSet ++ compiled.toSet).toSeq
  }

}