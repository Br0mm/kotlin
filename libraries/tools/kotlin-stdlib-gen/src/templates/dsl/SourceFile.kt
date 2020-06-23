/*
 * Copyright 2010-2018 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package templates

enum class SourceFile(
    jvmClassName: String? = null,
    val multifile: Boolean = true,
    val packageName: String? = null,
    testPackageName: String? = null,
    val jvmPackageName: String? = null
) {

    Arrays(packageName = "kotlin.collections"),
    UArrays(packageName = "kotlin.collections", jvmPackageName = "kotlin.collections.unsigned"),
    Collections(packageName = "kotlin.collections"),
    UCollections(packageName = "kotlin.collections"),
    Sets(packageName = "kotlin.collections"),
    Maps(packageName = "kotlin.collections"),
    Sequences(packageName = "kotlin.sequences"),
    USequences(packageName = "kotlin.sequences"),
    Ranges(packageName = "kotlin.ranges"),
    URanges(packageName = "kotlin.ranges"),
    Comparisons(packageName = "kotlin.comparisons"),
    UComparisons(packageName = "kotlin.comparisons", testPackageName = "test.unsigned"),
    Strings(packageName = "kotlin.text"),
    Primitives(testPackageName = "test.numbers"),
    Unsigned(testPackageName = "test.unsigned"),
    Misc()
    ;

    val jvmClassName = jvmClassName ?: (name.capitalize() + "Kt")
    val testPackageName = testPackageName ?: packageName?.replaceFirst("kotlin.", "test.")
}
