package org.jetbrains.kotlinx.jupyter.api.libraries

import org.jetbrains.kotlinx.jupyter.api.AfterCellExecutionCallback
import org.jetbrains.kotlinx.jupyter.api.ClassAnnotationHandler
import org.jetbrains.kotlinx.jupyter.api.CodePreprocessor
import org.jetbrains.kotlinx.jupyter.api.ExecutionCallback
import org.jetbrains.kotlinx.jupyter.api.FieldHandler
import org.jetbrains.kotlinx.jupyter.api.FileAnnotationHandler
import org.jetbrains.kotlinx.jupyter.api.InternalVariablesMarker
import org.jetbrains.kotlinx.jupyter.api.KotlinKernelVersion
import org.jetbrains.kotlinx.jupyter.api.RendererHandler
import org.jetbrains.kotlinx.jupyter.api.ThrowableRenderer
import org.jetbrains.kotlinx.jupyter.util.AcceptanceRule

/**
 * Library definition represents "library" concept in Kotlin kernel.
 */
interface LibraryDefinition {
    /**
     * List of artifact dependencies in gradle colon-separated format
     */
    val dependencies: List<String>
        get() = emptyList()

    /**
     * List of repository URLs to resolve dependencies in
     */
    val repositories: List<String>
        get() = emptyList()

    /**
     * List of imports: simple and star imports are both allowed
     */
    val imports: List<String>
        get() = emptyList()

    /**
     * List of code snippets evaluated on library initialization
     */
    val init: List<ExecutionCallback<*>>
        get() = emptyList()

    /**
     * List of code snippets evaluated before every cell evaluation
     */
    val initCell: List<ExecutionCallback<*>>
        get() = emptyList()

    /**
     * List of callbacks called after cell evaluation
     */
    val afterCellExecution: List<AfterCellExecutionCallback>
        get() = emptyList()

    /**
     * List of code snippets evaluated on kernel shutdown
     */
    val shutdown: List<ExecutionCallback<*>>
        get() = emptyList()

    /**
     * List of type renderers. Consider using [org.jetbrains.kotlinx.jupyter.api.Renderable]
     * as it's generally more convenient
     */
    val renderers: List<RendererHandler>
        get() = emptyList()

    /**
     * List of type converters used by type providers processors
     */
    val converters: List<FieldHandler>
        get() = emptyList()

    /**
     * List of type annotations used by annotations processor
     */
    val classAnnotations: List<ClassAnnotationHandler>
        get() = emptyList()

    /**
     * List of file annotation handlers
     */
    val fileAnnotations: List<FileAnnotationHandler>
        get() = emptyList()

    /**
     * List of library resources
     */
    val resources: List<LibraryResource>
        get() = emptyList()

    /**
     * List of code preprocessors
     */
    val codePreprocessors: List<CodePreprocessor>
        get() = emptyList()

    /**
     * Minimal kernel version that is supported by this library
     */
    val minKernelVersion: KotlinKernelVersion?

    /**
     * Original descriptor text, or **null** if it has non-textual nature
     */
    val originalDescriptorText: String?
        get() = null

    /**
     * Renderers of thrown exceptions
     */
    val throwableRenderers: List<ThrowableRenderer>
        get() = emptyList()

    /**
     * Predicates to tell if the declaration is internal.
     * Internal declarations tend to be not shown to a user in the variables view, but take place in resolution
     */
    val internalVariablesMarkers: List<InternalVariablesMarker>
        get() = emptyList()

    /**
     * Integration type name rules for the library integration classes which are about to be loaded transitively
     */
    val integrationTypeNameRules: List<AcceptanceRule<String>>
        get() = emptyList()
}
