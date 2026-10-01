package ir.pocora

enum class Role {
    CHILD,
    PARENT,
    ;

    companion object {
        val current: Role
            get() = if (BUILD_ROLE == "child") CHILD else PARENT
    }
}
