package com.github.tognitos.intellijstringconcatheredocplugin.services

import com.intellij.openapi.project.Project
import com.github.tognitos.intellijstringconcatheredocplugin.MyBundle

class MyProjectService(project: Project) {

    init {
        println(MyBundle.message("projectService", project.name))
    }
}
