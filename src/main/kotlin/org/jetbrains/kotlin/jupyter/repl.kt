package org.jetbrains.kotlin.jupyter

import jupyter.kotlin.DependsOn
import jupyter.kotlin.Repository
import jupyter.kotlin.ScriptTemplateWithDisplayHelpers
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageLocation
import org.jetbrains.kotlin.cli.common.repl.*
import org.jetbrains.kotlin.config.KotlinCompilerVersion
import java.io.File
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.script.dependencies.ScriptContents
import kotlin.script.experimental.api.*
import kotlin.script.experimental.host.createCompilationConfigurationFromTemplate
import kotlin.script.experimental.jvm.*
import kotlin.script.experimental.jvmhost.repl.JvmReplCompiler
import kotlin.script.experimental.jvmhost.repl.JvmReplEvaluator

data class EvalResult(val codeLine: LineId, val resultValue: Any?)

data class CheckResult(val codeLine: LineId, val isComplete: Boolean = true)

open class ReplException(message: String, cause: Throwable? = null) : Exception(message, cause)

class ReplEvalRuntimeException(val errorResult: ReplEvalResult.Error.Runtime) : ReplException(errorResult.message, errorResult.cause)

class ReplCompilerException(val errorResult: ReplCompileResult.Error) : ReplException(errorResult.message) {
    constructor (checkResult: ReplCheckResult.Error) : this(ReplCompileResult.Error(checkResult.message, checkResult.location))
    constructor (incompleteResult: ReplCompileResult.Incomplete) : this(ReplCompileResult.Error("Incomplete Code", null))
    constructor (checkResult: ReplEvalResult.Error.CompileTime) : this(ReplCompileResult.Error(checkResult.message, checkResult.location))
    constructor (incompleteResult: ReplEvalResult.Incomplete) : this(ReplCompileResult.Error("Incomplete Code", null))
    constructor (historyMismatchResult: ReplEvalResult.HistoryMismatch) : this(ReplCompileResult.Error("History Mismatch", CompilerMessageLocation.create(null, historyMismatchResult.lineNo, 0, null)))
}

class ReplForJupyter(val classpath: List<File> = emptyList()) {

    private fun ReplEvalResult.toResult(codeLine: LineId): EvalResult {
        return when (this) {
            is ReplEvalResult.Error.CompileTime -> throw ReplCompilerException(this)
            is ReplEvalResult.Error.Runtime -> throw ReplEvalRuntimeException(this)
            is ReplEvalResult.Incomplete -> throw ReplCompilerException(this)
            is ReplEvalResult.HistoryMismatch -> throw ReplCompilerException(this)
            is ReplEvalResult.UnitResult -> {
                EvalResult(codeLine, Unit)
            }
            is ReplEvalResult.ValueResult -> {
                EvalResult(codeLine, this.value)
            }
            else -> throw IllegalStateException("Unknown eval result type ${this}")
        }
    }

    private val resolver = JupyterScriptDependenciesResolver()

    private fun configureMavenDepsOnAnnotations(context: ScriptConfigurationRefinementContext): ResultWithDiagnostics<ScriptCompilationConfiguration> {
        val annotations = context.collectedData?.get(ScriptCollectedData.foundAnnotations)?.takeIf { it.isNotEmpty() }
                ?: return context.compilationConfiguration.asSuccess()
        val scriptContents = object : ScriptContents {
            override val annotations: Iterable<Annotation> = annotations
            override val file: File? = null
            override val text: CharSequence? = null
        }
        return try {
            val resolvedClasspath = resolver.resolveFromAnnotations(scriptContents)
            if(resolvedClasspath.isEmpty())
                return context.compilationConfiguration.asSuccess()
            context.compilationConfiguration.withUpdatedClasspath(resolvedClasspath).asSuccess()
        } catch (e: Throwable) {
            ResultWithDiagnostics.Failure(e.asDiagnostics(path = context.script.locationId))
        }
    }

    private val compilerConfiguration by lazy {
        createCompilationConfigurationFromTemplate(KotlinType(ScriptTemplateWithDisplayHelpers::class),
                defaultJvmScriptingHostConfiguration, ScriptTemplateWithDisplayHelpers::class) {
            defaultImports(DependsOn::class, Repository::class)
            jvm {
                updateClasspath(classpath)
            }
            refineConfiguration {
                onAnnotations(DependsOn::class, Repository::class, handler = { configureMavenDepsOnAnnotations(it) })
            }
        }
    }

    private val evaluatorConfiguration = ScriptEvaluationConfiguration { }

    private val compiler: ReplCompiler by lazy {
        JvmReplCompiler(compilerConfiguration)
    }

    private val evaluator: ReplEvaluator by lazy {
        JvmReplEvaluator(evaluatorConfiguration)
    }

    private val stateLock = ReentrantReadWriteLock()

    private val state = compiler.createState(stateLock)

    private val evaluatorState = evaluator.createState(stateLock)

    fun checkComplete(executionNumber: Long, code: String): CheckResult {
        val codeLine = ReplCodeLine(executionNumber.toInt(), 0, code)
        var result = compiler.check(state, codeLine)
        return when(result) {
            is ReplCheckResult.Error -> throw ReplCompilerException(result)
            is ReplCheckResult.Ok -> CheckResult(LineId(codeLine), true)
            is ReplCheckResult.Incomplete -> CheckResult(LineId(codeLine), false)
            else -> throw IllegalStateException("Unknown check result type ${result}")
        }
    }

    fun eval(executionNumber: Long, code: String): EvalResult {
        synchronized(this) {
            val codeLine = ReplCodeLine(executionNumber.toInt(), 0, code)
            val compileResult = compiler.compile(state, codeLine)
            when(compileResult){
                is ReplCompileResult.CompiledClasses -> {
                    var result = evaluator.eval(evaluatorState, compileResult)
                    return result.toResult(LineId(codeLine))
                }
                is ReplCompileResult.Error -> throw ReplCompilerException(compileResult)
                is ReplCompileResult.Incomplete -> throw ReplCompilerException(compileResult)
            }
        }
    }

    init {
        log.info("Starting kotlin repl ${KotlinCompilerVersion.VERSION}")
        log.info("Using classpath:\n${classpath}")
    }
}

