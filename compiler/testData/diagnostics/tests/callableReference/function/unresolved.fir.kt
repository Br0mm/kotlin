// !DIAGNOSTICS: -UNUSED_EXPRESSION, -UNUSED_PARAMETER
// !WITH_NEW_INFERENCE

class A

fun test1() {
    val foo = ::<!UNRESOLVED_REFERENCE!>foo<!>

    ::<!UNRESOLVED_REFERENCE!>bar<!>

    A::<!UNRESOLVED_REFERENCE!>bar<!>

    <!UNRESOLVED_REFERENCE!>B<!>::<!UNRESOLVED_REFERENCE!>bar<!>
}

fun test2() {
    fun foo(x: Any) {}
    fun foo() {}

    <!UNRESOLVED_REFERENCE!>Unresolved<!>::<!UNRESOLVED_REFERENCE!>foo<!>
    <!INAPPLICABLE_CANDIDATE!>foo<!>(<!UNRESOLVED_REFERENCE!>Unresolved<!>::<!UNRESOLVED_REFERENCE!>foo<!>)
    <!INAPPLICABLE_CANDIDATE!>foo<!>(<!UNRESOLVED_REFERENCE!>Unresolved<!>::<!UNRESOLVED_REFERENCE!>unresolved<!>)
    ::<!UNRESOLVED_REFERENCE!>unresolved<!>
}
