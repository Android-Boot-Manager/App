package org.andbootmgr.app

fun String.Companion.join(delimiter: String, list: Iterable<CharSequence>): String {
	return java.lang.String.join(delimiter, list)
}

fun Iterable<CharSequence>.join(delimiter: String): String {
	return String.join(delimiter, this)
}