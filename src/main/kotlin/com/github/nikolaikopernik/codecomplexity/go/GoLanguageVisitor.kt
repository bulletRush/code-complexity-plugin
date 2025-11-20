package com.github.nikolaikopernik.codecomplexity.go

import com.github.nikolaikopernik.codecomplexity.core.ComplexitySink
import com.github.nikolaikopernik.codecomplexity.core.ElementVisitor
import com.github.nikolaikopernik.codecomplexity.core.PointType
import com.goide.psi.*
import com.intellij.psi.PsiElement
import com.intellij.psi.tree.IElementType

internal class GoLanguageVisitor(private val sink: ComplexitySink) : ElementVisitor() {
    override fun processElement(element: PsiElement) {
        when (element) {
            is GoForStatement -> sink.increaseComplexityAndNesting(PointType.LOOP_FOR)
            is GoIfStatement -> element.processIfExpression()
            is GoSwitchStatement -> sink.increaseComplexityAndNesting(PointType.SWITCH)
            is GoTypeSwitchStatement -> sink.increaseComplexityAndNesting(PointType.SWITCH)
            is GoSelectStatement -> sink.increaseComplexityAndNesting(PointType.SWITCH)
            is GoCaseClause -> {
                // Each case adds complexity (except the first one which is counted by switch)
                if (element.parent is GoSwitchStatement || element.parent is GoTypeSwitchStatement) {
                    val parent = element.parent
                    val cases = parent.children.filterIsInstance<GoCaseClause>()
                    if (cases.indexOf(element) > 0) {
                        sink.increaseComplexity(PointType.SWITCH)
                    }
                }
            }
            is GoCommClause -> {
                // Each comm clause in select adds complexity
                if (element.parent is GoSelectStatement) {
                    val parent = element.parent as GoSelectStatement
                    val commClauses = parent.children.filterIsInstance<GoCommClause>()
                    if (commClauses.indexOf(element) > 0) {
                        sink.increaseComplexity(PointType.SWITCH)
                    }
                }
            }
            is GoBreakStatement -> {
                // Labeled break adds complexity
                val labelRef = element.children.firstOrNull { it is GoLabelRef }
                if (labelRef != null) sink.increaseComplexity(PointType.BREAK)
            }
            is GoContinueStatement -> {
                // Labeled continue adds complexity
                val labelRef = element.children.firstOrNull { it is GoLabelRef }
                if (labelRef != null) sink.increaseComplexity(PointType.CONTINUE)
            }
            is GoFunctionLit -> sink.increaseNesting()
            is GoAndExpr -> {
                // Handle && operator
                if (element.parent !is GoBinaryExpr) {
                    element.calculateBinaryComplexity()
                }
            }
            is GoOrExpr -> {
                // Handle || operator
                if (element.parent !is GoBinaryExpr) {
                    element.calculateBinaryComplexity()
                }
            }
            is GoCallExpr -> if (element.isRecursion()) sink.increaseComplexity(PointType.RECURSION)
        }
    }

    override fun postProcess(element: PsiElement) {
        when (element) {
            is GoForStatement,
            is GoIfStatement,
            is GoSwitchStatement,
            is GoTypeSwitchStatement,
            is GoSelectStatement,
            is GoFunctionLit -> sink.decreaseNesting()
        }
    }

    override fun shouldVisitElement(element: PsiElement): Boolean = true

    private fun GoIfStatement.processIfExpression() {
        // Check if this is an "else if" - if so, don't increase nesting
        if (this.isElseIf()) {
            return
        }
        sink.increaseComplexityAndNesting(PointType.IF)
    }

    private fun GoIfStatement.isElseIf(): Boolean {
        val parent = this.parent
        if (parent is GoIfStatement) {
            // This if statement is the else block of another if statement
            // Check if this is directly under the parent's else block
            val elseBlock = parent.children.firstOrNull { it.text.startsWith("else") }
            return elseBlock?.children?.contains(this) == true
        }
        return false
    }

    private fun GoBinaryExpr.calculateBinaryComplexity() {
        var prevOperator: PointType? = null

        fun processExpr(expr: GoExpression?) {
            when (expr) {
                is GoAndExpr -> {
                    if (prevOperator != PointType.LOGICAL_AND) {
                        sink.increaseComplexity(PointType.LOGICAL_AND)
                        prevOperator = PointType.LOGICAL_AND
                    }
                    processExpr(expr.left)
                    processExpr(expr.right)
                }
                is GoOrExpr -> {
                    if (prevOperator != PointType.LOGICAL_OR) {
                        sink.increaseComplexity(PointType.LOGICAL_OR)
                        prevOperator = PointType.LOGICAL_OR
                    }
                    processExpr(expr.left)
                    processExpr(expr.right)
                }
                is GoParenthesesExpr -> {
                    prevOperator = null
                    processExpr(expr.expression)
                }
            }
        }

        processExpr(this)
    }

    private fun GoCallExpr.isRecursion(): Boolean {
        val parentFunc: GoFunctionOrMethodDeclaration = this.findCurrentGoFunction() ?: return false
        val callName = this.expression?.text ?: return false
        val funcName = parentFunc.name ?: return false

        if (callName != funcName) return false

        // Simple check: compare argument count
        val callArgCount = this.argumentList?.expressionList?.size ?: 0
        val funcParamCount = parentFunc.signature?.parameters?.parameterDeclarationList?.size ?: 0

        return callArgCount == funcParamCount
    }
}

fun PsiElement.findCurrentGoFunction(): GoFunctionOrMethodDeclaration? {
    var element: PsiElement? = this
    while (element != null && element !is GoFunctionOrMethodDeclaration) {
        element = element.parent
    }
    return element as? GoFunctionOrMethodDeclaration
}
