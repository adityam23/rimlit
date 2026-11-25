package app.rimlit.data

/** The value of [E] named this, or [default] when there is none: stored enums tolerate unknown values. */
internal inline fun <reified E : Enum<E>> String?.toEnumOr(default: E): E = toEnumOrNull<E>() ?: default

/** The value of [E] named this; `null` when there is none, for a value with no fallback. */
internal inline fun <reified E : Enum<E>> String?.toEnumOrNull(): E? =
    this?.let { name -> enumValues<E>().firstOrNull { it.name == name } }
