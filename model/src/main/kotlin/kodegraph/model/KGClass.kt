package kodegraph.model

data class KGClass(
    val fqName: String,
    val simpleName: String,
    val packageName: String,
    val type: KGClassType,
    val dependencies: List<KGDependency>,
    val implementedInterfaces: List<String>
) {
    fun isInterfaceType(): Boolean {
        return type == KGClassType.INTERFACE
    }
}

