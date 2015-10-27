package com.ssachtleben.sbt.define

import com.typesafe.sbt.coffeescript.Import.CoffeeScriptKeys.coffeescript
import com.ssachtleben.sbt.handlebars.Import.HandlebarsKeys.handlebars
import com.typesafe.sbt.web.SbtWeb
import com.typesafe.sbt.web.Import._
import com.typesafe.sbt.web.pipeline.Pipeline
import com.typesafe.sbt.web.PathMapping
import collection.mutable
import mutable.ListBuffer
import java.io.File
import java.util.regex.Pattern
import org.apache.commons.io.FilenameUtils
import org.apache.commons.lang3.StringUtils

import sbt._
import Keys._

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

  private def checkFolder(logger: Logger, inputfolders: Seq[java.io.File], outputfolder: java.io.File, files: PathFinder, mappings: Seq[PathMapping]) : Seq[PathMapping] = {
    var reducedMappings = Seq.empty[PathMapping]
    val items = files.pair(relativeTo(inputfolders) | flat).toMap.values

    //logger.info("Define updating " + items.size + " source(s)"

    items.foreach { path =>
      val matchedFile = mappings.filter(f => f._2.equals(path) && includeFilter.value.accept(f._1) && !excludeFilter.value.accept(f._1)).head
      val paths = List.fromArray(path.split(Pattern.quote(File.separator)))
      val isHandlebar = matchedFile._1.getAbsolutePath().indexOf("handlebars" + File.separator) > -1
      val defineName = paths.drop(2).mkString("/").dropRight(FilenameUtils.getExtension(path).length() + 1)     
      val fileContent = IO.read(matchedFile._1)
      if (StringUtils.isNotBlank(fileContent) && !fileContent.trim().startsWith("define")) {
        logger.info("Define " + defineName + " -> " + matchedFile._1.getAbsolutePath())
        IO.write(new java.io.File(outputfolder, path), "define(\"" + defineName + "\", function() { " + (if(isHandlebar) "return " else "") + fileContent.trim() + " });")
        reducedMappings = reducedMappings ++ Seq.apply(matchedFile);
      }
    }
    reducedMappings
  }
    
  def transformFiles: Def.Initialize[Task[Pipeline.Stage]] = Def.task { (mappings: Seq[PathMapping]) =>
    val outputfolder = (resourceManaged in define in Assets).value
    if (outputfolder.exists() && outputfolder.isDirectory()) {
      org.apache.commons.io.FileUtils.cleanDirectory(outputfolder);
    }
    var reducedMappings = Seq.empty[PathMapping]
    val inputFolders = Seq.apply((resourceManaged in coffeescript in Assets).value) ++ Seq.apply((resourceManaged in handlebars in Assets).value)
    reducedMappings = reducedMappings ++ checkFolder(streams.value.log, inputFolders, outputfolder, files.value, mappings)
    val compiled = outputfolder.***.get.filter(f => f.isFile && f.getAbsolutePath.startsWith(outputfolder.getAbsolutePath)).pair(relativeTo(outputfolder))
    (mappings.toSet -- reducedMappings.toSet ++ compiled.toSet).toSeq
  }

}