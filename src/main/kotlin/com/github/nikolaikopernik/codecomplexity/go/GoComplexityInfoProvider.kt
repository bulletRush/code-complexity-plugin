package com.github.nikolaikopernik.codecomplexity.go

import com.github.nikolaikopernik.codecomplexity.core.ComplexityInfoProvider
import com.github.nikolaikopernik.codecomplexity.core.ComplexitySink
import com.github.nikolaikopernik.codecomplexity.core.ElementVisitor
import com.goide.GoLanguage
import com.goide.psi.GoFunctionOrMethodDeclaration
import com.goide.psi.GoTypeSpec
import com.intellij.lang.Language
import com.intellij.psi.PsiElement

class GoComplexityInfoProvider(override val language: Language = GoLanguage.INSTANCE) : ComplexityInfoProvider {

    override fun isComplexitySuitableMember(element: PsiElement): Boolean {
        return element is GoFunctionOrMethodDeclaration
    }

    override fun isClassWithBody(element: PsiElement): Boolean {
        // Go doesn't have classes, but we can check for type declarations with methods
        return element is GoTypeSpec && element.methods.isNotEmpty()
    }

    override fun getVisitor(sink: ComplexitySink): ElementVisitor {
        return GoLanguageVisitor(sink)
    }

    override fun getNameElementFor(element: PsiElement): PsiElement {
        if (element is GoFunctionOrMethodDeclaration) {
            return element.identifier ?: element
        }
        return element
    }
}
