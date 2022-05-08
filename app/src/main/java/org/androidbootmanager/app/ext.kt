package org.androidbootmanager.app

fun String.Companion.join(delimiter: String, list: Iterable<CharSequence>) {
	java.lang.String.join(delimiter, list)
}

fun Iterable<CharSequence>.join(delimiter: String) {
	String.join(delimiter, this)
}