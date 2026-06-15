package kodegraph.model

data class KGClass(
    val fqName: String,
    val simpleName: String,
    val packageName: String,
    val type: KGClassType,
    val dependencies: List<KGDependency>,
    val implementedInterfaces: List<String>,
    val imports: Map<String, String> = emptyMap()
) {
    fun isInterfaceType(): Boolean {
        return type == KGClassType.INTERFACE
    }
}

