/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package templates

import templates.Family.*
import java.io.StringReader
import java.util.StringTokenizer

@TemplateDsl
class MemberBuilder(
    allowedPlatforms: Set<Platform>,
    target: KotlinTarget,
    family: Family,
    primitive: PrimitiveType? = null
) : TemplateBuilderBase(allowedPlatforms, target, family, primitive) {
    lateinit var keyword: Keyword    // fun/val/var

    private val legacyMode = false
    override var hasPlatformSpecializations: Boolean = legacyMode
        private set

    var doc: String? = null; private set

    var samples = listOf<String>()

    val sequenceClassification = mutableListOf<SequenceClass>()
    var deprecate: Deprecation? = null; private set
    var since: String? = null; private set
    var platformName: String? = null; private set

    var visibility: String? = null; private set
    var external: Boolean = false; private set
    var inline: Inline = Inline.No; private set
    var infix: Boolean = false; private set
    var operator: Boolean = false; private set
    val typeParams = mutableListOf<String>()
    var primaryTypeParameter: String? = null; private set
    var customReceiver: String? = null; private set
    var genericStarProjection: Boolean = false
    var toNullableT: Boolean = false

    var returns: String? = null; private set
    val throwsExceptions = mutableListOf<ThrowsException>()
    var body: String? = null; private set

    fun deprecate(value: Deprecation) { deprecate = value }
    fun deprecate(value: String) { deprecate = Deprecation(value) }
    fun since(value: String) { since = value }
    fun sinceAtLeast(value: String) {
        // TODO: comparing versions as strings, will work only up until Kotlin 1.10 or Kotlin 10.0
        since = maxOf(since, value, nullsFirst())
    }

    fun platformName(name: String) { platformName = name }

    fun visibility(value: String) { visibility = value }
    fun external(value: Boolean = true) { external = value }
    fun operator(value: Boolean = true) { operator = value }
    fun infix(value: Boolean = true) { infix = value }
    fun inline(value: Inline = Inline.Yes, suppressWarning: Boolean = false) {
        inline = value
        if (suppressWarning) {
            require(value == Inline.Yes)
            inline = Inline.YesSuppressWarning
        }
    }
    fun inlineOnly() { inline = Inline.Only }

    fun receiver(value: String) { customReceiver = value }
    @Deprecated("Use receiver()", ReplaceWith("receiver(value)"))
    fun customReceiver(value: String) = receiver(value)
    fun returns(type: String) { returns = type }
    @Deprecated("Use specialFor", ReplaceWith("specialFor(*fs) { returns(run(valueBuilder)) }"))
    fun returns(vararg fs: Family, valueBuilder: () -> String) = specialFor(*fs) { returns(run(valueBuilder)) }

    fun throws(exceptionType: String, reason: String) { throwsExceptions += ThrowsException(exceptionType, reason) }

    fun typeParam(typeParameterName: String, primary: Boolean = false) {
        typeParams += typeParameterName
        if (primary) {
            check(primaryTypeParameter == null)
            primaryTypeParameter = typeParameterName
        }
    }

    fun sequenceClassification(vararg sequenceClass: SequenceClass) {
        sequenceClassification += sequenceClass
    }

    fun doc(valueBuilder: DocExtensions.() -> String) {
        doc = valueBuilder(DocExtensions)
    }

    @Deprecated("Use specialFor", ReplaceWith("specialFor(*fs) { doc(valueBuilder) }"))
    fun doc(vararg fs: Family, valueBuilder: DocExtensions.() -> String) = specialFor(*fs) { doc(valueBuilder) }

    fun sample(vararg sampleRef: String) {
        samples = sampleRef.asList()
    }

    fun body(valueBuilder: () -> String) {
        body = valueBuilder()
    }
    fun body(f: Family, valueBuilder: () -> String) {
        specialFor(f) { body(valueBuilder) }
    }
    fun body(vararg families: Family, valueBuilder: () -> String) {
        specialFor(*families) { body(valueBuilder) }
    }


    fun on(platform: Platform, action: () -> Unit) {
        require(platform in allowedPlatforms) { "Platform $platform is not in the list of allowed platforms $allowedPlatforms" }
        if (target.platform == platform)
            action()
        else {
            hasPlatformSpecializations = true
        }
    }

    fun on(backend: Backend, action: () -> Unit) {
        require(target.platform == Platform.JS)
        if (target.backend == backend) action()
    }


    override fun build(builder: Appendable) {
        val headerOnly: Boolean
        val isImpl: Boolean
        if (!legacyMode) {
            headerOnly = target.platform == Platform.Common && hasPlatformSpecializations
            isImpl = target.platform != Platform.Common && Platform.Common in allowedPlatforms
        }
        else {
            // legacy mode when all is headerOnly + no_impl
            // except functions with optional parameters - they are common + no_impl
            val hasOptionalParams = signature.contains("=")
            headerOnly =  target.platform == Platform.Common && !hasOptionalParams
            isImpl = false
        }

        val returnType = returns ?: throw RuntimeException("No return type specified for $signature")
        val primaryTypeParameter = this.primaryTypeParameter ?: "T"

        fun renderType(expression: String, receiver: String, self: String): String {
            val t = StringTokenizer(expression, " \t\n,:()<>?.", true)
            val answer = StringBuilder()

            while (t.hasMoreTokens()) {
                val token = t.nextToken()
                answer.append(when (token) {
                    "RECEIVER" -> receiver
                    "SELF" -> self
                    "PRIMITIVE" -> primitive?.name ?: token
                    "ONE" -> when (primitive) {
                        PrimitiveType.Double -> "1.0"
                        PrimitiveType.Float -> "1.0f"
                        PrimitiveType.Long -> "1L"
                        PrimitiveType.ULong -> "1uL"
                        in PrimitiveType.unsignedPrimitives -> "1u"
                        else -> "1"
                    }
                    "-ONE" -> when (primitive) {
                        PrimitiveType.Double -> "-1.0"
                        PrimitiveType.Float -> "-1.0f"
                        PrimitiveType.Long -> "-1L"
                        in PrimitiveType.unsignedPrimitives -> error("-ONE is not in the domain of unsigned primitives")
                        else -> "-1"
                    }
                    "TCollection" -> {
                        when (family) {
                            CharSequences, Strings -> "Appendable"
                            else -> renderType("MutableCollection<in $primaryTypeParameter>", receiver, self)
                        }
                    }
                    primaryTypeParameter -> {
                        when (family) {
                            Generic -> primaryTypeParameter
                            CharSequences, Strings -> "Char"
                            Maps -> "Map.Entry<K, V>"
                            else -> primitive?.name ?: token
                        }
                    }
                    "TRange" -> {
                        when (family) {
                            Generic -> "Range<$primaryTypeParameter>"
                            else -> primitive!!.name + "Range"
                        }
                    }
                    "TProgression" -> {
                        when (family) {
                            Generic -> "Progression<out $primaryTypeParameter>"
                            else -> primitive!!.name + "Progression"
                        }
                    }
                    else -> token
                })
            }

            return answer.toString()
        }

        val receiverT = if (genericStarProjection) "*" else primaryTypeParameter
        val self = (when (family) {
            Iterables -> "Iterable<$receiverT>"
            Collections -> "Collection<$receiverT>"
            Lists -> "List<$receiverT>"
            Maps -> "Map<out K, V>"
            Sets -> "Set<$receiverT>"
            Sequences -> "Sequence<$receiverT>"
            InvariantArraysOfObjects -> "Array<$primaryTypeParameter>"
            ArraysOfObjects -> "Array<${receiverT.replace(primaryTypeParameter, "out $primaryTypeParameter")}>"
            Strings -> "String"
            CharSequences -> "CharSequence"
            Ranges -> "ClosedRange<$receiverT>"
            ArraysOfPrimitives, ArraysOfUnsigned -> primitive?.let { it.name + "Array" } ?: throw IllegalArgumentException("Primitive array should specify primitive type")
            RangesOfPrimitives -> primitive?.let { it.name + "Range" } ?: throw IllegalArgumentException("Primitive range should specify primitive type")
            ProgressionsOfPrimitives -> primitive?.let { it.name + "Progression" } ?: throw IllegalArgumentException("Primitive progression should specify primitive type")
            Primitives, Unsigned -> primitive?.let { it.name } ?: throw IllegalArgumentException("Primitive should specify primitive type")
            Generic -> primaryTypeParameter
        })

        val receiver = (customReceiver ?: self).let { renderType(it, it, self) }

        fun String.renderType(): String = renderType(this, receiver, self)

        fun effectiveTypeParams(): List<TypeParameter> {
            val parameters = typeParams.mapTo(mutableListOf()) { parseTypeParameter(it.renderType()) }

            if (family == Generic) {
                if (parameters.none { it.name == primaryTypeParameter })
                    parameters.add(TypeParameter(primaryTypeParameter))
                return parameters
            } else if (primitive == null && family != Strings && family != CharSequences) {
                val mentionedTypes = parseTypeRef(receiver).mentionedTypes() + parameters.flatMap { it.mentionedTypeRefs() }
                val implicitTypeParameters = mentionedTypes.filter { it.name.all(Char::isUpperCase) }
                for (implicit in implicitTypeParameters.reversed()) {
                    if (implicit.name != "*" && parameters.none { it.name == implicit.name }) {
                        parameters.add(0, TypeParameter(implicit.name))
                    }
                }

                return parameters
            } else {
                // substituted T is no longer a parameter
                val renderedT = primaryTypeParameter.renderType()
                return parameters.filterNot { it.name == renderedT }
            }
        }


        doc?.let { methodDoc ->
            builder.append("/**\n")
            StringReader(methodDoc.trim()).forEachLine { line ->
                builder.append(" * ").append(line.trim()).append("\n")
            }
            if (family == Sequences && sequenceClassification.isNotEmpty()) {
                builder.append(" *\n")
                builder.append(" * The operation is ${sequenceClassification.joinToString(" and ") { "_${it}_" }}.\n")
            }
            if (throwsExceptions.any()) {
                builder.append(" * \n")
                throwsExceptions.forEach { (type, reason) -> builder.append(" * @throws $type $reason\n") }
            }
            if (samples.any()) {
                builder.append(" * \n")
                samples.forEach { builder.append(" * @sample $it\n") }
            }
            builder.append(" */\n")
        }



        deprecate?.let { deprecated ->
            val args = listOfNotNull(
                    "\"${deprecated.message}\"",
                    deprecated.replaceWith?.let { "ReplaceWith(\"$it\")" },
                    deprecated.level.let { if (it != DeprecationLevel.WARNING) "level = DeprecationLevel.$it" else null }
            )
            builder.append("@Deprecated(${args.joinToString(", ")})\n")
        }

        if (!f.isPrimitiveSpecialization && primitive != null) {
            platformName
                    ?.replace("<$primaryTypeParameter>", primitive!!.name)
                    ?.let { platformName -> builder.append("@kotlin.jvm.JvmName(\"${platformName}\")\n") }
        }

        since?.let { since ->
            builder.append("@SinceKotlin(\"$since\")\n")
        }

        annotations.forEach { builder.append(it.trimIndent()).append('\n') }

        when (inline) {
            Inline.Only -> builder.append("@kotlin.internal.InlineOnly").append('\n')
            Inline.YesSuppressWarning -> suppressions.add("NOTHING_TO_INLINE")
        }

        if (suppressions.isNotEmpty()) {
            suppressions.joinTo(builder, separator = ", ", prefix = "@Suppress(", postfix = ")\n") {
                """"$it""""
            }
        }

        listOfNotNull(
                visibility ?: "public",
                "expect".takeIf { headerOnly },
                "actual".takeIf { isImpl },
                "external".takeIf { external },
                "inline".takeIf { inline.isInline() },
                "infix".takeIf { infix },
                "operator".takeIf { operator },
                keyword.value
        ).forEach { builder.append(it).append(' ') }

        val types = effectiveTypeParams()
        if (!types.isEmpty()) {
            builder.append(types.joinToString(separator = ", ", prefix = "<", postfix = "> ", transform = { it.original }))
        }

        val receiverType = (if (toNullableT) receiver.replace("T>", "T?>") else receiver).renderType()

        builder.append(receiverType)
        if (receiverType.isNotEmpty()) builder.append('.')
        builder.append("${signature.renderType()}: ${returnType.renderType()}")

        if (headerOnly) {
            builder.append("\n\n")
            return
        }

        if (keyword == Keyword.Function) builder.append(" {")

        val body = (body ?:
                deprecate?.replaceWith?.let { "return $it" } ?:
                """TODO("Body is not provided")""".also { System.err.println("ERROR: $signature for ${target.fullName}: no body specified for ${family to primitive}") }
                ).trim('\n')
        val indent: Int = body.takeWhile { it == ' ' }.length

        builder.append('\n')
        body.lineSequence().forEach {
            var count = indent
            val line = it.dropWhile { count-- > 0 && it == ' ' }.renderType()
            if (!line.isEmpty()) {
                builder.append("    ").append(line)
                builder.append("\n")
            }
        }

        if (keyword == Keyword.Function) builder.append("}\n")
        builder.append("\n")
    }

}