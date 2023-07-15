package com.legacycode.eureka.hierarchy

import com.legacycode.eureka.dex.Ancestor
import com.legacycode.eureka.dex.InheritanceAdjacencyList
import com.legacycode.eureka.dex.TreeClusterJsonTreeBuilder
import com.legacycode.eureka.hierarchy.HierarchyServer.Companion.PARAM_CLASS
import com.legacycode.eureka.hierarchy.HierarchyServer.Companion.PARAM_PRUNE
import io.ktor.http.ContentType
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.engine.ApplicationEngine
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.request.uri
import io.ktor.server.response.respondRedirect
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.util.pipeline.PipelineContext
import java.io.File

class HierarchyServer(
  private val adjacencyList: InheritanceAdjacencyList,
  private val ancestorFromCommandLine: Ancestor,
  private val apkFile: File,
) {
  companion object {
    internal const val PARAM_CLASS = "class"
    internal const val PARAM_PRUNE = "prune"
  }

  private lateinit var webServer: ApplicationEngine

  fun start(port: Int) {
    webServer = embeddedServer(
      Netty,
      port = port,
      module = { setupRoutes(adjacencyList, ancestorFromCommandLine, apkFile) }
    ).start(wait = true)
  }
}

fun Application.setupRoutes(
  adjacencyList: InheritanceAdjacencyList,
  ancestorFromCommandLine: Ancestor,
  apkFile: File,
) {
  routing {
    get("/") {
      handleIndexRoute(adjacencyList, apkFile, ancestorFromCommandLine)
    }
  }
}

private suspend fun PipelineContext<Unit, ApplicationCall>.handleIndexRoute(
  adjacencyList: InheritanceAdjacencyList,
  apkFile: File,
  ancestorFromCommandLine: Ancestor,
) {
  val className = call.parameters[PARAM_CLASS]
  val urlHasClassParameter = className != null

  if (urlHasClassParameter) {
    val root = Ancestor(toClassDescriptor(className!!))
    val searchTerm = call.parameters[PARAM_PRUNE]
    val adjacencyListToUse = if (searchTerm != null) {
      adjacencyList.prune(searchTerm)
    } else {
      adjacencyList
    }

    val treeClusterJson = adjacencyListToUse.tree(root, TreeClusterJsonTreeBuilder())
    val html = getHierarchyHtml(getTitle(apkFile, root), getHeading(apkFile, root, searchTerm), treeClusterJson)
    call.respondText(html, ContentType.Text.Html)
  } else {
    val currentUrl = call.request.uri
    val redirectUrl = "$currentUrl?class=${ancestorFromCommandLine.fqn}"
    call.respondRedirect(redirectUrl)
  }
}

private fun toClassDescriptor(className: String): String {
  return "L${className.replace('.', '/')};"
}

private fun getHierarchyHtml(title: String, heading: String, json: String): String {
  return HierarchyServer::class.java.getResourceAsStream("/hierarchy.html")!!
    .bufferedReader()
    .use { it.readText() }
    .replace("{{title}}", title)
    .replace("{{heading}}", heading)
    .replace("{{data}}", json)
}

private fun getTitle(apkFile: File, ancestor: Ancestor): String {
  val className = ancestor.fqn
  return "${apkFile.name} (${className.substring(className.lastIndexOf('.') + 1)})"
}

private fun getHeading(
  apkFile: File,
  ancestor: Ancestor,
  pruneKeyword: String?,
): String {
  val className = ancestor.fqn
  return if (pruneKeyword != null) {
    "${apkFile.name} (${className} → showing \"$pruneKeyword\")"
  } else {
    "${apkFile.name} (${className})"
  }
}
