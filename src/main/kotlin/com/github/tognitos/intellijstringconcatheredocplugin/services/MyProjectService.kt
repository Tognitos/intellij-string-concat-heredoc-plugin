package com.github.tognitos.intellijstringconcatheredocplugin.services

import com.github.tognitos.intellijstringconcatheredocplugin.MyBundle
import com.intellij.openapi.project.Project

class MyProjectService(project: Project) {

    init {
        println(MyBundle.message("projectService", project.name))
        println("FINALLY WORKINssG2");
    }
}
